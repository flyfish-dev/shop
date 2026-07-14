import { execFile } from 'node:child_process';
import { access, cp, mkdir, mkdtemp, readFile, realpath, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';

const execFileAsync = promisify(execFile);

const webPackageName = '@file-viewer/web';
const corePackageName = '@file-viewer/core';
const rendererPackageNames = [
  '@file-viewer/renderer-word',
  '@file-viewer/renderer-pdf',
  '@file-viewer/renderer-spreadsheet'
];
const webPackageVersion = process.env.FLYFISH_FILE_VIEWER_WEB_VERSION || 'latest';
const rendererPackageVersion = process.env.FLYFISH_FILE_VIEWER_RENDERER_VERSION || process.env.FLYFISH_FILE_VIEWER_VERSION || 'latest';
const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const publicViewerRoot = path.join(projectRoot, 'public', 'file-viewer');
const viteBin = path.join(projectRoot, 'node_modules', 'vite', 'bin', 'vite.js');

const packageSpecs = [
  `${webPackageName}@${webPackageVersion}`,
  `${corePackageName}@${rendererPackageVersion}`,
  ...rendererPackageNames.map(name => `${name}@${rendererPackageVersion}`)
];

function fail(message, cause) {
  console.error(`[file-viewer] ${message}`);
  if (cause) {
    console.error(cause);
  }
  process.exit(1);
}

async function run(command, args, options = {}) {
  const { fatal = true, ...execOptions } = options;
  try {
    return await execFileAsync(command, args, {
      cwd: projectRoot,
      maxBuffer: 1024 * 1024 * 16,
      ...execOptions
    });
  } catch (error) {
    const detail = error.stderr || error.message;
    if (fatal) {
      fail(`${command} ${args.join(' ')} 执行失败`, detail);
    }
    throw new Error(detail);
  }
}

async function pathExists(target) {
  try {
    await access(target);
    return true;
  } catch {
    return false;
  }
}

function moduleRoot(tempDir, packageName) {
  return path.join(tempDir, 'node_modules', ...packageName.split('/'));
}

async function readPackageMetadata(tempDir, packageName) {
  const packageJson = path.join(moduleRoot(tempDir, packageName), 'package.json');
  try {
    return JSON.parse(await readFile(packageJson, 'utf8'));
  } catch (error) {
    fail(`读取 ${packageName} package.json 失败`, error.message);
  }
}

async function copyRequired(source, target, label) {
  if (!await pathExists(source)) {
    fail(`${label} 不存在`, source);
  }
  await mkdir(path.dirname(target), { recursive: true });
  await cp(source, target, { recursive: true });
}

async function copyOptional(source, target) {
  if (!await pathExists(source)) {
    return false;
  }
  await mkdir(path.dirname(target), { recursive: true });
  await cp(source, target, { recursive: true });
  return true;
}

async function installViewerPackages(tempDir) {
  await writeFile(path.join(tempDir, 'package.json'), `${JSON.stringify({
    private: true,
    type: 'module'
  }, null, 2)}\n`);

  await run('npm', [
    'install',
    '--prefix',
    tempDir,
    '--no-save',
    '--omit=dev',
    '--no-audit',
    '--no-fund',
    '--package-lock=false',
    ...packageSpecs
  ]);
}

async function writeViewerSource(tempDir) {
  const sourceDir = path.join(tempDir, 'src');
  const bundleDir = path.join(tempDir, 'bundle');
  await mkdir(sourceDir, { recursive: true });
  await writeFile(path.join(tempDir, 'index.html'), `<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <meta name="theme-color" content="#eef3f7" />
    <title>合同预览</title>
    <style>
      :root {
        color-scheme: light;
        background: #eef3f7;
      }

      html,
      body,
      #viewer {
        width: 100%;
        height: 100%;
        margin: 0;
      }

      body {
        overflow: hidden;
        color: #172033;
        background: #eef3f7;
        font: 14px/1.6 system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      }

      #viewer {
        min-width: 0;
        min-height: 0;
      }

      .viewer-status {
        position: fixed;
        inset: 0;
        display: grid;
        place-items: center;
        padding: 24px;
        color: #607268;
        background: #eef3f7;
        box-sizing: border-box;
        text-align: center;
      }

      .viewer-status[hidden] {
        display: none;
      }

      .viewer-status strong {
        display: block;
        margin-bottom: 6px;
        color: #203626;
        font-size: 15px;
      }
    </style>
  </head>
  <body>
    <main id="viewer" aria-label="合同预览"></main>
    <div id="viewer-status" class="viewer-status" role="status">
      <span><strong>正在加载合同</strong>请稍候</span>
    </div>
    <script type="module" src="/src/contract-viewer.js"></script>
  </body>
</html>
`);

  await writeFile(path.join(sourceDir, 'contract-viewer.js'), `import { mountViewer } from '@file-viewer/web';
import {
  renderFileViewerWordDoc,
  renderFileViewerWordDocx,
  wordRendererDefinitions
} from '@file-viewer/renderer-word';
import { pdfRenderer } from '@file-viewer/renderer-pdf';
import { spreadsheetRenderer } from '@file-viewer/renderer-spreadsheet';

const contractWordRendererIds = new Set(['office-word-openxml', 'office-word-binary']);
const contractWordRenderer = {
  id: 'file-viewer-renderer-contract-word',
  label: 'Flyfish contract Word renderer',
  definitions: wordRendererDefinitions.filter(definition => contractWordRendererIds.has(definition.id)),
  handlers: [
    {
      rendererId: 'office-word-openxml',
      handler: renderFileViewerWordDocx
    },
    {
      rendererId: 'office-word-binary',
      handler: renderFileViewerWordDoc
    }
  ]
};
const rendererPlugins = [contractWordRenderer, pdfRenderer, spreadsheetRenderer];
const statusEl = document.getElementById('viewer-status');
const viewerEl = document.getElementById('viewer');

const params = new URLSearchParams(window.location.search);

const getParam = (...names) => {
  for (const name of names) {
    const value = params.get(name);
    if (value !== null && value !== '') {
      return value;
    }
  }
  return '';
};

const parseJson = value => {
  if (!value) {
    return {};
  }
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
  } catch {
    return {};
  }
};

const inferFilenameFromUrl = value => {
  if (!value) {
    return '';
  }
  try {
    const url = new URL(value, window.location.href);
    const tail = url.pathname.split('/').filter(Boolean).pop() || '';
    return decodeURIComponent(tail);
  } catch {
    const tail = String(value).split('?')[0].split('#')[0].split('/').filter(Boolean).pop() || '';
    try {
      return decodeURIComponent(tail);
    } catch {
      return tail;
    }
  }
};

const inferTypeFromFilename = filename => {
  const match = String(filename || '').match(/\\.([a-z0-9]+)$/i);
  return match ? match[1].toLowerCase() : '';
};

const toPositiveNumber = value => {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? number : undefined;
};

const postParentEvent = (type, detail = {}) => {
  if (window.parent === window) {
    return;
  }
  try {
    window.parent.postMessage({
      source: 'flyfish-contract-viewer',
      type,
      detail
    }, window.location.origin);
  } catch {
    // Parent notifications are best-effort only.
  }
};

const setStatus = (title, description) => {
  if (!statusEl) {
    return;
  }
  if (!title && !description) {
    statusEl.hidden = true;
    return;
  }
  statusEl.hidden = false;
  statusEl.innerHTML = '';
  const wrapper = document.createElement('span');
  const strong = document.createElement('strong');
  strong.textContent = title || '合同预览';
  wrapper.appendChild(strong);
  if (description) {
    wrapper.appendChild(document.createTextNode(description));
  }
  statusEl.appendChild(wrapper);
};

const url = getParam('url', 'src');
const filename = getParam('filename', 'name') || inferFilenameFromUrl(url);
const explicitType = getParam('type', 'fileType', 'ext').replace(/^\\./, '').toLowerCase();
const type = explicitType || inferTypeFromFilename(filename);
const size = toPositiveNumber(getParam('size', 'fileSize'));
const incomingOptions = parseJson(getParam('options'));
const incomingToolbar = incomingOptions.toolbar;
const toolbar = incomingToolbar === false
  ? false
  : {
      download: true,
      print: true,
      exportHtml: false,
      ...(incomingToolbar && typeof incomingToolbar === 'object' && !Array.isArray(incomingToolbar) ? incomingToolbar : {})
    };

const options = {
  ...incomingOptions,
  theme: incomingOptions.theme || 'light',
  rendererMode: 'replace',
  builtinRenderers: 'none',
  autoRenderers: false,
  renderers: rendererPlugins,
  toolbar,
  pdf: {
    workerUrl: '/file-viewer/vendor/pdf/pdf.worker.mjs',
    cMapUrl: '/file-viewer/vendor/pdf/cmaps/',
    wasmUrl: '/file-viewer/vendor/pdf/wasm/',
    standardFontDataUrl: '/file-viewer/vendor/pdf/standard_fonts/',
    ...(incomingOptions.pdf && typeof incomingOptions.pdf === 'object' && !Array.isArray(incomingOptions.pdf) ? incomingOptions.pdf : {})
  },
  docx: {
    ...(incomingOptions.docx && typeof incomingOptions.docx === 'object' && !Array.isArray(incomingOptions.docx) ? incomingOptions.docx : {}),
    worker: false
  },
  spreadsheet: {
    ...(incomingOptions.spreadsheet && typeof incomingOptions.spreadsheet === 'object' && !Array.isArray(incomingOptions.spreadsheet) ? incomingOptions.spreadsheet : {}),
    worker: false
  }
};

if (!url) {
  setStatus('缺少合同文件地址', '请刷新页面后重试。');
} else {
  try {
    mountViewer(viewerEl, {
      url,
      filename,
      name: filename,
      type,
      size,
      options,
      onEvent(event) {
        if (event?.type === 'load-complete') {
          setStatus('', '');
        }
        if (event?.type === 'load-start' || event?.type === 'load-complete' || event?.type === 'load-error') {
          postParentEvent(event.type, {
            filename: event.payload?.filename || filename,
            type: event.payload?.type || type
          });
        }
      },
      onStateChange(state) {
        if (state?.error) {
          const message = state.error instanceof Error ? state.error.message : String(state.error);
          setStatus('合同预览失败', message || '请稍后重试。');
          postParentEvent('load-error', { filename, type });
        }
      }
    });
  } catch (error) {
    setStatus('合同预览失败', error instanceof Error ? error.message : '请稍后重试。');
    postParentEvent('load-error', { filename, type });
  }
}
`);

  await writeFile(path.join(tempDir, 'vite.config.mjs'), `export default {
  root: ${JSON.stringify(tempDir)},
  base: './',
  publicDir: false,
  build: {
    outDir: ${JSON.stringify(bundleDir)},
    emptyOutDir: true,
    sourcemap: false,
    minify: 'esbuild',
    target: 'es2020',
    rollupOptions: {
      external: ['pdfjs-dist/legacy/build/pdf.worker.mjs'],
      input: 'index.html',
      output: {
        entryFileNames: 'assets/[name]-[hash].js',
        chunkFileNames: 'assets/[name]-[hash].js',
        assetFileNames: 'assets/[name]-[hash][extname]'
      }
    }
  },
  resolve: {
    dedupe: ['@file-viewer/core']
  }
};
`);
}

async function buildViewerBundle(tempDir) {
  await run('node', [viteBin, 'build', '--config', path.join(tempDir, 'vite.config.mjs')], { cwd: tempDir });
}

async function copyPdfAssets(tempDir) {
  const pdfRoot = moduleRoot(tempDir, 'pdfjs-dist');
  const publicPdfRoot = path.join(publicViewerRoot, 'vendor', 'pdf');

  await copyRequired(
    path.join(pdfRoot, 'legacy', 'build', 'pdf.worker.mjs'),
    path.join(publicPdfRoot, 'pdf.worker.mjs'),
    'PDF.js worker'
  );
  await copyRequired(
    path.join(pdfRoot, 'cmaps'),
    path.join(publicPdfRoot, 'cmaps'),
    'PDF.js cMaps'
  );
  await copyOptional(
    path.join(pdfRoot, 'wasm'),
    path.join(publicPdfRoot, 'wasm')
  );
  await copyRequired(
    path.join(pdfRoot, 'standard_fonts'),
    path.join(publicPdfRoot, 'standard_fonts'),
    'PDF.js standard fonts'
  );
}

async function writeHeaders() {
  await writeFile(path.join(publicViewerRoot, '_headers'), `/*
  X-Content-Type-Options: nosniff
  Referrer-Policy: strict-origin-when-cross-origin

/*.html
  Cache-Control: public, max-age=0, must-revalidate

/assets/*
  Cache-Control: public, max-age=31536000, immutable

/vendor/*
  Cache-Control: public, max-age=31536000, immutable

/vendor/pdf/wasm/*.wasm
  Content-Type: application/wasm
`);
}

async function writeManifest(tempDir) {
  const packages = {};
  for (const packageName of [webPackageName, corePackageName, ...rendererPackageNames]) {
    const metadata = await readPackageMetadata(tempDir, packageName);
    packages[packageName] = metadata.version;
  }
  const pdfjsMetadata = await readPackageMetadata(tempDir, 'pdfjs-dist');

  await writeFile(path.join(publicViewerRoot, 'flyfish-viewer-manifest.json'), `${JSON.stringify({
    name: 'flyfish-contract-file-viewer',
    entry: 'index.html',
    packages,
    pdfjs: pdfjsMetadata.version,
    rendererMode: 'replace',
    renderers: rendererPackageNames,
    supportedContractExtensions: ['doc', 'docx', 'pdf', 'xls', 'xlsx'],
    notes: [
      'Contract preview intentionally bundles only DOC/DOCX, PDF, and XLS/XLSX renderer paths.',
      'DOCX and Spreadsheet workers are disabled to keep the deployed viewer assets minimal.'
    ],
    syncedAt: new Date().toISOString()
  }, null, 2)}\n`);
}

async function syncViewer() {
  const tempDir = await realpath(await mkdtemp(path.join(os.tmpdir(), 'flyfish-file-viewer-')));
  try {
    await installViewerPackages(tempDir);
    await writeViewerSource(tempDir);
    await buildViewerBundle(tempDir);

    await rm(publicViewerRoot, { recursive: true, force: true });
    await mkdir(publicViewerRoot, { recursive: true });
    await cp(path.join(tempDir, 'bundle'), publicViewerRoot, { recursive: true });
    await copyPdfAssets(tempDir);
    await writeHeaders();
    await writeManifest(tempDir);

    console.log(`[file-viewer] 已同步合同预览 viewer 到 public/file-viewer`);
    console.log(`[file-viewer] 组件: ${packageSpecs.join(', ')}`);
  } finally {
    await rm(tempDir, { recursive: true, force: true });
  }
}

await syncViewer();
