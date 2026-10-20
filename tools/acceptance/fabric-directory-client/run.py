"""Compile the isolated observation mod and run one Fabric client acceptance attempt."""
import argparse
import hashlib
import json
import os
import subprocess
import sys
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
for name, digest in configuration['testSources'].items():
    assert hashlib.sha256((output / name).read_bytes()).hexdigest() == digest, name

environment = dict(os.environ)
environment['JAVA_HOME'] = configuration['javaHome']
environment['PATH'] = str(Path(configuration['javaHome']) / 'bin') + os.pathsep + environment['PATH']
java = Path(configuration['javaHome']) / 'bin'
node = configuration['node']
gradle = [str(repo / 'gradlew.bat'), '-g', configuration['gradleHome'],
          '-Dorg.gradle.java.home=' + configuration['javaHome'], '--offline', '--no-parallel', '--console=plain',
          '-I', str(output / 'client.gradle')]
execution = []

def force_owned(marker):
    assert "'" not in marker and '\n' not in marker and '\r' not in marker
    query = ("@(Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'java.exe' -and "
             "$_.CommandLine -and $_.CommandLine.Contains('" + marker + "') } | "
             "Select-Object -ExpandProperty ProcessId) | ConvertTo-Json -Compress")
    owned = subprocess.run(['powershell.exe', '-NoProfile', '-Command', query], check=True,
                           stdout=subprocess.PIPE, stderr=subprocess.PIPE, creationflags=subprocess.CREATE_NO_WINDOW)
    payload = owned.stdout.decode('utf-8-sig').strip()
    pids = json.loads(payload) if payload else []
    if isinstance(pids, int):
        pids = [pids]
    for pid in pids:
        subprocess.run(['taskkill.exe', '/PID', str(pid), '/T', '/F'], check=False,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return pids

def execute(stage, arguments, timeout, monitor=None):
    start = time.monotonic()
    outcome = {'stage': stage}
    try:
        with (output / (stage + '.log')).open('xb') as log:
            process = subprocess.Popen(arguments, cwd=repo, env=environment, stdout=log, stderr=subprocess.STDOUT,
                                       stdin=subprocess.DEVNULL, creationflags=subprocess.CREATE_NO_WINDOW)
            outcome['launcherPid'] = process.pid
            try:
                if monitor is None:
                    outcome['exitCode'] = process.wait(timeout=timeout)
                else:
                    deadline = time.monotonic() + timeout
                    while process.poll() is None:
                        if time.monotonic() >= deadline:
                            raise subprocess.TimeoutExpired(stage, timeout)
                        monitor()
                        time.sleep(0.1)
                    outcome['exitCode'] = process.returncode
            except BaseException as failure:
                outcome['timedOut'] = isinstance(failure, subprocess.TimeoutExpired)
                # The game is launched by a Gradle daemon, so it may not be a descendant of the wrapper.
                outcome['forcedOwnedGamePids'] = force_owned('-Dneko.acceptance.output=' + output.as_posix())
                if process.poll() is None:
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
execute('compile', [str(java / 'javac.exe'), '-encoding', 'UTF-8', '-cp', classpath, '-d', str(compiled)] +
        [str(output / name) for name in ('FabricDirectoryClientAcceptance.java', 'SignedDirectoryFixture.java', 'SignedResourceServer.java')], 120)
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
server_process = None
server_log = None
commands = []
seen_requests = set()
server = output / 'server'

def rcon(command):
    properties = dict(line.split('=', 1) for line in (server / 'server.properties').read_text().splitlines() if '=' in line)
    response = subprocess.run([sys.executable, '-X', 'utf8', str(repo / 'bench/perf/rcon.py'),
                               properties['rcon.port'], properties['rcon.password'], '30', command],
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE, creationflags=subprocess.CREATE_NO_WINDOW)
    text = response.stdout.decode('utf-8', errors='replace')
    commands.append({'command': command, 'response': text, 'exitCode': response.returncode})
    (output / 'remote-commands.json').write_text(json.dumps(commands, indent=2) + '\n', encoding='utf-8')
    assert response.returncode == 0, 'RCON transport failed: ' + command
    return text

def monitor():
    assert server_process is not None and server_process.poll() is None, 'Owned TCP server exited'
    for request in sorted((output / 'remote-requests').glob('*.json')):
        if request.name in seen_requests:
            continue
        phase = json.loads(request.read_text())['phase']
        assert phase in ('restore', 'tampered', 'rotated')
        assert 'DIRECTORY-REMOTE ' + phase + ' PASS' in rcon('directoryprobe ' + phase)
        request.with_suffix('.ack').write_text('completed\n')
        seen_requests.add(request.name)

try:
    if configuration['remote']:
        execute('signed-fixture', [str(java / 'java.exe'), '-cp', str(compiled) + os.pathsep + classpath,
                                  'com.tkisor.nekojs.core.pack.sync.SignedDirectoryFixture', str(server), str(output)], 120)
        with zipfile.ZipFile(server / 'mods/neko-signed-resource-server.jar', 'x', zipfile.ZIP_DEFLATED) as archive:
            archive.writestr('fabric.mod.json', json.dumps({
                'schemaVersion': 1, 'id': 'neko_signed_resource_server', 'version': '1.0.0', 'environment': 'server',
                'entrypoints': {'main': ['neko.acceptance.SignedResourceServer']},
                'depends': {'fabricloader': '>=0.19.0', 'nekojs': '*', 'java': '>=25'},
            }) + '\n')
            for file in compiled.rglob('*.class'):
                name = file.relative_to(compiled).as_posix()
                if name.startswith(('neko/acceptance/SignedResourceServer', 'com/tkisor/nekojs/core/pack/sync/SignedDirectoryFixture')):
                    archive.write(file, name)
        (output / 'server-observer-sha256.txt').write_text(
            hashlib.sha256((server / 'mods/neko-signed-resource-server.jar').read_bytes()).hexdigest() + '\n')
        server_log = (output / 'server.log').open('xb')
        server_process = subprocess.Popen(gradle + [f':{node}:runServer'], cwd=repo, env=environment,
                                          stdout=server_log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL,
                                          creationflags=subprocess.CREATE_NO_WINDOW)
        deadline = time.monotonic() + 420
        while not (output / 'server-ready.json').exists():
            assert server_process.poll() is None, 'Owned server exited before readiness'
            assert time.monotonic() < deadline, 'Owned server readiness deadline exceeded'
            time.sleep(0.2)
    execute('client', gradle + [f':{node}:runClient'], 900, monitor if configuration['remote'] else None)
finally:
    if server_process is not None:
        normal_stop = False
        forced = []
        try:
            if server_process.poll() is None:
                assert rcon('stop').strip() == 'Stopping the server'
                server_process.wait(timeout=90)
                normal_stop = server_process.returncode == 0 and (output / 'server-stopped.json').exists()
        finally:
            if server_process.poll() is None:
                forced = force_owned('-Dneko.acceptance.server=' + server.as_posix())
                subprocess.run(['taskkill.exe', '/PID', str(server_process.pid), '/T', '/F'],
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                server_process.wait(timeout=30)
            if server_log is not None:
                server_log.close()
            (output / 'server-result.json').write_text(json.dumps({
                'normalStop': normal_stop, 'forcedOwnedGamePids': forced, 'launcherExitCode': server_process.returncode,
            }, indent=2) + '\n')
        assert normal_stop and not forced, 'Owned server did not stop normally'
result = json.loads((output / 'result.json').read_text(encoding='utf-8'))
assert result['accepted']
stopping = json.loads((output / 'stopping.json').read_text(encoding='utf-8'))
assert stopping['clientStopping'] and stopping['pid'] == result['pid']
expected = ['initial', 'world-a', 'f3-t-world-a', 'after-a', 'world-b', 'after-b', 'world-a-return', 'after-a-return']
if configuration['remote']:
    expected += ['remote-untrusted', 'remote-valid', 'remote-tampered', 'remote-recovered',
                 'remote-rotated', 'remote-final', 'remote-disconnected']
assert result['completed'] == expected
assert result['remoteSyncAccepted'] == configuration['remote']
assert classes() == production, 'Production classes changed during the attempt'
for name, digest in configuration['sources'].items():
    assert hashlib.sha256((repo / name).read_bytes()).hexdigest() == digest, name
assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=repo, text=True).strip() == configuration['revision']
print('Automated resource checks passed for the selected scope; review native screenshots separately')
