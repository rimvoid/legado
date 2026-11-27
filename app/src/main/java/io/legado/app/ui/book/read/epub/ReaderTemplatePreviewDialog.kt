package io.legado.app.ui.book.read.epub

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.TextPaint
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.model.localBook.epubcore.direct.EpubDirectPosition
import io.legado.app.model.localBook.epubcore.direct.EpubDirectSession
import io.legado.app.model.localBook.epubcore.layout.EpubCoreLayoutConfig
import io.legado.app.model.localBook.epubcore.layout.EpubReaderChromeData
import io.legado.app.model.localBook.epubcore.template.EpubReaderTemplate
import io.legado.app.model.localBook.epubcore.template.EpubTemplateLayoutPolicy
import io.legado.app.ui.book.read.config.rememberReaderMenuDialogStyle
import io.legado.app.ui.widget.compose.AppDialogStyle
import io.legado.app.ui.widget.compose.ComposeDialogFragment
import io.legado.app.ui.widget.compose.LegadoMiuixActionButton
import io.legado.app.ui.widget.compose.toMiuixPalette
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.legado.app.ui.widget.compose.LegadoCouiTheme
import java.util.UUID

/** A real reader surface whose position and lifetime are independent of the main reader. */
class ReaderTemplatePreviewDialog : ComposeDialogFragment() {
    override val dialogWidth = ViewGroup.LayoutParams.MATCH_PARENT
    override val dialogHeight = ViewGroup.LayoutParams.MATCH_PARENT

    private var registry: ReaderTemplatePreviewRegistry? = null
    private var request: ReaderTemplatePreviewRequest? = null
    private var layer: EpubDirectWebLayer? = null
    private var prepareJob: Job? = null
    private var prepareSequence = 0L
    private var backCheckJob: Job? = null
    private var backCheckSequence = 0L
    private var backCheckPending = false
    private var dismissRequested = false
    private var viewportWidth = 0
    private var viewportHeight = 0
    private var preparing = false
    private var ready by mutableStateOf(false)
    private var position by mutableStateOf<EpubDirectPosition?>(null)
    private var errorMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registry = ViewModelProvider(requireActivity())[ReaderTemplatePreviewRegistry::class.java]
        request = registry?.requests?.get(arguments?.getString(ARG_REQUEST))
        if (request?.session?.isClosed != false) {
            context?.toastOnUi(R.string.reader_template_preview_session_missing)
            closePreview()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            if (this is ComponentDialog) {
                // Replace the base dialog's key listener; otherwise it dismisses
                // this window before the WebView can close its image overlay.
                setOnKeyListener(null)
                onBackPressedDispatcher.addCallback(
                    this@ReaderTemplatePreviewDialog,
                    object : OnBackPressedCallback(true) {
                        override fun handleOnBackPressed() = handlePreviewBack()
                    }
                )
            } else {
                setOnKeyListener { _, keyCode, event ->
                    if (keyCode != KeyEvent.KEYCODE_BACK) false else {
                        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) handlePreviewBack()
                        true
                    }
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val current = request?.takeUnless { it.session.isClosed }
        if (dismissRequested || current == null) {
            if (!dismissRequested) {
                context?.toastOnUi(R.string.reader_template_preview_session_missing)
                closePreview()
            }
            return View(requireContext())
        }
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { LegadoCouiTheme { PreviewContent(current) } }
        }
    }

    @Composable
    private fun PreviewContent(current: ReaderTemplatePreviewRequest) {
        val style = rememberReaderMenuDialogStyle()
        DisposableEffect(Unit) { onDispose { releaseLayer() } }
        Column(
            modifier = Modifier.fillMaxSize().background(style.surface)
                .statusBarsPadding().navigationBarsPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.reader_template_preview_title), color = style.primaryText, fontSize = 17.sp)
            Text(current.template.name, color = style.secondaryText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            AndroidView(
                factory = { createLayer(it, current) },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            // Reserve a constant status height: an error must not resize the viewport and restart pagination.
            Box(modifier = Modifier.fillMaxWidth().height(58.dp).verticalScroll(rememberScrollState())) {
                val position = position
                Text(
                    text = errorMessage ?: if (ready && position != null) {
                        stringResource(R.string.reader_template_preview_position, position.pageIndex + 1, position.pageCount)
                    } else stringResource(R.string.reader_template_preview_loading),
                    color = if (errorMessage != null) style.danger else style.secondaryText,
                    fontSize = 13.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PreviewAction(R.string.reader_template_preview_previous, style, Modifier.weight(1f), ready && (position?.pageIndex ?: 0) > 0) { turnPage(-1) }
                PreviewAction(R.string.reader_template_preview_next, style, Modifier.weight(1f), ready && (position?.let { it.pageIndex + 1 < it.pageCount } == true)) { turnPage(1) }
                PreviewAction(R.string.reader_template_preview_close, style, Modifier.weight(1f)) { closePreview() }
            }
        }
    }

    @Composable
    private fun PreviewAction(label: Int, style: AppDialogStyle, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
        LegadoMiuixActionButton(
            text = stringResource(label), palette = style.toMiuixPalette(),
            modifier = modifier.alpha(if (enabled) 1f else .45f).semantics { if (!enabled) disabled() },
            onClick = { if (enabled) onClick() }
        )
    }

    private fun createLayer(context: Context, current: ReaderTemplatePreviewRequest): EpubDirectWebLayer {
        releaseLayer()
        return EpubDirectWebLayer(context, frameRenderer = true).also { preview ->
            layer = preview
            preview.setBackgroundColor(current.config.backgroundColor)
            preview.bindBorrowedSession(current.session)
            preview.updateReaderChromeData(current.fields)
            preview.setListener(object : EpubDirectWebLayer.Listener {
                override fun onReady(position: EpubDirectPosition) {
                    if (layer !== preview || preparing || dismissRequested) return
                    if (current.session.isClosed) { showSessionUnavailable(); return }
                    this@ReaderTemplatePreviewDialog.position = position
                    errorMessage = null
                    ready = true
                }

                override fun onPositionChanged(position: EpubDirectPosition) {
                    if (layer !== preview || preparing || dismissRequested) return
                    if (current.session.isClosed) { showSessionUnavailable(); return }
                    this@ReaderTemplatePreviewDialog.position = position
                }

                override fun onError(message: String, throwable: Throwable?) {
                    if (layer !== preview || preparing || dismissRequested) return
                    errorMessage = if (current.session.isClosed) getString(R.string.reader_template_preview_session_missing) else message
                    ready = false
                }

                // Links, source-image actions and chapter boundaries deliberately retain the no-op defaults.
                // Only the reading Activity may execute source actions or persist reading progress.
            })
            preview.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                prepare(preview, current, right - left, bottom - top)
            }
        }
    }

    private fun prepare(preview: EpubDirectWebLayer, current: ReaderTemplatePreviewRequest, width: Int, height: Int) {
        if (layer !== preview || dismissRequested) return
        if (current.session.isClosed) { showSessionUnavailable(); return }
        if (width <= 0 || height <= 0 || (width == viewportWidth && height == viewportHeight)) return
        viewportWidth = width
        viewportHeight = height
        val sequence = ++prepareSequence
        prepareJob?.cancel()
        preparing = true
        ready = false
        errorMessage = null
        val initialPosition = position
        val initialPage = initialPosition?.pageIndex ?: 0
        val initialFragment = initialPosition?.characterPosition?.let { "__legado_text_$it" }
        val config = EpubTemplateLayoutPolicy.isolate(
            current.config.copy(pageWidthPx = width, pageHeightPx = height, readerTemplate = current.template),
            resources.displayMetrics.density
        )
        prepareJob = lifecycleScope.launch {
            try {
                delay(100) // Coalesce layout changes while the window is appearing or resizing.
                // Do not interrupt a shared session loader: another reader may be awaiting the same chapter.
                val chapter = withContext(Dispatchers.IO) { current.session.prepareChapter(current.chapterIndex, config) }
                if (sequence != prepareSequence || layer !== preview || dismissRequested) return@launch
                if (current.session.isClosed) { showSessionUnavailable(); return@launch }
                preparing = false
                preview.showChapter(chapter, config, initialPageIndex = initialPage,
                    initialProgress = initialPosition?.progress?.takeIf { initialFragment == null },
                    initialFragmentId = initialFragment)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (sequence == prepareSequence && layer === preview) {
                    preparing = false
                    errorMessage = if (current.session.isClosed) getString(R.string.reader_template_preview_session_missing)
                        else error.localizedMessage ?: error.javaClass.simpleName
                    ready = false
                }
            }
        }
    }

    private fun turnPage(direction: Int) {
        if (!ready) return
        if (request?.session?.isClosed != false) { showSessionUnavailable(); return }
        val preview = layer ?: return
        val position = preview.position ?: return
        val target = position.pageIndex + direction
        if (target in 0 until position.pageCount) preview.setPage(target, animate = false)
    }

    private fun showSessionUnavailable() {
        prepareSequence++
        prepareJob?.cancel()
        prepareJob = null
        preparing = false
        ready = false
        errorMessage = getString(R.string.reader_template_preview_session_missing)
    }

    private fun handlePreviewBack() {
        if (!isCancelable || dismissRequested || backCheckPending) return
        val preview = layer ?: run { closePreview(); return }
        val sequence = ++backCheckSequence
        backCheckPending = true
        // The WebView has its own callback timeout. This independent deadline
        // also covers a detached surface or an exception before it can register.
        backCheckJob = lifecycleScope.launch {
            delay(BACK_CHECK_TIMEOUT_MS)
            finishPreviewBack(sequence, preview, annotationDismissed = false)
        }
        try {
            preview.dismissAnnotation { dismissed -> finishPreviewBack(sequence, preview, dismissed) }
        } catch (_: Exception) {
            finishPreviewBack(sequence, preview, annotationDismissed = false)
        }
    }

    private fun finishPreviewBack(sequence: Long, preview: EpubDirectWebLayer, annotationDismissed: Boolean) {
        if (sequence != backCheckSequence || !backCheckPending) return
        backCheckPending = false
        backCheckJob?.cancel()
        backCheckJob = null
        if (layer === preview && !dismissRequested && !annotationDismissed) closePreview()
    }

    private fun cancelBackCheck() {
        backCheckSequence++
        backCheckPending = false
        backCheckJob?.cancel()
        backCheckJob = null
    }

    private fun closePreview() {
        if (dismissRequested) return
        dismissRequested = true
        cancelBackCheck()
        dismissAllowingStateLoss()
    }

    private fun releaseLayer() {
        cancelBackCheck()
        prepareSequence++
        prepareJob?.cancel()
        prepareJob = null
        preparing = false
        ready = false
        val old = layer
        layer = null
        viewportWidth = 0
        viewportHeight = 0
        old?.setListener(null)
        old?.destroy() // bindBorrowedSession guarantees this never closes the main reader's session.
    }

    override fun onResume() {
        super.onResume()
        if (dismissRequested) return
        if (request?.session?.isClosed != false) {
            context?.toastOnUi(R.string.reader_template_preview_session_missing)
            closePreview()
            return
        }
        layer?.onHostResume()
    }
    override fun onPause() { layer?.onHostPause(); super.onPause() }
    override fun onDestroyView() { releaseLayer(); super.onDestroyView() }

    override fun onDismiss(dialog: DialogInterface) {
        dismissRequested = true
        releaseLayer()
        if (activity?.isChangingConfigurations != true) {
            arguments?.getString(ARG_REQUEST)?.let { registry?.requests?.remove(it) }
            request = null
        }
        super.onDismiss(dialog)
    }

    companion object {
        private const val ARG_REQUEST = "readerTemplatePreviewRequest"
        private const val TAG = "readerTemplatePreview"
        private const val BACK_CHECK_TIMEOUT_MS = 700L

        fun show(activity: FragmentActivity, session: EpubDirectSession, chapterIndex: Int,
                 template: EpubReaderTemplate, config: EpubCoreLayoutConfig, fields: EpubReaderChromeData) {
            val manager = activity.supportFragmentManager
            if (activity.isFinishing || activity.isDestroyed || manager.isStateSaved) return
            if (session.isClosed) { activity.toastOnUi(R.string.reader_template_preview_session_missing); return }
            (manager.findFragmentByTag(TAG) as? ReaderTemplatePreviewDialog)?.dismissAllowingStateLoss()
            val registry = ViewModelProvider(activity)[ReaderTemplatePreviewRegistry::class.java]
            val id = UUID.randomUUID().toString()
            registry.requests[id] = ReaderTemplatePreviewRequest(
                session, chapterIndex, template, config.copy(textPaint = TextPaint(config.textPaint)), fields
            )
            try {
                ReaderTemplatePreviewDialog().apply { arguments = Bundle().apply { putString(ARG_REQUEST, id) } }
                    .showNow(manager, TAG)
            } catch (error: Exception) {
                registry.requests.remove(id)
                throw error
            }
        }
    }
}

internal data class ReaderTemplatePreviewRequest(
    val session: EpubDirectSession,
    val chapterIndex: Int,
    val template: EpubReaderTemplate,
    val config: EpubCoreLayoutConfig,
    val fields: EpubReaderChromeData
)

/** References only: lifecycle cleanup must never close a borrowed session. */
internal class ReaderTemplatePreviewRegistry : ViewModel() {
    val requests = mutableMapOf<String, ReaderTemplatePreviewRequest>()
    override fun onCleared() { requests.clear(); super.onCleared() }
}
