import {
  CONSOLES, SHORT_CODES, PAGE_SIZE, state, $, $$, actions,
  escapeHtml, consoleMeta, consoleIconSvg, joinLocalPath,
  resolveLocalDownloadDir, resolveTargetRemoteDir, consoleSelectOptionsHtml,
  formatBytes, hashString, wrapTitleLines, buildConsoleMotif,
  buildCoverSvg, renderCoverMarkup, toast, formatRelativeTime, findLibraryEntry,
  setAmbientBackdrop,
} from '../state.js';
import { api } from '../api-client.js';
import { renderActivitySheet } from '../activity-sheet.js';

export async function performMinervaSearch(e) {
  if (e) e.preventDefault();
  const q = $('#minerva-query').value.trim();
  const platform = $('#minerva-platform').value;
  if (q.length < 2) { toast('Escribe al menos 2 caracteres para buscar en MiNERVA', 'error'); return; }
  const skeleton = $('#minerva-skeleton'), grid = $('#minerva-grid'), empty = $('#minerva-empty');
  skeleton.hidden = false; grid.hidden = true; empty.hidden = true;
  $('#minerva-status-text').textContent = 'Consultando MiNERVA…';
  try {
    const rows = await api('/api/v1/catalog/search?' + new URLSearchParams({ q, platform }));
    state.minervaResults = rows;
    skeleton.hidden = true;
    if (!rows.length) {
      empty.hidden = false;
      empty.querySelector('h3').textContent = 'Sin ediciones encontradas';
      empty.querySelector('p').textContent = `Sin coincidencias para "${q}".`;
      $('#minerva-status-text').textContent = '0 resultados';
      return;
    }
    const groupsMap = new Map();
    rows.forEach(r => {
      const cKey = (r.platform || 'other').toLowerCase();
      const key = `${cKey}:${String(r.title || '').toLowerCase()}`;
      if (!groupsMap.has(key)) groupsMap.set(key, { key, title: r.title, consoleKey: cKey, items: [], coverUrl: null });
      groupsMap.get(key).items.push(r);
    });
    const groups = [...groupsMap.values()];
    $('#minerva-status-text').textContent = `${groups.length} título(s) · ${rows.length} edición(es)`;

    const frag = document.createDocumentFragment();
    groups.forEach(g => {
      const rep = g.items[0];
      const meta = consoleMeta(g.consoleKey);
      const card = document.createElement('article');
      card.className = 'hylian-card rom-card';
      card.dataset.coverKey = g.key;
      card.tabIndex = 0;
      card.innerHTML = `
        <div class="cover-frame cover-frame-34">
          ${buildCoverSvg(g.title, g.consoleKey)}
          <span class="avail-dot hub" title="Disponible en MiNERVA"></span>
        </div>
        <div class="hylian-card-meta">
          <h3 class="card-title">${escapeHtml(g.title)}</h3>
          <span class="hylian-card-sub mono">${escapeHtml(meta.label)} · ${g.items.length} ed. · ${formatBytes(rep.size)}</span>
        </div>
      `;
      card.addEventListener('mouseenter', () => {
        if (g.coverUrl) setAmbientBackdrop(g.coverUrl, meta.color);
      });
      card.addEventListener('click', () => openMinervaDrawer(g));
      card.addEventListener('keydown', ev => { if (ev.key === 'Enter' || ev.key === ' ') { ev.preventDefault(); openMinervaDrawer(g); } });
      frag.appendChild(card);
    });
    grid.replaceChildren(frag);
    grid.hidden = false;

    // Auto-resolve lightweight WebP covers for search results
    const coverPayload = groups.slice(0, 20).map(g => ({ key: g.key, title: g.title, consoleKey: g.consoleKey }));
    api('/api/v1/covers/resolve-batch', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ items: coverPayload })
    }).then(res => {
      const resolved = res.covers || {};
      groups.forEach(g => {
        const url = resolved[g.key];
        if (!url) return;
        g.coverUrl = url;
        const cardEl = [...grid.children].find(el => el.dataset.coverKey === g.key);
        const frame = cardEl?.querySelector('.cover-frame');
        if (frame && !frame.querySelector('.cover-fg-contain')) {
          const dot = frame.querySelector('.avail-dot');
          const bg = document.createElement('img');
          bg.className = 'cover-blur-bg';
          bg.src = url;
          bg.decoding = 'async';
          bg.alt = '';
          bg.setAttribute('aria-hidden', 'true');
          bg.onerror = () => { bg.hidden = true; };
          const img = document.createElement('img');
          img.className = 'cover-photo cover-fg-contain';
          img.src = url;
          img.decoding = 'async';
          img.alt = `Carátula de ${g.title}`;
          img.onerror = () => { img.hidden = true; };
          frame.insertBefore(bg, dot || null);
          frame.insertBefore(img, dot || null);
        }
      });
      actions.refreshCoverStats();
    }).catch(() => {});
  } catch (err) {
    skeleton.hidden = true; empty.hidden = false;
    empty.querySelector('h3').textContent = 'Error consultando MiNERVA';
    empty.querySelector('p').textContent = err.message;
  }
}

export async function openMinervaDrawer(group) {
  const meta = consoleMeta(group.consoleKey);
  actions.openDrawerBase(`${meta.full} · EDICIONES CATÁLOGO`, group.title);
  const artHtml = group.coverUrl
    ? `${buildCoverSvg(group.title, group.consoleKey)}<img class="cover-photo" src="${escapeHtml(group.coverUrl)}" decoding="async" alt="Carátula de ${escapeHtml(group.title)}">`
    : buildCoverSvg(group.title, group.consoleKey);

  const isIaGroup = group.items.length > 0 && group.items.every(ed => Boolean(ed.identifier));
  const uniqueIaIds = isIaGroup ? new Set(group.items.map(ed => ed.identifier)) : new Set();
  const repIa = group.items[0];

  if (isIaGroup && uniqueIaIds.size === 1 && repIa?.identifier) {
    const localDest = resolveLocalDownloadDir(group.consoleKey, repIa.arcadeCore || '');
    $('#drawer-body').innerHTML = `
      <div class="drawer-hero">
        <div class="drawer-art">${artHtml}</div>
        <div class="drawer-summary">
          <div class="drawer-badges"><span class="sys-chip" style="color:${meta.color};">${escapeHtml(meta.label)}</span><span class="mini-badge">${escapeHtml(repIa.provider || 'Internet Archive')}</span></div>
          <p class="drawer-desc">Se guardará en: <code class="mono">${escapeHtml(localDest)}</code></p>
          <div><button id="minerva-goto-paths-btn" class="btn btn-ghost btn-sm" type="button">Cambiar carpeta destino</button></div>
        </div>
      </div>
      <div id="ia-files-container" class="editions-list">
        <div class="edition-item"><span class="mono" style="font-size:12px;color:var(--text-muted);">Cargando manifiesto de archivos de ${escapeHtml(repIa.identifier)}…</span></div>
      </div>
    `;
    $('#minerva-goto-paths-btn')?.addEventListener('click', () => {
      actions.closeDrawer();
      switchSettingsTab('paths');
      actions.switchView('settings');
    });
    try {
      const files = await api(`/api/v1/catalog/ia-files/${encodeURIComponent(repIa.identifier)}`);
      const box = $('#ia-files-container');
      if (!box) return;
      if (!files.length) {
        box.innerHTML = `<div class="edition-item"><span style="font-size:13px;color:var(--text-secondary);">No se encontraron archivos ROM/ISO directos en este ítem.</span><a class="btn btn-ghost btn-sm" href="${escapeHtml(repIa.archiveUrl)}" target="_blank" rel="noopener">Ver ficha IA</a></div>`;
        return;
      }
      box.innerHTML = files.map((f, idx) => `
        <div class="edition-item">
          <div class="edition-top">
            <strong style="font-size:12.5px;word-break:break-all;">${escapeHtml(f.name.split('/').pop())}</strong>
            <span class="mini-badge">${formatBytes(f.size)}</span>
          </div>
          <div style="display:flex;align-items:center;justify-content:space-between;gap:8px;flex-wrap:wrap;">
            <span class="mono" style="font-size:11px;color:var(--text-muted);">${escapeHtml(f.format || 'Archivo')}${f.isRom ? ' · ROM/ISO' : ''}</span>
            <button class="btn btn-primary btn-sm ia-dl-btn" type="button" data-idx="${idx}">Descargar en Oz</button>
          </div>
        </div>
      `).join('') + `<div style="padding-top:4px;"><a class="btn btn-ghost btn-sm" href="${escapeHtml(repIa.archiveUrl)}" target="_blank" rel="noopener">Ver ficha de origen en Archive.org</a></div>`;

      $$('.ia-dl-btn').forEach(btn => btn.addEventListener('click', () => {
        const f = files[Number(btn.dataset.idx)];
        triggerDirectIaDownload(repIa.identifier, f.name, group.consoleKey, group.title, repIa.arcadeCore || '', btn);
      }));
    } catch (err) {
      const box = $('#ia-files-container');
      if (box) box.innerHTML = `<div class="edition-item"><span style="font-size:12.5px;color:var(--danger);">${escapeHtml(err.message)}</span></div>`;
    }
    return;
  }

  const editionsHtml = group.items.map((ed, idx) => {
    const srcLabel = ed.identifier
      ? `${ed.provider || 'Internet Archive'} · Directo`
      : (ed.infoHash ? `MiNERVA Torrent · ${ed.infoHash.slice(0, 10)}…` : (ed.provider || 'MiNERVA'));
    return `
    <div class="edition-item">
      <div class="edition-top">
        <strong style="font-size:13px;">${escapeHtml(ed.edition || 'Edición estándar')}</strong>
        <span class="mini-badge">${formatBytes(ed.size)}</span>
      </div>
      <div class="mono" style="font-size:11.5px;color:var(--text-muted);word-break:break-all;">${escapeHtml(ed.filename)}</div>
      <div style="display:flex;align-items:center;justify-content:space-between;gap:8px;flex-wrap:wrap;">
        <span class="mono" style="font-size:11px;color:var(--text-secondary);">${escapeHtml(srcLabel)}</span>
        <div style="display:flex;gap:6px;flex-wrap:wrap;">
          ${ed.magnet ? `<button class="btn btn-secondary btn-sm minerva-magnet-btn" type="button" data-idx="${idx}">Copiar Magnet</button>` : ''}
          ${ed.identifier
            ? `<button class="btn btn-primary btn-sm minerva-ia-btn" type="button" data-idx="${idx}">Ver descarga en Oz</button><a class="btn btn-ghost btn-sm" href="${escapeHtml(ed.archiveUrl || ('https://archive.org/details/' + ed.identifier))}" target="_blank" rel="noopener">IA</a>`
            : (ed.archiveUrl
              ? `<a class="btn btn-primary btn-sm" href="${escapeHtml(ed.archiveUrl)}" target="_blank" rel="noopener">Ver ficha IA</a>`
              : (ed.magnet ? '' : `<button class="btn btn-primary btn-sm minerva-dl-btn" type="button" data-idx="${idx}">Descargar</button>`))}
        </div>
      </div>
      <div class="editions-list" id="ia-inline-${idx}" hidden style="margin-top:6px;"></div>
    </div>`;
  }).join('');
  $('#drawer-body').innerHTML = `
    <div class="drawer-hero">
      <div class="drawer-art">${artHtml}</div>
      <div class="drawer-summary">
        <div class="drawer-badges"><span class="sys-chip" style="color:${meta.color};">${escapeHtml(meta.label)}</span><span class="mini-badge">${group.items.length} edición(es)</span></div>
        <p class="drawer-desc">Catálogo multi-consola verificado. Las descargas se encolan en segundo plano para que sigas explorando.</p>
      </div>
    </div>
    <div class="editions-list">${editionsHtml}</div>
  `;
  $$('.minerva-dl-btn').forEach(btn => btn.addEventListener('click', () => triggerDownload(group.items[Number(btn.dataset.idx)], btn)));
  $$('.minerva-magnet-btn').forEach(btn => btn.addEventListener('click', () => {
    const ed = group.items[Number(btn.dataset.idx)];
    if (ed?.magnet) actions.copyText(ed.magnet, 'Enlace Magnet');
  }));
  $$('.minerva-ia-btn').forEach(btn => btn.addEventListener('click', async () => {
    const idx = Number(btn.dataset.idx);
    const ed = group.items[idx];
    const inlineBox = $(`#ia-inline-${idx}`);
    if (!ed?.identifier || !inlineBox) return;
    inlineBox.hidden = false;
    inlineBox.innerHTML = `<span class="mono" style="font-size:11.5px;color:var(--text-muted);">Cargando archivos…</span>`;
    try {
      const files = await api(`/api/v1/catalog/ia-files/${encodeURIComponent(ed.identifier)}`);
      if (!files.length) {
        inlineBox.innerHTML = `<span class="mono" style="font-size:11.5px;color:var(--text-muted);">Sin archivos ROM directos.</span>`;
        return;
      }
      inlineBox.innerHTML = files.slice(0, 6).map((f, fIdx) => `
        <div style="display:flex;align-items:center;justify-content:space-between;gap:8px;padding:6px 8px;background:var(--bg-surface);border-radius:6px;border:1px solid var(--border-subtle);">
          <span class="mono" style="font-size:11.5px;word-break:break-all;">${escapeHtml(f.name.split('/').pop())} (${formatBytes(f.size)})</span>
          <button class="btn btn-primary btn-sm ia-inline-dl" type="button" data-fidx="${fIdx}">Descargar en Oz</button>
        </div>
      `).join('');
      inlineBox.querySelectorAll('.ia-inline-dl').forEach(dlBtn => dlBtn.addEventListener('click', () => {
        const f = files[Number(dlBtn.dataset.fidx)];
        triggerDirectIaDownload(ed.identifier, f.name, group.consoleKey, ed.title || group.title, ed.arcadeCore || '', dlBtn);
      }));
    } catch (err) {
      inlineBox.innerHTML = `<span style="font-size:11.5px;color:var(--danger);">${escapeHtml(err.message)}</span>`;
    }
  }));
}

export function setQueueButtonPending(btn) {
  if (!btn) return () => {};
  const origText = btn.textContent;
  btn.disabled = true;
  btn.textContent = 'Encolando…';
  return queued => {
    btn.disabled = Boolean(queued);
    btn.textContent = queued ? '✓ En cola' : origText;
  };
}

export async function triggerDirectIaDownload(identifier, filename, platform, title, arcadeCore = '', btn = null) {
  const settleBtn = setQueueButtonPending(btn);
  try {
    const job = await api('/api/v1/downloads/ia', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ identifier, filename, platform, title, arcadeCore })
    });
    const key = 'IA:' + job.id;
    state.downloadJobs.set(key, { provider: 'Internet Archive', title: job.title || title, job });
    state.notifiedDownloads.set(key, 'queued');
    settleBtn(true);
    toast(`Descarga en cola: ${job.title || job.filename}`, 'info', {
      durationMs: 4200,
      actionLabel: 'Ver cola',
      onAction: () => { actions.closeDrawer(); actions.switchView('downloads'); }
    });
    renderQueues();
    pollDownloads();
  } catch (err) {
    settleBtn(false);
    toast('Error iniciando descarga IA: ' + err.message, 'error');
  }
}

export function downloadEndpoint(provider) {
  if (provider === 'Internet Archive') return '/oz/v1/internet-archive/downloads';
  if (provider === 'Manifest HTTP') return '/oz/v1/manifest/downloads';
  return '/oz/v1/minerva/downloads';
}

export async function triggerDownload(ed, btn = null) {
  const settleBtn = setQueueButtonPending(btn);
  try {
    const provider = ed.provider || 'MiNERVA';
    const job = await api(downloadEndpoint(provider), {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id: ed.id, provider })
    });
    const key = provider + ':' + job.id;
    state.downloadJobs.set(key, { provider, title: ed.title || job.title || job.id, job });
    state.notifiedDownloads.set(key, 'queued');
    settleBtn(true);
    toast(`Descarga en cola: ${ed.title}`, 'info', {
      durationMs: 4200,
      actionLabel: 'Ver cola',
      onAction: () => { actions.closeDrawer(); actions.switchView('downloads'); }
    });
    renderQueues();
    pollDownloads();
  } catch (err) {
    settleBtn(false);
    toast('Error iniciando descarga: ' + err.message, 'error');
  }
}

export function getDefaultFtpTarget() {
  return state.targets.find(t => t.id === state.activeTargetId) || state.targets[0] || null;
}

export function findLibraryRomForJob(job) {
  if (!job || !state.libraryItems.length) return null;
  const rawDest = String(job.destPath || job.targetPath || job.filename || job.fileName || '').trim();
  const leaf = rawDest.split(/[\\/]/).pop() || '';
  const leafLower = leaf.toLowerCase();
  const stemLower = leafLower.replace(/\.[^.]+$/, '');
  const platLower = String(job.platform || '').toLowerCase();
  const norm = s => String(s || '').toLowerCase().replace(/[^a-z0-9]+/g, '');
  if (leafLower) {
    const exact = state.libraryItems.find(it => {
      const rp = String(it.relativePath || '').split(/[\\/]/).pop().toLowerCase();
      return rp === leafLower && (!platLower || it.consoleKey === platLower || it.platform === platLower);
    }) || state.libraryItems.find(it => String(it.relativePath || '').split(/[\\/]/).pop().toLowerCase() === leafLower);
    if (exact) return exact;
  }
  if (stemLower) {
    const byRomset = state.libraryItems.find(it =>
      it.romset && String(it.romset).toLowerCase() === stemLower && (!platLower || it.consoleKey === platLower || it.platform === platLower)
    ) || state.libraryItems.find(it => it.romset && String(it.romset).toLowerCase() === stemLower);
    if (byRomset) return byRomset;
  }
  const titleNorm = norm(job.title || stemLower);
  if (titleNorm) {
    return state.libraryItems.find(it => norm(it.title) === titleNorm || norm(it.cleanTitle) === titleNorm) || null;
  }
  return null;
}

export async function directSendDownloadedRomFtp(job, btn = null) {
  const target = getDefaultFtpTarget();
  if (!target) {
    toast('Configura una consola FTP primero en Consolas FTP / Tinfoil', 'error');
    actions.closeDrawer();
    actions.switchView('bridge');
    return;
  }
  const origText = btn ? btn.textContent : '';
  if (btn) {
    btn.disabled = true;
    btn.textContent = 'Enviando…';
  }
  try {
    let rom = findLibraryRomForJob(job);
    if (!rom) {
      await api('/api/v1/library/rescan', { method: 'POST' }).catch(() => {});
      await actions.loadLibrary();
      rom = findLibraryRomForJob(job);
    }
    if (!rom) {
      if (btn) { btn.disabled = false; btn.textContent = origText; }
      toast('No se encontró el archivo local en Mi biblioteca para enviar por FTP', 'error');
      return;
    }
    const remoteDir = resolveTargetRemoteDir(target, rom.consoleKey, rom.platform);
    const trJob = await api('/api/v1/transfers', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ entryId: rom.entryId, targetId: target.id, remoteDir: remoteDir || null })
    });
    state.transferJobs.set(trJob.id, trJob);
    if (btn) {
      btn.disabled = true;
      btn.textContent = '✓ Enviado FTP';
    }
    toast(
      `Enviando por FTP a ${target.name}: ${rom.title}`,
      'success',
      state.view !== 'downloads'
        ? { durationMs: 4500, actionLabel: 'Ver cola', onAction: () => { actions.closeDrawer(); actions.switchView('downloads'); } }
        : { durationMs: 4200 }
    );
    renderQueues();
    pollTransfers();
  } catch (err) {
    if (btn) { btn.disabled = false; btn.textContent = origText; }
    toast('Error iniciando transferencia FTP: ' + err.message, 'error');
  }
}

export async function jumpToDownloadedRom(job, mode = 'library') {
  let rom = findLibraryRomForJob(job);
  if (!rom) {
    try {
      await api('/api/v1/library/rescan', { method: 'POST' });
      await actions.loadLibrary();
      rom = findLibraryRomForJob(job);
    } catch (e) { /* fallback to search query */ }
  }
  actions.closeDrawer();
  if (rom) {
    state.consoleFilter = '';
    state.stateFilter = 'all';
    $$('.nav-item[data-state-filter]').forEach(b => b.classList.toggle('active', b.dataset.stateFilter === 'all'));
    state.searchQuery = rom.title;
    $('#global-search').value = rom.title;
    $('#search-clear-btn').hidden = false;
    actions.switchView('library');
    actions.updateSidebarCounts();
    actions.applyLibraryFilters();
    if (mode === 'ftp') {
      actions.openLibraryDrawer(rom, false);
      setTimeout(() => $('#drawer-target-select')?.focus(), 70);
    } else {
      toast(`Mostrando en biblioteca: ${rom.title}`, 'info');
    }
    return;
  }
  const fallbackQ = String(job.title || job.filename || '').replace(/\.[^.]+$/, '').trim();
  state.searchQuery = fallbackQ;
  $('#global-search').value = fallbackQ;
  $('#search-clear-btn').hidden = !fallbackQ;
  actions.switchView('library');
  actions.applyLibraryFilters();
  toast(fallbackQ ? `Buscando "${fallbackQ}" en Mi biblioteca` : 'Revisa tu biblioteca local', 'info');
}

export function updateDownloadFloatingDock() {
  renderActivitySheet();
}

export function renderQueues() {
  const dlWrap = $('#download-jobs-list'), trWrap = $('#transfer-jobs-list');
  const activeDl = [...state.downloadJobs.values()].filter(x => !['complete', 'failed'].includes(x.job.state)).length;
  const activeTr = [...state.transferJobs.values()].filter(x => !['complete', 'failed'].includes(x.state)).length;
  if ($('#nav-dl-badge')) $('#nav-dl-badge').textContent = activeDl + activeTr;
  renderActivitySheet();
  if (!dlWrap || !trWrap) return;

  const defaultTarget = getDefaultFtpTarget();
  const ftpBtnTitle = defaultTarget
    ? `Enviar directo a ${defaultTarget.name} (${defaultTarget.host}:${defaultTarget.port})`
    : 'Configurar consola FTP por defecto';

  dlWrap.innerHTML = !state.downloadJobs.size
    ? `<div class="empty-card"><h3>Sin descargas activas</h3><p>Cuando descargues una edición desde MiNERVA verás el progreso aquí.</p></div>`
    : [...state.downloadJobs.entries()].map(([key, { provider, title, job }]) => {
        const pct = job.size ? Math.min(100, Math.round((job.bytes || 0) * 100 / job.size)) : 0;
        const completeActions = job.state === 'complete'
          ? `<div class="job-actions">
              <button class="btn btn-secondary btn-sm job-show-lib" type="button" data-job-key="${escapeHtml(key)}">Mostrar en biblioteca</button>
              <button class="btn btn-primary btn-sm job-send-ftp" type="button" data-job-key="${escapeHtml(key)}" title="${escapeHtml(ftpBtnTitle)}">Transferir por FTP</button>
            </div>`
          : '';
        return `<div class="job-card"><div class="job-head"><span class="job-title">${escapeHtml(title || job.id)}</span><span class="verify-pill ${job.state === 'complete' ? 'verified' : ''}">${escapeHtml(job.state || 'pending')}</span></div><div class="mono" style="font-size:11.5px;color:var(--text-muted);">${escapeHtml(provider)} · ${escapeHtml(job.message || '')}</div><div class="progress-track"><div class="progress-fill" style="width:${pct}%"></div></div><div class="mono" style="font-size:11.5px;color:var(--text-secondary);">${formatBytes(job.bytes)} / ${formatBytes(job.size)} (${pct}%)</div>${completeActions}</div>`;
      }).join('');

  dlWrap.querySelectorAll('.job-show-lib').forEach(btn => btn.addEventListener('click', () => {
    const entry = state.downloadJobs.get(btn.dataset.jobKey);
    if (entry) jumpToDownloadedRom(entry.job, 'library');
  }));
  dlWrap.querySelectorAll('.job-send-ftp').forEach(btn => btn.addEventListener('click', () => {
    const entry = state.downloadJobs.get(btn.dataset.jobKey);
    if (entry) directSendDownloadedRomFtp(entry.job, btn);
  }));

  trWrap.innerHTML = !state.transferJobs.size
    ? `<div class="empty-card"><h3>Sin transferencias activas</h3><p>Abre cualquier ROM de tu biblioteca y selecciona la consola destino para enviarla por FTP.</p></div>`
    : [...state.transferJobs.values()].map(job => {
        const pct = job.total ? Math.min(100, Math.round((job.bytes || 0) * 100 / job.total)) : 0;
        return `<div class="job-card"><div class="job-head"><span class="job-title">${escapeHtml(job.title)} → ${escapeHtml(job.targetName || '')}</span><span class="verify-pill ${job.state === 'complete' ? 'verified' : ''}">${escapeHtml(job.state)}</span></div><div class="mono" style="font-size:11.5px;color:var(--text-muted);">${escapeHtml(job.message || '')}</div><div class="progress-track"><div class="progress-fill" style="width:${pct}%"></div></div><div class="mono" style="font-size:11.5px;color:var(--text-secondary);">${formatBytes(job.bytes)} / ${formatBytes(job.total)} (${pct}%)</div></div>`;
      }).join('');

  updateDownloadFloatingDock();
}


function _trackDownloadUpdate(key, provider, title, job) {
  const prev = state.downloadJobs.get(key);
  const trackedState = state.notifiedDownloads.get(key);
  const becameComplete = job.state === 'complete' && ((prev && prev.job.state !== 'complete') || trackedState === 'queued');
  const becameFailed = job.state === 'failed' && ((prev && prev.job.state !== 'failed') || trackedState === 'queued');
  state.downloadJobs.set(key, { provider, title, job });
  if (becameComplete) {
    state.notifiedDownloads.set(key, 'complete');
  } else if (becameFailed) {
    state.notifiedDownloads.set(key, 'failed');
  } else if (!state.notifiedDownloads.has(key)) {
    state.notifiedDownloads.set(key, job.state || 'queued');
  }
  return { becameComplete, becameFailed };
}

export async function applyDownloadEvent(job) {
  if (!job || !job.id) return;
  const key = 'IA:' + job.id;
  const title = job.title || job.filename || job.id;
  const { becameComplete, becameFailed } = _trackDownloadUpdate(key, job.provider || 'Internet Archive', title, job);
  renderQueues();
  if (becameComplete) {
    await actions.loadLibrary();
    renderQueues();
    toast(`Descarga lista: ${title}`, 'success', {
      durationMs: 7500,
      actionLabel: 'Enviar FTP',
      onAction: () => directSendDownloadedRomFtp(job),
    });
  } else if (becameFailed) {
    toast(`Error en descarga: ${title}${job.message ? ' — ' + job.message : ''}`, 'error', { durationMs: 6500 });
  }
}

export function applyTransferEvent(job) {
  if (!job || !job.id) return;
  const prev = state.transferJobs.get(job.id);
  state.transferJobs.set(job.id, job);
  renderQueues();
  if (job.state === 'complete' && (!prev || prev.state !== 'complete')) {
    toast(`Transferencia FTP completada: ${job.title || job.id}`, 'success');
  } else if (job.state === 'failed' && (!prev || prev.state !== 'failed')) {
    toast(`Error en transferencia FTP: ${job.message || job.title || job.id}`, 'error');
  }
}

export async function pollDownloads() {
  clearTimeout(state.downloadTimer);
  const completedNow = [];
  const failedNow = [];
  try {
    const directRows = await api('/api/v1/downloads');
    directRows.forEach(job => {
      const key = 'IA:' + job.id;
      const title = job.title || job.filename || job.id;
      const res = _trackDownloadUpdate(key, job.provider || 'Internet Archive', title, job);
      if (res.becameComplete) completedNow.push({ title, job });
      if (res.becameFailed) failedNow.push({ title, job });
    });
  } catch (e) { /* ignore */ }
  const providers = ['MiNERVA', 'Internet Archive', 'Manifest HTTP'];
  const results = await Promise.allSettled(providers.map(async p => ({ p, rows: await api(downloadEndpoint(p)) })));
  results.forEach(r => {
    if (r.status !== 'fulfilled') return;
    r.value.rows.forEach(job => {
      const key = r.value.p + ':' + job.id;
      const prev = state.downloadJobs.get(key);
      const title = job.title || prev?.title || job.fileName || job.id;
      const res = _trackDownloadUpdate(key, r.value.p, title, job);
      if (res.becameComplete) completedNow.push({ title, job });
      if (res.becameFailed) failedNow.push({ title, job });
    });
  });
  renderQueues();
  if (completedNow.length) {
    await api('/api/v1/library/rescan', { method: 'POST' }).catch(() => {});
    await actions.loadLibrary();
    renderQueues();
    completedNow.forEach(({ title, job }) => {
      toast(`Descarga lista: ${title}`, 'success', {
        durationMs: 7500,
        actionLabel: 'Enviar FTP',
        onAction: () => directSendDownloadedRomFtp(job),
      });
    });
  }
  failedNow.forEach(({ title, job }) => {
    toast(`Error en descarga: ${title}${job.message ? ' — ' + job.message : ''}`, 'error', { durationMs: 6500 });
  });
}

export async function pollTransfers() {
  clearTimeout(state.transferTimer);
  try {
    const rows = await api('/api/v1/transfers');
    rows.forEach(job => state.transferJobs.set(job.id, job));
  } catch (e) { /* ignore */ }
  renderQueues();
}
