package com.tkisor.nekojs.client.gui.dashboard;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DashboardLayoutTest {
    @Test void controlsStayInsideEverySupportedScaledViewport() {
        for (int[] size : List.of(new int[]{320,180}, new int[]{427,240}, new int[]{427,250}, new int[]{640,360}, new int[]{854,480})) {
            for (boolean collapsed : new boolean[]{false,true}) {
                var l = DashboardLayout.calculate(size[0],size[1],collapsed,10);
                for (var r : List.of(l.header(),l.sidebar(),l.toggle(),l.list(),l.detailHeader(),l.copyPath(),l.copyDetails(),l.openSource(),l.details(),l.footer())) {
                    assertTrue(r.width() >= 0 && r.height() >= 0);
                    assertTrue(r.x() >= l.panel().x() && r.y() >= l.panel().y(), r.toString());
                assertEquals(0, l.panel().x());
                assertEquals(0, l.panel().y());
                assertEquals(size[0], l.panel().width());
                assertEquals(size[1], l.panel().height());
                    assertTrue(r.right() <= l.panel().right() && r.bottom() <= l.panel().bottom(), r.toString());
                }
                assertTrue(l.copyPath().width() >= 50);
                if (l.copyPath().y() == l.openSource().y()) {
                    assertTrue(l.copyPath().right() < l.copyDetails().x(), l.toString());
                    assertTrue(l.copyDetails().right() < l.openSource().x(), l.toString());
                } else {
                    assertTrue(l.copyPath().right() < l.copyDetails().x(), l.toString());
                    assertTrue(l.openSource().y() >= l.copyPath().bottom(), l.toString());
                }
                assertTrue(l.openSource().bottom() <= l.details().y());
                assertTrue(l.details().bottom() <= l.footer().y());
                assertTrue(l.list().bottom() <= l.footer().y());
                assertTrue(l.details().height() >= 80);
            }
        }
    }
    @Test void resizingNeverCreatesNegativeOrOverlappingActionRectangles() {
        for (int w=320;w<=800;w+=13) for(int h=180;h<=500;h+=17) {
            var l=DashboardLayout.calculate(w,h,false,10);
            assertTrue(l.openSource().right()<=l.detailHeader().right());
            assertTrue(l.copyDetails().x()>l.copyPath().right());
            if (l.copyPath().y() != l.openSource().y()) {
                assertTrue(l.openSource().y()>=l.copyPath().bottom());
            }
            assertTrue(l.details().height()>0);
        }
    }
    @Test void cardHeightUsesGuiFontLinesNotHtmlPixels() {
        assertEquals(78,DashboardLayout.calculate(320,180,false,10).cardHeight());
        assertEquals(84,DashboardLayout.calculate(640,360,false,12).cardHeight());
    }
    @Test void compactProfileUsesWebLikeProportions() {
        var l = DashboardLayout.calculate(427, 250, false, 10);
        assertTrue(l.compact());
        assertEquals(210, l.sidebar().width());
        assertEquals(58, l.clear().width());
        assertEquals(24, l.search().height());
        assertEquals(78, l.cardHeight());
        assertEquals(30, l.footer().height());
        assertEquals(l.copyPath().y(), l.copyDetails().y());
        assertTrue(l.copyPath().bottom() <= l.openSource().y());
        assertTrue(l.details().height() >= 80);
    }

    @Test void scrollOffsetSurvivesTemporaryReflow() {
        var scroll=new DashboardScroll();scroll.to(350);
        assertEquals(50,scroll.offset(150,100));
        assertEquals(350,scroll.offset(1000,100));
        scroll.move(-40,1000,100);assertEquals(310,scroll.offset(1000,100));
        scroll.move(10000,1000,100);assertEquals(900,scroll.offset(1000,100));
        scroll.to(-20);assertEquals(0,scroll.offset(1000,100));
    }
}




