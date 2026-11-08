package io.legado.app.ui.book.read.page

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import com.airbnb.lottie.ImageAssetDelegate
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.airbnb.lottie.LottieImageAsset
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.RenderMode
import com.airbnb.lottie.TextDelegate
import com.airbnb.lottie.model.LottieCompositionCache
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppConst.timeFormat
import io.legado.app.data.entities.Bookmark
import io.legado.app.databinding.ViewBookPageBinding
import io.legado.app.help.book.isEpub
import io.legado.app.help.config.AdvancedTitleConfig
import io.legado.app.help.config.LottieDerivedResourceCache
import io.legado.app.help.config.AdvancedTipConfig
import io.legado.app.help.config.AdvancedTipSlot
import io.legado.app.help.config.AdvancedTitleFontAssetDelegate
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadTipConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.entities.TextPos
import io.legado.app.ui.book.read.page.entities.ReadSelectionPosition
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.widget.BatteryView
import io.legado.app.utils.activity
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.applyStatusBarPadding
import io.legado.app.utils.decodeBase64DataUrlBytes
import io.legado.app.utils.dpToPx
import io.legado.app.utils.gone
import io.legado.app.utils.SvgUtils
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import io.legado.app.utils.setTextIfNotEqual
import splitties.views.backgroundColor
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Date
import kotlinx.coroutines.CoroutineStart
import org.json.JSONObject

/**
 * 页面视图
 */
class PageView(context: Context) : FrameLayout(context) {

    private val binding = ViewBookPageBinding.inflate(LayoutInflater.from(context), this, true)
    private val readBookActivity get() = activity as? ReadBookActivity
    private var battery = 100
    private var tvTitle: BatteryView? = null
    private var tvTime: BatteryView? = null
    private var tvBattery: BatteryView? = null
    private var tvBatteryP: BatteryView? = null
    private var tvPage: BatteryView? = null
    private var tvTotalProgress: BatteryView? = null
    private var tvTotalProgress1: BatteryView? = null
    private var tvPageAndTotal: BatteryView? = null
    private var tvBookName: BatteryView? = null
    private var tvTimeBattery: BatteryView? = null
    private var tvTimeBatteryP: BatteryView? = null
    private var isMainView = false
    /** Bumped when page-turn screenshot pixels may change (content / tip / lottie). */
    var snapRevision: Long = 1L
        private set
    private var overlayBindToken: Long = 0L
    private var overlayBindScheduledToken: Long = 0L
    private val overlayBindRunnable = Runnable {
        if (overlayBindScheduledToken != overlayBindToken) return@Runnable
        bindAdvancedOverlaysIdle()
    }
    private var currentTextPage: TextPage? = null
    private var pairedTextPage: TextPage? = null
    private val advancedTitleRequestGate = AdvancedTitleRequestGate()
    private data class AdvancedTitleRenderConfig(
        val imageAssetResolver: LottieImageAssetResolver,
        val cacheComposition: Boolean,
        val maintainOriginalImageBounds: Boolean = true
    )
    private data class PendingAdvancedTitleComposition(
        val token: AdvancedTitleRequestToken,
        val composition: com.airbnb.lottie.LottieComposition,
        val renderConfig: AdvancedTitleRenderConfig
    )
    private val pendingAdvancedTitleCompositions =
        arrayOfNulls<PendingAdvancedTitleComposition>(AdvancedTitleSlot.entries.size)
    private val advancedTitleImagePrepareJobs =
        arrayOfNulls<Coroutine<Boolean>>(AdvancedTitleSlot.entries.size)
    private val appliedAdvancedTitleKeys = arrayOfNulls<String>(AdvancedTitleSlot.entries.size)
    private val appliedAdvancedTitleCompositions =
        arrayOfNulls<com.airbnb.lottie.LottieComposition>(AdvancedTitleSlot.entries.size)
    private val appliedAdvancedTitleRenderConfigs =
        arrayOfNulls<AdvancedTitleRenderConfig>(AdvancedTitleSlot.entries.size)

    /**
     * The title block each slot currently has composed and on screen.
     *
     * Page turning re-runs [setContent], and tearing the slot down there means a title the
     * reader turns away from and back to has to be composed again, which reads as a flash.
     * [TextChapter] hands back the same [TextPage] instances, so an identity match proves
     * the slot already holds exactly this title and can simply stay up.
     */
    private val boundAdvancedTitleBlocks =
        arrayOfNulls<TextPage.EpubEmbeddedBlock>(AdvancedTitleSlot.entries.size)

    /**
     * Composition key last bound for a page, keyed by that page's position.
     *
     * Two things have to be true before a composition may go back up, and they need
     * separate mechanisms:
     *
     * The *lookup* is keyed by position — chapter index and page number — because that is
     * what stays stable when the reader turns away and back. Title text cannot be used:
     * chapter names repeat ("序", "第一章", "番外"), so two unrelated positions would
     * collide and one chapter could show another's composition.
     *
     * The *decision* stays with the recorded key, which encodes the styled JSON hash and
     * the view size. Changing the font, rotating, or swapping the title package therefore
     * produces a key that no longer resolves, and the page rebuilds instead of reusing a
     * composition built for different conditions — something position alone cannot detect.
     */
    private val advancedTitleKeysByPage = object : LinkedHashMap<String, String>(
        MAX_TITLE_KEYS_BY_PAGE, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > MAX_TITLE_KEYS_BY_PAGE
        }
    }

    /**
     * A composition already committed to a slot, kept per key rather than per slot.
     *
     * The slot itself can only hold one at a time, so recording the current one alone means
     * a page the reader turns away from and back has nothing to come back to: the next page
     * has overwritten the entry. Keeping a few keyed by composition lets a returning page
     * find its own again and go straight back up.
     */
    private data class CommittedAdvancedTitle(
        val composition: com.airbnb.lottie.LottieComposition,
        val renderConfig: AdvancedTitleRenderConfig
    )

    private val committedAdvancedTitles = object : LinkedHashMap<String, CommittedAdvancedTitle>(
        MAX_COMMITTED_TITLES, 0.75f, true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, CommittedAdvancedTitle>?
        ): Boolean {
            return size > MAX_COMMITTED_TITLES
        }
    }

    /**
     * Stable identity for a page's position within a book.
     *
     * Chapter and page numbers repeat across books, so the book has to take part or a stale
     * entry from a previously open book could answer for this one.
     */
    private fun advancedTitlePageKey(textPage: TextPage): String {
        val bookUrl = ReadBook.book?.bookUrl.orEmpty()
        return "$bookUrl#${textPage.chapterIndex}#${textPage.index}"
    }
    private var advancedTitleAnimationsFrozenForPageTurn = false
    private val advancedTitleAnimationsToResume =
        BooleanArray(AdvancedTitleSlot.entries.size)
    private val advancedTipAnimationsToResume =
        BooleanArray(AdvancedTipSlot.entries.size)
    private var scrollTitleLinger: List<ScrollAdvTitle> = emptyList()
    private var lastScrollPageOffsetForTitle: Int = Int.MIN_VALUE
    private var boundScrollTitleIds: Array<String?> = arrayOf(null, null)
    private var advancedHeaderLottieKey: String? = null
    private var advancedFooterLottieKey: String? = null
    private var headerTipTextDelegate: TipFieldTextDelegate? = null
    private var footerTipTextDelegate: TipFieldTextDelegate? = null
    private data class AdvancedTipRequestToken(
        val contentGeneration: Long,
        val requestGeneration: Long,
        val slot: AdvancedTipSlot,
        val compositionKey: String
    )
    private data class PendingAdvancedTipComposition(
        val token: AdvancedTipRequestToken,
        val composition: com.airbnb.lottie.LottieComposition,
        val variables: Map<String, String>,
        val imageAssetResolver: LottieImageAssetResolver
    )
    private data class PreparedAdvancedTipComposition(
        val composition: com.airbnb.lottie.LottieComposition,
        val imageAssetResolver: LottieImageAssetResolver
    )
    private var advancedTipRequestGeneration = 0L
    private val currentAdvancedTipRequests =
        arrayOfNulls<AdvancedTipRequestToken>(AdvancedTipSlot.entries.size)
    private val pendingAdvancedTipCompositions =
        arrayOfNulls<PendingAdvancedTipComposition>(AdvancedTipSlot.entries.size)
    private val advancedTipPrepareJobs =
        arrayOfNulls<Coroutine<PreparedAdvancedTipComposition?>>(AdvancedTipSlot.entries.size)
    private val advancedTipPrewarmKeys = arrayOfNulls<String>(AdvancedTipSlot.entries.size)
    private var lastTipContext: AdvancedTipConfig.TipContext = AdvancedTipConfig.TipContext()
    var isScroll = false

    val headerHeight: Int
        get() {
            val h1 = if (binding.vwStatusBar.isGone) 0 else binding.vwStatusBar.height
            val h2 = if (binding.llHeader.isGone) 0 else binding.llHeader.height
            return h1 + h2 + binding.vwRoot.paddingTop
        }
    val imgBgPaddingStart: Int
        get() {
            return binding.vwRoot.paddingStart
        }

    init {
        if (!isInEditMode) {
            // Hard-clip title to content band so it cannot paint through the (transparent) header.
            binding.advancedTitleOverlay.clipChildren = true
            binding.advancedTitleOverlay.clipToPadding = true
            // Avoid clipToOutline/SOFTWARE Lottie at inflate: every book pays this cost and
            // HarmonyOS mid-range devices can fail to draw the first frame (black screen).
            binding.advancedTitleOverlay.elevation = 0f
            upStyle()
            binding.vwStatusBar.applyStatusBarPadding()
            binding.vwNavigationBar.applyNavigationBarPadding()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        upBg()
    }

    override fun onDetachedFromWindow() {
        disposeAdvancedTitleRequests()
        binding.contentTextView.setScrollFollowBackground(null, 255)
        binding.vwRoot.background = null
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // onDetachedFromWindow releases the potentially large wallpaper drawable. A same-size
        // reattach does not guarantee onSizeChanged, so restore it explicitly here.
        upBg()
        val textPage = currentTextPage ?: return
        showSynchronousAdvancedTitleFallbacks(textPage, pairedTextPage)
        scheduleOverlayBind()
    }

    fun upStyle() = binding.run {
        upTipStyle()
        ReadBookConfig.let {
            val textColor = it.textColor
            val tipColor = with(ReadTipConfig) {
                if (tipColor == 0) textColor else tipColor
            }
            val tipDividerColor = with(ReadTipConfig) {
                when (tipDividerColor) {
                    -1 -> ContextCompat.getColor(context, R.color.divider)
                    0 -> textColor
                    else -> tipDividerColor
                }
            }
            tvHeaderLeft.setColor(tipColor)
            tvHeaderMiddle.setColor(tipColor)
            tvHeaderRight.setColor(tipColor)
            tvFooterLeft.setColor(tipColor)
            tvFooterMiddle.setColor(tipColor)
            tvFooterRight.setColor(tipColor)
            advancedTitleFallback.setTextColor(textColor)
            advancedTitleFallbackPair.setTextColor(textColor)
            advancedTitleFallback.textSize = advancedTitleTextSizeSp()
            advancedTitleFallbackPair.textSize = advancedTitleTextSizeSp()
            val titleTypeface = ChapterProvider.titlePaint.typeface ?: ChapterProvider.typeface
            advancedTitleFallback.typeface = titleTypeface
            advancedTitleFallbackPair.typeface = titleTypeface
            vwTopDivider.backgroundColor = tipDividerColor
            vwBottomDivider.backgroundColor = tipDividerColor
            upStatusBar()
            upNavigationBar()
            upPaddingDisplayCutouts()
            llHeader.setPadding(
                it.headerPaddingLeft.dpToPx(),
                it.headerPaddingTop.dpToPx(),
                it.headerPaddingRight.dpToPx(),
                it.headerPaddingBottom.dpToPx()
            )
            llFooter.setPadding(
                it.footerPaddingLeft.dpToPx(),
                it.footerPaddingTop.dpToPx(),
                it.footerPaddingRight.dpToPx(),
                it.footerPaddingBottom.dpToPx()
            )
            vwTopDivider.gone(llHeader.isGone || !it.showHeaderLine)
            vwBottomDivider.gone(llFooter.isGone || !it.showFooterLine)
        }
        upTime()
        upBattery(battery)
        invalidateTextRenderCache()
    }

    fun invalidateTextRenderCache() {
        currentTextPage?.invalidateAll()
        pairedTextPage?.invalidateAll()
        ViewCompat.postInvalidateOnAnimation(binding.contentTextView)
    }

    /**
     * 显示状态栏时隐藏header
     */
    fun upStatusBar() = with(binding.vwStatusBar) {
//        setPadding(paddingLeft, context.statusBarHeight, paddingRight, paddingBottom)
        isGone = ReadBook.book?.isEpub == true ||
            ReadBookConfig.hideStatusBar ||
            readBookActivity?.isInMultiWindow == true
    }

    fun upNavigationBar() {
        binding.vwNavigationBar.isGone = ReadBook.book?.isEpub == true || ReadBookConfig.hideNavigationBar
    }

    fun upPaddingDisplayCutouts() {
        if (ReadBookConfig.isNineBgImg) {
            ViewCompat.setOnApplyWindowInsetsListener(binding.vwRoot, null)
            return
        }
        if (AppConfig.paddingDisplayCutouts) {
            binding.vwRoot.setOnApplyWindowInsetsListenerCompat { _, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
                binding.vwRoot.setPadding(
                    insets.left,
                    if (binding.vwStatusBar.isGone) insets.top else 0,
                    insets.right,
                    insets.bottom
                )
                windowInsets
            }
        } else {
            ViewCompat.setOnApplyWindowInsetsListener(binding.vwRoot, null)
            binding.vwRoot.setPadding(0, 0, 0, 0)
        }
    }

    /**
     * 更新阅读信息
     */
    private fun upTipStyle(textPage: TextPage? = currentTextPage) = binding.run {
        val isEpub = ReadBook.book?.isEpub == true
        tvHeaderLeft.tag = null
        tvHeaderMiddle.tag = null
        tvHeaderRight.tag = null
        tvFooterLeft.tag = null
        tvFooterMiddle.tag = null
        tvFooterRight.tag = null
        llHeader.isGone = if (isEpub) {
            true
        } else {
            when (ReadTipConfig.headerMode) {
                ReadTipConfig.HEADER_MODE_SHOW,
                ReadTipConfig.HEADER_MODE_ADVANCED -> false
                ReadTipConfig.HEADER_MODE_HIDE -> true
                else -> !ReadBookConfig.hideStatusBar
            }
        }
        llFooter.isGone = if (isEpub) {
            true
        } else {
            when (ReadTipConfig.footerMode) {
                ReadTipConfig.FOOTER_MODE_HIDE -> true
                else -> false
            }
        }
        ReadTipConfig.apply {
            tvHeaderLeft.isGone = tipHeaderLeft == none
            tvHeaderRight.isGone = tipHeaderRight == none
            tvHeaderMiddle.isGone = tipHeaderMiddle == none
            tvFooterLeft.isInvisible = tipFooterLeft == none
            tvFooterRight.isGone = tipFooterRight == none
            tvFooterMiddle.isGone = tipFooterMiddle == none
        }
        tvTitle = getTipView(ReadTipConfig.chapterTitle)?.apply {
            tag = ReadTipConfig.chapterTitle
            isBattery = false
            typeface = ChapterProvider.typeface
            textSize = 12f
        }
        tvTime = getTipView(ReadTipConfig.time)?.apply {
            tag = ReadTipConfig.time
            isBattery = false
            typeface = ChapterProvider.typeface
            textSize = 12f
        }
        tvBattery = getTipView(ReadTipConfig.battery)?.apply {
            tag = ReadTipConfig.battery
            isBattery = true
            textSize = 11f
        }
        tvPage = getTipView(ReadTipConfig.page)?.apply {
            tag = ReadTipConfig.page
            isBattery = false
            typeface = ChapterProvider.typeface
            textSize = 12f
        }
        tvTotalProgress = getTipView(ReadTipConfig.totalProgress)?.apply {
            tag = ReadTipConfig.totalProgress
            isBattery = false
            typeface = ChapterProvider.typeface
            textSize = 12f
        }
        tvTotalProgress1 = getTipView(ReadTipConfig.totalProgress1)?.apply {
            tag = ReadTipConfig.totalProgress1
            isBattery = false
            typeface = ChapterProvider.typeface
            textSize = 12f
        }
        tvPageAndTotal = getTipView(ReadTipConfig.pageAndTotal)?.apply {
            tag = ReadTipConfig.pageAndTotal
            isBattery = false
            typeface = ChapterProvider.typeface
            textSize = 12f
        }
        tvBookName = getTipView(ReadTipConfig.bookName)?.apply {
            tag = ReadTipConfig.bookName
            isBattery = false
            typeface = ChapterProvider.typeface
            textSize = 12f
        }
        tvTimeBattery = getTipView(ReadTipConfig.timeBattery)?.apply {
            tag = ReadTipConfig.timeBattery
            isBattery = true
            typeface = ChapterProvider.typeface
            textSize = 11f
        }
        tvBatteryP = getTipView(ReadTipConfig.batteryPercentage)?.apply {
            tag = ReadTipConfig.batteryPercentage
            isBattery = false
            typeface = ChapterProvider.typeface
            textSize = 12f
        }
        tvTimeBatteryP = getTipView(ReadTipConfig.timeBatteryPercentage)?.apply {
            tag = ReadTipConfig.timeBatteryPercentage
            isBattery = false
            typeface = ChapterProvider.typeface
            textSize = 12f
        }
        applyAdvancedTipChromeVisibility()
    }

    /**
     * 获取信息视图
     * @param tip 信息类型
     */
    private fun getTipView(tip: Int): BatteryView? = binding.run {
        return when (tip) {
            ReadTipConfig.tipHeaderLeft -> tvHeaderLeft
            ReadTipConfig.tipHeaderMiddle -> tvHeaderMiddle
            ReadTipConfig.tipHeaderRight -> tvHeaderRight
            ReadTipConfig.tipFooterLeft -> tvFooterLeft
            ReadTipConfig.tipFooterMiddle -> tvFooterMiddle
            ReadTipConfig.tipFooterRight -> tvFooterRight
            else -> null
        }
    }

    /**
     * 更新背景
     */
    fun upBg() {
        val bgDrawable = ReadBookConfig.bg?.safePageBackgroundDrawable()
        val followScrollBackground =
            AppConfig.readScrollFollowBackground &&
                isScroll &&
                !ReadBookConfig.isNineBgImg &&
                bgDrawable is BitmapDrawable &&
                !bgDrawable.bitmap.isRecycled
        val bgAlpha = (ReadBookConfig.bgAlpha / 100f * 255).toInt()
        if (followScrollBackground) {
            // Draw scrolling wallpaper on the whole page so header/footer are not solid mean-color bars.
            // Content no longer paints its own copy (avoids double-darkening).
            binding.contentTextView.setScrollFollowBackground(null, bgAlpha)
            val follow = ScrollFollowBackgroundDrawable(
                bitmap = bgDrawable.bitmap,
                offsetProvider = { binding.contentTextView.getBackgroundOffset() },
                yBiasProvider = { binding.contentTextView.top.toFloat() }
            ).apply { alpha = bgAlpha }
            binding.vwRoot.background = LayerDrawable(
                arrayOf(
                    ReadBookConfig.bgMeanColor.toDrawable(),
                    follow
                )
            )
            binding.llHeader.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.llFooter.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        } else {
            binding.contentTextView.setScrollFollowBackground(null, bgAlpha)
            binding.vwRoot.background = bgDrawable?.let {
                LayerDrawable(
                    arrayOf(
                        ReadBookConfig.bgMeanColor.toDrawable(),
                        it
                    )
                )
            } ?: ReadBookConfig.bgMeanColor.toDrawable()
            binding.llHeader.background = null
            binding.llFooter.background = null
        }
        upBgAlpha()
    }

    /**
     * 更新背景透明度
     */
    fun upBgAlpha() {
        val bgAlpha = (ReadBookConfig.bgAlpha / 100f * 255).toInt()
        binding.contentTextView.setScrollFollowBackgroundAlpha(bgAlpha)
        val background = binding.vwRoot.background
        if (background is LayerDrawable && background.numberOfLayers > 1) {
            background.getDrawable(1).alpha = bgAlpha
        } else {
            background?.alpha = bgAlpha
        }
        binding.vwRoot.invalidate()
    }

    private fun Drawable.safePageBackgroundDrawable(): Drawable? {
        if (this is BitmapDrawable) {
            val source = bitmap ?: return null
            if (source.isRecycled) return null
            return BitmapDrawable(resources, source).apply {
                alpha = this@safePageBackgroundDrawable.alpha
            }
        }
        return constantState?.newDrawable(resources)?.mutate() ?: mutate()
    }

    /**
     * 更新时间信息
     */
    fun upTime() {
        tvTime?.text = timeFormat.format(Date(System.currentTimeMillis()))
        upTimeBattery()
        if (ReadTipConfig.isHeaderAdvanced() || ReadTipConfig.isFooterAdvanced()) {
            lastTipContext = lastTipContext.copy(time = AdvancedTipConfig.currentTimeText())
            refreshAdvancedTipFieldsIfBound()
            markSnapDirty()
        }
    }

    /**
     * 更新电池信息
     */
    @SuppressLint("SetTextI18n")
    fun upBattery(battery: Int) {
        this.battery = battery
        tvBattery?.setBattery(battery)
        tvBatteryP?.text = "$battery%"
        upTimeBattery()
        if (ReadTipConfig.isHeaderAdvanced() || ReadTipConfig.isFooterAdvanced()) {
            lastTipContext = lastTipContext.copy(battery = battery.toString())
            refreshAdvancedTipFieldsIfBound()
            markSnapDirty()
        }
    }

    /**
     * 更新电池信息
     */
    @SuppressLint("SetTextI18n")
    private fun upTimeBattery() {
        val time = timeFormat.format(Date(System.currentTimeMillis()))
        tvTimeBattery?.setBattery(battery, time)
        tvTimeBatteryP?.text = "$time $battery%"
    }

    /**
     * 设置内容
     */
    private fun markSnapDirty() {
        snapRevision++
    }

    private fun titleViews(slot: AdvancedTitleSlot): Pair<LottieAnimationView, TextView> {
        return when (slot) {
            AdvancedTitleSlot.PRIMARY -> binding.advancedTitleLottie to binding.advancedTitleFallback
            AdvancedTitleSlot.PAIR -> binding.advancedTitleLottiePair to binding.advancedTitleFallbackPair
        }
    }

    private fun clearPendingAdvancedTitleComposition(slot: AdvancedTitleSlot) {
        pendingAdvancedTitleCompositions[slot.ordinal] = null
    }

    private fun cancelAdvancedTitleImagePreparation(slot: AdvancedTitleSlot) {
        advancedTitleImagePrepareJobs[slot.ordinal]?.cancel()
        advancedTitleImagePrepareJobs[slot.ordinal] = null
    }

    private fun cancelAdvancedTipPreparation(slot: AdvancedTipSlot) {
        advancedTipPrepareJobs[slot.ordinal]?.cancel()
        advancedTipPrepareJobs[slot.ordinal] = null
    }

    private fun clearAppliedAdvancedTitle(slot: AdvancedTitleSlot) {
        appliedAdvancedTitleKeys[slot.ordinal] = null
        appliedAdvancedTitleCompositions[slot.ordinal] = null
        appliedAdvancedTitleRenderConfigs[slot.ordinal] = null
        boundAdvancedTitleBlocks[slot.ordinal] = null
    }

    internal fun clearPendingAdvancedTitleCompositions() {
        pendingAdvancedTitleCompositions.fill(null)
        AdvancedTitleSlot.entries.forEach(::cancelAdvancedTitleImagePreparation)
    }

    internal fun disposeAdvancedTitleRequests() {
        advancedTitleRequestGate.nextContent()
        overlayBindToken++
        overlayBindScheduledToken = overlayBindToken
        removeCallbacks(overlayBindRunnable)
        clearPendingAdvancedTitleCompositions()
        pendingAdvancedTipCompositions.fill(null)
        currentAdvancedTipRequests.fill(null)
        AdvancedTipSlot.entries.forEach(::cancelAdvancedTipPreparation)
        advancedTitleAnimationsFrozenForPageTurn = false
        advancedTitleAnimationsToResume.fill(false)
        advancedTipAnimationsToResume.fill(false)
        AdvancedTitleSlot.entries.forEach { slot ->
            hideAdvancedTitleSlot(slot)
            val (lottieView, _) = titleViews(slot)
            lottieView.setImageAssetDelegate(null)
            lottieView.setTextDelegate(null)
            clearAppliedAdvancedTitle(slot)
        }
        AdvancedTipSlot.entries.forEach(::clearAdvancedTipSlot)
    }

    internal fun freezeAdvancedTitleAnimationsForPageTurn() {
        if (isScroll || advancedTitleAnimationsFrozenForPageTurn) return
        advancedTitleAnimationsFrozenForPageTurn = true
        var pixelsWereChanging = false
        AdvancedTitleSlot.entries.forEach { slot ->
            val (lottieView, _) = titleViews(slot)
            if (lottieView.visibility == VISIBLE && lottieView.isAnimating) {
                advancedTitleAnimationsToResume[slot.ordinal] = true
                lottieView.pauseAnimation()
                pixelsWereChanging = true
            }
        }
        AdvancedTipSlot.entries.forEach { slot ->
            val lottieView = tipView(slot)
            if (lottieView.visibility == VISIBLE && lottieView.isAnimating) {
                advancedTipAnimationsToResume[slot.ordinal] = true
                lottieView.pauseAnimation()
                pixelsWereChanging = true
            }
        }
        if (pixelsWereChanging) {
            // Lottie frames do not otherwise advance snapRevision. Capture this stable live
            // frame instead of reusing a recorder from an earlier animation frame.
            markSnapDirty()
        }
    }

    internal fun resumeAdvancedTitleAnimationsAfterPageTurn() {
        if (!advancedTitleAnimationsFrozenForPageTurn) return
        advancedTitleAnimationsFrozenForPageTurn = false
        if (isMainView && !isScroll) {
            AdvancedTitleSlot.entries.forEach { slot ->
                val shouldResume = advancedTitleAnimationsToResume[slot.ordinal]
                advancedTitleAnimationsToResume[slot.ordinal] = false
                val (lottieView, _) = titleViews(slot)
                if (!shouldResume || lottieView.visibility != VISIBLE ||
                    !isAdvancedTitleReady(slot)
                ) return@forEach
                runCatching {
                    if (!lottieView.isAnimating) lottieView.playAnimation()
                }.onFailure { error ->
                    AppLog.put("PageView advanced title resume failed: $slot", error)
                    lottieView.pauseAnimation()
                }
            }
            AdvancedTipSlot.entries.forEach { slot ->
                val shouldResume = advancedTipAnimationsToResume[slot.ordinal]
                advancedTipAnimationsToResume[slot.ordinal] = false
                val lottieView = tipView(slot)
                if (shouldResume && lottieView.visibility == VISIBLE &&
                    advancedTipKey(slot) != null && !lottieView.isAnimating
                ) {
                    runCatching {
                        lottieView.playAnimation()
                    }.onFailure { error ->
                        AppLog.put("PageView advanced tip resume failed: $slot", error)
                        lottieView.pauseAnimation()
                    }
                }
            }
        } else {
            advancedTitleAnimationsToResume.fill(false)
            advancedTipAnimationsToResume.fill(false)
        }
    }

    /**
     * Slots whose composed title also belongs to the page being bound, so re-binding would
     * only rebuild what is already correct on screen.
     *
     * Deliberately strict: retention is only claimed when the slot is drawing a composition
     * for the identical block instance the incoming page carries. Retaining a slot wrongly
     * would leave one page's title sitting over another, which is worse than a flash.
     */
    /**
     * Slots already drawing the incoming page's title, so tearing them down would only
     * rebuild what is correct on screen.
     *
     * Must be called before the content generation is bumped: the proof it relies on is the
     * presented token, which the bump discards.
     */
    private fun advancedTitleSlotsAlreadyShowing(
        textPage: TextPage,
        pairedTextPage: TextPage?
    ): Set<AdvancedTitleSlot> {
        if (isScroll) return emptySet()
        if (ReadBookConfig.titleMode != AdvancedTitleConfig.TITLE_MODE_ADVANCED) return emptySet()
        val pages = arrayOf<TextPage?>(textPage, pairedTextPage)
        return AdvancedTitleSlot.entries.filterTo(mutableSetOf()) { slot ->
            val incoming = advancedTitleBlock(pages[slot.ordinal]) ?: return@filterTo false
            val (lottieView, _) = titleViews(slot)
            boundAdvancedTitleBlocks[slot.ordinal] === incoming &&
                isAdvancedTitlePresented(slot) &&
                lottieView.composition != null &&
                lottieView.visibility == VISIBLE
        }
    }

    /**
     * Put back the titles of pages being returned to, within the current frame.
     *
     * Must be called after the content generation is bumped, since the tokens it opens have
     * to belong to the generation now being bound.
     */
    private fun restoreCommittedAdvancedTitles(
        textPage: TextPage,
        pairedTextPage: TextPage?,
        skip: Set<AdvancedTitleSlot>
    ): Set<AdvancedTitleSlot> {
        if (isScroll) return emptySet()
        if (ReadBookConfig.titleMode != AdvancedTitleConfig.TITLE_MODE_ADVANCED) return emptySet()
        val pages = arrayOf<TextPage?>(textPage, pairedTextPage)
        return AdvancedTitleSlot.entries.filterTo(mutableSetOf()) { slot ->
            if (slot in skip) return@filterTo false
            val block = advancedTitleBlock(pages[slot.ordinal]) ?: return@filterTo false
            restoreCommittedAdvancedTitle(slot, pages[slot.ordinal], block)
        }
    }

    /**
     * Re-show a title whose composition is still held, within the current frame.
     *
     * Returns true only once the slot is actually drawing it. Anything unproven is left to
     * the normal bind, which repaints from scratch: showing the wrong title would be a far
     * worse outcome than the flash this avoids.
     */
    private fun restoreCommittedAdvancedTitle(
        slot: AdvancedTitleSlot,
        textPage: TextPage?,
        block: TextPage.EpubEmbeddedBlock
    ): Boolean {
        val page = textPage ?: return false
        val key = advancedTitleKeysByPage[advancedTitlePageKey(page)] ?: return false
        val committed = committedAdvancedTitles[key] ?: return false
        val (lottieView, fallbackView) = titleViews(slot)
        // The size baked into the key has to still be the size this slot renders at, or the
        // composition would be drawn for bounds it was not built for.
        val (targetWidth, targetHeight) = resolveTitleViewSize(block)
        if (!key.endsWith(":$targetWidth:$targetHeight")) return false
        return runCatching {
            // Geometry first: committing only swaps the composition in, so without this the
            // title would be drawn wherever the previous page left the slot.
            val params = lottieView.layoutParams as ViewGroup.LayoutParams
            if (params.width != targetWidth || params.height != targetHeight) {
                params.width = targetWidth
                params.height = targetHeight
                lottieView.layoutParams = params
            }
            lottieView.scaleType = ImageView.ScaleType.FIT_CENTER
            val pageOffsetX = if (slot == AdvancedTitleSlot.PAIR && ChapterProvider.doublePage) {
                binding.contentTextView.width / 2f
            } else {
                0f
            }
            lottieView.translationX = resolveTitleTranslationX(block, targetWidth, pageOffsetX)
            lottieView.translationY = resolveTitleTranslationY(block, targetHeight, 0f)
            lottieView.clipBounds = null
            val token = advancedTitleRequestGate.begin(
                slot,
                key,
                "content:${advancedTitleRequestGate.contentGeneration}:${slot.name}"
            )
            lottieView.tag = key
            commitAdvancedTitleComposition(
                PendingAdvancedTitleComposition(token, committed.composition, committed.renderConfig)
            )
            val restored = isAdvancedTitleReady(slot) && lottieView.visibility == VISIBLE
            if (restored) {
                boundAdvancedTitleBlocks[slot.ordinal] = block
                fallbackView.visibility = GONE
            }
            restored
        }.getOrDefault(false)
    }

    private fun hideAdvancedTitleSlot(slot: AdvancedTitleSlot) {
        val (lottieView, fallbackView) = titleViews(slot)
        val pixelsChanged = lottieView.visibility != GONE || fallbackView.visibility != GONE
        // This slot no longer shows anything, so it must not claim retention on the next bind.
        boundAdvancedTitleBlocks[slot.ordinal] = null
        advancedTitleRequestGate.clear(slot)
        clearPendingAdvancedTitleComposition(slot)
        cancelAdvancedTitleImagePreparation(slot)
        clearAdvancedTitleLoadingState(lottieView)
        lottieView.cancelAnimation()
        lottieView.renderMode = RenderMode.AUTOMATIC
        lottieView.visibility = GONE
        fallbackView.visibility = GONE
        if (pixelsChanged) markSnapDirty()
    }

    private fun advancedTitleBlock(textPage: TextPage?): TextPage.EpubEmbeddedBlock? {
        if (ReadBookConfig.titleMode != AdvancedTitleConfig.TITLE_MODE_ADVANCED) return null
        return textPage?.epubEmbeddedBlocks?.firstOrNull {
            it.role == AdvancedTitleConfig.LOTTIE_BLOCK_ROLE
        }
    }

    private fun resolveTitleViewSize(block: TextPage.EpubEmbeddedBlock): Pair<Int, Int> {
        return block.width.toInt().coerceAtLeast(1) to block.height.toInt().coerceAtLeast(1)
    }

    private fun resolveTitleTranslationX(
        block: TextPage.EpubEmbeddedBlock,
        targetWidth: Int,
        pageOffsetX: Float
    ): Float {
        val contentWidth = binding.contentTextView.width
        if (contentWidth <= 0) return pageOffsetX + block.offsetX
        val centeredX = (contentWidth - targetWidth) / 2f
        return pageOffsetX + block.offsetX - centeredX
    }

    private fun resolveTitleTranslationY(
        block: TextPage.EpubEmbeddedBlock,
        targetHeight: Int,
        scrollBaseY: Float
    ): Float {
        if (isScroll) return scrollBaseY + block.offsetY
        val contentHeight = binding.contentTextView.height
        if (contentHeight <= 0) return block.offsetY
        val maxTranslation = (contentHeight - targetHeight).toFloat().coerceAtLeast(0f)
        return block.offsetY.coerceIn(0f, maxTranslation)
    }

    /**
     * Lay out the plain-text title for [block]. [revealText] decides whether the text is
     * actually painted: while an advanced composition is still loading the slot only
     * reserves its geometry (INVISIBLE), so the reader never sees the plain title flash
     * before the advanced one. It is revealed only when the composition genuinely fails.
     */
    private fun showAdvancedTitleFallback(
        slot: AdvancedTitleSlot,
        textPage: TextPage,
        block: TextPage.EpubEmbeddedBlock,
        pageOffsetX: Float,
        scrollBaseY: Float = 0f,
        revealText: Boolean = false
    ) {
        hideAdvancedTitleSlot(slot)
        val (lottieView, fallbackView) = titleViews(slot)
        val (targetWidth, targetHeight) = resolveTitleViewSize(block)
        val params = fallbackView.layoutParams as ViewGroup.LayoutParams
        if (params.width != targetWidth || params.height != targetHeight) {
            params.width = targetWidth
            params.height = targetHeight
            fallbackView.layoutParams = params
        }
        fallbackView.translationX = resolveTitleTranslationX(block, targetWidth, pageOffsetX)
        fallbackView.translationY = resolveTitleTranslationY(block, targetHeight, scrollBaseY)
        if (isScroll) {
            applyTitleContentClip(
                fallbackView,
                fallbackView.translationY,
                targetWidth,
                targetHeight
            )
        } else {
            fallbackView.clipBounds = null
        }
        fallbackView.gravity = Gravity.CENTER
        fallbackView.text = textPage.title
        // Reserve geometry without painting text until the advanced title is known to fail.
        fallbackView.visibility = if (revealText) VISIBLE else INVISIBLE
        lottieView.visibility = GONE
        markSnapDirty()
    }

    private fun showSynchronousAdvancedTitleFallbacks(
        textPage: TextPage,
        pairedTextPage: TextPage?,
        retainedSlots: Set<AdvancedTitleSlot> = emptySet()
    ) {
        if (ReadBookConfig.titleMode != AdvancedTitleConfig.TITLE_MODE_ADVANCED) {
            AdvancedTitleSlot.entries.forEach(::hideAdvancedTitleSlot)
            return
        }
        if (isScroll) {
            val titles = collectScrollAdvancedTitles(
                binding.contentTextView.width.toFloat().coerceAtLeast(1f)
            )
            AdvancedTitleSlot.entries.forEach { slot ->
                val title = titles.getOrNull(slot.ordinal)
                boundScrollTitleIds[slot.ordinal] = title?.id
                if (title == null) {
                    hideAdvancedTitleSlot(slot)
                } else {
                    showAdvancedTitleFallback(
                        slot = slot,
                        textPage = title.textPage,
                        block = title.block,
                        pageOffsetX = 0f,
                        scrollBaseY = title.y - title.block.offsetY
                    )
                    val (_, fallbackView) = titleViews(slot)
                    fallbackView.translationX = title.x
                    fallbackView.translationY = title.y
                    applyTitleContentClip(fallbackView, title.y, title.width, title.height)
                }
            }
            return
        }

        val contentWidth = binding.contentTextView.width
        val useDoublePage = ChapterProvider.doublePage
        val pairOffsetX = if (useDoublePage) contentWidth / 2f else 0f
        val pages = arrayOf(textPage, pairedTextPage)
        AdvancedTitleSlot.entries.forEach { slot ->
            // A retained slot is already showing this page's title; touching it here would
            // hide the composition and reintroduce the flash this retention exists to avoid.
            if (slot in retainedSlots) return@forEach
            val page = pages[slot.ordinal]
            val block = advancedTitleBlock(page)
            if (page == null || block == null) {
                hideAdvancedTitleSlot(slot)
            } else {
                showAdvancedTitleFallback(
                    slot = slot,
                    textPage = page,
                    block = block,
                    pageOffsetX = if (slot == AdvancedTitleSlot.PAIR) pairOffsetX else 0f
                )
            }
        }
    }

    private fun schedulePageTurnPrewarm() {
        (parent as? ReadView)?.schedulePageTurnPrewarm()
    }

    /**
     * Critical path: body text + classic tip labels only.
     * Advanced title/header/footer Lottie always binds on the next frame (idle),
     * so first open / chapter switch never stalls on setComposition or Lottie draw.
     */
    fun setContent(
        textPage: TextPage,
        pairedTextPage: TextPage? = null,
        resetPageOffset: Boolean = true
    ) {
        // Note which slots are already drawing this page's title before the generation bump
        // retires the tokens that proves it.
        val alreadyShowing = advancedTitleSlotsAlreadyShowing(textPage, pairedTextPage)
        advancedTitleRequestGate.nextContent()
        clearPendingAdvancedTitleCompositions()
        pendingAdvancedTipCompositions.fill(null)
        currentAdvancedTipRequests.fill(null)
        boundScrollTitleIds.fill(null)
        AdvancedTitleSlot.entries.forEach { slot ->
            if (slot !in alreadyShowing) hideAdvancedTitleSlot(slot)
        }
        // Now that tokens belong to this generation, put back any title whose composition is
        // still held. Doing it here rather than in the posted bind keeps the slot from
        // sitting blank for a frame, which is what shows as a flash when turning back and
        // forth quickly.
        val retainedSlots = alreadyShowing +
            restoreCommittedAdvancedTitles(textPage, pairedTextPage, alreadyShowing)
        currentTextPage = textPage
        this.pairedTextPage = pairedTextPage
        markSnapDirty()
        upTipStyle(textPage)
        if (resetPageOffset) {
            resetPageOffset()
        }
        // Body text first and only on the hot path.
        binding.contentTextView.setContent(textPage, pairedTextPage, resetPageOffset)
        // Never let a previous page's composition survive beside the new body. The cheap,
        // correct-page fallback stays visible until the matching composition is drawable.
        showSynchronousAdvancedTitleFallbacks(textPage, pairedTextPage, retainedSlots)
        // Classic tip TextViews are cheap; keep them in sync for non-advanced chrome.
        applyProgressTexts(textPage)
        // If tip Lottie already composed, only refresh TextDelegate variables (no re-parse).
        refreshAdvancedTipFieldsIfBound()
        // Advanced Lottie bind/parse only on next frame; never prewarm screenshots here.
        scheduleOverlayBind()
    }

    private fun scheduleOverlayBind() {
        overlayBindToken++
        overlayBindScheduledToken = overlayBindToken
        removeCallbacks(overlayBindRunnable)
        // One frame later: never compete with body text layout/draw of this frame.
        post(overlayBindRunnable)
    }

    private fun bindAdvancedOverlaysIdle() {
        val textPage = currentTextPage ?: return
        val contentGeneration = advancedTitleRequestGate.contentGeneration
        runCatching {
            upAdvancedTitleLotties(textPage, pairedTextPage, contentGeneration)
        }.onFailure { error ->
            AppLog.put("PageView advanced title bind failed", error)
            showSynchronousAdvancedTitleFallbacks(textPage, pairedTextPage)
        }
        runCatching {
            if (ReadTipConfig.isHeaderAdvanced() || ReadTipConfig.isFooterAdvanced()) {
                upAdvancedTipLotties(textPage, contentGeneration)
            }
        }.onFailure { error ->
            AppLog.put("PageView advanced tip bind failed", error)
        }
        // Warm the next page's title so the first flip does not parse it on the hot path.
        runCatching {
            prewarmAdvancedTitleFromPage(pairedTextPage)
        }.onFailure { error ->
            AppLog.put("PageView advanced title prewarm failed", error)
        }
        // Header/footer packages are shared across page views; warming them once here
        // keeps the first flip off the parse/decode path.
        runCatching {
            prewarmAdvancedTipCompositions()
        }.onFailure { error ->
            AppLog.put("PageView advanced tip prewarm failed", error)
        }
    }

    /** Update classic tip labels without touching Lottie. */
    @SuppressLint("SetTextI18n")
    private fun applyProgressTexts(textPage: TextPage) = textPage.apply {
        tvBookName?.setTextIfNotEqual(ReadBook.book?.name)
        tvTitle?.setTextIfNotEqual(textPage.title)
        val readProgress = readProgress
        tvTotalProgress?.setTextIfNotEqual(readProgress)
        tvTotalProgress1?.setTextIfNotEqual("${chapterIndex.plus(1)}/${chapterSize}")
        if (textChapter.isCompleted) {
            tvPageAndTotal?.setTextIfNotEqual("${index.plus(1)}/$pageSize  $readProgress")
            tvPage?.setTextIfNotEqual("${index.plus(1)}/$pageSize")
        } else {
            val pageSizeInt = pageSize
            val pageSizeText = if (pageSizeInt <= 0) "-" else "~$pageSizeInt"
            tvPageAndTotal?.setTextIfNotEqual("${index.plus(1)}/$pageSizeText  $readProgress")
            tvPage?.setTextIfNotEqual("${index.plus(1)}/$pageSizeText")
        }
        lastTipContext = AdvancedTipConfig.TipContext(
            book = ReadBook.book?.name.orEmpty(),
            title = textPage.title,
            page = (index + 1).toString(),
            pages = if (textChapter.isCompleted) pageSize.toString() else if (pageSize <= 0) "-" else "~${pageSize}",
            progress = readProgress,
            time = AdvancedTipConfig.currentTimeText(),
            battery = battery.toString(),
            author = ReadBook.book?.author.orEmpty()
        )
    }

    /**
     * Cheap path: tip Lottie already on-screen for this package — only swap text fields.
     * Avoids setComposition / parse on page turns and minute ticks.
     */
    private fun refreshAdvancedTipFieldsIfBound() {
        if (!(ReadTipConfig.isHeaderAdvanced() || ReadTipConfig.isFooterAdvanced())) return
        val vars = AdvancedTipConfig.variables(lastTipContext)
        if (ReadTipConfig.isHeaderAdvanced()) {
            val key = AdvancedTipConfig.compositionCacheKey(AdvancedTipSlot.HEADER)
            if (advancedHeaderLottieKey == key && binding.advancedHeaderLottie.composition != null) {
                headerTipTextDelegate?.let {
                    it.variables = vars
                    binding.advancedHeaderLottie.invalidate()
                }
            }
        }
        if (ReadTipConfig.isFooterAdvanced()) {
            val key = AdvancedTipConfig.compositionCacheKey(AdvancedTipSlot.FOOTER)
            if (advancedFooterLottieKey == key && binding.advancedFooterLottie.composition != null) {
                footerTipTextDelegate?.let {
                    it.variables = vars
                    binding.advancedFooterLottie.invalidate()
                }
            }
        }
    }

    /** Best-effort: parse title payload of an adjacent page into Lottie cache off the hot path. */
    private fun prewarmAdvancedTitleFromPage(textPage: TextPage?) {
        if (ReadBookConfig.titleMode != AdvancedTitleConfig.TITLE_MODE_ADVANCED) return
        val block = textPage?.epubEmbeddedBlocks?.firstOrNull {
            it.role == AdvancedTitleConfig.LOTTIE_BLOCK_ROLE
        } ?: return
        val json = block.payload?.takeIf { it.isNotBlank() } ?: return
        val pageWidth = binding.contentTextView.width.toFloat().coerceAtLeast(1f)
        val styled = applyLottieTextFallbackStyle(json, advancedTitleTextLayerScale(block, pageWidth))
        val tw = block.width.toInt().coerceAtLeast(1)
        val th = block.height.toInt().coerceAtLeast(1)
        val key = AdvancedTitleLottieKeys.composition(styled.json, tw, th)
        if (LottieCompositionCache.getInstance().get(key) != null) return
        LottieCompositionFactory.fromJsonString(styled.json, key)
    }

    fun invalidateContentView() {
        binding.contentTextView.invalidate()
    }

    /**
     * 设置无障碍文本
     */
    fun setContentDescription(content: String) {
        binding.contentTextView.contentDescription = content
    }

    /**
     * 重置滚动位置
     */
    fun resetPageOffset() {
        binding.contentTextView.resetPageOffset()
    }

    /**
     * 设置进度
     */
    @SuppressLint("SetTextI18n")
    fun setProgress(textPage: TextPage) {
        applyProgressTexts(textPage)
        // Prefer field-only refresh; full tip bind stays on idle overlay path.
        refreshAdvancedTipFieldsIfBound()
        if ((ReadTipConfig.isHeaderAdvanced() || ReadTipConfig.isFooterAdvanced()) &&
            !advancedTipViewsBound()
        ) {
            scheduleOverlayBind()
        }
    }

    private fun advancedTipViewsBound(): Boolean {
        if (ReadTipConfig.isHeaderAdvanced()) {
            val key = AdvancedTipConfig.compositionCacheKey(AdvancedTipSlot.HEADER)
            if (advancedHeaderLottieKey != key || binding.advancedHeaderLottie.composition == null) {
                return false
            }
        }
        if (ReadTipConfig.isFooterAdvanced()) {
            val key = AdvancedTipConfig.compositionCacheKey(AdvancedTipSlot.FOOTER)
            if (advancedFooterLottieKey != key || binding.advancedFooterLottie.composition == null) {
                return false
            }
        }
        return true
    }

    fun setAutoPager(autoPager: AutoPager?) {
        binding.contentTextView.setAutoPager(autoPager)
    }

    fun submitRenderTask() {
        binding.contentTextView.submitRenderTask()
    }

    fun setIsScroll(value: Boolean) {
        val changed = isScroll != value
        isScroll = value
        if (changed && !value) {
            scrollTitleLinger = emptyList()
            lastScrollPageOffsetForTitle = Int.MIN_VALUE
            boundScrollTitleIds[0] = null
            boundScrollTitleIds[1] = null
        }
        binding.contentTextView.setIsScroll(value)
        if (value) {
            AdvancedTitleSlot.entries.forEach { slot ->
                titleViews(slot).first.pauseAnimation()
            }
        } else if (!advancedTitleAnimationsFrozenForPageTurn && isMainView) {
            AdvancedTitleSlot.entries.forEach { slot ->
                val lottieView = titleViews(slot).first
                if (lottieView.visibility == VISIBLE && isAdvancedTitleReady(slot)) {
                    lottieView.playAnimation()
                }
            }
        }
        if (changed && AppConfig.readScrollFollowBackground) {
            upBg()
        }
    }

    /**
     * 滚动事件
     */
    fun scroll(offset: Int) {
        binding.contentTextView.scroll(offset)
        if (isScroll) {
            // Position-only sync: never reparse/reload Lottie during finger scroll (avoids jitter).
            syncScrollAdvancedTitlePositions()
            // Root wallpaper is driven by backgroundScrollOffset; invalidate for follow-bg.
            if (AppConfig.readScrollFollowBackground) {
                binding.vwRoot.invalidate()
            }
        }
    }

    /**
     * 更新是否开启选择功能
     */
    fun upSelectAble(selectAble: Boolean) {
        binding.contentTextView.selectAble = selectAble
    }

    /**
     * 优先处理页面内单击
     * @return true:已处理, false:未处理
     */
    fun onClick(x: Float, y: Float): Boolean {
        return binding.contentTextView.click(x - imgBgPaddingStart, y - headerHeight)
    }

    /**
     * 长按事件
     */
    fun longPress(
        x: Float, y: Float,
        select: (textPos: TextPos) -> Unit,
    ): Boolean =
        binding.contentTextView.longPress(x - imgBgPaddingStart, y - headerHeight, select)

    /**
     * 点击位置的文本位置, 用于取词
     */
    fun textPosAt(x: Float, y: Float): TextPos? =
        binding.contentTextView.textPosAt(x - imgBgPaddingStart, y - headerHeight)

    /**
     * 选择文本
     */
    fun selectText(
        x: Float, y: Float,
        select: (textPos: TextPos) -> Unit,
    ) {
        return binding.contentTextView.selectText(x - imgBgPaddingStart, y - headerHeight, select)
    }

    fun getCurVisiblePage(): TextPage {
        return binding.contentTextView.getCurVisiblePage()
    }

    fun getReadAloudPos(): Pair<Int, TextLine>? {
        return binding.contentTextView.getReadAloudPos()
    }

    fun markAsMainView() {
        isMainView = true
        binding.contentTextView.isMainView = true
    }

    fun selectStartMove(x: Float, y: Float) {
        binding.contentTextView.selectStartMove(x - imgBgPaddingStart, y - headerHeight)
    }

    fun refreshSelectionHandles() = binding.contentTextView.refreshSelectionHandles()

    val selectionTop: Float get() = headerHeight + ChapterProvider.visibleRect.top
    val selectionBottom: Float get() = headerHeight + ChapterProvider.visibleRect.bottom

    fun selectStartMoveIndex(
        relativePagePos: Int,
        lineIndex: Int,
        charIndex: Int
    ) {
        binding.contentTextView.selectStartMoveIndex(relativePagePos, lineIndex, charIndex)
    }

    fun selectStartMoveIndex(textPos: TextPos) {
        binding.contentTextView.selectStartMoveIndex(textPos)
    }

    fun selectEndMove(x: Float, y: Float) {
        binding.contentTextView.selectEndMove(x - imgBgPaddingStart, y - headerHeight)
    }

    fun selectEndMoveIndex(
        relativePagePos: Int,
        lineIndex: Int,
        charIndex: Int
    ) {
        binding.contentTextView.selectEndMoveIndex(relativePagePos, lineIndex, charIndex)
    }

    fun selectEndMoveIndex(textPos: TextPos) {
        binding.contentTextView.selectEndMoveIndex(textPos)
    }

    fun getReverseStartCursor(): Boolean {
        return binding.contentTextView.reverseStartCursor
    }

    fun getReverseEndCursor(): Boolean {
        return binding.contentTextView.reverseEndCursor
    }

    fun isLongScreenShot(): Boolean {
        return binding.contentTextView.longScreenshot
    }

    fun resetReverseCursor() {
        binding.contentTextView.resetReverseCursor()
    }

    fun cancelSelect(clearSearchResult: Boolean = false) {
        binding.contentTextView.cancelSelect(clearSearchResult)
    }

    fun createBookmark(): Bookmark? {
        return binding.contentTextView.createBookmark()
    }

    fun relativePage(relativePagePos: Int): TextPage {
        return binding.contentTextView.relativePage(relativePagePos)
    }

    private fun upAdvancedTitleLotties(
        textPage: TextPage,
        pairedTextPage: TextPage?,
        contentGeneration: Long
    ) {
        if (contentGeneration != advancedTitleRequestGate.contentGeneration) return
        val contentWidth = binding.contentTextView.width
        if (isScroll) {
            val pageWidth = contentWidth.toFloat().coerceAtLeast(1f)
            val titles = collectScrollAdvancedTitles(pageWidth)
            bindScrollAdvancedTitle(
                title = titles.getOrNull(0),
                pageWidth = pageWidth,
                slot = AdvancedTitleSlot.PRIMARY,
                contentGeneration = contentGeneration
            )
            bindScrollAdvancedTitle(
                title = titles.getOrNull(1),
                pageWidth = pageWidth,
                slot = AdvancedTitleSlot.PAIR,
                contentGeneration = contentGeneration
            )
            boundScrollTitleIds[0] = titles.getOrNull(0)?.id
            boundScrollTitleIds[1] = titles.getOrNull(1)?.id
            return
        }
        val useDoublePage = ChapterProvider.doublePage
        val pairOffsetX = if (useDoublePage) contentWidth / 2f else 0f
        val pageWidth = if (useDoublePage) {
            contentWidth / 2f
        } else {
            contentWidth.toFloat()
        }.coerceAtLeast(1f)
        bindAdvancedTitleSlotSafely(
            slot = AdvancedTitleSlot.PRIMARY,
            textPage = textPage,
            pageOffsetX = 0f,
            pageWidth = pageWidth,
            scrollBaseY = 0f,
            contentGeneration = contentGeneration
        )
        bindAdvancedTitleSlotSafely(
            slot = AdvancedTitleSlot.PAIR,
            textPage = pairedTextPage,
            pageOffsetX = pairOffsetX,
            pageWidth = pageWidth,
            scrollBaseY = 0f,
            contentGeneration = contentGeneration
        )
    }
    private data class ScrollAdvTitle(
        val id: String,
        val textPage: TextPage,
        val block: TextPage.EpubEmbeddedBlock,
        val x: Float,
        val y: Float,
        val width: Int,
        val height: Int
    )

    /**
     * Gather advanced titles still intersecting the reading viewport.
     * Titles stay mounted until fully outside (top or bottom), like normal painted titles.
     */
    private fun collectScrollAdvancedTitles(pageWidth: Float): List<ScrollAdvTitle> {
        if (ReadBookConfig.titleMode != AdvancedTitleConfig.TITLE_MODE_ADVANCED) {
            scrollTitleLinger = emptyList()
            return scrollTitleLinger
        }
        val content = binding.contentTextView
        // Overlay is content-sized: same coordinate space as painted text pages.
        val contentHeight = content.height.toFloat().coerceAtLeast(1f)
        val contentWidth = content.width.toFloat().coerceAtLeast(1f)
        val live = ArrayList<ScrollAdvTitle>(3)
        for (pos in 0..2) {
            if (!content.hasScrollRelativePage(pos)) continue
            val page = content.scrollRelativePage(pos)
            val block = page.epubEmbeddedBlocks.firstOrNull {
                it.role == AdvancedTitleConfig.LOTTIE_BLOCK_ROLE
            } ?: continue
            val width = block.width.toInt().coerceAtLeast(1)
            val height = block.height.toInt().coerceAtLeast(1)
            // Identical to text: relativeOffset(pos) + in-page offsetY.
            val y = content.scrollRelativeOffset(pos) + block.offsetY
            val x = block.offsetX - (contentWidth - width) / 2f
            // Keep while any pixel still intersects the content viewport (like normal text).
            if (y + height <= 0f || y >= contentHeight) continue
            val id = "${page.chapterIndex}:${page.index}:${page.title}"
            live.add(ScrollAdvTitle(id, page, block, x, y, width, height))
        }
        live.sortBy { it.y }
        scrollTitleLinger = live.take(2)
        return scrollTitleLinger
    }

    /**
     * Finger-scroll path: only move overlays. Reload happens in setContent via full bind.
     */
    private fun syncScrollAdvancedTitlePositions() {
        if (!isScroll) return
        if (ReadBookConfig.titleMode != AdvancedTitleConfig.TITLE_MODE_ADVANCED) {
            boundScrollTitleIds.fill(null)
            AdvancedTitleSlot.entries.forEach(::hideAdvancedTitleSlot)
            return
        }
        val pageWidth = binding.contentTextView.width.toFloat().coerceAtLeast(1f)
        val titles = collectScrollAdvancedTitles(pageWidth)
        val id0 = titles.getOrNull(0)?.id
        val id1 = titles.getOrNull(1)?.id
        val sameBinding =
            boundScrollTitleIds[0] == id0 &&
                boundScrollTitleIds[1] == id1 &&
                (id0 == null || isAdvancedTitleReady(AdvancedTitleSlot.PRIMARY) ||
                    binding.advancedTitleFallback.visibility == VISIBLE) &&
                (id1 == null || isAdvancedTitleReady(AdvancedTitleSlot.PAIR) ||
                    binding.advancedTitleFallbackPair.visibility == VISIBLE)
        if (!sameBinding) {
            // Keep JSON rewrite/parsing out of MotionEvent.MOVE. Publish the new page's
            // lightweight fallback now and let the posted idle bind load its composition.
            currentTextPage?.let { textPage ->
                showSynchronousAdvancedTitleFallbacks(textPage, pairedTextPage)
                scheduleOverlayBind()
            }
            return
        }
        applyScrollTitlePosition(AdvancedTitleSlot.PRIMARY, titles.getOrNull(0))
        applyScrollTitlePosition(AdvancedTitleSlot.PAIR, titles.getOrNull(1))
    }

    private fun applyTitleContentClip(view: android.view.View, y: Float, width: Int, height: Int) {
        if (y >= 0f) {
            view.clipBounds = null
            return
        }
        val top = (-y).toInt().coerceIn(0, height)
        view.clipBounds = if (top >= height) {
            android.graphics.Rect(0, 0, 0, 0)
        } else {
            android.graphics.Rect(0, top, width.coerceAtLeast(1), height.coerceAtLeast(1))
        }
    }

    private fun applyScrollTitlePosition(
        slot: AdvancedTitleSlot,
        title: ScrollAdvTitle?
    ) {
        val (lottieView, fallbackView) = titleViews(slot)
        if (title == null) {
            hideAdvancedTitleSlot(slot)
            return
        }
        arrayOf<android.view.View>(lottieView, fallbackView).forEach { view ->
            val params = view.layoutParams
            if (params.width != title.width || params.height != title.height) {
                params.width = title.width
                params.height = title.height
                view.layoutParams = params
            }
            view.translationX = title.x
            view.translationY = title.y
            applyTitleContentClip(view, title.y, title.width, title.height)
        }
        if (isAdvancedTitleReady(slot)) {
            lottieView.visibility = VISIBLE
            lottieView.pauseAnimation()
            lottieView.progress = 0f
            fallbackView.visibility = if (isAdvancedTitlePresented(slot)) GONE else VISIBLE
        } else {
            lottieView.pauseAnimation()
            lottieView.visibility = GONE
            fallbackView.text = title.textPage.title
            // Blank while still loading; only a failed request may paint the plain title.
            fallbackView.visibility =
                if (advancedTitleRequestGate.hasFailed(slot)) VISIBLE else INVISIBLE
        }
    }

    private fun isAdvancedTitleReady(slot: AdvancedTitleSlot): Boolean {
        val token = advancedTitleRequestGate.readyToken(slot) ?: return false
        val (lottieView, _) = titleViews(slot)
        return advancedTitleRequestGate.accepts(token) &&
            lottieView.tag == token.compositionKey &&
            appliedAdvancedTitleKeys[slot.ordinal] == token.compositionKey &&
            lottieView.composition != null
    }

    private fun isAdvancedTitlePresented(slot: AdvancedTitleSlot): Boolean {
        val token = advancedTitleRequestGate.presentedToken(slot) ?: return false
        return advancedTitleRequestGate.accepts(token) && isAdvancedTitleReady(slot)
    }

    private fun bindScrollAdvancedTitle(
        title: ScrollAdvTitle?,
        pageWidth: Float,
        slot: AdvancedTitleSlot,
        contentGeneration: Long
    ): String? {
        if (title == null) {
            boundScrollTitleIds[slot.ordinal] = null
            hideAdvancedTitleSlot(slot)
            return null
        }
        boundScrollTitleIds[slot.ordinal] = title.id
        // Absolute content coordinates: scrollBaseY carries full Y (contentOrigin=0 for content overlay).
        val key = bindAdvancedTitleSlotSafely(
            slot = slot,
            textPage = title.textPage,
            pageOffsetX = 0f,
            pageWidth = pageWidth,
            scrollBaseY = title.y - title.block.offsetY,
            forceVisibleInScroll = true,
            sourceId = title.id,
            contentGeneration = contentGeneration
        )
        // Force exact live position after bind (avoid recomputation drift).
        applyScrollTitlePosition(slot, title)
        return key
    }

    private fun bindAdvancedTitleSlotSafely(
        slot: AdvancedTitleSlot,
        textPage: TextPage?,
        pageOffsetX: Float,
        pageWidth: Float,
        scrollBaseY: Float,
        forceVisibleInScroll: Boolean = false,
        contentGeneration: Long,
        sourceId: String = "content:$contentGeneration:${slot.name}"
    ): String? {
        val (lottieView, fallbackView) = titleViews(slot)
        return runCatching {
            upAdvancedTitleLottie(
                textPage = textPage,
                lottieView = lottieView,
                fallbackView = fallbackView,
                pageOffsetX = pageOffsetX,
                pageWidth = pageWidth,
                scrollBaseY = scrollBaseY,
                forceVisibleInScroll = forceVisibleInScroll,
                slot = slot,
                contentGeneration = contentGeneration,
                sourceId = sourceId
            )
        }.getOrElse { error ->
            AppLog.put("PageView advanced title bind failed: $slot", error)
            val block = advancedTitleBlock(textPage)
            if (textPage != null && block != null) {
                showAdvancedTitleFallback(slot, textPage, block, pageOffsetX, scrollBaseY)
            } else {
                hideAdvancedTitleSlot(slot)
            }
            null
        }
    }
    private fun upAdvancedTitleLottie(
        textPage: TextPage?,
        lottieView: LottieAnimationView,
        fallbackView: TextView,
        pageOffsetX: Float,
        pageWidth: Float,
        scrollBaseY: Float = 0f,
        forceVisibleInScroll: Boolean = false,
        slot: AdvancedTitleSlot,
        contentGeneration: Long,
        sourceId: String
    ): String? {
        if (contentGeneration != advancedTitleRequestGate.contentGeneration) return null
        val page = textPage ?: run {
            hideAdvancedTitleSlot(slot)
            return null
        }
        val block = advancedTitleBlock(page) ?: run {
            hideAdvancedTitleSlot(slot)
            return null
        }
        // Scroll: still show Lottie, but freeze on keyframe (progress=0) and follow pageOffset.
        val (targetWidth, targetHeight) = resolveTitleViewSize(block)
        val params = lottieView.layoutParams as ViewGroup.LayoutParams
        if (params.width != targetWidth || params.height != targetHeight) {
            params.width = targetWidth
            params.height = targetHeight
            lottieView.layoutParams = params
        }
        lottieView.scaleType = ImageView.ScaleType.FIT_CENTER
        lottieView.translationX = resolveTitleTranslationX(block, targetWidth, pageOffsetX)
        lottieView.translationY = resolveTitleTranslationY(block, targetHeight, scrollBaseY)
        if (isScroll && !forceVisibleInScroll) {
            val overlayHeight = binding.advancedTitleOverlay.height.toFloat()
                .takeIf { it > 0f }
                ?: (binding.llHeader.height + binding.contentTextView.height).toFloat().coerceAtLeast(1f)
            val y = lottieView.translationY
            // Fully outside the reading overlay (top or bottom edge) — same as normal painted titles.
            val offScreen = y + targetHeight <= 0f || y >= overlayHeight
            if (offScreen) {
                hideAdvancedTitleSlot(slot)
                return null
            }
        }
        // Non-scroll may loop; scroll freezes on keyframe (progress 0).
        lottieView.repeatCount = if (isScroll) 0 else LottieDrawable.INFINITE
        lottieView.setFontAssetDelegate(defaultFontAssetDelegate)
        val json = block.payload?.takeIf { it.isNotBlank() }
        val textScale = advancedTitleTextLayerScale(block, pageWidth)
        val styled = json?.let { applyLottieTextFallbackStyle(it, textScale) }
        val resolvedJson = styled?.json
        val nextKey = resolvedJson?.let {
            AdvancedTitleLottieKeys.composition(it, targetWidth, targetHeight)
        } ?: "advanced_title:raw:$targetWidth:$targetHeight"
        val compositionSize = styled?.compositionSize
        val renderConfig = AdvancedTitleRenderConfig(
            imageAssetResolver = LottieImageAssetResolver(
                viewWidth = targetWidth,
                viewHeight = targetHeight,
                compositionWidth = compositionSize?.width ?: targetWidth,
                compositionHeight = compositionSize?.height ?: targetHeight,
                fallbackAssets = block.lottieFallbackAssets
            ),
            cacheComposition = resolvedJson == null
        )

        // Remember which title this slot is working on so a later page turn can tell that a
        // slot already holds the incoming page's title and leave it up.
        boundAdvancedTitleBlocks[slot.ordinal] = block
        // Record against the page's position too, so returning to it can find this key back
        // without restyling the JSON on the page-turn path.
        advancedTitleKeysByPage[advancedTitlePageKey(page)] = nextKey

        if (advancedTitleRequestGate.hasCurrent(slot, nextKey, sourceId) && lottieView.tag == nextKey) {
            if (pendingAdvancedTitleCompositions[slot.ordinal] != null) return nextKey
            if (isAdvancedTitleReady(slot)) {
                lottieView.visibility = VISIBLE
                fallbackView.visibility = if (isAdvancedTitlePresented(slot)) GONE else VISIBLE
                if (isMainView && !isScroll && !advancedTitleAnimationsFrozenForPageTurn &&
                    !lottieView.isAnimating
                ) {
                    lottieView.playAnimation()
                } else {
                    lottieView.pauseAnimation()
                    if (!isMainView || isScroll) lottieView.progress = 0f
                }
            }
            return nextKey
        }

        // The slot may already be drawing exactly this composition: a page turn bumps the
        // content generation, which retires the token above even though nothing about what
        // is on screen changed. Re-adopt it instead of composing again — going through
        // setComposition would blank the slot for a frame and read as a flash.
        val liveComposition = appliedAdvancedTitleCompositions[slot.ordinal]
        if (appliedAdvancedTitleKeys[slot.ordinal] == nextKey &&
            liveComposition != null &&
            lottieView.composition === liveComposition &&
            lottieView.visibility == VISIBLE &&
            appliedAdvancedTitleRenderConfigs[slot.ordinal] != null
        ) {
            val token = advancedTitleRequestGate.begin(slot, nextKey, sourceId)
            lottieView.tag = nextKey
            if (advancedTitleRequestGate.markReady(token)) {
                // Already on screen, so it has provably rendered; no pre-draw wait needed.
                advancedTitleRequestGate.markPresented(token)
                fallbackView.visibility = GONE
                if (isMainView && !isScroll && !advancedTitleAnimationsFrozenForPageTurn) {
                    if (!lottieView.isAnimating) lottieView.playAnimation()
                } else {
                    lottieView.pauseAnimation()
                    if (!isMainView || isScroll) lottieView.progress = 0f
                }
                return nextKey
            }
        }

        // Bind identity changed even when the package renders to the same composition key.
        // Publish the new page's text/geometry before reusing or loading any Lottie content.
        showAdvancedTitleFallback(slot, page, block, pageOffsetX, scrollBaseY)

        // A composition committed earlier can be put straight back: this is the returning
        // page's path, where the slot itself has since been handed to another page. Skipping
        // the reload keeps the JSON parse, the image decode and the async hop out of the
        // page turn — that round trip is what the reader sees as a flash.
        val reusable = appliedAdvancedTitleCompositions[slot.ordinal]
            ?.takeIf { appliedAdvancedTitleKeys[slot.ordinal] == nextKey }
            ?.let { composition ->
                appliedAdvancedTitleRenderConfigs[slot.ordinal]
                    ?.let { CommittedAdvancedTitle(composition, it) }
            }
            ?: committedAdvancedTitles[nextKey]
        if (reusable != null) {
            val token = advancedTitleRequestGate.begin(slot, nextKey, sourceId)
            lottieView.tag = nextKey
            commitOrDeferAdvancedTitleComposition(token, reusable.composition, reusable.renderConfig)
            return nextKey
        }

        val token = advancedTitleRequestGate.begin(slot, nextKey, sourceId)
        lottieView.animate().cancel()
        lottieView.removeAllLottieOnCompositionLoadedListener()
        lottieView.setFailureListener(null)
        lottieView.tag = nextKey
        lottieView.alpha = 1f
        lottieView.visibility = GONE

        fun showFailureFallback() {
            if (!advancedTitleRequestGate.fail(token)) return
            cancelAdvancedTitleImagePreparation(slot)
            clearPendingAdvancedTitleComposition(slot)
            lottieView.cancelAnimation()
            lottieView.visibility = GONE
            // The fallback has followed any intervening scroll; never restore captured Y.
            fallbackView.visibility = VISIBLE
        }

        fun applyLoaded(composition: com.airbnb.lottie.LottieComposition) {
            cancelAdvancedTitleImagePreparation(slot)
            lateinit var prepareJob: Coroutine<Boolean>
            prepareJob = Coroutine.async(start = CoroutineStart.LAZY) {
                renderConfig.imageAssetResolver.prepareCompositionImages(composition)
            }.onSuccess { ready ->
                if (advancedTitleImagePrepareJobs[slot.ordinal] !== prepareJob) return@onSuccess
                advancedTitleImagePrepareJobs[slot.ordinal] = null
                if (!advancedTitleRequestGate.accepts(token)) return@onSuccess
                // The composition parsed, so it stays. Undecodable images are skipped by
                // Lottie; never drop a valid advanced title back to the plain-text one.
                if (!ready) {
                    AppLog.put("PageView advanced title images unavailable, rendering without them: $slot")
                }
                commitOrDeferAdvancedTitleComposition(token, composition, renderConfig)
            }.onError { error ->
                if (advancedTitleImagePrepareJobs[slot.ordinal] !== prepareJob) return@onError
                advancedTitleImagePrepareJobs[slot.ordinal] = null
                AppLog.put("PageView advanced title image preparation failed: $slot", error)
                // Images are best-effort; the parsed composition is still valid.
                if (advancedTitleRequestGate.accepts(token)) {
                    commitOrDeferAdvancedTitleComposition(token, composition, renderConfig)
                }
            }
            advancedTitleImagePrepareJobs[slot.ordinal] = prepareJob
            prepareJob.start()
        }

        if (resolvedJson != null) {
            LottieCompositionCache.getInstance().get(nextKey)?.let { composition ->
                applyLoaded(composition)
                return nextKey
            }
            LottieCompositionFactory.fromJsonString(resolvedJson, nextKey)
                .addListener { composition -> composition?.let(::applyLoaded) }
                .addFailureListener { showFailureFallback() }
        } else {
            LottieCompositionFactory.fromRawRes(context, R.raw.advanced_title_lottie)
                .addListener { composition -> composition?.let(::applyLoaded) }
                .addFailureListener { showFailureFallback() }
        }
        return nextKey
    }

    private fun commitOrDeferAdvancedTitleComposition(
        token: AdvancedTitleRequestToken,
        composition: com.airbnb.lottie.LottieComposition,
        renderConfig: AdvancedTitleRenderConfig
    ) {
        if (!advancedTitleRequestGate.accepts(token)) return
        val readView = parent as? ReadView
        if (!isScroll && readView?.isHorizontalPageTurnActive == true) {
            pendingAdvancedTitleCompositions[token.slot.ordinal] =
                PendingAdvancedTitleComposition(token, composition, renderConfig)
            return
        }
        commitAdvancedTitleComposition(
            PendingAdvancedTitleComposition(token, composition, renderConfig)
        )
    }

    private fun commitAdvancedTitleComposition(pending: PendingAdvancedTitleComposition) {
        val token = pending.token
        if (!advancedTitleRequestGate.accepts(token)) return
        val (lottieView, fallbackView) = titleViews(token.slot)
        if (lottieView.tag != token.compositionKey) return
        clearPendingAdvancedTitleComposition(token.slot)
        runCatching {
            // Hardware/automatic rendering. Software rendering makes Lottie allocate a
            // view-sized backing bitmap per animation view and rasterize it on the main
            // thread; across three PageViews times title and tip slots that reproduced the
            // 2026-07-23 regression (heap exhausted, main thread hung, reader killed with a
            // black screen and no crash dialog) which the 2026-07-24 repair fixed by
            // removing software rendering.
            lottieView.renderMode = RenderMode.AUTOMATIC
            lottieView.setMaintainOriginalImageBounds(pending.renderConfig.maintainOriginalImageBounds)
            lottieView.setImageAssetDelegate(pending.renderConfig.imageAssetResolver)
            lottieView.setCacheComposition(pending.renderConfig.cacheComposition)
            lottieView.setComposition(pending.composition)
            appliedAdvancedTitleKeys[token.slot.ordinal] = token.compositionKey
            appliedAdvancedTitleCompositions[token.slot.ordinal] = pending.composition
            appliedAdvancedTitleRenderConfigs[token.slot.ordinal] = pending.renderConfig
            // Outlives the slot: the next page overwrites the entries above, and without a
            // copy here a page the reader returns to would have to be composed again.
            committedAdvancedTitles[token.compositionKey] =
                CommittedAdvancedTitle(pending.composition, pending.renderConfig)
            lottieView.progress = 0f
            lottieView.alpha = 1f
            lottieView.visibility = VISIBLE
            if (isMainView && !isScroll && !advancedTitleAnimationsFrozenForPageTurn) {
                lottieView.playAnimation()
            } else {
                lottieView.pauseAnimation()
                if (isMainView && !isScroll && advancedTitleAnimationsFrozenForPageTurn) {
                    advancedTitleAnimationsToResume[token.slot.ordinal] = true
                }
            }
            if (!advancedTitleRequestGate.markReady(token)) {
                lottieView.cancelAnimation()
                lottieView.visibility = GONE
                clearAppliedAdvancedTitle(token.slot)
                return@runCatching
            }
            // Composition success is not proof that a vendor renderer produced a frame. Keep
            // the correct-page fallback until this exact request participates in pre-draw.
            lottieView.doOnPreDraw {
                if (lottieView.tag == token.compositionKey &&
                    lottieView.visibility == VISIBLE &&
                    lottieView.composition != null &&
                    advancedTitleRequestGate.markPresented(token)
                ) {
                    fallbackView.visibility = GONE
                    markSnapDirty()
                }
            }
            lottieView.invalidate()
        }.onFailure { error ->
            AppLog.put("PageView advanced title composition failed: ${token.slot}", error)
            clearAppliedAdvancedTitle(token.slot)
            if (advancedTitleRequestGate.fail(token)) {
                lottieView.cancelAnimation()
                lottieView.visibility = GONE
                fallbackView.visibility = VISIBLE
            }
        }
    }

    internal fun flushPendingAdvancedTitleCompositions() {
        val readView = parent as? ReadView
        if (!isScroll && readView?.isHorizontalPageTurnActive == true) return
        pendingAdvancedTitleCompositions.filterNotNull().forEach { pending ->
            commitAdvancedTitleComposition(pending)
        }
        pendingAdvancedTipCompositions.filterNotNull().forEach { pending ->
            commitAdvancedTipComposition(pending)
        }
        resumeAdvancedTitleAnimationsAfterPageTurn()
    }

    private fun tipView(slot: AdvancedTipSlot): LottieAnimationView {
        return when (slot) {
            AdvancedTipSlot.HEADER -> binding.advancedHeaderLottie
            AdvancedTipSlot.FOOTER -> binding.advancedFooterLottie
        }
    }

    private fun advancedTipKey(slot: AdvancedTipSlot): String? {
        return when (slot) {
            AdvancedTipSlot.HEADER -> advancedHeaderLottieKey
            AdvancedTipSlot.FOOTER -> advancedFooterLottieKey
        }
    }

    private fun setAdvancedTipKey(slot: AdvancedTipSlot, key: String?) {
        when (slot) {
            AdvancedTipSlot.HEADER -> advancedHeaderLottieKey = key
            AdvancedTipSlot.FOOTER -> advancedFooterLottieKey = key
        }
    }

    private fun advancedTipEnabled(slot: AdvancedTipSlot): Boolean {
        if (isEpubBook()) return false
        return when (slot) {
            AdvancedTipSlot.HEADER -> ReadTipConfig.isHeaderAdvanced()
            AdvancedTipSlot.FOOTER -> ReadTipConfig.isFooterAdvanced()
        }
    }

    private fun clearAdvancedTipSlot(slot: AdvancedTipSlot) {
        val lottieView = tipView(slot)
        val pixelsChanged = lottieView.visibility != GONE
        pendingAdvancedTipCompositions[slot.ordinal] = null
        currentAdvancedTipRequests[slot.ordinal] = null
        cancelAdvancedTipPreparation(slot)
        advancedTipAnimationsToResume[slot.ordinal] = false
        clearAdvancedTitleLoadingState(lottieView)
        lottieView.cancelAnimation()
        lottieView.setTextDelegate(null)
        lottieView.setImageAssetDelegate(null)
        lottieView.visibility = GONE
        setAdvancedTipKey(slot, null)
        when (slot) {
            AdvancedTipSlot.HEADER -> headerTipTextDelegate = null
            AdvancedTipSlot.FOOTER -> footerTipTextDelegate = null
        }
        if (advancedTipEnabled(slot)) {
            setClassicTipFallbackVisible(slot, true)
        }
        if (pixelsChanged) markSnapDirty()
    }

    private fun setClassicTipFallbackVisible(slot: AdvancedTipSlot, visible: Boolean) = binding.run {
        when (slot) {
            AdvancedTipSlot.HEADER -> {
                tvHeaderLeft.isGone = !visible || ReadTipConfig.tipHeaderLeft == ReadTipConfig.none
                tvHeaderMiddle.isGone = !visible || ReadTipConfig.tipHeaderMiddle == ReadTipConfig.none
                tvHeaderRight.isGone = !visible || ReadTipConfig.tipHeaderRight == ReadTipConfig.none
            }
            AdvancedTipSlot.FOOTER -> {
                tvFooterLeft.isInvisible = !visible || ReadTipConfig.tipFooterLeft == ReadTipConfig.none
                tvFooterMiddle.isGone = !visible || ReadTipConfig.tipFooterMiddle == ReadTipConfig.none
                tvFooterRight.isGone = !visible || ReadTipConfig.tipFooterRight == ReadTipConfig.none
            }
        }
    }

    private fun applyAdvancedTipChromeVisibility() = binding.run {
        val headerAdvanced = !isEpubBook() && ReadTipConfig.isHeaderAdvanced()
        val footerAdvanced = !isEpubBook() && ReadTipConfig.isFooterAdvanced()
        if (headerAdvanced) {
            val loaded = advancedHeaderLottieKey == AdvancedTipConfig.compositionCacheKey(
                AdvancedTipSlot.HEADER
            ) && advancedHeaderLottie.composition != null && advancedHeaderLottie.visibility == VISIBLE
            setClassicTipFallbackVisible(AdvancedTipSlot.HEADER, !loaded)
        }
        if (footerAdvanced) {
            val loaded = advancedFooterLottieKey == AdvancedTipConfig.compositionCacheKey(
                AdvancedTipSlot.FOOTER
            ) && advancedFooterLottie.composition != null && advancedFooterLottie.visibility == VISIBLE
            setClassicTipFallbackVisible(AdvancedTipSlot.FOOTER, !loaded)
        }
        if (!headerAdvanced) {
            clearAdvancedTipSlot(AdvancedTipSlot.HEADER)
        }
        if (!footerAdvanced) {
            clearAdvancedTipSlot(AdvancedTipSlot.FOOTER)
        }
    }

    private fun isEpubBook(): Boolean = ReadBook.book?.isEpub == true

    /**
     * Parse and decode the advanced header/footer packages once, off the UI thread,
     * before the first page turn needs them. Without this the first flip pays for
     * JSON parsing, derived-resource extraction and image decoding all at once.
     */
    internal fun prewarmAdvancedTipCompositions() {
        if (isEpubBook()) return
        AdvancedTipSlot.entries.forEach { slot ->
            if (!advancedTipEnabled(slot)) return@forEach
            val key = AdvancedTipConfig.compositionCacheKey(slot)
            if (LottieCompositionCache.getInstance().get(key) != null) return@forEach
            if (advancedTipPrewarmKeys[slot.ordinal] == key) return@forEach
            advancedTipPrewarmKeys[slot.ordinal] = key
            val lottieView = tipView(slot)
            val requestedWidth = lottieView.width.takeIf { it > 0 }
                ?: binding.contentTextView.width.takeIf { it > 0 }
            val requestedHeight = lottieView.height.takeIf { it > 0 }
            Coroutine.async {
                val document = AdvancedTipConfig.rawDocument(slot) ?: return@async
                val composition = LottieCompositionCache.getInstance().get(key)
                    ?: LottieCompositionFactory.fromJsonStringSync(document.json, key).value
                    ?: return@async
                val compositionWidth = composition.bounds.width().coerceAtLeast(1)
                val compositionHeight = composition.bounds.height().coerceAtLeast(1)
                LottieImageAssetResolver(
                    viewWidth = requestedWidth ?: compositionWidth,
                    viewHeight = requestedHeight ?: compositionHeight,
                    compositionWidth = compositionWidth,
                    compositionHeight = compositionHeight,
                    fallbackAssets = document.fallbackAssets
                ).prepareCompositionImages(composition)
            }.onError { error ->
                advancedTipPrewarmKeys[slot.ordinal] = null
                AppLog.put("PageView advanced tip prewarm failed: $slot", error)
            }
        }
    }

    private fun upAdvancedTipLotties(textPage: TextPage?, contentGeneration: Long) {
        AdvancedTipSlot.entries.forEach { slot ->
            runCatching {
                upAdvancedTipLottie(
                    slot = slot,
                    textPage = textPage,
                    contentGeneration = contentGeneration
                )
            }.onFailure { error ->
                AppLog.put("PageView advanced tip bind failed: $slot", error)
                clearAdvancedTipSlot(slot)
            }
        }
    }

    private fun upAdvancedTipLottie(
        slot: AdvancedTipSlot,
        textPage: TextPage?,
        contentGeneration: Long
    ) {
        if (contentGeneration != advancedTitleRequestGate.contentGeneration) return
        if (!advancedTipEnabled(slot)) {
            clearAdvancedTipSlot(slot)
            return
        }
        val lottieView = tipView(slot)
        val page = textPage ?: currentTextPage
        val context = lastTipContext.copy(
            book = lastTipContext.book.ifBlank { ReadBook.book?.name.orEmpty() },
            title = page?.title?.takeIf { it.isNotBlank() } ?: lastTipContext.title,
            time = AdvancedTipConfig.currentTimeText(),
            battery = battery.toString(),
            author = ReadBook.book?.author.orEmpty()
        )
        lastTipContext = context
        val vars = AdvancedTipConfig.variables(context)
        // Key by package identity only. Page/title/time updates use TextDelegate (no re-parse).
        val nextKey = AdvancedTipConfig.compositionCacheKey(slot)
        lottieView.scaleType = ImageView.ScaleType.FIT_CENTER
        lottieView.repeatCount = LottieDrawable.INFINITE
        lottieView.setFontAssetDelegate(defaultFontAssetDelegate)

        fun ensureDelegate() {
            val existing = when (slot) {
                AdvancedTipSlot.HEADER -> headerTipTextDelegate
                AdvancedTipSlot.FOOTER -> footerTipTextDelegate
            }
            if (existing != null) {
                existing.variables = vars
                lottieView.invalidate()
                return
            }
            val created = TipFieldTextDelegate(lottieView).also { it.variables = vars }
            lottieView.setTextDelegate(created)
            when (slot) {
                AdvancedTipSlot.HEADER -> headerTipTextDelegate = created
                AdvancedTipSlot.FOOTER -> footerTipTextDelegate = created
            }
        }

        if (advancedTipKey(slot) == nextKey && lottieView.composition != null) {
            pendingAdvancedTipCompositions[slot.ordinal] = null
            currentAdvancedTipRequests[slot.ordinal] = null
            lottieView.tag = nextKey
            ensureDelegate()
            lottieView.visibility = VISIBLE
            setClassicTipFallbackVisible(slot, false)
            if (isMainView && !advancedTitleAnimationsFrozenForPageTurn) {
                if (!lottieView.isAnimating) lottieView.playAnimation()
            } else {
                lottieView.pauseAnimation()
                if (isMainView && advancedTitleAnimationsFrozenForPageTurn) {
                    advancedTipAnimationsToResume[slot.ordinal] = true
                }
            }
            return
        }

        val existingRequest = currentAdvancedTipRequests[slot.ordinal]
        if (existingRequest?.contentGeneration == contentGeneration &&
            existingRequest.compositionKey == nextKey &&
            lottieView.tag == nextKey
        ) {
            return
        }
        val token = AdvancedTipRequestToken(
            contentGeneration = contentGeneration,
            requestGeneration = ++advancedTipRequestGeneration,
            slot = slot,
            compositionKey = nextKey
        )
        currentAdvancedTipRequests[slot.ordinal] = token
        pendingAdvancedTipCompositions[slot.ordinal] = null
        cancelAdvancedTipPreparation(slot)

        lottieView.animate().cancel()
        lottieView.removeAllLottieOnCompositionLoadedListener()
        lottieView.setFailureListener(null)
        lottieView.tag = nextKey
        lottieView.alpha = 1f
        if (!(lottieView.composition != null && lottieView.visibility == VISIBLE)) {
            lottieView.visibility = INVISIBLE
        }

        fun showFailure() {
            if (!acceptsAdvancedTipRequest(token)) return
            clearAdvancedTipSlot(slot)
        }

        val requestedWidth = lottieView.width.takeIf { it > 0 }
            ?: binding.contentTextView.width.takeIf { it > 0 }
        val requestedHeight = lottieView.height.takeIf { it > 0 }
        lateinit var prepareJob: Coroutine<PreparedAdvancedTipComposition?>
        prepareJob = Coroutine.async(start = CoroutineStart.LAZY) {
            val document = AdvancedTipConfig.rawDocument(slot) ?: return@async null
            val composition = LottieCompositionCache.getInstance().get(nextKey)
                ?: LottieCompositionFactory.fromJsonStringSync(document.json, nextKey).value
                ?: return@async null
            val compositionWidth = composition.bounds.width().coerceAtLeast(1)
            val compositionHeight = composition.bounds.height().coerceAtLeast(1)
            val resolver = LottieImageAssetResolver(
                viewWidth = requestedWidth ?: compositionWidth,
                viewHeight = requestedHeight ?: compositionHeight,
                compositionWidth = compositionWidth,
                compositionHeight = compositionHeight,
                fallbackAssets = document.fallbackAssets
            )
            // Images are best-effort: a composition that parsed still renders.
            resolver.prepareCompositionImages(composition)
            PreparedAdvancedTipComposition(composition, resolver)
        }.onSuccess { prepared ->
            if (advancedTipPrepareJobs[slot.ordinal] !== prepareJob) return@onSuccess
            advancedTipPrepareJobs[slot.ordinal] = null
            if (!acceptsAdvancedTipRequest(token)) return@onSuccess
            if (prepared == null) {
                showFailure()
            } else {
                commitOrDeferAdvancedTipComposition(token, prepared, vars)
            }
        }.onError { error ->
            if (advancedTipPrepareJobs[slot.ordinal] !== prepareJob) return@onError
            advancedTipPrepareJobs[slot.ordinal] = null
            AppLog.put("PageView advanced tip preparation failed: $slot", error)
            showFailure()
        }
        advancedTipPrepareJobs[slot.ordinal] = prepareJob
        prepareJob.start()
    }

    private fun acceptsAdvancedTipRequest(token: AdvancedTipRequestToken): Boolean {
        return token.contentGeneration == advancedTitleRequestGate.contentGeneration &&
            currentAdvancedTipRequests[token.slot.ordinal] == token &&
            tipView(token.slot).tag == token.compositionKey &&
            advancedTipEnabled(token.slot) &&
            AdvancedTipConfig.compositionCacheKey(token.slot) == token.compositionKey
    }

    private fun commitOrDeferAdvancedTipComposition(
        token: AdvancedTipRequestToken,
        prepared: PreparedAdvancedTipComposition,
        variables: Map<String, String>
    ) {
        if (!acceptsAdvancedTipRequest(token)) return
        if ((parent as? ReadView)?.isHorizontalPageTurnActive == true) {
            pendingAdvancedTipCompositions[token.slot.ordinal] =
                PendingAdvancedTipComposition(
                    token,
                    prepared.composition,
                    variables,
                    prepared.imageAssetResolver
                )
            return
        }
        commitAdvancedTipComposition(
            PendingAdvancedTipComposition(
                token,
                prepared.composition,
                variables,
                prepared.imageAssetResolver
            )
        )
    }

    private fun commitAdvancedTipComposition(pending: PendingAdvancedTipComposition) {
        val token = pending.token
        if (!acceptsAdvancedTipRequest(token)) return
        pendingAdvancedTipCompositions[token.slot.ordinal] = null
        val lottieView = tipView(token.slot)
        runCatching {
            lottieView.renderMode = RenderMode.AUTOMATIC
            lottieView.setMaintainOriginalImageBounds(true)
            lottieView.setImageAssetDelegate(pending.imageAssetResolver)
            lottieView.setComposition(pending.composition)
            val delegate = TipFieldTextDelegate(lottieView).also {
                it.variables = pending.variables
            }
            lottieView.setTextDelegate(delegate)
            when (token.slot) {
                AdvancedTipSlot.HEADER -> headerTipTextDelegate = delegate
                AdvancedTipSlot.FOOTER -> footerTipTextDelegate = delegate
            }
            lottieView.alpha = 1f
            lottieView.visibility = VISIBLE
            if (isMainView && !advancedTitleAnimationsFrozenForPageTurn) {
                if (!lottieView.isAnimating) lottieView.playAnimation()
            } else {
                lottieView.pauseAnimation()
                if (isMainView && advancedTitleAnimationsFrozenForPageTurn) {
                    advancedTipAnimationsToResume[token.slot.ordinal] = true
                }
            }
            setAdvancedTipKey(token.slot, token.compositionKey)
            lottieView.doOnPreDraw {
                if (acceptsAdvancedTipRequest(token) &&
                    advancedTipKey(token.slot) == token.compositionKey &&
                    lottieView.visibility == VISIBLE && lottieView.composition != null
                ) {
                    setClassicTipFallbackVisible(token.slot, false)
                    markSnapDirty()
                }
            }
            lottieView.invalidate()
        }.onFailure { error ->
            AppLog.put("PageView advanced tip composition failed: ${token.slot}", error)
            if (acceptsAdvancedTipRequest(token)) clearAdvancedTipSlot(token.slot)
        }
    }


    private class TipFieldTextDelegate(
        animationView: LottieAnimationView
    ) : TextDelegate(animationView) {
        @Volatile
        var variables: Map<String, String> = emptyMap()

        init {
            setCacheText(false)
        }

        override fun getText(input: String): String {
            return AdvancedTipConfig.substituteText(input, variables)
        }
    }


    private fun clearAdvancedTitleLoadingState(view: LottieAnimationView) {
        view.animate().cancel()
        view.removeAllLottieOnCompositionLoadedListener()
        view.setFailureListener(null)
        view.tag = null
        view.alpha = 1f
    }

    private fun advancedTitleTextSizeSp(): Float {
        return with(ReadBookConfig) {
            (textSize + titleSize * ADVANCED_TITLE_SIZE_FACTOR).coerceAtLeast(1f)
        }
    }

    private fun advancedTitleScale(): Float {
        return with(ReadBookConfig) {
            (advancedTitleTextSizeSp() / textSize.coerceAtLeast(1)).coerceIn(0.6f, 2.5f)
        }
    }
    private fun advancedTitleTextLayerScale(block: TextPage.EpubEmbeddedBlock, pageWidth: Float): Float {
        val contentWidth = pageWidth.takeIf { it > 0f } ?: block.width
        if (contentWidth <= 0f) return 1f
        val actualWidthRatio = block.width / contentWidth
        if (actualWidthRatio < 0.98f) return 1f
        val requestedWidthRatio = ADVANCED_TITLE_WIDTH_FACTOR * advancedTitleScale() *
            (AdvancedTitleConfig.heightFactor / AdvancedTitleConfig.DEFAULT_HEIGHT_FACTOR.toFloat())
        return (requestedWidthRatio / actualWidthRatio).coerceIn(1f, 2.5f)
    }

    private fun applyLottieTextFallbackStyle(rawJson: String, textScale: Float): StyledLottieJson {
        if (!AdvancedTitleLottieKeys.canApplyFallbackStyle(rawJson)) {
            return StyledLottieJson(rawJson, null)
        }
        val fallbackColor = ReadBookConfig.textColor
        val fallbackHex = String.format("#%06X", 0xFFFFFF and fallbackColor)
        val fallbackFont = "legado_default_font"
        val normalizedTextScale = textScale.coerceIn(1f, 2.5f)
        val cacheKey = AdvancedTitleLottieKeys.styledJson(
            rawJson = rawJson,
            fallbackColor = fallbackColor,
            textScaleBits = normalizedTextScale.toBits()
        )
        styledLottieJsonCache[cacheKey]?.let { return it }
        val styled = runCatching {
            val root = JSONObject(rawJson)
            val compositionSize = root.optInt("w").takeIf { it > 0 }?.let { width ->
                root.optInt("h").takeIf { it > 0 }?.let { height ->
                    LottieDecodeSize(width, height)
                }
            }
            normalizeFullWidthImageLayers(root)
            val layers = root.optJSONArray("layers")
            if (layers != null) {
                for (i in 0 until layers.length()) {
                    val layer = layers.optJSONObject(i) ?: continue
                    if (layer.optInt("ty") != 5) continue
                    val text = layer.optJSONObject("t") ?: continue
                    val d = text.optJSONObject("d") ?: continue
                    val kArr = d.optJSONArray("k") ?: continue
                    for (j in 0 until kArr.length()) {
                        val keyFrame = kArr.optJSONObject(j) ?: continue
                        val style = keyFrame.optJSONObject("s") ?: continue
                        if (!style.has("f") || style.optString("f").isBlank()) {
                            style.put("f", fallbackFont)
                        }
                        if (style.optString("f") == fallbackFont ||
                            !style.has("fc") || style.optJSONArray("fc") == null
                        ) {
                            style.put("fc", parseColorArray(fallbackHex))
                        }
                        scaleLottieTextStyle(style, normalizedTextScale)
                    }
                }
                val fonts = root.optJSONObject("fonts")
                    ?: JSONObject().also { root.put("fonts", it) }
                val list = fonts.optJSONArray("list")
                    ?: org.json.JSONArray().also { fonts.put("list", it) }
                var hasFont = false
                for (i in 0 until list.length()) {
                    val item = list.optJSONObject(i) ?: continue
                    if (item.optString("fName") == fallbackFont) {
                        hasFont = true
                        break
                    }
                }
                if (!hasFont) {
                    list.put(JSONObject().apply {
                        put("fName", fallbackFont)
                        put("fFamily", fallbackFont)
                        put("fStyle", "Regular")
                        put("ascent", 75)
                    })
                }
            }
            StyledLottieJson(
                json = if (layers == null) rawJson else root.toString(),
                compositionSize = compositionSize
            )
        }.getOrElse {
            StyledLottieJson(rawJson, null)
        }
        styledLottieJsonCache.put(cacheKey, styled)
        return styled
    }

    private fun normalizeFullWidthImageLayers(root: JSONObject) {
        val rootWidth = root.optDouble("w", 0.0)
        if (rootWidth <= 0.0) return
        val assets = root.optJSONArray("assets") ?: return
        val assetWidthMap = mutableMapOf<String, Double>()
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val id = asset.optString("id").takeIf { it.isNotBlank() } ?: continue
            assetWidthMap[id] = asset.optDouble("w", 0.0)
        }
        val layers = root.optJSONArray("layers") ?: return
        for (i in 0 until layers.length()) {
            val layer = layers.optJSONObject(i) ?: continue
            if (layer.optInt("ty") != 2) continue
            val assetWidth = assetWidthMap[layer.optString("refId")] ?: continue
            if (kotlin.math.abs(assetWidth - rootWidth) > 1.0) continue
            val scaleArray = layer.optJSONObject("ks")
                ?.optJSONObject("s")
                ?.optJSONArray("k") ?: continue
            val scaleX = scaleArray.optDouble(0, 100.0)
            val scaleY = scaleArray.optDouble(1, scaleX)
            if (scaleX <= 0.0 || scaleX >= 99.9) continue
            val fillScale = (100.0 / scaleX).coerceIn(1.0, 2.0)
            scaleArray.put(0, scaleX * fillScale)
            scaleArray.put(1, scaleY * fillScale)
        }
    }

    private fun scaleLottieTextStyle(style: JSONObject, scale: Float) {
        if (scale <= 1.001f) return
        val fontSize = style.optDouble("s", 0.0)
        if (fontSize > 0.0) {
            style.put("s", fontSize * scale)
        }
        val lineHeight = style.optDouble("lh", 0.0)
        if (lineHeight > 0.0) {
            style.put("lh", lineHeight * scale)
        }
        val size = style.optJSONArray("sz")
        val oldHeight = size?.optDouble(1, 0.0) ?: 0.0
        if (size != null && oldHeight > 0.0) {
            val newHeight = oldHeight * scale
            size.put(1, newHeight)
            val position = style.optJSONArray("ps")
            if (position != null && position.length() > 1) {
                val oldY = position.optDouble(1, 0.0)
                if (kotlin.math.abs(oldY + oldHeight / 2.0) < 1.0) {
                    position.put(1, -newHeight / 2.0)
                }
            }
        }
    }

    private fun parseColorArray(hex: String): org.json.JSONArray {
        val color = Color.parseColor(hex)
        return org.json.JSONArray().apply {
            put(Color.red(color) / 255.0)
            put(Color.green(color) / 255.0)
            put(Color.blue(color) / 255.0)
        }
    }

    private data class ResolvedLottieAssetSource(
        val identity: String,
        val file: File?,
        val fallbackDataUri: String?
    )

    private data class LottieAssetDecodeRequest(
        val asset: LottieImageAsset,
        val source: ResolvedLottieAssetSource,
        val decodeSize: LottieDecodeSize,
        val cacheKey: LottieImageCacheKey
    )

    private inner class LottieImageAssetResolver(
        private val viewWidth: Int,
        private val viewHeight: Int,
        private val compositionWidth: Int,
        private val compositionHeight: Int,
        private val fallbackAssets: Map<String, String>
    ) : ImageAssetDelegate {

        override fun fetchBitmap(asset: LottieImageAsset): Bitmap? {
            asset.bitmap?.takeUnless { it.isRecycled }?.let { return it }
            val request = decodeRequest(asset) ?: return null
            return bitmapFor(request)
        }

        /**
         * Decode this composition's images off the UI thread. Individual assets that
         * cannot be resolved or decoded are left to [fetchBitmap]; Lottie simply skips
         * them. Only a composition that declares images yet yields none at all is
         * reported as not ready, so a partially decodable package keeps rendering
         * instead of dropping to the plain-text title.
         */
        fun prepareCompositionImages(composition: com.airbnb.lottie.LottieComposition): Boolean {
            return synchronized(composition) {
                val assets = composition.images.values
                if (assets.isEmpty()) return@synchronized true
                val requests = assets.mapNotNull { asset -> decodeRequest(asset) }
                if (requests.isEmpty()) return@synchronized false
                val uniqueSizes = LinkedHashMap<LottieImageCacheKey, LottieDecodeSize>()
                requests.forEach { request ->
                    uniqueSizes.putIfAbsent(request.cacheKey, request.decodeSize)
                }
                // Shrink an oversized package rather than refusing to render it.
                val scale = LottieImageMemoryPolicy.compositionBudgetScale(uniqueSizes.values)
                val decoded = LinkedHashMap<LottieImageCacheKey, Bitmap>(uniqueSizes.size)
                val assignments = ArrayList<Pair<LottieImageAsset, Bitmap>>(requests.size)
                requests.forEach { request ->
                    val effective = if (scale >= 1f) {
                        request
                    } else {
                        val size = LottieImageMemoryPolicy.scaled(request.decodeSize, scale)
                        request.copy(
                            decodeSize = size,
                            cacheKey = request.cacheKey.copy(
                                width = size.width,
                                height = size.height
                            )
                        )
                    }
                    val bitmap = effective.asset.bitmap?.takeUnless { it.isRecycled }
                        ?: decoded[effective.cacheKey]
                        ?: bitmapFor(effective)
                        ?: return@forEach
                    decoded[effective.cacheKey] = bitmap
                    assignments.add(effective.asset to bitmap)
                }
                if (assignments.isEmpty()) return@synchronized false
                assignments.forEach { (asset, bitmap) -> asset.bitmap = bitmap }
                true
            }
        }

        private fun decodeRequest(asset: LottieImageAsset): LottieAssetDecodeRequest? {
            val source = resolveLottieAssetSource(asset, fallbackAssets) ?: return null
            val decodeSize = LottieImageMemoryPolicy.decodeSize(
                assetWidth = asset.width.takeIf { it > 0 }
                    ?: compositionWidth.coerceAtLeast(viewWidth),
                assetHeight = asset.height.takeIf { it > 0 }
                    ?: compositionHeight.coerceAtLeast(viewHeight),
                viewWidth = viewWidth,
                viewHeight = viewHeight,
                compositionWidth = compositionWidth,
                compositionHeight = compositionHeight
            ) ?: return null
            return LottieAssetDecodeRequest(
                asset = asset,
                source = source,
                decodeSize = decodeSize,
                cacheKey = LottieImageCacheKey(
                    sourceSha256 = LottieImageMemoryPolicy.sourceSha256(source.identity),
                    width = decodeSize.width,
                    height = decodeSize.height
                )
            )
        }

        private fun bitmapFor(request: LottieAssetDecodeRequest): Bitmap? {
            LottieImageBitmapCache.get(request.cacheKey)?.let { return it }
            return loadLottieAssetBitmap(request.source, request.decodeSize)?.also { bitmap ->
                LottieImageBitmapCache.put(request.cacheKey, bitmap)
            }
        }
    }

    private fun resolveLottieAssetSource(
        asset: LottieImageAsset,
        fallbackAssets: Map<String, String>
    ): ResolvedLottieAssetSource? {
        val candidates = arrayListOf<String>()
        asset.fileName?.let(candidates::add)
        if (!asset.dirName.isNullOrBlank() && !asset.fileName.isNullOrBlank()) {
            candidates.add(asset.dirName + asset.fileName)
        }
        candidates.forEach { candidate ->
            if (candidate.startsWith("data:image", ignoreCase = true)) {
                return ResolvedLottieAssetSource(
                    identity = candidate,
                    file = null,
                    fallbackDataUri = candidate
                )
            }
            if (LottieDerivedResourceCache.isResourceAlias(candidate)) {
                val fallback = fallbackAssets[candidate]
                    ?.takeIf { it.startsWith("data:image", ignoreCase = true) }
                    ?: return@forEach
                return ResolvedLottieAssetSource(
                    identity = candidate,
                    file = LottieDerivedResourceCache.resourceFile(candidate),
                    fallbackDataUri = fallback
                )
            }
        }
        return null
    }

    private fun loadLottieAssetBitmap(
        source: ResolvedLottieAssetSource,
        decodeSize: LottieDecodeSize
    ): Bitmap? {
        source.file?.takeIf {
            it.isFile && it.length() in 1..MAX_LOTTIE_DERIVED_RESOURCE_BYTES
        }?.let { file ->
            runCatching { decodeBitmapFileByType(file, decodeSize) }
                .getOrNull()
                ?.let { return it }
        }
        val fallback = source.fallbackDataUri ?: return null
        return runCatching {
            val bytes = fallback.decodeBase64DataUrlBytes() ?: return@runCatching null
            decodeBitmapByType(fallback, bytes, decodeSize)
        }.getOrNull()
    }

    private fun decodeBitmapFileByType(file: File, decodeSize: LottieDecodeSize): Bitmap? {
        return if (file.extension.equals("svg", ignoreCase = true)) {
            file.inputStream().buffered().use { input ->
                SvgUtils.createBitmap(input, decodeSize.width, decodeSize.height)
            }
        } else {
            decodeRasterBitmap(file, decodeSize)
        }
    }

    private fun decodeBitmapByType(
        source: String,
        bytes: ByteArray,
        decodeSize: LottieDecodeSize
    ): Bitmap? {
        val lower = source.lowercase()
        return if (lower.contains("image/svg+xml") || lower.endsWith(".svg")) {
            SvgUtils.createBitmap(ByteArrayInputStream(bytes), decodeSize.width, decodeSize.height)
        } else {
            decodeRasterBitmap(bytes, decodeSize)
        }
    }

    private fun decodeRasterBitmap(bytes: ByteArray, decodeSize: LottieDecodeSize): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val target = LottieImageMemoryPolicy.fitSourceInto(bounds.outWidth, bounds.outHeight, decodeSize)
            ?: return null
        val sampleSize = LottieImageMemoryPolicy.sampleSize(bounds.outWidth, bounds.outHeight, target)
        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        ) ?: return null
        if (decoded.width == target.width && decoded.height == target.height) return decoded
        return Bitmap.createScaledBitmap(decoded, target.width, target.height, true).also {
            if (it !== decoded) decoded.recycle()
        }
    }

    private fun decodeRasterBitmap(file: File, decodeSize: LottieDecodeSize): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val target = LottieImageMemoryPolicy.fitSourceInto(bounds.outWidth, bounds.outHeight, decodeSize)
            ?: return null
        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = LottieImageMemoryPolicy.sampleSize(
                    bounds.outWidth,
                    bounds.outHeight,
                    target
                )
            }
        ) ?: return null
        if (decoded.width == target.width && decoded.height == target.height) return decoded
        return Bitmap.createScaledBitmap(decoded, target.width, target.height, true).also {
            if (it !== decoded) decoded.recycle()
        }
    }

    private val defaultFontAssetDelegate = AdvancedTitleFontAssetDelegate {
        ChapterProvider.titlePaint.typeface ?: ChapterProvider.typeface ?: Typeface.DEFAULT
    }

    val textPage get() = binding.contentTextView.textPage

    val selectedText: String get() = binding.contentTextView.getSelectedText()

    fun hasSelection(): Boolean = binding.contentTextView.hasSelection()

    fun hasNativeSelection(): Boolean = binding.contentTextView.hasNativeSelection()

    fun getSelectedReadPosition(): ReadSelectionPosition? =
        binding.contentTextView.getSelectedReadPosition()

    val selectStartPos get() = binding.contentTextView.selectStart

    fun selectedStartPage(): TextPage? = binding.contentTextView.selectedStartPage()

    private companion object {
        const val ADVANCED_TITLE_SIZE_FACTOR = 1.25f
        const val ADVANCED_TITLE_WIDTH_FACTOR = 0.86f
        const val MAX_LOTTIE_DERIVED_RESOURCE_BYTES = 8L * 1024L * 1024L
        /**
         * Positions worth remembering a composition key for. Only pages within reach of a
         * few turns can be returned to before the key would be rebuilt anyway, and each
         * entry is two short strings.
         */
        const val MAX_TITLE_KEYS_BY_PAGE = 24
        /**
         * Compositions kept per view. Three PageViews share the Lottie parse cache, so this
         * only bounds the committed render state; a handful covers turning back and forth
         * across a title page without holding rasterized bitmaps for a whole chapter.
         */
        const val MAX_COMMITTED_TITLES = 6
        const val MAX_STYLED_LOTTIE_CACHE_CHARS = 1024 * 1024
        const val MAX_STYLED_LOTTIE_CACHE_UTF8_BYTES = 1024 * 1024
        val styledLottieJsonCache = StyledLottieJsonCache(
            maxChars = MAX_STYLED_LOTTIE_CACHE_CHARS,
            maxUtf8Bytes = MAX_STYLED_LOTTIE_CACHE_UTF8_BYTES
        )
    }
}
