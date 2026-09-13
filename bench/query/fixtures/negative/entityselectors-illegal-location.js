// Ticket 25 fixture（负向探针，刻意不放在 test_scripts/ 默认集里）：AC3「源位置」证据。
//
// 目的：证明非法 selector 调用在脚本层得到普通错误，且统一错误管线补上
// 域+调用入口（异常消息）与脚本源位置（环境/位置/原因 三元组）。
//
// 用法：把本文件复制到 <node>/run/nekojs/test_scripts/ 后执行 /nekojs test。
// 本脚本【预期失败】：顶层未捕获的非法调用会进入 ScriptError/ErrorTracker，
// 从 run/logs/nekojs/test.log 提取「环境/位置/原因」三元组作为 AC3 证据
// （见 REPORT §5）。跑完请把它从 test_scripts/ 移除，避免污染默认 fixture 集。

Test.section('EntitySelectors.illegal.location');

const ServerHooks = Java.type('net.neoforged.neoforge.server.ServerLifecycleHooks');
const server = ServerHooks.getCurrentServer();
const level = server.overworld();

// 先断言异常消息本身带域+入口（可捕获路径的证据）
try {
  EntitySelectors.find(level, null, 0, 0, 0);
  Test.fail('null selector did NOT throw — AC3 error semantics broken');
} catch (e) {
  Test.assertEquals('EntitySelectors.find: selector must not be null', e.message,
    'error message carries domain + call entry');
}

// 再触发一次顶层未捕获的非法调用：由统一错误管线补源位置（本脚本据此预期失败）
const illegal = EntitySelectors.find(level, null, 0, 0, 0);
Test.fail('illegal selector call did NOT throw (got ' + illegal + ') — AC3 error semantics broken');
