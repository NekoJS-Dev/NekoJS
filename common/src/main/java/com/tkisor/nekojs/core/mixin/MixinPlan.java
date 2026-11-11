package com.tkisor.nekojs.core.mixin;

import java.util.List;
import java.util.Set;

/** Immutable, versioned carrier ABI. Class names use the selected runtime namespace. */
public record MixinPlan(int abi, Profile profile, String hash, List<Hook> hooks) {
    public static final int ABI = 1;
    public record Profile(String minecraft, String loader, String loaderVersion, String namespace, String side) {
        public Profile {
            if (minecraft == null || minecraft.isBlank() || loaderVersion == null || loaderVersion.isBlank()
                    || !Set.of("neoforge", "fabric").contains(loader)
                    || !"runtime".equals(namespace) || !Set.of("client", "server").contains(side)) {
                throw invalid("An exact runtime-namespace loader profile and physical side are required");
            }
        }
    }
    public record Hook(String id, String target, String method, String descriptor, String at,
                       boolean cancellable, String scriptType, String thread, int require, boolean staticMethod) {
        public Hook {
            if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")
                    || target == null || !target.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")
                    || method == null || !method.matches("[A-Za-z_$][A-Za-z0-9_$]*")
                    || !Set.of("HEAD", "RETURN").contains(at) || require != 1
                    || !Set.of("SERVER", "CLIENT").contains(scriptType)
                    || !(scriptType + "_MAIN").equals(thread)) throw invalid("Invalid hook declaration: " + id);
            Descriptor.parse(descriptor);
        }
        public String selector() { return method + descriptor; }
    }
    public MixinPlan {
        if (abi != ABI || profile == null || hash == null || !hash.matches("[0-9a-f]{64}")) throw invalid("Invalid carrier ABI or hash");
        hooks = List.copyOf(hooks);
        Set<String> ids = new java.util.HashSet<>();
        for (Hook hook : hooks) {
            if (hook == null || !ids.add(hook.id())) throw invalid("Duplicate or null hook ID");
            if (profile.side().equals("server") && hook.scriptType().equals("CLIENT")) throw invalid("Client hook in a dedicated-server carrier");
        }
    }
    public static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("[NEKO-4090] " + message);
    }

    /** Strict JVM descriptor parser. Long values are withheld until BigInt ABI validation exists. */
    public record Descriptor(List<String> arguments, String result) {
        public static Descriptor parse(String input) {
            if (input == null || !input.startsWith("(")) throw invalid("Expected an exact method descriptor");
            int[] cursor = {1};
            var args = new java.util.ArrayList<String>();
            while (cursor[0] < input.length() && input.charAt(cursor[0]) != ')') args.add(type(input, cursor, false));
            if (cursor[0] >= input.length() || input.charAt(cursor[0]++) != ')') throw invalid("Unclosed method descriptor");
            String result = type(input, cursor, true);
            if (cursor[0] != input.length()) throw invalid("Trailing descriptor content");
            return new Descriptor(List.copyOf(args), result);
        }
        private static String type(String input, int[] cursor, boolean allowVoid) {
            int start = cursor[0];
            if (start >= input.length()) throw invalid("Incomplete descriptor");
            char kind = input.charAt(cursor[0]++);
            if ("ZBCSIFD".indexOf(kind) >= 0 || (kind == 'V' && allowVoid)) return input.substring(start, cursor[0]);
            if (kind == '[') { type(input, cursor, false); return input.substring(start, cursor[0]); }
            if (kind == 'L') {
                int end = input.indexOf(';', cursor[0]);
                if (end < 0 || !input.substring(cursor[0], end).matches("[A-Za-z0-9_$/]+")) throw invalid("Invalid reference descriptor");
                cursor[0] = end + 1;
                return input.substring(start, cursor[0]);
            }
            throw invalid("Unsupported descriptor type: " + kind);
        }
    }
}
