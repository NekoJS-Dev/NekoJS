import { $Class, $Enum, $String } from "java:java/lang";
import { $MethodHandle, $MethodHandles$Lookup, $MethodType, $TypeDescriptor$OfField, $TypeDescriptor$OfMethod } from "java:java/lang/invoke";
import { $List, $Optional } from "java:java/util";

declare module "java:java/lang/constant" {
    export interface $Constable {
        describeConstable(): $Optional<$ConstantDesc>;
    }

    export interface $ConstantDesc {
        resolveConstantDesc(arg0: $MethodHandles$Lookup): object;
    }

    export class $DynamicConstantDesc<T> implements $ConstantDesc {
        static ofCanonical<T>(arg0: $DirectMethodHandleDesc, arg1: string, arg2: $ClassDesc, arg3: $ConstantDesc[]): $ConstantDesc;
        static ofNamed<T>(arg0: $DirectMethodHandleDesc, arg1: string, arg2: $ClassDesc, arg3?: $ConstantDesc[]): $DynamicConstantDesc<T>;
        static of<T>(arg0: $DirectMethodHandleDesc, arg1?: $ConstantDesc[]): $DynamicConstantDesc<T>;
        static of<T>(arg0: $DirectMethodHandleDesc): $DynamicConstantDesc<T>;
        bootstrapArgsList(): $List<$ConstantDesc>;
        bootstrapArgs(): $ConstantDesc[];
        bootstrapMethod(): $DirectMethodHandleDesc;
        constantName(): string;
        constantType(): $ClassDesc;
        equals(arg0: object): boolean;
        hashCode(): number;
        resolveConstantDesc(arg0: $MethodHandles$Lookup): T;
        toString(): string;
    }

    export interface $ClassDesc extends $ConstantDesc, $TypeDescriptor$OfField {
        arrayType(arg0: number): $ClassDesc;
        arrayType(): $ClassDesc;
        arrayType(): $TypeDescriptor$OfField;
        componentType(): $ClassDesc;
        componentType(): $TypeDescriptor$OfField;
        descriptorString(): string;
        displayName(): string;
        equals(arg0: object): boolean;
        isArray(): boolean;
        isClassOrInterface(): boolean;
        isPrimitive(): boolean;
        nested(arg0: string, arg1?: string[]): $ClassDesc;
        nested(arg0: string): $ClassDesc;
        ofDescriptor(arg0: string): $ClassDesc;
        ofInternalName(arg0: string): $ClassDesc;
        of(arg0: string, arg1: string): $ClassDesc;
        of(arg0: string): $ClassDesc;
        packageName(): string;
        resolveConstantDesc(arg0: $MethodHandles$Lookup): $Class<any>;
        resolveConstantDesc(arg0: $MethodHandles$Lookup): object;
    }

    export interface $MethodHandleDesc extends $ConstantDesc {
        asType(arg0: $MethodTypeDesc): $MethodHandleDesc;
        equals(arg0: object): boolean;
        invocationType(): $MethodTypeDesc;
        ofConstructor(arg0: $ClassDesc, arg1?: $ClassDesc[]): $DirectMethodHandleDesc;
        ofField(arg0: $DirectMethodHandleDesc$Kind, arg1: $ClassDesc, arg2: string, arg3: $ClassDesc): $DirectMethodHandleDesc;
        ofMethod(arg0: $DirectMethodHandleDesc$Kind, arg1: $ClassDesc, arg2: string, arg3: $MethodTypeDesc): $DirectMethodHandleDesc;
        of(arg0: $DirectMethodHandleDesc$Kind, arg1: $ClassDesc, arg2: string, arg3: string): $DirectMethodHandleDesc;
        resolveConstantDesc(arg0: $MethodHandles$Lookup): object;
        resolveConstantDesc(arg0: $MethodHandles$Lookup): $MethodHandle;
    }

    export interface $MethodTypeDesc extends $ConstantDesc, $TypeDescriptor$OfMethod {
        changeParameterType(arg0: number, arg1: $ClassDesc): $MethodTypeDesc;
        changeParameterType(arg0: number, arg1: $TypeDescriptor$OfField): $TypeDescriptor$OfMethod;
        changeReturnType(arg0: $ClassDesc): $MethodTypeDesc;
        changeReturnType(arg0: $TypeDescriptor$OfField): $TypeDescriptor$OfMethod;
        descriptorString(): string;
        displayDescriptor(): string;
        dropParameterTypes(arg0: number, arg1: number): $MethodTypeDesc;
        dropParameterTypes(arg0: number, arg1: number): $TypeDescriptor$OfMethod;
        equals(arg0: object): boolean;
        insertParameterTypes(arg0: number, arg1?: $ClassDesc[]): $MethodTypeDesc;
        insertParameterTypes(arg0: number, arg1: $TypeDescriptor$OfField[]): $TypeDescriptor$OfMethod;
        ofDescriptor(arg0: string): $MethodTypeDesc;
        of(arg0: $ClassDesc, arg1?: $ClassDesc[]): $MethodTypeDesc;
        of(arg0: $ClassDesc, arg1: $ClassDesc[]): $MethodTypeDesc;
        of(arg0: $ClassDesc): $MethodTypeDesc;
        parameterArray(): $ClassDesc[];
        parameterArray(): $TypeDescriptor$OfField[];
        parameterCount(): number;
        parameterList(): $List<$ClassDesc>;
        parameterType(arg0: number): $ClassDesc;
        parameterType(arg0: number): $TypeDescriptor$OfField;
        resolveConstantDesc(arg0: $MethodHandles$Lookup): object;
        resolveConstantDesc(arg0: $MethodHandles$Lookup): $MethodType;
        returnType(): $ClassDesc;
        returnType(): $TypeDescriptor$OfField;
    }

    export interface $DirectMethodHandleDesc extends $MethodHandleDesc {
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

}
