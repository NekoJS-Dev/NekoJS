










        accessClass<T>(arg0: $Class<T>): $Class<T>;
        accessModeType(arg0: $VarHandle$AccessMode): $MethodType;
        appendParameterTypes(arg0: $Class<any>[]): $MethodType;
        appendParameterTypes(arg0?: $Class[]): $MethodType;
        arrayType(): F;
        asCollector(arg0: $Class<any>, arg1: number): $MethodHandle;
        asCollector(arg0: number, arg1: $Class<any>, arg2: number): $MethodHandle;
        asFixedArity(): $MethodHandle;
        asSpreader(arg0: $Class<any>, arg1: number): $MethodHandle;
        asSpreader(arg0: number, arg1: $Class<any>, arg2: number): $MethodHandle;
        asType(arg0: $MethodType): $MethodHandle;
        asVarargsCollector(arg0: $Class<any>): $MethodHandle;
        bind(arg0: object, arg1: string, arg2: $MethodType): $MethodHandle;
        bindTo(arg0: object): $MethodHandle;
        changeParameterType(arg0: number, arg1: $Class<any>): $MethodType;
        changeParameterType(arg0: number, arg1: $TypeDescriptor$OfField): $TypeDescriptor$OfMethod;
        changeParameterType(arg0: number, arg1: F): M;
        changeReturnType(arg0: $Class<any>): $MethodType;
        changeReturnType(arg0: $TypeDescriptor$OfField): $TypeDescriptor$OfMethod;
        changeReturnType(arg0: F): M;
        compareAndExchange(arg0?: object[]): object;
        compareAndExchangeAcquire(arg0?: object[]): object;
        compareAndExchangeRelease(arg0?: object[]): object;
        compareAndSet(arg0?: object[]): boolean;
        componentType(): F;
        coordinateTypes(): $List<$Class<any>>;
        defineClass(arg0: number[]): $Class<any>;
        defineHiddenClass(arg0: number[], arg1: boolean, arg2?: $MethodHandles$Lookup$ClassOption[]): $MethodHandles$Lookup;
        defineHiddenClassWithClassData(arg0: number[], arg1: object, arg2: boolean, arg3?: $MethodHandles$Lookup$ClassOption[]): $MethodHandles$Lookup;
        describeConstable(): $Optional<$MethodHandleDesc>;
        describeConstable(): $Optional<$MethodTypeDesc>;
        describeConstable(): $Optional<$VarHandle$VarHandleDesc>;
        descriptorString(): string;
        descriptorString(): string;
        dropLookupMode(arg0: number): $MethodHandles$Lookup;
        dropParameterTypes(arg0: number, arg1: number): $MethodType;
        dropParameterTypes(arg0: number, arg1: number): $TypeDescriptor$OfMethod;
        dropParameterTypes(arg0: number, arg1: number): M;
        ensureInitialized<T>(arg0: $Class<T>): $Class<T>;
        equals(arg0: object): boolean;
        erase(): $MethodType;
        findClass(arg0: string): $Class<any>;
        findConstructor(arg0: $Class<any>, arg1: $MethodType): $MethodHandle;
        findGetter(arg0: $Class<any>, arg1: string, arg2: $Class<any>): $MethodHandle;
        findSetter(arg0: $Class<any>, arg1: string, arg2: $Class<any>): $MethodHandle;
        findSpecial(arg0: $Class<any>, arg1: string, arg2: $MethodType, arg3: $Class<any>): $MethodHandle;
        findStatic(arg0: $Class<any>, arg1: string, arg2: $MethodType): $MethodHandle;
        findStaticGetter(arg0: $Class<any>, arg1: string, arg2: $Class<any>): $MethodHandle;
        findStaticSetter(arg0: $Class<any>, arg1: string, arg2: $Class<any>): $MethodHandle;
        findStaticVarHandle(arg0: $Class<any>, arg1: string, arg2: $Class<any>): $VarHandle;
        findVarHandle(arg0: $Class<any>, arg1: string, arg2: $Class<any>): $VarHandle;
        findVirtual(arg0: $Class<any>, arg1: string, arg2: $MethodType): $MethodHandle;
        generic(): $MethodType;
        get varargsCollector(): boolean;
        get(arg0?: object[]): object;
        getAcquire(arg0?: object[]): object;
        getAndAdd(arg0?: object[]): object;
        getAndAddAcquire(arg0?: object[]): object;
        getAndAddRelease(arg0?: object[]): object;
        getAndBitwiseAnd(arg0?: object[]): object;
        getAndBitwiseAndAcquire(arg0?: object[]): object;
        getAndBitwiseAndRelease(arg0?: object[]): object;
        getAndBitwiseOr(arg0?: object[]): object;
        getAndBitwiseOrAcquire(arg0?: object[]): object;
        getAndBitwiseOrRelease(arg0?: object[]): object;
        getAndBitwiseXor(arg0?: object[]): object;
        getAndBitwiseXorAcquire(arg0?: object[]): object;
        getAndBitwiseXorRelease(arg0?: object[]): object;
        getAndSet(arg0?: object[]): object;
        getAndSetAcquire(arg0?: object[]): object;
        getAndSetRelease(arg0?: object[]): object;
        getDeclaringClass(): $Class<any>;
        getMethodType(): $MethodType;
        getModifiers(): number;
        getName(): string;
        getOpaque(arg0?: object[]): object;
        getReferenceKind(): number;
        getVolatile(arg0?: object[]): object;
        hasFullPrivilegeAccess(): boolean;
        hasInvokeExactBehavior(): boolean;
        hasPrimitives(): boolean;
        hasPrivateAccess(): boolean;
        hasWrappers(): boolean;
        hashCode(): number;
        in(arg0: $Class<any>): $MethodHandles$Lookup;
        insertParameterTypes(arg0: number, arg1: $Class<any>[]): $MethodType;
        insertParameterTypes(arg0: number, arg1: $TypeDescriptor$OfField[]): $TypeDescriptor$OfMethod;
        insertParameterTypes(arg0: number, arg1?: $Class[]): $MethodType;
        insertParameterTypes(arg0: number, arg1?: $TypeDescriptor$OfField[]): M;
        invoke(arg0?: object[]): object;
        invokeExact(arg0?: object[]): object;
        invokeWithArguments(arg0: any[]): object;
        invokeWithArguments(arg0?: object[]): object;
        isAccessModeSupported(arg0: $VarHandle$AccessMode): boolean;
        isArray(): boolean;
        isPrimitive(): boolean;
        isVarArgs(): boolean;
        isVarargsCollector(): boolean;
        lastParameterType(): $Class<any>;
        lookupClass(): $Class<any>;
        lookupModes(): number;
        name(): string;
        ordinal(): number;
        parameterArray(): $Class<any>[];
        parameterArray(): $TypeDescriptor$OfField[];
        parameterArray(): F[];
        parameterCount(): number;
        parameterCount(): number;
        parameterList(): $List<$Class<any>>;
        parameterList(): $List<F>;
        parameterType(arg0: number): $Class<any>;
        parameterType(arg0: number): $TypeDescriptor$OfField;
        parameterType(arg0: number): F;
        previousLookupClass(): $Class<any>;
        referenceKindToString(arg0: number): string;
        reflectAs<T>(arg0: $Class<T>, arg1: $MethodHandles$Lookup): T;
        resolveConstantDesc(arg0: $MethodHandles$Lookup): $VarHandle;
        resolveConstantDesc(arg0: $MethodHandles$Lookup): object;
        returnType(): $Class<any>;
        returnType(): $TypeDescriptor$OfField;
        returnType(): F;
        revealDirect(arg0: $MethodHandle): $MethodHandleInfo;
        set(arg0?: object[]): void;
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
        static MODULE: number;
        static ORIGINAL: number;
        static PACKAGE: number;
        static PRIVATE: number;
        static PROTECTED: number;
        static PUBLIC: number;
        static REF_getField: number;
        static REF_getStatic: number;
        static REF_invokeInterface: number;
        static REF_invokeSpecial: number;
        static REF_invokeStatic: number;
        static REF_invokeVirtual: number;
        static REF_newInvokeSpecial: number;
        static REF_putField: number;
        static REF_putStatic: number;
        static SET: $VarHandle$AccessMode;
        static SET_OPAQUE: $VarHandle$AccessMode;
        static SET_RELEASE: $VarHandle$AccessMode;
        static SET_VOLATILE: $VarHandle$AccessMode;
        static UNCONDITIONAL: number;
        static WEAK_COMPARE_AND_SET: $VarHandle$AccessMode;
        static WEAK_COMPARE_AND_SET_ACQUIRE: $VarHandle$AccessMode;
        static WEAK_COMPARE_AND_SET_PLAIN: $VarHandle$AccessMode;
        static WEAK_COMPARE_AND_SET_RELEASE: $VarHandle$AccessMode;
        static acquireFence(): void;
        static fromMethodDescriptorString(arg0: string, arg1: $ClassLoader): $MethodType;
        static fullFence(): void;
        static genericMethodType(arg0: number): $MethodType;
        static genericMethodType(arg0: number, arg1: boolean): $MethodType;
        static loadLoadFence(): void;
        static methodType(arg0: $Class<any>): $MethodType;
        static methodType(arg0: $Class<any>, arg1: $Class<any>): $MethodType;
        static methodType(arg0: $Class<any>, arg1: $Class<any>, arg2?: $Class[]): $MethodType;
        static methodType(arg0: $Class<any>, arg1: $Class<any>[]): $MethodType;
        static methodType(arg0: $Class<any>, arg1: $Class<any>[]): $MethodType;
        static methodType(arg0: $Class<any>, arg1: $MethodType): $MethodType;
        static ofArray(arg0: $ClassDesc): $VarHandle$VarHandleDesc;
        static ofField(arg0: $ClassDesc, arg1: string, arg2: $ClassDesc): $VarHandle$VarHandleDesc;
        static ofStaticField(arg0: $ClassDesc, arg1: string, arg2: $ClassDesc): $VarHandle$VarHandleDesc;
        static releaseFence(): void;
        static storeStoreFence(): void;
        static valueOf(name: string): $VarHandle$AccessMode;
        static values(): $VarHandle$AccessMode[];
        toMethodDescriptorString(): string;
        toMethodHandle(arg0: $VarHandle$AccessMode): $MethodHandle;
        toString(): string;
        toString(): string;
        toString(): string;
        toString(): string;
        toString(): string;
        toString(): string;
        toString(arg0: number, arg1: $Class<any>, arg2: string, arg3: $MethodType): string;
        type(): $MethodType;
        unreflect(arg0: $Method): $MethodHandle;
        unreflectConstructor(arg0: $Constructor<any>): $MethodHandle;
        unreflectGetter(arg0: $Field): $MethodHandle;
        unreflectSetter(arg0: $Field): $MethodHandle;
        unreflectSpecial(arg0: $Method, arg1: $Class<any>): $MethodHandle;
        unreflectVarHandle(arg0: $Field): $VarHandle;
        unwrap(): $MethodType;
        varType(): $Class<any>;
        varType(): $ClassDesc;
        weakCompareAndSet(arg0?: object[]): boolean;
        weakCompareAndSetAcquire(arg0?: object[]): boolean;
        weakCompareAndSetPlain(arg0?: object[]): boolean;
        weakCompareAndSetRelease(arg0?: object[]): boolean;
        withInvokeBehavior(): $VarHandle;
        withInvokeExactBehavior(): $VarHandle;
        withVarargs(arg0: boolean): $MethodHandle;
        wrap(): $MethodType;
    export class $MethodHandle implements $Constable {
    export class $MethodHandles$Lookup {
    export class $MethodType implements $Constable, $TypeDescriptor$OfMethod, $Serializable {
    export class $VarHandle implements $Constable {
    export class $VarHandle$AccessMode {
    export class $VarHandle$VarHandleDesc extends $DynamicConstantDesc {
    export interface $MethodHandleInfo {
    export interface $TypeDescriptor {
    export interface $TypeDescriptor$OfField<F extends $TypeDescriptor$OfField<F>> extends $TypeDescriptor {
    export interface $TypeDescriptor$OfMethod<F extends $TypeDescriptor$OfField<F>, M extends $TypeDescriptor$OfMethod<F, M>> extends $TypeDescriptor {
    }
    }
    }
    }
    }
    }
    }
    }
    }
    }
declare module "java:java/lang/invoke" {
import { $Class, $ClassLoader, $Enum, $String } from "java:java/lang";
import { $ClassDesc, $Constable, $DynamicConstantDesc, $MethodHandleDesc, $MethodTypeDesc } from "java:java/lang/constant";
import { $Constructor, $Field, $Method } from "java:java/lang/reflect";
import { $List, $Optional } from "java:java/util";
import { $Serializable } from "java:java/io";
}