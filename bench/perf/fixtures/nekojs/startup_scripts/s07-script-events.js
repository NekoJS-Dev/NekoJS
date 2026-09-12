// perf02 startup fixture 7/10：ScriptEvents 自定义事件声明 ×3（README「自定义事件」——startup 侧声明，
// server_scripts 侧监听/触发；adapter-bench 用 adapterRun 事件做每次 load 的自触发基准入口）
console.info('PERF02-STARTUP s07 script-events load');
ScriptEvents.server(event => event.register('Perf02Bench', 'adapterRun'));
ScriptEvents.server(event => event.register('Perf02Bench', 'noopOne'));
ScriptEvents.server(event => event.register('Perf02Bench', 'noopTwo'));
