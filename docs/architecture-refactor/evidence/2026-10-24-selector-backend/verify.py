"""Verify the frozen local checks; optionally repeat the fourteen archived strict programs."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('--run-types', action='store_true')
args = parser.parse_args()
folder = Path(__file__).resolve().parent
repo = folder.parents[3]
manifest = json.loads((folder / 'manifest.json').read_text(encoding='utf-8'))
archive = folder / 'evidence.zip'
assert hashlib.sha256(archive.read_bytes()).hexdigest() == manifest['archiveSha256']
assert archive.stat().st_size == manifest['archiveBytes']
with zipfile.ZipFile(archive) as source:
    assert len(source.namelist()) == len(set(source.namelist()))
    assert set(source.namelist()) == set(manifest['entries'])
    payloads = {name: source.read(name) for name in source.namelist()}
for name, digest in manifest['entries'].items():
    assert hashlib.sha256(payloads[name]).hexdigest() == digest, name
summary = json.loads(payloads['summary.json'])
assert len(summary['checks']) == 9 and all(row['exitCode'] == 0 for row in summary['checks'])
assert b'BUILD SUCCESSFUL' in payloads['checks/selector-matrix-v2.log']
totals = dict.fromkeys(['tests', 'failures', 'errors', 'skipped'], 0)
for name, data in payloads.items():
    if name.startswith('junit/') and name.endswith('.xml'):
        root = ET.fromstring(data)
        for key in totals:
            totals[key] += int(root.get(key, '0'))
assert totals == summary['total'] == dict(tests=5174, failures=0, errors=0, skipped=271)
selector = ET.fromstring(payloads['junit/common/test/TEST-com.tkisor.nekojs.probe.ir.TypeScriptFieldMethodSelectorTest.xml'])
assert int(selector.get('tests')) == 16 and int(selector.get('failures')) == int(selector.get('errors')) == 0
assert not any(summary[key] for key in ['installedAcceptance', 'formalPerformance', 'maintainerAcceptance'])
assert manifest['privacy']['retainedMatches'] == manifest['privacy']['redactedFiles'] == 0
verified_sources = 0
for name, binding in summary['sources'].items():
    assert re.fullmatch(r'[0-9a-f]{40}', binding['gitBlob'])
    path = (repo / name).resolve()
    assert path.is_relative_to(repo.resolve())
    if path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest() == binding['sha256']:
        verified_sources += 1
        continue
    result = subprocess.run(['git', 'cat-file', 'blob', binding['gitBlob']], cwd=repo, capture_output=True)
    assert result.returncode == 0, 'Neither the original local source nor its Git blob is available: ' + name
    # Git normalizes text line endings; bind the recorded physical build input to the same LF/CRLF source.
    data = result.stdout.replace(b'\r\n', b'\n')
    assert binding['sha256'] in {hashlib.sha256(data).hexdigest(), hashlib.sha256(data.replace(b'\n', b'\r\n')).hexdigest()}, name
    verified_sources += 1
programs = summary['strictPrograms']
assert len(programs) == 14
for name in programs:
    config = json.loads(payloads['programs/' + name + '/tsconfig.json'])
    assert config['compilerOptions']['strict'] and not config['compilerOptions']['skipLibCheck']
    assert all('programs/' + name + '/' + entry in payloads for entry in config['files'])
replayed = 0
if args.run_types:
    with tempfile.TemporaryDirectory(prefix='nekojs-selector-check-') as temporary:
        target = Path(temporary).resolve()
        assert target.is_relative_to(Path(tempfile.gettempdir()).resolve())
        for name, data in payloads.items():
            if not name.startswith('programs/'):
                continue
            path = (target / name).resolve()
            assert path.is_relative_to(target)
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
        for name in programs:
            result = subprocess.run([shutil.which('node'), str(repo / 'node_modules/typescript/bin/tsc'),
                                     '-p', str(target / 'programs' / name / 'tsconfig.json'), '--pretty', 'false'],
                                    capture_output=True)
            assert result.returncode == 0, name + ': ' + result.stdout.decode('utf-8', errors='replace')[:1000]
            replayed += 1
print(json.dumps(dict(verdict='PASS', entries=len(payloads), tests=totals, sourceBindings=verified_sources,
                      archivedStrictPrograms=len(programs), strictProgramsReplayed=replayed,
                      privatePatternScan='Receipt bound; not independently repeated', installedAcceptance=False)))
