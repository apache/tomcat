/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

// Shared table + filter UI for the Logs and Access log pages. The set of
// filters that is shown depends on the fields the server reports for the
// selected file, so that only what the configured log format actually
// provides is filterable.

import { api, BASE } from './api.js';
import { el, clear, table, drawer, formatBytes, formatMs } from './ui.js';

// Column definitions per field name. `render` receives the row and returns a
// node or a string.
const COLUMNS = {
  time: { label: 'Time' },
  level: {
    label: 'Level',
    render: (r) => levelBadge(r.level),
  },
  thread: { label: 'Thread' },
  source: { label: 'Source' },
  message: { label: 'Message', wide: true },
  raw: { label: 'Line', wide: true },
  throwable: { label: 'Stack trace', wide: true },
  method: { label: 'Method' },
  host: { label: 'Host' },
  remoteAddr: { label: 'Remote addr' },
  localAddr: { label: 'Local addr' },
  localServerName: { label: 'Server' },
  user: { label: 'User' },
  path: { label: 'Path' },
  query: { label: 'Query' },
  request: { label: 'Request', wide: true },
  protocol: { label: 'Protocol' },
  statusCode: {
    label: 'Status',
    numeric: true,
    render: (r) => statusBadge(r.statusCode),
  },
  size: {
    label: 'Size',
    numeric: true,
    render: (r) => formatBytes(r.size),
  },
  byteSentNC: {
    label: 'Size (no C-L)',
    numeric: true,
    render: (r) => formatBytes(r.byteSentNC),
  },
  elapsedTime: {
    label: 'Elapsed',
    numeric: true,
    render: (r) => formatMs(r.elapsedTime),
  },
  elapsedTimeS: {
    label: 'Elapsed (s)',
    numeric: true,
  },
  firstByteTime: {
    label: 'First byte',
    numeric: true,
    render: (r) => formatMs(r.firstByteTime),
  },
  port: { label: 'Port', numeric: true },
  sessionId: { label: 'Session', mono: true },
  threadName: { label: 'Thread' },
  connectionStatus: { label: 'Connection' },
};

function levelBadge(level) {
  if (!level) return el('span', { class: 'muted' }, '-');
  const cls =
    level === 'SEVERE' ? 'danger'
    : level === 'WARNING' ? 'warn'
    : level === 'INFO' ? 'info'
    : 'plain';
  return el('span', { class: 'badge ' + cls }, level);
}

function statusBadge(status) {
  if (status === null || status === undefined || status === '') {
    return el('span', { class: 'muted' }, '-');
  }
  const n = Number(status);
  if (Number.isNaN(n)) return String(status);
  const cls =
    n >= 500 ? 'danger'
    : n >= 400 ? 'warn'
    : n >= 300 ? 'info'
    : 'ok';
  return el('span', { class: 'badge ' + cls }, String(status));
}

function labelFor(key) {
  return (COLUMNS[key] && COLUMNS[key].label) || key;
}

// Show all fields of a record in a drawer (full messages and stack traces).
function showRecord(row) {
  const body = el('div', {});
  for (const [key, value] of Object.entries(row)) {
    if (value === null || value === undefined) continue;
    if (key === 'logicalUserName') continue;
    if (key === 'throwable' || key === 'raw' || key === 'message') {
      body.append(
          el('h3', { style: 'margin:14px 0 8px;' }, labelFor(key)),
          el('pre', { class: 'block' }, Array.isArray(value) ? value.join('\n') : String(value)));
    } else {
      body.append(el('p', { style: 'margin:8px 0;' },
          el('strong', {}, labelFor(key) + ': '),
          el('code', {}, typeof value === 'object' ? JSON.stringify(value) : String(value))));
    }
  }
  drawer({ title: 'Record', content: body });
}

// Build the column descriptors from the ordered field list the server sent.
function columnsFor(fields) {
  const cols = [];
  for (const key of fields) {
    const def = COLUMNS[key] || { label: key };
    cols.push({
      key,
      label: def.label || key,
      numeric: def.numeric ? true : null,
      muted: !def.render && !def.numeric ? true : null,
      wide: def.wide ? true : null,
      render: def.render
        ? def.render
        : def.mono
          ? (r) => el('code', {}, r[key] === null || r[key] === undefined ? '-' : String(r[key]))
          : null,
    });
  }
  return cols;
}

function select(options, value, onChange) {
  const node = el('select', {}, options.map((o) =>
      el('option', { value: o.value }, o.label)));
  node.value = value;
  node.addEventListener('change', () => onChange(node.value));
  return node;
}

function textInput(placeholder, onInput, initial = '') {
  const node = el('input', { type: 'text', placeholder, autocomplete: 'off' });
  node.value = initial;
  let timer = 0;
  node.addEventListener('input', () => {
    clearTimeout(timer);
    timer = setTimeout(() => onInput(node.value.trim()), 300);
  });
  return node;
}

function has(fields, name) {
  return fields.includes(name);
}

/**
 * Render a log/access-log page.
 *
 * @param {object} container
 * @param {object} opts { kind: 'log'|'access', title, subtitle, listUrl,
 *          fileUrl }
 */
export async function logPage(container, opts) {
  const kind = opts.kind;

  const head = el('div', { class: 'page-head' },
      el('h1', {}, opts.title),
      el('p', {}, opts.subtitle));
  container.append(head);

  const controls = el('div', { class: 'card log-controls' });
  const statusLine = el('div', { class: 'muted log-status', style: 'margin:0 2px 10px;' });
  const tableHolder = el('div', { class: 'card', style: 'margin-top:0;' });
  container.append(controls, statusLine, tableHolder);

  const state = {
    files: [],
    file: null,
    lines: 500,
    filters: {},
    fields: [],
  };

  let loading = false;

  // ---- controls ----

  const fileSelect = el('select', {});
  const linesSelect = select(
      [500, 1000, 2500, 5000].map((n) => ({ value: String(n), label: String(n) })),
      '500',
      (v) => { state.lines = Number(v); loadFile(); });

  const refreshBtn = el('button', { type: 'button', class: 'btn btn-sm' },
      'Refresh');
  refreshBtn.addEventListener('click', () => { loadList(); });

  // Download the full, unfiltered raw file of the current selection. A
  // throw-away anchor keeps the SPA in place: the server answers with
  // Content-Disposition: attachment, so the browser saves the file instead
  // of navigating.
  const downloadBtn = el('button', { type: 'button', class: 'btn btn-sm' },
      'Download');
  downloadBtn.disabled = true;
  downloadBtn.addEventListener('click', () => {
    if (!state.file) return;
    const path = kind === 'log' ? '/api/logs/download' : '/api/access-log/download';
    const a = el('a', {
      href: BASE + path + '?name=' + encodeURIComponent(state.file),
      download: state.file,
    });
    document.body.append(a);
    a.click();
    a.remove();
  });

  const filterHolder = el('div', { class: 'log-filters' });

  const fileField = el('div', { class: 'field log-field' },
      el('label', {}, 'File'), fileSelect);
  const linesField = el('div', { class: 'field log-field' },
      el('label', {}, 'Max lines'), linesSelect);
  const refreshField = el('div', { class: 'field log-field log-field-btn' },
      el('label', {}, '\u00a0'),
      el('div', { class: 'log-btns' }, refreshBtn, downloadBtn));

  fileSelect.addEventListener('change', () => {
    state.file = fileSelect.value;
    state.filters = {};
    loadFile();
  });

  controls.append(fileField, linesField, filterHolder, refreshField);

  // Build the format dependent filters once we know the fields of the file.
  // The current filter values are kept across reloads; they are only reset
  // when the file changes.
  function buildFilters(fields, meta) {
    clear(filterHolder);
    const f = state.filters;
    if (fields.length === 0) return;

    if (kind === 'log') {
      // Severity filter from the levels present in the file.
      const levels = Object.keys(meta.levels || {});
      if (levels.length > 0) {
        const options = [{ value: '', label: 'All severities' }]
            .concat(levels.map((l) => ({ value: l, label: l })));
        filterHolder.append(el('div', { class: 'field log-field' },
            el('label', {}, 'Severity'),
            select(options, f.level || '', (v) => { state.filters.level = v; loadFile(); })));
      }
    } else {
      if (has(fields, 'method') || has(fields, 'request')) {
        const methods = (meta.methods || []).map((m) => m.name);
        filterHolder.append(el('div', { class: 'field log-field' },
            el('label', {}, 'Method'),
            select([{ value: '', label: 'All' }].concat(methods.map((m) => ({ value: m, label: m }))),
                f.method || '', (v) => { state.filters.method = v; loadFile(); })));
      }
      if (has(fields, 'statusCode')) {
        const cls = ['1xx', '2xx', '3xx', '4xx', '5xx'];
        const counts = meta.statusClasses || {};
        filterHolder.append(el('div', { class: 'field log-field' },
            el('label', {}, 'Status'),
            select(
                [{ value: '', label: 'All' }].concat(cls.map((c) => ({
                    value: c,
                    label: c + (counts[c] ? ' (' + counts[c] + ')' : ''),
                }))),
                f.status || '', (v) => { state.filters.status = v; loadFile(); })));
      }
      if (has(fields, 'user')) {
        filterHolder.append(el('div', { class: 'field log-field' },
            el('label', {}, 'User'),
            textInput('Filter by user', (v) => { state.filters.user = v; loadFile(); }, f.user || '')));
      }
      if (has(fields, 'sessionId')) {
        filterHolder.append(el('div', { class: 'field log-field' },
            el('label', {}, 'Session ID'),
            textInput('Filter by session ID', (v) => { state.filters.session = v; loadFile(); }, f.session || '')));
      }
    }
    // Free text search is available for both kinds.
    filterHolder.append(el('div', { class: 'field log-field log-field-search' },
        el('label', {}, 'Search'),
        textInput('Search', (v) => { state.filters.search = v; loadFile(); }, f.search || '')));
  }

  // ---- loading ----

  async function loadList() {
    let data;
    try {
      data = await api('GET', opts.listUrl);
    } catch (err) {
      clear(tableHolder);
      tableHolder.append(el('div', { class: 'empty' }, err.message));
      return;
    }
    state.files = data.logs || [];
    clear(fileSelect);
    if (state.files.length === 0) {
      fileSelect.append(el('option', { value: '' }, 'No log files found'));
      fileSelect.disabled = true;
      downloadBtn.disabled = true;
      clear(tableHolder);
      tableHolder.append(el('div', { class: 'empty' },
          'No ' + (kind === 'log' ? 'log' : 'access log') + ' files were found in the logs directory.'));
      statusLine.textContent = '';
      return;
    }
    fileSelect.disabled = false;
    for (const f of state.files) {
      const label = f.name + (f.format ? '  (' + f.format + ')' : '');
      fileSelect.append(el('option', { value: f.name }, label));
    }
    // Keep the selection if it still exists, otherwise the most recent.
    if (!state.files.some((f) => f.name === state.file)) {
      state.file = state.files[0].name;
    }
    fileSelect.value = state.file;
    downloadBtn.disabled = !state.file;
    loadFile();
  }

  async function loadFile() {
    if (!state.file || loading) return;
    loading = true;
    clear(tableHolder);
    tableHolder.append(el('div', { class: 'spinner', role: 'status', 'aria-label': 'Loading' }));

    const params = new URLSearchParams();
    params.set('name', state.file);
    params.set('lines', String(state.lines));
    for (const [k, v] of Object.entries(state.filters)) {
      if (v) params.set(k, v);
    }

    let data;
    try {
      data = await api('GET', opts.fileUrl + '?' + params.toString());
    } catch (err) {
      clear(tableHolder);
      tableHolder.append(el('div', { class: 'empty' }, err.message));
      loading = false;
      return;
    }

    // The identd field (%l, "User (identd)") is dead in practice: the value
    // is always "-", so it is dropped from the columns and the user filter.
    state.fields = (data.fields || []).filter((f) => f !== 'logicalUserName');
    buildFilters(state.fields, data);

    const records = data.records || [];
    let summary = 'Showing ' + records.length + ' of ' + data.matched +
        ' matching line' + (data.matched === 1 ? '' : 's') +
        ' (total ' + data.total + ')';
    if (data.truncated) {
      summary += ' - file truncated to the last ' + Math.round((data.readBytes / 1024 / 1024) * 10) / 10 + ' MiB';
    }
    statusLine.textContent = summary;

    const cols = columnsFor(state.fields);
    if (records.length === 0) {
      clear(tableHolder);
      tableHolder.append(el('div', { class: 'empty' },
          'No lines match the current filters.'));
    } else {
      clear(tableHolder);
      const t = table({
        columns: cols,
        rows: records,
        onRowClick: (row) => showRecord(row),
        empty: 'No lines match the current filters.',
      });
      t.classList.add('log-table');
      tableHolder.append(t);
    }
    loading = false;
  }

  await loadList();
  return null;
}
