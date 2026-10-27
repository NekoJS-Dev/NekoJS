package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.core.NekoSharedHostAccess;
import graal.graalvm.polyglot.Context;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pins the native coordinate access that the exact TypeScript migration preserves. */
class SectionPosDeclarationProjectionTest {
    @Test
    void retainsNativeBeanReadsDirectCallsAndGetters() {
        try (Context context = Context.newBuilder("js").allowExperimentalOptions(true)
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .option("js.nashorn-compat", "true").option("js.ecmascript-version", "2022").build()) {
            context.getBindings("js").putMember("base", new Vec3i(1, 2, 3));
            context.getBindings("js").putMember("section", SectionPos.of(4, 5, 6));
            assertEquals("1,2,3|4,5,6|4,5,6|4,5,6", context.eval("js", """
                    [ [base.x, base.y, base.z].join(','),
                      [section.x, section.y, section.z].join(','),
                      [section.x(), section.y(), section.z()].join(','),
                      [section.getX(), section.getY(), section.getZ()].join(',') ].join('|')
                    """).asString());
            for (String axis : List.of("x", "y", "z")) {
                var failure = assertThrows(graal.graalvm.polyglot.PolyglotException.class,
                        () -> context.eval("js", "section['" + axis + "()']()"));
                assertTrue(failure.getMessage().contains("not a function"), failure.getMessage());
            }
        }
    }
}
