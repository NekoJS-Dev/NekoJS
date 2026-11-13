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
 * Typed asset generation binding (bound name {@code Assets}).
 *
 * <p>Provides KubeJS-style {@code blockState} / {@code blockModel} / {@code itemModel} /
 * {@code texture} shortcuts, writing models/block states/textures to the NekoJS disk resource pack directory
 * {@code <gameDir>/nekojs/assets} (same directory as {@link DataGeneratorJS} for the {@code generateAssets} event),
 * registered by {@code NekoJSPackLoader} as a resource pack. Lazy reading ensures
 * correct reload timing; files written during script loading take effect on the next resource reload.
 *
 * <p>JSON writing reuses {@link DataGeneratorJS} (path containment check + capacity limits + atomic write);
 * binary writing (PNG) follows the same {@link DataGeneratorJS#resolve} check without bypassing any validations.
 */
@Doc("Typed asset generators (Assets.blockState/blockModel/itemModel/texture) writing into NekoJS's assets pack.")
public final class AssetGeneratorJS {
    /** Valid characters for the resource ID namespace (consistent with vanilla resource locations: lowercase letters/numbers/underscores/dots/hyphens). */
    private static final Pattern VALID_NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    /** Valid characters for the resource ID path (additionally allowing {@code /} subdirectories). */
    private static final Pattern VALID_PATH = Pattern.compile("[a-z0-9/._-]+");
    /** Side length of the placeholder texture in pixels. */
    private static final int PLACEHOLDER_TEXTURE_SIZE = 16;

    private final DataGeneratorJS generator;

    /** Creates a generator rooted at NekoJS's default resource pack ({@code <gameDir>/nekojs/assets}). */
    public AssetGeneratorJS() {
        this(NekoJSPaths.get().assets());
    }

    /** Creates a generator rooted at the given resource pack root (used by tests). */
    public AssetGeneratorJS(Path assetsRoot) {
        this.generator = new DataGeneratorJS(assetsRoot);
    }

    /**
     * Writes a blockstate JSON to {@code assets/<ns>/blockstates/<path>.json}.
     *
     * <p>When the second argument is an object it must contain exactly one of {@code variants}
     * (object) or {@code multipart} (array). A plain string that does not start with {@code '{'} is
     * shorthand for a single empty variant holding a model id, equivalent to
     * {@code { variants: { '': { model: <string> } } }}; the model id is used as is, with no prefix
     * added.
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
     * Writes a block model JSON to {@code assets/<ns>/models/block/<path>.json}.
     *
     * <p>A string value in the {@code textures} map that contains neither {@code :} nor {@code /}
     * is expanded to {@code <ns>:block/<value>}. Any other value (one with a namespace or a
     * directory) is kept as is.
     */
    @Doc("Writes a block model JSON to assets/<ns>/models/block/<path>.json.")
    @Param(name = "id", value = "block id; plain 'foo' defaults to namespace 'minecraft'; path may contain '/' subdirectories written under models/block/")
    @Param(name = "model", value = "model object (or JSON string), e.g. { parent: 'minecraft:block/cube_all', textures: { all: 'my_tex' } }")
    public void blockModel(String id, Value model) {
        writeModel(id, model, "block");
    }

    /**
     * Writes an item model JSON to {@code assets/<ns>/models/item/<path>.json}.
     *
     * <p>A string value in the {@code textures} map that contains neither {@code :} nor {@code /}
     * is expanded to {@code <ns>:item/<value>}. Any other value is kept as is.
     */
    @Doc("Writes an item model JSON to assets/<ns>/models/item/<path>.json.")
    @Param(name = "id", value = "item id; plain 'foo' defaults to namespace 'minecraft'; path may contain '/' subdirectories written under models/item/")
    @Param(name = "model", value = "model object (or JSON string), e.g. { parent: 'minecraft:item/generated', textures: { layer0: 'my_item' } }")
    public void itemModel(String id, Value model) {
        writeModel(id, model, "item");
    }

    /**
     * Writes a 16x16 magenta placeholder texture PNG to
     * {@code assets/<ns>/textures/<kind>/<path>.png}.
     *
     * <p>If the id path already contains {@code /} (for example {@code mymod:block/my_block}), the
     * file lands at {@code textures/<path>.png} as is; otherwise {@code block/} is added as the
     * default prefix. Use {@link #texture(String, String)} to pick the kind explicitly. The
     * placeholder only makes sure a model reference has a texture; replace it with a resource pack
     * or a later overwrite for the real look.
     */
    @Doc("Writes a 16x16 magenta placeholder PNG for the texture id.")
    @Param(name = "id", value = "texture id; a path containing '/' (e.g. 'mymod:block/my_block') is used as-is under textures/, otherwise it defaults to textures/block/")
    public void texture(String id) {
        texture(id, null);
    }

    /**
     * Like {@link #texture(String)}, but picks the {@code block} or {@code item} subdirectory
     * explicitly; the id path must not contain {@code /}.
     */
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

    /* ================= Model writes ================= */

    /** Shared by blockModel and itemModel: convert, expand texture shorthands, write under the given kind. */
    private void writeModel(String id, Value model, String kind) {
        ParsedId parsed = parseId(id);
        JsonObject json = asJsonObject(model, kind + "Model '" + id + "'");
        applyTextureShorthand(json, parsed.namespace(), kind);
        generator.json(parsed.namespace() + "/models/" + kind + "/" + parsed.path() + ".json", json.toString());
    }

    /**
     * Expands texture shorthands. A string value in the {@code textures} map that contains neither
     * {@code :} nor {@code /} becomes {@code <ns>:<kind>/<value>} (for example {@code 'my_tex'} →
     * {@code 'mymod:block/my_tex'}). Non-string values, such as the object-form texture references
     * of 26.x, are kept as is.
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

    /* ================= Value conversion ================= */

    /**
     * Converts the value to a JSON object: a Graal Value is serialized, and a string is parsed as
     * JSON (a parse failure throws).
     */
    private static JsonObject asJsonObject(Value value, String what) {
        JsonElement element = toJsonElement(value, what);
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException(what + " must be a JSON object, got: " + element);
        }
        return element.getAsJsonObject();
    }

    private static JsonElement toJsonElement(Value value, String what) {
        // The parameter is a Value rather than an Object: GraalJS passes Value arguments through as they
        // are, so objects, arrays and strings are unambiguous. An Object parameter is turned into
        // "[object Object]" early on some mapping paths (reproduced in a gametest on a real instance).
        if (value.isHostObject() && value.asHostObject() instanceof JsonElement element) {
            // A Java-side call (Value.asValue(JsonObject)) passes straight through
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

    /* ================= Id parsing ================= */

    /** A parsed resource id: namespace plus path, where the path may contain subdirectories. */
    private record ParsedId(String namespace, String path) {
        /** The path inside the pack: {@code <ns>/<folder>/<path><suffix>}. */
        String file(String folder, String suffix) {
            return namespace() + "/" + folder + "/" + path() + suffix;
        }
    }

    /**
     * Parses and validates a resource id: {@code 'foo'} → {@code minecraft:foo}. The namespace and
     * the path accept only valid lowercase characters.
     */
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

    /* ================= Binary writes (same validation as DataGeneratorJS) ================= */

    /**
     * Writes binary data. Path validation reuses the composed {@link DataGeneratorJS#resolve} (the
     * same NekoJSPaths containment check), and the write uses a sibling temp file plus an atomic
     * move, with the same per-file size limit.
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

    /* ================= Placeholder PNG construction (plain JDK) ================= */

    /**
     * Builds a {@code size x size} magenta placeholder PNG (8-bit truecolor RGB). The PNG chunks are
     * built by hand with {@link CRC32} and {@link Deflater}, so no Minecraft or image library is
     * needed.
     */
    private static byte[] placeholderPng(int size) {
        byte[] ihdr = new byte[13];
        putInt(ihdr, 0, size);
        putInt(ihdr, 4, size);
        ihdr[8] = 8; // bit depth
        ihdr[9] = 2; // color type: truecolor RGB
// ihdr[10..12] stays 0: compression, filter and interlace all use their defaults

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

    /** Writes one PNG chunk: length, type, data and a CRC32 over the type and the data. */
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
