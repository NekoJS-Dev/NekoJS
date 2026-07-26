





        aliases(): $Set<string>;
        averageBytesPerChar(): number;
        averageCharsPerByte(): number;
        canEncode(): boolean;
        canEncode(arg0: $CharSequence): boolean;
        canEncode(arg0: string): boolean;
        charset(): $Charset;
        charset(): $Charset;
        compareTo(arg0: $Charset): number;
        compareTo(arg0: object): number;
        contains(arg0: $Charset): boolean;
        decode(arg0: $ByteBuffer): $CharBuffer;
        decode(arg0: $ByteBuffer): $CharBuffer;
        decode(arg0: $ByteBuffer, arg1: $CharBuffer, arg2: boolean): $CoderResult;
        detectedCharset(): $Charset;
        displayName(): string;
        displayName(arg0: $Locale): string;
        encode(arg0: $CharBuffer): $ByteBuffer;
        encode(arg0: $CharBuffer): $ByteBuffer;
        encode(arg0: $CharBuffer, arg1: $ByteBuffer, arg2: boolean): $CoderResult;
        encode(arg0: string): $ByteBuffer;
        equals(arg0: object): boolean;
        flush(arg0: $ByteBuffer): $CoderResult;
        flush(arg0: $CharBuffer): $CoderResult;
        get autoDetecting(): boolean;
        get charsetDetected(): boolean;
        get error(): boolean;
        get malformed(): boolean;
        get overflow(): boolean;
        get registered(): boolean;
        get underflow(): boolean;
        get unmappable(): boolean;
        hashCode(): number;
        isAutoDetecting(): boolean;
        isCharsetDetected(): boolean;
        isError(): boolean;
        isLegalReplacement(arg0: number[]): boolean;
        isMalformed(): boolean;
        isOverflow(): boolean;
        isRegistered(): boolean;
        isUnderflow(): boolean;
        isUnmappable(): boolean;
        length(): number;
        malformedInputAction(): $CodingErrorAction;
        malformedInputAction(): $CodingErrorAction;
        maxBytesPerChar(): number;
        maxCharsPerByte(): number;
        name(): string;
        newDecoder(): $CharsetDecoder;
        newEncoder(): $CharsetEncoder;
        onMalformedInput(arg0: $CodingErrorAction): $CharsetDecoder;
        onMalformedInput(arg0: $CodingErrorAction): $CharsetEncoder;
        onUnmappableCharacter(arg0: $CodingErrorAction): $CharsetDecoder;
        onUnmappableCharacter(arg0: $CodingErrorAction): $CharsetEncoder;
        replaceWith(arg0: number[]): $CharsetEncoder;
        replaceWith(arg0: string): $CharsetDecoder;
        replacement(): number[];
        replacement(): string;
        reset(): $CharsetDecoder;
        reset(): $CharsetEncoder;
        static IGNORE: $CodingErrorAction;
        static OVERFLOW: $CoderResult;
        static REPLACE: $CodingErrorAction;
        static REPORT: $CodingErrorAction;
        static UNDERFLOW: $CoderResult;
        static availableCharsets(): $SortedMap<string, $Charset>;
        static defaultCharset(): $Charset;
        static forName(arg0: string): $Charset;
        static forName(arg0: string, arg1: $Charset): $Charset;
        static isSupported(arg0: string): boolean;
        static malformedForLength(arg0: number): $CoderResult;
        static unmappableForLength(arg0: number): $CoderResult;
        throwException(): void;
        toString(): string;
        toString(): string;
        toString(): string;
        unmappableCharacterAction(): $CodingErrorAction;
        unmappableCharacterAction(): $CodingErrorAction;
    export class $Charset implements $Comparable {
    export class $CharsetDecoder {
    export class $CharsetEncoder {
    export class $CoderResult {
    export class $CodingErrorAction {
    }
    }
    }
    }
    }
declare module "java:java/nio/charset" {
import { $ByteBuffer, $CharBuffer } from "java:java/nio";
import { $CharSequence, $Comparable, $String } from "java:java/lang";
import { $Locale, $Set, $SortedMap } from "java:java/util";
}