# DiPlay next release notes

Changes through DiPlay 0.2.17 are documented in [0.2.17 release notes](RELEASE-NOTES-0.2.17.md). Measured checks are in [VALIDATION.md](VALIDATION.md). Device acceptance and remaining failure families are tracked in [connection reliability validation](CONNECTION_RELIABILITY.md).

Add future unreleased changes here.

## Vehicle

- Show CarPlay arrows and the next-turn distance in the cluster's navigation view on head units built on the FCE/Coagent T7 platform, through their stock `fce_misc_service_hal_property` service, without ADB. The output follows **Navigation on HUD and dashboard**, whose card now also appears on these head units. See [BYD navigation](BYD_NAVIGATION.md#cluster-guidance-on-fce-head-units-needs-no-adb). The property path was confirmed in a car with Android Auto guidance; this CarPlay integration has not yet run in a car.
## Settings

- About now opens inside Settings like the other categories. On wide screens the category rail stays visible, and About is the last entry in it. The page uses the same title style.
## Connection setup and recovery

- Fix wired CarPlay on Android 7.1 head units, where every attempt stopped at the USB network step with `SocketException: Invalid argument at PlainSocketImpl.socketBind` ([#557](https://github.com/shihabal3amri/DiPlay/issues/557)). The AirPlay listener on the wired address `fe80::2` named no network interface. Android 8 and later add the interface themselves, and Android 7.1 does not, so Linux rejected the listener. DiPlay now binds it to the VPN tunnel that owns the address, and the log records the choice as `wired AirPlay listener scope iface=tun0 index=…`. Pending in-car acceptance.
