package neko.acceptance;

import com.google.gson.Gson;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tkisor.nekojs.core.pack.sync.SignedDirectoryFixture;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

/** Test-only server commands mutate owned fixtures and request the real Fabric configuration handshake. */
public final class SignedResourceServer implements ModInitializer {
    @Override
    public void onInitialize() {
        Path server = Path.of(System.getProperty("neko.acceptance.server"));
        Path output = server.getParent();
        ServerLifecycleEvents.SERVER_STARTED.register(ignored -> record(output.resolve("server-ready.json"),
                Map.of("pid", ProcessHandle.current().pid(), "server", server.toString())));
        ServerLifecycleEvents.SERVER_STOPPED.register(ignored -> record(output.resolve("server-stopped.json"),
                Map.of("pid", ProcessHandle.current().pid(), "serverStopped", true)));
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
                Commands.literal("directoryprobe").then(Commands.argument("phase", StringArgumentType.word())
                        .executes(command -> {
                            String phase = StringArgumentType.getString(command, "phase");
                            var players = command.getSource().getServer().getPlayerList().getPlayers();
                            if (!phase.equals("restore") && players.size() != 1) {
                                throw new AssertionError("Reconfiguration requires exactly one connected acceptance player");
                            }
                            try { SignedDirectoryFixture.phase(server, phase); }
                            catch (Exception failure) { throw new IllegalStateException("Fixture mutation failed", failure); }
                            if (!phase.equals("restore")) {
                                for (var player : players) ServerPlayNetworking.reconfigure(player);
                            }
                            record(output.resolve("server-phases.jsonl"), Map.of(
                                    "phase", phase, "players", players.size(), "pid", ProcessHandle.current().pid()));
                            command.getSource().sendSuccess(() -> Component.literal("DIRECTORY-REMOTE " + phase + " PASS"), false);
                            return 1;
                        }))));
    }

    private static void record(Path path, Map<String, ?> row) {
        try { Files.writeString(path, new Gson().toJson(row) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
        catch (Exception failure) { throw new IllegalStateException("Server observation failed", failure); }
    }
}
