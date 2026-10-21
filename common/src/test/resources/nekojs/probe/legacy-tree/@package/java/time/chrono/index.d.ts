import { $Serializable } from "java:java/io";
import { $Comparable, $Comparable_, $Enum } from "java:java/lang";
import { $Clock, $Instant, $LocalDate, $LocalDateTime, $LocalTime, $Period, $ZoneId, $ZoneOffset, $ZonedDateTime } from "java:java/time";
import { $DateTimeFormatter, $ResolverStyle, $ResolverStyle_, $TextStyle, $TextStyle_ } from "java:java/time/format";
import { $ChronoField, $ChronoField_, $Temporal, $TemporalAccessor, $TemporalAdjuster, $TemporalAdjuster_, $TemporalAmount, $TemporalField, $TemporalQuery, $TemporalQuery_, $TemporalUnit, $ValueRange } from "java:java/time/temporal";
import { $Comparator, $Comparator_, $List, $Locale, $Map, $Set } from "java:java/util";

declare module "java:java/time/chrono" {
    export interface $ChronoLocalDate extends $Temporal, $TemporalAdjuster, $Comparable<$ChronoLocalDate> {
        adjustInto(arg0: $Temporal): $Temporal;
        atTime(arg0: $LocalTime): $ChronoLocalDateTime<any>;
        compareTo(arg0: $ChronoLocalDate): number;
        equals(arg0: unknown): boolean;
        format(arg0: $DateTimeFormatter): string;
        getChronology(): $Chronology;
        getEra(): $Era;
        hashCode(): number;
        isAfter(arg0: $ChronoLocalDate): boolean;
        isBefore(arg0: $ChronoLocalDate): boolean;
        isEqual(arg0: $ChronoLocalDate): boolean;
        isLeapYear(): boolean;
        isSupported(arg0: $TemporalField): boolean;
        isSupported(arg0: $TemporalUnit): boolean;
        lengthOfMonth(): number;
        lengthOfYear(): number;
        minus(arg0: $TemporalAmount): $ChronoLocalDate;
        minus(arg0: number, arg1: $TemporalUnit): $ChronoLocalDate;
        plus(arg0: $TemporalAmount): $ChronoLocalDate;
        plus(arg0: number, arg1: $TemporalUnit): $ChronoLocalDate;
        query<R>(arg0: $TemporalQuery_<R, $TemporalAccessor, R>): R;
        toEpochDay(): number;
        toString(): string;
        until(arg0: $ChronoLocalDate): $ChronoPeriod;
        until(arg0: $Temporal, arg1: $TemporalUnit): number;
        with(arg0: $TemporalAdjuster_<$Temporal, $Temporal>): $ChronoLocalDate;
        with(arg0: $TemporalField, arg1: number): $ChronoLocalDate;
    }
    export const $ChronoLocalDate: {
        from(arg0: $TemporalAccessor): $ChronoLocalDate;
        timeLineOrder(): $Comparator<$ChronoLocalDate>;
    };

    export class $IsoChronology extends $AbstractChronology implements $Serializable {
        static INSTANCE: $IsoChronology;
        get calendarType(): string;
        getCalendarType(): string;
        get id(): string;
        getId(): string;
        get isoBased(): boolean;
        isIsoBased(): boolean;
        dateEpochDay(arg0: number): $LocalDate;
        dateNow(arg0: $Clock): $LocalDate;
        dateNow(arg0: $ZoneId): $LocalDate;
        dateNow(): $LocalDate;
        dateYearDay(arg0: number, arg1: number): $LocalDate;
        dateYearDay(arg0: $Era_<number>, arg1: number, arg2: number): $LocalDate;
        date(arg0: number, arg1: number, arg2: number): $LocalDate;
        date(arg0: $Era_<number>, arg1: number, arg2: number, arg3: number): $LocalDate;
        date(arg0: $TemporalAccessor): $LocalDate;
        epochSecond(arg0: number, arg1: number, arg2: number, arg3: number, arg4: number, arg5: number, arg6: $ZoneOffset): number;
        epochSecond(arg0: $Era_<number>, arg1: number, arg2: number, arg3: number, arg4: number, arg5: number, arg6: number, arg7: $ZoneOffset): number;
        equals(arg0: unknown): boolean;
        eraOf(arg0: number): $IsoEra;
        eras(): $List<$Era>;
        getDisplayName(arg0: $TextStyle_, arg1: $Locale): string;
        hashCode(): number;
        isLeapYear(arg0: number): boolean;
        localDateTime(arg0: $TemporalAccessor): $LocalDateTime;
        period(arg0: number, arg1: number, arg2: number): $Period;
        prolepticYear(arg0: $Era_<number>, arg1: number): number;
        range(arg0: $ChronoField_): $ValueRange;
        resolveDate(arg0: ($Map<$TemporalField, number> | ([$TemporalField] extends [PropertyKey] ? Partial<Record<Extract<$TemporalField, PropertyKey>, number>> : never)), arg1: $ResolverStyle_): $LocalDate;
        toString(): string;
        zonedDateTime(arg0: $Instant, arg1: $ZoneId): $ZonedDateTime;
        zonedDateTime(arg0: $TemporalAccessor): $ZonedDateTime;
    }

    export class $IsoEra {
        static BCE: $IsoEra;
        static CE: $IsoEra;
        name(): string;
        ordinal(): number;
        toString(): string;
        static values(): $IsoEra[];
        static valueOf(name: string): $IsoEra;
    }
    export interface $IsoEra extends $Enum<$IsoEra> {}

    export interface $ChronoLocalDateTime<D extends $ChronoLocalDate> extends $Temporal, $TemporalAdjuster, $Comparable<$ChronoLocalDateTime<any>> {
        adjustInto(arg0: $Temporal): $Temporal;
        atZone(arg0: $ZoneId): $ChronoZonedDateTime<D>;
        compareTo(arg0: $ChronoLocalDateTime<any>): number;
        equals(arg0: unknown): boolean;
        format(arg0: $DateTimeFormatter): string;
        getChronology(): $Chronology;
        hashCode(): number;
        isAfter(arg0: $ChronoLocalDateTime<any>): boolean;
        isBefore(arg0: $ChronoLocalDateTime<any>): boolean;
        isEqual(arg0: $ChronoLocalDateTime<any>): boolean;
        isSupported(arg0: $TemporalField): boolean;
        isSupported(arg0: $TemporalUnit): boolean;
        minus(arg0: $TemporalAmount): $ChronoLocalDateTime<D>;
        minus(arg0: number, arg1: $TemporalUnit): $ChronoLocalDateTime<D>;
        plus(arg0: $TemporalAmount): $ChronoLocalDateTime<D>;
        plus(arg0: number, arg1: $TemporalUnit): $ChronoLocalDateTime<D>;
        query<R>(arg0: $TemporalQuery_<R, $TemporalAccessor, R>): R;
        toEpochSecond(arg0: $ZoneOffset): number;
        toInstant(arg0: $ZoneOffset): $Instant;
        toLocalDate(): D;
        toLocalTime(): $LocalTime;
        toString(): string;
        with(arg0: $TemporalAdjuster_<$Temporal, $Temporal>): $ChronoLocalDateTime<D>;
        with(arg0: $TemporalField, arg1: number): $ChronoLocalDateTime<D>;
    }
    export const $ChronoLocalDateTime: {
        from(arg0: $TemporalAccessor): $ChronoLocalDateTime<any>;
        timeLineOrder(): $Comparator<$ChronoLocalDateTime<any>>;
    };

    export interface $Chronology extends $Comparable<$Chronology> {
        compareTo(arg0: $Chronology): number;
        dateEpochDay(arg0: number): $ChronoLocalDate;
        dateNow(arg0: $Clock): $ChronoLocalDate;
        dateNow(arg0: $ZoneId): $ChronoLocalDate;
        dateNow(): $ChronoLocalDate;
        dateYearDay(arg0: number, arg1: number): $ChronoLocalDate;
        dateYearDay(arg0: $Era_<number>, arg1: number, arg2: number): $ChronoLocalDate;
        date(arg0: number, arg1: number, arg2: number): $ChronoLocalDate;
        date(arg0: $Era_<number>, arg1: number, arg2: number, arg3: number): $ChronoLocalDate;
        date(arg0: $TemporalAccessor): $ChronoLocalDate;
        epochSecond(arg0: number, arg1: number, arg2: number, arg3: number, arg4: number, arg5: number, arg6: $ZoneOffset): number;
        epochSecond(arg0: $Era_<number>, arg1: number, arg2: number, arg3: number, arg4: number, arg5: number, arg6: number, arg7: $ZoneOffset): number;
        equals(arg0: unknown): boolean;
        eraOf(arg0: number): $Era;
        eras(): $List<$Era>;
        getCalendarType(): string;
        getDisplayName(arg0: $TextStyle_, arg1: $Locale): string;
        getId(): string;
        hashCode(): number;
        isIsoBased(): boolean;
        isLeapYear(arg0: number): boolean;
        localDateTime(arg0: $TemporalAccessor): $ChronoLocalDateTime<$ChronoLocalDate>;
        period(arg0: number, arg1: number, arg2: number): $ChronoPeriod;
        prolepticYear(arg0: $Era_<number>, arg1: number): number;
        range(arg0: $ChronoField_): $ValueRange;
        resolveDate(arg0: ($Map<$TemporalField, number> | ([$TemporalField] extends [PropertyKey] ? Partial<Record<Extract<$TemporalField, PropertyKey>, number>> : never)), arg1: $ResolverStyle_): $ChronoLocalDate;
        toString(): string;
        zonedDateTime(arg0: $Instant, arg1: $ZoneId): $ChronoZonedDateTime<$ChronoLocalDate>;
        zonedDateTime(arg0: $TemporalAccessor): $ChronoZonedDateTime<$ChronoLocalDate>;
    }
    export const $Chronology: {
        from(arg0: $TemporalAccessor): $Chronology;
        getAvailableChronologies(): $Set<$Chronology>;
        ofLocale(arg0: $Locale): $Chronology;
        of(arg0: string): $Chronology;
    };

    export interface $ChronoPeriod extends $TemporalAmount {
        addTo(arg0: $Temporal): $Temporal;
        equals(arg0: unknown): boolean;
        getChronology(): $Chronology;
        getUnits(): $List<$TemporalUnit>;
        get(arg0: $TemporalUnit): number;
        hashCode(): number;
        isNegative(): boolean;
        isZero(): boolean;
        minus(arg0: $TemporalAmount): $ChronoPeriod;
        multipliedBy(arg0: number): $ChronoPeriod;
        negated(): $ChronoPeriod;
        normalized(): $ChronoPeriod;
        plus(arg0: $TemporalAmount): $ChronoPeriod;
        subtractFrom(arg0: $Temporal): $Temporal;
        toString(): string;
    }
    export const $ChronoPeriod: {
        between(arg0: $ChronoLocalDate, arg1: $ChronoLocalDate): $ChronoPeriod;
    };

    export interface $ChronoZonedDateTime<D extends $ChronoLocalDate> extends $Temporal, $Comparable<$ChronoZonedDateTime<any>> {
        compareTo(arg0: $ChronoZonedDateTime<any>): number;
        equals(arg0: unknown): boolean;
        format(arg0: $DateTimeFormatter): string;
        getChronology(): $Chronology;
        getLong(arg0: $TemporalField): number;
        getOffset(): $ZoneOffset;
        getZone(): $ZoneId;
        get(arg0: $TemporalField): number;
        hashCode(): number;
        isAfter(arg0: $ChronoZonedDateTime<any>): boolean;
        isBefore(arg0: $ChronoZonedDateTime<any>): boolean;
        isEqual(arg0: $ChronoZonedDateTime<any>): boolean;
        isSupported(arg0: $TemporalField): boolean;
        isSupported(arg0: $TemporalUnit): boolean;
        minus(arg0: $TemporalAmount): $ChronoZonedDateTime<D>;
        minus(arg0: number, arg1: $TemporalUnit): $ChronoZonedDateTime<D>;
        plus(arg0: $TemporalAmount): $ChronoZonedDateTime<D>;
        plus(arg0: number, arg1: $TemporalUnit): $ChronoZonedDateTime<D>;
        query<R>(arg0: $TemporalQuery_<R, $TemporalAccessor, R>): R;
        range(arg0: $TemporalField): $ValueRange;
        toEpochSecond(): number;
        toInstant(): $Instant;
        toLocalDateTime(): $ChronoLocalDateTime<D>;
        toLocalDate(): D;
        toLocalTime(): $LocalTime;
        toString(): string;
        withEarlierOffsetAtOverlap(): $ChronoZonedDateTime<D>;
        withLaterOffsetAtOverlap(): $ChronoZonedDateTime<D>;
        withZoneSameInstant(arg0: $ZoneId): $ChronoZonedDateTime<D>;
        withZoneSameLocal(arg0: $ZoneId): $ChronoZonedDateTime<D>;
        with(arg0: $TemporalAdjuster_<$Temporal, $Temporal>): $ChronoZonedDateTime<D>;
        with(arg0: $TemporalField, arg1: number): $ChronoZonedDateTime<D>;
    }
    export const $ChronoZonedDateTime: {
        from(arg0: $TemporalAccessor): $ChronoZonedDateTime<any>;
        timeLineOrder(): $Comparator<$ChronoZonedDateTime<any>>;
    };

    export interface $Era extends $TemporalAccessor, $TemporalAdjuster {
        adjustInto(arg0: $Temporal): $Temporal;
        getDisplayName(arg0: $TextStyle_, arg1: $Locale): string;
        getLong(arg0: $TemporalField): number;
        getValue(): number;
        get(arg0: $TemporalField): number;
        isSupported(arg0: $TemporalField): boolean;
        query<R>(arg0: $TemporalQuery_<R, $TemporalAccessor, R>): R;
        range(arg0: $TemporalField): $ValueRange;
    }

    export class $AbstractChronology implements $Chronology {
        get calendarType(): string;
        getCalendarType(): string;
        get id(): string;
        getId(): string;
        get isoBased(): boolean;
        isIsoBased(): boolean;
        compareTo(arg0: $Chronology): number;
        dateEpochDay(arg0: number): $ChronoLocalDate;
        dateNow(arg0: $Clock): $ChronoLocalDate;
        dateNow(arg0: $ZoneId): $ChronoLocalDate;
        dateNow(): $ChronoLocalDate;
        dateYearDay(arg0: number, arg1: number): $ChronoLocalDate;
        dateYearDay(arg0: $Era_<number>, arg1: number, arg2: number): $ChronoLocalDate;
        date(arg0: number, arg1: number, arg2: number): $ChronoLocalDate;
        date(arg0: $Era_<number>, arg1: number, arg2: number, arg3: number): $ChronoLocalDate;
        date(arg0: $TemporalAccessor): $ChronoLocalDate;
        epochSecond(arg0: number, arg1: number, arg2: number, arg3: number, arg4: number, arg5: number, arg6: $ZoneOffset): number;
        epochSecond(arg0: $Era_<number>, arg1: number, arg2: number, arg3: number, arg4: number, arg5: number, arg6: number, arg7: $ZoneOffset): number;
        equals(arg0: unknown): boolean;
        eraOf(arg0: number): $Era;
        eras(): $List<$Era>;
        getDisplayName(arg0: $TextStyle_, arg1: $Locale): string;
        hashCode(): number;
        isLeapYear(arg0: number): boolean;
        localDateTime(arg0: $TemporalAccessor): $ChronoLocalDateTime<$ChronoLocalDate>;
        period(arg0: number, arg1: number, arg2: number): $ChronoPeriod;
        prolepticYear(arg0: $Era_<number>, arg1: number): number;
        range(arg0: $ChronoField_): $ValueRange;
        resolveDate(arg0: ($Map<$TemporalField, number> | ([$TemporalField] extends [PropertyKey] ? Partial<Record<Extract<$TemporalField, PropertyKey>, number>> : never)), arg1: $ResolverStyle_): $ChronoLocalDate;
        toString(): string;
        zonedDateTime(arg0: $Instant, arg1: $ZoneId): $ChronoZonedDateTime<$ChronoLocalDate>;
        zonedDateTime(arg0: $TemporalAccessor): $ChronoZonedDateTime<$ChronoLocalDate>;
    }

    export type $IsoEra_ = $IsoEra | "BCE" | "CE";
    export type $Era_<CallbackResult = number> = (() => CallbackResult) | ($Era & { readonly [Symbol.hasInstance]?: never });
}
