//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.regex.Pattern;

/** Validates item visuals and resolves one detached stack per staged node change. */
final class JsxItemVisual {
    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    private JsxItemVisual() {}

    record Descriptor(String item, int count, boolean itemTooltip) {
        Descriptor {
            if (item == null || !ITEM_ID.matcher(item).matches()) {
                throw invalid("item must be a lowercase qualified resource ID");
            }
            if (count < 1 || count > 99) {
                throw invalid("count must be an integer from 1 to 99");
            }
        }
    }

    interface Resolved {
        void paint(GuiGraphicsExtractor graphics, Font font, int x, int y);
        void tooltip(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY);
    }

    interface Resolver {
        Resolved resolve(Descriptor descriptor);
    }

    static Descriptor parse(Map<String, Object> props) {
        if (props == null || !(props.get("item") instanceof String item)) {
            throw invalid("item must be a lowercase qualified resource ID string");
        }
        int count = props.containsKey("count") ? count(props.get("count")) : 1;
        boolean itemTooltip = true;
        if (props.containsKey("itemTooltip")) {
            if (!(props.get("itemTooltip") instanceof Boolean enabled)) {
                throw invalid("itemTooltip must be a boolean");
            }
            itemTooltip = enabled;
        }
        if (props.containsKey("tooltip") && !(props.get("tooltip") instanceof String)) {
            throw invalid("tooltip must be a string");
        }
        return new Descriptor(item, count, itemTooltip);
    }

    private static int count(Object value) {
        if (!(value instanceof Number number)) {
            throw invalid("count must be an integer from 1 to 99");
        }
        int count;
        try {
            if (number instanceof BigDecimal decimal) {
                count = decimal.intValueExact();
            } else if (number instanceof BigInteger integer) {
                count = integer.intValueExact();
            } else {
                double numeric = number.doubleValue();
                if (!Double.isFinite(numeric) || numeric < 1 || numeric > 99 || numeric != Math.rint(numeric)) {
                    throw invalid("count must be an integer from 1 to 99");
                }
                count = (int) numeric;
            }
        } catch (ArithmeticException failure) {
            throw invalid("count must be an integer from 1 to 99");
        }
        return count;
    }

    /** The adapter owns thread checks and calls this resolver only while staging changes. */
    static Resolver minecraft() {
        return descriptor -> {
            Item item = BuiltInRegistries.ITEM.get(Identifier.parse(descriptor.item()))
                    .orElseThrow(() -> invalid("unknown item: " + descriptor.item())).value();
            if (item == Items.AIR) {
                throw invalid("air cannot be displayed as an item visual: " + descriptor.item());
            }
            ItemStack stack = new ItemStack(item, descriptor.count());
            return new Resolved() {
                @Override
                public void paint(GuiGraphicsExtractor graphics, Font font, int x, int y) {
                    graphics.item(stack, x, y);
                    graphics.itemDecorations(font, stack, x, y);
                }

                @Override
                public void tooltip(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
                    graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
                }
            };
        };
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("[NEKO-6012] Invalid JSX item visual: " + message);
    }
}
//?}
