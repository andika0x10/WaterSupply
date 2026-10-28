package com.andika.watersupply.widget

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

internal data class WidgetProgress(val total: Int, val target: Int, val canUndo: Boolean)

internal sealed interface WidgetState {
    data class Ready(val progress: WidgetProgress) : WidgetState
    data object Failed : WidgetState
}

/** An unavailable database must never look like a successfully loaded empty day. */
internal suspend fun loadWidgetState(
    onError: (Exception) -> Unit = {},
    load: suspend () -> WidgetProgress,
): WidgetState = try {
    withTimeoutOrNull(10_000) { WidgetState.Ready(load()) } ?: WidgetState.Failed
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    onError(error)
    WidgetState.Failed
}
