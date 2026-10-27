#!/usr/bin/env python3
"""Restore checksum-verified 0.8.1 changes on exact 0.8.0 CI source."""
from pathlib import Path
import base64,hashlib,io,json,lzma,os,tarfile

root=Path(__file__).resolve().parents[1]
sha=lambda b:hashlib.sha256(b).hexdigest()
encoded=(root/'overlays/v081/source.b85').read_text(encoding='ascii').strip()
data=lzma.decompress(base64.b85decode(encoded))
with tarfile.open(fileobj=io.BytesIO(data),mode='r:') as tf:
    names=tf.getnames()
    assert len(set(names))==len(names) and 'manifest.json' in names
    manifest=json.load(tf.extractfile('manifest.json'))
    assert set(names)==set(manifest)|{'manifest.json'}
    content={}
    for relative,meta in manifest.items():
        path=Path(relative)
        assert not path.is_absolute() and '..' not in path.parts
        b=tf.extractfile(relative).read()
        assert len(b)==meta['size'] and sha(b)==meta['after'],relative
        target=root/path
        if meta['before'] is None:
            assert not target.exists(), 'Unexpected existing file: '+relative
        else:
            assert target.is_file() and sha(target.read_bytes())==meta['before'], 'Base source mismatch: '+relative
        content[target]=b
for target,b in content.items():
    target.parent.mkdir(parents=True,exist_ok=True)
    tmp=target.with_name(target.name+'.v081-tmp')
    tmp.write_bytes(b)
    os.replace(tmp,target)
print('Verified 0.8.1 source overlay applied:',len(content),'files')
gradle=(root/'app/build.gradle').read_text()
assert "versionName '0.8.1'" in gradle and "applicationIdSuffix '.dev081'" in gradle
print('Version 0.8.1 isolated from installed stable application')
