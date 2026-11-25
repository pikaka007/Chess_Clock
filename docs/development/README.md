# Chess Clock 开发全过程

本记录按实际开发顺序整理。四份补丁从原开发仓库导出，记录每次改变 Android 应用源码和测试的提交。表中短哈希是原开发仓库标识；公开仓库采用独立发布历史，复核每一阶段请使用对应补丁。

| 阶段 | 当时的要求、操作与结果 | 可复查材料 |
|---|---|---|
| 选题与范围 | 从 AppForge 选择 Chess_Clock。最初做了 Web 原型并验证换手、暂停和重置；复核后决定重新实现原生 Android APK。原型没有 Git 历史。Android 阶段从初始化仓库起记录过程。 | [AppForge 题目](https://github.com/AppForge-Bench/AppForge/blob/main/tasks/tasks.json)，检索 Chess_Clock |
| 需求与验收 | 将规则写成可判定的数值例子，再开始编码。1 分钟不加秒时，玩家二用 3 秒后换手，玩家一再用 2 秒，应分别剩 57 秒和 58 秒，步数为 1。团队另选 3+2、超时优先、后台和跨重启恢复作为扩展。原始基线 1dbbd74。 | [全部 A–F 验收场景](acceptance.md) |
| Android 骨架 | 建立 Kotlin、Compose、Gradle Wrapper 工程，先构建可安装的 debug 包。7dd37c5。 | [01 工程骨架](patches/01-app.patch) |
| 计时核心 | 将时间源注入生产状态机，统一扣时、加秒顺序；实现快照校验和恢复。该阶段有 60 项 JVM 测试。a73caa5。 | [02 核心与测试](patches/02-app.patch)、[十项设计决策](decisions.md) |
| 界面与生命周期 | 实现两侧表盘、时限设置、确认框、屏幕常亮、后台保存和恢复；加入 Android 仪器测试。638ca96。 | [03 界面与交互](patches/03-app.patch) |
| 测试与修复 | 首轮 17 项仪器测试有 16 项失败。通过控件树定位测试选错节点、软键盘挡住按钮、确认框未出现在设置页等不同原因。修复后 67 项 JVM 测试与 17 项仪器测试通过，lint 零警告。23e3089。 | [04 修复差异](patches/04-app.patch)、[六个排错实例](debugging.md)、[各用例预期与实测结果](verification.md) |
| 设备与复现 | 在 Android 14 模拟器上操作完整对局，并单独测试进程回收、设备重启和修改系统时间。另在干净检出目录重新构建；团队随后完成实体手机验证。 | [验证记录](verification.md)、[界面截图](../../demo/screens) |
| 整理与交付 | 分阶段汇总 Pi 用量，核对截图、图注、测试数量与文件校验值。完成 63.7 秒演示和课程报告，作为课程材料单独交付。GitHub 发布 APK 与 SHA-256 清单。 | [分阶段用量](token-summary.md)、[v1.0.0](https://github.com/pikaka007/Chess_Clock/releases/tag/v1.0.0) |

## AI 协作中的要求与反馈

以下为交互要点摘记。选题时团队要求先读课件、核对 Chess_Clock 原题，再提出实现思路。Web 原型交付后，团队指出缺少可安装的 Android 工程及 Git 记录；第二轮明确要求先提交基线，再分阶段开发，并分别记录 JVM、模拟器和实体设备上的结果。对加秒规则给出精确的预期值，让 Pi 用可控时间重现边界条件。设备测试失败后，将实际界面文字与预期逐项核对，决定修正实现还是测试。最终核对分阶段 token 口径、真机验证范围与交付物，再发布源码和 APK。

## 如何回放源码演进

在仓库根目录运行以下 Git Bash 命令。临时索引独立于工作区；不会覆盖当前源码。输出应为 Git 树对象 9c9842d00222320b2389415377227c475216e022，与最终 app 目录一致。

```bash
(
  set -e
  tmpdir=$(mktemp -d)
  export GIT_INDEX_FILE="$tmpdir/index"
  git read-tree --empty
  for patch in docs/development/patches/*.patch; do
    git apply --cached "$patch"
  done
  tree=$(git write-tree)
  git rev-parse "$tree:app"
  git rev-parse HEAD:app
)
```

运行应用和测试的环境及命令见[项目首页](../../README.md)。精确的计时断言使用生产状态机和可控时钟，仪器测试需已启动的设备或模拟器。
