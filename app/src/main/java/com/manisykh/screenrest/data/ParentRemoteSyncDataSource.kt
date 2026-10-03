package com.manisykh.screenrest.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

enum class ParentRemoteSyncMode {
    LocalOnly,
    Cloud,
}

data class ParentRemoteSyncState(
    val mode: ParentRemoteSyncMode = ParentRemoteSyncMode.LocalOnly,
    val connected: Boolean = false,
    val lastSyncMillis: Long = 0L,
    val lastError: String = "",
)

sealed class ParentRemoteSyncResult {
    object Success : ParentRemoteSyncResult()
    object LocalOnly : ParentRemoteSyncResult()
    data class Failed(
        val reason: String,
        val retryable: Boolean = false,
        val kind: ParentRemoteFailureKind = ParentRemoteFailureKind.Unknown,
    ) : ParentRemoteSyncResult()
}

enum class ParentRemoteFailureKind {
    Network,
    PermissionDenied,
    Authentication,
    NotFound,
    Unknown,
}

sealed class ParentRemotePairingResolution {
    data class Success(val record: ParentRemotePairingRecord) : ParentRemotePairingResolution()
    object NotFound : ParentRemotePairingResolution()
    object Expired : ParentRemotePairingResolution()
    object AlreadyUsed : ParentRemotePairingResolution()
    object CloudUnavailable : ParentRemotePairingResolution()
    data class Failed(
        val reason: String,
        val retryable: Boolean,
        val kind: ParentRemoteFailureKind,
    ) : ParentRemotePairingResolution()
}

enum class PairingOperationFailure {
    InvalidAdminPin,
    InvalidCode,
    ExpiredCode,
    AlreadyUsedCode,
    CloudUnavailable,
    Network,
    PermissionDenied,
    Authentication,
    Unknown,
}

data class PairingOperationResult(
    val success: Boolean,
    val failure: PairingOperationFailure? = null,
) {
    companion object {
        val Success = PairingOperationResult(success = true)

        fun failed(failure: PairingOperationFailure) = PairingOperationResult(
            success = false,
            failure = failure,
        )
    }
}

data class ParentRemoteUnlockDecision(
    val requestId: String,
    val approved: Boolean,
    val extraMinutes: Int = 0,
    val unlockForToday: Boolean = false,
    val decidedAtMillis: Long = System.currentTimeMillis(),
)

enum class ParentRemoteChangeType {
    ChildProfile,
    ChildUsageSnapshot,
    ChildUsageRefresh,
    ImmediateBlock,
    UnlockRequests,
    RemoteCommands,
}

/** A replaceable daily snapshot; detailed app activity stays on the child device. */
data class ChildTopAppUsage(
    val appName: String,
    val usedMillis: Long,
)

data class ChildUsageSnapshot(
    val childDeviceId: String,
    val dateKey: String,
    val capturedAtMillis: Long,
    val todayUsedMillis: Long,
    val dailyCountedUsageMillis: Long,
    val effectiveDailyLimitMinutes: Int?,
    val dailyUnlockedForToday: Boolean,
    val usageAccessReady: Boolean,
    val protectionPaused: Boolean,
    val appUsageSharingEnabled: Boolean = false,
    val topApps: List<ChildTopAppUsage> = emptyList(),
) {
    fun isDelayed(nowMillis: Long, maxAgeMillis: Long = 75 * 60_000L): Boolean =
        capturedAtMillis <= 0L ||
            capturedAtMillis > nowMillis + 5 * 60_000L ||
            nowMillis - capturedAtMillis > maxAgeMillis

    fun remainingDailyMinutes(): Int? {
        if (!usageAccessReady || protectionPaused || dailyUnlockedForToday) return null
        val limit = effectiveDailyLimitMinutes ?: return null
        val countedMinutes = (dailyCountedUsageMillis / 60_000L).toInt()
        return (limit - countedMinutes).coerceAtLeast(0)
    }
}

/** One replaceable request per child; completion is acknowledged only after a new snapshot is saved. */
data class ChildUsageRefreshRequest(
    val requestId: String,
    val childDeviceId: String,
    val parentUid: String,
    val requestedAtMillis: Long,
    val expiresAtMillis: Long,
    val completedAtMillis: Long = 0L,
) {
    fun isPending(nowMillis: Long): Boolean =
        completedAtMillis == 0L && expiresAtMillis > nowMillis

    fun isDelayed(nowMillis: Long, thresholdMillis: Long = 2 * 60_000L): Boolean =
        isPending(nowMillis) && nowMillis - requestedAtMillis >= thresholdMillis

    fun canRequestAgain(nowMillis: Long): Boolean =
        nowMillis < requestedAtMillis || nowMillis - requestedAtMillis >= 60_000L
}

data class ParentRemoteChange(
    val childDeviceId: String,
    val type: ParentRemoteChangeType,
    val fromCache: Boolean,
    val hasPendingWrites: Boolean,
    val usageSnapshot: ChildUsageSnapshot? = null,
    val usageRefreshRequest: ChildUsageRefreshRequest? = null,
    val immediateBlockState: ImmediateBlockState? = null,
    val immediateBlockDocumentExists: Boolean = false,
)

enum class RemotePushTokenRole(val storageValue: String) {
    Child("child"),
    Parent("parent"),
}

data class RemotePushTokenTarget(
    val childDeviceId: String,
    val role: RemotePushTokenRole,
)

data class ParentAccountRecoverySnapshot(
    val childDevices: List<LinkedChildDevice> = emptyList(),
    val parentDisplayName: String = "",
)

interface ParentRemoteSyncDataSource {
    val syncState: StateFlow<ParentRemoteSyncState>

    fun observeChanges(
        childDeviceIds: Set<String>,
        deviceRole: ParentDeviceRole,
    ): Flow<ParentRemoteChange>

    suspend fun publishPairingCode(
        pairingCode: String,
        childDeviceId: String,
        childDeviceName: String,
        previousPairingCode: String = "",
    ): ParentRemoteSyncResult

    suspend fun resolvePairingCode(pairingCode: String): ParentRemotePairingRecord?

    suspend fun resolvePairingCode(
        pairingCode: String,
        parentDisplayName: String,
    ): ParentRemotePairingRecord? {
        return resolvePairingCode(pairingCode)
    }

    suspend fun resolvePairingCodeDetailed(
        pairingCode: String,
        parentDisplayName: String,
    ): ParentRemotePairingResolution {
        val record = resolvePairingCode(pairingCode, parentDisplayName)
        return if (record == null) {
            ParentRemotePairingResolution.NotFound
        } else {
            ParentRemotePairingResolution.Success(record)
        }
    }

    suspend fun publishUnlockRequest(request: RemoteUnlockRequest): ParentRemoteSyncResult

    suspend fun publishChildUsageSnapshot(snapshot: ChildUsageSnapshot): ParentRemoteSyncResult

    suspend fun fetchChildUsageSnapshot(childDeviceId: String): ChildUsageSnapshot?

    suspend fun requestChildUsageRefresh(childDeviceId: String): Result<ChildUsageRefreshRequest>

    suspend fun fetchChildUsageRefresh(childDeviceId: String): Result<ChildUsageRefreshRequest?>

    suspend fun acknowledgeChildUsageRefresh(childDeviceId: String, requestId: String): ParentRemoteSyncResult

    suspend fun issueImmediateBlock(childDeviceId: String, durationMinutes: Int): ParentRemoteSyncResult

    suspend fun revokeImmediateBlock(childDeviceId: String, requestId: String): ParentRemoteSyncResult

    suspend fun fetchImmediateBlock(childDeviceId: String): Result<ImmediateBlockState?>

    suspend fun acknowledgeImmediateBlock(childDeviceId: String, requestId: String): ParentRemoteSyncResult

    suspend fun acknowledgeImmediateBlockRelease(childDeviceId: String, requestId: String): ParentRemoteSyncResult

    suspend fun publishUnlockDecision(
        request: RemoteUnlockRequest,
        decision: ParentRemoteUnlockDecision,
    ): ParentRemoteSyncResult

    suspend fun fetchChildRequests(
        parentAccountId: String,
        childDeviceId: String,
    ): List<RemoteUnlockRequest>

    suspend fun fetchChildRequest(
        childDeviceId: String,
        requestId: String,
    ): RemoteUnlockRequest?

    suspend fun fetchChildCommand(
        childDeviceId: String,
        commandId: String,
    ): RemoteParentCommand?

    suspend fun fetchChildCommands(
        childDeviceId: String,
        afterTimestampMillis: Long = 0L,
    ): List<RemoteParentCommand>

    suspend fun cleanupExpiredRemoteData(
        childDeviceId: String,
        olderThanMillis: Long,
    ): ParentRemoteSyncResult

    suspend fun fetchLinkedParents(childDeviceId: String): List<LinkedParentDevice>

    suspend fun fetchLinkedChildDevices(childDeviceIds: List<String>): List<LinkedChildDevice>

    suspend fun fetchLinkedChildDevicesForCurrentParent(): List<LinkedChildDevice>

    suspend fun fetchParentAccountRecovery(): ParentAccountRecoverySnapshot =
        ParentAccountRecoverySnapshot(
            childDevices = fetchLinkedChildDevicesForCurrentParent(),
        )

    suspend fun updateChildProfile(
        childDeviceId: String,
        childDeviceName: String,
        pairingCode: String,
    ): ParentRemoteSyncResult

    suspend fun updateParentProfile(
        childDeviceIds: List<String>,
        parentDisplayName: String,
    ): ParentRemoteSyncResult

    suspend fun unlinkChild(
        childDeviceId: String,
        parentAccountId: String,
    ): ParentRemoteSyncResult

    suspend fun unlinkParentFromChild(
        childDeviceId: String,
        parentUid: String,
    ): ParentRemoteSyncResult

    suspend fun synchronizePushToken(
        registrationId: String,
        token: String,
        desiredTargets: Set<RemotePushTokenTarget>,
        obsoleteTargets: Set<RemotePushTokenTarget>,
    ): ParentRemoteSyncResult

    suspend fun deleteCurrentUserCloudData(): ParentRemoteSyncResult
}

object LocalOnlyParentRemoteSyncDataSource : ParentRemoteSyncDataSource {
    override val syncState: StateFlow<ParentRemoteSyncState> = MutableStateFlow(
        ParentRemoteSyncState(
            mode = ParentRemoteSyncMode.LocalOnly,
            connected = false,
            lastError = "Cloud configuration missing",
        ),
    )

    override fun observeChanges(
        childDeviceIds: Set<String>,
        deviceRole: ParentDeviceRole,
    ): Flow<ParentRemoteChange> = emptyFlow()

    override suspend fun publishPairingCode(
        pairingCode: String,
        childDeviceId: String,
        childDeviceName: String,
        previousPairingCode: String,
    ): ParentRemoteSyncResult {
        return ParentRemoteSyncResult.LocalOnly
    }

    override suspend fun resolvePairingCode(pairingCode: String): ParentRemotePairingRecord? {
        return null
    }

    override suspend fun resolvePairingCodeDetailed(
        pairingCode: String,
        parentDisplayName: String,
    ): ParentRemotePairingResolution = ParentRemotePairingResolution.CloudUnavailable

    override suspend fun publishUnlockRequest(request: RemoteUnlockRequest): ParentRemoteSyncResult {
        return ParentRemoteSyncResult.LocalOnly
    }

    override suspend fun publishChildUsageSnapshot(snapshot: ChildUsageSnapshot): ParentRemoteSyncResult =
        ParentRemoteSyncResult.LocalOnly

    override suspend fun fetchChildUsageSnapshot(childDeviceId: String): ChildUsageSnapshot? = null

    override suspend fun requestChildUsageRefresh(childDeviceId: String): Result<ChildUsageRefreshRequest> =
        Result.failure(IllegalStateException("Cloud configuration missing"))

    override suspend fun fetchChildUsageRefresh(childDeviceId: String): Result<ChildUsageRefreshRequest?> =
        Result.failure(IllegalStateException("Cloud configuration missing"))

    override suspend fun acknowledgeChildUsageRefresh(childDeviceId: String, requestId: String) =
        ParentRemoteSyncResult.LocalOnly

    override suspend fun issueImmediateBlock(childDeviceId: String, durationMinutes: Int) =
        ParentRemoteSyncResult.LocalOnly

    override suspend fun revokeImmediateBlock(childDeviceId: String, requestId: String) =
        ParentRemoteSyncResult.LocalOnly

    override suspend fun fetchImmediateBlock(childDeviceId: String): Result<ImmediateBlockState?> =
        Result.failure(IllegalStateException("Cloud configuration missing"))

    override suspend fun acknowledgeImmediateBlock(childDeviceId: String, requestId: String) =
        ParentRemoteSyncResult.LocalOnly

    override suspend fun acknowledgeImmediateBlockRelease(childDeviceId: String, requestId: String) =
        ParentRemoteSyncResult.LocalOnly

    override suspend fun publishUnlockDecision(
        request: RemoteUnlockRequest,
        decision: ParentRemoteUnlockDecision,
    ): ParentRemoteSyncResult {
        return ParentRemoteSyncResult.LocalOnly
    }

    override suspend fun fetchChildRequests(
        parentAccountId: String,
        childDeviceId: String,
    ): List<RemoteUnlockRequest> {
        return emptyList()
    }

    override suspend fun fetchChildRequest(
        childDeviceId: String,
        requestId: String,
    ): RemoteUnlockRequest? {
        return null
    }

    override suspend fun fetchChildCommand(
        childDeviceId: String,
        commandId: String,
    ): RemoteParentCommand? {
        return null
    }

    override suspend fun fetchChildCommands(
        childDeviceId: String,
        afterTimestampMillis: Long,
    ): List<RemoteParentCommand> {
        return emptyList()
    }

    override suspend fun cleanupExpiredRemoteData(
        childDeviceId: String,
        olderThanMillis: Long,
    ): ParentRemoteSyncResult {
        return ParentRemoteSyncResult.LocalOnly
    }

    override suspend fun fetchLinkedParents(childDeviceId: String): List<LinkedParentDevice> {
        return emptyList()
    }

    override suspend fun fetchLinkedChildDevices(childDeviceIds: List<String>): List<LinkedChildDevice> {
        return emptyList()
    }

    override suspend fun fetchLinkedChildDevicesForCurrentParent(): List<LinkedChildDevice> {
        return emptyList()
    }

    override suspend fun updateChildProfile(
        childDeviceId: String,
        childDeviceName: String,
        pairingCode: String,
    ): ParentRemoteSyncResult {
        return ParentRemoteSyncResult.LocalOnly
    }

    override suspend fun updateParentProfile(
        childDeviceIds: List<String>,
        parentDisplayName: String,
    ): ParentRemoteSyncResult {
        return ParentRemoteSyncResult.LocalOnly
    }

    override suspend fun unlinkChild(
        childDeviceId: String,
        parentAccountId: String,
    ): ParentRemoteSyncResult {
        return ParentRemoteSyncResult.LocalOnly
    }

    override suspend fun unlinkParentFromChild(
        childDeviceId: String,
        parentUid: String,
    ): ParentRemoteSyncResult {
        return ParentRemoteSyncResult.LocalOnly
    }

    override suspend fun synchronizePushToken(
        registrationId: String,
        token: String,
        desiredTargets: Set<RemotePushTokenTarget>,
        obsoleteTargets: Set<RemotePushTokenTarget>,
    ): ParentRemoteSyncResult = ParentRemoteSyncResult.LocalOnly

    override suspend fun deleteCurrentUserCloudData(): ParentRemoteSyncResult =
        ParentRemoteSyncResult.LocalOnly
}
