//? if neoforge {
package com.tkisor.nekojs.client.posteffect;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.client.render.ClientRenderRegistry;
import com.tkisor.nekojs.client.render.HudRenderContextJS;
import com.tkisor.nekojs.client.render.RenderRegistrationBusJS;
import com.tkisor.nekojs.client.render.WorldRenderContextJS;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.PolyglotException;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RenderRegistrationContractTest {
    @Test
    void actualRegistrationKeepsAllIntentionalFormsAndRejectsInvalidOptionsBeforeEffects() {
        ClientRenderRegistry.clearAll();
        try (Context context = Context.newBuilder("js").build()) {
            context.getBindings("js").putMember("hud", RenderRegistrationBusJS.hud());
            context.getBindings("js").putMember("world", RenderRegistrationBusJS.world());
            assertTrue(context.eval("js", "hud('plain', () => {})").asBoolean());
            assertTrue(context.eval("js", "hud('layer', ' FOREGROUND ', () => {})").asBoolean());
            assertTrue(context.eval("js", "hud('priority', -10, () => {})").asBoolean());
            assertTrue(context.eval("js", "world('world', {layer:'late',priority:2}, () => {})").asBoolean());
            assertTrue(ClientRenderRegistry.hasHud(ClientRenderRegistry.HudLayer.FOREGROUND));
            assertTrue(ClientRenderRegistry.hasWorld(ClientRenderRegistry.WorldLayer.LATE));
            for (String invalid : List.of("null", "[]", "()=>{}", "1.1", "2147483648", "NaN",
                    "{priority:'2'}", "{layer:2}", "{typo:true}", "'late'")) {
                assertThrows(PolyglotException.class,
                        () -> context.eval("js", "hud('invalid', " + invalid + ", () => {})"), invalid);
            }
            assertFalse(ClientRenderRegistry.unregisterHud("invalid"));
        } finally {
            ClientRenderRegistry.clearAll();
        }
    }

    @Test
    void registrationMetadataUsesActualCallbackTypesAndSchemaNotObjectPayload() throws Exception {
        var hud = RenderRegistrationBusJS.hud().callSignatures();
        var world = RenderRegistrationBusJS.world().callSignatures();
        assertEquals(2, hud.size());
        assertEquals(1, hud.getFirst().inputs().size());
        assertEquals(2, hud.getLast().inputs().size());
        assertEquals(AdapterInputShape.host(HudRenderContextJS.class), hud.getFirst().callbackParameters().getFirst().shape());
        assertEquals(AdapterInputShape.host(HudRenderContextJS.class.getMethod("graphics").getReturnType()),
                hud.getFirst().callbackParameters().getLast().shape());
        assertEquals(AdapterInputShape.host(WorldRenderContextJS.class), world.getFirst().callbackParameters().getFirst().shape());
        assertEquals(AdapterInputShape.bool(), hud.getFirst().returns());
        var options = (AdapterInputShape.UnionValue) hud.getLast().inputs().getLast().shape();
        var object = (AdapterInputShape.ObjectValue) options.members().getFirst();
        assertEquals(List.of("layer", "priority"), object.slots().stream().map(AdapterInputShape.Slot::name).toList());
        assertTrue(object.slots().stream().noneMatch(AdapterInputShape.Slot::required));
        var layers = (AdapterInputShape.UnionValue) object.slots().getFirst().shape();
        assertEquals(List.of("background", "normal", "foreground"), layers.members().stream()
                .map(shape -> ((AdapterInputShape.LiteralValue) shape).text()).toList());
    }
}
//?}
