// 覆盖率核查：DataV 源数据中应有的城市级单位 vs boundaries.bin 实际收录
import { readFileSync } from "node:fs";
import { join } from "node:path";

const BASE = "https://geo.datav.aliyun.com/areas_v3/bound";
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const virtualNode = (name) => /直辖县级行政区划/.test(name);

async function fetchJSON(url, retries = 3) {
  for (let i = 0; i < retries; i++) {
    try {
      const res = await fetch(url);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      return await res.json();
    } catch (e) {
      if (i === retries - 1) throw e;
      await sleep(800);
    }
  }
}

// 读取 bin 中的城市 adcode 集合
const b = readFileSync(join(process.cwd(), "app", "src", "main", "assets", "geo", "boundaries.bin"));
let p = 0;
function i16() { const v = b.readInt16LE(p); p += 2; return v; }
function i32() { const v = b.readInt32LE(p); p += 4; return v; }
p += 6; // magic + version
const cityCount = i32();
i32(); // stubCount 未用
const generated = new Map();
for (let i = 0; i < cityCount; i++) {
  const adcode = i32(); i32();
  const len = i16(); const name = b.subarray(p, p + len).toString("utf8"); p += len;
  p += 16;
  generated.set(adcode, name);
}

// 收集源数据中应有的城市
const provinces = (await fetchJSON(`${BASE}/100000_full.json`)).features
  .filter((f) => f.properties.level === "province");
const expected = new Map(); // adcode -> name
for (const prov of provinces) {
  const pr = prov.properties;
  const children = await fetchJSON(`${BASE}/${pr.adcode}_full.json`).catch(() => null);
  await sleep(250);
  if (!children) {
    expected.set(pr.adcode, `${pr.name}（省级回退）`);
    continue;
  }
  const cityFeats = children.features.filter((f) => f.properties.level === "city" && f.geometry && !virtualNode(f.properties.name));
  if (cityFeats.length === 0) {
    expected.set(pr.adcode, pr.name); // 直辖市/港澳：省级即城市
    continue;
  }
  for (const c of cityFeats) expected.set(c.properties.adcode, c.properties.name);
}

const missing = [...expected].filter(([ac]) => !generated.has(ac));
const extra = [...generated].filter(([ac]) => !expected.has(ac));

console.log(`源数据应有城市: ${expected.size}`);
console.log(`bin 实际收录:   ${generated.size}`);
console.log(`缺失 ${missing.length} 个:`, missing.map(([ac, n]) => `${n}(${ac})`).join(", ") || "无");
console.log(`多出 ${extra.length} 个:`, extra.map(([ac]) => ac).join(", ") || "无");
