package com.tkisor.nekojs.wrapper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.core.JsonObjectAdapter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import graal.graalvm.polyglot.Value;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * 类型化资产生成绑定（绑定名 {@code Assets}）。
 *
 * <p>提供 KubeJS 风格的 {@code blockState} / {@code blockModel} / {@code itemModel} /
 * {@code texture} 快捷方法，把模型/方块状态/占位贴图写入 NekoJS 的磁盘资源包目录
 * {@code <gameDir>/nekojs/assets}（与 {@code generateAssets} 事件的 {@link DataGeneratorJS}
 * 同一目录），该目录由 {@code NekoJSPackLoader} 注册为 resource pack，懒读磁盘保证
 * reload 时序正确——脚本加载期写入的文件在下一次资源 reload 生效。
 *
 * <p>JSON 写盘复用组合的 {@link DataGeneratorJS}（路径包含性校验 + 容量上限 + 原子写）；
 * 二进制写盘（PNG）走同一 {@link DataGeneratorJS#resolve} 校验，不绕过任何检查。
 */
@Doc("Typed asset generators (Assets.blockState/blockModel/itemModel/texture) writing into NekoJS's assets pack.")
public final class AssetGeneratorJS {
    /** 资源 id 的命名空间合法字符（与原版 resource location 一致：小写字母/数字/下划线/点/横线）。 */
    private static final Pattern VALID_NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    /** 资源 id 的路径合法字符（额外允许 {@code /} 子目录）。 */
    private static final Pattern VALID_PATH = Pattern.compile("[a-z0-9/._-]+");
    /** 占位贴图边长（像素）。 */
    private static final int PLACEHOLDER_TEXTURE_SIZE = 16;

    private final DataGeneratorJS generator;

    /** 以 NekoJS 默认资源包根（{@code <gameDir>/nekojs/assets}）构造。 */
    public AssetGeneratorJS() {
        this(NekoJSPaths.get().assets());
    }

    /** 以指定资源包根构造（测试用）。 */
    public AssetGeneratorJS(Path assetsRoot) {
        this.generator = new DataGeneratorJS(assetsRoot);
    }

    /**
     * 写方块状态 JSON 到 {@code assets/<ns>/blockstates/<path>.json}。
     *
     * <p>第二参数为对象时必须且只能含 {@code variants}（对象）或 {@code multipart}（数组）之一；
     * 为纯字符串（非 {@code '{'} 开头）时视为单个空 variant 的模型 id 简写，等价于
     * {@code { variants: { '': { model: <字符串> } } }}（模型 id 原样使用，不做前缀补全）。
     */
    @Doc("Writes a blockstate JSON to assets/<ns>/blockstates/<path>.json.")
    @Param(name = "id", value = "block id such as 'mymod:my_block'; plain 'foo' defaults to namespace 'minecraft'; path may contain '/' subdirectories")
    @Param(name = "state", value = "object with exactly one of 'variants' (object) or 'multipart' (array), a JSON string of such an object, or a plain model id string shorthand for the single empty-variant form")
    public void blockState(String id, Value state) {
        ParsedId parsed = parseId(id);
        JsonObject json;
        if (state.isString() && !state.asString().trim().startsWith("{")) {
            JsonObject variant = new JsonObject();
            variant.addProperty("model", state.asString().trim());
            JsonObject variants = new JsonObject();
            variants.add("", variant);
            json = new JsonObject();
            json.add("variants", variants);
        } else {
            json = asJsonObject(state, "blockState '" + id + "'");
        }
        boolean hasVariants = json.has("variants") && json.get("variants").isJsonObject();
        boolean hasMultipart = json.has("multipart") && json.get("multipart").isJsonArray();
        if (hasVariants == hasMultipart) {
            throw new IllegalArgumentException(
                    "blockState '" + id + "' must contain exactly one of 'variants' (object) or 'multipart' (array)");
        }
        generator.json(parsed.file("blockstates", ".json"), json.toString());
    }

    /**
     * 写方块模型 JSON 到 {@code assets/<ns>/models/block/<path>.json}。
     *
     * <p>{@code textures} 映射的字符串值在**既不含 {@code :} 也不含 {@code /}** 时自动补全为
     * {@code <ns>:block/<值>}，其余值（含命名空间或已带目录）原样保留。
     */
    @Doc("Writes a block model JSON to assets/<ns>/models/block/<path>.json.")
    @Param(name = "id", value = "block id; plain 'foo' defaults to namespace 'minecraft'; path may contain '/' subdirectories written under models/block/")
    @Param(name = "model", value = "model object (or JSON string), e.g. { parent: 'minecraft:block/cube_all', textures: { all: 'my_tex' } }")
    public void blockModel(String id, Value model) {
        writeModel(id, model, "block");
    }

    /**
     * 写物品模型 JSON 到 {@code assets/<ns>/models/item/<path>.json}。
     *
     * <p>{@code textures} 映射的字符串值在**既不含 {@code :} 也不含 {@code /}** 时自动补全为
     * {@code <ns>:item/<值>}，其余值原样保留。
     */
    @Doc("Writes an item model JSON to assets/<ns>/models/item/<path>.json.")
    @Param(name = "id", value = "item id; plain 'foo' defaults to namespace 'minecraft'; path may contain '/' subdirectories written under models/item/")
    @Param(name = "model", value = "model object (or JSON string), e.g. { parent: 'minecraft:item/generated', textures: { layer0: 'my_item' } }")
    public void itemModel(String id, Value model) {
        writeModel(id, model, "item");
    }

    /**
     * 写 16x16 洋红色占位贴图 PNG 到 {@code assets/<ns>/textures/<kind>/<path>.png}。
     *
     * <p>id 路径已含 {@code /}（如 {@code mymod:block/my_block}）时原样落到
     * {@code textures/<path>.png}；不含时默认补 {@code block/} 前缀（可用
     * {@link #texture(String, String)} 显式指定 kind）。占位图仅用于让模型引用不缺贴图，
     * 实际外观请用资源包或后续覆盖写入替换。
     */
    @Doc("Writes a 16x16 magenta placeholder PNG for the texture id.")
    @Param(name = "id", value = "texture id; a path containing '/' (e.g. 'mymod:block/my_block') is used as-is under textures/, otherwise it defaults to textures/block/")
    public void texture(String id) {
        texture(id, null);
    }

    /** 同 {@link #texture(String)}，显式指定 {@code block} / {@code item} 子目录（要求 id 路径不含 {@code /}）。 */
    @Doc("Writes a 16x16 magenta placeholder PNG under textures/<kind>/; kind is 'block' or 'item'.")
    @Param(name = "id", value = "texture id whose path must NOT contain '/' when kind is given")
    @Param(name = "kind", value = "'block' or 'item'; overrides the default 'block' directory")
    public void texture(String id, String kind) {
        ParsedId parsed = parseId(id);
        String directory;
        if (kind == null || kind.isEmpty()) {
            directory = parsed.path().contains("/") ? "" : "block/";
        } else if (kind.equals("block") || kind.equals("item")) {
            if (parsed.path().contains("/")) {
                throw new IllegalArgumentException(
                        "texture '" + id + "' already contains directories; pass no kind so the path is used as-is");
            }
            directory = kind + "/";
        } else {
            throw new IllegalArgumentException("texture kind must be 'block' or 'item', got: " + kind);
        }
        writeBytes(parsed.namespace() + "/textures/" + directory + parsed.path() + ".png",
                placeholderPng(PLACEHOLDER_TEXTURE_SIZE));
    }

    /* ================= 模型写入 ================= */

    /** blockModel/itemModel 共用：转换、贴图简写补全、按 kind 落盘。 */
    private void writeModel(String id, Value model, String kind) {
        ParsedId parsed = parseId(id);
        JsonObject json = asJsonObject(model, kind + "Model '" + id + "'");
        applyTextureShorthand(json, parsed.namespace(), kind);
        generator.json(parsed.namespace() + "/models/" + kind + "/" + parsed.path() + ".json", json.toString());
    }

    /**
     * 贴图简写补全：{@code textures} 映射里**既不含 {@code :} 也不含 {@code /}** 的字符串值
     * 补全为 {@code <ns>:<kind>/<值>}（如 {@code 'my_tex'} → {@code 'mymod:block/my_tex'}）。
     * 非字符串值（如 26.x 的对象形式贴图引用）原样保留。
     */
    private static void applyTextureShorthand(JsonObject model, String namespace, String kind) {
        JsonElement texturesElement = model.get("textures");
        if (texturesElement == null || !texturesElement.isJsonObject()) {
            return;
        }
        JsonObject rewritten = new JsonObject();
        boolean changed = false;
        for (var entry : texturesElement.getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                String texture = value.getAsString();
                if (!texture.contains(":") && !texture.contains("/")) {
                    rewritten.addProperty(entry.getKey(), namespace + ":" + kind + "/" + texture);
                    changed = true;
                    continue;
                }
            }
            rewritten.add(entry.getKey(), value);
        }
        if (changed) {
            model.add("textures", rewritten);
        }
    }

    /* ================= 值转换 ================= */

    /** 转为 JSON 对象；graal Value 自动序列化，字符串按 JSON 解析（失败则报错）。 */
    private static JsonObject asJsonObject(Value value, String what) {
        JsonElement element = toJsonElement(value, what);
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException(what + " must be a JSON object, got: " + element);
        }
        return element.getAsJsonObject();
    }

    private static JsonElement toJsonElement(Value value, String what) {
        // 参数类型取 Value 而非 Object：GraalJS 对 Value 参数按原样传入（对象/数组/字符串零歧义）；
        // Object 参数在部分映射路径下会被提前 toString 成 "[object Object]"（真机 gametest 复现）
        if (value.isHostObject() && value.asHostObject() instanceof JsonElement element) {
            // Java 侧调用（Value.asValue(JsonObject)）直通
            return element;
        }
        if (value.isString()) {
            try {
                return JsonParser.parseString(value.asString());
            } catch (RuntimeException error) {
                throw new IllegalArgumentException(what + " is not valid JSON: " + error.getMessage(), error);
            }
        }
        return JsonObjectAdapter.convertValueToJson(value);
    }

    /* ================= id 解析 ================= */

    /** 已解析的资源 id：命名空间 + 路径（路径可含子目录）。 */
    private record ParsedId(String namespace, String path) {
        /** 包内相对路径：{@code <ns>/<folder>/<path><suffix>}。 */
        String file(String folder, String suffix) {
            return namespace() + "/" + folder + "/" + path() + suffix;
        }
    }

    /** 解析并校验资源 id：{@code 'foo'} → {@code minecraft:foo}；命名空间/路径只允许小写合法字符。 */
    private static ParsedId parseId(String id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        String namespace = "minecraft";
        String path = id;
        int separator = id.indexOf(':');
        if (separator >= 0) {
            namespace = id.substring(0, separator);
            path = id.substring(separator + 1);
            if (id.indexOf(':', separator + 1) >= 0) {
                throw new IllegalArgumentException("Invalid id (more than one ':'): " + id);
            }
        }
        if (!VALID_NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException(
                    "Invalid id namespace '" + namespace + "' (expected [a-z0-9_.-]): " + id);
        }
        if (!VALID_PATH.matcher(path).matches()) {
            throw new IllegalArgumentException(
                    "Invalid id path '" + path + "' (expected [a-z0-9/._-], no uppercase): " + id);
        }
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Invalid id path (empty or '.'/'..' segment): " + id);
            }
        }
        return new ParsedId(namespace, path);
    }

    /* ================= 二进制写盘（与 DataGeneratorJS 同一校验） ================= */

    /**
     * 二进制写盘：路径校验复用组合的 {@link DataGeneratorJS#resolve}（同一
     * NekoJSPaths 包含性检查），写盘沿用 sibling temp + atomic move 与单文件容量上限。
     */
    private void writeBytes(String path, byte[] bytes) {
        if (bytes.length > DataGeneratorJS.MAX_GENERATED_FILE_BYTES) {
            throw new IllegalStateException(
                    "Generated file " + path + " is " + bytes.length + " bytes, exceeding the per-file limit of "
                            + DataGeneratorJS.MAX_GENERATED_FILE_BYTES + " bytes");
        }
        Path target = generator.resolve(path);
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = Files.createTempFile(parent, ".nekojs-gen-", ".tmp");
            try {
                Files.write(temp, bytes);
                try {
                    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException error) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Failed to write generated file " + path + ": " + error.getMessage(), error);
        }
    }

    /* ================= 占位 PNG 构造（纯 JDK） ================= */

    /**
     * 生成 {@code size x size} 的洋红色占位 PNG（8-bit truecolor RGB）。
     * PNG 块结构用 {@link CRC32} + {@link Deflater} 程序化构造，无需 MC/图像库。
     */
    private static byte[] placeholderPng(int size) {
        byte[] ihdr = new byte[13];
        putInt(ihdr, 0, size);
        putInt(ihdr, 4, size);
        ihdr[8] = 8; // bit depth
        ihdr[9] = 2; // color type: truecolor RGB
        // ihdr[10..12] 保持 0：压缩/滤波/隔行均为默认

        byte[] scanlines = new byte[size * (1 + size * 3)];
        for (int row = 0; row < size; row++) {
            int base = row * (1 + size * 3);
            scanlines[base] = 0; // filter: none
            for (int col = 0; col < size; col++) {
                int pixel = base + 1 + col * 3;
                scanlines[pixel] = (byte) 0xFF;     // R
                scanlines[pixel + 1] = 0;           // G
                scanlines[pixel + 2] = (byte) 0xFF; // B
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        pngChunk(out, "IHDR", ihdr);
        pngChunk(out, "IDAT", zlibDeflate(scanlines));
        pngChunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    /** 写一个 PNG 块：长度 + 类型 + 数据 + CRC32（覆盖类型与数据）。 */
    private static void pngChunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        writeInt(out, data.length);
        out.writeBytes(typeBytes);
        out.writeBytes(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        writeInt(out, (int) crc.getValue());
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write(value >>> 24);
        out.write(value >>> 16);
        out.write(value >>> 8);
        out.write(value);
    }

    private static void putInt(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }

    private static byte[] zlibDeflate(byte[] data) {
        Deflater deflater = new Deflater();
        try {
            deflater.setInput(data);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer));
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }
}
