package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.view.WindowInsets
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.text.StaticLayout
import android.text.TextPaint
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.get
import androidx.core.view.isVisible
import androidx.core.view.doOnLayout
import androidx.core.view.doOnPreDraw
import androidx.core.view.size
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.constant.PageAnim
import io.legado.app.constant.PreferKey
import io.legado.app.constant.Status
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookParagraphRule
import io.legado.app.data.entities.BookProgress
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppCloudStorage
import io.legado.app.help.AppWebDav
import io.legado.app.help.IntentData
import io.legado.app.help.TTS
import io.legado.app.help.ai.AiImageGalleryManager
import io.legado.app.help.ai.AiReadAloudRoleState
import io.legado.app.help.book.BookCloudEntryMode
import io.legado.app.help.book.BookCloudEntryModeStore
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.ParagraphRuleProcessor
import io.legado.app.help.book.ReadMenuCustomButtonExecutor
import io.legado.app.help.book.library.LibraryChapterManifestV3
import io.legado.app.help.book.library.LibraryChapterPayloadV3
import io.legado.app.help.book.library.LibraryCloudBackend
import io.legado.app.help.book.library.LibraryCloudChapterVersion
import io.legado.app.help.book.library.LibraryCloudCrypto
import io.legado.app.help.book.library.LibraryCloudKeys
import io.legado.app.help.book.library.LibraryCloudPaths
import io.legado.app.help.book.library.LibraryCloudSession
import io.legado.app.help.book.library.LibraryCloudState
import io.legado.app.help.book.library.LibraryCloudSync
import io.legado.app.help.book.library.LibraryContainerManager
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isEpub
import io.legado.app.help.book.usesDirectReader
import io.legado.app.help.book.ReaderTextPositionStore
import io.legado.app.model.localBook.epubcore.direct.TextReaderSessionProvider
import io.legado.app.model.localBook.epubcore.direct.TextReaderImageActionRequest
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isLocalTxt
import io.legado.app.help.book.isMobi
import io.legado.app.help.book.removeType
import io.legado.app.help.book.simulatedTotalChapterNum
import io.legado.app.help.book.update
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.BubblePackageManager
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadTipConfig
import io.legado.app.help.config.ShareNoteTemplateManager
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.readaloud.ReadAloudPlaybackState
import io.legado.app.help.readaloud.ReadAloudProgressState
import io.legado.app.help.source.getSourceType
import io.legado.app.help.storage.Backup
import io.legado.app.lib.dialogs.AndroidAlertBuilder
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.UiCorner
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.applyUiBodyTypefaceDeep
import io.legado.app.lib.theme.applyUiTitleTypeface
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.lib.theme.secondaryTextColor
import io.legado.app.lib.theme.themeCardColorOrDefault
import io.legado.app.lib.theme.uiTypeface
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.model.inheritNotShelfStateFrom
import io.legado.app.model.resolveStoredBookshelfState
import io.legado.app.model.ImageProvider
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setChapter
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.textHeight
import io.legado.app.model.localBook.EpubFile
import io.legado.app.model.localBook.epubcore.facade.EpubCoreProvider
import io.legado.app.model.localBook.epubcore.facade.EpubReadableChapterPolicy
import io.legado.app.model.localBook.epubcore.direct.EpubDirectChapter
import io.legado.app.model.localBook.epubcore.direct.EpubDirectPosition
import io.legado.app.model.localBook.epubcore.direct.EpubDirectSession
import io.legado.app.model.localBook.epubcore.font.EpubPreparedReaderFont
import io.legado.app.model.localBook.epubcore.font.EpubReaderFontPreparer
import io.legado.app.model.localBook.epubcore.layout.EpubCoreLayoutConfig
import io.legado.app.model.localBook.epubcore.layout.EpubReaderChromeConfig
import io.legado.app.model.localBook.epubcore.layout.EpubReaderChromeData
import io.legado.app.model.localBook.epubcore.layout.EpubReaderChromeLegacyFieldPolicy
import io.legado.app.model.localBook.epubcore.layout.EpubReaderChromeModePolicy
import io.legado.app.model.localBook.epubcore.template.EpubReaderTemplate
import io.legado.app.model.localBook.epubcore.template.EpubReaderTemplateStore
import io.legado.app.model.localBook.epubcore.template.EpubTemplateException
import io.legado.app.model.localBook.epubcore.template.EpubTemplateActiveClock
import io.legado.app.model.localBook.epubcore.template.EpubTemplateLayoutPolicy
import io.legado.app.model.localBook.MobiFile
import io.legado.app.receiver.NetworkChangedListener
import io.legado.app.receiver.TimeBatteryReceiver
import io.legado.app.ui.about.ReadRecordWidgetStore
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.book.bookmark.BookmarkDialog
import io.legado.app.ui.book.changesource.ChangeBookSourceDialog
import io.legado.app.ui.book.changesource.ChangeChapterSourceDialog
import io.legado.app.ui.book.character.BookCharacterManageActivity
import io.legado.app.ui.book.info.BookInfoStartActivityContract
import io.legado.app.ui.book.ShelfExitRequestGate
import io.legado.app.ui.book.read.config.AutoReadDialog
import io.legado.app.ui.book.read.config.BgTextConfigDialog.Companion.BG_COLOR
import io.legado.app.ui.book.read.config.BgTextConfigDialog.Companion.TEXT_ACCENT_COLOR
import io.legado.app.ui.book.read.config.BgTextConfigDialog.Companion.TEXT_COLOR
import io.legado.app.ui.book.read.config.MoreConfigDialog
import io.legado.app.ui.book.read.config.ParagraphRuleManageActivity
import io.legado.app.ui.book.read.config.HighlightRuleManageActivity
import io.legado.app.help.book.highlight.HighlightRules
import io.legado.app.ui.book.read.config.ParagraphRuleQuickDialog
import io.legado.app.ui.book.read.config.ReadMenuCustomButtonEditActivity
import io.legado.app.ui.book.read.config.ReadMenuButtonManageActivity
import io.legado.app.ui.book.read.config.ReadAloudDialog
import io.legado.app.ui.book.read.config.ReadStyleDialog
import io.legado.app.ui.book.read.config.ReaderTemplateDialog
import io.legado.app.ui.book.read.config.TipConfigDialog.Companion.TIP_COLOR
import io.legado.app.ui.book.read.config.TipConfigDialog.Companion.TIP_DIVIDER_COLOR
import io.legado.app.ui.book.read.page.ContentTextView
import io.legado.app.ui.book.read.page.ReadView
import io.legado.app.ui.book.read.page.LottieImageBitmapCache
import io.legado.app.ui.book.read.page.delegate.ScrollPageDelegate
import io.legado.app.ui.book.read.page.entities.PageDirection
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.book.read.page.provider.LayoutProgressListener
import io.legado.app.ui.book.read.epub.EpubChapterNavigationPolicy
import io.legado.app.ui.book.read.epub.EpubTemplateSafeAreaPolicy
import io.legado.app.ui.book.read.epub.EpubDirectAutoPager
import io.legado.app.ui.book.read.epub.EpubDirectFailureDiagnostics
import io.legado.app.ui.book.read.epub.EpubReadView
import io.legado.app.ui.book.read.epub.EpubLoadingWindowOverlay
import io.legado.app.ui.book.read.epub.ReaderTemplatePreviewDialog
import io.legado.app.ui.book.read.epub.EpubDirectInitialFragmentPolicy
import io.legado.app.ui.book.read.epub.EpubDirectNavigationTargetPolicy
import io.legado.app.ui.book.read.epub.EpubDirectPageAnimationPolicy
import io.legado.app.ui.book.read.epub.EpubDirectPrefetchPolicy
import io.legado.app.ui.book.read.epub.EpubDirectPrefetchScheduler
import io.legado.app.ui.book.read.epub.EpubDirectReadAloudPagePolicy
import io.legado.app.ui.book.read.epub.EpubDirectRequestCommitPolicy
import io.legado.app.ui.book.read.epub.EpubDirectRenderFailurePolicy
import io.legado.app.ui.book.read.epub.EpubDirectWebViewBudgetPolicy
import io.legado.app.ui.book.read.epub.EpubPageTurnResult
import io.legado.app.ui.book.read.epub.EpubPerformanceBudget
import io.legado.app.ui.book.read.epub.EpubPerformanceMode
import io.legado.app.ui.book.read.epub.EpubLayoutReadyActionGate
import io.legado.app.ui.book.read.epub.EpubTocNavigationPolicy
import io.legado.app.ui.book.searchContent.SearchContentActivity
import io.legado.app.ui.book.searchContent.SearchResult
import io.legado.app.model.SourceCallBack
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.toc.TocActivityResult
import io.legado.app.ui.book.toc.rule.TxtTocRuleDialog
import io.legado.app.ui.browser.WebViewActivity
import io.legado.app.ui.config.BubbleManageActivity
import io.legado.app.ui.config.ShareNoteTemplateManageActivity
import io.legado.app.ui.dict.DictDialog
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.replace.ReplaceRuleActivity
import io.legado.app.ui.replace.edit.ReplaceEditActivity
import io.legado.app.ui.widget.ModernActionPopup
import io.legado.app.ui.widget.PopupAction
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.ui.widget.dialog.BottomWebViewDialog
import io.legado.app.ui.widget.dialog.CommentWebViewSession
import io.legado.app.utils.ACache
import io.legado.app.utils.BookIntroUtils
import io.legado.app.utils.Debounce
import io.legado.app.utils.LogUtils
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.buildMainHandler
import io.legado.app.utils.dpToPx
import io.legado.app.utils.dismissDialogFragment
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import io.legado.app.utils.hexString
import io.legado.app.utils.iconItemOnLongClick
import io.legado.app.utils.statusBarHeight
import io.legado.app.utils.invisible
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isTrue
import io.legado.app.utils.launch
import io.legado.app.utils.navigationBarGravity
import io.legado.app.utils.observeEvent
import io.legado.app.utils.openUrl
import io.legado.app.utils.postEvent
import io.legado.app.utils.setLightStatusBar
import io.legado.app.utils.share
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.startActivity
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.spToPx
import io.legado.app.utils.sysScreenOffTime
import io.legado.app.utils.throttle
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import com.script.rhino.runScriptWithContext
import io.legado.app.model.analyzeRule.AnalyzeUrl.Companion.paramPattern
import io.legado.app.ui.login.SourceLoginJsExtensions
import java.text.DateFormat
import java.util.Date
import java.io.File
import java.util.regex.Matcher

/**
 * 阅读界面
 */
class ReadBookActivity : BaseReadBookActivity(),
    View.OnTouchListener,
    ReadView.CallBack,
    TextActionMenu.CallBack,
    ContentTextView.CallBack,
    MenuItem.OnMenuItemClickListener,
    ReadMenu.CallBack,
    SearchMenu.CallBack,
    ReadAloudDialog.CallBack,
    ReadAloudPlayerPanel.CallBack,
    ChangeBookSourceDialog.CallBack,
    ChangeChapterSourceDialog.CallBack,
    ReadBook.CallBack,
    AutoReadDialog.CallBack,
    TxtTocRuleDialog.CallBack,
    ColorPickerDialogListener,
    LayoutProgressListener,
    ReaderTemplateDialog.PreviewHost {

    private var pendingReadAloudPlayerOpen = false
    private var pendingReadAloudPanelIntentOpen = false
    private val shelfExitRequestGate = ShelfExitRequestGate()
    private var requestedReaderTemplate: EpubReaderTemplate? = null

    private val tocActivity =
        registerForActivityResult(TocActivityResult()) {
            it?.let {
                val keepReadAloudPanel = BaseReadAloudService.isRun &&
                        binding.readAloudPlayerPanel.isExpanded()
                val chapterIndex = it[0] as Int
                val chapterPos = it[1] as Int
                if (isEpubCoreMode()) {
                    if (ReadBook.book?.isEpub == true) {
                        val chapterUrl = (it.getOrNull(5) as? String).orEmpty()
                        val selection = EpubTocNavigationPolicy.selection(
                            chapterIndex = chapterIndex,
                            chapterUrl = chapterUrl,
                            startFragmentId = it.getOrNull(6) as? String
                        )
                        openEpubTocChapter(selection.chapterIndex, selection.fragmentId)
                    } else {
                        ReadBook.saveCurrentBookProgress()
                        openDirectTextChapter(chapterIndex, chapterPos)
                    }
                } else {
                    viewModel.openChapter(chapterIndex, chapterPos)
                }
                if (keepReadAloudPanel) {
                    binding.readAloudPlayerPanel.post {
                        binding.readAloudPlayerPanel.open(force = false)
                        binding.readAloudPlayerPanel.refresh()
                    }
                }
            }
        }
    private val sourceEditActivity =
        registerForActivityResult(StartActivityContract(BookSourceEditActivity::class.java)) {
            if (it.resultCode == RESULT_OK) {
                viewModel.upBookSource {
                    upMenuView()
                    if (isEpubCoreMode() && ReadBook.book?.isEpub == false) {
                        ReadBook.reloadCurrentContent("source-edited")
                    }
                }
            }
        }
    private val replaceActivity =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                viewModel.replaceRuleChanged()
            }
        }
    private val searchContentActivity =
        registerForActivityResult(StartActivityContract(SearchContentActivity::class.java)) {
            val data = it.data ?: return@registerForActivityResult
            val key = data.getLongExtra("key", System.currentTimeMillis())
            val index = data.getIntExtra("index", 0)
            val searchResult = IntentData.get<SearchResult>("searchResult$key")
            val searchResultList = IntentData.get<List<SearchResult>>("searchResultList$key")
            if (searchResult != null && searchResultList != null) {
                viewModel.searchContentQuery = searchResult.query
                binding.searchMenu.upSearchResultList(searchResultList)
                isShowingSearchResult = true
                viewModel.searchResultIndex = index
                binding.searchMenu.updateSearchResultIndex(index)
                binding.searchMenu.selectedSearchResult?.let { currentResult ->
                    ReadBook.saveCurrentBookProgress() //退出全文搜索恢复此时进度
                    skipToSearch(currentResult)
                    showActionMenu()
                }
            }
        }
    private val bookInfoActivity =
        registerForActivityResult(BookInfoStartActivityContract()) {
            if (it.resultCode == RESULT_OK) {
                setResult(RESULT_DELETED)
                super.finish()
            } else {
                ReadBook.loadOrUpContent()
            }
        }
    private var lastTextMenuAnchor: ReadAiFloatingPanel.Anchor? = null
    private val selectImageDir = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            ACache.get().put(AppConst.imagePathKey, uri.toString())
            viewModel.saveImage(it.value, uri)
        }
    }
    private var menu: Menu? = null
    private var modernMenuPopup: ModernActionPopup.Handle? = null
    private var backupJob: Job? = null
    private var tts: TTS? = null
    private var commentWebViewSession: CommentWebViewSession? = null
    private var shareNotePreviewOverlay: ShareNotePreviewOverlay? = null
    @Volatile
    private var commentBrowserOpening = false
    @Volatile
    private var commentBrowserShowing = false
    val textActionMenu: TextActionMenu by lazy {
        TextActionMenu(this, this)
    }
    private val popupAction: PopupAction by lazy {
        PopupAction(this)
    }
    override val isInitFinish: Boolean get() = viewModel.isInitFinish
    override val isScroll: Boolean get() = binding.readView.isScroll
    private val isAutoPage
        get() = binding.readView.isAutoPage ||
            epubDirectAutoPager.isRunning
    override var isShowingSearchResult = false
    override var isSelectingSearchResult = false
        set(value) {
            field = value && isShowingSearchResult
        }
    private val timeBatteryReceiver = TimeBatteryReceiver()
    private var screenTimeOut: Long = 0
    private var loadStates: Boolean = false
    override val pageFactory get() = binding.readView.pageFactory
    override val pageDelegate get() = binding.readView.pageDelegate
    override val headerHeight: Int get() = binding.readView.curPage.headerHeight
    override val imgBgPaddingStart: Int get() = binding.readView.curPage.imgBgPaddingStart
    private val nextPageDebounce by lazy { Debounce { keyPage(PageDirection.NEXT) } }
    private val prevPageDebounce by lazy { Debounce { keyPage(PageDirection.PREV) } }
    private var bookChanged = false
    private var pageChanged = false
    private val handler by lazy { buildMainHandler() }
    private val screenOffRunnable by lazy { Runnable { keepScreenOn(false) } }
    private val executor = ReadBook.executor
    private val upSeekBarThrottle = throttle(200) {
        runOnUiThread {
            upSeekBarProgress()
            binding.readMenu.upSeekBar()
        }
    }

    //恢复跳转前进度对话框的交互结果
    private var confirmRestoreProcess: Boolean? = null
    private val networkChangedListener by lazy {
        NetworkChangedListener(this)
    }
    private var justInitData: Boolean = false
    private var syncDialog: AlertDialog? = null
    private data class EpubCoreNavigationRequest(
        val chapterIndex: Int,
        val resetPageOffset: Boolean,
        val boundaryTransition: Boolean = false,
        val targetFragmentId: String? = null
    )

    private data class EpubDirectLoadResult(
        val session: EpubDirectSession,
        val chapter: io.legado.app.model.localBook.epubcore.direct.EpubDirectChapter,
        val config: EpubCoreLayoutConfig,
        val ownsSession: Boolean,
        val textPosition: Int? = null
    )

    private data class SelectedParagraphForImage(
        val contentIndex: Int,
        val text: String
    )

    private enum class EpubCoreNavigationMode {
        Sequential,
        ExplicitReplace
    }

    private enum class EpubCoreScheduleMode(
        val key: String,
        val titleRes: Int
    ) {
        Smart(EpubPerformanceMode.Smart.key, R.string.epub_core_schedule_mode_smart),
        PowerSave(EpubPerformanceMode.PowerSave.key, R.string.epub_core_schedule_mode_power_save),
        Balanced(EpubPerformanceMode.Balanced.key, R.string.epub_core_schedule_mode_balanced),
        Smooth(EpubPerformanceMode.Smooth.key, R.string.epub_core_schedule_mode_smooth),
        Extreme(EpubPerformanceMode.Extreme.key, R.string.epub_core_schedule_mode_extreme);

        companion object {
            fun fromKey(key: String?): EpubCoreScheduleMode {
                val normalized = EpubPerformanceMode.fromKey(key).key
                return values().firstOrNull { it.key == normalized } ?: Smart
            }
        }
    }

    private var epubCoreActive = false
    private var epubAnnotationBackCheckPending = false
    private var epubCorePageCount = 0
    private var epubCoreLoading = false
    private var epubCoreLoadingChapterIndex: Int? = null
    private var epubCoreRequestSeq = 0L
    private var epubCoreLoadJob: Job? = null
    private var epubCoreLoadTimeoutJob: Job? = null
    private val readerTemplateActiveClock = EpubTemplateActiveClock(SystemClock::uptimeMillis).also { it.pause() }
    private data class DirectPrefetchOwner(
        val session: EpubDirectSession,
        val config: EpubCoreLayoutConfig
    )
    private val epubCorePrefetch by lazy {
        EpubDirectPrefetchScheduler<EpubDirectChapter>(lifecycleScope, SystemClock::uptimeMillis)
    }
    private val epubCoreLayoutReadyActionGate = EpubLayoutReadyActionGate()
    private var epubCoreSuppressProgressSync = false
    private var epubCoreBoundaryTransition = false
    private var epubCoreForegroundTarget: EpubCoreNavigationRequest? = null
    private var epubCoreCommittedChapterIndex: Int? = null
    private var pendingDirectReadAloudProgress: ReadAloudProgressState? = null
    private var directReadAloudTargetChapterIndex: Int? = null
    private var epubDirectSession: EpubDirectSession? = null
    private var directSourceImageJob: Job? = null
    private var epubDirectOnFinish: (() -> Unit)? = null
    private var epubDirectCallbackRequestSeq = 0L
    private var epubDirectLinkRequestSeq = 0L
    private var epubReaderBatteryLevel = 100
    private var epubReaderChromeContentRevision = 0L
    private val epubReaderFontPreparer by lazy { EpubReaderFontPreparer(applicationContext) }
    private val epubDirectAutoPager by lazy {
        EpubDirectAutoPager(
            postDelayed = { runnable, delay -> binding.epubReadView.postDelayed(runnable, delay) },
            removeCallbacks = binding.epubReadView::removeCallbacks,
            intervalMillis = { ReadBookConfig.autoReadSpeed.coerceAtLeast(1) * 1_000L },
            requestNextPage = { requestNextEpubPage(automatic = true) },
            onStopped = ::finishAutoPageUi
        )
    }
    private var epubHostOverlaySettleRunnable: Runnable? = null
    private var libraryCloudSession: LibraryCloudSession? = null
    private var libraryCloudState: LibraryCloudState = LibraryCloudState.DISABLED
    private var lastReaderNightMode = AppConfig.isNightTheme
    private var epubLoadingWindow: EpubLoadingWindowOverlay? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        binding.cursorLeft.setColorFilter(accentColor)
        binding.cursorRight.setColorFilter(accentColor)
        binding.cursorLeft.setOnTouchListener(this)
        binding.cursorRight.setOnTouchListener(this)
        binding.readAiPanel.attach(this)
        binding.readAiSummaryPanel.attach(this)
        binding.readAloudPlayerPanel.attach(this, this)
        ReadAloudAppCapsuleHost.updateReadBookPanelActive(binding.readAloudPlayerPanel.isFullPanelActive())
        binding.readAloudPlayerPanel.post {
            consumeGlobalReadAloudPanelOpen()
        }
        binding.epubReadView.setListener(object : EpubReadView.Listener {
            override fun onLoadingPresentationChanged() {
                upSystemUiVisibility()
            }

            override fun onCenterTap(x: Float, y: Float) {
                if (binding.epubReadView.hasDirectContent) showActionMenu() else showMenuBar()
            }

            override fun onTapAction(action: Int, x: Float, y: Float) {
                handleEpubTapAction(action, x, y)
            }

            override fun onWordLookup(word: String) {
                showDictDialog(word)
            }

            override fun isWordLookupActive(): Boolean =
                this@ReadBookActivity.isWordLookupActive()

            override fun onPreviousPage() {
                previousEpubPage()
            }

            override fun onNextPage() {
                nextEpubPage()
            }

            override fun onPageChanged(pageIndex: Int, pageCount: Int) {
                binding.epubReadView.currentPage()?.let {
                    epubDirectAutoPager.onPageCommitted(
                        EpubDirectAutoPager.Position(it.chapterIndex, it.pageIndex)
                    )
                }
                syncEpubCoreProgress(pageIndex, pageCount)
                if (!epubCoreLoading) {
                    binding.epubReadView.currentPage()?.chapterIndex
                        ?.let { chapterIndex ->
                            consumePendingDirectReadAloudProgress(chapterIndex)
                            // Refill after memory reclamation or a transient preload failure.
                            // The scheduler preserves work already running for this chapter.
                            scheduleDirectEpubPrefetch(chapterIndex, epubCoreRequestSeq)
                        }
                }
                if (epubCoreBoundaryTransition &&
                    epubCoreLoadingChapterIndex == null &&
                    epubCoreForegroundTarget == null &&
                    epubCoreLoading.not()
                ) {
                    epubCoreBoundaryTransition = false
                    val currentChapterIndex = binding.epubReadView.currentPage()?.chapterIndex ?: return
                    commitEpubCoreDisplayedChapter(currentChapterIndex, pageIndex, epubCoreRequestSeq)
                }
            }

            override fun onPageBoundary(direction: Int) {
                requestEpubCoreChapterByDirection(
                    direction = direction,
                    intent = EpubChapterNavigationPolicy.Intent.PageTurnBoundary
                )
            }

            override fun onDirectChapterReady(position: EpubDirectPosition) {
                epubDirectAutoPager.onPageCommitted(
                    EpubDirectAutoPager.Position(position.chapterIndex, position.pageIndex)
                )
                val expectedChapterIndex = epubCoreLoadingChapterIndex
                if (isEpubCoreMode() &&
                    epubDirectCallbackRequestSeq == 0L &&
                    !epubCoreLoading &&
                    position.chapterIndex == epubCoreCommittedChapterIndex
                ) {
                    syncEpubCoreProgress(position.pageIndex, position.pageCount)
                    consumePendingDirectReadAloudProgress(position.chapterIndex)
                    scheduleDirectEpubPrefetch(position.chapterIndex, epubCoreRequestSeq)
                    return
                }
                if (!isEpubCoreMode() ||
                    epubDirectCallbackRequestSeq == 0L ||
                    epubDirectCallbackRequestSeq != epubCoreRequestSeq ||
                    position.chapterIndex != expectedChapterIndex
                ) {
                    AppLog.putDebug(
                        "EPUB direct ready ignored: actual=${position.chapterIndex}, " +
                            "expected=$expectedChapterIndex, callback=$epubDirectCallbackRequestSeq, " +
                            "request=$epubCoreRequestSeq"
                    )
                    return
                }
                epubCoreSuppressProgressSync = true
                commitEpubCoreDisplayedChapter(
                    chapterIndex = position.chapterIndex,
                    chapterPageIndex = position.pageIndex,
                    requestSeq = epubCoreRequestSeq,
                    schedulePrefetch = false
                )
                contentLoadFinish()
                epubDirectOnFinish?.invoke()
                epubDirectOnFinish = null
                epubDirectCallbackRequestSeq = 0L
                consumePendingDirectReadAloudProgress(position.chapterIndex)
                scheduleDirectEpubPrefetch(position.chapterIndex, epubCoreRequestSeq)
            }

            override fun onDirectRenderError(message: String, throwable: Throwable?) {
                epubDirectAutoPager.stop()
                finishDirectEpubFailure(
                    message = message,
                    throwable = throwable,
                    stage = "web-activation"
                )
            }

            override fun onDirectSourceImageAction(request: TextReaderImageActionRequest) {
                executeDirectSourceImageAction(request)
            }

            override fun onDirectLinkClicked(url: String) {
                val currentIndex = binding.epubReadView.currentPage()?.chapterIndex ?: ReadBook.durChapterIndex
                val session = epubDirectSession ?: return
                val bookUrl = ReadBook.book?.bookUrl ?: return
                val linkRequestSeq = ++epubDirectLinkRequestSeq
                lifecycleScope.launch(IO) {
                    val target = runCatching { session.resolveLink(url, currentIndex) }
                        .onFailure { AppLog.putDebug("EPUB direct link resolution failed: $url", it) }
                        .getOrNull()
                    withContext(Main.immediate) {
                        if (linkRequestSeq != epubDirectLinkRequestSeq ||
                            epubDirectSession !== session ||
                            ReadBook.book?.bookUrl != bookUrl
                        ) {
                            return@withContext
                        }
                        if (target != null) {
                            if (target.chapterIndex == currentIndex && target.fragmentId != null) {
                                val scheduled = binding.epubReadView.navigateDirectToFragment(target.fragmentId) { success ->
                                    if (!success &&
                                        binding.epubReadView.currentPage()?.chapterIndex == currentIndex
                                    ) {
                                        loadEpubCoreContent(
                                            targetChapterIndex = target.chapterIndex,
                                            resetPageOffset = true,
                                            targetFragmentId = target.fragmentId
                                        )
                                    }
                                }
                                if (scheduled) return@withContext
                            }
                            loadEpubCoreContent(
                                targetChapterIndex = target.chapterIndex,
                                resetPageOffset = true,
                                targetFragmentId = target.fragmentId
                            )
                        } else if ((url.startsWith("http://", true) || url.startsWith("https://", true)) &&
                            !EpubDirectSession.isLocalUrl(url)
                        ) {
                            openUrl(url)
                        }
                    }
                }
            }

            override fun onDirectFootnoteClicked(url: String) {
                val book = ReadBook.book ?: return
                val href = runCatching {
                    val uri = android.net.Uri.parse(url)
                    if (uri.scheme.equals(EpubDirectSession.SCHEME, true) &&
                        EpubDirectSession.isLocalHost(uri.host)
                    ) {
                        val path = android.net.Uri.decode(uri.encodedPath.orEmpty().removePrefix("/"))
                        val fragment = uri.encodedFragment?.takeIf { it.isNotBlank() } ?: return@runCatching url
                        "$path#$fragment"
                    } else {
                        url
                    }
                }.getOrDefault(url)
                lifecycleScope.launch(IO) {
                    val note = runCatching { EpubFile.getFootnote(book, href) }.getOrNull()
                    withContext(Main.immediate) {
                        if (note == null) {
                            toastOnUi(R.string.epub_footnote_load_failed)
                        } else {
                            showDialogFragment(TextDialog(note.title, note.html, TextDialog.Mode.HTML))
                        }
                    }
                }
            }

            override fun onTextSelected(startX: Float, topY: Float, endX: Float, bottomY: Float, startBottomY: Float, endBottomY: Float) {
                showEpubTextActionMenu(startX, topY, endX, bottomY, startBottomY, endBottomY)
            }

            override fun onSelectionInteractionStarted() {
                epubDirectAutoPager.pause()
                cancelEpubCoreLoadTimeout()
                hideEpubSelectionUi()
            }

            override fun onSelectionCleared() {
                hideEpubSelectionUi()
                resumeEpubDirectAutoPageIfUnblocked()
                directReadAloudTargetChapterIndex?.let { chapterIndex ->
                    requestDirectReadAloudChapter(
                        chapterIndex = chapterIndex,
                        progress = pendingDirectReadAloudProgress
                            ?.takeIf { it.chapterIndex == chapterIndex }
                    )
                } ?: pendingDirectReadAloudProgress
                    ?.takeIf { BaseReadAloudService.isPlay() }
                    ?.let(::applyDirectReadAloudProgress)
                val loadingChapter = epubCoreLoadingChapterIndex
                if (epubCoreLoading && loadingChapter != null && epubDirectCallbackRequestSeq != 0L) {
                    scheduleEpubCoreLoadTimeout(
                        requestSeq = epubDirectCallbackRequestSeq,
                        chapterIndex = loadingChapter,
                        stage = "selection-resume"
                    )
                }
            }
        })
        window.setBackgroundDrawable(null)
        upScreenTimeOut()
        ReadBook.register(this)
        Backup.autoBack(this)
        onBackPressedDispatcher.addCallback(this) {
            if (epubAnnotationBackCheckPending) return@addCallback
            epubAnnotationBackCheckPending = true
            binding.epubReadView.dismissDirectAnnotation { dismissed ->
                epubAnnotationBackCheckPending = false
                if (!dismissed) handleBackPressedAfterEpubAnnotation()
            }
        }
    }

    private fun handleBackPressedAfterEpubAnnotation() {
        if (binding.readAloudPlayerPanel.isExpanded()) {
            binding.readAloudPlayerPanel.close()
            return
        }
        if (binding.readAiPanel.isVisible) {
            if (!binding.readAiPanel.exitFullscreenIfNeeded()) {
                binding.readAiPanel.close()
            }
            return
        }
        if (isShowingSearchResult) {
            exitSearchMenu()
            restoreLastBookProcess()
            return
        }
        //拦截返回供恢复阅读进度
        if (ReadBook.lastBookProgress != null && confirmRestoreProcess != false) {
            restoreLastBookProcess()
            return
        }
        if (isAutoPage) {
            autoPageStop()
            return
        }
        if (getPrefBoolean("disableReturnKey") && !menuLayoutIsVisible) return
        finish()
    }

    private fun handleEpubTapAction(action: Int, x: Float, y: Float) {
        when (action) {
            -1 -> Unit
            0 -> showActionMenu()
            1 -> nextEpubPage()
            2 -> previousEpubPage()
            3 -> requestEpubCoreChapterByDirection(
                1,
                EpubChapterNavigationPolicy.Intent.ExplicitChapterJump
            )
            4 -> requestEpubCoreChapterByDirection(
                -1,
                EpubChapterNavigationPolicy.Intent.ExplicitChapterJump
            )
            5 -> ReadAloud.prevParagraph(this)
            6 -> ReadAloud.nextParagraph(this)
            7 -> addBookmark()
            8, 9, 11 -> toastOnUi(R.string.native_reader_feature_only)
            10 -> openChapterList()
            12 -> ReadBook.syncProgress(
                { progress -> sureNewProgress(progress) },
                { toastOnUi(R.string.upload_book_success) },
                { toastOnUi(R.string.sync_book_progress_success) }
            )
            13 -> {
                if (BaseReadAloudService.isPlay()) {
                    ReadAloud.pause(this)
                } else {
                    ReadAloud.resume(this)
                }
            }
            else -> showActionMenu()
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        viewModel.initReadBookConfig(intent)
        binding.root.post {
            AppLog.put("read-init: schedule, source=postCreate")
            viewModel.initData(intent)
        }
        justInitData = true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val openReadAloudPanel = requestOpenReadAloudPanelFromIntent(intent)
        if (openReadAloudPanel &&
            BaseReadAloudService.isRun &&
            intent.getStringExtra("bookUrl") == ReadBook.book?.bookUrl
        ) {
            openPendingReadAloudPanel()
            return
        }
        if (consumeGlobalReadAloudPanelOpen()) {
            return
        }
        viewModel.initData(intent) {
            if (openReadAloudPanel) {
                openPendingReadAloudPanel()
            }
            consumeGlobalReadAloudPanelOpen()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        upSystemUiVisibility()
        if (hasFocus) {
            binding.readMenu.upBrightnessState()
        } else if (!menuLayoutIsVisible) {
            ReadBook.cancelPreDownloadTask()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val readerNightMode = if (AppConfig.themeMode == "0") {
            newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        } else {
            AppConfig.isNightTheme
        }
        val readerNightModeChanged = readerNightMode != lastReaderNightMode
        lastReaderNightMode = readerNightMode
        upSystemUiVisibility()
        binding.readView.upStatusBar()
        if (epubCoreActive) {
            refreshEpubCoreAfterConfigurationChange()
        } else if (readerNightModeChanged) {
            binding.readView.refreshVisualStyle()
        }
    }

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        if (!isTopResumedActivity) {
            ReadBook.cancelPreDownloadTask()
        }
    }

    private var highlightRulesRevision: String? = null

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        val highlightBookUrl = ReadBook.book?.bookUrl
        lifecycleScope.launch {
            val revision = withContext(IO) { HighlightRules.store.revision() }
            val changed = highlightRulesRevision?.let { it != revision } == true
            highlightRulesRevision = revision
            if (changed && isInitFinish && highlightBookUrl == ReadBook.book?.bookUrl) {
                if (epubCoreActive) {
                    cancelEpubCorePrefetch()
                    loadEpubCoreContent(resetPageOffset = false)
                } else ReadBook.relayoutCurrentContent("highlight-rules")
            } else if (isInitFinish && epubCoreActive && !epubCoreLoading &&
                highlightBookUrl == ReadBook.book?.bookUrl
            ) {
                binding.epubReadView.currentPage()?.chapterIndex?.let {
                    scheduleDirectEpubPrefetch(it, epubCoreRequestSeq)
                }
            }
        }
        readerTemplateActiveClock.resume()
        binding.epubReadView.onDirectHostResume()
        ReadBook.readStartTime = System.currentTimeMillis()
        if (bookChanged) {
            bookChanged = false
            ReadBook.callBack = this
            viewModel.initData(intent)
            justInitData = true
        } else {
            //web端阅读时，app处于阅读界面，本地记录会覆盖web保存的进度，在此处恢复
            ReadBook.webBookProgress?.let {
                ReadBook.setProgress(it)
                ReadBook.webBookProgress = null
            }
        }
        upSystemUiVisibility()
        registerReceiver(timeBatteryReceiver, timeBatteryReceiver.filter)
        binding.readView.upTime()
        updateEpubReaderChromeData()
        screenOffTimerStart()
        // 网络监听，当从无网切换到网络环境时同步进度（注意注册的同时就会收到监听，因此界面激活时无需重复执行同步操作）
        networkChangedListener.register()
        networkChangedListener.onNetworkChanged = {
            // 当网络是可用状态且无需初始化时同步进度（初始化中已有同步进度逻辑）
            if (AppConfig.syncBookProgressPlus && NetworkUtils.isAvailable() && !justInitData && ReadBook.inBookshelf) {
                ReadBook.syncProgress({ progress -> sureNewProgress(progress) })
            }
        }
        binding.readAloudPlayerPanel.setForegroundActive(true)
        if (pendingReadAloudPanelIntentOpen && BaseReadAloudService.isRun) {
            binding.readAloudPlayerPanel.post {
                openPendingReadAloudPanel()
            }
        }
        binding.readAloudPlayerPanel.post {
            consumeGlobalReadAloudPanelOpen()
        }
    }

    override fun onPause() {
        readerTemplateActiveClock.pause()
        super.onPause()
        binding.epubReadView.onDirectHostPause()
        binding.readAloudPlayerPanel.setForegroundActive(false)
        autoPageStop()
        backupJob?.cancel()
        ReadBook.upReadTime(forceWidgetUpdate = true)
        if (isFinishing) {
            ImageProvider.clear()
        } else {
            ImageProvider.trimMemory()
        }
        ReadBook.saveRead()
        ReadBook.cancelPreDownloadTask()
        unregisterReceiver(timeBatteryReceiver)
        upSystemUiVisibility()
        if (ReadBook.inBookshelf) {
            if (AppConfig.syncBookProgressPlus) {
                ReadBook.syncProgress()
            } else {
                ReadBook.uploadProgress()
            }
        }
        justInitData = false
        networkChangedListener.unRegister()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_read, menu)
        menu.iconItemOnLongClick(R.id.menu_change_source) {
            modernMenuPopup = ModernActionPopup.showFromMenu(
                it,
                R.menu.book_read_change_source,
                modernMenuPopup,
                onClick = ::onMenuItemClick
            )
        }
        menu.iconItemOnLongClick(R.id.menu_refresh) {
            modernMenuPopup = ModernActionPopup.showFromMenu(
                it,
                R.menu.book_read_refresh,
                modernMenuPopup,
                onClick = ::onMenuItemClick
            )
        }
        binding.readMenu.refreshMenuColorFilter()
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        this.menu = menu
        upMenu()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.findItem(R.id.menu_same_title_removed)?.isChecked =
            ReadBook.curTextChapter?.sameTitleRemoved == true
        return super.onMenuOpened(featureId, menu)
    }

    /**
     * 更新菜单
     */
    private fun upMenu() {
        val menu = menu ?: return
        val book = ReadBook.book ?: return
        val onLine = !book.isLocal
        for (i in 0 until menu.size) {
            val item = menu[i]
            when (item.groupId) {
                R.id.menu_group_on_line -> item.isVisible = onLine
                R.id.menu_group_local -> item.isVisible = !onLine
                R.id.menu_group_text -> item.isVisible = book.isLocalTxt
                R.id.menu_group_epub -> {
                    item.isVisible = book.isEpub
                    when (item.itemId) {
                        R.id.menu_del_ruby_tag -> {
                            item.isVisible = book.isEpub && !isEpubCoreMode()
                            item.isChecked = book.getDelTag(Book.rubyTag)
                        }
                        R.id.menu_del_h_tag -> {
                            item.isVisible = book.isEpub && !isEpubCoreMode()
                            item.isChecked = book.getDelTag(Book.hTag)
                        }
                        R.id.menu_epub_schedule_mode -> {
                            item.isVisible = isEpubCoreMode()
                            item.title =
                                "${getString(R.string.epub_core_schedule_mode)} (${getString(currentEpubCoreSchedule().titleRes)})"
                        }
                    }
                }
                else -> when (item.itemId) {
                    R.id.menu_edit_content,
                    R.id.menu_same_title_removed,
                    R.id.menu_image_style -> {
                        item.isVisible = !isEpubCoreMode()
                    }
                    R.id.menu_enable_replace -> {
                        item.isVisible = supportsReplaceRules()
                        item.isChecked = book.getUseReplaceRule()
                    }
                    R.id.menu_effective_replaces -> item.isVisible = !isEpubCoreMode()
                    R.id.menu_re_segment -> {
                        item.isVisible = !isEpubCoreMode()
                        item.isChecked = book.getReSegment()
                    }
//                    R.id.menu_enable_review -> {
//                        item.isVisible = BuildConfig.DEBUG
//                        item.isChecked = AppConfig.enableReview
//                    }

                    R.id.menu_reverse_content -> item.isVisible = onLine && !isEpubCoreMode()
                    R.id.menu_paragraph_rule_manage -> item.isVisible = !book.isEpub && !isEpubCoreMode()
                }
            }
        }
        lifecycleScope.launch {
            val show = ReadBook.inBookshelf && withContext(IO) {
                AppWebDav.isOk
            }
            menu.findItem(R.id.menu_get_progress)?.isVisible = show
            menu.findItem(R.id.menu_cover_progress)?.isVisible = show
        }
    }

    /**
     * 菜单
     */
    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_change_source,
            R.id.menu_book_change_source -> {
                binding.readMenu.runMenuOut()
                ReadBook.book?.let {
                    showDialogFragment(ChangeBookSourceDialog(it.name, it.author))
                }
            }

            R.id.menu_chapter_change_source -> lifecycleScope.launch {
                val book = ReadBook.book ?: return@launch
                val chapter =
                    appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
                        ?: return@launch
                binding.readMenu.runMenuOut()
                showDialogFragment(
                    ChangeChapterSourceDialog(book.name, book.author, chapter.index, chapter.title)
                )
            }

            R.id.menu_refresh,
            R.id.menu_refresh_dur -> {
                if (ReadBook.bookSource == null) {
                    refreshContentWithoutSource()
                } else {
                    ReadBook.book?.let {
                        ReadBook.curTextChapter = null
                        binding.readView.upContent()
                        viewModel.refreshContentDur(it)
                    }
                }
            }

            R.id.menu_refresh_after -> {
                if (ReadBook.bookSource == null) {
                    refreshContentWithoutSource()
                } else {
                    ReadBook.book?.let {
                        ReadBook.clearTextChapter()
                        binding.readView.upContent()
                        viewModel.refreshContentAfter(it)
                    }
                }
            }

            R.id.menu_refresh_all -> {
                if (ReadBook.bookSource == null) {
                    refreshContentWithoutSource()
                } else {
                    ReadBook.book?.let {
                        refreshContentAll(it)
                    }
                }
            }

            R.id.menu_download -> showDownloadDialog()
            R.id.menu_add_bookmark -> addBookmark()
            R.id.menu_simulated_reading -> showSimulatedReading()
            R.id.menu_edit_content -> showDialogFragment(ContentEditDialog())
            R.id.menu_update_toc -> ReadBook.book?.let {
                if (it.isEpub) {
                    BookHelp.clearCache(it)
                    EpubFile.clear()
                }
                if (it.isMobi) {
                    MobiFile.clear()
                }
                loadChapterList(it)
            }

            R.id.menu_enable_replace -> changeReplaceRuleState()
            R.id.menu_re_segment -> ReadBook.book?.let {
                it.setReSegment(!it.getReSegment())
                item.isChecked = it.getReSegment()
                ReadBook.saveRead(fullUpdate = true)
                ReadBook.reloadCurrentContent("re-segment")
            }

//            R.id.menu_enable_review -> {
//                AppConfig.enableReview = !AppConfig.enableReview
//                item.isChecked = AppConfig.enableReview
//                ReadBook.loadContent(false)
//            }

            R.id.menu_del_ruby_tag -> ReadBook.book?.let {
                item.isChecked = !item.isChecked
                if (item.isChecked) {
                    it.addDelTag(Book.rubyTag)
                } else {
                    it.removeDelTag(Book.rubyTag)
                }
                ReadBook.saveRead(fullUpdate = true)
                ReadBook.reloadCurrentContent("delete-ruby-tag")
            }

            R.id.menu_del_h_tag -> ReadBook.book?.let {
                item.isChecked = !item.isChecked
                if (item.isChecked) {
                    it.addDelTag(Book.hTag)
                } else {
                    it.removeDelTag(Book.hTag)
                }
                ReadBook.saveRead(fullUpdate = true)
                ReadBook.reloadCurrentContent("delete-h-tag")
            }

            R.id.menu_epub_schedule_mode -> showEpubCoreScheduleModeDialog()
            R.id.menu_read_menu_edit -> startActivity<ReadMenuButtonManageActivity>()

            R.id.menu_page_anim -> showPageAnimConfig(::applyPageAnimationChange)

            R.id.menu_log -> showDialogFragment<AppLogDialog>()
            R.id.menu_toc_regex -> showDialogFragment(
                TxtTocRuleDialog(ReadBook.book?.tocUrl)
            )

            R.id.menu_reverse_content -> ReadBook.book?.let {
                viewModel.reverseContent(it)
            }

            R.id.menu_set_charset -> showCharsetConfig()
            R.id.menu_image_style -> {
                val imgStyles =
                    arrayListOf(
                        Book.imgStyleDefault, Book.imgStyleFull, Book.imgStyleText,
                        Book.imgStyleSingle
                    )
                selector(
                    R.string.image_style,
                    imgStyles
                ) { _, index ->
                    val imageStyle = imgStyles[index]
                    ReadBook.book?.setImageStyle(imageStyle)
                    if (imageStyle == Book.imgStyleSingle) {
                        ReadBook.book?.setPageAnim(0)  // 切换图片样式single后，自动切换为覆盖
                        binding.readView.upPageAnim()
                    }
                    ReadBook.saveRead(fullUpdate = true)
                    ReadBook.reloadCurrentContent("image-style")
                }
            }

            R.id.menu_get_progress -> ReadBook.book?.let {
                viewModel.syncBookProgress(it) { progress ->
                    sureSyncProgress(progress)
                }
            }

            R.id.menu_cover_progress -> ReadBook.book?.let {
                ReadBook.uploadProgress(true) { toastOnUi(R.string.upload_book_success) }
            }

            R.id.menu_same_title_removed -> {
                ReadBook.book?.let {
                    val contentProcessor = ContentProcessor.get(it)
                    val textChapter = ReadBook.curTextChapter
                    if (textChapter != null
                        && !textChapter.sameTitleRemoved
                        && !BookHelp.getChapterCacheFileNames(it, textChapter.chapter, "nr")
                            .any(contentProcessor.removeSameTitleCache::contains)
                    ) {
                        toastOnUi("未找到可移除的重复标题")
                    }
                }
                viewModel.reverseRemoveSameTitle()
            }

            R.id.menu_effective_replaces -> showEffectiveReplaces()

            R.id.menu_highlight_rule_manage -> showHighlightRuleManage()
            R.id.menu_paragraph_rule_manage -> ReadBook.book?.let {
                startActivity<ParagraphRuleManageActivity> {
                    putExtra("bookUrl", it.bookUrl)
                }
            }

            R.id.menu_help -> showHelp()
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun refreshContentAll(book: Book) {
        viewModel.refreshContentAll(book)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        return onCompatOptionsItemSelected(item)
    }

    /**
     * 按键拦截,显示菜单
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action
        val isDown = action == 0

        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (isDown && !binding.readMenu.canShowMenu) {
                binding.readMenu.runMenuIn()
                return true
            }
            if (!isDown && !binding.readMenu.canShowMenu) {
                binding.readMenu.canShowMenu = true
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * 鼠标滚轮事件
     */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (0 != (event.source and InputDevice.SOURCE_CLASS_POINTER)) {
            if (event.action == MotionEvent.ACTION_SCROLL) {
                val axisValue = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                LogUtils.d("onGenericMotionEvent", "axisValue = $axisValue")
                // 获得垂直坐标上的滚动方向
                if (axisValue < 0.0f) { // 滚轮向下滚
                    mouseWheelPage(PageDirection.NEXT, axisValue)
                } else { // 滚轮向上滚
                    mouseWheelPage(PageDirection.PREV, axisValue)
                }
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    /**
     * 按键事件
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (menuLayoutIsVisible) {
            return super.onKeyDown(keyCode, event)
        }
        val longPress = event.repeatCount > 0
        when {
            isPrevKey(keyCode) -> {
                handleKeyPage(PageDirection.PREV, longPress)
                return true
            }

            isNextKey(keyCode) -> {
                handleKeyPage(PageDirection.NEXT, longPress)
                return true
            }
        }
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> if (volumeKeyPage(PageDirection.PREV, longPress)) {
                return true
            }

            KeyEvent.KEYCODE_VOLUME_DOWN -> if (volumeKeyPage(PageDirection.NEXT, longPress)) {
                return true
            }

            KeyEvent.KEYCODE_PAGE_UP -> {
                handleKeyPage(PageDirection.PREV, longPress)
                return true
            }

            KeyEvent.KEYCODE_PAGE_DOWN -> {
                handleKeyPage(PageDirection.NEXT, longPress)
                return true
            }

            KeyEvent.KEYCODE_SPACE -> {
                handleKeyPage(PageDirection.NEXT, longPress)
                return true
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    /**
     * 松开按键事件
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (volumeKeyPage(PageDirection.NONE, false)) {
                    return true
                }
            }

        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * view触摸,文字选择
     */
    private var activeSelectionCursor: View? = null
    private var selectionPointerOffsetX = 0f
    private var selectionPointerOffsetY = 0f
    private val selectionScreenLocation = IntArray(2)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean = binding.run {
        // EPUB uses WebView's own selection handles. These cursors belong to the native reader.
        if (epubCoreActive || !binding.readView.isTextSelected) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeSelectionCursor = v
                v.getLocationOnScreen(selectionScreenLocation)
                selectionPointerOffsetX = selectionScreenLocation[0] + (if (v.id == R.id.cursor_left) v.width else 0) - event.rawX
                selectionPointerOffsetY = selectionScreenLocation[1] - event.rawY - 1f
                textActionMenu.dismiss()
                readView.beginSelectionHandleDrag(v.id == R.id.cursor_left)
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeSelectionCursor !== v) return@run true
                val x = event.rawX + selectionPointerOffsetX
                val y = event.rawY + selectionPointerOffsetY
                readView.getLocationOnScreen(selectionScreenLocation)
                readView.moveSelectionHandle(x - selectionScreenLocation[0], y - selectionScreenLocation[1])
            }

            MotionEvent.ACTION_UP -> {
                activeSelectionCursor = null
                readView.endSelectionHandleDrag()
                showTextActionMenu()
            }

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                activeSelectionCursor = null
                readView.endSelectionHandleDrag()
            }
        }
        return true
    }

    /**
     * 更新文字选择开始位置
     */
    override fun upSelectedStart(x: Float, y: Float, top: Float) = binding.run {
        if (!x.isFinite() || !y.isFinite() || !top.isFinite()) {
            cursorLeft.alpha = 0f
            if (activeSelectionCursor !== cursorLeft) cursorLeft.invisible()
            return@run
        }
        cursorLeft.alpha = 1f
        cursorLeft.x = x - cursorLeft.width
        cursorLeft.y = y
        cursorLeft.visible(true)
        textMenuPosition.x = x
        textMenuPosition.y = top
    }

    /**
     * 更新文字选择结束位置
     */
    override fun upSelectedEnd(x: Float, y: Float) = binding.run {
        if (!x.isFinite() || !y.isFinite()) {
            cursorRight.alpha = 0f
            if (activeSelectionCursor !== cursorRight) cursorRight.invisible()
            return@run
        }
        cursorRight.alpha = 1f
        cursorRight.x = x
        cursorRight.y = y
        cursorRight.visible(true)
    }

    /**
     * 取消文字选择
     */
    override fun onCancelSelect() = binding.run {
        activeSelectionCursor = null
        if (epubCoreActive) {
            clearEpubSelectionUi()
        } else {
            readView.isTextSelected = false
            readView.endSelectionHandleDrag()
            cursorLeft.invisible()
            cursorRight.invisible()
            textActionMenu.dismiss()
        }
    }

    private fun hideEpubSelectionUi() = binding.run {
        cursorLeft.invisible()
        cursorRight.invisible()
        textActionMenu.dismiss()
    }

    private fun clearEpubSelectionUi() = binding.run {
        textActionMenu.dismiss()
        cursorLeft.invisible()
        cursorRight.invisible()
        epubReadView.clearSelection(notify = false)
    }

    override fun onLongScreenshotTouchEvent(event: MotionEvent): Boolean {
        return binding.readView.onTouchEvent(event)
    }

    /**
     * 显示文本操作菜单
     */
    override fun showTextActionMenu() {
        val navigationBarHeight =
            if (!ReadBookConfig.hideNavigationBar && navigationBarGravity == Gravity.BOTTOM)
                binding.navigationBar.height else 0
        val startX = binding.textMenuPosition.x.toInt()
        val topY = binding.textMenuPosition.y.toInt()
        val endX = binding.cursorRight.x.toInt()
        val startBottomY = binding.cursorLeft.y.toInt() + binding.cursorLeft.height
        val endBottomY = binding.cursorRight.y.toInt() + binding.cursorRight.height
        val centerX = ((startX + endX) / 2f).toInt()
        val bottomY = maxOf(startBottomY, endBottomY)
        lastTextMenuAnchor = ReadAiFloatingPanel.Anchor(
            centerX = centerX,
            topY = topY,
            bottomY = bottomY
        )
        textActionMenu.show(
            binding.root,
            binding.root.height + navigationBarHeight,
            startX,
            topY,
            startBottomY,
            endX,
            endBottomY,
            navigationBarHeight
        )
    }

    private fun showEpubTextActionMenu(
        startX: Float,
        topY: Float,
        endX: Float,
        bottomY: Float,
        startBottomY: Float = bottomY,
        endBottomY: Float = bottomY
    ) = binding.run {
        val navigationBarHeight =
            if (!ReadBookConfig.hideNavigationBar && navigationBarGravity == Gravity.BOTTOM)
                binding.navigationBar.height else 0
        val start = startX.toInt()
        val top = topY.toInt()
        val end = endX.toInt()
        val bottom = endBottomY.toInt()
        if (epubReadView.isDirectMode) {
            cursorLeft.invisible()
            cursorRight.invisible()
        } else {
            cursorLeft.x = startX - cursorLeft.width
            cursorLeft.y = startBottomY
            cursorRight.x = endX
            cursorRight.y = endBottomY
            cursorLeft.visible(true)
            cursorRight.visible(true)
        }
        textMenuPosition.x = (startX + endX) / 2f
        textMenuPosition.y = topY
        lastTextMenuAnchor = ReadAiFloatingPanel.Anchor(
            centerX = ((startX + endX) / 2f).toInt(),
            topY = top,
            bottomY = bottom
        )
        textActionMenu.show(
            root,
            root.height + navigationBarHeight,
            start,
            top,
            endBottomY.toInt(),
            end,
            endBottomY.toInt(),
            navigationBarHeight
        )
    }

    /**
     * 当前选择的文本
     */
    override val selectedText: String
        get() = if (epubCoreActive) {
            binding.epubReadView.getSelectedText()
        } else {
            binding.readView.getSelectText()
        }

    /**
     * 文本选择菜单操作
     */
    override fun onMenuItemSelected(itemId: Int): Boolean {
        when (itemId) {
            R.id.menu_web_search -> {
                showDialogFragment(SelectionWebSearchDialog(selectedText))
                return true
            }

            R.id.menu_aloud -> {
                handleSelectedTextReadAloud()
                return true
            }

            R.id.menu_bookmark -> {
                val bookmark = if (epubCoreActive && binding.epubReadView.isDirectMode) {
                    createDirectEpubBookmark(selectedText)
                } else {
                    binding.readView.curPage.createBookmark()
                }
                if (bookmark == null) {
                    toastOnUi(R.string.create_bookmark_error)
                } else {
                    showDialogFragment(BookmarkDialog(bookmark))
                }
                return true
            }

            R.id.menu_replace -> {
                if (!supportsReplaceRules()) return true
                val scopes = arrayListOf<String>()
                ReadBook.book?.name?.let {
                    scopes.add(it)
                }
                ReadBook.bookSource?.bookSourceUrl?.let {
                    scopes.add(it)
                }
                val text = selectedText.lineSequence().map { it.trim() }.joinToString("\n")
                replaceActivity.launch(
                    ReplaceEditActivity.startIntent(
                        this,
                        pattern = text,
                        scope = scopes.joinToString(";")
                    )
                )
                return true
            }

            R.id.menu_search_content -> {
                viewModel.searchContentQuery = selectedText
                openSearchActivity(selectedText)
                return true
            }

            R.id.menu_dict -> {
                showDialogFragment(DictDialog(selectedText))
                return true
            }
            R.id.menu_word_lookup -> {
                setWordLookupMode(!isWordLookupActive(), selectedText)
                return true
            }
            R.id.menu_ask_ai -> {
                askAiBySelection()
                return true
            }
            R.id.menu_generate_image -> {
                generateImageBySelection()
                return true
            }
            R.id.menu_share_image -> {
                showShareNotePreviewOverlay(selectedText)
                return true
            }
        }
        return false
    }

    private fun handleSelectedTextReadAloud() {
        val shouldReadContinuously =
            BaseReadAloudService.isRun || AppConfig.contentSelectSpeakMod == 1
        if (epubCoreActive) {
            if (shouldReadContinuously) {
                toastOnUi(R.string.epub_selection_read_aloud_unsupported)
            } else {
                speak(selectedText)
            }
            return
        }
        if (!shouldReadContinuously) {
            speak(selectedText)
            return
        }
        val position = binding.readView.getSelectedReadPosition()
        if (position == null) {
            toastOnUi(R.string.epub_selection_read_aloud_unsupported)
            return
        }
        ReadAloud.playFromPosition(
            context = this,
            bookUrl = position.bookUrl,
            chapterIndex = position.chapterIndex,
            chapterUrl = position.chapterUrl,
            chapterPosition = position.chapterPosition
        )
    }

    private fun askAiBySelection() {
        val prompt = selectedText.trim()
        if (prompt.isEmpty()) return
        openReadAiPanel(prompt)
    }

    private fun generateImageBySelection() {
        if (epubCoreActive) {
            toastOnUi(R.string.ai_image_insert_failed)
            return
        }
        if (AppConfig.aiCurrentImageProvider == null) {
            toastOnUi(R.string.ai_missing_config)
            return
        }
        val paragraph = currentSelectedParagraphForImage()
        if (paragraph == null) {
            toastOnUi(R.string.ai_image_no_selection)
            return
        }
        val prompt = selectedText.trim().ifBlank { paragraph.text.trim() }
        if (prompt.isBlank()) {
            toastOnUi(R.string.ai_image_no_selection)
            return
        }
        showDialogFragment(
            ReadSelectionImageDialog(
                prompt = prompt,
                paragraphIndex = paragraph.contentIndex,
                paragraphText = paragraph.text
            )
        )
    }


    private fun showShareNotePreviewOverlay(selection: String) {
        val text = selection.trim()
        if (text.isBlank()) return
        val payload = buildShareNotePayload(text)
        lifecycleScope.launch {
            val entries = runCatching {
                withContext(IO) {
                    ShareNoteTemplateManager.loadEntries()
                }
            }.getOrElse {
                AppLog.put("摘录分享模板加载失败\n${it.localizedMessage}", it, true)
                toastOnUi(it.localizedMessage ?: getString(R.string.error))
                return@launch
            }
            val entry = entries.firstOrNull { it.dirName == ShareNoteTemplateManager.lastDirName() }
                ?: entries.firstOrNull()
            if (entry == null) {
                toastOnUi(R.string.share_note_no_template)
                return@launch
            }
            shareNotePreviewOverlay?.dismiss()
            shareNotePreviewOverlay = ShareNotePreviewOverlay.show(
                activity = this@ReadBookActivity,
                parent = binding.root,
                entry = entry,
                payload = payload
            )
        }
    }

    private fun buildShareNotePayload(text: String): ShareNoteImageRenderer.Payload {
        val book = ReadBook.book
        val now = Date()
        val goalConfig = ReadRecordWidgetStore.loadGoalConfig()
        val readTimeMs = book?.let { currentShareNoteReadTime(it, now.time) } ?: 0L
        val readTimeText = readTimeMs.takeIf { it > 0L }?.let(::formatShareNoteDuration).orEmpty()
        val progressText = currentShareNoteProgressText()
        val tags = book?.shareNoteTags().orEmpty()
        return ShareNoteImageRenderer.Payload(
            generatedAt = DateFormat.getDateTimeInstance().format(now),
            profile = ShareNoteImageRenderer.Profile(
                name = goalConfig.userName.orEmpty().ifBlank { "读者" },
                avatar = goalConfig.avatar
            ),
            book = ShareNoteImageRenderer.Book(
                title = book?.name.orEmpty().ifBlank { getString(R.string.book_name) },
                author = book?.getRealAuthor().orEmpty(),
                description = BookIntroUtils.listIntro(book?.getDisplayIntro()).orEmpty(),
                cover = book?.getDisplayCover()?.let(::normalizeShareNoteCoverUrl),
                type = tags,
                kind = tags,
                tags = tags,
                wordCountText = book?.wordCount.orEmpty(),
                readTimeText = readTimeText,
                readStatusText = readTimeText.takeIf { it.isNotBlank() }?.let { "阅读 $it" }.orEmpty(),
                readProgressText = progressText,
                readProgressPercent = parseShareNoteProgressPercent(progressText),
                lastReadTime = book?.durChapterTime?.takeIf { it > 0L }?.let {
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
                }.orEmpty()
            ),
            note = ShareNoteImageRenderer.Note(
                createAt = DateFormat.getDateInstance().format(now),
                sectionName = currentShareNoteChapterTitle(),
                description = text
            )
        )
    }

    private fun currentShareNoteReadTime(book: Book, now: Long): Long {
        val saved = appDb.readRecordDao.getReadTime(book.name) ?: 0L
        val live = if (AppConfig.enableReadRecord && ReadBook.book?.bookUrl == book.bookUrl) {
            (now - ReadBook.readStartTime).coerceAtLeast(0L)
        } else {
            0L
        }
        return saved + live
    }

    private fun formatShareNoteDuration(mss: Long): String {
        val days = mss / (1000 * 60 * 60 * 24)
        val hours = mss % (1000 * 60 * 60 * 24) / (1000 * 60 * 60)
        val minutes = mss % (1000 * 60 * 60) / (1000 * 60)
        val seconds = mss % (1000 * 60) / 1000
        val d = if (days > 0) getString(R.string.duration_day, days) else ""
        val h = if (hours > 0) getString(R.string.duration_hour, hours) else ""
        val m = if (minutes > 0) getString(R.string.duration_minute, minutes) else ""
        val s = if (seconds > 0 && days == 0L && hours == 0L) {
            getString(R.string.duration_second, seconds)
        } else {
            ""
        }
        return "$d$h$m$s".ifBlank { getString(R.string.duration_zero) }
    }

    private fun currentShareNoteProgressText(): String {
        return runCatching {
            if (epubCoreActive) {
                val page = binding.epubReadView.currentPage() ?: return@runCatching ""
                val chapterSize = currentShareNoteChapterSize()
                val chapterPageIndex = binding.epubReadView.currentChapterPageIndex()
                val chapterPageCount = binding.epubReadView.currentChapterPageCount()
                shareNoteProgressText(
                    chapterIndex = page.chapterIndex,
                    chapterSize = chapterSize,
                    pageIndex = chapterPageIndex,
                    pageSize = chapterPageCount
                )
            } else {
                currentTextShareNoteProgressText()
            }
        }.getOrDefault("")
    }

    private fun currentTextShareNoteProgressText(): String {
        val chapterSize = currentShareNoteChapterSize()
        if (chapterSize <= 0) return ""
        val textChapter = ReadBook.curTextChapter
        val currentPage = runCatching { binding.readView.curPage.textPage }.getOrNull()
        val chapterIndex = firstValidChapterIndex(
            chapterSize,
            textChapter?.chapter?.index,
            ReadBook.durChapterIndex,
            ReadBook.book?.durChapterIndex,
            currentPage?.takeIf { it.chapterSize > 0 }?.chapterIndex
        )
        val pageSize = textChapter?.pageSize?.takeIf { it > 0 }
            ?: currentPage?.pageSize?.takeIf { it > 0 }
            ?: 0
        val pageIndex = textChapter
            ?.getPageIndexByCharIndex(ReadBook.durChapterPos)
            ?.takeIf { it >= 0 }
            ?: currentPage?.index?.takeIf { it >= 0 }
            ?: 0
        return shareNoteProgressText(chapterIndex, chapterSize, pageIndex, pageSize)
    }

    private fun currentShareNoteChapterSize(): Int {
        return ReadBook.simulatedChapterSize.takeIf { it > 0 }
            ?: ReadBook.chapterSize.takeIf { it > 0 }
            ?: ReadBook.book?.simulatedTotalChapterNum()?.takeIf { it > 0 }
            ?: ReadBook.book?.totalChapterNum?.takeIf { it > 0 }
            ?: 0
    }

    private fun firstValidChapterIndex(chapterSize: Int, vararg candidates: Int?): Int {
        return candidates.firstOrNull { it != null && it in 0 until chapterSize } ?: 0
    }

    private fun parseShareNoteProgressPercent(progressText: String): Float? {
        val valueText = shareNoteProgressPercentRegex.find(progressText)
            ?.groupValues
            ?.getOrNull(1)
            ?: progressText.trim()
        return valueText.replace(',', '.')
            .toFloatOrNull()
            ?.div(100f)
            ?.coerceIn(0f, 1f)
    }

    private fun shareNoteProgressText(
        chapterIndex: Int,
        chapterSize: Int,
        pageIndex: Int,
        pageSize: Int
    ): String {
        if (chapterSize <= 0) return ""
        val safeChapterIndex = chapterIndex.coerceIn(0, chapterSize - 1)
        val safePageSize = pageSize.coerceAtLeast(0)
        val safePageIndex = if (safePageSize > 0) {
            pageIndex.coerceIn(0, safePageSize - 1)
        } else {
            0
        }
        val progress = if (safePageSize <= 0) {
            (safeChapterIndex + 1.0) / chapterSize.toDouble()
        } else {
            safeChapterIndex.toDouble() / chapterSize.toDouble() +
                (safePageIndex + 1.0) / safePageSize.toDouble() / chapterSize.toDouble()
        }.coerceIn(0.0, 1.0)
        return java.text.DecimalFormat("0.0%").format(progress)
    }

    private fun Book.shareNoteTags(): String {
        return customTag?.trim()?.takeIf { it.isNotBlank() }
            ?: kind?.trim()?.takeIf { it.isNotBlank() }
            ?: when {
                isAudio -> "音频"
                isEpub -> "EPUB"
                isMobi -> "MOBI"
                isLocalTxt -> "TXT"
                isLocal -> getString(R.string.local)
                else -> ""
            }
    }

    private fun shareSelectionAsNoteImage(
        text: String,
        entry: ShareNoteTemplateManager.Entry
    ) {
        val payload = buildShareNotePayload(text)
        lifecycleScope.launch {
            toastOnUi("正在生成分享图片")
            kotlin.runCatching {
                ShareNoteImageRenderer.renderShareImage(this@ReadBookActivity, entry, payload)
            }.onSuccess { file ->
                kotlin.runCatching {
                    share(file, "image/png")
                }.onFailure {
                    toastOnUi(it.localizedMessage ?: getString(R.string.error))
                }
            }.onFailure {
                AppLog.put("摘录分享图片生成失败\n${it.localizedMessage}", it, true)
                toastOnUi(it.localizedMessage ?: getString(R.string.error))
            }
        }
    }

    private fun currentShareNoteChapterTitle(): String {
        if (epubCoreActive) {
            epubCoreChapterTitle()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return ReadBook.curTextChapter?.chapter?.title
            ?: ReadBook.book?.durChapterTitle
            ?: ""
    }

    private fun normalizeShareNoteCoverUrl(raw: String): String? {
        val value = raw.trim()
        if (value.isBlank()) return null
        if (value.startsWith("http", ignoreCase = true) ||
            value.startsWith("file:", ignoreCase = true) ||
            value.startsWith("content:", ignoreCase = true) ||
            value.startsWith("data:", ignoreCase = true)
        ) {
            return value
        }
        return runCatching {
            File(value).takeIf { it.exists() }?.toURI()?.toString()
        }.getOrNull() ?: value
    }

    private fun currentSelectedParagraphForImage(): SelectedParagraphForImage? {
        val textChapter = ReadBook.curTextChapter ?: return null
        val selectStartPos = binding.readView.curPage.selectStartPos
        if (!selectStartPos.isSelected()) return null
        val page = binding.readView.curPage.selectedStartPage() ?: return null
        val line = page.getLine(selectStartPos.lineIndex)
        if (line.paragraphNum <= 0 || line.isTitle) return null
        val paragraphs = textChapter.getParagraphs(pageSplit = false)
        val paragraph = paragraphs.firstOrNull { it.realNum == line.paragraphNum }
            ?: return null
        if (paragraph.firstLine.isTitle) return null
        val contentIndex = paragraph.sourceIndex.takeIf { it >= 0 } ?: return null
        return SelectedParagraphForImage(
            contentIndex = contentIndex,
            text = paragraph.text
        )
    }

    override fun openReadAssistant() {
        openReadAiPanel("")
    }

    override fun openReadAiSummary() {
        val modelConfig = AppConfig.aiSummaryModelConfig
        if (AppConfig.aiProviderForModel(modelConfig)?.baseUrl.isNullOrBlank()) {
            toastOnUi(R.string.ai_missing_config)
            return
        }
        if (!AppConfig.aiAssistantEnabled) {
            toastOnUi(R.string.ai_not_enabled)
            return
        }
        val book = ReadBook.book ?: run {
            toastOnUi(R.string.book_name)
            return
        }
        val textChapter = ReadBook.curTextChapter ?: run {
            toastOnUi(R.string.chapter_list)
            return
        }
        val chapter = textChapter.chapter
        val content = textChapter.getContent().trim()
        if (content.isBlank()) {
            toastOnUi(R.string.content_empty)
            return
        }
        val anchor = lastTextMenuAnchor
            ?: ReadAiFloatingPanel.Anchor(
                centerX = binding.root.width / 2,
                topY = binding.root.height / 4,
                bottomY = binding.root.height / 3
            )
        binding.readAiSummaryPanel.open(book, chapter, content, anchor)
    }

    private fun openReadAiPanel(prompt: String) {
        val modelConfig = AppConfig.aiAskModelConfig
        if (AppConfig.aiProviderForModel(modelConfig)?.baseUrl.isNullOrBlank()) {
            toastOnUi(R.string.ai_missing_config)
            return
        }
        if (!AppConfig.aiAssistantEnabled) {
            toastOnUi(R.string.ai_not_enabled)
            return
        }
        val book = ReadBook.book
        val chapter = ReadBook.curTextChapter?.chapter
        val anchor = lastTextMenuAnchor
            ?: ReadAiFloatingPanel.Anchor(
                centerX = binding.root.width / 2,
                topY = binding.root.height / 3,
                bottomY = binding.root.height / 2
            )
        binding.readAiPanel.open(
            ReadAiFloatingPanel.ReadContext(
                bookUrl = book?.bookUrl.orEmpty().ifBlank { book?.name.orEmpty() },
                bookName = book?.name.orEmpty(),
                author = book?.author.orEmpty(),
                sourceName = ReadBook.bookSource?.bookSourceName.orEmpty(),
                chapterTitle = chapter?.title.orEmpty(),
                chapterIndex = chapter?.index ?: ReadBook.durChapterIndex,
                selectedText = prompt
            ),
            anchor = anchor
        )
    }

    /**
     * 文本选择菜单操作完成
     */
    override fun onMenuActionFinally() = binding.run {
        if (epubCoreActive) {
            clearEpubSelectionUi()
        } else {
            textActionMenu.dismiss()
            readView.cancelSelect()
        }
    }

    private fun speak(text: String) {
        if (tts == null) {
            tts = TTS()
        }
        tts?.speak(text)
    }

    /**
     * 鼠标滚轮翻页
     */
    private fun mouseWheelPage(direction: PageDirection, distance: Float) {
        if (menuLayoutIsVisible || !AppConfig.mouseWheelPage) {
            return
        }
        if (epubCoreActive) {
            keyPage(direction)
            return
        }
        if (binding.readView.isScroll) {
            // 滚动视图时滚动,否则翻页
            (binding.readView.pageDelegate as? ScrollPageDelegate)?.curPage?.scroll((distance * 50).toInt())
        } else {
            keyPageDebounce(direction, mouseWheel = true, longPress = false)
        }
    }

    /**
     * 音量键翻页
     */
    private fun volumeKeyPage(direction: PageDirection, longPress: Boolean): Boolean {
        if (binding.readAloudPlayerPanel.isExpanded()) {
            return false
        }
        if (!AppConfig.volumeKeyPage) {
            return false
        }
        if (!AppConfig.volumeKeyPageOnPlay && BaseReadAloudService.isPlay()) {
            return false
        }
        handleKeyPage(direction, longPress)
        return true
    }

    private fun handleKeyPage(direction: PageDirection, longPress: Boolean) {
        if (AppConfig.keyPageOnLongPress || direction == PageDirection.NONE) {
            keyPage(direction)
        } else {
            keyPageDebounce(direction, longPress = longPress)
        }
    }

    private fun keyPageDebounce(
        direction: PageDirection,
        mouseWheel: Boolean = false,
        longPress: Boolean
    ) {
        if (longPress) {
            return
        }
        nextPageDebounce.apply {
            wait = if (mouseWheel) 200L else 600L
            leading = !mouseWheel
            trailing = mouseWheel
        }
        prevPageDebounce.apply {
            wait = if (mouseWheel) 200L else 600L
            leading = !mouseWheel
            trailing = mouseWheel
        }
        when (direction) {
            PageDirection.NEXT -> nextPageDebounce.invoke()
            PageDirection.PREV -> prevPageDebounce.invoke()
            else -> {}
        }
    }

    private fun keyPage(direction: PageDirection) {
        if (epubCoreActive) {
            when (direction) {
                PageDirection.NEXT -> nextEpubPage()
                PageDirection.PREV -> previousEpubPage()
                else -> Unit
            }
            return
        }
        binding.readView.cancelSelect()
        binding.readView.pageDelegate?.isCancel = false
        binding.readView.pageDelegate?.keyTurnPage(direction)
    }

    override fun upMenuView() {
        handler.post {
            upMenu()
            binding.readMenu.upBookView()
            binding.readAloudPlayerPanel.onChapterContentChanged()
            if (BookCloudEntryModeStore.get(ReadBook.book?.bookUrl.orEmpty()) == BookCloudEntryMode.LIBRARY_CHAPTER) {
                refreshLibraryCloudSession(refresh = false, silent = true)
            } else {
                libraryCloudSession = null
                libraryCloudState = LibraryCloudState.DISABLED
                binding.readMenu.updateCloudLibraryState(libraryCloudState)
            }
        }
    }

    override fun loadChapterList(book: Book) {
        ReadBook.upMsg(getString(R.string.toc_updateing))
        viewModel.loadChapterList(book)
    }

    /**
     * 内容加载完成
     */
    override fun contentLoadFinish() {
        if (intent.getBooleanExtra("readAloud", false)) {
            intent.removeExtra("readAloud")
            ReadBook.readAloud()
        }
        consumeOpenReadAloudPanelIntent(intent)
        consumeGlobalReadAloudPanelOpen()
        loadStates = true
    }

    private fun consumeOpenReadAloudPanelIntent(intent: Intent = this.intent): Boolean {
        val requested = requestOpenReadAloudPanelFromIntent(intent) || pendingReadAloudPanelIntentOpen
        if (!requested) return false
        return openPendingReadAloudPanel()
    }

    private fun requestOpenReadAloudPanelFromIntent(intent: Intent): Boolean {
        if (!intent.getBooleanExtra("openReadAloudPanel", false)) return false
        intent.removeExtra("openReadAloudPanel")
        pendingReadAloudPanelIntentOpen = true
        return true
    }

    private fun openPendingReadAloudPanel(): Boolean {
        if (!pendingReadAloudPanelIntentOpen) return false
        pendingReadAloudPanelIntentOpen = false
        return openReadAloudPanelFromExternalRequest()
    }

    private fun consumeGlobalReadAloudPanelOpen(): Boolean {
        if (!BaseReadAloudService.isRun) return false
        val bookUrl = ReadBook.book?.bookUrl ?: return false
        if (!ReadAloudAppCapsuleHost.consumeReadAloudPanelOpen(bookUrl)) return false
        return openReadAloudPanelFromExternalRequest()
    }

    fun openReadAloudPanelFromGlobalCapsule(): Boolean {
        return openReadAloudPanelFromExternalRequest()
    }

    private fun openReadAloudPanelFromExternalRequest(): Boolean {
        if (!BaseReadAloudService.isRun) return false
        pendingReadAloudPlayerOpen = false
        binding.readAloudPlayerPanel.post {
            binding.readAloudPlayerPanel.openFromBottom(force = true)
            binding.readAloudPlayerPanel.refresh()
        }
        return true
    }

    /**
     * 更新内容
     */
    override fun upContent(
        relativePosition: Int,
        resetPageOffset: Boolean,
        success: (() -> Unit)?
    ) {
        lifecycleScope.launch {
            if (isEpubCoreMode()) {
                if (relativePosition != 0) {
                    success?.invoke()
                    return@launch
                }
                val statusMessage = ReadBook.msg
                if (!ReadBookStartupPolicy.shouldPrepareDirectContent(statusMessage)) {
                    showDirectEpubStatus(statusMessage.orEmpty(), success)
                    return@launch
                }
                loadEpubCoreContent(
                    relativePosition = relativePosition,
                    resetPageOffset = resetPageOffset,
                    onFinish = {
                        if (relativePosition == 0) {
                            upSeekBarProgress()
                        }
                        loadStates = false
                        success?.invoke()
                    }
                )
                return@launch
            } else {
                deactivateEpubCore()
                if (!prepareNativeReaderPosition()) {
                    success?.invoke()
                    return@launch
                }
                binding.readView.upContent(relativePosition, resetPageOffset)
            }
            if (relativePosition == 0) {
                upSeekBarProgress()
            }
            loadStates = false
            success?.invoke()
        }
    }

    override suspend fun upContentAwait(
        relativePosition: Int,
        resetPageOffset: Boolean,
        success: (() -> Unit)?
    ) = withContext(Main.immediate) {
        if (isEpubCoreMode()) {
            if (relativePosition != 0) {
                success?.invoke()
                return@withContext
            }
            val statusMessage = ReadBook.msg
            if (!ReadBookStartupPolicy.shouldPrepareDirectContent(statusMessage)) {
                showDirectEpubStatus(statusMessage.orEmpty(), success)
                return@withContext
            }
            loadEpubCoreContent(
                relativePosition = relativePosition,
                resetPageOffset = resetPageOffset,
                onFinish = {
                    if (relativePosition == 0) {
                        upSeekBarProgress()
                    }
                    loadStates = false
                    success?.invoke()
                }
            )
            return@withContext
        } else {
            deactivateEpubCore()
            if (!prepareNativeReaderPosition()) {
                success?.invoke()
                return@withContext
            }
            binding.readView.upContent(relativePosition, resetPageOffset)
        }
        if (relativePosition == 0) {
            upSeekBarProgress()
        }
        loadStates = false
    }

    private fun showDirectEpubStatus(message: String, success: (() -> Unit)?) {
        if (epubCoreLoading || epubCoreLoadJob != null) {
            cancelActiveEpubCoreNavigation()
            epubDirectOnFinish = null
            epubDirectCallbackRequestSeq = 0L
        }
        upEpubRendererStyle(prepareEpubReaderBackground())
        switchEpubCore(true)
        binding.epubReadView.setError(message)
        loadStates = false
        success?.invoke()
    }

    override fun upPageAnim(upRecorder: Boolean) {
        runOnUiThread {
            // The hidden text reader is not part of the direct EPUB pipeline. Its
            // ChapterProvider.upLayout() posts UP_CONFIG and would start a second
            // EPUB chapter load while an animation setting is being applied.
            if (!isEpubCoreMode()) {
                binding.readView.upPageAnim(upRecorder)
            }
            binding.epubReadView.refreshDirectPageAnimationStyle()
        }
    }

    fun applyPageAnimationChange(previousPageAnim: Int) {
        runOnUiThread {
            val nextPageAnim = ReadBook.pageAnim()
            if (nextPageAnim == previousPageAnim) return@runOnUiThread
            if (!isEpubCoreMode()) {
                binding.readView.upPageAnim()
                ReadBook.relayoutCurrentContent("page-anim")
                return@runOnUiThread
            }
            val currentScrollMode = binding.epubReadView.layoutConfig
                ?.takeIf { epubCoreActive && binding.epubReadView.hasDirectContent }
                ?.scrollMode
            val requiresLayoutReload = currentScrollMode?.let {
                it != (nextPageAnim == PageAnim.scrollPageAnim)
            }
                ?: EpubDirectPageAnimationPolicy.requiresLayoutReload(
                    previousPageAnim = previousPageAnim,
                    nextPageAnim = nextPageAnim
                )
            if (!requiresLayoutReload) {
                if (epubCoreActive || epubCoreLoading) {
                    binding.epubReadView.refreshDirectPageAnimationStyle()
                } else {
                    loadEpubCoreContent(resetPageOffset = false)
                }
                return@runOnUiThread
            }

            cancelEpubCorePrefetch()
            when {
                epubCoreLoading -> loadEpubCoreContent(resetPageOffset = false)
                epubCoreActive && binding.epubReadView.hasDirectContent -> {
                    applyEpubRendererStyleOnly()
                }
                else -> loadEpubCoreContent(resetPageOffset = false)
            }
        }
    }

    private fun isEpubCoreMode(): Boolean {
        return ReadBook.book?.usesDirectReader == true
    }

    private suspend fun prepareNativeReaderPosition(): Boolean {
        val book = ReadBook.book ?: return true
        val chapter = ReadBook.curTextChapter ?: return true
        val requestedPosition = ReadBook.durChapterPos
        if (book.isEpub || !ReaderTextPositionStore.needsNativeRestore(book.bookUrl, chapter.chapter.url, requestedPosition)) return true
        if (!chapter.isCompleted) return false
        val restored = withContext(IO) {
            val text = chapter.pages.joinToString("") { it.text }
            text to ReaderTextPositionStore.resolve(book.bookUrl, chapter.chapter.url, "native", text, requestedPosition)
        }
        if (isEpubCoreMode() || ReadBook.book?.bookUrl != book.bookUrl || ReadBook.curTextChapter !== chapter ||
            ReadBook.durChapterPos != requestedPosition) return false
        ReadBook.durChapterPos = restored.second
        ReaderTextPositionStore.remember(book.bookUrl, chapter.chapter.url, "native", restored.first, restored.second)
        return true
    }

    private fun switchEpubCore(active: Boolean) = binding.run {
        epubCoreActive = active
        epubReadView.isVisible = active
        readView.isVisible = !active
        if (!active) {
            epubCorePageCount = 0
            epubCoreCommittedChapterIndex = null
        }
        if (active) {
            readView.cancelSelect(true)
        }
        if (epubReadView.hasLoadingPresentation || epubLoadingWindow?.isShowing == true) {
            upSystemUiVisibility()
        }
    }

    private fun deactivateEpubCore() {
        val hasDirectState = epubCoreActive ||
            epubCoreLoading ||
            epubCoreLoadJob != null ||
            epubDirectSession != null ||
            binding.epubReadView.isDirectMode
        if (hasDirectState) {
            epubDirectLinkRequestSeq++
            cancelActiveEpubCoreNavigation()
            cancelEpubCorePrefetch()
            binding.epubReadView.releaseDirectMode()
            epubDirectSession?.close()
            epubDirectSession = null
            epubDirectOnFinish = null
            epubDirectCallbackRequestSeq = 0L
        }
        switchEpubCore(false)
    }

    private fun loadEpubCoreContent(
        relativePosition: Int = 0,
        targetChapterIndex: Int? = null,
        resetPageOffset: Boolean = true,
        boundaryTransition: Boolean = false,
        onFinish: (() -> Unit)? = null,
        targetFragmentId: String? = null
    ) {
        val book = ReadBook.book
        if (book?.usesDirectReader != true) {
            deactivateEpubCore()
            onFinish?.invoke()
            return
        }
        loadDirectEpubContent(
            relativePosition = relativePosition,
            targetChapterIndex = targetChapterIndex,
            resetPageOffset = resetPageOffset,
            boundaryTransition = boundaryTransition,
            onFinish = onFinish,
            targetFragmentId = targetFragmentId
        )
    }

    private fun loadDirectEpubContent(
        relativePosition: Int,
        targetChapterIndex: Int?,
        resetPageOffset: Boolean,
        boundaryTransition: Boolean,
        onFinish: (() -> Unit)?,
        targetFragmentId: String?
    ) {
        epubDirectLinkRequestSeq++
        val book = ReadBook.book?.takeIf { it.usesDirectReader } ?: run {
            onFinish?.invoke()
            return
        }
        updateEpubReaderChromeData()
        val pendingRequest = epubCoreForegroundTarget?.takeIf {
            epubCoreLoading && targetChapterIndex == null
        }
        val navigationTarget = EpubDirectNavigationTargetPolicy.resolve(
            requestedChapterIndex = targetChapterIndex,
            requestedResetPageOffset = resetPageOffset,
            requestedBoundaryTransition = boundaryTransition,
            requestedFragmentId = targetFragmentId,
            pendingTarget = pendingRequest?.let {
                EpubDirectNavigationTargetPolicy.Target(
                    chapterIndex = it.chapterIndex,
                    resetPageOffset = it.resetPageOffset,
                    boundaryTransition = it.boundaryTransition,
                    fragmentId = it.targetFragmentId
                )
            },
            currentChapterIndex = ReadBook.durChapterIndex,
            relativePosition = relativePosition,
            chapterCount = ReadBook.chapterSize,
            preserveChapterIdentity = !book.isEpub
        ) ?: run {
            onFinish?.invoke()
            return
        }
        val preferredDirection = when {
            relativePosition < 0 -> -1
            navigationTarget.boundaryTransition && !navigationTarget.resetPageOffset -> -1
            else -> 1
        }
        val effectiveResetPageOffset = navigationTarget.resetPageOffset
        val effectiveBoundaryTransition = navigationTarget.boundaryTransition
        val effectiveTargetFragmentId = navigationTarget.fragmentId
        val resolvedChapterIndex = resolveReadableEpubChapterIndex(
            navigationTarget.chapterIndex,
            preferredDirection
        )
        // Sequential boundary navigation is an identity-preserving transaction. If the
        // current direct session cannot confirm the exact stored target, abort instead of
        // remapping it to a nearby chapter.
        val chapterIndex = if (effectiveBoundaryTransition) {
            resolvedChapterIndex?.takeIf { it == navigationTarget.chapterIndex }
        } else {
            resolvedChapterIndex
        } ?: run {
            binding.epubReadView.cancelPendingBoundaryTurn()
            if (!book.isEpub && targetChapterIndex != null) toastOnUi("章节目录已更新，请重新选择章节")
            onFinish?.invoke()
            return
        }
        val foregroundTarget = EpubCoreNavigationRequest(
            chapterIndex = chapterIndex,
            resetPageOffset = effectiveResetPageOffset,
            boundaryTransition = effectiveBoundaryTransition,
            targetFragmentId = effectiveTargetFragmentId
        )
        // Boundary navigation should consume the already-warmed neighbour.
        // Other navigation invalidates prefetch because its target/config may differ.
        if (!effectiveBoundaryTransition) {
            epubCorePrefetch.handoff(chapterIndex)
            cancelEpubCorePrefetch()
        }
        if (binding.epubReadView.width <= 0 || binding.epubReadView.height <= 0) {
            requestedReaderTemplate = selectedReaderTemplate()
            binding.epubReadView.cancelPendingDirectChapterLoad()
            epubCoreLoadJob?.cancel()
            epubCoreLoadJob = null
            val queuedRequestSeq = ++epubCoreRequestSeq
            epubCoreLoading = true
            epubCoreLoadingChapterIndex = chapterIndex
            epubCoreForegroundTarget = foregroundTarget
            epubCoreBoundaryTransition = effectiveBoundaryTransition
            epubCoreSuppressProgressSync = true
            epubDirectOnFinish = onFinish
            epubDirectCallbackRequestSeq = queuedRequestSeq
            scheduleEpubCoreLoadTimeout(queuedRequestSeq, chapterIndex, "layout")
            ReadBook.msg = null
            AppLog.putDebug(
                "EPUB direct navigation waiting for layout: index=$chapterIndex, " +
                    "request=$queuedRequestSeq, fragment=$effectiveTargetFragmentId"
            )
            prepareEpubCoreLayout {
                loadDirectEpubContent(
                    relativePosition = 0,
                    targetChapterIndex = chapterIndex,
                    resetPageOffset = effectiveResetPageOffset,
                    boundaryTransition = effectiveBoundaryTransition,
                    onFinish = onFinish,
                    targetFragmentId = effectiveTargetFragmentId
                )
            }
            return
        }
        // Preparing a replacement happens off-main. Invalidate any older staged
        // WebView now so it cannot win the race and clear this request's state.
        binding.epubReadView.cancelPendingDirectChapterLoad()
        val requestSeq = ++epubCoreRequestSeq
        val requestBookUrl = book.bookUrl
        val explicitTextPosition = if (!book.isEpub) effectiveTargetFragmentId
            ?.takeIf { it.startsWith("__legado_text_") }?.removePrefix("__legado_text_")?.toIntOrNull()
            else null
        val requestedTextPosition = explicitTextPosition ?: ReadBook.durChapterPos
        val requestedTextRevision = ReadBook.directTextRevision
        val requestedTextSource = ReadBook.bookSource
        val fontSource = ReadBookConfig.textFont.takeIf { selectedReaderTemplate() == null }.orEmpty()
        val hadVisibleDocument = binding.epubReadView.hasDirectContent
        val reusableBoundaryConfig = binding.epubReadView.layoutConfig?.takeIf {
            effectiveBoundaryTransition && hadVisibleDocument &&
                it.pageWidthPx == binding.epubReadView.width &&
                it.pageHeightPx == binding.epubReadView.height
        }
        val baseConfig = reusableBoundaryConfig ?: buildEpubCoreLayoutConfig(
            backgroundColor = prepareEpubReaderBackground()
        )
        requestedReaderTemplate = baseConfig.readerTemplate
        val visualRestoreProgress = binding.epubReadView.currentPage()
            ?.takeIf { !effectiveResetPageOffset && it.chapterIndex == chapterIndex }
            ?.let { binding.epubReadView.currentDirectProgress() }
        epubCoreLoading = true
        epubCoreLoadingChapterIndex = chapterIndex
        epubCoreForegroundTarget = foregroundTarget
        epubCoreBoundaryTransition = effectiveBoundaryTransition
        epubCoreSuppressProgressSync = true
        epubDirectOnFinish = onFinish
        epubDirectCallbackRequestSeq = requestSeq
        scheduleEpubCoreLoadTimeout(requestSeq, chapterIndex, "prepare")
        ReadBook.msg = null
        epubCoreLoadJob?.cancel()
        if (!hadVisibleDocument) {
            showInitialEpubCoreLoadingPage(baseConfig)
        }
        epubCoreLoadJob = lifecycleScope.launch(IO) {
            var failureStage = "open-session"
            val result = runCatching {
                failureStage = "prepare-reader-font"
                val config = reusableBoundaryConfig ?: withTimeout(EPUB_PREPARE_TIMEOUT_MS) {
                    baseConfig.withPreparedReaderFont(
                        fontSource = fontSource,
                        font = epubReaderFontPreparer.prepare(fontSource)
                    )
                }
                val existingSession = epubDirectSession?.takeIf {
                    isCurrentDirectSession(it, book)
                }
                val directSession = existingSession ?: if (book.isEpub) {
                    EpubCoreProvider.openDirectSession(book)
                } else {
                    TextReaderSessionProvider.open(book.copy(), requestedTextSource, requestedTextRevision) {
                        ReadBook.book?.bookUrl == requestBookUrl && isEpubCoreMode() &&
                            ReadBook.directTextRevision == requestedTextRevision
                    }
                }
                try {
                    failureStage = "prepare-chapter"
                    val preparedChapter = if (book.isEpub) directSession.prepareChapter(chapterIndex, config)
                        else runInterruptible { directSession.prepareChapter(chapterIndex, config) }
                    EpubDirectLoadResult(
                        session = directSession,
                        chapter = preparedChapter,
                        config = config,
                        ownsSession = existingSession == null,
                        textPosition = if (!book.isEpub && !effectiveBoundaryTransition &&
                            (!effectiveResetPageOffset || explicitTextPosition != null)) {
                            ReaderTextPositionStore.resolve(requestBookUrl, preparedChapter.sourceChapterUrl.orEmpty(),
                                "epub", preparedChapter.plainText, requestedTextPosition)
                        } else null
                    )
                } catch (throwable: Throwable) {
                    if (existingSession == null) directSession.close()
                    throw throwable
                }
            }
            withContext(Main.immediate) {
                val canCommit = EpubDirectRequestCommitPolicy.canCommit(
                    requestSeq = requestSeq,
                    currentRequestSeq = epubCoreRequestSeq,
                    requestBookUrl = requestBookUrl,
                    currentBookUrl = ReadBook.book?.bookUrl,
                    usesCore = isEpubCoreMode()
                )
                if (!canCommit || epubDirectCallbackRequestSeq != requestSeq ||
                    (!book.isEpub && ReadBook.directTextRevision != requestedTextRevision)) {
                    result.getOrNull()?.takeIf { it.ownsSession }?.session?.close()
                    if (epubDirectCallbackRequestSeq == requestSeq) {
                        epubDirectOnFinish = null
                        epubDirectCallbackRequestSeq = 0L
                    }
                    return@withContext
                }
                result.onSuccess { loadResult ->
                    cancelEpubCoreLoadTimeout()
                    runCatching {
                        val session = loadResult.session
                        val chapter = loadResult.chapter
                        val config = loadResult.config
                        if (epubDirectSession !== session) epubDirectSession?.close()
                        epubDirectSession = session
                        binding.epubReadView.layoutConfig = config
                        switchEpubCore(true)
                        val openAtEnd = effectiveBoundaryTransition && !effectiveResetPageOffset
                        val readAloudProgress = directReadAloudInitialProgress(
                            chapterIndex = chapterIndex,
                            directChapterLength = chapter.plainText.length
                        )
                        val initialPage = if (!book.isEpub || readAloudProgress != null || effectiveResetPageOffset) {
                            0
                        } else {
                            ReadBook.durChapterPos.coerceAtLeast(0)
                        }
                        val initialFragmentId = loadResult.textPosition?.let { "__legado_text_" + it }
                            ?: EpubDirectInitialFragmentPolicy.resolve(
                            requestedFragmentId = effectiveTargetFragmentId,
                            chapterStartFragmentId = chapter.startFragmentId,
                            resetPageOffset = effectiveResetPageOffset
                        )
                        binding.epubReadView.showDirectChapter(
                            session = session,
                            chapter = chapter,
                            config = config,
                            initialPageIndex = initialPage,
                            openAtEnd = openAtEnd,
                            initialProgress = if (book.isEpub) readAloudProgress ?: visualRestoreProgress else null,
                            initialFragmentId = initialFragmentId,
                            boundaryTransition = effectiveBoundaryTransition
                        )
                        // Download/decorate the next chapter while this WebView is laying
                        // out. The layer queues its rendering until the foreground is ready.
                        scheduleDirectEpubPrefetch(chapterIndex, requestSeq)
                    }.onFailure { throwable ->
                        if (loadResult.ownsSession && !binding.epubReadView.hasDirectContent) {
                            if (epubDirectSession === loadResult.session) epubDirectSession = null
                            loadResult.session.close()
                        }
                        finishDirectEpubFailure(
                            message = throwable.localizedMessage ?: throwable.toString(),
                            throwable = throwable,
                            stage = "web-activation",
                            chapterIndex = chapterIndex,
                            requestSeq = requestSeq
                        )
                    }
                }.onFailure { throwable ->
                    finishDirectEpubFailure(
                        message = throwable.localizedMessage ?: throwable.toString(),
                        throwable = throwable,
                        stage = failureStage,
                        chapterIndex = chapterIndex,
                        requestSeq = requestSeq
                    )
                }
            }
        }
    }

    private fun finishDirectEpubFailure(
        message: String,
        throwable: Throwable?,
        stage: String = "web-activation",
        chapterIndex: Int? = epubCoreLoadingChapterIndex,
        requestSeq: Long = epubCoreRequestSeq
    ) {
        val diagnosticMessage = EpubDirectFailureDiagnostics.userMessage(message, throwable)
        logDirectEpubFailure(stage, chapterIndex, requestSeq, diagnosticMessage, throwable)
        if (!EpubDirectRequestCommitPolicy.ownsActiveNavigation(
                requestSeq = requestSeq,
                currentRequestSeq = epubCoreRequestSeq,
                callbackRequestSeq = epubDirectCallbackRequestSeq
            )
        ) {
            AppLog.putDebug(
                "EPUB direct stale failure ignored: stage=$stage, chapter=$chapterIndex, " +
                    "request=$requestSeq, current=$epubCoreRequestSeq, " +
                    "callback=$epubDirectCallbackRequestSeq"
            )
            return
        }
        cancelEpubCoreLoadTimeout()
        epubDirectAutoPager.onTurnFailed()
        binding.epubReadView.cancelPendingBoundaryTurn()
        epubCoreLayoutReadyActionGate.cancel()
        epubCoreLoadJob = null
        val failureAction = EpubDirectRenderFailurePolicy.decide(
            hasVisibleDocument = binding.epubReadView.hasDirectContent
        )
        val onFinish = epubDirectOnFinish
        epubDirectOnFinish = null
        epubDirectCallbackRequestSeq = 0L
        epubCoreLoading = false
        epubCoreLoadingChapterIndex = null
        epubCoreForegroundTarget = null
        epubCoreBoundaryTransition = false
        epubCoreSuppressProgressSync = false
        if (chapterIndex == directReadAloudTargetChapterIndex) {
            directReadAloudTargetChapterIndex = null
        }
        binding.epubReadView.clearLoading()
        when (failureAction) {
            EpubDirectRenderFailurePolicy.Action.KeepVisibleDocument -> {
                binding.epubReadView.clearLoading()
                toastOnUi(diagnosticMessage.take(240))
            }
            EpubDirectRenderFailurePolicy.Action.ShowError -> {
                binding.epubReadView.setError(getString(R.string.error) + "\n" + diagnosticMessage)
                switchEpubCore(true)
            }
        }
        contentLoadFinish()
        onFinish?.invoke()
    }

    private fun selectedReaderTemplate(): EpubReaderTemplate? {
        if (ReadBook.book?.isEpub != false) return null
        return runCatching { EpubReaderTemplateStore.ensureReaderSelection() }.getOrElse {
            // A damaged library must not re-enable native typography or overwrite user code.
            AppLog.putDebug("读取页面模板失败，使用内置花间书页", it)
            EpubReaderTemplateStore.defaultTemplate
        }
    }

    override fun previewReaderTemplate(template: EpubReaderTemplate) {
        val session = epubDirectSession?.takeUnless { it.isClosed } ?: run {
            toastOnUi("请先加载正文，再预览页面模板")
            return
        }
        val book = ReadBook.book?.takeIf { !it.isEpub } ?: return
        val config = binding.epubReadView.layoutConfig ?: buildEpubCoreLayoutConfig(prepareEpubReaderBackground())
        ReaderTemplatePreviewDialog.show(this, session,
            binding.epubReadView.currentPage()?.chapterIndex ?: ReadBook.durChapterIndex,
            template, config, EpubReaderChromeData(bookName = book.name,
                timeLabel = AppConst.timeFormat.format(Date(System.currentTimeMillis())),
                batteryLabel = "${epubReaderBatteryLevel.coerceIn(0, 100)}%",
                batteryPercentageLabel = "${epubReaderBatteryLevel.coerceIn(0, 100)}%",
                chapterCount = currentShareNoteChapterSize()))
    }

    private fun scheduleEpubCoreLoadTimeout(
        requestSeq: Long,
        chapterIndex: Int,
        stage: String
    ) {
        cancelEpubCoreLoadTimeout()
        val templateDeadline = if (requestedReaderTemplate != null) readerTemplateActiveClock.now() + 60_000L else null
        epubCoreLoadTimeoutJob = lifecycleScope.launch {
            if (templateDeadline == null) {
                delay(EPUB_PREPARE_TIMEOUT_MS)
            } else {
                while (true) {
                    lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
                    val remaining = templateDeadline - readerTemplateActiveClock.now()
                    if (remaining <= 0L) break
                    delay(remaining)
                }
            }
            if (!EpubDirectRequestCommitPolicy.ownsActiveNavigation(
                    requestSeq = requestSeq,
                    currentRequestSeq = epubCoreRequestSeq,
                    callbackRequestSeq = epubDirectCallbackRequestSeq
                )
            ) {
                return@launch
            }
            epubCoreLoadTimeoutJob = null
            epubCoreLoadJob?.cancel()
            epubCoreLoadJob = null
            finishDirectEpubFailure(
                message = "EPUB $stage timed out",
                throwable = null,
                stage = "$stage-timeout",
                chapterIndex = chapterIndex,
                requestSeq = requestSeq
            )
        }
    }

    private fun cancelEpubCoreLoadTimeout() {
        epubCoreLoadTimeoutJob?.cancel()
        epubCoreLoadTimeoutJob = null
    }

    private fun logDirectEpubFailure(
        stage: String,
        chapterIndex: Int?,
        requestSeq: Long,
        message: String,
        throwable: Throwable?
    ) {
        val causeChain = EpubDirectFailureDiagnostics.logCauseChain(throwable)
        val book = ReadBook.book
        AppLog.put(
            "EPUB direct failure: stage=$stage, request=$requestSeq, " +
                "chapter=$chapterIndex, book=${book?.name.orEmpty()}, " +
                "bookUrl=${book?.bookUrl.orEmpty()}, error=$causeChain, detail=$message",
            throwable
        )
    }

    private fun scheduleDirectEpubPrefetch(chapterIndex: Int, requestSeq: Long) {
        if (!isEpubCoreMode() || requestSeq != epubCoreRequestSeq) return
        val book = ReadBook.book ?: return
        val session = epubDirectSession?.takeIf { isCurrentDirectSession(it, book) } ?: return
        val bookUrl = book.bookUrl
        val config = binding.epubReadView.layoutConfig ?: return
        val schedule = currentEpubPerformanceBudget()
        val candidates = EpubDirectPrefetchPolicy.candidates(
            chapterIndex = chapterIndex,
            capacity = minOf(binding.epubReadView.directPreloadCapacity(), schedule.chapterPreloadLimit),
            adjacentChapterIndex = session::adjacentChapterIndex
        )
        binding.epubReadView.updateDirectPreloadCandidates(session, candidates, config)
        epubCorePrefetch.schedule(
            owner = DirectPrefetchOwner(session, config),
            candidates = candidates,
            staggerMillis = schedule.chapterPreloadStaggerMillis,
            isPrepared = { binding.epubReadView.hasDirectChapterPreload(session, it, config) },
            prepare = { targetIndex ->
                if (book.isEpub) session.prepareChapter(targetIndex, config)
                else runInterruptible { session.prepareChapter(targetIndex, config) }
            },
            onPrepared = { targetIndex, chapter ->
                if (requestSeq == epubCoreRequestSeq && ReadBook.book?.bookUrl == bookUrl &&
                    epubDirectSession === session && isCurrentDirectSession(session, book)
                ) {
                    binding.epubReadView.preloadDirectChapter(
                        session = session,
                        chapter = chapter,
                        config = config,
                        openAtEnd = targetIndex < chapterIndex
                    )
                }
            },
            onFailure = { targetIndex, error ->
                AppLog.putDebug("EPUB direct prefetch failed: index=$targetIndex", error)
            }
        )
    }

    private fun prepareEpubCoreLayout(onReady: () -> Unit) {
        scheduleEpubCoreLayoutAction(hideUntilReady = true, onReady = onReady)
    }

    private fun scheduleEpubCoreLayoutAction(
        hideUntilReady: Boolean,
        onReady: () -> Unit
    ) {
        binding.run {
            if (hideUntilReady) {
                epubReadView.visibility = View.INVISIBLE
                readView.isVisible = !epubCoreActive
            }
            val registrationId = epubCoreLayoutReadyActionGate.submit(onReady)
            epubReadView.requestLayout()
            if (registrationId != null) {
                epubReadView.doOnLayout {
                    epubCoreLayoutReadyActionGate.consume(registrationId)?.invoke()
                }
            }
        }
    }

    private fun refreshEpubCoreAfterConfigurationChange() {
        scheduleEpubCoreLayoutAction(hideUntilReady = false) {
            loadEpubCoreContent(resetPageOffset = false)
        }
    }

    private fun applyEpubRendererStyleOnly() {
        val backgroundColor = prepareEpubReaderBackground()
        upEpubRendererStyle(backgroundColor)
        val fontSource = ReadBookConfig.textFont.takeIf { selectedReaderTemplate() == null }.orEmpty()
        val preparedFont = if (fontSource.isBlank()) {
            null
        } else {
            epubReaderFontPreparer.cached(fontSource) ?: run {
                loadEpubCoreContent(resetPageOffset = false)
                return
            }
        }
        val config = buildEpubCoreLayoutConfig(backgroundColor)
            .withPreparedReaderFont(fontSource, preparedFont)
        if (binding.epubReadView.reloadDirectStyle(config)) {
            cancelEpubCorePrefetch()
            return
        }
        loadEpubCoreContent(resetPageOffset = false)
    }

    private fun showInitialEpubCoreLoadingPage(config: EpubCoreLayoutConfig) {
        upEpubRendererStyle(config.backgroundColor)
        binding.epubReadView.layoutConfig = config
        switchEpubCore(true)
        binding.epubReadView.showLoading(getString(R.string.loading))
    }

    private fun syncEpubCoreProgress(pageIndex: Int, pageCount: Int) {
        val page = binding.epubReadView.currentPage()
        val oldChapterIndex = ReadBook.durChapterIndex
        val oldDisplayedChapterIndex = epubCoreCommittedChapterIndex
        val chapterIndex = page?.chapterIndex ?: oldChapterIndex
        val chapterPageIndex = page?.pageIndex ?: pageIndex
        epubCorePageCount = page?.totalPagesInChapter ?: pageCount
        if (epubCoreSuppressProgressSync) return
        if (BaseReadAloudService.isRun && isEpubCoreMode()) {
            epubCoreCommittedChapterIndex = chapterIndex
            if (chapterIndex != oldDisplayedChapterIndex) {
                upMenuView()
                scheduleDirectEpubPrefetch(chapterIndex, epubCoreRequestSeq)
            }
            pageChanged()
            return
        }
        updateDirectStoredPosition(chapterIndex, chapterPageIndex)
        ReadBook.saveRead(true)
        if (chapterIndex != oldChapterIndex) {
            upMenuView()
            scheduleDirectEpubPrefetch(chapterIndex, epubCoreRequestSeq)
        }
        pageChanged()
    }

    private fun updateDirectStoredPosition(chapterIndex: Int, chapterPageIndex: Int) {
        val book = ReadBook.book ?: return
        if (book.isEpub) {
            ReadBook.durChapterPos = chapterPageIndex.coerceAtLeast(0)
        } else {
            val offset = binding.epubReadView.currentDirectTextPosition()
            ReadBook.durChapterPos = offset ?: if (ReadBook.durChapterIndex == chapterIndex) ReadBook.durChapterPos else 0
            val sourceUrl = binding.epubReadView.currentSourceChapterUrl()
            if (offset != null && sourceUrl != null) {
                ReaderTextPositionStore.remember(book.bookUrl, sourceUrl, "epub", binding.epubReadView.currentDirectText(), offset)
            }
        }
        ReadBook.durChapterIndex = chapterIndex
    }

    private fun cancelActiveEpubCoreNavigation(
        clearBoundary: Boolean = true,
        advanceRequestSeq: Boolean = true
    ) {
        epubCoreLayoutReadyActionGate.cancel()
        cancelEpubCoreLoadTimeout()
        if (advanceRequestSeq) {
            epubCoreRequestSeq++
        }
        epubCoreLoadJob?.cancel()
        epubCoreLoadJob = null
        epubCoreLoading = false
        epubCoreLoadingChapterIndex = null
        epubCoreBoundaryTransition = false
        epubCoreForegroundTarget = null
        epubCoreSuppressProgressSync = false
        binding.epubReadView.clearLoading()
        if (clearBoundary) {
            epubDirectAutoPager.onTurnCancelled()
            binding.epubReadView.cancelPendingBoundaryTurn()
        }
    }

    private fun commitEpubCoreDisplayedChapter(
        chapterIndex: Int,
        chapterPageIndex: Int = binding.epubReadView.currentChapterPageIndex(),
        requestSeq: Long = ++epubCoreRequestSeq,
        schedulePrefetch: Boolean = true
    ) {
        epubCoreSuppressProgressSync = false
        epubCoreLoading = false
        epubCoreLoadingChapterIndex = null
        epubCoreForegroundTarget = null
        epubCoreBoundaryTransition = false
        binding.epubReadView.clearLoading()
        epubCorePageCount = binding.epubReadView.currentChapterPageCount()
        val preserveReadAloudPosition = BaseReadAloudService.isRun && isEpubCoreMode()
        if (!preserveReadAloudPosition) {
            updateDirectStoredPosition(chapterIndex, chapterPageIndex)
        }
        epubCoreCommittedChapterIndex = chapterIndex
        if (!preserveReadAloudPosition) ReadBook.saveRead(true)
        binding.readMenu.upBookView()
        upSeekBarProgress()
        pageChanged()
        if (schedulePrefetch) {
            scheduleDirectEpubPrefetch(chapterIndex, requestSeq)
        }
        AppLog.putDebug(
            "EPUB core commit: chapter=$chapterIndex, page=$chapterPageIndex, " +
                    "pages=$epubCorePageCount, seq=$requestSeq"
        )
    }

    private fun handleEpubCoreConfigUpdate(values: List<Int>) {
        if (values.contains(13)) {
            reloadAfterEpubEngineChanged()
            return
        }
        if (values.contains(0)) {
            upSystemUiVisibility()
        }
        if (values.contains(1)) {
            refreshEpubReaderBackgroundImmediately()
        }
        if (values.any { it == 8 || it == 10 }) {
            ChapterProvider.upStyle()
            epubReaderFontPreparer.invalidate()
        }
        val needsLayout = values.any { it == 1 || it == 2 || it == 5 || it == 6 || it == 8 || it == 10 }
        if (needsLayout) {
            cancelEpubCorePrefetch()
            loadEpubCoreContent(resetPageOffset = false)
            return
        }
        if (values.contains(0)) {
            refreshEpubCoreAfterConfigurationChange()
        } else {
            applyEpubRendererStyleOnly()
        }
    }

    private fun handleReadConfigUpdate(values: List<Int>) = binding.run {
        if (values.contains(13)) {
            reloadAfterEpubEngineChanged()
            return@run
        }
        val needSystemUi = values.contains(0)
        val needBackground = values.contains(1)
        val needStyle = values.any { it == 2 || it == 8 || it == 10 }
        val needReload = values.any { it == 5 || it == 6 || it == 8 || it == 10 }
        val needInvalidate = values.contains(9)
        val needSubmitRender = values.contains(11)
        var textRenderInvalidated = false
        if (needSystemUi) {
            upSystemUiVisibility()
        }
        if (values.contains(4)) {
            readView.upPageSlopSquare()
        }
        if (values.contains(12)) {
            readView.upPageTouchClick()
        }
        if (epubCoreActive) {
            handleEpubCoreConfigUpdate(values)
            return@run
        }
        if (needBackground) {
            readView.upBg()
        }
        if (values.contains(3)) {
            readView.upBgAlpha()
        }
        if (needStyle) {
            readView.upStyle()
            if (!needReload) {
                readView.invalidateTextPage()
                readView.submitRenderTask()
                textRenderInvalidated = true
            }
        }
        if (needReload && isInitFinish) {
            ReadBook.relayoutCurrentContent("read-config")
        } else {
            if (needInvalidate && !textRenderInvalidated) {
                readView.invalidateTextPage()
            }
            if (needSubmitRender && !textRenderInvalidated) {
                readView.submitRenderTask()
            }
        }
    }

    private fun reloadAfterEpubEngineChanged() {
        val book = ReadBook.book ?: return
        val useCore = isEpubCoreMode()
        if (!book.isEpub && epubCoreActive == useCore && ReadBookConfig.usingEpubLayout == useCore) return
        if (epubCoreActive) {
            binding.epubReadView.currentPage()?.let { updateDirectStoredPosition(it.chapterIndex, it.pageIndex) }
        }
        ReadBook.saveRead()
        if (BaseReadAloudService.isRun) ReadAloud.stopForBookSwitch(this)
        deactivateEpubCore()
        if (book.isEpub) {
            EpubCoreProvider.clearBookCache(book)
            EpubFile.clearBook(book)
            BookHelp.clearCache(book)
        }
        ReadBook.invalidateDirectTextContent(book.bookUrl)
        ReadBook.invalidateParagraphRuleLayout()
        ReadBookConfig.selectLayout(useCore)
        ChapterProvider.upStyle()
        ReadBook.msg = null
        if (useCore) {
            switchEpubCore(true)
            loadEpubCoreContent(resetPageOffset = false)
        } else {
            binding.readView.upBg()
            ReadBook.loadContent(resetPageOffset = false)
        }
        binding.readMenu.upBookView()
        upSeekBarProgress()
    }

    private fun cancelEpubCorePrefetch() {
        epubCorePrefetch.cancel()
    }

    private fun currentEpubCoreSchedule(): EpubCoreScheduleMode {
        return EpubCoreScheduleMode.fromKey(AppConfig.epubCoreScheduleMode)
    }

    private fun currentEpubPerformanceBudget(): EpubPerformanceBudget {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return EpubDirectWebViewBudgetPolicy.resolve(
            modeKey = AppConfig.epubCoreScheduleMode,
            isLowRamDevice = activityManager?.isLowRamDevice == true,
            memoryClassMb = activityManager?.memoryClass ?: 256
        )
    }

    private fun showEpubCoreScheduleModeDialog() {
        val modes = EpubCoreScheduleMode.values()
        selector(
            R.string.epub_core_schedule_mode,
            modes.map { getString(it.titleRes) }
        ) { _, index ->
            val mode = modes.getOrNull(index) ?: return@selector
            if (AppConfig.epubCoreScheduleMode == mode.key) return@selector
            AppConfig.epubCoreScheduleMode = mode.key
            binding.epubReadView.applyDirectPerformanceMode()
            menu?.findItem(R.id.menu_epub_schedule_mode)?.title =
                "${getString(R.string.epub_core_schedule_mode)} (${getString(mode.titleRes)})"
            if (!isEpubCoreMode()) return@selector
            cancelEpubCorePrefetch()
            if (!epubCoreActive || epubCoreLoading) return@selector
            val chapterIndex = binding.epubReadView.currentPage()?.chapterIndex ?: ReadBook.durChapterIndex
            scheduleDirectEpubPrefetch(chapterIndex, epubCoreRequestSeq)
        }
    }

    private fun buildEpubCoreLayoutConfig(backgroundColor: Int): EpubCoreLayoutConfig {
        val view = binding.epubReadView
        val template = selectedReaderTemplate()
        val scrollMode = ReadBook.pageAnim() == PageAnim.scrollPageAnim
        val displayCutout = if (template != null || AppConfig.paddingDisplayCutouts) {
            ViewCompat.getRootWindowInsets(view)
                ?.getInsets(WindowInsetsCompat.Type.displayCutout())
        } else {
            null
        }
        if (template != null) {
            val insets = ViewCompat.getRootWindowInsets(view)?.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val origin = IntArray(2)
            view.getLocationInWindow(origin)
            val safe = EpubTemplateSafeAreaPolicy.resolve(
                window.decorView.width.coerceAtLeast(origin[0] + view.width),
                window.decorView.height.coerceAtLeast(origin[1] + view.height),
                origin[0], origin[1], view.width, view.height,
                EpubTemplateSafeAreaPolicy.Insets(
                    insets?.left ?: 0, insets?.top ?: 0, insets?.right ?: 0, insets?.bottom ?: 0
                )
            )
            return EpubTemplateLayoutPolicy.isolate(EpubCoreLayoutConfig(
                pageWidthPx = view.width, pageHeightPx = view.height, textPaint = TextPaint(),
                readerSafeInsetLeftPx = safe.left, readerSafeInsetTopPx = safe.top,
                readerSafeInsetRightPx = safe.right, readerSafeInsetBottomPx = safe.bottom,
                scrollMode = scrollMode, readerTemplate = template
            ), resources.displayMetrics.density)
        }
        val textPaint = TextPaint().apply {
            isAntiAlias = true
            color = ReadBookConfig.textColor
            textSize = ReadBookConfig.textSize.toFloat().spToPx()
            letterSpacing = ReadBookConfig.letterSpacing
            typeface = ChapterProvider.contentPaint.typeface
        }
        val readerFontName = epubCoreReaderFontName()
        val textHeightPx = textPaint.textHeight
        val paragraphIndentPx = ReadBookConfig.paragraphIndent.takeIf { it.isNotEmpty() }?.let {
            var width = StaticLayout.getDesiredWidth(it, textPaint)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                width += textPaint.letterSpacing * textPaint.textSize
            }
            width
        } ?: 0f
        return EpubCoreLayoutConfig(
            pageWidthPx = view.width,
            pageHeightPx = view.height,
            paddingLeftPx = 0,
            paddingTopPx = 0,
            paddingRightPx = 0,
            paddingBottomPx = 0,
            readerPaddingLeftPx = ReadBookConfig.paddingLeft.dpToPx(),
            readerPaddingTopPx = if (scrollMode) 0 else ReadBookConfig.paddingTop.dpToPx(),
            readerPaddingRightPx = ReadBookConfig.paddingRight.dpToPx(),
            readerPaddingBottomPx = if (scrollMode) 0 else ReadBookConfig.paddingBottom.dpToPx(),
            readerSafeInsetLeftPx = displayCutout?.left ?: 0,
            readerSafeInsetTopPx = displayCutout?.top ?: 0,
            readerSafeInsetRightPx = displayCutout?.right ?: 0,
            readerSafeInsetBottomPx = displayCutout?.bottom ?: 0,
            paragraphSpacingPx = textHeightPx * ReadBookConfig.paragraphSpacing / 10f,
            paragraphIndentPx = paragraphIndentPx,
            textPaint = textPaint,
            textFontWeight = ReadBookConfig.textWeight,
            textFontItalic = textPaint.typeface?.isItalic == true,
            readerFontFamily = readerFontName,
            textFullJustify = ReadBookConfig.textFullJustify,
            textBottomJustify = ReadBookConfig.textBottomJustify,
            lineHeightPx = textHeightPx * ReadBookConfig.lineSpacingExtra / 10f,
            scrollMode = scrollMode,
            backgroundColor = backgroundColor,
            selectionColor = ContextCompat.getColor(this, R.color.btn_bg_press_2),
            readerBackgroundImage = epubReaderUsesImageBackground(),
            readerChrome = buildEpubReaderChromeConfig()
        )
    }

    private fun buildEpubReaderChromeConfig(): EpubReaderChromeConfig {
        val headerMode = ReadTipConfig.headerMode.takeIf {
            EpubReaderChromeModePolicy.isSupported(
                directEpub = true,
                mode = it,
                advancedMode = ReadTipConfig.HEADER_MODE_ADVANCED
            )
        }
        val footerMode = ReadTipConfig.footerMode.takeIf {
            EpubReaderChromeModePolicy.isSupported(
                directEpub = true,
                mode = it,
                advancedMode = ReadTipConfig.FOOTER_MODE_ADVANCED
            )
        }
        val headerEnabled = when (headerMode) {
            ReadTipConfig.HEADER_MODE_SHOW -> true
            ReadTipConfig.HEADER_MODE_STATUS -> !ReadBookConfig.hideStatusBar
            else -> false
        }
        val footerEnabled = footerMode == ReadTipConfig.FOOTER_MODE_SHOW
        if (!headerEnabled && !footerEnabled) return EpubReaderChromeConfig.DISABLED

        val textSizePx = 12f.spToPx()
        val tipPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = textSizePx
            typeface = ChapterProvider.typeface
        }
        val lineHeightPx = maxOf(
            tipPaint.fontMetricsInt.descent - tipPaint.fontMetricsInt.ascent,
            (textSizePx * 1.2f).toInt()
        ).coerceAtLeast(1)
        val headerPaddingLeftPx = ReadBookConfig.headerPaddingLeft.dpToPx()
        val headerPaddingTopPx = ReadBookConfig.headerPaddingTop.dpToPx()
        val headerPaddingRightPx = ReadBookConfig.headerPaddingRight.dpToPx()
        val headerPaddingBottomPx = ReadBookConfig.headerPaddingBottom.dpToPx()
        val footerPaddingLeftPx = ReadBookConfig.footerPaddingLeft.dpToPx()
        val footerPaddingTopPx = ReadBookConfig.footerPaddingTop.dpToPx()
        val footerPaddingRightPx = ReadBookConfig.footerPaddingRight.dpToPx()
        val footerPaddingBottomPx = ReadBookConfig.footerPaddingBottom.dpToPx()
        val dividerHeightPx = 0.5f.dpToPx().toInt().coerceAtLeast(1)
        val headerHeightPx = if (headerEnabled) {
            lineHeightPx + headerPaddingTopPx + headerPaddingBottomPx +
                dividerHeightPx.takeIf { ReadBookConfig.showHeaderLine }.orZero()
        } else {
            0
        }
        val footerHeightPx = if (footerEnabled) {
            lineHeightPx + footerPaddingTopPx + footerPaddingBottomPx +
                dividerHeightPx.takeIf { ReadBookConfig.showFooterLine }.orZero()
        } else {
            0
        }
        val textColor = ReadTipConfig.tipColor.takeUnless { it == 0 } ?: ReadBookConfig.textColor
        val dividerColor = when (ReadTipConfig.tipDividerColor) {
            -1 -> ContextCompat.getColor(this, R.color.divider)
            0 -> ReadBookConfig.textColor
            else -> ReadTipConfig.tipDividerColor
        }
        val geometryRevision = listOf(
            headerEnabled.hashCode(), footerEnabled.hashCode(), headerHeightPx, footerHeightPx
        ).fold(17L) { result, value -> result * 31L + value }
        return EpubReaderChromeConfig(
            enabled = true,
            headerEnabled = headerEnabled,
            footerEnabled = footerEnabled,
            hideHeaderOnChapterFirstPage = true,
            headerHeightPx = headerHeightPx,
            footerHeightPx = footerHeightPx,
            headerPaddingLeftPx = headerPaddingLeftPx,
            headerPaddingTopPx = headerPaddingTopPx,
            headerPaddingRightPx = headerPaddingRightPx,
            headerPaddingBottomPx = headerPaddingBottomPx,
            footerPaddingLeftPx = footerPaddingLeftPx,
            footerPaddingTopPx = footerPaddingTopPx,
            footerPaddingRightPx = footerPaddingRightPx,
            footerPaddingBottomPx = footerPaddingBottomPx,
            textSizePx = textSizePx,
            textColor = textColor,
            dividerColor = dividerColor,
            headerDividerEnabled = ReadBookConfig.showHeaderLine,
            footerDividerEnabled = ReadBookConfig.showFooterLine,
            backgroundColor = Color.TRANSPARENT,
            backgroundAlpha = 0f,
            headerLeft = EpubReaderChromeLegacyFieldPolicy.resolve(ReadTipConfig.tipHeaderLeft),
            headerCenter = EpubReaderChromeLegacyFieldPolicy.resolve(ReadTipConfig.tipHeaderMiddle),
            headerRight = EpubReaderChromeLegacyFieldPolicy.resolve(ReadTipConfig.tipHeaderRight),
            footerLeft = EpubReaderChromeLegacyFieldPolicy.resolve(ReadTipConfig.tipFooterLeft),
            footerCenter = EpubReaderChromeLegacyFieldPolicy.resolve(ReadTipConfig.tipFooterMiddle),
            footerRight = EpubReaderChromeLegacyFieldPolicy.resolve(ReadTipConfig.tipFooterRight),
            geometryRevision = geometryRevision
        )
    }

    private fun Int?.orZero(): Int = this ?: 0

    private fun updateEpubReaderChromeData() {
        val book = ReadBook.book?.takeIf { it.usesDirectReader } ?: return
        val battery = epubReaderBatteryLevel.coerceIn(0, 100)
        val batteryLabel = "$battery%"
        binding.epubReadView.updateDirectReaderChromeData(
            EpubReaderChromeData(
                bookName = book.name,
                timeLabel = AppConst.timeFormat.format(Date(System.currentTimeMillis())),
                batteryLabel = batteryLabel,
                batteryPercentageLabel = batteryLabel,
                chapterCount = currentShareNoteChapterSize(),
                contentRevision = ++epubReaderChromeContentRevision
            )
        )
    }

    private fun upEpubRendererStyle(backgroundColor: Int) {
        // The host is only a neutral fallback. Reader theme artwork is installed by
        // EpubDirectWebLayer after checking the active chapter's background policy.
        binding.epubReadView.background = null
        binding.epubReadView.setBackgroundColor(backgroundColor)
        binding.epubReadView.loadingNightMode = AppConfig.isNightTheme
        binding.epubReadView.invalidate()
    }

    private fun refreshEpubReaderBackgroundImmediately() {
        val backgroundColor = prepareEpubReaderBackground()
        upEpubRendererStyle(backgroundColor)
        val currentConfig = binding.epubReadView.layoutConfig ?: return
        binding.epubReadView.refreshDirectReaderBackground(
            currentConfig.copy(
                backgroundColor = backgroundColor,
                readerBackgroundImage = epubReaderUsesImageBackground()
            )
        )
    }

    private fun prepareEpubReaderBackground(): Int {
        if (selectedReaderTemplate() != null) return android.graphics.Color.WHITE
        val metrics = resources.displayMetrics
        val width = binding.epubReadView.width.takeIf { it > 0 } ?: metrics.widthPixels
        val height = binding.epubReadView.height.takeIf { it > 0 } ?: metrics.heightPixels
        ReadBookConfig.upBg(width.coerceAtLeast(1), height.coerceAtLeast(1))
        return epubReaderBackgroundColor()
    }

    private fun epubReaderUsesImageBackground(): Boolean {
        if (selectedReaderTemplate() != null) return false
        return ReadBookConfig.durConfig.curBgType() != 0
    }

    private fun epubReaderBackgroundColor(): Int {
        if (selectedReaderTemplate() != null) return android.graphics.Color.WHITE
        val config = ReadBookConfig.durConfig
        return runCatching {
            if (config.curBgType() == 0) {
                android.graphics.Color.parseColor(config.curBgStr())
            } else {
                ReadBookConfig.bgMeanColor.takeIf { android.graphics.Color.alpha(it) > 0 }
                    ?: android.graphics.Color.rgb(250, 248, 241)
            }
        }.getOrDefault(android.graphics.Color.rgb(250, 248, 241))
    }

    private fun epubCoreReaderFontName(): String? {
        return if (ReadBookConfig.textFont.isBlank()) {
            when (AppConfig.systemTypefaces) {
                1 -> "serif"
                2 -> "monospace"
                else -> "sans-serif"
            }
        } else {
            "legado-reader-font"
        }
    }

    private fun epubCoreReaderFontUrl(): String? {
        return ReadBookConfig.textFont
            .takeIf { it.isNotBlank() }
            ?.let { "https://epub.local/__legado_reader_font__" }
    }

    private fun nextEpubPage() {
        requestNextEpubPage()
    }

    private fun requestNextEpubPage(automatic: Boolean = false): EpubPageTurnResult {
        if (!epubCoreActive && !epubCoreLoading) {
            return EpubPageTurnResult.Unavailable
        }
        if (binding.epubReadView.isSelectionBlockingPageTurn) {
            return EpubPageTurnResult.Rejected
        }
        if (!automatic) ReadBook.markReadAloudUserNavigation()
        if (epubCoreLoading) {
            if (binding.epubReadView.hasPendingBoundaryTurn(1)) {
                return EpubPageTurnResult.Queued
            }
            if (automatic) return EpubPageTurnResult.Rejected
            requestEpubCoreChapterByDirection(
                1,
                EpubChapterNavigationPolicy.Intent.PageTurnBoundary
            )
            return EpubPageTurnResult.BoundaryRequired
        }
        val result = binding.epubReadView.nextPage()
        if (result.requiresBoundaryNavigation) {
            requestEpubCoreChapterByDirection(
                1,
                EpubChapterNavigationPolicy.Intent.PageTurnBoundary
            )
        }
        return result
    }

    private fun previousEpubPage() {
        if (!epubCoreActive && !epubCoreLoading) return
        if (binding.epubReadView.isSelectionBlockingPageTurn) return
        ReadBook.markReadAloudUserNavigation()
        if (epubCoreLoading) {
            if (binding.epubReadView.hasPendingBoundaryTurn(-1)) return
            requestEpubCoreChapterByDirection(
                -1,
                EpubChapterNavigationPolicy.Intent.PageTurnBoundary
            )
            return
        }
        if (binding.epubReadView.previousPage().requiresBoundaryNavigation) {
            requestEpubCoreChapterByDirection(
                -1,
                EpubChapterNavigationPolicy.Intent.PageTurnBoundary
            )
        }
    }

    private fun openEpubCoreChapter(
        index: Int,
        resetPageOffset: Boolean,
        boundaryTransition: Boolean = false,
        targetFragmentId: String? = null
    ) {
        val chapterIndex = if (boundaryTransition || ReadBook.book?.isEpub == false) {
            index.takeIf { it >= 0 } ?: return
        } else {
            index.coerceIn(0, (ReadBook.chapterSize - 1).coerceAtLeast(0))
        }
        if (!BaseReadAloudService.isRun) {
            ReadBook.durChapterPos = if (resetPageOffset) 0 else ReadBook.durChapterPos
        }
        loadEpubCoreContent(
            targetChapterIndex = chapterIndex,
            resetPageOffset = resetPageOffset,
            boundaryTransition = boundaryTransition,
            targetFragmentId = targetFragmentId
        )
    }

    override fun openNextEpubCoreChapter() {
        requestEpubCoreChapterByDirection(
            1,
            EpubChapterNavigationPolicy.Intent.ExplicitChapterJump
        )
    }

    override fun openPreviousEpubCoreChapter() {
        requestEpubCoreChapterByDirection(
            -1,
            EpubChapterNavigationPolicy.Intent.ExplicitChapterJump
        )
    }

    private fun requestEpubCoreChapterByDirection(
        direction: Int,
        intent: EpubChapterNavigationPolicy.Intent
    ) {
        if (binding.epubReadView.isSelectionBlockingPageTurn) {
            binding.epubReadView.cancelPendingBoundaryTurn()
            return
        }
        if (ReadBook.chapterSize <= 0) {
            binding.epubReadView.cancelPendingBoundaryTurn()
            return
        }
        val target = EpubChapterNavigationPolicy.resolve(direction, intent) ?: run {
            binding.epubReadView.cancelPendingBoundaryTurn()
            return
        }
        if (target.boundaryTransition && epubCoreLoading) {
            // One accepted boundary gesture owns the navigation transaction until it commits
            // or fails. Opposite rapid input must not recalculate from the still-visible source.
            return
        }
        val resetPageOffset = target.edge == EpubChapterNavigationPolicy.TargetEdge.Start
        val currentIndex = binding.epubReadView.currentPage()?.chapterIndex
            ?: epubCoreCommittedChapterIndex
            ?: ReadBook.durChapterIndex
        // A boundary turn already resolved its target in EpubDirectWebLayer. Reuse that exact
        // identity; only explicit navigation needs a fresh adjacent lookup.
        val targetIndex = if (target.boundaryTransition) {
            binding.epubReadView.pendingBoundaryChapterIndex(direction) ?: run {
                binding.epubReadView.cancelPendingBoundaryTurn()
                return
            }
        } else {
            adjacentLogicalEpubChapterIndex(currentIndex, target.chapterDelta)
        }
        if (targetIndex == null) {
            binding.epubReadView.cancelPendingBoundaryTurn()
            return
        }
        epubCoreBoundaryTransition = target.boundaryTransition
        requestEpubCoreChapter(
            index = targetIndex,
            resetPageOffset = resetPageOffset,
            navigationMode = EpubCoreNavigationMode.Sequential,
            boundaryTransition = target.boundaryTransition
        )
    }

    private fun isCurrentDirectSession(session: EpubDirectSession, book: io.legado.app.data.entities.Book): Boolean =
        !session.isClosed && session.bookUrl == book.bookUrl &&
            (book.isEpub || session.sourceRevision == ReadBook.directTextRevision)

    private fun resolveReadableEpubChapterIndex(
        requestedIndex: Int,
        preferredDirection: Int
    ): Int? {
        val book = ReadBook.book ?: return null
        val bookUrl = book.bookUrl
        epubDirectSession?.takeIf { isCurrentDirectSession(it, book) }?.let { session ->
            session.resolveReadableChapterIndex(requestedIndex, preferredDirection)?.let { return it }
        }
        val chapters = runCatching { appDb.bookChapterDao.getChapterList(bookUrl) }
            .getOrDefault(emptyList())
        if (chapters.isEmpty()) {
            return requestedIndex.takeIf { it in 0 until ReadBook.chapterSize }
        }
        return if (book.isEpub) EpubReadableChapterPolicy.resolve(chapters, requestedIndex, preferredDirection)
            else chapters.firstOrNull { it.index == requestedIndex }?.index
    }

    private fun adjacentLogicalEpubChapterIndex(currentIndex: Int, direction: Int): Int? {
        val book = ReadBook.book ?: return null
        val bookUrl = book.bookUrl
        epubDirectSession?.takeIf { isCurrentDirectSession(it, book) }?.let { session ->
            session.adjacentLogicalChapterIndex(currentIndex, direction)?.let { return it }
        }
        val chapters = runCatching { appDb.bookChapterDao.getChapterList(bookUrl) }
            .getOrDefault(emptyList())
        if (chapters.isEmpty()) {
            return (currentIndex + if (direction < 0) -1 else 1)
                .takeIf { it in 0 until ReadBook.chapterSize }
        }
        return if (book.isEpub) EpubReadableChapterPolicy.adjacentLogical(chapters, currentIndex, direction)
            else if (direction < 0) chapters.filter { it.index < currentIndex }.maxOfOrNull { it.index }
            else chapters.filter { it.index > currentIndex }.minOfOrNull { it.index }
    }

    private fun EpubCoreLayoutConfig.withPreparedReaderFont(
        fontSource: String,
        font: EpubPreparedReaderFont?
    ): EpubCoreLayoutConfig {
        if (readerTemplate != null) return this
        if (fontSource.isNotBlank()) {
            check(font != null) { "Custom EPUB reader font is not prepared" }
        }
        return copy(
            readerFontFamily = if (font != null) "legado-reader-font" else readerFontFamily,
            readerFontUrl = font?.let { "https://epub.local/__legado_reader_font__" },
            readerFontPath = font?.filePath,
            readerFontRevision = font?.revision,
            readerFontMimeType = font?.mimeType,
            readerFontLength = font?.length,
            readerFontOverridePublisher = font != null
        )
    }

    private fun requestEpubCoreChapter(
        index: Int,
        resetPageOffset: Boolean,
        navigationMode: EpubCoreNavigationMode,
        boundaryTransition: Boolean = false,
        targetFragmentId: String? = null,
        fromReadAloud: Boolean = false
    ) {
        if (!fromReadAloud) {
            ReadBook.markReadAloudUserNavigation()
            pendingDirectReadAloudProgress = null
            directReadAloudTargetChapterIndex = null
        }
        if (binding.epubReadView.isSelectionBlockingPageTurn) {
            binding.epubReadView.cancelPendingBoundaryTurn()
            return
        }
        if (ReadBook.chapterSize <= 0) return
        if (!boundaryTransition) binding.epubReadView.cancelPendingBoundaryTurn()
        val chapterIndex = if (boundaryTransition || ReadBook.book?.isEpub == false) {
            index.takeIf { it >= 0 } ?: run {
                binding.epubReadView.cancelPendingBoundaryTurn()
                return
            }
        } else {
            index.coerceIn(0, ReadBook.chapterSize - 1)
        }
        val currentRealPage = binding.epubReadView.currentPage()
        if (!boundaryTransition &&
            chapterIndex == currentRealPage?.chapterIndex &&
            !epubCoreLoading
        ) {
            if (!targetFragmentId.isNullOrBlank()) {
                val navigationSeq = ++epubDirectLinkRequestSeq
                val scheduled = binding.epubReadView.navigateDirectToFragment(targetFragmentId) { success ->
                    if (!success &&
                        navigationSeq == epubDirectLinkRequestSeq &&
                        binding.epubReadView.currentPage()?.chapterIndex == chapterIndex
                    ) {
                        openEpubCoreChapter(
                            index = chapterIndex,
                            resetPageOffset = true,
                            targetFragmentId = targetFragmentId
                        )
                    }
                }
                if (scheduled) return
                openEpubCoreChapter(
                    index = chapterIndex,
                    resetPageOffset = true,
                    targetFragmentId = targetFragmentId
                )
                return
            }
            binding.epubReadView.setChapterPageEdge(chapterIndex, toLastPage = !resetPageOffset)
            return
        }
        if (!boundaryTransition &&
            navigationMode == EpubCoreNavigationMode.Sequential &&
            epubCoreActive &&
            currentRealPage != null &&
            binding.epubReadView.setChapterPageEdge(chapterIndex, toLastPage = !resetPageOffset)
        ) {
            finishEpubCoreInstantNavigation(chapterIndex)
            return
        }
        if (epubCoreLoading) {
            val previous = epubCoreForegroundTarget
            AppLog.putDebug(
                "EPUB core navigation preempt: from=${previous?.chapterIndex} to=$chapterIndex reset=$resetPageOffset"
            )
            cancelActiveEpubCoreNavigation(clearBoundary = !boundaryTransition)
        }
        openEpubCoreChapter(
            index = chapterIndex,
            resetPageOffset = resetPageOffset,
            boundaryTransition = boundaryTransition,
            targetFragmentId = targetFragmentId
        )
    }

    private fun finishEpubCoreInstantNavigation(chapterIndex: Int) {
        epubCoreSuppressProgressSync = false
        val requestSeq = epubCoreRequestSeq
        epubCoreLoadJob?.cancel()
        epubCoreLoadJob = null
        cancelEpubCoreLoadTimeout()
        epubCoreLoading = false
        epubCoreLoadingChapterIndex = null
        epubCoreForegroundTarget = null
        binding.epubReadView.clearLoading()
        epubCorePageCount = binding.epubReadView.currentChapterPageCount()
        val preserveReadAloudPosition = BaseReadAloudService.isRun && isEpubCoreMode()
        if (!preserveReadAloudPosition) {
            updateDirectStoredPosition(chapterIndex, binding.epubReadView.currentChapterPageIndex())
        }
        epubCoreCommittedChapterIndex = chapterIndex
        if (!preserveReadAloudPosition) ReadBook.saveRead(true)
        binding.readMenu.upBookView()
        upSeekBarProgress()
        consumePendingDirectReadAloudProgress(chapterIndex)
        scheduleDirectEpubPrefetch(chapterIndex, requestSeq)
    }

    private fun directReadAloudChapterLength(progress: ReadAloudProgressState): Int {
        val textChapterLength = ReadBook.curTextChapter
            ?.takeIf { it.chapter.index == progress.chapterIndex }
            ?.lastPage
            ?.let { (it.chapterPosition + it.text.length).coerceAtLeast(0) }
            ?: 0
        val directChapterLength = binding.epubReadView.currentPage()
            ?.takeIf { it.chapterIndex == progress.chapterIndex }
            ?.text
            ?.length
            ?: 0
        val progressExtent = (progress.chapterPosition.toLong() + progress.cueText.length + 1L)
            .coerceIn(1L, Int.MAX_VALUE.toLong())
            .toInt()
        return maxOf(
            1,
            textChapterLength,
            directChapterLength,
            progressExtent
        )
    }

    private fun directReadAloudInitialProgress(
        chapterIndex: Int,
        directChapterLength: Int
    ): Float? {
        if (!BaseReadAloudService.isRun ||
            ReadBook.isReadAloudUserNavigationActive() ||
            ReadBook.durChapterIndex != chapterIndex
        ) return null
        val characterPosition = ReadBook.durChapterPos
        if (characterPosition == Int.MAX_VALUE) return 1f
        val textChapterLength = ReadBook.curTextChapter
            ?.takeIf { it.chapter.index == chapterIndex }
            ?.lastPage
            ?.let { (it.chapterPosition + it.text.length).coerceAtLeast(0) }
            ?: 0
        val chapterLength = maxOf(
            1,
            directChapterLength,
            textChapterLength,
            characterPosition.coerceAtLeast(0) + 1
        )
        return characterPosition.toDouble()
            .coerceAtLeast(0.0)
            .div((chapterLength - 1).coerceAtLeast(1).toDouble())
            .toFloat()
            .coerceIn(0f, 1f)
    }

    private fun requestDirectReadAloudChapter(
        chapterIndex: Int,
        progress: ReadAloudProgressState?
    ) {
        if (!isEpubCoreMode() || !BaseReadAloudService.isRun) return
        if (chapterIndex !in 0 until ReadBook.chapterSize) return
        val safeProgress = progress
            ?: pendingDirectReadAloudProgress?.takeIf { it.chapterIndex == chapterIndex }
            ?: ReadAloudProgressState(
                bookUrl = ReadBook.book?.bookUrl.orEmpty(),
                chapterIndex = chapterIndex,
                chapterUrl = ReadBook.curTextChapter
                    ?.takeIf { it.chapter.index == chapterIndex }
                    ?.chapter?.url.orEmpty(),
                chapterPosition = ReadBook.durChapterPos.coerceAtLeast(0)
        )
        pendingDirectReadAloudProgress = safeProgress
        directReadAloudTargetChapterIndex = chapterIndex
        if (ReadBook.isReadAloudUserNavigationActive() ||
            binding.epubReadView.isSelectionBlockingPageTurn
        ) return
        ReadBook.durChapterIndex = chapterIndex
        ReadBook.durChapterPos = safeProgress.chapterPosition.coerceAtLeast(0)
        val displayedChapter = binding.epubReadView.currentPage()?.chapterIndex
        if (displayedChapter == chapterIndex && !epubCoreLoading) {
            consumePendingDirectReadAloudProgress(
                binding.epubReadView.currentPage()?.chapterIndex ?: return
            )
            return
        }
        if (epubCoreLoadingChapterIndex == chapterIndex ||
            epubCoreForegroundTarget?.chapterIndex == chapterIndex
        ) {
            return
        }
        requestEpubCoreChapter(
            index = chapterIndex,
            resetPageOffset = safeProgress.chapterPosition <= 0,
            navigationMode = EpubCoreNavigationMode.Sequential,
            boundaryTransition = false,
            fromReadAloud = true
        )
    }

    private fun applyDirectReadAloudProgress(progress: ReadAloudProgressState) {
        if (!isEpubCoreMode() || !BaseReadAloudService.isRun) return
        if (ReadBook.isReadAloudUserNavigationActive() ||
            binding.epubReadView.isSelectionBlockingPageTurn
        ) return
        ReadBook.durChapterIndex = progress.chapterIndex
        ReadBook.durChapterPos = progress.chapterPosition.coerceAtLeast(0)
        val position = binding.epubReadView.currentPage()
        if (position == null || position.chapterIndex != progress.chapterIndex || epubCoreLoading) {
            if (epubCoreLoadingChapterIndex != progress.chapterIndex &&
                epubCoreForegroundTarget?.chapterIndex != progress.chapterIndex
            ) {
                requestDirectReadAloudChapter(
                    chapterIndex = progress.chapterIndex,
                    progress = progress
                )
            }
            return
        }
        if (directReadAloudTargetChapterIndex == progress.chapterIndex) {
            directReadAloudTargetChapterIndex = null
        }
        if (!BaseReadAloudService.isPlay() || progress.cueText.isBlank()) {
            pendingDirectReadAloudProgress = pendingDirectReadAloudProgress
                ?.takeUnless { it == progress }
            return
        }
        val chapterLength = directReadAloudChapterLength(progress)
        val approximateProgress = progress.chapterPosition.toDouble()
            .coerceAtLeast(0.0)
            .div((chapterLength - 1).coerceAtLeast(1).toDouble())
            .toFloat()
        val accepted = binding.epubReadView.followReadAloud(
            cueText = progress.cueText,
            cueOffset = (progress.chapterPosition - progress.cueStartPosition)
                .coerceAtLeast(0),
            approximateProgress = approximateProgress
        )
        if (accepted) {
            pendingDirectReadAloudProgress = pendingDirectReadAloudProgress
                ?.takeUnless { it == progress }
        }
    }

    private fun consumePendingDirectReadAloudProgress(chapterIndex: Int) {
        val pending = pendingDirectReadAloudProgress ?: return
        if (pending.chapterIndex != chapterIndex) return
        if (ReadBook.isReadAloudUserNavigationActive() ||
            binding.epubReadView.isSelectionBlockingPageTurn
        ) return
        pendingDirectReadAloudProgress = null
        directReadAloudTargetChapterIndex = null
        applyDirectReadAloudProgress(pending)
    }

    private fun persistDirectVisualProgress() {
        val readAloudTarget = directReadAloudTargetChapterIndex
        if (readAloudTarget != null &&
            (epubCoreLoadingChapterIndex == readAloudTarget ||
                epubCoreForegroundTarget?.chapterIndex == readAloudTarget)
        ) {
            cancelActiveEpubCoreNavigation()
        }
        val position = binding.epubReadView.currentPage() ?: return
        updateDirectStoredPosition(position.chapterIndex, position.pageIndex)
        epubCoreCommittedChapterIndex = position.chapterIndex
        epubCorePageCount = position.totalPagesInChapter
        ReadBook.saveRead(true)
        binding.readMenu.upBookView()
        upSeekBarProgress()
    }

    override fun onReadAloudChapterChanged(chapterIndex: Int, direction: Int) {
        if (!isEpubCoreMode() || !BaseReadAloudService.isRun) return
        runOnUiThread {
            requestDirectReadAloudChapter(
                chapterIndex = chapterIndex,
                progress = null
            )
        }
    }

    override fun notifyBookChanged() {
        bookChanged = true
        if (!ReadBook.inBookshelf) {
            viewModel.removeFromBookshelf { super.finish() }
        }
    }

    override fun cancelSelect() {
        runOnUiThread {
            binding.readView.cancelSelect()
            binding.epubReadView.cancelSelect()
        }
    }

    /**
     * 页面改变
     */
    override fun pageChanged() {
        pageChanged = true
        if (!epubCoreActive) {
            binding.readView.onPageChange()
        }
        handler.post {
            upSeekBarProgress()
        }
        executor.execute {
            startBackupJob()
        }
    }

    /**
     * 更新进度条位置
     */
    private fun upSeekBarProgress() {
        val progress = when (AppConfig.progressBarBehavior) {
            "page" -> if (epubCoreActive) binding.epubReadView.currentChapterPageIndex() else ReadBook.durPageIndex
            else /* chapter */ -> ReadBook.durChapterIndex
        }
        binding.readMenu.setSeekPage(progress)
    }

    /**
     * 显示菜单
     */
    override fun showMenuBar() {
        binding.readMenu.runMenuIn()
    }

    override val oldBook: Book?
        get() = ReadBook.book

    override fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        if (!book.isAudio) {
            viewModel.changeTo(book, toc)
        } else {
            ReadAloud.stop(this)
            lifecycleScope.launch {
                withContext(IO) {
                    val oldBook = ReadBook.book
                    oldBook?.migrateTo(book, toc)
                    book.inheritNotShelfStateFrom(oldBook)
                    book.removeType(BookType.updateError)
                    oldBook?.delete()
                    appDb.bookDao.insert(book)
                }
                startActivityForBook(book) {
                    putExtra("inBookshelf", resolveStoredBookshelfState(book))
                }
                super.finish()
            }
        }
    }

    override fun replaceContent(content: String) {
        ReadBook.book?.let {
            viewModel.saveContent(it, content)
        }
    }

    override fun showActionMenu() {
        when {
            isAutoPage -> showDialogFragment<AutoReadDialog>()
            isShowingSearchResult -> binding.searchMenu.runMenuIn()
            else -> binding.readMenu.runMenuIn()
        }
    }

    /**
     * 显示朗读菜单
     */
    override fun showReadAloudDialog() {
        fun openPlayerPanel() {
            binding.readAloudPlayerPanel.openFromBottom(force = true)
        }
        fun startOrOpenPlayerPanel() {
            when {
                !BaseReadAloudService.isRun -> {
                    pendingReadAloudPlayerOpen = true
                    onClickReadAloud()
                }
                BaseReadAloudService.pause -> {
                    ReadAloud.resume(this)
                    openPlayerPanel()
                }
                else -> openPlayerPanel()
            }
        }
        if (binding.readMenu.isVisible) {
            binding.readMenu.runMenuOut {
                startOrOpenPlayerPanel()
            }
        } else {
            startOrOpenPlayerPanel()
        }
    }

    override fun onReadAloudPlayerVisibilityChanged(visible: Boolean) {
        ReadAloudAppCapsuleHost.updateReadBookPanelActive(visible)
        upSystemUiVisibility()
    }

    /**
     * 自动翻页
     */
    override fun autoPage() {
        ReadAloud.stop(this)
        if (isAutoPage) {
            autoPageStop()
        } else if (isEpubCoreMode()) {
            val position = binding.epubReadView.currentPage()?.let {
                EpubDirectAutoPager.Position(it.chapterIndex, it.pageIndex)
            } ?: return
            if (!epubDirectAutoPager.start(position)) return
            binding.readMenu.setAutoPage(true)
            screenTimeOut = -1L
            screenOffTimerStart()
        } else {
            binding.readView.autoPager.start()
            binding.readMenu.setAutoPage(true)
            screenTimeOut = -1L
            screenOffTimerStart()
        }
    }

    override fun autoPageStop() {
        if (epubDirectAutoPager.isRunning) {
            epubDirectAutoPager.stop()
        } else if (binding.readView.isAutoPage) {
            binding.readView.autoPager.stop()
            binding.readMenu.setAutoPage(false)
            dismissDialogFragment<AutoReadDialog>()
            upScreenTimeOut()
        }
    }

    private fun finishAutoPageUi() {
        if (!binding.readView.isAutoPage && !epubDirectAutoPager.isRunning) {
            binding.readMenu.setAutoPage(false)
            dismissDialogFragment<AutoReadDialog>()
            upScreenTimeOut()
        }
    }

    override fun openSourceEditActivity() {
        ReadBook.bookSource?.let {
            sourceEditActivity.launch {
                putExtra("sourceUrl", it.bookSourceUrl)
            }
        }
    }

    override fun openBookInfoActivity() {
        ReadBook.book?.let {
            bookInfoActivity.launch {
                putExtra("name", it.name)
                putExtra("author", it.author)
            }
        }
    }

    override fun returnToBookshelf() {
        finish()
    }

    override fun openReplaceRule() {
        if (!supportsReplaceRules()) return
        replaceActivity.launch(Intent(this, ReplaceRuleActivity::class.java))
    }

    /**
     * 打开目录
     */
    override fun openChapterList() {
        ReadBook.book?.let {
            tocActivity.launch(it.bookUrl)
        }
    }

    /**
     * 打开搜索界面
     */
    override fun openSearchActivity(searchWord: String?) {
        if (isEpubCoreMode()) {
            toastOnUi(R.string.native_reader_feature_only)
            return
        }
        val book = ReadBook.book ?: return
        searchContentActivity.launch {
            putExtra("bookUrl", book.bookUrl)
            putExtra("searchWord", searchWord ?: viewModel.searchContentQuery)
            putExtra("searchResultIndex", viewModel.searchResultIndex)
            viewModel.searchResultList?.first()?.let {
                if (it.query == viewModel.searchContentQuery) {
                    IntentData.put("searchResultList", viewModel.searchResultList)
                }
            }
        }
    }

    /**
     * 禁用书源
     */
    override fun disableSource() {
        viewModel.disableSource()
    }

    /**
     * 显示阅读样式配置
     */
    override fun showReadStyle() {
        if (ReadBook.usesPageTemplate()) showDialogFragment<io.legado.app.ui.book.read.config.ReaderTemplateDialog>()
        else showDialogFragment<ReadStyleDialog>()
    }

    /**
     * 显示更多设置
     */
    override fun showMoreSetting() {
        showDialogFragment<MoreConfigDialog>()
    }

    override fun showParagraphRuleQuickDialog() {
        if (isEpubCoreMode()) return
        val book = ReadBook.book ?: run {
            toastOnUi(R.string.paragraph_rule_no_book_hint)
            return
        }
        lifecycleScope.launch {
            val isEmpty = withContext(IO) {
                appDb.paragraphRuleDao.all().isEmpty()
            }
            if (isEmpty) {
                toastOnUi(R.string.paragraph_rule_empty)
                return@launch
            }
            ParagraphRuleQuickDialog.create(book.bookUrl)
                .show(supportFragmentManager, "paragraphRuleQuick")
        }
    }

    override fun showBubbleQuickSwitch() {
        if (isEpubCoreMode()) return
        showDialogFragment(
            BubbleQuickSwitchDialog.create(
                onSelected = { entry -> applyBubblePackageFromReadMenu(entry) },
                onManage = { startActivity<BubbleManageActivity>() }
            )
        )
    }

    private fun applyBubblePackageFromReadMenu(entry: BubblePackageManager.Entry) {
        lifecycleScope.launch {
            kotlin.runCatching {
                withContext(IO) {
                    BubblePackageManager.apply(entry)
                    ImageProvider.clear()
                }
            }.onSuccess {
                postEvent(EventBus.UP_CONFIG, arrayListOf(5))
                toastOnUi(R.string.success)
            }.onFailure {
                toastOnUi(it.localizedMessage ?: getString(R.string.error))
            }
        }
    }

    override fun runCustomReadMenuButton(id: Long) {
        val book = ReadBook.book ?: run {
            toastOnUi(R.string.paragraph_rule_no_book_hint)
            return
        }
        val chapter = ReadBook.curTextChapter?.chapter ?: run {
            toastOnUi(R.string.read_menu_no_current_chapter)
            return
        }
        lifecycleScope.launch {
            val button = withContext(IO) { appDb.readMenuCustomButtonDao.get(id) }
            if (button == null) {
                toastOnUi(R.string.read_menu_custom_button_missing)
                return@launch
            }
            kotlin.runCatching {
                withContext(IO) {
                    val content = BookHelp.getContent(book, chapter).orEmpty()
                    ReadMenuCustomButtonExecutor.execute(
                        this@ReadBookActivity,
                        button,
                        book,
                        chapter,
                        content,
                        ReadBook.bookSource
                    )
                }
            }.onFailure {
                AppLog.put("阅读菜单自定义按键执行失败: ${button.displayName()}\n${it.localizedMessage}", it, true)
                toastOnUi(it.localizedMessage ?: getString(R.string.error))
            }
        }
    }

    override fun editCustomReadMenuButton(id: Long) {
        startActivity<ReadMenuCustomButtonEditActivity> {
            putExtra("id", id)
        }
    }

    override fun loginCustomReadMenuButton(id: Long) {
        lifecycleScope.launch {
            val button = withContext(IO) { appDb.readMenuCustomButtonDao.get(id) }
            if (button == null) {
                toastOnUi(R.string.read_menu_custom_button_missing)
                return@launch
            }
            if (button.loginUrl.isBlank() && button.loginUi.isBlank()) {
                toastOnUi(R.string.source_no_login)
                return@launch
            }
            startActivity<SourceLoginActivity> {
                putExtra("bookType", -1)
                putExtra("type", "readMenuCustomButton")
                putExtra("key", id.toString())
                ReadBook.book?.bookUrl?.let { putExtra("bookUrl", it) }
                putExtra("chapterIndex", ReadBook.durChapterIndex)
            }
        }
    }

    internal fun refreshParagraphRuleLayout() {
        if (ReadBook.book == null) return
        ReadBook.invalidateParagraphRuleLayout()
        ReadBook.callBack?.upContent(resetPageOffset = false)
        ReadBook.loadContent(resetPageOffset = false)
    }

    override fun openBookCharacters() {
        val book = ReadBook.book ?: run {
            toastOnUi("当前书籍不存在")
            return
        }
        startActivity<BookCharacterManageActivity> {
            putExtra(BookCharacterManageActivity.EXTRA_BOOK_URL, book.bookUrl)
        }
    }

    override fun showSearchSetting() {
        showDialogFragment<MoreConfigDialog>()
    }

    /**
     * 取词模式, 按书记忆
     */
    override fun isWordLookupActive(): Boolean = ReadBook.book?.isWordLookupEnabled() == true

    /**
     * 切换取词模式, 进入时顺带查询长按选中的字词
     */
    private fun setWordLookupMode(enabled: Boolean, lookupText: String? = null) {
        val book = ReadBook.book ?: return
        book.setWordLookup(enabled)
        ReadBook.saveRead(fullUpdate = true)
        toastOnUi(if (enabled) R.string.word_lookup_on else R.string.word_lookup_off)
        if (!enabled) return
        val word = lookupText?.trim().orEmpty()
        if (word.isNotEmpty()) {
            showDictDialog(word)
        }
        lifecycleScope.launch {
            val hasDictRule = withContext(IO) { appDb.dictRuleDao.enabled.isNotEmpty() }
            if (!hasDictRule) {
                toastOnUi(R.string.word_lookup_no_rule)
            }
        }
    }

    override fun showDictDialog(word: String) {
        val key = word.trim()
        if (key.isEmpty()) return
        showDialogFragment(DictDialog(key))
    }

    override fun isLibraryCloudEnabled(): Boolean {
        val book = ReadBook.book ?: return false
        if (book.isLocal) return false
        return when (BookCloudEntryModeStore.get(book.bookUrl)) {
            BookCloudEntryMode.CACHE_PACKAGE -> false
            BookCloudEntryMode.LIBRARY_CHAPTER ->
                LibraryContainerManager.readContainer() != null
        }
    }

    override fun libraryCloudState(): LibraryCloudState = libraryCloudState

    override fun showLibraryCloudChapters(refresh: Boolean) {
        val book = ReadBook.book ?: return
        if (BookCloudEntryModeStore.get(book.bookUrl) == BookCloudEntryMode.CACHE_PACKAGE) {
            return
        }
        val chapterIndex = ReadBook.durChapterIndex
        lifecycleScope.launch {
            val session = withContext(IO) {
                if (refresh) {
                    LibraryCloudSync.refreshSession(book)
                } else {
                    currentLibraryCloudSession() ?: LibraryCloudSync.openSession(book)
                }
            }
            libraryCloudSession = session
            libraryCloudState = session.state
            binding.readMenu.updateCloudLibraryState(session.state)
            if (session.state != LibraryCloudState.READY) {
                toastOnUi(libraryCloudStateMessage(session))
                return@launch
            }
            val currentChapter = withContext(IO) {
                appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex)
            } ?: run {
                toastOnUi("未找到当前章节")
                return@launch
            }
            val versions = withContext(IO) {
                session.listChapterVersions(currentChapter)
            }
            if (versions.isEmpty()) {
                toastOnUi("云端没有匹配当前章节的缓存")
                return@launch
            }
            showLibraryCloudChapterDialog(book, session, currentChapter, versions)
        }
    }

    override fun showLibraryCloudDebug() {
        if (!BuildConfig.DEBUG) return
        val book = ReadBook.book ?: return
        val chapterIndex = ReadBook.durChapterIndex
        lifecycleScope.launch {
            val chapter = withContext(IO) {
                appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex)
            }
            val container = LibraryContainerManager.readContainer()
            val v3Debug = if (chapter != null && container != null) {
                withContext(IO) {
                    val v3SharedBookKey = LibraryCloudKeys.sharedBookKey(book)
                    val v3ExactBookKey = LibraryCloudKeys.bookKey(book)
                    val v3ChapterKey = LibraryCloudKeys.libraryChapterKey(chapter)
                    val currentPaths = listOf(
                        LibraryCloudPaths.v3CurrentPath(v3SharedBookKey, v3ChapterKey),
                        LibraryCloudPaths.v3CurrentPath(v3ExactBookKey, v3ChapterKey)
                    ).distinct()
                    val manifestPaths = listOf(
                        LibraryCloudPaths.v3ManifestPath(v3ExactBookKey, v3ChapterKey),
                        LibraryCloudPaths.v3ManifestPath(v3SharedBookKey, v3ChapterKey)
                    ).distinct()
                    val backend = LibraryCloudBackend(container)
                    suspend fun currentState(path: String): String {
                        return runCatching {
                            val bytes = backend.downloadOrNull(path)
                                ?: return@runCatching "$path = missing"
                            val json = LibraryCloudCrypto.decodeString(bytes, container.password)
                            val payload = GSON.fromJsonObject<LibraryChapterPayloadV3>(json).getOrThrow()
                            "$path = exists source=${payload.sourceName.ifBlank { payload.sourceUrl }} hash=${payload.contentHash}"
                        }.getOrElse {
                            "$path = error ${it.localizedMessage}"
                        }
                    }
                    suspend fun manifestState(path: String): String {
                        return runCatching {
                            val bytes = backend.downloadOrNull(path)
                                ?: return@runCatching "$path = missing"
                            val json = LibraryCloudCrypto.decodeString(bytes, container.password)
                            val manifest = GSON.fromJsonObject<LibraryChapterManifestV3>(json).getOrThrow()
                            "$path = exists variants=${manifest.variants.size}"
                        }.getOrElse {
                            "$path = error ${it.localizedMessage}"
                        }
                    }
                    runCatching {
                        val currentLines = currentPaths.map { currentState(it) }
                        val manifestLines = manifestPaths.map { manifestState(it) }
                        buildString {
                            appendLine("v3CurrentState:")
                            currentLines.forEach { appendLine(it) }
                            appendLine("v3ManifestState:")
                            manifestLines.forEach { appendLine(it) }
                        }.trimEnd()
                    }.getOrElse {
                        "v3State=error ${it.localizedMessage}"
                    }
                }
            } else {
                "v3State=not_checked"
            }
            val message = buildString {
                appendLine("book.name=${book.name}")
                appendLine("book.author=${book.getRealAuthor()}")
                appendLine("book.bookUrl=${book.bookUrl}")
                appendLine("book.origin=${book.origin}")
                appendLine("book.originName=${book.originName}")
                appendLine()
                appendLine("exactBookKey=${LibraryCloudKeys.bookKey(book)}")
                appendLine("sharedBookKey=${LibraryCloudKeys.sharedBookKey(book)}")
                appendLine("allBookKeys=${LibraryCloudKeys.bookKeys(book).joinToString()}")
                appendLine("normalizedBookName=${LibraryCloudKeys.normalizeBookName(book.name)}")
                appendLine()
                appendLine("container=${LibraryContainerManager.displayLabel(container)}")
                appendLine("containerId=${container?.id.orEmpty()}")
                appendLine("containerPrefix=${container?.container?.prefix.orEmpty()}")
                appendLine()
                if (chapter == null) {
                    appendLine("chapter=null")
                } else {
                    appendLine("chapter.index=${chapter.index}")
                    appendLine("chapter.title=${chapter.title}")
                    appendLine("chapter.url=${chapter.url}")
                    appendLine("chapter.identity=${chapter.contentCacheIdentity()}")
                    appendLine("canonicalTitle=${LibraryCloudKeys.canonicalTextForKey(chapter.title)}")
                    appendLine("titleCodePoints=${LibraryCloudKeys.debugCodePoints(chapter.title)}")
                    appendLine("chapterKey=${LibraryCloudKeys.chapterKey(chapter)}")
                    appendLine("titleKey=${LibraryCloudKeys.titleKey(chapter)}")
                    appendLine("relaxedTitle=${LibraryCloudKeys.relaxedTitle(chapter.title)}")
                    appendLine("relaxedTitleKey=${LibraryCloudKeys.relaxedTitleKey(chapter)}")
                    appendLine("ordinalTitle=${LibraryCloudKeys.chapterOrdinal(chapter.title).orEmpty()}")
                    appendLine("ordinalTitleKey=${LibraryCloudKeys.ordinalTitleKey(chapter).orEmpty()}")
                    appendLine("matchKeys=${LibraryCloudKeys.matchKeys(chapter).joinToString { "${it.kind}:${it.key}" }}")
                    appendLine("sourceKey=${LibraryCloudKeys.sourceKey(book.origin)}")
                    appendLine()
                    val v3SharedBookKey = LibraryCloudKeys.sharedBookKey(book)
                    val v3ExactBookKey = LibraryCloudKeys.bookKey(book)
                    val v3ChapterKey = LibraryCloudKeys.libraryChapterKey(chapter)
                    val sourceKey = LibraryCloudKeys.sourceKey(book.origin)
                    appendLine("v3SharedBookKey=$v3SharedBookKey")
                    appendLine("v3ExactBookKey=$v3ExactBookKey")
                    appendLine("v3ChapterKey=$v3ChapterKey")
                    appendLine("v3SharedCurrentPath=${LibraryCloudPaths.v3CurrentPath(v3SharedBookKey, v3ChapterKey)}")
                    appendLine("v3ExactCurrentPath=${LibraryCloudPaths.v3CurrentPath(v3ExactBookKey, v3ChapterKey)}")
                    appendLine("v3LegacyManifestPath=${LibraryCloudPaths.v3ManifestPath(v3ExactBookKey, v3ChapterKey)}")
                    appendLine("v3LegacyPayloadPath=${LibraryCloudPaths.v3PayloadPath(v3ExactBookKey, v3ChapterKey, sourceKey)}")
                    appendLine(v3Debug)
                    appendLine("v3RequestEstimate.uploadNew=1B+1A")
                    appendLine("v3RequestEstimate.uploadDuplicate=1B")
                    appendLine("v3RequestEstimate.readHit=1B")
                    appendLine("v3RequestEstimate.readLegacyFallback<=4B")
                    appendLine("v3RequestEstimate.listVersions<=4B")
                    appendLine()
                    appendLine("listPrefixes:")
                    LibraryCloudKeys.bookKeys(book).forEach { bookKey ->
                        LibraryCloudKeys.matchKeys(chapter).forEach { matchKey ->
                            appendLine(LibraryCloudPaths.variantsPrefix(bookKey, matchKey))
                        }
                    }
                    appendLine()
                    appendLine("currentPaths:")
                    LibraryCloudKeys.bookKeys(book).forEach { bookKey ->
                        LibraryCloudKeys.matchKeys(chapter).forEach { matchKey ->
                            appendLine(LibraryCloudPaths.currentChapterPath(bookKey, matchKey))
                        }
                    }
                }
            }
            val debugTextView = TextView(this@ReadBookActivity).apply {
                text = message
                setTextIsSelectable(true)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(20.dpToPx(), 12.dpToPx(), 20.dpToPx(), 12.dpToPx())
            }
            val debugScrollView = ScrollView(this@ReadBookActivity).apply {
                addView(
                    debugTextView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
            AlertDialog.Builder(this@ReadBookActivity)
                .setTitle("书库调试")
                .setView(debugScrollView)
                .setNegativeButton("复制") { _, _ ->
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("书库调试", message))
                    toastOnUi("已复制")
                }
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun refreshLibraryCloudSession(refresh: Boolean, silent: Boolean) {
        val book = ReadBook.book
        if (book == null || !isLibraryCloudEnabled()) {
            libraryCloudSession = null
            libraryCloudState = LibraryCloudState.DISABLED
            binding.readMenu.updateCloudLibraryState(libraryCloudState)
            return
        }
        lifecycleScope.launch {
            val session = withContext(IO) {
                if (refresh) {
                    LibraryCloudSync.refreshSession(book)
                } else {
                    currentLibraryCloudSession() ?: LibraryCloudSync.openSession(book)
                }
            }
            if (ReadBook.book?.bookUrl != book.bookUrl) return@launch
            libraryCloudSession = session
            libraryCloudState = session.state
            binding.readMenu.updateCloudLibraryState(session.state)
            if (!silent && session.state != LibraryCloudState.READY) {
                toastOnUi(libraryCloudStateMessage(session))
            }
        }
    }

    private fun currentLibraryCloudSession(): LibraryCloudSession? {
        val currentContainerId = LibraryContainerManager.readContainer()?.id
        return libraryCloudSession?.takeIf { it.config?.id == currentContainerId }
    }

    private fun downloadLibraryCloudChapter(
        book: Book,
        session: LibraryCloudSession,
        localChapter: BookChapter,
        item: LibraryCloudChapterVersion
    ) {
        lifecycleScope.launch {
            ReadBook.upMsg("读取云端章节")
            val result = withContext(IO) {
                runCatching {
                    val content = session.downloadChapter(item)
                        ?: throw NoStackTraceException("云端章节内容不存在")
                    BookHelp.saveText(book, localChapter, content)
                    localChapter
                }
            }
            ReadBook.upMsg(null)
            result.onSuccess { chapter ->
                if (ReadBook.book?.bookUrl != book.bookUrl) return@onSuccess
                LibraryCloudSync.setCloudReadingActive(book, true)
                libraryCloudState = LibraryCloudState.READY
                binding.readMenu.updateCloudLibraryState(libraryCloudState)
                if (isEpubCoreMode()) {
                    skipToChapter(chapter.index)
                } else {
                    viewModel.openChapter(chapter.index)
                }
            }.onFailure {
                toastOnUi("读取云端章节失败\n${it.localizedMessage}")
            }
        }
    }

    private fun showLibraryCloudChapterDialog(
        book: Book,
        session: LibraryCloudSession,
        currentChapter: BookChapter,
        items: List<LibraryCloudChapterVersion>
    ) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4.dpToPx(), 0, 4.dpToPx(), 0)
        }
        val summaryView = TextView(this).apply {
            text = "当前章节：${currentChapter.title}"
            textSize = 13f
            setTextColor(secondaryTextColor)
            setPadding(0, 0, 0, 8.dpToPx())
            applyUiBodyTypefaceDeep(this@ReadBookActivity.uiTypeface())
        }
        val listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        var dialog: AlertDialog? = null
        items.groupBy { libraryCloudSourceGroupKey(it) }.forEach { (_, sourceItems) ->
            val sourceLabel = libraryCloudSourceLabel(sourceItems.first())
            listLayout.addView(TextView(this).apply {
                text = sourceLabel
                textSize = 14f
                setTextColor(secondaryTextColor)
                setPadding(2.dpToPx(), 12.dpToPx(), 2.dpToPx(), 6.dpToPx())
                applyUiTitleTypeface(this@ReadBookActivity)
            })
            sourceItems.forEach { item ->
                listLayout.addView(createLibraryCloudChapterRow(book, session, currentChapter, item) {
                    dialog?.dismiss()
                })
            }
        }
        val scrollView = NestedScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                listLayout,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.55f).toInt()
            )
        }
        content.addView(summaryView)
        content.addView(scrollView)
        dialog = AndroidAlertBuilder(this).apply {
            setTitle("选择书库章节")
            setCustomView(content)
            negativeButton(android.R.string.cancel)
        }.show()
    }

    private fun createLibraryCloudChapterRow(
        book: Book,
        session: LibraryCloudSession,
        currentChapter: BookChapter,
        item: LibraryCloudChapterVersion,
        dismissParent: () -> Unit
    ): View {
        val cardColor = themeCardColorOrDefault()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dpToPx(), 10.dpToPx(), 14.dpToPx(), 10.dpToPx())
            background = UiCorner.opaqueRounded(
                cardColor,
                UiCorner.panelRadius(this@ReadBookActivity)
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 10.dpToPx()
            }
            addView(TextView(this@ReadBookActivity).apply {
                text = libraryCloudChapterTitle(item)
                setTextColor(primaryTextColor)
                textSize = 15f
                applyUiTitleTypeface(this@ReadBookActivity)
            })
            libraryCloudChapterTime(item)?.let { time ->
                addView(TextView(this@ReadBookActivity).apply {
                    text = time
                    textSize = 13f
                    setTextColor(secondaryTextColor)
                    setPadding(0, 4.dpToPx(), 0, 8.dpToPx())
                    applyUiBodyTypefaceDeep(this@ReadBookActivity.uiTypeface())
                })
            }
            addView(LinearLayout(this@ReadBookActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(libraryCloudActionText("读取", accentColor) {
                    dismissParent()
                    downloadLibraryCloudChapter(book, session, currentChapter, item)
                })
                addView(libraryCloudActionText("删除", 0xFFD32F2F.toInt()) {
                    confirmDeleteLibraryCloudChapter(book, session, currentChapter, item, dismissParent)
                })
            })
            setOnClickListener {
                dismissParent()
                downloadLibraryCloudChapter(book, session, currentChapter, item)
            }
        }
    }

    private fun libraryCloudActionText(
        text: String,
        textColor: Int,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(textColor)
            textSize = 14f
            setPadding(0, 4.dpToPx(), 14.dpToPx(), 4.dpToPx())
            applyUiBodyTypefaceDeep(this@ReadBookActivity.uiTypeface())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { onClick() }
        }
    }

    private fun confirmDeleteLibraryCloudChapter(
        book: Book,
        session: LibraryCloudSession,
        currentChapter: BookChapter,
        item: LibraryCloudChapterVersion,
        dismissParent: () -> Unit
    ) {
        val input = EditText(this).apply {
            hint = "请输入 删除"
            setSingleLine(true)
            typeface = uiTypeface()
            setPadding(20.dpToPx(), 8.dpToPx(), 20.dpToPx(), 8.dpToPx())
        }
        val confirmDialog = AndroidAlertBuilder(this).apply {
            setTitle("删除云端章节")
            setMessage("将删除云端章节：${item.payload.title.ifBlank { currentChapter.title }}\n请输入“删除”确认。")
            setCustomView(input)
            negativeButton(android.R.string.cancel)
            positiveButton("删除")
        }.show()
        confirmDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (input.text?.toString()?.trim() != "删除") {
                toastOnUi("请输入 删除 后再确认")
                return@setOnClickListener
            }
            confirmDialog.dismiss()
            dismissParent()
            deleteLibraryCloudChapter(book, session, currentChapter, item)
        }
    }

    private fun deleteLibraryCloudChapter(
        book: Book,
        session: LibraryCloudSession,
        currentChapter: BookChapter,
        item: LibraryCloudChapterVersion
    ) {
        lifecycleScope.launch {
            ReadBook.upMsg("删除云端章节")
            val result = withContext(IO) {
                runCatching {
                    if (!session.deleteChapter(item)) {
                        throw NoStackTraceException("云端章节不存在或删除失败")
                    }
                    LibraryCloudSync.refreshSession(book)
                }
            }
            ReadBook.upMsg(null)
            val refreshedSession = result.getOrNull()
            if (refreshedSession != null) {
                if (ReadBook.book?.bookUrl != book.bookUrl) return@launch
                libraryCloudSession = refreshedSession
                libraryCloudState = refreshedSession.state
                binding.readMenu.updateCloudLibraryState(refreshedSession.state)
                toastOnUi("已删除云端章节")
                showLibraryCloudChapters(refresh = false)
            } else {
                toastOnUi("删除云端章节失败\n${result.exceptionOrNull()?.localizedMessage.orEmpty()}")
            }
        }
    }

    private fun libraryCloudSourceGroupKey(item: LibraryCloudChapterVersion): String {
        val payload = item.payload
        return listOf(payload.sourceUrl, payload.sourceBookUrl, payload.sourceName).joinToString("\u001F")
    }

    private fun libraryCloudSourceLabel(item: LibraryCloudChapterVersion): String {
        return item.payload.sourceName
            .ifBlank { item.payload.sourceUrl }
            .ifBlank { "旧缓存/未知来源" }
    }

    private fun libraryCloudChapterTitle(item: LibraryCloudChapterVersion): String {
        val payload = item.payload
        val title = payload.title.ifBlank { payload.normalizedTitle.ifBlank { payload.chapterKey } }
        val prefix = if (payload.chapterIndex >= 0) "${payload.chapterIndex + 1}. " else ""
        return "$prefix$title"
    }

    private fun libraryCloudChapterTime(item: LibraryCloudChapterVersion): String? {
        return item.payload.updatedAt.takeIf { it > 0L }?.let {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
        }
    }

    private fun libraryCloudStateMessage(session: LibraryCloudSession): String {
        return when (session.state) {
            LibraryCloudState.DISABLED -> "未配置书库容器"
            LibraryCloudState.READY -> "云端书库可用"
            LibraryCloudState.ERROR -> session.errorMessage?.let { "云端书库读取失败\n$it" }
                ?: "云端书库读取失败"
        }
    }

    /**
     * 更新状态栏,导航栏
     */
    override fun upSystemUiVisibility() {
        epubLoadingWindow?.restoreSystemBars()
        if (binding.readAloudPlayerPanel.isFullPanelActive()) {
            applyReadAloudPlayerSystemBars()
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            upSystemUiVisibility(isInMultiWindow, !menuLayoutIsVisible, bottomDialog > 0)
            upNavigationBarColor()
        }
        syncEpubLoadingWindow()
    }

    private fun syncEpubLoadingWindow() {
        val show = epubCoreActive && binding.epubReadView.hasLoadingPresentation &&
            !menuLayoutIsVisible && !binding.readAloudPlayerPanel.isFullPanelActive()
        val overlay = epubLoadingWindow ?: if (show) {
            EpubLoadingWindowOverlay(window, binding.epubReadView).also { epubLoadingWindow = it }
        } else return
        overlay.update(show)
    }

    @Suppress("DEPRECATION")
    private fun applyReadAloudPlayerSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding.navigationBar.visibility = View.GONE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.getInsetsController(window, binding.root)?.run {
                if (ReadBookConfig.hideNavigationBar) {
                    hide(WindowInsetsCompat.Type.navigationBars())
                } else {
                    show(WindowInsetsCompat.Type.navigationBars())
                }
                if (ReadBookConfig.hideStatusBar) {
                    hide(WindowInsetsCompat.Type.statusBars())
                } else {
                    show(WindowInsetsCompat.Type.statusBars())
                }
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
        var flag = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        if (ReadBookConfig.hideNavigationBar) {
            flag = flag or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        }
        if (ReadBookConfig.hideStatusBar) {
            flag = flag or View.SYSTEM_UI_FLAG_FULLSCREEN
        }
        window.decorView.systemUiVisibility = flag
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        setLightStatusBar(false)
    }

    override fun onNightThemeChanged() = binding.run {
        ThemeConfig.applyTheme(this@ReadBookActivity)
        readMenu.reset()
        if (epubCoreActive) {
            if (epubReadView.width > 0 && epubReadView.height > 0) {
                ReadBookConfig.upBg(epubReadView.width, epubReadView.height)
            }
            epubReadView.clearSelection()
            cancelEpubCorePrefetch()
            refreshEpubCoreAfterConfigurationChange()
        } else {
            readView.refreshVisualStyle()
        }
        upSystemUiVisibility()
    }

    // 退出全文搜索
    override fun exitSearchMenu() {
        if (isShowingSearchResult) {
            if (binding.searchMenu.isVisible) {
                binding.searchMenu.runMenuOut { exitSearchMenu() }
                return
            }
            isShowingSearchResult = false
            binding.searchMenu.invalidate()
            binding.searchMenu.invisible()
            ReadBook.clearSearchResult()
            binding.readView.cancelSelect(true)
        }
    }

    /* 恢复到 全文搜索/进度条跳转前的位置 */
    private fun restoreLastBookProcess() {
        if (confirmRestoreProcess == true) {
            ReadBook.restoreLastBookProgress()
        } else if (confirmRestoreProcess == null) {
            alert(R.string.draw) {
                setMessage(R.string.restore_last_book_process)
                yesButton {
                    confirmRestoreProcess = true
                    ReadBook.restoreLastBookProgress() //恢复启动全文搜索前的进度
                }
                noButton {
                    ReadBook.clearLastBookProgress()
                    confirmRestoreProcess = false
                }
                onCancelled {
                    ReadBook.clearLastBookProgress()
                    confirmRestoreProcess = false
                }
            }
        }
    }

    private fun clearRestoreProcessState() {
        confirmRestoreProcess = null
        ReadBook.clearLastBookProgress()
    }

    override fun showLogin() {
        ReadBook.bookSource?.let {
            startActivity<SourceLoginActivity> {
                putExtra("bookType", BookType.text)
            }
        }
    }

    override fun payAction() {
        val book = ReadBook.book ?: return
        if (book.isLocal) return
        val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
        if (chapter == null) {
            toastOnUi("no chapter")
            return
        }
        alert(R.string.chapter_pay) {
            setMessage(chapter.title)
            yesButton {
                Coroutine.async(lifecycleScope) {
                    val source =
                        ReadBook.bookSource ?: throw NoStackTraceException("no book source")
                    val payAction = source.getContentRule().payAction
                    if (payAction.isNullOrBlank()) {
                        throw NoStackTraceException("no pay action")
                    }
                    val java = SourceLoginJsExtensions(this@ReadBookActivity, source, BookType.text)
                    runScriptWithContext {
                        source.evalJS(payAction) {
                            put("java", java)
                            put("book", book)
                            put("chapter", chapter)
                            put("title", chapter.title)
                            put("baseUrl", chapter.url)
                            put("result", null)
                            put("src", null)
                        }.toString()
                    }
                }.onSuccess(IO) {
                    if (it.isAbsUrl()) {
                        startActivity<WebViewActivity> {
                            val bookSource = ReadBook.bookSource
                            putExtra("title", getString(R.string.chapter_pay))
                            putExtra("url", it)
                            putExtra("sourceOrigin", bookSource?.bookSourceUrl)
                            putExtra("sourceName", bookSource?.bookSourceName)
                            putExtra("sourceType", bookSource?.getSourceType())
                        }
                    } else if (it.isTrue()) {
                        //购买成功后刷新目录
                        ReadBook.book?.let {
                            ReadBook.curTextChapter = null
                            BookHelp.delContent(book, chapter)
                            loadChapterList(book)
                        }
                    }
                }.onError {
                    AppLog.put("执行购买操作出错\n${it.localizedMessage}", it, true)
                }
            }
            noButton()
        }
    }

    /**
     * 点击图片
     */
    private fun executeDirectSourceImageAction(request: TextReaderImageActionRequest) {
        if (directSourceImageJob?.isActive == true) return
        val book = ReadBook.book ?: return
        val source = ReadBook.bookSource ?: return
        val bookUrl = book.bookUrl
        fun ownsSource(): Boolean = ReadBook.book === book && book.bookUrl == bookUrl &&
            ReadBook.bookSource === source && source.bookSourceUrl == request.chapter.sourceImages?.sourceKey &&
            request.session.bookUrl == bookUrl && request.session.sourceRevision == ReadBook.directTextRevision &&
            !request.session.isClosed && !book.isEpub && book.usesDirectReader
        fun isCurrent(): Boolean = ownsSource() && epubDirectSession === request.session &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
            !epubCoreLoading && !epubCoreBoundaryTransition &&
            ReadBook.durChapterIndex == request.chapter.chapterIndex &&
            binding.epubReadView.isCurrentSourceImageAction(request)
        if (!isCurrent() || ParagraphRuleProcessor.isParagraphClick(request.action.click.trimStart())) return
        autoPageStop()
        directSourceImageJob = lifecycleScope.launch {
            try {
                val chapter = withContext(IO) {
                    appDb.bookChapterDao.getChapter(bookUrl, request.chapter.chapterIndex)
                } ?: return@launch
                if (!isCurrent() || chapter.bookUrl != bookUrl ||
                    chapter.url != request.chapter.sourceChapterUrl ||
                    chapter.index != request.chapter.chapterIndex) return@launch
                withContext(IO) {
                    // Capture book/source/chapter before dispatch. Do not bind a
                    // delayed click to ReadBook's newly selected chapter.
                    if (!ownsSource() || ReadBook.durChapterIndex != chapter.index) return@withContext
                    BookHelp.ensureLegacyContentAlias(book, chapter)
                    val java = SourceLoginJsExtensions(this@ReadBookActivity, source, BookType.text)
                    runScriptWithContext {
                        source.evalJS(request.action.click) {
                            put("java", java)
                            put("book", book)
                            put("chapter", chapter)
                            put("result", request.action.source)
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                AppLog.put("执行正文图片 click 出错\n${error.localizedMessage}", error, true)
            }
        }
    }

    private fun evalParagraphRuleClick(click: String?, src: String): Boolean {
        if (!ParagraphRuleProcessor.isParagraphClick(click)) return false
        if (commentBrowserOpening || commentBrowserShowing) return true
        val clickValue = click ?: return false
        commentBrowserOpening = true
        getCommentWebViewSession().prepare(applicationContext)
        Coroutine.async(lifecycleScope, IO) {
            val book = ReadBook.book ?: return@async
            val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
                ?: throw Exception("no find chapter")
            ParagraphRuleProcessor.evalClick(
                book,
                chapter,
                clickValue,
                src,
                paragraphRuleBrowserCallback(),
                coroutineContext
            )
        }.onError {
            AppLog.put("ParagraphRule pclick error: ${it.localizedMessage}", it, true)
        }.onFinally {
            if (!commentBrowserShowing) {
                commentBrowserOpening = false
            }
        }
        return true
    }
    private fun getCommentWebViewSession(): CommentWebViewSession {
        return commentWebViewSession ?: CommentWebViewSession.shared.also { commentWebViewSession = it }
    }

    private fun ensureCurrentChapterCacheForClick(book: Book, chapter: BookChapter) {
        val current = ReadBook.curTextChapter
            ?.takeIf { it.chapter.index == chapter.index }
            ?.getContent()
            ?.takeIf { it.isNotBlank() }
        if (!BookHelp.hasContent(book, chapter) && current != null) {
            BookHelp.saveText(book, chapter, current)
        }
        BookHelp.ensureLegacyContentAlias(book, chapter, current)
    }

    private fun paragraphRuleBrowserCallback(): ParagraphRuleProcessor.BrowserCallback {
        return object : ParagraphRuleProcessor.BrowserCallback {
            override fun showBrowser(
                url: String,
                html: String?,
                preloadJs: String?,
                config: String?,
                sourceKey: String?
            ): Boolean {
                val browserSourceKey = sourceKey ?: ReadBook.bookSource?.getKey() ?: return false
                commentBrowserShowing = true
                runOnUiThread {
                    if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                        commentBrowserShowing = false
                        commentBrowserOpening = false
                        return@runOnUiThread
                    }
                    showDialogFragment(
                        BottomWebViewDialog(
                            browserSourceKey,
                            BookType.text,
                            url,
                            html,
                            preloadJs,
                            config,
                            getCommentWebViewSession(),
                            onDismiss = {
                                commentBrowserShowing = false
                                commentBrowserOpening = false
                            }
                        )
                    )
                }
                return true
            }
        }
    }

    override fun oldClickImg(src: String): Boolean {
        val urlMatcher = paramPattern.matcher(src)
        if (urlMatcher.find()) {
            val urlOptionStr = src.substring(urlMatcher.end())
            val urlOptionMap = GSON.fromJsonObject<Map<String, String>>(urlOptionStr).getOrNull()
            val pclick = urlOptionMap?.get("pclick")
            if (!pclick.isNullOrBlank() && evalParagraphRuleClick(pclick, src)) return true
            val click = urlOptionMap?.get("click")
                ?.takeUnless { ParagraphRuleProcessor.isParagraphClick(it) }
            if (!click.isNullOrBlank()) {
                Coroutine.async(lifecycleScope,IO) {
                    val source = ReadBook.bookSource ?: return@async
                    val java = SourceLoginJsExtensions(this@ReadBookActivity, source, BookType.text)
                    val book = ReadBook.book ?: return@async
                    val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex) ?: throw Exception("no find chapter")
                    ensureCurrentChapterCacheForClick(book, chapter)
                    runScriptWithContext {
                        source.evalJS(click) {
                            put("java", java)
                            put("book", book)
                            put("chapter", chapter)
                            put("result", src)
                        }
                    }
                }.onError {
                    AppLog.put("执行图片链接click键值出错\n${it.localizedMessage}", it, true)
                }
                return true
            }
            val jsStr = urlOptionMap?.get("js") ?: return false
            Coroutine.async(lifecycleScope, IO) {
                val source = ReadBook.bookSource ?: return@async
                val book = ReadBook.book ?: return@async
                val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex) ?: throw Exception("no find chapter")
                val urlNoOption = src.take(urlMatcher.start())
                AnalyzeRule(book, source).apply {
                    setCoroutineContext(coroutineContext)
                    setBaseUrl(chapter.url)
                    setChapter(chapter)
                    evalJS(jsStr, urlNoOption)
                }
            }.onError {
                AppLog.put("执行图片链接js键值出错\n${it.localizedMessage}", it, true)
            }
            return true
        }
        return false
    }

    override fun clickImg(click: String, src: String) {
        if (evalParagraphRuleClick(click, src)) return
        Coroutine.async(lifecycleScope,IO) {
            val source = ReadBook.bookSource ?: return@async
            val java = SourceLoginJsExtensions(this@ReadBookActivity, source, BookType.text)
            val book = ReadBook.book ?: return@async
            val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex) ?: throw Exception("no find chapter")
            ensureCurrentChapterCacheForClick(book, chapter)
            runScriptWithContext {
                source.evalJS(click) {
                    put("java", java)
                    put("book", book)
                    put("chapter", chapter)
                    put("result", src)
                }
            }
        }.onError {
            AppLog.put("执行图片链接click键值出错\n${it.localizedMessage}", it, true)
        }
    }


    /**
     * 朗读按钮
     */
    private fun startDirectEpubReadAloud() {
        val requestedChapterIndex = binding.epubReadView.currentPage()?.chapterIndex ?: return

        fun startFromCurrentDirectPage() {
            val directPage = binding.epubReadView.currentPage()
                ?.takeIf { it.chapterIndex == requestedChapterIndex }
                ?: return
            val textChapter = ReadBook.curTextChapter
                ?.takeIf { it.isCompleted && it.chapter.index == directPage.chapterIndex }
                ?: return
            val lastPage = textChapter.lastPage ?: return
            val chapterLength = (lastPage.chapterPosition + lastPage.text.length)
                .coerceAtLeast(1)
            val chapterPosition = binding.epubReadView.currentDirectTextPosition()
                ?: EpubDirectReadAloudPagePolicy.chapterPositionForPage(
                pageIndex = directPage.pageIndex,
                pageCount = directPage.totalPagesInChapter,
                chapterLength = chapterLength
            )
            val textPageIndex = textChapter.getPageIndexByCharIndex(chapterPosition)
                .coerceAtLeast(0)
            ReadBook.durChapterIndex = directPage.chapterIndex
            ReadBook.durChapterPos = chapterPosition
            ReadBook.readAloud(
                startPos = (chapterPosition - textChapter.getReadLength(textPageIndex))
                    .coerceAtLeast(0)
            )
        }

        val currentTextChapter = ReadBook.curTextChapter
        if (currentTextChapter?.isCompleted == true &&
            currentTextChapter.chapter.index == requestedChapterIndex
        ) {
            startFromCurrentDirectPage()
            return
        }
        ReadBook.openChapter(
            index = requestedChapterIndex,
            durChapterPos = 0,
            upContent = false,
            fromReadAloud = true,
            success = ::startFromCurrentDirectPage
        )
    }

    override fun onClickReadAloud() {
        autoPageStop()
        if (isEpubCoreMode()) {
            when {
                !BaseReadAloudService.isRun -> {
                    ReadAloud.upReadAloudClass()
                    startDirectEpubReadAloud()
                }
                BaseReadAloudService.pause -> ReadAloud.resume(this)
                else -> ReadAloud.pause(this)
            }
            return
        }
        when {
            !BaseReadAloudService.isRun -> {
                ReadAloud.upReadAloudClass()
                val scrollPageAnim = ReadBook.pageAnim() == 3
                if (scrollPageAnim) {
                    val pos = binding.readView.getReadAloudPos()
                    if (pos != null) {
                        val (index, line) = pos
                        if (ReadBook.durChapterIndex != index) {
                            ReadBook.openChapter(index, line.chapterPosition, false) {
                                ReadBook.readAloud(startPos = line.pagePosition)
                            }
                        } else {
                            ReadBook.durChapterPos = line.chapterPosition
                            ReadBook.readAloud(startPos = line.pagePosition)
                        }
                    } else {
                        ReadBook.readAloud()
                    }
                } else {
                    ReadBook.readAloud()
                }
            }

            BaseReadAloudService.pause -> {
                val scrollPageAnim = ReadBook.pageAnim() == 3
                if (scrollPageAnim && pageChanged) {
                    pageChanged = false
                    val pos = binding.readView.getReadAloudPos()
                    if (pos != null) {
                        val (index, line) = pos
                        if (ReadBook.durChapterIndex != index) {
                            ReadBook.openChapter(index, line.chapterPosition, false) {
                                ReadBook.readAloud(startPos = line.pagePosition)
                            }
                        } else {
                            ReadBook.durChapterPos = line.chapterPosition
                            ReadBook.readAloud(startPos = line.pagePosition)
                        }
                    } else {
                        ReadBook.readAloud()
                    }
                } else {
                    ReadAloud.resume(this)
                }
            }

            else -> ReadAloud.pause(this)
        }
    }

    override fun showHelp() {
        showHelp("readMenuHelp")
    }

    /**
     * 长按图片
     */
    @SuppressLint("RtlHardcoded")
    override fun onImageLongPress(
        x: Float,
        y: Float,
        src: String,
        paragraphNum: Int,
        imageIndexInParagraph: Int
    ) {
        val aiImageId = AiImageGalleryManager.imageIdFromUri(src)
        val items = mutableListOf(
            SelectItem(getString(R.string.show), "show"),
            SelectItem(getString(R.string.refresh), "refresh"),
            SelectItem(getString(R.string.action_save), "save"),
            SelectItem(getString(R.string.menu), "menu"),
            SelectItem(getString(R.string.select_folder), "selectFolder")
        )
        if (aiImageId != null) {
            items += SelectItem(getString(R.string.ai_image_delete_insert), "deleteAiImage")
        }
        popupAction.setItems(
            items
        )
        popupAction.onActionClick = {
            when (it) {
                "show" -> showDialogFragment(PhotoDialog(src, isBook = true))
                "refresh" -> viewModel.refreshImage(src)
                "save" -> {
                    val path = ACache.get().getAsString(AppConst.imagePathKey)
                    if (path.isNullOrEmpty()) {
                        selectImageDir.launch {
                            value = src
                        }
                    } else {
                        viewModel.saveImage(src, path.toUri())
                    }
                }

                "menu" -> showActionMenu()
                "selectFolder" -> selectImageDir.launch()
                "deleteAiImage" -> confirmDeleteAiInsertedImage(src, paragraphNum, imageIndexInParagraph)
            }
            popupAction.dismiss()
        }
        val navigationBarHeight =
            if (!ReadBookConfig.hideNavigationBar && navigationBarGravity == Gravity.BOTTOM)
                binding.navigationBar.height else 0
        popupAction.showAtLocation(
            binding.readView, Gravity.BOTTOM or Gravity.LEFT, x.toInt(),
            binding.root.height + navigationBarHeight - y.toInt()
        )
    }

    private fun confirmDeleteAiInsertedImage(
        src: String,
        paragraphNum: Int,
        imageIndexInParagraph: Int
    ) {
        alert(titleResource = R.string.delete, messageResource = R.string.ai_image_delete_insert_confirm) {
            okButton {
                deleteAiInsertedImage(src, paragraphNum, imageIndexInParagraph)
            }
            cancelButton()
        }
    }

    private fun deleteAiInsertedImage(
        src: String,
        paragraphNum: Int,
        imageIndexInParagraph: Int
    ) {
        lifecycleScope.launch {
            val deleted = withContext(IO) {
                removeAiInsertedImageFromCurrentChapter(src, paragraphNum, imageIndexInParagraph)
            }
            if (deleted) {
                ReadBook.clearTextChapter()
                postEvent(EventBus.UP_CONFIG, arrayListOf(5))
                toastOnUi(R.string.ai_image_insert_deleted)
            } else {
                toastOnUi(R.string.ai_image_insert_not_found)
            }
        }
    }

    private fun removeAiInsertedImageFromCurrentChapter(
        src: String,
        paragraphNum: Int,
        imageIndexInParagraph: Int
    ): Boolean {
        val imageId = AiImageGalleryManager.imageIdFromUri(src) ?: return false
        val book = ReadBook.book ?: return false
        val chapter = ReadBook.curTextChapter?.chapter ?: return false
        val rawContent = BookHelp.getContent(book, chapter).orEmpty()
        if (rawContent.isBlank()) return false
        val contentProcessor = ContentProcessor.get(book.name, book.origin)
        val lines = contentProcessor.getContent(book, chapter, rawContent, includeTitle = false)
            .textList
            .toMutableList()
        val targetIndex = currentChapterContentIndex(paragraphNum).takeIf { it in lines.indices }
            ?: return false
        val newLine = removeAiImageTag(lines[targetIndex], imageId, imageIndexInParagraph)
            ?: return false
        lines[targetIndex] = newLine
        BookHelp.saveText(book, chapter, lines.joinToString("\n"))
        return true
    }

    private fun currentChapterContentIndex(paragraphNum: Int): Int {
        val textChapter = ReadBook.curTextChapter ?: return -1
        val paragraphs = textChapter.getParagraphs(pageSplit = false)
        val targetParagraph = paragraphs.firstOrNull { it.realNum == paragraphNum }
            ?: return -1
        return targetParagraph.sourceIndex
    }

    private fun removeAiImageTag(
        line: String,
        imageId: String,
        imageIndexInParagraph: Int
    ): String? {
        val matcher = AppPattern.imgPattern.matcher(line)
        val result = StringBuffer()
        var sameImageIndex = 0
        var removed = false
        while (matcher.find()) {
            val matchedSrc = matcher.group(1)
            if (
                AiImageGalleryManager.imageIdFromUri(matchedSrc) == imageId &&
                sameImageIndex++ == imageIndexInParagraph
            ) {
                matcher.appendReplacement(result, "")
                removed = true
            } else {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0).orEmpty()))
            }
        }
        if (!removed) return null
        matcher.appendTail(result)
        return result.toString()
    }

    /**
     * colorSelectDialog
     */
    override fun onColorSelected(dialogId: Int, color: Int) = ReadBookConfig.durConfig.run {
        when (dialogId) {
            TEXT_COLOR -> {
                setCurTextColor(color)
                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6, 9, 11))
                if (AppConfig.readBarStyleFollowPage) {
                    postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                }
            }

            TEXT_ACCENT_COLOR -> {
                setCurTextAccentColor(color)
                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6, 9, 11))
                if (AppConfig.readBarStyleFollowPage) {
                    postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                }
            }

            BG_COLOR -> {
                setCurBg(0, "#${color.hexString}")
                postEvent(EventBus.UP_CONFIG, arrayListOf(1))
                if (AppConfig.readBarStyleFollowPage) {
                    postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                }
            }

            TIP_COLOR -> {
                ReadTipConfig.tipColor = color
                postEvent(EventBus.TIP_COLOR, "")
                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
            }

            TIP_DIVIDER_COLOR -> {
                ReadTipConfig.tipDividerColor = color
                postEvent(EventBus.TIP_COLOR, "")
                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
            }
        }
    }

    /**
     * colorSelectDialog
     */
    override fun onDialogDismissed(dialogId: Int) = Unit

    override fun onTocRegexDialogResult(tocRegex: String) {
        ReadBook.book?.let {
            it.tocUrl = tocRegex
            loadChapterList(it)
        }
    }

    private fun sureSyncProgress(progress: BookProgress) {
        alert(R.string.get_book_progress) {
            setMessage(R.string.current_progress_exceeds_cloud)
            okButton {
                ReadBook.setProgress(progress)
            }
            noButton()
        }
    }

    override fun openDirectTextChapter(index: Int, characterPosition: Int, success: (() -> Unit)?) {
        val bookUrl = ReadBook.book?.bookUrl ?: return
        lifecycleScope.launch {
            if (ReadBook.book?.bookUrl != bookUrl || !isEpubCoreMode() || ReadBook.book?.isEpub != false) {
                success?.invoke()
                return@launch
            }
            ReadBook.markReadAloudUserNavigation()
            pendingDirectReadAloudProgress = null
            directReadAloudTargetChapterIndex = null
            // An explicit bookmark/cloud/TOC selection replaces an older pending target.
            // Commit the new progress only after the selected document is ready.
            cancelActiveEpubCoreNavigation()
            loadEpubCoreContent(
                targetChapterIndex = index,
                resetPageOffset = true,
                targetFragmentId = "__legado_text_" + characterPosition.coerceAtLeast(0),
                onFinish = success
            )
        }
    }

    /* 进度条跳转到指定章节 */
    private fun openEpubTocChapter(
        index: Int,
        targetFragmentId: String?
    ) {
        ReadBook.saveCurrentBookProgress()
        // The selected index belongs to the persisted chapter list. The Direct
        // provider resolves that complete BookChapter against the current EPUB facade.
        epubDirectLinkRequestSeq++
        requestEpubCoreChapter(
            index = index,
            resetPageOffset = true,
            navigationMode = EpubCoreNavigationMode.ExplicitReplace,
            targetFragmentId = targetFragmentId
        )
    }

    override fun skipToChapter(index: Int) {
        ReadBook.saveCurrentBookProgress() //退出章节跳转恢复此时进度
        if (isEpubCoreMode()) {
            requestEpubCoreChapter(
                index = index,
                resetPageOffset = true,
                navigationMode = EpubCoreNavigationMode.ExplicitReplace
            )
            return
        }
        viewModel.openChapter(index)
    }

    /* 全文搜索跳转 */
    override fun navigateToSearch(searchResult: SearchResult, index: Int) {
        if (isEpubCoreMode()) {
            skipToChapter(searchResult.chapterIndex)
            return
        }
        viewModel.searchResultIndex = index
        skipToSearch(searchResult)
    }

    override fun onMenuShow() {
        epubLoadingWindow?.dismiss()
        epubHostOverlaySettleRunnable?.let(binding.root::removeCallbacks)
        epubHostOverlaySettleRunnable = null
        binding.readAloudPlayerPanel.setReadMenuVisible(true)
        binding.epubReadView.setHostOverlayCaptureBlocked(epubCoreActive)
        if (epubCoreActive) epubDirectAutoPager.pause()
        binding.readMenu.post { syncReadMenuAvoidBounds() }
        binding.readMenu.doOnLayout { syncReadMenuAvoidBounds() }
        if (epubCoreActive) return
        binding.readView.autoPager.pause()
    }

    override fun onMenuHide() {
        binding.readAloudPlayerPanel.setReadMenuAvoidBounds(null)
        if (epubCoreActive) {
            if (!binding.readMenu.isVisible && !binding.searchMenu.isVisible && bottomDialog <= 0) {
                scheduleEpubHostOverlayCaptureRelease()
            }
            return
        }
        binding.readView.autoPager.resume()
    }

    override fun onMenuHidden() {
        // ReadMenu/SearchMenu invoke this callback before their final system-bar update. Keep
        // capture blocked through that update and one actual root pre-draw so PixelCopy cannot
        // cache an Insets transition frame with white top/bottom strips.
        upSystemUiVisibility()
        if (!epubCoreActive) {
            binding.epubReadView.setHostOverlayCaptureBlocked(false)
            resumeEpubDirectAutoPageIfUnblocked()
            return
        }
        scheduleEpubHostOverlayCaptureRelease()
    }

    private fun scheduleEpubHostOverlayCaptureRelease() {
        ViewCompat.requestApplyInsets(binding.root)
        epubHostOverlaySettleRunnable?.let(binding.root::removeCallbacks)
        lateinit var settle: Runnable
        settle = Runnable {
            if (epubHostOverlaySettleRunnable !== settle) return@Runnable
            binding.root.removeCallbacks(settle)
            epubHostOverlaySettleRunnable = null
            if (binding.readMenu.isVisible || binding.searchMenu.isVisible || bottomDialog > 0) {
                return@Runnable
            }
            binding.epubReadView.setHostOverlayCaptureBlocked(false)
            resumeEpubDirectAutoPageIfUnblocked()
        }
        epubHostOverlaySettleRunnable = settle
        binding.root.doOnPreDraw {
            if (epubHostOverlaySettleRunnable === settle) binding.root.postOnAnimation(settle)
        }
        binding.root.postDelayed(settle, HOST_OVERLAY_SETTLE_TIMEOUT_MS)
    }

    private fun resumeEpubDirectAutoPageIfUnblocked() {
        if (epubCoreActive && !binding.readMenu.isVisible &&
            !binding.epubReadView.isSelectionBlockingPageTurn
        ) {
            epubDirectAutoPager.resume()
        }
    }

    private fun syncReadMenuAvoidBounds() {
        val bounds = binding.readMenu.bottomMenuBoundsIn(binding.readAloudPlayerPanel)
        binding.readAloudPlayerPanel.setReadMenuAvoidBounds(bounds)
    }

    override fun epubCorePageCount(): Int {
        return if (epubCoreActive) binding.epubReadView.currentChapterPageCount() else 0
    }

    override fun epubCorePageIndex(): Int {
        return if (epubCoreActive) binding.epubReadView.currentChapterPageIndex() else 0
    }

    override fun isEpubCoreBook(): Boolean {
        return isEpubCoreMode()
    }

    override fun supportsReplaceRules(): Boolean = ReadBook.book?.let {
        ReadMenuButtonConfig.supportsReplaceRules(it.isEpub, it.usesDirectReader)
    } == true

    override fun epubCoreChapterTitle(): String? {
        val book = ReadBook.book?.takeIf { it.usesDirectReader } ?: return null
        val chapterIndex = epubCoreForegroundTarget?.chapterIndex
            ?: epubCoreLoadingChapterIndex
            ?: binding.epubReadView.currentPage()?.chapterIndex
            ?: ReadBook.durChapterIndex
        return appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex)?.title
            ?: book.durChapterTitle?.takeIf { it.isNotBlank() }
    }

    override fun epubCoreChapterUrl(): String? {
        val book = ReadBook.book?.takeIf { it.usesDirectReader } ?: return null
        val chapterIndex = epubCoreForegroundTarget?.chapterIndex
            ?: epubCoreLoadingChapterIndex
            ?: binding.epubReadView.currentPage()?.chapterIndex
            ?: ReadBook.durChapterIndex
        return appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex)?.url
    }

    override fun skipToEpubCorePage(index: Int): Boolean {
        if (!epubCoreActive) return false
        if (binding.epubReadView.isSelectionBlockingPageTurn) return true
        if (epubCoreLoading || epubCorePageCount <= 0) return true
        ReadBook.markReadAloudUserNavigation()
        val pageCount = binding.epubReadView.currentChapterPageCount().coerceAtLeast(1)
        val pageIndex = index.coerceIn(0, pageCount - 1)
        val displayedChapterIndex = binding.epubReadView.currentPage()?.chapterIndex
            ?: ReadBook.durChapterIndex
        val changed = binding.epubReadView.setChapterPageIndex(displayedChapterIndex, pageIndex)
        if (!changed) {
            if (!BaseReadAloudService.isRun) {
                updateDirectStoredPosition(displayedChapterIndex, pageIndex)
                ReadBook.saveRead(true)
            }
            upSeekBarProgress()
        }
        return true
    }

    override fun onLayoutPageCompleted(index: Int, page: TextPage) {
        upSeekBarThrottle.invoke()
        if (!epubCoreActive) {
            binding.readView.onLayoutPageCompleted(index, page)
        }
    }

    /* 全文搜索跳转 */
    private fun skipToSearch(searchResult: SearchResult) {
        if (searchResult.chapterIndex != ReadBook.durChapterIndex) {
            viewModel.openChapter(searchResult.chapterIndex) {
                jumpToPosition(searchResult)
            }
        } else {
            jumpToPosition(searchResult)
        }
    }

    private fun jumpToPosition(searchResult: SearchResult) {
        val curTextChapter = ReadBook.curTextChapter ?: return
        binding.searchMenu.updateSearchInfo()
        val searchResultPositions =
            viewModel.searchResultPositions(curTextChapter, searchResult)
        val (pageIndex, lineIndex, charIndex, addLine, charIndex2) = searchResultPositions
        ReadBook.skipToPage(pageIndex) {
            isSelectingSearchResult = true
            binding.readView.curPage.selectStartMoveIndex(0, lineIndex, charIndex)
            when (addLine) {
                0 -> binding.readView.curPage.selectEndMoveIndex(
                    0,
                    lineIndex,
                    charIndex + searchResultPositions[5] - 1
                )

                1 -> binding.readView.curPage.selectEndMoveIndex(
                    0, lineIndex + 1, charIndex2
                )
                //consider change page, jump to scroll position
                -1 -> binding.readView.curPage.selectEndMoveIndex(1, 0, charIndex2)
            }
            binding.readView.isTextSelected = true
            isSelectingSearchResult = false
        }
    }

    override fun addBookmark() {
        val book = ReadBook.book
        if (book != null && epubCoreActive && binding.epubReadView.isDirectMode) {
            createDirectEpubBookmark()?.let { showDialogFragment(BookmarkDialog(it)) }
                ?: toastOnUi(R.string.create_bookmark_error)
            return
        }
        val page = ReadBook.curTextChapter?.getPage(ReadBook.durPageIndex)
        if (book != null && page != null) {
            val bookmark = book.createBookMark().apply {
                chapterIndex = ReadBook.durChapterIndex
                chapterPos = ReadBook.durChapterPos
                chapterName = page.title
                bookText = page.text.trim()
            }
            showDialogFragment(BookmarkDialog(bookmark))
        }
    }

    private fun createDirectEpubBookmark(text: String = ""): io.legado.app.data.entities.Bookmark? {
        val book = ReadBook.book ?: return null
        val page = binding.epubReadView.currentPage() ?: return null
        return book.createBookMark().apply {
            chapterIndex = page.chapterIndex
            chapterPos = if (book.isEpub) page.pageIndex else binding.epubReadView.currentDirectTextPosition() ?: 0
            chapterName = epubCoreChapterTitle().orEmpty()
            bookText = text.ifBlank { page.text.toString() }.trim()
        }
    }

    override fun changeReplaceRuleState() {
        if (!supportsReplaceRules()) return
        ReadBook.book?.let {
            it.setUseReplaceRule(!it.getUseReplaceRule())
            ReadBook.saveRead(fullUpdate = true)
            menu?.findItem(R.id.menu_enable_replace)?.isChecked = it.getUseReplaceRule()
            viewModel.replaceRuleChanged()
        }
    }

    override fun refreshContent() {
        if (ReadBook.bookSource == null) {
            refreshContentWithoutSource()
        } else {
            ReadBook.book?.let {
                ReadBook.curTextChapter = null
                binding.readView.upContent()
                viewModel.refreshContentDur(it)
            }
        }
    }

    private fun refreshContentWithoutSource() {
        val book = ReadBook.book
        if (book != null && !book.isEpub && isEpubCoreMode()) {
            // A local chapter refresh must also retry failed image resources.
            ReadBook.invalidateDirectTextContent(book.bookUrl)
            epubDirectSession?.close()
            upContent(resetPageOffset = false)
            return
        }
        upContent()
    }

    override fun changeSource() {
        binding.readMenu.runMenuOut()
        ReadBook.book?.let {
            showDialogFragment(ChangeBookSourceDialog(it.name, it.author))
        }
    }

    override fun changeSourceSingle() {
        lifecycleScope.launch {
            val book = ReadBook.book ?: return@launch
            val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex) ?: return@launch
            binding.readMenu.runMenuOut()
            showDialogFragment(
                ChangeChapterSourceDialog(book.name, book.author, chapter.index, chapter.title)
            )
        }
    }

    override fun showRefreshOptions() {
        val labels = listOf(
            getString(R.string.menu_refresh_dur),
            getString(R.string.menu_refresh_after),
            getString(R.string.menu_refresh_all)
        )
        binding.readMenu.runMenuOut()
        selector(R.string.refresh, labels) { _, index ->
            if (ReadBook.bookSource == null) {
                refreshContentWithoutSource()
                return@selector
            }
            ReadBook.book?.let { book ->
                when (index) {
                    0 -> {
                        ReadBook.curTextChapter = null
                        binding.readView.upContent()
                        viewModel.refreshContentDur(book)
                    }
                    1 -> {
                        ReadBook.clearTextChapter()
                        binding.readView.upContent()
                        viewModel.refreshContentAfter(book)
                    }
                    2 -> refreshContentAll(book)
                }
            }
        }
    }

    override fun showCacheDialog() {
        showDownloadDialog()
    }

    override fun editContent() {
        if (isEpubCoreMode()) return
        showDialogFragment(ContentEditDialog())
    }

    override fun showPageAnim() {
        showPageAnimConfig(::applyPageAnimationChange)
    }

    override fun editMenu() {
        startActivity<ReadMenuButtonManageActivity>()
    }

    override fun updateToc() {
        ReadBook.book?.let {
            if (it.isEpub) {
                BookHelp.clearCache(it)
                EpubFile.clear()
            }
            if (it.isMobi) {
                MobiFile.clear()
            }
            loadChapterList(it)
        }
    }

    override fun reverseContent() {
        if (isEpubCoreMode()) return
        ReadBook.book?.let {
            viewModel.reverseContent(it)
        }
    }

    override fun showReSegment() {
        if (isEpubCoreMode()) return
        ReadBook.book?.let {
            it.setReSegment(!it.getReSegment())
            ReadBook.saveRead(fullUpdate = true)
            ReadBook.reloadCurrentContent("re-segment")
        }
    }

    override fun showSameTitleRemoved() {
        ReadBook.book?.let {
            val contentProcessor = ContentProcessor.get(it)
            val textChapter = ReadBook.curTextChapter
            if (textChapter != null
                && !textChapter.sameTitleRemoved
                && !BookHelp.getChapterCacheFileNames(it, textChapter.chapter, "nr")
                    .any(contentProcessor.removeSameTitleCache::contains)
            ) {
                toastOnUi("未找到可移除的重复标题")
            }
        }
        viewModel.reverseRemoveSameTitle()
    }

    override fun showImageStyle() {
        val imgStyles = arrayListOf(
            Book.imgStyleDefault,
            Book.imgStyleFull,
            Book.imgStyleText,
            Book.imgStyleSingle
        )
        selector(R.string.image_style, imgStyles) { _, index ->
            val imageStyle = imgStyles[index]
            ReadBook.book?.setImageStyle(imageStyle)
            if (imageStyle == Book.imgStyleSingle) {
                ReadBook.book?.setPageAnim(0)
                binding.readView.upPageAnim()
            }
            ReadBook.saveRead(fullUpdate = true)
            ReadBook.reloadCurrentContent("image-style")
        }
    }

    override fun showHighlightRuleManage() {
        startActivity<HighlightRuleManageActivity> {
            ReadBook.book?.bookUrl?.let { putExtra("bookUrl", it) }
        }
    }

    override fun showParagraphRuleManage() {
        ReadBook.book?.let {
            startActivity<ParagraphRuleManageActivity> {
                putExtra("bookUrl", it.bookUrl)
            }
        }
    }

    override fun showEffectiveReplaces() {
        if (isEpubCoreMode()) return
        showDialogFragment<EffectiveReplacesDialog>()
    }

    override fun showLog() {
        showDialogFragment<AppLogDialog>()
    }

    override fun showGetProgress() {
        ReadBook.book?.let {
            viewModel.syncBookProgress(it) { progress ->
                sureSyncProgress(progress)
            }
        }
    }

    override fun showCoverProgress() {
        ReadBook.book?.let {
            ReadBook.uploadProgress(true) { toastOnUi(R.string.upload_book_success) }
        }
    }

    private fun startBackupJob() {
        backupJob?.cancel()
        backupJob = lifecycleScope.launch(IO) {
            delay(300000)
            ReadBook.book?.let {
                AppCloudStorage.uploadBookProgress(it)
                ensureActive()
                it.update()
            }
        }
    }

    override fun sureNewProgress(progress: BookProgress) {
        syncDialog?.dismiss()
        syncDialog = alert(R.string.get_book_progress) {
            setMessage(R.string.cloud_progress_exceeds_current)
            okButton {
                ReadBook.setProgress(progress)
            }
            noButton()
        }
    }

    override fun finish() {
        if (isFinishing) return
        val book = ReadBook.book ?: return super.finish()
        if (ReadBook.inBookshelf) {
            callBackBookEnd()
            return super.finish()
        }
        if (!shelfExitRequestGate.tryBegin()) return
        if (!AppConfig.showAddToShelfAlert) {
            callBackBookEnd()
            viewModel.removeFromBookshelf { super.finish() }
        } else {
            val dialog = alert(title = getString(R.string.add_to_bookshelf)) {
                setMessage(getString(R.string.check_add_bookshelf, book.name))
                okButton {
                    ReadBook.book?.removeType(BookType.notShelf)
                    ReadBook.book?.save()
                    SourceCallBack.callBackBook(SourceCallBack.ADD_BOOK_SHELF, ReadBook.bookSource, ReadBook.book)
                    ReadBook.inBookshelf = true
                    setResult(RESULT_OK)
                    callBackBookEnd()
                    super.finish()
                }
                noButton {
                    callBackBookEnd()
                    viewModel.removeFromBookshelf { super.finish() }
                }
            }
            dialog.setOnCancelListener { shelfExitRequestGate.cancel() }
        }
    }

    private fun callBackBookEnd() {
        SourceCallBack.callBackBook(SourceCallBack.END_READ, ReadBook.bookSource, ReadBook.book, ReadBook.curTextChapter?.chapter)
    }

    override fun onDestroy() {
        binding.epubReadView.setListener(null)
        epubLoadingWindow?.dismiss()
        epubLoadingWindow = null
        epubHostOverlaySettleRunnable?.let(binding.root::removeCallbacks)
        epubHostOverlaySettleRunnable = null
        if (!isChangingConfigurations) {
            ReadAloudAppCapsuleHost.updateReadBookPanelActive(false)
        }
        clearRestoreProcessState()
        super.onDestroy()
        epubCoreRequestSeq++
        epubCoreLayoutReadyActionGate.cancel()
        epubCoreLoadJob?.cancel()
        epubCoreLoadJob = null
        cancelEpubCoreLoadTimeout()
        cancelEpubCorePrefetch()
        epubCoreLoading = false
        epubCoreLoadingChapterIndex = null
        epubCoreForegroundTarget = null
        epubCoreCommittedChapterIndex = null
        epubDirectSession?.close()
        binding.epubReadView.destroyDirectMode()
        epubDirectSession = null
        epubDirectOnFinish = null
        epubDirectCallbackRequestSeq = 0L
        val releasedReadSession = ReadBook.unregister(this)
        if (releasedReadSession) {
            EpubCoreProvider.clear()
        }
        tts?.clearTts()
        textActionMenu.dismiss()
        popupAction.dismiss()
        shareNotePreviewOverlay?.dismiss()
        shareNotePreviewOverlay = null
        binding.readView.onDestroy()
        commentWebViewSession?.destroy()
        commentWebViewSession = null
        commentBrowserOpening = false
        commentBrowserShowing = false
        handler.removeCallbacksAndMessages(null) // 清理Handler消息
        if (!ReadBook.inBookshelf && !isChangingConfigurations) {
            viewModel.removeFromBookshelf(null)
        }
    }


    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        commentWebViewSession?.trimMemory(level)
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                    level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                ImageProvider.clear()
                LottieImageBitmapCache.clear()
                binding.epubReadView.trimDirectMemory()
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                ImageProvider.trimMemory()
                LottieImageBitmapCache.trimMemory()
                binding.epubReadView.trimDirectMemory()
            }
        }
    }

    private fun isCurrentReadAloudProgress(progress: ReadAloudProgressState): Boolean {
        val book = ReadBook.book ?: return false
        if (progress.bookUrl.isNotBlank() && progress.bookUrl != book.bookUrl) return false
        if (progress.chapterIndex >= 0) {
            if (progress.chapterIndex != ReadBook.durChapterIndex) return false
            val chapter = ReadBook.curTextChapter
                ?.takeIf { it.chapter.index == progress.chapterIndex }
            if (chapter != null &&
                progress.chapterUrl.isNotBlank() && chapter.chapter.url.isNotBlank() &&
                progress.chapterUrl != chapter.chapter.url
            ) return false
        }
        return true
    }

    override fun observeLiveBus() = binding.run {
        observeEvent<String>(EventBus.TIME_CHANGED) {
            readView.upTime()
            updateEpubReaderChromeData()
        }
        observeEvent<Int>(EventBus.BATTERY_CHANGED) {
            readView.upBattery(it)
            if (it >= 0) epubReaderBatteryLevel = it
            updateEpubReaderChromeData()
        }
        observeEvent<Boolean>(EventBus.MEDIA_BUTTON) {
            if (it) {
                onClickReadAloud()
            } else {
                ReadBook.readAloud(!BaseReadAloudService.pause)
            }
        }
        observeEvent<ArrayList<Int>>(EventBus.UP_CONFIG) {
            handleReadConfigUpdate(it)
        }
        observeEvent<Boolean>(EventBus.LIBRARY_CONTAINER_CHANGED) {
            val bookUrl = ReadBook.book?.bookUrl.orEmpty()
            if (BookCloudEntryModeStore.get(bookUrl) == BookCloudEntryMode.LIBRARY_CHAPTER) {
                libraryCloudSession = null
                refreshLibraryCloudSession(refresh = true, silent = true)
            } else {
                libraryCloudSession = null
                libraryCloudState = LibraryCloudState.DISABLED
                readMenu.updateCloudLibraryState(libraryCloudState)
            }
        }
        observeEvent<Bundle>(EventBus.READ_ALOUD_CONFIG_CHANGED) {
            readAloudPlayerPanel.onReadAloudConfigChanged(
                it.getString(EventBus.READ_ALOUD_CONFIG_SCOPE)
            )
        }
        observeEvent<Int>(EventBus.ALOUD_STATE) {
            val shouldOpenPendingPanel = pendingReadAloudPlayerOpen && it == Status.PLAY
            readAloudPlayerPanel.onAloudState(it, autoExpand = !shouldOpenPendingPanel)
            if (shouldOpenPendingPanel) {
                pendingReadAloudPlayerOpen = false
                readAloudPlayerPanel.openFromBottom(force = true)
            } else if (it == Status.STOP) {
                pendingReadAloudPlayerOpen = false
            }
            if (it == Status.STOP || it == Status.PAUSE) {
                if (isEpubCoreMode()) {
                    if (it == Status.STOP) {
                        persistDirectVisualProgress()
                        pendingDirectReadAloudProgress = null
                        directReadAloudTargetChapterIndex = null
                    } else {
                        // While paused, durChapterPos remains the speech character offset.
                        ReadBook.saveRead(true)
                    }
                    return@observeEvent
                }
                ReadBook.curTextChapter?.let { textChapter ->
                    val page = textChapter.getPageByReadPos(ReadBook.durChapterPos)
                    if (page != null) {
                        page.removePageAloudSpan()
                        readView.upContent(resetPageOffset = false)
                    }
                }
            }
        }
        observeEvent<Int>(EventBus.READ_ALOUD_DS) {
            readAloudPlayerPanel.onTimerChanged(it)
        }
        observeEvent<ReadAloudPlaybackState>(EventBus.READ_ALOUD_PLAYBACK_STATE) {
            readAloudPlayerPanel.onPlaybackState(it)
        }
        observeEvent<AiReadAloudRoleState>(EventBus.AI_READ_ALOUD_ROLE_STATE) {
            readAloudPlayerPanel.onAiRoleState(it)
            if (pendingReadAloudPlayerOpen &&
                it.stage == AiReadAloudRoleState.STAGE_CURRENT &&
                it.bookUrl == ReadBook.book?.bookUrl
            ) {
                pendingReadAloudPlayerOpen = false
                readAloudPlayerPanel.openFromBottom(force = true)
            }
        }
        observeEvent<ReadAloudProgressState>(EventBus.READ_ALOUD_PROGRESS) { progress ->
            if (!isCurrentReadAloudProgress(progress)) return@observeEvent
            val chapterStart = progress.chapterPosition
            readAloudPlayerPanel.onTtsProgress(chapterStart)
            if (isEpubCoreMode()) {
                if (!BaseReadAloudService.isRun) return@observeEvent
                pendingDirectReadAloudProgress = progress
                applyDirectReadAloudProgress(progress)
                return@observeEvent
            }
            if (BaseReadAloudService.isPlay() &&
                !ReadBook.isReadAloudUserNavigationActive() &&
                isCurrentReadAloudProgress(progress)
            ) {
                ReadBook.curTextChapter?.let { textChapter ->
                    val previousPageIndex = ReadBook.durPageIndex
                    ReadBook.durChapterPos = chapterStart
                    val pageIndex = ReadBook.durPageIndex
                    val aloudSpanStart = chapterStart - textChapter.getReadLength(pageIndex)
                    textChapter.getPage(pageIndex)?.upPageAloudSpan(aloudSpanStart)
                    if (pageIndex != previousPageIndex) {
                        upContent()
                    } else {
                        readView.curPage.invalidateContentView()
                    }
                }
            }
        }
        observeEvent<Boolean>(PreferKey.keepLight) {
            upScreenTimeOut()
        }
        observeEvent<Boolean>(PreferKey.textSelectAble) {
            readView.curPage.upSelectAble(it)
        }
        observeEvent<String>(PreferKey.showBrightnessView) {
            readMenu.upBrightnessState()
        }
        observeEvent<List<SearchResult>>(EventBus.SEARCH_RESULT) {
            viewModel.searchResultList = it
        }
        observeEvent<Boolean>(EventBus.UPDATE_READ_ACTION_BAR) {
            readMenu.reset()
        }
        observeEvent<Boolean>(EventBus.READ_MENU_BUTTON_CHANGED) {
            readMenu.reset()
        }
        observeEvent<Boolean>(EventBus.CONTENT_SELECT_MENU_CONFIG_CHANGED) {
            textActionMenu.upMenu()
        }
        observeEvent<Boolean>(EventBus.UP_SEEK_BAR) {
            readMenu.upSeekBar()
        }
        observeEvent<Boolean>(EventBus.REFRESH_BOOK_CONTENT) { //书源js函数触发刷新
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                ReadBook.book?.let {
                    viewModel.refreshContentDur(it)
                }
            }
        }
        observeEvent<Boolean>(EventBus.REFRESH_BOOK_TOC) { //书源js函数触发刷新
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                ReadBook.book?.let {
                    loadChapterList(it)
                }
            }
        }
    }

    private fun upScreenTimeOut() {
        val keepLightPrefer = getPrefString(PreferKey.keepLight)?.toInt() ?: 0
        screenTimeOut = keepLightPrefer * 1000L
        screenOffTimerStart()
    }

    /**
     * 重置黑屏时间
     */
    override fun screenOffTimerStart() {
        handler.post {
            if (screenTimeOut < 0) {
                keepScreenOn(true)
                return@post
            }
            val t = screenTimeOut - sysScreenOffTime
            if (t > 0) {
                keepScreenOn(true)
                handler.removeCallbacks(screenOffRunnable)
                handler.postDelayed(screenOffRunnable, screenTimeOut)
            } else {
                keepScreenOn(false)
            }
        }
    }

    companion object {
        const val RESULT_DELETED = 100
        private const val EPUB_PREPARE_TIMEOUT_MS = 15_000L
        private const val HOST_OVERLAY_SETTLE_TIMEOUT_MS = 500L
        private val shareNoteProgressPercentRegex = Regex("""(\d+(?:[,.]\d+)?)\s*%""")
    }

}
