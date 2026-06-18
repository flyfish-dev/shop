import { execFile } from 'node:child_process';
import { cp, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';

const execFileAsync = promisify(execFile);

const packageName = '@flyfish-group/file-viewer-web';
const packageVersion = process.env.FLYFISH_FILE_VIEWER_VERSION || 'latest';
const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const publicViewerRoot = path.join(projectRoot, 'public', 'file-viewer');

function fail(message, cause) {
  console.error(`[file-viewer] ${message}`);
  if (cause) {
    console.error(cause);
  }
  process.exit(1);
}

async function run(command, args, options = {}) {
  try {
    return await execFileAsync(command, args, {
      cwd: projectRoot,
      maxBuffer: 1024 * 1024 * 16,
      ...options
    });
  } catch (error) {
    fail(`${command} ${args.join(' ')} 执行失败`, error.stderr || error.message);
  }
}

async function packLatestViewer(tempDir) {
  const { stdout } = await run('npm', [
    'pack',
    `${packageName}@${packageVersion}`,
    '--pack-destination',
    tempDir,
    '--json'
  ]);
  let metadata;
  try {
    metadata = JSON.parse(stdout)[0];
  } catch (error) {
    fail('解析 npm pack 输出失败', stdout || error.message);
  }
  if (!metadata?.filename || !metadata?.version) {
    fail('npm pack 没有返回有效的包文件信息', stdout);
  }
  return {
    version: metadata.version,
    tarball: path.join(tempDir, metadata.filename)
  };
}

async function syncViewer() {
  const tempDir = await mkdtemp(path.join(os.tmpdir(), 'flyfish-file-viewer-'));
  try {
    const packed = await packLatestViewer(tempDir);
    await run('tar', ['-xzf', packed.tarball, '-C', tempDir]);

    const viewerRoot = path.join(tempDir, 'package', 'viewer');
    const indexHtml = path.join(viewerRoot, 'index.html');
    try {
      await readFile(indexHtml, 'utf8');
    } catch (error) {
      fail(`${packageName}@${packed.version} 不包含独立 viewer/index.html，不能作为 public/file-viewer 产物`, error.message);
    }

    await rm(publicViewerRoot, { recursive: true, force: true });
    await cp(viewerRoot, publicViewerRoot, { recursive: true });
    await writeFile(path.join(publicViewerRoot, 'flyfish-viewer-manifest.json'), `${JSON.stringify({
      name: packageName,
      version: packed.version,
      entry: 'index.html',
      syncedAt: new Date().toISOString()
    }, null, 2)}\n`);

    console.log(`[file-viewer] 已同步 ${packageName}@${packed.version} 到 public/file-viewer`);
  } finally {
    await rm(tempDir, { recursive: true, force: true });
  }
}

await syncViewer();
