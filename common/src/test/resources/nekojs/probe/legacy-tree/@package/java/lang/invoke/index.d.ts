import { $Serializable } from "java:java/io";
import { $Class, $ClassLoader, $Enum } from "java:java/lang";
import { $ClassDesc, $Constable, $Constable_, $ConstantDesc, $ConstantDesc_, $DynamicConstantDesc, $MethodHandleDesc, $MethodTypeDesc } from "java:java/lang/constant";
import { $Constructor, $Field, $Member, $Method } from "java:java/lang/reflect";
import { $List, $Optional, $OptionalView } from "java:java/util";

declare module "java:java/lang/invoke" {
    export class $MethodHandle implements $Constable {
        get varargsCollector(): boolean;
        isVarargsCollector(): boolean;
        asCollector(arg0: number, arg1: $Class<any>, arg2: number): $MethodHandle;
        asCollector(arg0: $Class<any>, arg1: number): $MethodHandle;
        asFixedArity(): $MethodHandle;
        asSpreader(arg0: number, arg1: $Class<any>, arg2: number): $MethodHandle;
        asSpreader(arg0: $Class<any>, arg1: number): $MethodHandle;
        asType(arg0: $MethodType): $MethodHandle;
        asVarargsCollector(arg0: $Class<any>): $MethodHandle;
        bindTo(arg0: unknown): $MethodHandle;
        describeConstable(): $Optional<$MethodHandleDesc> & $OptionalView<(number | string | $ConstantDesc)>;
        invokeExact(arg0?: unknown[]): unknown;
        invokeWithArguments(arg0?: unknown[]): unknown;
        invokeWithArguments(arg0: ($List<any> | (any)[])): unknown;
        invoke(arg0?: unknown[]): unknown;
        toString(): string;
        type(): $MethodType;
        withVarargs(arg0: boolean): $MethodHandle;
    }

    export interface $MethodHandleInfo {
        getDeclaringClass(): $Class<any>;
        getMethodType(): $MethodType;
        getModifiers(): number;
        getName(): string;
        getReferenceKind(): number;
        isVarArgs(): boolean;
        reflectAs<T extends $Member>(arg0: $Class<T>, arg1: $MethodHandles$Lookup): T;
    }
    export const $MethodHandleInfo: {
        referenceKindToString(arg0: number): string;
        toString(arg0: number, arg1: $Class<any>, arg2: string, arg3: $MethodType): string;
        readonly REF_getField: number;
        readonly REF_getStatic: number;
        readonly REF_invokeInterface: number;
        readonly REF_invokeSpecial: number;
        readonly REF_invokeStatic: number;
        readonly REF_invokeVirtual: number;
        readonly REF_newInvokeSpecial: number;
        readonly REF_putField: number;
        readonly REF_putStatic: number;
    };

    export class $MethodHandles$Lookup {
        static MODULE: number;
        static ORIGINAL: number;
        static PACKAGE: number;
        static PRIVATE: number;
        static PROTECTED: number;
        static PUBLIC: number;
        static UNCONDITIONAL: number;
        accessClass<T>(arg0: $Class<T>): $Class<T>;
        bind(arg0: unknown, arg1: string, arg2: $MethodType): $MethodHandle;
        defineClass(arg0: number[]): $Class<any>;
        defineHiddenClassWithClassData(arg0: number[], arg1: unknown, arg2: boolean, arg3?: $MethodHandles$Lookup$ClassOption_[]): $MethodHandles$Lookup;
        defineHiddenClass(arg0: number[], arg1: boolean, arg2?: $MethodHandles$Lookup$ClassOption_[]): $MethodHandles$Lookup;
        dropLookupMode(arg0: number): $MethodHandles$Lookup;
        ensureInitialized<T>(arg0: $Class<T>): $Class<T>;
        findClass(arg0: string): $Class<any>;
        findConstructor(arg0: $Class<any>, arg1: $MethodType): $MethodHandle;
        findGetter(arg0: $Class<any>, arg1: string, arg2: $Class<any>): $MethodHandle;
        findSetter(arg0: $Class<any>, arg1: string, arg2: $Class<any>): $MethodHandle;
        findSpecial(arg0: $Class<any>, arg1: string, arg2: $MethodType, arg3: $Class<any>): $MethodHandle;
        findStaticGetter(arg0: $Class<any>, arg1: string, arg2: $Class<any>): $MethodHandle;
        findStaticSetter(arg0: $Class<any>, arg1: string, arg2: $Class<any>): $MethodHandle;
        findStaticVarHandle(arg0: $Class<any>, arg1: string, arg2: $Class<any>): $VarHandle;
        findStatic(arg0: $Class<any>, arg1: string, arg2: $MethodType): $MethodHandle;
        findVarHandle(arg0: $Class<any>, arg1: string, arg2: $Class<any>): $VarHandle;
        findVirtual(arg0: $Class<any>, arg1: string, arg2: $MethodType): $MethodHandle;
        hasFullPrivilegeAccess(): boolean;
        hasPrivateAccess(): boolean;
        in(arg0: $Class<any>): $MethodHandles$Lookup;
        lookupClass(): $Class<any>;
        lookupModes(): number;
        previousLookupClass(): $Class<any>;
        revealDirect(arg0: $MethodHandle): $MethodHandleInfo;
        toString(): string;
        unreflectConstructor(arg0: $Constructor<any>): $MethodHandle;
        unreflectGetter(arg0: $Field): $MethodHandle;
        unreflectSetter(arg0: $Field): $MethodHandle;
        unreflectSpecial(arg0: $Method, arg1: $Class<any>): $MethodHandle;
        unreflectVarHandle(arg0: $Field): $VarHandle;
        unreflect(arg0: $Method): $MethodHandle;
    }

    export class $MethodHandles$Lookup$ClassOption {
        static NESTMATE: $MethodHandles$Lookup$ClassOption;
        static STRONG: $MethodHandles$Lookup$ClassOption;
        name(): string;
        ordinal(): number;
        toString(): string;
        static values(): $MethodHandles$Lookup$ClassOption[];
        static valueOf(name: string): $MethodHandles$Lookup$ClassOption;
    }
    export interface $MethodHandles$Lookup$ClassOption extends $Enum<$MethodHandles$Lookup$ClassOption> {}

    export class $MethodType implements $Constable, $TypeDescriptor$OfMethod<$Class<any>, $MethodType>, $Serializable {
        static fromMethodDescriptorString(arg0: string, arg1: $ClassLoader): $MethodType;
        static genericMethodType(arg0: number, arg1: boolean): $MethodType;
        static genericMethodType(arg0: number): $MethodType;
        static methodType(arg0: $Class<any>, arg1: $Class<any>[]): $MethodType;
        static methodType(arg0: $Class<any>, arg1: $Class<any>, arg2?: $Class<any>[]): $MethodType;
        static methodType(arg0: $Class<any>, arg1: $Class<any>): $MethodType;
        static methodType(arg0: $Class<any>, arg1: $MethodType): $MethodType;
        static methodType(arg0: $Class<any>, arg1: ($List<$Class<any>> | ($Class<any>)[])): $MethodType;
        static methodType(arg0: $Class<any>): $MethodType;
        appendParameterTypes(arg0?: $Class<any>[]): $MethodType;
        appendParameterTypes(arg0: ($List<$Class<any>> | ($Class<any>)[])): $MethodType;
        changeParameterType(arg0: number, arg1: $Class<any>): $MethodType;
        changeReturnType(arg0: $Class<any>): $MethodType;
        describeConstable(): $Optional<$MethodTypeDesc> & $OptionalView<(number | string | $ConstantDesc)>;
        descriptorString(): string;
        dropParameterTypes(arg0: number, arg1: number): $MethodType;
        equals(arg0: unknown): boolean;
        erase(): $MethodType;
        generic(): $MethodType;
        hasPrimitives(): boolean;
        hasWrappers(): boolean;
        hashCode(): number;
        insertParameterTypes(arg0: number, arg1?: $Class<any>[]): $MethodType;
        insertParameterTypes(arg0: number, arg1: ($List<$Class<any>> | ($Class<any>)[])): $MethodType;
        lastParameterType(): $Class<any>;
        parameterArray(): $Class<any>[];
        parameterCount(): number;
        parameterList(): $List<$Class<any>>;
        parameterType(arg0: number): $Class<any>;
        returnType(): $Class<any>;
        toMethodDescriptorString(): string;
        toString(): string;
        unwrap(): $MethodType;
        wrap(): $MethodType;
    }

    export interface $TypeDescriptor {
        descriptorString(): string;
    }

    export interface $TypeDescriptor$OfField<F extends $TypeDescriptor$OfField<F>> extends $TypeDescriptor {
        arrayType(): F;
        componentType(): F;
        isArray(): boolean;
        isPrimitive(): boolean;
    }

    export interface $TypeDescriptor$OfMethod<F extends $TypeDescriptor$OfField<F>, M extends $TypeDescriptor$OfMethod<F, M>> extends $TypeDescriptor {
        changeParameterType(arg0: number, arg1: F): M;
        changeReturnType(arg0: F): M;
        dropParameterTypes(arg0: number, arg1: number): M;
        insertParameterTypes(arg0: number, arg1?: F[]): M;
        parameterArray(): F[];
        parameterCount(): number;
        parameterList(): $List<F>;
        parameterType(arg0: number): F;
        returnType(): F;
    }

    export class $VarHandle implements $Constable {
        static acquireFence(): void;
        static fullFence(): void;
        static loadLoadFence(): void;
        static releaseFence(): void;
        static storeStoreFence(): void;
        accessModeType(arg0: $VarHandle$AccessMode_): $MethodType;
        compareAndExchangeAcquire(arg0?: unknown[]): unknown;
        compareAndExchangeRelease(arg0?: unknown[]): unknown;
        compareAndExchange(arg0?: unknown[]): unknown;
        compareAndSet(arg0?: unknown[]): boolean;
        coordinateTypes(): $List<$Class<any>>;
        describeConstable(): $Optional<$VarHandle$VarHandleDesc> & $OptionalView<(number | string | $ConstantDesc)>;
        getAcquire(arg0?: unknown[]): unknown;
        getAndAddAcquire(arg0?: unknown[]): unknown;
        getAndAddRelease(arg0?: unknown[]): unknown;
        getAndAdd(arg0?: unknown[]): unknown;
        getAndBitwiseAndAcquire(arg0?: unknown[]): unknown;
        getAndBitwiseAndRelease(arg0?: unknown[]): unknown;
        getAndBitwiseAnd(arg0?: unknown[]): unknown;
        getAndBitwiseOrAcquire(arg0?: unknown[]): unknown;
        getAndBitwiseOrRelease(arg0?: unknown[]): unknown;
        getAndBitwiseOr(arg0?: unknown[]): unknown;
        getAndBitwiseXorAcquire(arg0?: unknown[]): unknown;
        getAndBitwiseXorRelease(arg0?: unknown[]): unknown;
        getAndBitwiseXor(arg0?: unknown[]): unknown;
        getAndSetAcquire(arg0?: unknown[]): unknown;
        getAndSetRelease(arg0?: unknown[]): unknown;
        getAndSet(arg0?: unknown[]): unknown;
        getOpaque(arg0?: unknown[]): unknown;
        getVolatile(arg0?: unknown[]): unknown;
        get(arg0?: unknown[]): unknown;
        hasInvokeExactBehavior(): boolean;
        isAccessModeSupported(arg0: $VarHandle$AccessMode_): boolean;
        setOpaque(arg0?: unknown[]): void;
        setRelease(arg0?: unknown[]): void;
        setVolatile(arg0?: unknown[]): void;
        set(arg0?: unknown[]): void;
        toMethodHandle(arg0: $VarHandle$AccessMode_): $MethodHandle;
        toString(): string;
        varType(): $Class<any>;
        weakCompareAndSetAcquire(arg0?: unknown[]): boolean;
        weakCompareAndSetPlain(arg0?: unknown[]): boolean;
        weakCompareAndSetRelease(arg0?: unknown[]): boolean;
        weakCompareAndSet(arg0?: unknown[]): boolean;
        withInvokeBehavior(): $VarHandle;
        withInvokeExactBehavior(): $VarHandle;
    }

    export class $VarHandle$AccessMode {
        static COMPARE_AND_EXCHANGE: $VarHandle$AccessMode;
        static COMPARE_AND_EXCHANGE_ACQUIRE: $VarHandle$AccessMode;
        static COMPARE_AND_EXCHANGE_RELEASE: $VarHandle$AccessMode;
        static COMPARE_AND_SET: $VarHandle$AccessMode;
        static GET: $VarHandle$AccessMode;
        static GET_ACQUIRE: $VarHandle$AccessMode;
        static GET_AND_ADD: $VarHandle$AccessMode;
        static GET_AND_ADD_ACQUIRE: $VarHandle$AccessMode;
        static GET_AND_ADD_RELEASE: $VarHandle$AccessMode;
        static GET_AND_BITWISE_AND: $VarHandle$AccessMode;
        static GET_AND_BITWISE_AND_ACQUIRE: $VarHandle$AccessMode;
        static GET_AND_BITWISE_AND_RELEASE: $VarHandle$AccessMode;
        static GET_AND_BITWISE_OR: $VarHandle$AccessMode;
        static GET_AND_BITWISE_OR_ACQUIRE: $VarHandle$AccessMode;
        static GET_AND_BITWISE_OR_RELEASE: $VarHandle$AccessMode;
        static GET_AND_BITWISE_XOR: $VarHandle$AccessMode;
        static GET_AND_BITWISE_XOR_ACQUIRE: $VarHandle$AccessMode;
        static GET_AND_BITWISE_XOR_RELEASE: $VarHandle$AccessMode;
        static GET_AND_SET: $VarHandle$AccessMode;
        static GET_AND_SET_ACQUIRE: $VarHandle$AccessMode;
        static GET_AND_SET_RELEASE: $VarHandle$AccessMode;
        static GET_OPAQUE: $VarHandle$AccessMode;
        static GET_VOLATILE: $VarHandle$AccessMode;
        static SET: $VarHandle$AccessMode;
        static SET_OPAQUE: $VarHandle$AccessMode;
        static SET_RELEASE: $VarHandle$AccessMode;
        static SET_VOLATILE: $VarHandle$AccessMode;
        static WEAK_COMPARE_AND_SET: $VarHandle$AccessMode;
        static WEAK_COMPARE_AND_SET_ACQUIRE: $VarHandle$AccessMode;
        static WEAK_COMPARE_AND_SET_PLAIN: $VarHandle$AccessMode;
        static WEAK_COMPARE_AND_SET_RELEASE: $VarHandle$AccessMode;
        name(): string;
        ordinal(): number;
        toString(): string;
        static values(): $VarHandle$AccessMode[];
        static valueOf(name: string): $VarHandle$AccessMode;
    }
    export interface $VarHandle$AccessMode extends $Enum<$VarHandle$AccessMode> {}

    export class $VarHandle$VarHandleDesc extends $DynamicConstantDesc<$VarHandle> {
        static ofArray(arg0: $ClassDesc): $VarHandle$VarHandleDesc;
        static ofField(arg0: $ClassDesc, arg1: string, arg2: $ClassDesc): $VarHandle$VarHandleDesc;
        static ofStaticField(arg0: $ClassDesc, arg1: string, arg2: $ClassDesc): $VarHandle$VarHandleDesc;
        resolveConstantDesc(arg0: $MethodHandles$Lookup): $VarHandle;
        toString(): string;
        varType(): $ClassDesc;
    }

    export type $MethodHandles$Lookup$ClassOption_ = $MethodHandles$Lookup$ClassOption | "NESTMATE" | "STRONG";
    export type $TypeDescriptor_<CallbackResult = string> = (() => CallbackResult) | ($TypeDescriptor & { readonly [Symbol.hasInstance]?: never }) | (globalThis.NoInfer<{ readonly "descriptorString": () => CallbackResult } & { readonly [Symbol.hasInstance]?: never }>);
    export type $VarHandle$AccessMode_ = $VarHandle$AccessMode | "COMPARE_AND_EXCHANGE" | "COMPARE_AND_EXCHANGE_ACQUIRE" | "COMPARE_AND_EXCHANGE_RELEASE" | "COMPARE_AND_SET" | "GET" | "GET_ACQUIRE" | "GET_AND_ADD" | "GET_AND_ADD_ACQUIRE" | "GET_AND_ADD_RELEASE" | "GET_AND_BITWISE_AND" | "GET_AND_BITWISE_AND_ACQUIRE" | "GET_AND_BITWISE_AND_RELEASE" | "GET_AND_BITWISE_OR" | "GET_AND_BITWISE_OR_ACQUIRE" | "GET_AND_BITWISE_OR_RELEASE" | "GET_AND_BITWISE_XOR" | "GET_AND_BITWISE_XOR_ACQUIRE" | "GET_AND_BITWISE_XOR_RELEASE" | "GET_AND_SET" | "GET_AND_SET_ACQUIRE" | "GET_AND_SET_RELEASE" | "GET_OPAQUE" | "GET_VOLATILE" | "SET" | "SET_OPAQUE" | "SET_RELEASE" | "SET_VOLATILE" | "WEAK_COMPARE_AND_SET" | "WEAK_COMPARE_AND_SET_ACQUIRE" | "WEAK_COMPARE_AND_SET_PLAIN" | "WEAK_COMPARE_AND_SET_RELEASE";
}
