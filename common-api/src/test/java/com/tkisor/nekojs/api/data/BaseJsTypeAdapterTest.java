package com.tkisor.nekojs.api.data;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Collection;
import java.util.List;

class BaseJsTypeAdapterTest {

    @Test
    void supportsStringWhenIsStringReturnsTrue() {
        JsTypeAdapter<String> adapter = new UpperCaseAdapter();
        JsValueView stringView = new MockJsValueView.StringMock("hello");

        assertTrue(adapter.supports(stringView, ConversionContext.empty()));
    }

    @Test
    void convertsStringToUpperCase() {
        JsTypeAdapter<String> adapter = new UpperCaseAdapter();
        JsValueView stringView = new MockJsValueView.StringMock("hello");

        assertEquals("HELLO", adapter.convert(stringView, ConversionContext.empty()));
    }

    @Test
    void rejectsNullWhenAcceptNullIsFalse() {
        JsTypeAdapter<String> adapter = new UpperCaseAdapter();
        JsValueView nullView = new MockJsValueView.NullMock();

        assertFalse(adapter.supports(nullView, ConversionContext.empty()));
    }

    @Test
    void returnsDefaultWhenNullAllowed() {
        JsTypeAdapter<String> adapter = new NullableAdapter();
        JsValueView nullView = new MockJsValueView.NullMock();

        assertTrue(adapter.supports(nullView, ConversionContext.empty()));
        assertEquals("DEFAULT", adapter.convert(nullView, ConversionContext.empty()));
    }

    @Test
    void supportsHostObjectWhenTypeMatches() {
        JsTypeAdapter<String> adapter = new UpperCaseAdapter();
        JsValueView hostView = new MockJsValueView.HostMock("hello");

        assertTrue(adapter.supports(hostView, ConversionContext.empty()));
        assertEquals("HELLO", adapter.convert(hostView, ConversionContext.empty()));
    }

    @Test
    void targetTypeIsCorrect() {
        JsTypeAdapter<String> adapter = new UpperCaseAdapter();
        assertEquals(String.class, adapter.targetType());
    }

    @Test
    void defaultPrecedenceIsLowest() {
        JsTypeAdapter<String> adapter = new UpperCaseAdapter();
        assertEquals(ConversionPrecedence.LOWEST, adapter.precedence());
    }

    @Test
    void fromOtherThrowsValueConversionException() {
        JsTypeAdapter<String> adapter = new UpperCaseAdapter();
        JsValueView otherView = new MockJsValueView() {
            @Override public boolean isNull() { return false; }
            @Override public boolean isString() { return false; }
            @Override public boolean isHostObject() { return false; }
            @Override public boolean isBoolean() { return true; }
            @Override public boolean asBoolean() { return false; }
            @Override public boolean isNumber() { return false; }
            @Override public boolean isArray() { return false; }
        };

        assertFalse(adapter.supports(otherView, ConversionContext.empty()));
        assertThrows(ValueConversionException.class,
            () -> adapter.convert(otherView, ConversionContext.empty()));
    }

    @Test
    void rejectsStringWhenFromStringIsNotOverridden() {
        JsTypeAdapter<String> adapter = new NoStringAdapter();
        JsValueView stringView = new MockJsValueView.StringMock("hello");

        assertFalse(adapter.supports(stringView, ConversionContext.empty()),
                "adapter that does not override fromString must reject the string shape");
        assertThrows(ValueConversionException.class,
                () -> adapter.convert(stringView, ConversionContext.empty()));
    }

    /** 不接受 null（acceptNull=false）时 convert 必须抛异常，而非静默返回 defaultValue。 */
    @Test
    void convertThrowsOnNullWhenAcceptNullIsFalse() {
        JsTypeAdapter<String> adapter = new UpperCaseAdapter();
        JsValueView nullView = new MockJsValueView.NullMock();

        assertThrows(ValueConversionException.class,
                () -> adapter.convert(nullView, ConversionContext.empty()));
    }

    /** fromHostObject 对已接受的 host 输入返回 null 视为失败，异常消息需指明适配器类。 */
    @Test
    void convertThrowsWhenFromHostObjectReturnsNull() {
        JsTypeAdapter<String> adapter = new NullHostAdapter();
        JsValueView hostView = new MockJsValueView.HostMock("hello");

        assertTrue(adapter.supports(hostView, ConversionContext.empty()),
                "host object shape is accepted before conversion");
        ValueConversionException e = assertThrows(ValueConversionException.class,
                () -> adapter.convert(hostView, ConversionContext.empty()));
        assertTrue(e.getMessage().contains("NullHostAdapter"),
                "exception message should name the adapter class: " + e.getMessage());
    }

    /** fromOther 对已接受的其它形状返回 null 视为失败，异常消息需指明适配器类。 */
    @Test
    void convertThrowsWhenFromOtherReturnsNull() {
        JsTypeAdapter<String> adapter = new NullOtherAdapter();
        JsValueView otherView = new MockJsValueView() {};

        assertTrue(adapter.supports(otherView, ConversionContext.empty()),
                "other shape is accepted before conversion");
        ValueConversionException e = assertThrows(ValueConversionException.class,
                () -> adapter.convert(otherView, ConversionContext.empty()));
        assertTrue(e.getMessage().contains("NullOtherAdapter"),
                "exception message should name the adapter class: " + e.getMessage());
    }

    /** 不接受字符串：未覆盖 fromString（supportsString 默认 true 但探测 fromString 会失败）。 */
    private static final class NoStringAdapter extends BaseJsTypeAdapter<String> {
        NoStringAdapter() { super(String.class); }
        @Override protected String fromHostObject(Object host) { return host.toString(); }
    }

    /** 接受 host object 形状但 fromHostObject 返回 null（真实子类的缺陷形态，如 ComponentAdapter）。 */
    private static final class NullHostAdapter extends BaseJsTypeAdapter<String> {
        NullHostAdapter() { super(String.class); }
        @Override protected String fromString(String s) { return s; }
        @Override protected String fromHostObject(Object host) { return null; }
    }

    /** 接受其它形状（acceptOther）但 fromOther 返回 null。 */
    private static final class NullOtherAdapter extends BaseJsTypeAdapter<String> {
        NullOtherAdapter() { super(String.class); }
        @Override protected boolean acceptOther(JsValueView value) { return true; }
        @Override protected String fromOther(JsValueView value) { return null; }
        @Override protected String fromHostObject(Object host) { return host.toString(); }
    }

    private static final class UpperCaseAdapter extends BaseJsTypeAdapter<String> {
        UpperCaseAdapter() { super(String.class); }
        @Override protected String fromString(String s) { return s.toUpperCase(); }
        @Override protected String fromHostObject(Object host) { return host.toString().toUpperCase(); }
    }

    private static final class NullableAdapter extends BaseJsTypeAdapter<String> {
        NullableAdapter() { super(String.class); }
        @Override protected boolean acceptNull() { return true; }
        @Override protected String defaultValue() { return "DEFAULT"; }
        @Override protected String fromString(String s) { return s; }
        @Override protected String fromHostObject(Object host) { return host.toString(); }
    }
}
