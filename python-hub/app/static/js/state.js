export const CONSOLES = {
  nes: { label: 'NES', full: 'Nintendo NES', color: '#FCA5A5', bg: '#2B161B', target: 'Roms/nes' },
  snes: { label: 'SNES', full: 'Super Nintendo', color: '#C4B5FD', bg: '#1D1833', target: 'Roms/snes' },
  gb: { label: 'GB', full: 'Game Boy', color: '#BEF264', bg: '#1B2413', target: 'Roms/gb' },
  gbc: { label: 'GBC', full: 'Game Boy Color', color: '#A3E635', bg: '#182413', target: 'Roms/gbc' },
  gba: { label: 'GBA', full: 'Game Boy Advance', color: '#A5B4FC', bg: '#181E36', target: 'Roms/gba' },
  nds: { label: 'NDS', full: 'Nintendo DS', color: '#93C5FD', bg: '#142033', target: 'Roms/nds' },
  '3ds': { label: '3DS', full: 'Nintendo 3DS', color: '#F9A8D4', bg: '#2A1624', target: 'Roms/3ds' },
  n64: { label: 'N64', full: 'Nintendo 64', color: '#6EE7B7', bg: '#122820', target: 'Roms/n64' },
  gc: { label: 'GameCube', full: 'Nintendo GameCube', color: '#93C5FD', bg: '#152035', target: 'Roms/gc' },
  wii: { label: 'Wii', full: 'Nintendo Wii', color: '#BAE6FD', bg: '#132433', target: 'Roms/wii' },
  wiiu: { label: 'Wii U', full: 'Nintendo Wii U', color: '#67E8F9', bg: '#12242E', target: 'Roms/wiiu' },
  switch: { label: 'Switch', full: 'Nintendo Switch', color: '#F87171', bg: '#2C1419', target: 'switch/nsp' },
  genesis: { label: 'Genesis', full: 'Mega Drive / Genesis', color: '#7DD3FC', bg: '#122233', target: 'Roms/md' },
  sms: { label: 'Master Sys', full: 'Sega Master System', color: '#60A5FA', bg: '#131F33', target: 'Roms/sms' },
  gg: { label: 'Game Gear', full: 'Sega Game Gear', color: '#818CF8', bg: '#181D35', target: 'Roms/gg' },
  segacd: { label: 'Sega CD', full: 'Sega CD', color: '#38BDF8', bg: '#102130', target: 'Roms/segacd' },
  sega32x: { label: '32X', full: 'Sega 32X', color: '#FBBF24', bg: '#282012', target: 'Roms/sega32x' },
  saturn: { label: 'Saturn', full: 'Sega Saturn', color: '#E9D5FF', bg: '#231730', target: 'Roms/saturn' },
  dc: { label: 'Dreamcast', full: 'Sega Dreamcast', color: '#FDBA74', bg: '#2B1D12', target: 'Roms/dc' },
  ps1: { label: 'PS1', full: 'PlayStation', color: '#5EEAD4', bg: '#102627', target: 'Roms/ps1' },
  ps2: { label: 'PS2', full: 'PlayStation 2', color: '#93C5FD', bg: '#131D34', target: 'Roms/ps2' },
  ps3: { label: 'PS3', full: 'PlayStation 3', color: '#A5B4FC', bg: '#161B33', target: 'PS3ISO' },
  ps4: { label: 'PS4', full: 'PlayStation 4', color: '#60A5FA', bg: '#111C33', target: 'PS4' },
  psp: { label: 'PSP', full: 'PlayStation Portable', color: '#CBD5E1', bg: '#1A202C', target: 'Roms/psp' },
  psvita: { label: 'PS Vita', full: 'PlayStation Vita', color: '#7DD3FC', bg: '#12202E', target: 'PSVITA' },
  arcade: { label: 'Arcade', full: 'Arcade (FBNeo / MAME)', color: '#FCD34D', bg: '#292011', target: 'Roms/arcade' },
  mame: { label: 'MAME', full: 'MAME (Arcade)', color: '#FBBF24', bg: '#292011', target: 'Roms/arcade' },
  pce: { label: 'PC Engine', full: 'PC Engine', color: '#FDBA74', bg: '#281C14', target: 'Roms/pce' },
  atari2600: { label: 'Atari 2600', full: 'Atari 2600', color: '#FDA4AF', bg: '#28161B', target: 'Roms/atari2600' },
  atari7800: { label: 'Atari 7800', full: 'Atari 7800', color: '#FDBA74', bg: '#261B14', target: 'Roms/atari7800' },
  atarilynx: { label: 'Lynx', full: 'Atari Lynx', color: '#FCD34D', bg: '#241E12', target: 'Roms/atarilynx' },
  atarijaguar: { label: 'Jaguar', full: 'Atari Jaguar', color: '#FCA5A5', bg: '#261618', target: 'Roms/atarijaguar' },
  msx: { label: 'MSX', full: 'MSX / MSX2', color: '#CBD5E1', bg: '#191E28', target: 'Roms/msx' },
  amiga: { label: 'Amiga', full: 'Commodore Amiga', color: '#F9A8D4', bg: '#241621', target: 'Roms/amiga' }
};

export const SHORT_CODES = {
  nes: 'NES', snes: 'SNES', gb: 'GB', gbc: 'GBC', gba: 'GBA', nds: 'NDS', '3ds': '3DS',
  n64: 'N64', gc: 'GC', wii: 'WII', wiiu: 'WIIU', switch: 'NSW', genesis: 'GEN',
  sms: 'SMS', gg: 'GG', segacd: 'SCD', sega32x: '32X', saturn: 'SAT', dc: 'DC',
  ps1: 'PS1', ps2: 'PS2', ps3: 'PS3', ps4: 'PS4', psp: 'PSP', psvita: 'VITA',
  arcade: 'ARC', pce: 'PCE', atari2600: '2600', atari7800: '7800', atarilynx: 'LYNX',
  atarijaguar: 'JAG', msx: 'MSX', amiga: 'AMG'
};

export const PAGE_SIZE = 60;
export const state = {
  view: 'home',
  exploreTab: 'sagas',
  viewMode: 'grid',
  searchQuery: '',
  stateFilter: 'all',
  consoleFilter: '',
  sortBy: 'title-asc',
  showSystemFiles: false,
  libraryGrouped: true,
  maintenanceReport: null,
  homeData: null,
  visibleCount: PAGE_SIZE,
  libraryItems: [],
  filteredItems: [],
  minervaResults: [],
  targets: [],
  settings: null,
  systemInfo: null,
  onboardingPrompted: false,
  wizardStep: 1,
  wizardConsoleId: 'pc',
  selectedEntryId: null,
  activeTargetId: null,
  activeSettingsTab: 'paths',
  consolePathsDraft: {},
  heroItem: null,
  heroOverrideItem: null,
  lastHeroDescId: null,
  recommendations: null,
  recsLoading: false,
  sagas: [],
  sagasScope: 'owned',
  activeSagaId: null,
  sagaOwnedOnly: false,
  sagaAddEntry: null,
  sagaArtResolved: false,
  sagaGhostCovers: {},
  sagaGhostInFlight: new Set(),
  recsOffset: 0,
  sessionSeed: Math.floor(Math.random() * 100000),
  featuredSeed: Math.floor(Math.random() * 100000),
  seenRecIds: new Set(),
  moodMode: 'quick',
  moodOffset: 0,
  seenMoodIdsByMode: {},
  coverPickerEntry: null,
  downloadJobs: new Map(),
  notifiedDownloads: new Map(),
  transferJobs: new Map(),
  downloadTimer: null,
  transferTimer: null,
  coverSyncTimer: null,
  lastFocusedElement: null
};

export const $ = sel => document.querySelector(sel);
export const $$ = sel => [...document.querySelectorAll(sel)];

export function escapeHtml(text) {
  if (text === null || text === undefined) return '';
  return String(text)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

export function consoleMeta(key) {
  if (CONSOLES[key]) return CONSOLES[key];
  const label = (key || 'ROM').slice(0, 10).toUpperCase();
  return { label, full: label, color: '#CBD5E1', bg: '#181E29', target: 'Roms/' + (key || 'other') };
}

export function consoleIconSvg(consoleKey, size = 14) {
  const ck = String(consoleKey || '').toLowerCase();
  const s = Number(size) || 14;
  const wrap = inner => `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${inner}</svg>`;
  if (ck === 'snes') return wrap('<circle cx="12" cy="5.5" r="2.1" fill="currentColor"/><circle cx="18.5" cy="12" r="2.1"/><circle cx="12" cy="18.5" r="2.1" fill="currentColor"/><circle cx="5.5" cy="12" r="2.1"/>');
  if (ck === 'nes') return wrap('<rect x="2.5" y="6.5" width="19" height="11" rx="2.5"/><path d="M7.5 9.5v5M5 12h5"/><circle cx="14.5" cy="13.5" r="1.3" fill="currentColor"/><circle cx="18" cy="11" r="1.3" fill="currentColor"/>');
  if (ck === 'n64') return wrap('<path d="M4 9.5c0-2 1.8-3.5 4-3.5h8c2.2 0 4 1.5 4 3.5l1.5 6.5c.3 1.4-1 2.5-2.2 1.8L16 15.5l-2 4.5c-.6 1.2-3.4 1.2-4 0l-2-4.5-3.3 2.3C3.5 18.5 2.2 17.4 2.5 16L4 9.5Z"/>');
  if (ck === 'gc') return wrap('<path d="M12 3 20 7.5v9L12 21l-8-4.5v-9L12 3Z"/><path d="M12 12 20 7.5M12 12v9M12 12 4 7.5"/>');
  if (ck === 'gba') return wrap('<rect x="2.5" y="6.5" width="19" height="11" rx="4.5"/><rect x="7.5" y="9" width="9" height="6" rx="1.4"/><path d="M5 12h1.2M17.8 11.2h.01M18.8 12.8h.01"/>');
  if (ck === 'gb' || ck === 'gbc') return wrap('<rect x="5.5" y="2.5" width="13" height="19" rx="3"/><rect x="8" y="5" width="8" height="6" rx="1.2"/><path d="M9 15.5h2M10 14.5v2"/><circle cx="15.2" cy="15.2" r="1.1" fill="currentColor"/>');
  if (ck === 'nds' || ck === '3ds') return wrap('<rect x="5" y="3" width="14" height="8.5" rx="2"/><rect x="5" y="12.5" width="14" height="8.5" rx="2"/><line x1="7.5" y1="7.2" x2="16.5" y2="7.2"/>');
  if (ck === 'wii' || ck === 'wiiu') return wrap('<rect x="7.5" y="2.5" width="9" height="19" rx="2.5"/><path d="M12 6.5v3M10.5 8h3"/><circle cx="12" cy="14" r="1.4" fill="currentColor"/>');
  if (ck === 'switch') return wrap('<rect x="3" y="4" width="6.5" height="16" rx="3"/><rect x="14.5" y="4" width="6.5" height="16" rx="3"/><circle cx="6.25" cy="9" r="1.2" fill="currentColor"/><circle cx="17.75" cy="14" r="1.2" fill="currentColor"/>');
  if (ck === 'genesis' || ck === 'sms' || ck === 'sega32x' || ck === 'gg') return wrap('<path d="M3.5 13.5c0-4.2 3.8-7.5 8.5-7.5s8.5 3.3 8.5 7.5c0 2.6-2 4.5-4.3 4.5-1.8 0-3-1-4.2-1s-2.4 1-4.2 1c-2.3 0-4.3-1.9-4.3-4.5Z"/><circle cx="15" cy="12" r="1.1" fill="currentColor"/><circle cx="17.8" cy="10.8" r="1.1" fill="currentColor"/>');
  if (ck === 'dc') return wrap('<circle cx="12" cy="12" r="9"/><path d="M12 12a2.5 2.5 0 0 0 2.5-2.5A4.5 4.5 0 0 0 10 5a6.5 6.5 0 0 0-6.5 6.5c0 4 3.3 7.5 7.5 7.5"/>');
  if (ck === 'saturn') return wrap('<circle cx="12" cy="12" r="5.2"/><ellipse cx="12" cy="12" rx="10" ry="3.8" transform="rotate(-24 12 12)"/>');
  if (ck === 'ps1' || ck === 'ps2' || ck === 'ps3' || ck === 'ps4' || ck === 'psp' || ck === 'psvita') return wrap('<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="2.6"/><path d="M12 5.2v2.4M16.8 14.5l-2-1.2" opacity="0.75"/>');
  if (ck === 'arcade' || ck === 'mame') return wrap('<circle cx="9.5" cy="7.5" r="3"/><path d="M9.5 10.5V16M5 16h14l1 4H4l1-4Z"/><circle cx="15.5" cy="13.5" r="1.3" fill="currentColor"/><circle cx="18.2" cy="12.5" r="1.3" fill="currentColor"/>');
  return wrap('<rect x="4" y="4" width="16" height="16" rx="3"/><path d="M7.5 8h9v5.5h-9z"/><path d="M8 17h8"/>');
}

export function joinLocalPath(rootDir, subPath) {
  const sub = String(subPath || '').trim();
  if (!sub) return String(rootDir || '');
  if (/^[A-Za-z]:[\\/]/.test(sub) || sub.startsWith('/')) return sub;
  const base = String(rootDir || '').replace(/[\\/]+$/, '');
  const sep = base.includes('\\') || /^[A-Za-z]:/.test(base) ? '\\' : '/';
  const cleanSub = sub.replace(/^[\\/]+/, '').replace(/[\\/]/g, sep);
  return base ? `${base}${sep}${cleanSub}` : cleanSub;
}

export function resolveLocalDownloadDir(consoleKey, arcadeCore = '') {
  const s = state.settings || {};
  const root = $('#setting-roms-dir')?.value?.trim() || s.roms_dir || 'F:\\Games\\RetroArch\\roms';
  const paths = Object.keys(state.consolePathsDraft || {}).length ? state.consolePathsDraft : (s.console_paths || {});
  const ck = String(consoleKey || 'other').trim().toLowerCase();
  const isMame = ck === 'mame' || String(arcadeCore || '').trim().toLowerCase() === 'mame';
  const folder = isMame
    ? (paths.mame || paths.arcade || 'arcade')
    : (paths[ck] || ck);
  return joinLocalPath(root, folder);
}

export function resolveTargetRemoteDir(target, consoleKey, rawPlatform = '') {
  if (!target) return '';
  const overrides = {};
  Object.entries(target.path_overrides || {}).forEach(([k, v]) => {
    if (v) overrides[String(k).trim().toLowerCase()] = String(v).trim().replace(/\/+$/, '');
  });
  const rawKey = String(rawPlatform || '').trim().toLowerCase();
  if (rawKey && overrides[rawKey]) return overrides[rawKey];
  const ck = String(consoleKey || '').trim().toLowerCase();
  if (ck && overrides[ck]) return overrides[ck];
  const base = String(target.base_path || 'Roms').replace(/\/+$/, '');
  return base ? `${base}/${ck}` : ck;
}

export function consoleSelectOptionsHtml(excludeKeys = new Set()) {
  const ordered = ['mame', 'arcade', ...Object.keys(CONSOLES).filter(k => k !== 'mame' && k !== 'arcade')];
  return ordered
    .filter(k => !excludeKeys.has(k))
    .map(k => `<option value="${escapeHtml(k)}">${escapeHtml(consoleMeta(k).full)} (${escapeHtml(k)})</option>`)
    .join('');
}

export function formatBytes(bytes) {
  if (!bytes || bytes <= 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  const i = Math.min(units.length - 1, Math.floor(Math.log(bytes) / Math.log(1024)));
  return (bytes / Math.pow(1024, i)).toFixed(i === 0 ? 0 : 1) + ' ' + units[i];
}

export function hashString(str) {
  let h = 2166136261;
  for (let i = 0; i < str.length; i++) { h ^= str.charCodeAt(i); h = Math.imul(h, 16777619); }
  return h >>> 0;
}

export function wrapTitleLines(title, maxLen = 17, maxLines = 3) {
  const clean = String(title || '').replace(/\s*\([^)]*\)/g, '').trim() || String(title || 'ROM');
  const words = clean.split(/\s+/);
  const lines = [];
  let cur = '';
  for (const w of words) {
    const next = cur ? cur + ' ' + w : w;
    if (next.length <= maxLen) cur = next;
    else {
      if (cur) lines.push(cur);
      cur = w.length > maxLen ? w.slice(0, maxLen - 1) + '…' : w;
      if (lines.length === maxLines) break;
    }
  }
  if (cur && lines.length < maxLines) lines.push(cur);
  return lines;
}

export function buildConsoleMotif(consoleKey, color, seed) {
  const dx = (seed % 18) - 9;
  const dy = ((seed >> 4) % 14) - 7;
  switch (consoleKey) {
    case 'snes':
      return `<g transform="translate(${dx},${dy})" opacity="0.55">
        <circle cx="215" cy="108" r="11" fill="${color}" fill-opacity="0.22" stroke="${color}" stroke-width="1.5"/>
        <circle cx="239" cy="130" r="11" fill="#F87171" fill-opacity="0.22" stroke="#F87171" stroke-width="1.5"/>
        <circle cx="191" cy="130" r="11" fill="#38BDF8" fill-opacity="0.22" stroke="#38BDF8" stroke-width="1.5"/>
        <circle cx="215" cy="152" r="11" fill="#34D399" fill-opacity="0.22" stroke="#34D399" stroke-width="1.5"/>
      </g>`;
    case 'nes':
    case 'switch':
      return `<g transform="translate(${dx},${dy})" opacity="0.55">
        <path d="M196 105h20v20h20v20h-20v20h-20v-20h-20v-20h20z" fill="${color}" fill-opacity="0.14" stroke="${color}" stroke-width="1.5"/>
        <rect x="22" y="108" width="84" height="5" rx="2.5" fill="${color}" fill-opacity="0.45"/>
      </g>`;
    case 'genesis':
      return `<g transform="translate(${dx},${dy})" opacity="0.5">
        <path d="M135 60L270 195M165 60L290 185M195 60L300 165" stroke="${color}" stroke-width="6" stroke-linecap="round" stroke-opacity="0.24"/>
        <circle cx="215" cy="130" r="34" fill="none" stroke="${color}" stroke-opacity="0.4" stroke-width="1.6"/>
      </g>`;
    case 'gba':
    case 'nds':
    case '3ds':
      return `<g transform="translate(${dx},${dy})" opacity="0.55">
        <rect x="155" y="88" width="108" height="76" rx="12" fill="${color}" fill-opacity="0.08" stroke="${color}" stroke-width="1.6"/>
        <rect x="171" y="102" width="76" height="48" rx="4" fill="rgba(11,14,20,0.45)" stroke="${color}" stroke-opacity="0.45" stroke-width="1.2"/>
      </g>`;
    case 'arcade':
      return `<g transform="translate(${dx},${dy})" opacity="0.55">
        <polygon points="210,82 252,106 252,154 210,178 168,154 168,106" fill="${color}" fill-opacity="0.1" stroke="${color}" stroke-width="1.6"/>
        <circle cx="210" cy="130" r="14" fill="${color}" fill-opacity="0.32" stroke="${color}" stroke-width="1.5"/>
      </g>`;
    case 'ps1': case 'ps2': case 'ps3': case 'ps4': case 'psp': case 'dc': case 'saturn':
      return `<g transform="translate(${dx},${dy})" opacity="0.55">
        <circle cx="212" cy="128" r="52" fill="${color}" fill-opacity="0.07" stroke="${color}" stroke-opacity="0.35" stroke-width="1.6"/>
        <circle cx="212" cy="128" r="32" fill="none" stroke="${color}" stroke-opacity="0.25" stroke-dasharray="10 6" stroke-width="1.5"/>
        <circle cx="212" cy="128" r="12" fill="#0B0E14" stroke="${color}" stroke-width="1.6"/>
      </g>`;
    case 'n64': case 'gc': case 'wii': case 'wiiu':
      return `<g transform="translate(${dx},${dy})" opacity="0.55">
        <polygon points="212,84 256,110 256,158 212,184 168,158 168,110" fill="${color}" fill-opacity="0.09" stroke="${color}" stroke-width="1.6"/>
        <path d="M212 84v50m0 0l44 24m-44-24l-44 24" stroke="${color}" stroke-opacity="0.45" stroke-width="1.5"/>
      </g>`;
    default:
      return `<g transform="translate(${dx},${dy})" opacity="0.5">
        <circle cx="212" cy="128" r="46" fill="none" stroke="${color}" stroke-opacity="0.28" stroke-width="1.8"/>
        <circle cx="212" cy="128" r="22" fill="${color}" fill-opacity="0.1" stroke="${color}" stroke-opacity="0.45" stroke-width="1.5"/>
      </g>`;
  }
}

export function buildCoverSvg(title, consoleKey) {
  const meta = consoleMeta(consoleKey);
  const seed = hashString(title + ':' + consoleKey);
  const lines = wrapTitleLines(title);
  const textEls = lines.map((line, idx) =>
    `<text x="24" y="${262 + idx * 24}" fill="#F8F6E8" font-family="'Chakra Petch', 'JetBrains Mono', sans-serif" font-size="18.5" font-weight="700" letter-spacing="0.2">${escapeHtml(line)}</text>`
  ).join('');
  return `<svg class="cover-svg" viewBox="0 0 300 400" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="Carátula de ${escapeHtml(title)}">
    <defs>
      <linearGradient id="bg-${seed}" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0%" stop-color="${meta.bg}"/><stop offset="100%" stop-color="#08080F"/>
      </linearGradient>
      <radialGradient id="glow-${seed}" cx="0.72" cy="0.32" r="0.72">
        <stop offset="0%" stop-color="${meta.color}" stop-opacity="0.28"/><stop offset="100%" stop-color="${meta.color}" stop-opacity="0"/>
      </radialGradient>
    </defs>
    <rect width="300" height="400" fill="url(#bg-${seed})"/>
    <rect width="300" height="400" fill="url(#glow-${seed})"/>
    <path d="M0 64h300M0 128h300M0 192h300M0 256h300M0 320h300M60 0v400M120 0v400M180 0v400M240 0v400" stroke="rgba(255,255,255,0.035)" stroke-width="1"/>
    <g opacity="0.52">
      <rect x="64" y="10" width="172" height="5" rx="2.5" fill="${meta.color}" opacity="0.45"/>
      <rect x="76" y="19" width="148" height="4" rx="2" fill="${meta.color}" opacity="0.35"/>
      <rect x="88" y="27" width="124" height="4" rx="2" fill="${meta.color}" opacity="0.25"/>
    </g>
    <rect x="14" y="42" width="272" height="344" rx="18" fill="none" stroke="${meta.color}" stroke-opacity="0.34" stroke-width="1.5"/>
    ${buildConsoleMotif(consoleKey, meta.color, seed)}
    <rect x="24" y="218" width="46" height="4" rx="2" fill="${meta.color}"/>
    ${textEls}
  </svg>`;
}

export function renderCoverMarkup(item, opts = {}) {
  const svg = buildCoverSvg(item.title, item.consoleKey);
  const avail = opts.availability || item.availability || 'local';
  const showAvail = opts.showAvail !== false;
  const availLabel = avail === 'hub' ? 'Disponible en PC Hub' : 'Disponible localmente';
  const dotHtml = showAvail
    ? `<span class="avail-dot ${avail === 'hub' ? 'hub' : 'local'}" title="${escapeHtml(availLabel)}" aria-label="${escapeHtml(availLabel)}"></span>`
    : '';
  if (item.hasCover && item.coverUrl) {
    const src = escapeHtml(item.coverUrl);
    const alt = escapeHtml(`Carátula de ${item.title}`);
    return `${svg}<img class="cover-blur-bg" src="${src}" decoding="async" aria-hidden="true" onerror="this.hidden=true" alt=""><img class="cover-photo cover-fg-contain" src="${src}" decoding="async" onerror="this.hidden=true" alt="${alt}">${dotHtml}`;
  }
  return `${svg}${dotHtml}`;
}

let _ambientTimer = null;
export function setAmbientBackdrop(imageUrl = '', accentColor = '') {
  const wrap = $('#ambient-backdrop');
  const img = $('#ambient-backdrop-img');
  if (!wrap || !img) return;
  if (accentColor) {
    document.documentElement.style.setProperty('--ambient-accent', accentColor);
  }
  clearTimeout(_ambientTimer);
  if (!imageUrl) {
    wrap.classList.remove('active');
    _ambientTimer = setTimeout(() => { img.hidden = true; img.removeAttribute('src'); }, 220);
    return;
  }
  if (img.getAttribute('src') !== imageUrl) {
    img.src = imageUrl;
  }
  img.hidden = false;
  requestAnimationFrame(() => wrap.classList.add('active'));
}


export function toast(message, kind = 'info', opts = {}) {
  const region = $('#toast-region');
  const el = document.createElement('div');
  el.className = 'toast ' + kind;
  const msgSpan = document.createElement('span');
  msgSpan.textContent = message;
  el.appendChild(msgSpan);
  if (opts && opts.actionLabel && typeof opts.onAction === 'function') {
    const actions = document.createElement('div');
    actions.className = 'toast-actions';
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'toast-action';
    btn.textContent = opts.actionLabel;
    btn.addEventListener('click', () => {
      opts.onAction();
      el.remove();
    });
    actions.appendChild(btn);
    el.appendChild(actions);
  }
  region.appendChild(el);
  const ttl = Number(opts?.durationMs) > 0 ? Number(opts.durationMs) : 3400;
  setTimeout(() => {
    el.style.opacity = '0';
    el.style.transition = 'opacity 180ms ease';
    setTimeout(() => el.remove(), 200);
  }, ttl);
}

export function formatRelativeTime(iso) {
  if (!iso) return 'Recién jugado';
  const diffSec = Math.max(0, Math.round((Date.now() - Date.parse(iso)) / 1000));
  if (!Number.isFinite(diffSec) || diffSec < 60) return 'Hace un momento';
  const mins = Math.round(diffSec / 60);
  if (mins < 60) return `Hace ${mins} min`;
  const hrs = Math.round(mins / 60);
  if (hrs < 24) return `Hace ${hrs} h`;
  return `Hace ${Math.round(hrs / 24)} d`;
}

export function findLibraryEntry(entryId) {
  if (!entryId) return { canonical: null, variant: null };
  for (const it of state.libraryItems) {
    const v = (it.variants || []).find(vv => vv.entryId === entryId);
    if (v) return { canonical: it, variant: v };
    if (it.entryId === entryId) return { canonical: it, variant: it };
  }
  return { canonical: null, variant: null };
}


export const actions = {};
