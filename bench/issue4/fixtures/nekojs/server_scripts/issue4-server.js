(function () {
    const PlatformApi = Java.type('com.tkisor.nekojs.platform.Platform');
    const loader = String(PlatformApi.getLoaderId());
    const isFabric = loader === 'fabric';
    const NativeDirection = Java.type('net.minecraft.core.Direction');
    const BuiltInRegistries = Java.type('net.minecraft.core.registries.BuiltInRegistries');
    const CommandsApi = Java.type('net.minecraft.commands.Commands');
    const TagOutput = Java.type('net.minecraft.world.level.storage.TagValueOutput');
    const TagInput = Java.type('net.minecraft.world.level.storage.TagValueInput');
    const Problems = Java.type('net.minecraft.util.ProblemReporter$Collector');
    const NativeFluidTypes = Java.type('net.minecraft.world.level.material.Fluids');
    const FloatGoal = Java.type('net.minecraft.world.entity.ai.goal.FloatGoal');
    const LookAtGoal = Java.type('net.minecraft.world.entity.ai.goal.LookAtPlayerGoal');
    const key = 'issue4_value';
    const checks = new Set();
    const logins = new Set();
    const pendingRespawns = [];
    let server = null;
    let level = null;
    let ticks = 0;
    let phase = 'waiting_for_server';
    let failed = false;
    let firstLoginTick = null;
    let mob = null;
    let pig = null;
    let otherPig = null;
    let entityWriteDone = false;
    let lastSummary = '';
    let api = null;

    function requireCondition(condition, description) {
        if (!condition) throw new Error('ISSUE4 assertion failed: ' + description);
    }
    function equal(actual, expected, description) {
        requireCondition(Number(actual) === Number(expected), description + ': expected=' + expected + ' actual=' + actual);
    }
    function summary() {
        const status = failed ? 'FAILED' : phase === 'ready' ? 'READY' : 'PENDING';
        const text = 'status=' + status + ' loader=' + loader + ' passed=' + checks.size
            + ' phase=' + phase + ' login=' + checks.has('pdata.player_login_write')
            + ' entity_auto_write=' + checks.has('pdata.entity_auto_write')
            + ' respawn_copy=' + checks.has('pdata.player_respawn_copy');
        if (text !== lastSummary) {
            lastSummary = text;
            console.info('ISSUE4 SUMMARY server ' + text);
        }
    }
    function runCheck(name, action) {
        if (failed) return false;
        try {
            action();
            if (!checks.has(name)) {
                checks.add(name);
                console.info('ISSUE4 PASS ' + name);
            }
            summary();
            return true;
        } catch (error) {
            failed = true;
            phase = 'failed';
            console.error('ISSUE4 FAIL ' + name + ' ' + error);
            summary();
            return false;
        }
    }
    function command(text) {
        const source = server.createCommandSourceStack().withSuppressedOutput();
        const parsed = server.getCommands().getDispatcher().parse(text, source);
        CommandsApi.validateParseResults(parsed);
        server.getCommands().performPrefixedCommand(source, text);
    }
    function transaction(parent, commit, action) {
        const opened = parent === null ? api.openRoot() : api.openNested(parent);
        try {
            const result = action(opened);
            if (commit) opened.commit();
            return result;
        } finally {
            opened.close();
        }
    }
    function roundTrip(storage, createEmpty) {
        const problems = new Problems();
        const output = TagOutput.createWithContext(problems, server.registryAccess());
        api.save(storage, output);
        requireCondition(problems.isEmpty(), 'serialize reported problems: ' + problems.getReport());
        const tag = output.buildResult();
        requireCondition(!tag.isEmpty(), 'serialize produced an empty tag');
        const restored = createEmpty();
        api.load(restored, TagInput.create(problems, server.registryAccess(), tag));
        requireCondition(problems.isEmpty(), 'deserialize reported problems: ' + problems.getReport());
        return restored;
    }
    function makeApi() {
        if (isFabric) {
            const FabricQueries = Java.type('com.tkisor.nekojs.fabric.FabricCapabilities');
            const Transaction = Java.type('net.fabricmc.fabric.api.transfer.v1.transaction.Transaction');
            const ItemVariant = Java.type('net.fabricmc.fabric.api.transfer.v1.item.ItemVariant');
            const FluidVariant = Java.type('net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant');
            const FluidConstants = Java.type('net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants');
            const ItemContext = Java.type('net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext');
            const BlockLookup = Java.type('net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup');
            const EntityLookup = Java.type('net.fabricmc.fabric.api.lookup.v1.entity.EntityApiLookup');
            const ItemLookup = Java.type('net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup');
            const EnergyApi = Java.type('com.tkisor.nekojs.fabric.capability.FabricEnergyHandler');
            const customId = Identifier.parse('nekojs:issue4_energy');
            const customBlock = BlockLookup.get(customId, EnergyApi.class, NativeDirection.class);
            const customEntity = EntityLookup.get(customId, EnergyApi.class, NativeDirection.class);
            const customItem = ItemLookup.get(customId, EnergyApi.class, NativeDirection.class);
            return {
                openRoot: () => Transaction.openOuter(),
                openNested: parent => parent.openNested(),
                energyAmount: storage => Number(storage.getAmount()),
                save: (storage, output) => storage.writeValue(output),
                load: (storage, input) => storage.readValue(input),
                itemContext: stack => ItemContext.withConstant(stack),
                blockEnergy: (position, side) => FabricQueries.getBlock(level, position, 'energy', side),
                entityEnergy: (entity, side) => FabricQueries.getEntity(entity, 'energy', side),
                itemEnergy: (stack, context) => FabricQueries.getItem(stack, 'energy', context),
                customBlock: (position, side) => customBlock.find(level, position, side),
                customEntity: (entity, side) => customEntity.find(entity, side),
                customItem: (stack, side) => customItem.find(stack, side),
                entityItems: entity => FabricQueries.getEntity(entity, 'item', NativeDirection.NORTH),
                entityFluid: entity => FabricQueries.getEntity(entity, 'fluid', NativeDirection.NORTH),
                itemResource: stack => ItemVariant.of(stack),
                fluidResource: () => FluidVariant.of(NativeFluidTypes.WATER),
                itemAmount: storage => Number(storage.getSlot(0).getAmount()),
                fluidAmount: storage => Number(storage.getAmount()),
                itemStoredResource: storage => storage.getSlot(0).getResource(),
                fluidStoredResource: storage => storage.getResource(),
                resourceInsert: (storage, resource, amount, opened) => storage.insert(resource, amount, opened),
                resourceExtract: (storage, resource, amount, opened) => storage.extract(resource, amount, opened),
                fluidUnit: Number(FluidConstants.BUCKET) / 1000
            };
        }
        const NativeEnergy = Java.type('net.neoforged.neoforge.capabilities.Capabilities$Energy');
        const NativeItems = Java.type('net.neoforged.neoforge.capabilities.Capabilities$Item');
        const NativeFluids = Java.type('net.neoforged.neoforge.capabilities.Capabilities$Fluid');
        const Transaction = Java.type('net.neoforged.neoforge.transfer.transaction.Transaction');
        const ItemResource = Java.type('net.neoforged.neoforge.transfer.item.ItemResource');
        const FluidResource = Java.type('net.neoforged.neoforge.transfer.fluid.FluidResource');
        const ItemAccess = Java.type('net.neoforged.neoforge.transfer.access.ItemAccess');
        const BlockCapability = Java.type('net.neoforged.neoforge.capabilities.BlockCapability');
        const EntityCapability = Java.type('net.neoforged.neoforge.capabilities.EntityCapability');
        const ItemCapability = Java.type('net.neoforged.neoforge.capabilities.ItemCapability');
        const EnergyApi = Java.type('net.neoforged.neoforge.transfer.energy.EnergyHandler');
        const customId = Identifier.parse('nekojs:issue4_energy');
        const customBlock = BlockCapability.createSided(customId, EnergyApi.class);
        const customEntity = EntityCapability.createSided(customId, EnergyApi.class);
        const customItem = ItemCapability.create(customId, EnergyApi.class, NativeDirection.class);
        return {
            openRoot: () => Transaction.openRoot(),
            openNested: parent => Transaction.open(parent),
            energyAmount: storage => Number(storage.getAmountAsLong()),
            save: (storage, output) => storage.serialize(output),
            load: (storage, input) => storage.deserialize(input),
            itemContext: stack => ItemAccess.forStack(stack),
            blockEnergy: (position, side) => level.getCapability(NativeEnergy.BLOCK, position, side),
            entityEnergy: (entity, side) => entity.getCapability(NativeEnergy.ENTITY, side),
            itemEnergy: (nativeStack, context) => nativeStack.getCapability(NativeEnergy.ITEM, context),
            customBlock: (position, side) => level.getCapability(customBlock, position, side),
            customEntity: (entity, side) => entity.getCapability(customEntity, side),
            customItem: (nativeStack, side) => nativeStack.getCapability(customItem, side),
            entityItems: entity => entity.getCapability(NativeItems.ENTITY, null),
            entityFluid: entity => entity.getCapability(NativeFluids.ENTITY, NativeDirection.NORTH),
            itemResource: stack => ItemResource.of(stack),
            fluidResource: () => FluidResource.of(NativeFluidTypes.WATER),
            itemAmount: storage => Number(storage.getAmountAsLong(0)),
            fluidAmount: storage => Number(storage.getAmountAsLong(0)),
            itemStoredResource: storage => storage.getResource(0),
            fluidStoredResource: storage => storage.getResource(0),
            resourceInsert: (storage, resource, amount, opened) => storage.insert(0, resource, amount, opened),
            resourceExtract: (storage, resource, amount, opened) => storage.extract(0, resource, amount, opened),
            fluidUnit: 1
        };
    }
    function stableScope(name, query, otherQuery) {
        const storage = query(NativeDirection.NORTH);
        requireCondition(storage !== null, name + ' NORTH query is absent');
        requireCondition(storage === query(NativeDirection.NORTH), name + ' repeated query is not stable');
        requireCondition(storage !== otherQuery(NativeDirection.NORTH), name + ' two owners share a handler');
        requireCondition(otherQuery(NativeDirection.NORTH) !== null, name + ' second owner is absent');
        requireCondition(query(NativeDirection.SOUTH) === null, name + ' SOUTH must be rejected');
        requireCondition(query(null) === null, name + ' null must be rejected');
        return storage;
    }
    function testEnergy(storage) {
        const initial = api.energyAmount(storage);
        transaction(null, false, opened => equal(storage.insert(100, opened), 100, 'root insert'));
        equal(api.energyAmount(storage), initial, 'root abort restored energy');
        transaction(null, false, outer => {
            equal(storage.insert(10, outer), 10, 'outer insert');
            transaction(outer, true, inner => equal(storage.insert(25, inner), 25, 'nested insert'));
            equal(api.energyAmount(storage), initial + 35, 'nested commit visible before root abort');
        });
        equal(api.energyAmount(storage), initial, 'nested commit reverted with root abort');
        transaction(null, true, outer => {
            equal(storage.insert(60, outer), 60, 'commit insert');
            transaction(outer, false, inner => equal(storage.extract(20, inner), 20, 'aborted child extraction'));
            equal(api.energyAmount(storage), initial + 60, 'nested abort restored parent');
        });
        equal(api.energyAmount(storage), initial + 60, 'root commit persisted energy');
        transaction(null, true, opened => equal(storage.extract(18, opened), 18, 'committed extraction'));
        equal(api.energyAmount(storage), initial + 42, 'committed net energy');
        const restored = roundTrip(storage, () => Capabilities.energyStorage(1000, 1000, 1000));
        equal(api.energyAmount(restored), initial + 42, 'energy serialization round trip');
    }
    function testResource(kind, storage, resource, amount, removed, createEmpty) {
        const getAmount = kind === 'item' ? api.itemAmount : api.fluidAmount;
        const getResource = kind === 'item' ? api.itemStoredResource : api.fluidStoredResource;
        equal(getAmount(storage), 0, kind + ' empty initial storage');
        transaction(null, false, opened => equal(api.resourceInsert(storage, resource, amount, opened), amount, kind + ' abort insert'));
        equal(getAmount(storage), 0, kind + ' root rollback');
        transaction(null, false, outer => {
            transaction(outer, true, inner => equal(api.resourceInsert(storage, resource, amount, inner), amount, kind + ' nested commit insert'));
            equal(getAmount(storage), amount, kind + ' nested commit visible');
        });
        equal(getAmount(storage), 0, kind + ' nested commit rolls back with parent');
        transaction(null, true, opened => equal(api.resourceInsert(storage, resource, amount, opened), amount, kind + ' commit insert'));
        transaction(null, true, opened => equal(api.resourceExtract(storage, resource, removed, opened), removed, kind + ' commit extraction'));
        equal(getAmount(storage), amount - removed, kind + ' final amount');
        requireCondition(resource.equals(getResource(storage)), kind + ' final resource identity');
        const restored = roundTrip(storage, createEmpty);
        equal(getAmount(restored), amount - removed, kind + ' serialization amount');
        requireCondition(resource.equals(getResource(restored)), kind + ' serialization resource');
    }
    function prepare() {
        command('forceload add -16 -16 16 16');
        phase = 'waiting_for_chunks';
        console.info('ISSUE4 READY server.forceload_requested');
    }
    function createWorldFixtures() {
        for (const tag of ['issue4_mob', 'issue4_pig', 'issue4_other_pig', 'issue4_native_zombie']) {
            command('kill @e[tag=' + tag + ']');
        }
        command('fill -8 63 -8 8 63 8 minecraft:grass_block');
        command('fill -8 64 -8 8 70 8 minecraft:air');
        command('setblock -2 64 0 minecraft:furnace');
        command('setblock -4 64 0 minecraft:furnace');
        command('summon nekojs:issue4_mob 0.5 64 0.5 {Tags:["issue4_mob"],Invulnerable:1b,PersistenceRequired:1b}');
        command('summon minecraft:pig 2.5 64 0.5 {Tags:["issue4_pig"],NoAI:1b,Invulnerable:1b,PersistenceRequired:1b}');
        command('summon minecraft:pig 4.5 64 0.5 {Tags:["issue4_other_pig"],NoAI:1b,Invulnerable:1b,PersistenceRequired:1b}');
        command('summon nekojs:issue4_native_zombie -3.5 64 3.5 {Tags:["issue4_native_zombie"],NoAI:1b,Invulnerable:1b,PersistenceRequired:1b}');
        command('time set 1000');
        phase = 'waiting_for_entities';
        console.info('ISSUE4 READY server.world_commands_issued');
    }
    function findEntity(tag) {
        let found = null;
        for (const entity of level.getAllEntities()) {
            if (entity.entityTags().contains(tag)) {
                requireCondition(found === null, 'multiple entities with fixture tag ' + tag);
                found = entity;
            }
        }
        return found;
    }
    function verifyWorld() {
        const furnacePosition = new BlockPos(-2, 64, 0);
        const otherFurnacePosition = new BlockPos(-4, 64, 0);
        const plainPosition = new BlockPos(-2, 63, -2);
        const otherPlainPosition = new BlockPos(-4, 63, -2);
        runCheck('entity.spawn_attributes_goals_egg', () => {
            equal(mob.getMaxHealth(), 42, 'maxHealth-only builder preserves required mob baseline');
            equal(mob.getHealth(), 42, 'summoned entity initial health');
            let floatGoals = 0;
            let lookGoals = 0;
            for (const wrapped of mob.goalSelector.getAvailableGoals()) {
                if (FloatGoal.class.isInstance(wrapped.getGoal())) floatGoals++;
                if (LookAtGoal.class.isInstance(wrapped.getGoal())) lookGoals++;
            }
            equal(floatGoals, 1, 'native custom FloatGoal installed once');
            equal(lookGoals, 1, 'lookAt player goal installed once');
            requireCondition(BuiltInRegistries.ITEM.getOptional(Identifier.parse('nekojs:issue4_mob_spawn_egg')).isPresent(), 'spawn egg registered');
            mob.pdata().putInt(key, 41);
            equal(mob.pdata().getInt(key), 41, 'server entity initial pdata');
        });
        runCheck('entity.native_constructor_and_attribute_baseline', () => {
            const nativeMob = findEntity('issue4_native_zombie');
            const ZombieClass = Java.type('net.minecraft.world.entity.monster.zombie.Zombie');
            const AttributesApi = Java.type('net.minecraft.world.entity.ai.attributes.Attributes');
            requireCondition(nativeMob !== null && ZombieClass.class.isInstance(nativeMob), 'native Zombie constructor class');
            requireCondition(String(BuiltInRegistries.ENTITY_TYPE.getKey(nativeMob.getType())) === 'nekojs:issue4_native_zombie', 'native factory type identity');
            equal(nativeMob.getMaxHealth(), 44, 'native maxHealth override');
            requireCondition(Math.abs(nativeMob.getAttributeBaseValue(AttributesApi.MOVEMENT_SPEED) - 0.23) < 0.00001,
                'native movement speed retained');
            equal(nativeMob.getAttributeBaseValue(AttributesApi.ARMOR), 2, 'native armor retained');
        });
        runCheck('capability.standard_block_scope', () => {
            stableScope('standard furnace', side => api.blockEnergy(furnacePosition, side), side => api.blockEnergy(otherFurnacePosition, side));
        });
        runCheck('capability.standard_entity_scope', () => {
            stableScope('standard pig', side => api.entityEnergy(pig, side), side => api.entityEnergy(otherPig, side));
        });
        runCheck('capability.custom_block_entity_scope', () => {
            stableScope('custom furnace', side => api.customBlock(furnacePosition, side), side => api.customBlock(otherFurnacePosition, side));
        });
        runCheck('capability.custom_plain_block_scope', () => {
            stableScope('custom grass block', side => api.customBlock(plainPosition, side), side => api.customBlock(otherPlainPosition, side));
        });
        runCheck('capability.custom_entity_scope', () => {
            stableScope('custom pig', side => api.customEntity(pig, side), side => api.customEntity(otherPig, side));
        });
        runCheck('capability.standard_and_custom_item_scope', () => {
            const first = Item.of('minecraft:stick');
            const second = Item.of('minecraft:stick');
            const firstContext = api.itemContext(first);
            const secondContext = api.itemContext(second);
            const storage = api.itemEnergy(first, firstContext);
            requireCondition(storage !== null, 'standard item energy with native location context');
            requireCondition(storage === api.itemEnergy(first, firstContext), 'standard item repeated query stable');
            requireCondition(storage !== api.itemEnergy(second, secondContext), 'standard item two stacks distinct');
            requireCondition(api.itemEnergy(second, secondContext) !== null, 'second stack has standard item handler');
            requireCondition(api.itemEnergy(first, null) === null, 'standard item null location context rejected');
            stableScope('custom item', side => api.customItem(first, side), side => api.customItem(second, side));
        });
        runCheck('pdata.item_components_and_serialization', () => {
            const stack = Item.of('minecraft:stick');
            stack.pdata().putInt('issue4_item', 42);
            equal(stack.pdata().getInt('issue4_item'), 42, 'item custom component pdata');
            const copied = stack.copy();
            const StackApi = Java.type('net.minecraft.world.item.ItemStack');
            const RegistryOps = Java.type('net.minecraft.resources.RegistryOps');
            const NbtOps = Java.type('net.minecraft.nbt.NbtOps');
            const operations = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess());
            const encoded = StackApi.CODEC.encodeStart(operations, copied).getOrThrow();
            const restored = StackApi.CODEC.parse(operations, encoded).getOrThrow();
            equal(restored.pdata().getInt('issue4_item'), 42, 'native ItemStack codec round trip');
            stack.pdata().clear();
            requireCondition(!stack.pdata().contains('issue4_item'), 'item clear removes its data');
            equal(copied.pdata().getInt('issue4_item'), 42, 'item copies do not share mutable data');
        });
        runCheck('capability.energy_transactions_and_serialization', () => testEnergy(api.entityEnergy(pig, NativeDirection.NORTH)));
        runCheck('capability.items_transactions_and_serialization', () => {
            const storage = api.entityItems(pig);
            requireCondition(storage !== null, 'queried entity item storage');
            requireCondition(storage === api.entityItems(pig), 'queried item storage stable');
            const other = api.entityItems(otherPig);
            requireCondition(other !== null && storage !== other, 'different entities do not share item storage');
            testResource('item', storage, api.itemResource(Item.of('minecraft:diamond')), 12, 5, () => Capabilities.itemHandler(2));
        });
        runCheck('capability.fluids_transactions_and_serialization', () => {
            const storage = api.entityFluid(pig);
            requireCondition(storage !== null, 'queried entity fluid storage');
            requireCondition(storage === api.entityFluid(pig), 'queried fluid storage stable');
            const other = api.entityFluid(otherPig);
            requireCondition(other !== null && storage !== other, 'different entities do not share fluid storage');
            testResource('fluid', storage, api.fluidResource(), 500 * api.fluidUnit, 125 * api.fluidUnit, () => Capabilities.fluidTank(1000));
        });
        if (!failed) {
            phase = 'ready';
            console.info('ISSUE4 READY server.native_baseline waiting_for_player=true');
            summary();
        }
    }
    function playerFrom(event) {
        return isFabric ? event.player : event.entity;
    }
    function placePlayer(player) {
        const selector = player.getName().getString();
        requireCondition(new RegExp('^[A-Za-z0-9_]+$').test(selector), 'fixture requires a command-safe player name');
        command('gamemode creative ' + selector);
        command('tp ' + selector + ' 0.5 64 5.5 180 5');
        command('give ' + selector + ' nekojs:issue4_mob_spawn_egg');
        command('give ' + selector + ' minecraft:stick[minecraft:custom_data={issue4_item:42}]');
    }

    ServerEvents.started(event => {
        runCheck('server.safety_and_started', () => {
            requireCondition(String(PlatformApi.getGameDir().toAbsolutePath()).toLowerCase()
                .split(/[\\/]/).some(component => component.includes('issue4')), 'private issue4 game directory');
            requireCondition(String(PlatformApi.getMcVersion()).startsWith('26.'), '26.x transfer API node');
            requireCondition(loader === 'neoforge' || isFabric, 'supported loader');
            server = event.server;
            level = server.overworld();
            api = makeApi();
            requireCondition(level !== null, 'overworld is available');
            prepare();
        });
    });
    ServerEvents.tickPost(event => {
        if (failed || server === null) return;
        ticks++;
        if (phase === 'waiting_for_chunks' && ticks >= 40) {
            runCheck('server.prepare_world', createWorldFixtures);
        }
        if (phase === 'waiting_for_entities' && ticks >= 60) {
            runCheck('server.discover_world_entities', () => {
                mob = findEntity('issue4_mob');
                pig = findEntity('issue4_pig');
                otherPig = findEntity('issue4_other_pig');
                requireCondition(mob !== null && pig !== null && otherPig !== null, 'summoned fixture entities are indexed');
                requireCondition(level.getBlockEntity(new BlockPos(-2, 64, 0)) !== null, 'first furnace exists');
                requireCondition(level.getBlockEntity(new BlockPos(-4, 64, 0)) !== null, 'second furnace exists');
            });
            if (!failed) verifyWorld();
        }
        if (phase === 'ready' && firstLoginTick !== null && !entityWriteDone && ticks - firstLoginTick >= 120) {
            runCheck('pdata.entity_auto_write', () => {
                mob.pdata().putInt(key, 42);
                equal(mob.pdata().getInt(key), 42, 'entity authoritative pdata after mutation');
                entityWriteDone = true;
            });
        }
        while (!failed && pendingRespawns.length > 0 && pendingRespawns[0].due <= ticks) {
            const player = pendingRespawns.shift().player;
            runCheck('pdata.player_respawn_copy', () => {
                equal(player.pdata().getInt(key), 42, 'replacement player copied pdata without fixture rewriting it');
                placePlayer(player);
            });
        }
    });
    PlayerEvents.loggedIn(event => {
        runCheck('pdata.player_login_write', () => {
            const player = playerFrom(event);
            const identity = String(player.getUUID());
            if (logins.has(identity)) {
                equal(player.pdata().getInt(key), 42, 'reconnected player retained pdata');
                console.info('ISSUE4 PASS pdata.player_reconnect_retained');
            } else if (player.pdata().contains(key)) {
                equal(player.pdata().getInt(key), 42, 'persisted player pdata from a prior isolated session');
                console.info('ISSUE4 PASS pdata.player_login_persisted');
            }
            logins.add(identity);
            player.pdata().putInt(key, 42);
            equal(player.pdata().getInt(key), 42, 'login authoritative player pdata');
            if (firstLoginTick === null) firstLoginTick = ticks;
            placePlayer(player);
        });
    });
    PlayerEvents.respawned(event => {
        if (!failed) pendingRespawns.push({ player: playerFrom(event), due: ticks + 1 });
    });
    ServerEvents.stopping(event => {
        summary();
        console.info('ISSUE4 SUMMARY server.final failed=' + failed + ' checks=' + checks.size
            + ' native_baseline=' + (phase === 'ready') + ' respawn_copy=' + checks.has('pdata.player_respawn_copy')
            + ' client_pixels_and_mirrors_require_client_evidence=true');
    });
    console.info('ISSUE4 READY server.listeners_installed loader=' + loader);
})();
