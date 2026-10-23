#!/usr/bin/env python3
"""Restore checksum-verified Hasel 360 0.7.7 source onto 0.7.6 baseline."""
from pathlib import Path
import json,hashlib,base64,io,tarfile,lzma
root=Path(__file__).resolve().parents[1]
manifest=json.loads((root/'overlays/v077/manifest.json').read_text())
raw=base64.b64decode((root/'overlays/v077/overlay.b64').read_text().strip(),validate=True)
if hashlib.sha256(raw).hexdigest()!=manifest.pop('__payload_sha256__'):raise SystemExit('0.7.7 payload checksum differs')
with tarfile.open(fileobj=io.BytesIO(lzma.decompress(raw)),mode='r:') as archive:
 members=archive.getmembers()
 if {m.name for m in members}!=set(manifest):raise SystemExit('0.7.7 file list mismatch')
 for m in members:
  path=Path(m.name)
  if not m.isfile() or path.is_absolute() or '..' in path.parts or m.issym() or m.islnk():raise SystemExit('unsafe overlay file')
  dest=root/path
  prev=hashlib.sha256(dest.read_bytes()).hexdigest() if dest.exists() else ''
  if prev!=manifest[m.name]['before']:
   if m.name=='README.md':print('non-executable README baseline differs')
   else:raise SystemExit('0.7.6 base mismatch: '+m.name+' actual='+prev)
  content=archive.extractfile(m).read()
  if hashlib.sha256(content).hexdigest()!=manifest[m.name]['after']:raise SystemExit('0.7.7 file checksum mismatch: '+m.name)
  dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(content)
  print('Verified',m.name)
print('Hasel 360 version 0.7.7 overlay restored')