# 仪器测试失败与修复

**排错记录（仪器测试）**

| 现象 | 定位 | 修复 |
|---|---|---|
| `clockP1Time` 找不到节点 | 报错提示"unmerged tree 中有 1 个节点" | `ClockFace` 用 `semantics(mergeDescendants = true)`（一块表盘一个 TalkBack 节点），测试需 `useUnmergedTree = true` 取内部数字 |
| 点击后界面不刷新（`autoAdvance = false`） | 计时器是 `delay(80)` 循环，自动推进会让 `waitForIdle` 永不到 idle | 测试改为手动驱动时钟：每次点击后 `advanceTimeByFrame()` + `waitForIdle()` |
| 输入 5 分钟后点"进入对局"无反应 | 软键盘（`adjustResize`）遮住按钮，`performClick` 的注入坐标落在键盘上 | 测试先 `Espresso.closeSoftKeyboard()`；产品侧补 `imePadding()` 让按钮可滚到键盘上方 |
| 从设置页"开始新对局"没有弹窗 | 弹窗写在 `BoardScreen` 内，而当时在设置页 → 时钟被暂停却没有提示 | **产品缺陷**：把两个确认弹窗提升到 `MainActivity` 层的 `ClockDialogs`（`docs/decisions.md` D6） |
| 表盘数字 2:58，无障碍描述读 2 分 59 秒 | `uiautomator dump` 同时读到两处文本，发现四舍五入不一致 | **产品缺陷**：`ClockFormat.spoken` 改为与 `display` 同样截断，并加 0–600 s 全扫描回归测试 |
| 加秒闪烁抓不到 | 日志显示 `flash2=2000`（逻辑正确），只是 1.6 s 窗口短于 dump 往返 | 窗口调到 2.5 s（对棋手也更易察觉），并用 JVM 测试钉住边界 |
