package com.prismml.grove.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GradientScreen(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050815)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF091022),
                            Color(0xFF111D3E),
                            Color(0xFF1B1548),
                            Color(0xFF090C18),
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF6D6AFD).copy(alpha = 0.38f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(220f, 120f),
                        radius = 650f,
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF2AC5FF).copy(alpha = 0.22f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(920f, 320f),
                        radius = 820f,
                    )
                )
        )
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 18.dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Surface(
        modifier = modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), shape),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
        shape = shape,
        content = {
            Box(modifier = Modifier.padding(contentPadding)) {
                content()
            }
        },
    )
}

@Composable
fun AccentPill(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        modifier = modifier,
        color = tint.copy(alpha = 0.16f),
        contentColor = tint,
        shape = RoundedCornerShape(999.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}
