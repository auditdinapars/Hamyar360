#!/usr/bin/env python3
"""Restore v0.8.0 candidate on verified v0.7.8; never touch installed user data."""
from pathlib import Path
from zipfile import ZipFile
import base64,hashlib,io,runpy
root=Path(__file__).resolve().parents[1]
blobfile=root/'overlays/v080/payload.b64'
raw=base64.b64decode(blobfile.read_text().strip(),validate=True)
with ZipFile(io.BytesIO(raw)) as z:
    members={'v080_patch.py','v080_fix.py','v080_doc.py','ui_v080.py','v080.test.cjs'}
    assert set(z.namelist())==members,'Unexpected patch members'
    scripts=root/'scripts'
    scripts.mkdir(exist_ok=True)
    for name in ('v080_patch.py','v080_fix.py','v080_doc.py'):
        (scripts/name).write_bytes(z.read(name))
    (root/'qa/ui_v080.py').write_bytes(z.read('ui_v080.py'))
    (root/'tests/v080.test.cjs').write_bytes(z.read('v080.test.cjs'))
for name in ('v080_patch.py','v080_fix.py','v080_doc.py'):
    runpy.run_path(str(root/'scripts'/name),run_name='__main__')
assert "versionName '0.8.0'" in (root/'app/build.gradle').read_text()
assert "applicationIdSuffix '.dev080'" in (root/'app/build.gradle').read_text()
print('v0.8.0 candidate restored, production app identity unchanged')
