import hashlib
import json
import shutil
import tempfile
from pathlib import Path


REPO = Path(__file__).resolve().parents[4]
OLD_ARTIFACT = REPO / 'build/issue4-server-neoforge/mods/nekojs-neoforge-26.2.0-1.1.0-preview3.jar'
CANDIDATE = REPO / 'versions/26.2.0/build/libs/nekojs-neoforge-26.2.0-1.1.0-preview3.jar'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def tree(root):
    return {path.relative_to(root).as_posix(): digest(path)
            for path in sorted(root.rglob('*')) if path.is_file()}


def verify(condition, message):
    if not condition:
        raise RuntimeError(message)


def main():
    output_root = REPO / 'build/ticket37-rollback-rehearsal'
    output_root.mkdir(parents=True, exist_ok=True)
    profile = Path(tempfile.mkdtemp(prefix='owned-', dir=output_root))
    protected = profile / 'protected'
    protected.mkdir()
    fixtures = {
        'nekojs/config/engine.toml': 'unsafeFeatures = false\nuserFixture = "preserve"\n',
        'nekojs/config/probe.toml': 'enabled = false\n',
        'nekojs/config/trusted-servers.json': '{"trustedServers":{"fixture-bucket":true},"trustedKeys":{}}\n',
        'nekojs/server_scripts/src/user.js': 'console.info("user-owned fixture");\n',
        'nekojs/packs/user/manifest.json': '{"id":"user","enabled":false,"clientSync":false}\n',
        'nekojs/packs/user/.neko_pack.state.json': '{"enabled":false}\n',
        'world/nekojs_packs/world-user/manifest.json': '{"id":"world-user","enabled":true}\n',
        'world/entities/old-pdata.snbt': '{id:"minecraft:armor_stand",NeoForgeData:{NekoJSPersistentData:{mana:303,name:"neko"},SomeOtherModsKey:7}}\n',
        'world/playerdata/old-player.snbt': '{NeoForgeData:{NekoJSPersistentData:{quest:7}},Inventory:[]}\n',
        'nekojs/server_scripts/jsconfig.json': '{"userTop":42,"compilerOptions":{"customConditions":["user"]}}\n',
        '.vscode/settings.json': '{"python.languageServer":"Jedi","userTop":42}\n',
        'logs/nekojs/user.log': '[fixture] diagnostic history is not regenerable\n',
    }
    for relative, content in fixtures.items():
        target = protected / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding='utf-8')
    before = tree(protected)
    backup = profile / 'backup'
    shutil.copytree(protected, backup)
    verify(tree(backup) == before, 'Backup bytes differ from the original fixtures')
    deployed = profile / 'mods/nekojs.jar'
    deployed.parent.mkdir()
    old_hash = digest(OLD_ARTIFACT)
    candidate_hash = digest(CANDIDATE)
    verify(old_hash != candidate_hash, 'The rollback must use genuinely different artifact bytes')
    shutil.copy2(OLD_ARTIFACT, deployed)
    verify(digest(deployed) == old_hash, 'Old artifact staging failed')
    shutil.copy2(CANDIDATE, deployed)
    verify(digest(deployed) == candidate_hash, 'Candidate artifact staging failed')
    verify(tree(protected) == before, 'Candidate artifact swap changed protected bytes')
    shutil.copy2(OLD_ARTIFACT, deployed)
    verify(digest(deployed) == old_hash, 'Artifact rollback did not restore the old bytes')
    verify(tree(protected) == before, 'Artifact rollback changed protected bytes')
    selected = protected / 'nekojs/server_scripts/src/user.js'
    selected.write_text('damaged fixture\n', encoding='utf-8')
    damaged = tree(protected)
    verify(damaged != before, 'The data restore trial did not introduce damage')
    shutil.copy2(CANDIDATE, deployed)
    shutil.copy2(OLD_ARTIFACT, deployed)
    verify(tree(protected) == damaged, 'Artifact rollback was incorrectly treated as data recovery')
    saved_original = profile / 'damaged-original'
    shutil.copytree(protected, saved_original)
    stage = profile / 'restore-stage'
    shutil.copytree(backup, stage)
    verify(tree(stage) == before, 'Restore staging hash check failed')
    restore_failure = None
    try:
        raise OSError('Injected operator cancellation after staging, before restore publish')
    except OSError as failure:
        restore_failure = str(failure)
    verify(restore_failure is not None, 'The restore failure injection did not execute')
    verify(tree(protected) == damaged, 'Failed-before-publish restore changed live fixtures')
    verify(tree(saved_original) == damaged, 'Failed restore did not retain the damaged original')
    for restored in sorted(stage.rglob('*')):
        if restored.is_file():
            target = protected / restored.relative_to(stage)
            shutil.copy2(restored, target)
    verify(tree(protected) == before, 'Separate backed-up data restore did not restore exact bytes')
    for restored in sorted(backup.rglob('*')):
        if restored.is_file():
            shutil.copy2(restored, protected / restored.relative_to(backup))
    verify(tree(protected) == before, 'Repeating the restore was not idempotent')
    verify(tree(backup) == before, 'Data restore modified the backup')
    verify(tree(saved_original) == damaged, 'Data restore did not preserve the damaged original')
    report = {
        'kind': 'bounded-offline-operator-rehearsal-not-minecraft-runtime',
        'profile': profile.relative_to(REPO).as_posix(),
        'oldArtifact': {'path': OLD_ARTIFACT.relative_to(REPO).as_posix(), 'sha256': old_hash},
        'candidateArtifact': {'path': CANDIDATE.relative_to(REPO).as_posix(), 'sha256': candidate_hash},
        'protectedFixtureHashes': before,
        'artifactSwapAndRollbackPreserveProtectedBytes': True,
        'artifactRollbackDoesNotUndoDataDamage': True,
        'separateRestoreExactAndIdempotent': True,
        'injectedRestoreFailure': restore_failure,
        'failureBeforePublishPreservesDamagedLiveAndOriginal': True,
        'backupAndDamagedOriginalRetained': True,
        'productionMigrationIntroduced': False,
        'minecraftRuntimeRollbackTested': False,
        'limitations': ['Synthetic file fixtures, not production config/NBT parsing.',
                        'No directory-wide atomic restore or power-loss guarantee.',
                        'No arbitrary Java/network/world side-effect undo.',
                        'Old artifact source revision not inferred from filename or hash.'],
    }
    report_path = profile / 'result.json'
    report_path.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, indent=2))
    print('REHEARSAL-PASS ' + report_path.relative_to(REPO).as_posix())


if __name__ == '__main__':
    main()
