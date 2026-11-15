"""Lossless independent-keyframe candidates for resources rejected by Flutter's codec.
Source assets are never overwritten. Feed the JSON report from catalog_codec_test.dart.
"""
import argparse, concurrent.futures, hashlib, json
from pathlib import Path
from PIL import Image, ImageSequence
from benchmark_animation import equivalent


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--assets', type=Path, required=True)
    ap.add_argument('--failures', type=Path, required=True)
    ap.add_argument('--output', type=Path, required=True)
    args = ap.parse_args()
    if args.assets.resolve() == args.output.resolve():
        ap.error('Output must differ from source assets')
    args.output.mkdir(parents=True, exist_ok=True)
    names = json.loads(args.failures.read_text(encoding='utf-8'))
    def convert(name):
        if Path(name).name != name or not name.endswith('.webp'):
            raise ValueError('Expected a WebP basename')
        source = args.assets / name
        destination = args.output / name
        with Image.open(source) as image:
            frames, durations = [], []
            for frame in ImageSequence.Iterator(image):
                frames.append(frame.convert('RGBA'))
                durations.append(frame.info['duration'])
            frames[0].save(destination, format='WEBP', save_all=True,
                append_images=frames[1:], duration=durations, loop=image.info.get('loop', 0),
                lossless=True, quality=100, method=4, kmin=1, kmax=1, exact=True)
        if not equivalent(source, destination):
            raise ValueError('Visible pixels, alpha, timing or loop mismatch: ' + name)
        return dict(file=name, before=source.stat().st_size, after=destination.stat().st_size,
            equal=True, sha256=hashlib.sha256(destination.read_bytes()).hexdigest())
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        rows = list(pool.map(convert, names))
    (args.output / 'compatibility-manifest.json').write_text(json.dumps(rows, indent=2), encoding='utf-8')
    print(json.dumps(dict(count=len(rows), before=sum(r['before'] for r in rows), after=sum(r['after'] for r in rows))))


if __name__ == '__main__':
    main()
