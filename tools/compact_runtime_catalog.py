"""Create a compact runtime catalogue; retain the archived input and every runtime value."""
import argparse
import gzip
import hashlib
import json
import sqlite3
import tempfile
from contextlib import closing
from pathlib import Path


def compact(source: Path, output: Path):
    if source.resolve() == output.resolve():
        raise ValueError("Output must differ from archived source")
    original = source.read_bytes()
    with tempfile.TemporaryDirectory() as temporary:
        database = Path(temporary) / "catalog.sqlite"
        database.write_bytes(gzip.decompress(original))
        with closing(sqlite3.connect(database)) as connection:
            columns = [row[1] for row in connection.execute("PRAGMA table_info(catalog_translation)")]
            if "instruction_steps_json" not in columns:
                raise ValueError("Archived catalogue must include instruction_steps_json")
            tables = [row[0] for row in connection.execute("SELECT name FROM sqlite_master WHERE type='table'")]
            def snapshot():
                result = {}
                for table in tables:
                    names = [row[1] for row in connection.execute(f'PRAGMA table_info("{table}")') if row[1] != 'instruction_steps_json']
                    projection = ','.join('"'+name+'"' for name in names)
                    result[table] = sorted(connection.execute(f'SELECT {projection} FROM "{table}"').fetchall(), key=repr)
                return result
            before = snapshot()
            connection.execute("ALTER TABLE catalog_translation DROP COLUMN instruction_steps_json")
            connection.commit()
            connection.execute("VACUUM")
            assert connection.execute("PRAGMA integrity_check").fetchone() == ('ok',)
            assert not connection.execute("PRAGMA foreign_key_check").fetchall()
            assert snapshot() == before, "Runtime data changed"
            report = dict(exercises=len(before['catalog_exercise']), translations=len(before['catalog_translation']),
                          runtime_values_unchanged=True, sqlite_bytes=database.stat().st_size)
        packed = gzip.compress(database.read_bytes(), compresslevel=9, mtime=0)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(packed)
    report.update(source_sha256=hashlib.sha256(original).hexdigest(), gzip_bytes=len(packed), archived_gzip_bytes=len(original))
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    print(json.dumps(compact(args.source, args.output)))

