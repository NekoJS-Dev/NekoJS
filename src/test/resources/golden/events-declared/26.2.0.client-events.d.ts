import { $KeyBindEvents$KeyBindEventJS } from "java:com/tkisor/nekojs/bindings/event/client";
import { $PostEffectEventJS } from "java:com/tkisor/nekojs/client/posteffect";
import { $DataGeneratorJS, $LangGeneratorJS } from "java:com/tkisor/nekojs/wrapper";
import { $PainterJS, $ScreenRenderEventJS } from "java:com/tkisor/nekojs/wrapper/client";
import { $RecipeViewerCategoryListJS, $RecipeViewerEntryListJS, $RecipeViewerInformationJS, $RecipeViewerRecipeListJS } from "java:com/tkisor/nekojs/wrapper/viewer";
import { $String } from "java:java/lang";
import { $Item } from "java:net/minecraft/world/item";
import { $ClientPlayerNetworkEvent$Clone, $ClientPlayerNetworkEvent$LoggingIn, $ClientPlayerNetworkEvent$LoggingOut, $ClientTickEvent$Post, $ClientTickEvent$Pre, $EntityRenderersEvent$RegisterRenderers, $InputEvent$InteractionKeyMappingTriggered, $RegisterClientCommandsEvent, $RegisterKeyMappingsEvent, $RegisterMenuScreensEvent, $RegisterParticleProvidersEvent } from "java:net/neoforged/neoforge/client/event";
import { $ItemTooltipEvent } from "java:net/neoforged/neoforge/event/entity/player";
import { $PlayerTickEvent$Post, $PlayerTickEvent$Pre } from "java:net/neoforged/neoforge/event/tick";

declare module "@side-only/client/events" {
}

export {};

declare global {
    namespace ClientEvents {
        function tickPre(handler: ((event: $ClientTickEvent$Pre) => void)): void;
        function tickPost(handler: ((event: $ClientTickEvent$Post) => void)): void;
        function tick(handler: ((event: $ClientTickEvent$Post) => void)): void;
        function playerTickPre(handler: ((event: $PlayerTickEvent$Pre) => void)): void;
        function playerTickPost(handler: ((event: $PlayerTickEvent$Post) => void)): void;
        function interactionKey(handler: ((event: $InputEvent$InteractionKeyMappingTriggered) => void)): void;
        function loggedIn(handler: ((event: $ClientPlayerNetworkEvent$LoggingIn) => void)): void;
        function loggedOut(handler: ((event: $ClientPlayerNetworkEvent$LoggingOut) => void)): void;
        function cloned(handler: ((event: $ClientPlayerNetworkEvent$Clone) => void)): void;
        function commandRegistry(handler: ((event: $RegisterClientCommandsEvent) => void)): void;
        function registerKeyMappings(handler: ((event: $RegisterKeyMappingsEvent) => void)): void;
        function registerMenuScreens(handler: ((event: $RegisterMenuScreensEvent) => void)): void;
        function registerRenderers(handler: ((event: $EntityRenderersEvent$RegisterRenderers) => void)): void;
        function registerEntityRenderers(handler: ((event: $EntityRenderersEvent$RegisterRenderers) => void)): void;
        function registerBlockEntityRenderers(handler: ((event: $EntityRenderersEvent$RegisterRenderers) => void)): void;
        function registerParticleProviders(handler: ((event: $RegisterParticleProvidersEvent) => void)): void;
        function generateAssets(handler: ((event: $DataGeneratorJS) => void)): void;
        function generateAssets(extra: string, handler: ((event: $DataGeneratorJS) => void)): void;
        function lang(handler: ((event: $LangGeneratorJS) => void)): void;
        function lang(extra: string, handler: ((event: $LangGeneratorJS) => void)): void;
        function postEffects(handler: ((event: $PostEffectEventJS) => void)): void;
        function hud(handler: ((event: $PainterJS) => void)): void;
        function screenRender(handler: ((event: $ScreenRenderEventJS) => void)): void;
        function hudRender(handler: ((event: $Object) => void)): void;
        function worldRender(handler: ((event: $Object) => void)): void;
    }

    namespace ItemEvents {
        function tooltip(handler: ((event: $ItemTooltipEvent) => void)): void;
        function tooltip(extra: $Item, handler: ((event: $ItemTooltipEvent) => void)): void;
    }

    namespace KeyBindEvents {
        function pressed(handler: ((event: $KeyBindEvents$KeyBindEventJS) => void)): void;
        function pressed(extra: string, handler: ((event: $KeyBindEvents$KeyBindEventJS) => void)): void;
        function released(handler: ((event: $KeyBindEvents$KeyBindEventJS) => void)): void;
        function released(extra: string, handler: ((event: $KeyBindEvents$KeyBindEventJS) => void)): void;
        function tick(handler: ((event: $KeyBindEvents$KeyBindEventJS) => void)): void;
        function tick(extra: string, handler: ((event: $KeyBindEvents$KeyBindEventJS) => void)): void;
        function register(handler: ((event: $Object) => void)): void;
    }

    namespace RecipeViewerEvents {
        function addEntries(handler: ((event: $RecipeViewerEntryListJS) => void)): void;
        function addEntries(extra: string, handler: ((event: $RecipeViewerEntryListJS) => void)): void;
        function removeEntries(handler: ((event: $RecipeViewerEntryListJS) => void)): void;
        function removeEntries(extra: string, handler: ((event: $RecipeViewerEntryListJS) => void)): void;
        function removeRecipes(handler: ((event: $RecipeViewerRecipeListJS) => void)): void;
        function removeCategories(handler: ((event: $RecipeViewerCategoryListJS) => void)): void;
        function addInformation(handler: ((event: $RecipeViewerInformationJS) => void)): void;
    }

}
