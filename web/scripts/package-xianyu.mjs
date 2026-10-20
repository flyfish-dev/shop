import fs from 'node:fs/promises';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
const root = path.resolve('dist-xianyu');
const apiBase=process.env.VITE_XIANYU_API_BASE || '';
if(apiBase && !/^https:\/\//.test(apiBase)) throw new Error('A release package requires an approved HTTPS API endpoint');
await fs.rename(path.join(root, 'xianyu.html'), path.join(root, 'index.html'));
await fs.cp('public/file-viewer', path.join(root, 'file-viewer'), { recursive: true });
await fs.copyFile('public/favicon.ico', path.join(root, 'favicon.ico'));
const viewer = path.join(root, 'file-viewer/index.html');
let html = await fs.readFile(viewer, 'utf8');
html = html.replace("window.__FLYFISH_STATIC_RUNTIME__?.baseUrl || window.location.origin + '/'", "new URL('../', window.location.href).href");
await fs.writeFile(viewer, html);
const entries = [];
async function walk(dir) {
  for (const entry of await fs.readdir(dir, { withFileTypes: true })) {
    const file = path.join(dir, entry.name);
    if (entry.isDirectory()) await walk(file);
    else {
      const size = (await fs.stat(file)).size;
      const relative = path.relative(root, file);
      if (entry.isSymbolicLink() || /[^\x20-\x7e]/.test(relative) || entry.name.startsWith('.') || /\.(gz|lnk)$/i.test(relative)
        || size > 20 * 1024 * 1024) throw new Error(`Invalid package entry: ${relative}`);
      entries.push({ path: relative, size });
    }
  }
}
await walk(root);
const total = entries.reduce((sum, e) => sum + e.size, 0);
if (entries.length > 3000 || total > 60 * 1024 * 1024) throw new Error('Miniapp package exceeds platform limits');
const index = await fs.readFile(path.join(root, 'index.html'), 'utf8');
if (!index.includes('checkEvn.js') || !index.includes('miniapp-uni-api')) throw new Error('Official SDK missing');
await fs.writeFile(path.join(root, 'package-manifest.json'), JSON.stringify({ files: entries.length, totalBytes: total,
  apiBase, deployable:!!apiBase, platformAcceptance:'pending', runtime:'xianyu' }, null, 2));
const zip = path.resolve('dist-xianyu.zip');
await fs.rm(zip, { force: true });
execFileSync('zip', ['-q', '-r', zip, '.'], { cwd: root });
console.log(JSON.stringify({ files: entries.length, totalBytes: total, zip }));
