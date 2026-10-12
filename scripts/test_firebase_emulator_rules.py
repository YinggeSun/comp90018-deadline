"""Host-only integration checks; no Android SDK or third-party Python packages.

Run: python scripts/test_firebase_emulator_rules.py
Requires the already-running demo-deadline Auth/Firestore emulators. Does not
load/deploy rules or clear existing data. Leaves unique test accounts/documents
in the disposable emulator. This tests REST/rules, not Android repositories.
"""

import base64
import datetime as dt
import hashlib
import json
from pathlib import Path
import secrets
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

PROJECT = "demo-deadline"
ROOT = Path(__file__).resolve().parents[1]
DOCS = f"projects/{PROJECT}/databases/(default)/documents"
FS = f"http://127.0.0.1:8080/v1/{DOCS}"
AUTH = "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:"
SERVER = object()
RESULTS = []


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise RuntimeError("Redirects are forbidden in emulator tests")


HTTP = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())


def request(method, url, body=None, token=None):
    parts = urllib.parse.urlsplit(url)
    if parts.scheme != "http" or parts.hostname != "127.0.0.1" or parts.port not in (8080, 9099, 4400):
        raise RuntimeError("Only fixed loopback emulator endpoints are permitted")
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, None if body is None else json.dumps(body).encode(), headers, method=method)
    try:
        with HTTP.open(req, timeout=20) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read())


def check(name, response, expected=200):
    status, data = response
    passed = status == expected
    if expected == 403:
        passed = passed and data.get("error", {}).get("status") == "PERMISSION_DENIED"
    RESULTS.append({"name": name, "passed": passed, "expected": expected, "actual": status})
    print(f"{'PASS' if passed else 'FAIL'} {name}: HTTP {status}", flush=True)
    return data


def require(response):
    status, data = response
    if status != 200:
        raise RuntimeError(f"Emulator setup failed: HTTP {status}; {data.get('error', {}).get('status', 'unknown')}")
    return data


def validate_token(token):
    payload = token.split('.')[1]
    claims = json.loads(base64.urlsafe_b64decode(payload + '=' * (-len(payload) % 4)))
    if claims.get('aud') != PROJECT or claims.get('iss') != f'https://securetoken.google.com/{PROJECT}':
        raise RuntimeError("Authentication emulator project mismatch")


def account(registered=True):
    body = {"returnSecureToken": True}
    if registered:
        body.update(email=f"issue65-{secrets.token_hex(12)}@example.test", password="emulator-password")
    result = require(request("POST", AUTH + "signUp?key=emulator-only", body))
    validate_token(result['idToken'])
    return result


def timestamp(seconds):
    return dt.datetime.now(dt.timezone.utc) + dt.timedelta(seconds=seconds)


def value(v):
    if isinstance(v, dt.datetime):
        return {"timestampValue": v.isoformat().replace('+00:00', 'Z')}
    if isinstance(v, bool):
        return {"booleanValue": v}
    if isinstance(v, int):
        return {"integerValue": str(v)}
    if isinstance(v, float):
        return {"doubleValue": v}
    if isinstance(v, str):
        return {"stringValue": v}
    if isinstance(v, list):
        return {"arrayValue": {"values": [value(item) for item in v]}}
    if isinstance(v, dict):
        return {"mapValue": {"fields": {k: value(item) for k, item in v.items()}}}
    raise TypeError(type(v))


def write(path, fields, token, merge=False):
    operation = {"update": {"name": DOCS + '/' + path, "fields": {k: value(v) for k, v in fields.items() if v is not SERVER}}}
    transforms = [{"fieldPath": k, "setToServerValue": "REQUEST_TIME"} for k, v in fields.items() if v is SERVER]
    if transforms:
        operation['updateTransforms'] = transforms
    if merge:
        operation['updateMask'] = {"fieldPaths": [k for k, v in fields.items() if v is not SERVER]}
    return request('POST', FS + ':commit', {"writes": [operation]}, token)


def get(path, token):
    return request('GET', FS + '/' + path, token=token)


def delete(path, token):
    return request('POST', FS + ':commit', {"writes": [{"delete": DOCS + '/' + path}]}, token)


def listing(collection, token, parent=''):
    return request('POST', FS + ('/' + parent if parent else '') + ':runQuery',
                   {"structuredQuery": {"from": [{"collectionId": collection}]}}, token)


def profile(email):
    return dict(nickname='Student', email=email, accountType='registered', createdAt=SERVER, lastLoginAt=SERVER, updatedAt=SERVER)


def progress():
    return dict(completedLevelIds=['level_1'], highestUnlockedWeek=3,
                personalBests={'level_1': dict(timeMillis=40000, achievedAtMillis=1)}, lastModifiedMillis=1, updatedAt=SERVER)


def code():
    return ''.join(secrets.choice('23456789ABCDEFGHJKLMNPQRSTUVWXYZ') for _ in range(8))


def run():
    hub = require(request('GET', 'http://127.0.0.1:4400/emulators'))
    if hub['auth']['port'] != 9099 or hub['firestore']['port'] != 8080:
        raise RuntimeError('Emulator ports mismatch')
    coverage_url = f'http://127.0.0.1:8080/emulator/v1/projects/{PROJECT}:ruleCoverage'
    coverage = require(request('GET', coverage_url))
    source = ROOT.joinpath('firestore.rules').read_bytes()
    loaded = coverage['rules']['files']
    if len(loaded) != 1 or loaded[0]['content'].encode() != source:
        raise RuntimeError('Loaded emulator rules do not exactly match firestore.rules; no tests executed')
    print('Verified loaded rules SHA256:', hashlib.sha256(source).hexdigest(), flush=True)

    owner, other, guest = account(), account(), account(False)
    token, uid, email = owner['idToken'], owner['localId'], owner['email']
    stranger, anonymous = other['idToken'], guest['idToken']
    path = 'users/' + uid

    # Each rejected creation starts from a valid shape and changes one field.
    invalid_profile = [('nickname', ''), ('nickname', '   '), ('nickname', 'x' * 21), ('nickname', 7),
                       ('email', 'forged@example.test'), ('email', 1), ('accountType', 'guest'),
                       ('password', 'forbidden'), ('createdAt', timestamp(-3600)),
                       ('lastLoginAt', timestamp(-3600)), ('updatedAt', timestamp(-3600))]
    for index, (field, bad) in enumerate(invalid_profile):
        check(f'profile/create invalid {field} #{index}', write(path, profile(email) | {field: bad}, token), 403)
    for field in profile(email):
        fields = profile(email)
        del fields[field]
        check(f'profile/create missing {field}', write(path, fields, token), 403)
    check('profile/anonymous cannot create registered profile', write('users/' + guest['localId'], profile(email), anonymous), 403)
    check('profile/cross-account create denied', write('users/' + secrets.token_hex(16), profile(email), token), 403)
    check('profile/owner creates valid profile', write(path, profile(email), token))
    original = check('profile/owner reads profile', get(path, token))
    for label, actor in [('registered stranger', stranger), ('anonymous stranger', anonymous), ('signed out', None)]:
        check(f'profile/{label} read denied', get(path, actor), 403)
        check(f'profile/{label} update denied', write(path, {'nickname': 'Intruder', 'updatedAt': SERVER}, actor, True), 403)
        check(f'profile/{label} listing denied', listing('users', actor), 403)
    check('profile/owner listing denied', listing('users', token), 403)
    check('profile/owner delete denied', delete(path, token), 403)
    for index, (field, bad) in enumerate(invalid_profile):
        check(f'profile/update invalid {field} #{index}', write(path, {'updatedAt': SERVER, field: bad}, token, True), 403)
    check('profile/createdAt cannot be replaced with server timestamp', write(path, {'createdAt': SERVER, 'updatedAt': SERVER}, token, True), 403)
    check('profile/valid 20-character nickname update', write(path, {'nickname': 'x' * 20, 'updatedAt': SERVER}, token, True))
    updated = require(get(path, token))['fields']
    assert updated['createdAt'] == original['fields']['createdAt']
    assert updated['lastLoginAt'] == original['fields']['lastLoginAt']
    assert updated['nickname']['stringValue'] == 'x' * 20
    check('profile/server lastLoginAt and updatedAt update', write(path, {'lastLoginAt': SERVER, 'updatedAt': SERVER}, token, True))
    logged = require(get(path, token))['fields']
    assert logged['createdAt'] == original['fields']['createdAt']
    assert logged['lastLoginAt'] == logged['updatedAt']
    assert logged['lastLoginAt'] != original['fields']['lastLoginAt']

    ppath = 'progress/' + guest['localId']
    check('progress/anonymous owner creates', write(ppath, progress(), anonymous))
    check('progress/anonymous owner reads', get(ppath, anonymous))
    # Link an emulator anonymous identity and prove UID and saved progress survive.
    linked_email = f'issue65-{secrets.token_hex(12)}@example.test'
    linked = require(request('POST', AUTH + 'update?key=emulator-only',
                             {'idToken': anonymous, 'email': linked_email, 'password': 'emulator-password', 'returnSecureToken': True}))
    assert linked['localId'] == guest['localId']
    validate_token(linked['idToken'])
    linked_token = linked['idToken']
    check('auth/link retains own progress access', get(ppath, linked_token))
    check('auth/linked identity creates registered profile', write('users/' + linked['localId'], profile(linked_email), linked_token))
    login = require(request('POST', AUTH + 'signInWithPassword?key=emulator-only',
                            {'email': linked_email, 'password': 'emulator-password', 'returnSecureToken': True}))
    assert login['localId'] == linked['localId']
    validate_token(login['idToken'])
    check('auth/password login retains profile access', get('users/' + linked['localId'], login['idToken']))
    for actor_name, actor in [('other account', token), ('signed out', None)]:
        check(f'progress/{actor_name} read denied', get(ppath, actor), 403)
        check(f'progress/{actor_name} write denied', write(ppath, progress(), actor), 403)
    check('progress/cross-account create denied', write('progress/' + secrets.token_hex(16), progress(), token), 403)
    check('progress/listing denied', listing('progress', linked_token), 403)
    improved = progress() | {'highestUnlockedWeek': 5, 'completedLevelIds': ['level_1', 'level_2'],
                            'personalBests': {'level_1': dict(timeMillis=30000, achievedAtMillis=1), 'level_2': dict(timeMillis=70000, achievedAtMillis=1)}}
    check('progress/owner improves progress', write(ppath, improved, linked_token))
    for field, bad in [('highestUnlockedWeek', 4), ('completedLevelIds', ['level_1']), ('personalBests', {}),
                       ('personalBests', improved['personalBests'] | {'level_1': dict(timeMillis=35000, achievedAtMillis=1)})]:
        check(f'progress/regression {field} denied', write(ppath, improved | {field: bad}, linked_token), 403)
    check('progress/delete denied', delete(ppath, linked_token), 403)
    fresh_path = 'progress/' + uid
    for index, (field, bad) in enumerate([('extra', 1), ('highestUnlockedWeek', 0), ('highestUnlockedWeek', 13),
                                        ('highestUnlockedWeek', '3'), ('completedLevelIds', ['level_9']), ('updatedAt', timestamp(-3600)),
                                        ('personalBests', {'level_9': dict(timeMillis=40000, achievedAtMillis=1)}),
                                        ('personalBests', {'level_1': dict(timeMillis=4499, achievedAtMillis=1)}),
                                        ('personalBests', {'level_1': dict(timeMillis=86400001, achievedAtMillis=1)})]):
        check(f'progress/invalid {field} #{index}', write(fresh_path, progress() | {field: bad}, token), 403)
    fields = progress()
    del fields['lastModifiedMillis']
    check('progress/missing lastModifiedMillis denied', write(fresh_path, fields, token), 403)

    entry = dict(uid=uid, nickname='Student', timeMillis=30000, week=2, submittedAt=SERVER)
    epath = f'leaderboard/level_1/entries/{uid}'
    for index, (field, bad) in enumerate([('uid', 'other'), ('week', 4), ('timeMillis', 4499), ('timeMillis', 1),
                                        ('timeMillis', 86400001), ('timeMillis', '20000'), ('timeMillis', 20000.5),
                                        ('week', '2'), ('nickname', 5), ('nickname', ''), ('nickname', '   '),
                                        ('nickname', 'x' * 21), ('extra', 1), ('submittedAt', timestamp(-3600))]):
        check(f'leaderboard/invalid {field} #{index}', write(epath, entry | {field: bad}, token), 403)
    check('leaderboard/missing week denied', write(epath, {k: v for k, v in entry.items() if k != 'week'}, token), 403)
    check('leaderboard/cross-account create denied', write(f'leaderboard/level_1/entries/{other["localId"]}', entry | {'uid': other['localId']}, token), 403)
    check('leaderboard/valid entry accepted', write(epath, entry, token))
    check('leaderboard/signed-in list accepted', listing('entries', stranger, 'leaderboard/level_1'))
    check('leaderboard/signed-out read denied', listing('entries', None, 'leaderboard/level_1'), 403)
    check('leaderboard/signed-out write denied', write(epath, entry, None), 403)
    check('leaderboard/cross-account update denied', write(epath, entry | {'timeMillis': 20000}, stranger), 403)
    for t in (30000, 35000):
        check(f'leaderboard/equal or slower {t} denied', write(epath, entry | {'timeMillis': t}, token), 403)
    check('leaderboard/faster update accepted', write(epath, entry | {'timeMillis': 20000}, token))
    check('leaderboard/level1 floor and 20-character nickname accepted', write(epath, entry | {'timeMillis': 4500, 'nickname': 'x' * 20}, token))
    check('leaderboard/level6 below floor denied', write(f'leaderboard/level_6/entries/{uid}', entry | {'timeMillis': 15749, 'week': 12}, token), 403)
    check('leaderboard/level6 floor accepted', write(f'leaderboard/level_6/entries/{uid}', entry | {'timeMillis': 15750, 'week': 12}, token))
    check('leaderboard/unknown level write denied', write(f'leaderboard/level_7/entries/{uid}', entry | {'week': 14}, token), 403)
    check('leaderboard/unknown level read denied', listing('entries', token, 'leaderboard/level_7'), 403)
    check('leaderboard/delete denied', delete(epath, token), 403)
    check('unmatched/read denied', get('players/' + uid, token), 403)
    check('unmatched/write denied', write('players/' + uid, {'x': 1}, token), 403)

    transfer = {k: v for k, v in progress().items() if k != 'updatedAt'}
    transfer.update(ownerId=uid, createdAt=SERVER, expiresAt=timestamp(23 * 3600))
    for index, (field, bad) in enumerate([('ownerId', 'other'), ('highestUnlockedWeek', 13), ('extra', 1),
                                        ('createdAt', timestamp(-3600)), ('expiresAt', timestamp(25 * 3600)), ('expiresAt', timestamp(-60))]):
        check(f'transfer/invalid {field} #{index}', write('transfers/' + code(), transfer | {field: bad}, token), 403)
    for invalid_code in ['k7qm3xpd', 'K7QM3XP0', 'K7QM3XP']:
        check(f'transfer/invalid code {invalid_code}', write('transfers/' + invalid_code, transfer, token), 403)
    tpath = 'transfers/' + code()
    check('transfer/valid creation accepted', write(tpath, transfer, token))
    check('transfer/another signed-in player reads known code', get(tpath, stranger))
    check('transfer/signed-out create denied', write('transfers/' + code(), transfer, None), 403)
    check('transfer/signed-out read denied', get(tpath, None), 403)
    check('transfer/missing code denied', get('transfers/' + code(), token), 403)
    check('transfer/list denied', listing('transfers', token), 403)
    check('transfer/update denied', write(tpath, transfer | {'highestUnlockedWeek': 12}, token), 403)
    check('transfer/delete denied', delete(tpath, token), 403)
    expiring_path = 'transfers/' + code()
    check('transfer/short-lived code created', write(expiring_path, transfer | {'expiresAt': timestamp(3)}, token))
    check('transfer/short-lived code initially readable', get(expiring_path, stranger))
    time.sleep(4)
    check('transfer/expired code denied', get(expiring_path, stranger), 403)
    coverage = require(request('GET', coverage_url))
    assert coverage['rules']['files'][0]['content'].encode() == source, 'Rules changed during execution'
    ROOT.joinpath('build/issue65-rules-coverage.json').write_text(json.dumps(coverage, indent=2), encoding='utf-8')


if __name__ == '__main__':
    ROOT.joinpath('build').mkdir(exist_ok=True)
    error = None
    try:
        run()
    except Exception as exc:
        error = f'{type(exc).__name__}: {exc}'
        print('ERROR:', error)
    failed = sum(not r['passed'] for r in RESULTS)
    report = {'project': PROJECT, 'checks': RESULTS, 'passed': len(RESULTS) - failed, 'failed': failed, 'error': error}
    ROOT.joinpath('build/issue65-emulator-results.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(f"RESULT: {report['passed']} passed, {failed} failed, {int(error is not None)} execution errors")
    sys.exit(1 if failed or error else 0)
