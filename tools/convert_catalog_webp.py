"""Convert archived catalogue GIFs to lossless animated WebP; never edits source files."""
import argparse, concurrent.futures, hashlib, json, subprocess
from pathlib import Path
from benchmark_animation import equivalent

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--assets', type=Path, required=True, help='Archived original GIF directory')
    ap.add_argument('--output', type=Path, required=True)
    ap.add_argument('--gif2webp', type=Path, required=True)
    ap.add_argument('--workers', type=int, default=6)
    args=ap.parse_args()
    sources=sorted(args.assets.glob('*.gif'))
    if not sources: ap.error('No original GIFs found; use the pre-migration archive.')
    if args.assets.resolve()==args.output.resolve(): ap.error('Output must differ from the original directory.')
    args.output.mkdir(parents=True,exist_ok=True)
    def convert(source):
        dest=args.output/(source.stem+'.webp')
        subprocess.run([str(args.gif2webp),'-min_size','-m','6','-q','100','-metadata','all',str(source),'-o',str(dest)],check=True,capture_output=True)
        if not equivalent(source,dest): raise ValueError('Pixel/timing/loop mismatch: '+source.name)
        return dict(gif=source.name,gif_bytes=source.stat().st_size,webp_bytes=dest.stat().st_size,equal=True,
                    source_sha256=hashlib.sha256(source.read_bytes()).hexdigest())
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool: rows=list(pool.map(convert,sources))
    (args.output/'manifest.json').write_text(json.dumps(rows,indent=2),encoding='utf-8')
    print(json.dumps(dict(count=len(rows),gif_bytes=sum(r['gif_bytes'] for r in rows),webp_bytes=sum(r['webp_bytes'] for r in rows))))
if __name__=='__main__': main()
