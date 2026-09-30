#!/usr/bin/env python3
"""Bundle the website, styles, fonts, scripts, and installer downloads into one HTML."""
from pathlib import Path
import base64,re
root=Path(__file__).resolve().parents[1]
web=root/'web/dist';dist=root/'dist';dist.mkdir(exist_ok=True)
out=dist/'Voiceish-0.1.4.html'
if out.exists():raise SystemExit('Refusing to replace existing release: '+str(out))
def uri(path,mime):return 'data:'+mime+';base64,'+base64.b64encode(path.read_bytes()).decode()
core=(web/'core.js').read_text()
exports=re.findall(r'^export\s+(?:async\s+)?(?:function|const)\s+(\w+)',core,re.M)
core=re.sub(r'^export\s+','',core,flags=re.M)
engine=(web/'engine.js').read_text()
engine=re.sub(r"^import \{([^}]+)\} from './core.js';",r'const {\1}=Core;',engine,flags=re.M)
engine=re.sub(r'^export\s+','',engine,flags=re.M)
app=(web/'app.js').read_text()
app=app.replace("import {handle as directHandle} from './engine.js';","const {handle:directHandle}=Engine;").replace("import {reconcileMessages, destination, addressKey} from './core.js';","const {reconcileMessages,destination,addressKey}=Core;")
app=app.replace("if('serviceWorker'in navigator)navigator.serviceWorker.register('/sw.js').catch(()=>{});",'')
app=app.replace('navigator.clipboard.writeText(location.origin)',"navigator.clipboard.writeText('https://voipmsms.robinc.chatgpt.site')")
js='const Core=(()=>{\n'+core+'\nreturn {'+','.join(exports)+'};})();\nconst Engine=(()=>{\n'+engine+'\nreturn {handle};})();\n'+app
assert not re.search(r'^import\s',js,re.M),'Unexpected unresolved import'
css=(web/'style.css').read_text().replace("url('/geist.ttf')",'url("'+uri(web/'geist.ttf','font/ttf')+'")')
html=(web/'index.html').read_text()
html=html.replace('<link rel="manifest" href="/manifest.webmanifest">','')
html=html.replace('href="/favicon.svg"','href="'+uri(web/'favicon.svg','image/svg+xml')+'"')
html=html.replace('<link rel="stylesheet" href="/style.css">','<style>'+css+'</style>')
html=html.replace('<script type="module" src="/app.js"></script>','<script type="module">'+js.replace('</script','<\\/script')+'</script>')
html=html.replace('href="/"','href="#"')
for name,mime in [('voiceish-setup-0.1.4.apk','application/vnd.android.package-archive'),('voiceish-sim-chrome-0.2.2.zip','application/zip')]:
 html=html.replace('href="/'+name+'" download','href="'+uri(dist/name,mime)+'" download="'+name+'"')
html=html.replace('If the install prompt is unavailable, use your browser menu → Install Voiceish or Add to Home Screen. Installation availability depends on your browser.','This is the standalone HTML edition. Open <a href="https://voipmsms.robinc.chatgpt.site">the hosted Voiceish workspace</a> for PWA installation and the Chrome extension connection. Direct phone connections still depend on browser network permissions.')
with out.open('x') as f:f.write(html)
print(out)
