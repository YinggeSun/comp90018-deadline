"""Print only the persisted default Firebase user's UID/anonymous flag from a debug APK.

No app launch, SDK call, network request, data mutation, or credential output.
Firebase's private storage format can change; unknown formats fail closed.
"""

import argparse
import base64
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

PACKAGE = "com.comp90018.deadline"
ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--adb', help='Path to adb; otherwise use local.properties')
    args = parser.parse_args()
    if args.adb:
        adb = args.adb
    else:
        lines = ROOT.joinpath('local.properties').read_text().splitlines()
        sdk = next(line[8:] for line in lines if line.startswith('sdk.dir='))
        sdk = sdk.replace('\\:', ':').replace('\\\\', '\\')
        adb = str(Path(sdk) / 'platform-tools' / ('adb.exe' if sys.platform == 'win32' else 'adb'))

    def call(*command):
        result = subprocess.run([adb, '-s', args.serial, *command], capture_output=True)
        if result.returncode:
            # Never print captured subprocess output: an auth store may contain tokens.
            raise RuntimeError('ADB operation failed; check the device and installed debug app')
        return result.stdout

    installed = call('shell', 'dumpsys', 'package', PACKAGE).decode()
    flags = re.findall(r'^\s*flags=\[([^\]]*)\]', installed, re.MULTILINE)
    if not any('DEBUGGABLE' in flag.split() for flag in flags):
        raise RuntimeError('Installed app is absent or not debuggable; refusing to read identity')

    # Select only the default app's store corresponding to this checkout's app ID.
    # No API keys or other Google Services fields are printed.
    config = json.loads(ROOT.joinpath('app/google-services.json').read_text())
    clients = [c for c in config['client']
               if c['client_info']['android_client_info']['package_name'] == PACKAGE]
    if len(clients) != 1:
        raise RuntimeError('Ambiguous Firebase application configuration')
    app_id = clients[0]['client_info']['mobilesdk_app_id']
    encode = lambda text: base64.urlsafe_b64encode(text.encode()).decode().rstrip('=')
    name = 'com.google.firebase.auth.api.Store.' + encode('[DEFAULT]') + '+' + encode(app_id) + '.xml'
    files = call('shell', 'run-as', PACKAGE, 'ls', 'shared_prefs').decode().splitlines()
    if name not in files:
        raise RuntimeError('Expected default Firebase store is absent; no identity was created')
    # Raw XML/JSON stays in memory. Do not dump it, even on parse errors.
    root = ET.fromstring(call('shell', 'run-as', PACKAGE, 'cat', 'shared_prefs/' + name))
    node = root.find("./string[@name='com.google.firebase.auth.FIREBASE_USER']")
    if node is None:
        raise RuntimeError('No persisted Firebase user; no identity was created')
    user = json.loads(node.text)
    if user.get('applicationName') != '[DEFAULT]' or type(user.get('anonymous')) is not bool:
        raise RuntimeError('Unrecognized Firebase user format')
    providers = [json.loads(p) if isinstance(p, str) else p for p in user['userInfos']]
    firebase = [p for p in providers if p.get('providerId') == 'firebase']
    if len(firebase) != 1 or not re.fullmatch(r'[A-Za-z0-9_-]{1,128}', firebase[0].get('userId', '')):
        raise RuntimeError('Missing or unexpected Firebase UID format')
    print('source=persisted-debug-Firebase-user')
    print('uid=' + firebase[0]['userId'])
    print('anonymous=' + str(user['anonymous']).lower())


if __name__ == '__main__':
    try:
        main()
    except RuntimeError as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
    except Exception:
        # Parser exceptions can contain input values. Suppress them entirely.
        print('Identity diagnostic failed; no stored credential data was printed.', file=sys.stderr)
        sys.exit(1)
