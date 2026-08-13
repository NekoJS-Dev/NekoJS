import { $AutoCloseable, $Double, $Enum, $Integer, $Long, $Runnable, $String } from "java:java/lang";
import { $Comparator, $DoubleSummaryStatistics, $IntSummaryStatistics, $Iterator, $List, $LongSummaryStatistics, $Optional, $OptionalDouble, $OptionalInt, $OptionalLong, $PrimitiveIterator$OfDouble, $PrimitiveIterator$OfInt, $PrimitiveIterator$OfLong, $Set, $Spliterator, $Spliterator$OfDouble, $Spliterator$OfInt, $Spliterator$OfLong } from "java:java/util";
import { $BiConsumer, $BiFunction, $BinaryOperator, $Consumer, $DoubleBinaryOperator, $DoubleConsumer, $DoubleFunction, $DoublePredicate, $DoubleSupplier, $DoubleToIntFunction, $DoubleToLongFunction, $DoubleUnaryOperator, $Function, $IntBinaryOperator, $IntConsumer, $IntFunction, $IntPredicate, $IntSupplier, $IntToDoubleFunction, $IntToLongFunction, $IntUnaryOperator, $LongBinaryOperator, $LongConsumer, $LongFunction, $LongPredicate, $LongSupplier, $LongToDoubleFunction, $LongToIntFunction, $LongUnaryOperator, $ObjDoubleConsumer, $ObjIntConsumer, $ObjLongConsumer, $Predicate, $Supplier, $ToDoubleFunction, $ToIntFunction, $ToLongFunction, $UnaryOperator } from "java:java/util/function";

declare module "java:java/util/stream" {
    export interface $BaseStream<T, S extends $BaseStream<T, S>> extends $AutoCloseable {
        close(): void;
        isParallel(): boolean;
        iterator(): $Iterator<T>;
        onClose(arg0: $Runnable): S;
        parallel(): S;
        sequential(): S;
        spliterator(): $Spliterator<T>;
        unordered(): S;
    }

    export interface $Collector<T, A, R> {
        accumulator(): $BiConsumer<A, T>;
        characteristics(): $Set<$Collector$Characteristics>;
        combiner(): $BinaryOperator<A>;
        finisher(): $Function<A, R>;
        of<T, A, R>(arg0: $Supplier<A>, arg1: $BiConsumer<A, T>, arg2: $BinaryOperator<A>, arg3: $Function<A, R>, arg4?: $Collector$Characteristics[]): $Collector<T, A, R>;
        of<T, R>(arg0: $Supplier<R>, arg1: $BiConsumer<R, T>, arg2: $BinaryOperator<R>, arg3?: $Collector$Characteristics[]): $Collector<T, R, R>;
        supplier(): $Supplier<A>;
    }

    export class $Collector$Characteristics {
        static CONCURRENT: $Collector$Characteristics;
        static IDENTITY_FINISH: $Collector$Characteristics;
        static UNORDERED: $Collector$Characteristics;
        name(): string;
        ordinal(): number;
        toString(): string;
        static values(): $Collector$Characteristics[];
        static valueOf(name: string): $Collector$Characteristics;
    }

    export interface $DoubleStream extends $BaseStream {
        allMatch(arg0: $DoublePredicate): boolean;
        anyMatch(arg0: $DoublePredicate): boolean;
        average(): $OptionalDouble;
        boxed(): $Stream<number>;
        builder(): $DoubleStream$Builder;
        collect<R>(arg0: $Supplier<R>, arg1: $ObjDoubleConsumer<R>, arg2: $BiConsumer<R, R>): R;
        concat(arg0: $DoubleStream, arg1: $DoubleStream): $DoubleStream;
        count(): number;
        distinct(): $DoubleStream;
        dropWhile(arg0: $DoublePredicate): $DoubleStream;
        empty(): $DoubleStream;
        filter(arg0: $DoublePredicate): $DoubleStream;
        findAny(): $OptionalDouble;
        findFirst(): $OptionalDouble;
        flatMap(arg0: $DoubleFunction<$DoubleStream>): $DoubleStream;
        forEachOrdered(arg0: $DoubleConsumer): void;
        forEach(arg0: $DoubleConsumer): void;
        generate(arg0: $DoubleSupplier): $DoubleStream;
        iterate(arg0: number, arg1: $DoublePredicate, arg2: $DoubleUnaryOperator): $DoubleStream;
        iterate(arg0: number, arg1: $DoubleUnaryOperator): $DoubleStream;
        iterator(): $Iterator;
        iterator(): $PrimitiveIterator$OfDouble;
        limit(arg0: number): $DoubleStream;
        mapMulti(arg0: $DoubleStream$DoubleMapMultiConsumer): $DoubleStream;
        mapToInt(arg0: $DoubleToIntFunction): $IntStream;
        mapToLong(arg0: $DoubleToLongFunction): $LongStream;
        mapToObj<U>(arg0: $DoubleFunction<U>): $Stream<U>;
        map(arg0: $DoubleUnaryOperator): $DoubleStream;
        max(): $OptionalDouble;
        min(): $OptionalDouble;
        noneMatch(arg0: $DoublePredicate): boolean;
        of(arg0?: number[]): $DoubleStream;
        of(arg0: number): $DoubleStream;
        parallel(): $BaseStream;
        parallel(): $DoubleStream;
        peek(arg0: $DoubleConsumer): $DoubleStream;
        reduce(arg0: number, arg1: $DoubleBinaryOperator): number;
        reduce(arg0: $DoubleBinaryOperator): $OptionalDouble;
        sequential(): $BaseStream;
        sequential(): $DoubleStream;
        skip(arg0: number): $DoubleStream;
        sorted(): $DoubleStream;
        spliterator(): $Spliterator;
        spliterator(): $Spliterator$OfDouble;
        summaryStatistics(): $DoubleSummaryStatistics;
        sum(): number;
        takeWhile(arg0: $DoublePredicate): $DoubleStream;
        toArray(): number[];
    }

    export interface $DoubleStream$Builder extends $DoubleConsumer {
        accept(arg0: number): void;
        add(arg0: number): $DoubleStream$Builder;
        build(): $DoubleStream;
    }

    export interface $DoubleStream$DoubleMapMultiConsumer {
        accept(arg0: number, arg1: $DoubleConsumer): void;
    }

    export interface $IntStream extends $BaseStream {
        allMatch(arg0: $IntPredicate): boolean;
        anyMatch(arg0: $IntPredicate): boolean;
        asDoubleStream(): $DoubleStream;
        asLongStream(): $LongStream;
        average(): $OptionalDouble;
        boxed(): $Stream<number>;
        builder(): $IntStream$Builder;
        collect<R>(arg0: $Supplier<R>, arg1: $ObjIntConsumer<R>, arg2: $BiConsumer<R, R>): R;
        concat(arg0: $IntStream, arg1: $IntStream): $IntStream;
        count(): number;
        distinct(): $IntStream;
        dropWhile(arg0: $IntPredicate): $IntStream;
        empty(): $IntStream;
        filter(arg0: $IntPredicate): $IntStream;
        findAny(): $OptionalInt;
        findFirst(): $OptionalInt;
        flatMap(arg0: $IntFunction<$IntStream>): $IntStream;
        forEachOrdered(arg0: $IntConsumer): void;
        forEach(arg0: $IntConsumer): void;
        generate(arg0: $IntSupplier): $IntStream;
        iterate(arg0: number, arg1: $IntPredicate, arg2: $IntUnaryOperator): $IntStream;
        iterate(arg0: number, arg1: $IntUnaryOperator): $IntStream;
        iterator(): $Iterator;
        iterator(): $PrimitiveIterator$OfInt;
        limit(arg0: number): $IntStream;
        mapMulti(arg0: $IntStream$IntMapMultiConsumer): $IntStream;
        mapToDouble(arg0: $IntToDoubleFunction): $DoubleStream;
        mapToLong(arg0: $IntToLongFunction): $LongStream;
        mapToObj<U>(arg0: $IntFunction<U>): $Stream<U>;
        map(arg0: $IntUnaryOperator): $IntStream;
        max(): $OptionalInt;
        min(): $OptionalInt;
        noneMatch(arg0: $IntPredicate): boolean;
        of(arg0?: number[]): $IntStream;
        of(arg0: number): $IntStream;
        parallel(): $BaseStream;
        parallel(): $IntStream;
        peek(arg0: $IntConsumer): $IntStream;
        rangeClosed(arg0: number, arg1: number): $IntStream;
        range(arg0: number, arg1: number): $IntStream;
        reduce(arg0: number, arg1: $IntBinaryOperator): number;
        reduce(arg0: $IntBinaryOperator): $OptionalInt;
        sequential(): $BaseStream;
        sequential(): $IntStream;
        skip(arg0: number): $IntStream;
        sorted(): $IntStream;
        spliterator(): $Spliterator;
        spliterator(): $Spliterator$OfInt;
        summaryStatistics(): $IntSummaryStatistics;
        sum(): number;
        takeWhile(arg0: $IntPredicate): $IntStream;
        toArray(): number[];
    }

    export interface $IntStream$Builder extends $IntConsumer {
        accept(arg0: number): void;
        add(arg0: number): $IntStream$Builder;
        build(): $IntStream;
    }

    export interface $IntStream$IntMapMultiConsumer {
        accept(arg0: number, arg1: $IntConsumer): void;
    }

    export interface $LongStream extends $BaseStream {
        allMatch(arg0: $LongPredicate): boolean;
        anyMatch(arg0: $LongPredicate): boolean;
        asDoubleStream(): $DoubleStream;
        average(): $OptionalDouble;
        boxed(): $Stream<number>;
        builder(): $LongStream$Builder;
        collect<R>(arg0: $Supplier<R>, arg1: $ObjLongConsumer<R>, arg2: $BiConsumer<R, R>): R;
        concat(arg0: $LongStream, arg1: $LongStream): $LongStream;
        count(): number;
        distinct(): $LongStream;
        dropWhile(arg0: $LongPredicate): $LongStream;
        empty(): $LongStream;
        filter(arg0: $LongPredicate): $LongStream;
        findAny(): $OptionalLong;
        findFirst(): $OptionalLong;
        flatMap(arg0: $LongFunction<$LongStream>): $LongStream;
        forEachOrdered(arg0: $LongConsumer): void;
        forEach(arg0: $LongConsumer): void;
        generate(arg0: $LongSupplier): $LongStream;
        iterate(arg0: number, arg1: $LongPredicate, arg2: $LongUnaryOperator): $LongStream;
        iterate(arg0: number, arg1: $LongUnaryOperator): $LongStream;
        iterator(): $Iterator;
        iterator(): $PrimitiveIterator$OfLong;
        limit(arg0: number): $LongStream;
        mapMulti(arg0: $LongStream$LongMapMultiConsumer): $LongStream;
        mapToDouble(arg0: $LongToDoubleFunction): $DoubleStream;
        mapToInt(arg0: $LongToIntFunction): $IntStream;
        mapToObj<U>(arg0: $LongFunction<U>): $Stream<U>;
        map(arg0: $LongUnaryOperator): $LongStream;
        max(): $OptionalLong;
        min(): $OptionalLong;
        noneMatch(arg0: $LongPredicate): boolean;
        of(arg0?: number[]): $LongStream;
        of(arg0: number): $LongStream;
        parallel(): $BaseStream;
        parallel(): $LongStream;
        peek(arg0: $LongConsumer): $LongStream;
        rangeClosed(arg0: number, arg1: number): $LongStream;
        range(arg0: number, arg1: number): $LongStream;
        reduce(arg0: $LongBinaryOperator): $OptionalLong;
        reduce(arg0: number, arg1: $LongBinaryOperator): number;
        sequential(): $BaseStream;
        sequential(): $LongStream;
        skip(arg0: number): $LongStream;
        sorted(): $LongStream;
        spliterator(): $Spliterator;
        spliterator(): $Spliterator$OfLong;
        summaryStatistics(): $LongSummaryStatistics;
        sum(): number;
        takeWhile(arg0: $LongPredicate): $LongStream;
        toArray(): number[];
    }

    export interface $LongStream$Builder extends $LongConsumer {
        accept(arg0: number): void;
        add(arg0: number): $LongStream$Builder;
        build(): $LongStream;
    }

    export interface $LongStream$LongMapMultiConsumer {
        accept(arg0: number, arg1: $LongConsumer): void;
    }

    export interface $Stream<T> extends $BaseStream {
        allMatch(arg0: $Predicate<any>): boolean;
        anyMatch(arg0: $Predicate<any>): boolean;
        builder<T>(): $Stream$Builder<T>;
        collect<R>(arg0: $Supplier<R>, arg1: $BiConsumer<R, any>, arg2: $BiConsumer<R, R>): R;
        collect<R, A>(arg0: $Collector<any, A, R>): R;
        concat<T>(arg0: $Stream<T>, arg1: $Stream<T>): $Stream<T>;
        count(): number;
        distinct(): $Stream<T>;
        dropWhile(arg0: $Predicate<any>): $Stream<T>;
        empty<T>(): $Stream<T>;
        filter(arg0: $Predicate<any>): $Stream<T>;
        findAny(): $Optional<T>;
        findFirst(): $Optional<T>;
        flatMapToDouble(arg0: $Function<any, $DoubleStream>): $DoubleStream;
        flatMapToInt(arg0: $Function<any, $IntStream>): $IntStream;
        flatMapToLong(arg0: $Function<any, $LongStream>): $LongStream;
        flatMap<R>(arg0: $Function<any, $Stream<R>>): $Stream<R>;
        forEachOrdered(arg0: $Consumer<any>): void;
        forEach(arg0: $Consumer<any>): void;
        generate<T>(arg0: $Supplier<T>): $Stream<T>;
        iterate<T>(arg0: T, arg1: $Predicate<any>, arg2: $UnaryOperator<T>): $Stream<T>;
        iterate<T>(arg0: T, arg1: $UnaryOperator<T>): $Stream<T>;
        limit(arg0: number): $Stream<T>;
        mapMultiToDouble(arg0: $BiConsumer<any, any>): $DoubleStream;
        mapMultiToInt(arg0: $BiConsumer<any, any>): $IntStream;
        mapMultiToLong(arg0: $BiConsumer<any, any>): $LongStream;
        mapMulti<R>(arg0: $BiConsumer<any, any>): $Stream<R>;
        mapToDouble(arg0: $ToDoubleFunction<any>): $DoubleStream;
        mapToInt(arg0: $ToIntFunction<any>): $IntStream;
        mapToLong(arg0: $ToLongFunction<any>): $LongStream;
        map<R>(arg0: $Function<any, R>): $Stream<R>;
        max(arg0: $Comparator<any>): $Optional<T>;
        min(arg0: $Comparator<any>): $Optional<T>;
        noneMatch(arg0: $Predicate<any>): boolean;
        ofNullable<T>(arg0: T): $Stream<T>;
        of<T>(arg0: T): $Stream<T>;
        of<T>(arg0?: object[]): $Stream<T>;
        peek(arg0: $Consumer<any>): $Stream<T>;
        reduce(arg0: T, arg1: $BinaryOperator<T>): T;
        reduce<U>(arg0: U, arg1: $BiFunction<U, any, U>, arg2: $BinaryOperator<U>): U;
        reduce(arg0: $BinaryOperator<T>): $Optional<T>;
        skip(arg0: number): $Stream<T>;
        sorted(arg0: $Comparator<any>): $Stream<T>;
        sorted(): $Stream<T>;
        takeWhile(arg0: $Predicate<any>): $Stream<T>;
        toArray<A>(arg0: $IntFunction<A[]>): A[];
        toArray(): object[];
        toList(): $List<T>;
    }

    export interface $Stream$Builder<T> extends $Consumer {
        accept(arg0: T): void;
        add(arg0: T): $Stream$Builder<T>;
        build(): $Stream<T>;
    }

    export type $Stream_<T> = T[];
}
