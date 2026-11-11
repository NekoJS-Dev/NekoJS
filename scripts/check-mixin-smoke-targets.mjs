import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { execFileSync } from 'node:child_process';

// Bytecode dispatch audit for trusted local smoke examples, not native execution or a declaration compiler.
const [jar, directory] = process.argv.slice(2);
assert.ok(jar && directory, 'Usage: node scripts/check-mixin-smoke-targets.mjs <minecraft.jar> <trusted-smoke-declarations>');
const hooks = [];
const Mixin = { target(target, configure) {
    configure({ inject(selector, id, configureHook) {
        const builder = { staticMethod: false };
        configureHook(builder);
        hooks.push({ target, selector, id, ...builder });
    } });
} };
for (const file of fs.readdirSync(directory).filter(file => file.endsWith('.mixin.js'))) {
    const source = fs.readFileSync(path.join(directory, file), 'utf8');
    vm.runInNewContext(source.replace(/import\s*\{\s*Mixin\s*\}\s*from\s*["']nekojs\/mixin["'];?/, ''), { Mixin }, { filename: file, timeout: 1000 });
}
const classes = new Map();
function describe(name) {
    if (classes.has(name)) return classes.get(name);
    const output = execFileSync('javap', ['-classpath', jar, '-s', name], { encoding: 'utf8' });
    const parent = output.match(/\bclass\s+\S+\s+extends\s+([^\s{]+)/)?.[1];
    const methods = new Set();
    const lines = output.split(/\r?\n/);
    for (let i = 0; i < lines.length; i++) {
        const descriptor = lines[i].trim().match(/^descriptor: (\(.*)$/)?.[1];
        if (!descriptor) continue;
        const method = lines[i - 1].trim().match(/([^\s(]+)\(/)?.[1];
        if (method) methods.add(method + descriptor);
    }
    const result = { parent, methods };
    classes.set(name, result);
    return result;
}
function owner(receiver, selector) {
    for (let name = receiver; name; name = describe(name).parent) {
        if (describe(name).methods.has(selector)) return name;
    }
}
let checked = 0;
for (const hook of hooks.filter(hook => hook.target === 'net.minecraft.server.MinecraftServer' && !hook.staticMethod)) {
    for (const receiver of ['net.minecraft.client.server.IntegratedServer', 'net.minecraft.server.dedicated.DedicatedServer']) {
        const actual = owner(receiver, hook.selector);
        assert.equal(actual, hook.target,
            hook.id + ': ' + receiver + '.' + hook.selector + ' dispatches to ' + actual + ', bypassing the declared smoke target');
        checked++;
    }
}
assert.ok(checked > 0, 'No shared-server smoke targets were checked');
console.log('MixinJS smoke dispatch audit passed: ' + checked + ' concrete receiver/selector pairs (not a native runtime test)');
