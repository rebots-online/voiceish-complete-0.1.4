#!/usr/bin/env python3
"""Check the distributable installer, its embedded signed companion and pinned identity."""
from pathlib import Path
import hashlib, os, re, subprocess, tempfile, zipfile
root = Path(__file__).resolve().parents[1]
sdk = os.getenv('ANDROID_SDK_ROOT') or os.getenv('ANDROID_HOME')
tools = Path(os.getenv('VOICEISH_BUILD_TOOLS') or (str(Path(sdk)/'build-tools/35.0.0') if sdk else str(Path(os.getenv('VOICEISH_TOOLCHAIN', str(root.parent/'toolchain')))/'build-tools/android-15')))
cert = '56c13674ef22df95deb1e5c468820e8cfa3ea2f522511749ab7b6e5bde3bd943'
installer = root/'dist/voiceish-setup-0.1.4.apk'
expected = root/'android/setup/src/main/assets/voiceish-sim.apk'
def run(*args):
    return subprocess.check_output([str(arg) for arg in args], text=True)
def verify(apk, package, code, minimum):
    result = run(tools/'apksigner','verify','--print-certs',apk)
    assert f'Signer #1 certificate SHA-256 digest: {cert}' in result, 'Wrong organizational certificate'
    manifest = run(tools/'aapt','dump','badging',apk)
    assert f"package: name='{package}' versionCode='{code}'" in manifest, 'Wrong package/version'
    assert "versionName='0.1.4'" in manifest, 'Wrong displayed version'
    assert f"sdkVersion:'{minimum}'" in manifest, 'Wrong minimum SDK'
    assert "targetSdkVersion:'35'" in manifest, 'Wrong target SDK'
    return manifest
manifest = verify(installer,'com.voiceish.setup',5,29)
permissions = re.findall(r"uses-permission: name='([^']+)'",manifest)
assert permissions == ['android.permission.REQUEST_INSTALL_PACKAGES'], permissions
with zipfile.ZipFile(installer) as archive:
    payload = archive.read('assets/voiceish-sim.apk')
assert payload == expected.read_bytes(), 'Embedded companion changed'
pin_source = (root/'android/setup/src/main/java/com/voiceish/setup/Payload.java').read_text()
assert hashlib.sha256(payload).hexdigest() in pin_source, 'Payload hash not pinned'
with tempfile.TemporaryDirectory() as directory:
    inner = Path(directory)/'companion.apk'; inner.write_bytes(payload)
    companion = verify(inner,'com.voiceish.sim',5,26)
    assert "uses-permission: name='android.permission.SEND_SMS'" in companion
print('PASS: installer signature, package/version, SDK, minimal permissions, exact embedded companion, hash pin, companion production signature and SMS declaration.')
print('Phone installation, runtime permission grants and real carrier SMS remain device checks.')
