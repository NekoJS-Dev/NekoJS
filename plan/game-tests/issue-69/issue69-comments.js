console.info('ISSUE69_JS_LOAD');

const issue69LineEmpty = Text
    // A full-line comment must not split the suffix chain.
    .of('issue69')
    .isEmpty();
if (issue69LineEmpty !== false) {
    throw new Error('NEKO-ISSUE69-JS-LINE: Expected nonempty text');
}

const issue69EndEmpty = Text.of('issue69') // Keep the next member in this chain.
    .isEmpty();
if (issue69EndEmpty !== false) {
    throw new Error('NEKO-ISSUE69-JS-END: Expected nonempty text');
}

const issue69BlockEmpty = Text /* before member */ .of /* before call */ (
    /* before argument */ 'issue69'
) /* before member */ .isEmpty /* before call */ ();
if (issue69BlockEmpty !== false) {
    throw new Error('NEKO-ISSUE69-JS-BLOCK: Expected nonempty text');
}
console.info('ISSUE69_JS_PASS line=end=block textEmpty=false');
