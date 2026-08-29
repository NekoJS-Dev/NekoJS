package com.tkisor.nekojs.core.node;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class NekoNodePath {
    public String sep() {
        return java.io.File.separator;
    }

    public String delimiter() {
        return java.io.File.pathSeparator;
    }

    public String join(String... parts) {
        if (parts == null || parts.length == 0) return ".";
        Path path = Paths.get(parts[0] == null ? "" : parts[0]);
        for (int i = 1; i < parts.length; i++) {
            path = path.resolve(parts[i] == null ? "" : parts[i]);
        }
        return normalize(path.toString());
    }

    public String resolve(String... parts) {
        Path path = Paths.get("");
        if (parts != null) {
            for (String part : parts) {
                if (part == null || part.isEmpty()) continue;
                Path candidate = Paths.get(part);
                path = candidate.isAbsolute() ? candidate : path.resolve(candidate);
            }
        }
        return path.toAbsolutePath().normalize().toString();
    }

    public String normalize(String path) {
        if (path == null || path.isEmpty()) return ".";
        return Paths.get(path).normalize().toString();
    }

    public String dirname(String path) {
        Path parent = Paths.get(path == null ? "" : path).getParent();
        return parent == null ? "." : parent.toString();
    }

    public String basename(String path) {
        Path fileName = Paths.get(path == null ? "" : path).getFileName();
        return fileName == null ? "" : fileName.toString();
    }

    public String basename(String path, String suffix) {
        String base = basename(path);
        if (suffix != null && !suffix.isEmpty() && base.endsWith(suffix)) {
            return base.substring(0, base.length() - suffix.length());
        }
        return base;
    }

    public String extname(String path) {
        String base = basename(path);
        int dot = base.lastIndexOf('.');
        return dot <= 0 ? "" : base.substring(dot);
    }

    public String relative(String from, String to) {
        return Paths.get(from == null ? "" : from).normalize().relativize(Paths.get(to == null ? "" : to).normalize()).toString();
    }

    public boolean isAbsolute(String path) {
        return path != null && Paths.get(path).isAbsolute();
    }

    public PathParts parse(String path) {
        String value = path == null ? "" : path;
        Path parsed = Paths.get(value);
        String root = parsed.getRoot() == null ? "" : parsed.getRoot().toString();
        String dir = dirname(value);
        String base = basename(value);
        String ext = extname(value);
        String name = ext.isEmpty() ? base : base.substring(0, base.length() - ext.length());
        return new PathParts(root, dir, base, ext, name);
    }

    public String format(PathParts parts) {
        if (parts == null) return "";
        String dir = parts.dir() == null ? "" : parts.dir();
        String base = parts.base() == null || parts.base().isEmpty() ? (parts.name() == null ? "" : parts.name()) + (parts.ext() == null ? "" : parts.ext()) : parts.base();
        if (dir.isEmpty() || ".".equals(dir)) return base;
        return Paths.get(dir).resolve(base).toString();
    }

    public Posix posix() {
        return new Posix();
    }

    public Win32 win32() {
        return new Win32();
    }

    public record PathParts(String root, String dir, String base, String ext, String name) {}

    public static final class Posix {
        public String sep() { return "/"; }
        public String delimiter() { return ":"; }
        public String join(String... parts) { return normalize(String.join("/", clean(parts))); }
        public String normalize(String path) { return normalizeSeparated(path, "/"); }
        public String dirname(String path) { int index = normalize(path).lastIndexOf('/'); return index < 0 ? "." : normalize(path).substring(0, index); }
        public String basename(String path) { String value = normalize(path); int index = value.lastIndexOf('/'); return index < 0 ? value : value.substring(index + 1); }
        public String extname(String path) { String base = basename(path); int dot = base.lastIndexOf('.'); return dot <= 0 ? "" : base.substring(dot); }
        public boolean isAbsolute(String path) { return path != null && path.startsWith("/"); }
    }

    public static final class Win32 {
        public String sep() { return "\\"; }
        public String delimiter() { return ";"; }
        public String join(String... parts) { return normalize(String.join("\\", clean(parts))); }
        public String normalize(String path) { return normalizeSeparated(path, "\\"); }
        public String dirname(String path) { String value = normalize(path); int index = value.lastIndexOf('\\'); return index < 0 ? "." : value.substring(0, index); }
        public String basename(String path) { String value = normalize(path); int index = value.lastIndexOf('\\'); return index < 0 ? value : value.substring(index + 1); }
        public String extname(String path) { String base = basename(path); int dot = base.lastIndexOf('.'); return dot <= 0 ? "" : base.substring(dot); }
        public boolean isAbsolute(String path) { return path != null && (path.startsWith("\\") || path.matches("^[A-Za-z]:[/\\\\].*")); }
    }

    private static String[] clean(String[] parts) {
        if (parts == null) return new String[0];
        List<String> values = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isEmpty()) values.add(part);
        }
        return values.toArray(String[]::new);
    }

    private static String normalizeSeparated(String path, String separator) {
        if (path == null || path.isEmpty()) return ".";
        String unified = path.replace("\\", "/");
        boolean absolute = unified.startsWith("/");
        List<String> result = new ArrayList<>();
        for (String part : unified.split("/+")) {
            if (part.isEmpty() || ".".equals(part)) continue;
            if ("..".equals(part)) {
                if (!result.isEmpty() && !"..".equals(result.getLast())) result.removeLast();
                else if (!absolute) result.add(part);
            } else {
                result.add(part);
            }
        }
        String joined = String.join(separator, result);
        if (absolute) joined = separator + joined;
        return joined.isEmpty() ? (absolute ? separator : ".") : joined;
    }
}
