#!/usr/bin/env python3
"""Restore v0.8.2 only over the exact checked v0.8.1 source."""
from pathlib import Path
from zipfile import ZipFile
import hashlib,base64,io,json,os

root=Path(__file__).resolve().parents[1]
payload=base64.b64decode((root/'overlays/v082/payload.b64').read_text('utf-8'),validate=False)
sha=lambda data:hashlib.sha256(data).hexdigest()
with ZipFile(io.BytesIO(payload)) as z:
    manifest=json.loads(z.read('V082-OVERLAY-MANIFEST.json').decode('utf-8'))
    assert set(z.namelist())==set(manifest)|{'V082-OVERLAY-MANIFEST.json'},'Unexpected archive contents'
    output={}
    for rel,entry in manifest.items():
        p=Path(rel)
        assert not p.is_absolute() and '..' not in p.parts and p.parts,'Unsafe path'
        target=root/p
        expected=entry['before']
        assert (not target.exists() if expected is None else target.is_file() and sha(target.read_bytes())==expected),f'Base source conflict: {rel}'
        data=z.read(rel)
        assert len(data)==entry['size'] and sha(data)==entry['after'],f'Corrupt source: {rel}'
        output[target]=data
for target,data in output.items():
    target.parent.mkdir(parents=True,exist_ok=True)
    temp=target.with_suffix(target.suffix+'.v082-tmp')
    temp.write_bytes(data)
    os.replace(temp,target)
    print('Verified v0.8.2',target.relative_to(root))
gradle=(root/'app/build.gradle').read_text('utf-8')
assert "versionName '0.8.2'" in gradle and "applicationIdSuffix '.dev082'" in gradle,'Wrong version'
print('v0.8.2 restored over exact v0.8.1 baseline; installed app data untouched.')
