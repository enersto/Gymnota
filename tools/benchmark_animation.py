"""Reproducible, deterministic media experiment. Never replaces application assets."""
import argparse, concurrent.futures, csv, hashlib, io, json, statistics, subprocess, time
from pathlib import Path
from PIL import Image

def frames(path):
    with Image.open(path) as im:
        result=[]
        for i in range(im.n_frames):
            im.seek(i)
            result.append((im.convert('RGBA').copy(), int(im.info.get('duration', 0))))
        return result, im.info.get('loop', 1)

def equivalent(a,b):
    left,loopa=frames(a); right,loopb=frames(b)
    if loopa!=loopb or sum(t for _,t in left)!=sum(t for _,t in right):return False
    def timeline(sequence):
        t=0; out=[]
        for image,duration in sequence:
            # Compare visible pixels including alpha, ignoring hidden RGB under alpha=0.
            visible=Image.alpha_composite(Image.new('RGBA',image.size,'white'),image)
            out.append((t,t+duration,image.size,visible.tobytes(),image.getchannel('A').tobytes()))
            t+=duration
        return out
    l,r=timeline(left),timeline(right)
    points=sorted({p for row in l+r for p in row[:2]})
    for start,end in zip(points,points[1:]):
        x=next((row for row in l if row[0]<=start<row[1]),None)
        y=next((row for row in r if row[0]<=start<row[1]),None)
        if x is None or y is None or x[2:]!=y[2:]:return False
    return True

def decode_times(path):
    first=[]; full=[]
    for _ in range(5):
        start=time.perf_counter()
        with Image.open(path) as image:
            image.seek(0);image.convert('RGBA').load()
            first.append((time.perf_counter()-start)*1000)
            for i in range(1,image.n_frames):image.seek(i);image.convert('RGBA').load()
        full.append((time.perf_counter()-start)*1000)
    return statistics.median(first),statistics.median(full)

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--assets',type=Path,required=True);ap.add_argument('--gif2webp',type=Path,required=True);ap.add_argument('--output',type=Path,required=True);ap.add_argument('--android-assets',type=Path);args=ap.parse_args()
    args.output.mkdir(parents=True,exist_ok=True)
    inventory=[]
    for path in sorted(args.assets.glob('*.gif')):
        with Image.open(path) as im:inventory.append({'path':path,'frames':im.n_frames,'size':path.stat().st_size,'width':im.width,'height':im.height})
    if not inventory: ap.error("No original GIFs found; use a directory extracted from the pre-WebP archive.")
    selected={}
    # Cover every frame-count bucket, its size extremes, and global size quantiles.
    for count in sorted({r['frames'] for r in inventory}):
        bucket=sorted((r for r in inventory if r['frames']==count),key=lambda r:r['size'])
        for index in {0,len(bucket)//2,len(bucket)-1}:selected[bucket[index]['path'].name]=bucket[index]
    ordered=sorted(inventory,key=lambda r:r['size'])
    for i in range(17):r=ordered[round(i*(len(ordered)-1)/16)];selected[r['path'].name]=r
    def convert(row):
        path=row['path'];result={k:v for k,v in row.items() if k!='path'};result['file']=path.name
        for mode,options in [('lossless',['-min_size','-m','6','-q','100']),('lossy90',['-lossy','-q','90','-m','6','-min_size'])]:
            dest=args.output/mode/(path.stem+'.webp');dest.parent.mkdir(exist_ok=True)
            start=time.perf_counter();subprocess.run([str(args.gif2webp),*options,'-metadata','all',str(path),'-o',str(dest)],check=True,capture_output=True)
            result[mode+'_bytes']=dest.stat().st_size;result[mode+'_encode_ms']=(time.perf_counter()-start)*1000
            if mode=='lossless':result['pixel_timeline_equal']=equivalent(path,dest)
        return result
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:results=list(pool.map(convert,selected.values()))
    # Decode benchmarks run sequentially after encoding to avoid competing encoder CPU load.
    for row in results:
        for mode,path in [('gif',args.assets/row['file']),('lossless',args.output/'lossless'/(Path(row['file']).stem+'.webp')),('lossy90',args.output/'lossy90'/(Path(row['file']).stem+'.webp'))]:
            row[mode+'_first_ms'],row[mode+'_full_ms']=decode_times(path)
    before=sum(r['size'] for r in results);after=sum(r['lossless_bytes'] for r in results)
    summary={'inventory_count':len(inventory),'sample_count':len(results),'sample_gif_bytes':before,'sample_lossless_bytes':after,'lossless_saving_percent':100*(1-after/before),
        'lossy90_bytes':sum(r['lossy90_bytes'] for r in results),'all_pixel_timelines_equal':all(r['pixel_timeline_equal'] for r in results),
        'inventory_gif_bytes':sum(r['size'] for r in inventory),'sample_strategy':'frame count bucket: min/median/max size; global 17 size quantiles; deduplicated',
        'decoder':'Pillow '+Image.__version__,'converter':subprocess.check_output([str(args.gif2webp),'-version'],text=True).strip(),
        'source_hashes':{r['file']:hashlib.sha256((args.assets/r['file']).read_bytes()).hexdigest() for r in results}}
    (args.output/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
    with (args.output/'results.csv').open('w',newline='',encoding='utf-8-sig') as out:
        writer=csv.DictWriter(out,fieldnames=list(results[0]));writer.writeheader();writer.writerows(results)
    if args.android_assets:
        target=args.android_assets/'media_bench';target.mkdir(parents=True,exist_ok=True)
        cases=[]
        # One example per frame-count, plus overall smallest/largest; copied only into test APK assets.
        for row in sorted(results,key=lambda r:r['frames']):
            if row['frames'] not in {c['frames'] for c in cases}:cases.append(row)
        for i,row in enumerate(cases):
            (target/f'{i}.gif').write_bytes((args.assets/row['file']).read_bytes())
            (target/f'{i}.webp').write_bytes((args.output/'lossless'/(Path(row['file']).stem+'.webp')).read_bytes())
        (target/'manifest.json').write_text(json.dumps([{'id':i,'frames':r['frames'],'width':r['width'],'height':r['height']} for i,r in enumerate(cases)]),encoding='utf-8')
    print(json.dumps({k:v for k,v in summary.items() if k!='source_hashes'},indent=2))

if __name__=='__main__':main()
