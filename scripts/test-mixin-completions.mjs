import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import ts from 'typescript';

// Exercise the editor language service, not just assignability of generated declarations.
function completions(directory, declaration, source, compilerOptions = {}) {
    const root = path.resolve(directory).replaceAll("\\", "/");
    const file = `${root}/completion.js`;
    const position = source.indexOf('/*cursor*/');
    assert.notEqual(position, -1);
    const text = source.replace('/*cursor*/', '');
    const options = { strict: true, allowJs: true, checkJs: true, noEmit: true, target: ts.ScriptTarget.ES2022, types: [], ...compilerOptions };
    const host = {
        getScriptFileNames: () => [path.join(root, declaration), file],
        getScriptVersion: () => '1',
        getScriptSnapshot: name => {
            const value = name === file ? text : ts.sys.readFile(name);
            return value === undefined ? undefined : ts.ScriptSnapshot.fromString(value);
        },
        getCurrentDirectory: () => root,
        getCompilationSettings: () => options,
        getDefaultLibFileName: ts.getDefaultLibFilePath,
        fileExists: name => name === file || ts.sys.fileExists(name),
        readFile: name => name === file ? text : ts.sys.readFile(name),
        readDirectory: ts.sys.readDirectory,
    };
    const service = ts.createLanguageService(host);
    try {
        return service.getCompletionsAtPosition(file, position, {})?.entries.map(entry => entry.name) ?? [];
    } finally {
        service.dispose();
    }
}

const declarations = 'common/build/probe-ts-mixin-declarations';
const text = fs.readFileSync(path.join(declarations, 'mixin.d.ts'), 'utf8');
const target = text.match(/"([^"]+\$Target)":/)?.[1];
assert.ok(target, 'generated fixture target');
const prefix = 'import { Mixin } from "nekojs/mixin";\n';
const classes = completions(declarations, 'mixin.d.ts', prefix + 'Mixin.target("/*cursor*/", target => {});');
assert.ok(classes.includes(target), 'runtime class literal completion');
const selectors = completions(declarations, 'mixin.d.ts', prefix + 'Mixin.target("' + target + '", target => { target.inject("/*cursor*/", "demo:id", build => {}); });');
assert.ok(selectors.includes('count(IZ)I'), 'exact method descriptor completion');
assert.ok(selectors.includes('reset()V'), 'static selector completion');
assert.ok(!selectors.includes('nativeMethod()V'), 'unsupported method is not suggested');
assert.ok(!selectors.includes('withheld()J'), 'long ABI is not suggested');
const builder = completions(declarations, 'mixin.d.ts', prefix + 'Mixin.target("' + target + '", target => { target.inject("reset()V", "demo:id", build => { build./*cursor*/ }); });');
for (const key of ['at', 'cancellable', 'scriptType', 'thread', 'require', 'staticMethod']) assert.ok(builder.includes(key), key);
assert.ok(!builder.includes('remap'), 'unsupported builder field');
for (const [key, values] of Object.entries({ at: ['HEAD', 'RETURN'], scriptType: ['SERVER', 'CLIENT'], thread: ['SERVER_MAIN', 'CLIENT_MAIN'] })) {
    const choices = completions(declarations, 'mixin.d.ts', prefix + 'Mixin.target("' + target + '", target => { target.inject("reset()V", "demo:id", build => { build.' + key + ' = "/*cursor*/"; }); });');
    for (const value of values) assert.ok(choices.includes(value), key + ':' + value);
}
const runtime = 'common/build/probe-ts-mixin-hooks';
const ids = completions(runtime, 'hooks.d.ts', 'MixinEvents.inject("/*cursor*/", event => {});');
assert.ok(ids.includes('demo:health'), 'installed hook literal completion');
assert.ok(!ids.includes('demo:pending'), 'pending hook is not suggested');
assert.ok(!ids.includes('demo:client'), 'other-side hook is not suggested');
const event = completions(runtime, 'hooks.d.ts', 'MixinEvents.inject("demo:health", event => { event./*cursor*/ });');
for (const key of ['self', 'args', 'returnValue']) assert.ok(event.includes(key), key);
assert.ok(!event.includes('cancel'), 'non-void RETURN has no cancel completion');
for (const checkJs of [true, false]) {
    const cancellation = completions(runtime, 'hooks.d.ts',
        'let mode = "cancel"; let cancellationCount = 0; function check(condition, label) {}\n' +
        'MixinEvents.inject("demo:cancel", event => {\n' +
        '  if (mode !== "cancel") return;\n' +
        '  cancellationCount++;\n' +
        '  check(event.args[0] === "NekoJS cancelled setter", "reference argument");\n' +
        '  event./*cursor*/\n' +
        '});', {
            checkJs,
            skipLibCheck: true,
            moduleDetection: ts.ModuleDetectionKind.Force,
            module: ts.ModuleKind.ESNext,
            moduleResolution: ts.ModuleResolutionKind.Bundler,
        });
    for (const key of ['self', 'args', 'cancel']) assert.ok(cancellation.includes(key), key + ':checkJs=' + checkJs);
    assert.ok(!cancellation.includes('returnValue'), 'void HEAD has no return replacement');
}
console.log('MixinJS language-service completion: class, selector, builder, installed ID and event fields including void cancellation passed');
