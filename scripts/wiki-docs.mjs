import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const wiki = path.join(root, 'wiki');
const manifestPath = path.join(root, 'docs/wiki-pages.json');
const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));

export function bodyOf(text) {
  return text.replace(/\r\n/g, '\n')
    .replace(/^<!-- wiki-page:.*?-->\n*/s, '')
    .replace(/^> \*\*(?:中文|English)\*\*.*\n*/, '')
    .split('<!-- wiki-nav -->')[0]
    .replace(/<a id="wiki-section-\d+"><\/a>\n/g, '')
    .trim() + '\n';
}

export function headings(text) {
  const output = [];
  outsideFences(text, line => {
    const match = line.match(/^(#{1,6})\s+(.+)$/);
    if (match) output.push({ level: match[1].length, title: match[2] });
    return line;
  });
  return output;
}

export function digest(text) {
  return crypto.createHash('sha256').update(bodyOf(text)).digest('hex');
}

function pageName(page, locale) {
  return `${page.id}_${locale}`;
}

function slug(title) {
  return title.toLowerCase().replace(/<[^>]+>/g, '').replace(/[^\p{L}\p{N}\s_-]/gu, '').replace(/\s/g, '-');
}

function sourceBody(text) {
  const firstHeading = text.search(/^# /m);
  if (firstHeading < 0) throw new Error('Source page has no title');
  return text.slice(firstHeading).trim() + '\n';
}

function headingMap(text) {
  const result = new Map();
  const counts = new Map();
  headings(text).forEach((heading, index) => {
    const base = slug(heading.title);
    const occurrence = counts.get(base) ?? 0;
    counts.set(base, occurrence + 1);
    result.set(occurrence ? `${base}-${occurrence}` : base, `wiki-section-${index + 1}`);
  });
  return result;
}

function outsideFences(text, transform) {
  let fence = null;
  return text.split('\n').map(line => {
    const delimiter = line.match(/^\s*(`{3,}|~{3,})(.*)$/);
    if (delimiter) {
      if (fence === null) fence = delimiter[1];
      else if (delimiter[1][0] === fence[0] && delimiter[1].length >= fence.length && delimiter[2].trim() === '') fence = null;
      return line;
    }
    return fence === null ? transform(line) : line;
  }).join('\n');
}

function languageHeader(page, locale) {
  return `<!-- wiki-page: ${page.id}; locale: ${locale} -->\n\n> **${locale === 'cn' ? '中文' : 'English'}** · [${locale === 'cn' ? 'English' : '中文'}](${pageName(page, locale === 'cn' ? 'us' : 'cn')})\n\n`;
}

function importPages() {
  const sources = new Map(manifest.pages.map(page => [page.id, sourceBody(fs.readFileSync(path.join(wiki, page.legacy), 'utf8'))]));
  const legacy = new Map(manifest.pages.flatMap(page => [
    [page.legacy.replace(/\.md$/, ''), page],
    [path.basename(page.legacy, '.md'), page]
  ]));
  for (const page of manifest.pages) {
    const locale = page.sourceLocale ?? 'cn';
    const target = path.join(wiki, `${pageName(page, locale)}.md`);
    if (fs.existsSync(target)) throw new Error(`Refusing to overwrite imported page: ${target}`);
    const maps = new Map([...sources].map(([id, text]) => [id, headingMap(text)]));
    const body = outsideFences(sources.get(page.id), line => line.replace(/\]\(([^)]+)\)/g, (match, destination) => {
      if (/^(?:https?:|mailto:)/.test(destination)) return match;
      const [rawTarget, rawFragment] = destination.split('#');
      let decoded;
      try { decoded = decodeURIComponent(rawTarget); } catch { decoded = rawTarget; }
      const targetPage = decoded === '' ? page : legacy.get(decoded.replace(/^(?:\.\.\/|\.\/)/, '').replace(/\.md$/, ''));
      if (!targetPage) return match;
      const fragment = rawFragment ? maps.get(targetPage.id).get(decodeURIComponent(rawFragment)) : null;
      return `](${decoded === '' ? '' : pageName(targetPage, locale)}${fragment ? `#${fragment}` : ''})`;
    }));
    fs.writeFileSync(target, languageHeader(page, locale) + body);
    console.log(`Imported ${path.basename(target)}`);
  }
}

function anchors(text) {
  let index = 0;
  return outsideFences(text, line => /^(#{1,6})\s+/.test(line)
    ? `<a id="wiki-section-${++index}"></a>\n${line}` : line);
}

function navigation(page, locale, index) {
  const previous = manifest.pages[index - 1];
  const next = manifest.pages[index + 1];
  const links = [`[${locale === 'cn' ? '目录' : 'Contents'}](Home)`];
  if (previous) links.unshift(`[${locale === 'cn' ? '上一篇' : 'Previous'}: ${previous[locale]}](${pageName(previous, locale)})`);
  if (next) links.push(`[${locale === 'cn' ? '下一篇' : 'Next'}: ${next[locale]}](${pageName(next, locale)})`);
  return `\n<!-- wiki-nav -->\n\n---\n\n${links.join(' · ')}\n`;
}

function contents(locale) {
  return manifest.sections.map(section => `## ${section[locale]}\n\n` + manifest.pages
    .filter(page => page.section === section.id)
    .map(page => `- [${page[locale]}](${pageName(page, locale)})`).join('\n')).join('\n\n');
}

function sharedSidebar() {
  return '**[NekoJS Wiki](Home)**\n\n[中文](home_cn) · [English](home_us)\n\n' + manifest.sections.map(section =>
    `## ${section.cn} / ${section.us}\n\n` + manifest.pages.filter(page => page.section === section.id).map(page =>
      `- ${page.cn} / ${page.us} · [中文](${pageName(page, 'cn')}) · [EN](${pageName(page, 'us')})`).join('\n')).join('\n\n') + '\n';
}

function sharedHome() {
  return '# NekoJS Wiki\n\n[简体中文](home_cn) · [English](home_us)\n\n' + contents('cn') + '\n\n---\n\n' + contents('us') + '\n';
}

function syncPages() {
  manifest.pages.forEach((page, index) => {
    for (const locale of ['cn', 'us']) {
      const filename = path.join(wiki, `${pageName(page, locale)}.md`);
      if (!fs.existsSync(filename)) throw new Error(`Missing page: ${filename}`);
      fs.writeFileSync(filename, languageHeader(page, locale) + anchors(bodyOf(fs.readFileSync(filename, 'utf8'))) + navigation(page, locale, index));
    }
  });
  fs.writeFileSync(path.join(wiki, '_Sidebar.md'), sharedSidebar());
  fs.writeFileSync(path.join(wiki, 'Home.md'), sharedHome());
}

function snapshotLegacy() {
  for (const page of manifest.pages) {
    page.legacyDigest = crypto.createHash('sha256').update(fs.readFileSync(path.join(wiki, page.legacy), 'utf8').replace(/\r\n/g, '\n')).digest('hex');
  }
  fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + '\n');
}

function legacyPages() {
  const check = checkPages(false);
  if (check.length) throw new Error('Complete and validate all pairs before replacing legacy pages');
  for (const page of manifest.pages) {
    if (page.legacy === 'Home.md') continue;
    const existing = fs.readFileSync(path.join(wiki, page.legacy), 'utf8').replace(/\r\n/g, '\n');
    if (existing.startsWith(`<!-- wiki-legacy: ${page.id} -->`)) continue;
    const currentDigest = crypto.createHash('sha256').update(existing).digest('hex');
    if (!page.legacyDigest || currentDigest !== page.legacyDigest) throw new Error(`Legacy page changed during migration: ${page.legacy}`);
  }
  for (const page of manifest.pages) {
    if (page.legacy === 'Home.md') continue;
    const locale = page.sourceLocale ?? 'cn';
    const filename = path.resolve(wiki, page.legacy);
    if (!filename.startsWith(wiki + path.sep)) throw new Error('Legacy path escapes wiki');
    const prefix = page.legacy.includes('/') ? '../' : '';
    fs.writeFileSync(filename, `<!-- wiki-legacy: ${page.id} -->\n\n# ${page[locale]}\n\n${locale === 'cn' ? '本页已迁移到' : 'This page has moved to'} [${page[locale]}](${prefix}${pageName(page, locale)})${locale === 'cn' ? '。' : '.'}\n\n[中文](${prefix}${pageName(page, 'cn')}) · [English](${prefix}${pageName(page, 'us')})\n`);
  }
  fs.writeFileSync(path.join(wiki, 'en_us/Home.md'), '<!-- wiki-legacy: home -->\n\n# NekoJS Wiki\n\n[English](../home_us) · [中文](../home_cn)\n');
  fs.writeFileSync(path.join(wiki, 'en_us/_Sidebar.md'), '<!-- wiki-legacy-navigation -->\n\n[Shared bilingual contents](../Home) · [English](../home_us) · [中文](../home_cn)\n');
}

export function tableShape(text) {
  const result = [];
  let table = null;
  outsideFences(text, line => {
    if (/^\s*\|/.test(line)) {
      if (!table) { table = []; result.push(table); }
      table.push(line.replace(/\\\|/g, '').split('|').length);
    } else table = null;
    return line;
  });
  return result;
}

export function fenceShape(text) {
  const languages = [];
  let delimiter = null;
  for (const line of text.split('\n')) {
    const match = line.match(/^\s*(`{3,}|~{3,})(.*)$/);
    if (!match) continue;
    if (delimiter === null) {
      delimiter = match[1];
      languages.push(match[2].trim());
    } else if (match[1][0] === delimiter[0] && match[1].length >= delimiter.length && match[2].trim() === '') {
      delimiter = null;
    }
  }
  return { languages, closed: delimiter === null };
}

export function markdownLinks(line) {
  const prose = line.replace(/(`+)(.*?)\1(?!`)/g, match => ' '.repeat(match.length));
  return [...prose.matchAll(/\]\(([^)]*)\)/g)].map(match => match[1]);
}

function checkPages(checkDigest = true) {
  const failures = [];
  const ids = new Set();
  for (const page of manifest.pages) {
    if (ids.has(page.id)) failures.push(`DUPLICATE ID: ${page.id}`);
    ids.add(page.id);
    if (!/^[a-z][a-z0-9-]*$/.test(page.id)) failures.push(`INVALID ID: ${page.id}`);
    if (!manifest.sections.some(section => section.id === page.section)) failures.push(`UNKNOWN SECTION: ${page.id}`);
    const texts = {};
    for (const locale of ['cn', 'us']) {
      const filename = path.join(wiki, `${pageName(page, locale)}.md`);
      if (!fs.existsSync(filename)) { failures.push(`MISSING: ${pageName(page, locale)}`); continue; }
      texts[locale] = fs.readFileSync(filename, 'utf8');
      if (!texts[locale].startsWith(languageHeader(page, locale).trim())) failures.push(`LANGUAGE LINK: ${pageName(page, locale)}`);
      if (!fenceShape(texts[locale]).closed) failures.push(`UNCLOSED FENCE: ${pageName(page, locale)}`);
      const index = manifest.pages.indexOf(page);
      if (!texts[locale].endsWith(navigation(page, locale, index))) failures.push(`NAVIGATION ORDER: ${pageName(page, locale)}`);
      outsideFences(texts[locale], line => {
        for (const destination of markdownLinks(line)) {
          if (destination === '') { failures.push(`EMPTY LINK: ${pageName(page, locale)}`); continue; }
          if (/^(?:https?:|mailto:)/.test(destination)) continue;
          const [target, fragment] = destination.split('#');
          const targetFile = target ? path.resolve(wiki, target.endsWith('.md') ? target : `${target}.md`) : filename;
          if (!fs.existsSync(targetFile)) { failures.push(`BROKEN LINK: ${pageName(page, locale)} -> ${destination}`); continue; }
          if (target && /_(cn|us)$/.test(target) && !target.endsWith(`_${locale}`)
            && target !== pageName(page, locale === 'cn' ? 'us' : 'cn')) failures.push(`WRONG LOCALE: ${pageName(page, locale)} -> ${destination}`);
          if (fragment && !fs.readFileSync(targetFile, 'utf8').includes(`id="${fragment}"`)) failures.push(`BROKEN ANCHOR: ${pageName(page, locale)} -> ${destination}`);
        }
        return line;
      });
    }
    if (texts.cn && texts.us) {
      if (JSON.stringify(headings(bodyOf(texts.cn)).map(heading => heading.level)) !== JSON.stringify(headings(bodyOf(texts.us)).map(heading => heading.level))) failures.push(`HEADING STRUCTURE: ${page.id}`);
      if (JSON.stringify(tableShape(bodyOf(texts.cn))) !== JSON.stringify(tableShape(bodyOf(texts.us)))) failures.push(`TABLE STRUCTURE: ${page.id}`);
      if (JSON.stringify(fenceShape(bodyOf(texts.cn))) !== JSON.stringify(fenceShape(bodyOf(texts.us)))) failures.push(`CODE BLOCK STRUCTURE: ${page.id}`);
      if (checkDigest && (!page.sourceDigest || page.sourceDigest !== digest(texts.cn))) failures.push(`STALE OR UNSTAMPED: ${page.id}`);
      if (checkDigest && (!page.translationDigest || page.translationDigest !== digest(texts.us))) failures.push(`UNREVIEWED TRANSLATION: ${page.id}`);
    }
  }
  for (const filename of fs.readdirSync(wiki)) {
    const match = filename.match(/^(.*)_(cn|us)\.md$/);
    if (match && !ids.has(match[1])) failures.push(`ORPHANED: ${filename}`);
  }
  for (const [filename, expected] of [['Home.md', sharedHome()], ['_Sidebar.md', sharedSidebar()]]) {
    if (!fs.existsSync(path.join(wiki, filename)) || fs.readFileSync(path.join(wiki, filename), 'utf8').replace(/\r\n/g, '\n') !== expected) failures.push(`GENERATED CONTENTS: ${filename}`);
  }
  return failures;
}

function stampPages() {
  const requested = process.argv.slice(3);
  const all = requested.includes('--all');
  const selected = all ? manifest.pages : manifest.pages.filter(page => requested.includes(page.id));
  const unknown = requested.filter(id => id !== '--all' && !manifest.pages.some(page => page.id === id));
  if (!selected.length || unknown.length) throw new Error('Specify reviewed topic IDs or --all');
  const failures = checkPages(false);
  if (failures.length) throw new Error(failures.join('\n'));
  for (const page of selected) {
    page.sourceDigest = digest(fs.readFileSync(path.join(wiki, `${pageName(page, 'cn')}.md`), 'utf8'));
    page.translationDigest = digest(fs.readFileSync(path.join(wiki, `${pageName(page, 'us')}.md`), 'utf8'));
    page.translationStatus = 'draft';
    page.humanReview = 'pending';
  }
  fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + '\n');
}

const direct = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (direct) {
  const command = process.argv[2] ?? 'check';
  const strict = process.argv.includes('--strict');
  const quiet = process.argv.includes('--quiet');
  if (process.argv.includes('--help')) console.log('node scripts/wiki-docs.mjs [check|sync|stamp TOPIC...|stamp --all|legacy] [--strict] [--quiet] [--structure-only]');
  else if (command === 'import') importPages();
  else if (command === 'snapshot-legacy') snapshotLegacy();
  else if (command === 'sync') syncPages();
  else if (command === 'legacy') legacyPages();
  else if (command === 'stamp') stampPages();
  else if (command === 'check') {
    const failures = checkPages(!process.argv.includes('--structure-only'));
    for (const failure of failures) console.log(failure);
    if (!quiet || failures.length) console.log(`${manifest.pages.length} bilingual topics; ${failures.length} problem(s).`);
    if (strict && failures.length) process.exitCode = 1;
  } else if (command === '--help') console.log('node scripts/wiki-docs.mjs [check|sync|stamp|legacy] [--strict] [--quiet] [--structure-only]');
  else throw new Error(`Unknown command: ${command}`);
}
