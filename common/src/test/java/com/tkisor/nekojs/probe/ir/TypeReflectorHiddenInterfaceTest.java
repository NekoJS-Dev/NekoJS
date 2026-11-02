package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Hidden host implementation contracts must not reappear through TypeScript heritage. */
class TypeReflectorHiddenInterfaceTest {
    public interface InternalContract {
        void read();
        default boolean remove(String name) { return false; }
    }
    public static class HiddenImplementation implements InternalContract {
        @HideFromJS public void read() {}
        @HideFromJS public boolean remove(String name) { return false; }
        public String visible() { return "visible"; }
    }
    public static class HiddenSubclass extends HiddenImplementation implements InternalContract {}
    public static class PartialImplementation implements InternalContract {
        @HideFromJS public void read() {}
    }
    public interface AbstractContract { void pending(); }
    public abstract static class HiddenAbstractOwner implements AbstractContract {
        @HideFromJS public abstract void pending();
    }
    public interface HiddenChildContract extends AbstractContract {
        @HideFromJS void pending();
    }
    public interface Marker {}
    public interface StaticOnly { static void utility() {} }
    public static class MarkerImplementation implements Marker, StaticOnly {}
    public interface GenericContract<T> { void accept(T value); }
    public static class GenericImplementation implements GenericContract<String> {
        @HideFromJS public void accept(String value) {}
    }

    @Test void whollyHiddenInterfaceCannotReintroduceHostMethods() {
        TypeDecl declaration = new TypeReflector().reflect(HiddenImplementation.class);
        assertTrue(declaration.interfaces.isEmpty());
        TypeScriptClassRenderer renderer = new TypeScriptClassRenderer(new TypeAliasRegistry());
        renderer.setClassDeclarations(Map.of(declaration.fqn, declaration));
        String output = renderer.render(declaration);
        assertFalse(output.contains("$InternalContract"), output);
        assertFalse(output.contains("read("), output);
        assertFalse(output.contains("remove("), output);
        assertTrue(output.contains("visible("), output);
    }

    @Test void inheritedHiddenImplementationsAreResolvedOnTheConcreteClass() {
        assertTrue(new TypeReflector().reflect(HiddenSubclass.class).interfaces.isEmpty());
    }

    @Test void aVisibleDefaultMethodKeepsTheInterfaceContract() {
        assertEquals(1, new TypeReflector().reflect(PartialImplementation.class).interfaces.size());
    }

    @Test void hiddenAbstractDeclarationsKeepTheUnimplementedContract() {
        assertEquals(1, new TypeReflector().reflect(HiddenAbstractOwner.class).interfaces.size());
    }

    @Test void hiddenChildInterfaceDeclarationsKeepTheirParentContract() {
        assertEquals(1, new TypeReflector().reflect(HiddenChildContract.class).interfaces.size());
    }

    @Test void markerAndStaticOnlyInterfacesRemainIntact() {
        assertEquals(2, new TypeReflector().reflect(MarkerImplementation.class).interfaces.size());
    }

    @Test void hiddenGenericImplementationsRetainTheSameErasureResolution() {
        assertTrue(new TypeReflector().reflect(GenericImplementation.class).interfaces.isEmpty());
    }
}
