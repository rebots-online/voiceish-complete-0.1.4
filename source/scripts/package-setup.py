#!/usr/bin/env python3
"""Create one complete, immutable 0.1.4 source release and install guide."""
from pathlib import Path
import hashlib, shutil, zipfile
root=Path(__file__).resolve().parents[1]
dist=root/'dist';dist.mkdir(exist_ok=True)
apks=[dist/'voiceish-sim-0.1.4.apk',dist/'voiceish-setup-0.1.4.apk']
if not all(p.is_file() for p in apks): raise SystemExit('Build both 0.1.4 APKs first.')
source=dist/'voiceish-sim-source-0.1.4.zip'
guide=dist/'Voiceish-Install-0.1.4.md'
checksums=dist/'Voiceish-0.1.4-SHA256SUMS.txt'
for p in [source,guide,checksums]:
 if p.exists(): raise SystemExit('Refusing to replace existing release: '+str(p))
omit={'build','.gradle','.git','node_modules','__pycache__'}
with zipfile.ZipFile(source,'x',zipfile.ZIP_DEFLATED) as archive:
 for file in sorted(root.rglob('*')):
  rel=file.relative_to(root)
  if not file.is_file() or rel.parts[0]=='dist' or omit.intersection(rel.parts): continue
  if file.suffix in {'.jks','.keystore','.pem','.idsig','.pyc'} or file.name in {'local.properties','keystore.properties','.env'}: continue
  archive.write(file,Path('voiceish-sim-0.1.4')/rel)
with guide.open('x') as output: output.write((root/'docs/PHONE-SETUP.md').read_text())
with checksums.open('x') as output: output.write(''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n' for p in apks+[source,guide]))
for file in apks+[source,guide,checksums]: print(f'{file.name}: {file.stat().st_size} bytes')
