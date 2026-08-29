package com.tkisor.nekojs.wrapper.clientdata;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.tkisor.nekojs.core.JsonObjectAdapter;
import graal.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端→客户端键值数据（{@code ClientData.sync} / {@code clientData.get}）的客户端存储。
 *
 * <p>纯 common、无 Minecraft 依赖：平台网络层收到 {@code ClientDataSyncPacket} 后在主线程
 * 调用 {@link #accept(String, JsonElement)} 写入；脚本侧经 {@code clientData} 绑定只读访问。
 * 值以 gson {@link JsonElement} 保存（网络传输用 JSON 字符串，三个平台共用一种编解码），
 * 读取时经 {@link #jsonToObject} 还原成 Map/List/String/Number/Boolean/null 交给 GraalJS。
 *
 * <p>v1 语义：同 key 直接覆盖；断线/切世界时由平台客户端钩子调用 {@link #clear()}
 * （NeoForge 侧挂在 level unload，同 {@code PDataSyncService.clearClientMirrors} 的时机）。
 *
 * <p>JSON 类型（string/number/bool/object/array/null）可完整往返；其它 Java 类型由
 * {@link #toJsonElement(Object)} 拒绝（抛 {@link IllegalArgumentException}）。
 */
public final class ClientDataStore {

    /** key 上限：与脚本网络 channel（64）相比放宽到 256，足够标识 HUD/界面数据槽位。 */
    public static final int MAX_KEY_LENGTH = 256;

    /** 进程级共享实例：客户端收包与各 ScriptType 的 {@code clientData} 绑定都读写它。 */
    public static final ClientDataStore SHARED = new ClientDataStore();

    private final Map<String, JsonElement> data = new ConcurrentHashMap<>();

    /** 客户端收到同步包：覆盖写入（v1 无版本号，同 key 后到者胜）。 */
    public void accept(String key, JsonElement value) {
        data.put(requireKey(key), requireValue(value));
    }

    /** 读取 key 对应值并还原为脚本友好对象；不存在或值为 JSON null 时返回 {@code null}。 */
    public Object get(String key) {
        JsonElement element = data.get(requireKey(key));
        return element == null ? null : jsonToObject(element);
    }

    /** 是否存在该 key（值为 JSON null 时也返回 true，与 {@link #get} 的 null 返回区分）。 */
    public boolean has(String key) {
        return data.containsKey(requireKey(key));
    }

    /** 当前全部 key 的快照（无序）。 */
    public Set<String> keys() {
        return Set.copyOf(data.keySet());
    }

    /** 当前条目数。 */
    public int size() {
        return data.size();
    }

    /** 清空全部数据（断线/切世界钩子调用）。 */
    public void clear() {
        data.clear();
    }

    private static String requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "client data key must be non-blank and at most " + MAX_KEY_LENGTH + " characters");
        }
        return key;
    }

    private static JsonElement requireValue(JsonElement value) {
        if (value == null) {
            throw new IllegalArgumentException("client data value must not be null (use JsonNull for JSON null)");
        }
        return value;
    }

    /**
     * 把脚本传入的任意值转成 gson {@link JsonElement}。
     *
     * <p>GraalJS 对 {@code Object} 参数的默认映射：JS object→{@code Map}、array→{@code List}、
     * 原始值→装箱类型；直接传入的 Graal {@link Value} 走 {@link JsonObjectAdapter#convertValueToJson}。
     * 已是 {@link JsonElement} 的原样通过；其余类型（函数、宿主对象等非 JSON 值）抛
     * {@link IllegalArgumentException}。
     */
    public static JsonElement toJsonElement(Object value) {
        if (value == null) return JsonNull.INSTANCE;
        if (value instanceof JsonElement element) return element;
        if (value instanceof Boolean bool) return new JsonPrimitive(bool);
        if (value instanceof String string) return new JsonPrimitive(string);
        if (value instanceof Character character) return new JsonPrimitive(character);
        if (value instanceof Number number) {
            // 保持整数的 int/long 精度，其余按 double（与 JsonObjectAdapter.convertValueToJson 一致）
            if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
                return new JsonPrimitive(number.longValue());
            }
            return new JsonPrimitive(number.doubleValue());
        }
        if (value instanceof Map<?, ?> map) {
            JsonObject object = new JsonObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                object.add(String.valueOf(entry.getKey()), toJsonElement(entry.getValue()));
            }
            return object;
        }
        if (value instanceof Collection<?> collection) {
            JsonArray array = new JsonArray();
            for (Object element : collection) {
                array.add(toJsonElement(element));
            }
            return array;
        }
        if (value.getClass().isArray()) {
            JsonArray array = new JsonArray();
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                array.add(toJsonElement(java.lang.reflect.Array.get(value, i)));
            }
            return array;
        }
        if (value instanceof Value graalValue) {
            return JsonObjectAdapter.convertValueToJson(graalValue);
        }
        throw new IllegalArgumentException(
                "client data values must be JSON types (string/number/bool/object/array), got "
                        + value.getClass().getName());
    }

    /**
     * 把 gson {@link JsonElement} 还原为脚本友好对象：
     * object→{@code LinkedHashMap}、array→{@code ArrayList}、原始值→装箱类型、null→{@code null}。
     * 整数统一为 {@link Long}、小数为 {@link Double}（JSON 无 int/double 之分，往返保持数值等值）。
     */
    public static Object jsonToObject(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) return primitive.getAsBoolean();
            if (primitive.isString()) return primitive.getAsString();
            if (primitive.isNumber()) {
                // gson 反序列化出的 LazilyParsedNumber 不区分 int/double，按文本形态判定：
                // 无 '.'/'e'/'E' 视作整数统一为 Long，否则 Double。与序列化侧
                // （整数存 long、其余存 double）互为逆操作，数值往返无损。
                String raw = primitive.getAsString();
                if (raw.indexOf('.') < 0 && raw.indexOf('e') < 0 && raw.indexOf('E') < 0) {
                    return primitive.getAsLong();
                }
                return primitive.getAsDouble();
            }
            return primitive.getAsString();
        }
        if (element.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonElement child : element.getAsJsonArray()) {
                list.add(jsonToObject(child));
            }
            return list;
        }
        if (element.isJsonObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                map.put(entry.getKey(), jsonToObject(entry.getValue()));
            }
            return map;
        }
        return null;
    }
}
