import { $Closeable, $Closeable_ } from "java:java/io";
import { $AutoCloseable, $AutoCloseable_, $Throwable } from "java:java/lang";
import { $Arena, $MemorySegment } from "java:java/lang/foreign";
import { $ByteBuffer, $MappedByteBuffer } from "java:java/nio";
import { $AbstractInterruptibleChannel } from "java:java/nio/channels/spi";
import { $OpenOption, $Path } from "java:java/nio/file";
import { $FileAttribute } from "java:java/nio/file/attribute";
import { $Set, $Set_ } from "java:java/util";
import { $ExecutorService, $Future } from "java:java/util/concurrent";

export * as spi from "java:java/nio/channels/spi";

declare module "java:java/nio/channels" {
    export class $AsynchronousFileChannel implements $AsynchronousChannel {
        get open(): boolean;
        isOpen(): boolean;
        static open(arg0: $Path, arg1?: $OpenOption[]): $AsynchronousFileChannel;
        static open(
            arg0: $Path,
            arg1: $Set<$OpenOption>,
            arg2: $ExecutorService,
            arg3?: $FileAttribute<any>[]
        ): $AsynchronousFileChannel;
        close(): void;
        force(arg0: boolean): void;
        lock<A>(arg0: A, arg1: $CompletionHandler<$FileLock, any>): void;
        lock<A>(arg0: number, arg1: number, arg2: boolean, arg3: A, arg4: $CompletionHandler<$FileLock, any>): void;
        lock(arg0: number, arg1: number, arg2: boolean): $Future<$FileLock>;
        lock(): $Future<$FileLock>;
        read<A>(arg0: $ByteBuffer, arg1: number, arg2: A, arg3: $CompletionHandler<number, any>): void;
        read(arg0: $ByteBuffer, arg1: number): $Future<number>;
        size(): number;
        truncate(arg0: number): $AsynchronousFileChannel;
        tryLock(arg0: number, arg1: number, arg2: boolean): $FileLock;
        tryLock(): $FileLock;
        write<A>(arg0: $ByteBuffer, arg1: number, arg2: A, arg3: $CompletionHandler<number, any>): void;
        write(arg0: $ByteBuffer, arg1: number): $Future<number>;
    }

    export class $FileChannel extends $AbstractInterruptibleChannel implements $SeekableByteChannel, $GatheringByteChannel, $ScatteringByteChannel {
        get open(): boolean;
        isOpen(): boolean;
        static open(arg0: $Path, arg1?: $OpenOption[]): $FileChannel;
        static open(arg0: $Path, arg1: $Set<$OpenOption>, arg2?: $FileAttribute<any>[]): $FileChannel;
        close(): void;
        force(arg0: boolean): void;
        lock(arg0: number, arg1: number, arg2: boolean): $FileLock;
        lock(): $FileLock;
        map(arg0: $FileChannel$MapMode, arg1: number, arg2: number, arg3: $Arena): $MemorySegment;
        map(arg0: $FileChannel$MapMode, arg1: number, arg2: number): $MappedByteBuffer;
        position(arg0: number): $FileChannel;
        position(): number;
        read(arg0: $ByteBuffer[], arg1: number, arg2: number): number;
        read(arg0: $ByteBuffer[]): number;
        read(arg0: $ByteBuffer, arg1: number): number;
        read(arg0: $ByteBuffer): number;
        size(): number;
        transferFrom(arg0: $ReadableByteChannel, arg1: number, arg2: number): number;
        transferTo(arg0: number, arg1: number, arg2: $WritableByteChannel): number;
        truncate(arg0: number): $FileChannel;
        tryLock(arg0: number, arg1: number, arg2: boolean): $FileLock;
        tryLock(): $FileLock;
        write(arg0: $ByteBuffer[], arg1: number, arg2: number): number;
        write(arg0: $ByteBuffer[]): number;
        write(arg0: $ByteBuffer, arg1: number): number;
        write(arg0: $ByteBuffer): number;
    }

    export interface $SeekableByteChannel extends $ByteChannel {
        position(arg0: number): $SeekableByteChannel;
        position(): number;
        read(arg0: $ByteBuffer): number;
        size(): number;
        truncate(arg0: number): $SeekableByteChannel;
        write(arg0: $ByteBuffer): number;
    }

    export interface $AsynchronousChannel extends $Channel {
        close(): void;
    }

    export interface $CompletionHandler<V, A> {
        completed(arg0: V, arg1: A): void;
        failed(arg0: $Throwable, arg1: A): void;
    }

    export class $FileLock implements $AutoCloseable {
        get shared(): boolean;
        isShared(): boolean;
        get valid(): boolean;
        isValid(): boolean;
        acquiredBy(): $Channel;
        channel(): $FileChannel;
        close(): void;
        overlaps(arg0: number, arg1: number): boolean;
        position(): number;
        release(): void;
        size(): number;
        toString(): string;
    }

    export interface $Channel extends $Closeable {
        get open(): boolean;
        isOpen(): boolean;
        close(): void;
    }

    export class $FileChannel$MapMode {
        static PRIVATE: $FileChannel$MapMode;
        static READ_ONLY: $FileChannel$MapMode;
        static READ_WRITE: $FileChannel$MapMode;
        toString(): string;
    }

    export interface $GatheringByteChannel extends $WritableByteChannel {
        write(arg0: $ByteBuffer[], arg1: number, arg2: number): number;
        write(arg0: $ByteBuffer[]): number;
        write(arg0: $ByteBuffer): number;
    }

    export interface $ReadableByteChannel extends $Channel {
        read(arg0: $ByteBuffer): number;
    }

    export interface $ScatteringByteChannel extends $ReadableByteChannel {
        read(arg0: $ByteBuffer[], arg1: number, arg2: number): number;
        read(arg0: $ByteBuffer[]): number;
        read(arg0: $ByteBuffer): number;
    }

    export interface $WritableByteChannel extends $Channel {
        write(arg0: $ByteBuffer): number;
    }

    export interface $ByteChannel extends $ReadableByteChannel, $WritableByteChannel {
        get open(): boolean;
        isOpen(): boolean;
        close(): void;
    }

    export interface $InterruptibleChannel extends $Channel {
        close(): void;
    }

}
