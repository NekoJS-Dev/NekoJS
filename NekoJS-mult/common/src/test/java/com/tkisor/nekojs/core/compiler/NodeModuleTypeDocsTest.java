package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.core.plugin.TypeDocsRegister;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NodeModuleTypeDocs} 测试：{@code extractTS}（.ts 类型注解）
 * 以及 {@code registerBuiltin} 的端到端提取链路。
 *
 * <p>成员签名以 {@code export} 暴露（{@code declare module} 内非 export 声明对 require() 不可见）。
 */
class NodeModuleTypeDocsTest {

    // ============ extractTS ============

    @Test
    void extractsTypedNodeModule() {
        String src = """
;(function () {
  const runtime = globalThis.__nekoNodeInternal.runtime

  interface ParsedPath { root: string; dir: string; base: string; ext: string; name: string }
  type Platform = 'posix' | 'win32';

  function normalize(p: string): string { return runtime.normalize(p) }
  function join(...parts: string[]): string { return runtime.join(parts) }
  function id<T>(x: T): T { return x }
  const sep: string = runtime.sep
  const delimiter: string = runtime.delimiter

  const api = {
    normalize,
    join,
    id,
    sep,
    delimiter,
    parse(path: string): ParsedPath { return runtime.parse(path) },
    version: '1.0.0'
  }

  globalThis.__nekoNodeDefine(['path', 'node:path'], api)
})()
""";
        String out = NodeModuleTypeDocs.extractTS(src);
        assertFalse(out.isBlank(), out);
        assertTrue(out.contains("declare module 'path' {"), out);
        assertTrue(out.contains("declare module 'node:path' {"), out);
        assertTrue(out.contains("export function normalize(p: string): string;"), out);
        assertTrue(out.contains("export function join(...parts: string[]): string;"), out);
        assertTrue(out.contains("export function id<T>(x: T): T;"), out);
        assertTrue(out.contains("export const sep: string;"), out);
        assertTrue(out.contains("export const delimiter: string;"), out);
        assertTrue(out.contains("export function parse(path: string): ParsedPath;"), out);
        assertTrue(out.contains("export const version: string;"), out);
        assertTrue(out.contains("interface ParsedPath { root: string; dir: string; base: string; ext: string; name: string }"), out);
        assertTrue(out.contains("type Platform = 'posix' | 'win32';"), out);
    }

    @Test
    void extractsDirectObjectLiteralExports() {
        String src = """
;(function () {
  const runtime = globalThis.__nekoNodeInternal.runtime
  function arch(): string { return runtime.arch() }
  globalThis.__nekoNodeDefine(['os', 'node:os'], { arch, version: '1.2.3' })
})()
""";
        String out = NodeModuleTypeDocs.extractTS(src);
        assertTrue(out.contains("declare module 'os' {"), out);
        assertTrue(out.contains("declare module 'node:os' {"), out);
        assertTrue(out.contains("export function arch(): string;"), out);
        assertTrue(out.contains("export const version: string;"), out);
    }

    @Test
    void handlesGenericAndArrayReturnTypes() {
        String src = """
;(function () {
  function cpus(): CpuInfo[] { return [] }
  const api = { cpus }
  globalThis.__nekoNodeDefine(['os'], api)
})()
""";
        String out = NodeModuleTypeDocs.extractTS(src);
        assertTrue(out.contains("export function cpus(): CpuInfo[];"), out);
    }

    @Test
    void arrowFunctionExportBecomesFunctionSig() {
        String src = """
;(function () {
  const api = { run: (n: number): boolean => n > 0 }
  globalThis.__nekoNodeDefine(['m'], api)
})()
""";
        String out = NodeModuleTypeDocs.extractTS(src);
        assertTrue(out.contains("export function run(n: number): boolean;"), out);
    }

    @Test
    void tsSingleStringIdIsAccepted() {
        String src = """
;(function () {
  const api = { a: 1 }
  globalThis.__nekoNodeDefine('solo', api)
})()
""";
        String out = NodeModuleTypeDocs.extractTS(src);
        assertTrue(out.contains("declare module 'solo' {"), out);
        assertTrue(out.contains("export const a: number;"), out);
    }

    // ============ registerBuiltin 端到端 ============

    @Test
    void registerBuiltinAllNodeModulesAutoExtracted() {
        List<ManualDeclarationCatalogEntry> entries = new ArrayList<>();
        TypeDocsRegister registry = new TypeDocsRegister() {
            @Override
            public void register(TypeDocCatalogEntry entry) {}
            @Override
            public void registerManualDeclaration(ManualDeclarationCatalogEntry entry) {
                entries.add(entry);
            }
        };
        NodeModuleTypeDocs.registerBuiltin(registry);
        String all = entries.stream()
            .map(ManualDeclarationCatalogEntry::declaration)
            .reduce("", String::concat);
        // 自动提取的 example.ts 模块声明
        assertTrue(all.contains("declare module 'node:example' {"), all);
        assertTrue(all.contains("declare module 'example' {"), all);
        assertTrue(all.contains("export function getInfo(id: number): ExampleInfo;"), all);
        assertTrue(all.contains("export function status(): Status;"), all);
        assertTrue(all.contains("export const version: string;"), all);
        assertTrue(all.contains("interface ExampleInfo { id: number; name: string }"), all);
        assertTrue(all.contains("type Status = 'ok' | 'error';"), all);
        // 所有内置 node 模块均由 .ts 自动提取（无手写回退）；node:path 关键签名在
        assertTrue(all.contains("declare module 'node:path'"), all);
        assertTrue(all.contains("join(...parts: string[]): string"), all);
    }

    // ============ 通用降级 ============

    @Test
    void tsReturnsEmptyWhenNoDefineCall() {
        assertEquals("", NodeModuleTypeDocs.extractTS("const x = 1"));
    }

    @Test
    void returnsEmptyForBlankInput() {
        assertEquals("", NodeModuleTypeDocs.extractTS(""));
        assertEquals("", NodeModuleTypeDocs.extractTS(null));
    }

    @Test
    void ignoresDefineAssignment() {
        String src = "globalThis.__nekoNodeDefine = function (names, value) {}";
        assertEquals("", NodeModuleTypeDocs.extractTS(src));
    }

    @Test
    void exampleTsIsErasedToRunnableJs() throws Exception {
        // example.ts 在运行时由 loadManifest 擦除后 eval；确认擦除不抛异常且类型被干净移除
        String src = readResource("nekojs/node/modules/example.ts");
        String js = NekoTypeScriptCompiler.eraseTypescript(Path.of("example.ts"), src);
        assertFalse(js.contains("interface ExampleInfo"), "interface must be erased:\n" + js);
        assertFalse(js.contains("type Status"), "type alias must be erased:\n" + js);
        assertFalse(js.contains(": number"), "param type annotation must be erased:\n" + js);
        assertFalse(js.contains(": string"), "const type annotation must be erased:\n" + js);
        assertTrue(js.contains("__nekoNodeDefine(['example', 'node:example']"), "runtime define must remain:\n" + js);
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in = NodeModuleTypeDocsTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "resource not found: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ============ extractTS 增强：class / 嵌套对象 / 多 define / getter-setter ============

    @Test
    void extractsClassMembers() {
        String src = """
;(function () {
  class EventEmitter {
    private _events: Record<string, Function> = {};
    static defaultMaxListeners: number = 10;
    constructor() {}
    on(name: string, fn: () => void): this { return this }
    static listenerCount(e: EventEmitter, name: string): number { return 0 }
    get maxListeners(): number { return 10 }
    set maxListeners(v: number) {}
  }
  const api = { EventEmitter }
  globalThis.__nekoNodeDefine(['events', 'node:events'], api)
})()
""";
        String out = NodeModuleTypeDocs.extractTS(src);
        assertTrue(out.contains("declare module 'node:events' {"), out);
        assertTrue(out.contains("export declare class EventEmitter"), out);
        assertTrue(out.contains("constructor();"), out);
        assertTrue(out.contains("on(name: string, fn: () => void): this;"), out);
        assertTrue(out.contains("static defaultMaxListeners: number;"), out);
        assertTrue(out.contains("static listenerCount(e: EventEmitter, name: string): number;"), out);
        assertTrue(out.contains("maxListeners: number;"), out);
    }

    @Test
    void extractsNestedObjectInlineType() {
        String src = """
;(function () {
  const util = {
    types: {
      isPromise: (value: unknown): boolean => !!value,
      isMap: (value: unknown): value is Map<unknown, unknown> => value instanceof Map
    },
    version: '1.0'
  }
  globalThis.__nekoNodeDefine(['util', 'node:util'], util)
})()
""";
        String out = NodeModuleTypeDocs.extractTS(src);
        assertTrue(out.contains("export const types:"), out);
        assertTrue(out.contains("isPromise(value: unknown): boolean"), out);
        assertTrue(out.contains("isMap(value: unknown): value is Map<unknown, unknown>"), out);
        assertTrue(out.contains("export const version: string;"), out);
    }

    @Test
    void handlesMultipleDefineCalls() {
        String src = """
;(function () {
  const fs = { readFileSync: (p: string): Buffer => null }
  globalThis.__nekoNodeDefine(['fs', 'node:fs'], fs)
  const promises = { readFile: (p: string): Promise<Buffer> => null }
  globalThis.__nekoNodeDefine(['fs/promises', 'node:fs/promises'], promises)
})()
""";
        String out = NodeModuleTypeDocs.extractTS(src);
        assertTrue(out.contains("declare module 'node:fs' {"), out);
        assertTrue(out.contains("export function readFileSync(p: string): Buffer;"), out);
        assertTrue(out.contains("declare module 'node:fs/promises' {"), out);
        assertTrue(out.contains("export function readFile(p: string): Promise<Buffer>;"), out);
    }

    @Test
    void topLevelTypeAliasWithArrowMemberNotTruncated() {
        // type X = { m?: (a: T) => R; ... }：=> 的 > 曾被 typeDeclEnd 误判为泛型闭合（与 ()[]{} 共用 depth），
        // 在首个 ; 处提前截断，丢失闭合 } 与后续成员 → 外层 declare module 借用闭合 }，TS 报 "Expected '}'"。
        String src = """
;(function () {
  type NekoTestExtra = { mapStackLine?: (line: string) => string; formatError?: (error: unknown) => string }
  const api = {}
  globalThis.__nekoNodeDefine(['test', 'node:test'], api)
})()
""";
        String out = NodeModuleTypeDocs.extractTS(src);
        assertTrue(out.contains(
            "type NekoTestExtra = { mapStackLine?: (line: string) => string; formatError?: (error: unknown) => string }"),
            "含 => 成员的顶层 type 别名须完整提取（含闭合 }）: " + out);
        assertTrue(out.contains("declare module 'node:test' {"), out);
    }

    @Test
    void nodeModuleJSDocCommentsExtracted() throws Exception {
        // .ts node 模块顶层声明前的 /** */ JSDoc 随导出签名一并提取，使补全可见文档（实现与声明同源）
        String src = readResource("nekojs/node/modules/example.ts");
        String out = NodeModuleTypeDocs.extractTS(src);
        assertTrue(out.contains("/** 根据 id 构造示例信息。 */"), out);
        assertTrue(out.contains("/** 返回当前状态。 */"), out);
        // 注释紧贴其后的 export function 签名（经 indentBlock 各缩进 4 空格）
        assertTrue(out.contains("/** 根据 id 构造示例信息。 */\n    export function getInfo(id: number): ExampleInfo;"), out);
        assertTrue(out.contains("/** 返回当前状态。 */\n    export function status(): Status;"), out);
    }

    @Test
    void extractsObjectLiteralGetters() {
        String src = """
;(function () {
  const proc = {
    get platform(): string { return 'linux' },
    get pid(): number { return 1 },
    set exitCode(code: number) {}
  }
  globalThis.__nekoNodeDefine(['process', 'node:process'], proc)
})()
""";
        String out = NodeModuleTypeDocs.extractTS(src);
        assertTrue(out.contains("export const platform: string;"), out);
        assertTrue(out.contains("export const pid: number;"), out);
        assertTrue(out.contains("export const exitCode: number;"), out);
    }
}
