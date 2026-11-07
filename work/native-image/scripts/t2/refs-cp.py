# refs-cp.py <inst dir> <scratch dir> <mcv>: write <scratch>/refs.cp, the classpath a ref scan (scripts/t4/glfwrefs.py)
# runs over: the client jar + every mod jar (+ the jars nested in their META-INF/jars, extracted to <scratch>/nested)
# + the vanilla/fabric libraries except LWJGL. Two instance layouts: the mkcp layout (vanilla/client-<mcv>.jar,
# fgamedir/mods, vanilla.cp) when present, else scripts/m3a/fetch-inst.py's (files/). The same block is inline in
# build-klglfw341.sh; build-klsdl.sh uses this file.
import zipfile, os, sys, io
I, S, V = sys.argv[1], sys.argv[2], sys.argv[3]; os.makedirs(S + '/nested', exist_ok=True)
baked = os.path.isfile(f'{I}/vanilla/client-{V}.jar') and os.path.isdir(f'{I}/fgamedir/mods')
cp = [f'{I}/vanilla/client-{V}.jar' if baked else f'{I}/files/versions/{V}/client.jar']
M = f'{I}/fgamedir/mods' if baked else f'{I}/files/mods'
def walk(path, data=None):
    z = zipfile.ZipFile(io.BytesIO(data) if data else path)
    for n in z.namelist():
        if n.startswith('META-INF/jars/') and n.endswith('.jar'):
            b = z.read(n); out = os.path.join(S, 'nested', os.path.basename(n)); open(out, 'wb').write(b); cp.append(out); walk(None, b)
for m in sorted(os.listdir(M)):
    if m.endswith('.jar'): p = M + '/' + m; cp.append(p); walk(p)
if baked:
    libs = open(I + '/vanilla.cp').read().strip().split(':')
else:
    libs = [f'{I}/files/' + l.strip() for t in ('vanilla', 'fabric') for l in open(f'{I}/files/versions/{V}/libs-{t}.txt') if l.strip()]
for l in libs:
    if 'lwjgl' not in os.path.basename(l) and l not in cp: cp.append(l)
open(S + '/refs.cp', 'w').write(':'.join(cp))
