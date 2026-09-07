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
    UnlockRequests,
    RemoteCommands,
}

data class ParentRemoteChange(
    val childDeviceId: String,
    val type: ParentRemoteChangeType,
    val fromCache: Boolean,
    val hasPendingWrites: Boolean,
)

enum class RemotePushTokenRole(val storageValue: String) {
    Child("child"),
    Parent("parent"),
}

data class RemotePushTokenTarget(
    val childDeviceId: String,
    val role: RemotePushTokenRole,
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
