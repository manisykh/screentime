package com.manisykh.screenrest.data

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Date
import java.io.IOException
import java.security.MessageDigest
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
                functions = FirebaseFunctions.getInstance(app, "asia-northeast3"),
            )
        } catch (_: Throwable) {
            LocalOnlyParentRemoteSyncDataSource
        }
    }
}

class FirebaseParentRemoteSyncDataSource(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) : ParentRemoteSyncDataSource {
    private val mutableSyncState = MutableStateFlow(
        ParentRemoteSyncState(
            mode = ParentRemoteSyncMode.Cloud,
            connected = false,
        ),
    )

    override val syncState: StateFlow<ParentRemoteSyncState> = mutableSyncState

    override fun observeChanges(
        childDeviceIds: Set<String>,
        deviceRole: ParentDeviceRole,
    ): Flow<ParentRemoteChange> = callbackFlow {
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
                .orderBy("createdAtMillis", Query.Direction.DESCENDING)
                .limit(REMOTE_LISTENER_DOCUMENT_LIMIT)
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
            val usageRegistration = if (deviceRole == ParentDeviceRole.Parent) {
                usageSnapshotsCollection(childDeviceId).document("current")
                    .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                        if (error != null) {
                            // Older deployed rules may not grant this optional read yet.
                            // Keep request/command listeners alive in that case.
                        } else if (snapshot != null) {
                            updateConnected()
                            trySend(
                                ParentRemoteChange(
                                    childDeviceId = childDeviceId,
                                    type = ParentRemoteChangeType.ChildUsageSnapshot,
                                    fromCache = snapshot.metadata.isFromCache,
                                    hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                                    usageSnapshot = snapshot.toChildUsageSnapshot(childDeviceId),
                                ),
                            )
                        }
                    }
            } else {
                null
            }
            val usageRefreshRegistration = usageRefreshDocument(childDeviceId)
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                    if (error == null && snapshot != null) {
                        updateConnected()
                        trySend(
                            ParentRemoteChange(
                                childDeviceId = childDeviceId,
                                type = ParentRemoteChangeType.ChildUsageRefresh,
                                fromCache = snapshot.metadata.isFromCache,
                                hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                                usageRefreshRequest = snapshot.toChildUsageRefreshRequest(childDeviceId),
                            ),
                        )
                    }
                    // Keep existing listeners alive until the updated optional rules are deployed.
                }
            val immediateBlockRegistration = immediateBlockDocument(childDeviceId)
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                    if (error == null && snapshot != null) {
                        updateConnected()
                        trySend(
                            ParentRemoteChange(
                                childDeviceId = childDeviceId,
                                type = ParentRemoteChangeType.ImmediateBlock,
                                fromCache = snapshot.metadata.isFromCache,
                                hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                                immediateBlockState = snapshot.toImmediateBlockState(childDeviceId),
                                immediateBlockDocumentExists = snapshot.exists(),
                            ),
                        )
                    }
                    // Older rules may not include this document; keep existing listeners alive.
                }
            val commandRegistration = if (deviceRole == ParentDeviceRole.Child) {
                commandsCollection(childDeviceId)
                    .orderBy("timestampMillis", Query.Direction.DESCENDING)
                    .limit(REMOTE_LISTENER_DOCUMENT_LIMIT)
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
            } else {
                null
            }
            listOfNotNull(childRegistration, requestRegistration, usageRegistration,
                usageRefreshRegistration,
                immediateBlockRegistration, commandRegistration)
        }

        awaitClose {
            registrations.forEach { registration -> registration.remove() }
        }
    }

    override suspend fun publishPairingCode(
        pairingCode: String,
        childDeviceId: String,
        childDeviceName: String,
        previousPairingCode: String,
    ): ParentRemoteSyncResult {
        val cleanCode = pairingCode.trim().uppercase()
        val cleanPreviousCode = previousPairingCode.trim().uppercase()
        if (cleanCode.isBlank() || childDeviceId.isBlank()) {
            return ParentRemoteSyncResult.Failed("Pairing code or child device id is blank")
        }
        return runRemote("publish pairing code") {
            val now = System.currentTimeMillis()
            val childUid = currentUid()
            val childRef = childrenCollection().document(childDeviceId)
            val pairingRef = pairingCodesCollection().document(cleanCode)
            childRef.set(
                mapOf(
                    "childDeviceId" to childDeviceId,
                    "childDeviceName" to childDeviceName,
                    "childUid" to childUid,
                    "activePairingCode" to cleanCode,
                    "status" to "active",
                    "updatedAtMillis" to now,
                ),
                SetOptions.merge(),
            ).awaitResult()

            // Release rules inspect these arrays before an unlinked parent may read the
            // child document. Older and newly created child documents may not have them.
            // Add only missing fields so regenerating a code never erases existing parents.
            val childSnapshot = childRef.get().awaitResult()
            val missingRelationFields = buildMap<String, Any> {
                if (!childSnapshot.contains("parentUids")) {
                    put("parentUids", emptyList<String>())
                }
                if (!childSnapshot.contains("linkedParents")) {
                    put("linkedParents", emptyList<Map<String, Any>>())
                }
            }
            if (missingRelationFields.isNotEmpty()) {
                childRef.set(missingRelationFields, SetOptions.merge()).awaitResult()
            }

            pairingRef.set(
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
            ).awaitResult()

            if (cleanPreviousCode.isNotBlank() && cleanPreviousCode != cleanCode) {
                runCatching {
                    pairingCodesCollection()
                        .document(cleanPreviousCode)
                        .delete()
                        .awaitResult()
                }
            }
        }
    }

    override suspend fun resolvePairingCode(pairingCode: String): ParentRemotePairingRecord? {
        return resolvePairingCode(pairingCode, "")
    }

    override suspend fun resolvePairingCode(
        pairingCode: String,
        parentDisplayName: String,
    ): ParentRemotePairingRecord? {
        return when (val result = resolvePairingCodeDetailed(pairingCode, parentDisplayName)) {
            is ParentRemotePairingResolution.Success -> result.record
            else -> null
        }
    }

    override suspend fun resolvePairingCodeDetailed(
        pairingCode: String,
        parentDisplayName: String,
    ): ParentRemotePairingResolution {
        val cleanCode = pairingCode.trim().uppercase()
        if (cleanCode.isBlank()) {
            return ParentRemotePairingResolution.NotFound
        }
        return try {
            ensureSignedIn()
            val now = System.currentTimeMillis()
            val parentUid = currentUid()
            val pairingRef = pairingCodesCollection().document(cleanCode)
            val record = firestore.runTransaction { transaction ->
                val snapshot = transaction.get(pairingRef)
                if (!snapshot.exists()) {
                    return@runTransaction ParentRemotePairingResolution.NotFound
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
                    return@runTransaction ParentRemotePairingResolution.Expired
                }
                if (used && !usedByCurrentParent) {
                    return@runTransaction ParentRemotePairingResolution.AlreadyUsed
                }
                val pairingRecord = snapshot.toPairingRecord()
                    ?: return@runTransaction ParentRemotePairingResolution.NotFound
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
                ParentRemotePairingResolution.Success(pairingRecord)
            }.awaitResult()
            updateConnected()
            record
        } catch (error: Throwable) {
            updateFailed(error)
            ParentRemotePairingResolution.Failed(
                reason = error.message.orEmpty(),
                retryable = error.isRetryableRemoteFailure(),
                kind = error.remoteFailureKind(),
            )
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

    override suspend fun fetchLinkedChildDevicesForCurrentParent(): List<LinkedChildDevice> {
        return try {
            ensureSignedIn()
            val uid = currentUid()
            if (uid.isBlank() || auth.currentUser?.isAnonymous != false) {
                return emptyList()
            }
            val snapshot = childrenCollection()
                .whereArrayContains("parentUids", uid)
                .limit(RECOVERED_CHILD_DOCUMENT_LIMIT)
                .get()
                .awaitResult()
            val children = snapshot.documents.mapNotNull { document ->
                if (!document.exists()) {
                    null
                } else {
                    LinkedChildDevice(
                        childDeviceId = document.id,
                        childDeviceName = document.getString("childDeviceName")
                            .orEmpty()
                            .ifBlank { "Child device" },
                        pairingCode = "",
                        linkedAtMillis = document.getLong("linkedAtMillis") ?: 0L,
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

    override suspend fun publishChildUsageSnapshot(snapshot: ChildUsageSnapshot): ParentRemoteSyncResult {
        if (snapshot.childDeviceId.isBlank()) {
            return ParentRemoteSyncResult.Failed("Child usage snapshot is missing device id")
        }
        return runRemote("publish child usage snapshot") {
            usageSnapshotsCollection(snapshot.childDeviceId).document("current")
                .set(
                    mapOf(
                        "childDeviceId" to snapshot.childDeviceId,
                        "childUid" to currentUid(),
                        "dateKey" to snapshot.dateKey,
                        "capturedAtMillis" to snapshot.capturedAtMillis,
                        "todayUsedMillis" to snapshot.todayUsedMillis,
                        "dailyCountedUsageMillis" to snapshot.dailyCountedUsageMillis,
                        "effectiveDailyLimitMinutes" to snapshot.effectiveDailyLimitMinutes,
                        "dailyUnlockedForToday" to snapshot.dailyUnlockedForToday,
                        "usageAccessReady" to snapshot.usageAccessReady,
                        "protectionPaused" to snapshot.protectionPaused,
                        "appUsageSharingEnabled" to snapshot.appUsageSharingEnabled,
                        "topApps" to snapshot.topApps.take(5).map { app ->
                            mapOf(
                                "appName" to app.appName,
                                "usedMillis" to app.usedMillis,
                            )
                        },
                    ),
                )
                .awaitResult()
        }
    }

    override suspend fun fetchChildUsageSnapshot(childDeviceId: String): ChildUsageSnapshot? {
        if (childDeviceId.isBlank()) return null
        return try {
            ensureSignedIn()
            val document = usageSnapshotsCollection(childDeviceId).document("current")
                .get()
                .awaitResult()
            updateConnected()
            document.toChildUsageSnapshot(childDeviceId)
        } catch (error: Throwable) {
            updateFailed(error)
            null
        }
    }

    override suspend fun requestChildUsageRefresh(
        childDeviceId: String,
    ): Result<ChildUsageRefreshRequest> = runCatching {
        require(childDeviceId.isNotBlank()) { "Child device id is missing" }
        ensureSignedIn()
        val ref = usageRefreshDocument(childDeviceId)
        val request = firestore.runTransaction { transaction ->
            val now = System.currentTimeMillis()
            val current = transaction.get(ref).toChildUsageRefreshRequest(childDeviceId)
            if (current != null && !current.canRequestAgain(now)) {
                current
            } else {
                ChildUsageRefreshRequest(
                    requestId = java.util.UUID.randomUUID().toString(),
                    childDeviceId = childDeviceId,
                    parentUid = currentUid(),
                    requestedAtMillis = now,
                    expiresAtMillis = now + 30 * 60_000L,
                ).also { next ->
                    transaction.set(ref, next.toRemoteMap())
                }
            }
        }.awaitResult()
        updateConnected()
        request
    }.onFailure(::updateFailed)

    override suspend fun fetchChildUsageRefresh(
        childDeviceId: String,
    ): Result<ChildUsageRefreshRequest?> = runCatching {
        require(childDeviceId.isNotBlank()) { "Child device id is missing" }
        ensureSignedIn()
        val request = usageRefreshDocument(childDeviceId).get(Source.SERVER).awaitResult()
            .toChildUsageRefreshRequest(childDeviceId)
        updateConnected()
        request
    }.onFailure(::updateFailed)

    override suspend fun acknowledgeChildUsageRefresh(
        childDeviceId: String,
        requestId: String,
    ): ParentRemoteSyncResult = runRemote("acknowledge child usage refresh") {
        val ref = usageRefreshDocument(childDeviceId)
        firestore.runTransaction { transaction ->
            val current = transaction.get(ref).toChildUsageRefreshRequest(childDeviceId)
            if (current?.requestId == requestId && current.isPending(System.currentTimeMillis())) {
                transaction.update(ref, "completedAtMillis", System.currentTimeMillis())
            }
        }.awaitResult()
    }

    override suspend fun issueImmediateBlock(
        childDeviceId: String,
        durationMinutes: Int,
    ): ParentRemoteSyncResult {
        if (childDeviceId.isBlank() || durationMinutes !in 1..1440) {
            return ParentRemoteSyncResult.Failed("Invalid immediate block target or duration")
        }
        return runRemote("issue immediate block") {
            val now = System.currentTimeMillis()
            immediateBlockDocument(childDeviceId).set(
                mapOf(
                    "requestId" to java.util.UUID.randomUUID().toString(),
                    "childDeviceId" to childDeviceId,
                    "parentUid" to currentUid(),
                    "requestedAtMillis" to now,
                    "expiresAtMillis" to now + durationMinutes * 60_000L,
                    "revokedAtMillis" to 0L,
                    "appliedAtMillis" to 0L,
                    "releasedAtMillis" to 0L,
                ),
            ).awaitResult()
        }
    }

    override suspend fun revokeImmediateBlock(
        childDeviceId: String,
        requestId: String,
    ): ParentRemoteSyncResult = runRemote("revoke immediate block") {
        val ref = immediateBlockDocument(childDeviceId)
        firestore.runTransaction { transaction ->
            val current = transaction.get(ref)
            if (current.getString("requestId") != requestId) {
                throw IllegalStateException("Immediate block changed before revocation")
            }
            transaction.update(ref, "revokedAtMillis", System.currentTimeMillis())
        }.awaitResult()
    }

    override suspend fun fetchImmediateBlock(childDeviceId: String): Result<ImmediateBlockState?> =
        runCatching {
            ensureSignedIn()
            val snapshot = immediateBlockDocument(childDeviceId).get(Source.SERVER).awaitResult()
            updateConnected()
            snapshot.toImmediateBlockState(childDeviceId).also { block ->
                if (snapshot.exists() && block == null) {
                    throw IllegalStateException("Invalid immediate block document")
                }
            }
        }.onFailure(::updateFailed)

    override suspend fun acknowledgeImmediateBlock(
        childDeviceId: String,
        requestId: String,
    ): ParentRemoteSyncResult = runRemote("acknowledge immediate block") {
        val ref = immediateBlockDocument(childDeviceId)
        firestore.runTransaction { transaction ->
            val current = transaction.get(ref)
            if (current.getString("requestId") != requestId ||
                (current.getLong("revokedAtMillis") ?: 0L) != 0L ||
                (current.getLong("expiresAtMillis") ?: 0L) <= System.currentTimeMillis()
            ) {
                return@runTransaction null
            }
            if ((current.getLong("appliedAtMillis") ?: 0L) == 0L) {
                transaction.update(ref, "appliedAtMillis", System.currentTimeMillis())
            }
        }.awaitResult()
    }

    override suspend fun acknowledgeImmediateBlockRelease(
        childDeviceId: String,
        requestId: String,
    ): ParentRemoteSyncResult = runRemote("acknowledge immediate block release") {
        val ref = immediateBlockDocument(childDeviceId)
        firestore.runTransaction { transaction ->
            val current = transaction.get(ref)
            if (current.getString("requestId") != requestId ||
                (current.getLong("revokedAtMillis") ?: 0L) == 0L
            ) return@runTransaction null
            if ((current.getLong("releasedAtMillis") ?: 0L) == 0L) {
                transaction.update(ref, "releasedAtMillis", System.currentTimeMillis())
            }
        }.awaitResult()
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
                if (decision.approved) {
                    transaction.set(
                        commandsCollection(request.childDeviceId).document(command.id),
                        command.toRemoteMap() + mapOf("parentUid" to parentUid),
                        SetOptions.merge(),
                    )
                }
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
                .limit(REMOTE_QUERY_DOCUMENT_LIMIT)
                .get()
                .awaitResult()
            updateConnected()
            snapshot.documents.mapNotNull { document -> document.toRemoteUnlockRequest() }
        } catch (error: Throwable) {
            updateFailed(error)
            emptyList()
        }
    }

    override suspend fun fetchChildRequest(
        childDeviceId: String,
        requestId: String,
    ): RemoteUnlockRequest? {
        if (childDeviceId.isBlank() || requestId.isBlank()) {
            return null
        }
        return try {
            ensureSignedIn()
            val snapshot = requestsCollection(childDeviceId)
                .document(requestId)
                .get()
                .awaitResult()
            updateConnected()
            snapshot.toRemoteUnlockRequest()
        } catch (error: Throwable) {
            updateFailed(error)
            null
        }
    }

    override suspend fun fetchChildCommand(
        childDeviceId: String,
        commandId: String,
    ): RemoteParentCommand? {
        if (childDeviceId.isBlank() || commandId.isBlank()) {
            return null
        }
        return try {
            ensureSignedIn()
            val snapshot = commandsCollection(childDeviceId)
                .document(commandId)
                .get()
                .awaitResult()
            updateConnected()
            snapshot.toRemoteParentCommand()
        } catch (error: Throwable) {
            updateFailed(error)
            null
        }
    }

    override suspend fun fetchChildCommands(
        childDeviceId: String,
        afterTimestampMillis: Long,
    ): List<RemoteParentCommand> {
        if (childDeviceId.isBlank()) {
            return emptyList()
        }
        return try {
            ensureSignedIn()
            val baseQuery = commandsCollection(childDeviceId)
                .orderBy(
                    "timestampMillis",
                    if (afterTimestampMillis > 0L) Query.Direction.ASCENDING else Query.Direction.DESCENDING,
                )
            val query = if (afterTimestampMillis > 0L) {
                baseQuery.whereGreaterThanOrEqualTo("timestampMillis", afterTimestampMillis)
            } else {
                baseQuery
            }
            val snapshot = query
                .limit(REMOTE_QUERY_DOCUMENT_LIMIT)
                .get()
                .awaitResult()
            updateConnected()
            snapshot.documents.mapNotNull { document -> document.toRemoteParentCommand() }
        } catch (error: Throwable) {
            updateFailed(error)
            emptyList()
        }
    }

    override suspend fun cleanupExpiredRemoteData(
        childDeviceId: String,
        olderThanMillis: Long,
    ): ParentRemoteSyncResult {
        if (childDeviceId.isBlank() || olderThanMillis <= 0L) {
            return ParentRemoteSyncResult.Failed("Remote cleanup target is invalid")
        }
        return try {
            ensureSignedIn()
            val expiredRequests = requestsCollection(childDeviceId)
                .whereLessThanOrEqualTo("createdAtMillis", olderThanMillis)
                .orderBy("createdAtMillis", Query.Direction.ASCENDING)
                .limit(REMOTE_CLEANUP_DOCUMENT_LIMIT)
                .get()
                .awaitResult()
            val expiredCommands = commandsCollection(childDeviceId)
                .whereLessThanOrEqualTo("timestampMillis", olderThanMillis)
                .orderBy("timestampMillis", Query.Direction.ASCENDING)
                .limit(REMOTE_CLEANUP_DOCUMENT_LIMIT)
                .get()
                .awaitResult()
            val documents = expiredRequests.documents + expiredCommands.documents
            if (documents.isNotEmpty()) {
                val batch = firestore.batch()
                documents.forEach { document -> batch.delete(document.reference) }
                batch.commit().awaitResult()
            }
            ParentRemoteSyncResult.Success
        } catch (error: Throwable) {
            // Retention is best-effort maintenance. A missing release rule or a
            // temporary network failure must not make normal parent sync appear offline.
            ParentRemoteSyncResult.Failed(
                reason = "remote cleanup failed: ${error.message.orEmpty()}",
                retryable = error.isRetryableRemoteFailure(),
                kind = error.remoteFailureKind(),
            )
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

    override suspend fun synchronizePushToken(
        registrationId: String,
        token: String,
        desiredTargets: Set<RemotePushTokenTarget>,
        obsoleteTargets: Set<RemotePushTokenTarget>,
    ): ParentRemoteSyncResult {
        if (registrationId.isBlank() || token.isBlank()) {
            return ParentRemoteSyncResult.Failed("Push registration id or token is blank")
        }
        if (desiredTargets.isEmpty() && obsoleteTargets.isEmpty()) {
            return ParentRemoteSyncResult.Success
        }
        return runRemote("synchronize push token") {
            val uid = currentUid()
            val tokenDocumentId = pushTokenDocumentId(uid, registrationId)
            val batch = firestore.batch()
            val desiredChildIds = desiredTargets.mapTo(hashSetOf()) { target -> target.childDeviceId }
            obsoleteTargets
                .filterNot { target -> target.childDeviceId in desiredChildIds }
                .forEach { target ->
                if (target.childDeviceId.isNotBlank()) {
                    batch.delete(
                        pushTokensCollection(target.childDeviceId).document(tokenDocumentId),
                    )
                }
            }
            val now = System.currentTimeMillis()
            desiredTargets.forEach { target ->
                if (target.childDeviceId.isNotBlank()) {
                    batch.set(
                        pushTokensCollection(target.childDeviceId).document(tokenDocumentId),
                        mapOf(
                            "uid" to uid,
                            "token" to token,
                            "role" to target.role.storageValue,
                            "platform" to "android",
                            "updatedAtMillis" to now,
                        ),
                        SetOptions.merge(),
                    )
                }
            }
            batch.commit().awaitResult()
        }
    }

    override suspend fun deleteCurrentUserCloudData(): ParentRemoteSyncResult {
        return try {
            ensureSignedIn()
            functions.getHttpsCallable("deleteCurrentUserData")
                .call(emptyMap<String, Any>())
                .awaitResult()
            auth.signOut()
            updateConnected()
            ParentRemoteSyncResult.Success
        } catch (error: Throwable) {
            updateFailed(error)
            ParentRemoteSyncResult.Failed(
                reason = "delete current user data failed: ${error.message.orEmpty()}",
                retryable = error.isRetryableRemoteFailure(),
                kind = error.remoteFailureKind(),
            )
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
            ParentRemoteSyncResult.Failed(
                reason = "$action failed: ${error.message.orEmpty()}",
                retryable = error.isRetryableRemoteFailure(),
                kind = error.remoteFailureKind(),
            )
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

    private fun usageSnapshotsCollection(childDeviceId: String) =
        childrenCollection().document(childDeviceId).collection("usage_snapshots")

    private fun usageRefreshDocument(childDeviceId: String) =
        childrenCollection().document(childDeviceId).collection("usage_refresh").document("current")

    private fun immediateBlockDocument(childDeviceId: String) =
        childrenCollection().document(childDeviceId).collection("immediate_block").document("current")

    private fun commandsCollection(childDeviceId: String) =
        firestore.collection("screenrest_children")
            .document(childDeviceId)
            .collection("remote_commands")

    private fun pushTokensCollection(childDeviceId: String) =
        firestore.collection("screenrest_children")
            .document(childDeviceId)
            .collection("push_tokens")
}

private fun pushTokenDocumentId(uid: String, registrationId: String): String {
    val bytes = MessageDigest.getInstance("SHA-256")
        .digest("$uid:$registrationId".toByteArray(Charsets.UTF_8))
    return bytes.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private fun Throwable.remoteFailureKind(): ParentRemoteFailureKind {
    return when (this) {
        is FirebaseNetworkException,
        is IOException -> ParentRemoteFailureKind.Network

        is FirebaseAuthException -> ParentRemoteFailureKind.Authentication

        is FirebaseFirestoreException -> when (code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> ParentRemoteFailureKind.PermissionDenied
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> ParentRemoteFailureKind.Authentication
            FirebaseFirestoreException.Code.NOT_FOUND -> ParentRemoteFailureKind.NotFound
            FirebaseFirestoreException.Code.ABORTED,
            FirebaseFirestoreException.Code.CANCELLED,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            FirebaseFirestoreException.Code.INTERNAL,
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.UNKNOWN -> ParentRemoteFailureKind.Network
            else -> ParentRemoteFailureKind.Unknown
        }

        else -> cause
            ?.takeIf { causeError -> causeError !== this }
            ?.remoteFailureKind()
            ?: ParentRemoteFailureKind.Unknown
    }
}

private fun Throwable.isRetryableRemoteFailure(): Boolean {
    return when (this) {
        is FirebaseNetworkException,
        is IOException -> true

        is FirebaseFirestoreException -> code in setOf(
            FirebaseFirestoreException.Code.ABORTED,
            FirebaseFirestoreException.Code.CANCELLED,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            FirebaseFirestoreException.Code.INTERNAL,
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.UNKNOWN,
        )

        else -> cause?.takeIf { causeError -> causeError !== this }?.isRetryableRemoteFailure() == true
    }
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

private fun DocumentSnapshot.toChildUsageSnapshot(expectedChildDeviceId: String): ChildUsageSnapshot? {
    if (!exists() || getString("childDeviceId") != expectedChildDeviceId) return null
    val dateKey = getString("dateKey") ?: return null
    val capturedAtMillis = getLong("capturedAtMillis") ?: return null
    val todayUsedMillis = getLong("todayUsedMillis") ?: return null
    val dailyCountedUsageMillis = getLong("dailyCountedUsageMillis") ?: return null
    if (dateKey.length != 10 || capturedAtMillis <= 0L || todayUsedMillis < 0L ||
        dailyCountedUsageMillis < 0L || dailyCountedUsageMillis > todayUsedMillis
    ) return null
    val appUsageSharingEnabled = getBoolean("appUsageSharingEnabled") ?: false
    return ChildUsageSnapshot(
        childDeviceId = expectedChildDeviceId,
        dateKey = dateKey,
        capturedAtMillis = capturedAtMillis,
        todayUsedMillis = todayUsedMillis,
        dailyCountedUsageMillis = dailyCountedUsageMillis,
        effectiveDailyLimitMinutes = getLong("effectiveDailyLimitMinutes")?.toInt(),
        dailyUnlockedForToday = getBoolean("dailyUnlockedForToday") ?: false,
        usageAccessReady = getBoolean("usageAccessReady") ?: false,
        protectionPaused = getBoolean("protectionPaused") ?: false,
        appUsageSharingEnabled = appUsageSharingEnabled,
        topApps = (if (appUsageSharingEnabled) get("topApps") as? List<*> else null)
            ?.take(5)?.mapNotNull { item ->
            val app = item as? Map<*, *> ?: return@mapNotNull null
            val appName = (app["appName"] as? String)?.trim().orEmpty()
            val usedMillis = (app["usedMillis"] as? Number)?.toLong() ?: 0L
            if (appName.isBlank() || appName.length > 80 ||
                usedMillis <= 0L || usedMillis > todayUsedMillis
            ) null else ChildTopAppUsage(appName, usedMillis)
        }.orEmpty(),
    )
}

private fun ChildUsageRefreshRequest.toRemoteMap(): Map<String, Any> = mapOf(
    "requestId" to requestId,
    "childDeviceId" to childDeviceId,
    "parentUid" to parentUid,
    "requestedAtMillis" to requestedAtMillis,
    "expiresAtMillis" to expiresAtMillis,
    "completedAtMillis" to completedAtMillis,
)

private fun DocumentSnapshot.toChildUsageRefreshRequest(
    expectedChildDeviceId: String,
): ChildUsageRefreshRequest? {
    if (!exists() || getString("childDeviceId") != expectedChildDeviceId) return null
    val requestId = getString("requestId") ?: return null
    val parentUid = getString("parentUid") ?: return null
    val requestedAtMillis = getLong("requestedAtMillis") ?: return null
    val expiresAtMillis = getLong("expiresAtMillis") ?: return null
    val completedAtMillis = getLong("completedAtMillis") ?: return null
    if (requestId.isBlank() || parentUid.isBlank() || requestedAtMillis <= 0L ||
        expiresAtMillis <= requestedAtMillis ||
        expiresAtMillis - requestedAtMillis > 30 * 60_000L ||
        completedAtMillis < 0L
    ) return null
    return ChildUsageRefreshRequest(
        requestId = requestId,
        childDeviceId = expectedChildDeviceId,
        parentUid = parentUid,
        requestedAtMillis = requestedAtMillis,
        expiresAtMillis = expiresAtMillis,
        completedAtMillis = completedAtMillis,
    )
}

private fun DocumentSnapshot.toImmediateBlockState(expectedChildDeviceId: String): ImmediateBlockState? {
    if (!exists() || getString("childDeviceId") != expectedChildDeviceId) return null
    val requestId = getString("requestId") ?: return null
    val parentUid = getString("parentUid") ?: return null
    val requestedAt = getLong("requestedAtMillis") ?: return null
    val expiresAt = getLong("expiresAtMillis") ?: return null
    val revokedAt = getLong("revokedAtMillis") ?: return null
    val appliedAt = getLong("appliedAtMillis") ?: return null
    val releasedAt = getLong("releasedAtMillis") ?: return null
    if (requestId.isBlank() || parentUid.isBlank() || requestedAt <= 0L ||
        expiresAt <= requestedAt || expiresAt - requestedAt > 24L * 60L * 60_000L ||
        revokedAt < 0L || appliedAt < 0L || releasedAt < 0L
    ) return null
    return ImmediateBlockState(
        requestId = requestId,
        childDeviceId = expectedChildDeviceId,
        requestedAtMillis = requestedAt,
        expiresAtMillis = expiresAt,
        revokedAtMillis = revokedAt,
        parentUid = parentUid,
        appliedAtMillis = appliedAt,
        releasedAtMillis = releasedAt,
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
private const val REMOTE_LISTENER_DOCUMENT_LIMIT = 30L
private const val REMOTE_QUERY_DOCUMENT_LIMIT = 30L
private const val REMOTE_CLEANUP_DOCUMENT_LIMIT = 20L
private const val RECOVERED_CHILD_DOCUMENT_LIMIT = 100L
