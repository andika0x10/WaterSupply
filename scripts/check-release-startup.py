"""Check a signed release APK on a disposable emulator, including a fresh install.

Usage: python scripts/check-release-startup.py --adb PATH --serial emulator-5556 --apk PATH
The selected emulator's Water Supply installation and data will be removed.
"""

import argparse
from pathlib import Path
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--apk", required=True, type=Path)
    args = parser.parse_args()
    if not args.apk.is_file():
        parser.error("APK does not exist")

    def adb(*command, check=True):
        return subprocess.run(
            [args.adb, "-s", args.serial, *command], check=check,
            text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            timeout=60,
        ).stdout.strip()

    if adb("shell", "getprop", "ro.kernel.qemu") != "1":
        parser.error("Select a disposable emulator; this check removes app data")

    package = "com.andika.watersupply"
    adb("uninstall", package, check=False)
    print(adb("install", str(args.apk.resolve())))
    for launch in range(2):
        adb("shell", "am", "force-stop", package)
        adb("logcat", "-b", "crash", "-c")
        print(adb("shell", "am", "start", "-W", "-n", f"{package}/.MainActivity"))
        time.sleep(5)
        crashes = adb("logcat", "-b", "crash", "-d")
        if package in crashes:
            raise RuntimeError(f"Release crashed on launch {launch + 1}:\n{crashes}")
        if not adb("shell", "pidof", package, check=False):
            raise RuntimeError("App process exited after launch")
        adb("shell", "uiautomator", "dump", "/data/local/tmp/watersupply-startup.xml")
        ui = adb("shell", "cat", "/data/local/tmp/watersupply-startup.xml")
        if f'package="{package}"' not in ui or "Water Supply" not in ui:
            raise RuntimeError("Water Supply UI did not appear")
    print("PASS: release opens after fresh install and cold relaunch")


if __name__ == "__main__":
    main()
