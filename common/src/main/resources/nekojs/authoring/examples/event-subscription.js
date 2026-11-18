import { ServerEvents } from "nekojs:event/versioned";

// Place in server_scripts. Observe the recipe event without changing recipes.
ServerEvents.recipes(event => {
    console.info("NekoJS recipe authoring example loaded");
});
