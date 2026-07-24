package com.tkisor.nekojs.bindings.static_access;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.core.fs.NekoJSPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 脚本侧 JSON 工具（对标 KubeJS {@code JsonIO}），绑定名为 {@code JsonIO}。
 *
 * <p>GraalJS 互操作约定：脚本传入的 JS 普通对象/数组会被 GraalJS 自动转换为 Java {@code Map}/{@code List}
 * （HostAccess 的对象映射），因此 {@code toString(Object)} 等接受 {@code Object} 的方法可直接序列化
 * 脚本对象；{@code parse} 返回的 {@code Map}/{@code List} 也会被 GraalJS 呈现为 JS 对象/数组。
 *
 * <p>文件 {@code read}/{@code write} 以 {@link NekoJSPaths#root()} 解析相对路径；传入绝对路径时
 * {@link Path#resolve} 会直接返回该绝对路径，故两者都支持。
 *
 * <pre>
 * JsonIO.toString({a: 1, b: [2, 3]})          // '{"a":1,"b":[2,3]}'
 * JsonIO.toPrettyString({a: 1})               // 带缩进
 * JsonIO.parse('{"a":1}').a                    // 1
 * JsonIO.read('config/my.json')               // 读文件 → JS 对象
 * JsonIO.write('config/out.json', {x: 1})     // 写文件
 * </pre>
 */
public final class JsonIOJS {
    private static final Gson GSON = new Gson();
    private static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting().create();

    private JsonIOJS() {}

    /** 解析 JSON 字符串为 Java 对象（Map/List/Number/Boolean/String/null），脚本侧呈现为对应 JS 值。 */
    public static Object parse(String json) {
        return GSON.fromJson(json, Object.class);
    }

    /** 解析 JSON 字符串为原始 Gson {@link JsonElement}（可用于需要 JsonElement 的 adapter/binding）。 */
    public static JsonElement parseRaw(String json) {
        return JsonParser.parseString(json);
    }

    /** 把脚本对象（Map/List/原始值）序列化为紧凑 JSON 字符串。 */
    public static String toString(Object obj) {
        return GSON.toJson(obj);
    }

    /** 把脚本对象序列化为带缩进的 JSON 字符串。 */
    public static String toPrettyString(Object obj) {
        return GSON_PRETTY.toJson(obj);
    }

    /** 读取 JSON 文件并解析为对象（相对 {@link NekoJSPaths#root()}）；文件不存在返回 {@code null}。 */
    public static Object read(String path) throws IOException {
        Path p = NekoJSPaths.get().root().resolve(path);
        if (!Files.isRegularFile(p)) return null;
        try (var reader = Files.newBufferedReader(p)) {
            return GSON.fromJson(reader, Object.class);
        }
    }

    /** 把对象写入 JSON 文件（相对 {@link NekoJSPaths#root()}，带缩进，自动建父目录）。 */
    public static void write(String path, Object obj) throws IOException {
        Path p = NekoJSPaths.get().root().resolve(path);
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        Files.writeString(p, GSON_PRETTY.toJson(obj));
    }
}
