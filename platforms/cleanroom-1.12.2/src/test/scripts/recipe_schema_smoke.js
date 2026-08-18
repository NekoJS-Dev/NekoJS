// 1.12.2 配方 schema smoke（参考副本 —— 复制到游戏运行目录 nekojs/test_scripts/ 后 /nekojs test 运行）
// 前置：重建 cleanroom jar 并启动游戏；本脚本验证：
//   1) 自动扫描的命名空间/类型/字段 目录可见（RecipeSchema.*）
//   2) JSON 目录查询（RecipeSchema.jsonTypes）
//   3) 脚本侧 registerSchema + 反射构造路径（含错误路径不中断事件）

console.log('=== recipe schema smoke ===');
console.log('namespaces:', RecipeSchema.namespaces().join(', '));

// 1) 自动扫描目录可见性
const ns = RecipeSchema.namespaces().find(n => n !== 'minecraft' && n !== 'nekojs');
if (ns) {
    console.log('types of', ns, ':', RecipeSchema.types(ns).join(', '));
    const t = RecipeSchema.types(ns)[0];
    if (t) {
        const desc = RecipeSchema.describe(ns, t);
        console.log('describe:', JSON.stringify(desc, null, 2));
        if (desc.fields && desc.fields.length > 0) {
            console.log('auto-discovered schema OK for', ns + ':' + t);
        }
    }
} else {
    console.log('note: no non-vanilla recipe namespace found (empty server or all mods vanilla-only)');
}

// 2) JSON 目录（若有）
if (typeof RecipeSchema.jsonTypes === 'function') {
    console.log('json catalog of minecraft:', RecipeSchema.jsonTypes('minecraft').join(', '));
}

// 3) registerSchema + 构造（错误路径验证：伪造类+缺字段应记错误但不中断事件）
ServerEvents.recipes(event => {
    // 自动发现 schema 的命名对象构造（字段注入路径）——若存在非 vanilla 类型且有字段
    if (ns && RecipeSchema.types(ns).length > 0) {
        const t = RecipeSchema.types(ns)[0];
        const desc = RecipeSchema.describe(ns, t);
        if (desc.fields && desc.fields.length > 0) {
            try {
                // 只填 string 字段，其余缺省 → 缺 required 字段会在 flush 记错误（验证错误路径）
                const f = {};
                for (const fd of desc.fields) {
                    if (fd.kind === 'STRING') f[fd.name] = 'smoke';
                }
                event.recipes[ns][t](f);
                console.log('schema construct attempted for', ns + ':' + t, '(expect error entry if required fields missing)');
            } catch (e) {
                console.log('schema construct threw (expected if named-object branch rejects):', e);
            }
        }
    }

    // registerSchema 兜底演示：nekojs 命名空间伪造类型（构造必然失败 → 验证错误路径不中断）
    event.registerSchema('nekojs', 'smoke_test', {
        fields: { output: 'itemstack' },
        class: 'net.minecraft.item.crafting.ShapelessRecipes'
    });
    try {
        event.recipes.nekojs.smoke_test('minecraft:stone');
        console.log('registerSchema + ctor-style call OK (flush 时会因字段缺失记错误，属预期)');
    } catch (e) {
        console.log('smoke_test call threw:', e);
    }
});
console.log('=== recipe schema smoke done (check error panel for expected construction errors) ===');
