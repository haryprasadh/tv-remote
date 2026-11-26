package com.hari.tvremote.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hari.tvremote.TvConfig
import com.hari.tvremote.remote.TvKey
import com.hari.tvremote.remote.TvRemote

/**
 * A big remote button with a haptic tick. Short tap -> [onTap]. If [onHoldStart] is given, holding
 * past the threshold calls it once and [onHoldEnd] on release (or if the button goes away mid-hold).
 */
@Composable
fun PressKey(
    label: String,
    enabled: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    /** Square size; null = fill the available width and wrap the content's height. */
    size: Dp? = 76.dp,
    shape: Shape = CircleShape,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    showLabel: Boolean = true,
    onHoldStart: (() -> Unit)? = null,
    onHoldEnd: (() -> Unit)? = null,
    content: @Composable (Color) -> Unit,
) {
    val view = LocalView.current
    var pressed by remember { mutableStateOf(false) }
    val tap by rememberUpdatedState(onTap)
    val holdStart by rememberUpdatedState(onHoldStart)
    val holdEnd by rememberUpdatedState(onHoldEnd)
    val pressedColor = contentColor.copy(alpha = 0.18f).compositeOver(container)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .then(if (size != null) Modifier.size(size) else Modifier.fillMaxWidth())
                .scale(if (pressed) 0.94f else 1f)
                .alpha(if (enabled) 1f else 0.38f)
                .clip(shape)
                .background(if (pressed) pressedColor else container)
                .semantics {
                    role = Role.Button
                    contentDescription = label
                    if (!enabled) disabled()
                    onClick(label) {
                        tap()
                        true
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        pressed = true
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        try {
                            val start = holdStart
                            if (start == null) {
                                if (waitForUpOrCancellation() != null) tap()
                            } else {
                                var finishedEarly = false
                                var up: PointerInputChange? = null
                                withTimeoutOrNull(TvConfig.HOLD_THRESHOLD_MS) {
                                    up = waitForUpOrCancellation()
                                    finishedEarly = true
                                }
                                if (finishedEarly) {
                                    if (up != null) tap()
                                } else {
                                    start()
                                    try {
                                        waitForUpOrCancellation()
                                    } finally {
                                        holdEnd?.invoke()
                                    }
                                }
                            }
                        } finally {
                            pressed = false
                        }
                    }
                },
        ) {
            content(contentColor)
        }
        if (showLabel) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** A [PressKey] bound to a TV key: SHORT on tap, START_LONG/END_LONG on hold for holdable keys. */
@Composable
fun TvKeyButton(
    key: TvKey,
    icon: Int,
    remote: TvRemote,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 76.dp,
    shape: Shape = CircleShape,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    showLabel: Boolean = true,
) {
    PressKey(
        label = key.label,
        enabled = enabled,
        onTap = { remote.press(key) },
        onHoldStart = if (key.holdable) ({ remote.hold(key) }) else null,
        onHoldEnd = if (key.holdable) ({ remote.release(key) }) else null,
        modifier = modifier,
        size = size,
        shape = shape,
        container = container,
        contentColor = contentColor,
        showLabel = showLabel,
    ) { tint ->
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.46f))
    }
}
