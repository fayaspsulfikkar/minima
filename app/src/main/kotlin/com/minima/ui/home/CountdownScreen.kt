package com.minima.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minima.R
import com.minima.ui.theme.GreyLight
import com.minima.ui.theme.GreyMedium
import com.minima.ui.theme.PureBlack
import com.minima.ui.theme.PureWhite

/**
 * Minimalist countdown screen presented during an intentional app-opening pause.
 *
 * Adheres strictly to MINIMA's monochrome, typography-focused philosophy:
 * - High-contrast black and white.
 * - Bold, prominent countdown number.
 * - Minimal explanatory text (app label and simple cancel action).
 * - No guilt-inducing messages, gamification, or animations.
 */
@Composable
fun CountdownScreen(
    appLabel: String,
    remainingSeconds: Int,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Intercept system back gestures and buttons to cancel the countdown
    BackHandler(onBack = onCancel)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Target application name
            Text(
                text = appLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = GreyLight
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Large, clear countdown typography
            Text(
                text = remainingSeconds.toString(),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 80.sp,
                    lineHeight = 84.sp
                ),
                color = PureWhite
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Simple, low-friction cancellation action
            Text(
                text = stringResource(R.string.cancel_countdown),
                style = MaterialTheme.typography.bodyMedium,
                color = GreyMedium,
                modifier = Modifier
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
    }
}

