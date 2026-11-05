//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import graal.graalvm.polyglot.Value;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.*;

class NativeControlDesignScaleTest {
    @ParameterizedTest
    @ValueSource(doubles = {0.3333333333333333, 0.5, 1, 2})
    void controlsPaintAndEditInTheSameDesignCoordinates(double scale) throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            fixture.guiScale(1);
            fixture.installRuntime();
            int width = (int) Math.round(480 * scale);
            int height = (int) Math.round(270 * scale);
            fixture.adapter.resize(width, height);
            fixture.context.getBindings("js").putMember("viewWidth", width);
            fixture.context.getBindings("js").putMember("viewHeight", height);
            fixture.context.eval("js", """
                    globalThis.value=UI.createSignal('abcdefghij');
                    globalThis.level=UI.createSignal(0);
                    globalThis.clicks=0;
                    globalThis.root=UI.createRoot(()=>UI.element('column',{
                      coordinateSpace:'design',width:'fill',height:'fill',gap:8,children:[
                        UI.element('button',{id:'action',coordinateSpace:'design',width:120,height:24,
                          text:'Resize',onClick:()=>clicks++}),
                        UI.element('input',{id:'field',coordinateSpace:'design',width:160,height:24,
                          value:value.get(),onChange:event=>value.set(event.value)}),
                        UI.element('slider',{id:'level',coordinateSpace:'design',width:160,height:20,
                          min:0,max:100,value:level.get(),onChange:event=>level.set(Number(event.value))}),
                        UI.element('label',{id:'tail',coordinateSpace:'design',text:'Tail'})
                      ]}),host,{viewport:{width:viewWidth,height:viewHeight,designWidth:480,designHeight:270}});
                    host.bindRoot(root);
                    globalThis.rect=id=>{
                      function find(nodes){for(const node of nodes){
                        if(node.id===id)return node.rect;
                        const found=find(node.children);if(found)return found;
                      }}
                      return find(root.layout().nodes);
                    };
                    """);
            var draws = fixture.transformedText();
            var button = draws.stream().filter(draw -> draw.text().equals("Resize")).findFirst().orElseThrow();
            assertEquals(36 * scale, button.width(), 0.0001);
            assertEquals(9 * scale, button.height(), 0.0001);
            assertEquals(24 * scale, draws.stream().filter(draw -> draw.text().equals("Tail"))
                    .findFirst().orElseThrow().width(), 0.0001, "control paint must restore the caller's pose");
            assertTrue(fixture.adapter.dispatchAt(60 * scale, 12 * scale, "click", 0, 0));
            fixture.adapter.dispatchRelease(60 * scale, 12 * scale, 0);
            assertEquals(1, fixture.context.eval("js", "clicks").asInt());

            Value field = fixture.context.getBindings("js").getMember("rect").execute("field");
            double fieldX = Math.floor(field.getMember("x").asDouble());
            double fieldY = Math.floor(field.getMember("y").asDouble());
            double clickY = fieldY + 10 * scale;
            assertTrue(fixture.adapter.dispatchAt(fieldX + 16 * scale, clickY, "click", 0, 0));
            fixture.adapter.dispatchRelease(fieldX + 16 * scale, clickY, 0);
            assertTrue(fixture.adapter.textInput('X'));
            assertEquals("abXcdefghij", fixture.context.eval("js", "value.get()").asString());
            fixture.adapter.dispatchAt(fieldX + 16 * scale, clickY, "click", 0, 0);
            fixture.adapter.dispatchDrag(fieldX + 34 * scale, clickY, 0);
            fixture.adapter.dispatchRelease(fieldX + 34 * scale, clickY, 0);
            fixture.adapter.textInput('Y');
            assertEquals("abYefghij", fixture.context.eval("js", "value.get()").asString());

            fixture.context.eval("js", "value.set('abcdefghij'.repeat(10))");
            fixture.adapter.key(GLFW.GLFW_KEY_END, 0);
            var scrolled = fixture.transformedText().stream().filter(draw -> draw.text().length() == 100)
                    .findFirst().orElseThrow();
            assertTrue(scrolled.x() < fieldX, "the caret must scroll within the scaled input viewport");
            fixture.adapter.dispatchAt(fieldX + 4 * scale, clickY, "click", 0, 0);
            fixture.adapter.dispatchRelease(fieldX + 4 * scale, clickY, 0);
            fixture.adapter.textInput('Z');
            assertTrue(fixture.context.eval("js", "value.get().indexOf('Z')").asInt() > 50,
                    "mouse positioning must include the design-space horizontal scroll");
            fixture.adapter.key(GLFW.GLFW_KEY_HOME, 0);
            var home = fixture.transformedText().stream().filter(draw -> draw.text().length() == 101)
                    .findFirst().orElseThrow();
            assertEquals(fieldX + 4 * scale, home.x(), 0.0001);

            Value slider = fixture.context.getBindings("js").getMember("rect").execute("level");
            double sliderX = Math.floor(slider.getMember("x").asDouble());
            double sliderY = Math.floor(slider.getMember("y").asDouble());
            fixture.adapter.dispatchAt(sliderX + 80 * scale, sliderY + 10 * scale, "click", 0, 0);
            fixture.adapter.dispatchRelease(sliderX + 80 * scale, sliderY + 10 * scale, 0);
            assertEquals(50, fixture.context.eval("js", "level.get()").asDouble(), 1,
                    "slider painting and hit mapping must share the scale");
        }
    }
}
//?}
