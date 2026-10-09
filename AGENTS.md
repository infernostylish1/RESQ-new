# Project instructions

@C:\Users\dom\.codex\RTK.md

- Always test Android changes on a connected physical device before handing them over. Detect devices with `adb devices -l` and explicitly select the physical device serial. Emulator testing may supplement the device run.
- Preserve the device's app data and saved preferences while testing. Install updates with `adb install -r`; do not clear app data or uninstall the user's app as part of a routine test run.
- On a user's physical device, use `tools/map/test_on_device.py --serial <physical-serial>` (or explicit `adb install -r` plus `adb shell am instrument`). Do not use `connectedDebugAndroidTest`: its default UTP cleanup uninstalls the target application. Keep `android.injected.androidTest.leaveApksInstalledAfterRun=true` enabled as an additional guard.
- If no physical device is connected or testing cannot run, clearly report that limitation.
