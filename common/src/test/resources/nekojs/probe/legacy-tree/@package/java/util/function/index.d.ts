import { $Comparator } from "java:java/util";

declare module "java:java/util/function" {
    export interface $Function<T, R> {
        andThen<V>(arg0: $Function<any, V>): $Function<T, V>;
        apply(arg0: T): R;
        compose<V>(arg0: $Function<any, T>): $Function<V, R>;
        identity<T>(): $Function<T, T>;
    }

    export interface $Consumer<T> {
        accept(arg0: T): void;
        andThen(arg0: $Consumer<any>): $Consumer<T>;
    }

    export interface $IntFunction<R> {
        apply(arg0: number): R;
    }

    export interface $Supplier<T> {
        get(): T;
    }

    export interface $BiConsumer<T, U> {
        accept(arg0: T, arg1: U): void;
        andThen(arg0: $BiConsumer<any, any>): $BiConsumer<T, U>;
    }

    export interface $Predicate<T> {
        and(arg0: $Predicate<any>): $Predicate<T>;
        isEqual<T>(arg0: object): $Predicate<T>;
        negate(): $Predicate<T>;
        not<T>(arg0: $Predicate<any>): $Predicate<T>;
        or(arg0: $Predicate<any>): $Predicate<T>;
        test(arg0: T): boolean;
    }

    export interface $BiFunction<T, U, R> {
        andThen<V>(arg0: $Function<any, V>): $BiFunction<T, U, V>;
        apply(arg0: T, arg1: U): R;
    }

    export interface $BinaryOperator<T> extends $BiFunction {
        maxBy<T>(arg0: $Comparator<any>): $BinaryOperator<T>;
        minBy<T>(arg0: $Comparator<any>): $BinaryOperator<T>;
    }

    export interface $UnaryOperator<T> extends $Function {
        identity<T>(): $UnaryOperator<T>;
    }

    export interface $ToIntFunction<T> {
        applyAsInt(arg0: T): number;
    }

    export interface $ToLongFunction<T> {
        applyAsLong(arg0: T): number;
    }

    export interface $ToDoubleFunction<T> {
        applyAsDouble(arg0: T): number;
    }

    export interface $IntUnaryOperator {
        andThen(arg0: $IntUnaryOperator): $IntUnaryOperator;
        applyAsInt(arg0: number): number;
        compose(arg0: $IntUnaryOperator): $IntUnaryOperator;
        identity(): $IntUnaryOperator;
    }

    export interface $ObjIntConsumer<T> {
        accept(arg0: T, arg1: number): void;
    }

    export interface $IntPredicate {
        and(arg0: $IntPredicate): $IntPredicate;
        negate(): $IntPredicate;
        or(arg0: $IntPredicate): $IntPredicate;
        test(arg0: number): boolean;
    }

    export interface $IntConsumer {
        accept(arg0: number): void;
        andThen(arg0: $IntConsumer): $IntConsumer;
    }

    export interface $IntBinaryOperator {
        applyAsInt(arg0: number, arg1: number): number;
    }

    export interface $IntSupplier {
        getAsInt(): number;
    }

    export interface $IntToLongFunction {
        applyAsLong(arg0: number): number;
    }

    export interface $IntToDoubleFunction {
        applyAsDouble(arg0: number): number;
    }

    export interface $LongUnaryOperator {
        andThen(arg0: $LongUnaryOperator): $LongUnaryOperator;
        applyAsLong(arg0: number): number;
        compose(arg0: $LongUnaryOperator): $LongUnaryOperator;
        identity(): $LongUnaryOperator;
    }

    export interface $ObjLongConsumer<T> {
        accept(arg0: T, arg1: number): void;
    }

    export interface $LongPredicate {
        and(arg0: $LongPredicate): $LongPredicate;
        negate(): $LongPredicate;
        or(arg0: $LongPredicate): $LongPredicate;
        test(arg0: number): boolean;
    }

    export interface $LongFunction<R> {
        apply(arg0: number): R;
    }

    export interface $LongConsumer {
        accept(arg0: number): void;
        andThen(arg0: $LongConsumer): $LongConsumer;
    }

    export interface $LongBinaryOperator {
        applyAsLong(arg0: number, arg1: number): number;
    }

    export interface $LongSupplier {
        getAsLong(): number;
    }

    export interface $LongToIntFunction {
        applyAsInt(arg0: number): number;
    }

    export interface $LongToDoubleFunction {
        applyAsDouble(arg0: number): number;
    }

    export interface $DoubleUnaryOperator {
        andThen(arg0: $DoubleUnaryOperator): $DoubleUnaryOperator;
        applyAsDouble(arg0: number): number;
        compose(arg0: $DoubleUnaryOperator): $DoubleUnaryOperator;
        identity(): $DoubleUnaryOperator;
    }

    export interface $ObjDoubleConsumer<T> {
        accept(arg0: T, arg1: number): void;
    }

    export interface $DoublePredicate {
        and(arg0: $DoublePredicate): $DoublePredicate;
        negate(): $DoublePredicate;
        or(arg0: $DoublePredicate): $DoublePredicate;
        test(arg0: number): boolean;
    }

    export interface $DoubleFunction<R> {
        apply(arg0: number): R;
    }

    export interface $DoubleConsumer {
        accept(arg0: number): void;
        andThen(arg0: $DoubleConsumer): $DoubleConsumer;
    }

    export interface $DoubleBinaryOperator {
        applyAsDouble(arg0: number, arg1: number): number;
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

    export type $Function_<T, R> = (T) => R;
    export type $Consumer_<T> = (T) => void;
    export type $Supplier_<T> = () => T;
    export type $BiConsumer_<T, U> = (T, U) => void;
    export type $Predicate_<T> = (T) => boolean;
    export type $BiFunction_<T, U, R> = (T, U) => any;
    export type $BinaryOperator_<T> = (T, T) => T;
    export type $UnaryOperator_<T> = (T) => {1};
}
