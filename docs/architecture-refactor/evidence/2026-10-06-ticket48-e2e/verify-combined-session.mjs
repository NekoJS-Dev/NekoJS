import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const location = process.argv[2] ?? fileURLToPath(new URL('./combined-after-fix.log', import.meta.url));
const text = readFileSync(location, 'utf8');
const lines = text.split(/\r?\n/);
const records = lines.flatMap((line, index) => {
  const marker = line.indexOf('[ticket48-auto] ');
  if (marker < 0) return [];
  const json = line.slice(marker + '[ticket48-auto] '.length);
  return [{ index, ...JSON.parse(json) }];
});
assert.ok(records.length > 0, 'No combined-session records');
for (let profile = 1; profile <= 6; profile++) {
  assert.ok(records.some(record => record.stage === `profile-${profile}-result-true`
    && record.profile === profile), `Profile ${profile} was not applied`);
}
assert.ok(records.some(record => record.packFontWidth === 36), 'Missing font revision B readback');
assert.ok(records.some(record => record.packFontWidth === 16), 'Missing font revision A readback');

const failureIndex = lines.findIndex(line => line.includes('[NEKO-1008]'));
assert.ok(failureIndex >= 0, 'Missing rejected candidate diagnostic');
const before = records.filter(record => record.index < failureIndex).at(-1);
const after = records.find(record => record.index > failureIndex);
assert.equal(before.username, 'NekoTester');
assert.equal(after.username, before.username);
assert.equal(after.passwordLength, before.passwordLength);
assert.equal(after.lastSubmitted, before.lastSubmitted);
assert.equal(after.statistics.initialBuild, before.statistics.initialBuild);
assert.equal(after.statistics.reconcile, before.statistics.reconcile);
assert.equal(after.disposed, false);
assert.ok(after.statistics.paint > before.statistics.paint, 'Retained screen did not keep painting');
assert.ok(text.includes('ticket48 deliberate event failure'), 'Missing native event error');
assert.ok(text.includes('ticket48 deliberate render failure'), 'Missing native render error');
assert.ok(records.some(record => record.recoveryCount >= 1 && record.renderFault === false
  && record.statistics.diagnostics >= 2), 'Healthy handler did not recover the retained tree');

const final = records.at(-1);
assert.equal(final.stage, 'closed');
assert.equal(final.disposed, true);
assert.equal(final.statistics.cleanup, 1);
assert.equal(final.statistics.initialBuild, 1);
assert.equal(final.username, 'NekoTester');
assert.equal(final.passwordLength, 7);
assert.equal(final.lastSubmitted, 'NekoTester');
assert.ok(final.statistics.resize >= 2, 'Native Screen resize was not observed');
assert.ok(final.statistics.paint > 0);
assert.ok(final.statistics.reconcile > 0);
assert.ok(records.some(record => record.statistics.resize >= 2
  && record.usernameRect?.height === 20 && record.passwordRect?.height === 20
  && record.usernameRect?.width === 196 && record.passwordRect?.width === 196
  && record.submitRect.y >= record.passwordRect.y + record.passwordRect.height),
  'Final resized input geometry is missing');
if (records.some(record => Array.isArray(record.listNodes))) {
  assert.ok(text.includes('[NEKO-6004]'), 'Missing same-session resource failure');
  assert.ok(records.some(record => record.packFontWidth === 24), 'Missing native fallback width');
  const inserted = records.find(record => record.stage === 'list-phase-1');
  const updated = records.find(record => record.stage === 'list-phase-2');
  const removed = records.find(record => record.stage === 'list-phase-3');
  assert.ok(inserted && updated && removed, 'Missing keyed-list phases');
  assert.deepEqual(inserted.listNodes.map(node => [node.id, node.text]), [['list-first', 'A'], ['list-second', 'B']]);
  assert.deepEqual(updated.listNodes.map(node => [node.id, node.text]), [['list-first', 'AX'], ['list-second', 'B']]);
  assert.deepEqual(removed.listNodes.map(node => [node.id, node.text]), [['list-second', 'B']]);
  assert.ok(updated.statistics.reconcile > inserted.statistics.reconcile);
  assert.ok(removed.statistics.reconcile > updated.statistics.reconcile);
  assert.equal(removed.statistics.initialBuild, inserted.statistics.initialBuild);
}
console.log(JSON.stringify({ result: 'pass', records: records.length,
  retainedState: { username: after.username, passwordLength: after.passwordLength,
    lastSubmitted: after.lastSubmitted }, finalStatistics: final.statistics }, null, 2));
