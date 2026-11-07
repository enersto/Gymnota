import argparse
import hashlib
import json
import sqlite3
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("source", type=Path)
parser.add_argument("database", type=Path)
parser.add_argument("--display-names", type=Path)
args = parser.parse_args()
source = args.source.read_bytes()
rows = json.loads(source)
if args.database.exists():
    parser.error("Choose a new output path to preserve existing annotations.")

parts = {
    "chest": "part_chest", "back": "part_back", "shoulders": "part_shoulders",
    "upper arms": "part_arms", "lower arms": "part_arms", "cardio": "part_cardio",
    "lower legs": "part_calves", "waist": "part_abs", "neck": "part_other",
}
upper_leg_targets = {
    "glutes": "part_hips", "abductors": "part_hips", "quads": "part_thighs",
    "hamstrings": "part_thighs", "adductors": "part_thighs",
}
supported_locales = {"zh", "en", "de", "es", "ja", "tr", "ru", "ko"}
equipment = {
    "barbell": "equip_barbell", "dumbbell": "equip_dumbbell", "cable": "equip_cable",
    "body weight": "equip_bodyweight", "smith machine": "equip_smith_machine",
    "leverage machine": "equip_machine",
}
matches = dict(zip(
    ("坐姿推胸", "卷腹", "杠铃深蹲", "椭圆仪", "悬垂举腿", "高位下拉", "坐姿绳索划船",
     "哑铃弯举", "绳索下压", "罗马尼亚硬拉", "腿举", "坐姿腿弯举", "坐姿腿屈伸",
     "杠铃卧推", "哑铃肩推", "上斜哑铃卧推", "反向飞鸟"),
    ("0576", "0274", "0043", "2141", "0472", "0150", "0861", "0294", "0200",
     "0085", "0739", "0599", "0585", "0025", "0405", "0314", "0602"),
))
db = sqlite3.connect(":memory:")
db.executescript("""
PRAGMA foreign_keys = ON;
PRAGMA user_version = 1;
CREATE TABLE metadata (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL);
CREATE TABLE catalog_exercise (
    source_id TEXT PRIMARY KEY NOT NULL,
    canonical_name TEXT NOT NULL,
    source_category TEXT NOT NULL,
    source_body_part TEXT NOT NULL,
    source_equipment TEXT NOT NULL,
    target_muscle TEXT NOT NULL,
    muscle_group TEXT,
    secondary_muscles_json TEXT NOT NULL,
    category TEXT CHECK(category IN ('STRENGTH', 'CARDIO', 'CORE')),
    bodyPart TEXT,
    equipment TEXT,
    defaultTarget TEXT,
    logType INTEGER CHECK(logType IN (0, 1, 2)),
    isUnilateral INTEGER CHECK(isUnilateral IN (0, 1)),
    media_id TEXT,
    thumbnail_path TEXT,
    gif_path TEXT,
    attribution TEXT
);
CREATE TABLE catalog_translation (
    source_id TEXT NOT NULL REFERENCES catalog_exercise(source_id),
    locale TEXT NOT NULL,
    display_name TEXT,
    instruction TEXT,
    instruction_steps_json TEXT,
    PRIMARY KEY(source_id, locale)
);
CREATE TABLE gymnota_match (
    locale TEXT NOT NULL,
    preset_name TEXT NOT NULL,
    source_id TEXT NOT NULL REFERENCES catalog_exercise(source_id),
    PRIMARY KEY(locale, preset_name)
);
""")
with db:
    db.executemany("INSERT INTO metadata VALUES (?, ?)", [
        ("source", "https://github.com/hasaneyldrm/exercises-dataset"),
        ("source_sha256", hashlib.sha256(source).hexdigest()),
        ("license", "https://github.com/hasaneyldrm/exercises-dataset/blob/main/LICENSE"),
        ("media_terms", "https://github.com/hasaneyldrm/exercises-dataset/blob/main/NOTICE.md"),
    ])
    for row in rows:
        part = row["body_part"]
        db.execute("INSERT INTO catalog_exercise VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", (
            row["id"], row["name"], row["category"], part, row["equipment"], row["target"],
            row.get("muscle_group"), json.dumps(row.get("secondary_muscles", []), ensure_ascii=False),
            "CARDIO" if part == "cardio" else None, (upper_leg_targets.get(row["target"]) if part == "upper legs" else parts.get(part)), equipment.get(row["equipment"]),
            None, None, None, row.get("media_id"), row.get("image"), row.get("gif_url"), row.get("attribution"),
        ))
        instructions = row.get("instructions") or {}
        steps = row.get("instruction_steps") or {}
        for locale in sorted(instructions.keys() | steps.keys() | supported_locales):
            db.execute("INSERT INTO catalog_translation VALUES (?,?,?,?,?)", (
                row["id"], locale, row["name"] if locale == "en" else None,
                instructions.get(locale),
                json.dumps(steps[locale], ensure_ascii=False) if locale in steps else None,
            ))
    db.executemany("INSERT INTO gymnota_match VALUES ('zh', ?, ?)", matches.items())

if args.display_names:
    names = json.loads(args.display_names.read_text(encoding="utf8"))
    with db:
        for source_id, translations in names.items():
            for locale, name in translations.items():
                db.execute("UPDATE catalog_translation SET display_name = ? WHERE source_id = ? AND locale = ?",
                           (name, source_id, locale))
assert db.execute("PRAGMA integrity_check").fetchone() == ("ok",)
assert db.execute("PRAGMA foreign_key_check").fetchall() == []
assert db.execute("SELECT count(*) FROM catalog_exercise WHERE logType IS NULL").fetchone() == (len(rows),)
with sqlite3.connect(args.database) as output:
    db.backup(output)
for table in ("catalog_exercise", "catalog_translation", "gymnota_match"):
    print(table, db.execute(f"SELECT count(*) FROM {table}").fetchone()[0])
