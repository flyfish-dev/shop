import { createReadStream } from 'node:fs';
import { stat } from 'node:fs/promises';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const distRoot = path.join(projectRoot, 'dist');
const shellRoot = path.join(distRoot, 'shell');
const staticRoot = path.join(distRoot, 'static');
const ports = {
  shell: Number(process.env.FLYFISH_SHELL_PORT || 4173),
  staticCn: Number(process.env.FLYFISH_STATIC_CN_PORT || 4174),
  staticGlobal: Number(process.env.FLYFISH_STATIC_GLOBAL_PORT || 4175)
};

const mimeTypes = new Map([
  ['.css', 'text/css; charset=utf-8'],
  ['.html', 'text/html; charset=utf-8'],
  ['.ico', 'image/x-icon'],
  ['.jpg', 'image/jpeg'],
  ['.jpeg', 'image/jpeg'],
  ['.js', 'application/javascript; charset=utf-8'],
  ['.json', 'application/json; charset=utf-8'],
  ['.mjs', 'application/javascript; charset=utf-8'],
  ['.pdf', 'application/pdf'],
  ['.png', 'image/png'],
  ['.svg', 'image/svg+xml'],
  ['.ttf', 'font/ttf'],
  ['.wasm', 'application/wasm'],
  ['.webp', 'image/webp'],
  ['.woff', 'font/woff'],
  ['.woff2', 'font/woff2']
]);

function safeFile(root, requestPath) {
  const pathname = decodeURIComponent(new URL(requestPath, 'http://localhost').pathname);
  const target = path.resolve(root, `.${pathname}`);
  return target === root || target.startsWith(`${root}${path.sep}`) ? target : '';
}

async function sendFile(response, file, { cors = false, immutable = false } = {}) {
  try {
    const info = await stat(file);
    if (!info.isFile()) {
      return false;
    }
    response.statusCode = 200;
    response.setHeader('Content-Type', mimeTypes.get(path.extname(file).toLowerCase()) || 'application/octet-stream');
    response.setHeader('Content-Length', info.size);
    response.setHeader('Cache-Control', immutable ? 'public, max-age=31536000, immutable' : 'no-store');
    if (cors) {
      response.setHeader('Access-Control-Allow-Origin', '*');
      response.setHeader('Cross-Origin-Resource-Policy', 'cross-origin');
      response.setHeader('X-Content-Type-Options', 'nosniff');
    }
    if (response.req.method === 'HEAD') {
      response.end();
    } else {
      createReadStream(file).pipe(response);
    }
    return true;
  } catch {
    return false;
  }
}

function startStaticServer(port, delayMs = 0) {
  const server = http.createServer(async (request, response) => {
    if (!['GET', 'HEAD'].includes(request.method)) {
      response.writeHead(405).end();
      return;
    }
    if (delayMs > 0 && request.url.includes('bootstrap-manifest.json')) {
      await new Promise(resolve => setTimeout(resolve, delayMs));
    }
    const file = safeFile(staticRoot, request.url);
    if (!file || !await sendFile(response, file, { cors: true, immutable: true })) {
      response.writeHead(404).end();
    }
  });
  server.listen(port, '127.0.0.1');
  return server;
}

const shellServer = http.createServer(async (request, response) => {
  if (!['GET', 'HEAD'].includes(request.method)) {
    response.writeHead(405).end();
    return;
  }
  if (request.url === '/sample-contract.pdf' && process.env.FLYFISH_SAMPLE_CONTRACT) {
    if (await sendFile(response, path.resolve(process.env.FLYFISH_SAMPLE_CONTRACT))) {
      return;
    }
  }
  const requested = safeFile(shellRoot, request.url);
  if (requested && await sendFile(response, requested)) {
    return;
  }
  await sendFile(response, path.join(shellRoot, 'index.html'));
});

const servers = [
  shellServer,
  startStaticServer(ports.staticCn, Number(process.env.FLYFISH_STATIC_CN_DELAY_MS || 0)),
  startStaticServer(ports.staticGlobal, Number(process.env.FLYFISH_STATIC_GLOBAL_DELAY_MS || 0))
];
shellServer.listen(ports.shell, '127.0.0.1');

console.log(`Flyfish shell: http://127.0.0.1:${ports.shell}`);
console.log(`Flyfish static CN: http://127.0.0.1:${ports.staticCn}`);
console.log(`Flyfish static global: http://127.0.0.1:${ports.staticGlobal}`);

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    servers.forEach(server => server.close());
  });
}
