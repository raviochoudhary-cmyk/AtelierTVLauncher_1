#!/usr/bin/env python3
# Structural validation of the built APK. Fails the build if the APK looks
# like the earlier 12.7 KB empty shell (no dex, no resources, missing manifest
# entries). Pure standard library.
import hashlib
import os
import sys
import zipfile

APK_MIN_SIZE = 100_000  # bytes; a real launcher APK is > 1 MB


def contains(haystack, token):
    if token.encode("utf-8") in haystack:
        return True
    try:
        return token.encode("utf-16-le") in haystack
    except Exception:
        return False


def main():
    if len(sys.argv) < 2:
        print("usage: validate_apk.py <path-to-apk>")
        return 2

    path = sys.argv[1]
    failures = 0

    def check(ok, msg):
        nonlocal failures
        print(("[ OK ] " if ok else "[FAIL] ") + msg)
        if not ok:
            failures += 1

    check(os.path.isfile(path), "APK exists: " + path)
    if failures:
        return 1

    size = os.path.getsize(path)
    check(size >= APK_MIN_SIZE,
          "size = {:,} bytes (>= {:,}); a ~12 KB APK means an empty shell".format(size, APK_MIN_SIZE))
    check(zipfile.is_zipfile(path), "is a valid ZIP/APK")

    zf = zipfile.ZipFile(path)
    names = zf.namelist()

    for entry in ("AndroidManifest.xml", "classes.dex", "resources.arsc"):
        check(entry in names, "contains " + entry)

    res = [n for n in names if n.startswith("res/")]
    check(len(res) >= 10, "resource entries: {} (>= 10)".format(len(res)))
    check(any("banner" in n.lower() for n in res), "TV banner asset present")
    check(any(n.startswith("res/mipmap") for n in res), "launcher icon mipmaps present")

    manifest = zf.read("AndroidManifest.xml")
    for token in ("android.intent.action.HOME",
                  "android.intent.category.LEANBACK_LAUNCHER",
                  "com.atelier.tvlauncher"):
        check(contains(manifest, token), "manifest declares: " + token)

    dex = zf.read("classes.dex")
    for token in ("Lcom/atelier/tvlauncher/MainActivity;",
                  "Lcom/atelier/tvlauncher/HdmiInputHelper;",
                  "Lcom/atelier/tvlauncher/AppDiscovery;"):
        check(contains(dex, token), "classes.dex contains: " + token)

    with open(path, "rb") as fh:
        sha = hashlib.sha256(fh.read()).hexdigest()
    print("sha256:", sha)

    if failures:
        print("")
        print("VALIDATION FAILED: {} check(s) failed.".format(failures))
        return 1
    print("")
    print("All checks passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
