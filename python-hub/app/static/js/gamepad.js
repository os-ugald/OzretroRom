import { $, $$, actions, setAmbientBackdrop } from './state.js';
import { isActivitySheetExpanded, toggleActivitySheet } from './activity-sheet.js';

const CONSOLE_DESTINATIONS = ['home', 'explore', 'settings'];
const FOCUSABLE_SELECTOR = [
  '.hylian-card:not([hidden])',
  '.rom-card:not([hidden])',
  '.featured-cart-card:not([hidden])',
  '.rec-card:not([hidden])',
  '.saga-card:not([hidden])',
  '.console-tile:not([hidden])',
  '.omnibox-item:not([hidden])',
  '.hero-primary-cta:not([hidden])',
  '.explore-seg-btn:not([hidden])',
  '.console-nav-btn:not([hidden])',
  '.settings-tab-btn:not([hidden])',
  '.btn:not([hidden]):not(:disabled)',
].join(', ');

let currentFocusEl = null;
let lastRepeatMs = 0;
const prevButtons = new Map();

function isElementVisible(el) {
  if (!el || !el.isConnected) return false;
  if (el.closest('[hidden]') || el.closest('[inert]')) return false;
  const r = el.getBoundingClientRect();
  return r.width > 0 && r.height > 0;
}

function getVisibleFocusables() {
  return $$(FOCUSABLE_SELECTOR).filter(isElementVisible);
}

export function setConsoleFocus(el) {
  if (currentFocusEl && currentFocusEl !== el) {
    currentFocusEl.classList.remove('gamepad-focused');
  }
  currentFocusEl = el || null;
  if (!el) return;
  el.classList.add('gamepad-focused');
  if (typeof el.focus === 'function') {
    try { el.focus({ preventScroll: true }); } catch (e) { el.focus(); }
  }
  try { el.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'smooth' }); } catch (e) {}
  const coverImg = el.querySelector('img.cover-fg-contain, img.saga-card-art-bg, img.cover-photo');
  if (coverImg && coverImg.getAttribute('src') && !coverImg.hidden) {
    const accent = el.style.getPropertyValue('--card-accent') || el.style.getPropertyValue('--saga-accent') || '';
    setAmbientBackdrop(coverImg.getAttribute('src'), accent);
  }
}

function moveSpatial(dx, dy) {
  const candidates = getVisibleFocusables();
  if (!candidates.length) return;
  if (!currentFocusEl || !isElementVisible(currentFocusEl)) {
    setConsoleFocus(candidates[0]);
    return;
  }
  const srcRect = currentFocusEl.getBoundingClientRect();
  const sx = srcRect.left + srcRect.width / 2;
  const sy = srcRect.top + srcRect.height / 2;
  let best = null;
  let bestScore = Infinity;

  for (const el of candidates) {
    if (el === currentFocusEl) continue;
    const r = el.getBoundingClientRect();
    const cx = r.left + r.width / 2;
    const cy = r.top + r.height / 2;
    const vx = cx - sx;
    const vy = cy - sy;

    if (dx > 0 && vx <= 8) continue;
    if (dx < 0 && vx >= -8) continue;
    if (dy > 0 && vy <= 8) continue;
    if (dy < 0 && vy >= -8) continue;

    const primary = dx !== 0 ? Math.abs(vx) : Math.abs(vy);
    const cross = dx !== 0 ? Math.abs(vy) : Math.abs(vx);
    const score = primary + cross * 2.4;
    if (score < bestScore) {
      bestScore = score;
      best = el;
    }
  }
  if (best) setConsoleFocus(best);
}

function cycleDestination(step) {
  const current = CONSOLE_DESTINATIONS.includes(actions.getActiveConsoleTab?.() || '')
    ? actions.getActiveConsoleTab()
    : 'home';
  const idx = CONSOLE_DESTINATIONS.indexOf(current);
  const next = CONSOLE_DESTINATIONS[(idx + step + CONSOLE_DESTINATIONS.length) % CONSOLE_DESTINATIONS.length];
  actions.switchView(next);
}

function focusOmnibox() {
  const inp = $('#global-search');
  if (!inp) return;
  inp.focus();
  inp.select?.();
}

function handleButtonDown(btnIdx) {
  if (actions.isHylianSplashVisible?.()) {
    actions.dismissHylianSplash?.();
    return;
  }
  switch (btnIdx) {
    case 4: // L1 / LB
      cycleDestination(-1);
      break;
    case 5: // R1 / RB
      cycleDestination(1);
      break;
    case 3: // Y / Triangle -> Omnibox
      focusOmnibox();
      break;
    case 0: { // A / Cross -> Launch / Activate focused
      const target = isElementVisible(currentFocusEl) ? currentFocusEl : document.activeElement;
      if (target && typeof target.click === 'function') {
        target.click();
      } else {
        $('#home-hero-open-btn')?.click();
      }
      break;
    }
    case 1: // B / Circle -> Back / Close
      if ($('#omnibox-panel') && !$('#omnibox-panel').hidden) {
        $('#omnibox-panel').hidden = true;
      } else if (isActivitySheetExpanded()) {
        toggleActivitySheet(false);
      } else if (actions.handleBackNavigation) {
        actions.handleBackNavigation();
      }
      break;
    case 12: moveSpatial(0, -1); break; // D-Pad Up
    case 13: moveSpatial(0, 1); break;  // D-Pad Down
    case 14: moveSpatial(-1, 0); break; // D-Pad Left
    case 15: moveSpatial(1, 0); break;  // D-Pad Right
    default: break;
  }
}

function pollGamepads(now) {
  const pads = typeof navigator.getGamepads === 'function' ? navigator.getGamepads() : [];
  for (const pad of pads) {
    if (!pad) continue;
    const prev = prevButtons.get(pad.index) || [];
    const next = pad.buttons.map(b => Boolean(b?.pressed));
    next.forEach((pressed, idx) => {
      if (pressed && !prev[idx]) {
        handleButtonDown(idx);
      } else if (pressed && idx >= 12 && idx <= 15 && now - lastRepeatMs > 185) {
        lastRepeatMs = now;
        handleButtonDown(idx);
      }
    });
    const axX = pad.axes?.[0] || 0;
    const axY = pad.axes?.[1] || 0;
    if (now - lastRepeatMs > 200) {
      if (axX > 0.65) { lastRepeatMs = now; moveSpatial(1, 0); }
      else if (axX < -0.65) { lastRepeatMs = now; moveSpatial(-1, 0); }
      else if (axY > 0.65) { lastRepeatMs = now; moveSpatial(0, 1); }
      else if (axY < -0.65) { lastRepeatMs = now; moveSpatial(0, -1); }
    }
    prevButtons.set(pad.index, next);
  }
  requestAnimationFrame(pollGamepads);
}

export function initGamepadAndKeyboard() {
  window.addEventListener('keydown', ev => {
    if (actions.isHylianSplashVisible?.()) {
      ev.preventDefault();
      actions.dismissHylianSplash?.();
      return;
    }
    const inInput = ['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement?.tagName || '');
    if ((ev.ctrlKey || ev.metaKey) && ev.key.toLowerCase() === 'k') {
      ev.preventDefault();
      focusOmnibox();
      return;
    }
    if (!inInput && (ev.key === '/' || ev.key.toLowerCase() === 'y')) {
      ev.preventDefault();
      focusOmnibox();
      return;
    }
    if (!inInput && ev.key === '[') {
      ev.preventDefault();
      cycleDestination(-1);
      return;
    }
    if (!inInput && ev.key === ']') {
      ev.preventDefault();
      cycleDestination(1);
      return;
    }
    if (!inInput && (ev.key === 'ArrowRight' || ev.key === 'ArrowLeft' || ev.key === 'ArrowUp' || ev.key === 'ArrowDown')) {
      if (ev.key === 'ArrowRight') moveSpatial(1, 0);
      if (ev.key === 'ArrowLeft') moveSpatial(-1, 0);
      if (ev.key === 'ArrowUp') moveSpatial(0, -1);
      if (ev.key === 'ArrowDown') moveSpatial(0, 1);
      ev.preventDefault();
      return;
    }
    if (ev.key === 'Escape') {
      if ($('#omnibox-panel') && !$('#omnibox-panel').hidden) {
        $('#omnibox-panel').hidden = true;
        return;
      }
      if (isActivitySheetExpanded()) {
        toggleActivitySheet(false);
        return;
      }
      if (actions.handleBackNavigation) actions.handleBackNavigation();
    }
  });

  if (typeof navigator !== 'undefined' && typeof navigator.getGamepads === 'function') {
    requestAnimationFrame(pollGamepads);
  }
}
