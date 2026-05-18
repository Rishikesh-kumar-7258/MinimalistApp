package com.minimalist.launcher.feature.scratchpad

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp

@Composable
fun ScratchPadScreen(
    content: String,
    onContentChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler { onClose() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text     = "←",
                style    = MaterialTheme.typography.bodyLarge,
                color    = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .clickable { onClose() }
                    .padding(end = 16.dp, top = 4.dp, bottom = 4.dp),
            )
            Text(
                text  = "scratch pad",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.height(32.dp))
        BasicTextField(
            value         = content,
            onValueChange = onContentChange,
            modifier      = Modifier
                .fillMaxWidth()
                .weight(1f),
            textStyle     = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onBackground,
            ),
            cursorBrush   = SolidColor(MaterialTheme.colorScheme.onBackground),
            decorationBox = { inner ->
                if (content.isEmpty()) {
                    Text(
                        text  = "start typing…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                    )
                }
                inner()
            },
        )
        Spacer(Modifier.height(32.dp))
    }
}
