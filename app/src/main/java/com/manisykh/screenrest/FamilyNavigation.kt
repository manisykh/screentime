package com.manisykh.screenrest

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.manisykh.screenrest.data.AppLanguage
import com.manisykh.screenrest.data.ChildUsageRefreshRequest
import com.manisykh.screenrest.data.ChildUsageSnapshot
import com.manisykh.screenrest.data.ImmediateBlockReadState
import com.manisykh.screenrest.data.ImmediateBlockState
import com.manisykh.screenrest.data.LinkedChildDevice
import com.manisykh.screenrest.data.ParentAccountAuthState
import com.manisykh.screenrest.data.ParentDeviceRole
import com.manisykh.screenrest.data.ParentManagementState
import com.manisykh.screenrest.data.ParentNotificationState
import com.manisykh.screenrest.ui.designsystem.*
import com.manisykh.screenrest.ui.safety.PolicySummary
import com.manisykh.screenrest.usage.InstalledAppInfo

internal enum class FamilyDestination {
    Home,
    Management,
}

@Composable
internal fun FamilyContent(
    destination: FamilyDestination,
    onDestinationChanged: (FamilyDestination) -> Unit,
    parentState: ParentManagementState,
    childTopAppsSharingEnabled: Boolean,
    childUsageSnapshots: Map<String, ChildUsageSnapshot>,
    childUsageRefreshRequests: Map<String, ChildUsageRefreshRequest>,
    childImmediateBlocks: Map<String, ImmediateBlockReadState>,
    localImmediateBlock: ImmediateBlockState,
    parentAccountAuthState: ParentAccountAuthState,
    notificationState: ParentNotificationState,
    parentRequestNotificationReady: Boolean,
    parentRequestNotificationIssue: String,
    installedApps: List<InstalledAppInfo>,
    policySummary: PolicySummary,
    safeModeEnabled: Boolean,
    policyEnforcementEnabled: Boolean,
    text: AppStrings,
    onPairParentAccount: (String, String, String) -> Unit,
    onParentProfileNameChanged: (String) -> Unit,
    onParentDeviceRoleChanged: (ParentDeviceRole, String) -> Unit,
    onGenerateChildPairingCode: (String) -> Unit,
    onRegisterChildPairingCode: (String, String, String) -> Unit,
    onParentGoogleSignIn: () -> Unit,
    onDeleteAccountAndCloudData: (String) -> Unit,
    onUnlinkParentAccount: (String) -> Unit,
    onUnlinkLinkedChildDevice: (String, String) -> Unit,
    onUnlinkLinkedParentDevice: (String, String) -> Unit,
    onSyncParentDevice: () -> Unit,
    onCheckImmediateBlock: (String) -> Unit,
    onChildTopAppsSharingChanged: (Boolean) -> Unit,
    onStartImmediateBlock: (String, Int, String) -> Unit,
    onStopImmediateBlock: (String, String, String) -> Unit,
    pendingParentManagementAction: ParentManagementPendingAction?,
    onPendingParentManagementActionChanged: (ParentManagementPendingAction?) -> Unit,
    onClearRemoteParentCommands: (String) -> Unit,
    onRemoteAppExtraTime: (String, String, Int) -> Unit,
    onRemoteAppUnlockToday: (String, String) -> Unit,
    onRemoteTotalExtraTime: (Int) -> Unit,
    onRemoteTotalUnlockToday: () -> Unit,
    onApproveRemoteUnlockRequest: (String, Int, Boolean) -> Unit,
    onRejectRemoteUnlockRequest: (String) -> Unit,
    onOpenLocalRules: () -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    when (destination) {
        FamilyDestination.Home -> FamilyHomeContent(
            parentState = parentState,
            childTopAppsSharingEnabled = childTopAppsSharingEnabled,
            childUsageSnapshots = childUsageSnapshots,
            childUsageRefreshRequests = childUsageRefreshRequests,
            childImmediateBlocks = childImmediateBlocks,
            localImmediateBlock = localImmediateBlock,
            policySummary = policySummary,
            safeModeEnabled = safeModeEnabled,
            policyEnforcementEnabled = policyEnforcementEnabled,
            text = text,
            onOpenManagement = { onDestinationChanged(FamilyDestination.Management) },
            onSyncParentDevice = onSyncParentDevice,
            onCheckImmediateBlock = onCheckImmediateBlock,
            onChildTopAppsSharingChanged = onChildTopAppsSharingChanged,
            onStartImmediateBlock = onStartImmediateBlock,
            onStopImmediateBlock = onStopImmediateBlock,
            onOpenLocalRules = onOpenLocalRules,
            onApproveRemoteUnlockRequest = onApproveRemoteUnlockRequest,
            onRejectRemoteUnlockRequest = onRejectRemoteUnlockRequest,
        )

        FamilyDestination.Management -> Column(
            verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.md),
        ) {
            MoreDetailHeader(
                title = if (korean) "가족 및 기기 관리" else "Family and device management",
                subtitle = if (korean) "역할, 프로필, 연결과 동기화를 관리합니다" else "Manage roles, profiles, pairing, and sync",
                onBack = { onDestinationChanged(FamilyDestination.Home) },
            )
            ParentManagementSection(
                parentState = parentState,
                parentAccountAuthState = parentAccountAuthState,
                notificationState = notificationState,
                parentRequestNotificationReady = parentRequestNotificationReady,
                parentRequestNotificationIssue = parentRequestNotificationIssue,
                installedApps = installedApps,
                policySummary = policySummary,
                text = text,
                onPairParentAccount = onPairParentAccount,
                onParentProfileNameChanged = onParentProfileNameChanged,
                onParentDeviceRoleChanged = onParentDeviceRoleChanged,
                onGenerateChildPairingCode = onGenerateChildPairingCode,
                onRegisterChildPairingCode = onRegisterChildPairingCode,
                onParentGoogleSignIn = onParentGoogleSignIn,
                onDeleteAccountAndCloudData = onDeleteAccountAndCloudData,
                onUnlinkParentAccount = onUnlinkParentAccount,
                onUnlinkLinkedChildDevice = onUnlinkLinkedChildDevice,
                onUnlinkLinkedParentDevice = onUnlinkLinkedParentDevice,
                onSyncParentDevice = onSyncParentDevice,
                pendingParentManagementAction = pendingParentManagementAction,
                onPendingParentManagementActionChanged = onPendingParentManagementActionChanged,
                onClearRemoteParentCommands = onClearRemoteParentCommands,
                onRemoteAppExtraTime = onRemoteAppExtraTime,
                onRemoteAppUnlockToday = onRemoteAppUnlockToday,
                onRemoteTotalExtraTime = onRemoteTotalExtraTime,
                onRemoteTotalUnlockToday = onRemoteTotalUnlockToday,
                onApproveRemoteUnlockRequest = onApproveRemoteUnlockRequest,
                onRejectRemoteUnlockRequest = onRejectRemoteUnlockRequest,
                wrapInCard = false,
            )
        }
    }
}

@Composable
internal fun FamilyHomeContent(
    parentState: ParentManagementState,
    childTopAppsSharingEnabled: Boolean,
    childUsageSnapshots: Map<String, ChildUsageSnapshot>,
    childUsageRefreshRequests: Map<String, ChildUsageRefreshRequest>,
    childImmediateBlocks: Map<String, ImmediateBlockReadState>,
    localImmediateBlock: ImmediateBlockState,
    policySummary: PolicySummary,
    safeModeEnabled: Boolean,
    policyEnforcementEnabled: Boolean,
    text: AppStrings,
    onOpenManagement: () -> Unit,
    onSyncParentDevice: () -> Unit,
    onCheckImmediateBlock: (String) -> Unit,
    onChildTopAppsSharingChanged: (Boolean) -> Unit,
    onStartImmediateBlock: (String, Int, String) -> Unit,
    onStopImmediateBlock: (String, String, String) -> Unit,
    onOpenLocalRules: () -> Unit,
    onApproveRemoteUnlockRequest: (String, Int, Boolean) -> Unit,
    onRejectRemoteUnlockRequest: (String) -> Unit,
) {
    val childDevices = remember(
        parentState.linkedChildDevices,
        parentState.linkedChildPairingCodes,
        parentState.remoteUnlockRequests,
    ) {
        parentState.linkedChildDevices.ifEmpty {
            parentState.linkedChildPairingCodes.map { code ->
                LinkedChildDevice(
                    childDeviceId = code,
                    childDeviceName = code,
                    pairingCode = code,
                )
            }
        }.ifEmpty {
            parentState.remoteUnlockRequests
                .distinctBy { request -> request.childDeviceId }
                .map { request ->
                    LinkedChildDevice(
                        childDeviceId = request.childDeviceId,
                        childDeviceName = request.childDeviceName,
                    )
                }
        }
    }
    val connected = if (parentState.deviceRole == ParentDeviceRole.Parent) {
        parentState.paired || childDevices.isNotEmpty()
    } else {
        parentState.paired || parentState.linkedParentDevices.isNotEmpty()
    }
    val korean = text.appLanguage == AppLanguage.Korean

    Column(verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.md)) {
        ScreenRestPageHeader(
            title = if (korean) "가족" else "Family",
            subtitle = when (parentState.deviceRole) {
                ParentDeviceRole.Parent -> if (korean) "자녀 기기와 승인 요청을 관리합니다" else "Manage child devices and approval requests"
                ParentDeviceRole.Child -> if (korean) "내 기기와 부모 연결 상태를 확인합니다" else "Review this device and its parent connection"
            },
            trailing = {
                ScreenRestStatusPill(
                    label = if (connected) {
                        if (korean) "정상 연결" else "Connected"
                    } else {
                        if (korean) "미연결" else "Not linked"
                    },
                    tone = if (connected) ScreenRestTone.Success else ScreenRestTone.Warning,
                )
            },
        )

        when {
            !connected -> FamilyUnpairedContent(
                parentState = parentState,
                text = text,
                onOpenManagement = onOpenManagement,
            )

            parentState.deviceRole == ParentDeviceRole.Parent -> FamilyParentDashboard(
                parentState = parentState,
                childDevices = childDevices,
                childUsageSnapshots = childUsageSnapshots,
                childUsageRefreshRequests = childUsageRefreshRequests,
                childImmediateBlocks = childImmediateBlocks,
                text = text,
                onOpenManagement = onOpenManagement,
                onSyncParentDevice = onSyncParentDevice,
                onCheckImmediateBlock = onCheckImmediateBlock,
                onStartImmediateBlock = onStartImmediateBlock,
                onStopImmediateBlock = onStopImmediateBlock,
                onApproveRemoteUnlockRequest = onApproveRemoteUnlockRequest,
                onRejectRemoteUnlockRequest = onRejectRemoteUnlockRequest,
            )

            else -> FamilyChildDashboard(
                parentState = parentState,
                childTopAppsSharingEnabled = childTopAppsSharingEnabled,
                immediateBlock = localImmediateBlock,
                policySummary = policySummary,
                safeModeEnabled = safeModeEnabled,
                policyEnforcementEnabled = policyEnforcementEnabled,
                text = text,
                onOpenManagement = onOpenManagement,
                onSyncParentDevice = onSyncParentDevice,
                onChildTopAppsSharingChanged = onChildTopAppsSharingChanged,
                onOpenLocalRules = onOpenLocalRules,
            )
        }
    }
}

@Composable
internal fun FamilyUnpairedContent(
    parentState: ParentManagementState,
    text: AppStrings,
    onOpenManagement: () -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    ScreenRestCard(tone = ScreenRestTone.Warning) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.sm),
        ) {
            MoreMenuIcon(R.drawable.ic_nav_family, ScreenRestTone.Warning)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (korean) "가족 연결이 필요합니다" else "Family connection required",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (korean) {
                        if (parentState.deviceRole == ParentDeviceRole.Parent) {
                            "부모 프로필을 저장하고 자녀의 연결 코드를 등록하세요"
                        } else {
                            "자녀 프로필을 저장하고 부모에게 보낼 연결 코드를 만드세요"
                        }
                    } else if (parentState.deviceRole == ParentDeviceRole.Parent) {
                        "Save the parent profile and register the child's pairing code"
                    } else {
                        "Save the child profile and create a pairing code for a parent"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ScreenRestPrimaryButton(
            label = if (korean) "연결 설정" else "Set up connection",
            onClick = onOpenManagement,
        )
    }
}

