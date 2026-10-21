import { $InputStream, $Reader, $Serializable } from "java:java/io";
import { $ClassLoader } from "java:java/lang";
import { $URL } from "java:java/net";
import { $CertPath, $Certificate } from "java:java/security/cert";
import { $Collection, $Date, $Enumeration, $List, $Map, $Map$Entry, $Properties, $Set } from "java:java/util";
import { $BiConsumer, $BiConsumer_, $BiFunction, $BiFunction_, $Function, $Function_ } from "java:java/util/function";
import { $Stream } from "java:java/util/stream";
import { $Subject } from "java:javax/security/auth";

export * as cert from "java:java/security/cert";

declare module "java:java/security" {
    export class $CodeSource implements $Serializable {
        constructor(arg0: $URL, arg1: $CodeSigner[]);
        constructor(arg0: $URL, arg1: $Certificate[]);
        get certificates(): $Certificate[];
        getCertificates(): $Certificate[];
        get codeSigners(): $CodeSigner[];
        getCodeSigners(): $CodeSigner[];
        get location(): $URL;
        getLocation(): $URL;
        equals(arg0: unknown): boolean;
        hashCode(): number;
        implies(arg0: $CodeSource): boolean;
        toString(): string;
    }

    export class $Permission implements $Guard, $Serializable {
        constructor(arg0: string);
        get actions(): string;
        getActions(): string;
        get name(): string;
        getName(): string;
        checkGuard(arg0: unknown): void;
        equals(arg0: unknown): boolean;
        hashCode(): number;
        implies(arg0: $Permission): boolean;
        newPermissionCollection(): $PermissionCollection;
        toString(): string;
    }

    export class $PermissionCollection implements $Serializable {
        constructor();
        get readOnly(): boolean;
        isReadOnly(): boolean;
        add(arg0: $Permission): void;
        elementsAsStream(): $Stream<$Permission>;
        elements(): $Enumeration<$Permission>;
        implies(arg0: $Permission): boolean;
        setReadOnly(): void;
        toString(): string;
    }

    export interface $Principal {
        equals(arg0: unknown): boolean;
        getName(): string;
        hashCode(): number;
        implies(arg0: $Subject): boolean;
        toString(): string;
    }

    export class $ProtectionDomain {
        constructor(arg0: $CodeSource, arg1: $PermissionCollection);
        constructor(arg0: $CodeSource, arg1: $PermissionCollection, arg2: $ClassLoader, arg3: $Principal_<string>[]);
        get classLoader(): $ClassLoader;
        getClassLoader(): $ClassLoader;
        get codeSource(): $CodeSource;
        getCodeSource(): $CodeSource;
        get permissions(): $PermissionCollection;
        getPermissions(): $PermissionCollection;
        get principals(): $Principal[];
        getPrincipals(): $Principal[];
        implies(arg0: $Permission): boolean;
        staticPermissionsOnly(): boolean;
        toString(): string;
    }

    export class $CodeSigner implements $Serializable {
        constructor(arg0: $CertPath, arg1: $Timestamp);
        get signerCertPath(): $CertPath;
        getSignerCertPath(): $CertPath;
        get timestamp(): $Timestamp;
        getTimestamp(): $Timestamp;
        equals(arg0: unknown): boolean;
        hashCode(): number;
        toString(): string;
    }

    export class $Timestamp implements $Serializable {
        constructor(arg0: $Date, arg1: $CertPath);
        get signerCertPath(): $CertPath;
        getSignerCertPath(): $CertPath;
        get timestamp(): $Date;
        getTimestamp(): $Date;
        equals(arg0: unknown): boolean;
        hashCode(): number;
        toString(): string;
    }

    export interface $Guard {
        checkGuard(arg0: unknown): void;
    }

    export class $Provider extends $Properties {
        get info(): string;
        getInfo(): string;
        get name(): string;
        getName(): string;
        get services(): $Set<$Provider$Service>;
        getServices(): $Set<$Provider$Service>;
        get versionStr(): string;
        getVersionStr(): string;
        get version(): number;
        getVersion(): number;
        get configured(): boolean;
        isConfigured(): boolean;
        clear(): void;
        computeIfAbsent(arg0: unknown, arg1: $Function_<unknown, any, unknown, any>): unknown;
        computeIfPresent(arg0: unknown, arg1: $BiFunction_<unknown, unknown, any, unknown, unknown, any>): unknown;
        compute(arg0: unknown, arg1: $BiFunction_<unknown, unknown, any, unknown, unknown, any>): unknown;
        configure(arg0: string): $Provider;
        elements(): $Enumeration<unknown>;
        entrySet(): $Set<$Map$Entry<unknown, unknown>>;
        equals(arg0: unknown): boolean;
        forEach(arg0: $BiConsumer_<unknown, unknown, unknown, unknown>): void;
        getOrDefault(arg0: unknown, arg1: unknown): unknown;
        getProperty(arg0: string, arg1: string): string;
        getProperty(arg0: string): string;
        getService(arg0: string, arg1: string): $Provider$Service;
        get(arg0: unknown): unknown;
        hashCode(): number;
        keySet(): $Set<unknown>;
        keys(): $Enumeration<unknown>;
        load(arg0: $InputStream): void;
        load(arg0: $Reader): void;
        merge(arg0: unknown, arg1: unknown, arg2: $BiFunction_<unknown, unknown, any, unknown, unknown, any>): unknown;
        putAll(arg0: ($Map<any, any> | ([any] extends [PropertyKey] ? Partial<Record<Extract<any, PropertyKey>, any>> : never))): void;
        putIfAbsent(arg0: unknown, arg1: unknown): unknown;
        put(arg0: unknown, arg1: unknown): unknown;
        remove(arg0: unknown, arg1: unknown): boolean;
        remove(arg0: unknown): unknown;
        replaceAll(arg0: $BiFunction_<unknown, unknown, any, unknown, unknown, any>): void;
        replace(arg0: unknown, arg1: unknown, arg2: unknown): boolean;
        replace(arg0: unknown, arg1: unknown): unknown;
        toString(): string;
        values(): $Collection<unknown>;
    }

    export interface $PublicKey extends $Key {
    }
    export const $PublicKey: {
        readonly serialVersionUID: number;
    };

    export class $Provider$Service {
        constructor(arg0: $Provider, arg1: string, arg2: string, arg3: string, arg4: string[], arg5: ($Map<string, string> | ([string] extends [PropertyKey] ? Partial<Record<Extract<string, PropertyKey>, string>> : never)));
        get algorithm(): string;
        getAlgorithm(): string;
        get className(): string;
        getClassName(): string;
        get provider(): $Provider;
        getProvider(): $Provider;
        get type(): string;
        getType(): string;
        getAttribute(arg0: string): string;
        newInstance(arg0: unknown): unknown;
        supportsParameter(arg0: unknown): boolean;
        toString(): string;
    }

    export interface $Key extends $Serializable {
        getAlgorithm(): string;
        getEncoded(): number[];
        getFormat(): string;
    }
    export const $Key: {
        readonly serialVersionUID: number;
    };

    export class $AccessControlContext {
        constructor(arg0: $AccessControlContext, arg1: $DomainCombiner_<$ProtectionDomain[], $ProtectionDomain[], $ProtectionDomain[]>);
        constructor(arg0: $ProtectionDomain[]);
        get domainCombiner(): $DomainCombiner;
        getDomainCombiner(): $DomainCombiner;
        checkPermission(arg0: $Permission): void;
        equals(arg0: unknown): boolean;
        hashCode(): number;
    }

    export interface $PrivilegedAction<T> {
        run(): T;
    }

    export interface $PrivilegedExceptionAction<T> {
        run(): T;
    }

    export interface $DomainCombiner {
        combine(arg0: $ProtectionDomain[], arg1: $ProtectionDomain[]): $ProtectionDomain[];
    }

    export type $Principal_<CallbackResult = string> = (() => CallbackResult) | ($Principal & { readonly [Symbol.hasInstance]?: never });
    export type $Guard_<CallbackArg0 = unknown> = ((arg0: CallbackArg0) => void) | ($Guard & { readonly [Symbol.hasInstance]?: never });
    export type $PrivilegedAction_<Host0 = any, CallbackResult = Host0> = (() => CallbackResult) | ($PrivilegedAction<Host0> & { readonly [Symbol.hasInstance]?: never });
    export type $PrivilegedExceptionAction_<Host0 = any, CallbackResult = Host0> = (() => CallbackResult) | ($PrivilegedExceptionAction<Host0> & { readonly [Symbol.hasInstance]?: never });
    export type $DomainCombiner_<CallbackArg0 = $ProtectionDomain[], CallbackArg1 = $ProtectionDomain[], CallbackResult = $ProtectionDomain[]> = ((arg0: CallbackArg0, arg1: CallbackArg1) => CallbackResult) | ($DomainCombiner & { readonly [Symbol.hasInstance]?: never });
}
