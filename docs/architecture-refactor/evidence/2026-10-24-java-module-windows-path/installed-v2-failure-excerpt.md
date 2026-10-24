# v2 安装失败摘录（修复前，保留为失败记录）

## 1.21.1 — verdict: NOT GREEN
errors: ['No current trade observation for declared']
reload failed: type=server generation=1 phase=EXECUTION source=server/owned-selectors.ts owner=ScriptManager[server] domain=script-execution error=Illegal char <:> at index 4: java:net/minecraft/world/phys[$Vec3]; candidate was discarded and the active generation remains unchanged.

## 26.1.2 — verdict: NOT GREEN
errors: ['No current trade observation for declared']
reload failed: type=server generation=1 phase=EXECUTION source=server/owned-selectors.ts owner=ScriptManager[server] domain=script-execution error=Illegal char <:> at index 4: java:net/minecraft/world/phys[$Vec3]; candidate was discarded and the active generation remains unchanged.

## 26.2.0 — verdict: NOT GREEN
errors: ['No current trade observation for declared']
reload failed: type=server generation=1 phase=EXECUTION source=server/owned-selectors.ts owner=ScriptManager[server] domain=script-execution error=Illegal char <:> at index 4: java:net/minecraft/world/phys[$Vec3]; candidate was discarded and the active generation remains unchanged.

## 26.1.2-fabric — verdict: NOT GREEN
errors: ['Owned server exited before readiness']
(server exited before readiness; no RCON transcript)

## 26.2.0-fabric — verdict: NOT GREEN
errors: ['Owned server exited before readiness']
(server exited before readiness; no RCON transcript)

## 26.1.2-fabric startup stack (key frames)
```
ric":"public void net.minecraft.world.entity.Entity.push(net.minecraft.world.phys.Vec3)","returnType":"void","bridge":false,"synthetic":false},{"name":"push","declaringClass":"net.minecraft.world.entity.LivingEntity","parameters":["net.minecraft.world.entity.Entity"],"generic":"public void net.minecraft.world.entity.LivingEntity.push(net.minecraft.world.entity.Entity)","returnType":"void","bridge":false,"synthetic":false}],"binding":{"class":"net.minecraft.world.entity.LivingEntity","codeSource":"file:/D:/mcmodDemo/NekoJS-mult/build/ticket37-ec7-installed-nodes/26.1.2-fabric/versions/26.1.2/server-26.1.2.jar","classSha256":"b1a68c8942ebdac98b65ce281692491e637b37948649b1e36a8d9a1f1abdcb6e"}}]}
[22:45:34] [Server thread/ERROR]: [server] [NEKO-1003] 脚本执行失败: nekojs:server/owned-selectors.ts
环境: SERVER
位置: server_scripts/owned-selectors.ts
原因: java:net/minecraft/world/phys[$Vec3]
script-diagnostic id=nekojs:server/owned-selectors.ts type=server phase=EXECUTION owner=Script Execution Environment generation=1 candidate=true source=server_scripts/owned-selectors.ts module=server_scripts/owned-selectors.ts cacheRevision=ab9b81502c8057d52c3e4146f09196b6d9bbfd0ffb08a912c940842e7684e0c6 cause=InvalidPathException: Illegal char <:> at index 4: java:net/minecraft/world/phys[$Vec3] — script execution failed
[22:45:34] [Server thread/ERROR]: Encountered an unexpected exception
com.tkisor.nekojs.core.lifecycle.NekoReloadException: reload failed: type=server generation=1 phase=EXECUTION source=server/owned-selectors.ts owner=ScriptManager[server] domain=script-execution error=Illegal char <:> at index 4: java:net/minecraft/world/phys[$Vec3]
	at knot//com.tkisor.nekojs.script.ScriptManager.reloadFailure(ScriptManager.java:1407)
	at knot//com.tkisor.nekojs.script.ScriptManager.loadCandidateScripts(ScriptManager.java:1360)
	at knot//com.tkisor.nekojs.script.ScriptManager.reloadScriptsTransactional(ScriptManager.java:1029)
	at knot//com.tkisor.nekojs.script.ScriptManager.doReloadScripts(ScriptManager.java:952)
	at knot//com.tkisor.nekojs.script.ScriptManager.reloadScripts(ScriptManager
```