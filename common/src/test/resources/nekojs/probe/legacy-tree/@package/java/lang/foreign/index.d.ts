import { $AutoCloseable, $AutoCloseable_, $Class, $Thread } from "java:java/lang";
import { $MethodHandle, $VarHandle } from "java:java/lang/invoke";
import { $Buffer, $ByteBuffer, $ByteOrder } from "java:java/nio";
import { $List, $Optional, $Spliterator } from "java:java/util";
import { $Consumer, $Consumer_ } from "java:java/util/function";
import { $Stream } from "java:java/util/stream";

declare module "java:java/lang/foreign" {
    export interface $Arena extends $SegmentAllocator, $AutoCloseable {
        allocate(arg0: $AddressLayout, arg1: $MemorySegment): $MemorySegment;
        allocate(arg0: $MemoryLayout): $MemorySegment;
        allocate(arg0: $ValueLayout$OfByte, arg1: number): $MemorySegment;
        allocate(arg0: $ValueLayout$OfChar, arg1: string): $MemorySegment;
        allocate(arg0: $ValueLayout$OfDouble, arg1: number): $MemorySegment;
        allocate(arg0: $ValueLayout$OfFloat, arg1: number): $MemorySegment;
        allocate(arg0: $ValueLayout$OfInt, arg1: number): $MemorySegment;
        allocate(arg0: $ValueLayout$OfLong, arg1: number): $MemorySegment;
        allocate(arg0: $ValueLayout$OfShort, arg1: number): $MemorySegment;
        allocate(arg0: number, arg1: number): $MemorySegment;
        allocate(arg0: number): $MemorySegment;
        close(): void;
        scope(): $MemorySegment$Scope;
    }
    export const $Arena: {
        global(): $Arena;
        ofAuto(): $Arena;
        ofConfined(): $Arena;
        ofShared(): $Arena;
    };

    export interface $MemorySegment {
        address(): number;
        asByteBuffer(): $ByteBuffer;
        asOverlappingSlice(arg0: $MemorySegment): $Optional<$MemorySegment>;
        asReadOnly(): $MemorySegment;
        asSlice(arg0: number, arg1: $MemoryLayout): $MemorySegment;
        asSlice(arg0: number, arg1: number, arg2: number): $MemorySegment;
        asSlice(arg0: number, arg1: number): $MemorySegment;
        asSlice(arg0: number): $MemorySegment;
        byteSize(): number;
        copyFrom(arg0: $MemorySegment): $MemorySegment;
        elements(arg0: $MemoryLayout): $Stream<$MemorySegment>;
        equals(arg0: unknown): boolean;
        fill(arg0: number): $MemorySegment;
        force(): void;
        getAtIndex(arg0: $AddressLayout, arg1: number): $MemorySegment;
        getAtIndex(arg0: $ValueLayout$OfBoolean, arg1: number): boolean;
        getAtIndex(arg0: $ValueLayout$OfByte, arg1: number): number;
        getAtIndex(arg0: $ValueLayout$OfChar, arg1: number): string;
        getAtIndex(arg0: $ValueLayout$OfDouble, arg1: number): number;
        getAtIndex(arg0: $ValueLayout$OfFloat, arg1: number): number;
        getAtIndex(arg0: $ValueLayout$OfInt, arg1: number): number;
        getAtIndex(arg0: $ValueLayout$OfLong, arg1: number): number;
        getAtIndex(arg0: $ValueLayout$OfShort, arg1: number): number;
        getUtf8String(arg0: number): string;
        get(arg0: $AddressLayout, arg1: number): $MemorySegment;
        get(arg0: $ValueLayout$OfBoolean, arg1: number): boolean;
        get(arg0: $ValueLayout$OfByte, arg1: number): number;
        get(arg0: $ValueLayout$OfChar, arg1: number): string;
        get(arg0: $ValueLayout$OfDouble, arg1: number): number;
        get(arg0: $ValueLayout$OfFloat, arg1: number): number;
        get(arg0: $ValueLayout$OfInt, arg1: number): number;
        get(arg0: $ValueLayout$OfLong, arg1: number): number;
        get(arg0: $ValueLayout$OfShort, arg1: number): number;
        hashCode(): number;
        heapBase(): $Optional<unknown>;
        isAccessibleBy(arg0: $Thread): boolean;
        isLoaded(): boolean;
        isMapped(): boolean;
        isNative(): boolean;
        isReadOnly(): boolean;
        load(): void;
        mismatch(arg0: $MemorySegment): number;
        reinterpret(arg0: $Arena, arg1: $Consumer_<$MemorySegment, $MemorySegment>): $MemorySegment;
        reinterpret(arg0: number, arg1: $Arena, arg2: $Consumer_<$MemorySegment, $MemorySegment>): $MemorySegment;
        reinterpret(arg0: number): $MemorySegment;
        scope(): $MemorySegment$Scope;
        segmentOffset(arg0: $MemorySegment): number;
        setAtIndex(arg0: $AddressLayout, arg1: number, arg2: $MemorySegment): void;
        setAtIndex(arg0: $ValueLayout$OfBoolean, arg1: number, arg2: boolean): void;
        setAtIndex(arg0: $ValueLayout$OfByte, arg1: number, arg2: number): void;
        setAtIndex(arg0: $ValueLayout$OfChar, arg1: number, arg2: string): void;
        setAtIndex(arg0: $ValueLayout$OfDouble, arg1: number, arg2: number): void;
        setAtIndex(arg0: $ValueLayout$OfFloat, arg1: number, arg2: number): void;
        setAtIndex(arg0: $ValueLayout$OfInt, arg1: number, arg2: number): void;
        setAtIndex(arg0: $ValueLayout$OfLong, arg1: number, arg2: number): void;
        setAtIndex(arg0: $ValueLayout$OfShort, arg1: number, arg2: number): void;
        setUtf8String(arg0: number, arg1: string): void;
        set(arg0: $AddressLayout, arg1: number, arg2: $MemorySegment): void;
        set(arg0: $ValueLayout$OfBoolean, arg1: number, arg2: boolean): void;
        set(arg0: $ValueLayout$OfByte, arg1: number, arg2: number): void;
        set(arg0: $ValueLayout$OfChar, arg1: number, arg2: string): void;
        set(arg0: $ValueLayout$OfDouble, arg1: number, arg2: number): void;
        set(arg0: $ValueLayout$OfFloat, arg1: number, arg2: number): void;
        set(arg0: $ValueLayout$OfInt, arg1: number, arg2: number): void;
        set(arg0: $ValueLayout$OfLong, arg1: number, arg2: number): void;
        set(arg0: $ValueLayout$OfShort, arg1: number, arg2: number): void;
        spliterator(arg0: $MemoryLayout): $Spliterator<$MemorySegment>;
        toArray(arg0: $ValueLayout$OfByte): number[];
        toArray(arg0: $ValueLayout$OfChar): string[];
        toArray(arg0: $ValueLayout$OfDouble): number[];
        toArray(arg0: $ValueLayout$OfFloat): number[];
        toArray(arg0: $ValueLayout$OfInt): number[];
        toArray(arg0: $ValueLayout$OfLong): number[];
        toArray(arg0: $ValueLayout$OfShort): number[];
        unload(): void;
    }
    export const $MemorySegment: {
        copy(arg0: unknown, arg1: number, arg2: $MemorySegment, arg3: $ValueLayout, arg4: number, arg5: number): void;
        copy(arg0: $MemorySegment, arg1: $ValueLayout, arg2: number, arg3: unknown, arg4: number, arg5: number): void;
        copy(arg0: $MemorySegment, arg1: $ValueLayout, arg2: number, arg3: $MemorySegment, arg4: $ValueLayout, arg5: number, arg6: number): void;
        copy(arg0: $MemorySegment, arg1: number, arg2: $MemorySegment, arg3: number, arg4: number): void;
        mismatch(arg0: $MemorySegment, arg1: number, arg2: number, arg3: $MemorySegment, arg4: number, arg5: number): number;
        ofAddress(arg0: number): $MemorySegment;
        ofArray(arg0: number[]): $MemorySegment;
        ofArray(arg0: string[]): $MemorySegment;
        ofArray(arg0: number[]): $MemorySegment;
        ofArray(arg0: number[]): $MemorySegment;
        ofArray(arg0: number[]): $MemorySegment;
        ofArray(arg0: number[]): $MemorySegment;
        ofArray(arg0: number[]): $MemorySegment;
        ofBuffer(arg0: $Buffer): $MemorySegment;
        readonly NULL: $MemorySegment;
    };

    export interface $AddressLayout extends $ValueLayout {
        targetLayout(): $Optional<$MemoryLayout>;
        withByteAlignment(arg0: number): $AddressLayout;
        withName(arg0: string): $AddressLayout;
        withOrder(arg0: $ByteOrder): $AddressLayout;
        withTargetLayout(arg0: $MemoryLayout): $AddressLayout;
        withoutName(): $AddressLayout;
        withoutTargetLayout(): $AddressLayout;
    }

    export interface $MemoryLayout {
        byteAlignment(): number;
        byteOffsetHandle(arg0?: $MemoryLayout$PathElement[]): $MethodHandle;
        byteOffset(arg0?: $MemoryLayout$PathElement[]): number;
        byteSize(): number;
        equals(arg0: unknown): boolean;
        hashCode(): number;
        name(): $Optional<string>;
        select(arg0?: $MemoryLayout$PathElement[]): $MemoryLayout;
        sliceHandle(arg0?: $MemoryLayout$PathElement[]): $MethodHandle;
        toString(): string;
        varHandle(arg0?: $MemoryLayout$PathElement[]): $VarHandle;
        withByteAlignment(arg0: number): $MemoryLayout;
        withName(arg0: string): $MemoryLayout;
        withoutName(): $MemoryLayout;
    }
    export const $MemoryLayout: {
        paddingLayout(arg0: number): $PaddingLayout;
        sequenceLayout(arg0: $MemoryLayout): $SequenceLayout;
        sequenceLayout(arg0: number, arg1: $MemoryLayout): $SequenceLayout;
        structLayout(arg0?: $MemoryLayout[]): $StructLayout;
        unionLayout(arg0?: $MemoryLayout[]): $UnionLayout;
    };

    export interface $MemorySegment$Scope {
        equals(arg0: unknown): boolean;
        hashCode(): number;
        isAlive(): boolean;
    }

    export interface $SegmentAllocator {
        allocateArray(arg0: $MemoryLayout, arg1: number): $MemorySegment;
        allocateArray(arg0: $ValueLayout$OfByte, arg1?: number[]): $MemorySegment;
        allocateArray(arg0: $ValueLayout$OfChar, arg1?: string[]): $MemorySegment;
        allocateArray(arg0: $ValueLayout$OfDouble, arg1?: number[]): $MemorySegment;
        allocateArray(arg0: $ValueLayout$OfFloat, arg1?: number[]): $MemorySegment;
        allocateArray(arg0: $ValueLayout$OfInt, arg1?: number[]): $MemorySegment;
        allocateArray(arg0: $ValueLayout$OfLong, arg1?: number[]): $MemorySegment;
        allocateArray(arg0: $ValueLayout$OfShort, arg1?: number[]): $MemorySegment;
        allocateUtf8String(arg0: string): $MemorySegment;
        allocate(arg0: $AddressLayout, arg1: $MemorySegment): $MemorySegment;
        allocate(arg0: $MemoryLayout): $MemorySegment;
        allocate(arg0: $ValueLayout$OfByte, arg1: number): $MemorySegment;
        allocate(arg0: $ValueLayout$OfChar, arg1: string): $MemorySegment;
        allocate(arg0: $ValueLayout$OfDouble, arg1: number): $MemorySegment;
        allocate(arg0: $ValueLayout$OfFloat, arg1: number): $MemorySegment;
        allocate(arg0: $ValueLayout$OfInt, arg1: number): $MemorySegment;
        allocate(arg0: $ValueLayout$OfLong, arg1: number): $MemorySegment;
        allocate(arg0: $ValueLayout$OfShort, arg1: number): $MemorySegment;
        allocate(arg0: number, arg1: number): $MemorySegment;
        allocate(arg0: number): $MemorySegment;
    }
    export const $SegmentAllocator: {
        prefixAllocator(arg0: $MemorySegment): $SegmentAllocator;
        slicingAllocator(arg0: $MemorySegment): $SegmentAllocator;
    };

    export interface $ValueLayout$OfByte extends $ValueLayout {
        withByteAlignment(arg0: number): $ValueLayout$OfByte;
        withName(arg0: string): $ValueLayout$OfByte;
        withOrder(arg0: $ByteOrder): $ValueLayout$OfByte;
        withoutName(): $ValueLayout$OfByte;
    }

    export interface $ValueLayout$OfChar extends $ValueLayout {
        withByteAlignment(arg0: number): $ValueLayout$OfChar;
        withName(arg0: string): $ValueLayout$OfChar;
        withOrder(arg0: $ByteOrder): $ValueLayout$OfChar;
        withoutName(): $ValueLayout$OfChar;
    }

    export interface $ValueLayout$OfDouble extends $ValueLayout {
        withByteAlignment(arg0: number): $ValueLayout$OfDouble;
        withName(arg0: string): $ValueLayout$OfDouble;
        withOrder(arg0: $ByteOrder): $ValueLayout$OfDouble;
        withoutName(): $ValueLayout$OfDouble;
    }

    export interface $ValueLayout$OfFloat extends $ValueLayout {
        withByteAlignment(arg0: number): $ValueLayout$OfFloat;
        withName(arg0: string): $ValueLayout$OfFloat;
        withOrder(arg0: $ByteOrder): $ValueLayout$OfFloat;
        withoutName(): $ValueLayout$OfFloat;
    }

    export interface $ValueLayout$OfInt extends $ValueLayout {
        withByteAlignment(arg0: number): $ValueLayout$OfInt;
        withName(arg0: string): $ValueLayout$OfInt;
        withOrder(arg0: $ByteOrder): $ValueLayout$OfInt;
        withoutName(): $ValueLayout$OfInt;
    }

    export interface $ValueLayout$OfLong extends $ValueLayout {
        withByteAlignment(arg0: number): $ValueLayout$OfLong;
        withName(arg0: string): $ValueLayout$OfLong;
        withOrder(arg0: $ByteOrder): $ValueLayout$OfLong;
        withoutName(): $ValueLayout$OfLong;
    }

    export interface $ValueLayout$OfShort extends $ValueLayout {
        withByteAlignment(arg0: number): $ValueLayout$OfShort;
        withName(arg0: string): $ValueLayout$OfShort;
        withOrder(arg0: $ByteOrder): $ValueLayout$OfShort;
        withoutName(): $ValueLayout$OfShort;
    }

    export interface $ValueLayout extends $MemoryLayout {
        arrayElementVarHandle(arg0?: number[]): $VarHandle;
        carrier(): $Class<any>;
        order(): $ByteOrder;
        withByteAlignment(arg0: number): $ValueLayout;
        withName(arg0: string): $ValueLayout;
        withOrder(arg0: $ByteOrder): $ValueLayout;
        withoutName(): $ValueLayout;
    }
    export const $ValueLayout: {
        readonly ADDRESS: $AddressLayout;
        readonly ADDRESS_UNALIGNED: $AddressLayout;
        readonly JAVA_BOOLEAN: $ValueLayout$OfBoolean;
        readonly JAVA_BYTE: $ValueLayout$OfByte;
        readonly JAVA_CHAR: $ValueLayout$OfChar;
        readonly JAVA_CHAR_UNALIGNED: $ValueLayout$OfChar;
        readonly JAVA_DOUBLE: $ValueLayout$OfDouble;
        readonly JAVA_DOUBLE_UNALIGNED: $ValueLayout$OfDouble;
        readonly JAVA_FLOAT: $ValueLayout$OfFloat;
        readonly JAVA_FLOAT_UNALIGNED: $ValueLayout$OfFloat;
        readonly JAVA_INT: $ValueLayout$OfInt;
        readonly JAVA_INT_UNALIGNED: $ValueLayout$OfInt;
        readonly JAVA_LONG: $ValueLayout$OfLong;
        readonly JAVA_LONG_UNALIGNED: $ValueLayout$OfLong;
        readonly JAVA_SHORT: $ValueLayout$OfShort;
        readonly JAVA_SHORT_UNALIGNED: $ValueLayout$OfShort;
    };

    export interface $MemoryLayout$PathElement {
    }
    export const $MemoryLayout$PathElement: {
        dereferenceElement(): $MemoryLayout$PathElement;
        groupElement(arg0: string): $MemoryLayout$PathElement;
        groupElement(arg0: number): $MemoryLayout$PathElement;
        sequenceElement(arg0: number, arg1: number): $MemoryLayout$PathElement;
        sequenceElement(arg0: number): $MemoryLayout$PathElement;
        sequenceElement(): $MemoryLayout$PathElement;
    };

    export interface $PaddingLayout extends $MemoryLayout {
        withByteAlignment(arg0: number): $PaddingLayout;
        withName(arg0: string): $PaddingLayout;
        withoutName(): $PaddingLayout;
    }

    export interface $SequenceLayout extends $MemoryLayout {
        elementCount(): number;
        elementLayout(): $MemoryLayout;
        flatten(): $SequenceLayout;
        reshape(arg0?: number[]): $SequenceLayout;
        withByteAlignment(arg0: number): $SequenceLayout;
        withElementCount(arg0: number): $SequenceLayout;
        withName(arg0: string): $SequenceLayout;
        withoutName(): $MemoryLayout;
    }

    export interface $StructLayout extends $GroupLayout {
        withByteAlignment(arg0: number): $StructLayout;
        withName(arg0: string): $StructLayout;
        withoutName(): $StructLayout;
    }

    export interface $UnionLayout extends $GroupLayout {
        withByteAlignment(arg0: number): $UnionLayout;
        withName(arg0: string): $UnionLayout;
        withoutName(): $UnionLayout;
    }

    export interface $ValueLayout$OfBoolean extends $ValueLayout {
        withByteAlignment(arg0: number): $ValueLayout$OfBoolean;
        withName(arg0: string): $ValueLayout$OfBoolean;
        withOrder(arg0: $ByteOrder): $ValueLayout$OfBoolean;
        withoutName(): $ValueLayout$OfBoolean;
    }

    export interface $GroupLayout extends $MemoryLayout {
        memberLayouts(): $List<$MemoryLayout>;
        withByteAlignment(arg0: number): $GroupLayout;
        withName(arg0: string): $GroupLayout;
        withoutName(): $GroupLayout;
    }

    export type $MemorySegment$Scope_<CallbackResult = boolean> = (() => CallbackResult) | ($MemorySegment$Scope & { readonly [Symbol.hasInstance]?: never });
    export type $SegmentAllocator_<CallbackArg0 = number, CallbackArg1 = number, CallbackResult = $MemorySegment> = ((arg0: CallbackArg0, arg1: CallbackArg1) => CallbackResult) | ($SegmentAllocator & { readonly [Symbol.hasInstance]?: never });
}
