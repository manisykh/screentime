package com.manisykh.screenrest.blocking

import android.graphics.Color
import com.manisykh.screenrest.data.HardshipLevel

/** Visual-only contract shared by the Activity fallback and the foreground overlay. */
internal data class BlockScreenVisualModel(
    val background: Int,
    val panel: Int,
    val panelBorder: Int,
    val accent: Int,
    val accentSoft: Int,
    val onDark: Int = Color.WHITE,
    val onDarkMuted: Int = Color.rgb(190, 202, 223),
    val onPanel: Int = Color.rgb(20, 35, 61),
    val onPanelMuted: Int = Color.rgb(88, 101, 124),
) {
    companion object {
        fun categoryLabel(reason: String, korean: Boolean): String = when (reason) {
            "total limit exceeded" -> if (korean) "하루 제한" else "Daily limit"
            "group limit exceeded" -> if (korean) "앱 그룹" else "App group"
            "schedule block active" -> if (korean) "스케줄 차단" else "Schedule"
            "allow-only mode active" -> if (korean) "허용앱만" else "Allowed apps only"
            "app limit exceeded" -> if (korean) "앱별 제한" else "App limit"
            "parent immediate block active" -> if (korean) "부모 즉시 차단" else "Parent block"
            else -> if (korean) "사용 제한" else "Use restricted"
        }

        fun forReason(reason: String, hardshipLevel: HardshipLevel): BlockScreenVisualModel {
            val accent = when (hardshipLevel) {
                HardshipLevel.Level1 -> Color.rgb(220, 160, 49)
                HardshipLevel.Level2 -> Color.rgb(238, 111, 46)
                HardshipLevel.Level3 -> Color.rgb(217, 65, 101)
                HardshipLevel.Off -> when (reason) {
                    "total limit exceeded" -> Color.rgb(255, 111, 103)
                    "group limit exceeded" -> Color.rgb(242, 157, 46)
                    "schedule block active" -> Color.rgb(126, 129, 245)
                    "allow-only mode active" -> Color.rgb(34, 180, 166)
                    "parent immediate block active" -> Color.rgb(220, 65, 65)
                    else -> Color.rgb(89, 137, 248)
                }
            }
            return BlockScreenVisualModel(
                background = Color.rgb(16, 30, 54),
                panel = Color.rgb(250, 252, 255),
                panelBorder = Color.rgb(213, 222, 236),
                accent = accent,
                accentSoft = Color.argb(37, Color.red(accent), Color.green(accent), Color.blue(accent)),
            )
        }
    }
}
