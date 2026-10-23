#!/usr/bin/env python3
"""Restore the audited 0.7.1 changes onto the verified 0.7.0 source."""
from pathlib import Path
import base64
import hashlib
import io
import lzma
import struct

root = Path(__file__).resolve().parents[1]
encoded = (root / 'overlays/v071/v071-delta.b64').read_text(encoding='ascii')
packed = base64.b64decode(''.join(encoded.split()), validate=True)
expected = 'd701ef2a7692c5385fc4bda44da8334a2d3f4f98606bc7cab00fd55f2ed8a19e'
if hashlib.sha256(packed).hexdigest() != expected:
    raise SystemExit('0.7.1 delta archive checksum mismatch')
source = io.BytesIO(lzma.decompress(packed))
if source.read(11) != b'HASELDELTA1':
    raise SystemExit('Invalid 0.7.1 delta format')

def read_exact(count):
    data = source.read(count)
    if len(data) != count:
        raise SystemExit('Truncated 0.7.1 delta')
    return data

def u16():
    return struct.unpack('>H', read_exact(2))[0]

def u32():
    return struct.unpack('>I', read_exact(4))[0]

count = 0
while True:
    path_len = u16()
    if not path_len:
        break
    rel = Path(read_exact(path_len).decode('utf-8'))
    if rel.is_absolute() or '..' in rel.parts:
        raise SystemExit('Invalid output path')
    before_sha, after_sha = read_exact(32), read_exact(32)
    target = root / rel
    old = target.read_bytes() if target.exists() else b''
    if hashlib.sha256(old).digest() != before_sha:
        raise SystemExit('Wrong 0.7.0 source for ' + str(rel))
    lines = old.splitlines(keepends=True)
    output = bytearray()
    for _ in range(u32()):
        op = read_exact(1)
        if op == b'C':
            i, j = u32(), u32()
            if i > j or j > len(lines):
                raise SystemExit('Invalid copy range')
            output.extend(b''.join(lines[i:j]))
        elif op == b'L':
            output.extend(read_exact(u32()))
        else:
            raise SystemExit('Invalid delta opcode')
    if hashlib.sha256(output).digest() != after_sha:
        raise SystemExit('Output checksum mismatch for ' + str(rel))
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(output)
    print('Restored:', rel)
    count += 1
if source.read():
    raise SystemExit('Unexpected trailing delta bytes')
print('Verified and restored', count, '0.7.1 source files')