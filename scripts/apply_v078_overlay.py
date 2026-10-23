#!/usr/bin/env python3
"""Apply verified 0.7.8 overlay on the restored, exact 0.7.7.1 baseline.
This never touches installed app data. Fail atomically on any version conflict.
"""
from pathlib import Path
from zipfile import ZipFile
import json,hashlib,base64,io,os
root=Path(__file__).resolve().parents[1]
dir=root/'overlays/v078'
manifest=json.loads((dir/'manifest.json').read_text('utf-8'))
blob=base64.b64decode((dir/'payload.b64').read_text().strip(),validate=True)
sha=lambda b:hashlib.sha256(b).hexdigest()
with ZipFile(io.BytesIO(blob)) as z:
    assert set(z.namelist())==set(manifest),"Invalid source member list"
    files={p:z.read(p) for p in z.namelist()}
for rel,info in manifest.items():
    p=root/rel
    assert rel and not rel.startswith('/') and '..' not in Path(rel).parts,rel
    assert info['before'] is not None and p.is_file() or info['before'] is None and not p.exists(),f"Unexpected 0.7.7.1 path: {rel}"
    if info['before'] is not None:
      assert sha(p.read_bytes())==info['before'],f"Wrong base file: {rel}"
    assert len(files[rel])==info['size'] and sha(files[rel])==info['after'],f"Corrupt overlay member: {rel}"
for rel,content in files.items():
    target=root/rel;target.parent.mkdir(parents=True,exist_ok=True)
    tmp=target.with_name(target.name+'.v078-new')
    tmp.write_bytes(content)
    os.replace(tmp,target)
    print('Verified v0.7.8',rel)
print('Hasel 360 v0.7.8 overlay applied; existing user databases unchanged.')