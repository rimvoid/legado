package io.legado.app.ui.book.read.epub

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.core.view.doOnLayout
import androidx.core.view.doOnPreDraw
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.model.ReadBook
import io.legado.app.model.localBook.epubcore.direct.EpubDirectChapter
import io.legado.app.model.localBook.epubcore.direct.EpubDirectLayoutMode
import io.legado.app.model.localBook.epubcore.direct.EpubDirectPosition
import io.legado.app.model.localBook.epubcore.direct.EpubDirectReaderChromePolicy
import io.legado.app.model.localBook.epubcore.direct.EpubDirectReaderTypographyPolicy
import io.legado.app.model.localBook.epubcore.direct.EpubDirectResource
import io.legado.app.model.localBook.epubcore.direct.EpubDirectSession
import io.legado.app.model.localBook.epubcore.direct.TextReaderImageActionGate
import io.legado.app.model.localBook.epubcore.direct.TextReaderImageActionRequest
import io.legado.app.model.localBook.epubcore.direct.TextReaderImageClickPolicy
import io.legado.app.model.localBook.epubcore.layout.EpubCoreLayoutConfig
import io.legado.app.model.localBook.epubcore.layout.EpubReaderChromeConfig
import io.legado.app.model.localBook.epubcore.layout.EpubReaderChromeData
import io.legado.app.model.localBook.epubcore.layout.EpubReaderChromeDataPolicy
import io.legado.app.model.localBook.epubcore.template.EpubTemplateBridgePolicy
import io.legado.app.model.localBook.epubcore.template.EpubTemplateActiveClock
import io.legado.app.model.localBook.epubcore.template.EpubTemplateDocument
import io.legado.app.model.localBook.epubcore.template.EpubTemplateException
import io.legado.app.model.localBook.epubcore.template.EpubTemplateTapGate
import io.legado.app.model.localBook.epubcore.template.EpubReaderTemplate
import io.legado.app.model.localBook.epubcore.template.EpubReaderTemplateStore
import io.legado.app.model.localBook.epubcore.web.EpubWebDocumentLoadMarker
import io.legado.app.model.localBook.epubcore.web.EpubWebMainDocumentUrlMatcher
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.lang.ref.WeakReference
import java.util.LinkedHashSet
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import splitties.init.appCtx
import kotlin.math.abs
import kotlin.math.roundToInt

@SuppressLint("SetJavaScriptEnabled")
class EpubDirectWebLayer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    private val frameRenderer: Boolean = false
) : FrameLayout(context, attrs) {

    private data class RenderRecoveryRequest(
        val chapter: EpubDirectChapter,
        val config: EpubCoreLayoutConfig,
        val initialPageIndex: Int,
        val openAtEnd: Boolean,
        val progress: Float?,
        val fragmentId: String?
    )

    private data class PendingChapterTurn(
        val sourceChapterIndex: Int,
        val sourceChapterHref: String,
        val targetChapterIndex: Int,
        val sourcePageIndex: Int,
        val sourcePageCount: Int,
        val logicalDirection: Int,
        val visualDirection: Int,
        val style: EpubDirectPageAnimationPolicy.Style,
        val backgroundColor: Int,
        val sourceRtl: Boolean,
        val viewportWidth: Int,
        val viewportHeight: Int,
        val sourceLayoutSignature: String,
        val sourceReaderChromeContentRevision: Long,
        val sourceView: ReaderWebView,
        var sourceBitmap: Bitmap?,
        var targetBitmap: Bitmap?,
        val targetFrame: CachedAnimationTarget? = null,
        var targetToken: Long? = null
    )

    private data class MountedPageAnimation(
        val sequence: Long,
        val overlay: EpubDirectPageAnimationOverlay
    )

    private data class PageFrameMetadata(
        val chapterIndex: Int,
        val chapterHref: String,
        val pageIndex: Int,
        val pageCount: Int,
        val layoutSignature: String,
        val readerChromeContentRevision: Long
    )

    private data class AnimationSourceFrame(
        val overlay: EpubDirectPageAnimationOverlay,
        val metadata: PageFrameMetadata
    )

    private data class CachedAnimationTarget(
        val pipeline: EpubAdjacentPageFramePipeline,
        val target: EpubPageFrameTarget,
        val pageIndex: Int,
        val pageCount: Int
    )

    private data class LivePageAnimationTarget(
        val sequence: Long,
        val overlay: EpubDirectPageAnimationOverlay,
        val view: ReaderWebView,
        val style: EpubDirectPageAnimationPolicy.Style,
        val action: EpubDirectPageAnimationPolicy.TurnAction,
        val visualDirection: Int,
        val originalLayerType: Int,
        val originalVisibility: Int,
        val originalAlpha: Float,
        val boundToken: Long,
        val boundChapterKey: String?,
        val wasCurrent: Boolean,
        var revealed: Boolean = false,
        var hardwareLayerPrepared: Boolean = false,
        var transientStateSet: Boolean = false
    )

    private data class InteractivePageTurn(
        val request: Long,
        val token: Long,
        val sourceView: ReaderWebView,
        val sourceChapterIndex: Int,
        val sourcePageIndex: Int,
        val targetPageIndex: Int,
        val logicalDirection: Int,
        val visualDirection: Int,
        val style: EpubDirectPageAnimationPolicy.Style,
        val sequence: Long,
        val overlay: EpubDirectPageAnimationOverlay,
        val gesture: EpubDirectGesturePolicy.DragState,
        var releaseVelocityX: Float = 0f,
        val boundary: Boolean = false,
        var targetToken: Long? = null,
        var targetPageCount: Int? = null,
        var targetApplied: Boolean = false,
        var requestedProgress: Float = 0f,
        var finishRequested: Boolean? = null,
        var navigationDispatched: Boolean = false,
        var settling: Boolean = false,
        var restoring: Boolean = false,
        val framePipeline: EpubAdjacentPageFramePipeline? = null,
        val expectedFrameTarget: EpubPageFrameTarget? = null,
        val sourceLayoutRevision: Long = -1L,
        var cachedTargetFrame: CachedAnimationTarget? = null,
    )

    interface Listener {
        fun onReady(position: EpubDirectPosition) = Unit
        fun onPositionChanged(position: EpubDirectPosition) = Unit
        fun onPageBoundary(direction: Int) = Unit
        fun onTap(x: Float, y: Float) = Unit
        fun onSelectionChanged(text: String, rects: List<RectF>) = Unit
        fun onSelectionInteractionChanged(active: Boolean) = Unit
        fun onSelectionCleared() = Unit
        fun onLinkClicked(url: String) = Unit
        fun onFootnoteClicked(url: String) = Unit
        fun onImageClicked(url: String) = Unit
        fun onSourceImageAction(request: TextReaderImageActionRequest) = Unit
        fun onError(message: String, throwable: Throwable? = null) = Unit
    }

    private var listener: Listener? = null
    private var session: EpubDirectSession? = null
    private var ownsBoundSession = true
    private var chapter: EpubDirectChapter? = null
    private var config: EpubCoreLayoutConfig? = null
    private var currentWebView: ReaderWebView = createWebView()
    private var standbyWebView: ReaderWebView? = null
    private var generation = 0L
    private val sourceImageActionGate = TextReaderImageActionGate()
    private val activationGate = EpubDirectActivationGate()
    private var pendingActivationView: ReaderWebView? = null
    private var pendingActivationTarget: EpubDirectActivationTargetPolicy.Target? = null
    private var pendingActivationVisualVerified = false
    private var activationProbeRunnable: Runnable? = null
    private var activationApplyInFlight = false
    private var activationTimeoutRunnable: Runnable? = null
    private var pageIndex = 0
    private var pageCount = 1
    private var currentLayoutRevision = -1L
    private var runtimeMetricsSyncRunnable: Runnable? = null
    private var runtimeMetricsSyncTimeout: Runnable? = null
    private var runtimeMetricsSyncInFlight = false
    private var runtimeMetricsSyncSequence = 0L
    private var hostPaused = false
    private val templateActiveClock = EpubTemplateActiveClock(SystemClock::uptimeMillis)
    private val templateResumeCallbacks = LinkedHashMap<Runnable, View>()
    private var animationRenderStateChanged = false
    private var pendingPageIndex = 0
    private var pendingLastPage = false
    private var pendingProgress: Float? = null
    private var pendingFragmentId: String? = null
    private var documentReady = false
    private var selectionActive = false
    private var readAloudCueSequence = 0L
    private var pendingReadAloudCue: PendingReadAloudCue? = null
    private var readAloudCueDrainRunnable: Runnable? = null
    private var annotationVisible = false
    private var destroyed = false
    private var lastEmbeddedInteractionAt = 0L
    private var lastBoundaryAt = 0L
    private var ignoreScrollUntil = 0L
    private var horizontalScrollCorrectionPosted = false
    private var preloadSequence = 0L
    private data class ScheduledPreload(
        val chapterIndex: Int,
        val config: EpubCoreLayoutConfig,
        val action: Runnable
    )
    private val preloadRunnables = LinkedHashMap<String, ScheduledPreload>()
    private var preloadChapterOrder = emptyList<Int>()
    private var preloadGestureActive = false
    private var performanceBudget = resolvePerformanceBudget(context)
    val preloadCapacity: Int
        get() = performanceBudget.preloadedWebViews
    private val preloadedWebViews = EpubDirectPreloadCache<ReaderWebView>(
        preloadCapacity,
        ::destroyWebView
    )
    // A preloading WebView owns native resources before it can safely enter the ready cache.
    // Keep it addressable during that interval so it can be promoted or discarded deterministically.
    private val loadingPreloadedWebViews = LinkedHashMap<String, ReaderWebView>()
    private var renderRecoveryRunnable: Runnable? = null
    private var renderRecoveryRequest: RenderRecoveryRequest? = null
    private var renderRecoveryAttempts = 0
    private var lastRenderRecoveryAt = 0L
    private val styleReloadExecutor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(1),
        { runnable -> Thread(runnable, "epub-direct-style").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardOldestPolicy()
    )
    private var styleReloadFuture: Future<*>? = null
    private var styleReloadRunnable: Runnable? = null
    private var styleReloadSequence = 0L
    private var pageApplySequence = 0L
    private var pageAnimationSequence = 0L
    private var pageFinalFrameSequence: Long? = null
    private var pageAnimationOverlay: EpubDirectPageAnimationOverlay? = null
    private var pageAnimationSourceFrame: AnimationSourceFrame? = null
    private var pageAnimator: ValueAnimator? = null
    private var livePageAnimationTarget: LivePageAnimationTarget? = null
    private var deferredLiveTargetLayerRelease: Runnable? = null
    private var interactivePageTurn: InteractivePageTurn? = null
    private var lastPageGestureYFraction = 0.9f
    private var interactiveRestoreTimeout: Runnable? = null
    private var pageAnimationStartTimeout: Runnable? = null
    private var pageHandoffRequest: Long? = null
    private var pageHandoffRestoringRequest: Long? = null
    private var pageHandoffTimeoutRunnable: Runnable? = null
    private var pageHandoffRestoreTimeoutRunnable: Runnable? = null
    private var recoverySnapshotOverlay: EpubDirectRecoverySnapshotOverlay? = null
    private var pageTransitionSnapshotOverlay: EpubDirectRecoverySnapshotOverlay? = null
    private var foregroundRevealRunnable: Runnable? = null
    private var foregroundRevealView: ReaderWebView? = null
    private var pendingChapterTurn: PendingChapterTurn? = null
    private var chapterTurnTimeoutRunnable: Runnable? = null
    private var snapshotSceneRevision = 0L
    private val snapshotPixelBudget = snapshotPixelBudget(context)
    private val snapshotCallbackHandler = Handler(Looper.getMainLooper())
    private val committedPageSnapshots = EpubCommittedPageSnapshotCache<Bitmap> { bitmap ->
        if (!bitmap.isRecycled) bitmap.recycle()
    }
    private var adjacentPageFrames: EpubAdjacentPageFramePipeline? = null
    private var interactiveFrameReadyRunnable: Runnable? = null
    private var adjacentFrameInitializationRetryAt = 0L
    private var adjacentPageFrameResumeRunnable: Runnable? = null
    private val queuedPageTurns = EpubPageTurnQueue(MAX_QUEUED_PAGE_TURN_RUNS)
    private var queuedPageTurnDrainRunnable: Runnable? = null
    private var internalPageTurnSetPage = false
    private var committedSnapshotRefreshRunnable: Runnable? = null
    private var warmupActivatedAt = 0L
    private var warmupResumeRunnable: Runnable? = null
    private var startupSourceReported = false
    private var startupInputCount = 0
    private var startupInputAt = 0L
    private var startupMotionReported = false
    private var reviewedFieldTemplate: EpubReaderTemplate? = null
    private var reviewedFieldReference: EpubReaderTemplate? = null
    private var animationMaterialWait: AnimationMaterialWait? = null
    private var animationMaterialTimeoutRunnable: Runnable? = null
    private var legacySnapshotBackendReported = false
    private val reportedResourceFailures = LinkedHashSet<String>()
    private var readerChromeTemplate = EpubReaderChromeData()
    private var readerChromeContentRevision = 0L
    private var hostOverlayCaptureBlocked = false
    private var textPositionToken = -1L
    private var textPositionPage = -1
    private var textPositionRevision = -1L
    private var textPositionOffset = 0

    val position: EpubDirectPosition?
        get() = chapter?.let {
            EpubDirectPosition(
                chapterIndex = it.chapterIndex,
                chapterHref = it.href,
                pageIndex = pageIndex,
                pageCount = pageCount.coerceAtLeast(1),
                progress = pageIndex.toFloat() / (pageCount - 1).coerceAtLeast(1),
                characterPosition = textPositionOffset.takeIf { _ ->
                    it.sourceChapterUrl != null && textPositionToken == generation &&
                        textPositionPage == pageIndex && textPositionRevision == currentLayoutRevision
                }
            )
        }

    val chapterText: String get() = chapter?.plainText.orEmpty()
    val sourceChapterUrl: String? get() = chapter?.sourceChapterUrl

    val hasVisibleDocument: Boolean
        get() = documentReady && chapter != null && currentWebView.visibility == VISIBLE

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val finished = event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            preloadGestureActive = true
            pauseTemplateMotion(currentWebView)
            suspendAdjacentPageFrameScheduling()
            adjacentPageFrames?.prepareGestureFrames()
        } else if (finished) {
            preloadGestureActive = false
        }
        val handled = super.dispatchTouchEvent(event)
        if (finished || (event.actionMasked == MotionEvent.ACTION_DOWN && !handled)) {
            preloadGestureActive = false
            resumeScheduledPreloads()
            scheduleAdjacentPageFrameResume()
            scheduleCommittedPageSnapshotRefresh("gesture-finished")
        }
        return handled
    }

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        addView(currentWebView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width == oldWidth && height == oldHeight) return
        closeAdjacentPageFrames()
        clearQueuedPageTurns()
        invalidateCommittedPageSnapshot()
        if (width > 0 && height > 0) {
            post {
                scheduleCommittedPageSnapshotRefresh("viewport-changed")
                syncAdjacentPageFrames()
            }
        }
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        if (childAffectsCommittedSnapshotScene(child)) {
            markSnapshotSceneChanged(preserveCommittedPage = child is EpubDirectPageAnimationOverlay)
        }
    }

    override fun onViewRemoved(child: View) {
        super.onViewRemoved(child)
        if (childAffectsCommittedSnapshotScene(child)) {
            markSnapshotSceneChanged(preserveCommittedPage = child is EpubDirectPageAnimationOverlay)
        }
    }

    private fun childAffectsCommittedSnapshotScene(child: View): Boolean {
        if (child !is ReaderWebView) return true
        if (child === currentWebView) return true
        return child.visibility == VISIBLE && child.alpha > CANDIDATE_RENDER_ALPHA
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            post { scheduleCommittedPageSnapshotRefresh("window-visible") }
        } else {
            pauseTemplateMotion(currentWebView)
            invalidateCommittedPageSnapshot()
        }
    }

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    /**
     * Updates only fixed-size chrome text. It never participates in document readiness,
     * pagination or chapter activation and is safe to call before any WebView is ready.
     */
    fun updateReaderChromeData(template: EpubReaderChromeData) {
        val contentChanged = renderedChromeFields(chapter, readerChromeTemplate).copy(contentRevision = 0L) !=
            renderedChromeFields(chapter, template).copy(contentRevision = 0L)
        readerChromeTemplate = if (contentChanged) {
            template
        } else {
            template.copy(contentRevision = readerChromeTemplate.contentRevision)
        }
        readerChromeContentRevision = maxOf(
            readerChromeContentRevision,
            readerChromeTemplate.contentRevision
        )
        val activeChromeEnabled = chapter?.let { activeChapter ->
            config?.let { activeConfig ->
                EpubPageFrameTarget.readerChromeEnabled(activeChapter, activeConfig)
            }
        } == true
        if (contentChanged && activeChromeEnabled) invalidateCommittedPageSnapshot()
        if (contentChanged && (config?.readerChrome?.enabled == true || chapter?.readerTemplate != null)) syncAdjacentPageFrames()
        forEachReaderWebView(::applyReaderChromeToView)
    }

    private fun reviewedFrameReference(template: EpubReaderTemplate): EpubReaderTemplate? {
        if (reviewedFieldTemplate !== template) {
            reviewedFieldTemplate = template
            reviewedFieldReference = EpubReaderTemplateStore.reviewedFrameTemplate(template)
        }
        return reviewedFieldReference
    }

    private fun renderedChromeFields(
        prepared: EpubDirectChapter?,
        data: EpubReaderChromeData
    ): EpubReaderChromeData {
        val template = prepared?.readerTemplate ?: return data
        if (template.javascript.isBlank()) return data
        return EpubTemplateFieldPolicy.renderedFields(template, reviewedFrameReference(template), data)
    }

    private fun forEachReaderWebView(action: (ReaderWebView) -> Unit) {
        val views = LinkedHashSet<ReaderWebView>()
        views += currentWebView
        standbyWebView?.let(views::add)
        loadingPreloadedWebViews.values.forEach(views::add)
        preloadedWebViews.forEachValue(views::add)
        views.forEach(action)
    }

    private data class ReaderChromePayload(
        val config: EpubReaderChromeConfig,
        val data: EpubReaderChromeData
    )

    private fun readerChromePayload(
        view: ReaderWebView,
        preparedChapter: EpubDirectChapter? = view.preparedChapter,
        preparedConfig: EpubCoreLayoutConfig? = view.preparedConfig,
        pageIndexOverride: Int? = null,
        pageCountOverride: Int? = null
    ): ReaderChromePayload {
        if (preparedChapter == null || preparedConfig == null) {
            return ReaderChromePayload(EpubReaderChromeConfig.DISABLED, readerChromeTemplate)
        }
        val resolvedConfig = EpubDirectReaderChromePolicy.resolve(
            requested = preparedConfig.readerChrome,
            input = EpubDirectReaderChromePolicy.Input(
                layoutMode = preparedChapter.layoutMode,
                fullPageArtwork = preparedChapter.fullPageArtwork,
                implicitSinglePage = preparedChapter.implicitSinglePage,
                duokanGallery = preparedChapter.duokanGallery,
                scripted = preparedChapter.scripted,
                scrollMode = preparedConfig.scrollMode
            ),
            pageHeightPx = preparedConfig.pageHeightPx
        )
        val preparedCount = pageCountOverride?.coerceAtLeast(1) ?: when {
            view === currentWebView && documentReady && chapter?.chapterIndex == preparedChapter.chapterIndex ->
                pageCount
            view.preloadPageCount > 0 && (view.preloadReady || view.promotedReady) -> view.preloadPageCount
            else -> view.preparedPageCount
        }.coerceAtLeast(1)
        val target = when {
            view === currentWebView && documentReady && chapter?.chapterIndex == preparedChapter.chapterIndex ->
                EpubDirectActivationTargetPolicy.pageIndex(pageIndex)
            view.preloadTarget != null -> view.preloadTarget
            pendingActivationView === view -> pendingActivationTarget
            view.token == generation && !view.preloading -> when {
                pendingLastPage -> EpubDirectActivationTargetPolicy.chapterBoundary(openAtEnd = true)
                else -> EpubDirectActivationTargetPolicy.pageIndex(pendingPageIndex)
            }
            else -> null
        }
        val preparedIndex = pageIndexOverride?.coerceIn(0, preparedCount - 1) ?: when {
            view === currentWebView && documentReady && chapter?.chapterIndex == preparedChapter.chapterIndex ->
                pageIndex
            view.preloadReady || view.promotedReady -> view.preloadPageIndex
            target != null -> EpubDirectActivationTargetPolicy.resolve(target, preparedCount)
            else -> 0
        }.coerceIn(0, preparedCount - 1)
        val unresolvedRevisionData = EpubReaderChromeDataPolicy.resolve(
            config = resolvedConfig,
            template = renderedChromeFields(preparedChapter, readerChromeTemplate).copy(contentRevision = 0L),
            page = EpubReaderChromeDataPolicy.Page(
                chapterIndex = preparedChapter.chapterIndex,
                chapterTitle = preparedChapter.title,
                pageIndex = preparedIndex,
                pageCount = preparedCount
            )
        )
        val previousData = view.appliedReaderChromeData
        val data = if (previousData?.copy(contentRevision = 0L) == unresolvedRevisionData) {
            previousData
        } else {
            unresolvedRevisionData.copy(contentRevision = ++readerChromeContentRevision)
        }
        return ReaderChromePayload(resolvedConfig, data)
    }

    private fun applyReaderChromeToView(view: ReaderWebView) {
        if (destroyed || !view.loadComplete || !view.runtimeInstalled) return
        // The in-flight page command owns its labels. A clock/battery callback
        // still sees the old native index until commit and must not overwrite them.
        if (view === currentWebView && (pageAnimationOverlay != null || pageHandoffRequest != null)) return
        val payload = readerChromePayload(view)
        if (view.appliedReaderChromeConfig == payload.config &&
            view.appliedReaderChromeData == payload.data
        ) {
            return
        }
        applyReaderChromePayloadToView(view, payload) {}
    }

    private fun applyReaderChromePayloadToView(
        view: ReaderWebView,
        payload: ReaderChromePayload,
        onApplied: (Boolean) -> Unit
    ) {
        if (destroyed || !view.loadComplete || !view.runtimeInstalled) {
            onApplied(false)
            return
        }
        val token = view.token
        val chapterKey = view.loadedChapterKey
        val applySequence = ++view.readerChromeApplySequence
        view.readerChromeApplyInFlight = true
        view.appliedReaderChromeConfig = payload.config
        view.appliedReaderChromeData = payload.data
        evaluateReaderCommand(view, readerChromeApplyScript(token, payload)) { raw ->
            if (view.readerChromeApplySequence != applySequence) {
                onApplied(false)
                return@evaluateReaderCommand
            }
            view.readerChromeApplyInFlight = false
            if (view.token != token || view.loadedChapterKey != chapterKey) {
                onApplied(false)
                return@evaluateReaderCommand
            }
            val applied = raw == "true"
            if (!applied) {
                view.appliedReaderChromeConfig = null
                view.appliedReaderChromeData = null
            }
            onApplied(applied)
            resumeCommittedSnapshotAfterReaderChrome(view, "reader-chrome-applied")
        }
    }

    private fun resumeCommittedSnapshotAfterReaderChrome(view: ReaderWebView, reason: String) {
        if (destroyed || view !== currentWebView || !documentReady) return
        scheduleCommittedPageSnapshotRefresh(reason)
    }

    private fun readerChromePayloadMatches(view: ReaderWebView, payload: ReaderChromePayload): Boolean {
        return !view.readerChromeApplyInFlight &&
            view.appliedReaderChromeConfig == payload.config &&
            view.appliedReaderChromeData == payload.data
    }

    private fun readerChromeApplyScript(token: Long, payload: ReaderChromePayload): String {
        return "(function(){var api=window.__legadoEpub;" +
            "if(!api||api.token!==$token)return false;" +
            "api.setReaderChrome(${readerChromeConfigJson(payload.config)});" +
            "api.setReaderChromeData(${readerChromeDataJson(payload.data)});" +
            "return true;})()"
    }

    private fun commitPageAndReaderChrome(
        view: ReaderWebView,
        index: Int,
        pageCountHint: Int,
        targetChapter: EpubDirectChapter,
        targetConfig: EpubCoreLayoutConfig,
        behavior: String,
        boundary: String? = null,
        onApplied: (WebMetrics?) -> Unit
    ) {
        val safePageCount = maxOf(pageCountHint, index + 1, 1)
        val safePageIndex = index.coerceIn(0, safePageCount - 1)
        val payload = readerChromePayload(
            view = view,
            preparedChapter = targetChapter,
            preparedConfig = targetConfig,
            pageIndexOverride = safePageIndex,
            pageCountOverride = safePageCount
        )
        val token = view.token
        val chapterKey = view.loadedChapterKey
        val commitSequence = ++view.pageCommitSequence
        val chromeApplySequence = ++view.readerChromeApplySequence
        view.readerChromeApplyInFlight = true
        view.appliedReaderChromeConfig = payload.config
        view.appliedReaderChromeData = payload.data
        val pageCommand = boundary?.let {
            "api.setActivationPage(${JSONObject.quote(it)},$safePageIndex);"
        } ?: "api.setPage($safePageIndex,0,'$behavior');"
        val script = "(function(){var api=window.__legadoEpub;" +
            "if(!api||api.token!==$token)return null;" +
            "api.setReaderChrome(${readerChromeConfigJson(payload.config)});" +
            "if(api.commitPage){api.commitPage($safePageIndex,'$behavior'," +
            "${readerChromeDataJson(payload.data)},${boundary?.let(JSONObject::quote) ?: "null"});}" +
            "else{api.setReaderChromeData(${readerChromeDataJson(payload.data)});$pageCommand}" +
            "return JSON.stringify(api.metrics(true));})()"
        evaluateReaderCommand(view, script, returnMetrics = true) { raw ->
            val ownsChromeApply = view.readerChromeApplySequence == chromeApplySequence
            if (ownsChromeApply) view.readerChromeApplyInFlight = false
            if (view.token != token || view.loadedChapterKey != chapterKey) {
                if (ownsChromeApply) {
                    resumeCommittedSnapshotAfterReaderChrome(view, "stale-page-and-reader-chrome")
                }
                return@evaluateReaderCommand
            }
            if (view.pageCommitSequence != commitSequence) {
                if (ownsChromeApply) {
                    resumeCommittedSnapshotAfterReaderChrome(view, "superseded-page-and-reader-chrome")
                }
                return@evaluateReaderCommand
            }
            val metrics = parseMetrics(raw)
            if (ownsChromeApply && metrics == null) {
                view.appliedReaderChromeConfig = null
                view.appliedReaderChromeData = null
            }
            onApplied(metrics)
            if (ownsChromeApply) {
                resumeCommittedSnapshotAfterReaderChrome(view, "page-and-reader-chrome-applied")
            }
        }
    }

    private fun evaluateReaderCommand(
        view: ReaderWebView,
        script: String,
        returnMetrics: Boolean = false,
        onResult: (String?) -> Unit
    ) {
        if (view.preparedChapter?.readerTemplate == null) {
            evaluatePageJavascript(view, script, onResult)
            return
        }
        val expectedToken = view.token
        val expectedChapter = view.loadedChapterKey
        val deadline = templateActiveClock.now() +
            if (view.runtimeStable) 8_000L else TEMPLATE_STABLE_TIMEOUT_MS
        var completed = false
        fun current() = !destroyed && !view.surfaceDestroyed &&
            view.token == expectedToken && view.loadedChapterKey == expectedChapter
        fun finish(result: String?) {
            if (completed) return
            completed = true
            onResult(result)
        }
        lateinit var start: Runnable
        start = Runnable start@{
            if (!current()) { finish(null); return@start }
            if (deferTemplateUntilResumed(view, start)) return@start
            evaluatePageJavascript(view, script) initial@{ initialResult ->
                if (!current()) { finish(null); return@initial }
                lateinit var poll: Runnable
                poll = Runnable poll@{
                    if (completed) return@poll
                    if (!current()) { finish(null); return@poll }
                    if (deferTemplateUntilResumed(view, poll)) return@poll
                    evaluatePageJavascript(view, MEASURE_SCRIPT) measured@{ settled ->
                        if (!current()) { finish(null); return@measured }
                        if (deferTemplateUntilResumed(view, poll)) return@measured
                        val measured = parseMetrics(settled)
                        if (measured != null && !measured.layoutPending) {
                            finish(if (returnMetrics) settled else initialResult)
                        } else if (templateActiveClock.now() >= deadline) {
                            val hasDisplayedDocument = view === currentWebView && documentReady
                            finish(null)
                            if (!hasDisplayedDocument) {
                                onTemplateFailure(view, expectedToken, "页面提交超时，请检查模板的布局或脚本")
                            }
                        } else view.postDelayed(poll, 24L)
                    }
                }
                poll.run()
            }
        }
        start.run()
    }

    /** Losing an evaluateJavascript callback is not evidence that the document was lost. */
    private fun evaluatePageJavascript(
        view: ReaderWebView,
        script: String,
        onResult: (String?) -> Unit
    ) {
        val token = view.token
        val chapterKey = view.loadedChapterKey
        val deadline = templateActiveClock.now() + PAGE_JAVASCRIPT_TIMEOUT_MS
        var completed = false
        lateinit var timeout: Runnable
        lateinit var start: Runnable
        fun current() = !destroyed && !view.surfaceDestroyed && view.token == token &&
            view.loadedChapterKey == chapterKey
        fun finish(result: String?) {
            if (completed) return
            completed = true
            removeCallbacks(timeout)
            templateResumeCallbacks.remove(timeout)
            templateResumeCallbacks.remove(start)
            if (!current()) return
            val delivery = Runnable { if (current()) onResult(result) }
            if (!deferTemplateUntilResumed(view, delivery)) delivery.run()
        }
        timeout = Runnable {
            if (completed) return@Runnable
            if (!current()) { finish(null); return@Runnable }
            if (deferTemplateDeadline(this, timeout, deadline)) return@Runnable
            finish(null)
        }
        start = Runnable {
            if (completed) return@Runnable
            if (!current()) { finish(null); return@Runnable }
            if (deferTemplateUntilResumed(view, start)) return@Runnable
            runCatching { view.evaluateJavascript(script, ::finish) }.onFailure {
                AppLog.putDebug("EPUB page command callback unavailable", it)
                finish(null)
            }
        }
        postDelayed(timeout, PAGE_JAVASCRIPT_TIMEOUT_MS)
        start.run()
    }

    private fun deferTemplateUntilResumed(owner: View, callback: Runnable): Boolean {
        if (!hostPaused) return false
        templateResumeCallbacks[callback] = owner
        return true
    }

    private fun deferTemplateDeadline(owner: View, callback: Runnable, deadline: Long): Boolean {
        if (deferTemplateUntilResumed(owner, callback)) return true
        val remaining = deadline - templateActiveClock.now()
        if (remaining <= 0L) return false
        owner.postDelayed(callback, remaining)
        return true
    }

    private fun readerChromeConfigJson(config: EpubReaderChromeConfig): String {
        return JSONObject().apply {
            put("enabled", config.enabled)
            put("headerEnabled", config.headerEnabled)
            put("footerEnabled", config.footerEnabled)
            put("hideHeaderOnChapterFirstPage", config.hideHeaderOnChapterFirstPage)
            put("headerHeightPx", config.headerHeightPx)
            put("footerHeightPx", config.footerHeightPx)
            put("headerPaddingLeftPx", config.headerPaddingLeftPx)
            put("headerPaddingTopPx", config.headerPaddingTopPx)
            put("headerPaddingRightPx", config.headerPaddingRightPx)
            put("headerPaddingBottomPx", config.headerPaddingBottomPx)
            put("footerPaddingLeftPx", config.footerPaddingLeftPx)
            put("footerPaddingTopPx", config.footerPaddingTopPx)
            put("footerPaddingRightPx", config.footerPaddingRightPx)
            put("footerPaddingBottomPx", config.footerPaddingBottomPx)
            put("textSizePx", config.textSizePx.toDouble())
            put("textColor", config.textColor)
            put("dividerColor", config.dividerColor)
            put("headerDividerEnabled", config.headerDividerEnabled)
            put("footerDividerEnabled", config.footerDividerEnabled)
        }.toString()
    }

    private fun readerChromeDataJson(data: EpubReaderChromeData): String {
        return JSONObject().apply {
            put("bookName", data.bookName)
            put("chapterTitle", data.chapterTitle)
            put("page", data.pageLabel)
            put("progress", data.progressLabel)
            put("chapterProgress", data.chapterProgressLabel)
            put("time", data.timeLabel)
            put("battery", data.batteryLabel)
            put("batteryPercentage", data.batteryPercentageLabel)
            put("chapterCount", data.chapterCount)
            put("headerLeft", data.headerLeft)
            put("headerCenter", data.headerCenter)
            put("headerRight", data.headerRight)
            put("footerLeft", data.footerLeft)
            put("footerCenter", data.footerCenter)
            put("footerRight", data.footerRight)
            put("chapterFirstPage", data.chapterFirstPage)
            put("contentRevision", data.contentRevision)
        }.toString()
    }

    fun bindSession(value: EpubDirectSession) {
        bindSession(value, ownsSession = true)
    }

    internal fun bindBorrowedSession(value: EpubDirectSession) {
        bindSession(value, ownsSession = false)
    }

    private fun bindSession(value: EpubDirectSession, ownsSession: Boolean) {
        if (session === value) return
        val previousSession = session
        val closePreviousSession = ownsBoundSession
        closeAdjacentPageFrames()
        clearQueuedPageTurns()
        invalidateCommittedPageSnapshot()
        cancelPendingChapterTurn()
        cancelPageAnimation()
        cancelPageHandoff()
        clearRecoverySnapshot()
        cancelStyleReload()
        cancelRenderRecovery()
        cancelPendingActivation()
        advanceGeneration(preserveCommittedView = false)
        cancelScheduledPreloads()
        clearPreloadedWebViews()
        discardStandbyForLifecycle(preserveForegroundCandidate = false)
        if (session?.bookUrl != value.bookUrl || session?.resourceHost != value.resourceHost) {
            chapter = null
            documentReady = false
            synchronized(reportedResourceFailures) { reportedResourceFailures.clear() }
        }
        session = value
        ownsBoundSession = ownsSession
        if (closePreviousSession) previousSession?.close()
    }

    fun showChapter(
        chapter: EpubDirectChapter,
        config: EpubCoreLayoutConfig,
        initialPageIndex: Int,
        openAtEnd: Boolean = false,
        initialProgress: Float? = null,
        initialFragmentId: String? = null,
        boundaryTransition: Boolean = false
    ) {
        showChapterInternal(
            chapter,
            config,
            initialPageIndex,
            openAtEnd,
            initialProgress,
            initialFragmentId,
            boundaryTransition
        )
    }

    /** The off-screen renderer can move within its committed document without reloading it. */
    internal fun hasReusableChapterForFrame(
        chapter: EpubDirectChapter,
        config: EpubCoreLayoutConfig
    ): Boolean = frameRenderer && !destroyed && documentReady &&
        currentWebView.token == generation && currentWebView.runtimeStable &&
        !currentWebView.renderState.layoutPending &&
        currentWebView.preparedChapter === chapter && this.config == config &&
        currentWebView.loadedChapterKey == chapterKey(chapter, config)

    internal fun reuseChapterForFrame(
        chapter: EpubDirectChapter,
        config: EpubCoreLayoutConfig,
        requestedPageIndex: Int,
        openAtEnd: Boolean
    ): Boolean {
        if (!hasReusableChapterForFrame(chapter, config) || isPageTurnBusy()) return false
        val target = if (openAtEnd) pageCount - 1 else requestedPageIndex
        if (target !in 0 until pageCount) return false
        if (target != pageIndex) return setPage(target, animate = false)
        position?.let { listener?.onReady(it) }
        return true
    }

    private fun showChapterInternal(
        chapter: EpubDirectChapter,
        config: EpubCoreLayoutConfig,
        initialPageIndex: Int,
        openAtEnd: Boolean,
        progress: Float?,
        fragmentId: String?,
        boundaryTransition: Boolean
    ) {
        check(!destroyed) { "EPUB direct layer is destroyed" }
        val chapterTurn = pendingChapterTurn?.takeIf {
            it.sourceView === currentWebView && it.viewportWidth == width &&
                it.viewportHeight == height &&
                EpubDirectChapterTurnPolicy.canBind(
                    boundaryTransition = boundaryTransition,
                    sourceChapterIndex = it.sourceChapterIndex,
                    expectedTargetChapterIndex = it.targetChapterIndex,
                    targetChapterIndex = chapter.chapterIndex,
                    logicalDirection = it.logicalDirection,
                    initialPageIndex = initialPageIndex,
                    openAtEnd = openAtEnd,
                    hasProgress = progress != null,
                    targetFragmentId = fragmentId,
                    chapterStartFragmentId = chapter.startFragmentId,
                    sourceRtl = it.sourceRtl,
                    targetRtl = chapter.isRtlLayout()
                )
        }
        val interactiveBoundaryTurn = chapterTurn?.let(::interactiveTurnFor)
        if (chapterTurn == null) {
            cancelPendingChapterTurn()
        }
        if (interactiveBoundaryTurn == null) {
            cancelPageAnimation(preserveSource = chapterTurn == null)
        }
        cancelPageHandoff()
        cancelStyleReload()
        cancelPendingActivation()
        cancelRenderRecovery()
        val activeSession = session ?: error("EPUB direct session is not bound")
        if (width <= 0 || height <= 0) {
            doOnLayout {
                if (!destroyed && session === activeSession) {
                    showChapterInternal(
                        chapter,
                        config,
                        initialPageIndex,
                        openAtEnd,
                        progress,
                        fragmentId,
                        boundaryTransition
                    )
                }
            }
            return
        }
        annotationVisible = false
        cancelRuntimeMetricsSync()
        generation++
        // Queued input belongs to the document generation that accepted it. Preserve the
        // bound boundary animation, but never replay old page turns inside the new chapter.
        clearQueuedPageTurns()
        currentLayoutRevision = -1L
        invalidateCommittedPageSnapshot()
        val token = generation
        chapterTurn?.targetToken = token
        interactiveBoundaryTurn?.targetToken = token
        pendingPageIndex = initialPageIndex.coerceAtLeast(0)
        pendingLastPage = openAtEnd
        pendingProgress = progress
        pendingFragmentId = fragmentId?.takeIf { it.isNotBlank() }
        pendingActivationTarget = if (boundaryTransition) {
            EpubDirectActivationTargetPolicy.chapterBoundary(openAtEnd)
        } else {
            null
        }
        selectionActive = false

        cancelScheduledPreloads()
        val chapterKey = chapterKey(chapter, config)
        val cachedCandidate = preloadedWebViews.take(chapterKey)
        val cached = cachedCandidate?.takeIf {
            it.loadedChapterKey == chapterKey && it.preloadReady
        }
        if (cachedCandidate != null && cached == null) destroyWebView(cachedCandidate)
        val loadingCandidate = takeLoadingPreload(chapterKey)
        val preloaded = cached ?: loadingCandidate ?: standbyWebView?.takeIf {
            it !== currentWebView && it.loadedChapterKey == chapterKey
        }
        if (preloaded != null) {
            standbyWebView?.takeIf { it !== currentWebView && it !== preloaded }?.let(::destroyWebView)
            standbyWebView = preloaded
        }
        val incoming = runCatching { preloaded ?: obtainStandbyWebView() }.getOrElse {
            failCandidateSetup(null, token, "EPUB WebView candidate could not be created", it)
            return
        }
        val reuseReadyPreload = boundaryTransition && progress == null &&
            fragmentId == null && pendingActivationTarget != null &&
            incoming.preloadReady && incoming.preloadBoundaryReady &&
            incoming.preloadTarget == pendingActivationTarget
        val keepCurrentVisible = hasVisibleDocument
        val setupFailure = runCatching {
            stageCandidate(incoming, chapter, config, keepCurrentVisible)
            if (incoming.parent !== this) {
                addView(
                    incoming,
                    if (keepCurrentVisible) 0 else childCount,
                    LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                )
            }
            if (preloaded != null) {
                incoming.promote(
                    activeSession,
                    token,
                    chapter,
                    config,
                    chapterKey,
                    reuseReady = reuseReadyPreload
                )
            } else {
                incoming.prepare(activeSession, token, chapter, config, chapterKey, preloading = false)
                incoming.loadPreparedDocument(chapter)
            }
        }.exceptionOrNull()
        if (setupFailure != null) {
            failCandidateSetup(incoming, token, "EPUB WebView candidate could not be prepared", setupFailure)
        }
    }

    fun updatePreloadCandidates(
        session: EpubDirectSession,
        chapterIndexes: List<Int>,
        config: EpubCoreLayoutConfig
    ) {
        if (destroyed || this.session !== session) return
        preloadChapterOrder = chapterIndexes.distinct()
        val wanted = chapterIndexes.toSet()
        fun obsolete(view: ReaderWebView): Boolean =
            view.preparedChapter?.chapterIndex !in wanted || view.preparedConfig != config
        // A TOC jump must release the old neighbourhood before checking capacity.
        // Otherwise every slot can remain occupied by chapters we will never consume.
        preloadedWebViews.evictWhere(::obsolete)
        loadingPreloadedWebViews.values.filter(::obsolete).forEach { view ->
            removeLoadingPreload(view)
            destroyWebView(view)
        }
        preloadRunnables.entries.filter {
            it.value.chapterIndex !in wanted || it.value.config != config
        }.map { it.key }.forEach { key ->
            preloadRunnables.remove(key)?.action?.let(::removeCallbacks)
        }
        resumeScheduledPreloads()
    }

    fun hasChapterPreload(
        session: EpubDirectSession,
        chapterIndex: Int,
        config: EpubCoreLayoutConfig
    ): Boolean {
        if (destroyed || this.session !== session) return false
        fun matches(view: ReaderWebView): Boolean =
            view.loadedChapterKey != null && view.preparedChapter?.chapterIndex == chapterIndex &&
                view.preparedConfig == config
        if (matches(currentWebView) || standbyWebView?.let(::matches) == true ||
            loadingPreloadedWebViews.values.any(::matches) ||
            preloadRunnables.values.any { it.chapterIndex == chapterIndex && it.config == config }
        ) return true
        var cached = false
        preloadedWebViews.forEachValue { if (matches(it)) cached = true }
        return cached
    }

    fun preloadChapter(
        session: EpubDirectSession,
        chapter: EpubDirectChapter,
        config: EpubCoreLayoutConfig,
        openAtEnd: Boolean = false
    ) {
        if (destroyed || this.session !== session || this.chapter?.chapterIndex == chapter.chapterIndex) return
        adjacentPageFrames?.offerPreparedChapter(session, chapter, config)
        val key = chapterKey(chapter, config)
        if (isPreloadOwned(key) || standbyWebView?.loadedChapterKey == key ||
            !hasPreloadCapacity()
        ) return
        lateinit var preload: Runnable
        preload = Runnable {
            if (preloadRunnables[key]?.action !== preload) return@Runnable
            // Prepare chapter data while the foreground is laying out, but defer a
            // second WebView's layout until the current document has committed.
            if (!canStartPreload()) return@Runnable
            preloadRunnables.remove(key)
            if (destroyed || this.session !== session || this.chapter?.chapterIndex == chapter.chapterIndex) {
                resumeScheduledPreloads()
                return@Runnable
            }
            if (isPreloadOwned(key) || !hasPreloadCapacity()) {
                resumeScheduledPreloads()
                return@Runnable
            }
            val standby = runCatching { createWebView() }.getOrElse {
                AppLog.putDebug("EPUB direct preload WebView creation failed", it)
                resumeScheduledPreloads()
                return@Runnable
            }
            val token = --preloadSequence
            val attached = runCatching {
                stageCandidate(standby, chapter, config, keepCurrentVisible = true)
                standby.prepare(
                    session,
                    token,
                    chapter,
                    config,
                    key,
                    preloading = true,
                    preloadTarget = EpubDirectActivationTargetPolicy.chapterBoundary(openAtEnd)
                )
                addView(standby, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            }.onFailure {
                AppLog.putDebug("EPUB direct preload WebView attachment failed", it)
            }.isSuccess
            if (!attached) {
                destroyWebView(standby)
                resumeScheduledPreloads()
                return@Runnable
            }
            loadingPreloadedWebViews[key] = standby
            runCatching { standby.loadPreparedDocument(chapter) }.onFailure {
                discardFailedPreload(standby)
                AppLog.putDebug("EPUB direct preload document failed", it)
            }
        }
        preloadRunnables[key] = ScheduledPreload(chapter.chapterIndex, config, preload)
        // Let the committed page draw once, then start the already-prepared neighbour.
        resumeScheduledPreloads()
    }

    private fun canStartPreload(): Boolean =
        !destroyed && !hostPaused && documentReady && currentWebView.token == generation &&
            !preloadGestureActive && loadingPreloadedWebViews.isEmpty() &&
            !isPageTurnBusy() && interactivePageTurn == null && !isSelectionPageTurnBlocked() &&
            EpubReaderWarmupPolicy.canStartChapterLayout(
                sourceReady = hasReadyCurrentSnapshot(),
                elapsedMillis = SystemClock.uptimeMillis() - warmupActivatedAt,
                frameLayoutRunning = adjacentPageFrames?.hasColdLayoutInFlight() == true,
                nearPagesReady = adjacentPageFrames?.hasNearForwardFrames() == true,
                requiredForNearPages = pageCount - pageIndex - 1 < 2,
                frameRenderingAvailable = adjacentPageFrames != null
            )

    private fun hasReadyCurrentSnapshot(): Boolean =
        committedPageSnapshotKey()?.let(committedPageSnapshots::contains) == true

    private fun beginFrameWarmup() {
        warmupResumeRunnable?.let(::removeCallbacks)
        warmupActivatedAt = SystemClock.uptimeMillis()
        startupSourceReported = false
        startupInputCount = 0
        startupInputAt = 0L
        startupMotionReported = false
        if (frameRenderer) return
        AppLog.putDebug("EPUB startup visible: chapter=${chapter?.chapterIndex}, page=$pageIndex, pages=$pageCount")
        val expectedGeneration = generation
        lateinit var resume: Runnable
        resume = Runnable {
            if (warmupResumeRunnable !== resume) return@Runnable
            warmupResumeRunnable = null
            if (destroyed || generation != expectedGeneration || !documentReady) return@Runnable
            syncAdjacentPageFrames()
            resumeScheduledPreloads()
            val remaining = EpubReaderWarmupPolicy.NEAR_PAGE_PRIORITY_MS -
                (SystemClock.uptimeMillis() - warmupActivatedAt)
            if (remaining > 0L) {
                warmupResumeRunnable = resume
                postDelayed(resume, remaining)
            }
        }
        warmupResumeRunnable = resume
        // A failed main-surface capture must not starve all offscreen work.
        postDelayed(resume, EpubReaderWarmupPolicy.SOURCE_HEAD_START_MS)
    }

    private fun onCurrentSnapshotReady() {
        if (frameRenderer) return
        if (!startupSourceReported) {
            startupSourceReported = true
            AppLog.putDebug("EPUB startup source-ready: chapter=${chapter?.chapterIndex}, " +
                "page=$pageIndex, elapsedMs=${SystemClock.uptimeMillis() - warmupActivatedAt}")
        }
        syncAdjacentPageFrames()
        scheduleQueuedPageTurnDrain()
        currentWebView.resumePendingInteractiveDrag()
    }

    private fun resumeScheduledPreloads() {
        if (!canStartPreload()) return
        preloadRunnables.values.forEach {
            removeCallbacks(it.action)
        }
        // Keep the nearest chapters first and start one cold layout at a time. Warming
        // several WebViews in the same frame competes with the user's next gesture.
        val next = preloadRunnables.values.minByOrNull {
            preloadChapterOrder.indexOf(it.chapterIndex).takeIf { order -> order >= 0 } ?: Int.MAX_VALUE
        } ?: return
        postOnAnimation(next.action)
    }

    fun nextPage(animate: Boolean = true): EpubPageTurnResult {
        return performPageTurn(logicalDirection = 1, animate = animate, queueIfBusy = true)
    }

    fun previousPage(animate: Boolean = true): EpubPageTurnResult {
        return performPageTurn(logicalDirection = -1, animate = animate, queueIfBusy = true)
    }

    private fun performPageTurn(
        logicalDirection: Int,
        animate: Boolean,
        queueIfBusy: Boolean
    ): EpubPageTurnResult {
        val direction = if (logicalDirection < 0) -1 else 1
        if (isSelectionPageTurnBlocked()) return EpubPageTurnResult.Rejected
        if (isPageTurnBusy()) {
            val queued = queueIfBusy && enqueuePageTurn(direction)
            if (pendingChapterTurn != null) snapToCurrentHorizontalPage(currentWebView)
            return if (queued) EpubPageTurnResult.Queued else EpubPageTurnResult.Rejected
        }
        if (!documentReady) return EpubPageTurnResult.Unavailable
        val atBoundary = if (direction > 0) pageIndex >= pageCount - 1 else pageIndex <= 0
        // Chapter navigation is a logical handoff, not an animation-material request. Resolve
        // it immediately while the current-page snapshot is refreshing; visual animation stays
        // best effort inside the pending chapter transaction.
        if (atBoundary) {
            return if (prepareChapterTurn(direction = direction, animate = animate) != null) {
                EpubPageTurnResult.BoundaryRequired
            } else {
                EpubPageTurnResult.Unavailable
            }
        }
        if (shouldWaitForPageAnimationMaterial(direction, animate)) {
            return if (queueIfBusy && enqueuePageTurn(direction)) {
                EpubPageTurnResult.Queued
            } else {
                EpubPageTurnResult.Unavailable
            }
        }
        val targetPage = pageIndex + direction
        if (setPageFromPageTurn(targetPage, animate)) {
            return EpubPageTurnResult.MovedWithinChapter
        }
        return if (queueIfBusy && enqueuePageTurn(direction)) {
            EpubPageTurnResult.Queued
        } else {
            EpubPageTurnResult.Rejected
        }
    }

    private fun enqueuePageTurn(logicalDirection: Int): Boolean {
        val direction = if (logicalDirection < 0) -1 else 1
        if (!queuedPageTurns.enqueue(direction)) {
            AppLog.putDebug(
                "EPUB rapid-turn queue is full: direction=$direction, " +
                    "runs=${queuedPageTurns.runCount}"
            )
            return false
        }
        scheduleQueuedPageTurnDrain()
        return true
    }

    private fun scheduleQueuedPageTurnDrain() {
        if (destroyed) return
        if (queuedPageTurns.isEmpty) {
            schedulePendingReadAloudCueDrain()
            resumeScheduledPreloads()
            return
        }
        if (queuedPageTurnDrainRunnable != null) return
        lateinit var drain: Runnable
        drain = Runnable {
            if (queuedPageTurnDrainRunnable !== drain) return@Runnable
            queuedPageTurnDrainRunnable = null
            if (destroyed || queuedPageTurns.isEmpty) return@Runnable
            if (!documentReady || isPageTurnBusy()) {
                // Completion paths explicitly restart the drain. Polling every frame while
                // a WebView activates or an animation runs causes visible hitches under
                // rapid input without making a queued turn actionable any sooner.
                return@Runnable
            }
            val direction = queuedPageTurns.peekDirection() ?: return@Runnable
            val result = performPageTurn(
                logicalDirection = direction,
                animate = true,
                queueIfBusy = false
            )
            val handled = when (result) {
                EpubPageTurnResult.MovedWithinChapter -> true
                EpubPageTurnResult.BoundaryRequired -> dispatchBoundary(
                    direction = direction,
                    accepted = true
                )
                EpubPageTurnResult.Queued,
                EpubPageTurnResult.Rejected,
                EpubPageTurnResult.Unavailable -> false
            }
            if (handled) queuedPageTurns.markHeadHandled()
            if (handled && !queuedPageTurns.isEmpty) scheduleQueuedPageTurnDrain()
            if (handled && queuedPageTurns.isEmpty) {
                schedulePendingReadAloudCueDrain()
                resumeScheduledPreloads()
            }
        }
        queuedPageTurnDrainRunnable = drain
        postOnAnimation(drain)
    }

    private fun clearQueuedPageTurns() {
        queuedPageTurnDrainRunnable?.let(::removeCallbacks)
        queuedPageTurnDrainRunnable = null
        queuedPageTurns.clear()
        cancelAnimationMaterialWait()
    }

    private fun isPageTurnBusy(): Boolean {
        return pendingChapterTurn != null || pageAnimationOverlay != null ||
            pageAnimator != null || pageHandoffRequest != null || pendingActivationView != null ||
            renderRecoveryRequest != null || foregroundRevealRunnable != null
    }

    private fun shouldWaitForPageAnimationMaterial(
        logicalDirection: Int,
        animate: Boolean
    ): Boolean {
        val style = EpubDirectPageAnimationPolicy.style(
            pageAnim = ReadBook.pageAnim(),
            horizontal = animate && !isVerticalMode() && chapter?.layoutMode?.singlePage != true
        )
        if (style == EpubDirectPageAnimationPolicy.Style.None) return false
        val key = committedPageSnapshotKey(currentWebView) ?: return false
        val direction = if (logicalDirection < 0) -1 else 1
        val action = EpubDirectPageAnimationPolicy.turnAction(direction)
        val targetPage = pageIndex + direction
        val activeChapter = chapter ?: return false
        val targetChapterIndex: Int
        val targetPageIndex: Int?
        if (targetPage in 0 until pageCount.coerceAtLeast(1)) {
            targetChapterIndex = activeChapter.chapterIndex
            targetPageIndex = targetPage
        } else {
            targetChapterIndex = session?.adjacentChapterIndex(activeChapter.chapterIndex, direction)
                ?: return false
            targetPageIndex = if (direction > 0) 0 else null
        }
        val requiresAdjacent = supportsAdjacentPageFrames() &&
            !EpubDirectPageAnimationPolicy.hasRequiredBitmapFrames(
            style = style,
            action = action,
            hasTargetBitmap = false
        )
        val sourceReady = committedPageSnapshots.contains(key)
        val targetReady = !requiresAdjacent || hasAdjacentPageBitmap(
            logicalDirection = direction,
            expectedChapterIndex = targetChapterIndex,
            expectedPageIndex = targetPageIndex
        )
        val expected = AnimationMaterialWait(
            snapshotKey = key,
            logicalDirection = direction,
            targetChapterIndex = targetChapterIndex,
            targetPageIndex = targetPageIndex,
            requiresAdjacent = requiresAdjacent
        )
        if (sourceReady && targetReady) {
            cancelAnimationMaterialWait()
            return false
        }
        val waiting = animationMaterialWait
        if (waiting?.matches(expected) == true && waiting.fallbackAllowed) {
            cancelAnimationMaterialWait()
            return false
        }
        if (waiting?.matches(expected) != true) beginAnimationMaterialWait(expected)
        if (!sourceReady) {
            scheduleCommittedPageSnapshotRefresh(
                reason = "page-turn-awaiting-source",
                expectedKey = key
            )
        }
        if (!targetReady) syncAdjacentPageFrames()
        return true
    }

    private fun hasAdjacentPageBitmap(
        logicalDirection: Int,
        expectedChapterIndex: Int,
        expectedPageIndex: Int?
    ): Boolean {
        val direction = adjacentPageDirection(logicalDirection)
        val pipeline = adjacentPageFrames ?: return false
        val target = pipeline.target(direction) ?: return false
        return adjacentPageTargetMatches(target, expectedChapterIndex, expectedPageIndex) &&
            pipeline.hasFrame(direction)
    }

    private fun beginAnimationMaterialWait(wait: AnimationMaterialWait) {
        cancelAnimationMaterialWait()
        animationMaterialWait = wait
        lateinit var timeout: Runnable
        timeout = Runnable {
            if (animationMaterialTimeoutRunnable !== timeout || animationMaterialWait != wait) {
                return@Runnable
            }
            animationMaterialTimeoutRunnable = null
            animationMaterialWait?.fallbackAllowed = true
            scheduleQueuedPageTurnDrain()
        }
        animationMaterialTimeoutRunnable = timeout
        postDelayed(timeout, PAGE_ANIMATION_MATERIAL_TIMEOUT_MS)
    }

    private fun allowAnimationMaterialFallback(key: EpubCommittedPageSnapshotKey) {
        val waiting = animationMaterialWait ?: run {
            // A queued turn can be waiting for the capture itself before it has entered the
            // material-wait state. Wake it so the bounded 180 ms fallback can own the outcome.
            if (!queuedPageTurns.isEmpty) scheduleQueuedPageTurnDrain()
            return
        }
        if (waiting.snapshotKey != key) return
        animationMaterialTimeoutRunnable?.let(::removeCallbacks)
        animationMaterialTimeoutRunnable = null
        waiting.fallbackAllowed = true
        scheduleQueuedPageTurnDrain()
    }

    private fun cancelAnimationMaterialWait() {
        animationMaterialTimeoutRunnable?.let(::removeCallbacks)
        animationMaterialTimeoutRunnable = null
        animationMaterialWait = null
    }

    private fun setPageFromPageTurn(index: Int, animate: Boolean): Boolean {
        internalPageTurnSetPage = true
        return try {
            setPage(index, animate)
        } finally {
            internalPageTurnSetPage = false
        }
    }

    fun setPage(index: Int, animate: Boolean = false): Boolean {
        // Selection owns the viewport until the range is explicitly cleared.  Keep this
        // guard at the lowest native page-mutation entry point because layout, highlight,
        // handoff and animation callbacks can all arrive without going through EpubReadView.
        if (isSelectionPageTurnBlocked()) return false
        if (!internalPageTurnSetPage) clearQueuedPageTurns()
        if (pendingChapterTurn != null) cancelPendingChapterTurn()
        if (EpubDirectPageAnimationPolicy.consumesRepeatedTurn(
                pageAnimationOverlay != null || pageAnimator != null
            )
        ) return false
        if (!documentReady) {
            pendingPageIndex = index.coerceAtLeast(0)
            return false
        }
        val target = index.coerceIn(0, pageCount - 1)
        val replacingHandoff = pageHandoffRequest != null
        val changed = target != pageIndex
        if (!changed && !replacingHandoff) return false
        // Cancel stale measurements only once a new command owns the position.
        // Before that, a query may still help prepare the first animation source.
        cancelRuntimeMetricsSync()
        cancelPageHandoff()
        clearSelection()
        val previous = pageIndex
        val logicalDirection = if (target > previous) 1 else -1
        val direction = EpubDirectPageAnimationPolicy.visualDirection(
            logicalDirection = logicalDirection,
            rtl = chapter.isRtlLayout()
        )
        val request = ++pageApplySequence
        val token = generation
        val view = currentWebView
        fun reconcileAppliedPage(metrics: WebMetrics?) {
            if (request != pageApplySequence || token != generation || view !== currentWebView) return
            val nextCount = (metrics?.pageCount ?: pageCount).coerceAtLeast(1)
            // This result has passed target and visual verification. A reflow may
            // have clamped the request to a new last page; keep that actual position.
            val nextIndex = (metrics?.pageIndex ?: target).coerceIn(0, nextCount - 1)
            val positionChanged = nextCount != pageCount || nextIndex != pageIndex
            val layoutChanged = metrics?.layoutRevision?.takeIf { it >= 0L }
                ?.let { it != currentLayoutRevision } == true
            pageCount = nextCount
            pageIndex = nextIndex
            applyReaderChromeToView(view)
            if (layoutChanged) {
                currentLayoutRevision = checkNotNull(metrics).layoutRevision
                closeAdjacentPageFrames()
                invalidateCommittedPageSnapshot()
            }
            if (positionChanged) notifyPositionChanged()
        }
        val requestedStyle = EpubDirectPageAnimationPolicy.style(
                pageAnim = ReadBook.pageAnim(),
                horizontal = animate && !isVerticalMode() && chapter?.layoutMode?.singlePage != true
            )
        if (!startPageAnimation(
                view = view,
                index = target,
                logicalDirection = logicalDirection,
                visualDirection = direction,
                animate = animate,
                onApplied = ::reconcileAppliedPage
            )
        ) {
            if (requestedStyle != EpubDirectPageAnimationPolicy.Style.None) {
                scheduleCommittedPageSnapshotRefresh("programmatic-turn-source-unavailable")
            }
            pageHandoffRequest = request
            val transitionSnapshot = preservePageTransitionSnapshot(view)
            invalidateCommittedPageSnapshot()
            schedulePageHandoffTimeout(
                view = view,
                request = request,
                token = token,
                sourcePageIndex = previous,
                transitionSnapshot = transitionSnapshot,
                onResult = null,
                forwardTargetPageIndex = target
            )
            applyPage(
                view = view,
                index = target,
                animate = animate
            ) { appliedMetrics ->
                completeAppliedPage(
                    view = view,
                    targetPageIndex = target,
                    metrics = appliedMetrics,
                    isActive = { isPageHandoffActive(view, request, token) },
                    onCommitted = { metrics ->
                        finishPageHandoff(request)
                        reconcileAppliedPage(metrics)
                        clearRecoverySnapshot(transitionSnapshot)
                        syncAdjacentPageFrames()
                        scheduleCommittedPageSnapshotRefresh("page-handoff-committed")
                        scheduleQueuedPageTurnDrain()
                    },
                    onNeedsProbe = {
                        verifyAnimationTargetPage(
                            view = view,
                            target = EpubDirectActivationTargetPolicy.pageIndex(target),
                            requiresRenderableContent = chapter.requiresRenderableContent(),
                            isActive = { isPageHandoffActive(view, request, token) },
                            onVerified = { metrics ->
                                finishPageHandoff(request)
                                reconcileAppliedPage(metrics)
                                clearRecoverySnapshot(transitionSnapshot)
                                syncAdjacentPageFrames()
                                scheduleCommittedPageSnapshotRefresh("page-handoff-committed")
                                scheduleQueuedPageTurnDrain()
                            },
                            onFailure = { recoverUncommittedPageTurn(view, token, target) }
                        )
                    }
                )
            }
        }
        return true
    }

    fun followReadAloud(cueText: String, cueOffset: Int, approximateProgress: Float): Boolean {
        if (!documentReady || cueText.isBlank() || isSelectionPageTurnBlocked()) return false
        val activeChapter = chapter ?: return false
        pendingReadAloudCue = PendingReadAloudCue(
            sequence = ++readAloudCueSequence,
            token = generation,
            chapterIndex = activeChapter.chapterIndex,
            text = cueText,
            offset = cueOffset.coerceAtLeast(0),
            progress = approximateProgress.coerceIn(0f, 1f)
        )
        schedulePendingReadAloudCueDrain()
        return true
    }

    private fun schedulePendingReadAloudCueDrain() {
        if (destroyed || pendingReadAloudCue == null || readAloudCueDrainRunnable != null) return
        lateinit var drain: Runnable
        drain = Runnable {
            if (readAloudCueDrainRunnable !== drain) return@Runnable
            readAloudCueDrainRunnable = null
            val cue = pendingReadAloudCue ?: return@Runnable
            if (destroyed || cue.token != generation || cue.chapterIndex != chapter?.chapterIndex) {
                if (pendingReadAloudCue === cue) pendingReadAloudCue = null
                return@Runnable
            }
            if (!documentReady || isSelectionPageTurnBlocked() || isPageTurnBusy() ||
                interactivePageTurn != null || !queuedPageTurns.isEmpty
            ) return@Runnable
            val view = currentWebView
            val script =
                "(function(){var api=window.__legadoEpub;" +
                    "if(!api||api.token!==${cue.token}||!api.locateReadAloud)return null;" +
                    "return JSON.stringify(api.locateReadAloud(${JSONObject.quote(cue.text)}," +
                    "${cue.offset},${cue.progress}));})();"
            view.evaluateJavascript(script) { raw ->
                if (pendingReadAloudCue !== cue || destroyed || cue.sequence != readAloudCueSequence ||
                    cue.token != generation || cue.chapterIndex != chapter?.chapterIndex ||
                    view !== currentWebView
                ) return@evaluateJavascript
                val decoded = runCatching { JSONArray("[$raw]").optString(0) }.getOrNull()
                val result = decoded?.let { runCatching { JSONObject(it) }.getOrNull() }
                val targetPage = result
                    ?.takeIf { it.optBoolean("accepted", false) }
                    ?.optInt("pageIndex", -1)
                    ?.takeIf { it in 0 until pageCount }
                if (result?.optString("reason") == "selection" ||
                    targetPage != null && targetPage != pageIndex &&
                    (isSelectionPageTurnBlocked() || isPageTurnBusy() ||
                        interactivePageTurn != null || !queuedPageTurns.isEmpty)
                ) {
                    schedulePendingReadAloudCueDrain()
                    return@evaluateJavascript
                }
                pendingReadAloudCue = null
                if (targetPage != null && targetPage != pageIndex) setPage(targetPage, animate = false)
            }
        }
        readAloudCueDrainRunnable = drain
        postOnAnimation(drain)
    }

    fun clearSelection() {
        val hadSelection = selectionActive || currentWebView.hasLongPressSelectionGesture()
        selectionActive = false
        // Ordinary navigation must not enqueue selection commands in the WebView.
        if (!hadSelection) return
        currentWebView.evaluateJavascript(CLEAR_SELECTION_SCRIPT, null)
        post {
            resumePendingActivationAfterSelection()
            if (currentWebView.renderState.needsMetrics) scheduleRuntimeMetricsSync()
            schedulePendingReadAloudCueDrain()
        }
    }

    /**
     * 取点击位置所在的单词, 供取词模式使用
     */
    fun wordAtPoint(x: Float, y: Float, callback: (String?) -> Unit) {
        if (destroyed || !documentReady) {
            callback(null)
            return
        }
        val view = currentWebView
        val token = generation
        val script = "(function(){try{var api=window.__legadoEpub;" +
            "if(!api||!api.wordAt)return null;" +
            "return api.wordAt($x,$y)||null;}catch(e){return null;}})();"
        view.evaluateJavascript(script) { raw ->
            if (destroyed || view !== currentWebView || token != generation) {
                callback(null)
                return@evaluateJavascript
            }
            val word = runCatching { JSONArray("[$raw]").optString(0) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() && it != "null" }
            callback(word)
        }
    }

    fun dismissAnnotation(onResult: (Boolean) -> Unit) {
        if (!documentReady) {
            onResult(false)
            return
        }
        val view = currentWebView
        val token = generation
        val completed = AtomicBoolean(false)
        val timeout = Runnable {
            if (completed.compareAndSet(false, true)) onResult(false)
        }
        postDelayed(timeout, ANNOTATION_DISMISS_CALLBACK_TIMEOUT_MS)
        view.evaluateJavascript(DISMISS_ANNOTATION_SCRIPT) { rawResult ->
            if (!completed.compareAndSet(false, true)) return@evaluateJavascript
            removeCallbacks(timeout)
            val dismissed = !destroyed && view === currentWebView && view.token == token &&
                token == generation && rawResult == "true"
            if (dismissed) annotationVisible = false
            onResult(dismissed)
        }
    }

    fun navigateToFragment(fragmentId: String, onResult: ((Boolean) -> Unit)? = null): Boolean {
        if (!documentReady || fragmentId.isBlank() || isSelectionPageTurnBlocked()) return false
        clearQueuedPageTurns()
        val transitionSnapshot = cancelPageAnimation(preserveSource = true)
            ?: preservePageTransitionSnapshot(currentWebView)
        cancelPageHandoff()
        clearSelection()
        val request = ++pageApplySequence
        pageHandoffRequest = request
        val token = generation
        val view = currentWebView
        val sourcePageIndex = pageIndex
        schedulePageHandoffTimeout(
            view = view,
            request = request,
            token = token,
            sourcePageIndex = sourcePageIndex,
            transitionSnapshot = transitionSnapshot,
            onResult = onResult
        )
        val script = "(function(){var api=window.__legadoEpub;" +
            "if(!api||!api.goToFragment(${JSONObject.quote(fragmentId)},false))return null;" +
            "return api.lastFragmentPage?api.lastFragmentPage():null;})();"
        view.evaluateJavascript(script) { raw ->
            if (token != generation || view !== currentWebView) return@evaluateJavascript
            val expectedPageIndex = raw?.trim()?.toIntOrNull()
            if (expectedPageIndex == null || expectedPageIndex < 0) {
                restorePageAfterFailedHandoff(
                    view = view,
                    request = request,
                    token = token,
                    sourcePageIndex = sourcePageIndex,
                    transitionSnapshot = transitionSnapshot,
                    onResult = onResult
                )
                return@evaluateJavascript
            }
            verifyPageHandoff(
                view = view,
                request = request,
                token = token,
                expectedPageIndex = expectedPageIndex,
                sourcePageIndex = sourcePageIndex,
                transitionSnapshot = transitionSnapshot,
                onCommitted = { metrics ->
                    commitPageMetrics(metrics)
                    onResult?.invoke(true)
                },
                onFailure = onResult
            )
        }
        return true
    }

    private fun verifyPageHandoff(
        view: ReaderWebView,
        request: Long,
        token: Long,
        expectedPageIndex: Int,
        sourcePageIndex: Int,
        transitionSnapshot: EpubDirectRecoverySnapshotOverlay?,
        onCommitted: (WebMetrics) -> Unit,
        onFailure: ((Boolean) -> Unit)? = null,
        attempt: Int = 0
    ) {
        fun isVerified(metrics: WebMetrics): Boolean {
            return isMeasuredRenderStateCurrent(view, metrics) && EpubDirectActivationVisualPolicy.canNavigate(
                requiresViewportContent = chapter?.sourceChapterUrl != null,
                requiresRenderableContent = chapter.requiresRenderableContent(),
                hasRenderableContent = metrics.hasRenderableContent,
                hasViewportContent = metrics.hasViewportContent,
                expectedPageIndex = expectedPageIndex,
                actualPageIndex = metrics.pageIndex
            )
        }
        fun commit(metrics: WebMetrics) {
            if (!isPageHandoffActive(view, request, token)) return
            finishPageHandoff(request)
            clearRecoverySnapshot(transitionSnapshot)
            onCommitted(metrics)
            scheduleCommittedPageSnapshotRefresh("page-handoff-committed")
            scheduleQueuedPageTurnDrain()
        }
        completeAfterVisualState(view) {
            if (!isPageHandoffActive(view, request, token)) {
                return@completeAfterVisualState
            }
            val renderSequence = view.renderState.sequence
            view.evaluateJavascript(MEASURE_VIEWPORT_SCRIPT) { raw ->
                if (!isPageHandoffActive(view, request, token)) {
                    return@evaluateJavascript
                }
                val metrics = parseMetrics(raw)
                val verified = metrics != null && isVerified(metrics) && view.renderState.measured(
                    token, metrics.visualRevision, metrics.layoutPending, renderSequence
                )
                if (!verified && attempt < MAX_PAGE_HANDOFF_VERIFY_ATTEMPTS) {
                    postDelayed({
                        verifyPageHandoff(
                            view = view,
                            request = request,
                            token = token,
                            expectedPageIndex = expectedPageIndex,
                            sourcePageIndex = sourcePageIndex,
                            transitionSnapshot = transitionSnapshot,
                            onCommitted = onCommitted,
                            onFailure = onFailure,
                            attempt = attempt + 1
                        )
                    }, PAGE_HANDOFF_RETRY_MS)
                    return@evaluateJavascript
                }
                if (!verified) {
                    restorePageAfterFailedHandoff(
                        view,
                        request,
                        token,
                        sourcePageIndex,
                        transitionSnapshot,
                        onFailure
                    )
                    return@evaluateJavascript
                }
                commit(checkNotNull(metrics))
            }
        }
    }

    private fun restorePageAfterFailedHandoff(
        view: ReaderWebView,
        request: Long,
        token: Long,
        sourcePageIndex: Int,
        transitionSnapshot: EpubDirectRecoverySnapshotOverlay?,
        onResult: ((Boolean) -> Unit)?
    ) {
        if (request != pageApplySequence || token != generation || view !== currentWebView ||
            pageHandoffRequest != request || pageHandoffRestoringRequest == request
        ) return
        pageHandoffRestoringRequest = request
        pageHandoffTimeoutRunnable?.let(::removeCallbacks)
        pageHandoffTimeoutRunnable = null
        var finished = false
        fun finishRestore(metrics: WebMetrics?) {
            if (finished || request != pageApplySequence || token != generation ||
                view !== currentWebView || pageHandoffRequest != request
            ) return
            finished = true
            pageHandoffRestoreTimeoutRunnable?.let(::removeCallbacks)
            pageHandoffRestoreTimeoutRunnable = null
            val restored = metrics?.takeIf {
                EpubDirectActivationVisualPolicy.canNavigate(
                    requiresViewportContent = chapter?.sourceChapterUrl != null,
                    requiresRenderableContent = chapter.requiresRenderableContent(),
                    hasRenderableContent = it.hasRenderableContent,
                    hasViewportContent = it.hasViewportContent,
                    expectedPageIndex = sourcePageIndex.coerceIn(0, it.pageCount.coerceAtLeast(1) - 1),
                    actualPageIndex = it.pageIndex
                )
            }
            if (restored != null) {
                commitPageMetrics(restored)
            } else if (pageIndex != sourcePageIndex) {
                pageIndex = sourcePageIndex.coerceIn(0, pageCount.coerceAtLeast(1) - 1)
                notifyPositionChanged()
            }
            finishPageHandoff(request)
            // A snapshot may hide resources that finish decoding after the restore
            // deadline. Never leave it mounted once this handoff is no longer active.
            clearRecoverySnapshot(transitionSnapshot)
            onResult?.invoke(false)
            scheduleCommittedPageSnapshotRefresh("page-handoff-restored")
            scheduleQueuedPageTurnDrain()
        }
        lateinit var restoreTimeout: Runnable
        restoreTimeout = Runnable {
            if (pageHandoffRestoreTimeoutRunnable !== restoreTimeout) return@Runnable
            finishRestore(null)
        }
        pageHandoffRestoreTimeoutRunnable = restoreTimeout
        postDelayed(restoreTimeout, PAGE_HANDOFF_RESTORE_TIMEOUT_MS)
        val script = "(function(){var api=window.__legadoEpub;if(!api)return null;" +
            "api.setPage($sourcePageIndex,0,'auto');return true;})();"
        view.evaluateJavascript(script) {
            completeAfterCompositorFrames(view) {
                if (request != pageApplySequence || token != generation || view !== currentWebView) {
                    return@completeAfterCompositorFrames
                }
                view.evaluateJavascript(MEASURE_VIEWPORT_SCRIPT) { raw ->
                    finishRestore(parseMetrics(raw))
                }
            }
        }
    }

    private fun schedulePageHandoffTimeout(
        view: ReaderWebView,
        request: Long,
        token: Long,
        sourcePageIndex: Int,
        transitionSnapshot: EpubDirectRecoverySnapshotOverlay?,
        onResult: ((Boolean) -> Unit)?,
        forwardTargetPageIndex: Int? = null
    ) {
        pageHandoffTimeoutRunnable?.let(::removeCallbacks)
        val timeoutMillis = if (forwardTargetPageIndex == null) PAGE_HANDOFF_TIMEOUT_MS else PAGE_ANIMATION_COMMIT_TIMEOUT_MS
        val deadline = templateActiveClock.now() + timeoutMillis
        lateinit var timeout: Runnable
        timeout = Runnable {
            if (pageHandoffTimeoutRunnable !== timeout) return@Runnable
            if (deferTemplateDeadline(this, timeout, deadline)) return@Runnable
            pageHandoffTimeoutRunnable = null
            if (forwardTargetPageIndex != null && isPageHandoffActive(view, request, token)) {
                recoverUncommittedPageTurn(view, token, forwardTargetPageIndex)
                return@Runnable
            }
            restorePageAfterFailedHandoff(
                view = view,
                request = request,
                token = token,
                sourcePageIndex = sourcePageIndex,
                transitionSnapshot = transitionSnapshot,
                onResult = onResult
            )
        }
        pageHandoffTimeoutRunnable = timeout
        postDelayed(timeout, timeoutMillis)
    }

    private fun isPageHandoffActive(view: ReaderWebView, request: Long, token: Long): Boolean {
        return request == pageApplySequence && token == generation && view === currentWebView &&
            pageHandoffRequest == request && pageHandoffRestoringRequest != request
    }

    private fun commitPageMetrics(metrics: WebMetrics) {
        val nextCount = metrics.pageCount.coerceAtLeast(1)
        val nextIndex = metrics.pageIndex.coerceIn(0, nextCount - 1)
        val changed = nextCount != pageCount || nextIndex != pageIndex
        val layoutChanged = metrics.layoutRevision >= 0L &&
            metrics.layoutRevision != currentLayoutRevision
        pageCount = nextCount
        pageIndex = nextIndex
        applyReaderChromeToView(currentWebView)
        if (layoutChanged) {
            currentLayoutRevision = metrics.layoutRevision
            closeAdjacentPageFrames()
            invalidateCommittedPageSnapshot()
        }
        if (changed) notifyPositionChanged()
    }

    fun reloadStyle(nextConfig: EpubCoreLayoutConfig) {
        val currentChapter = chapter ?: return
        val activeSession = session ?: return
        if (selectionActive) {
            clearSelection()
            listener?.onSelectionCleared()
        }
        closeAdjacentPageFrames()
        clearQueuedPageTurns()
        cancelPendingChapterTurn()
        val transitionSnapshot = cancelPageAnimation(preserveSource = true)
        cancelPageHandoff()
        cancelScheduledPreloads()
        clearPreloadedWebViews()
        val request = ++styleReloadSequence
        styleReloadRunnable?.let(::removeCallbacks)
        styleReloadFuture?.cancel(true)
        styleReloadExecutor.purge()
        lateinit var reload: Runnable
        reload = Runnable {
            if (styleReloadRunnable !== reload) return@Runnable
            styleReloadRunnable = null
            if (destroyed || request != styleReloadSequence || session !== activeSession ||
                chapter?.chapterIndex != currentChapter.chapterIndex
            ) {
                return@Runnable
            }
            styleReloadFuture = styleReloadExecutor.submit {
                val result = runCatching {
                    activeSession.prepareChapter(currentChapter.chapterIndex, nextConfig)
                }
                post {
                    if (destroyed || request != styleReloadSequence || session !== activeSession ||
                        chapter?.chapterIndex != currentChapter.chapterIndex
                    ) {
                        return@post
                    }
                    styleReloadFuture = null
                    result.onSuccess {
                        val textFragment = position?.characterPosition?.takeIf { currentChapter.sourceChapterUrl != null }
                            ?.let { offset -> "__legado_text_$offset" }
                        showChapterInternal(
                            it,
                            nextConfig,
                            pageIndex,
                            false,
                            position?.progress,
                            textFragment,
                            false
                        )
                    }.onFailure {
                        clearRecoverySnapshot(transitionSnapshot)
                        if (it !is InterruptedException) {
                            listener?.onError(it.localizedMessage ?: "EPUB style reload failed", it)
                        }
                    }
                }
            }
        }
        styleReloadRunnable = reload
        postDelayed(reload, STYLE_RELOAD_DEBOUNCE_MS)
    }

    fun onHostPause() {
        if (destroyed) return
        val unfinishedDrag = interactivePageTurn?.takeIf {
            !it.boundary && it.finishRequested != true && it.sourceView === currentWebView &&
                it.token == generation
        }
        preloadGestureActive = false
        pauseTemplateMotion(currentWebView)
        standbyWebView?.let(::pauseTemplateMotion)
        templateActiveClock.pause()
        hostPaused = true
        currentWebView.cancelPendingInteractiveDrag()
        cancelRuntimeMetricsSync()
        closeAdjacentPageFrames()
        clearQueuedPageTurns()
        cancelPendingChapterTurn()
        cancelPageAnimation()
        // A gesture which was never released is a cancellation. Restore its source
        // before resume reconciles progress; an accepted turn keeps its target.
        unfinishedDrag?.let { turn ->
            applyPage(turn.sourceView, turn.sourcePageIndex, animate = false) {
                requestRuntimeMetricsSync()
            }
        }
        invalidateCommittedPageSnapshot()
        currentWebView.onPause()
        standbyWebView?.onPause()
        preloadedWebViews.forEachValue { it.onPause() }
        loadingPreloadedWebViews.values.toList().forEach { it.onPause() }
    }

    fun onHostResume() {
        if (destroyed) return
        templateActiveClock.resume()
        hostPaused = false
        currentWebView.onResume()
        standbyWebView?.onResume()
        preloadedWebViews.forEachValue { it.onResume() }
        loadingPreloadedWebViews.values.toList().forEach { it.onResume() }
        val resumedCallbacks = templateResumeCallbacks.keys.toList()
        templateResumeCallbacks.clear()
        // Flush suspended commands before Activity.onResume sends fresh fields.
        // Posting these would let an old payload execute after the newer one.
        resumedCallbacks.forEach(Runnable::run)
        requestRuntimeMetricsSync()
        scheduleCommittedPageSnapshotRefresh("host-resumed")
        resumeScheduledPreloads()
        post { syncAdjacentPageFrames() }
    }

    fun setHostOverlayCaptureBlocked(blocked: Boolean) {
        if (hostOverlayCaptureBlocked == blocked) return
        hostOverlayCaptureBlocked = blocked
        if (blocked) pauseTemplateMotion(currentWebView)
        invalidateCommittedPageSnapshot()
        if (!blocked && !destroyed && documentReady) {
            post {
                if (destroyed || !documentReady) return@post
                scheduleCommittedPageSnapshotRefresh("host-overlay-hidden")
                scheduleQueuedPageTurnDrain()
            }
        }
    }

    fun applyPerformanceMode() {
        val nextBudget = resolvePerformanceBudget(context)
        if (nextBudget == performanceBudget) return
        performanceBudget = nextBudget
        cancelScheduledPreloads()
        clearPreloadedWebViews()
        preloadedWebViews.resize(nextBudget.preloadedWebViews)
        closeAdjacentPageFrames()
        post { syncAdjacentPageFrames() }
    }

    fun refreshPageAnimationStyle() {
        // Animation style is consumed when the next turn starts; it is not WebView state.
        // Keep the verified current snapshot and every active navigation transaction intact.
        if (destroyed || !documentReady) return
        post {
            if (destroyed || !documentReady) return@post
            scheduleCommittedPageSnapshotRefresh("page-animation-style-changed")
            syncAdjacentPageFrames()
        }
    }

    fun refreshReaderSurfaceBackground(nextConfig: EpubCoreLayoutConfig) {
        val currentChapter = chapter ?: return
        config = nextConfig
        applyReaderSurfaceBackground(nextConfig, currentChapter)
        currentWebView.setBackgroundColor(
            if (EpubReaderBackgroundPolicy.shouldUseReaderBackground(currentChapter, nextConfig)) {
                android.graphics.Color.TRANSPARENT
            } else {
                nextConfig.backgroundColor
            }
        )
        invalidateCommittedPageSnapshot()
        invalidate()
        if (!destroyed && documentReady) {
            post { scheduleCommittedPageSnapshotRefresh("reader-background-changed") }
        }
    }

    fun cancelPendingChapterLoad() {
        if (destroyed) return
        val standby = standbyWebView?.takeIf { it !== currentWebView } ?: return
        val pendingActivation = pendingActivationView === standby
        val action = EpubDirectStandbyLifecyclePolicy.decide(
            standbyToken = standby.token,
            generation = generation,
            preloading = standby.preloading,
            hasPreparedChapter = standby.preparedChapter != null,
            pendingActivation = pendingActivation,
            preserveForegroundCandidate = false
        )
        if (action == EpubDirectStandbyLifecyclePolicy.Action.DiscardAndAdvanceGeneration) {
            discardStandbyForLifecycle(preserveForegroundCandidate = false)
            clearPageTransitionSnapshot()
        }
    }

    fun onHidden() {
        warmupResumeRunnable?.let(::removeCallbacks)
        warmupResumeRunnable = null
        preloadGestureActive = false
        cancelForegroundReveal()
        closeAdjacentPageFrames()
        clearQueuedPageTurns()
        invalidateCommittedPageSnapshot()
        cancelPendingChapterTurn()
        cancelPageAnimation()
        cancelPageHandoff()
        clearRecoverySnapshot()
        cancelPendingActivation()
        cancelRenderRecovery()
        cancelStyleReload()
        advanceGeneration(preserveCommittedView = false)
        cancelScheduledPreloads()
        clearPreloadedWebViews()
        discardStandbyForLifecycle(preserveForegroundCandidate = false)
        documentReady = false
        chapter = null
        config = null
        pageIndex = 0
        pageCount = 1
        currentLayoutRevision = -1L
        pendingPageIndex = 0
        pendingLastPage = false
        pendingProgress = null
        pendingFragmentId = null
        selectionActive = false
        currentWebView.run {
            animate().cancel()
            translationX = 0f
            cancelDocumentLoadTimeout()
            cancelDocumentLoadProbe()
            visibility = INVISIBLE
            alpha = 1f
            runCatching { stopLoading() }
            runCatching { loadUrl(WebViewBlank) }
            boundSession = null
            loadedChapterKey = null
            expectedBaseUrl = null
            loadComplete = false
            runtimeInstalled = false
            preparedChapter = null
            preparedConfig = null
        }
    }

    fun releaseSession() {
        if (destroyed) return
        onHidden()
        val closingSession = session
        val closeSession = ownsBoundSession
        session = null
        ownsBoundSession = true
        if (closeSession) closingSession?.close()
    }

    fun trimMemory() {
        // Memory pressure can arrive in the middle of a swipe. Release speculative
        // work, while keeping the two bounded foreground frames and their commit.
        // Cancelling that transaction used to expose/reset the source on every trim.
        closeAdjacentPageFrames()
        invalidateCommittedPageSnapshot()
        cancelScheduledPreloads()
        clearPreloadedWebViews()
        discardStandbyForLifecycle(preserveForegroundCandidate = true)
    }

    fun destroy() {
        warmupResumeRunnable?.let(::removeCallbacks)
        warmupResumeRunnable = null
        if (destroyed) return
        destroyed = true
        interactiveFrameReadyRunnable?.let(::removeCallbacks)
        interactiveFrameReadyRunnable = null
        templateResumeCallbacks.clear()
        cancelRuntimeMetricsSync()
        cancelForegroundReveal()
        closeAdjacentPageFrames()
        clearQueuedPageTurns()
        annotationVisible = false
        generation++
        invalidateCommittedPageSnapshot()
        cancelPendingChapterTurn()
        cancelPageAnimation()
        cancelPageHandoff()
        clearRecoverySnapshot()
        cancelScheduledPreloads()
        clearPreloadedWebViews()
        cancelPendingActivation()
        cancelRenderRecovery()
        cancelStyleReload()
        styleReloadExecutor.shutdownNow()
        val closingSession = session
        val closeSession = ownsBoundSession
        session = null
        ownsBoundSession = true
        if (closeSession) closingSession?.close()
        listOfNotNull(currentWebView, standbyWebView).distinct().forEach(::destroyWebView)
        standbyWebView = null
        removeAllViews()
    }

    private fun onDocumentReady(view: ReaderWebView, token: Long) {
        if (token != generation || view.token != token) return
        if (pendingActivationView == null) {
            if (!activationGate.begin(token)) return
            pendingActivationView = view
            pendingActivationVisualVerified = false
            activationApplyInFlight = false
            scheduleActivationTimeout(view = view, token = token)
        }
        if (pendingActivationView !== view) return
        requestPendingActivationProbe(view = view, token = token, immediate = true)
    }

    /**
     * The runtime owns the page number after each reflow. Native only keeps the semantic
     * destination and repeatedly measures the same generation until it can commit it.
     */
    private fun requestPendingActivationProbe(
        view: ReaderWebView,
        token: Long,
        immediate: Boolean = false
    ) {
        if (token != generation || view.token != token || pendingActivationView !== view ||
            isSelectionPageTurnBlocked()
        ) return
        activationProbeRunnable?.let { scheduled ->
            if (!immediate) return
            removeCallbacks(scheduled)
            activationProbeRunnable = null
        }
        lateinit var probe: Runnable
        probe = Runnable {
            if (activationProbeRunnable !== probe) return@Runnable
            activationProbeRunnable = null
            probePendingActivation(view, token)
        }
        activationProbeRunnable = probe
        if (immediate) post(probe) else postDelayed(probe, READY_RETRY_MS)
    }

    private fun probePendingActivation(view: ReaderWebView, token: Long) {
        if (token != generation || view.token != token || pendingActivationView !== view ||
            isSelectionPageTurnBlocked()
        ) return
        if (activationApplyInFlight) {
            requestPendingActivationProbe(view, token)
            return
        }
        view.evaluateJavascript(MEASURE_SCRIPT) { raw ->
            if (token != generation || view.token != token || pendingActivationView !== view) {
                return@evaluateJavascript
            }
            val incomingChapter = view.preparedChapter ?: run {
                if (activationGate.cancel(token)) {
                    failPendingLoad(view, token, "EPUB chapter was unavailable during activation")
                }
                return@evaluateJavascript
            }
            val metrics = parseMetrics(raw)
            val requiresRenderableContent = EpubDirectRenderableContentPolicy.requiresRenderableContent(
                hasText = incomingChapter.plainText.isNotBlank(),
                singlePage = incomingChapter.layoutMode.singlePage,
                fullPageArtwork = incomingChapter.fullPageArtwork,
                duokanGallery = incomingChapter.duokanGallery
            )
            if (metrics == null) {
                requestPendingActivationProbe(view, token)
                return@evaluateJavascript
            }
            if (!EpubDirectRenderableContentPolicy.canActivate(
                    requiresRenderableContent,
                    metrics.hasRenderableContent
                )
            ) {
                // Fragment windows can be empty while the browser applies a font or a
                // reflow. The bounded activation timeout owns the final failure decision.
                requestPendingActivationProbe(view, token)
                return@evaluateJavascript
            }
            val incomingConfig = view.preparedConfig ?: run {
                if (activationGate.cancel(token)) {
                    failPendingLoad(view, token, "EPUB layout configuration was unavailable")
                }
                return@evaluateJavascript
            }
            val incomingPageCount = if (incomingChapter.layoutMode.singlePage) {
                1
            } else {
                metrics.pageCount.coerceAtLeast(1)
            }
            val fallbackPageIndex = when {
                pendingLastPage -> incomingPageCount - 1
                pendingProgress != null -> (
                    pendingProgress!!.coerceIn(0f, 1f) * (incomingPageCount - 1)
                    ).roundToInt()
                else -> pendingPageIndex.coerceIn(0, incomingPageCount - 1)
            }
            fun applyActivation(pageIndex: Int) {
                if (token != generation || view.token != token) return
                val activationTarget = pendingActivationTarget
                    ?: EpubDirectActivationTargetPolicy.pageIndex(pageIndex)
                val incomingPageIndex = EpubDirectActivationTargetPolicy.resolve(
                    activationTarget,
                    incomingPageCount
                )
                pendingActivationView = view
                pendingActivationVisualVerified = false
                applyCandidatePage(
                    view = view,
                    index = incomingPageIndex,
                    target = activationTarget,
                    token = token,
                    incomingChapter = incomingChapter,
                    incomingConfig = incomingConfig,
                    incomingPageCount = incomingPageCount,
                    requiresRenderableContent = requiresRenderableContent
                )
            }
            val fragmentId = pendingFragmentId
            if (fragmentId.isNullOrBlank() || incomingChapter.layoutMode.singlePage) {
                activationApplyInFlight = true
                applyActivation(fallbackPageIndex)
                return@evaluateJavascript
            }
            activationApplyInFlight = true
            view.evaluateJavascript(
                "!!(window.__legadoEpub&&window.__legadoEpub.goToFragment(" +
                    "${JSONObject.quote(fragmentId)},false));"
            ) { fragmentResult ->
                if (token != generation || view.token != token || pendingActivationView !== view) {
                    return@evaluateJavascript
                }
                if (fragmentResult != "true") {
                    activationApplyInFlight = false
                    requestPendingActivationProbe(view, token)
                    return@evaluateJavascript
                }
                view.evaluateJavascript(MEASURE_SCRIPT) { positionedRaw ->
                    if (token != generation || view.token != token || pendingActivationView !== view) {
                        return@evaluateJavascript
                    }
                    applyActivation(parseMetrics(positionedRaw)?.pageIndex ?: fallbackPageIndex)
                }
            }
        }
    }

    private fun applyCandidatePage(
        view: ReaderWebView,
        index: Int,
        target: EpubDirectActivationTargetPolicy.Target,
        token: Long,
        incomingChapter: EpubDirectChapter,
        incomingConfig: EpubCoreLayoutConfig,
        incomingPageCount: Int,
        requiresRenderableContent: Boolean
    ) {
        commitPageAndReaderChrome(
            view = view,
            index = index,
            pageCountHint = incomingPageCount,
            targetChapter = incomingChapter,
            targetConfig = incomingConfig,
            behavior = "auto",
            boundary = target.jsBoundary
        ) { appliedMetrics ->
            if (token != generation || view.token != token || pendingActivationView !== view) {
                return@commitPageAndReaderChrome
            }
            if (appliedMetrics == null) {
                activationApplyInFlight = false
                requestPendingActivationProbe(view, token)
                return@commitPageAndReaderChrome
            }
            completePageAnimationVisualState(view, token) {
                if (token != generation || view.token != token || pendingActivationView !== view) {
                    return@completePageAnimationVisualState
                }
                view.evaluateJavascript(MEASURE_VIEWPORT_SCRIPT) { raw ->
                    if (token != generation || view.token != token || pendingActivationView !== view) {
                        return@evaluateJavascript
                    }
                    val metrics = parseMetrics(raw)
                    if (!isCandidateActivationVerified(
                            target, incomingChapter.sourceChapterUrl != null,
                            requiresRenderableContent, metrics
                        )
                    ) {
                        activationApplyInFlight = false
                        requestPendingActivationProbe(view, token)
                        return@evaluateJavascript
                    }
                    val finalMetrics = checkNotNull(metrics)
                    val finalRenderSequence = view.renderState.sequence
                    val finalPayload = readerChromePayload(
                        view = view,
                        preparedChapter = incomingChapter,
                        preparedConfig = incomingConfig,
                        pageIndexOverride = finalMetrics.pageIndex,
                        pageCountOverride = finalMetrics.pageCount
                    )
                    fun completeVerifiedActivation() {
                        if (view.renderState.sequence != finalRenderSequence ||
                            !isMeasuredRenderStateCurrent(view, finalMetrics)
                        ) {
                            activationApplyInFlight = false
                            requestPendingActivationProbe(view, token)
                            return
                        }
                        activationApplyInFlight = false
                        pendingActivationVisualVerified = true
                        completeActivation(
                            view = view,
                            token = token,
                            incomingChapter = incomingChapter,
                            incomingConfig = incomingConfig,
                            incomingPageIndex = finalMetrics.pageIndex,
                            incomingPageCount = finalMetrics.pageCount,
                            incomingLayoutRevision = finalMetrics.layoutRevision
                        )
                    }
                    if (readerChromePayloadMatches(view, finalPayload)) {
                        completeVerifiedActivation()
                    } else {
                        applyReaderChromePayloadToView(view, finalPayload) { applied ->
                            if (!applied || token != generation ||
                                view.token != token || pendingActivationView !== view
                            ) {
                                activationApplyInFlight = false
                                requestPendingActivationProbe(view, token)
                                return@applyReaderChromePayloadToView
                            }
                            completePageAnimationVisualState(view, token) {
                                if (token != generation || view.token != token ||
                                    pendingActivationView !== view ||
                                    !readerChromePayloadMatches(view, finalPayload)
                                ) return@completePageAnimationVisualState
                                completeVerifiedActivation()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isCandidateActivationVerified(
        target: EpubDirectActivationTargetPolicy.Target,
        requiresViewportContent: Boolean,
        requiresRenderableContent: Boolean,
        metrics: WebMetrics?
    ): Boolean {
        if (metrics == null || metrics.layoutPending) return false
        val expectedPageIndex = EpubDirectActivationTargetPolicy.resolve(target, metrics.pageCount)
        return EpubDirectActivationTargetPolicy.isSatisfied(
            target = target,
            pageIndex = metrics.pageIndex,
            pageCount = metrics.pageCount
        ) && EpubDirectActivationVisualPolicy.canNavigate(
            requiresViewportContent = requiresViewportContent,
            requiresRenderableContent = requiresRenderableContent,
            hasRenderableContent = metrics.hasRenderableContent,
            hasViewportContent = metrics.hasViewportContent,
            expectedPageIndex = expectedPageIndex,
            actualPageIndex = metrics.pageIndex
        ) && (target.jsBoundary == null || metrics.activationTargetRevision == metrics.layoutRevision)
    }

    private fun isMeasuredRenderStateCurrent(view: ReaderWebView, metrics: WebMetrics): Boolean {
        if (metrics.layoutPending || metrics.visualRevision < view.renderState.visualRevision ||
            metrics.contentRevision < view.renderState.contentRevision
        ) return false
        reportTemplateContentChanged(view, view.token, metrics.contentRevision)
        // A fresh settled query can complete a pending bridge notification. Requiring
        // that notification to settle first makes the same page wait on its own result.
        return true
    }

    private fun completeActivation(
        view: ReaderWebView,
        token: Long,
        incomingChapter: EpubDirectChapter,
        incomingConfig: EpubCoreLayoutConfig,
        incomingPageIndex: Int,
        incomingPageCount: Int,
        incomingLayoutRevision: Long
    ) {
        if (token != generation || view.token != token || pendingActivationView !== view) return
        if (!pendingActivationVisualVerified) return
        val expectedChrome = readerChromePayload(
            view = view,
            preparedChapter = incomingChapter,
            preparedConfig = incomingConfig,
            pageIndexOverride = incomingPageIndex,
            pageCountOverride = incomingPageCount
        )
        if (!readerChromePayloadMatches(view, expectedChrome)) return
        if (isSelectionPageTurnBlocked()) {
            // Keep the fully prepared candidate, but never replace the visible chapter while
            // a DOM range is owned by the reader. Clearing the range restarts this generation.
            activationApplyInFlight = false
            pausePendingActivationForSelection()
            return
        }
        if (!activationGate.complete(token)) return
        val chapterTurn = pendingChapterTurn?.takeIf {
            it.targetToken == token && it.viewportWidth == width &&
                it.viewportHeight == height &&
                EpubDirectChapterTurnPolicy.canBind(
                    boundaryTransition = true,
                    sourceChapterIndex = it.sourceChapterIndex,
                    expectedTargetChapterIndex = it.targetChapterIndex,
                    targetChapterIndex = incomingChapter.chapterIndex,
                    logicalDirection = it.logicalDirection,
                    initialPageIndex = incomingPageIndex,
                    openAtEnd = it.logicalDirection < 0,
                    hasProgress = false,
                    targetFragmentId = null,
                    chapterStartFragmentId = incomingChapter.startFragmentId,
                    sourceRtl = it.sourceRtl,
                    targetRtl = incomingChapter.isRtlLayout()
                ) &&
                EpubDirectChapterTurnPolicy.isExpectedTargetPage(
                    logicalDirection = it.logicalDirection,
                    targetPageIndex = incomingPageIndex,
                    targetPageCount = incomingPageCount
                )
        }
        val interactiveBoundaryTurn = chapterTurn?.let(::interactiveTurnFor)
        activationTimeoutRunnable?.let(::removeCallbacks)
        activationTimeoutRunnable = null
        activationProbeRunnable?.let(::removeCallbacks)
        activationProbeRunnable = null
        pendingActivationView = null
        pendingActivationTarget = null
        pendingActivationVisualVerified = false
        activationApplyInFlight = false
        pageCount = incomingPageCount
        pageIndex = incomingPageIndex
        currentLayoutRevision = incomingLayoutRevision
        view.preparedPageCount = incomingPageCount
        view.preloadBoundaryReady = false
        view.promotedReady = false
        pendingProgress = null
        pendingFragmentId = null
        chapter = incomingChapter
        config = incomingConfig
        applyReaderSurfaceBackground(incomingConfig, incomingChapter)
        // Mark the boundary candidate as entering activation before the foreground WebView
        // changes; this keeps the existing drag overlay valid across the view swap.
        interactiveBoundaryTurn?.let { it.targetApplied = true }
        // Boundary turns keep the committed source visible until the animation overlay has
        // been mounted and bound to the verified incoming document.  Hiding the source first
        // exposes one compositor frame of the candidate before the overlay can cover it.
        val keepOutgoingVisible = chapterTurn != null ||
            (hasVisibleDocument && view !== currentWebView)
        activateWebView(view, keepOutgoingVisible = keepOutgoingVisible)
        documentReady = true
        beginFrameWarmup()
        renderRecoveryAttempts = 0
        if (interactiveBoundaryTurn != null) {
            completeInteractiveChapterActivation(
                turn = interactiveBoundaryTurn,
                incomingView = view,
                targetPageCount = incomingPageCount
            )
        } else if (chapterTurn != null) {
            completeChapterTurn(
                turn = chapterTurn,
                incomingView = view
            )
        } else {
            cancelPendingChapterTurn()
            if (keepOutgoingVisible) {
                revealActivatedWebViewWithoutAnimation(view)
            } else {
                clearRecoverySnapshot()
            }
        }
        syncAdjacentPageFrames()
        position?.let {
            listener?.onReady(it)
            listener?.onPositionChanged(it)
        }
        requestTextReaderPosition()
        requestRuntimeMetricsSync()
        scheduleCommittedPageSnapshotRefresh("chapter-activated")
        scheduleQueuedPageTurnDrain()
        resumeScheduledPreloads()
    }

    private fun completeChapterTurn(
        turn: PendingChapterTurn,
        incomingView: ReaderWebView
    ) {
        if (pendingChapterTurn !== turn || destroyed) {
            cancelPendingChapterTurn()
            clearRecoverySnapshot()
            return
        }
        val sourceBitmap = turn.sourceBitmap
        turn.sourceBitmap = null
        val targetBitmap = turn.targetBitmap
        turn.targetBitmap = null
        pendingChapterTurn = null
        chapterTurnTimeoutRunnable?.let(::removeCallbacks)
        chapterTurnTimeoutRunnable = null
        val action = EpubDirectPageAnimationPolicy.turnAction(turn.logicalDirection)
        val hasRequiredFrames = EpubDirectPageAnimationPolicy.hasRequiredBitmapFrames(
            style = turn.style,
            action = action,
            hasTargetBitmap = targetBitmap != null && !targetBitmap.isRecycled
        )
        var skipReason = "committed-source-unavailable"
        if (sourceBitmap != null && !sourceBitmap.isRecycled && hasRequiredFrames) {
            val mounted = mountPageAnimationOverlay(
                sourceBitmap = sourceBitmap,
                sourceFrame = PageFrameMetadata(
                    chapterIndex = turn.sourceChapterIndex,
                    chapterHref = turn.sourceChapterHref,
                    pageIndex = turn.sourcePageIndex,
                    pageCount = turn.sourcePageCount,
                    layoutSignature = turn.sourceLayoutSignature,
                    readerChromeContentRevision = turn.sourceReaderChromeContentRevision
                ),
                action = action,
                direction = turn.visualDirection,
                style = turn.style,
                backgroundColor = turn.backgroundColor,
                opaqueBackground = true,
                targetBitmap = targetBitmap
            )
            if (mounted != null) {
                if (bindLivePageAnimationTarget(
                        mounted = mounted,
                        view = incomingView,
                        style = turn.style,
                        action = action,
                        visualDirection = turn.visualDirection
                )
                ) {
                    hideOutgoingWebView(incomingView)
                    revealLivePageAnimationTarget(mounted.overlay)
                    clearRecoverySnapshot()
                    startOverlayAnimator(mounted.sequence, mounted.overlay, turn.style)
                    return
                }
                skipReason = "live-target-bind-failed"
                cancelPageAnimation()
            } else {
                skipReason = "overlay-mount-failed"
            }
        } else if (sourceBitmap != null && !sourceBitmap.isRecycled) {
            skipReason = "simulation-previous-target-unavailable"
            sourceBitmap.recycle()
        }
        AppLog.putDebug(
            "EPUB chapter turn animation skipped: reason=$skipReason, " +
                "sourceChapter=${turn.sourceChapterIndex}, targetChapter=${chapter?.chapterIndex}"
        )
        revealActivatedWebViewWithoutAnimation(incomingView)
        targetBitmap?.takeUnless { it.isRecycled }?.recycle()
        scheduleAdjacentPageFrameResume()
        scheduleCommittedPageSnapshotRefresh("chapter-turn-source-unavailable")
        scheduleQueuedPageTurnDrain()
    }

    private fun completeInteractiveChapterActivation(
        turn: InteractivePageTurn,
        incomingView: ReaderWebView,
        targetPageCount: Int
    ) {
        if (!isInteractivePageTurnActive(turn) || !turn.boundary) {
            return
        }
        pendingChapterTurn?.let { pending ->
            if (interactiveTurnFor(pending) === turn) {
                chapterTurnTimeoutRunnable?.let(::removeCallbacks)
                chapterTurnTimeoutRunnable = null
                pendingChapterTurn = null
                pending.sourceBitmap?.takeUnless { it.isRecycled }?.recycle()
                pending.sourceBitmap = null
                pending.targetBitmap?.takeUnless { it.isRecycled }?.recycle()
                pending.targetBitmap = null
            }
        }
        turn.targetPageCount = targetPageCount.coerceAtLeast(1)
        turn.targetToken = generation
        turn.targetApplied = true
        if (!bindLivePageAnimationTarget(
                sequence = turn.sequence,
                overlay = turn.overlay,
                view = incomingView,
                style = turn.style,
                action = EpubDirectPageAnimationPolicy.turnAction(turn.logicalDirection),
                visualDirection = turn.visualDirection
            )
        ) {
            AppLog.putDebug(
                "EPUB interactive chapter target could not bind; using direct commit: " +
                    "sourceChapter=${turn.sourceChapterIndex}, targetChapter=${chapter?.chapterIndex}"
            )
            cancelPageAnimation()
            revealActivatedWebViewWithoutAnimation(incomingView)
            scheduleAdjacentPageFrameResume()
            scheduleCommittedPageSnapshotRefresh("interactive-chapter-target-bind-failed")
            scheduleQueuedPageTurnDrain()
            return
        }
        hideOutgoingWebView(incomingView)
        revealLivePageAnimationTarget(turn.overlay)
        clearRecoverySnapshot()
        maybeStartInteractiveSettle(turn)
    }

    private fun scheduleActivationTimeout(view: ReaderWebView, token: Long) {
        activationTimeoutRunnable?.let {
            removeCallbacks(it)
            templateResumeCallbacks.remove(it)
        }
        val timeoutMillis = PAGE_ACTIVATION_TIMEOUT_MS
        val templateDeadline = if (view.preparedChapter?.readerTemplate != null) {
            templateActiveClock.now() + timeoutMillis
        } else null
        lateinit var timeout: Runnable
        timeout = Runnable {
            if (activationTimeoutRunnable !== timeout) return@Runnable
            if (destroyed || token != generation || view.token != token || pendingActivationView !== view) {
                activationTimeoutRunnable = null
                return@Runnable
            }
            if (templateDeadline != null && deferTemplateDeadline(this, timeout, templateDeadline)) return@Runnable
            activationTimeoutRunnable = null
            if (!activationGate.cancel(token)) return@Runnable
            failPendingLoad(view, token, "EPUB page activation timed out")
        }
        activationTimeoutRunnable = timeout
        postDelayed(timeout, timeoutMillis)
    }

    private fun cancelPendingActivation(view: ReaderWebView? = null) {
        if (view != null && pendingActivationView !== view) return
        cancelForegroundReveal()
        activationTimeoutRunnable?.let {
            removeCallbacks(it)
            templateResumeCallbacks.remove(it)
        }
        activationTimeoutRunnable = null
        activationProbeRunnable?.let(::removeCallbacks)
        activationProbeRunnable = null
        pendingActivationView = null
        pendingActivationTarget = null
        pendingActivationVisualVerified = false
        activationApplyInFlight = false
        activationGate.clear()
    }

    private fun pausePendingActivationForSelection() {
        activationTimeoutRunnable?.let {
            removeCallbacks(it)
            templateResumeCallbacks.remove(it)
        }
        activationTimeoutRunnable = null
        activationProbeRunnable?.let(::removeCallbacks)
        activationProbeRunnable = null
    }

    private fun resumePendingActivationAfterSelection() {
        if (isSelectionPageTurnBlocked()) return
        val view = pendingActivationView ?: return
        val token = view.token
        if (destroyed || token != generation) return
        scheduleActivationTimeout(view, token)
        requestPendingActivationProbe(view, token, immediate = true)
    }

    private fun cancelPageHandoff(clearTransitionSnapshot: Boolean = false) {
        pageApplySequence++
        pageHandoffRequest = null
        pageHandoffRestoringRequest = null
        pageHandoffTimeoutRunnable?.let(::removeCallbacks)
        pageHandoffTimeoutRunnable = null
        pageHandoffRestoreTimeoutRunnable?.let(::removeCallbacks)
        pageHandoffRestoreTimeoutRunnable = null
        if (clearTransitionSnapshot) clearPageTransitionSnapshot()
        if (currentWebView.renderState.needsMetrics) scheduleRuntimeMetricsSync()
    }

    private fun finishPageHandoff(request: Long) {
        if (pageHandoffRequest != request) return
        pageHandoffRequest = null
        pageHandoffRestoringRequest = null
        pageHandoffTimeoutRunnable?.let(::removeCallbacks)
        pageHandoffTimeoutRunnable = null
        pageHandoffRestoreTimeoutRunnable?.let(::removeCallbacks)
        pageHandoffRestoreTimeoutRunnable = null
        requestTextReaderPosition()
        if (currentWebView.renderState.needsMetrics) scheduleRuntimeMetricsSync()
    }

    private fun failPendingLoad(view: ReaderWebView, token: Long, message: String, throwable: Throwable? = null) {
        if (token != generation || view.token != token) return
        val reportedError = throwable ?: view.preparedChapter?.readerTemplate?.let {
            EpubTemplateException(it.contentHash(), message)
        }
        val committedView = currentWebView.takeIf {
            it !== view && !it.surfaceDestroyed && documentReady && chapter != null
        }
        clearQueuedPageTurns()
        cancelPendingActivation(view)
        cancelPageHandoff(clearTransitionSnapshot = true)
        if (pendingChapterTurn?.targetToken == token) {
            chapterTurnTimeoutRunnable?.let(::removeCallbacks)
            chapterTurnTimeoutRunnable = null
            releasePendingChapterTurn(resumeAdjacentFrames = false)
        }
        cancelPageAnimation()
        clearRecoverySnapshot()
        view.cancelDocumentLoadTimeout()
        view.cancelDocumentLoadProbe()
        preloadedWebViews.removeValue(view)
        removeLoadingPreload(view)
        if (standbyWebView === view) standbyWebView = null
        advanceGenerationKeepingCommittedView(view)
        if (view !== currentWebView) destroyWebView(view)
        else if (view.preparedChapter?.readerTemplate != null) {
            // A broken author script must not survive behind the fallback chapter.
            removeView(view)
            destroyWebView(view)
            currentWebView = createWebView().also { replacement ->
                addView(replacement, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            }
            documentReady = false
        }
        restoreCommittedViewAfterCandidateFailure(committedView)
        listener?.onError(message, reportedError)
    }

    private fun failCandidateSetup(
        view: ReaderWebView?,
        token: Long,
        message: String,
        throwable: Throwable
    ) {
        if (token != generation) {
            view?.takeIf { it !== currentWebView }?.let(::destroyWebView)
            return
        }
        val committedView = currentWebView.takeIf {
            it !== view && !it.surfaceDestroyed && documentReady && chapter != null
        }
        clearQueuedPageTurns()
        if (view != null) cancelPendingActivation(view) else cancelPendingActivation()
        cancelPageHandoff(clearTransitionSnapshot = true)
        if (pendingChapterTurn?.targetToken == token) {
            chapterTurnTimeoutRunnable?.let(::removeCallbacks)
            chapterTurnTimeoutRunnable = null
            releasePendingChapterTurn(resumeAdjacentFrames = false)
        }
        cancelPageAnimation()
        clearRecoverySnapshot()
        if (view != null) {
            preloadedWebViews.removeValue(view)
            removeLoadingPreload(view)
            if (standbyWebView === view) standbyWebView = null
        }
        advanceGeneration(preserveCommittedView = committedView != null)
        view?.takeIf { it !== currentWebView }?.let(::destroyWebView)
        restoreCommittedViewAfterCandidateFailure(committedView)
        listener?.onError(message, throwable)
    }

    private fun restoreCommittedViewAfterCandidateFailure(committedView: ReaderWebView?) {
        pendingPageIndex = pageIndex
        pendingLastPage = false
        pendingProgress = null
        pendingFragmentId = null
        if (committedView == null) {
            documentReady = false
            return
        }
        committedView.animate().cancel()
        committedView.translationX = 0f
        committedView.alpha = 1f
        committedView.visibility = VISIBLE
        committedView.bringToFront()
        documentReady = true
        syncAdjacentPageFrames()
        scheduleCommittedPageSnapshotRefresh("candidate-load-failed")
    }

    private fun advanceGenerationKeepingCommittedView(excluded: ReaderWebView) {
        advanceGeneration(preserveCommittedView = currentWebView !== excluded)
    }

    private fun advanceGeneration(preserveCommittedView: Boolean) {
        cancelRuntimeMetricsSync()
        cancelForegroundReveal()
        invalidateCommittedPageSnapshot()
        annotationVisible = false
        generation++
        if (!preserveCommittedView || !documentReady || chapter == null) return
        currentWebView.token = generation
        currentWebView.evaluateJavascript(
            "window.__legadoEpub&&window.__legadoEpub.setToken($generation);",
            null
        )
        scheduleCommittedPageSnapshotRefresh("generation-advanced")
    }

    private fun discardStandbyForLifecycle(preserveForegroundCandidate: Boolean) {
        val standby = standbyWebView?.takeIf { it !== currentWebView } ?: return
        val pendingActivation = pendingActivationView === standby
        val action = EpubDirectStandbyLifecyclePolicy.decide(
            standbyToken = standby.token,
            generation = generation,
            preloading = standby.preloading,
            hasPreparedChapter = standby.preparedChapter != null,
            pendingActivation = pendingActivation,
            preserveForegroundCandidate = preserveForegroundCandidate
        )
        if (action == EpubDirectStandbyLifecyclePolicy.Action.KeepRequiredCandidate) return
        if (pendingActivation) cancelPendingActivation()
        if (action == EpubDirectStandbyLifecyclePolicy.Action.DiscardAndAdvanceGeneration) {
            advanceGeneration(preserveCommittedView = true)
        }
        standbyWebView = null
        destroyWebView(standby)
    }

    private fun cancelRenderRecovery() {
        renderRecoveryRunnable?.let(::removeCallbacks)
        renderRecoveryRunnable = null
        renderRecoveryRequest = null
    }

    private fun cancelScheduledPreloads() {
        preloadRunnables.values.forEach { removeCallbacks(it.action) }
        preloadRunnables.clear()
    }

    private fun isPreloadOwned(key: String): Boolean {
        return preloadedWebViews.contains(key) || loadingPreloadedWebViews.containsKey(key) ||
            preloadRunnables.containsKey(key)
    }

    private fun hasPreloadCapacity(): Boolean {
        return preloadedWebViews.size + loadingPreloadedWebViews.size + preloadRunnables.size < preloadCapacity
    }

    private fun takeLoadingPreload(key: String): ReaderWebView? {
        val view = loadingPreloadedWebViews.remove(key) ?: return null
        return view.takeIf {
            it.loadedChapterKey == key && it.preloading && !destroyed
        } ?: run {
            destroyWebView(view)
            null
        }
    }

    private fun removeLoadingPreload(view: ReaderWebView): Boolean {
        val entry = loadingPreloadedWebViews.entries.firstOrNull { it.value === view } ?: return false
        loadingPreloadedWebViews.remove(entry.key)
        resumeScheduledPreloads()
        return true
    }

    private fun clearPreloadedWebViews() {
        preloadedWebViews.clear()
        val loading = loadingPreloadedWebViews.values.toList()
        loadingPreloadedWebViews.clear()
        loading.forEach { view ->
            if (view !== currentWebView && view !== standbyWebView) destroyWebView(view)
        }
    }

    private fun discardFailedPreload(view: ReaderWebView) {
        preloadedWebViews.removeValue(view)
        removeLoadingPreload(view)
        view.cancelDocumentLoadTimeout()
        view.cancelDocumentLoadProbe()
        view.preloading = false
        view.preloadVerificationInFlight = false
        post {
            if (view !== currentWebView && view !== standbyWebView) destroyWebView(view)
        }
    }

    private fun cancelStyleReload() {
        styleReloadSequence++
        styleReloadRunnable?.let(::removeCallbacks)
        styleReloadRunnable = null
        styleReloadFuture?.cancel(true)
        styleReloadFuture = null
        styleReloadExecutor.purge()
    }

    private fun scheduleRenderRecovery(
        replacement: ReaderWebView,
        recoveryChapter: EpubDirectChapter?,
        recoveryConfig: EpubCoreLayoutConfig?,
        initialPageIndex: Int,
        openAtEnd: Boolean,
        progress: Float?,
        fragmentId: String?,
        requireBlankCurrent: Boolean
    ): Boolean {
        cancelRenderRecovery()
        if (recoveryChapter == null || recoveryConfig == null || session == null) return false
        val now = SystemClock.uptimeMillis()
        if (now - lastRenderRecoveryAt > RENDER_PROCESS_RECOVERY_WINDOW_MS) {
            renderRecoveryAttempts = 0
        }
        if (renderRecoveryAttempts >= MAX_RENDER_PROCESS_RECOVERIES) return false
        renderRecoveryAttempts++
        lastRenderRecoveryAt = now
        val request = RenderRecoveryRequest(
            chapter = recoveryChapter,
            config = recoveryConfig,
            initialPageIndex = initialPageIndex,
            openAtEnd = openAtEnd,
            progress = progress,
            fragmentId = fragmentId
        )
        lateinit var recovery: Runnable
        recovery = Runnable {
            if (renderRecoveryRunnable !== recovery) return@Runnable
            renderRecoveryRunnable = null
            renderRecoveryRequest = null
            if (destroyed || currentWebView !== replacement || session == null ||
                (requireBlankCurrent && documentReady)
            ) {
                return@Runnable
            }
            showChapterInternal(
                request.chapter,
                request.config,
                request.initialPageIndex,
                request.openAtEnd,
                request.progress,
                request.fragmentId,
                false
            )
        }
        renderRecoveryRequest = request
        renderRecoveryRunnable = recovery
        postDelayed(recovery, RENDER_PROCESS_RECOVERY_DELAY_MS)
        return true
    }

    private fun onDocumentStable(token: Long) {
        onRuntimeTerminal(token, RuntimeTerminalKind.Ready, null)
    }

    private fun onRuntimeTerminal(
        token: Long,
        kind: RuntimeTerminalKind,
        message: String?
    ) {
        val view = findViewForRuntimeToken(token) ?: return
        if (!view.recordRuntimeTerminal(token, kind, message)) return
        view.cancelRuntimeStableTimeout()
        if (view.runtimeInstalled) consumeRuntimeTerminal(view, token)
    }

    private fun findViewForRuntimeToken(token: Long): ReaderWebView? {
        listOfNotNull(currentWebView, standbyWebView)
            .firstOrNull { it.token == token }
            ?.let { return it }
        loadingPreloadedWebViews.values.firstOrNull { it.token == token }
            ?.let { return it }
        var matched: ReaderWebView? = null
        preloadedWebViews.forEachValue { candidate ->
            if (candidate.token == token) matched = candidate
        }
        return matched
    }

    private fun consumeRuntimeTerminal(view: ReaderWebView, token: Long) {
        val terminal = view.takeRuntimeTerminal(token) ?: return
        if (terminal.kind == RuntimeTerminalKind.ReaderFontError) {
            val detail = terminal.message?.takeIf { it.isNotBlank() }
                ?: "Custom EPUB reader font failed to load"
            if (view.preloading) {
                AppLog.putDebug("EPUB preload discarded after reader font failure: $detail")
                discardFailedPreload(view)
            } else {
                failPendingLoad(view, token, detail)
            }
            return
        }
        view.runtimeStable = true
        if (view.preloading) {
            completePreload(view, token)
        } else if (token != generation) {
            // A cached or outgoing WebView can publish resource stability after another
            // chapter owns the foreground generation. It must never activate itself.
            return
        } else if (view.promotedReady && view.preloadBoundaryReady) {
            view.promotedReady = false
            verifyPromotedPreload(view, token)
        } else if (view === currentWebView && documentReady && pendingActivationView == null) {
            return
        } else {
            onDocumentReady(view, token)
        }
    }

    private fun onReaderFontError(token: Long, message: String) {
        onRuntimeTerminal(token, RuntimeTerminalKind.ReaderFontError, message)
    }

    private fun completePreload(view: ReaderWebView, token: Long) {
        if (destroyed || !view.preloading || view.token != token ||
            view.preloadVerificationInFlight
        ) return
        val chapterKey = view.loadedChapterKey ?: return
        val incomingChapter = view.preparedChapter ?: return
        val incomingConfig = view.preparedConfig ?: return
        val requiresRenderableContent = incomingChapter.requiresRenderableContent()
        fun isActive(): Boolean {
            return !destroyed && view.preloading && view.token == token &&
                view.loadedChapterKey == chapterKey
        }
        fun canCache(metrics: WebMetrics?): Boolean {
            return EpubDirectPreloadReadyPolicy.canCache(
                loadComplete = view.loadComplete,
                runtimeInstalled = view.runtimeInstalled,
                stable = view.runtimeStable,
                chromeApplied = !view.readerChromeApplyInFlight &&
                    view.appliedReaderChromeConfig != null &&
                    view.appliedReaderChromeData != null,
                metricsAvailable = metrics != null,
                requiresRenderableContent = requiresRenderableContent,
                hasRenderableContent = metrics?.hasRenderableContent == true,
                hasViewportContent = metrics?.hasViewportContent == true
            )
        }
        lateinit var verificationTimeout: Runnable
        verificationTimeout = Runnable {
            if (view.preloadVerificationInFlight && isActive()) {
                view.preloadVerificationInFlight = false
                discardFailedPreload(view)
                AppLog.putDebug("EPUB direct preload verification timed out: key=$chapterKey")
            }
        }
        var finished = false
        fun finishCache(metrics: WebMetrics, boundaryReady: Boolean) {
            if (finished || !isActive()) return
            view.preparedPageCount = metrics.pageCount.coerceAtLeast(1)
            view.preloadPageCount = view.preparedPageCount
            view.preloadPageIndex = metrics.pageIndex.coerceIn(0, view.preloadPageCount - 1)
            view.preloadLayoutRevision = metrics.layoutRevision
            val payload = readerChromePayload(
                view = view,
                preparedChapter = incomingChapter,
                preparedConfig = incomingConfig,
                pageIndexOverride = view.preloadPageIndex,
                pageCountOverride = view.preloadPageCount
            )
            applyReaderChromePayloadToView(view, payload) { applied ->
                if (finished || !isActive()) return@applyReaderChromePayloadToView
                if (!applied) {
                    view.preloadVerificationInFlight = false
                    discardFailedPreload(view)
                    return@applyReaderChromePayloadToView
                }
                completeAfterVisualState(view) {
                    if (finished || !isActive() || !readerChromePayloadMatches(view, payload)) {
                        if (!finished && isActive()) {
                            view.preloadVerificationInFlight = false
                            discardFailedPreload(view)
                        }
                        return@completeAfterVisualState
                    }
                    finished = true
                    removeCallbacks(verificationTimeout)
                    view.preloadVerificationInFlight = false
                    removeLoadingPreload(view)
                    view.preloadBoundaryReady = boundaryReady
                    view.preloadReady = true
                    view.preloading = false
                    preloadedWebViews.put(chapterKey, view)
                    // Publish the verified page count now, so the snapshot window
                    // can prepare this chapter's opening before the reader reaches it.
                    syncAdjacentPageFrames()
                }
            }
        }
        view.preloadVerificationInFlight = true
        postDelayed(verificationTimeout, PRELOAD_VERIFICATION_TIMEOUT_MS)
        view.evaluateJavascript(MEASURE_VIEWPORT_SCRIPT) { raw ->
            if (!isActive()) {
                view.preloadVerificationInFlight = false
                return@evaluateJavascript
            }
            val metrics = parseMetrics(raw)
            if (!canCache(metrics)) {
                view.preloadVerificationInFlight = false
                discardFailedPreload(view)
                return@evaluateJavascript
            }
            completeAfterVisualState(view) {
                if (!isActive()) {
                    view.preloadVerificationInFlight = false
                    return@completeAfterVisualState
                }
                view.evaluateJavascript(MEASURE_VIEWPORT_SCRIPT) { confirmedRaw ->
                    if (!isActive()) {
                        view.preloadVerificationInFlight = false
                        return@evaluateJavascript
                    }
                    val confirmedMetrics = parseMetrics(confirmedRaw)
                    if (!canCache(confirmedMetrics)) {
                        view.preloadVerificationInFlight = false
                        discardFailedPreload(view)
                        return@evaluateJavascript
                    }
                    val stableMetrics = checkNotNull(confirmedMetrics)
                    val target = view.preloadTarget
                    if (target == null) {
                        finishCache(stableMetrics, boundaryReady = false)
                        return@evaluateJavascript
                    }
                    val fallbackPageIndex = EpubDirectActivationTargetPolicy.resolve(
                        target,
                        stableMetrics.pageCount
                    )
                    applySemanticBoundaryPage(
                        view = view,
                        target = target,
                        fallbackPageIndex = fallbackPageIndex,
                        targetChapter = incomingChapter,
                        targetConfig = incomingConfig,
                        pageCountHint = stableMetrics.pageCount
                    ) {
                        if (!isActive()) {
                            view.preloadVerificationInFlight = false
                            return@applySemanticBoundaryPage
                        }
                        verifyAnimationTargetPage(
                            view = view,
                            target = target,
                            requiresRenderableContent = requiresRenderableContent,
                            requireActivationTargetRevision = true,
                            isActive = { isActive() },
                            onVerified = { metrics -> finishCache(metrics, boundaryReady = true) },
                            onFailure = { metrics ->
                                // The document is still a valid warm candidate. Keep it in
                                // the normal cache, but force foreground activation to use
                                // the existing measured-page path instead of a blind swap.
                                finishCache(metrics ?: stableMetrics, boundaryReady = false)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun verifyPromotedPreload(view: ReaderWebView, token: Long) {
        if (destroyed || token != generation || view.token != token ||
            !view.preloadBoundaryReady || pendingActivationView != null
        ) {
            return
        }
        val target = view.preloadTarget ?: run {
            onDocumentReady(view, token)
            return
        }
        val incomingChapter = view.preparedChapter ?: run {
            onDocumentReady(view, token)
            return
        }
        val incomingConfig = view.preparedConfig ?: run {
            onDocumentReady(view, token)
            return
        }
        view.evaluateJavascript(MEASURE_VIEWPORT_SCRIPT) { raw ->
            if (destroyed || token != generation || view.token != token) return@evaluateJavascript
            val metrics = parseMetrics(raw)
            val valid = metrics != null && metrics.resourcesReady &&
                isMeasuredRenderStateCurrent(view, metrics) &&
                metrics.activationTargetRevision == metrics.layoutRevision &&
                isCandidateActivationVerified(
                    target = target,
                    requiresViewportContent = incomingChapter.sourceChapterUrl != null,
                    requiresRenderableContent = incomingChapter.requiresRenderableContent(),
                    metrics = metrics
                )
            if (!valid) {
                view.preloadBoundaryReady = false
                onDocumentReady(view, token)
                return@evaluateJavascript
            }
            val verifiedMetrics = metrics ?: return@evaluateJavascript
            if (!activationGate.begin(token)) {
                onDocumentReady(view, token)
                return@evaluateJavascript
            }
            pendingActivationView = view
            pendingActivationVisualVerified = true
            activationApplyInFlight = false
            completeActivation(
                view = view,
                token = token,
                incomingChapter = incomingChapter,
                incomingConfig = incomingConfig,
                incomingPageIndex = verifiedMetrics.pageIndex,
                incomingPageCount = verifiedMetrics.pageCount,
                incomingLayoutRevision = verifiedMetrics.layoutRevision
            )
        }
    }

    private fun activateWebView(
        incoming: ReaderWebView,
        keepOutgoingVisible: Boolean = false
    ) {
        cancelForegroundReveal()
        if (incoming === currentWebView) {
            if (livePageAnimationTarget?.view !== incoming) incoming.translationX = 0f
            incoming.alpha = if (keepOutgoingVisible) 0f else 1f
            incoming.visibility = VISIBLE
            incoming.bringToFront()
            return
        }
        val outgoing = currentWebView
        pauseTemplateMotion(outgoing)
        markSnapshotSceneChanged()
        currentWebView = incoming
        standbyWebView = outgoing
        incoming.animate().cancel()
        if (livePageAnimationTarget?.view !== incoming) incoming.translationX = 0f
        incoming.alpha = if (keepOutgoingVisible) 0f else 1f
        incoming.visibility = VISIBLE
        incoming.bringToFront()
        outgoing.animate().cancel()
        outgoing.translationX = 0f
        if (!keepOutgoingVisible) outgoing.visibility = INVISIBLE
        outgoing.alpha = 1f
        // Keep the last committed chapter warm. This mirrors Moting's current/previous
        // WebView pair and avoids reparsing the chapter when the user immediately goes back.
        if (!keepOutgoingVisible) {
            runCatching { outgoing.evaluateJavascript(PAUSE_MEDIA_SCRIPT, null) }
        }
    }

    private fun hideOutgoingWebView(incoming: ReaderWebView) {
        val outgoing = standbyWebView?.takeIf { it !== incoming && it !== currentWebView }
            ?: return
        pauseTemplateMotion(outgoing)
        outgoing.animate().cancel()
        outgoing.translationX = 0f
        outgoing.alpha = 1f
        outgoing.visibility = INVISIBLE
        runCatching { outgoing.evaluateJavascript(PAUSE_MEDIA_SCRIPT, null) }
    }

    private fun revealActivatedWebViewWithoutAnimation(incoming: ReaderWebView) {
        cancelForegroundReveal()
        incoming.animate().cancel()
        incoming.translationX = 0f
        incoming.alpha = FOREGROUND_REVEAL_STAGING_ALPHA
        incoming.visibility = VISIBLE
        incoming.bringToFront()
        val token = generation
        lateinit var reveal: Runnable
        reveal = Runnable {
            if (foregroundRevealRunnable !== reveal) return@Runnable
            removeCallbacks(reveal)
            foregroundRevealRunnable = null
            foregroundRevealView = null
            if (destroyed || token != generation || incoming !== currentWebView ||
                incoming.parent !== this || !documentReady
            ) {
                if (!destroyed && incoming === currentWebView && incoming.parent === this) {
                    incoming.alpha = 1f
                    incoming.visibility = VISIBLE
                }
                return@Runnable
            }
            incoming.alpha = 1f
            incoming.bringToFront()
            hideOutgoingWebView(incoming)
            clearRecoverySnapshot()
            scheduleCommittedPageSnapshotRefresh("foreground-reveal-committed")
            scheduleQueuedPageTurnDrain()
        }
        foregroundRevealRunnable = reveal
        foregroundRevealView = incoming
        incoming.doOnPreDraw {
            if (foregroundRevealRunnable === reveal) postOnAnimation(reveal)
        }
        postDelayed(reveal, FOREGROUND_REVEAL_TIMEOUT_MS)
    }

    private fun cancelForegroundReveal() {
        foregroundRevealRunnable?.let(::removeCallbacks)
        foregroundRevealRunnable = null
        val view = foregroundRevealView
        foregroundRevealView = null
        if (!destroyed && view != null && view === currentWebView && view.parent === this) {
            view.animate().cancel()
            view.alpha = 1f
            view.visibility = VISIBLE
            view.bringToFront()
            hideOutgoingWebView(view)
        }
    }

    private fun stageCandidate(
        candidate: ReaderWebView,
        candidateChapter: EpubDirectChapter,
        config: EpubCoreLayoutConfig,
        keepCurrentVisible: Boolean
    ) {
        candidate.animate().cancel()
        if (livePageAnimationTarget?.view !== candidate) candidate.translationX = 0f
        val useReaderBackground = EpubReaderBackgroundPolicy.shouldUseReaderBackground(
            candidateChapter,
            config
        )
        candidate.setBackgroundColor(
            if (useReaderBackground) android.graphics.Color.TRANSPARENT
            else config.backgroundColor
        )
        if (!keepCurrentVisible) applyReaderSurfaceBackground(config, candidateChapter)
        candidate.visibility = VISIBLE
        // Chromium may defer compositor work for INVISIBLE/fully transparent WebViews, so
        // candidates stay attached with a tiny non-zero alpha until activation. They must
        // never be fully opaque merely because they sit behind the committed WebView: text
        // chapters deliberately use a transparent surface for the reader background, which
        // would otherwise expose every preloaded chapter underneath as a persistent ghost.
        val revealedPreview = livePageAnimationTarget?.takeIf {
            it.view === candidate && it.revealed && it.overlay === pageAnimationOverlay &&
                it.sequence == pageAnimationSequence && it.boundChapterKey == candidate.loadedChapterKey
        }
        // A warm preview may be promoted to a new token while the user's drag is still
        // showing it. Staging must not make that animation-owned live page transparent.
        candidate.alpha = if (revealedPreview != null) 1f else CANDIDATE_RENDER_ALPHA
    }

    private fun applyReaderSurfaceBackground(
        config: EpubCoreLayoutConfig,
        chapter: EpubDirectChapter? = this.chapter
    ) {
        if (!EpubReaderBackgroundPolicy.shouldUseReaderBackground(chapter, config)) {
            background = null
            setBackgroundColor(config.backgroundColor)
            return
        }
        background = ReadBookConfig.bg
            ?.constantState
            ?.newDrawable(resources)
            ?.mutate()
            ?: android.graphics.drawable.ColorDrawable(config.backgroundColor)
    }

    private fun obtainStandbyWebView(): ReaderWebView {
        val standby = standbyWebView
        if (standby != null && standby !== currentWebView) {
            runCatching { standby.animate().cancel() }
            standby.translationX = 0f
            standby.visibility = VISIBLE
            runCatching { standby.stopLoading() }
            runCatching { standby.clearHistory() }
            standby.preloading = false
            standby.preloadReady = false
            standby.preloadBoundaryReady = false
            standby.promotedReady = false
            standby.preloadVerificationInFlight = false
            standby.preparedPageCount = 1
            standby.preloadTarget = null
            standby.preloadPageIndex = 0
            standby.preloadPageCount = 1
            standby.preloadLayoutRevision = -1L
            standby.loadedChapterKey = null
            standby.expectedBaseUrl = null
            standby.loadComplete = false
            standby.runtimeInstalled = false
            standby.preparedChapter = null
            standby.preparedConfig = null
            return standby
        }
        return createWebView().also { standbyWebView = it }
    }

    private fun applyPage(
        view: ReaderWebView,
        index: Int,
        animate: Boolean,
        targetChapter: EpubDirectChapter? = chapter,
        targetConfig: EpubCoreLayoutConfig? = config,
        onApplied: ((WebMetrics?) -> Unit)? = null
    ) {
        if (isSelectionPageTurnBlocked()) return
        val activeChapter = targetChapter ?: run {
            onApplied?.invoke(null)
            return
        }
        val activeConfig = targetConfig ?: run {
            onApplied?.invoke(null)
            return
        }
        val behavior = if (animate && activeConfig.scrollMode) "smooth" else "auto"
        if (animate && isVerticalMode()) ignoreScrollUntil = SystemClock.uptimeMillis() + VERTICAL_SCROLL_SETTLE_MS
        val pageCountHint = when {
            view === currentWebView && documentReady && chapter === activeChapter -> pageCount
            view.preloadPageCount > 0 && (view.preloadReady || view.promotedReady) ->
                view.preloadPageCount
            else -> view.preparedPageCount
        }.coerceAtLeast(1)
        commitPageAndReaderChrome(
            view = view,
            index = index,
            pageCountHint = pageCountHint,
            targetChapter = activeChapter,
            targetConfig = activeConfig,
            behavior = behavior
        ) { metrics ->
            if (onApplied == null) return@commitPageAndReaderChrome
            if (behavior == "smooth") {
                postDelayed({
                    view.evaluateJavascript(MEASURE_SCRIPT) { settledRaw ->
                        onApplied(parseMetrics(settledRaw))
                    }
                }, VERTICAL_SCROLL_SETTLE_MS)
            } else {
                onApplied(metrics)
            }
        }
    }

    private fun applySemanticBoundaryPage(
        view: ReaderWebView,
        target: EpubDirectActivationTargetPolicy.Target,
        fallbackPageIndex: Int,
        targetChapter: EpubDirectChapter,
        targetConfig: EpubCoreLayoutConfig,
        pageCountHint: Int,
        onApplied: (WebMetrics?) -> Unit
    ) {
        if (isSelectionPageTurnBlocked()) return
        val boundary = target.jsBoundary ?: run {
            onApplied(null)
            return
        }
        commitPageAndReaderChrome(
            view = view,
            index = fallbackPageIndex,
            pageCountHint = pageCountHint,
            targetChapter = targetChapter,
            targetConfig = targetConfig,
            behavior = "auto",
            boundary = boundary,
            onApplied = onApplied
        )
    }

    private fun beginInteractivePageTurn(view: ReaderWebView, deltaX: Float, touchSlop: Int): Boolean {
        if (destroyed || !documentReady || view !== currentWebView || isVerticalMode() ||
            pendingChapterTurn != null || pageAnimationOverlay != null || pageAnimator != null ||
            pageHandoffRequest != null
        ) {
            return false
        }
        val sourceChapter = chapter ?: return false
        val sourceConfig = config ?: return false
        val style = EpubDirectPageAnimationPolicy.style(
            pageAnim = ReadBook.pageAnim(),
            horizontal = true
        )
        if (style == EpubDirectPageAnimationPolicy.Style.None) return false
        val visualDirection = if (deltaX < 0f) 1 else -1
        val logicalDirection = if (sourceChapter.isRtlLayout()) -visualDirection else visualDirection
        val sourcePageIndex = pageIndex
        val targetPageIndex = sourcePageIndex + logicalDirection
        adjacentPageFrames?.prepareGestureFrames(adjacentPageDirection(logicalDirection))
        if (targetPageIndex !in 0 until pageCount.coerceAtLeast(1)) {
            return beginInteractiveChapterTurn(
                view = view,
                sourceChapter = sourceChapter,
                logicalDirection = logicalDirection,
                visualDirection = visualDirection,
                style = style,
                touchSlop = touchSlop
            )
        }

        val backgroundColor = sourceConfig.backgroundColor
        // Check both frame identities before taking ownership. A cold adjacent
        // frame must not copy/recycle a full-screen source on every MOVE event.
        if (!hasCommittedPageSnapshot(view, "interactive-turn-source-unavailable")) return false
        suspendAdjacentPageFrameScheduling()
        val framePipeline = adjacentPageFrames
        val expectedFrameTarget = framePipeline?.target(adjacentPageDirection(logicalDirection))
        var cachedTargetFrame: CachedAnimationTarget? = null
        val targetBitmap = takeAdjacentPageBitmap(
            logicalDirection = logicalDirection,
            expectedChapterIndex = sourceChapter.chapterIndex,
            expectedPageIndex = targetPageIndex,
            onTaken = { cachedTargetFrame = it }
        )
        val action = EpubDirectPageAnimationPolicy.turnAction(logicalDirection)
        if (!EpubDirectPageAnimationPolicy.hasRequiredBitmapFrames(
                style = style,
                action = action,
                hasTargetBitmap = targetBitmap != null && !targetBitmap.isRecycled
            )
        ) {
            targetBitmap?.takeUnless { it.isRecycled }?.recycle()
            scheduleAdjacentPageFrameResume()
            return false
        }
        val sourceBitmap = takeCommittedPageSnapshot(
            view = view,
            reason = "interactive-turn-source-unavailable",
            transferOwnership = true
        ) ?: run {
            targetBitmap?.takeUnless { it.isRecycled }?.recycle()
            scheduleAdjacentPageFrameResume()
            return false
        }
        cancelRuntimeMetricsSync()
        clearSelection()
        val mounted = mountPageAnimationOverlay(
            sourceBitmap = sourceBitmap,
            sourceFrame = pageFrameMetadata(
                chapter = sourceChapter,
                config = sourceConfig,
                pageIndex = sourcePageIndex,
                pageCount = pageCount.coerceAtLeast(1)
            ),
            action = action,
            direction = visualDirection,
            style = style,
            backgroundColor = backgroundColor,
            opaqueBackground = true,
            targetBitmap = targetBitmap,
            targetFrameMetadata = pageFrameMetadata(
                chapter = sourceChapter,
                config = sourceConfig,
                pageIndex = targetPageIndex,
                pageCount = pageCount.coerceAtLeast(1)
            ).toAnimationTargetMetadata(currentLayoutRevision)
        ) ?: run {
            scheduleAdjacentPageFrameResume()
            return false
        }
        val request = ++pageApplySequence
        val turn = InteractivePageTurn(
            request = request,
            token = generation,
            sourceView = view,
            sourceChapterIndex = sourceChapter.chapterIndex,
            sourcePageIndex = sourcePageIndex,
            targetPageIndex = targetPageIndex,
            logicalDirection = logicalDirection,
            visualDirection = visualDirection,
            style = style,
            sequence = mounted.sequence,
            overlay = mounted.overlay,
            gesture = EpubDirectGesturePolicy.DragState(visualDirection, touchSlop),
            framePipeline = framePipeline,
            expectedFrameTarget = expectedFrameTarget,
            sourceLayoutRevision = currentLayoutRevision,
            cachedTargetFrame = cachedTargetFrame
        )
        interactivePageTurn = turn
        if (!bindLivePageAnimationTarget(
                mounted = mounted,
                view = view,
                style = style,
                action = EpubDirectPageAnimationPolicy.turnAction(logicalDirection),
                visualDirection = visualDirection
            )
        ) {
            cancelPageAnimation()
            return false
        }
        lateinit var targetTimeout: Runnable
        targetTimeout = Runnable {
            if (pageAnimationStartTimeout !== targetTimeout ||
                !isInteractivePageTurnActive(turn) || turn.request != pageApplySequence ||
                turn.restoring
            ) {
                return@Runnable
            }
            pageAnimationStartTimeout = null
            AppLog.putDebug(
                "EPUB interactive target commit timed out: " +
                    "chapter=${turn.sourceChapterIndex}, sourcePage=${turn.sourcePageIndex}, " +
                    "targetPage=${turn.targetPageIndex}, style=${turn.style}"
            )
            failInteractiveTarget(turn)
        }
        pageAnimationStartTimeout = targetTimeout
        postDelayed(targetTimeout, PAGE_ANIMATION_COMMIT_TIMEOUT_MS)

        applyPage(view, targetPageIndex, animate = false) { metrics ->
            if (!isInteractivePageTurnActive(turn) || turn.request != pageApplySequence ||
                turn.restoring
            ) {
                return@applyPage
            }
            val stillActive = {
                isInteractivePageTurnActive(turn) && turn.request == pageApplySequence && !turn.restoring
            }
            val commitTarget: (WebMetrics) -> Unit = { verifiedMetrics ->
                if (pageAnimationStartTimeout === targetTimeout) {
                    removeCallbacks(targetTimeout)
                    pageAnimationStartTimeout = null
                }
                turn.targetPageCount = verifiedMetrics.pageCount.coerceAtLeast(1)
                turn.targetApplied = true
                revealLivePageAnimationTarget(turn.overlay)
                if (turn.finishRequested == null && !turn.settling) {
                    setPageAnimationProgress(turn.overlay, turn.requestedProgress)
                }
                maybeStartInteractiveSettle(turn)
            }
            completeAppliedPage(
                view = view,
                targetPageIndex = targetPageIndex,
                metrics = metrics,
                isActive = stillActive,
                onCommitted = commitTarget,
                onNeedsProbe = {
                    verifyAnimationTargetPage(
                        view = view,
                        target = EpubDirectActivationTargetPolicy.pageIndex(targetPageIndex),
                        requiresRenderableContent = sourceChapter.requiresRenderableContent(),
                        isActive = stillActive,
                        onVerified = commitTarget,
                        onFailure = { failedMetrics ->
                            AppLog.putDebug(
                                "EPUB interactive target verification failed: " +
                                    "chapter=${turn.sourceChapterIndex}, sourcePage=${turn.sourcePageIndex}, " +
                                    "targetPage=${turn.targetPageIndex}, metrics=$failedMetrics"
                            )
                            failInteractiveTarget(turn)
                        }
                    )
                }
            )
        }
        return true
    }

    private fun beginInteractiveChapterTurn(
        view: ReaderWebView,
        sourceChapter: EpubDirectChapter,
        logicalDirection: Int,
        visualDirection: Int,
        style: EpubDirectPageAnimationPolicy.Style,
        touchSlop: Int
    ): Boolean {
        if (isBoundaryDebounced()) return false
        suspendAdjacentPageFrameScheduling()
        val pending = prepareChapterTurn(
            direction = logicalDirection,
            animate = true,
            scheduleTimeout = false,
            interactive = true
        ) ?: run {
            scheduleAdjacentPageFrameResume()
            return false
        }
        val sourceBitmap = pending.sourceBitmap ?: run {
            releasePendingChapterTurn()
            scheduleAdjacentPageFrameResume()
            return false
        }
        val targetBitmap = pending.targetBitmap
        val action = EpubDirectPageAnimationPolicy.turnAction(logicalDirection)
        if (!EpubDirectPageAnimationPolicy.hasRequiredBitmapFrames(
                style = style,
                action = action,
                hasTargetBitmap = targetBitmap != null && !targetBitmap.isRecycled
            )
        ) {
            releasePendingChapterTurn()
            scheduleAdjacentPageFrameResume()
            return false
        }
        pending.sourceBitmap = null
        pending.targetBitmap = null
        clearSelection()
        val mounted = mountPageAnimationOverlay(
            sourceBitmap = sourceBitmap,
            sourceFrame = PageFrameMetadata(
                chapterIndex = sourceChapter.chapterIndex,
                chapterHref = sourceChapter.href,
                pageIndex = pageIndex,
                pageCount = pageCount.coerceAtLeast(1),
                layoutSignature = pending.sourceLayoutSignature,
                readerChromeContentRevision = pending.sourceReaderChromeContentRevision
            ),
            action = action,
            direction = visualDirection,
            style = style,
            backgroundColor = pending.backgroundColor,
            opaqueBackground = true,
            targetBitmap = targetBitmap
        ) ?: run {
            releasePendingChapterTurn()
            scheduleAdjacentPageFrameResume()
            return false
        }
        val turn = InteractivePageTurn(
            request = ++pageApplySequence,
            token = generation,
            sourceView = view,
            sourceChapterIndex = sourceChapter.chapterIndex,
            sourcePageIndex = pageIndex,
            targetPageIndex = pageIndex + logicalDirection,
            logicalDirection = logicalDirection,
            visualDirection = visualDirection,
            style = style,
            sequence = mounted.sequence,
            overlay = mounted.overlay,
            gesture = EpubDirectGesturePolicy.DragState(visualDirection, touchSlop),
            boundary = true,
            framePipeline = adjacentPageFrames,
            expectedFrameTarget = adjacentPageFrames?.target(adjacentPageDirection(logicalDirection)),
            sourceLayoutRevision = currentLayoutRevision,
            cachedTargetFrame = pending.targetFrame
        )
        interactivePageTurn = turn
        prepareInteractiveBoundaryPreview(turn)
        return true
    }

    private fun prepareInteractiveBoundaryPreview(turn: InteractivePageTurn) {
        if (!turn.boundary || !isInteractivePageTurnActive(turn)) return
        val targetChapterIndex = session
            ?.adjacentChapterIndex(turn.sourceChapterIndex, turn.logicalDirection)
            ?: return
        val previewView = findPreparedChapterView(targetChapterIndex) ?: return
        val previewChapter = previewView.preparedChapter ?: return
        val previewConfig = previewView.preparedConfig ?: return
        val activeConfig = config ?: return
        if (previewView.loadedChapterKey != chapterKey(previewChapter, activeConfig)) return
        if (previewChapter.isRtlLayout() != chapter.isRtlLayout()) return
        val previewToken = previewView.token
        val previewKey = previewView.loadedChapterKey
        val activationTarget = EpubDirectActivationTargetPolicy.chapterBoundary(
            openAtEnd = turn.logicalDirection < 0
        )
        val fallbackPageIndex = EpubDirectActivationTargetPolicy.resolve(
            activationTarget,
            previewView.preparedPageCount
        )
        applySemanticBoundaryPage(
            view = previewView,
            target = activationTarget,
            fallbackPageIndex = fallbackPageIndex,
            targetChapter = previewChapter,
            targetConfig = previewConfig,
            pageCountHint = previewView.preparedPageCount
        ) { metrics ->
            if (!isInteractivePageTurnActive(turn) || turn.targetApplied ||
                previewView.token != previewToken || previewView.loadedChapterKey != previewKey
            ) {
                return@applySemanticBoundaryPage
            }
            previewView.preparedPageCount = metrics?.pageCount?.coerceAtLeast(1)
                ?: previewView.preparedPageCount
            verifyAnimationTargetPage(
                view = previewView,
                target = activationTarget,
                requiresRenderableContent = previewChapter.requiresRenderableContent(),
                isActive = {
                    isInteractivePageTurnActive(turn) && !turn.targetApplied &&
                        previewView.token == previewToken && previewView.loadedChapterKey == previewKey
                },
                onVerified = { verifiedMetrics ->
                    previewView.preparedPageCount = verifiedMetrics.pageCount.coerceAtLeast(1)
                    if (bindLivePageAnimationTarget(
                            sequence = turn.sequence,
                            overlay = turn.overlay,
                            view = previewView,
                            style = turn.style,
                            action = EpubDirectPageAnimationPolicy.turnAction(turn.logicalDirection),
                            visualDirection = turn.visualDirection
                        )
                    ) {
                        revealLivePageAnimationTarget(turn.overlay)
                        if (turn.finishRequested == null && !turn.settling) {
                            setPageAnimationProgress(turn.overlay, turn.requestedProgress)
                        }
                    }
                },
                onFailure = { failedMetrics ->
                    AppLog.putDebug(
                        "EPUB boundary preview not renderable; waiting for chapter activation: " +
                            "targetChapter=$targetChapterIndex, target=$activationTarget, " +
                            "metrics=$failedMetrics"
                    )
                }
            )
        }
    }

    private fun findPreparedChapterView(chapterIndex: Int): ReaderWebView? {
        fun ReaderWebView.matches(): Boolean {
            return this !== currentWebView && preparedChapter?.chapterIndex == chapterIndex &&
                loadComplete && runtimeInstalled && (preloadReady || this === standbyWebView)
        }
        standbyWebView?.takeIf { it.matches() }?.let { return it }
        var matched: ReaderWebView? = null
        preloadedWebViews.forEachValue { candidate ->
            if (matched == null && candidate.matches()) matched = candidate
        }
        return matched
    }

    private fun interactiveTurnFor(pending: PendingChapterTurn): InteractivePageTurn? {
        return interactivePageTurn?.takeIf { turn ->
            turn.boundary && turn.sourceView === pending.sourceView &&
                turn.sourceChapterIndex == pending.sourceChapterIndex &&
                turn.logicalDirection == pending.logicalDirection &&
                turn.visualDirection == pending.visualDirection &&
                turn.style == pending.style
        }
    }

    private fun releaseInteractivePendingChapterTurn(turn: InteractivePageTurn) {
        val pending = pendingChapterTurn ?: return
        if (interactiveTurnFor(pending) !== turn) return
        chapterTurnTimeoutRunnable?.let(::removeCallbacks)
        chapterTurnTimeoutRunnable = null
        releasePendingChapterTurn()
    }

    private fun stopInteractiveSettle(turn: InteractivePageTurn) {
        if (!turn.settling) return
        pageAnimator?.removeAllListeners()
        pageAnimator?.cancel()
        pageAnimator = null
        turn.settling = false
    }

    private fun updateInteractivePageTurn(view: ReaderWebView, deltaX: Float, touchYFraction: Float): Boolean {
        val turn = interactivePageTurn ?: return false
        if (turn.sourceView !== view || !isInteractivePageTurnActive(turn)) return false
        // A new swipe during settlement belongs to the next queued turn.
        if (turn.finishRequested != null || turn.settling || turn.restoring) return false
        turn.gesture.update(deltaX)
        turn.requestedProgress = turn.gesture.progress(
            viewportWidth = width,
            simulation = turn.style == EpubDirectPageAnimationPolicy.Style.Simulation
        )
        turn.overlay.updateDrag(touchYFraction)
        setPageAnimationProgress(turn.overlay, turn.requestedProgress)
        return true
    }

    private fun finishInteractivePageTurn(
        view: ReaderWebView,
        commit: Boolean,
        velocityX: Float = 0f
    ): Boolean {
        val turn = interactivePageTurn ?: return false
        if (turn.sourceView !== view || !isInteractivePageTurnActive(turn)) return false
        if (turn.finishRequested != null) return true
        turn.releaseVelocityX = velocityX
        turn.finishRequested = commit
        if (commit) {
            if (turn.boundary) {
                if (!turn.navigationDispatched) {
                    turn.navigationDispatched = true
                    dispatchBoundary(turn.logicalDirection, accepted = true)
                }
                if (isInteractivePageTurnActive(turn)) {
                    startInteractiveSettle(turn, commit = true)
                }
            } else {
                maybeStartInteractiveSettle(turn)
            }
        } else {
            startInteractiveSettle(turn, commit = false)
        }
        return true
    }

    private fun maybeStartInteractiveSettle(turn: InteractivePageTurn) {
        if (!isInteractivePageTurnActive(turn) || turn.finishRequested != true ||
            turn.settling || turn.restoring
        ) {
            return
        }
        startInteractiveSettle(turn, commit = true)
    }

    private fun startInteractiveSettle(turn: InteractivePageTurn, commit: Boolean) {
        if (!isInteractivePageTurnActive(turn) || turn.settling || turn.restoring) return
        if (commit && !turn.overlay.canAnimate) return
        turn.settling = true
        if (!commit || turn.targetApplied) {
            pageAnimationStartTimeout?.let(::removeCallbacks)
            pageAnimationStartTimeout = null
        }
        val targetProgress = EpubDirectGesturePolicy.interactiveSettleTarget(commit)
        val startProgress = turn.overlay.progress
        turn.overlay.prepareSettle(targetProgress)
        val remaining = abs(targetProgress - startProgress)
        if (remaining <= 0.001f) {
            setPageAnimationProgress(turn.overlay, targetProgress)
            turn.overlay.postOnAnimation {
                if (!isInteractivePageTurnActive(turn)) return@postOnAnimation
                completeInteractiveSettle(turn, commit)
            }
            return
        }
        val settleMotion = EpubDirectPageAnimationPolicy.settleMotion(
            style = turn.style,
            baseDurationMillis = ReadBookConfig.pageAnimationSpeed.durationMillis,
            startProgress = startProgress,
            targetProgress = targetProgress,
            viewportWidth = width,
            velocityTowardsTarget = -turn.visualDirection * turn.releaseVelocityX * if (commit) 1f else -1f
        )
        lateinit var animator: ValueAnimator
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = settleMotion.durationMillis
            // The motion already preserves finger speed and settles to rest.
            // Applying the automatic-turn easing here would change that speed.
            interpolator = LINEAR_INTERPOLATOR
            addUpdateListener {
                setPageAnimationProgress(
                    turn.overlay,
                    settleMotion.progress(it.animatedValue as Float)
                )
            }
            addListener(object : AnimatorListenerAdapter() {
                private var finished = false

                override fun onAnimationEnd(animation: Animator) = finish()

                override fun onAnimationCancel(animation: Animator) = finish()

                private fun finish() {
                    if (finished) return
                    finished = true
                    if (pageAnimator === animator) pageAnimator = null
                    if (!isInteractivePageTurnActive(turn)) return
                    completeInteractiveSettle(turn, commit)
                }
            })
        }
        pageAnimator = animator
        runCatching { animator.start() }.onFailure {
            if (pageAnimator === animator) pageAnimator = null
            AppLog.putDebug("EPUB interactive page settle failed", it)
            setPageAnimationProgress(turn.overlay, targetProgress)
            completeInteractiveSettle(turn, commit)
        }
    }

    private fun completeInteractiveSettle(turn: InteractivePageTurn, commit: Boolean) {
        if (!isInteractivePageTurnActive(turn)) return
        turn.settling = false
        if (!commit) {
            restoreInteractiveSource(turn)
            return
        }
        val targetReady = turn.targetApplied
        if (!EpubDirectGesturePolicy.canFinalizeInteractiveCommit(true, targetReady)) return
        if (turn.boundary && turn.overlay.progress < 0.999f) {
            startInteractiveSettle(turn, commit = true)
            return
        }
        completeInteractiveCommit(turn)
    }

    private fun completeInteractiveCommit(turn: InteractivePageTurn) {
        if (!isInteractivePageTurnActive(turn) || turn.restoring) return
        turn.settling = false
        if (!turn.boundary) {
            val nextCount = (turn.targetPageCount ?: pageCount).coerceAtLeast(1)
            val nextIndex = turn.targetPageIndex.coerceIn(0, nextCount - 1)
            val changed = nextCount != pageCount || nextIndex != pageIndex
            pageCount = nextCount
            pageIndex = nextIndex
            if (changed) notifyPositionChanged()
        }
        finishPageAnimationAfterFinalFrame(turn.sequence, turn.overlay)
    }

    private fun restoreInteractiveSource(turn: InteractivePageTurn) {
        if (!isInteractivePageTurnActive(turn) || turn.restoring) return
        stopInteractiveSettle(turn)
        turn.restoring = true
        setPageAnimationProgress(turn.overlay, 0f)
        pageAnimationStartTimeout?.let(::removeCallbacks)
        pageAnimationStartTimeout = null
        if (turn.boundary) {
            turn.overlay.postOnAnimation {
                if (isInteractivePageTurnActive(turn)) completeInteractiveRollback(turn)
            }
            return
        }
        val restoreRequest = ++pageApplySequence
        val restoreDeadline = templateActiveClock.now() + PAGE_HANDOFF_RESTORE_TIMEOUT_MS
        fun isRestoring() = isInteractivePageTurnActive(turn) && restoreRequest == pageApplySequence
        fun recoverSource() {
            if (!isRestoring()) return
            // Cancellation displays the source. Forward recovery normally keeps
            // the target bitmap, so discard that ownership before restoring here.
            turn.overlay.takeTargetBitmap()?.takeUnless { it.isRecycled }?.recycle()
            recoverUncommittedPageTurn(turn.sourceView, turn.token, turn.sourcePageIndex)
        }
        lateinit var restoreTimeout: Runnable
        restoreTimeout = Runnable {
            if (interactiveRestoreTimeout !== restoreTimeout ||
                !isInteractivePageTurnActive(turn)
            ) {
                return@Runnable
            }
            interactiveRestoreTimeout = null
            recoverSource()
        }
        interactiveRestoreTimeout = restoreTimeout
        postDelayed(restoreTimeout, PAGE_HANDOFF_RESTORE_TIMEOUT_MS)
        applyPage(turn.sourceView, turn.sourcePageIndex, animate = false) {
            verifyAnimationTargetPage(
                view = turn.sourceView,
                target = EpubDirectActivationTargetPolicy.pageIndex(turn.sourcePageIndex),
                requiresRenderableContent = turn.sourceView.preparedChapter.requiresRenderableContent(),
                isActive = ::isRestoring,
                onVerified = { metrics ->
                    commitPageMetrics(metrics)
                    completeInteractiveRollback(turn)
                },
                onFailure = { recoverSource() },
                deadline = restoreDeadline
            )
        }
    }

    private fun failInteractiveTarget(turn: InteractivePageTurn) {
        if (!isInteractivePageTurnActive(turn) || turn.restoring) return
        if (turn.finishRequested == true && !turn.boundary) {
            recoverUncommittedPageTurn(turn.sourceView, turn.token, turn.targetPageIndex)
        } else {
            restoreInteractiveSource(turn)
        }
    }

    private fun completeInteractiveRollback(turn: InteractivePageTurn) {
        if (!isInteractivePageTurnActive(turn) || !turn.restoring) return
        interactiveRestoreTimeout?.let(::removeCallbacks)
        interactiveRestoreTimeout = null
        if (turn.boundary) releaseInteractivePendingChapterTurn(turn)
        interactivePageTurn = null
        if (pageAnimationOverlay === turn.overlay) pageAnimationOverlay = null
        pageAnimationSequence++
        preserveAnimationSourceForCurrentOrAdjacent(turn.overlay)
        val returnedTarget = takeInteractiveTargetForCache(turn)
        releasePageAnimationOverlay(turn.overlay, deferTargetLayerRelease = true)
        returnedTarget?.let { (owner, frame) -> owner.pipeline.returnFrame(owner.target, frame) }
        requestRuntimeMetricsSync()
        scheduleAdjacentPageFrameResume()
        scheduleCommittedPageSnapshotRefresh("interactive-turn-rolled-back")
        scheduleQueuedPageTurnDrain()
    }

    private fun takeInteractiveTargetForCache(
        turn: InteractivePageTurn
    ): Pair<CachedAnimationTarget, EpubRenderedPageFrame>? {
        val owner = turn.cachedTargetFrame ?: return null
        if (animationRenderStateChanged || turn.sourceLayoutRevision != currentLayoutRevision) return null
        val bitmap = turn.overlay.takeTargetBitmap() ?: return null
        return owner to EpubRenderedPageFrame(
            owner.target.chapterIndex, owner.target.chapterHref, owner.pageIndex, owner.pageCount,
            bitmap, owner.target.layoutSignature, owner.target.readerChromeContentRevision
        )
    }

    private fun isInteractivePageTurnActive(turn: InteractivePageTurn): Boolean {
        if (interactivePageTurn !== turn || pageAnimationOverlay !== turn.overlay ||
            turn.sequence != pageAnimationSequence || destroyed
        ) {
            return false
        }
        if (!turn.boundary) {
            return turn.token == generation && turn.sourceView === currentWebView
        }
        return turn.restoring ||
            (turn.targetApplied && turn.targetToken == generation) ||
            (turn.navigationDispatched && turn.targetToken == generation) ||
            turn.sourceView === currentWebView
    }

    /** A command acknowledgement schedules a fresh check after the WebView can draw. */
    private fun completeAppliedPage(
        view: ReaderWebView,
        targetPageIndex: Int,
        metrics: WebMetrics?,
        isActive: () -> Boolean,
        onCommitted: (WebMetrics) -> Unit,
        onNeedsProbe: () -> Unit
    ) {
        if (!isActive()) return
        if (metrics == null || metrics.layoutPending) {
            onNeedsProbe()
            return
        }
        verifyAnimationTargetPage(
            view = view,
            target = EpubDirectActivationTargetPolicy.pageIndex(targetPageIndex),
            requiresRenderableContent = view.preparedChapter.requiresRenderableContent(),
            isActive = isActive,
            onVerified = onCommitted,
            onFailure = { onNeedsProbe() },
            // Try once on the normal path; the caller owns bounded retry/recovery.
            deadline = templateActiveClock.now()
        )
    }

    private fun verifyAnimationTargetPage(
        view: ReaderWebView,
        target: EpubDirectActivationTargetPolicy.Target,
        requiresRenderableContent: Boolean,
        requireActivationTargetRevision: Boolean = false,
        isActive: () -> Boolean,
        onVerified: (WebMetrics) -> Unit,
        onFailure: (WebMetrics?) -> Unit,
        deadline: Long = templateActiveClock.now() + PAGE_ANIMATION_COMMIT_TIMEOUT_MS
    ) {
        if (!isActive()) return
        fun isVerified(metrics: WebMetrics): Boolean {
            if (!isMeasuredRenderStateCurrent(view, metrics)) return false
            val expectedPageIndex = EpubDirectActivationTargetPolicy.resolve(target, metrics.pageCount)
            return EpubDirectActivationTargetPolicy.isSatisfied(
                    target = target,
                    pageIndex = metrics.pageIndex,
                    pageCount = metrics.pageCount
                ) &&
                EpubDirectActivationVisualPolicy.canNavigate(
                    requiresViewportContent = view.preparedChapter?.sourceChapterUrl != null,
                    requiresRenderableContent = requiresRenderableContent,
                    hasRenderableContent = metrics.hasRenderableContent,
                    hasViewportContent = metrics.hasViewportContent,
                    expectedPageIndex = expectedPageIndex,
                    actualPageIndex = metrics.pageIndex
                ) && (!requireActivationTargetRevision ||
                    metrics.activationTargetRevision == metrics.layoutRevision)
        }
        fun retry(metrics: WebMetrics?) {
            if (!isActive()) return
            if (templateActiveClock.now() >= deadline) {
                onFailure(metrics)
                return
            }
            postDelayed({
                verifyAnimationTargetPage(
                    view = view,
                    target = target,
                    requiresRenderableContent = requiresRenderableContent,
                    requireActivationTargetRevision = requireActivationTargetRevision,
                    isActive = isActive,
                    onVerified = onVerified,
                    onFailure = onFailure,
                    deadline = deadline
                )
            }, PAGE_HANDOFF_RETRY_MS)
        }
        // Yield for drawing first, then inspect the current page. A late/missing
        // visual notification must not pin input to a measurement made before it.
        completeAfterVisualState(view, onUnavailable = { retry(null) }) {
            if (!isActive()) return@completeAfterVisualState
            val renderSequence = view.renderState.sequence
            evaluatePageJavascript(view, MEASURE_VIEWPORT_SCRIPT) { raw ->
                if (!isActive()) return@evaluatePageJavascript
                val metrics = parseMetrics(raw)
                val previousVisualRevision = view.renderState.visualRevision
                val verified = metrics != null && isVerified(metrics) &&
                    view.renderState.measured(view.token, metrics.visualRevision, metrics.layoutPending, renderSequence)
                if (!verified) {
                    retry(metrics)
                    return@evaluatePageJavascript
                }
                val accepted = checkNotNull(metrics)
                if (view === currentWebView && view.preparedChapter?.readerTemplate == null &&
                    previousVisualRevision >= 0L && accepted.visualRevision > previousVisualRevision
                ) {
                    animationRenderStateChanged = true
                    invalidateCommittedPageSnapshot()
                    closeAdjacentPageFrames()
                }
                onVerified(accepted)
            }
        }
    }

    /** Reconcile a failed command on its existing document; only renderer loss reloads it. */
    private fun recoverUncommittedPageTurn(view: ReaderWebView, token: Long, targetPageIndex: Int) {
        if (destroyed || view !== currentWebView || token != generation ||
            view.token != token || !isPageTurnBusy()
        ) return
        val overlay = pageAnimationOverlay
        // A prepared target may already be fully visible at the end of the animation.
        // Keep those accepted pixels while its live document is being reconciled.
        val bitmap = overlay?.takeTargetBitmap() ?: overlay?.takeSourceBitmap()
        cancelPageAnimation()
        val transition = if (bitmap != null && showRecoverySnapshot(bitmap, pageTransition = true)) {
            pageTransitionSnapshotOverlay
        } else {
            preservePageTransitionSnapshot(view)
        }
        cancelPageHandoff()
        cancelRuntimeMetricsSync()
        invalidateCommittedPageSnapshot()
        closeAdjacentPageFrames()
        val request = ++pageApplySequence
        pageHandoffRequest = request
        val deadline = templateActiveClock.now() + PAGE_HANDOFF_RECOVERY_TIMEOUT_MS
        fun current() = !destroyed && isPageHandoffActive(view, request, token)
        fun finish(metrics: WebMetrics?) {
            if (!current()) return
            finishPageHandoff(request)
            if (metrics != null) {
                commitPageMetrics(metrics)
            } else {
                clearQueuedPageTurns()
                cancelRuntimeMetricsSync()
                // Stop the failed transaction without declaring its pixels ready.
                // The next user turn or resume starts a fresh measurement.
                view.renderState.requireMetrics()
            }
            clearRecoverySnapshot(transition)
            if (metrics != null) {
                syncAdjacentPageFrames()
                scheduleCommittedPageSnapshotRefresh("page-command-recovered")
                scheduleQueuedPageTurnDrain()
            } else {
                listener?.onError("页面绘制暂未完成，请再试一次", null)
            }
        }
        lateinit var timeout: Runnable
        timeout = Runnable {
            if (pageHandoffTimeoutRunnable !== timeout || !current()) return@Runnable
            if (deferTemplateDeadline(this, timeout, deadline)) return@Runnable
            finish(null)
        }
        pageHandoffTimeoutRunnable = timeout
        postDelayed(timeout, PAGE_HANDOFF_RECOVERY_TIMEOUT_MS)
        fun probe() {
            verifyAnimationTargetPage(
                view = view,
                target = EpubDirectActivationTargetPolicy.pageIndex(targetPageIndex),
                requiresRenderableContent = chapter.requiresRenderableContent(),
                isActive = ::current,
                onVerified = { finish(it) },
                onFailure = { finish(null) },
                deadline = deadline
            )
        }
        fun applied(metrics: WebMetrics?) {
            completeAppliedPage(view, targetPageIndex, metrics, ::current, { finish(it) }, ::probe)
        }
        AppLog.putDebug("EPUB page command recovery on the current document: " +
            "chapter=${chapter?.chapterIndex}, page=$targetPageIndex")
        evaluatePageJavascript(view, MEASURE_VIEWPORT_SCRIPT) { raw ->
            if (!current()) return@evaluatePageJavascript
            val metrics = parseMetrics(raw)
            if (metrics != null && metrics.pageIndex == targetPageIndex) {
                // The command may have succeeded while its acknowledgement was lost.
                applied(metrics)
            } else {
                // Absolute page assignment is idempotent. Retry it once, not a chapter load.
                applyPage(view, targetPageIndex, animate = false, onApplied = ::applied)
            }
        }
    }

    private fun startPageAnimation(
        view: ReaderWebView,
        index: Int,
        logicalDirection: Int,
        visualDirection: Int,
        animate: Boolean,
        onApplied: (WebMetrics?) -> Unit
    ): Boolean {
        val style = EpubDirectPageAnimationPolicy.style(
            pageAnim = ReadBook.pageAnim(),
            horizontal = animate && !isVerticalMode() && chapter?.layoutMode?.singlePage != true
        )
        if (style == EpubDirectPageAnimationPolicy.Style.None) return false
        val activeChapter = chapter ?: return false
        val activeConfig = config ?: return false
        val backgroundColor = activeConfig.backgroundColor
        val sourcePageIndex = pageIndex
        val source = takeCommittedPageSnapshot(
            view = view,
            reason = "programmatic-turn-source-unavailable"
        )
            ?: return false
        val targetBitmap = takeAdjacentPageBitmap(
            logicalDirection = logicalDirection,
            expectedChapterIndex = activeChapter.chapterIndex,
            expectedPageIndex = index
        )
        val targetFrameMetadata = pageFrameMetadata(
            chapter = activeChapter,
            config = activeConfig,
            pageIndex = index,
            pageCount = pageCount.coerceAtLeast(1)
        ).toAnimationTargetMetadata(currentLayoutRevision)
        val action = EpubDirectPageAnimationPolicy.turnAction(logicalDirection)
        if (!EpubDirectPageAnimationPolicy.hasRequiredBitmapFrames(
                style = style,
                action = action,
                hasTargetBitmap = targetBitmap != null && !targetBitmap.isRecycled
            )
        ) {
            source.takeUnless { it.isRecycled }?.recycle()
            targetBitmap?.takeUnless { it.isRecycled }?.recycle()
            scheduleAdjacentPageFrameResume()
            return false
        }
        val mounted = mountPageAnimationOverlay(
            sourceBitmap = source,
            sourceFrame = pageFrameMetadata(
                chapter = activeChapter,
                config = activeConfig,
                pageIndex = sourcePageIndex,
                pageCount = pageCount.coerceAtLeast(1)
            ),
            action = action,
            direction = visualDirection,
            style = style,
            backgroundColor = backgroundColor,
            opaqueBackground = true,
            targetBitmap = targetBitmap,
            targetFrameMetadata = targetFrameMetadata
        ) ?: run {
            scheduleAdjacentPageFrameResume()
            return false
        }
        val sequence = mounted.sequence
        val overlay = mounted.overlay
        if (!bindLivePageAnimationTarget(
                mounted = mounted,
                view = view,
                style = style,
                action = EpubDirectPageAnimationPolicy.turnAction(logicalDirection),
                visualDirection = visualDirection
            )
        ) {
            cancelPageAnimation()
            return false
        }

        var handoffStarted = false
        var animationStarted = false
        var commitReported = false
        var lastTargetMetrics: WebMetrics? = null
        val token = generation

        fun reportTargetCommit(metrics: WebMetrics?) {
            if (metrics != null) lastTargetMetrics = metrics
            if (commitReported) return
            commitReported = true
            onApplied(lastTargetMetrics)
        }

        fun startAfterCommit(metrics: WebMetrics) {
            if (handoffStarted) return
            handoffStarted = true
            pageAnimationStartTimeout?.let(::removeCallbacks)
            pageAnimationStartTimeout = null
            if (sequence != pageAnimationSequence || pageAnimationOverlay !== overlay || destroyed) {
                return
            }
            reportTargetCommit(metrics)
            revealLivePageAnimationTarget(overlay)
            if (!animationStarted) {
                animationStarted = true
                startOverlayAnimator(sequence, overlay, style)
            }
        }

        // A prepared target is already a complete hardware-rendered page. Animate its
        // pixels immediately while the live WebView catches up underneath. Without it,
        // retain the complete source until the live target is committed.
        pageAnimationStartTimeout = Runnable {
            if (sequence == pageAnimationSequence && pageAnimationOverlay === overlay && !destroyed) {
                recoverUncommittedPageTurn(view, token, index)
            }
        }.also {
            postDelayed(it, PAGE_ANIMATION_COMMIT_TIMEOUT_MS)
        }
        if (overlay.hasPreparedTarget) {
            animationStarted = true
            startOverlayAnimator(sequence, overlay, style)
        }
        applyPage(view, index, animate = false) { metrics ->
            if (handoffStarted || sequence != pageAnimationSequence ||
                pageAnimationOverlay !== overlay || destroyed
            ) return@applyPage
            if (metrics != null) lastTargetMetrics = metrics
            val stillActive = {
                !handoffStarted && sequence == pageAnimationSequence &&
                    pageAnimationOverlay === overlay && token == generation &&
                    view === currentWebView
            }
            completeAppliedPage(
                view = view,
                targetPageIndex = index,
                metrics = metrics,
                isActive = stillActive,
                onCommitted = ::startAfterCommit,
                onNeedsProbe = {
                    verifyAnimationTargetPage(
                        view = view,
                        target = EpubDirectActivationTargetPolicy.pageIndex(index),
                        requiresRenderableContent = activeChapter.requiresRenderableContent(),
                        isActive = stillActive,
                        onVerified = ::startAfterCommit,
                        onFailure = { recoverUncommittedPageTurn(view, token, index) }
                    )
                }
            )
        }
        return true
    }

    private fun mountPageAnimationOverlay(
        sourceBitmap: Bitmap,
        sourceFrame: PageFrameMetadata,
        action: EpubDirectPageAnimationPolicy.TurnAction,
        direction: Int,
        style: EpubDirectPageAnimationPolicy.Style,
        backgroundColor: Int,
        opaqueBackground: Boolean = false,
        targetBitmap: Bitmap? = null,
        targetFrameMetadata: EpubAnimationTargetMetadata? = null
    ): MountedPageAnimation? {
        cancelPageAnimation()
        val sequence = ++pageAnimationSequence
        val overlay = EpubDirectPageAnimationOverlay(
            context = context,
            sourceBitmap = sourceBitmap,
            action = action,
            direction = direction,
            style = style,
            backgroundColor = backgroundColor,
            opaqueBackground = opaqueBackground,
            targetBitmap = targetBitmap,
            targetFrameMetadata = targetFrameMetadata,
            simulationStartYFraction = lastPageGestureYFraction
        )
        val attached = runCatching {
            addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            overlay.bringToFront()
        }.isSuccess
        if (!attached) {
            releasePageAnimationOverlay(overlay)
            return null
        }
        pageAnimationOverlay = overlay
        animationRenderStateChanged = false
        pageAnimationSourceFrame = AnimationSourceFrame(overlay, sourceFrame)
        return MountedPageAnimation(sequence, overlay)
    }

    private fun bindLivePageAnimationTarget(
        mounted: MountedPageAnimation,
        view: ReaderWebView,
        style: EpubDirectPageAnimationPolicy.Style,
        action: EpubDirectPageAnimationPolicy.TurnAction,
        visualDirection: Int
    ): Boolean {
        return bindLivePageAnimationTarget(
            sequence = mounted.sequence,
            overlay = mounted.overlay,
            view = view,
            style = style,
            action = action,
            visualDirection = visualDirection
        )
    }

    private fun bindLivePageAnimationTarget(
        sequence: Long,
        overlay: EpubDirectPageAnimationOverlay,
        view: ReaderWebView,
        style: EpubDirectPageAnimationPolicy.Style,
        action: EpubDirectPageAnimationPolicy.TurnAction,
        visualDirection: Int
    ): Boolean {
        if (sequence != pageAnimationSequence || pageAnimationOverlay !== overlay ||
            view.parent !== this || destroyed
        ) return false
        flushDeferredLiveTargetLayerRelease()
        clearLivePageAnimationTarget()
        val originalVisibility = view.visibility
        val originalAlpha = view.alpha
        view.animate().cancel()
        view.alpha = 1f
        view.visibility = VISIBLE
        livePageAnimationTarget = LivePageAnimationTarget(
            sequence = sequence,
            overlay = overlay,
            view = view,
            style = style,
            action = action,
            visualDirection = visualDirection,
            originalLayerType = view.layerType,
            originalVisibility = originalVisibility,
            originalAlpha = originalAlpha,
            boundToken = view.token,
            boundChapterKey = view.loadedChapterKey,
            wasCurrent = view === currentWebView
        )
        view.bringToFront()
        overlay.bringToFront()
        updateLivePageAnimationTarget(overlay.progress)
        return true
    }

    private fun setPageAnimationProgress(
        overlay: EpubDirectPageAnimationOverlay,
        progress: Float
    ) {
        overlay.progress = progress
        updateLivePageAnimationTarget(overlay.progress)
        val turn = interactivePageTurn
        if (turn?.overlay === overlay && overlay.progress > 0f && !startupMotionReported &&
            startupInputAt != 0L && startupInputCount <= 3
        ) {
            startupMotionReported = true
            AppLog.putDebug("EPUB startup drag-moving: input=$startupInputCount, boundary=${turn.boundary}, " +
                "preparedTarget=${overlay.hasPreparedTarget}, liveCommitted=${turn.targetApplied}, " +
                "elapsedMs=${SystemClock.uptimeMillis() - startupInputAt}")
        }
    }

    private fun updateLivePageAnimationTarget(progress: Float) {
        val target = livePageAnimationTarget ?: return
        if (target.sequence != pageAnimationSequence || pageAnimationOverlay !== target.overlay ||
            target.view.parent !== this
        ) {
            clearLivePageAnimationTarget()
            return
        }
        val viewportWidth = width.coerceAtLeast(target.view.width).coerceAtLeast(1)
        target.view.translationX = if (target.revealed && !target.overlay.hasPreparedTarget) {
            EpubDirectPageAnimationPolicy.liveTargetTranslationX(
                style = target.style,
                action = target.action,
                visualDirection = target.visualDirection,
                progress = progress,
                viewportWidth = viewportWidth
            )
        } else {
            0f
        }
    }

    private fun revealLivePageAnimationTarget(
        overlay: EpubDirectPageAnimationOverlay
    ): Boolean {
        val target = livePageAnimationTarget ?: return false
        if (target.sequence != pageAnimationSequence || target.overlay !== overlay ||
            pageAnimationOverlay !== overlay || target.view.parent !== this
        ) return false
        prepareLivePageAnimationTarget(target)
        // The source is already represented by the overlay bitmap. A transparent live
        // preview must not also composite the source chapter underneath its target area.
        if (target.view !== currentWebView && currentWebView.parent === this) {
            // Keep the gesture owner attached/visible so its drag still receives UP/CANCEL.
            currentWebView.alpha = 0f
            markSnapshotSceneChanged()
        }
        target.revealed = true
        updateLivePageAnimationTarget(overlay.progress)
        val finalFrameWaiting = overlay.revealLiveTarget()
        overlay.bringToFront()
        if (finalFrameWaiting) {
            post { finishPageAnimationAfterFinalFrame(target.sequence, overlay) }
        }
        return true
    }

    private fun prepareLivePageAnimationTarget(target: LivePageAnimationTarget) {
        if (target.overlay.hasPreparedTarget) return
        if (!EpubDirectPageAnimationPolicy.requiresMovingLiveTarget(target.style, target.action)) return
        if (!target.transientStateSet) {
            target.view.setHasTransientState(true)
            target.transientStateSet = true
        }
        if (interactivePageTurn?.overlay === target.overlay) return
        if (!target.view.isHardwareAccelerated || target.hardwareLayerPrepared) return
        runCatching {
            markSnapshotSceneChanged(preserveCommittedPage = true)
            target.view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            target.hardwareLayerPrepared = true
            target.view.buildLayer()
        }.onFailure {
            restoreLiveTargetLayer(target.view, target.originalLayerType)
            target.hardwareLayerPrepared = false
            AppLog.putDebug("EPUB live target hardware layer preparation failed", it)
        }
    }

    private fun clearLivePageAnimationTarget(deferLayerRelease: Boolean = false) {
        val target = livePageAnimationTarget ?: return
        livePageAnimationTarget = null
        if (target.view === currentWebView || target.view.token == target.boundToken ||
            target.boundChapterKey != null && target.view.loadedChapterKey == target.boundChapterKey
        ) {
            target.view.animate().cancel()
            target.view.translationX = 0f
            if (target.view === currentWebView) {
                target.view.alpha = 1f
                target.view.visibility = VISIBLE
            } else {
                // A cancelled boundary preview keeps its original warm/candidate role.
                // A page that was current and has since been replaced stays hidden.
                target.view.alpha = if (target.wasCurrent) 1f else target.originalAlpha
                target.view.visibility = if (target.wasCurrent) INVISIBLE else target.originalVisibility
            }
        }
        if (target.hardwareLayerPrepared) {
            if (deferLayerRelease) {
                scheduleLiveTargetLayerRelease(target.view, target.originalLayerType)
            } else {
                restoreLiveTargetLayer(target.view, target.originalLayerType)
            }
        }
        if (target.transientStateSet) target.view.setHasTransientState(false)
        if (target.view !== currentWebView && currentWebView.parent === this) {
            currentWebView.animate().cancel()
            currentWebView.translationX = 0f
            currentWebView.alpha = 1f
            currentWebView.visibility = VISIBLE
            currentWebView.bringToFront()
            pageAnimationOverlay?.bringToFront()
        }
    }

    private fun scheduleLiveTargetLayerRelease(view: ReaderWebView, originalLayerType: Int) {
        flushDeferredLiveTargetLayerRelease()
        lateinit var release: Runnable
        release = Runnable {
            if (deferredLiveTargetLayerRelease !== release) return@Runnable
            deferredLiveTargetLayerRelease = null
            removeCallbacks(release)
            restoreLiveTargetLayer(view, originalLayerType)
            // A snapshot requested while the temporary hardware layer was active is
            // deliberately rejected below. Start a fresh capture now that the layer
            // has been restored and the compositor has a clean source again.
            scheduleCommittedPageSnapshotRefresh("live-target-layer-restored")
        }
        deferredLiveTargetLayerRelease = release
        completeAfterVisualState(view) { release.run() }
        postDelayed(release, LIVE_TARGET_LAYER_RELEASE_TIMEOUT_MS)
    }

    private fun flushDeferredLiveTargetLayerRelease() {
        val release = deferredLiveTargetLayerRelease ?: return
        removeCallbacks(release)
        release.run()
    }

    private fun restoreLiveTargetLayer(view: ReaderWebView, originalLayerType: Int) {
        runCatching {
            if (view.layerType != originalLayerType) {
                markSnapshotSceneChanged(preserveCommittedPage = true)
                view.setLayerType(originalLayerType, null)
            }
            view.invalidate()
            view.postInvalidateOnAnimation()
            invalidate()
        }
            .onFailure { AppLog.putDebug("EPUB live target hardware layer release failed", it) }
    }

    private fun startOverlayAnimator(
        sequence: Long,
        overlay: EpubDirectPageAnimationOverlay,
        style: EpubDirectPageAnimationPolicy.Style
    ) {
        if (sequence != pageAnimationSequence || pageAnimationOverlay !== overlay) return
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = EpubDirectPageAnimationPolicy.durationMillis(
                style,
                ReadBookConfig.pageAnimationSpeed.durationMillis
            )
            interpolator = pageAnimationInterpolator(style)
            addUpdateListener {
                setPageAnimationProgress(
                    overlay,
                    EpubDirectPageAnimationPolicy.settledProgress(
                        style,
                        it.animatedValue as Float
                    )
                )
            }
            addListener(object : AnimatorListenerAdapter() {
                private var finished = false

                override fun onAnimationEnd(animation: Animator) = finish()

                override fun onAnimationCancel(animation: Animator) = finish()

                private fun finish() {
                    if (finished) return
                    finished = true
                    finishPageAnimationAfterFinalFrame(sequence, overlay)
                }
            })
        }
        pageAnimator = animator
        runCatching { animator.start() }.onFailure {
            if (pageAnimator === animator) pageAnimator = null
            AppLog.putDebug("EPUB page animation failed", it)
            finishPageAnimationAfterFinalFrame(sequence, overlay)
        }
    }

    private fun finishPageAnimationAfterFinalFrame(
        sequence: Long,
        overlay: EpubDirectPageAnimationOverlay
    ) {
        if (sequence != pageAnimationSequence || pageAnimationOverlay !== overlay) return
        setPageAnimationProgress(overlay, 1f)
        // Preserve the complete target bitmap if its animation finishes before the live
        // WebView. Removing it on an animator timer exposes an uncommitted grey surface.
        if (!overlay.finishAnimation() || pageFinalFrameSequence == sequence) return
        pageFinalFrameSequence = sequence
        val completed = AtomicBoolean(false)
        val observer = overlay.viewTreeObserver
        lateinit var drawListener: android.view.ViewTreeObserver.OnDrawListener
        lateinit var finish: Runnable
        finish = Runnable {
            if (!completed.compareAndSet(false, true)) return@Runnable
            if (observer.isAlive) observer.removeOnDrawListener(drawListener)
            removeCallbacks(finish)
            finishPageAnimation(sequence, overlay)
        }
        drawListener = android.view.ViewTreeObserver.OnDrawListener {
            snapshotCallbackHandler.postAtFrontOfQueue(finish)
        }
        observer.addOnDrawListener(drawListener)
        overlay.invalidate()
        postDelayed(finish, FINAL_FRAME_FALLBACK_MS)
    }

    private fun pageAnimationInterpolator(style: EpubDirectPageAnimationPolicy.Style): TimeInterpolator =
        if (style == EpubDirectPageAnimationPolicy.Style.LinkedCover) {
            LINKED_COVER_INTERPOLATOR
        } else {
            LINEAR_INTERPOLATOR
        }

    private fun finishPageAnimation(
        sequence: Long,
        overlay: EpubDirectPageAnimationOverlay
    ) {
        if (sequence != pageAnimationSequence || pageAnimationOverlay !== overlay) return
        if (startupInputAt != 0L && startupInputCount <= 3) {
            AppLog.putDebug("EPUB startup turn-committed: input=$startupInputCount, " +
                "page=$pageIndex, elapsedMs=${SystemClock.uptimeMillis() - startupInputAt}")
            startupInputAt = 0L
        }
        pageFinalFrameSequence = null
        if (interactivePageTurn?.overlay === overlay) {
            interactivePageTurn = null
            interactiveRestoreTimeout?.let(::removeCallbacks)
            interactiveRestoreTimeout = null
        }
        pageAnimator = null
        pageAnimationOverlay = null
        applyReaderChromeToView(currentWebView)
        requestTextReaderPosition()
        // A drag may have committed while frame scheduling was suspended. Bind its
        // actual position before offering the outgoing page for an immediate back turn.
        syncAdjacentPageFrames()
        preserveAnimationTargetAsCommittedSnapshot(overlay)
        preserveAnimationSourceAsAdjacentFrame(overlay)
        releasePageAnimationOverlay(overlay, deferTargetLayerRelease = true)
        // The handoff already measured and verified this target. Only a newer visual
        // change needs another round trip before the next queued turn can start.
        if (!currentWebView.renderState.canCapture) {
            requestRuntimeMetricsSync()
        }
        scheduleAdjacentPageFrameResume()
        scheduleCommittedPageSnapshotRefresh("page-animation-finished")
        resumeScheduledPreloads()
        scheduleQueuedPageTurnDrain()
    }

    private fun cancelPageAnimation(
        preserveSource: Boolean = false
    ): EpubDirectRecoverySnapshotOverlay? {
        flushDeferredLiveTargetLayerRelease()
        val shouldResumeAdjacentFrames = interactivePageTurn != null || pageAnimationOverlay != null
        val existingTransition = if (preserveSource) pageTransitionSnapshotOverlay else null
        val preservedSource = if (preserveSource) {
            pageAnimationOverlay?.also(::clearAnimationSourceMetadata)?.takeSourceBitmap()
        } else {
            pageAnimationOverlay?.let(::preserveAnimationSourceForCurrentOrAdjacent)
            null
        }
        val interactiveTurn = interactivePageTurn
        interactivePageTurn = null
        interactiveRestoreTimeout?.let(::removeCallbacks)
        interactiveRestoreTimeout = null
        if (interactiveTurn != null) pageApplySequence++
        pageAnimationSequence++
        pageFinalFrameSequence = null
        pageAnimationStartTimeout?.let(::removeCallbacks)
        pageAnimationStartTimeout = null
        pageAnimator?.removeAllListeners()
        pageAnimator?.cancel()
        pageAnimator = null
        pageAnimationOverlay?.let { overlay ->
            releasePageAnimationOverlay(overlay)
        }
        pageAnimationOverlay = null
        if (shouldResumeAdjacentFrames) requestRuntimeMetricsSync()
        if (shouldResumeAdjacentFrames) scheduleAdjacentPageFrameResume()
        if (preservedSource != null) {
            if (destroyed) {
                preservedSource.takeUnless { it.isRecycled }?.recycle()
            } else if (showRecoverySnapshot(preservedSource, pageTransition = true)) {
                return recoverySnapshotOverlay
            }
        }
        return existingTransition
    }

    private fun preservePageTransitionSnapshot(view: ReaderWebView): EpubDirectRecoverySnapshotOverlay? {
        pageTransitionSnapshotOverlay?.let {
            it.bringToFront()
            return it
        }
        val bitmap = takeCommittedPageSnapshot(
            view = view,
            reason = "page-handoff-source-unavailable"
        ) ?: return null
        return if (showRecoverySnapshot(bitmap, pageTransition = true)) {
            pageTransitionSnapshotOverlay
        } else {
            null
        }
    }

    private fun releasePageAnimationOverlay(
        overlay: EpubDirectPageAnimationOverlay,
        deferTargetLayerRelease: Boolean = false
    ) {
        if (livePageAnimationTarget?.overlay === overlay) {
            clearLivePageAnimationTarget(deferLayerRelease = deferTargetLayerRelease)
        }
        clearAnimationSourceMetadata(overlay)
        runCatching { if (overlay.parent === this) removeView(overlay) }
        overlay.release()
    }

    private fun showRecoverySnapshot(
        bitmap: Bitmap,
        pageTransition: Boolean = false
    ): Boolean {
        clearRecoverySnapshot()
        val overlay = EpubDirectRecoverySnapshotOverlay(context, bitmap)
        val attached = runCatching {
            addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            overlay.bringToFront()
        }.isSuccess
        if (!attached) {
            releaseRecoverySnapshotOverlay(overlay)
            return false
        }
        recoverySnapshotOverlay = overlay
        pageTransitionSnapshotOverlay = overlay.takeIf { pageTransition }
        return true
    }

    private fun clearRecoverySnapshot(expected: EpubDirectRecoverySnapshotOverlay?) {
        if (expected == null) return
        if (recoverySnapshotOverlay !== expected) return
        clearRecoverySnapshot()
    }

    private fun clearRecoverySnapshot() {
        val overlay = recoverySnapshotOverlay ?: return
        recoverySnapshotOverlay = null
        if (pageTransitionSnapshotOverlay === overlay) pageTransitionSnapshotOverlay = null
        releaseRecoverySnapshotOverlay(overlay)
    }

    private fun clearPageTransitionSnapshot() {
        clearRecoverySnapshot(pageTransitionSnapshotOverlay)
    }

    private fun releaseRecoverySnapshotOverlay(overlay: EpubDirectRecoverySnapshotOverlay) {
        runCatching { if (overlay.parent === this) removeView(overlay) }
        overlay.release()
    }

    private fun committedPageSnapshotKey(
        view: ReaderWebView = currentWebView
    ): EpubCommittedPageSnapshotKey? {
        val activeChapter = chapter ?: return null
        val activeConfig = config ?: return null
        if (destroyed || !documentReady || view !== currentWebView || view.token != generation ||
            view.width <= 0 || view.height <= 0 || view.visibility != VISIBLE
        ) {
            return null
        }
        return EpubCommittedPageSnapshotKey(
            generation = generation,
            token = view.token,
            viewIdentity = System.identityHashCode(view),
            chapterIndex = activeChapter.chapterIndex,
            pageIndex = pageIndex,
            layoutRevision = currentLayoutRevision,
            visualRevision = view.renderState.visualRevision,
            viewportWidth = view.width,
            viewportHeight = view.height,
            readerChromeGeometryKey = EpubPageFrameTarget.readerChromeGeometryKey(
                activeChapter,
                activeConfig
            )
        )
    }

    private fun preserveAnimationSourceAsAdjacentFrame(
        overlay: EpubDirectPageAnimationOverlay
    ): Boolean {
        val frame = takeAnimationSourceFrame(overlay) ?: return false
        val accepted = adjacentPageFrames?.offerFrame(frame) ?: run {
            frame.close()
            false
        }
        if (accepted) {
            AppLog.putDebug(
                "EPUB animation source cached for back turn: " +
                    "chapter=${frame.chapterIndex}, page=${frame.pageIndex}"
            )
        }
        return accepted
    }

    private fun preserveAnimationTargetAsCommittedSnapshot(
        overlay: EpubDirectPageAnimationOverlay
    ): Boolean {
        val metadata = overlay.targetFrameMetadata()
        val bitmap = overlay.takeTargetBitmap() ?: return false
        if (!targetFrameMatchesCommittedPage(metadata)) {
            bitmap.takeUnless { it.isRecycled }?.recycle()
            return false
        }
        return preserveBitmapAsCommittedSnapshot(
            bitmap = bitmap,
            view = currentWebView,
            requireViewportSize = false
        )
    }

    private fun targetFrameMatchesCommittedPage(
        metadata: EpubAnimationTargetMetadata?
    ): Boolean {
        val activeChapter = chapter ?: return false
        val activeConfig = config ?: return false
        // Source images can differ between independent virtual/live documents even when
        // their local layout counters happen to match. Capture the settled live page.
        if (activeChapter.sourceImages?.resources?.isNotEmpty() == true ||
            animationRenderStateChanged || !currentWebView.renderState.canCapture
        ) return false
        val key = committedPageSnapshotKey(currentWebView) ?: return false
        if (metadata == null || metadata.layoutRevision != currentLayoutRevision) return false
        if (metadata.chapterIndex != key.chapterIndex ||
            metadata.chapterHref != activeChapter.href ||
            metadata.pageIndex != key.pageIndex
        ) return false
        if (metadata.layoutSignature != EpubPageFrameTarget.layoutSignature(
                activeConfig,
                currentWebView.width,
                currentWebView.height
            )
        ) return false
        return metadata.readerChromeContentRevision ==
            EpubPageFrameTarget.readerChromeContentRevision(
                activeChapter,
                activeConfig,
                readerChromeTemplate
            )
    }

    private fun PageFrameMetadata.toAnimationTargetMetadata(
        layoutRevision: Long
    ): EpubAnimationTargetMetadata {
        return EpubAnimationTargetMetadata(
            chapterIndex = chapterIndex,
            chapterHref = chapterHref,
            pageIndex = pageIndex,
            layoutSignature = layoutSignature,
            readerChromeContentRevision = readerChromeContentRevision,
            layoutRevision = layoutRevision
        )
    }

    private fun preserveAnimationSourceForCurrentOrAdjacent(
        overlay: EpubDirectPageAnimationOverlay
    ): Boolean {
        val frame = takeAnimationSourceFrame(overlay) ?: return false
        val activeChapter = chapter
        val activeConfig = config
        val matchesCurrentPage = activeChapter != null && activeConfig != null &&
            activeChapter.chapterIndex == frame.chapterIndex &&
            activeChapter.href == frame.chapterHref && pageIndex == frame.pageIndex &&
            frame.layoutSignature == EpubPageFrameTarget.layoutSignature(
                activeConfig,
                width,
                height
            ) && frame.readerChromeContentRevision ==
            EpubPageFrameTarget.readerChromeContentRevision(
                activeChapter,
                activeConfig,
                readerChromeTemplate
            )
        if (matchesCurrentPage) {
            val bitmap = frame.takeBitmap() ?: run {
                frame.close()
                return false
            }
            frame.close()
            return preserveBitmapAsCommittedSnapshot(
                bitmap = bitmap,
                view = currentWebView,
                requireViewportSize = false
            )
        }
        val accepted = adjacentPageFrames?.offerFrame(frame) ?: run {
            frame.close()
            false
        }
        return accepted
    }

    private fun takeAnimationSourceFrame(
        overlay: EpubDirectPageAnimationOverlay
    ): EpubRenderedPageFrame? {
        if (animationRenderStateChanged) return null
        val source = pageAnimationSourceFrame?.takeIf { it.overlay === overlay } ?: return null
        pageAnimationSourceFrame = null
        val bitmap = overlay.takeSourceBitmap() ?: return null
        return EpubRenderedPageFrame(
            chapterIndex = source.metadata.chapterIndex,
            chapterHref = source.metadata.chapterHref,
            pageIndex = source.metadata.pageIndex,
            pageCount = source.metadata.pageCount,
            bitmap = bitmap,
            layoutSignature = source.metadata.layoutSignature,
            readerChromeContentRevision = source.metadata.readerChromeContentRevision
        )
    }

    private fun pageFrameMetadata(
        chapter: EpubDirectChapter,
        config: EpubCoreLayoutConfig,
        pageIndex: Int,
        pageCount: Int
    ): PageFrameMetadata {
        return PageFrameMetadata(
            chapterIndex = chapter.chapterIndex,
            chapterHref = chapter.href,
            pageIndex = pageIndex,
            pageCount = pageCount,
            layoutSignature = EpubPageFrameTarget.layoutSignature(config, width, height),
            readerChromeContentRevision = EpubPageFrameTarget.readerChromeContentRevision(
                chapter,
                config,
                readerChromeTemplate
            )
        )
    }

    private fun clearAnimationSourceMetadata(overlay: EpubDirectPageAnimationOverlay) {
        if (pageAnimationSourceFrame?.overlay === overlay) pageAnimationSourceFrame = null
    }

    private fun preserveBitmapAsCommittedSnapshot(
        bitmap: Bitmap,
        view: ReaderWebView,
        requireViewportSize: Boolean
    ): Boolean {
        val key = committedPageSnapshotKey(view) ?: run {
            bitmap.takeUnless { it.isRecycled }?.recycle()
            return false
        }
        if (bitmap.isRecycled || requireViewportSize &&
            (bitmap.width != key.viewportWidth || bitmap.height != key.viewportHeight)
        ) {
            bitmap.takeUnless { it.isRecycled }?.recycle()
            return false
        }
        val request = committedPageSnapshots.begin(key, snapshotSceneRevision)
        return committedPageSnapshots.complete(request, key, snapshotSceneRevision, bitmap)
    }

    private fun hasCommittedPageSnapshot(view: ReaderWebView, reason: String): Boolean {
        if (view.renderState.layoutPending) return false
        val key = committedPageSnapshotKey(view) ?: return false
        if (committedPageSnapshots.peek(key)?.isRecycled == false) return true
        if (view.renderState.canCapture) scheduleCommittedPageSnapshotRefresh(reason, expectedKey = key)
        return false
    }

    private fun takeCommittedPageSnapshot(
        view: ReaderWebView,
        reason: String,
        transferOwnership: Boolean = false
    ): Bitmap? {
        if (view.renderState.layoutPending) return null
        val key = committedPageSnapshotKey(view) ?: return null
        // A metrics-only query does not change these validated pixels. Actual
        // layout/content notifications invalidate the cache before requesting it.
        val cached = if (transferOwnership) committedPageSnapshots.take(key) else committedPageSnapshots.peek(key)
        if (cached != null && !cached.isRecycled) {
            // The overlay now owns this immutable bitmap. Rollback already
            // returns its source through preserveAnimationSourceForCurrentOrAdjacent.
            if (transferOwnership) return cached
            val copy = runCatching { cached.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
            if (copy != null && !copy.isRecycled) return copy
        }
        if (!view.renderState.canCapture) return null
        scheduleCommittedPageSnapshotRefresh(reason, expectedKey = key)
        return null
    }

    private fun scheduleCommittedPageSnapshotRefresh(
        reason: String,
        attempt: Int = 0,
        blockedAttempt: Int = 0,
        expectedKey: EpubCommittedPageSnapshotKey? = null
    ) {
        if (frameRenderer) return
        val key = expectedKey ?: committedPageSnapshotKey() ?: return
        val view = currentWebView
        if (committedPageSnapshotKey(view) != key) return
        if (hasCommittedSnapshotCaptureBlocker(view)) {
            if (blockedAttempt >= MAX_BLOCKED_SNAPSHOT_RETRIES) {
                allowAnimationMaterialFallback(key)
                return
            }
            if (committedSnapshotRefreshRunnable != null) return
            lateinit var retry: Runnable
            retry = Runnable {
                if (committedSnapshotRefreshRunnable !== retry) return@Runnable
                committedSnapshotRefreshRunnable = null
                scheduleCommittedPageSnapshotRefresh(
                    reason = reason,
                    attempt = attempt,
                    blockedAttempt = blockedAttempt + 1,
                    expectedKey = key
                )
            }
            committedSnapshotRefreshRunnable = retry
            postDelayed(retry, SNAPSHOT_CAPTURE_RETRY_MS)
            return
        }
        if (committedPageSnapshots.contains(key)) {
            resumeTemplateMotion(view)
            return
        }
        if (committedPageSnapshots.isPending(key)) return
        if (!settleTemplateMotionForSnapshot(view, reason)) return
        if (committedSnapshotRefreshRunnable != null) return
        lateinit var refresh: Runnable
        refresh = Runnable {
            if (committedSnapshotRefreshRunnable !== refresh) return@Runnable
            committedSnapshotRefreshRunnable = null
            if (committedPageSnapshotKey(view) != key) {
                return@Runnable
            }
            if (hasCommittedSnapshotCaptureBlocker(view)) {
                scheduleCommittedPageSnapshotRefresh(
                    reason = reason,
                    attempt = attempt,
                    blockedAttempt = blockedAttempt + 1,
                    expectedKey = key
                )
                return@Runnable
            }
            val request = committedPageSnapshots.begin(key, snapshotSceneRevision)
            completePageAnimationVisualState(view, request.sequence, onUnavailable = {
                failCommittedPageSnapshotCapture(
                    request = request,
                    view = view,
                    reason = reason,
                    attempt = attempt,
                    failure = "visual-commit-unavailable",
                    windowRect = null
                )
            }) {
                completeAfterCompositorFrames(view) {
                    if (committedPageSnapshotKey(view) != key) {
                        committedPageSnapshots.fail(request)
                        return@completeAfterCompositorFrames
                    }
                    if (hasCommittedSnapshotCaptureBlocker(view)) {
                        committedPageSnapshots.fail(request)
                        scheduleCommittedPageSnapshotRefresh(
                            reason = reason,
                            attempt = attempt,
                            blockedAttempt = blockedAttempt + 1,
                            expectedKey = key
                        )
                        return@completeAfterCompositorFrames
                    }
                    requestCommittedPageSnapshot(
                        request = request,
                        view = view,
                        reason = reason,
                        attempt = attempt,
                        blockedAttempt = blockedAttempt,
                        backgroundColor = config?.backgroundColor
                            ?: android.graphics.Color.WHITE,
                        requireVisualContent = chapter.requiresRenderableContent()
                    )
                }
            }
        }
        committedSnapshotRefreshRunnable = refresh
        val delay = if (attempt <= 0) 0L else SNAPSHOT_CAPTURE_RETRY_MS * attempt
        if (delay == 0L) postOnAnimation(refresh) else postDelayed(refresh, delay)
    }

    private fun hasCommittedSnapshotCaptureBlocker(view: ReaderWebView): Boolean {
        return hostPaused || !view.renderState.canCapture ||
            hasUnexpectedVisibleReader(view) ||
            hostOverlayCaptureBlocked || !isAttachedToWindow || windowVisibility != VISIBLE || !view.isShown ||
            view.alpha < 0.999f || abs(view.translationX) > 0.5f ||
            pageAnimationOverlay != null || pageAnimator != null ||
            recoverySnapshotOverlay != null || pageHandoffRequest != null ||
            pendingActivationView != null || deferredLiveTargetLayerRelease != null ||
            foregroundRevealRunnable != null || view.readerChromeApplyInFlight
    }

    private fun templateMotionScript(view: ReaderWebView, state: String): String =
        "(function(){var api=window.__legadoEpub;" +
            "return !!(api&&api.token===${view.token}&&api.setTemplateMotionState&&" +
            "api.setTemplateMotionState('$state'));})()"

    private fun pauseTemplateMotion(view: ReaderWebView) {
        if (view.preparedChapter?.readerTemplate == null || view.surfaceDestroyed ||
            view.templateMotionState == "paused"
        ) return
        view.templateMotionState = "paused"
        runCatching { view.evaluateJavascript(templateMotionScript(view, "paused"), null) }
    }

    private fun resumeTemplateMotion(view: ReaderWebView) {
        if (frameRenderer || destroyed || preloadGestureActive || view !== currentWebView ||
            view.preparedChapter?.readerTemplate == null ||
            view.templateMotionSettlePending || hasCommittedSnapshotCaptureBlocker(view)
        ) return
        // The runtime settles itself on page changes. A verified animation target
        // can populate the native cache without another capture, so the Kotlin
        // state alone cannot prove that this particular page is already playing.
        // The host's idempotent command uses the runtime's actual motion state.
        view.templateMotionState = "running"
        runCatching { view.evaluateJavascript(templateMotionScript(view, "running"), null) }
    }

    private fun settleTemplateMotionForSnapshot(view: ReaderWebView, reason: String): Boolean {
        if (view.preparedChapter?.readerTemplate == null) return true
        if (view.templateMotionSettlePending) return false
        val snapshotKey = committedPageSnapshotKey(view)
        if (snapshotKey != null && view.templateMotionFailedSnapshotKey == snapshotKey) {
            allowAnimationMaterialFallback(snapshotKey)
            return false
        }
        if (view.templateMotionState == "settled") return true
        val token = view.token
        val chapterKey = view.loadedChapterKey
        view.templateMotionState = "settled"
        view.templateMotionSettlePending = true
        // This uses the template command acknowledgement, not a guessed animation
        // duration. The next capture obtains a new key after the settled revision.
        fun settle(attempt: Int) {
            evaluateReaderCommand(view, templateMotionScript(view, "settled")) { result ->
                if (view.token != token || view.loadedChapterKey != chapterKey) return@evaluateReaderCommand
                if (destroyed || view.surfaceDestroyed || view !== currentWebView) return@evaluateReaderCommand
                if (result == null && attempt == 0) {
                    // This setter is idempotent. Obtain its real acknowledgement again
                    // instead of treating a missing return value as a broken template.
                    settle(attempt + 1)
                    return@evaluateReaderCommand
                }
                view.templateMotionSettlePending = false
                if (result != "true") {
                    view.templateMotionState = "unknown"
                    view.templateMotionFailedSnapshotKey = committedPageSnapshotKey(view)
                    view.templateMotionFailedSnapshotKey?.let(::allowAnimationMaterialFallback)
                    AppLog.putDebug("EPUB template snapshot motion confirmation unavailable")
                    resumeTemplateMotion(view)
                    return@evaluateReaderCommand
                }
                view.templateMotionFailedSnapshotKey = null
                requestRuntimeMetricsSync()
                scheduleCommittedPageSnapshotRefresh(reason)
            }
        }
        settle(0)
        return false
    }

    private fun hasUnexpectedVisibleReader(current: ReaderWebView): Boolean {
        for (index in 0 until childCount) {
            val other = getChildAt(index)
            if (other is ReaderWebView && other !== current &&
                other.visibility == VISIBLE && other.alpha > CANDIDATE_RENDER_ALPHA
            ) return true
        }
        return false
    }

    private fun requestCommittedPageSnapshot(
        request: EpubCommittedPageSnapshotCache.Request,
        view: ReaderWebView,
        reason: String,
        attempt: Int,
        blockedAttempt: Int,
        backgroundColor: Int,
        requireVisualContent: Boolean
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            if (!legacySnapshotBackendReported) {
                legacySnapshotBackendReported = true
                AppLog.putDebug(
                    "EPUB committed snapshot uses validated software capture: api=${Build.VERSION.SDK_INT}"
                )
            }
            val bitmap = captureView(
                view = view,
                backgroundColor = backgroundColor,
                requireVisualContent = requireVisualContent
            )
            if (bitmap == null) {
                failCommittedPageSnapshotCapture(
                    request = request,
                    view = view,
                    reason = reason,
                    attempt = attempt,
                    failure = "legacy-software-capture-empty",
                    windowRect = committedSnapshotWindowRect(view)
                )
                return
            }
            if (committedPageSnapshots.complete(
                    request,
                    committedPageSnapshotKey(view),
                    snapshotSceneRevision,
                    bitmap
                )
            ) {
                runCatching { bitmap.prepareToDraw() }
                resumeTemplateMotion(view)
                onCurrentSnapshotReady()
            }
            return
        }

        val activity = findHostActivity()
        val windowRect = committedSnapshotWindowRect(view)
        if (activity == null || windowRect == null) {
            failCommittedPageSnapshotCapture(
                request = request,
                view = view,
                reason = reason,
                attempt = attempt,
                failure = if (activity == null) "activity-window-unavailable" else "invalid-window-rect",
                windowRect = windowRect
            )
            return
        }
        val bitmapSize = EpubSnapshotSizePolicy.sizeFor(
            width = request.key.viewportWidth,
            height = request.key.viewportHeight,
            pixelBudget = snapshotPixelBudget
        )
        val bitmap = runCatching {
            Bitmap.createBitmap(
                bitmapSize.width,
                bitmapSize.height,
                Bitmap.Config.ARGB_8888
            )
        }.getOrElse { error ->
            failCommittedPageSnapshotCapture(
                request = request,
                view = view,
                reason = reason,
                attempt = attempt,
                failure = "bitmap-allocation-failed",
                windowRect = windowRect,
                throwable = error
            )
            return
        }
        val persistentPipeline = adjacentPageFrames
        val persistentRequest = persistentPipeline?.persistentWriteRequest(
            request.key.chapterIndex, request.key.pageIndex, pageCount
        )
        runCatching {
            PixelCopy.request(
                activity.window,
                windowRect,
                bitmap,
                { result ->
                    var persistentPixels = if (result == PixelCopy.SUCCESS && persistentRequest != null) {
                        EpubSnapshotPixels.copy(bitmap)
                    } else null
                    snapshotCallbackHandler.post applyCapture@{
                        try {
                            if (result != PixelCopy.SUCCESS) {
                                failCommittedPageSnapshotCapture(
                                    request = request,
                                    view = view,
                                    reason = reason,
                                    attempt = attempt,
                                    failure = "pixel-copy-${pixelCopyResultName(result)}",
                                    windowRect = windowRect,
                                    pixelCopyResult = result,
                                    bitmap = bitmap
                                )
                                return@applyCapture
                            }
                            val currentKey = committedPageSnapshotKey(view)
                            val captureSceneStable = snapshotSceneRevision == request.sceneRevision &&
                                currentKey == request.key &&
                                !hasCommittedSnapshotCaptureBlocker(view) &&
                                committedSnapshotWindowRect(view) == windowRect
                            if (!captureSceneStable) {
                                discardCommittedPageSnapshotCapture(
                                    request = request,
                                    view = view,
                                    reason = reason,
                                    attempt = attempt,
                                    blockedAttempt = blockedAttempt,
                                    bitmap = bitmap
                                )
                                return@applyCapture
                            }
                            if (requireVisualContent &&
                                !snapshotHasVisualContent(bitmap, backgroundColor)
                            ) {
                                failCommittedPageSnapshotCapture(
                                    request = request,
                                    view = view,
                                    reason = reason,
                                    attempt = attempt,
                                    failure = "pixel-copy-uniform-background",
                                    windowRect = windowRect,
                                    pixelCopyResult = result,
                                    bitmap = bitmap
                                )
                                return@applyCapture
                            }
                            val committed = committedPageSnapshots.complete(
                                request = request,
                                currentKey = currentKey,
                                currentSceneRevision = snapshotSceneRevision,
                                value = bitmap
                            )
                            if (committed) {
                                // Queue GPU preparation while this is an idle,
                                // validated snapshot, before the first drag draw.
                                runCatching { bitmap.prepareToDraw() }
                                persistentPixels?.let { pixels ->
                                    persistentPixels = null
                                    persistentPipeline?.persistPixels(persistentRequest, pixels) ?: pixels.close()
                                }
                                resumeTemplateMotion(view)
                                onCurrentSnapshotReady()
                            }
                        } finally {
                            persistentPixels?.close()
                        }
                    }
                },
                if (persistentRequest == null) snapshotCallbackHandler else EpubPageSnapshotPersistence.captureHandler
            )
        }.onFailure { error ->
            failCommittedPageSnapshotCapture(
                request = request,
                view = view,
                reason = reason,
                attempt = attempt,
                failure = "pixel-copy-request-failed",
                windowRect = windowRect,
                bitmap = bitmap,
                throwable = error
            )
        }
    }

    private fun discardCommittedPageSnapshotCapture(
        request: EpubCommittedPageSnapshotCache.Request,
        view: ReaderWebView,
        reason: String,
        attempt: Int,
        blockedAttempt: Int,
        bitmap: Bitmap
    ) {
        val activeRequest = committedPageSnapshots.fail(request)
        bitmap.takeUnless { it.isRecycled }?.recycle()
        if (!activeRequest || committedPageSnapshotKey(view) != request.key) return
        if (hasCommittedSnapshotCaptureBlocker(view)) {
            if (blockedAttempt < MAX_BLOCKED_SNAPSHOT_RETRIES) {
                scheduleCommittedPageSnapshotRefresh(
                    reason = reason,
                    attempt = attempt,
                    blockedAttempt = blockedAttempt + 1,
                    expectedKey = request.key
                )
            }
            return
        }
        if (attempt >= MAX_SNAPSHOT_CAPTURE_RETRIES) {
            allowAnimationMaterialFallback(request.key)
            return
        }
        scheduleCommittedPageSnapshotRefresh(
            reason = reason,
            attempt = attempt + 1,
            expectedKey = request.key
        )
    }

    private fun failCommittedPageSnapshotCapture(
        request: EpubCommittedPageSnapshotCache.Request,
        view: ReaderWebView,
        reason: String,
        attempt: Int,
        failure: String,
        windowRect: Rect?,
        pixelCopyResult: Int? = null,
        bitmap: Bitmap? = null,
        throwable: Throwable? = null
    ) {
        val activeRequest = committedPageSnapshots.fail(request)
        bitmap?.takeUnless { it.isRecycled }?.recycle()
        if (!activeRequest) return
        reportCommittedPageSnapshotFailure(
            request = request,
            view = view,
            reason = reason,
            attempt = attempt,
            failure = failure,
            windowRect = windowRect,
            pixelCopyResult = pixelCopyResult,
            throwable = throwable
        )
        if (attempt < MAX_SNAPSHOT_CAPTURE_RETRIES &&
            committedPageSnapshotKey(view) == request.key
        ) {
            scheduleCommittedPageSnapshotRefresh(
                reason = reason,
                attempt = attempt + 1,
                expectedKey = request.key
            )
        } else if (committedPageSnapshotKey(view) == request.key) {
            allowAnimationMaterialFallback(request.key)
        }
    }

    private fun reportCommittedPageSnapshotFailure(
        request: EpubCommittedPageSnapshotCache.Request,
        view: ReaderWebView,
        reason: String,
        attempt: Int,
        failure: String,
        windowRect: Rect?,
        pixelCopyResult: Int?,
        throwable: Throwable?
    ) {
        val result = pixelCopyResult?.let { "$it/${pixelCopyResultName(it)}" } ?: "not-requested"
        val baseMessage = "EPUB committed snapshot failed: failure=$failure, reason=$reason, " +
            "attempt=$attempt, pixelCopy=$result, key=${request.key}, " +
            "current=${committedPageSnapshotKey(view)}, viewport=${view.width}x${view.height}, " +
            "windowRect=$windowRect, androidScrollX=${view.scrollX}, api=${Build.VERSION.SDK_INT}"
        val reported = AtomicBoolean(false)
        lateinit var fallback: Runnable
        fun report(domState: String) {
            if (!reported.compareAndSet(false, true)) return
            removeCallbacks(fallback)
            val message = "$baseMessage, dom=$domState"
            if (throwable == null) AppLog.putDebug(message) else AppLog.putDebug(message, throwable)
        }
        fallback = Runnable { report("unavailable") }
        postDelayed(fallback, SNAPSHOT_DIAGNOSTIC_TIMEOUT_MS)
        runCatching {
            view.evaluateJavascript(SNAPSHOT_DIAGNOSTICS_SCRIPT) { raw ->
                report(raw ?: "null")
            }
        }.onFailure { report("evaluation-failed:${it.javaClass.simpleName}") }
    }

    private fun committedSnapshotWindowRect(view: View): Rect? {
        if (view.width <= 0 || view.height <= 0 || !view.isShown) return null
        val activity = findHostActivity() ?: return null
        val decor = activity.window.decorView
        if (!decor.isAttachedToWindow || decor.width <= 0 || decor.height <= 0) return null
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val rect = Rect(
            location[0],
            location[1],
            location[0] + view.width,
            location[1] + view.height
        )
        return rect.takeIf {
            it.width() == view.width && it.height() == view.height &&
                it.left >= 0 && it.top >= 0 &&
                it.right <= decor.width && it.bottom <= decor.height
        }
    }

    private fun findHostActivity(): Activity? {
        var current: Context? = context
        while (current != null) {
            if (current is Activity) return current
            val wrapper = current as? ContextWrapper ?: return null
            val base = wrapper.baseContext
            if (base === current) return null
            current = base
        }
        return null
    }

    private fun pixelCopyResultName(result: Int): String {
        return when (result) {
            PixelCopy.SUCCESS -> "success"
            PixelCopy.ERROR_UNKNOWN -> "unknown"
            PixelCopy.ERROR_TIMEOUT -> "timeout"
            PixelCopy.ERROR_SOURCE_NO_DATA -> "source-no-data"
            PixelCopy.ERROR_SOURCE_INVALID -> "source-invalid"
            PixelCopy.ERROR_DESTINATION_INVALID -> "destination-invalid"
            else -> "code-$result"
        }
    }

    private fun invalidateCommittedPageSnapshot() {
        committedSnapshotRefreshRunnable?.let(::removeCallbacks)
        committedSnapshotRefreshRunnable = null
        markSnapshotSceneChanged()
    }

    private fun markSnapshotSceneChanged(preserveCommittedPage: Boolean = false) {
        snapshotSceneRevision++
        cancelAnimationMaterialWait()
        if (preserveCommittedPage) {
            committedPageSnapshots.cancelPendingCapture()
        } else {
            committedPageSnapshots.invalidate()
        }
    }

    private fun captureView(
        view: View,
        backgroundColor: Int,
        requireVisualContent: Boolean = false
    ): Bitmap? {
        val viewWidth = view.width.coerceAtLeast(1)
        val viewHeight = view.height.coerceAtLeast(1)
        val bitmapSize = EpubSnapshotSizePolicy.sizeFor(
            width = viewWidth,
            height = viewHeight,
            pixelBudget = snapshotPixelBudget
        )
        val bitmapWidth = bitmapSize.width
        val bitmapHeight = bitmapSize.height
        var bitmap: Bitmap? = null
        return runCatching {
            Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888).also {
                bitmap = it
                val canvas = Canvas(it)
                canvas.scale(bitmapWidth.toFloat() / viewWidth, bitmapHeight.toFloat() / viewHeight)
                if (EpubReaderBackgroundPolicy.shouldUseReaderBackground(chapter, config)) {
                    background?.let { readerBackground ->
                        readerBackground.setBounds(0, 0, viewWidth, viewHeight)
                        readerBackground.draw(canvas)
                    } ?: canvas.drawColor(backgroundColor)
                } else {
                    canvas.drawColor(backgroundColor)
                }
                view.draw(canvas)
                if (requireVisualContent && !snapshotHasVisualContent(it, backgroundColor)) {
                    it.recycle()
                    bitmap = null
                    return@runCatching null
                }
            }
        }.getOrElse {
            bitmap?.takeUnless { candidate -> candidate.isRecycled }?.recycle()
            null
        }
    }

    private fun snapshotHasVisualContent(bitmap: Bitmap, backgroundColor: Int): Boolean {
        val columns = bitmap.width.coerceAtMost(SNAPSHOT_SAMPLE_GRID)
        val rows = bitmap.height.coerceAtMost(SNAPSHOT_SAMPLE_GRID)
        val colors = IntArray(columns * rows)
        val rowPixels = IntArray(bitmap.width)
        var offset = 0
        for (row in 0 until rows) {
            val y = ((row + 0.5f) * bitmap.height / rows).toInt().coerceAtMost(bitmap.height - 1)
            bitmap.getPixels(rowPixels, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (column in 0 until columns) {
                val x = ((column + 0.5f) * bitmap.width / columns).toInt()
                    .coerceAtMost(bitmap.width - 1)
                colors[offset++] = rowPixels[x]
            }
        }
        return EpubDirectSnapshotVisualPolicy.hasVisualContent(backgroundColor, colors)
    }

    private fun completeAfterVisualState(
        view: ReaderWebView,
        onUnavailable: (() -> Unit)? = null,
        callback: (() -> Unit)?
    ) {
        callback ?: return
        completePageAnimationVisualState(view, view.token, onUnavailable) {
            view.postOnAnimation(callback)
        }
    }

    private fun completePageAnimationVisualState(
        view: ReaderWebView,
        requestId: Long,
        onUnavailable: (() -> Unit)? = null,
        callback: () -> Unit
    ) {
        val completed = AtomicBoolean(false)
        val token = view.token
        val chapterKey = view.loadedChapterKey
        val deadline = templateActiveClock.now() + VISUAL_FRAME_FALLBACK_MS
        var timeout: Runnable? = null
        fun finish(ready: Boolean) {
            if (!completed.compareAndSet(false, true)) return
            timeout?.let(::removeCallbacks)
            timeout?.let(templateResumeCallbacks::remove)
            if (destroyed || view.surfaceDestroyed || view.token != token || view.loadedChapterKey != chapterKey) return
            val delivery = Runnable {
                if (!destroyed && !view.surfaceDestroyed && view.token == token && view.loadedChapterKey == chapterKey) {
                    if (ready) callback() else onUnavailable?.invoke()
                }
            }
            if (!deferTemplateUntilResumed(view, delivery)) delivery.run()
        }
        // Match the published reader's short drawing opportunity. This is not
        // pixel verification: navigation measures the live target afterwards,
        // and captures still validate PixelCopy, scene identity and nonempty pixels.
        lateinit var wait: Runnable
        wait = Runnable {
            if (deferTemplateDeadline(this, wait, deadline)) return@Runnable
            finish(true)
        }
        timeout = wait
        if (!postDelayed(wait, VISUAL_FRAME_FALLBACK_MS)) {
            finish(false)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                view.postVisualStateCallback(requestId, object : WebView.VisualStateCallback() {
                    override fun onComplete(completedRequestId: Long) {
                        if (completedRequestId == requestId) finish(true)
                    }
                })
                view.postInvalidateOnAnimation()
            }.onFailure {
                AppLog.putDebug("EPUB visual commit callback unavailable", it)
                // Some WebView providers cannot deliver this notification; the
                // scheduled drawing fallback still runs the caller's validation.
            }
        } else {
            view.postOnAnimation { view.postOnAnimation { finish(true) } }
        }
    }

    private fun completeAfterCompositorFrames(view: ReaderWebView, callback: () -> Unit) {
        view.postOnAnimation(callback)
    }

    internal fun probeCurrentPageFrame(
        expectedChapterIndex: Int,
        expectedPageIndex: Int,
        callback: (Boolean) -> Unit
    ) {
        if (destroyed || !documentReady || chapter?.chapterIndex != expectedChapterIndex ||
            pageIndex != expectedPageIndex
        ) {
            callback(false)
            return
        }
        val token = generation
        val view = currentWebView
        val expectedChapter = chapter ?: run {
            callback(false)
            return
        }
        var firstMetrics: WebMetrics? = null
        fun isActive(): Boolean {
            return !destroyed && token == generation && view === currentWebView &&
                chapter === expectedChapter && chapter?.chapterIndex == expectedChapterIndex
        }
        fun evaluate(onResult: (Boolean) -> Unit) {
            if (!isActive()) {
                onResult(false)
                return
            }
            val renderSequence = view.renderState.sequence
            view.evaluateJavascript(MEASURE_VIEWPORT_SCRIPT) { raw ->
                if (!isActive()) {
                    onResult(false)
                    return@evaluateJavascript
                }
                val metrics = parseMetrics(raw)
                val previous = firstMetrics
                val sameLayout = previous == null || metrics != null &&
                    previous.layoutRevision == metrics.layoutRevision &&
                    previous.visualRevision == metrics.visualRevision
                if (firstMetrics == null) firstMetrics = metrics
                onResult(
                    metrics != null && sameLayout &&
                        metrics.layoutRevision == currentLayoutRevision &&
                        (!frameRenderer || metrics.sourceImagesPending == 0) &&
                        view.renderState.measured(token, metrics.visualRevision, metrics.layoutPending, renderSequence) &&
                        EpubDirectFrameReadinessPolicy.isReady(
                        expectedChapterIndex = expectedChapterIndex,
                        actualChapterIndex = chapter?.chapterIndex,
                        expectedPageIndex = expectedPageIndex,
                        actualPageIndex = metrics.pageIndex,
                        resourcesReady = metrics.resourcesReady,
                        requiresRenderableContent = expectedChapter.requiresRenderableContent(),
                        hasRenderableContent = metrics.hasRenderableContent,
                        hasViewportContent = metrics.hasViewportContent
                    )
                )
            }
        }
        completeAfterVisualState(view) {
            evaluate { firstReady ->
                if (!firstReady || !isActive()) {
                    callback(false)
                    return@evaluate
                }
                completeAfterCompositorFrames(view) {
                    evaluate(callback)
                }
            }
        }
    }

    internal fun currentPageFrameStamp(): EpubPageFrameStamp? {
        val view = currentWebView
        if (destroyed || !documentReady || view.token != generation || !view.renderState.canCapture) return null
        return EpubPageFrameStamp(
            generation, System.identityHashCode(view), chapter?.chapterIndex ?: return null,
            pageIndex, currentLayoutRevision, view.renderState.visualRevision,
            view.renderState.sequence, view.width, view.height
        )
    }

    private fun chapterKey(chapter: EpubDirectChapter, config: EpubCoreLayoutConfig): String {
        return buildString {
            append(chapter.chapterIndex).append('|').append(chapter.href).append('|')
            append(chapter.baseUrl).append('|')
            append(chapter.startFragmentId).append('|').append(chapter.endFragmentId).append('|')
            append(EpubPageFrameTarget.chapterContentRevision(chapter)).append('|')
            append(config.pageWidthPx).append('x').append(config.pageHeightPx).append('|')
            append(config.readerPaddingLeftPx).append(',').append(config.readerPaddingTopPx).append(',')
            append(config.readerPaddingRightPx).append(',').append(config.readerPaddingBottomPx).append('|')
            append(config.readerSafeInsetLeftPx).append(',').append(config.readerSafeInsetTopPx).append(',')
            append(config.readerSafeInsetRightPx).append(',').append(config.readerSafeInsetBottomPx).append('|')
            append(config.scrollMode).append('|').append(config.textPaint.textSize).append('|')
            append(config.textPaint.color).append('|').append(config.textPaint.letterSpacing).append('|')
            append(config.lineHeightPx).append('|')
            append(config.paragraphSpacingPx).append('|').append(config.readerFontFamily).append('|')
            append(config.paragraphIndentPx).append('|')
            append(config.textFontWeight).append('|').append(config.textFontItalic).append('|')
            append(config.readerFontUrl).append('|').append(config.readerFontRevision).append('|')
            append(config.readerFontLength).append('|')
            append(config.alignment).append('|')
            append(config.textFullJustify).append('|').append(config.backgroundColor).append('|')
            append(config.textBottomJustify).append('|')
            append(config.readerBackgroundImage)
            config.readerChromeGeometryKey.takeIf { it.isNotEmpty() }?.let {
                append('|').append(it)
            }
            config.readerTemplateKey.takeIf { it.isNotEmpty() }?.let {
                append("|template:").append(it)
            }
            append('|').append(chapter.publisherPageBackground)
        }
    }

    private fun EpubDirectChapter.baseUrlWithLoadToken(token: Long): String {
        return Uri.parse(baseUrl).buildUpon()
            .appendQueryParameter("__legado_load__", token.toString())
            .build()
            .toString()
    }

    private fun onWebScroll(view: ReaderWebView, scrollY: Int) {
        if (view !== currentWebView || !documentReady) return
        val activeConfig = config ?: return
        val activeChapter = chapter ?: return
        if (!activeConfig.scrollMode && activeChapter.layoutMode == EpubDirectLayoutMode.REFLOWABLE) {
            if (scrollY != 0 && !horizontalScrollCorrectionPosted) {
                horizontalScrollCorrectionPosted = true
                view.postOnAnimation {
                    horizontalScrollCorrectionPosted = false
                    if (view === currentWebView && documentReady &&
                        config?.scrollMode == false &&
                        chapter?.layoutMode == EpubDirectLayoutMode.REFLOWABLE &&
                        view.scrollY != 0
                    ) {
                        view.scrollTo(view.scrollX, 0)
                    }
                }
            }
            return
        }
        if (!activeConfig.scrollMode) return
        if (pageHandoffRequest != null) return
        if (activeChapter.layoutMode.singlePage) return
        if (!activeChapter.startFragmentId.isNullOrBlank() || !activeChapter.endFragmentId.isNullOrBlank()) {
            // Runtime reports relative page metrics for a logical window inside a shared XHTML.
            return
        }
        if (SystemClock.uptimeMillis() < ignoreScrollUntil) return
        val next = (scrollY.toFloat() / height.coerceAtLeast(1)).roundToInt().coerceIn(0, pageCount - 1)
        if (next != pageIndex) {
            pageIndex = next
            notifyPositionChanged()
        }
    }

    private fun onSwipe(deltaX: Float, deltaY: Float) {
        if (!documentReady || isVerticalMode() || abs(deltaX) <= abs(deltaY)) return
        val rtl = chapter.isRtlLayout()
        val direction = if (rtl) {
            if (deltaX > 0f) 1 else -1
        } else {
            if (deltaX < 0f) 1 else -1
        }
        val atBoundary = if (direction > 0) pageIndex >= pageCount - 1 else pageIndex <= 0
        if (!isPageTurnBusy() && atBoundary) {
            snapToCurrentHorizontalPage(currentWebView)
            dispatchBoundary(direction)
            return
        }
        when (performPageTurn(direction, animate = true, queueIfBusy = true)) {
            EpubPageTurnResult.BoundaryRequired -> {
                snapToCurrentHorizontalPage(currentWebView)
                dispatchBoundary(direction, accepted = true)
            }
            EpubPageTurnResult.Rejected,
            EpubPageTurnResult.Unavailable -> {
                if (!isPageTurnBusy()) snapToCurrentHorizontalPage(currentWebView)
            }
            EpubPageTurnResult.MovedWithinChapter,
            EpubPageTurnResult.Queued -> Unit
        }
    }

    private fun snapToCurrentHorizontalPage(view: ReaderWebView) {
        if (isSelectionPageTurnBlocked()) return
        val token = generation
        val pageRequest = pageApplySequence
        view.postOnAnimation {
            if (destroyed || token != generation || view !== currentWebView ||
                !documentReady || isVerticalMode() || pageRequest != pageApplySequence ||
                isPageTurnBusy() || isSelectionPageTurnBlocked()
            ) {
                return@postOnAnimation
            }
            val target = pageIndex.coerceIn(0, pageCount.coerceAtLeast(1) - 1)
            view.evaluateJavascript(
                "window.__legadoEpub&&window.__legadoEpub.setPage($target,0,'auto');",
                null
            )
        }
    }

    private fun isVerticalMode(): Boolean {
        return config?.scrollMode == true && chapter?.layoutMode?.scalesPublisherViewport != true
    }

    private fun prepareChapterTurn(
        direction: Int,
        animate: Boolean,
        scheduleTimeout: Boolean = true,
        interactive: Boolean = false
    ): PendingChapterTurn? {
        val sourceChapter = chapter ?: return null
        val sourceConfig = config ?: return null
        val normalizedDirection = if (direction < 0) -1 else 1
        val style = EpubDirectPageAnimationPolicy.style(
            pageAnim = ReadBook.pageAnim(),
            horizontal = animate && !isVerticalMode()
        )
        if (!documentReady) return null
        val targetChapterIndex = session
            ?.adjacentChapterIndex(sourceChapter.chapterIndex, normalizedDirection)
            ?: return null
        val existing = pendingChapterTurn
        if (existing != null && existing.sourceView === currentWebView &&
            existing.sourceChapterIndex == sourceChapter.chapterIndex &&
            existing.targetChapterIndex == targetChapterIndex &&
            existing.logicalDirection == normalizedDirection && existing.style == style &&
            existing.viewportWidth == width && existing.viewportHeight == height
        ) {
            if (scheduleTimeout) scheduleChapterTurnTimeout()
            return existing
        }
        cancelPendingChapterTurn()
        val backgroundColor = sourceConfig.backgroundColor
        var targetBitmap: Bitmap? = null
        var targetFrame: CachedAnimationTarget? = null
        if (interactive && style != EpubDirectPageAnimationPolicy.Style.None) {
            if (!hasCommittedPageSnapshot(currentWebView, "interactive-chapter-source-unavailable")) return null
            targetBitmap = takeAdjacentPageBitmap(
                logicalDirection = normalizedDirection,
                expectedChapterIndex = targetChapterIndex,
                expectedPageIndex = if (normalizedDirection > 0) 0 else null,
                onTaken = { targetFrame = it }
            )
            if (!EpubDirectPageAnimationPolicy.hasRequiredBitmapFrames(
                    style = style,
                    action = EpubDirectPageAnimationPolicy.turnAction(normalizedDirection),
                    hasTargetBitmap = targetBitmap?.isRecycled == false
                )
            ) {
                targetBitmap?.takeUnless { it.isRecycled }?.recycle()
                return null
            }
        }
        val sourceBitmap = if (style != EpubDirectPageAnimationPolicy.Style.None) {
            takeCommittedPageSnapshot(
                view = currentWebView,
                reason = "chapter-turn-source-unavailable",
                transferOwnership = interactive
            )
        } else {
            null
        }
        if (interactive && sourceBitmap == null) {
            targetBitmap?.takeUnless { it.isRecycled }?.recycle()
            return null
        }
        if (!interactive) {
            targetBitmap = sourceBitmap?.let {
                takeAdjacentPageBitmap(
                    logicalDirection = normalizedDirection,
                    expectedChapterIndex = targetChapterIndex,
                    expectedPageIndex = if (normalizedDirection > 0) 0 else null
                )
            }
        }
        val pending = PendingChapterTurn(
            sourceChapterIndex = sourceChapter.chapterIndex,
            sourceChapterHref = sourceChapter.href,
            targetChapterIndex = targetChapterIndex,
            sourcePageIndex = pageIndex,
            sourcePageCount = pageCount.coerceAtLeast(1),
            logicalDirection = normalizedDirection,
            visualDirection = EpubDirectPageAnimationPolicy.visualDirection(
                logicalDirection = normalizedDirection,
                rtl = sourceChapter.isRtlLayout()
            ),
            style = style,
            backgroundColor = backgroundColor,
            sourceRtl = sourceChapter.isRtlLayout(),
            viewportWidth = width,
            viewportHeight = height,
            sourceLayoutSignature = EpubPageFrameTarget.layoutSignature(
                sourceConfig,
                width,
                height
            ),
            sourceReaderChromeContentRevision = EpubPageFrameTarget.readerChromeContentRevision(
                sourceChapter,
                sourceConfig,
                readerChromeTemplate
            ),
            sourceView = currentWebView,
            sourceBitmap = sourceBitmap,
            targetBitmap = targetBitmap,
            targetFrame = targetFrame
        )
        pendingChapterTurn = pending
        if (scheduleTimeout) scheduleChapterTurnTimeout()
        return pending
    }

    private fun scheduleChapterTurnTimeout() {
        if (pendingChapterTurn == null) return
        chapterTurnTimeoutRunnable?.let(::removeCallbacks)
        lateinit var timeout: Runnable
        timeout = Runnable {
            if (chapterTurnTimeoutRunnable !== timeout) return@Runnable
            chapterTurnTimeoutRunnable = null
            cancelPendingChapterTurn()
        }
        chapterTurnTimeoutRunnable = timeout
        postDelayed(timeout, CHAPTER_TURN_TIMEOUT_MS)
    }

    fun hasPendingChapterTurn(direction: Int? = null): Boolean {
        val pending = pendingChapterTurn ?: return false
        return direction == null || pending.logicalDirection == if (direction < 0) -1 else 1
    }

    fun pendingChapterTurnTarget(direction: Int): Int? {
        val pending = pendingChapterTurn ?: return null
        val normalizedDirection = if (direction < 0) -1 else 1
        return pending.targetChapterIndex.takeIf { pending.logicalDirection == normalizedDirection }
    }

    fun cancelPendingChapterTurn() {
        chapterTurnTimeoutRunnable?.let(::removeCallbacks)
        chapterTurnTimeoutRunnable = null
        val interactiveTurn = pendingChapterTurn
            ?.let(::interactiveTurnFor)
            ?.takeIf(::isInteractivePageTurnActive)
        releasePendingChapterTurn(resumeAdjacentFrames = interactiveTurn == null)
        if (interactiveTurn != null) {
            interactiveTurn.finishRequested = false
            stopInteractiveSettle(interactiveTurn)
            startInteractiveSettle(interactiveTurn, commit = false)
        }
        scheduleQueuedPageTurnDrain()
    }

    private fun releasePendingChapterTurn(resumeAdjacentFrames: Boolean = true) {
        val pending = pendingChapterTurn ?: return
        pendingChapterTurn = null
        pending.sourceBitmap?.takeUnless { it.isRecycled }?.recycle()
        pending.sourceBitmap = null
        pending.targetBitmap?.takeUnless { it.isRecycled }?.recycle()
        pending.targetBitmap = null
        if (pending.sourceView === currentWebView) {
            if (currentWebView.renderState.needsMetrics) scheduleRuntimeMetricsSync()
            scheduleCommittedPageSnapshotRefresh("chapter-turn-cancelled")
        }
        if (resumeAdjacentFrames) scheduleAdjacentPageFrameResume()
    }

    private fun isBoundaryDebounced(now: Long = SystemClock.uptimeMillis()): Boolean {
        return now - lastBoundaryAt < BOUNDARY_DEBOUNCE_MS
    }

    private fun dispatchBoundary(direction: Int, accepted: Boolean = false): Boolean {
        if (isSelectionPageTurnBlocked()) return false
        val normalizedDirection = if (direction < 0) -1 else 1
        val now = SystemClock.uptimeMillis()
        if (!accepted && isBoundaryDebounced(now)) return false
        // The chapter request is the logical result of an accepted boundary gesture.  A
        // missing/low-quality snapshot only removes the visual animation; it must never
        // suppress the chapter handoff itself.
        if (!documentReady || chapter == null) return false
        prepareChapterTurn(normalizedDirection, animate = true) ?: return false
        lastBoundaryAt = now
        listener?.onPageBoundary(normalizedDirection)
        return true
    }

    private fun notifyPositionChanged() {
        applyReaderChromeToView(currentWebView)
        syncAdjacentPageFrames()
        position?.let { listener?.onPositionChanged(it) }
        requestTextReaderPosition()
    }

    private fun requestTextReaderPosition() {
        if (destroyed || !documentReady || chapter?.sourceChapterUrl == null ||
            currentWebView.token != generation || pageAnimationOverlay != null ||
            pageAnimator != null || pageHandoffRequest != null) return
        currentWebView.evaluateJavascript("window.__legadoEpub&&window.__legadoEpub.report();", null)
    }

    private fun takeAdjacentPageBitmap(
        logicalDirection: Int,
        expectedChapterIndex: Int,
        expectedPageIndex: Int?,
        onTaken: ((CachedAnimationTarget) -> Unit)? = null
    ): Bitmap? {
        val direction = adjacentPageDirection(logicalDirection)
        val pipeline = adjacentPageFrames ?: return null
        val target = pipeline.target(direction) ?: return null
        if (!adjacentPageTargetMatches(target, expectedChapterIndex, expectedPageIndex)) {
            return null
        }
        val frame = pipeline.takeFrame(direction) ?: return null
        return frame.use { rendered ->
            val renderedPageMatches = if (expectedPageIndex == null) {
                rendered.pageIndex == rendered.pageCount.coerceAtLeast(1) - 1
            } else {
                rendered.pageIndex == expectedPageIndex
            }
            if (rendered.chapterIndex != expectedChapterIndex ||
                !renderedPageMatches
            ) {
                AppLog.putDebug(
                    "EPUB adjacent frame did not match requested target: " +
                        "direction=$direction, expected=$expectedChapterIndex/" +
                        "${expectedPageIndex ?: "last"}, " +
                        "actual=${rendered.chapterIndex}/${rendered.pageIndex}"
                )
                null
            } else {
                rendered.takeBitmap()?.also {
                    onTaken?.invoke(CachedAnimationTarget(pipeline, target, rendered.pageIndex, rendered.pageCount))
                }
            }
        }
    }

    private fun adjacentPageDirection(
        logicalDirection: Int
    ): EpubAdjacentPageFramePipeline.Direction {
        return if (logicalDirection < 0) {
            EpubAdjacentPageFramePipeline.Direction.Previous
        } else {
            EpubAdjacentPageFramePipeline.Direction.Next
        }
    }

    private fun adjacentPageTargetMatches(
        target: EpubPageFrameTarget,
        expectedChapterIndex: Int,
        expectedPageIndex: Int?
    ): Boolean {
        if (target.chapterIndex != expectedChapterIndex) return false
        return if (expectedPageIndex == null) {
            target.openAtEnd
        } else {
            !target.openAtEnd && target.requestedPageIndex == expectedPageIndex
        }
    }

    private fun supportsAdjacentPageFrames(): Boolean {
        if (frameRenderer || !performanceBudget.adjacentFramesEnabled || isVerticalMode() ||
            EpubDirectPageAnimationPolicy.style(
                pageAnim = ReadBook.pageAnim(),
                horizontal = true
            ) == EpubDirectPageAnimationPolicy.Style.None
        ) return false
        val template = chapter?.readerTemplate
        // Ordinary books and static templates do not depend on any bundled
        // template asset. Load the reference only to verify a scripted theme.
        val reference = if (template?.javascript?.isNotBlank() == true) {
            reviewedFrameReference(template)
        } else null
        return EpubTemplateFramePolicy.supports(template, reference)
    }

    private fun syncAdjacentPageFrames() {
        if (!supportsAdjacentPageFrames() || destroyed || !documentReady || width <= 0 || height <= 0
        ) {
            closeAdjacentPageFrames()
            return
        }
        if (!currentWebView.renderState.canCapture) return
        if (adjacentPageFrames == null && SystemClock.uptimeMillis() < adjacentFrameInitializationRetryAt) return
        // Updating targets does not start work while suspended, and must not leave
        // the cache bound to the previous page throughout a drag.
        if (preloadGestureActive && adjacentPageFrames == null) return
        val activeSession = session ?: return
        val activeChapter = chapter ?: return
        val activeConfig = config ?: return
        val activePosition = position ?: return
        val pipeline = adjacentPageFrames ?: runCatching {
            EpubAdjacentPageFramePipeline(
                context = context,
                viewportWidth = width,
                viewportHeight = height,
                densityDpi = resources.displayMetrics.densityDpi,
                farPrefetchEnabled = performanceBudget.farFramePrefetchEnabled,
                cacheCapacity = adjacentFrameCacheCapacity(context, width, height, performanceBudget.adjacentFrameCacheCapacity),
                canStartColdLayout = {
                    EpubReaderWarmupPolicy.canStartFrameLayout(
                        sourceReady = hasReadyCurrentSnapshot(),
                        elapsedMillis = SystemClock.uptimeMillis() - warmupActivatedAt,
                        chapterLayoutRunning = loadingPreloadedWebViews.isNotEmpty()
                    )
                }
            )
        }.onFailure {
            // A missing display service or failed surface must not create four new
            // WebViews again on every metrics/chrome notification.
            adjacentFrameInitializationRetryAt = SystemClock.uptimeMillis() + ADJACENT_FRAME_INITIALIZATION_BACKOFF_MS
            AppLog.putDebug("EPUB adjacent frame pipeline initialization failed", it)
        }.getOrNull()?.also {
            adjacentFrameInitializationRetryAt = 0L
            it.setListener(object : EpubAdjacentPageFramePipeline.Listener {
                override fun onAdjacentFrameReady(
                    direction: EpubAdjacentPageFramePipeline.Direction,
                    target: EpubPageFrameTarget
                ) {
                    onAdjacentPageFrameReady(direction, target)
                    scheduleQueuedPageTurnDrain()
                }

                override fun onFrameWorkChanged() {
                    resumeScheduledPreloads()
                }

                override fun hasCurrentFrame(): Boolean = hasReadyCurrentSnapshot()

                override fun onCurrentFrameRestored(frame: EpubRenderedPageFrame) {
                    restorePersistentCurrentFrame(frame)
                }
            })
            adjacentPageFrames = it
        } ?: return
        if (preloadGestureActive || interactivePageTurn != null) pipeline.suspendScheduling()
        pipeline.bindCurrent(
            activeSession,
            activeChapter,
            activeConfig,
            activePosition,
            renderedChromeFields(activeChapter, readerChromeTemplate)
        )

        fun offer(view: ReaderWebView?) {
            val preparedChapter = view?.preparedChapter ?: return
            val preparedConfig = view.preparedConfig ?: return
            val preparedPageCount = view.preloadPageCount.takeIf {
                (view.preloadReady || view.promotedReady) && !view.renderState.layoutPending
            }
            pipeline.offerPreparedChapter(activeSession, preparedChapter, preparedConfig, preparedPageCount)
        }
        offer(standbyWebView)
        preloadedWebViews.forEachValue(::offer)
        loadingPreloadedWebViews.values.toList().forEach(::offer)
    }

    private fun onAdjacentPageFrameReady(
        direction: EpubAdjacentPageFramePipeline.Direction,
        target: EpubPageFrameTarget
    ) {
        val turn = interactivePageTurn
        if (turn == null) {
            // Cache callbacks can run inside bindCurrent/prepareGestureFrames. Start
            // a waiting gesture after that update, without requiring another MOVE.
            if (interactiveFrameReadyRunnable != null) return
            lateinit var ready: Runnable
            ready = Runnable {
                if (interactiveFrameReadyRunnable !== ready) return@Runnable
                interactiveFrameReadyRunnable = null
                if (!destroyed) currentWebView.resumePendingInteractiveDrag()
            }
            interactiveFrameReadyRunnable = ready
            postOnAnimation(ready)
            return
        }
        if (!isInteractivePageTurnActive(turn) || turn.restoring ||
            turn.finishRequested == false || turn.overlay.canAnimate || animationRenderStateChanged ||
            direction != adjacentPageDirection(turn.logicalDirection) ||
            turn.framePipeline !== adjacentPageFrames || turn.expectedFrameTarget != target
        ) return
        val sourceStillCurrent = turn.token == generation && turn.sourceLayoutRevision == currentLayoutRevision
        val boundaryActivating = turn.boundary && turn.navigationDispatched && turn.targetToken == generation
        if (!sourceStillCurrent && !boundaryActivating) return
        val expectedChapter = if (turn.boundary) target.chapterIndex else turn.sourceChapterIndex
        val expectedPage = if (turn.boundary) {
            if (turn.logicalDirection > 0) 0 else null
        } else turn.targetPageIndex
        if (!adjacentPageTargetMatches(target, expectedChapter, expectedPage)) return
        var cachedTargetFrame: CachedAnimationTarget? = null
        val bitmap = takeAdjacentPageBitmap(turn.logicalDirection, expectedChapter, expectedPage) {
            cachedTargetFrame = it
        } ?: return
        if (!turn.overlay.supplyPreparedTarget(bitmap)) {
            bitmap.takeUnless { it.isRecycled }?.recycle()
            return
        }
        turn.cachedTargetFrame = cachedTargetFrame
        // These pixels already passed the same checks as a frame available at DOWN.
        // Do not wait for the slower live page command to unlock finger movement.
        setPageAnimationProgress(turn.overlay, turn.requestedProgress)
        maybeStartInteractiveSettle(turn)
    }

    private fun suspendAdjacentPageFrameScheduling() {
        adjacentPageFrameResumeRunnable?.let(::removeCallbacks)
        adjacentPageFrameResumeRunnable = null
        adjacentPageFrames?.suspendScheduling()
    }

    private fun scheduleAdjacentPageFrameResume() {
        val pipeline = adjacentPageFrames ?: return
        adjacentPageFrameResumeRunnable?.let(::removeCallbacks)
        lateinit var resume: Runnable
        resume = Runnable {
            if (adjacentPageFrameResumeRunnable !== resume) return@Runnable
            adjacentPageFrameResumeRunnable = null
            if (destroyed || adjacentPageFrames !== pipeline) return@Runnable
            if (preloadGestureActive || interactivePageTurn != null || pageAnimationOverlay != null) return@Runnable
            syncAdjacentPageFrames()
            if (adjacentPageFrames === pipeline) pipeline.resumeScheduling()
        }
        adjacentPageFrameResumeRunnable = resume
        postOnAnimation(resume)
    }

    private fun closeAdjacentPageFrames() {
        adjacentPageFrameResumeRunnable?.let(::removeCallbacks)
        adjacentPageFrameResumeRunnable = null
        adjacentPageFrames?.close()
        adjacentPageFrames = null
    }

    private fun restorePersistentCurrentFrame(frame: EpubRenderedPageFrame) {
        frame.use {
            val activeChapter = chapter ?: return
            val activeConfig = config ?: return
            val key = committedPageSnapshotKey() ?: return
            if (destroyed || !documentReady || hasCommittedSnapshotCaptureBlocker(currentWebView) ||
                committedPageSnapshots.contains(key) || frame.chapterIndex != activeChapter.chapterIndex ||
                frame.chapterHref != activeChapter.href || frame.pageIndex != pageIndex || frame.pageCount != pageCount ||
                frame.layoutSignature != EpubPageFrameTarget.layoutSignature(activeConfig, width, height) ||
                frame.readerChromeContentRevision != EpubPageFrameTarget.readerChromeContentRevision(
                    activeChapter, activeConfig, readerChromeTemplate)
            ) return
            val bitmap = frame.takeBitmap() ?: return
            // The pipeline verified the stable content identity and the freshly measured page count.
            // Rebind to this WebView's current key; never restore an old runtime token/revision.
            if (preserveBitmapAsCommittedSnapshot(bitmap, currentWebView, requireViewportSize = true)) {
                AppLog.putDebug("EPUB persisted source restored: chapter=${frame.chapterIndex}, page=${frame.pageIndex}")
                resumeTemplateMotion(currentWebView)
                onCurrentSnapshotReady()
            }
        }
    }

    private fun reportRenderState(
        view: ReaderWebView,
        token: Long,
        visualRevision: Long,
        layoutPending: Boolean
    ) {
        if (destroyed || view.token != token || view.parent !== this ||
            !view.renderState.changed(token, visualRevision, layoutPending)
        ) return
        val contentChange = view.preparedChapter?.readerTemplate == null
        if (contentChange && pageAnimationOverlay != null &&
            (view === currentWebView || view === livePageAnimationTarget?.view ||
                view === interactivePageTurn?.sourceView || view === pendingChapterTurn?.sourceView ||
                pageAnimationSourceFrame?.metadata?.let { source ->
                    source.chapterIndex == view.preparedChapter?.chapterIndex &&
                        source.chapterHref == view.preparedChapter?.href
                } == true)
        ) {
            animationRenderStateChanged = true
        }
        if (view !== currentWebView || token != generation) {
            // A warm neighbour can finish an image after its virtual frame was cached.
            // Its local counter cannot validate that independent frame's pixels.
            if (contentChange && view.preparedChapter?.sourceImages?.resources?.isNotEmpty() == true) {
                closeAdjacentPageFrames()
                if (!layoutPending && !isPageTurnBusy()) syncAdjacentPageFrames()
            }
            return
        }
        // Invalidate at the start of a visual change, before its delayed metrics arrive.
        // This also rejects PixelCopy requests already in flight with the old scene.
        invalidateCommittedPageSnapshot()
        if (contentChange) closeAdjacentPageFrames()
        if (!layoutPending) scheduleRuntimeMetricsSync()
    }

    private fun reportTemplateContentChanged(view: ReaderWebView, token: Long, revision: Long) {
        if (destroyed || view.token != token || view.parent !== this ||
            view.preparedChapter?.readerTemplate == null ||
            !view.renderState.contentChanged(token, revision)
        ) return
        if (pageAnimationOverlay != null) animationRenderStateChanged = true
        if (view === currentWebView) invalidateCommittedPageSnapshot()
        closeAdjacentPageFrames()
        if (view === currentWebView && !view.renderState.layoutPending) scheduleRuntimeMetricsSync()
    }

    private fun isRuntimeMetricsSyncBlocked(): Boolean =
        destroyed || hostPaused || !documentReady || currentWebView.token != generation ||
            isSelectionPageTurnBlocked() || pageAnimationOverlay != null || pageAnimator != null ||
            pageHandoffRequest != null || pendingActivationView != null || pendingChapterTurn != null ||
            renderRecoveryRequest != null

    private fun requestRuntimeMetricsSync() {
        if (destroyed || !documentReady) return
        currentWebView.renderState.requireMetrics()
        scheduleRuntimeMetricsSync()
    }

    private fun scheduleRuntimeMetricsSync(attempt: Int = 0) {
        if (isRuntimeMetricsSyncBlocked() || runtimeMetricsSyncRunnable != null ||
            runtimeMetricsSyncInFlight
        ) return
        val view = currentWebView
        val token = generation
        lateinit var measure: Runnable
        measure = Runnable {
            if (runtimeMetricsSyncRunnable !== measure) return@Runnable
            runtimeMetricsSyncRunnable = null
            if (isRuntimeMetricsSyncBlocked() || view !== currentWebView || token != generation) return@Runnable
            val request = ++runtimeMetricsSyncSequence
            val renderSequence = view.renderState.sequence
            val pageRequest = pageApplySequence
            runtimeMetricsSyncInFlight = true
            fun isActive(): Boolean = request == runtimeMetricsSyncSequence &&
                !destroyed && !hostPaused && view === currentWebView && view.token == token && token == generation
            fun retry() {
                if (view.renderState.sequence != renderSequence || pageRequest != pageApplySequence) {
                    // A newer settled signal can arrive while this query is in flight,
                    // including on its final retry. Give that new scene its own budget.
                    scheduleRuntimeMetricsSync()
                } else if (attempt < MAX_RUNTIME_METRICS_RETRIES) {
                    scheduleRuntimeMetricsSync(attempt + 1)
                } else {
                    // Keep the last valid page and stop polling. A new render-state signal,
                    // user turn or host resume starts a fresh, bounded measurement.
                    AppLog.putDebug("EPUB runtime metrics still pending: token=$token, " +
                        "visual=${view.renderState.visualRevision}, layoutPending=${view.renderState.layoutPending}")
                }
            }
            lateinit var timeout: Runnable
            timeout = Runnable {
                if (!isActive() || runtimeMetricsSyncTimeout !== timeout) return@Runnable
                runtimeMetricsSyncTimeout = null
                runtimeMetricsSyncInFlight = false
                runtimeMetricsSyncSequence++
                retry()
            }
            runtimeMetricsSyncTimeout = timeout
            postDelayed(timeout, RUNTIME_METRICS_TIMEOUT_MS)
            runCatching {
                view.evaluateJavascript(MEASURE_VIEWPORT_SCRIPT) { raw ->
                    if (!isActive() || runtimeMetricsSyncTimeout !== timeout) return@evaluateJavascript
                    removeCallbacks(timeout)
                    runtimeMetricsSyncTimeout = null
                    runtimeMetricsSyncInFlight = false
                    val metrics = parseMetrics(raw)
                    if (isRuntimeMetricsSyncBlocked()) return@evaluateJavascript
                    if (pageRequest != pageApplySequence || metrics == null ||
                        !isMeasuredRenderStateCurrent(view, metrics) ||
                        !EpubDirectActivationVisualPolicy.canNavigate(
                            requiresViewportContent = chapter?.sourceChapterUrl != null,
                            requiresRenderableContent = chapter.requiresRenderableContent(),
                            hasRenderableContent = metrics.hasRenderableContent,
                            hasViewportContent = metrics.hasViewportContent,
                            expectedPageIndex = metrics.pageIndex,
                            actualPageIndex = metrics.pageIndex
                        ) || !view.renderState.measured(
                            token, metrics.visualRevision, metrics.layoutPending, renderSequence
                        )
                    ) {
                        retry()
                        return@evaluateJavascript
                    }
                    commitPageMetrics(metrics)
                    requestTextReaderPosition()
                    syncAdjacentPageFrames()
                    scheduleCommittedPageSnapshotRefresh("runtime-metrics-reconciled")
                    scheduleQueuedPageTurnDrain()
                }
            }.onFailure {
                if (isActive()) {
                    removeCallbacks(timeout)
                    runtimeMetricsSyncTimeout = null
                    runtimeMetricsSyncInFlight = false
                    runtimeMetricsSyncSequence++
                    retry()
                }
            }
        }
        runtimeMetricsSyncRunnable = measure
        if (attempt == 0) postOnAnimation(measure) else postDelayed(measure, RUNTIME_METRICS_RETRY_MS)
    }

    private fun cancelRuntimeMetricsSync() {
        runtimeMetricsSyncSequence++
        runtimeMetricsSyncRunnable?.let(::removeCallbacks)
        runtimeMetricsSyncRunnable = null
        runtimeMetricsSyncTimeout?.let(::removeCallbacks)
        runtimeMetricsSyncTimeout = null
        runtimeMetricsSyncInFlight = false
    }

    private fun reportMetrics(
        token: Long,
        pageCountFromJs: Int,
        pageIndexFromJs: Int,
        layoutRevisionFromJs: Long
    ) {
        if (token != generation || !documentReady || currentWebView.token != token) return
        val nextCount = if (chapter?.layoutMode?.singlePage == true) 1 else pageCountFromJs.coerceAtLeast(1)
        val nextIndex = pageIndexFromJs.coerceIn(0, nextCount - 1)
        val layoutChanged = layoutRevisionFromJs >= 0L && layoutRevisionFromJs != currentLayoutRevision
        if (!currentWebView.renderState.needsMetrics &&
            nextCount == pageCount && nextIndex == pageIndex && !layoutChanged
        ) return
        if (isRuntimeMetricsSyncBlocked()) {
            // The active page command owns its result. A delayed page notification
            // must not invalidate that result or send navigation back to its source.
            if (layoutChanged) currentWebView.renderState.requireMetrics()
            return
        }
        // Notifications can wait on Android's main queue. Read the live document
        // before accepting a different position instead of committing an old page.
        requestRuntimeMetricsSync()
    }

    private fun reportSelection(token: Long, json: String) {
        if (token != generation || currentWebView.token != token || destroyed || !documentReady) return
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return
        val text = obj.optString("text")
        if (text.isBlank()) {
            // Clearing an empty DOM range is not a page/layout state transition.
            if (!selectionActive) return
            selectionActive = false
            clearQueuedPageTurns()
            listener?.onSelectionCleared()
            resumePendingActivationAfterSelection()
            requestRuntimeMetricsSync()
            return
        }
        val rectsJson = obj.optJSONArray("rects") ?: JSONArray()
        val viewportWidth = obj.optDouble("viewportWidth", width.toDouble()).toFloat().coerceAtLeast(1f)
        val viewportHeight = obj.optDouble("viewportHeight", height.toDouble()).toFloat().coerceAtLeast(1f)
        val scaleX = width.coerceAtLeast(1) / viewportWidth
        val scaleY = height.coerceAtLeast(1) / viewportHeight
        if (!scaleX.isFinite() || !scaleY.isFinite() || scaleX <= 0f || scaleY <= 0f) return
        val rects = buildList {
            for (index in 0 until minOf(rectsJson.length(), 512)) {
                val item = rectsJson.optJSONObject(index) ?: continue
                val left = (item.optDouble("left").toFloat() * scaleX).coerceIn(0f, width.toFloat())
                val top = (item.optDouble("top").toFloat() * scaleY).coerceIn(0f, height.toFloat())
                val right = (item.optDouble("right").toFloat() * scaleX).coerceIn(0f, width.toFloat())
                val bottom = (item.optDouble("bottom").toFloat() * scaleY).coerceIn(0f, height.toFloat())
                if (right > left && bottom > top) add(RectF(left, top, right, bottom))
            }
        }.sortedWith(compareBy<RectF> { it.top }.thenBy { it.left })
        selectionActive = rects.isNotEmpty()
        if (selectionActive) {
            lockPageTurnsForSelection()
            listener?.onSelectionChanged(text, rects)
        } else {
            listener?.onSelectionCleared()
            resumePendingActivationAfterSelection()
            requestRuntimeMetricsSync()
        }
    }

    private fun isSelectionPageTurnBlocked(): Boolean {
        return selectionActive || currentWebView.hasLongPressSelectionGesture()
    }

    /** Cancel every visual/input path that could continue moving the page after long press. */
    private fun lockPageTurnsForSelection() {
        clearQueuedPageTurns()
        pausePendingActivationForSelection()
        cancelPageHandoff(clearTransitionSnapshot = true)
        cancelPendingChapterTurn()
        cancelPageAnimation()
    }

    private fun reportHighlightPage(token: Long, targetPage: Int) {
        if (token != generation || targetPage !in 0 until pageCount ||
            isSelectionPageTurnBlocked()
        ) return
        setPage(targetPage, animate = false)
    }

    private fun parseMetrics(raw: String?): WebMetrics? {
        val decoded = runCatching { JSONArray("[$raw]").optString(0) }.getOrNull() ?: return null
        val obj = runCatching { JSONObject(decoded) }.getOrNull() ?: return null
        if (!obj.optBoolean("ready", false)) return null
        return WebMetrics(
            pageCount = obj.optInt("pageCount", 1),
            pageIndex = obj.optInt("pageIndex", 0),
            resourcesReady = obj.optBoolean("resourcesReady", false),
            resourcesFailed = obj.optBoolean("resourcesFailed", false),
            layoutRevision = obj.optLong("layoutRevision", -1L),
            layoutPending = obj.optBoolean("layoutPending", false),
            visualRevision = obj.optLong("visualRevision", 0L),
            contentRevision = obj.optLong("contentRevision", -1L),
            sourceImagesPending = obj.optInt("sourceImagesPending", 0),
            activationTargetRevision = obj.optLong("activationTargetRevision", -1L),
            activationTargetSatisfied = obj.optBoolean("activationTargetSatisfied", false),
            hasRenderableContent = obj.optBoolean("renderable", false),
            hasViewportContent = obj.optBoolean("viewportRenderable", false)
        )
    }

    private fun resourceResponse(
        request: WebResourceRequest,
        boundSession: EpubDirectSession?,
        preparedConfig: EpubCoreLayoutConfig?
    ): WebResourceResponse? {
        return resourceResponse(
            uri = request.url,
            method = request.method,
            requestHeaders = request.requestHeaders,
            boundSession = boundSession,
            preparedConfig = preparedConfig
        )
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    private fun resourceResponse(
        url: String?,
        boundSession: EpubDirectSession?,
        preparedConfig: EpubCoreLayoutConfig?
    ): WebResourceResponse? {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        return resourceResponse(
            uri = uri,
            method = "GET",
            requestHeaders = emptyMap(),
            boundSession = boundSession,
            preparedConfig = preparedConfig
        )
    }

    private fun resourceResponse(
        uri: Uri,
        method: String,
        requestHeaders: Map<String, String>,
        boundSession: EpubDirectSession?,
        preparedConfig: EpubCoreLayoutConfig?
    ): WebResourceResponse? {
        if (uri.host.equals(io.legado.app.help.reader.ReaderAssetReferences.HOST, true)) {
            if (method != "GET" && method != "HEAD") return methodNotAllowedResponse()
            return runCatching {
                io.legado.app.help.reader.ReaderAssets.resource(uri.toString(), method == "HEAD")
                    ?.toWebResponse(withBody = method != "HEAD", crossOrigin = true)
            }.getOrNull() ?: errorResponse(404, "Not Found")
        }
        if (uri.scheme.equals(EpubDirectSession.SCHEME, true) &&
            uri.host.equals(boundSession?.resourceHost, true)
        ) {
            val normalizedMethod = method.uppercase()
            if (normalizedMethod != "GET" && normalizedMethod != "HEAD") {
                return methodNotAllowedResponse()
            }
            if (preparedConfig?.readerTemplate != null && uri.path?.startsWith("/__reader_template__/") == true) {
                val asset = when (uri.path) {
                    "/__reader_template__/paged.js" -> "epub/vendor/paged.js"
                    "/__reader_template__/source-map.js" -> "epub/template-source-map.js"
                    "/__reader_template__/page-alignment.js" -> "epub/page-alignment.js"
                    "/__reader_template__/browser-flow.js" -> "epub/template-browser-flow.js"
                    "/__reader_template__/runtime.js" -> "epub/template-runtime.js"
                    else -> return errorResponse(404, "Not Found")
                }
                return WebResourceResponse("application/javascript", "UTF-8", 200, "OK",
                    mapOf("Access-Control-Allow-Origin" to "*", "Cache-Control" to "no-store"),
                    if (normalizedMethod == "HEAD") ByteArrayInputStream(ByteArray(0)) else context.assets.open(asset))
            }
            if (Uri.decode(uri.encodedPath.orEmpty()).endsWith("/__legado_reader_font__")) {
                if (normalizedMethod == "HEAD") {
                    return if (preparedConfig?.readerFontPath.isNullOrBlank() ||
                        preparedConfig?.readerFontLength == null
                    ) {
                        errorResponse(404, "Not Found")
                    } else {
                        emptyResponse(readerFontHeaders(preparedConfig))
                    }
                }
                return readerFontResponse(preparedConfig)
            }
            val rangeHeader = requestHeaders.entries
                .firstOrNull { it.key.equals("Range", true) }
                ?.value
            val result = runCatching {
                if (normalizedMethod == "HEAD") {
                    boundSession?.openHeadResource(uri.toString(), rangeHeader)
                } else {
                    boundSession?.openResource(uri.toString(), rangeHeader)
                }
            }
            result.exceptionOrNull()?.let { throwable ->
                reportResourceFailure(uri, 500, throwable.localizedMessage ?: "resource read failed", throwable)
                return errorResponse(500, "EPUB Resource Error")
            }
            val resource = result.getOrNull() ?: run {
                reportResourceFailure(uri, 404, "entry not found")
                return errorResponse(404, "Not Found")
            }
            return resource.toWebResponse(withBody = normalizedMethod != "HEAD",
                crossOrigin = preparedConfig?.readerTemplate != null)
        }
        return when (uri.scheme?.lowercase()) {
            "data", "blob", "about" -> null
            "http", "https" -> if (preparedConfig?.readerTemplate != null) null else errorResponse(403, "Forbidden")
            else -> errorResponse(403, "Forbidden")
        }
    }

    private fun reportResourceFailure(
        uri: Uri,
        statusCode: Int,
        reason: String,
        throwable: Throwable? = null
    ) {
        val path = Uri.decode(uri.encodedPath.orEmpty()).ifBlank { "/" }
        val key = "$statusCode|${uri.host}|$path"
        val shouldReport = synchronized(reportedResourceFailures) {
            if (key in reportedResourceFailures || reportedResourceFailures.size >= MAX_RESOURCE_FAILURE_REPORTS) {
                false
            } else {
                reportedResourceFailures.add(key)
            }
        }
        if (!shouldReport) return
        val message = "EPUB direct resource failed: status=$statusCode, host=${uri.host}, path=$path, reason=$reason"
        if (throwable == null) AppLog.putDebug(message) else AppLog.putDebug(message, throwable)
    }

    private fun readerFontResponse(preparedConfig: EpubCoreLayoutConfig?): WebResourceResponse {
        val path = preparedConfig?.readerFontPath ?: return errorResponse(404, "Not Found")
        val expectedLength = preparedConfig.readerFontLength ?: return errorResponse(404, "Not Found")
        val file = File(path)
        if (!file.isFile || file.length() != expectedLength) return errorResponse(404, "Not Found")
        val stream = runCatching { FileInputStream(file) }.getOrNull()
            ?: return errorResponse(404, "Not Found")
        return WebResourceResponse(
            preparedConfig.readerFontMimeType ?: "font/ttf",
            null,
            200,
            "OK",
            readerFontHeaders(preparedConfig),
            stream
        )
    }

    private fun readerFontHeaders(config: EpubCoreLayoutConfig): Map<String, String> {
        return mapOf(
            "Cache-Control" to "public, max-age=31536000, immutable",
            "Content-Length" to (config.readerFontLength ?: 0L).toString(),
            "Access-Control-Allow-Origin" to "*",
            "X-Content-Type-Options" to "nosniff"
        )
    }

    private fun EpubDirectResource.toWebResponse(withBody: Boolean, crossOrigin: Boolean = false): WebResourceResponse {
        val responseStream = if (withBody) stream else {
            stream.close()
            ByteArrayInputStream(ByteArray(0))
        }
        return WebResourceResponse(mimeType, encoding, statusCode, reasonPhrase,
            if (crossOrigin) headers + ("Access-Control-Allow-Origin" to "*") else headers, responseStream)
    }

    private fun methodNotAllowedResponse() = WebResourceResponse(
        "text/plain",
        Charsets.UTF_8.name(),
        405,
        "Method Not Allowed",
        nonCacheableResourceHeaders() + mapOf("Allow" to "GET, HEAD"),
        ByteArrayInputStream(ByteArray(0))
    )

    private fun emptyResponse(headers: Map<String, String> = emptyMap()) = WebResourceResponse(
        "text/plain",
        Charsets.UTF_8.name(),
        200,
        "OK",
        headers,
        ByteArrayInputStream(ByteArray(0))
    )

    private fun errorResponse(statusCode: Int, reasonPhrase: String) = WebResourceResponse(
        "text/plain",
        Charsets.UTF_8.name(),
        statusCode,
        reasonPhrase,
        nonCacheableResourceHeaders(),
        ByteArrayInputStream(ByteArray(0))
    )

    private fun nonCacheableResourceHeaders(): Map<String, String> {
        return mapOf(
            "Cache-Control" to "no-store, max-age=0",
            "Pragma" to "no-cache",
            "Access-Control-Allow-Origin" to "*"
        )
    }

    private fun createWebView(): ReaderWebView {
        return ReaderWebView(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            settings.loadsImagesAutomatically = true
            settings.blockNetworkImage = false
            settings.allowFileAccess = false
            settings.allowContentAccess = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.textZoom = 100
            settings.useWideViewPort = false
            settings.loadWithOverviewMode = false
            settings.mediaPlaybackRequiresUserGesture = false
            settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            webChromeClient = WebChromeClient()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                settings.offscreenPreRaster = true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                settings.safeBrowsingEnabled = true
            }
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            addJavascriptInterface(Bridge(this@EpubDirectWebLayer, this), BRIDGE_NAME)
            if (hostPaused) onPause()
        }
    }

    private fun destroyWebView(view: ReaderWebView) {
        if (view.surfaceDestroyed) return
        view.surfaceDestroyed = true
        templateResumeCallbacks.entries.removeAll { it.value === view }
        if (livePageAnimationTarget?.view === view) clearLivePageAnimationTarget()
        runCatching { view.animate().cancel() }
        view.translationX = 0f
        view.cancelDocumentLoadTimeout()
        view.cancelDocumentLoadProbe()
        view.cancelRuntimeStableTimeout()
        view.cancelTemplateWatchdog()
        runCatching { view.stopLoading() }
        view.preloading = false
        view.boundSession = null
        view.expectedBaseUrl = null
        view.loadedChapterKey = null
        view.loadComplete = false
        view.runtimeInstalled = false
        view.resetRuntimeTerminal(0L)
        view.preloadReady = false
        view.preloadBoundaryReady = false
        view.promotedReady = false
        view.preparedPageCount = 1
        view.preloadTarget = null
        view.preloadPageIndex = 0
        view.preloadPageCount = 1
        view.preloadLayoutRevision = -1L
        view.preparedChapter = null
        view.preparedConfig = null
        view.appliedReaderChromeConfig = null
        view.appliedReaderChromeData = null
        view.pageCommitSequence++
        view.readerChromeApplySequence++
        view.readerChromeApplyInFlight = false
        runCatching { view.removeJavascriptInterface(BRIDGE_NAME) }
        runCatching { view.removeJavascriptInterface(TEMPLATE_BRIDGE_NAME) }
        runCatching { view.webViewClient = WebViewClient() }
        runCatching { view.webChromeClient = null }
        runCatching {
            if (view.parent === this) removeView(view)
        }
        runCatching { view.loadUrl(WebViewBlank) }
        runCatching { view.clearHistory() }
        runCatching { view.destroy() }
    }

    private fun discardRetiredTemplateCurrent(view: ReaderWebView) {
        if (destroyed || view !== currentWebView) return
        // A newer chapter may already own generation and pendingActivationView.
        // Retire only the dead source surface; its crash cannot cancel that request.
        documentReady = false
        selectionActive = false
        annotationVisible = false
        cancelRuntimeMetricsSync()
        closeAdjacentPageFrames()
        clearQueuedPageTurns()
        cancelPageAnimation()
        cancelPageHandoff(clearTransitionSnapshot = true)
        cancelPendingChapterTurn()
        invalidateCommittedPageSnapshot()
        cancelPendingActivation(view)
        if (standbyWebView === view) standbyWebView = null
        preloadedWebViews.removeValue(view)
        removeLoadingPreload(view)
        destroyWebView(view)
        currentWebView = createWebView().also { replacement ->
            addView(replacement, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        listener?.onSelectionCleared()
    }

    private class ReaderSelectionActionModeCallback(
        private val delegate: ActionMode.Callback
    ) : ActionMode.Callback {

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            val created = delegate.onCreateActionMode(mode, menu)
            menu.clear()
            return created
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            delegate.onPrepareActionMode(mode, menu)
            menu.clear()
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = true

        override fun onDestroyActionMode(mode: ActionMode) {
            delegate.onDestroyActionMode(mode)
        }
    }

    private inner class ReaderWebView(context: Context) : WebView(context) {
        private var appliedSourceImageMode: String? = null
        val renderState = EpubRuntimeRenderState().apply { reset(0L) }
        @Volatile
        var token: Long = 0L
            set(value) {
                field = value
                renderState.reset(value)
                templateMotionState = "settled"
                templateMotionSettlePending = false
            }
        @Volatile
        var boundSession: EpubDirectSession? = null
        var preparedChapter: EpubDirectChapter? = null
        @Volatile
        var preparedConfig: EpubCoreLayoutConfig? = null
        var loadedChapterKey: String? = null
        var expectedBaseUrl: String? = null
        var loadComplete = false
        var runtimeInstalled = false
        var runtimeStable = false
        private var runtimeTerminalToken = 0L
        private var runtimeTerminalKind = RuntimeTerminalKind.Pending
        private var runtimeTerminalMessage: String? = null
        private var runtimeTerminalConsumed = false
        private var documentLoadTimeout: Runnable? = null
        private var documentLoadProbe: Runnable? = null
        private var runtimeStableTimeout: Runnable? = null
        var surfaceDestroyed = false
        var templateSecret = ""
            private set
        private var templateWatchdog: Runnable? = null
        private val templateTapGate = EpubTemplateTapGate()
        private var templateTouchToken = -1L
        private var documentLoadMarkerToken: Long? = null
        var preloading = false
        var preloadReady = false
        var preloadBoundaryReady = false
        var promotedReady = false
        var preloadVerificationInFlight = false
        var preparedPageCount = 1
        var preloadTarget: EpubDirectActivationTargetPolicy.Target? = null
        var preloadPageIndex = 0
        var preloadPageCount = 1
        var preloadLayoutRevision = -1L
        var appliedReaderChromeConfig: EpubReaderChromeConfig? = null
        var appliedReaderChromeData: EpubReaderChromeData? = null
        var pageCommitSequence = 0L
        var readerChromeApplySequence = 0L
        var readerChromeApplyInFlight = false
        var templateMotionState = "settled"
        var templateMotionSettlePending = false
        var templateMotionFailedSnapshotKey: EpubCommittedPageSnapshotKey? = null
        private var downX = 0f
        private var downY = 0f
        private var downViewportY = 0f
        private var downAt = 0L
        private var embeddedInteractionAtDown = 0L
        private var moved = false
        private var horizontalDrag = false
        private var dragContactActive = false
        private var lastDragDeltaX = 0f
        private var lastDragYFraction = 0.5f
        private var multiPointerGesture = false
        private var longPressSelectionGesture = false
        private var selectionGestureArmRunnable: Runnable? = null
        private var selectionGestureUnlockRunnable: Runnable? = null
        private var webTouchCancelled = false
        private var embeddedInteractionGesture = false
        private val embeddedInteraction = EpubDirectEmbeddedInteractionPolicy()
        private var startedAtTop = false
        private var startedAtBottom = false
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private val pageTouchSlop: Int
            get() = AppConfig.pageTouchSlop.takeIf { it > 0 } ?: touchSlop
        private val minimumFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
        private val maximumFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity.toFloat()
        private var velocityTracker: VelocityTracker? = null

        override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
            super.onScrollChanged(l, t, oldl, oldt)
            if (boundSession != null) onWebScroll(this, t)
        }

        private fun preservesSystemSelectionMenu(): Boolean {
            return hitTestResult.type == HitTestResult.EDIT_TEXT_TYPE
        }

        private fun suppressSelectionMenu(callback: ActionMode.Callback): ActionMode.Callback {
            return if (callback is ReaderSelectionActionModeCallback) callback
            else ReaderSelectionActionModeCallback(callback)
        }

        override fun startActionMode(callback: ActionMode.Callback): ActionMode? {
            if (preservesSystemSelectionMenu()) return super.startActionMode(callback)
            return super.startActionMode(suppressSelectionMenu(callback))
        }

        override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return startActionMode(callback)
            if (preservesSystemSelectionMenu()) return super.startActionMode(callback, type)
            return super.startActionMode(suppressSelectionMenu(callback), type)
        }

        fun setEmbeddedInteraction(interactionId: Long, active: Boolean): Boolean {
            embeddedInteractionGesture = embeddedInteraction.onBridgeState(interactionId, active)
            return embeddedInteractionGesture
        }

        fun prepare(
            session: EpubDirectSession,
            token: Long,
            chapter: EpubDirectChapter,
            config: EpubCoreLayoutConfig,
            chapterKey: String,
            preloading: Boolean,
            preloadTarget: EpubDirectActivationTargetPolicy.Target? = null
        ) {
            cancelDocumentLoadTimeout()
            cancelDocumentLoadProbe()
            cancelRuntimeStableTimeout()
            cancelTemplateWatchdog()
            settings.domStorageEnabled = chapter.scripted || chapter.layoutMode == EpubDirectLayoutMode.INTERACTIVE
            this.boundSession = session
            this.token = token
            this.preparedChapter = chapter
            this.preparedConfig = config
            this.loadedChapterKey = chapterKey
            configureTemplateBridge(chapter)
            this.expectedBaseUrl = chapter.baseUrlWithLoadToken(token)
            this.documentLoadMarkerToken = null
            this.loadComplete = false
            this.runtimeInstalled = false
            this.runtimeStable = false
            resetRuntimeTerminal(token)
            this.preloading = preloading
            this.preloadReady = false
            this.preloadBoundaryReady = false
            this.promotedReady = false
            this.preloadVerificationInFlight = false
            this.preparedPageCount = 1
            this.preloadTarget = preloadTarget
            this.preloadPageIndex = 0
            this.preloadPageCount = 1
            this.preloadLayoutRevision = -1L
            this.appliedReaderChromeConfig = null
            this.appliedReaderChromeData = null
            this.pageCommitSequence++
            this.readerChromeApplySequence++
            this.readerChromeApplyInFlight = false
            this.templateMotionState = "settled"
            this.templateMotionSettlePending = false
            embeddedInteraction.reset()
            configureClient(chapter, config)
        }

        fun promote(
            session: EpubDirectSession,
            token: Long,
            chapter: EpubDirectChapter,
            config: EpubCoreLayoutConfig,
            chapterKey: String,
            reuseReady: Boolean = false
        ) {
            val reusableBoundary = reuseReady && preloadReady && preloadBoundaryReady &&
                loadComplete && runtimeInstalled && runtimeStable
            cancelDocumentLoadTimeout()
            cancelRuntimeStableTimeout()
            settings.domStorageEnabled = chapter.scripted || chapter.layoutMode == EpubDirectLayoutMode.INTERACTIVE
            this.boundSession = session
            this.token = token
            this.runtimeStable = false
            resetRuntimeTerminal(token)
            this.preparedChapter = chapter
            this.preparedConfig = config
            this.loadedChapterKey = chapterKey
            this.preloading = false
            this.promotedReady = reusableBoundary
            this.preloadReady = false
            this.preloadBoundaryReady = reusableBoundary
            this.preloadVerificationInFlight = false
            this.appliedReaderChromeConfig = null
            this.appliedReaderChromeData = null
            this.pageCommitSequence++
            this.readerChromeApplySequence++
            this.readerChromeApplyInFlight = false
            embeddedInteraction.reset()
            configureClient(chapter, config)
            // An in-flight preload has not committed its main document yet. Its new
            // WebViewClient will install the runtime from onPageFinished instead.
            if (loadComplete) {
                installRuntime(chapter, config)
            } else {
                scheduleDocumentLoadTimeout(chapter)
            }
        }

        fun loadPreparedDocument(chapter: EpubDirectChapter) {
            cancelDocumentLoadTimeout()
            cancelDocumentLoadProbe()
            val markerToken = token
            documentLoadMarkerToken = markerToken
            loadDataWithBaseURL(
                expectedBaseUrl ?: chapter.baseUrl,
                EpubWebDocumentLoadMarker.inject(chapter.html, markerToken),
                DOCUMENT_MIME_TYPE,
                "UTF-8",
                null
            )
            scheduleDocumentLoadTimeout(chapter)
            scheduleDocumentLoadProbe(chapter, preparedConfig ?: return)
        }

        private fun configureTemplateBridge(chapter: EpubDirectChapter) {
            // addJavascriptInterface is visible to every frame. Remove the original
            // bridge before navigation; the sandbox never receives the host nonce.
            removeJavascriptInterface(BRIDGE_NAME)
            removeJavascriptInterface(TEMPLATE_BRIDGE_NAME)
            templateTapGate.reset()
            templateSecret = if (chapter.readerTemplate != null) UUID.randomUUID().toString() + UUID.randomUUID() else ""
            settings.allowContentAccess = chapter.readerTemplate == null
            if (chapter.readerTemplate != null) {
                addJavascriptInterface(TemplateBridge(this@EpubDirectWebLayer, this, templateSecret), TEMPLATE_BRIDGE_NAME)
            } else {
                addJavascriptInterface(Bridge(this@EpubDirectWebLayer, this), BRIDGE_NAME)
            }
        }

        fun cancelDocumentLoadTimeout() {
            documentLoadTimeout?.let {
                removeCallbacks(it)
                templateResumeCallbacks.remove(it)
            }
            documentLoadTimeout = null
        }

        fun cancelDocumentLoadProbe() {
            documentLoadProbe?.let(::removeCallbacks)
            documentLoadProbe = null
            documentLoadMarkerToken = null
        }

        fun cancelRuntimeStableTimeout() {
            runtimeStableTimeout?.let {
                removeCallbacks(it)
                templateResumeCallbacks.remove(it)
            }
            runtimeStableTimeout = null
        }

        fun cancelTemplateWatchdog() {
            templateWatchdog?.let(::removeCallbacks)
            templateWatchdog = null
        }

        private fun startTemplateWatchdog() {
            cancelTemplateWatchdog()
            if (preparedChapter?.readerTemplate == null) return
            val expectedChapter = loadedChapterKey
            val watchdog = EpubTemplateRenderWatchdog(SystemClock::uptimeMillis)
            var probeInFlight = false
            lateinit var pulse: Runnable
            pulse = Runnable {
                if (templateWatchdog !== pulse || destroyed || loadedChapterKey != expectedChapter) return@Runnable
                if (hostPaused) {
                    watchdog.pause()
                    postDelayed(pulse, 2_000L)
                    return@Runnable
                }
                watchdog.beginProbe()
                if (watchdog.timedOut(runtimeStable, renderState.layoutPending)) {
                    cancelTemplateWatchdog()
                    // This timer runs on Android's UI thread, outside template JS.
                    // Destroy only this surface. WebView render processes may be
                    // shared with comments, login pages and source requests;
                    // terminating one here can also crash those unrelated views.
                    onTemplateFailure(this@ReaderWebView, token, "模板脚本长时间无响应，已停止本次渲染")
                    return@Runnable
                }
                if (!probeInFlight) {
                    probeInFlight = true
                    evaluateJavascript(
                        "(function(){var a=window.__legadoEpub;return a&&a.templateHeartbeat?a.templateHeartbeat():0;})()"
                    ) { raw ->
                        if (templateWatchdog === pulse && loadedChapterKey == expectedChapter) {
                            probeInFlight = false
                            watchdog.acknowledge(raw?.toLongOrNull() ?: 0L)
                        }
                    }
                }
                postDelayed(pulse, 2_000L)
            }
            templateWatchdog = pulse
            postDelayed(pulse, 2_000L)
        }

        fun resetRuntimeTerminal(token: Long) {
            runtimeTerminalToken = token
            runtimeTerminalKind = RuntimeTerminalKind.Pending
            runtimeTerminalMessage = null
            runtimeTerminalConsumed = false
        }

        fun recordRuntimeTerminal(
            expectedToken: Long,
            kind: RuntimeTerminalKind,
            message: String?
        ): Boolean {
            if (token != expectedToken || runtimeTerminalToken != expectedToken ||
                runtimeTerminalKind != RuntimeTerminalKind.Pending
            ) return false
            runtimeTerminalKind = kind
            runtimeTerminalMessage = message
            return true
        }

        fun takeRuntimeTerminal(expectedToken: Long): RuntimeTerminal? {
            if (token != expectedToken || runtimeTerminalToken != expectedToken ||
                runtimeTerminalKind == RuntimeTerminalKind.Pending || runtimeTerminalConsumed
            ) return null
            runtimeTerminalConsumed = true
            return RuntimeTerminal(runtimeTerminalKind, runtimeTerminalMessage)
        }

        private fun isRuntimeTerminalPending(expectedToken: Long): Boolean {
            return token == expectedToken && runtimeTerminalToken == expectedToken &&
                runtimeTerminalKind == RuntimeTerminalKind.Pending
        }

        private fun scheduleRuntimeStableTimeout() {
            cancelRuntimeStableTimeout()
            val expectedToken = token
            val expectedChapterKey = loadedChapterKey
            val templateDeadline = if (preparedChapter?.readerTemplate != null) {
                templateActiveClock.now() + TEMPLATE_STABLE_TIMEOUT_MS
            } else null
            lateinit var timeout: Runnable
            timeout = Runnable {
                if (runtimeStableTimeout !== timeout) return@Runnable
                if (destroyed || !isRuntimeTerminalPending(expectedToken) || token != expectedToken ||
                    loadedChapterKey != expectedChapterKey
                ) {
                    runtimeStableTimeout = null
                    return@Runnable
                }
                if (templateDeadline != null && deferTemplateDeadline(this, timeout, templateDeadline)) return@Runnable
                runtimeStableTimeout = null
                if (preloading) {
                    discardFailedPreload(this@ReaderWebView)
                } else if (preparedChapter?.readerTemplate != null) {
                    onTemplateFailure(this@ReaderWebView, expectedToken, "模板排版未能在限定时间内完成")
                } else {
                    failPendingLoad(
                        this@ReaderWebView,
                        expectedToken,
                        "EPUB runtime did not become stable"
                    )
                }
            }
            runtimeStableTimeout = timeout
            postDelayed(timeout, if (preparedChapter?.readerTemplate != null) TEMPLATE_STABLE_TIMEOUT_MS else RUNTIME_STABLE_TIMEOUT_MS)
        }

        private fun scheduleDocumentLoadProbe(
            chapter: EpubDirectChapter,
            config: EpubCoreLayoutConfig
        ) {
            val markerToken = documentLoadMarkerToken ?: return
            val expectedChapterKey = loadedChapterKey
            lateinit var probe: Runnable
            probe = Runnable {
                if (documentLoadProbe !== probe || loadComplete ||
                    documentLoadMarkerToken != markerToken || loadedChapterKey != expectedChapterKey
                ) {
                    return@Runnable
                }
                evaluateJavascript(EpubWebDocumentLoadMarker.readyScript(markerToken)) { raw ->
                    if (documentLoadProbe !== probe || loadComplete ||
                        documentLoadMarkerToken != markerToken || loadedChapterKey != expectedChapterKey
                    ) {
                        return@evaluateJavascript
                    }
                    if (EpubWebDocumentLoadMarker.isReadyResult(raw)) {
                        completeMainDocumentLoad(chapter, config)
                    } else {
                        postDelayed(probe, DOCUMENT_READY_PROBE_DELAY_MS)
                    }
                }
            }
            documentLoadProbe = probe
            postDelayed(probe, DOCUMENT_READY_PROBE_DELAY_MS)
        }

        private fun scheduleDocumentLoadTimeout(chapter: EpubDirectChapter) {
            cancelDocumentLoadTimeout()
            val expectedToken = token
            val expectedChapterKey = loadedChapterKey
            val templateDeadline = if (chapter.readerTemplate != null) {
                templateActiveClock.now() + DOCUMENT_LOAD_TIMEOUT_MS
            } else null
            lateinit var timeout: Runnable
            timeout = Runnable {
                if (documentLoadTimeout !== timeout) return@Runnable
                if (destroyed || loadComplete || token != expectedToken || loadedChapterKey != expectedChapterKey) {
                    documentLoadTimeout = null
                    return@Runnable
                }
                if (templateDeadline != null && deferTemplateDeadline(this, timeout, templateDeadline)) return@Runnable
                documentLoadTimeout = null
                AppLog.putDebug(
                    "EPUB direct document callback timed out: chapter=${chapter.chapterIndex}, " +
                        "expected=$expectedBaseUrl, viewUrl=$url, preloading=$preloading"
                )
                if (preloading) {
                    discardFailedPreload(this@ReaderWebView)
                } else {
                    failPendingLoad(
                        this@ReaderWebView,
                        expectedToken,
                        "EPUB document load timed out"
                    )
                }
            }
            documentLoadTimeout = timeout
            postDelayed(timeout, DOCUMENT_LOAD_TIMEOUT_MS)
        }

        private fun configureClient(chapter: EpubDirectChapter, config: EpubCoreLayoutConfig) {
            val clientSession = boundSession
            val clientToken = token
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                    return resourceResponse(request, clientSession, config)
                }

                @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
                override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
                    return resourceResponse(url, clientSession, config)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                    if (chapter.readerTemplate != null && !request.isForMainFrame &&
                        request.url.scheme?.lowercase() in setOf("http", "https", "data", "blob", "about")) return false
                    markEmbeddedInteraction()
                    listener?.onLinkClicked(request.url.toString())
                    return true
                }

                @Deprecated("Deprecated in Android")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url.isNullOrBlank()) return true
                    if (chapter.readerTemplate != null && url.toUri().scheme?.lowercase() in
                        setOf("http", "https", "data", "blob", "about")) return false
                    markEmbeddedInteraction()
                    listener?.onLinkClicked(url)
                    return true
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    if (!isExpectedMainDocument(url)) return
                    loadComplete = false
                    runtimeInstalled = false
                    if (!preloading && this@ReaderWebView === currentWebView && this@ReaderWebView.token == generation) {
                        documentReady = false
                    }
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    if (url.equals(WebViewBlank, ignoreCase = true)) return
                    if (!isExpectedMainDocument(url)) {
                        AppLog.putDebug(
                            "EPUB direct ignored unexpected document callback: " +
                                "expected=$expectedBaseUrl, actual=$url, viewUrl=${view.url}, token=$clientToken"
                        )
                        return
                    }
                    verifyCurrentDocument(chapter, config)
                }

                @Deprecated("Deprecated in Android")
                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) return
                    handleDocumentLoadError(failingUrl, description ?: "WebView error $errorCode")
                }

                @RequiresApi(Build.VERSION_CODES.M)
                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {
                    if (!request.isForMainFrame) return
                    handleDocumentLoadError(request.url.toString(), error.description.toString())
                }

                private fun handleDocumentLoadError(failingUrl: String?, description: String) {
                    if (!isExpectedMainDocument(failingUrl)) return
                    cancelDocumentLoadTimeout()
                    cancelDocumentLoadProbe()
                    loadComplete = false
                    runtimeInstalled = false
                    loadedChapterKey = null
                    if (preloading) {
                        discardFailedPreload(this@ReaderWebView)
                        return
                    }
                    failPendingLoad(
                        this@ReaderWebView,
                        token,
                        "EPUB document load failed: $description",
                    )
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse
                ) {
                    if (errorResponse.statusCode < 400) return
                    if (!request.isForMainFrame) {
                        if (request.url.host.equals(clientSession?.resourceHost, true)) {
                            reportResourceFailure(
                                request.url,
                                errorResponse.statusCode,
                                errorResponse.reasonPhrase.orEmpty()
                            )
                        }
                        return
                    }
                    if (!isExpectedMainDocument(request.url.toString())) return
                    cancelDocumentLoadTimeout()
                    cancelDocumentLoadProbe()
                    loadComplete = false
                    runtimeInstalled = false
                    loadedChapterKey = null
                    if (preloading) {
                        discardFailedPreload(this@ReaderWebView)
                    } else {
                        failPendingLoad(
                            this@ReaderWebView,
                            token,
                            "EPUB document HTTP ${errorResponse.statusCode}: ${errorResponse.reasonPhrase}"
                        )
                    }
                }

                override fun onReceivedSslError(
                    view: WebView,
                    handler: SslErrorHandler,
                    error: SslError
                ) {
                    if (!isExpectedMainDocument(error.url)) {
                        handler.cancel()
                        return
                    }
                    handler.cancel()
                    cancelDocumentLoadTimeout()
                    cancelDocumentLoadProbe()
                    loadComplete = false
                    runtimeInstalled = false
                    loadedChapterKey = null
                    if (preloading) {
                        discardFailedPreload(this@ReaderWebView)
                    } else {
                        failPendingLoad(this@ReaderWebView, token, "EPUB document SSL validation failed")
                    }
                }

                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    if (chapter.readerTemplate != null) {
                        cancelTemplateWatchdog()
                        if (!destroyed && this@ReaderWebView === currentWebView && token != generation) {
                            discardRetiredTemplateCurrent(this@ReaderWebView)
                        } else {
                            onTemplateFailure(this@ReaderWebView, token, "模板渲染进程已退出")
                            if (standbyWebView === this@ReaderWebView) standbyWebView = null
                            destroyWebView(this@ReaderWebView)
                        }
                        return true
                    }
                    cancelDocumentLoadTimeout()
                    cancelDocumentLoadProbe()
                    val wasCurrent = view === currentWebView
                    val animationSourceSnapshot = if (wasCurrent && !destroyed) {
                        pageAnimationOverlay?.takeSourceBitmap()
                    } else {
                        null
                    }
                    val chapterTurnSourceSnapshot = if (wasCurrent && !destroyed) {
                        pendingChapterTurn?.sourceBitmap?.also {
                            pendingChapterTurn?.sourceBitmap = null
                        }
                    } else {
                        null
                    }
                    val committedSourceSnapshot = if (animationSourceSnapshot == null &&
                        chapterTurnSourceSnapshot == null && wasCurrent && !destroyed
                    ) {
                        committedPageSnapshotKey(this@ReaderWebView)?.let(committedPageSnapshots::take)
                    } else {
                        null
                    }
                    val recoverySnapshot = animationSourceSnapshot ?: chapterTurnSourceSnapshot ?:
                    committedSourceSnapshot ?: if (
                        wasCurrent && !destroyed && recoverySnapshotOverlay == null
                    ) {
                        captureView(
                            this@EpubDirectWebLayer,
                            this@EpubDirectWebLayer.config?.backgroundColor
                                ?: android.graphics.Color.WHITE,
                            requireVisualContent = this@EpubDirectWebLayer.chapter
                                .requiresRenderableContent()
                        )
                    } else {
                        null
                    }
                    cancelPendingChapterTurn()
                    cancelPageAnimation()
                    cancelPageHandoff()
                    invalidateCommittedPageSnapshot()
                    val wasPending = !wasCurrent && !preloading && token == generation
                    val hadVisibleDocument = hasVisibleDocument
                    val recoveryDecision = EpubDirectRecoveryPolicy.decide(
                        wasCurrent = wasCurrent,
                        wasPending = wasPending,
                        hadVisibleDocument = hadVisibleDocument,
                        destroyed = destroyed
                    )
                    val failedPendingChapter = preparedChapter.takeIf { wasPending }
                    val failedPendingConfig = preparedConfig.takeIf { wasPending }
                    val queuedRecovery = renderRecoveryRequest
                    val pendingCandidate = if (wasCurrent) {
                        standbyWebView?.takeIf {
                            !it.preloading && it.token == generation && it.preparedChapter != null
                        }
                    } else {
                        null
                    }
                    val recoveryChapter = pendingCandidate?.preparedChapter
                        ?: queuedRecovery?.chapter
                        ?: this@EpubDirectWebLayer.chapter
                    val recoveryConfig = pendingCandidate?.preparedConfig
                        ?: queuedRecovery?.config
                        ?: this@EpubDirectWebLayer.config
                    val recoveryPageIndex = when {
                        pendingCandidate != null -> pendingPageIndex
                        queuedRecovery != null -> queuedRecovery.initialPageIndex
                        else -> pageIndex
                    }
                    val recoveryAtEnd = when {
                        pendingCandidate != null -> pendingLastPage
                        queuedRecovery != null -> queuedRecovery.openAtEnd
                        else -> false
                    }
                    val recoveryProgress = when {
                        pendingCandidate != null -> pendingProgress
                        queuedRecovery != null -> queuedRecovery.progress
                        else -> position?.progress
                    }
                    val recoveryFragmentId = when {
                        pendingCandidate != null -> pendingFragmentId
                        queuedRecovery != null -> queuedRecovery.fragmentId
                        else -> null
                    }
                    cancelPendingActivation(this@ReaderWebView)
                    if (view === standbyWebView) standbyWebView = null
                    preloadedWebViews.removeValue(this@ReaderWebView)
                    removeLoadingPreload(this@ReaderWebView)
                    if (!destroyed && wasCurrent) {
                        documentReady = false
                    } else if (!destroyed && wasPending) {
                        advanceGenerationKeepingCommittedView(this@ReaderWebView)
                    }
                    view?.let { crashed ->
                        runCatching { (crashed.parent as? FrameLayout)?.removeView(crashed) }
                        runCatching { crashed.destroy() }
                    }
                    if (recoveryDecision.retry && wasCurrent) {
                        val replacement = runCatching { createWebView() }.getOrElse { error ->
                            recoverySnapshot?.takeUnless { it.isRecycled }?.recycle()
                            listener?.onError("EPUB WebView replacement could not be created", error)
                            return true
                        }
                        val replacementAttachFailure = runCatching {
                            replacement.setBackgroundColor(
                                this@EpubDirectWebLayer.config?.let { activeConfig ->
                                    if (EpubReaderBackgroundPolicy.shouldUseReaderBackground(
                                            this@EpubDirectWebLayer.chapter,
                                            activeConfig
                                        )) {
                                        android.graphics.Color.TRANSPARENT
                                    } else {
                                        activeConfig.backgroundColor
                                    }
                                } ?: android.graphics.Color.TRANSPARENT
                            )
                            this@EpubDirectWebLayer.config?.let(::applyReaderSurfaceBackground)
                            addView(
                                replacement,
                                FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT
                                )
                            )
                        }.exceptionOrNull()
                        if (replacementAttachFailure != null) {
                            destroyWebView(replacement)
                            recoverySnapshot?.takeUnless { it.isRecycled }?.recycle()
                            listener?.onError(
                                "EPUB WebView replacement could not be attached",
                                replacementAttachFailure
                            )
                            return true
                        }
                        currentWebView = replacement
                        if (recoverySnapshot != null) {
                            showRecoverySnapshot(recoverySnapshot)
                        } else {
                            recoverySnapshotOverlay?.bringToFront()
                        }
                        val recoveryScheduled = scheduleRenderRecovery(
                            replacement = replacement,
                            recoveryChapter = recoveryChapter,
                            recoveryConfig = recoveryConfig,
                            initialPageIndex = recoveryPageIndex,
                            openAtEnd = recoveryAtEnd,
                            progress = recoveryProgress,
                            fragmentId = recoveryFragmentId,
                            requireBlankCurrent = recoveryDecision.requireBlankCurrent
                        )
                        if (!recoveryScheduled) {
                            clearRecoverySnapshot()
                            listener?.onError("EPUB WebView render process exited")
                        }
                    } else if (recoveryDecision.retry && wasPending && renderRecoveryRunnable == null) {
                        val recoveryScheduled = scheduleRenderRecovery(
                            replacement = currentWebView,
                            recoveryChapter = failedPendingChapter,
                            recoveryConfig = failedPendingConfig,
                            initialPageIndex = pendingPageIndex,
                            openAtEnd = pendingLastPage,
                            progress = pendingProgress,
                            fragmentId = pendingFragmentId,
                            requireBlankCurrent = recoveryDecision.requireBlankCurrent
                        )
                        if (!recoveryScheduled) {
                            listener?.onError("EPUB candidate WebView render process exited")
                        }
                    } else {
                        recoverySnapshot?.takeUnless { it.isRecycled }?.recycle()
                    }
                    return true
                }
            }
        }

        private fun isExpectedMainDocument(url: String?): Boolean {
            val expected = expectedBaseUrl ?: return false
            return EpubWebMainDocumentUrlMatcher.matches(expected, url)
        }

        private fun completeMainDocumentLoad(chapter: EpubDirectChapter, config: EpubCoreLayoutConfig) {
            if (loadComplete) return
            cancelDocumentLoadTimeout()
            cancelDocumentLoadProbe()
            loadComplete = true
            if (preloading) {
                evaluateJavascript(PAUSE_MEDIA_SCRIPT, null)
                // Warm pagination and media layout while this WebView is off-screen.
                // Promotion reuses the singleton runtime and only advances its token.
                installRuntime(chapter, config)
                return
            }
            if (this@ReaderWebView.token != generation) return
            installRuntime(chapter, config)
        }

        private fun verifyCurrentDocument(chapter: EpubDirectChapter, config: EpubCoreLayoutConfig) {
            val markerToken = documentLoadMarkerToken ?: return
            val expectedChapterKey = loadedChapterKey
            evaluateJavascript(EpubWebDocumentLoadMarker.readyScript(markerToken)) { raw ->
                if (loadComplete || documentLoadMarkerToken != markerToken ||
                    loadedChapterKey != expectedChapterKey
                ) {
                    return@evaluateJavascript
                }
                if (EpubWebDocumentLoadMarker.isReadyResult(raw)) {
                    completeMainDocumentLoad(chapter, config)
                }
            }
        }

        private fun installRuntime(chapter: EpubDirectChapter, config: EpubCoreLayoutConfig) {
            startTemplateWatchdog()
            val installToken = token
            val installChapterKey = loadedChapterKey
            val reusedRuntime = runtimeInstalled
            val readerChromePayload = readerChromePayload(this, chapter, config)
            // The runtime's bootstrap handles both a fresh document and a promoted
            // preload. In the latter case it replays stable for the new token.
            val runtime = if (chapter.readerTemplate != null) {
                EpubTemplateDocument.runtimeScript(installToken, chapter, config, templateSecret,
                    readerChromeDataJson(readerChromePayload.data), TEMPLATE_STABLE_TIMEOUT_MS)
            } else runtimeScript(installToken, chapter, config)
            val script = runtime +
                "\n;${readerChromeApplyScript(installToken, readerChromePayload)};" +
                "\n;!!window.__legadoEpub;"
            evaluateJavascript(script) { installed ->
                if (token != installToken || loadedChapterKey != installChapterKey) {
                    return@evaluateJavascript
                }
                if (installed != "true") {
                    runtimeInstalled = false
                    if (reusedRuntime) {
                        runtimeStable = false
                        installRuntime(chapter, config)
                    } else if (preloading) {
                        discardFailedPreload(this@ReaderWebView)
                    } else {
                        failPendingLoad(
                            this@ReaderWebView,
                            installToken,
                            "EPUB runtime initialization failed"
                        )
                    }
                    return@evaluateJavascript
                }
                runtimeInstalled = true
                pageCommitSequence++
                readerChromeApplySequence++
                readerChromeApplyInFlight = false
                appliedReaderChromeConfig = readerChromePayload.config
                appliedReaderChromeData = readerChromePayload.data
                if (isRuntimeTerminalPending(installToken)) {
                    runtimeStable = false
                    scheduleRuntimeStableTimeout()
                } else {
                    consumeRuntimeTerminal(this@ReaderWebView, installToken)
                }
            }
        }

        fun cancelPendingInteractiveDrag() {
            dragContactActive = false
        }

        fun resumePendingInteractiveDrag() {
            if (!dragContactActive || !horizontalDrag || !webTouchCancelled || multiPointerGesture ||
                hostPaused || embeddedInteractionGesture || selectionActive || longPressSelectionGesture ||
                this !== currentWebView || token != generation || templateTouchToken != token || interactivePageTurn != null
            ) return
            if (beginInteractivePageTurn(this, lastDragDeltaX, pageTouchSlop)) {
                updateInteractivePageTurn(this, lastDragDeltaX, lastDragYFraction)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> dragContactActive = true
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN ->
                    dragContactActive = false
            }
            if (this === currentWebView && token == generation && !preloading) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> listener?.onSelectionInteractionChanged(true)
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> listener?.onSelectionInteractionChanged(false)
                }
            }
            var pendingTap: Pair<Float, Float>? = null
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                multiPointerGesture = false
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
            }
            addRawVelocityMovement(event)
            if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                multiPointerGesture = true
                moved = true
                horizontalDrag = false
                resetLongPressSelectionGesture()
                finishInteractivePageTurn(this, commit = false)
            }
            if (multiPointerGesture) {
                // Lifting the first pointer must not turn its replacement's position
                // into a large page swipe. Keep embedded pinch/zoom with the WebView.
                val handled = if (webTouchCancelled) true else super.onTouchEvent(event)
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    embeddedInteraction.onNativeTouchFinished()
                    embeddedInteractionGesture = false
                    webTouchCancelled = false
                    multiPointerGesture = false
                    velocityTracker?.recycle()
                    velocityTracker = null
                }
                return handled
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    templateTapGate.reset()
                    templateTouchToken = token
                    syncSourceImageMode()
                    resetLongPressSelectionGesture()
                    downAt = SystemClock.uptimeMillis()
                    armLongPressSelectionGesture()
                    embeddedInteraction.onNativeTouchStarted()
                    downX = event.rawX
                    downY = event.rawY
                    downViewportY = event.y
                    lastPageGestureYFraction = (downViewportY / height.coerceAtLeast(1)).coerceIn(0f, 1f)
                    lastDragDeltaX = 0f
                    lastDragYFraction = lastPageGestureYFraction
                    if (startupInputCount < 3 && !frameRenderer) {
                        startupInputCount++
                        startupInputAt = SystemClock.uptimeMillis()
                        startupMotionReported = false
                        AppLog.putDebug("EPUB startup input: input=$startupInputCount, page=$pageIndex, " +
                            "sourceReady=${hasReadyCurrentSnapshot()}, " +
                            "nearReady=${adjacentPageFrames?.hasNearForwardFrames() == true}, " +
                            "elapsedMs=${startupInputAt - warmupActivatedAt}")
                    }
                    embeddedInteractionAtDown = lastEmbeddedInteractionAt
                    moved = false
                    horizontalDrag = false
                    webTouchCancelled = false
                    embeddedInteractionGesture = false
                    startedAtTop = !canScrollVertically(-1)
                    startedAtBottom = !canScrollVertically(1)
                    scheduleCommittedPageSnapshotRefresh("gesture-down")
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    val touchYFraction = (downViewportY + dy) / height.coerceAtLeast(1)
                    lastDragDeltaX = dx
                    lastDragYFraction = touchYFraction
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        moved = true
                        if (preparedChapter?.readerTemplate != null && !longPressSelectionGesture) {
                            selectionGestureArmRunnable?.let(::removeCallbacks)
                            selectionGestureArmRunnable = null
                        }
                    }
                    if (embeddedInteractionGesture) {
                        val interactiveCancelled = finishInteractivePageTurn(this, commit = false)
                        horizontalDrag = false
                        return if (interactiveCancelled || webTouchCancelled) {
                            true
                        } else {
                            super.onTouchEvent(event)
                        }
                    }
                    updateLongPressSelectionGesture()
                    if (updateInteractivePageTurn(this, dx, touchYFraction)) return true
                    if (!horizontalDrag && EpubDirectGesturePolicy.startsHorizontalDrag(
                            dx,
                            dy,
                            pageTouchSlop,
                            isVerticalMode(),
                            selectionActive || longPressSelectionGesture,
                            embeddedInteractionGesture
                        )
                    ) {
                        horizontalDrag = true
                    }
                    if (webTouchCancelled) {
                        if (horizontalDrag && interactivePageTurn == null &&
                            beginInteractivePageTurn(this, dx, pageTouchSlop)
                        ) {
                            updateInteractivePageTurn(this, dx, touchYFraction)
                        }
                        return true
                    }
                    if (EpubDirectGesturePolicy.shouldCancelWebTouch(
                            horizontalDrag = horizontalDrag,
                            embeddedInteraction = embeddedInteractionGesture,
                            alreadyCancelled = webTouchCancelled
                        )
                    ) {
                        cancelWebTouch(event)
                        webTouchCancelled = true
                        if (beginInteractivePageTurn(this, dx, pageTouchSlop)) {
                            updateInteractivePageTurn(this, dx, touchYFraction)
                        }
                        return true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (preparedChapter?.readerTemplate != null && token == templateTouchToken &&
                        !moved && !webTouchCancelled && abs(dx) <= touchSlop && abs(dy) <= touchSlop &&
                        SystemClock.uptimeMillis() - downAt in 0L until ViewConfiguration.getLongPressTimeout().toLong()) {
                        templateTapGate.record(token, SystemClock.uptimeMillis())
                    }
                    updateLongPressSelectionGesture()
                    if (embeddedInteractionGesture) {
                        val interactiveCancelled = finishInteractivePageTurn(this, commit = false)
                        horizontalDrag = false
                        val handled = if (webTouchCancelled) true else super.onTouchEvent(event)
                        embeddedInteraction.onNativeTouchFinished()
                        embeddedInteractionGesture = false
                        webTouchCancelled = false
                        resetLongPressSelectionGesture()
                        velocityTracker?.recycle()
                        velocityTracker = null
                        return if (interactiveCancelled) true else handled
                    }
                    val activeTurn = interactivePageTurn?.takeIf {
                        it.sourceView === this && it.finishRequested == null && !it.restoring
                    }
                    if (activeTurn != null) {
                        updateInteractivePageTurn(this, dx, (downViewportY + dy) / height.coerceAtLeast(1))
                        velocityTracker?.computeCurrentVelocity(1000, maximumFlingVelocity)
                        val velocityX = velocityTracker?.xVelocity ?: 0f
                        val commit = activeTurn.gesture.shouldCommit(velocityX, minimumFlingVelocity, width)
                        if (!webTouchCancelled) {
                            cancelWebTouch(event)
                            webTouchCancelled = true
                        }
                        finishInteractivePageTurn(this, commit, velocityX)
                        horizontalDrag = false
                        velocityTracker?.recycle()
                        velocityTracker = null
                        embeddedInteraction.onNativeTouchFinished()
                        embeddedInteractionGesture = false
                        webTouchCancelled = false
                        resetLongPressSelectionGesture()
                        return true
                    }
                    val horizontalGesture = horizontalDrag || EpubDirectGesturePolicy.startsHorizontalDrag(
                        dx,
                        dy,
                        pageTouchSlop,
                        isVerticalMode(),
                        selectionActive || longPressSelectionGesture,
                        embeddedInteractionGesture
                    )
                    if (horizontalGesture) {
                        velocityTracker?.computeCurrentVelocity(1000, maximumFlingVelocity)
                        val velocityX = velocityTracker?.xVelocity ?: 0f
                        val turnPage = EpubDirectGesturePolicy.shouldTurnPage(
                            dx,
                            dy,
                            velocityX,
                            pageTouchSlop,
                            minimumFlingVelocity,
                            width
                        )
                        if (!webTouchCancelled) {
                            cancelWebTouch(event)
                            webTouchCancelled = true
                        }
                        if (turnPage) {
                            onSwipe(dx, dy)
                        } else if (!isPageTurnBusy()) {
                            snapToCurrentHorizontalPage(this)
                        }
                        velocityTracker?.recycle()
                        velocityTracker = null
                        embeddedInteraction.onNativeTouchFinished()
                        embeddedInteractionGesture = false
                        webTouchCancelled = false
                        resetLongPressSelectionGesture()
                        return true
                    }
                    // A template scrolls inside its sandboxed iframe. The outer
                    // WebView reports both edges even while the chapter is midway.
                    if (isVerticalMode() && preparedChapter?.readerTemplate == null &&
                        !selectionActive && !longPressSelectionGesture) {
                        val threshold = touchSlop * 4
                        if (startedAtBottom && !canScrollVertically(1) && dy < -threshold && abs(dy) > abs(dx)) {
                            dispatchBoundary(1)
                        } else if (startedAtTop && !canScrollVertically(-1) && dy > threshold && abs(dy) > abs(dx)) {
                            dispatchBoundary(-1)
                        }
                    }
                    val elapsed = SystemClock.uptimeMillis() - downAt
                    if (!moved && elapsed < ViewConfiguration.getLongPressTimeout()) {
                        val tapX = event.x
                        val tapY = event.y
                        if (!selectionActive && !longPressSelectionGesture &&
                            lastEmbeddedInteractionAt == embeddedInteractionAtDown &&
                            !nativeHitConsumesReaderTap()
                        ) {
                            pendingTap = tapX to tapY
                        }
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    templateTapGate.reset()
                    parent?.requestDisallowInterceptTouchEvent(false)
                    val interactiveCancelled = finishInteractivePageTurn(this, commit = false)
                    embeddedInteraction.onNativeTouchFinished()
                    embeddedInteractionGesture = false
                    horizontalDrag = false
                    webTouchCancelled = false
                    settleLongPressSelectionGesture()
                    velocityTracker?.recycle()
                    velocityTracker = null
                    if (interactiveCancelled) return true
                }
            }
            val handled = super.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                embeddedInteraction.onNativeTouchFinished()
                embeddedInteractionGesture = false
                webTouchCancelled = false
                settleLongPressSelectionGesture()
                velocityTracker?.recycle()
                velocityTracker = null
            }
            pendingTap?.let { (tapX, tapY) -> listener?.onTap(tapX, tapY) }
            return handled
        }

        fun hasLongPressSelectionGesture(): Boolean = longPressSelectionGesture

        fun consumeTemplateSourceTap(): Boolean =
            preparedChapter?.readerTemplate == null || templateTapGate.consume(token, SystemClock.uptimeMillis())

        private fun armLongPressSelectionGesture() {
            lateinit var arm: Runnable
            arm = Runnable {
                if (selectionGestureArmRunnable !== arm) return@Runnable
                selectionGestureArmRunnable = null
                updateLongPressSelectionGesture()
            }
            selectionGestureArmRunnable = arm
            postDelayed(arm, ViewConfiguration.getLongPressTimeout().toLong())
        }

        private fun updateLongPressSelectionGesture(now: Long = SystemClock.uptimeMillis()) {
            if (longPressSelectionGesture) return
            if (EpubDirectGesturePolicy.startsLongPressSelection(
                    elapsedMillis = now - downAt,
                    timeoutMillis = ViewConfiguration.getLongPressTimeout().toLong(),
                    moved = moved && preparedChapter?.readerTemplate != null,
                    horizontalDrag = horizontalDrag,
                    cancelled = webTouchCancelled
                )
            ) {
                longPressSelectionGesture = true
                clearQueuedPageTurns()
                lockPageTurnsForSelection()
            }
        }

        private fun resetLongPressSelectionGesture() {
            selectionGestureArmRunnable?.let(::removeCallbacks)
            selectionGestureArmRunnable = null
            selectionGestureUnlockRunnable?.let(::removeCallbacks)
            selectionGestureUnlockRunnable = null
            longPressSelectionGesture = false
        }

        private fun settleLongPressSelectionGesture() {
            selectionGestureArmRunnable?.let(::removeCallbacks)
            selectionGestureArmRunnable = null
            selectionGestureUnlockRunnable?.let(::removeCallbacks)
            selectionGestureUnlockRunnable = null
            if (!longPressSelectionGesture) return
            lateinit var unlock: Runnable
            unlock = Runnable {
                if (selectionGestureUnlockRunnable !== unlock) return@Runnable
                selectionGestureUnlockRunnable = null
                longPressSelectionGesture = false
                if (this === currentWebView) {
                    resumePendingActivationAfterSelection()
                    if (renderState.needsMetrics) scheduleRuntimeMetricsSync()
                }
            }
            selectionGestureUnlockRunnable = unlock
            postDelayed(unlock, SELECTION_REPORT_SETTLE_MS)
        }

        private fun nativeHitConsumesReaderTap(): Boolean {
            val images = preparedChapter?.sourceImages
            if (images != null) {
                val mode = TextReaderImageClickPolicy.mode(AppConfig.clickImgWay, images)
                if (hitTestResult.type == HitTestResult.SRC_IMAGE_ANCHOR_TYPE) return mode != "3"
                if (hitTestResult.type == HitTestResult.IMAGE_TYPE && mode == "1") return true
            }
            return when (hitTestResult.type) {
                HitTestResult.EDIT_TEXT_TYPE,
                HitTestResult.EMAIL_TYPE,
                HitTestResult.GEO_TYPE,
                HitTestResult.PHONE_TYPE,
                HitTestResult.SRC_ANCHOR_TYPE,
                HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> true

                else -> false
            }
        }

        private fun syncSourceImageMode() {
            if (this !== currentWebView || !documentReady || preparedChapter?.sourceImages == null) return
            val mode = TextReaderImageClickPolicy.mode(AppConfig.clickImgWay, preparedChapter?.sourceImages)
            if (appliedSourceImageMode == mode) return
            appliedSourceImageMode = mode
            evaluateJavascript("(function(){var a=window.__legadoEpub;if(a&&a.token==$token&&a.setTextImageMode)a.setTextImageMode(${JSONObject.quote(mode)});})()", null)
        }

        private fun addRawVelocityMovement(event: MotionEvent) {
            val tracker = velocityTracker ?: return
            val rawEvent = MotionEvent.obtain(event)
            rawEvent.offsetLocation(event.rawX - event.x, event.rawY - event.y)
            tracker.addMovement(rawEvent)
            rawEvent.recycle()
        }

        private fun cancelWebTouch(event: MotionEvent) {
            val cancel = MotionEvent.obtain(event).apply {
                action = MotionEvent.ACTION_CANCEL
            }
            super.onTouchEvent(cancel)
            cancel.recycle()
        }
    }

    private fun markEmbeddedInteraction() {
        lastEmbeddedInteractionAt = SystemClock.uptimeMillis()
    }

    private fun reportEmbeddedInteraction(token: Long, interactionId: Long, active: Boolean) {
        val view = listOfNotNull(currentWebView, standbyWebView).firstOrNull { it.token == token } ?: return
        if (token != generation || view !== currentWebView) return
        if (view.setEmbeddedInteraction(interactionId, active) && active) markEmbeddedInteraction()
    }

    private fun reportAnnotationState(token: Long, visible: Boolean) {
        val view = listOfNotNull(currentWebView, standbyWebView).firstOrNull { it.token == token } ?: return
        if (token != generation || view !== currentWebView) return
        annotationVisible = visible
        if (visible) markEmbeddedInteraction()
    }

    private fun sourceImageViewReady(view: ReaderWebView): Boolean =
        !destroyed && !view.surfaceDestroyed && documentReady && view === currentWebView && view.parent === this &&
            !hostPaused && view.renderState.canCapture &&
            view.isShown && windowVisibility == VISIBLE && !hostOverlayCaptureBlocked &&
            !view.preloading && view.token == generation && view.preparedChapter === chapter &&
            session != null && session?.isClosed == false && view.boundSession === session &&
            pendingActivationView == null && pageAnimationOverlay == null && pageAnimator == null &&
            pageHandoffRequest == null && interactivePageTurn == null && !isSelectionPageTurnBlocked()

    fun isCurrentSourceImageAction(request: TextReaderImageActionRequest): Boolean =
        sourceImageViewReady(currentWebView) && session === request.session && chapter === request.chapter &&
            generation == request.generation && pageIndex == request.pageIndex &&
            currentLayoutRevision == request.layoutRevision &&
            chapter?.sourceImages?.actions?.get(request.imageId) === request.action &&
            TextReaderImageClickPolicy.allowsAction(AppConfig.clickImgWay, chapter?.sourceImages)

    private fun reportSourceImageAction(view: ReaderWebView, event: TextReaderImageActionGate.Event) {
        val currentChapter = chapter ?: return
        val currentSession = session ?: return
        if (!sourceImageViewReady(view) || event.generation != generation ||
            event.page != pageIndex || event.revision != currentLayoutRevision ||
            !view.consumeTemplateSourceTap()
        ) return
        val action = sourceImageActionGate.accept(
            currentChapter, generation, pageIndex, currentLayoutRevision, event,
            sourceImageViewReady(view) && TextReaderImageClickPolicy.allowsAction(AppConfig.clickImgWay, currentChapter.sourceImages),
            SystemClock.uptimeMillis()
        ) ?: return
        markEmbeddedInteraction()
        listener?.onSourceImageAction(TextReaderImageActionRequest(
            currentSession, currentChapter, generation, pageIndex, currentLayoutRevision, event.imageId, action
        ))
    }

    private fun onTemplateFailure(view: ReaderWebView, token: Long, message: String) {
        if (destroyed || view.token != token) return
        val template = view.preparedChapter?.readerTemplate ?: return
        view.cancelTemplateWatchdog()
        if (view.preloading) {
            discardFailedPreload(view)
        } else if (token == generation) {
            failPendingLoad(view, token, "阅读模板：$message", EpubTemplateException(template.contentHash(), message))
        }
    }

    /** The only native entry available to template frames requires a host-only nonce. */
    private class TemplateBridge(layer: EpubDirectWebLayer, view: ReaderWebView, private val secret: String) {
        private val layerRef = WeakReference(layer)
        private val viewRef = WeakReference(view)
        private val delegate = Bridge(layer, view)

        @JavascriptInterface
        fun post(key: String, raw: String) {
            if (key != secret || raw.length > 262_144) return
            layerRef.get()?.post dispatch@{
                val layer = layerRef.get() ?: return@dispatch
                val view = viewRef.get() ?: return@dispatch
                if (view.preparedChapter?.readerTemplate == null || view.templateSecret != secret) return@dispatch
                val event = EpubTemplateBridgePolicy.parse(raw, view.token) ?: return@dispatch
                val p = event.payload
                val token = event.token
                fun int(name: String) = p.get(name).asInt
                fun long(name: String) = p.get(name).asLong
                fun string(name: String) = p.get(name).asString
                when (event.type) {
                    "stable" -> delegate.onStable(token)
                    "error" -> layer.onTemplateFailure(view, token, string("message"))
                    // This dispatch already runs on the UI thread. Posting these again
                    // lets an older pending notification overtake a fresh JS result.
                    "metrics" -> layer.reportMetrics(token, int("pageCount"), int("pageIndex"), long("layoutRevision"))
                    "renderState" -> layer.reportRenderState(view, token, long("visualRevision"), p.get("layoutPending").asBoolean)
                    "contentChanged" -> layer.reportTemplateContentChanged(view, token, long("revision"))
                    "textPosition" -> delegate.onTextPosition(token, int("page"), long("revision"), int("offset"))
                    "selection" -> delegate.onSelection(token, p.toString())
                    "annotationState" -> delegate.onAnnotationState(token, p.get("visible").asBoolean)
                    "boundary" -> {
                        val direction = int("direction")
                        if (view === layer.currentWebView && token == layer.generation &&
                            layer.documentReady && layer.isVerticalMode() && !view.preloading &&
                            !layer.annotationVisible && !layer.hostOverlayCaptureBlocked && !layer.hostPaused &&
                            !layer.isSelectionPageTurnBlocked() && !view.hasLongPressSelectionGesture() &&
                            !layer.isPageTurnBusy() &&
                            (if (direction < 0) layer.pageIndex == 0 else layer.pageIndex == layer.pageCount - 1)) {
                            layer.dispatchBoundary(direction)
                        }
                    }
                    "sourceImage" -> delegate.onSourceImage(token, int("page"), long("revision"), string("imageId"), long("sequence"))
                    "image" -> delegate.onImage(token, string("url"))
                    "link" -> delegate.onLink(token, string("url"))
                    "embeddedInteraction" -> delegate.onEmbeddedInteraction(token, long("interactionId"), p.get("active").asBoolean)
                }
            }
        }
    }

    private class Bridge(layer: EpubDirectWebLayer, view: ReaderWebView) {
        private val layerRef = WeakReference(layer)
        private val viewRef = WeakReference(view)

        @JavascriptInterface
        fun onSourceImage(token: Long, page: Int, revision: Long, imageId: String, sequence: Long) {
            if (imageId.length > 64) return
            val event = TextReaderImageActionGate.Event(token, page, revision, imageId, sequence)
            layerRef.get()?.post {
                val layer = layerRef.get() ?: return@post
                val view = viewRef.get() ?: return@post
                layer.reportSourceImageAction(view, event)
            }
        }

        @JavascriptInterface
        fun onStable(token: Long) {
            layerRef.get()?.post { layerRef.get()?.onDocumentStable(token) }
        }

        @JavascriptInterface
        fun onFontError(token: Long, message: String) {
            layerRef.get()?.post { layerRef.get()?.onReaderFontError(token, message) }
        }

        @JavascriptInterface
        fun onMetrics(token: Long, pageCount: Int, pageIndex: Int, layoutRevision: Long) {
            layerRef.get()?.post {
                layerRef.get()?.reportMetrics(token, pageCount, pageIndex, layoutRevision)
            }
        }

        @JavascriptInterface
        fun onRenderState(token: Long, visualRevision: Long, layoutPending: Boolean) {
            layerRef.get()?.post {
                val layer = layerRef.get() ?: return@post
                val view = viewRef.get() ?: return@post
                layer.reportRenderState(view, token, visualRevision, layoutPending)
            }
        }

        @JavascriptInterface
        fun onTextPosition(token: Long, page: Int, revision: Long, offset: Int) {
            layerRef.get()?.post {
                val layer = layerRef.get() ?: return@post
                val chapter = layer.chapter ?: return@post
                if (chapter.sourceChapterUrl == null || token != layer.generation ||
                    page != layer.pageIndex || revision != layer.currentLayoutRevision ||
                    !layer.documentReady || layer.currentWebView.token != token || offset < 0 ||
                    layer.isSelectionPageTurnBlocked() || layer.pageAnimationOverlay != null ||
                    layer.pageAnimator != null || layer.pageHandoffRequest != null) return@post
                val boundedOffset = offset.coerceAtMost(chapter.plainText.length)
                if (layer.textPositionToken == token && layer.textPositionPage == page &&
                    layer.textPositionRevision == revision && layer.textPositionOffset == boundedOffset) return@post
                layer.textPositionToken = token
                layer.textPositionPage = page
                layer.textPositionRevision = revision
                layer.textPositionOffset = boundedOffset
                layer.position?.let { layer.listener?.onPositionChanged(it) }
            }
        }

        @JavascriptInterface
        fun onSelection(token: Long, json: String) {
            layerRef.get()?.post { layerRef.get()?.reportSelection(token, json) }
        }

        @JavascriptInterface
        fun onHighlightPage(token: Long, page: Int) {
            layerRef.get()?.post { layerRef.get()?.reportHighlightPage(token, page) }
        }

        @JavascriptInterface
        fun onLink(token: Long, url: String) {
            layerRef.get()?.post {
                layerRef.get()?.takeIf { token == it.generation }?.let {
                    it.markEmbeddedInteraction()
                    it.listener?.onLinkClicked(url)
                }
            }
        }

        @JavascriptInterface
        fun onFootnote(token: Long, url: String) {
            layerRef.get()?.post {
                layerRef.get()?.takeIf { token == it.generation }?.let {
                    it.markEmbeddedInteraction()
                    it.listener?.onFootnoteClicked(url)
                }
            }
        }

        @JavascriptInterface
        fun onAnnotationState(token: Long, visible: Boolean) {
            layerRef.get()?.post {
                layerRef.get()?.reportAnnotationState(token, visible)
            }
        }

        @JavascriptInterface
        fun onEmbeddedInteraction(token: Long, interactionId: Long, active: Boolean) {
            layerRef.get()?.post {
                layerRef.get()?.reportEmbeddedInteraction(token, interactionId, active)
            }
        }

        @JavascriptInterface
        fun onImage(token: Long, url: String) {
            layerRef.get()?.post {
                layerRef.get()?.takeIf { token == it.generation }?.let {
                    it.markEmbeddedInteraction()
                    it.listener?.onImageClicked(url)
                }
            }
        }

    }

    companion object {
        internal const val DOCUMENT_MIME_TYPE = "text/html"
        private const val BRIDGE_NAME = "LegadoEpubBridge"
        private const val TEMPLATE_BRIDGE_NAME = "LegadoTemplateHost"
        private const val TEMPLATE_STABLE_TIMEOUT_MS = EpubRenderTimeoutPolicy.TEMPLATE_STARTUP_MS
        private const val WebViewBlank = "about:blank"
        private const val MAX_RESOURCE_FAILURE_REPORTS = 32
        private const val MAX_PAGE_HANDOFF_VERIFY_ATTEMPTS = 16
        private const val PAGE_HANDOFF_RETRY_MS = 48L
        private const val PAGE_HANDOFF_TIMEOUT_MS = 1_800L
        private const val PAGE_HANDOFF_RESTORE_TIMEOUT_MS = 600L
        private const val READY_RETRY_MS = 40L
        private const val DOCUMENT_READY_PROBE_DELAY_MS = 80L
        private const val DOCUMENT_LOAD_TIMEOUT_MS = 12_000L
        // A cold WebView may spend several frames measuring a large XHTML document after
        // resources/fonts settle. Keep this finite, but leave enough room for that one bounded
        // layout pass so a slow first chapter is not reported as a stability failure.
        private const val RUNTIME_STABLE_TIMEOUT_MS = EpubRenderTimeoutPolicy.DIRECT_STARTUP_MS
        private const val PRELOAD_VERIFICATION_TIMEOUT_MS = 6_000L
        // Resource/font reflow is bounded but may legitimately span several
        // compositor frames on a cold WebView. Keep the timeout finite without
        // turning a slow first chapter into a false render failure.
        private const val PAGE_ACTIVATION_TIMEOUT_MS = 8_000L
        // Keep candidate WebViews compositor-active without allowing several preloads to
        // become perceptible through a transparent foreground reading surface.
        private const val CANDIDATE_RENDER_ALPHA = 0.001f
        private const val PAGE_ANIMATION_COMMIT_TIMEOUT_MS = 900L
        private const val PAGE_HANDOFF_RECOVERY_TIMEOUT_MS = 600L
        private const val PAGE_JAVASCRIPT_TIMEOUT_MS = 160L
        private const val VISUAL_FRAME_FALLBACK_MS = 96L
        private const val SNAPSHOT_CAPTURE_RETRY_MS = 48L
        private const val SNAPSHOT_DIAGNOSTIC_TIMEOUT_MS = 160L
        private const val MAX_SNAPSHOT_CAPTURE_RETRIES = 3
        private const val MAX_BLOCKED_SNAPSHOT_RETRIES = 16
        private const val MAX_QUEUED_PAGE_TURN_RUNS = 8
        private const val PAGE_ANIMATION_MATERIAL_TIMEOUT_MS = 180L
        private const val ADJACENT_FRAME_INITIALIZATION_BACKOFF_MS = 30_000L
        private const val SNAPSHOT_SAMPLE_GRID = 64
        private const val FINAL_FRAME_FALLBACK_MS = 48L
        private const val SELECTION_REPORT_SETTLE_MS = 96L
        private val LINEAR_INTERPOLATOR = LinearInterpolator()
        private val LINKED_COVER_INTERPOLATOR = PathInterpolator(0.4f, 0f, 0.2f, 1f)
        private const val LIVE_TARGET_LAYER_RELEASE_TIMEOUT_MS = 500L
        private const val FOREGROUND_REVEAL_STAGING_ALPHA = 0.001f
        private const val FOREGROUND_REVEAL_TIMEOUT_MS = 250L
        private const val STYLE_RELOAD_DEBOUNCE_MS = 80L
        private const val LAYOUT_REFRESH_MIN_INTERVAL_MS = 80
        private const val LAYOUT_REFRESH_IMMEDIATE_MIN_INTERVAL_MS = 40
        private const val LAYOUT_REFRESH_MAX_INTERVAL_MS = 500
        private const val LAYOUT_REFRESH_COST_MULTIPLIER = 10
        private const val BOUNDARY_DEBOUNCE_MS = 500L
        private const val CHAPTER_TURN_TIMEOUT_MS = 12_000L
        private const val VERTICAL_SCROLL_SETTLE_MS = 500L
        private const val RENDER_PROCESS_RECOVERY_DELAY_MS = 48L
        private const val RENDER_PROCESS_RECOVERY_WINDOW_MS = 10_000L
        private const val MAX_RENDER_PROCESS_RECOVERIES = 2
        private const val ANNOTATION_DISMISS_CALLBACK_TIMEOUT_MS = 500L
        private const val CLEAR_SELECTION_SCRIPT =
            "(function(){var a=window.__legadoEpub;if(a&&a.clearSelection){a.clearSelection();return;}" +
                "var s=window.getSelection&&window.getSelection();if(s)s.removeAllRanges();})();"
        private const val DISMISS_ANNOTATION_SCRIPT =
            "(function(){var a=window.__legadoEpub;return !!(a&&a.dismissAnnotation&&a.dismissAnnotation());})();"
        private const val PAUSE_MEDIA_SCRIPT =
            "(function(){document.querySelectorAll('audio,video').forEach(function(m){try{m.pause();}catch(_){}});})();"
        private const val MEASURE_SCRIPT =
            "(function(){if(!window.__legadoEpub)return null;return JSON.stringify(window.__legadoEpub.metrics());})();"
        private const val MAX_RUNTIME_METRICS_RETRIES = 8
        private const val RUNTIME_METRICS_RETRY_MS = 48L
        private const val RUNTIME_METRICS_TIMEOUT_MS = 800L
        private const val MEASURE_VIEWPORT_SCRIPT =
            "(function(){if(!window.__legadoEpub)return null;return JSON.stringify(window.__legadoEpub.metrics(true));})();"
        private const val SNAPSHOT_DIAGNOSTICS_SCRIPT =
            "(function(){var r=document.scrollingElement||document.documentElement;" +
                "return JSON.stringify({scrollLeft:r?Number(r.scrollLeft)||0:0," +
                "windowScrollX:Number(window.scrollX)||0," +
                "documentScrollLeft:Number(document.documentElement&&document.documentElement.scrollLeft)||0," +
                "bodyScrollLeft:Number(document.body&&document.body.scrollLeft)||0});})();"
        private val RUNTIME_SCRIPT_TEMPLATE by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            appCtx.assets.open("epub/page-alignment.js").bufferedReader().use { it.readText() } + "\n" +
                appCtx.assets.open("epub/direct-runtime.js").bufferedReader().use { it.readText() }
        }

        private data class WebMetrics(
            val pageCount: Int,
            val pageIndex: Int,
            val resourcesReady: Boolean,
            val resourcesFailed: Boolean,
            val layoutRevision: Long,
            val layoutPending: Boolean,
            val visualRevision: Long,
            val contentRevision: Long,
            val sourceImagesPending: Int,
            val activationTargetRevision: Long,
            val activationTargetSatisfied: Boolean,
            val hasRenderableContent: Boolean,
            val hasViewportContent: Boolean
        )

        private fun resolvePerformanceBudget(context: Context): EpubPerformanceBudget {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            return EpubDirectWebViewBudgetPolicy.resolve(
                modeKey = AppConfig.epubCoreScheduleMode,
                isLowRamDevice = activityManager?.isLowRamDevice == true,
                memoryClassMb = activityManager?.memoryClass ?: 256
            )
        }

        private fun snapshotPixelBudget(context: Context): Long {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            return EpubDirectWebViewBudgetPolicy.maxSnapshotPixels(
                isLowRamDevice = activityManager?.isLowRamDevice == true,
                memoryClassMb = activityManager?.memoryClass ?: 256
            )
        }

        private fun adjacentFrameCacheCapacity(context: Context, width: Int, height: Int, requested: Int): Int {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            return EpubDirectWebViewBudgetPolicy.maxAdjacentFrames(
                requested, width, height,
                isLowRamDevice = activityManager?.isLowRamDevice == true,
                memoryClassMb = activityManager?.memoryClass ?: 256
            )
        }

        private fun EpubDirectChapter?.isRtlLayout(): Boolean {
            return (this?.pageLayoutDirection ?: this?.pageProgressionDirection).equals("rtl", true)
        }

        private fun EpubDirectChapter?.requiresRenderableContent(): Boolean {
            return this != null && EpubDirectRenderableContentPolicy.requiresRenderableContent(
                hasText = plainText.isNotBlank(),
                singlePage = layoutMode.singlePage,
                fullPageArtwork = fullPageArtwork,
                duokanGallery = duokanGallery
            )
        }

        private fun runtimeScript(
            token: Long,
            chapter: EpubDirectChapter,
            config: EpubCoreLayoutConfig
        ): String {
            val vertical = config.scrollMode && !chapter.layoutMode.scalesPublisherViewport
            val fixed = chapter.layoutMode.singlePage
            val scaled = chapter.layoutMode.scalesPublisherViewport
            val publisherStyled = chapter.layoutMode == EpubDirectLayoutMode.PUBLISHER_STYLED
            val readerTypography = EpubDirectReaderTypographyPolicy.isSupported(
                EpubDirectReaderTypographyPolicy.Input(
                    layoutMode = chapter.layoutMode,
                    fullPageArtwork = chapter.fullPageArtwork,
                    implicitSinglePage = chapter.implicitSinglePage,
                    duokanGallery = chapter.duokanGallery,
                    scripted = chapter.scripted
                )
            )
            val rtl = chapter.isRtlLayout()
            val startId = chapter.startFragmentId?.let(JSONObject::quote) ?: "null"
            val endId = chapter.endFragmentId?.let(JSONObject::quote) ?: "null"
            val sourceWidth = chapter.viewportWidth ?: 0f
            val sourceHeight = chapter.viewportHeight ?: 0f
            val readerChrome = EpubDirectReaderChromePolicy.resolve(
                requested = config.readerChrome,
                input = EpubDirectReaderChromePolicy.Input(
                    layoutMode = chapter.layoutMode,
                    fullPageArtwork = chapter.fullPageArtwork,
                    implicitSinglePage = chapter.implicitSinglePage,
                    duokanGallery = chapter.duokanGallery,
                    scripted = chapter.scripted,
                    scrollMode = config.scrollMode
                ),
                pageHeightPx = config.pageHeightPx
            )
            val replacements = mapOf(
                "__LEGADO_TOKEN__" to token.toString(),
                "__LEGADO_TEXT_IMAGE_MODE__" to JSONObject.quote(TextReaderImageClickPolicy.mode(AppConfig.clickImgWay, chapter.sourceImages)),
                "__LEGADO_VERTICAL__" to vertical.toString(),
                "__LEGADO_FIXED__" to fixed.toString(),
                "__LEGADO_SCALED__" to scaled.toString(),
                "__LEGADO_PUBLISHER_STYLED__" to publisherStyled.toString(),
                "__LEGADO_READER_TYPOGRAPHY__" to readerTypography.toString(),
                "__LEGADO_BOTTOM_JUSTIFY__" to config.textBottomJustify.toString(),
                "__LEGADO_PUBLISHER_FULLSCREEN__" to chapter.publisherFullscreen.toString(),
                "__LEGADO_PADDING_LEFT__" to config.readerPaddingLeftPx.toString(),
                "__LEGADO_PADDING_TOP__" to config.readerPaddingTopPx.toString(),
                "__LEGADO_PADDING_RIGHT__" to config.readerPaddingRightPx.toString(),
                "__LEGADO_PADDING_BOTTOM__" to config.readerPaddingBottomPx.toString(),
                "__LEGADO_SAFE_INSET_LEFT__" to config.readerSafeInsetLeftPx.toString(),
                "__LEGADO_SAFE_INSET_TOP__" to config.readerSafeInsetTopPx.toString(),
                "__LEGADO_SAFE_INSET_RIGHT__" to config.readerSafeInsetRightPx.toString(),
                "__LEGADO_SAFE_INSET_BOTTOM__" to config.readerSafeInsetBottomPx.toString(),
                "__LEGADO_RTL__" to rtl.toString(),
                "__LEGADO_START_ID__" to startId,
                "__LEGADO_END_ID__" to endId,
                "__LEGADO_ANNOTATION_TITLE__" to JSONObject.quote(appCtx.getString(R.string.epub_annotation_title)),
                "__LEGADO_ANNOTATION_CLOSE__" to JSONObject.quote(appCtx.getString(R.string.epub_annotation_close)),
                "__LEGADO_ANNOTATION_BACK__" to JSONObject.quote(appCtx.getString(R.string.epub_annotation_back)),
                "__LEGADO_READER_FONT__" to (
                    !config.readerFontUrl.isNullOrBlank() &&
                        !config.readerFontPath.isNullOrBlank() &&
                        !config.readerFontRevision.isNullOrBlank()
                    ).toString(),
                "__LEGADO_CHROME_ENABLED__" to readerChrome.enabled.toString(),
                "__LEGADO_CHROME_HEADER_ENABLED__" to readerChrome.headerEnabled.toString(),
                "__LEGADO_CHROME_FOOTER_ENABLED__" to readerChrome.footerEnabled.toString(),
                "__LEGADO_CHROME_HIDE_HEADER_FIRST__" to
                    readerChrome.hideHeaderOnChapterFirstPage.toString(),
                "__LEGADO_CHROME_HEADER_HEIGHT__" to readerChrome.headerHeightPx.toString(),
                "__LEGADO_CHROME_FOOTER_HEIGHT__" to readerChrome.footerHeightPx.toString(),
                "__LEGADO_CHROME_HEADER_PADDING_LEFT__" to readerChrome.headerPaddingLeftPx.toString(),
                "__LEGADO_CHROME_HEADER_PADDING_TOP__" to readerChrome.headerPaddingTopPx.toString(),
                "__LEGADO_CHROME_HEADER_PADDING_RIGHT__" to readerChrome.headerPaddingRightPx.toString(),
                "__LEGADO_CHROME_HEADER_PADDING_BOTTOM__" to readerChrome.headerPaddingBottomPx.toString(),
                "__LEGADO_CHROME_FOOTER_PADDING_LEFT__" to readerChrome.footerPaddingLeftPx.toString(),
                "__LEGADO_CHROME_FOOTER_PADDING_TOP__" to readerChrome.footerPaddingTopPx.toString(),
                "__LEGADO_CHROME_FOOTER_PADDING_RIGHT__" to readerChrome.footerPaddingRightPx.toString(),
                "__LEGADO_CHROME_FOOTER_PADDING_BOTTOM__" to readerChrome.footerPaddingBottomPx.toString(),
                "__LEGADO_CHROME_TEXT_SIZE__" to readerChrome.textSizePx.toString(),
                "__LEGADO_CHROME_TEXT_COLOR__" to readerChrome.textColor.toString(),
                "__LEGADO_CHROME_DIVIDER_COLOR__" to readerChrome.dividerColor.toString(),
                "__LEGADO_CHROME_HEADER_DIVIDER__" to readerChrome.headerDividerEnabled.toString(),
                "__LEGADO_CHROME_FOOTER_DIVIDER__" to readerChrome.footerDividerEnabled.toString(),
                "__LEGADO_REFRESH_MIN__" to LAYOUT_REFRESH_MIN_INTERVAL_MS.toString(),
                "__LEGADO_REFRESH_IMMEDIATE__" to LAYOUT_REFRESH_IMMEDIATE_MIN_INTERVAL_MS.toString(),
                "__LEGADO_REFRESH_MAX__" to LAYOUT_REFRESH_MAX_INTERVAL_MS.toString(),
                "__LEGADO_REFRESH_COST_MULTIPLIER__" to LAYOUT_REFRESH_COST_MULTIPLIER.toString(),
                "__LEGADO_BRIDGE__" to BRIDGE_NAME,
                "__LEGADO_SOURCE_WIDTH__" to sourceWidth.toString(),
                "__LEGADO_SOURCE_HEIGHT__" to sourceHeight.toString()
            )
            return replacements.entries.fold(RUNTIME_SCRIPT_TEMPLATE) { script, (placeholder, value) ->
                script.replace(placeholder, value)
            }.also { script ->
                check(!script.contains("__LEGADO_")) {
                    "Unresolved EPUB runtime placeholder"
                }
            }
        }
    }

    private enum class RuntimeTerminalKind {
        Pending,
        Ready,
        ReaderFontError
    }

    private data class RuntimeTerminal(
        val kind: RuntimeTerminalKind,
        val message: String?
    )

    private data class AnimationMaterialWait(
        val snapshotKey: EpubCommittedPageSnapshotKey,
        val logicalDirection: Int,
        val targetChapterIndex: Int,
        val targetPageIndex: Int?,
        val requiresAdjacent: Boolean,
        var fallbackAllowed: Boolean = false
    ) {
        fun matches(other: AnimationMaterialWait): Boolean {
            return snapshotKey == other.snapshotKey &&
                logicalDirection == other.logicalDirection &&
                targetChapterIndex == other.targetChapterIndex &&
                targetPageIndex == other.targetPageIndex &&
                requiresAdjacent == other.requiresAdjacent
        }
    }

    private data class PendingReadAloudCue(
        val sequence: Long,
        val token: Long,
        val chapterIndex: Int,
        val text: String,
        val offset: Int,
        val progress: Float
    )
}
