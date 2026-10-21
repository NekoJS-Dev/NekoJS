import { $Closeable, $Closeable_, $File } from "java:java/io";
import { $Class, $Comparable, $Comparable_, $Enum, $Iterable } from "java:java/lang";
import { $URI } from "java:java/net";
import { $FileAttributeView, $FileAttributeView_, $FileStoreAttributeView, $FileStoreAttributeView_, $UserPrincipalLookupService } from "java:java/nio/file/attribute";
import { $FileSystemProvider } from "java:java/nio/file/spi";
import { $Iterator, $List, $Set } from "java:java/util";
import { $TimeUnit, $TimeUnit_ } from "java:java/util/concurrent";

export * as attribute from "java:java/nio/file/attribute";
export * as spi from "java:java/nio/file/spi";

declare module "java:java/nio/file" {
    export interface $Path extends $Comparable<$Path>, $Iterable<$Path>, $Watchable {
        compareTo(arg0: $Path): number;
        endsWith(arg0: string): boolean;
        endsWith(arg0: $Path): boolean;
        equals(arg0: unknown): boolean;
        getFileName(): $Path;
        getFileSystem(): $FileSystem;
        getNameCount(): number;
        getName(arg0: number): $Path;
        getParent(): $Path;
        getRoot(): $Path;
        hashCode(): number;
        isAbsolute(): boolean;
        iterator(): $Iterator<$Path>;
        normalize(): $Path;
        register(arg0: $WatchService, arg1: $WatchEvent$Kind<any>[], arg2?: $WatchEvent$Modifier_<string>[]): $WatchKey;
        register(arg0: $WatchService, arg1?: $WatchEvent$Kind<any>[]): $WatchKey;
        relativize(arg0: $Path): $Path;
        resolveSibling(arg0: string): $Path;
        resolveSibling(arg0: $Path): $Path;
        resolve(arg0: string): $Path;
        resolve(arg0: $Path): $Path;
        startsWith(arg0: string): boolean;
        startsWith(arg0: $Path): boolean;
        subpath(arg0: number, arg1: number): $Path;
        toAbsolutePath(): $Path;
        toFile(): $File;
        toRealPath(arg0?: $LinkOption_[]): $Path;
        toString(): string;
        toUri(): $URI;
    }
    export const $Path: {
        of(arg0: string, arg1?: string[]): $Path;
        of(arg0: $URI): $Path;
    };

    export class $FileSystem implements $Closeable {
        get fileStores(): $Iterable<$FileStore>;
        getFileStores(): $Iterable<$FileStore>;
        get rootDirectories(): $Iterable<$Path>;
        getRootDirectories(): $Iterable<$Path>;
        get separator(): string;
        getSeparator(): string;
        get userPrincipalLookupService(): $UserPrincipalLookupService;
        getUserPrincipalLookupService(): $UserPrincipalLookupService;
        get open(): boolean;
        isOpen(): boolean;
        get readOnly(): boolean;
        isReadOnly(): boolean;
        close(): void;
        getPathMatcher(arg0: string): $PathMatcher;
        getPath(arg0: string, arg1?: string[]): $Path;
        newWatchService(): $WatchService;
        provider(): $FileSystemProvider;
        supportedFileAttributeViews(): $Set<string>;
    }

    export class $LinkOption {
        static NOFOLLOW_LINKS: $LinkOption;
        name(): string;
        ordinal(): number;
        toString(): string;
        static values(): $LinkOption[];
        static valueOf(name: string): $LinkOption;
    }
    export interface $LinkOption extends $Enum<$LinkOption> {}

    export interface $WatchEvent$Kind<T> {
        name(): string;
        type(): $Class<T>;
    }

    export interface $WatchEvent$Modifier {
        name(): string;
    }

    export interface $WatchKey {
        cancel(): void;
        isValid(): boolean;
        pollEvents(): $List<$WatchEvent<any>>;
        reset(): boolean;
        watchable(): $Watchable;
    }

    export interface $WatchService extends $Closeable {
        close(): void;
        poll(arg0: number, arg1: $TimeUnit_): $WatchKey;
        poll(): $WatchKey;
        take(): $WatchKey;
    }

    export interface $Watchable {
        register(arg0: $WatchService, arg1: $WatchEvent$Kind<any>[], arg2?: $WatchEvent$Modifier_<string>[]): $WatchKey;
        register(arg0: $WatchService, arg1?: $WatchEvent$Kind<any>[]): $WatchKey;
    }

    export class $FileStore {
        get blockSize(): number;
        getBlockSize(): number;
        get totalSpace(): number;
        getTotalSpace(): number;
        get unallocatedSpace(): number;
        getUnallocatedSpace(): number;
        get usableSpace(): number;
        getUsableSpace(): number;
        get readOnly(): boolean;
        isReadOnly(): boolean;
        getAttribute(arg0: string): unknown;
        getFileStoreAttributeView<V extends $FileStoreAttributeView>(arg0: $Class<V>): V;
        name(): string;
        supportsFileAttributeView(arg0: $Class<$FileAttributeView>): boolean;
        supportsFileAttributeView(arg0: string): boolean;
        type(): string;
    }

    export interface $PathMatcher {
        matches(arg0: $Path): boolean;
    }

    export interface $WatchEvent<T> {
        context(): T;
        count(): number;
        kind(): $WatchEvent$Kind<T>;
    }

    export class $AccessMode {
        static EXECUTE: $AccessMode;
        static READ: $AccessMode;
        static WRITE: $AccessMode;
        name(): string;
        ordinal(): number;
        toString(): string;
        static values(): $AccessMode[];
        static valueOf(name: string): $AccessMode;
    }
    export interface $AccessMode extends $Enum<$AccessMode> {}

    export interface $CopyOption {
    }

    export interface $DirectoryStream<T> extends $Closeable, $Iterable<T> {
        iterator(): $Iterator<T>;
    }

    export interface $DirectoryStream$Filter<T> {
        accept(arg0: T): boolean;
    }

    export interface $OpenOption {
    }

    export type $LinkOption_ = $LinkOption | "NOFOLLOW_LINKS";
    export type $WatchEvent$Modifier_<CallbackResult = string> = (() => CallbackResult) | ($WatchEvent$Modifier & { readonly [Symbol.hasInstance]?: never });
    export type $PathMatcher_<CallbackArg0 = $Path, CallbackResult = boolean> = ((arg0: CallbackArg0) => CallbackResult) | ($PathMatcher & { readonly [Symbol.hasInstance]?: never });
    export type $AccessMode_ = $AccessMode | "EXECUTE" | "READ" | "WRITE";
    export type $DirectoryStream$Filter_<Host0 = any, CallbackArg0 = Host0, CallbackResult = boolean> = ((arg0: CallbackArg0) => CallbackResult) | ($DirectoryStream$Filter<Host0> & { readonly [Symbol.hasInstance]?: never });
}
