package com.tkisor.nekojs.core.dynamic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DynamicRegistrationBookkeeping} claim/stale transition semantics and
 * {@link DynamicRegisterMode} parsing — the platform-free half of the
 * DynamicRegistry feature.
 */
class DynamicRegistrationBookkeepingTest {

    @Test
    void claimCreatesTrackedEntry() {
        DynamicRegistrationBookkeeping bookkeeping = new DynamicRegistrationBookkeeping("item");

        bookkeeping.claim("mymod:ruby", "nekojs:server/foo.js", DynamicRegisterMode.WORLD);

        assertEquals(1, bookkeeping.count());
        assertTrue(bookkeeping.isTracked("mymod:ruby"));
        assertTrue(bookkeeping.staleIds().isEmpty());
        var entry = bookkeeping.entry("mymod:ruby");
        assertEquals("mymod:ruby", entry.id());
        assertEquals("nekojs:server/foo.js", entry.ownerScriptId());
        assertSame(DynamicRegisterMode.WORLD, entry.mode());
        assertFalse(entry.stale());
    }

    @Test
    void beginReloadMarksEverythingStaleAndReclaimClearsIt() {
        DynamicRegistrationBookkeeping bookkeeping = new DynamicRegistrationBookkeeping("sound_event");
        bookkeeping.claim("mymod:boom", "nekojs:server/a.js", DynamicRegisterMode.RELOADABLE);
        bookkeeping.claim("mymod:ping", "nekojs:server/b.js", DynamicRegisterMode.WORLD);

        bookkeeping.beginReload();

        assertEquals(List.of("mymod:boom", "mymod:ping"), bookkeeping.staleIds());
        assertEquals(2, bookkeeping.count(), "stale-retain: entries stay tracked");

        // script a re-runs and re-claims its id
        bookkeeping.claim("mymod:boom", "nekojs:server/a.js", DynamicRegisterMode.RELOADABLE);

        assertEquals(List.of("mymod:ping"), bookkeeping.staleIds());
    }

    @Test
    void repeatedReloadWithoutReclaimStaysStale() {
        DynamicRegistrationBookkeeping bookkeeping = new DynamicRegistrationBookkeeping("mob_effect");
        bookkeeping.claim("mymod:wither", "nekojs:server/gone.js", DynamicRegisterMode.WORLD);

        bookkeeping.beginReload();
        bookkeeping.beginReload();

        assertEquals(List.of("mymod:wither"), bookkeeping.staleIds());
        assertTrue(bookkeeping.isTracked("mymod:wither"), "never unregistered mid-session");
    }

    @Test
    void reclaimCanChangeOwnerAndMode() {
        DynamicRegistrationBookkeeping bookkeeping = new DynamicRegistrationBookkeeping("item");
        bookkeeping.claim("mymod:ruby", "nekojs:server/old.js", DynamicRegisterMode.WORLD);

        bookkeeping.beginReload();
        bookkeeping.claim("mymod:ruby", "nekojs:server/packs/new/main.js", DynamicRegisterMode.RELOADABLE);

        var entry = bookkeeping.entry("mymod:ruby");
        assertEquals("nekojs:server/packs/new/main.js", entry.ownerScriptId());
        assertSame(DynamicRegisterMode.RELOADABLE, entry.mode());
        assertFalse(entry.stale());
    }

    @Test
    void blankOwnerIsRecordedAsUnknown() {
        DynamicRegistrationBookkeeping bookkeeping = new DynamicRegistrationBookkeeping("item");

        bookkeeping.claim("mymod:x", null, DynamicRegisterMode.WORLD);

        assertEquals("<unknown>", bookkeeping.entry("mymod:x").ownerScriptId());
    }

    @Test
    void claimValidatesIdAndMode() {
        DynamicRegistrationBookkeeping bookkeeping = new DynamicRegistrationBookkeeping("item");

        assertThrows(IllegalArgumentException.class, () -> bookkeeping.claim("  ", "owner", DynamicRegisterMode.WORLD));
        assertThrows(IllegalArgumentException.class, () -> bookkeeping.claim(null, "owner", DynamicRegisterMode.WORLD));
        assertThrows(NullPointerException.class, () -> bookkeeping.claim("mymod:x", "owner", null));
    }

    @Test
    void removeDropsClaimEntirely() {
        DynamicRegistrationBookkeeping bookkeeping = new DynamicRegistrationBookkeeping("item");
        bookkeeping.claim("mymod:ruby", "owner", DynamicRegisterMode.WORLD);

        bookkeeping.remove("mymod:ruby");

        assertEquals(0, bookkeeping.count());
        assertFalse(bookkeeping.isTracked("mymod:ruby"));
        assertNull(bookkeeping.entry("mymod:ruby"));
    }

    @Test
    void idsByModeFiltersIncludingStaleEntries() {
        DynamicRegistrationBookkeeping bookkeeping = new DynamicRegistrationBookkeeping("item");
        bookkeeping.claim("mymod:a", "owner", DynamicRegisterMode.WORLD);
        bookkeeping.claim("mymod:b", "owner", DynamicRegisterMode.RELOADABLE);
        bookkeeping.beginReload();

        assertEquals(List.of("mymod:a"), bookkeeping.idsByMode(DynamicRegisterMode.WORLD));
        assertEquals(List.of("mymod:b"), bookkeeping.idsByMode(DynamicRegisterMode.RELOADABLE));
    }

    @Test
    void trackedIdsPreserveClaimOrder() {
        DynamicRegistrationBookkeeping bookkeeping = new DynamicRegistrationBookkeeping("item");
        bookkeeping.claim("mymod:c", "owner", DynamicRegisterMode.WORLD);
        bookkeeping.claim("mymod:a", "owner", DynamicRegisterMode.WORLD);
        bookkeeping.claim("mymod:b", "owner", DynamicRegisterMode.WORLD);

        assertEquals(List.of("mymod:c", "mymod:a", "mymod:b"), List.copyOf(bookkeeping.trackedIds()));
    }

    @Test
    void modeParsingAcceptsKnownModesCaseInsensitive() {
        assertSame(DynamicRegisterMode.WORLD, DynamicRegisterMode.parse("world"));
        assertSame(DynamicRegisterMode.WORLD, DynamicRegisterMode.parse(" WORLD "));
        assertSame(DynamicRegisterMode.RELOADABLE, DynamicRegisterMode.parse("Reloadable"));
        // absent mode resolves to WORLD (script default)
        assertSame(DynamicRegisterMode.WORLD, DynamicRegisterMode.parse("world"));
    }

    @Test
    void modeParsingRejectsGlobalWithActionableMessage() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> DynamicRegisterMode.parse("global"));

        assertTrue(error.getMessage().contains("RegistryEvents"), "must point at the STARTUP alternative");
    }

    @Test
    void modeParsingRejectsUnknownAndMissing() {
        IllegalArgumentException unknown = assertThrows(
                IllegalArgumentException.class, () -> DynamicRegisterMode.parse("forever"));
        assertTrue(unknown.getMessage().contains("forever"));

        assertThrows(IllegalArgumentException.class, () -> DynamicRegisterMode.parse(null));
        assertThrows(IllegalArgumentException.class, () -> DynamicRegisterMode.parse("   "));
    }
}
