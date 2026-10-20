"""Compile the isolated observation mod and run one Fabric client acceptance attempt."""
import argparse
import hashlib
import json
import os
import subprocess
import time
import zipfile
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('output', type=Path)
args = parser.parse_args()
output = args.output.resolve()
configuration = json.loads((output / 'configuration.json').read_text(encoding='utf-8'))
repo = Path(configuration['repo'])
assert output == Path(configuration['output'])
assert output.is_relative_to(repo / 'build/priority-closeout')
assert not (output / 'execution.json').exists(), 'Preserve this attempt and prepare another directory'
assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=repo, text=True).strip() == configuration['revision']
for name, digest in configuration['sources'].items():
    assert hashlib.sha256((repo / name).read_bytes()).hexdigest() == digest, name
assert hashlib.sha256((output / 'FabricDirectoryClientAcceptance.java').read_bytes()).hexdigest() == configuration['entrypointSha256']

environment = dict(os.environ)
environment['JAVA_HOME'] = configuration['javaHome']
environment['PATH'] = str(Path(configuration['javaHome']) / 'bin') + os.pathsep + environment['PATH']
java = Path(configuration['javaHome']) / 'bin'
node = configuration['node']
gradle = [str(repo / 'gradlew.bat'), '-g', configuration['gradleHome'],
          '-Dorg.gradle.java.home=' + configuration['javaHome'], '--offline', '--no-parallel', '--console=plain',
          '-I', str(output / 'client.gradle')]
execution = []

def execute(stage, arguments, timeout):
    start = time.monotonic()
    outcome = {'stage': stage}
    try:
        with (output / (stage + '.log')).open('xb') as log:
            process = subprocess.Popen(arguments, cwd=repo, env=environment, stdout=log, stderr=subprocess.STDOUT,
                                       stdin=subprocess.DEVNULL, creationflags=subprocess.CREATE_NO_WINDOW)
            outcome['launcherPid'] = process.pid
            try:
                outcome['exitCode'] = process.wait(timeout=timeout)
            except subprocess.TimeoutExpired:
                outcome['timedOut'] = True
                # The game is launched by a Gradle daemon, so it may not be a descendant of the wrapper.
                marker = '-Dneko.acceptance.output=' + output.as_posix()
                assert "'" not in marker and '\n' not in marker and '\r' not in marker
                query = ("@(Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'java.exe' -and "
                         "$_.CommandLine -and $_.CommandLine.Contains('" + marker + "') } | "
                         "Select-Object -ExpandProperty ProcessId) | ConvertTo-Json -Compress")
                owned = subprocess.run(['powershell.exe', '-NoProfile', '-Command', query], check=True,
                                       stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                       creationflags=subprocess.CREATE_NO_WINDOW)
                payload = owned.stdout.decode('utf-8-sig').strip()
                pids = json.loads(payload) if payload else []
                if isinstance(pids, int):
                    pids = [pids]
                outcome['forcedOwnedGamePids'] = pids
                for pid in pids:
                    subprocess.run(['taskkill.exe', '/PID', str(pid), '/T', '/F'], check=False,
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                subprocess.run(['taskkill.exe', '/PID', str(process.pid), '/T', '/F'], check=False,
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                process.wait(timeout=30)
                outcome['exitCode'] = process.returncode
                raise
    finally:
        outcome['seconds'] = time.monotonic() - start
        execution.append(outcome)
        (output / 'execution.json').write_text(json.dumps(execution, indent=2) + '\n', encoding='utf-8')
    assert outcome['exitCode'] == 0, stage

def classes():
    result = {}
    for relative in ['common/build/classes/java/main', f'versions/{node}/build/classes/java/main']:
        directory = repo / relative
        for file in sorted(directory.rglob('*.class')):
            key = file.relative_to(directory).as_posix()
            assert key not in result, key
            result[key] = hashlib.sha256(file.read_bytes()).hexdigest()
    assert result
    return result

execute('classpath', gradle + [f':{node}:directoryClientClasspath'], 600)
production = classes()
(output / 'production-classes.json').write_text(json.dumps(production, indent=2) + '\n', encoding='utf-8')
compiled = output / 'classes'
compiled.mkdir()
classpath = os.pathsep.join([str(repo / 'common/build/classes/java/main'),
                            str(repo / f'versions/{node}/build/classes/java/main'),
                            (output / 'compile-classpath.txt').read_text()])
execute('compile', [str(java / 'javac.exe'), '-encoding', 'UTF-8', '-cp', classpath, '-d', str(compiled),
                    str(output / 'FabricDirectoryClientAcceptance.java')], 120)
mod = output / 'game/mods/neko-directory-client-acceptance.jar'
with zipfile.ZipFile(mod, 'x', zipfile.ZIP_DEFLATED) as archive:
    archive.writestr('fabric.mod.json', json.dumps({
        'schemaVersion': 1, 'id': 'neko_directory_client_acceptance', 'version': '1.0.0', 'environment': 'client',
        'entrypoints': {'fabric-client-gametest': ['neko.acceptance.FabricDirectoryClientAcceptance']},
        'depends': {'fabricloader': '>=0.19.0', 'fabric-client-gametest-api-v1': '*', 'nekojs': '*', 'java': '>=25'},
    }, indent=2) + '\n')
    for file in sorted(compiled.rglob('*.class')):
        archive.write(file, file.relative_to(compiled).as_posix())
(output / 'observer-sha256.txt').write_text(hashlib.sha256(mod.read_bytes()).hexdigest() + '\n')
execute('client', gradle + [f':{node}:runClient'], 900)
result = json.loads((output / 'result.json').read_text(encoding='utf-8'))
assert result['accepted']
stopping = json.loads((output / 'stopping.json').read_text(encoding='utf-8'))
assert stopping['clientStopping'] and stopping['pid'] == result['pid']
assert result['completed'] == ['initial', 'world-a', 'f3-t-world-a', 'after-a', 'world-b', 'after-b',
                               'world-a-return', 'after-a-return']
assert classes() == production, 'Production classes changed during the attempt'
for name, digest in configuration['sources'].items():
    assert hashlib.sha256((repo / name).read_bytes()).hexdigest() == digest, name
assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=repo, text=True).strip() == configuration['revision']
print('Automated local resource checks passed; review screenshots and retain remote sync as a separate gap')
