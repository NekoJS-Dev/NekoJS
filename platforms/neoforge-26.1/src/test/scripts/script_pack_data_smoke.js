// 26.x 脚本包 datapack 挂载 smoke（配合 villager_trades_smoke 使用）。
// 前置：在 <游戏目录>/nekojs/packs/demo_data/ 下创建包：
//   nekojs/packs/demo_data/manifest.json -> { "id": "demo_data", "name": "Demo Data", "version": "1.0.0" }
//   nekojs/packs/demo_data/server_scripts/script_pack_data_smoke.js（本文件副本）
//   nekojs/packs/demo_data/data/nekojs_demo/recipe/tin_from_dirt.json
//     （任意合法配方 JSON；最简单用原版配方复制改名）
//   可选：nekojs/packs/demo_data/data/nekojs_demo/villager_trade/emerald_for_gold.json
//     （26.x 村民交易本身即 datapack 注册表，可直接 JSON 注入，字段名按 CODEC：
//      { "wants": { "id": "minecraft:gold_ingot", "count": 1 },
//        "gives":  { "id": "minecraft:emerald", "count": 2 },
//        "max_uses": 12, "xp": 2, "price_multiplier": 0.05 }）
// 验证结果：
//   1) 服务器启动日志出现 "Reloading server resources for N nekojs script pack(s)"
//   2) /datapack list 中出现 nekojs_pack_global_demo_data（TOP、source: nekojs）
//   3) 配方 JSON 生效（/recipe 或合成配方界面可见）
//   4) 退出并重进世界后 /datapack list 不再包含该 id（worldData 已剔除，不落盘）
//   5) 未改动 data/ 内容时重启无 "Reloading server resources..."（内容签名跳过）

console.log('=== script pack data smoke loaded (pack data/ mounted as synthetic server datapack) ===');
