package com.manisykh.screenrest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.manisykh.screenrest.data.AppLanguage
import com.manisykh.screenrest.ui.designsystem.*

@Composable
internal fun ChildTopAppsSharingCard(
    enabled: Boolean,
    text: AppStrings,
    onChanged: (Boolean) -> Unit,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    var showConsent by rememberSaveable { mutableStateOf(false) }
    ScreenRestCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (korean) "앱별 사용 현황 공유" else "Share app usage summary",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            ScreenRestStatusPill(
                label = if (enabled) {
                    if (korean) "공유 중" else "Sharing"
                } else {
                    if (korean) "꺼짐" else "Off"
                },
                tone = if (enabled) ScreenRestTone.Success else ScreenRestTone.Neutral,
            )
        }
        Text(
            if (korean) "오늘 많이 사용한 앱 최대 5개의 이름과 사용 시간을 연결된 부모에게 보여줍니다."
            else "Share names and durations of up to five most-used apps with linked parents.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (enabled) {
            TextButton(onClick = { onChanged(false) }) {
                Text(if (korean) "공유 중지" else "Stop sharing")
            }
        } else {
            Button(onClick = { showConsent = true }) {
                Text(if (korean) "공유 내용 확인" else "Review sharing")
            }
        }
    }
    if (showConsent) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showConsent = false },
            title = { Text(if (korean) "앱별 사용 현황 공유" else "Share app usage summary") },
            text = {
                Text(
                    if (korean) {
                        "자녀 기기에서 오늘 많이 사용한 앱 최대 5개의 이름과 사용 시간을 Firebase로 전송해 연결된 부모 기기에 표시합니다. 전체 설치 앱 목록과 앱을 사용한 정확한 시각은 보내지 않습니다. 공유를 중지해도 오프라인인 동안에는 이전 요약이 보일 수 있으며, 재연결 후 갱신됩니다."
                    } else {
                        "Up to five app names and today's usage durations are sent through Firebase to linked parents. The full installed-app list and exact usage times are not sent. If this device is offline when sharing is stopped, the previous summary may remain visible until it reconnects."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConsent = false
                    onChanged(true)
                }) { Text(if (korean) "동의하고 공유" else "Agree and share") }
            },
            dismissButton = {
                TextButton(onClick = { showConsent = false }) {
                    Text(if (korean) "나중에" else "Not now")
                }
            },
        )
    }
}

@Composable
internal fun FamilyDeviceCard(
    name: String,
    deviceLabel: String,
    lastSyncMillis: Long,
    text: AppStrings,
    onSync: () -> Unit,
    syncEnabled: Boolean = true,
) {
    val korean = text.appLanguage == AppLanguage.Korean
    ScreenRestCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.sm),
        ) {
            MoreMenuIcon(R.drawable.ic_family_device, ScreenRestTone.Primary)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.xxs),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = deviceLabel.ifBlank { if (korean) "기기 정보" else "Device" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onSync, enabled = syncEnabled) {
                Icon(
                    painter = painterResource(R.drawable.ic_family_sync),
                    contentDescription = if (syncEnabled) {
                        if (korean) "새 사용 현황 요청" else "Request fresh usage"
                    } else {
                        if (korean) "다시 요청은 1분 후 가능" else "Try again in one minute"
                    },
                    tint = if (syncEnabled) ScreenRestTheme.colors.success
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(1.dp)
                .background(ScreenRestTheme.colors.divider),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoreMenuIcon(R.drawable.ic_family_sync, ScreenRestTone.Success)
            Text(
                text = familySyncLabel(lastSyncMillis, korean),
                modifier = Modifier.weight(1f).padding(start = ScreenRestTheme.spacing.sm),
                style = MaterialTheme.typography.bodyMedium,
                color = ScreenRestTheme.colors.success,
            )
            if (!syncEnabled) {
                ScreenRestStatusPill(
                    label = if (korean) "잠시 후" else "Wait",
                    tone = ScreenRestTone.Neutral,
                )
            }
        }
    }
}

