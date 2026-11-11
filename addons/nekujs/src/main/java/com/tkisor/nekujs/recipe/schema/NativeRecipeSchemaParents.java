package com.tkisor.nekujs.recipe.schema;

import com.google.gson.JsonParser;
import java.util.List;

/** Native-supported fallback dependencies; mirror/shrink controls are deliberately not advertised. */
public final class NativeRecipeSchemaParents {
    public static final String SHAPED_FACTORY = "nekujs:native_shaped";
    private NativeRecipeSchemaParents() {}

    /** Returns fresh in-memory documents; owned source documents take precedence over these dependencies. */
    public static List<SchemaDocument> documents() {
        return List.of(document("minecraft:shaped", """
                {"factory":"nekujs:native_shaped","keys":[
                  {"name":"result","role":"output","type":"item_stack"},
                  {"name":"pattern","type":{"type":"list","component":"string"}},
                  {"name":"key","role":"input","type":{"type":"pattern","component":"optional_ingredient"}},
                  {"name":"category","role":"other","type":"crafting_book_category","optional":"misc","excluded":true},
                  {"name":"show_notification","role":"other","type":"boolean","optional":true,"excluded":true}
                ],"unique":["result"],"constructors":[{"keys":["result","pattern","key"]}],"functions":{
                  "noNotification":{"type":"set","key":"show_notification","value":false},
                  "buildingCategory":{"type":"set","key":"category","value":"building"},
                  "redstoneCategory":{"type":"set","key":"category","value":"redstone"},
                  "equipmentCategory":{"type":"set","key":"category","value":"equipment"}
                }}
                """), document("minecraft:special", """
                {"hidden":true,"keys":[
                  {"name":"category","role":"other","type":"crafting_book_category","optional":"misc"}
                ],"constructors":[{"keys":[]}]}
                """));
    }

    private static SchemaDocument document(String id, String json) {
        return new SchemaDocument("nekujs:native_parents", id, "native/recipe_schema/" + id.substring(id.indexOf(':') + 1) + ".json",
                JsonParser.parseString(json).getAsJsonObject());
    }
}
