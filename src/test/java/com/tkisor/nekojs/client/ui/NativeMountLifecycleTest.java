//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises root cleanup through the retained native host, not a second guest reconciler. */
class NativeMountLifecycleTest {
    @Test
    void rejectedGuestCloseRetainsTheMountedTreeAndCanBeRetried() throws Exception {
        try (var fixture = new NativeUiScreenFixture()) {
            fixture.commitLabel(java.util.Map.of("text", "Retained"), 40, 10);
            fixture.context.eval("js", "globalThis.acceptClose=false;globalThis.retryRoot={close(){return acceptClose},forceClose(){globalThis.forced=true}}");
            fixture.adapter.bindRoot(fixture.context.eval("js", "retryRoot"));
            fixture.adapter.close();
            assertFalse(fixture.screen.isDisposed());
            assertEquals(1, fixture.adapter.roots().size(), "a rejected close must retain the native tree");
            assertEquals(0L, fixture.adapter.performanceCounters().get("cleanup"));
            fixture.context.eval("js", "acceptClose=true");
            fixture.adapter.close();
            assertTrue(fixture.adapter.roots().isEmpty());
            assertEquals(1L, fixture.adapter.performanceCounters().get("cleanup"));
        }
    }

    @Test
    void pendingOrFailedGuestCloseIsNotAcceptance() throws Exception {
        for (String outcome : java.util.List.of("return 'queued'", "return undefined", "throw Error('clear failed')")) {
            try (var fixture = new NativeUiScreenFixture()) {
                fixture.commitLabel(java.util.Map.of("text", "Retained"), 40, 10);
                fixture.adapter.bindRoot(fixture.context.eval("js", "({close(){" + outcome + "},forceClose(){}})"));
                assertFalse(fixture.adapter.close(), outcome);
                assertEquals(1, fixture.adapter.roots().size());
                assertEquals(0L, fixture.adapter.performanceCounters().get("cleanup"));
            }
        }
    }

    @Test
    void committedRootScreenPropsUpdateNativeTitleAndPauseWithoutReopening() throws Exception {
        try (var fixture = new NativeUiScreenFixture()) {
            fixture.installRuntime();
            fixture.context.eval("js", """
                    globalThis.title = 'Managed title'; globalThis.pause = true; globalThis.reject = false;
                    globalThis.root = UI.createRoot(() => {
                      if (reject) throw Error('candidate failed');
                      return UI.element('screen', {id:'page', title, pausesGame:pause, children:[]});
                    }, host); host.bindRoot(root);
                    """);
            assertEquals("Managed title", fixture.screen.getTitle().getString());
            assertTrue(fixture.screen.isPauseScreen());
            fixture.context.eval("js", "title='Updated title'; pause=false; root.refresh()");
            assertEquals("Updated title", fixture.screen.getTitle().getString());
            assertFalse(fixture.screen.isPauseScreen());
            fixture.context.eval("js", "title='Rejected title'; pause=true; reject=true; root.refresh()");
            assertEquals("Updated title", fixture.screen.getTitle().getString());
            assertFalse(fixture.screen.isPauseScreen());
        }
    }

    @Test
    void competingCommonRootCannotOverwriteTheBoundRootsPublishedTree() throws Exception {
        try (var fixture = new NativeUiScreenFixture()) {
            fixture.installRuntime();
            fixture.context.eval("js", """
                    globalThis.clicks=0;
                    globalThis.root=UI.createRoot(() => UI.element(
                      'button', {id:'original',text:'Original',width:80,height:20,onClick:()=>clicks++}),host);
                    host.bindRoot(root);
                    """);
            String originalRootId = fixture.adapter.inspect().rootId();
            long identity = fixture.adapter.roots().getFirst().identity;
            assertThrows(graal.graalvm.polyglot.PolyglotException.class, () -> fixture.context.eval("js", """
                    UI.createRoot(() => UI.element(
                      'label', {id:'replacement',text:'Replacement',width:80,height:20}),host);
                    """));
            assertEquals(identity, fixture.adapter.roots().getFirst().identity);
            assertEquals("original", fixture.adapter.roots().getFirst().props.get("id"));
            assertEquals(originalRootId, fixture.adapter.inspect().rootId());
            assertTrue(fixture.adapter.dispatchAt(5, 5, "click", 0, 0));
            assertEquals(1, fixture.context.eval("js", "clicks").asInt());
            assertEquals(0L, fixture.adapter.performanceCounters().get("cleanup"));
        }
    }

    @Test
    void rootBindingIsIdempotentButCannotReplaceItsGuestHandle() throws Exception {
        try (var fixture = new NativeUiScreenFixture()) {
            fixture.commitLabel(java.util.Map.of("text", "Retained"), 40, 10);
            fixture.context.eval("js", "globalThis.first={id:'first',close(){return false},forceClose(){}};globalThis.second={id:'second',close(){return true},forceClose(){}}");
            fixture.adapter.bindRoot(fixture.context.eval("js", "first"));
            assertDoesNotThrow(() -> fixture.adapter.bindRoot(fixture.context.eval("js", "first")));
            assertThrows(IllegalStateException.class, () -> fixture.adapter.bindRoot(fixture.context.eval("js", "second")));
            assertFalse(fixture.adapter.close(), "the original guest handle must remain bound");
            assertEquals(1, fixture.adapter.roots().size());
            assertEquals(0L, fixture.adapter.performanceCounters().get("cleanup"));
        }
    }

    @Test
    void ordinaryCloseDuringCommonRefreshDoesNotDisposeTheInFlightMount() throws Exception {
        try (var fixture = new NativeUiScreenFixture()) {
            fixture.installRuntime();
            fixture.context.eval("js", """
                    globalThis.attemptClose=false;globalThis.closeResult=null;globalThis.label='Original';
                    globalThis.root=UI.createRoot(() => {
                      if(attemptClose) closeResult=host.close();
                      return UI.element('label',{id:'stable',text:label,width:80,height:20});
                    },host);
                    host.bindRoot(root);
                    attemptClose=true;label='Updated';root.refresh();
                    """);
            assertFalse(fixture.context.eval("js", "closeResult").asBoolean());
            assertEquals("Updated", fixture.adapter.roots().getFirst().props.get("text"));
            assertEquals(0L, fixture.adapter.performanceCounters().get("cleanup"));
        }
    }

    @Test
    void screenRemovalForcesGuestAndNativeDisposalOnceEvenWhenGuestClearFails() throws Exception {
        try (var fixture = new NativeUiScreenFixture()) {
            fixture.commitLabel(java.util.Map.of("text", "Retained"), 40, 10);
            fixture.context.eval("js", "globalThis.forced=0");
            fixture.adapter.bindRoot(fixture.context.eval("js", "({close(){return false},forceClose(){forced++;throw Error('host clear failed')}})"));
            fixture.screen.removed();
            fixture.screen.removed();
            assertEquals(1, fixture.context.eval("js", "forced").asInt());
            assertTrue(fixture.screen.isDisposed());
            assertTrue(fixture.adapter.roots().isEmpty());
            assertEquals(1L, fixture.adapter.performanceCounters().get("cleanup"));
        }
    }
}
//?}
