package com.tkisor.nekojs.api.facade;

import com.tkisor.nekojs.api.data.JsonValue;

/**
 * JSON 门面，暴露为脚本侧全局对象 {@code JsonIO}。
 *
 * <p>提供 JSON 字符串解析/序列化，以及基于数据目录的 JSON 文件读写。所有值均表示为
 * 平台无关的 {@link JsonValue}。解析失败或超出限制时抛出
 * {@link com.tkisor.nekojs.api.error.ApiInvocationException}
 * （错误码见 {@link com.tkisor.nekojs.api.error.ApiErrorCodes}）。
 */
public interface JsonFacade {
    /** 解析 JSON 字符串为 {@link JsonValue}；非法 JSON 或超限时抛异常。 */
    JsonValue parse(String source);

    /** 把 {@link JsonValue} 序列化为紧凑 JSON 字符串。 */
    String toString(JsonValue value);

    /** 把 {@link JsonValue} 序列化为带缩进的易读 JSON 字符串。 */
    String toPrettyString(JsonValue value);

    /**
     * 从数据目录读取指定相对路径的 JSON 文件并解析为 {@link JsonValue}。
     *
     * @return the parsed value, or {@code null} when the file does not exist
     */
    JsonValue read(String path);

    /**
     * 把 {@link JsonValue} 序列化后写入数据目录下指定相对路径的 JSON 文件。
     *
     * <p>Writes are atomic (temp file + atomic move), so readers never observe a partial file.
     */
    void write(String path, JsonValue value);
}
