import { execFile } from 'node:child_process';
import { cp, mkdir, readFile, readdir, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';

const execFileAsync = promisify(execFile);
const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const distRoot = path.join(projectRoot, 'dist');
const shellRoot = path.join(distRoot, 'shell');
const runtimeSourcePath = path.join(projectRoot, 'scripts', 'static-bootstrap-runtime.js');
const staticOrigins = (process.env.FLYFISH_STATIC_ORIGINS
  || 'https://static-cn.flyfish.group,https://static.flyfish.group')
  .split(',')
  .map(value => value.trim().replace(/\/+$/, ''))
  .filter(Boolean);

function fail(message) {
  throw new Error(`[static-release] ${message}`);
}

async function gitShortHash() {
  if (process.env.FLYFISH_WEB_RELEASE) {
    return '';
  }
  try {
    const { stdout } = await execFileAsync('git', ['rev-parse', '--short=10', 'HEAD'], {
      cwd: path.resolve(projectRoot, '..')
    });
    return stdout.trim();
  } catch {
    return 'nogit';
  }
}

function utcTimestamp() {
  return new Date().toISOString().replace(/[-:TZ.]/g, '').slice(0, 14);
}

async function releaseId() {
  const supplied = process.env.FLYFISH_WEB_RELEASE?.trim();
  const value = supplied || `${utcTimestamp()}-${await gitShortHash()}`;
  if (!/^[a-zA-Z0-9][a-zA-Z0-9._-]{2,80}$/.test(value)) {
    fail(`invalid release id: ${value}`);
  }
  return value;
}

function normalizeAssetPath(value) {
  const normalized = String(value || '').replace(/^\.\//, '').replace(/^\//, '');
  if (!normalized || normalized.includes('..') || /^[a-z][a-z\d+.-]*:/i.test(normalized)) {
    fail(`unsafe generated asset path: ${value}`);
  }
  return normalized;
}

function parseBuildReferences(html, label) {
  const scripts = Array.from(html.matchAll(/<script\b([^>]*)\bsrc=["']([^"']+)["']([^>]*)><\/script>/gi));
  const moduleScripts = scripts.filter(match => /\btype=["']module["']/i.test(`${match[1]} ${match[3]}`));
  if (moduleScripts.length !== 1) {
    fail(`${label} must contain exactly one module entry, found ${moduleScripts.length}`);
  }

  const links = Array.from(html.matchAll(/<link\b[^>]*>/gi));
  const linkAttribute = (tag, name) => tag.match(new RegExp(`\\b${name}=["']([^"']+)["']`, 'i'))?.[1] || '';
  const styles = [];
  const modulePreloads = [];
  const removableLinks = [];
  for (const match of links) {
    const rel = linkAttribute(match[0], 'rel').toLowerCase();
    const href = linkAttribute(match[0], 'href');
    if (rel === 'stylesheet') {
      styles.push(normalizeAssetPath(href));
      removableLinks.push(match[0]);
    } else if (rel === 'modulepreload') {
      modulePreloads.push(normalizeAssetPath(href));
      removableLinks.push(match[0]);
    }
  }

  return {
    entry: normalizeAssetPath(moduleScripts[0][2]),
    styles,
    modulePreloads,
    removableScript: moduleScripts[0][0],
    removableLinks
  };
}

function bootstrapMarkup(runtimeSource, release, target, manifest) {
  const config = JSON.stringify({
    release,
    target,
    origins: staticOrigins,
    cacheTtl: 6 * 60 * 60 * 1000,
    probeTimeout: 2500,
    manifest
  }).replace(/</g, '\\u003c');
  return `<script>window.__FLYFISH_BOOTSTRAP_CONFIG__=${config};</script>\n<script>${runtimeSource}</script>`;
}

function transformHtml(html, references, runtimeSource, release, target, manifest) {
  let transformed = html.replace(references.removableScript, '');
  for (const tag of references.removableLinks) {
    transformed = transformed.replace(tag, '');
  }
  // Vite 在相对 base 下会把 favicon 改写成 ./favicon.ico。SPA 深层路由会因此
  // 请求 /shop/detail/favicon.ico，shell 资源必须固定从站点根目录读取。
  transformed = transformed.replace(/href=(["'])\.\/favicon\.ico/gi, 'href=$1/favicon.ico');
  const markup = bootstrapMarkup(runtimeSource, release, target, manifest);
  if (!transformed.includes('</body>')) {
    fail(`${target} HTML does not contain </body>`);
  }
  return transformed.replace('</body>', `  ${markup}\n  </body>`);
}

async function copyIfPresent(source, target) {
  try {
    await cp(source, target, { recursive: true });
    return true;
  } catch (error) {
    if (error.code === 'ENOENT') {
      return false;
    }
    throw error;
  }
}

async function buildRelease() {
  const release = await releaseId();
  const releaseRoot = path.join(distRoot, 'static', 'releases', release);
  const sourceIndexPath = path.join(distRoot, 'index.html');
  const sourceViewerPath = path.join(distRoot, 'file-viewer', 'index.html');
  const [indexHtml, viewerHtml, runtimeSource] = await Promise.all([
    readFile(sourceIndexPath, 'utf8'),
    readFile(sourceViewerPath, 'utf8'),
    readFile(runtimeSourcePath, 'utf8')
  ]);
  const app = parseBuildReferences(indexHtml, 'app index');
  const viewer = parseBuildReferences(viewerHtml, 'contract viewer');

  await rm(shellRoot, { recursive: true, force: true });
  await rm(path.join(distRoot, 'static'), { recursive: true, force: true });
  await mkdir(path.join(shellRoot, 'file-viewer'), { recursive: true });
  await mkdir(releaseRoot, { recursive: true });

  for (const name of ['assets', 'images']) {
    await copyIfPresent(path.join(distRoot, name), path.join(releaseRoot, name));
  }
  // 验证码降级模式由后端返回同源 /qr.png，必须跟随 shell 部署，不能迁移到静态域名。
  await copyIfPresent(path.join(distRoot, 'qr.png'), path.join(shellRoot, 'qr.png'));
  for (const name of ['assets', 'vendor', 'flyfish-viewer-manifest.json']) {
    await copyIfPresent(
      path.join(distRoot, 'file-viewer', name),
      path.join(releaseRoot, 'file-viewer', name)
    );
  }

  const topLevelEntries = await readdir(distRoot, { withFileTypes: true });
  for (const entry of topLevelEntries) {
    if (entry.isDirectory() || entry.name === 'index.html') {
      continue;
    }
    if (entry.name === 'favicon.ico' || entry.name.endsWith('.txt')) {
      await cp(path.join(distRoot, entry.name), path.join(shellRoot, entry.name));
    }
  }

  const manifest = {
    release,
    entry: app.entry,
    styles: app.styles,
    modulePreloads: app.modulePreloads,
    viewer: {
      entry: `file-viewer/${viewer.entry}`,
      styles: viewer.styles.map(asset => `file-viewer/${asset}`),
      modulePreloads: viewer.modulePreloads.map(asset => `file-viewer/${asset}`)
    }
  };

  await Promise.all([
    writeFile(
      path.join(shellRoot, 'index.html'),
      transformHtml(indexHtml, app, runtimeSource, release, 'app', manifest)
    ),
    writeFile(
      path.join(shellRoot, 'file-viewer', 'index.html'),
      transformHtml(viewerHtml, viewer, runtimeSource, release, 'viewer', manifest)
    ),
    writeFile(path.join(releaseRoot, 'bootstrap-manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`),
    writeFile(path.join(distRoot, 'release-id.txt'), `${release}\n`)
  ]);

  await Promise.all([
    rm(sourceIndexPath, { force: true }),
    rm(path.join(distRoot, 'assets'), { recursive: true, force: true }),
    rm(path.join(distRoot, 'images'), { recursive: true, force: true }),
    rm(path.join(distRoot, 'file-viewer'), { recursive: true, force: true }),
    rm(path.join(distRoot, 'favicon.ico'), { force: true }),
    rm(path.join(distRoot, 'qr.png'), { force: true })
  ]);
  for (const entry of topLevelEntries) {
    if (!entry.isDirectory() && entry.name.endsWith('.txt')) {
      await rm(path.join(distRoot, entry.name), { force: true });
    }
  }

  console.log(`[static-release] release ${release}`);
  console.log(`[static-release] shell: dist/shell`);
  console.log(`[static-release] static: dist/static/releases/${release}`);
}

await buildRelease();
