package io.legado.app.ui.widget.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.itos.coui.theme.CouiColorScheme
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.LocalCouiDarkTheme
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.lib.theme.rememberThemeUiPalette
import io.legado.app.lib.theme.secondaryTextColor
import io.legado.app.utils.ColorUtils

/**
 * legado -> COUI-Kit 主题桥接。
 *
 * COUI 组件默认读取 `CouiTheme` 的 [CouiColorScheme] 与 [LocalCouiDarkTheme]；
 * legado 本身有一套可自定义/可导入的主题系统（ThemeStore + AppConfig）。
 * 本文件把 legado 当前生效的主题色实时映射为 COUI 色板，从而让 COUI 组件
 * 跟随用户在 legado 里选择的主题（含日夜切换），而不是写死的 ColorOS 默认色。
 *
 * 使用方式：在各 ComposeView 的 setContent 根、或共享脚手架外层包一层
 * [LegadoCouiTheme]，再正常调用 COUI 组件即可。
 */
@Composable
fun rememberLegadoCouiColorScheme(): CouiColorScheme {
    val context = LocalContext.current
    val dark = AppConfig.isNightTheme
    // 主题签名作为重组 key：用户切换/导入主题包时重新取色，保证 COUI 控件即时跟随。
    val themeSignature = rememberThemeUiPalette().signature

    return remember(context, dark, themeSignature) {
        val accentInt = context.accentColor
        val accent = Color(accentInt)
        val onAccent = if (ColorUtils.isColorLight(accentInt)) Color.Black else Color.White
        val background = Color(context.bottomBackground)
        val onSurface = Color(context.primaryTextColor)
        val onSurfaceVariant = Color(context.secondaryTextColor)

        // COUI 的控件强调色（controlAccent）走 legado 的 accent，
        // 保证开关/滑条/复选等实色控件与 App 主题一致。
        CouiColorScheme(
            accent = accent,
            onAccent = onAccent,
            surface = background,
            container = background,
            onSurface = onSurface,
            onSurfaceVariant = onSurfaceVariant,
            outlineVariant = onSurfaceVariant.copy(alpha = 0.3f),
            background = background,
            card = background,
            menuSurface = background,
            checkboxAccent = accent,
            menuCheckboxAccent = accent,
        )
    }
}

/**
 * 为子树建立 COUI 主题作用域，并同步 legado 的日夜模式。
 *
 * @param dark legado 当前是否夜间主题；缺省时读取 [AppConfig.isNightTheme]。
 */
@Composable
fun LegadoCouiTheme(
    dark: Boolean = AppConfig.isNightTheme,
    content: @Composable () -> Unit,
) {
    val colorScheme = rememberLegadoCouiColorScheme()
    CouiTheme(darkTheme = dark, colorScheme = colorScheme) {
        // legado 有独立的日夜开关，必须向 COUI 提供当前模式
        CompositionLocalProvider(LocalCouiDarkTheme provides dark) {
            content()
        }
    }
}
