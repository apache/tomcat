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
import { t } from './i18n.js';

// Column definitions per field name. `render` receives the row and returns a
// node or a string.
const COLUMNS = {
  time: { labelKey: 'manager2.ui.logviewer.col.time' },
  level: {
    labelKey: 'manager2.ui.logviewer.col.level',
    render: (r) => levelBadge(r.level),
  },
  thread: { labelKey: 'manager2.ui.logviewer.col.thread' },
  source: { labelKey: 'manager2.ui.logviewer.col.source' },
  message: { labelKey: 'manager2.ui.logviewer.col.message', wide: true },
  raw: { labelKey: 'manager2.ui.logviewer.col.raw', wide: true },
  throwable: { labelKey: 'manager2.ui.logviewer.col.throwable', wide: true },
  method: { labelKey: 'manager2.ui.logviewer.col.method' },
  host: { labelKey: 'manager2.ui.col.host' },
  remoteAddr: { labelKey: 'manager2.ui.logviewer.col.remoteAddr' },
  localAddr: { labelKey: 'manager2.ui.logviewer.col.localAddr' },
  localServerName: { labelKey: 'manager2.ui.logviewer.col.server' },
  user: { labelKey: 'manager2.ui.logviewer.col.user' },
  path: { labelKey: 'manager2.ui.col.path' },
  query: { labelKey: 'manager2.ui.logviewer.col.query' },
  request: { labelKey: 'manager2.ui.logviewer.col.request', wide: true },
  protocol: { labelKey: 'manager2.ui.logviewer.col.protocol' },
  statusCode: {
    labelKey: 'manager2.ui.logviewer.col.status',
    numeric: true,
    render: (r) => statusBadge(r.statusCode),
  },
  size: {
    labelKey: 'manager2.ui.logviewer.col.size',
    numeric: true,
    render: (r) => formatBytes(r.size),
  },
  byteSentNC: {
    labelKey: 'manager2.ui.logviewer.col.sizeNoContentLength',
    numeric: true,
    render: (r) => formatBytes(r.byteSentNC),
  },
  elapsedTime: {
    labelKey: 'manager2.ui.logviewer.col.elapsed',
    numeric: true,
    render: (r) => formatMs(r.elapsedTime),
  },
  elapsedTimeS: {
    labelKey: 'manager2.ui.logviewer.col.elapsedSeconds',
    numeric: true,
  },
  firstByteTime: {
    labelKey: 'manager2.ui.logviewer.col.firstByte',
    numeric: true,
    render: (r) => formatMs(r.firstByteTime),
  },
  port: { labelKey: 'manager2.ui.logviewer.col.port', numeric: true },
  sessionId: { labelKey: 'manager2.ui.logviewer.col.session', mono: true },
  threadName: { labelKey: 'manager2.ui.logviewer.col.thread' },
  connectionStatus: { labelKey: 'manager2.ui.logviewer.col.connection' },
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
  const def = COLUMNS[key];
  return def && def.labelKey ? t(def.labelKey) : key;
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
  drawer({ title: t('manager2.ui.logviewer.recordTitle'), content: body });
}

// Build the column descriptors from the ordered field list the server sent.
function columnsFor(fields) {
  const cols = [];
  for (const key of fields) {
    // The raw request line duplicates the method / path / query / protocol
    // columns derived from it and is the widest column of the table; it
    // stays available in the record drawer and in the method filter.
    if (key === 'request') continue;
    const def = COLUMNS[key] || {};
    cols.push({
      key,
      label: labelFor(key),
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
      t('manager2.ui.common.refresh'));
  refreshBtn.addEventListener('click', () => { loadList(); });

  // Download the full, unfiltered raw file of the current selection. A
  // throw-away anchor keeps the SPA in place: the server answers with
  // Content-Disposition: attachment, so the browser saves the file instead
  // of navigating.
  const downloadBtn = el('button', { type: 'button', class: 'btn btn-sm' },
      t('manager2.ui.common.download'));
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
      el('label', {}, t('manager2.ui.logviewer.file')), fileSelect);
  const linesField = el('div', { class: 'field log-field' },
      el('label', {}, t('manager2.ui.logviewer.maxLines')), linesSelect);
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
        const options = [{ value: '', label: t('manager2.ui.logviewer.allSeverities') }]
            .concat(levels.map((l) => ({ value: l, label: l })));
        filterHolder.append(el('div', { class: 'field log-field' },
            el('label', {}, t('manager2.ui.logviewer.severity')),
            select(options, f.level || '', (v) => { state.filters.level = v; loadFile(); })));
      }
    } else {
      if (has(fields, 'method') || has(fields, 'request')) {
        const methods = (meta.methods || []).map((m) => m.name);
        filterHolder.append(el('div', { class: 'field log-field' },
            el('label', {}, t('manager2.ui.logviewer.col.method')),
            select([{ value: '', label: t('manager2.ui.common.all') }].concat(methods.map((m) => ({ value: m, label: m }))),
                f.method || '', (v) => { state.filters.method = v; loadFile(); })));
      }
      if (has(fields, 'statusCode')) {
        const cls = ['1xx', '2xx', '3xx', '4xx', '5xx'];
        const counts = meta.statusClasses || {};
        filterHolder.append(el('div', { class: 'field log-field' },
            el('label', {}, t('manager2.ui.logviewer.col.status')),
            select(
                [{ value: '', label: t('manager2.ui.common.all') }].concat(cls.map((c) => ({
                    value: c,
                    label: c + (counts[c] ? ' (' + counts[c] + ')' : ''),
                }))),
                f.status || '', (v) => { state.filters.status = v; loadFile(); })));
      }
      if (has(fields, 'user')) {
        filterHolder.append(el('div', { class: 'field log-field' },
            el('label', {}, t('manager2.ui.logviewer.col.user')),
            textInput(t('manager2.ui.logviewer.filterByUser'), (v) => { state.filters.user = v; loadFile(); }, f.user || '')));
      }
      if (has(fields, 'sessionId')) {
        filterHolder.append(el('div', { class: 'field log-field' },
            el('label', {}, t('manager2.ui.logviewer.sessionId')),
            textInput(t('manager2.ui.logviewer.filterBySessionId'), (v) => { state.filters.session = v; loadFile(); }, f.session || '')));
      }
    }
    // Free text search is available for both kinds.
    filterHolder.append(el('div', { class: 'field log-field log-field-search' },
        el('label', {}, t('manager2.ui.logviewer.search')),
        textInput(t('manager2.ui.logviewer.searchPlaceholder'), (v) => { state.filters.search = v; loadFile(); }, f.search || '')));
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
      fileSelect.append(el('option', { value: '' }, t('manager2.ui.logviewer.noFiles')));
      fileSelect.disabled = true;
      downloadBtn.disabled = true;
      clear(tableHolder);
      tableHolder.append(el('div', { class: 'empty' },
          t(kind === 'log' ? 'manager2.ui.logviewer.noLogFiles' : 'manager2.ui.logviewer.noAccessLogFiles')));
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
    tableHolder.append(el('div', { class: 'spinner', role: 'status', 'aria-label': t('manager2.ui.common.loading') }));

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
    let summary = data.matched === 1
        ? t('manager2.ui.logviewer.summarySingular', records.length, data.matched, data.total)
        : t('manager2.ui.logviewer.summaryPlural', records.length, data.matched, data.total);
    if (data.truncated) {
      summary += ' - ' + t('manager2.ui.logviewer.truncated',
          Math.round((data.readBytes / 1024 / 1024) * 10) / 10);
    }
    statusLine.textContent = summary;

    const cols = columnsFor(state.fields);
    if (records.length === 0) {
      clear(tableHolder);
      tableHolder.append(el('div', { class: 'empty' },
          t('manager2.ui.logviewer.noMatches')));
    } else {
      clear(tableHolder);
      const tbl = table({
        columns: cols,
        rows: records,
        onRowClick: (row) => showRecord(row),
        empty: t('manager2.ui.logviewer.noMatches'),
      });
      tbl.classList.add('log-table');
      tableHolder.append(tbl);
    }
    loading = false;
  }

  await loadList();
  return null;
}
