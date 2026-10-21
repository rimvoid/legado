package io.legado.app.ui.book.read

import io.legado.app.help.config.AppConfig

internal object FollowReaderAppearance {
    @Volatile var active = false
    val highlight get() = active && AppConfig.readAloudFollowHighlight
    const val textColor = 0xff9c4300.toInt()
    const val backgroundColor = 0xffffff66.toInt()
}
