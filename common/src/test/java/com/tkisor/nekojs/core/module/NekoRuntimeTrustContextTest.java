package com.tkisor.nekojs.core.module;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NekoRuntimeTrustContextTest {
    @TempDir
    Path root;

    @Test
    void keepsLocalApprovalBoundToTheSubjectCheckedAgainstProtectedRoots() throws Exception {
        Path local = Files.writeString(root.resolve("local.js"), "local");
        Path remoteRoot = Files.createDirectory(root.resolve("remote"));
        Path remote = Files.writeString(remoteRoot.resolve("remote.js"), "remote");
        NekoRuntimeTrustContext trust = NekoRuntimeTrustContext.local();
        trust.revokeRemoteSources(remoteRoot);

        AtomicReference<Path> current = new AtomicReference<>(local);
        Path changing = (Path) Proxy.newProxyInstance(Path.class.getClassLoader(), new Class<?>[]{Path.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "normalize", "toAbsolutePath" -> proxy;
                    // Model a path retarget immediately after its first real-path observation.
                    case "toRealPath" -> current.getAndSet(remote).toRealPath();
                    default -> method.invoke(current.get(), arguments);
                });

        NekoTrustApprovedSource approval = trust.approvalFor(changing);
        assertEquals(NekoTrustApprovedSource.Kind.LOCAL_TRUSTED, approval.kind());
        assertEquals(NekoCanonicalPath.of(local), approval.subject());
        assertTrue(approval.covers(local));
        assertFalse(approval.covers(changing));
        assertFalse(approval.covers(remote));
        assertNull(trust.approvalFor(changing));
        assertNull(trust.approvalFor(remote));
    }

    @Test
    void rechecksThePathWhenRequestingAnotherLocalApproval() throws Exception {
        Path first = Files.writeString(root.resolve("first.js"), "first");
        Path second = Files.writeString(root.resolve("second.js"), "second");
        NekoRuntimeTrustContext trust = NekoRuntimeTrustContext.local();
        AtomicReference<Path> current = new AtomicReference<>(first);
        Path changing = (Path) Proxy.newProxyInstance(Path.class.getClassLoader(), new Class<?>[]{Path.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "normalize", "toAbsolutePath" -> proxy;
                    case "toRealPath" -> current.get().toRealPath();
                    default -> method.invoke(current.get(), arguments);
                });

        NekoTrustApprovedSource previous = trust.approvalFor(changing);
        current.set(second);
        assertFalse(previous.covers(changing));
        NekoTrustApprovedSource next = trust.approvalFor(changing);
        assertEquals(NekoCanonicalPath.of(second), next.subject());
        assertTrue(next.covers(changing));
    }
}
