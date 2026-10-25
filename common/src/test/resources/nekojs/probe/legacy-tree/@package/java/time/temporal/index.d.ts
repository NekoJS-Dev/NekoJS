import { $Serializable } from "java:java/io";
import { $Enum } from "java:java/lang";
import { $Duration } from "java:java/time";
import { $ResolverStyle, $ResolverStyle_ } from "java:java/time/format";
import { $List, $Locale, $Map } from "java:java/util";

declare module "java:java/time/temporal" {
    export interface $Temporal extends $TemporalAccessor {
        isSupported(arg0: $TemporalField): boolean;
        isSupported(arg0: $TemporalUnit): boolean;
        minus(arg0: $TemporalAmount): $Temporal;
        minus(arg0: number, arg1: $TemporalUnit): $Temporal;
        plus(arg0: $TemporalAmount): $Temporal;
        plus(arg0: number, arg1: $TemporalUnit): $Temporal;
        until(arg0: $Temporal, arg1: $TemporalUnit): number;
        with(arg0: $TemporalAdjuster_<$Temporal, $Temporal>): $Temporal;
        with(arg0: $TemporalField, arg1: number): $Temporal;
    }

    export interface $TemporalAmount {
        addTo(arg0: $Temporal): $Temporal;
        getUnits(): $List<$TemporalUnit>;
        get(arg0: $TemporalUnit): number;
        subtractFrom(arg0: $Temporal): $Temporal;
    }

    export interface $TemporalUnit {
        addTo<R extends $Temporal>(arg0: R, arg1: number): R;
        between(arg0: $Temporal, arg1: $Temporal): number;
        getDuration(): $Duration;
        isDateBased(): boolean;
        isDurationEstimated(): boolean;
        isSupportedBy(arg0: $Temporal): boolean;
        isTimeBased(): boolean;
        toString(): string;
    }

    export interface $TemporalAccessor {
        getLong(arg0: $TemporalField): number;
        get(arg0: $TemporalField): number;
        isSupported(arg0: $TemporalField): boolean;
        query<R>(arg0: $TemporalQuery_<R, $TemporalAccessor, R>): R;
        range(arg0: $TemporalField): $ValueRange;
    }

    export interface $TemporalAdjuster {
        adjustInto(arg0: $Temporal): $Temporal;
    }

    export interface $TemporalField {
        adjustInto<R extends $Temporal>(arg0: R, arg1: number): R;
        getBaseUnit(): $TemporalUnit;
        getDisplayName(arg0: $Locale): string;
        getFrom(arg0: $TemporalAccessor): number;
        getRangeUnit(): $TemporalUnit;
        isDateBased(): boolean;
        isSupportedBy(arg0: $TemporalAccessor): boolean;
        isTimeBased(): boolean;
        rangeRefinedBy(arg0: $TemporalAccessor): $ValueRange;
        range(): $ValueRange;
        resolve(arg0: ($Map<$TemporalField, number> | (([$TemporalField] extends [PropertyKey] ? unknown : never) & Partial<Record<Extract<$TemporalField, PropertyKey>, number>>)), arg1: $TemporalAccessor, arg2: $ResolverStyle_): $TemporalAccessor;
        toString(): string;
    }

    export interface $TemporalQuery<R> {
        queryFrom(arg0: $TemporalAccessor): R;
    }

    export class $ValueRange implements $Serializable {
        get largestMinimum(): number;
        getLargestMinimum(): number;
        get maximum(): number;
        getMaximum(): number;
        get minimum(): number;
        getMinimum(): number;
        get smallestMaximum(): number;
        getSmallestMaximum(): number;
        get fixed(): boolean;
        isFixed(): boolean;
        get intValue(): boolean;
        isIntValue(): boolean;
        static of(arg0: number, arg1: number, arg2: number, arg3: number): $ValueRange;
        static of(arg0: number, arg1: number, arg2: number): $ValueRange;
        static of(arg0: number, arg1: number): $ValueRange;
        checkValidIntValue(arg0: number, arg1: $TemporalField): number;
        checkValidValue(arg0: number, arg1: $TemporalField): number;
        equals(arg0: unknown): boolean;
        hashCode(): number;
        isValidIntValue(arg0: number): boolean;
        isValidValue(arg0: number): boolean;
        toString(): string;
    }

    export class $ChronoField {
        static ALIGNED_DAY_OF_WEEK_IN_MONTH: $ChronoField;
        static ALIGNED_DAY_OF_WEEK_IN_YEAR: $ChronoField;
        static ALIGNED_WEEK_OF_MONTH: $ChronoField;
        static ALIGNED_WEEK_OF_YEAR: $ChronoField;
        static AMPM_OF_DAY: $ChronoField;
        static CLOCK_HOUR_OF_AMPM: $ChronoField;
        static CLOCK_HOUR_OF_DAY: $ChronoField;
        static DAY_OF_MONTH: $ChronoField;
        static DAY_OF_WEEK: $ChronoField;
        static DAY_OF_YEAR: $ChronoField;
        static EPOCH_DAY: $ChronoField;
        static ERA: $ChronoField;
        static HOUR_OF_AMPM: $ChronoField;
        static HOUR_OF_DAY: $ChronoField;
        static INSTANT_SECONDS: $ChronoField;
        static MICRO_OF_DAY: $ChronoField;
        static MICRO_OF_SECOND: $ChronoField;
        static MILLI_OF_DAY: $ChronoField;
        static MILLI_OF_SECOND: $ChronoField;
        static MINUTE_OF_DAY: $ChronoField;
        static MINUTE_OF_HOUR: $ChronoField;
        static MONTH_OF_YEAR: $ChronoField;
        static NANO_OF_DAY: $ChronoField;
        static NANO_OF_SECOND: $ChronoField;
        static OFFSET_SECONDS: $ChronoField;
        static PROLEPTIC_MONTH: $ChronoField;
        static SECOND_OF_DAY: $ChronoField;
        static SECOND_OF_MINUTE: $ChronoField;
        static YEAR: $ChronoField;
        static YEAR_OF_ERA: $ChronoField;
        name(): string;
        ordinal(): number;
        toString(): string;
        static values(): $ChronoField[];
        static valueOf(name: string): $ChronoField;
    }
    export interface $ChronoField extends $Enum<$ChronoField>, $TemporalField {}

    export type $TemporalAdjuster_<CallbackArg0 = $Temporal, CallbackResult = $Temporal> = ((arg0: CallbackArg0) => CallbackResult) | ($TemporalAdjuster & { readonly [Symbol.hasInstance]?: never }) | (globalThis.NoInfer<{ readonly "adjustInto": (arg0: CallbackArg0) => CallbackResult } & { readonly [Symbol.hasInstance]?: never }>);
    export type $TemporalQuery_<Host0 = any, CallbackArg0 = $TemporalAccessor, CallbackResult = Host0> = ((arg0: CallbackArg0) => CallbackResult) | ($TemporalQuery<Host0> & { readonly [Symbol.hasInstance]?: never }) | (globalThis.NoInfer<{ readonly "queryFrom": (arg0: CallbackArg0) => CallbackResult } & { readonly [Symbol.hasInstance]?: never }>);
    export type $ChronoField_ = $ChronoField | "ALIGNED_DAY_OF_WEEK_IN_MONTH" | "ALIGNED_DAY_OF_WEEK_IN_YEAR" | "ALIGNED_WEEK_OF_MONTH" | "ALIGNED_WEEK_OF_YEAR" | "AMPM_OF_DAY" | "CLOCK_HOUR_OF_AMPM" | "CLOCK_HOUR_OF_DAY" | "DAY_OF_MONTH" | "DAY_OF_WEEK" | "DAY_OF_YEAR" | "EPOCH_DAY" | "ERA" | "HOUR_OF_AMPM" | "HOUR_OF_DAY" | "INSTANT_SECONDS" | "MICRO_OF_DAY" | "MICRO_OF_SECOND" | "MILLI_OF_DAY" | "MILLI_OF_SECOND" | "MINUTE_OF_DAY" | "MINUTE_OF_HOUR" | "MONTH_OF_YEAR" | "NANO_OF_DAY" | "NANO_OF_SECOND" | "OFFSET_SECONDS" | "PROLEPTIC_MONTH" | "SECOND_OF_DAY" | "SECOND_OF_MINUTE" | "YEAR" | "YEAR_OF_ERA";
}
