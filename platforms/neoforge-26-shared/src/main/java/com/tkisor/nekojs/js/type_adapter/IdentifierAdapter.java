package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.data.ValueConversionException;
import java.util.List;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import net.minecraft.resources.Identifier;

public class IdentifierAdapter extends AbstractJSTypeAdapter<Identifier> {

    private static final String DEFAULT_NAMESPACE = NekoJS.MODID;

    @Override
    public Class<Identifier> getTargetClass() {
        return Identifier.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                self(),
                string(),
                host(NekoId.class));
    }

    @Override
    protected Identifier fromString(String id) {
        if (id.contains(":")) {
            Identifier parsed = Identifier.tryParse(id);
            if (parsed == null) {
                throw new ValueConversionException(Identifier.class, "valid identifier", id,
                    "invalid id syntax");
            }
            return parsed;
        }
        return Identifier.fromNamespaceAndPath(DEFAULT_NAMESPACE, id);
    }

    @Override
    protected Identifier fromHostObject(Object host) {
        if (host instanceof Identifier identifier) return identifier;
        if (host instanceof NekoId id) {
            return Identifier.fromNamespaceAndPath(id.namespace(), id.path());
        }
        return null; // 不识别
    }
}
