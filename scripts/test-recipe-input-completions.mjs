import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import ts from 'typescript';

const root = path.resolve('common/build/probe-ts-recipe-readable');
const configurationPath = path.join(root, 'tsconfig.json');
assert.ok(fs.existsSync(configurationPath), 'Run RecipeReadableInputTypeScriptTest to generate the canonical fixture first');
const configuration = ts.readConfigFile(configurationPath, ts.sys.readFile);
assert.equal(configuration.error, undefined, 'Readable recipe fixture configuration parses');
const parsed = ts.parseJsonConfigFileContent(configuration.config, ts.sys, root);
assert.deepEqual(parsed.errors, [], 'Readable recipe fixture configuration is valid');
const formatHost = {
    getCurrentDirectory: () => root,
    getCanonicalFileName: name => name,
    getNewLine: () => '\n',
};

// Compile the positive calls and every @ts-expect-error against real generator output.
const program = ts.createProgram(parsed.fileNames, parsed.options);
const diagnostics = ts.getPreEmitDiagnostics(program);
assert.equal(diagnostics.length, 0, ts.formatDiagnosticsWithColorAndContext(diagnostics, formatHost));
const declarations = fs.readFileSync(path.join(root, 'recipes.d.ts'), 'utf8');
assert.ok(declarations.includes('$List_<'), 'Original nominal List input alias remains visible');
for (const removed of ['SingleOrArray', 'Create$FluidStackInput', 'Create$ProcessingOutputInput']) {
    assert.ok(!declarations.includes(removed), 'Custom recipe projection is absent: ' + removed);
}
assert.ok(!declarations.includes('export type Create$ResultsInput'), 'Opaque whole-field alias is not emitted');

const prefix = 'import type { DocumentedRecipes } from "./recipes";\ndeclare const recipes: DocumentedRecipes;\n';
function inspect(source, operation) {
    const marker = '/*cursor*/';
    const position = source.indexOf(marker);
    assert.notEqual(position, -1, 'Exactly one cursor is present');
    assert.equal(source.indexOf(marker, position + marker.length), -1, 'Cursor marker is unambiguous');
    const text = source.replace(marker, '');
    const file = path.join(root, 'editor.ts');
    const equalPath = name => path.resolve(name) === file;
    const host = {
        getScriptFileNames: () => [...parsed.fileNames.filter(name => !name.endsWith('exercise.ts')), file],
        getScriptVersion: () => '1',
        getScriptSnapshot: name => {
            const value = equalPath(name) ? text : ts.sys.readFile(name);
            return value === undefined ? undefined : ts.ScriptSnapshot.fromString(value);
        },
        getCurrentDirectory: () => root,
        getCompilationSettings: () => parsed.options,
        getDefaultLibFileName: ts.getDefaultLibFilePath,
        fileExists: name => equalPath(name) || ts.sys.fileExists(name),
        readFile: name => equalPath(name) ? text : ts.sys.readFile(name),
        readDirectory: ts.sys.readDirectory,
    };
    const service = ts.createLanguageService(host);
    try {
        const errors = [...service.getSyntacticDiagnostics(file), ...service.getSemanticDiagnostics(file)];
        assert.equal(errors.length, 0, ts.formatDiagnosticsWithColorAndContext(errors, formatHost));
        return operation(service, file, position);
    } finally {
        service.dispose();
    }
}

const signature = inspect(prefix + 'recipes.create.cutting(/*cursor*/[{id: "minecraft:iron_ingot", chance: 1}], ["minecraft:iron_block"]);', (service, file, position) => {
    return service.getSignatureHelpItems(file, position, { triggerReason: { kind: 'invoked' } });
});
assert.ok(signature, 'Language service offers recipe signature help');
assert.equal(signature.items.length, 2, 'Exactly positional optional-tail and named-options overloads');
const positional = signature.items.find(item => item.parameters.length === 3);
const named = signature.items.find(item => item.parameters.length === 1);
assert.ok(positional, 'Coalesced positional overload is present');
assert.ok(named, 'Named-options overload is present');
const display = item => ts.displayPartsToString(item.prefixDisplayParts)
    + item.parameters.map(parameter => ts.displayPartsToString(parameter.displayParts)).join(', ')
    + ts.displayPartsToString(item.suffixDisplayParts);
const shown = display(positional);
assert.ok(shown.includes('$List_<'), shown);
assert.ok(shown.includes('FluidStack_') && shown.includes('ProcessingOutput_'), shown);
assert.ok(!shown.includes('SingleOrArray'), shown);
assert.ok(!shown.includes('Create$ResultsInput'), shown);
assert.ok(positional.parameters[2].isOptional, 'Only processing time is the optional trailing positional argument');
assert.ok(!positional.parameters[0].isOptional && !positional.parameters[1].isOptional, 'Both required positions remain required');
assert.ok(display(named).includes('Create$CuttingOptions'), display(named));

for (const [label, call] of [
    ['explicit canWriteSelf result', 'recipes.create.cutting_self({id: "minecraft:iron_ingot", /*cursor*/}, ["minecraft:iron_block"]);'],
    ['list element', 'recipes.create.cutting([{id: "minecraft:iron_ingot", /*cursor*/}], ["minecraft:iron_block"]);'],
    ['named result element', 'recipes.create.cutting({results: [{id: "minecraft:iron_ingot", /*cursor*/}], ingredients: ["minecraft:iron_block"]});'],
]) {
    inspect(prefix + call, (service, file, position) => {
        const result = service.getCompletionsAtPosition(file, position, {});
        assert.ok(result, label + ': contextual completions are available');
        for (const field of ['count', 'chance']) {
            const entry = result.entries.find(value => value.name === field);
            assert.ok(entry, label + ': ' + field + ' is suggested');
            const details = service.getCompletionEntryDetails(file, position, field, {}, entry.source, {}, entry.data);
            assert.ok(details, label + ': ' + field + ' completion has typed details');
            const type = ts.displayPartsToString(details.displayParts);
            assert.ok(type.includes('number'), label + ': ' + field + ' retains its canonical numeric alternative: ' + type);
            if (field === 'chance') assert.ok(type.includes('string'), label + ': original wrapper numeric-string chance remains visible: ' + type);
            assert.ok(!type.includes('any'), label + ': ' + field + ' must not degrade to any: ' + type);
        }
        assert.ok(!result.entries.some(entry => entry.name === 'filter'), label + ': unrelated broad adapter fields are absent');
    });
}
console.log('Original nominal recipe TypeScript: strict List inputs, canonical adapter aliases, two signatures, and count/chance object completions passed');
