"""Verify the exact proposed interface case and current compiler/common test evidence."""
import hashlib
import json
import re
import textwrap
import zipfile
from pathlib import Path
from xml.etree import ElementTree as ET

root=Path(__file__).resolve().parent
manifest=json.loads((root/'manifest.json').read_text())
artifact=root/'evidence.zip'
assert artifact.stat().st_size==manifest['archiveBytes']
assert hashlib.sha256(artifact.read_bytes()).hexdigest()==manifest['archiveSha256']
with zipfile.ZipFile(artifact) as archive:
    assert set(archive.namelist())==set(manifest['entries'])
    for name,expected in manifest['entries'].items():
        assert hashlib.sha256(archive.read(name)).hexdigest()==expected,name
    current=archive.read('source/current.java').decode()
    proposal=archive.read('source/proposal-c7e87571.java').decode()
    pattern=r'"arrow-fn-type-in-interface",\s*"""\r?\n(.*?)\r?\n\s*""",\s*false,\s*true'
    first=re.search(pattern,proposal,re.S)
    second=re.search(pattern,current,re.S)
    assert first and second
    assert textwrap.dedent(first.group(1))==textwrap.dedent(second.group(1))
    assert current.count('new CorpusCase(')==59
    focused=ET.fromstring(archive.read('run/community61-interface-case.xml'))
    assert int(focused.attrib['tests'])==73
    assert all(int(focused.attrib.get(key,0))==0 for key in ['failures','errors','skipped'])
    assert any('arrow-fn-type-in-interface' in row.attrib['name'] for row in focused.findall('testcase'))
    totals={key:0 for key in ['tests','failures','errors','skipped']}
    for name in archive.namelist():
        if not name.startswith('common-results/') or not name.endswith('.xml'):continue
        result=ET.fromstring(archive.read(name))
        for key in totals:totals[key]+=int(result.attrib.get(key,0))
    assert totals==dict(tests=2376,failures=0,errors=0,skipped=4),totals
    log=archive.read('run/community61-common-check.log').decode('utf-8-sig',errors='replace')
    assert 'BUILD SUCCESSFUL' in log and '> Task :common:checkCommonIsolation' in log
    print(json.dumps(dict(status='PASS',corpusCases=59,focusedTests=73,common=totals),indent=2))
