package dev.latvian.mods.kubejs.script;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Routes addon diagnostics to the native logger without owning a JavaScript runtime. */
public class ConsoleJS {
    public static final ConsoleJS STARTUP = new ConsoleJS("startup");
    public static final ConsoleJS SERVER = new ConsoleJS("server");
    public static final ConsoleJS CLIENT = new ConsoleJS("client");
    private static final Logger LOGGER = LoggerFactory.getLogger("NekuJS/LegacyConsole");
    private final String phase;
    public ConsoleJS(String phase) { this.phase = java.util.Objects.requireNonNull(phase); }
    public ConsoleLine info(Object value) {
        String message = String.valueOf(value);
        LOGGER.info("Legacy addon message: phase={}, message={}", phase, message);
        return new ConsoleLine(message, null);
    }
    public ConsoleLine error(Object value) {
        return error(String.valueOf(value), null);
    }
    public ConsoleLine error(String message, Throwable failure) {
        LOGGER.error("[NEKO-4280] Legacy addon reported an error: phase={}, message={}", phase, message, failure);
        return new ConsoleLine(message, failure);
    }
}
