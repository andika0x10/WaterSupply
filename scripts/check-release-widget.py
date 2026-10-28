"""Exercise an installed release widget on a disposable emulator's home screen.

Place Water Supply's widget on the home screen first. --apk optionally checks an
in-place, same-signature APK update while a drink and a widget already exist.
Successful checks undo the drinks they add; no app data is cleared.
"""

import argparse
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--screenshot", type=Path)
    args = parser.parse_args()
    if args.apk and not args.apk.is_file():
        parser.error("APK does not exist")

    def adb(*command, check=True):
        return subprocess.run(
            [args.adb, "-s", args.serial, *command], check=check,
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=60,
        ).stdout

    if adb("shell", "getprop", "ro.kernel.qemu").strip() != b"1":
        parser.error("Select a disposable emulator; this check adds and undoes test drinks")

    def nodes():
        adb("shell", "uiautomator", "dump", "/data/local/tmp/watersupply-widget.xml")
        return list(ET.fromstring(adb("shell", "cat", "/data/local/tmp/watersupply-widget.xml")).iter("node"))

    def units(value):
        whole, _, fraction = value.partition(",")
        return int(whole) * 2 + (1 if fraction == "5" else 0)

    def progress(expected=None):
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            for node in nodes():
                match = re.fullmatch(r"(\d+(?:,5)?) / (\d+(?:,5)?)", node.get("text", ""))
                if match:
                    total = units(match[1])
                    if expected is None or expected == total:
                        return total
            time.sleep(0.2)
        raise RuntimeError(f"Widget did not render expected progress {expected}; check launcher and logcat")

    def tap(description):
        action = next((n for n in nodes() if n.get("content-desc") == description), None)
        if action is None:
            raise RuntimeError(f"Widget action missing: {description}")
        left, top, right, bottom = map(int, re.findall(r"\d+", action.get("bounds", "")))
        adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))

    package = "com.andika.watersupply"
    adb("shell", "input", "keyevent", "3")
    before = progress()
    tap("Tambah 1 gelas")
    progress(before + 2)
    print("PASS: release widget renders and adds a drink", flush=True)

    # Let Glance's 45-second session finish before killing the background process.
    time.sleep(60)
    adb("shell", "am", "kill", package)
    if adb("shell", "pidof", package, check=False).strip():
        raise RuntimeError("App is still active; cold-process widget check could not run")
    tap("Tambah setengah gelas")
    progress(before + 3)
    tap("Urungkan catatan terakhir hari ini")
    progress(before + 2)
    print("PASS: widget actions restart a cold app process", flush=True)

    if args.apk:
        print(adb("install", "-r", str(args.apk.resolve())).decode().strip(), flush=True)
        progress(before + 2)
        print("PASS: APK update preserves widget and recorded drink", flush=True)
    tap("Urungkan catatan terakhir hari ini")
    progress(before)
    if args.screenshot:
        args.screenshot.parent.mkdir(parents=True, exist_ok=True)
        args.screenshot.write_bytes(adb("exec-out", "screencap", "-p"))
    print("PASS: undo restores original progress", flush=True)


if __name__ == "__main__":
    main()
