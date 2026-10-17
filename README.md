# DiPlay — 零跑（Leapmotor）2021–2023 适配分支

**简体中文 · [English below](#english)**

> ## 来源与版权声明（请先读完）
>
> **本仓库不是本人从零开发的项目。** 全部源码来自上游 **[shihabal3amri/DiPlay](https://github.com/shihabal3amri/DiPlay)**（GPL-3.0），
> 本人 **没有参与上游开发**，也不是上游的维护者。
>
> 本仓库 = **上游源码原样镜像** + **本人新增的零跑（Leapmotor）适配补丁**，两者在同一分支上，便于跟随上游升级。
>
> | 项目 | 说明 |
> |---|---|
> | 上游仓库 | https://github.com/shihabal3amri/DiPlay |
> | 上游许可证 | GNU GPL v3.0（见 [`LICENSE`](LICENSE)，未作任何修改） |
> | 本仓库许可证 | 同为 **GPL-3.0**（适配补丁随上游同 License 发布） |
> | 本人实际贡献范围 | **仅限零跑适配**（见下表），其余全部是上游作者与贡献者的成果 |
> | 基线版本 | 上游 `v0.2.20` |
>
> 如果零跑适配部分对你没用，请直接去上游仓库 — **功能、兼容性、安全性、更新都由上游负责**，请给上游点 Star 并在上游提 issue。
> 零跑相关问题请发在本仓库，但**不要**拿本分支去打扰上游作者。

---

## 1. 先选对你的 APK

零跑在 2024 款换过车机软件架构，两代车机给第三方 App 的接口**不是一套东西**，因此每个版本发布**两个 APK**：

| 产物 | 分支 | 适用车型 |
|---|---|---|
| **`Diplay-V<上游版本>-Leap21_23.apk`** | **本分支** | 零跑 **2021–2023** 款全部车型 |
| `Diplay-V<上游版本>-Leap24_27.apk` | `leapmotor-24-27` | 零跑 **2024–2027** 款全部车型 |

两个 APK 的**包名、versionName、versionCode 都沿用上游**（`com.shihab.diplay` / `0.2.20` / `39`），所以一台车机上只能装其中一个，**按车型年份挑对应的那个**。装错版本方控不会有反应。

---

## 2. 这个分支做了什么

零跑车机与 BYD 车机的物理层完全不同：**方向盘按键不会以 Android `KeyEvent` 的形式投递给第三方 App**，而是走
CAN 总线 → 仪表控制器（ICU）→ 广播 的链路。上游 DiPlay 的方控建立在 KeyEvent / 无障碍服务之上，在零跑车机上收不到任何东西。

同理，零跑的仪表是一块 **1920 × 720** 的长条屏，而 CarPlay 的仪表流是正方形的，映射规则也与 BYD DiLink 5.1 不同。

本分支补齐的就是这三层：**方控、投仪表、挡位联动**。除此之外，CarPlay 本体（有线/无线连接、编解码、音频、
界面、更新检查等）**一行未改**，与上游完全一致。

### 适配车型范围

本分支的适配目标是 **零跑 2021–2023 款全部车型**。

方控的广播通道是按"广撒网"方式注册的，覆盖已知固件里出现过的全部拼写形式，因此在目标范围内的不同车型、
不同固件版本上具备较高的命中率。投仪表部分依赖车机平台指纹识别，命中后才会生效。

---

## 3. 零跑适配包含什么

### 新增文件

全部位于 `common/src/main/java/com/shilapi/xcertplay/`：

| 文件 | 作用 |
|---|---|
| `LeapmotorMediaKeys.kt` | **方控核心**。内含 `LeapmotorMediaProtocol`：4 类广播通道共 38 个 action、三种载荷解析（二进制长度前缀 JSON / JSON / int extra），250 ms 去重窗口后映射为 CarPlay 媒体键 |
| `LeapmotorGearMonitor.kt` | **挡位联动**。监听 `leapmotor.cantoapp.update` 的 int extra `1010105`（1=R / 2=N / 3=D），挂倒挡自动暂停 CarPlay 让位给倒车影像与雷达提示音，退出恢复；并维持对 `com.leapmotor.carcanserver` 的 1 秒心跳 |
| `LeapmotorClusterLayout.kt` | **投仪表**。用 `Build.FINGERPRINT` 含 `qti/msmnile_gvmq` 识别零跑车机平台，把 1920×720 仪表面板换算成右侧 **720×720** 正方形视口（`left = 1920-720 = 1200`，`top = 0`） |
| `ClusterMapViewport.kt` | 抽象的视口数据类，供完整改造路线使用 |
| `LearnedWheelKeys.kt` | **过渡桩**。"方控按键自定义学习"（Capture → 存储 → UI）是一整套独立实现，不属于上游，目前尚未移植；此桩保证编译通过并保证方控走通用映射。**补齐真实实现后直接删除此文件** |

### 改动的上游文件（3 个，约 50 行）

| 文件 | 改动 |
|---|---|
| `common/.../CarPlayHostActivity.kt` | 会话建立时挂载方控 + 挡位监听，两处会话销毁处卸载（**必须成对，漏一处会导致重连后重复按键**） |
| `common/.../ClusterMapPresentation.kt` | `videoParams` 增加零跑分支；DiLink 判定失败后才走零跑分支，BYD 行为不变 |
| `shared/.../airplay/CarPlayMediaButton.kt` | 追加 `forLeapmotorAction()` / `isWheelAction()`；二者覆盖范围**故意不一致**（后者排除 `play`/`pause`/`stop`），否则收音机与语音下发的指令会被误当成方控 |

技术细节、接入方式与各模块的参数说明见 **[`docs/LEAPMOTOR.md`](docs/LEAPMOTOR.md)**。

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

此外还需要**本地 CarPlay 配件身份**（同样不含在仓库里，做法见上游 `docs/BUILD.md`）：

```bash
# 该目录下放 offline-mfi/identity.pk8 与 offline-mfi/certificate.p7b
export DIPLAY_AUTH_ASSETS_DIR=/path/to/runtime-assets
```

不设置它构建也能通过，但打出的包装到车机上会提示**「无法加载 CarPlay 认证」**，无法连接 iPhone。

本分支的发布产物名为 **`Diplay-V<上游版本>-Leap21_23.apk`**，当前为 **`Diplay-V0.2.20-Leap21_23.apk`**。
包名、`versionName`、`versionCode` **全部沿用上游**（`com.shihab.diplay` / `0.2.20` / `39`），本分支不改动。

上游的原始免责声明同样适用于本分支，这里只摘最关键的一条：
**这不是 Apple 认证产品。** APK 内置的是从公开 Carlinkit 固件中提取的实验性配件身份，不是为本项目申请的新 MFi 身份，
其内置私钥可被提取；未来 iOS 更新后的可用性、不同车机的可靠性均无保证。完整声明见上游 README。

---

## 5. 版本与升级策略

| 项 | 值 |
|---|---|
| 当前发布版本 | **`v0.2.20-Leap`** |
| 对应上游基线 | `v0.2.20` |
| 升级方式 | 跟随上游。上游发布新版本后，两个分支各自重打补丁，产出 `Diplay-V<上游版本>-Leap21_23.apk` 与 `Diplay-V<上游版本>-Leap24_27.apk` |

本分支**不会**在 CarPlay 本体上做任何独立演进 —— 上游每发布一版，这里就基于该版本重打一版零跑补丁。

历次发布记录见 **[`docs/RELEASES.md`](docs/RELEASES.md)**。

---

## 6. 与上游保持同步

```bash
git remote add upstream https://github.com/shihabal3amri/DiPlay.git   # 只需一次
bash scripts/sync-upstream.sh                                          # 抓取上游改动并报告差异
```

上游每个版本之间改动面很大（v0.2.18 → v0.2.20 有 245 个文件），直接 `git merge` 会撞上大量无关冲突。
本仓库的做法是**整棵树换成上游新版本，再把零跑那一小摞改动按语义锚点重新贴回去**；
脚本只负责取上游与报差异，重贴动作照 [`docs/LEAPMOTOR.md`](docs/LEAPMOTOR.md) 第 5 节的描述手工完成。

> **已知差异 —— CI**：本仓库不含上游的 `.github/workflows/`（3 个 Actions 文件），因此没有自动构建。
> 推这些文件需要带 `workflow` 权限的令牌；拿到后从上游把这 3 个文件拷回来即可。

上游的原始 README 已完整保留在 [`docs/upstream/README.md`](docs/upstream/README.md) 与
[`docs/upstream/README.zh-CN.md`](docs/upstream/README.zh-CN.md)。

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
> | Scope of my contribution | **Leapmotor adaptation only** (see below). Everything else is the work of the upstream author and contributors |
> | Base version | Upstream `v0.2.20` |
>
> If the Leapmotor part is of no use to you, use upstream directly — it owns the features, compatibility,
> security and updates, and deserves your star and your issues. Leapmotor-specific questions belong here,
> and please do **not** take this branch to the upstream author.

---

## 1. Pick the right APK first

Leapmotor changed its head-unit software architecture for the 2024 model year. The two generations expose
**different interfaces to third-party apps**, so every release ships **two APKs**:

| Artifact | Branch | Models |
|---|---|---|
| **`Diplay-V<upstream version>-Leap21_23.apk`** | **this branch** | Leapmotor **2021–2023** model years |
| `Diplay-V<upstream version>-Leap24_27.apk` | `leapmotor-24-27` | Leapmotor **2024–2027** model years |

Both keep **upstream's** package name, `versionName` and `versionCode` (`com.shihab.diplay` / `0.2.20` / `39`),
so only one can be installed on a head unit — **pick the one matching your model year**. Installing the wrong
one means the wheel keys simply do nothing.

---

## 2. What this branch does

A Leapmotor head unit is physically different from a BYD one: **steering-wheel keys never reach third-party
apps as Android `KeyEvent`s**. They travel CAN → instrument cluster (ICU) → broadcast. Upstream DiPlay builds
its wheel-key support on key events and an accessibility service, so on Leapmotor firmware it receives nothing.

Likewise, the Leapmotor instrument panel is a **1920 × 720** strip while the CarPlay dashboard stream is
square, and the mapping rules differ from BYD DiLink 5.1.

This branch adds exactly those three layers: **wheel keys, cluster projection and gear handling**. CarPlay
itself — wired/wireless connection, codecs, audio, UI, update checks — is **untouched** and identical to upstream.

### Vehicle scope

The adaptation target of this branch is **all Leapmotor 2021–2023 model years**.

The wheel-key channels are registered defensively across every action spelling seen in known firmware, which
gives good coverage across models and firmware versions inside the target range. Cluster projection is gated on
a platform fingerprint and only engages when it matches.

---

## 3. What the Leapmotor part contains

### New files — all under `common/src/main/java/com/shilapi/xcertplay/`

| File | Purpose |
|---|---|
| `LeapmotorMediaKeys.kt` | **Wheel keys.** Contains `LeapmotorMediaProtocol`: four channel families, 38 actions, three payload shapes (length-prefixed binary JSON / JSON / int extra), a 250 ms de-duplication window, then mapping to CarPlay media buttons |
| `LeapmotorGearMonitor.kt` | **Gear.** Listens to `leapmotor.cantoapp.update` int extra `1010105` (1=R / 2=N / 3=D); pauses CarPlay while reversing so the reversing camera and parking chimes own audio, resumes on exit; keeps a 1 s heartbeat toward `com.leapmotor.carcanserver` |
| `LeapmotorClusterLayout.kt` | **Cluster projection.** Detects the platform via `Build.FINGERPRINT` containing `qti/msmnile_gvmq`, and derives a right-hand **720×720** square viewport from the 1920×720 panel (`left = 1920-720 = 1200`, `top = 0`) |
| `ClusterMapViewport.kt` | Abstract viewport data class for the full-rewrite route |
| `LearnedWheelKeys.kt` | **Transitional stub.** Key learning (capture → storage → UI) is a self-contained feature that is not part of upstream and has not been ported yet. The stub keeps the tree compiling and guarantees keys take the generic mapping. **Delete this file once the real implementation lands** |

### Upstream files modified (3 files, about 50 lines)

| File | Change |
|---|---|
| `common/.../CarPlayHostActivity.kt` | Attach keys + gear monitor on session start; detach at both teardown sites (**both** — missing one causes doubled presses after reconnect) |
| `common/.../ClusterMapPresentation.kt` | Adds the Leapmotor case to `videoParams`, taken only when DiLink planning fails; BYD behaviour is unchanged |
| `shared/.../airplay/CarPlayMediaButton.kt` | Adds `forLeapmotorAction()` / `isWheelAction()`. Their coverage is **deliberately unequal** (the latter excludes `play`/`pause`/`stop`), otherwise radio and voice commands would be hijacked as wheel presses |

Reasoning, integration points and per-module parameters are documented in [`docs/LEAPMOTOR.md`](docs/LEAPMOTOR.md).

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

A **local CarPlay accessory identity** is also required (also never distributed with the sources;
see upstream `docs/BUILD.md`):

```bash
# this directory must contain offline-mfi/identity.pk8 and offline-mfi/certificate.p7b
export DIPLAY_AUTH_ASSETS_DIR=/path/to/runtime-assets
```

Without it the build still succeeds, but the APK cannot connect an iPhone — it reports that
CarPlay authentication could not be loaded.

Release artifacts are named **`Diplay-V<upstream version>-Leap21_23.apk`**, currently **`Diplay-V0.2.20-Leap21_23.apk`**.
Package name, `versionName` and `versionCode` are **all upstream's** (`com.shihab.diplay` / `0.2.20` / `39`);
this branch does not change them.

The upstream disclaimer applies here in full; the most important part is repeated:
**This is not an Apple-certified product.** The APK bundles an experimental accessory identity recovered from public
Carlinkit firmware, not a newly provisioned MFi identity. A bundled private key is extractable, and acceptance after
future iOS updates, reliability across head units and suitability of that identity for general distribution are
unresolved. See the upstream README for the full statement.

---

## 5. Versioning and upgrades

| Item | Value |
|---|---|
| Current release | **`v0.2.20-Leap`** |
| Upstream base | `v0.2.20` |
| Upgrade path | Track upstream. When upstream ships a new version, both branches are rebuilt, producing `Diplay-V<upstream version>-Leap21_23.apk` and `Diplay-V<upstream version>-Leap24_27.apk` |

This branch does **not** evolve CarPlay itself independently. Each upstream release gets rebuilt here with the
Leapmotor patches reapplied.

Release history lives in [`docs/RELEASES.md`](docs/RELEASES.md).

---

## 6. Keeping up with upstream

```bash
git remote add upstream https://github.com/shihabal3amri/DiPlay.git   # once
bash scripts/sync-upstream.sh                                          # fetch and report the difference
```

Upstream changes a lot between releases (v0.2.18 → v0.2.20 touched 245 files), so a plain `git merge` drowns in
unrelated conflicts. This repository instead **replaces the whole tracked tree with the new upstream version and
re-applies the small stack of Leapmotor hunks on top**, using the semantic anchors in
[`docs/LEAPMOTOR.md`](docs/LEAPMOTOR.md). The script only fetches and reports; the re-application itself is manual.

> **Known difference — CI:** this repository does not carry upstream's `.github/workflows/` (three Actions
> files), so there is no automated build. Pushing them requires a token with the `workflow` scope; copy them
> back from upstream once you have one.

The upstream README is preserved verbatim at [`docs/upstream/README.md`](docs/upstream/README.md) and
[`docs/upstream/README.zh-CN.md`](docs/upstream/README.zh-CN.md).

---

## 7. Licensing and attribution

- Upstream code remains the copyright of its author, licensed **GNU GPL v3.0** ([`LICENSE`](LICENSE), unmodified).
- The Leapmotor patches are published under the same GPL-3.0, by [Wh1t3co1azZ](https://github.com/Wh1t3co1azZ).
- The patches contain **no proprietary Leapmotor assets** — no artwork, firmware binaries or authentication files.
- Any head-unit malfunction, data loss or warranty dispute caused by using this is **entirely at your own risk**.
