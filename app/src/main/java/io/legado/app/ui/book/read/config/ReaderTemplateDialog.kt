package io.legado.app.ui.book.read.config

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.constant.PageAnim
import io.legado.app.help.CacheManager
import io.legado.app.help.book.isEpub
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.reader.ReaderAssets
import io.legado.app.help.reader.ReaderTemplateAssetStyle
import io.legado.app.model.ReadBook
import io.legado.app.model.localBook.epubcore.template.EpubReaderTemplate
import io.legado.app.model.localBook.epubcore.template.EpubReaderTemplatePackages
import io.legado.app.model.localBook.epubcore.template.EpubReaderTemplateStore
import io.legado.app.model.localBook.epubcore.template.ReaderTemplateOperationQueue
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.widget.compose.AppDialogStyle
import io.legado.app.ui.widget.compose.AppListSpacing
import io.legado.app.ui.widget.compose.AppManagementMenuAction
import io.legado.app.ui.widget.compose.AppManagementMoreActionButton
import io.legado.app.ui.widget.compose.AppManagementPalette
import io.legado.app.ui.widget.compose.AppPackageManageActionButton
import io.legado.app.ui.widget.compose.AppPackageManageItemCard
import io.legado.app.ui.widget.compose.AppRuleTextField
import io.legado.app.ui.widget.compose.LegadoMiuixActionButton
import io.legado.app.ui.widget.compose.rememberAppManagementPalette
import io.legado.app.ui.widget.compose.showComposeConfirmDialog
import io.legado.app.ui.widget.compose.toMiuixPalette
import io.legado.app.utils.inputStream
import io.legado.app.utils.postEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.legado.app.ui.widget.compose.LegadoCouiTheme
import java.io.File

class ReaderTemplateDialog : ReaderBottomSheetComposeDialogFragment() {
    override val maxSheetHeightFraction = .9f
    private val model by viewModels<ReaderTemplateViewModel>()
    private val resourcePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val draftId = model.resourceDraftId
        val kind = model.resourceKind
        model.resourceDraftId = null
        model.resourceKind = null
        if (result.resultCode == RESULT_OK && kind != null && draftId != null) {
            val id = result.data?.getStringExtra("assetId") ?: return@registerForActivityResult
            val message = getString(R.string.reader_assets_template_done)
            model.perform(enqueue = true) {
                withContext(Dispatchers.IO) {
                    if (id.isNotEmpty()) require(ReaderAssets.store.find(id)?.kind == kind && ReaderAssets.store.file(id) != null) { "素材已丢失，请重新导入" }
                }
                model.draft?.takeIf { it.id == draftId }?.let { draft ->
                    model.draft = draft.copy(css = if (kind == "font") ReaderTemplateAssetStyle.font(draft.css, id.ifEmpty { null })
                        else ReaderTemplateAssetStyle.background(draft.css, id.ifEmpty { null }))
                    model.message = message
                }
            }
        }
    }

    /** The host connects this to the same pagination runtime used for reading. */
    interface PreviewHost {
        fun previewReaderTemplate(template: EpubReaderTemplate)
    }

    private val importFile = registerForActivityResult(HandleFileContract()) { result ->
        val uri = result.uri ?: return@registerForActivityResult
        val context = requireContext().applicationContext
        val importedMessage = getString(R.string.reader_template_imported)
        model.perform(enqueue = true) {
            // Complete the commit and notify the reader together, even if this sheet is dismissed.
            withContext(NonCancellable) {
                val previousId = ReadBookConfig.config.readerTemplateId
                withContext(Dispatchers.IO) {
                    uri.inputStream(context).getOrThrow().use(EpubReaderTemplatePackages::importPackage)
                }
                notifySelectionChange(previousId)
                model.refresh()
                model.message = importedMessage
            }
        }
    }

    private val exportFile = registerForActivityResult(HandleFileContract()) { result ->
        model.releaseExportFile()
        if (result.uri != null) {
            model.clearMessage()
            model.message = getString(R.string.export_success)
        }
    }

    private val codeEditor = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val field = model.editingField
        model.editingField = null
        model.editorCacheKey?.let(CacheManager::deleteMemory)
        model.editorCacheKey = null
        if (result.resultCode == RESULT_OK && field != null) {
            val data = result.data
            val resultKey = data?.getStringExtra("cacheKey")
            val text = if (resultKey != null) {
                (CacheManager.getFromMemory(resultKey) as? String).also { CacheManager.deleteMemory(resultKey) }
            } else data?.getStringExtra("text")
            if (text != null) model.updateCode(field, text)
            else if (data?.hasExtra("cacheKey") == true) model.message = getString(R.string.reader_template_editor_unavailable)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model.load()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { LegadoCouiTheme { this@ReaderTemplateDialog.ReaderTemplateContent() } }
        }

    @Composable
    private fun ReaderTemplateContent() {
        val palette = rememberAppManagementPalette()
        ReaderBottomSheetFrame(maxHeightFraction = maxSheetHeightFraction) { style ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ReaderSheetHeader(
                    title = stringResource(R.string.reader_template_title),
                    style = style,
                    trailing = {
                        AppManagementMoreActionButton(
                            actionsProvider = ::libraryActions,
                            palette = palette,
                            contentDescription = stringResource(R.string.more)
                        )
                    }
                )
                val draft = model.draft
                if (draft == null) {
                    LibraryContent(palette, style, Modifier.weight(1f, fill = false))
                } else {
                    key(draft.id) {
                        EditorContent(style, draft, Modifier.weight(1f, fill = false))
                    }
                }
                if (model.busy) Text(stringResource(R.string.reader_template_busy), color = style.secondaryText, fontSize = 13.sp)
                model.message?.let {
                    Text(it, color = if (model.failed) style.danger else style.secondaryText, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    @Composable
    private fun LibraryContent(palette: AppManagementPalette, style: AppDialogStyle, modifier: Modifier) {
        // Keep a single bounded lazy list in the reader sheet, as in the other package managers.
        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(AppListSpacing.Normal)
        ) {
            if (model.templates.any { it.id == model.appliedId }) {
                item(key = "page-animation") {
                    ReaderSectionCard(style = style, title = stringResource(R.string.reader_template_page_animation)) {
                        Text(
                            text = model.templates.first { it.id == model.appliedId }.name,
                            color = style.secondaryText,
                            fontSize = 12.sp
                        )
                        if (!model.templates.first { it.id == model.appliedId }.isScrolling) ReaderSegmentedOptions(
                            options = templatePageAnimationOptions(),
                            selectedValue = (model.pageAnimation ?: -1).toString(),
                            style = style,
                            scrollable = true,
                            pillStyle = true
                        ) { value ->
                            if (!model.busy) changePageAnimation(value.toIntOrNull()?.takeIf { it >= 0 })
                        }
                        Text(
                            text = stringResource(if (model.templates.first { it.id == model.appliedId }.isScrolling)
                                R.string.reader_template_scroll_locked else R.string.reader_template_page_animation_hint),
                            color = style.secondaryText,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            item(key = "actions") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppPackageManageActionButton(
                        text = stringResource(R.string.reader_template_import),
                        palette = palette.miuix,
                        modifier = Modifier.weight(1f),
                        onClick = ::openImport
                    )
                    AppPackageManageActionButton(
                        text = stringResource(R.string.reader_template_backup),
                        palette = palette.miuix,
                        modifier = Modifier.weight(1f),
                        onClick = { exportPackage(null) }
                    )
                }
            }
            items(model.templates, key = { "template:" + it.id }) { template ->
                val builtIn = EpubReaderTemplateStore.isBuiltIn(template.id)
                AppPackageManageItemCard(
                    title = template.name,
                    info = template.description,
                    isActive = template.id == model.appliedId,
                    canEdit = true,
                    applyText = stringResource(if (template.id == model.appliedId) R.string.reader_template_selected else R.string.reader_template_apply),
                    editText = stringResource(if (builtIn) R.string.reader_template_copy_edit else R.string.reader_template_edit),
                    moreActions = templateActions(template),
                    palette = palette,
                    onApply = { applyTemplate(template.id) },
                    onEdit = { editTemplate(template, asCopy = builtIn) }
                )
            }
        }
    }

    @Composable
    private fun templatePageAnimationOptions(): List<ReaderOption> = listOf(
        ReaderOption("-1", stringResource(R.string.reader_template_page_animation_follow)),
        ReaderOption(PageAnim.coverPageAnim.toString(), stringResource(R.string.page_anim_cover)),
        ReaderOption(PageAnim.linkedCoverPageAnim.toString(), stringResource(R.string.page_anim_linked_cover)),
        ReaderOption(PageAnim.slidePageAnim.toString(), stringResource(R.string.page_anim_slide)),
        ReaderOption(PageAnim.simulationPageAnim.toString(), stringResource(R.string.page_anim_simulation)),
        ReaderOption(PageAnim.scrollPageAnim.toString(), stringResource(R.string.page_anim_scroll)),
        ReaderOption(PageAnim.noAnim.toString(), stringResource(R.string.page_anim_none))
    )

    private fun changePageAnimation(animation: Int?) {
        val id = model.appliedId
        model.perform {
            val previousPageAnim = ReadBook.pageAnim()
            withContext(Dispatchers.IO) { EpubReaderTemplateStore.savePageAnimation(id, animation) }
            model.pageAnimation = animation
            if (ReadBook.usesPageTemplate() && ReadBookConfig.config.readerTemplateId == id) {
                (activity as? ReadBookActivity)?.applyPageAnimationChange(previousPageAnim)
            }
        }
    }

    @Composable
    private fun EditorContent(style: AppDialogStyle, draft: EpubReaderTemplate, modifier: Modifier) {
        Column(
            modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Field(draft.name, R.string.reader_template_name, style, singleLine = true) { model.draft = draft.copy(name = it) }
            Field(draft.description, R.string.reader_template_description, style) { model.draft = draft.copy(description = it) }
            ReaderSectionCard(style = style, title = stringResource(R.string.reader_template_type)) {
                ReaderSegmentedOptions(
                    options = listOf(
                        ReaderOption(EpubReaderTemplate.TYPE_PAGED, stringResource(R.string.reader_template_type_paged)),
                        ReaderOption(EpubReaderTemplate.TYPE_SCROLL, stringResource(R.string.reader_template_type_scroll))
                    ),
                    selectedValue = draft.type,
                    style = style,
                    pillStyle = true
                ) { type -> if (!model.busy) model.changeType(type) }
                if (draft.isScrolling) Text(
                    text = stringResource(R.string.reader_template_scroll_locked),
                    color = style.secondaryText,
                    fontSize = 12.sp
                )
            }
            TemplateCodeField.entries.filter { field ->
                when (field) {
                    TemplateCodeField.FIRST, TemplateCodeField.OTHER -> !draft.isScrolling
                    TemplateCodeField.SCROLL -> draft.isScrolling
                    else -> true
                }
            }.forEach { field ->
                Action(field.label, style) { openCodeEditor(field) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action(R.string.reader_assets_font_select, style, Modifier.weight(1f)) { openResources("font") }
                Action(R.string.reader_assets_image_select, style, Modifier.weight(1f)) { openResources("image") }
            }
            Action(R.string.reader_assets_title, style) { openResources(null) }
            Action(R.string.reader_template_preview, style) { model.draft?.let(::preview) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action(R.string.reader_template_save, style, Modifier.weight(1f)) { saveDraft(false) }
                Action(R.string.reader_template_save_apply, style, Modifier.weight(1f), primary = true) { saveDraft(true) }
            }
            Action(R.string.reader_template_cancel_edit, style, onClick = ::cancelEdit)
        }
    }

    @Composable
    private fun Action(label: Int, style: AppDialogStyle, modifier: Modifier = Modifier, primary: Boolean = false, onClick: () -> Unit) {
        LegadoMiuixActionButton(
            text = stringResource(label), palette = style.toMiuixPalette(), primary = primary,
            modifier = modifier.fillMaxWidth(), onClick = { if (!model.busy) onClick() }
        )
    }

    @Composable
    private fun Field(value: String, label: Int, style: AppDialogStyle, singleLine: Boolean = false, minLines: Int = 2, onChange: (String) -> Unit) {
        var fieldValue by remember { mutableStateOf(TextFieldValue(value)) }
        LaunchedEffect(value) {
            if (fieldValue.text != value) fieldValue = TextFieldValue(value)
        }
        AppRuleTextField(
            value = fieldValue,
            onValueChange = {
                if (!model.busy) {
                    fieldValue = it
                    onChange(it.text)
                }
            },
            label = stringResource(label),
            singleLine = singleLine,
            minLines = minLines,
            maxLines = 4,
            style = style
        )
    }

    private fun templateActions(template: EpubReaderTemplate): List<AppManagementMenuAction> = buildList {
        add(AppManagementMenuAction(getString(R.string.reader_template_preview), enabled = !model.busy) { preview(template) })
        if (!EpubReaderTemplateStore.isBuiltIn(template.id)) {
            add(AppManagementMenuAction(getString(R.string.reader_template_copy_edit), enabled = !model.busy) { editTemplate(template, asCopy = true) })
        }
        add(AppManagementMenuAction(getString(R.string.reader_template_export), enabled = !model.busy) { exportPackage(template) })
        add(AppManagementMenuAction(getString(R.string.delete), enabled = !model.busy, danger = true) { confirmDelete(template) })
    }

    private fun libraryActions(): List<AppManagementMenuAction> = buildList {
        add(AppManagementMenuAction(getString(R.string.reader_assets_title), enabled = !model.busy) { openResources(null) })
        if (model.hasHiddenBuiltIns && model.draft == null) {
            add(AppManagementMenuAction(getString(R.string.reader_template_restore_builtins), enabled = !model.busy) { restoreBuiltIns() })
        }
        add(AppManagementMenuAction(getString(R.string.reader_template_help)) { showHelp() })
    }

    private fun openResources(kind: String?) {
        if (model.busy) return
        if (kind == null) {
            startActivity(Intent(requireContext(), ReaderAssetManageActivity::class.java))
            return
        }
        val draft = model.draft ?: return
        model.resourceDraftId = draft.id
        model.resourceKind = kind
        resourcePicker.launch(Intent(requireContext(), ReaderAssetManageActivity::class.java)
            .putExtra("pickKind", kind).putExtra("selectedId", ReaderTemplateAssetStyle.selected(draft.css,
                if (kind == "font") "font" else "background")))
    }

    private fun editTemplate(template: EpubReaderTemplate, asCopy: Boolean) {
        if (model.busy) return
        model.draft = if (asCopy) EpubReaderTemplateStore.copyOf(template) else template
        model.clearMessage()
    }

    private fun cancelEdit() {
        if (model.busy) return
        model.draft = null
        model.clearMessage()
    }

    private fun openImport() {
        if (model.busy) return
        runCatching {
            importFile.launch {
                mode = HandleFileContract.FILE
                title = getString(R.string.reader_template_import_title)
                allowExtensions = arrayOf("zip", "json")
            }
        }.onFailure(model::showError)
    }

    private fun exportPackage(template: EpubReaderTemplate?) {
        if (model.busy) return
        val cacheDir = requireContext().cacheDir
        val name = if (template == null) getString(R.string.reader_template_backup_file_name) else {
            template.name.trim().ifBlank { "reader-template" }
                .replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_").take(80) + ".zip"
        }
        model.perform {
            var prepared: File? = null
            try {
                val file = withContext(Dispatchers.IO) {
                    File.createTempFile("reader-template-export-", ".zip", cacheDir).also {
                        prepared = it
                        it.outputStream().use { output ->
                            if (template == null) EpubReaderTemplatePackages.exportBackup(output)
                            else EpubReaderTemplatePackages.exportTemplate(template.id, output)
                        }
                    }
                }
                if (isAdded) {
                    model.pendingExportFile = file
                    exportFile.launch {
                        mode = HandleFileContract.EXPORT
                        showUploadUrl = false
                        fileData = HandleFileContract.FileData(name, file, EpubReaderTemplatePackages.mimeType)
                    }
                    prepared = null
                }
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { prepared?.delete() }
            }
        }
    }

    private fun confirmDelete(template: EpubReaderTemplate) {
        if (model.busy) return
        val builtIn = EpubReaderTemplateStore.isBuiltIn(template.id)
        showComposeConfirmDialog(
            title = getString(R.string.delete),
            message = getString(if (builtIn) R.string.reader_template_delete_builtin else R.string.reader_template_delete_custom, template.name),
            positiveText = getString(R.string.delete),
            dangerPositive = true,
            onPositive = {
                val deletedMessage = getString(R.string.reader_template_deleted)
                model.perform {
                    withContext(NonCancellable) {
                        val previousId = ReadBookConfig.config.readerTemplateId
                        withContext(Dispatchers.IO) { EpubReaderTemplateStore.delete(template.id) }
                        notifySelectionChange(previousId)
                        model.refresh()
                        model.message = deletedMessage
                    }
                }
            }
        )
    }

    private fun restoreBuiltIns() {
        val restoredMessage = getString(R.string.reader_template_restored)
        model.perform {
            withContext(NonCancellable) {
                withContext(Dispatchers.IO) { EpubReaderTemplateStore.restoreBuiltIns() }
                model.refresh()
                model.message = restoredMessage
            }
        }
    }

    private fun notifySelectionChange(previousId: String) {
        if (previousId != ReadBookConfig.config.readerTemplateId) {
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
    }

    private fun showHelp() {
        showComposeConfirmDialog(
            title = getString(R.string.reader_template_help),
            message = listOf(
                R.string.reader_template_scope,
                R.string.reader_template_backup_help,
                R.string.reader_template_code_note,
                R.string.reader_template_author_help,
                R.string.reader_template_scroll_author_help,
                R.string.reader_template_javascript_help
            ).joinToString("\n\n") { getString(it) } + "\n\n" + getString(R.string.reader_assets_hint) +
                "\n可在编辑页直接选择字体和背景，或从素材库复制图片地址、字体 CSS 放入页面代码。",
            showNegative = false,
            messageInContent = true,
            onPositive = {}
        )
    }

    private fun openCodeEditor(field: TemplateCodeField) {
        val draft = model.draft ?: return
        val key = "reader_template_edit_" + System.nanoTime()
        val source = field.read(draft)
        CacheManager.putMemory(key, source)
        model.editorCacheKey = key
        model.editingField = field
        runCatching {
            codeEditor.launch(Intent(requireContext(), CodeEditActivity::class.java).apply {
                putExtra("cacheKey", key)
                putExtra("writable", true)
                putExtra("title", getString(field.label))
                putExtra("languageName", field.language)
            })
        }.onFailure {
            CacheManager.deleteMemory(key)
            model.editorCacheKey = null
            model.editingField = null
            model.showError(it)
        }
    }

    private fun saveDraft(apply: Boolean) {
        val draft = model.draft ?: return
        val savedMessage = getString(R.string.reader_template_saved)
        val scopeError = getString(R.string.reader_template_scope_error)
        model.perform {
            withContext(NonCancellable) {
                val saved = withContext(Dispatchers.IO) { EpubReaderTemplateStore.save(draft) }
                model.refresh()
                model.draft = null
                model.message = savedMessage
                if (apply) applyTemplateNow(saved.id, scopeError)
                else if (saved.id == ReadBookConfig.config.readerTemplateId && ReadBook.usesPageTemplate()) {
                    postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
                }
            }
        }
    }

    private fun applyTemplate(id: String) {
        val scopeError = getString(R.string.reader_template_scope_error)
        model.perform { applyTemplateNow(id, scopeError) }
    }

    private suspend fun applyTemplateNow(id: String, scopeError: String) {
        require(ReadBookConfig.usingEpubLayout && ReadBook.book?.isEpub == false) { scopeError }
        require(id.isNotEmpty()) { scopeError }
        withContext(NonCancellable) {
            withContext(Dispatchers.IO) { EpubReaderTemplateStore.saveSelection(id) }
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
            if (isAdded) dismissAllowingStateLoss()
        }
    }

    private fun preview(template: EpubReaderTemplate) {
        if (model.busy) return
        runCatching {
            val errors = template.validate()
            require(errors.isEmpty()) { errors.joinToString("\n") }
            val host = (parentFragment as? PreviewHost) ?: (activity as? PreviewHost)
            requireNotNull(host) { getString(R.string.reader_template_preview_unavailable) }.previewReaderTemplate(template)
        }.onFailure(model::showError)
    }

}

internal enum class TemplateCodeField(val label: Int, val language: String) {
    FIRST(R.string.reader_template_first_html, "text.html.basic"),
    OTHER(R.string.reader_template_other_html, "text.html.basic"),
    SCROLL(R.string.reader_template_scroll_html, "text.html.basic"),
    // The app bundles no CSS TextMate grammar. This registered lexical mode keeps the editor usable.
    CSS(R.string.reader_template_css, "source.js"),
    JAVASCRIPT(R.string.reader_template_javascript, "source.js");

    fun read(template: EpubReaderTemplate): String = when (this) {
        FIRST -> template.firstPageHtml
        OTHER -> template.otherPageHtml
        SCROLL -> template.scrollHtml
        CSS -> template.css
        JAVASCRIPT -> template.javascript
    }
}

internal class ReaderTemplateViewModel : ViewModel() {
    var templates by mutableStateOf<List<EpubReaderTemplate>>(emptyList())
    var appliedId by mutableStateOf(ReadBookConfig.config.readerTemplateId)
    var pageAnimation by mutableStateOf<Int?>(null)
    var hasHiddenBuiltIns by mutableStateOf(false)
    var draft by mutableStateOf<EpubReaderTemplate?>(null)
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var failed by mutableStateOf(false)
    var editingField: TemplateCodeField? = null
    var editorCacheKey: String? = null
    var pendingExportFile: File? = null
    var resourceDraftId: String? = null
    var resourceKind: String? = null
    private var loaded = false
    private val operations by lazy {
        ReaderTemplateOperationQueue(
            scope = viewModelScope,
            onBusyChanged = { busy = it },
            onStart = ::clearMessage,
            onError = ::showError
        )
    }

    fun load() {
        if (!loaded) { loaded = true; perform { refresh() } }
    }

    suspend fun refresh() {
        withContext(NonCancellable) {
            val previousId = ReadBookConfig.config.readerTemplateId
            val library = withContext(Dispatchers.IO) {
                if (ReadBook.usesPageTemplate()) EpubReaderTemplateStore.ensureReaderSelection()
                EpubReaderTemplateStore.list() to EpubReaderTemplateStore.hasHiddenBuiltIns()
            }
            appliedId = ReadBookConfig.config.readerTemplateId
            pageAnimation = EpubReaderTemplateStore.pageAnimation(appliedId)
            if (previousId != appliedId) postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
            templates = library.first
            hasHiddenBuiltIns = library.second
        }
    }
    fun clearMessage() { message = null; failed = false }
    fun showError(error: Throwable) { message = error.localizedMessage ?: error.javaClass.simpleName; failed = true }

    fun perform(enqueue: Boolean = false, action: suspend () -> Unit) {
        operations.submit(enqueue, action)
    }

    fun updateCode(field: TemplateCodeField, source: String) {
        val current = draft ?: return
        draft = when (field) {
            TemplateCodeField.FIRST -> current.copy(firstPageHtml = source)
            TemplateCodeField.OTHER -> current.copy(otherPageHtml = source)
            TemplateCodeField.SCROLL -> current.copy(scrollHtml = source)
            TemplateCodeField.CSS -> current.copy(css = source)
            TemplateCodeField.JAVASCRIPT -> current.copy(javascript = source)
        }
    }

    fun changeType(type: String) {
        val current = draft ?: return
        if (type == current.type) return
        draft = current.copy(
            type = type,
            schemaVersion = if (type == EpubReaderTemplate.TYPE_SCROLL) EpubReaderTemplate.SCROLL_SCHEMA_VERSION else EpubReaderTemplate.SCHEMA_VERSION,
            scrollHtml = current.scrollHtml.ifBlank { "<main data-reader-flow=\"body\"></main>" },
            firstPageHtml = current.firstPageHtml.ifBlank { "<main data-reader-flow=\"body\" style=\"height:100%\"></main>" },
            otherPageHtml = current.otherPageHtml.ifBlank { "<main data-reader-flow=\"body\" style=\"height:100%\"></main>" }
        )
    }

    fun releaseExportFile() {
        val file = pendingExportFile ?: return
        pendingExportFile = null
        viewModelScope.launch(NonCancellable + Dispatchers.IO) { file.delete() }
    }

    override fun onCleared() {
        editorCacheKey?.let(CacheManager::deleteMemory)
        pendingExportFile?.delete()
        super.onCleared()
    }
}
