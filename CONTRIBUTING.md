# 贡献指南

1. Fork 后从 `main` 创建修改分支。
2. 保留现有 Kotlin / Compose 代码风格，将界面状态与数据处理分开。
3. 每个 PR 聚焦一个问题，避免混入无关改动。
4. 提 PR 时说明问题、修改结果和实际完成的验证。界面修改附前后截图；天气规则修改说明数据依据。

需要检查时可运行 `./gradlew :app:testDebugUnitTest :app:lintDebug`，Windows 使用 `gradlew.bat`。

提交代码即同意按项目 MIT 许可证发布，第三方素材沿用其原始许可。
