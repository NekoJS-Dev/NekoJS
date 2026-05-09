package com.tkisor.nekojs.wrapper.fluid;

import com.tkisor.nekojs.api.data.NekoId;
import graal.graalvm.polyglot.Value;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.ArrayList;
import java.util.List;

public final class FluidResolver {
    private FluidResolver() {}

    public static FluidStack stackFromString(String raw) {
        if (isEmptyStackString(raw)) {
            return FluidStack.EMPTY;
        }
        ParsedFluidInput input = parseFluidInput(raw, false);
        return stackFromFluid(getFluid(input.id()), input.amount());
    }

    public static FluidStack stackFromFluid(Fluid fluid) {
        return stackFromFluid(fluid, FluidAmounts.BUCKET);
    }

    public static FluidStack stackFromFluid(Fluid fluid, int amount) {
        if (fluid == Fluids.EMPTY || amount <= 0) return FluidStack.EMPTY;
        return new FluidStack(fluid, amount);
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
            if (value.hasMember("tag")) throw new IllegalArgumentException("FluidStack cannot be created from a tag");
        }
        throw new IllegalArgumentException("Unsupported fluid stack value: " + value);
    }

    public static FluidIngredient ingredientFromString(String raw) {
        ParsedFluidInput input = parseFluidInput(raw, true);
        if (input.tag()) {
            ResourceLocation id = ResourceLocation.parse(input.id());
            TagKey<Fluid> tagKey = TagKey.create(Registries.FLUID, id);
            var tag = BuiltInRegistries.FLUID.getTag(tagKey);
            if (tag.isEmpty()) throw new IllegalArgumentException("Fluid tag not found: #" + input.id());
            return FluidIngredient.of(tag.get().stream().map(Holder::value).toArray(Fluid[]::new));
        }
        return ingredientFromFluid(getFluid(input.id()));
    }

    public static FluidIngredient ingredientFromFluid(Fluid fluid) {
        if (fluid == Fluids.EMPTY) throw new IllegalArgumentException("Empty fluid ingredients are not supported");
        return FluidIngredient.of(fluid);
    }

    public static FluidIngredient ingredientFromStack(FluidStack stack) {
        if (stack.isEmpty()) throw new IllegalArgumentException("Empty fluid ingredients are not supported");
        return FluidIngredient.of(stack);
    }

    public static FluidIngredient ingredientFromValue(Value value) {
        if (value == null || value.isNull()) throw new IllegalArgumentException("Empty fluid ingredients are not supported");
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
        if (value.hasArrayElements()) {
            List<FluidIngredient> ingredients = new ArrayList<>();
            for (long i = 0; i < value.getArraySize(); i++) ingredients.add(ingredientFromValue(value.getArrayElement(i)));
            return combine(ingredients);
        }
        if (value.hasMembers()) {
            if (value.hasMember("fluid")) return ingredientFromString(value.getMember("fluid").asString());
            if (value.hasMember("id")) return ingredientFromString(value.getMember("id").asString());
            if (value.hasMember("tag")) {
                String tag = value.getMember("tag").asString();
                return ingredientFromString(tag.startsWith("#") ? tag : "#" + tag);
            }
        }
        throw new IllegalArgumentException("Unsupported fluid ingredient value: " + value);
    }

    public static FluidIngredient combine(List<FluidIngredient> alternatives) {
        if (alternatives.isEmpty()) throw new IllegalArgumentException("FluidIngredient cannot be empty");
        if (alternatives.size() == 1) return alternatives.getFirst();
        List<Fluid> fluids = new ArrayList<>();
        for (FluidIngredient ingredient : alternatives) {
            for (FluidStack stack : ingredient.getStacks()) {
                if (!stack.isEmpty()) fluids.add(stack.getFluid());
            }
        }
        if (fluids.isEmpty()) throw new IllegalArgumentException("FluidIngredient cannot be empty");
        return FluidIngredient.of(fluids.toArray(Fluid[]::new));
    }

    public static SizedFluidIngredient sizedFromString(String raw) {
        ParsedFluidInput input = parseFluidInput(raw, true);
        return sizedFromIngredient(ingredientFromString((input.tag() ? "#" : "") + input.id()), input.amount());
    }

    public static SizedFluidIngredient sizedFromValue(Value value) {
        if (value == null || value.isNull()) throw new IllegalArgumentException("Empty sized fluid ingredients are not supported");
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
            if (value.hasMember("fluid")) return sizedFromIngredient(ingredientFromString(value.getMember("fluid").asString()), amount);
            if (value.hasMember("id")) return sizedFromIngredient(ingredientFromString(value.getMember("id").asString()), amount);
            if (value.hasMember("tag")) {
                String tag = value.getMember("tag").asString();
                return sizedFromIngredient(ingredientFromString(tag.startsWith("#") ? tag : "#" + tag), amount);
            }
        }
        throw new IllegalArgumentException("Unsupported sized fluid ingredient value: " + value);
    }

    public static SizedFluidIngredient sizedFromIngredient(FluidIngredient ingredient, int amount) {
        if (amount <= 0) throw new IllegalArgumentException("Fluid amount must be positive: " + amount);
        return new SizedFluidIngredient(ingredient, amount);
    }

    public static Fluid getFluid(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(normalizeFluidId(raw));
        if (id == null) throw new IllegalArgumentException("Invalid fluid id: " + raw);
        return BuiltInRegistries.FLUID.getOptional(id).orElseThrow(() -> new IllegalArgumentException("Fluid not found: " + id));
    }

    public static String normalizeFluidId(String raw) {
        String id = normalizeRaw(raw);
        if (id.startsWith("#")) throw new IllegalArgumentException("Expected fluid id but got tag id: " + raw);
        rejectUnsupported(id);
        return id.contains(":") ? id : "minecraft:" + id;
    }

    public static String normalizeFluidTagId(String raw) {
        String id = normalizeRaw(raw);
        String tag = id.startsWith("#") ? id.substring(1) : id;
        rejectUnsupported(tag);
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
        if (tag && !allowTag) throw new IllegalArgumentException("FluidStack cannot be created from a tag: " + raw);
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
        if (amount < 1) throw new IllegalArgumentException("Fluid amount must be positive: " + raw);
        return amount;
    }

    private static int memberAmount(Value value, int fallback) {
        if (!value.hasMember("amount")) return fallback;
        Value amount = value.getMember("amount");
        if (!amount.isNumber() || !amount.fitsInInt()) {
            throw new IllegalArgumentException("Fluid amount must be an integer");
        }
        int parsed = amount.asInt();
        if (parsed <= 0) {
            throw new IllegalArgumentException("Fluid amount must be positive: " + parsed);
        }
        return parsed;
    }

    private static boolean isEmptyStackString(String raw) {
        if (raw == null) return true;
        return switch (raw.trim()) {
            case "", "-", "empty", "minecraft:empty" -> true;
            default -> false;
        };
    }

    private static String normalizeRaw(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("Fluid id cannot be empty");
        return raw.trim();
    }

    private static void rejectUnsupported(String value) {
        if (value.equals("*") || value.startsWith("@") || value.startsWith("/") || value.contains("[") || value.contains("{")) {
            throw new IllegalArgumentException("Unsupported fluid syntax: " + value);
        }
    }

    private record ParsedFluidInput(String id, int amount, boolean tag) {}
}
