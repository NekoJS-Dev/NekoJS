package com.tkisor.nekojs.client.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Focused ticket 41 checks for retained transactions and the guest-free paint path. */
class Ticket41JsxHostAdapterTest {
    @Test
    void failedCommitAndRollbackPreserveCommittedTreeAndRemovedHandlesExpire() {
        JsxHostTree tree = new JsxHostTree();
        JsxHostTree.Transaction initial = tree.begin();
        Object button = initial.create("button", "ok", Map.of("id", "ok"));
        initial.order(null, List.of(button));
        initial.commit(List.of(button));

        JsxHostTree.Transaction failed = tree.begin();
        Object staged = failed.create("label", "staged", Map.of("id", "staged"));
        failed.order(null, List.of(staged));
        assertThrows(IllegalStateException.class, () -> failed.commit(List.of()));
        failed.rollback();
        assertEquals("ok", tree.roots().getFirst().props.get("id"));

        JsxHostTree.Transaction remove = tree.begin();
        remove.remove(button);
        remove.commit(List.of());
        assertTrue(tree.roots().isEmpty());
        JsxHostTree.Transaction stale = tree.begin();
        assertThrows(IllegalArgumentException.class,
                () -> stale.update(button, "button", "ok", Map.of("id", "ok")));
        stale.rollback();
    }

    @Test
    void failedRootCleanupRetainsHostNodesForRetry() {
        JsxHostTree tree = new JsxHostTree();
        JsxHostTree.Transaction initial = tree.begin();
        Object node = initial.create("label", "label", Map.of("id", "label"));
        initial.order(null, List.of(node));
        initial.commit(List.of(node));

        assertThrows(IllegalStateException.class, () -> tree.close(() -> { throw new IllegalStateException("close failed"); }));
        assertEquals(1, tree.roots().size(), "failed root cleanup must retain the last host tree");
        tree.close(() -> { });
        assertTrue(tree.roots().isEmpty(), "successful retry releases host nodes");
    }

    @Test
    void screenPaintUsesCommittedHostNodesWithoutGuestRender() throws Exception {
        Path root = sourceRoot();
        String screen = Files.readString(root.resolve("JsxScreen.java"));
        String adapter = Files.readString(root.resolve("JsxHostAdapter.java"));
        String hostTree = Files.readString(root.resolve("JsxHostTree.java"));
        assertTrue(screen.contains("adapter.paintNode"), "paint must consume committed host nodes");
        assertTrue(!screen.contains("render("), "normal paint must not execute guest render");
        assertTrue(screen.contains("adapter.close();"), "Screen removal must use adapter cleanup");
        assertTrue(adapter.contains("tree.close(() ->"), "Screen cleanup must pass root release to the retained tree");
        assertTrue(hostTree.indexOf("closeRoot.run()") < hostTree.indexOf("clear();"), "failed root release must retain nodes");
    }

    private static Path sourceRoot() {
        Path direct = Path.of("src/main/java/com/tkisor/nekojs/client/ui");
        return Files.isDirectory(direct) ? direct : Path.of("../../src/main/java/com/tkisor/nekojs/client/ui");
    }
}
