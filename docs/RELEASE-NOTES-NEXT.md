# DiPlay next release notes

Changes through DiPlay 0.2.17 are documented in [0.2.17 release notes](RELEASE-NOTES-0.2.17.md). Measured checks are in [VALIDATION.md](VALIDATION.md). Device acceptance and remaining failure families are tracked in [connection reliability validation](CONNECTION_RELIABILITY.md).

Add future unreleased changes here.

## Vehicle

- Show CarPlay arrows and the next-turn distance in the cluster's navigation view on head units built on the FCE/Coagent T7 platform, through their stock `fce_misc_service_hal_property` service, without ADB. The output follows **Navigation on HUD and dashboard**, whose card now also appears on these head units. See [BYD navigation](BYD_NAVIGATION.md#cluster-guidance-on-fce-head-units-needs-no-adb). The property path was confirmed in a car with Android Auto guidance; this CarPlay integration has not yet run in a car.
## Settings

- About now opens inside Settings like the other categories. On wide screens the category rail stays visible, and About is the last entry in it. The page uses the same title style.
## Connection setup and recovery

- Fix wired CarPlay on Android 7.1 head units, where every attempt stopped at the USB network step with `SocketException: Invalid argument at PlainSocketImpl.socketBind` ([#557](https://github.com/shihabal3amri/DiPlay/issues/557)). The AirPlay listener on the wired address `fe80::2` named no network interface. Android 8 and later add the interface themselves, and Android 7.1 does not, so Linux rejected the listener. DiPlay now binds it to the VPN tunnel that owns the address, and the log records the choice as `wired AirPlay listener scope iface=tun0 index=…`. Pending in-car acceptance.
## Connection setup and recovery

- Switch the iPhone into wired CarPlay mode when a head-unit kernel driver holds its USB audio interface. On an 8227L head unit (Android 9), `snd-usb-audio` claimed interface 0 of the iPhone's audio configuration, Linux refused every switch to configuration 6, and DiPlay failed with "Android could not claim the NCM interface 3" ([#518](https://github.com/shihabal3amri/DiPlay/issues/518)). When Android refuses the switch, DiPlay now detaches the head unit's drivers from the iPhone's current configuration and tries again. The report records the outcome as `USB configuration select target=6 ok=… before=… detached=… after=…`, and an iPhone that stays in another configuration now fails with that cause instead of the NCM claim error. Pending in-car acceptance. [#PR]
- On head units with no VPN consent dialog, wired CarPlay stopped at "This head unit cannot open VPN authorization" ([#604](https://github.com/shihabal3amri/DiPlay/issues/604)). When the car's network ADB is already approved, DiPlay now allows its VPN through it (`appops set … ACTIVATE_VPN allow`) and continues. It never asks for ADB approval at that point. The report logs `VPN consent over adb access=… granted=…`. Pending in-car acceptance. [#PR]
- Wireless CarPlay on a Ford SYNC+ head unit (i.MX8, Android 8.1) failed at the first Bluetooth message with "Bluetooth RFCOMM write failed": the message was written, but the head unit's security policy refused Android's flush of the Bluetooth socket ([#582](https://github.com/shihabal3amri/DiPlay/issues/582)). DiPlay now stops flushing a socket that refuses it and keeps the link; the report notes `Bluetooth RFCOMM flush refused by the head unit`. Other write and flush failures still end the attempt. Pending in-car acceptance. [#PR]
- When an iPhone on iOS 12 or 15 refuses wired CarPlay mode, the report now shows the three bytes it returns for its USB mode instead of `unreadable(3)` ([#509](https://github.com/shihabal3amri/DiPlay/issues/509), [#455](https://github.com/shihabal3amri/DiPlay/issues/455)). [#PR]

## Audio, calls and Siri

- Fix short music dropouts. While DiPlay waited for the car to play queued sound, the music decoder filled up and DiPlay discarded the next AAC packet after waiting only 10 ms, cutting about 21 ms of sound. A DiLink 3.0 report lost 80 packets in one drive, roughly one every 10–20 s; reports from DiLink 5.0 and a Harman head unit also lost some ([#381](https://github.com/shihabal3amri/DiPlay/issues/381)). DiPlay now plays the decoded sound and keeps waiting up to 0.5 s, as the video decoder already did. `decoderDroppedTotal` in the report counts any packet still dropped. [#PR]
