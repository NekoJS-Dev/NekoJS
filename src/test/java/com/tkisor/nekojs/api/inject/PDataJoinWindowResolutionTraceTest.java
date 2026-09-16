package com.tkisor.nekojs.api.inject;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票 18 joinLevel 窗口修复的源码 trace（票 17 NetworkRegistrationSourceTraceTest 先例）：
 * pdata 写入路径在 03 号票实测发现——{@code EntityJoinLevelEvent} 回调窗口内实体尚未进入
 * level 实体索引，按实体 id 反查（{@code level.getEntity(id)}）必空，脚本写 pdata 被静默
 * 丢弃（03 REPORT §3-3，归本票修复）。
 *
 * <p>修复形状：{@link EntityPDataStore.Access} 增加实体引用面（平台实现直接解引用持久化
 * 容器，不做 id 反查），脚本入口 {@code EntityExtension#neko$pdata()} 经
 * {@link EntityPDataStore#getPDataTag}/{@code setPDataTag} 走实体引用面。真实 joinLevel
 * 事件属平台行为（裸 JVM 无 FML 无法构造实体/level，见 EntityPDataStoreTest 的说明），
 * 本测试把「写入不依赖 id 反查」钉在源码结构上：实体引用面方法体不出现反查调用，
 * 反查调用计数保持为 id 面专属。
 */
class PDataJoinWindowResolutionTraceTest {

    // ---- 仓库根定位（节点测试 CWD = versions/<node>，向上找到控制器脚本） ----

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("stonecutter.gradle.kts"))
                    && Files.isDirectory(dir.resolve("src"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("cannot locate repo root from " + Path.of("").toAbsolutePath());
    }

    private static String read(String relative) {
        Path path = repoRoot().resolve(relative);
        try {
            return Files.readString(path);
        } catch (Exception e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    // ---- 桥本体：实体引用面存在，静态入口走实体引用面 ----

    @Test
    void bridgeExposesEntityReferenceOverloadsAndScriptEntriesPreferThem() {
        String bridge = read("src/main/java/com/tkisor/nekojs/api/inject/EntityPDataStore.java");
        assertTrue(bridge.contains("default CompoundTag get(Entity entity, String key)"),
                "Access must declare the entity-reference get overload (default delegates to id face)");
        assertTrue(bridge.contains("default void set(Entity entity, String key, CompoundTag tag)"),
                "Access must declare the entity-reference set overload (default delegates to id face)");
        assertTrue(bridge.contains("return current.get(entity, key);"),
                "getPDataTag must route through the entity-reference face, not entity.getId()");
        assertTrue(bridge.contains("current.set(entity, key, tag);"),
                "setPDataTag must route through the entity-reference face, not entity.getId()");

        // 脚本入口（EntityExtension#neko$pdata）经桥的静态入口，不自带容器解析
        // （setter 的 needle 不含结尾括号：源码形如 setPDataTag(self(), KEY, tag)）
        String extension = read("src/main/java/com/tkisor/nekojs/api/inject/EntityExtension.java");
        assertTrue(extension.contains("EntityPDataStore.getPDataTag(self(), NEKO_PDATA_KEY)"),
                "the script-facing pdata getter must go through EntityPDataStore.getPDataTag");
        assertTrue(extension.contains("EntityPDataStore.setPDataTag(self(), NEKO_PDATA_KEY"),
                "the script-facing pdata setter must go through EntityPDataStore.setPDataTag");
    }

    // ---- NeoForge：实体引用面直接解引用 getPersistentData()，反查只留在 id 面 ----

    @Test
    void neoforgeInstallOverridesEntityFaceWithDirectContainerDereference() {
        String mod = read("src/main/java/com/tkisor/nekojs/NekoJSMod.java");

        // 实体引用面 override 存在，且直接解引用 NeoForge 平台容器（两版本分支同形前缀）
        assertTrue(mod.contains("public net.minecraft.nbt.CompoundTag get(net.minecraft.world.entity.Entity entity, String key)"),
                "NeoForge install must override the entity-reference get face");
        assertTrue(mod.contains("public void set(net.minecraft.world.entity.Entity entity, String key, net.minecraft.nbt.CompoundTag tag)"),
                "NeoForge install must override the entity-reference set face");
        assertTrue(mod.contains("entity.getPersistentData().getCompound(key)"),
                "entity-reference get must dereference Entity#getPersistentData() directly");
        assertTrue(mod.contains("var container = entity.getPersistentData();"),
                "entity-reference set must dereference Entity#getPersistentData() directly");

        // id 反查（pdataContainer）保持 id 面专属：恰好 get/set 各一处调用点
        // （方法定义处的签名是 pdataContainer(int entityId)，不计入）。
        // 若实体引用面回归为经 id 反查（join 窗口写静默丢弃复活），该计数会增长
        assertTrue(countOccurrences(mod, "pdataContainer(entityId)") == 2,
                "reverse lookup must stay confined to the id-keyed face (exactly the id get/set call sites)");
    }

    // ---- Fabric：实体引用面直接解引用 mixin duck 接口，反查只留在 id 面 ----

    @Test
    void fabricInstallOverridesEntityFaceWithDirectDuckDereference() {
        String sync = read("src/fabric/java/com/tkisor/nekojs/fabric/FabricPDataSync.java");

        assertTrue(sync.contains("public CompoundTag get(Entity entity, String key)"),
                "fabric install must override the entity-reference get face");
        assertTrue(sync.contains("public void set(Entity entity, String key, CompoundTag tag)"),
                "fabric install must override the entity-reference set face");
        assertTrue(countOccurrences(sync, "((NekoEntityPData) entity).neko$getPDataRoot()") >= 4,
                "both faces must dereference the mixin duck root directly (id face 2 + entity face 2)");

        // findEntity 反查保持 id 面专属：恰好 get/set 各一处。实体引用面若回归反查（join
        // 窗口写静默丢弃复活），计数会增长到 4
        assertTrue(countOccurrences(sync, "findEntity(entityId)") == 2,
                "reverse lookup must stay confined to the id-keyed face");
    }

    // ---- 平台两面的 NBT 读写语义同形：拷贝防御 + 空 tag 移除子键 ----

    @Test
    void bothLoaderFacesKeepCopySemanticsAndEmptyTagRemoval() {
        // 同一段「copy 读出、空写移除」语义在 NeoForge install、fabric install 与
        // EntityPDataStoreTest 的内存实现三处同形（桥的域契约）
        try (Stream<Path> files = Stream.of(
                Path.of("src/main/java/com/tkisor/nekojs/NekoJSMod.java"),
                Path.of("src/fabric/java/com/tkisor/nekojs/fabric/FabricPDataSync.java"))) {
            for (Path relative : files.toList()) {
                String source = read(relative.toString().replace('\\', '/'));
                assertTrue(source.contains("if (tag.isEmpty())"),
                        relative + " must keep the empty-tag-removes-key semantics");
                assertTrue(source.contains("tag.copy()"),
                        relative + " must keep the defensive copy on write");
            }
        }
    }
}
