# 发布记录 / Release history

本文件记录本仓库自身的发布。**CarPlay 本体的变更日志属于上游**，见上游仓库的 `CHANGELOG.md`——
本仓库跟随上游同步时会把它一并带过来，但其中的条目不是本仓库的工作。

同步方式、车型范围与零跑适配的技术细节见 [`LEAPMOTOR.md`](LEAPMOTOR.md)（2021–2023 分支）与
[`LEAPMOTOR24.md`](LEAPMOTOR24.md)（2024–2027 分支）。

---

## v0.2.20-Leap

| 项 | 值 |
|---|---|
| 上游基线 | `v0.2.20`（本次跨过 v0.2.19 与 v0.2.20 两个版本：178 个提交、245 个文件） |
| 包名 | `com.shihab.diplay`（沿用上游） |
| versionName / versionCode | `0.2.20` / `39`（沿用上游） |
| 产物 | `Diplay-V0.2.20-Leap21_23.apk` · `Diplay-V0.2.20-Leap24_27.apk` |
| 大小 | 各 18,482,233 B |
| SHA-256 | `18e3beca…`（21_23）· `ed81e7a6…`（24_27） |
| 签名 | V2 + V3，release keystore 不在仓库内 |

零跑适配部分**代码没有变化**，本次是把两代补丁原样贴到上游 v0.2.20 的树上：

- 会话建立时挂载 / 两处销毁时卸载（两代各自的监听器）
- 2021–2023：方控广播、投仪表视口、挡位联动
- 2024–2027：方控广播与 Android 媒体键的双通道去重、车辆数据只读诊断

同步过程中**有两处本地改动被丢弃**，因为上游在这两个版本里自己做了同样的事：

| 原来的本地改动 | 上游现状 |
|---|---|
| 连接提示文案改用英文串匹配（`englishMessage`） | 上游 v0.2.19 起已是 `englishMessage` |
| 关于页并入设置分类（`SettingsCategory.ABOUT`） | 上游 v0.2.19 起已是设置分类 |

还有一处上游行为变化值得留意：**上游 v0.2.18 起不再随源码分发 CarPlay 配件身份**，
改为构建时通过 `DIPLAY_AUTH_ASSETS_DIR` 显式注入（做法见上游 `docs/BUILD.md`）。
因此不注入就构建出来的 APK 装到车机上会提示「无法加载 CarPlay 认证」。

---

## v0.2.18-Leap

| 项 | 值 |
|---|---|
| 上游基线 | `v0.2.18` |
| 产物 | `Diplay-V0.2.18-Leap21_23.apk` · `Diplay-V0.2.18-Leap24_27.apk` |
| 大小 | 各 18,211,826 B |

首个按车型年份分两个 APK 的版本。

---

## v0.2.17-Leap

| 项 | 值 |
|---|---|
| 上游基线 | `v0.2.17` |
| 产物 | `Diplay-V0.2.17-Leap.apk` |

两代适配还未分家时的单包版本。

---

## Release history (English)

This file records **this repository's** releases only. The CarPlay changelog belongs to upstream
(`CHANGELOG.md`); it arrives with each sync but its entries are not work done here.

## v0.2.20-Leap

| Item | Value |
|---|---|
| Upstream base | `v0.2.20` (this sync spans upstream v0.2.19 and v0.2.20: 178 commits, 245 files) |
| Package name | `com.shihab.diplay` (upstream's) |
| versionName / versionCode | `0.2.20` / `39` (upstream's) |
| Artifacts | `Diplay-V0.2.20-Leap21_23.apk` · `Diplay-V0.2.20-Leap24_27.apk` |
| Size | 18,482,233 B each |
| SHA-256 | `18e3beca…` (21_23) · `ed81e7a6…` (24_27) |
| Signing | V2 + V3 with a release keystore that is not in the repository |

The Leapmotor code itself did not change; both generations were re-applied onto the upstream v0.2.20 tree:

- attach on session start, detach at both teardown sites (each generation's own listeners)
- 2021–2023: wheel-key broadcasts, cluster viewport, reverse-gear handling
- 2024–2027: de-duplication across the wheel broadcast and the Android media key, plus the read-only vehicle probe

**Two local hunks were dropped** because upstream made the same change:

| Former local hunk | Upstream now |
|---|---|
| Match connection messages against the English string | Upstream uses `englishMessage` since v0.2.19 |
| Fold the About page into the settings categories | Upstream uses `SettingsCategory.ABOUT` since v0.2.19 |

One upstream behaviour change matters for building: **upstream stopped shipping the CarPlay accessory identity
with the sources at v0.2.18.** It is now injected at build time through `DIPLAY_AUTH_ASSETS_DIR`
(see upstream `docs/BUILD.md`). Without it the APK reports that CarPlay authentication could not be loaded.

## v0.2.18-Leap

| Item | Value |
|---|---|
| Upstream base | `v0.2.18` |
| Artifacts | `Diplay-V0.2.18-Leap21_23.apk` · `Diplay-V0.2.18-Leap24_27.apk` |
| Size | 18,211,826 B each |

First release shipping two APKs split by model year.

## v0.2.17-Leap

| Item | Value |
|---|---|
| Upstream base | `v0.2.17` |
| Artifact | `Diplay-V0.2.17-Leap.apk` |

Single-APK release from before the two generations were split.
