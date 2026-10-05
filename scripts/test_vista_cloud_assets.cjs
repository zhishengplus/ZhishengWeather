const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const sharp = require(process.env.VISTA_SHARP_MODULE || 'sharp');
(async () => {
  for (const mode of ['day', 'night']) {
    const file = path.resolve(__dirname, `../artwork/rejected-cloud-20260907/vista_cloud_bank_${mode}.png`);
    assert.ok(fs.existsSync(file), `Cloud texture missing: ${mode}`);
    const { data, info } = await sharp(file).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
    assert.equal(info.width, 640);
    assert.equal(info.height, 420);
    const alpha = (x,y) => data[(y * info.width + x) * 4 + 3];
    assert.equal(alpha(10,10), 0, 'Toolbar corner must remain clear');
    assert.equal(alpha(10,210), 0, 'Primary temperature reading plane must remain clear');
    assert.equal(alpha(400,419), 0, 'Clouds must fade before downstream cards');
    assert.ok(data.some((v,i) => i % 4 === 3 && v > 180), 'A cloud needs a visible body, not an invisible wash');
    console.log(`Cloud artwork safeguards passed: ${mode}`);
  }
})().catch(e => { console.error(e.message); process.exitCode=1; });
