//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeMultilineTextScaleTest {
    @ParameterizedTest
    @ValueSource(doubles = {3.00234375, 6.0046875, 9})
    void lastLineRemainsVisibleAtTheEndOfFractionallyScaledText(double fontSize) throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            fixture.installRuntime();
            fixture.adapter.resize(200, 100);
            fixture.context.getBindings("js").putMember("requestedSize", fontSize);
            fixture.context.eval("js", """
                    globalThis.root=UI.createRoot(()=>UI.element('scroll',{
                      id:'viewport',width:160,height:50,scrollY:true,children:
                        UI.element('label',{id:'lines',width:'fill',fontSize:requestedSize,
                          text:Array.from({length:160},(_,index)=>'line'+index).join('\\n')})
                    }),host,{viewport:{width:200,height:100}});
                    host.bindRoot(root);
                    """);
            assertTrue(fixture.adapter.dispatchScroll(5, 5, -10000));
            assertTrue(fixture.paint().stream().anyMatch(draw -> draw.text().equals("line159")),
                    () -> "The last line must be reachable at font size " + fontSize);
        }
    }
}
//?}
