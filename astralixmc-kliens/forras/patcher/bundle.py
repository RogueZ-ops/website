import json, os, sys, hashlib, zipfile
orig, patched, out = sys.argv[1], sys.argv[2], sys.argv[3]
m = json.load(open(os.path.join(orig, 'manifest.json')))
blobs = {}
groups = {}
for g, files in m['groups'].items():
    names = [n for n, _ in files]
    extra = 'hu/astralixmc/hud/ui/Transitions.class'
    if extra not in names:
        names.append(extra)
    lst = []
    for n in names:
        data = open(os.path.join(patched, g, n), 'rb').read()
        h = hashlib.sha1(data).hexdigest()
        blobs[h] = data
        lst.append([n, h])
    groups[g] = lst
man = json.dumps({'v': m['v'], 'groups': groups}, separators=(',', ':')).encode()
with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
    z.writestr('manifest.json', man)
    for h in sorted(blobs):
        z.writestr('b/' + h, blobs[h])
print(len(blobs), 'blobs', os.path.getsize(out), 'bytes')
