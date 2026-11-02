package com.tkisor.nekojs.authoring;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthoringJsonTest {
    @Test
    void streamedJsonHashMatchesCompactUtf8DocumentIdentity() {
        JsonObject document = new JsonObject();
        document.addProperty("text", "quotes \" slash \\ newline \n html <>&=\u2028\u2029 supplementary \uD83D\uDE3A lone \uD800");
        document.add("numbers", JsonParser.parseString("[0,-0,1.2300,1e100,123456789012345678901234567890]"));
        document.add("null", null);
        JsonArray rows = new JsonArray();
        for (int i = 0; i < 1000; i++) {
            JsonObject row = new JsonObject();
            row.addProperty("id", i);
            row.addProperty("text", "row " + i);
            rows.add(row);
        }
        document.add("resources", rows);
        assertEquals(AuthoringJson.hash(document.toString()), AuthoringJson.hashJson(document));
        document.addProperty("snapshotId", "snapshot:" + AuthoringJson.hashJson(document));
        assertEquals(AuthoringJson.hash(document.toString()), AuthoringJson.hashJson(document));
    }
}
