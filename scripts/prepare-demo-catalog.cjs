// Build the deployment allowlist from the reviewed catalog and delivered JPEGs.
// This does not generate or creatively edit images and never touches the database.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const root = path.resolve(__dirname, '..');
const items = JSON.parse(fs.readFileSync(path.join(root, 'scripts/data/demo-catalog-plan.json'), 'utf8'));
const directory = path.join(root, 'backend/src/main/resources/demo/catalog-v2');
if (items.length !== 65 || new Set(items.map(p => p.title)).size !== 65) throw Error('Expected 65 distinct listings');
const hashes = new Set();
for (const p of items) {
  if (!/^item-\d{3}$/.test(p.key)) throw Error('Invalid catalog key');
  p.images = ['front', 'back', 'side'].map(view => {
    const file = `demo043-${p.key}-${view}.jpg`;
    const data = fs.readFileSync(path.join(directory, file));
    if (data[0] !== 255 || data[1] !== 216 || data.length > 1048576) throw Error('Invalid delivery image: ' + file);
    const sha256 = crypto.createHash('sha256').update(data).digest('hex');
    if (hashes.has(sha256)) throw Error('Repeated photograph: ' + file);
    hashes.add(sha256);
    return { view, file, sha256, bytes: data.length };
  });
}
const manifest = { batch: 'maimai-generated-media-043', disclosure: 'AI 生成演示图，非卖家实拍，不实际出售', items };
fs.writeFileSync(path.join(directory, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
console.log(JSON.stringify({ products: items.length, distinctImages: hashes.size, bytes: items.reduce((n, p) => n + p.images.reduce((m, i) => m + i.bytes, 0), 0) }));
