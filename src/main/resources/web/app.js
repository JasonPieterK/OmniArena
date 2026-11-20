/* OmniArena Control — MMM-styled dashboard, talks to Java WebDashboardServer */
(function () {
  'use strict';

  // Token comes from the /oa web link (?token=...). Keep it in sessionStorage and strip it
  // from the address bar so it does not linger in history / screenshots.
  function initToken() {
    let t = new URLSearchParams(location.search).get('token') || '';
    try {
      if (t) {
        sessionStorage.setItem('omniarena-token', t);
        history.replaceState(null, '', location.pathname);
      } else {
        t = sessionStorage.getItem('omniarena-token') || '';
      }
    } catch (e) { /* storage blocked: token lives in memory only */ }
    return t;
  }

  const S = {
    token: initToken(),
    liveByArena: {},
    busy: false,
    expired: false,
    snapshot: null,
    selectedArena: null,
    draftGlobals: {},
    draftArena: {},
    docsRaw: '',
    dirty: false,
    cfgQuery: '',
  };

  const $ = (id) => document.getElementById(id);
  const esc = (v) => String(v ?? '')
    .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');

  // ── API ──────────────────────────────────────────────────────────────
  async function api(path, opts = {}) {
    let res;
    try {
      res = await fetch(path, {
        ...opts,
        headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + S.token, ...(opts.headers || {}) },
        cache: 'no-store',
      });
    } catch (e) {
      throw new Error('Cannot connect to server. Make sure the web dashboard is running (/oa plugin start).');
    }
    const txt = await res.text();
    if (!res.ok) {
      let msg = '';
      try { const j = JSON.parse(txt); msg = j.error || (Array.isArray(j.errors) ? j.errors.join('; ') : ''); } catch (e) { msg = txt; }
      const err = new Error(msg || `HTTP ${res.status}`);
      err.status = res.status;
      if (res.status === 401) sessionExpired();
      throw err;
    }
    if (opts.text) return txt;
    try { return txt ? JSON.parse(txt) : {}; } catch (e) { throw new Error('Server sent an unreadable response.'); }
  }

  function sessionExpired() {
    if (S.expired) return;
    S.expired = true;
    stopLivePolling();
    setStatus(false, 'Session expired');
    $('applyBtn').disabled = true;
    toast('Session expired or logged out. Run /oa web in-game for a new link.', 'error');
  }

  // ── Toast + confetti ───────────────────────────────────────────────────
  function toast(msg, type = 'success') {
    const t = document.createElement('div');
    t.className = `toast ${type}`;
    t.innerHTML = `<span class="toast-ico">${type === 'success' ? '✓' : '✕'}</span><span>${esc(msg)}</span>`;
    $('toastContainer').appendChild(t);
    setTimeout(() => { t.style.opacity = '0'; t.style.transform = 'translateX(40px)'; setTimeout(() => t.remove(), 250); }, type === 'error' ? 6000 : 3000);
  }

  function confettiBurst() {
    const wrap = $('confettiWrap');
    const colors = ['var(--mean)','var(--median)','var(--mode)','var(--sun)','var(--coral)','var(--plum)'];
    for (let i = 0; i < 34; i++) {
      const c = document.createElement('span');
      c.className = 'confetti';
      const size = 8 + Math.random() * 9;
      const sq = i % 3 === 0;
      const startX = 40 + Math.random() * 20;
      c.style.cssText = `left:${startX}%;top:108%;width:${size}px;height:${size}px;background:${colors[i % colors.length]};border-radius:${sq ? '2px' : '50%'};`;
      const dx = (Math.random() - 0.5) * 60;
      const dy = -(60 + Math.random() * 50);
      const rot = Math.random() * 720 - 360;
      c.animate([
        { transform: 'translate(0,0) rotate(0)', opacity: 1 },
        { transform: `translate(${dx}vw, ${dy}vh) rotate(${rot}deg)`, opacity: 0 },
      ], { duration: 1100 + Math.random() * 400, easing: 'cubic-bezier(0.2,0.8,0.2,1)' });
      wrap.appendChild(c);
      setTimeout(() => c.remove(), 1600);
    }
  }

  function countUp(el, target, dur = 800) {
    const start = performance.now();
    function tick(now) {
      const p = Math.min(1, (now - start) / dur);
      const eased = 1 - Math.pow(1 - p, 3);
      el.textContent = Math.round(target * eased);
      if (p < 1) requestAnimationFrame(tick);
    }
    requestAnimationFrame(tick);
  }

  function setStatus(ok, text) {
    $('statusDot').className = 'status-dot ' + (ok ? 'ok' : 'err');
    $('sessionText').textContent = text;
  }

  function markDirty() { S.dirty = true; $('applyBtn').disabled = false; }

  // ── Tabs ─────────────────────────────────────────────────────────────
  function initTabs() {
    document.querySelectorAll('.nav-link').forEach(link => {
      link.addEventListener('click', () => {
        const tab = link.dataset.tab;
        document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
        link.classList.add('active');
        document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
        $('tab-' + tab).classList.add('active');
        stopLivePolling();
        if (tab === 'docs' && !S.docsRaw) loadDocs();
        if (tab === 'live') loadLiveMatches();
        if (tab === 'players') loadPlayers();
        window.scrollTo({ top: 0, behavior: 'smooth' });
      });
    });
  }

  // ── Load ─────────────────────────────────────────────────────────────
  async function load() {
    if (!S.token) { setStatus(false, 'No token - run /oa web'); $('statGrid').innerHTML = ''; $('arenaGrid').innerHTML = emptyState('ph-key', 'Not logged in', 'Run /oa web in-game and open the link it prints.'); return; }
    if (S.expired) return;
    setStatus(false, 'Loading…');
    $('arenaGrid').innerHTML = $('arenaGrid').innerHTML || '<div class="empty"><i class="ph-bold ph-circle-notch"></i><p>Loading…</p></div>';
    try {
      const [snapshot, live] = await Promise.all([
        api('/api/state'),
        api('/api/live-matches').catch(() => []),
      ]);
      S.snapshot = snapshot;
      S.snapshot.arenas = S.snapshot.arenas || [];
      S.snapshot.globals = S.snapshot.globals || {};
      S.snapshot.settings = S.snapshot.settings || [];
      S.liveByArena = {};
      (Array.isArray(live) ? live : []).forEach(m => { S.liveByArena[String(m.arena).toLowerCase()] = m; });
      S.draftGlobals = { ...S.snapshot.globals };
      if (S.selectedArena && !S.snapshot.arenas.some(a => a.name === S.selectedArena)) S.selectedArena = null;
      if (!S.selectedArena && S.snapshot.arenas.length)
        S.selectedArena = S.snapshot.arenas[0].name;
      const arena = S.snapshot.arenas.find(a => a.name === S.selectedArena);
      S.draftArena = arena ? cloneArena(arena) : {};
      S.dirty = false; $('applyBtn').disabled = true;
      setStatus(true, `${S.snapshot.arenas.length} arena${S.snapshot.arenas.length === 1 ? '' : 's'}`);
      renderAll();
    } catch (e) {
      if (e.status === 401) return;
      const msg = e.message || 'Unknown error';
      setStatus(false, msg.includes('Cannot connect') ? 'Server offline' : 'Error');
      toast('Failed to load: ' + msg, 'error');
      if (!S.snapshot) $('arenaGrid').innerHTML = emptyState('ph-warning-circle', 'Could not load', msg);
    }
  }

  async function loadDocs() {
    try {
      S.docsRaw = await api('/api/docs', { text: true });
      renderDocs();
    } catch (e) {
      $('docsBody').innerHTML = '<p>Couldn\'t load docs: ' + esc(e.message) + '</p>';
    }
  }

  function renderAll() {
    renderStats();
    renderDashboardGrid();
    renderArenaList();
    renderArenaEditor();
    renderConfig();
  }

  // ArenaView.state is the Java ArenaState name (WAITING, STARTING, RUNNING, ...).
  function stateOf(a) {
    if (a.enabled === false) return 'disabled';
    return String(a.state || 'waiting').toLowerCase();
  }
  function arenaPlayers(a) {
    const m = S.liveByArena[String(a.name).toLowerCase()];
    return m ? (m.totalPlayers ?? 0) : 0;
  }

  // ── Stats ────────────────────────────────────────────────────────────
  function renderStats() {
    const sn = S.snapshot;
    const arenas = sn?.arenas || [];
    const live = arenas.filter(a => stateOf(a) === 'running').length;
    const players = arenas.reduce((n, a) => n + arenaPlayers(a), 0);
    const cfgCount = sn ? Object.keys(sn.globals || {}).length : 0;
    const cards = [
      { cls: 's-mean',   label: 'Arenas',      val: arenas.length, foot: 'configured' },
      { cls: 's-median', label: 'Live now',    val: live,          foot: 'matches running' },
      { cls: 's-mode',   label: 'Players',     val: players,       foot: 'in arenas' },
      { cls: 's-sun',    label: 'Settings',    val: cfgCount,      foot: 'config keys' },
    ];
    $('statGrid').innerHTML = cards.map(c => `
      <div class="stat ${esc(c.cls)}">
        <div class="eyebrow">${esc(c.label)}</div>
        <div class="num" data-count="${esc(c.val)}">0</div>
        <div class="foot">${esc(c.foot)}</div>
      </div>`).join('');
    $('statGrid').querySelectorAll('.num').forEach(el => countUp(el, Number(el.dataset.count) || 0));
  }

  // ── Dashboard grid ───────────────────────────────────────────────────
  function renderDashboardGrid() {
    const sn = S.snapshot;
    const grid = $('arenaGrid');
    const arenas = sn?.arenas || [];
    if (!arenas.length) {
      grid.innerHTML = emptyState('ph-stack', 'No arenas yet', 'Create one in the Arenas tab.');
      return;
    }
    grid.innerHTML = arenas.map(a => {
      const enabled = a.enabled !== false;
      const state = stateOf(a);
      const badgeCls = { running:'badge-live', starting:'badge-starting', waiting:'badge-waiting', ending:'badge-starting', resetting:'badge-starting', disabled:'badge-disabled', maintenance:'badge-disabled' }[state] || 'badge-idle';
      const badgeLabel = { running:'LIVE', starting:'STARTING', waiting:'WAITING', ending:'ENDING', resetting:'RESETTING', disabled:'OFF', maintenance:'MAINT' }[state] || state.toUpperCase();
      const dot = state === 'running' ? '<span class="live-dot"></span>' : '';
      const cardCls = state === 'running' ? 'arena-card live' : state === 'starting' ? 'arena-card starting' : 'arena-card';
      const players = arenaPlayers(a);
      const maxP = a.maxPlayers ?? '?';
      const pct = (a.maxPlayers ? Math.round(players / a.maxPlayers * 100) : 0);
      return `<div class="${esc(cardCls)}">
        <div class="arena-card-top">
          <div>
            <div class="arena-card-name">${esc(a.name)}</div>
            <div class="arena-card-world">${esc(a.regionWorld || '—')}</div>
          </div>
          <span class="badge ${esc(badgeCls)}">${dot}${esc(badgeLabel)}</span>
        </div>
        <div class="arena-card-info">
          <span><i class="ph-bold ph-users"></i> ${esc(players)}/${esc(maxP)}</span>
          <span><i class="ph-bold ph-game-controller"></i> ${esc(a.gameMode || '—')}</span>
        </div>
        <div class="pbar"><div style="width:${Number(pct) || 0}%"></div></div>
      </div>`;
    }).join('');
  }

  // ── Arena list ───────────────────────────────────────────────────────
  function renderArenaList() {
    const sn = S.snapshot;
    const list = $('arenaList');
    const arenas = sn?.arenas || [];
    if (!arenas.length) { list.innerHTML = '<div class="card">No arenas yet. Click <b>New arena</b> to create one.</div>'; return; }
    list.innerHTML = arenas.map(a => {
      const sel = a.name === S.selectedArena ? ' selected' : '';
      return `<div class="arena-list-item${sel}" data-arena="${esc(a.name)}">
        <div class="arena-list-item-name">${esc(a.name)}</div>
        <div class="arena-list-item-meta">
          <span>${esc(a.gameMode || '—')}</span>
          <span>${a.enabled !== false ? '● on' : '○ off'}</span>
        </div>
      </div>`;
    }).join('');
    list.querySelectorAll('.arena-list-item').forEach(item => {
      item.addEventListener('click', () => {
        S.selectedArena = item.dataset.arena;
        const arena = S.snapshot.arenas.find(a => a.name === S.selectedArena);
        S.draftArena = arena ? cloneArena(arena) : {};
        renderArenaList();
        renderArenaEditor();
      });
    });
  }

  // ── Arena editor ─────────────────────────────────────────────────────
  function renderArenaEditor() {
    const editor = $('arenaEditor');
    const arena = S.snapshot?.arenas.find(a => a.name === S.selectedArena);
    if (!arena) {
      editor.innerHTML = `<div class="card">${emptyState('ph-cursor-click', 'Pick an arena', 'Select one from the list to edit it.')}</div>`;
      return;
    }
    const d = S.draftArena;

    editor.innerHTML = `
      <div class="editor-header">
        <div class="editor-title">${esc(arena.name)}</div>
        <div class="editor-actions">
          <button class="btn btn-danger btn-sm" id="deleteArenaBtn"><i class="ph-bold ph-trash"></i> Delete</button>
          <button class="btn btn-sm" id="saveArenaBtn"><i class="ph-bold ph-floppy-disk"></i> Save arena</button>
        </div>
      </div>

      ${section('General', 'ph-sliders', true, `
        <div class="form-grid">
          ${field('Game type', select('gameMode', d.gameMode, ['DEATHMATCH','TEAM_DEATHMATCH','LAST_ONE_STANDING','SUMO','CLASSIC_PVP','CRYSTAL_PVP','MACE','SWORD_DUEL','AXE_SHIELD','UHC','POTION_PVP','NODEBUFF','ARCHER','NETHERITE_OP','SPLEEF','GLADIATOR']))}
          ${field('Min players', numInput('minPlayers', d.minPlayers))}
          ${field('Max players', numInput('maxPlayers', d.maxPlayers))}
          ${field('Match duration (s)', numInput('matchDuration', d.matchDuration))}
          ${field('Countdown (s)', numInput('countdown', d.countdown))}
          ${field('Respawn delay (s)', numInput('respawnDelay', d.respawnDelay))}
          ${field('Lives (0 = mode default)', numInput('lives', d.lives))}
          ${field('Wave respawn interval (s, WAVE only)', numInput('respawnWaveInterval', d.respawnWaveInterval))}
        </div>
        ${cfgToggle('enabled', 'Enabled', d.enabled)}
        ${cfgToggle('midMatchJoin', 'Mid-match join', d.midMatchJoin)}
        ${cfgToggle('spectatorAllowed', 'Spectators allowed', d.spectatorAllowed)}
        ${cfgToggle('whitelistEnabled', 'Whitelist', d.whitelistEnabled)}
      `)}

      ${section('Rules', 'ph-shield', false, `
        ${cfgToggle('rules.fallDamage', 'Fall damage', d.rules?.fallDamage)}
        ${cfgToggle('rules.fireDamage', 'Fire damage', d.rules?.fireDamage)}
        ${cfgToggle('rules.friendlyFire', 'Friendly fire', d.rules?.friendlyFire)}
        ${cfgToggle('rules.blockBreak', 'Block break', d.rules?.blockBreak)}
        ${cfgToggle('rules.blockPlace', 'Block place', d.rules?.blockPlace)}
        ${cfgToggle('rules.itemDrop', 'Item drop', d.rules?.itemDrop)}
        ${cfgToggle('rules.hungerDrain', 'Hunger drain', d.rules?.hungerDrain)}
      `)}

      ${section('Visual', 'ph-sparkle', false, `
        ${cfgToggle('visual.titles', 'Titles', d.visual?.titles)}
        ${cfgToggle('visual.sounds', 'Sounds', d.visual?.sounds)}
        ${cfgToggle('visual.particles', 'Particles', d.visual?.particles)}
        ${cfgToggle('visual.scoreboard', 'Scoreboard', d.visual?.scoreboard)}
        ${cfgToggle('visual.bossBar', 'Boss bar', d.visual?.bossBar)}
        ${cfgToggle('visual.actionBar', 'Action bar', d.visual?.actionBar)}
        ${cfgToggle('visual.holograms', 'Holograms', d.visual?.holograms)}
      `)}

      ${section('Region', 'ph-cube', false, `
        ${field('World', textInput('region.worldName', d.region?.worldName))}
        <div class="form-grid">
          ${field('Min X', numInput('region.minX', d.region?.minX))}
          ${field('Min Y', numInput('region.minY', d.region?.minY))}
          ${field('Min Z', numInput('region.minZ', d.region?.minZ))}
          ${field('Max X', numInput('region.maxX', d.region?.maxX))}
          ${field('Max Y', numInput('region.maxY', d.region?.maxY))}
          ${field('Max Z', numInput('region.maxZ', d.region?.maxZ))}
        </div>
      `)}
    `;

    wireSections(editor);
    editor.querySelectorAll('[data-key]').forEach(el => {
      el.addEventListener('change', () => {
        const key = el.dataset.key;
        const val = el.type === 'checkbox' ? el.checked : (el.type === 'number' ? (el.value === '' ? '' : Number(el.value)) : el.value);
        setNested(S.draftArena, key, val);
        markDirty();
      });
    });
    $('saveArenaBtn').addEventListener('click', saveArena);
    $('deleteArenaBtn').addEventListener('click', deleteArena);
  }

  // ── Config (categorized + hover descriptions) ──────────────────────────
  function renderConfig() {
    const sn = S.snapshot;
    const container = $('configGroups');
    if (!sn) { container.innerHTML = ''; return; }

    // Group rich SettingDefinitions by their .group category.
    const settings = sn.settings || [];
    const byGroup = {};
    const q = S.cfgQuery.toLowerCase();
    settings.forEach(def => {
      if (def.key && def.key.startsWith('messages.')) return; // skip raw message strings
      if (q && !(def.label + ' ' + def.key).toLowerCase().includes(q)) return;
      (byGroup[def.group || 'General'] ||= []).push(def);
    });

    const groups = Object.keys(byGroup).sort();
    if (!groups.length) { container.innerHTML = `<div class="card">${emptyState('ph-magnifying-glass','No matches','Try a different search.')}</div>`; return; }

    const ICONS = {
      Config:'ph-gear', Arena:'ph-flag', Match:'ph-timer', Regen:'ph-arrows-clockwise',
      Web:'ph-globe', Modules:'ph-puzzle-piece', Integrations:'ph-plugs', Kits:'ph-sword',
      Visual:'ph-sparkle', Performance:'ph-gauge', Database:'ph-database', Rewards:'ph-coins',
      Killstreak:'ph-fire', Signs:'ph-sign-posts', Quality:'ph-heart', Plugin:'ph-cube',
      'Boss Bar':'ph-minus-square', Scoreboard:'ph-list-numbers',
    };

    container.innerHTML = groups.map((g, idx) => {
      const rows = byGroup[g].map(def => configRow(def)).join('');
      const icon = ICONS[g] || 'ph-gear';
      return sectionRaw(g, icon, idx < 2, byGroup[g].length, rows);
    }).join('');

    wireSections(container);
    container.querySelectorAll('[data-key]').forEach(el => {
      el.addEventListener('change', () => {
        const rawKey = el.dataset.key.replace(/^cfg\./, '');
        const val = el.type === 'checkbox' ? el.checked : (el.type === 'number' ? (el.value === '' ? '' : Number(el.value)) : el.value);
        S.draftGlobals[rawKey] = val;
        markDirty();
      });
    });
  }

  function configRow(def) {
    const val = S.draftGlobals[def.key];
    const type = (def.type || 'STRING').toUpperCase();
    const key = 'cfg.' + def.key;
    let control;
    if (type === 'BOOLEAN') {
      control = `<label class="toggle"><input type="checkbox" data-key="${esc(key)}"${val ? ' checked' : ''}><span class="slider"></span></label>`;
    } else if (type === 'INTEGER' || type === 'DECIMAL') {
      control = `<input class="input" type="number" step="${type === 'DECIMAL' ? 'any' : '1'}" data-key="${esc(key)}" value="${esc(val ?? '')}">`;
    } else {
      control = `<input class="input" type="text" data-key="${esc(key)}" value="${esc(val ?? '')}">`;
    }
    const tip = def.description ? `<span class="info">?<span class="tip">${esc(def.description)}</span></span>` : '';
    return `<div class="cfg-row">
      <div class="cfg-grow">
        <div class="field-label">${esc(def.label || def.key)} ${tip}</div>
        <div class="key-chip">${esc(def.key)}</div>
      </div>
      <div class="cfg-control">${control}</div>
    </div>`;
  }

  // ── Docs ─────────────────────────────────────────────────────────────
  function renderDocs() {
    const body = $('docsBody'), toc = $('docsToc');
    if (!S.docsRaw) { body.innerHTML = '<p>Docs not loaded.</p>'; return; }
    body.innerHTML = markdownToHtml(S.docsRaw);
    let tocHtml = '';
    body.querySelectorAll('h1,h2,h3').forEach((h, i) => {
      const id = 'doc-h-' + i; h.id = id;
      tocHtml += `<a href="#${id}" class="docs-toc-item ${h.tagName.toLowerCase()}">${esc(h.textContent)}</a>`;
    });
    toc.innerHTML = tocHtml;
  }

  // Build <table> blocks from GitHub pipe tables. Runs on already-escaped text,
  // emits each table as a single line so later passes leave it alone. Inline
  // **bold** / `code` inside cells is applied here.
  function mdTables(escaped) {
    const inline = (s) => s
      .replace(/`([^`]+)`/g, '<code>$1</code>')
      .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
    const lines = escaped.split('\n');
    const out = [];
    for (let i = 0; i < lines.length; i++) {
      const row = lines[i].trim();
      const next = (lines[i + 1] || '').trim();
      const isRow = (s) => s.startsWith('|') && s.endsWith('|');
      const isSep = (s) => /^\|[\s:|-]+\|$/.test(s);
      if (isRow(row) && isSep(next)) {
        const cells = (s) => s.slice(1, -1).split('|').map(c => c.trim());
        let html = '<table><thead><tr>' + cells(row).map(c => `<th>${inline(c)}</th>`).join('') + '</tr></thead><tbody>';
        i += 2;
        while (i < lines.length && isRow(lines[i].trim())) {
          html += '<tr>' + cells(lines[i].trim()).map(c => `<td>${inline(c)}</td>`).join('') + '</tr>';
          i++;
        }
        i--;
        out.push(html + '</tbody></table>');
      } else {
        out.push(lines[i]);
      }
    }
    return out.join('\n');
  }

  function markdownToHtml(md) {
    let html = md.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
    html = mdTables(html); // tables first, on escaped text
    return html
      .replace(/```[\w]*\n?([\s\S]*?)```/g, (_, c) => `<pre><code>${c}</code></pre>`)
      .replace(/`([^`]+)`/g, '<code>$1</code>')
      .replace(/^#### (.+)$/gm, '<h4>$1</h4>')
      .replace(/^### (.+)$/gm, '<h3>$1</h3>')
      .replace(/^## (.+)$/gm, '<h2>$1</h2>')
      .replace(/^# (.+)$/gm, '<h1>$1</h1>')
      .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
      .replace(/\*(.+?)\*/g, '<em>$1</em>')
      .replace(/^> (.+)$/gm, '<blockquote>$1</blockquote>')
      .replace(/^---+$/gm, '<hr>')
      .replace(/^[-*] (.+)$/gm, '<li>$1</li>')
      .replace(/(<li>[\s\S]+?<\/li>)/g, '<ul>$1</ul>')
      .replace(/^(\d+)\. (.+)$/gm, '<li>$2</li>')
      .replace(/^(?!<[hbupliot]|<pre|<hr|<blockquote)(.+)$/gm, (_, l) => l.trim() ? `<p>${l}</p>` : '');
  }

  // ── Apply / save / create / delete ─────────────────────────────────────
  // Runs fn once at a time; ignores clicks while another write is in flight (double-submit guard).
  async function guarded(fn) {
    if (S.busy || S.expired) return;
    S.busy = true;
    document.body.classList.add('busy');
    try { await fn(); } finally { S.busy = false; document.body.classList.remove('busy'); }
  }

  const APPLY_LABEL = '<i class="ph-bold ph-check-circle"></i> Apply';

  function applyAll() {
    return guarded(async () => {
      const btn = $('applyBtn');
      btn.disabled = true; btn.innerHTML = '<i class="ph-bold ph-circle-notch"></i> Applying…';
      try {
        const body = { globals: S.draftGlobals };
        if (S.selectedArena) { body.arena = S.selectedArena; body.arenaSettings = S.draftArena; }
        await api('/api/apply', { method: 'POST', body: JSON.stringify(body) });
        confettiBurst();
        toast('Changes applied!');
        S.dirty = false;
        S.busy = false; await load(); S.busy = true;
      } catch (e) {
        toast('Apply failed: ' + e.message, 'error');
        btn.disabled = S.expired;
      }
      btn.innerHTML = APPLY_LABEL;
    });
  }

  function saveArena() {
    return guarded(async () => {
      try {
        await api('/api/apply', { method: 'POST',
          body: JSON.stringify({ arena: S.selectedArena, arenaSettings: S.draftArena }) });
        toast('Arena saved');
        S.busy = false; await load(); S.busy = true;
      } catch (e) { toast('Save failed: ' + e.message, 'error'); }
    });
  }

  function createArena() {
    if (S.busy || S.expired) return;
    const name = (prompt('New arena name (letters, digits, _ or -, max 32):') || '').trim();
    if (!name) return;
    if (!/^[A-Za-z0-9_-]{1,32}$/.test(name)) { toast('Invalid arena name: use letters, digits, _ or - (max 32).', 'error'); return; }
    return guarded(async () => {
      try {
        await api('/api/create-arena', { method: 'POST', body: JSON.stringify({ name }) });
        toast('Arena "' + name + '" created');
        S.selectedArena = name;
        S.busy = false; await load(); S.busy = true;
        // server stores arena names as typed; make sure the editor opens it
        const created = (S.snapshot?.arenas || []).find(a => a.name.toLowerCase() === name.toLowerCase());
        if (created && S.selectedArena !== created.name) { S.selectedArena = created.name; S.draftArena = cloneArena(created); renderArenaList(); renderArenaEditor(); }
      } catch (e) { toast('Create failed: ' + e.message, 'error'); }
    });
  }

  function deleteArena() {
    if (!S.selectedArena || S.busy || S.expired) return;
    if (!confirm('Delete arena "' + S.selectedArena + '"? This cannot be undone.')) return;
    return guarded(async () => {
      try {
        await api('/api/delete-arena', { method: 'POST', body: JSON.stringify({ name: S.selectedArena }) });
        toast('Arena deleted');
        S.selectedArena = null;
        S.busy = false; await load(); S.busy = true;
      } catch (e) { toast('Delete failed: ' + e.message, 'error'); }
    });
  }

  async function logout() {
    try { await api('/api/logout', { method: 'POST', body: '{}' }); } catch (e) { /* already gone */ }
    try { sessionStorage.removeItem('omniarena-token'); } catch (e) {}
    S.token = ''; S.expired = true; stopLivePolling();
    setStatus(false, 'Logged out');
    $('applyBtn').disabled = true;
    toast('Logged out. Run /oa web for a new link.');
  }

  // ── Arena draft helpers (matches Java ArenaView json) ──────────────────
  function cloneArena(a) {
    return {
      enabled: a.enabled, gameMode: a.gameMode,
      minPlayers: a.minPlayers, maxPlayers: a.maxPlayers,
      matchDuration: a.matchDuration, countdown: a.countdown, respawnDelay: a.respawnDelay, lives: a.lives, respawnWaveInterval: a.respawnWaveInterval,
      midMatchJoin: a.midMatchJoin, spectatorAllowed: a.spectatorAllowed, whitelistEnabled: a.whitelistEnabled,
      region: {
        worldName: a.regionWorld || '',
        minX: a.regionMinX ?? '', minY: a.regionMinY ?? '', minZ: a.regionMinZ ?? '',
        maxX: a.regionMaxX ?? '', maxY: a.regionMaxY ?? '', maxZ: a.regionMaxZ ?? '',
      },
      rules: {
        fallDamage: a.fallDamage, fireDamage: a.fireDamage, friendlyFire: a.friendlyFire,
        blockBreak: a.blockBreak, blockPlace: a.blockPlace, itemDrop: a.itemDrop, hungerDrain: a.hungerDrain,
      },
      visual: {
        titles: a.titles, sounds: a.sounds, particles: a.particles,
        scoreboard: a.scoreboard, bossBar: a.bossBar, actionBar: a.actionBar, holograms: a.holograms,
      },
    };
  }

  function setNested(obj, key, val) {
    const parts = key.split('.');
    let cur = obj;
    for (let i = 0; i < parts.length - 1; i++) { if (!cur[parts[i]]) cur[parts[i]] = {}; cur = cur[parts[i]]; }
    cur[parts[parts.length - 1]] = val;
  }

  // ── HTML builders ──────────────────────────────────────────────────────
  function section(title, icon, open, body) { return sectionRaw(title, icon, open, null, body); }
  function sectionRaw(title, icon, open, count, body) {
    const badge = count != null ? `<span class="section-count">${esc(count)}</span>` : '';
    return `<div class="section${open ? '' : ' collapsed'}">
      <div class="section-head">
        <span class="section-title"><i class="ph-bold ${esc(icon)}"></i> ${esc(title)} ${badge}</span>
        <i class="ph-bold ph-caret-down chevron"></i>
      </div>
      <div class="section-body">${body}</div>
    </div>`;
  }
  function field(label, inputHtml) { return `<div class="field"><label class="field-label">${esc(label)}</label>${inputHtml}</div>`; }
  function textInput(key, val) { return `<input class="input" data-key="${esc(key)}" value="${esc(val ?? '')}" type="text">`; }
  function numInput(key, val) { return `<input class="input" data-key="${esc(key)}" value="${esc(val ?? '')}" type="number">`; }
  function select(key, val, opts) {
    if (val && !opts.includes(val)) opts = [val, ...opts];
    return `<select class="input" data-key="${esc(key)}">${opts.map(o => `<option value="${esc(o)}"${o === val ? ' selected' : ''}>${esc(o)}</option>`).join('')}</select>`;
  }
  function cfgToggle(key, label, val) {
    return `<div class="cfg-row"><div class="cfg-grow"><div class="field-label">${esc(label)}</div></div>
      <div class="cfg-control"><label class="toggle"><input type="checkbox" data-key="${esc(key)}"${val ? ' checked' : ''}><span class="slider"></span></label></div></div>`;
  }
  function emptyState(icon, title, sub) {
    return `<div class="empty"><i class="ph-bold ${esc(icon)}"></i><div style="font-family:var(--font-display);font-weight:800;font-size:20px;color:var(--ink)">${esc(title)}</div><div>${esc(sub)}</div></div>`;
  }

  function wireSections(root) {
    root.querySelectorAll('.section-head').forEach(h => {
      h.addEventListener('click', () => h.parentElement.classList.toggle('collapsed'));
    });
  }

  // ── Theme Toggle ─────────────────────────────────────────────────────────
  function initTheme() {
    if (!$('themeToggle')) return; // no theme switch in the UI
    let saved = 'light';
    try { saved = localStorage.getItem('omniarena-theme') || 'light'; } catch (e) {}
    document.documentElement.setAttribute('data-theme', saved);
    updateThemeIcon(saved);
    $('themeToggle').addEventListener('click', () => {
      const current = document.documentElement.getAttribute('data-theme') || 'light';
      const next = current === 'light' ? 'dark' : 'light';
      document.documentElement.setAttribute('data-theme', next);
      try { localStorage.setItem('omniarena-theme', next); } catch (e) {}
      updateThemeIcon(next);
    });
  }

  function updateThemeIcon(theme) {
    const icon = $('themeToggle').querySelector('i');
    icon.className = theme === 'light' ? 'ph-bold ph-moon' : 'ph-bold ph-sun';
  }

  // ── Init ───────────────────────────────────────────────────────────────
  function init() {
    initTabs();
    initTheme();
    $('refreshBtn').addEventListener('click', load);
    $('applyBtn').addEventListener('click', applyAll);
    $('logoutBtn').addEventListener('click', logout);
    window.addEventListener('beforeunload', (e) => { if (S.dirty) { e.preventDefault(); e.returnValue = ''; } });
    window.addEventListener('load', detectIcons);
    $('createArenaBtn').addEventListener('click', createArena);

    $('cfgSearch').addEventListener('input', (e) => { S.cfgQuery = e.target.value; renderConfig(); });
    $('cfgExpand').addEventListener('click', () => document.querySelectorAll('#configGroups .section').forEach(s => s.classList.remove('collapsed')));
    $('cfgCollapse').addEventListener('click', () => document.querySelectorAll('#configGroups .section').forEach(s => s.classList.add('collapsed')));

    $('liveRefreshBtn').addEventListener('click', loadLiveMatches);
    $('playerSearch').addEventListener('input', debounce(() => loadPlayers(), 300));

    const topbar = $('topbar'), toTop = $('toTop');
    window.addEventListener('scroll', () => {
      topbar.classList.toggle('scrolled', window.scrollY > 8);
      toTop.classList.toggle('show', window.scrollY > 500);
    }, { passive: true });
    toTop.addEventListener('click', () => window.scrollTo({ top: 0, behavior: 'smooth' }));

    load();
  }

  // ── Live Matches ───────────────────────────────────────────────────────
  let liveMatchTimer = null;
  function stopLivePolling() { if (liveMatchTimer) { clearTimeout(liveMatchTimer); liveMatchTimer = null; } }
  function liveTabActive() { return $('tab-live').classList.contains('active'); }
  async function loadLiveMatches() {
    stopLivePolling();
    if (S.expired || !S.token) return;
    try {
      const data = await api('/api/live-matches');
      renderLiveMatches(data);
    } catch (e) {
      if (e.status === 401) return;
      $('liveMatchesGrid').innerHTML = '<div class="empty"><i class="ph-bold ph-warning-circle"></i><p>Failed to load live matches: ' + esc(e.message) + '</p></div>';
    }
    // poll only while the Live tab is open and the browser tab is visible
    if (liveTabActive() && !S.expired) liveMatchTimer = setTimeout(loadLiveMatches, 5000);
  }

  function renderLiveMatches(matches) {
    if (!matches || matches.length === 0) {
      $('liveMatchesGrid').innerHTML = '<div class="empty"><i class="ph-bold ph-soccer-ball"></i><p>No live matches right now. Matches appear here once an arena starts.</p></div>';
      return;
    }
    $('liveMatchesGrid').innerHTML = matches.map(m => {
      const leaderboardRows = (m.leaderboard || []).slice(0, 10).map((p, idx) => `
        <tr class="lb-row">
          <td class="lb-rank">${idx + 1}</td>
          <td class="lb-name">${esc(p.name)}</td>
          <td class="lb-stat">${esc(p.kills)}</td>
          <td class="lb-stat">${esc(p.deaths)}</td>
          <td class="lb-stat">${esc(p.streak)}</td>
        </tr>
      `).join('');
      return `
        <div class="live-match-card">
          <div class="live-match-header">
            <div class="live-match-header-left">
              <span class="live-match-arena">${esc(m.arena)}</span>
              <span class="live-match-mode">${esc(m.gameMode)}</span>
            </div>
            <div class="live-match-header-right">
              <span class="live-match-timer">${esc(m.time)}</span>
              <span class="live-match-status"><span class="live-dot"></span> ${esc(m.status)}</span>
            </div>
          </div>

          <div class="live-match-body">
            <div class="live-match-scoreboard">
              <div class="live-match-team">
                <div class="live-match-team-name">${esc(m.team1.name || 'Team 1')}</div>
                <div class="live-match-team-score">${esc(m.team1.score)}</div>
                <div class="live-match-team-members">${esc(m.team1.members)} members</div>
              </div>
              <div class="live-match-divider">VS</div>
              <div class="live-match-team">
                <div class="live-match-team-name">${esc(m.team2.name || 'Team 2')}</div>
                <div class="live-match-team-score">${esc(m.team2.score)}</div>
                <div class="live-match-team-members">${esc(m.team2.members)} members</div>
              </div>
            </div>

            <div class="live-match-stats-grid">
              <div class="live-match-stat">
                <div class="live-match-stat-label">Kills</div>
                <div class="live-match-stat-value">${esc(m.team1.kills)} - ${esc(m.team2.kills)}</div>
              </div>
              <div class="live-match-stat">
                <div class="live-match-stat-label">Deaths</div>
                <div class="live-match-stat-value">${esc(m.team1.deaths)} - ${esc(m.team2.deaths)}</div>
              </div>
              <div class="live-match-stat">
                <div class="live-match-stat-label">Players</div>
                <div class="live-match-stat-value">${esc(m.playersAlive)} / ${esc(m.totalPlayers)}</div>
              </div>
            </div>

            <div class="live-match-leaderboard">
              <h3 class="lb-title">Top Players</h3>
              <table class="lb-table">
                <thead>
                  <tr class="lb-header">
                    <th class="lb-rank">#</th>
                    <th class="lb-name">Player</th>
                    <th class="lb-stat">K</th>
                    <th class="lb-stat">D</th>
                    <th class="lb-stat">S</th>
                  </tr>
                </thead>
                <tbody>
                  ${leaderboardRows}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      `;
    }).join('');
  }

  // ── Players ───────────────────────────────────────────────────────────
  async function loadPlayers() {
    const query = $('playerSearch').value.trim();
    if (S.expired || !S.token) return;
    try {
      const data = await api(`/api/players${query ? '?search=' + encodeURIComponent(query) : ''}`);
      renderPlayers(data);
    } catch (e) {
      $('playersGrid').innerHTML = '<div class="empty"><i class="ph-bold ph-users"></i><p>Failed to load players: ' + esc(e.message) + '</p></div>';
    }
  }

  function renderPlayers(players) {
    if (!players || players.length === 0) {
      $('playersGrid').innerHTML = '<div class="empty"><i class="ph-bold ph-users"></i><p>No online players found.</p></div>';
      return;
    }
    $('playersGrid').innerHTML = players.map(p => `
      <div class="player-card">
        <div class="player-card-header">
          <div class="player-card-avatar">${esc(String(p.name ?? '?').charAt(0).toUpperCase())}</div>
          <div>
            <div class="player-card-name">${esc(p.name)}</div>
            <div class="player-card-elo">ELO: ${esc(p.elo)}</div>
          </div>
        </div>
        <div class="player-card-stats">
          <div class="player-card-stat">
            <div class="player-card-stat-label">Wins</div>
            <div class="player-card-stat-value">${esc(p.wins)}</div>
          </div>
          <div class="player-card-stat">
            <div class="player-card-stat-label">Losses</div>
            <div class="player-card-stat-value">${esc(p.losses)}</div>
          </div>
          <div class="player-card-stat">
            <div class="player-card-stat-label">K/D</div>
            <div class="player-card-stat-value">${esc((Number(p.kd) || 0).toFixed(2))}</div>
          </div>
          <div class="player-card-stat">
            <div class="player-card-stat-label">Matches</div>
            <div class="player-card-stat-value">${esc(p.matches)}</div>
          </div>
        </div>
      </div>
    `).join('');
  }

  // If the icon font (unpkg) did not load, hide the empty glyph boxes; labels remain.
  function detectIcons() {
    try {
      const probe = document.createElement('i');
      probe.className = 'ph-bold ph-check-circle';
      probe.style.cssText = 'position:absolute;visibility:hidden';
      document.body.appendChild(probe);
      const c = getComputedStyle(probe, '::before').content;
      probe.remove();
      if (!c || c === 'none' || c === 'normal') document.documentElement.classList.add('no-icons');
    } catch (e) { /* ignore */ }
  }

  function debounce(fn, delay) {
    let timeout;
    return (...args) => {
      clearTimeout(timeout);
      timeout = setTimeout(() => fn(...args), delay);
    };
  }

  document.readyState === 'loading'
    ? document.addEventListener('DOMContentLoaded', init)
    : init();
})();
