package com.bunko.reader.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private val PullRefreshContainerColor = Color(0xFF24352F)
private val PullRefreshIndicatorColor = Color(0xFF86D39B)

/**
 * The shared brand-green M3 Expressive pull-to-refresh indicator, aligned to the top center.
 * Use as the `indicator` of a [androidx.compose.material3.pulltorefresh.PullToRefreshBox] so every
 * refreshable screen shows the same thing. The single source of the brand colors.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BoxScope.BunkoPullToRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean
) {
    PullToRefreshDefaults.LoadingIndicator(
        state = state,
        isRefreshing = isRefreshing,
        modifier = Modifier.align(Alignment.TopCenter),
        containerColor = PullRefreshContainerColor,
        color = PullRefreshIndicatorColor
    )
}
