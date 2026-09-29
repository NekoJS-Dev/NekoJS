import { $RecipeBuilder, $RecipeCreationContext, $RecipeEntryJS, $RecipeFilter, $RecipeJsonBuilder, $RecipeJsonValue, $RecipeLifecycleContext } from "java:com/tkisor/nekojs/api/recipe";
import { $RecipeFieldDefinition, $RecipeFieldKind, $RecipeFieldRole, $RecipeTypeDefinition, $RecipeTypeDefinitionRegistry, $RecipeTypeDefinitionRegistry$Builder } from "java:com/tkisor/nekojs/api/recipe/definition";
import { $KeyBindEvents$KeyBindEventJS } from "java:com/tkisor/nekojs/bindings/event/client";
import { $RecipeRegistryProxy } from "java:com/tkisor/nekojs/wrapper";
import { $GoalRegistry$GoalBuilderJS } from "java:com/tkisor/nekojs/wrapper/entity";
import { $BlockBrokenEventJS } from "java:com/tkisor/nekojs/wrapper/event/block";
import { $ClientTickEventJS } from "java:com/tkisor/nekojs/wrapper/event/client";
import { $EntityJoinLevelEventJS, $EntityLeaveLevelEventJS, $EntityTickEventJS, $GoalRegisterEventJS, $LivingDamageEventJS, $LivingDeathEventJS, $LivingDropsEventJS, $MobFinalizeSpawnEventJS } from "java:com/tkisor/nekojs/wrapper/event/entity";
import { $ItemDroppedEventJS, $ItemEntityPickupEventJS, $ItemRightClickEventJS, $ItemTooltipEventJS, $ItemUseFinishedEventJS, $PlayerEntityInteractEventJS } from "java:com/tkisor/nekojs/wrapper/event/item";
import { $LevelEventJS } from "java:com/tkisor/nekojs/wrapper/event/level";
import { $InventoryChangedEventJS, $PlayerAdvancementEventJS, $PlayerChangedDimensionEventJS, $PlayerCloneEventJS, $PlayerContainerEventJS, $PlayerCraftedEventJS, $PlayerDestroyItemEventJS, $PlayerEntityInteractEventJS, $PlayerLifecycleEventJS, $PlayerRespawnEventJS, $PlayerTickEventJS, $ServerChatEventJS } from "java:com/tkisor/nekojs/wrapper/event/player";
import { $BlockModificationEventJS, $BlockModificationJS, $CommandRegistryEventJS, $DatapackSyncEventJS, $ItemModificationComponents$FoodSpec, $ItemModificationComponents$ToolSpec, $ItemModificationEventJS, $ItemModificationJS, $LootTableLoadEventJS, $RecipeEventJS, $ServerLifecycleEventJS, $ServerTickEventJS, $TagUpdatedEventJS } from "java:com/tkisor/nekojs/wrapper/event/server";
import { $RecipeViewerCategoryListJS, $RecipeViewerEntryListJS, $RecipeViewerInformationJS, $RecipeViewerRecipeListJS } from "java:com/tkisor/nekojs/wrapper/viewer";
import { $BufferedReader, $Closeable, $DataInput, $DataOutput, $File, $FileFilter, $FilenameFilter, $FilterOutputStream, $Flushable, $InputStream, $OutputStream, $PrintStream, $PrintWriter, $Reader, $Serializable, $Writer } from "java:java/io";
import { $AbstractStringBuilder, $Appendable, $AutoCloseable, $Boolean, $Byte, $CharSequence, $Character, $Class, $ClassLoader, $Cloneable, $Comparable, $Double, $Enum, $Enum$EnumDesc, $Exception, $Float, $IllegalStateException, $Integer, $Iterable, $Long, $Module, $ModuleLayer, $ModuleLayer$Controller, $NamedPackage, $Number, $Package, $Readable, $Record, $Runnable, $Runtime$Version, $RuntimeException, $Short, $StackTraceElement, $String, $StringBuffer, $StringBuilder, $Thread, $Thread$Builder, $Thread$Builder$OfPlatform, $Thread$Builder$OfVirtual, $Thread$State, $Thread$UncaughtExceptionHandler, $ThreadGroup, $Throwable, $Void } from "java:java/lang";
import { $Annotation } from "java:java/lang/annotation";
import { $ClassDesc, $Constable, $ConstantDesc, $DirectMethodHandleDesc, $DirectMethodHandleDesc$Kind, $DynamicConstantDesc, $MethodHandleDesc, $MethodTypeDesc } from "java:java/lang/constant";
import { $AddressLayout, $Arena, $GroupLayout, $MemoryLayout, $MemoryLayout$PathElement, $MemorySegment, $MemorySegment$Scope, $PaddingLayout, $SegmentAllocator, $SequenceLayout, $StructLayout, $UnionLayout, $ValueLayout, $ValueLayout$OfBoolean, $ValueLayout$OfByte, $ValueLayout$OfChar, $ValueLayout$OfDouble, $ValueLayout$OfFloat, $ValueLayout$OfInt, $ValueLayout$OfLong, $ValueLayout$OfShort } from "java:java/lang/foreign";
import { $MethodHandle, $MethodHandleInfo, $MethodHandles$Lookup, $MethodHandles$Lookup$ClassOption, $MethodType, $TypeDescriptor, $TypeDescriptor$OfField, $TypeDescriptor$OfMethod, $VarHandle, $VarHandle$AccessMode, $VarHandle$VarHandleDesc } from "java:java/lang/invoke";
import { $Configuration, $ModuleDescriptor, $ModuleDescriptor$Builder, $ModuleDescriptor$Exports, $ModuleDescriptor$Exports$Modifier, $ModuleDescriptor$Modifier, $ModuleDescriptor$Opens, $ModuleDescriptor$Opens$Modifier, $ModuleDescriptor$Provides, $ModuleDescriptor$Requires, $ModuleDescriptor$Requires$Modifier, $ModuleDescriptor$Version, $ModuleFinder, $ModuleReader, $ModuleReference, $ResolvedModule } from "java:java/lang/module";
import { $AccessFlag, $AccessFlag$Location, $AccessibleObject, $AnnotatedElement, $AnnotatedType, $ClassFileFormatVersion, $Constructor, $Executable, $Field, $GenericDeclaration, $Member, $Method, $Parameter, $RecordComponent, $Type, $TypeVariable } from "java:java/lang/reflect";
import { $RoundingMode } from "java:java/math";
import { $ContentHandler, $ContentHandlerFactory, $FileNameMap, $InetAddress, $InetSocketAddress, $InterfaceAddress, $NetworkInterface, $Proxy, $Proxy$Type, $SocketAddress, $URI, $URL, $URLConnection, $URLStreamHandler, $URLStreamHandlerFactory } from "java:java/net";
import { $Buffer, $ByteBuffer, $ByteOrder, $CharBuffer, $DoubleBuffer, $FloatBuffer, $IntBuffer, $LongBuffer, $MappedByteBuffer, $ShortBuffer } from "java:java/nio";
import { $AsynchronousChannel, $AsynchronousFileChannel, $ByteChannel, $Channel, $CompletionHandler, $FileChannel, $FileChannel$MapMode, $FileLock, $GatheringByteChannel, $InterruptibleChannel, $ReadableByteChannel, $ScatteringByteChannel, $SeekableByteChannel, $WritableByteChannel } from "java:java/nio/channels";
import { $AbstractInterruptibleChannel } from "java:java/nio/channels/spi";
import { $Charset, $CharsetDecoder, $CharsetEncoder, $CoderResult, $CodingErrorAction } from "java:java/nio/charset";
import { $AccessMode, $CopyOption, $DirectoryStream, $DirectoryStream$Filter, $FileStore, $FileSystem, $LinkOption, $OpenOption, $Path, $PathMatcher, $WatchEvent, $WatchEvent$Kind, $WatchEvent$Modifier, $WatchKey, $WatchService, $Watchable } from "java:java/nio/file";
import { $FileAttribute, $FileTime, $GroupPrincipal, $UserPrincipal, $UserPrincipalLookupService } from "java:java/nio/file/attribute";
import { $FileSystemProvider } from "java:java/nio/file/spi";
import { $AsymmetricKey, $CodeSigner, $CodeSource, $DEREncodable, $Guard, $Key, $KeyPair, $Permission, $PermissionCollection, $Principal, $PrivateKey, $ProtectionDomain, $Provider, $Provider$Service, $PublicKey, $Timestamp } from "java:java/security";
import { $CertPath, $Certificate } from "java:java/security/cert";
import { $AlgorithmParameterSpec } from "java:java/security/spec";
import { $AttributedCharacterIterator, $AttributedCharacterIterator$Attribute, $CharacterIterator, $DateFormat, $DateFormatSymbols, $DecimalFormat, $DecimalFormatSymbols, $FieldPosition, $Format, $Format$Field, $NumberFormat, $NumberFormat$Style, $ParsePosition, $SimpleDateFormat } from "java:java/text";
import { $Clock, $DayOfWeek, $Duration, $Instant, $InstantSource, $LocalDate, $LocalDateTime, $LocalTime, $Month, $OffsetDateTime, $OffsetTime, $Period, $ZoneId, $ZoneOffset, $ZonedDateTime } from "java:java/time";
import { $AbstractChronology, $ChronoLocalDate, $ChronoLocalDateTime, $ChronoPeriod, $ChronoZonedDateTime, $Chronology, $Era, $IsoChronology, $IsoEra } from "java:java/time/chrono";
import { $DateTimeFormatter, $DecimalStyle, $FormatStyle, $ResolverStyle, $TextStyle } from "java:java/time/format";
import { $ChronoField, $ChronoUnit, $Temporal, $TemporalAccessor, $TemporalAdjuster, $TemporalAmount, $TemporalField, $TemporalQuery, $TemporalUnit, $ValueRange } from "java:java/time/temporal";
import { $ZoneOffsetTransition, $ZoneOffsetTransitionRule, $ZoneOffsetTransitionRule$TimeDefinition, $ZoneRules } from "java:java/time/zone";
import { $AbstractCollection, $AbstractList, $AbstractSet, $ArrayList, $BitSet, $Calendar, $Collection, $Comparator, $Currency, $Date, $Dictionary, $DoubleSummaryStatistics, $EnumSet, $Enumeration, $Hashtable, $IntSummaryStatistics, $Iterator, $List, $ListIterator, $Locale, $Locale$Category, $Locale$FilteringMode, $Locale$IsoCountryCode, $Locale$LanguageRange, $LongSummaryStatistics, $Map, $Map$Entry, $Optional, $OptionalDouble, $OptionalInt, $OptionalLong, $PrimitiveIterator, $PrimitiveIterator$OfDouble, $PrimitiveIterator$OfInt, $PrimitiveIterator$OfLong, $Properties, $RandomAccess, $SequencedCollection, $SequencedMap, $SequencedSet, $Set, $SortedMap, $Spliterator, $Spliterator$OfDouble, $Spliterator$OfInt, $Spliterator$OfLong, $Spliterator$OfPrimitive, $TimeZone, $UUID } from "java:java/util";
import { $Callable, $CompletableFuture, $CompletionStage, $Executor, $ExecutorService, $Future, $Future$State, $ThreadFactory, $TimeUnit } from "java:java/util/concurrent";
import { $BiConsumer, $BiFunction, $BiPredicate, $BinaryOperator, $BooleanSupplier, $Consumer, $DoubleBinaryOperator, $DoubleConsumer, $DoubleFunction, $DoublePredicate, $DoubleSupplier, $DoubleToIntFunction, $DoubleToLongFunction, $DoubleUnaryOperator, $Function, $IntBinaryOperator, $IntConsumer, $IntFunction, $IntPredicate, $IntSupplier, $IntToDoubleFunction, $IntToLongFunction, $IntUnaryOperator, $LongBinaryOperator, $LongConsumer, $LongFunction, $LongPredicate, $LongSupplier, $LongToDoubleFunction, $LongToIntFunction, $LongUnaryOperator, $ObjDoubleConsumer, $ObjIntConsumer, $ObjLongConsumer, $Predicate, $Supplier, $ToDoubleFunction, $ToIntFunction, $ToLongFunction, $UnaryOperator } from "java:java/util/function";
import { $BaseStream, $Collector, $Collector$Characteristics, $DoubleStream, $DoubleStream$Builder, $DoubleStream$DoubleMapMultiConsumer, $Gatherer, $Gatherer$Downstream, $Gatherer$Integrator, $Gatherer$Integrator$Greedy, $IntStream, $IntStream$Builder, $IntStream$IntMapMultiConsumer, $LongStream, $LongStream$Builder, $LongStream$LongMapMultiConsumer, $Stream, $Stream$Builder } from "java:java/util/stream";
import { $ZipConstants, $ZipEntry, $ZipFile } from "java:java/util/zip";
import { $ChatFormatting, $CrashReport, $CrashReportCategory, $CrashReportDetail, $ReportType, $ReportedException, $SystemReport } from "java:net/minecraft";
import { $Advancement, $AdvancementHolder, $AdvancementNode, $AdvancementProgress, $AdvancementRequirements, $AdvancementRewards, $AdvancementTree, $AdvancementTree$Listener, $AdvancementType, $CriterionProgress, $DisplayInfo } from "java:net/minecraft/advancements";
import { $Criterion, $CriterionTrigger } from "java:net/minecraft/advancements/triggers";
import { $KeyMapping, $KeyMapping$Category } from "java:net/minecraft/client";
import { $InputWithModifiers, $KeyEvent, $MouseButtonEvent, $MouseButtonInfo } from "java:net/minecraft/client/input";
import { $CacheableFunction, $CommandBuildContext, $CommandResultCallback, $CommandSigningContext, $CommandSource, $CommandSourceStack, $Commands, $Commands$ParseFunction, $ExecutionCommandSource, $SharedSuggestionProvider, $SharedSuggestionProvider$ElementSuggestionType, $SharedSuggestionProvider$TextCoordinates } from "java:net/minecraft/commands";
import { $ArgumentSignatures, $ArgumentSignatures$Entry, $ArgumentSignatures$Signer, $EntityAnchorArgument$Anchor, $NbtPathArgument$NbtPath } from "java:net/minecraft/commands/arguments";
import { $EntitySelector } from "java:net/minecraft/commands/arguments/selector";
import { $CommandQueueEntry, $EntryAction, $ExecutionContext, $Frame, $Frame$FrameControl, $TraceCallbacks, $UnboundEntryAction } from "java:net/minecraft/commands/execution";
import { $CommandFunction, $InstantiatedFunction } from "java:net/minecraft/commands/functions";
import { $AxisCycle, $BlockPos, $BlockPos$MutableBlockPos, $BlockPos$TraversalNodeStatus, $ClientAsset, $ClientAsset$ResourceTexture, $ClientAsset$Texture, $Direction, $Direction$Axis, $Direction$AxisDirection, $Direction$Plane, $Direction8, $GlobalPos, $Holder, $Holder$Kind, $Holder$Reference, $HolderGetter, $HolderGetter$Provider, $HolderLookup, $HolderLookup$Provider, $HolderLookup$RegistryLookup, $HolderOwner, $HolderSet, $HolderSet$Direct, $HolderSet$ListBacked, $HolderSet$Named, $IdMap, $IdMapper, $LayeredRegistryAccess, $NonNullList, $Position, $RegistrationInfo, $Registry, $Registry$PendingTags, $RegistryAccess, $RegistryAccess$Frozen, $RegistryAccess$RegistryEntry, $SectionPos, $TypedInstance, $Vec3i } from "java:net/minecraft/core";
import { $DataComponentExactPredicate, $DataComponentExactPredicate$Builder, $DataComponentGetter, $DataComponentHolder, $DataComponentInitializers$Initializer, $DataComponentInitializers$SingleComponentInitializer, $DataComponentLookup, $DataComponentMap, $DataComponentMap$Builder, $DataComponentPatch, $DataComponentPatch$Builder, $DataComponentPatch$SplitResult, $DataComponentType, $DataComponentType$Builder, $PatchedDataComponentMap, $TypedDataComponent } from "java:net/minecraft/core/component";
import { $ExplosionParticleInfo, $ParticleOptions, $ParticleType } from "java:net/minecraft/core/particles";
import { $BootstrapContext } from "java:net/minecraft/data/worldgen";
import { $GameTestAssertException, $GameTestAssertPosException, $GameTestEntityBuilder, $GameTestException, $GameTestHelper, $GameTestInstance, $GameTestMobBuilder, $GameTestSequence, $GameTestSequence$Condition, $TestEnvironmentDefinition, $TestEnvironmentDefinition$Activation } from "java:net/minecraft/gametest/framework";
import { $ByteArrayTag, $ByteTag, $CollectionTag, $CompoundTag, $DoubleTag, $EndTag, $FloatTag, $IntArrayTag, $IntTag, $ListTag, $LongArrayTag, $LongTag, $NbtAccounter, $NumericTag, $PrimitiveTag, $ShortTag, $StreamTagVisitor, $StreamTagVisitor$EntryResult, $StreamTagVisitor$ValueResult, $StringTag, $Tag, $TagType, $TagVisitor } from "java:net/minecraft/nbt";
import { $BandwidthDebugMonitor, $ClientboundPacketListener, $Connection, $ConnectionProtocol, $DisconnectionDetails, $FriendlyByteBuf, $HashedPatchMap$HashGenerator, $HashedStack, $PacketListener, $PacketProcessor, $ProtocolInfo, $RegistryFriendlyByteBuf, $ServerboundPacketListener, $TickablePacketListener } from "java:net/minecraft/network";
import { $ChatDecorator, $ChatType, $ChatType$Bound, $ChatTypeDecoration, $ChatTypeDecoration$Parameter, $ClickEvent, $ClickEvent$Action, $Component, $ComponentContents, $FilterMask, $FontDescription, $FontDescription$Resource, $FormattedText, $FormattedText$ContentConsumer, $FormattedText$StyledContentConsumer, $HoverEvent, $HoverEvent$Action, $LastSeenMessages, $LastSeenMessages$Packed, $LastSeenMessages$Update, $MessageSignature, $MessageSignature$Packed, $MessageSignatureCache, $MutableComponent, $OutgoingChatMessage, $PlayerChatMessage, $RemoteChatSession, $RemoteChatSession$Data, $ResolutionContext, $ResolutionContext$Builder, $ResolutionContext$LimitBehavior, $SignableCommand, $SignableCommand$Argument, $SignedMessageBody, $SignedMessageBody$Packed, $SignedMessageChain$Decoder, $SignedMessageLink, $SignedMessageValidator, $Style, $TextColor } from "java:net/minecraft/network/chat";
import { $DataSource } from "java:net/minecraft/network/chat/contents/data";
import { $ObjectInfo } from "java:net/minecraft/network/chat/contents/objects";
import { $NumberFormat, $NumberFormatType } from "java:net/minecraft/network/chat/numbers";
import { $StreamCodec, $StreamCodec$CodecOperation, $StreamDecoder, $StreamEncoder, $StreamMemberEncoder } from "java:net/minecraft/network/codec";
import { $BundleDelimiterPacket, $BundlePacket, $BundlerInfo, $BundlerInfo$Bundler, $Packet, $PacketFlow, $PacketType } from "java:net/minecraft/network/protocol";
import { $ClientCommonPacketListener, $ClientboundClearDialogPacket, $ClientboundCustomPayloadPacket, $ClientboundCustomReportDetailsPacket, $ClientboundDisconnectPacket, $ClientboundKeepAlivePacket, $ClientboundPingPacket, $ClientboundResourcePackPopPacket, $ClientboundResourcePackPushPacket, $ClientboundServerLinksPacket, $ClientboundShowDialogPacket, $ClientboundStoreCookiePacket, $ClientboundTransferPacket, $ClientboundUpdateTagsPacket, $ServerCommonPacketListener, $ServerboundClientInformationPacket, $ServerboundCustomClickActionPacket, $ServerboundCustomPayloadPacket, $ServerboundKeepAlivePacket, $ServerboundPongPacket, $ServerboundResourcePackPacket, $ServerboundResourcePackPacket$Action } from "java:net/minecraft/network/protocol/common";
import { $CustomPacketPayload, $CustomPacketPayload$FallbackProvider, $CustomPacketPayload$Type, $CustomPacketPayload$TypeAndCodec } from "java:net/minecraft/network/protocol/common/custom";
import { $ClientCookiePacketListener, $ClientboundCookieRequestPacket, $ServerCookiePacketListener, $ServerboundCookieResponsePacket } from "java:net/minecraft/network/protocol/cookie";
import { $ClientGamePacketListener, $ClientboundAddEntityPacket, $ClientboundAnimatePacket, $ClientboundAwardStatsPacket, $ClientboundBlockChangedAckPacket, $ClientboundBlockDestructionPacket, $ClientboundBlockEntityDataPacket, $ClientboundBlockEventPacket, $ClientboundBlockUpdatePacket, $ClientboundBossEventPacket, $ClientboundBossEventPacket$Handler, $ClientboundBundlePacket, $ClientboundChangeDifficultyPacket, $ClientboundChunkBatchFinishedPacket, $ClientboundChunkBatchStartPacket, $ClientboundChunksBiomesPacket, $ClientboundChunksBiomesPacket$ChunkBiomeData, $ClientboundClearTitlesPacket, $ClientboundCommandSuggestionsPacket, $ClientboundCommandSuggestionsPacket$Entry, $ClientboundCommandsPacket, $ClientboundCommandsPacket$NodeBuilder, $ClientboundContainerClosePacket, $ClientboundContainerSetContentPacket, $ClientboundContainerSetDataPacket, $ClientboundContainerSetSlotPacket, $ClientboundCooldownPacket, $ClientboundCustomChatCompletionsPacket, $ClientboundCustomChatCompletionsPacket$Action, $ClientboundDamageEventPacket, $ClientboundDebugBlockValuePacket, $ClientboundDebugChunkValuePacket, $ClientboundDebugEntityValuePacket, $ClientboundDebugEventPacket, $ClientboundDebugSamplePacket, $ClientboundDeleteChatPacket, $ClientboundDisguisedChatPacket, $ClientboundEntityEventPacket, $ClientboundEntityPositionSyncPacket, $ClientboundExplodePacket, $ClientboundForgetLevelChunkPacket, $ClientboundGameEventPacket, $ClientboundGameEventPacket$Type, $ClientboundGameRuleValuesPacket, $ClientboundGameTestHighlightPosPacket, $ClientboundHurtAnimationPacket, $ClientboundInitializeBorderPacket, $ClientboundLevelChunkPacketData, $ClientboundLevelChunkPacketData$BlockEntityTagOutput, $ClientboundLevelChunkWithLightPacket, $ClientboundLevelEventPacket, $ClientboundLevelParticlesPacket, $ClientboundLightUpdatePacket, $ClientboundLightUpdatePacketData, $ClientboundLoginPacket, $ClientboundLowDiskSpaceWarningPacket, $ClientboundMapItemDataPacket, $ClientboundMerchantOffersPacket, $ClientboundMountScreenOpenPacket, $ClientboundMoveEntityPacket, $ClientboundMoveMinecartPacket, $ClientboundMoveVehiclePacket, $ClientboundOpenBookPacket, $ClientboundOpenScreenPacket, $ClientboundOpenSignEditorPacket, $ClientboundPlaceGhostRecipePacket, $ClientboundPlayerAbilitiesPacket, $ClientboundPlayerChatPacket, $ClientboundPlayerCombatEndPacket, $ClientboundPlayerCombatEnterPacket, $ClientboundPlayerCombatKillPacket, $ClientboundPlayerInfoRemovePacket, $ClientboundPlayerInfoUpdatePacket, $ClientboundPlayerInfoUpdatePacket$Action, $ClientboundPlayerInfoUpdatePacket$Entry, $ClientboundPlayerLookAtPacket, $ClientboundPlayerPositionPacket, $ClientboundPlayerRotationPacket, $ClientboundProjectilePowerPacket, $ClientboundRecipeBookAddPacket, $ClientboundRecipeBookAddPacket$Entry, $ClientboundRecipeBookRemovePacket, $ClientboundRecipeBookSettingsPacket, $ClientboundRemoveEntitiesPacket, $ClientboundRemoveMobEffectPacket, $ClientboundResetScorePacket, $ClientboundRespawnPacket, $ClientboundRotateHeadPacket, $ClientboundSectionBlocksUpdatePacket, $ClientboundSelectAdvancementsTabPacket, $ClientboundServerDataPacket, $ClientboundSetActionBarTextPacket, $ClientboundSetBorderCenterPacket, $ClientboundSetBorderLerpSizePacket, $ClientboundSetBorderSizePacket, $ClientboundSetBorderWarningDelayPacket, $ClientboundSetBorderWarningDistancePacket, $ClientboundSetCameraPacket, $ClientboundSetChunkCacheCenterPacket, $ClientboundSetChunkCacheRadiusPacket, $ClientboundSetCursorItemPacket, $ClientboundSetDefaultSpawnPositionPacket, $ClientboundSetDisplayObjectivePacket, $ClientboundSetEntityDataPacket, $ClientboundSetEntityLinkPacket, $ClientboundSetEntityMotionPacket, $ClientboundSetEquipmentPacket, $ClientboundSetExperiencePacket, $ClientboundSetHealthPacket, $ClientboundSetHeldSlotPacket, $ClientboundSetObjectivePacket, $ClientboundSetPassengersPacket, $ClientboundSetPlayerInventoryPacket, $ClientboundSetPlayerTeamPacket, $ClientboundSetPlayerTeamPacket$Action, $ClientboundSetPlayerTeamPacket$Parameters, $ClientboundSetScorePacket, $ClientboundSetSimulationDistancePacket, $ClientboundSetSubtitleTextPacket, $ClientboundSetTimePacket, $ClientboundSetTitleTextPacket, $ClientboundSetTitlesAnimationPacket, $ClientboundSoundEntityPacket, $ClientboundSoundPacket, $ClientboundStartConfigurationPacket, $ClientboundStopSoundPacket, $ClientboundSystemChatPacket, $ClientboundTabListPacket, $ClientboundTagQueryPacket, $ClientboundTakeItemEntityPacket, $ClientboundTeleportEntityPacket, $ClientboundTestInstanceBlockStatus, $ClientboundTickingStatePacket, $ClientboundTickingStepPacket, $ClientboundTrackedWaypointPacket, $ClientboundTrackedWaypointPacket$Operation, $ClientboundUpdateAdvancementsPacket, $ClientboundUpdateAttributesPacket, $ClientboundUpdateAttributesPacket$AttributeSnapshot, $ClientboundUpdateMobEffectPacket, $ClientboundUpdateRecipesPacket, $CommonPlayerSpawnInfo, $GameProtocols$Context, $ServerGamePacketListener, $ServerPacketListener, $ServerboundAcceptTeleportationPacket, $ServerboundAttackPacket, $ServerboundBlockEntityTagQueryPacket, $ServerboundChangeDifficultyPacket, $ServerboundChangeGameModePacket, $ServerboundChatAckPacket, $ServerboundChatCommandPacket, $ServerboundChatCommandSignedPacket, $ServerboundChatPacket, $ServerboundChatSessionUpdatePacket, $ServerboundChunkBatchReceivedPacket, $ServerboundClientCommandPacket, $ServerboundClientCommandPacket$Action, $ServerboundClientTickEndPacket, $ServerboundCommandSuggestionPacket, $ServerboundConfigurationAcknowledgedPacket, $ServerboundContainerButtonClickPacket, $ServerboundContainerClickPacket, $ServerboundContainerClosePacket, $ServerboundContainerSlotStateChangedPacket, $ServerboundDebugSubscriptionRequestPacket, $ServerboundEditBookPacket, $ServerboundEntityTagQueryPacket, $ServerboundInteractPacket, $ServerboundJigsawGeneratePacket, $ServerboundLockDifficultyPacket, $ServerboundMovePlayerPacket, $ServerboundMoveVehiclePacket, $ServerboundPaddleBoatPacket, $ServerboundPickItemFromBlockPacket, $ServerboundPickItemFromEntityPacket, $ServerboundPlaceRecipePacket, $ServerboundPlayerAbilitiesPacket, $ServerboundPlayerActionPacket, $ServerboundPlayerActionPacket$Action, $ServerboundPlayerCommandPacket, $ServerboundPlayerCommandPacket$Action, $ServerboundPlayerInputPacket, $ServerboundPlayerLoadedPacket, $ServerboundRecipeBookChangeSettingsPacket, $ServerboundRecipeBookSeenRecipePacket, $ServerboundRenameItemPacket, $ServerboundSeenAdvancementsPacket, $ServerboundSeenAdvancementsPacket$Action, $ServerboundSelectBundleItemPacket, $ServerboundSelectTradePacket, $ServerboundSetBeaconPacket, $ServerboundSetCarriedItemPacket, $ServerboundSetCommandBlockPacket, $ServerboundSetCommandMinecartPacket, $ServerboundSetCreativeModeSlotPacket, $ServerboundSetGameRulePacket, $ServerboundSetGameRulePacket$Entry, $ServerboundSetJigsawBlockPacket, $ServerboundSetStructureBlockPacket, $ServerboundSetTestBlockPacket, $ServerboundSignUpdatePacket, $ServerboundSpectatorActionPacket, $ServerboundSwingPacket, $ServerboundTeleportToEntityPacket, $ServerboundTestInstanceBlockActionPacket, $ServerboundTestInstanceBlockActionPacket$Action, $ServerboundUseItemOnPacket, $ServerboundUseItemPacket, $VecDeltaCodec } from "java:net/minecraft/network/protocol/game";
import { $ClientLoginPacketListener, $ClientboundCustomQueryPacket, $ClientboundHelloPacket, $ClientboundLoginCompressionPacket, $ClientboundLoginDisconnectPacket, $ClientboundLoginFinishedPacket } from "java:net/minecraft/network/protocol/login";
import { $CustomQueryPayload } from "java:net/minecraft/network/protocol/login/custom";
import { $ClientPongPacketListener, $ClientboundPongResponsePacket, $ServerPingPacketListener, $ServerboundPingRequestPacket } from "java:net/minecraft/network/protocol/ping";
import { $ClientStatusPacketListener, $ClientboundStatusResponsePacket, $ServerStatus, $ServerStatus$Favicon, $ServerStatus$Players, $ServerStatus$Version } from "java:net/minecraft/network/protocol/status";
import { $EntityDataAccessor, $EntityDataSerializer, $SyncedDataHolder, $SynchedEntityData, $SynchedEntityData$DataValue } from "java:net/minecraft/network/syncher";
import { $DelegatingOps, $FileToIdConverter, $Identifier, $RegistryOps, $RegistryOps$RegistryInfo, $RegistryOps$RegistryInfoLookup, $ResourceKey } from "java:net/minecraft/resources";
import { $MinecraftServer, $MinecraftServer$MultiplayerScope, $MinecraftServer$ServerResourcePackInfo, $PlayerAdvancements, $PlayerAdvancements$TriggerInstanceKey, $RegistryLayer, $ReloadableServerRegistries$Holder, $ServerAdvancementManager, $ServerFunctionLibrary, $ServerFunctionManager, $ServerInfo, $ServerInterface, $ServerLinks, $ServerLinks$Entry, $ServerLinks$KnownLinkType, $ServerLinks$UntrustedEntry, $ServerScoreboard, $ServerTickRateManager, $Services, $TickTask } from "java:net/minecraft/server";
import { $CustomBossEvent, $CustomBossEvent$Packed, $CustomBossEvents } from "java:net/minecraft/server/bossevents";
import { $DedicatedPlayerList, $DedicatedServer, $DedicatedServerProperties, $Settings, $Settings$MutableValue } from "java:net/minecraft/server/dedicated";
import { $CommonDialogData, $Dialog, $DialogAction, $Input } from "java:net/minecraft/server/dialog";
import { $Action, $Action$ValueGetter } from "java:net/minecraft/server/dialog/action";
import { $DialogBody } from "java:net/minecraft/server/dialog/body";
import { $InputControl } from "java:net/minecraft/server/dialog/input";
import { $ChunkGenerationTask, $ChunkHolder, $ChunkHolder$PlayerProvider, $ChunkMap, $ChunkResult, $ChunkTrackingView, $ClientInformation, $DistanceManager, $FullChunkStatus, $GeneratingChunkMap, $GenerationChunkHolder, $ParticleStatus, $ServerBossEvent, $ServerChunkCache, $ServerEntity, $ServerEntityGetter, $ServerLevel, $ServerPlayer, $ServerPlayer$RespawnConfig, $ServerPlayerGameMode, $ThreadedLevelLightEngine, $Ticket, $TicketType, $WorldGenRegion } from "java:net/minecraft/server/level";
import { $ChunkLoadStatusView, $LevelLoadListener, $LevelLoadListener$Stage } from "java:net/minecraft/server/level/progress";
import { $CommonListenerCookie, $EventLoopGroupHolder, $FilteredText, $PlayerChunkSender, $ServerCommonPacketListenerImpl, $ServerConnectionListener, $ServerGamePacketListenerImpl, $ServerPlayerConnection, $TextFilter } from "java:net/minecraft/server/network";
import { $NotificationManager, $NotificationService, $ServerActivityMonitor } from "java:net/minecraft/server/notifications";
import { $PackLocationInfo, $PackResources, $PackResources$ResourceOutput, $PackSelectionConfig, $PackType } from "java:net/minecraft/server/packs";
import { $MetadataSectionType, $MetadataSectionType$WithValue } from "java:net/minecraft/server/packs/metadata";
import { $PackFormat } from "java:net/minecraft/server/packs/metadata/pack";
import { $KnownPack, $Pack, $Pack$Metadata, $Pack$Position, $Pack$ResourcesSupplier, $PackCompatibility, $PackRepository, $PackSource } from "java:net/minecraft/server/packs/repository";
import { $IoSupplier, $PreparableReloadListener, $PreparableReloadListener$PreparationBarrier, $PreparableReloadListener$SharedState, $PreparableReloadListener$StateKey, $Resource, $ResourceManager, $ResourceMetadata, $ResourceProvider, $SimpleJsonResourceReloadListener, $SimplePreparableReloadListener } from "java:net/minecraft/server/packs/resources";
import { $LevelBasedPermissionSet, $Permission, $PermissionCheck, $PermissionLevel, $PermissionProviderCheck, $PermissionSet, $PermissionSetSupplier } from "java:net/minecraft/server/permissions";
import { $BanListEntry, $IpBanList, $IpBanListEntry, $NameAndId, $PlayerList, $ProfileResolver, $ServerOpList, $ServerOpListEntry, $StoredUserEntry, $StoredUserList, $UserBanList, $UserBanListEntry, $UserNameToIdResolver, $UserWhiteList, $UserWhiteListEntry } from "java:net/minecraft/server/players";
import { $ServerWaypointManager } from "java:net/minecraft/server/waypoints";
import { $SoundEvent, $SoundSource } from "java:net/minecraft/sounds";
import { $RecipeBook, $RecipeBookSettings, $RecipeBookSettings$TypeSettings, $ServerRecipeBook, $ServerRecipeBook$Packed, $ServerStatsCounter, $Stat, $StatFormatter, $StatType, $StatsCounter } from "java:net/minecraft/stats";
import { $TagKey, $TagLoader$LoadResult, $TagNetworkSerialization$NetworkPayload } from "java:net/minecraft/tags";
import { $BlockUtil$FoundRectangle, $CompilableString, $EasingType, $ExtraCodecs$LateBoundIdMapper, $FormattedCharSequence, $FormattedCharSink, $InclusiveRange, $KeyDispatchDataCodec, $Keyframe, $KeyframeTrack, $KeyframeTrack$Builder, $KeyframeTrackSampler, $ModCheck, $ModCheck$Confidence, $ProblemReporter, $ProblemReporter$PathElement, $ProblemReporter$Problem, $ProgressListener, $RandomSource, $SignatureUpdater, $SignatureUpdater$Output, $SignatureValidator, $StaticCache2D, $StaticCache2D$Initializer, $StringRepresentable, $StringRepresentable$EnumCodec, $StringRepresentable$StringRepresentableCodec, $TaskChainer, $ToFloatFunction, $TriState, $Unit } from "java:net/minecraft/util";
import { $ContextKey, $ContextKeySet, $ContextMap } from "java:net/minecraft/util/context";
import { $DataFixTypes } from "java:net/minecraft/util/datafix";
import { $DebugPoiInfo, $DebugSubscription, $DebugSubscription$Event, $DebugSubscription$Update, $DebugValueSource, $DebugValueSource$Registration, $DebugValueSource$ValueGetter, $LevelDebugSynchronizers, $ServerDebugSubscribers } from "java:net/minecraft/util/debug";
import { $AbstractSampleLogger, $LocalSampleLogger, $RemoteDebugSampleType, $SampleLogger, $SampleStorage } from "java:net/minecraft/util/debugchart";
import { $ProfileResults, $ProfilerFiller, $ResultField, $Zone } from "java:net/minecraft/util/profiling";
import { $MetricCategory, $MetricSampler, $MetricSampler$MetricSamplerBuilder, $MetricSampler$SamplerResult, $MetricSampler$SamplingPhase, $MetricSampler$ThresholdTest, $ProfilerMeasured } from "java:net/minecraft/util/profiling/metrics";
import { $Weighted, $WeightedList, $WeightedList$Builder } from "java:net/minecraft/util/random";
import { $BlockableEventLoop, $ReentrantBlockableEventLoop, $TaskScheduler } from "java:net/minecraft/util/thread";
import { $FloatProvider, $IntProvider, $SampledFloat } from "java:net/minecraft/util/valueproviders";
import { $BossEvent, $BossEvent$BossBarColor, $BossEvent$BossBarOverlay, $Clearable, $Container, $Difficulty, $DifficultyInstance, $InteractionHand, $InteractionResult, $InteractionResult$Fail, $InteractionResult$ItemContext, $InteractionResult$Pass, $InteractionResult$Success, $InteractionResult$SwingSource, $InteractionResult$TryEmptyHandInteraction, $ItemStackWithSlot, $MenuProvider, $Nameable, $RandomSequence, $RandomSequences, $SimpleContainer, $Stopwatch, $Stopwatches, $TickRateManager } from "java:net/minecraft/world";
import { $AttributeRange, $AttributeType, $EnvironmentAttribute, $EnvironmentAttribute$Builder, $EnvironmentAttributeLayer, $EnvironmentAttributeLayer$Constant, $EnvironmentAttributeLayer$Positional, $EnvironmentAttributeLayer$TimeBased, $EnvironmentAttributeMap, $EnvironmentAttributeMap$Builder, $EnvironmentAttributeMap$Entry, $EnvironmentAttributeReader, $EnvironmentAttributeSystem, $EnvironmentAttributeSystem$Builder, $LerpFunction, $SpatialAttributeInterpolator } from "java:net/minecraft/world/attribute";
import { $AttributeModifier, $AttributeModifier$OperationId } from "java:net/minecraft/world/attribute/modifier";
import { $ClockManager, $ClockNetworkState, $ClockState, $ClockTimeMarker, $PackedClockStates, $ServerClockManager, $WorldClock } from "java:net/minecraft/world/clock";
import { $CombatTracker, $DamageEffects, $DamageScaling, $DamageSource, $DamageSources, $DamageType, $DeathMessageType } from "java:net/minecraft/world/damagesource";
import { $MobEffect, $MobEffectCategory, $MobEffectInstance } from "java:net/minecraft/world/effect";
import { $AgeableMob, $Attackable, $Avatar, $ContainerUser, $ConversionParams, $ConversionParams$AfterConversion, $ConversionType, $DropChances, $ElytraAnimationState, $Entity, $Entity$RemovalReason, $EntityAttachment, $EntityAttachments, $EntityAttachments$Builder, $EntityDimensions, $EntityProcessor, $EntityReference, $EntitySpawnReason, $EntitySpawnRequest, $EntityType, $EquipmentSlot, $EquipmentSlot$Type, $EquipmentSlotGroup, $EquipmentTable, $EquipmentUser, $HasCustomInventoryScreen, $HumanoidArm, $InsideBlockEffectApplier, $InsideBlockEffectType, $InterpolationHandler, $ItemOwner, $Leashable, $Leashable$LeashData, $LightningBolt, $LivingEntity, $LivingEntity$Fallsounds, $Mob, $MobCategory, $MoverType, $OwnableEntity, $PathfinderMob, $PlayerRideable, $PlayerRideableJumping, $PortalProcessor, $Pose, $PositionMoveRotation, $PostSpawnProcessor, $Relative, $ReputationEventHandler, $SlotAccess, $SlotProvider, $SpawnGroupData, $SpawnPlacementType, $TamableAnimal, $Targeting, $TraceableEntity, $WalkAnimationState } from "java:net/minecraft/world/entity";
import { $ActivityData, $Brain, $Brain$ActivitySupplier, $Brain$Packed, $Brain$Provider, $Brain$Visitor } from "java:net/minecraft/world/entity/ai";
import { $Attribute, $Attribute$Sentiment, $AttributeInstance, $AttributeInstance$Packed, $AttributeMap, $AttributeModifier, $AttributeModifier$Operation, $AttributeSupplier, $AttributeSupplier$Builder } from "java:net/minecraft/world/entity/ai/attributes";
import { $Behavior$Status, $BehaviorControl, $PositionTracker, $SpearAttack$SpearStatus } from "java:net/minecraft/world/entity/ai/behavior";
import { $Control, $JumpControl, $LookControl, $MoveControl } from "java:net/minecraft/world/entity/ai/control";
import { $Goal, $Goal$Flag, $GoalSelector, $WrappedGoal } from "java:net/minecraft/world/entity/ai/goal";
import { $ExpirableValue, $MemoryMap, $MemoryMap$Value, $MemoryModuleType, $MemoryStatus, $NearestVisibleLivingEntities, $WalkTarget } from "java:net/minecraft/world/entity/ai/memory";
import { $PathNavigation } from "java:net/minecraft/world/entity/ai/navigation";
import { $Sensing } from "java:net/minecraft/world/entity/ai/sensing";
import { $TargetingConditions, $TargetingConditions$Selector } from "java:net/minecraft/world/entity/ai/targeting";
import { $ReputationEventType } from "java:net/minecraft/world/entity/ai/village";
import { $PoiManager, $PoiManager$Occupancy, $PoiRecord, $PoiRecord$Packed, $PoiType } from "java:net/minecraft/world/entity/ai/village/poi";
import { $Animal } from "java:net/minecraft/world/entity/animal";
import { $AbstractHorse } from "java:net/minecraft/world/entity/animal/equine";
import { $AbstractNautilus } from "java:net/minecraft/world/entity/animal/nautilus";
import { $Parrot$Variant } from "java:net/minecraft/world/entity/animal/parrot";
import { $DragonFlightHistory, $DragonFlightHistory$Sample, $EndCrystal, $EnderDragon, $EnderDragonPart } from "java:net/minecraft/world/entity/boss/enderdragon";
import { $AbstractDragonPhaseInstance, $AbstractDragonSittingPhase, $DragonChargePlayerPhase, $DragonDeathPhase, $DragonHoldingPatternPhase, $DragonHoverPhase, $DragonLandingApproachPhase, $DragonLandingPhase, $DragonPhaseInstance, $DragonSittingAttackingPhase, $DragonSittingFlamingPhase, $DragonSittingScanningPhase, $DragonStrafePlayerPhase, $DragonTakeoffPhase, $EnderDragonPhase, $EnderDragonPhaseManager } from "java:net/minecraft/world/entity/boss/enderdragon/phases";
import { $BlockAttachedEntity, $HangingEntity, $ItemFrame } from "java:net/minecraft/world/entity/decoration";
import { $ItemEntity } from "java:net/minecraft/world/entity/item";
import { $Enemy, $Monster, $PatrollingMonster } from "java:net/minecraft/world/entity/monster";
import { $Hoglin, $HoglinBase } from "java:net/minecraft/world/entity/monster/hoglin";
import { $AbstractPiglin, $PiglinArmPose } from "java:net/minecraft/world/entity/monster/piglin";
import { $WardenSpawnTracker } from "java:net/minecraft/world/entity/monster/warden";
import { $Abilities, $Abilities$Packed, $ChatVisiblity, $Input, $Inventory, $Player, $Player$BedSleepingProblem, $PlayerModelPart, $PlayerModelType, $PlayerSkin$Patch, $ProfilePublicKey, $ProfilePublicKey$Data, $StackedContents$IngredientInfo, $StackedContents$Output, $StackedItemContents } from "java:net/minecraft/world/entity/player";
import { $FireworkRocketEntity, $FishingHook, $ItemSupplier, $Projectile, $Projectile$ProjectileFactory, $ProjectileDeflection, $ThrowableProjectile } from "java:net/minecraft/world/entity/projectile";
import { $AbstractArrow, $AbstractArrow$Pickup } from "java:net/minecraft/world/entity/projectile/arrow";
import { $AbstractHurtingProjectile, $Fireball, $WitherSkull } from "java:net/minecraft/world/entity/projectile/hurtingprojectile";
import { $ThrowableItemProjectile, $ThrownEnderpearl } from "java:net/minecraft/world/entity/projectile/throwableitemprojectile";
import { $Raid, $Raider, $Raids } from "java:net/minecraft/world/entity/raid";
import { $Activity } from "java:net/minecraft/world/entity/schedule";
import { $VehicleEntity } from "java:net/minecraft/world/entity/vehicle";
import { $AbstractMinecart, $MinecartBehavior, $MinecartCommandBlock, $NewMinecartBehavior$MinecartStep } from "java:net/minecraft/world/entity/vehicle/minecart";
import { $FeatureElement, $FeatureFlag, $FeatureFlagSet } from "java:net/minecraft/world/flag";
import { $FoodData, $FoodProperties } from "java:net/minecraft/world/food";
import { $AbstractContainerMenu, $AbstractCraftingMenu, $AbstractFurnaceMenu, $AnvilMenu, $BeaconMenu, $BlastFurnaceMenu, $BrewingStandMenu, $CartographyTableMenu, $ChestMenu, $ClickAction, $ContainerInput, $ContainerListener, $ContainerSynchronizer, $CrafterMenu, $CraftingContainer, $CraftingMenu, $DispenserMenu, $EnchantmentMenu, $FurnaceMenu, $GrindstoneMenu, $HopperMenu, $InventoryMenu, $ItemCombinerMenu, $LecternMenu, $LoomMenu, $MenuConstructor, $MenuType, $MerchantMenu, $PlayerEnderChestContainer, $RecipeBookMenu, $RecipeBookMenu$PostPlaceAction, $RecipeBookType, $RemoteSlot, $ShulkerBoxMenu, $Slot, $SmithingMenu, $SmokerMenu, $StackedContentsCompatible, $StonecutterMenu } from "java:net/minecraft/world/inventory";
import { $TooltipComponent } from "java:net/minecraft/world/inventory/tooltip";
import { $DyeColor, $Item, $Item$Properties, $Item$TooltipContext, $ItemCooldowns, $ItemInstance, $ItemStack, $ItemStackTemplate, $ItemUseAnimation, $JukeboxSong, $Rarity, $SwingAnimationType, $ToolMaterial, $TooltipFlag, $TooltipFlag$Default } from "java:net/minecraft/world/item";
import { $Potion, $PotionBrewing, $PotionBrewing$Builder } from "java:net/minecraft/world/item/alchemy";
import { $AttackRange, $Consumable, $Consumable$Builder, $ConsumableListener, $FireworkExplosion, $FireworkExplosion$Shape, $ItemAttributeModifiers, $ItemAttributeModifiers$Builder, $ItemAttributeModifiers$Display, $ItemAttributeModifiers$Display$Type, $ItemAttributeModifiers$Entry, $ResolvableProfile, $SwingAnimation, $TooltipDisplay, $TooltipProvider, $TypedEntityData } from "java:net/minecraft/world/item/component";
import { $ApplyStatusEffectsConsumeEffect, $ClearAllStatusEffectsConsumeEffect, $ConsumeEffect, $ConsumeEffect$Type, $PlaySoundConsumeEffect, $RemoveStatusEffectsConsumeEffect, $TeleportRandomlyConsumeEffect } from "java:net/minecraft/world/item/consume_effects";
import { $BlockPlaceContext, $UseOnContext } from "java:net/minecraft/world/item/context";
import { $AbstractCookingRecipe, $AbstractCookingRecipe$CookingBookInfo, $AbstractCookingRecipe$Factory, $BlastingRecipe, $CampfireCookingRecipe, $CookingBookCategory, $CraftingBookCategory, $CraftingInput, $CraftingInput$Positioned, $CraftingRecipe, $ExtendedRecipeBookCategory, $Ingredient, $PlacementInfo, $Recipe, $Recipe$BookInfo, $Recipe$BookInfo$Constructor, $Recipe$CommonInfo, $RecipeAccess, $RecipeBookCategory, $RecipeHolder, $RecipeInput, $RecipeManager, $RecipeManager$CachedCheck, $RecipeManager$ServerDisplayInfo, $RecipePropertySet, $RecipeSerializer, $RecipeType, $SelectableRecipe, $SelectableRecipe$SingleInputEntry, $SelectableRecipe$SingleInputSet, $SingleItemRecipe, $SingleItemRecipe$Factory, $SingleRecipeInput, $SmeltingRecipe, $SmithingRecipe, $SmithingRecipeInput, $SmokingRecipe, $StonecutterRecipe } from "java:net/minecraft/world/item/crafting";
import { $DisplayContentsFactory, $RecipeDisplay, $RecipeDisplay$Type, $RecipeDisplayEntry, $RecipeDisplayId, $SlotDisplay, $SlotDisplay$Type } from "java:net/minecraft/world/item/crafting/display";
import { $ConditionalEffect, $EnchantedItemInUse, $Enchantment, $Enchantment$Builder, $Enchantment$Cost, $Enchantment$EnchantmentDefinition, $Enchantment$FloatAction, $Enchantment$GenericAction, $EnchantmentTarget, $ItemEnchantments, $LevelBasedValue, $LevelBasedValue$Constant, $LevelBasedValue$Linear, $LevelBasedValue$Lookup, $TargetedConditionalEffect } from "java:net/minecraft/world/item/enchantment";
import { $EnchantmentAttributeEffect, $EnchantmentEntityEffect, $EnchantmentLocationBasedEffect, $EnchantmentValueEffect } from "java:net/minecraft/world/item/enchantment/effects";
import { $ArmorMaterial, $ArmorType, $EquipmentAsset } from "java:net/minecraft/world/item/equipment";
import { $MaterialAssetGroup, $MaterialAssetGroup$AssetInfo, $TrimMaterial } from "java:net/minecraft/world/item/equipment/trim";
import { $SlotCollection } from "java:net/minecraft/world/item/slot";
import { $ItemCost, $MerchantOffer, $MerchantOffers } from "java:net/minecraft/world/item/trading";
import { $BaseCommandBlock, $BlockAndLightGetter, $BlockGetter, $BlockGetter$BlockStepVisitor, $CardinalLighting, $CardinalLighting$Type, $ChunkPos, $ClipBlockStateContext, $ClipContext, $ClipContext$Block, $ClipContext$Fluid, $ClipContext$ShapeGetter, $CollisionGetter, $CommonLevelAccessor, $DataPackConfig, $EntityGetter, $Explosion, $Explosion$BlockInteraction, $ExplosionDamageCalculator, $GameType, $ItemLike, $Level, $Level$ExplosionInteraction, $LevelAccessor, $LevelHeightAccessor, $LevelReader, $LevelSettings, $LevelSettings$DifficultySettings, $LevelSimulatedRW, $LevelSimulatedReader, $LevelWriter, $LightLayer, $NaturalSpawner$SpawnState, $NoiseColumn, $PathNavigationRegion, $ScheduledTickAccess, $ServerLevelAccessor, $SignalGetter, $StructureManager, $WorldDataConfiguration, $WorldGenLevel } from "java:net/minecraft/world/level";
import { $Biome, $Biome$Precipitation, $BiomeGenerationSettings, $BiomeManager, $BiomeManager$NoiseBiomeSource, $BiomeResolver, $BiomeSource, $BiomeSpecialEffects, $BiomeSpecialEffects$GrassColorModifier, $Climate$Parameter, $Climate$ParameterPoint, $Climate$Sampler, $Climate$TargetPoint, $MobSpawnSettings, $MobSpawnSettings$MobSpawnCost, $MobSpawnSettings$SpawnerData } from "java:net/minecraft/world/level/biome";
import { $Block, $Mirror, $Portal, $Portal$Transition, $RenderShape, $Rotation, $SoundType, $SupportType } from "java:net/minecraft/world/level/block";
import { $BannerPattern, $BeaconBeamOwner, $BeaconBeamOwner$Section, $BlockEntity, $BlockEntityTicker, $BlockEntityType, $BoundingBoxRenderable, $BoundingBoxRenderable$Mode, $BoundingBoxRenderable$RenderableBox, $CommandBlockEntity, $CommandBlockEntity$Mode, $ContainerOpenersCounter, $EnderChestBlockEntity, $FuelValues, $JigsawBlockEntity, $JigsawBlockEntity$JointType, $LidBlockEntity, $SignBlockEntity, $SignText, $StructureBlockEntity, $StructureBlockEntity$UpdateType, $TestBlockEntity, $TestInstanceBlockEntity, $TestInstanceBlockEntity$Data, $TestInstanceBlockEntity$ErrorMarker, $TestInstanceBlockEntity$Status, $TickingBlockEntity } from "java:net/minecraft/world/level/block/entity";
import { $BlockBehaviour, $BlockBehaviour$BlockStateBase, $BlockBehaviour$OffsetType, $BlockBehaviour$PostProcess, $BlockBehaviour$Properties, $BlockBehaviour$StateArgumentPredicate, $BlockBehaviour$StatePredicate, $BlockState, $StateDefinition, $StateHolder } from "java:net/minecraft/world/level/block/state";
import { $BlockInWorld } from "java:net/minecraft/world/level/block/state/pattern";
import { $NoteBlockInstrument, $Property, $Property$Value, $RailShape, $StructureMode, $TestBlockMode } from "java:net/minecraft/world/level/block/state/properties";
import { $BorderChangeListener, $BorderStatus, $WorldBorder } from "java:net/minecraft/world/level/border";
import { $BlockColumn, $CarvingMask, $CarvingMask$Mask, $ChunkAccess, $ChunkAccess$PackedTicks, $ChunkGenerator, $ChunkGeneratorStructureState, $ChunkSource, $DataLayer, $GlobalPalette, $ImposterProtoChunk, $LevelChunk, $LevelChunk$EntityCreationType, $LevelChunk$UnsavedListener, $LevelChunkSection, $LightChunk, $LightChunkGetter, $Palette, $PaletteResize, $PalettedContainer, $PalettedContainer$CountConsumer, $PalettedContainerFactory, $PalettedContainerRO, $PalettedContainerRO$PackedData, $ProtoChunk, $Strategy, $StructureAccess, $UpgradeData } from "java:net/minecraft/world/level/chunk";
import { $ChunkDependencies, $ChunkStatus, $ChunkStatusTask, $ChunkStep, $ChunkType, $WorldGenContext } from "java:net/minecraft/world/level/chunk/status";
import { $ChunkIOErrorReporter, $ChunkScanAccess, $RegionStorageInfo, $SectionStorage, $SimpleRegionStorage } from "java:net/minecraft/world/level/chunk/storage";
import { $DimensionType, $DimensionType$MonsterSettings, $DimensionType$Skybox, $LevelStem } from "java:net/minecraft/world/level/dimension";
import { $EnderDragonFight } from "java:net/minecraft/world/level/dimension/end";
import { $EntityAccess, $EntityInLevelCallback, $EntityTypeTest, $UUIDLookup, $UniquelyIdentifyable } from "java:net/minecraft/world/level/entity";
import { $BlockPositionSource, $DynamicGameEventListener, $EntityPositionSource, $GameEvent, $GameEvent$Context, $GameEventListener, $GameEventListener$DeliveryMode, $GameEventListenerRegistry, $GameEventListenerRegistry$ListenerVisitor, $PositionSource, $PositionSourceType } from "java:net/minecraft/world/level/gameevent";
import { $GameRule, $GameRuleCategory, $GameRuleMap, $GameRuleType, $GameRuleTypeVisitor, $GameRules } from "java:net/minecraft/world/level/gamerules";
import { $Aquifer, $Aquifer$FluidPicker, $Aquifer$FluidStatus, $BelowZeroRetrogen, $BitRandomSource, $DensityFunction, $DensityFunction$ContextProvider, $DensityFunction$FunctionContext, $DensityFunction$NoiseHolder, $DensityFunction$SimpleFunction, $DensityFunction$Visitor, $DensityFunctions$BeardifierOrMarker, $GenerationStep$Decoration, $Heightmap, $Heightmap$Types, $LegacyRandomSource, $NoiseChunk, $NoiseGeneratorSettings, $NoiseRouter, $NoiseSettings, $PositionalRandomFactory, $RandomState, $RandomSupport$Seed128bit, $SurfaceRules$RuleSource, $SurfaceSystem, $VerticalAnchor, $WorldDimensions, $WorldDimensions$Complete, $WorldGenSettings, $WorldGenerationContext, $WorldOptions, $WorldgenRandom, $WorldgenRandom$Algorithm } from "java:net/minecraft/world/level/levelgen";
import { $Blender, $Blender$BlendingOutput, $Blender$DistanceGetter, $BlendingData, $BlendingData$Packed } from "java:net/minecraft/world/level/levelgen/blending";
import { $AllOfPredicate, $AnyOfPredicate, $BlockPredicate, $BlockPredicateType, $CombiningPredicate, $HasSturdyFacePredicate, $InsideWorldBoundsPredicate, $MatchingBiomesPredicate, $MatchingBlockTagPredicate, $MatchingBlocksPredicate, $MatchingFluidsPredicate, $NotPredicate, $ReplaceablePredicate, $SolidPredicate, $StateTestingPredicate, $TrueBlockPredicate, $UnobstructedPredicate, $WouldSurvivePredicate } from "java:net/minecraft/world/level/levelgen/blockpredicates";
import { $CanyonCarverConfiguration, $CanyonCarverConfiguration$CanyonShapeConfiguration, $CarverConfiguration, $CarverDebugSettings, $CarvingContext, $CaveCarverConfiguration, $ConfiguredWorldCarver, $WorldCarver } from "java:net/minecraft/world/level/levelgen/carver";
import { $ConfiguredFeature } from "java:net/minecraft/world/level/levelgen/feature";
import { $FeatureConfiguration, $NoneFeatureConfiguration, $ProbabilityFeatureConfiguration } from "java:net/minecraft/world/level/levelgen/feature/configurations";
import { $BiasedToBottomHeight, $ConstantHeight, $HeightProvider, $HeightProviderType, $TrapezoidHeight, $UniformHeight, $VeryBiasedToBottomHeight, $WeightedListHeight } from "java:net/minecraft/world/level/levelgen/heightproviders";
import { $BiomeFilter, $BlockPredicateFilter, $CountOnEveryLayerPlacement, $CountPlacement, $EnvironmentScanPlacement, $FixedPlacement, $HeightRangePlacement, $HeightmapPlacement, $InSquarePlacement, $NoiseBasedCountPlacement, $NoiseThresholdCountPlacement, $PlacedFeature, $PlacementContext, $PlacementFilter, $PlacementModifier, $PlacementModifierType, $RandomOffsetPlacement, $RarityFilter, $RepeatingPlacement, $SurfaceRelativeThresholdFilter, $SurfaceWaterDepthFilter } from "java:net/minecraft/world/level/levelgen/placement";
import { $BoundingBox, $SinglePieceStructure, $Structure, $Structure$GenerationContext, $Structure$GenerationStub, $Structure$StructureSettings, $StructureCheckResult, $StructurePiece, $StructurePieceAccessor, $StructureSet, $StructureSet$StructureSelectionEntry, $StructureSpawnOverride, $StructureSpawnOverride$BoundingBoxType, $StructureStart, $StructureType, $TerrainAdjustment } from "java:net/minecraft/world/level/levelgen/structure";
import { $PiecesContainer, $StructurePieceSerializationContext, $StructurePieceType, $StructurePiecesBuilder } from "java:net/minecraft/world/level/levelgen/structure/pieces";
import { $ConcentricRingsStructurePlacement, $RandomSpreadStructurePlacement, $RandomSpreadType, $StructurePlacement, $StructurePlacementType } from "java:net/minecraft/world/level/levelgen/structure/placement";
import { $DimensionPadding, $EmptyPoolElement, $FeaturePoolElement, $LegacySinglePoolElement, $ListPoolElement, $SinglePoolElement, $StructurePoolElement, $StructurePoolElementType, $StructureTemplatePool, $StructureTemplatePool$Projection } from "java:net/minecraft/world/level/levelgen/structure/pools";
import { $DirectPoolAlias, $PoolAliasBinding, $RandomGroupPoolAlias, $RandomPoolAlias } from "java:net/minecraft/world/level/levelgen/structure/pools/alias";
import { $BuriedTreasureStructure, $DesertPyramidStructure, $EndCityStructure, $IglooStructure, $JigsawStructure, $JungleTempleStructure, $MineshaftStructure, $NetherFortressStructure, $NetherFossilStructure, $OceanMonumentStructure, $OceanRuinStructure, $OceanRuinStructure$Type, $RuinedPortalStructure, $ShipwreckStructure, $StrongholdStructure, $SwampHutStructure, $WoodlandMansionStructure } from "java:net/minecraft/world/level/levelgen/structure/structures";
import { $LiquidSettings, $StructurePlaceSettings, $StructureProcessor, $StructureProcessorList, $StructureTemplate, $StructureTemplate$JigsawBlockInfo, $StructureTemplate$Palette, $StructureTemplate$StructureBlockInfo, $StructureTemplateManager } from "java:net/minecraft/world/level/levelgen/structure/templatesystem";
import { $TemplatePathFactory } from "java:net/minecraft/world/level/levelgen/structure/templatesystem/loader";
import { $NormalNoise, $NormalNoise$NoiseParameters, $PerlinSimplexNoise } from "java:net/minecraft/world/level/levelgen/synth";
import { $ChunkSkyLightSources, $LayerLightEventListener, $LayerLightSectionStorage$SectionType, $LevelLightEngine, $LightEventListener } from "java:net/minecraft/world/level/lighting";
import { $Fluid, $FluidState, $MapColor, $MapColor$Brightness, $PushReaction } from "java:net/minecraft/world/level/material";
import { $Node, $NodeEvaluator, $Path, $Path$DebugData, $PathComputationType, $PathType, $PathTypeCache, $PathfindingContext, $Target } from "java:net/minecraft/world/level/pathfinder";
import { $PortalForcer, $TeleportTransition, $TeleportTransition$PostTeleportTransition } from "java:net/minecraft/world/level/portal";
import { $Orientation, $Orientation$SideBias } from "java:net/minecraft/world/level/redstone";
import { $SavedData, $SavedDataType, $WeatherData } from "java:net/minecraft/world/level/saveddata";
import { $MapBanner, $MapDecoration, $MapDecorationType, $MapId, $MapItemSavedData, $MapItemSavedData$HoldingPlayer, $MapItemSavedData$MapPatch } from "java:net/minecraft/world/level/saveddata/maps";
import { $CommandStorage, $LevelData, $LevelData$RespawnData, $LevelResource, $PrimaryLevelData$SpecialWorldProperty, $SavedDataStorage, $ServerLevelData, $ValueInput, $ValueInput$TypedInputList, $ValueInput$ValueInputList, $ValueOutput, $ValueOutput$TypedOutputList, $ValueOutput$ValueOutputList, $WorldData, $WritableLevelData } from "java:net/minecraft/world/level/storage";
import { $LootContext, $LootContext$VisitedEntry, $LootContextUser, $LootDataType, $LootDataType$ContextGetter, $LootParams, $LootParams$Builder, $LootParams$DynamicDrop, $LootPool, $LootPool$Builder, $LootTable, $LootTable$Builder, $Validatable, $ValidationContext, $ValidationContextSource } from "java:net/minecraft/world/level/storage/loot";
import { $AlternativesEntry$Builder, $ComposableEntryContainer, $EntryGroup$Builder, $LootPoolEntry, $LootPoolEntryContainer, $LootPoolEntryContainer$Builder, $SequentialEntry$Builder } from "java:net/minecraft/world/level/storage/loot/entries";
import { $FunctionUserBuilder, $LootItemFunction, $LootItemFunction$Builder } from "java:net/minecraft/world/level/storage/loot/functions";
import { $AllOfCondition$Builder, $AnyOfCondition$Builder, $CompositeLootItemCondition$Builder, $ConditionUserBuilder, $LootItemCondition, $LootItemCondition$Builder } from "java:net/minecraft/world/level/storage/loot/predicates";
import { $NumberProvider } from "java:net/minecraft/world/level/storage/loot/providers/number";
import { $TimerCallback, $TimerQueue } from "java:net/minecraft/world/level/timers";
import { $AABB, $BlockHitResult, $HitResult, $HitResult$Type, $Vec2, $Vec3 } from "java:net/minecraft/world/phys";
import { $CollisionContext, $DiscreteVoxelShape, $DiscreteVoxelShape$IntFaceConsumer, $DiscreteVoxelShape$IntLineConsumer, $Shapes$DoubleLineConsumer, $VoxelShape } from "java:net/minecraft/world/phys/shapes";
import { $DisplaySlot, $Objective, $Objective$Packed, $PlayerScoreEntry, $PlayerTeam, $PlayerTeam$Packed, $ReadOnlyScoreInfo, $Score$Packed, $ScoreAccess, $ScoreHolder, $Scoreboard, $Scoreboard$PackedScore, $ScoreboardSaveData, $ScoreboardSaveData$Packed, $Team, $Team$CollisionRule, $Team$Visibility, $TeamColor } from "java:net/minecraft/world/scores";
import { $ObjectiveCriteria, $ObjectiveCriteria$RenderType } from "java:net/minecraft/world/scores/criteria";
import { $LevelChunkTicks, $LevelTickAccess, $LevelTicks, $SavedTick, $ScheduledTick, $SerializableTickContainer, $TickAccess, $TickContainerAccess, $TickPriority } from "java:net/minecraft/world/ticks";
import { $AttributeTrackSampler, $Timeline, $Timeline$Builder } from "java:net/minecraft/world/timeline";
import { $PartialTickSupplier, $TrackedWaypoint, $TrackedWaypoint$Camera, $TrackedWaypoint$PitchDirection, $TrackedWaypoint$Projector, $TrackedWaypointManager, $Waypoint, $Waypoint$Icon, $WaypointManager, $WaypointStyleAsset, $WaypointTransmitter, $WaypointTransmitter$Connection } from "java:net/minecraft/world/waypoints";

declare module "@side-only/startup/events" {
}

export {};

declare global {
    namespace BlockEvents {
        function broken(handler: ((event: $BlockBrokenEventJS) => void)): void;
        function broken(extra: $Block, handler: ((event: $BlockBrokenEventJS) => void)): void;
        function modification(handler: ((event: $BlockModificationEventJS) => void)): void;
    }

    namespace ClientEvents {
        function tickPre(handler: ((event: $ClientTickEventJS) => void)): void;
        function tickPost(handler: ((event: $ClientTickEventJS) => void)): void;
        function tick(handler: ((event: $ClientTickEventJS) => void)): void;
    }

    namespace CommandEvents {
        function register(handler: ((event: $CommandRegistryEventJS) => void)): void;
    }

    namespace EntityEvents {
        function joinLevel(handler: ((event: $EntityJoinLevelEventJS) => void)): void;
        function joinLevel(extra: $EntityType, handler: ((event: $EntityJoinLevelEventJS) => void)): void;
        function death(handler: ((event: $LivingDeathEventJS) => void)): void;
        function death(extra: $EntityType, handler: ((event: $LivingDeathEventJS) => void)): void;
        function damagePre(handler: ((event: $LivingDamageEventJS) => void)): void;
        function damagePre(extra: $EntityType, handler: ((event: $LivingDamageEventJS) => void)): void;
        function damagePost(handler: ((event: $LivingDamageEventJS) => void)): void;
        function damagePost(extra: $EntityType, handler: ((event: $LivingDamageEventJS) => void)): void;
        function drops(handler: ((event: $LivingDropsEventJS) => void)): void;
        function drops(extra: $EntityType, handler: ((event: $LivingDropsEventJS) => void)): void;
        function finalizeSpawn(handler: ((event: $MobFinalizeSpawnEventJS) => void)): void;
        function finalizeSpawn(extra: $EntityType, handler: ((event: $MobFinalizeSpawnEventJS) => void)): void;
        function tickPre(handler: ((event: $EntityTickEventJS) => void)): void;
        function tickPre(extra: $EntityType, handler: ((event: $EntityTickEventJS) => void)): void;
        function tickPost(handler: ((event: $EntityTickEventJS) => void)): void;
        function tickPost(extra: $EntityType, handler: ((event: $EntityTickEventJS) => void)): void;
        function leaveLevel(handler: ((event: $EntityLeaveLevelEventJS) => void)): void;
        function leaveLevel(extra: $EntityType, handler: ((event: $EntityLeaveLevelEventJS) => void)): void;
    }

    namespace GoalEvents {
        function register(handler: ((event: $GoalRegisterEventJS) => void)): void;
    }

    namespace ItemEvents {
        function rightClicked(handler: ((event: $ItemRightClickEventJS) => void)): void;
        function rightClicked(extra: $Item, handler: ((event: $ItemRightClickEventJS) => void)): void;
        function tooltip(handler: ((event: $ItemTooltipEventJS) => void)): void;
        function tooltip(extra: $Item, handler: ((event: $ItemTooltipEventJS) => void)): void;
        function canPickUp(handler: ((event: $ItemEntityPickupEventJS) => void)): void;
        function canPickUp(extra: $Item, handler: ((event: $ItemEntityPickupEventJS) => void)): void;
        function pickedUpPre(handler: ((event: $ItemEntityPickupEventJS) => void)): void;
        function pickedUpPre(extra: $Item, handler: ((event: $ItemEntityPickupEventJS) => void)): void;
        function pickedUp(handler: ((event: $ItemEntityPickupEventJS) => void)): void;
        function pickedUp(extra: $Item, handler: ((event: $ItemEntityPickupEventJS) => void)): void;
        function dropped(handler: ((event: $ItemDroppedEventJS) => void)): void;
        function dropped(extra: $Item, handler: ((event: $ItemDroppedEventJS) => void)): void;
        function foodEaten(handler: ((event: $ItemUseFinishedEventJS) => void)): void;
        function foodEaten(extra: $Item, handler: ((event: $ItemUseFinishedEventJS) => void)): void;
        function entityInteracted(handler: ((event: $PlayerEntityInteractEventJS) => void)): void;
        function entityInteracted(extra: $Item, handler: ((event: $PlayerEntityInteractEventJS) => void)): void;
        function modification(handler: ((event: $ItemModificationEventJS) => void)): void;
    }

    namespace KeyBindEvents {
        function pressed(handler: ((event: $KeyBindEvents$KeyBindEventJS) => void)): void;
        function pressed(extra: string, handler: ((event: $KeyBindEvents$KeyBindEventJS) => void)): void;
        function released(handler: ((event: $KeyBindEvents$KeyBindEventJS) => void)): void;
        function released(extra: string, handler: ((event: $KeyBindEvents$KeyBindEventJS) => void)): void;
        function tick(handler: ((event: $KeyBindEvents$KeyBindEventJS) => void)): void;
        function tick(extra: string, handler: ((event: $KeyBindEvents$KeyBindEventJS) => void)): void;
        function register(handler: ((event: $Object) => void)): void;
    }

    namespace LevelEvents {
        function loaded(handler: ((event: $LevelEventJS) => void)): void;
        function unloaded(handler: ((event: $LevelEventJS) => void)): void;
        function tickPre(handler: ((event: $LevelEventJS) => void)): void;
        function tickPost(handler: ((event: $LevelEventJS) => void)): void;
    }

    namespace PlayerEvents {
        function loggedIn(handler: ((event: $PlayerLifecycleEventJS) => void)): void;
        function loggedOut(handler: ((event: $PlayerLifecycleEventJS) => void)): void;
        function tickPre(handler: ((event: $PlayerTickEventJS) => void)): void;
        function tickPost(handler: ((event: $PlayerTickEventJS) => void)): void;
        function cloned(handler: ((event: $PlayerCloneEventJS) => void)): void;
        function respawned(handler: ((event: $PlayerRespawnEventJS) => void)): void;
        function chat(handler: ((event: $ServerChatEventJS) => void)): void;
        function containerOpened(handler: ((event: $PlayerContainerEventJS) => void)): void;
        function inventoryOpened(handler: ((event: $PlayerContainerEventJS) => void)): void;
        function containerClosed(handler: ((event: $PlayerContainerEventJS) => void)): void;
        function inventoryClosed(handler: ((event: $PlayerContainerEventJS) => void)): void;
        function crafted(handler: ((event: $PlayerCraftedEventJS) => void)): void;
        function crafted(extra: $Item, handler: ((event: $PlayerCraftedEventJS) => void)): void;
        function smelted(handler: ((event: $PlayerCraftedEventJS) => void)): void;
        function smelted(extra: $Item, handler: ((event: $PlayerCraftedEventJS) => void)): void;
        function destroyed(handler: ((event: $PlayerDestroyItemEventJS) => void)): void;
        function destroyed(extra: $Item, handler: ((event: $PlayerDestroyItemEventJS) => void)): void;
        function advancement(handler: ((event: $PlayerAdvancementEventJS) => void)): void;
        function entityInteract(handler: ((event: $PlayerEntityInteractEventJS) => void)): void;
        function changedDimension(handler: ((event: $PlayerChangedDimensionEventJS) => void)): void;
        function inventoryChanged(handler: ((event: $InventoryChangedEventJS) => void)): void;
        function inventoryChanged(extra: $Item, handler: ((event: $InventoryChangedEventJS) => void)): void;
    }

    namespace RecipeViewerEvents {
        function addEntries(handler: ((event: $RecipeViewerEntryListJS) => void)): void;
        function addEntries(extra: string, handler: ((event: $RecipeViewerEntryListJS) => void)): void;
        function removeEntries(handler: ((event: $RecipeViewerEntryListJS) => void)): void;
        function removeEntries(extra: string, handler: ((event: $RecipeViewerEntryListJS) => void)): void;
        function removeRecipes(handler: ((event: $RecipeViewerRecipeListJS) => void)): void;
        function removeCategories(handler: ((event: $RecipeViewerCategoryListJS) => void)): void;
        function addInformation(handler: ((event: $RecipeViewerInformationJS) => void)): void;
    }

    namespace ServerEvents {
        function aboutToStart(handler: ((event: $ServerLifecycleEventJS) => void)): void;
        function starting(handler: ((event: $ServerLifecycleEventJS) => void)): void;
        function started(handler: ((event: $ServerLifecycleEventJS) => void)): void;
        function stopping(handler: ((event: $ServerLifecycleEventJS) => void)): void;
        function stopped(handler: ((event: $ServerLifecycleEventJS) => void)): void;
        function tickPre(handler: ((event: $ServerTickEventJS) => void)): void;
        function tickPost(handler: ((event: $ServerTickEventJS) => void)): void;
        function datapackSync(handler: ((event: $DatapackSyncEventJS) => void)): void;
        function tagsUpdated(handler: ((event: $TagUpdatedEventJS) => void)): void;
        function lootTableLoad(handler: ((event: $LootTableLoadEventJS) => void)): void;
        function recipes(handler: ((event: $RecipeEventJS) => void)): void;
        function afterRecipes(handler: ((event: $RecipeEventJS) => void)): void;
        function tradeDeclaration(handler: ((event: $VillagerTradeDeclarationEventJS) => void)): void;
        function tradeReload(handler: ((event: $VillagerTradeReloadEventJS) => void)): void;
    }

}
