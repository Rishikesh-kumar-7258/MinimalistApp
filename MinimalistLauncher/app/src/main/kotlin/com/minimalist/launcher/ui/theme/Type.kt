package com.minimalist.launcher.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.minimalist.launcher.core.model.AppFontFamily
import com.minimalist.launcher.core.model.FontSize

fun buildTypography(size: FontSize, family: AppFontFamily): Typography {
    val ff = when (family) {
        AppFontFamily.MONOSPACE  -> FontFamily.Monospace
        AppFontFamily.SANS_SERIF -> FontFamily.SansSerif
        AppFontFamily.SERIF      -> FontFamily.Serif
    }
    val scale = when (size) {
        FontSize.SMALL  -> 0.875f
        FontSize.MEDIUM -> 1.0f
        FontSize.LARGE  -> 1.125f
    }

    return Typography(
        bodyLarge = TextStyle(
            fontFamily    = ff,
            fontWeight    = FontWeight.Normal,
            fontSize      = (16f * scale).sp,
            lineHeight    = (26f * scale).sp,
            letterSpacing = 1.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily    = ff,
            fontWeight    = FontWeight.Normal,
            fontSize      = (14f * scale).sp,
            lineHeight    = (22f * scale).sp,
            letterSpacing = 0.5.sp,
        ),
        displayMedium = TextStyle(
            fontFamily = ff,
            fontWeight = FontWeight.Light,
            fontSize   = (45f * scale).sp,
        ),
        titleLarge = TextStyle(
            fontFamily    = ff,
            fontWeight    = FontWeight.Light,
            fontSize      = (20f * scale).sp,
            lineHeight    = (28f * scale).sp,
            letterSpacing = 4.sp,
        ),
        labelSmall = TextStyle(
            fontFamily    = ff,
            fontWeight    = FontWeight.Normal,
            fontSize      = (11f * scale).sp,
            lineHeight    = (16f * scale).sp,
            letterSpacing = 2.sp,
        ),
    )
}
