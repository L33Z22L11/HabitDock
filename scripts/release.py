#!/usr/bin/env python3
"""Small, dependency-free release helpers. Never print signing material."""

import argparse
import base64
import binascii
import hashlib
import json
import os
from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parent.parent
VERSION_PATTERN = r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?"
SIGNING_NAMES = (
    "ANDROID_KEYSTORE_BASE64",
    "ANDROID_KEYSTORE_PASSWORD",
    "ANDROID_KEY_ALIAS",
    "ANDROID_KEY_PASSWORD",
)


def version(root=ROOT):
    values = {}
    for line in (root / "version.properties").read_text().splitlines():
        if line.strip() and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    name = values["versionName"]
    code = int(values["versionCode"])
    if not re.fullmatch(VERSION_PATTERN, name) or not 0 < code <= 2100000000:
        raise ValueError("Invalid version.properties: use a semantic version and a positive Android versionCode.")
    return name, code


def validate_tag(tag, root=ROOT):
    name, code = version(root)
    if tag != "v" + name:
        raise ValueError("Tag must match version.properties exactly (expected v" + name + ").")
    return name, code


def signing_key(destination, env=None):
    env = os.environ if env is None else env
    missing = [name for name in SIGNING_NAMES if not env.get(name)]
    if missing:
        raise ValueError("Configure repository Actions secrets: " + ", ".join(missing))
    try:
        encoded = "".join(env["ANDROID_KEYSTORE_BASE64"].split())
        data = base64.b64decode(encoded, validate=True)
    except (ValueError, binascii.Error):
        raise ValueError("ANDROID_KEYSTORE_BASE64 is not valid Base64.") from None
    if not data:
        raise ValueError("The signing key is empty.")
    # Exclusive creation prevents accidental reuse of another task's key file.
    with os.fdopen(os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "wb") as output:
        output.write(data)


def package(tag, root=ROOT):
    name, code = validate_tag(tag, root)
    apk_dir = root / "app/build/outputs/apk/release"
    metadata = json.loads((apk_dir / "output-metadata.json").read_text())
    elements = metadata["elements"]
    if len(elements) != 1:
        raise ValueError("Expected one universal APK.")
    artifact = elements[0]
    if artifact["versionName"] != name or artifact["versionCode"] != code:
        raise ValueError("Built APK version does not match the release tag.")
    if artifact["outputFile"] != "app-release.apk":
        raise ValueError("Expected the signed app-release.apk output.")
    destination = root / "dist"
    destination.mkdir(exist_ok=True)
    apk = destination / ("HabitDock-" + name + ".apk")
    shutil.copyfile(apk_dir / artifact["outputFile"], apk)
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    (destination / "SHA256SUMS").write_text(digest + "  " + apk.name + "\n")
    return apk


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("validate", "signing-key", "package"))
    parser.add_argument("value")
    args = parser.parse_args()
    try:
        if args.command == "validate":
            name, code = validate_tag(args.value)
            print("Release version:", name, "versionCode:", code)
        elif args.command == "signing-key":
            signing_key(args.value)
            print("Temporary signing key ready.")
        else:
            print("Packaged:", package(args.value).name)
    except (ValueError, KeyError, OSError) as error:
        parser.exit(1, str(error) + "\n")


if __name__ == "__main__":
    main()
