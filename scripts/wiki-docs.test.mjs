import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { bodyOf, digest, headings, tableShape, fenceShape, markdownLinks } from './wiki-docs.mjs';

test('the CLI rejects missing pairs and drift, and sync is idempotent', () => {
  const temporaryRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'nekojs-wiki-test-'));
  assert.equal(path.dirname(temporaryRoot), path.resolve(os.tmpdir()));
  assert.ok(path.basename(temporaryRoot).startsWith('nekojs-wiki-test-'));
  try {
    for (const directory of ['scripts', 'wiki', 'docs']) fs.mkdirSync(path.join(temporaryRoot, directory));
    fs.copyFileSync(fileURLToPath(new URL('./wiki-docs.mjs', import.meta.url)), path.join(temporaryRoot, 'scripts/wiki-docs.mjs'));
    fs.writeFileSync(path.join(temporaryRoot, 'docs/wiki-pages.json'), JSON.stringify({
      sections: [{ id: 'start', cn: '开始', us: 'Start' }],
      pages: [{ id: 'counter', section: 'start', cn: '计数器', us: 'Counter' }]
    }));
    const sourceFile = path.join(temporaryRoot, 'wiki/counter_cn.md');
    const translationFile = path.join(temporaryRoot, 'wiki/counter_us.md');
    fs.writeFileSync(sourceFile, '# 计数器\n\nText.\n');
    fs.writeFileSync(translationFile, '# Counter\n\nText.\n');
    const run = args => spawnSync(process.execPath, ['scripts/wiki-docs.mjs', ...args], {
      cwd: temporaryRoot, stdio: 'ignore'
    }).status;
    assert.equal(run(['sync']), 0);
    const first = fs.readFileSync(sourceFile, 'utf8');
    assert.equal(run(['sync']), 0);
    assert.equal(fs.readFileSync(sourceFile, 'utf8'), first);
    assert.equal(run(['stamp']), 1);
    assert.equal(run(['stamp', '--all']), 0);
    assert.equal(run(['check', '--strict']), 0);
    fs.writeFileSync(sourceFile, first.replace('Text.', 'Changed.'));
    assert.equal(run(['check']), 0);
    assert.equal(run(['check', '--strict']), 1);
    assert.equal(run(['stamp', 'counter']), 0);
    fs.appendFileSync(sourceFile, '\n[Broken]()\n');
    assert.equal(run(['check', '--strict']), 1);
    fs.writeFileSync(sourceFile, first.replace('counter_us', 'wrong_us'));
    assert.equal(run(['check', '--strict']), 1);
    fs.writeFileSync(sourceFile, first);
    fs.writeFileSync(translationFile, '# Counter\n\n## Extra heading\n');
    assert.equal(run(['sync']), 0);
    assert.equal(run(['check', '--structure-only', '--strict']), 1);
    fs.writeFileSync(translationFile, '# Counter\n\nText.\n');
    assert.equal(run(['sync']), 0);
    assert.equal(run(['stamp', 'counter']), 0);
    fs.appendFileSync(path.join(temporaryRoot, 'wiki/_Sidebar.md'), '\nChanged order\n');
    assert.equal(run(['check', '--strict']), 1);
    assert.equal(run(['sync']), 0);
    fs.renameSync(translationFile, path.join(temporaryRoot, 'wiki/missing.md'));
    assert.equal(run(['check', '--strict']), 1);
  } finally {
    assert.equal(path.dirname(temporaryRoot), path.resolve(os.tmpdir()));
    assert.ok(path.basename(temporaryRoot).startsWith('nekojs-wiki-test-'));
    fs.rmSync(temporaryRoot, { recursive: true, force: true });
  }
});

test('links ignore inline code calls but retain code labels and real empty targets', () => {
  assert.deepEqual(markdownLinks('`x[Symbol.iterator]()` / `e[key]()`'), []);
  assert.deepEqual(markdownLinks('[`Item.of`](global-bindings_us) and [Broken]()'), ['global-bindings_us', '']);
});

test('body digests ignore generated headers, anchors and navigation', () => {
  const body = '# Topic\n\nText.\n';
  const generated = '<!-- wiki-page: topic; locale: cn -->\n\n> **中文** · [English](topic_us)\n\n<a id="wiki-section-1"></a>\n' + body + '\n<!-- wiki-nav -->\n\n---\nNext';
  assert.equal(bodyOf(generated), body);
  assert.equal(digest(generated), digest(body));
});

test('digests normalize Windows newlines and detect uncommitted content edits', () => {
  assert.equal(digest('# Topic\r\n\r\nText.\r\n'), digest('# Topic\n\nText.\n'));
  assert.notEqual(digest('# Topic\n\nNew value.\n'), digest('# Topic\n\nOld value.\n'));
});

test('heading extraction ignores code and supports both fence kinds', () => {
  const body = '# Topic\n```python\n# code comment\n```\n## Section\n~~~text\n### not a heading\n~~~\n### Detail\n';
  assert.deepEqual(headings(body).map(heading => heading.level), [1, 2, 3]);
});

test('table shape ignores code blocks and escaped pipe characters', () => {
  const body = '| Column | Meaning |\n|---|---|\n| `a\\|b` | Value |\n\n```text\n| not | a | table |\n```\n';
  assert.deepEqual(tableShape(body), [[4, 4, 4]]);
});

test('fence shape retains language sequence and catches an unfinished block', () => {
  assert.deepEqual(fenceShape('```js\nrun()\n```\n~~~python\nprint(1)\n~~~\n'), { languages: ['js', 'python'], closed: true });
  assert.equal(fenceShape('```python\nprint(1)\n').closed, false);
});

test('a shorter nested delimiter cannot close a longer fence', () => {
  assert.equal(fenceShape('````text\n```\ninside\n').closed, false);
  assert.equal(fenceShape('````text\n```\ninside\n````\n').closed, true);
  assert.deepEqual(headings('# Topic\n````text\n```\n## code only\n````\n## Real\n').map(heading => heading.level), [1, 2]);
  assert.deepEqual(tableShape('````text\n```\n| not | a table |\n````\n'), []);
});
