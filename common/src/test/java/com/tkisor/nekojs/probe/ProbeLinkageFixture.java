package com.tkisor.nekojs.probe;

import java.util.List;

/** Loaded in a separate class loader with Missing deliberately unavailable. */
public final class ProbeLinkageFixture {
    private ProbeLinkageFixture() {}

    public static class Missing {}

    public static class Extension {
        public Missing clientModel() {
            return null;
        }
    }

    public static class GenericExtension {
        public List<Missing> clientModels() {
            return List.of();
        }
    }

    public static class GenericHost extends Host {
        public GenericExtension genericExtension() {
            return null;
        }
    }

    public interface ClientCallback {
        Missing clientModel();
    }

    public static class CallbackHost extends Host {
        public ClientCallback callback() {
            return null;
        }
    }

    public static class Host {
        static {
            rejectInitialization();
        }

        private static void rejectInitialization() {
            throw new AssertionError("Probe must not initialize the fixture host");
        }

        public Extension extension() {
            return null;
        }

        public String getStatus() {
            return "available";
        }
    }
}
