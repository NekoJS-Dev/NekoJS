import ast
import hashlib
import json
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path
from zipfile import ZipFile

root = Path(__file__).resolve().parent
sha = lambda value: hashlib.sha256(value).hexdigest()
manifest = json.loads((root / 'manifest.json').read_text(encoding='utf-8'))
summary = json.loads((root / 'runtime-summary.json').read_text(encoding='utf-8'))
archive = root / manifest['archive']
assert sha(archive.read_bytes()) == manifest['sha256'] and archive.stat().st_size == manifest['bytes']
assert summary['sourceBase'] == '99e48fa30755ce226581b101cd59d4e9ffc48b17'
assert summary['productionPatchSha256'] == 'd0afd044e05fe8cfdfd1c635e728f04dc2f64c0d97e79e867adb3850b8c07baf'
with ZipFile(archive) as data:
    assert data.testzip() is None
    assert set(data.namelist()) == {row['name'] for row in manifest['entries']}
    for row in manifest['entries']:
        value = data.read(row['name'])
        assert len(value) == row['bytes'] and sha(value) == row['sha256']
    load = lambda name: json.loads(data.read(name).decode('utf-8-sig'))
    lines = lambda name: [json.loads(line) for line in data.read(name).decode('utf-8').splitlines()]
    assert sha(data.read('checks/production-source.patch')) == summary['productionPatchSha256']
    red = ET.fromstring(data.read('checks/red-test.xml'))
    assert int(red.attrib['failures']) == 1 and b'must not invalidate a signed content hash' in data.read('checks/red-test.xml')
    assert load('checks/red-exit.json')['exitCode'] == 1
    assert load('checks/green-exit.json')['exitCode'] == 0
    for name in ('green-workspace', 'green-preserve', 'green-managed', 'green-signatures'):
        record = ET.fromstring(data.read('checks/' + name + '.xml'))
        assert int(record.attrib['failures']) == int(record.attrib['errors']) == 0
    stats = dict(tests=0, skipped=0, failures=0, errors=0, xml=0)
    for name in data.namelist():
        if name.startswith('checks/matrix-xml/') and name.endswith('.xml'):
            record = ET.fromstring(data.read(name))
            for key in ('tests', 'skipped', 'failures', 'errors'):
                stats[key] += int(record.attrib.get(key, 0))
            stats['xml'] += 1
    assert stats == summary['matrix'] == dict(tests=4811, skipped=271, failures=0, errors=0, xml=810)
    assert load('checks/matrix-final-exit.json')['exitCode'] == 0
    assert b'BUILD SUCCESSFUL' in data.read('checks/matrix-final.txt')
    gates = load('checks/platform-gates.json')['gates']
    assert all(row['state'] == 'pass' and not row['failures'] for row in gates)

    def signed_pack(prefix, sync_id):
        raw = data.read(prefix + '/manifest.json')
        document = json.loads(raw)
        signature = document.pop('signature')
        files = sorted(name for name in data.namelist() if name.startswith(prefix + '/') and name != prefix + '/manifest.json')
        assert len(files) == 1 and files[0].endswith('.js')
        digest = hashlib.sha256(raw + b'\0')
        payload = hashlib.sha256(b'nekojs-pack-signature-v1\0' + sync_id.encode() + b'\0GLOBAL\0' + json.dumps(document, separators=(',', ':'), ensure_ascii=False).encode() + b'\0')
        for name in files:
            relative = name[len(prefix) + 1:].encode()
            value = relative + b'\0' + data.read(name) + b'\0'
            digest.update(value)
            payload.update(value)
        request = dict(publicKey=signature['publicKey'], signature=signature['signature'], digest=payload.hexdigest())
        script = "const fs=require('node:fs'),c=require('node:crypto'),r=JSON.parse(fs.readFileSync(0,'utf8'));if(!c.verify(null,Buffer.from(r.digest,'hex'),{key:Buffer.from(r.publicKey,'base64'),format:'der',type:'spki'},Buffer.from(r.signature,'base64')))process.exit(1);"
        result = subprocess.run(['node', '-e', script], input=json.dumps(request), text=True, capture_output=True)
        assert result.returncode == 0, result.stderr
        return digest.hexdigest()

    nodes = ['1.21.1', '26.1.2', '26.2.0', '26.1.2-fabric', '26.2.0-fabric']
    assert set(summary['artifacts']) == set(nodes)
    runtime = load('installed/runtime-summary.json')
    assert runtime == summary['runtime'] and {row['node'] for row in runtime} == set(nodes)
    assert all(row['verdict'] == 'PASS' and not row['errors'] for row in runtime)
    ast_count = 0
    for node in nodes:
        assert load('installed/' + node + '/server-exit.json')['exitCode'] == summary['directExits'][node] == 0
        proof = summary['signedContent']['nodes'][node]
        assert proof['before']['hash'] == proof['after']['hash'] and proof['before']['signatureValid'] and proof['after']['signatureValid']
        prefix = 'installed/' + node + '/nekojs/packs/owned-signed-workspace'
        assert signed_pack(prefix, 'packs:owned-signed-workspace') == proof['before']['hash']
        names = [name for name in data.namelist() if name.startswith('installed/' + node + '/.neko_probe/python/') and name.endswith('.pyi')]
        for name in names:
            ast.parse(data.read(name).decode('utf-8'), filename=name)
        ast_count += len(names)
        assert len(names) == next(row for row in summary['ide']['nodes'] if row['node'] == node)['pyright']['stubCount']
        assert 'com/tkisor/nekojs/script/WorkspaceGenerator.class' in summary['artifacts'][node]['classes']
    assert ast_count == 1600
    assert all(row['added'] == row['removed'] == 0 for row in summary['diagnosticComparison']['rows'])
    assert [row['pyright']['summary']['errorCount'] for row in summary['ide']['nodes']] == [7, 7, 7, 13, 13]
    assert all(row['pyright']['exitCode'] == 1 and all(case['exitCode'] == 2 and case['defaultEditorIncludes'] and not case['skipLibCheck'] for case in row['typescript']) for row in summary['ide']['nodes'])
    client = summary['clientDownload']
    assert client == load('client-window/download-verdict.json') and client['result'] == 'PASS'
    assert client['sourceBase'] == summary['sourceBase'] and client['sourcePatchSha256'] == summary['productionPatchSha256']
    preparation = load('client-window/preparation.json')
    assert preparation['artifactSha256'] == summary['artifacts']['26.2.0']['sha256']
    assert len(preparation['classBindings']) == 1592 and preparation['classBindings'] == summary['artifacts']['26.2.0']['classes']
    assert preparation['changedClassesFrom7d'] == ['com/tkisor/nekojs/script/WorkspaceGenerator.class']
    assert load('client-window/orchestrator-exit.json')['exitCode'] == 0
    for node, code in summary['clientExits'].items():
        assert code == load('client-window/' + node + '/exit.json')['exitCode'] == 0
        parent = 'client-window/' + node + '/nekojs/' + ('packs/' if node == 'server' else 'server_packs/')
        names = [name for name in data.namelist() if name.startswith(parent) and name.endswith('/manifest.json')]
        assert len(names) == 1
        assert signed_pack(names[0].rsplit('/', 1)[0], 'packs:owned-download') == summary['clientPackHashes'][node]
    assert len(set(summary['clientPackHashes'].values())) == 1
    pending, committed = client['awaitingAckWhilePartial'], client['latestGenerationCatchup']
    assert pending['status'] == 'AWAITING_ACK' and pending['activatedGeneration'] == 2
    assert not any(row['generation'] == 3 and row['phase'] == 'COMMIT' for row in pending['phases'])
    assert len({row['participant'] for row in pending['outcomes'] if row['generation'] == 3 and row['outcome'] == 'ack'}) == 1
    assert committed['status'] == 'COMMITTED' and committed['activatedGeneration'] == 3
    for outcome in ('ack', 'activation-report'):
        assert len({row['participant'] for row in committed['outcomes'] if row['generation'] == 3 and row['outcome'] == outcome}) == 2
    server = data.read('client-window/server/logs/latest.log').decode('utf-8')
    states = [json.loads(line.split('LIVE-WINDOW ', 1)[1]) for line in server.splitlines() if 'LIVE-WINDOW ' in line]
    assert pending in states and committed in states and 'NekoWindow7dD joined the game' in server
    assert 'Gave' in client['give']['response']
    for letter in ('C', 'D'):
        assert b'PACK-DOWNLOAD-PROOF executed=true' in data.read('client-window/client' + letter + '/stdout.log')
        events = lines('client-window/proxy' + letter + '.jsonl')
        assert not any(row['event'] in ('hold-expired', 'bundle-hold-expired') for row in events)
        for row in events:
            if row['event'] in ('pack-frame-forwarded', 'dynamic-frame-forwarded'):
                original = 'pack-frame' if row['event'] == 'pack-frame-forwarded' else 'dynamic-frame'
                assert any(value['event'] == original and value['frameSha256'] == row['frameSha256'] and value['bytes'] == row['bytes'] for value in events)
    assert summary['newSourcePerformance'] == 'NOT_RUN' and summary['previous7dFormalReload'] == 'FAIL'
    assert summary['humanAcceptance'] == 'NOT_RECORDED' and summary['overall'] == 'NOT_COMPLETE'
print(json.dumps(dict(result='PASS', evidenceOnly=True, entries=len(manifest['entries']), tests=stats['tests'], pythonAst=ast_count, independentEd25519=True, overall='NOT_COMPLETE')))
