export async function api(url, options = {}) {
  const resp = await fetch(url, options);
  const text = await resp.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch (e) { data = text; }
  if (!resp.ok) throw new Error((data && data.detail) || (data && data.message) || `HTTP ${resp.status}`);
  return data;
}

/**
 * Subscribe to real-time PC Hub SSE updates (/api/v1/events) instead of timer polling.
 */
export function connectHubEvents(handlers = {}) {
  if (typeof EventSource === 'undefined') {
    return { close() {} };
  }
  const source = new EventSource('/api/v1/events');
  if (typeof handlers.onOpen === 'function') {
    source.onopen = ev => handlers.onOpen(ev);
  }
  if (typeof handlers.onError === 'function') {
    source.onerror = ev => handlers.onError(ev);
  }
  const eventNames = [
    'connected',
    'covers.progress',
    'covers.complete',
    'download.state',
    'download.progress',
    'transfer.state',
    'transfer.progress',
    'library.updated',
    'sync.updated',
  ];

  eventNames.forEach(name => {
    source.addEventListener(name, ev => {
      const fn = handlers[name];
      if (typeof fn !== 'function') return;
      let payload = null;
      try {
        payload = ev.data ? JSON.parse(ev.data) : null;
      } catch (e) {
        payload = ev.data;
      }
      fn(payload, ev);
    });
  });

  return {
    source,
    close() {
      try { source.close(); } catch (e) { /* ignore */ }
    },
  };
}
