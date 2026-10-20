package neko.acceptance;

import com.google.gson.Gson;
import com.tkisor.nekojs.core.pack.ScriptPackRegistry;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.pack.sync.PackSyncTrustStore;
import com.tkisor.nekojs.core.pack.sync.PackSyncClient;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.fabric.api.networking.v1.context.PacketContextProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackResources;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runs only in an isolated Fabric client GameTest; production APIs and control mods are unchanged. */
public final class FabricDirectoryClientAcceptance implements FabricClientGameTest {
    private static final Gson JSON = new Gson();
    private static final Identifier MARKER = Identifier.parse("neko_acceptance:marker.txt");
    private static final Identifier ITEM = Identifier.parse("neko_acceptance:auto_item");
    private static final Path OUTPUT = Path.of(System.getProperty("neko.acceptance.output"));
    private static final Path GAME = Path.of(System.getProperty("neko.acceptance.game"));
    private final List<String> completed = new ArrayList<>();
    private boolean remoteAccepted;
    private int remoteRequest;
    private volatile String remoteHost;

    @Override
    public void runTest(ClientGameTestContext context) {
        try {
            ClientLifecycleEvents.CLIENT_STOPPING.register(client -> write(OUTPUT.resolve("stopping.json"),
                    JSON.toJson(Map.of("pid", ProcessHandle.current().pid(), "clientStopping", true)) + "\n"));
            check(context, "initial", "GLOBAL", 2);
            TestWorldSave firstSave;
            try (var first = createWorld(context, "NekoDirectoryA", "A")) {
                firstSave = first.getWorldSave();
                waitForMarker(context, "A");
                check(context, "world-a", "A", 3);
                showItem(context, first, "world-a-item");
                reloadWithKeys(context);
            }
            waitForMarker(context, "GLOBAL");
            check(context, "after-a", "GLOBAL", 2);
            try (var second = createWorld(context, "NekoDirectoryB", "B")) {
                waitForMarker(context, "B");
                check(context, "world-b", "B", 3);
                showItem(context, second, "world-b-item");
            }
            waitForMarker(context, "GLOBAL");
            check(context, "after-b", "GLOBAL", 2);
            try (var reopened = firstSave.open()) {
                waitForMarker(context, "A");
                check(context, "world-a-return", "A", 3);
                showItem(context, reopened, "world-a-return-item");
            }
            waitForMarker(context, "GLOBAL");
            check(context, "after-a-return", "GLOBAL", 2);
            if (System.getProperty("neko.acceptance.remote") != null) remote(context);
            writeResult(true, null);
        } catch (Throwable failure) {
            writeResult(false, failure.toString());
            throw new AssertionError("Fabric directory client acceptance failed", failure);
        }
    }

    private TestSingleplayerContext createWorld(ClientGameTestContext context, String name, String marker) {
        return context.worldBuilder().setUseConsistentSettings(true).adjustSettings(settings -> {
            settings.setName(name);
            Path save = Minecraft.getInstance().getLevelSource().getBaseDir().resolve(settings.getTargetFolder());
            require(save.toAbsolutePath().normalize().startsWith(GAME.resolve("saves").toAbsolutePath().normalize()),
                    "World fixture escaped the isolated game directory");
            Path pack = save.resolve(ScriptPackRegistry.WORLD_PACKS_DIR).resolve("world_marker");
            write(pack.resolve("manifest.json"), JSON.toJson(Map.of(
                    "formatVersion", 1, "id", "world_marker", "version", "1.0.0", "entries", Map.of())));
            write(pack.resolve("assets/neko_acceptance/marker.txt"), marker);
            write(pack.resolve("assets/neko_acceptance/lang/en_us.json"), JSON.toJson(Map.of(
                    "neko.acceptance.shared", marker, "neko.acceptance.world_" + marker.toLowerCase(), marker)));
        }).create();
    }

    private static void waitForMarker(ClientGameTestContext context, String expected) {
        // Wait through the normal queued adapter refresh; forcing a reload here would hide a missing request.
        // Resource files switch before asynchronous language/model listeners finish applying.
        context.waitFor(client -> expected.equals(readMarker(client))
                && I18n.get("neko.acceptance.shared").equals(expected) && !ClientScreens.hasOverlay(client), 1200);
        context.waitTicks(5);
    }

    private void check(ClientGameTestContext context, String label, String expected, int stackSize) throws Exception {
        var row = context.computeOnClient(client -> {
            var resources = client.getResourceManager();
            var stack = resources.getResourceStack(MARKER);
            List<String> bodies = new ArrayList<>();
            for (var resource : stack) {
                try (var input = resource.open()) {
                    bodies.add(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            require(bodies.size() == stackSize, "Directory resource stack size changed: " + bodies);
            require(bodies.getFirst().equals("ROOT") && bodies.get(1).startsWith("GLOBAL"),
                    "Directory resource order changed: " + bodies);
            require(expected.equals(readMarker(client)), "Native winner changed: " + label);
            require(I18n.get("neko.acceptance.root").equals("ROOT"), "Root translation was lost");
            require(I18n.get("neko.acceptance.global").equals("GLOBAL"), "Global translation was lost");
            require(I18n.get("neko.acceptance.shared").equals(expected),
                    "Translation precedence changed at " + label + ": expected=" + expected
                            + " actual=" + I18n.get("neko.acceptance.shared"));
            boolean world = expected.equals("A") || expected.equals("B");
            require(ScriptPackRegistry.get().worldPacks().size() == (world ? 1 : 0), "WORLD registry leaked or was absent");
            boolean remote = expected.equals("REMOTE");
            require(ScriptPackRegistry.get().serverCachePacks().size() == (remote ? 1 : 0), "Remote activation leaked or was absent");
            require(resources.getResource(Identifier.parse("neko_acceptance:remote-only.txt")).isPresent() == remote,
                    "Native remote resource did not follow activation lifetime");
            require(I18n.get("neko.acceptance.remote").equals(remote ? "REMOTE" : "neko.acceptance.remote"),
                    "Remote translation did not follow activation lifetime");
            for (String marker : List.of("A", "B")) {
                String key = "neko.acceptance.world_" + marker.toLowerCase();
                require(I18n.get(key).equals(marker.equals(expected) ? marker : key), "Stale WORLD translation: " + key);
            }
            var model = client.getModelManager().getItemModel(ITEM);
            require(model != null && !(model instanceof MissingItemModel), "Generated item model was missing");
            var modelId = Identifier.parse("neko_acceptance:models/item/auto_item.json");
            var definitionId = Identifier.parse("neko_acceptance:items/auto_item.json");
            var generated = resources.getResource(modelId).orElseThrow();
            String modelJson;
            try (var reader = generated.openAsReader()) {
                var json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                require(json.get("parent").getAsString().equals("minecraft:item/generated"), "Generated parent changed");
                require(json.getAsJsonObject("textures").get("layer0").getAsString().equals("minecraft:item/diamond"),
                        "Generated texture changed");
                modelJson = json.toString();
            }
            require(resources.getResource(definitionId).isPresent(), "Client item definition was missing");
            require(resources.getResource(Identifier.parse("minecraft:textures/item/diamond.png")).isPresent(),
                    "Generated texture reference did not resolve");
            var result = new LinkedHashMap<String, Object>();
            result.put("label", label);
            result.put("pid", ProcessHandle.current().pid());
            result.put("marker", expected);
            result.put("stack", bodies);
            result.put("worldPacks", ScriptPackRegistry.get().worldPacks().stream().map(pack -> pack.root().toString()).toList());
            result.put("packs", resources.listPacks().map(PackResources::packId).toList());
            result.put("itemModel", model.getClass().getName());
            result.put("generatedModel", modelJson);
            return result;
        });
        Files.writeString(OUTPUT.resolve("resources.jsonl"), JSON.toJson(row) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        completed.add(label);
    }

    private void showItem(ClientGameTestContext context, TestSingleplayerContext world, String name) throws Exception {
        world.getServer().computeOnServer(server -> {
            server.getPlayerList().getPlayers().getFirst().getInventory().setItem(0,
                    new ItemStack(BuiltInRegistries.ITEM.getValue(ITEM)));
            return null;
        });
        context.waitTicks(5);
        context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        context.waitTicks(5);
        Path screenshot = context.takeScreenshot(name);
        Files.writeString(OUTPUT.resolve("screenshots.jsonl"), JSON.toJson(Map.of("label", name, "path", screenshot.toString())) + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        context.setScreen(() -> null);
    }

    private void reloadWithKeys(ClientGameTestContext context) throws Exception {
        Path global = GAME.resolve("nekojs/packs/global_marker");
        write(global.resolve("assets/neko_acceptance/reload.txt"), "UPDATED");
        write(global.resolve("assets/neko_acceptance/lang/en_us.json"), JSON.toJson(Map.of(
                "neko.acceptance.shared", "GLOBAL", "neko.acceptance.global", "GLOBAL", "neko.acceptance.reload", "UPDATED")));
        context.getInput().holdKey(GLFW.GLFW_KEY_F3);
        try {
            context.waitTick();
            context.getInput().pressKey(GLFW.GLFW_KEY_T);
        } finally {
            context.getInput().releaseKey(GLFW.GLFW_KEY_F3);
        }
        context.waitFor(client -> I18n.get("neko.acceptance.reload").equals("UPDATED")
                && !ClientScreens.hasOverlay(client), 1200);
        context.runOnClient(client -> {
            try (var reader = client.getResourceManager().getResource(Identifier.parse("neko_acceptance:reload.txt"))
                    .orElseThrow().openAsReader()) {
                require(new java.io.BufferedReader(reader).readLine().equals("UPDATED"), "F3+T did not publish the edited resource");
            }
        });
        check(context, "f3-t-world-a", "A", 3);
    }

    private static String readMarker(Minecraft client) {
        try {
            var resource = client.getResourceManager().getResource(MARKER);
            if (resource.isEmpty()) return "";
            try (var input = resource.get().open()) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    private void remote(ClientGameTestContext context) throws Exception {
        require(NekoJSPaths.get().root().toAbsolutePath().normalize().equals(GAME.resolve("nekojs").toAbsolutePath().normalize()),
                "Trust configuration escaped the isolated client");
        var key = com.google.gson.JsonParser.parseString(Files.readString(OUTPUT.resolve("remote-fixture.json"))).getAsJsonObject();
        require(key.get("connectHost").getAsString().equals("127.0.0.1") && key.get("port").getAsInt() == 25893,
                "Remote fixture must target the owned loopback server");
        // Fabric normalizes the connection hostname, which can be a local alias for the numeric target.
        context.runOnClient(client -> ClientConfigurationConnectionEvents.INIT.register((handler, ignored) -> {
            var connection = ((PacketContextProvider) handler).getPacketContext().get(PacketContext.CONNECTION);
            require(connection != null && !connection.isMemoryConnection(), "Remote configuration must use TCP");
            require(connection.getRemoteAddress() instanceof InetSocketAddress, "Remote socket address was absent");
            var address = (InetSocketAddress) connection.getRemoteAddress();
            require(address.getAddress() != null && address.getAddress().isLoopbackAddress() && address.getPort() == 25893,
                    "Remote configuration escaped the owned loopback endpoint");
            String observed = PackSyncClient.normalizeRemoteAddress(address);
            if (remoteHost == null) {
                require(!PackSyncTrustStore.get().isServerTrusted(PackSyncTrustStore.bucketFor(observed)),
                        "Actual fixture host was already trusted");
                remoteHost = observed;
                write(OUTPUT.resolve("remote-address.json"), JSON.toJson(Map.of(
                        "normalizedHost", observed, "address", address.getAddress().getHostAddress(),
                        "port", address.getPort(), "loopback", true, "initiallyTrusted", false)) + "\n");
            } else require(remoteHost.equals(observed), "Owned server identity changed between configurations");
        }));
        connect(context);
        context.waitFor(client -> remoteHost != null, 1200);
        String host = remoteHost;
        rejected(context, "untrusted", "Run /nekojs trust " + host);
        check(context, "remote-untrusted", "GLOBAL", 2);
        context.runOnClient(client -> PackSyncTrustStore.get().trustServer(host));
        connect(context);
        waitForMarker(context, "REMOTE");
        check(context, "remote-valid", "REMOTE", 3);
        context.runOnClient(client -> require(key.get("publicKey").getAsString().equals(
                PackSyncTrustStore.get().trustedPublicKey(key.get("keyId").getAsString())), "Accepted key was not pinned"));
        request(context, "tampered");
        rejected(context, "tampered", "signature mismatch for " + key.get("keyId").getAsString());
        check(context, "remote-tampered", "GLOBAL", 2);
        request(context, "restore");
        connect(context);
        waitForMarker(context, "REMOTE");
        check(context, "remote-recovered", "REMOTE", 3);
        request(context, "rotated");
        rejected(context, "rotated", "key rotation not accepted");
        check(context, "remote-rotated", "GLOBAL", 2);
        request(context, "restore");
        connect(context);
        waitForMarker(context, "REMOTE");
        check(context, "remote-final", "REMOTE", 3);
        context.runOnClient(client -> {
            require(client.getConnection() != null && !client.getConnection().getConnection().isMemoryConnection(),
                    "Remote acceptance must use a real TCP connection");
            client.level.disconnect(Component.literal("Directory acceptance disconnect"));
            client.disconnectWithSavingScreen();
        });
        context.waitFor(client -> client.level == null, 1200);
        waitForMarker(context, "GLOBAL");
        check(context, "remote-disconnected", "GLOBAL", 2);
        context.setScreen(TitleScreen::new);
        context.waitForScreen(TitleScreen.class);
        remoteAccepted = true;
    }

    private static void connect(ClientGameTestContext context) {
        String endpoint = System.getProperty("neko.acceptance.remote");
        context.runOnClient(client -> ConnectScreen.startConnecting(ClientScreens.current(client), client,
                ServerAddress.parseString(endpoint), new ServerData("Directory acceptance", endpoint, ServerData.Type.OTHER), false, null));
    }

    private static void rejected(ClientGameTestContext context, String phase, String expected) throws Exception {
        context.waitFor(client -> client.level == null && ClientScreens.current(client) instanceof DisconnectedScreen, 1200);
        String reason = context.computeOnClient(client -> ClientScreens.current(client).getNarrationMessage().getString());
        require(reason.contains(expected), "Unexpected " + phase + " rejection: " + reason);
        Files.writeString(OUTPUT.resolve("rejections.jsonl"), JSON.toJson(Map.of("phase", phase, "reason", reason)) + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        waitForMarker(context, "GLOBAL");
    }

    private void request(ClientGameTestContext context, String phase) throws Exception {
        String token = String.format("%02d", ++remoteRequest);
        Path folder = OUTPUT.resolve("remote-requests");
        Files.createDirectories(folder);
        Path pending = folder.resolve(token + ".tmp");
        Files.writeString(pending, JSON.toJson(Map.of("phase", phase)), StandardOpenOption.CREATE_NEW);
        Files.move(pending, folder.resolve(token + ".json"), StandardCopyOption.ATOMIC_MOVE);
        context.waitFor(client -> Files.isRegularFile(folder.resolve(token + ".ack")), 1200);
    }

    private static void write(Path path, String text) {
        try { Files.createDirectories(path.getParent()); Files.writeString(path, text, StandardCharsets.UTF_8); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private void writeResult(boolean accepted, String failure) {
        var row = new LinkedHashMap<String, Object>();
        row.put("accepted", accepted);
        row.put("pid", ProcessHandle.current().pid());
        row.put("completed", List.copyOf(completed));
        row.put("failure", failure);
        row.put("remoteSyncAccepted", remoteAccepted);
        row.put("screenshotsReviewed", false);
        write(OUTPUT.resolve("result.json"), JSON.toJson(row) + "\n");
    }
}
