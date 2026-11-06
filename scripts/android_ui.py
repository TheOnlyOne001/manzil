"""Small, explicit ADB helpers for inspecting the connected Manzil test build.

Usage: python scripts/android_ui.py snapshot home
       python scripts/android_ui.py tap 'Save sample journey'
Snapshots include only the screen the operator has brought to the foreground.
"""
import argparse
import os
import sys
import pathlib
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
ADB = pathlib.Path('C:/Users/shaya/android-sdk/platform-tools/adb.exe')
EVIDENCE = ROOT / 'android-evidence'


def adb(*args):
    result = subprocess.run([str(ADB), '-s', os.environ.get('MANZIL_DEVICE_SERIAL', 'RZGL40EETBZ'), *args], capture_output=True, check=True)
    return result.stdout


def hierarchy():
    adb('shell', 'uiautomator', 'dump', '/sdcard/manzil-window.xml')
    return adb('shell', 'cat', '/sdcard/manzil-window.xml')


def nodes(xml):
    for item in ET.fromstring(xml).iter('node'):
        label = item.get('text') or item.get('content-desc')
        if label:
            yield item, label


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['snapshot', 'tap'])
    parser.add_argument('value')
    args = parser.parse_args()
    xml = hierarchy()
    if args.action == 'snapshot':
        if not re.fullmatch(r'[a-zA-Z0-9_-]+', args.value):
            raise ValueError('Snapshot names must use only letters, numbers, underscores or hyphens.')
        EVIDENCE.mkdir(exist_ok=True)
        (EVIDENCE / f'{args.value}.xml').write_bytes(xml)
        (EVIDENCE / f'{args.value}.png').write_bytes(adb('exec-out', 'screencap', '-p'))
        for item, label in nodes(xml):
            print(f'{label} | {item.get("bounds")}')
    else:
        matches = [item for item, label in nodes(xml) if label == args.value]
        if len(matches) != 1:
            raise ValueError(f'Expected exactly one visible label {args.value!r}; found {len(matches)}.')
        item = matches[0]
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', item.get('bounds', '')))
        adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))


if __name__ == '__main__':
    main()
