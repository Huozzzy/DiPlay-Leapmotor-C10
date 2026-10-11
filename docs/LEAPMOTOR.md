# 零跑适配技术说明 / Leapmotor adaptation notes

上游：[shihabal3amri/DiPlay](https://github.com/shihabal3amri/DiPlay)（GPL-3.0）。
本文件描述本仓库**唯一原创的部分**：零跑适配补丁。源文件清单见 [`../README.md`](../README.md)。

> Upstream: [shihabal3amri/DiPlay](https://github.com/shihabal3amri/DiPlay) (GPL-3.0).
> This document covers the **only original part** of this repository: the Leapmotor patches.

适配车型：**零跑 2021–2023 款全部车型**。其它车型与其它品牌车机未做验证。
Target range: **all Leapmotor 2021–2023 model years**. Anything else is unvalidated.

---

## 1. 会话生命周期挂载（必须先做）

零跑的两套监听器都必须跟随 CarPlay 会话生命周期：会话建立才有权接管方控与响应挡位，会话结束必须立刻释放，
否则会抢走车机收音机的按键，或在没连 iPhone 时误发暂停。

上游在同一位置挂载硬件媒体键版 `CarPlayMediaKeys`，零跑这套广播版与之对等，因此复用同一组锚点。
锚点文件：`common/src/main/java/com/shilapi/xcertplay/CarPlayHostActivity.kt`

- **挂载点**：紧跟 `CarPlayMediaKeys.attach(this, next)`
- **卸载点 A**：紧跟 `CarPlayMediaKeys.detach(oldController)`，下一行为 `CarPlayBackgroundSession.clear(oldController, keepOwner = true)`
- **卸载点 B**：紧跟 `CarPlayMediaKeys.detach(oldController)`，下一行为 `CarPlayBackgroundSession.clear(oldController)`

> ⚠️ 该文件在上游版本之间改动较大。**不要用行号对**，用上面的语义锚点。
> **两处卸载都要改**，漏一处会导致重连后重复按键或暂停后不恢复。

Android 13+：`LeapmotorMediaKeys` / `LeapmotorGearMonitor` 已按 `RECEIVER_NOT_EXPORTED` 处理 API 33+。
**不要**改成两参数 `registerReceiver`，某些固件上会被系统拒绝，导致整个方控失效。

可选 — 接诊断输出：

```kotlin
LeapmotorMediaKeys.onDiagnostic = { line -> diagnosticLog?.append(line) }
LeapmotorGearMonitor.onDiagnostic = { line -> diagnosticLog?.append(line) }
```

---

## 2. 方控（steering-wheel keys）

**为什么不能用上游方案**：零跑车机的方向盘按键经 CAN → ICU → 广播转发，第三方 App 收不到任何 `KeyEvent`。
所以必须接收广播。

源文件：`common/src/main/java/com/shilapi/xcertplay/LeapmotorMediaKeys.kt`

### 2.1 通道清单

| 通道族 | 数量 | 载荷 | 说明 |
|---|---|---|---|
| `LeapmotorMediaProtocol.CHANNEL_A` | 31 | 字符串 action / `receiver` 二进制 / `action` extra | 主通道。**一次注册 31 个**，覆盖已知固件里的全部 action 拼写形式，兼容不同车型与固件版本的差异 |
| `STOCK_HOUSEKEEPING` | 5 | — | 车机收音机/语音助手自用命令，**必须识别并丢弃**，否则会抢走收音机 |
| `EXTRA_CHANNELS` | 2 | int extra `ICU_MediaSwitch` / `ICU_MediaKey` | 键码走整形 extra，不是文本 |
| `CHANNEL_C11` | 2 | `keyEventType` + `value` | `keyEventType ∈ {steeringWheel, backRow}`，`value` 1=上一首 / 2=下一首 / 3=播放暂停 |

### 2.2 三种载荷解析

1. **二进制 `receiver` extra**：前两字节是长度前缀，`length = b0*100 + b1`，其后为 UTF-8 JSON
2. **JSON**：取 `data.action`；`src == "diplay"` 时丢弃（**防回环**——自己发出的写操作不能再被当成按键）；`type` 非 `music` 时丢弃
3. **int extra**：`ICU_MediaKey == 1` → 播放暂停；`ICU_MediaSwitch` 1 → 上一首，2 → 下一首

### 2.3 去重与分发

- **250 ms 去重窗口**：同一物理键常被多个通道同时投递，窗口内同一映射结果只放行一次
- 优先级：按键学习捕获 → 已学习的绑定 → `CarPlayMediaButton.forLeapmotorAction()`
- `controller == null`（无 CarPlay 会话）时**不动作**
- 本地视频播放器（`CarPlayVideo.onMediaKey`）优先于转发给 iPhone

### 2.4 映射表

追加在 `shared/src/main/java/com/shilapi/xcertplay/airplay/CarPlayMediaButton.kt`：

| 动作串 | CarPlay 键 |
|---|---|
| `play` | `PLAY` (1) |
| `pause` / `stop` | `PAUSE` (2) |
| `playpause` / `pauseplay` / `toggleplay` / `toggleplaypause` / `play_pause` | `PLAY_PAUSE` (3) |
| `next` / `nextone` / `nexttrack` | `NEXT` (4) |
| `prev` / `preone` / `previous` / `previoustrack` | `PREVIOUS` (5) |

> **关键**：`isWheelAction()` 覆盖的范围**故意小于** `forLeapmotorAction()` —— 前者排除 `play` / `pause` / `stop`。
> 裸的 `play`/`pause`/`stop` 同样来自收音机和语音路径，放行会造成误触。这个不一致必须保留。

---

## 3. 投仪表（instrument cluster projection）

源文件：`common/src/main/java/com/shilapi/xcertplay/LeapmotorClusterLayout.kt`

### 3.1 车型识别

```kotlin
Build.FINGERPRINT.contains("qti/msmnile_gvmq")
```

`msmnile_gvmq` 是高通 SA8155P 车规平台的板级标识。这是本分支唯一的车型识别入口，改动时留意别引入第二处判定。

### 3.2 视口几何

```
仪表面板          1920 × 720
CarPlay 仪表流    720 × 720（正方形）
放置位置          right-aligned → left = 1920 - 720 = 1200，top = 0
```

零跑的仪表是一块长条屏，右侧留出一个正方形区域给导航地图。

### 3.3 接入方式（当前采用窄口径改造）

改造集中在 `common/src/main/java/com/shilapi/xcertplay/ClusterMapPresentation.kt` 的 `onCreate`：

```kotlin
val plan = if (adaptive) DiLink51ClusterLayout.plan(size.x, size.y, theme) else null
// Leapmotor exposes no measurable DiLink layers, so its square window is derived from
// the physical panel size instead. It is only ever used when DiLink planning failed.
val leap = if (plan == null) LeapmotorClusterLayout.plan(size.x, size.y) else null
```

`videoParams` 改为三分支：

```kotlin
val videoParams = when {
    leap != null -> FrameLayout.LayoutParams(leap.width, leap.height, Gravity.TOP or Gravity.LEFT).apply {
        leftMargin = leap.left; topMargin = leap.top
    }
    plan == null -> FrameLayout.LayoutParams(-1, -1)
    else -> FrameLayout.LayoutParams(plan.width, plan.height, Gravity.TOP or Gravity.LEFT).apply {
        leftMargin = plan.left; topMargin = plan.top
    }
}
```

**为什么这样够用**：零跑的仪表流本身就是 720×720 正方形，不需要 TextureView 的裁剪/缩放变换，
改动面小一个数量级。BYD 路径（`plan != null`）行为完全不变。

### 3.4 完整改造路线

`ClusterMapViewport.kt` 已在仓库里，供把整条渲染链抽象成统一视口时使用。那需要同步修改
`InstrumentContrastView` 的构造签名（当前接收 `DiLink51ClusterLayout.Plan`），改动面大得多。

---

## 4. 挡位联动（gear monitor）

源文件：`common/src/main/java/com/shilapi/xcertplay/LeapmotorGearMonitor.kt`

| 项 | 值 |
|---|---|
| 广播 | `leapmotor.cantoapp.update` |
| CAN 信号 extra | `1010105`（int） |
| 取值 | `-1` = 未初始化（忽略） / `1` = R / `2` = N / `3` = D |
| 心跳 | 每秒向 `com.leapmotor.carcanserver` 发 `leapmotor.apptocan.update`，extra `upgradeAsk=upgrade` |

- 进入 R 档：`controller.sendMediaButton(PAUSE)`，让倒车影像与雷达提示音独占音频
- 退出 R 档：`controller.sendMediaButton(PLAY)`，从原位置恢复播放
- `pausedByReverse` 标志保证只暂停一次、只恢复一次
- 心跳用于维持车机对 DiPlay 多媒体占用状态的认知——只有在被认可为当前音源时暂停才被接受，且状态丢失时没有回调

---

## 5. 上游同步要点

跟随上游升级时会涉及的文件：

| 类 / 文件 | 同步难度 | 说明 |
|---|---|---|
| `shared/.../airplay/CarPlayMediaButton.kt` | 低 | 纯追加两个函数，几乎不会冲突 |
| `common/.../ClusterMapPresentation.kt` | 中 | `plan == null` 的判定结构稳定，按语义重新贴即可 |
| `common/.../CarPlayHostActivity.kt` | 中 | 上游改动较频繁，**用语义锚点，不要按行号** |

### 关于 `LearnedWheelKeys`

"方控按键自定义学习"（Capture → 存储 → UI）是独立于上游的一整套功能，不是上游的基类或接口，
上游仓库中不存在同名实现。

方控可用性与按键可自定义学习是两件事，可以分开推进。仓库里的 `LearnedWheelKeys.kt` 是过渡桩，
四个钩子全部返回"没学过任何键"，保证按键一定走通用映射。**实现补齐后删除该桩文件即可**，
没有其它地方按名依赖它。

---

## 6. 调试与排查

```bash
# ① 投仪表分支有没有命中（看车机平台指纹）
adb shell getprop ro.build.fingerprint | grep msmnile_gvmq

# ② 方控有没有收到广播（看哪些 action 被命中）
adb logcat -s DiPlay-MediaKeys

# ③ 挡位有没有被解析
adb logcat -s DiPlay-Gear
```

常见问题：

| 现象 | 排查方向 |
|---|---|
| 方控完全无反应 | ① 确认车机会发广播（看 `DiPlay-MediaKeys` 日志有没有任何 action 命中）；② 确认两处 `detach` 都已挂载；③ Android 13+ 不要用两参数 `registerReceiver` |
| 一次按键切两首歌 | 同一物理键被多个通道重复投递；检查 250 ms 去重窗口是否生效 |
| 按方控会把车机收音机也切掉 | `STOCK_HOUSEKEEPING` 通道没有被正确丢弃 |
| 挂倒挡后音频不恢复 | 检查 `pausedByReverse` 是否被重复置位（通常是 `detach` 漏了一处） |
| 投仪表黑屏或偏位 | 先确认平台指纹是否命中；命中后检查可用显示区是否为 1920×720 |
| HUD 转向指引无效果 | 该能力依赖上游的 BYD SOME/IP 集成（`com.ts.car.someip.*`），零跑车机上不保证可用 |
