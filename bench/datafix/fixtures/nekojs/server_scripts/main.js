// datafix03 server fixture：固定 marker 集合——每类场景的日志观察点。
// 全部输出走 console.info（log4j2 INFO，进 logs/latest.log 与控制台）。
console.info('DATAFIX-SERVER main load v1');

ServerEvents.started(event => {
    console.info('DATAFIX-SERVER started-marker');
});

ServerEvents.stopped(event => {
    console.info('DATAFIX-SERVER stopped-marker');
});

// 用于"失败 reload 后旧环境仍可用"的观察：reload 前注册的监听器在失败 reload 后
// 依然要能触发（mod 契约：失败保留当前环境）。
