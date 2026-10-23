#!/usr/bin/env python3
"""Restore the exact v0.7.0 cash-budget source overlay before CI builds."""
from pathlib import Path
import base64, hashlib, io, lzma, tarfile

root = Path(__file__).resolve().parents[1]
parts = sorted((root / "overlays" / "v070").glob("part-*.txt"))
if len(parts) != 5:
    raise SystemExit(f"Expected five overlay parts, found {len(parts)}")
packed = base64.b64decode("".join(p.read_text().strip() for p in parts), validate=True)
actual = hashlib.sha256(packed).hexdigest()
expected = "a6fc0f3ef5ba08e282c838a06a0c6bfd4d1d5c8e69061fcf55f27967d65eeb51"
if actual != expected:
    raise SystemExit(f"Overlay integrity mismatch: {actual}")
with tarfile.open(fileobj=io.BytesIO(lzma.decompress(packed)), mode="r:") as tf:
    members = tf.getmembers()
    for member in members:
        path = Path(member.name)
        if not member.isfile() or path.is_absolute() or ".." in path.parts:
            raise SystemExit("Unsafe overlay entry")
        dest = root / path
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(tf.extractfile(member).read())
        print("Restored", path)
print("Source overlay v0.7.0 verified")
