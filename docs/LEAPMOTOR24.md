# 零跑 2024+（C10 等）适配说明

> 适用分支：`leapmotor-24-27`　｜　产物：`Diplay-V<上游版本>-Leap24_27.apk`
> 基线：上游 [DiPlay](https://github.com/shihabal3amri/DiPlay) `v0.2.18`
> 与 2021–2023 的适配（`main` 分支，`Diplay-V<上游版本>-Leap21_23.apk`）**互不重合，不要混用**

---

## 一、为什么分成两个 APK

零跑在 2024 款换过车机软件架构，两代车机给第三方 App 的接口**完全不是一套东西**：

| | 2021–2023（`main` 分支） | 2024+（本分支） |
|---|---|---|
| 车辆数据通路 | CAN → ICU → **广播**推给 App | App 主动 `bindService` **拉** AIDL 接口 |
| 方控在 dex 里的痕迹 | `leapmotor.cantoapp.update`、`com.leapmotor.carcanserver`、`1010105` | `com.leapmotor.steerwheelcontrol.media` |
| 平台识别 | `Build.FINGERPRINT` 含 `qti/msmnile_gvmq` | 无平台指纹 |

两套机制的 action、载荷、取数方式无一相同，塞进同一个 APK 只能靠运行时分流互相污染。所以按代分两个 APK，各自只带自己那一套。

两个 APK **包名相同**（`com.shihab.diplay`，沿用上游），因此一台车机上只能装其中一个 —— 按车型年份挑对应的那个。

---

## 二、本分支做了什么

### 新增文件（`common/src/main/java/com/shilapi/xcertplay/`）

| 文件 | 作用 |
|---|---|
| `Leapmotor24WheelKeys.kt` | **方控**。接收 `com.leapmotor.steerwheelcontrol.media`，读 int extra `action`，只有三个有效值；并持有两条通道共用的去重窗口 |
| `Leapmotor24CarData.kt` | **车速与挡位（只读诊断）**。绑定 `com.leapmotor.carcontrol` 的 BinderPoolService，手工 `transact` 取属性，`Logcat` 里输出快照 |
| `Leapmotor24ConfigReceiver.kt` | **ADB 配置入口**。用于实车切换方控来源、手动触发一次车辆读取 |

### 改动的上游文件（2 个）

| 文件 | 改动 |
|---|---|
| `common/.../CarPlayHostActivity.kt` | 会话建立时挂载方控 + 车辆读取，两处销毁（含 `shutdown`）都卸载；`dispatchKeyEvent` 的 CarPlay 窗口按键分支接入去重 |
| `common/.../CarPlayMediaKeys.kt` | `CarPlayMediaCallback.onMediaButtonEvent`（Android KeyEvent 通路）接入去重 |
| `mobile/src/main/AndroidManifest.xml` | 注册 `Leapmotor24ConfigReceiver` |

---

## 三、方控

### 协议

```
action : com.leapmotor.steerwheelcontrol.media
extra  : action (int)

  1 → PREVIOUS (5)     上一首
  4 → NEXT (4)         下一首
  7 → PLAY_PAUSE (3)   播放 / 暂停
其它  → 忽略（音量、电话等归车机）
```

比 2021–2023 那套简单得多：那代是「4 类通道 × 38 个 action × 三种载荷（长度前缀二进制 JSON / JSON / int extra）」的广撒网，这一代是一个 action、一个 int、三个有效值。

### 双通道去重（本代的核心问题）

2024+ 上**同一次物理按键会同时产生两条事件**：

1. 车机自家的广播 `com.leapmotor.steerwheelcontrol.media`
2. 普通的 Android 媒体键 `KeyEvent`（走 `MediaSession`，或由 CarPlay 窗口直接认领）

2021–2023 只有第 1 条 —— 这正是当初整个适配的起因。这一代若不加处理，一次按键会「切两首」或「暂停后又立刻播放」。

处理方式：`Leapmotor24WheelKeys.claim()` 持有一个 350 ms 的窗口，两条通道共用；先到者放行并记录 `(按钮, 时刻)`，窗口内同按钮的第二次一律丢弃。窗口比 21–23 的 250 ms 宽，因为两条路径的时延差比单通道内部的抖动大得多。

### 三档来源（ADB 可调）

车机之间存在差异：有的只发广播，有的只发 KeyEvent，有的双发。默认 `auto`（合并去重），可用 ADB 改：

```bash
# auto（默认，两路合并去重） / factory（只认车机广播） / android（只认 KeyEvent）
adb shell am broadcast -a com.shihab.diplay.leapmotor24.CONFIGURE --es wheel_source factory

# 手动触发一次车辆读取
adb shell am broadcast -a com.shihab.diplay.leapmotor24.CONFIGURE --ez read_vehicle true

# 看结果
adb logcat -s DiPlay-Leapmotor24
```

设置落在 SharedPreferences `xcertplay_airplay` 的 `leapmotor24_wheel_source`，卸载重装会回到 `auto`。

---

## 四、车速与挡位（只读诊断）

### 通路

```
bindService(
  Intent("com.leapmotor.carcontrol.services.BINDER_POOL")
    .setComponent("com.leapmotor.carcontrol",
                  "com.leapmotor.carcontrol.services.BinderPoolService"))
  → 得到 IBinderPool
  → transact(1, writeInt(端点 1 或 2)) → readStrongBinder()   // 取具体端点
  → 按描述符决定 code：
       com.leapmotor.carcontrol.ICarControl          → transact 2
       com.leapmotor.carcontrol.ICarControlExternal  → transact 1
     data.writeInterfaceToken(描述符) + data.writeInt(属性)
  → reply.readString()
```

属性只有两个：**`0xFBE`（4022）** 与 **`0x145B`（5211）**，分别是车速和挡位，**但哪个对应哪个尚不能确定**（接口本身没标注，回也不带单位/名称），因此日志里按 id 原样输出，不做猜测。

返回值可能是 `{"value": …}`、`{"data": …}` 或裸数值字符串，三种都要能解析 —— `Leapmotor24CarData.numericOf()` 处理这回事。

### 用途严格限定为诊断

与 2021–2023 最大的不同：那代的挡位**驱动行为**（挂 R 挡真的会暂停 CarPlay，让位给倒车影像与雷达提示音）；本代只是一次快照，代码里明确写明：

> 仅为本次读取服务，**不作为当前挡位或驻车授权**。

会话建立时会读一次，结果进 `Logcat`；也可以上面那条广播随时手动触发。绑定失败、超时（5 s）、拿到空 binder 都会如实记录，不会静默。

---

## 五、这一代没有做（及原因）

| 项 | 2021–2023 分支有 | 本分支 |
|---|---|---|
| 挡位驱动行为（倒挡暂停 CarPlay） | 有 | **无**。车端 AIDL 的语义尚未确认，不做任何影响驾驶的联动 |
| 投仪表（1920×720 → 右侧 720×720） | 有（写死几何 + 平台指纹） | **无**。上一代那套是针对 8155 `msmnile_gvmq` 平台的硬编码几何，对这一代不适用；这一代应由上游 0.2.18 自带的副屏/仪表通路承担 |
| 音频 `AudioAttributes` 候选 | 无 | **无**。上游 0.2.18 的音频焦点策略与之不同，套用会改变焦点行为，需实车评估后再定 |
| 副屏枚举 + 区域框选校准 | 无 | **无**。是一整套 UI 与配置系统，与上游现有仪表通路重复度高，成本远大于收益 |
| CAN 心跳（1 s `upgradeAsk`） | 有 | **无**。这一代没有 `carcanserver` 心跳 |

---

## 六、适配车型范围

本分支的适配目标是 **零跑 2024–2027 款全部车型**。

---

## 七、待实车确认

1. 属性 `4022` 与 `5211` 分别是车速还是挡位（当前实现按 id 原样输出，不猜）。
2. 挡位的取值枚举（2021–2023 是 `1=R / 2=N / 3=D`；这一代未见映射表，返回值可能是文本档位名）。
3. 具体车型上两条方控通道是否都存在 —— 若某一车型只有一条，`--es wheel_source` 可以当场切换验证。
