



































        accept(arg0: number): void;
        accept(arg0: number): void;
        accept(arg0: number): void;
        accept(arg0: number): void;
        add(arg0: E): boolean;
        add(arg0: E): boolean;
        add(arg0: E): boolean;
        add(arg0: E): void;
        add(arg0: number, arg1: E): void;
        addAll(arg0: E[]): boolean;
        addAll(arg0: E[]): boolean;
        addAll(arg0: E[]): boolean;
        addAll(arg0: number, arg1: E[]): boolean;
        addFirst(arg0: E): void;
        addFirst(arg0: E): void;
        addLast(arg0: E): void;
        addLast(arg0: E): void;
        asIterator(): $Iterator<E>;
        characteristics(): number;
        clear(): void;
        clear(): void;
        clear(): void;
        clear(): void;
        clone(): object;
        combine(arg0: $DoubleSummaryStatistics): void;
        combine(arg0: $IntSummaryStatistics): void;
        combine(arg0: $LongSummaryStatistics): void;
        comparator(): $Comparator<any>;
        compare(arg0: T, arg1: T): number;
        comparing<T, U>(arg0: $Function<any, U>): $Comparator<T>;
        comparing<T, U>(arg0: $Function<any, U>, arg1: $Comparator<any>): $Comparator<T>;
        comparingByKey<K, V>(): $Comparator<$Map$Entry<K, V>>;
        comparingByKey<K, V>(arg0: $Comparator<any>): $Comparator<$Map$Entry<K, V>>;
        comparingByValue<K, V>(): $Comparator<$Map$Entry<K, V>>;
        comparingByValue<K, V>(arg0: $Comparator<any>): $Comparator<$Map$Entry<K, V>>;
        comparingDouble<T>(arg0: $ToDoubleFunction<any>): $Comparator<T>;
        comparingInt<T>(arg0: $ToIntFunction<any>): $Comparator<T>;
        comparingLong<T>(arg0: $ToLongFunction<any>): $Comparator<T>;
        compute(arg0: K, arg1: $BiFunction<any, any, V>): V;
        computeIfAbsent(arg0: K, arg1: $Function<any, V>): V;
        computeIfPresent(arg0: K, arg1: $BiFunction<any, any, V>): V;
        constructor();
        constructor();
        constructor();
        constructor(arg0: number, arg1: number, arg2: number, arg3: number);
        constructor(arg0: number, arg1: number, arg2: number, arg3: number);
        constructor(arg0: number, arg1: number, arg2: number, arg3: number);
        constructor(arg0: string);
        constructor(arg0: string);
        constructor(arg0: string, arg1: number);
        constructor(arg0: string, arg1: string);
        constructor(arg0: string, arg1: string, arg2: string);
        contains(arg0: object): boolean;
        contains(arg0: object): boolean;
        contains(arg0: object): boolean;
        containsAll(arg0: any[]): boolean;
        containsAll(arg0: any[]): boolean;
        containsAll(arg0: any[]): boolean;
        containsKey(arg0: object): boolean;
        containsValue(arg0: object): boolean;
        copyOf<E>(arg0: E[]): $List<E>;
        copyOf<E>(arg0: E[]): $Set<E>;
        copyOf<K, V>(arg0: $Map$Entry<K, V>): $Map$Entry<K, V>;
        copyOf<K, V>(arg0: { [key: K]: V }): $Map<K, V>;
        entry<K, V>(arg0: K, arg1: V): $Map$Entry<K, V>;
        entrySet(): $Set<$Map$Entry<K, V>>;
        entrySet(): $Set<$Map$Entry<K, V>>;
        equals(arg0: object): boolean;
        equals(arg0: object): boolean;
        equals(arg0: object): boolean;
        equals(arg0: object): boolean;
        equals(arg0: object): boolean;
        equals(arg0: object): boolean;
        equals(arg0: object): boolean;
        equals(arg0: object): boolean;
        equals(arg0: object): boolean;
        equals(arg0: object): boolean;
        equals(arg0: object): boolean;
        equals(arg0: object): boolean;
        estimateSize(): number;
        filter(arg0: $Predicate<any>): $Optional<T>;
        firstEntry(): $Map$Entry<K, V>;
        firstKey(): K;
        flatMap<U>(arg0: $Function<any, $Optional<U>>): $Optional<U>;
        forEach(arg0: $BiConsumer<any, any>): void;
        forEachRemaining(arg0: $Consumer<any>): void;
        forEachRemaining(arg0: $Consumer<any>): void;
        forEachRemaining(arg0: $Consumer<any>): void;
        forEachRemaining(arg0: $Consumer<any>): void;
        forEachRemaining(arg0: $Consumer<any>): void;
        forEachRemaining(arg0: $Consumer<any>): void;
        forEachRemaining(arg0: $Consumer<any>): void;
        forEachRemaining(arg0: $Consumer<any>): void;
        forEachRemaining(arg0: $DoubleConsumer): void;
        forEachRemaining(arg0: $DoubleConsumer): void;
        forEachRemaining(arg0: $IntConsumer): void;
        forEachRemaining(arg0: $IntConsumer): void;
        forEachRemaining(arg0: $LongConsumer): void;
        forEachRemaining(arg0: $LongConsumer): void;
        forEachRemaining(arg0: T_CONS): void;
        forEachRemaining(arg0: T_CONS): void;
        forEachRemaining(arg0: object): void;
        forEachRemaining(arg0: object): void;
        forEachRemaining(arg0: object): void;
        forEachRemaining(arg0: object): void;
        forEachRemaining(arg0: object): void;
        forEachRemaining(arg0: object): void;
        get asDouble(): number;
        get asInt(): number;
        get asLong(): number;
        get average(): number;
        get average(): number;
        get average(): number;
        get count(): number;
        get count(): number;
        get count(): number;
        get country(): string;
        get displayCountry(): string;
        get displayLanguage(): string;
        get displayName(): string;
        get displayScript(): string;
        get displayVariant(): string;
        get empty(): boolean;
        get empty(): boolean;
        get empty(): boolean;
        get empty(): boolean;
        get extensionKeys(): $Set<$Character>;
        get iSO3Country(): string;
        get iSO3Language(): string;
        get language(): string;
        get max(): number;
        get max(): number;
        get max(): number;
        get min(): number;
        get min(): number;
        get min(): number;
        get present(): boolean;
        get present(): boolean;
        get present(): boolean;
        get present(): boolean;
        get range(): string;
        get script(): string;
        get sum(): number;
        get sum(): number;
        get sum(): number;
        get unicodeLocaleAttributes(): $Set<string>;
        get unicodeLocaleKeys(): $Set<string>;
        get variant(): string;
        get weight(): number;
        get(): T;
        get(arg0: number): E;
        get(arg0: object): V;
        getAsDouble(): number;
        getAsInt(): number;
        getAsLong(): number;
        getAverage(): number;
        getAverage(): number;
        getAverage(): number;
        getComparator(): $Comparator<any>;
        getCount(): number;
        getCount(): number;
        getCount(): number;
        getCountry(): string;
        getDisplayCountry(): string;
        getDisplayCountry(arg0: $Locale): string;
        getDisplayLanguage(): string;
        getDisplayLanguage(arg0: $Locale): string;
        getDisplayName(): string;
        getDisplayName(arg0: $Locale): string;
        getDisplayScript(): string;
        getDisplayScript(arg0: $Locale): string;
        getDisplayVariant(): string;
        getDisplayVariant(arg0: $Locale): string;
        getExactSizeIfKnown(): number;
        getExtension(arg0: string): string;
        getExtensionKeys(): $Set<$Character>;
        getFirst(): E;
        getFirst(): E;
        getISO3Country(): string;
        getISO3Language(): string;
        getKey(): K;
        getLanguage(): string;
        getLast(): E;
        getLast(): E;
        getMax(): number;
        getMax(): number;
        getMax(): number;
        getMin(): number;
        getMin(): number;
        getMin(): number;
        getOrDefault(arg0: object, arg1: V): V;
        getRange(): string;
        getScript(): string;
        getSum(): number;
        getSum(): number;
        getSum(): number;
        getUnicodeLocaleAttributes(): $Set<string>;
        getUnicodeLocaleKeys(): $Set<string>;
        getUnicodeLocaleType(arg0: string): string;
        getValue(): V;
        getVariant(): string;
        getWeight(): number;
        hasCharacteristics(arg0: number): boolean;
        hasExtensions(): boolean;
        hasMoreElements(): boolean;
        hasNext(): boolean;
        hasNext(): boolean;
        hasPrevious(): boolean;
        hashCode(): number;
        hashCode(): number;
        hashCode(): number;
        hashCode(): number;
        hashCode(): number;
        hashCode(): number;
        hashCode(): number;
        hashCode(): number;
        hashCode(): number;
        hashCode(): number;
        hashCode(): number;
        headMap(arg0: K): $SortedMap<K, V>;
        ifPresent(arg0: $Consumer<any>): void;
        ifPresent(arg0: $DoubleConsumer): void;
        ifPresent(arg0: $IntConsumer): void;
        ifPresent(arg0: $LongConsumer): void;
        ifPresentOrElse(arg0: $Consumer<any>, arg1: $Runnable): void;
        ifPresentOrElse(arg0: $DoubleConsumer, arg1: $Runnable): void;
        ifPresentOrElse(arg0: $IntConsumer, arg1: $Runnable): void;
        ifPresentOrElse(arg0: $LongConsumer, arg1: $Runnable): void;
        indexOf(arg0: object): number;
        isEmpty(): boolean;
        isEmpty(): boolean;
        isEmpty(): boolean;
        isEmpty(): boolean;
        isEmpty(): boolean;
        isEmpty(): boolean;
        isEmpty(): boolean;
        isEmpty(): boolean;
        isPresent(): boolean;
        isPresent(): boolean;
        isPresent(): boolean;
        isPresent(): boolean;
        iterator(): $Iterator<E>;
        iterator(): $Iterator<E>;
        iterator(): $Iterator<E>;
        keySet(): $Set<K>;
        keySet(): $Set<K>;
        lastEntry(): $Map$Entry<K, V>;
        lastIndexOf(arg0: object): number;
        lastKey(): K;
        listIterator(): $ListIterator<E>;
        listIterator(arg0: number): $ListIterator<E>;
        map<U>(arg0: $Function<any, U>): $Optional<U>;
        merge(arg0: K, arg1: V, arg2: $BiFunction<any, any, V>): V;
        name(): string;
        name(): string;
        name(): string;
        naturalOrder<T>(): $Comparator<T>;
        next(): E;
        next(): E;
        next(): number;
        next(): number;
        next(): number;
        next(): object;
        next(): object;
        next(): object;
        nextDouble(): number;
        nextElement(): E;
        nextIndex(): number;
        nextInt(): number;
        nextLong(): number;
        nullsFirst<T>(arg0: $Comparator<any>): $Comparator<T>;
        nullsLast<T>(arg0: $Comparator<any>): $Comparator<T>;
        of<E>(): $List<E>;
        of<E>(): $Set<E>;
        of<E>(arg0: E): $List<E>;
        of<E>(arg0: E): $Set<E>;
        of<E>(arg0: E, arg1: E): $List<E>;
        of<E>(arg0: E, arg1: E): $Set<E>;
        of<E>(arg0: E, arg1: E, arg2: E): $List<E>;
        of<E>(arg0: E, arg1: E, arg2: E): $Set<E>;
        of<E>(arg0: E, arg1: E, arg2: E, arg3: E): $List<E>;
        of<E>(arg0: E, arg1: E, arg2: E, arg3: E): $Set<E>;
        of<E>(arg0: E, arg1: E, arg2: E, arg3: E, arg4: E): $List<E>;
        of<E>(arg0: E, arg1: E, arg2: E, arg3: E, arg4: E): $Set<E>;
        of<E>(arg0: E, arg1: E, arg2: E, arg3: E, arg4: E, arg5: E): $List<E>;
        of<E>(arg0: E, arg1: E, arg2: E, arg3: E, arg4: E, arg5: E): $Set<E>;
        of<E>(arg0: E, arg1: E, arg2: E, arg3: E, arg4: E, arg5: E, arg6: E): $List<E>;
        of<E>(arg0: E, arg1: E, arg2: E, arg3: E, arg4: E, arg5: E, arg6: E): $Set<E>;
        of<E>(arg0: E, arg1: E, arg2: E, arg3: E, arg4: E, arg5: E, arg6: E, arg7: E): $List<E>;
        of<E>(arg0: E, arg1: E, arg2: E, arg3: E, arg4: E, arg5: E, arg6: E, arg7: E): $Set<E>;
        of<E>(arg0: E, arg1: E, arg2: E, arg3: E, arg4: E, arg5: E, arg6: E, arg7: E, arg8: E): $List<E>;
        of<E>(arg0: E, arg1: E, arg2: E, arg3: E, arg4: E, arg5: E, arg6: E, arg7: E, arg8: E): $Set<E>;
        of<E>(arg0: E, arg1: E, arg2: E, arg3: E, arg4: E, arg5: E, arg6: E, arg7: E, arg8: E, arg9: E): $List<E>;
        of<E>(arg0: E, arg1: E, arg2: E, arg3: E, arg4: E, arg5: E, arg6: E, arg7: E, arg8: E, arg9: E): $Set<E>;
        of<E>(arg0?: object[]): $List<E>;
        of<E>(arg0?: object[]): $Set<E>;
        of<K, V>(): $Map<K, V>;
        of<K, V>(arg0: K, arg1: V): $Map<K, V>;
        of<K, V>(arg0: K, arg1: V, arg2: K, arg3: V): $Map<K, V>;
        of<K, V>(arg0: K, arg1: V, arg2: K, arg3: V, arg4: K, arg5: V): $Map<K, V>;
        of<K, V>(arg0: K, arg1: V, arg2: K, arg3: V, arg4: K, arg5: V, arg6: K, arg7: V): $Map<K, V>;
        of<K, V>(arg0: K, arg1: V, arg2: K, arg3: V, arg4: K, arg5: V, arg6: K, arg7: V, arg8: K, arg9: V): $Map<K, V>;
        of<K, V>(arg0: K, arg1: V, arg2: K, arg3: V, arg4: K, arg5: V, arg6: K, arg7: V, arg8: K, arg9: V, arg10: K, arg11: V): $Map<K, V>;
        of<K, V>(arg0: K, arg1: V, arg2: K, arg3: V, arg4: K, arg5: V, arg6: K, arg7: V, arg8: K, arg9: V, arg10: K, arg11: V, arg12: K, arg13: V): $Map<K, V>;
        of<K, V>(arg0: K, arg1: V, arg2: K, arg3: V, arg4: K, arg5: V, arg6: K, arg7: V, arg8: K, arg9: V, arg10: K, arg11: V, arg12: K, arg13: V, arg14: K, arg15: V): $Map<K, V>;
        of<K, V>(arg0: K, arg1: V, arg2: K, arg3: V, arg4: K, arg5: V, arg6: K, arg7: V, arg8: K, arg9: V, arg10: K, arg11: V, arg12: K, arg13: V, arg14: K, arg15: V, arg16: K, arg17: V): $Map<K, V>;
        of<K, V>(arg0: K, arg1: V, arg2: K, arg3: V, arg4: K, arg5: V, arg6: K, arg7: V, arg8: K, arg9: V, arg10: K, arg11: V, arg12: K, arg13: V, arg14: K, arg15: V, arg16: K, arg17: V, arg18: K, arg19: V): $Map<K, V>;
        ofEntries<K, V>(arg0?: $Map$Entry[]): $Map<K, V>;
        or(arg0: $Supplier<$Optional<T>>): $Optional<T>;
        orElse(arg0: T): T;
        orElse(arg0: number): number;
        orElse(arg0: number): number;
        orElse(arg0: number): number;
        orElseGet(arg0: $DoubleSupplier): number;
        orElseGet(arg0: $IntSupplier): number;
        orElseGet(arg0: $LongSupplier): number;
        orElseGet(arg0: $Supplier<T>): T;
        orElseThrow(): T;
        orElseThrow(): number;
        orElseThrow(): number;
        orElseThrow(): number;
        orElseThrow<X>(arg0: $Supplier<X>): T;
        orElseThrow<X>(arg0: $Supplier<X>): number;
        orElseThrow<X>(arg0: $Supplier<X>): number;
        orElseThrow<X>(arg0: $Supplier<X>): number;
        ordinal(): number;
        ordinal(): number;
        ordinal(): number;
        parallelStream(): $Stream<E>;
        pollFirstEntry(): $Map$Entry<K, V>;
        pollLastEntry(): $Map$Entry<K, V>;
        previous(): E;
        previousIndex(): number;
        put(arg0: K, arg1: V): V;
        putAll(arg0: { [key: K]: V }): void;
        putFirst(arg0: K, arg1: V): V;
        putFirst(arg0: K, arg1: V): V;
        putIfAbsent(arg0: K, arg1: V): V;
        putLast(arg0: K, arg1: V): V;
        putLast(arg0: K, arg1: V): V;
        remove(): void;
        remove(): void;
        remove(arg0: number): E;
        remove(arg0: object): V;
        remove(arg0: object): boolean;
        remove(arg0: object): boolean;
        remove(arg0: object): boolean;
        remove(arg0: object, arg1: object): boolean;
        removeAll(arg0: any[]): boolean;
        removeAll(arg0: any[]): boolean;
        removeAll(arg0: any[]): boolean;
        removeFirst(): E;
        removeFirst(): E;
        removeIf(arg0: $Predicate<any>): boolean;
        removeLast(): E;
        removeLast(): E;
        replace(arg0: K, arg1: V): V;
        replace(arg0: K, arg1: V, arg2: V): boolean;
        replaceAll(arg0: $BiFunction<any, any, V>): void;
        replaceAll(arg0: $UnaryOperator<E>): void;
        retainAll(arg0: any[]): boolean;
        retainAll(arg0: any[]): boolean;
        retainAll(arg0: any[]): boolean;
        reverseOrder<T>(): $Comparator<T>;
        reversed(): $Comparator<T>;
        reversed(): $List<E>;
        reversed(): $SequencedCollection;
        reversed(): $SequencedCollection;
        reversed(): $SequencedCollection<E>;
        reversed(): $SequencedMap;
        reversed(): $SequencedMap<K, V>;
        reversed(): $SequencedSet<E>;
        reversed(): $SortedMap<K, V>;
        sequencedEntrySet(): $SequencedSet<$Map$Entry<K, V>>;
        sequencedKeySet(): $SequencedSet<K>;
        sequencedValues(): $SequencedCollection<V>;
        set(arg0: E): void;
        set(arg0: number, arg1: E): E;
        setValue(arg0: V): V;
        size(): number;
        size(): number;
        size(): number;
        size(): number;
        sort(arg0: $Comparator<any>): void;
        spliterator(): $Spliterator<E>;
        spliterator(): $Spliterator<E>;
        spliterator(): $Spliterator<E>;
        static AUTOSELECT_FILTERING: $Locale$FilteringMode;
        static CANADA: $Locale;
        static CANADA_FRENCH: $Locale;
        static CHINA: $Locale;
        static CHINESE: $Locale;
        static CONCURRENT: number;
        static DISPLAY: $Locale$Category;
        static DISTINCT: number;
        static ENGLISH: $Locale;
        static EXTENDED_FILTERING: $Locale$FilteringMode;
        static FORMAT: $Locale$Category;
        static FRANCE: $Locale;
        static FRENCH: $Locale;
        static GERMAN: $Locale;
        static GERMANY: $Locale;
        static IGNORE_EXTENDED_RANGES: $Locale$FilteringMode;
        static IMMUTABLE: number;
        static ITALIAN: $Locale;
        static ITALY: $Locale;
        static JAPAN: $Locale;
        static JAPANESE: $Locale;
        static KOREA: $Locale;
        static KOREAN: $Locale;
        static MAP_EXTENDED_RANGES: $Locale$FilteringMode;
        static MAX_WEIGHT: number;
        static MIN_WEIGHT: number;
        static NONNULL: number;
        static ORDERED: number;
        static PART1_ALPHA2: $Locale$IsoCountryCode;
        static PART1_ALPHA3: $Locale$IsoCountryCode;
        static PART3: $Locale$IsoCountryCode;
        static PRC: $Locale;
        static PRIVATE_USE_EXTENSION: string;
        static REJECT_EXTENDED_RANGES: $Locale$FilteringMode;
        static ROOT: $Locale;
        static SIMPLIFIED_CHINESE: $Locale;
        static SIZED: number;
        static SORTED: number;
        static SUBSIZED: number;
        static TAIWAN: $Locale;
        static TRADITIONAL_CHINESE: $Locale;
        static UK: $Locale;
        static UNICODE_LOCALE_EXTENSION: string;
        static US: $Locale;
        static availableLocales(): $Stream<$Locale>;
        static caseFoldLanguageTag(arg0: string): string;
        static empty(): $OptionalDouble;
        static empty(): $OptionalInt;
        static empty(): $OptionalLong;
        static empty<T>(): $Optional<T>;
        static filter(arg0: $Locale$LanguageRange[], arg1: $Locale[]): $List<$Locale>;
        static filter(arg0: $Locale$LanguageRange[], arg1: $Locale[], arg2: $Locale$FilteringMode): $List<$Locale>;
        static filterTags(arg0: $Locale$LanguageRange[], arg1: string[]): $List<string>;
        static filterTags(arg0: $Locale$LanguageRange[], arg1: string[], arg2: $Locale$FilteringMode): $List<string>;
        static forLanguageTag(arg0: string): $Locale;
        static getAvailableLocales(): $Locale[];
        static getDefault(): $Locale;
        static getDefault(arg0: $Locale$Category): $Locale;
        static getISOCountries(): string[];
        static getISOCountries(arg0: $Locale$IsoCountryCode): $Set<string>;
        static getISOLanguages(): string[];
        static lookup(arg0: $Locale$LanguageRange[], arg1: $Locale[]): $Locale;
        static lookupTag(arg0: $Locale$LanguageRange[], arg1: string[]): string;
        static mapEquivalents(arg0: $Locale$LanguageRange[], arg1: { [key: string]: string[] }): $List<$Locale$LanguageRange>;
        static of(arg0: number): $OptionalDouble;
        static of(arg0: number): $OptionalInt;
        static of(arg0: number): $OptionalLong;
        static of(arg0: string): $Locale;
        static of(arg0: string, arg1: string): $Locale;
        static of(arg0: string, arg1: string, arg2: string): $Locale;
        static of<T>(arg0: T): $Optional<T>;
        static ofNullable<T>(arg0: T): $Optional<T>;
        static parse(arg0: string): $List<$Locale$LanguageRange>;
        static parse(arg0: string, arg1: { [key: string]: string[] }): $List<$Locale$LanguageRange>;
        static setDefault(arg0: $Locale$Category, arg1: $Locale): void;
        static setDefault(arg0: $Locale): void;
        static valueOf(name: string): $Locale$Category;
        static valueOf(name: string): $Locale$FilteringMode;
        static valueOf(name: string): $Locale$IsoCountryCode;
        static values(): $Locale$Category[];
        static values(): $Locale$FilteringMode[];
        static values(): $Locale$IsoCountryCode[];
        stream(): $DoubleStream;
        stream(): $IntStream;
        stream(): $LongStream;
        stream(): $Stream<E>;
        stream(): $Stream<T>;
        stripExtensions(): $Locale;
        subList(arg0: number, arg1: number): $List<E>;
        subMap(arg0: K, arg1: K): $SortedMap<K, V>;
        tailMap(arg0: K): $SortedMap<K, V>;
        thenComparing(arg0: $Comparator<any>): $Comparator<T>;
        thenComparing<U>(arg0: $Function<any, U>): $Comparator<T>;
        thenComparing<U>(arg0: $Function<any, U>, arg1: $Comparator<any>): $Comparator<T>;
        thenComparingDouble(arg0: $ToDoubleFunction<any>): $Comparator<T>;
        thenComparingInt(arg0: $ToIntFunction<any>): $Comparator<T>;
        thenComparingLong(arg0: $ToLongFunction<any>): $Comparator<T>;
        toArray(): object[];
        toArray(): object[];
        toArray(): object[];
        toArray<T>(arg0: $IntFunction<T[]>): T[];
        toArray<T>(arg0: T[]): T[];
        toArray<T>(arg0: T[]): T[];
        toArray<T>(arg0: T[]): T[];
        toLanguageTag(): string;
        toString(): string;
        toString(): string;
        toString(): string;
        toString(): string;
        toString(): string;
        toString(): string;
        toString(): string;
        toString(): string;
        toString(): string;
        toString(): string;
        toString(): string;
        toString(): string;
        tryAdvance(arg0: $Consumer<any>): boolean;
        tryAdvance(arg0: $Consumer<any>): boolean;
        tryAdvance(arg0: $Consumer<any>): boolean;
        tryAdvance(arg0: $Consumer<any>): boolean;
        tryAdvance(arg0: $DoubleConsumer): boolean;
        tryAdvance(arg0: $IntConsumer): boolean;
        tryAdvance(arg0: $LongConsumer): boolean;
        tryAdvance(arg0: T_CONS): boolean;
        tryAdvance(arg0: object): boolean;
        tryAdvance(arg0: object): boolean;
        tryAdvance(arg0: object): boolean;
        trySplit(): $Spliterator$OfDouble;
        trySplit(): $Spliterator$OfInt;
        trySplit(): $Spliterator$OfLong;
        trySplit(): $Spliterator$OfPrimitive;
        trySplit(): $Spliterator$OfPrimitive;
        trySplit(): $Spliterator$OfPrimitive;
        trySplit(): $Spliterator;
        trySplit(): $Spliterator;
        trySplit(): $Spliterator;
        trySplit(): $Spliterator;
        trySplit(): $Spliterator<T>;
        trySplit(): T_SPLITR;
        values(): $Collection<V>;
        values(): $Collection<V>;
    export class $DoubleSummaryStatistics implements $DoubleConsumer {
    export class $IntSummaryStatistics implements $IntConsumer {
    export class $Locale implements $Cloneable, $Serializable {
    export class $Locale$Category {
    export class $Locale$FilteringMode {
    export class $Locale$IsoCountryCode {
    export class $Locale$LanguageRange {
    export class $LongSummaryStatistics implements $LongConsumer, $IntConsumer {
    export class $Optional<T> {
    export class $OptionalDouble {
    export class $OptionalInt {
    export class $OptionalLong {
    export interface $Collection<E> extends $Iterable {
    export interface $Comparator<T> {
    export interface $Enumeration<E> {
    export interface $Iterator<E> {
    export interface $List<E> extends $SequencedCollection {
    export interface $ListIterator<E> extends $Iterator {
    export interface $Map$Entry<K, V> {
    export interface $Map<K, V> {
    export interface $PrimitiveIterator$OfDouble extends $PrimitiveIterator {
    export interface $PrimitiveIterator$OfInt extends $PrimitiveIterator {
    export interface $PrimitiveIterator$OfLong extends $PrimitiveIterator {
    export interface $PrimitiveIterator<T, T_CONS> extends $Iterator {
    export interface $SequencedCollection<E> extends $Collection {
    export interface $SequencedMap<K, V> extends $Map {
    export interface $SequencedSet<E> extends $SequencedCollection, $Set {
    export interface $Set<E> extends $Collection {
    export interface $SortedMap<K, V> extends $SequencedMap {
    export interface $Spliterator$OfDouble extends $Spliterator$OfPrimitive {
    export interface $Spliterator$OfInt extends $Spliterator$OfPrimitive {
    export interface $Spliterator$OfLong extends $Spliterator$OfPrimitive {
    export interface $Spliterator$OfPrimitive<T, T_CONS, T_SPLITR extends $Spliterator$OfPrimitive<T, T_CONS, T_SPLITR>> extends $Spliterator {
    export interface $Spliterator<T> {
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
    }
    }
    }
    }
    }
    }
    }
    }
    }
    }
    }
    }
    }
    }
    }
    }
    }
    }
    }
    }
    }
    }
    }
    }
    }
    }
    }
    }
    }
    }
    }
    }
    }
declare module "java:java/util" {
export * as function from "java:java/util/function";
export * as stream from "java:java/util/stream";
import { $BiConsumer, $BiFunction, $Consumer, $DoubleConsumer, $DoubleSupplier, $Function, $IntConsumer, $IntFunction, $IntSupplier, $LongConsumer, $LongSupplier, $Predicate, $Supplier, $ToDoubleFunction, $ToIntFunction, $ToLongFunction, $UnaryOperator } from "java:java/util/function";
import { $Character, $Cloneable, $Double, $Enum, $Integer, $Iterable, $Long, $Runnable, $String } from "java:java/lang";
import { $DoubleStream, $IntStream, $LongStream, $Stream } from "java:java/util/stream";
import { $Serializable } from "java:java/io";
}