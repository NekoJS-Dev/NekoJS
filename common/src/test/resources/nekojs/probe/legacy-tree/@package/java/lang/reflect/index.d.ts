import { $Class, $Enum, $Runtime$Version, $String } from "java:java/lang";
import { $Annotation } from "java:java/lang/annotation";
import { $Set } from "java:java/util";

declare module "java:java/lang/reflect" {
    export class $AccessFlag {
        static ABSTRACT: $AccessFlag;
        static ANNOTATION: $AccessFlag;
        static BRIDGE: $AccessFlag;
        static ENUM: $AccessFlag;
        static FINAL: $AccessFlag;
        static INTERFACE: $AccessFlag;
        static MANDATED: $AccessFlag;
        static MODULE: $AccessFlag;
        static NATIVE: $AccessFlag;
        static OPEN: $AccessFlag;
        static PRIVATE: $AccessFlag;
        static PROTECTED: $AccessFlag;
        static PUBLIC: $AccessFlag;
        static STATIC: $AccessFlag;
        static STATIC_PHASE: $AccessFlag;
        static STRICT: $AccessFlag;
        static SUPER: $AccessFlag;
        static SYNCHRONIZED: $AccessFlag;
        static SYNTHETIC: $AccessFlag;
        static TRANSIENT: $AccessFlag;
        static TRANSITIVE: $AccessFlag;
        static VARARGS: $AccessFlag;
        static VOLATILE: $AccessFlag;
        name(): string;
        ordinal(): number;
        toString(): string;
        static values(): $AccessFlag[];
        static valueOf(name: string): $AccessFlag;
    }

    export class $AccessFlag$Location {
        static CLASS: $AccessFlag$Location;
        static FIELD: $AccessFlag$Location;
        static INNER_CLASS: $AccessFlag$Location;
        static METHOD: $AccessFlag$Location;
        static METHOD_PARAMETER: $AccessFlag$Location;
        static MODULE: $AccessFlag$Location;
        static MODULE_EXPORTS: $AccessFlag$Location;
        static MODULE_OPENS: $AccessFlag$Location;
        static MODULE_REQUIRES: $AccessFlag$Location;
        name(): string;
        ordinal(): number;
        toString(): string;
        static values(): $AccessFlag$Location[];
        static valueOf(name: string): $AccessFlag$Location;
    }

    export class $AccessibleObject implements $AnnotatedElement {
        get annotations(): $Annotation[];
        getAnnotations(): $Annotation[];
        get declaredAnnotations(): $Annotation[];
        getDeclaredAnnotations(): $Annotation[];
        get accessible(): boolean;
        isAccessible(): boolean;
        set accessible(value: boolean);
        static setAccessible(arg0: $AccessibleObject[], arg1: boolean): void;
        canAccess(arg0: object): boolean;
        getAnnotationsByType<T>(arg0: $Class<T>): T[];
        getAnnotation<T>(arg0: $Class<T>): T;
        getDeclaredAnnotationsByType<T>(arg0: $Class<T>): T[];
        getDeclaredAnnotation<T>(arg0: $Class<T>): T;
        isAnnotationPresent(arg0: $Class<$Annotation>): boolean;
        trySetAccessible(): boolean;
    }

    export interface $AnnotatedElement {
        getAnnotationsByType<T>(arg0: $Class<T>): T[];
        getAnnotations(): $Annotation[];
        getAnnotation<T>(arg0: $Class<T>): T;
        getDeclaredAnnotationsByType<T>(arg0: $Class<T>): T[];
        getDeclaredAnnotations(): $Annotation[];
        getDeclaredAnnotation<T>(arg0: $Class<T>): T;
        isAnnotationPresent(arg0: $Class<$Annotation>): boolean;
    }

    export interface $AnnotatedType extends $AnnotatedElement {
        getAnnotatedOwnerType(): $AnnotatedType;
        getAnnotations(): $Annotation[];
        getAnnotation<T>(arg0: $Class<T>): T;
        getDeclaredAnnotations(): $Annotation[];
        getType(): $Type;
    }

    export class $ClassFileFormatVersion {
        static RELEASE_0: $ClassFileFormatVersion;
        static RELEASE_1: $ClassFileFormatVersion;
        static RELEASE_10: $ClassFileFormatVersion;
        static RELEASE_11: $ClassFileFormatVersion;
        static RELEASE_12: $ClassFileFormatVersion;
        static RELEASE_13: $ClassFileFormatVersion;
        static RELEASE_14: $ClassFileFormatVersion;
        static RELEASE_15: $ClassFileFormatVersion;
        static RELEASE_16: $ClassFileFormatVersion;
        static RELEASE_17: $ClassFileFormatVersion;
        static RELEASE_18: $ClassFileFormatVersion;
        static RELEASE_19: $ClassFileFormatVersion;
        static RELEASE_2: $ClassFileFormatVersion;
        static RELEASE_20: $ClassFileFormatVersion;
        static RELEASE_21: $ClassFileFormatVersion;
        static RELEASE_3: $ClassFileFormatVersion;
        static RELEASE_4: $ClassFileFormatVersion;
        static RELEASE_5: $ClassFileFormatVersion;
        static RELEASE_6: $ClassFileFormatVersion;
        static RELEASE_7: $ClassFileFormatVersion;
        static RELEASE_8: $ClassFileFormatVersion;
        static RELEASE_9: $ClassFileFormatVersion;
        name(): string;
        ordinal(): number;
        toString(): string;
        static values(): $ClassFileFormatVersion[];
        static valueOf(name: string): $ClassFileFormatVersion;
    }

    export class $Constructor<T> extends $Executable {
        get annotatedReceiverType(): $AnnotatedType;
        getAnnotatedReceiverType(): $AnnotatedType;
        get annotatedReturnType(): $AnnotatedType;
        getAnnotatedReturnType(): $AnnotatedType;
        get declaredAnnotations(): $Annotation[];
        getDeclaredAnnotations(): $Annotation[];
        get declaringClass(): $Class<T>;
        getDeclaringClass(): $Class<T>;
        get exceptionTypes(): $Class<any>[];
        getExceptionTypes(): $Class<any>[];
        get genericExceptionTypes(): $Type[];
        getGenericExceptionTypes(): $Type[];
        get genericParameterTypes(): $Type[];
        getGenericParameterTypes(): $Type[];
        get modifiers(): number;
        getModifiers(): number;
        get name(): string;
        getName(): string;
        get parameterAnnotations(): $Annotation[][];
        getParameterAnnotations(): $Annotation[][];
        get parameterCount(): number;
        getParameterCount(): number;
        get parameterTypes(): $Class<any>[];
        getParameterTypes(): $Class<any>[];
        get typeParameters(): $TypeVariable<$Constructor<T>>[];
        getTypeParameters(): $TypeVariable<$Constructor<T>>[];
        get synthetic(): boolean;
        isSynthetic(): boolean;
        get varArgs(): boolean;
        isVarArgs(): boolean;
        equals(arg0: object): boolean;
        getAnnotation<T>(arg0: $Class<T>): T;
        hashCode(): number;
        newInstance(arg0?: object[]): T;
        toGenericString(): string;
        toString(): string;
    }

    export class $Executable extends $AccessibleObject implements $Member, $GenericDeclaration {
        get annotatedExceptionTypes(): $AnnotatedType[];
        getAnnotatedExceptionTypes(): $AnnotatedType[];
        get annotatedParameterTypes(): $AnnotatedType[];
        getAnnotatedParameterTypes(): $AnnotatedType[];
        get annotatedReceiverType(): $AnnotatedType;
        getAnnotatedReceiverType(): $AnnotatedType;
        get annotatedReturnType(): $AnnotatedType;
        getAnnotatedReturnType(): $AnnotatedType;
        get declaredAnnotations(): $Annotation[];
        getDeclaredAnnotations(): $Annotation[];
        get declaringClass(): $Class<any>;
        getDeclaringClass(): $Class<any>;
        get exceptionTypes(): $Class<any>[];
        getExceptionTypes(): $Class<any>[];
        get genericExceptionTypes(): $Type[];
        getGenericExceptionTypes(): $Type[];
        get genericParameterTypes(): $Type[];
        getGenericParameterTypes(): $Type[];
        get modifiers(): number;
        getModifiers(): number;
        get name(): string;
        getName(): string;
        get parameterAnnotations(): $Annotation[][];
        getParameterAnnotations(): $Annotation[][];
        get parameterCount(): number;
        getParameterCount(): number;
        get parameterTypes(): $Class<any>[];
        getParameterTypes(): $Class<any>[];
        get parameters(): $Parameter[];
        getParameters(): $Parameter[];
        get typeParameters(): $TypeVariable<any>[];
        getTypeParameters(): $TypeVariable<any>[];
        get synthetic(): boolean;
        isSynthetic(): boolean;
        get varArgs(): boolean;
        isVarArgs(): boolean;
        accessFlags(): $Set<$AccessFlag>;
        getAnnotationsByType<T>(arg0: $Class<T>): T[];
        getAnnotation<T>(arg0: $Class<T>): T;
        toGenericString(): string;
    }

    export class $Field extends $AccessibleObject implements $Member {
        get annotatedType(): $AnnotatedType;
        getAnnotatedType(): $AnnotatedType;
        get declaredAnnotations(): $Annotation[];
        getDeclaredAnnotations(): $Annotation[];
        get declaringClass(): $Class<any>;
        getDeclaringClass(): $Class<any>;
        get genericType(): $Type;
        getGenericType(): $Type;
        get modifiers(): number;
        getModifiers(): number;
        get name(): string;
        getName(): string;
        get type(): $Class<any>;
        getType(): $Class<any>;
        get enumConstant(): boolean;
        isEnumConstant(): boolean;
        get synthetic(): boolean;
        isSynthetic(): boolean;
        accessFlags(): $Set<$AccessFlag>;
        equals(arg0: object): boolean;
        getAnnotationsByType<T>(arg0: $Class<T>): T[];
        getAnnotation<T>(arg0: $Class<T>): T;
        getBoolean(arg0: object): boolean;
        getByte(arg0: object): number;
        getChar(arg0: object): string;
        getDouble(arg0: object): number;
        getFloat(arg0: object): number;
        getInt(arg0: object): number;
        getLong(arg0: object): number;
        getShort(arg0: object): number;
        get(arg0: object): object;
        hashCode(): number;
        setBoolean(arg0: object, arg1: boolean): void;
        setByte(arg0: object, arg1: number): void;
        setChar(arg0: object, arg1: string): void;
        setDouble(arg0: object, arg1: number): void;
        setFloat(arg0: object, arg1: number): void;
        setInt(arg0: object, arg1: number): void;
        setLong(arg0: object, arg1: number): void;
        setShort(arg0: object, arg1: number): void;
        set(arg0: object, arg1: object): void;
        toGenericString(): string;
        toString(): string;
    }

    export interface $GenericDeclaration extends $AnnotatedElement {
        getTypeParameters(): $TypeVariable<any>[];
    }

    export interface $Member {
        accessFlags(): $Set<$AccessFlag>;
        getDeclaringClass(): $Class<any>;
        getModifiers(): number;
        getName(): string;
        isSynthetic(): boolean;
        static DECLARED: number;
        static PUBLIC: number;
    }

    export class $Method extends $Executable {
        get annotatedReturnType(): $AnnotatedType;
        getAnnotatedReturnType(): $AnnotatedType;
        get declaredAnnotations(): $Annotation[];
        getDeclaredAnnotations(): $Annotation[];
        get declaringClass(): $Class<any>;
        getDeclaringClass(): $Class<any>;
        get defaultValue(): object;
        getDefaultValue(): object;
        get exceptionTypes(): $Class<any>[];
        getExceptionTypes(): $Class<any>[];
        get genericExceptionTypes(): $Type[];
        getGenericExceptionTypes(): $Type[];
        get genericParameterTypes(): $Type[];
        getGenericParameterTypes(): $Type[];
        get genericReturnType(): $Type;
        getGenericReturnType(): $Type;
        get modifiers(): number;
        getModifiers(): number;
        get name(): string;
        getName(): string;
        get parameterAnnotations(): $Annotation[][];
        getParameterAnnotations(): $Annotation[][];
        get parameterCount(): number;
        getParameterCount(): number;
        get parameterTypes(): $Class<any>[];
        getParameterTypes(): $Class<any>[];
        get returnType(): $Class<any>;
        getReturnType(): $Class<any>;
        get typeParameters(): $TypeVariable<$Method>[];
        getTypeParameters(): $TypeVariable<$Method>[];
        get bridge(): boolean;
        isBridge(): boolean;
        get default(): boolean;
        isDefault(): boolean;
        get synthetic(): boolean;
        isSynthetic(): boolean;
        get varArgs(): boolean;
        isVarArgs(): boolean;
        equals(arg0: object): boolean;
        getAnnotation<T>(arg0: $Class<T>): T;
        hashCode(): number;
        invoke(arg0: object, arg1?: object[]): object;
        toGenericString(): string;
        toString(): string;
    }

    export interface $Type {
        getTypeName(): string;
    }

    export interface $TypeVariable<D extends $GenericDeclaration> extends $Type, $AnnotatedElement {
        getAnnotatedBounds(): $AnnotatedType[];
        getBounds(): $Type[];
        getGenericDeclaration(): D;
        getName(): string;
    }

    export type $AccessFlag_ = $AccessFlag | "ABSTRACT" | "ANNOTATION" | "BRIDGE" | "ENUM" | "FINAL" | "INTERFACE" | "MANDATED" | "MODULE" | "NATIVE" | "OPEN" | "PRIVATE" | "PROTECTED" | "PUBLIC" | "STATIC" | "STATIC_PHASE" | "STRICT" | "SUPER" | "SYNCHRONIZED" | "SYNTHETIC" | "TRANSIENT" | "TRANSITIVE" | "VARARGS" | "VOLATILE";
    export type $AccessFlag$Location_ = $AccessFlag$Location | "CLASS" | "FIELD" | "INNER_CLASS" | "METHOD" | "METHOD_PARAMETER" | "MODULE" | "MODULE_EXPORTS" | "MODULE_OPENS" | "MODULE_REQUIRES";
    export type $ClassFileFormatVersion_ = $ClassFileFormatVersion | "RELEASE_0" | "RELEASE_1" | "RELEASE_10" | "RELEASE_11" | "RELEASE_12" | "RELEASE_13" | "RELEASE_14" | "RELEASE_15" | "RELEASE_16" | "RELEASE_17" | "RELEASE_18" | "RELEASE_19" | "RELEASE_2" | "RELEASE_20" | "RELEASE_21" | "RELEASE_3" | "RELEASE_4" | "RELEASE_5" | "RELEASE_6" | "RELEASE_7" | "RELEASE_8" | "RELEASE_9";
}
