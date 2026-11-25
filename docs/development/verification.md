# 验证记录（真实执行结果）

本文件只写**实际执行过**的命令与观察结果。三级证据严格区分：

- **A = 自动化（JVM 单元测试）**：`:app:testDebugUnitTest`
- **B = 自动化（模拟器仪器测试）**：`:app:connectedDebugAndroidTest`
- **C = 模拟器人工实测**：借助 `adb` 与 `uiautomator dump` 观察真实界面文本/边界
- **D = 未执行**（写清原因）

设备/模拟器：AVD `bara_api34`（Pixel-5 外形，x86_64，Android 14 / API 34，1080×2340 @440dpi）。
下列命令、读数与截图来自 Android 14 模拟器。团队另已完成 Android 手机验证。

---

## 1. 构建与静态检查

| 项 | 命令 | 结果 |
|---|---|---|
| Debug 构建 | `gradlew.bat :app:assembleDebug --offline` | BUILD SUCCESSFUL，`app-debug.apk` |
| 单元测试 | `gradlew.bat :app:testDebugUnitTest --offline` | **67 tests, 0 failures** |
| 仪器测试 | `gradlew.bat :app:connectedDebugAndroidTest --offline` | **17 tests, 0 failures**（模拟器） |
| Lint | `gradlew.bat :app:lintDebug` | 见 §2（首次因 `dl.google.com` 连接超时失败，重试成功） |
| 安装 | `adb install -r app/build/outputs/apk/debug/app-debug.apk` | `Success` |

测试分布（JVM，共 67 项）：

| 测试类 | 用例 | 覆盖 |
|---|---:|---|
| `ClockStateTest` | 14 | A 组零增量、B 组 3+2、超时优先、重复点击、暂停/继续、一次结算、锚点异常 |
| `ClockControllerTest` | 20 | C 组重置/取消/新对局确认、D 组快照恢复、E 组跳变补算、持久化节流 |
| `ClockSnapshotCodecTest` | 15 | 编解码往返、截断/重复键/版本/越界拒绝、同 boot 补扣一次、跨 boot 暂停 |
| `ClockFormatTest` | 6 | 1:00 / 5:00 / 十分位、负值、朗读与视觉一致（0–600 s 全扫描） |
| `TimeControlDraftTest` | 8 | 预设与自定义、空值/非数字/越界、摘要文案 |
| `ClockUiStateTest` | 4 | 加秒闪烁归属与 2 500 ms 窗口 |
| 合计 | **67** | |

---

## 2. Lint

首次执行（离线）失败：`No cached version of com.android.tools.lint:lint-gradle:31.5.2 available for offline mode`。
联网重试时 `dl.google.com` 连接超时（15 分 39 秒后失败），再次重试成功。

结果：**0 error**，warning 已逐条处理：

| Warning | 处理 |
|---|---|
| `RedundantLabel`（activity 与 application 同 label） | 删除 activity 上的 `android:label` |
| `ModifierParameter`（`ClockFace` 的 modifier 不是第一个可选参数） | 取消 `flashIncrementMs` 的默认值，使其成为唯一可选参数 |
| `UnusedResources` / 其它 | 见 §2 开头与最终报告 `docs/lint-results-debug.txt`（当前内容为 `No issues found.`） |

---

## 3. A 组：零增量（1 分钟预设，60 000 ms）

**A 级证据**：`ClockStateTest."A - sixty second game, handover after 3s, 2s later"` 断言完全等于预期：
60 000 → 点玩家二 → 3 000 ms 后点玩家一（玩家二 57 000、步数 1）→ 再过 2 000 ms（玩家一 58 000）
→ 暂停 5 000 ms（数值不变）→ 继续 1 000 ms（玩家一 57 000、玩家二 57 000）。

**C 级佐证**（模拟器，5 分钟对局以便观察，零增量语义相同）：

```
玩家二 4:55 未计时 | 步数 1 | 玩家一 行棋中 | 玩家一 4:51 计时中     ← 换手后
[暂停] 玩家二 4:55 已暂停 | 玩家一 4:47 已暂停                      ← 立即暂停
[4 秒后] 玩家二 4:55 已暂停 | 玩家一 4:47 已暂停                    ← 读数完全不变
[继续] 玩家二 4:55 | 玩家一 4:46 计时中 → 4:41                      ← 只有玩家一走时
```

## 4. B 组：Fischer 3+2

**A 级证据**：`ClockStateTest` / `ClockControllerTest`：

- 换手后移动方 `180 000 − 3 000 + 2 000 = 179 000`，另一方 `180 000`，步数 1；
- 重复点击运行方 3 次：余时、步数、`lastIncrement` 时间戳均不变；
- 暂停 4 s 后继续：双方余时不变、不加秒；
- 余时恰好为 0 的换手：`FINISHED` + `flagged`，**不加秒、不加步**，另一方未启动。

**C 级佐证**（模拟器，3+2）：换手后 `uiautomator dump` 读到 `玩家二 2:54 / 玩家一 3:00 / 步数 2`
以及获得加秒一方表盘上的 `+2 秒` 文本；控制条常驻 `换手 +2 秒`，状态行 `玩家二 行棋中 · 换手后该方 +2 秒`。

## 5. C 组：设置与重置

| 场景 | 结果 |
|---|---|
| 预设 1 分钟 | 设置页摘要"双方各 1:00，不加秒"；进入后 `1:00 / 1:00`，`0`（C 级） |
| 预设 3+2 | 摘要"双方各 3:00，每走完一步 +2 秒"+ 加秒说明；进入后 `3:00 / 3:00`（B/C 级） |
| 自定义 5 分钟 | 输入 5 分 0 秒 → 摘要"双方各 5:00，不加秒" → 进入后 `5:00 / 5:00`（B/C 级，对应 AF-2/2-1） |
| 空值 | 停留在设置页，摘要给出"请填写基础时长…"（B 级） |
| 越界 61 分钟 | 停留在设置页，摘要含"60"（B 级） |
| 非数字 | 输入框过滤非数字（UI），校验层仍拒绝（A 级 `TimeControlDraftTest`） |
| 重置 → 取消 | 弹窗期间双方冻结；取消后**原计时方**继续，余时与步数不变（A/B/C 级） |
| 重置 → 确认（5 分钟） | `5:00 / 5:00`、步数 0、等待首次点击；弹窗文案"双方恢复至 5:00，步数归零。"（A/B/C 级） |

C 级原始观察（弹窗文本，取自 `uiautomator dump`）：

```
重置对局？ | 双方恢复至 5:00，步数归零。\n\n计时已暂停：确认或取消都不会损失时间。取消后由原计时方继续。 | 取消 | 重置
```

## 6. D 组：恢复（严格区分四种情形）

| 情形 | 覆盖 | 结果 |
|---|---|---|
| D1 Activity 重建（旋转/重建） | B 级 `activityRecreation_keepsTheGameAndTheRunningSide` | 步数、配置、计时方保留；读数只前进真实经过的秒数（<5 s 容差断言） |
| D2 后台/息屏后返回（进程存活） | B 级 `stopAndRestart_restoresFromTheSnapshot`；C 级息屏实测 | 后台时长按真实经过扣除且只扣一次 |
| D3 系统回收后台进程 | **C 级**：`adb shell am kill` 后重新 `am start` | 见下方原始记录 |
| D4 用户强制停止 | 与 D3 同路径（快照仍在），**未单独实测**（D） | 说明：`am force-stop` 会清空任务，重新启动走同一恢复分支 |
| D5 设备重启 | **C 级**：`adb reboot` 后启动应用 | 运行中的对局回到暂停 + 明确提示 |

D3 原始记录（PID 变化证明进程确实被杀）：

```
pid before: 7947
[adb shell am kill com.bara.chessclock]
pid after kill: ''            ← 进程不存在
[am start]  pid after restart: 8021
玩家二 4:31 计时中 | 步数 0 | 玩家一 5:00 未计时   ← 单次补扣，仍为运行中
```

D5 原始记录：

```
重启前：玩家二 3:49 计时中
[adb reboot] → 启动应用
重启后：玩家二 3:51 已暂停 |
       检测到设备重启：无法计算关机期间的时间，已按上次保存的读数暂停。请核对后点"继续"。
```

> 说明：3:51 略大于重启前的 3:49，是因为快照写于更早的时刻（节流/ON_STOP 时点），
> 恢复的是"最后一次已知读数"。这正是保守策略的预期行为，不是缺陷。

## 7. E 组：时间异常

| 场景 | 覆盖 | 结果 |
|---|---|---|
| 30 s 无刷新后补算一次 | A 级 `a single late refresh deducts the whole elapsed span exactly once` | 只扣一次，重复读取同一时刻不重复扣 |
| 屏幕关闭 6 s 后返回 | C 级 | 4:23 → 4:12（含 adb 往返），运行状态保留 |
| 修改系统日期（同一次开机） | C 级：`adb shell date 112317302026.00`（+4 h06m） | 4:00 → 3:58，仅真实秒数（时钟完全不受影响） |
| 修改时区 | C 级：`setprop persist.sys.timezone America/New_York` | 3:58 → 3:55，仅真实秒数 |
| 锚点在未来（异常） | A 级 | 钳制为 0 增量，不产生负值、不白送时间 |
| 崩溃/卡顿导致快照损坏 | A 级（6 个用例） | 丢弃并回到默认设置 + `SNAPSHOT_DISCARDED` |

## 8. F 组：Android 交付与交互

- 安装：`adb install -r …` → `Success`（模拟器 API 34）。
- 完整流程（C 级）：选时 → 开局 → 换手 → 暂停 → 继续 → 重置，逐步用 `uiautomator dump` 核对文本。
- 屏幕常亮：B 级 `screenIsKeptOnWhileRunningAndReleasedWhenPaused` 断言
  `FLAG_KEEP_SCREEN_ON` 在运行中出现、暂停后清除。
- 超时：5 秒自定义对局实测 → `玩家二 0:00 超时 | 玩家二 超时 · 双方时钟已停止 | 步数 0`；
  超时后点击另一方表盘无任何变化（C 级）。
- 返回键：棋盘页按返回 → 进入设置页并暂停；从设置页"返回当前对局"恢复原计时方（B 级）。
- 大字号 / 小屏：`font_scale=1.3` 与 `wm size 720x1280 + density 320` 下逐节点检查边界，
  无裁切、无重叠（C 级，见 `demo/screens/` 与下方边界数据）。

小屏 + 1.3 倍字号下的关键节点边界（屏幕 720×1280）：

```
玩家二 表盘 20,86,700,465     3:51 229,222,491,348     步数 302,481,374,545
玩家一 表盘 20,793,700,1172   5:00 229,910,491,1036
```

## 9. 未验证 / 已知限制

| 项 | 状态 |
|---|---|
| 手机触控延迟、息屏功耗 | 真机验证未提供这些项目的专项测量数据。 |
| 用户「强制停止」（设置 → 应用 → 强制停止） | **未单独实测**。它与 D3 的 `am kill` 是两个入口：`am kill` 只杀后台进程、任务栈保留；强制停止会让应用进入 stopped 状态并清空任务栈。预期仍从磁盘快照恢复，但未验证。 |
| 通知/来电打断、画中画、分屏 | **未验证** |
| 横屏布局 | 代码未锁定方向，重建测试通过；**横屏视觉未逐屏检查** |
| 系统深色/浅色切换 | 应用固定深色主题，未提供浅色变体 |
| 多语言 | 仅中文界面（课程范围） |
| 长时间（>1 小时）连续计时的漂移 | 未做长跑测试；计算基于单调时钟，理论上无累积漂移 |

## 10. 干净检出复现

在独立目录检出源码，按 README 配置 SDK 后执行 Gradle Wrapper 的 assembleDebug 和 testDebugUnitTest。同机缓存与调试签名相同的条件下，构建耗时 17 秒，67 项单元测试通过，生成的 APK 为 8,346,000 字节，SHA-256 与发布附件一致。
