package com.tkisor.nekojs.api.event;

import org.jetbrains.annotations.ApiStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public interface EventGroupRegistry {
    void register(EventGroup group);

    Map<String, EventGroup> view();

    @ApiStatus.Internal
    final class Impl implements EventGroupRegistry {
        private final Map<String, EventGroup> groups = new LinkedHashMap<>();

        @Override
        public void register(EventGroup group) {
            Objects.requireNonNull(group, "group == null");
            groups.computeIfAbsent(group.name(), EventGroup::of).merge(group);
        }

        @Override
        public Map<String, EventGroup> view() {
            return Collections.unmodifiableMap(groups);
        }
    }
}
