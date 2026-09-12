package com.tkisor.nekojs.client.gui.dashboard;

import com.tkisor.nekojs.core.error.ErrorDashboardModel;
import com.tkisor.nekojs.network.ErrorSummaryDTO;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class DashboardViewTest {
    private record Draw(String text,int x,int y) {}
    private record Fill(DashboardLayout.Rect rect,int color) {}
    private static final class Capture implements DashboardView.Canvas {
        final List<Draw> draws=new ArrayList<>();
        final List<Fill> fills=new ArrayList<>();
        @Override public void fill(DashboardLayout.Rect r,int color) { assertTrue(r.width()>=0&&r.height()>=0); fills.add(new Fill(r,color)); }
        @Override public void text(String s,int x,int y,int color) { draws.add(new Draw(s,x,y)); }
        @Override public void clip(DashboardLayout.Rect r) {}
        @Override public void unclip() {}
        boolean contains(String token) { return draws.stream().anyMatch(d->d.text().contains(token)); }
    }
    private ErrorSummaryDTO error(int i,String raw) { return new ErrorSummaryDTO("id"+i,"a"+i+".js",12,3,"ordinary error",raw); }
    private DashboardView view(ErrorDashboardModel model,int width,int height) {
        var v=new DashboardView(model,(key,args)->key);v.refresh();
        v.configure(width,height,10,s->s.codePointCount(0,s.length())*6);return v;
    }
    private Capture paint(DashboardView v) { var c=new Capture();v.paint(c,-1,-1);return c; }

    @Test void collapseResizeAndRefreshRetainSearchSelectionAndFolds() {
        var model=new ErrorDashboardModel();String raw="RAW_SENTINEL\n    at STACK_SENTINEL";
        model.update(List.of(error(0,raw),error(1,"other")));
        var v=view(model,640,360);assertTrue(paint(v).contains("RAW_SENTINEL"));
        v.toggleFold(DashboardView.Action.RAW);assertFalse(paint(v).contains("RAW_SENTINEL"));
        model.setSearch("a0.js");v.refresh();v.toggleSidebar();v.configure(427,240,10,s->s.length()*6);
        assertTrue(v.collapsed());assertEquals("a0.js",model.search());assertEquals("id0",model.selectedError().id());
        v.toggleSidebar();model.update(List.of(error(0,raw)));v.refresh();
        assertFalse(paint(v).contains("RAW_SENTINEL"));assertEquals(raw,model.selectedError().fullDetails());
        v.toggleFold(DashboardView.Action.RAW);assertTrue(paint(v).contains("RAW_SENTINEL"));
    }
    @Test void hiddenOrClippedCardsAndDisabledOpenNeverActivate() {
        var model=new ErrorDashboardModel();model.update(List.of(error(0,"raw"),error(1,"raw"),error(2,"raw")));
        var v=view(model,320,180);model.select("id1");v.refresh();var list=v.layout().list();
        v.click(list.x()+4,list.y()-1);assertEquals("id1",model.selectedError().id());
        v.click(list.x()+4,list.bottom()+1);assertEquals("id1",model.selectedError().id());
        v.location(false,"remote reason");var open=v.layout().openSource();
        assertEquals(DashboardView.Action.NONE,v.click(open.x()+2,open.y()+2));
        assertFalse(v.enabled(DashboardView.Action.OPEN_SOURCE));
        v.toggleSidebar();v.click(list.x()+30,list.y()+20);assertEquals("id1",model.selectedError().id());
    }
    @Test void wheelRoutesToOnlyTheHoveredViewport() {
        var model=new ErrorDashboardModel();List<ErrorSummaryDTO> errors=new ArrayList<>();
        for(int i=0;i<20;i++)errors.add(error(i,"raw ".repeat(1000)));
        model.update(errors);var v=view(model,427,240);var d=v.layout().details();
        var before=paint(v).draws.stream().filter(x->x.x()<v.layout().sidebar().right()).toList();
        v.scroll(d.x()+5,d.y()+5,120);
        var after=paint(v).draws.stream().filter(x->x.x()<v.layout().sidebar().right()).toList();
        assertEquals(before,after);
        var list=v.layout().list();v.scroll(list.x()+5,list.y()+5,200);
        var scrolled=paint(v).draws.stream().filter(x->x.x()<v.layout().sidebar().right()).toList();
        assertNotEquals(before,scrolled);
    }
    @Test void modalAndCollapsedFocusDoNotReachHiddenControls() {
        var model=new ErrorDashboardModel();model.update(List.of(error(0,"raw")));var v=view(model,320,180);
        v.dialog("failed","message");var toggle=v.layout().toggle();v.click(toggle.x()+2,toggle.y()+2);
        assertFalse(v.collapsed());assertTrue(v.modal());v.dismissDialog();
        v.toggleSidebar();
        for(int i=0;i<25;i++) { v.tab(false);assertNotEquals(DashboardView.Action.SEARCH,v.focus());assertNotEquals(DashboardView.Action.CLEAR,v.focus());assertNotEquals(DashboardView.Action.LIST,v.focus()); }
        v.focusSearch();assertFalse(v.collapsed());assertEquals(DashboardView.Action.SEARCH,v.focus());
    }
    @Test void emptyAndNoMatchHaveNoOldDetailsOrEnabledFileActions() {
        var model=new ErrorDashboardModel();model.update(List.of(error(0,"old body")));var v=view(model,640,360);
        model.setSearch("not-present");v.refresh();assertTrue(paint(v).contains("no_match"));assertFalse(paint(v).contains("old body"));
        assertFalse(v.enabled(DashboardView.Action.COPY_DETAILS));
        model.update(List.of());v.refresh();assertTrue(paint(v).contains("empty"));
    }
    @Test void aFrameNeverRewrapsMaximumLengthDetailsAndOnlyDrawsVisibleRows() {
        String raw="§cx".repeat(87381)+"x";assertEquals(262144,raw.length());
        var model=new ErrorDashboardModel();model.update(List.of(error(0,raw)));var v=view(model,320,180);
        AtomicLong measured=new AtomicLong();v.configure(320,180,10,s->{measured.addAndGet(s.length());return s.length()*6;});
        measured.set(0);Capture c=paint(v);
        assertTrue(c.draws.size()<100,"Only visible rows: "+c.draws.size());
        assertTrue(measured.get()<20000,"No full-text measurement on render: "+measured);
        assertTrue(c.draws.stream().noneMatch(d->d.text().contains("§")));
        assertEquals(raw,model.selectedError().fullDetails());
        measured.set(0);v.location(false,"remote reason");v.toggleFold(DashboardView.Action.RAW);
        assertTrue(measured.get()<20000,"Location feedback/folding must reuse text layout: "+measured);
    }

    @Test void filterFallbackRevealsFirstWithoutResettingOrdinaryUpdates() {
        var model=new ErrorDashboardModel();List<ErrorSummaryDTO> errors=new ArrayList<>();
        for(int i=0;i<20;i++)errors.add(new ErrorSummaryDTO("id"+i,
                (i<10?"keep_":"drop_")+i+".js",12,3,"ordinary error","raw"));
        model.update(errors);var v=view(model,320,180);var list=v.layout().list();
        model.select("id19");v.refresh();v.scroll(list.x()+5,list.y()+5,10000);
        assertTrue(paint(v).draws.stream().anyMatch(d->d.text().equals("drop_19.js")&&list.contains(d.x(),d.y())));
        model.setSearch("keep_");v.refresh();
        assertEquals("id0",model.selectedError().id());
        assertTrue(paint(v).draws.stream().anyMatch(d->d.text().equals("keep_0.js")&&list.contains(d.x(),d.y())),
                "Automatic first selection must be visible after filtering a scrolled list");
        v.scroll(list.x()+5,list.y()+5,90);
        var before=paint(v).draws.stream().filter(d->list.contains(d.x(),d.y())).toList();
        model.update(errors);v.refresh();
        var after=paint(v).draws.stream().filter(d->list.contains(d.x(),d.y())).toList();
        assertEquals(before,after,"A normal update with a retained selection must preserve scrolling");
    }

    @Test void grabbingEitherScrollbarThumbDoesNotJumpAndDraggingCanReachTheEnd() {
        var model=new ErrorDashboardModel();List<ErrorSummaryDTO> errors=new ArrayList<>();
        for(int i=0;i<20;i++)errors.add(error(i,"detail row\n".repeat(100)));
        model.update(errors);var v=view(model,427,240);
        for(var action:List.of(DashboardView.Action.LIST,DashboardView.Action.DETAILS)) {
            var area=v.rect(action);v.scroll(area.x()+5,area.y()+5,150);
            var before=paint(v);
            var thumb=before.fills.stream().filter(f->f.color()==DashboardView.BLUE
                    &&f.rect().width()==3&&f.rect().x()==area.right()-3).findFirst().orElseThrow().rect();
            double x=thumb.x()+1,y=thumb.y()+thumb.height()/2.0;
            v.click(x,y);
            assertEquals(before.draws,paint(v).draws,action+" must preserve the thumb grab offset");
            v.drag(x,y);
            assertEquals(before.draws,paint(v).draws,action+" must not move without pointer movement");
            v.drag(x,area.bottom()+100);v.release();
            var end=paint(v).fills.stream().filter(f->f.color()==DashboardView.BLUE
                    &&f.rect().width()==3&&f.rect().x()==area.right()-3).findFirst().orElseThrow().rect();
            assertEquals(area.bottom(),end.bottom(),action+" must reach the end");
        }
    }

    @Test void refreshMovesFocusOffFoldHeadersThatNoLongerExist() {
        var model=new ErrorDashboardModel();model.update(List.of(error(0,"body\n    at trace")));
        var v=view(model,427,240);v.focus(DashboardView.Action.STACK);
        model.update(List.of(error(0,"new frozen body without a stack")));v.refresh();
        assertEquals(DashboardView.Action.DETAILS,v.focus());
        v.focus(DashboardView.Action.RAW);model.setSearch("absent");v.refresh();
        assertEquals(DashboardView.Action.DETAILS,v.focus());
        assertFalse(v.enabled(DashboardView.Action.RAW));
        assertFalse(v.enabled(DashboardView.Action.STACK));
    }

    @Test void tabSkipsDisabledActionsRatherThanLeavingInvisibleOrDeadFocus() {
        var model=new ErrorDashboardModel();model.update(List.of(error(0,"raw")));
        var v=view(model,427,240);v.focusSearch();v.tab(false);
        assertEquals(DashboardView.Action.LIST,v.focus(),"An empty search cannot be cleared");
        v.focus(DashboardView.Action.COPY_DETAILS);v.location(false,"remote");v.tab(false);
        assertEquals(DashboardView.Action.DETAILS,v.focus(),"Unavailable source opening is not a tab stop");
        v.tab(true);assertEquals(DashboardView.Action.COPY_DETAILS,v.focus());
        v.focus(DashboardView.Action.NONE);v.tab(true);
        assertEquals(DashboardView.Action.RAW,v.focus(),"Reverse tab from no focus starts at the final control");
    }

    @Test void emptySnapshotStatusIsNotConfusedWithAnEmptySearchProjection() {
        var model=new ErrorDashboardModel();var v=view(model,320,180);var footer=v.layout().footer();
        assertEquals("readonly_empty",v.tooltip(footer.x()+1,footer.y()+1));
        model.update(List.of(error(0,"frozen")));model.setSearch("absent");v.refresh();
        assertEquals("readonly",v.tooltip(footer.x()+1,footer.y()+1));
        v.status("copied_full");assertEquals("copied_full",v.tooltip(footer.x()+1,footer.y()+1));
    }

    @Test void detailScrollAndFocusSurviveReflowAndEqualSnapshotRefresh() {
        var model=new ErrorDashboardModel();String raw="RAW_SENTINEL\n".repeat(100);
        model.update(List.of(error(0,raw)));var v=view(model,320,180);var area=v.layout().details();
        v.focus(DashboardView.Action.DETAILS);v.scroll(area.x()+5,area.y()+5,175);
        var before=paint(v).draws.stream().filter(d->area.contains(d.x(),d.y())).toList();
        v.toggleSidebar();v.configure(854,480,10,s->s.codePointCount(0,s.length())*6);
        v.toggleSidebar();v.configure(320,180,10,s->s.codePointCount(0,s.length())*6);
        model.update(List.of(error(0,raw)));v.refresh();
        assertEquals(before,paint(v).draws.stream().filter(d->area.contains(d.x(),d.y())).toList());
        assertEquals(DashboardView.Action.DETAILS,v.focus());
        assertEquals(raw,model.selectedError().fullDetails());
    }
}
