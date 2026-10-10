import { $Comparator, $Comparator_ } from "java:java/util";

declare module "java:java/util/function" {
    export interface $BiConsumer<T, U> {
        accept(arg0: T, arg1: U): void;
        andThen(arg0: $BiConsumer_<T, U, T, U>): $BiConsumer<T, U>;
    }

    export interface $BiFunction<T, U, R> {
        andThen<V>(arg0: $Function_<R, V, R, V>): $BiFunction<T, U, V>;
        apply(arg0: T, arg1: U): R;
    }

    export interface $BinaryOperator<T> extends $BiFunction<T, T, T> {
    }
    export const $BinaryOperator: {
        maxBy<T>(arg0: $Comparator_<T, T, T, number>): $BinaryOperator<T>;
        minBy<T>(arg0: $Comparator_<T, T, T, number>): $BinaryOperator<T>;
    };

    export interface $Consumer<T> {
        accept(arg0: T): void;
        andThen(arg0: $Consumer_<T, T>): $Consumer<T>;
    }

    export interface $DoubleBinaryOperator {
        applyAsDouble(arg0: number, arg1: number): number;
    }

    export interface $DoubleConsumer {
        accept(arg0: number): void;
        andThen(arg0: $DoubleConsumer_<number>): $DoubleConsumer;
    }

    export interface $DoubleFunction<R> {
        apply(arg0: number): R;
    }

    export interface $DoublePredicate {
        and(arg0: $DoublePredicate_<number, boolean>): $DoublePredicate;
        negate(): $DoublePredicate;
        or(arg0: $DoublePredicate_<number, boolean>): $DoublePredicate;
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
        andThen(arg0: $DoubleUnaryOperator_<number, number>): $DoubleUnaryOperator;
        applyAsDouble(arg0: number): number;
        compose(arg0: $DoubleUnaryOperator_<number, number>): $DoubleUnaryOperator;
    }
    export const $DoubleUnaryOperator: {
        identity(): $DoubleUnaryOperator;
    };

    export interface $Function<T, R> {
        andThen<V>(arg0: $Function_<R, V, R, V>): $Function<T, V>;
        apply(arg0: T): R;
        compose<V>(arg0: $Function_<V, T, V, T>): $Function<V, R>;
    }
    export const $Function: {
        identity<T>(): $Function<T, T>;
    };

    export interface $IntBinaryOperator {
        applyAsInt(arg0: number, arg1: number): number;
    }

    export interface $IntConsumer {
        accept(arg0: number): void;
        andThen(arg0: $IntConsumer_<number>): $IntConsumer;
    }

    export interface $IntFunction<R> {
        apply(arg0: number): R;
    }

    export interface $IntPredicate {
        and(arg0: $IntPredicate_<number, boolean>): $IntPredicate;
        negate(): $IntPredicate;
        or(arg0: $IntPredicate_<number, boolean>): $IntPredicate;
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
        andThen(arg0: $IntUnaryOperator_<number, number>): $IntUnaryOperator;
        applyAsInt(arg0: number): number;
        compose(arg0: $IntUnaryOperator_<number, number>): $IntUnaryOperator;
    }
    export const $IntUnaryOperator: {
        identity(): $IntUnaryOperator;
    };

    export interface $LongBinaryOperator {
        applyAsLong(arg0: number, arg1: number): number;
    }

    export interface $LongConsumer {
        accept(arg0: number): void;
        andThen(arg0: $LongConsumer_<number>): $LongConsumer;
    }

    export interface $LongFunction<R> {
        apply(arg0: number): R;
    }

    export interface $LongPredicate {
        and(arg0: $LongPredicate_<number, boolean>): $LongPredicate;
        negate(): $LongPredicate;
        or(arg0: $LongPredicate_<number, boolean>): $LongPredicate;
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
        andThen(arg0: $LongUnaryOperator_<number, number>): $LongUnaryOperator;
        applyAsLong(arg0: number): number;
        compose(arg0: $LongUnaryOperator_<number, number>): $LongUnaryOperator;
    }
    export const $LongUnaryOperator: {
        identity(): $LongUnaryOperator;
    };

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
        and(arg0: $Predicate_<T, T, boolean>): $Predicate<T>;
        negate(): $Predicate<T>;
        or(arg0: $Predicate_<T, T, boolean>): $Predicate<T>;
        test(arg0: T): boolean;
    }
    export const $Predicate: {
        isEqual<T>(arg0: object): $Predicate<T>;
        not<T>(arg0: $Predicate_<T, T, boolean>): $Predicate<T>;
    };

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
    }
    export const $UnaryOperator: {
        identity<T>(): $UnaryOperator<T>;
    };

    export type $BiConsumer_<Host0 = any, Host1 = any, CallbackArg0 = Host0, CallbackArg1 = Host1> = ((arg0: CallbackArg0, arg1: CallbackArg1) => void) | ($BiConsumer<Host0, Host1> & { readonly [Symbol.hasInstance]?: never });
    export type $BiFunction_<Host0 = any, Host1 = any, Host2 = any, CallbackArg0 = Host0, CallbackArg1 = Host1, CallbackResult = Host2> = ((arg0: CallbackArg0, arg1: CallbackArg1) => CallbackResult) | ($BiFunction<Host0, Host1, Host2> & { readonly [Symbol.hasInstance]?: never });
    export type $BinaryOperator_<Host0 = any, CallbackArg0 = Host0, CallbackArg1 = Host0, CallbackResult = Host0> = ((arg0: CallbackArg0, arg1: CallbackArg1) => CallbackResult) | ($BinaryOperator<Host0> & { readonly [Symbol.hasInstance]?: never });
    export type $Consumer_<Host0 = any, CallbackArg0 = Host0> = ((arg0: CallbackArg0) => void) | ($Consumer<Host0> & { readonly [Symbol.hasInstance]?: never });
    export type $DoubleBinaryOperator_<CallbackArg0 = number, CallbackArg1 = number, CallbackResult = number> = ((arg0: CallbackArg0, arg1: CallbackArg1) => CallbackResult) | ($DoubleBinaryOperator & { readonly [Symbol.hasInstance]?: never });
    export type $DoubleConsumer_<CallbackArg0 = number> = ((arg0: CallbackArg0) => void) | ($DoubleConsumer & { readonly [Symbol.hasInstance]?: never });
    export type $DoubleFunction_<Host0 = any, CallbackArg0 = number, CallbackResult = Host0> = ((arg0: CallbackArg0) => CallbackResult) | ($DoubleFunction<Host0> & { readonly [Symbol.hasInstance]?: never });
    export type $DoublePredicate_<CallbackArg0 = number, CallbackResult = boolean> = ((arg0: CallbackArg0) => CallbackResult) | ($DoublePredicate & { readonly [Symbol.hasInstance]?: never });
    export type $DoubleSupplier_<CallbackResult = number> = (() => CallbackResult) | ($DoubleSupplier & { readonly [Symbol.hasInstance]?: never });
    export type $DoubleToIntFunction_<CallbackArg0 = number, CallbackResult = number> = ((arg0: CallbackArg0) => CallbackResult) | ($DoubleToIntFunction & { readonly [Symbol.hasInstance]?: never });
    export type $DoubleToLongFunction_<CallbackArg0 = number, CallbackResult = number> = ((arg0: CallbackArg0) => CallbackResult) | ($DoubleToLongFunction & { readonly [Symbol.hasInstance]?: never });
    export type $DoubleUnaryOperator_<CallbackArg0 = number, CallbackResult = number> = ((arg0: CallbackArg0) => CallbackResult) | ($DoubleUnaryOperator & { readonly [Symbol.hasInstance]?: never });
    export type $Function_<Host0 = any, Host1 = any, CallbackArg0 = Host0, CallbackResult = Host1> = ((arg0: CallbackArg0) => CallbackResult) | ($Function<Host0, Host1> & { readonly [Symbol.hasInstance]?: never });
    export type $IntBinaryOperator_<CallbackArg0 = number, CallbackArg1 = number, CallbackResult = number> = ((arg0: CallbackArg0, arg1: CallbackArg1) => CallbackResult) | ($IntBinaryOperator & { readonly [Symbol.hasInstance]?: never });
    export type $IntConsumer_<CallbackArg0 = number> = ((arg0: CallbackArg0) => void) | ($IntConsumer & { readonly [Symbol.hasInstance]?: never });
    export type $IntFunction_<Host0 = any, CallbackArg0 = number, CallbackResult = Host0> = ((arg0: CallbackArg0) => CallbackResult) | ($IntFunction<Host0> & { readonly [Symbol.hasInstance]?: never });
    export type $IntPredicate_<CallbackArg0 = number, CallbackResult = boolean> = ((arg0: CallbackArg0) => CallbackResult) | ($IntPredicate & { readonly [Symbol.hasInstance]?: never });
    export type $IntSupplier_<CallbackResult = number> = (() => CallbackResult) | ($IntSupplier & { readonly [Symbol.hasInstance]?: never });
    export type $IntToDoubleFunction_<CallbackArg0 = number, CallbackResult = number> = ((arg0: CallbackArg0) => CallbackResult) | ($IntToDoubleFunction & { readonly [Symbol.hasInstance]?: never });
    export type $IntToLongFunction_<CallbackArg0 = number, CallbackResult = number> = ((arg0: CallbackArg0) => CallbackResult) | ($IntToLongFunction & { readonly [Symbol.hasInstance]?: never });
    export type $IntUnaryOperator_<CallbackArg0 = number, CallbackResult = number> = ((arg0: CallbackArg0) => CallbackResult) | ($IntUnaryOperator & { readonly [Symbol.hasInstance]?: never });
    export type $LongBinaryOperator_<CallbackArg0 = number, CallbackArg1 = number, CallbackResult = number> = ((arg0: CallbackArg0, arg1: CallbackArg1) => CallbackResult) | ($LongBinaryOperator & { readonly [Symbol.hasInstance]?: never });
    export type $LongConsumer_<CallbackArg0 = number> = ((arg0: CallbackArg0) => void) | ($LongConsumer & { readonly [Symbol.hasInstance]?: never });
    export type $LongFunction_<Host0 = any, CallbackArg0 = number, CallbackResult = Host0> = ((arg0: CallbackArg0) => CallbackResult) | ($LongFunction<Host0> & { readonly [Symbol.hasInstance]?: never });
    export type $LongPredicate_<CallbackArg0 = number, CallbackResult = boolean> = ((arg0: CallbackArg0) => CallbackResult) | ($LongPredicate & { readonly [Symbol.hasInstance]?: never });
    export type $LongSupplier_<CallbackResult = number> = (() => CallbackResult) | ($LongSupplier & { readonly [Symbol.hasInstance]?: never });
    export type $LongToDoubleFunction_<CallbackArg0 = number, CallbackResult = number> = ((arg0: CallbackArg0) => CallbackResult) | ($LongToDoubleFunction & { readonly [Symbol.hasInstance]?: never });
    export type $LongToIntFunction_<CallbackArg0 = number, CallbackResult = number> = ((arg0: CallbackArg0) => CallbackResult) | ($LongToIntFunction & { readonly [Symbol.hasInstance]?: never });
    export type $LongUnaryOperator_<CallbackArg0 = number, CallbackResult = number> = ((arg0: CallbackArg0) => CallbackResult) | ($LongUnaryOperator & { readonly [Symbol.hasInstance]?: never });
    export type $ObjDoubleConsumer_<Host0 = any, CallbackArg0 = Host0, CallbackArg1 = number> = ((arg0: CallbackArg0, arg1: CallbackArg1) => void) | ($ObjDoubleConsumer<Host0> & { readonly [Symbol.hasInstance]?: never });
    export type $ObjIntConsumer_<Host0 = any, CallbackArg0 = Host0, CallbackArg1 = number> = ((arg0: CallbackArg0, arg1: CallbackArg1) => void) | ($ObjIntConsumer<Host0> & { readonly [Symbol.hasInstance]?: never });
    export type $ObjLongConsumer_<Host0 = any, CallbackArg0 = Host0, CallbackArg1 = number> = ((arg0: CallbackArg0, arg1: CallbackArg1) => void) | ($ObjLongConsumer<Host0> & { readonly [Symbol.hasInstance]?: never });
    export type $Predicate_<Host0 = any, CallbackArg0 = Host0, CallbackResult = boolean> = ((arg0: CallbackArg0) => CallbackResult) | ($Predicate<Host0> & { readonly [Symbol.hasInstance]?: never });
    export type $Supplier_<Host0 = any, CallbackResult = Host0> = (() => CallbackResult) | ($Supplier<Host0> & { readonly [Symbol.hasInstance]?: never });
    export type $ToDoubleFunction_<Host0 = any, CallbackArg0 = Host0, CallbackResult = number> = ((arg0: CallbackArg0) => CallbackResult) | ($ToDoubleFunction<Host0> & { readonly [Symbol.hasInstance]?: never });
    export type $ToIntFunction_<Host0 = any, CallbackArg0 = Host0, CallbackResult = number> = ((arg0: CallbackArg0) => CallbackResult) | ($ToIntFunction<Host0> & { readonly [Symbol.hasInstance]?: never });
    export type $ToLongFunction_<Host0 = any, CallbackArg0 = Host0, CallbackResult = number> = ((arg0: CallbackArg0) => CallbackResult) | ($ToLongFunction<Host0> & { readonly [Symbol.hasInstance]?: never });
    export type $UnaryOperator_<Host0 = any, CallbackArg0 = Host0, CallbackResult = Host0> = ((arg0: CallbackArg0) => CallbackResult) | ($UnaryOperator<Host0> & { readonly [Symbol.hasInstance]?: never });
}
