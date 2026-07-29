package com.novamind.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownTypography

/**
 * Novie 的 Markdown 字号体系。
 *
 * 渲染库默认把 H1 映射到 Material 3 displayLarge（57sp），不适合移动端笔记正文；
 * 这里保留 H1-H6 的语义层级，但将视觉尺寸限制在 16-28sp。
 */
@Composable
fun novieMarkdownTypography(): MarkdownTypography {
    val type = MaterialTheme.typography
    val body = type.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp)
    return markdownTypography(
        h1 = type.headlineMedium.copy(
            fontSize = 28.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        h2 = type.headlineSmall.copy(
            fontSize = 24.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        h3 = type.titleLarge.copy(
            fontSize = 20.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Medium,
        ),
        h4 = type.titleMedium.copy(
            fontSize = 18.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Medium,
        ),
        h5 = body.copy(fontWeight = FontWeight.Medium),
        h6 = body.copy(fontWeight = FontWeight.Medium),
        text = body,
        paragraph = body,
        ordered = body,
        bullet = body,
        list = body,
        link = body.copy(fontWeight = FontWeight.Medium),
        table = body,
    )
}
