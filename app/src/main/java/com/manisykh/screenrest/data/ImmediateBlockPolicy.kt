package com.manisykh.screenrest.data

/** A separate blocking layer; it does not change any of the five saved usage policies. */
data class ImmediateBlockState(
    val requestId: String = "",
    val childDeviceId: String = "",
    val requestedAtMillis: Long = 0L,
    val expiresAtMillis: Long? = null,
    val revokedAtMillis: Long = 0L,
    val parentUid: String = "",
    val appliedAtMillis: Long = 0L,
    val releasedAtMillis: Long = 0L,
) {
    fun isActiveAt(nowMillis: Long): Boolean =
        requestId.isNotBlank() &&
            requestedAtMillis > 0L &&
            nowMillis >= requestedAtMillis &&
            revokedAtMillis == 0L &&
            expiresAtMillis != null && nowMillis < expiresAtMillis

    fun appliesTo(packageName: String, nowMillis: Long): Boolean =
        packageName.isNotBlank() && isActiveAt(nowMillis)
}

sealed interface ImmediateBlockReadState {
    data class Known(val block: ImmediateBlockState?) : ImmediateBlockReadState
    data object Failed : ImmediateBlockReadState
}

enum class ImmediateBlockAvailability { Unverified, Failed, Active, Inactive }

fun immediateBlockAvailability(
    readState: ImmediateBlockReadState?,
    nowMillis: Long,
): ImmediateBlockAvailability = when {
    readState == null -> ImmediateBlockAvailability.Unverified
    readState == ImmediateBlockReadState.Failed -> ImmediateBlockAvailability.Failed
    (readState as ImmediateBlockReadState.Known).block?.isActiveAt(nowMillis) == true ->
        ImmediateBlockAvailability.Active
    else -> ImmediateBlockAvailability.Inactive
}

/** Firestore listener and explicit fetch can finish out of order; never regress a confirmed order. */
fun mergeImmediateBlockReadState(
    current: ImmediateBlockReadState?,
    incoming: ImmediateBlockReadState,
): ImmediateBlockReadState {
    if (current !is ImmediateBlockReadState.Known || incoming !is ImmediateBlockReadState.Known) {
        return if (current is ImmediateBlockReadState.Known) current else incoming
    }
    val oldBlock = current.block ?: return incoming
    val newBlock = incoming.block ?: return current
    if (oldBlock.requestId != newBlock.requestId) {
        return if (oldBlock.requestedAtMillis > newBlock.requestedAtMillis) current else incoming
    }
    return ImmediateBlockReadState.Known(
        newBlock.copy(
            revokedAtMillis = maxOf(oldBlock.revokedAtMillis, newBlock.revokedAtMillis),
            appliedAtMillis = maxOf(oldBlock.appliedAtMillis, newBlock.appliedAtMillis),
            releasedAtMillis = maxOf(oldBlock.releasedAtMillis, newBlock.releasedAtMillis),
        ),
    )
}
