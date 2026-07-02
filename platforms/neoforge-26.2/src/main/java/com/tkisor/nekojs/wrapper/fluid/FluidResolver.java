package com.tkisor.nekojs.wrapper.fluid;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.data.ValueConversionException;
import com.tkisor.nekojs.holder.NamespaceHolderSet;
import com.tkisor.nekojs.holder.PredicateHolderSet;
import com.tkisor.nekojs.holder.RegexHolderSet;
import graal.graalvm.polyglot.Value;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.registries.holdersets.AnyHolderSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class FluidResolver {
    private FluidResolver() {}

    /** 26.x 的 DefaultedRegistry 没有 asLookup()；从 vanilla RegistryAccess 取 lookup。 */
    private static final HolderLookup.RegistryLookup<Fluid> FLUID_LOOKUP =
        RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).lookupOrThrow(Registries.FLUID);

    // ===================== FluidStack（具体 stack，无 tag/通配/命名空间/正则）=====================

    public static FluidStack stackFromString(String raw) {
        String s = normalizeRaw(raw);
        char c = s.charAt(0);
        if (c == '*' || c == '@' || c == '/' || c == '#') {
            throw new ValueConversionException(FluidStack.class,
                "fluid id (no tag/mod/regex/wildcard prefix)", s,
                "FluidStack cannot be created from '" + c + "' syntax; use FluidIngredient instead");
        }
        ParsedFluidInput input = parseFluidInput(s, false);
        return stackFromFluid(getFluid(input.id()), input.amount());
    }

    public static FluidStack stackFromFluid(Fluid fluid) {
        return stackFromFluid(fluid, FluidAmounts.BUCKET);
    }

    public static FluidStack stackFromFluid(Fluid fluid, int amount) {
        if (fluid == Fluids.EMPTY || amount <= 0) return FluidStack.EMPTY;
        return new FluidStack(fluid.builtInRegistryHolder(), amount);
    }

    public static FluidStack stackFromValue(Value value) {
        if (value == null || value.isNull()) return FluidStack.EMPTY;
        if (value.isString()) return stackFromString(value.asString());
        if (value.isHostObject()) {
            Object obj = value.asHostObject();
            if (obj instanceof FluidStack stack) return stack.copy();
            if (obj instanceof Fluid fluid) return stackFromFluid(fluid);
            if (obj instanceof NekoId id) return stackFromString(id.toString());
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

    // ===================== FluidIngredient =====================

    public static FluidIngredient ingredientFromString(String raw) {
        String s = normalizeRaw(raw);
        char c = s.charAt(0);
        if (c == '*') return allFluids();
        if (c == '@') return ingredientOfHolders(new NamespaceHolderSet<>(FLUID_LOOKUP, s.substring(1)));
        if (c == '/') {
            String body = (s.length() > 2 && s.charAt(s.length() - 1) == '/')
                ? s.substring(1, s.length() - 1) : s.substring(1);
            return ingredientOfHolders(new RegexHolderSet<>(FLUID_LOOKUP, Pattern.compile(body)));
        }
        ParsedFluidInput input = parseFluidInput(s, true);
        if (input.tag()) {
            Identifier id = Identifier.parse(input.id());
            TagKey<Fluid> tagKey = TagKey.create(Registries.FLUID, id);
            var tag = BuiltInRegistries.FLUID.get(tagKey);
            if (tag.isEmpty()) {
                throw new ValueConversionException(FluidIngredient.class, "existing fluid tag", "#" + input.id(),
                    "fluid tag not found: #" + input.id());
            }
            return FluidIngredient.of(tag.get());
        }
        return ingredientFromFluid(getFluid(input.id()));
    }

    public static FluidIngredient ingredientFromFluid(Fluid fluid) {
        if (fluid == Fluids.EMPTY) {
            throw new ValueConversionException(FluidIngredient.class, "non-empty fluid", fluid,
                "empty fluid ingredients are not supported");
        }
        return FluidIngredient.of(fluid);
    }

    public static FluidIngredient ingredientFromStack(FluidStack stack) {
        if (stack.isEmpty()) {
            throw new ValueConversionException(FluidIngredient.class, "non-empty fluid stack", stack,
                "empty fluid ingredients are not supported");
        }
        return FluidIngredient.of(stack);
    }

    public static FluidIngredient ingredientFromValue(Value value) {
        if (value == null || value.isNull()) {
            throw new ValueConversionException(FluidIngredient.class, "non-null", value,
                "empty fluid ingredients are not supported");
        }
        if (value.isString()) return ingredientFromString(value.asString());
        if (value.isHostObject()) {
            Object obj = value.asHostObject();
            if (obj instanceof FluidIngredientJS wrapper) return wrapper.unwrap();
            if (obj instanceof FluidIngredient ingredient) return ingredient;
            if (obj instanceof SizedFluidIngredient sized) return sized.ingredient();
            if (obj instanceof FluidStack stack) return ingredientFromStack(stack);
            if (obj instanceof Fluid fluid) return ingredientFromFluid(fluid);
            if (obj instanceof NekoId id) return ingredientFromString(id.toString());
        }
        if (value.hasArrayElements()) return combineFluids(toIngredientList(value));
        if (value.hasMembers()) return fromFluidObject(value);
        throw new ValueConversionException(FluidIngredient.class,
            "fluid / fluid id / fluid ingredient object / array", value, "unsupported fluid ingredient value");
    }

    private static FluidIngredient fromFluidObject(Value value) {
        if (value.hasMember("filter")) return filterFluids(value.getMember("filter"));
        if (value.hasMember("any")) return combineFluids(toIngredientList(value.getMember("any")));
        if (value.hasMember("all")) return intersectFluidIngredients(toIngredientList(value.getMember("all")));
        if (value.hasMember("not")) {
            return exceptFluidIngredients(allFluids(), ingredientFromValue(value.getMember("not")));
        }
        if (value.hasMember("wildcard") && value.getMember("wildcard").asBoolean()) return allFluids();
        if (value.hasMember("mod")) return ingredientOfHolders(new NamespaceHolderSet<>(
            FLUID_LOOKUP, value.getMember("mod").asString()));
        if (value.hasMember("regex")) return ingredientOfHolders(new RegexHolderSet<>(
            FLUID_LOOKUP, Pattern.compile(value.getMember("regex").asString())));
        if (value.hasMember("fluid")) return ingredientFromString(value.getMember("fluid").asString());
        if (value.hasMember("id")) return ingredientFromString(value.getMember("id").asString());
        if (value.hasMember("tag")) {
            String tag = value.getMember("tag").asString();
            return ingredientFromString(tag.startsWith("#") ? tag : "#" + tag);
        }
        throw new ValueConversionException(FluidIngredient.class,
            "recognized field (fluid|id|tag|mod|regex|wildcard|filter|any|all|not)", value,
            "no recognized field in fluid ingredient object");
    }

    // ===================== SizedFluidIngredient =====================

    public static SizedFluidIngredient sizedFromString(String raw) {
        ParsedFluidInput input = parseFluidInput(raw, true);
        FluidIngredient ingredient;
        if (input.tag()) {
            Identifier id = Identifier.parse(input.id());
            TagKey<Fluid> tagKey = TagKey.create(Registries.FLUID, id);
            var tag = BuiltInRegistries.FLUID.get(tagKey);
            if (tag.isEmpty()) {
                throw new ValueConversionException(FluidIngredient.class, "existing fluid tag", "#" + input.id(),
                    "fluid tag not found: #" + input.id());
            }
            ingredient = FluidIngredient.of(tag.get());
        } else {
            ingredient = ingredientFromFluid(getFluid(input.id()));
        }
        return new SizedFluidIngredient(ingredient, input.amount());
    }

    public static SizedFluidIngredient sizedFromValue(Value value) {
        if (value == null || value.isNull()) {
            throw new ValueConversionException(SizedFluidIngredient.class, "non-null", value,
                "empty sized fluid ingredients are not supported");
        }
        if (value.isString()) return sizedFromString(value.asString());
        if (value.isHostObject()) {
            Object obj = value.asHostObject();
            if (obj instanceof SizedFluidIngredient sized) return sized;
            if (obj instanceof FluidIngredient ingredient) return sizedFromIngredient(ingredient, FluidAmounts.BUCKET);
            if (obj instanceof FluidIngredientJS wrapper) return sizedFromIngredient(wrapper.unwrap(), FluidAmounts.BUCKET);
            if (obj instanceof FluidStack stack) return SizedFluidIngredient.of(stack.getFluid(), stack.getAmount());
            if (obj instanceof Fluid fluid) return SizedFluidIngredient.of(fluid, FluidAmounts.BUCKET);
            if (obj instanceof NekoId id) return sizedFromString(id.toString());
        }
        if (value.hasMembers()) {
            int amount = memberAmount(value, FluidAmounts.BUCKET);
            FluidIngredient base;
            if (value.hasMember("ingredient")) {
                base = ingredientFromValue(value.getMember("ingredient"));
            } else if (value.hasMember("fluid") || value.hasMember("id") || value.hasMember("tag")
                || value.hasMember("mod") || value.hasMember("regex") || value.hasMember("wildcard")
                || value.hasMember("filter") || value.hasMember("any") || value.hasMember("all")
                || value.hasMember("not")) {
                base = ingredientFromValue(value);
            } else {
                throw new ValueConversionException(SizedFluidIngredient.class,
                    "sized fluid ingredient object (ingredient or fluid+amount)", value,
                    "missing ingredient or fluid field");
            }
            return sizedFromIngredient(base, amount);
        }
        throw new ValueConversionException(SizedFluidIngredient.class,
            "fluid / sized fluid ingredient object", value, "unsupported sized fluid ingredient value");
    }

    public static SizedFluidIngredient sizedFromIngredient(FluidIngredient ingredient, int amount) {
        if (amount <= 0) {
            throw new ValueConversionException(SizedFluidIngredient.class, "positive integer amount", amount,
                "fluid amount must be positive: " + amount);
        }
        return new SizedFluidIngredient(ingredient, amount);
    }

    // ===================== 旧 combine（FluidIngredientJS 仍在用，保持兼容）=====================

    public static FluidIngredient combine(List<FluidIngredient> alternatives) {
        List<FluidIngredient> present = alternatives.stream().filter(i -> i != null).toList();
        if (present.isEmpty()) {
            throw new ValueConversionException(FluidIngredient.class, "non-empty list", alternatives,
                "no non-empty alternatives to combine");
        }
        if (present.size() == 1) return present.get(0);
        Set<Fluid> set = new LinkedHashSet<>();
        for (FluidIngredient fi : present) {
            for (FluidStack s : matchingStacks(fi)) {
                if (s != null && !s.isEmpty()) set.add(s.getFluid());
            }
        }
        if (set.isEmpty()) {
            throw new ValueConversionException(FluidIngredient.class, "non-empty combined", present,
                "combined fluid ingredient is empty");
        }
        return FluidIngredient.of(set.toArray(Fluid[]::new));
    }

    // ===================== 内部 helper =====================

    private static FluidIngredient ingredientOfHolders(HolderSet<Fluid> holders) {
        Fluid[] fluids = holders.stream().map(Holder::value).toArray(Fluid[]::new);
        return FluidIngredient.of(fluids);
    }

    private static FluidIngredient allFluids() {
        return ingredientOfHolders(new AnyHolderSet<>(FLUID_LOOKUP));
    }

    private static FluidIngredient filterFluids(Value fn) {
        if (!fn.canExecute()) {
            throw new ValueConversionException(FluidIngredient.class,
                "{ filter: (fluid)=>boolean }", fn, "'filter' must be a function");
        }
        return ingredientOfHolders(new PredicateHolderSet<>(
            FLUID_LOOKUP, fn,
            holder -> new FluidStack(holder, FluidAmounts.BUCKET)));
    }

    private static FluidIngredient combineFluids(List<FluidIngredient> ings) {
        Set<Fluid> set = new LinkedHashSet<>();
        for (FluidIngredient fi : ings) {
            for (FluidStack s : matchingStacks(fi)) {
                if (s != null && !s.isEmpty()) set.add(s.getFluid());
            }
        }
        if (set.isEmpty()) {
            throw new ValueConversionException(FluidIngredient.class, "non-empty fluid array", ings,
                "combined fluid ingredient is empty");
        }
        return FluidIngredient.of(set.toArray(Fluid[]::new));
    }

    private static FluidIngredient intersectFluidIngredients(List<FluidIngredient> ings) {
        if (ings.isEmpty()) {
            throw new ValueConversionException(FluidIngredient.class, "non-empty 'all' array", ings,
                "'all' array is empty");
        }
        Set<Fluid> result = null;
        for (FluidIngredient fi : ings) {
            Set<Fluid> cur = new HashSet<>();
            for (FluidStack s : matchingStacks(fi)) {
                if (s != null && !s.isEmpty()) cur.add(s.getFluid());
            }
            if (result == null) result = cur;
            else result.retainAll(cur);
            if (result.isEmpty()) break;
        }
        if (result == null || result.isEmpty()) {
            throw new ValueConversionException(FluidIngredient.class, "non-empty fluid intersection", ings,
                "fluid intersection is empty");
        }
        return FluidIngredient.of(result.toArray(Fluid[]::new));
    }

    private static FluidIngredient exceptFluidIngredients(FluidIngredient base, FluidIngredient sub) {
        Set<Fluid> set = new LinkedHashSet<>();
        for (FluidStack s : matchingStacks(base)) {
            if (s != null && !s.isEmpty()) set.add(s.getFluid());
        }
        for (FluidStack s : matchingStacks(sub)) {
            if (s != null && !s.isEmpty()) set.remove(s.getFluid());
        }
        if (set.isEmpty()) {
            throw new ValueConversionException(FluidIngredient.class, "non-empty 'not' result", base + " - " + sub,
                "difference is empty");
        }
        return FluidIngredient.of(set.toArray(Fluid[]::new));
    }

    private static List<FluidIngredient> toIngredientList(Value arr) {
        if (!arr.hasArrayElements()) {
            throw new ValueConversionException(FluidIngredient.class, "fluid ingredient array", arr, "expected array");
        }
        List<FluidIngredient> list = new ArrayList<>();
        for (long i = 0; i < arr.getArraySize(); i++) list.add(ingredientFromValue(arr.getArrayElement(i)));
        return list;
    }

    /**
     * 26.x 的 FluidIngredient 抽象类暴露的匹配栈枚举方法名（{@code getMatchingStacks}）。
     * 若 API 实际命名不同由编译错误驱动调整（保留此处以便定位）。
     */
    private static List<FluidStack> matchingStacks(FluidIngredient fi) {
        try {
            return (List<FluidStack>) FluidIngredient.class.getMethod("getMatchingStacks").invoke(fi);
        } catch (ReflectiveOperationException e) {
            throw new ValueConversionException(FluidIngredient.class, "fluid ingredient", fi,
                "FluidIngredient.getMatchingStacks unavailable: " + e.getMessage());
        }
    }

    public static Fluid getFluid(String raw) {
        Identifier id = Identifier.tryParse(normalizeFluidId(raw));
        if (id == null) {
            throw new ValueConversionException(FluidIngredient.class, "valid fluid id", raw, "invalid fluid id: " + raw);
        }
        Fluid fluid = BuiltInRegistries.FLUID.getValue(id);
        if (fluid == Fluids.EMPTY && !id.getPath().equals("empty")) {
            throw new ValueConversionException(FluidIngredient.class, "registered fluid id", id, "fluid not found: " + id);
        }
        return fluid;
    }

    public static String normalizeFluidId(String raw) {
        String id = normalizeRaw(raw);
        if (id.startsWith("#")) {
            throw new ValueConversionException(FluidIngredient.class, "fluid id (no '#' prefix)", raw,
                "expected fluid id but got tag id");
        }
        return id.contains(":") ? id : "minecraft:" + id;
    }

    public static String normalizeFluidTagId(String raw) {
        String id = normalizeRaw(raw);
        String tag = id.startsWith("#") ? id.substring(1) : id;
        return tag.contains(":") ? tag : "minecraft:" + tag;
    }

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
            throw new ValueConversionException(FluidIngredient.class, "positive integer amount", raw,
                "fluid amount must be positive");
        }
        return amount;
    }

    private static int memberAmount(Value value, int fallback) {
        if (!value.hasMember("amount")) return fallback;
        Value amount = value.getMember("amount");
        if (!amount.isNumber() || !amount.fitsInInt()) {
            throw new ValueConversionException(FluidIngredient.class, "integer amount", amount,
                "fluid amount must be an integer");
        }
        int parsed = amount.asInt();
        if (parsed <= 0) {
            throw new ValueConversionException(FluidIngredient.class, "positive integer amount", parsed,
                "fluid amount must be positive: " + parsed);
        }
        return parsed;
    }

    private static String normalizeRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValueConversionException(FluidIngredient.class, "non-blank string", raw,
                "fluid id cannot be empty");
        }
        return raw.trim();
    }

    private record ParsedFluidInput(String id, int amount, boolean tag) {}
}
