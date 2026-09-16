package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.api.inject.EntityExtension;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票 18 AC1 存档兼容 fixture（fabric 节点本地，两 fabric 节点同体分发）：实体 pdata 的
 * 存档形状——外层 {@code NeoForgeData}（NeoForge 容器键）下挂 NekoJS 的
 * {@code NekoJSPersistentData} 子键——以<b>生产 mixin 的真实方法体</b>（反射调
 * {@code neko$loadPData}/{@code neko$savePData}，不是复制品）钉住：
 * <ul>
 *   <li>旧 NeoForge 存档 fixture（literal NBT，含 vanilla 字段 + NeoForgeData 容器）
 *       在 fabric 上回读出同样的 pdata 内容——跨 loader 存档迁移不丢；</li>
 *   <li>fabric 写出形状与 NeoForge patched {@code Entity#addAdditionalSaveData} 的
 *       {@code storeNullable("NeoForgeData", ...)} 同形（NeoForge 侧读路径
 *       {@code getPersistentData()} 即读该容器）；</li>
 *   <li>空 pdata 不写 {@code NeoForgeData} 键；缺 {@code NeoForgeData} 的存档回读为空。</li>
 * </ul>
 * key 常量（{@code NekoJSPersistentData}）经 {@link EntityExtension#NEKO_PDATA_KEY} 公开
 * 常量断言；容器键经行为断言（写出/读入的 tag 形状）。
 */
class NekoEntityPDataSaveShapeTest {

    // ---- 旧存档 literal fixture ----

    /** 旧 NeoForge 存档：vanilla 字段 + NeoForge patched Entity 写出的 NeoForgeData 容器。 */
    private static CompoundTag oldNeoForgeSave() {
        CompoundTag nekoPData = new CompoundTag();
        nekoPData.putInt("mana", 303);
        nekoPData.putString("name", "neko");
        nekoPData.putLong("seen", 1_700_000_000_000L);

        CompoundTag neoForgeData = new CompoundTag();
        neoForgeData.put(EntityExtension.NEKO_PDATA_KEY, nekoPData.copy());
        // NeoForge 容器里可能还有其它 mod 的键——NekoJS 只动自己的子键
        neoForgeData.putInt("SomeOtherModsKey", 7);

        CompoundTag save = new CompoundTag();
        save.putString("id", "minecraft:armor_stand");
        save.putDouble("Health", 20.0);
        save.put("NeoForgeData", neoForgeData.copy());
        return save;
    }

    /** 缺 NeoForgeData 的旧存档（未装 NekoJS 时期的 vanilla/fabric 存档）。 */
    private static CompoundTag vanillaOnlySave() {
        CompoundTag save = new CompoundTag();
        save.putString("id", "minecraft:cow");
        save.putDouble("Health", 10.0);
        return save;
    }

    // ---- 生产 mixin 驱动（反射：@Inject 处理器是 private，测试直接执行其方法体） ----

    private static final class DrivenMixin extends NekoEntityPDataMixin {
    }

    private static void load(DrivenMixin mixin, CompoundTag save) throws Exception {
        ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, save);
        Method method = NekoEntityPDataMixin.class.getDeclaredMethod("neko$loadPData",
                ValueInput.class, org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class);
        method.setAccessible(true);
        method.invoke(mixin, input, null);
    }

    private static CompoundTag save(DrivenMixin mixin) throws Exception {
        TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        Method method = NekoEntityPDataMixin.class.getDeclaredMethod("neko$savePData",
                ValueOutput.class, org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class);
        method.setAccessible(true);
        method.invoke(mixin, output, null);
        return output.buildResult();
    }

    private static void writeRoot(DrivenMixin mixin, CompoundTag pdata) throws Exception {
        Field root = NekoEntityPDataMixin.class.getDeclaredField("neko$pdataTag");
        root.setAccessible(true);
        root.set(mixin, pdata);
    }

    // ---- 回读：旧存档 fixture → pdata 内容不变 ----

    @Test
    void oldNeoForgeSaveReadsBackOnFabric() throws Exception {
        DrivenMixin mixin = new DrivenMixin();
        load(mixin, oldNeoForgeSave());

        CompoundTag pdata = mixin.neko$getPDataRoot();
        assertEquals(303, pdata.getIntOr("mana", -1));
        assertEquals("neko", pdata.getStringOr("name", ""));
        assertEquals(1_700_000_000_000L, pdata.getLongOr("seen", -1L));
        assertEquals(3, pdata.size(), "only the NekoJS sub-key content, nothing else leaks in");
    }

    @Test
    void vanillaOnlySaveReadsBackEmpty() throws Exception {
        DrivenMixin mixin = new DrivenMixin();
        load(mixin, vanillaOnlySave());
        assertTrue(mixin.neko$getPDataRoot().isEmpty(), "no NeoForgeData container means no pdata");
    }

    // ---- 写出：形状与 NeoForge patched Entity 同形 ----

    @Test
    void saveWritesTheCrossLoaderCompatibleShape() throws Exception {
        DrivenMixin mixin = new DrivenMixin();
        CompoundTag pdata = new CompoundTag();
        pdata.putInt("mana", 303);
        writeRoot(mixin, pdata);

        CompoundTag saved = save(mixin);
        assertTrue(saved.contains("NeoForgeData"), "container key must stay NeoForgeData (cross-loader)");
        CompoundTag container = saved.getCompound("NeoForgeData").orElseThrow();
        CompoundTag stored = container.getCompound(EntityExtension.NEKO_PDATA_KEY).orElseThrow();
        assertEquals(pdata, stored, "payload must live under the NekoJSPersistentData sub-key");
        assertEquals(1, container.size(), "only NekoJS's own sub-key is written by the fabric side");
    }

    @Test
    void emptyPDataWritesNoContainerKey() throws Exception {
        DrivenMixin mixin = new DrivenMixin();
        writeRoot(mixin, new CompoundTag());
        assertFalse(save(mixin).contains("NeoForgeData"), "empty pdata must not write the container");
    }

    @Test
    void nullRootWritesNoContainerKey() throws Exception {
        DrivenMixin mixin = new DrivenMixin();
        // load 未跑过（全新实体的形态）：root 字段为 null → 不写
        assertFalse(save(mixin).contains("NeoForgeData"));
    }

    /** 写读往返：fabric 写出 → fabric 回读，pdata 内容逐项不变（幂等）。 */
    @Test
    void saveLoadRoundTripIsStable() throws Exception {
        DrivenMixin writer = new DrivenMixin();
        CompoundTag pdata = new CompoundTag();
        pdata.putInt("a", 1);
        pdata.putString("b", "x");
        writeRoot(writer, pdata);

        CompoundTag saved = save(writer);

        DrivenMixin reader = new DrivenMixin();
        load(reader, saved);
        assertEquals(pdata, reader.neko$getPDataRoot());
    }

    /** 防御拷贝语义：容器写回后改源头不影响已存内容（与平台 Access 面同契约）。 */
    @Test
    void savedShapeIsIndependentOfTheSourceTag() throws Exception {
        DrivenMixin mixin = new DrivenMixin();
        CompoundTag pdata = new CompoundTag();
        pdata.putInt("a", 1);
        writeRoot(mixin, pdata);
        CompoundTag saved = save(mixin);

        pdata.putInt("a", 99); // 改源头
        CompoundTag stored = saved.getCompound("NeoForgeData").orElseThrow()
                .getCompound(EntityExtension.NEKO_PDATA_KEY).orElseThrow();
        assertEquals(1, stored.getIntOr("a", -1), "saved copy must not alias the source tag");
        assertNotEquals(99, stored.getIntOr("a", -1));
    }
}
