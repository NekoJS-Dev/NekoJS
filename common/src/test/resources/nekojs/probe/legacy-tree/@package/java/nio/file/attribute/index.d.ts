import { $Comparable, $Comparable_ } from "java:java/lang";
import { $Principal, $Principal_ } from "java:java/security";
import { $Instant } from "java:java/time";
import { $TimeUnit, $TimeUnit_ } from "java:java/util/concurrent";

declare module "java:java/nio/file/attribute" {
    export class $UserPrincipalLookupService {
        lookupPrincipalByGroupName(arg0: string): $GroupPrincipal;
        lookupPrincipalByName(arg0: string): $UserPrincipal;
    }

    export interface $FileAttributeView extends $AttributeView {
    }

    export interface $FileStoreAttributeView extends $AttributeView {
    }

    export interface $AttributeView {
        name(): string;
    }

    export interface $GroupPrincipal extends $UserPrincipal {
    }

    export interface $UserPrincipal extends $Principal {
    }

    export interface $BasicFileAttributes {
        creationTime(): $FileTime;
        fileKey(): unknown;
        isDirectory(): boolean;
        isOther(): boolean;
        isRegularFile(): boolean;
        isSymbolicLink(): boolean;
        lastAccessTime(): $FileTime;
        lastModifiedTime(): $FileTime;
        size(): number;
    }

    export interface $FileAttribute<T> {
        name(): string;
        value(): T;
    }

    export class $FileTime implements $Comparable<$FileTime> {
        static fromMillis(arg0: number): $FileTime;
        static from(arg0: $Instant): $FileTime;
        static from(arg0: number, arg1: $TimeUnit_): $FileTime;
        compareTo(arg0: $FileTime): number;
        equals(arg0: unknown): boolean;
        hashCode(): number;
        toInstant(): $Instant;
        toMillis(): number;
        toString(): string;
        to(arg0: $TimeUnit_): number;
    }

    export type $FileAttributeView_<CallbackResult = string> = (() => CallbackResult) | ($FileAttributeView & { readonly [Symbol.hasInstance]?: never }) | (globalThis.NoInfer<{ readonly "name": () => CallbackResult } & { readonly [Symbol.hasInstance]?: never }>);
    export type $FileStoreAttributeView_<CallbackResult = string> = (() => CallbackResult) | ($FileStoreAttributeView & { readonly [Symbol.hasInstance]?: never }) | (globalThis.NoInfer<{ readonly "name": () => CallbackResult } & { readonly [Symbol.hasInstance]?: never }>);
    export type $AttributeView_<CallbackResult = string> = (() => CallbackResult) | ($AttributeView & { readonly [Symbol.hasInstance]?: never }) | (globalThis.NoInfer<{ readonly "name": () => CallbackResult } & { readonly [Symbol.hasInstance]?: never }>);
    export type $GroupPrincipal_<CallbackResult = string> = (() => CallbackResult) | ($GroupPrincipal & { readonly [Symbol.hasInstance]?: never }) | (globalThis.NoInfer<{ readonly "getName": () => CallbackResult } & { readonly [Symbol.hasInstance]?: never }>);
    export type $UserPrincipal_<CallbackResult = string> = (() => CallbackResult) | ($UserPrincipal & { readonly [Symbol.hasInstance]?: never }) | (globalThis.NoInfer<{ readonly "getName": () => CallbackResult } & { readonly [Symbol.hasInstance]?: never }>);
}
