import { $CharSequence, $Comparable, $String } from "java:java/lang";
import { $ByteBuffer, $CharBuffer } from "java:java/nio";
import { $Locale, $Set, $SortedMap } from "java:java/util";

declare module "java:java/nio/charset" {
    export class $Charset implements $Comparable {
        get registered(): boolean;
        isRegistered(): boolean;
        static availableCharsets(): $SortedMap<string, $Charset>;
        static defaultCharset(): $Charset;
        static forName(arg0: string, arg1: $Charset): $Charset;
        static forName(arg0: string): $Charset;
        static isSupported(arg0: string): boolean;
        aliases(): $Set<string>;
        canEncode(): boolean;
        compareTo(arg0: object): number;
        compareTo(arg0: $Charset): number;
        contains(arg0: $Charset): boolean;
        decode(arg0: $ByteBuffer): $CharBuffer;
        displayName(arg0: $Locale): string;
        displayName(): string;
        encode(arg0: string): $ByteBuffer;
        encode(arg0: $CharBuffer): $ByteBuffer;
        equals(arg0: object): boolean;
        hashCode(): number;
        name(): string;
        newDecoder(): $CharsetDecoder;
        newEncoder(): $CharsetEncoder;
        toString(): string;
    }

    export class $CharsetDecoder {
        get autoDetecting(): boolean;
        isAutoDetecting(): boolean;
        get charsetDetected(): boolean;
        isCharsetDetected(): boolean;
        averageCharsPerByte(): number;
        charset(): $Charset;
        decode(arg0: $ByteBuffer, arg1: $CharBuffer, arg2: boolean): $CoderResult;
        decode(arg0: $ByteBuffer): $CharBuffer;
        detectedCharset(): $Charset;
        flush(arg0: $CharBuffer): $CoderResult;
        malformedInputAction(): $CodingErrorAction;
        maxCharsPerByte(): number;
        onMalformedInput(arg0: $CodingErrorAction): $CharsetDecoder;
        onUnmappableCharacter(arg0: $CodingErrorAction): $CharsetDecoder;
        replaceWith(arg0: string): $CharsetDecoder;
        replacement(): string;
        reset(): $CharsetDecoder;
        unmappableCharacterAction(): $CodingErrorAction;
    }

    export class $CharsetEncoder {
        averageBytesPerChar(): number;
        canEncode(arg0: string): boolean;
        canEncode(arg0: $CharSequence): boolean;
        charset(): $Charset;
        encode(arg0: $CharBuffer, arg1: $ByteBuffer, arg2: boolean): $CoderResult;
        encode(arg0: $CharBuffer): $ByteBuffer;
        flush(arg0: $ByteBuffer): $CoderResult;
        isLegalReplacement(arg0: number[]): boolean;
        malformedInputAction(): $CodingErrorAction;
        maxBytesPerChar(): number;
        onMalformedInput(arg0: $CodingErrorAction): $CharsetEncoder;
        onUnmappableCharacter(arg0: $CodingErrorAction): $CharsetEncoder;
        replaceWith(arg0: number[]): $CharsetEncoder;
        replacement(): number[];
        reset(): $CharsetEncoder;
        unmappableCharacterAction(): $CodingErrorAction;
    }

    export class $CodingErrorAction {
        static IGNORE: $CodingErrorAction;
        static REPLACE: $CodingErrorAction;
        static REPORT: $CodingErrorAction;
        toString(): string;
    }

    export class $CoderResult {
        static OVERFLOW: $CoderResult;
        static UNDERFLOW: $CoderResult;
        get error(): boolean;
        isError(): boolean;
        get malformed(): boolean;
        isMalformed(): boolean;
        get overflow(): boolean;
        isOverflow(): boolean;
        get underflow(): boolean;
        isUnderflow(): boolean;
        get unmappable(): boolean;
        isUnmappable(): boolean;
        static malformedForLength(arg0: number): $CoderResult;
        static unmappableForLength(arg0: number): $CoderResult;
        length(): number;
        throwException(): void;
        toString(): string;
    }

}
