#!/usr/bin/env python3
"""Fail-closed restore of audited v0.7.2 source over GitHub v0.7.1 base."""
from pathlib import Path
import io,tarfile,lzma,base64,hashlib,json
root=Path(__file__).resolve().parents[1]
encoded=(root/'overlays/v072/overlay.b64').read_text()
blob=base64.b64decode(''.join(encoded.split()),validate=True)
if hashlib.sha256(blob).hexdigest()!='cfc24c4ef96fda90f550cb9b0c79eeffb0bbcdbe23a6f83b66678efb83b2bb31':raise SystemExit('v0.7.2 overlay SHA256 mismatch')
manifest=json.loads((root/'overlays/v072/manifest.json').read_text())
with tarfile.open(fileobj=io.BytesIO(lzma.decompress(blob)),mode='r:') as z:
  entries=z.getmembers()
  if len(entries)!=len(manifest):raise SystemExit('Overlay member count mismatch')
  for member in entries:
    path=Path(member.name)
    if not member.isfile() or path.is_absolute() or '..' in path.parts or member.name not in manifest:raise SystemExit('Unsafe overlay entry')
    dest=root/path; expected=manifest[member.name];old=dest.read_bytes() if dest.exists() else None
    before=hashlib.sha256(old).hexdigest() if old is not None else ''
    if before!=expected['old']:
      normalized_java=(member.name=='app/src/main/java/com/hamyar360/app/MainActivity.java' and before=='c1119cbacaae0638383a4a1dbbaecf0058fc080343883a772d33f68eea65ec63')
      if not normalized_java:raise SystemExit('Unexpected v0.7.1 base: '+member.name)
    raw=z.extractfile(member).read()
    if hashlib.sha256(raw).hexdigest()!=expected['new']:raise SystemExit('Modified overlay entry')
    dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(raw)
    print('Restored verified',member.name)
print('v0.7.2 verified source restored')