import { $AutoCloseable, $AutoCloseable_, $Enum, $Runnable, $Runnable_ } from "java:java/lang";
import { $Collection, $Comparator, $Comparator_, $DoubleSummaryStatistics, $IntSummaryStatistics, $Iterator, $List, $ListView, $List_, $LongSummaryStatistics, $Optional, $OptionalDouble, $OptionalInt, $OptionalLong, $OptionalView, $PrimitiveIterator$OfDouble, $PrimitiveIterator$OfInt, $PrimitiveIterator$OfLong, $Set, $Set_, $Spliterator, $Spliterator$OfDouble, $Spliterator$OfInt, $Spliterator$OfLong } from "java:java/util";
import { $BiConsumer, $BiConsumer_, $BiFunction, $BiFunction_, $BinaryOperator, $BinaryOperator_, $Consumer, $Consumer_, $DoubleBinaryOperator, $DoubleBinaryOperator_, $DoubleConsumer, $DoubleConsumer_, $DoubleFunction, $DoubleFunction_, $DoublePredicate, $DoublePredicate_, $DoubleSupplier, $DoubleSupplier_, $DoubleToIntFunction, $DoubleToIntFunction_, $DoubleToLongFunction, $DoubleToLongFunction_, $DoubleUnaryOperator, $DoubleUnaryOperator_, $Function, $Function_, $IntBinaryOperator, $IntBinaryOperator_, $IntConsumer, $IntConsumer_, $IntFunction, $IntFunction_, $IntPredicate, $IntPredicate_, $IntSupplier, $IntSupplier_, $IntToDoubleFunction, $IntToDoubleFunction_, $IntToLongFunction, $IntToLongFunction_, $IntUnaryOperator, $IntUnaryOperator_, $LongBinaryOperator, $LongBinaryOperator_, $LongConsumer, $LongConsumer_, $LongFunction, $LongFunction_, $LongPredicate, $LongPredicate_, $LongSupplier, $LongSupplier_, $LongToDoubleFunction, $LongToDoubleFunction_, $LongToIntFunction, $LongToIntFunction_, $LongUnaryOperator, $LongUnaryOperator_, $ObjDoubleConsumer, $ObjDoubleConsumer_, $ObjIntConsumer, $ObjIntConsumer_, $ObjLongConsumer, $ObjLongConsumer_, $Predicate, $Predicate_, $Supplier, $Supplier_, $ToDoubleFunction, $ToDoubleFunction_, $ToIntFunction, $ToIntFunction_, $ToLongFunction, $ToLongFunction_, $UnaryOperator, $UnaryOperator_ } from "java:java/util/function";

declare module "java:java/util/stream" {
    export interface $BaseStream<T, S extends $BaseStream<T, S>> extends $AutoCloseable {
        isParallel(): boolean;
        close(): void;
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
        supplier(): $Supplier<A>;
    }
    export const $Collector: {
        of<T, A, R>(
            arg0: $Supplier_<A, A>,
            arg1: $BiConsumer_<A, T, A, T>,
            arg2: $BinaryOperator_<A, A, A, A>,
            arg3: $Function_<A, R, A, R>,
            arg4?: $Collector$Characteristics_[]
        ): $Collector<T, A, R>;
        of<T, R>(
            arg0: $Supplier_<R, R>,
            arg1: $BiConsumer_<R, T, R, T>,
            arg2: $BinaryOperator_<R, R, R, R>,
            arg3?: $Collector$Characteristics_[]
        ): $Collector<T, R, R>;
    };

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
    export interface $Collector$Characteristics extends $Enum<$Collector$Characteristics> {}

    export interface $DoubleStream extends $BaseStream<number, $DoubleStream> {
        allMatch(arg0: $DoublePredicate_<number, boolean>): boolean;
        anyMatch(arg0: $DoublePredicate_<number, boolean>): boolean;
        average(): $OptionalDouble;
        boxed(): $Stream<number>;
        collect<R>(arg0: $Supplier_<R, R>, arg1: $ObjDoubleConsumer_<R, R, number>, arg2: $BiConsumer_<R, R, R, R>): R;
        count(): number;
        distinct(): $DoubleStream;
        dropWhile(arg0: $DoublePredicate_<number, boolean>): $DoubleStream;
        filter(arg0: $DoublePredicate_<number, boolean>): $DoubleStream;
        findAny(): $OptionalDouble;
        findFirst(): $OptionalDouble;
        flatMap(arg0: $DoubleFunction_<$DoubleStream, number, $DoubleStream>): $DoubleStream;
        forEachOrdered(arg0: $DoubleConsumer_<number>): void;
        forEach(arg0: $DoubleConsumer_<number>): void;
        iterator(): $PrimitiveIterator$OfDouble;
        limit(arg0: number): $DoubleStream;
        mapMulti(arg0: $DoubleStream$DoubleMapMultiConsumer_<number, $DoubleConsumer>): $DoubleStream;
        mapToInt(arg0: $DoubleToIntFunction_<number, number>): $IntStream;
        mapToLong(arg0: $DoubleToLongFunction_<number, number>): $LongStream;
        mapToObj<U>(arg0: $DoubleFunction_<U, number, U>): $Stream<U>;
        map(arg0: $DoubleUnaryOperator_<number, number>): $DoubleStream;
        max(): $OptionalDouble;
        min(): $OptionalDouble;
        noneMatch(arg0: $DoublePredicate_<number, boolean>): boolean;
        parallel(): $DoubleStream;
        peek(arg0: $DoubleConsumer_<number>): $DoubleStream;
        reduce(arg0: number, arg1: $DoubleBinaryOperator_<number, number, number>): number;
        reduce(arg0: $DoubleBinaryOperator_<number, number, number>): $OptionalDouble;
        sequential(): $DoubleStream;
        skip(arg0: number): $DoubleStream;
        sorted(): $DoubleStream;
        spliterator(): $Spliterator$OfDouble;
        summaryStatistics(): $DoubleSummaryStatistics;
        sum(): number;
        takeWhile(arg0: $DoublePredicate_<number, boolean>): $DoubleStream;
        toArray(): number[];
    }
    export const $DoubleStream: {
        builder(): $DoubleStream$Builder;
        concat(arg0: $DoubleStream, arg1: $DoubleStream): $DoubleStream;
        empty(): $DoubleStream;
        generate(arg0: $DoubleSupplier_<number>): $DoubleStream;
        iterate(
            arg0: number,
            arg1: $DoublePredicate_<number, boolean>,
            arg2: $DoubleUnaryOperator_<number, number>
        ): $DoubleStream;
        iterate(arg0: number, arg1: $DoubleUnaryOperator_<number, number>): $DoubleStream;
        of(arg0?: number[]): $DoubleStream;
        of(arg0: number): $DoubleStream;
    };

    export interface $DoubleStream$Builder extends $DoubleConsumer {
        accept(arg0: number): void;
        add(arg0: number): $DoubleStream$Builder;
        build(): $DoubleStream;
    }

    export interface $DoubleStream$DoubleMapMultiConsumer {
        accept(arg0: number, arg1: $DoubleConsumer_<number>): void;
    }

    export interface $IntStream extends $BaseStream<number, $IntStream> {
        allMatch(arg0: $IntPredicate_<number, boolean>): boolean;
        anyMatch(arg0: $IntPredicate_<number, boolean>): boolean;
        asDoubleStream(): $DoubleStream;
        asLongStream(): $LongStream;
        average(): $OptionalDouble;
        boxed(): $Stream<number>;
        collect<R>(arg0: $Supplier_<R, R>, arg1: $ObjIntConsumer_<R, R, number>, arg2: $BiConsumer_<R, R, R, R>): R;
        count(): number;
        distinct(): $IntStream;
        dropWhile(arg0: $IntPredicate_<number, boolean>): $IntStream;
        filter(arg0: $IntPredicate_<number, boolean>): $IntStream;
        findAny(): $OptionalInt;
        findFirst(): $OptionalInt;
        flatMap(arg0: $IntFunction_<$IntStream, number, $IntStream>): $IntStream;
        forEachOrdered(arg0: $IntConsumer_<number>): void;
        forEach(arg0: $IntConsumer_<number>): void;
        iterator(): $PrimitiveIterator$OfInt;
        limit(arg0: number): $IntStream;
        mapMulti(arg0: $IntStream$IntMapMultiConsumer_<number, $IntConsumer>): $IntStream;
        mapToDouble(arg0: $IntToDoubleFunction_<number, number>): $DoubleStream;
        mapToLong(arg0: $IntToLongFunction_<number, number>): $LongStream;
        mapToObj<U>(arg0: $IntFunction_<U, number, U>): $Stream<U>;
        map(arg0: $IntUnaryOperator_<number, number>): $IntStream;
        max(): $OptionalInt;
        min(): $OptionalInt;
        noneMatch(arg0: $IntPredicate_<number, boolean>): boolean;
        parallel(): $IntStream;
        peek(arg0: $IntConsumer_<number>): $IntStream;
        reduce(arg0: number, arg1: $IntBinaryOperator_<number, number, number>): number;
        reduce(arg0: $IntBinaryOperator_<number, number, number>): $OptionalInt;
        sequential(): $IntStream;
        skip(arg0: number): $IntStream;
        sorted(): $IntStream;
        spliterator(): $Spliterator$OfInt;
        summaryStatistics(): $IntSummaryStatistics;
        sum(): number;
        takeWhile(arg0: $IntPredicate_<number, boolean>): $IntStream;
        toArray(): number[];
    }
    export const $IntStream: {
        builder(): $IntStream$Builder;
        concat(arg0: $IntStream, arg1: $IntStream): $IntStream;
        empty(): $IntStream;
        generate(arg0: $IntSupplier_<number>): $IntStream;
        iterate(
            arg0: number,
            arg1: $IntPredicate_<number, boolean>,
            arg2: $IntUnaryOperator_<number, number>
        ): $IntStream;
        iterate(arg0: number, arg1: $IntUnaryOperator_<number, number>): $IntStream;
        of(arg0?: number[]): $IntStream;
        of(arg0: number): $IntStream;
        rangeClosed(arg0: number, arg1: number): $IntStream;
        range(arg0: number, arg1: number): $IntStream;
    };

    export interface $IntStream$Builder extends $IntConsumer {
        accept(arg0: number): void;
        add(arg0: number): $IntStream$Builder;
        build(): $IntStream;
    }

    export interface $IntStream$IntMapMultiConsumer {
        accept(arg0: number, arg1: $IntConsumer_<number>): void;
    }

    export interface $LongStream extends $BaseStream<number, $LongStream> {
        allMatch(arg0: $LongPredicate_<number, boolean>): boolean;
        anyMatch(arg0: $LongPredicate_<number, boolean>): boolean;
        asDoubleStream(): $DoubleStream;
        average(): $OptionalDouble;
        boxed(): $Stream<number>;
        collect<R>(arg0: $Supplier_<R, R>, arg1: $ObjLongConsumer_<R, R, number>, arg2: $BiConsumer_<R, R, R, R>): R;
        count(): number;
        distinct(): $LongStream;
        dropWhile(arg0: $LongPredicate_<number, boolean>): $LongStream;
        filter(arg0: $LongPredicate_<number, boolean>): $LongStream;
        findAny(): $OptionalLong;
        findFirst(): $OptionalLong;
        flatMap(arg0: $LongFunction_<$LongStream, number, $LongStream>): $LongStream;
        forEachOrdered(arg0: $LongConsumer_<number>): void;
        forEach(arg0: $LongConsumer_<number>): void;
        iterator(): $PrimitiveIterator$OfLong;
        limit(arg0: number): $LongStream;
        mapMulti(arg0: $LongStream$LongMapMultiConsumer_<number, $LongConsumer>): $LongStream;
        mapToDouble(arg0: $LongToDoubleFunction_<number, number>): $DoubleStream;
        mapToInt(arg0: $LongToIntFunction_<number, number>): $IntStream;
        mapToObj<U>(arg0: $LongFunction_<U, number, U>): $Stream<U>;
        map(arg0: $LongUnaryOperator_<number, number>): $LongStream;
        max(): $OptionalLong;
        min(): $OptionalLong;
        noneMatch(arg0: $LongPredicate_<number, boolean>): boolean;
        parallel(): $LongStream;
        peek(arg0: $LongConsumer_<number>): $LongStream;
        reduce(arg0: $LongBinaryOperator_<number, number, number>): $OptionalLong;
        reduce(arg0: number, arg1: $LongBinaryOperator_<number, number, number>): number;
        sequential(): $LongStream;
        skip(arg0: number): $LongStream;
        sorted(): $LongStream;
        spliterator(): $Spliterator$OfLong;
        summaryStatistics(): $LongSummaryStatistics;
        sum(): number;
        takeWhile(arg0: $LongPredicate_<number, boolean>): $LongStream;
        toArray(): number[];
    }
    export const $LongStream: {
        builder(): $LongStream$Builder;
        concat(arg0: $LongStream, arg1: $LongStream): $LongStream;
        empty(): $LongStream;
        generate(arg0: $LongSupplier_<number>): $LongStream;
        iterate(
            arg0: number,
            arg1: $LongPredicate_<number, boolean>,
            arg2: $LongUnaryOperator_<number, number>
        ): $LongStream;
        iterate(arg0: number, arg1: $LongUnaryOperator_<number, number>): $LongStream;
        of(arg0?: number[]): $LongStream;
        of(arg0: number): $LongStream;
        rangeClosed(arg0: number, arg1: number): $LongStream;
        range(arg0: number, arg1: number): $LongStream;
    };

    export interface $LongStream$Builder extends $LongConsumer {
        accept(arg0: number): void;
        add(arg0: number): $LongStream$Builder;
        build(): $LongStream;
    }

    export interface $LongStream$LongMapMultiConsumer {
        accept(arg0: number, arg1: $LongConsumer_<number>): void;
    }

    export interface $Stream<T> extends $BaseStream<T, $Stream<T>> {
        allMatch(arg0: $Predicate_<T, T, boolean>): boolean;
        anyMatch(arg0: $Predicate_<T, T, boolean>): boolean;
        collect<R>(arg0: $Supplier_<R, R>, arg1: $BiConsumer_<R, T, R, T>, arg2: $BiConsumer_<R, R, R, R>): R;
        collect<R, A>(arg0: $Collector<any, A, R>): R;
        count(): number;
        distinct(): $Stream<T>;
        dropWhile(arg0: $Predicate_<T, T, boolean>): $Stream<T>;
        filter(arg0: $Predicate_<T, T, boolean>): $Stream<T>;
        findAny(): $Optional<T>;
        findFirst(): $Optional<T>;
        flatMapToDouble(arg0: $Function_<T, $DoubleStream, T, $DoubleStream>): $DoubleStream;
        flatMapToInt(arg0: $Function_<T, $IntStream, T, $IntStream>): $IntStream;
        flatMapToLong(arg0: $Function_<T, $LongStream, T, $LongStream>): $LongStream;
        flatMap<R>(arg0: $Function_<T, $Stream<R>, T, $Stream<R>>): $Stream<R>;
        forEachOrdered(arg0: $Consumer_<T, T>): void;
        forEach(arg0: $Consumer_<T, T>): void;
        limit(arg0: number): $Stream<T>;
        mapMultiToDouble(arg0: $BiConsumer_<T, $DoubleConsumer, T, $DoubleConsumer>): $DoubleStream;
        mapMultiToInt(arg0: $BiConsumer_<T, $IntConsumer, T, $IntConsumer>): $IntStream;
        mapMultiToLong(arg0: $BiConsumer_<T, $LongConsumer, T, $LongConsumer>): $LongStream;
        mapMulti<R>(arg0: $BiConsumer_<T, $Consumer<R>, T, $Consumer<R>>): $Stream<R>;
        mapToDouble(arg0: $ToDoubleFunction_<T, T, number>): $DoubleStream;
        mapToInt(arg0: $ToIntFunction_<T, T, number>): $IntStream;
        mapToLong(arg0: $ToLongFunction_<T, T, number>): $LongStream;
        map<R>(arg0: $Function_<T, R, T, R>): $Stream<R>;
        max(arg0: $Comparator_<T, T, T, number>): $Optional<T>;
        min(arg0: $Comparator_<T, T, T, number>): $Optional<T>;
        noneMatch(arg0: $Predicate_<T, T, boolean>): boolean;
        peek(arg0: $Consumer_<T, T>): $Stream<T>;
        reduce(arg0: T, arg1: $BinaryOperator_<T, T, T, T>): T;
        reduce<U>(arg0: U, arg1: $BiFunction_<U, T, U, U, T, U>, arg2: $BinaryOperator_<U, U, U, U>): U;
        reduce(arg0: $BinaryOperator_<T, T, T, T>): $Optional<T>;
        skip(arg0: number): $Stream<T>;
        sorted(arg0: $Comparator_<T, T, T, number>): $Stream<T>;
        sorted(): $Stream<T>;
        takeWhile(arg0: $Predicate_<T, T, boolean>): $Stream<T>;
        toArray<A>(arg0: $IntFunction_<A[], number, A[]>): A[];
        toArray(): unknown[];
        toList(): $List<T>;
    }
    export const $Stream: {
        builder<T>(): $Stream$Builder<T>;
        concat<T>(arg0: $Stream<T>, arg1: $Stream<T>): $Stream<T>;
        empty<T>(): $Stream<T>;
        generate<T>(arg0: $Supplier_<T, T>): $Stream<T>;
        iterate<T>(arg0: T, arg1: $Predicate_<T, T, boolean>, arg2: $UnaryOperator_<T, T, T>): $Stream<T>;
        iterate<T>(arg0: T, arg1: $UnaryOperator_<T, T, T>): $Stream<T>;
        ofNullable<T>(arg0: T): $Stream<T>;
        of<T>(arg0?: T[]): $Stream<T>;
        of<T>(arg0: T): $Stream<T>;
    };

    export interface $Stream$Builder<T> extends $Consumer<T> {
        accept(arg0: T): void;
        add(arg0: T): $Stream$Builder<T>;
        build(): $Stream<T>;
    }

    export type $Collector$Characteristics_ = $Collector$Characteristics | "CONCURRENT" | "IDENTITY_FINISH" | "UNORDERED";
    export type $DoubleStream$DoubleMapMultiConsumer_<CallbackArg0 = number, CallbackArg1 = $DoubleConsumer> = ((arg0: CallbackArg0, arg1: CallbackArg1) => void) | ($DoubleStream$DoubleMapMultiConsumer & { readonly [Symbol.hasInstance]?: never }) | (globalThis.NoInfer<{ readonly "accept": (arg0: CallbackArg0, arg1: CallbackArg1) => void } & { readonly [Symbol.hasInstance]?: never }>);
    export type $IntStream$IntMapMultiConsumer_<CallbackArg0 = number, CallbackArg1 = $IntConsumer> = ((arg0: CallbackArg0, arg1: CallbackArg1) => void) | ($IntStream$IntMapMultiConsumer & { readonly [Symbol.hasInstance]?: never }) | (globalThis.NoInfer<{ readonly "accept": (arg0: CallbackArg0, arg1: CallbackArg1) => void } & { readonly [Symbol.hasInstance]?: never }>);
    export type $LongStream$LongMapMultiConsumer_<CallbackArg0 = number, CallbackArg1 = $LongConsumer> = ((arg0: CallbackArg0, arg1: CallbackArg1) => void) | ($LongStream$LongMapMultiConsumer & { readonly [Symbol.hasInstance]?: never }) | (globalThis.NoInfer<{ readonly "accept": (arg0: CallbackArg0, arg1: CallbackArg1) => void } & { readonly [Symbol.hasInstance]?: never }>);
    export type $Stream_<T> = T[];
    type $NekoCaptureCollectionInput<$Value> = globalThis.NoInfer<$Collection<any>> & { readonly "iterator": () => $Iterator<$Value> };
    type $NekoCaptureOptionalInput<$Value> = globalThis.NoInfer<$Optional<any>> & { readonly "get": () => $Value };
    type $NekoCaptureStreamInput<$Value> = globalThis.NoInfer<$Stream<any>> & { readonly "iterator": () => $Iterator<$Value> };
    export interface $StreamView<B> extends $Stream<B> {
        allMatch(
            arg0: $Predicate<B> & { readonly "test": (arg0: B) => boolean } & { readonly [Symbol.hasInstance]?: never }
        ): boolean;
        allMatch(
            arg0: ((arg0: B) => boolean) | globalThis.NoInfer<{ readonly "test": (arg0: B) => boolean } & { readonly [Symbol.hasInstance]?: never }>
        ): boolean;
        anyMatch(
            arg0: $Predicate<B> & { readonly "test": (arg0: B) => boolean } & { readonly [Symbol.hasInstance]?: never }
        ): boolean;
        anyMatch(
            arg0: ((arg0: B) => boolean) | globalThis.NoInfer<{ readonly "test": (arg0: B) => boolean } & { readonly [Symbol.hasInstance]?: never }>
        ): boolean;
        collect<R>(
            arg0: $Supplier_<R, R>,
            arg1: $BiConsumer<R, B> & { readonly "accept": (arg0: R, arg1: B) => void } & { readonly [Symbol.hasInstance]?: never },
            arg2: $BiConsumer_<R, R, R, R>
        ): R;
        collect<R>(
            arg0: $Supplier_<R, R>,
            arg1: ((arg0: R, arg1: B) => void) | globalThis.NoInfer<{ readonly "accept": (arg0: R, arg1: B) => void } & { readonly [Symbol.hasInstance]?: never }>,
            arg2: $BiConsumer_<R, R, R, R>
        ): R;
        collect<R, A>(
            arg0: $Collector<B, A, R> & { readonly "accumulator": () => { readonly "accept": (arg0: A, arg1: B) => void } }
        ): R;
        distinct(): $StreamView<B>;
        dropWhile(
            arg0: $Predicate<B> & { readonly "test": (arg0: B) => boolean } & { readonly [Symbol.hasInstance]?: never }
        ): $StreamView<B>;
        dropWhile(
            arg0: ((arg0: B) => boolean) | globalThis.NoInfer<{ readonly "test": (arg0: B) => boolean } & { readonly [Symbol.hasInstance]?: never }>
        ): $StreamView<B>;
        filter(
            arg0: $Predicate<B> & { readonly "test": (arg0: B) => boolean } & { readonly [Symbol.hasInstance]?: never }
        ): $StreamView<B>;
        filter(
            arg0: ((arg0: B) => boolean) | globalThis.NoInfer<{ readonly "test": (arg0: B) => boolean } & { readonly [Symbol.hasInstance]?: never }>
        ): $StreamView<B>;
        findAny(): $OptionalView<B>;
        findFirst(): $OptionalView<B>;
        flatMapToDouble(
            arg0: $Function<B, $DoubleStream> & { readonly "apply": (arg0: B) => $DoubleStream } & { readonly [Symbol.hasInstance]?: never }
        ): $DoubleStream;
        flatMapToDouble(
            arg0: ((arg0: B) => $DoubleStream) | globalThis.NoInfer<{ readonly "apply": (arg0: B) => $DoubleStream } & { readonly [Symbol.hasInstance]?: never }>
        ): $DoubleStream;
        flatMapToInt(
            arg0: $Function<B, $IntStream> & { readonly "apply": (arg0: B) => $IntStream } & { readonly [Symbol.hasInstance]?: never }
        ): $IntStream;
        flatMapToInt(
            arg0: ((arg0: B) => $IntStream) | globalThis.NoInfer<{ readonly "apply": (arg0: B) => $IntStream } & { readonly [Symbol.hasInstance]?: never }>
        ): $IntStream;
        flatMapToLong(
            arg0: $Function<B, $LongStream> & { readonly "apply": (arg0: B) => $LongStream } & { readonly [Symbol.hasInstance]?: never }
        ): $LongStream;
        flatMapToLong(
            arg0: ((arg0: B) => $LongStream) | globalThis.NoInfer<{ readonly "apply": (arg0: B) => $LongStream } & { readonly [Symbol.hasInstance]?: never }>
        ): $LongStream;
        flatMap<R>(
            arg0: $Function<B, $NekoCaptureStreamInput<R>> & { readonly "apply": (arg0: B) => $NekoCaptureStreamInput<R> } & { readonly [Symbol.hasInstance]?: never }
        ): $StreamView<R>;
        flatMap<R>(
            arg0: ((arg0: B) => $NekoCaptureStreamInput<R>) | globalThis.NoInfer<{ readonly "apply": (arg0: B) => $NekoCaptureStreamInput<R> } & { readonly [Symbol.hasInstance]?: never }>
        ): $StreamView<R>;
        forEachOrdered(
            arg0: $Consumer<B> & { readonly "accept": (arg0: B) => void } & { readonly [Symbol.hasInstance]?: never }
        ): void;
        forEachOrdered(
            arg0: ((arg0: B) => void) | globalThis.NoInfer<{ readonly "accept": (arg0: B) => void } & { readonly [Symbol.hasInstance]?: never }>
        ): void;
        forEach(
            arg0: $Consumer<B> & { readonly "accept": (arg0: B) => void } & { readonly [Symbol.hasInstance]?: never }
        ): void;
        forEach(
            arg0: ((arg0: B) => void) | globalThis.NoInfer<{ readonly "accept": (arg0: B) => void } & { readonly [Symbol.hasInstance]?: never }>
        ): void;
        limit(arg0: number): $StreamView<B>;
        mapMultiToDouble(
            arg0: $BiConsumer<B, $DoubleConsumer> & { readonly "accept": (arg0: B, arg1: $DoubleConsumer) => void } & { readonly [Symbol.hasInstance]?: never }
        ): $DoubleStream;
        mapMultiToDouble(
            arg0: ((arg0: B, arg1: $DoubleConsumer) => void) | globalThis.NoInfer<{ readonly "accept": (arg0: B, arg1: $DoubleConsumer) => void } & { readonly [Symbol.hasInstance]?: never }>
        ): $DoubleStream;
        mapMultiToInt(
            arg0: $BiConsumer<B, $IntConsumer> & { readonly "accept": (arg0: B, arg1: $IntConsumer) => void } & { readonly [Symbol.hasInstance]?: never }
        ): $IntStream;
        mapMultiToInt(
            arg0: ((arg0: B, arg1: $IntConsumer) => void) | globalThis.NoInfer<{ readonly "accept": (arg0: B, arg1: $IntConsumer) => void } & { readonly [Symbol.hasInstance]?: never }>
        ): $IntStream;
        mapMultiToLong(
            arg0: $BiConsumer<B, $LongConsumer> & { readonly "accept": (arg0: B, arg1: $LongConsumer) => void } & { readonly [Symbol.hasInstance]?: never }
        ): $LongStream;
        mapMultiToLong(
            arg0: ((arg0: B, arg1: $LongConsumer) => void) | globalThis.NoInfer<{ readonly "accept": (arg0: B, arg1: $LongConsumer) => void } & { readonly [Symbol.hasInstance]?: never }>
        ): $LongStream;
        mapMulti<R>(
            arg0: $BiConsumer<B, $Consumer<R>> & { readonly "accept": (arg0: B, arg1: $Consumer<R>) => void } & { readonly [Symbol.hasInstance]?: never }
        ): $StreamView<R>;
        mapMulti<R>(
            arg0: ((arg0: B, arg1: $Consumer<R>) => void) | globalThis.NoInfer<{ readonly "accept": (arg0: B, arg1: $Consumer<R>) => void } & { readonly [Symbol.hasInstance]?: never }>
        ): $StreamView<R>;
        mapToDouble(
            arg0: $ToDoubleFunction<B> & { readonly "applyAsDouble": (arg0: B) => number } & { readonly [Symbol.hasInstance]?: never }
        ): $DoubleStream;
        mapToDouble(
            arg0: ((arg0: B) => number) | globalThis.NoInfer<{ readonly "applyAsDouble": (arg0: B) => number } & { readonly [Symbol.hasInstance]?: never }>
        ): $DoubleStream;
        mapToInt(
            arg0: $ToIntFunction<B> & { readonly "applyAsInt": (arg0: B) => number } & { readonly [Symbol.hasInstance]?: never }
        ): $IntStream;
        mapToInt(
            arg0: ((arg0: B) => number) | globalThis.NoInfer<{ readonly "applyAsInt": (arg0: B) => number } & { readonly [Symbol.hasInstance]?: never }>
        ): $IntStream;
        mapToLong(
            arg0: $ToLongFunction<B> & { readonly "applyAsLong": (arg0: B) => number } & { readonly [Symbol.hasInstance]?: never }
        ): $LongStream;
        mapToLong(
            arg0: ((arg0: B) => number) | globalThis.NoInfer<{ readonly "applyAsLong": (arg0: B) => number } & { readonly [Symbol.hasInstance]?: never }>
        ): $LongStream;
        map<R>(
            arg0: $Function<B, R> & { readonly "apply": (arg0: B) => R } & { readonly [Symbol.hasInstance]?: never }
        ): $StreamView<R>;
        map<R>(
            arg0: ((arg0: B) => R) | globalThis.NoInfer<{ readonly "apply": (arg0: B) => R } & { readonly [Symbol.hasInstance]?: never }>
        ): $StreamView<R>;
        max(
            arg0: $Comparator<B> & { readonly "compare": (arg0: B, arg1: B) => number } & { readonly [Symbol.hasInstance]?: never }
        ): $OptionalView<B>;
        max(
            arg0: ((arg0: B, arg1: B) => number) | globalThis.NoInfer<{ readonly "compare": (arg0: B, arg1: B) => number } & { readonly [Symbol.hasInstance]?: never }>
        ): $OptionalView<B>;
        min(
            arg0: $Comparator<B> & { readonly "compare": (arg0: B, arg1: B) => number } & { readonly [Symbol.hasInstance]?: never }
        ): $OptionalView<B>;
        min(
            arg0: ((arg0: B, arg1: B) => number) | globalThis.NoInfer<{ readonly "compare": (arg0: B, arg1: B) => number } & { readonly [Symbol.hasInstance]?: never }>
        ): $OptionalView<B>;
        noneMatch(
            arg0: $Predicate<B> & { readonly "test": (arg0: B) => boolean } & { readonly [Symbol.hasInstance]?: never }
        ): boolean;
        noneMatch(
            arg0: ((arg0: B) => boolean) | globalThis.NoInfer<{ readonly "test": (arg0: B) => boolean } & { readonly [Symbol.hasInstance]?: never }>
        ): boolean;
        peek(
            arg0: $Consumer<B> & { readonly "accept": (arg0: B) => void } & { readonly [Symbol.hasInstance]?: never }
        ): $StreamView<B>;
        peek(
            arg0: ((arg0: B) => void) | globalThis.NoInfer<{ readonly "accept": (arg0: B) => void } & { readonly [Symbol.hasInstance]?: never }>
        ): $StreamView<B>;
        reduce(
            arg0: B,
            arg1: $BinaryOperator<B> & { readonly "apply": (arg0: B, arg1: B) => B } & { readonly [Symbol.hasInstance]?: never }
        ): B;
        reduce(
            arg0: B,
            arg1: ((arg0: B, arg1: B) => B) | globalThis.NoInfer<{ readonly "apply": (arg0: B, arg1: B) => B } & { readonly [Symbol.hasInstance]?: never }>
        ): B;
        reduce<U>(
            arg0: U,
            arg1: $BiFunction<U, B, U> & { readonly "apply": (arg0: U, arg1: B) => U } & { readonly [Symbol.hasInstance]?: never },
            arg2: $BinaryOperator_<U, U, U, U>
        ): U;
        reduce<U>(
            arg0: U,
            arg1: ((arg0: U, arg1: B) => U) | globalThis.NoInfer<{ readonly "apply": (arg0: U, arg1: B) => U } & { readonly [Symbol.hasInstance]?: never }>,
            arg2: $BinaryOperator_<U, U, U, U>
        ): U;
        reduce(
            arg0: $BinaryOperator<B> & { readonly "apply": (arg0: B, arg1: B) => B } & { readonly [Symbol.hasInstance]?: never }
        ): $OptionalView<B>;
        reduce(
            arg0: ((arg0: B, arg1: B) => B) | globalThis.NoInfer<{ readonly "apply": (arg0: B, arg1: B) => B } & { readonly [Symbol.hasInstance]?: never }>
        ): $OptionalView<B>;
        skip(arg0: number): $StreamView<B>;
        sorted(): $StreamView<B>;
        sorted(
            arg0: $Comparator<B> & { readonly "compare": (arg0: B, arg1: B) => number } & { readonly [Symbol.hasInstance]?: never }
        ): $StreamView<B>;
        sorted(
            arg0: ((arg0: B, arg1: B) => number) | globalThis.NoInfer<{ readonly "compare": (arg0: B, arg1: B) => number } & { readonly [Symbol.hasInstance]?: never }>
        ): $StreamView<B>;
        takeWhile(
            arg0: $Predicate<B> & { readonly "test": (arg0: B) => boolean } & { readonly [Symbol.hasInstance]?: never }
        ): $StreamView<B>;
        takeWhile(
            arg0: ((arg0: B) => boolean) | globalThis.NoInfer<{ readonly "test": (arg0: B) => boolean } & { readonly [Symbol.hasInstance]?: never }>
        ): $StreamView<B>;
        toList(): $ListView<B>;
        onClose(arg0: $Runnable_): $StreamView<B>;
        parallel(): $StreamView<B>;
        sequential(): $StreamView<B>;
        unordered(): $StreamView<B>;
    }
}
