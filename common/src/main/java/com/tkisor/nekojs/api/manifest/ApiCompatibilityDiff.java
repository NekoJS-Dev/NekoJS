package com.tkisor.nekojs.api.manifest;

import com.tkisor.nekojs.api.surface.ApiSignature;
import com.tkisor.nekojs.api.surface.ApiSurfaceSnapshot;
import com.tkisor.nekojs.api.surface.ApiSymbol;

import java.util.*;

public final class ApiCompatibilityDiff {

    public enum Severity { BREAKING, ADDITIVE, DOCUMENTATION_ONLY }

    public enum ChangeKind {
        REMOVED,
        ADDED,
        SIGNATURE_CHANGED,
        OVERLOAD_ADDED,
        OVERLOAD_REMOVED,
        CAPABILITY_ADDED,
        CAPABILITY_REMOVED,
        VERSION_CHANGED
    }

    public record DiffEntry(
            Severity severity,
            String symbolId,
            ChangeKind changeKind,
            String before,
            String after
    ) {}

    private ApiCompatibilityDiff() {}

    public static List<DiffEntry> diff(ApiSurfaceSnapshot before, ApiSurfaceSnapshot after) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");

        List<DiffEntry> entries = new ArrayList<>();

        Map<String, ApiSymbol> beforeSymbols = indexSymbols(before.symbols());
        Map<String, ApiSymbol> afterSymbols = indexSymbols(after.symbols());

        for (Map.Entry<String, ApiSymbol> entry : beforeSymbols.entrySet()) {
            String id = entry.getKey();
            ApiSymbol beforeSym = entry.getValue();
            ApiSymbol afterSym = afterSymbols.get(id);

            if (afterSym == null) {
                entries.add(new DiffEntry(
                        Severity.BREAKING,
                        id,
                        ChangeKind.REMOVED,
                        serializeSignatures(beforeSym),
                        null));
            } else {
                diffSignatures(id, beforeSym, afterSym, entries);
            }
        }

        for (Map.Entry<String, ApiSymbol> entry : afterSymbols.entrySet()) {
            String id = entry.getKey();
            if (!beforeSymbols.containsKey(id)) {
                entries.add(new DiffEntry(
                        Severity.ADDITIVE,
                        id,
                        ChangeKind.ADDED,
                        null,
                        serializeSignatures(entry.getValue())));
            }
        }

        diffCapabilities(before.activeCapabilityNames(), after.activeCapabilityNames(), entries);

        return entries;
    }

    private static void diffSignatures(
            String symbolId,
            ApiSymbol before,
            ApiSymbol after,
            List<DiffEntry> entries) {

        Set<String> beforeKeys = new LinkedHashSet<>();
        for (ApiSignature sig : before.signatures()) {
            beforeKeys.add(sig.callKey());
        }

        Set<String> afterKeys = new LinkedHashSet<>();
        for (ApiSignature sig : after.signatures()) {
            afterKeys.add(sig.callKey());
        }

        boolean signatureChanged = false;

        if (before.signatures().size() == after.signatures().size()) {
            for (int i = 0; i < before.signatures().size(); i++) {
                ApiSignature beforeSig = before.signatures().get(i);
                ApiSignature afterSig = after.signatures().get(i);
                if (!beforeSig.compatibilityKey().equals(afterSig.compatibilityKey())) {
                    signatureChanged = true;
                }
            }
        }

        for (ApiSignature beforeSig : before.signatures()) {
            boolean found = false;
            for (ApiSignature afterSig : after.signatures()) {
                if (beforeSig.callKey().equals(afterSig.callKey())) {
                    if (!beforeSig.compatibilityKey().equals(afterSig.compatibilityKey())) {
                        signatureChanged = true;
                    }
                    found = true;
                    break;
                }
            }
            if (!found && !signatureChanged) {
                entries.add(new DiffEntry(
                        Severity.BREAKING,
                        symbolId,
                        ChangeKind.OVERLOAD_REMOVED,
                        beforeSig.compatibilityKey(),
                        null));
            }
        }

        for (ApiSignature afterSig : after.signatures()) {
            boolean found = false;
            for (ApiSignature beforeSig : before.signatures()) {
                if (afterSig.callKey().equals(beforeSig.callKey())) {
                    found = true;
                    break;
                }
            }
            if (!found && !signatureChanged) {
                entries.add(new DiffEntry(
                        Severity.ADDITIVE,
                        symbolId,
                        ChangeKind.OVERLOAD_ADDED,
                        null,
                        afterSig.compatibilityKey()));
            }
        }

        if (signatureChanged) {
            entries.add(new DiffEntry(
                    Severity.BREAKING,
                    symbolId,
                    ChangeKind.SIGNATURE_CHANGED,
                    serializeSignatures(before),
                    serializeSignatures(after)));
        }
    }

    private static void diffCapabilities(
            Set<String> before,
            Set<String> after,
            List<DiffEntry> entries) {

        for (String cap : before) {
            if (!after.contains(cap)) {
                entries.add(new DiffEntry(
                        Severity.BREAKING,
                        "capability:" + cap,
                        ChangeKind.CAPABILITY_REMOVED,
                        cap,
                        null));
            }
        }

        for (String cap : after) {
            if (!before.contains(cap)) {
                entries.add(new DiffEntry(
                        Severity.ADDITIVE,
                        "capability:" + cap,
                        ChangeKind.CAPABILITY_ADDED,
                        null,
                        cap));
            }
        }
    }

    private static Map<String, ApiSymbol> indexSymbols(List<ApiSymbol> symbols) {
        Map<String, ApiSymbol> index = new LinkedHashMap<>();
        for (ApiSymbol s : symbols) {
            index.put(s.id().value(), s);
        }
        return index;
    }

    private static String serializeSignatures(ApiSymbol symbol) {
        StringBuilder sb = new StringBuilder();
        sb.append(symbol.id().value()).append("(");
        for (int i = 0; i < symbol.signatures().size(); i++) {
            if (i > 0) sb.append("|");
            sb.append(symbol.signatures().get(i).compatibilityKey());
        }
        sb.append(")");
        return sb.toString();
    }
}
