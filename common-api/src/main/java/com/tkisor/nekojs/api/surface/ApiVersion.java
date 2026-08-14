package com.tkisor.nekojs.api.surface;

import java.util.Comparator;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 语义化版本（SemVer 2.0.0），{@code major.minor.patch[-prerelease][+build]}。
 *
 * <p>实现 {@link Comparable}，比较规则遵循 SemVer：先比较主/次/补丁版本，再比较预发布
 * 标识（无预发布 > 有预发布；预发布内部按数字/字典序分段比较）；build 元数据不参与比较。
 * 不可变。
 *
 * @param major      主版本
 * @param minor      次版本
 * @param patch      补丁版本
 * @param prerelease 预发布标识（如 {@code "alpha.1"}），可为 {@code null}
 * @param build      build 元数据，可为 {@code null}
 */
public record ApiVersion(int major, int minor, int patch, String prerelease, String build)
        implements Comparable<ApiVersion> {

    /** 拒绝负数版本分量；{@link #parse(String)} 不会产生，但直接构造可能。 */
    public ApiVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("negative version component: " + major + "." + minor + "." + patch);
        }
    }

    private static final Pattern SEMVER = Pattern.compile(
            "^(?<major>0|[1-9]\\d*)\\.(?<minor>0|[1-9]\\d*)\\.(?<patch>0|[1-9]\\d*)" +
            "(?:-(?<prerelease>[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?" +
            "(?:\\+(?<build>[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?$");

    /** 解析 SemVer 字符串；非法格式抛 {@link IllegalArgumentException}。 */
    public static ApiVersion parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        Matcher m = SEMVER.matcher(raw);
        if (!m.matches()) throw new IllegalArgumentException("invalid semver: " + raw);
        return new ApiVersion(
                Integer.parseInt(m.group("major")),
                Integer.parseInt(m.group("minor")),
                Integer.parseInt(m.group("patch")),
                m.group("prerelease"),
                m.group("build"));
    }

    /** 按 SemVer 规则比较（build 元数据不参与）。 */
    @Override
    public int compareTo(ApiVersion other) {
        int c = Integer.compare(this.major, other.major);
        if (c != 0) return c;
        c = Integer.compare(this.minor, other.minor);
        if (c != 0) return c;
        c = Integer.compare(this.patch, other.patch);
        if (c != 0) return c;
        // prerelease presence: no prerelease > has prerelease
        if (this.prerelease == null && other.prerelease != null) return 1;
        if (this.prerelease != null && other.prerelease == null) return -1;
        if (this.prerelease != null) {
            return comparePrerelease(this.prerelease, other.prerelease);
        }
        return 0;
    }

    private static int comparePrerelease(String a, String b) {
        String[] aParts = a.split("\\.");
        String[] bParts = b.split("\\.");
        int len = Math.min(aParts.length, bParts.length);
        for (int i = 0; i < len; i++) {
            boolean aNum = isNumeric(aParts[i]);
            boolean bNum = isNumeric(bParts[i]);
            if (aNum && bNum) {
                int c = Long.compare(Long.parseLong(aParts[i]), Long.parseLong(bParts[i]));
                if (c != 0) return c;
            } else if (aNum) {
                return -1; // numeric < alphanumeric
            } else if (bNum) {
                return 1;
            } else {
                int c = aParts[i].compareTo(bParts[i]);
                if (c != 0) return c;
            }
        }
        return Integer.compare(aParts.length, bParts.length);
    }

    private static boolean isNumeric(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    /** 返回规范 SemVer 字符串。 */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(major).append('.').append(minor).append('.').append(patch);
        if (prerelease != null) sb.append('-').append(prerelease);
        if (build != null) sb.append('+').append(build);
        return sb.toString();
    }
}
