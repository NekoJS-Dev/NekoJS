package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.tkisor.nekojs.api.AdapterInputShape.*;

/**
 * {@link Holder} 输入适配器：让 {@code stack.enchant('minecraft:sharpness', 1)} 这类
 * 原版 {@code Holder} 参数直接接受 id 字符串。
 *
 * <p>Graal 的 {@code targetTypeMapping} 只到 raw class——本适配器收不到
 * {@code Holder<Enchantment>} 的类型实参，无法只查附魔注册表。因此按「跨注册表 id 查找」
 * 实现：无命名空间默认 {@code minecraft:}；恰一个注册表含该 id 时返回其
 * {@link Holder.Reference}；命中多个注册表时显式报歧义（列出注册表），调用方应改用具体
 * 注册表的绑定 API。
 *
 * <p>26.x 侧是本文件的机械改名副本（Identifier↔ResourceLocation 等，见
 * scripts/check-platform-drift）。
 */
public final class HolderAdapter extends AbstractJSTypeAdapter<Holder<?>> {

    private static final String EXPECTED = "registry entry id ('minecraft:sharpness', namespace defaults to minecraft)";

    // 惰性初始化：BuiltInRegistries 的静态链在裸 JVM（无 FML Loader）上无法完成，类加载期
    // 触碰会让适配器连纯逻辑路径都不可测。首次转换只发生在游戏内，此时 FML 必然就绪。
    private static volatile RegistryAccess registryAccess;

    private static RegistryAccess registryAccess() {
        RegistryAccess access = registryAccess;
        if (access == null) {
            access = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            registryAccess = access;
        }
        return access;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Class<Holder<?>> getTargetClass() {
        return (Class) Holder.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        // 不按注册表出字面量联合：同一 raw Holder 别名要覆盖全部注册表，联合所有条目是数千条
        // 字面量。用裸 string——id 合法性与歧义在运行时校验，报错带上下文。
        return List.of(
                self(),
                string());
    }

    @Override
    public Optional<String> syntaxDoc() {
        return Optional.of("registry entry id, e.g. 'minecraft:sharpness' (namespace defaults to minecraft)");
    }

    @Override
    protected Holder<?> fromString(String id) {
        String normalized = id.contains(":") ? id : "minecraft:" + id;
        ResourceLocation location = ResourceLocation.tryParse(normalized);
        if (location == null) {
            throw new ValueConversionException(Holder.class, EXPECTED, id, "invalid id");
        }
        List<String> registries = new ArrayList<>();
        Holder<?> resolved = null;
        for (ResourceKey<? extends Registry<?>> registryKey : registryAccess().listRegistries().toList()) {
            Registry<?> registry = lookup(registryKey);
            if (registry == null || !registry.containsKey(location)) continue;
            registries.add(registryKey.location().toString());
            if (resolved == null) resolved = holderOf(registry, location);
        }
        if (registries.isEmpty()) {
            throw new ValueConversionException(Holder.class, EXPECTED, id,
                    "no registry contains id '" + normalized + "'");
        }
        if (registries.size() > 1) {
            throw new ValueConversionException(Holder.class, EXPECTED, id,
                    "id '" + normalized + "' is ambiguous, present in registries " + registries
                            + "; a Holder parameter cannot pick a registry — use the registry-specific binding API");
        }
        return resolved;
    }

    @Override
    protected Holder<?> fromHostObject(Object host) {
        return host instanceof Holder<?> holder ? holder : null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Registry<?> lookup(ResourceKey<? extends Registry<?>> registryKey) {
        // 与 NeoForgeRegistryQueryService.registry 同款 raw 收窄：registry 返回
        // Optional<Registry<E>>（E 为捕获类型），这里只需要 Registry<?> 的能力
        Optional<Registry<?>> result = registryAccess().registry((ResourceKey) registryKey);
        return result.orElse(null);
    }

    private static <T> Holder<T> holderOf(Registry<T> registry, ResourceLocation location) {
        return registry.getHolder(ResourceKey.create(registry.key(), location)).orElse(null);
    }
}
