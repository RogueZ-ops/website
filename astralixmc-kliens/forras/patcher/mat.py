import json,os,sys,shutil
root=sys.argv[1]; out=sys.argv[2]
m=json.load(open(os.path.join(root,'manifest.json')))
for g,files in m['groups'].items():
  for name,h in files:
    p=os.path.join(out,g,name); os.makedirs(os.path.dirname(p),exist_ok=True)
    shutil.copy(os.path.join(root,'b',h),p)
