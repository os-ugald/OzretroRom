import {
  CONSOLES, SHORT_CODES, PAGE_SIZE, state, $, $$, actions,
  escapeHtml, consoleMeta, consoleIconSvg, joinLocalPath,
  resolveLocalDownloadDir, resolveTargetRemoteDir, consoleSelectOptionsHtml,
  formatBytes, hashString, wrapTitleLines, buildConsoleMotif,
  buildCoverSvg, renderCoverMarkup, toast, formatRelativeTime, findLibraryEntry,
  setAmbientBackdrop,
} from '../state.js';
import { api } from '../api-client.js';
import { recordActivityNotice, renderActivitySheet } from '../activity-sheet.js';

export function buildLibraryCard(item, rightPillText = '') {
  const meta = consoleMeta(item.consoleKey);
  const isFav = Boolean(item.favorite);
  const displayTitle = (state.libraryGrouped && item.cleanTitle) ? item.cleanTitle : item.title;
  const card = document.createElement('article');
  card.className = 'hylian-card rom-card' + (state.selectedEntryId === item.entryId || (item.variants || []).some(v => v.entryId === state.selectedEntryId) ? ' selected' : '');
  card.style.setProperty('--card-accent', '#1E8E63');
  card.tabIndex = 0;
  card.setAttribute('aria-label', `${displayTitle} (${meta.label})`);
  const pillLabel = rightPillText || meta.label;
  card.innerHTML = `
    <div class="cover-frame cover-frame-34">
      ${renderCoverMarkup(item, { availability: item.availability || 'local' })}
    </div>
    <div class="hylian-card-meta">
      <div class="hylian-card-title-row">
        <h3 class="card-title" title="${escapeHtml(item.title)}">${escapeHtml(displayTitle)}</h3>
        ${isFav ? '<span class="hylian-star" aria-label="Favorito">★</span>' : ''}
      </div>
      <span class="hylian-card-sub mono">${escapeHtml(pillLabel)}${item.hasSpanishPatch ? ' · [ES]' : ''}</span>
    </div>
  `;
  const triggerAmbient = () => {
    setAmbientBackdrop(item.hasCover && item.coverUrl ? item.coverUrl : '', meta.color);
  };
  card.addEventListener('mouseenter', triggerAmbient);
  card.addEventListener('focus', triggerAmbient);
  card.addEventListener('click', () => openLibraryDrawer(item));
  card.addEventListener('contextmenu', ev => openGameContextMenu(ev, item));
  card.addEventListener('keydown', ev => {
    if (ev.key === 'Enter' || ev.key === ' ') { ev.preventDefault(); openLibraryDrawer(item); }
  });
  return card;
}

export async function markPlayed(entryId, title) {
  try {
    const res = await api(`/api/v1/library/${encodeURIComponent(entryId)}/played`, { method: 'POST' });
    const { canonical, variant } = findLibraryEntry(entryId);
    if (variant) {
      variant.lastPlayedAt = res.lastPlayedAt;
      variant.playCount = res.playCount;
    }
    if (canonical && canonical !== variant) {
      canonical.lastPlayedAt = res.lastPlayedAt;
      canonical.playCount = (canonical.variants || []).reduce((acc, v) => acc + Number(v.playCount || 0), 0);
    }
    toast(`Partida registrada (${res.playCount}x): ${title}`, 'success');
    updateSidebarCounts();
    actions.renderHome();
    actions.loadRecommendations(false, false);
    applyLibraryFilters();
    if (state.selectedEntryId === entryId && canonical) openLibraryDrawer(canonical, false, entryId);
  } catch (err) {
    toast('Error registrando partida: ' + err.message, 'error');
  }
}

export async function toggleFavorite(entryId, title) {
  try {
    const res = await api(`/api/v1/library/${encodeURIComponent(entryId)}/favorite`, { method: 'POST' });
    const { canonical, variant } = findLibraryEntry(entryId);
    if (variant) variant.favorite = Boolean(res.favorite);
    if (canonical) {
      canonical.favorite = canonical.variants && canonical.variants.length
        ? canonical.variants.some(v => Boolean(v.favorite))
        : Boolean(res.favorite);
    }
    toast(`${res.favorite ? 'Añadido a favoritas' : 'Quitado de favoritas'}: ${title}`, res.favorite ? 'success' : 'info');
    updateSidebarCounts();
    actions.renderHome();
    actions.loadRecommendations(false, false);
    applyLibraryFilters();
    if (state.selectedEntryId === entryId && canonical) openLibraryDrawer(canonical, false, entryId);
  } catch (err) {
    toast('Error actualizando favorita: ' + err.message, 'error');
  }
}

export function visibleBaseItems() {
  return state.showSystemFiles ? state.libraryItems : state.libraryItems.filter(x => !x.isSys);
}

export function updateSidebarCounts() {
  const base = visibleBaseItems();
  if ($('#nav-lib-count')) $('#nav-lib-count').textContent = base.length;
  if ($('#filter-count-all')) $('#filter-count-all').textContent = base.length;
  if ($('#filter-count-recent')) $('#filter-count-recent').textContent = base.filter(x => x.lastPlayedAt).length;
  if ($('#filter-count-verified')) $('#filter-count-verified').textContent = base.filter(x => x.isVerified).length;
  if ($('#filter-count-favorites')) $('#filter-count-favorites').textContent = base.filter(x => x.favorite).length;
  if ($('#filter-count-untagged')) $('#filter-count-untagged').textContent = base.filter(x => !x.hasMetadata).length;

  const totalBytes = base.reduce((acc, x) => acc + (x.size || 0), 0);
  const stEl = $('#storage-total-label');
  if (stEl) {
    stEl.textContent = (totalBytes / (1024 * 1024 * 1024)).toFixed(1) + ' GB local';
    stEl.style.whiteSpace = 'nowrap';
    stEl.title = `Espacio real ocupado por ${base.length} ROMs locales en tu carpeta configurada`;
  }

  const counts = new Map();
  base.forEach(item => counts.set(item.consoleKey, (counts.get(item.consoleKey) || 0) + 1));
  const sorted = [...counts.entries()].sort((a, b) => b[1] - a[1]);

  const list = $('#sidebar-consoles');
  if (list) {
    list.innerHTML = '';
    sorted.forEach(([key, count]) => {
      const meta = consoleMeta(key);
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'nav-item' + (state.consoleFilter === key ? ' active' : '');
      btn.textContent = `${meta.full} (${count})`;
      list.appendChild(btn);
    });
  }

  renderLibraryConsolePills(base, sorted);
}

export function renderLibraryConsolePills(base = visibleBaseItems(), sortedCounts = null) {
  const bar = $('#library-console-pills');
  if (!bar) return;
  let sorted = sortedCounts;
  if (!sorted) {
    const counts = new Map();
    base.forEach(item => counts.set(item.consoleKey, (counts.get(item.consoleKey) || 0) + 1));
    sorted = [...counts.entries()].sort((a, b) => b[1] - a[1]);
  }
  const frag = document.createDocumentFragment();
  const mkPill = (key, label, color, count) => {
    const pill = document.createElement('button');
    pill.type = 'button';
    pill.className = 'lib-console-pill' + (state.consoleFilter === key ? ' active' : '');
    pill.style.setProperty('--sys-color', color);
    pill.setAttribute('aria-pressed', String(state.consoleFilter === key));
    pill.innerHTML = `${consoleIconSvg(key || 'all', 14)}<span>${escapeHtml(label)}</span><span class="lib-pill-count">${count}</span>`;
    pill.addEventListener('click', () => {
      state.consoleFilter = (key && state.consoleFilter === key) ? '' : key;
      updateSidebarCounts();
      applyLibraryFilters();
    });
    return pill;
  };
  frag.appendChild(mkPill('', 'Todas', '#FFB000', base.length));
  sorted.forEach(([key, count]) => {
    const meta = consoleMeta(key);
    frag.appendChild(mkPill(key, meta.label, meta.color, count));
  });
  bar.replaceChildren(frag);
}


export function applyLibraryFilters() {
  const base = visibleBaseItems();
  const q = state.searchQuery.trim().toLowerCase();

  const result = base.filter(item => {
    if (state.stateFilter === 'recent' && !item.lastPlayedAt) return false;
    if (state.stateFilter === 'verified' && !item.isVerified) return false;
    if (state.stateFilter === 'favorites' && !item.favorite) return false;
    if (state.stateFilter === 'untagged' && item.hasMetadata) return false;
    if (state.consoleFilter && item.consoleKey !== state.consoleFilter) return false;
    if (q) {
      const hay = `${item.title} ${item.cleanTitle || ''} ${item.romset || ''} ${item.arcadeCore || ''} ${item.consoleKey} ${item.region} ${item.ext} ${item.relativePath}`.toLowerCase();
      if (!hay.includes(q)) return false;
    }
    return true;
  });

  result.sort((a, b) => {
    switch (state.sortBy) {
      case 'title-desc': return b.title.localeCompare(a.title);
      case 'recent-first': return (b.lastPlayedAt ? Date.parse(b.lastPlayedAt) : 0) - (a.lastPlayedAt ? Date.parse(a.lastPlayedAt) : 0) || a.title.localeCompare(b.title);
      case 'size-desc': return (b.size || 0) - (a.size || 0);
      case 'size-asc': return (a.size || 0) - (b.size || 0);
      case 'platform-asc': return a.consoleKey.localeCompare(b.consoleKey) || a.title.localeCompare(b.title);
      case 'favorites-first': return (a.favorite ? 0 : 1) - (b.favorite ? 0 : 1) || a.title.localeCompare(b.title);
      default: return a.title.localeCompare(b.title);
    }
  });

  state.filteredItems = result;
  state.visibleCount = PAGE_SIZE;
  renderLibrary();
}

export function renderActivePills() {
  const wrap = $('#active-filter-pills');
  wrap.innerHTML = '';
  const pills = [];
  if (state.stateFilter !== 'all') {
    const labels = { recent: 'Recientes', verified: 'Verificadas', favorites: 'Favoritas', untagged: 'Sin metadatos' };
    pills.push({ label: labels[state.stateFilter] || state.stateFilter, clear: () => actions.setStateFilter('all') });
  }
  if (state.consoleFilter) {
    pills.push({ label: consoleMeta(state.consoleFilter).full, clear: () => { state.consoleFilter = ''; updateSidebarCounts(); applyLibraryFilters(); } });
  }
  if (state.searchQuery.trim()) {
    pills.push({ label: `"${state.searchQuery.trim()}"`, clear: () => { state.searchQuery = ''; $('#global-search').value = ''; $('#search-clear-btn').hidden = true; applyLibraryFilters(); } });
  }

  pills.forEach(p => {
    const span = document.createElement('span');
    span.className = 'filter-pill';
    span.innerHTML = `<span>${escapeHtml(p.label)}</span><button type="button" aria-label="Quitar filtro ${escapeHtml(p.label)}">&times;</button>`;
    span.querySelector('button').addEventListener('click', p.clear);
    wrap.appendChild(span);
  });
  $('#reset-filters-btn').hidden = pills.length === 0;
}

export function renderLibrary() {
  const grid = $('#library-grid');
  const tableWrap = $('#library-table-wrap');
  const tbody = $('#library-table-body');
  const empty = $('#library-empty');
  const loadMore = $('#library-load-more');
  $('#library-skeleton').hidden = true;
  renderActivePills();

  const total = state.filteredItems.length;
  const slice = state.filteredItems.slice(0, state.visibleCount);
  $('#results-counter').textContent = `Mostrando ${slice.length} de ${total} ROMs`;

  if (!total) {
    grid.hidden = true;
    tableWrap.hidden = true;
    loadMore.hidden = true;
    empty.hidden = false;
    if (state.stateFilter === 'verified') {
      $('#library-empty-title').textContent = 'Sin ROMs verificadas todavía';
      $('#library-empty-desc').textContent = 'Abre cualquier ROM y pulsa «Verificar SHA-256» para calcular y guardar su firma.';
    } else if (state.stateFilter === 'favorites') {
      $('#library-empty-title').textContent = 'Aún no tienes favoritas';
      $('#library-empty-desc').textContent = 'Marca cualquier juego con la estrella para acceder rápido desde aquí.';
    } else {
      $('#library-empty-title').textContent = 'Ninguna coincidencia';
      $('#library-empty-desc').textContent = 'No hay ROMs que coincidan con los filtros activos.';
    }
    return;
  }
  empty.hidden = true;

  if (state.viewMode === 'grid') {
    tableWrap.hidden = true;
    grid.hidden = false;
    const frag = document.createDocumentFragment();
    slice.forEach(item => frag.appendChild(buildLibraryCard(item)));
    grid.replaceChildren(frag);
  } else {
    grid.hidden = true;
    tableWrap.hidden = false;
    const frag = document.createDocumentFragment();
    slice.forEach(item => {
      const meta = consoleMeta(item.consoleKey);
      const isFav = Boolean(item.favorite);
      const tr = document.createElement('tr');
      if (state.selectedEntryId === item.entryId) tr.className = 'selected';
      const displayTitle = (state.libraryGrouped && item.cleanTitle) ? item.cleanTitle : item.title;
      const vCount = Number(item.variantCount || 1);
      tr.innerHTML = `
        <td class="table-title-cell" title="${escapeHtml(item.title)}">${escapeHtml(displayTitle)}${vCount > 1 ? ` <span class="mini-badge variant-badge">${item.isMultiDisc ? `${vCount} discos` : `${vCount} ver.`}</span>` : ''}</td>
        <td><span class="sys-chip" style="color:${meta.color};">${escapeHtml(meta.label)}</span></td>
        <td class="mono">${escapeHtml(item.region)}</td>
        <td class="mono">.${escapeHtml(item.ext)}</td>
        <td class="mono">${formatBytes(item.size)}</td>
        <td><span class="verify-pill ${item.isVerified ? 'verified' : (!item.hasMetadata ? 'untagged' : '')}">${item.isVerified ? 'SHA-256' : (!item.hasMetadata ? 'Sin meta' : 'Local')}</span></td>
        <td>
          <div class="table-actions">
            <button class="btn btn-ghost btn-sm tbl-fav" type="button" aria-label="${isFav ? 'Quitar favorita' : 'Favorita'}: ${escapeHtml(item.title)}" style="${isFav ? 'color:#FCD34D;border-color:rgba(251,191,36,0.55);' : ''}">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="${isFav ? 'currentColor' : 'none'}" stroke="currentColor" stroke-width="2" aria-hidden="true"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
            </button>
            <button class="btn btn-secondary btn-sm tbl-inspect" type="button">Inspeccionar</button>
          </div>
        </td>
      `;
      tr.querySelector('.tbl-fav').addEventListener('click', ev => { ev.stopPropagation(); toggleFavorite(item.entryId, item.title); });
      tr.querySelector('.tbl-inspect').addEventListener('click', ev => { ev.stopPropagation(); openLibraryDrawer(item); });
      tr.addEventListener('click', () => openLibraryDrawer(item));
      frag.appendChild(tr);
    });
    tbody.replaceChildren(frag);
  }

  if (slice.length < total) {
    loadMore.hidden = false;
    $('#library-load-more-btn').textContent = `Cargar ${Math.min(PAGE_SIZE, total - slice.length)} más (${total - slice.length} restantes)`;
  } else {
    loadMore.hidden = true;
  }
}

export function openDrawerBase(eyebrow, title, focusClose = true) {
  state.lastFocusedElement = document.activeElement;
  $('#drawer-eyebrow').textContent = eyebrow;
  $('#drawer-title').textContent = title;
  const drawer = $('#inspection-drawer');
  drawer.removeAttribute('inert');
  drawer.setAttribute('aria-hidden', 'false');
  drawer.classList.add('open');
  $('#drawer-backdrop').classList.add('open');
  if (focusClose) setTimeout(() => $('#drawer-close-btn').focus(), 40);
}

export function closeDrawer() {
  const drawer = $('#inspection-drawer');
  if (!drawer.classList.contains('open')) return;
  drawer.classList.remove('open');
  drawer.setAttribute('aria-hidden', 'true');
  drawer.setAttribute('inert', '');
  $('#drawer-backdrop').classList.remove('open');
  state.selectedEntryId = null;
  if (state.view === 'library') renderLibrary();
  if (state.view === 'home') actions.renderHome();
  if (state.lastFocusedElement && typeof state.lastFocusedElement.focus === 'function') state.lastFocusedElement.focus();
}

export function copyText(text, label) {
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(text).then(() => toast(`${label} copiado al portapapeles`, 'success')).catch(() => toast(`${label}: ${text}`));
  } else toast(`${label}: ${text}`);
}

export function openLibraryDrawer(inputItem, moveFocus = true, activeVariantId = null) {
  const lookup = findLibraryEntry(inputItem?.entryId);
  const canonical = lookup.canonical || inputItem;
  const variants = (canonical && Array.isArray(canonical.variants) && canonical.variants.length)
    ? canonical.variants
    : [canonical];
  const chosenId = activeVariantId || (lookup.variant && lookup.variant.entryId) || canonical.entryId;
  const chosenVariant = variants.find(v => v.entryId === chosenId) || variants[0] || canonical;
  const item = {
    ...canonical,
    ...chosenVariant,
    cleanTitle: chosenVariant.cleanTitle || canonical.cleanTitle || chosenVariant.title,
    description: canonical.description || chosenVariant.description || '',
    releaseYear: canonical.releaseYear || chosenVariant.releaseYear || '',
    genre: canonical.genre || chosenVariant.genre || '',
    developer: canonical.developer || chosenVariant.developer || '',
    coverUrl: chosenVariant.coverUrl || canonical.coverUrl,
    hasCover: Boolean(chosenVariant.hasCover || canonical.hasCover),
    variants,
    variantCount: variants.length,
    isMultiDisc: Boolean(canonical.isMultiDisc),
  };
  state.selectedEntryId = item.entryId;
  const meta = consoleMeta(item.consoleKey);
  const isFav = Boolean(item.favorite);
  const shaDisplay = item.expectedSha256 || 'Pendiente de cálculo';
  const targetOpts = state.targets.map(t => `<option value="${escapeHtml(t.id)}">${escapeHtml(t.name)} (${escapeHtml(t.host)}:${t.port})</option>`).join('');

  openDrawerBase(`${meta.full} · FICHA TÉCNICA`, item.cleanTitle || item.title, moveFocus);

  const variantsHtml = variants.length > 1 ? `
    <div id="drawer-variants-box" class="drawer-variants-box">
      <div class="drawer-variants-head">
        <span class="meta-key" style="margin:0;">Versiones y regiones en tu bóveda (${variants.length})</span>
        <span class="mini-badge">${item.isMultiDisc ? 'Multi-Disco / Edición' : 'Ediciones agrupadas'}</span>
      </div>
      <div class="drawer-variants-list">
        ${variants.map(v => `
          <button type="button" class="variant-chip-btn ${v.entryId === item.entryId ? 'active' : ''}" data-variant-id="${escapeHtml(v.entryId)}" title="${escapeHtml(v.title)}">
            <span>${escapeHtml(v.editionLabel || v.region)}</span>
            <span class="mono" style="opacity:0.78;">.${escapeHtml(v.ext)} · ${formatBytes(v.size)}</span>
          </button>
        `).join('')}
      </div>
    </div>
  ` : '';

  $('#drawer-body').innerHTML = `
    <div class="drawer-hero">
      <div class="drawer-art">${renderCoverMarkup(item)}</div>
      <div class="drawer-summary">
        <div class="drawer-badges">
          <span class="sys-chip" style="color:${meta.color};">${escapeHtml(meta.label)}</span>
          ${item.arcadeCore ? `<span class="mini-badge">${escapeHtml(item.arcadeCore)}</span>` : ''}
          <span class="mini-badge">${escapeHtml(item.region)}</span>
          <span class="mini-badge">.${escapeHtml(item.ext)}</span>
          ${item.editionLabel ? `<span class="mini-badge">${escapeHtml(item.editionLabel)}</span>` : ''}
        </div>
        <p class="drawer-desc">
          Archivo activo: <code class="mono">${escapeHtml(item.platform)}/${escapeHtml(item.relativePath)}</code>
        </p>
        <div style="display:flex;gap:8px;flex-wrap:wrap;margin-top:4px;">
          <button id="drawer-played-btn" class="btn btn-primary btn-sm" type="button">▶ Marcar jugado</button>
          <a id="drawer-download-rom-btn" class="btn btn-secondary btn-sm" href="/api/v1/library/${encodeURIComponent(item.entryId)}/download" download>⬇ Descargar ROM</a>
          <button id="drawer-fav-btn" class="btn btn-secondary btn-sm" type="button">${isFav ? '★ En favoritas' : '☆ Favorita'}</button>
          <button id="drawer-cover-btn" class="btn btn-ghost btn-sm" type="button">${item.hasCover ? 'Actualizar carátula' : 'Buscar carátula'}</button>
          <button id="drawer-custom-cover-btn" class="btn btn-ghost btn-sm" type="button">Subir / Cambiar carátula</button>
        </div>
      </div>
    </div>

    ${variantsHtml}

    <div id="drawer-saga-slot" hidden></div>

    <div id="drawer-synopsis-box" class="drawer-synopsis-box">
      <div style="display:flex;align-items:center;justify-content:space-between;gap:8px;">
        <span class="meta-key" style="margin:0;">Acerca del juego</span>
        <div style="display:flex;align-items:center;gap:6px;flex-wrap:wrap;" id="drawer-synopsis-badges">
          ${item.releaseYear ? `<span class="mini-badge">${escapeHtml(item.releaseYear)}</span>` : ''}
          ${item.genre ? `<span class="mini-badge">${escapeHtml(item.genre)}</span>` : ''}
          ${item.developer ? `<span class="mini-badge">${escapeHtml(item.developer)}</span>` : ''}
          <button id="drawer-refresh-desc-btn" class="btn btn-ghost btn-sm" type="button" style="height:22px;padding:0 8px;font-size:11px;" title="Volver a buscar sinopsis">↻</button>
        </div>
      </div>
      <p id="drawer-synopsis-text" class="drawer-synopsis-text clamped">${escapeHtml(item.description || 'Consultando sinopsis, año y género…')}</p>
      <button id="drawer-synopsis-toggle" class="btn btn-ghost btn-sm" type="button" style="align-self:flex-start;height:24px;padding:0 10px;font-size:11px;" hidden>Leer más</button>
    </div>

    <div id="drawer-patches-box" class="drawer-patches-box">
      <div style="display:flex;align-items:center;justify-content:space-between;gap:8px;flex-wrap:wrap;">
        <span class="meta-key" style="margin:0;">Traducciones y Parches (.IPS / .BPS)</span>
        <span id="drawer-patch-badge" class="mini-badge">${item.hasSpanishPatch ? '[ES] Aplicado' : 'Idioma original'}</span>
      </div>
      <div id="drawer-applied-patches" style="display:flex;flex-direction:column;gap:6px;"></div>
      <div style="display:flex;gap:8px;flex-wrap:wrap;">
        <button id="drawer-search-patches-btn" class="btn btn-secondary btn-sm" type="button">Buscar traducción [ES]</button>
        <button id="drawer-patch-file-btn" class="btn btn-ghost btn-sm" type="button">Subir .IPS / .BPS / .ZIP</button>
        <input id="drawer-patch-file-input" type="file" accept=".ips,.bps,.zip" hidden>
      </div>
      <label class="checkbox-row" style="font-size:12px;">
        <input id="drawer-patch-save-lib" type="checkbox">
        <span>Guardar también una copia [ES] en mi carpeta de ROMs</span>
      </label>
      <div id="drawer-patch-status" class="mono" style="font-size:11.5px;color:var(--text-secondary);"></div>
      <div id="drawer-patch-results" style="display:flex;flex-direction:column;gap:6px;"></div>
    </div>

    <div class="meta-grid">
      ${item.romset ? `
      <div class="meta-row">
        <span class="meta-key">Romset Arcade</span>
        <span class="meta-val">${escapeHtml(item.romset)} · ${escapeHtml(item.arcadeCore || 'Arcade')}</span>
      </div>` : ''}
      <div class="meta-row">
        <span class="meta-key">Actividad</span>
        <span class="meta-val">${item.playCount ? `Jugado ${item.playCount}x · ${formatRelativeTime(item.lastPlayedAt)}` : 'Sin partidas registradas'}</span>
      </div>
      <div class="meta-row">
        <span class="meta-key">Entry ID</span>
        <div class="meta-val-row">
          <span class="meta-val">${escapeHtml(item.entryId)}</span>
          <button class="btn btn-ghost btn-sm" type="button" id="copy-entry-btn">Copiar</button>
        </div>
      </div>
      <div class="meta-row">
        <span class="meta-key">Tamaño exacto</span>
        <span class="meta-val">${formatBytes(item.size)} (${Number(item.size || 0).toLocaleString('es-ES')} bytes)</span>
      </div>
      <div class="meta-row">
        <span class="meta-key">Hash SHA-256</span>
        <span class="meta-val">${escapeHtml(shaDisplay)}</span>
      </div>
      <div class="meta-row">
        <span class="meta-key">Carátula</span>
        <span class="meta-val">${item.hasCover ? `Caché WebP (${escapeHtml(item.coverSource || 'local')})` : 'Fallback vectorial SVG'}</span>
      </div>
    </div>

      <div class="drawer-actions">
      <button id="drawer-verify-btn" class="btn btn-primary" type="button">Verificar SHA-256 en disco</button>
      <div class="transfer-row">
        <label for="drawer-target-select" class="sr-only">Consola destino FTP</label>
        <select id="drawer-target-select" class="control-select" aria-label="Consola destino FTP">${targetOpts}</select>
        <button id="drawer-send-ftp-btn" class="btn btn-secondary" type="button">Enviar FTP</button>
      </div>
      <div class="field-group" style="margin-top:2px;">
        <div style="display:flex;align-items:center;justify-content:space-between;gap:8px;">
          <label for="drawer-ftp-path">Ruta destino FTP</label>
          <button id="drawer-goto-bridge-btn" class="btn btn-ghost btn-sm" type="button" style="height:24px;padding:0 8px;font-size:11px;">Editar perfiles FTP</button>
        </div>
        <input id="drawer-ftp-path" class="text-input mono" type="text" value="${escapeHtml(resolveTargetRemoteDir(state.targets[0], item.consoleKey, item.platform))}">
      </div>
      <button id="drawer-minerva-btn" class="btn btn-ghost" type="button">Buscar ediciones en MiNERVA</button>
    </div>
  `;

  $('#drawer-played-btn').addEventListener('click', () => markPlayed(item.entryId, item.title));
  $('#drawer-fav-btn').addEventListener('click', () => toggleFavorite(item.entryId, item.title));
  $('#copy-entry-btn').addEventListener('click', () => copyText(item.entryId, 'Entry ID'));
  $('#drawer-verify-btn').addEventListener('click', () => verifyAndPrepareEntry(item));
  $('#drawer-cover-btn').addEventListener('click', () => fetchSingleCover(item));
  $('#drawer-custom-cover-btn')?.addEventListener('click', () => actions.openCoverPicker(item));
  $('#drawer-target-select').addEventListener('change', ev => {
    const t = state.targets.find(x => x.id === ev.target.value);
    $('#drawer-ftp-path').value = resolveTargetRemoteDir(t, item.consoleKey, item.platform);
  });
  $('#drawer-goto-bridge-btn').addEventListener('click', () => {
    state.activeTargetId = $('#drawer-target-select').value;
    closeDrawer();
    actions.renderTargets();
    actions.switchView('bridge');
  });
  $('#drawer-send-ftp-btn').addEventListener('click', () => {
    const targetId = $('#drawer-target-select').value;
    const remoteDir = $('#drawer-ftp-path').value.trim();
    sendToConsoleFtp(item, targetId, remoteDir);
  });
  $('#drawer-minerva-btn').addEventListener('click', () => {
    closeDrawer();
    actions.switchView('catalog');
    $('#minerva-query').value = item.cleanTitle || item.title;
    actions.performMinervaSearch();
  });

  loadDrawerSynopsisAndSaga(item, false);
  loadDrawerPatches(item);
  $$('#drawer-variants-box .variant-chip-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const vid = btn.dataset.variantId;
      if (vid && vid !== item.entryId) {
        openLibraryDrawer(canonical, false, vid);
      }
    });
  });
  $('#drawer-refresh-desc-btn')?.addEventListener('click', () => loadDrawerSynopsisAndSaga(item, true));
  $('#drawer-synopsis-toggle')?.addEventListener('click', () => {
    const txtEl = $('#drawer-synopsis-text');
    const btn = $('#drawer-synopsis-toggle');
    if (!txtEl || !btn) return;
    const clamped = txtEl.classList.toggle('clamped');
    btn.textContent = clamped ? 'Leer más' : 'Ver menos';
  });
  $('#drawer-search-patches-btn')?.addEventListener('click', () => searchDrawerPatches(item));
  $('#drawer-patch-file-btn')?.addEventListener('click', () => $('#drawer-patch-file-input')?.click());
  $('#drawer-patch-file-input')?.addEventListener('change', ev => {
    const file = ev.target.files && ev.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => applyDrawerPatch(item, { patchDataBase64: String(reader.result || ''), patchFilename: file.name, label: file.name.replace(/\.[^.]+$/, ''), sourceName: 'Archivo local' });
    reader.readAsDataURL(file);
  });
  renderLibrary();
}

export async function loadDrawerSynopsisAndSaga(item, refresh = false) {
  try {
    const data = await api(`/api/v1/library/${encodeURIComponent(item.entryId)}/description?refresh=${refresh ? 'true' : 'false'}`);
    if (state.selectedEntryId !== item.entryId) return;
    item.description = data.description || '';
    item.releaseYear = data.releaseYear || '';
    item.genre = data.genre || '';
    item.developer = data.developer || '';
    const txtEl = $('#drawer-synopsis-text');
    if (txtEl) {
      txtEl.textContent = item.description || 'Sin sinopsis disponible.';
      const tog = $('#drawer-synopsis-toggle');
      if (tog) tog.hidden = (item.description || '').length < 150;
    }
    const bEl = $('#drawer-synopsis-badges');
    if (bEl) {
      const btnRef = bEl.querySelector('#drawer-refresh-desc-btn');
      const chipsHtml = [
        item.releaseYear ? `<span class="mini-badge">${escapeHtml(item.releaseYear)}</span>` : '',
        item.genre ? `<span class="mini-badge">${escapeHtml(item.genre)}</span>` : '',
        item.developer ? `<span class="mini-badge">${escapeHtml(item.developer)}</span>` : ''
      ].join('');
      bEl.innerHTML = chipsHtml;
      if (btnRef) bEl.appendChild(btnRef);
    }
    const sagaSlot = $('#drawer-saga-slot');
    if (sagaSlot && data.saga) {
      sagaSlot.hidden = false;
      sagaSlot.innerHTML = `
        <button type="button" class="drawer-saga-pill" style="--saga-accent:${escapeHtml(data.saga.accent || '#FFB000')};">
          <span>📚 Parte de la saga <strong>${escapeHtml(data.saga.name)}</strong> (${data.saga.ownedCount}/${data.saga.totalCount})</span>
          <span>Ver saga →</span>
        </button>
      `;
      sagaSlot.querySelector('button').addEventListener('click', () => {
        closeDrawer();
        actions.switchView('sagas');
        actions.openSagaDetail(data.saga.id);
      });
    }
  } catch (e) { /* ignore */ }
}

export async function loadDrawerPatches(item) {
  const listEl = $('#drawer-applied-patches');
  const badgeEl = $('#drawer-patch-badge');
  if (!listEl) return;
  try {
    const rows = await api(`/api/v1/library/${encodeURIComponent(item.entryId)}/patches`);
    if (state.selectedEntryId !== item.entryId) return;
    if (badgeEl) badgeEl.textContent = rows.length ? `${rows.length} parche(s) [ES]` : 'Idioma original';
    if (!rows.length) {
      listEl.innerHTML = '<span class="mono" style="font-size:11.5px;color:var(--text-muted);">Aún no hay traducción aplicada. Busca una o sube tu archivo .ips / .bps.</span>';
      return;
    }
    listEl.innerHTML = rows.map(p => `
      <div class="patch-row">
        <div style="min-width:0;display:flex;flex-direction:column;gap:2px;">
          <strong style="font-size:12.5px;word-break:break-word;">${escapeHtml(p.label)}</strong>
          <span class="mono" style="font-size:11px;color:var(--text-muted);">${escapeHtml(p.patch_format)} · ${escapeHtml(p.patched_filename)} · CRC ${escapeHtml(p.crc32_hex || '')}</span>
        </div>
        <div style="display:flex;gap:6px;flex-wrap:wrap;">
          <a class="btn btn-secondary btn-sm" href="/api/v1/patches/download/${encodeURIComponent(p.patch_id)}" download>⬇ Bajar [${escapeHtml(p.language)}]</a>
          <button class="btn btn-primary btn-sm patch-ftp-btn" type="button" data-pid="${escapeHtml(p.patch_id)}">Enviar FTP</button>
          <button class="btn btn-ghost btn-sm patch-del-btn" type="button" data-pid="${escapeHtml(p.patch_id)}" aria-label="Eliminar parche">×</button>
        </div>
      </div>
    `).join('');
    listEl.querySelectorAll('.patch-ftp-btn').forEach(b => b.addEventListener('click', () => {
      const targetId = $('#drawer-target-select')?.value;
      const remoteDir = $('#drawer-ftp-path')?.value?.trim() || '';
      sendToConsoleFtp(item, targetId, remoteDir, b.dataset.pid);
    }));
    listEl.querySelectorAll('.patch-del-btn').forEach(b => b.addEventListener('click', async () => {
      await api(`/api/v1/patches/${encodeURIComponent(b.dataset.pid)}`, { method: 'DELETE' });
      toast('Parche eliminado', 'info');
      await loadDrawerPatches(item);
      await actions.loadLibrary();
    }));
  } catch (e) { /* ignore */ }
}

export async function searchDrawerPatches(item) {
  const resBox = $('#drawer-patch-results');
  const st = $('#drawer-patch-status');
  if (!resBox || !st) return;
  st.textContent = 'Buscando traducciones [ES] en Internet Archive…';
  st.style.color = 'var(--text-secondary)';
  resBox.innerHTML = '';
  try {
    const rows = await api('/api/v1/patches/search?' + new URLSearchParams({ q: item.cleanTitle || item.title, platform: item.consoleKey }));
    if (state.selectedEntryId !== item.entryId) return;
    if (!rows.length) {
      st.textContent = 'No hallamos coincidencias automáticas en IA; puedes subir cualquier .ips/.bps/.zip local.';
      return;
    }
    st.textContent = `${rows.length} paquete(s) encontrados listos para aplicar:`;
    resBox.innerHTML = rows.map((r, idx) => `
      <div class="patch-row">
        <div style="min-width:0;display:flex;flex-direction:column;gap:2px;">
          <strong style="font-size:12px;word-break:break-word;">${escapeHtml(r.filename)}</strong>
          <span class="mono" style="font-size:10.5px;color:var(--text-muted);">${escapeHtml(r.format)} · ${escapeHtml(r.title)}</span>
        </div>
        <button class="btn btn-primary btn-sm patch-apply-online-btn" type="button" data-idx="${idx}">Descargar y aplicar</button>
      </div>
    `).join('');
    resBox.querySelectorAll('.patch-apply-online-btn').forEach(b => b.addEventListener('click', () => {
      const r = rows[Number(b.dataset.idx)];
      applyDrawerPatch(item, {
        patchUrl: r.patchUrl,
        patchFilename: r.filename,
        label: r.filename.replace(/\.[^.]+$/, '') + ' [ES]',
        sourceName: r.sourceName
      });
    }));
  } catch (err) {
    st.textContent = '✗ Error buscando parches: ' + err.message;
    st.style.color = 'var(--danger)';
  }
}

export async function applyDrawerPatch(item, payload) {
  const st = $('#drawer-patch-status');
  if (st) { st.textContent = 'Descargando y aplicando parche…'; st.style.color = 'var(--text-secondary)'; }
  try {
    const saveToLib = Boolean($('#drawer-patch-save-lib')?.checked);
    const res = await api(`/api/v1/patches/apply/${encodeURIComponent(item.entryId)}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...payload, saveToLibrary: saveToLib })
    });
    if (st) {
      st.textContent = `✓ Parche ${res.patch.patch_format} aplicado (CRC32 ${res.crc32}).`;
      st.style.color = 'var(--success)';
    }
    item.hasSpanishPatch = true;
    toast(`ROM traducida lista: ${res.patch.patched_filename}`, 'success');
    recordActivityNotice(`Parche [ES] aplicado · ${item.cleanTitle || item.title}`, res.patch.patched_filename, 'gold');
    await loadDrawerPatches(item);
    actions.loadSagas();
    if (saveToLib) await actions.loadLibrary();
  } catch (err) {
    if (st) {
      st.textContent = '✗ ' + err.message;
      st.style.color = 'var(--danger)';
    }
    toast('Error aplicando parche: ' + err.message, 'error');
  }
}

export async function fetchSingleCover(item) {
  const btn = $('#drawer-cover-btn');
  if (btn) { btn.disabled = true; btn.textContent = 'Buscando…'; }
  try {
    const res = await api(`/api/v1/covers/fetch/${encodeURIComponent(item.entryId)}`, { method: 'POST' });
    if (res.found) {
      item.hasCover = true;
      item.coverSource = res.source;
      item.coverUrl = res.coverUrl + '?t=' + Date.now();
      actions.syncSagasFromLibrary();
      toast(`Carátula WebP guardada (${Math.round(res.bytes / 1024)} KB · ${res.source})`, 'success');
      actions.refreshCoverStats();
      applyLibraryFilters();
      if (state.view === 'sagas') {
        if (state.activeSagaId) actions.openSagaDetail(state.activeSagaId);
        else actions.renderSagasGallery();
      }
      if (state.selectedEntryId === item.entryId) openLibraryDrawer(item, false);
    } else {
      toast('No se encontró carátula externa para este título', 'info');
      if (btn) { btn.disabled = false; btn.textContent = 'Reintentar carátula'; }
    }
  } catch (err) {
    toast('Error buscando carátula: ' + err.message, 'error');
    if (btn) { btn.disabled = false; btn.textContent = 'Buscar carátula'; }
  }
}

export async function syncHomeCovers(limit = 24) {
  const base = visibleBaseItems();
  const uncachedPool = [];
  const seen = new Set();
  const pushUncached = item => {
    if (item && !item.isSys && !item.hasCover && !seen.has(item.entryId)) {
      seen.add(item.entryId);
      uncachedPool.push(item.entryId);
    }
  };
  if (state.heroItem) pushUncached(state.heroItem);
  base.filter(x => x.lastPlayedAt).slice(0, 8).forEach(pushUncached);
  base.filter(x => x.favorite).slice(0, 8).forEach(pushUncached);
  pickDiverseFeatured(base, base.filter(x => x.hasCover), 12).forEach(pushUncached);
  base.forEach(pushUncached);
  const targetIds = uncachedPool.slice(0, limit);
  if (!targetIds.length) {
    toast('Todas tus ROMs ya tienen carátula descargada', 'info');
    return;
  }
  [$('#home-fill-covers-btn'), $('#home-featured-fill-btn')].forEach(b => {
    if (b) { b.disabled = true; b.textContent = `Rellenando 0/${targetIds.length}`; }
  });
  const homeBanner = $('#home-cover-sync-banner');
  if (homeBanner) {
    homeBanner.hidden = false;
    $('#home-cover-sync-title').textContent = `Rellenando ${targetIds.length} carátula(s) de Inicio…`;
    $('#home-cover-sync-counts').textContent = `0 / ${targetIds.length}`;
    $('#home-cover-sync-fill').style.width = '4%';
    $('#home-cover-sync-current').textContent = 'Conectando con Libretro / SteamGridDB…';
  }
  try {
    await api('/api/v1/covers/sync', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        limit: targetIds.length,
        platform: '',
        entryIds: targetIds,
        retryMisses: true
      })
    });
    toast(`Rellenando ${targetIds.length} carátula(s) para Inicio…`, 'info');
    pollCoverSync();
  } catch (err) {
    toast('Error iniciando descarga de carátulas: ' + err.message, 'error');
    if (homeBanner) homeBanner.hidden = true;
    [$('#home-fill-covers-btn'), $('#home-featured-fill-btn')].forEach(b => {
      if (b) { b.disabled = false; b.textContent = 'Rellenar carátulas'; }
    });
  }
}

export async function syncBatchCovers(limit = 24) {
  const uncached = state.filteredItems.filter(x => !x.hasCover).slice(0, limit).map(x => x.entryId);
  if (!uncached.length) {
    toast('Todas las ROMs visibles ya tienen carátula', 'info');
    return;
  }
  const btn = $('#sync-covers-btn');
  const banner = $('#cover-sync-banner');
  if (btn) { btn.disabled = true; btn.textContent = `Sincronizando 0/${uncached.length}`; }
  if (banner) {
    banner.hidden = false;
    $('#cover-sync-title').textContent = `Sincronizando ${uncached.length} carátula(s) filtrada(s)…`;
    $('#cover-sync-counts').textContent = `0 / ${uncached.length}`;
    $('#cover-sync-fill').style.width = '4%';
    $('#cover-sync-current').textContent = 'Conectando con proveedores…';
  }
  try {
    await api('/api/v1/covers/sync', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        limit: uncached.length,
        platform: state.consoleFilter,
        entryIds: uncached,
        retryMisses: uncached.length <= 24
      })
    });
    toast(`Sincronizando ${uncached.length} carátula(s)…`, 'info');
    pollCoverSync();
  } catch (err) {
    toast('Error iniciando sincronización: ' + err.message, 'error');
    if (banner) banner.hidden = true;
    if (btn) { btn.disabled = false; btn.textContent = 'Sincronizar carátulas'; }
  }
}


export function applyCoverSyncStatus(st) {
  if (!st) return;
  clearTimeout(state.coverSyncTimer);
  state.coverSyncStatus = st;
  renderActivitySheet();
  actions.updateCoverStatsUi(st);
  if (Array.isArray(st.newlyCached) && st.newlyCached.length) {
    let changed = false;
    const byId = new Map(state.libraryItems.map(x => [x.entryId, x]));
    st.newlyCached.forEach(nc => {
      const item = byId.get(nc.entryId);
      if (item && !item.hasCover) {
        item.hasCover = true;
        item.coverSource = nc.source || 'libretro';
        item.coverUrl = nc.coverUrl;
        changed = true;
      }
    });
    if (changed) {
      actions.syncSagasFromLibrary();
      if (state.view === 'library') renderLibrary();
      if (state.view === 'home') actions.renderHome();
      if (state.view === 'sagas') {
        if (state.activeSagaId) actions.openSagaDetail(state.activeSagaId);
        else actions.renderSagasGallery();
      }
    }
  }
  const btn = $('#sync-covers-btn');
  const banner = $('#cover-sync-banner');
  const homeBtn = $('#home-fill-covers-btn');
  const homeFeatBtn = $('#home-featured-fill-btn');
  const homeBanner = $('#home-cover-sync-banner');
  const sagaBtnDetail = $('#saga-fill-covers-btn');
  const sagaBtnGal = $('#sagas-gallery-fill-btn');
  const sagaBanner = $('#sagas-cover-sync-banner');
  const pct = st.total ? Math.min(100, Math.round((st.processed * 100) / st.total)) : 0;
  if (st.running) {
    if (btn) { btn.disabled = true; btn.textContent = `Sincronizando ${st.processed}/${st.total}`; }
    [homeBtn, homeFeatBtn, sagaBtnDetail, sagaBtnGal].forEach(b => {
      if (b) { b.disabled = true; b.textContent = `Rellenando ${st.processed}/${st.total}`; }
    });
    if (banner) {
      banner.hidden = false;
      $('#cover-sync-title').textContent = `Sincronizando carátulas (${st.found} encontradas)`;
      $('#cover-sync-counts').textContent = `${st.processed} / ${st.total}`;
      $('#cover-sync-fill').style.width = `${Math.max(5, pct)}%`;
      $('#cover-sync-current').textContent = st.currentTitle ? `Procesando: ${st.currentTitle}` : 'Descargando y optimizando a WebP…';
    }
    if (homeBanner) {
      homeBanner.hidden = false;
      $('#home-cover-sync-title').textContent = `Rellenando carátulas (${st.found} encontradas)`;
      $('#home-cover-sync-counts').textContent = `${st.processed} / ${st.total}`;
      $('#home-cover-sync-fill').style.width = `${Math.max(5, pct)}%`;
      $('#home-cover-sync-current').textContent = st.currentTitle ? `Procesando: ${st.currentTitle}` : 'Descargando y optimizando a WebP…';
    }
    if (sagaBanner && !sagaBanner.hidden) {
      $('#sagas-cover-sync-title').textContent = `Rellenando carátulas de Sagas (${st.found} encontradas)`;
      $('#sagas-cover-sync-counts').textContent = `${st.processed} / ${st.total}`;
      $('#sagas-cover-sync-fill').style.width = `${Math.max(5, pct)}%`;
      $('#sagas-cover-sync-current').textContent = st.currentTitle ? `Procesando: ${st.currentTitle}` : 'Descargando y optimizando a WebP…';
    }
  } else {
    if (btn) { btn.disabled = false; btn.textContent = 'Sincronizar carátulas'; }
    [homeBtn, homeFeatBtn, sagaBtnDetail, sagaBtnGal].forEach(b => {
      if (b) { b.disabled = false; b.textContent = 'Rellenar carátulas'; }
    });
    if (banner) banner.hidden = true;
    if (homeBanner) homeBanner.hidden = true;
    if (sagaBanner) sagaBanner.hidden = true;
    if (st.total > 0) {
      toast(`Sincronización lista: ${st.found} de ${st.total} carátulas aplicadas`, st.found > 0 ? 'success' : 'info');
      actions.loadSagas();
    }
  }
}

export async function pollCoverSync() {
  try { applyCoverSyncStatus(await api('/api/v1/covers/status')); } catch (e) { /* ignore */ }
}


export async function verifyAndPrepareEntry(item) {
  const btn = $('#drawer-verify-btn');
  if (btn) { btn.disabled = true; btn.textContent = 'Calculando SHA-256…'; }
  try {
    const res = await api(`/api/v1/library/${encodeURIComponent(item.entryId)}/verify`, { method: 'POST' });
    const { canonical, variant } = findLibraryEntry(item.entryId);
    if (variant) {
      variant.expectedSha256 = res.sha256;
      variant.isVerified = true;
    }
    if (canonical && canonical.entryId === item.entryId) {
      canonical.expectedSha256 = res.sha256;
      canonical.isVerified = true;
    }
    item.expectedSha256 = res.sha256;
    item.isVerified = true;
    updateSidebarCounts();
    applyLibraryFilters();
    if (state.selectedEntryId === item.entryId) openLibraryDrawer(canonical || item, false, item.entryId);
    toast(`SHA-256 verificado: ${res.sha256.slice(0, 14)}…`, 'success');
  } catch (err) {
    toast('Error verificando SHA-256: ' + err.message, 'error');
    if (btn) { btn.disabled = false; btn.textContent = 'Verificar SHA-256 en disco'; }
  }
}

export async function sendToConsoleFtp(item, targetId, remoteDir = '', patchId = '') {
  try {
    const job = await api('/api/v1/transfers', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ entryId: item.entryId, targetId, remoteDir: remoteDir || null, patchId: patchId || null })
    });
    state.transferJobs.set(job.id, job);
    toast(`Encolado hacia ${job.targetName}`, 'success');
    closeDrawer();
    actions.switchView('downloads');
    actions.pollTransfers();
  } catch (err) {
    toast('Error encolando transferencia: ' + err.message, 'error');
  }
}

