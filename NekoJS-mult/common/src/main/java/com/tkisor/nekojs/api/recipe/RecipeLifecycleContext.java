package com.tkisor.nekojs.api.recipe;

import java.util.Set;

public interface RecipeLifecycleContext {
    Set<String> ids();

    int count();

    boolean exists(String id);

    String getJson(String id);

    void setJson(String id, String json);

    void removeById(String id);

    String dump();

    void print();
}
