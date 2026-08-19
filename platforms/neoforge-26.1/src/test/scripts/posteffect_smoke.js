// 26.x PostEffects 绑定 smoke（参考副本 —— 复制到游戏运行目录 nekojs/client_scripts/ 后生效，
// 进入世界后脚本执行；游戏内改脚本后 F3+T 触发 CLIENT reload 重放）。
// 本脚本验证（feature 8b，v1 无 mixin 阶段）：
//   1) 预设清单 PostEffects.presets() 与资源可用性 isAvailable(id)
//   2) set/clear/toggle/current/isActive 状态机（minecraft:invert 为例）
//   3) register 运行时链（自定义 GLSL）：能注册/查询，但 set 会被拒绝并警告
//      （ShaderManager 运行时链激活依赖待接入的 ShaderManagerMixin，本批次不落地）
//   4) reload 进度 HUD（feature 8e）：F3+T 或 /nekojs reload client 时屏幕顶部出现 220x28 进度条
// 验证结果：控制台出现 "=== posteffect smoke" 各行；设置 invert 后画面反色，clear 恢复。

console.log('=== posteffect smoke ===');
console.log('presets: ' + PostEffects.presets().join(', '));
console.log('invert available: ' + PostEffects.isAvailable('minecraft:invert'));
console.log('custom available (should be false): ' + PostEffects.isAvailable('nekojs:no_such_effect'));

// 1) 激活/查询/清除
if (PostEffects.isAvailable('minecraft:invert')) {
    PostEffects.set('minecraft:invert');
    console.log('set invert requested; current=' + PostEffects.current()
        + ' active=' + PostEffects.isActive()
        + ' (视觉确认：画面应短暂反色，约 2 秒后自动清除)');
    // 保留 2 秒供肉眼确认，然后清除（setTimeout 由 client timer 提供）
    setTimeout(() => {
        PostEffects.clear();
        console.log('cleared; current=' + PostEffects.current() + ' active=' + PostEffects.isActive());
        // 2) toggle：再开一次再关一次
        PostEffects.toggle('minecraft:invert');
        console.log('toggle on: current=' + PostEffects.current());
        setTimeout(() => {
            PostEffects.toggle('minecraft:invert');
            console.log('toggle off: current=' + PostEffects.current() + ' (画面恢复)');
        }, 1000);
    }, 2000);
} else {
    console.log('minecraft:invert 不可用，跳过视觉验证（资源包异常？）');
}

// 3) 运行时注册（v1：注册成功但激活被拒绝，等待 ShaderManagerMixin）
const fragmentShader = `#version 330
uniform sampler2D InSampler;
layout(std140) uniform SamplerInfo { vec2 OutSize; vec2 InSize; };
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec4 color = texture(InSampler, texCoord);
    float gray = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
    fragColor = vec4(mix(color.rgb, vec3(gray), 1.0), color.a);
}`;
const registered = PostEffects.register('nekojs:smoke_grayscale', { fragmentShader: fragmentShader });
console.log('runtime register nekojs:smoke_grayscale: ' + registered
    + ', has=' + PostEffects.has('nekojs:smoke_grayscale'));
const activated = PostEffects.set('nekojs:smoke_grayscale');
console.log('activate runtime-only id (expect false until ShaderManagerMixin lands): ' + activated);
PostEffects.unregister('nekojs:smoke_grayscale');
console.log('unregistered; has=' + PostEffects.has('nekojs:smoke_grayscale'));

console.log('=== posteffect smoke done (watch the top-center HUD on next /nekojs reload client or F3+T) ===');
