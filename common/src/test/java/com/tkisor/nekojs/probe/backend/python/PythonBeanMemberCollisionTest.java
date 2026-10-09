package com.tkisor.nekojs.probe.backend.python;

import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonBeanMemberCollisionTest {
    public static final class CollisionFixture {
        public int count = 4;
        private String value = "bean";

        public String getRead() { return value; }
        public void setRead(String value) { this.value = value; }
        public void setRead(int value) { this.value = Integer.toString(value); }
        public String read() { return "method"; }
        public int getCount() { return count; }
        public void setCount(int value) { count = value; }
        public String getName() { return "fixture"; }
        public static String name(String value) { return "factory:" + value; }
        public boolean isReady() { return true; }
    }

    @Test
    void originalAccessorsAndRealMethodsRemainCallableUnderProductionHostAccess() {
        try (Context context = Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(new ClassFilter(SandboxConfig.defaultConfig()))
                .allowCreateProcess(false)
                .option("js.nashorn-compat", "true")
                .build()) {
            context.getBindings("js").putMember("fixture", new CollisionFixture());
            assertTrue(context.eval("js", "fixture.read() === 'method' && fixture.getRead() === 'bean'").asBoolean());
            assertTrue(context.eval("js", "fixture.setRead('changed'); fixture.getRead() === 'changed'").asBoolean());
            assertTrue(context.eval("js", "fixture.setRead(7); fixture.getRead() === '7'").asBoolean());
            assertTrue(context.eval("js", "fixture.setCount(9); fixture.count === 9 && fixture.getCount() === 9").asBoolean());
            assertTrue(context.eval("js", "Java.type('" + CollisionFixture.class.getName()
                    + "').name('x') === 'factory:x' && fixture.getName() === 'fixture'").asBoolean());
        }
    }

    @Test
    void collisionsKeepOriginalAccessorsWithoutShadowingMethodsOrFields() {
        var declaration = new TypeReflector().reflect(CollisionFixture.class);
        var renderer = new PythonClassRenderer(new ApiTypeRefPyRenderer(Set.of(declaration.fqn)));
        String output = renderer.render(declaration);
        assertTrue(output.contains("def getRead(self) -> str"), output);
        assertTrue(output.contains("def setRead(self, value: str) -> None"), output);
        assertTrue(output.contains("def read(self) -> str"), output);
        assertTrue(output.contains("def getCount(self) -> int"), output);
        assertTrue(output.contains("def setCount(self, value: int) -> None"), output);
        assertTrue(output.contains("    count: int"), output);
        assertTrue(output.contains("def getName(self) -> str"), output);
        assertTrue(output.contains("def name(value: str) -> str"), output);
        assertFalse(output.contains("@property\n    def read("), output);
        assertFalse(output.contains("@property\n    def count("), output);
        assertFalse(output.contains("@property\n    def name("), output);
        assertTrue(output.contains("@property\n    def ready(self) -> bool"), output);
        assertTrue(output.contains("@overload\n    def setRead(self, value: int) -> None"), output);
        assertTrue(output.contains("@overload\n    def setRead(self, value: str) -> None"), output);
        assertTrue(PythonClassRenderer.hasOverloads(declaration));
    }

    @Test
    void hiddenOrRenamedMembersDoNotSuppressUnambiguousBeanProperties() {
        var declaration = new TypeReflector().reflect(CollisionFixture.class);
        declaration.methods.stream().filter(m -> m.name.equals("read")).forEach(m -> m.hidden = true);
        declaration.methods.stream().filter(m -> m.name.equals("name")).forEach(m -> m.renameTo = "factory");
        declaration.fields.forEach(f -> f.hidden = true);
        String output = new PythonClassRenderer(new ApiTypeRefPyRenderer(Set.of(declaration.fqn))).render(declaration);
        assertTrue(output.contains("@property\n    def read(self) -> str"), output);
        assertTrue(output.contains("@property\n    def count(self) -> int"), output);
        assertTrue(output.contains("@property\n    def name(self) -> str"), output);
        assertTrue(output.contains("def factory(value: str) -> str"), output);
        assertFalse(output.contains("def getRead("), output);
        assertFalse(output.contains("def getCount("), output);
    }
}
