#!/usr/bin/env python3
"""Restore integrity-verified Hasel 360 0.7.6 changes onto 0.7.5 Android sources."""
from pathlib import Path
import base64,hashlib,io,json,lzma,tarfile
root=Path(__file__).resolve().parents[1]
overlay=root/'overlays/v076'
manifest=json.loads((overlay/'manifest.json').read_text(encoding='utf8'))
blob=base64.b64decode((overlay/'overlay.b64').read_text().strip(),validate=True)
payload_hash=manifest.pop('__payload_sha256__')
if hashlib.sha256(blob).hexdigest()!=payload_hash:raise SystemExit('v0.7.6 overlay integrity mismatch')
with tarfile.open(fileobj=io.BytesIO(lzma.decompress(blob)),mode='r:') as archive:
 members=archive.getmembers()
 if {x.name for x in members}!=set(manifest):raise SystemExit('Manifest has extra/missing paths')
 for m in members:
  rel=Path(m.name)
  if not m.isfile() or m.issym() or m.islnk() or rel.is_absolute() or '..' in rel.parts:raise SystemExit('Unsafe overlay path')
  dest=root/rel
  expected=manifest[m.name]
  before=hashlib.sha256(dest.read_bytes()).hexdigest() if dest.exists() else ''
  if before!=expected['before']:raise SystemExit('Wrong v0.7.5 base for '+m.name+' '+before)
  data=archive.extractfile(m).read()
  if hashlib.sha256(data).hexdigest()!=expected['after']:raise SystemExit('File checksum mismatch for '+m.name)
  dest.parent.mkdir(parents=True,exist_ok=True)
  dest.write_bytes(data)
  print('Verified',rel)
print('Hasel 360 v0.7.6 overlay completed')