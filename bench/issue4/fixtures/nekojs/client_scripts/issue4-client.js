(function () {
    const client = Minecraft.getInstance();
    const BuiltInRegistries = Java.type('net.minecraft.core.registries.BuiltInRegistries');
    const PlatformApi = Java.type('com.tkisor.nekojs.platform.Platform');
    const loader = String(PlatformApi.getLoaderId());
    const checks = new Set();
    const key = 'issue4_value';
    let failed = false;
    let activeTicks = 0;
    let sawInitialEntityMirror = false;
    let initialPlayer = null;
    let lastSummary = '';

    function requireCondition(condition, description) {
        if (!condition) throw new Error('ISSUE4 assertion failed: ' + description);
    }
    function summary() {
        const complete = checks.has('painter.hud.first_frame') && checks.has('painter.screen.first_frame')
            && checks.has('pdata.player_mirror_42') && checks.has('pdata.entity_mirror_42')
            && checks.has('pdata.player_readonly') && checks.has('pdata.entity_readonly')
            && checks.has('pdata.item_inventory_components_42');
        const text = 'status=' + (failed ? 'FAILED' : complete ? 'READY_FOR_SCREENSHOT' : 'PENDING')
            + ' loader=' + loader + ' passed=' + checks.size + ' initial_entity_41=' + sawInitialEntityMirror
            + ' replacement_player_mirror=' + checks.has('pdata.replacement_player_mirror_42')
            + ' pixels_require_external_screenshot=true';
        if (text !== lastSummary) {
            lastSummary = text;
            console.info('ISSUE4 SUMMARY client ' + text);
        }
    }
    function runCheck(name, action) {
        if (failed || checks.has(name)) return;
        try {
            action();
            checks.add(name);
            console.info('ISSUE4 PASS ' + name);
            summary();
        } catch (error) {
            failed = true;
            console.error('ISSUE4 FAIL ' + name + ' ' + error);
            summary();
        }
    }
    function rejectMirrorWrite(entity) {
        const pdata = entity.pdata();
        requireCondition(Number(pdata.getInt(key)) === 42, 'mirror has the authoritative value before write rejection');
        let rejected = false;
        try {
            pdata.putInt(key, 999);
        } catch (error) {
            requireCondition(String(error).includes('NEKO-4013'), 'readonly write raised the documented diagnostic: ' + error);
            rejected = true;
        }
        requireCondition(rejected, 'client mirror accepted a write');
        requireCondition(Number(entity.pdata().getInt(key)) === 42, 'failed write changed mirror data');
    }
    function paint(painter, label, top) {
        painter.rect(16, top, 156, 66, 0xE6000000)
            .outline(16, top, 156, 66, 0xFF22CC66)
            .textureFull('minecraft:textures/block/stone.png', 24, top + 34, 24, 24)
            .text(label, 24, top + 8, 0xFFFFFFFF)
            .text(failed ? 'FAIL' : 'ISSUE4', 55, top + 38, failed ? 0xFFFF4444 : 0xFF22CC66)
            .text(checks.has('pdata.player_readonly') && checks.has('pdata.entity_readonly')
                ? 'pdata 42 / readonly' : 'pdata pending', 24, top + 21, 0xFFFFFF55);
    }

    ClientEvents.hud(painter => {
        if (client.player === null || failed) return;
        try {
            paint(painter, 'HUD / ' + loader, 16);
            runCheck('painter.hud.first_frame', () => requireCondition(painter.width >= 172 && painter.height >= 82, 'HUD viewport fits fixture'));
        } catch (error) {
            runCheck('painter.hud.render_failure', () => { throw error; });
        }
    });
    ClientEvents.screenRender(event => {
        if (client.player === null || failed) return;
        try {
            paint(event.painter, 'SCREEN / ' + loader, 88);
            runCheck('painter.screen.first_frame', () => requireCondition(event.painter.width >= 172 && event.painter.height >= 154, 'screen viewport fits fixture'));
        } catch (error) {
            runCheck('painter.screen.render_failure', () => { throw error; });
        }
    });
    function observeMirrors() {
        activeTicks++;
        if (initialPlayer === null) initialPlayer = client.player;
        const playerValue = Number(client.player.pdata().getInt(key));
        if (playerValue === 42) {
            runCheck('pdata.player_mirror_42', () => requireCondition(playerValue === 42, 'automatic player mirror'));
            runCheck('pdata.player_readonly', () => rejectMirrorWrite(client.player));
            if (client.player !== initialPlayer) {
                runCheck('pdata.replacement_player_mirror_42', () => rejectMirrorWrite(client.player));
            }
        }
        for (let slot = 0; slot < 36; slot++) {
            const stack = client.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.pdata().getInt('issue4_item') === 42) {
                runCheck('pdata.item_inventory_components_42', () =>
                    requireCondition(stack.pdata().getInt('issue4_item') === 42, 'vanilla item component synchronization'));
                break;
            }
        }
        for (const entity of client.level.entitiesForRendering()) {
            if (String(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())) !== 'nekojs:issue4_mob') continue;
            const value = Number(entity.pdata().getInt(key));
            if (value === 41 && !sawInitialEntityMirror) {
                sawInitialEntityMirror = true;
                console.info('ISSUE4 PASS pdata.entity_initial_tracking_41');
            }
            if (value === 42) {
                runCheck('pdata.entity_mirror_42', () => requireCondition(value === 42, 'automatic entity mirror after server mutation'));
                runCheck('pdata.entity_readonly', () => rejectMirrorWrite(entity));
            }
        }
        if (activeTicks >= 1200 && (!checks.has('pdata.player_mirror_42') || !checks.has('pdata.entity_mirror_42')
                || !checks.has('pdata.item_inventory_components_42'))) {
            runCheck('pdata.client_mirror_timeout', () => { throw new Error('Expected entity, player and item client data within 1200 active client ticks'); });
        }
        summary();
    }
    ClientEvents.tickPost(event => {
        if (client.player === null || client.level === null || failed) return;
        try {
            observeMirrors();
        } catch (error) {
            runCheck('pdata.client_tick_failure', () => { throw error; });
        }
    });
    console.info('ISSUE4 READY client.listeners_installed loader=' + loader
        + ' render_pass_markers_mean_calls_issued_not_pixel_validation=true');
})();
