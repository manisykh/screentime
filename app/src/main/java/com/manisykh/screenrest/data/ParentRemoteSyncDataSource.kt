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
    data class Failed(val reason: String) : ParentRemoteSyncResult()
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

interface ParentRemoteSyncDataSource {
    val syncState: StateFlow<ParentRemoteSyncState>

    fun observeChanges(childDeviceIds: Set<String>): Flow<ParentRemoteChange>

    suspend fun publishPairingCode(
        pairingCode: String,
        childDeviceId: String,
        childDeviceName: String,
    ): ParentRemoteSyncResult

    suspend fun resolvePairingCode(pairingCode: String): ParentRemotePairingRecord?

    suspend fun resolvePairingCode(
        pairingCode: String,
        parentDisplayName: String,
    ): ParentRemotePairingRecord? {
        return resolvePairingCode(pairingCode)
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

    suspend fun fetchChildCommands(childDeviceId: String): List<RemoteParentCommand>

    suspend fun fetchLinkedParents(childDeviceId: String): List<LinkedParentDevice>

    suspend fun fetchLinkedChildDevices(childDeviceIds: List<String>): List<LinkedChildDevice>

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
}

object LocalOnlyParentRemoteSyncDataSource : ParentRemoteSyncDataSource {
    override val syncState: StateFlow<ParentRemoteSyncState> = MutableStateFlow(
        ParentRemoteSyncState(
            mode = ParentRemoteSyncMode.LocalOnly,
            connected = false,
            lastError = "Cloud configuration missing",
        ),
    )

    override fun observeChanges(childDeviceIds: Set<String>): Flow<ParentRemoteChange> = emptyFlow()

    override suspend fun publishPairingCode(
        pairingCode: String,
        childDeviceId: String,
        childDeviceName: String,
    ): ParentRemoteSyncResult {
        return ParentRemoteSyncResult.LocalOnly
    }

    override suspend fun resolvePairingCode(pairingCode: String): ParentRemotePairingRecord? {
        return null
    }

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

    override suspend fun fetchChildCommands(childDeviceId: String): List<RemoteParentCommand> {
        return emptyList()
    }

    override suspend fun fetchLinkedParents(childDeviceId: String): List<LinkedParentDevice> {
        return emptyList()
    }

    override suspend fun fetchLinkedChildDevices(childDeviceIds: List<String>): List<LinkedChildDevice> {
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
}
