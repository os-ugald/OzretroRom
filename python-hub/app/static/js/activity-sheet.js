import { state, $, actions, escapeHtml, formatBytes } from './state.js';

let sheetExpanded = false;
const patchFeed = [];

export function isActivitySheetExpanded() {
  return sheetExpanded;
}

export function toggleActivitySheet(forceState = null) {
  const sheet = $('#activity-sheet');
  const body = $('#activity-sheet-body');
  const btn = $('#activity-sheet-toggle');
  if (!sheet || !body || !btn) return;
  sheetExpanded = typeof forceState === 'boolean' ? forceState : !sheetExpanded;
  sheet.classList.toggle('expanded', sheetExpanded);
  body.hidden = !sheetExpanded;
  btn.setAttribute('aria-expanded', String(sheetExpanded));
}

export function recordActivityNotice(label, detail = '', tone = 'emerald') {
  patchFeed.unshift({
    id: 'n-' + Date.now() + '-' + Math.random().toString(16).slice(2, 6),
    label,
    detail,
    tone,
    at: new Date().toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' }),
  });
  if (patchFeed.length > 6) patchFeed.length = 6;
  renderActivitySheet();
}

export function setSseConnectionState(connected) {
  state.sseConnected = Boolean(connected);
  renderActivitySheet();
}

export function renderActivitySheet() {
  const sheet = $('#activity-sheet');
  const summaryEl = $('#activity-sheet-summary');
  const pillEl = $('#activity-sheet-pill');
  const listEl = $('#activity-sheet-list');
  const sseDot = $('#activity-sse-dot');
  if (!sheet || !summaryEl || !listEl) return;

  const activeDownloads = [...state.downloadJobs.values()].filter(
    x => !['complete', 'failed', 'interrupted'].includes(x.job?.state)
  );
  const activeTransfers = [...state.transferJobs.values()].filter(
    j => !['complete', 'failed', 'interrupted'].includes(j?.state)
  );
  const coverSync = state.coverSyncStatus && state.coverSyncStatus.running ? state.coverSyncStatus : null;

  const activeCount = activeDownloads.length + activeTransfers.length + (coverSync ? 1 : 0);
  if (sseDot) {
    sseDot.classList.toggle('online', Boolean(state.sseConnected));
  }

  let headline = state.sseConnected ? 'SSE en vivo · Sin actividad en cola' : 'Actividad en espera · SSE';
  if (activeDownloads.length) {
    const first = activeDownloads[0];
    const pct = first.job.size ? Math.min(100, Math.round(((first.job.bytes || 0) * 100) / first.job.size)) : 0;
    headline = `Descargando: ${first.title || first.job.filename || 'ROM'} (${pct}%)`;
  } else if (coverSync) {
    headline = `Carátulas: ${coverSync.processed || 0}/${coverSync.total || 0} · ${coverSync.currentTitle || 'Sincronizando'}`;
  } else if (activeTransfers.length) {
    const tr = activeTransfers[0];
    const pct = tr.total ? Math.min(100, Math.round(((tr.bytes || 0) * 100) / tr.total)) : 0;
    headline = `FTP → ${tr.targetName || 'Consola'}: ${tr.title || ''} (${pct}%)`;
  } else if (patchFeed.length) {
    headline = `${patchFeed[0].label}${patchFeed[0].detail ? ' · ' + patchFeed[0].detail : ''}`;
  }

  summaryEl.textContent = headline;
  if (pillEl) {
    pillEl.textContent = activeCount > 0 ? `${activeCount} activa(s)` : (state.sseConnected ? 'SSE OK' : 'SSE');
    pillEl.classList.toggle('is-busy', activeCount > 0);
  }

  const rowsHtml = [];
  if (coverSync) {
    const pct = coverSync.total ? Math.min(100, Math.round(((coverSync.processed || 0) * 100) / coverSync.total)) : 0;
    rowsHtml.push(`
      <div class="activity-row">
        <div class="activity-row-head">
          <strong>🖼 Sincronizando carátulas WebP (${coverSync.found || 0} halladas)</strong>
          <span class="mono">${coverSync.processed || 0}/${coverSync.total || 0}</span>
        </div>
        <div class="progress-track"><div class="progress-fill" style="width:${Math.max(4, pct)}%"></div></div>
        <span class="mono activity-row-sub">${escapeHtml(coverSync.currentTitle || 'Procesando portadas…')}</span>
      </div>
    `);
  }

  [...state.downloadJobs.entries()].slice(-4).reverse().forEach(([key, { provider, title, job }]) => {
    const pct = job.size ? Math.min(100, Math.round(((job.bytes || 0) * 100) / job.size)) : 0;
    const isComplete = job.state === 'complete';
    rowsHtml.push(`
      <div class="activity-row">
        <div class="activity-row-head">
          <strong>⬇ ${escapeHtml(title || job.filename || job.id)}</strong>
          <span class="mono">${escapeHtml(job.state || 'pending')} · ${pct}%</span>
        </div>
        <div class="progress-track"><div class="progress-fill" style="width:${pct}%"></div></div>
        <div class="activity-row-foot">
          <span class="mono activity-row-sub">${escapeHtml(provider)} · ${formatBytes(job.bytes)} / ${formatBytes(job.size)}</span>
          ${isComplete ? `<button class="btn btn-secondary btn-sm sheet-open-dl" type="button" data-job-key="${escapeHtml(key)}">Abrir</button>` : ''}
        </div>
      </div>
    `);
  });

  [...state.transferJobs.values()].slice(-4).reverse().forEach(job => {
    const pct = job.total ? Math.min(100, Math.round(((job.bytes || 0) * 100) / job.total)) : 0;
    rowsHtml.push(`
      <div class="activity-row">
        <div class="activity-row-head">
          <strong>📡 ${escapeHtml(job.title || job.id)} → ${escapeHtml(job.targetName || '')}</strong>
          <span class="mono">${escapeHtml(job.state || '')} · ${pct}%</span>
        </div>
        <div class="progress-track"><div class="progress-fill" style="width:${pct}%"></div></div>
        <span class="mono activity-row-sub">${escapeHtml(job.message || '')}</span>
      </div>
    `);
  });

  patchFeed.forEach(item => {
    rowsHtml.push(`
      <div class="activity-row">
        <div class="activity-row-head">
          <strong>${escapeHtml(item.label)}</strong>
          <span class="mono">${escapeHtml(item.at)}</span>
        </div>
        ${item.detail ? `<span class="mono activity-row-sub">${escapeHtml(item.detail)}</span>` : ''}
      </div>
    `);
  });

  if (!rowsHtml.length) {
    listEl.innerHTML = '<div class="activity-empty">Descargas MiNERVA, parches [ES], carátulas WebP y envíos FTP aparecerán aquí en tiempo real vía SSE.</div>';
  } else {
    listEl.innerHTML = rowsHtml.join('');
    listEl.querySelectorAll('.sheet-open-dl').forEach(btn => btn.addEventListener('click', () => {
      const entry = state.downloadJobs.get(btn.dataset.jobKey);
      if (entry && actions.jumpToDownloadedRom) actions.jumpToDownloadedRom(entry.job, 'library');
      toggleActivitySheet(false);
    }));
  }
}

export function initActivitySheet() {
  const toggleBtn = $('#activity-sheet-toggle');
  const closeBtn = $('#activity-sheet-close');
  toggleBtn?.addEventListener('click', () => toggleActivitySheet());
  closeBtn?.addEventListener('click', () => toggleActivitySheet(false));
  renderActivitySheet();
}
