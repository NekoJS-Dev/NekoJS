package com.tkisor.nekojs.core.module.cjs;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CjsStaticAnalyzer} 单测：CJS 静态分析（字面量依赖提取、导出形状、__esModule、
 * 影子 require、字符串/注释/正则干扰）。
 */
class CjsStaticAnalyzerTest {

    @Test
    void emptySourceYieldsEmptyRecord() {
        assertEquals(CjsModuleRecord.EMPTY, CjsStaticAnalyzer.analyze(null));
        assertEquals(CjsModuleRecord.EMPTY, CjsStaticAnalyzer.analyze(""));
    }

    @Test
    void extractsLiteralRequireDependenciesInOrderDeduplicated() {
        CjsModuleRecord record = CjsStaticAnalyzer.analyze("""
                const fs = require('fs');
                const path = require('node:path');
                const again = require('fs');
                """);
        assertEquals(List.of("fs", "node:path"), record.staticDependencies());
        assertFalse(record.shadowedRequire());
    }

    @Test
    void ignoresRequireInsideStringsCommentsRegexAndTemplates() {
        CjsModuleRecord record = CjsStaticAnalyzer.analyze("""
                // require('comment-fake')
                const s = "require('string-fake')";
                const r = /require\\('regex-fake'\\)/;
                const t = `require('template-fake')`;
                const real = require('real');
                """);
        assertEquals(List.of("real"), record.staticDependencies());
    }

    @Test
    void ignoresMemberAccessRequireAndDynamicArguments() {
        CjsModuleRecord record = CjsStaticAnalyzer.analyze("""
                const helper = { require: (id) => id };
                helper.require('member-fake');
                const dynamic = require(someVar);
                const real = require('./real.js');
                """);
        assertEquals(List.of("./real.js"), record.staticDependencies());
    }

    @Test
    void detectsModuleExportsReassignment() {
        CjsModuleRecord record = CjsStaticAnalyzer.analyze("""
                module.exports = { answer: 42 };
                """);
        assertTrue(record.assignsModuleExports());
        assertFalse(record.assignsExportsMember());
    }

    @Test
    void detectsExportsMemberAssignment() {
        CjsModuleRecord record = CjsStaticAnalyzer.analyze("""
                exports.foo = 1;
                module.exports.bar = 2;
                """);
        assertFalse(record.assignsModuleExports());
        assertTrue(record.assignsExportsMember());
    }

    @Test
    void detectsEsmInteropMarker() {
        assertTrue(CjsStaticAnalyzer.analyze("exports.__esModule = true;").hasEsmInteropMarker());
        assertTrue(CjsStaticAnalyzer.analyze("module.exports.__esModule = true;").hasEsmInteropMarker());
        // 普通成员赋值不算互操作标记
        assertFalse(CjsStaticAnalyzer.analyze("exports.__esModule = 42;").hasEsmInteropMarker());
    }

    @Test
    void shadowedRequireMarksDependenciesUnreliable() {
        CjsModuleRecord asParam = CjsStaticAnalyzer.analyze("""
                function load(require) {
                    return require('via-param');
                }
                """);
        assertTrue(asParam.shadowedRequire());
        assertEquals(List.of(), asParam.staticDependencies());

        CjsModuleRecord asDecl = CjsStaticAnalyzer.analyze("let require = (id) => id; require('x');");
        assertTrue(asDecl.shadowedRequire(), "asDecl record=" + asDecl);
        assertEquals(List.of(), asDecl.staticDependencies());

        CjsModuleRecord asAssign = CjsStaticAnalyzer.analyze("require = fakeRequire; require('x');");
        assertTrue(asAssign.shadowedRequire());
        assertEquals(List.of(), asAssign.staticDependencies());
    }

    @Test
    void moduleExportsReassignmentAndMembersCanCoexist() {
        CjsModuleRecord record = CjsStaticAnalyzer.analyze("""
                exports.helper = () => {};
                if (condition) {
                    module.exports = { other: true };
                }
                """);
        assertTrue(record.assignsModuleExports());
        assertTrue(record.assignsExportsMember());
    }
}
