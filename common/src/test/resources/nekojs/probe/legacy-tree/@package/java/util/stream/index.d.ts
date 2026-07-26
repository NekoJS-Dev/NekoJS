














        accept(arg0: T): void;
        accept(arg0: number): void;
        accept(arg0: number): void;
        accept(arg0: number): void;
        accept(arg0: number, arg1: $DoubleConsumer): void;
        accept(arg0: number, arg1: $IntConsumer): void;
        accept(arg0: number, arg1: $LongConsumer): void;
        accumulator(): $BiConsumer<A, T>;
        add(arg0: T): $Stream$Builder<T>;
        add(arg0: number): $DoubleStream$Builder;
        add(arg0: number): $IntStream$Builder;
        add(arg0: number): $LongStream$Builder;
        allMatch(arg0: $DoublePredicate): boolean;
        allMatch(arg0: $IntPredicate): boolean;
        allMatch(arg0: $LongPredicate): boolean;
        allMatch(arg0: $Predicate<any>): boolean;
        anyMatch(arg0: $DoublePredicate): boolean;
        anyMatch(arg0: $IntPredicate): boolean;
        anyMatch(arg0: $LongPredicate): boolean;
        anyMatch(arg0: $Predicate<any>): boolean;
        asDoubleStream(): $DoubleStream;
        asDoubleStream(): $DoubleStream;
        asLongStream(): $LongStream;
        average(): $OptionalDouble;
        average(): $OptionalDouble;
        average(): $OptionalDouble;
        boxed(): $Stream<number>;
        boxed(): $Stream<number>;
        boxed(): $Stream<number>;
        build(): $DoubleStream;
        build(): $IntStream;
        build(): $LongStream;
        build(): $Stream<T>;
        builder(): $DoubleStream$Builder;
        builder(): $IntStream$Builder;
        builder(): $LongStream$Builder;
        builder<T>(): $Stream$Builder<T>;
        characteristics(): $Set<$Collector$Characteristics>;
        close(): void;
        collect<R, A>(arg0: $Collector<any, A, R>): R;
        collect<R>(arg0: $Supplier<R>, arg1: $BiConsumer<R, any>, arg2: $BiConsumer<R, R>): R;
        collect<R>(arg0: $Supplier<R>, arg1: $ObjDoubleConsumer<R>, arg2: $BiConsumer<R, R>): R;
        collect<R>(arg0: $Supplier<R>, arg1: $ObjIntConsumer<R>, arg2: $BiConsumer<R, R>): R;
        collect<R>(arg0: $Supplier<R>, arg1: $ObjLongConsumer<R>, arg2: $BiConsumer<R, R>): R;
        combiner(): $BinaryOperator<A>;
        concat(arg0: $DoubleStream, arg1: $DoubleStream): $DoubleStream;
        concat(arg0: $IntStream, arg1: $IntStream): $IntStream;
        concat(arg0: $LongStream, arg1: $LongStream): $LongStream;
        concat<T>(arg0: $Stream<T>, arg1: $Stream<T>): $Stream<T>;
        count(): number;
        count(): number;
        count(): number;
        count(): number;
        distinct(): $DoubleStream;
        distinct(): $IntStream;
        distinct(): $LongStream;
        distinct(): $Stream<T>;
        dropWhile(arg0: $DoublePredicate): $DoubleStream;
        dropWhile(arg0: $IntPredicate): $IntStream;
        dropWhile(arg0: $LongPredicate): $LongStream;
        dropWhile(arg0: $Predicate<any>): $Stream<T>;
        empty(): $DoubleStream;
        empty(): $IntStream;
        empty(): $LongStream;
        empty<T>(): $Stream<T>;
        filter(arg0: $DoublePredicate): $DoubleStream;
        filter(arg0: $IntPredicate): $IntStream;
        filter(arg0: $LongPredicate): $LongStream;
        filter(arg0: $Predicate<any>): $Stream<T>;
        findAny(): $Optional<T>;
        findAny(): $OptionalDouble;
        findAny(): $OptionalInt;
        findAny(): $OptionalLong;
        findFirst(): $Optional<T>;
        findFirst(): $OptionalDouble;
        findFirst(): $OptionalInt;
        findFirst(): $OptionalLong;
        finisher(): $Function<A, R>;
        flatMap(arg0: $DoubleFunction<$DoubleStream>): $DoubleStream;
        flatMap(arg0: $IntFunction<$IntStream>): $IntStream;
        flatMap(arg0: $LongFunction<$LongStream>): $LongStream;
        flatMap<R>(arg0: $Function<any, $Stream<R>>): $Stream<R>;
        flatMapToDouble(arg0: $Function<any, $DoubleStream>): $DoubleStream;
        flatMapToInt(arg0: $Function<any, $IntStream>): $IntStream;
        flatMapToLong(arg0: $Function<any, $LongStream>): $LongStream;
        forEach(arg0: $Consumer<any>): void;
        forEach(arg0: $DoubleConsumer): void;
        forEach(arg0: $IntConsumer): void;
        forEach(arg0: $LongConsumer): void;
        forEachOrdered(arg0: $Consumer<any>): void;
        forEachOrdered(arg0: $DoubleConsumer): void;
        forEachOrdered(arg0: $IntConsumer): void;
        forEachOrdered(arg0: $LongConsumer): void;
        generate(arg0: $DoubleSupplier): $DoubleStream;
        generate(arg0: $IntSupplier): $IntStream;
        generate(arg0: $LongSupplier): $LongStream;
        generate<T>(arg0: $Supplier<T>): $Stream<T>;
        isParallel(): boolean;
        iterate(arg0: number, arg1: $DoublePredicate, arg2: $DoubleUnaryOperator): $DoubleStream;
        iterate(arg0: number, arg1: $DoubleUnaryOperator): $DoubleStream;
        iterate(arg0: number, arg1: $IntPredicate, arg2: $IntUnaryOperator): $IntStream;
        iterate(arg0: number, arg1: $IntUnaryOperator): $IntStream;
        iterate(arg0: number, arg1: $LongPredicate, arg2: $LongUnaryOperator): $LongStream;
        iterate(arg0: number, arg1: $LongUnaryOperator): $LongStream;
        iterate<T>(arg0: T, arg1: $Predicate<any>, arg2: $UnaryOperator<T>): $Stream<T>;
        iterate<T>(arg0: T, arg1: $UnaryOperator<T>): $Stream<T>;
        iterator(): $Iterator;
        iterator(): $Iterator;
        iterator(): $Iterator;
        iterator(): $Iterator<T>;
        iterator(): $PrimitiveIterator$OfDouble;
        iterator(): $PrimitiveIterator$OfInt;
        iterator(): $PrimitiveIterator$OfLong;
        limit(arg0: number): $DoubleStream;
        limit(arg0: number): $IntStream;
        limit(arg0: number): $LongStream;
        limit(arg0: number): $Stream<T>;
        map(arg0: $DoubleUnaryOperator): $DoubleStream;
        map(arg0: $IntUnaryOperator): $IntStream;
        map(arg0: $LongUnaryOperator): $LongStream;
        map<R>(arg0: $Function<any, R>): $Stream<R>;
        mapMulti(arg0: $DoubleStream$DoubleMapMultiConsumer): $DoubleStream;
        mapMulti(arg0: $IntStream$IntMapMultiConsumer): $IntStream;
        mapMulti(arg0: $LongStream$LongMapMultiConsumer): $LongStream;
        mapMulti<R>(arg0: $BiConsumer<any, any>): $Stream<R>;
        mapMultiToDouble(arg0: $BiConsumer<any, any>): $DoubleStream;
        mapMultiToInt(arg0: $BiConsumer<any, any>): $IntStream;
        mapMultiToLong(arg0: $BiConsumer<any, any>): $LongStream;
        mapToDouble(arg0: $IntToDoubleFunction): $DoubleStream;
        mapToDouble(arg0: $LongToDoubleFunction): $DoubleStream;
        mapToDouble(arg0: $ToDoubleFunction<any>): $DoubleStream;
        mapToInt(arg0: $DoubleToIntFunction): $IntStream;
        mapToInt(arg0: $LongToIntFunction): $IntStream;
        mapToInt(arg0: $ToIntFunction<any>): $IntStream;
        mapToLong(arg0: $DoubleToLongFunction): $LongStream;
        mapToLong(arg0: $IntToLongFunction): $LongStream;
        mapToLong(arg0: $ToLongFunction<any>): $LongStream;
        mapToObj<U>(arg0: $DoubleFunction<U>): $Stream<U>;
        mapToObj<U>(arg0: $IntFunction<U>): $Stream<U>;
        mapToObj<U>(arg0: $LongFunction<U>): $Stream<U>;
        max(): $OptionalDouble;
        max(): $OptionalInt;
        max(): $OptionalLong;
        max(arg0: $Comparator<any>): $Optional<T>;
        min(): $OptionalDouble;
        min(): $OptionalInt;
        min(): $OptionalLong;
        min(arg0: $Comparator<any>): $Optional<T>;
        name(): string;
        noneMatch(arg0: $DoublePredicate): boolean;
        noneMatch(arg0: $IntPredicate): boolean;
        noneMatch(arg0: $LongPredicate): boolean;
        noneMatch(arg0: $Predicate<any>): boolean;
        of(arg0: number): $DoubleStream;
        of(arg0: number): $IntStream;
        of(arg0: number): $LongStream;
        of(arg0?: number[]): $DoubleStream;
        of(arg0?: number[]): $IntStream;
        of(arg0?: number[]): $LongStream;
        of<T, A, R>(arg0: $Supplier<A>, arg1: $BiConsumer<A, T>, arg2: $BinaryOperator<A>, arg3: $Function<A, R>, arg4?: $Collector$Characteristics[]): $Collector<T, A, R>;
        of<T, R>(arg0: $Supplier<R>, arg1: $BiConsumer<R, T>, arg2: $BinaryOperator<R>, arg3?: $Collector$Characteristics[]): $Collector<T, R, R>;
        of<T>(arg0: T): $Stream<T>;
        of<T>(arg0?: object[]): $Stream<T>;
        ofNullable<T>(arg0: T): $Stream<T>;
        onClose(arg0: $Runnable): S;
        ordinal(): number;
        parallel(): $BaseStream;
        parallel(): $BaseStream;
        parallel(): $BaseStream;
        parallel(): $DoubleStream;
        parallel(): $IntStream;
        parallel(): $LongStream;
        parallel(): S;
        peek(arg0: $Consumer<any>): $Stream<T>;
        peek(arg0: $DoubleConsumer): $DoubleStream;
        peek(arg0: $IntConsumer): $IntStream;
        peek(arg0: $LongConsumer): $LongStream;
        range(arg0: number, arg1: number): $IntStream;
        range(arg0: number, arg1: number): $LongStream;
        rangeClosed(arg0: number, arg1: number): $IntStream;
        rangeClosed(arg0: number, arg1: number): $LongStream;
        reduce(arg0: $BinaryOperator<T>): $Optional<T>;
        reduce(arg0: $DoubleBinaryOperator): $OptionalDouble;
        reduce(arg0: $IntBinaryOperator): $OptionalInt;
        reduce(arg0: $LongBinaryOperator): $OptionalLong;
        reduce(arg0: T, arg1: $BinaryOperator<T>): T;
        reduce(arg0: number, arg1: $DoubleBinaryOperator): number;
        reduce(arg0: number, arg1: $IntBinaryOperator): number;
        reduce(arg0: number, arg1: $LongBinaryOperator): number;
        reduce<U>(arg0: U, arg1: $BiFunction<U, any, U>, arg2: $BinaryOperator<U>): U;
        sequential(): $BaseStream;
        sequential(): $BaseStream;
        sequential(): $BaseStream;
        sequential(): $DoubleStream;
        sequential(): $IntStream;
        sequential(): $LongStream;
        sequential(): S;
        skip(arg0: number): $DoubleStream;
        skip(arg0: number): $IntStream;
        skip(arg0: number): $LongStream;
        skip(arg0: number): $Stream<T>;
        sorted(): $DoubleStream;
        sorted(): $IntStream;
        sorted(): $LongStream;
        sorted(): $Stream<T>;
        sorted(arg0: $Comparator<any>): $Stream<T>;
        spliterator(): $Spliterator$OfDouble;
        spliterator(): $Spliterator$OfInt;
        spliterator(): $Spliterator$OfLong;
        spliterator(): $Spliterator;
        spliterator(): $Spliterator;
        spliterator(): $Spliterator;
        spliterator(): $Spliterator<T>;
        static CONCURRENT: $Collector$Characteristics;
        static IDENTITY_FINISH: $Collector$Characteristics;
        static UNORDERED: $Collector$Characteristics;
        static valueOf(name: string): $Collector$Characteristics;
        static values(): $Collector$Characteristics[];
        sum(): number;
        sum(): number;
        sum(): number;
        summaryStatistics(): $DoubleSummaryStatistics;
        summaryStatistics(): $IntSummaryStatistics;
        summaryStatistics(): $LongSummaryStatistics;
        supplier(): $Supplier<A>;
        takeWhile(arg0: $DoublePredicate): $DoubleStream;
        takeWhile(arg0: $IntPredicate): $IntStream;
        takeWhile(arg0: $LongPredicate): $LongStream;
        takeWhile(arg0: $Predicate<any>): $Stream<T>;
        toArray(): number[];
        toArray(): number[];
        toArray(): number[];
        toArray(): object[];
        toArray<A>(arg0: $IntFunction<A[]>): A[];
        toList(): $List<T>;
        toString(): string;
        unordered(): S;
    export class $Collector$Characteristics {
    export interface $BaseStream<T, S extends $BaseStream<T, S>> extends $AutoCloseable {
    export interface $Collector<T, A, R> {
    export interface $DoubleStream extends $BaseStream {
    export interface $DoubleStream$Builder extends $DoubleConsumer {
    export interface $DoubleStream$DoubleMapMultiConsumer {
    export interface $IntStream extends $BaseStream {
    export interface $IntStream$Builder extends $IntConsumer {
    export interface $IntStream$IntMapMultiConsumer {
    export interface $LongStream extends $BaseStream {
    export interface $LongStream$Builder extends $LongConsumer {
    export interface $LongStream$LongMapMultiConsumer {
    export interface $Stream$Builder<T> extends $Consumer {
    export interface $Stream<T> extends $BaseStream {
    export type $Stream_<T> = T[];
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
declare module "java:java/util/stream" {
import { $AutoCloseable, $Double, $Enum, $Integer, $Long, $Runnable, $String } from "java:java/lang";
import { $BiConsumer, $BiFunction, $BinaryOperator, $Consumer, $DoubleBinaryOperator, $DoubleConsumer, $DoubleFunction, $DoublePredicate, $DoubleSupplier, $DoubleToIntFunction, $DoubleToLongFunction, $DoubleUnaryOperator, $Function, $IntBinaryOperator, $IntConsumer, $IntFunction, $IntPredicate, $IntSupplier, $IntToDoubleFunction, $IntToLongFunction, $IntUnaryOperator, $LongBinaryOperator, $LongConsumer, $LongFunction, $LongPredicate, $LongSupplier, $LongToDoubleFunction, $LongToIntFunction, $LongUnaryOperator, $ObjDoubleConsumer, $ObjIntConsumer, $ObjLongConsumer, $Predicate, $Supplier, $ToDoubleFunction, $ToIntFunction, $ToLongFunction, $UnaryOperator } from "java:java/util/function";
import { $Comparator, $DoubleSummaryStatistics, $IntSummaryStatistics, $Iterator, $List, $LongSummaryStatistics, $Optional, $OptionalDouble, $OptionalInt, $OptionalLong, $PrimitiveIterator$OfDouble, $PrimitiveIterator$OfInt, $PrimitiveIterator$OfLong, $Set, $Spliterator, $Spliterator$OfDouble, $Spliterator$OfInt, $Spliterator$OfLong } from "java:java/util";
}