package com.manisykh.screenrest

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.manisykh.screenrest.data.AppLanguage
import com.manisykh.screenrest.data.ChildUsageRefreshRequest
import com.manisykh.screenrest.data.ChildUsageSnapshot
import com.manisykh.screenrest.data.ImmediateBlockAvailability
import com.manisykh.screenrest.data.ImmediateBlockReadState
import com.manisykh.screenrest.data.LinkedChildDevice
import com.manisykh.screenrest.data.ParentManagementState
import com.manisykh.screenrest.data.immediateBlockAvailability
import com.manisykh.screenrest.ui.designsystem.*
import kotlinx.coroutines.delay

@Composable
internal fun FamilyParentDashboard(
    parentState: ParentManagementState,
    childDevices: List<LinkedChildDevice>,
    childUsageSnapshots: Map<String, ChildUsageSnapshot>,
    childUsageRefreshRequests: Map<String, ChildUsageRefreshRequest>,
    childImmediateBlocks: Map<String, ImmediateBlockReadState>,
    text: AppStrings,
    onOpenManagement: () -> Unit,
    onSyncParentDevice: () -> Unit,
    onCheckImmediateBlock: (String) -> Unit,
    onStartImmediateBlock: (String, Int, String) -> Unit,
    onStopImmediateBlock: (String, String, String) -> Unit,
    onApproveRemoteUnlockRequest: (String, Int, Boolean) -> Unit,
    onRejectRemoteUnlockRequest: (String) -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    var selectedChildId by rememberSaveable {
        mutableStateOf(childDevices.firstOrNull()?.childDeviceId.orEmpty())
    }
    LaunchedEffect(childDevices, selectedChildId) {
        if (childDevices.none { child -> child.childDeviceId == selectedChildId }) {
            selectedChildId = childDevices.firstOrNull()?.childDeviceId.orEmpty()
        }
    }
    val selectedChild = childDevices.firstOrNull { child -> child.childDeviceId == selectedChildId }
        ?: childDevices.firstOrNull()
    val selectedBlockRead = selectedChild?.childDeviceId?.let(childImmediateBlocks::get)
    val selectedBlock = (selectedBlockRead as? ImmediateBlockReadState.Known)?.block
    val selectedRefreshRequest = selectedChild?.childDeviceId?.let(childUsageRefreshRequests::get)
    var blockClockMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(selectedBlock?.requestId) {
        while (true) {
            delay(30_000L)
            blockClockMillis = System.currentTimeMillis()
        }
    }
    val blockAvailability = immediateBlockAvailability(selectedBlockRead, blockClockMillis)
    val blockActive = blockAvailability == ImmediateBlockAvailability.Active
    var showDurationDialog by rememberSaveable { mutableStateOf(false) }
    var showBlockPinDialog by rememberSaveable { mutableStateOf(false) }
    var showStopPinDialog by rememberSaveable { mutableStateOf(false) }
    var selectedBlockMinutes by rememberSaveable { mutableStateOf(60) }
    val selectedRequests = parentState.remoteUnlockRequests.filter { request ->
        selectedChild == null || request.childDeviceId == selectedChild.childDeviceId
    }

    FamilySectionTitle(if (korean) "연결된 자녀" else "Connected children")
    if (childDevices.size > 1) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.xs),
        ) {
            childDevices.forEach { child ->
                Surface(
                    onClick = { selectedChildId = child.childDeviceId },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(ScreenRestTheme.radii.chip),
                    color = if (child.childDeviceId == selectedChild?.childDeviceId) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    border = BorderStroke(
                        1.dp,
                        if (child.childDeviceId == selectedChild?.childDeviceId) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
                ) {
                    Box(
                        modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = child.childDeviceName.ifBlank { child.childDeviceId },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (child.childDeviceId == selectedChild?.childDeviceId) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
    selectedChild?.let { child ->
        FamilyDeviceCard(
            name = child.childDeviceName.ifBlank { child.childDeviceId },
            deviceLabel = child.childDeviceId.take(12).ifBlank { child.pairingCode },
            lastSyncMillis = childUsageSnapshots[child.childDeviceId]?.capturedAtMillis
                ?: parentState.lastSyncMillis,
            text = text,
            onSync = onSyncParentDevice,
            syncEnabled = selectedRefreshRequest?.canRequestAgain(blockClockMillis) != false,
        )
    }

    FamilySectionTitle(if (korean) "자녀 사용 현황" else "Child usage status")
    FamilyRemoteSnapshotCard(
        snapshot = selectedChild?.childDeviceId?.let(childUsageSnapshots::get),
        refreshRequest = selectedRefreshRequest,
        text = text,
    )

    FamilySectionTitle(if (korean) "부모님이 할 수 있는 작업" else "Parent actions")
    ScreenRestCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.sm),
        ) {
            MoreMenuIcon(R.drawable.ic_family_block,
                if (blockActive) ScreenRestTone.Warning else ScreenRestTone.Blocked)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (korean) "즉시 차단" else "Immediate block",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (blockActive) {
                        if (korean) "자녀 기기에 적용 중" else "Active on child device"
                    } else {
                        if (korean) "필요할 때 사용을 잠시 멈춥니다" else "Pause use when needed"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Button(
            onClick = {
                when (blockAvailability) {
                    ImmediateBlockAvailability.Active -> showStopPinDialog = true
                    ImmediateBlockAvailability.Inactive -> showDurationDialog = true
                    ImmediateBlockAvailability.Unverified, ImmediateBlockAvailability.Failed ->
                        selectedChild?.childDeviceId?.let(onCheckImmediateBlock)
                }
            },
            enabled = selectedChild != null,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(ScreenRestTheme.radii.button),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (blockAvailability == ImmediateBlockAvailability.Inactive)
                    ScreenRestPalette.Coral else ScreenRestPalette.Cobalt,
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_family_block),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(ScreenRestTheme.spacing.xs))
            Text(when (blockAvailability) {
                ImmediateBlockAvailability.Active -> if (korean) "차단 종료" else "End block"
                ImmediateBlockAvailability.Inactive -> if (korean) "지금 차단" else "Block now"
                ImmediateBlockAvailability.Unverified -> if (korean) "차단 상태 확인" else "Check block status"
                ImmediateBlockAvailability.Failed -> if (korean) "상태 다시 확인" else "Retry block status"
            })
        }
        if (blockAvailability == ImmediateBlockAvailability.Unverified ||
            blockAvailability == ImmediateBlockAvailability.Failed
        ) {
            Text(
                text = if (blockAvailability == ImmediateBlockAvailability.Failed) {
                    if (korean) "차단 상태를 읽지 못했습니다. 다시 확인해 주세요."
                    else "Could not read block status. Tap to retry."
                } else {
                    if (korean) "차단 상태 확인 중 · 오래 걸리면 버튼을 눌러 주세요."
                    else "Checking block status · tap if this takes too long."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (blockActive && selectedBlock != null) {
            val remainingMinutes = (((selectedBlock.expiresAtMillis ?: blockClockMillis) -
                blockClockMillis + 59_999L) / 60_000L).toInt().coerceAtLeast(0)
            ScreenRestStatusPill(
                label = if (selectedBlock.appliedAtMillis > 0L) {
                    if (korean) "차단 적용됨 · ${formatLimitMinutesLabel(remainingMinutes)} 남음"
                    else "Block applied · ${formatLimitMinutesLabel(remainingMinutes)} left"
                } else {
                    if (korean) "서버 등록됨 · 자녀 기기 적용 대기" else "Saved · waiting for child device"
                },
                tone = if (selectedBlock.appliedAtMillis > 0L) ScreenRestTone.Warning
                    else ScreenRestTone.Neutral,
            )
            Text(
                text = if (korean) "제한 없는 앱과 필수 시스템 앱은 계속 사용 가능 · 시간이 끝나면 자동 해제"
                    else "Unrestricted and essential apps remain available · ends automatically",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selectedBlock != null && selectedBlock.revokedAtMillis > 0L &&
            (selectedBlock.expiresAtMillis ?: 0L) > blockClockMillis
        ) {
            ScreenRestStatusPill(
                label = if (selectedBlock.releasedAtMillis > 0L) {
                    if (korean) "차단 종료 확인됨" else "Block ended on child"
                } else {
                    if (korean) "종료 요청됨 · 자녀 기기 확인 대기" else "Stop sent · waiting for child device"
                },
                tone = ScreenRestTone.Neutral,
            )
        }
        val childSnapshot = selectedChild?.childDeviceId?.let(childUsageSnapshots::get)
        if (childSnapshot?.protectionPaused == true || childSnapshot?.usageAccessReady == false) {
            Text(
                text = if (korean) "자녀 기기의 보호가 중지되었거나 사용 기록 권한이 없습니다. 자녀 기기에서 확인해 주세요."
                    else "Protection is paused or usage access is missing on the child device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (showDurationDialog && selectedChild != null) {
        Dialog(onDismissRequest = { showDurationDialog = false }) {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        if (korean) "지금 차단" else "Block now",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (korean) "일반 앱을 선택한 시간 동안 차단합니다. 제한 없는 앱과 필수 시스템 앱은 계속 사용됩니다."
                        else "Blocks regular apps for the selected duration. Unrestricted and essential apps stay available.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(15, 30, 60, 120, 240, 480, 1440).forEach { minutes ->
                            Surface(
                                onClick = { selectedBlockMinutes = minutes },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedBlockMinutes == minutes)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Text(
                                    formatLimitMinutesLabel(minutes),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                )
                            }
                        }
                    }
                    Text(
                        if (korean) "오프라인이면 종료 전 재연결할 때만 적용됩니다. 기존 고행 차단은 유지됩니다."
                        else "If offline, applies only when reconnected before expiry. Existing hardship blocks stay in place.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showDurationDialog = false }, modifier = Modifier.weight(1f)) {
                            Text(if (korean) "취소" else "Cancel")
                        }
                        Button(onClick = {
                            showDurationDialog = false
                            showBlockPinDialog = true
                        }, modifier = Modifier.weight(1f)) {
                            Text(if (korean) "다음" else "Next")
                        }
                    }
                }
            }
        }
    }
    if (showBlockPinDialog && selectedChild != null) {
        AdminPinConfirmDialog(
            title = if (korean) "차단 확인" else "Confirm block",
            description = if (korean) "${selectedChild.childDeviceName} · ${formatLimitMinutesLabel(selectedBlockMinutes)} 동안 차단"
                else "Block ${selectedChild.childDeviceName} for ${formatLimitMinutesLabel(selectedBlockMinutes)}",
            confirmLabel = if (korean) "차단 요청" else "Send block",
            text = text,
            onDismiss = { showBlockPinDialog = false },
            onConfirm = { pin ->
                onStartImmediateBlock(selectedChild.childDeviceId, selectedBlockMinutes, pin)
                showBlockPinDialog = false
            },
        )
    }
    if (showStopPinDialog && selectedChild != null && selectedBlock != null) {
        AdminPinConfirmDialog(
            title = if (korean) "차단 종료" else "End block",
            description = if (korean) "이 즉시 차단만 종료합니다. 기존 규칙과 고행 차단은 유지됩니다."
                else "Only this immediate block ends. Existing rules and hardship blocks remain.",
            confirmLabel = if (korean) "종료 요청" else "Send stop",
            text = text,
            onDismiss = { showStopPinDialog = false },
            onConfirm = { pin ->
                onStopImmediateBlock(selectedChild.childDeviceId, selectedBlock.requestId, pin)
                showStopPinDialog = false
            },
        )
    }

    FamilySectionTitle(if (korean) "승인 요청" else "Approval requests")
    RemoteUnlockRequestList(
        requests = selectedRequests,
        text = text,
        onApproveExtraTime = { request, minutes ->
            onApproveRemoteUnlockRequest(request.id, minutes, false)
        },
        onApproveUnlockToday = { request ->
            onApproveRemoteUnlockRequest(request.id, 0, true)
        },
        onReject = { request -> onRejectRemoteUnlockRequest(request.id) },
    )

    FamilyManagementEntry(text = text, onClick = onOpenManagement)
}
