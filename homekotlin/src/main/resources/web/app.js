// HomeKotlin Dashboard Client Script
// Ingress-aware API path resolution
const basePath = window.location.pathname.replace(/\/+$/, '');
const apiBase = basePath ? `${basePath}/api` : '/api';

let automations = [];
let entities = [];
let scripts = [];
let feedItems = [];
let feedFilter = 'all';
let currentDomainFilter = '';
let eventCount = 0;

// Initialize Dashboard
document.addEventListener('DOMContentLoaded', () => {
  fetchStatus();
  fetchAutomations();
  fetchEntities();
  fetchScripts();
  fetchServices();
  fetchTriggers();
  initEventStream();

  // Periodic polling for status/stats sync
  setInterval(fetchStatus, 4000);
  setInterval(fetchAutomations, 5000);
});

// Tab Navigation
function switchTab(tabId) {
  document.querySelectorAll('.nav-tab').forEach(btn => btn.classList.remove('active'));
  document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));

  const activeBtn = document.getElementById(`tab-btn-${tabId}`);
  const activePanel = document.getElementById(`panel-${tabId}`);
  if (activeBtn) activeBtn.classList.add('active');
  if (activePanel) activePanel.classList.add('active');
}

// 1. Telemetry & Status
async function fetchStatus() {
  try {
    const res = await fetch(`${apiBase}/status`);
    if (!res.ok) return;
    const data = await res.json();

    const pill = document.getElementById('ha-status-pill');
    const dot = document.getElementById('ha-status-dot');
    const text = document.getElementById('ha-status-text');

    if (data.isDemoMode) {
      text.innerText = 'DEMO / SIMULATION';
      dot.style.background = '#00F0FF';
      dot.style.boxShadow = '0 0 10px #00F0FF';
    } else if (data.connectionState === 'CONNECTED') {
      text.innerText = 'HA CONNECTED';
      dot.style.background = '#10B981';
      dot.style.boxShadow = '0 0 10px #10B981';
    } else if (data.connectionState === 'CONNECTING' || data.connectionState === 'AUTHENTICATING' || data.connectionState === 'RECONNECTING') {
      text.innerText = data.connectionState;
      dot.style.background = '#F59E0B';
      dot.style.boxShadow = '0 0 10px #F59E0B';
    } else {
      text.innerText = 'DISCONNECTED';
      dot.style.background = '#EF4444';
      dot.style.boxShadow = '0 0 10px #EF4444';
    }

    document.getElementById('stat-active-automations').innerText = data.activeAutomations;
    document.getElementById('stat-total-entities').innerText = data.totalEntities;
    document.getElementById('stat-memory').innerText = `${data.memoryUsageMb} MB`;
    document.getElementById('stat-uptime').innerText = formatUptime(data.uptimeSeconds);
  } catch (err) {
    console.warn('Status fetch failed', err);
  }
}

function formatUptime(sec) {
  if (sec < 60) return `${sec}s`;
  if (sec < 3600) return `${Math.floor(sec / 60)}m ${sec % 60}s`;
  return `${Math.floor(sec / 3600)}h ${Math.floor((sec % 3600) / 60)}m`;
}

// 2. Automations
async function fetchAutomations() {
  try {
    const res = await fetch(`${apiBase}/automations`);
    if (!res.ok) return;
    automations = await res.json();
    renderAutomations();
  } catch (err) {
    console.error('Failed to load automations', err);
  }
}

function renderAutomations() {
  const container = document.getElementById('automations-container');
  const query = (document.getElementById('automation-search-input')?.value || '').toLowerCase();

  const filtered = automations.filter(a => {
    return a.name.toLowerCase().includes(query) ||
      a.description.toLowerCase().includes(query) ||
      (a.sourceFile && a.sourceFile.toLowerCase().includes(query)) ||
      a.triggers.some(t => t.toLowerCase().includes(query));
  });

  document.getElementById('badge-automations-count').innerText = automations.length;
  document.getElementById('filter-total').innerText = automations.length;
  document.getElementById('filter-enabled').innerText = automations.filter(a => a.enabled).length;

  if (filtered.length === 0) {
    container.innerHTML = `
      <div style="grid-column: 1 / -1; padding: 40px; text-align: center; color: var(--text-dim);">
        <p style="font-size: 16px; margin-bottom: 8px;">No automations found matching your search</p>
        <span style="font-size: 13px;">Create new automations in <code class="inline-code">automations/*.kts</code></span>
      </div>`;
    return;
  }

  container.innerHTML = filtered.map(a => {
    const lastTrig = a.lastTriggered ? formatRelativeTime(a.lastTriggered) : 'Never';
    const duration = a.lastDurationMs != null ? `${a.lastDurationMs}ms` : '-';
    const triggerBadges = a.triggers.map(t => `<div class="trigger-badge">⚡ ${escapeHtml(t)}</div>`).join('');

    return `
      <div class="automation-card" id="card-${a.id}">
        <div>
          <div class="card-top">
            <div class="card-title-group">
              <h3 class="card-title">${escapeHtml(a.name)}</h3>
              <span class="card-source-tag">${escapeHtml(a.sourceFile || 'built-in')}</span>
            </div>
            <label class="switch" title="Toggle Automation">
              <input type="checkbox" ${a.enabled ? 'checked' : ''} onchange="toggleAutomation('${a.id}')">
              <span class="slider"></span>
            </label>
          </div>
          <p class="card-desc">${escapeHtml(a.description || 'No description provided.')}</p>
          <div class="card-triggers">
            ${triggerBadges}
          </div>
        </div>

        <div class="card-footer">
          <div class="stats-group">
            <span>Runs: <strong>${a.executionCount}</strong></span>
            <span>Last: <strong>${lastTrig}</strong></span>
            <span>Speed: <strong>${duration}</strong></span>
          </div>
          <button class="run-btn" onclick="triggerAutomation('${a.id}', '${escapeHtml(a.name)}')">
            ▶ Run Now
          </button>
        </div>
      </div>
    `;
  }).join('');
}

function filterAutomations() {
  renderAutomations();
}

async function toggleAutomation(id) {
  try {
    const res = await fetch(`${apiBase}/automations/${id}/toggle`, { method: 'POST' });
    if (res.ok) {
      const data = await res.json();
      const auto = automations.find(a => a.id === id);
      if (auto) auto.enabled = data.enabled;
      renderAutomations();
      showToast('Toggle State', `Automation is now ${data.enabled ? 'Enabled' : 'Disabled'}`);
    }
  } catch (e) {
    showToast('Error', 'Failed to toggle automation', true);
  }
}

async function triggerAutomation(id, name) {
  showToast('Executing', `Triggering ${name}...`);
  try {
    const res = await fetch(`${apiBase}/automations/${id}/trigger`, { method: 'POST' });
    if (res.ok) {
      const data = await res.json();
      if (data.success) {
        showToast('Success', `${name} executed in ${data.durationMs}ms`);
      } else {
        showToast('Warning', `${name} conditions not met or action failed: ${data.error}`, true);
      }
      fetchAutomations();
    }
  } catch (e) {
    showToast('Error', 'Execution failed: ' + e.message, true);
  }
}

// 3. Real-time Event Stream (SSE)
function initEventStream() {
  const sseUrl = `${apiBase}/stream`;
  const eventSource = new EventSource(sseUrl);

  eventSource.addEventListener('ha_event', (e) => {
    try {
      const event = JSON.parse(e.data);
      eventCount++;
      document.getElementById('badge-events-count').innerText = eventCount;
      appendFeedLine('ha_event', `[State Change] ${event.entityId} ➔ "${event.newState?.state}" (was "${event.oldState?.state || 'null'}")`);
      updateEntityStateInTable(event);
    } catch (err) {}
  });

  eventSource.addEventListener('execution', (e) => {
    try {
      const entry = JSON.parse(e.data);
      eventCount++;
      document.getElementById('badge-events-count').innerText = eventCount;
      const statusText = entry.success ? `SUCCESS (${entry.durationMs}ms)` : `FAILED (${entry.error})`;
      appendFeedLine(entry.success ? 'execution' : 'error', `[Automation Run] ${entry.automationName} triggered by ${entry.triggerSource} ➔ ${statusText}`);
      fetchAutomations();
    } catch (err) {}
  });

  eventSource.addEventListener('status', (e) => {
    fetchStatus();
  });

  eventSource.onerror = () => {
    // Reconnects automatically
  };
}

function appendFeedLine(type, message) {
  const feed = document.getElementById('terminal-feed');
  const now = new Date().toLocaleTimeString();

  let badgeClass = 'badge-state';
  let badgeLabel = 'STATE';
  if (type === 'execution') {
    badgeClass = 'badge-exec';
    badgeLabel = 'EXEC';
  } else if (type === 'error') {
    badgeClass = 'badge-err';
    badgeLabel = 'ERR';
  }

  const line = document.createElement('div');
  line.className = 'feed-line';
  line.dataset.type = type;
  line.innerHTML = `
    <span class="feed-time">${now}</span>
    <span class="feed-badge ${badgeClass}">${badgeLabel}</span>
    <span class="feed-content">${escapeHtml(message)}</span>
  `;

  if (feedFilter !== 'all' && type !== feedFilter) {
    line.style.display = 'none';
  }

  feed.appendChild(line);

  if (document.getElementById('autoscroll-toggle').checked) {
    feed.scrollTop = feed.scrollHeight;
  }
}

function setFeedFilter(filter) {
  feedFilter = filter;
  document.querySelectorAll('.feed-controls .pill-btn').forEach(b => b.classList.remove('active'));
  const activeBtn = document.getElementById(`feed-filter-${filter === 'ha_event' ? 'state' : filter === 'execution' ? 'exec' : 'all'}`);
  if (activeBtn) activeBtn.classList.add('active');

  const lines = document.querySelectorAll('.feed-line');
  lines.forEach(l => {
    if (filter === 'all' || l.dataset.type === filter) {
      l.style.display = 'flex';
    } else {
      l.style.display = 'none';
    }
  });
}

function clearFeed() {
  document.getElementById('terminal-feed').innerHTML = `
    <div class="terminal-intro">
      <span class="prompt-text">homekotlin@ha:~$</span> stream cleared. Waiting for events...
    </div>
  `;
}

// 4. Entities
async function fetchEntities() {
  try {
    const res = await fetch(`${apiBase}/entities`);
    if (!res.ok) return;
    entities = await res.json();
    document.getElementById('badge-entities-count').innerText = entities.length;
    renderEntities();
  } catch (err) {
    console.error('Failed to load entities', err);
  }
}

function renderEntities() {
  const tbody = document.getElementById('entities-table-body');
  const query = (document.getElementById('entity-search-input')?.value || '').toLowerCase();

  const filtered = entities.filter(e => {
    const matchesDomain = !currentDomainFilter || e.domain === currentDomainFilter;
    const matchesQuery = !query || e.entity_id.toLowerCase().includes(query) || (e.friendly_name && e.friendly_name.toLowerCase().includes(query));
    return matchesDomain && matchesQuery;
  });

  if (filtered.length === 0) {
    tbody.innerHTML = `<tr><td colspan="5" style="text-align: center; color: var(--text-dim); padding: 30px;">No entities found</td></tr>`;
    return;
  }

  tbody.innerHTML = filtered.map(e => {
    let stateClass = 'state-val';
    if (e.state === 'on') stateClass = 'state-on';
    else if (e.state === 'off') stateClass = 'state-off';

    return `
      <tr id="row-${escapeHtml(e.entity_id)}">
        <td style="font-family: var(--font-mono); font-weight: 600; color: #FFF;">${escapeHtml(e.entity_id)}</td>
        <td style="color: var(--text-muted);">${escapeHtml(e.friendly_name || e.entity_id)}</td>
        <td><span class="state-pill ${stateClass}">${escapeHtml(e.state)}</span></td>
        <td><span class="inline-code">${escapeHtml(e.domain)}</span></td>
        <td style="color: var(--text-dim); font-size: 11px;">${escapeHtml(e.last_updated || 'Just now')}</td>
      </tr>
    `;
  }).join('');
}

function filterEntities() {
  renderEntities();
}

function selectDomainFilter(domain) {
  currentDomainFilter = domain;
  document.querySelectorAll('#domain-filters .pill-btn').forEach(btn => {
    btn.classList.remove('active');
    if (btn.innerText.toLowerCase().includes(domain) || (domain === '' && btn.innerText === 'All')) {
      btn.classList.add('active');
    }
  });
  renderEntities();
}

function updateEntityStateInTable(event) {
  const existing = entities.find(e => e.entity_id === event.entityId);
  if (existing && event.newState) {
    existing.state = event.newState.state;
    existing.last_updated = event.newState.last_updated;
    renderEntities();
  }
}

// 5. Scripts & Reload
async function fetchScripts() {
  try {
    const res = await fetch(`${apiBase}/scripts`);
    if (!res.ok) return;
    scripts = await res.json();
    document.getElementById('badge-scripts-count').innerText = scripts.length;
    renderScripts();
  } catch (err) {}
}

function renderScripts() {
  const container = document.getElementById('scripts-container');
  if (scripts.length === 0) {
    container.innerHTML = `<div style="padding: 20px; color: var(--text-dim);">No script files detected yet.</div>`;
    return;
  }

  container.innerHTML = scripts.map(s => {
    const isOk = s.status === 'LOADED';
    const statusBadge = isOk
      ? `<span class="script-status-badge status-loaded">✓ LOADED (${s.automationCount} automations)</span>`
      : `<span class="script-status-badge status-error">✕ COMPILATION ERROR</span>`;

    const errorBox = s.error ? `<div class="diag-error-box"><strong>Error:</strong> ${escapeHtml(s.error)}</div>` : '';

    return `
      <div class="script-item-card">
        <div style="flex: 1;">
          <div class="script-header">
            <span class="script-name">${escapeHtml(s.filename)}</span>
            ${statusBadge}
          </div>
          <div style="margin-top: 4px; font-size: 12px; color: var(--text-dim);">Path: ${escapeHtml(s.path)}</div>
          ${errorBox}
        </div>
      </div>
    `;
  }).join('');
}

async function reloadScripts() {
  showToast('Reloading', 'Recompiling all automation scripts...');
  try {
    const res = await fetch(`${apiBase}/scripts/reload`, { method: 'POST' });
    if (res.ok) {
      await fetchScripts();
      await fetchAutomations();
      showToast('Success', 'Scripts recompiled successfully!');
    }
  } catch (err) {
    showToast('Error', 'Reload failed', true);
  }
}

// 6. Codegen Modal
async function openCodegenModal() {
  const modal = document.getElementById('codegen-modal');
  modal.classList.add('active');
  const codeEl = document.getElementById('codegen-output');
  codeEl.innerText = '// Fetching entities from Home Assistant and generating Kotlin DSL models...';

  try {
    const res = await fetch(`${apiBase}/codegen`, { method: 'POST' });
    if (res.ok) {
      const data = await res.json();
      codeEl.innerText = data.code;
    } else {
      codeEl.innerText = '// Error generating code';
    }
  } catch (e) {
    codeEl.innerText = '// Failed to connect to server';
  }
}

function closeCodegenModal(event) {
  document.getElementById('codegen-modal').classList.remove('active');
}

function copyCodegen() {
  const code = document.getElementById('codegen-output').innerText;
  navigator.clipboard.writeText(code).then(() => {
    showToast('Copied', 'Entities.kt copied to clipboard!');
  });
}

// Helpers
function showToast(title, message, isError = false) {
  const toast = document.getElementById('execution-toast');
  document.getElementById('toast-title').innerText = title;
  document.getElementById('toast-msg').innerText = message;
  document.getElementById('toast-icon').innerText = isError ? '⚠️' : '🚀';
  toast.classList.add('show');
  setTimeout(() => toast.classList.remove('show'), 3500);
}

function formatRelativeTime(timestamp) {
  const diffSec = Math.floor((Date.now() - timestamp) / 1000);
  if (diffSec < 5) return 'Just now';
  if (diffSec < 60) return `${diffSec}s ago`;
  if (diffSec < 3600) return `${Math.floor(diffSec / 60)}m ago`;
  return `${Math.floor(diffSec / 3600)}h ago`;
}

function escapeHtml(text) {
  if (!text) return '';
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

// 6. Services & Triggers Explorer
let servicesList = [];
let triggersCatalog = null;

async function fetchServices() {
  try {
    const res = await fetch(`${apiBase}/services`);
    if (!res.ok) return;
    servicesList = await res.json();
    const countEl = document.getElementById('badge-services-count');
    const badgeEl = document.getElementById('services-badge-count');
    if (countEl) countEl.innerText = servicesList.length;
    if (badgeEl) badgeEl.innerText = servicesList.length;

    // Populate domain filter dropdown
    const domains = [...new Set(servicesList.map(s => s.domain))].sort();
    const select = document.getElementById('service-domain-filter');
    if (select && select.children.length <= 1) {
      select.innerHTML = '<option value="">All Domains</option>' +
        domains.map(d => `<option value="${d}">${d} (${servicesList.filter(s => s.domain === d).length})</option>`).join('');
    }

    renderServices();
  } catch (err) {
    console.error('Failed to load services', err);
  }
}

function renderServices() {
  const container = document.getElementById('services-container');
  if (!container) return;

  const query = (document.getElementById('service-search-input')?.value || '').toLowerCase();
  const domainFilter = document.getElementById('service-domain-filter')?.value || '';

  const filtered = servicesList.filter(s => {
    const matchesDomain = !domainFilter || s.domain === domainFilter;
    const matchesQuery = !query ||
      s.serviceId.toLowerCase().includes(query) ||
      (s.name && s.name.toLowerCase().includes(query)) ||
      (s.description && s.description.toLowerCase().includes(query));
    return matchesDomain && matchesQuery;
  });

  if (filtered.length === 0) {
    container.innerHTML = `<div style="text-align: center; color: var(--text-dim); padding: 30px;">No services matching criteria</div>`;
    return;
  }

  container.innerHTML = filtered.map(s => {
    let fieldsHtml = '';
    if (s.fields && Object.keys(s.fields).length > 0) {
      const fieldItems = Object.entries(s.fields).map(([k, f]) => `
        <div class="service-field-item">
          <span class="field-name">${escapeHtml(k)}</span>
          ${f.required ? '<span class="field-req">Required</span>' : ''}
          <span class="field-desc">— ${escapeHtml(f.description || f.name || '')}</span>
        </div>
      `).join('');
      fieldsHtml = `<div class="service-fields-list">${fieldItems}</div>`;
    }

    return `
      <div class="service-card">
        <div class="service-card-header">
          <span class="service-name">${escapeHtml(s.name || s.service)}</span>
          <span class="service-id">${escapeHtml(s.serviceId)}</span>
        </div>
        <div class="service-desc">${escapeHtml(s.description || 'No description available')}</div>
        ${fieldsHtml}
      </div>
    `;
  }).join('');
}

function filterServices() {
  renderServices();
}

async function fetchTriggers() {
  try {
    const res = await fetch(`${apiBase}/triggers`);
    if (!res.ok) return;
    triggersCatalog = await res.json();
    const trigCountEl = document.getElementById('triggers-badge-count');
    const actCountEl = document.getElementById('active-triggers-badge-count');
    if (trigCountEl) trigCountEl.innerText = triggersCatalog.supportedTypes.length;
    if (actCountEl) actCountEl.innerText = triggersCatalog.totalActive;

    renderTriggers();
  } catch (err) {
    console.error('Failed to load triggers', err);
  }
}

function renderTriggers() {
  if (!triggersCatalog) return;

  // Render Supported Types
  const refContainer = document.getElementById('triggers-reference-container');
  if (refContainer) {
    refContainer.innerHTML = triggersCatalog.supportedTypes.map(t => `
      <div class="trigger-type-card">
        <div class="trigger-card-header">
          <span class="trigger-name">${escapeHtml(t.displayName)}</span>
          <span class="trigger-type-badge">${escapeHtml(t.type)}</span>
        </div>
        <div class="trigger-desc">${escapeHtml(t.description)}</div>
        <pre class="trigger-code-example"><code>${escapeHtml(t.exampleDsl)}</code></pre>
      </div>
    `).join('');
  }

  // Render Active Triggers
  const activeContainer = document.getElementById('active-triggers-container');
  if (activeContainer) {
    if (triggersCatalog.activeTriggers.length === 0) {
      activeContainer.innerHTML = `<div style="text-align: center; color: var(--text-dim); padding: 20px;">No active triggers running</div>`;
    } else {
      activeContainer.innerHTML = triggersCatalog.activeTriggers.map(at => `
        <div class="active-trigger-card">
          <div class="trigger-card-header">
            <span class="trigger-name">${escapeHtml(at.automationName)}</span>
            <span class="trigger-type-badge">${escapeHtml(at.triggerType)}</span>
          </div>
          <div class="trigger-desc">⚡ ${escapeHtml(at.description)}</div>
        </div>
      `).join('');
    }
  }
}

