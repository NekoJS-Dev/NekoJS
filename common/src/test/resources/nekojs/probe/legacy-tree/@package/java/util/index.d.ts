import { $Serializable } from "java:java/io";
import { $Character, $Cloneable, $Double, $Enum, $Integer, $Iterable, $Long, $Runnable, $String } from "java:java/lang";
import { $BiConsumer, $BiFunction, $Consumer, $DoubleConsumer, $DoubleSupplier, $Function, $IntConsumer, $IntFunction, $IntSupplier, $LongConsumer, $LongSupplier, $Predicate, $Supplier, $ToDoubleFunction, $ToIntFunction, $ToLongFunction, $UnaryOperator } from "java:java/util/function";
import { $DoubleStream, $IntStream, $LongStream, $Stream } from "java:java/util/stream";

export * as function from "java:java/util/function";
export * as stream from "java:java/util/stream";

declare module "java:java/util" {
    export interface $Collection<E> extends $Iterable {
        addAll(arg0: E[]): boolean;
        add(arg0: E): boolean;
        clear(): void;
        containsAll(arg0: any[]): boolean;
        contains(arg0: object): boolean;
        equals(arg0: object): boolean;
        hashCode(): number;
        isEmpty(): boolean;
        iterator(): $Iterator<E>;
        parallelStream(): $Stream<E>;
        removeAll(arg0: any[]): boolean;
        removeIf(arg0: $Predicate<any>): boolean;
        remove(arg0: object): boolean;
        retainAll(arg0: any[]): boolean;
        size(): number;
        spliterator(): $Spliterator<E>;
        stream(): $Stream<E>;
        toArray<T>(arg0: T[]): T[];
        toArray<T>(arg0: $IntFunction<T[]>): T[];
        toArray(): object[];
    }

    export interface $Comparator<T> {
        compare(arg0: T, arg1: T): number;
        comparingDouble<T>(arg0: $ToDoubleFunction<any>): $Comparator<T>;
        comparingInt<T>(arg0: $ToIntFunction<any>): $Comparator<T>;
        comparingLong<T>(arg0: $ToLongFunction<any>): $Comparator<T>;
        comparing<T, U>(arg0: $Function<any, U>, arg1: $Comparator<any>): $Comparator<T>;
        comparing<T, U>(arg0: $Function<any, U>): $Comparator<T>;
        equals(arg0: object): boolean;
        naturalOrder<T>(): $Comparator<T>;
        nullsFirst<T>(arg0: $Comparator<any>): $Comparator<T>;
        nullsLast<T>(arg0: $Comparator<any>): $Comparator<T>;
        reverseOrder<T>(): $Comparator<T>;
        reversed(): $Comparator<T>;
        thenComparingDouble(arg0: $ToDoubleFunction<any>): $Comparator<T>;
        thenComparingInt(arg0: $ToIntFunction<any>): $Comparator<T>;
        thenComparingLong(arg0: $ToLongFunction<any>): $Comparator<T>;
        thenComparing(arg0: $Comparator<any>): $Comparator<T>;
        thenComparing<U>(arg0: $Function<any, U>, arg1: $Comparator<any>): $Comparator<T>;
        thenComparing<U>(arg0: $Function<any, U>): $Comparator<T>;
    }

    export class $DoubleSummaryStatistics implements $DoubleConsumer {
        constructor();
        constructor(arg0: number, arg1: number, arg2: number, arg3: number);
        get average(): number;
        getAverage(): number;
        get count(): number;
        getCount(): number;
        get max(): number;
        getMax(): number;
        get min(): number;
        getMin(): number;
        get sum(): number;
        getSum(): number;
        accept(arg0: number): void;
        combine(arg0: $DoubleSummaryStatistics): void;
        toString(): string;
    }

    export interface $Enumeration<E> {
        asIterator(): $Iterator<E>;
        hasMoreElements(): boolean;
        nextElement(): E;
    }

    export class $IntSummaryStatistics implements $IntConsumer {
        constructor();
        constructor(arg0: number, arg1: number, arg2: number, arg3: number);
        get average(): number;
        getAverage(): number;
        get count(): number;
        getCount(): number;
        get max(): number;
        getMax(): number;
        get min(): number;
        getMin(): number;
        get sum(): number;
        getSum(): number;
        accept(arg0: number): void;
        combine(arg0: $IntSummaryStatistics): void;
        toString(): string;
    }

    export interface $Iterator<E> {
        forEachRemaining(arg0: $Consumer<any>): void;
        hasNext(): boolean;
        next(): E;
        remove(): void;
    }

    export interface $List<E> extends $SequencedCollection {
        addAll(arg0: number, arg1: E[]): boolean;
        addAll(arg0: E[]): boolean;
        addFirst(arg0: E): void;
        addLast(arg0: E): void;
        add(arg0: E): boolean;
        add(arg0: number, arg1: E): void;
        clear(): void;
        containsAll(arg0: any[]): boolean;
        contains(arg0: object): boolean;
        copyOf<E>(arg0: E[]): $List<E>;
        equals(arg0: object): boolean;
        getFirst(): E;
        getLast(): E;
        get(arg0: number): E;
        hashCode(): number;
        indexOf(arg0: object): number;
        isEmpty(): boolean;
        iterator(): $Iterator<E>;
        lastIndexOf(arg0: object): number;
        listIterator(arg0: number): $ListIterator<E>;
        listIterator(): $ListIterator<E>;
        of<E>(arg0: E, arg1: E, arg2: E, arg3: E, arg4: E, arg5: E, arg6: E, arg7: E, arg8: E, arg9: E): $List<E>;
        of<E>(arg0: E, arg1: E, arg2: E, arg3: E, arg4: E, arg5: E, arg6: E, arg7: E, arg8: E): $List<E>;
        of<E>(arg0: E, arg1: E, arg2: E, arg3: E, arg4: E, arg5: E, arg6: E, arg7: E): $List<E>;
        of<E>(arg0: E, arg1: E, arg2: E, arg3: E, arg4: E, arg5: E, arg6: E): $List<E>;
        of<E>(arg0: E, arg1: E, arg2: E, arg3: E, arg4: E, arg5: E): $List<E>;
        of<E>(arg0: E, arg1: E, arg2: E, arg3: E, arg4: E): $List<E>;
        of<E>(arg0: E, arg1: E, arg2: E, arg3: E): $List<E>;
        of<E>(arg0: E, arg1: E, arg2: E): $List<E>;
        of<E>(arg0: E, arg1: E): $List<E>;
        of<E>(arg0: E): $List<E>;
        of<E>(arg0?: object[]): $List<E>;
        of<E>(): $List<E>;
        removeAll(arg0: any[]): boolean;
        removeFirst(): E;
        removeLast(): E;
        remove(arg0: number): E;
        remove(arg0: object): boolean;
        replaceAll(arg0: $UnaryOperator<E>): void;
        retainAll(arg0: any[]): boolean;
        reversed(): $List<E>;
        reversed(): $SequencedCollection;
        set(arg0: number, arg1: E): E;
        size(): number;
        sort(arg0: $Comparator<any>): void;
        spliterator(): $Spliterator<E>;
        subList(arg0: number, arg1: number): $List<E>;
        toArray<T>(arg0: T[]): T[];
        toArray(): object[];
    }

    export interface $ListIterator<E> extends $Iterator {
        add(arg0: E): void;
        hasNext(): boolean;
        hasPrevious(): boolean;
        nextIndex(): number;
        next(): E;
        previousIndex(): number;
        previous(): E;
        remove(): void;
        set(arg0: E): void;
    }

    export class $Locale implements $Cloneable, $Serializable {
        constructor(arg0: string);
        constructor(arg0: string, arg1: string);
        constructor(arg0: string, arg1: string, arg2: string);
        static CANADA: $Locale;
        static CANADA_FRENCH: $Locale;
        static CHINA: $Locale;
        static CHINESE: $Locale;
        static ENGLISH: $Locale;
        static FRANCE: $Locale;
        static FRENCH: $Locale;
        static GERMAN: $Locale;
        static GERMANY: $Locale;
        static ITALIAN: $Locale;
        static ITALY: $Locale;
        static JAPAN: $Locale;
        static JAPANESE: $Locale;
        static KOREA: $Locale;
        static KOREAN: $Locale;
        static PRC: $Locale;
        static PRIVATE_USE_EXTENSION: string;
        static ROOT: $Locale;
        static SIMPLIFIED_CHINESE: $Locale;
        static TAIWAN: $Locale;
        static TRADITIONAL_CHINESE: $Locale;
        static UK: $Locale;
        static UNICODE_LOCALE_EXTENSION: string;
        static US: $Locale;
        get country(): string;
        getCountry(): string;
        get displayCountry(): string;
        getDisplayCountry(): string;
        get displayLanguage(): string;
        getDisplayLanguage(): string;
        get displayName(): string;
        getDisplayName(): string;
        get displayScript(): string;
        getDisplayScript(): string;
        get displayVariant(): string;
        getDisplayVariant(): string;
        get extensionKeys(): $Set<$Character>;
        getExtensionKeys(): $Set<$Character>;
        get iSO3Country(): string;
        getISO3Country(): string;
        get iSO3Language(): string;
        getISO3Language(): string;
        get language(): string;
        getLanguage(): string;
        get script(): string;
        getScript(): string;
        get unicodeLocaleAttributes(): $Set<string>;
        getUnicodeLocaleAttributes(): $Set<string>;
        get unicodeLocaleKeys(): $Set<string>;
        getUnicodeLocaleKeys(): $Set<string>;
        get variant(): string;
        getVariant(): string;
        static availableLocales(): $Stream<$Locale>;
        static caseFoldLanguageTag(arg0: string): string;
        static filterTags(arg0: $Locale$LanguageRange[], arg1: string[], arg2: $Locale$FilteringMode): $List<string>;
        static filterTags(arg0: $Locale$LanguageRange[], arg1: string[]): $List<string>;
        static filter(arg0: $Locale$LanguageRange[], arg1: $Locale[], arg2: $Locale$FilteringMode): $List<$Locale>;
        static filter(arg0: $Locale$LanguageRange[], arg1: $Locale[]): $List<$Locale>;
        static forLanguageTag(arg0: string): $Locale;
        static getAvailableLocales(): $Locale[];
        static getDefault(arg0: $Locale$Category): $Locale;
        static getDefault(): $Locale;
        static getISOCountries(arg0: $Locale$IsoCountryCode): $Set<string>;
        static getISOCountries(): string[];
        static getISOLanguages(): string[];
        static lookupTag(arg0: $Locale$LanguageRange[], arg1: string[]): string;
        static lookup(arg0: $Locale$LanguageRange[], arg1: $Locale[]): $Locale;
        static of(arg0: string, arg1: string, arg2: string): $Locale;
        static of(arg0: string, arg1: string): $Locale;
        static of(arg0: string): $Locale;
        static setDefault(arg0: $Locale$Category, arg1: $Locale): void;
        static setDefault(arg0: $Locale): void;
        clone(): object;
        equals(arg0: object): boolean;
        getDisplayCountry(arg0: $Locale): string;
        getDisplayLanguage(arg0: $Locale): string;
        getDisplayName(arg0: $Locale): string;
        getDisplayScript(arg0: $Locale): string;
        getDisplayVariant(arg0: $Locale): string;
        getExtension(arg0: string): string;
        getUnicodeLocaleType(arg0: string): string;
        hasExtensions(): boolean;
        hashCode(): number;
        stripExtensions(): $Locale;
        toLanguageTag(): string;
        toString(): string;
    }

    export class $Locale$Category {
        static DISPLAY: $Locale$Category;
        static FORMAT: $Locale$Category;
        name(): string;
        ordinal(): number;
        toString(): string;
        static values(): $Locale$Category[];
        static valueOf(name: string): $Locale$Category;
    }

    export class $Locale$FilteringMode {
        static AUTOSELECT_FILTERING: $Locale$FilteringMode;
        static EXTENDED_FILTERING: $Locale$FilteringMode;
        static IGNORE_EXTENDED_RANGES: $Locale$FilteringMode;
        static MAP_EXTENDED_RANGES: $Locale$FilteringMode;
        static REJECT_EXTENDED_RANGES: $Locale$FilteringMode;
        name(): string;
        ordinal(): number;
        toString(): string;
        static values(): $Locale$FilteringMode[];
        static valueOf(name: string): $Locale$FilteringMode;
    }

    export class $Locale$IsoCountryCode {
        static PART1_ALPHA2: $Locale$IsoCountryCode;
        static PART1_ALPHA3: $Locale$IsoCountryCode;
        static PART3: $Locale$IsoCountryCode;
        name(): string;
        ordinal(): number;
        toString(): string;
        static values(): $Locale$IsoCountryCode[];
        static valueOf(name: string): $Locale$IsoCountryCode;
    }

    export class $Locale$LanguageRange {
        constructor(arg0: string);
        constructor(arg0: string, arg1: number);
        static MAX_WEIGHT: number;
        static MIN_WEIGHT: number;
        get range(): string;
        getRange(): string;
        get weight(): number;
        getWeight(): number;
        static mapEquivalents(arg0: $Locale$LanguageRange[], arg1: { [key: string]: string[] }): $List<$Locale$LanguageRange>;
        static parse(arg0: string, arg1: { [key: string]: string[] }): $List<$Locale$LanguageRange>;
        static parse(arg0: string): $List<$Locale$LanguageRange>;
        equals(arg0: object): boolean;
        hashCode(): number;
        toString(): string;
    }

    export class $LongSummaryStatistics implements $LongConsumer, $IntConsumer {
        constructor();
        constructor(arg0: number, arg1: number, arg2: number, arg3: number);
        get average(): number;
        getAverage(): number;
        get count(): number;
        getCount(): number;
        get max(): number;
        getMax(): number;
        get min(): number;
        getMin(): number;
        get sum(): number;
        getSum(): number;
        accept(arg0: number): void;
        accept(arg0: number): void;
        combine(arg0: $LongSummaryStatistics): void;
        toString(): string;
    }

    export interface $Map<K, V> {
        clear(): void;
        computeIfAbsent(arg0: K, arg1: $Function<any, V>): V;
        computeIfPresent(arg0: K, arg1: $BiFunction<any, any, V>): V;
        compute(arg0: K, arg1: $BiFunction<any, any, V>): V;
        containsKey(arg0: object): boolean;
        containsValue(arg0: object): boolean;
        copyOf<K, V>(arg0: { [key: K]: V }): $Map<K, V>;
        entrySet(): $Set<$Map$Entry<K, V>>;
        entry<K, V>(arg0: K, arg1: V): $Map$Entry<K, V>;
        equals(arg0: object): boolean;
        forEach(arg0: $BiConsumer<any, any>): void;
        getOrDefault(arg0: object, arg1: V): V;
        get(arg0: object): V;
        hashCode(): number;
        isEmpty(): boolean;
        keySet(): $Set<K>;
        merge(arg0: K, arg1: V, arg2: $BiFunction<any, any, V>): V;
        ofEntries<K, V>(arg0?: $Map$Entry[]): $Map<K, V>;
        of<K, V>(arg0: K, arg1: V, arg2: K, arg3: V, arg4: K, arg5: V, arg6: K, arg7: V, arg8: K, arg9: V, arg10: K, arg11: V, arg12: K, arg13: V, arg14: K, arg15: V, arg16: K, arg17: V, arg18: K, arg19: V): $Map<K, V>;
        of<K, V>(arg0: K, arg1: V, arg2: K, arg3: V, arg4: K, arg5: V, arg6: K, arg7: V, arg8: K, arg9: V, arg10: K, arg11: V, arg12: K, arg13: V, arg14: K, arg15: V, arg16: K, arg17: V): $Map<K, V>;
        of<K, V>(arg0: K, arg1: V, arg2: K, arg3: V, arg4: K, arg5: V, arg6: K, arg7: V, arg8: K, arg9: V, arg10: K, arg11: V, arg12: K, arg13: V, arg14: K, arg15: V): $Map<K, V>;
        of<K, V>(arg0: K, arg1: V, arg2: K, arg3: V, arg4: K, arg5: V, arg6: K, arg7: V, arg8: K, arg9: V, arg10: K, arg11: V, arg12: K, arg13: V): $Map<K, V>;
        of<K, V>(arg0: K, arg1: V, arg2: K, arg3: V, arg4: K, arg5: V, arg6: K, arg7: V, arg8: K, arg9: V, arg10: K, arg11: V): $Map<K, V>;
        of<K, V>(arg0: K, arg1: V, arg2: K, arg3: V, arg4: K, arg5: V, arg6: K, arg7: V, arg8: K, arg9: V): $Map<K, V>;
        of<K, V>(arg0: K, arg1: V, arg2: K, arg3: V, arg4: K, arg5: V, arg6: K, arg7: V): $Map<K, V>;
        of<K, V>(arg0: K, arg1: V, arg2: K, arg3: V, arg4: K, arg5: V): $Map<K, V>;
        of<K, V>(arg0: K, arg1: V, arg2: K, arg3: V): $Map<K, V>;
        of<K, V>(arg0: K, arg1: V): $Map<K, V>;
        of<K, V>(): $Map<K, V>;
        putAll(arg0: { [key: K]: V }): void;
        putIfAbsent(arg0: K, arg1: V): V;
        put(arg0: K, arg1: V): V;
        remove(arg0: object, arg1: object): boolean;
        remove(arg0: object): V;
        replaceAll(arg0: $BiFunction<any, any, V>): void;
        replace(arg0: K, arg1: V, arg2: V): boolean;
        replace(arg0: K, arg1: V): V;
        size(): number;
        values(): $Collection<V>;
    }

    export interface $Map$Entry<K, V> {
        comparingByKey<K, V>(arg0: $Comparator<any>): $Comparator<$Map$Entry<K, V>>;
        comparingByKey<K, V>(): $Comparator<$Map$Entry<K, V>>;
        comparingByValue<K, V>(arg0: $Comparator<any>): $Comparator<$Map$Entry<K, V>>;
        comparingByValue<K, V>(): $Comparator<$Map$Entry<K, V>>;
        copyOf<K, V>(arg0: $Map$Entry<K, V>): $Map$Entry<K, V>;
        equals(arg0: object): boolean;
        getKey(): K;
        getValue(): V;
        hashCode(): number;
        setValue(arg0: V): V;
    }

    export class $Optional<T> {
        get empty(): boolean;
        isEmpty(): boolean;
        get present(): boolean;
        isPresent(): boolean;
        static empty<T>(): $Optional<T>;
        static ofNullable<T>(arg0: T): $Optional<T>;
        static of<T>(arg0: T): $Optional<T>;
        equals(arg0: object): boolean;
        filter(arg0: $Predicate<any>): $Optional<T>;
        flatMap<U>(arg0: $Function<any, $Optional<U>>): $Optional<U>;
        get(): T;
        hashCode(): number;
        ifPresentOrElse(arg0: $Consumer<any>, arg1: $Runnable): void;
        ifPresent(arg0: $Consumer<any>): void;
        map<U>(arg0: $Function<any, U>): $Optional<U>;
        orElseGet(arg0: $Supplier<T>): T;
        orElseThrow<X>(arg0: $Supplier<X>): T;
        orElseThrow(): T;
        orElse(arg0: T): T;
        or(arg0: $Supplier<$Optional<T>>): $Optional<T>;
        stream(): $Stream<T>;
        toString(): string;
    }

    export class $OptionalDouble {
        get asDouble(): number;
        getAsDouble(): number;
        get empty(): boolean;
        isEmpty(): boolean;
        get present(): boolean;
        isPresent(): boolean;
        static empty(): $OptionalDouble;
        static of(arg0: number): $OptionalDouble;
        equals(arg0: object): boolean;
        hashCode(): number;
        ifPresentOrElse(arg0: $DoubleConsumer, arg1: $Runnable): void;
        ifPresent(arg0: $DoubleConsumer): void;
        orElseGet(arg0: $DoubleSupplier): number;
        orElseThrow<X>(arg0: $Supplier<X>): number;
        orElseThrow(): number;
        orElse(arg0: number): number;
        stream(): $DoubleStream;
        toString(): string;
    }

    export class $OptionalInt {
        get asInt(): number;
        getAsInt(): number;
        get empty(): boolean;
        isEmpty(): boolean;
        get present(): boolean;
        isPresent(): boolean;
        static empty(): $OptionalInt;
        static of(arg0: number): $OptionalInt;
        equals(arg0: object): boolean;
        hashCode(): number;
        ifPresentOrElse(arg0: $IntConsumer, arg1: $Runnable): void;
        ifPresent(arg0: $IntConsumer): void;
        orElseGet(arg0: $IntSupplier): number;
        orElseThrow<X>(arg0: $Supplier<X>): number;
        orElseThrow(): number;
        orElse(arg0: number): number;
        stream(): $IntStream;
        toString(): string;
    }

    export class $OptionalLong {
        get asLong(): number;
        getAsLong(): number;
        get empty(): boolean;
        isEmpty(): boolean;
        get present(): boolean;
        isPresent(): boolean;
        static empty(): $OptionalLong;
        static of(arg0: number): $OptionalLong;
        equals(arg0: object): boolean;
        hashCode(): number;
        ifPresentOrElse(arg0: $LongConsumer, arg1: $Runnable): void;
        ifPresent(arg0: $LongConsumer): void;
        orElseGet(arg0: $LongSupplier): number;
        orElseThrow<X>(arg0: $Supplier<X>): number;
        orElseThrow(): number;
        orElse(arg0: number): number;
        stream(): $LongStream;
        toString(): string;
    }

    export interface $PrimitiveIterator<T, T_CONS> extends $Iterator {
        forEachRemaining(arg0: T_CONS): void;
    }

    export interface $PrimitiveIterator$OfDouble extends $PrimitiveIterator {
        forEachRemaining(arg0: object): void;
        forEachRemaining(arg0: $Consumer<any>): void;
        forEachRemaining(arg0: $DoubleConsumer): void;
        nextDouble(): number;
        next(): number;
        next(): object;
    }

    export interface $PrimitiveIterator$OfInt extends $PrimitiveIterator {
        forEachRemaining(arg0: object): void;
        forEachRemaining(arg0: $Consumer<any>): void;
        forEachRemaining(arg0: $IntConsumer): void;
        nextInt(): number;
        next(): number;
        next(): object;
    }

    export interface $PrimitiveIterator$OfLong extends $PrimitiveIterator {
        forEachRemaining(arg0: object): void;
        forEachRemaining(arg0: $Consumer<any>): void;
        forEachRemaining(arg0: $LongConsumer): void;
        nextLong(): number;
        next(): number;
        next(): object;
    }

    export interface $SequencedCollection<E> extends $Collection {
        addFirst(arg0: E): void;
        addLast(arg0: E): void;
        getFirst(): E;
        getLast(): E;
        removeFirst(): E;
        removeLast(): E;
        reversed(): $SequencedCollection<E>;
    }

    export interface $SequencedMap<K, V> extends $Map {
        firstEntry(): $Map$Entry<K, V>;
        lastEntry(): $Map$Entry<K, V>;
        pollFirstEntry(): $Map$Entry<K, V>;
        pollLastEntry(): $Map$Entry<K, V>;
        putFirst(arg0: K, arg1: V): V;
        putLast(arg0: K, arg1: V): V;
        reversed(): $SequencedMap<K, V>;
        sequencedEntrySet(): $SequencedSet<$Map$Entry<K, V>>;
        sequencedKeySet(): $SequencedSet<K>;
        sequencedValues(): $SequencedCollection<V>;
    }

    export interface $SequencedSet<E> extends $SequencedCollection, $Set {
        reversed(): $SequencedCollection;
        reversed(): $SequencedSet<E>;
    }

    export interface $Set<E> extends $Collection {
        addAll(arg0: E[]): boolean;
        add(arg0: E): boolean;
        clear(): void;
        containsAll(arg0: any[]): boolean;
        contains(arg0: object): boolean;
        copyOf<E>(arg0: E[]): $Set<E>;
        equals(arg0: object): boolean;
        hashCode(): number;
        isEmpty(): boolean;
        iterator(): $Iterator<E>;
        of<E>(arg0: E, arg1: E, arg2: E, arg3: E, arg4: E, arg5: E, arg6: E, arg7: E, arg8: E, arg9: E): $Set<E>;
        of<E>(arg0: E, arg1: E, arg2: E, arg3: E, arg4: E, arg5: E, arg6: E, arg7: E, arg8: E): $Set<E>;
        of<E>(arg0: E, arg1: E, arg2: E, arg3: E, arg4: E, arg5: E, arg6: E, arg7: E): $Set<E>;
        of<E>(arg0: E, arg1: E, arg2: E, arg3: E, arg4: E, arg5: E, arg6: E): $Set<E>;
        of<E>(arg0: E, arg1: E, arg2: E, arg3: E, arg4: E, arg5: E): $Set<E>;
        of<E>(arg0: E, arg1: E, arg2: E, arg3: E, arg4: E): $Set<E>;
        of<E>(arg0: E, arg1: E, arg2: E, arg3: E): $Set<E>;
        of<E>(arg0: E, arg1: E, arg2: E): $Set<E>;
        of<E>(arg0: E, arg1: E): $Set<E>;
        of<E>(arg0: E): $Set<E>;
        of<E>(arg0?: object[]): $Set<E>;
        of<E>(): $Set<E>;
        removeAll(arg0: any[]): boolean;
        remove(arg0: object): boolean;
        retainAll(arg0: any[]): boolean;
        size(): number;
        spliterator(): $Spliterator<E>;
        toArray<T>(arg0: T[]): T[];
        toArray(): object[];
    }

    export interface $SortedMap<K, V> extends $SequencedMap {
        comparator(): $Comparator<any>;
        entrySet(): $Set<$Map$Entry<K, V>>;
        firstKey(): K;
        headMap(arg0: K): $SortedMap<K, V>;
        keySet(): $Set<K>;
        lastKey(): K;
        putFirst(arg0: K, arg1: V): V;
        putLast(arg0: K, arg1: V): V;
        reversed(): $SequencedMap;
        reversed(): $SortedMap<K, V>;
        subMap(arg0: K, arg1: K): $SortedMap<K, V>;
        tailMap(arg0: K): $SortedMap<K, V>;
        values(): $Collection<V>;
    }

    export interface $Spliterator<T> {
        characteristics(): number;
        estimateSize(): number;
        forEachRemaining(arg0: $Consumer<any>): void;
        getComparator(): $Comparator<any>;
        getExactSizeIfKnown(): number;
        hasCharacteristics(arg0: number): boolean;
        tryAdvance(arg0: $Consumer<any>): boolean;
        trySplit(): $Spliterator<T>;
        static CONCURRENT: number;
        static DISTINCT: number;
        static IMMUTABLE: number;
        static NONNULL: number;
        static ORDERED: number;
        static SIZED: number;
        static SORTED: number;
        static SUBSIZED: number;
    }

    export interface $Spliterator$OfDouble extends $Spliterator$OfPrimitive {
        forEachRemaining(arg0: object): void;
        forEachRemaining(arg0: $Consumer<any>): void;
        forEachRemaining(arg0: $DoubleConsumer): void;
        tryAdvance(arg0: object): boolean;
        tryAdvance(arg0: $Consumer<any>): boolean;
        tryAdvance(arg0: $DoubleConsumer): boolean;
        trySplit(): $Spliterator;
        trySplit(): $Spliterator$OfDouble;
        trySplit(): $Spliterator$OfPrimitive;
    }

    export interface $Spliterator$OfInt extends $Spliterator$OfPrimitive {
        forEachRemaining(arg0: object): void;
        forEachRemaining(arg0: $Consumer<any>): void;
        forEachRemaining(arg0: $IntConsumer): void;
        tryAdvance(arg0: object): boolean;
        tryAdvance(arg0: $Consumer<any>): boolean;
        tryAdvance(arg0: $IntConsumer): boolean;
        trySplit(): $Spliterator;
        trySplit(): $Spliterator$OfInt;
        trySplit(): $Spliterator$OfPrimitive;
    }

    export interface $Spliterator$OfLong extends $Spliterator$OfPrimitive {
        forEachRemaining(arg0: object): void;
        forEachRemaining(arg0: $Consumer<any>): void;
        forEachRemaining(arg0: $LongConsumer): void;
        tryAdvance(arg0: object): boolean;
        tryAdvance(arg0: $Consumer<any>): boolean;
        tryAdvance(arg0: $LongConsumer): boolean;
        trySplit(): $Spliterator;
        trySplit(): $Spliterator$OfLong;
        trySplit(): $Spliterator$OfPrimitive;
    }

    export interface $Spliterator$OfPrimitive<T, T_CONS, T_SPLITR extends $Spliterator$OfPrimitive<T, T_CONS, T_SPLITR>> extends $Spliterator {
        forEachRemaining(arg0: T_CONS): void;
        tryAdvance(arg0: T_CONS): boolean;
        trySplit(): T_SPLITR;
        trySplit(): $Spliterator;
    }

    export type $Collection_<E> = E[];
    export type $Iterator_<E> = E[];
    export type $List_<E> = E[];
    export type $Map_<K, V> = { [key: string]: V };
    export type $Optional_<T> = T | null;
    export type $SequencedCollection_<E> = E[];
    export type $SequencedMap_<K, V> = { [key: string]: V };
    export type $SequencedSet_<E> = E[];
    export type $Set_<E> = E[];
    export type $SortedMap_<K, V> = { [key: string]: V };
    export type $Spliterator_<T> = T[];
}
