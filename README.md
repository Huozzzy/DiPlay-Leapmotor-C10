# DiPlay — 零跑（Leapmotor）2024–2027 适配分支

**简体中文 · [English below](#english)**

> ## 来源与版权声明（请先读完）
>
> **本仓库不是本人从零开发的项目。** 全部源码来自上游 **[shihabal3amri/DiPlay](https://github.com/shihabal3amri/DiPlay)**（GPL-3.0），
> 本人 **没有参与上游开发**，也不是上游的维护者。
>
> 本仓库 = **上游源码原样镜像** + **本人新增的零跑适配补丁**，两者在同一分支上，便于跟随上游升级。
>
> | 项目 | 说明 |
> |---|---|
> | 上游仓库 | https://github.com/shihabal3amri/DiPlay |
> | 上游许可证 | GNU GPL v3.0（见 [`LICENSE`](LICENSE)，未作任何修改） |
> | 本仓库许可证 | 同为 **GPL-3.0**（适配补丁随上游同 License 发布） |
> | 本人实际贡献范围 | **仅限零跑适配**（见下表），其余全部是上游作者与贡献者的成果 |
> | 基线版本 | 上游 `v0.2.18` |
>
> 如果零跑适配部分对你没用，请直接去上游仓库 — **功能、兼容性、安全性、更新都由上游负责**，请给上游点 Star 并在上游提 issue。
> 零跑相关问题请发在本仓库，但**不要**拿本分支去打扰上游作者。

---

## 1. 先选对你的 APK

零跑在 2024 款换过车机软件架构，两代车机给第三方 App 的接口**不是一套东西**，因此每个版本发布**两个 APK**：

| 产物 | 分支 | 适用车型 |
|---|---|---|
| `Diplay-V<上游版本>-Leap21_23.apk` | `main` | 零跑 **2021–2023** 款全部车型 |
| **`Diplay-V<上游版本>-Leap24_27.apk`** | **本分支** | 零跑 **2024–2027** 款全部车型 |

两个 APK 的**包名、versionName、versionCode 都沿用上游**（`com.shihab.diplay` / `0.2.18` / `37`），所以一台车机上只能装其中一个，**按车型年份挑对应的那个**。

不要混用：这两代车机的数据通路完全不同（见 [`docs/LEAPMOTOR24.md`](docs/LEAPMOTOR24.md) 第一节），装错版本方控不会有反应。

---

## 2. 这个分支做了什么

2024 款起的零跑车机与 2021–2023 不是一个架构：车辆数据不再"广播推给 App"，而是提供一个标准服务让 App **主动查**；方向盘按键虽然仍是广播，但协议从"覆盖已知拼写"变成了一个 action、一个 int、三个有效值。

本分支补齐的是这一代的两件事：**方控**、**车速与挡位的只读诊断**。除此之外，CarPlay 本体（有线/无线连接、编解码、音频、界面、更新检查等）**一行未改**，与上游完全一致。

### 适配车型范围

| 车型 | 状态 |
|---|---|
| **零跑 2024–2027 款全部车型** | 本分支的适配目标范围 |
| 零跑 2021–2023 款 | **不适用**，请用 `Diplay-V<版本>-Leap21_23.apk` |
| BYD 及其它品牌车机 | **未做验证**，请使用上游仓库 |

### 这一代特有的难点：一次按键来两遍

2024+ 上**同一次物理按键会同时产生两条事件**：车机自家的广播，外加一条普通的 Android 媒体键 `KeyEvent`。2021–2023 只有前一条 —— 这正是当初整个适配的起因。若不处理，一次按键会"切两首"或"暂停后又立刻播放"。

本分支给两条通道设了**共用的 350 ms 去重窗口**，先到者放行。所以没有额外要求：装上即用。

---

## 3. 零跑适配包含什么

### 新增文件

全部位于 `common/src/main/java/com/shilapi/xcertplay/`：

| 文件 | 作用 |
|---|---|
| `Leapmotor24WheelKeys.kt` | **方控**。接收 `com.leapmotor.steerwheelcontrol.media` 的 int extra `action`（`1`→上一首、`4`→下一首、`7`→播放/暂停，其余忽略），并持有两条通道共用的去重窗口 |
| `Leapmotor24CarData.kt` | **车速与挡位（只读诊断）**。绑定 `com.leapmotor.carcontrol` 的 BinderPoolService，手工 Binder `transact` 读取属性 `4022` / `5211`，快照进 Logcat；**明确不用于任何挡位判断或驻车授权** |
| `Leapmotor24ConfigReceiver.kt` | ADB 配置入口：现场切换方控来源（`auto` / `factory` / `android`）、手动触发一次车辆读取 |

### 改动的上游文件

| 文件 | 改动 |
|---|---|
| `common/.../CarPlayHostActivity.kt` | 会话建立时挂载方控 + 车辆读取，两处会话销毁处卸载（**必须成对**）；`dispatchKeyEvent` 的 CarPlay 窗口按键分支接入去重 |
| `common/.../CarPlayMediaKeys.kt` | `CarPlayMediaCallback.onMediaButtonEvent`（Android KeyEvent 通路）接入去重 |
| `mobile/src/main/AndroidManifest.xml` | 注册 `Leapmotor24ConfigReceiver` |

技术细节、接口参数与"这一代没做什么"逐条见 **[`docs/LEAPMOTOR24.md`](docs/LEAPMOTOR24.md)**。

### 实车调试

```bash
adb logcat -s DiPlay-Leapmotor24

# 某车型若只有一条方控通道有效，可现场切换
adb shell am broadcast -a com.shihab.diplay.leapmotor24.CONFIGURE --es wheel_source factory
# 手动读一次车速与挡位
adb shell am broadcast -a com.shihab.diplay.leapmotor24.CONFIGURE --ez read_vehicle true
```

---

## 4. 构建与安装

构建、安装、首次连接的流程与上游**完全一致**，请照上游文档做：
[`docs/BUILD.md`](docs/BUILD.md) · [`docs/INSTALL.md`](docs/INSTALL.md) · [`docs/CONNECTION_SETUP.md`](docs/CONNECTION_SETUP.md)

```bash
./gradlew :mobile:assembleRelease   # 或 :automotive:assembleRelease，视车机形态
adb install -r mobile/build/outputs/apk/release/*.apk
```

`release` 构建会签名，签名信息通过环境变量传入（不含在仓库里）：

```bash
export ANDROID_KEYSTORE_PATH=/path/to/diplay-release.jks
export ANDROID_KEYSTORE_PASSWORD=<store password>
export ANDROID_KEY_ALIAS=diplay-leap
export ANDROID_KEY_PASSWORD=<key password>
```

本分支的发布产物名为 **`Diplay-V<上游版本>-Leap24_27.apk`**。

上游的原始免责声明同样适用于本分支，这里只摘最关键的一条：
**这不是 Apple 认证产品。** APK 内置的是从公开 Carlinkit 固件中提取的实验性配件身份，不是为本项目申请的新 MFi 身份，
其内置私钥可被提取；未来 iOS 更新后的可用性、不同车机的可靠性均无保证。完整声明见上游 README。

---

## 5. 版本与升级策略

| 项 | 值 |
|---|---|
| 当前发布版本 | **`v0.2.18-Leap`**（与 21–23 分支共用一个 Release） |
| 对应上游基线 | `v0.2.18` |
| 升级方式 | 跟随上游。上游发布新版本后，两个分支各自重打补丁，产出 `Diplay-V<上游版本>-Leap21_23.apk` 与 `Diplay-V<上游版本>-Leap24_27.apk` |

本分支**不会**在 CarPlay 本体上做任何独立演进 —— 上游每发布一版，这里就基于该版本重打一版零跑补丁。

---

## 6. 与上游保持同步

```bash
git remote add upstream https://github.com/shihabal3amri/DiPlay.git   # 只需一次
bash scripts/sync-upstream.sh                                          # 抓取上游改动并报告冲突文件
```

> **已知差异 —— CI**：本仓库不含上游的 `.github/workflows/`，因此没有自动构建。
> 需要的话从上游拷回来即可（推送需带 `workflow` 权限的令牌）。

上游的原始 README 归档在 [`docs/upstream/README.md`](docs/upstream/README.md)。

---

## 7. 许可与归属

- 上游代码版权归原作者所有，许可证 **GNU General Public License v3.0**（见 [`LICENSE`](LICENSE)，未作任何修改）。
- 本分支的零跑适配补丁按 GPL-3.0 同协议发布，作者 [Wh1t3co1azZ](https://github.com/Wh1t3co1azZ)。
- 适配补丁中**不包含**任何零跑私有资源（无图标、无固件二进制、无认证文件）。
- 由此带来的任何车机异常、数据损失或保修争议，**由使用者自行承担**。

---

<br>

# English

> ## Attribution & license notice (please read first)
>
> **This repository is not my own project.** All source code comes from upstream
> **[shihabal3amri/DiPlay](https://github.com/shihabal3amri/DiPlay)** (GPL-3.0). I am **not** an upstream
> developer or maintainer.
>
> This repository = **a faithful mirror of upstream** + **my Leapmotor adaptation patches**, kept on the
> same branch so upstream releases can be picked up.
>
> | Item | Detail |
> |---|---|
> | Upstream repository | https://github.com/shihabal3amri/DiPlay |
> | Upstream license | GNU GPL v3.0 (see [`LICENSE`](LICENSE), unmodified) |
> | License here | Same: **GPL-3.0** (the patches ship under the identical licence) |
> | Scope of my contribution | **Leapmotor adaptation only**. Everything else is the work of the upstream author and contributors |
> | Base version | Upstream `v0.2.18` |
>
> If you do not own a Leapmotor, use upstream instead: features, compatibility, security and updates are all
> handled there — star it and file issues there. Leapmotor-specific questions belong here, and please do **not**
> take this branch to the upstream author.

---

## 1. Pick the right APK first

Leapmotor changed its head-unit software architecture for the 2024 model year. The two generations expose
**different interfaces to third-party apps**, so every release ships **two APKs**:

| Artifact | Branch | Models |
|---|---|---|
| `Diplay-V<upstream version>-Leap21_23.apk` | `main` | Leapmotor **2021–2023** model years |
| **`Diplay-V<upstream version>-Leap24_27.apk`** | **this branch** | Leapmotor **2024–2027** model years |

Both keep **upstream's** package name, `versionName` and `versionCode` (`com.shihab.diplay` / `0.2.18` / `37`),
so only one can be installed on a head unit — **pick the one matching your model year**.

Do not mix them: the two generations move vehicle data over completely different paths (see section 1 of
`docs/LEAPMOTOR24.md`). Installing the wrong one means the wheel keys simply do nothing.

---

## 2. What this branch does

From 2024 the head unit no longer pushes CAN data out for anyone to listen to; it exposes a standard service
that an app queries. Steering-wheel keys are still broadcasts, but the protocol collapses from "cover every
known spelling" to one action, one integer and three meaningful values.

This branch adds this generation's two pieces: **wheel keys** and a **read-only vehicle data probe**. CarPlay
itself — wired/wireless connection, codecs, audio, UI, update checks — is **untouched** and identical to upstream.

### Vehicle scope

| Model | Status |
|---|---|
| **Leapmotor 2024–2027 model years** | Adaptation target range of this branch |
| Leapmotor 2021–2023 | **Not applicable** — use `Diplay-V<version>-Leap21_23.apk` |
| BYD and other brands | **Not validated** — use upstream |

### The quirk of this generation: every press arrives twice

On 2024+ cars one physical press produces **both** the head unit's own broadcast **and** an ordinary Android
media `KeyEvent`. The 2021–2023 cars only produced the former — which is why that adaptation had to exist at
all. Left alone, one press would skip two tracks, or pause and immediately resume.

Both routes therefore share a single **350 ms de-duplication window**, first one wins. There is nothing to set
up: it works once installed.

---

## 3. What the Leapmotor part contains

### New files — all under `common/src/main/java/com/shilapi/xcertplay/`

| File | Purpose |
|---|---|
| `Leapmotor24WheelKeys.kt` | **Wheel keys.** Receives `com.leapmotor.steerwheelcontrol.media`, reads the int extra `action` (`1` → previous, `4` → next, `7` → play/pause, everything else ignored), and owns the window shared by both routes |
| `Leapmotor24CarData.kt` | **Speed and gear, read-only.** Binds `com.leapmotor.carcontrol`'s binder pool service and reads properties `4022` / `5211` with a hand-rolled Binder transact; the snapshot goes to Logcat and is **explicitly not used as gear or parking authority** |
| `Leapmotor24ConfigReceiver.kt` | An `adb` entry point: switch the wheel source (`auto` / `factory` / `android`) in place, or trigger one vehicle read |

### Upstream files modified

| File | Change |
|---|---|
| `common/.../CarPlayHostActivity.kt` | Attach wheel keys + vehicle probe on session start, detach at **both** teardown sites; the CarPlay-window key branch in `dispatchKeyEvent` joins de-duplication |
| `common/.../CarPlayMediaKeys.kt` | `CarPlayMediaCallback.onMediaButtonEvent` (the Android key route) joins de-duplication |
| `mobile/src/main/AndroidManifest.xml` | Registers `Leapmotor24ConfigReceiver` |

Per-module parameters, including what this generation deliberately does *not* do, are in
[`docs/LEAPMOTOR24.md`](docs/LEAPMOTOR24.md).

### In-car tuning

```bash
adb logcat -s DiPlay-Leapmotor24

# if only one of the two key routes works on your model:
adb shell am broadcast -a com.shihab.diplay.leapmotor24.CONFIGURE --es wheel_source factory
# read speed and gear once
adb shell am broadcast -a com.shihab.diplay.leapmotor24.CONFIGURE --ez read_vehicle true
```

---

## 4. Build and install

Build, install and first connection steps are **identical to upstream** — follow the upstream documents:
[`docs/BUILD.md`](docs/BUILD.md) · [`docs/INSTALL.md`](docs/INSTALL.md) · [`docs/CONNECTION_SETUP.md`](docs/CONNECTION_SETUP.md)

```bash
./gradlew :mobile:assembleRelease   # or :automotive:assembleRelease, depending on your form factor
adb install -r mobile/build/outputs/apk/release/*.apk
```

The `release` variant is signed; credentials come from the environment (never stored in the repo):

```bash
export ANDROID_KEYSTORE_PATH=/path/to/diplay-release.jks
export ANDROID_KEYSTORE_PASSWORD=<store password>
export ANDROID_KEY_ALIAS=diplay-leap
export ANDROID_KEY_PASSWORD=<key password>
```

Release artifacts are named **`Diplay-V<upstream version>-Leap24_27.apk`**.

The upstream disclaimer applies here in full; the most important part is repeated:
**This is not an Apple-certified product.** The APK bundles an experimental accessory identity recovered from public
Carlinkit firmware, not a newly provisioned MFi identity. A bundled private key is extractable, and acceptance after
future iOS updates, reliability across head units and suitability of that identity for general distribution are
unresolved. See the upstream README for the full statement.

---

## 5. Versioning and upgrades

| Item | Value |
|---|---|
| Current release | **`v0.2.18-Leap`** (shared with the 21–23 branch) |
| Upstream base | `v0.2.18` |
| Upgrade path | Track upstream. When upstream ships a new version, both branches are rebuilt, producing `Diplay-V<upstream version>-Leap21_23.apk` and `Diplay-V<upstream version>-Leap24_27.apk` |

This branch does **not** evolve CarPlay itself independently. Each upstream release gets rebuilt here with the
Leapmotor patches reapplied.

---

## 6. Keeping up with upstream

```bash
git remote add upstream https://github.com/shihabal3amri/DiPlay.git   # once
bash scripts/sync-upstream.sh                                          # fetch and report conflicts
```

> **Known difference — CI:** this repository does not carry upstream's `.github/workflows/`, so there is no
> automated build. Copy them back from upstream if you want CI (pushing them requires a token with the
> `workflow` scope).

The upstream README is preserved at [`docs/upstream/README.md`](docs/upstream/README.md).

---

## 7. Licensing and attribution

- Upstream code remains the copyright of its author, licensed **GNU GPL v3.0** ([`LICENSE`](LICENSE), unmodified).
- The Leapmotor patches are published under the same GPL-3.0, by [Wh1t3co1azZ](https://github.com/Wh1t3co1azZ).
- The patches contain **no proprietary Leapmotor assets** — no artwork, firmware binaries or authentication files.
- Any head-unit malfunction, data loss or warranty dispute caused by using this is **entirely at your own risk**.
