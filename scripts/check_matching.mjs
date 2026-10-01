// 模拟 Kotlin BoundaryMatcher 的匹配链，对 bin 中全部条目及其常用命名变体做解析测试
import { readFileSync } from "node:fs";
import { join } from "node:path";

const b = readFileSync(join(process.cwd(), "app", "src", "main", "assets", "geo", "boundaries.bin"));
let p = 0;
const i32 = () => { const v = b.readInt32LE(p); p += 4; return v; };
const i16 = () => { const v = b.readInt16LE(p); p += 2; return v; };
const nm = () => { const l = i16(); const s = b.subarray(p, p + l).toString("utf8"); p += l; return s; };

p = 6;
const cityCount = i32(), stubCount = i32();
const cities = [];
for (let i = 0; i < cityCount; i++) {
  const adcode = i32(); i32();
  const name = nm(); i32(); i32(); i32(); i32();
  cities.push({ adcode, name });
}
const districts = [];
for (let i = 0; i < stubCount; i++) {
  i32();
  const parent = i32();
  const name = nm(); i32(); i32(); i32(); i32();
  districts.push({ adcode: 0, parent, name });
}

// 与 Kotlin 匹配链一致的模拟实现
const SUFFIXES = ["特别行政区", "维吾尔自治区", "回族自治区", "壮族自治区", "自治区", "省", "市", "地区", "自治州", "盟", "林区"];
const cityByName = new Map();
for (const c of cities) {
  if (!cityByName.has(c.name)) cityByName.set(c.name, []);
  cityByName.get(c.name).push(c);
}
const cityByAd = new Map(cities.map((c) => [c.adcode, c]));
const districtByName = new Map();
for (const d of districts) {
  if (!districtByName.has(d.name)) districtByName.set(d.name, []);
  districtByName.get(d.name).push(d);
}

function matchByName(name) {
  const found = new Map();
  const add = (list) => list?.forEach((e) => { if (!found.has(e.adcode)) found.set(e.adcode, e); });
  add(cityByName.get(name));
  add(districtByName.get(name));
  let stripped = name;
  for (const s of SUFFIXES) {
    if (stripped.endsWith(s)) { stripped = stripped.slice(0, -s.length); break; }
  }
  if (stripped.length < name.length) {
    add(cityByName.get(stripped));
    add(districtByName.get(stripped));
  }
  if (!name.endsWith("市") && stripped.length >= 2 && stripped.length <= 4) add(cityByName.get(stripped + "市"));
  if (name.endsWith("州") && name.length >= 3 && name.length <= 6) {
    const base = name.slice(0, -1);
    for (const list of cityByName.values()) {
      for (const e of list) {
        if (e.name.startsWith(base) && (e.name.endsWith("自治州") || e.name.endsWith("盟")) && !found.has(e.adcode)) found.set(e.adcode, e);
      }
    }
  }
  return [...found.values()];
}
function resolve(name) {
  if (matchByName(name).length) return true;
  if (name.length >= 2 && name.length <= 5) {
    for (const list of [...cityByName.values(), ...districtByName.values()]) {
      if (list.some((e) => e.name.startsWith(name))) return true;
    }
  }
  return false;
}

// ── 1. 全称自匹配：数据里每个条目按全称必须能解析 ──
const allNames = [...new Set([...cities.map((c) => c.name), ...districts.map((d) => d.name)])];
const fullMiss = allNames.filter((n) => !resolve(n));

// ── 2. 常用简称变体：去 suffix 的短名、X州简称、无后缀专名 ──
const variants = new Set();
for (const n of allNames) {
  for (const s of SUFFIXES) {
    if (n.endsWith(s)) {
      const base = n.slice(0, -s.length);
      if (base.length >= 2) variants.add(base);
      if (n.endsWith("自治州")) variants.add(base + "州");
      break;
    }
  }
}
// 用户/地理编码常用的裸短名
["台湾", "香港", "澳门", "北京", "上海", "天津", "重庆", "甘孜州", "阿坝州", "凉山州", "湘西州", "恩施州", "延边州",
  "巴音郭楞州", "伊犁州", "博尔塔拉州", "克孜勒苏州", "大理州", "红河州", "文山州", "西双版纳州", "楚雄州", "德宏州",
  "怒江州", "迪庆州", "临夏州", "甘南州", "黄南州", "果洛州", "玉树州", "海西州", "海北州", "海南州", "海东",
  "锡林郭勒州", "呼伦贝尔", "兴安", "乌兰察布", "巴彦淖尔", "阿拉善盟", "济源", "仙桃", "潜江", "天门", "神农架",
  "五指山", "琼海", "文昌", "万宁", "东方", "定安", "屯昌", "澄迈", "临高", "白沙", "昌江", "乐东", "陵水", "保亭", "琼中",
].forEach((v) => variants.add(v));
const variantMiss = [...variants].filter((v) => !resolve(v));

console.log(`全称条目: ${allNames.length} 个，解析失败 ${fullMiss.length} 个`);
if (fullMiss.length) console.log("  失败:", fullMiss.join(", "));
console.log(`简称变体: ${variants.size} 个，解析失败 ${variantMiss.length} 个`);
if (variantMiss.length) console.log("  失败:", variantMiss.join(", "));
