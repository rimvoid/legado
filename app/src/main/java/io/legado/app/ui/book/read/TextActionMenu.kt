package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.text.TextPaint
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.PopupWindow
import androidx.annotation.RequiresApi
import androidx.appcompat.view.SupportMenuInflater
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.uiTypeface
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.widget.compose.LegadoComposeTheme
import io.legado.app.ui.widget.compose.installViewTreeOwnersFrom
import io.legado.app.ui.widget.compose.rememberAppDialogStyle
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getPrefString
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.sendToClip
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi
import kotlin.math.ceil

@SuppressLint("RestrictedApi")
class TextActionMenu(private val context: Context, private val callBack: CallBack) :
    PopupWindow(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) {

    private val composeView = ComposeView(context)
    private val allMenuItems: List<MenuItemImpl>
    private var menuActions by mutableStateOf<List<TextMenuAction>>(emptyList())
    private var popupWidthPx by mutableIntStateOf(1)
    private var popupHeightPx by mutableIntStateOf(1)
    private var columns by mutableIntStateOf(1)
    private var rows by mutableIntStateOf(1)
    private var itemWidthPx by mutableIntStateOf(64.dpToPx())
    private var pageCapacity by mutableIntStateOf(1)
    private var lastMaxMainWidth = 0
    private var overlayParent: ViewGroup? = null
    private val itemTextPaint = TextPaint().apply {
        textSize = 12f * context.resources.displayMetrics.scaledDensity
        typeface = context.uiTypeface()
    }

    private val configuredActionIds: Set<String>
        get() = ContentSelectConfig.selectedActionIds(context)

    private val defaultOpenActionId: String
        get() = context.getPrefString(PreferKey.contentSelectDefaultOpen, "").orEmpty()

    init {
        contentView = composeView
        isTouchable = true
        isOutsideTouchable = false
        isFocusable = false
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            elevation = 0f
        }

        val myMenu = MenuBuilder(context)
        val otherMenu = MenuBuilder(context)
        SupportMenuInflater(context).inflate(R.menu.content_select_action, myMenu)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            onInitializeMenu(otherMenu)
        }
        allMenuItems = myMenu.visibleItems + otherMenu.visibleItems

        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        composeView.setContent {
            LegadoComposeTheme {
                TextActionMenuContent(
                    actions = menuActions,
                    popupWidthPx = popupWidthPx,
                    popupHeightPx = popupHeightPx,
                    columns = columns,
                    rows = rows,
                    pageCapacity = pageCapacity,
                    itemWidthPx = itemWidthPx,
                    onActionClick = { action ->
                        try {
                            if (!callBack.onMenuItemSelected(action.itemId)) {
                                onMenuItemSelected(action)
                            }
                        } catch (error: Exception) {
                            // Multi-page selections can exceed an external app's Binder or
                            // clipboard limit. The reader must remain usable if it refuses.
                            AppLog.putDebug("Reader selection action failed", error)
                            context.toastOnUi("文字操作失败，请缩小选区后重试")
                        } finally {
                            callBack.onMenuActionFinally()
                        }
                    },
                    onActionLongClick = { action ->
                        if (action.itemId == R.id.menu_aloud) toggleSpeakMode()
                    }
                )
            }
        }
        upMenu()
    }

    private fun menuItemToActionId(itemId: Int): String? = when (itemId) {
        R.id.menu_web_search -> ContentSelectConfig.ACTION_WEB_SEARCH
        R.id.menu_replace -> ContentSelectConfig.ACTION_REPLACE
        R.id.menu_copy -> ContentSelectConfig.ACTION_COPY
        R.id.menu_bookmark -> ContentSelectConfig.ACTION_BOOKMARK
        R.id.menu_aloud -> ContentSelectConfig.ACTION_ALOUD
        R.id.menu_dict -> ContentSelectConfig.ACTION_DICT
        R.id.menu_word_lookup -> ContentSelectConfig.ACTION_WORD_LOOKUP
        R.id.menu_ask_ai -> ContentSelectConfig.ACTION_ASK_AI
        R.id.menu_generate_image -> ContentSelectConfig.ACTION_GENERATE_IMAGE
        R.id.menu_share_image -> ContentSelectConfig.ACTION_SHARE_IMAGE
        else -> null
    }

    private fun filteredMenuActions(): List<TextMenuAction> {
        return allMenuItems.mapNotNull { item ->
            val actionId = menuItemToActionId(item.itemId) ?: return@mapNotNull null
            // 取词入口常驻在长按菜单中, 不参与可配置菜单项
            val isWordLookup = actionId == ContentSelectConfig.ACTION_WORD_LOOKUP
            if (!isWordLookup && !configuredActionIds.contains(actionId)) {
                return@mapNotNull null
            }
            if (actionId == ContentSelectConfig.ACTION_REPLACE && !callBack.supportsReplaceRules()) {
                return@mapNotNull null
            }
            TextMenuAction(
                itemId = item.itemId,
                actionId = actionId,
                title = if (item.itemId == R.id.menu_aloud &&
                    (BaseReadAloudService.isRun || AppConfig.contentSelectSpeakMod == 1)
                ) {
                    context.getString(R.string.read_aloud_from_here)
                } else {
                    item.title?.toString().orEmpty()
                },
                intent = item.intent,
                active = isWordLookup && callBack.isWordLookupActive()
            )
        }
    }

    fun upMenu() {
        upMenuForWidth(lastMaxMainWidth.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels)
    }

    private fun upMenuForWidth(maxMainWidth: Int) {
        lastMaxMainWidth = maxMainWidth
        val actions = filteredMenuActions()
        val maxWidth = (maxMainWidth - 16.dpToPx()).coerceAtLeast(140.dpToPx())
        val layout = calculateLayout(actions, maxWidth)
        menuActions = actions
        columns = layout.columns
        rows = layout.rows
        itemWidthPx = layout.itemWidth
        pageCapacity = layout.capacity
        popupWidthPx = layout.width
        popupHeightPx = layout.height
        width = layout.width
        height = layout.height
        composeView.layoutParams = ViewGroup.LayoutParams(layout.width, layout.height)
    }

    private fun calculateLayout(actions: List<TextMenuAction>, maxWidth: Int): MenuLayout {
        val safeCount = actions.size.coerceAtLeast(1)
        val columns = when {
            safeCount <= 2 -> safeCount
            safeCount <= 4 -> safeCount
            else -> 4
        }.coerceAtMost((maxWidth - horizontalPaddingPx()) / minItemWidthPx()).coerceAtLeast(1)
        val needsPages = safeCount > columns * 2
        val rows = if (needsPages) 2 else ceil(safeCount / columns.toFloat()).toInt().coerceIn(1, 2)
        val capacity = (columns * 2).coerceAtLeast(1)
        val itemWidth = actions
            .take(capacity)
            .maxOfOrNull(::estimateItemWidth)
            ?.coerceIn(minItemWidthPx(), maxItemWidthPx())
            ?: minItemWidthPx()
        val menuWidth = (itemWidth * columns + horizontalPaddingPx())
            .coerceAtMost(maxWidth)
            .coerceAtLeast(minItemWidthPx() + horizontalPaddingPx())
        val menuHeight = rows * itemHeightPx() + verticalPaddingPx() +
            if (actions.size > capacity) indicatorHeightPx() else 0
        return MenuLayout(
            columns = columns,
            rows = rows,
            capacity = capacity,
            itemWidth = ((menuWidth - horizontalPaddingPx()) / columns).coerceAtLeast(minItemWidthPx()),
            width = menuWidth,
            height = menuHeight
        )
    }

    private fun estimateItemWidth(action: TextMenuAction): Int {
        val textWidth = ceil(itemTextPaint.measureText(action.title)).toInt()
        return textWidth + 24.dpToPx()
    }

    private fun minItemWidthPx(): Int = 56.dpToPx()

    private fun maxItemWidthPx(): Int = 112.dpToPx()

    private fun itemHeightPx(): Int = 40.dpToPx()

    private fun horizontalPaddingPx(): Int = 12.dpToPx()

    private fun verticalPaddingPx(): Int = 12.dpToPx()

    private fun indicatorHeightPx(): Int = 16.dpToPx()

    fun show(
        view: View,
        windowHeight: Int,
        startX: Int,
        startTopY: Int,
        startBottomY: Int,
        endX: Int,
        endBottomY: Int,
        reservedBottom: Int = 0
    ) {
        val defaultActionId = defaultOpenActionId
        if (defaultActionId.isNotEmpty() && configuredActionIds.contains(defaultActionId)) {
            val defaultItem = filteredMenuActions().firstOrNull { it.actionId == defaultActionId }
            if (defaultItem != null) {
                if (!callBack.onMenuItemSelected(defaultItem.itemId)) {
                    onMenuItemSelected(defaultItem)
                }
                callBack.onMenuActionFinally()
                return
            }
        }
        upMenuForWidth(view.width)
        val popupWidth = popupWidthPx
        val popupHeight = popupHeightPx
        val margin = 8.dpToPx()
        val safeWindowHeight = (windowHeight - reservedBottom).coerceAtLeast(margin * 2)
        val spaceAbove = startTopY
        val spaceBelow = safeWindowHeight - endBottomY
        val showAbove = spaceAbove >= popupHeight + margin || spaceAbove > spaceBelow
        val preferredX = ((startX + endX) / 2f - popupWidth / 2f).toInt()
        val maxX = (view.width - popupWidth - margin).coerceAtLeast(margin)
        val x = preferredX.coerceIn(margin, maxX)
        val y = if (showAbove) {
            (startTopY - popupHeight - margin).coerceAtLeast(margin)
        } else {
            (endBottomY + margin).coerceAtMost((safeWindowHeight - popupHeight - margin).coerceAtLeast(margin))
        }
        showInHostView(view, x, y)
    }

    private fun showInHostView(anchor: View, x: Int, y: Int) {
        dismiss()
        composeView.installViewTreeOwnersFrom(anchor, context)
        val parent = anchor.rootView as? ViewGroup
        if (parent == null) {
            showAtLocation(anchor, Gravity.TOP or Gravity.START, x, y)
            return
        }
        (composeView.parent as? ViewGroup)?.removeView(composeView)
        parent.addView(
            composeView,
            FrameLayout.LayoutParams(popupWidthPx, popupHeightPx).apply {
                leftMargin = x
                topMargin = y
            }
        )
        overlayParent = parent
    }

    override fun dismiss() {
        overlayParent?.let {
            (composeView.parent as? ViewGroup)?.removeView(composeView)
            overlayParent = null
            return
        }
        super.dismiss()
    }

    private fun toggleSpeakMode() {
        if (AppConfig.contentSelectSpeakMod == 0) {
            AppConfig.contentSelectSpeakMod = 1
            context.toastOnUi(R.string.content_select_speak_from_selection)
        } else {
            AppConfig.contentSelectSpeakMod = 0
            context.toastOnUi(R.string.content_select_speak_selected)
        }
    }

    private fun onMenuItemSelected(action: TextMenuAction) {
        when (action.itemId) {
            R.id.menu_copy -> context.sendToClip(callBack.selectedText)
            R.id.menu_share_str -> context.share(callBack.selectedText)
            R.id.menu_browser -> {
                kotlin.runCatching {
                    val intent = if (callBack.selectedText.isAbsUrl()) {
                        Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse(callBack.selectedText)
                        }
                    } else {
                        Intent(Intent.ACTION_WEB_SEARCH).apply {
                            putExtra(SearchManager.QUERY, callBack.selectedText)
                        }
                    }
                    context.startActivity(intent)
                }.onFailure {
                    it.printOnDebug()
                    context.toastOnUi(it.localizedMessage ?: "ERROR")
                }
            }

            else -> action.intent?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    kotlin.runCatching {
                        it.putExtra(Intent.EXTRA_PROCESS_TEXT, callBack.selectedText)
                        context.startActivity(it)
                    }.onFailure { e ->
                        AppLog.put("Text action menu error\n$e", e, true)
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun createProcessTextIntent(): Intent {
        return Intent()
            .setAction(Intent.ACTION_PROCESS_TEXT)
            .setType("text/plain")
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun getSupportedActivities(): List<ResolveInfo> {
        return context.packageManager
            .queryIntentActivities(createProcessTextIntent(), 0)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun createProcessTextIntentForResolveInfo(info: ResolveInfo): Intent {
        return createProcessTextIntent()
            .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
            .setClassName(info.activityInfo.packageName, info.activityInfo.name)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun onInitializeMenu(menu: Menu) {
        kotlin.runCatching {
            var menuItemOrder = 100
            for (resolveInfo in getSupportedActivities()) {
                menu.add(
                    Menu.NONE, Menu.NONE,
                    menuItemOrder++, resolveInfo.loadLabel(context.packageManager)
                ).intent = createProcessTextIntentForResolveInfo(resolveInfo)
            }
        }.onFailure {
            context.toastOnUi("Text action menu init error:${it.localizedMessage}")
        }
    }

    interface CallBack {
        val selectedText: String

        fun supportsReplaceRules(): Boolean = true

        fun isWordLookupActive(): Boolean = false

        fun onMenuItemSelected(itemId: Int): Boolean

        fun onMenuActionFinally()
    }

    private data class MenuLayout(
        val columns: Int,
        val rows: Int,
        val capacity: Int,
        val itemWidth: Int,
        val width: Int,
        val height: Int
    )
}

private data class TextMenuAction(
    val itemId: Int,
    val actionId: String,
    val title: String,
    val intent: Intent?,
    val active: Boolean = false
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TextActionMenuContent(
    actions: List<TextMenuAction>,
    popupWidthPx: Int,
    popupHeightPx: Int,
    columns: Int,
    rows: Int,
    pageCapacity: Int,
    itemWidthPx: Int,
    onActionClick: (TextMenuAction) -> Unit,
    onActionLongClick: (TextMenuAction) -> Unit
) {
    val style = rememberAppDialogStyle()
    val density = LocalDensity.current
    val pages = actions.chunked(pageCapacity.coerceAtLeast(1)).ifEmpty { listOf(emptyList()) }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    LaunchedEffect(pages.size) {
        val target = pagerState.currentPage.coerceAtMost((pages.size - 1).coerceAtLeast(0))
        if (target != pagerState.currentPage) {
            pagerState.scrollToPage(target)
        }
    }
    val widthDp = with(density) { popupWidthPx.toDp() }
    val heightDp = with(density) { popupHeightPx.toDp() }
    val itemWidthDp = with(density) { itemWidthPx.toDp() }
    val itemHeight = 34.dp
    val shape = RoundedCornerShape(style.panelRadius)

    Column(
        modifier = Modifier
            .width(widthDp)
            .height(heightDp)
            .shadow(12.dp, shape, clip = false)
            .clip(shape)
            .background(style.surface)
            .border(1.dp, style.stroke, shape)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .width(widthDp - 12.dp)
                .height((rows * 40).dp)
        ) { page ->
            val pageItems = pages[page]
            Column(
                modifier = Modifier.width(widthDp - 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                repeat(rows) { rowIndex ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(columns) { columnIndex ->
                            val itemIndex = rowIndex * columns + columnIndex
                            val action = pageItems.getOrNull(itemIndex)
                            if (action == null) {
                                Spacer(
                                    modifier = Modifier
                                        .width(itemWidthDp)
                                        .height(itemHeight)
                                )
                            } else {
                                TextActionChip(
                                    action = action,
                                    width = itemWidthDp,
                                    height = itemHeight,
                                    textColor = style.primaryText,
                                    background = style.fieldSurface,
                                    accent = style.accent,
                                    onClick = { onActionClick(action) },
                                    onLongClick = { onActionLongClick(action) }
                                )
                            }
                        }
                    }
                }
            }
        }
        if (pages.size > 1) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pages.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (selected) 6.dp else 4.dp)
                            .clip(CircleShape)
                            .background(if (selected) style.accent else style.secondaryText.copy(alpha = 0.45f))
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TextActionChip(
    action: TextMenuAction,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    textColor: ComposeColor,
    background: ComposeColor,
    accent: ComposeColor,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val chipBackground = if (action.active) accent.copy(alpha = 0.22f) else background
    val chipBorder = if (action.active) accent else accent.copy(alpha = 0.16f)
    val chipTextColor = if (action.active) accent else textColor
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .padding(horizontal = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(chipBackground)
            .border(1.dp, chipBorder, RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = action.title,
            color = chipTextColor,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.widthIn(max = width - 16.dp)
        )
    }
}
