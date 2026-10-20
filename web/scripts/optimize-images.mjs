import { createHash } from 'node:crypto';
import { readFile, writeFile, readdir, rename, mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { resolve, relative, dirname } from 'node:path';

const root = fileURLToPath(new URL('..', import.meta.url));
const manifestPath = resolve(root, 'image-optimization.json');
const apply = process.argv.includes('--apply');
const hash = bytes => createHash('sha256').update(bytes).digest('hex');
const supported = /\.(png|jpe?g|webp|avif)$/i;
const privacyExcluded = new Set([
  resolve(root, 'public/images/contact/customer-wechat.jpg'),
  resolve(root, 'src/modules/shop/assets/contact/customer-wechat.jpg'),
  resolve(root, 'public/qr.png')
]);
const files = [];
async function collect(path) {
  for (const item of await readdir(path, { withFileTypes: true })) {
    const child = resolve(path, item.name);
    if (item.isDirectory()) await collect(child);
    else if (item.isFile() && supported.test(item.name) && !privacyExcluded.has(child)) files.push(child);
  }
}
await collect(resolve(root, 'src'));
await collect(resolve(root, 'lowcode/src'));
await collect(resolve(root, 'public/images'));
files.sort();
const manifest = await readFile(manifestPath, 'utf8').then(JSON.parse).catch(error => {
  if (error.code !== 'ENOENT') throw error;
  return { provider: 'Tinify', files: {} };
});
const pending = [];
for (const path of files) {
  const data = await readFile(path);
  if (manifest.files[relative(root, path)]?.sha256 !== hash(data)) pending.push({ path, data });
}
if (!apply) {
  if (pending.length) throw Error(`Tinify verification required for ${pending.length} images. Run npm run images:compress.`);
  console.log(`Tinify verification passed: ${files.length} images.`);
  process.exit(0);
}
if (!pending.length) {
  console.log(`All ${files.length} images already verified; no API calls.`);
  process.exit(0);
}
const envArg = process.argv.indexOf('--env-file');
const envPath = envArg >= 0 ? resolve(process.argv[envArg + 1]) : resolve(root, '../.local/tinify.env');
const key = process.env.TINIFY_API_KEY || (await readFile(envPath, 'utf8')).match(/^TINIFY_API_KEY=(.+)$/m)?.[1]?.trim();
if (!key) throw Error('TINIFY_API_KEY is not configured');
const authorization = `Basic ${Buffer.from(`api:${key}`).toString('base64')}`;
const headers = { Authorization: authorization, 'Content-Type': 'application/octet-stream' };
const endpoint = 'https://api.tinify.com/shrink';
const probe = await fetch(endpoint, { method: 'POST', headers, signal: AbortSignal.timeout(30000) });
await probe.arrayBuffer();
let count = Number(probe.headers.get('compression-count'));
if (![400, 429].includes(probe.status) || !probe.headers.has('compression-count') || !Number.isFinite(count)) {
  throw Error(`Tinify credential/quota check failed (${probe.status})`);
}
// Do not silently turn a publishing task into paid API usage.
if (count + pending.length > 500) throw Error(`Tinify free allowance insufficient: used ${count}, required ${pending.length}.`);
let before = 0, after = 0;
for (const { path, data } of pending) {
  const name = relative(root, path);
  const response = await fetch(endpoint, { method: 'POST', headers, body: data, signal: AbortSignal.timeout(90000) });
  if (response.status !== 201) throw Error(`Tinify compression failed for ${name} (${response.status})`);
  count = Number(response.headers.get('compression-count'));
  const location = new URL(response.headers.get('location'));
  if (location.origin !== 'https://api.tinify.com' || !location.pathname.startsWith('/output/')) throw Error('Unexpected Tinify output URL');
  const result = await fetch(location, { headers: { Authorization: authorization }, signal: AbortSignal.timeout(90000) });
  if (!result.ok || !result.headers.get('content-type')?.startsWith('image/')) throw Error(`Invalid Tinify output for ${name}`);
  const optimized = Buffer.from(await result.arrayBuffer());
  if (!optimized.length) throw Error(`Empty Tinify output for ${name}`);
  const kept = optimized.length < data.length ? optimized : data;
  const backup = resolve(root, '../target/tinify-originals', name);
  await mkdir(dirname(backup), { recursive: true });
  await writeFile(backup, data, { flag: 'wx' }).catch(error => { if (error.code !== 'EEXIST') throw error; });
  if (kept !== data) {
    await writeFile(`${path}.tinify-next`, kept);
    await rename(`${path}.tinify-next`, path);
  }
  manifest.files[name] = { sha256: hash(kept), originalBytes: data.length, bytes: kept.length, tinifyBytes: optimized.length,
    width: Number(result.headers.get('image-width')), height: Number(result.headers.get('image-height')) };
  await writeFile(`${manifestPath}.next`, `${JSON.stringify(manifest, null, 2)}\n`);
  await rename(`${manifestPath}.next`, manifestPath);
  before += data.length; after += kept.length;
  console.log(`${name}: ${data.length} -> ${kept.length} bytes`);
}
console.log(JSON.stringify({ processed: pending.length, before, after, saved: before - after, compressionCount: count }));
