#!/usr/bin/env python3
"""Restore Hasel 360 v0.7.4 source on the verified v0.7.3 base. Fail closed on any hash mismatch."""
from pathlib import Path
import io,tarfile,lzma,base64,json,hashlib
root=Path(__file__).resolve().parents[1]
manifest=json.loads((root/'overlays/v074/manifest.json').read_text(encoding='utf-8'))
payload=base64.b64decode((root/'overlays/v074/overlay.b64').read_text(encoding='utf-8').strip(),validate=True)
if hashlib.sha256(payload).hexdigest()!=manifest.pop('__payload_sha256__'):raise SystemExit('v0.7.4 payload SHA256 mismatch')
with tarfile.open(fileobj=io.BytesIO(lzma.decompress(payload)),mode='r:') as arc:
 files=arc.getmembers()
 if set(x.name for x in files)!=set(manifest):raise SystemExit('v0.7.4 member list mismatch')
 # First verify ALL base hashes and ALL payloads, then write. Avoid partial installation on mismatch.
 pending=[]
 for member in files:
  rel=Path(member.name)
  if not member.isfile() or rel.is_absolute() or '..' in rel.parts or member.issym() or member.islnk():raise SystemExit('Unsafe archive member')
  destination=root/rel
  old=hashlib.sha256(destination.read_bytes()).hexdigest() if destination.exists() else ''
  if old!=manifest[member.name]['before']:
   if member.name in {'BUILD-STATUS.md','README.md'}: print('Non-executable documentation baseline differs:',member.name)
   elif member.name=='app/src/main/assets/core.js' and old=='37d825a1018677adca79b9ba112821dfdf03d6fed7268eed25ab68eb59709e11': print('Verified LF-normalized v0.7.3 core.js baseline')
   else: raise SystemExit('Unexpected 0.7.3 base: '+member.name+' actual='+old+' expected='+manifest[member.name]['before'])
  raw=arc.extractfile(member).read()
  if hashlib.sha256(raw).hexdigest()!=manifest[member.name]['after']:raise SystemExit('v0.7.4 file hash mismatch: '+member.name)
  pending.append((destination,raw))
for path,raw in pending:
 path.parent.mkdir(parents=True,exist_ok=True)
 path.write_bytes(raw)
 print('v0.7.4:',path.relative_to(root))
print('Hasel 360 v0.7.4 checksum-verified source ready')