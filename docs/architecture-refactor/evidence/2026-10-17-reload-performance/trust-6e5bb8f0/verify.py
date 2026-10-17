"""Verify the sealed evidence and recompute verdicts without a game installation."""

import argparse
import hashlib
import json
import statistics
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path


def verify():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--require-performance-pass', action='store_true')
    args = parser.parse_args()
    root = Path(__file__).resolve().parent
    manifest = json.loads((root / 'manifest.json').read_text(encoding='utf-8'))
    packed = root / 'evidence.zip'
    assert hashlib.sha256(packed.read_bytes()).hexdigest() == manifest['archiveSha256']
    with zipfile.ZipFile(packed) as archive:
        names = archive.namelist()
        assert len(names) == len(set(names))
        assert set(names) == set(manifest['entries'])
        for name in names:
            data = archive.read(name)
            expected = manifest['entries'][name]
            assert len(data) == expected['bytes'] and hashlib.sha256(data).hexdigest() == expected['sha256'], name

        def read_json(name):
            return json.loads(archive.read(name).decode('utf-8-sig'))

        all_pass = True
        for label in ['candidate-6e5bb8f0-formal']:
            prep = read_json(label + '/metadata/preparation.json')
            collected = read_json(label + '/metadata/groups.json')
            recorded = read_json(label + '/metadata/judgment.json')
            assert [row['group'] for row in collected] == ['reload-1', 'reload-2', 'startup']
            assert prep['plannedGroups'] == ['reload-1', 'reload-2', 'startup']
            assert prep['samples'] == prep['reloads'] == 5 and prep['warmup'] == 2
            assert prep['classesMatchBuiltArtifact'] and len(prep['classBindings']) > 1500
            assert prep['samplerSha256'] == '1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4'
            for collected_group, recorded_group in zip(collected, recorded['groups'], strict=True):
                group = collected_group['group']
                assert collected_group['exitCode'] == 0
                raw = archive.read(label + '/' + group + '/samples.jsonl').decode('utf-8-sig')
                rows = [json.loads(line) for line in raw.splitlines() if line.strip()]
                startup = group == 'startup'
                formal = [r for r in rows if r['kind'] == 'formal']
                warmup = [r for r in rows if r['kind'] == 'warmup']
                field, limit = ('wall_done_ms', 41173) if startup else ('marker_ms', 285.3)
                assert len(formal) == 5 and len(warmup) == (2 if startup else 0)
                assert len(rows) == (7 if startup else 5)
                assert sorted(r['index'] for r in rows) == list(range(1, len(rows) + 1))
                invalid = []
                for r in rows:
                    valid = (r.get(field) is not None and r.get('wall_done_ms') is not None
                        and r.get('done_s') is not None and r.get('killed') is False
                        and r.get('forced_kill') is False and not r.get('timeout')
                        and r.get('stop_channel') == 'rcon')
                    if not startup:
                        valid = valid and 'phase=COMMIT - no errors.' in r.get('rcon_response', '')
                    if not valid:
                        invalid.append(r['index'])
                values = [r[field] for r in formal]
                mean = statistics.mean(values) if not invalid else None
                verdict = 'INVALID' if invalid else ('PASS' if mean <= limit else 'FAIL')
                assert recorded_group['values'] == values and recorded_group['mean'] == mean
                assert recorded_group['invalidIndices'] == invalid and recorded_group['verdict'] == verdict
                assert recorded_group['revision'] == prep['revision'] and recorded_group['limit'] == limit
                all_pass = all_pass and verdict == 'PASS'
                print(json.dumps(dict(revision=prep['revision'][:8], group=group,
                    values=values, mean=mean, invalidIndices=invalid, verdict=verdict)))

        totals = dict(tests=0, failures=0, errors=0, skipped=0)
        for name in names:
            if name.startswith('final-validation/test-results/') and name.endswith('.xml'):
                suite = ET.fromstring(archive.read(name))
                for key in totals:
                    totals[key] += int(suite.attrib.get(key, 0))
        assert totals == read_json('final-validation/summary.json')['total']
        assert totals == dict(tests=4959, failures=0, errors=0, skipped=271)
        print(json.dumps(dict(integrity='PASS', testXmlTotals=totals, performanceAllPass=all_pass)))
        if args.require_performance_pass and not all_pass:
            raise SystemExit(2)


if __name__ == '__main__':
    verify()
