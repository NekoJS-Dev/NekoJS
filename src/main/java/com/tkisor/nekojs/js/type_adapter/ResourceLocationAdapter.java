//? if <26 {
/*package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;
import java.util.List;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import com.tkisor.nekojs.api.data.NekoId;
import net.minecraft.resources.Identifier;

/^*
 * Adapter for Identifier. A string without a namespace uses {@link NekoJS#MODID} as the default
 * namespace. An input that is recognized but invalid (for example a failed tryParse) throws
 * {@link ValueConversionException}.
^/
public class ResourceLocationAdapter extends AbstractJSTypeAdapter<Identifier> {

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
protected Identifier fromString(String s) {
if (s.contains(":")) {
// 1.21.1: Identifier.parse throws on invalid input; tryParse first so one null check
// reports a uniform error.
Identifier parsed = Identifier.tryParse(s);
if (parsed == null) {
throw new ValueConversionException(Identifier.class, "valid resource location string", s,
"invalid resource location: " + s);
}
return parsed;
}
return Identifier.fromNamespaceAndPath(DEFAULT_NAMESPACE, s);
}

@Override
protected Identifier fromHostObject(Object host) {
if (host instanceof Identifier location) return location;
if (host instanceof NekoId id) return Identifier.fromNamespaceAndPath(id.namespace(), id.path());
return null;
}
}
 *///?}
