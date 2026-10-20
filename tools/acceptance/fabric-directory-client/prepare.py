"""Prepare isolated client fixtures without compiling or launching Minecraft."""
import argparse
import hashlib
import json
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--node', choices=['26.1.2-fabric', '26.2.0-fabric'], default='26.2.0-fabric')
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--java-home', type=Path, required=True)
parser.add_argument('--gradle-home', type=Path, required=True)
args = parser.parse_args()
repo = Path(__file__).resolve().parents[3]
output = args.output.resolve()
assert output.is_relative_to(repo / 'build/priority-closeout'), 'Use a new isolated acceptance directory'
assert all(character not in output.as_posix() for character in "'\r\n"), 'Unsupported path quoting'
output.mkdir(parents=True, exist_ok=False)
game = output / 'game'
(game / 'mods').mkdir(parents=True)

def write(relative, value):
    destination = game / relative
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(value, encoding='utf-8')

def document(relative, value):
    write(relative, json.dumps(value, indent=2) + '\n')

document('nekojs/packs/global_marker/manifest.json', {
    'formatVersion': 1, 'id': 'global_marker', 'version': '1.0.0', 'entries': {},
})
write('nekojs/assets/neko_acceptance/marker.txt', 'ROOT')
document('nekojs/assets/neko_acceptance/lang/en_us.json', {
    'neko.acceptance.root': 'ROOT', 'neko.acceptance.shared': 'ROOT',
})
write('nekojs/packs/global_marker/assets/neko_acceptance/marker.txt', 'GLOBAL')
document('nekojs/packs/global_marker/assets/neko_acceptance/lang/en_us.json', {
    'neko.acceptance.global': 'GLOBAL', 'neko.acceptance.shared': 'GLOBAL',
})
write('nekojs/startup_scripts/client_assets.js', '''
RegistryEvents.register(event => {
    event.item('neko_acceptance:auto_item', build => {
        build.parentModel = 'minecraft:item/generated';
        build.texture('layer0', 'minecraft:item/diamond');
    });
});
'''.lstrip())
write('options.txt', 'lang:en_us\nrenderDistance:2\nsimulationDistance:2\nmaxFps:60\n')

for name in ('FabricDirectoryClientAcceptance.java', 'prepare.py', 'run.py'):
    (output / name).write_bytes((Path(__file__).parent / name).read_bytes())

init = """gradle.projectsEvaluated {
    def target = gradle.rootProject.findProject(':%s')
    if (target == null) return
    target.tasks.register('directoryClientClasspath') {
        dependsOn(target.tasks.named('classes'))
        doLast { new File('%s').text = target.sourceSets.main.compileClasspath.asPath }
    }
    target.extensions.getByName('loom').runs.named('client') {
        runDir('%s')
        vmArg('-Dfabric.client.gametest')
        vmArg('-Dfabric.client.gametest.modid=neko_directory_client_acceptance')
        vmArg('-Dneko.acceptance.output=%s')
        vmArg('-Dneko.acceptance.game=%s')
    }
}
""" % (args.node, (output / 'compile-classpath.txt').as_posix(), game.as_posix(), output.as_posix(), game.as_posix())
(output / 'client.gradle').write_text(init, encoding='utf-8')
revision = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=repo, text=True).strip()
files = subprocess.check_output(['git', 'ls-files', '--', 'common/src/main', 'src/main', 'src/fabric',
                                 f'versions/{args.node}/src/main'], cwd=repo, text=True).splitlines()
source_hashes = {name: hashlib.sha256((repo / name).read_bytes()).hexdigest() for name in files}
(output / 'configuration.json').write_text(json.dumps({
    'repo': str(repo), 'output': str(output), 'node': args.node, 'javaHome': str(args.java_home.resolve()),
    'gradleHome': str(args.gradle_home.resolve()), 'revision': revision, 'sources': source_hashes,
    'entrypointSha256': hashlib.sha256((output / 'FabricDirectoryClientAcceptance.java').read_bytes()).hexdigest(),
    'preparedOnly': True,
}, indent=2) + '\n', encoding='utf-8')
print(f'Prepared only: {output}')
