package com.tkisor.nekojs.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Server address argument for {@code /nekojs trust <address>}: an unquoted token up to the next
 * whitespace, or a brigadier quoted string. Unlike {@link com.mojang.brigadier.arguments.StringArgumentType#string()}
 * the unquoted form keeps characters that words reject — {@code :} in {@code host:port} is the
 * common trust target — so the unquoted {@code 127.0.0.1:25565} form reaches the command instead
 * of failing at parse. Quoted input keeps {@code StringArgumentType.string()} semantics (quotes
 * stripped, escapes and inner spaces honored); embedded spaces in unquoted input stay a parse
 * error. No suggestions: addresses are user-supplied.
 */
public final class AddressArgument implements ArgumentType<String> {

    private static final AddressArgument INSTANCE = new AddressArgument();
    private static final Collection<String> EXAMPLES = List.of("127.0.0.1", "127.0.0.1:25565", "\"host.example.com\"");

    private AddressArgument() {}

    public static AddressArgument address() {
        return INSTANCE;
    }

    public static String getAddress(final CommandContext<?> context, final String name) {
        return context.getArgument(name, String.class);
    }

    @Override
    public String parse(final StringReader reader) throws CommandSyntaxException {
        if (reader.peek() == '"') {
            return reader.readString();
        }
        final int start = reader.getCursor();
        while (reader.canRead() && !Character.isWhitespace(reader.peek())) {
            reader.skip();
        }
        return reader.getString().substring(start, reader.getCursor());
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(
            final CommandContext<S> context, final SuggestionsBuilder builder) {
        return builder.buildFuture();
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
