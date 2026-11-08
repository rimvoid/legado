package io.legado.app.ui.book.read

import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.ui.widget.compose.releaseComposeImage
import android.graphics.PorterDuff
import io.legado.app.R
import io.legado.app.data.entities.ReadMenuCustomButton
import io.legado.app.help.book.library.LibraryCloudState
import io.legado.app.ui.book.read.ReadMenuButtonConfig
import io.legado.app.ui.widget.ModernActionPopup
import io.legado.app.ui.widget.compose.AppDialogStyle
import io.legado.app.ui.widget.compose.AppThemedStepperSlider
import io.legado.app.ui.widget.compose.toMiuixPalette
import io.legado.app.utils.dpToPx

private const val MENU_BUTTONS_PER_PAGE = 4
private val READ_MENU_BUTTON_MIN_HEIGHT = 52.dp
private val READ_MENU_TITLE_ICON_SIZE = 22.dp
private val READ_MENU_TITLE_TOUCH_SIZE = 44.dp
private val READ_MENU_TITLE_ICON_SPACING = 2.dp

data class ReadMenuTitleBarState(
    val bookName: String?,
    val isLocalBook: Boolean,
    val isEpub: Boolean,
    val supportsReplaceRules: Boolean
)

data class ReadMenuTitleBarActions(
    val onBackClick: () -> Unit,
    val onBookClick: () -> Unit,
    val onChangeSourceClick: () -> Unit,
    val onChangeSourceLongClick: () -> Unit,
    val onRefreshClick: () -> Unit,
    val onRefreshLongClick: () -> Unit,
    val onCacheClick: () -> Unit,
    val onAddBookmarkClick: () -> Unit,
    val onEditContentClick: () -> Unit,
    val onPageAnimClick: () -> Unit,
    val onMenuEditClick: () -> Unit,
    val onGetProgressClick: () -> Unit,
    val onCoverProgressClick: () -> Unit,
    val onReverseContentClick: () -> Unit,
    val onSimulatedReadingClick: () -> Unit,
    val onChangeReplaceRuleClick: () -> Unit,
    val onSameTitleRemovedClick: () -> Unit,
    val onReSegmentClick: () -> Unit,
    val onImageStyleClick: () -> Unit,
    val onUpdateTocClick: () -> Unit,
    val onParagraphRuleClick: () -> Unit,
    val onHighlightRuleClick: () -> Unit,
    val onEffectiveReplacesClick: () -> Unit,
    val onLogClick: () -> Unit,
    val onHelpClick: () -> Unit
)

data class ReadMenuActionBarState(
    val chapterName: String?,
    val isLocalBook: Boolean,
    val sourceName: String?,
    val showCustomButton: Boolean,
    val showCloudIcon: Boolean,
    val cloudState: LibraryCloudState,
    val hasLogin: Boolean,
    val hasVipChapter: Boolean
)

data class ReadMenuActionBarActions(
    val onChapterClick: () -> Unit,
    val onChapterLongClick: () -> Unit,
    val onLoginClick: () -> Unit,
    val onPayClick: () -> Unit,
    val onEditSourceClick: () -> Unit,
    val onDisableSourceClick: () -> Unit,
    val onCustomButtonClick: () -> Unit,
    val onCustomButtonLongClick: () -> Unit,
    val onCloudClick: () -> Unit,
    val onCloudLongClick: () -> Unit
)

@Composable
fun ReadMenuTitleBar(
    state: ReadMenuTitleBarState,
    actions: ReadMenuTitleBarActions,
    style: AppDialogStyle,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var popupHandle by remember { mutableStateOf<ModernActionPopup.Handle?>(null) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = style.surface,
        contentColor = style.primaryText,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = actions.onBookClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 返回按钮
            ReadMenuTitleIconButton(
                painterRes = R.drawable.ic_arrow_back,
                contentDescription = null,
                tint = style.primaryText,
                style = style,
                onClick = actions.onBackClick
            )
            Spacer(modifier = Modifier.width(4.dp))
            // 书名
            Text(
                text = state.bookName ?: "",
                color = style.primaryText,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            // 换源图标
            if (!state.isLocalBook) {
                ReadMenuTitleIconButton(
                    painterRes = R.drawable.ic_exchange,
                    contentDescription = "换源",
                    tint = style.primaryText,
                    style = style,
                    onLongClick = actions.onChangeSourceLongClick,
                    onClick = actions.onChangeSourceClick
                )
                Spacer(modifier = Modifier.width(READ_MENU_TITLE_ICON_SPACING))
            }
            // 刷新图标
            ReadMenuTitleIconButton(
                painterRes = R.drawable.ic_refresh_black_24dp,
                contentDescription = "刷新",
                tint = style.primaryText,
                style = style,
                onLongClick = actions.onRefreshLongClick,
                onClick = actions.onRefreshClick
            )
            Spacer(modifier = Modifier.width(READ_MENU_TITLE_ICON_SPACING))
            // 缓存图标
            if (!state.isLocalBook) {
                ReadMenuTitleIconButton(
                    painterRes = R.drawable.ic_download_line,
                    contentDescription = "缓存",
                    tint = style.primaryText,
                    style = style,
                    onClick = actions.onCacheClick
                )
                Spacer(modifier = Modifier.width(READ_MENU_TITLE_ICON_SPACING))
            }
            // 三点菜单（overflow menu）- 使用 ModernActionPopup
            var anchorBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
            Box(
                modifier = Modifier
                    .size(READ_MENU_TITLE_TOUCH_SIZE)
                    .clip(RoundedCornerShape(style.actionRadius))
                    .onGloballyPositioned { coordinates ->
                        val pos = coordinates.localToWindow(androidx.compose.ui.geometry.Offset.Zero)
                        val size = coordinates.size
                        anchorBounds = androidx.compose.ui.geometry.Rect(
                            pos.x, pos.y,
                            pos.x + size.width, pos.y + size.height
                        )
                    }
                    .clickable {
                        val overflowActions = buildOverflowActions(
                            isLocalBook = state.isLocalBook,
                            isEpub = state.isEpub,
                            supportsReplaceRules = state.supportsReplaceRules,
                            onAddBookmarkClick = actions.onAddBookmarkClick,
                            onEditContentClick = actions.onEditContentClick,
                            onPageAnimClick = actions.onPageAnimClick,
                            onMenuEditClick = actions.onMenuEditClick,
                            onGetProgressClick = actions.onGetProgressClick,
                            onCoverProgressClick = actions.onCoverProgressClick,
                            onReverseContentClick = actions.onReverseContentClick,
                            onSimulatedReadingClick = actions.onSimulatedReadingClick,
                            onChangeReplaceRuleClick = actions.onChangeReplaceRuleClick,
                            onSameTitleRemovedClick = actions.onSameTitleRemovedClick,
                            onReSegmentClick = actions.onReSegmentClick,
                            onImageStyleClick = actions.onImageStyleClick,
                            onUpdateTocClick = actions.onUpdateTocClick,
                            onParagraphRuleClick = actions.onParagraphRuleClick,
                            onHighlightRuleClick = actions.onHighlightRuleClick,
                            onEffectiveReplacesClick = actions.onEffectiveReplacesClick,
                            onLogClick = actions.onLogClick,
                            onHelpClick = actions.onHelpClick
                        )
                        popupHandle = ModernActionPopup.show(
                            context,
                            anchorBounds.left.toInt(),
                            anchorBounds.top.toInt(),
                            anchorBounds.right.toInt(),
                            anchorBounds.bottom.toInt(),
                            overflowActions,
                            popupHandle
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert),
                    contentDescription = "更多选项",
                    tint = style.primaryText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ReadMenuTitleIconButton(
    painterRes: Int,
    contentDescription: String?,
    tint: Color,
    style: AppDialogStyle,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    } else {
        Modifier.clickable(onClick = onClick)
    }

    Box(
        modifier = modifier
            .size(READ_MENU_TITLE_TOUCH_SIZE)
            .clip(RoundedCornerShape(style.actionRadius))
            .then(clickModifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(painterRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(READ_MENU_TITLE_ICON_SIZE)
        )
    }
}

@Composable
fun ReadMenuActionBar(
    state: ReadMenuActionBarState,
    actions: ReadMenuActionBarActions,
    style: AppDialogStyle,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var sourcePopupHandle by remember { mutableStateOf<ModernActionPopup.Handle?>(null) }
    var sourceAnchorBounds by remember { mutableStateOf(Rect.Zero) }
    val bookSourceTitle = stringResource(R.string.book_source)
    val loginTitle = stringResource(R.string.login)
    val chapterPayTitle = stringResource(R.string.chapter_pay)
    val editSourceTitle = stringResource(R.string.edit_source)
    val disableSourceTitle = stringResource(R.string.disable_source)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = style.surface,
        contentColor = style.primaryText,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val sourceMaxWidth = (maxWidth / 3f).coerceIn(72.dp, 180.dp)

            Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 章节信息（只显示章节名，不显示URL）
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = actions.onChapterClick)
            ) {
                state.chapterName?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        color = style.primaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 云端图标（无背景）
            if (state.showCloudIcon) {
                val cloudAlpha = when (state.cloudState) {
                    LibraryCloudState.READY -> 1f
                    LibraryCloudState.ERROR -> 0.9f
                    LibraryCloudState.DISABLED -> 0.35f
                }
                Icon(
                    painter = painterResource(R.drawable.ic_outline_cloud_24),
                    contentDescription = null,
                    tint = style.primaryText.copy(alpha = cloudAlpha),
                    modifier = Modifier
                        .size(22.dp)
                        .combinedClickable(
                            onClick = actions.onCloudClick,
                            onLongClick = actions.onCloudLongClick
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // 自定义按钮（无背景）
            if (state.showCustomButton) {
                Icon(
                    painter = painterResource(R.drawable.ic_custom),
                    contentDescription = null,
                    tint = style.accent,
                    modifier = Modifier
                        .size(22.dp)
                        .combinedClickable(
                            onClick = actions.onCustomButtonClick,
                            onLongClick = actions.onCustomButtonLongClick
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // 书源操作（带弹出菜单）
            if (!state.isLocalBook) {
                Box(
                    modifier = Modifier
                        .widthIn(max = sourceMaxWidth)
                        .height(34.dp)
                        .clip(RoundedCornerShape(style.actionRadius))
                        .background(style.fieldSurface)
                        .onGloballyPositioned { coordinates ->
                            val pos = coordinates.localToWindow(androidx.compose.ui.geometry.Offset.Zero)
                            val size = coordinates.size
                            sourceAnchorBounds = Rect(
                                pos.x,
                                pos.y,
                                pos.x + size.width,
                                pos.y + size.height
                            )
                        }
                        .clickable {
                            val menuActions = buildList {
                                if (state.hasLogin) {
                                    add(ModernActionPopup.Action(title = loginTitle, invoke = actions.onLoginClick))
                                }
                                if (state.hasVipChapter) {
                                    add(ModernActionPopup.Action(title = chapterPayTitle, invoke = actions.onPayClick))
                                }
                                add(ModernActionPopup.Action(title = editSourceTitle, invoke = actions.onEditSourceClick))
                                add(ModernActionPopup.Action(title = disableSourceTitle, invoke = actions.onDisableSourceClick))
                            }
                            sourcePopupHandle = ModernActionPopup.show(
                                context,
                                sourceAnchorBounds.left.toInt(),
                                sourceAnchorBounds.top.toInt(),
                                sourceAnchorBounds.right.toInt(),
                                sourceAnchorBounds.bottom.toInt(),
                                menuActions,
                                sourcePopupHandle
                            )
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.sourceName ?: bookSourceTitle,
                        color = style.primaryText,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        }
    }
}

@Composable
fun ReadMenuSeekBarRow(
    seekProgress: Int,
    seekMax: Int,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    style: AppDialogStyle,
    onPrevClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeekStart: () -> Unit,
    onSeekStop: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 上一章图标
        Icon(
            painter = painterResource(R.drawable.ic_skip_previous),
            contentDescription = "上一章",
            tint = if (canGoPrev) style.primaryText else style.secondaryText.copy(alpha = 0.5f),
            modifier = Modifier
                .size(26.dp)
                .clickable(
                    interactionSource = null,
                    indication = null,
                    enabled = canGoPrev,
                    onClick = onPrevClick
                )
        )

        Spacer(modifier = Modifier.width(10.dp))

        // 进度滑块
        var localProgress by remember { mutableIntStateOf(seekProgress) }
        LaunchedEffect(seekProgress) { localProgress = seekProgress }
        AppThemedStepperSlider(
            value = localProgress,
            range = 0..seekMax.coerceAtLeast(1),
            onValueChange = { localProgress = it },
            onValueChangeFinished = { onSeekStop(localProgress) },
            palette = style.toMiuixPalette(),
            modifier = Modifier.weight(1f),
            trackHeight = 28.dp,
            thumbSize = 22.dp,
            endpointWidth = 24.dp
        )

        Spacer(modifier = Modifier.width(10.dp))

        // 下一章图标
        Icon(
            painter = painterResource(R.drawable.ic_skip_next),
            contentDescription = "下一章",
            tint = if (canGoNext) style.primaryText else style.secondaryText.copy(alpha = 0.5f),
            modifier = Modifier
                .size(26.dp)
                .clickable(
                    interactionSource = null,
                    indication = null,
                    enabled = canGoNext,
                    onClick = onNextClick
                )
        )
    }
}

@Composable
fun ReadMenuBrightnessRow(
    brightness: Int,
    isAuto: Boolean,
    showBrightnessView: Boolean,
    style: AppDialogStyle,
    onAutoClick: () -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onBrightnessStop: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!showBrightnessView) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 自动按钮（左侧图标）
        Icon(
            painter = painterResource(R.drawable.ic_brightness_auto),
            contentDescription = "自动亮度",
            tint = if (isAuto) style.accent else style.secondaryText,
            modifier = Modifier
                .size(26.dp)
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = onAutoClick
                )
        )

        Spacer(modifier = Modifier.width(10.dp))

        // 亮度滑块
        var localBrightness by remember { mutableIntStateOf(brightness) }
        LaunchedEffect(brightness) { localBrightness = brightness }
        AppThemedStepperSlider(
            value = localBrightness,
            range = 0..255,
            onValueChange = {
                localBrightness = it
                onBrightnessChange(it)
            },
            onValueChangeFinished = { onBrightnessStop(localBrightness) },
            palette = style.toMiuixPalette(),
            enabled = !isAuto,
            modifier = Modifier.weight(1f),
            trackHeight = 28.dp,
            thumbSize = 22.dp,
            endpointWidth = 24.dp
        )

        // 右侧占位（和进度条的下一章图标等宽）
        Spacer(modifier = Modifier.width(36.dp))
    }
}

@Composable
fun ReadMenuButtonGrid(
    firstRow: List<ReadMenuButtonConfig.ButtonRef>,
    secondRow: List<ReadMenuButtonConfig.ButtonRef>,
    customButtonMetadata: Map<Long, ReadMenuCustomButton>,
    autoPageActive: Boolean,
    isNightTheme: Boolean,
    style: AppDialogStyle,
    onClick: (ReadMenuButtonConfig.ButtonRef) -> Unit,
    onLongClick: (ReadMenuButtonConfig.ButtonRef) -> Boolean,
    modifier: Modifier = Modifier
) {
    val hasFirstRow = firstRow.isNotEmpty()
    val hasSecondRow = secondRow.isNotEmpty()
    if (!hasFirstRow && !hasSecondRow) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (hasFirstRow) {
            ReadMenuButtonRow(
                buttons = firstRow,
                customButtonMetadata = customButtonMetadata,
                autoPageActive = autoPageActive,
                isNightTheme = isNightTheme,
                style = style,
                onClick = onClick,
                onLongClick = onLongClick
            )
        }
        if (hasSecondRow) {
            ReadMenuButtonRow(
                buttons = secondRow,
                customButtonMetadata = customButtonMetadata,
                autoPageActive = autoPageActive,
                isNightTheme = isNightTheme,
                style = style,
                onClick = onClick,
                onLongClick = onLongClick
            )
        }
    }
}

@Composable
private fun ReadMenuButtonRow(
    buttons: List<ReadMenuButtonConfig.ButtonRef>,
    customButtonMetadata: Map<Long, ReadMenuCustomButton>,
    autoPageActive: Boolean,
    isNightTheme: Boolean,
    style: AppDialogStyle,
    onClick: (ReadMenuButtonConfig.ButtonRef) -> Unit,
    onLongClick: (ReadMenuButtonConfig.ButtonRef) -> Boolean,
    modifier: Modifier = Modifier
) {
    if (buttons.size <= MENU_BUTTONS_PER_PAGE) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            buttons.forEach { ref ->
                ReadMenuButton(
                    ref = ref,
                    customButtonMetadata = customButtonMetadata,
                    autoPageActive = autoPageActive,
                    isNightTheme = isNightTheme,
                    style = style,
                    onClick = { onClick(ref) },
                    onLongClick = { onLongClick(ref) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    } else {
        val pages = buttons.chunked(MENU_BUTTONS_PER_PAGE)
        val pagerState = rememberPagerState(pageCount = { pages.size })
        HorizontalPager(
            state = pagerState,
            modifier = modifier.fillMaxWidth()
        ) { page ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                pages[page].forEach { ref ->
                    ReadMenuButton(
                        ref = ref,
                        customButtonMetadata = customButtonMetadata,
                        autoPageActive = autoPageActive,
                        isNightTheme = isNightTheme,
                        style = style,
                        onClick = { onClick(ref) },
                        onLongClick = { onLongClick(ref) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(MENU_BUTTONS_PER_PAGE - pages[page].size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ReadMenuButton(
    ref: ReadMenuButtonConfig.ButtonRef,
    customButtonMetadata: Map<Long, ReadMenuCustomButton>,
    autoPageActive: Boolean,
    isNightTheme: Boolean,
    style: AppDialogStyle,
    onClick: () -> Unit,
    onLongClick: () -> Boolean,
    modifier: Modifier = Modifier
) {
    val title = readMenuButtonTitle(ref, customButtonMetadata)
    val iconRes = readMenuButtonIconRes(ref, autoPageActive, isNightTheme)
    val contentColor = style.primaryText
    val customIconPath = if (ref.type == ReadMenuButtonConfig.TYPE_CUSTOM) {
        ref.id.toLongOrNull()?.let { customButtonMetadata[it]?.iconPath }
    } else null

    Column(
        modifier = modifier
            .heightIn(min = READ_MENU_BUTTON_MIN_HEIGHT)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onLongClick() }
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AndroidView(
            factory = { context ->
                AppCompatImageView(context).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(22.dpToPx(), 22.dpToPx())
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setImageDrawable(
                        ReadMenuButtonIconHelper.drawable(context, ref, iconRes, customIconPath)
                    )
                    setColorFilter(contentColor.toArgb(), PorterDuff.Mode.SRC_IN)
                }
            },
            update = { imageView ->
                imageView.setImageDrawable(
                    ReadMenuButtonIconHelper.drawable(imageView.context, ref, iconRes, customIconPath)
                )
                imageView.setColorFilter(contentColor.toArgb(), PorterDuff.Mode.SRC_IN)
            },
            onRelease = { it.releaseComposeImage() },
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = title,
            color = contentColor,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
        )
    }
}

@Composable
fun ReadMenuDivider(
    style: AppDialogStyle,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(style.stroke.copy(alpha = 0.3f))
    )
}

private fun readMenuButtonTitle(
    ref: ReadMenuButtonConfig.ButtonRef,
    customButtonMetadata: Map<Long, ReadMenuCustomButton>
): String {
    ref.titleOverride.trim().takeIf { it.isNotBlank() }?.let { return it }
    return when (ref.type) {
        ReadMenuButtonConfig.TYPE_CUSTOM -> {
            ref.id.toLongOrNull()
                ?.let { customButtonMetadata[it]?.displayName() }
                ?: ref.id
        }
        else -> when (ref.id) {
            ReadMenuButtonConfig.Builtin.SEARCH -> "搜索"
            ReadMenuButtonConfig.Builtin.AUTO_PAGE -> "自动"
            ReadMenuButtonConfig.Builtin.REPLACE_RULE -> "替换"
            ReadMenuButtonConfig.Builtin.NIGHT_THEME -> "夜间"
            ReadMenuButtonConfig.Builtin.CATALOG -> "目录"
            ReadMenuButtonConfig.Builtin.READ_ALOUD -> "朗读"
            ReadMenuButtonConfig.Builtin.READ_STYLE -> "界面"
            ReadMenuButtonConfig.Builtin.SETTING -> "设置"
            ReadMenuButtonConfig.Builtin.READ_ASSISTANT -> "助手"
            ReadMenuButtonConfig.Builtin.AI_SUMMARY -> "AI"
            ReadMenuButtonConfig.Builtin.PARAGRAPH_RULES -> "段落"
            ReadMenuButtonConfig.Builtin.BUBBLE -> "气泡"
            ReadMenuButtonConfig.Builtin.CHARACTERS -> "角色"
            else -> ref.id
        }
    }
}

private fun readMenuButtonIconRes(
    ref: ReadMenuButtonConfig.ButtonRef,
    autoPageActive: Boolean,
    isNightTheme: Boolean
): Int {
    if (ref.type == ReadMenuButtonConfig.TYPE_CUSTOM) return R.drawable.ic_custom
    return when (ref.id) {
        ReadMenuButtonConfig.Builtin.SEARCH -> R.drawable.ic_search
        ReadMenuButtonConfig.Builtin.AUTO_PAGE -> {
            if (autoPageActive) R.drawable.ic_auto_page_stop else R.drawable.ic_auto_page
        }
        ReadMenuButtonConfig.Builtin.REPLACE_RULE -> R.drawable.ic_find_replace
        ReadMenuButtonConfig.Builtin.NIGHT_THEME -> {
            if (isNightTheme) R.drawable.ic_daytime else R.drawable.ic_brightness
        }
        ReadMenuButtonConfig.Builtin.CATALOG -> R.drawable.ic_toc
        ReadMenuButtonConfig.Builtin.READ_ALOUD -> R.drawable.ic_read_aloud
        ReadMenuButtonConfig.Builtin.READ_STYLE -> R.drawable.ic_interface_setting
        ReadMenuButtonConfig.Builtin.SETTING -> R.drawable.ic_settings
        ReadMenuButtonConfig.Builtin.READ_ASSISTANT -> R.drawable.ic_bottom_ai_assistant
        ReadMenuButtonConfig.Builtin.AI_SUMMARY -> R.drawable.ic_bottom_ai
        ReadMenuButtonConfig.Builtin.PARAGRAPH_RULES -> R.drawable.ic_code
        ReadMenuButtonConfig.Builtin.BUBBLE -> R.drawable.ic_bubble_chart
        ReadMenuButtonConfig.Builtin.CHARACTERS -> R.drawable.ic_bottom_person
        else -> R.drawable.ic_custom
    }
}

private fun buildOverflowActions(
    isLocalBook: Boolean,
    isEpub: Boolean,
    supportsReplaceRules: Boolean,
    onAddBookmarkClick: () -> Unit,
    onEditContentClick: () -> Unit,
    onPageAnimClick: () -> Unit,
    onMenuEditClick: () -> Unit,
    onGetProgressClick: () -> Unit,
    onCoverProgressClick: () -> Unit,
    onReverseContentClick: () -> Unit,
    onSimulatedReadingClick: () -> Unit,
    onChangeReplaceRuleClick: () -> Unit,
    onSameTitleRemovedClick: () -> Unit,
    onReSegmentClick: () -> Unit,
    onImageStyleClick: () -> Unit,
    onUpdateTocClick: () -> Unit,
    onParagraphRuleClick: () -> Unit,
    onHighlightRuleClick: () -> Unit,
    onEffectiveReplacesClick: () -> Unit,
    onLogClick: () -> Unit,
    onHelpClick: () -> Unit
): List<ModernActionPopup.Action> {
    val actions = mutableListOf<ModernActionPopup.Action>()
    actions.add(ModernActionPopup.Action(title = "添加书签", invoke = onAddBookmarkClick))
    if (!isEpub) actions.add(ModernActionPopup.Action(title = "编辑内容", invoke = onEditContentClick))
    actions.add(ModernActionPopup.Action(title = "翻页动画", invoke = onPageAnimClick))
    actions.add(ModernActionPopup.Action(title = "菜单编辑", invoke = onMenuEditClick))
    if (!isLocalBook) {
        actions.add(ModernActionPopup.Action(title = "拉取云端进度", invoke = onGetProgressClick))
        actions.add(ModernActionPopup.Action(title = "覆盖云端进度", invoke = onCoverProgressClick))
    }
    if (!isEpub) actions.add(ModernActionPopup.Action(title = "反转内容", invoke = onReverseContentClick))
    actions.add(ModernActionPopup.Action(title = "模拟追读", invoke = onSimulatedReadingClick))
    if (supportsReplaceRules) {
        val isReplaceEnabled = io.legado.app.model.ReadBook.book?.getUseReplaceRule() == true
        actions.add(ModernActionPopup.Action(
            title = "替换净化",
            checked = isReplaceEnabled,
            persistent = true,
            invoke = onChangeReplaceRuleClick
        ))
    }
    if (!isEpub) {
    val isSameTitleRemoved = io.legado.app.model.ReadBook.curTextChapter?.sameTitleRemoved == true
    actions.add(ModernActionPopup.Action(
        title = "移除重复标题",
        checked = isSameTitleRemoved,
        persistent = true,
        invoke = onSameTitleRemovedClick
    ))
    val isReSegment = io.legado.app.model.ReadBook.book?.getReSegment() == true
    actions.add(ModernActionPopup.Action(
        title = "重新分段",
        checked = isReSegment,
        persistent = true,
        invoke = onReSegmentClick
    ))
    actions.add(ModernActionPopup.Action(title = "图片样式", invoke = onImageStyleClick))
    }
    actions.add(ModernActionPopup.Action(title = "更新目录", invoke = onUpdateTocClick))
    if (!isEpub) {
        actions.add(ModernActionPopup.Action(title = "段落规则", invoke = onParagraphRuleClick))
    }
    actions.add(ModernActionPopup.Action(title = "高亮规则", invoke = onHighlightRuleClick))
    if (!isEpub) actions.add(ModernActionPopup.Action(title = "起效的替换", invoke = onEffectiveReplacesClick))
    actions.add(ModernActionPopup.Action(title = "日志", invoke = onLogClick))
    actions.add(ModernActionPopup.Action(title = "帮助", invoke = onHelpClick))
    return actions
}
