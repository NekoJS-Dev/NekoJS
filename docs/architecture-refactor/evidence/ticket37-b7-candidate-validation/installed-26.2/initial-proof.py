import importlib.util
import os
import socket
import sys
import time
from pathlib import Path

root = Path(sys.argv[1]).resolve()
helper_path = Path(sys.argv[2]).resolve()
spec = importlib.util.spec_from_file_location('owned_rcon', helper_path)
helper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helper)
properties = dict(line.split('=', 1) for line in (root / 'server.properties').read_text().splitlines() if '=' in line)
port = int(properties['rcon.port'])
password = properties['rcon.password']


def command(text):
    with socket.create_connection(('127.0.0.1', port), timeout=15) as connection:
        connection.settimeout(30)
        helper.send_packet(connection, 1, 3, password)
        auth = helper.recv_packet(connection)
        if auth is None or auth[0] == -1:
            raise RuntimeError('Owned RCON authentication failed')
        helper.send_packet(connection, 10, 2, text)
        response = helper.recv_packet(connection)
        if response is None:
            raise RuntimeError('Owned RCON response missing')
        print('COMMAND=' + text, flush=True)
        print('RESPONSE=' + response[2], flush=True)
        return response[2]


failure = None
try:
    command('nekojs registry')
    command('nekojs probe typescript')
    command('nekojs probe python')
    command('summon minecraft:pig 0 1 0 {Tags:["b7_effect_proof"],NoAI:1b,PersistenceRequired:1b}')
    before = command('effect give @e[type=minecraft:pig,tag=b7_effect_proof,limit=1] ticket37_b7:proof_effect 60 0 true')
    if 'Applied effect' not in before:
        raise RuntimeError('Actual installed dynamic MobEffect application before reload did not succeed')
    entity = command('data get entity @e[type=minecraft:pig,tag=b7_effect_proof,limit=1]')
    if 'ticket37_b7:proof_effect' not in entity:
        raise RuntimeError('Actual living entity effect data does not contain the registered MobEffect')
    reload_result = command('nekojs reload server')
    if 'phase=COMMIT - no errors' not in reload_result:
        raise RuntimeError('Installed identical-definition reload did not commit')
    time.sleep(2)
    command('nekojs registry')
    command('effect clear @e[type=minecraft:pig,tag=b7_effect_proof,limit=1] ticket37_b7:proof_effect')
    after = command('effect give @e[type=minecraft:pig,tag=b7_effect_proof,limit=1] ticket37_b7:proof_effect 60 1 true')
    if 'Applied effect' not in after:
        raise RuntimeError('Actual installed dynamic MobEffect application after reload did not succeed')
    entity_after = command('data get entity @e[type=minecraft:pig,tag=b7_effect_proof,limit=1]')
    if 'ticket37_b7:proof_effect' not in entity_after:
        raise RuntimeError('Post-reload living entity effect data missing')
    print('ACTUAL_INSTALLED_SINGLE_SERVER_EFFECT_BEFORE_AFTER_RELOAD_PASS=true', flush=True)
    print('CLIENTS_AND_MULTIPLAYER_NOT_TESTED=true', flush=True)
except Exception as error:
    failure = error
    print('PROOF_FAILURE=' + str(error), flush=True)
finally:
    try:
        command('stop')
    except Exception as stop_error:
        print('OWNED_STOP_FAILURE=' + str(stop_error), flush=True)
        if failure is None:
            failure = stop_error
if failure is not None:
    sys.exit(1)
