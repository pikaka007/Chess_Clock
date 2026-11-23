# 开发记录

1. 根据 AppForge 的 Chess_Clock 操作场景确定换手、暂停、重置和计步规则，列出可控时间的验收样例。
2. 用 Kotlin 实现独立计时核心，先结算已用时间，再判断超时与 Fischer 加秒。
3. 用 Jetpack Compose 实现双向表盘与时限设置；在状态切换时保存对局，设备重启后按快照暂停恢复。
4. 在 Android 14 模拟器上检查对局流程。更换时限的确认框原来只挂在表盘页面，移至两个页面共用的上层后通过回归测试。
5. 构建 debug APK，录制演示并整理测试结果与课程报告。

[验证结果](verification.md) · [分阶段 token 用量](token-summary.md)
