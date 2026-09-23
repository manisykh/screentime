package com.manisykh.screenrest.blocking

import com.manisykh.screenrest.data.RemoteRequestBlockReason
import com.manisykh.screenrest.safety.BlockDecision

internal fun BlockDecision.isWouldBlock(): Boolean {
    return this == BlockDecision.WouldBlockTotalLimit ||
        this == BlockDecision.WouldBlockSchedule ||
        this == BlockDecision.WouldBlockAllowOnly ||
        this == BlockDecision.WouldBlockGroupLimit ||
        this == BlockDecision.WouldBlockAppLimit ||
        this == BlockDecision.WouldBlockImmediate
}

internal fun BlockDecision.toLogReason(): String {
    return when (this) {
        BlockDecision.AllowedSafeMode -> "safe mode"
        BlockDecision.AllowedPolicyDisabled -> "policy disabled"
        BlockDecision.AllowedWhitelist -> "whitelist"
        BlockDecision.AllowedNoLimit -> "no limit"
        BlockDecision.AllowedUnderLimit -> "under limit"
        BlockDecision.WouldBlockTotalLimit -> "total limit exceeded"
        BlockDecision.WouldBlockSchedule -> "schedule block active"
        BlockDecision.WouldBlockAllowOnly -> "allow-only mode active"
        BlockDecision.WouldBlockGroupLimit -> "group limit exceeded"
        BlockDecision.WouldBlockAppLimit -> "app limit exceeded"
        BlockDecision.WouldBlockImmediate -> "parent immediate block active"
    }
}

internal fun BlockDecision.toRemoteRequestBlockReason(): RemoteRequestBlockReason {
    return when (this) {
        BlockDecision.WouldBlockTotalLimit -> RemoteRequestBlockReason.DailyLimit
        BlockDecision.WouldBlockSchedule -> RemoteRequestBlockReason.ScheduleBlock
        BlockDecision.WouldBlockAllowOnly -> RemoteRequestBlockReason.AllowOnlyMode
        BlockDecision.WouldBlockGroupLimit -> RemoteRequestBlockReason.AppGroupLimit
        BlockDecision.WouldBlockAppLimit,
        BlockDecision.WouldBlockImmediate,
        BlockDecision.AllowedSafeMode,
        BlockDecision.AllowedPolicyDisabled,
        BlockDecision.AllowedWhitelist,
        BlockDecision.AllowedNoLimit,
        BlockDecision.AllowedUnderLimit -> RemoteRequestBlockReason.AppLimit
    }
}

internal fun BlockDecision.toBlockCategory(): String {
    return when (this) {
        BlockDecision.WouldBlockAppLimit -> "APP_LIMIT"
        BlockDecision.WouldBlockGroupLimit -> "GROUP_LIMIT"
        BlockDecision.WouldBlockTotalLimit -> "DAILY_LIMIT"
        BlockDecision.WouldBlockSchedule -> "SCHEDULE"
        BlockDecision.WouldBlockAllowOnly -> "ALLOW_ONLY"
        BlockDecision.WouldBlockImmediate -> "PARENT_IMMEDIATE"
        BlockDecision.AllowedSafeMode -> "SAFE_MODE"
        BlockDecision.AllowedPolicyDisabled -> "POLICY_OFF"
        BlockDecision.AllowedWhitelist -> "WHITELIST"
        BlockDecision.AllowedNoLimit -> "NO_LIMIT"
        BlockDecision.AllowedUnderLimit -> "UNDER_LIMIT"
    }
}

internal fun BlockDecision.toKoreanBlockReason(): String {
    return when (this) {
        BlockDecision.WouldBlockAppLimit -> "?깅퀎 ?쒗븳 珥덇낵"
        BlockDecision.WouldBlockGroupLimit -> "洹몃９ ?쒗븳 珥덇낵"
        BlockDecision.WouldBlockTotalLimit -> "?쇱씪 ?쒗븳 珥덇낵"
        BlockDecision.WouldBlockSchedule -> "?ㅼ?以?李⑤떒"
        BlockDecision.WouldBlockAllowOnly -> "?덉슜??紐⑤뱶 李⑤떒"
        BlockDecision.WouldBlockImmediate -> "부모가 지금 차단 중"
        BlockDecision.AllowedSafeMode -> "?덉쟾 紐⑤뱶"
        BlockDecision.AllowedPolicyDisabled -> "?뺤콉 鍮꾪솢?깊솕"
        BlockDecision.AllowedWhitelist -> "?꾩닔 ?덉쇅"
        BlockDecision.AllowedNoLimit -> "?쒗븳 ?놁쓬"
        BlockDecision.AllowedUnderLimit -> "?쒗븳 誘몃쭔"
    }
}

