//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.state.GenerationGlobals;
import com.tkisor.nekojs.core.state.GlobalStateStores;
import com.tkisor.nekojs.script.ScriptManager;
import com.tkisor.nekojs.wrapper.client.McFontAdapter;
import graal.graalvm.polyglot.Context;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix3x2fStack;
import sun.misc.Unsafe;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

final class NativeUiScreenFixture implements AutoCloseable {
    final Context context = Context.newBuilder("js").allowAllAccess(true).build();
    final GenerationGlobals globals = new GlobalStateStores().newGeneration(ScriptType.CLIENT, false);
    final JsxHostAdapter adapter;
    final JsxScreen screen;
    final MemoryResources resources = new MemoryResources();
    private final Field clientField;
    private final Minecraft previous;
    private Object handle;

    NativeUiScreenFixture() throws Exception {
        clientField = Minecraft.class.getDeclaredField("instance");
        clientField.setAccessible(true);
        previous = (Minecraft) clientField.get(null);
        Minecraft minecraft = allocate(Minecraft.class);
        RecordingFont font = allocate(RecordingFont.class);
        Field lineHeight = Font.class.getDeclaredField("lineHeight");
        lineHeight.setAccessible(true);
        lineHeight.set(font, 9);
        setField(minecraft, "font", font);
        setField(minecraft, "gui", allocate(net.minecraft.client.gui.Gui.class));
        adapter = allocate(JsxHostAdapter.class);
        setField(adapter, "minecraft", minecraft);
        setField(adapter, "ownerThread", Thread.currentThread());
        setField(adapter, "tree", new JsxHostTree());
        setField(adapter, "fontAdapter", new McFontAdapter(font));
        setField(adapter, "retainedErrors", new ArrayList<>());
        setField(adapter, "manager", new ScriptManager(ScriptType.CLIENT, null, null, null, null,
                null, null, null, List.of(), null));
        setField(adapter, "globals", globals);
        setField(adapter, "lifecycle", new UiRootLifecycle(false, 0, globals));
        setField(adapter, "viewportWidth", 100);
        setField(adapter, "viewportHeight", 100);
        setField(adapter, "resources", new MinecraftUiResourceResolver(resources, new UiTextureBackend() {
            @Override
            public Size upload(Identifier slot, Identifier resource, byte[] bytes) {
                throw new AssertionError("Text fixture owns no images");
            }

            @Override
            public void release(Identifier slot) {
                throw new AssertionError("Text fixture owns no images");
            }
        }, () -> resources.revision));
        screen = allocate(JsxScreen.class);
        setField(screen, "adapter", adapter);
        setScreenField("minecraft", minecraft);
        setScreenField("title", Component.literal("Fixture screen"));
        setScreenField("narrationState", new net.minecraft.client.gui.narration.ScreenNarrationCollector());
        setScreenField("narratables", new ArrayList<>());
        setField(adapter, "screen", screen);
        globals.registerUiRoot(adapter);
        clientField.set(null, minecraft);
    }

    List<String> narrate() {
        var collector = new net.minecraft.client.gui.narration.ScreenNarrationCollector();
        collector.update(output -> output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
                screen.getNarrationMessage()));
        return List.of(collector.collectNarrationText(true));
    }

    private void setScreenField(String name, Object value) throws Exception {
        Field field = net.minecraft.client.gui.screens.Screen.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(screen, value);
    }

    void installRuntime() throws Exception {
        try (var input = getClass().getResourceAsStream("/nekojs/node/modules/jsx-runtime.ts")) {
            if (input == null) throw new IllegalStateException("Canonical UI runtime is absent");
            String source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            String runtime = com.tkisor.nekojs.core.compiler.NekoTypeScriptCompiler.eraseTypescript(
                    java.nio.file.Path.of("jsx-runtime.ts"), source);
            context.eval("js", "globalThis.__nekoNodeDefine = (ids, exports) => { globalThis.UI = exports.UI }");
            context.eval("js", runtime);
            context.getBindings("js").putMember("host", adapter);
        }
    }

    void commitLabel(Map<String, Object> props, int width, int height) {
        Map<String, Object> rect = Map.of("x", 10, "y", 20, "width", width, "height", height);
        Map<String, Object> node = Map.of("type", "label", "id", "label", "visible", true, "rect", rect,
                "clip", rect, "overflow", Map.of("left", 0, "top", 0, "right", 0, "bottom", 0),
                "style", props, "children", List.of(), "bindings", List.of());
        Map<String, Object> viewport = Map.of("width", 100, "height", 100, "contentWidth", 100,
                "contentHeight", 100, "profile", 1, "designScale", 1,
                "safeArea", Map.of("left", 0, "top", 0, "right", 0, "bottom", 0));
        String snapshot = new com.google.gson.Gson().toJson(Map.of("rootId", "text-test", "viewport", viewport,
                "nodes", List.of(node), "diagnostics", List.of()));
        adapter.layout(null, null, context.eval("js", "(" + snapshot + ")"));
        JsxHostAdapter.JsxHostTransaction transaction = adapter.begin();
        if (handle == null) handle = transaction.create("label", "label", props);
        else transaction.update(handle, "label", "label", props);
        transaction.order(null, List.of(handle));
        transaction.commit(List.of(handle));
    }

    List<DrawnText> paint() throws Exception {
        return paint(-1, -1).draws;
    }

    String tooltipAt(int mouseX, int mouseY) throws Exception {
        return paint(mouseX, mouseY).tooltip;
    }

    private RecordingGraphics paint(int mouseX, int mouseY) throws Exception {
        RecordingGraphics graphics = allocate(RecordingGraphics.class);
        graphics.draws = new ArrayList<>();
        graphics.matrices = new Matrix3x2fStack(16);
        screen.extractRenderState(graphics, mouseX, mouseY, 0);
        return graphics;
    }

    @Override
    public void close() throws IllegalAccessException {
        try {
            globals.close();
        } finally {
            try {
                context.close();
            } finally {
                clientField.set(null, previous);
            }
        }
    }

    record DrawnText(String text, FontDescription font, int width, int x, int y, int color) { }

    private static final class RecordingFont extends Font {
        private RecordingFont() { super(null); }

        @Override
        public int width(String text) { return text.codePointCount(0, text.length()) * 6; }

        @Override
        public String plainSubstrByWidth(String text, int maximumWidth) {
            int count = Math.min(text.codePointCount(0, text.length()), Math.max(0, maximumWidth / 6));
            return text.substring(0, text.offsetByCodePoints(0, count));
        }

        @Override
        public int width(FormattedText text) {
            int[] width = new int[1];
            text.visit((style, segment) -> {
                boolean custom = style.getFont() instanceof FontDescription.Resource resource
                        && resource.id().equals(Identifier.parse("demo:wide"));
                width[0] += segment.codePointCount(0, segment.length()) * (custom ? 11 : 6);
                return Optional.empty();
            }, Style.EMPTY);
            return width[0];
        }
    }

    private static final class RecordingGraphics extends GuiGraphicsExtractor {
        private List<DrawnText> draws;
        private Matrix3x2fStack matrices;
        private String tooltip;

        private RecordingGraphics() { super(null, null, 0, 0); }

        @Override
        public void fill(int left, int top, int right, int bottom, int color) { }

        @Override
        public void outline(int left, int top, int right, int bottom, int color) { }

        @Override
        public Matrix3x2fStack pose() { return matrices; }

        @Override
        public void enableScissor(int left, int top, int right, int bottom) { }

        @Override
        public void disableScissor() { }

        @Override
        public void setTooltipForNextFrame(Font font, Component text, int mouseX, int mouseY) {
            tooltip = text.getString();
        }

        @Override
        public void text(Font font, String text, int x, int y, int color, boolean shadow) {
            draws.add(new DrawnText(text, FontDescription.DEFAULT, font.width(text), x, y, color));
        }

        @Override
        public void text(Font font, Component text, int x, int y, int color, boolean shadow) {
            draws.add(new DrawnText(text.getString(), text.getStyle().getFont(), font.width(text), x, y, color));
        }
    }

    static final class MemoryResources implements ResourceManager {
        private final Map<Identifier, Resource> entries = new HashMap<>();
        int revision;

        void add(String id, String json) {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            entries.put(Identifier.parse(id), new Resource(null, () -> new ByteArrayInputStream(bytes)));
        }

        @Override
        public Optional<Resource> getResource(Identifier id) { return Optional.ofNullable(entries.get(id)); }

        @Override
        public Set<String> getNamespaces() { return Set.of("demo"); }

        @Override
        public List<Resource> getResourceStack(Identifier id) { return getResource(id).stream().toList(); }

        @Override
        public Map<Identifier, Resource> listResources(String directory, Predicate<Identifier> filter) { return Map.copyOf(entries); }

        @Override
        public Map<Identifier, List<Resource>> listResourceStacks(String directory, Predicate<Identifier> filter) { return Map.of(); }

        @Override
        public Stream<PackResources> listPacks() { return Stream.empty(); }
    }

    private static <ValueType> ValueType allocate(Class<ValueType> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
//?}
