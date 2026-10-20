package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator;
import com.tkisor.nekojs.probe.backend.typescript.IndexFileGenerator;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.core.NekoSharedHostAccess;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TypeScriptInterfaceMergeTest {
    public static class Result { public String marker; }
    public static class SpecificResult extends Result { public int code; }
    public interface Source<T> {
        T value();
        T value(T input);
    }
    public interface SpecificSource {
        SpecificResult value();
        Result value(String input);
    }
    public interface Joined extends Source<Result>, SpecificSource {}
    public static class NumericArgument { public int numeric; }
    public static class TextArgument { public String textual; }
    public interface NumericSelection { <T extends NumericArgument> Result select(T input); }
    public interface TextSelection { <T extends TextArgument> SpecificResult select(T input); }
    public interface Selection extends NumericSelection, TextSelection {}
    public interface GenericJoined<T extends SpecificResult> extends Source<T>, SpecificSource {}

    public static class JoinedValue implements Joined {
        public SpecificResult value() {
            var result = new SpecificResult();
            result.marker = "result";
            result.code = 7;
            return result;
        }
        public Result value(Result input) { return input; }
        public Result value(String input) {
            var result = new Result();
            result.marker = input;
            return result;
        }
    }

    @Test
    void methodVariablesWithDifferentBoundsRemainDistinctOverloads() {
        long count = new TypeReflector().reflect(Selection.class).methods.stream()
                .filter(method -> method.name.equals("select")).count();
        assertEquals(2, count);
    }

    @Test
    void inheritedCovariantMethodsRemainCallableThroughBothParentContracts() throws Exception {
        var aliases = new TypeAliasRegistry();
        var generator = new IndexFileGenerator(new TypeScriptClassRenderer(aliases), new AdapterAliasGenerator(aliases));
        List<Class<?>> types = List.of(Result.class, SpecificResult.class, Source.class, SpecificSource.class, Joined.class,
                NumericArgument.class, TextArgument.class, NumericSelection.class, TextSelection.class, Selection.class,
                GenericJoined.class);
        Set<String> names = new LinkedHashSet<>();
        for (Class<?> type : types) names.add(type.getName());
        generator.prepareFunctionalInterfaceAliases(names, Set.of());
        for (Class<?> type : types) generator.predeclareClass(type.getName(), new TypeReflector().reflect(type), Set.of());
        String declaration = generator.generate(getClass().getPackageName(), types.stream()
                .map(type -> type.getName().substring(type.getPackageName().length() + 1)).toList(), List.of(), names);
        Path output = Path.of("build/probe-ts-interface-merge");
        Files.createDirectories(output);
        Files.writeString(output.resolve("index.d.ts"), declaration);
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022","module":"ESNext","moduleResolution":"Bundler","types":[]},"files":["index.d.ts","exercise.ts"]}
                """);
        Files.writeString(output.resolve("exercise.ts"), """
                import {
                    $TypeScriptInterfaceMergeTest$Joined as Joined,
                    $TypeScriptInterfaceMergeTest$Source as Source,
                    $TypeScriptInterfaceMergeTest$SpecificSource as SpecificSource,
                    $TypeScriptInterfaceMergeTest$Result as Result,
                    $TypeScriptInterfaceMergeTest$SpecificResult as SpecificResult,
                    $TypeScriptInterfaceMergeTest$NumericArgument as NumericArgument,
                    $TypeScriptInterfaceMergeTest$TextArgument as TextArgument
                } from 'java:com/tkisor/nekojs/probe/ir';
                import type {
                    $TypeScriptInterfaceMergeTest$Selection as Selection,
                    $TypeScriptInterfaceMergeTest$GenericJoined as GenericJoined
                } from 'java:com/tkisor/nekojs/probe/ir';
                declare const joined: Joined;
                const parent: Source<Result> = joined;
                const specificParent: SpecificSource = joined;
                const result: SpecificResult = joined.value();
                const marker: string = parent.value().marker;
                const code: number = specificParent.value().code;
                const echoed: Result = joined.value(new Result());
                const text: Result = joined.value('text');
                declare const selection: Selection;
                const numeric: Result = selection.select(new NumericArgument());
                const textual: SpecificResult = selection.select(new TextArgument());
                function generic<T extends SpecificResult>(value: GenericJoined<T>): T {
                    const parent: Source<T> = value;
                    const other: SpecificSource = value;
                    return value.value();
                }
                // @ts-expect-error The inherited covariant result is not a primitive.
                const invalid: number = joined.value();
                // @ts-expect-error The inherited overloads retain their actual inputs.
                joined.value(1);
                """);
    }

    @Test
    void productionHostAccessRetainsTheCovariantResultAndBothOverloads() {
        try (Context context = Context.newBuilder("js")
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(name -> false)
                .allowCreateThread(false)
                .allowCreateProcess(false)
                .build()) {
            context.getBindings("js").putMember("joined", new JoinedValue());
            assertEquals(7, context.eval("js", "joined.value().code").asInt());
            assertEquals("text", context.eval("js", "joined.value('text').marker").asString());
            assertEquals(7, context.eval("js", "joined.value(joined.value()).code").asInt());
        }
    }
}
