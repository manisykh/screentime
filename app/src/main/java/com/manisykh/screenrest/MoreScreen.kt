package com.manisykh.screenrest

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.manisykh.screenrest.data.AppLanguage
import com.manisykh.screenrest.data.ParentAccountAuthState
import com.manisykh.screenrest.data.ParentDeviceRole
import com.manisykh.screenrest.data.ParentManagementState
import com.manisykh.screenrest.safety.BlockDecisionResult
import com.manisykh.screenrest.ui.designsystem.*
import com.manisykh.screenrest.ui.safety.PolicySummary
import com.manisykh.screenrest.ui.safety.SafeModeUiState

internal enum class MoreDestination {
    Home,
    ProtectionStatus,
    AdminPin,
    EmergencyPass,
    Permissions,
    Notifications,
    Language,
    GoogleAccount,
    Help,
    Diagnostics,
}

private data class MoreMenuItem(
    val title: String,
    val description: String,
    val iconRes: Int,
    val tone: ScreenRestTone,
    val destination: MoreDestination,
)

@Composable
internal fun MoreContent(
    destination: MoreDestination,
    onDestinationChanged: (MoreDestination) -> Unit,
    layoutMode: ScreenLayoutMode,
    onLayoutModeChanged: (ScreenLayoutMode) -> Unit,
    uiState: SafeModeUiState,
    parentAccountAuthState: ParentAccountAuthState,
    safeRecoveryAdminPin: String,
    text: AppStrings,
    isExpanded: Boolean,
    onSafeModeChanged: (Boolean) -> Unit,
    onSafeModeEnableWithPin: (String) -> Unit,
    onSafeModePinStatusSeen: () -> Unit,
    onPolicyEnforcementChanged: (Boolean) -> Unit,
    onPolicyEnforcementDisableWithPin: (String) -> Unit,
    onOpenBlockScreenPreview: (BlockDecisionResult) -> Unit,
    onSafeRecoveryPinChanged: (String) -> Unit,
    onSafeRecoveryClick: () -> Unit,
    onAppLanguageChanged: (AppLanguage) -> Unit,
    onWarningNotificationsChanged: (Boolean) -> Unit,
    onLimitNotificationsChanged: (Boolean) -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenNotificationAccessSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onUpdateAdminPin: (String, String) -> Unit,
    onPinInputChanged: () -> Unit,
    onParentGoogleSignIn: () -> Unit,
    onDeleteAccountAndCloudData: (String) -> Unit,
    onClearEventLog: () -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    when (destination) {
        MoreDestination.Home -> MoreHomeContent(
            uiState = uiState,
            parentAccountAuthState = parentAccountAuthState,
            text = text,
            onDestinationChanged = onDestinationChanged,
            layoutMode = layoutMode,
            onLayoutModeChanged = onLayoutModeChanged,
        )

        MoreDestination.ProtectionStatus -> Column(
            verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.md),
        ) {
            MoreDetailHeader(
                title = if (korean) "보호 상태" else "Protection status",
                subtitle = if (korean) "차단 준비 상태와 보호 작동을 확인합니다" else "Review protection and blocking readiness",
                onBack = { onDestinationChanged(MoreDestination.Home) },
            )
            SafetyContent(
                uiState = uiState,
                safeRecoveryAdminPin = safeRecoveryAdminPin,
                text = text,
                isExpanded = isExpanded,
                onSafeModeChanged = onSafeModeChanged,
                onSafeModeEnableWithPin = onSafeModeEnableWithPin,
                onSafeModePinStatusSeen = onSafeModePinStatusSeen,
                onPolicyEnforcementChanged = onPolicyEnforcementChanged,
                onPolicyEnforcementDisableWithPin = onPolicyEnforcementDisableWithPin,
                onOpenBlockScreenPreview = onOpenBlockScreenPreview,
                onSafeRecoveryPinChanged = onSafeRecoveryPinChanged,
                onSafeRecoveryClick = onSafeRecoveryClick,
            )
        }

        MoreDestination.AdminPin -> MoreAdminPinContent(
            uiState = uiState,
            text = text,
            onBack = { onDestinationChanged(MoreDestination.Home) },
            onUpdateAdminPin = onUpdateAdminPin,
            onPinInputChanged = onPinInputChanged,
        )

        MoreDestination.EmergencyPass -> MoreEmergencyPassContent(
            summary = uiState.policySummary,
            text = text,
            onBack = { onDestinationChanged(MoreDestination.Home) },
        )

        MoreDestination.Permissions -> Column(
            verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.md),
        ) {
            MoreDetailHeader(
                title = if (korean) "권한 및 작동 상태" else "Permissions and operation",
                subtitle = if (korean) "차단에 필요한 Android 권한을 확인합니다" else "Review Android permissions required for blocking",
                onBack = { onDestinationChanged(MoreDestination.Home) },
            )
            PermissionSettingsSection(
                readiness = uiState.blockingReadiness,
                text = text,
                onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                onOpenOverlaySettings = onOpenOverlaySettings,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onOpenNotificationAccessSettings = onOpenNotificationAccessSettings,
                onOpenExactAlarmSettings = onOpenExactAlarmSettings,
            )
        }

        MoreDestination.Notifications -> MoreNotificationContent(
            uiState = uiState,
            text = text,
            onBack = { onDestinationChanged(MoreDestination.Home) },
            onWarningNotificationsChanged = onWarningNotificationsChanged,
            onLimitNotificationsChanged = onLimitNotificationsChanged,
        )

        MoreDestination.Language -> MoreLanguageContent(
            appLanguage = uiState.appLanguage,
            text = text,
            onBack = { onDestinationChanged(MoreDestination.Home) },
            onAppLanguageChanged = onAppLanguageChanged,
        )

        MoreDestination.GoogleAccount -> MoreGoogleAccountContent(
            parentState = uiState.parentManagementState,
            authState = parentAccountAuthState,
            text = text,
            onBack = { onDestinationChanged(MoreDestination.Home) },
            onParentGoogleSignIn = onParentGoogleSignIn,
            onDeleteAccountAndCloudData = onDeleteAccountAndCloudData,
        )

        MoreDestination.Help -> MoreHelpContent(
            text = text,
            onBack = { onDestinationChanged(MoreDestination.Home) },
        )

        MoreDestination.Diagnostics -> Column(
            verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.md),
        ) {
            MoreDetailHeader(
                title = if (korean) "진단 정보" else "Diagnostics",
                subtitle = if (korean) "최근 앱 작동 기록을 확인합니다" else "Review recent app activity records",
                onBack = { onDestinationChanged(MoreDestination.Home) },
            )
            EventLogSection(
                eventLog = uiState.eventLog,
                text = text,
                onClearEventLog = onClearEventLog,
            )
            ScreenRestCard {
                ScreenRestListRow(
                    title = if (korean) "상세 작동 진단" else "Detailed operation diagnostics",
                    supportingText = if (korean) "감시 서비스와 차단 판단은 보호 상태에서 확인합니다" else "Monitor and blocking diagnostics are available under Protection status",
                    onClick = { onDestinationChanged(MoreDestination.ProtectionStatus) },
                    leading = {
                        MoreMenuIcon(R.drawable.ic_more_protection, ScreenRestTone.Primary)
                    },
                    trailing = { MoreChevron() },
                )
            }
        }
    }
}

@Composable
internal fun ScreenLayoutModePicker(
    layoutMode: ScreenLayoutMode,
    text: AppStrings,
    onLayoutModeChanged: (ScreenLayoutMode) -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    ScreenRestCard {
        ScreenRestSectionHeader(
            title = if (korean) "화면 구성" else "Screen layout",
            supportingText = if (korean) {
                "탭 배치만 바뀝니다. 규칙과 사용 기록은 그대로 유지됩니다."
            } else {
                "Only navigation changes. Rules and usage history stay the same."
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.xs)) {
            ChoiceButton(
                label = if (korean) "새 구성" else "New layout",
                selected = layoutMode == ScreenLayoutMode.Modern,
                onClick = { onLayoutModeChanged(ScreenLayoutMode.Modern) },
            )
            ChoiceButton(
                label = if (korean) "기존 탭 구성" else "Classic tabs",
                selected = layoutMode == ScreenLayoutMode.Classic,
                onClick = { onLayoutModeChanged(ScreenLayoutMode.Classic) },
            )
        }
    }
}

@Composable
private fun MoreHomeContent(
    uiState: SafeModeUiState,
    parentAccountAuthState: ParentAccountAuthState,
    text: AppStrings,
    onDestinationChanged: (MoreDestination) -> Unit,
    layoutMode: ScreenLayoutMode,
    onLayoutModeChanged: (ScreenLayoutMode) -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    val protectionReady = uiState.blockingReadiness.readyForBlocking &&
        uiState.policyEnforcementEnabled &&
        !uiState.safeModeEnabled
    val protectionTitle = when {
        uiState.safeModeEnabled -> if (korean) "보호 일시 중지" else "Protection paused"
        !uiState.blockingReadiness.readyForBlocking -> if (korean) "확인 필요" else "Needs attention"
        !uiState.policyEnforcementEnabled -> if (korean) "규칙 적용 꺼짐" else "Rules are off"
        else -> if (korean) "정상 작동 중" else "Working normally"
    }
    val protectionDescription = when {
        uiState.safeModeEnabled -> if (korean) "규칙은 보존되지만 현재 차단하지 않습니다" else "Rules are preserved, but blocking is paused"
        !uiState.blockingReadiness.readyForBlocking -> if (korean) "필수 권한 또는 감시 상태를 확인해 주세요" else "Review required permissions or monitoring status"
        !uiState.policyEnforcementEnabled -> if (korean) "저장된 규칙을 적용하지 않고 있습니다" else "Saved rules are not being enforced"
        else -> if (korean) "저장된 규칙과 차단 기능이 작동하고 있습니다" else "Saved rules and blocking are active"
    }

    Column(verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.md)) {
        ScreenRestPageHeader(
            title = if (korean) "더보기" else "More",
            subtitle = if (korean) "보호 상태와 앱 설정을 관리합니다" else "Manage protection and app settings",
        )

        ScreenLayoutModePicker(
            layoutMode = layoutMode,
            text = text,
            onLayoutModeChanged = onLayoutModeChanged,
        )

        ScreenRestCard(tone = if (protectionReady) ScreenRestTone.Success else ScreenRestTone.Warning) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.sm),
            ) {
                MoreMenuIcon(
                    iconRes = R.drawable.ic_more_protection,
                    tone = if (protectionReady) ScreenRestTone.Success else ScreenRestTone.Warning,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = protectionTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = protectionDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ScreenRestStatusPill(
                    label = if (protectionReady) {
                        if (korean) "정상" else "Ready"
                    } else {
                        if (korean) "확인" else "Check"
                    },
                    tone = if (protectionReady) ScreenRestTone.Success else ScreenRestTone.Warning,
                )
            }
            ScreenRestPrimaryButton(
                label = if (korean) "상태 확인" else "View status",
                onClick = { onDestinationChanged(MoreDestination.ProtectionStatus) },
            )
        }

        MoreMenuGroup(
            title = if (korean) "보호 및 보안" else "Protection and security",
            items = listOf(
                MoreMenuItem(
                    title = if (korean) "관리 PIN" else "Admin PIN",
                    description = if (korean) "설정 변경과 보호 기능 확인" else "Confirm settings and protection actions",
                    iconRes = R.drawable.ic_more_pin,
                    tone = ScreenRestTone.Primary,
                    destination = MoreDestination.AdminPin,
                ),
                MoreMenuItem(
                    title = if (korean) "긴급 사용권" else "Emergency Pass",
                    description = if (korean) {
                        "Emergency Pass · ${moreEmergencyPassSummary(uiState.policySummary, true)}"
                    } else {
                        moreEmergencyPassSummary(uiState.policySummary, false)
                    },
                    iconRes = R.drawable.ic_more_emergency,
                    tone = ScreenRestTone.Warning,
                    destination = MoreDestination.EmergencyPass,
                ),
                MoreMenuItem(
                    title = if (korean) "권한 및 작동 상태" else "Permissions and operation",
                    description = if (uiState.blockingReadiness.readyForBlocking) {
                        if (korean) "필수 권한 준비됨" else "Required permissions ready"
                    } else {
                        if (korean) "확인이 필요한 항목이 있습니다" else "Some items need attention"
                    },
                    iconRes = R.drawable.ic_more_protection,
                    tone = if (uiState.blockingReadiness.readyForBlocking) ScreenRestTone.Success else ScreenRestTone.Warning,
                    destination = MoreDestination.Permissions,
                ),
            ),
            onDestinationChanged = onDestinationChanged,
        )

        MoreMenuGroup(
            title = if (korean) "앱 설정" else "App settings",
            items = listOf(
                MoreMenuItem(
                    title = if (korean) "알림" else "Notifications",
                    description = if (korean) "경고 및 사용 초과 알림" else "Warnings and limit alerts",
                    iconRes = R.drawable.ic_more_notifications,
                    tone = ScreenRestTone.Primary,
                    destination = MoreDestination.Notifications,
                ),
                MoreMenuItem(
                    title = if (korean) "언어" else "Language",
                    description = if (uiState.appLanguage == AppLanguage.Korean) "한국어" else "English",
                    iconRes = R.drawable.ic_more_language,
                    tone = ScreenRestTone.Success,
                    destination = MoreDestination.Language,
                ),
            ),
            onDestinationChanged = onDestinationChanged,
        )

        MoreMenuGroup(
            title = if (korean) "계정 및 지원" else "Account and support",
            items = listOf(
                MoreMenuItem(
                    title = if (korean) "Google 계정" else "Google account",
                    description = when {
                        parentAccountAuthState.recoverable -> parentAccountAuthState.email
                            .ifBlank { parentAccountAuthState.displayName }
                            .ifBlank { if (korean) "연결됨" else "Connected" }
                        uiState.parentManagementState.deviceRole == ParentDeviceRole.Child ->
                            if (korean) "자녀 기기 익명 계정" else "Child device anonymous account"
                        else -> if (korean) "로그인 및 계정 관리" else "Sign in and manage account"
                    },
                    iconRes = R.drawable.ic_more_account,
                    tone = ScreenRestTone.Primary,
                    destination = MoreDestination.GoogleAccount,
                ),
                MoreMenuItem(
                    title = if (korean) "도움말" else "Help",
                    description = if (korean) "규칙과 보호 기능 알아보기" else "Learn about rules and protection",
                    iconRes = R.drawable.ic_more_help,
                    tone = ScreenRestTone.Schedule,
                    destination = MoreDestination.Help,
                ),
                MoreMenuItem(
                    title = if (korean) "진단 정보" else "Diagnostics",
                    description = if (korean) "최근 작동 기록 ${uiState.eventLog.size}개" else "${uiState.eventLog.size} recent records",
                    iconRes = R.drawable.ic_more_diagnostics,
                    tone = ScreenRestTone.Neutral,
                    destination = MoreDestination.Diagnostics,
                ),
            ),
            onDestinationChanged = onDestinationChanged,
        )
    }
}

@Composable
private fun MoreMenuGroup(
    title: String,
    items: List<MoreMenuItem>,
    onDestinationChanged: (MoreDestination) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.xs)) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = ScreenRestTheme.spacing.xs),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        ScreenRestCard(contentPadding = PaddingValues(vertical = ScreenRestTheme.spacing.xxs)) {
            items.forEachIndexed { index, item ->
                ScreenRestListRow(
                    title = item.title,
                    supportingText = item.description,
                    onClick = { onDestinationChanged(item.destination) },
                    leading = { MoreMenuIcon(item.iconRes, item.tone) },
                    trailing = { MoreChevron() },
                )
                if (index != items.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 72.dp, end = ScreenRestTheme.spacing.sm)
                            .height(1.dp)
                            .background(ScreenRestTheme.colors.divider),
                    )
                }
            }
        }
    }
}

@Composable
internal fun MoreMenuIcon(iconRes: Int, tone: ScreenRestTone) {
    val tint = when (tone) {
        ScreenRestTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        ScreenRestTone.Primary -> MaterialTheme.colorScheme.primary
        ScreenRestTone.Success -> ScreenRestTheme.colors.success
        ScreenRestTone.Warning -> ScreenRestTheme.colors.warning
        ScreenRestTone.Blocked -> ScreenRestTheme.colors.blocked
        ScreenRestTone.Schedule -> ScreenRestTheme.colors.schedule
    }
    ScreenRestIconContainer(tone = tone) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(ScreenRestTheme.sizes.icon),
            tint = tint,
        )
    }
}

@Composable
internal fun MoreChevron() {
    Icon(
        painter = painterResource(R.drawable.ic_chevron_right),
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun MoreDetailHeader(title: String, subtitle: String, onBack: () -> Unit) {
    ScreenRestPageHeader(
        title = title,
        subtitle = subtitle,
        leading = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        },
    )
}

@Composable
private fun MoreAdminPinContent(
    uiState: SafeModeUiState,
    text: AppStrings,
    onBack: () -> Unit,
    onUpdateAdminPin: (String, String) -> Unit,
    onPinInputChanged: () -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    var currentAdminPin by remember { mutableStateOf("") }
    var newAdminPin by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.md)) {
        MoreDetailHeader(
            title = if (korean) "관리 PIN" else "Admin PIN",
            subtitle = if (korean) "설정과 허용된 보호 동작을 확인합니다" else "Confirm settings and permitted protection actions",
            onBack = onBack,
        )
        ScreenRestCard {
            PinChangeFields(
                currentPin = currentAdminPin,
                newPin = newAdminPin,
                currentLabel = text.currentAdminPin,
                newLabel = text.newAdminPin,
                onCurrentChanged = {
                    currentAdminPin = it
                    onPinInputChanged()
                },
                onNewChanged = {
                    newAdminPin = it
                    onPinInputChanged()
                },
                onSave = {
                    onUpdateAdminPin(currentAdminPin, newAdminPin)
                    currentAdminPin = ""
                    newAdminPin = ""
                },
                status = uiState.pinChangeStatus,
                text = text,
            )
            Text(
                text = if (korean) {
                    "관리 PIN은 설정 변경, 부모 연결, 허용된 차단 해제와 안전 복구에 사용됩니다. 고행 3단계는 PIN만으로 종료할 수 없습니다."
                } else {
                    "The Admin PIN confirms settings, pairing, allowed unlocks, and Safe Recovery. It cannot end active hardship level 3 by itself."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MoreEmergencyPassContent(
    summary: PolicySummary,
    text: AppStrings,
    onBack: () -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    val nextAvailableAt = summary.emergencyPassNextAvailableAtMillis
    val available = nextAvailableAt <= 0L || System.currentTimeMillis() >= nextAvailableAt
    Column(verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.md)) {
        MoreDetailHeader(
            title = if (korean) "긴급 사용권" else "Emergency Pass",
            subtitle = if (korean) "고행 3단계의 제한된 긴급 예외 권한" else "A limited emergency exception for hardship level 3",
            onBack = onBack,
        )
        ScreenRestCard(tone = if (available) ScreenRestTone.Success else ScreenRestTone.Warning) {
            ScreenRestSectionHeader(
                title = if (available) {
                    if (korean) "사용 가능" else "Available"
                } else {
                    if (korean) "이미 사용함" else "Already used"
                },
                supportingText = if (available) {
                    if (korean) "필요할 때 블록 화면에서 사용할 수 있습니다" else "Use it from the block screen when needed"
                } else {
                    if (korean) "다음 사용 가능 ${formatDateTime(nextAvailableAt)}" else "Available again ${formatDateTime(nextAvailableAt)}"
                },
                action = {
                    ScreenRestStatusPill(
                        label = if (available) {
                            if (korean) "1회" else "1 use"
                        } else {
                            if (korean) "대기" else "Waiting"
                        },
                        tone = if (available) ScreenRestTone.Success else ScreenRestTone.Warning,
                    )
                },
            )
        }
        ScreenRestCard {
            MoreInformationRow(
                title = if (korean) "공유 주기" else "Shared interval",
                description = if (korean) "모든 고행 3단계에서 7일에 한 번" else "Once every 7 days across all level-3 policies",
            )
            MoreInformationRow(
                title = if (korean) "적용 범위" else "Scope",
                description = if (korean) "현재 차단된 앱 하나에만 적용" else "Applies only to the currently blocked app",
            )
            MoreInformationRow(
                title = if (korean) "종료 시점" else "Expiration",
                description = if (korean) "현재 차단 정책이 끝나면 자동 만료" else "Expires when the current blocking policy ends",
            )
            MoreInformationRow(
                title = if (korean) "사용 위치" else "Where to use",
                description = if (korean) "고행 3단계 블록 화면에서만 사용" else "Available only from a level-3 block screen",
            )
        }
    }
}

@Composable
private fun MoreInformationRow(title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ScreenRestTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.xxs),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MoreNotificationContent(
    uiState: SafeModeUiState,
    text: AppStrings,
    onBack: () -> Unit,
    onWarningNotificationsChanged: (Boolean) -> Unit,
    onLimitNotificationsChanged: (Boolean) -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    Column(verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.md)) {
        MoreDetailHeader(
            title = if (korean) "알림" else "Notifications",
            subtitle = if (korean) "사용 시간과 차단 상태 알림을 설정합니다" else "Configure usage and blocking alerts",
            onBack = onBack,
        )
        ScreenRestCard {
            NotificationPreferenceRow(
                title = text.warningNotifications,
                checked = uiState.warningNotificationsEnabled,
                onCheckedChange = onWarningNotificationsChanged,
            )
            NotificationPreferenceRow(
                title = text.limitNotifications,
                checked = uiState.limitNotificationsEnabled,
                onCheckedChange = onLimitNotificationsChanged,
            )
        }
    }
}

@Composable
private fun MoreLanguageContent(
    appLanguage: AppLanguage,
    text: AppStrings,
    onBack: () -> Unit,
    onAppLanguageChanged: (AppLanguage) -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    Column(verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.md)) {
        MoreDetailHeader(
            title = if (korean) "언어" else "Language",
            subtitle = if (korean) "앱에서 사용할 언어를 선택합니다" else "Choose the language used in the app",
            onBack = onBack,
        )
        ScreenRestCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.xs),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    ChoiceButton(
                        label = text.korean,
                        selected = appLanguage == AppLanguage.Korean,
                        onClick = { onAppLanguageChanged(AppLanguage.Korean) },
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    ChoiceButton(
                        label = "English",
                        selected = appLanguage == AppLanguage.English,
                        onClick = { onAppLanguageChanged(AppLanguage.English) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreGoogleAccountContent(
    parentState: ParentManagementState,
    authState: ParentAccountAuthState,
    text: AppStrings,
    onBack: () -> Unit,
    onParentGoogleSignIn: () -> Unit,
    onDeleteAccountAndCloudData: (String) -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    var showDeleteDialog by remember { mutableStateOf(false) }
    val isParent = parentState.deviceRole == ParentDeviceRole.Parent
    Column(verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.md)) {
        MoreDetailHeader(
            title = if (korean) "Google 계정" else "Google account",
            subtitle = if (korean) "로그인과 클라우드 계정 데이터를 관리합니다" else "Manage sign-in and cloud account data",
            onBack = onBack,
        )
        ScreenRestCard(tone = if (authState.recoverable) ScreenRestTone.Success else ScreenRestTone.Neutral) {
            ScreenRestSectionHeader(
                title = when {
                    authState.recoverable -> if (korean) "Google 계정 연결됨" else "Google account connected"
                    isParent -> if (korean) "Google 로그인 필요" else "Google sign-in required"
                    else -> if (korean) "자녀 기기 계정" else "Child device account"
                },
                supportingText = when {
                    authState.recoverable -> authState.email
                        .ifBlank { authState.displayName }
                        .ifBlank { authState.uid }
                    isParent -> if (korean) "재설치 후 가족 연결을 복구하려면 로그인하세요" else "Sign in to restore family links after reinstalling"
                    else -> if (korean) "자녀 기기는 익명 계정으로 연결 정보를 동기화합니다" else "Child devices sync links with an anonymous account"
                },
                action = {
                    ScreenRestStatusPill(
                        label = if (authState.recoverable) {
                            if (korean) "연결됨" else "Connected"
                        } else {
                            if (korean) "미연결" else "Not linked"
                        },
                        tone = if (authState.recoverable) ScreenRestTone.Success else ScreenRestTone.Neutral,
                    )
                },
            )
            if (!authState.recoverable && isParent) {
                ScreenRestPrimaryButton(
                    label = if (korean) "Google 계정으로 로그인" else "Sign in with Google",
                    onClick = onParentGoogleSignIn,
                    enabled = authState.available,
                )
            }
            authState.lastError.takeIf(String::isNotBlank)?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        ScreenRestCard(tone = ScreenRestTone.Blocked) {
            ScreenRestSectionHeader(
                title = if (korean) "계정 및 클라우드 데이터 삭제" else "Delete account and cloud data",
                supportingText = if (korean) {
                    "부모·자녀 연결, 요청, 명령과 알림 토큰이 삭제되며 복구할 수 없습니다"
                } else {
                    "Family links, requests, commands, and notification tokens are permanently deleted"
                },
            )
            OutlinedButton(
                onClick = { showDeleteDialog = true },
                enabled = authState.authenticated && (parentState.paired || authState.recoverable),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(ScreenRestTheme.radii.button),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
            ) {
                Text(if (korean) "계정 데이터 삭제" else "Delete account data")
            }
        }
    }
    if (showDeleteDialog) {
        AdminPinConfirmDialog(
            title = if (korean) "계정 및 클라우드 데이터 삭제" else "Delete account and cloud data",
            description = if (korean) {
                "이 기기의 Firebase 계정과 연결된 클라우드 데이터를 삭제합니다. 삭제 후 복구할 수 없습니다."
            } else {
                "This deletes the Firebase account and linked cloud data. It cannot be undone."
            },
            confirmLabel = if (korean) "삭제" else "Delete",
            text = text,
            onDismiss = { showDeleteDialog = false },
            onConfirm = { adminPin ->
                showDeleteDialog = false
                onDeleteAccountAndCloudData(adminPin)
            },
        )
    }
}

@Composable
private fun MoreHelpContent(text: AppStrings, onBack: () -> Unit) {
    val korean = text.appLanguage == AppLanguage.Korean
    Column(verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.md)) {
        MoreDetailHeader(
            title = if (korean) "도움말" else "Help",
            subtitle = if (korean) "폰 쉼의 규칙과 보호 방식을 확인합니다" else "Learn how ScreenRest rules and protection work",
            onBack = onBack,
        )
        listOf(
            Pair(
                if (korean) "시간 규칙" else "Time rules",
                if (korean) "요일별·앱 그룹·앱별 제한은 함께 적용되며 가장 먼저 끝나는 제한으로 차단됩니다." else "Daily, app-group, and per-app limits work together; the first limit reached blocks usage.",
            ),
            Pair(
                if (korean) "사용 가능 앱 규칙" else "Available-app rules",
                if (korean) "스케줄 차단과 허용앱만 모드는 지금 실행할 수 있는 앱의 범위를 정합니다." else "Schedule blocking and allow-only mode decide which apps can run now.",
            ),
            Pair(
                if (korean) "고행 모드" else "Hardship mode",
                if (korean) "단계가 높을수록 변경과 해제가 더 엄격합니다. 3단계는 시작 후 정책 종료 전까지 관리 PIN만으로 해제할 수 없습니다." else "Higher levels make changes and unlocks stricter. Level 3 cannot be ended with the Admin PIN alone before the policy ends.",
            ),
            Pair(
                if (korean) "부모 연결" else "Parent connection",
                if (korean) "가족 탭에서 기기 역할, 연결 코드, 승인 요청과 연결 기기를 관리합니다." else "Use Family to manage device roles, pairing codes, approval requests, and linked devices.",
            ),
            Pair(
                "Emergency Pass",
                if (korean) "모든 고행 3단계에서 7일에 한 번 공유되며 블록 화면의 현재 앱에만 적용됩니다." else "Shared across all level-3 policies once every 7 days and applies only to the current app from the block screen.",
            ),
        ).forEach { (title, description) ->
            ScreenRestCard {
                MoreInformationRow(title = title, description = description)
            }
        }
    }
}

private fun moreEmergencyPassSummary(summary: PolicySummary, korean: Boolean): String {
    val nextAvailableAt = summary.emergencyPassNextAvailableAtMillis
    return if (nextAvailableAt <= 0L || System.currentTimeMillis() >= nextAvailableAt) {
        if (korean) "사용 가능 · 1회" else "Available · 1 use"
    } else {
        if (korean) "사용 완료 · ${formatDateTime(nextAvailableAt)} 갱신" else "Used · renews ${formatDateTime(nextAvailableAt)}"
    }
}
