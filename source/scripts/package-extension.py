#!/usr/bin/env python3
from pathlib import Path
import zipfile
root=Path(__file__).resolve().parents[1];out=root/'dist/voiceish-sim-chrome-0.2.2.zip';out.parent.mkdir(exist_ok=True)
if out.exists():raise SystemExit('Refusing to replace existing release: '+str(out))
with zipfile.ZipFile(out,'x',zipfile.ZIP_DEFLATED) as z:
 for p in sorted((root/'extension').rglob('*')):
  rel=p.relative_to(root/'extension')
  if p.is_file() and 'tests' not in rel.parts and p.name!='package.json':z.write(p,rel)
print(out)
