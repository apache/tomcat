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

// Logging configuration panel of the Logs page: the conf/logging.properties
// editor (save with backup, apply to the running server) and the live logger
// table of the server class loader contexts with quick level changes.

import { api } from './api.js';
import { el, clear, table, toast, confirm } from './ui.js';
import { t } from './i18n.js';

const LEVEL_NAMES = ['OFF', 'SEVERE', 'WARNING', 'INFO', 'CONFIG', 'FINE', 'FINER', 'FINEST', 'ALL'];

export async function logConfigPanel(container) {
  const card = el('div', { class: 'card', style: 'margin-top:18px;' });
  container.append(card);

  const editor = el('textarea', { rows: '16', spellcheck: 'false', style: 'width:100%;' });
  const fileMissing = el('div', { class: 'muted', style: 'display:none;' },
      t('manager2.ui.logconfig.fileMissing'));
  const saveBtn = el('button', { type: 'button', class: 'btn btn-sm btn-primary' },
      t('manager2.ui.logconfig.save'));
  const applyBtn = el('button', { type: 'button', class: 'btn btn-sm' },
      t('manager2.ui.logconfig.apply'));
  const reloadBtn = el('button', { type: 'button', class: 'btn btn-sm' },
      t('manager2.ui.common.refresh'));
  const noteLine = el('div', { class: 'muted', style: 'margin:6px 2px 0;display:none;' });
  const statusLine = el('div', { class: 'muted', style: 'margin:6px 2px 0;' });

  const contextSelect = el('select', {});
  const loggerHolder = el('div', {});

  const state = { contexts: [], context: null };

  card.append(el('h2', {}, t('manager2.ui.logconfig.title')),
      el('p', { class: 'muted' }, t('manager2.ui.logconfig.subtitle')),
      fileMissing, editor,
      el('div', { class: 'log-btns', style: 'margin-top:8px;' }, saveBtn, applyBtn, reloadBtn),
      noteLine, statusLine,
      el('h3', { style: 'margin-top:var(--space-4);' }, t('manager2.ui.logconfig.loggers')),
      contextSelect, loggerHolder);

  contextSelect.addEventListener('change', () => { state.context = contextSelect.value; renderLoggers(); });
  reloadBtn.addEventListener('click', () => load());

  saveBtn.addEventListener('click', async () => {
    try {
      const res = await api('POST', '/api/logs/config/file', { text: editor.value });
      statusLine.textContent = res.message || '';
      toast(res.message || '', 'success');
    } catch (e) {
      toast(e.message || String(e), 'error');
    }
  });

  applyBtn.addEventListener('click', async () => {
    const ok = await confirm({
      title: t('manager2.ui.logconfig.apply'),
      message: t('manager2.ui.logconfig.applyConfirm'),
      confirmLabel: t('manager2.ui.logconfig.apply'),
    });
    if (!ok) return;
    try {
      const res = await api('POST', '/api/logs/config/apply', {});
      statusLine.textContent = res.message || '';
      toast(res.message || '', 'success');
      await loadLoggers();
    } catch (e) {
      toast(e.message || String(e), 'error');
    }
  });

  async function load() {
    let data;
    try {
      data = await api('GET', '/api/logs/config');
    } catch (e) {
      toast(e.message || String(e), 'error');
      return;
    }
    fileMissing.style.display = data.file && data.file.exists ? 'none' : 'block';
    editor.value = data.file ? data.file.text : '';
    noteLine.style.display = data.note ? 'block' : 'none';
    if (data.note) noteLine.textContent = data.note;
    editor.readOnly = false;
    state.contexts = data.contexts || [];
    if (!data.contexts.some((c) => c.id === state.context)) {
      state.context = state.contexts.length > 0 ? state.contexts[0].id : null;
    }
    clear(contextSelect);
    state.contexts.forEach((c) => contextSelect.append(
        el('option', { value: c.id }, t('manager2.ui.logconfig.context.' + c.id))));
    contextSelect.value = state.context || '';
    renderLoggers();
  }

  async function loadLoggers() {
    // Re-read only the live state after a quick level change: keep the
    // (possibly edited) file text in the editor.
    try {
      const data = await api('GET', '/api/logs/config');
      state.contexts = data.contexts || [];
      renderLoggers();
    } catch (e) {
      toast(e.message || String(e), 'error');
    }
  }

  function renderLoggers() {
    clear(loggerHolder);
    const context = state.contexts.find((c) => c.id === state.context);
    if (!context) return;
    const rows = context.loggers.map((l) => Object.assign({ contextId: context.id }, l));
    loggerHolder.append(table({
      columns: [
        { key: 'name', label: t('manager2.ui.logconfig.col.logger'), sortable: true,
          render: (r) => (r.name === '' ? '(' + t('manager2.ui.logconfig.root') + ')' : r.name) },
        { key: 'level', label: t('manager2.ui.logconfig.col.level'),
          render: (r) => levelEditor(r) },
        { key: 'effectiveLevel', label: t('manager2.ui.logconfig.col.effectiveLevel') },
        { key: 'handlers', label: t('manager2.ui.logconfig.col.handlers'), wide: true,
          render: (r) => handlerList(r) },
      ],
      rows,
      sortKey: 'name',
    }));
  }

  function levelEditor(row) {
    if (row.name === '') return el('span', { class: 'muted' }, row.level || '-');
    const options = [{ value: 'INHERIT', label: t('manager2.ui.logconfig.inherit') }]
        .concat(LEVEL_NAMES.map((n) => ({ value: n, label: n })));
    const select = el('select', {}, options.map((o) => el('option', { value: o.value }, o.label)));
    select.value = row.level || 'INHERIT';
    const setBtn = el('button', { type: 'button', class: 'btn btn-sm' },
        t('manager2.ui.logconfig.set'));
    setBtn.addEventListener('click', async () => {
      setBtn.disabled = true;
      try {
        const res = await api('POST', '/api/logs/config/level',
            { context: row.contextId, name: row.name, level: select.value });
        statusLine.textContent = res.message || '';
        await loadLoggers();
      } catch (e) {
        toast(e.message || String(e), 'error');
        setBtn.disabled = false;
      }
    });
    return el('div', { class: 'log-btns' }, select, setBtn);
  }

  function handlerList(row) {
    const handlers = row.handlers || [];
    if (handlers.length === 0) {
      return row.useParentHandlers ? el('span', { class: 'muted' }, '-') : el('span', {});
    }
    return el('span', { class: 'mono' },
        handlers.map((h) => simpleName(h.class) + ' [' + (h.level || 'ALL') + ']').join(', '));
  }

  await load();
}

function simpleName(className) {
  const pos = className.lastIndexOf('.');
  return pos >= 0 ? className.substring(pos + 1) : className;
}
