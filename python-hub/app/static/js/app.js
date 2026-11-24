import { state, $, $$, actions, toast, escapeHtml, consoleMeta, setAmbientBackdrop } from './state.js';
import { api, connectHubEvents } from './api-client.js';
import * as homeView from './views/home.js';
import * as sagasView from './views/sagas.js';
import * as libraryView from './views/library.js';
import * as catalogView from './views/catalog.js';
import * as settingsView from './views/settings.js';
import { initActivitySheet, renderActivitySheet, setSseConnectionState, toggleActivitySheet, recordActivityNotice } from './activity-sheet.js';
import { initGamepadAndKeyboard } from './gamepad.js';

const {
  pinHeroGame, closeGameContextMenu, openCoverPicker, closeCoverPicker,
  saveCustomCover, loadRecommendations, openMoodPicker, closeMoodPicker, renderHome,
} = homeView;
const {
  syncSagasFromLibrary, loadSagas, syncSagaCovers, renderSagasGallery, openSagaDetail,
  openSagaModal, closeSagaModal,
} = sagasView;
const {
  markPlayed, toggleFavorite, updateSidebarCounts, applyLibraryFilters, renderLibrary,
  closeDrawer, copyText, openLibraryDrawer, applyCoverSyncStatus,
} = libraryView;
const {
  performMinervaSearch, renderQueues, applyDownloadEvent, applyTransferEvent,
  pollDownloads, pollTransfers, openMinervaDrawer,
} = catalogView;
const {
  renderConsolePathsEditor, switchSettingsTab, renderTargets, loadSettingsAndTargets,
  updateCoverStatsUi, refreshCoverStats, loadSystemInfo, testSteamGridKeyInto, testIaCredsInto,
  inspectWizardPath, pickNativeFolder, openSetupWizard, closeSetupWizard, setWizardStep, finishSetupWizard,
} = settingsView;

let activeConsoleTab = 'home';

export function getActiveConsoleTab() {
  return activeConsoleTab;
}

export function setExploreTab(tabName) {
  const next = tabName === 'consoles' ? 'consoles' : 'sagas';
  state.exploreTab = next;
  $$('.explore-seg-btn').forEach(btn => {
    const active = btn.dataset.exploreTab === next;
    btn.classList.toggle('active', active);
    btn.setAttribute('aria-selected', String(active));
  });
  const sagasPane = $('#explore-sagas-pane');
  const consolesPane = $('#explore-consoles-pane');
  if (sagasPane) sagasPane.hidden = next !== 'sagas';
  if (consolesPane) consolesPane.hidden = next !== 'consoles';
  if (next === 'sagas') {
    if (state.activeSagaId) openSagaDetail(state.activeSagaId);
    else renderSagasGallery();
  }
}

export function openExploreConsoleLibrary(consoleKey = '') {
  switchView('explore');
  setExploreTab('consoles');
  if (typeof consoleKey === 'string') {
    state.consoleFilter = consoleKey;
  }
  const overview = $('#explore-consoles-overview');
  const libSub = $('#view-library');
  const catSub = $('#view-catalog');
  if (overview) overview.hidden = true;
  if (catSub) catSub.hidden = true;
  if (libSub) libSub.hidden = false;
  updateSidebarCounts();
  applyLibraryFilters();
}

export function openExploreMinervaCatalog() {
  switchView('explore');
  setExploreTab('consoles');
  const overview = $('#explore-consoles-overview');
  const libSub = $('#view-library');
  const catSub = $('#view-catalog');
  if (overview) overview.hidden = true;
  if (libSub) libSub.hidden = true;
  if (catSub) catSub.hidden = false;
}

export function resetExploreConsolesOverview() {
  const overview = $('#explore-consoles-overview');
  const libSub = $('#view-library');
  const catSub = $('#view-catalog');
  if (overview) overview.hidden = false;
  if (libSub) libSub.hidden = true;
  if (catSub) catSub.hidden = true;
}

export function switchView(viewName) {
  let consoleTab = 'home';
  if (viewName === 'home') {
    state.view = 'home';
    consoleTab = 'home';
  } else if (viewName === 'explore' || viewName === 'sagas') {
    state.view = 'explore';
    consoleTab = 'explore';
  } else if (viewName === 'library') {
    state.view = 'explore';
    consoleTab = 'explore';
  } else if (viewName === 'catalog') {
    state.view = 'explore';
    consoleTab = 'explore';
  } else if (viewName === 'downloads') {
    toggleActivitySheet(true);
    return;
  } else if (viewName === 'bridge' || viewName === 'settings') {
    state.view = 'settings';
    consoleTab = 'settings';
  } else {
    state.view = 'home';
    consoleTab = 'home';
  }

  activeConsoleTab = consoleTab;
  $$('.view-section').forEach(el => el.classList.toggle('active', el.id === 'view-' + consoleTab));
  $$('.console-nav-btn[data-view]').forEach(btn => {
    const active = btn.dataset.view === consoleTab;
    btn.classList.toggle('active', active);
    if (active) btn.setAttribute('aria-current', 'page');
    else btn.removeAttribute('aria-current');
  });

  if (viewName === 'home') {
    renderHome();
  } else if (viewName === 'sagas' || (viewName === 'explore' && state.exploreTab !== 'consoles')) {
    setExploreTab('sagas');
  } else if (viewName === 'explore') {
    setExploreTab('consoles');
  } else if (viewName === 'library') {
    setExploreTab('consoles');
    const overview = $('#explore-consoles-overview');
    const libSub = $('#view-library');
    const catSub = $('#view-catalog');
    if (overview) overview.hidden = true;
    if (catSub) catSub.hidden = true;
    if (libSub) libSub.hidden = false;
    updateSidebarCounts();
    applyLibraryFilters();
  } else if (viewName === 'catalog') {
    setExploreTab('consoles');
    const overview = $('#explore-consoles-overview');
    const libSub = $('#view-library');
    const catSub = $('#view-catalog');
    if (overview) overview.hidden = true;
    if (libSub) libSub.hidden = true;
    if (catSub) catSub.hidden = false;
  } else if (viewName === 'bridge') {
    switchSettingsTab('bridge');
  } else if (viewName === 'settings') {
    refreshCoverStats();
    loadSystemInfo();
  }
}

export function setStateFilter(filterKey) {
  state.stateFilter = filterKey;
  openExploreConsoleLibrary(state.consoleFilter || '');
}

let omniboxTimer = null;
function renderOmniboxResults(query, minervaRows = null) {
  const panel = $('#omnibox-panel');
  const localList = $('#omnibox-local-list');
  const minervaList = $('#omnibox-minerva-list');
  if (!panel || !localList || !minervaList) return;
  const q = String(query || '').trim().toLowerCase();
  if (q.length < 2) {
    panel.hidden = true;
    return;
  }
  panel.hidden = false;
  const localMatches = (state.libraryItems || [])
    .filter(it => !it.isSys && `${it.cleanTitle || ''} ${it.title || ''} ${it.consoleKey || ''}`.toLowerCase().includes(q))
    .slice(0, 6);
  if ($('#omnibox-local-count')) $('#omnibox-local-count').textContent = `${localMatches.length}`;
  if (!localMatches.length) {
    localList.innerHTML = '<div class="omnibox-empty">Sin coincidencias locales</div>';
  } else {
    localList.innerHTML = '';
    localMatches.forEach(item => {
      const meta = consoleMeta(item.consoleKey);
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'omnibox-item';
      btn.innerHTML = `<span><strong>${escapeHtml(item.cleanTitle || item.title)}</strong> <small class="mono">${escapeHtml(meta.label)}</small></span><span class="avail-pill local">Local</span>`;
      btn.addEventListener('click', () => {
        panel.hidden = true;
        openLibraryDrawer(item);
      });
      localList.appendChild(btn);
    });
  }

  if (Array.isArray(minervaRows)) {
    const dedup = [];
    const seen = new Set();
    minervaRows.forEach(r => {
      const key = `${(r.platform || '').toLowerCase()}:${(r.title || '').toLowerCase()}`;
      if (!seen.has(key)) {
        seen.add(key);
        dedup.push(r);
      }
    });
    const topM = dedup.slice(0, 6);
    if ($('#omnibox-minerva-count')) $('#omnibox-minerva-count').textContent = `${topM.length}`;
    if (!topM.length) {
      minervaList.innerHTML = '<div class="omnibox-empty">Sin coincidencias en MiNERVA</div>';
    } else {
      minervaList.innerHTML = '';
      topM.forEach(r => {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'omnibox-item';
        btn.innerHTML = `<span><strong>${escapeHtml(r.title)}</strong> <small class="mono">${escapeHtml((r.platform || '').toUpperCase())}</small></span><span class="avail-pill hub">MiNERVA</span>`;
        btn.addEventListener('click', () => {
          panel.hidden = true;
          openExploreMinervaCatalog();
          if ($('#minerva-query')) $('#minerva-query').value = r.title;
          if ($('#minerva-platform') && r.platform) $('#minerva-platform').value = String(r.platform).toLowerCase();
          performMinervaSearch();
        });
        minervaList.appendChild(btn);
      });
    }
  }
}

async function checkHealth() {
  try {
    const res = await api('/health');
    if ($('#health-dot')) $('#health-dot').classList.remove('offline');
    if ($('#health-text')) $('#health-text').textContent = 'HYLIAN v' + (res.contract_version || 2);
    if ($('#health-covers')) $('#health-covers').textContent = `${res.cached_covers || 0} covers`;
    if (res.minerva_entries && $('#minerva-status-text')) {
      $('#minerva-status-text').textContent = `${Number(res.minerva_entries).toLocaleString('es-ES')} ediciones (${Number(res.mame_entries || 0).toLocaleString('es-ES')} MAME IA)`;
    }
  } catch (e) {
    if ($('#health-dot')) $('#health-dot').classList.add('offline');
    if ($('#health-text')) $('#health-text').textContent = 'Offline';
  }
}

async function loadLibrary() {
  if ($('#library-skeleton')) $('#library-skeleton').hidden = false;
  if ($('#library-grid')) $('#library-grid').hidden = true;
  if ($('#library-table-wrap')) $('#library-table-wrap').hidden = true;
  if ($('#library-empty')) $('#library-empty').hidden = true;
  try {
    const [items, maint, homePayload] = await Promise.all([
      api(`/api/v1/library?grouped=${state.libraryGrouped ? 'true' : 'false'}`),
      api('/api/v1/library/maintenance').catch(() => null),
      api('/api/v1/home').catch(() => null),
    ]);
    state.libraryItems = items;
    state.maintenanceReport = maint;
    state.homeData = homePayload;
    const maintPill = $('#library-maint-pill');
    if (maintPill && maint) {
      maintPill.hidden = false;
      maintPill.textContent = `${Number(maint.uniqueCount || 0).toLocaleString('es-ES')} únicos`;
    }
    const grpBtn = $('#toggle-grouped-btn');
    if (grpBtn) {
      grpBtn.textContent = state.libraryGrouped ? 'Agrupar duplicados: Sí' : 'Agrupar duplicados: No';
    }
    updateSidebarCounts();
    renderHome();
    applyLibraryFilters();
    loadRecommendations(false);
    loadSagas();
  } catch (err) {
    if ($('#library-skeleton')) $('#library-skeleton').hidden = true;
    if ($('#library-empty')) $('#library-empty').hidden = false;
    if ($('#library-empty-title')) $('#library-empty-title').textContent = 'No se pudo cargar la biblioteca';
    if ($('#library-empty-desc')) $('#library-empty-desc').textContent = err.message;
  }
}

export function handleBackNavigation() {
  if ($('#game-context-menu') && !$('#game-context-menu').hidden) { closeGameContextMenu(); return; }
  if ($('#mood-picker-backdrop') && !$('#mood-picker-backdrop').hidden) { closeMoodPicker(); return; }
  if ($('#saga-add-backdrop') && !$('#saga-add-backdrop').hidden) { closeSagaModal(); return; }
  if ($('#cover-picker-backdrop') && !$('#cover-picker-backdrop').hidden) { closeCoverPicker(); return; }
  if ($('#setup-wizard-backdrop') && !$('#setup-wizard-backdrop').hidden) { closeSetupWizard(true); return; }
  if ($('#inspection-drawer')?.classList.contains('open')) { closeDrawer(); return; }
  if (activeConsoleTab === 'explore') {
    if (state.exploreTab === 'sagas' && state.activeSagaId) {
      renderSagasGallery();
      return;
    }
    if (state.exploreTab === 'consoles' && (!$('#view-library')?.hidden || !$('#view-catalog')?.hidden)) {
      resetExploreConsolesOverview();
      return;
    }
    switchView('home');
  } else if (activeConsoleTab === 'settings') {
    switchView('home');
  }
}

// Event wiring
$$('.console-nav-btn[data-view]').forEach(btn => btn.addEventListener('click', () => switchView(btn.dataset.view)));
$$('.explore-seg-btn[data-explore-tab]').forEach(btn => btn.addEventListener('click', () => setExploreTab(btn.dataset.exploreTab)));
$('#explore-open-library-btn')?.addEventListener('click', () => openExploreConsoleLibrary(''));
$('#explore-open-catalog-btn')?.addEventListener('click', () => openExploreMinervaCatalog());
$('#library-back-consoles-btn')?.addEventListener('click', resetExploreConsolesOverview);
$('#catalog-back-consoles-btn')?.addEventListener('click', resetExploreConsolesOverview);
$('#toggle-grouped-btn')?.addEventListener('click', () => { state.libraryGrouped = !state.libraryGrouped; loadLibrary(); });

$('#brand-home-link')?.addEventListener('click', ev => {
  ev.preventDefault();
  if (activeConsoleTab === 'home') {
    state.featuredSeed = ((state.featuredSeed || 1) + 7919) % 100000;
    state.sessionSeed = ((state.sessionSeed || 1) + 104729) % 100000;
    renderHome();
    loadRecommendations(false, true);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  } else {
    switchView('home');
  }
});

$('#home-hero-open-btn')?.addEventListener('click', () => { if (state.heroItem) openLibraryDrawer(state.heroItem); });
$('#home-what-to-play-btn')?.addEventListener('click', () => openMoodPicker(state.moodMode, 0));
$('#mood-close-btn')?.addEventListener('click', closeMoodPicker);
$('#mood-done-btn')?.addEventListener('click', closeMoodPicker);
$('#mood-picker-backdrop')?.addEventListener('click', ev => { if (ev.target.id === 'mood-picker-backdrop') closeMoodPicker(); });
$$('.mood-choice-btn').forEach(btn => btn.addEventListener('click', () => openMoodPicker(btn.dataset.mood, 0)));
$('#mood-reroll-btn')?.addEventListener('click', () => openMoodPicker(state.moodMode, (state.moodOffset || 0) + 1));

$('#sagas-search')?.addEventListener('input', () => renderSagasGallery());
$('#sagas-scope-owned')?.addEventListener('click', () => { state.sagasScope = 'owned'; renderSagasGallery(); });
$('#sagas-scope-all')?.addEventListener('click', () => { state.sagasScope = 'all'; renderSagasGallery(); });
$('#sagas-gallery-fill-btn')?.addEventListener('click', () => syncSagaCovers(null));
$('#saga-fill-covers-btn')?.addEventListener('click', () => syncSagaCovers(state.activeSagaId));
$('#new-saga-btn')?.addEventListener('click', () => openSagaModal(null));
$('#saga-back-btn')?.addEventListener('click', () => renderSagasGallery());
$('#saga-filter-all-btn')?.addEventListener('click', () => { state.sagaOwnedOnly = false; if (state.activeSagaId) openSagaDetail(state.activeSagaId); });
$('#saga-filter-owned-btn')?.addEventListener('click', () => { state.sagaOwnedOnly = true; if (state.activeSagaId) openSagaDetail(state.activeSagaId); });
$('#saga-delete-custom-btn')?.addEventListener('click', async () => {
  if (!state.activeSagaId) return;
  state.sagas = await api(`/api/v1/sagas/custom/${encodeURIComponent(state.activeSagaId)}`, { method: 'DELETE' });
  toast('Saga personalizada eliminada', 'info');
  renderSagasGallery();
});
$('#saga-add-close-btn')?.addEventListener('click', closeSagaModal);
$('#saga-add-backdrop')?.addEventListener('click', ev => { if (ev.target.id === 'saga-add-backdrop') closeSagaModal(); });
$('#saga-existing-add-btn')?.addEventListener('click', async () => {
  const sid = $('#saga-existing-select')?.value;
  const item = state.sagaAddEntry;
  if (!sid || !item) return;
  state.sagas = await api(`/api/v1/sagas/${encodeURIComponent(sid)}/add`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ entryId: item.entryId }),
  });
  toast(`Añadido a saga: ${item.cleanTitle || item.title}`, 'success');
  closeSagaModal();
});
$('#saga-create-submit-btn')?.addEventListener('click', async () => {
  const name = $('#saga-new-name')?.value?.trim();
  if (!name) { toast('Escribe un nombre para la saga', 'error'); return; }
  const entryIds = state.sagaAddEntry ? [state.sagaAddEntry.entryId] : [];
  state.sagas = await api('/api/v1/sagas/custom', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, tagline: $('#saga-new-tagline')?.value?.trim() || '', accent: $('#saga-new-accent')?.value || '#1E8E63', entryIds }),
  });
  toast(`Saga creada: ${name}`, 'success');
  closeSagaModal();
  if (activeConsoleTab === 'explore') renderSagasGallery();
});

$('#home-hero-play-btn')?.addEventListener('click', () => { if (state.heroItem) markPlayed(state.heroItem.entryId, state.heroItem.title); });
$('#home-hero-gotw-btn')?.addEventListener('click', () => {
  const gid = state.recommendations?.gameOfTheWeek?.entryId;
  const target = gid ? state.libraryItems.find(x => x.entryId === gid) : null;
  if (!target) return;
  state.heroOverrideItem = state.heroOverrideItem?.entryId === target.entryId ? null : target;
  renderHome();
});
$('#home-hero-unpin-btn')?.addEventListener('click', () => pinHeroGame('', ''));
$('#home-recs-ai-btn')?.addEventListener('click', () => loadRecommendations(true, true));
$$('.shelf-arrow-btn').forEach(btn => btn.addEventListener('click', () => {
  const wrap = document.getElementById(btn.dataset.scrollTarget);
  if (!wrap) return;
  wrap.scrollBy({ left: (Number(btn.dataset.dir) || 1) * Math.max(260, Math.round(wrap.clientWidth * 0.72)), behavior: 'smooth' });
}));
$('#home-pick-sample-btn')?.addEventListener('click', () => { switchView('explore'); setExploreTab('consoles'); });
$('#home-favs-goto-lib-btn')?.addEventListener('click', () => openExploreConsoleLibrary(''));

$('#view-mode-grid')?.addEventListener('click', () => {
  state.viewMode = 'grid';
  $('#view-mode-grid')?.classList.add('active'); $('#view-mode-grid')?.setAttribute('aria-pressed', 'true');
  $('#view-mode-table')?.classList.remove('active'); $('#view-mode-table')?.setAttribute('aria-pressed', 'false');
  renderLibrary();
});
$('#view-mode-table')?.addEventListener('click', () => {
  state.viewMode = 'table';
  $('#view-mode-table')?.classList.add('active'); $('#view-mode-table')?.setAttribute('aria-pressed', 'true');
  $('#view-mode-grid')?.classList.remove('active'); $('#view-mode-grid')?.setAttribute('aria-pressed', 'false');
  renderLibrary();
});
$('#sort-select')?.addEventListener('change', ev => { state.sortBy = ev.target.value; applyLibraryFilters(); });
const resetAll = () => {
  state.searchQuery = ''; state.consoleFilter = ''; state.stateFilter = 'all';
  if ($('#global-search')) $('#global-search').value = '';
  if ($('#search-clear-btn')) $('#search-clear-btn').hidden = true;
  updateSidebarCounts(); applyLibraryFilters();
};
$('#reset-filters-btn')?.addEventListener('click', resetAll);
$('#empty-reset-btn')?.addEventListener('click', resetAll);

$('#global-search')?.addEventListener('input', ev => {
  const val = ev.target.value;
  if ($('#search-clear-btn')) $('#search-clear-btn').hidden = !val;
  renderOmniboxResults(val);
  clearTimeout(omniboxTimer);
  const trimmed = val.trim();
  if (trimmed.length >= 2) {
    omniboxTimer = setTimeout(async () => {
      try {
        const rows = await api('/api/v1/catalog/search?' + new URLSearchParams({ q: trimmed }));
        renderOmniboxResults(val, rows);
      } catch (e) { /* offline */ }
    }, 180);
  }
});
$('#global-search')?.addEventListener('keydown', ev => {
  if (ev.key === 'Enter') {
    const val = ev.target.value.trim();
    if (!val) return;
    ev.preventDefault();
    if ($('#omnibox-panel')) $('#omnibox-panel').hidden = true;
    state.searchQuery = val;
    openExploreConsoleLibrary('');
  }
});
$('#search-clear-btn')?.addEventListener('click', () => {
  if ($('#global-search')) $('#global-search').value = '';
  if ($('#search-clear-btn')) $('#search-clear-btn').hidden = true;
  if ($('#omnibox-panel')) $('#omnibox-panel').hidden = true;
  state.searchQuery = '';
  applyLibraryFilters();
});
document.addEventListener('click', ev => {
  const wrap = $('.omnibox-wrap');
  const panel = $('#omnibox-panel');
  if (panel && !panel.hidden && wrap && !wrap.contains(ev.target)) {
    panel.hidden = true;
  }
  const menu = $('#game-context-menu');
  if (menu && !menu.hidden && !menu.contains(ev.target)) closeGameContextMenu();
});

$('#library-load-more-btn')?.addEventListener('click', () => { state.visibleCount += PAGE_SIZE; renderLibrary(); });
$('#sync-covers-btn')?.addEventListener('click', () => libraryView.syncBatchCovers(24));
$('#settings-sync-covers-btn')?.addEventListener('click', () => libraryView.syncBatchCovers(24));

$('#copy-local-url-btn')?.addEventListener('click', () => copyText($('#share-local-url')?.value || '', 'URL local'));
$('#copy-lan-url-btn')?.addEventListener('click', () => copyText($('#share-lan-url')?.value || '', 'URL LAN'));
$('#toggle-sgdb-vis')?.addEventListener('click', () => {
  const inp = $('#setting-sgdb-key'); if (!inp) return;
  inp.type = inp.type === 'password' ? 'text' : 'password';
});
$('#test-sgdb-btn')?.addEventListener('click', () => testSteamGridKeyInto($('#setting-sgdb-key')?.value || '', $('#sgdb-test-status')));
$('#browse-roms-dir-btn')?.addEventListener('click', () => pickNativeFolder($('#setting-roms-dir'), 'Selecciona tu carpeta raíz de ROMs'));
$('#browse-covers-dir-btn')?.addEventListener('click', () => pickNativeFolder($('#setting-covers-dir'), 'Selecciona carpeta de carátulas WebP'));
$('#reset-covers-dir-btn')?.addEventListener('click', () => { if ($('#setting-covers-dir')) $('#setting-covers-dir').value = ''; });
$('#open-wizard-btn')?.addEventListener('click', () => openSetupWizard(1));
$('#settings-reopen-wizard-btn')?.addEventListener('click', () => openSetupWizard(1));
$('#settings-unpin-hero-btn')?.addEventListener('click', () => pinHeroGame('', ''));
$('#toggle-ai-vis')?.addEventListener('click', () => { const inp = $('#setting-ai-key'); if (inp) inp.type = inp.type === 'password' ? 'text' : 'password'; });
$('#test-ai-btn')?.addEventListener('click', async () => {
  const stEl = $('#ai-test-status'); if (!stEl) return;
  try {
    const res = await api('/api/v1/settings/test-ai', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ provider: $('#setting-ai-provider')?.value, apiKey: $('#setting-ai-key')?.value?.trim(), model: $('#setting-ai-model')?.value?.trim(), baseUrl: $('#setting-ai-base-url')?.value?.trim() }),
    });
    stEl.textContent = (res.ok ? '✓ ' : '✗ ') + res.message;
  } catch (err) { stEl.textContent = '✗ ' + err.message; }
});
$('#save-ai-settings-btn')?.addEventListener('click', async () => {
  try {
    const updated = await api('/api/v1/settings', {
      method: 'PUT', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ai_provider: $('#setting-ai-provider')?.value, ai_api_key: $('#setting-ai-key')?.value?.trim(), ai_model: $('#setting-ai-model')?.value?.trim() || 'gemini-2.5-flash', ai_base_url: $('#setting-ai-base-url')?.value?.trim() }),
    });
    state.settings = updated;
    toast('Preferencias IA guardadas', 'success');
    loadRecommendations(updated.ai_provider !== 'local');
  } catch (err) { toast('Error: ' + err.message, 'error'); }
});

$('#cover-picker-close-btn')?.addEventListener('click', closeCoverPicker);
$('#cover-picker-backdrop')?.addEventListener('click', ev => { if (ev.target.id === 'cover-picker-backdrop') closeCoverPicker(); });
$('#cover-picker-file')?.addEventListener('change', ev => {
  const file = ev.target.files?.[0]; if (!file) return;
  const r = new FileReader(); r.onload = () => saveCustomCover({ imageDataUrl: String(r.result || '') }); r.readAsDataURL(file);
});
$('#cover-picker-apply-url-btn')?.addEventListener('click', () => {
  const url = $('#cover-picker-url')?.value?.trim(); if (url) saveCustomCover({ imageUrl: url });
});
$('#cover-picker-auto-btn')?.addEventListener('click', async () => {
  const item = state.coverPickerEntry; if (!item) return;
  try {
    const res = await api(`/api/v1/covers/fetch/${encodeURIComponent(item.entryId)}`, { method: 'POST' });
    if (res.found) { item.hasCover = true; item.coverSource = res.source; item.coverUrl = res.coverUrl + '?v=' + Date.now(); refreshCoverStats(); renderHome(); applyLibraryFilters(); }
  } catch (e) {}
});

$('#save-cover-settings-btn')?.addEventListener('click', async () => {
  try {
    state.settings = await api('/api/v1/settings', {
      method: 'PUT', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ covers_dir: $('#setting-covers-dir')?.value?.trim() || '', steamgriddb_api_key: $('#setting-sgdb-key')?.value?.trim() || '', enable_libretro_fallback: $('#setting-libretro-fallback')?.checked !== false, cover_max_width: Number($('#setting-cover-width')?.value) || 260, cover_quality: Number($('#setting-cover-quality')?.value) || 76 }),
    });
    toast('Carátulas guardadas', 'success'); refreshCoverStats(); loadLibrary();
  } catch (err) { toast('Error: ' + err.message, 'error'); }
});
$('#clear-covers-btn')?.addEventListener('click', async () => {
  const res = await api('/api/v1/covers/cache', { method: 'DELETE' });
  toast(`Caché limpiado (${res.removed})`, 'info'); refreshCoverStats(); loadLibrary();
});
$('#toggle-ia-vis')?.addEventListener('click', () => { const inp = $('#setting-ia-secret'); if (inp) inp.type = inp.type === 'password' ? 'text' : 'password'; });
$('#test-ia-btn')?.addEventListener('click', () => testIaCredsInto($('#setting-ia-access')?.value?.trim() || '', $('#setting-ia-secret')?.value?.trim() || '', $('#setting-ia-cookie')?.value?.trim() || '', $('#ia-test-status')));
$('#save-ia-settings-btn')?.addEventListener('click', async () => {
  await api('/api/v1/settings', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ia_access_key: $('#setting-ia-access')?.value?.trim() || '', ia_secret_key: $('#setting-ia-secret')?.value?.trim() || '', ia_cookie: $('#setting-ia-cookie')?.value?.trim() || '' }) });
  toast('Credenciales IA guardadas', 'success');
});

$$('.settings-tab-btn').forEach(btn => btn.addEventListener('click', () => switchSettingsTab(btn.dataset.settingsTab)));
$('#setting-roms-dir')?.addEventListener('input', () => renderConsolePathsEditor());
$('#add-console-path-btn')?.addEventListener('click', () => {
  const ck = $('#add-console-path-select')?.value; const val = $('#add-console-path-val')?.value?.trim();
  if (!ck || !val) return;
  state.consolePathsDraft[ck] = val; $('#add-console-path-val').value = ''; renderConsolePathsEditor();
});
$('#save-paths-btn')?.addEventListener('click', async () => {
  const updated = await api('/api/v1/settings', {
    method: 'PUT', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ roms_dir: $('#setting-roms-dir')?.value?.trim() || '', console_paths: state.consolePathsDraft || {}, java_companion_url: $('#setting-java-url')?.value?.trim() || '' }),
  });
  state.settings = updated; toast('Rutas guardadas', 'success'); loadLibrary();
});
$('#rescan-lib-btn')?.addEventListener('click', async () => { const res = await api('/api/v1/library/rescan', { method: 'POST' }); toast(`Escaneo: ${res.count} archivos`, 'success'); loadLibrary(); });
$('#add-target-btn')?.addEventListener('click', async () => {
  const created = await api('/api/v1/targets', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ id: 'custom-' + Date.now().toString(36), name: 'Consola FTP', console: 'Custom', host: '192.168.100.180', port: 21, username: 'anonymous', password: '', base_path: '/roms', path_overrides: {}, notes: 'Personalizado' }),
  });
  state.targets.push(created); state.activeTargetId = created.id; renderTargets();
});
$('#copy-tinfoil-btn')?.addEventListener('click', () => copyText($('#tinfoil-url-block')?.textContent || '', 'URL Tinfoil'));
$('#minerva-search-form')?.addEventListener('submit', performMinervaSearch);
$$('.suggestion-chip').forEach(btn => btn.addEventListener('click', () => {
  if (btn.dataset.platform !== undefined && $('#minerva-platform')) $('#minerva-platform').value = btn.dataset.platform;
  if ($('#minerva-query')) $('#minerva-query').value = btn.dataset.q;
  performMinervaSearch();
}));
$('#refresh-downloads-btn')?.addEventListener('click', () => { pollDownloads(); pollTransfers(); });
$('#wiz-prev-btn')?.addEventListener('click', () => setWizardStep(state.wizardStep - 1));
$('#wiz-next-btn')?.addEventListener('click', () => setWizardStep(state.wizardStep + 1));
$('#wiz-finish-btn')?.addEventListener('click', finishSetupWizard);
$('#wiz-skip-btn')?.addEventListener('click', () => closeSetupWizard(true));
$('#wiz-pick-roms-btn')?.addEventListener('click', () => pickNativeFolder($('#wiz-roms-dir'), 'Carpeta raíz de ROMs', $('#wiz-roms-status')));
$('#wiz-pick-covers-btn')?.addEventListener('click', () => pickNativeFolder($('#wiz-covers-dir'), 'Carpeta carátulas WebP', $('#wiz-covers-status'), state.systemInfo?.defaultCoversDir || ''));
$('#wiz-default-covers-btn')?.addEventListener('click', () => { if ($('#wiz-covers-dir')) $('#wiz-covers-dir').value = ''; inspectWizardPath($('#wiz-covers-dir'), $('#wiz-covers-status'), state.systemInfo?.defaultCoversDir || ''); });
$('#drawer-close-btn')?.addEventListener('click', closeDrawer);
$('#drawer-backdrop')?.addEventListener('click', closeDrawer);

const originBase = location.origin || ('http://' + (location.host || '127.0.0.1:17894'));
if ($('#host-label')) $('#host-label').textContent = location.host || '127.0.0.1:17894';
if ($('#tinfoil-url-block')) $('#tinfoil-url-block').textContent = `${originBase}/tinfoil/index.json`;

const SPLASH_SEEN_KEY = 'oz_hylian_splash_seen';
const SPLASH_DURATION_MS = 3200;
const SPLASH_FADE_MS = 420;
let splashTimer = null;
let splashActive = false;

export function isHylianSplashVisible() {
  const el = $('#hylian-splash-overlay');
  return Boolean(splashActive && el && !el.hidden);
}

export function dismissHylianSplash() {
  const el = $('#hylian-splash-overlay');
  if (splashTimer) {
    clearTimeout(splashTimer);
    splashTimer = null;
  }
  try { sessionStorage.setItem(SPLASH_SEEN_KEY, '1'); } catch (e) {}
  if (!el || el.hidden) {
    splashActive = false;
    return;
  }
  if (el.classList.contains('is-fading')) return;
  splashActive = false;
  el.classList.add('is-fading');
  setTimeout(() => {
    el.hidden = true;
    el.classList.remove('is-fading');
  }, SPLASH_FADE_MS);
}

function initHylianSplash() {
  const el = $('#hylian-splash-overlay');
  if (!el) return;
  let seen = false;
  try { seen = sessionStorage.getItem(SPLASH_SEEN_KEY) === '1'; } catch (e) {}
  if (seen) {
    el.hidden = true;
    splashActive = false;
    return;
  }
  splashActive = true;
  el.hidden = false;
  el.addEventListener('click', dismissHylianSplash, { once: true });
  splashTimer = setTimeout(dismissHylianSplash, SPLASH_DURATION_MS);
}

Object.assign(actions, {
  ...homeView,
  ...sagasView,
  ...libraryView,
  ...catalogView,
  ...settingsView,
  switchView,
  setStateFilter,
  setExploreTab,
  openExploreConsoleLibrary,
  openExploreMinervaCatalog,
  getActiveConsoleTab,
  handleBackNavigation,
  setAmbientBackdrop,
  loadLibrary,
  isHylianSplashVisible,
  dismissHylianSplash,
});

initHylianSplash();
initActivitySheet();
initGamepadAndKeyboard();
checkHealth();
loadSettingsAndTargets();
refreshCoverStats();
loadSystemInfo();
loadLibrary();

let sseReloadTimer = null;
const hubEvents = connectHubEvents({
  onOpen: () => setSseConnectionState(true),
  onError: () => setSseConnectionState(false),
  'connected': () => setSseConnectionState(true),
  'covers.progress': st => applyCoverSyncStatus(st),
  'covers.complete': st => { applyCoverSyncStatus(st); refreshCoverStats(); },
  'download.progress': job => applyDownloadEvent(job),
  'download.state': job => applyDownloadEvent(job),
  'transfer.progress': job => applyTransferEvent(job),
  'transfer.state': job => applyTransferEvent(job),
  'sync.updated': () => recordActivityNotice('Sincronización delta LAN', 'Estado actualizado', 'emerald'),
  'library.updated': () => {
    clearTimeout(sseReloadTimer);
    sseReloadTimer = setTimeout(() => { loadLibrary(); renderActivitySheet(); }, 250);
  },
});
window.addEventListener('beforeunload', () => hubEvents.close());
