//? if neoforge {
package com.tkisor.nekojs.client.posteffect;

import com.tkisor.nekojs.api.annotation.HideFromJS;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** A callback-scoped declaration builder; its detached values become inert candidate data. */
public final class PostEffectBuilder implements ProxyObject {
    private final Map<String, Object> values = new LinkedHashMap<>();
    private boolean sealed;
    private RuntimeException failure;

    PostEffectBuilder() {}

    public String getChainJson() { return (String) read("chainJson"); }
    public void setChainJson(String value) { set("chainJson", value); }

    public String getFragmentShader() { return (String) read("fragmentShader"); }
    public void setFragmentShader(String value) { set("fragmentShader", value); }

    public String getFragmentShaderId() { return (String) read("fragmentShaderId"); }
    public void setFragmentShaderId(String value) { set("fragmentShaderId", value); }

    public Map<String, String> getFragmentShaders() { return (Map<String, String>) read("fragmentShaders"); }
    public void setFragmentShaders(Map<String, String> value) { set("fragmentShaders", value); }

    public Map<String, String> getVertexShaders() { return (Map<String, String>) read("vertexShaders"); }
    public void setVertexShaders(Map<String, String> value) { set("vertexShaders", value); }

    public String getUniformsJson() { return (String) read("uniformsJson"); }
    public void setUniformsJson(String value) { set("uniformsJson", value); }

    public Double getBlurRadius() { return (Double) read("blurRadius"); }
    public void setBlurRadius(Double value) { set("blurRadius", value); }

    public Integer getBlurRounds() { return (Integer) read("blurRounds"); }
    public void setBlurRounds(Integer value) { set("blurRounds", value); }

    /** Unset reads have typed defaults without adding an explicit declaration field. */
    private Object read(String name) {
        if (values.containsKey(name)) return values.get(name);
        return switch (name) {
            case "fragmentShaders", "vertexShaders" -> Map.of();
            case "blurRadius" -> 0.0;
            case "blurRounds" -> 1;
            default -> "";
        };
    }

    private void set(String name, Object value) {
        if (sealed) throw new IllegalStateException("[NEKO-4061] Post-effect builder is no longer active");
        try {
            values.put(name, PostEffectOptions.SCHEMA.validateField(name, value));
        } catch (RuntimeException invalid) {
            failure = invalid;
            throw invalid;
        }
    }

    Map<String, Object> finish() {
        sealed = true;
        if (failure != null) throw new IllegalArgumentException("[NEKO-4061] Post-effect builder contains invalid options", failure);
        return Map.copyOf(values);
    }

    void seal() { sealed = true; }

    @HideFromJS
    @Override
    public Object getMember(String name) {
        if (PostEffectOptions.SCHEMA.hasField(name)) return read(name);
        String field = accessorField(name);
        if (field == null) return null;
        return (ProxyExecutable) args -> {
            boolean setter = name.startsWith("set");
            if (args.length != (setter ? 1 : 0)) {
                var invalid = new IllegalArgumentException("[NEKO-4061] Invalid builder accessor argument count");
                failure = invalid;
                throw invalid;
            }
            if (setter) { set(field, args[0]); return null; }
            return read(field);
        };
    }

    private static String accessorField(String name) {
        if (name.length() <= 3 || !(name.startsWith("get") || name.startsWith("set"))) return null;
        String field = Character.toLowerCase(name.charAt(3)) + name.substring(4);
        return PostEffectOptions.SCHEMA.hasField(field) ? field : null;
    }

    @HideFromJS
    @Override
    public Object getMemberKeys() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String name : PostEffectOptions.SCHEMA.fieldNames()) {
            names.add(name);
            String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
            names.add("get" + suffix);
            names.add("set" + suffix);
        }
        return names.toArray(String[]::new);
    }
    @HideFromJS
    @Override
    public boolean hasMember(String name) { return PostEffectOptions.SCHEMA.hasField(name) || accessorField(name) != null; }
    @HideFromJS
    @Override
    public void putMember(String name, Value value) { set(name, value); }

    @HideFromJS
    @Override
    public boolean removeMember(String name) {
        if (sealed) throw new IllegalStateException("[NEKO-4061] Post-effect builder is no longer active");
        var invalid = new IllegalArgumentException("[NEKO-4061] Post-effect builder fields cannot be deleted: " + name);
        failure = invalid;
        throw invalid;
    }
}
//?}
