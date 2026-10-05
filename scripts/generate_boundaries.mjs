// 生成城市卡片行政区划轮廓地图数据（app/src/main/assets/geo/boundaries.bin）
//
// 流程：
//   1. 从 DataV GeoAtlas 抓取全国城市 + 区县级边界（一次抓取，无需手工步骤）
//   2. 每城市独立构建 topojson 拓扑 → 共享边只 DP 简化一次（容差按卡片渲染分辨率定标）
//   3. 城市外轮廓 = 区县拓扑 merge（与区县边界严格共边，无双线错位）
//      区县内部线 = 拓扑内部 mesh（每条内部边界只画一次）
//   4. 编码为紧凑二进制：坐标 1e-5° 量化 + zigzag varint delta，索引与几何分离
//
// 运行：npm install && node generate_boundaries.mjs（在仓库根目录执行）
import { writeFile, mkdir } from "node:fs/promises";
import { join } from "node:path";
import { topology } from "topojson-server";
import { merge, mesh } from "topojson-client";

const BASE = "https://geo.datav.aliyun.com/areas_v3/bound";
const OUT = join(process.cwd(), "app", "src", "main", "assets", "geo");
const DELAY = 300;          // 抓取限速 ms
const OUT_Q = 1e5;          // 输出坐标量化 1e-5°（约 1.1m）
const TOPO_Q = 1e6;         // 拓扑构建量化
const CARD_PX = 300;        // 卡片地图区参考宽度（px）
const MAGIC = 0x31594442;   // "BDY1"
const VERSION = 2;          // 2: 区县条目含自身轮廓块

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
      console.warn(`  retry ${i + 1} for ${url}: ${e.message}`);
      await sleep(1000);
    }
  }
}

// ── 几何工具 ──────────────────────────────────────────────

function forEachRing(geom, fn) {
  if (!geom) return;
  if (geom.type === "Polygon") geom.coordinates.forEach(fn);
  else if (geom.type === "MultiPolygon") geom.coordinates.forEach((poly) => poly.forEach(fn));
}

function geomBbox(geoms) {
  let minLon = Infinity, maxLon = -Infinity, minLat = Infinity, maxLat = -Infinity;
  for (const g of geoms) {
    forEachRing(g, (ring) => {
      for (const [lon, lat] of ring) {
        if (lon < minLon) minLon = lon;
        if (lon > maxLon) maxLon = lon;
        if (lat < minLat) minLat = lat;
        if (lat > maxLat) maxLat = lat;
      }
    });
  }
  if (minLon === Infinity) return null;
  return [minLon, maxLon, minLat, maxLat];
}

// Douglas-Peucker，度坐标，保首尾点（拓扑共享边的锚点不受影响）
function dp(points, eps) {
  if (points.length <= 2) return points;
  const eps2 = eps * eps;
  const keep = new Uint8Array(points.length);
  keep[0] = keep[points.length - 1] = 1;
  const stack = [[0, points.length - 1]];
  while (stack.length) {
    const [s, e] = stack.pop();
    let maxD2 = 0, idx = -1;
    const ax = points[s][0], ay = points[s][1];
    const bx = points[e][0], by = points[e][1];
    const dx = bx - ax, dy = by - ay;
    const len2 = dx * dx + dy * dy;
    for (let i = s + 1; i < e; i++) {
      const px = points[i][0], py = points[i][1];
      let d2;
      if (len2 === 0) {
        const vx = px - ax, vy = py - ay;
        d2 = vx * vx + vy * vy;
      } else {
        let t = ((px - ax) * dx + (py - ay) * dy) / len2;
        t = t < 0 ? 0 : t > 1 ? 1 : t;
        const vx = px - (ax + t * dx), vy = py - (ay + t * dy);
        d2 = vx * vx + vy * vy;
      }
      if (d2 > maxD2) { maxD2 = d2; idx = i; }
    }
    if (maxD2 > eps2 && idx > 0) {
      keep[idx] = 1;
      stack.push([s, idx], [idx, e]);
    }
  }
  const out = [];
  for (let i = 0; i < points.length; i++) if (keep[i]) out.push(points[i]);
  return out;
}

// 拓扑 arcs（量化 delta int）↔ 度坐标折线
function arcsToDeg(topo) {
  const [sx, sy] = topo.transform.scale;
  const [tx, ty] = topo.transform.translate;
  return topo.arcs.map((arc) => {
    const pts = [];
    let x = 0, y = 0;
    for (const [dx, dy] of arc) {
      x += dx; y += dy;
      pts.push([tx + x * sx, ty + y * sy]);
    }
    return pts;
  });
}

function degToArc(pts, scale, translate) {
  const out = [];
  let px = null, py = null;
  for (const [lon, lat] of pts) {
    const x = Math.round((lon - translate[0]) / scale[0]);
    const y = Math.round((lat - translate[1]) / scale[1]);
    out.push(px === null ? [x, y] : [x - px, y - py]);
    px = x; py = y;
  }
  return out;
}

// ── 二进制编码 ────────────────────────────────────────────

function zz(v) { return (v << 1) ^ (v >> 31); }

function writeVarint(out, v) {
  let u = v < 0 ? v + 4294967296 : v;
  while (u > 0x7f) { out.push((u & 0x7f) | 0x80); u >>>= 7; }
  out.push(u);
}

// 编码一条线/环：varint 点数 + zigzag varint delta（1e-5° 量化，相邻重复点去重）
function encodePath(ptsDeg) {
  const ints = [];
  for (const [lon, lat] of ptsDeg) {
    const x = Math.round(lon * OUT_Q), y = Math.round(lat * OUT_Q);
    const n = ints.length;
    if (n >= 2 && ints[n - 2] === x && ints[n - 1] === y) continue;
    ints.push(x, y);
  }
  if (ints.length < 4) return null;
  const out = [];
  writeVarint(out, ints.length / 2);
  let px = 0, py = 0;
  for (let i = 0; i < ints.length; i += 2) {
    writeVarint(out, zz(ints[i] - px));
    writeVarint(out, zz(ints[i + 1] - py));
    px = ints[i]; py = ints[i + 1];
  }
  return out;
}

function utf8(s) { return Array.from(Buffer.from(s, "utf8")); }

// 块布局：varint 外环数 + 各外环 + varint 内线数 + 各内线
function assembleBlock(ringParts, lineParts) {
  const out = [];
  writeVarint(out, ringParts.length);
  for (const e of ringParts) out.push(...e);
  writeVarint(out, lineParts.length);
  for (const e of lineParts) out.push(...e);
  return out;
}

// ── 抓取 ──────────────────────────────────────────────────

async function collectCities(provinces) {
  const cities = [];
  const pushCity = (adcode, name, center, parent, subFeats) => {
    const subs = subFeats
      .filter((f) => f.geometry && f.geometry.coordinates && f.geometry.coordinates.length)
      .filter((f) => f.properties.name !== "市辖区")
      .map((f) => ({
        adcode: f.properties.adcode ?? 0,
        name: f.properties.name,
        isDistrict: f.properties.level === "district",
        center: f.properties.center || f.properties.centroid || null,
        geom: f.geometry,
      }));
    if (subs.length === 0) return false;
    cities.push({ adcode, name, center, parent, subs });
    return true;
  };

  // 递归收集一个 adcode 的子级；返回 true 表示该 adcode 已作为城市收录
  async function buildCity(adcode, name, center, parent) {
    let subFeats = [], subCities = [];
    try {
      const data = await fetchJSON(`${BASE}/${adcode}_full.json`);
      await sleep(DELAY);
      subFeats = data.features.filter(
        (f) => (f.properties.level === "district" || f.properties.level === "street") && f.geometry
      );
      subCities = data.features.filter((f) => f.properties.level === "city" && f.geometry);
    } catch (e) {
      // _full 不存在（无下级的直筒子/县级单位），走叶子回退
      await sleep(DELAY);
    }
    if (subFeats.length > 0) {
      pushCity(adcode, name, center, parent, subFeats);
      return true;
    }
    if (subCities.length > 0) {
      // 空壳分组节点：递归其 city 子级
      for (const sc of subCities) {
        if (virtualNode(sc.properties.name)) continue;
        await buildCity(
          sc.properties.adcode,
          sc.properties.name,
          sc.properties.center || sc.properties.centroid,
          adcode
        );
        await sleep(DELAY);
      }
      return true;
    }
    // 叶子节点：非 _full 端点取自身轮廓（无区县内部线）
    try {
      const self = await fetchJSON(`${BASE}/${adcode}.json`);
      await sleep(DELAY);
      const g = self.features?.[0]?.geometry;
      if (g) {
        pushCity(adcode, name, center, parent, [{
          properties: { name, level: "district", adcode, center },
          geometry: g,
        }]);
        return true;
      }
    } catch { /* ignore */ }
    return false;
  }

  for (const prov of provinces) {
    const p = prov.properties;
    process.stdout.write(`${p.name} (${p.adcode}) `);
    try {
      const data = await fetchJSON(`${BASE}/${p.adcode}_full.json`);
      await sleep(DELAY);
      const cityFeats = data.features.filter(
        (f) => f.properties.level === "city" && f.geometry && !virtualNode(f.properties.name)
      );
      if (cityFeats.length === 0) {
        // 直辖市 / 港澳：省级本身即城市，区县为其子级
        const ok = await buildCity(p.adcode, p.name, p.center || p.centroid, 0);
        console.log(ok ? "· 直辖" : "· 空");
      } else {
        let n = 0;
        for (const c of cityFeats) {
          try {
            if (await buildCity(c.properties.adcode, c.properties.name,
              c.properties.center || c.properties.centroid, p.adcode)) n++;
          } catch (e) { console.warn(`  skip ${c.properties.name}: ${e.message}`); }
          await sleep(DELAY);
        }
        console.log(`· ${n} 城市`);
      }
    } catch (e) {
      // 省级 _full 不可用（如台湾省无区县级数据）：回退省级自身轮廓作为无内部线的城市
      const ok = await buildCity(p.adcode, p.name, p.center || p.centroid, 0);
      console.log(ok ? "· 省级回退" : ` ERROR: ${e.message}`);
    }
  }
  return cities;
}

// ── 每城市：拓扑简化 + 轮廓 merge + 内部线 mesh ───────────

function buildCityBlock(city) {
  const geoms = city.subs.map((s) => s.geom);
  const bbox = geomBbox(geoms);
  if (!bbox) return null;
  const [minLon, maxLon, minLat, maxLat] = bbox;
  const spanDeg = Math.max(maxLon - minLon, maxLat - minLat);
  // 渲染范围 = bbox + 两侧各 0.04×span 的极窄边距（与 CityOutlineMap 渲染一致，
  // 轮廓约以 93% 宽度充满卡片），容差取半个像素对应的度数
  const expand = spanDeg * 0.04;
  const eps = Math.max(((spanDeg + 2 * expand) / CARD_PX) * 0.5, 1.5e-4);

  const objmap = {};
  city.subs.forEach((s, i) => { objmap["g" + i] = s.geom; });
  const topo = topology(objmap, TOPO_Q);

  // 拓扑共享边统一 DP 简化
  const { scale, translate } = topo.transform;
  const arcsDeg = arcsToDeg(topo);
  topo.arcs = arcsDeg.map((pts) => degToArc(dp(pts, eps), scale, translate));

  const cityGeoms = Object.values(topo.objects);

  // 城市外轮廓：区县 merge（与区县边界严格共边）
  let outerRings = [];
  const merged = merge(topo, cityGeoms);
  if (merged) forEachRing(merged, (ring) => { if (ring.length >= 4) outerRings.push(ring); });

  let fallback = false;
  if (outerRings.length === 0) {
    // merge 失败（极少见）：标记回退，由主流程改用非 _full 端点的城市自身轮廓
    fallback = true;
  }

  // 区县内部线：共享边 mesh（每条内部边界一次）
  const interior = mesh(topo, { type: "GeometryCollection", geometries: cityGeoms }, (a, b) => a !== b);
  const innerLines = (interior && interior.coordinates ? interior.coordinates : []).filter((l) => l.length >= 2);

  // 每个区县自身轮廓（单要素 merge，与城市外轮廓/邻区严格共边）
  const districtRings = cityGeoms.map((g) => {
    const rings = [];
    const m = merge(topo, [g]);
    if (m) forEachRing(m, (ring) => { if (ring.length >= 4) rings.push(ring); });
    return rings;
  });

  return { outerRings, innerLines, districtRings, eps, fallback };
}

async function fetchFallbackOutline(city) {
  try {
    const data = await fetchJSON(`${BASE}/${city.adcode}.json`);
    await sleep(DELAY);
    const g = data.features?.[0]?.geometry;
    const rings = [];
    if (g) forEachRing(g, (r) => { if (r.length >= 4) rings.push(r); });
    return rings;
  } catch { return []; }
}

// ── 主流程 ────────────────────────────────────────────────

async function main() {
  await mkdir(OUT, { recursive: true });

  console.log("Fetching provinces...");
  const provinces = (await fetchJSON(`${BASE}/100000_full.json`))
    .features.filter((f) => f.properties.level === "province");

  console.log("Collecting cities...");
  const cities = await collectCities(provinces);
  console.log(`\nCollected ${cities.length} cities.\n`);

  // 索引与块编码（城市条目与区县条目同布局：adcode,parent,name,lon,lat,offset,len）
  const cityEntries = [];
  const stubEntries = [];

  let totalRings = 0, totalLines = 0, cityPts = 0, districtPts = 0, fallbackCount = 0;

  for (const city of cities) {
    let block = buildCityBlock(city);
    if (block && block.fallback) {
      const rings = await fetchFallbackOutline(city);
      if (rings.length > 0) {
        block.outerRings = rings.map((r) => dp(r, block.eps));
        block.fallback = false;
        fallbackCount++;
      }
    }
    if (!block || block.fallback || (block.outerRings.length === 0)) {
      console.warn(`  !! skip ${city.name} (${city.adcode}): no outline`);
      continue;
    }

    const ringParts = [], lineParts = [];
    for (const ring of block.outerRings) { const e = encodePath(ring); if (e) { ringParts.push(e); totalRings++; cityPts += ring.length; } }
    for (const line of block.innerLines) { const e = encodePath(line); if (e) { lineParts.push(e); totalLines++; cityPts += line.length; } }
    if (ringParts.length === 0) continue;
    const center = city.center || bboxCenter(city);
    cityEntries.push({
      adcode: city.adcode, parent: city.parent, name: city.name,
      lon: Math.round((center[0] ?? 0) * 1e6), lat: Math.round((center[1] ?? 0) * 1e6),
      bytes: assembleBlock(ringParts, lineParts),
    });

    // 区县自身轮廓：以区县名为名的城市卡片直接渲染所属区县
    city.subs.forEach((s, i) => {
      if (!s.isDistrict || !s.center) return;
      const parts = [];
      for (const ring of block.districtRings[i] || []) {
        const e = encodePath(ring);
        if (e) { parts.push(e); districtPts += ring.length; }
      }
      stubEntries.push({
        adcode: s.adcode ?? 0, parent: city.adcode, name: s.name,
        lon: Math.round(s.center[0] * 1e6), lat: Math.round(s.center[1] * 1e6),
        bytes: parts.length ? assembleBlock(parts, []) : null,
      });
    });
  }

  // 组装：头部 + 索引（城市+区县）+ 城市块 + 区县块
  const head = Buffer.alloc(14);
  head.writeInt32LE(MAGIC, 0);
  head.writeUInt16LE(VERSION, 4);
  head.writeInt32LE(cityEntries.length, 6);
  head.writeInt32LE(stubEntries.length, 10);

  const allEntries = [...cityEntries, ...stubEntries];
  // 索引条目 = adcode(4)+parent(4)+nameLen(2)+name+lon(4)+lat(4)+offset(4)+len(4)
  let cursor = head.length + allEntries.reduce((s2, e) => s2 + 26 + Buffer.byteLength(e.name, "utf8"), 0);
  for (const e of allEntries) {
    if (!e.bytes) { e.offset = 0; e.len = 0; continue; }
    e.offset = cursor; e.len = e.bytes.length; cursor += e.bytes.length;
  }

  const idx = [];
  for (const e of allEntries) {
    writeI32(idx, e.adcode); writeI32(idx, e.parent);
    writeI16(idx, Buffer.byteLength(e.name, "utf8"));
    idx.push(...utf8(e.name));
    writeI32(idx, e.lon); writeI32(idx, e.lat);
    writeI32(idx, e.offset); writeI32(idx, e.len);
  }

  const file = Buffer.concat([
    head,
    Buffer.from(idx),
    ...cityEntries.map((e) => Buffer.from(e.bytes)),
    ...stubEntries.flatMap((e) => (e.bytes ? [Buffer.from(e.bytes)] : [])),
  ]);
  const path = join(OUT, "boundaries.bin");
  await writeFile(path, file);

  console.log(`\nWrote ${path}`);
  console.log(`  cities: ${cityEntries.length}, district entries: ${stubEntries.length} (own outline: ${stubEntries.filter((e) => e.bytes).length})`);
  console.log(`  city rings: ${totalRings}, interior lines: ${totalLines}, points: ${cityPts} + ${districtPts}`);
  console.log(`  fallback outlines: ${fallbackCount}`);
  console.log(`  file size: ${(file.length / 1024).toFixed(0)} KB`);
}

function bboxCenter(city) {
  const b = geomBbox(city.subs.map((s) => s.geom));
  return b ? [(b[0] + b[1]) / 2, (b[2] + b[3]) / 2] : [0, 0];
}

function writeI16(arr, v) { arr.push(v & 0xff, (v >> 8) & 0xff); }
function writeI32(arr, v) { arr.push(v & 0xff, (v >> 8) & 0xff, (v >> 16) & 0xff, (v >>> 24) & 0xff); }

main().catch((e) => { console.error(e); process.exit(1); });
