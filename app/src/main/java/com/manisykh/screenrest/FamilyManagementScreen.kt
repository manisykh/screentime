package com.manisykh.screenrest

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.manisykh.screenrest.data.*
import com.manisykh.screenrest.ui.designsystem.*
import com.manisykh.screenrest.ui.safety.LimitStatus
import com.manisykh.screenrest.ui.safety.PolicySummary
import com.manisykh.screenrest.ui.theme.AppSafe
import com.manisykh.screenrest.ui.theme.AppWarn
import com.manisykh.screenrest.usage.InstalledAppInfo

@Composable
fun ParentManagementSection(
    parentState: ParentManagementState,
    parentAccountAuthState: ParentAccountAuthState,
    notificationState: ParentNotificationState,
    parentRequestNotificationReady: Boolean,
    parentRequestNotificationIssue: String,
    installedApps: List<InstalledAppInfo>,
    policySummary: PolicySummary,
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
    pendingParentManagementAction: ParentManagementPendingAction?,
    onPendingParentManagementActionChanged: (ParentManagementPendingAction?) -> Unit,
    onClearRemoteParentCommands: (String) -> Unit,
    onRemoteAppExtraTime: (String, String, Int) -> Unit,
    onRemoteAppUnlockToday: (String, String) -> Unit,
    onRemoteTotalExtraTime: (Int) -> Unit,
    onRemoteTotalUnlockToday: () -> Unit,
    onApproveRemoteUnlockRequest: (String, Int, Boolean) -> Unit,
    onRejectRemoteUnlockRequest: (String) -> Unit,
    wrapInCard: Boolean = true,
) {
    val focusManager = LocalFocusManager.current
    var profileName by remember(parentState.localProfileName, parentState.deviceRole) {
        mutableStateOf(
            parentState.localProfileName.ifBlank {
                if (parentState.deviceRole == ParentDeviceRole.Parent) {
                    parentState.parentAccountId.ifBlank { "Parent device" }
                } else {
                    parentState.childDeviceName.ifBlank { "Child device" }
                }
            },
        )
    }
    var profileSaveRequested by rememberSaveable { mutableStateOf(false) }
    var profileSaveAcknowledged by rememberSaveable { mutableStateOf(false) }
    var selectedRoleDraft by remember { mutableStateOf(parentState.deviceRole) }
    var childPairingCodeInput by remember { mutableStateOf("") }
    var showGeneratePairingCodePinDialog by remember { mutableStateOf(false) }
    var pairingCodePendingRegistration by remember { mutableStateOf("") }
    var showDeleteAccountPinDialog by remember { mutableStateOf(false) }
    val parentRoleConfirmed = parentState.deviceRole == ParentDeviceRole.Parent
    val profileSavedVisible = parentState.localProfileName.isNotBlank() &&
        profileName.trim() == parentState.localProfileName
    val visibleRole = selectedRoleDraft
    val roleContainerColor = when (visibleRole) {
        ParentDeviceRole.Child -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
        ParentDeviceRole.Parent -> AppSafe.copy(alpha = 0.12f)
    }
    val roleBorderColor = when (visibleRole) {
        ParentDeviceRole.Child -> MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
        ParentDeviceRole.Parent -> AppSafe.copy(alpha = 0.28f)
    }

    LaunchedEffect(parentState.deviceRole) {
        selectedRoleDraft = parentState.deviceRole
    }
    LaunchedEffect(parentState.deviceRole, pendingParentManagementAction) {
        val action = pendingParentManagementAction
        selectedRoleDraft = if (action is ParentManagementPendingAction.ChangeRole) {
            action.role
        } else {
            parentState.deviceRole
        }
    }
    LaunchedEffect(parentState.localProfileName, profileSaveRequested, profileName) {
        if (
            profileSaveRequested &&
            profileName.trim().isNotBlank() &&
            parentState.localProfileName == profileName.trim()
        ) {
            profileSaveAcknowledged = true
            profileSaveRequested = false
            focusManager.clearFocus(force = true)
        }
    }

    OptionalSimpleCard(wrapInCard = wrapInCard) {
        if (wrapInCard) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(text.parentManagement, Modifier.weight(1f))
                StatusBadge(
                    label = if (parentState.paired) text.parentLinked else text.parentNotLinked,
                    status = if (parentState.paired) LimitStatus.Normal else LimitStatus.Warning,
                )
            }
        }

        if (visibleRole == ParentDeviceRole.Parent) {
            RemoteUnlockRequestList(
                requests = parentState.remoteUnlockRequests,
                text = text,
                onApproveExtraTime = { request, minutes ->
                    onApproveRemoteUnlockRequest(request.id, minutes, false)
                },
                onApproveUnlockToday = { request ->
                    onApproveRemoteUnlockRequest(request.id, 0, true)
                },
                onReject = { request ->
                    onRejectRemoteUnlockRequest(request.id)
                },
            )
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = roleContainerColor,
            border = BorderStroke(1.dp, roleBorderColor),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text.parentDeviceRole,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceButton(
                        label = text.childDeviceMode,
                        selected = selectedRoleDraft == ParentDeviceRole.Child,
                        onClick = {
                            selectedRoleDraft = ParentDeviceRole.Child
                            onPendingParentManagementActionChanged(
                                if (ParentDeviceRole.Child == parentState.deviceRole) {
                                    null
                                } else {
                                    ParentManagementPendingAction.ChangeRole(ParentDeviceRole.Child)
                                },
                            )
                        },
                    )
                    ChoiceButton(
                        label = text.parentDeviceMode,
                        selected = selectedRoleDraft == ParentDeviceRole.Parent,
                        onClick = {
                            selectedRoleDraft = ParentDeviceRole.Parent
                            onPendingParentManagementActionChanged(
                                if (ParentDeviceRole.Parent == parentState.deviceRole) {
                                    null
                                } else {
                                    ParentManagementPendingAction.ChangeRole(ParentDeviceRole.Parent)
                                },
                            )
                        },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = {
                            profileName = it
                            profileSaveRequested = false
                            profileSaveAcknowledged = false
                        },
                        label = { Text(text.profileName) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus(force = true) },
                        ),
                    )
                    Button(
                        onClick = {
                            focusManager.clearFocus(force = true)
                            profileSaveRequested = true
                            profileSaveAcknowledged = false
                            onParentProfileNameChanged(profileName)
                        },
                        enabled = profileName.isNotBlank(),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    ) {
                        Text(
                            if (profileSaveAcknowledged || profileSavedVisible) text.profileSaved else text.savePolicy,
                            maxLines = 1,
                        )
                    }
                }
                if (profileSaveAcknowledged || profileSavedVisible) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = AppSafe.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, AppSafe.copy(alpha = 0.26f)),
                    ) {
                        Text(
                            text.profileSaved,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppSafe,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                if (visibleRole == ParentDeviceRole.Child) {
                    ParentLinkDetailRow(
                        text.childPairingCode,
                        parentState.pairingCode.ifBlank { "-" },
                    )
                    LinkedParentDeviceList(
                        parents = parentState.linkedParentDevices,
                        text = text,
                        onUnlink = { parentUid ->
                            onPendingParentManagementActionChanged(
                                ParentManagementPendingAction.UnlinkLinkedParent(parentUid),
                            )
                        },
                    )
                    Button(
                        onClick = { showGeneratePairingCodePinDialog = true },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text.generatePairingCode, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = if (parentAccountAuthState.recoverable) {
                            AppSafe.copy(alpha = 0.12f)
                        } else {
                            AppWarn.copy(alpha = 0.12f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (parentAccountAuthState.recoverable) {
                                AppSafe.copy(alpha = 0.28f)
                            } else {
                                AppWarn.copy(alpha = 0.28f)
                            },
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = if (parentAccountAuthState.recoverable) {
                                    if (text.appLanguage == AppLanguage.Korean) {
                                        "부모 Google 계정 연결됨"
                                    } else {
                                        "Parent Google account connected"
                                    }
                                } else {
                                    if (text.appLanguage == AppLanguage.Korean) {
                                        "재설치 후에도 자녀 연결을 복구하려면 Google 로그인이 필요합니다."
                                    } else {
                                        "Google sign-in is required to restore child links after reinstalling."
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (parentAccountAuthState.recoverable) AppSafe else AppWarn,
                            )
                            if (parentAccountAuthState.recoverable) {
                                Text(
                                    text = parentAccountAuthState.email
                                        .ifBlank { parentAccountAuthState.displayName }
                                        .ifBlank { parentAccountAuthState.uid },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else {
                                Button(
                                    onClick = onParentGoogleSignIn,
                                    enabled = parentRoleConfirmed && parentAccountAuthState.available,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Text(
                                        if (text.appLanguage == AppLanguage.Korean) {
                                            "Google 계정으로 로그인"
                                        } else {
                                            "Sign in with Google"
                                        },
                                    )
                                }
                                if (!parentRoleConfirmed) {
                                    Text(
                                        text = if (text.appLanguage == AppLanguage.Korean) {
                                            "먼저 부모 기기 모드를 관리 PIN으로 저장해 주세요."
                                        } else {
                                            "Save parent device mode with the admin PIN first."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = childPairingCodeInput,
                        onValueChange = { value ->
                            childPairingCodeInput = value
                                .uppercase()
                                .filter { char -> char.isLetterOrDigit() }
                                .removePrefix("SR")
                                .take(6)
                        },
                        label = { Text(text.childPairingCode) },
                        leadingIcon = {
                            Text(
                                "SR-",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                    )
                    Button(
                        onClick = {
                            pairingCodePendingRegistration = "SR-$childPairingCodeInput"
                        },
                        enabled = childPairingCodeInput.length == 6 &&
                            parentAccountAuthState.recoverable && parentRoleConfirmed,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text.registerChildDevice, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    ParentLinkDetailRow(
                        text.linkedChildDevices,
                        parentState.linkedChildDevices
                            .ifEmpty {
                                parentState.linkedChildPairingCodes.map { code ->
                                    LinkedChildDevice(
                                        childDeviceId = code,
                                        childDeviceName = code,
                                        pairingCode = code,
                                    )
                                }
                            }
                            .size
                            .toString(),
                    )
                    LinkedChildDeviceList(
                        children = parentState.linkedChildDevices,
                        text = text,
                        onUnlink = { childDeviceId ->
                            onPendingParentManagementActionChanged(
                                ParentManagementPendingAction.UnlinkLinkedChild(childDeviceId),
                            )
                        },
                    )
                }
            }
        }

        pendingParentManagementAction?.let { action ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.18f)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        action.parentManagementActionTitle(text),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(
                        onClick = {
                            onPendingParentManagementActionChanged(null)
                        },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    ) {
                        Text(text.cancel, maxLines = 1)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    onPendingParentManagementActionChanged(ParentManagementPendingAction.UnlinkParentAccount)
                },
                enabled = parentState.paired,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(text.unlinkParent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = onSyncParentDevice,
                enabled = parentState.paired,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(text.syncNow, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        OutlinedButton(
            onClick = { showDeleteAccountPinDialog = true },
            enabled = parentAccountAuthState.authenticated &&
                (parentState.paired || parentAccountAuthState.recoverable),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
        ) {
            Text(
                if (text.appLanguage == AppLanguage.Korean) {
                    "계정 및 클라우드 데이터 삭제"
                } else {
                    "Delete account and cloud data"
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        ParentLinkDetailRow(text.childDeviceId, parentState.childDeviceId.ifBlank { "-" })
        ParentLinkDetailRow(
            text.lastSync,
            if (parentState.lastSyncMillis > 0L) formatClockTime(parentState.lastSyncMillis) else "-",
        )
        ParentLinkDetailRow(
            if (text.appLanguage == AppLanguage.Korean) "요청 알림 상태" else "Request alerts",
            if (parentRequestNotificationReady) {
                if (text.appLanguage == AppLanguage.Korean) "사용 가능" else "Available"
            } else {
                text.parentNotificationIssueLabel(parentRequestNotificationIssue)
            },
        )
        ParentLinkDetailRow(
            if (text.appLanguage == AppLanguage.Korean) "마지막 요청 알림" else "Last request alert",
            if (notificationState.lastSuccessMillis > 0L) {
                formatClockTime(notificationState.lastSuccessMillis)
            } else {
                if (text.appLanguage == AppLanguage.Korean) "아직 없음" else "None yet"
            },
        )
        if (!parentRequestNotificationReady && notificationState.lastError.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.error.copy(alpha = 0.24f),
                ),
            ) {
                Text(
                    text = if (text.appLanguage == AppLanguage.Korean) {
                        "최근 요청 알림 문제: ${text.parentNotificationIssueLabel(notificationState.lastError)}"
                    } else {
                        "Recent request alert issue: ${text.parentNotificationIssueLabel(notificationState.lastError)}"
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }

    if (showGeneratePairingCodePinDialog) {
        AdminPinConfirmDialog(
            title = text.generatePairingCode,
            description = text.generatePairingCodePinInstruction(),
            confirmLabel = text.generatePairingCode,
            text = text,
            onDismiss = { showGeneratePairingCodePinDialog = false },
            onConfirm = { adminPin ->
                showGeneratePairingCodePinDialog = false
                onGenerateChildPairingCode(adminPin)
            },
        )
    }
    if (pairingCodePendingRegistration.isNotBlank()) {
        AdminPinConfirmDialog(
            title = text.registerChildDevice,
            description = text.registerChildDevicePinInstruction(),
            confirmLabel = text.registerChildDevice,
            text = text,
            onDismiss = { pairingCodePendingRegistration = "" },
            onConfirm = { adminPin ->
                val pairingCode = pairingCodePendingRegistration
                pairingCodePendingRegistration = ""
                onRegisterChildPairingCode(pairingCode, "", adminPin)
            },
        )
    }
    if (showDeleteAccountPinDialog) {
        AdminPinConfirmDialog(
            title = if (text.appLanguage == AppLanguage.Korean) {
                "계정 및 클라우드 데이터 삭제"
            } else {
                "Delete account and cloud data"
            },
            description = if (text.appLanguage == AppLanguage.Korean) {
                "이 기기의 Firebase 계정, 부모·자녀 연결, 요청, 명령과 알림 토큰을 삭제합니다. 삭제한 클라우드 데이터는 복구할 수 없습니다."
            } else {
                "This deletes the Firebase account, parent-child links, requests, commands, and notification tokens. Deleted cloud data cannot be restored."
            },
            confirmLabel = if (text.appLanguage == AppLanguage.Korean) "삭제" else "Delete",
            text = text,
            onDismiss = { showDeleteAccountPinDialog = false },
            onConfirm = { adminPin ->
                showDeleteAccountPinDialog = false
                onDeleteAccountAndCloudData(adminPin)
            },
        )
    }
}
