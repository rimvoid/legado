package io.legado.app.ui.about

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.constant.AppConst.appInfo
import io.legado.app.constant.AppLog
import io.legado.app.help.CrashHandler
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.update.AppUpdate
import io.legado.app.help.update.AppUpdateConfig
import io.legado.app.ui.config.compose.ComposeSettingFragment
import io.legado.app.ui.config.compose.SettingActionSpec
import io.legado.app.ui.config.compose.SettingPageSpec
import io.legado.app.ui.config.compose.SettingSectionSpec
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.ui.widget.compose.showComposeTextFormDialogWithChecks
import io.legado.app.utils.FileDoc
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.createFileIfNotExist
import io.legado.app.utils.createFolderIfNotExist
import io.legado.app.utils.delete
import io.legado.app.utils.externalCache
import io.legado.app.utils.find
import io.legado.app.utils.list
import io.legado.app.utils.openInputStream
import io.legado.app.utils.openOutputStream
import io.legado.app.utils.openUrl
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.delay
import splitties.init.appCtx
import java.io.File

class AboutFragment : ComposeSettingFragment() {

    private val waitDialog by lazy {
        WaitDialog(requireContext())
    }

    override val titleRes: Int = R.string.about

    override val autoOpenTargetItem: Boolean = false

    override fun buildPageSpec(): SettingPageSpec {
        return SettingPageSpec(
            titleRes = titleRes,
            sections = listOf(
                SettingSectionSpec(
                    items = listOfNotNull(
                        action(
                            key = KEY_CONTRIBUTORS,
                            title = getString(R.string.contributors),
                            summary = getString(R.string.contributors_summary_sigma)
                        ) {
                            openUrl(R.string.contributors_url)
                        },
                        action(
                            key = KEY_UPDATE_LOG,
                            title = getString(R.string.update_log),
                            summary = "${getString(R.string.version)} ${appInfo.versionName}"
                        ) {
                            showMdFile(getString(R.string.update_log), "updateLog.md")
                        },
                        if (AppUpdate.checkUpdateEnabled) {
                            action(
                                key = KEY_CHECK_UPDATE,
                                title = getString(R.string.check_update)
                            ) {
                                checkUpdate()
                            }
                        } else {
                            null
                        },
                        action(
                            key = KEY_INTERNAL_BETA,
                            title = "内测版",
                            summary = internalBetaSummary()
                        ) {
                            showInternalBetaDialog()
                        },
                        action(
                            key = KEY_UPDATE_ACCELERATION,
                            title = getString(R.string.update_acceleration_manage),
                            summary = AppUpdateConfig.summary(requireContext())
                        ) {
                            showUpdateAccelerationManage()
                        }
                    )
                ),
                SettingSectionSpec(
                    title = getString(R.string.other),
                    items = listOf(
                        action(
                            key = KEY_CRASH_LOG,
                            title = getString(R.string.crash_log)
                        ) {
                            showDialogFragment<CrashLogsDialog>()
                        },
                        action(
                            key = KEY_SAVE_LOG,
                            title = getString(R.string.save_log)
                        ) {
                            saveLog()
                        },
                        action(
                            key = KEY_CREATE_HEAP_DUMP,
                            title = getString(R.string.create_heap_dump)
                        ) {
                            createHeapDump()
                        },
                        action(
                            key = KEY_PRIVACY_POLICY,
                            title = getString(R.string.privacy_policy)
                        ) {
                            showMdFile(getString(R.string.privacy_policy), "privacyPolicy.md")
                        },
                        action(
                            key = KEY_LICENSE,
                            title = getString(R.string.license)
                        ) {
                            showMdFile(getString(R.string.license), "LICENSE.md")
                        },
                        action(
                            key = KEY_DISCLAIMER,
                            title = getString(R.string.disclaimer)
                        ) {
                            showMdFile(getString(R.string.disclaimer), "disclaimer.md")
                        }
                    )
                )
            )
        )
    }

    private fun action(
        key: String,
        title: CharSequence,
        summary: CharSequence? = null,
        onClick: () -> Unit
    ): SettingActionSpec {
        return SettingActionSpec(
            key = key,
            title = title,
            summary = summary,
            onClick = onClick
        )
    }

    private fun showUpdateAccelerationManage() {
        UpdateAcceleratorDialog.show(this) {
            refreshSettings()
        }
    }

    private fun internalBetaSummary(): String {
        val enabled = AppUpdateConfig.internalBetaConfigured
        val url = AppUpdateConfig.internalBetaUrl.orEmpty()
        return if (enabled) {
            "已启用 · $url"
        } else {
            "未启用"
        }
    }

    private fun showInternalBetaDialog() {
        showComposeTextFormDialogWithChecks(
            title = "内测版更新",
            labels = listOf("下载密钥", "下载地址"),
            initialValues = listOf(
                AppUpdateConfig.internalBetaKey.orEmpty(),
                AppUpdateConfig.internalBetaUrl.orEmpty()
            ),
            passwordFields = setOf(0),
            checkboxLabels = listOf("启用内测版更新"),
            checkedIndices = if (AppUpdateConfig.internalBetaEnabled) setOf(0) else emptySet(),
            message = "启用后，检测更新会优先比较正式版和内测版。正式版版本高于内测版时更新正式版，否则在没有更高正式版时更新内测版。",
            validateInput = { values ->
                val key = values.getOrNull(0).orEmpty().trim()
                val url = values.getOrNull(1).orEmpty().trim()
                val hasAny = key.isNotBlank() || url.isNotBlank()
                !hasAny || (key.isNotBlank() && (url.startsWith("https://") || url.startsWith("http://")))
            },
            onPositive = { values, checked ->
                val key = values.getOrNull(0).orEmpty().trim()
                val url = values.getOrNull(1).orEmpty().trim()
                val valid = key.isNotBlank() && (url.startsWith("https://") || url.startsWith("http://"))
                AppUpdateConfig.internalBetaKey = key
                AppUpdateConfig.internalBetaUrl = url
                AppUpdateConfig.internalBetaEnabled = checked.getOrNull(0) == true && valid
                refreshSettings()
            }
        )
    }

    @Suppress("SameParameterValue")
    private fun openUrl(@StringRes addressID: Int) {
        requireContext().openUrl(getString(addressID))
    }

    /**
     * 显示md文件
     */
    private fun showMdFile(title: String, fileName: String) {
        val mdText = String(requireContext().assets.open(fileName).readBytes())
        showDialogFragment(TextDialog(title, mdText, TextDialog.Mode.MD))
    }

    /**
     * 检测更新
     */
    private fun checkUpdate() {
        waitDialog.show()
        AppUpdate.preferredUpdate.run {
            check(lifecycleScope)
                .onSuccess {
                    showDialogFragment(
                        UpdateDialog(it)
                    )
                }.onError {
                    showDialogFragment(
                        TextDialog(
                            getString(R.string.check_update),
                            if (AppUpdate.isLatestVersionError(it)) {
                                getString(R.string.update_no_new_version)
                            } else {
                                it.localizedMessage ?: getString(R.string.check_update)
                            },
                            TextDialog.Mode.TEXT
                        )
                    )
                }.onFinally {
                    waitDialog.dismiss()
                }
        }
    }


    /**
     * 加入qq群
     */
    private fun joinQQGroup(key: String): Boolean {
        val intent = Intent()
        intent.data =
            Uri.parse("mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26k%3D$key")
        // 此Flag可根据具体产品需要自定义，如设置，则在加群界面按返回，返回手Q主界面，不设置，按返回会返回到呼起产品界面
        // intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        kotlin.runCatching {
            startActivity(intent)
            return true
        }.onFailure {
            toastOnUi("添加失败,请手动添加")
        }
        return false
    }

    private fun saveLog() {
        Coroutine.async {
            val backupPath = AppConfig.backupPath ?: let {
                appCtx.toastOnUi("未设置备份目录")
                return@async
            }
            if (!AppConfig.recordLog) {
                appCtx.toastOnUi("未开启日志记录，请去其他设置里打开记录日志")
                delay(3000)
            }
            val doc = FileDoc.fromUri(Uri.parse(backupPath), true)
            copyLogs(doc)
            copyHeapDump(doc)
            appCtx.toastOnUi("已保存至备份目录")
        }.onError {
            AppLog.put("保存日志出错\n${it.localizedMessage}", it, true)
        }
    }

    private fun createHeapDump() {
        Coroutine.async {
            val backupPath = AppConfig.backupPath ?: let {
                appCtx.toastOnUi("未设置备份目录")
                return@async
            }
            if (!AppConfig.recordHeapDump) {
                appCtx.toastOnUi("未开启堆转储记录，请去其他设置里打开记录堆转储")
                delay(3000)
            }
            appCtx.toastOnUi("开始创建堆转储")
            System.gc()
            CrashHandler.doHeapDump(true)
            val doc = FileDoc.fromUri(Uri.parse(backupPath), true)
            if (!copyHeapDump(doc)) {
                appCtx.toastOnUi("未找到堆转储文件")
            } else {
                appCtx.toastOnUi("已保存至备份目录")
            }
        }.onError {
            AppLog.put("保存堆转储失败\n${it.localizedMessage}", it)
        }
    }

    private fun copyLogs(doc: FileDoc) {
        val cacheDir = appCtx.externalCache
        val logFiles = File(cacheDir, "logs")
        val crashFiles = File(cacheDir, "crash")
        val logcatFile = File(cacheDir, "logcat.txt")

        dumpLogcat(logcatFile)

        val zipFile = File(cacheDir, "logs.zip")
        ZipUtils.zipFiles(arrayListOf(logFiles, crashFiles, logcatFile), zipFile)

        doc.find("logs.zip")?.delete()

        zipFile.inputStream().use { input ->
            doc.createFileIfNotExist("logs.zip").openOutputStream().getOrNull()
                ?.use {
                    input.copyTo(it)
                }
        }
        zipFile.delete()
    }

    private fun copyHeapDump(doc: FileDoc): Boolean {
        val heapFile = FileDoc.fromFile(File(appCtx.externalCache, "heapDump")).list()
            ?.firstOrNull() ?: return false
        doc.find("heapDump")?.delete()
        val heapDumpDoc = doc.createFolderIfNotExist("heapDump")
        heapFile.openInputStream().getOrNull()?.use { input ->
            heapDumpDoc.createFileIfNotExist(heapFile.name).openOutputStream().getOrNull()
                ?.use {
                    input.copyTo(it)
                }
        }
        return true
    }

    private fun dumpLogcat(file: File) {
        try {
            val process = Runtime.getRuntime().exec("logcat -d")
            file.outputStream().use {
                process.inputStream.copyTo(it)
            }
        } catch (e: Exception) {
            AppLog.put("保存Logcat失败\n$e", e)
        }
    }

    companion object {
        private const val KEY_CONTRIBUTORS = "contributors"
        private const val KEY_UPDATE_LOG = "update_log"
        private const val KEY_CHECK_UPDATE = "check_update"
        private const val KEY_INTERNAL_BETA = "internal_beta"
        private const val KEY_UPDATE_ACCELERATION = "update_acceleration_manage"
        private const val KEY_LICENSE = "license"
        private const val KEY_DISCLAIMER = "disclaimer"
        private const val KEY_PRIVACY_POLICY = "privacyPolicy"
        private const val KEY_CRASH_LOG = "crashLog"
        private const val KEY_SAVE_LOG = "saveLog"
        private const val KEY_CREATE_HEAP_DUMP = "createHeapDump"
    }

}
