package com.tkisor.nekojs.client.gui.dashboard;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class DashboardTextTest {
    @Test void stackPartitionIsLosslessIncludingBlankLinesAndCrLf() {
        String raw="Error: example\r\n\tcode\r\n\r\n    at callback (file.js:5)\r\nCaused by: nested\r\n";
        var parts=DashboardText.splitStack(raw);
        assertEquals(raw,parts.body()+parts.stack());
        assertTrue(parts.stack().startsWith("    at "));
        assertEquals(raw,DashboardText.splitStack(raw).body()+DashboardText.splitStack(raw).stack());
        assertEquals("ordinary text",DashboardText.splitStack("ordinary text").body());
        assertEquals("",DashboardText.splitStack("ordinary text").stack());
    }
    @Test void visualTextCannotInterpretSectionFormattingButOriginalIsUnchanged() {
        String original="§cError\t<span>\r\ntext";
        String visual=DashboardText.display(original);
        assertFalse(visual.contains("§"));assertTrue(visual.contains("<span>"));
        assertEquals("§cError\t<span>\r\ntext",original);
    }
    @Test void wrapsDoNotSplitSurrogatePairsOrDiscardWhitespace() {
        String text="甲😀乙  xyz";
        var lines=DashboardText.wrap(text,2,s->s.codePointCount(0,s.length()));
        assertEquals(text,String.join("",lines));
        assertTrue(lines.stream().allMatch(s->s.codePointCount(0,s.length())<=2));
        assertTrue(lines.stream().noneMatch(s->!s.isEmpty()&&Character.isLowSurrogate(s.charAt(0))));
        assertEquals("",DashboardText.fit(text,0,String::length));
    }
    @Test void maximumNetworkTextHasBoundedLocalMeasurementCost() {
        String raw="x".repeat(262144);AtomicLong measured=new AtomicLong();
        var lines=DashboardText.wrap(raw,80,s->{measured.addAndGet(s.length());return s.length();});
        assertEquals(raw,String.join("",lines));
        assertTrue(lines.stream().allMatch(s->s.length()<=80));
        assertTrue(measured.get()<raw.length()*40L,"Avoid quadratic remainder scans: "+measured);
        measured.set(0);
        assertEquals(2,DashboardText.preview(raw,80,2,s->{measured.addAndGet(s.length());return s.length();}).size());
        assertTrue(measured.get()<10000,"Cards must not wrap the whole message: "+measured);
    }
}
