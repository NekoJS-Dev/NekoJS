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
    com.tkisor.nekojs.platform.compat.McClientCompat.Impl dashboardScreens;
    private Object previousSessionFactory;
    private Object previousScreenHost;
    private final java.util.Queue<Runnable> clientTasks = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private net.minecraft.client.gui.screens.Screen currentScreen;
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
        setField(adapter, "textLayouts", new UiTextLayoutCache());
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

    List<DrawnText> paintHost(JsxHostAdapter host) throws Exception {
        RecordingGraphics graphics = recordingGraphics();
        host.render(graphics, -1, -1);
        return List.copyOf(graphics.draws);
    }

    ChromeFrame paintHostFrame(JsxHostAdapter host) throws Exception {
        RecordingGraphics graphics = recordingGraphics();
        host.render(graphics, -1, -1);
        return new ChromeFrame(List.copyOf(graphics.fills), List.copyOf(graphics.cursors),
                List.copyOf(graphics.transformedFills));
    }

    void prepareBuiltinHost() throws Exception {
        setField(Minecraft.getInstance(), "resourceManager",
                new net.minecraft.server.packs.resources.ReloadableResourceManager(net.minecraft.server.packs.PackType.CLIENT_RESOURCES));
    }

    void installDashboardScreens() throws Exception {
        try { com.tkisor.nekojs.platform.Platform.instance(); }
        catch (IllegalStateException missing) {
            com.tkisor.nekojs.platform.Platform.init(new com.tkisor.nekojs.platform.IPlatform() {
                public boolean isClient() { return false; }
                public boolean isDevelopment() { return true; }
                public String getMcVersion() { return "test"; }
                public java.nio.file.Path getGameDir() { return com.tkisor.nekojs.TestGameDirs.unique("native-dashboard"); }
                public Map<String, com.tkisor.nekojs.platform.IModInfo> getMods() { return Map.of(); }
                public com.tkisor.nekojs.platform.IModInfo getInfo(String id) { return null; }
                public String getLoaderId() { return "test"; }
                public String getLoaderVersion() { return "0"; }
            });
        }
        prepareBuiltinHost();
        for (Class<?> type = Minecraft.class; type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                if (field.getType() == Thread.class) field.set(Minecraft.getInstance(), Thread.currentThread());
                if (type.getName().contains("BlockableEventLoop") && java.util.Queue.class.isAssignableFrom(field.getType())) {
                    field.set(Minecraft.getInstance(), clientTasks);
                }
            }
        }
        for (String name : List.of("sessions", "screenHost")) {
            var field = com.tkisor.nekojs.client.gui.ErrorDashboardScreens.class.getDeclaredField(name);
            field.setAccessible(true);
            if (name.equals("sessions")) previousSessionFactory = field.get(null);
            else previousScreenHost = field.get(null);
        }
        dashboardScreens = new com.tkisor.nekojs.platform.compat.McClientCompat.Impl() {
            @Override public net.minecraft.client.gui.screens.Screen currentScreen() { return currentScreen; }
            @Override public void showScreen(net.minecraft.client.gui.screens.Screen next) {
                var previousScreen = currentScreen;
                currentScreen = next;
                if (previousScreen != null) previousScreen.removed();
                if (next instanceof BuiltinErrorDashboard dashboard) {
                    try {
                        for (String name : List.of("minecraft", "font")) {
                            var field = net.minecraft.client.gui.screens.Screen.class.getDeclaredField(name);
                            field.setAccessible(true);
                            field.set(dashboard, name.equals("minecraft") ? Minecraft.getInstance() : Minecraft.getInstance().font);
                        }
                        dashboard.width = 854; dashboard.height = 499;
                        dashboard.init();
                    } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
                }
            }
            @Override public String renderBackendDescription() { return "fixture"; }
            @Override public void addOrUpdateSystemToast(net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId id,
                    Component title, Component description) { }
            @Override public net.minecraft.network.chat.HoverEvent hoverEventShowText(Component text) {
                return new net.minecraft.network.chat.HoverEvent.ShowText(text);
            }
        };
    }

    void flushClientTasks() {
        Runnable task;
        while ((task = clientTasks.poll()) != null) task.run();
    }

    List<TransformedText> transformedText() throws Exception {
        return List.copyOf(paint(-1, -1).transformedTexts);
    }

    void resetFontMeasurements() {
        RecordingFont font = (RecordingFont) Minecraft.getInstance().font;
        font.measurementCalls = 0;
        font.measuredCharacters = 0;
    }

    FontMeasurements fontMeasurements() {
        RecordingFont font = (RecordingFont) Minecraft.getInstance().font;
        return new FontMeasurements(font.measurementCalls, font.measuredCharacters);
    }

    record FontMeasurements(long calls, long characters) { }

    String tooltipAt(int mouseX, int mouseY) throws Exception {
        return paint(mouseX, mouseY).tooltip;
    }

    ChromeFrame chromeAt(int mouseX, int mouseY) throws Exception {
        return chromeAt(mouseX, mouseY, false);
    }

    ChromeFrame chromeAt(int mouseX, int mouseY, boolean region) throws Exception {
        RecordingGraphics graphics = paint(mouseX, mouseY, region);
        return new ChromeFrame(List.copyOf(graphics.fills), List.copyOf(graphics.cursors),
                List.copyOf(graphics.transformedFills));
    }

    void guiScale(int scale) throws Exception {
        var window = allocate(com.mojang.blaze3d.platform.Window.class);
        window.setGuiScale(scale);
        setField(Minecraft.getInstance(), "window", window);
    }

    record Fill(int left, int top, int right, int bottom, int color) { }

    record ChromeFrame(List<Fill> fills,
                       List<com.mojang.blaze3d.platform.cursor.CursorType> cursors,
                       List<TransformedFill> transformedFills) { }

    record TransformedFill(float left, float top, float right, float bottom, int color) { }

    private RecordingGraphics paint(int mouseX, int mouseY) throws Exception {
        return paint(mouseX, mouseY, false);
    }

    private RecordingGraphics paint(int mouseX, int mouseY, boolean region) throws Exception {
        RecordingGraphics graphics = recordingGraphics();
        if (region) adapter.render(graphics, mouseX, mouseY);
        else screen.extractRenderState(graphics, mouseX, mouseY, 0);
        return graphics;
    }

    private RecordingGraphics recordingGraphics() throws Exception {
        RecordingGraphics graphics = allocate(RecordingGraphics.class);
        graphics.draws = new ArrayList<>();
        graphics.fills = new ArrayList<>();
        graphics.cursors = new ArrayList<>();
        graphics.transformedFills = new ArrayList<>();
        graphics.transformedTexts = new ArrayList<>();
        graphics.matrices = new Matrix3x2fStack(16);
        return graphics;
    }

    @Override
    public void close() throws Exception {
        try {
            globals.close();
        } finally {
            try {
                context.close();
            } finally {
                try {
                    if (dashboardScreens != null) {
                        for (String name : List.of("sessions", "screenHost")) {
                            var field = com.tkisor.nekojs.client.gui.ErrorDashboardScreens.class.getDeclaredField(name);
                            field.setAccessible(true);
                            field.set(null, name.equals("sessions") ? previousSessionFactory : previousScreenHost);
                        }
                    }
                }
                finally { clientField.set(null, previous); }
            }
        }
    }

    record DrawnText(String text, FontDescription font, int width, int x, int y, int color) { }

    record TransformedText(String text, float x, float y, float width, float height) { }

    private static final class RecordingFont extends Font {
        private long measurementCalls;
        private long measuredCharacters;

        private RecordingFont() { super(null); }

        @Override
        public int width(String text) {
            measurementCalls++;
            measuredCharacters += text.length();
            return text.codePointCount(0, text.length()) * 6;
        }

        @Override
        public String plainSubstrByWidth(String text, int maximumWidth) {
            int count = Math.min(text.codePointCount(0, text.length()), Math.max(0, maximumWidth / 6));
            return text.substring(0, text.offsetByCodePoints(0, count));
        }

        @Override
        public int width(FormattedText text) {
            measurementCalls++;
            int[] width = new int[1];
            text.visit((style, segment) -> {
                measuredCharacters += segment.length();
                boolean custom = style.getFont() instanceof FontDescription.Resource resource
                        && (resource.id().equals(Identifier.parse("demo:wide"))
                        || resource.id().equals(Identifier.parse("demo:reload")));
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
        private List<Fill> fills;
        private List<com.mojang.blaze3d.platform.cursor.CursorType> cursors;
        private List<TransformedFill> transformedFills;
        private List<TransformedText> transformedTexts;

        private RecordingGraphics() { super(null, null, 0, 0); }

        @Override
        public void fill(int left, int top, int right, int bottom, int color) {
            fills.add(new Fill(left, top, right, bottom, color));
            var start = matrices.transformPosition(left, top, new org.joml.Vector2f());
            var end = matrices.transformPosition(right, bottom, new org.joml.Vector2f());
            transformedFills.add(new TransformedFill(start.x, start.y, end.x, end.y, color));
        }

        @Override
        public void requestCursor(com.mojang.blaze3d.platform.cursor.CursorType cursor) {
            cursors.add(cursor);
        }

        @Override
        public void outline(int left, int top, int width, int height, int color) {
            super.outline(left, top, width, height, color);
        }

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
            recordTransformedText(text, font.width(text), font.lineHeight, x, y);
        }

        @Override
        public void text(Font font, Component text, int x, int y, int color, boolean shadow) {
            draws.add(new DrawnText(text.getString(), text.getStyle().getFont(), font.width(text), x, y, color));
            recordTransformedText(text.getString(), font.width(text), font.lineHeight, x, y);
        }

        private void recordTransformedText(String text, int width, int height, int x, int y) {
            var start = matrices.transformPosition(x, y, new org.joml.Vector2f());
            var end = matrices.transformPosition(x + width, y + height, new org.joml.Vector2f());
            transformedTexts.add(new TransformedText(text, start.x, start.y, end.x - start.x, end.y - start.y));
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
