import { $Serializable } from "java:java/io";
import { $Cloneable, $StringBuffer } from "java:java/lang";
import { $Map, $Set } from "java:java/util";

declare module "java:java/text" {
    export class $Format implements $Serializable, $Cloneable {
        clone(): unknown;
        formatToCharacterIterator(arg0: unknown): $AttributedCharacterIterator;
        format(arg0: unknown, arg1: $StringBuffer, arg2: $FieldPosition): $StringBuffer;
        format(arg0: unknown): string;
        parseObject(arg0: string, arg1: $ParsePosition): unknown;
        parseObject(arg0: string): unknown;
    }

    export class $ParsePosition {
        constructor(arg0: number);
        get errorIndex(): number;
        getErrorIndex(): number;
        set errorIndex(value: number);
        get index(): number;
        getIndex(): number;
        set index(value: number);
        equals(arg0: unknown): boolean;
        hashCode(): number;
        setErrorIndex(arg0: number): void;
        setIndex(arg0: number): void;
        toString(): string;
    }

    export interface $AttributedCharacterIterator extends $CharacterIterator {
        getAllAttributeKeys(): $Set<$AttributedCharacterIterator$Attribute>;
        getAttributes(): $Map<$AttributedCharacterIterator$Attribute, unknown>;
        getAttribute(arg0: $AttributedCharacterIterator$Attribute): unknown;
        getRunLimit(arg0: $AttributedCharacterIterator$Attribute): number;
        getRunLimit(arg0: $AttributedCharacterIterator$Attribute[]): number;
        getRunLimit(): number;
        getRunStart(arg0: $AttributedCharacterIterator$Attribute): number;
        getRunStart(arg0: $AttributedCharacterIterator$Attribute[]): number;
        getRunStart(): number;
    }

    export class $FieldPosition {
        constructor(arg0: number);
        constructor(arg0: $Format$Field);
        constructor(arg0: $Format$Field, arg1: number);
        get beginIndex(): number;
        getBeginIndex(): number;
        set beginIndex(value: number);
        get endIndex(): number;
        getEndIndex(): number;
        set endIndex(value: number);
        get fieldAttribute(): $Format$Field;
        getFieldAttribute(): $Format$Field;
        get field(): number;
        getField(): number;
        equals(arg0: unknown): boolean;
        hashCode(): number;
        setBeginIndex(arg0: number): void;
        setEndIndex(arg0: number): void;
        toString(): string;
    }

    export class $AttributedCharacterIterator$Attribute implements $Serializable {
        static INPUT_METHOD_SEGMENT: $AttributedCharacterIterator$Attribute;
        static LANGUAGE: $AttributedCharacterIterator$Attribute;
        static READING: $AttributedCharacterIterator$Attribute;
        equals(arg0: unknown): boolean;
        hashCode(): number;
        toString(): string;
    }

    export interface $CharacterIterator extends $Cloneable {
        clone(): unknown;
        current(): string;
        first(): string;
        getBeginIndex(): number;
        getEndIndex(): number;
        getIndex(): number;
        last(): string;
        next(): string;
        previous(): string;
        setIndex(arg0: number): string;
    }
    export const $CharacterIterator: {
        readonly DONE: string;
    };

    export class $Format$Field extends $AttributedCharacterIterator$Attribute {
    }

}
