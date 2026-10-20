import { createServer } from 'vite';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('..', import.meta.url));
const publicPaths = /^\/shops\/(?:items(?:\/\d+)?|item-groups|current|pricing|payment-methods)$/;
const server = await createServer({
  root,
  configFile: `${root}/vite.config.js`,
  plugins: [{
    name: 'read-only-catalog-preview',
    configureServer(vite) {
      vite.middlewares.use(async (req, res, next) => {
        const url = new URL(req.url, 'http://localhost');
        const json = (status, body) => {
          res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
          res.end(JSON.stringify(body));
        };
        if (url.pathname === '/portal/users/current') return json(200, { success: true, result: null });
        if (url.pathname === '/__shop/portal/capabilities') return json(200, { success: true, result: [{ code: 'shop', path: '/', name: 'Flyfish Store' }] });
        const publicRead = publicPaths.test(url.pathname) || url.pathname.startsWith('/images/20');
        const backendPath = /^\/(?:shops|portal|oauth|wx|email|__shop|__lowcode)(?:\/|$)/.test(url.pathname);
        if (!publicRead && !backendPath) return next();
        // This preview never forwards authentication, writes, payment or private files.
        if (!publicRead || req.method !== 'GET') return json(403, { success: false, message: 'Read-only design preview. Use npm run dev with local services for account and checkout testing.' });
        try {
          const response = await fetch(`https://dev.flyfish.group${url.pathname}${url.search}`, {
            headers: { 'Accept-Language': req.headers['accept-language'] || 'zh-CN' },
            signal: AbortSignal.timeout(15000), redirect: 'error'
          });
          res.writeHead(response.status, {
            'Content-Type': response.headers.get('content-type') || 'application/octet-stream',
            'Cache-Control': 'no-store'
          });
          res.end(Buffer.from(await response.arrayBuffer()));
        } catch {
          json(502, { success: false, message: 'Public catalog preview is temporarily unavailable.' });
        }
      });
    }
  }],
  server: { port: 9999, host: '127.0.0.1' }
});
await server.listen();
server.printUrls();
console.log('Read-only public catalog preview; production writes and private endpoints are blocked.');
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, async () => { await server.close(); process.exit(0); });
