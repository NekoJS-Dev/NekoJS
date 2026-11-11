package com.tkisor.nekojs.api.data;

/** Selects exactly one module category for a binding registration. */
public final class BindingExposure {
    public enum Category {
        STABLE("stable"), VERSION("version");

        private final String path;

        Category(String path) {
            this.path = path;
        }

        public String path() {
            return path;
        }
    }

    private Category category;
    private boolean frozen;

    public BindingExposure stable() {
        return select(Category.STABLE);
    }

    public BindingExposure versioned() {
        return select(Category.VERSION);
    }

    private BindingExposure select(Category target) {
        if (frozen || category != null) {
            throw new IllegalArgumentException("[NEKO-4057] API exposure must select exactly one target once");
        }
        category = target;
        return this;
    }

    public Category freeze() {
        frozen = true;
        if (category == null) {
            throw new IllegalArgumentException("[NEKO-4057] API exposure requires stable() or versioned()");
        }
        return category;
    }
}
