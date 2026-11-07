# 阅读 Archive

[中文](README.md) · [English](English.md)

<p align="center"><img width="125" height="125" src="docs/archive_icon.svg" alt="阅读 Archive"></p>

阅读 Archive 继承自 Lyc 维护的 Legado 分支，在 [Legado](https://github.com/gedoor/legado) 的基础上继续完善正文与 EPUB 阅读、界面主题、听书、AI 和自动任务。

应用不内置书籍内容。你可以自行添加书源，也可以导入本地 TXT、EPUB 书籍。

## 下载与更新

- [GitHub Releases](https://github.com/Rimchars/legado/releases)：公开版本、安装包和更新说明。
- [Gitee Releases](https://gitee.com/zziji/legado/releases)：国内下载镜像。
- [Gitee 更新通道](https://gitee.com/zziji/legado/releases/tag/latest-arm64-release)：固定版本入口，供手动下载。

当前公开版本为第十五版 `3.26.09242230`，提供 arm64-v8a 安装包。安装包和完整更新说明见上方 Release 页面。应用内的检查更新与自动更新已暂时关闭，请从上方页面手动下载。

## 本轮更新

第十五版新增阅读主题与加载模板，并改善分页、翻页和详情页体验：

- 新增 Minecraft、明日香、诡秘之主、哆啦 A 梦四套内置阅读主题。
- 新增固定画框的滚动模板，四周装饰保持固定，正文在中央阅读框连续滚动；分页模板可单独设置翻页方式。
- 新增独立 EPUB 加载模板，默认「山茶花」，支持预览、复制编辑和导入导出，背景延伸至状态栏并适配日夜模式。
- 页面模板统一管理字体、配色、页眉页脚和布局，隐藏不适用的原生排版设置。
- 修复引号、反引号及段评图片提前跳页引起的大片留白，优化前后页快照、连续翻页与跨章衔接。
- 优化详情页加载、刷新和网页简介，定时任务入口统一放到「更多」菜单。

[查看第十五版完整更新文案](docs/releases/2026-09-24-v15.md) · [更新日志](CHANGELOG.md)

## 本地修改

第十五版发布之后，本地新增了尚未合入 Release 的「取词模式」（提交 `47c9174`），行为说明如下：

- 长按正文选中字词后，操作菜单新增常驻「取词」按钮，该按钮不参与菜单项自定义配置。开启后按钮高亮，并立即查询当前选中字词。
- 取词模式开启时，点击正文中的单词直接弹出词典释义；点到空白处或未命中文字时，仍按点击区域正常翻页或呼出菜单。再次点按「取词」即可退出。
- 取词开关按书单独记忆；释义使用「我的 → 词典规则」中启用的规则，没有启用规则时会提示先添加。
- 原生正文与 EPUB 直读均支持取词，EPUB 侧由直读渲染层在网页内识别点击的单词并回调查词。
- 改动覆盖阅读页取词判定与长按菜单、EPUB 直读层、按书记忆字段及中英繁多语言文案，功能描述同步见 [docs/features.md](docs/features.md)。

另有一项本地调整：应用内的检查更新和自动更新已暂时关闭。

- 「关于」页不再显示「检查更新」入口，启动时也不再执行每 24 小时一次的自动检查；「内测版」「更新加速」设置项保留。
- 开关集中在 [AppUpdate.kt](app/src/main/java/io/legado/app/help/update/AppUpdate.kt) 的 `checkUpdateEnabled`，恢复更新功能时改为 `true` 即可。

## 功能总览

| 方向 | 主要能力 |
| --- | --- |
| 阅读 | 书源与本地书籍、原生与 EPUB 排版、翻页动画、阅读样式、书签与进度 |
| 书架与详情 | 列表和网格、分组、标签、批量管理、沉浸详情、目录与定时更新 |
| 高亮与模板 | 本地 RED 规则、图案高亮、字体选择、页面 HTML/CSS/JavaScript、横排与竖排 |
| 素材与主题 | 共用图片字体库、日夜主题、背景、高级标题、页眉页脚与气泡 |
| 听书与多媒体 | 系统和网络 TTS、原文跟随、跨应用悬浮控件、漫画与视频入口 |
| AI | 可配置 AI 服务、书源搜索、书籍与章节读取、阅读记录查询及联网工具 |
| 自动化与数据 | 定时任务、缓存、备份恢复、WebDAV、对象存储与容器管理 |
| 网络 | DNS/DoH 选择、按功能分流、域名例外、服务配置与测速 |

[查看详细功能及模式差异](docs/features.md)

## 使用文档

- [页面模板：应用、分享与编写](docs/reader-templates.md)
- [网络与 DNS](docs/doh-network.md)
- [高级标题等视觉资源包](docs/visual-resource-packages.md)
- [段落规则和气泡包导入](docs/online-package-import.md)
- [Web 与 Content Provider API](api.md)
- [上游帮助文档](https://www.yuque.com/legado/wiki)

高亮规则使用本地 `.red` 文件导入。完整图片图案与复杂 CSS 效果使用 EPUB 渲染；普通正文的 EPUB 模式当前不启用段落规则与 `pclick`，原始图片 `click` 和替换净化继续支持。页面模板库提供独立备份。

本版已完成自动化与浏览器检查，尚未完成 Android 真机显示、触控和整机帧率验收。问题反馈请提供版本、渲染模式和复现步骤。

## 开源与致谢

感谢 [gedoor/legado](https://github.com/gedoor/legado)、[Luoyacheng/legado](https://github.com/Luoyacheng/legado) 及上游贡献者。定时任务功能感谢明月的贡献与支持。

项目使用了 Rhino、Jsoup、OkHttp、Glide、Miuix、Paged.js 等开源组件；各组件保留各自许可。项目许可见 [LICENSE](LICENSE)，应用内使用的组件说明见 [开源许可](app/src/main/assets/LICENSE.md)。

历史说明保存在 [2026 年 7 月更新记录](docs/changelog/2026-07.md) 和 [上游历史日志](docs/changelog/upstream-2022.md)。
