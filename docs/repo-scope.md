# 仓库内容范围说明

本仓库是「词刷」的可读完整工程，为了控制仓库体积与合规风险，做了几处裁剪，阅读代码与评估实现不受影响：

1. 词库：`app/src/main/assets/` 内置的是每库 12 词的**示例数据**（`banks.json` 的 `count` 为 12），仅用于让工程直接跑通。完整词库（四级 5351 / 六级 7106 / 专升本 4649）需本地用 `tools/build_open_words.py` 重建，流程见 `docs/rebuild-data.md`。
2. Gradle Wrapper：`gradlew` 与 `gradle/wrapper/gradle-wrapper.jar` 为二进制/生成物，未入库；用 Android Studio 打开工程即可自动同步，或在本地执行 `gradle wrapper --gradle-version 8.9` 生成。
3. 单元测试：入仓库的是不依赖词库内容的纯逻辑测试——`WordEngineTest`（16 项，引擎权重/阶段/拼写）与 `FeedPlannerTest`（7 项，推送规划），共 23 项，可直接 
    `./gradlew testDebugUnitTest --tests "com.cishua.app.WordEngineTest" --tests "com.cishua.app.FeedPlannerTest"` 跑过。
   其余 25 项（`WordAssetsTest` 6、`FeedFlowTest` 10、`AppUiTest` 7、`CardPreviewTest` 2）依赖 **完整词库** 与 Robolectric/Compose 测试环境，在示例数据下会失败，故未入库。

签名密钥不在仓库内：`app/build.gradle` 从环境变量或 `gradle.properties` 读取 keystore 路径与口令，缺失时只构架未签名包。
