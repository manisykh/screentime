package com.manisykh.screenrest.usage

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageStatsMergeTest {
    @Test
    fun inCallUi_isReportedAsLaunchablePhoneApp() {
        assertEquals(
            "com.samsung.android.dialer",
            canonicalPhoneUsagePackageName(
                packageName = "com.samsung.android.incallui",
                launchablePackages = setOf("com.samsung.android.dialer"),
            ),
        )
    }

    @Test
    fun partialEvents_keepPackagesOnlySeenInAggregateSources() {
        val merged = mergeUsageSources(
            eventUsageByPackage = mapOf("app.events" to 1_000L),
            statsUsageByPackage = mapOf(
                "app.events" to 900L,
                "app.aggregate" to 2_000L,
            ),
            dailyUsageByPackage = mapOf("app.daily" to 3_000L),
        )

        assertEquals(
            mapOf(
                "app.events" to 1_000L,
                "app.aggregate" to 2_000L,
                "app.daily" to 3_000L,
            ),
            merged,
        )
    }

    @Test
    fun conflictingSources_useLargestPlausibleValue() {
        val merged = mergeUsageSources(
            eventUsageByPackage = mapOf("app.shared" to 2_000L),
            statsUsageByPackage = mapOf("app.shared" to 4_000L),
            dailyUsageByPackage = mapOf("app.shared" to 3_000L),
        )

        assertEquals(4_000L, merged["app.shared"])
    }

    @Test
    fun availableTimeline_doesNotLetAggregateScreenOffTimeOverrideEvents() {
        val merged = mergeUsageSources(
            eventUsageByPackage = mapOf("app.shared" to 2_000L),
            statsUsageByPackage = mapOf("app.shared" to 9_000L),
            dailyUsageByPackage = mapOf("app.shared" to 8_000L),
            eventTimelineAvailable = true,
        )

        assertEquals(mapOf("app.shared" to 2_000L), merged)
    }

    @Test
    fun timeline_closesUsageWhenScreenTurnsOffAndWhenAppGoesBackground() {
        val timeline = aggregateUserVisibleUsageByDay(
            events = listOf(
                UserVisibleUsageEvent(1_000L, "app.video", UserVisibleUsageEventType.Foreground),
                UserVisibleUsageEvent(4_000L, type = UserVisibleUsageEventType.SessionPause),
                UserVisibleUsageEvent(8_000L, type = UserVisibleUsageEventType.SessionResume),
                UserVisibleUsageEvent(10_000L, "app.video", UserVisibleUsageEventType.Background),
            ),
            dayWindows = listOf(UsageDayWindow(0L, 20_000L)),
            rangeEndMillis = 12_000L,
            currentForegroundPackageName = null,
        )

        assertEquals(5_000L, timeline.usageByDay.getValue(0L).getValue("app.video"))
    }

    @Test
    fun timeline_splitsForegroundSessionAtLocalDayBoundary() {
        val timeline = aggregateUserVisibleUsageByDay(
            events = listOf(
                UserVisibleUsageEvent(9_000L, "app.video", UserVisibleUsageEventType.Foreground),
                UserVisibleUsageEvent(12_000L, "app.video", UserVisibleUsageEventType.Background),
            ),
            dayWindows = listOf(
                UsageDayWindow(0L, 10_000L),
                UsageDayWindow(10_000L, 20_000L),
            ),
            rangeEndMillis = 15_000L,
            currentForegroundPackageName = null,
        )

        assertEquals(1_000L, timeline.usageByDay.getValue(0L).getValue("app.video"))
        assertEquals(2_000L, timeline.usageByDay.getValue(10_000L).getValue("app.video"))
    }

    @Test
    fun stoppingPreviousActivity_doesNotCloseAnotherActivityInSameApp() {
        val timeline = aggregateUserVisibleUsageByDay(
            events = listOf(
                UserVisibleUsageEvent(
                    timestampMillis = 1_000L,
                    packageName = "com.kakao.talk",
                    type = UserVisibleUsageEventType.Foreground,
                    activityIdentity = "chat",
                ),
                UserVisibleUsageEvent(
                    timestampMillis = 5_000L,
                    packageName = "com.kakao.talk",
                    type = UserVisibleUsageEventType.Foreground,
                    activityIdentity = "photo",
                ),
                UserVisibleUsageEvent(
                    timestampMillis = 5_100L,
                    packageName = "com.kakao.talk",
                    type = UserVisibleUsageEventType.Background,
                    activityIdentity = "chat",
                ),
                UserVisibleUsageEvent(
                    timestampMillis = 35_000L,
                    packageName = "com.kakao.talk",
                    type = UserVisibleUsageEventType.Background,
                    activityIdentity = "photo",
                ),
            ),
            dayWindows = listOf(UsageDayWindow(0L, 40_000L)),
            rangeEndMillis = 40_000L,
            currentForegroundPackageName = null,
        )

        assertEquals(
            34_000L,
            timeline.usageByDay.getValue(0L).getValue("com.kakao.talk"),
        )
    }
}
