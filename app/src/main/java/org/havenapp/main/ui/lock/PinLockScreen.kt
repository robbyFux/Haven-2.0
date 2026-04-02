package org.havenapp.main.ui.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.havenapp.main.R

/**
 * Full-screen PIN entry gate shown when [AppLockState.locked] is true and PIN is enabled.
 *
 * Accepts 4–6 digit PIN. Uses [BackHandler] to block back-press escape.
 * Calls [onVerify] to check correctness and [onUnlocked] on success.
 *
 * @param onVerify Returns true if the entered PIN is correct.
 * @param onUnlocked Called after successful verification; caller should call AppLockState.unlock().
 */
@Composable
fun PinLockScreen(
    onVerify: (pin: String) -> Boolean,
    onUnlocked: () -> Unit,
) {
    // Prevent back press from bypassing the lock screen
    BackHandler(enabled = true) { /* do nothing */ }

    var enteredPin by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = stringResource(R.string.app_name),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.pin_enter_prompt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))

            // Dot indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val maxDots = maxOf(enteredPin.length, 4)
                repeat(maxDots) { index ->
                    val filled = index < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(
                                color = if (filled) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape,
                            )
                            .border(
                                width = 1.dp,
                                color = if (filled) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline,
                                shape = CircleShape,
                            ),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Error text
            if (showError) {
                Text(
                    text = stringResource(R.string.pin_wrong),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            } else {
                // Reserve space so layout doesn't jump
                Spacer(Modifier.height(20.dp))
            }

            Spacer(Modifier.height(24.dp))

            // Numeric keypad: rows 1-9, then [blank] 0 [confirm]
            val digits = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
            )
            digits.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(bottom = 16.dp),
                ) {
                    row.forEach { digit ->
                        PinKeyButton(
                            onClick = {
                                if (enteredPin.length < 6) {
                                    enteredPin += digit
                                    showError = false
                                }
                            },
                        ) {
                            Text(
                                text = digit,
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        }
                    }
                }
            }
            // Bottom row: backspace, 0, confirm
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Backspace
                PinKeyButton(
                    onClick = {
                        if (enteredPin.isNotEmpty()) {
                            enteredPin = enteredPin.dropLast(1)
                            showError = false
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = stringResource(R.string.pin_backspace),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                // 0
                PinKeyButton(
                    onClick = {
                        if (enteredPin.length < 6) {
                            enteredPin += "0"
                            showError = false
                        }
                    },
                ) {
                    Text(
                        text = "0",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                // Confirm
                PinKeyButton(
                    onClick = {
                        if (enteredPin.length >= 4) {
                            if (onVerify(enteredPin)) {
                                onUnlocked()
                            } else {
                                showError = true
                                enteredPin = ""
                            }
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = stringResource(R.string.pin_confirm),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun PinKeyButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
    ) {
        content()
    }
}
