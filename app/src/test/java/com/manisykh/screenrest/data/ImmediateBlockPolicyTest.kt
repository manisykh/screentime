package com.manisykh.screenrest.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmediateBlockPolicyTest {
    @Test
    fun unreadableParentState_neverLooksLikeAnInactiveBlock() {
        assertEquals(
            ImmediateBlockAvailability.Unverified,
            immediateBlockAvailability(null, 2_000L),
        )
        assertEquals(
            ImmediateBlockAvailability.Failed,
            immediateBlockAvailability(ImmediateBlockReadState.Failed, 2_000L),
        )
        assertEquals(
            ImmediateBlockAvailability.Inactive,
            immediateBlockAvailability(ImmediateBlockReadState.Known(null), 2_000L),
        )
    }

    @Test
    fun fourHourOrder_staysTerminableBeforeItsActualExpiry() {
        val requestedAt = 1_000L
        val active = ImmediateBlockReadState.Known(
            ImmediateBlockState(
                requestId = "order-4h",
                requestedAtMillis = requestedAt,
                expiresAtMillis = requestedAt + 4L * 60L * 60_000L,
            ),
        )
        assertEquals(
            ImmediateBlockAvailability.Active,
            immediateBlockAvailability(active, requestedAt + 3L * 60L * 60_000L),
        )
        assertEquals(
            ImmediateBlockAvailability.Inactive,
            immediateBlockAvailability(active, requestedAt + 4L * 60L * 60_000L),
        )
    }

    @Test
    fun lateEmptyFetch_doesNotEraseAnActiveListenerOrder() {
        val active = ImmediateBlockReadState.Known(
            ImmediateBlockState(
                requestId = "order-4h",
                requestedAtMillis = 1_000L,
                expiresAtMillis = 14_401_000L,
            ),
        )
        val merged = mergeImmediateBlockReadState(active, ImmediateBlockReadState.Known(null))
        assertEquals(ImmediateBlockAvailability.Active, immediateBlockAvailability(merged, 2_000L))
    }

    @Test
    fun lateOldFetch_doesNotUndoRevocation() {
        val base = ImmediateBlockState(
            requestId = "order-4h",
            requestedAtMillis = 1_000L,
            expiresAtMillis = 14_401_000L,
        )
        val revoked = ImmediateBlockReadState.Known(base.copy(revokedAtMillis = 5_000L))
        val merged = mergeImmediateBlockReadState(revoked, ImmediateBlockReadState.Known(base))
        assertEquals(ImmediateBlockAvailability.Inactive, immediateBlockAvailability(merged, 6_000L))
    }

    @Test
    fun missingExpiry_neverCreatesAnIndefiniteBlock() {
        val block = ImmediateBlockState(
            requestId = "order-1", requestedAtMillis = 1_000L,
        )
        assertFalse(block.isActiveAt(2_000L))
    }

    @Test
    fun expiresAtChosenTime_evenWhenChildReconnectsLater() {
        val block = ImmediateBlockState(
            requestId = "order-1",
            childDeviceId = "child-1",
            requestedAtMillis = 1_000L,
            expiresAtMillis = 11_000L,
        )
        assertTrue(block.appliesTo("com.example.app", 10_999L))
        assertFalse(block.appliesTo("com.example.app", 11_000L))
    }

    @Test
    fun allOrdinaryApps_areCoveredByTheSameOrder() {
        val block = ImmediateBlockState(
            requestId = "order-1",
            childDeviceId = "child-1",
            requestedAtMillis = 1_000L,
            expiresAtMillis = 11_000L,
        )
        assertTrue(block.appliesTo("com.example.game", 2_000L))
        assertTrue(block.appliesTo("com.example.study", 2_000L))
    }

    @Test
    fun revokedOrder_neverAppliesAgain() {
        val block = ImmediateBlockState(
            requestId = "order-1",
            requestedAtMillis = 1_000L,
            expiresAtMillis = 11_000L,
            revokedAtMillis = 3_000L,
        )
        assertFalse(block.isActiveAt(4_000L))
    }
}
