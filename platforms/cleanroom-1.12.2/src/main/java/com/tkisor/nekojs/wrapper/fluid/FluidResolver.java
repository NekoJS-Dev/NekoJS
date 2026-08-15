package com.tkisor.nekojs.wrapper.fluid;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.data.ValueConversionException;
import com.tkisor.nekojs.wrapper.FluidAmounts;
import graal.graalvm.polyglot.Value;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 1.12.2 FluidResolver — resolves fluid identifiers to FluidStacks and fluid ingredient lists.
 *
 * <p>Adapted from neoforge-26.1 FluidResolver. Key differences:
 * <ul>
 *   <li>Uses {@link FluidRegistry} instead of BuiltInRegistries/HolderLookup</li>
 *   <li>Returns {@code List<FluidStack>} instead of {@code FluidIngredient} (1.12.2 has no FluidIngredient class)</li>
 *   <li>Fluid tags ({@code #tag}) return empty — 1.12.2 has no fluid tag system</li>
 *   <li>Uses {@code new FluidStack(fluid, amount)} for construction</li>
 * </ul>
 */
public final class FluidResolver {
    private FluidResolver() {}

    // ===================== FluidStack =====================

    /** Resolve a fluid id string to a FluidStack (1000mb default). */
    public static FluidStack fromString(String raw) {
        return fromString(raw, FluidAmounts.BUCKET);
    }

    /** Resolve a fluid id string to a FluidStack with the given amount. */
    public static FluidStack fromString(String raw, int amount) {
        if (raw == null || raw.isBlank()) {
            throw new ValueConversionException(FluidStack.class, "fluid id", raw, "fluid id cannot be empty");
        }
        String s = raw.trim();
        char c = s.charAt(0);
        if (c == '*' || c == '@' || c == '/' || c == '#') {
            throw new ValueConversionException(FluidStack.class,
                    "fluid id (no tag/mod/regex/wildcard prefix)", s,
                    "FluidStack cannot be created from '" + c + "' syntax; use FluidIngredientJS instead");
        }
        ParsedFluidInput input = parseFluidInput(s, false);
        return stackFromFluid(getFluid(input.id()), input.amount());
    }

    public static FluidStack stackFromFluid(Fluid fluid) {
        return stackFromFluid(fluid, FluidAmounts.BUCKET);
    }

    public static FluidStack stackFromFluid(Fluid fluid, int amount) {
        if (fluid == null || amount <= 0) return null;
        return new FluidStack(fluid, amount);
    }

    public static FluidStack stackFromValue(Value value) {
        if (value == null || value.isNull()) return null;
        if (value.isString()) return fromString(value.asString());
        if (value.isHostObject()) {
            Object obj = value.asHostObject();
            if (obj instanceof FluidStack stack) return stack.copy();
            if (obj instanceof Fluid fluid) return stackFromFluid(fluid);
            if (obj instanceof NekoId id) return fromString(id.toString());
        }
        if (value.hasMembers()) {
            int amount = memberAmount(value, FluidAmounts.BUCKET);
            if (value.hasMember("fluid")) return stackFromFluid(getFluid(value.getMember("fluid").asString()), amount);
            if (value.hasMember("id")) return stackFromFluid(getFluid(value.getMember("id").asString()), amount);
            if (value.hasMember("tag")) {
                throw new ValueConversionException(FluidStack.class, "fluid stack (no 'tag')", value,
                        "FluidStack cannot be created from a tag");
            }
        }
        throw new ValueConversionException(FluidStack.class, "fluid / fluid id / fluid object", value,
                "unsupported fluid stack value");
    }

    // ===================== Fluid Ingredient (List<FluidStack>) =====================

    public static List<FluidStack> ingredientFromString(String raw) {
        String s = normalizeRaw(raw);
        char c = s.charAt(0);
        if (c == '*') return allFluids();
        if (c == '@') return namespaceFluids(s.substring(1));
        if (c == '/') {
            String body = (s.length() > 2 && s.charAt(s.length() - 1) == '/')
                    ? s.substring(1, s.length() - 1) : s.substring(1);
            return regexFluids(body);
        }
        ParsedFluidInput input = parseFluidInput(s, true);
        if (input.tag()) {
            // 1.12.2: no fluid tags, return empty
            return Collections.emptyList();
        }
        Fluid fluid = getFluid(input.id());
        return Collections.singletonList(new FluidStack(fluid, input.amount()));
    }

    public static List<FluidStack> ingredientFromFluid(Fluid fluid) {
        if (fluid == null) {
            throw new ValueConversionException(List.class, "non-null fluid", fluid,
                    "fluid is null");
        }
        return Collections.singletonList(new FluidStack(fluid, FluidAmounts.BUCKET));
    }

    public static List<FluidStack> ingredientFromStack(FluidStack stack) {
        if (stack == null || stack.amount <= 0) {
            throw new ValueConversionException(List.class, "non-empty fluid stack", stack,
                    "empty fluid ingredients are not supported");
        }
        return Collections.singletonList(stack.copy());
    }

    public static List<FluidStack> ingredientFromValue(Value value) {
        if (value == null || value.isNull()) {
            throw new ValueConversionException(List.class, "non-null", value,
                    "empty fluid ingredients are not supported");
        }
        if (value.isString()) return ingredientFromString(value.asString());
        if (value.isHostObject()) {
            Object obj = value.asHostObject();
            if (obj instanceof FluidIngredientJS wrapper) return wrapper.getFluids();
            if (obj instanceof FluidStack stack) return ingredientFromStack(stack);
            if (obj instanceof Fluid fluid) return ingredientFromFluid(fluid);
            if (obj instanceof NekoId id) return ingredientFromString(id.toString());
            if (obj instanceof List) {
                @SuppressWarnings("unchecked")
                List<FluidStack> list = (List<FluidStack>) obj;
                return new ArrayList<>(list);
            }
        }
        if (value.hasArrayElements()) return combineFluids(toIngredientList(value));
        if (value.hasMembers()) return fromFluidObject(value);
        throw new ValueConversionException(List.class,
                "fluid / fluid id / fluid ingredient object / array", value, "unsupported fluid ingredient value");
    }

    private static List<FluidStack> fromFluidObject(Value value) {
        if (value.hasMember("filter")) return filterFluids(value.getMember("filter"));
        if (value.hasMember("any")) return combineFluids(toIngredientList(value.getMember("any")));
        if (value.hasMember("all")) return intersectFluidIngredients(toIngredientList(value.getMember("all")));
        if (value.hasMember("not")) {
            return exceptFluidIngredients(allFluids(), ingredientFromValue(value.getMember("not")));
        }
        if (value.hasMember("wildcard") && value.getMember("wildcard").asBoolean()) return allFluids();
        if (value.hasMember("mod")) return namespaceFluids(value.getMember("mod").asString());
        if (value.hasMember("regex")) return regexFluids(value.getMember("regex").asString());
        if (value.hasMember("fluid")) return ingredientFromString(value.getMember("fluid").asString());
        if (value.hasMember("id")) return ingredientFromString(value.getMember("id").asString());
        if (value.hasMember("tag")) {
            String tag = value.getMember("tag").asString();
            return ingredientFromString(tag.startsWith("#") ? tag : "#" + tag);
        }
        throw new ValueConversionException(List.class,
                "recognized field (fluid|id|tag|mod|regex|wildcard|filter|any|all|not)", value,
                "no recognized field in fluid ingredient object");
    }

    // ===================== Combine / Intersect / Except =====================

    /** Combine multiple ingredient lists into one (union). */
    @SafeVarargs
    public static List<FluidStack> combine(List<FluidStack>... alternatives) {
        Set<Fluid> seen = new LinkedHashSet<>();
        List<FluidStack> result = new ArrayList<>();
        for (List<FluidStack> list : alternatives) {
            if (list == null) continue;
            for (FluidStack fs : list) {
                if (fs != null && fs.amount > 0 && seen.add(fs.getFluid())) {
                    result.add(fs);
                }
            }
        }
        if (result.isEmpty()) {
            // Snapshot the generic varargs array into a reifiable list before passing it
            // anywhere, so the array itself can never be polluted or escape.
            List<List<FluidStack>> snapshot = new ArrayList<>(alternatives.length);
            for (List<FluidStack> alternative : alternatives) {
                snapshot.add(alternative);
            }
            throw new ValueConversionException(List.class, "non-empty list", snapshot,
                    "no non-empty alternatives to combine");
        }
        return result;
    }

    // ===================== Wildcard / Namespace / Regex =====================

    private static List<FluidStack> allFluids() {
        List<FluidStack> result = new ArrayList<>();
        for (Fluid f : FluidRegistry.getRegisteredFluids().values()) {
            result.add(new FluidStack(f, FluidAmounts.BUCKET));
        }
        return result;
    }

    private static List<FluidStack> namespaceFluids(String namespace) {
        List<FluidStack> result = new ArrayList<>();
        String prefix = namespace + ":";
        for (Fluid f : FluidRegistry.getRegisteredFluids().values()) {
            if (f.getName() != null && f.getName().startsWith(prefix)) {
                result.add(new FluidStack(f, FluidAmounts.BUCKET));
            }
        }
        if (result.isEmpty()) {
            throw new ValueConversionException(List.class, "fluid namespace", "@" + namespace,
                    "no fluids found in namespace: " + namespace);
        }
        return result;
    }

    private static List<FluidStack> regexFluids(String regex) {
        Pattern pattern = Pattern.compile(regex);
        List<FluidStack> result = new ArrayList<>();
        for (Fluid f : FluidRegistry.getRegisteredFluids().values()) {
            if (f.getName() != null && pattern.matcher(f.getName()).find()) {
                result.add(new FluidStack(f, FluidAmounts.BUCKET));
            }
        }
        if (result.isEmpty()) {
            throw new ValueConversionException(List.class, "matching fluid regex", "/" + regex + "/",
                    "no fluids match regex: " + regex);
        }
        return result;
    }

    // ===================== Filter =====================

    private static List<FluidStack> filterFluids(Value fn) {
        if (!fn.canExecute()) {
            throw new ValueConversionException(List.class,
                    "{ filter: (fluidId)=>boolean }", fn, "'filter' must be a function");
        }
        List<FluidStack> result = new ArrayList<>();
        for (Fluid f : FluidRegistry.getRegisteredFluids().values()) {
            Value v = fn.execute(new FluidStack(f, FluidAmounts.BUCKET));
            if (v != null && v.isBoolean() && v.asBoolean()) {
                result.add(new FluidStack(f, FluidAmounts.BUCKET));
            }
        }
        if (result.isEmpty()) {
            throw new ValueConversionException(List.class, "non-empty filter result", fn,
                    "filter matched no fluids");
        }
        return result;
    }

    // ===================== Combine / Intersect / Except helpers =====================

    private static List<FluidStack> combineFluids(List<List<FluidStack>> ings) {
        Set<Fluid> seen = new LinkedHashSet<>();
        List<FluidStack> result = new ArrayList<>();
        for (List<FluidStack> list : ings) {
            for (FluidStack fs : list) {
                if (fs != null && fs.amount > 0 && seen.add(fs.getFluid())) {
                    result.add(fs.copy());
                }
            }
        }
        if (result.isEmpty()) {
            throw new ValueConversionException(List.class, "non-empty fluid array", ings,
                    "combined fluid ingredient is empty");
        }
        return result;
    }

    private static List<FluidStack> intersectFluidIngredients(List<List<FluidStack>> ings) {
        if (ings.isEmpty()) {
            throw new ValueConversionException(List.class, "non-empty 'all' array", ings,
                    "'all' array is empty");
        }
        Set<Fluid> result = null;
        for (List<FluidStack> list : ings) {
            Set<Fluid> cur = new HashSet<>();
            for (FluidStack fs : list) {
                if (fs != null && fs.amount > 0) cur.add(fs.getFluid());
            }
            if (result == null) result = cur;
            else result.retainAll(cur);
            if (result.isEmpty()) break;
        }
        if (result == null || result.isEmpty()) {
            throw new ValueConversionException(List.class, "non-empty fluid intersection", ings,
                    "fluid intersection is empty");
        }
        List<FluidStack> out = new ArrayList<>();
        for (Fluid f : result) {
            out.add(new FluidStack(f, FluidAmounts.BUCKET));
        }
        return out;
    }

    private static List<FluidStack> exceptFluidIngredients(List<FluidStack> base, List<FluidStack> sub) {
        Set<Fluid> subSet = new HashSet<>();
        for (FluidStack fs : sub) {
            if (fs != null && fs.amount > 0) subSet.add(fs.getFluid());
        }
        List<FluidStack> result = new ArrayList<>();
        for (FluidStack fs : base) {
            if (fs != null && fs.amount > 0 && !subSet.contains(fs.getFluid())) {
                result.add(fs.copy());
            }
        }
        if (result.isEmpty()) {
            throw new ValueConversionException(List.class, "non-empty 'not' result", base + " - " + sub,
                    "difference is empty");
        }
        return result;
    }

    private static List<List<FluidStack>> toIngredientList(Value arr) {
        if (!arr.hasArrayElements()) {
            throw new ValueConversionException(List.class, "fluid ingredient array", arr, "expected array");
        }
        List<List<FluidStack>> list = new ArrayList<>();
        for (long i = 0; i < arr.getArraySize(); i++) {
            list.add(ingredientFromValue(arr.getArrayElement(i)));
        }
        return list;
    }

    // ===================== Fluid lookup =====================

    public static Fluid getFluid(String raw) {
        String id = normalizeFluidId(raw);
        Fluid fluid = FluidRegistry.getFluid(id);
        if (fluid == null) {
            throw new ValueConversionException(FluidStack.class, "registered fluid id", id,
                    "Fluid not found: " + id);
        }
        return fluid;
    }

    public static String normalizeFluidId(String raw) {
        String id = normalizeRaw(raw);
        if (id.startsWith("#")) {
            throw new ValueConversionException(FluidStack.class, "fluid id (no '#' prefix)", raw,
                    "expected fluid id but got tag id");
        }
        return id.contains(":") ? id : "minecraft:" + id;
    }

    public static String normalizeFluidTagId(String raw) {
        String id = normalizeRaw(raw);
        String tag = id.startsWith("#") ? id.substring(1) : id;
        return tag.contains(":") ? tag : "minecraft:" + tag;
    }

    // ===================== Parsing =====================

    private static ParsedFluidInput parseFluidInput(String raw, boolean allowTag) {
        String value = normalizeRaw(raw);
        int amount = FluidAmounts.BUCKET;
        int xIndex = value.indexOf('x');
        if (xIndex > 0 && value.length() > xIndex + 1 && Character.isWhitespace(value.charAt(xIndex + 1))) {
            amount = parseAmount(value.substring(0, xIndex).trim());
            value = value.substring(xIndex + 1).trim();
        }
        boolean tag = value.startsWith("#");
        if (tag && !allowTag) {
            throw new ValueConversionException(FluidStack.class, "fluid id (no '#' tag prefix)", raw,
                    "FluidStack cannot be created from a tag");
        }
        String id = tag ? normalizeFluidTagId(value) : normalizeFluidId(value);
        return new ParsedFluidInput(id.replaceFirst("^#", ""), amount, tag);
    }

    private static int parseAmount(String raw) {
        String value = raw.trim().toLowerCase();
        int divisor = 1;
        int slashIndex = value.indexOf('/');
        if (slashIndex >= 0) {
            divisor = Integer.parseInt(value.substring(slashIndex + 1));
            value = value.substring(0, slashIndex);
        }
        int multiplier = 1;
        if (value.endsWith("b")) {
            multiplier = FluidAmounts.BUCKET;
            value = value.substring(0, value.length() - 1);
        }
        int amount = (int) Math.floor(Double.parseDouble(value) * multiplier / divisor);
        if (amount < 1) {
            throw new ValueConversionException(FluidStack.class, "positive integer amount", raw,
                    "fluid amount must be positive");
        }
        return amount;
    }

    private static int memberAmount(Value value, int fallback) {
        if (!value.hasMember("amount")) return fallback;
        Value amount = value.getMember("amount");
        if (!amount.isNumber() || !amount.fitsInInt()) {
            throw new ValueConversionException(FluidStack.class, "integer amount", amount,
                    "fluid amount must be an integer");
        }
        int parsed = amount.asInt();
        if (parsed <= 0) {
            throw new ValueConversionException(FluidStack.class, "positive integer amount", parsed,
                    "fluid amount must be positive: " + parsed);
        }
        return parsed;
    }

    private static String normalizeRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValueConversionException(FluidStack.class, "non-blank string", raw,
                    "fluid id cannot be empty");
        }
        return raw.trim();
    }

    private record ParsedFluidInput(String id, int amount, boolean tag) {}
}
