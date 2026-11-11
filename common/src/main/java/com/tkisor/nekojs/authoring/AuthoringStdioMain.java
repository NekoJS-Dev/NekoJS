package com.tkisor.nekojs.authoring;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;

/** Standalone MCP entry point; stdout belongs exclusively to the JSON-RPC stream. */
public final class AuthoringStdioMain {
    private AuthoringStdioMain() {}

    public static void main(String[] args) {
        int status = run(args, System.in, System.out, System.err);
        if (status != 0) System.exit(status);
    }

    static int run(String[] args, InputStream input, OutputStream output, PrintStream errors) {
        if (args.length == 1 && args[0].equals("--help")) {
            errors.println("Usage: java -jar nekojs-<version>-authoring-stdio.jar --game-dir <game-directory> [--ai-dir <game-relative-directory>]");
            errors.println("Read-only MCP over UTF-8 newline-delimited stdio. Requires Java 21+ and an exported environment.json.");
            errors.println("Discovery: --ai-dir override, then output.directory in nekojs/config/ai.toml, then " + AuthoringPaths.DEFAULT_DIRECTORY + ", then legacy nekojs/ai.");
            errors.println("Reads exports and output configuration only; never creates configuration or relays live control requests.");
            return 0;
        }
        String gameDirectory = null;
        String aiDirectory = null;
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 >= args.length || args[i + 1].isBlank() || args[i + 1].startsWith("--")) return invalidArguments(errors);
            if (args[i].equals("--game-dir") && gameDirectory == null) gameDirectory = args[i + 1];
            else if (args[i].equals("--ai-dir") && aiDirectory == null) aiDirectory = args[i + 1];
            else return invalidArguments(errors);
        }
        if (gameDirectory == null) return invalidArguments(errors);
        try {
            AuthoringBundle bundle = AuthoringBundle.load();
            try (AuthoringSnapshotSession session = new AuthoringSnapshotSession(Path.of(gameDirectory), bundle, aiDirectory)) {
                new AuthoringStdio(bundle, session::execute).run(input, output, errors);
            }
            return 0;
        } catch (Exception failure) {
            errors.println("[NEKO-1019] Authoring stdio stopped: " + failure.getMessage());
            return 2;
        }
    }

    private static int invalidArguments(PrintStream errors) {
        errors.println("[NEKO-4055] Invalid arguments. Use --help or --game-dir <game-directory> [--ai-dir <game-relative-directory>].");
        return 2;
    }
}
