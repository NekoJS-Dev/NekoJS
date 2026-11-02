package com.tkisor.nekojs.client.render;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Executes the production public context and each 26.2 node facade with recording game contracts.
 * The fixture deliberately has no RenderBuffers.bufferSource(): the original reflective fallback
 * compiles, but submits no vertices. This checks orchestration and geometry, not GPU visibility.
 */
class WorldRenderPipelineTest {
    @TempDir
    static Path temporary;
    private static final Map<String, URLClassLoader> LOADERS = new LinkedHashMap<>();

    @BeforeAll
    static void compileProductionPaths() throws Exception {
        Path repository = Path.of("").toAbsolutePath();
        while (!Files.isRegularFile(repository.resolve("settings.gradle.kts"))) {
            repository = repository.getParent();
            if (repository == null) throw new IllegalStateException("Cannot locate repository root");
        }
        for (String node : List.of("26.2.0", "26.2.0-fabric")) {
            Path root = temporary.resolve(node);
            Files.createDirectories(root);
            List<String> arguments = new ArrayList<>(List.of("-proc:none", "-d", root.toString()));
            for (Map.Entry<String, String> fixture : fixtureSources().entrySet()) {
                Path file = root.resolve(fixture.getKey() + ".java");
                Files.createDirectories(file.getParent());
                Files.writeString(file, fixture.getValue());
                arguments.add(file.toString());
            }
            arguments.add(repository.resolve("src/main/java/com/tkisor/nekojs/client/render/WorldRenderContextJS.java").toString());
            arguments.add(repository.resolve("common/src/main/java/com/tkisor/nekojs/api/ui/UiColor.java").toString());
            Path facade = repository.resolve("versions/" + node + "/src/main/java/com/tkisor/nekojs/client/render/WorldRenderPlatform.java");
            // Keep the pre-fix path executable so it fails on zero draws, not a missing new class.
            if (Files.exists(facade)) arguments.add(facade.toString());
            StringWriter diagnostics = new StringWriter();
            var compiler = ToolProvider.getSystemJavaCompiler();
            assertTrue(compiler != null, "the rendering contract fixture requires a JDK");
            try (var manager = compiler.getStandardFileManager(null, null, null)) {
                List<String> sources = arguments.subList(3, arguments.size());
                var units = manager.getJavaFileObjectsFromStrings(sources);
                assertTrue(compiler.getTask(diagnostics, manager, null, arguments.subList(0, 3), null, units).call(),
                        node + " fixture compilation failed: " + diagnostics);
            }
            LOADERS.put(node, new URLClassLoader(new java.net.URL[] {root.toUri().toURL()}, ClassLoader.getPlatformClassLoader()));
        }
    }

    @AfterAll
    static void closeLoaders() throws Exception {
        for (URLClassLoader loader : LOADERS.values()) loader.close();
        LOADERS.clear();
    }

    @BeforeEach
    void resetRecording() throws Exception {
        for (URLClassLoader loader : LOADERS.values()) probe(loader).getMethod("reset").invoke(null);
    }

    @Test
    void publicLineSubmitsCameraRelativeColoredWidthAwareGeometry() throws Exception {
        for (URLClassLoader loader : LOADERS.values()) {
            Object context = context(loader, 30_000_000.25, 64, -20);
            Object returned = line(context, 30_000_000.5, 65, -18, 30_000_003.5, 69, -18, 0xFE123456L, 2.5f);
            assertSame(context, returned);
            List<float[]> vertices = vertices(loader);
            assertEquals(2, vertices.size(), "26.2 line must not silently skip rendering");
            assertArrayEquals(new float[] {0.25f, 1, 2, 0.6f, 0.8f, 0, 2.5f}, vertices.get(0), 0.00001f);
            assertArrayEquals(new float[] {3.25f, 5, 2, 0.6f, 0.8f, 0, 2.5f}, vertices.get(1), 0.00001f);
            assertEquals(List.of((int) 0xFE123456L, (int) 0xFE123456L), value(loader, "colors"));
            assertEquals(List.of("open", "append", "vertices", "upload", "prepare", "executeInfo", "draw", "close"), value(loader, "events"));
            assertEquals(1, value(loader, "draws"));
            assertEquals(1, value(loader, "closes"));
            assertEquals("incoming", currentModelView(loader));
        }
    }

    @Test
    void publicBoxBatchesTwelveDistinctEdgesIntoOneSubmission() throws Exception {
        for (URLClassLoader loader : LOADERS.values()) {
            Object context = context(loader, 10, 20, 30);
            assertSame(context, context.getClass().getMethod("box", double.class, double.class, double.class,
                    double.class, double.class, double.class, Number.class).invoke(context, 10, 20, 30, 12, 23, 34, 0xFFFFFFFFL));
            List<float[]> vertices = vertices(loader);
            assertEquals(24, vertices.size(), "all twelve box edges must reach the pipeline");
            java.util.Set<String> edges = new java.util.HashSet<>();
            for (int index = 0; index < vertices.size(); index += 2) {
                float[] start = vertices.get(index), end = vertices.get(index + 1);
                int changed = 0;
                for (int axis = 0; axis < 3; axis++) {
                    float maximum = new float[] {2, 3, 4}[axis];
                    assertTrue(start[axis] == 0 || start[axis] == maximum);
                    assertTrue(end[axis] == 0 || end[axis] == maximum);
                    if (start[axis] != end[axis]) changed++;
                }
                assertEquals(1, changed, "a box edge changes exactly one coordinate");
                String a = java.util.Arrays.toString(java.util.Arrays.copyOf(start, 3));
                String b = java.util.Arrays.toString(java.util.Arrays.copyOf(end, 3));
                edges.add(a.compareTo(b) < 0 ? a + b : b + a);
                assertEquals(1f, start[6]);
            }
            assertEquals(12, edges.size());
            assertEquals(1, value(loader, "draws"), "a box must not allocate twelve GPU stages");
            assertEquals(1, value(loader, "closes"));
        }
    }

    @Test
    void minimumWidthAndDegenerateDirectionStayFinite() throws Exception {
        for (URLClassLoader loader : LOADERS.values()) {
            Object context = context(loader, 0, 0, 0);
            line(context, 1, 2, 3, 1, 2, 3, -1, 0.1f);
            assertEquals(2, vertices(loader).size());
            assertArrayEquals(new float[] {1, 2, 3, 0, 0, 0, 1}, vertices(loader).get(0));
        }
    }

    @Test
    void failedSubmissionReleasesOwnedStageAndDoesNotPoisonNextCall() throws Exception {
        for (URLClassLoader loader : LOADERS.values()) {
            Object context = context(loader, 0, 0, 0);
            for (String phase : List.of("emit", "upload", "prepare", "executeInfo", "draw")) {
                probe(loader).getMethod("reset").invoke(null);
                probe(loader).getField("failPhase").set(null, phase);
                InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                        () -> line(context, 0, 0, 0, 1, 0, 0, -1, 1));
                assertInstanceOf(IllegalStateException.class, failure.getCause());
                assertEquals("fixture " + phase + " failed", failure.getCause().getMessage());
                assertEquals(1, value(loader, "closes"));
                assertEquals("incoming", currentModelView(loader));
                probe(loader).getMethod("reset").invoke(null);
                line(context, 0, 0, 0, 1, 0, 0, -1, 1);
                assertEquals(1, value(loader, "draws"));
                assertEquals(1, value(loader, "closes"));
                assertEquals("incoming", currentModelView(loader));
            }
        }
    }

    @Test
    void invalidColorFailsBeforeAllocatingGpuResources() throws Exception {
        for (URLClassLoader loader : LOADERS.values()) {
            Object context = context(loader, 0, 0, 0);
            InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                    () -> line(context, 0, 0, 0, 1, 0, 0, 1.25, 1));
            assertInstanceOf(IllegalArgumentException.class, failure.getCause());
            assertEquals(List.of(), value(loader, "events"));
        }
    }

    private static Object context(URLClassLoader loader, double x, double y, double z) throws Exception {
        Class<?> vector = loader.loadClass("net.minecraft.world.phys.Vec3");
        return loader.loadClass("com.tkisor.nekojs.client.render.WorldRenderContextJS")
                .getConstructor(vector, float.class).newInstance(vector.getConstructor(double.class, double.class, double.class)
                        .newInstance(x, y, z), 0.5f);
    }

    private static Object line(Object context, double x1, double y1, double z1, double x2, double y2, double z2,
                               Number color, float width) throws Exception {
        return context.getClass().getMethod("line", double.class, double.class, double.class,
                double.class, double.class, double.class, Number.class, float.class)
                .invoke(context, x1, y1, z1, x2, y2, z2, color, width);
    }

    private static Class<?> probe(URLClassLoader loader) throws ClassNotFoundException {
        return loader.loadClass("fixture.Probe");
    }

    private static Object value(URLClassLoader loader, String field) throws Exception {
        return probe(loader).getField(field).get(null);
    }

    private static Object currentModelView(URLClassLoader loader) throws Exception {
        Object stack = loader.loadClass("com.mojang.blaze3d.systems.RenderSystem")
                .getMethod("getModelViewStack").invoke(null);
        return stack.getClass().getField("current").get(stack);
    }

    @SuppressWarnings("unchecked")
    private static List<float[]> vertices(URLClassLoader loader) throws Exception {
        return (List<float[]>) value(loader, "vertices");
    }

    private static Map<String, String> fixtureSources() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Probe", """
                package fixture;
                public class Probe {
                    public static final java.util.List<float[]> vertices = new java.util.ArrayList<>();
                    public static final java.util.List<Integer> colors = new java.util.ArrayList<>();
                    public static final java.util.List<String> events = new java.util.ArrayList<>();
                    public static int draws, closes;
                    public static String failPhase = "";
                    public static void fail(String phase) {
                        if (phase.equals(failPhase)) throw new IllegalStateException("fixture " + phase + " failed");
                    }
                    public static void reset() { vertices.clear(); colors.clear(); events.clear(); draws=0; closes=0; failPhase=""; }
                }
                """);
        sources.put("net/minecraft/world/phys/Vec3", """
                package net.minecraft.world.phys;
                public class Vec3 {
                    public final double x,y,z;
                    public Vec3(double x,double y,double z) { this.x=x; this.y=y; this.z=z; }
                }
                """);
        sources.put("net/minecraft/client/Minecraft", """
                package net.minecraft.client;
                public class Minecraft {
                    public final GameRenderer gameRenderer = new GameRenderer();
                    public static Minecraft getInstance() { return new Minecraft(); }
                    public static class GameRenderer { public State gameRenderState() { return new State(); } }
                    public static class State { public final Level levelRenderState = new Level(); }
                    public static class Level { public final Camera cameraRenderState = new Camera(); }
                    public static class Camera { public final String viewRotationMatrix = "camera"; }
                    public net.minecraft.client.renderer.RenderBuffers renderBuffers() { return new net.minecraft.client.renderer.RenderBuffers(); }
                }
                """);
        sources.put("net/minecraft/client/renderer/RenderBuffers", """
                package net.minecraft.client.renderer;
                public class RenderBuffers {}
                """);
        sources.put("com/tkisor/nekojs/NekoJS", """
                package com.tkisor.nekojs;
                public class NekoJS {
                    public static final Log LOGGER = new Log();
                    public static class Log { public void warn(String message) {} }
                }
                """);
        sources.put("com/mojang/blaze3d/vertex/PoseStack", """
                package com.mojang.blaze3d.vertex;
                public class PoseStack {
                    public void translate(double x,double y,double z) {}
                    public Pose last() { return new Pose(); }
                    public static class Pose {}
                }
                """);
        sources.put("com/mojang/blaze3d/vertex/VertexConsumer", """
                package com.mojang.blaze3d.vertex;
                public class VertexConsumer {
                    private float[] current;
                    public VertexConsumer addVertex(float x,float y,float z) {
                        fixture.Probe.fail("emit");
                        current = new float[] {x,y,z,0,0,0,0}; fixture.Probe.vertices.add(current); return this;
                    }
                    public VertexConsumer addVertex(PoseStack.Pose pose,float x,float y,float z) { return addVertex(x,y,z); }
                    public VertexConsumer setColor(int r,int g,int b,int a) { fixture.Probe.colors.add(a<<24|r<<16|g<<8|b); return this; }
                    public VertexConsumer setNormal(float x,float y,float z) { current[3]=x; current[4]=y; current[5]=z; return this; }
                    public VertexConsumer setLineWidth(float width) { current[6]=width; return this; }
                }
                """);
        sources.put("com/mojang/blaze3d/systems/RenderSystem", """
                package com.mojang.blaze3d.systems;
                public class RenderSystem {
                    private static final ModelViewStack STACK = new ModelViewStack();
                    public static ModelViewStack getModelViewStack() { return STACK; }
                    public static class ModelViewStack {
                        public String current = "incoming";
                        private final java.util.ArrayDeque<String> saved = new java.util.ArrayDeque<>();
                        public void pushMatrix() { saved.push(current); }
                        public void set(String value) { current=value; }
                        public void popMatrix() { current=saved.pop(); }
                    }
                }
                """);
        sources.put("com/mojang/blaze3d/vertex/VertexFormat", "package com.mojang.blaze3d.vertex; public class VertexFormat {}");
        sources.put("com/mojang/blaze3d/PrimitiveTopology", "package com.mojang.blaze3d; public enum PrimitiveTopology { LINES }");
        sources.put("net/minecraft/client/renderer/rendertype/RenderTypes", """
                package net.minecraft.client.renderer.rendertype;
                public class RenderTypes { public static RenderType linesTranslucent() { return new RenderType(); } }
                """);
        sources.put("net/minecraft/client/renderer/rendertype/RenderType", """
                package net.minecraft.client.renderer.rendertype;
                public class RenderType {
                    public static final int TRANSIENT_BUFFER_SIZE = 1536;
                    public com.mojang.blaze3d.vertex.VertexFormat format() { return new com.mojang.blaze3d.vertex.VertexFormat(); }
                    public com.mojang.blaze3d.PrimitiveTopology primitiveTopology() { return com.mojang.blaze3d.PrimitiveTopology.LINES; }
                    public PreparedRenderType prepare() {
                        fixture.Probe.events.add("prepare");
                        if (!"camera".equals(com.mojang.blaze3d.systems.RenderSystem.getModelViewStack().current))
                            throw new IllegalStateException("missing late-stage camera rotation");
                        fixture.Probe.fail("prepare");
                        return new PreparedRenderType();
                    }
                }
                """);
        sources.put("net/minecraft/client/renderer/rendertype/PreparedRenderType", """
                package net.minecraft.client.renderer.rendertype;
                public class PreparedRenderType {
                    public void drawFromBuffer(net.minecraft.client.renderer.StagedVertexBuffer.ExecuteInfo info) {
                        fixture.Probe.events.add("draw");
                        fixture.Probe.fail("draw");
                        if (info == null || info.vertexCount() == 0) throw new IllegalStateException("empty draw");
                        fixture.Probe.draws++;
                    }
                }
                """);
        sources.put("net/minecraft/client/renderer/StagedVertexBuffer", """
                package net.minecraft.client.renderer;
                public class StagedVertexBuffer implements AutoCloseable {
                    private boolean uploaded;
                    public StagedVertexBuffer(java.util.function.Supplier<String> label,int capacity) { fixture.Probe.events.add("open"); }
                    public Draw appendDraw(com.mojang.blaze3d.vertex.VertexFormat format,com.mojang.blaze3d.PrimitiveTopology topology) {
                        if (topology != com.mojang.blaze3d.PrimitiveTopology.LINES) throw new IllegalStateException("wrong topology");
                        fixture.Probe.events.add("append"); return new Draw();
                    }
                    public com.mojang.blaze3d.vertex.VertexConsumer getVertexBuilder(Draw draw) {
                        fixture.Probe.events.add("vertices"); return new com.mojang.blaze3d.vertex.VertexConsumer();
                    }
                    public void upload() { fixture.Probe.events.add("upload"); fixture.Probe.fail("upload"); uploaded=true; }
                    public ExecuteInfo getExecuteInfo(Draw draw) {
                        if (!uploaded) throw new IllegalStateException("draw before upload");
                        fixture.Probe.events.add("executeInfo"); fixture.Probe.fail("executeInfo");
                        return new ExecuteInfo(fixture.Probe.vertices.size());
                    }
                    public void close() { fixture.Probe.events.add("close"); fixture.Probe.closes++; }
                    public static class Draw {}
                    public record ExecuteInfo(int vertexCount) {}
                }
                """);
        return sources;
    }
}
