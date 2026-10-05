"""Validate the release set and write portable SHA-256 checksums."""
from pathlib import Path
import hashlib, json, sys, zipfile

root=Path(__file__).resolve().parents[1]
folder=Path(sys.argv[1]) if len(sys.argv)>1 else root/'build/releases/1.1.0'
targets=json.loads((root/'ports/fabric/targets.json').read_text())
forge=['1.8.9','1.9.4','1.10.2','1.11.2','1.12.2','1.13.2']
expected={f'MusicIsland-forge-{v}-1.1.0.jar' for v in forge}
expected.update(f'MusicIsland-fabric-{v}-1.1.0.jar' for v in targets)
expected.add('MusicIsland-fabric-26.1-26.3-1.1.0.jar')
actual={p.name for p in folder.glob('*.jar')}
if expected!=actual:raise SystemExit(f'Release set mismatch: missing {expected-actual}; unexpected {actual-expected}')
helper=(root/'build/music-island-helper/MusicSessionBridge.exe').read_bytes()
icon=(root/'src/main/resources/assets/musicisland/logo.png').read_bytes()
checksums=[]
for name in sorted(expected):
    path=folder/name
    with zipfile.ZipFile(path) as jar:
        assert jar.testzip() is None, name+' corrupt ZIP'
        assert jar.read('assets/musicisland/music/MusicSessionBridge.exe')==helper,name+' helper differs'
        assert jar.read('assets/musicisland/logo.png')==icon,name+' icon differs'
        assert 'META-INF/THIRD_PARTY_NOTICES.md' in jar.namelist(),name+' missing notices'
        assert not any('/smoke/' in entry or entry=='musicisland-smoke-pass.txt' for entry in jar.namelist()),name+' test mod leaked'
        if 'fabric.mod.json' in jar.namelist():
            metadata=json.loads(jar.read('fabric.mod.json'))
            assert metadata['environment']=='client' and metadata['version']=='1.1.0'
            if '26.1-26.3' in name:
                assert metadata['depends']['minecraft']==['26.1','26.1.1','26.1.2','26.2','26.3']
            else:
                mc=name[len('MusicIsland-fabric-'):-len('-1.1.0.jar')]
                assert metadata['depends']['minecraft']==mc
                assert targets[mc]['apiId'] in metadata['depends']
            for mixin in metadata.get('mixins',[]):
                config=json.loads(jar.read(mixin))
                assert config['package'].endswith('.mixin'),name+' unsafe mixin package'
                assert config['refmap'] in jar.namelist(),name+' missing refmap'
        elif 'mcmod.info' in jar.namelist():
            metadata=json.loads(jar.read('mcmod.info'))[0]
            mc=name[len('MusicIsland-forge-'):-len('-1.1.0.jar')]
            assert metadata['mcversion']==mc and metadata['version']=='1.1.0'
        else:
            assert b'version="1.1.0"' in jar.read('META-INF/mods.toml')
    checksums.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+name)
(folder/'SHA256SUMS.txt').write_text('\n'.join(checksums)+'\n',encoding='utf-8')
print(f'PASS: {len(expected)} release jars, metadata, bridge, logo, mixins, notices and test isolation')
