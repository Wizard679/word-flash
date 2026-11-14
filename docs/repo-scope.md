# 仓库内容范围说明

本仓库是「词刷」的可读完整工程，为了控制仓库体积与合规风险，做了三处裁剪，阅读代码与评估实现不受影响：

1. 词库：`app/src/main/assets/` 内置的是每库 12 词的**示例数据**（`banks.json` 的 `count` 为 12），仅用于让工程直接跑通。完整词库（四级 5351 / 六级 7106 / 专升本 4649）需本地用 `tools/build_open_words.py` 重建，流程见 `docs/rebuild-data.md`。
2. Gradle Wrapper：`gradlew` 与 `gradle/wrapper/gradle-wrapper.jar` 为二进制/生成物，未入库；用 Android Studio 打开工程即可自动同步，或在本地执行 `gradle wrapper --gradle-version 8.9` 生成。
3. 单元测试：48 项测试（Engine 16、Flow 10、Planner 7、Assets 6、UI 7、CardPreview 2）在开发环境运行通过，测试源码未随仓库公开分发；如需可单独索取。

签名密钥不在仓库内：`app/build.gradle` 从环境变量或 `gradle.properties` 读取 keystore 路径与口令，缺失时只构架未签名包。
