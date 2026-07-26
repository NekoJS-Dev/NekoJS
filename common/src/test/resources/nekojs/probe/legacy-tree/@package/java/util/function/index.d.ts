






































        accept(arg0: T): void;
        accept(arg0: T, arg1: U): void;
        accept(arg0: T, arg1: number): void;
        accept(arg0: T, arg1: number): void;
        accept(arg0: T, arg1: number): void;
        accept(arg0: number): void;
        accept(arg0: number): void;
        accept(arg0: number): void;
        and(arg0: $DoublePredicate): $DoublePredicate;
        and(arg0: $IntPredicate): $IntPredicate;
        and(arg0: $LongPredicate): $LongPredicate;
        and(arg0: $Predicate<any>): $Predicate<T>;
        andThen(arg0: $BiConsumer<any, any>): $BiConsumer<T, U>;
        andThen(arg0: $Consumer<any>): $Consumer<T>;
        andThen(arg0: $DoubleConsumer): $DoubleConsumer;
        andThen(arg0: $DoubleUnaryOperator): $DoubleUnaryOperator;
        andThen(arg0: $IntConsumer): $IntConsumer;
        andThen(arg0: $IntUnaryOperator): $IntUnaryOperator;
        andThen(arg0: $LongConsumer): $LongConsumer;
        andThen(arg0: $LongUnaryOperator): $LongUnaryOperator;
        andThen<V>(arg0: $Function<any, V>): $BiFunction<T, U, V>;
        andThen<V>(arg0: $Function<any, V>): $Function<T, V>;
        apply(arg0: T): R;
        apply(arg0: T, arg1: U): R;
        apply(arg0: number): R;
        apply(arg0: number): R;
        apply(arg0: number): R;
        applyAsDouble(arg0: T): number;
        applyAsDouble(arg0: number): number;
        applyAsDouble(arg0: number): number;
        applyAsDouble(arg0: number): number;
        applyAsDouble(arg0: number, arg1: number): number;
        applyAsInt(arg0: T): number;
        applyAsInt(arg0: number): number;
        applyAsInt(arg0: number): number;
        applyAsInt(arg0: number): number;
        applyAsInt(arg0: number, arg1: number): number;
        applyAsLong(arg0: T): number;
        applyAsLong(arg0: number): number;
        applyAsLong(arg0: number): number;
        applyAsLong(arg0: number): number;
        applyAsLong(arg0: number, arg1: number): number;
        compose(arg0: $DoubleUnaryOperator): $DoubleUnaryOperator;
        compose(arg0: $IntUnaryOperator): $IntUnaryOperator;
        compose(arg0: $LongUnaryOperator): $LongUnaryOperator;
        compose<V>(arg0: $Function<any, T>): $Function<V, R>;
        get(): T;
        getAsDouble(): number;
        getAsInt(): number;
        getAsLong(): number;
        identity(): $DoubleUnaryOperator;
        identity(): $IntUnaryOperator;
        identity(): $LongUnaryOperator;
        identity<T>(): $Function<T, T>;
        identity<T>(): $UnaryOperator<T>;
        isEqual<T>(arg0: object): $Predicate<T>;
        maxBy<T>(arg0: $Comparator<any>): $BinaryOperator<T>;
        minBy<T>(arg0: $Comparator<any>): $BinaryOperator<T>;
        negate(): $DoublePredicate;
        negate(): $IntPredicate;
        negate(): $LongPredicate;
        negate(): $Predicate<T>;
        not<T>(arg0: $Predicate<any>): $Predicate<T>;
        or(arg0: $DoublePredicate): $DoublePredicate;
        or(arg0: $IntPredicate): $IntPredicate;
        or(arg0: $LongPredicate): $LongPredicate;
        or(arg0: $Predicate<any>): $Predicate<T>;
        test(arg0: T): boolean;
        test(arg0: number): boolean;
        test(arg0: number): boolean;
        test(arg0: number): boolean;
    export interface $BiConsumer<T, U> {
    export interface $BiFunction<T, U, R> {
    export interface $BinaryOperator<T> extends $BiFunction {
    export interface $Consumer<T> {
    export interface $DoubleBinaryOperator {
    export interface $DoubleConsumer {
    export interface $DoubleFunction<R> {
    export interface $DoublePredicate {
    export interface $DoubleSupplier {
    export interface $DoubleToIntFunction {
    export interface $DoubleToLongFunction {
    export interface $DoubleUnaryOperator {
    export interface $Function<T, R> {
    export interface $IntBinaryOperator {
    export interface $IntConsumer {
    export interface $IntFunction<R> {
    export interface $IntPredicate {
    export interface $IntSupplier {
    export interface $IntToDoubleFunction {
    export interface $IntToLongFunction {
    export interface $IntUnaryOperator {
    export interface $LongBinaryOperator {
    export interface $LongConsumer {
    export interface $LongFunction<R> {
    export interface $LongPredicate {
    export interface $LongSupplier {
    export interface $LongToDoubleFunction {
    export interface $LongToIntFunction {
    export interface $LongUnaryOperator {
    export interface $ObjDoubleConsumer<T> {
    export interface $ObjIntConsumer<T> {
    export interface $ObjLongConsumer<T> {
    export interface $Predicate<T> {
    export interface $Supplier<T> {
    export interface $ToDoubleFunction<T> {
    export interface $ToIntFunction<T> {
    export interface $ToLongFunction<T> {
    export interface $UnaryOperator<T> extends $Function {
    export type $BiConsumer_<T, U> = (T, U) => void;
    export type $BiFunction_<T, U, R> = (T, U) => any;
    export type $BinaryOperator_<T> = (T, T) => T;
    export type $Consumer_<T> = (T) => void;
    export type $Function_<T, R> = (T) => R;
    export type $Predicate_<T> = (T) => boolean;
    export type $Supplier_<T> = () => T;
    export type $UnaryOperator_<T> = (T) => {1};
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
    }
    }
    }
    }
declare module "java:java/util/function" {
import { $Comparator } from "java:java/util";
}