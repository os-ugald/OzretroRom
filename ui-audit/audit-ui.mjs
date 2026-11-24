import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

function parseArgs(argv) {
  const args = {
    url: 'http://192.168.100.173:17893/',
    out: path.join(__dirname, 'reports', 'latest'),
    mode: 'redesign',
    chrome: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
  };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--url' && argv[i + 1]) args.url = argv[++i];
    else if (a === '--out' && argv[i + 1]) args.out = argv[++i];
    else if (a === '--mode' && argv[i + 1]) args.mode = argv[++i];
    else if (a === '--chrome' && argv[i + 1]) args.chrome = argv[++i];
  }
  return args;
}

const VIEWPORTS = [
  { name: 'desktop', width: 1920, height: 1080, isMobile: false, hasTouch: false },
  { name: 'laptop', width: 1280, height: 800, isMobile: false, hasTouch: false },
  { name: 'mobile', width: 390, height: 844, isMobile: true, hasTouch: true }
];

async function collectDomMetrics(page) {
  return await page.evaluate(() => {
    const docEl = document.documentElement;
    const vw = window.innerWidth;
    const vh = window.innerHeight;
    const scrollW = docEl.scrollWidth;
    const horizontalOverflow = scrollW > vw;

    // Broken visible images
    const imgs = [...document.querySelectorAll('img')];
    const brokenImages = [];
    for (const img of imgs) {
      const style = window.getComputedStyle(img);
      const rect = img.getBoundingClientRect();
      if (style.display === 'none' || style.visibility === 'hidden' || img.hidden || rect.width === 0 || rect.height === 0) continue;
      if (!img.complete || img.naturalWidth === 0) {
        brokenImages.push({
          src: img.currentSrc || img.getAttribute('src') || '',
          alt: img.alt || ''
        });
      }
    }

    // Card cover aspect ratio consistency
    const coverEls = [...document.querySelectorAll('.cover-frame, .card-art-container')].filter(el => {
      const s = window.getComputedStyle(el);
      const r = el.getBoundingClientRect();
      return s.display !== 'none' && s.visibility !== 'hidden' && r.width > 0;
    });
    const ratios = coverEls.map(el => {
      const r = el.getBoundingClientRect();
      return { width: r.width, height: r.height, ratio: r.height > 0 ? r.width / r.height : 0 };
    });
    let aspectRatioAnomalies = [];
    if (ratios.length > 0) {
      const zeroHeight = ratios.filter(x => x.height <= 0 || x.width <= 0);
      if (zeroHeight.length) {
        aspectRatioAnomalies.push({ reason: 'zero-size cover frame', count: zeroHeight.length });
      }
      const valid = ratios.filter(x => x.ratio > 0);
      if (valid.length > 0) {
        const baselineRatio = valid[0].ratio;
        for (let idx = 0; idx < valid.length; idx++) {
          const diff = Math.abs(valid[idx].ratio - baselineRatio) / baselineRatio;
          if (diff > 0.03) {
            aspectRatioAnomalies.push({
              index: idx,
              expectedRatio: Number(baselineRatio.toFixed(3)),
              actualRatio: Number(valid[idx].ratio.toFixed(3)),
              deviationPct: Number((diff * 100).toFixed(2))
            });
          }
        }
      }
    }

    // Overlap & clipping check
    const overlapAndClipping = [];
    const header = document.querySelector('header, .app-header');
    const sidebar = document.querySelector('aside.app-sidebar, nav.sidebar');
    const main = document.querySelector('main, .app-main');
    const drawer = document.querySelector('#inspection-drawer');

    const isVisible = el => {
      if (!el) return false;
      const s = window.getComputedStyle(el);
      const r = el.getBoundingClientRect();
      return s.display !== 'none' && s.visibility !== 'hidden' && s.opacity !== '0' && r.width > 0 && r.height > 0;
    };

    if (isVisible(header) && isVisible(main)) {
      const hr = header.getBoundingClientRect();
      const mr = main.getBoundingClientRect();
      // Only flag if header overlaps main's first child content significantly at scroll=0
      const firstSection = main.querySelector('.view-section.active, .toolbar-row, .hero, .results-bar, .rom-grid');
      if (firstSection && window.scrollY === 0) {
        const fr = firstSection.getBoundingClientRect();
        if (fr.top < hr.bottom - 2 && fr.left < hr.right && fr.right > hr.left) {
          overlapAndClipping.push({
            type: 'header-overlaps-content',
            headerBottom: Math.round(hr.bottom),
            contentTop: Math.round(fr.top)
          });
        }
      }
    }

    if (isVisible(sidebar) && isVisible(main)) {
      const sr = sidebar.getBoundingClientRect();
      const mr = main.getBoundingClientRect();
      const sStyle = window.getComputedStyle(sidebar);
      if (sStyle.position !== 'fixed' && sr.right > mr.left + 2) {
        overlapAndClipping.push({
          type: 'sidebar-overlaps-main',
          sidebarRight: Math.round(sr.right),
          mainLeft: Math.round(mr.left)
        });
      }
    }

    // Interactive element viewport clipping check (only check visible buttons/inputs/links not inside horizontally scrollable tables)
    const interactives = [...document.querySelectorAll('header button, header input, header a, .toolbar-row button, .toolbar-row select, .app-sidebar button, .app-sidebar a, .drawer.open button')];
    for (const el of interactives) {
      if (!isVisible(el)) continue;
      // Skip items inside scrollable containers that are off-screen vertically
      const r = el.getBoundingClientRect();
      if (r.right > vw + 2 || r.left < -2) {
        overlapAndClipping.push({
          type: 'clipped-interactive-x',
          tag: el.tagName,
          id: el.id || null,
          text: (el.textContent || el.getAttribute('aria-label') || '').trim().slice(0, 40),
          left: Math.round(r.left),
          right: Math.round(r.right),
          viewportWidth: vw
        });
      }
    }

    // Drawer check if open
    if (drawer && drawer.classList.contains('open')) {
      const dr = drawer.getBoundingClientRect();
      if (dr.right > vw + 2 || dr.left < -2 || dr.width > vw + 2) {
        overlapAndClipping.push({
          type: 'drawer-out-of-bounds',
          left: Math.round(dr.left),
          right: Math.round(dr.right),
          viewportWidth: vw
        });
      }
    }

    // Timing info
    const navTiming = performance.getEntriesByType('navigation')[0];
    const timing = navTiming ? {
      domContentLoadedMs: Math.round(navTiming.domContentLoadedEventEnd),
      loadMs: Math.round(navTiming.loadEventEnd),
      ttfbMs: Math.round(navTiming.responseStart)
    } : {};

    return {
      viewportWidth: vw,
      viewportHeight: vh,
      scrollWidth: scrollW,
      horizontalOverflow,
      brokenImages,
      coverFramesMeasured: coverEls.length,
      aspectRatioAnomalies,
      overlapAndClipping,
      domNodeCount: document.querySelectorAll('*').length,
      timing
    };
  });
}

async function runAxe(page, axeSource) {
  await page.addScriptTag({ content: axeSource });
  return await page.evaluate(async () => {
    const res = await window.axe.run(document, {
      runOnly: {
        type: 'tag',
        values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']
      }
    });
    return {
      violations: res.violations.map(v => ({
        id: v.id,
        impact: v.impact,
        description: v.description,
        help: v.help,
        nodeCount: v.nodes.length,
        samples: v.nodes.slice(0, 3).map(n => ({
          target: n.target,
          failureSummary: n.failureSummary
        }))
      })),
      passesCount: res.passes.length
    };
  });
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  fs.mkdirSync(args.out, { recursive: true });

  const axePath = path.join(__dirname, 'node_modules', 'axe-core', 'axe.min.js');
  const axeSource = fs.readFileSync(axePath, 'utf8');

  const launchOpts = { headless: true };
  if (fs.existsSync(args.chrome)) {
    launchOpts.executablePath = args.chrome;
  }

  const browser = await chromium.launch(launchOpts);
  const report = {
    url: args.url,
    mode: args.mode,
    timestamp: new Date().toISOString(),
    viewports: {},
    interactions: {},
    passed: true
  };

  try {
    for (const vp of VIEWPORTS) {
      const context = await browser.newContext({
        viewport: { width: vp.width, height: vp.height },
        isMobile: vp.isMobile,
        hasTouch: vp.hasTouch,
        deviceScaleFactor: 1
      });
      const page = await context.newPage();

      const consoleErrors = [];
      const pageErrors = [];
      const networkFailures = [];

      page.on('console', msg => {
        if (msg.type() === 'error') {
          consoleErrors.push(msg.text());
        }
      });
      page.on('pageerror', err => {
        pageErrors.push(err.message || String(err));
      });
      page.on('requestfailed', req => {
        networkFailures.push({ url: req.url(), method: req.method(), reason: req.failure()?.errorText || 'failed' });
      });
      page.on('response', resp => {
        if (resp.status() >= 400) {
          networkFailures.push({ url: resp.url(), status: resp.status(), statusText: resp.statusText() });
        }
      });

      const navStart = Date.now();
      await page.goto(args.url, { waitUntil: 'load', timeout: 30000 });

      // Wait until initial library count or cards load and skeleton is hidden
      await page.waitForFunction(() => {
        const skel = document.querySelector('#library-skeleton');
        if (skel && !skel.hidden) return false;
        const badge = document.querySelector('#nav-lib-count');
        if (badge && Number(badge.textContent.replace(/\D/g, '')) > 0) return true;
        return document.readyState === 'complete' && !skel;
      }, { timeout: 20000 }).catch(() => {});

      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => {});
      const libraryReadyMs = Date.now() - navStart;
      // Short settle for fonts/layout
      await page.waitForTimeout(200);

      await page.screenshot({ path: path.join(args.out, `${vp.name}-main.png`), fullPage: false });
      if (vp.name === 'desktop') {
        await page.screenshot({ path: path.join(args.out, 'desktop-home.png'), fullPage: false });
      }

      const metrics = await collectDomMetrics(page);
      metrics.libraryReadyMs = libraryReadyMs;
      const axe = await runAxe(page, axeSource);

      const vpReport = {
        viewport: `${vp.width}x${vp.height}`,
        metrics,
        consoleErrors,
        pageErrors,
        networkFailures,
        axeViolations: axe.violations,
        axePassesCount: axe.passesCount
      };

      // Interactive flows in redesign mode
      if (args.mode === 'redesign') {
        const interactionResults = {};

        // 0. On desktop, verify Home dashboard + Collapsible Mini Icon Sidebar + Mark Played -> Recientes
        if (vp.name === 'desktop') {
          const homeActive = await page.locator('#view-home.active').count();
          const consoleTiles = await page.locator('#home-consoles-grid .console-tile').count();
          const heroPresent = await page.locator('#home-hero-card').count();

          // Test sidebar collapse to mini icon rail + a11y + zero clipping
          await page.click('#sidebar-collapse-btn');
          await page.waitForTimeout(220);
          const collapsedInfo = await page.evaluate(() => {
            const shell = document.querySelector('#app-shell');
            const sb = document.querySelector('#app-sidebar');
            return {
              hasClass: Boolean(shell && shell.classList.contains('sidebar-collapsed')),
              width: sb ? Math.round(sb.getBoundingClientRect().width) : 0
            };
          });
          const collapsedMetrics = await collectDomMetrics(page);
          const collapsedAxe = await runAxe(page, axeSource);
          await page.screenshot({ path: path.join(args.out, 'desktop-sidebar-collapsed.png'), fullPage: false });
          // Restore expanded sidebar
          await page.click('#sidebar-collapse-btn');
          await page.waitForTimeout(180);

          interactionResults.sidebarCollapse = {
            ...collapsedInfo,
            horizontalOverflow: collapsedMetrics.horizontalOverflow,
            overlapAndClipping: collapsedMetrics.overlapAndClipping,
            axeSevere: collapsedAxe.violations.filter(v => v.impact === 'critical' || v.impact === 'serious').length
          };

          // Mark played on first active Home/Hero game and verify Recientes populates
          const heroPlayBtn = page.locator('#home-hero-play-btn');
          if (await heroPlayBtn.isVisible().catch(() => false)) {
            await Promise.all([
              page.waitForResponse(r => r.url().includes('/played') && r.request().method() === 'POST', { timeout: 5000 }).catch(() => null),
              heroPlayBtn.click()
            ]);
            await page.waitForTimeout(220);
          }
          const recentCardsCount = await page.locator('#home-recent-grid .rom-card').count();
          await page.screenshot({ path: path.join(args.out, 'desktop-home-recent.png'), fullPage: false });

          const featuredSection = page.locator('#home-featured-section');
          if (await featuredSection.isVisible().catch(() => false)) {
            await featuredSection.scrollIntoViewIfNeeded();
            await page.waitForTimeout(180);
          }
          const featuredCartridgesCount = await page.locator('#home-featured-grid .featured-cart-card .cartridge-shell').count();
          const featuredClutterCheck = await page.evaluate(() => {
            const forbidden = ['cartucho ancho', 'cartucho mega', 'carátula sistema', 'caja jewel', 'svg retro', 'estuche switch', 'cartucho 8-bit', 'cartucho gba', 'cartucho compacto', 'cartucho mvs'];
            const cards = [...document.querySelectorAll('#home-featured-grid .featured-cart-card')];
            const dirtyCards = [];
            let allHaveIcon = cards.length > 0;
            let allHaveFav = cards.length > 0;
            for (const c of cards) {
              const txt = (c.innerText || '').toLowerCase();
              const hit = forbidden.find(f => txt.includes(f));
              const hasStandaloneCoverPill = [...c.querySelectorAll('.verify-pill, .mini-badge')].some(el => el.textContent.trim().toLowerCase() === 'carátula');
              if (hit || hasStandaloneCoverPill) dirtyCards.push({ title: c.querySelector('.card-title')?.textContent, hit: hit || 'Carátula' });
              if (!c.querySelector('.sys-chip svg')) allHaveIcon = false;
              if (!c.querySelector('.fav-btn')) allHaveFav = false;
            }
            return { dirtyCards, allHaveIcon, allHaveFav };
          });
          const cartridgeShapes = await page.evaluate(() =>
            Array.from(document.querySelectorAll('#home-featured-grid .cartridge-shell')).map(el => el.className)
          );
          await page.screenshot({ path: path.join(args.out, 'desktop-home-cartridges.png'), fullPage: false });

          // Verify right-click context menu opens with customization actions & closes on Escape
          let ctxMenuOk = false;
          if (featuredCartridgesCount > 0) {
            await page.locator('#home-featured-grid .featured-cart-card').first().click({ button: 'right' });
            await page.waitForTimeout(150);
            ctxMenuOk = await page.evaluate(() => {
              const m = document.querySelector('#game-context-menu');
              if (!m || m.hidden) return false;
              const txt = m.innerText || '';
              return txt.includes('principal') && txt.includes('Cambiar carátula');
            });
            await page.keyboard.press('Escape');
            await page.waitForTimeout(100);
          }

          // Verify Recommendations section renders personalized library picks and MiNERVA discovery pills
          const recsSection = page.locator('#home-recommendations-section');
          if (await recsSection.isVisible().catch(() => false)) {
            await recsSection.scrollIntoViewIfNeeded();
            await page.waitForTimeout(180);
          }
          const recsCardsCount = await page.locator('#home-recs-grid .rec-card').count();
          const discoverPillsCount = await page.locator('#home-discover-row .discover-pill').count();

          // Verify "Rellenar carátulas" button on Home triggers POST /api/v1/covers/sync with retryMisses: true
          let homeFillReq = null;
          await page.route('**/api/v1/covers/sync', async route => {
            try { homeFillReq = route.request().postDataJSON(); } catch (_) { homeFillReq = {}; }
            await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ status: 'started', total: 1 }) });
          });
          await page.route('**/api/v1/covers/status', async route => {
            await route.fulfill({
              status: 200,
              contentType: 'application/json',
              body: JSON.stringify({ running: false, total: 1, done: 1, found: 1, skipped: 0, errors: 0, cachedCount: 379, cachedBytes: 120000, currentTitle: 'Game Vault Audit', providerStats: {} })
            });
          });
          try {
            const fillBtn = page.locator('#home-fill-covers-btn');
            if (await fillBtn.isVisible().catch(() => false)) {
              await fillBtn.scrollIntoViewIfNeeded();
              await fillBtn.click();
              await page.waitForTimeout(400);
            }
          } finally {
            await page.unroute('**/api/v1/covers/sync');
            await page.unroute('**/api/v1/covers/status');
          }

          const homeBrandStyle = await page.evaluate(() => {
            const brandText = (document.querySelector('#app-sidebar .brand-row')?.innerText || '').trim();
            const upper = brandText.toUpperCase();
            const btn = document.querySelector('#view-home .btn-primary') || document.querySelector('.btn-primary');
            const btnRadius = btn ? parseFloat(getComputedStyle(btn).borderTopLeftRadius || '0') : 0;
            return {
              brandText,
              brandHasGameVault: upper.includes('GAME VAULT'),
              brandHasOldBrand: upper.includes('ARCADE VAULT') || upper.includes('OZ RETROROM') || upper.includes('PYTHON HUB'),
              primaryBtnRadius: btnRadius
            };
          });

          // Verify "¿Qué juego hoy?" mood picker modal opens cleanly, shows picks & closes with Escape
          let moodPickerOk = false;
          const moodBtn = page.locator('#home-what-to-play-btn');
          if (await moodBtn.isVisible().catch(() => false)) {
            await moodBtn.scrollIntoViewIfNeeded();
            await moodBtn.click();
            await page.waitForSelector('#mood-picker-backdrop:not([hidden])', { timeout: 4000 }).catch(() => {});
            await page.waitForSelector('#mood-picks-grid .mood-pick-card', { timeout: 6000 }).catch(() => {});
            const picksCount = await page.locator('#mood-picks-grid .mood-pick-card').count();
            const mVm = await collectDomMetrics(page);
            const mAxe = await runAxe(page, axeSource);
            await page.screenshot({ path: path.join(args.out, 'desktop-mood-picker.png'), fullPage: false });
            await page.keyboard.press('Escape');
            await page.waitForTimeout(140);
            const moodClosed = await page.locator('#mood-picker-backdrop').evaluate(el => el.hidden);
            moodPickerOk = picksCount > 0 && moodClosed && !mVm.horizontalOverflow && mAxe.violations.filter(v => v.impact === 'critical' || v.impact === 'serious').length === 0;
          }

          interactionResults.homeDashboard = {
            homeActive: homeActive === 1,
            heroPresent: heroPresent === 1,
            consoleTiles,
            recentCardsCount,
            featuredCartridgesCount,
            featuredClean: featuredClutterCheck.dirtyCards.length === 0 && featuredClutterCheck.allHaveIcon && featuredClutterCheck.allHaveFav,
            featuredClutterDebug: featuredClutterCheck,
            ctxMenuOk,
            moodPickerOk,
            recsCardsCount,
            discoverPillsCount,
            cartridgeShapesCount: cartridgeShapes.length,
            homeFillPosted: Boolean(homeFillReq),
            homeFillRetryMisses: Boolean(homeFillReq && homeFillReq.retryMisses === true),
            ...homeBrandStyle
          };
        }

        // Switch to Library before drawer/table/search checks
        if (vp.isMobile) {
          await page.click('#mobile-menu-btn');
          await page.waitForTimeout(220);
        }
        if (await page.locator('#nav-library').count() > 0) {
          await page.click('#nav-library');
          await page.waitForTimeout(220);
        }

        if (vp.name === 'desktop') {
          interactionResults.coverFrameRadius = await page.evaluate(() => {
            const cf = document.querySelector('.view-section.active .rom-card .cover-frame');
            return cf ? parseFloat(getComputedStyle(cf).borderTopLeftRadius || '0') : 0;
          });
          interactionResults.libraryConsolePills = await page.locator('#library-console-pills .lib-console-pill').count();
          await page.screenshot({ path: path.join(args.out, 'desktop-library-colors.png'), fullPage: false });
        }

        // 1. Open inspection drawer on desktop, laptop and mobile
        const firstCard = page.locator('.view-section.active .rom-card').first();
        if (await firstCard.count() > 0) {
          await firstCard.click();
          await page.waitForSelector('#inspection-drawer.open', { timeout: 5000 });
          await page.waitForTimeout(320);
          await page.screenshot({ path: path.join(args.out, `${vp.name}-drawer.png`), fullPage: false });
          const drawerMetrics = await collectDomMetrics(page);
          const drawerAxe = await runAxe(page, axeSource);
          const drawerFtpPathVal = vp.name === 'desktop'
            ? await page.inputValue('#drawer-ftp-path').catch(() => '')
            : 'n/a';
          const drawerHasPhase1 = vp.name === 'desktop'
            ? (
              (await page.locator('#drawer-synopsis-box').count()) > 0 &&
              (await page.locator('#drawer-download-rom-btn').count()) > 0 &&
              (await page.locator('#drawer-patches-box').count()) > 0
            )
            : true;
          interactionResults.drawer = {
            opened: true,
            drawerFtpPathVal,
            drawerHasPhase1,
            horizontalOverflow: drawerMetrics.horizontalOverflow,
            overlapAndClipping: drawerMetrics.overlapAndClipping,
            axeViolations: drawerAxe.violations
          };
          await page.keyboard.press('Escape');
          await page.waitForTimeout(250);
        }

        // 2. Table view on desktop & mobile
        if (vp.name === 'desktop' || vp.name === 'mobile') {
          const tableBtn = page.locator('#view-mode-table');
          if (await tableBtn.count() > 0 && await tableBtn.isVisible().catch(() => false)) {
            await tableBtn.click();
            await page.waitForSelector('#library-table-wrap:not([hidden])', { timeout: 5000 });
            await page.waitForTimeout(200);
            await page.screenshot({ path: path.join(args.out, `${vp.name}-table.png`), fullPage: false });
            const tableMetrics = await collectDomMetrics(page);
            interactionResults.tableView = {
              rendered: true,
              horizontalOverflow: tableMetrics.horizontalOverflow,
              overlapAndClipping: tableMetrics.overlapAndClipping
            };
            await page.locator('#view-mode-grid').click();
            await page.waitForTimeout(150);
          }
        }

        // 3. Desktop-specific interactions: Ctrl+K search, Favorite toggle, all nav tabs
        if (vp.name === 'desktop') {
          await page.keyboard.press('Control+k');
          const focusedId = await page.evaluate(() => document.activeElement?.id || '');
          await page.fill('#global-search', 'sonic');
          await page.waitForTimeout(250);
          const filteredStatus = await page.locator('#results-counter').textContent();
          const filteredCount = await page.locator('#library-grid .rom-card').count();
          await page.fill('#global-search', '');
          await page.waitForTimeout(200);
          const restoredStatus = await page.locator('#results-counter').textContent();
          interactionResults.ctrlKSearch = {
            focusedInput: focusedId === 'global-search',
            filteredCount,
            filteredStatus: filteredStatus?.trim(),
            restoredStatus: restoredStatus?.trim()
          };

          // Favorite toggle
          const favBefore = Number((await page.locator('#filter-count-favorites').textContent()) || '0');
          await page.locator('#library-grid .rom-card .fav-btn').first().click();
          await page.waitForTimeout(150);
          const favAfter = Number((await page.locator('#filter-count-favorites').textContent()) || '0');
          await page.locator('#library-grid .rom-card .fav-btn').first().click();
          await page.waitForTimeout(150);
          interactionResults.favoritesToggle = {
            favBefore,
            favAfter,
            incremented: favAfter === favBefore + 1
          };

          // Targeted filtered cover sync E2E ("Resident")
          if (await page.locator('#sync-covers-btn').count() > 0) {
            await page.fill('#global-search', 'Resident');
            await page.waitForTimeout(220);
            const resFilteredCount = await page.locator('#library-grid .rom-card').count();
            let syncTotalObserved = 0;
            const [syncResp] = await Promise.all([
              page.waitForResponse(r => r.url().includes('/api/v1/covers/sync') && r.request().method() === 'POST', { timeout: 5000 }).catch(() => null),
              page.click('#sync-covers-btn')
            ]);
            if (syncResp) {
              const syncJson = await syncResp.json().catch(() => ({}));
              syncTotalObserved = Number(syncJson.total || 0);
              await page.waitForFunction(() => {
                const b = document.querySelector('#sync-covers-btn');
                return b && !b.disabled;
              }, { timeout: 25000 }).catch(() => {});
              await page.waitForTimeout(250);
            }
            const searchValueAfter = await page.inputValue('#global-search');
            const residentImgsLoaded = await page.evaluate(() =>
              [...document.querySelectorAll('#library-grid img.cover-photo')].filter(i => i.complete && i.naturalWidth > 0).length
            );
            await page.screenshot({ path: path.join(args.out, 'desktop-resident-sync.png'), fullPage: false });
            interactionResults.targetedCoverSync = {
              filteredCount: resFilteredCount,
              syncTotalObserved,
              preservedSearch: searchValueAfter === 'Resident',
              residentImgsLoaded,
              validScope: syncTotalObserved <= resFilteredCount
            };
            await page.fill('#global-search', '');
            await page.waitForTimeout(200);
          }

          // Switch views: Sagas, MiNERVA (+ MAME), Downloads, Bridge, Settings, Library, Home
          const viewChecks = {};
          const viewIds = ['sagas', 'catalog', 'downloads', 'bridge', 'settings', 'library', 'home'];
          for (const viewName of viewIds) {
            const navBtn = page.locator(`#nav-${viewName}`);
            if (await navBtn.count() === 0) continue;
            await navBtn.click();
            await page.waitForTimeout(220);
            if (viewName === 'sagas') {
              const chestLogoPresent = (await page.locator('.brand-logo .chest-lid').count()) > 0;
              const sagaCardsCount = await page.locator('#sagas-gallery .saga-card').count();
              const sagaCardArtCount = await page.locator('#sagas-gallery .saga-card-art').count();
              const galleryFillBtn = (await page.locator('#sagas-gallery-fill-btn').count()) > 0;
              await page.screenshot({ path: path.join(args.out, 'desktop-sagas.png'), fullPage: false });
              let timelineEntriesCount = 0;
              let detailOverflow = false;
              let detailAxeSevere = 0;
                let detailHasDryLabel = false;
                let detailHasHistory = false;
                let detailFillBtn = false;
                let heroKeyImgOk = false;
                if (sagaCardsCount > 0) {
                  const sonicCard = page.locator('#sagas-gallery .saga-card', { hasText: 'Sonic' });
                  if ((await sonicCard.count()) > 0) await sonicCard.first().click();
                  else await page.locator('#sagas-gallery .saga-card').first().click();
                  await page.waitForTimeout(280);
                  timelineEntriesCount = await page.locator('#saga-timeline .saga-entry').count();
                  detailHasDryLabel = (await page.locator('#saga-detail-banner').innerText()).toUpperCase().includes('SAGA CURADA');
                  detailHasHistory = (await page.locator('#saga-history-container').innerText()).trim().length >= 60;
                  detailFillBtn = (await page.locator('#saga-fill-covers-btn').count()) > 0;
                  heroKeyImgOk = await page.evaluate(() => {
                    const img = document.querySelector('.saga-hero-keyart img');
                    return Boolean(img && !img.hidden && img.complete && img.naturalWidth > 0 && window.getComputedStyle(img).position === 'absolute');
                  });
                const dVm = await collectDomMetrics(page);
                const dAxe = await runAxe(page, axeSource);
                detailOverflow = dVm.horizontalOverflow;
                detailAxeSevere = dAxe.violations.filter(v => v.impact === 'critical' || v.impact === 'serious').length;
                await page.screenshot({ path: path.join(args.out, 'desktop-saga-detail.png'), fullPage: false });
                await page.click('#saga-back-btn');
                await page.waitForTimeout(160);
              }
              interactionResults.sagasView = {
                chestLogoPresent,
                sagaCardsCount,
                sagaCardArtCount,
                galleryFillBtn,
                timelineEntriesCount,
                detailOverflow,
                detailAxeSevere,
                detailHasDryLabel,
                detailHasHistory,
                detailFillBtn,
                heroKeyImgOk
              };
            }
            if (viewName === 'catalog') {
              const platformCount = await page.locator('#minerva-platform option').count();
              const hasExtended = await page.evaluate(() => {
                const vals = [...document.querySelectorAll('#minerva-platform option')].map(o => o.value);
                return ['mame', 'dc', 'saturn', 'psp', 'ps2', 'gc', 'wii', 'switch'].every(v => vals.includes(v));
              });
              // 1. Arcade unified FBNeo + MAME: search "Killer Instinct" -> MAME direct download!
              await page.selectOption('#minerva-platform', 'arcade');
              await page.fill('#minerva-query', 'Killer Instinct');
              await page.click('#minerva-submit-btn');
              await page.waitForSelector('#minerva-grid:not([hidden])', { timeout: 10000 }).catch(() => {});
              const killerInstinctHits = await page.locator('#minerva-grid .rom-card').count();
              const hasMameBadge = await page.evaluate(() =>
                [...document.querySelectorAll('#minerva-grid .rom-card')].some(c => c.textContent.includes('MAME'))
              );
              let kinstDirectBtn = false;
              let nonIntrusiveFlow = null;
              if (killerInstinctHits > 0) {
                await page.locator('#minerva-grid .rom-card').first().click();
                await page.waitForSelector('#inspection-drawer.open', { timeout: 5000 }).catch(() => {});
                await page.waitForSelector('#ia-files-container .ia-dl-btn, #inspection-drawer .minerva-ia-btn', { timeout: 12000 }).catch(() => {});
                kinstDirectBtn = (await page.locator('#ia-files-container .ia-dl-btn, #inspection-drawer .minerva-ia-btn').count()) > 0;

                if ((await page.locator('#ia-files-container .ia-dl-btn').count()) > 0) {
                  let dlCompleted = false;
                  let postedTransferPayload = null;
                  let mockTrJob = null;
                  const sampleRom = await page.evaluate(async () => {
                    try {
                      const rows = await fetch('/api/v1/library').then(r => r.json());
                      return rows.find(x => !x.isSys && x.relativePath) || null;
                    } catch {
                      return null;
                    }
                  });
                  const sampleLeaf = sampleRom ? String(sampleRom.relativePath).split(/[\\/]/).pop() : 'kinst.zip';
                  const mockJobBase = {
                    id: 'mock-kinst-1',
                    provider: 'Internet Archive',
                    identifier: 'arcade_kinst',
                    filename: sampleLeaf,
                    destPath: `F:\\Games\\RetroArch\\${sampleRom?.platform || 'arcade'}\\${sampleLeaf}`,
                    platform: sampleRom?.platform || 'arcade',
                    title: sampleRom?.title || 'Killer Instinct (Mock Test)',
                    size: 2048000,
                    bytes: 1024000,
                    state: 'downloading',
                    message: 'Descargando en segundo plano…'
                  };
                  await page.route('**/api/v1/downloads/ia', async route => {
                    if (route.request().method() === 'POST') {
                      await route.fulfill({
                        status: 200,
                        contentType: 'application/json',
                        body: JSON.stringify(mockJobBase)
                      });
                    } else {
                      await route.continue();
                    }
                  });
                  await page.route('**/api/v1/downloads', async route => {
                    if (route.request().method() === 'GET') {
                      const payload = dlCompleted
                        ? [{ ...mockJobBase, bytes: 2048000, state: 'complete', message: 'Descarga lista' }]
                        : [mockJobBase];
                      await route.fulfill({
                        status: 200,
                        contentType: 'application/json',
                        body: JSON.stringify(payload)
                      });
                    } else {
                      await route.continue();
                    }
                  });
                  await page.route('**/api/v1/transfers', async route => {
                    if (route.request().method() === 'POST') {
                      postedTransferPayload = route.request().postDataJSON();
                      mockTrJob = {
                        id: 'mock-tr-1',
                        entryId: postedTransferPayload.entryId,
                        title: sampleRom?.title || 'Killer Instinct',
                        targetId: postedTransferPayload.targetId,
                        targetName: 'PS4 (EmuSuNi)',
                        remotePath: `${postedTransferPayload.remoteDir || '/data/EmuSuNi/Roms'}/${sampleLeaf}`,
                        state: 'complete',
                        bytes: 1024000,
                        total: 1024000,
                        message: `Enviado directo a ${postedTransferPayload.remoteDir || '/data/EmuSuNi/Roms'}/${sampleLeaf}`
                      };
                      await route.fulfill({
                        status: 200,
                        contentType: 'application/json',
                        body: JSON.stringify(mockTrJob)
                      });
                    } else if (route.request().method() === 'GET') {
                      await route.fulfill({
                        status: 200,
                        contentType: 'application/json',
                        body: JSON.stringify(mockTrJob ? [mockTrJob] : [])
                      });
                    } else {
                      await route.continue();
                    }
                  });

                  const firstDlBtn = page.locator('#ia-files-container .ia-dl-btn').first();
                  await firstDlBtn.click();
                  await page.waitForSelector('#toast-region .toast', { timeout: 4000 }).catch(() => {});
                  await page.waitForTimeout(200);

                  const stayedInCatalog = await page.locator('#view-catalog.active').count() > 0;
                  const drawerStayedOpen = await page.locator('#inspection-drawer.open').count() > 0;
                  const btnTextAfter = (await firstDlBtn.textContent()) || '';
                  const btnDisabledAfter = await firstDlBtn.isDisabled();
                  const queueToastShown = ((await page.locator('#toast-region').textContent()) || '').includes('Descarga en cola');
                  const floatingDockVisible = !(await page.locator('#download-mini-dock').evaluate(el => el.hidden));
                  const dockVm = await collectDomMetrics(page);
                  const dockAxe = await runAxe(page, axeSource);

                  await page.screenshot({ path: path.join(args.out, 'desktop-mame-kinst.png'), fullPage: false });

                  dlCompleted = true;
                  await page.waitForFunction(
                    () => (document.querySelector('#toast-region')?.textContent || '').includes('Descarga lista'),
                    { timeout: 4000 }
                  ).catch(() => {});
                  const completionToastShown = ((await page.locator('#toast-region').textContent()) || '').includes('Descarga lista');

                  await page.keyboard.press('Escape');
                  await page.waitForTimeout(200);
                  await page.click('#nav-downloads');
                  await page.waitForTimeout(220);
                  const showLibBtns = await page.locator('#download-jobs-list .job-show-lib').count();
                  const sendFtpBtns = await page.locator('#download-jobs-list .job-send-ftp').count();

                  if (sendFtpBtns > 0) {
                    await page.locator('#download-jobs-list .job-send-ftp').first().click();
                    await page.waitForSelector('#transfer-jobs-list .job-card', { timeout: 4000 }).catch(() => {});
                    await page.waitForTimeout(180);
                  }
                  await page.screenshot({ path: path.join(args.out, 'desktop-downloads.png'), fullPage: false });

                  const directFtpPosted = Boolean(
                    postedTransferPayload &&
                    (!sampleRom || postedTransferPayload.entryId === sampleRom.entryId) &&
                    postedTransferPayload.targetId === 'ps4-emusuni'
                  );
                  const directFtpNoDrawer = (await page.locator('#inspection-drawer.open').count()) === 0;
                  const directFtpStayedInDownloads = (await page.locator('#view-downloads.active').count()) > 0;
                  const directFtpTransferCardVisible = (await page.locator('#transfer-jobs-list .job-card').count()) > 0;

                  nonIntrusiveFlow = {
                    stayedInCatalog,
                    drawerStayedOpen,
                    buttonMarkedQueued: btnDisabledAfter && btnTextAfter.includes('En cola'),
                    queueToastShown,
                    floatingDockVisible,
                    completionToastShown,
                    completedActionsPresent: showLibBtns > 0 && sendFtpBtns > 0,
                    directFtpPosted,
                    directFtpNoDrawer,
                    directFtpStayedInDownloads,
                    directFtpTransferCardVisible,
                    dockOverflow: dockVm.horizontalOverflow,
                    dockAxeSevere: dockAxe.violations.filter(v => v.impact === 'critical' || v.impact === 'serious').length
                  };

                  await page.unroute('**/api/v1/downloads/ia');
                  await page.unroute('**/api/v1/downloads');
                  await page.unroute('**/api/v1/transfers');
                  await page.evaluate(() => {
                    if (typeof state !== 'undefined' && state.downloadJobs) {
                      state.downloadJobs.delete('IA:mock-kinst-1');
                      state.notifiedDownloads?.delete('IA:mock-kinst-1');
                      if (typeof renderQueues === 'function') renderQueues();
                    }
                  });
                  await page.click('#nav-catalog');
                  await page.waitForTimeout(180);
                } else {
                  await page.screenshot({ path: path.join(args.out, 'desktop-mame-kinst.png'), fullPage: false });
                  await page.keyboard.press('Escape');
                  await page.waitForTimeout(200);
                }
              }

              // 2. Arcade unified "Mortal Kombat" (returns both FBNeo + MAME hits)
              await page.selectOption('#minerva-platform', 'arcade');
              await page.fill('#minerva-query', 'Mortal Kombat');
              await page.click('#minerva-submit-btn');
              await page.waitForSelector('#minerva-grid:not([hidden])', { timeout: 10000 }).catch(() => {});
              const mkHits = await page.locator('#minerva-grid .rom-card').count();

              // 3. Extended PS2 IA search + in-app download drawer check ("Descargar en Oz")
              await page.selectOption('#minerva-platform', 'ps2');
              await page.fill('#minerva-query', 'Resident Evil');
              await page.click('#minerva-submit-btn');
              await page.waitForSelector('#minerva-grid:not([hidden])', { timeout: 15000 }).catch(() => {});
              const ps2Hits = await page.locator('#minerva-grid .rom-card').count();
              let iaDrawerHasDirectBtn = false;
              if (ps2Hits > 0) {
                await page.locator('#minerva-grid .rom-card').first().click();
                await page.waitForSelector('#inspection-drawer.open', { timeout: 5000 }).catch(() => {});
                await page.waitForSelector('#ia-files-container .ia-dl-btn', { timeout: 12000 }).catch(() => {});
                iaDrawerHasDirectBtn = (await page.locator('#ia-files-container .ia-dl-btn').count()) > 0;
                await page.screenshot({ path: path.join(args.out, 'desktop-ia-drawer.png'), fullPage: false });
                await page.keyboard.press('Escape');
                await page.waitForTimeout(200);
              }

              // 4. All platforms search ("Resident") + auto-loaded WebP covers
              await page.selectOption('#minerva-platform', '');
              await page.fill('#minerva-query', 'Resident');
              await page.click('#minerva-submit-btn');
              await page.waitForSelector('#minerva-grid:not([hidden])', { timeout: 12000 }).catch(() => {});
              await page.waitForFunction(() =>
                document.querySelectorAll('#minerva-grid img.cover-photo').length > 0,
                { timeout: 12000 }
              ).catch(() => {});
              await page.waitForTimeout(300);
              const minervaAutoCovers = await page.evaluate(() =>
                [...document.querySelectorAll('#minerva-grid img.cover-photo')].filter(i => i.complete && i.naturalWidth > 0).length
              );
              await page.screenshot({ path: path.join(args.out, 'desktop-minerva-covers.png'), fullPage: false });
              interactionResults.minervaCatalog = {
                platformCount,
                hasExtended,
                killerInstinctHits,
                hasMameBadge,
                kinstDirectBtn,
                nonIntrusiveFlow,
                mkHits,
                ps2Hits,
                iaDrawerHasDirectBtn,
                minervaAutoCovers
              };
            }
            if (viewName === 'bridge') {
              const targetPills = await page.locator('#bridge-selector-grid .bridge-target-pill').count();
              const overrideRows = await page.locator('.target-overrides-list .override-row').count();
              await page.screenshot({ path: path.join(args.out, 'desktop-bridge.png'), fullPage: false });
              interactionResults.bridgeOverrides = { targetPills, overrideRows };
            }
            if (viewName === 'settings') {
              const consolePathRows = await page.locator('#console-paths-list .console-path-row').count();
              await page.screenshot({ path: path.join(args.out, 'desktop-settings.png'), fullPage: false });
              const settingsTabIssues = [];
              for (const tabKey of ['paths', 'covers', 'ia', 'ai', 'share']) {
                await page.click(`#stab-${tabKey}`);
                await page.waitForTimeout(140);
                const tVm = await collectDomMetrics(page);
                const tAxe = await runAxe(page, axeSource);
                const sev = tAxe.violations.filter(v => v.impact === 'critical' || v.impact === 'serious').length;
                if (tVm.horizontalOverflow || tVm.overlapAndClipping.length || sev) {
                  settingsTabIssues.push({ tabKey, overflow: tVm.horizontalOverflow, overlap: tVm.overlapAndClipping, axeSevere: sev });
                }
                if (tabKey === 'ia') {
                  await page.screenshot({ path: path.join(args.out, 'desktop-settings-ia.png'), fullPage: false });
                }
              }
              // Verify Setup Wizard (5 steps + API guides + summary) without opening native OS file dialogs
              await page.click('#open-wizard-btn');
              await page.waitForSelector('#setup-wizard-backdrop:not([hidden])', { timeout: 3000 }).catch(() => {});
              const wizOpen = !(await page.locator('#setup-wizard-backdrop').evaluate(el => el.hidden));
              const wizStep1Inputs = (await page.locator('#wiz-roms-dir').count()) > 0 && (await page.locator('#wiz-covers-dir').count()) > 0;
              const wiz1Vm = await collectDomMetrics(page);
              const wiz1Axe = await runAxe(page, axeSource);
              await page.screenshot({ path: path.join(args.out, 'desktop-wizard-step1.png'), fullPage: false });

              await page.click('#wiz-next-btn');
              await page.waitForTimeout(140);
              const wizSgdbGuide = await page.locator('#wiz-step-2 a[href*="steamgriddb.com/profile/preferences/api"]').count() > 0;
              const wiz2Vm = await collectDomMetrics(page);
              const wiz2Axe = await runAxe(page, axeSource);
              await page.screenshot({ path: path.join(args.out, 'desktop-wizard-step2.png'), fullPage: false });

              await page.click('#wiz-next-btn');
              await page.waitForTimeout(120);
              const wizIaGuide = await page.locator('#wiz-step-3 a[href*="archive.org/account/s3.php"]').count() > 0;

              await page.click('#wiz-next-btn');
              await page.waitForTimeout(120);
              await page.click('.wiz-console-card[data-wiz-console="ps4-emusuni"]');
              await page.waitForTimeout(120);
              const wizFtpExpanded = !(await page.locator('#wiz-ftp-fields').evaluate(el => el.hidden));

              await page.click('#wiz-next-btn');
              await page.waitForTimeout(140);
              const wizSummaryCards = await page.locator('#wiz-summary-grid .wiz-summary-card').count();
              await page.screenshot({ path: path.join(args.out, 'desktop-wizard-step5.png'), fullPage: false });

              await page.click('#wiz-skip-btn');
              await page.waitForTimeout(140);
              const wizClosedClean = await page.locator('#setup-wizard-backdrop').evaluate(el => el.hidden);

              await page.click('#stab-paths');
              await page.waitForTimeout(120);
              interactionResults.iaSettingsPresent =
                (await page.locator('#setting-covers-dir').count()) > 0 &&
                (await page.locator('#setting-ia-access').count()) > 0 &&
                (await page.locator('#setting-ia-secret').count()) > 0 &&
                (await page.locator('#setting-ai-provider').count()) > 0 &&
                (await page.locator('#share-portable-path').count()) > 0 &&
                consolePathRows > 5 &&
                settingsTabIssues.length === 0;
              interactionResults.setupWizard = {
                wizOpen,
                wizStep1Inputs,
                wizSgdbGuide,
                wizIaGuide,
                wizFtpExpanded,
                wizSummaryCards,
                wizClosedClean,
                overflow: wiz1Vm.horizontalOverflow || wiz2Vm.horizontalOverflow,
                axeSevere:
                  wiz1Axe.violations.filter(v => v.impact === 'critical' || v.impact === 'serious').length +
                  wiz2Axe.violations.filter(v => v.impact === 'critical' || v.impact === 'serious').length
              };
              interactionResults.settingsConsolePaths = { consolePathRows, settingsTabIssues };
            }
            const vm = await collectDomMetrics(page);
            const vAxe = await runAxe(page, axeSource);
            viewChecks[viewName] = {
              horizontalOverflow: vm.horizontalOverflow,
              overlapAndClipping: vm.overlapAndClipping,
              axeSevere: vAxe.violations.filter(v => v.impact === 'critical' || v.impact === 'serious').length
            };
          }
          interactionResults.views = viewChecks;
        }

        vpReport.interactions = interactionResults;
      }

      report.viewports[vp.name] = vpReport;
      await context.close();
    }
  } finally {
    await browser.close();
  }

  // Evaluate pass/fail criteria
  const failures = [];
  for (const [vpName, vpData] of Object.entries(report.viewports)) {
    if (vpData.consoleErrors.length) failures.push(`${vpName}: ${vpData.consoleErrors.length} console errors`);
    if (vpData.pageErrors.length) failures.push(`${vpName}: ${vpData.pageErrors.length} page errors`);
    if (vpData.networkFailures.length) failures.push(`${vpName}: ${vpData.networkFailures.length} network failures`);
    if (vpData.metrics.brokenImages.length) failures.push(`${vpName}: ${vpData.metrics.brokenImages.length} broken images`);
    if (vpData.metrics.horizontalOverflow) failures.push(`${vpName}: horizontal overflow (${vpData.metrics.scrollWidth} > ${vpData.metrics.viewportWidth})`);
    if (vpData.metrics.aspectRatioAnomalies.length) failures.push(`${vpName}: aspect ratio anomalies`);
    if (vpData.metrics.overlapAndClipping.length) failures.push(`${vpName}: overlap/clipping (${JSON.stringify(vpData.metrics.overlapAndClipping)})`);
    const severeAxe = vpData.axeViolations.filter(v => v.impact === 'critical' || v.impact === 'serious');
    if (severeAxe.length) failures.push(`${vpName}: ${severeAxe.length} critical/serious axe violations (${severeAxe.map(v => v.id).join(', ')})`);

    if (vpData.interactions) {
      if (vpData.interactions.drawer) {
        if (vpData.interactions.drawer.horizontalOverflow) failures.push(`${vpName} drawer: horizontal overflow`);
        if (vpData.interactions.drawer.overlapAndClipping.length) failures.push(`${vpName} drawer: overlap/clipping`);
        if (vpName === 'desktop' && !vpData.interactions.drawer.drawerFtpPathVal) failures.push(`${vpName} drawer: missing resolved #drawer-ftp-path`);
        if (vpName === 'desktop' && vpData.interactions.drawer.drawerHasPhase1 === false) failures.push(`${vpName} drawer: missing synopsis/download/patches blocks`);
        const drawerAxe = vpData.interactions.drawer.axeViolations.filter(v => v.impact === 'critical' || v.impact === 'serious');
        if (drawerAxe.length) failures.push(`${vpName} drawer: ${drawerAxe.length} severe axe violations (${drawerAxe.map(v => v.id).join(', ')})`);
      }
      if (vpData.interactions.bridgeOverrides) {
        const bo = vpData.interactions.bridgeOverrides;
        if (bo.targetPills < 3 || bo.overrideRows <= 0) failures.push(`${vpName}: bridge console tabs/overrides missing (${JSON.stringify(bo)})`);
      }
      if (vpData.interactions.tableView) {
        if (vpData.interactions.tableView.horizontalOverflow) failures.push(`${vpName} table: horizontal overflow`);
      }
      if (vpData.interactions.ctrlKSearch && !vpData.interactions.ctrlKSearch.focusedInput) {
        failures.push(`${vpName}: Ctrl+K did not focus #global-search`);
      }
      if (vpData.interactions.favoritesToggle && !vpData.interactions.favoritesToggle.incremented) {
        failures.push(`${vpName}: favorite toggle did not increment count`);
      }
      if (vpData.interactions.targetedCoverSync) {
        const tc = vpData.interactions.targetedCoverSync;
        if (!tc.validScope) failures.push(`${vpName}: targeted cover sync exceeded filtered count (${tc.syncTotalObserved} > ${tc.filteredCount})`);
        if (!tc.preservedSearch) failures.push(`${vpName}: cover sync reset active search filter`);
      }
      if (vpData.interactions.sidebarCollapse) {
        const sc = vpData.interactions.sidebarCollapse;
        if (!sc.hasClass || sc.width > 82) failures.push(`${vpName}: sidebar did not collapse (${sc.width}px)`);
        if (sc.horizontalOverflow) failures.push(`${vpName} collapsed sidebar: horizontal overflow`);
        if (sc.overlapAndClipping.length) failures.push(`${vpName} collapsed sidebar: overlap/clipping (${JSON.stringify(sc.overlapAndClipping)})`);
        if (sc.axeSevere) failures.push(`${vpName} collapsed sidebar: ${sc.axeSevere} severe axe violations`);
      }
      if (vpData.interactions.homeDashboard) {
        const hd = vpData.interactions.homeDashboard;
        if (!hd.homeActive || !hd.heroPresent || hd.consoleTiles <= 0) failures.push(`${vpName}: Home dashboard incomplete`);
        if (hd.recentCardsCount <= 0) failures.push(`${vpName}: Home Recientes did not update after mark played`);
        if (!hd.featuredCartridgesCount || hd.featuredCartridgesCount <= 0) failures.push(`${vpName}: Destacados cartridge shelves missing`);
        if (!hd.featuredClean) failures.push(`${vpName}: Destacados still has superfluous text or missing console icons (${JSON.stringify(hd.featuredClutterDebug)})`);
        if (!hd.ctxMenuOk) failures.push(`${vpName}: Right-click context menu failed on featured card`);
        if (!hd.moodPickerOk) failures.push(`${vpName}: Mood picker "¿Qué juego hoy?" check failed`);
        if ((hd.recsCardsCount || 0) + (hd.discoverPillsCount || 0) <= 0) failures.push(`${vpName}: Home Recommendations shelf empty`);
        if (!hd.homeFillPosted || !hd.homeFillRetryMisses) failures.push(`${vpName}: Home "Rellenar carátulas" failed to trigger retryMisses sync (${JSON.stringify(hd)})`);
        if (!hd.brandHasGameVault || hd.brandHasOldBrand) failures.push(`${vpName}: Branding mismatch (${hd.brandText})`);
        if (!hd.primaryBtnRadius || hd.primaryBtnRadius < 18) failures.push(`${vpName}: Primary buttons not pill/oval (${hd.primaryBtnRadius}px)`);
      }
      if (vpData.interactions.sagasView) {
        const sv = vpData.interactions.sagasView;
        if (sv.sagaCardsCount <= 0 || sv.timelineEntriesCount <= 0) failures.push(`${vpName}: Sagas view gallery/timeline empty (${JSON.stringify(sv)})`);
        if (!sv.chestLogoPresent) failures.push(`${vpName}: Treasure chest brand logo missing`);
        if (sv.sagaCardArtCount !== sv.sagaCardsCount || !sv.galleryFillBtn || !sv.detailFillBtn || !sv.detailHasHistory || sv.detailHasDryLabel || !sv.heroKeyImgOk) {
          failures.push(`${vpName}: Sagas overhaul assertions failed (${JSON.stringify(sv)})`);
        }
        if (sv.detailOverflow || sv.detailAxeSevere) failures.push(`${vpName}: Sagas detail overflow/axe failure (${JSON.stringify(sv)})`);
      }
      if (vpName === 'desktop' && args.mode === 'redesign') {
        const cfRad = vpData.interactions.coverFrameRadius || 0;
        if (cfRad < 12) failures.push(`${vpName}: Library .cover-frame not rounded app-icon style (${cfRad}px)`);
        if ((vpData.interactions.libraryConsolePills || 0) < 2) failures.push(`${vpName}: Colored console pills in Mi Biblioteca missing (${vpData.interactions.libraryConsolePills})`);
      }
      if (vpData.interactions.minervaCatalog) {
        const mc = vpData.interactions.minervaCatalog;
        if (!mc.hasExtended || mc.platformCount < 15) failures.push(`${vpName}: MiNERVA platforms missing extended systems (${mc.platformCount})`);
        if (mc.killerInstinctHits <= 0 || !mc.hasMameBadge || !mc.kinstDirectBtn) failures.push(`${vpName}: MAME Killer Instinct check failed (${JSON.stringify(mc)})`);
        if (mc.mkHits <= 0) failures.push(`${vpName}: Mortal Kombat Arcade search returned 0 hits`);
        if (mc.ps2Hits > 0 && !mc.iaDrawerHasDirectBtn) failures.push(`${vpName}: PS2 IA drawer missing direct "Descargar en Oz" button`);
        if (mc.minervaAutoCovers <= 0) failures.push(`${vpName}: MiNERVA search did not auto-load WebP covers`);
        if (!mc.nonIntrusiveFlow) {
          failures.push(`${vpName}: nonIntrusiveFlow did not run`);
        } else {
          const nf = mc.nonIntrusiveFlow;
          if (!nf.stayedInCatalog || !nf.drawerStayedOpen) failures.push(`${vpName}: download kicked user out of drawer/catalog (${JSON.stringify(nf)})`);
          if (!nf.buttonMarkedQueued || !nf.queueToastShown || !nf.floatingDockVisible) failures.push(`${vpName}: missing inline queue/toast/dock feedback (${JSON.stringify(nf)})`);
          if (!nf.completionToastShown || !nf.completedActionsPresent) failures.push(`${vpName}: missing completion toast or library/FTP actions (${JSON.stringify(nf)})`);
          if (!nf.directFtpPosted || !nf.directFtpNoDrawer || !nf.directFtpStayedInDownloads || !nf.directFtpTransferCardVisible) {
            failures.push(`${vpName}: direct 1-click FTP transfer check failed (${JSON.stringify(nf)})`);
          }
          if (nf.dockOverflow || nf.dockAxeSevere) failures.push(`${vpName}: floating dock overflow/axe failure (${JSON.stringify(nf)})`);
        }
      }
      if (vpData.interactions.iaSettingsPresent === false) {
        failures.push(`${vpName}: IA API settings inputs missing`);
      }
      if (vpData.interactions.setupWizard) {
        const sw = vpData.interactions.setupWizard;
        if (!sw.wizOpen || !sw.wizStep1Inputs || !sw.wizSgdbGuide || !sw.wizIaGuide || !sw.wizFtpExpanded || sw.wizSummaryCards !== 5 || !sw.wizClosedClean) {
          failures.push(`${vpName}: setup wizard steps failed (${JSON.stringify(sw)})`);
        }
        if (sw.overflow || sw.axeSevere) {
          failures.push(`${vpName}: setup wizard overflow/axe failure (${JSON.stringify(sw)})`);
        }
      }
      if (vpData.interactions.views) {
        for (const [vName, vRes] of Object.entries(vpData.interactions.views)) {
          if (vRes.horizontalOverflow) failures.push(`${vpName} view ${vName}: horizontal overflow`);
          if (vRes.overlapAndClipping.length) failures.push(`${vpName} view ${vName}: overlap/clipping`);
          if (vRes.axeSevere) failures.push(`${vpName} view ${vName}: ${vRes.axeSevere} severe axe violations`);
        }
      }
    }
  }

  report.failures = failures;
  report.passed = failures.length === 0;

  const reportPath = path.join(args.out, 'audit-report.json');
  fs.writeFileSync(reportPath, JSON.stringify(report, null, 2), 'utf8');

  console.log(`\n=== AUDIT SUMMARY (${args.mode}) -> ${report.passed ? 'PASS' : 'FAIL'} ===`);
  for (const [vpName, vpData] of Object.entries(report.viewports)) {
    const m = vpData.metrics;
    console.log(
      `[${vpName} ${vpData.viewport}] overflow=${m.horizontalOverflow} (${m.scrollWidth}/${m.viewportWidth}) | ` +
      `consoleErr=${vpData.consoleErrors.length} | netFail=${vpData.networkFailures.length} | ` +
      `brokenImg=${m.brokenImages.length} | aspectAnom=${m.aspectRatioAnomalies.length} | ` +
      `overlap=${m.overlapAndClipping.length} | axeViol=${vpData.axeViolations.length} | ` +
      `readyMs=${m.libraryReadyMs}`
    );
  }
  if (failures.length) {
    console.log('Failures:');
    for (const f of failures) console.log(' - ' + f);
  }
  console.log(`Report saved: ${reportPath}`);

  if (args.mode === 'redesign' && !report.passed) {
    process.exitCode = 1;
  }
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});
