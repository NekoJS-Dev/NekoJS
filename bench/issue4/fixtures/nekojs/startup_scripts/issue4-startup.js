(function () {
    const PlatformApi = Java.type('com.tkisor.nekojs.platform.Platform');
    const gameDirectory = String(PlatformApi.getGameDir().toAbsolutePath());
    if (!gameDirectory.toLowerCase().split(/[\\/]/).some(component => component.includes('issue4'))) {
        throw new Error('ISSUE4 FAIL safety: use a new private game directory whose path contains an issue4 component');
    }
    if (!String(PlatformApi.getMcVersion()).startsWith('26.')) {
        throw new Error('ISSUE4 FAIL safety: this fixture targets the 26.x transfer APIs, not 1.21.1');
    }
    const loader = String(PlatformApi.getLoaderId());
    if (loader !== 'fabric' && loader !== 'neoforge') {
        throw new Error('ISSUE4 FAIL safety: unsupported loader ' + loader);
    }
    const FloatGoal = Java.type('net.minecraft.world.entity.ai.goal.FloatGoal');
    const NativeDirection = Java.type('net.minecraft.core.Direction');
    const WeakHashMap = Java.type('java.util.WeakHashMap');
    const HashMap = Java.type('java.util.HashMap');
    const energyByOwner = new WeakHashMap();
    const itemsByOwner = new WeakHashMap();
    const fluidsByOwner = new WeakHashMap();
    const plainBlocksByLevel = new WeakHashMap();

    function energy(owner) {
        let storage = energyByOwner.get(owner);
        if (storage === null) {
            storage = Capabilities.energyStorage(1000, 1000, 1000);
            energyByOwner.put(owner, storage);
        }
        return storage;
    }
    function items(owner) {
        let storage = itemsByOwner.get(owner);
        if (storage === null) {
            storage = Capabilities.itemHandler(2);
            itemsByOwner.put(owner, storage);
        }
        return storage;
    }
    function fluids(owner) {
        let storage = fluidsByOwner.get(owner);
        if (storage === null) {
            storage = Capabilities.fluidTank(1000);
            fluidsByOwner.put(owner, storage);
        }
        return storage;
    }
    function plainBlockEnergy(level, position) {
        let byPosition = plainBlocksByLevel.get(level);
        if (byPosition === null) {
            byPosition = new HashMap();
            plainBlocksByLevel.put(level, byPosition);
        }
        const key = position.immutable();
        let storage = byPosition.get(key);
        if (storage === null) {
            storage = Capabilities.energyStorage(1000, 1000, 1000);
            byPosition.put(key, storage);
        }
        return storage;
    }
    function sidedEnergy(owner, side) {
        return side === NativeDirection.NORTH ? energy(owner) : null;
    }

    RegistryEvents.register(event => {
        try {
            event.entityType('nekojs:issue4_mob', build => {
                build.category = 'creature';
                build.size(0.6, 1.8);
                build.attributes(build => build.maxHealth(42));
                build.goals(build => build.customClass(0, FloatGoal).lookAt(2, 'minecraft:player', 12));
                build.spawnEgg(0x22AA55, 0xFFFFFF);
            });
            event.entityType('nekojs:issue4_native_zombie', build => {
                build.entityClass(Java.type('net.minecraft.world.entity.monster.zombie.Zombie'));
                build.renderer = 'net.minecraft.client.renderer.entity.ZombieRenderer';
                build.attributes(build => build.maxHealth(44));
            });
            console.info('ISSUE4 READY startup.entity_declared loader=' + loader + ' renderer=default_humanoid');
        } catch (error) {
            console.error('ISSUE4 FAIL startup.entity_registration ' + error);
            throw error;
        }
    });

    function registerNative(payload) {
        const NativeBlock = Java.type('net.neoforged.neoforge.capabilities.BlockCapability');
        const NativeEntity = Java.type('net.neoforged.neoforge.capabilities.EntityCapability');
        const NativeItem = Java.type('net.neoforged.neoforge.capabilities.ItemCapability');
        const EnergyApi = Java.type('net.neoforged.neoforge.transfer.energy.EnergyHandler');
        const customBlock = NativeBlock.createSided(Identifier.parse('nekojs:issue4_energy'), EnergyApi.class);
        const customEntity = NativeEntity.createSided(Identifier.parse('nekojs:issue4_energy'), EnergyApi.class);
        const customItem = NativeItem.create(Identifier.parse('nekojs:issue4_energy'), EnergyApi.class, NativeDirection.class);
        payload.registerBlockEntityContext('minecraft:furnace', 'energy', sidedEnergy);
        payload.registerEntity('minecraft:pig', 'energy', sidedEnergy);
        payload.registerItem('minecraft:stick', 'energy', (stack, context) => context === null ? null : energy(stack));
        payload.registerEntity('minecraft:pig', 'item', (entity, context) => items(entity));
        payload.registerEntity('minecraft:pig', 'fluid', (entity, side) => side === NativeDirection.NORTH ? fluids(entity) : null);
        payload.registerBlockEntityNative('minecraft:furnace', customBlock, sidedEnergy);
        payload.registerEntityNative('minecraft:pig', customEntity, sidedEnergy);
        payload.registerItemNative('minecraft:stick', customItem, sidedEnergy);
        payload.registerBlockNative('minecraft:grass_block', customBlock,
            (level, position, state, blockEntity, side) => side === NativeDirection.NORTH ? plainBlockEnergy(level, position) : null);
    }

    function registerFabric(payload) {
        const BlockLookup = Java.type('net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup');
        const EntityLookup = Java.type('net.fabricmc.fabric.api.lookup.v1.entity.EntityApiLookup');
        const ItemLookup = Java.type('net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup');
        const EnergyApi = Java.type('com.tkisor.nekojs.fabric.capability.FabricEnergyHandler');
        const customBlock = BlockLookup.get(Identifier.parse('nekojs:issue4_energy'), EnergyApi.class, NativeDirection.class);
        const customEntity = EntityLookup.get(Identifier.parse('nekojs:issue4_energy'), EnergyApi.class, NativeDirection.class);
        const customItem = ItemLookup.get(Identifier.parse('nekojs:issue4_energy'), EnergyApi.class, NativeDirection.class);
        payload.registerBlockEntityContext('minecraft:furnace', 'energy', sidedEnergy);
        payload.registerEntityContext('minecraft:pig', 'energy', sidedEnergy);
        payload.registerItemContext('minecraft:stick', 'energy', (stack, context) => context === null ? null : energy(stack));
        payload.registerEntityContext('minecraft:pig', 'item', (entity, side) => side === NativeDirection.NORTH ? items(entity) : null);
        payload.registerEntityContext('minecraft:pig', 'fluid', (entity, side) => side === NativeDirection.NORTH ? fluids(entity) : null);
        payload.registerBlockEntityLookup('minecraft:furnace', customBlock, sidedEnergy);
        payload.registerEntityLookup('minecraft:pig', customEntity, sidedEnergy);
        payload.registerItemLookup('minecraft:stick', customItem, sidedEnergy);
        payload.registerBlockLookup('minecraft:grass_block', customBlock,
            (level, position, state, blockEntity, side) => side === NativeDirection.NORTH ? plainBlockEnergy(level, position) : null);
    }

    CapabilityEvents.register(event => {
        try {
            if (loader === 'neoforge') {
                registerNative(event);
            } else {
                registerFabric(event);
            }
            console.info('ISSUE4 READY startup.capabilities_collected loader=' + loader + ' queries_not_yet_run=true');
        } catch (error) {
            console.error('ISSUE4 FAIL startup.capabilities_registration ' + error);
            throw error;
        }
    });
    console.info('ISSUE4 READY startup.listeners_installed loader=' + loader);
})();
