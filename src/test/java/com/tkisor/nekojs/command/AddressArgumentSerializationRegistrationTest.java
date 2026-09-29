//? if neoforge {
package com.tkisor.nekojs.command;

import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Serialization registration for the custom trust address argument (ticket-36 regression):
 * a real client join makes the server build ClientboundCommandsPacket, which resolves each
 * argument through ArgumentTypeInfos.byClass and serializes the info's numeric
 * command_argument_type registry id. The 2026-09-29 regression shipped AddressArgument without
 * either registration, so RCON dispatch (which never serializes the tree) passed while every
 * real world entry failed with "Unrecognized argument type".
 *
 * <p>Two legs, matching what a bare JVM can see: {@link #addressArgumentIsRecognized()} pins the
 * class-map half by running the exact supplier the register entry uses;
 * {@link #argumentTypeRegistrationIsWiredInEveryLoaderEntry()} source-traces the wiring the JVM
 * cannot execute — the NeoForge DeferredRegister pair (registry entry + same-instance class map)
 * and the fabric ArgumentTypeRegistry call in the fabric entrypoint. The client-visible halves
 * (registry id on the wire, client-side deserialization) need a mod runtime and are left to the
 * runServer spot-check plus the maintainer re-trial recorded in the evidence pack.
 */
class AddressArgumentSerializationRegistrationTest {

    @Test
    void addressArgumentIsRecognized() {
        NekoJSArgumentTypes.addressArgumentInfo();

        assertTrue(ArgumentTypeInfos.isClassRecognized(AddressArgument.class),
                "ArgumentTypeInfos.BY_CLASS must contain AddressArgument, or ClientboundCommandsPacket"
                        + ".createEntry throws 'Unrecognized argument type' and blocks world entry");
        // unpack() is the exact frame the regression hit (ArgumentTypeInfos.unpack -> byClass).
        assertNotNull(ArgumentTypeInfos.unpack(AddressArgument.address()),
                "unpack must resolve a wire template for the trust address argument");
    }

    @Test
    void argumentTypeRegistrationIsWiredInEveryLoaderEntry() throws IOException {
        Path root = repoRoot();

        // NeoForge shared entry: all three NeoForge nodes funnel through this constructor.
        String neoforgeEntry = read(root.resolve(
                "src/main/java/com/tkisor/nekojs/NekoJSMod.java"));
        assertTrue(neoforgeEntry.contains("NekoJSArgumentTypes.register(modEventBus);"),
                "NekoJSMod must wire the argument-type registration during mod construction");

        // The registration unit itself: one DeferredRegister supplier doing both the class map
        // (registerByClass) and the minecraft:command_argument_type registry entry, so the wire
        // id and the class map refer to the same info instance.
        String registration = read(root.resolve(
                "src/main/java/com/tkisor/nekojs/command/NekoJSArgumentTypes.java"));
        assertTrue(registration.contains("ArgumentTypeInfos.registerByClass(AddressArgument.class,"),
                "the info supplier must populate ArgumentTypeInfos.BY_CLASS for AddressArgument");
        assertTrue(registration.contains("BuiltInRegistries.COMMAND_ARGUMENT_TYPE"),
                "the info must be registered in minecraft:command_argument_type (numeric wire id)");
        assertTrue(registration.contains("NekoJSArgumentTypes::addressArgumentInfo"),
                "the register supplier and the class-map call must be the same instance");

        // Fabric entry: same vanilla serialization path, registered via fabric-command-api.
        String fabricEntry = read(root.resolve(
                "src/fabric/java/com/tkisor/nekojs/fabric/NekoJSFabricMod.java"));
        assertTrue(fabricEntry.contains("ArgumentTypeRegistry.registerArgumentType(")
                        && fabricEntry.contains("AddressArgument.class"),
                "the fabric entrypoint must register AddressArgument for command sync");
        assertFalse(fabricEntry.contains("ArgumentTypeInfos.registerByClass"),
                "fabric must use fabric's ArgumentTypeRegistry, not the NeoForge registerByClass hook");
    }

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

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
//?}
