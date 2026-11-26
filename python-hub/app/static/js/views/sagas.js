import {
  CONSOLES, SHORT_CODES, PAGE_SIZE, state, $, $$, actions,
  escapeHtml, consoleMeta, consoleIconSvg, joinLocalPath,
  resolveLocalDownloadDir, resolveTargetRemoteDir, consoleSelectOptionsHtml,
  formatBytes, hashString, wrapTitleLines, buildConsoleMotif,
  buildCoverSvg, renderCoverMarkup, toast, formatRelativeTime, findLibraryEntry,
} from '../state.js';
import { api } from '../api-client.js';

export function syncSagasFromLibrary() {
  if (!Array.isArray(state.sagas) || !state.sagas.length) return false;
  const byId = new Map(state.libraryItems.map(x => [x.entryId, x]));
  let changed = false;
  state.sagas.forEach(saga => {
    (saga.entries || []).forEach(entry => {
      if (entry.owned && entry.entryId) {
        const lib = byId.get(entry.entryId);
        if (lib && lib.hasCover && lib.coverUrl && (!entry.hasCover || entry.coverUrl !== lib.coverUrl)) {
          entry.hasCover = true;
          entry.coverUrl = lib.coverUrl;
          if (!saga.bannerCoverUrl) saga.bannerCoverUrl = lib.coverUrl;
          changed = true;
        }
      }
    });
  });
  return changed;
}

export async function loadSagas() {
  try {
    const rows = await api('/api/v1/sagas');
    state.sagas = Array.isArray(rows) ? rows : [];
    syncSagasFromLibrary();
    const totalOwned = state.sagas.filter(s => s.ownedCount > 0 || s.isCustom).length;
  if ($('#nav-sagas-count')) $('#nav-sagas-count').textContent = totalOwned;
  if (state.view === 'sagas' || state.view === 'collections' || (state.view === 'explore' && state.exploreTab === 'sagas')) {
    if (state.activeSagaId) openSagaDetail(state.activeSagaId);
    else renderSagasGallery();
  }
  } catch (e) { /* ignore */ }
}

export async function ensureSagaArt(sagaIds = null) {
  if (!sagaIds && state.sagaArtResolved) return;
  if (!sagaIds) state.sagaArtResolved = true;
  try {
    const res = await api('/api/v1/sagas/resolve-art', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sagaIds })
    });
    const artMap = res?.art || {};
    let updated = false;
    state.sagas.forEach(saga => {
      const art = artMap[saga.id];
      if (!art) return;
      if (art.heroBannerUrl && saga.heroBannerUrl !== art.heroBannerUrl) {
        saga.heroBannerUrl = art.heroBannerUrl;
        updated = true;
      }
      if (art.keyArtUrl && saga.keyArtUrl !== art.keyArtUrl) {
        saga.keyArtUrl = art.keyArtUrl;
        updated = true;
      }
      if (saga.heroBannerUrl || saga.keyArtUrl) {
        saga.bannerCoverUrl = saga.heroBannerUrl || saga.keyArtUrl || saga.bannerCoverUrl;
      }
    });
    if (updated && state.view === 'sagas') {
      if (state.activeSagaId) renderSagaHero(state.sagas.find(s => s.id === state.activeSagaId));
      else renderSagasGallery();
    }
  } catch (e) { /* ignore offline */ }
}

export async function resolveSagaGhostCovers(saga) {
  if (!saga || saga.isCustom || state.sagaGhostInFlight.has(saga.id)) return;
  const missing = (saga.entries || []).filter(e => !e.owned && !e.hasCover && !state.sagaGhostCovers[`saga:${saga.id}:${e.title.toLowerCase()}`]);
  if (!missing.length) return;
  state.sagaGhostInFlight.add(saga.id);
  const batch = missing.slice(0, 16).map(e => ({
    key: `saga:${saga.id}:${e.title.toLowerCase()}`,
    title: e.minervaQuery || e.title,
    consoleKey: e.consoleKey || ''
  }));
  try {
    const res = await api('/api/v1/covers/resolve-batch', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ items: batch })
    });
    const covers = res?.covers || {};
    let patched = false;
    Object.entries(covers).forEach(([k, url]) => {
      if (url) {
        state.sagaGhostCovers[k] = url;
        patched = true;
      }
    });
    if (patched) {
      (saga.entries || []).forEach(e => {
        const u = state.sagaGhostCovers[`saga:${saga.id}:${e.title.toLowerCase()}`];
        if (u && !e.hasCover) { e.coverUrl = u; e.hasCover = true; }
      });
      if (state.view === 'sagas' && state.activeSagaId === saga.id) renderSagaTimeline(saga);
    }
  } catch (e) { /* ignore offline */ }
  finally { state.sagaGhostInFlight.delete(saga.id); }
}

export async function syncSagaCovers(sagaId = null) {
  let targetSagas = [];
  if (sagaId) {
    const s = state.sagas.find(x => x.id === sagaId);
    if (s) targetSagas = [s];
  } else {
    targetSagas = state.sagasScope === 'owned'
      ? state.sagas.filter(s => s.ownedCount > 0 || s.isCustom)
      : state.sagas;
  }

  const missingOwnedIds = [];
  const seen = new Set();
  targetSagas.forEach(s => {
    (s.entries || []).forEach(e => {
      if (e.owned && e.entryId && !seen.has(e.entryId)) {
        const lib = state.libraryItems.find(x => x.entryId === e.entryId);
        const hasCov = Boolean((lib && lib.hasCover) || e.hasCover);
        if (!hasCov) {
          seen.add(e.entryId);
          missingOwnedIds.push(e.entryId);
        }
      }
    });
  });

  const capped = missingOwnedIds.slice(0, sagaId ? 40 : 32);
  const btnDetail = $('#saga-fill-covers-btn');
  const btnGal = $('#sagas-gallery-fill-btn');
  [btnDetail, btnGal].forEach(b => { if (b) { b.disabled = true; b.textContent = 'Buscando arte…'; } });

  await ensureSagaArt(sagaId ? [sagaId] : null);
  if (sagaId && targetSagas[0]) {
    await resolveSagaGhostCovers(targetSagas[0]);
  }

  if (!capped.length) {
    [btnDetail, btnGal].forEach(b => { if (b) { b.disabled = false; b.textContent = 'Rellenar carátulas'; } });
    toast('Carátulas y arte de saga actualizados', 'success');
    if (state.view === 'sagas') {
      if (state.activeSagaId) openSagaDetail(state.activeSagaId);
      else renderSagasGallery();
    }
    return;
  }

  const sBanner = $('#sagas-cover-sync-banner');
  if (sBanner) {
    sBanner.hidden = false;
    $('#sagas-cover-sync-title').textContent = `Rellenando ${capped.length} carátula(s) de Sagas…`;
    $('#sagas-cover-sync-counts').textContent = `0 / ${capped.length}`;
    $('#sagas-cover-sync-fill').style.width = '6%';
    $('#sagas-cover-sync-current').textContent = 'Descargando portadas reales…';
  }
  try {
    await api('/api/v1/covers/sync', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ limit: capped.length, platform: '', entryIds: capped, retryMisses: true })
    });
    toast(`Rellenando ${capped.length} carátula(s) de Sagas…`, 'info');
    pollCoverSync();
  } catch (err) {
    toast('Error sincronizando carátulas de Saga: ' + err.message, 'error');
    if (sBanner) sBanner.hidden = true;
    [btnDetail, btnGal].forEach(b => { if (b) { b.disabled = false; b.textContent = 'Rellenar carátulas'; } });
  }
}

export function renderSagasGallery() {
  const gallery = $('#sagas-gallery');
  const detail = $('#saga-detail');
  if (!gallery) return;
  state.activeSagaId = null;
  if (detail) detail.hidden = true;
  gallery.hidden = false;

  $('#sagas-scope-owned')?.setAttribute('aria-pressed', String(state.sagasScope === 'owned'));
  $('#sagas-scope-owned')?.classList.toggle('btn-secondary', state.sagasScope === 'owned');
  $('#sagas-scope-owned')?.classList.toggle('btn-ghost', state.sagasScope !== 'owned');
  $('#sagas-scope-all')?.setAttribute('aria-pressed', String(state.sagasScope === 'all'));
  $('#sagas-scope-all')?.classList.toggle('btn-secondary', state.sagasScope === 'all');
  $('#sagas-scope-all')?.classList.toggle('btn-ghost', state.sagasScope !== 'all');

  const q = ($('#sagas-search')?.value || '').trim().toLowerCase();
  let filtered = state.sagasScope === 'owned'
    ? state.sagas.filter(s => s.ownedCount > 0 || s.isCustom)
    : [...state.sagas];
  if (q) {
    filtered = filtered.filter(s => `${s.name} ${s.tagline} ${s.history || ''}`.toLowerCase().includes(q));
  }

  if ($('#sagas-count-label')) {
    const scopeLabel = state.sagasScope === 'owned' ? 'en tu bóveda' : 'investigadas';
    $('#sagas-count-label').textContent = `${filtered.length} saga(s) ${q ? 'filtrada(s)' : scopeLabel}`;
  }

  if (!filtered.length) {
    gallery.innerHTML = '<div class="empty-card" style="grid-column:1/-1;"><h3>Sin sagas coincidentes</h3><p>Prueba otro término, cambia a "Catálogo completo" o crea una saga con "+ Nueva saga".</p></div>';
    return;
  }

  if (!state.sagaArtResolved && filtered.some(s => !s.heroBannerUrl && !s.isCustom)) {
    ensureSagaArt();
  }

  const frag = document.createDocumentFragment();
  filtered.forEach(saga => {
    const pct = saga.totalCount ? Math.min(100, Math.round((saga.ownedCount * 100) / saga.totalCount)) : 0;
    const card = document.createElement('button');
    card.type = 'button';
    card.className = 'saga-card';
    card.style.setProperty('--saga-accent', saga.accent || '#FFB000');
    const bgArt = saga.heroBannerUrl || saga.bannerCoverUrl || '';
    const keyArt = saga.keyArtUrl || saga.bannerCoverUrl || '';
    const consoleChips = (saga.consoles || []).slice(0, 4).map(ck => {
      const cm = consoleMeta(ck);
      return `<span class="sys-chip" style="--chip-color:${cm.color};color:${cm.color};">${consoleIconSvg(ck, 11)}<span>${escapeHtml(cm.label)}</span></span>`;
    }).join('');
    const firstCk = (saga.consoles && saga.consoles[0]) || 'snes';
    const fallbackThumb = buildCoverSvg(saga.name, firstCk);
    const keyOverlay = keyArt
      ? `<img src="${escapeHtml(keyArt)}" decoding="async" onerror="this.hidden=true" alt="Arte clave de ${escapeHtml(saga.name)}">`
      : '';
    card.innerHTML = `
      <div class="saga-card-art">
        ${bgArt ? `<img class="saga-card-art-bg" src="${escapeHtml(bgArt)}" decoding="async" onerror="this.hidden=true" alt="">` : ''}
        <div style="position:relative;z-index:2;padding:10px 14px;display:flex;align-items:center;justify-content:space-between;gap:8px;">
          <span class="mini-badge" style="background:rgba(10,11,22,0.82);border-color:${escapeHtml(saga.accent || '#FFB000')}77;color:${escapeHtml(saga.accent || '#FFB000')};">
            ${saga.isCustom ? 'Personalizada' : (saga.foundedYear ? `Desde ${saga.foundedYear}` : 'Franquicia')}
          </span>
        </div>
        <div class="saga-card-keywrap">
          ${fallbackThumb}
          ${keyOverlay}
        </div>
      </div>
      <div class="saga-card-body">
        <div class="saga-card-content" style="padding-right:0;">
          <div style="display:flex;align-items:baseline;justify-content:space-between;gap:8px;">
            <h3 class="saga-card-title">${escapeHtml(saga.name)}</h3>
            <span class="mono" style="font-size:11.5px;color:var(--text-secondary);white-space:nowrap;">${saga.ownedCount}/${saga.totalCount}</span>
          </div>
          <p class="saga-card-tag">${escapeHtml(saga.tagline)}</p>
        </div>
        <div class="saga-card-foot">
          <div style="display:flex;flex-wrap:wrap;gap:6px;">${consoleChips}</div>
          <div class="progress-track"><div class="progress-fill" style="width:${pct}%;background:${escapeHtml(saga.accent || 'var(--accent)')};"></div></div>
        </div>
      </div>
    `;
    card.addEventListener('mouseenter', () => {
      if (bgArt) actions.setAmbientBackdrop?.(bgArt, saga.accent || '#1E8E63');
    });
    card.addEventListener('click', () => openSagaDetail(saga.id));
    frag.appendChild(card);
  });
  gallery.replaceChildren(frag);
}

export function renderSagaHero(saga) {
  const banner = $('#saga-detail-banner');
  if (!banner || !saga) return;
  banner.style.setProperty('--saga-accent', saga.accent || '#FFB000');
  const bgUrl = saga.heroBannerUrl || saga.bannerCoverUrl || '';
  const keyUrl = saga.keyArtUrl || saga.bannerCoverUrl || '';
  const firstCk = (saga.consoles && saga.consoles[0]) || 'snes';
  const pct = saga.totalCount ? Math.min(100, Math.round((saga.ownedCount * 100) / saga.totalCount)) : 0;
  const bgHtml = bgUrl ? `<div class="saga-card-bg" aria-hidden="true"><img src="${escapeHtml(bgUrl)}" decoding="async" onerror="this.hidden=true" alt=""></div>` : '';
  const metaParts = [];
  if (saga.foundedYear) metaParts.push(`Desde ${saga.foundedYear}`);
  if (saga.creator) metaParts.push(saga.creator);
  metaParts.push(`${saga.ownedCount} de ${saga.totalCount} juegos en tu bóveda`);
  const consoleChips = (saga.consoles || []).slice(0, 5).map(ck => {
    const cm = consoleMeta(ck);
    return `<span class="sys-chip" style="--chip-color:${cm.color};color:${cm.color};">${consoleIconSvg(ck, 11)}<span>${escapeHtml(cm.label)}</span></span>`;
  }).join('');

  banner.innerHTML = `
    ${bgHtml}
    <div style="position:relative;z-index:1;display:flex;flex-direction:column;gap:9px;min-width:0;">
      <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;">
        <span class="mini-badge" style="border-color:${escapeHtml(saga.accent || '#FFB000')}77;color:${escapeHtml(saga.accent || '#FFB000')};">
          ${escapeHtml(metaParts[0] || '')}
        </span>
        <span class="mono" style="font-size:12px;color:var(--text-secondary);">${escapeHtml(metaParts.slice(1).join(' · '))}</span>
      </div>
      <h2 style="margin:0;font-size:clamp(22px,2.8vw,30px);font-weight:700;letter-spacing:-0.01em;">${escapeHtml(saga.name)}</h2>
      <p style="margin:0;color:var(--text-secondary);font-size:13.8px;max-width:66ch;line-height:1.45;">${escapeHtml(saga.tagline)}</p>
      <div style="display:flex;flex-wrap:wrap;gap:6px;">${consoleChips}</div>
      <div style="display:flex;align-items:center;gap:10px;max-width:340px;">
        <div class="progress-track" style="flex:1;"><div class="progress-fill" style="width:${pct}%;background:${escapeHtml(saga.accent || 'var(--accent)')};"></div></div>
        <span class="mono" style="font-size:11.5px;color:var(--accent);">${pct}%</span>
      </div>
    </div>
    <div class="saga-hero-keyart" aria-hidden="true">
      ${buildCoverSvg(saga.name, firstCk)}
      ${keyUrl ? `<img src="${escapeHtml(keyUrl)}" decoding="async" onerror="this.hidden=true" alt="Ilustración principal de ${escapeHtml(saga.name)}">` : ''}
    </div>
  `;

  const histBox = $('#saga-history-container');
  if (histBox) {
    if (saga.history) {
      histBox.hidden = false;
      histBox.innerHTML = `
        <strong style="font-size:13px;color:${escapeHtml(saga.accent || '#FFB000')};text-transform:uppercase;letter-spacing:0.06em;">Sobre la saga</strong>
        <p>${escapeHtml(saga.history)}</p>
      `;
    } else {
      histBox.hidden = true;
    }
  }
}

export function openSagaDetail(sagaId) {
  const saga = state.sagas.find(s => s.id === sagaId);
  if (!saga) { renderSagasGallery(); return; }
  state.activeSagaId = sagaId;
  $('#sagas-gallery').hidden = true;
  $('#saga-detail').hidden = false;

  const delBtn = $('#saga-delete-custom-btn');
  if (delBtn) delBtn.hidden = !saga.isCustom;

  syncSagasFromLibrary();
  renderSagaHero(saga);
  renderSagaTimeline(saga);

  if (!saga.isCustom && (!saga.heroBannerUrl || !saga.keyArtUrl)) {
    ensureSagaArt([saga.id]);
  }
  if (!saga.isCustom && (saga.entries || []).some(e => !e.owned && !e.hasCover)) {
    resolveSagaGhostCovers(saga);
  }
}

export function renderSagaTimeline(saga) {
  const list = $('#saga-timeline');
  if (!list) return;
  const rows = state.sagaOwnedOnly ? saga.entries.filter(e => e.owned) : saga.entries;
  if (!rows.length) {
    list.innerHTML = '<div class="empty-card"><h3>Sin juegos propios en esta vista</h3><p>Cambia a "Cronología completa" para ver los títulos faltantes y buscarlos en MiNERVA.</p></div>';
    return;
  }
  const byLibId = new Map(state.libraryItems.map(x => [x.entryId, x]));
  const frag = document.createDocumentFragment();
  rows.forEach(entry => {
    const meta = consoleMeta(entry.consoleKey);
    const el = document.createElement('div');
    el.className = 'saga-entry' + (entry.owned ? '' : ' is-missing');
    el.style.setProperty('--saga-accent', saga.accent || meta.color);

    const live = entry.owned && entry.entryId ? byLibId.get(entry.entryId) : null;
    const ghostKey = `saga:${saga.id}:${String(entry.title || '').toLowerCase()}`;
    const coverUrl = (live && live.hasCover && live.coverUrl)
      || (entry.hasCover && entry.coverUrl)
      || state.sagaGhostCovers[ghostKey]
      || '';
    const fallbackSvg = buildCoverSvg(entry.matchedTitle || entry.title, entry.consoleKey);
    const thumbHtml = coverUrl
      ? `${fallbackSvg}<img class="cover-photo" src="${escapeHtml(coverUrl)}" decoding="async" onerror="this.hidden=true" alt="Carátula de ${escapeHtml(entry.title)}">`
      : fallbackSvg;

    const eraTag = entry.era ? `<span class="mini-badge">${escapeHtml(entry.era)}</span>` : '';
    const editionsTag = entry.variantCount > 1 ? `<span class="mono" style="font-size:10.5px;color:var(--text-muted);">(${entry.variantCount} ediciones)</span>` : '';
    const romDiffNote = entry.matchedTitle ? ` · ROM: ${escapeHtml(entry.matchedTitle)}` : '';

    el.innerHTML = `
      <span class="saga-order-num">${entry.isExtra ? 'EX' : String(entry.order).padStart(2, '0')}</span>
      <div class="saga-entry-thumb">${thumbHtml}</div>
      <div style="min-width:0;display:flex;flex-direction:column;gap:4px;">
        <div style="display:flex;align-items:center;gap:7px;flex-wrap:wrap;">
          <strong style="font-size:14.5px;color:var(--text-primary);">${escapeHtml(entry.title)}</strong>
          ${entry.year ? `<span class="mini-badge">${entry.year}</span>` : ''}
          ${eraTag}
          <span class="sys-chip" style="--chip-color:${meta.color};color:${meta.color};">${consoleIconSvg(entry.consoleKey, 11)}<span>${escapeHtml(meta.label)}</span></span>
          ${entry.hasSpanishPatch ? '<span class="verify-pill verified">[ES] Parcheado</span>' : ''}
          ${editionsTag}
        </div>
        <span class="mono" style="font-size:11.5px;color:var(--text-secondary);">
          ${entry.owned ? `En tu bóveda · ${entry.playCount ? `Jugado ${entry.playCount}x` : 'Listo para jugar'}${romDiffNote}` : 'Falta en tu bóveda — encuéntralo con 1 clic en MiNERVA'}
        </span>
      </div>
      <div class="saga-entry-actions" style="display:flex;gap:8px;flex-wrap:wrap;">
        ${entry.owned
          ? `<button class="btn btn-primary btn-sm saga-open-btn" type="button">Abrir ficha</button>
             <a class="btn btn-secondary btn-sm" href="/api/v1/library/${encodeURIComponent(entry.entryId)}/download" download>⬇ Bajar</a>`
          : `<button class="btn btn-secondary btn-sm saga-find-minerva-btn" type="button">+ Buscar en MiNERVA</button>`}
      </div>
    `;
    if (entry.owned) {
      el.querySelector('.saga-open-btn').addEventListener('click', () => {
        const real = state.libraryItems.find(x => x.entryId === entry.entryId);
        if (real) actions.openLibraryDrawer(real);
      });
    } else {
      el.querySelector('.saga-find-minerva-btn').addEventListener('click', () => {
        actions.switchView('catalog');
        $('#minerva-query').value = entry.minervaQuery || entry.title;
        if (entry.consoleKey) $('#minerva-platform').value = entry.consoleKey;
        actions.performMinervaSearch();
      });
    }
    frag.appendChild(el);
  });
  list.replaceChildren(frag);
}

export function openSagaModal(item = null) {
  state.sagaAddEntry = item;
  $('#saga-add-sub').textContent = item
    ? `Añadir "${item.cleanTitle || item.title}" a una saga existente o crear una nueva.`
    : 'Crea una colección de franquicia con orden y color personalizado.';
  const selWrap = $('#saga-existing-wrap');
  const sel = $('#saga-existing-select');
  if (item && state.sagas.length && sel && selWrap) {
    selWrap.hidden = false;
    sel.innerHTML = state.sagas.map(s => `<option value="${escapeHtml(s.id)}">${escapeHtml(s.name)} (${s.ownedCount})</option>`).join('');
  } else if (selWrap) {
    selWrap.hidden = true;
  }
  $('#saga-new-name').value = '';
  $('#saga-new-tagline').value = '';
  $('#saga-add-backdrop').hidden = false;
}

export function closeSagaModal() {
  const bd = $('#saga-add-backdrop');
  if (bd) bd.hidden = true;
  state.sagaAddEntry = null;
}

