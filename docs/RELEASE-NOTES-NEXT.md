# DiPlay next release notes

Changes through DiPlay 0.2.16 are documented in [0.2.16 release notes](RELEASE-NOTES-0.2.16.md). Measured checks are in [VALIDATION.md](VALIDATION.md). Device acceptance and remaining failure families are tracked in [connection reliability validation](CONNECTION_RELIABILITY.md).

## Connection setup and recovery

- Settings recommends the built-in car hotspot and lists it first, and the Wi-Fi Direct description says why it can stutter. Diagnostic reports from DiLink 4.0 and 5.0 cars lost Wi-Fi Direct audio packets on a 10 s rhythm while the car's own Wi-Fi client searched for networks; the car hotspot on the same cars lost none or almost none.
- Pause that network search during Wi-Fi Direct sessions on every supported Android version (7.1 and later), not only Android 10, when network ADB is already approved. A shell-user helper turns off Android's automatic joining (`enableWifiConnectivityManager` on Android 7.1–10, `allowAutojoinGlobal` on 11+), restores it when CarPlay ends, and on Android 13+ leaves a setting someone else turned off alone. The diagnostic report now says why a pause did not happen, for example `reason=adb-not-approved`. The car hotspot and Same LAN no longer pause it. Pending in-car acceptance.
