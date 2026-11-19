import { $StableClientEvent, $StableClientInputEvent, $StablePlayerEvent } from "java:com/tkisor/nekojs/api/event";
import { $KeyBindEvents$KeyBindEventJS } from "java:com/tkisor/nekojs/bindings/event/client";
import { $PainterJS, $ScreenRenderEventJS } from "java:com/tkisor/nekojs/wrapper/client";
import { $ClientCommandRegistryEventJS, $ClientPlayerNetworkEventJS, $ClientTickEventJS } from "java:com/tkisor/nekojs/wrapper/event/client";
import { $ItemTooltipEventJS } from "java:com/tkisor/nekojs/wrapper/event/item";
import { $RecipeViewerCategoryListJS, $RecipeViewerEntryListJS, $RecipeViewerInformationJS, $RecipeViewerRecipeListJS } from "java:com/tkisor/nekojs/wrapper/viewer";
import { $String } from "java:java/lang";
import { $Item } from "java:net/minecraft/world/item";

declare module "@side-only/client/events" {
}

export {};

declare global {
    namespace ClientEvents {
        function tickPre(handler: ((event: $ClientTickEventJS) => void)): void;
        function tickPost(handler: ((event: $ClientTickEventJS) => void)): void;
        function tick(handler: ((event: $ClientTickEventJS) => void)): void;
        function hud(handler: ((event: $PainterJS) => void)): void;
        function screenRender(handler: ((event: $ScreenRenderEventJS) => void)): void;
        function loggedIn(handler: ((event: $ClientPlayerNetworkEventJS) => void)): void;
        function loggedOut(handler: ((event: $ClientPlayerNetworkEventJS) => void)): void;
        function commandRegistry(handler: ((event: $ClientCommandRegistryEventJS) => void)): void;
        function playerTickPre(handler: ((event: $StablePlayerEvent) => void)): void;
        function playerTickPost(handler: ((event: $StablePlayerEvent) => void)): void;
        function interactionKey(handler: ((event: $StableClientInputEvent) => void)): void;
        function cloned(handler: ((event: $StablePlayerEvent) => void)): void;
        function registerKeyMappings(handler: ((event: $StableClientEvent) => void)): void;
        function registerMenuScreens(handler: ((event: $StableClientEvent) => void)): void;
        function registerRenderers(handler: ((event: $StableClientEvent) => void)): void;
        function registerParticleProviders(handler: ((event: $StableClientEvent) => void)): void;
    }

    namespace ItemEvents {
        function tooltip(handler: ((event: $ItemTooltipEventJS) => void)): void;
        function tooltip(extra: $Item, handler: ((event: $ItemTooltipEventJS) => void)): void;
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
