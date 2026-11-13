package com.tkisor.nekojs.probe.ir;

import java.util.ArrayList;
import java.util.List;

/**
 * Field declaration IR (mutable). Covers regular fields, static constants and enum constants
 * ({@code isEnumConstant}).
 */
public final class FieldDecl {
    /** Distinguishes reflected fields from authored synthetic properties during backend projection. */
    java.lang.reflect.Field sourceField;
    public String name;
    public String renameTo;        // null = use name
    public TypeSlot type;
    public boolean isStatic;
    public boolean isFinal;
    public boolean isEnumConstant;
    public boolean hidden;
    public final List<String> docs = new ArrayList<>();

    public FieldDecl(String name, TypeSlot type) {
        this.name = name;
        this.type = type;
    }

    /** The name used when rendering (renameTo takes precedence). */
    public String effectiveName() {
        return renameTo != null ? renameTo : name;
    }
}
