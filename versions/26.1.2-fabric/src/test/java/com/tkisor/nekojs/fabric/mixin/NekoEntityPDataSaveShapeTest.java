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

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Save compatibility fixture for entity pdata, local to the fabric node and duplicated on both
 * fabric nodes. It pins the save shape, a {@code NeoForgeData} outer container (the NeoForge
 * container key) holding NekoJS's {@code NekoJSPersistentData} child key, using the real method
 * bodies of the production mixin: the test reflects into {@code neko$loadPData} and
 * {@code neko$savePData} rather than copying them.
 * <ul>
 *   <li>An old NeoForge save fixture (literal NBT with vanilla fields plus a NeoForgeData
 *       container) reads back the same pdata content on fabric, so saves survive a loader
 *       migration;</li>
 *   <li>the shape fabric writes matches what NeoForge's patched
 *       {@code Entity#addAdditionalSaveData} writes through
 *       {@code storeNullable("NeoForgeData", ...)}, and the NeoForge read path
 *       {@code getPersistentData()} reads that same container;</li>
 *   <li>empty pdata writes no {@code NeoForgeData} key, and a save without {@code NeoForgeData}
 *       reads back empty.</li>
 * </ul>
 * The test asserts the key constant ({@code NekoJSPersistentData}) through
 * {@link EntityExtension#NEKO_PDATA_KEY} and the container key through behavior, by the shape of
 * the tags it writes and reads.
 */
class NekoEntityPDataSaveShapeTest {

    // ---- Literal fixture of an old save ----

    /** Old NeoForge save: vanilla fields plus the NeoForgeData container that NeoForge's patched Entity
     * writes. */
    private static CompoundTag oldNeoForgeSave() {
        CompoundTag nekoPData = new CompoundTag();
        nekoPData.putInt("mana", 303);
        nekoPData.putString("name", "neko");
        nekoPData.putLong("seen", 1_700_000_000_000L);

        CompoundTag neoForgeData = new CompoundTag();
        neoForgeData.put(EntityExtension.NEKO_PDATA_KEY, nekoPData.copy());
        // The NeoForge container may also hold other mods' keys; NekoJS only touches its own child key.
        neoForgeData.putInt("SomeOtherModsKey", 7);

        CompoundTag save = new CompoundTag();
        save.putString("id", "minecraft:armor_stand");
        save.putDouble("Health", 20.0);
        save.put("NeoForgeData", neoForgeData.copy());
        return save;
    }

    /** Old save without NeoForgeData, as written before NekoJS was installed. */
    private static CompoundTag vanillaOnlySave() {
        CompoundTag save = new CompoundTag();
        save.putString("id", "minecraft:cow");
        save.putDouble("Health", 10.0);
        return save;
    }

    // ---- Driver for the production mixin; the @Inject handlers are private, so the test calls their
    // method bodies through reflection ----

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

    private static void writeRoot(DrivenMixin mixin, CompoundTag pdata) {
        if (!pdata.isEmpty()) {
            mixin.neko$getPDataRoot().put(EntityExtension.NEKO_PDATA_KEY, pdata);
        }
    }

    // ---- Read back: the old save fixture yields unchanged pdata content ----

    @Test
    void oldNeoForgeSaveReadsBackOnFabric() throws Exception {
        DrivenMixin mixin = new DrivenMixin();
        load(mixin, oldNeoForgeSave());

        CompoundTag root = mixin.neko$getPDataRoot();
        assertEquals(oldNeoForgeSave().getCompound("NeoForgeData").orElseThrow(), root,
                "the duck root must retain the entire persistence container");
        CompoundTag pdata = root.getCompound(EntityExtension.NEKO_PDATA_KEY).orElseThrow();
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

    // ---- Write: the shape matches NeoForge's patched Entity ----

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
        // load never ran (a fresh entity): the root field is null, so nothing is written.
        assertFalse(save(mixin).contains("NeoForgeData"));
    }

    /** Save then load on fabric: every pdata entry comes back unchanged, so the round trip is idempotent. */
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
        assertEquals(pdata, reader.neko$getPDataRoot().getCompound(EntityExtension.NEKO_PDATA_KEY).orElseThrow());
    }

    /** Defensive copy: mutating the source after writing leaves the saved copy unchanged, like the platform accessors. */
    @Test
    void savedShapeIsIndependentOfTheSourceTag() throws Exception {
        DrivenMixin mixin = new DrivenMixin();
        CompoundTag pdata = new CompoundTag();
        pdata.putInt("a", 1);
        writeRoot(mixin, pdata);
        CompoundTag saved = save(mixin);

        pdata.putInt("a", 99); // change the source tag
        CompoundTag stored = saved.getCompound("NeoForgeData").orElseThrow()
                .getCompound(EntityExtension.NEKO_PDATA_KEY).orElseThrow();
        assertEquals(1, stored.getIntOr("a", -1), "saved copy must not alias the source tag");
        assertNotEquals(99, stored.getIntOr("a", -1));
    }
}
