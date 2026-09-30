#!/usr/bin/env python3
"""Immutable complete release: binaries, single HTML, all source and build instructions."""
from pathlib import Path
import hashlib,zipfile
root=Path(__file__).resolve().parents[1];dist=root/'dist'
files=[dist/n for n in ['voiceish-setup-0.1.4.apk','voiceish-sim-0.1.4.apk','voiceish-sim-chrome-0.2.2.zip','Voiceish-0.1.4.html']]
if not all(p.is_file() for p in files):raise SystemExit('Build the APKs, extension ZIP and standalone HTML first.')
output=dist/'voiceish-complete-0.1.4.zip'
if output.exists():raise SystemExit('Refusing to replace existing release: '+str(output))
omit={'build','.gradle','.git','node_modules','__pycache__'}
with zipfile.ZipFile(output,'x',zipfile.ZIP_DEFLATED) as archive:
 for p in files:archive.write(p,p.name)
 archive.write(root/'RELEASE-0.1.4.md','START-HERE.md')
 archive.writestr('SHA256SUMS.txt',''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n' for p in files))
 for p in sorted(root.rglob('*')):
  rel=p.relative_to(root)
  if not p.is_file() or rel.parts[0]=='dist' or omit.intersection(rel.parts):continue
  if p.suffix in {'.jks','.keystore','.pem','.idsig','.pyc'} or p.name in {'local.properties','keystore.properties','.env'}:continue
  archive.write(p,Path('source')/rel)
print(output)
