package io.legado.app.ui.about

import android.content.Context
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.widget.compose.AppDialogFrame
import io.legado.app.ui.widget.compose.LegadoComposeTheme
import io.legado.app.ui.widget.compose.LegadoMiuixCard
import io.legado.app.ui.widget.compose.LegadoMiuixPalette
import io.legado.app.ui.widget.compose.LegadoMiuixSwitch
import io.legado.app.ui.widget.compose.rememberAppDialogStyle
import io.legado.app.ui.widget.compose.toMiuixPalette
import io.legado.app.lib.theme.titleTypeface
import io.legado.app.lib.theme.uiTypeface
import io.legado.app.utils.applyModernWindowStyle
import io.legado.app.utils.dpToPx
import io.legado.app.utils.setLayout
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.min

object ReadRecordComponentConfigDialog {

    fun show(
        context: Context,
        initialItems: List<ReadRecordComponentItem>,
        onSaved: (List<ReadRecordComponentItem>) -> Unit
    ) {
        lateinit var dialog: AlertDialog
        val screenHeightPx = context.resources.displayMetrics.heightPixels
        // 列表高度按屏幕可用空间收敛。原先固定 420dp/48% 会让「标题 + 说明 + 列表
        // + 底部操作区」超过窗口的 68% 固定高度，底部按钮连同文字被一起裁掉，
        // 只剩一块红色底色。列表本身可滚动，少显示一两项不影响功能。
        val fixedListHeight = min(
            360.dpToPx(),
            (screenHeightPx * 0.40f).toInt()
        ).coerceAtLeast(220.dpToPx())
        val composeView = ComposeView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                (context.resources.displayMetrics.widthPixels * 0.9f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                LegadoComposeTheme {
                    ReadRecordComponentConfigContent(
                        initialItems = initialItems,
                        listHeightDp = fixedListHeight / context.resources.displayMetrics.density,
                        onCancel = { dialog.dismiss() },
                        onSave = { items ->
                            val normalized = items.map { it.copy() }.toMutableList()
                            if (normalized.none { it.enabled }) {
                                normalized.firstOrNull()?.enabled = true
                            }
                            // Applied as it is edited, so the dialog stays open.
                            onSaved(normalized)
                        }
                    )
                }
            }
        }
        dialog = AlertDialog.Builder(context)
            .setView(composeView)
            .create()
        dialog.setOnShowListener {
            // 高度改为自适应内容（保留浮动窗口的宽度上限），避免固定比例窗口把
            // 底部操作区裁掉。setLayout(Int, Int) 会走 resolveFloatingDialogWidth。
            dialog.setLayout(
                (context.resources.displayMetrics.widthPixels * 0.9f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.applyModernWindowStyle()
        // AppDialogFrame 自带圆角面板背景，清掉 AlertDialog 自身窗口背景，避免双层背景。
        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        )
        dialog.show()
    }
}

@Composable
private fun ReadRecordComponentConfigContent(
    initialItems: List<ReadRecordComponentItem>,
    listHeightDp: Float,
    onCancel: () -> Unit,
    onSave: (List<ReadRecordComponentItem>) -> Unit
) {
    val items = remember(initialItems) {
        mutableStateListOf<ReadRecordComponentItem>().apply {
            addAll(initialItems.map { it.copy() })
        }
    }
    val dialogStyle = rememberAppDialogStyle()
    val palette = dialogStyle.toMiuixPalette()
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        items.add(to.index, items.removeAt(from.index))
    }
    // Every change takes effect immediately: the list behind this dialog is the thing being
    // edited, so waiting for a confirm button only makes the edit look lost.
    fun commit() = onSave(items.map { it.copy() })
    AppDialogFrame(
        title = stringResource(R.string.read_record_customize_components),
        message = stringResource(R.string.read_record_components_hint),
        scrollContent = false,
        content = {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = listHeightDp.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(
                    items = items,
                    key = { item -> item.type.name }
                ) { item ->
                    ReorderableItem(reorderState, key = item.type.name) {
                        ReadRecordComponentConfigRow(
                            item = item,
                            // Resolve the position at click time. Rows are keyed, so a
                            // composable outlives reordering and an index captured when it
                            // was composed would go on addressing the row's old slot.
                            onToggle = { checked ->
                                val index = items.indexOfFirst { it.type == item.type }
                                if (index >= 0) {
                                    items[index] = item.copy(enabled = checked)
                                    commit()
                                }
                            },
                            dragHandle = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_drag_handle),
                                    contentDescription = stringResource(R.string.sort),
                                    tint = palette.secondaryText,
                                    modifier = Modifier
                                        .padding(end = 10.dp)
                                        .draggableHandle(onDragStopped = { commit() })
                                )
                            }
                        )
                    }
                }
            }
        },
        actions = {
            // 自绘关闭按钮：显式组合 Text 并保证最小尺寸与点击语义，避免第三方
            // MiuixButton 在此弹窗中丢失 content（只剩背景、无文字、不可点击）。
            DialogCloseButton(
                text = stringResource(R.string.close),
                palette = palette,
                onClick = onCancel
            )
        }
    )
}

@Composable
private fun DialogCloseButton(
    text: String,
    palette: LegadoMiuixPalette,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.defaultMinSize(minWidth = 76.dp, minHeight = 40.dp),
        shape = RoundedCornerShape(palette.actionRadius ?: 16.dp),
        color = palette.accent,
        contentColor = palette.onAccent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                color = palette.onAccent,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ReadRecordComponentConfigRow(
    item: ReadRecordComponentItem,
    onToggle: (Boolean) -> Unit,
    dragHandle: @Composable () -> Unit
) {
    val context = LocalContext.current
    val style = rememberAppDialogStyle()
    val palette = style.toMiuixPalette()
    val titleFont = FontFamily(context.titleTypeface())
    val bodyFont = FontFamily(context.uiTypeface())
    LegadoMiuixCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!item.enabled) },
        color = palette.surfaceVariant,
        contentColor = palette.primaryText,
        cornerRadius = style.panelRadius,
        insidePadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            dragHandle()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(item.type.titleRes),
                    color = palette.primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = titleFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(item.type.hintRes),
                    color = palette.secondaryText,
                    fontSize = 12.sp,
                    fontFamily = bodyFont,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            LegadoMiuixSwitch(
                checked = item.enabled,
                palette = palette,
                onCheckedChange = onToggle
            )
        }
    }
}
