package com.minimalist.launcher.feature.friction

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.minimalist.launcher.core.model.FrictionReason
import kotlinx.coroutines.delay

@Composable
fun FrictionScreen(
    appLabel: String,
    reason: FrictionReason,
    message: String,
    onProceed: () -> Unit,
    onGoBack: () -> Unit,
) {
    var countdown by remember { mutableIntStateOf(5) }

    // Count down from 5 to 0, then allow proceeding.
    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1_000)
            countdown--
        }
    }

    val reasonText = when (reason) {
        FrictionReason.DAILY_LIMIT  -> "Daily limit reached"
        FrictionReason.TIME_WINDOW  -> "Outside allowed time window"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text  = appLabel,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text  = reasonText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
        )
        Spacer(Modifier.height(40.dp))
        Text(
            text  = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(56.dp))

        if (countdown > 0) {
            Text(
                text  = "$countdown",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
            )
        } else {
            Text(
                text  = "proceed anyway",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                modifier = Modifier
                    .clickable { onProceed() }
                    .padding(4.dp),
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text  = "go back",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
            modifier = Modifier
                .clickable { onGoBack() }
                .padding(4.dp),
        )
    }
}
