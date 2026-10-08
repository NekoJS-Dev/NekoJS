# Current-source diagnosis and real client join failure

Runtime source: `4431faeb2b404c914e66e85c41c2fd0ba3d14535`. This follow-up retains failures and diagnosis; it is not multiplayer or performance acceptance.

## Separately instrumented reload

Ten diagnostic reloads ran after the unchanged formal groups finished. JFR attached only to the verified owned game PID25160; sampler/launcher PID29704 is a different process. Server shutdown was normal RCON, sampler exit0. The retained helper disables sensitive environment/property/process/JVM events, then creates an event-whitelisted recording. The committed safe recording is399312bytes; an independent extraction of forbidden events returns an empty list. The original raw recording stays ignored/local.

The existing streaming consumer finds272 Server-thread CPU/native samples,45 within the reload chain and10 clusters matching reload timestamps. Inclusive counts include32 candidate-script loading,22 module-host preparation and9 pipeline-cache preparation. Nearest frames include7 canonical-path conversion and3 sandbox construction samples. Counts are not milliseconds or exclusive percentages; sparse samples and zero observed reload-chain park duration do not prove no wait. This run does not establish a new safe optimization or explain every formal delay. Path containment, symlink verification and sandbox policy remain unchanged. Diagnostic values never count toward the formal gate, despite the sampler's generic kind=formal label.

## First actual client connection: FAIL

A new owned server/client pair uses the exact official NeoForge26.2 production JAR SHA256 `1209F0FC75528607DD1CDFB9D5D01F8876E501377B689C07418E05B170AAEB7C`, installed NeoForge26.2.0.75 and JDK25.0.2. The dedicated server is loopback25901/RCON25902. ClientA has an isolated launcher configuration and synthetic offline identity, copied version JSON/JAR and shared existing assets/libraries; no user's launcher config, account, mods or world is modified. A second isolated client profile is prepared but was not launched.

Server STARTUP definitions are copied to ClientA, and both explicitly enable the existing dynamic registry gate. The server also activates Item, SoundEvent and MobEffect from its server script. ClientA launch succeeds and reaches the server configuration stage, then NeoForge disconnects it with:

> The server sent registries with unknown keys

The diagnostic lists `ticket37_b7:proof_effect`, `ticket37_b7:proof_sound` and `ticket37_b7:proof_item`. The real Connection Lost screen was observed through the computer-use skill, consistent with client and server raw logs. No screenshot from an earlier source is reused and no successful world-entry or multiplayer claim is made.

Source trace identifies the timing gap: both26.x dynamic payload registrations are playBidirectional only; DynamicRegistrySyncWire initiates join catch-up from PlayerLoggedInEvent. The failed connection has not reached that event when NeoForge validates registry keys. The existing pack-sync configuration task illustrates an earlier lifecycle seam, but it is not a dynamic registry fix. A correction must respect actual configuration-task ordering, receive-side owner threads, acknowledgement/failure cleanup, concurrent prepare/commit and the existing root owner; injecting pretend ACKs or pre-seeding client dynamic IDs would conceal the failure.

Server PID27432 stopped normally through RCON. Client PID11700 returned from Connection Lost to its main menu and quit through the actual UI; its window disappeared and shutdown logs show loader close. No unrelated process was stopped, control-mode permission bypassed or user UI changed. The MCP mod's control channel was not used.

`runtime-followup.zip` preserves19 byte-verified entries: safe profiling output/settings/helper, full initial server/client logs, launch/stop transcripts and preparation script. Credentials, server.properties, worlds, launcher account config, user app inventories and raw JFR are excluded. [artifact.json](artifact.json) binds archive/source/JAR hashes. Full IDE diagnostics, current-source reload gate, actual two-client prepare/ACK/catch-up/abort, other installed nodes and visual/maintainer acceptance remain open.
