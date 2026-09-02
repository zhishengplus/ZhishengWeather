// 校验 boundaries.bin：按 Kotlin 侧相同逻辑解析，抽查城市与区县名消歧
import { readFileSync } from "node:fs";
import { join } from "node:path";

const path = join(process.cwd(), "app", "src", "main", "assets", "geo", "boundaries.bin");
const b = readFileSync(path);
let p = 0;

function i16() { const v = b.readInt16LE(p); p += 2; return v; }
function i32() { const v = b.readInt32LE(p); p += 4; return v; }
function readName() { const len = i16(); const s = b.subarray(p, p + len).toString("utf8"); p += len; return s; }
function varint() {
  let shift = 0, r = 0;
  for (;;) {
    const byte = b[p++];
    r |= (byte & 0x7f) << shift;
    if ((byte & 0x80) === 0) break;
    shift += 7;
  }
  return r;
}
function zz() { const v = varint(); return (v >>> 1) ^ -(v & 1); }
function pathPts() {
  const n = varint();
  const pts = [];
  let x = 0, y = 0;
  for (let i = 0; i < n; i++) {
    x += zz(); y += zz();
    pts.push([x / 1e5, y / 1e5]);
  }
  return pts;
}

const magic = i32();
if (magic !== 0x31594442) { console.error("bad magic:", magic.toString(16)); process.exit(1); }
i16(); // version
const cityCount = i32();
const stubCount = i32();

const cities = [];
for (let i = 0; i < cityCount; i++) {
  const adcode = i32(); i32(); // parent
  const name = readName();
  const lon = i32() / 1e6, lat = i32() / 1e6;
  const offset = i32(), len = i32();
  cities.push({ adcode, name, lon, lat, offset, len });
}
const stubs = [];
for (let i = 0; i < stubCount; i++) {
  const adcode = i32();
  const parent = i32();
  const name = readName();
  const lon = i32() / 1e6, lat = i32() / 1e6;
  const offset = i32(), len = i32();
  stubs.push({ adcode, parent, name, lon, lat, offset, len });
}

let ok = 0, bad = 0, totalPts = 0, maxBlock = 0;
const problems = [];
for (const c of cities) {
  try {
    p = c.offset;
    const end = c.offset + c.len;
    const ringCount = varint();
    if (ringCount < 1) throw new Error("no rings");
    for (let r = 0; r < ringCount; r++) { const pts = pathPts(); totalPts += pts.length; if (pts.length < 2) throw new Error("tiny ring"); }
    const lineCount = varint();
    for (let l = 0; l < lineCount; l++) { const pts = pathPts(); totalPts += pts.length; if (pts.length < 2) throw new Error("tiny line"); }
    if (p !== end) throw new Error(`length mismatch: consumed ${p - c.offset} of ${c.len}`);
    // bbox 合理性（中国范围外视为异常）
    p = c.offset; varint();
    const first = pathPts();
    for (const [lon, lat] of first) {
      if (lon < 73 || lon > 136 || lat < 15 || lat > 55) { if (c.name !== "三沙市") throw new Error(`coord out of CN: ${lon},${lat}`); }
    }
    ok++;
    maxBlock = Math.max(maxBlock, c.len);
  } catch (e) {
    bad++;
    if (problems.length < 10) problems.push(`${c.name}(${c.adcode}): ${e.message}`);
  }
}

console.log(`magic ok, cities=${cityCount}, stubs=${stubCount}`);
console.log(`blocks decoded: ok=${ok}, bad=${bad}, total points=${totalPts}, max block=${(maxBlock / 1024).toFixed(1)}KB`);
if (problems.length) console.log("problems:", problems);

// 抽查
function find(n) { return cities.filter((c) => c.name === n); }
for (const n of ["杭州市", "北京市", "嘉峪关市", "五指山市", "澳门特别行政区", "东莞市", "三沙市"]) {
  const hits = find(n);
  const c = hits[0];
  console.log(`${n}: ${c ? `adcode=${c.adcode} center=(${c.lon},${c.lat}) block=${c.len}B` : "MISSING"}`);
}
const chaoyang = stubs.filter((s) => s.name === "朝阳区");
console.log(`朝阳区 stubs: ${chaoyang.map((s) => `${s.parent} (${s.lon},${s.lat})`).join(", ")}`);
const tiesi = stubs.filter((s) => s.name === "铁西区");
console.log(`铁西区 stubs: ${tiesi.map((s) => `${s.parent} (${s.lon},${s.lat})`).join(", ")}`);

// 抽查区县自身轮廓块（格式 v2）
for (const n of ["南川区", "朝阳区"]) {
  for (const s of stubs.filter((x) => x.name === n)) {
    if (!s.offset) { console.log(`${n}(${s.parent}): 无自身轮廓`); continue; }
    p = s.offset;
    const ringCount = varint();
    let minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9;
    for (let r = 0; r < ringCount; r++) {
      for (const [lon, lat] of pathPts()) {
        minX = Math.min(minX, lon); maxX = Math.max(maxX, lon);
        minY = Math.min(minY, lat); maxY = Math.max(maxY, lat);
      }
    }
    const span = Math.max(maxX - minX, maxY - minY);
    console.log(`${n}(${s.adcode}, parent ${s.parent}): rings=${ringCount}, span=${span.toFixed(2)}°, block=${s.len}B`);
  }
}
