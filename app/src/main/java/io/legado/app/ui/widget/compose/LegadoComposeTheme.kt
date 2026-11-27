package io.legado.app.ui.widget.compose

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import io.legado.app.lib.theme.rememberThemeUiPalette
import io.legado.app.lib.theme.titleTypeface
import io.legado.app.lib.theme.uiTypeface

/**
 * 统一的 Compose 主题字体下发。
 *
 * 项目早先只在 AppDialogFrame 等个别作用域里 `LocalTextStyle.copy(fontFamily=...)`，导致大量
 * Compose Text 回退到系统字体、标题字体几乎不生效。用本包裹在 ComposeView 的 setContent 根或
 * 各共享脚手架（AppManagementScaffold / ComposePreferenceScreen / AppDialogFrame）外层，即可让
 * 其内所有未显式指定 fontFamily 的文本都继承主题「界面字体」，标题样式继承「标题字体」。
 */
@Composable
fun LegadoComposeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val themeSignature = rememberThemeUiPalette().signature
    val bodyFamily = remember(context, themeSignature) { FontFamily(context.uiTypeface()) }
    val titleFamily = remember(context, themeSignature) { FontFamily(context.titleTypeface()) }
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography.withUiFontFamilies(bodyFamily, titleFamily)
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = bodyFamily)
        ) {
            // 同时下发 COUI 主题，使子树内的 COUI 组件跟随 legado 主题色与日夜模式。
            LegadoCouiTheme {
                content()
            }
        }
    }
}

/** 把界面字体设为正文/标签默认，标题字体设为 display/headline/titleLarge。 */
private fun Typography.withUiFontFamilies(body: FontFamily, title: FontFamily): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = title),
    displayMedium = displayMedium.copy(fontFamily = title),
    displaySmall = displaySmall.copy(fontFamily = title),
    headlineLarge = headlineLarge.copy(fontFamily = title),
    headlineMedium = headlineMedium.copy(fontFamily = title),
    headlineSmall = headlineSmall.copy(fontFamily = title),
    titleLarge = titleLarge.copy(fontFamily = title),
    titleMedium = titleMedium.copy(fontFamily = body),
    titleSmall = titleSmall.copy(fontFamily = body),
    bodyLarge = bodyLarge.copy(fontFamily = body),
    bodyMedium = bodyMedium.copy(fontFamily = body),
    bodySmall = bodySmall.copy(fontFamily = body),
    labelLarge = labelLarge.copy(fontFamily = body),
    labelMedium = labelMedium.copy(fontFamily = body),
    labelSmall = labelSmall.copy(fontFamily = body)
)
