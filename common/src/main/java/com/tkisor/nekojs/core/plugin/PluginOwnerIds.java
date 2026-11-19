package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Normalizes plugin owner identifiers for module-scoped binding and event registrations.
 *
 * <p>Only the owner passed to {@code scopedTo(...)} is normalized here; raw
 * {@link com.tkisor.nekojs.api.plugin.PluginIdentity} strings remain untouched so managed API
 * contract verification preserves legacy identity semantics.
 */
public final class PluginOwnerIds {

    private static final String DEFAULT_OWNER = "nekojs";
    private static final Pattern VALID_OWNER = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern INVALID_CHARS = Pattern.compile("[^a-z0-9_.-]");
    private static final Pattern MULTIPLE_DOTS = Pattern.compile("\\.{2,}");

    private static final Set<String> CORE_PLUGIN_CLASSES = Set.of(
            "com.tkisor.nekojs.core.NekoJSCorePlugin",
            "com.tkisor.nekojs.fabric.FabricCorePlugin",
            "com.tkisor.nekojs.core.plugin.NekoBuiltinPointsPlugin"
    );

    private PluginOwnerIds() {
    }

    /**
     * Resolves a module owner identifier guaranteed to be non-empty and match {@code [a-z0-9_.-]+}.
     *
     * @param rawOwnerId raw owner string supplied by the bootstrap or extension point caller, may be {@code null}
     * @param plugin     contributing plugin instance, may be {@code null}
     * @return normalized owner identifier suitable for {@code ModuleBinding} and {@code ModuleEventGroup}
     */
    public static String resolve(String rawOwnerId, NekoJSPlugin plugin) {
        if (rawOwnerId != null && !rawOwnerId.isBlank() && VALID_OWNER.matcher(rawOwnerId).matches()) {
            return rawOwnerId;
        }
        String candidate;
        if (rawOwnerId == null || rawOwnerId.isBlank()) {
            if (plugin == null) {
                return DEFAULT_OWNER;
            }
            String className = plugin.getClass().getName();
            if (CORE_PLUGIN_CLASSES.contains(className)) {
                return DEFAULT_OWNER;
            }
            candidate = className;
        } else {
            candidate = rawOwnerId;
        }
        return sanitize(candidate);
    }

    private static String sanitize(String candidate) {
        String lowered = candidate.toLowerCase(Locale.ROOT);
        String replaced = INVALID_CHARS.matcher(lowered).replaceAll(".");
        String collapsed = MULTIPLE_DOTS.matcher(replaced).replaceAll(".");
        int start = 0;
        int end = collapsed.length();
        while (start < end && (collapsed.charAt(start) == '.' || collapsed.charAt(start) == '-')) {
            start++;
        }
        while (end > start && (collapsed.charAt(end - 1) == '.' || collapsed.charAt(end - 1) == '-')) {
            end--;
        }
        if (start >= end) {
            return DEFAULT_OWNER;
        }
        return collapsed.substring(start, end);
    }
}
