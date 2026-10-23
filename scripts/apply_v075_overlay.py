#!/usr/bin/env python3
"""Atomically restore v0.7.5 to a checksum-verified v0.7.4 base."""
from pathlib import Path
import json,hashlib,tarfile,lzma,base64,io
root=Path(__file__).resolve().parents[1]
manifest=json.loads((root/'overlays/v075/manifest.json').read_text(encoding='utf-8'))
encoded=''.join((root/'overlays/v075/overlay.b64').read_text().split())
data=base64.b64decode(encoded,validate=True)
if hashlib.sha256(data).hexdigest()!=manifest.pop('__payload_sha256__'):raise SystemExit('v075 payload integrity mismatch')
with tarfile.open(fileobj=io.BytesIO(lzma.decompress(data)),mode='r:') as a:
 entries=a.getmembers()
 if set(x.name for x in entries)!=set(manifest):raise SystemExit('v075 manifest mismatch')
 pending=[]
 for e in entries:
  rel=Path(e.name)
  if not e.isfile() or rel.is_absolute() or '..' in rel.parts or e.issym() or e.islnk():raise SystemExit('Unsafe v075 archive member')
  dest=root/rel; before=hashlib.sha256(dest.read_bytes()).hexdigest() if dest.exists() else ''
  if before!=manifest[e.name]['before']:
   if e.name in {'BUILD-STATUS.md','README.md'}: print('Non-functional documentation baseline differs:',e.name)
   else: raise SystemExit('Unexpected v074 executable file: '+e.name+' actual='+before)
  content=a.extractfile(e).read()
  if hashlib.sha256(content).hexdigest()!=manifest[e.name]['after']:raise SystemExit('v075 file integrity mismatch: '+e.name)
  pending.append((dest,content))
for path,content in pending:
 path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(content)
 print('v075 restored',path.relative_to(root))
print('Hasel 360 v075 checksum-verified overlay complete')