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
> | 基线版本 | 上游 `v0.2.17` |
>
> 如果零跑适配部分对你没用，请直接去上游仓库 — **功能、兼容性、安全性、更新都由上游负责**，请给上游点 Star 并在上游提 issue。
> 零跑相关问题请发在本仓库，但**不要**拿本分支去打扰上游作者。

---

## 1. 这个分支做了什么

零跑车机与 BYD 车机的物理层完全不同：**方向盘按键不会以 Android `KeyEvent` 的形式投递给第三方 App**，而是
CAN 总线 → 仪表控制器（ICU）→ 广播 的链路。上游 DiPlay 的方控建立在 KeyEvent / 无障碍服务之上，在零跑车机上收不到任何东西。

同理，零跑 C11 的仪表是一块 **1920 × 720** 的长条屏，而 CarPlay 的仪表流是正方形的，映射规则也与 BYD DiLink 5.1 不同。

本分支补齐的就是这三层：**方控、投仪表、挡位联动**。除此之外，CarPlay 本体（有线/无线连接、编解码、音频、
界面、更新检查等）**一行未改**，与上游完全一致。

### 适配范围

| 车型 | 状态 |
|---|---|
| 零跑 C11（21–23 款） | 主要目标车型；仪表 1920×720 布局按此推导 |
| 零跑 C01 / C10 / T03 / B01 / B10 | **未验证**。方控广播通道是"广撒网"式注册，理论上可能命中，但没人在这几款车上试过 |
| BYD 及其他品牌 | 请直接用上游；本分支不影响上游能力，但对你是多余的 |

---

## 2. 零跑适配包含什么

### 新增文件

全部位于 `common/src/main/java/com/shilapi/xcertplay/`：

| 文件 | 作用 |
|---|---|
| `LeapmotorMediaKeys.kt` | **方控核心**。内含 `LeapmotorMediaProtocol`：4 类广播通道共 38 个 action、三种载荷解析（二进制长度前缀 JSON / JSON / int extra），250 ms 去重窗口后映射为 CarPlay 媒体键 |
| `LeapmotorGearMonitor.kt` | **挡位联动**。监听 `leapmotor.cantoapp.update` 的 int extra `1010105`（1=R / 2=N / 3=D），挂倒挡自动暂停 CarPlay 让位给倒车影像与雷达提示音，退出恢复；并维持对 `com.leapmotor.carcanserver` 的 1 秒心跳 |
| `LeapmotorClusterLayout.kt` | **投仪表**。用 `Build.FINGERPRINT` 含 `qti/msmnile_gvmq` 识别零跑车机平台，把 1920×720 仪表面板换算成右侧 **720×720** 正方形视口（`left = 1920-720 = 1200`，`top = 0`） |
| `ClusterMapViewport.kt` | 抽象的视口数据类（完整改造路线才用得上，当前路径未引用） |
| `LearnedWheelKeys.kt` | **过渡桩**。原闭源构建里的"方控按键自定义学习"是一整套 Capture→存储→UI 实现（约 549 行），不属于上游，其源码未随闭源构建发布，故暂未移植；此桩保证编译通过并保证方控走通用映射。**补齐真实实现后直接删除此文件** |

### 改动的上游文件（3 处，共 47 行）

| 文件 | 改动 |
|---|---|
| `common/.../CarPlayHostActivity.kt` | 会话建立时挂载方控 + 挡位监听，两处会话销毁处卸载（**必须成对，漏一处会导致重连后重复按键**） |
| `common/.../ClusterMapPresentation.kt` | `videoParams` 增加零跑分支；DiLink 判定失败后才走零跑分支，BYD 行为不变 |
| `shared/.../airplay/CarPlayMediaButton.kt` | 追加 `forLeapmotorAction()` / `isWheelAction()`；二者覆盖范围**故意不一致**（后者排除 `play`/`pause`/`stop`），否则收音机与语音下发的指令会被误当成方控 |

技术细节、推导过程与逐条证据见 **[`docs/LEAPMOTOR.md`](docs/LEAPMOTOR.md)**，
逆向方法论见 **[`docs/LEAPMOTOR-ANALYSIS.md`](docs/LEAPMOTOR-ANALYSIS.md)**。

---

## 3. 诚实的状态说明（重要）

本适配**不是在车上逐条联调出来的**，而是从一份第三方闭源构建（基于上游 v0.2.15 的 C11 定制版）逆向还原并重写的。
功能边界与协议细节有证据支撑，但**没有在实车上被本人验证过**：

- ✅ 改动的 3 个上游文件**落地前逐行核对过 API**（`CarPlayVideo.onMediaKey`、`CarPlayController.sendMediaButton`、
  `CarPlayMediaButton` 常量均存在，且在 v0.2.15→v0.2.17 之间未变更）
- ⚠️ **本仓库的完整 Gradle 构建尚未编译验证通过**，请先按 [`docs/BUILD.md`](docs/BUILD.md) 自行构建
- ⚠️ 以下几项**必须实车确认**，请勿假设可用：
  1. `ro.build.fingerprint` 是否真的包含 `qti/msmnile_gvmq`（决定投仪表分支会不会命中）
  2. 你的固件实际发送的是 38 个 action 中的哪一个（实车 `logcat` 看 tag `DiPlay-MediaKeys`）
  3. 同一物理键是否被多个通道重复投递 → 表现为"按一次切两首歌"
  4. HUD 转向指引依赖上游的 BYD SOME/IP（`com.ts.car.someip.*`），在零跑上大概率绑定失败，**应视为不可用**

上车前先跑这条命令确认会不会命中零跑分支：

```bash
adb shell getprop ro.build.fingerprint | grep msmnile_gvmq
```

有输出 = 会命中；没有输出 = 你的车机不适用本分支的投仪表部分。

---

## 4. 构建与安装

构建、安装、首次连接的流程与上游**完全一致**，请照上游文档做：
[`docs/BUILD.md`](docs/BUILD.md) · [`docs/INSTALL.md`](docs/INSTALL.md) · [`docs/CONNECTION_SETUP.md`](docs/CONNECTION_SETUP.md)

```bash
./gradlew :mobile:assembleRelease   # 或 :automotive:assembleRelease，视车机形态
adb install -r mobile/build/outputs/apk/release/*.apk
```

上游的原始免责声明同样适用于本分支，这里只摘最关键的一条：
**这不是 Apple 认证产品。** APK 内置的是从公开 Carlinkit 固件中提取的实验性配件身份，不是为本项目申请的新 MFi 身份，
其内置私钥可被提取；未来 iOS 更新后的可用性、不同车机的可靠性均无保证。完整声明见上游 README。

---

## 5. 与上游保持同步

```bash
git remote add upstream https://github.com/shihabal3amri/DiPlay.git   # 只需一次
bash scripts/sync-upstream.sh                                          # 抓取上游改动并报告冲突文件
```

冲突通常只会落在那 3 个被改动的上游文件上，按 `docs/LEAPMOTOR.md` 里的语义锚点重新贴一遍即可。

> **已知差异 —— CI**：本仓库历史中**不含**上游的 `.github/workflows/`（3 个 Actions 文件）。
> 建仓时所用的 GitHub 令牌没有 `workflow` 权限，GitHub 会拒绝任何"新增/修改 workflow 文件"的推送，
> 导致整段历史一个字节都推不上去。这是推送环境的限制，**并非有意改动**。
> 需要 CI 就从上游把那 3 个文件拷回来，用带 `workflow` 权限的令牌推送。

上游的原始 README 已完整保留在 [`docs/upstream/README.md`](docs/upstream/README.md) 与
[`docs/upstream/README.zh-CN.md`](docs/upstream/README.zh-CN.md)。

---

## 6. 许可与归属

- 上游代码版权归原作者所有，许可证 **GNU General Public License v3.0**（见 [`LICENSE`](LICENSE)，未作任何修改）。
- 本分支的零跑适配补丁按 GPL-3.0 同协议发布，作者 [Wh1t3co1azZ](https://github.com/Wh1t3co1azZ)。
- 适配补丁中**不包含**任何零跑私有资源（无图标、无固件二进制、无认证文件）；协议常量由实车固件行为逆向得出。
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
> | Base version | Upstream `v0.2.17` |
>
> If you do not own a Leapmotor, use upstream instead: features, compatibility, security and updates are all
> handled there — star it and file issues there. Leapmotor-specific questions belong here, and please do **not**
> take this branch to the upstream author.

---

## 1. What this branch does

A Leapmotor head unit is physically different from a BYD one: **steering-wheel keys never reach third-party
apps as Android `KeyEvent`s**. They travel CAN → instrument cluster (ICU) → broadcast. Upstream DiPlay builds
its wheel-key support on key events and an accessibility service, so on Leapmotor firmware it receives nothing.

Likewise, the Leapmotor C11 instrument panel is a **1920 × 720** strip while the CarPlay dashboard stream is
square, and the mapping rules differ from BYD DiLink 5.1.

This branch adds exactly those three layers: **wheel keys, cluster projection and gear handling**. CarPlay
itself — wired/wireless connection, codecs, audio, UI, update checks — is **untouched** and identical to upstream.

### Vehicle scope

| Model | Status |
|---|---|
| Leapmotor C11 (2021–2023) | Primary target; the 1920×720 cluster geometry is derived from this model |
| Leapmotor C01 / C10 / T03 / B01 / B10 | **Unverified.** The broadcast channels are registered defensively across every reported spelling, so keys may land — nobody has tested these models |
| BYD and other brands | Use upstream; this branch neither helps nor hinders, and adds nothing for you |

---

## 2. What the Leapmotor part contains

### New files — all under `common/src/main/java/com/shilapi/xcertplay/`

| File | Purpose |
|---|---|
| `LeapmotorMediaKeys.kt` | **Wheel keys.** Contains `LeapmotorMediaProtocol`: four channel families, 38 actions, three payload shapes (length-prefixed binary JSON / JSON / int extra), a 250 ms de-duplication window, then mapping to CarPlay media buttons |
| `LeapmotorGearMonitor.kt` | **Gear.** Listens to `leapmotor.cantoapp.update` int extra `1010105` (1=R / 2=N / 3=D); pauses CarPlay while reversing so the reversing camera and parking chimes own audio, resumes on exit; keeps a 1 s heartbeat toward `com.leapmotor.carcanserver` |
| `LeapmotorClusterLayout.kt` | **Cluster projection.** Detects the platform via `Build.FINGERPRINT` containing `qti/msmnile_gvmq`, and derives a right-hand **720×720** square viewport from the 1920×720 panel (`left = 1920-720 = 1200`, `top = 0`) |
| `ClusterMapViewport.kt` | Abstract viewport data class — unused on the current path, kept for the full-rewrite route |
| `LearnedWheelKeys.kt` | **Transitional stub.** The original closed build ships a whole key-learning flow (capture → storage → UI, ≈549 lines) that is not part of upstream and whose source was never distributed, so it has not been ported. The stub keeps the tree compiling and guarantees keys take the generic mapping. **Delete this file once the real implementation lands** |

### Upstream files modified (3 files, 47 lines)

| File | Change |
|---|---|
| `common/.../CarPlayHostActivity.kt` | Attach keys + gear monitor on session start; detach at both teardown sites (**both** — missing one causes doubled presses after reconnect) |
| `common/.../ClusterMapPresentation.kt` | Adds the Leapmotor case to `videoParams`, taken only when DiLink planning fails; BYD behaviour is unchanged |
| `shared/.../airplay/CarPlayMediaButton.kt` | Adds `forLeapmotorAction()` / `isWheelAction()`. Their coverage is **deliberately unequal** (the latter excludes `play`/`pause`/`stop`), otherwise radio and voice commands would be hijacked as wheel presses |

Detailed reasoning and evidence are in [`docs/LEAPMOTOR.md`](docs/LEAPMOTOR.md); the reverse-engineering
method is in [`docs/LEAPMOTOR-ANALYSIS.md`](docs/LEAPMOTOR-ANALYSIS.md).

---

## 3. Honest status (please read before installing)

This adaptation was **not** developed and tuned in a car. It was reconstructed by reverse-engineering a third-party
closed build (a C11 custom version based on upstream v0.2.15) and rewriting it. The boundaries and protocol details
are evidence-backed, but nothing here has been validated by me on a vehicle:

- ✅ Every touched upstream API was checked line by line before landing
  (`CarPlayVideo.onMediaKey`, `CarPlayController.sendMediaButton`, the `CarPlayMediaButton` constants are all
  present and unchanged between v0.2.15 and v0.2.17)
- ⚠️ The Gradle build of **this repository has not been compiled end-to-end yet** — build it yourself per
  [`docs/BUILD.md`](docs/BUILD.md)
- ⚠️ These items **must** be confirmed on your car; do not assume they work:
  1. Whether `ro.build.fingerprint` actually contains `qti/msmnile_gvmq` (decides if the cluster branch triggers)
  2. Which of the 38 actions your firmware really sends (watch `logcat` for tag `DiPlay-MediaKeys`)
  3. Whether one physical key is delivered on several channels → symptom: "one press skips two tracks"
  4. HUD turn guidance depends on upstream's BYD SOME/IP integration (`com.ts.car.someip.*`); on Leapmotor it will
     most likely fail to bind and should be treated as **unavailable**

Check the cluster branch before installing:

```bash
adb shell getprop ro.build.fingerprint | grep msmnile_gvmq
```

Output = the branch applies. No output = the cluster part of this branch is not for your head unit.

---

## 4. Build and install

Build, install and first connection steps are **identical to upstream** — follow the upstream documents:
[`docs/BUILD.md`](docs/BUILD.md) · [`docs/INSTALL.md`](docs/INSTALL.md) · [`docs/CONNECTION_SETUP.md`](docs/CONNECTION_SETUP.md)

```bash
./gradlew :mobile:assembleRelease   # or :automotive:assembleRelease, depending on your form factor
adb install -r mobile/build/outputs/apk/release/*.apk
```

The upstream disclaimer applies here in full; the most important part is repeated:
**This is not an Apple-certified product.** The APK bundles an experimental accessory identity recovered from public
Carlinkit firmware, not a newly provisioned MFi identity. A bundled private key is extractable, and acceptance after
future iOS updates, reliability across head units and suitability of that identity for general distribution are
unresolved. See the upstream README for the full statement.

---

## 5. Keeping up with upstream

```bash
git remote add upstream https://github.com/shihabal3amri/DiPlay.git   # once
bash scripts/sync-upstream.sh                                          # fetch and report conflicts
```

Conflicts, when they occur, are normally limited to the three modified upstream files; re-apply them using the
semantic anchors listed in `docs/LEAPMOTOR.md`.

> **Known difference — CI:** this repository's history **does not contain** upstream's `.github/workflows/`
> (three Actions files). The GitHub token used to create it lacks the `workflow` scope, and GitHub rejects any
> push that adds or modifies workflow files — which blocked pushing the history at all. This is a limitation of
> the push environment, **not an intentional change**. To get CI back, copy those three files from upstream and
> push them with a token that has the `workflow` scope.

The upstream README is preserved verbatim at [`docs/upstream/README.md`](docs/upstream/README.md) and
[`docs/upstream/README.zh-CN.md`](docs/upstream/README.zh-CN.md).

---

## 6. Licensing and attribution

- Upstream code remains the copyright of its author, licensed **GNU GPL v3.0** ([`LICENSE`](LICENSE), unmodified).
- The Leapmotor patches are published under the same GPL-3.0, by [Wh1t3co1azZ](https://github.com/Wh1t3co1azZ).
- The patches contain **no proprietary Leapmotor assets** — no artwork, firmware binaries or authentication files.
  Protocol constants were derived from observed vehicle firmware behaviour.
- Any head-unit malfunction, data loss or warranty dispute caused by using this is **entirely at your own risk**.
