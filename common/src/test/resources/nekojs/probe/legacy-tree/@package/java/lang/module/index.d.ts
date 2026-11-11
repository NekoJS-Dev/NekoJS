import { $Closeable, $Closeable_, $InputStream } from "java:java/io";
import { $Comparable, $Comparable_, $Enum } from "java:java/lang";
import { $AccessFlag, $AccessFlag_ } from "java:java/lang/reflect";
import { $URI } from "java:java/net";
import { $ByteBuffer } from "java:java/nio";
import { $Path } from "java:java/nio/file";
import { $Collection, $Collection_, $List, $List_, $Optional, $Set, $Set_ } from "java:java/util";
import { $Supplier, $Supplier_ } from "java:java/util/function";
import { $Stream } from "java:java/util/stream";

declare module "java:java/lang/module" {
    export class $ModuleDescriptor implements $Comparable<$ModuleDescriptor> {
        get automatic(): boolean;
        isAutomatic(): boolean;
        get open(): boolean;
        isOpen(): boolean;
        static newAutomaticModule(arg0: string): $ModuleDescriptor$Builder;
        static newModule(arg0: string, arg1: $Set<$ModuleDescriptor$Modifier>): $ModuleDescriptor$Builder;
        static newModule(arg0: string): $ModuleDescriptor$Builder;
        static newOpenModule(arg0: string): $ModuleDescriptor$Builder;
        static read(arg0: $InputStream, arg1: $Supplier_<$Set<string>, $Set<string>>): $ModuleDescriptor;
        static read(arg0: $InputStream): $ModuleDescriptor;
        static read(arg0: $ByteBuffer, arg1: $Supplier_<$Set<string>, $Set<string>>): $ModuleDescriptor;
        static read(arg0: $ByteBuffer): $ModuleDescriptor;
        accessFlags(): $Set<$AccessFlag>;
        compareTo(arg0: $ModuleDescriptor): number;
        equals(arg0: unknown): boolean;
        exports(): $Set<$ModuleDescriptor$Exports>;
        hashCode(): number;
        mainClass(): $Optional<string>;
        modifiers(): $Set<$ModuleDescriptor$Modifier>;
        name(): string;
        opens(): $Set<$ModuleDescriptor$Opens>;
        packages(): $Set<string>;
        provides(): $Set<$ModuleDescriptor$Provides>;
        rawVersion(): $Optional<string>;
        requires(): $Set<$ModuleDescriptor$Requires>;
        toNameAndVersion(): string;
        toString(): string;
        uses(): $Set<string>;
        version(): $Optional<$ModuleDescriptor$Version>;
    }

    export class $Configuration {
        static empty(): $Configuration;
        static resolveAndBind(
            arg0: $ModuleFinder,
            arg1: $List_<$Configuration>,
            arg2: $ModuleFinder,
            arg3: $Collection_<string>
        ): $Configuration;
        static resolve(
            arg0: $ModuleFinder,
            arg1: $List_<$Configuration>,
            arg2: $ModuleFinder,
            arg3: $Collection_<string>
        ): $Configuration;
        findModule(arg0: string): $Optional<$ResolvedModule>;
        modules(): $Set<$ResolvedModule>;
        parents(): $List<$Configuration>;
        resolveAndBind(arg0: $ModuleFinder, arg1: $ModuleFinder, arg2: $Collection_<string>): $Configuration;
        resolve(arg0: $ModuleFinder, arg1: $ModuleFinder, arg2: $Collection_<string>): $Configuration;
        toString(): string;
    }

    export interface $ModuleFinder {
        findAll(): $Set<$ModuleReference>;
        find(arg0: string): $Optional<$ModuleReference>;
    }
    export const $ModuleFinder: {
        compose(arg0?: $ModuleFinder[]): $ModuleFinder;
        ofSystem(): $ModuleFinder;
        of(arg0?: $Path[]): $ModuleFinder;
    };

    export class $ResolvedModule {
        configuration(): $Configuration;
        equals(arg0: unknown): boolean;
        hashCode(): number;
        name(): string;
        reads(): $Set<$ResolvedModule>;
        reference(): $ModuleReference;
        toString(): string;
    }

    export class $ModuleDescriptor$Builder {
        build(): $ModuleDescriptor;
        exports(arg0: string, arg1: $Set<string>): $ModuleDescriptor$Builder;
        exports(arg0: string): $ModuleDescriptor$Builder;
        exports(arg0: $ModuleDescriptor$Exports): $ModuleDescriptor$Builder;
        exports(
            arg0: $Set<$ModuleDescriptor$Exports$Modifier>,
            arg1: string,
            arg2: $Set<string>
        ): $ModuleDescriptor$Builder;
        exports(arg0: $Set<$ModuleDescriptor$Exports$Modifier>, arg1: string): $ModuleDescriptor$Builder;
        mainClass(arg0: string): $ModuleDescriptor$Builder;
        opens(arg0: string, arg1: $Set<string>): $ModuleDescriptor$Builder;
        opens(arg0: string): $ModuleDescriptor$Builder;
        opens(arg0: $ModuleDescriptor$Opens): $ModuleDescriptor$Builder;
        opens(
            arg0: $Set<$ModuleDescriptor$Opens$Modifier>,
            arg1: string,
            arg2: $Set<string>
        ): $ModuleDescriptor$Builder;
        opens(arg0: $Set<$ModuleDescriptor$Opens$Modifier>, arg1: string): $ModuleDescriptor$Builder;
        packages(arg0: $Set<string>): $ModuleDescriptor$Builder;
        provides(arg0: string, arg1: $List_<string>): $ModuleDescriptor$Builder;
        provides(arg0: $ModuleDescriptor$Provides): $ModuleDescriptor$Builder;
        requires(arg0: string): $ModuleDescriptor$Builder;
        requires(arg0: $ModuleDescriptor$Requires): $ModuleDescriptor$Builder;
        requires(
            arg0: $Set<$ModuleDescriptor$Requires$Modifier>,
            arg1: string,
            arg2: $ModuleDescriptor$Version
        ): $ModuleDescriptor$Builder;
        requires(arg0: $Set<$ModuleDescriptor$Requires$Modifier>, arg1: string): $ModuleDescriptor$Builder;
        uses(arg0: string): $ModuleDescriptor$Builder;
        version(arg0: string): $ModuleDescriptor$Builder;
        version(arg0: $ModuleDescriptor$Version): $ModuleDescriptor$Builder;
    }

    export class $ModuleDescriptor$Exports implements $Comparable<$ModuleDescriptor$Exports> {
        get qualified(): boolean;
        isQualified(): boolean;
        accessFlags(): $Set<$AccessFlag>;
        compareTo(arg0: $ModuleDescriptor$Exports): number;
        equals(arg0: unknown): boolean;
        hashCode(): number;
        modifiers(): $Set<$ModuleDescriptor$Exports$Modifier>;
        source(): string;
        targets(): $Set<string>;
        toString(): string;
    }

    export class $ModuleDescriptor$Modifier {
        static AUTOMATIC: $ModuleDescriptor$Modifier;
        static MANDATED: $ModuleDescriptor$Modifier;
        static OPEN: $ModuleDescriptor$Modifier;
        static SYNTHETIC: $ModuleDescriptor$Modifier;
        name(): string;
        ordinal(): number;
        toString(): string;
        static values(): $ModuleDescriptor$Modifier[];
        static valueOf(name: string): $ModuleDescriptor$Modifier;
    }
    export interface $ModuleDescriptor$Modifier extends $Enum<$ModuleDescriptor$Modifier> {}

    export class $ModuleDescriptor$Opens implements $Comparable<$ModuleDescriptor$Opens> {
        get qualified(): boolean;
        isQualified(): boolean;
        accessFlags(): $Set<$AccessFlag>;
        compareTo(arg0: $ModuleDescriptor$Opens): number;
        equals(arg0: unknown): boolean;
        hashCode(): number;
        modifiers(): $Set<$ModuleDescriptor$Opens$Modifier>;
        source(): string;
        targets(): $Set<string>;
        toString(): string;
    }

    export class $ModuleDescriptor$Provides implements $Comparable<$ModuleDescriptor$Provides> {
        compareTo(arg0: $ModuleDescriptor$Provides): number;
        equals(arg0: unknown): boolean;
        hashCode(): number;
        providers(): $List<string>;
        service(): string;
        toString(): string;
    }

    export class $ModuleDescriptor$Requires implements $Comparable<$ModuleDescriptor$Requires> {
        accessFlags(): $Set<$AccessFlag>;
        compareTo(arg0: $ModuleDescriptor$Requires): number;
        compiledVersion(): $Optional<$ModuleDescriptor$Version>;
        equals(arg0: unknown): boolean;
        hashCode(): number;
        modifiers(): $Set<$ModuleDescriptor$Requires$Modifier>;
        name(): string;
        rawCompiledVersion(): $Optional<string>;
        toString(): string;
    }

    export class $ModuleDescriptor$Version implements $Comparable<$ModuleDescriptor$Version> {
        static parse(arg0: string): $ModuleDescriptor$Version;
        compareTo(arg0: $ModuleDescriptor$Version): number;
        equals(arg0: unknown): boolean;
        hashCode(): number;
        toString(): string;
    }

    export class $ModuleDescriptor$Exports$Modifier {
        static MANDATED: $ModuleDescriptor$Exports$Modifier;
        static SYNTHETIC: $ModuleDescriptor$Exports$Modifier;
        name(): string;
        ordinal(): number;
        toString(): string;
        static values(): $ModuleDescriptor$Exports$Modifier[];
        static valueOf(name: string): $ModuleDescriptor$Exports$Modifier;
    }
    export interface $ModuleDescriptor$Exports$Modifier extends $Enum<$ModuleDescriptor$Exports$Modifier> {}

    export class $ModuleDescriptor$Opens$Modifier {
        static MANDATED: $ModuleDescriptor$Opens$Modifier;
        static SYNTHETIC: $ModuleDescriptor$Opens$Modifier;
        name(): string;
        ordinal(): number;
        toString(): string;
        static values(): $ModuleDescriptor$Opens$Modifier[];
        static valueOf(name: string): $ModuleDescriptor$Opens$Modifier;
    }
    export interface $ModuleDescriptor$Opens$Modifier extends $Enum<$ModuleDescriptor$Opens$Modifier> {}

    export class $ModuleDescriptor$Requires$Modifier {
        static MANDATED: $ModuleDescriptor$Requires$Modifier;
        static STATIC: $ModuleDescriptor$Requires$Modifier;
        static SYNTHETIC: $ModuleDescriptor$Requires$Modifier;
        static TRANSITIVE: $ModuleDescriptor$Requires$Modifier;
        name(): string;
        ordinal(): number;
        toString(): string;
        static values(): $ModuleDescriptor$Requires$Modifier[];
        static valueOf(name: string): $ModuleDescriptor$Requires$Modifier;
    }
    export interface $ModuleDescriptor$Requires$Modifier extends $Enum<$ModuleDescriptor$Requires$Modifier> {}

    export class $ModuleReference {
        descriptor(): $ModuleDescriptor;
        location(): $Optional<$URI>;
        open(): $ModuleReader;
    }

    export interface $ModuleReader extends $Closeable {
        close(): void;
        find(arg0: string): $Optional<$URI>;
        list(): $Stream<string>;
        open(arg0: string): $Optional<$InputStream>;
        read(arg0: string): $Optional<$ByteBuffer>;
        release(arg0: $ByteBuffer): void;
    }

    export type $ModuleDescriptor$Modifier_ = $ModuleDescriptor$Modifier | "AUTOMATIC" | "MANDATED" | "OPEN" | "SYNTHETIC";
    export type $ModuleDescriptor$Exports$Modifier_ = $ModuleDescriptor$Exports$Modifier | "MANDATED" | "SYNTHETIC";
    export type $ModuleDescriptor$Opens$Modifier_ = $ModuleDescriptor$Opens$Modifier | "MANDATED" | "SYNTHETIC";
    export type $ModuleDescriptor$Requires$Modifier_ = $ModuleDescriptor$Requires$Modifier | "MANDATED" | "STATIC" | "SYNTHETIC" | "TRANSITIVE";
}
