import {
  CONSOLES, SHORT_CODES, PAGE_SIZE, state, $, $$, actions,
  escapeHtml, consoleMeta, consoleIconSvg, joinLocalPath,
  resolveLocalDownloadDir, resolveTargetRemoteDir, consoleSelectOptionsHtml,
  formatBytes, hashString, wrapTitleLines, buildConsoleMotif,
  buildCoverSvg, renderCoverMarkup, toast, formatRelativeTime, findLibraryEntry,
} from '../state.js';
import { api } from '../api-client.js';

export function renderConsolePathsEditor() {
  const list = $('#console-paths-list');
  const sel = $('#add-console-path-select');
  if (!list) return;
  const draft = state.consolePathsDraft || {};
  const entries = Object.entries(draft).sort((a, b) => {
    const prio = k => (k === 'mame' ? '00' : k === 'arcade' ? '01' : k);
    return prio(a[0]).localeCompare(prio(b[0]));
  });
  $('#stab-paths-count').textContent = entries.length;
  const romsRoot = $('#setting-roms-dir')?.value?.trim() || state.settings?.roms_dir || 'F:\\Games\\RetroArch\\roms';

  list.innerHTML = entries.map(([key, val]) => {
    const meta = consoleMeta(key);
    const resolved = joinLocalPath(romsRoot, val);
    return `
      <div class="console-path-row" data-ck="${escapeHtml(key)}">
        <span class="nav-item-left" style="gap:8px;min-width:0;">
          <span class="console-dot" style="background:${meta.color};"></span>
          <strong style="font-size:12.5px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${escapeHtml(meta.full)}</strong>
          <span class="mini-badge">${escapeHtml(key)}</span>
        </span>
        <input class="text-input mono console-path-input" type="text" value="${escapeHtml(val)}" aria-label="Carpeta local para ${escapeHtml(meta.full)}" placeholder="${escapeHtml(key)}">
        <span class="path-preview-text" title="${escapeHtml(resolved)}">→ ${escapeHtml(resolved)}</span>
        <button class="btn btn-ghost btn-sm cp-del" type="button" aria-label="Quitar carpeta personalizada de ${escapeHtml(meta.full)}">Quitar</button>
      </div>
    `;
  }).join('');

  list.querySelectorAll('.console-path-row').forEach(row => {
    const ck = row.dataset.ck;
    const inp = row.querySelector('.console-path-input');
    const preview = row.querySelector('.path-preview-text');
    inp.addEventListener('input', () => {
      state.consolePathsDraft[ck] = inp.value.trim();
      const r = joinLocalPath($('#setting-roms-dir').value.trim(), inp.value.trim());
      preview.textContent = `→ ${r}`;
      preview.title = r;
    });
    row.querySelector('.cp-del').addEventListener('click', () => {
      delete state.consolePathsDraft[ck];
      renderConsolePathsEditor();
    });
  });

  if (sel) {
    sel.innerHTML = consoleSelectOptionsHtml(new Set(Object.keys(draft))) || '<option value="">Todas configuradas</option>';
  }
  const tinfoilFolder = $('#tinfoil-local-folder');
  if (tinfoilFolder) tinfoilFolder.textContent = resolveLocalDownloadDir('switch');
}

export function switchSettingsTab(tabName) {
  state.activeSettingsTab = tabName;
  $$('.settings-tab-btn').forEach(btn => {
    const active = btn.dataset.settingsTab === tabName;
    btn.classList.toggle('active', active);
    if (active) btn.setAttribute('aria-current', 'true');
    else btn.removeAttribute('aria-current');
  });
  $$('.settings-panel').forEach(panel => {
    panel.hidden = panel.id !== 'spanel-' + tabName;
  });
  if (tabName === 'covers') refreshCoverStats();
  if (tabName === 'share') loadSystemInfo();
}

export function renderTargets() {
  const list = $('#targets-list');
  const selector = $('#bridge-selector-grid');
  if ($('#nav-targets-count')) $('#nav-targets-count').textContent = state.targets.length;
  if ($('#bridge-summary-label')) $('#bridge-summary-label').textContent = `${state.targets.length} perfiles FTP configurados`;

  if (!state.targets.length) {
    if (selector) selector.innerHTML = '';
    list.innerHTML = '<div class="empty-card"><h3>Sin consolas configuradas</h3><p>Añade una consola FTP para empezar.</p></div>';
    return;
  }
  if (!state.activeTargetId || !state.targets.some(t => t.id === state.activeTargetId)) {
    state.activeTargetId = state.targets[0].id;
  }

  if (selector) {
    selector.innerHTML = state.targets.map(t => {
      const active = t.id === state.activeTargetId;
      const rulesCount = Object.keys(t.path_overrides || {}).length;
      return `
        <button type="button" class="bridge-target-pill ${active ? 'active' : ''}" data-tid="${escapeHtml(t.id)}" aria-pressed="${active}">
          <div style="display:flex;align-items:center;justify-content:space-between;gap:8px;width:100%;">
            <strong style="font-size:13.5px;color:var(--text-primary);">${escapeHtml(t.name)}</strong>
            <span class="mini-badge">${escapeHtml(t.console || 'FTP')}</span>
          </div>
          <div class="mono" style="font-size:11.5px;color:var(--text-muted);">${escapeHtml(t.host || '0.0.0.0')}:${Number(t.port || 21)} · ${rulesCount} rutas</div>
        </button>
      `;
    }).join('');
    selector.querySelectorAll('.bridge-target-pill').forEach(btn => {
      btn.addEventListener('click', () => {
        state.activeTargetId = btn.dataset.tid;
        renderTargets();
      });
    });
  }

  const t = state.targets.find(x => x.id === state.activeTargetId) || state.targets[0];
  const tid = escapeHtml(t.id);
  const overrides = { ...(t.path_overrides || {}) };
  list.innerHTML = '';

  const card = document.createElement('div');
  card.className = 'surface-panel';
  card.innerHTML = `
    <div class="target-card-head">
      <div>
        <h3 style="margin:0;font-size:16px;">${escapeHtml(t.name)}</h3>
        <span class="target-status" id="t-status-${tid}">${escapeHtml(t.notes || 'Listo')}</span>
      </div>
      <div style="display:flex;gap:8px;flex-wrap:wrap;">
        <button class="btn btn-secondary btn-sm t-test" type="button">Probar FTP</button>
        <button class="btn btn-primary btn-sm t-save" type="button">Guardar perfil</button>
        ${['ps4-emusuni', 'ps3-webman', 'switch-ftp'].includes(t.id) ? '' : '<button class="btn btn-danger btn-sm t-del" type="button">Eliminar</button>'}
      </div>
    </div>

    <div class="bridge-form-grid">
      <div class="field-group"><label for="t-name-${tid}">Nombre del perfil</label><input id="t-name-${tid}" class="text-input" value="${escapeHtml(t.name || '')}"></div>
      <div class="field-group"><label for="t-host-${tid}">Host / IP</label><input id="t-host-${tid}" class="text-input mono" value="${escapeHtml(t.host || '')}"></div>
      <div class="field-group"><label for="t-port-${tid}">Puerto FTP</label><input id="t-port-${tid}" class="text-input mono" type="number" value="${Number(t.port || 21)}"></div>
      <div class="field-group"><label for="t-user-${tid}">Usuario</label><input id="t-user-${tid}" class="text-input mono" value="${escapeHtml(t.username || 'anonymous')}"></div>
      <div class="field-group"><label for="t-pass-${tid}">Contraseña</label><input id="t-pass-${tid}" class="text-input mono" type="password" value="${escapeHtml(t.password || '')}"></div>
      <div class="field-group"><label for="t-base-${tid}">Ruta base fallback</label><input id="t-base-${tid}" class="text-input mono" value="${escapeHtml(t.base_path || 'Roms')}"></div>
    </div>

    <div style="border-top:1px solid var(--border-subtle);padding-top:14px;display:flex;flex-direction:column;gap:10px;">
      <div>
        <div style="font-size:13.5px;font-weight:600;color:var(--text-primary);">Rutas FTP por consola (<code>path_overrides</code>)</div>
        <div class="setting-hint">Cada regla manda esa plataforma a su carpeta exacta. Si falta una consola, se usa <code id="t-fallback-preview">${escapeHtml((t.base_path || 'Roms').replace(/\/+$/, ''))}/&lt;consola&gt;</code>.</div>
      </div>
      <div class="override-list target-overrides-list"></div>
      <div class="add-rule-row">
        <select class="control-select ov-add-key" aria-label="Consola para nueva regla FTP"></select>
        <input class="text-input mono ov-add-val" type="text" placeholder="Ej. Roms/arcade" style="flex:1 1 180px;" aria-label="Ruta remota FTP para nueva consola">
        <button class="btn btn-secondary btn-sm ov-add-btn" type="button">+ Añadir ruta FTP</button>
      </div>
    </div>
  `;

  const baseInput = card.querySelector(`#t-base-${t.id}`);
  const fbPreview = card.querySelector('#t-fallback-preview');
  baseInput.addEventListener('input', () => {
    fbPreview.textContent = `${baseInput.value.trim().replace(/\/+$/, '') || 'Roms'}/<consola>`;
  });

  const ovList = card.querySelector('.target-overrides-list');
  const ovSelect = card.querySelector('.ov-add-key');
  const ovAddVal = card.querySelector('.ov-add-val');

  const renderOverrides = () => {
    const entries = Object.entries(overrides).sort((a, b) => a[0].localeCompare(b[0]));
    ovList.innerHTML = !entries.length
      ? '<div class="setting-hint">Sin reglas específicas: todas las consolas usarán la ruta base fallback.</div>'
      : entries.map(([ck, rpath]) => {
          const meta = consoleMeta(ck);
          return `
            <div class="override-row" data-ck="${escapeHtml(ck)}">
              <span class="nav-item-left" style="gap:8px;min-width:0;">
                <span class="console-dot" style="background:${meta.color};"></span>
                <strong style="font-size:12.5px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${escapeHtml(meta.full)}</strong>
                <span class="mini-badge">${escapeHtml(ck)}</span>
              </span>
              <input class="text-input mono ov-val-input" type="text" value="${escapeHtml(rpath)}" aria-label="Ruta FTP en ${escapeHtml(t.name)} para ${escapeHtml(meta.full)}">
              <span class="path-preview-text">FTP → ${escapeHtml(rpath)}</span>
              <button class="btn btn-ghost btn-sm ov-del" type="button" aria-label="Quitar regla FTP de ${escapeHtml(meta.full)}">Quitar</button>
            </div>
          `;
        }).join('');
    ovList.querySelectorAll('.override-row').forEach(row => {
      const ck = row.dataset.ck;
      const inp = row.querySelector('.ov-val-input');
      const prev = row.querySelector('.path-preview-text');
      inp.addEventListener('input', () => {
        overrides[ck] = inp.value.trim();
        prev.textContent = `FTP → ${inp.value.trim()}`;
      });
      row.querySelector('.ov-del').addEventListener('click', () => {
        delete overrides[ck];
        renderOverrides();
      });
    });
    ovSelect.innerHTML = consoleSelectOptionsHtml(new Set(Object.keys(overrides))) || '<option value="">Todas definidas</option>';
    const defKey = ovSelect.value || 'mame';
    ovAddVal.placeholder = `Ej. ${(baseInput.value.trim().replace(/\/+$/, '') || 'Roms')}/${defKey === 'mame' ? 'arcade' : defKey}`;
  };

  renderOverrides();
  ovSelect.addEventListener('change', () => {
    const defKey = ovSelect.value || 'arcade';
    ovAddVal.placeholder = `Ej. ${(baseInput.value.trim().replace(/\/+$/, '') || 'Roms')}/${defKey === 'mame' ? 'arcade' : defKey}`;
  });
  card.querySelector('.ov-add-btn').addEventListener('click', () => {
    const k = ovSelect.value;
    const v = ovAddVal.value.trim();
    if (!k || !v) { toast('Selecciona consola y escribe una ruta FTP', 'error'); return; }
    overrides[k] = v;
    ovAddVal.value = '';
    renderOverrides();
  });

  const readTargetPayload = () => {
    const cleanOverrides = {};
    Object.entries(overrides).forEach(([k, v]) => {
      const val = String(v || '').trim();
      if (k && val) cleanOverrides[k] = val;
    });
    return {
      ...t,
      name: card.querySelector(`#t-name-${t.id}`).value.trim() || t.name,
      host: card.querySelector(`#t-host-${t.id}`).value.trim(),
      port: Number(card.querySelector(`#t-port-${t.id}`).value) || 21,
      username: card.querySelector(`#t-user-${t.id}`).value.trim(),
      password: card.querySelector(`#t-pass-${t.id}`).value,
      base_path: card.querySelector(`#t-base-${t.id}`).value.trim(),
      path_overrides: cleanOverrides
    };
  };

  card.querySelector('.t-save').addEventListener('click', async () => {
    try {
      const updated = await api(`/api/v1/targets/${encodeURIComponent(t.id)}`, {
        method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(readTargetPayload())
      });
      state.targets = state.targets.map(x => x.id === t.id ? updated : x);
      renderTargets();
      toast(`Perfil guardado: ${updated.name}`, 'success');
    } catch (err) { toast('Error guardando perfil: ' + err.message, 'error'); }
  });
  card.querySelector('.t-test').addEventListener('click', async () => {
    const stEl = card.querySelector(`#t-status-${t.id}`);
    stEl.textContent = 'Probando conexión FTP…';
    try {
      const updated = await api(`/api/v1/targets/${encodeURIComponent(t.id)}`, {
        method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(readTargetPayload())
      });
      state.targets = state.targets.map(x => x.id === t.id ? updated : x);
      const res = await api(`/api/v1/targets/${encodeURIComponent(t.id)}/test`, { method: 'POST' });
      stEl.textContent = (res.ok ? '✓ ' : '✗ ') + res.message;
      stEl.style.color = res.ok ? 'var(--success)' : 'var(--danger)';
    } catch (err) {
      stEl.textContent = '✗ ' + err.message;
      stEl.style.color = 'var(--danger)';
    }
  });
  const delBtn = card.querySelector('.t-del');
  if (delBtn) {
    delBtn.addEventListener('click', async () => {
      await api(`/api/v1/targets/${encodeURIComponent(t.id)}`, { method: 'DELETE' });
      state.targets = state.targets.filter(x => x.id !== t.id);
      state.activeTargetId = state.targets[0]?.id || null;
      renderTargets();
      toast('Perfil eliminado', 'info');
    });
  }
  list.appendChild(card);
}

export async function loadSettingsAndTargets() {
  try {
    const s = await api('/api/v1/settings');
    state.settings = s;
    state.targets = s.targets || [];
    state.consolePathsDraft = { ...(s.console_paths || { mame: 'arcade', arcade: 'arcade' }) };
    $('#setting-sgdb-key').value = s.steamgriddb_api_key || '';
    $('#setting-ia-access').value = s.ia_access_key || '';
    $('#setting-ia-secret').value = s.ia_secret_key || '';
    $('#setting-ia-cookie').value = s.ia_cookie || '';
    $('#setting-libretro-fallback').checked = s.enable_libretro_fallback !== false;
    $('#setting-cover-width').value = s.cover_max_width || 260;
    $('#setting-cover-quality').value = s.cover_quality || 76;
    $('#setting-roms-dir').value = s.roms_dir || '';
    if ($('#setting-covers-dir')) $('#setting-covers-dir').value = s.covers_dir || '';
    $('#setting-java-url').value = s.java_companion_url || '';
    if ($('#setting-ai-provider')) $('#setting-ai-provider').value = s.ai_provider || 'local';
    if ($('#setting-ai-key')) $('#setting-ai-key').value = s.ai_api_key || '';
    if ($('#setting-ai-model')) $('#setting-ai-model').value = s.ai_model || 'gemini-2.5-flash';
    if ($('#setting-ai-base-url')) $('#setting-ai-base-url').value = s.ai_base_url || '';
    const aiBadge = $('#ai-mode-badge');
    if (aiBadge) aiBadge.hidden = true;
    renderConsolePathsEditor();
    renderTargets();
    actions.renderHome();
    const forceSetup = new URLSearchParams(location.search).get('setup') === '1';
    if (!state.onboardingPrompted && (s.onboarding_completed === false || forceSetup)) {
      state.onboardingPrompted = true;
      openSetupWizard(1);
    }
  } catch (e) { /* ignore */ }
}

export function updateCoverStatsUi(st) {
  if (!st || typeof st.cachedCount !== 'number') return;
  if ($('#health-covers')) $('#health-covers').textContent = `${st.cachedCount} covers`;
  if ($('#covers-cache-badge')) $('#covers-cache-badge').textContent = `${st.cachedCount}`;
  if ($('#covers-cache-stats-text')) {
    $('#covers-cache-stats-text').textContent = `${st.cachedCount} carátulas · ${Math.round((st.cachedBytes || 0) / 1024)} KB`;
  }
}

export async function refreshCoverStats() {
  try { updateCoverStatsUi(await api('/api/v1/covers/status')); } catch (e) { /* ignore */ }
}

export async function loadSystemInfo() {
  try {
    const info = await api('/api/v1/system/info');
    state.systemInfo = info;
    const port = location.port || '17894';
    $('#share-local-url').value = `http://127.0.0.1:${port}/`;
    $('#share-lan-url').value = `http://${info.lanIp || '192.168.100.173'}:${port}/`;
    $('#share-mode-badge').textContent = info.desktopMode ? 'Ventana Desktop' : 'Servidor Hub';
    $('#share-portable-path').value = info.portableZipExists
      ? `Listo para compartir: ${info.portableZipPath}`
      : `No compilado aún (${info.portableZipPath})`;
    if (info.defaultCoversDir) {
      if ($('#setting-covers-default-hint')) $('#setting-covers-default-hint').textContent = info.defaultCoversDir;
      if ($('#setting-covers-dir')) $('#setting-covers-dir').placeholder = info.defaultCoversDir;
      if ($('#wiz-covers-dir')) $('#wiz-covers-dir').placeholder = info.defaultCoversDir;
    }
  } catch (e) { /* ignore */ }
}


export async function testSteamGridKeyInto(apiKey, statusEl) {
  if (!statusEl) return;
  statusEl.textContent = 'Probando clave…';
  try {
    const res = await api('/api/v1/settings/test-steamgriddb', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ apiKey: String(apiKey || '').trim() })
    });
    statusEl.textContent = (res.ok ? '✓ ' : '✗ ') + res.message;
    statusEl.style.color = res.ok ? 'var(--success)' : 'var(--danger)';
  } catch (err) {
    statusEl.textContent = '✗ ' + err.message;
    statusEl.style.color = 'var(--danger)';
  }
}

export async function testIaCredsInto(accessKey, secretKey, cookie, statusEl) {
  if (!statusEl) return;
  statusEl.textContent = 'Probando credenciales IA…';
  try {
    const res = await api('/api/v1/settings/test-ia', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ accessKey, secretKey, cookie })
    });
    statusEl.textContent = (res.ok ? '✓ ' : '✗ ') + res.message;
    statusEl.style.color = res.ok ? 'var(--success)' : 'var(--danger)';
  } catch (err) {
    statusEl.textContent = '✗ ' + err.message;
    statusEl.style.color = 'var(--danger)';
  }
}

export async function inspectWizardPath(inputEl, statusEl, defaultPath = '') {
  if (!statusEl) return;
  const val = (inputEl?.value || '').trim() || defaultPath;
  if (!val) {
    statusEl.textContent = 'Se usará la carpeta portable por defecto.';
    statusEl.style.color = 'var(--text-muted)';
    return;
  }
  try {
    const res = await api('/api/v1/system/inspect-path', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ path: val })
    });
    statusEl.textContent = (res.valid ? (res.exists ? '✓ ' : 'ℹ ') : '✗ ') + res.message;
    statusEl.style.color = !res.valid ? 'var(--danger)' : (res.exists ? 'var(--success)' : 'var(--text-secondary)');
  } catch (err) {
    statusEl.textContent = err.message;
  }
}

export async function pickNativeFolder(inputEl, title, statusEl = null, defaultPath = '') {
  try {
    const res = await api('/api/v1/system/pick-folder', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title, initialDir: (inputEl?.value || '').trim() || defaultPath })
    });
    if (res.ok && res.path && inputEl) {
      inputEl.value = res.path;
      inputEl.dispatchEvent(new Event('input', { bubbles: true }));
      if (statusEl) inspectWizardPath(inputEl, statusEl, defaultPath);
    } else if (res.supported === false) {
      toast(res.message || 'Escribe la ruta manualmente.', 'info');
    }
  } catch (err) {
    toast('No se pudo abrir el selector: ' + err.message, 'error');
  }
}

export function selectWizardConsole(consoleId) {
  state.wizardConsoleId = consoleId || 'pc';
  $$('#setup-wizard-backdrop .wiz-console-card').forEach(card => {
    const active = card.dataset.wizConsole === state.wizardConsoleId;
    card.classList.toggle('active', active);
    card.setAttribute('aria-pressed', String(active));
  });
  const fields = $('#wiz-ftp-fields');
  if (!fields) return;
  if (state.wizardConsoleId === 'pc') {
    fields.hidden = true;
    return;
  }
  fields.hidden = false;
  const target = state.targets.find(t => t.id === state.wizardConsoleId);
  if (target) {
    $('#wiz-ftp-host').value = target.host || '';
    $('#wiz-ftp-port').value = target.port || 21;
    $('#wiz-ftp-base').value = target.base_path || '';
  }
}

export function renderWizardSummary() {
  const grid = $('#wiz-summary-grid');
  if (!grid) return;
  const romsPath = $('#wiz-roms-dir').value.trim() || state.settings?.roms_dir || 'Documentos\\OzRetroRom\\roms';
  const covRaw = $('#wiz-covers-dir').value.trim();
  const covDisplay = covRaw || (state.systemInfo?.defaultCoversDir ? `Portable (${state.systemInfo.defaultCoversDir})` : 'Por defecto portable (data\\covers)');
  const hasSgdb = Boolean($('#wiz-sgdb-key').value.trim());
  const hasLibretro = $('#wiz-libretro-fallback').checked;
  const hasIa = Boolean($('#wiz-ia-access').value.trim() && $('#wiz-ia-secret').value.trim());
  const cLabel = state.wizardConsoleId === 'pc'
    ? 'Solo este PC (puedes agregar FTP luego)'
    : `${state.targets.find(t => t.id === state.wizardConsoleId)?.name || state.wizardConsoleId} (${$('#wiz-ftp-host').value.trim()}:${$('#wiz-ftp-port').value || 21})`;

  const cards = [
    ['Carpeta ROMs', romsPath],
    ['Carpeta Carátulas', covDisplay],
    ['Portadas WebP', hasSgdb ? 'SteamGridDB HD + Libretro' : (hasLibretro ? 'Libretro Thumbnails automático' : 'Solo manual / SVG retro')],
    ['Internet Archive', hasIa ? 'Claves S3 personales configuradas' : 'Catálogo público directo (sin clave)'],
    ['Consola FTP', cLabel]
  ];
  grid.innerHTML = cards.map(([k, v]) => `
    <div class="wiz-summary-card">
      <span class="meta-key">${escapeHtml(k)}</span>
      <span class="mono" style="font-size:12px;color:var(--text-primary);word-break:break-all;">${escapeHtml(v)}</span>
    </div>
  `).join('');
}

export function setWizardStep(step) {
  const clamped = Math.max(1, Math.min(5, Number(step) || 1));
  state.wizardStep = clamped;
  for (let i = 1; i <= 5; i++) {
    const pane = $(`#wiz-step-${i}`);
    if (pane) pane.hidden = i !== clamped;
  }
  $$('#setup-wizard-backdrop .wizard-step-btn').forEach(btn => {
    const sNum = Number(btn.dataset.wizStep);
    btn.classList.toggle('active', sNum === clamped);
    btn.classList.toggle('done', sNum < clamped);
    btn.setAttribute('aria-current', sNum === clamped ? 'step' : 'false');
  });
  $('#wiz-prev-btn').disabled = clamped === 1;
  $('#wiz-step-counter').textContent = `Paso ${clamped} de 5`;
  $('#wiz-next-btn').hidden = clamped === 5;
  $('#wiz-finish-btn').hidden = clamped !== 5;
  if (clamped === 5) renderWizardSummary();
}

export async function openSetupWizard(step = 1) {
  if (!state.systemInfo) await loadSystemInfo();
  const s = state.settings || {};
  $('#wiz-roms-dir').value = s.roms_dir || state.systemInfo?.romsDir || '';
  $('#wiz-covers-dir').value = s.covers_dir || '';
  $('#wiz-covers-dir').placeholder = state.systemInfo?.defaultCoversDir || 'data\\covers';
  $('#wiz-sgdb-key').value = s.steamgriddb_api_key || '';
  $('#wiz-libretro-fallback').checked = s.enable_libretro_fallback !== false;
  $('#wiz-ia-access').value = s.ia_access_key || '';
  $('#wiz-ia-secret').value = s.ia_secret_key || '';
  $('#wiz-sgdb-status').textContent = '';
  $('#wiz-ia-status').textContent = '';
  $('#wiz-ftp-status').textContent = '';
  selectWizardConsole('pc');
  setWizardStep(step);
  $('#setup-wizard-backdrop').hidden = false;
  inspectWizardPath($('#wiz-roms-dir'), $('#wiz-roms-status'));
  inspectWizardPath($('#wiz-covers-dir'), $('#wiz-covers-status'), state.systemInfo?.defaultCoversDir || '');
  setTimeout(() => $('#wiz-roms-dir').focus(), 40);
}

export async function closeSetupWizard(markCompleted = true) {
  $('#setup-wizard-backdrop').hidden = true;
  if (markCompleted && state.settings && state.settings.onboarding_completed === false) {
    try {
      const updated = await api('/api/v1/settings', {
        method: 'PUT', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ onboarding_completed: true })
      });
      state.settings = updated;
    } catch (e) { /* ignore */ }
  }
}

export async function finishSetupWizard() {
  const btn = $('#wiz-finish-btn');
  btn.disabled = true;
  btn.textContent = 'Guardando…';
  try {
    const patch = {
      roms_dir: $('#wiz-roms-dir').value.trim(),
      covers_dir: $('#wiz-covers-dir').value.trim(),
      steamgriddb_api_key: $('#wiz-sgdb-key').value.trim(),
      enable_libretro_fallback: $('#wiz-libretro-fallback').checked,
      ia_access_key: $('#wiz-ia-access').value.trim(),
      ia_secret_key: $('#wiz-ia-secret').value.trim(),
      onboarding_completed: true
    };
    const updated = await api('/api/v1/settings', {
      method: 'PUT', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(patch)
    });
    state.settings = updated;

    if (state.wizardConsoleId && state.wizardConsoleId !== 'pc') {
      const existing = state.targets.find(t => t.id === state.wizardConsoleId);
      if (existing) {
        const payload = {
          ...existing,
          host: $('#wiz-ftp-host').value.trim() || existing.host,
          port: Number($('#wiz-ftp-port').value) || existing.port || 21,
          base_path: $('#wiz-ftp-base').value.trim() || existing.base_path
        };
        await api(`/api/v1/targets/${encodeURIComponent(existing.id)}`, {
          method: 'PUT', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        });
        state.activeTargetId = existing.id;
      }
    }

    await loadSettingsAndTargets();
    await loadSystemInfo();
    await refreshCoverStats();
    await actions.loadLibrary();
    $('#setup-wizard-backdrop').hidden = true;
    toast('¡Configuración inicial guardada! Bóveda lista para jugar.', 'success');
  } catch (err) {
    toast('Error guardando configuración inicial: ' + err.message, 'error');
  } finally {
    btn.disabled = false;
    btn.textContent = 'Guardar y entrar a la Bóveda';
  }
}

$$('#setup-wizard-backdrop .wizard-step-btn').forEach(btn => {
  btn.addEventListener('click', () => setWizardStep(Number(btn.dataset.wizStep)));
});
$$('#setup-wizard-backdrop .wiz-console-card').forEach(card => {
  card.addEventListener('click', () => selectWizardConsole(card.dataset.wizConsole));
});
