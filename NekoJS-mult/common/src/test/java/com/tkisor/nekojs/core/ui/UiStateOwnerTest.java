package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ui.UiDiagnosticStage;
import com.tkisor.nekojs.api.ui.UiFailure;
import com.tkisor.nekojs.api.ui.UiSignal;
import com.tkisor.nekojs.api.ui.UiStateOwner;
import com.tkisor.nekojs.api.ui.UiStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC3：signal/store 的读取、写入、批量失效、依赖重建、disposed root 写入和
 * owner-thread 拒绝语义可从公开调用者 Interface 观察，不检查私有依赖集合。
 */
class UiStateOwnerTest {

    /** 观察「哪些 root 被请求 reconcile」。 */
    private static final class FakeRoot implements com.tkisor.nekojs.api.ui.UiRootHandle {
        int reconciles;

        @Override public String rootId() { return "fake-root"; }
        @Override public int generation() { return 1; }
        @Override public com.tkisor.nekojs.api.ui.UiRootStatus status() {
            return com.tkisor.nekojs.api.ui.UiRootStatus.OPEN;
        }
        @Override public boolean isActive() { return true; }
        @Override public com.tkisor.nekojs.api.ui.UiScreenOptions options() {
            return com.tkisor.nekojs.api.ui.UiScreenOptions.defaults();
        }
        @Override public void open() { }
        @Override public void invalidate() { }
        @Override public void invalidateAndReconcile() { reconciles++; }
        @Override public void dispatchEvent(com.tkisor.nekojs.api.ui.UiEvent event) { }
        @Override public com.tkisor.nekojs.api.ui.UiHostInspection paint() {
            return com.tkisor.nekojs.api.ui.UiHostInspection.empty();
        }
        @Override public void dispose() { }
        @Override public com.tkisor.nekojs.api.ui.UiHostInspection inspection() {
            return com.tkisor.nekojs.api.ui.UiHostInspection.empty();
        }
        @Override public com.tkisor.nekojs.api.ui.UiVNode committedTree() { return null; }
        @Override public String focusedPath() { return null; }
    }

    @Test
    void signalReadDuringRenderEstablishesDependencyAndWriteInvalidatesOnlyThatRoot() {
        DefaultUiStateOwner owner = new DefaultUiStateOwner(Thread.currentThread());
        UiSignal<Integer> count = owner.createSignal(0);
        FakeRoot reader = new FakeRoot();
        FakeRoot other = new FakeRoot();
        owner.register(reader, reader::invalidateAndReconcile);
        owner.register(other, other::invalidateAndReconcile);

        owner.beginTracking(reader);
        assertEquals(0, count.get());
        owner.endTracking(reader);

        count.set(1);
        owner.runTick();
        assertEquals(1, reader.reconciles, "读取过该 signal 的 root 必须被失效");
        assertEquals(0, other.reconciles, "未读取该 signal 的 root 不得被失效");

        owner.beginTracking(reader);
        assertEquals(1, count.get());
        owner.endTracking(reader);
    }

    @Test
    void repeatedWritesInSameTickCoalesceIntoOneReconcile() {
        DefaultUiStateOwner owner = new DefaultUiStateOwner(Thread.currentThread());
        UiSignal<Integer> count = owner.createSignal(0);
        FakeRoot root = new FakeRoot();
        owner.register(root, root::invalidateAndReconcile);
        owner.beginTracking(root);
        count.get();
        owner.endTracking(root);

        count.set(1);
        count.set(2);
        count.set(3);
        owner.runTick();

        assertEquals(1, root.reconciles, "同一 tick 内的重复失效必须合并为一次 reconcile");
        assertEquals(3, count.get());
    }

    @Test
    void noOpWriteDoesNotInvalidate() {
        DefaultUiStateOwner owner = new DefaultUiStateOwner(Thread.currentThread());
        UiSignal<String> label = owner.createSignal("a");
        FakeRoot root = new FakeRoot();
        owner.register(root, root::invalidateAndReconcile);
        owner.beginTracking(root);
        label.get();
        owner.endTracking(root);

        label.set("a");
        owner.runTick();
        assertEquals(0, root.reconciles, "写入相同值不得触发失效");
    }

    @Test
    void dependencyRebuildsWhenRenderStopsReadingASignal() {
        DefaultUiStateOwner owner = new DefaultUiStateOwner(Thread.currentThread());
        UiSignal<Integer> used = owner.createSignal(0);
        UiSignal<Integer> dropped = owner.createSignal(0);
        FakeRoot root = new FakeRoot();
        owner.register(root, root::invalidateAndReconcile);

        owner.beginTracking(root);
        used.get();
        dropped.get();
        owner.endTracking(root);

        // 下一次 render 不再读取 dropped：依赖集合重建
        owner.beginTracking(root);
        used.get();
        owner.endTracking(root);

        dropped.set(1);
        owner.runTick();
        assertEquals(0, root.reconciles, "重建后不再读取的依赖不得再触发失效");

        used.set(1);
        owner.runTick();
        assertEquals(1, root.reconciles);
    }

    @Test
    void storeFieldReadsTrackPerKeyAndMergeIsOneInvalidation() {
        DefaultUiStateOwner owner = new DefaultUiStateOwner(Thread.currentThread());
        UiStore store = owner.createStore(Map.of("title", "a", "count", 0));
        FakeRoot titleReader = new FakeRoot();
        owner.register(titleReader, titleReader::invalidateAndReconcile);
        owner.beginTracking(titleReader);
        store.read("title");
        owner.endTracking(titleReader);

        store.write("count", 5);
        owner.runTick();
        assertEquals(0, titleReader.reconciles, "未读取的字段写入不得失效该 root");

        store.merge(Map.of("title", "b", "count", 6));
        owner.runTick();
        assertEquals(1, titleReader.reconciles, "merge 必须作为一次失效");
        assertEquals("b", store.read("title"));
        assertEquals(6, store.read("count"));
    }

    @Test
    void disposedRootNoLongerReceivesInvalidation() {
        DefaultUiStateOwner owner = new DefaultUiStateOwner(Thread.currentThread());
        UiSignal<Integer> count = owner.createSignal(0);
        FakeRoot root = new FakeRoot();
        Runnable unregister = owner.register(root, root::invalidateAndReconcile);
        owner.beginTracking(root);
        count.get();
        owner.endTracking(root);

        unregister.run();
        count.set(1);
        owner.runTick();
        assertEquals(0, root.reconciles, "disposed root 不得再收到 reconcile");

        // owner 自身释放后，写入不再触发任何 root
        owner.dispose();
        assertTrue(owner.isDisposed());
        count.set(2);
        owner.runTick();
        assertEquals(0, root.reconciles);
    }

    @Test
    void nonOwnerThreadWriteFailsExplicitly() throws Exception {
        AtomicReference<Thread> ownerThread = new AtomicReference<>();
        CountDownLatch ready = new CountDownLatch(1);
        AtomicReference<DefaultUiStateOwner> holder = new AtomicReference<>();
        Thread owner = new Thread(() -> {
            holder.set(new DefaultUiStateOwner(Thread.currentThread()));
            ownerThread.set(Thread.currentThread());
            ready.countDown();
        }, "ui-owner-test");
        owner.start();
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        DefaultUiStateOwner stateOwner = holder.get();

        UiSignal<Integer> signal = stateOwner.createSignal(0);
        AtomicReference<Throwable> offThreadFailure = new AtomicReference<>();
        Thread writer = new Thread(() -> {
            try {
                signal.set(1);
            } catch (Throwable failure) {
                offThreadFailure.set(failure);
            }
        }, "ui-off-thread-writer");
        writer.start();
        writer.join(5000);

        assertTrue(offThreadFailure.get() instanceof UiFailure,
                "非 owner thread 写入必须显式失败: " + offThreadFailure.get());
        assertEquals(UiFailure.OFF_OWNER_THREAD, ((UiFailure) offThreadFailure.get()).code());
        assertEquals(0, signal.get(), "失败的跨线程写入不得改变值");

        // 非 owner thread 驱动 tick 同样显式失败
        AtomicReference<Throwable> tickFailure = new AtomicReference<>();
        Thread ticker = new Thread(() -> {
            try {
                stateOwner.runTick();
            } catch (Throwable failure) {
                tickFailure.set(failure);
            }
        });
        ticker.start();
        ticker.join(5000);
        assertTrue(tickFailure.get() instanceof UiFailure);
        assertEquals(UiFailure.OFF_OWNER_THREAD,
                ((UiFailure) tickFailure.get()).code());
    }

    @Test
    void ownerThreadReportsItselfAndDisposeIsIdempotent() {
        DefaultUiStateOwner owner = new DefaultUiStateOwner(Thread.currentThread());
        assertTrue(owner.onOwnerThread());
        assertEquals(Thread.currentThread(), owner.ownerThread());
        owner.dispose();
        owner.dispose();
        assertTrue(owner.isDisposed());
    }
}
