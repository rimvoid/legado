# 项目长期记忆

## 本机构建环境（legado / 阅读 Archive）—— 已实测可编译

- JDK：**必须 17**（`C:/Users/45574/.workbuddy/binaries/jdk/jdk-17.0.20.1+1`）；用 21 会报 `Cannot find a Java installation ... {languageVersion=17}`（Gradle toolchain 要求 17）
- Android SDK：`C:/Users/45574/.workbuddy/binaries/android-sdk`（platforms: android-34/35/36/37.0；build-tools 36.0.0）
- 项目：compileSdk 36 / targetSdk 36 / minSdk 21，AGP 8.13.2，Kotlin 2.3.10
- **不要用 `./gradlew`**：wrapper 要下 8.14.4 发行版，实测 13 分钟仅 18MB，直接用本地已解压的 Gradle 8.13：
  `$(ls ~/.gradle/wrapper/dists/gradle-8.13-bin/*/gradle-8.13/bin/gradle)`
- 独立 `GRADLE_USER_HOME=D:/tmp/gradle-home`（junction 到原 caches），避开 `aliyun-prepend.init.gradle` 的 settings repo 冲突；该目录只放 `mirror.init.gradle`（阿里云镜像），需在线模式解析 AGP
- **Kotlin 编译必须加内存**，否则后端 OOM（报 `Couldn't transform method node` + `GC overhead limit exceeded`，且报错指向无关文件）：
  `-Pkotlin.daemon.jvmargs="-Xmx6g -XX:MaxMetaspaceSize=1g -XX:+UseParallelGC"`

```bash
cd /d/weiheng/proj/legado
export JAVA_HOME="C:/Users/45574/.workbuddy/binaries/jdk/jdk-17.0.20.1+1"
export ANDROID_HOME="C:/Users/45574/.workbuddy/binaries/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="D:/tmp/gradle-home"
"$(ls ~/.gradle/wrapper/dists/gradle-8.13-bin/*/gradle-8.13/bin/gradle)" :app:assembleDebug \
  --no-daemon --console=plain \
  -Pkotlin.daemon.jvmargs="-Xmx6g -XX:MaxMetaspaceSize=1g -XX:+UseParallelGC"
```

- 产物路径带 flavor：`app/build/outputs/apk/app/debug/legado_app_<版本>_<build>.apk`（**不是** `outputs/apk/debug`）
- 全量构建约 14 分钟，改代码后增量约 10 分钟；单测任务名是 `:app:testAppDebugUnitTest`
- `java` 不在 PATH，未提交 `local.properties`，必须显式设 JAVA_HOME / ANDROID_HOME；**先查 `~/.workbuddy/binaries` 再判断"无法编译"**
- `apksigner` / `aapt2` 等 sdk 工具同样要 JAVA_HOME

## release 打包（用户默认要 release）

- 签名靠 Gradle 属性，缺了就出未签名包，所以必须显式传：
  ```bash
  :app:assembleRelease -PRELEASE_STORE_FILE=ci-debug.keystore \
    -PRELEASE_STORE_PASSWORD=legado-ci-debug \
    -PRELEASE_KEY_ALIAS=legado-ci-debug \
    -PRELEASE_KEY_PASSWORD=legado-ci-debug
  ```
  密钥库在仓库内 `app/ci-debug.keystore`（PKCS12，证书 `CN=Legado CI Debug`），官方 GitHub 工作流用的也是它
- release 变体：包名 `io.legado.app.Archive`（debug 是 `.debug`，两者可共存）、`minifyEnabled false`
- 产物：`app/build/outputs/apk/app/release/legado_app_<版本>_<build>.apk`，构建脚本还会自动复制一份到 `D:/weiheng/proj/`
- release 全量约 21 分钟（比 debug 多 lintVitalAnalyze 与 optimizeResources）


