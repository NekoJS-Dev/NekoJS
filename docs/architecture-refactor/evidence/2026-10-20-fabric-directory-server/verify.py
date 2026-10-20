"""Verify frozen native server observations without starting Minecraft."""
import hashlib
import io
import json
import re
import zipfile
from pathlib import Path

root=Path(__file__).resolve().parent
expected=json.loads((root/'verification.json').read_text(encoding='utf-8'))
archive=root/'evidence.zip'
assert archive.stat().st_size==expected['bytes']
assert hashlib.sha256(archive.read_bytes()).hexdigest()==expected['sha256']
observations={}
with zipfile.ZipFile(archive) as evidence:
    names=evidence.namelist()
    manifest=json.loads(evidence.read('manifest.json'))
    assert len(names)==len(set(names))==expected['entries']
    assert set(names)==set(manifest)|{'manifest.json'}
    for name,digest in manifest.items():
        assert hashlib.sha256(evidence.read(name)).hexdigest()==digest,name
    assert not any(name.endswith('server.properties') for name in names)
    assert evidence.read('production-delta-from-measured-source.patch')==b''
    binding=json.loads(evidence.read('binding.json'))
    assert binding['productionDeltaEmpty'] and binding['sourceRuntimeAcceptance']
    assert not binding['installedJarAcceptance'] and not binding['clientAssetsAccepted']
    for node in ['26.1.2-fabric','26.2.0-fabric']:
        prefix='accepted/'+node+'/'
        result=json.loads(evidence.read(prefix+'result.json'))
        assert result==expected['results'][node]
        assert result['ready'] and result['accepted'] and result['normalStop']
        assert result['launcherExitCode']==0 and result['revision']==expected['revision']
        assert not result['clientAssetsAccepted']
        source=json.loads(evidence.read(prefix+'source-binding.json'))
        assert source['revision']==expected['revision'] and source['node']==node
        artifact=evidence.read('artifacts/'+source['artifactName'])
        assert hashlib.sha256(artifact).hexdigest()==source['artifactSha256']
        with zipfile.ZipFile(io.BytesIO(artifact)) as jar:
            for name,digest in source['classes'].items():
                assert hashlib.sha256(jar.read(name)).hexdigest()==digest,name
        fixtures=json.loads(evidence.read(prefix+'fixtures.json'))
        hashes=json.loads(evidence.read(prefix+'fixture-hashes.json'))
        assert set(hashes)==set(fixtures)
        for name,digest in hashes.items():
            assert hashlib.sha256(fixtures[name].encode()).hexdigest()==digest
        state='world/nekojs_packs/world-data/.neko_pack.state.json'
        assert json.loads(json.loads(evidence.read(prefix+'fixtures-disabled.json'))[state])=={'enabled':False}
        assert json.loads(json.loads(evidence.read(prefix+'fixtures-restored.json'))[state])=={'enabled':True}
        log=evidence.read(prefix+'latest.log').decode('utf-8')
        assert 'Found new data pack nekojs:directory' in log
        assert 'FABRIC-BRIDGE-LIVE started recipes ok' in log
        assert 'FABRIC-BRIDGE-LIVE global entry' in log and 'FABRIC-BRIDGE-LIVE world entry' in log
        assert 'Stopping server' in log
        assert 'BUILD SUCCESSFUL' in evidence.read(prefix+'launcher.log').decode('utf-8')
        commands=json.loads(evidence.read(prefix+'commands.json'))
        assert all(row['exitCode']==0 for row in commands)
        def responses(command):
            return [row['response'] for row in commands if row['command']==command]
        def scores(name):
            return [int(re.search(r'has (-?\d+)',value).group(1))
                    for value in responses('scoreboard players get '+name+' bridge')]
        winners=scores('WINNER')
        # Repeated polling may retain an earlier state; every transition must be observed in order.
        position=0
        for value in [30,31,20,31]:
            position=winners.index(value,position)+1
        assert scores('BASE_ONLY')==[1]
        assert scores('GLOBAL_ONLY')==[2,2]
        assert scores('WORLD_ONLY')==[3,3,3]
        for name in ['disabled_only','blocked_only','world_only']:
            assert any('Unknown function' in response for response in responses('function bridge:'+name))
        assert scores('CHUNK')[-1]==1
        for name in ['STONE','DIRT','COBBLESTONE','ANDESITE']:
            assert scores('TAG_'+name) and all(value==1 for value in scores('TAG_'+name))
        for block in ['stone','dirt','cobblestone','andesite']:
            assert responses('execute if block 0 200 0 #bridge:additive run scoreboard players set TAG_'+block.upper()+' bridge 1')
        assert responses('execute unless block 0 200 0 #bridge:additive run scoreboard players set TAG_COBBLESTONE bridge 1')
        assert responses('execute unless block 0 200 0 #bridge:additive run scoreboard players set TAG_ANDESITE bridge 1')
        reloads=responses('nekojs reload')
        assert len(reloads)==3 and all('phase=COMMIT - no errors.' in response for response in reloads)
        assert len(responses('reload'))==3
        assert [value.strip() for value in responses('stop')]==['Stopping the server']
        observations[node]=dict(commands=len(commands),classes=len(source['classes']),winners=winners,
                                scriptCommits=len(reloads),nativeReloads=len(responses('reload')))
    failed='failed/fabric-directory-live-acdad763-26.1.2-fabric/'
    assert not json.loads(evidence.read(failed+'result.json'))['accepted']
    assert 'could not be found in project' in evidence.read(failed+'launcher.log').decode('utf-8')
    failed='failed/fabric-directory-live-acdad763-v2-26.1.2-fabric/'
    result=json.loads(evidence.read(failed+'result.json'))
    assert result['ready'] and result['normalStop'] and not result['accepted']
    commands=json.loads(evidence.read(failed+'commands.json'))
    say=[row for row in commands if row['command'].startswith('execute if loaded')]
    assert say and all(not row['response'].strip() for row in say)
print(json.dumps(dict(evidenceVerification='PASS',nativeServerAcceptance=observations,clientAssetsAccepted=False)))
