import { $Class, $Enum } from "java:java/lang";
import { $MethodHandle, $MethodHandles$Lookup, $MethodType, $TypeDescriptor$OfField, $TypeDescriptor$OfMethod } from "java:java/lang/invoke";
import { $List, $List_, $Optional, $OptionalView } from "java:java/util";

declare module "java:java/lang/constant" {
    export interface $ClassDesc extends $ConstantDesc, $TypeDescriptor$OfField<$ClassDesc> {
        get array(): boolean;
        isArray(): boolean;
        get classOrInterface(): boolean;
        isClassOrInterface(): boolean;
        get primitive(): boolean;
        isPrimitive(): boolean;
        arrayType(arg0: number): $ClassDesc;
        arrayType(): $ClassDesc;
        componentType(): $ClassDesc;
        descriptorString(): string;
        displayName(): string;
        equals(arg0: unknown): boolean;
        nested(arg0: string, arg1?: string[]): $ClassDesc;
        nested(arg0: string): $ClassDesc;
        packageName(): string;
        resolveConstantDesc(arg0: $MethodHandles$Lookup): $Class<any>;
    }
    export const $ClassDesc: {
        ofDescriptor(arg0: string): $ClassDesc;
        ofInternalName(arg0: string): $ClassDesc;
        of(arg0: string, arg1: string): $ClassDesc;
        of(arg0: string): $ClassDesc;
    };

    export interface $Constable {
        describeConstable(): $OptionalView<(number | string | $ConstantDesc)>;
    }

    export interface $ConstantDesc {
        resolveConstantDesc(arg0: $MethodHandles$Lookup): unknown;
    }

    export interface $DirectMethodHandleDesc extends $MethodHandleDesc {
        get ownerInterface(): boolean;
        isOwnerInterface(): boolean;
        kind(): $DirectMethodHandleDesc$Kind;
        lookupDescriptor(): string;
        methodName(): string;
        owner(): $ClassDesc;
        refKind(): number;
    }

    export class $DirectMethodHandleDesc$Kind {
        static CONSTRUCTOR: $DirectMethodHandleDesc$Kind;
        static GETTER: $DirectMethodHandleDesc$Kind;
        static INTERFACE_SPECIAL: $DirectMethodHandleDesc$Kind;
        static INTERFACE_STATIC: $DirectMethodHandleDesc$Kind;
        static INTERFACE_VIRTUAL: $DirectMethodHandleDesc$Kind;
        static SETTER: $DirectMethodHandleDesc$Kind;
        static SPECIAL: $DirectMethodHandleDesc$Kind;
        static STATIC: $DirectMethodHandleDesc$Kind;
        static STATIC_GETTER: $DirectMethodHandleDesc$Kind;
        static STATIC_SETTER: $DirectMethodHandleDesc$Kind;
        static VIRTUAL: $DirectMethodHandleDesc$Kind;
        name(): string;
        ordinal(): number;
        toString(): string;
        static values(): $DirectMethodHandleDesc$Kind[];
        static valueOf(name: string): $DirectMethodHandleDesc$Kind;
    }
    export interface $DirectMethodHandleDesc$Kind extends $Enum<$DirectMethodHandleDesc$Kind> {}

    export class $DynamicConstantDesc<T> implements $ConstantDesc {
        static ofCanonical<T>(
            arg0: $DirectMethodHandleDesc,
            arg1: string,
            arg2: $ClassDesc,
            arg3: (number | string | $ConstantDesc_<$MethodHandles$Lookup, unknown>)[]
        ): (number | string | $ConstantDesc);
        static ofNamed<T>(
            arg0: $DirectMethodHandleDesc,
            arg1: string,
            arg2: $ClassDesc,
            arg3?: (number | string | $ConstantDesc_<$MethodHandles$Lookup, unknown>)[]
        ): $DynamicConstantDesc<T>;
        static of<T>(
            arg0: $DirectMethodHandleDesc,
            arg1?: (number | string | $ConstantDesc_<$MethodHandles$Lookup, unknown>)[]
        ): $DynamicConstantDesc<T>;
        static of<T>(arg0: $DirectMethodHandleDesc): $DynamicConstantDesc<T>;
        bootstrapArgsList(): $List<(number | string | $ConstantDesc)>;
        bootstrapArgs(): (number | string | $ConstantDesc)[];
        bootstrapMethod(): $DirectMethodHandleDesc;
        constantName(): string;
        constantType(): $ClassDesc;
        equals(arg0: unknown): boolean;
        hashCode(): number;
        resolveConstantDesc(arg0: $MethodHandles$Lookup): T;
        toString(): string;
    }

    export interface $MethodHandleDesc extends $ConstantDesc {
        asType(arg0: $MethodTypeDesc): $MethodHandleDesc;
        equals(arg0: unknown): boolean;
        invocationType(): $MethodTypeDesc;
        resolveConstantDesc(arg0: $MethodHandles$Lookup): $MethodHandle;
    }
    export const $MethodHandleDesc: {
        ofConstructor(arg0: $ClassDesc, arg1?: $ClassDesc[]): $DirectMethodHandleDesc;
        ofField(
            arg0: $DirectMethodHandleDesc$Kind_,
            arg1: $ClassDesc,
            arg2: string,
            arg3: $ClassDesc
        ): $DirectMethodHandleDesc;
        ofMethod(
            arg0: $DirectMethodHandleDesc$Kind_,
            arg1: $ClassDesc,
            arg2: string,
            arg3: $MethodTypeDesc
        ): $DirectMethodHandleDesc;
        of(arg0: $DirectMethodHandleDesc$Kind_, arg1: $ClassDesc, arg2: string, arg3: string): $DirectMethodHandleDesc;
    };

    export interface $MethodTypeDesc extends $ConstantDesc, $TypeDescriptor$OfMethod<$ClassDesc, $MethodTypeDesc> {
        changeParameterType(arg0: number, arg1: $ClassDesc): $MethodTypeDesc;
        changeReturnType(arg0: $ClassDesc): $MethodTypeDesc;
        descriptorString(): string;
        displayDescriptor(): string;
        dropParameterTypes(arg0: number, arg1: number): $MethodTypeDesc;
        equals(arg0: unknown): boolean;
        insertParameterTypes(arg0: number, arg1?: $ClassDesc[]): $MethodTypeDesc;
        parameterArray(): $ClassDesc[];
        parameterCount(): number;
        parameterList(): $List<$ClassDesc>;
        parameterType(arg0: number): $ClassDesc;
        resolveConstantDesc(arg0: $MethodHandles$Lookup): $MethodType;
        returnType(): $ClassDesc;
    }
    export const $MethodTypeDesc: {
        ofDescriptor(arg0: string): $MethodTypeDesc;
        of(arg0: $ClassDesc, arg1?: $ClassDesc[]): $MethodTypeDesc;
        of(arg0: $ClassDesc, arg1: $List_<$ClassDesc>): $MethodTypeDesc;
        of(arg0: $ClassDesc): $MethodTypeDesc;
    };

    export type $Constable_<CallbackResult = $Optional<(number | string | $ConstantDesc)>> = (() => CallbackResult) | ($Constable & { readonly [Symbol.hasInstance]?: never }) | (globalThis.NoInfer<{ readonly "describeConstable": () => CallbackResult } & { readonly [Symbol.hasInstance]?: never }>);
    export type $ConstantDesc_<CallbackArg0 = $MethodHandles$Lookup, CallbackResult = unknown> = ($ConstantDesc & { readonly [Symbol.hasInstance]?: never });
    export type $DirectMethodHandleDesc$Kind_ = $DirectMethodHandleDesc$Kind | "CONSTRUCTOR" | "GETTER" | "INTERFACE_SPECIAL" | "INTERFACE_STATIC" | "INTERFACE_VIRTUAL" | "SETTER" | "SPECIAL" | "STATIC" | "STATIC_GETTER" | "STATIC_SETTER" | "VIRTUAL";
}
