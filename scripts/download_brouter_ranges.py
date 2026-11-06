"""Resume verified HTTP ranges when the origin stalls on long transfers; all requests use TLS."""
from pathlib import Path
import urllib.request, concurrent.futures, time, hashlib, json, os
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'data/offline-routing/segments'
OUT.mkdir(parents=True,exist_ok=True)

def download(name):
 url='https://brouter.de/brouter/segments4/'+name
 with urllib.request.urlopen(urllib.request.Request(url,method='HEAD'),timeout=30) as r:
  size=int(r.headers['Content-Length']);etag=r.headers.get('ETag');modified=r.headers.get('Last-Modified')
 parts=OUT/(name+'.parts');parts.mkdir(exist_ok=True)
 identity=parts/'source.json'
 source={'url':url,'bytes':size,'etag':etag,'last_modified':modified}
 if identity.exists() and json.loads(identity.read_text())!=source:
  raise RuntimeError('Source changed; use a new parts directory to avoid mixing segment versions')
 if not identity.exists() and any(parts.iterdir()):
  raise RuntimeError('Existing chunks lack source identity; verify provenance before reuse')
 identity.write_text(json.dumps(source,indent=2))
 block=8192
 tasks=list(range(0,size,block));completed=0;start_time=time.time()
 def chunk(start):
  end=min(size,start+block)-1;part=parts/str(start)
  if part.exists() and part.stat().st_size==end-start+1:return
  for attempt in range(6):
   try:
    req=urllib.request.Request(url,headers={'Range':f'bytes={start}-{end}','If-Range':etag,'User-Agent':'ManzilOfflinePack/0.1'})
    with urllib.request.urlopen(req,timeout=15) as r:
     if r.status!=206 or r.headers.get('Content-Range')!=f'bytes {start}-{end}/{size}':raise ValueError('Invalid range response')
     data=r.read(end-start+1)
     if len(data)!=end-start+1:raise ValueError('Short read')
    part.write_bytes(data);return
   except Exception:
    if attempt==5:raise
    time.sleep(min(2**attempt,8))
 with concurrent.futures.ThreadPoolExecutor(max_workers=int(os.environ.get("BROUTER_DOWNLOAD_WORKERS","12"))) as pool:
  for _ in pool.map(chunk,tasks):
   completed+=1
   if completed%100==0:print(name,completed,len(tasks),round(time.time()-start_time),flush=True)
 tmp=OUT/(name+'.tmp')
 with tmp.open('wb') as f:
  for start in tasks:f.write((parts/str(start)).read_bytes())
 assert tmp.stat().st_size==size
 target=OUT/name;tmp.replace(target)
 result={'url':url,'bytes':size,'etag':etag,'last_modified':modified,'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'downloaded_at_utc':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime())}
 (OUT/(name+'.json')).write_text(json.dumps(result,indent=2));print('COMPLETE',result,flush=True)
for name in ['E80_N10.rd5','E75_N10.rd5']:download(name)
