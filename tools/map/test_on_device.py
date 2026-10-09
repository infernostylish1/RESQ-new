"""Build and test on one physical device without uninstalling or clearing its app.

Before updates, save a private app-data archive under ignored build/map-device.
Use this instead of connectedDebugAndroidTest on a user's device.
"""
import argparse
from datetime import datetime, timezone
import os
from pathlib import Path
import subprocess
import tarfile

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = "com.resq.app"


def run(arguments, *, capture=False, env=None):
    return subprocess.run(["rtk", "proxy", *map(str, arguments)], cwd=ROOT,
                          env=env, check=True, text=True,
                          stdout=subprocess.PIPE if capture else None)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", default=str(Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk/platform-tools/adb.exe"))
    parser.add_argument("--java-home", default="C:/Program Files/Android/Android Studio/jbr")
    parser.add_argument("--test-class", default="com.resq.app.MapScreenTest")
    arguments = parser.parse_args()
    device_list = run([arguments.adb, "devices", "-l"], capture=True).stdout
    selected = next((line.split() for line in device_list.splitlines()
                     if line.split() and line.split()[0] == arguments.serial), None)
    if not selected or selected[1] != "device" or arguments.serial.startswith("emulator-") or not any(
            value.startswith("model:") for value in selected):
        raise SystemExit("Select an online physical device from adb devices -l.")
    adb = [arguments.adb, "-s", arguments.serial]
    installed = run([*adb, "shell", "pm", "list", "packages", "--user", "0", PACKAGE], capture=True).stdout
    output = ROOT / "build/map-device"
    output.mkdir(parents=True, exist_ok=True)
    if f"package:{PACKAGE}" in installed.splitlines():
        run([*adb, "shell", "am", "force-stop", PACKAGE])
        # Abort before updating if private data cannot be read (for example a
        # non-debuggable installed build). Failed probes must not skip backup.
        run([*adb, "shell", "run-as", PACKAGE, "pwd"], capture=True)
        directories = []
        for directory in ("databases", "shared_prefs", "files"):
            probe = subprocess.run(["rtk", "proxy", *adb, "shell", "run-as", PACKAGE, "ls", "-d", directory],
                                   cwd=ROOT, capture_output=True, text=True)
            if probe.returncode == 0:
                directories.append(directory)
        if directories:
            stamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
            backup = output / f"private-app-data-{arguments.serial}-{stamp}.tar"
            # File redirection uses a binary stream to preserve SQLite files and archives.
            with backup.open("wb") as target:
                subprocess.run(["rtk", "proxy", *adb, "exec-out", "run-as", PACKAGE,
                                "tar", "-cf", "-", *directories], cwd=ROOT, stdout=target, check=True)
            with tarfile.open(backup, "r:") as archive:
                members = archive.getmembers()
                if not members or not all(any(member.name.rstrip("/") == directory
                                               for member in members) for directory in directories):
                    raise SystemExit("Private app-data backup validation failed; APKs were not installed.")
            print(f"Private data backup saved: {backup.name}", flush=True)
    environment = dict(os.environ, JAVA_HOME=arguments.java_home, ANDROID_SERIAL=arguments.serial)
    run([ROOT / "gradlew.bat", ":app:testDebugUnitTest", ":app:assembleDebug",
         ":app:assembleDebugAndroidTest", "--offline"], env=environment)
    run([*adb, "install", "-r", ROOT / "app/build/outputs/apk/debug/app-debug.apk"])
    run([*adb, "install", "-r", ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"])
    result = run([*adb, "shell", "am", "instrument", "-w", "-r", "-e", "class",
                  arguments.test_class, f"{PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner"], capture=True)
    report = output / "instrumentation.txt"
    report.write_text(result.stdout, encoding="utf-8")
    print(result.stdout, flush=True)
    # Instrumentation may report failures with shell exit code 0.
    if "OK (" not in result.stdout or "FAILURES!!!" in result.stdout or "INSTRUMENTATION_FAILED" in result.stdout:
        raise SystemExit("Device checks failed; inspect build/map-device/instrumentation.txt. App remains installed.")
    print("Physical-device checks passed. App and saved data remain installed.", flush=True)


if __name__ == "__main__":
    main()
