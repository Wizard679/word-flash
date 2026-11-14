# 词刷 WordFlash

竖版信息流式背单词安卓 App：像刷短视频一样上滑换词，三个大按钮（不熟 / 熟 / 熟练）替代点赞收藏，点完自动滑到下一个单词。

> 本项目为独立开发的单词学习工具，交互形式为通用的竖向卡片信息流，**与抖音、字节跳动没有任何关系**，不使用其商标、Logo 或素材。

## 功能

- **三个词库**：四级 5351、六级 7106、专升本 4649，全部离线内置，进度分别独立保存（SQLite）。
- **信息流**：上滑换词；底部单手握持友好的三个大按钮，点击后自动翻到下一个单词。
- **三档状态**
  - `▲ 不熟`：权重 +18，约 1 分钟后重新插入信息流；
  - `▼ 熟`：权重 −12，下一次推送变成四选一；
  - `✓ 熟练`：权重压到最低，30 天内基本不再推送。
- **学习闭环**：首次推送带音标 / 释义 / 英中例句 → 四选一 → 四选一连对 3 次进入拼写挑战（听音 + 音标 + 例句挖空），拼对标记「熟」。
- **答题自动翻页**：答对 0.9 秒、答错 2.1 秒后自动滑到下一词，留时间看正确释义。
- **翻页自动朗读开关**：设置页可自定义，默认开启；关闭后只在点「发音」时朗读，拼写卡始终朗读。
- **记忆曲线**：10 分钟 → 1 小时 → 6 小时 → 1 天 → 2 天 → 4 天 → 7 天 → 15 天 → 30 天。
- **统计页**：今日新词 / 复习 / 熟练、三库熟练进度、记忆曲线说明。
- **速记模式**：复习间隔压缩 20 倍，方便快速验证完整流程。

## 词库与许可

| 内容 | 来源 | 许可 |
| --- | --- | --- |
| 词条 / 中文释义 | [ECDICT](https://github.com/skywind3000/ECDICT) | MIT |
| 音标（美式 IPA） | [CMU Pronouncing Dictionary](http://www.speech.cs.cmu.edu/cgi-bin/cmudict) | BSD 风格 |
| 例句 / 中文翻译 | [Tatoeba](https://tatoeba.org) | CC BY 2.0 FR |

- 音标由 CMUdict 的 ARPAbet 按规则转写为统一美式 IPA，旧词库里 `beik`、`θrəu`、西里尔 `ә` 一类不规范音标已清洗；99.8% 的词有干净音标。
- 例句按词建立索引、去重、过滤不雅内容，中文统一转为简体；99% 的词有例句，其中约 83% 带中文对照。
- 发音调用 Android 系统离线 TTS，**不内置任何第三方录音**。
- 完整的署名与再分发要求见 [`NOTICE`](NOTICE) 与 [`app/src/main/assets/ATTRIBUTION.md`](app/src/main/assets/ATTRIBUTION.md)。

## 构建

环境：JDK 17、Android SDK（compileSdk 35 / build-tools 34.0.0）、Gradle 8.9。

```bash
gradle wrapper --gradle-version 8.9   # 首次生成 wrapper
./gradlew testDebugUnitTest
gradle assembleDebug                 # 调试包
```

发布包签名信息不入库。如需构建 release：

```bash
mkdir -p keystore && cp <your>.jks keystore/cishua.jks
export CISHUA_STORE_PASS=... CISHUA_KEY_ALIAS=cishua CISHUA_KEY_PASS=...
gradle assembleRelease
```

也可以把口令写在 `gradle.properties`（已被 `.gitignore` 排除）的 `cishua.storePass` / `cishua.keyAlias` / `cishua.keyPass`。

## 重新生成词库

仓库内已内置生成好的词库资源，直接编译即可运行。如需从原始语料重建：

```bash
python3 tools/build_open_words.py \
  --ecdict ecdict.csv --cmudict cmudict.dict --tatoeba ./tatoeba \
  --out app/src/main/assets --sxb-list app/src/main/assets/words_sxb.json
```

需要自行下载 ECDICT、CMUdict 与 Tatoeba 英中句对（细节见 `docs/rebuild-data.md`）。构建期用到 OpenCC 做繁转简，仅构建需要，不打进 APK。

## 测试

| 测试集 | 数量 | 覆盖 |
| --- | --- | --- |
| WordEngineTest | 16 | 权重、状态流转、连对进拼写、答错回退、记忆曲线、选项唯一、拼写比对 |
| FeedPlannerTest | 7 | 新词额度、熟练词过滤、近期去重、高权重优先、逾期优先 |
| WordAssetsTest | 6 | 词数与元信息一致、释义完整、去重、例句含目标词、音标字符合法 |
| FeedFlowTest | 10 | 首推释义 → 四选一 → 答错加权重 → 连对进拼写 → 拼对熟练 → 重启留存 → 切库 |
| AppUiTest | 7 | 启动、按钮自动翻页、上滑换词、设置、朗读开关持久化、统计、截图 |
| CardPreviewTest | 2 | 四选一 / 拼写卡片渲染 |

## 目录

```
app/src/main/java/com/cishua/app/
├── data/      词库加载、SQLite 进度
├── engine/    权重、记忆曲线、答题判定
├── ui/        信息流卡片、底部按钮、设置/统计面板、TTS
├── vm/        ViewModel
└── MainActivity.kt
app/src/main/assets/   词库 JSON + ATTRIBUTION
app/src/test/          JVM + Robolectric 测试
tools/build_open_words.py  词库构建脚本
```

## 免责声明

本项目仅用于个人学习与技术交流。词库、例句、音标版权归各原始项目与贡献者所有，请遵守对应许可；二次分发时请保留 `NOTICE` 与 `ATTRIBUTION.md`。App 完全离线运行，不收集、不上传任何个人数据。
