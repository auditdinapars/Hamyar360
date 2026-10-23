#!/usr/bin/env python3
"""Restore audited Excel import event persistence into 0.7.7 core with SHA-256 guards."""
from pathlib import Path
import hashlib,base64
root=Path(__file__).resolve().parents[1]
p=root/'app/src/main/assets/core.js'
source_sha='3cd6e75ede421a707a4a76bf9f624fce6936bbb69e6e224628bc515e295d397d'
target_sha='be07cfd7b72e87ffbdc195dc13ff06b1d07e27a9b5bfc886d54f0e93c7882cbd'
base=p.read_bytes()
if hashlib.sha256(base).hexdigest()!=source_sha:
 raise SystemExit('v0.7.7 core import audit base differs; refusing unsafe overwrite')
content=base64.b64decode((root/'overlays/v077/core-importlog.b64').read_text().strip(),validate=True)
if hashlib.sha256(content).hexdigest()!=target_sha:
 raise SystemExit('v0.7.7 core import audit payload checksum differs')
p.write_bytes(content)
print('Verified 0.7.7 Excel import audit data schema')