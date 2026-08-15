import { $InputStream } from "java:java/io";
import { $Comparable, $String } from "java:java/lang";
import { $AccessFlag, $AccessFlag_ } from "java:java/lang/reflect";
import { $ByteBuffer } from "java:java/nio";
import { $Optional, $Set } from "java:java/util";
import { $Supplier } from "java:java/util/function";

declare module "java:java/lang/module" {
    export class $ModuleDescriptor implements $Comparable {
        get automatic(): boolean;
        isAutomatic(): boolean;
        get open(): boolean;
        isOpen(): boolean;
        static newAutomaticModule(arg0: string): $ModuleDescriptor$Builder;
        static newModule(arg0: string, arg1: $ModuleDescriptor$Modifier_[]): $ModuleDescriptor$Builder;
        static newModule(arg0: string): $ModuleDescriptor$Builder;
        static newOpenModule(arg0: string): $ModuleDescriptor$Builder;
        static read(arg0: $InputStream, arg1: $Supplier<string[]>): $ModuleDescriptor;
        static read(arg0: $InputStream): $ModuleDescriptor;
        static read(arg0: $ByteBuffer, arg1: $Supplier<string[]>): $ModuleDescriptor;
        static read(arg0: $ByteBuffer): $ModuleDescriptor;
        accessFlags(): $Set<$AccessFlag>;
        compareTo(arg0: object): number;
        compareTo(arg0: $ModuleDescriptor): number;
        equals(arg0: object): boolean;
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

}
