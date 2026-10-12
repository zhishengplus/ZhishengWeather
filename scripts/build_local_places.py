"""Build a pinned, attributed GeoNames administrative-place index (stdlib only).

Raw snapshots are supplied explicitly; this script never downloads at build time.
Reproduce: python scripts/build_local_places.py --inputs PATH --output PATH
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import re
import zipfile

PINNED = {
    "CN": "09dd67aaac8f6f9920a08eb8f2a61d8203aec96160053be9c658a4ed329a1916",
    "HK": "4f7088d3275880a36ef6959fcbb4a528e88c52d5b75a665e1273034622fd5090",
    "MO": "02e9541fc26bc48f301e2c5c8bbd6b3cbd0abc437395625cffe65341219a4188",
    "TW": "f72dcf2985ed61e0963e496745ee0475b8426ff39597b30cb3c700ab992ce87f",
}
REGIONS = {"CN": "", "HK": "香港", "MO": "澳门", "TW": "台湾"}
HAN = re.compile(r"[\u3400-\u9fff]")
CHINESE_NAME = re.compile(r"[\u3400-\u9fff〇0-9·（）()\- ]+")


def display_name(row):
    candidates = list(dict.fromkeys([row[1], *row[3].split(",")]))
    chinese = [name for name in candidates if CHINESE_NAME.fullmatch(name) and HAN.search(name) and len(name) <= 40]
    suffixes = {"ADM1": ("省", "自治区", "特别行政区"), "ADM2": ("市", "州", "地区", "盟", "縣", "县"), "ADM3": ("区", "區", "县", "縣", "市", "旗", "镇", "鎮")}
    preferred = [name for name in chinese if name.endswith(suffixes[row[7]])]
    return sorted(preferred or chinese, key=lambda name: (-len(name), name))[0] if chinese else row[1]


def build_records(rows_by_region):
    places, profile, ids = [], {}, set()
    for region in sorted(rows_by_region):
        rows = [row for row in rows_by_region[region] if row[7] in ("ADM1", "ADM2", "ADM3")]
        parents = {}
        for row in rows:
            level = int(row[7][-1])
            key = (level, *row[10:10 + level])
            # Ambiguous upstream parent codes do not establish a trusted parent.
            parents.setdefault(key, []).append(row)
        stats = {"records": 0, "withoutChineseName": 0, "missingParent": 0}
        for row in rows:
            ident = region + ":" + row[0]
            if ident in ids:
                raise ValueError("Duplicate place ID: " + ident)
            ids.add(ident)
            lat, lon = float(row[4]), float(row[5])
            if not (math.isfinite(lat) and math.isfinite(lon) and -90 <= lat <= 90 and -180 <= lon <= 180):
                raise ValueError("Invalid coordinate: " + ident)
            name = display_name(row)
            if not HAN.search(name):
                stats["withoutChineseName"] += 1
            hierarchy = []
            for level in range(1, int(row[7][-1])):
                hits = parents.get((level, *row[10:10 + level]), [])
                if len(hits) == 1:
                    hierarchy.append(display_name(hits[0]))
                else:
                    stats["missingParent"] += 1
            if REGIONS[region]:
                hierarchy.insert(0, REGIONS[region])
            aliases = sorted({
                alias.strip() for alias in [row[1], row[2], *row[3].split(",")]
                if 0 < len(alias.strip()) <= 80 and (HAN.search(alias) or alias.isascii())
            })
            places.append({
                "id": ident, "name": name,
                "affiliation": "·".join(dict.fromkeys(value for value in hierarchy if value != name)),
                "lat": lat, "lon": lon, "aliases": aliases, "level": row[7],
            })
            stats["records"] += 1
        profile[region] = stats
    return sorted(places, key=lambda place: place["id"]), profile


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--inputs", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    rows_by_region = {}
    for region, expected in PINNED.items():
        raw = (args.inputs / (region + ".zip")).read_bytes()
        if hashlib.sha256(raw).hexdigest() != expected:
            raise ValueError("Snapshot hash mismatch: " + region)
        with zipfile.ZipFile(args.inputs / (region + ".zip")) as archive:
            with archive.open(region + ".txt") as stream:
                rows_by_region[region] = [
                    row for line in stream
                    if len(row := line.decode("utf-8").rstrip("\r\n").split("\t")) == 19 and row[7] in ("ADM1", "ADM2", "ADM3")
                ]
    places, profile = build_records(rows_by_region)
    if not places:
        raise ValueError("Empty administrative dataset")
    dataset = {"schema": 1, "places": places}
    raw = (json.dumps(dataset, ensure_ascii=False, separators=(",", ":")) + "\n").encode()
    manifest = {
        "schema": 1, "datasetVersion": "geonames-20261011", "count": len(places),
        "sha256": hashlib.sha256(raw).hexdigest(), "bytes": len(raw),
        "license": "CC-BY-4.0", "attribution": "GeoNames",
        "source": "https://download.geonames.org/export/dump/",
        "inputs": {key + ".zip": value for key, value in PINNED.items()},
        "scope": "ADM1/ADM2/ADM3 records in pinned CN/HK/MO/TW exports; representative points, no boundaries or POIs; not a complete official subdivision list",
        "profile": profile,
    }
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "local_places.json").write_bytes(raw)
    (args.output / "local_places-manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: manifest[key] for key in ("count", "bytes", "sha256", "profile")}, ensure_ascii=False))


if __name__ == "__main__":
    main()
