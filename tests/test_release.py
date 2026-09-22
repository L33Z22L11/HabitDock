import base64
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("release", Path(__file__).resolve().parents[1] / "scripts/release.py")
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "version.properties").write_text("versionName=1.2.3\nversionCode=42\n")

    def test_tag_must_match_version_and_reject_path_injection(self):
        self.assertEqual(release.validate_tag("v1.2.3", self.root), ("1.2.3", 42))
        for tag in ("1.2.3", "v1.2.4", "v../../key", "v1.2.3;echo secret", "v1.2.3\n"):
            with self.assertRaises(ValueError):
                release.validate_tag(tag, self.root)

    def test_invalid_android_version_code(self):
        for code in (0, -1, 2100000001):
            (self.root / "version.properties").write_text(f"versionName=1.2.3\nversionCode={code}\n")
            with self.assertRaises(ValueError):
                release.validate_tag("v1.2.3", self.root)

    def test_missing_and_invalid_secrets_do_not_write_key(self):
        target = self.root / "release.keystore"
        for env in ({}, {name: "?" for name in release.SIGNING_NAMES}):
            with self.assertRaises(ValueError):
                release.signing_key(target, env)
            self.assertFalse(target.exists())

    def test_key_is_private_and_cannot_overwrite_existing_file(self):
        target = self.root / "release.keystore"
        env = {name: "fixture" for name in release.SIGNING_NAMES}
        env["ANDROID_KEYSTORE_BASE64"] = base64.b64encode(b"fixture key bytes").decode()
        release.signing_key(target, env)
        self.assertEqual(target.read_bytes(), b"fixture key bytes")
        self.assertEqual(target.stat().st_mode & 0o777, 0o600)
        with self.assertRaises(FileExistsError):
            release.signing_key(target, env)

    def test_release_artifact_checks_built_version_and_output(self):
        folder = self.root / "app/build/outputs/apk/release"
        folder.mkdir(parents=True)
        apk = b"fixture APK contents"
        (folder / "app-release.apk").write_bytes(apk)
        element = {"versionName": "1.2.3", "versionCode": 42, "outputFile": "app-release.apk"}
        metadata = folder / "output-metadata.json"
        metadata.write_text(json.dumps({"elements": [element]}))
        result = release.package("v1.2.3", self.root)
        self.assertEqual(result.read_bytes(), apk)
        self.assertEqual((self.root / "dist/SHA256SUMS").read_text().split()[1], result.name)
        for changes in ({"versionCode": 41}, {"versionName": "1.2.2"}, {"outputFile": "app-release-unsigned.apk"}):
            metadata.write_text(json.dumps({"elements": [dict(element, **changes)]}))
            with self.assertRaises(ValueError):
                release.package("v1.2.3", self.root)


if __name__ == "__main__":
    unittest.main()
