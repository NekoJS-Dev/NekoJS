// The interface method must disappear before JavaScript-only validation.
interface Issue69Shape {
    issue69TypeOnlyMethod(): number;
}

console.info('ISSUE69_TS_LOAD');
const issue69TsEmpty = Text.of('issue69').isEmpty();
if (issue69TsEmpty !== false) {
    throw new Error('NEKO-ISSUE69-TS: Expected nonempty text');
}
console.info('ISSUE69_TS_PASS textEmpty=false');
