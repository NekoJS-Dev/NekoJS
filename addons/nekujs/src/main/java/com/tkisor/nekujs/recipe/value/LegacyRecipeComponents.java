package com.tkisor.nekujs.recipe.value;

import static com.tkisor.nekojs.api.AdapterInputShape.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.tkisor.nekojs.api.AdapterInputShape;
import dev.latvian.mods.rhino.type.TypeInfo;
import com.tkisor.nekojs.api.recipe.RecipeSchemaHost;
import com.tkisor.nekojs.api.recipe.RecipeExecutionFactory;
import java.util.List;
import java.util.Map;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldKind;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldValueCodec;
import com.tkisor.nekujs.recipe.schema.SemanticComponents;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaStorage;
import dev.latvian.mods.kubejs.util.RegistryAccessContainer;
import dev.latvian.mods.kubejs.util.TickDuration;
import graal.graalvm.polyglot.Value;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/**
 * Finite value bindings for the supplied 1.21.1 schema graph, not a KubeJS schema engine.
 * Runtime numeric integers must be JavaScript-safe; exact JSON constants and typed host codecs retain full longs.
 * Structured runtime item/processing-output component-patch fields are not advertised; exact typed hosts retain them.
 * Item strings also retain the existing native component-patch DSL through the native host converter.
 */
public final class LegacyRecipeComponents implements SemanticComponents {
    private static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;
    private final RecipeSchemaStorage storage;
    private final RegistryAccessContainer registries;

    public LegacyRecipeComponents(RecipeSchemaStorage storage, RegistryAccessContainer registries) {
        this.storage = Objects.requireNonNull(storage);
        this.registries = Objects.requireNonNull(registries);
    }

    @Override
    public RecipeFieldValueCodec resolve(JsonElement expression) {
        Binding binding = bind(expression);
        return new RecipeFieldValueCodec(binding.shape(),
                binding.nominalInput() ? LegacyRecipeInputTypes.of(inputType(expression)) : null, binding::encode);
    }

    private TypeInfo inputType(JsonElement expression) {
        if (expression.isJsonObject()) {
            JsonObject object = expression.getAsJsonObject();
            return switch (text(object.get("type"))) {
                case "int" -> TypeInfo.of(Integer.class);
                case "double" -> TypeInfo.of(Double.class);
                case "either" -> inputType(object.get("left")).or(inputType(object.get("right")));
                case "pattern" -> TypeInfo.of(Map.class).withParams(TypeInfo.of(String.class), inputType(object.get("component")));
                case "list" -> {
                    TypeInfo child = inputType(object.get("component"));
                    TypeInfo list = TypeInfo.of(java.util.List.class).withParams(child);
                    yield object.has("can_write_self") && object.get("can_write_self").getAsBoolean() ? list.or(child) : list;
                }
                default -> throw error(5283, "Unsupported dynamic component: " + expression);
            };
        }
        String name = text(expression);
        return switch (name) {
            case "boolean" -> TypeInfo.of(Boolean.class);
            case "string" -> TypeInfo.of(String.class);
            case "crafting_book_category" -> TypeInfo.of(CraftingBookCategory.class);
            case "ticks" -> TickDuration.TYPE_INFO;
            case "positive_long", "non_negative_long" -> TypeInfo.of(Long.class);
            case "ingredient", "optional_ingredient" -> TypeInfo.of(Ingredient.class);
            case "item_stack", "optional_item_stack" -> TypeInfo.of(ItemStack.class);
            case "fluid_stack", "optional_fluid_stack" -> TypeInfo.of(FluidStack.class);
            case "flat_sized_ingredient" -> TypeInfo.of(SizedIngredient.class);
            case "flat_sized_fluid_ingredient" -> TypeInfo.of(SizedFluidIngredient.class);
            default -> parse(storage.recipeComponentCodec, expression).typeInfo();
        };
    }

    @Override
    public JsonElement encodeConstant(JsonElement expression, JsonElement literal) {
        return bind(expression).constant(literal);
    }

    private interface Binding {
        AdapterInputShape shape();
        JsonElement encode(Value value, RecipeSchemaHost host);
        JsonElement constant(JsonElement value);
        default boolean nominalInput() { return true; }
    }

    private record Scalar(AdapterInputShape shape, RecipeFieldValueCodec.Encoder encoder,
                          Function<JsonElement, JsonElement> constants, boolean nominalInput) implements Binding {
        private Scalar(AdapterInputShape shape, RecipeFieldValueCodec.Encoder encoder,
                       Function<JsonElement, JsonElement> constants) {
            this(shape, encoder, constants, true);
        }
        @Override public JsonElement encode(Value value, RecipeSchemaHost host) { return encoder.encode(value, host); }
        @Override public JsonElement constant(JsonElement value) { return constants.apply(value); }
    }

    private Binding bind(JsonElement expression) {
        if (expression == null || expression.isJsonNull()) throw error(5280, "Missing component expression");
        if (expression.isJsonObject()) {
            JsonObject object = expression.getAsJsonObject();
            String type = text(object.get("type"));
            return switch (type) {
                case "int" -> {
                    exactKeys(object, Set.of("type", "min"));
                    if (integer(object.get("min"), 0, 0) != 0) throw error(5281, "Unsupported int range");
                    yield numberBinding(0, Integer.MAX_VALUE, true);
                }
                case "double" -> {
                    exactKeys(object, Set.of("type", "min", "max"));
                    if (decimal(object.get("min")).compareTo(new BigDecimal("0.00001")) != 0
                            || decimal(object.get("max")).compareTo(BigDecimal.ONE) != 0) {
                        throw error(5282, "Unsupported double range");
                    }
                    yield numberBinding(0.00001, 1, false);
                }
                case "list" -> listBinding(object);
                case "pattern" -> patternBinding(object);
                case "either" -> eitherBinding(object);
                default -> throw error(5283, "Unsupported dynamic component: " + type);
            };
        }
        String type = text(expression);
        return switch (type) {
            case "string" -> new Scalar(string(), (value, host) -> {
                if (!value.isString()) throw error(5306, "Expected a string component value");
                return new JsonPrimitive(value.asString());
            }, value -> new JsonPrimitive(text(value)));
            case "crafting_book_category" -> categoryBinding();
            case "optional_ingredient" -> optionalIngredientBinding();
            case "boolean" -> new Scalar(bool(), (value, host) -> {
                if (!value.isBoolean()) throw error(5284, "Expected a boolean");
                return new JsonPrimitive(value.asBoolean());
            }, value -> {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw error(5284, "Expected a boolean");
                return value.deepCopy();
            });
            case "ticks" -> ticks();
            case "positive_long" -> numberBinding(1, Long.MAX_VALUE, true);
            case "non_negative_long" -> numberBinding(0, Long.MAX_VALUE, true);
            case "ingredient" -> nativeBinding(RecipeFieldKind.INGREDIENT, Ingredient.CODEC_NONEMPTY, Ingredient.class,
                    ingredientShape(), false);
            case "item_stack", "optional_item_stack" -> nativeBinding(RecipeFieldKind.ITEM_STACK,
                    type.startsWith("optional") ? ItemStack.OPTIONAL_CODEC : ItemStack.CODEC, ItemStack.class,
                    itemShape(), type.startsWith("optional"));
            case "fluid_stack", "optional_fluid_stack" -> nativeBinding(RecipeFieldKind.FLUID_STACK,
                    type.startsWith("optional") ? FluidStack.OPTIONAL_CODEC : FluidStack.CODEC, FluidStack.class,
                    fluidStackShape(), type.startsWith("optional"));
            case "flat_sized_ingredient" -> sizedIngredient();
            case "flat_sized_fluid_ingredient" -> nativeBinding(RecipeFieldKind.SIZED_FLUID_INGREDIENT,
                    SizedFluidIngredient.FLAT_CODEC, SizedFluidIngredient.class, sizedFluidShape(), false);
            case "create:processing_output", "create:sized_fluid_ingredient", "create:heat_condition",
                    "mekanism:chemical_stack", "mekanism:optional_chemical_stack", "mekanism:chemical_stack_ingredient" -> custom(type);
            case "nested_recipe" -> throw error(5285, "Nested recipe construction is not supported");
            default -> throw error(5286, "Unsupported component: " + type);
        };
    }

    @Override
    public RecipeExecutionFactory executionFactory(JsonElement factory, List<JsonElement> processors) {
        if (factory != null && factory.isJsonPrimitive() && factory.getAsJsonPrimitive().isString()
                && factory.getAsString().equals("nekujs:native_shaped") && processors.isEmpty()) {
            return new NativeShapedRecipeExecution(registries);
        }
        throw error(5250, "Unsupported recipe execution factory or post-processor");
    }

    private Binding categoryBinding() {
        AdapterInputShape shape = union(host(CraftingBookCategory.class), literal("building"), literal("redstone"),
                literal("equipment"), literal("misc"));
        return new Scalar(shape, (value, host) -> {
            if (value.isHostObject() && value.asHostObject() instanceof CraftingBookCategory category) {
                return encode(CraftingBookCategory.CODEC, category);
            }
            if (!value.isString()) throw error(5308, "Expected a crafting book category");
            return normalize(CraftingBookCategory.CODEC, new JsonPrimitive(value.asString()));
        }, value -> normalize(CraftingBookCategory.CODEC, value), false);
    }

    private Binding optionalIngredientBinding() {
        return new Scalar(union(host(Ingredient.class), ingredientShape(), arrayOf(ingredientShape())), (value, host) -> {
            if (value.isHostObject() && value.asHostObject() instanceof Ingredient ingredient) {
                return encode(Ingredient.CODEC, ingredient);
            }
            if (value.hasArrayElements() && value.getArraySize() == 0) {
                return encode(Ingredient.CODEC, Ingredient.EMPTY);
            }
            return normalize(Ingredient.CODEC, host.encodeField(RecipeFieldKind.INGREDIENT, value));
        }, value -> normalize(Ingredient.CODEC, value));
    }

    private Binding patternBinding(JsonObject expression) {
        exactKeys(expression, Set.of("type", "component"));
        Binding child = bind(expression.get("component"));
        return new Scalar(object(), (value, host) -> {
            JsonObject result = new JsonObject();
            if (value.hasHashEntries()) {
                Value entries = value.getHashEntriesIterator();
                while (entries.hasIteratorNextElement()) {
                    Value pair = entries.getIteratorNextElement();
                    Value key = pair.getArrayElement(0);
                    String name = key.isString() ? key.asString()
                            : key.isHostObject() && key.asHostObject() instanceof Character character
                            ? character.toString() : null;
                    result.add(patternKey(name), child.encode(pair.getArrayElement(1), host));
                }
            } else if (value.hasMembers() && !value.hasArrayElements()) {
                for (String key : value.getMemberKeys()) result.add(patternKey(key), child.encode(value.getMember(key), host));
            } else throw error(5307, "Pattern keys require an object or host map");
            if (result.isEmpty()) throw error(5307, "Pattern key map must not be empty");
            return result;
        }, value -> {
            if (!value.isJsonObject() || value.getAsJsonObject().isEmpty()) {
                throw error(5307, "Pattern key map must be a nonempty object");
            }
            JsonObject result = new JsonObject();
            for (var entry : value.getAsJsonObject().entrySet()) {
                result.add(patternKey(entry.getKey()), child.constant(entry.getValue()));
            }
            return result;
        }, child.nominalInput());
    }

    private static String patternKey(String key) {
        if (key == null || key.length() != 1 || key.charAt(0) == ' ') {
            throw error(5307, "Pattern key must be one non-space character");
        }
        return key;
    }

    private Binding numberBinding(double min, double max, boolean integral) {
        Function<JsonElement, JsonElement> convert = value -> {
            BigDecimal number = decimal(value);
            if (integral) {
                long result = integer(value, min <= 0 ? 0 : 1, max == Integer.MAX_VALUE ? Integer.MAX_VALUE : Long.MAX_VALUE);
                return new JsonPrimitive(result);
            }
            double result = number.doubleValue();
            if (!Double.isFinite(result) || result < min || result > max) throw error(5287, "Number outside component bounds");
            return new JsonPrimitive(result);
        };
        return new Scalar(number(), (value, host) -> {
            if (!value.isNumber()) throw error(5288, "Expected a number");
            if (integral) {
                if (!value.fitsInLong()) throw error(5289, "Expected an exact long integer");
                return convert.apply(new JsonPrimitive(safeRuntimeLong(value)));
            }
            return convert.apply(new JsonPrimitive(value.asDouble()));
        }, convert);
    }

    private Binding ticks() {
        Function<JsonElement, JsonElement> convert = value -> new JsonPrimitive(integer(value, Long.MIN_VALUE, Long.MAX_VALUE));
        return new Scalar(union(number(), string(), host(TickDuration.class), host(Duration.class)), (value, host) -> {
            if (value.isString()) return convert.apply(new JsonPrimitive(
                    safeRuntimeLong(Value.asValue(TickDuration.wrap(null, value.asString()).ticks()))));
            if (value.isHostObject()) {
                Object object = value.asHostObject();
                if (object instanceof TickDuration ticks) return new JsonPrimitive(ticks.ticks());
                if (object instanceof Duration duration) return new JsonPrimitive(TickDuration.wrap(null, duration).ticks());
            }
            if (!value.isNumber() || !value.fitsInLong()) throw error(5290, "Ticks require an exact long, TickDuration, or Duration");
            return convert.apply(new JsonPrimitive(safeRuntimeLong(value)));
        }, convert);
    }

    private <T> Binding nativeBinding(RecipeFieldKind kind, Codec<T> codec, Class<T> target,
                                      AdapterInputShape shape, boolean optional) {
        AdapterInputShape accepted = optional ? union(host(target), shape, object()) : union(host(target), shape);
        return new Scalar(accepted, (value, host) -> {
            if (value.isHostObject() && target.isInstance(value.asHostObject())) {
                return encode(codec, target.cast(value.asHostObject()));
            }
            if (optional && emptyObject(value)) return normalize(codec, new JsonObject());
            validateRuntimeNumbers(kind, value);
            // The native host writes sized fluid ingredients in nested form.
            JsonElement encoded = host.encodeField(kind, value);
            validateNativeNumbers(kind, encoded);
            if (kind == RecipeFieldKind.SIZED_FLUID_INGREDIENT) {
                SizedFluidIngredient sized = parse(SizedFluidIngredient.NESTED_CODEC, encoded);
                return encode(SizedFluidIngredient.FLAT_CODEC, sized);
            }
            return normalize(codec, encoded);
        }, value -> {
            validateNativeNumbers(kind, value);
            return normalize(codec, value);
        });
    }

    private Binding sizedIngredient() {
        AdapterInputShape accepted = union(host(SizedIngredient.class), host(ItemStack.class), string(), ingredientShape(),
                object(Slot.req("ingredient", ingredientShape()), Slot.req("count", number())),
                object(Slot.req("item", string()), Slot.req("count", number())),
                object(Slot.req("tag", string()), Slot.req("count", number())));
        return new Scalar(accepted, (value, host) -> {
            if (value.isHostObject()) {
                Object object = value.asHostObject();
                if (object instanceof SizedIngredient sized) return encode(SizedIngredient.FLAT_CODEC, sized);
                if (object instanceof ItemStack stack) return encode(SizedIngredient.FLAT_CODEC,
                        new SizedIngredient(Ingredient.of(stack), stack.getCount()));
            }
            int count = 1;
            Value ingredient = value;
            if (value.isString()) {
                String raw = value.asString();
                int separator = raw.indexOf("x ");
                if (separator > 0) {
                    count = (int) integer(new JsonPrimitive(new BigDecimal(raw.substring(0, separator).trim())), 1, Integer.MAX_VALUE);
                    ingredient = Value.asValue(raw.substring(separator + 2).trim());
                }
            } else if (value.hasMembers()) {
                if (value.hasMember("count")) count = strictCount(value.getMember("count"));
                if (value.hasMember("ingredient")) ingredient = value.getMember("ingredient");
            }
            Ingredient decoded = parse(Ingredient.CODEC_NONEMPTY, host.encodeField(RecipeFieldKind.INGREDIENT, ingredient));
            return encode(SizedIngredient.FLAT_CODEC, new SizedIngredient(decoded, count));
        }, value -> {
            validateIntegerField(value, "count", Integer.MAX_VALUE);
            return normalize(SizedIngredient.FLAT_CODEC, value);
        });
    }

    private Binding custom(String name) {
        RecipeComponent<?> component = parse(storage.recipeComponentCodec, new JsonPrimitive(name));
        return customBinding(name, component);
    }

    private <T> Binding customBinding(String name, RecipeComponent<T> component) {
        Class<?> target = component.typeInfo().asClass();
        AdapterInputShape literalShape = literalShapeFor(name);
        AdapterInputShape accepted = union(host(target), literalShape);
        return new Scalar(accepted, (value, host) -> {
            if (value.isHostObject() && target.isInstance(value.asHostObject())) {
                return encodeHost(component.codec(), target, value.asHostObject());
            }
            if (name.equals("create:processing_output") || name.equals("create:sized_fluid_ingredient")) {
                validateRuntimeCustomNumbers(name, value);
                dev.latvian.mods.kubejs.recipe.RecipeScriptContext context = dev.latvian.mods.rhino.Context::new;
                T converted = component.wrap(context, value);
                JsonElement encoded = encode(component.codec(), converted);
                validateCustomNumbers(name, encoded);
                return encoded;
            }
            // These remaining codec-only inputs do not claim the broader legacy registry-dependent wrappers.
            JsonElement json = literalValue(value);
            requireShape(literalShape, json);
            validateCustomNumbers(name, json);
            return normalize(component.codec(), json);
        }, value -> {
            requireShape(literalShape, value);
            validateCustomNumbers(name, value);
            return normalize(component.codec(), value);
        });
    }

    private AdapterInputShape literalShapeFor(String name) {
        return switch (name) {
            case "create:processing_output" -> object(Slot.req("id", string()), Slot.opt("count", number()), Slot.opt("chance", number()));
            case "create:sized_fluid_ingredient" -> union(
                    object(Slot.req("type", literal("neoforge:single")), Slot.req("fluid", string()), Slot.req("amount", number())),
                    object(Slot.req("type", literal("neoforge:tag")), Slot.req("tag", string()), Slot.req("amount", number())));
            case "create:heat_condition" -> union(literal("none"), literal("heated"), literal("superheated"));
            case "mekanism:chemical_stack_ingredient" -> union(
                    object(Slot.req("chemical", string()), Slot.req("amount", number())),
                    object(Slot.req("tag", string()), Slot.req("amount", number())));
            case "mekanism:optional_chemical_stack" -> union(object(), object(Slot.req("id", string()), Slot.req("amount", number())));
            default -> object(Slot.req("id", string()), Slot.req("amount", number()));
        };
    }

    private Binding eitherBinding(JsonObject object) {
        exactKeys(object, Set.of("type", "left", "right"));
        String left = text(object.get("left"));
        String right = text(object.get("right"));
        if (!(left.equals("fluid_stack") && right.equals("create:processing_output"))
                && !(left.equals("create:sized_fluid_ingredient") && right.equals("ingredient"))) {
            throw error(5291, "Unsupported either component");
        }
        Binding first = bind(object.get("left"));
        Binding second = bind(object.get("right"));
        return new Scalar(union(first.shape(), second.shape()), (value, host) -> {
            if (value.isHostObject()) return (fluidValue(value) ? first : second).encode(value, host);
            try {
                return first.encode(value, host);
            } catch (com.tkisor.nekojs.api.data.ValueConversionException | IllegalArgumentException firstFailure) {
                try {
                    return second.encode(value, host);
                } catch (RuntimeException secondFailure) {
                    secondFailure.addSuppressed(firstFailure);
                    throw secondFailure;
                }
            }
        }, value -> (fluidJson(value) ? first : second).constant(value));
    }

    private Binding listBinding(JsonObject object) {
        exactKeys(object, Set.of("type", "component", "spread", "can_write_self"));
        Binding child = bind(object.get("component"));
        boolean spread = object.has("spread");
        boolean canWriteSelf = object.has("can_write_self") && object.get("can_write_self").getAsBoolean();
        if (spread && (!isCreateIngredientEither(object.get("component")) || !isSpread(object.get("spread")))) {
            throw error(5292, "Unsupported list spread component");
        }
        AdapterInputShape elementShape = spread ? union(child.shape(), sizedIngredient().shape()) : child.shape();
        return new Scalar(union(elementShape, arrayOf(elementShape)), (value, host) -> {
            JsonArray result = new JsonArray();
            if (value.hasArrayElements()) {
                for (long index = 0; index < value.getArraySize(); index++) addRuntime(result, child, spread, value.getArrayElement(index), host);
            } else addRuntime(result, child, spread, value, host);
            return canWriteSelf && result.size() == 1 ? result.get(0) : result;
        }, value -> {
            JsonArray result = new JsonArray();
            if (value.isJsonArray()) for (JsonElement entry : value.getAsJsonArray()) addConstant(result, child, spread, entry);
            else addConstant(result, child, spread, value);
            return canWriteSelf && result.size() == 1 ? result.get(0) : result;
        }, child.nominalInput());
    }

    private void addRuntime(JsonArray result, Binding child, boolean spread, Value value, RecipeSchemaHost host) {
        if (!spread || fluidValue(value)) { result.add(child.encode(value, host)); return; }
        SizedIngredient sized = parse(SizedIngredient.FLAT_CODEC, sizedIngredient().encode(value, host));
        JsonElement ingredient = encode(Ingredient.CODEC_NONEMPTY, sized.ingredient());
        for (int index = 0; index < sized.count(); index++) result.add(ingredient.deepCopy());
    }

    private void addConstant(JsonArray result, Binding child, boolean spread, JsonElement value) {
        if (!spread || fluidJson(value)) { result.add(child.constant(value)); return; }
        JsonObject flat = value.isJsonObject() ? value.getAsJsonObject().deepCopy() : null;
        if (flat == null) { result.add(child.constant(value)); return; }
        if (!flat.has("count")) flat.addProperty("count", 1);
        validateIntegerField(flat, "count", Integer.MAX_VALUE);
        SizedIngredient sized = parse(SizedIngredient.FLAT_CODEC, flat);
        JsonElement ingredient = encode(Ingredient.CODEC_NONEMPTY, sized.ingredient());
        for (int index = 0; index < sized.count(); index++) result.add(ingredient.deepCopy());
    }

    private static boolean isCreateIngredientEither(JsonElement value) {
        if (value == null || !value.isJsonObject()) return false;
        JsonObject object = value.getAsJsonObject();
        return object.size() == 3 && "either".equals(text(object.get("type")))
                && "create:sized_fluid_ingredient".equals(text(object.get("left")))
                && "ingredient".equals(text(object.get("right")));
    }

    private static boolean isSpread(JsonElement value) {
        if (value == null || !value.isJsonObject()) return false;
        JsonObject object = value.getAsJsonObject();
        return object.size() == 3 && "either".equals(text(object.get("type")))
                && "ignore".equals(text(object.get("left"))) && "sized_ingredient".equals(text(object.get("right")));
    }

    private static boolean fluidValue(Value value) {
        if (value.isHostObject()) {
            Object object = value.asHostObject();
            if (object instanceof FluidStack || object instanceof SizedFluidIngredient) return true;
        }
        return value.hasMembers() && (value.hasMember("fluid") || value.hasMember("id") && value.hasMember("amount")
                || value.hasMember("type") && value.getMember("type").isString()
                && isFluidType(value.getMember("type").asString()));
    }

    private static boolean fluidJson(JsonElement value) {
        if (!value.isJsonObject()) return false;
        JsonObject object = value.getAsJsonObject();
        return object.has("fluid") || object.has("id") && object.has("amount") || object.has("type")
                && object.get("type").isJsonPrimitive() && isFluidType(object.get("type").getAsString());
    }

    private static boolean isFluidType(String type) {
        return type.equals("neoforge:single") || type.equals("neoforge:tag");
    }

    private static AdapterInputShape ingredientShape() {
        return union(string(), object(Slot.req("item", string())), object(Slot.req("tag", string())));
    }

    private static AdapterInputShape itemShape() {
        return union(string(), object(Slot.req("id", string()), Slot.opt("count", number())),
                object(Slot.req("item", string()), Slot.opt("count", number())));
    }

    private static AdapterInputShape fluidStackShape() {
        return union(string(), object(Slot.req("id", string()), Slot.req("amount", number())),
                object(Slot.req("fluid", string()), Slot.req("amount", number())));
    }

    private static AdapterInputShape sizedFluidShape() {
        return union(object(Slot.req("fluid", string()), Slot.req("amount", number()), Slot.opt("type", string())),
                object(Slot.req("tag", string()), Slot.req("amount", number()), Slot.opt("type", string())));
    }

    private <T> T parse(Codec<T> codec, JsonElement value) {
        return codec.parse(registries.json(), value).getOrThrow(message -> error(5293, "Component decode failed: " + message));
    }

    private <T> JsonElement encode(Codec<T> codec, T value) {
        return codec.encodeStart(registries.json(), value).getOrThrow(message -> error(5294, "Component encode failed: " + message));
    }

    private <T> JsonElement normalize(Codec<T> codec, JsonElement value) { return encode(codec, parse(codec, value)); }

    @SuppressWarnings("unchecked")
    private <T> JsonElement encodeHost(Codec<T> codec, Class<?> target, Object value) {
        return encode(codec, (T) target.cast(value));
    }

    private static void validateRuntimeNumbers(RecipeFieldKind kind, Value value) {
        switch (kind) {
            case ITEM_STACK -> validateRuntimeIntegerField(value, "count", 99);
            case FLUID_STACK, SIZED_FLUID_INGREDIENT -> validateRuntimeIntegerField(value, "amount", Integer.MAX_VALUE);
            default -> { }
        }
    }

    private static void validateRuntimeCustomNumbers(String name, Value value) {
        switch (name) {
            case "create:processing_output" -> validateRuntimeIntegerField(value, "count", 99);
            case "create:sized_fluid_ingredient" -> validateRuntimeIntegerField(value, "amount", Integer.MAX_VALUE);
            case "mekanism:chemical_stack", "mekanism:optional_chemical_stack", "mekanism:chemical_stack_ingredient" ->
                    validateRuntimeIntegerField(value, "amount", Long.MAX_VALUE);
            default -> { }
        }
    }

    private static void validateRuntimeIntegerField(Value value, String field, long max) {
        if (!value.isHostObject() && value.hasMembers() && value.hasMember(field)) {
            integer(literalValue(value.getMember(field)), 1, max);
        }
    }

    private static void validateNativeNumbers(RecipeFieldKind kind, JsonElement value) {
        switch (kind) {
            case ITEM_STACK -> validateIntegerField(value, "count", 99);
            case FLUID_STACK, SIZED_FLUID_INGREDIENT -> validateIntegerField(value, "amount", Integer.MAX_VALUE);
            default -> { }
        }
    }

    private static void validateCustomNumbers(String name, JsonElement value) {
        switch (name) {
            case "create:processing_output" -> validateIntegerField(value, "count", 99);
            case "create:sized_fluid_ingredient" -> validateIntegerField(value, "amount", Integer.MAX_VALUE);
            case "mekanism:chemical_stack", "mekanism:optional_chemical_stack", "mekanism:chemical_stack_ingredient" ->
                    validateIntegerField(value, "amount", Long.MAX_VALUE);
            default -> { }
        }
    }

    private static void validateIntegerField(JsonElement value, String field, long max) {
        // Primitive codecs coerce Number.intValue/longValue before checking bounds; check exactness first.
        if (value.isJsonObject() && value.getAsJsonObject().has(field)) {
            integer(value.getAsJsonObject().get(field), 1, max);
        }
    }

    private static long safeRuntimeLong(Value value) {
        long result = value.asLong();
        if (result < -MAX_SAFE_INTEGER || result > MAX_SAFE_INTEGER) {
            throw error(5305, "Runtime numeric integer exceeds the JavaScript-safe range; use an exact typed host value");
        }
        return result;
    }

    private static int strictCount(Value value) {
        if (!value.isNumber() || !value.fitsInInt() || value.asInt() < 1) throw error(5295, "Ingredient count must be a positive int");
        return value.asInt();
    }

    private static boolean emptyObject(Value value) {
        return value.hasMembers() && !value.isHostObject() && value.getMemberKeys().isEmpty();
    }

    private static JsonElement literalValue(Value value) {
        if (value.isString()) return new JsonPrimitive(value.asString());
        if (value.isBoolean()) return new JsonPrimitive(value.asBoolean());
        if (value.isNumber()) {
            if (value.fitsInLong()) return new JsonPrimitive(safeRuntimeLong(value));
            double number = value.asDouble();
            if (!Double.isFinite(number)) throw error(5296, "Non-finite component literal");
            if (number == Math.rint(number) && Math.abs(number) > MAX_SAFE_INTEGER) {
                throw error(5305, "Runtime numeric integer exceeds the JavaScript-safe range; use an exact typed host value");
            }
            return new JsonPrimitive(number);
        }
        if (!value.isHostObject() && value.hasArrayElements()) {
            JsonArray array = new JsonArray();
            for (long index = 0; index < value.getArraySize(); index++) array.add(literalValue(value.getArrayElement(index)));
            return array;
        }
        if (!value.isHostObject() && value.hasMembers()) {
            JsonObject object = new JsonObject();
            for (String key : value.getMemberKeys()) object.add(key, literalValue(value.getMember(key)));
            return object;
        }
        throw error(5297, "Expected a component JSON literal or its exact typed host value");
    }

    private static void requireShape(AdapterInputShape shape, JsonElement value) {
        if (!matches(shape, value)) throw error(5298, "Component literal does not match its advertised input shape: " + value);
    }

    private static boolean matches(AdapterInputShape shape, JsonElement value) {
        if (value == null || value.isJsonNull()) return false;
        if (shape instanceof AdapterInputShape.UnionValue union) return union.members().stream().anyMatch(member -> matches(member, value));
        if (shape instanceof AdapterInputShape.StringValue) return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
        if (shape instanceof AdapterInputShape.LiteralValue literal) return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && literal.text().equals(value.getAsString());
        if (shape instanceof AdapterInputShape.NumberValue) return value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
        if (shape instanceof AdapterInputShape.ObjectValue object && value.isJsonObject()) {
            JsonObject json = value.getAsJsonObject();
            for (AdapterInputShape.Slot slot : object.slots()) {
                if (!json.has(slot.name())) { if (slot.required()) return false; }
                else if (!matches(slot.shape(), json.get(slot.name()))) return false;
            }
            return json.keySet().stream().allMatch(key -> object.slots().stream().anyMatch(slot -> slot.name().equals(key)));
        }
        return false;
    }

    private static String text(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw error(5299, "Expected a component name");
        return value.getAsString();
    }

    private static BigDecimal decimal(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw error(5300, "Expected a numeric literal");
        try { return value.getAsBigDecimal(); }
        catch (NumberFormatException failure) { throw error(5301, "Invalid finite numeric literal"); }
    }

    private static long integer(JsonElement value, long min, long max) {
        final long result;
        try { result = decimal(value).longValueExact(); }
        catch (ArithmeticException failure) { throw error(5302, "Expected an exact long integer"); }
        if (result < min || result > max) throw error(5303, "Integer outside component bounds");
        return result;
    }

    private static void exactKeys(JsonObject object, Set<String> allowed) {
        if (!allowed.containsAll(object.keySet())) throw error(5304, "Unknown component metadata: " + object);
    }

    private static IllegalArgumentException error(int code, String message) {
        return new IllegalArgumentException("[NEKO-" + code + "] " + message);
    }
}
