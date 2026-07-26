

        accessFlags(): $Set<$AccessFlag>;
        compareTo(arg0: $ModuleDescriptor): number;
        compareTo(arg0: object): number;
        equals(arg0: object): boolean;
        exports(): $Set<$ModuleDescriptor$Exports>;
        get automatic(): boolean;
        get open(): boolean;
        hashCode(): number;
        isAutomatic(): boolean;
        isOpen(): boolean;
        mainClass(): $Optional<string>;
        modifiers(): $Set<$ModuleDescriptor$Modifier>;
        name(): string;
        opens(): $Set<$ModuleDescriptor$Opens>;
        packages(): $Set<string>;
        provides(): $Set<$ModuleDescriptor$Provides>;
        rawVersion(): $Optional<string>;
        requires(): $Set<$ModuleDescriptor$Requires>;
        static newAutomaticModule(arg0: string): $ModuleDescriptor$Builder;
        static newModule(arg0: string): $ModuleDescriptor$Builder;
        static newModule(arg0: string, arg1: $ModuleDescriptor$Modifier[]): $ModuleDescriptor$Builder;
        static newOpenModule(arg0: string): $ModuleDescriptor$Builder;
        static read(arg0: $ByteBuffer): $ModuleDescriptor;
        static read(arg0: $ByteBuffer, arg1: $Supplier<string[]>): $ModuleDescriptor;
        static read(arg0: $InputStream): $ModuleDescriptor;
        static read(arg0: $InputStream, arg1: $Supplier<string[]>): $ModuleDescriptor;
        toNameAndVersion(): string;
        toString(): string;
        uses(): $Set<string>;
        version(): $Optional<$ModuleDescriptor$Version>;
    export class $ModuleDescriptor implements $Comparable {
    }
declare module "java:java/lang/module" {
import { $AccessFlag } from "java:java/lang/reflect";
import { $ByteBuffer } from "java:java/nio";
import { $Comparable, $String } from "java:java/lang";
import { $InputStream } from "java:java/io";
import { $Optional, $Set } from "java:java/util";
import { $Supplier } from "java:java/util/function";
}