package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ui.UiDiagnosticStage;
import com.tkisor.nekojs.api.ui.UiFailure;
import com.tkisor.nekojs.api.ui.UiHostNode;
import com.tkisor.nekojs.api.ui.UiPrimitive;
import com.tkisor.nekojs.api.ui.UiRootHandle;
import com.tkisor.nekojs.api.ui.UiRootStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * code-review 修订（契约与实现一致性）：
 * <ul>
 *   <li>AC2/AC4：{@code visible=false} 真正不参与布局、绘制与已提交树。</li>
 *   <li>AC8 / 规格 127：{@code invalidate()} 之后 root 停止接受新事件。</li>
 *   <li>AC3：{@code dispatchEvent}/{@code open} 的 owner-thread 拒绝；跨线程 store 写入。</li>
 * </ul>
 */
class UiContractSemanticsTest {

    @Test
    void invisibleNodeIsExcludedFromLayoutPaintAndCommittedTree() {
        UiTestFixture fixture = new UiTestFixture();
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null,
                UiTestFixture.props("width", 100, "height", 60),
                UiTestFixture.host(UiPrimitive.LABEL, null,
                        UiTestFixture.props("text", "hidden", "visible", false)),
                UiTestFixture.host(UiPrimitive.LABEL, null,
                        UiTestFixture.props("text", "shown")));
        UiRootHandle root = fixture.facade().open(fixture.screen());

        assertEquals(List.of("screen", "label"), fixture.tagsOf(root.inspection().committedRoot()),
                "visible=false 的节点不得出现在已提交树中");
        assertEquals(2, fixture.adapter.creates().size(),
                "visible=false 的节点不得创建 host node");

        var inspection = root.paint();
        assertEquals(List.of("screen", "screen/label[1]"), inspection.paintOperations(),
                "visible=false 的节点不得参与绘制");
    }

    @Test
    void invisibleNodeIsReintroducedWhenVisibleFlipsBack() {
        UiTestFixture fixture = new UiTestFixture();
        boolean[] visible = {false};
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null, UiTestFixture.props(),
                UiTestFixture.host(UiPrimitive.PANEL, null,
                        UiTestFixture.props("visible", visible[0])));
        UiRootHandle root = fixture.facade().open(fixture.screen());
        assertEquals(List.of("screen"), fixture.tagsOf(root.inspection().committedRoot()));

        visible[0] = true;
        root.invalidateAndReconcile();
        assertEquals(List.of("screen", "panel"), fixture.tagsOf(root.inspection().committedRoot()),
                "visible 恢复后必须重新参与布局与提交");
    }

    @Test
    void invalidatedRootStopsAcceptingNewEvents() {
        UiTestFixture fixture = new UiTestFixture();
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null,
                UiTestFixture.props("width", 100, "height", 60),
                UiTestFixture.host(UiPrimitive.LABEL, null, UiTestFixture.props("text", "x")));
        UiRootHandle root = fixture.facade().open(fixture.screen());
        UiHostNode committed = root.inspection().committedRoot();

        root.invalidate();
        assertEquals(UiRootStatus.INVALIDATED, root.status());

        UiFailure failure = assertThrows(UiFailure.class,
                () -> root.dispatchEvent(UiTestFixture.click(5, 5)));
        assertEquals(UiFailure.ROOT_INVALIDATED, failure.code(),
                "invalidate() 之后必须停止接受新事件（规格 127）");
        assertTrue(fixture.dispatched.isEmpty(), "失效 root 不得再分发事件");
        assertSame(committed, root.inspection().committedRoot(),
                "失效后仍保留最后一次有效树供显示");

        // 重复失效幂等
        root.invalidate();
        assertEquals(UiRootStatus.INVALIDATED, root.status());
        assertThrows(UiFailure.class, () -> root.dispatchEvent(UiTestFixture.click(5, 5)));
    }

    @Test
    void invalidatedRootStillAllowsPaintOfLastValidTree() {
        UiTestFixture fixture = new UiTestFixture();
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null,
                UiTestFixture.props("width", 40, "height", 20));
        UiRootHandle root = fixture.facade().open(fixture.screen());
        root.invalidate();
        var inspection = root.paint();
        assertTrue(inspection.hasCommittedTree(), "失效后最后一次有效树仍可绘制");
    }

    @Test
    void operationsOnNonOwnerThreadAreRejectedExplicitly() throws Exception {
        AtomicReference<Thread> ownerThread = new AtomicReference<>();
        CountDownLatch ready = new CountDownLatch(1);
        AtomicReference<UiRootHandle> handle = new AtomicReference<>();
        AtomicReference<Throwable> setupFailure = new AtomicReference<>();
        Thread owner = new Thread(() -> {
            try {
                UiTestFixture fixture = new UiTestFixture();
                fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null,
                        UiTestFixture.props("width", 40, "height", 20));
                handle.set(fixture.facade().open(fixture.screen()));
                ownerThread.set(Thread.currentThread());
            } catch (Throwable failure) {
                setupFailure.set(failure);
            } finally {
                ready.countDown();
            }
        }, "ui-root-owner");
        owner.start();
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        assertNull(setupFailure.get(), String.valueOf(setupFailure.get()));

        UiRootHandle root = handle.get();
        AtomicReference<Throwable> eventFailure = new AtomicReference<>();
        AtomicReference<Throwable> openFailure = new AtomicReference<>();
        Thread offThread = new Thread(() -> {
            try {
                root.dispatchEvent(UiTestFixture.click(1, 1));
            } catch (Throwable failure) {
                eventFailure.set(failure);
            }
            try {
                root.open();
            } catch (Throwable failure) {
                openFailure.set(failure);
            }
        }, "ui-root-off-thread");
        offThread.start();
        offThread.join(5000);

        assertTrue(eventFailure.get() instanceof UiFailure,
                "非 owner thread 的 dispatchEvent 必须显式失败: " + eventFailure.get());
        assertEquals(UiFailure.OFF_OWNER_THREAD, ((UiFailure) eventFailure.get()).code());
        assertTrue(openFailure.get() instanceof UiFailure,
                "非 owner thread 的 open 必须显式失败: " + openFailure.get());
        assertEquals(UiFailure.OFF_OWNER_THREAD, ((UiFailure) openFailure.get()).code());
    }

    @Test
    void nonOwnerThreadStoreWriteIsRejectedExplicitly() throws Exception {
        AtomicReference<DefaultUiStateOwner> holder = new AtomicReference<>();
        AtomicReference<com.tkisor.nekojs.api.ui.UiStore> store = new AtomicReference<>();
        CountDownLatch ready = new CountDownLatch(1);
        Thread owner = new Thread(() -> {
            DefaultUiStateOwner stateOwner = new DefaultUiStateOwner(Thread.currentThread());
            holder.set(stateOwner);
            store.set(stateOwner.createStore(java.util.Map.of("title", "a")));
            ready.countDown();
        }, "ui-store-owner");
        owner.start();
        assertTrue(ready.await(5, TimeUnit.SECONDS));

        AtomicReference<Throwable> writeFailure = new AtomicReference<>();
        AtomicReference<Throwable> mergeFailure = new AtomicReference<>();
        Thread offThread = new Thread(() -> {
            try {
                store.get().write("title", "b");
            } catch (Throwable failure) {
                writeFailure.set(failure);
            }
            try {
                store.get().merge(java.util.Map.of("title", "c"));
            } catch (Throwable failure) {
                mergeFailure.set(failure);
            }
        }, "ui-store-off-thread");
        offThread.start();
        offThread.join(5000);

        assertTrue(writeFailure.get() instanceof UiFailure,
                "跨线程 store.write 必须显式失败: " + writeFailure.get());
        assertEquals(UiFailure.OFF_OWNER_THREAD, ((UiFailure) writeFailure.get()).code());
        assertTrue(mergeFailure.get() instanceof UiFailure,
                "跨线程 store.merge 必须显式失败: " + mergeFailure.get());
        assertEquals(UiFailure.OFF_OWNER_THREAD, ((UiFailure) mergeFailure.get()).code());
    }

    private static void assertSame(Object expected, Object actual, String message) {
        org.junit.jupiter.api.Assertions.assertSame(expected, actual, message);
    }

    private static void assertNull(Object value, String message) {
        org.junit.jupiter.api.Assertions.assertNull(value, message);
    }
}
