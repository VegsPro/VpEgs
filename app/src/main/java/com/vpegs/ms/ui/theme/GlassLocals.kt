
package com.vpegs.ms.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import dev.chrisbanes.haze.HazeState

/** When true, cards use Haze backdrop blur (real glass). */
val LocalGlassEnabled = staticCompositionLocalOf { true }

/** Shared Haze state from root; null if glass disabled. */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }
