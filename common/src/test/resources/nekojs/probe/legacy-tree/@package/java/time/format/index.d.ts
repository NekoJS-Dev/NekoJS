import { $Appendable, $CharSequence, $Enum } from "java:java/lang";
import { $Format, $ParsePosition } from "java:java/text";
import { $Period, $ZoneId } from "java:java/time";
import { $Chronology } from "java:java/time/chrono";
import { $TemporalAccessor, $TemporalField, $TemporalQuery, $TemporalQuery_ } from "java:java/time/temporal";
import { $Locale, $Set } from "java:java/util";

declare module "java:java/time/format" {
    export class $DateTimeFormatter {
        static BASIC_ISO_DATE: $DateTimeFormatter;
        static ISO_DATE: $DateTimeFormatter;
        static ISO_DATE_TIME: $DateTimeFormatter;
        static ISO_INSTANT: $DateTimeFormatter;
        static ISO_LOCAL_DATE: $DateTimeFormatter;
        static ISO_LOCAL_DATE_TIME: $DateTimeFormatter;
        static ISO_LOCAL_TIME: $DateTimeFormatter;
        static ISO_OFFSET_DATE: $DateTimeFormatter;
        static ISO_OFFSET_DATE_TIME: $DateTimeFormatter;
        static ISO_OFFSET_TIME: $DateTimeFormatter;
        static ISO_ORDINAL_DATE: $DateTimeFormatter;
        static ISO_TIME: $DateTimeFormatter;
        static ISO_WEEK_DATE: $DateTimeFormatter;
        static ISO_ZONED_DATE_TIME: $DateTimeFormatter;
        static RFC_1123_DATE_TIME: $DateTimeFormatter;
        get chronology(): $Chronology;
        getChronology(): $Chronology;
        get decimalStyle(): $DecimalStyle;
        getDecimalStyle(): $DecimalStyle;
        get locale(): $Locale;
        getLocale(): $Locale;
        get resolverFields(): $Set<$TemporalField>;
        getResolverFields(): $Set<$TemporalField>;
        get resolverStyle(): $ResolverStyle;
        getResolverStyle(): $ResolverStyle;
        get zone(): $ZoneId;
        getZone(): $ZoneId;
        static ofLocalizedDateTime(arg0: $FormatStyle_, arg1: $FormatStyle_): $DateTimeFormatter;
        static ofLocalizedDateTime(arg0: $FormatStyle_): $DateTimeFormatter;
        static ofLocalizedDate(arg0: $FormatStyle_): $DateTimeFormatter;
        static ofLocalizedPattern(arg0: string): $DateTimeFormatter;
        static ofLocalizedTime(arg0: $FormatStyle_): $DateTimeFormatter;
        static ofPattern(arg0: string, arg1: $Locale): $DateTimeFormatter;
        static ofPattern(arg0: string): $DateTimeFormatter;
        static parsedExcessDays(): $TemporalQuery<$Period>;
        static parsedLeapSecond(): $TemporalQuery<boolean>;
        formatTo(arg0: $TemporalAccessor, arg1: $Appendable): void;
        format(arg0: $TemporalAccessor): string;
        localizedBy(arg0: $Locale): $DateTimeFormatter;
        parseBest(arg0: $CharSequence, arg1?: $TemporalQuery_<any, $TemporalAccessor, any>[]): $TemporalAccessor;
        parseUnresolved(arg0: $CharSequence, arg1: $ParsePosition): $TemporalAccessor;
        parse(arg0: $CharSequence, arg1: $ParsePosition): $TemporalAccessor;
        parse<T>(arg0: $CharSequence, arg1: $TemporalQuery_<T, $TemporalAccessor, T>): T;
        parse(arg0: $CharSequence): $TemporalAccessor;
        toFormat(arg0: $TemporalQuery_<any, $TemporalAccessor, any>): $Format;
        toFormat(): $Format;
        toString(): string;
        withChronology(arg0: $Chronology): $DateTimeFormatter;
        withDecimalStyle(arg0: $DecimalStyle): $DateTimeFormatter;
        withLocale(arg0: $Locale): $DateTimeFormatter;
        withResolverFields(arg0?: $TemporalField[]): $DateTimeFormatter;
        withResolverFields(arg0: $TemporalField[]): $DateTimeFormatter;
        withResolverStyle(arg0: $ResolverStyle_): $DateTimeFormatter;
        withZone(arg0: $ZoneId): $DateTimeFormatter;
    }

    export class $TextStyle {
        static FULL: $TextStyle;
        static FULL_STANDALONE: $TextStyle;
        static NARROW: $TextStyle;
        static NARROW_STANDALONE: $TextStyle;
        static SHORT: $TextStyle;
        static SHORT_STANDALONE: $TextStyle;
        name(): string;
        ordinal(): number;
        toString(): string;
        static values(): $TextStyle[];
        static valueOf(name: string): $TextStyle;
    }
    export interface $TextStyle extends $Enum<$TextStyle> {}

    export class $ResolverStyle {
        static LENIENT: $ResolverStyle;
        static SMART: $ResolverStyle;
        static STRICT: $ResolverStyle;
        name(): string;
        ordinal(): number;
        toString(): string;
        static values(): $ResolverStyle[];
        static valueOf(name: string): $ResolverStyle;
    }
    export interface $ResolverStyle extends $Enum<$ResolverStyle> {}

    export class $DecimalStyle {
        static STANDARD: $DecimalStyle;
        get decimalSeparator(): string;
        getDecimalSeparator(): string;
        get negativeSign(): string;
        getNegativeSign(): string;
        get positiveSign(): string;
        getPositiveSign(): string;
        get zeroDigit(): string;
        getZeroDigit(): string;
        static getAvailableLocales(): $Set<$Locale>;
        static ofDefaultLocale(): $DecimalStyle;
        static of(arg0: $Locale): $DecimalStyle;
        equals(arg0: unknown): boolean;
        hashCode(): number;
        toString(): string;
        withDecimalSeparator(arg0: string): $DecimalStyle;
        withNegativeSign(arg0: string): $DecimalStyle;
        withPositiveSign(arg0: string): $DecimalStyle;
        withZeroDigit(arg0: string): $DecimalStyle;
    }

    export class $FormatStyle {
        static FULL: $FormatStyle;
        static LONG: $FormatStyle;
        static MEDIUM: $FormatStyle;
        static SHORT: $FormatStyle;
        name(): string;
        ordinal(): number;
        toString(): string;
        static values(): $FormatStyle[];
        static valueOf(name: string): $FormatStyle;
    }
    export interface $FormatStyle extends $Enum<$FormatStyle> {}

    export type $TextStyle_ = $TextStyle | "FULL" | "FULL_STANDALONE" | "NARROW" | "NARROW_STANDALONE" | "SHORT" | "SHORT_STANDALONE";
    export type $ResolverStyle_ = $ResolverStyle | "LENIENT" | "SMART" | "STRICT";
    export type $FormatStyle_ = $FormatStyle | "FULL" | "LONG" | "MEDIUM" | "SHORT";
}
