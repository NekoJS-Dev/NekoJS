import { $AutoCloseable, $AutoCloseable_, $Enum, $Runnable, $Runnable_, $Thread, $Throwable } from "java:java/lang";
import { $Collection, $List } from "java:java/util";

declare module "java:java/util/concurrent" {
    export class $TimeUnit {
        static DAYS: $TimeUnit;
        static HOURS: $TimeUnit;
        static MICROSECONDS: $TimeUnit;
        static MILLISECONDS: $TimeUnit;
        static MINUTES: $TimeUnit;
        static NANOSECONDS: $TimeUnit;
        static SECONDS: $TimeUnit;
        name(): string;
        ordinal(): number;
        toString(): string;
        static values(): $TimeUnit[];
        static valueOf(name: string): $TimeUnit;
    }
    export interface $TimeUnit extends $Enum<$TimeUnit> {}

    export interface $ExecutorService extends $Executor, $AutoCloseable {
        awaitTermination(arg0: number, arg1: $TimeUnit_): boolean;
        close(): void;
        invokeAll<T>(arg0: $Callable_<T, T>[], arg1: number, arg2: $TimeUnit_): $List<$Future<T>>;
        invokeAll<T>(arg0: $Callable_<T, T>[]): $List<$Future<T>>;
        invokeAny<T>(arg0: $Callable_<T, T>[], arg1: number, arg2: $TimeUnit_): T;
        invokeAny<T>(arg0: $Callable_<T, T>[]): T;
        isShutdown(): boolean;
        isTerminated(): boolean;
        shutdownNow(): $List<$Runnable>;
        shutdown(): void;
        submit<T>(arg0: $Runnable_, arg1: T): $Future<T>;
        submit(arg0: $Runnable_): $Future<any>;
        submit<T>(arg0: $Callable_<T, T>): $Future<T>;
    }

    export interface $Future<V> {
        cancel(arg0: boolean): boolean;
        exceptionNow(): $Throwable;
        get(arg0: number, arg1: $TimeUnit_): V;
        get(): V;
        isCancelled(): boolean;
        isDone(): boolean;
        resultNow(): V;
        state(): $Future$State;
    }

    export interface $ThreadFactory {
        newThread(arg0: $Runnable_): $Thread;
    }

    export interface $Callable<V> {
        call(): V;
    }

    export interface $Executor {
        execute(arg0: $Runnable_): void;
    }

    export class $Future$State {
        static CANCELLED: $Future$State;
        static FAILED: $Future$State;
        static RUNNING: $Future$State;
        static SUCCESS: $Future$State;
        name(): string;
        ordinal(): number;
        toString(): string;
        static values(): $Future$State[];
        static valueOf(name: string): $Future$State;
    }
    export interface $Future$State extends $Enum<$Future$State> {}

    export type $TimeUnit_ = $TimeUnit | "DAYS" | "HOURS" | "MICROSECONDS" | "MILLISECONDS" | "MINUTES" | "NANOSECONDS" | "SECONDS";
    export type $ThreadFactory_<CallbackArg0 = $Runnable, CallbackResult = $Thread> = ((arg0: CallbackArg0) => CallbackResult) | ($ThreadFactory & { readonly [Symbol.hasInstance]?: never });
    export type $Callable_<Host0 = any, CallbackResult = Host0> = (() => CallbackResult) | ($Callable<Host0> & { readonly [Symbol.hasInstance]?: never });
    export type $Executor_<CallbackArg0 = $Runnable> = ((arg0: CallbackArg0) => void) | ($Executor & { readonly [Symbol.hasInstance]?: never });
    export type $Future$State_ = $Future$State | "CANCELLED" | "FAILED" | "RUNNING" | "SUCCESS";
}
