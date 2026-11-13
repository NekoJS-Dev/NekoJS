//~ mc_legacy_api
package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.List;

import static com.tkisor.nekojs.api.AdapterInputShape.*;

/**
 * BlockState input adapter: converts a {@code 'minecraft:stone[prop=val,prop2=val2]'} string
 * (KubeJS style) into a {@link BlockState}. Properties inside {@code [...]} are comma
 * separated and resolved against the block's property table; an unknown property or an
 * illegal value throws {@link ValueConversionException}.
 */
public class BlockStateAdapter extends AbstractJSTypeAdapter<BlockState> {

    @Override
    public Class<BlockState> getTargetClass() {
        return BlockState.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                self(),
                string(),
                registry("Block"),
                host(Block.class));
    }

    @Override
    protected BlockState defaultValue() {
        return Blocks.AIR.defaultBlockState();
    }

    @Override
    protected BlockState fromString(String s) {
        if (s == null || s.isBlank()) return defaultValue();
        String trimmed = s.trim();
        String idPart = trimmed;
        String propsPart = null;
        int bracket = trimmed.indexOf('[');
        if (bracket >= 0) {
            if (!trimmed.endsWith("]")) {
                throw new ValueConversionException(BlockState.class, "block state string", s,
                        "expected ']' to close property list");
            }
            idPart = trimmed.substring(0, bracket).trim();
            propsPart = trimmed.substring(bracket + 1, trimmed.length() - 1).trim();
        }
        Identifier location = ParseIds.parseItemOrBlockId(idPart);
        String blockId = idPart;
        Block block = BuiltInRegistries.BLOCK.getOptional(location)
                .orElseThrow(() -> new ValueConversionException(
                        BlockState.class, "registered block id", blockId, "block not found: " + blockId));
        BlockState state = block.defaultBlockState();
        if (propsPart != null && !propsPart.isEmpty()) {
            state = applyProperties(state, propsPart, s);
        }
        return state;
    }

    @Override
    protected BlockState fromHostObject(Object host) {
        if (host instanceof BlockState state) return state;
        if (host instanceof Block block) return block.defaultBlockState();
        return null; // Not recognized
    }

    private static BlockState applyProperties(BlockState state, String propsPart, String original) {
        BlockState result = state;
        for (String pair : propsPart.split(",")) {
            String trimmed = pair.trim();
            if (trimmed.isEmpty()) continue;
            int eq = trimmed.indexOf('=');
            if (eq <= 0) {
                throw new ValueConversionException(BlockState.class, "property=value pair", trimmed,
                        "expected 'prop=value' in block state string: " + original);
            }
            String propName = trimmed.substring(0, eq).trim();
            String valueStr = trimmed.substring(eq + 1).trim();
            Property<?> property = result.getBlock().getStateDefinition().getProperty(propName);
            if (property == null) {
                throw new ValueConversionException(BlockState.class, "known property", propName,
                        "block " + result.getBlock() + " has no property '" + propName + "'");
            }
            result = withValue(result, property, valueStr, original);
        }
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState withValue(BlockState state, Property property, String valueStr, String original) {
        Object parsed = property.getValue(valueStr).orElse(null);
        if (parsed == null) {
            throw new ValueConversionException(BlockState.class, "valid value for property " + property.getName(),
                    valueStr, "invalid value in block state string: " + original);
        }
        return state.setValue(property, (Comparable) parsed);
    }
}
