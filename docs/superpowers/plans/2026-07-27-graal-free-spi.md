# Graal-free Conversion SPI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Define Graal-free replacement types for `JSTypeAdapter` / `AbstractJSTypeAdapter` / `JSTypeAdapterRegistry` in `:common-api`, with runtime bridges in `:common` so all existing code continues working.

**Architecture:** New SPI types (`JsValueView`, `JsTypeAdapter`, `ConversionPrecedence`, `ConversionContext`, `AbstractJsTypeAdapter`, `JsTypeAdapterRegistry`) live in `common-api/src/.../api/data/`. Two relocated Graal-free types (`AdapterInputShape`, `ValueConversionException`) move from `common` to `common-api` keeping the same package. Three bridge classes in `common` (`GraalValueView`, `LegacyAdapterBridge`, `NewAdapterBridge`) translate between old Graal-tainted and new Graal-free interfaces.

**Tech Stack:** Java 21, no new dependencies (types use only `java.lang`, `java.util`).

## Global Constraints

- `:common-api` must compile with ZERO Graal imports (`grep -r "graal\." common-api/src` → empty)
- All types in `common-api` use `com.tkisor.nekojs.api` or `com.tkisor.nekojs.api.data` package
- Bridge classes in `common` go in `com.tkisor.nekojs.core.bridge` package
- `JAVA_HOME` = `C:\Program Files\Java\jdk-25.0.2`
- Existing `JSTypeAdapter`, `AbstractJSTypeAdapter`, `JSTypeAdapterRegistry` remain untouched in `common`
- No platform code changes (all adapter implementations stay as-is)

---

### Task 1: Relocate `AdapterInputShape` to common-api

**Files:**
- Move: `common/src/main/java/com/tkisor/nekojs/api/AdapterInputShape.java` → `common-api/src/main/java/com/tkisor/nekojs/api/AdapterInputShape.java`

**Description:** Move the Graal-free `AdapterInputShape` sealed interface from `common` to `common-api`. Package unchanged (`com.tkisor.nekojs.api`), so zero import changes needed in consumers.

- [ ] **Step 1: Create target directory**

```powershell
New-Item -ItemType Directory -Force -Path "common-api\src\main\java\com\tkisor\nekojs\api"
```

- [ ] **Step 2: Copy file, delete original**

```powershell
Copy-Item -LiteralPath "common\src\main\java\com\tkisor\nekojs\api\AdapterInputShape.java" -Destination "common-api\src\main\java\com\tkisor\nekojs\api\AdapterInputShape.java"
Remove-Item -LiteralPath "common\src\main\java\com\tkisor\nekojs\api\AdapterInputShape.java"
```

- [ ] **Step 3: Verify common-api compiles**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'; .\gradlew.bat :common-api:compileJava --no-daemon
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Verify common still compiles (it already depends on common-api)**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'; .\gradlew.bat :common:compileJava --no-daemon
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```powershell
git add common-api/src/main/java/com/tkisor/nekojs/api/AdapterInputShape.java common/src/main/java/com/tkisor/nekojs/api/AdapterInputShape.java
git commit -m "refactor: move AdapterInputShape to common-api"
```

---

### Task 2: Relocate `ValueConversionException` to common-api

**Files:**
- Move: `common/src/main/java/com/tkisor/nekojs/api/data/ValueConversionException.java` → `common-api/src/main/java/com/tkisor/nekojs/api/data/ValueConversionException.java`

**Description:** Move the Graal-free `ValueConversionException` from `common` to `common-api`. Package unchanged, no import changes.

- [ ] **Step 1: Create target directory**

```powershell
New-Item -ItemType Directory -Force -Path "common-api\src\main\java\com\tkisor\nekojs\api\data"
```

- [ ] **Step 2: Copy file, delete original**

```powershell
Copy-Item -LiteralPath "common\src\main\java\com\tkisor\nekojs\api\data\ValueConversionException.java" -Destination "common-api\src\main\java\com\tkisor\nekojs\api\data\ValueConversionException.java"
Remove-Item -LiteralPath "common\src\main\java\com\tkisor\nekojs\api\data\ValueConversionException.java"
```

- [ ] **Step 3: Verify both modules compile**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'; .\gradlew.bat :common-api:compileJava :common:compileJava --no-daemon
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```powershell
git add common-api/src/main/java/com/tkisor/nekojs/api/data/ValueConversionException.java common/src/main/java/com/tkisor/nekojs/api/data/ValueConversionException.java
git commit -m "refactor: move ValueConversionException to common-api"
```

---

### Task 3: Create `ConversionPrecedence` enum

**Files:**
- Create: `common-api/src/main/java/com/tkisor/nekojs/api/data/ConversionPrecedence.java`

**Description:** Simple 4-value enum mirroring `HostAccess.TargetMappingPrecedence` semantics without the Graal dependency.

- [ ] **Step 1: Write the enum**

```java
package com.tkisor.nekojs.api.data;

public enum ConversionPrecedence {
    LOWEST,
    LOW,
    HIGH,
    HIGHEST
}
```

- [ ] **Step 2: Write a skip-test (only enum, nothing testable yet)**

No unit test needed — enum has no behavior.

- [ ] **Step 3: Verify common-api compiles**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'; .\gradlew.bat :common-api:compileJava --no-daemon
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```powershell
git add common-api/src/main/java/com/tkisor/nekojs/api/data/ConversionPrecedence.java
git commit -m "feat(api): add ConversionPrecedence enum (Graal-free)"
```

---

### Task 4: Create `ConversionContext` class

**Files:**
- Create: `common-api/src/main/java/com/tkisor/nekojs/api/data/ConversionContext.java`

**Description:** Opaque context class with singleton `empty()` factory. Reserved for future flags.

- [ ] **Step 1: Write the class**

```java
package com.tkisor.nekojs.api.data;

public final class ConversionContext {
    private static final ConversionContext EMPTY = new ConversionContext();

    private ConversionContext() {}

    public static ConversionContext empty() {
        return EMPTY;
    }
}
```

- [ ] **Step 2: Verify common-api compiles**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'; .\gradlew.bat :common-api:compileJava --no-daemon
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```powershell
git add common-api/src/main/java/com/tkisor/nekojs/api/data/ConversionContext.java
git commit -m "feat(api): add ConversionContext class (Graal-free)"
```

---

### Task 5: Create `JsValueView` interface

**Files:**
- Create: `common-api/src/main/java/com/tkisor/nekojs/api/data/JsValueView.java`

**Description:** Graal-free value inspection interface. Replaces `graal.graalvm.polyglot.Value` in SPI signatures.

- [ ] **Step 1: Write the interface**

```java
package com.tkisor.nekojs.api.data;

import java.util.Collection;

public interface JsValueView {
    boolean isNull();
    boolean isString();
    boolean isNumber();
    boolean isBoolean();
    boolean isHostObject();
    boolean isArray();

    String asString();
    int asInt();
    double asDouble();
    boolean asBoolean();

    <T> T asHostObject(Class<T> type);

    boolean hasMember(String key);
    JsValueView getMember(String key);
    JsValueView getArrayElement(long index);
    long getArraySize();
    Collection<String> getMemberKeys();
}
```

- [ ] **Step 2: Verify common-api compiles**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'; .\gradlew.bat :common-api:compileJava --no-daemon
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```powershell
git add common-api/src/main/java/com/tkisor/nekojs/api/data/JsValueView.java
git commit -m "feat(api): add JsValueView interface (Graal-free)"
```

---

### Task 6: Create `JsTypeAdapter<T>` interface and `JsTypeAdapterRegistry`

**Files:**
- Create: `common-api/src/main/java/com/tkisor/nekojs/api/data/JsTypeAdapter.java`
- Create: `common-api/src/main/java/com/tkisor/nekojs/api/data/JsTypeAdapterRegistry.java`

**Description:** Graal-free adapter and registry interfaces. `JsTypeAdapter` does NOT extend `Predicate`/`Function` — callers use `supports()` + `convert()`.

- [ ] **Step 1: Write `JsTypeAdapter.java`**

```java
package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.AdapterInputShape;

import java.util.List;

public interface JsTypeAdapter<T> {
    Class<T> targetType();

    boolean supports(JsValueView value, ConversionContext context);

    T convert(JsValueView value, ConversionContext context);

    ConversionPrecedence precedence();

    default List<AdapterInputShape> inputShapes() {
        return List.of();
    }
}
```

- [ ] **Step 2: Write `JsTypeAdapterRegistry.java`**

```java
package com.tkisor.nekojs.api.data;

import java.util.Collection;

public interface JsTypeAdapterRegistry {
    <T> void register(JsTypeAdapter<T> adapter);

    Collection<JsTypeAdapter<?>> view();
}
```

- [ ] **Step 3: Verify common-api compiles**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'; .\gradlew.bat :common-api:compileJava --no-daemon
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```powershell
git add common-api/src/main/java/com/tkisor/nekojs/api/data/JsTypeAdapter.java common-api/src/main/java/com/tkisor/nekojs/api/data/JsTypeAdapterRegistry.java
git commit -m "feat(api): add JsTypeAdapter and JsTypeAdapterRegistry (Graal-free)"
```

---

### Task 7: Create `AbstractJsTypeAdapter<T>`

**Files:**
- Create: `common-api/src/main/java/com/tkisor/nekojs/api/data/AbstractJsTypeAdapter.java`
- Test: `common-api/src/test/java/com/tkisor/nekojs/api/data/AbstractJsTypeAdapterTest.java`

**Interfaces:**
- Consumes: `JsTypeAdapter<T>`, `JsValueView`, `ConversionContext`, `ValueConversionException` (all from Tasks 2-6)
- Produces: `AbstractJsTypeAdapter<T>` — concrete base class for adapters

**Description:** Convenience base class implementing `supports()`/`convert()` dispatching by null/string/host/other. Similar to old `AbstractJSTypeAdapter` but using `JsValueView`.

- [ ] **Step 1: Add test-source support to common-api build.gradle**

Edit `common-api/build.gradle` to add test dependencies. Add after the `dependencies` block's existing line:

```groovy
    testImplementation platform('org.junit:junit-bom:6.0.0')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
```

And add a `test` block at the bottom:

```groovy
test {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Create test directories**

```powershell
New-Item -ItemType Directory -Force -Path "common-api\src\test\java\com\tkisor\nekojs\api\data"
```

- [ ] **Step 3: Write `AbstractJsTypeAdapter.java`**

```java
package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.AdapterInputShape;

import java.util.List;
import java.util.Objects;

public abstract class AbstractJsTypeAdapter<T> implements JsTypeAdapter<T> {
    private final Class<T> targetType;

    protected AbstractJsTypeAdapter(Class<T> targetType) {
        this.targetType = Objects.requireNonNull(targetType, "targetType");
    }

    @Override
    public Class<T> targetType() {
        return targetType;
    }

    @Override
    public ConversionPrecedence precedence() {
        return ConversionPrecedence.LOWEST;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of();
    }

    protected boolean acceptNull() {
        return false;
    }

    protected T defaultValue() {
        return null;
    }

    protected T fromString(String s) {
        throw new ValueConversionException(targetType, "string", s, "not supported");
    }

    protected abstract T fromHostObject(Object host);

    protected boolean acceptOther(JsValueView value) {
        return false;
    }

    protected T fromOther(JsValueView value) {
        throw new ValueConversionException(targetType, "other", value, "not supported");
    }

    @Override
    public boolean supports(JsValueView value, ConversionContext context) {
        if (value.isNull()) return acceptNull();
        if (value.isString()) return true;
        if (value.isHostObject()) {
            Object host = value.asHostObject(Object.class);
            return host != null && targetType.isAssignableFrom(host.getClass());
        }
        return acceptOther(value);
    }

    @Override
    public T convert(JsValueView value, ConversionContext context) {
        if (value.isNull()) return defaultValue();
        if (value.isString()) return fromString(value.asString());
        if (value.isHostObject()) return fromHostObject(value.asHostObject(Object.class));
        return fromOther(value);
    }
}
```

- [ ] **Step 4: Write failing test**

```java
package com.tkisor.nekojs.api.data;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Collection;
import java.util.List;

class AbstractJsTypeAdapterTest {

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

    private static final class UpperCaseAdapter extends AbstractJsTypeAdapter<String> {
        UpperCaseAdapter() { super(String.class); }
        @Override protected String fromString(String s) { return s.toUpperCase(); }
        @Override protected String fromHostObject(Object host) { return host.toString().toUpperCase(); }
    }

    private static final class NullableAdapter extends AbstractJsTypeAdapter<String> {
        NullableAdapter() { super(String.class); }
        @Override protected boolean acceptNull() { return true; }
        @Override protected String defaultValue() { return "DEFAULT"; }
        @Override protected String fromString(String s) { return s; }
        @Override protected String fromHostObject(Object host) { return host.toString(); }
    }
}
```

- [ ] **Step 5: Write minimal `MockJsValueView` test helper**

File: `common-api/src/test/java/com/tkisor/nekojs/api/data/MockJsValueView.java`

```java
package com.tkisor.nekojs.api.data;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

interface MockJsValueView extends JsValueView {

    default boolean isNull() { return false; }
    default boolean isString() { return false; }
    default boolean isNumber() { return false; }
    default boolean isBoolean() { return false; }
    default boolean isHostObject() { return false; }
    default boolean isArray() { return false; }

    default String asString() { throw new UnsupportedOperationException(); }
    default int asInt() { throw new UnsupportedOperationException(); }
    default double asDouble() { throw new UnsupportedOperationException(); }
    default boolean asBoolean() { throw new UnsupportedOperationException(); }

    default <T> T asHostObject(Class<T> type) { throw new UnsupportedOperationException(); }

    default boolean hasMember(String key) { return false; }
    default JsValueView getMember(String key) { throw new UnsupportedOperationException(); }
    default JsValueView getArrayElement(long index) { throw new UnsupportedOperationException(); }
    default long getArraySize() { return 0; }
    default Collection<String> getMemberKeys() { return Collections.emptyList(); }

    final class NullMock implements MockJsValueView {
        @Override public boolean isNull() { return true; }
    }

    final class StringMock implements MockJsValueView {
        private final String value;
        StringMock(String value) { this.value = value; }
        @Override public boolean isString() { return true; }
        @Override public String asString() { return value; }
    }

    final class HostMock implements MockJsValueView {
        private final Object value;
        HostMock(Object value) { this.value = value; }
        @Override public boolean isHostObject() { return true; }
        @Override public <T> T asHostObject(Class<T> type) { return type.cast(value); }
    }

    final class NumberMock implements MockJsValueView {
        private final int value;
        NumberMock(int value) { this.value = value; }
        @Override public boolean isNumber() { return true; }
        @Override public int asInt() { return value; }
        @Override public double asDouble() { return value; }
    }

    final class ArrayMock implements MockJsValueView {
        private final List<JsValueView> elements;
        ArrayMock(List<JsValueView> elements) { this.elements = elements; }
        @Override public boolean isArray() { return true; }
        @Override public JsValueView getArrayElement(long index) { return elements.get((int) index); }
        @Override public long getArraySize() { return elements.size(); }
    }
}
```

- [ ] **Step 6: Run tests to verify pass**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'; .\gradlew.bat :common-api:test --no-daemon --tests "com.tkisor.nekojs.api.data.AbstractJsTypeAdapterTest"
```

Expected: all 8 tests PASS

- [ ] **Step 7: Commit**

```powershell
git add common-api/build.gradle common-api/src/main/java/com/tkisor/nekojs/api/data/AbstractJsTypeAdapter.java common-api/src/test/
git commit -m "feat(api): add AbstractJsTypeAdapter with tests"
```

---

### Task 8: Create `GraalValueView` bridge

**Files:**
- Create: `common/src/main/java/com/tkisor/nekojs/core/bridge/GraalValueView.java`
- Test: `common/src/test/java/com/tkisor/nekojs/core/bridge/GraalValueViewTest.java`

**Interfaces:**
- Consumes: `JsValueView` (from Task 5), `graal.graalvm.polyglot.Value`, `graal.graalvm.polyglot.Context`
- Produces: `GraalValueView` — `JsValueView` impl wrapping a Graal `Value`

- [ ] **Step 1: Create test directories**

```powershell
New-Item -ItemType Directory -Force -Path "common\src\test\java\com\tkisor\nekojs\core\bridge"
New-Item -ItemType Directory -Force -Path "common\src\main\java\com\tkisor\nekojs\core\bridge"
```

- [ ] **Step 2: Write failing test**

```java
package com.tkisor.nekojs.core.bridge;

import com.tkisor.nekojs.api.data.JsValueView;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GraalValueViewTest {

    private Context context;

    @BeforeEach
    void setUp() {
        context = Context.newBuilder("js").allowAllAccess(true).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void wrapsNullValue() {
        Value v = context.eval("js", "null");
        JsValueView view = GraalValueView.wrap(v);

        assertTrue(view.isNull());
    }

    @Test
    void wrapsStringValue() {
        Value v = context.eval("js", "'hello'");
        JsValueView view = GraalValueView.wrap(v);

        assertTrue(view.isString());
        assertFalse(view.isNull());
        assertEquals("hello", view.asString());
    }

    @Test
    void wrapsNumberValue() {
        Value v = context.eval("js", "42");
        JsValueView view = GraalValueView.wrap(v);

        assertTrue(view.isNumber());
        assertEquals(42, view.asInt());
        assertEquals(42.0, view.asDouble(), 0.001);
    }

    @Test
    void wrapsBooleanValue() {
        Value v = context.eval("js", "true");
        JsValueView view = GraalValueView.wrap(v);

        assertTrue(view.isBoolean());
        assertTrue(view.asBoolean());
    }

    @Test
    void wrapsHostObject() {
        Value v = context.eval("js", "new java.lang.String('test')");
        JsValueView view = GraalValueView.wrap(v);

        assertTrue(view.isHostObject());
        String result = view.asHostObject(String.class);
        assertEquals("test", result);
    }

    @Test
    void hasMemberReturnsTrueForExistingProperty() {
        Value v = context.eval("js", "({ a: 1, b: 'x' })");
        JsValueView view = GraalValueView.wrap(v);

        assertTrue(view.hasMember("a"));
        assertTrue(view.hasMember("b"));
        assertFalse(view.hasMember("c"));
    }

    @Test
    void getMemberReturnsWrappedValue() {
        Value v = context.eval("js", "({ name: 'NekoJS' })");
        JsValueView view = GraalValueView.wrap(v);
        JsValueView member = view.getMember("name");

        assertTrue(member.isString());
        assertEquals("NekoJS", member.asString());
    }

    @Test
    void getArrayElementAccessesByIndex() {
        Value v = context.eval("js", "[10, 20, 30]");
        JsValueView view = GraalValueView.wrap(v);

        assertTrue(view.isArray());
        assertEquals(3, view.getArraySize());
        assertEquals(10, view.getArrayElement(0).asInt());
        assertEquals(20, view.getArrayElement(1).asInt());
        assertEquals(30, view.getArrayElement(2).asInt());
    }

    @Test
    void wrapNullValueReturnsNull() {
        assertNull(GraalValueView.wrap(null));
    }

    @Test
    void unwrapReturnsOriginalValue() {
        Value original = context.eval("js", "42");
        GraalValueView view = (GraalValueView) GraalValueView.wrap(original);

        assertSame(original, view.unwrap());
    }
}
```

- [ ] **Step 3: Verify test fails**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'; .\gradlew.bat :common:test --no-daemon --tests "com.tkisor.nekojs.core.bridge.GraalValueViewTest"
```

Expected: FAIL

- [ ] **Step 4: Write `GraalValueView.java`**

```java
package com.tkisor.nekojs.core.bridge;

import com.tkisor.nekojs.api.data.JsValueView;
import graal.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public final class GraalValueView implements JsValueView {
    private final Value delegate;

    private GraalValueView(Value delegate) {
        this.delegate = delegate;
    }

    public static JsValueView wrap(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        return new GraalValueView(value);
    }

    public Value unwrap() {
        return delegate;
    }

    @Override
    public boolean isNull() {
        return delegate.isNull();
    }

    @Override
    public boolean isString() {
        return delegate.isString();
    }

    @Override
    public boolean isNumber() {
        return delegate.isNumber();
    }

    @Override
    public boolean isBoolean() {
        return delegate.isBoolean();
    }

    @Override
    public boolean isHostObject() {
        return delegate.isHostObject();
    }

    @Override
    public boolean isArray() {
        return delegate.hasArrayElements();
    }

    @Override
    public String asString() {
        return delegate.asString();
    }

    @Override
    public int asInt() {
        return delegate.asInt();
    }

    @Override
    public double asDouble() {
        return delegate.asDouble();
    }

    @Override
    public boolean asBoolean() {
        return delegate.asBoolean();
    }

    @Override
    public <T> T asHostObject(Class<T> type) {
        return delegate.asHostObject() instanceof Object obj ? type.cast(obj) : null;
    }

    @Override
    public boolean hasMember(String key) {
        return delegate.hasMember(key);
    }

    @Override
    public JsValueView getMember(String key) {
        return wrap(delegate.getMember(key));
    }

    @Override
    public JsValueView getArrayElement(long index) {
        return wrap(delegate.getArrayElement(index));
    }

    @Override
    public long getArraySize() {
        return delegate.getArraySize();
    }

    @Override
    public Collection<String> getMemberKeys() {
        return new ArrayList<>(delegate.getMemberKeys());
    }
}
```

- [ ] **Step 5: Run tests to verify pass**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'; .\gradlew.bat :common:test --no-daemon --tests "com.tkisor.nekojs.core.bridge.GraalValueViewTest"
```

Expected: all 10 tests PASS

- [ ] **Step 6: Commit**

```powershell
git add common/src/main/java/com/tkisor/nekojs/core/bridge/GraalValueView.java common/src/test/java/com/tkisor/nekojs/core/bridge/GraalValueViewTest.java
git commit -m "feat(core): add GraalValueView bridge (Value -> JsValueView)"
```

---

### Task 9: Create `LegacyAdapterBridge`

**Files:**
- Create: `common/src/main/java/com/tkisor/nekojs/core/bridge/LegacyAdapterBridge.java`
- Test: `common/src/test/java/com/tkisor/nekojs/core/bridge/LegacyAdapterBridgeTest.java`

**Interfaces:**
- Consumes: `JSTypeAdapter` (existing), `JsTypeAdapter` (from Task 6), `GraalValueView` (from Task 8)
- Produces: `LegacyAdapterBridge<T>` — wraps old `JSTypeAdapter<T>` as new `JsTypeAdapter<T>`

- [ ] **Step 1: Write failing test**

```java
package com.tkisor.nekojs.core.bridge;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.*;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.HostAccess;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LegacyAdapterBridgeTest {

    private Context context;

    @BeforeEach
    void setUp() {
        context = Context.newBuilder("js").allowAllAccess(true).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void targetTypeDelegatesToWrappedAdapter() {
        JSTypeAdapter<String> legacy = new StringLengthAdapter();
        JsTypeAdapter<String> bridge = new LegacyAdapterBridge<>(legacy);

        assertEquals(String.class, bridge.targetType());
    }

    @Test
    void supportsStringViaGraalValueView() {
        JSTypeAdapter<String> legacy = new StringLengthAdapter();
        JsTypeAdapter<String> bridge = new LegacyAdapterBridge<>(legacy);

        Value v = context.eval("js", "'hello world'");
        JsValueView view = GraalValueView.wrap(v);

        assertTrue(bridge.supports(view, ConversionContext.empty()));
    }

    @Test
    void convertReturnsAdaptedValue() {
        JSTypeAdapter<String> legacy = new StringLengthAdapter();
        JsTypeAdapter<String> bridge = new LegacyAdapterBridge<>(legacy);

        Value v = context.eval("js", "'abc'");
        JsValueView view = GraalValueView.wrap(v);

        assertEquals("3", bridge.convert(view, ConversionContext.empty()));
    }

    @Test
    void precedenceMapsFromHostAccess() {
        JSTypeAdapter<String> legacy = new HighPrecedenceAdapter();
        JsTypeAdapter<String> bridge = new LegacyAdapterBridge<>(legacy);

        assertEquals(ConversionPrecedence.HIGH, bridge.precedence());
    }

    @Test
    void supportsReturnsFalseForNonGraalValueView() {
        JsTypeAdapter<String> bridge = new LegacyAdapterBridge<>(new StringLengthAdapter());
        JsValueView mock = new MockJsValueView.StringMock("test");

        assertFalse(bridge.supports(mock, ConversionContext.empty()));
    }

    @Test
    void inputShapesDelegates() {
        final class ShapedAdapter implements JSTypeAdapter<String> {
            @Override public Class<String> getTargetClass() { return String.class; }
            @Override public boolean test(Value value) { return value.isString(); }
            @Override public String apply(Value value) { return value.asString(); }
            @Override public List<AdapterInputShape> inputShapes() {
                return List.of(AdapterInputShape.string());
            }
        }

        JsTypeAdapter<String> bridge = new LegacyAdapterBridge<>(new ShapedAdapter());
        assertEquals(1, bridge.inputShapes().size());
    }

    private static final class StringLengthAdapter implements JSTypeAdapter<String> {
        @Override public Class<String> getTargetClass() { return String.class; }
        @Override public boolean test(Value value) { return value.isString(); }
        @Override public String apply(Value value) { return String.valueOf(value.asString().length()); }
    }

    private static final class HighPrecedenceAdapter implements JSTypeAdapter<String> {
        @Override public Class<String> getTargetClass() { return String.class; }
        @Override public boolean test(Value value) { return value.isString(); }
        @Override public String apply(Value value) { return value.asString(); }
        @Override public HostAccess.TargetMappingPrecedence getPrecedence() {
            return HostAccess.TargetMappingPrecedence.HIGH;
        }
    }
}
```

- [ ] **Step 2: Verify test fails**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'; .\gradlew.bat :common:test --no-daemon --tests "com.tkisor.nekojs.core.bridge.LegacyAdapterBridgeTest"
```

Expected: FAIL

- [ ] **Step 3: Write `LegacyAdapterBridge.java`**

```java
package com.tkisor.nekojs.core.bridge;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.*;

import java.util.List;
import java.util.Objects;

public final class LegacyAdapterBridge<T> implements JsTypeAdapter<T> {
    private final JSTypeAdapter<T> legacy;

    public LegacyAdapterBridge(JSTypeAdapter<T> legacy) {
        this.legacy = Objects.requireNonNull(legacy, "legacy");
    }

    @Override
    public Class<T> targetType() {
        return legacy.getTargetClass();
    }

    @Override
    public boolean supports(JsValueView value, ConversionContext context) {
        if (!(value instanceof GraalValueView gv)) return false;
        return legacy.test(gv.unwrap());
    }

    @Override
    public T convert(JsValueView value, ConversionContext context) {
        if (!(value instanceof GraalValueView gv)) {
            throw new ValueConversionException(targetType(), "Graal Value", value,
                "LegacyAdapterBridge requires a GraalValueView");
        }
        return legacy.apply(gv.unwrap());
    }

    @Override
    public ConversionPrecedence precedence() {
        return switch (legacy.getPrecedence()) {
            case LOWEST -> ConversionPrecedence.LOWEST;
            case LOW -> ConversionPrecedence.LOW;
            case HIGH -> ConversionPrecedence.HIGH;
            case HIGHEST -> ConversionPrecedence.HIGHEST;
        };
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return legacy.inputShapes();
    }
}
```

- [ ] **Step 4: Run tests to verify pass**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'; .\gradlew.bat :common:test --no-daemon --tests "com.tkisor.nekojs.core.bridge.LegacyAdapterBridgeTest"
```

Expected: all 6 tests PASS

- [ ] **Step 5: Commit**

```powershell
git add common/src/main/java/com/tkisor/nekojs/core/bridge/LegacyAdapterBridge.java common/src/test/java/com/tkisor/nekojs/core/bridge/LegacyAdapterBridgeTest.java
git commit -m "feat(core): add LegacyAdapterBridge (JSTypeAdapter -> JsTypeAdapter)"
```

---

### Task 10: Create `NewAdapterBridge` + update `JSTypeAdapterRegistry.Impl`

**Files:**
- Create: `common/src/main/java/com/tkisor/nekojs/core/bridge/NewAdapterBridge.java`
- Test: `common/src/test/java/com/tkisor/nekojs/core/bridge/NewAdapterBridgeTest.java`
- Modify: `common/src/main/java/com/tkisor/nekojs/api/data/JSTypeAdapterRegistry.java` (lines 50-67, the `Impl` inner class)

**Interfaces:**
- Consumes: `JSTypeAdapter` (existing), `JsTypeAdapter` (from Task 6), `GraalValueView` (from Task 8)
- Produces: `NewAdapterBridge<T>` — wraps new `JsTypeAdapter<T>` as old `JSTypeAdapter<T>`, enabling runtime to accept new adapters

- [ ] **Step 1: Write failing test**

```java
package com.tkisor.nekojs.core.bridge;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.*;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.HostAccess;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NewAdapterBridgeTest {

    private Context context;

    @BeforeEach
    void setUp() {
        context = Context.newBuilder("js").allowAllAccess(true).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void getTargetClassDelegates() {
        JsTypeAdapter<Integer> neo = new StringToIntAdapter();
        NewAdapterBridge<Integer> bridge = new NewAdapterBridge<>(neo);

        assertEquals(Integer.class, bridge.getTargetClass());
    }

    @Test
    void testDelegatesToSupports() {
        JsTypeAdapter<Integer> neo = new StringToIntAdapter();
        NewAdapterBridge<Integer> bridge = new NewAdapterBridge<>(neo);

        Value v = context.eval("js", "'42'");
        assertTrue(bridge.test(v));

        Value nonString = context.eval("js", "true");
        assertFalse(bridge.test(nonString));
    }

    @Test
    void applyDelegatesToConvert() {
        JsTypeAdapter<Integer> neo = new StringToIntAdapter();
        NewAdapterBridge<Integer> bridge = new NewAdapterBridge<>(neo);

        Value v = context.eval("js", "'100'");
        assertEquals(100, bridge.apply(v));
    }

    @Test
    void getPrecedenceMapsToHostAccess() {
        final class HighPrecAdapter implements JsTypeAdapter<Integer> {
            @Override public Class<Integer> targetType() { return Integer.class; }
            @Override public boolean supports(JsValueView v, ConversionContext c) { return v.isNumber(); }
            @Override public Integer convert(JsValueView v, ConversionContext c) { return v.asInt(); }
            @Override public ConversionPrecedence precedence() { return ConversionPrecedence.HIGH; }
        }

        NewAdapterBridge<Integer> bridge = new NewAdapterBridge<>(new HighPrecAdapter());
        assertEquals(HostAccess.TargetMappingPrecedence.HIGH, bridge.getPrecedence());
    }

    @Test
    void inputShapesDelegates() {
        JsTypeAdapter<Integer> neo = new StringToIntAdapter();
        NewAdapterBridge<Integer> bridge = new NewAdapterBridge<>(neo);

        assertEquals(2, bridge.inputShapes().size());
    }

    @Test
    void nullFromWrapReturnsNull() {
        JsTypeAdapter<Integer> neo = new StringToIntAdapter();
        NewAdapterBridge<Integer> bridge = new NewAdapterBridge<>(neo);

        Value v = context.eval("js", "null");
        assertTrue(v.isNull());
        assertNull(bridge.apply(v));
    }

    private static final class StringToIntAdapter extends AbstractJsTypeAdapter<Integer> {
        StringToIntAdapter() { super(Integer.class); }
        @Override protected Integer fromString(String s) { return Integer.parseInt(s); }
        @Override protected Integer fromHostObject(Object host) {
            return host instanceof Number n ? n.intValue() : null;
        }
        @Override public List<AdapterInputShape> inputShapes() {
            return List.of(AdapterInputShape.string(), AdapterInputShape.number());
        }
    }
}
```

- [ ] **Step 2: Verify test fails**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'; .\gradlew.bat :common:test --no-daemon --tests "com.tkisor.nekojs.core.bridge.NewAdapterBridgeTest"
```

Expected: FAIL

- [ ] **Step 3: Write `NewAdapterBridge.java`**

```java
package com.tkisor.nekojs.core.bridge;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.*;
import graal.graalvm.polyglot.HostAccess;
import graal.graalvm.polyglot.Value;

import java.util.List;
import java.util.Objects;

public final class NewAdapterBridge<T> implements JSTypeAdapter<T> {
    private final JsTypeAdapter<T> delegate;

    public NewAdapterBridge(JsTypeAdapter<T> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public Class<T> getTargetClass() {
        return delegate.targetType();
    }

    @Override
    public boolean test(Value value) {
        JsValueView view = GraalValueView.wrap(value);
        if (view == null) return false;
        return delegate.supports(view, ConversionContext.empty());
    }

    @Override
    public T apply(Value value) {
        JsValueView view = GraalValueView.wrap(value);
        if (view == null) return null;
        return delegate.convert(view, ConversionContext.empty());
    }

    @Override
    public HostAccess.TargetMappingPrecedence getPrecedence() {
        return switch (delegate.precedence()) {
            case LOWEST -> HostAccess.TargetMappingPrecedence.LOWEST;
            case LOW -> HostAccess.TargetMappingPrecedence.LOW;
            case HIGH -> HostAccess.TargetMappingPrecedence.HIGH;
            case HIGHEST -> HostAccess.TargetMappingPrecedence.HIGHEST;
        };
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return delegate.inputShapes();
    }
}
```

- [ ] **Step 4: Run tests to verify pass**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'; .\gradlew.bat :common:test --no-daemon --tests "com.tkisor.nekojs.core.bridge.NewAdapterBridgeTest"
```

Expected: all 6 tests PASS

- [ ] **Step 5: Update `JSTypeAdapterRegistry.Impl` to accept new adapters**

Add a convenience method to the `Impl` inner class. Read `JSTypeAdapterRegistry.java` and add this method inside `final class Impl`:

```java
public <T> void register(JsTypeAdapter<T> adapter) {
    register(new NewAdapterBridge<>(adapter));
}
```

This requires adding an import to the file: `import com.tkisor.nekojs.core.bridge.NewAdapterBridge;` (or use the FQN).

Full replacement of the `Impl` class (lines ~50-67):

```java
    final class Impl implements JSTypeAdapterRegistry {
        private final List<JSTypeAdapter<?>> adapters = new ArrayList<>();

        @Override
        public <T> void register(JSTypeAdapter<T> adapter) {
            adapters.add(Objects.requireNonNull(adapter, "adapter"));
        }

        public <T> void register(com.tkisor.nekojs.api.data.JsTypeAdapter<T> adapter) {
            adapters.add(Objects.requireNonNull(
                new com.tkisor.nekojs.core.bridge.NewAdapterBridge<>(adapter), "adapter"));
        }

        @Override
        public Collection<JSTypeAdapter<?>> view() {
            return Collections.unmodifiableList(adapters);
        }
    }
```

Note: Use FQN for `NewAdapterBridge` and `JsTypeAdapter` to avoid adding circular-ish imports to `JSTypeAdapterRegistry`.

- [ ] **Step 6: Write integration test verifying registry accepts new adapters**

Add a test class: `common/src/test/java/com/tkisor/nekojs/core/bridge/RegistryBridgeIntegrationTest.java`

```java
package com.tkisor.nekojs.core.bridge;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.*;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class RegistryBridgeIntegrationTest {

    private Context context;

    @BeforeEach
    void setUp() {
        context = Context.newBuilder("js").allowAllAccess(true).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void registerNewAdapterAndUseViaLegacyView() {
        JSTypeAdapterRegistry.Impl registry = new JSTypeAdapterRegistry.Impl();

        JsTypeAdapter<String> neo = new AbstractJsTypeAdapter<>(String.class) {
            @Override protected String fromString(String s) { return "[" + s + "]"; }
            @Override protected String fromHostObject(Object host) { return host.toString(); }
        };

        registry.register(neo);
        Collection<JSTypeAdapter<?>> view = registry.view();
        assertEquals(1, view.size());

        JSTypeAdapter<?> legacy = view.iterator().next();
        Value v = context.eval("js", "'hello'");
        assertTrue(legacy.test(v));
        assertEquals("[hello]", legacy.apply(v));
    }

    @Test
    void oldAndNewAdaptersCoexistInSameRegistry() {
        JSTypeAdapterRegistry.Impl registry = new JSTypeAdapterRegistry.Impl();

        registry.register(new AbstractJsTypeAdapter<>(Integer.class) {
            @Override protected Integer fromString(String s) { return Integer.parseInt(s); }
            @Override protected Integer fromHostObject(Object host) { return 0; }
        });

        registry.register(Integer.class,
            v -> v.isNumber(),
            v -> v.asInt() * 2);

        assertEquals(2, registry.view().size());
    }
}
```

- [ ] **Step 7: Run integration tests**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'; .\gradlew.bat :common:test --no-daemon --tests "com.tkisor.nekojs.core.bridge.RegistryBridgeIntegrationTest"
```

Expected: both tests PASS

- [ ] **Step 8: Commit**

```powershell
git add common/src/main/java/com/tkisor/nekojs/core/bridge/NewAdapterBridge.java common/src/main/java/com/tkisor/nekojs/api/data/JSTypeAdapterRegistry.java common/src/test/java/com/tkisor/nekojs/core/bridge/NewAdapterBridgeTest.java common/src/test/java/com/tkisor/nekojs/core/bridge/RegistryBridgeIntegrationTest.java
git commit -m "feat(core): add NewAdapterBridge and registry integration for JsTypeAdapter"
```

---

### Task 11: Update `AdapterCatalogEntry` to use `ConversionPrecedence`

**Files:**
- Modify: `common/src/main/java/com/tkisor/nekojs/api/catalog/AdapterCatalogEntry.java`
- Modify: all callers that create `AdapterCatalogEntry` (search required)

**Description:** Replace `HostAccess.TargetMappingPrecedence` with `ConversionPrecedence` in the record. Find and update all callers.

- [ ] **Step 1: Search for all callers creating AdapterCatalogEntry**

```powershell
rg "new AdapterCatalogEntry|AdapterCatalogEntry\.of" --include "*.java" common/src
```

- [ ] **Step 2: Update `AdapterCatalogEntry.java`**

Replace `HostAccess.TargetMappingPrecedence` → `ConversionPrecedence`:

```java
package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.ConversionPrecedence;

import java.util.List;
import java.util.Optional;

public record AdapterCatalogEntry(
        Class<?> targetType,
        List<AdapterInputShape> shapes,
        ConversionPrecedence precedence,
        Optional<String> syntaxDoc
) {
    public static AdapterCatalogEntry of(Class<?> targetType, ConversionPrecedence precedence) {
        return new AdapterCatalogEntry(targetType, List.of(), precedence, Optional.empty());
    }
}
```

Remove the Graal `import graal.graalvm.polyglot.HostAccess;` and add `import com.tkisor.nekojs.api.data.ConversionPrecedence;`.

- [ ] **Step 3: Find and fix all callers**

For each caller found in Step 1, update:
- `HostAccess.TargetMappingPrecedence.X` → `ConversionPrecedence.X`
- Remove `import graal.graalvm.polyglot.HostAccess;` if no other use

Use the mapping:
- `HostAccess.TargetMappingPrecedence.LOWEST` → `ConversionPrecedence.LOWEST`
- `HostAccess.TargetMappingPrecedence.LOW` → `ConversionPrecedence.LOW`
- `HostAccess.TargetMappingPrecedence.HIGH` → `ConversionPrecedence.HIGH`
- `HostAccess.TargetMappingPrecedence.HIGHEST` → `ConversionPrecedence.HIGHEST`

- [ ] **Step 4: Verify common compiles**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'; .\gradlew.bat :common:compileJava --no-daemon
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```powershell
git add -A
git commit -m "refactor: AdapterCatalogEntry uses ConversionPrecedence instead of HostAccess.TargetMappingPrecedence"
```

---

### Task 12: Verify Zero Graal Imports in common-api

**Files:**
- No create/modify. Verification only.

- [ ] **Step 1: Grep for Graal imports in common-api**

```powershell
rg "graal\." common-api/src --include "*.java"
```

Expected: EMPTY (no matches)

- [ ] **Step 2: Compile common-api**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'; .\gradlew.bat :common-api:compileJava --no-daemon
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Compile common**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'; .\gradlew.bat :common:compileJava --no-daemon
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run all common-api tests**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'; .\gradlew.bat :common-api:test --no-daemon
```

Expected: all tests PASS

- [ ] **Step 5: Run all common tests**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'; .\gradlew.bat :common:test --no-daemon
```

Expected: all tests PASS

- [ ] **Step 6: Compile all four platforms**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'; .\gradlew.bat :neoforge-26.1:compileJava :neoforge-26.2:compileJava :neoforge-1.21.1:compileJava :cleanroom-1.12.2:compileJava --no-daemon
```

Expected: BUILD SUCCESSFUL (all 4 platforms)

- [ ] **Step 7: Commit (if any fixes needed) or verify clean git status**

```powershell
git status
```

---

## Execution Notes

- **Step numbers may have gaps** in some tasks — this is intentional and matches the step numbering convention.
- **FQN references** in `JSTypeAdapterRegistry.Impl` are intentional to avoid adding imports for types in a different module/bridge pattern.
- After all tasks complete, the `common-api` module will contain 6 Graal-free SPI types and 2 relocated types, all ready for future `common-api` package migration.
