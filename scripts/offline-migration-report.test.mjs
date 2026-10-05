import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { spawnSync } from 'node:child_process';

const script = path.resolve('scripts/offline-migration-report.mjs');

test('offline migration report is deterministic and read-only', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'nekojs-migration-report-'));
  const migration = path.join(directory, 'migration.md');
  const protection = path.join(directory, 'protection.md');
  fs.writeFileSync(migration, '# Migration\n\n| old | new |\n| --- | --- |\n| A | B |\n');
  fs.writeFileSync(protection, '# Protection\n\n| data | rollback |\n| --- | --- |\n| world | backup |\n');
  const before = [fs.readFileSync(migration), fs.readFileSync(protection)];
  const first = spawnSync(process.execPath, [script, '--migration', migration, '--protection', protection, '--json'], { encoding: 'utf8' });
  const second = spawnSync(process.execPath, [script, '--migration', migration, '--protection', protection, '--json'], { encoding: 'utf8' });
  assert.equal(first.status, 0, first.stderr);
  assert.equal(second.status, 0, second.stderr);
  assert.equal(first.stdout, second.stdout);
  assert.deepEqual([fs.readFileSync(migration), fs.readFileSync(protection)], before);
  const report = JSON.parse(first.stdout);
  assert.equal(report.readOnly, true);
  assert.deepEqual(report.migration.headings, ['# Migration']);
  assert.deepEqual(report.protection.rows, ['| data | rollback |', '| world | backup |']);
});

test('offline migration report rejects malformed or empty inputs', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'nekojs-migration-report-empty-'));
  const migration = path.join(directory, 'migration.md');
  const protection = path.join(directory, 'protection.md');
  fs.writeFileSync(migration, '');
  fs.writeFileSync(protection, '# Protection\n');
  const empty = spawnSync(process.execPath, [script, '--migration', migration, '--protection', protection], { encoding: 'utf8' });
  assert.equal(empty.status, 2);
  assert.match(empty.stderr, /migration input is empty/);
  const malformed = spawnSync(process.execPath, [script, '--migration', migration, '--protection', protection, '--unknown'], { encoding: 'utf8' });
  assert.equal(malformed.status, 2);
  assert.match(malformed.stderr, /Unknown argument/);
});

test('offline migration report rejects missing inputs', () => {
  const result = spawnSync(process.execPath, [script, '--migration', 'missing.md', '--protection', 'missing-protection.md'], { encoding: 'utf8' });
  assert.equal(result.status, 2);
  assert.match(result.stderr, /migration input cannot be read/);
});
