// Renders every NN-*.html to ../images/NN-*.png (2x) and the icon (00-icon.svg -> png 512/256/64).
// Run: NODE_PATH=<node_modules with playwright> node render.js
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
(async () => {
  const dir = __dirname, out = path.join(dir, '..', 'images');
  const b = await chromium.launch();
  const ctx = await b.newContext({ viewport: { width: 1600, height: 400 }, deviceScaleFactor: 2 });
  for (const f of fs.readdirSync(dir).filter(x => /^\d\d-.*\.html$/.test(x)).sort()) {
    const p = await ctx.newPage();
    await p.goto('file://' + path.join(dir, f));
    await p.waitForTimeout(150);
    const h = await p.evaluate(() => document.querySelector('.frame').getBoundingClientRect().height);
    await p.setViewportSize({ width: 1600, height: Math.ceil(h) });
    await p.screenshot({ path: path.join(out, f.replace('.html', '.png')), clip: { x: 0, y: 0, width: 1600, height: Math.ceil(h) } });
    console.log(f, Math.ceil(h));
    await p.close();
  }
  const svg = fs.readFileSync(path.join(dir, 'icon.svg'), 'utf8');
  fs.copyFileSync(path.join(dir, 'icon.svg'), path.join(out, '00-icon.svg'));
  for (const s of [512, 256, 64]) {
    const c = await b.newContext({ viewport: { width: s, height: s }, deviceScaleFactor: 1 });
    const p = await c.newPage();
    await p.setContent(`<body style="margin:0">${svg.replace('width="512" height="512"', `width="${s}" height="${s}"`)}</body>`);
    await p.screenshot({ path: path.join(out, s === 512 ? '00-icon.png' : `00-icon-${s}.png`), clip: { x: 0, y: 0, width: s, height: s } });
    await c.close();
  }
  await b.close();
})();
