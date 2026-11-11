import { $Serializable } from "java:java/io";
import { $Comparable, $Comparable_, $Enum } from "java:java/lang";
import { $DayOfWeek, $DayOfWeek_, $Duration, $Instant, $LocalDateTime, $LocalTime, $Month, $Month_, $ZoneOffset } from "java:java/time";
import { $List, $List_ } from "java:java/util";

declare module "java:java/time/zone" {
    export class $ZoneRules implements $Serializable {
        get transitionRules(): $List<$ZoneOffsetTransitionRule>;
        getTransitionRules(): $List<$ZoneOffsetTransitionRule>;
        get transitions(): $List<$ZoneOffsetTransition>;
        getTransitions(): $List<$ZoneOffsetTransition>;
        get fixedOffset(): boolean;
        isFixedOffset(): boolean;
        static of(
            arg0: $ZoneOffset,
            arg1: $ZoneOffset,
            arg2: $List_<$ZoneOffsetTransition>,
            arg3: $List_<$ZoneOffsetTransition>,
            arg4: $List_<$ZoneOffsetTransitionRule>
        ): $ZoneRules;
        static of(arg0: $ZoneOffset): $ZoneRules;
        equals(arg0: unknown): boolean;
        getDaylightSavings(arg0: $Instant): $Duration;
        getOffset(arg0: $Instant): $ZoneOffset;
        getOffset(arg0: $LocalDateTime): $ZoneOffset;
        getStandardOffset(arg0: $Instant): $ZoneOffset;
        getTransition(arg0: $LocalDateTime): $ZoneOffsetTransition;
        getValidOffsets(arg0: $LocalDateTime): $List<$ZoneOffset>;
        hashCode(): number;
        isDaylightSavings(arg0: $Instant): boolean;
        isValidOffset(arg0: $LocalDateTime, arg1: $ZoneOffset): boolean;
        nextTransition(arg0: $Instant): $ZoneOffsetTransition;
        previousTransition(arg0: $Instant): $ZoneOffsetTransition;
        toString(): string;
    }

    export class $ZoneOffsetTransition implements $Comparable<$ZoneOffsetTransition>, $Serializable {
        get dateTimeAfter(): $LocalDateTime;
        getDateTimeAfter(): $LocalDateTime;
        get dateTimeBefore(): $LocalDateTime;
        getDateTimeBefore(): $LocalDateTime;
        get duration(): $Duration;
        getDuration(): $Duration;
        get instant(): $Instant;
        getInstant(): $Instant;
        get offsetAfter(): $ZoneOffset;
        getOffsetAfter(): $ZoneOffset;
        get offsetBefore(): $ZoneOffset;
        getOffsetBefore(): $ZoneOffset;
        get gap(): boolean;
        isGap(): boolean;
        get overlap(): boolean;
        isOverlap(): boolean;
        static of(arg0: $LocalDateTime, arg1: $ZoneOffset, arg2: $ZoneOffset): $ZoneOffsetTransition;
        compareTo(arg0: $ZoneOffsetTransition): number;
        equals(arg0: unknown): boolean;
        hashCode(): number;
        isValidOffset(arg0: $ZoneOffset): boolean;
        toEpochSecond(): number;
        toString(): string;
    }

    export class $ZoneOffsetTransitionRule implements $Serializable {
        get dayOfMonthIndicator(): number;
        getDayOfMonthIndicator(): number;
        get dayOfWeek(): $DayOfWeek;
        getDayOfWeek(): $DayOfWeek;
        get localTime(): $LocalTime;
        getLocalTime(): $LocalTime;
        get month(): $Month;
        getMonth(): $Month;
        get offsetAfter(): $ZoneOffset;
        getOffsetAfter(): $ZoneOffset;
        get offsetBefore(): $ZoneOffset;
        getOffsetBefore(): $ZoneOffset;
        get standardOffset(): $ZoneOffset;
        getStandardOffset(): $ZoneOffset;
        get timeDefinition(): $ZoneOffsetTransitionRule$TimeDefinition;
        getTimeDefinition(): $ZoneOffsetTransitionRule$TimeDefinition;
        get midnightEndOfDay(): boolean;
        isMidnightEndOfDay(): boolean;
        static of(
            arg0: $Month_,
            arg1: number,
            arg2: $DayOfWeek_,
            arg3: $LocalTime,
            arg4: boolean,
            arg5: $ZoneOffsetTransitionRule$TimeDefinition_,
            arg6: $ZoneOffset,
            arg7: $ZoneOffset,
            arg8: $ZoneOffset
        ): $ZoneOffsetTransitionRule;
        createTransition(arg0: number): $ZoneOffsetTransition;
        equals(arg0: unknown): boolean;
        hashCode(): number;
        toString(): string;
    }

    export class $ZoneOffsetTransitionRule$TimeDefinition {
        static STANDARD: $ZoneOffsetTransitionRule$TimeDefinition;
        static UTC: $ZoneOffsetTransitionRule$TimeDefinition;
        static WALL: $ZoneOffsetTransitionRule$TimeDefinition;
        name(): string;
        ordinal(): number;
        toString(): string;
        static values(): $ZoneOffsetTransitionRule$TimeDefinition[];
        static valueOf(name: string): $ZoneOffsetTransitionRule$TimeDefinition;
    }
    export interface $ZoneOffsetTransitionRule$TimeDefinition extends $Enum<$ZoneOffsetTransitionRule$TimeDefinition> {}

    export type $ZoneOffsetTransitionRule$TimeDefinition_ = $ZoneOffsetTransitionRule$TimeDefinition | "STANDARD" | "UTC" | "WALL";
}
