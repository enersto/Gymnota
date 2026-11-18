"""Audit distributable assets and compare them with the pre-trimming APK."""
import argparse
import gzip
import hashlib
import json
import sqlite3
import tempfile
import zipfile
from contextlib import closing
from pathlib import Path


def verify(apk, baseline=None):
    with zipfile.ZipFile(apk) as package, tempfile.TemporaryDirectory() as temporary:
        names=set(package.namelist())
        assert not any(n.startswith('assets/catalog/videos/') and n.endswith('.gif') for n in names)
        webps={n for n in names if n.startswith('assets/catalog/videos/') and n.endswith('.webp')}
        assert len(webps)==1324
        databases={n for n in names if n.startswith('assets/catalog/') and n.endswith('.sqlite.bin')}
        assert databases=={'assets/catalog/gymnota-catalog-v5.sqlite.bin'},databases
        dbpath=Path(temporary)/'runtime.sqlite'
        dbpath.write_bytes(gzip.decompress(package.read(next(iter(databases)))))
        with closing(sqlite3.connect(dbpath)) as db:
            assert db.execute('PRAGMA integrity_check').fetchone()==('ok',)
            assert 'instruction_steps_json' not in [r[1] for r in db.execute('PRAGMA table_info(catalog_translation)')]
            assert db.execute('SELECT COUNT(*) FROM catalog_exercise').fetchone()[0]==1324
            locales=dict(db.execute('SELECT locale,COUNT(*) FROM catalog_translation GROUP BY locale'))
            assert len(locales)==12 and all(n==1324 for n in locales.values()),locales
            for path, in db.execute('SELECT gif_path FROM catalog_exercise'):
                assert 'assets/catalog/'+path.removesuffix('.gif')+'.webp' in webps,path
            if baseline:
                oldpath=Path(temporary)/'before.sqlite'
                with zipfile.ZipFile(baseline) as old:
                    oldpath.write_bytes(gzip.decompress(old.read('assets/catalog/gymnota-catalog-v4.sqlite.bin')))
                    for name in webps:assert package.read(name)==old.read(name),name
                with closing(sqlite3.connect(oldpath)) as olddb:
                    for table in ['metadata','catalog_exercise','catalog_translation','gymnota_match']:
                        columns=[r[1] for r in db.execute(f'PRAGMA table_info("{table}")')]
                        projection=','.join('"'+c+'"' for c in columns)
                        assert sorted(db.execute(f'SELECT {projection} FROM "{table}"').fetchall(),key=repr)==sorted(olddb.execute(f'SELECT {projection} FROM "{table}"').fetchall(),key=repr),table
        result=dict(apk_bytes=apk.stat().st_size,apk_mib=round(apk.stat().st_size/1048576,2),
            webp_count=len(webps),gif_count=0,sqlite_bytes=dbpath.stat().st_size,
            translation_locales=locales,dex_compressed_bytes=sum(i.compress_size for i in package.infolist() if i.filename.endswith('.dex')),
            native_bytes=sum(i.file_size for i in package.infolist() if i.filename.startswith('lib/')),
            sha256=hashlib.sha256(apk.read_bytes()).hexdigest())
        if baseline:result.update(baseline_bytes=baseline.stat().st_size,saved_bytes=baseline.stat().st_size-apk.stat().st_size,webp_bytes_unchanged=True,runtime_database_values_unchanged=True)
        return result


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk',type=Path)
    parser.add_argument('--baseline',type=Path)
    parser.add_argument('--report',type=Path)
    args=parser.parse_args()
    result=verify(args.apk,args.baseline)
    if args.report:args.report.write_text(json.dumps(result,indent=2),encoding='utf8')
    print(json.dumps(result,indent=2))
