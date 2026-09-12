// perf02 startup fixture 9/10：java: 模块解析 + 绑定引用（测模块解析/binding 装载负载）
console.info('PERF02-STARTUP s09 interop-refs load');
const { Math: JavaMath, Integer } = require('java:java/lang');
const perf02Interop = {
    sqrt25: JavaMath.sqrt(25),
    intValue: Integer.parseInt('42'),
    itemsBound: typeof Item !== 'undefined',
    blocksBound: typeof Block !== 'undefined',
    utilsBound: typeof Utils !== 'undefined',
    timeBound: typeof Time !== 'undefined',
};
if (perf02Interop.sqrt25 !== 5 || perf02Interop.intValue !== 42) {
    throw new Error('perf02 interop self-check failed: ' + JSON.stringify(perf02Interop));
}
console.info('PERF02-STARTUP s09 interop ok');
