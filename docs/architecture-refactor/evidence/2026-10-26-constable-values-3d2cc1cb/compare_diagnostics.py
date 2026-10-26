from collections import Counter
from functools import lru_cache
import json
from pathlib import Path
import re
import sys

HEADER = re.compile(r'(.*?)\((\d+),(\d+)\): error (TS\d+): (.*)')
MISSING = re.compile(r"Cannot find name '([^']+)'\.(?: Did you mean '[^']+'\?)?")


@lru_cache(maxsize=None)
def source_lines(path):
    return Path(path).read_text(encoding='utf-8').splitlines()


def parse(logs, load_source=source_lines):
    raw, semantic, counts = Counter(), Counter(), Counter()
    missing_contexts = []
    for name, log in sorted(logs.items()):
        entry = name.removesuffix('-tsc.txt')
        for text in log.splitlines():
            match = HEADER.match(text)
            if not match:
                continue
            file, line, column, code, message = match.groups()
            relative = file.replace('\\', '/').split('.neko_probe/typescript/', 1)[-1]
            key = (entry, relative, code, message)
            raw[key] += 1
            counts[entry] += 1
            name = MISSING.fullmatch(message)
            if name and code in ('TS2304', 'TS2552'):
                lines = load_source(file)
                index = int(line) - 1
                owner = next((s.strip() for s in reversed(lines[:index + 1])
                              if s.strip().startswith(('export class ', 'export interface ', 'export const '))), '')
                key = (entry, relative, 'UNRESOLVED_NAME', name[1], owner, lines[index].strip())
                missing_contexts.append({'entry': entry, 'file': relative, 'line': int(line), 'column': int(column),
                    'code': code, 'message': message, 'owner': owner, 'sourceLine': lines[index].strip(), 'name': name[1]})
            semantic[key] += 1
    return raw, semantic, counts, missing_contexts


def diagnostics(folder):
    return parse({path.name: path.read_text(encoding='utf-8') for path in folder.glob('*-tsc.txt')})


def compare(before_folder, after_folder):
    before, before_semantic, before_counts, before_contexts = diagnostics(before_folder)
    after, after_semantic, after_counts, after_contexts = diagnostics(after_folder)
    report = {'before': sum(before.values()), 'after': sum(after.values()),
              'rawRemoved': sum((before - after).values()), 'rawAdded': sum((after - before).values()),
              'semanticRemoved': sum((before_semantic - after_semantic).values()),
              'semanticAdded': sum((after_semantic - before_semantic).values()),
              'beforeEntrypoints': dict(before_counts), 'afterEntrypoints': dict(after_counts),
              'rawRemovedIdentities': [{'identity': list(k), 'count': n} for k, n in sorted((before - after).items())],
              'rawAddedIdentities': [{'identity': list(k), 'count': n} for k, n in sorted((after - before).items())],
              'missingNameNormalization': 'Only TS2304/TS2552 exact Cannot find name diagnostics; compare identifier, actual owner declaration and actual source line, preserving counts. Optional spelling suggestions are not name resolution changes.',
              'beforeMissingContexts': before_contexts, 'afterMissingContexts': after_contexts}
    return report


if __name__ == '__main__':
    report = compare(Path('build/tsbaseline-primary-f4-dad806fa/ide'), Path('build/tsbaseline-enum-84ae51c1/ide'))
    Path('build/enum-interface-diagnostic-delta.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(json.dumps({k: v for k, v in report.items() if k not in ('rawRemovedIdentities', 'rawAddedIdentities', 'beforeMissingContexts', 'afterMissingContexts')}))
    for item in report['rawAddedIdentities']:
        print(item)
