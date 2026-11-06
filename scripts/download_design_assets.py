"""Acquire a pinned, small set of official design assets; Python standard library only."""
import concurrent.futures,datetime,hashlib,json,pathlib,urllib.parse,urllib.request,xml.etree.ElementTree as ET
ROOT=pathlib.Path(__file__).resolve().parents[1]
OUT=ROOT/'design/assets'
LUCIDE='ee8e10d746a10907205366cfd984922f0c58939a'
FONTS='eb20e9cf19d97cf579fa3867d729d4222d6c59e1'
ICONS='navigation route map-pin clock wallet mic volume-2 accessibility bus train-front footprints cloud-rain triangle-exclamation-point wifi-off arrow-left chevron-right check x sliders-horizontal bookmark locate-fixed users shield info undo-2 pause play search menu chevron-down circle-question-mark download'.split()

def get(job):
    repository,commit,source,relative,license_note=job
    url=f'https://raw.githubusercontent.com/{repository}/{commit}/'+urllib.parse.quote(source)
    path=OUT/relative; path.parent.mkdir(parents=True,exist_ok=True)
    if path.exists(): raw=path.read_bytes()
    else:
        request=urllib.request.Request(url,headers={'User-Agent':'ManzilDesignAssetResearch/0.1'})
        with urllib.request.urlopen(request,timeout=45) as response:
            raw=response.read(5*1024*1024+1)
            if len(raw)>5*1024*1024: raise ValueError('Asset exceeds 5 MiB cap')
    entry={'path':str(path.relative_to(ROOT)),'source_url':url,'repository':repository,'commit':commit,'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest(),'license':license_note}
    if path.suffix=='.svg':
        root=ET.fromstring(raw)
        assert root.tag=='{http://www.w3.org/2000/svg}svg',root.tag
        assert root.attrib.get('viewBox')=='0 0 24 24',root.attrib
        assert root.attrib.get('fill')=='none',root.attrib
        assert root.attrib.get('stroke')=='currentColor',root.attrib
        assert root.attrib.get('stroke-width')=='2',root.attrib
        forbidden={'script','image','foreignObject','use'}
        tags=[e.tag.split('}')[-1] for e in root.iter()]
        assert not forbidden.intersection(tags),tags
        assert all(not k.lower().startswith('on') and not k.lower().endswith('href') for e in root.iter() for k in e.attrib)
        entry['svg_validation']={'xml_valid':True,'viewBox':root.attrib['viewBox'],'width':root.attrib.get('width'),'height':root.attrib.get('height'),'stroke_width':root.attrib['stroke-width'],'elements':sorted(set(tags))}
    path.write_bytes(raw)
    return entry

def main():
    jobs=[('lucide-icons/lucide',LUCIDE,'icons/'+name+'.svg','icons/'+name+'.svg','Lucide ISC; some Feather-derived icons also covered by included MIT notice') for name in ICONS]
    jobs.append(('lucide-icons/lucide',LUCIDE,'LICENSE','licenses/LUCIDE-LICENSE','ISC and included Feather MIT notices'))
    for family,filename in [('manrope','Manrope[wght].ttf'),('notosanstamil','NotoSansTamil[wdth,wght].ttf')]:
        for source in [filename,'OFL.txt','METADATA.pb']:
            jobs.append(('google/fonts',FONTS,'ofl/'+family+'/'+source,'fonts/'+family+'/'+source,'SIL Open Font License 1.1; see adjacent OFL.txt'))
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        entries=list(pool.map(get,jobs))
    manifest={'fetched_at_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'icon_family':'Lucide','icon_count':len(ICONS),'icon_names':ICONS,'total_bytes':sum(x['bytes'] for x in entries),'assets':entries}
    (OUT/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
    print(json.dumps({k:v for k,v in manifest.items() if k!='assets'},indent=2))

if __name__=='__main__':main()
