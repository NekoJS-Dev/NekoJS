package com.tkisor.nekojs.core.node;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * nekojs/node 内置模块的端到端回归：真实安装 manifest 到 GraalJS Context，
 * 在 guest 侧验证 2026-08 修复面——assert.rejects/doesNotReject 调用 fn、
 * deepEqual 宽松语义、path.parse/format 普通对象化与 posix/win32 独立语义、
 * buffer base64/hex/base64url 编码与字符串 needle、fs.lstat 与 withFileTypes、
 * os.EOL 平台化、module.isBuiltin、events.errorMonitor、process.exit/stdout。
 */
class NodeModulesJsRegressionTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    private static final class Installed implements AutoCloseable {
        final Context context = Context.newBuilder("js").allowAllAccess(true).build();
        final NekoNodeRuntime runtime = NekoNodeModuleInstaller.install(context, ScriptType.TEST);
        final List<String> failures = new ArrayList<>();
        final List<String> marks = new ArrayList<>();

        Installed() {
            context.getBindings("js").putMember("__out", failures);
            context.getBindings("js").putMember("__marks", marks);
        }

        void eval(String js) {
            context.eval("js", js);
        }

        /** 模拟 ScriptManager.flushTestTimers 的驱动循环，并强制排空 promise 微任务队列。 */
        void drainTimers() throws InterruptedException {
            for (int i = 0; i < 1000 && runtime.hasPendingTimers(); i++) {
                runtime.flushReadyTimers();
                Thread.sleep(1L);
            }
            runtime.flushReadyTimers();
            context.eval("js", "0");
        }

        @Override
        public void close() {
            runtime.close();
            context.close();
        }
    }

    @Test
    void nodeModuleSurfaceMatchesNodeSemantics() {
        try (Installed installed = new Installed()) {
            installed.eval(SYNC_CHECKS_JS);
            assertTrue(installed.failures.isEmpty(), "failed checks: " + installed.failures);
        }
    }

    @Test
    void asyncAssertRejectsInvokesFunctionAndReports() throws InterruptedException {
        try (Installed installed = new Installed()) {
            installed.eval(ASYNC_CHECKS_JS);
            installed.drainTimers();
            assertTrue(installed.failures.isEmpty(), "failed: " + installed.failures);
            assertTrue(installed.marks.contains("rejects.sync.ok"), "marks: " + installed.marks);
            assertTrue(installed.marks.contains("rejects.async.ok"), "marks: " + installed.marks);
            assertTrue(installed.marks.contains("dnr.ok"), "marks: " + installed.marks);
            assertTrue(installed.marks.contains("dnr.throw.caught:ERR_ASSERTION"), "marks: " + installed.marks);
            assertTrue(installed.marks.contains("nextTick.ran"), "marks: " + installed.marks);
            assertTrue(installed.marks.contains("blob.text:ab"), "marks: " + installed.marks);
            assertFalse(installed.marks.contains("dnr.throw.false-pass"), "marks: " + installed.marks);
        }
    }

    private static final String SYNC_CHECKS_JS = """
const out = globalThis.__out
function check(name, cond) { if (!cond) out.add(name) }
const req = globalThis.__nekoNodeResolve
const assert = req('node:assert')
const path = req('node:path')
const buffer = req('node:buffer')
const Buffer = buffer.Buffer
const Blob = buffer.Blob
const os = req('node:os')
const fs = req('node:fs')
const mod = req('node:module')
const events = req('node:events')
const util = req('node:util')

// path.parse 解包为普通对象（此前泄漏 Java record 访问器方法）
const p1 = path.parse('b.txt')
check('path.parse.base', p1.base === 'b.txt')
check('path.parse.ext', p1.ext === '.txt')
check('path.parse.name', p1.name === 'b')
check('path.parse.dir', p1.dir === '.')
check('path.parse.root', p1.root === '')
// format 接受普通对象（此前宿主只接受 PathParts record，guest 无法构造）
check('path.format.plain', path.format({ dir: 'x', base: 'a.txt' }).endsWith('a.txt'))
check('path.format.nameext', path.format({ name: 'a', ext: '.txt' }) === 'a.txt')

// posix 子模块独立语义（此前 parse/format/resolve/relative 被平台实现劫持）
check('posix.sep', path.posix.sep === '/')
check('posix.parse', path.posix.parse('/a/b.txt').dir === '/a' && path.posix.parse('/a/b.txt').ext === '.txt')
check('posix.parse.root', path.posix.parse('/a/b.txt').root === '/')
check('posix.parse.trailing', path.posix.parse('/a/b/').base === 'b')
check('posix.format', path.posix.format({ dir: '/x/y', base: 'a.txt' }) === '/x/y/a.txt')
check('posix.format.nameext', path.posix.format({ dir: '/x', name: 'a', ext: '.txt' }) === '/x/a.txt')
check('posix.resolve', path.posix.resolve('/a', 'b') === '/a/b')
check('posix.resolve.parent', path.posix.resolve('/a/b', '../c') === '/a/c')
check('posix.relative', path.posix.relative('/a/b', '/a/c') === '../c')
check('posix.relative.same', path.posix.relative('/a', '/a') === '.')
check('posix.join', path.posix.join('/a', 'b', 'c') === '/a/b/c')

// win32 子模块独立语义
const wPath = String.raw`C:\\a\\b.txt`
const w1 = path.win32.parse(wPath)
check('win32.parse.root', w1.root === 'C:' + path.win32.sep)
check('win32.parse.dir', w1.dir === String.raw`C:\\a`)
check('win32.parse.base', w1.base === 'b.txt')
check('win32.format', path.win32.format({ dir: String.raw`C:\\x`, base: 'a.txt' }) === String.raw`C:\\x\\a.txt`)
check('win32.parse.fwd', path.win32.parse('C:/x/y.txt').base === 'y.txt')

// buffer 编码（此前 fromString 对 base64/hex 抛 IllegalCharsetNameException）
check('b64.decode', Buffer.from('aGk=', 'base64').toString() === 'hi')
check('b64.encode', Buffer.from('hi').toString('base64') === 'aGk=')
check('b64url.encode', Buffer.from('hi').toString('base64url') === 'aGk')
check('b64url.decode', Buffer.from('aGk', 'base64url').toString() === 'hi')
check('isEncoding.b64url', Buffer.isEncoding('base64url'))
check('hex.decode', Buffer.from('48656c6c6f', 'hex').toString() === 'Hello')
check('hex.encode', Buffer.from('Hello').toString('hex') === '48656c6c6f')

// buffer 字符串 needle（此前宿主收到 null 抛错）
check('indexOf.str', Buffer.from('abc').indexOf('bc') === 1)
check('indexOf.str.miss', Buffer.from('abc').indexOf('z') === -1)
check('indexOf.buf', Buffer.from('abc').indexOf(Buffer.from('bc')) === 1)
check('includes.str', Buffer.from('abc').includes('ab'))

// byteLength 非字符串入参（此前 String(value) 得到 "[object ...]" 长度）
check('byteLength.buf', Buffer.byteLength(Buffer.from('héllo')) === 6)
check('byteLength.str', Buffer.byteLength('héllo') === 6)
check('byteLength.view', Buffer.byteLength(new Uint8Array(5)) === 5)

// write / alloc fill
const wb = Buffer.alloc(8)
check('write.returns', wb.write('xyz', 2) === 5)
check('write.bytes', wb.toString('hex') === '000078797a000000')
check('alloc.fill.str', Buffer.alloc(4, 'ab').toString() === 'abab')
check('alloc.fill.num', Buffer.alloc(3, 7).toString('hex') === '070707')

// Blob.size 按字节计（此前按 UTF-16 code unit 计数）
check('blob.size', new Blob(['héllo']).size === 6)

// assert 宽松/严格 deepEqual 语义分离（此前 deepEqual 实为严格、notDeepEqual 是别名）
check('deepStrictEqual.rejects.coercion', (() => { try { assert.deepStrictEqual(['1'], [1]); return false } catch (_) { return true } })())
check('deepEqual.allows.coercion', (() => { try { assert.deepEqual(['1'], [1]); return true } catch (e) { out.add('dbg.deepEqual:' + e); return false } })())
check('notDeepEqual.loose', (() => { try { assert.notDeepEqual(['1'], [1]); return false } catch (_) { return true } })())
check('notDeepStrictEqual.strict', (() => { try { assert.notDeepStrictEqual([1], [1]); return false } catch (_) { return true } })())

// 深比较环引用保护
const ca = { name: 'c' }; ca.self = ca
const cb = { name: 'c' }; cb.self = cb
check('assert.circular', (() => { try { assert.deepStrictEqual(ca, cb); return true } catch (e) { out.add('dbg.circular:' + e); return false } })())
check('util.circular', (() => { try { return util.isDeepStrictEqual(ca, cb) } catch (e) { out.add('dbg.util.circular:' + e); return false } })())

// os.EOL 平台化（此前硬编码 \\n）
check('os.EOL', os.EOL === (process.platform === 'win32' ? '\\r\\n' : '\\n'))

// module.isBuiltin / builtinModules
check('isBuiltin.node', mod.isBuiltin('node:fs'))
check('isBuiltin.bare', mod.isBuiltin('fs'))
check('isBuiltin.negative', !mod.isBuiltin('java:java.util.Map'))
check('builtinModules', mod.builtinModules.indexOf('fs') >= 0 && mod.builtinModules.indexOf('node:fs') < 0)

// events.errorMonitor：监听器被调用且不阻止抛出
const emitter = new events.EventEmitter()
let monitored = false
emitter.on(events.errorMonitor, () => { monitored = true })
let errorThrew = false
try { emitter.emit('error', new Error('boom')) } catch (_) { errorThrew = true }
check('errorMonitor', errorThrew && monitored)
check('setMaxListeners.infinity', (() => { try { emitter.setMaxListeners(Infinity); return true } catch (_) { return false } })())

// process.exit no-op、stdout.write
let exitThrew = false
try { process.exit(0) } catch (_) { exitThrew = true }
check('process.exit.noop', !exitThrew)
check('process.stdout.write', process.stdout.write('x') === true)

// fs.lstat（此前调用了不存在的宿主方法 lstatSync）+ withFileTypes + 回调必填
const dirName = 'nekojs/node-regression-' + Date.now()
fs.mkdirSync(dirName, { recursive: true })
fs.writeFileSync(dirName + '/f.txt', 'hello')
check('fs.lstatSync.file', fs.lstatSync(dirName + '/f.txt').isFile())
check('fs.lstatSync.size', fs.lstatSync(dirName + '/f.txt').size === 5)
const entries = fs.readdirSync(dirName, { withFileTypes: true })
check('fs.withFileTypes', (() => {
  if (!entries || entries.length !== 1) { out.add('dbg.wft.count:' + entries.length); return false }
  const d = entries[0]
  if (d.name !== 'f.txt') { out.add('dbg.wft.name:' + d.name); return false }
  if (!d.isFile()) { out.add('dbg.wft.isFile:' + d.isFile() + ' dir:' + d.isDirectory()); return false }
  return true
})())
check('fs.callback.required', (() => { try { fs.readFile('whatever'); return false } catch (_) { return true } })())
fs.rmSync(dirName, { recursive: true, force: true })
check('fs.cleanup', !fs.existsSync(dirName))
""";

    private static final String ASYNC_CHECKS_JS = """
const out = globalThis.__out
const marks = globalThis.__marks
const req = globalThis.__nekoNodeResolve
const assert = req('node:assert')
const buffer = req('node:buffer')

// assert.rejects 此前从不调用 fn（任何合法用例必失败）；同步 throw 与异步 reject 都按 rejection 处理
assert.rejects(() => { throw new Error('boom-sync') }, /boom-sync/).then(
  () => marks.add('rejects.sync.ok'),
  (e) => out.add('rejects.sync.fail:' + e))
assert.rejects(() => new Promise((_, reject) => setTimeout(() => reject(new Error('boom-async')), 5)), /boom-async/).then(
  () => marks.add('rejects.async.ok'),
  (e) => out.add('rejects.async.fail:' + e))
// doesNotReject 此前从不调用 fn（无条件假阳性）；真异常必须报 ERR_ASSERTION
assert.doesNotReject(() => Promise.resolve(1)).then(
  () => marks.add('dnr.ok'),
  (e) => out.add('dnr.fail:' + e))
assert.doesNotReject(() => { throw new Error('dnr-boom') }).then(
  () => out.add('dnr.throw.false-pass'),
  (e) => marks.add('dnr.throw.caught:' + (e && e.code)))
// nextTick 正常路径 + Blob.text/arrayBuffer
process.nextTick(() => marks.add('nextTick.ran'))
new buffer.Blob(['a', 'b']).text().then((t) => marks.add('blob.text:' + t))
new buffer.Blob(['ab']).arrayBuffer().then((ab) => marks.add('blob.ab:' + ab.byteLength))
""";
}
