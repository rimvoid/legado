package io.legado.app.help.update

import io.legado.app.constant.AppConst
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.coroutine.Coroutine
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withTimeoutOrNull

object AppUpdate {

    /**
     * 暂时关闭检查更新功能，恢复时改为 true
     */
    const val checkUpdateEnabled = false

    val giteeUpdate: AppUpdateInterface by lazy {
        AppUpdateGitee
    }
    val githubUpdate: AppUpdateInterface by lazy {
        AppUpdateGitHub
    }
    val preferredUpdate: AppUpdateInterface by lazy {
        PreferredAppUpdate
    }

    data class UpdateInfo(
        val tagName: String,
        val updateLog: String,
        val downloadUrl: String,
        val fileName: String,
        val versionCode: Long = versionCodeFromFileName(fileName),
        val requestHeaders: Map<String, String> = emptyMap()
    )

    interface AppUpdateInterface {

        fun check(scope: CoroutineScope): Coroutine<UpdateInfo>

    }

    fun isLatestVersionError(error: Throwable): Boolean {
        val message = error.message ?: return false
        return error is NoStackTraceException &&
            (message.contains("最新版本") || message.contains("鏈€鏂扮増鏈"))
    }

    fun latestVersionError(): NoStackTraceException {
        return NoStackTraceException("已是最新版本")
    }

    fun versionCodeFromFileName(fileName: String): Long {
        return Regex("""^.+?_.+?_([^_]+)(?:_(\d+))?\.apk$""")
            .matchEntire(fileName)
            ?.groupValues
            ?.getOrNull(2)
            ?.toLongOrNull()
            ?: 0L
    }

    fun isNewerThanCurrent(updateInfo: UpdateInfo): Boolean {
        return if (updateInfo.versionCode > 0L) {
            updateInfo.versionCode > AppConst.appInfo.versionCode
        } else {
            updateInfo.tagName > AppConst.appInfo.versionName
        }
    }

    private fun compareVersion(left: UpdateInfo, right: UpdateInfo): Int {
        return if (left.versionCode > 0L && right.versionCode > 0L) {
            left.versionCode.compareTo(right.versionCode)
        } else {
            left.tagName.compareTo(right.tagName)
        }
    }

    private object PreferredAppUpdate : AppUpdateInterface {
        override fun check(scope: CoroutineScope): Coroutine<UpdateInfo> {
            return Coroutine.async(scope) {
                checkNow()
            }.timeout(15000)
        }

        private suspend fun checkNow(): UpdateInfo = coroutineScope {
            val officialDeferred = async {
                withTimeoutOrNull(8_000) {
                    runCatching { checkOfficialNow() }
                } ?: Result.failure(NoStackTraceException("正式版更新检查超时"))
            }
            if (!AppUpdateConfig.internalBetaConfigured) {
                return@coroutineScope officialDeferred.await().getOrThrow()
            }

            val internalBetaDeferred = async {
                withTimeoutOrNull(8_000) {
                    runCatching { AppUpdateInternal.checkNow() }
                } ?: Result.failure(NoStackTraceException("内测版更新检查超时"))
            }

            val internalBetaResult = internalBetaDeferred.await()
            val officialResult = officialDeferred.await()
            val official = officialResult.getOrNull()
            val internalBeta = internalBetaResult.getOrNull()

            if (official != null && internalBeta != null) {
                return@coroutineScope if (compareVersion(official, internalBeta) >= 0) {
                    official
                } else {
                    internalBeta
                }
            }
            official?.let { return@coroutineScope it }
            internalBeta?.let { return@coroutineScope it }

            val officialError = officialResult.exceptionOrNull()
            val internalBetaError = internalBetaResult.exceptionOrNull()
            if (officialError != null && !isLatestVersionError(officialError)) {
                throw officialError
            }
            if (internalBetaError != null && !isLatestVersionError(internalBetaError)) {
                throw internalBetaError
            }
            throw officialError ?: internalBetaError ?: latestVersionError()
        }

        private suspend fun checkOfficialNow(): UpdateInfo {
            return when (AppUpdateConfig.strategy) {
                AppUpdateConfig.STRATEGY_GITEE_ONLY -> AppUpdateGitee.checkNow()
                AppUpdateConfig.STRATEGY_GITHUB_ONLY -> AppUpdateGitHub.checkNow()
                else -> checkGiteeThenGithub()
            }
        }

        private suspend fun checkGiteeThenGithub(): UpdateInfo {
            return try {
                AppUpdateGitee.checkNow()
            } catch (giteeError: Throwable) {
                try {
                    AppUpdateGitHub.checkNow()
                } catch (githubError: Throwable) {
                    if (isLatestVersionError(giteeError) && !isLatestVersionError(githubError)) {
                        throw githubError
                    }
                    if (isLatestVersionError(githubError)) {
                        throw githubError
                    }
                    throw NoStackTraceException(
                        "Gitee 更新失败: ${giteeError.localizedMessage ?: giteeError.message ?: giteeError}\n" +
                            "GitHub 更新失败: ${githubError.localizedMessage ?: githubError.message ?: githubError}"
                    )
                }
            }
        }
    }

}
