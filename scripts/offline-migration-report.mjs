#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

function usage() {
  return 'Usage: node scripts/offline-migration-report.mjs --migration <file> --protection <file> [--json]';
}

function parseArgs(argv) {
  const values = { json: false };
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (argument === '--json') {
      values.json = true;
      continue;
    }
    if (argument === '--migration' || argument === '--protection') {
      const value = argv[index + 1];
      if (!value || value.startsWith('--')) throw new Error(`${argument} requires a file path`);
      values[argument.slice(2)] = value;
      index += 1;
      continue;
    }
    throw new Error(`Unknown argument: ${argument}`);
  }
  if (!values.migration || !values.protection) throw new Error(usage());
  return values;
}

function readInput(label, file) {
  const absolute = path.resolve(file);
  let content;
  try {
    content = fs.readFileSync(absolute, 'utf8');
  } catch (error) {
    throw new Error(`${label} input cannot be read: ${absolute}: ${error.message}`);
  }
  if (!content.trim()) throw new Error(`${label} input is empty: ${absolute}`);
  return { absolute, content };
}

function headings(content) {
  return content.split(/\r?\n/).filter(line => /^#{1,6}\s+\S/.test(line)).map(line => line.trim());
}

function tableRows(content) {
  return content.split(/\r?\n/)
    .filter(line => /^\s*\|/.test(line) && !/^\s*\|(?:\s*:?-+:?\s*\|)+\s*$/.test(line))
    .map(line => line.trim());
}

function splitTableRow(line) {
  return line.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map(cell => cell.trim());
}

function tables(content) {
  const lines = content.split(/\r?\n/);
  const result = [];
  for (let index = 0; index + 2 < lines.length; index += 1) {
    if (!/^\s*\|/.test(lines[index]) || !/^\s*\|(?:\s*:?-+:?\s*\|)+\s*$/.test(lines[index + 1])) continue;
    const headers = splitTableRow(lines[index]);
    const rows = [];
    index += 2;
    while (index < lines.length && /^\s*\|/.test(lines[index])) {
      const cells = splitTableRow(lines[index]);
      if (cells.length === headers.length) rows.push(Object.fromEntries(headers.map((header, cell) => [header, cells[cell]])));
      index += 1;
    }
    result.push({ headers, rows });
    index -= 1;
  }
  return result;
}

function migrationAssociations(content) {
  const candidateTables = tables(content).filter(table => table.headers.some(header =>
    /old|legacy|旧|删除|原形态|new|replacement|替代|新写法|新形态/i.test(header)));
  const records = candidateTables.flatMap(table => table.rows.map(row => {
    const entries = Object.entries(row);
    const oldEntry = entries.find(([header]) => /old|legacy|旧|删除|原形态/i.test(header));
    const newEntry = entries.find(([header]) => /new|replacement|替代|新写法|新形态/i.test(header));
    return {
      old: oldEntry?.[1] ?? null,
      replacement: newEntry?.[1] ?? null,
      missing: [oldEntry ? null : 'old symbol', newEntry ? null : 'replacement path'].filter(Boolean),
    };
  }));
  return {
    records,
    missing: records.length === 0 ? ['migration symbol table'] : [...new Set(records.flatMap(record => record.missing))],
  };
}

function protectionAssociations(content) {
  const topics = ['config', 'world', 'pdata', 'pack', 'trust-store', 'workspace', 'logs', 'cache'];
  const normalized = content.toLowerCase();
  return {
    topics: topics.filter(topic => normalized.includes(topic)),
    missing: topics.filter(topic => !normalized.includes(topic)),
  };
}

function buildReport(values) {
  const migration = readInput('migration', values.migration);
  const protection = readInput('protection', values.protection);
  const associations = migrationAssociations(migration.content);
  const protectionSummary = protectionAssociations(protection.content);
  return {
    format: 1,
    readOnly: true,
    migration: {
      file: migration.absolute,
      headings: headings(migration.content),
      rows: tableRows(migration.content),
      associations: associations.records,
      missing: associations.missing,
    },
    protection: {
      file: protection.absolute,
      headings: headings(protection.content),
      rows: tableRows(protection.content),
      topics: protectionSummary.topics,
      missing: protectionSummary.missing,
    },
    warnings: [
      'This report does not perform migration or rollback.',
      'Missing symbols, fixtures, rollback evidence, and maintainer decisions require manual review.',
    ],
  };
}

try {
  const values = parseArgs(process.argv.slice(2));
  const report = buildReport(values);
  if (values.json) {
    process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
  } else {
    process.stdout.write(`Offline migration report (read-only)\nMigration: ${report.migration.file}\nProtection: ${report.protection.file}\nMigration headings: ${report.migration.headings.length}\nProtection headings: ${report.protection.headings.length}\nMigration rows: ${report.migration.rows.length}\nProtection rows: ${report.protection.rows.length}\nWarnings: ${report.warnings.length}\n`);
  }
} catch (error) {
  process.stderr.write(`offline-migration-report: ${error.message}\n`);
  process.exitCode = 2;
}
