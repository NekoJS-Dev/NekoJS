package test;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventGroupJS;
import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.utils.event.dispatch.DispatchKey;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.HostAccess;
import net.neoforged.fml.loading.FMLLoader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * @author ZZZank
 */
public class EventBusJSTest {
    static {
        // 不这样会导致GraalJS加载时ClassLoadingGuardian骂人
        // 不涉及GraalJS的测试不需要这样
        Thread.currentThread().setContextClassLoader(FMLLoader.getCurrent().getCurrentClassLoader());
    }

    private static final EventGroup EVENT_GROUP = EventGroup.of("TestEvents");
    private static final EventBusJS<IntSupplier, Void> CANCELLABLE =
        EVENT_GROUP.add("cancellable", ScriptType.COMMON, EventBusJS.of(IntSupplier.class, true));

    private static IntSupplier supplyInt(int value) {
        return () -> value;
    }

    @Test
    public void testCancellable() {
        try (var cx = createTestContext()) {
            Assertions.assertFalse(CANCELLABLE.post(supplyInt(42)));
            Assertions.assertFalse(CANCELLABLE.post(supplyInt(0)));

            cx.eval(
                "js", """
                    TestEvents.cancellable((e) => e.getAsInt() != 0);"""
            );

            Assertions.assertTrue(CANCELLABLE.post(supplyInt(42)));
            Assertions.assertFalse(CANCELLABLE.post(supplyInt(0)));
        }
    }

    private static final EventBusJS<IntConsumer, Void> REGULAR =
        EVENT_GROUP.add("regular", ScriptType.COMMON, EventBusJS.of(IntConsumer.class));

    @Test
    public void testRegular() {
        try (var cx = createTestContext()) {
            Assertions.assertFalse(REGULAR.post(null));
            Assertions.assertFalse(REGULAR.post(ignored -> {}));

            cx.eval(
                "js", """
                    TestEvents.regular(e => e.accept(42));"""
            );

            var holder = new int[1];
            Assertions.assertFalse(REGULAR.post(value -> holder[0] = value));
            Assertions.assertEquals(42, holder[0]);
        }
    }

    private static final EventBusJS<List, String> DISPATCH =
        EVENT_GROUP.add("dispatch",
            ScriptType.COMMON,
            EventBusJS.of(List.class, false, DispatchKey.of(String.class, Object::toString))
        );

    @Test
    public void testDispatch() {
        try (var cx = createTestContext()) {
            var toTest = new ArrayList<String>();
            Assertions.assertFalse(DISPATCH.post(null));
            Assertions.assertFalse(DISPATCH.post(toTest));

            cx.eval(
                "js", """
                    TestEvents.dispatch(list => list.add('noop'));
                    TestEvents.dispatch('noop', list => list.add('matched'));
                    """
            );

            toTest = new ArrayList<>();
            Assertions.assertFalse(DISPATCH.post(toTest));
            Assertions.assertEquals(Set.of("noop"), Set.copyOf(toTest));

            toTest = new ArrayList<>();
            Assertions.assertFalse(DISPATCH.post(toTest, "noop"));
            Assertions.assertEquals(Set.of("noop", "matched"), Set.copyOf(toTest));
        }
    }

    private static Context createTestContext() {
        var cx = Context.newBuilder("js")
            .allowHostAccess(HostAccess.ALL)
            .build();

        var bindings = cx.getBindings("js");
        bindings.putMember(EVENT_GROUP.name(), new EventGroupJS(EVENT_GROUP, ScriptType.COMMON));

        return cx;
    }
}
