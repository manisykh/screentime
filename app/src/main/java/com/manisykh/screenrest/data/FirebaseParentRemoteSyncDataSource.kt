package com.manisykh.screenrest.data

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Date
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ParentRemotePairingRecord(
    val pairingCode: String,
    val childDeviceId: String,
    val childDeviceName: String,
    val createdAtMillis: Long,
)

object ParentRemoteSyncDataSourceFactory {
    fun create(context: Context): ParentRemoteSyncDataSource {
        return try {
            val app = FirebaseApp.initializeApp(context) ?: FirebaseApp.getApps(context).firstOrNull()
                ?: return LocalOnlyParentRemoteSyncDataSource
            FirebaseParentRemoteSyncDataSource(
                auth = FirebaseAuth.getInstance(app),
                firestore = FirebaseFirestore.getInstance(app),
            )
        } catch (_: Throwable) {
            LocalOnlyParentRemoteSyncDataSource
        }
    }
}

class FirebaseParentRemoteSyncDataSource(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ParentRemoteSyncDataSource {
    private val mutableSyncState = MutableStateFlow(
        ParentRemoteSyncState(
            mode = ParentRemoteSyncMode.Cloud,
            connected = false,
        ),
    )

    override val syncState: StateFlow<ParentRemoteSyncState> = mutableSyncState

    override fun observeChanges(childDeviceIds: Set<String>): Flow<ParentRemoteChange> = callbackFlow {
        val cleanChildIds = childDeviceIds
            .map { childDeviceId -> childDeviceId.trim() }
            .filter { childDeviceId -> childDeviceId.isNotBlank() }
            .distinct()
        if (cleanChildIds.isEmpty()) {
            close()
            return@callbackFlow
        }

        try {
            ensureSignedIn()
        } catch (error: Throwable) {
            updateFailed(error)
            close(error)
            return@callbackFlow
        }

        val registrations = cleanChildIds.flatMap { childDeviceId ->
            val childRegistration = childrenCollection()
                .document(childDeviceId)
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                    if (error != null) {
                        updateFailed(error)
                        close(error)
                    } else if (snapshot != null) {
                        updateConnected()
                        trySend(
                            ParentRemoteChange(
                                childDeviceId = childDeviceId,
                                type = ParentRemoteChangeType.ChildProfile,
                                fromCache = snapshot.metadata.isFromCache,
                                hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                            ),
                        )
                    }
                }
            val requestRegistration = requestsCollection(childDeviceId)
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                    if (error != null) {
                        updateFailed(error)
                        close(error)
                    } else if (snapshot != null) {
                        updateConnected()
                        trySend(
                            ParentRemoteChange(
                                childDeviceId = childDeviceId,
                                type = ParentRemoteChangeType.UnlockRequests,
                                fromCache = snapshot.metadata.isFromCache,
                                hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                            ),
                        )
                    }
                }
            val commandRegistration = commandsCollection(childDeviceId)
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                    if (error != null) {
                        updateFailed(error)
                        close(error)
                    } else if (snapshot != null) {
                        updateConnected()
                        trySend(
                            ParentRemoteChange(
                                childDeviceId = childDeviceId,
                                type = ParentRemoteChangeType.RemoteCommands,
                                fromCache = snapshot.metadata.isFromCache,
                                hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                            ),
                        )
                    }
                }
            listOf(childRegistration, requestRegistration, commandRegistration)
        }

        awaitClose {
            registrations.forEach { registration -> registration.remove() }
        }
    }

    override suspend fun publishPairingCode(
        pairingCode: String,
        childDeviceId: String,
        childDeviceName: String,
    ): ParentRemoteSyncResult {
        val cleanCode = pairingCode.trim().uppercase()
        if (cleanCode.isBlank() || childDeviceId.isBlank()) {
            return ParentRemoteSyncResult.Failed("Pairing code or child device id is blank")
        }
        return runRemote("publish pairing code") {
            val now = System.currentTimeMillis()
            val childUid = currentUid()
            val childRef = childrenCollection().document(childDeviceId)
            val pairingRef = pairingCodesCollection().document(cleanCode)
            firestore.runTransaction { transaction ->
                transaction.set(
                    childRef,
                    mapOf(
                        "childDeviceId" to childDeviceId,
                        "childDeviceName" to childDeviceName,
                        "childUid" to childUid,
                        "activePairingCode" to cleanCode,
                        "status" to "active",
                        "updatedAtMillis" to now,
                    ),
                    SetOptions.merge(),
                )
                transaction.set(
                    pairingRef,
                    mapOf(
                        "pairingCode" to cleanCode,
                        "childDeviceId" to childDeviceId,
                        "childDeviceName" to childDeviceName,
                        "childUid" to childUid,
                        "createdAtMillis" to now,
                        "expiresAtMillis" to now + PAIRING_CODE_TTL_MILLIS,
                        "expiresAt" to Timestamp(Date(now + PAIRING_CODE_TTL_MILLIS)),
                        "status" to "active",
                        "used" to false,
                        "updatedAtMillis" to now,
                    ),
                    SetOptions.merge(),
                )
                null
            }.awaitResult()
        }
    }

    override suspend fun resolvePairingCode(pairingCode: String): ParentRemotePairingRecord? {
        return resolvePairingCode(pairingCode, "")
    }

    override suspend fun resolvePairingCode(
        pairingCode: String,
        parentDisplayName: String,
    ): ParentRemotePairingRecord? {
        val cleanCode = pairingCode.trim().uppercase()
        if (cleanCode.isBlank()) {
            return null
        }
        return try {
            ensureSignedIn()
            val now = System.currentTimeMillis()
            val parentUid = currentUid()
            val pairingRef = pairingCodesCollection().document(cleanCode)
            val record = firestore.runTransaction { transaction ->
                val snapshot = transaction.get(pairingRef)
                if (!snapshot.exists()) {
                    return@runTransaction null
                }
                val expiresAtMillis = snapshot.getLong("expiresAtMillis") ?: 0L
                val used = snapshot.getBoolean("used") ?: false
                val usedByCurrentParent = snapshot.getString("parentUid").orEmpty() == parentUid
                if (expiresAtMillis > 0L && expiresAtMillis < now) {
                    transaction.set(
                        pairingRef,
                        mapOf(
                            "status" to "expired",
                            "parentUid" to parentUid,
                            "expiredAt" to Timestamp(Date(now)),
                            "updatedAtMillis" to now,
                        ),
                        SetOptions.merge(),
                    )
                    return@runTransaction null
                }
                if (used && !usedByCurrentParent) {
                    return@runTransaction null
                }
                val pairingRecord = snapshot.toPairingRecord() ?: return@runTransaction null
                val linkedParent = mapOf(
                    "parentUid" to parentUid,
                    "parentDisplayName" to parentDisplayName.trim().ifBlank { "Parent device" },
                    "linkedAtMillis" to now,
                )
                val childRef = childrenCollection().document(pairingRecord.childDeviceId)
                val childSnapshot = transaction.get(childRef)
                val linkedParents = childSnapshot.linkedParents()
                    .filterNot { parent -> parent.parentUid == parentUid }
                    .map { parent ->
                        mapOf(
                            "parentUid" to parent.parentUid,
                            "parentDisplayName" to parent.parentDisplayName,
                            "linkedAtMillis" to parent.linkedAtMillis,
                        )
                    } + linkedParent
                val parentUids = (childSnapshot.parentUids() + parentUid).distinct()
                transaction.set(
                    childRef,
                    mapOf(
                        "parentUids" to parentUids,
                        "linkedParents" to linkedParents,
                        "status" to "active",
                        "linkedAtMillis" to now,
                        "updatedAtMillis" to now,
                    ),
                    SetOptions.merge(),
                )
                transaction.set(
                    pairingRef,
                    mapOf(
                        "used" to true,
                        "status" to "used",
                        "parentUid" to parentUid,
                        "usedAtMillis" to now,
                        "usedAt" to Timestamp(Date(now)),
                        "updatedAtMillis" to now,
                    ),
                    SetOptions.merge(),
                )
                pairingRecord
            }.awaitResult()
            updateConnected()
            record
        } catch (error: Throwable) {
            updateFailed(error)
            null
        }
    }

    override suspend fun fetchLinkedParents(childDeviceId: String): List<LinkedParentDevice> {
        if (childDeviceId.isBlank()) {
            return emptyList()
        }
        return try {
            ensureSignedIn()
            val snapshot = childrenCollection().document(childDeviceId).get().awaitResult()
            updateConnected()
            snapshot.linkedParents()
        } catch (error: Throwable) {
            updateFailed(error)
            emptyList()
        }
    }

    override suspend fun fetchLinkedChildDevices(childDeviceIds: List<String>): List<LinkedChildDevice> {
        val cleanIds = childDeviceIds
            .map { id -> id.trim() }
            .filter { id -> id.isNotBlank() }
            .distinct()
        if (cleanIds.isEmpty()) {
            return emptyList()
        }
        return try {
            ensureSignedIn()
            val children = cleanIds.mapNotNull { childDeviceId ->
                val snapshot = childrenCollection().document(childDeviceId).get().awaitResult()
                if (!snapshot.exists()) {
                    null
                } else {
                    LinkedChildDevice(
                        childDeviceId = childDeviceId,
                        childDeviceName = snapshot.getString("childDeviceName")
                            .orEmpty()
                            .ifBlank { "Child device" },
                        pairingCode = "",
                        linkedAtMillis = snapshot.getLong("linkedAtMillis") ?: 0L,
                    )
                }
            }
            updateConnected()
            children
        } catch (error: Throwable) {
            updateFailed(error)
            emptyList()
        }
    }

    override suspend fun updateChildProfile(
        childDeviceId: String,
        childDeviceName: String,
        pairingCode: String,
    ): ParentRemoteSyncResult {
        val cleanChildDeviceId = childDeviceId.trim()
        val cleanChildDeviceName = childDeviceName.trim().ifBlank { "Child device" }
        if (cleanChildDeviceId.isBlank()) {
            return ParentRemoteSyncResult.Failed("Child device id is blank")
        }
        return runRemote("update child profile") {
            val now = System.currentTimeMillis()
            childrenCollection()
                .document(cleanChildDeviceId)
                .set(
                    mapOf(
                        "childDeviceName" to cleanChildDeviceName,
                        "updatedAtMillis" to now,
                    ),
                    SetOptions.merge(),
                )
                .awaitResult()

            val cleanPairingCode = pairingCode.trim().uppercase()
            if (cleanPairingCode.isNotBlank()) {
                pairingCodesCollection()
                    .document(cleanPairingCode)
                    .set(
                        mapOf(
                            "childDeviceName" to cleanChildDeviceName,
                            "updatedAtMillis" to now,
                        ),
                        SetOptions.merge(),
                    )
                    .awaitResult()
            }
        }
    }

    override suspend fun updateParentProfile(
        childDeviceIds: List<String>,
        parentDisplayName: String,
    ): ParentRemoteSyncResult {
        val cleanIds = childDeviceIds
            .map { id -> id.trim() }
            .filter { id -> id.isNotBlank() }
            .distinct()
        val cleanParentDisplayName = parentDisplayName.trim().ifBlank { "Parent device" }
        if (cleanIds.isEmpty()) {
            return ParentRemoteSyncResult.LocalOnly
        }
        return runRemote("update parent profile") {
            val now = System.currentTimeMillis()
            val parentUid = currentUid()
            cleanIds.forEach { childDeviceId ->
                val childRef = childrenCollection().document(childDeviceId)
                firestore.runTransaction { transaction ->
                    val snapshot = transaction.get(childRef)
                    val linkedParents = snapshot.linkedParents()
                    val existing = linkedParents.firstOrNull { parent -> parent.parentUid == parentUid }
                    val updatedLinkedParents = (
                        linkedParents.filterNot { parent -> parent.parentUid == parentUid } +
                            LinkedParentDevice(
                                parentUid = parentUid,
                                parentDisplayName = cleanParentDisplayName,
                                linkedAtMillis = existing?.linkedAtMillis ?: now,
                            )
                        )
                        .distinctBy { parent -> parent.parentUid }
                        .map { parent ->
                            mapOf(
                                "parentUid" to parent.parentUid,
                                "parentDisplayName" to parent.parentDisplayName,
                                "linkedAtMillis" to parent.linkedAtMillis,
                            )
                        }
                    val parentUids = (snapshot.parentUids() + parentUid).distinct()
                    transaction.set(
                        childRef,
                        mapOf(
                            "parentUids" to parentUids,
                            "linkedParents" to updatedLinkedParents,
                            "updatedAtMillis" to now,
                        ),
                        SetOptions.merge(),
                    )
                    null
                }.awaitResult()
            }
        }
    }

    override suspend fun publishUnlockRequest(request: RemoteUnlockRequest): ParentRemoteSyncResult {
        if (request.childDeviceId.isBlank() || request.id.isBlank()) {
            return ParentRemoteSyncResult.Failed("Unlock request is missing child device id or request id")
        }
        return runRemote("publish unlock request") {
            requestsCollection(request.childDeviceId)
                .document(request.id)
                .set(
                    request.toRemoteMap() + mapOf("childUid" to currentUid()),
                    SetOptions.merge(),
                )
                .awaitResult()
        }
    }

    override suspend fun publishUnlockDecision(
        request: RemoteUnlockRequest,
        decision: ParentRemoteUnlockDecision,
    ): ParentRemoteSyncResult {
        if (request.childDeviceId.isBlank() || request.id.isBlank()) {
            return ParentRemoteSyncResult.Failed("Unlock decision is missing child device id or request id")
        }
        return runRemote("publish unlock decision") {
            val command = decision.toRemoteCommand(request)
            val parentUid = currentUid()
            val requestRef = requestsCollection(request.childDeviceId).document(request.id)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(requestRef)
                val currentStatus = snapshot.getString("status") ?: RemoteUnlockRequestStatus.Pending.toRemoteValue()
                if (currentStatus != RemoteUnlockRequestStatus.Pending.toRemoteValue()) {
                    return@runTransaction null
                }
                transaction.set(
                    requestRef,
                    mapOf(
                        "status" to if (decision.approved) {
                            RemoteUnlockRequestStatus.Approved.toRemoteValue()
                        } else {
                            RemoteUnlockRequestStatus.Rejected.toRemoteValue()
                        },
                        "decisionApproved" to decision.approved,
                        "decisionExtraMinutes" to decision.extraMinutes.coerceAtLeast(0),
                        "decisionUnlockForToday" to decision.unlockForToday,
                        "decidedAtMillis" to decision.decidedAtMillis.coerceAtLeast(0L),
                        "parentUid" to parentUid,
                    ),
                    SetOptions.merge(),
                )
                transaction.set(
                    commandsCollection(request.childDeviceId).document(command.id),
                    command.toRemoteMap() + mapOf("parentUid" to parentUid),
                    SetOptions.merge(),
                )
                null
            }.awaitResult()
        }
    }

    override suspend fun fetchChildRequests(
        parentAccountId: String,
        childDeviceId: String,
    ): List<RemoteUnlockRequest> {
        if (childDeviceId.isBlank()) {
            return emptyList()
        }
        return try {
            ensureSignedIn()
            val snapshot = requestsCollection(childDeviceId)
                .orderBy("createdAtMillis", Query.Direction.DESCENDING)
                .limit(30)
                .get()
                .awaitResult()
            updateConnected()
            snapshot.documents.mapNotNull { document -> document.toRemoteUnlockRequest() }
        } catch (error: Throwable) {
            updateFailed(error)
            emptyList()
        }
    }

    override suspend fun fetchChildCommands(childDeviceId: String): List<RemoteParentCommand> {
        if (childDeviceId.isBlank()) {
            return emptyList()
        }
        return try {
            ensureSignedIn()
            val snapshot = commandsCollection(childDeviceId)
                .orderBy("timestampMillis", Query.Direction.DESCENDING)
                .limit(30)
                .get()
                .awaitResult()
            updateConnected()
            snapshot.documents.mapNotNull { document -> document.toRemoteParentCommand() }
        } catch (error: Throwable) {
            updateFailed(error)
            emptyList()
        }
    }

    override suspend fun unlinkChild(
        childDeviceId: String,
        parentAccountId: String,
    ): ParentRemoteSyncResult {
        if (childDeviceId.isBlank()) {
            return ParentRemoteSyncResult.Failed("Child device id is blank")
        }
        return runRemote("unlink child") {
            val now = System.currentTimeMillis()
            val parentUid = currentUid()
            val childRef = childrenCollection().document(childDeviceId)
            val childSnapshot = childRef.get().awaitResult()
            val remainingParentUids = childSnapshot.parentUids()
                .filterNot { uid -> uid == parentUid }
                .distinct()
            val remainingLinkedParents = childSnapshot.linkedParents()
                .filterNot { parent -> parent.parentUid == parentUid }
                .map { parent ->
                    mapOf(
                        "parentUid" to parent.parentUid,
                        "parentDisplayName" to parent.parentDisplayName,
                        "linkedAtMillis" to parent.linkedAtMillis,
                    )
                }
            childRef.set(
                mapOf(
                    "parentUids" to remainingParentUids,
                    "linkedParents" to remainingLinkedParents,
                    "status" to if (remainingParentUids.isEmpty()) "unlinked" else "active",
                    "lastUnlinkedParentUid" to parentUid,
                    "remainingParentCount" to remainingParentUids.size,
                    "unlinkedAtMillis" to now,
                    "updatedAtMillis" to now,
                ),
                SetOptions.merge(),
            ).awaitResult()
        }
    }

    override suspend fun unlinkParentFromChild(
        childDeviceId: String,
        parentUid: String,
    ): ParentRemoteSyncResult {
        if (childDeviceId.isBlank() || parentUid.isBlank()) {
            return ParentRemoteSyncResult.Failed("Child device id or parent uid is blank")
        }
        return runRemote("unlink parent from child") {
            val now = System.currentTimeMillis()
            val childRef = childrenCollection().document(childDeviceId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(childRef)
                val remainingParents = snapshot.parentUids()
                    .filterNot { uid -> uid == parentUid }
                    .distinct()
                val remainingLinkedParents = snapshot.linkedParents()
                    .filterNot { parent -> parent.parentUid == parentUid }
                    .map { parent ->
                        mapOf(
                            "parentUid" to parent.parentUid,
                            "parentDisplayName" to parent.parentDisplayName,
                            "linkedAtMillis" to parent.linkedAtMillis,
                        )
                    }
                transaction.set(
                    childRef,
                    mapOf(
                        "parentUids" to remainingParents,
                        "linkedParents" to remainingLinkedParents,
                        "status" to if (remainingParents.isEmpty()) "unlinked" else "active",
                        "lastUnlinkedParentUid" to parentUid,
                        "remainingParentCount" to remainingParents.size,
                        "unlinkedAtMillis" to now,
                        "updatedAtMillis" to now,
                    ),
                    SetOptions.merge(),
                )
                null
            }.awaitResult()
        }
    }

    private suspend fun runRemote(action: String, block: suspend () -> Unit): ParentRemoteSyncResult {
        return try {
            ensureSignedIn()
            block()
            updateConnected()
            ParentRemoteSyncResult.Success
        } catch (error: Throwable) {
            updateFailed(error)
            ParentRemoteSyncResult.Failed("$action failed: ${error.message.orEmpty()}")
        }
    }

    private suspend fun ensureSignedIn() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().awaitResult()
        }
    }

    private fun currentUid(): String = auth.currentUser?.uid.orEmpty()

    private fun updateConnected() {
        mutableSyncState.value = ParentRemoteSyncState(
            mode = ParentRemoteSyncMode.Cloud,
            connected = true,
            lastSyncMillis = System.currentTimeMillis(),
        )
    }

    private fun updateFailed(error: Throwable) {
        mutableSyncState.value = ParentRemoteSyncState(
            mode = ParentRemoteSyncMode.Cloud,
            connected = false,
            lastSyncMillis = System.currentTimeMillis(),
            lastError = error.message.orEmpty(),
        )
    }

    private fun pairingCodesCollection() = firestore.collection("screenrest_pairing_codes")

    private fun childrenCollection() = firestore.collection("screenrest_children")

    private fun requestsCollection(childDeviceId: String) =
        firestore.collection("screenrest_children")
            .document(childDeviceId)
            .collection("unlock_requests")

    private fun commandsCollection(childDeviceId: String) =
        firestore.collection("screenrest_children")
            .document(childDeviceId)
            .collection("remote_commands")
}

private fun ParentRemoteUnlockDecision.toRemoteCommand(request: RemoteUnlockRequest): RemoteParentCommand {
    val targetName = request.targetAppName.ifBlank {
        request.targetGroupName.ifBlank { request.targetPackageName }
    }
    val type = when {
        unlockForToday && request.blockReason == RemoteRequestBlockReason.DailyLimit ->
            RemoteParentCommandType.UnlockTotalToday
        unlockForToday -> RemoteParentCommandType.UnlockAppToday
        request.blockReason == RemoteRequestBlockReason.DailyLimit -> RemoteParentCommandType.AddTotalTime
        else -> RemoteParentCommandType.AddAppTime
    }
    val commandMinutes = if (unlockForToday) 0 else extraMinutes.coerceAtLeast(1)
    val message = if (unlockForToday) {
        "Remote parent approved unlock for today: ${request.blockReason.toRemoteValue()} $targetName"
    } else {
        "Remote parent approved ${commandMinutes}m: ${request.blockReason.toRemoteValue()} $targetName"
    }
    return RemoteParentCommand(
        id = requestId,
        timestampMillis = decidedAtMillis,
        type = type,
        targetPackageName = request.targetPackageName,
        targetAppName = targetName,
        minutes = commandMinutes,
        status = if (approved) RemoteParentCommandStatus.Applied else RemoteParentCommandStatus.Failed,
        message = message,
    )
}

private fun RemoteUnlockRequest.toRemoteMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "childDeviceId" to childDeviceId,
        "childDeviceName" to childDeviceName,
        "createdAtMillis" to createdAtMillis,
        "expiresAtMillis" to expiresAtMillis,
        "blockReason" to blockReason.toRemoteValue(),
        "targetPackageName" to targetPackageName,
        "targetAppName" to targetAppName,
        "targetGroupName" to targetGroupName,
        "scheduleName" to scheduleName,
        "usedMillis" to usedMillis,
        "limitMillis" to limitMillis,
        "alreadyGrantedExtraMinutes" to alreadyGrantedExtraMinutes,
        "unlockedForToday" to unlockedForToday,
        "requestedMinutes" to requestedMinutes,
        "childMessage" to childMessage,
        "status" to status.toRemoteValue(),
    )
}

private fun RemoteParentCommand.toRemoteMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "timestampMillis" to timestampMillis,
        "type" to type.toRemoteValue(),
        "targetPackageName" to targetPackageName,
        "targetAppName" to targetAppName,
        "minutes" to minutes,
        "status" to status.toRemoteValue(),
        "message" to message,
    )
}

private fun DocumentSnapshot.toPairingRecord(): ParentRemotePairingRecord? {
    if (!exists()) {
        return null
    }
    val code = getString("pairingCode").orEmpty().ifBlank { id }
    val childDeviceId = getString("childDeviceId").orEmpty()
    if (childDeviceId.isBlank()) {
        return null
    }
    return ParentRemotePairingRecord(
        pairingCode = code,
        childDeviceId = childDeviceId,
        childDeviceName = getString("childDeviceName").orEmpty().ifBlank { "Child device" },
        createdAtMillis = getLong("createdAtMillis") ?: 0L,
    )
}

private fun DocumentSnapshot.parentUids(): List<String> {
    return (get("parentUids") as? List<*>)
        ?.mapNotNull { value -> value as? String }
        .orEmpty()
}

private fun DocumentSnapshot.linkedParents(): List<LinkedParentDevice> {
    return (get("linkedParents") as? List<*>)
        ?.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val parentUid = map["parentUid"] as? String ?: return@mapNotNull null
            LinkedParentDevice(
                parentUid = parentUid,
                parentDisplayName = (map["parentDisplayName"] as? String)
                    .orEmpty()
                    .ifBlank { "Parent device" },
                linkedAtMillis = (map["linkedAtMillis"] as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L,
            )
        }
        .orEmpty()
        .distinctBy { parent -> parent.parentUid }
}

private fun DocumentSnapshot.toRemoteUnlockRequest(): RemoteUnlockRequest? {
    val blockReason = getString("blockReason")
        ?.toRemoteRequestBlockReasonOrNull()
        ?: return null
    val status = getString("status")
        ?.toRemoteUnlockRequestStatusOrNull()
        ?: RemoteUnlockRequestStatus.Pending
    return RemoteUnlockRequest(
        id = getString("id").orEmpty().ifBlank { id },
        childDeviceId = getString("childDeviceId").orEmpty(),
        childDeviceName = getString("childDeviceName").orEmpty(),
        createdAtMillis = getLong("createdAtMillis") ?: 0L,
        expiresAtMillis = getLong("expiresAtMillis") ?: 0L,
        blockReason = blockReason,
        targetPackageName = getString("targetPackageName").orEmpty(),
        targetAppName = getString("targetAppName").orEmpty(),
        targetGroupName = getString("targetGroupName").orEmpty(),
        scheduleName = getString("scheduleName").orEmpty(),
        usedMillis = getLong("usedMillis") ?: 0L,
        limitMillis = getLong("limitMillis"),
        alreadyGrantedExtraMinutes = getLong("alreadyGrantedExtraMinutes")?.toInt() ?: 0,
        unlockedForToday = getBoolean("unlockedForToday") ?: false,
        requestedMinutes = getLong("requestedMinutes")?.toInt() ?: 0,
        childMessage = getString("childMessage").orEmpty(),
        status = status,
    )
}

private fun DocumentSnapshot.toRemoteParentCommand(): RemoteParentCommand? {
    val type = getString("type")
        ?.toRemoteParentCommandTypeOrNull()
        ?: return null
    val status = getString("status")
        ?.toRemoteParentCommandStatusOrNull()
        ?: RemoteParentCommandStatus.Pending
    return RemoteParentCommand(
        id = getString("id").orEmpty().ifBlank { id },
        timestampMillis = getLong("timestampMillis") ?: 0L,
        type = type,
        targetPackageName = getString("targetPackageName").orEmpty(),
        targetAppName = getString("targetAppName").orEmpty(),
        minutes = getLong("minutes")?.toInt() ?: 0,
        status = status,
        message = getString("message").orEmpty(),
    )
}

private fun RemoteRequestBlockReason.toRemoteValue(): String {
    return when (this) {
        RemoteRequestBlockReason.DailyLimit -> "DailyLimit"
        RemoteRequestBlockReason.AppGroupLimit -> "AppGroupLimit"
        RemoteRequestBlockReason.AppLimit -> "AppLimit"
        RemoteRequestBlockReason.ScheduleBlock -> "ScheduleBlock"
        RemoteRequestBlockReason.AllowOnlyMode -> "AllowOnlyMode"
    }
}

private fun String.toRemoteRequestBlockReasonOrNull(): RemoteRequestBlockReason? {
    return when (this) {
        "DailyLimit" -> RemoteRequestBlockReason.DailyLimit
        "AppGroupLimit" -> RemoteRequestBlockReason.AppGroupLimit
        "AppLimit" -> RemoteRequestBlockReason.AppLimit
        "ScheduleBlock" -> RemoteRequestBlockReason.ScheduleBlock
        "AllowOnlyMode" -> RemoteRequestBlockReason.AllowOnlyMode
        else -> null
    }
}

private fun RemoteUnlockRequestStatus.toRemoteValue(): String {
    return when (this) {
        RemoteUnlockRequestStatus.Pending -> "Pending"
        RemoteUnlockRequestStatus.Approved -> "Approved"
        RemoteUnlockRequestStatus.Rejected -> "Rejected"
        RemoteUnlockRequestStatus.Expired -> "Expired"
        RemoteUnlockRequestStatus.Failed -> "Failed"
    }
}

private fun String.toRemoteUnlockRequestStatusOrNull(): RemoteUnlockRequestStatus? {
    return when (this) {
        "Pending" -> RemoteUnlockRequestStatus.Pending
        "Approved" -> RemoteUnlockRequestStatus.Approved
        "Rejected" -> RemoteUnlockRequestStatus.Rejected
        "Expired" -> RemoteUnlockRequestStatus.Expired
        "Failed" -> RemoteUnlockRequestStatus.Failed
        else -> null
    }
}

private fun RemoteParentCommandType.toRemoteValue(): String {
    return when (this) {
        RemoteParentCommandType.AddAppTime -> "AddAppTime"
        RemoteParentCommandType.UnlockAppToday -> "UnlockAppToday"
        RemoteParentCommandType.AddTotalTime -> "AddTotalTime"
        RemoteParentCommandType.UnlockTotalToday -> "UnlockTotalToday"
    }
}

private fun String.toRemoteParentCommandTypeOrNull(): RemoteParentCommandType? {
    return when (this) {
        "AddAppTime" -> RemoteParentCommandType.AddAppTime
        "UnlockAppToday" -> RemoteParentCommandType.UnlockAppToday
        "AddTotalTime" -> RemoteParentCommandType.AddTotalTime
        "UnlockTotalToday" -> RemoteParentCommandType.UnlockTotalToday
        else -> null
    }
}

private fun RemoteParentCommandStatus.toRemoteValue(): String {
    return when (this) {
        RemoteParentCommandStatus.Pending -> "Pending"
        RemoteParentCommandStatus.Applied -> "Applied"
        RemoteParentCommandStatus.Failed -> "Failed"
    }
}

private fun String.toRemoteParentCommandStatusOrNull(): RemoteParentCommandStatus? {
    return when (this) {
        "Pending" -> RemoteParentCommandStatus.Pending
        "Applied" -> RemoteParentCommandStatus.Applied
        "Failed" -> RemoteParentCommandStatus.Failed
        else -> null
    }
}

private suspend fun <T> Task<T>.awaitResult(): T {
    return suspendCancellableCoroutine { continuation: CancellableContinuation<T> ->
        addOnSuccessListener { result ->
            continuation.resume(result)
        }
        addOnFailureListener { error ->
            continuation.resumeWithException(error)
        }
    }
}

private const val PAIRING_CODE_TTL_MILLIS = 10L * 60L * 1_000L
