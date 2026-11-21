// Usage: NODE_PATH=/opt/node-tools/node_modules node render-svg.js <in.svg> <out.png> <size> [bg]
const { chromium } = require('playwright');
const fs = require('fs');
(async () => {
  const jobs = JSON.parse(process.argv[2]); // [[in, out, w, h, bg|null], ...]
  const b = await chromium.launch();
  for (const [inp, out, w, h, bg] of jobs) {
    const c = await b.newContext({ viewport: { width: w, height: h }, deviceScaleFactor: 1 });
    const p = await c.newPage();
    const svg = fs.readFileSync(inp, 'utf8').replace(/<svg ([^>]*?)width="[\d.]+" height="[\d.]+"/, `<svg $1width="${w}" height="${h}"`);
    await p.setContent(`<body style="margin:0;background:${bg || 'transparent'}">${svg}</body>`);
    await p.screenshot({ path: out, omitBackground: !bg, clip: { x: 0, y: 0, width: w, height: h } });
    await c.close();
  }
  await b.close();
})();
