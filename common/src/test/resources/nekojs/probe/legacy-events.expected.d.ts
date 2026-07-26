import { $LegacyProbeCompatibilityTest$SampleEvent } from "java:com/tkisor/nekojs/probe";
import { $Serializable, $InputStream, $Closeable, $OutputStream, $Flushable, $PrintWriter, $Writer, $PrintStream, $FilterOutputStream, $File, $FilenameFilter, $FileFilter, $Reader } from "java:java/io";
import { $String, $Comparable, $CharSequence, $AutoCloseable, $Runnable, $Integer, $Number, $Enum, $Class, $ClassLoader, $Iterable, $Package, $NamedPackage, $Module, $Runtime$Version, $Appendable, $Readable, $ModuleLayer, $Long, $Cloneable, $Character, $Thread, $StackTraceElement, $ThreadGroup, $Thread$UncaughtExceptionHandler, $Throwable, $Thread$Builder$OfPlatform, $Thread$Builder, $Thread$Builder$OfVirtual, $Thread$State, $ModuleLayer$Controller, $Boolean, $StringBuffer, $AbstractStringBuilder, $Enum$EnumDesc, $Double } from "java:java/lang";
import { $Annotation } from "java:java/lang/annotation";
import { $Constable, $ConstantDesc, $MethodTypeDesc, $ClassDesc, $MethodHandleDesc, $DirectMethodHandleDesc, $DirectMethodHandleDesc$Kind, $DynamicConstantDesc } from "java:java/lang/constant";
import { $MemorySegment, $MemorySegment$Scope, $ValueLayout$OfLong, $ValueLayout, $MemoryLayout, $MemoryLayout$PathElement, $SequenceLayout, $PaddingLayout, $StructLayout, $GroupLayout, $UnionLayout, $AddressLayout, $ValueLayout$OfByte, $ValueLayout$OfBoolean, $ValueLayout$OfChar, $ValueLayout$OfShort, $ValueLayout$OfInt, $ValueLayout$OfFloat, $ValueLayout$OfDouble, $Arena, $SegmentAllocator } from "java:java/lang/foreign";
import { $TypeDescriptor$OfField, $TypeDescriptor, $MethodHandles$Lookup, $MethodHandleInfo, $MethodType, $TypeDescriptor$OfMethod, $MethodHandle, $MethodHandles$Lookup$ClassOption, $VarHandle, $VarHandle$VarHandleDesc, $VarHandle$AccessMode } from "java:java/lang/invoke";
import { $ModuleDescriptor, $ModuleDescriptor$Modifier, $ModuleDescriptor$Version, $ModuleDescriptor$Exports, $ModuleDescriptor$Exports$Modifier, $ModuleDescriptor$Opens, $ModuleDescriptor$Opens$Modifier, $ModuleDescriptor$Provides, $ModuleDescriptor$Requires, $ModuleDescriptor$Requires$Modifier, $ModuleDescriptor$Builder, $Configuration, $ModuleFinder, $ModuleReference, $ModuleReader, $ResolvedModule } from "java:java/lang/module";
import { $GenericDeclaration, $AnnotatedElement, $TypeVariable, $Type, $AnnotatedType, $AccessFlag, $AccessFlag$Location, $ClassFileFormatVersion, $Method, $Executable, $AccessibleObject, $Member, $Parameter, $Constructor, $Field, $RecordComponent } from "java:java/lang/reflect";
import { $URL, $URI, $URLStreamHandler, $URLConnection, $FileNameMap, $ContentHandlerFactory, $ContentHandler, $Proxy, $Proxy$Type, $SocketAddress, $URLStreamHandlerFactory } from "java:java/net";
import { $ByteBuffer, $Buffer, $ByteOrder, $CharBuffer, $ShortBuffer, $IntBuffer, $LongBuffer, $FloatBuffer, $DoubleBuffer, $MappedByteBuffer } from "java:java/nio";
import { $SeekableByteChannel, $ByteChannel, $ReadableByteChannel, $Channel, $WritableByteChannel, $FileChannel, $InterruptibleChannel, $GatheringByteChannel, $ScatteringByteChannel, $FileLock, $FileChannel$MapMode, $AsynchronousFileChannel, $AsynchronousChannel, $CompletionHandler } from "java:java/nio/channels";
import { $AbstractInterruptibleChannel } from "java:java/nio/channels/spi";
import { $Charset, $CharsetDecoder, $CodingErrorAction, $CoderResult, $CharsetEncoder } from "java:java/nio/charset";
import { $Path, $Watchable, $WatchKey, $WatchEvent, $WatchEvent$Kind, $WatchService, $WatchEvent$Modifier, $LinkOption, $OpenOption, $CopyOption, $FileSystem, $AccessMode, $DirectoryStream, $DirectoryStream$Filter, $FileStore, $PathMatcher } from "java:java/nio/file";
import { $FileAttribute, $UserPrincipalLookupService, $UserPrincipal, $GroupPrincipal } from "java:java/nio/file/attribute";
import { $FileSystemProvider } from "java:java/nio/file/spi";
import { $Permission, $Guard, $PermissionCollection, $Principal, $ProtectionDomain, $CodeSource, $PublicKey, $Key, $Provider, $Provider$Service, $CodeSigner, $Timestamp } from "java:java/security";
import { $Certificate, $CertPath } from "java:java/security/cert";
import { $ParsePosition, $Format, $FieldPosition, $Format$Field, $AttributedCharacterIterator$Attribute, $AttributedCharacterIterator, $CharacterIterator } from "java:java/text";
import { $Duration, $Instant, $Clock, $InstantSource, $ZoneId, $ZoneOffset, $LocalDateTime, $LocalTime, $OffsetTime, $LocalDate, $Month, $Period, $ZonedDateTime, $DayOfWeek, $OffsetDateTime } from "java:java/time";
import { $ChronoLocalDateTime, $Chronology, $ChronoLocalDate, $ChronoPeriod, $IsoChronology, $AbstractChronology, $Era, $IsoEra, $ChronoZonedDateTime } from "java:java/time/chrono";
import { $ResolverStyle, $TextStyle, $DateTimeFormatter, $FormatStyle, $DecimalStyle } from "java:java/time/format";
import { $TemporalAmount, $TemporalUnit, $Temporal, $TemporalAccessor, $TemporalField, $ValueRange, $TemporalQuery, $TemporalAdjuster, $ChronoUnit, $ChronoField } from "java:java/time/temporal";
import { $ZoneRules, $ZoneOffsetTransition, $ZoneOffsetTransitionRule, $ZoneOffsetTransitionRule$TimeDefinition } from "java:java/time/zone";
import { $Iterator, $Spliterator, $Comparator, $OptionalInt, $PrimitiveIterator$OfInt, $PrimitiveIterator, $Optional, $Enumeration, $Map, $Collection, $Set, $Map$Entry, $List, $SequencedCollection, $ListIterator, $Locale, $Locale$Category, $Locale$LanguageRange, $Locale$FilteringMode, $Locale$IsoCountryCode, $SortedMap, $SequencedMap, $SequencedSet, $Properties, $Hashtable, $Dictionary, $Date, $OptionalLong, $PrimitiveIterator$OfLong, $Spliterator$OfLong, $Spliterator$OfPrimitive, $OptionalDouble, $PrimitiveIterator$OfDouble, $Spliterator$OfDouble, $DoubleSummaryStatistics, $LongSummaryStatistics, $Spliterator$OfInt, $IntSummaryStatistics } from "java:java/util";
import { $TimeUnit, $ThreadFactory, $Future, $Future$State, $ExecutorService, $Executor, $Callable } from "java:java/util/concurrent";
import { $Consumer, $Function, $ToIntFunction, $ToLongFunction, $ToDoubleFunction, $Supplier, $IntConsumer, $IntSupplier, $IntFunction, $BiConsumer, $BinaryOperator, $BiFunction, $Predicate, $UnaryOperator, $LongConsumer, $LongSupplier, $LongUnaryOperator, $ObjLongConsumer, $LongPredicate, $LongFunction, $LongBinaryOperator, $LongToIntFunction, $DoubleConsumer, $DoubleSupplier, $DoubleUnaryOperator, $ObjDoubleConsumer, $DoublePredicate, $DoubleFunction, $DoubleBinaryOperator, $DoubleToIntFunction, $DoubleToLongFunction, $LongToDoubleFunction, $IntUnaryOperator, $ObjIntConsumer, $IntPredicate, $IntBinaryOperator, $IntToLongFunction, $IntToDoubleFunction } from "java:java/util/function";
import { $IntStream, $BaseStream, $Stream, $Collector, $Collector$Characteristics, $Stream$Builder, $LongStream, $LongStream$Builder, $DoubleStream, $DoubleStream$Builder, $DoubleStream$DoubleMapMultiConsumer, $LongStream$LongMapMultiConsumer, $IntStream$Builder, $IntStream$IntMapMultiConsumer } from "java:java/util/stream";

declare module "@side-only/server/events" {
}

export {};

declare global {
    namespace ServerEvents {
        function sample(handler: ((event: $LegacyProbeCompatibilityTest$SampleEvent) => void)): void;
    }

}
