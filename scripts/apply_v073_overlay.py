#!/usr/bin/env python3
"""Apply audited Hasel 360 v0.7.3 source patch after verified v0.7.2 restoration."""
from pathlib import Path
import base64,hashlib,io,json,lzma,tarfile
root=Path(__file__).resolve().parents[1]
manifest=json.loads((root/'overlays/v073/manifest.json').read_text(encoding='utf-8'))
data=base64.b64decode((root/'overlays/v073/overlay.b64').read_text().strip(),validate=True)
expected=manifest.pop('__payload_sha256__')
if hashlib.sha256(data).hexdigest()!=expected:raise SystemExit('v0.7.3 overlay integrity mismatch')
with tarfile.open(fileobj=io.BytesIO(lzma.decompress(data)),mode='r:') as archive:
 members=archive.getmembers()
 if set(m.name for m in members)!=set(manifest):raise SystemExit('v0.7.3 member names differ')
 for m in members:
  rel=Path(m.name)
  if not m.isfile() or rel.is_absolute() or '..' in rel.parts or m.issym() or m.islnk():raise SystemExit('Unsafe v0.7.3 member')
  dest=root/rel
  before=hashlib.sha256(dest.read_bytes()).hexdigest() if dest.exists() else ''
  expected_before=manifest[m.name]['before']
  if before!=expected_before:
   # CI adds notes to documentation and normalized Java files; tolerate known non-business files only.
   if m.name in {'README.md','BUILD-STATUS.md'}:
    print('Document baseline differs:',m.name)
   elif m.name.endswith('.java') and dest.exists() and hashlib.sha256(dest.read_bytes().replace(b'\r\n',b'\n')).hexdigest()==hashlib.sha256(archive.extractfile(m).read().replace(b'\r\n',b'\n')).hexdigest():
    print('Normalized Java source has no semantic change',m.name)
   else:raise SystemExit('Unexpected base before v0.7.3: '+m.name)
  raw=archive.extractfile(m).read()
  if hashlib.sha256(raw).hexdigest()!=manifest[m.name]['after']:raise SystemExit('File checksum differs: '+m.name)
  dest.parent.mkdir(parents=True,exist_ok=True)
  dest.write_bytes(raw)
  print('Restored',m.name)
print('Hasel 360 v0.7.3 source restored and verified')
