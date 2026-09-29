package com.tkisor.nekojs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Parse-level contract of the {@code /nekojs trust <address>} argument (smoke finding F1):
 * the unquoted {@code host:port} form must reach the executor instead of failing brigadier's
 * word parsing, while quoted input keeps {@code StringArgumentType.string()} semantics.
 */
class AddressArgumentTest {

    private final CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
    private final AtomicReference<String> captured = new AtomicReference<>();

    AddressArgumentTest() {
        dispatcher.register(LiteralArgumentBuilder.<Object>literal("nekojs")
                .then(LiteralArgumentBuilder.<Object>literal("trust")
                        .then(RequiredArgumentBuilder.<Object, String>argument("address", AddressArgument.address())
                                .executes(context -> {
                                    captured.set(AddressArgument.getAddress(context, "address"));
                                    return 1;
                                }))));
    }

    @Test
    void unquotedHostPortReachesTheExecutor() throws Exception {
        assertEquals(1, dispatcher.execute("nekojs trust 127.0.0.1:25565", new Object()));
        assertEquals("127.0.0.1:25565", captured.get(), "host:port must parse as one unquoted token");
    }

    @Test
    void unquotedPortlessFormIsUnchanged() throws Exception {
        assertEquals(1, dispatcher.execute("nekojs trust 127.0.0.1", new Object()));
        assertEquals("127.0.0.1", captured.get());
    }

    @Test
    void quotedFormKeepsStringArgumentSemantics() throws Exception {
        assertEquals(1, dispatcher.execute("nekojs trust \"127.0.0.1:25565\"", new Object()));
        assertEquals("127.0.0.1:25565", captured.get(), "quotes must be stripped like StringArgumentType.string()");

        assertEquals(1, dispatcher.execute("nekojs trust \"my host.example.com\"", new Object()));
        assertEquals("my host.example.com", captured.get(), "quoted input may contain spaces");
    }

    @Test
    void unquotedInputStopsAtWhitespace() {
        // Message text differs across brigadier versions ("Expected whitespace..." vs
        // "Unknown or incomplete command"); the contract is that trailing data stays an error.
        assertThrows(CommandSyntaxException.class,
                () -> dispatcher.execute("nekojs trust 127.0.0.1 25565", new Object()));
    }
}
