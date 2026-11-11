package dev.latvian.mods.kubejs.util;

import com.google.gson.JsonElement;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;

public class RegistryOpsContainer extends OpsContainer {
    private final RegistryOps<Tag> nbt;
    private final RegistryOps<JsonElement> json;
    private final RegistryOps<Object> java;
    public RegistryOpsContainer(RegistryOps<Tag> nbt, RegistryOps<JsonElement> json, RegistryOps<Object> java) {
        super(nbt, json, java);
        this.nbt = nbt;
        this.json = json;
        this.java = java;
    }
    @Override public RegistryOps<Tag> nbt() { return nbt; }
    @Override public RegistryOps<JsonElement> json() { return json; }
    @Override public RegistryOps<Object> java() { return java; }
}
