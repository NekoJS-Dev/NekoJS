import { $Comparator } from "java:java/util";

declare module "java:java/util/function" {
    export interface $BiConsumer<T, U> {
        accept(arg0: T, arg1: U): void;
        andThen(arg0: $BiConsumer_<T, U>): $BiConsumer<T, U>;
    }

    export interface $BiFunction<T, U, R> {
        andThen<V>(arg0: $Function_<R, V>): $BiFunction<T, U, V>;
        apply(arg0: T, arg1: U): R;
    }

    export interface $BinaryOperator<T> extends $BiFunction<T, T, T> {
        maxBy<T>(arg0: $Comparator<T>): $BinaryOperator<T>;
        minBy<T>(arg0: $Comparator<T>): $BinaryOperator<T>;
    }

    export interface $Consumer<T> {
        accept(arg0: T): void;
        andThen(arg0: $Consumer_<T>): $Consumer<T>;
    }

    export interface $DoubleBinaryOperator {
        applyAsDouble(arg0: number, arg1: number): number;
    }

    export interface $DoubleConsumer {
        accept(arg0: number): void;
        andThen(arg0: $DoubleConsumer_): $DoubleConsumer;
    }

    export interface $DoubleFunction<R> {
        apply(arg0: number): R;
    }

    export interface $DoublePredicate {
        and(arg0: $DoublePredicate_): $DoublePredicate;
        negate(): $DoublePredicate;
        or(arg0: $DoublePredicate_): $DoublePredicate;
        test(arg0: number): boolean;
    }

    export interface $DoubleSupplier {
        getAsDouble(): number;
    }

    export interface $DoubleToIntFunction {
        applyAsInt(arg0: number): number;
    }

    export interface $DoubleToLongFunction {
        applyAsLong(arg0: number): number;
    }

    export interface $DoubleUnaryOperator {
        andThen(arg0: $DoubleUnaryOperator_): $DoubleUnaryOperator;
        applyAsDouble(arg0: number): number;
        compose(arg0: $DoubleUnaryOperator_): $DoubleUnaryOperator;
        identity(): $DoubleUnaryOperator;
    }

    export interface $Function<T, R> {
        andThen<V>(arg0: $Function_<R, V>): $Function<T, V>;
        apply(arg0: T): R;
        compose<V>(arg0: $Function_<V, T>): $Function<V, R>;
        identity<T>(): $Function<T, T>;
    }

    export interface $IntBinaryOperator {
        applyAsInt(arg0: number, arg1: number): number;
    }

    export interface $IntConsumer {
        accept(arg0: number): void;
        andThen(arg0: $IntConsumer_): $IntConsumer;
    }

    export interface $IntFunction<R> {
        apply(arg0: number): R;
    }

    export interface $IntPredicate {
        and(arg0: $IntPredicate_): $IntPredicate;
        negate(): $IntPredicate;
        or(arg0: $IntPredicate_): $IntPredicate;
        test(arg0: number): boolean;
    }

    export interface $IntSupplier {
        getAsInt(): number;
    }

    export interface $IntToDoubleFunction {
        applyAsDouble(arg0: number): number;
    }

    export interface $IntToLongFunction {
        applyAsLong(arg0: number): number;
    }

    export interface $IntUnaryOperator {
        andThen(arg0: $IntUnaryOperator_): $IntUnaryOperator;
        applyAsInt(arg0: number): number;
        compose(arg0: $IntUnaryOperator_): $IntUnaryOperator;
        identity(): $IntUnaryOperator;
    }

    export interface $LongBinaryOperator {
        applyAsLong(arg0: number, arg1: number): number;
    }

    export interface $LongConsumer {
        accept(arg0: number): void;
        andThen(arg0: $LongConsumer_): $LongConsumer;
    }

    export interface $LongFunction<R> {
        apply(arg0: number): R;
    }

    export interface $LongPredicate {
        and(arg0: $LongPredicate_): $LongPredicate;
        negate(): $LongPredicate;
        or(arg0: $LongPredicate_): $LongPredicate;
        test(arg0: number): boolean;
    }

    export interface $LongSupplier {
        getAsLong(): number;
    }

    export interface $LongToDoubleFunction {
        applyAsDouble(arg0: number): number;
    }

    export interface $LongToIntFunction {
        applyAsInt(arg0: number): number;
    }

    export interface $LongUnaryOperator {
        andThen(arg0: $LongUnaryOperator_): $LongUnaryOperator;
        applyAsLong(arg0: number): number;
        compose(arg0: $LongUnaryOperator_): $LongUnaryOperator;
        identity(): $LongUnaryOperator;
    }

    export interface $ObjDoubleConsumer<T> {
        accept(arg0: T, arg1: number): void;
    }

    export interface $ObjIntConsumer<T> {
        accept(arg0: T, arg1: number): void;
    }

    export interface $ObjLongConsumer<T> {
        accept(arg0: T, arg1: number): void;
    }

    export interface $Predicate<T> {
        and(arg0: $Predicate_<T>): $Predicate<T>;
        isEqual<T>(arg0: object): $Predicate<T>;
        negate(): $Predicate<T>;
        not<T>(arg0: $Predicate_<T>): $Predicate<T>;
        or(arg0: $Predicate_<T>): $Predicate<T>;
        test(arg0: T): boolean;
    }

    export interface $Supplier<T> {
        get(): T;
    }

    export interface $ToDoubleFunction<T> {
        applyAsDouble(arg0: T): number;
    }

    export interface $ToIntFunction<T> {
        applyAsInt(arg0: T): number;
    }

    export interface $ToLongFunction<T> {
        applyAsLong(arg0: T): number;
    }

    export interface $UnaryOperator<T> extends $Function<T, T> {
        identity<T>(): $UnaryOperator<T>;
    }

    export type $BiConsumer_<T, U> = (arg0: T, arg1: U) => void;
    export type $BiFunction_<T, U, R> = (arg0: T, arg1: U) => R;
    export type $BinaryOperator_<T> = (arg0: T, arg1: U) => R;
    export type $Consumer_<T> = (arg0: T) => void;
    export type $DoubleBinaryOperator_ = (arg0: number, arg1: number) => number;
    export type $DoubleConsumer_ = (arg0: number) => void;
    export type $DoubleFunction_<R> = (arg0: number) => R;
    export type $DoublePredicate_ = (arg0: number) => boolean;
    export type $DoubleSupplier_ = () => number;
    export type $DoubleToIntFunction_ = (arg0: number) => number;
    export type $DoubleToLongFunction_ = (arg0: number) => number;
    export type $DoubleUnaryOperator_ = (arg0: number) => number;
    export type $Function_<T, R> = (arg0: T) => R;
    export type $IntBinaryOperator_ = (arg0: number, arg1: number) => number;
    export type $IntConsumer_ = (arg0: number) => void;
    export type $IntFunction_<R> = (arg0: number) => R;
    export type $IntPredicate_ = (arg0: number) => boolean;
    export type $IntSupplier_ = () => number;
    export type $IntToDoubleFunction_ = (arg0: number) => number;
    export type $IntToLongFunction_ = (arg0: number) => number;
    export type $IntUnaryOperator_ = (arg0: number) => number;
    export type $LongBinaryOperator_ = (arg0: number, arg1: number) => number;
    export type $LongConsumer_ = (arg0: number) => void;
    export type $LongFunction_<R> = (arg0: number) => R;
    export type $LongPredicate_ = (arg0: number) => boolean;
    export type $LongSupplier_ = () => number;
    export type $LongToDoubleFunction_ = (arg0: number) => number;
    export type $LongToIntFunction_ = (arg0: number) => number;
    export type $LongUnaryOperator_ = (arg0: number) => number;
    export type $ObjDoubleConsumer_<T> = (arg0: T, arg1: number) => void;
    export type $ObjIntConsumer_<T> = (arg0: T, arg1: number) => void;
    export type $ObjLongConsumer_<T> = (arg0: T, arg1: number) => void;
    export type $Predicate_<T> = (arg0: T) => boolean;
    export type $Supplier_<T> = () => T;
    export type $ToDoubleFunction_<T> = (arg0: T) => number;
    export type $ToIntFunction_<T> = (arg0: T) => number;
    export type $ToLongFunction_<T> = (arg0: T) => number;
    export type $UnaryOperator_<T> = (arg0: T) => R;
}
