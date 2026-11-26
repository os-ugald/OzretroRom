import {
  CONSOLES, SHORT_CODES, PAGE_SIZE, state, $, $$, actions,
  escapeHtml, consoleMeta, consoleIconSvg, joinLocalPath,
  resolveLocalDownloadDir, resolveTargetRemoteDir, consoleSelectOptionsHtml,
  formatBytes, hashString, wrapTitleLines, buildConsoleMotif,
  buildCoverSvg, renderCoverMarkup, toast, formatRelativeTime, findLibraryEntry,
  setAmbientBackdrop,
} from '../state.js';
import { api } from '../api-client.js';

export function buildHylianGameCard(item, subtitleText = '') {
  const meta = consoleMeta(item.consoleKey);
  const isFav = Boolean(item.favorite);
  const displayTitle = (state.libraryGrouped && item.cleanTitle) ? item.cleanTitle : item.title;
  const card = document.createElement('article');
  card.className = 'oz-game-card rom-card' + (state.selectedEntryId === item.entryId ? ' selected' : '');
  card.tabIndex = 0;
  card.setAttribute('aria-label', `${displayTitle} (${meta.full})`);
  card.innerHTML = `
    <div class="cover-frame cover-frame-34">
      ${renderCoverMarkup(item, { availability: item.availability || 'local' })}
      <button class="card-fav-corner ${isFav ? 'is-fav' : ''}" type="button" aria-label="${isFav ? 'Quitar favorito' : 'Favorito'}">${isFav ? '♥' : '♡'}</button>
    </div>
    <div class="oz-card-footer">
      <h3 class="card-title">${escapeHtml(displayTitle)}</h3>
      <div class="oz-card-meta-row">
        <span class="oz-tag">${escapeHtml(meta.label)}</span>
        <span class="oz-card-status">${item.hasSpanishPatch ? '<span class="oz-tag secondary">[ES]</span>' : ''}<span>✓</span><button class="card-more-dot" type="button" style="border:0;background:transparent;padding:0 2px;font-size:10px;color:var(--text-secondary);">•••</button></span>
      </div>
    </div>
  `;
  const triggerAmbient = () => {
    setAmbientBackdrop(item.hasCover && item.coverUrl ? item.coverUrl : '', meta.color);
  };
  card.querySelector('.card-fav-corner')?.addEventListener('click', ev => {
    ev.stopPropagation();
    actions.toggleFavorite(item.entryId, item.title);
  });
  card.querySelector('.card-more-dot')?.addEventListener('click', ev => openGameContextMenu(ev, item));
  card.addEventListener('mouseenter', triggerAmbient);
  card.addEventListener('focus', triggerAmbient);
  card.addEventListener('click', () => actions.openLibraryDrawer(item));
  card.addEventListener('contextmenu', ev => openGameContextMenu(ev, item));
  card.addEventListener('keydown', ev => {
    if (ev.key === 'Enter' || ev.key === ' ') { ev.preventDefault(); actions.openLibraryDrawer(item); }
  });
  return card;
}

export function buildFeaturedCartridgeCard(item) {
  return buildHylianGameCard(item, item.hasSpanishPatch ? '[ES] Parcheado' : '');
}

export function applyHomeSectionOrder() {
  const stack = $('#home-sections-stack');
  if (!stack) return;
  const defaultOrder = ['consoles', 'recommendations', 'featured', 'recent', 'favorites'];
  const rawOrder = Array.isArray(state.settings?.home_section_order) && state.settings.home_section_order.length
    ? state.settings.home_section_order
    : defaultOrder;
  const merged = [...rawOrder.filter(k => defaultOrder.includes(k)), ...defaultOrder.filter(k => !rawOrder.includes(k))];
  merged.forEach(key => {
    const sec = stack.querySelector(`[data-home-section="${key}"]`);
    if (sec) stack.appendChild(sec);
  });
}

export async function moveHomeSection(sectionKey, delta) {
  const defaultOrder = ['consoles', 'recommendations', 'featured', 'recent', 'favorites'];
  const cur = Array.isArray(state.settings?.home_section_order) && state.settings.home_section_order.length
    ? [...state.settings.home_section_order]
    : [...defaultOrder];
  defaultOrder.forEach(k => { if (!cur.includes(k)) cur.push(k); });
  const idx = cur.indexOf(sectionKey);
  const targetIdx = idx + delta;
  if (idx < 0 || targetIdx < 0 || targetIdx >= cur.length) return;
  [cur[idx], cur[targetIdx]] = [cur[targetIdx], cur[idx]];
  if (!state.settings) state.settings = {};
  state.settings.home_section_order = cur;
  applyHomeSectionOrder();
  try {
    await api('/api/v1/settings', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ home_section_order: cur })
    });
    toast('Orden de Inicio actualizado', 'info', { durationMs: 1800 });
  } catch (err) { /* ignore */ }
}

export async function pinHeroGame(entryId, title = '') {
  if (!state.settings) state.settings = {};
  state.heroOverrideItem = null;
  state.settings.pinned_hero_entry_id = entryId || '';
  renderHome();
  try {
    await api('/api/v1/settings', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ pinned_hero_entry_id: entryId || '' })
    });
    toast(entryId ? `Fijado en Inicio: ${title}` : 'Juego principal restablecido', 'success');
  } catch (err) {
    toast('No se pudo guardar juego principal: ' + err.message, 'error');
  }
}

export function closeGameContextMenu() {
  const menu = $('#game-context-menu');
  if (menu) menu.hidden = true;
}

export function openGameContextMenu(ev, item) {
  if (!item) return;
  ev.preventDefault();
  ev.stopPropagation();
  const menu = $('#game-context-menu');
  if (!menu) return;
  const isPinned = state.settings?.pinned_hero_entry_id === item.entryId;
  const isFav = Boolean(item.favorite);
  menu.innerHTML = `
    <div class="context-menu-head">${escapeHtml(item.cleanTitle || item.title)}</div>
    <button class="context-menu-item" type="button" role="menuitem" data-ctx="pin">${isPinned ? '★ Quitar de juego principal' : '★ Fijar como juego principal'}</button>
    <button class="context-menu-item" type="button" role="menuitem" data-ctx="cover">🖼 Cambiar carátula…</button>
    <button class="context-menu-item" type="button" role="menuitem" data-ctx="saga">📚 Añadir a saga…</button>
    <button class="context-menu-item" type="button" role="menuitem" data-ctx="fav">${isFav ? '☆ Quitar de favoritas' : '☆ Marcar como favorita'}</button>
    <button class="context-menu-item" type="button" role="menuitem" data-ctx="played">▶ Registrar partida</button>
    <div class="context-menu-sep"></div>
    <button class="context-menu-item" type="button" role="menuitem" data-ctx="drawer">Abrir ficha completa</button>
  `;
  menu.hidden = false;
  const rectW = Math.max(240, menu.offsetWidth || 240);
  const rectH = Math.max(230, menu.offsetHeight || 240);
  const x = Math.min(Math.max(12, ev.clientX), Math.max(12, window.innerWidth - rectW - 12));
  const y = Math.min(Math.max(12, ev.clientY), Math.max(12, window.innerHeight - rectH - 12));
  menu.style.left = `${x}px`;
  menu.style.top = `${y}px`;

  menu.querySelector('[data-ctx="pin"]').addEventListener('click', () => {
    closeGameContextMenu();
    pinHeroGame(isPinned ? '' : item.entryId, item.cleanTitle || item.title);
  });
  menu.querySelector('[data-ctx="cover"]').addEventListener('click', () => {
    closeGameContextMenu();
    openCoverPicker(item);
  });
  menu.querySelector('[data-ctx="saga"]').addEventListener('click', () => {
    closeGameContextMenu();
    actions.openSagaModal(item);
  });
  menu.querySelector('[data-ctx="fav"]').addEventListener('click', () => {
    closeGameContextMenu();
    actions.toggleFavorite(item.entryId, item.title);
  });
  menu.querySelector('[data-ctx="played"]').addEventListener('click', () => {
    closeGameContextMenu();
    actions.markPlayed(item.entryId, item.title);
  });
  menu.querySelector('[data-ctx="drawer"]').addEventListener('click', () => {
    closeGameContextMenu();
    actions.openLibraryDrawer(item);
  });
}

export function openCoverPicker(item) {
  if (!item) return;
  state.coverPickerEntry = item;
  $('#cover-picker-heading').textContent = item.cleanTitle || item.title;
  $('#cover-picker-preview').innerHTML = renderCoverMarkup(item);
  $('#cover-picker-file').value = '';
  $('#cover-picker-url').value = '';
  $('#cover-picker-status').textContent = '';
  $('#cover-picker-backdrop').hidden = false;
}

export function closeCoverPicker() {
  const bd = $('#cover-picker-backdrop');
  if (bd) bd.hidden = true;
  state.coverPickerEntry = null;
}

export async function saveCustomCover(payload) {
  const item = state.coverPickerEntry;
  if (!item) return;
  const statusEl = $('#cover-picker-status');
  statusEl.textContent = 'Optimizando carátula WebP…';
  try {
    const res = await api(`/api/v1/covers/custom/${encodeURIComponent(item.entryId)}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    item.hasCover = true;
    item.coverSource = 'custom';
    item.coverUrl = res.coverUrl;
    actions.syncSagasFromLibrary();
    $('#cover-picker-preview').innerHTML = renderCoverMarkup(item);
    statusEl.textContent = '✓ Carátula personalizada guardada';
    statusEl.style.color = 'var(--success)';
    toast(`Carátula actualizada: ${item.cleanTitle || item.title}`, 'success');
    actions.refreshCoverStats();
    renderHome();
    actions.applyLibraryFilters();
    if (state.view === 'sagas') {
      if (state.activeSagaId) actions.openSagaDetail(state.activeSagaId);
      else actions.renderSagasGallery();
    }
    if (state.selectedEntryId === item.entryId) actions.openLibraryDrawer(item, false);
  } catch (err) {
    statusEl.textContent = '✗ ' + err.message;
    statusEl.style.color = 'var(--danger)';
  }
}

export async function loadRecommendations(useAi = false, reroll = false) {
  if (state.recsLoading) return;
  state.recsLoading = true;
  const btn = $('#home-recs-ai-btn');
  if (btn && reroll) { btn.disabled = true; btn.textContent = 'Buscando…'; }
  if (!(state.seenRecIds instanceof Set)) state.seenRecIds = new Set();
  if (reroll) {
    state.recsOffset = (state.recsOffset || 0) + 1;
  } else {
    state.recsOffset = 0;
    state.seenRecIds.clear();
  }
  const excludeParam = reroll && state.seenRecIds.size
    ? `&exclude=${encodeURIComponent(Array.from(state.seenRecIds).slice(-90).join(','))}`
    : '';
  try {
    const res = await api(`/api/v1/recommendations?use_ai=${useAi ? 'true' : 'false'}&limit=6&offset=${state.recsOffset}&seed=${state.sessionSeed || 0}${excludeParam}`);
    if (res.cycled) state.seenRecIds.clear();
    (res.fromLibrary || []).forEach(it => { if (it?.entryId) state.seenRecIds.add(it.entryId); });
    state.recommendations = res;
    renderRecommendations();
    if (useAi && res.aiWarning) toast(res.aiWarning, 'info', { durationMs: 4200 });
    else if (reroll) toast('Nuevas sugerencias cargadas', 'success');
  } catch (err) {
    if (useAi || reroll) toast('Error obteniendo recomendaciones: ' + err.message, 'error');
  } finally {
    state.recsLoading = false;
    if (btn) {
      btn.disabled = false;
      const prov = state.settings?.ai_provider || 'local';
      btn.textContent = prov !== 'local' ? 'Recomendar con IA' : 'Pedir ideas';
    }
  }
}

export async function populateHeroSynopsis(hero) {
  const descEl = $('#home-hero-desc');
  if (!descEl) return;
  if (!hero) { descEl.hidden = true; descEl.textContent = ''; return; }
  if (hero.description) {
    descEl.textContent = hero.description;
    descEl.hidden = false;
    return;
  }
  if (state.lastHeroDescId === hero.entryId) return;
  state.lastHeroDescId = hero.entryId;
  descEl.hidden = true;
  try {
    const data = await api(`/api/v1/library/${encodeURIComponent(hero.entryId)}/description`);
    hero.description = data.description || '';
    hero.releaseYear = data.releaseYear || '';
    hero.genre = data.genre || '';
    hero.developer = data.developer || '';
    if (state.heroItem?.entryId === hero.entryId && hero.description) {
      descEl.textContent = hero.description;
      descEl.hidden = false;
    }
  } catch (e) { /* ignore */ }
}

export async function openMoodPicker(mood = state.moodMode, offset = state.moodOffset) {
  state.moodMode = mood;
  state.moodOffset = offset;
  if (!state.seenMoodIdsByMode || typeof state.seenMoodIdsByMode !== 'object') state.seenMoodIdsByMode = {};
  if (!(state.seenMoodIdsByMode[mood] instanceof Set)) state.seenMoodIdsByMode[mood] = new Set();
  const seenSet = state.seenMoodIdsByMode[mood];
  const excludeParam = seenSet.size
    ? `&exclude=${encodeURIComponent(Array.from(seenSet).slice(-120).join(','))}`
    : '';
  $('#mood-picker-backdrop').hidden = false;
  $('#mood-close-btn')?.focus({ preventScroll: true });
  $$('.mood-choice-btn').forEach(b => {
    const active = b.dataset.mood === mood;
    b.classList.toggle('btn-primary', active);
    b.classList.toggle('btn-secondary', !active);
  });
  const grid = $('#mood-picks-grid');
  grid.innerHTML = '<div class="home-shelf-empty" style="grid-column:1/-1;"><span>Eligiendo 3 títulos perfectos para este momento…</span></div>';
  try {
    const res = await api(`/api/v1/recommendations/mood?mood=${encodeURIComponent(mood)}&offset=${offset}${excludeParam}`);
    if (res.cycled) seenSet.clear();
    (res.picks || []).forEach(p => { if (p?.entryId) seenSet.add(p.entryId); });
    const countHint = res.totalMatches > 3 ? ` · ${res.totalMatches} opciones` : '';
    $('#mood-summary-label').textContent = `${res.moodLabel || mood} · Duración aprox: ${res.durationHint || 'Variable'}${countHint}`;
    renderMoodPicks(res);
  } catch (err) {
    grid.innerHTML = `<div class="home-shelf-empty" style="grid-column:1/-1;"><span>${escapeHtml(err.message)}</span></div>`;
  }
}

export function closeMoodPicker() {
  const bd = $('#mood-picker-backdrop');
  if (bd) bd.hidden = true;
}

export function renderMoodPicks(res) {
  const grid = $('#mood-picks-grid');
  const picks = Array.isArray(res?.picks) ? res.picks : [];
  if (!picks.length) {
    grid.innerHTML = '<div class="home-shelf-empty" style="grid-column:1/-1;"><span>No hay ROMs disponibles en esta categoría.</span></div>';
    return;
  }
  const frag = document.createDocumentFragment();
  picks.forEach(item => {
    const meta = consoleMeta(item.consoleKey);
    const card = document.createElement('article');
    card.className = 'mood-pick-card';
    card.style.setProperty('--card-accent', meta.color);
    card.style.setProperty('--card-glow', meta.color + '55');
    card.innerHTML = `
      <div style="display:flex;gap:12px;align-items:center;">
        <div class="rec-thumb" style="width:68px;">${renderCoverMarkup(item)}</div>
        <div style="display:flex;flex-direction:column;gap:5px;min-width:0;">
          <span class="sys-chip" style="--chip-color:${meta.color};color:${meta.color};align-self:flex-start;">${consoleIconSvg(item.consoleKey, 12)}<span>${escapeHtml(meta.label)}</span></span>
          <h3 class="card-title" style="margin:0;">${escapeHtml(item.cleanTitle || item.title)}</h3>
          <span class="mini-badge" style="align-self:flex-start;">⏱ ${escapeHtml(item.durationHint || '15–30 min')}</span>
        </div>
      </div>
      <p class="rec-reason">${escapeHtml(item.description || item.moodReason || '')}</p>
      <div style="display:flex;gap:8px;flex-wrap:wrap;margin-top:auto;">
        <button class="btn btn-primary btn-sm mood-open-btn" type="button">Abrir ficha</button>
        <a class="btn btn-secondary btn-sm" href="/api/v1/library/${encodeURIComponent(item.entryId)}/download" download>⬇ Descargar</a>
      </div>
    `;
    card.querySelector('.mood-open-btn').addEventListener('click', () => {
      closeMoodPicker();
      const real = state.libraryItems.find(x => x.entryId === item.entryId) || item;
      actions.openLibraryDrawer(real);
    });
    frag.appendChild(card);
  });
  grid.replaceChildren(frag);
}


export function renderRecommendations() {
  const grid = $('#home-recs-grid');
  const discRow = $('#home-discover-row');
  const subEl = $('#home-recs-sub');
  const badge = $('#home-recs-engine-badge');
  const gotwBtn = $('#home-hero-gotw-btn');
  if (!grid) return;

  const recs = state.recommendations;
  if (!recs) {
    grid.innerHTML = '<div class="home-shelf-empty" style="width:100%;"><span>Analizando tus gustos y partidas para sugerir juegos…</span></div>';
    return;
  }
  if (subEl) subEl.textContent = recs.summary || 'Basado en tus juegos favoritos y actividad reciente';
  if (badge) { badge.hidden = true; badge.textContent = ''; }

  if (gotwBtn && recs.gameOfTheWeek?.entryId) {
    gotwBtn.hidden = false;
    gotwBtn.title = recs.gameOfTheWeek.reason || 'Ver juego de la semana';
  }

  const libItems = Array.isArray(recs.fromLibrary) ? recs.fromLibrary : [];
  if (libItems.length) {
    const frag = document.createDocumentFragment();
    libItems.forEach(item => {
      const meta = consoleMeta(item.consoleKey);
      const isFav = Boolean(item.favorite);
      const card = document.createElement('article');
      card.className = 'rec-card';
      card.style.setProperty('--card-accent', meta.color);
      card.style.setProperty('--card-glow', meta.color + '48');
      card.style.setProperty('--card-glow-strong', meta.color + '80');
      card.tabIndex = 0;
      card.setAttribute('aria-label', `${item.cleanTitle || item.title} · ${item.recReason || ''}`);
      card.innerHTML = `
        <div class="rec-card-top">
          <div class="rec-thumb">
            ${renderCoverMarkup(item)}
            <div class="cover-top-bar">
              <span class="sys-chip sys-chip-icon" style="--chip-color:${meta.color};color:${meta.color};" title="${escapeHtml(meta.full)}">${consoleIconSvg(item.consoleKey, 14)}</span>
              <button class="fav-btn ${isFav ? 'is-fav' : ''}" type="button" aria-label="${isFav ? 'Quitar de favoritas' : 'Marcar como favorita'}: ${escapeHtml(item.title)}" aria-pressed="${isFav}">
                <svg viewBox="0 0 24 24" fill="${isFav ? 'currentColor' : 'none'}" stroke="currentColor" stroke-width="2" aria-hidden="true"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
              </button>
            </div>
          </div>
          <div class="rec-info">
            <h3 class="card-title">${escapeHtml(item.cleanTitle || item.title)}</h3>
          </div>
        </div>
        <p class="rec-reason">${escapeHtml(item.recReason || 'Recomendado de tu colección')}</p>
      `;
      card.querySelector('.fav-btn')?.addEventListener('click', ev => {
        ev.stopPropagation();
        actions.toggleFavorite(item.entryId, item.title);
      });
      card.addEventListener('click', () => {
        const real = state.libraryItems.find(x => x.entryId === item.entryId) || item;
        actions.openLibraryDrawer(real);
      });
      card.addEventListener('contextmenu', ev => {
        const real = state.libraryItems.find(x => x.entryId === item.entryId) || item;
        openGameContextMenu(ev, real);
      });
      card.addEventListener('keydown', ev => {
        if (ev.key === 'Enter' || ev.key === ' ') {
          ev.preventDefault();
          const real = state.libraryItems.find(x => x.entryId === item.entryId) || item;
          actions.openLibraryDrawer(real);
        }
      });
      frag.appendChild(card);
    });
    grid.replaceChildren(frag);
  } else {
    grid.innerHTML = '<div class="home-shelf-empty" style="width:100%;"><span>Registra partidas o marca favoritos para activar recomendaciones personalizadas.</span></div>';
  }

  const discover = Array.isArray(recs.discoverCatalog) ? recs.discoverCatalog : [];
  if (discRow) {
    if (discover.length) {
      discRow.hidden = false;
      const dFrag = document.createDocumentFragment();
      const label = document.createElement('span');
      label.className = 'mono';
      label.style.cssText = 'font-size:11.5px;color:var(--text-muted);flex-shrink:0;';
      label.textContent = 'Para descubrir en MiNERVA:';
      dFrag.appendChild(label);
      const pillsWrap = document.createElement('div');
      pillsWrap.className = 'discover-pills-wrap';
      discover.forEach(d => {
        const meta = consoleMeta(d.consoleKey);
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'discover-pill';
        btn.style.setProperty('--sys-color', meta.color);
        btn.title = d.reason || d.title;
        btn.innerHTML = `${consoleIconSvg(d.consoleKey, 13)}<span>${escapeHtml(d.title)}</span><span class="lib-pill-count" style="color:${meta.color};">${escapeHtml(meta.label)}</span>`;
        btn.addEventListener('click', () => {
          actions.switchView('catalog');
          $('#minerva-query').value = d.minervaQuery || d.title;
          if (d.consoleKey) $('#minerva-platform').value = d.consoleKey;
          actions.performMinervaSearch();
        });
        pillsWrap.appendChild(btn);
      });
      dFrag.appendChild(pillsWrap);
      discRow.replaceChildren(dFrag);
    } else {
      discRow.hidden = true;
      discRow.replaceChildren();
    }
  }
}


export function featuredQualityScore(item, seed) {
  let score = 0;
  if (item.hasCover) score += 32;
  if (item.favorite) score += 40;
  const plays = Number(item.playCount || 0);
  if (plays > 0) score += Math.min(plays, 8) * 4;
  if (item.hasMetadata) score += 10;
  if (item.description || item.releaseYear) score += 8;
  const rawTitle = String(item.title || '').toLowerCase();
  if (/\((?:unl|pirate|proto|prototype|beta|demo|sample)\)|mahjong|super jogos|\b16t\b/i.test(rawTitle) || rawTitle.split(' + ').length >= 3) {
    score -= 42;
  }
  // Deterministic per-launch tie-breaker so Destacados rotates across launches while staying curated
  const idStr = String(item.entryId || item.title || '');
  let h = (Number(seed) || 0) * 2654435761;
  for (let i = 0; i < idStr.length; i++) h = ((h * 31) ^ idStr.charCodeAt(i)) >>> 0;
  return score + (h % 1000) / 1000;
}

export function pickDiverseFeatured(base, covered, limit = 12) {
  const pool = [];
  const seen = new Set();
  const seed = state.featuredSeed || 1;
  const rankedCovered = [...covered].sort((a, b) => featuredQualityScore(b, seed) - featuredQualityScore(a, seed));
  const rankedRest = base.filter(x => !x.hasCover).sort((a, b) => featuredQualityScore(b, seed) - featuredQualityScore(a, seed));
  const addByPlatformRoundRobin = source => {
    const buckets = new Map();
    source.forEach(it => {
      const arr = buckets.get(it.consoleKey) || [];
      arr.push(it);
      buckets.set(it.consoleKey, arr);
    });
    let added = true;
    while (pool.length < limit && added) {
      added = false;
      for (const arr of buckets.values()) {
        if (!arr.length) continue;
        const it = arr.shift();
        if (!seen.has(it.entryId)) {
          seen.add(it.entryId);
          pool.push(it);
          added = true;
          if (pool.length >= limit) break;
        }
      }
    }
  };
  addByPlatformRoundRobin(rankedCovered);
  if (pool.length < limit) addByPlatformRoundRobin(rankedRest);
  return pool;
}

export function renderHome() {
  const base = actions.visibleBaseItems();
  const recent = base
    .filter(x => x.lastPlayedAt)
    .sort((a, b) => Date.parse(b.lastPlayedAt || 0) - Date.parse(a.lastPlayedAt || 0));
  const favs = base.filter(x => x.favorite).sort((a, b) => a.title.localeCompare(b.title));
  const covered = base.filter(x => x.hasCover);
  const consoleMap = new Map();
  base.forEach(item => {
    const cur = consoleMap.get(item.consoleKey) || { key: item.consoleKey, count: 0, bytes: 0 };
    cur.count += 1;
    cur.bytes += item.size || 0;
    consoleMap.set(item.consoleKey, cur);
  });
  const consolesSorted = [...consoleMap.values()].sort((a, b) => b.count - a.count);

  const pinnedId = state.settings?.pinned_hero_entry_id || '';
  const pinnedItem = pinnedId ? base.find(x => x.entryId === pinnedId) : null;
  const qrPayloadHero = state.homeData?.quickResumeHero;
  const qrFromBase = qrPayloadHero
    ? (base.find(x => x.entryId === qrPayloadHero.entryId) || qrPayloadHero)
    : base.find(x => x.quickResume && typeof x.quickResume === 'object');
  const hero = state.heroOverrideItem || pinnedItem || qrFromBase || recent[0] || favs[0] || covered[0] || base[0] || null;
  state.heroItem = hero;
  const heroCard = $('#home-hero-card');
  const heroBgImg = $('#home-hero-bg-img');
  const unpinBtn = $('#home-hero-unpin-btn');
  if (hero && heroCard) {
    const meta = consoleMeta(hero.consoleKey);
    heroCard.style.setProperty('--hero-glow', 'rgba(30, 142, 99, 0.34)');
    heroCard.style.setProperty('--hero-glow-strong', 'rgba(217, 182, 78, 0.45)');
    if (heroBgImg) {
      if (hero.hasCover && hero.coverUrl) {
        heroBgImg.src = hero.coverUrl;
        heroBgImg.hidden = false;
        setAmbientBackdrop(hero.coverUrl, meta.color);
      } else {
        heroBgImg.hidden = true;
      }
    }
    const heroSysTag = $('#home-hero-sys-tag');
    if (heroSysTag) {
      heroSysTag.hidden = false;
      heroSysTag.textContent = meta.label;
    }
    const qrInfo = hero.quickResume || qrPayloadHero?.quickResume;
    const eyebrowText = qrInfo
      ? `CONTINUAR JUGANDO · SLOT ${qrInfo.slot !== undefined ? qrInfo.slot : 0}`
      : 'CONTINUAR JUGANDO';
    if ($('#home-hero-eyebrow')) $('#home-hero-eyebrow').textContent = eyebrowText;
    if ($('#home-hero-title')) $('#home-hero-title').textContent = hero.cleanTitle || hero.title;
    if ($('#home-hero-meta')) {
      $('#home-hero-meta').innerHTML = `
        <span>${escapeHtml(meta.full)}</span>
        ${hero.releaseYear ? `<span>· ${escapeHtml(hero.releaseYear)}</span>` : ''}
        ${hero.playCount ? `<span>· ${hero.playCount}x · ${formatRelativeTime(hero.lastPlayedAt)}</span>` : ''}
      `;
    }
    if ($('#home-hero-open-btn')) $('#home-hero-open-btn').hidden = false;
    if ($('#home-hero-play-btn')) $('#home-hero-play-btn').hidden = true;
    if (unpinBtn) unpinBtn.hidden = true;
    populateHeroSynopsis(hero);
  } else if (heroCard) {
    if (heroBgImg) heroBgImg.hidden = true;
    if ($('#home-hero-sys-tag')) $('#home-hero-sys-tag').hidden = true;
    if ($('#home-hero-eyebrow')) $('#home-hero-eyebrow').textContent = 'OZ GAME VAULT';
    if ($('#home-hero-title')) $('#home-hero-title').textContent = 'Tu bóveda retro está lista';
    if ($('#home-hero-meta')) $('#home-hero-meta').innerHTML = '<span>Explora colecciones o añade tu carpeta de ROMs desde Buscar.</span>';
    if ($('#home-hero-open-btn')) $('#home-hero-open-btn').hidden = true;
    if (unpinBtn) unpinBtn.hidden = true;
    populateHeroSynopsis(null);
  }

  const quickConsoles = $('#home-consoles-quick-row');
  if (quickConsoles) {
    const qFrag = document.createDocumentFragment();
    consolesSorted.slice(0, 10).forEach(entry => {
      const meta = consoleMeta(entry.key);
      const chip = document.createElement('button');
      chip.type = 'button';
      chip.className = 'oz-hud-chip';
      chip.innerHTML = `${consoleIconSvg(entry.key, 14)}<span>${escapeHtml(meta.label)}</span>`;
      chip.addEventListener('click', () => {
        state.consoleFilter = entry.key;
        state.stateFilter = 'all';
        actions.openExploreConsoleLibrary(entry.key);
      });
      qFrag.appendChild(chip);
    });
    quickConsoles.replaceChildren(qFrag);
  }

  const aiSearchGrid = $('#search-ai-grid');
  if (aiSearchGrid) {
    const aiItems = (favs.length ? favs : covered).slice(0, 4);
    aiSearchGrid.replaceChildren(...aiItems.map(it => buildHylianGameCard(it)));
  }

  const recentGrid = $('#home-recent-grid');
  const recentEmpty = $('#home-recent-empty');
  if ($('#home-recent-count')) $('#home-recent-count').textContent = `${recent.length}`;
  if (recentGrid && recentEmpty) {
    if (recent.length) {
      recentEmpty.hidden = true;
      recentGrid.hidden = false;
      recentGrid.replaceChildren(...recent.slice(0, 12).map(it => buildHylianGameCard(it, formatRelativeTime(it.lastPlayedAt))));
    } else {
      recentGrid.replaceChildren();
      recentGrid.hidden = true;
      recentEmpty.hidden = false;
    }
  }

  const favGrid = $('#home-favorites-grid');
  const favEmpty = $('#home-favorites-empty');
  if ($('#home-favs-count')) $('#home-favs-count').textContent = `${favs.length}`;
  if (favGrid && favEmpty) {
    if (favs.length) {
      favEmpty.hidden = true;
      favGrid.hidden = false;
      favGrid.replaceChildren(...favs.slice(0, 12).map(it => buildHylianGameCard(it)));
    } else {
      favGrid.replaceChildren();
      favGrid.hidden = true;
      favEmpty.hidden = false;
    }
  }

  const featPool = pickDiverseFeatured(base, covered, 12);
  const featGrid = $('#home-featured-grid');
  if (featGrid) featGrid.replaceChildren(...featPool.map(it => buildFeaturedCartridgeCard(it)));

  const conGrid = $('#home-consoles-grid');
  if ($('#home-consoles-sub')) $('#home-consoles-sub').textContent = `${consolesSorted.length} sistemas`;
  if (conGrid) {
    const conFrag = document.createDocumentFragment();
    consolesSorted.forEach(entry => {
      const meta = consoleMeta(entry.key);
      const tile = document.createElement('button');
      tile.type = 'button';
      tile.className = 'console-tile';
      tile.style.setProperty('--tile-accent', meta.color);
      tile.innerHTML = `
        <div class="console-tile-left">
          <span class="console-tile-icon">${consoleIconSvg(entry.key, 20)}</span>
          <div class="console-tile-info">
            <span class="console-tile-name">${escapeHtml(meta.full)}</span>
            <span class="console-tile-meta">${entry.count} juegos · ${formatBytes(entry.bytes)}</span>
          </div>
        </div>
        <span class="count-pill">${entry.count}</span>
      `;
      tile.addEventListener('click', () => {
        state.consoleFilter = entry.key;
        state.stateFilter = 'all';
        actions.openExploreConsoleLibrary(entry.key);
      });
      conFrag.appendChild(tile);
    });
    conGrid.replaceChildren(conFrag);
  }
}

