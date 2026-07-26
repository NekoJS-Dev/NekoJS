








        arrayType(): $ClassDesc;
        arrayType(): $TypeDescriptor$OfField;
        arrayType(arg0: number): $ClassDesc;
        asType(arg0: $MethodTypeDesc): $MethodHandleDesc;
        bootstrapArgs(): $ConstantDesc[];
        bootstrapArgsList(): $List<$ConstantDesc>;
        bootstrapMethod(): $DirectMethodHandleDesc;
        changeParameterType(arg0: number, arg1: $ClassDesc): $MethodTypeDesc;
        changeParameterType(arg0: number, arg1: $TypeDescriptor$OfField): $TypeDescriptor$OfMethod;
        changeReturnType(arg0: $ClassDesc): $MethodTypeDesc;
        changeReturnType(arg0: $TypeDescriptor$OfField): $TypeDescriptor$OfMethod;
        componentType(): $ClassDesc;
        componentType(): $TypeDescriptor$OfField;
        constantName(): string;
        constantType(): $ClassDesc;
        describeConstable(): $Optional<$ConstantDesc>;
        descriptorString(): string;
        descriptorString(): string;
        displayDescriptor(): string;
        displayName(): string;
        dropParameterTypes(arg0: number, arg1: number): $MethodTypeDesc;
        dropParameterTypes(arg0: number, arg1: number): $TypeDescriptor$OfMethod;
        equals(arg0: object): boolean;
        equals(arg0: object): boolean;
        equals(arg0: object): boolean;
        equals(arg0: object): boolean;
        hashCode(): number;
        insertParameterTypes(arg0: number, arg1: $TypeDescriptor$OfField[]): $TypeDescriptor$OfMethod;
        insertParameterTypes(arg0: number, arg1?: $ClassDesc[]): $MethodTypeDesc;
        invocationType(): $MethodTypeDesc;
        isArray(): boolean;
        isClassOrInterface(): boolean;
        isOwnerInterface(): boolean;
        isPrimitive(): boolean;
        kind(): $DirectMethodHandleDesc$Kind;
        lookupDescriptor(): string;
        methodName(): string;
        name(): string;
        nested(arg0: string): $ClassDesc;
        nested(arg0: string, arg1?: string[]): $ClassDesc;
        of(arg0: $ClassDesc): $MethodTypeDesc;
        of(arg0: $ClassDesc, arg1: $ClassDesc[]): $MethodTypeDesc;
        of(arg0: $ClassDesc, arg1?: $ClassDesc[]): $MethodTypeDesc;
        of(arg0: $DirectMethodHandleDesc$Kind, arg1: $ClassDesc, arg2: string, arg3: string): $DirectMethodHandleDesc;
        of(arg0: string): $ClassDesc;
        of(arg0: string, arg1: string): $ClassDesc;
        ofConstructor(arg0: $ClassDesc, arg1?: $ClassDesc[]): $DirectMethodHandleDesc;
        ofDescriptor(arg0: string): $ClassDesc;
        ofDescriptor(arg0: string): $MethodTypeDesc;
        ofField(arg0: $DirectMethodHandleDesc$Kind, arg1: $ClassDesc, arg2: string, arg3: $ClassDesc): $DirectMethodHandleDesc;
        ofInternalName(arg0: string): $ClassDesc;
        ofMethod(arg0: $DirectMethodHandleDesc$Kind, arg1: $ClassDesc, arg2: string, arg3: $MethodTypeDesc): $DirectMethodHandleDesc;
        ordinal(): number;
        owner(): $ClassDesc;
        packageName(): string;
        parameterArray(): $ClassDesc[];
        parameterArray(): $TypeDescriptor$OfField[];
        parameterCount(): number;
        parameterList(): $List<$ClassDesc>;
        parameterType(arg0: number): $ClassDesc;
        parameterType(arg0: number): $TypeDescriptor$OfField;
        refKind(): number;
        resolveConstantDesc(arg0: $MethodHandles$Lookup): $Class<any>;
        resolveConstantDesc(arg0: $MethodHandles$Lookup): $MethodHandle;
        resolveConstantDesc(arg0: $MethodHandles$Lookup): $MethodType;
        resolveConstantDesc(arg0: $MethodHandles$Lookup): T;
        resolveConstantDesc(arg0: $MethodHandles$Lookup): object;
        resolveConstantDesc(arg0: $MethodHandles$Lookup): object;
        resolveConstantDesc(arg0: $MethodHandles$Lookup): object;
        resolveConstantDesc(arg0: $MethodHandles$Lookup): object;
        returnType(): $ClassDesc;
        returnType(): $TypeDescriptor$OfField;
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
        static of<T>(arg0: $DirectMethodHandleDesc): $DynamicConstantDesc<T>;
        static of<T>(arg0: $DirectMethodHandleDesc, arg1?: $ConstantDesc[]): $DynamicConstantDesc<T>;
        static ofCanonical<T>(arg0: $DirectMethodHandleDesc, arg1: string, arg2: $ClassDesc, arg3: $ConstantDesc[]): $ConstantDesc;
        static ofNamed<T>(arg0: $DirectMethodHandleDesc, arg1: string, arg2: $ClassDesc, arg3?: $ConstantDesc[]): $DynamicConstantDesc<T>;
        static valueOf(name: string): $DirectMethodHandleDesc$Kind;
        static values(): $DirectMethodHandleDesc$Kind[];
        toString(): string;
        toString(): string;
    export class $DirectMethodHandleDesc$Kind {
    export class $DynamicConstantDesc<T> implements $ConstantDesc {
    export interface $ClassDesc extends $ConstantDesc, $TypeDescriptor$OfField {
    export interface $Constable {
    export interface $ConstantDesc {
    export interface $DirectMethodHandleDesc extends $MethodHandleDesc {
    export interface $MethodHandleDesc extends $ConstantDesc {
    export interface $MethodTypeDesc extends $ConstantDesc, $TypeDescriptor$OfMethod {
    }
    }
    }
    }
    }
    }
    }
    }
declare module "java:java/lang/constant" {
import { $Class, $Enum, $String } from "java:java/lang";
import { $List, $Optional } from "java:java/util";
import { $MethodHandle, $MethodHandles$Lookup, $MethodType, $TypeDescriptor$OfField, $TypeDescriptor$OfMethod } from "java:java/lang/invoke";
}