package com.manisykh.screenrest

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight
import com.manisykh.screenrest.data.AppLanguage
import com.manisykh.screenrest.data.ImmediateBlockState
import com.manisykh.screenrest.data.ParentManagementState
import com.manisykh.screenrest.ui.designsystem.*
import com.manisykh.screenrest.ui.safety.PolicySummary
import kotlinx.coroutines.delay

@Composable
internal fun FamilyChildDashboard(
    parentState: ParentManagementState,
    childTopAppsSharingEnabled: Boolean,
    immediateBlock: ImmediateBlockState,
    policySummary: PolicySummary,
    safeModeEnabled: Boolean,
    policyEnforcementEnabled: Boolean,
    text: AppStrings,
    onOpenManagement: () -> Unit,
    onSyncParentDevice: () -> Unit,
    onChildTopAppsSharingChanged: (Boolean) -> Unit,
    onOpenLocalRules: () -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    var blockClockMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(immediateBlock.requestId) {
        while (true) {
            delay(30_000L)
            blockClockMillis = System.currentTimeMillis()
        }
    }
    if (parentState.paired && immediateBlock.isActiveAt(blockClockMillis) &&
        parentState.linkedParentDevices.any { it.parentUid == immediateBlock.parentUid }
    ) {
        ScreenRestCard(tone = ScreenRestTone.Warning) {
            Text(
                if (safeModeEnabled || !policyEnforcementEnabled) {
                    if (korean) "부모 차단 요청 · 보호 중지 상태" else "Parent block requested · protection paused"
                } else {
                    if (korean) "부모가 지금 차단 중입니다" else "Parent block is active"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (safeModeEnabled || !policyEnforcementEnabled) {
                    if (korean) "차단을 적용하려면 자녀 기기의 보호 기능을 켜야 합니다."
                    else "Turn on protection on the child device to apply the block."
                } else {
                    if (korean) "제한 없는 앱과 필수 시스템 앱은 사용 가능합니다. 지정된 시간이 끝나면 자동 해제됩니다."
                    else "Unrestricted and essential apps remain available. The block ends automatically."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    val profileName = parentState.localProfileName.ifBlank {
        parentState.childDeviceName.ifBlank { if (korean) "내 기기" else "This device" }
    }
    FamilySectionTitle(if (korean) "내 기기" else "This device")
    FamilyDeviceCard(
        name = profileName,
        deviceLabel = parentState.childDeviceName.ifBlank { parentState.childDeviceId.take(12) },
        lastSyncMillis = parentState.lastSyncMillis,
        text = text,
        onSync = onSyncParentDevice,
    )

    FamilySectionTitle(if (korean) "오늘 사용 현황" else "Today's usage")
    FamilyLocalUsageCard(
        summary = policySummary,
        safeModeEnabled = safeModeEnabled,
        policyEnforcementEnabled = policyEnforcementEnabled,
        text = text,
        onOpenRules = onOpenLocalRules,
    )

    ChildTopAppsSharingCard(
        enabled = childTopAppsSharingEnabled,
        text = text,
        onChanged = onChildTopAppsSharingChanged,
    )

    FamilySectionTitle(if (korean) "연결된 부모" else "Connected parents")
    ScreenRestCard {
        if (parentState.linkedParentDevices.isEmpty()) {
            Text(
                text = parentState.parentAccountId.ifBlank {
                    if (korean) "연결된 부모 정보가 없습니다" else "No linked parent information"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            parentState.linkedParentDevices.forEach { parent ->
                ScreenRestListRow(
                    title = parent.parentDisplayName.ifBlank { parent.parentUid },
                    supportingText = if (korean) "연결된 부모 기기" else "Linked parent device",
                    leading = { MoreMenuIcon(R.drawable.ic_nav_family, ScreenRestTone.Success) },
                    trailing = {
                        ScreenRestStatusPill(
                            label = if (korean) "연결됨" else "Linked",
                            tone = ScreenRestTone.Success,
                        )
                    },
                )
            }
        }
    }

    FamilyChildRequestHistory(requests = parentState.remoteUnlockRequests, text = text)
    FamilyManagementEntry(text = text, onClick = onOpenManagement)
}
