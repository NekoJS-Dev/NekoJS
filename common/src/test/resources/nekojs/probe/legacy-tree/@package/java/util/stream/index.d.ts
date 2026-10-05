import { $AutoCloseable, $AutoCloseable_, $Double, $Enum, $Integer, $Long, $Runnable, $Runnable_, $String } from "java:java/lang";
import { $Comparator, $DoubleSummaryStatistics, $IntSummaryStatistics, $Iterator, $List, $LongSummaryStatistics, $Optional, $OptionalDouble, $OptionalInt, $OptionalLong, $PrimitiveIterator$OfDouble, $PrimitiveIterator$OfInt, $PrimitiveIterator$OfLong, $Set, $Spliterator, $Spliterator$OfDouble, $Spliterator$OfInt, $Spliterator$OfLong } from "java:java/util";
import { $BiConsumer, $BiConsumer_, $BiFunction, $BiFunction_, $BinaryOperator, $BinaryOperator_, $Consumer, $Consumer_, $DoubleBinaryOperator, $DoubleBinaryOperator_, $DoubleConsumer, $DoubleConsumer_, $DoubleFunction, $DoubleFunction_, $DoublePredicate, $DoublePredicate_, $DoubleSupplier, $DoubleSupplier_, $DoubleToIntFunction, $DoubleToIntFunction_, $DoubleToLongFunction, $DoubleToLongFunction_, $DoubleUnaryOperator, $DoubleUnaryOperator_, $Function, $Function_, $IntBinaryOperator, $IntBinaryOperator_, $IntConsumer, $IntConsumer_, $IntFunction, $IntFunction_, $IntPredicate, $IntPredicate_, $IntSupplier, $IntSupplier_, $IntToDoubleFunction, $IntToDoubleFunction_, $IntToLongFunction, $IntToLongFunction_, $IntUnaryOperator, $IntUnaryOperator_, $LongBinaryOperator, $LongBinaryOperator_, $LongConsumer, $LongConsumer_, $LongFunction, $LongFunction_, $LongPredicate, $LongPredicate_, $LongSupplier, $LongSupplier_, $LongToDoubleFunction, $LongToDoubleFunction_, $LongToIntFunction, $LongToIntFunction_, $LongUnaryOperator, $LongUnaryOperator_, $ObjDoubleConsumer, $ObjDoubleConsumer_, $ObjIntConsumer, $ObjIntConsumer_, $ObjLongConsumer, $ObjLongConsumer_, $Predicate, $Predicate_, $Supplier, $Supplier_, $ToDoubleFunction, $ToDoubleFunction_, $ToIntFunction, $ToIntFunction_, $ToLongFunction, $ToLongFunction_, $UnaryOperator, $UnaryOperator_ } from "java:java/util/function";

declare module "java:java/util/stream" {
    export interface $BaseStream<T, S extends $BaseStream<T, S>> extends $AutoCloseable {
        close(): void;
        isParallel(): boolean;
        iterator(): $Iterator<T>;
        onClose(arg0: $Runnable_): S;
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
        of<T, A, R>(arg0: $Supplier_<A>, arg1: $BiConsumer_<A, T>, arg2: $BinaryOperator_<A>, arg3: $Function_<A, R>, arg4?: $Collector$Characteristics_[]): $Collector<T, A, R>;
        of<T, R>(arg0: $Supplier_<R>, arg1: $BiConsumer_<R, T>, arg2: $BinaryOperator_<R>, arg3?: $Collector$Characteristics_[]): $Collector<T, R, R>;
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

    export interface $DoubleStream extends $BaseStream<number, $DoubleStream> {
        allMatch(arg0: $DoublePredicate_): boolean;
        anyMatch(arg0: $DoublePredicate_): boolean;
        average(): $OptionalDouble;
        boxed(): $Stream<number>;
        builder(): $DoubleStream$Builder;
        collect<R>(arg0: $Supplier_<R>, arg1: $ObjDoubleConsumer_<R>, arg2: $BiConsumer_<R, R>): R;
        concat(arg0: $DoubleStream, arg1: $DoubleStream): $DoubleStream;
        count(): number;
        distinct(): $DoubleStream;
        dropWhile(arg0: $DoublePredicate_): $DoubleStream;
        empty(): $DoubleStream;
        filter(arg0: $DoublePredicate_): $DoubleStream;
        findAny(): $OptionalDouble;
        findFirst(): $OptionalDouble;
        flatMap(arg0: $DoubleFunction_<$DoubleStream>): $DoubleStream;
        forEachOrdered(arg0: $DoubleConsumer_): void;
        forEach(arg0: $DoubleConsumer_): void;
        generate(arg0: $DoubleSupplier_): $DoubleStream;
        iterate(arg0: number, arg1: $DoublePredicate_, arg2: $DoubleUnaryOperator_): $DoubleStream;
        iterate(arg0: number, arg1: $DoubleUnaryOperator_): $DoubleStream;
        iterator(): $PrimitiveIterator$OfDouble;
        limit(arg0: number): $DoubleStream;
        mapMulti(arg0: $DoubleStream$DoubleMapMultiConsumer_): $DoubleStream;
        mapToInt(arg0: $DoubleToIntFunction_): $IntStream;
        mapToLong(arg0: $DoubleToLongFunction_): $LongStream;
        mapToObj<U>(arg0: $DoubleFunction_<U>): $Stream<U>;
        map(arg0: $DoubleUnaryOperator_): $DoubleStream;
        max(): $OptionalDouble;
        min(): $OptionalDouble;
        noneMatch(arg0: $DoublePredicate_): boolean;
        of(arg0?: number[]): $DoubleStream;
        of(arg0: number): $DoubleStream;
        parallel(): $DoubleStream;
        peek(arg0: $DoubleConsumer_): $DoubleStream;
        reduce(arg0: number, arg1: $DoubleBinaryOperator_): number;
        reduce(arg0: $DoubleBinaryOperator_): $OptionalDouble;
        sequential(): $DoubleStream;
        skip(arg0: number): $DoubleStream;
        sorted(): $DoubleStream;
        spliterator(): $Spliterator$OfDouble;
        summaryStatistics(): $DoubleSummaryStatistics;
        sum(): number;
        takeWhile(arg0: $DoublePredicate_): $DoubleStream;
        toArray(): number[];
    }

    export interface $DoubleStream$Builder extends $DoubleConsumer {
        accept(arg0: number): void;
        add(arg0: number): $DoubleStream$Builder;
        build(): $DoubleStream;
    }

    export interface $DoubleStream$DoubleMapMultiConsumer {
        accept(arg0: number, arg1: $DoubleConsumer_): void;
    }

    export interface $IntStream extends $BaseStream<number, $IntStream> {
        allMatch(arg0: $IntPredicate_): boolean;
        anyMatch(arg0: $IntPredicate_): boolean;
        asDoubleStream(): $DoubleStream;
        asLongStream(): $LongStream;
        average(): $OptionalDouble;
        boxed(): $Stream<number>;
        builder(): $IntStream$Builder;
        collect<R>(arg0: $Supplier_<R>, arg1: $ObjIntConsumer_<R>, arg2: $BiConsumer_<R, R>): R;
        concat(arg0: $IntStream, arg1: $IntStream): $IntStream;
        count(): number;
        distinct(): $IntStream;
        dropWhile(arg0: $IntPredicate_): $IntStream;
        empty(): $IntStream;
        filter(arg0: $IntPredicate_): $IntStream;
        findAny(): $OptionalInt;
        findFirst(): $OptionalInt;
        flatMap(arg0: $IntFunction_<$IntStream>): $IntStream;
        forEachOrdered(arg0: $IntConsumer_): void;
        forEach(arg0: $IntConsumer_): void;
        generate(arg0: $IntSupplier_): $IntStream;
        iterate(arg0: number, arg1: $IntPredicate_, arg2: $IntUnaryOperator_): $IntStream;
        iterate(arg0: number, arg1: $IntUnaryOperator_): $IntStream;
        iterator(): $PrimitiveIterator$OfInt;
        limit(arg0: number): $IntStream;
        mapMulti(arg0: $IntStream$IntMapMultiConsumer_): $IntStream;
        mapToDouble(arg0: $IntToDoubleFunction_): $DoubleStream;
        mapToLong(arg0: $IntToLongFunction_): $LongStream;
        mapToObj<U>(arg0: $IntFunction_<U>): $Stream<U>;
        map(arg0: $IntUnaryOperator_): $IntStream;
        max(): $OptionalInt;
        min(): $OptionalInt;
        noneMatch(arg0: $IntPredicate_): boolean;
        of(arg0?: number[]): $IntStream;
        of(arg0: number): $IntStream;
        parallel(): $IntStream;
        peek(arg0: $IntConsumer_): $IntStream;
        rangeClosed(arg0: number, arg1: number): $IntStream;
        range(arg0: number, arg1: number): $IntStream;
        reduce(arg0: number, arg1: $IntBinaryOperator_): number;
        reduce(arg0: $IntBinaryOperator_): $OptionalInt;
        sequential(): $IntStream;
        skip(arg0: number): $IntStream;
        sorted(): $IntStream;
        spliterator(): $Spliterator$OfInt;
        summaryStatistics(): $IntSummaryStatistics;
        sum(): number;
        takeWhile(arg0: $IntPredicate_): $IntStream;
        toArray(): number[];
    }

    export interface $IntStream$Builder extends $IntConsumer {
        accept(arg0: number): void;
        add(arg0: number): $IntStream$Builder;
        build(): $IntStream;
    }

    export interface $IntStream$IntMapMultiConsumer {
        accept(arg0: number, arg1: $IntConsumer_): void;
    }

    export interface $LongStream extends $BaseStream<number, $LongStream> {
        allMatch(arg0: $LongPredicate_): boolean;
        anyMatch(arg0: $LongPredicate_): boolean;
        asDoubleStream(): $DoubleStream;
        average(): $OptionalDouble;
        boxed(): $Stream<number>;
        builder(): $LongStream$Builder;
        collect<R>(arg0: $Supplier_<R>, arg1: $ObjLongConsumer_<R>, arg2: $BiConsumer_<R, R>): R;
        concat(arg0: $LongStream, arg1: $LongStream): $LongStream;
        count(): number;
        distinct(): $LongStream;
        dropWhile(arg0: $LongPredicate_): $LongStream;
        empty(): $LongStream;
        filter(arg0: $LongPredicate_): $LongStream;
        findAny(): $OptionalLong;
        findFirst(): $OptionalLong;
        flatMap(arg0: $LongFunction_<$LongStream>): $LongStream;
        forEachOrdered(arg0: $LongConsumer_): void;
        forEach(arg0: $LongConsumer_): void;
        generate(arg0: $LongSupplier_): $LongStream;
        iterate(arg0: number, arg1: $LongPredicate_, arg2: $LongUnaryOperator_): $LongStream;
        iterate(arg0: number, arg1: $LongUnaryOperator_): $LongStream;
        iterator(): $PrimitiveIterator$OfLong;
        limit(arg0: number): $LongStream;
        mapMulti(arg0: $LongStream$LongMapMultiConsumer_): $LongStream;
        mapToDouble(arg0: $LongToDoubleFunction_): $DoubleStream;
        mapToInt(arg0: $LongToIntFunction_): $IntStream;
        mapToObj<U>(arg0: $LongFunction_<U>): $Stream<U>;
        map(arg0: $LongUnaryOperator_): $LongStream;
        max(): $OptionalLong;
        min(): $OptionalLong;
        noneMatch(arg0: $LongPredicate_): boolean;
        of(arg0?: number[]): $LongStream;
        of(arg0: number): $LongStream;
        parallel(): $LongStream;
        peek(arg0: $LongConsumer_): $LongStream;
        rangeClosed(arg0: number, arg1: number): $LongStream;
        range(arg0: number, arg1: number): $LongStream;
        reduce(arg0: $LongBinaryOperator_): $OptionalLong;
        reduce(arg0: number, arg1: $LongBinaryOperator_): number;
        sequential(): $LongStream;
        skip(arg0: number): $LongStream;
        sorted(): $LongStream;
        spliterator(): $Spliterator$OfLong;
        summaryStatistics(): $LongSummaryStatistics;
        sum(): number;
        takeWhile(arg0: $LongPredicate_): $LongStream;
        toArray(): number[];
    }

    export interface $LongStream$Builder extends $LongConsumer {
        accept(arg0: number): void;
        add(arg0: number): $LongStream$Builder;
        build(): $LongStream;
    }

    export interface $LongStream$LongMapMultiConsumer {
        accept(arg0: number, arg1: $LongConsumer_): void;
    }

    export interface $Stream<T> extends $BaseStream<T, $Stream<T>> {
        allMatch(arg0: $Predicate_<T>): boolean;
        anyMatch(arg0: $Predicate_<T>): boolean;
        builder<T>(): $Stream$Builder<T>;
        collect<R>(arg0: $Supplier_<R>, arg1: $BiConsumer_<R, T>, arg2: $BiConsumer_<R, R>): R;
        collect<R, A>(arg0: $Collector<T, A, R>): R;
        concat<T>(arg0: $Stream<T>, arg1: $Stream<T>): $Stream<T>;
        count(): number;
        distinct(): $Stream<T>;
        dropWhile(arg0: $Predicate_<T>): $Stream<T>;
        empty<T>(): $Stream<T>;
        filter(arg0: $Predicate_<T>): $Stream<T>;
        findAny(): $Optional<T>;
        findFirst(): $Optional<T>;
        flatMapToDouble(arg0: $Function_<T, $DoubleStream>): $DoubleStream;
        flatMapToInt(arg0: $Function_<T, $IntStream>): $IntStream;
        flatMapToLong(arg0: $Function_<T, $LongStream>): $LongStream;
        flatMap<R>(arg0: $Function_<T, $Stream<R>>): $Stream<R>;
        forEachOrdered(arg0: $Consumer_<T>): void;
        forEach(arg0: $Consumer_<T>): void;
        generate<T>(arg0: $Supplier_<T>): $Stream<T>;
        iterate<T>(arg0: T, arg1: $Predicate_<T>, arg2: $UnaryOperator_<T>): $Stream<T>;
        iterate<T>(arg0: T, arg1: $UnaryOperator_<T>): $Stream<T>;
        limit(arg0: number): $Stream<T>;
        mapMultiToDouble(arg0: $BiConsumer_<T, $DoubleConsumer_>): $DoubleStream;
        mapMultiToInt(arg0: $BiConsumer_<T, $IntConsumer_>): $IntStream;
        mapMultiToLong(arg0: $BiConsumer_<T, $LongConsumer_>): $LongStream;
        mapMulti<R>(arg0: $BiConsumer_<T, $Consumer_<R>>): $Stream<R>;
        mapToDouble(arg0: $ToDoubleFunction_<T>): $DoubleStream;
        mapToInt(arg0: $ToIntFunction_<T>): $IntStream;
        mapToLong(arg0: $ToLongFunction_<T>): $LongStream;
        map<R>(arg0: $Function_<T, R>): $Stream<R>;
        max(arg0: $Comparator<T>): $Optional<T>;
        min(arg0: $Comparator<T>): $Optional<T>;
        noneMatch(arg0: $Predicate_<T>): boolean;
        ofNullable<T>(arg0: T): $Stream<T>;
        of<T>(arg0?: T[]): $Stream<T>;
        of<T>(arg0: T): $Stream<T>;
        peek(arg0: $Consumer_<T>): $Stream<T>;
        reduce(arg0: T, arg1: $BinaryOperator_<T>): T;
        reduce<U>(arg0: U, arg1: $BiFunction_<U, T, U>, arg2: $BinaryOperator_<U>): U;
        reduce(arg0: $BinaryOperator_<T>): $Optional<T>;
        skip(arg0: number): $Stream<T>;
        sorted(arg0: $Comparator<T>): $Stream<T>;
        sorted(): $Stream<T>;
        takeWhile(arg0: $Predicate_<T>): $Stream<T>;
        toArray<A>(arg0: $IntFunction_<A[]>): A[];
        toArray(): object[];
        toList(): $List<T>;
    }

    export interface $Stream$Builder<T> extends $Consumer<T> {
        accept(arg0: T): void;
        add(arg0: T): $Stream$Builder<T>;
        build(): $Stream<T>;
    }

    export type $Collector$Characteristics_ = $Collector$Characteristics | "CONCURRENT" | "IDENTITY_FINISH" | "UNORDERED";
    export type $DoubleStream$DoubleMapMultiConsumer_ = (arg0: number, arg1: $DoubleConsumer_) => void;
    export type $IntStream$IntMapMultiConsumer_ = (arg0: number, arg1: $IntConsumer_) => void;
    export type $LongStream$LongMapMultiConsumer_ = (arg0: number, arg1: $LongConsumer_) => void;
    export type $Stream_<T> = T[];
}
