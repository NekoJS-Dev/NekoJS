package com.tkisor.nekojs.network;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票 17 注册时机与 loader 子集的源码 trace fixture（纯文件读，全部节点同跑）：
 *
 * <ul>
 *   <li>AC1：平台 network 注册只发生在 loader 原生初始化入口——NeoForge 的
 *       {@code RegisterPayloadHandlersEvent} 订阅（{@code NekoJSNetwork}）与 fabric 的
 *       entrypoint（{@code NekoJSFabricMod#onInitialize} / {@code NekoJSFabricClient}）；
 *       {@code PlayPacketDispatchers.install} 全仓 main 源恰两处（两 loader 各一）；
 *       reload/命令面文件不含任何 payload 注册调用。多次 SERVER/CLIENT reload 的运行期
 *       不变性由「reload 路径不含注册调用 + 生命周期测试（票 06/07 与本票 routing fixture）
 *       不触碰 dispatcher」联合钉住；平台 event 只发一次是 loader 契约（REPORT
 *       characterization）。</li>
 *   <li>AC7：fabric 网络面是显式子集——payload 注册恰为
 *       {script_payload（双向）、client_data_sync、pdata_sync、pack_hashes、pack_bundle}，
 *       {@code ShowErrorListPacket}（错误面板/显示域）在 {@code src/fabric} 零引用：
 *       不伪造编辑器/dashboard/客户端显示 parity。</li>
 * </ul>
 */
class NetworkRegistrationSourceTraceTest {

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

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }

    /** 只保留代码行（去掉 javadoc/行注释形态的行）：类名出现在注释里不是接线引用。 */
    private static String codeLinesOnly(String source) {
        StringBuilder out = new StringBuilder(source.length());
        for (String line : source.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("*") || trimmed.startsWith("/*") || trimmed.startsWith("//")) {
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString();
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

    /** 枚举 main 源根（共享 src/main + common main + fabric raw root + 全部节点 main）下的 java 文本流。 */
    private static List<Path> mainJavaFiles() {
        Path root = repoRoot();
        Stream<Path> shared = Stream.of(root.resolve("src/main/java"));
        Stream<Path> common = Stream.of(root.resolve("common/src/main/java"));
        Stream<Path> fabric = Stream.of(root.resolve("src/fabric/java"));
        Stream<Path> nodes = java.util.Arrays.stream(root.resolve("versions").toFile().listFiles())
                .filter(nodeDir -> nodeDir.isDirectory()
                        && Files.isDirectory(nodeDir.toPath().resolve("src/main/java")))
                .map(nodeDir -> nodeDir.toPath().resolve("src/main/java"));
        List<Path> roots = Stream.concat(Stream.concat(Stream.concat(shared, common), fabric), nodes).toList();
        return roots.stream()
                .filter(Files::isDirectory)
                .flatMap(dir -> {
                    try (Stream<Path> walk = Files.walk(dir)) {
                        return walk.filter(p -> p.toString().endsWith(".java")).toList().stream();
                    } catch (IOException e) {
                        throw new IllegalStateException("cannot walk " + dir, e);
                    }
                })
                .toList();
    }

    // ---- AC1：注册只在 loader 原生初始化时机 ----

    @Test
    void neoforgeRegistrationLivesInLoaderEventSubscriber() {
        String source = read(repoRoot().resolve("src/main/java/com/tkisor/nekojs/network/NekoJSNetwork.java"));
        assertTrue(source.contains("@EventBusSubscriber(modid = com.tkisor.nekojs.NekoJS.MODID)")
                        || source.contains("@EventBusSubscriber(modid = NekoJS.MODID)"),
                "payload registration must stay in the FML event subscriber (loader-native timing)");
        assertTrue(source.contains("RegisterPayloadHandlersEvent"),
                "payload registration must hook the loader's payload registration event");
        assertEquals(1, countOccurrences(source, "PlayPacketDispatchers.install("),
                "the dispatcher is assembled exactly once in the NeoForge network entry");
    }

    /** AC2 的「方向」钉住：NeoForge 侧每个 payload 的注册方向（S2C / 双向 / 配置期 S2C）。 */
    @Test
    void neoforgePayloadDirectionsStayOnLegacyRegistration() {
        String source = read(repoRoot().resolve("src/main/java/com/tkisor/nekojs/network/NekoJSNetwork.java"));
        List<String> expectedDirections = List.of(
                "registrar.playToClient(ShowErrorListPacket.TYPE",
                "registrar.playToClient(PDataSyncPacket.TYPE",
                "registrar.playToClient(ClientDataSyncPacket.TYPE",
                "registrar.configurationToClient(PackHashListPayload.TYPE",
                "registrar.configurationToClient(PackBundlePayload.TYPE");
        for (String line : expectedDirections) {
            assertTrue(source.contains(line), "NeoForge registration line missing: " + line);
        }
        // 脚本自定义通道的双向注册经版本 compat 门面（26.x 4 参 playBidirectional，
        // 1.21.1 3 参 + flow 判别）——形状由 ScriptPayloadRegistrationShapeTest 钉住，
        // 这里钉住「确实经门面注册」这一方向事实。
        assertTrue(source.contains("McPlatformCompat.get().registerScriptPayload(registrar)"),
                "the bidirectional script payload must be registered through the version compat facade");
    }

    /** AC2 的「方向」钉住：fabric 侧显式子集的注册方向。 */
    @Test
    void fabricPayloadDirectionsStayOnLegacyRegistration() {
        Path root = repoRoot();
        String play = read(root.resolve("src/fabric/java/com/tkisor/nekojs/fabric/FabricPlayNetwork.java"));
        assertTrue(play.contains("PayloadTypeRegistry.serverboundPlay().register("),
                "script channel must stay serverbound-registered on fabric");
        assertTrue(play.contains("PayloadTypeRegistry.clientboundPlay().register("),
                "script channel / client data must stay clientbound-registered on fabric");
        String pdata = read(root.resolve("src/fabric/java/com/tkisor/nekojs/fabric/FabricPDataSync.java"));
        assertTrue(pdata.contains("PayloadTypeRegistry.clientboundPlay().register("),
                "pdata sync must stay clientbound on fabric");
        String pack = read(root.resolve("src/fabric/java/com/tkisor/nekojs/fabric/FabricPackSync.java"));
        assertEquals(2, countOccurrences(pack, "PayloadTypeRegistry.clientboundConfiguration().register("),
                "pack sync payloads must stay configuration-phase clientbound on fabric");
    }

    @Test
    void dispatcherInstallAppearsExactlyOncePerLoaderInMainSources() {
        int installs = 0;
        for (Path file : mainJavaFiles()) {
            installs += countOccurrences(read(file), "PlayPacketDispatchers.install(");
        }
        assertEquals(2, installs,
                "expected exactly two dispatcher installs in main sources (NeoForge NekoJSNetwork + FabricPlayNetwork)");
    }

    @Test
    void fabricRegistrationRunsOnceFromLoaderEntrypoints() {
        Path root = repoRoot();
        String mod = read(root.resolve("src/fabric/java/com/tkisor/nekojs/fabric/NekoJSFabricMod.java"));
        assertEquals(1, countOccurrences(mod, "FabricPlayNetwork.registerServer();"),
                "FabricPlayNetwork.registerServer must be called exactly once (mod entrypoint)");
        assertTrue(mod.indexOf("void onInitialize()") < mod.indexOf("FabricPlayNetwork.registerServer();"),
                "the single registerServer call must live inside onInitialize");

        String client = read(root.resolve("src/fabric/java/com/tkisor/nekojs/fabric/NekoJSFabricClient.java"));
        assertEquals(1, countOccurrences(client, "FabricPlayNetwork.registerClient();"),
                "FabricPlayNetwork.registerClient must be called exactly once (client entrypoint)");
    }

    @Test
    void reloadAndCommandPathsContainNoPayloadRegistration() {
        // reload 的可达入口：命令面（三节点）+ 生命周期 owner（root/manager）。
        // 这些文件里出现平台注册调用 = reload 会重复注册，直接红。
        Path root = repoRoot();
        List<Path> reloadFacing = List.of(
                root.resolve("src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java"),
                root.resolve("versions/1.21.1/src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java"),
                root.resolve("src/fabric/java/com/tkisor/nekojs/fabric/FabricNekoJSCommands.java"),
                root.resolve("common/src/main/java/com/tkisor/nekojs/core/lifecycle/NekoRuntimeRoot.java"),
                root.resolve("common/src/main/java/com/tkisor/nekojs/script/ScriptManager.java"));
        for (Path path : reloadFacing) {
            String code = codeLinesOnly(read(path));
            for (String token : List.of("PlayPacketDispatchers.install", "PayloadTypeRegistry",
                    "registerGlobalReceiver", "playBidirectional", "playToClient", "configurationToClient")) {
                assertFalse(code.contains(token),
                        () -> path.getFileName() + " must not contain network registration call '" + token + "'");
            }
        }
    }

    // ---- AC7：fabric 显式子集 ----

    @Test
    void fabricRegistersExactlyTheExplicitPayloadSubset() {
        Path root = repoRoot();
        List<Path> fabricFiles;
        try (Stream<Path> walk = Files.walk(root.resolve("src/fabric/java"))) {
            fabricFiles = walk.filter(p -> p.toString().endsWith(".java")).toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        assertFalse(fabricFiles.isEmpty());

        int registryCalls = 0;
        for (Path file : fabricFiles) {
            String source = read(file);
            // 只数调用点（"PayloadTypeRegistry."），不含 import 行
            registryCalls += countOccurrences(source, "PayloadTypeRegistry.");
        }
        // FabricPlayNetwork(3: client_data_sync + script_payload 双向) + FabricPDataSync(1)
        // + FabricPackSync(2: pack_hashes/pack_bundle) = 6 处注册调用、5 个 payload 类型
        assertEquals(6, registryCalls,
                "fabric payload type registrations must stay the explicit six calls / five types");
    }

    @Test
    void fabricOmitsEditorDashboardAndDisplayPackets() {
        Path root = repoRoot();
        try (Stream<Path> walk = Files.walk(root.resolve("src/fabric"))) {
            for (Path file : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                String code = codeLinesOnly(read(file));
                assertFalse(code.contains("ShowErrorListPacket"),
                        () -> "display-domain packet leaked into fabric face: " + file);
                assertFalse(code.contains("ScriptSyncFiles"),
                        () -> "editor sync packet leaked into fabric face: " + file);
                assertFalse(code.contains("FetchScriptContent"),
                        () -> "editor fetch packet leaked into fabric face: " + file);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void fabricScriptChannelReceiversHopMainThreadBeforeNeutralDispatch() {
        String source = read(repoRoot().resolve("src/fabric/java/com/tkisor/nekojs/fabric/FabricPlayNetwork.java"));
        // 两处 receiver（server/client）都必须先切平台主线程再进中立投递核心
        Pattern receiverHop = Pattern.compile(
                "context\\.(server|client)\\(\\)\\.execute\\(\\(\\) ->\\s*[^;]*NetworkMessageHandler\\.post(Server|Client)Event");
        Matcher matcher = receiverHop.matcher(source);
        int hopped = 0;
        while (matcher.find()) {
            hopped++;
        }
        assertEquals(2, hopped,
                "both fabric script-channel receivers must hop the platform main thread before neutral dispatch");
        assertEquals(2, countOccurrences(source, "NetworkMessageHandler.post"),
                "every dispatch into NetworkMessageHandler on fabric must be inside a main-thread hop");
    }
}
