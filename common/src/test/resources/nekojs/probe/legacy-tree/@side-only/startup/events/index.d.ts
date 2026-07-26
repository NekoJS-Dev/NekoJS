



        function cancellable(handler: ((event: $LegacyProbeFixture$SampleCancellableEvent) => void)): void;
        function dispatch(extra: $LegacyProbeFixture$SampleDispatchKey, handler: ((event: $LegacyProbeFixture$SampleDispatchEvent) => void)): void;
        function dispatch(handler: ((event: $LegacyProbeFixture$SampleDispatchEvent) => void)): void;
    namespace SampleEvents {
    }
declare global {
declare module "@side-only/startup/events" {
export {};
import { $AbstractChronology, $ChronoLocalDate, $ChronoLocalDateTime, $ChronoPeriod, $ChronoZonedDateTime, $Chronology, $Era, $IsoChronology, $IsoEra } from "java:java/time/chrono";
import { $AbstractInterruptibleChannel } from "java:java/nio/channels/spi";
import { $AbstractStringBuilder, $Appendable, $AutoCloseable, $Boolean, $CharSequence, $Character, $Class, $ClassLoader, $Cloneable, $Comparable, $Double, $Enum, $Enum$EnumDesc, $Integer, $Iterable, $Long, $Module, $ModuleLayer, $ModuleLayer$Controller, $NamedPackage, $Number, $Package, $Readable, $Runnable, $Runtime$Version, $StackTraceElement, $String, $StringBuffer, $Thread, $Thread$Builder, $Thread$Builder$OfPlatform, $Thread$Builder$OfVirtual, $Thread$State, $Thread$UncaughtExceptionHandler, $ThreadGroup, $Throwable } from "java:java/lang";
import { $AccessFlag, $AccessFlag$Location, $AccessibleObject, $AnnotatedElement, $AnnotatedType, $ClassFileFormatVersion, $Constructor, $Executable, $Field, $GenericDeclaration, $Member, $Method, $Parameter, $RecordComponent, $Type, $TypeVariable } from "java:java/lang/reflect";
import { $AccessMode, $CopyOption, $DirectoryStream, $DirectoryStream$Filter, $FileStore, $FileSystem, $LinkOption, $OpenOption, $Path, $PathMatcher, $WatchEvent, $WatchEvent$Kind, $WatchEvent$Modifier, $WatchKey, $WatchService, $Watchable } from "java:java/nio/file";
import { $AddressLayout, $Arena, $GroupLayout, $MemoryLayout, $MemoryLayout$PathElement, $MemorySegment, $MemorySegment$Scope, $PaddingLayout, $SegmentAllocator, $SequenceLayout, $StructLayout, $UnionLayout, $ValueLayout, $ValueLayout$OfBoolean, $ValueLayout$OfByte, $ValueLayout$OfChar, $ValueLayout$OfDouble, $ValueLayout$OfFloat, $ValueLayout$OfInt, $ValueLayout$OfLong, $ValueLayout$OfShort } from "java:java/lang/foreign";
import { $Annotation } from "java:java/lang/annotation";
import { $AsynchronousChannel, $AsynchronousFileChannel, $ByteChannel, $Channel, $CompletionHandler, $FileChannel, $FileChannel$MapMode, $FileLock, $GatheringByteChannel, $InterruptibleChannel, $ReadableByteChannel, $ScatteringByteChannel, $SeekableByteChannel, $WritableByteChannel } from "java:java/nio/channels";
import { $AttributedCharacterIterator, $AttributedCharacterIterator$Attribute, $CharacterIterator, $FieldPosition, $Format, $Format$Field, $ParsePosition } from "java:java/text";
import { $BaseStream, $Collector, $Collector$Characteristics, $DoubleStream, $DoubleStream$Builder, $DoubleStream$DoubleMapMultiConsumer, $IntStream, $IntStream$Builder, $IntStream$IntMapMultiConsumer, $LongStream, $LongStream$Builder, $LongStream$LongMapMultiConsumer, $Stream, $Stream$Builder } from "java:java/util/stream";
import { $BiConsumer, $BiFunction, $BinaryOperator, $Consumer, $DoubleBinaryOperator, $DoubleConsumer, $DoubleFunction, $DoublePredicate, $DoubleSupplier, $DoubleToIntFunction, $DoubleToLongFunction, $DoubleUnaryOperator, $Function, $IntBinaryOperator, $IntConsumer, $IntFunction, $IntPredicate, $IntSupplier, $IntToDoubleFunction, $IntToLongFunction, $IntUnaryOperator, $LongBinaryOperator, $LongConsumer, $LongFunction, $LongPredicate, $LongSupplier, $LongToDoubleFunction, $LongToIntFunction, $LongUnaryOperator, $ObjDoubleConsumer, $ObjIntConsumer, $ObjLongConsumer, $Predicate, $Supplier, $ToDoubleFunction, $ToIntFunction, $ToLongFunction, $UnaryOperator } from "java:java/util/function";
import { $Buffer, $ByteBuffer, $ByteOrder, $CharBuffer, $DoubleBuffer, $FloatBuffer, $IntBuffer, $LongBuffer, $MappedByteBuffer, $ShortBuffer } from "java:java/nio";
import { $Callable, $Executor, $ExecutorService, $Future, $Future$State, $ThreadFactory, $TimeUnit } from "java:java/util/concurrent";
import { $CertPath, $Certificate } from "java:java/security/cert";
import { $Charset, $CharsetDecoder, $CharsetEncoder, $CoderResult, $CodingErrorAction } from "java:java/nio/charset";
import { $ChronoField, $ChronoUnit, $Temporal, $TemporalAccessor, $TemporalAdjuster, $TemporalAmount, $TemporalField, $TemporalQuery, $TemporalUnit, $ValueRange } from "java:java/time/temporal";
import { $ClassDesc, $Constable, $ConstantDesc, $DirectMethodHandleDesc, $DirectMethodHandleDesc$Kind, $DynamicConstantDesc, $MethodHandleDesc, $MethodTypeDesc } from "java:java/lang/constant";
import { $Clock, $DayOfWeek, $Duration, $Instant, $InstantSource, $LocalDate, $LocalDateTime, $LocalTime, $Month, $OffsetDateTime, $OffsetTime, $Period, $ZoneId, $ZoneOffset, $ZonedDateTime } from "java:java/time";
import { $Closeable, $File, $FileFilter, $FilenameFilter, $FilterOutputStream, $Flushable, $InputStream, $OutputStream, $PrintStream, $PrintWriter, $Reader, $Serializable, $Writer } from "java:java/io";
import { $CodeSigner, $CodeSource, $Guard, $Key, $Permission, $PermissionCollection, $Principal, $ProtectionDomain, $Provider, $Provider$Service, $PublicKey, $Timestamp } from "java:java/security";
import { $Collection, $Comparator, $Date, $Dictionary, $DoubleSummaryStatistics, $Enumeration, $Hashtable, $IntSummaryStatistics, $Iterator, $List, $ListIterator, $Locale, $Locale$Category, $Locale$FilteringMode, $Locale$IsoCountryCode, $Locale$LanguageRange, $LongSummaryStatistics, $Map, $Map$Entry, $Optional, $OptionalDouble, $OptionalInt, $OptionalLong, $PrimitiveIterator, $PrimitiveIterator$OfDouble, $PrimitiveIterator$OfInt, $PrimitiveIterator$OfLong, $Properties, $SequencedCollection, $SequencedMap, $SequencedSet, $Set, $SortedMap, $Spliterator, $Spliterator$OfDouble, $Spliterator$OfInt, $Spliterator$OfLong, $Spliterator$OfPrimitive } from "java:java/util";
import { $Configuration, $ModuleDescriptor, $ModuleDescriptor$Builder, $ModuleDescriptor$Exports, $ModuleDescriptor$Exports$Modifier, $ModuleDescriptor$Modifier, $ModuleDescriptor$Opens, $ModuleDescriptor$Opens$Modifier, $ModuleDescriptor$Provides, $ModuleDescriptor$Requires, $ModuleDescriptor$Requires$Modifier, $ModuleDescriptor$Version, $ModuleFinder, $ModuleReader, $ModuleReference, $ResolvedModule } from "java:java/lang/module";
import { $ContentHandler, $ContentHandlerFactory, $FileNameMap, $Proxy, $Proxy$Type, $SocketAddress, $URI, $URL, $URLConnection, $URLStreamHandler, $URLStreamHandlerFactory } from "java:java/net";
import { $DateTimeFormatter, $DecimalStyle, $FormatStyle, $ResolverStyle, $TextStyle } from "java:java/time/format";
import { $FileAttribute, $GroupPrincipal, $UserPrincipal, $UserPrincipalLookupService } from "java:java/nio/file/attribute";
import { $FileSystemProvider } from "java:java/nio/file/spi";
import { $LegacyProbeFixture$SampleCancellableEvent, $LegacyProbeFixture$SampleDispatchEvent, $LegacyProbeFixture$SampleDispatchKey } from "java:com/tkisor/nekojs/probe";
import { $MethodHandle, $MethodHandleInfo, $MethodHandles$Lookup, $MethodHandles$Lookup$ClassOption, $MethodType, $TypeDescriptor, $TypeDescriptor$OfField, $TypeDescriptor$OfMethod, $VarHandle, $VarHandle$AccessMode, $VarHandle$VarHandleDesc } from "java:java/lang/invoke";
import { $ZoneOffsetTransition, $ZoneOffsetTransitionRule, $ZoneOffsetTransitionRule$TimeDefinition, $ZoneRules } from "java:java/time/zone";
}
}