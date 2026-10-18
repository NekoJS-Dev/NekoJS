"""Verify package resource observations and preserve the failed full-check record."""
import hashlib
import json
import re
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path


def verify():
    root = Path(__file__).resolve().parent
    manifest = json.loads((root/'manifest.json').read_text())
    packed = root/'evidence.zip'
    assert hashlib.sha256(packed.read_bytes()).hexdigest() == manifest['archiveSha256']
    with zipfile.ZipFile(packed) as archive:
        assert len(archive.namelist()) == len(set(archive.namelist()))
        assert set(archive.namelist()) == set(manifest['entries'])
        for name, expected in manifest['entries'].items():
            data = archive.read(name)
            assert len(data) == expected['bytes'] and hashlib.sha256(data).hexdigest() == expected['sha256'], name
        for label, expected_tests, expected_failures in [('focused-v1', 5, 0), ('check', 2375, 1),
                                                       ('lang-control', 3, 0), ('check-v2', 2375, 0)]:
            result = json.loads(archive.read(label+'/result.json'))
            totals = dict(tests=0, failures=0, errors=0, skipped=0)
            names = [name for name in archive.namelist() if name.startswith(label+'/xml/')]
            roots = [ET.fromstring(archive.read(name)) for name in names]
            for suite in roots:
                for key in totals:
                    totals[key] += int(suite.attrib.get(key, 0))
            assert totals['tests'] == expected_tests and totals['failures'] == expected_failures and totals['errors'] == 0
            assert (result['exitCode'] == 0) == (expected_failures == 0)
            assert json.loads(archive.read(label+'/summary.json'))['totals'] == totals
            for name, expected in result['sources'].items():
                assert hashlib.sha256(archive.read('sources/'+Path(name).name)).hexdigest() == expected
            if label == 'check':
                failures = [failure for suite in roots for failure in suite.findall('./testcase/failure')]
                assert len(failures) == 1 and 'AccessDeniedException' in failures[0].text
                assert 'LangGeneratorJS.writeTo' in failures[0].text
            if label in {'focused-v1', 'check-v2'}:
                suite = next(suite for suite in roots if suite.attrib['name'].endswith('.PackResourceStabilityTest'))
                assert int(suite.attrib['tests']) == 5 and int(suite.attrib['skipped']) == 0
                text = suite.findtext('system-out', '')
                owners = re.findall(r'packs=(\d+) activeContexts=(\d+) peakContexts=(\d+) legacyHeapBytes=(\d+) activeHeapBytes=(\d+) candidateHeapBytes=(\d+)', text)
                assert [int(row[0]) for row in owners] == [1, 4, 8]
                for packs, active, peak, *_ in owners:
                    assert int(active) == int(packs)+1 and int(peak) == 2*int(active)
                windows = re.findall(r'reloads=(\d+) packs=4 activeContexts=5 peakContexts=10 heapBytes=(\d+) retiredContexts=(\d+) observedCollected=(\d+)', text)
                assert [int(row[0]) for row in windows] == [10, 20, 30, 40, 50]
                assert int(windows[-1][1]) <= int(windows[0][1])+32*1024*1024
                assert all(int(retired) == int(reloads)*5 and 0 <= int(collected) <= int(retired)
                           for reloads, _, retired, collected in windows)
                assert 'rejectedBatches=5 candidatesPerBatch=5 healthyContexts=5 recovery=PASS release=PASS' in text
        assert manifest['productionTreesUnchanged'] is True
    print(json.dumps(dict(integrity='PASS', focusedTests=5, finalCommonTests=2375,
                         preservedFailedCheck=True, packageResourceAutomation='PASS',
                         nativeMemoryAndActualClientAcceptanceNotEstablished=True)))


if __name__ == '__main__':
    verify()
