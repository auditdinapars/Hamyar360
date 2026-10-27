#!/usr/bin/env python3
"""Stage exact checksum-verified local Persian and English traineddata for offline desktop OCR."""
from pathlib import Path
import urllib.request
import hashlib
import shutil
root=Path(__file__).resolve().parents[1]
dest=root/"desktop"/"ocr-langs"
dest.mkdir(parents=True,exist_ok=True)
checks={
 "fas":"db1c0a91208aff00d3cf1ed2c1d23f76419afd5f024688b4f71adc3f2ce4a505",
 "eng":"7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2",
}
for lang,expected in checks.items():
    source=root/"app"/"src"/"main"/"assets"/"tessdata"/(lang+".traineddata")
    target=dest/(lang+".traineddata")
    if source.is_file():
        data=source.read_bytes()
        if hashlib.sha256(data).hexdigest()==expected:
            target.write_bytes(data)
        else:
            raise RuntimeError("Invalid bundled OCR language checksum: "+lang)
    else:
        url=f"https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/4.1.0/{lang}.traineddata"
        req=urllib.request.Request(url,headers={"User-Agent":"Hasel360-build/0.9.1"})
        with urllib.request.urlopen(req,timeout=45) as response:
            raw=response.read(12_000_001)
        if len(raw)>12_000_000:
            raise RuntimeError("OCR model too large: "+lang)
        if hashlib.sha256(raw).hexdigest()!=expected:
            raise RuntimeError("Unverified OCR model: "+lang)
        target.write_bytes(raw)
    print("Verified offline OCR",lang,target.stat().st_size,expected)
