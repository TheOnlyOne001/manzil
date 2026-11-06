"""Inspect local snapshots and render a map without downloading map tiles."""
import collections,csv,io,json,pathlib,zipfile,hashlib
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.collections import LineCollection
import download_data as download

ROOT=pathlib.Path(__file__).resolve().parents[1]; DATA=ROOT/'data'
derived=DATA/'derived'; derived.mkdir(exist_ok=True)
outer=DATA/'transit/cumta-static-mtc-gtfs.zip'
outputs=[]
if outer.exists():
    with zipfile.ZipFile(outer) as z:
        for n in z.namelist():
            if n.endswith(('.zip','.geojson')):
                p=derived/pathlib.PurePosixPath(n).name
                p.write_bytes(z.read(n))
                outputs.append({'path':str(p.relative_to(ROOT)),'source_archive':str(outer.relative_to(ROOT)),'member':n,'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
                if n.endswith('.zip'):
                    report=download.inspect_gtfs(p)
                    (DATA/'cumta_nested_gtfs_validation.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
                    with zipfile.ZipFile(p) as gtfs:
                        departure_counts=collections.Counter()
                        for name in gtfs.namelist():
                            if name.endswith('stop_times.txt'):
                                for row in csv.DictReader(io.TextIOWrapper(gtfs.open(name),encoding='utf-8-sig')):
                                    departure_counts[row.get('departure_time','')]+=1
                        (DATA/'cumta_departure_counts.json').write_text(json.dumps(departure_counts.most_common(20),indent=2),encoding='utf-8')
(DATA/'derived_provenance.json').write_text(json.dumps(outputs,indent=2),encoding='utf-8')

osm=json.loads((DATA/'osm/chennai_transport.json').read_text(encoding='utf-8'))
nodes={x['id']:(x['lon'],x['lat']) for x in osm['elements'] if x['type']=='node'}
ways=[x for x in osm['elements'] if x['type']=='way']
major={'motorway','trunk','primary','secondary','tertiary','motorway_link','trunk_link','primary_link','secondary_link','tertiary_link'}
roads=[]; rail=[]
for way in ways:
    tags=way.get('tags',{}); segment=[nodes[n] for n in way['nodes'] if n in nodes]
    if len(segment)<2: continue
    if tags.get('highway') in major: roads.append(segment)
    if tags.get('railway') in {'rail','subway','light_rail'}: rail.append(segment)
with zipfile.ZipFile(DATA/'transit/chennai-unified-gtfs.zip') as z:
    stops=list(csv.DictReader(io.TextIOWrapper(z.open('stops.txt'),encoding='utf-8-sig')))
    features=[{'type':'Feature','geometry':{'type':'Point','coordinates':[float(x['stop_lon']),float(x['stop_lat'])]},'properties':x} for x in stops]
    (derived/'unified_stops.geojson').write_text(json.dumps({'type':'FeatureCollection','features':features},ensure_ascii=False),encoding='utf-8')

plt.rcParams.update({'font.family':'DejaVu Sans','font.size':10})
fig,axes=plt.subplots(1,2,figsize=(16,10),facecolor='#f6f8fb')
for ax in axes:
    ax.set_facecolor('#f6f8fb')
    ax.add_collection(LineCollection(roads,colors='#c3ccd7',linewidths=.35,zorder=1))
    ax.add_collection(LineCollection(rail,colors='#708295',linewidths=.6,zorder=2))
    ax.set_aspect(1/0.974)
    ax.set_xlabel('Longitude');ax.set_ylabel('Latitude'); ax.grid(alpha=.12)
    for spine in ax.spines.values(): spine.set_visible(False)
axes[0].set_xlim(79.94,80.36);axes[0].set_ylim(12.64,13.31)
axes[0].set_title('OSM transport snapshot: metro pilot extent\nMajor roads + railway shown; minor ways retained in raw data',loc='left',fontsize=12)
colors={'MTC':'#087f8c','CMRL':'#b65b17','SRR':'#7a49b5'}
for prefix,color in colors.items():
    subset=[x for x in stops if x['stop_id'].startswith(prefix+'-')]
    axes[1].scatter([float(x['stop_lon']) for x in subset],[float(x['stop_lat']) for x in subset],s=3 if prefix=='MTC' else 12,c=color,label=f'{prefix}: {len(subset):,} stop/platform/entrance records',alpha=.6,zorder=3)
axes[1].set_xlim(79.64,80.36);axes[1].set_ylim(12.59,13.55)
axes[1].plot([79.95,80.35,80.35,79.95,79.95],[12.65,12.65,13.30,13.30,12.65],color='#ef6c3e',linewidth=1,linestyle='--',label='OSM query extent')
axes[1].legend(loc='lower left',fontsize=8,framealpha=.9)
axes[1].set_title('Transit coverage extends beyond the OSM pilot box\nThese are stop records, not unique stations',loc='left',fontsize=12)
fig.suptitle('MANZIL | Chennai data inspection',fontsize=21,fontweight='bold',x=.08,ha='left')
fig.text(.08,.035,'OSM snapshot: '+str(osm.get('osm3s',{}).get('timestamp_osm_base'))+' | GTFS downloaded 5 Nov 2026; CMRL calendar expired 2021.\nMap data © OpenStreetMap contributors (ODbL). Transit: UngalSoththu / ChennaiGTFS and source agencies.\nResearch preview only: no live traffic, current lift status, waterlogging or lighting verification. Not an offline tile pack.',fontsize=9,color='#374151')
fig.subplots_adjust(left=.08,right=.97,top=.86,bottom=.14,wspace=.20)
fig.savefig(derived/'chennai_data_preview.png',dpi=160,facecolor=fig.get_facecolor())
for filename,sources in [('unified_stops.geojson',['data/transit/chennai-unified-gtfs.zip:stops.txt']),('chennai_data_preview.png',['data/osm/chennai_transport.json','data/transit/chennai-unified-gtfs.zip:stops.txt'])]:
    p=derived/filename
    outputs.append({'path':str(p.relative_to(ROOT)),'derived_from':sources,'generator':'scripts/inspect_chennai_data.py','bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
(DATA/'derived_provenance.json').write_text(json.dumps(outputs,indent=2),encoding='utf-8')
print('Preview:',derived/'chennai_data_preview.png')
