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

// ============================ DOM helpers ============================

/**
 * Create an element.
 *
 * @param {string} tag
 * @param {object} [attrs] attributes/event handlers (on* keys are events)
 * @param {...(Node|string)} children
 */
export function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs)) {
    if (value === null || value === undefined || value === false) {
      continue;
    }
    if (key.startsWith('on') && typeof value === 'function') {
      node.addEventListener(key.substring(2).toLowerCase(), value);
    } else if (key === 'class') {
      node.className = value;
    } else if (key === 'style') {
      // Set through the CSS object, not setAttribute: the web app's
      // Content-Security-Policy (style-src 'self', no 'unsafe-inline')
      // blocks style attributes applied that way.
      node.style.cssText = value;
    } else if (key === 'dataset') {
      Object.assign(node.dataset, value);
    } else if (key === 'text') {
      node.textContent = value;
    } else if (key === 'html') {
      node.innerHTML = value; // only ever used for trusted static markup
    } else if (value === true) {
      node.setAttribute(key, '');
    } else {
      node.setAttribute(key, String(value));
    }
  }
  for (const child of children.flat(Infinity)) {
    if (child === null || child === undefined || child === false) {
      continue;
    }
    node.append(child.nodeType ? child : document.createTextNode(String(child)));
  }
  return node;
}

export function clear(node) {
  while (node.firstChild) {
    node.removeChild(node.firstChild);
  }
  return node;
}

// ============================ Formatting ===============================

export function formatBytes(n) {
  if (n === null || n === undefined || isNaN(n)) return '-';
  const abs = Math.abs(n);
  if (abs < 1024) return n + ' B';
  if (abs < 1024 * 1024) return (n / 1024).toFixed(1) + ' KiB';
  if (abs < 1024 * 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + ' MiB';
  return (n / 1024 / 1024 / 1024).toFixed(2) + ' GiB';
}

export function formatRate(n) {
  if (n === null || n === undefined || isNaN(n)) return '-';
  return n.toFixed(1) + '/s';
}

export function formatMs(n) {
  if (n === null || n === undefined || isNaN(n)) return '-';
  if (n < 1000) return Math.round(n) + ' ms';
  return (n / 1000).toFixed(2) + ' s';
}

export function formatSeconds(n) {
  if (n === null || n === undefined || isNaN(n)) return '-';
  if (n < 60) return Math.round(n) + ' s';
  if (n < 3600) return Math.floor(n / 60) + ' min ' + Math.round(n % 60) + ' s';
  return Math.floor(n / 3600) + ' h ' + Math.floor((n % 3600) / 60) + ' min';
}

export function formatDuration(ms) {
  if (ms === null || ms === undefined || isNaN(ms)) return '-';
  const s = Math.floor(ms / 1000);
  const d = Math.floor(s / 86400);
  const h = Math.floor((s % 86400) / 3600);
  const m = Math.floor((s % 3600) / 60);
  if (d > 0) return d + ' d ' + h + ' h';
  if (h > 0) return h + ' h ' + m + ' min';
  if (m > 0) return m + ' min';
  return Math.max(0, s) + ' s';
}

export function formatTimestamp(ts) {
  if (!ts) return '-';
  return new Date(ts).toLocaleString();
}

export function formatTime(ts) {
  if (!ts) return '-';
  return new Date(ts).toLocaleTimeString();
}

// ============================ State badges =============================

export function stateBadge(state) {
  // The context state reported by the API is "STARTED" (or "STOPPED");
  // "RUNNABLE" is accepted for compatibility with callers that normalize
  // the available flag themselves.
  const running = state === 'RUNNABLE' || state === 'STARTED' || state === 'AVAILABLE';
  return el('span', { class: 'badge ' + (running ? 'ok' : 'stop') }, running ? 'Running' : 'Stopped');
}

// ============================ Toasts ===================================

let toastTimer = 0;

export function toast(message, type = 'info', timeout = 5000) {
  const region = document.getElementById('toast-region');
  const node = el('div', { class: 'toast ' + type },
      el('div', { class: 'toast-msg' }, message));
  region.append(node);
  setTimeout(() => {
    node.remove();
  }, timeout);
}

// ============================ Modal / confirm ==========================

/**
 * Show a modal. Returns a close function.
 *
 * @param {object} opts { title, content (node or fn), actions (array of
 *          {label, class, onClick}), wide (boolean) }
 */
export function modal({ title, content, actions = [], wide = false, onClose = null }) {
  const root = document.getElementById('modal-root');
  const close = () => {
    backdrop.remove();
    document.removeEventListener('keydown', onKey, true);
    if (onClose) onClose();
  };
  const onKey = (e) => {
    if (e.key === 'Escape') close();
  };

  const actionRow = el('div', { class: 'modal-actions' },
      actions.map((a) => el('button', {
        type: 'button',
        class: 'btn ' + (a.class || ''),
        onclick: () => {
          if (a.onClick) a.onClick(close);
          else close();
        },
      }, a.label)));

  const body = typeof content === 'function' ? content() : content;
  const box = el('div', { class: 'modal' + (wide ? ' wide' : ''), role: 'dialog', 'aria-modal': 'true' },
      el('h2', {}, title),
      body,
      actionRow);

  const backdrop = el('div', { class: 'modal-backdrop', onclick: (e) => {
    if (e.target === backdrop) close();
  } }, box);
  root.append(backdrop);
  document.addEventListener('keydown', onKey, true);
  const firstButton = box.querySelector('button, input, select, textarea');
  if (firstButton) firstButton.focus();
  return close;
}

/**
 * Confirmation dialog. Resolves with true/false.
 *
 * @param {object} opts { title, message (string|node), confirmLabel,
 *          danger (boolean), requireText (string - user must type it) }
 */
export function confirm(opts) {
  return new Promise((resolve) => {
    let done = false;
    const finish = (value) => {
      if (done) return;
      done = true;
      close();
      resolve(value);
    };
    const content = el('div', {},
        typeof opts.message === 'string'
            ? el('p', { style: 'margin-top:0;color:var(--text-soft);' }, opts.message)
            : opts.message);
    let input = null;
    if (opts.requireText) {
      input = el('div', { class: 'field', style: 'margin-top:16px;' },
          el('label', {}, 'Type ', el('code', {}, opts.requireText), ' to confirm'),
          el('input', { type: 'text', autocomplete: 'off' }));
      content.append(input);
    }
    const close = modal({
      title: opts.title,
      content,
      actions: [
        { label: 'Cancel', onClick: () => finish(false) },
        {
          label: opts.confirmLabel || 'Confirm',
          class: opts.danger ? 'btn-danger' : 'btn-primary',
          onClick: () => {
            if (input) {
              const value = input.querySelector('input').value;
              if (value !== opts.requireText) {
                input.querySelector('input').focus();
                return;
              }
            }
            finish(true);
          },
        },
      ],
      // Dismissal via backdrop or Escape
      onClose: () => finish(false),
    });
  });
}

// ============================ Drawer ===================================

/**
 * Show a right-hand drawer. Returns a close function.
 */
export function drawer({ title, content, onClose = null }) {
  const close = () => {
    backdrop.remove();
    panel.remove();
    document.removeEventListener('keydown', onKey, true);
    if (onClose) onClose();
  };
  const onKey = (e) => {
    if (e.key === 'Escape') close();
  };

  const panel = el('aside', { class: 'drawer', role: 'dialog', 'aria-modal': 'true' },
      el('div', { class: 'drawer-head' },
          el('h3', {}, title),
          el('button', { type: 'button', class: 'icon-btn', 'aria-label': 'Close', onclick: close },
              el('span', { html: '&times;', style: 'font-size:20px;line-height:1;' }))),
      el('div', { class: 'drawer-body' }, content));

  const backdrop = el('div', { class: 'drawer-backdrop', onclick: close });
  document.body.append(backdrop, panel);
  document.addEventListener('keydown', onKey, true);
  const firstButton = panel.querySelector('button, input, select, textarea');
  if (firstButton) firstButton.focus();
  return close;
}

// ============================ Table ====================================

/**
 * Build a data table.
 *
 * @param {object} opts { columns: [{key, label, sortable, render, numeric}],
 *          rows: [object], sortKey, sortAsc, onSort(key), onRowClick(row),
 *          empty (string) }
 */
export function table(opts) {
  const { columns, rows, sortKey = null, sortAsc = true, onSort, onRowClick, empty = 'No data' } = opts;

  const thead = el('tr', {}, columns.map((c) => {
    const label = c.sortable
        ? c.label + (sortKey === c.key ? (sortAsc ? ' \u25B2' : ' \u25BC') : '')
        : c.label;
    return el('th', {
      class: (c.sortable ? 'sortable' : '') + (c.numeric ? ' num' : ''),
      role: c.sortable ? 'button' : null,
      onclick: c.sortable && onSort ? () => onSort(c.key) : null,
    }, label);
  }));

  const tbody = el('tbody', {}, rows.length === 0
      ? el('tr', {}, el('td', { colspan: columns.length, class: 'empty' }, empty))
      : rows.map((row) => el('tr', {
        onclick: onRowClick ? () => onRowClick(row) : null,
        style: onRowClick ? 'cursor:pointer' : null,
      }, columns.map((c) => {
        const value = c.render ? c.render(row) : row[c.key];
        const node = el('td', { class: c.numeric ? 'num' : (c.muted ? 'muted' : '') });
        if (value === null || value === undefined) {
          node.append(document.createTextNode('-'));
        } else if (value.nodeType) {
          node.append(value);
        } else {
          node.append(document.createTextNode(String(value)));
        }
        return node;
      }))));

  return el('div', { class: 'table-wrap' },
      el('table', { class: 'data' }, el('thead', {}, thead), tbody));
}

// ============================ Icons ====================================

const ICONS = {
  dashboard: 'M3 3h8v8H3V3Zm10 0h8v5h-8V3Zm0 7h8v11h-8V10ZM3 13h8v8H3v-8Z',
  apps: 'M4 4h7v7H4V4Zm9 0h7v7h-7V4ZM4 13h7v7H4v-7Zm9 3.5a3.5 3.5 0 1 0 7 0 3.5 3.5 0 0 0-7 0Z',
  hosts: 'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20Zm0 2c1.9 0 3.7.6 5.2 1.6-.3 1.9-1.1 3.6-2.3 4.9-1.9-.3-3.8-.3-5.6 0-1.3-1.3-2.1-3-2.4-4.9A9.9 9.9 0 0 1 12 4Zm-7.7 4.4c.7 2.7 2.1 5 4 6.7-.3 1.9-.9 3.6-1.8 5.1A8 8 0 0 1 4.3 8.4Zm7.7 12.1c-.7 0-1.4-.1-2.1-.2.9-1.6 1.6-3.4 1.9-5.3 1.3-.2 2.7-.2 4 0 .3 1.9 1 3.7 1.9 5.3-.7.1-1.4.2-2.1.2h-3.6Zm7.7-1.8c-.9-1.5-1.5-3.2-1.8-5.1 1.9-1.7 3.3-4 4-6.7a8 8 0 0 1-2.2 11.8Z',
  monitoring: 'M3 13h4l3-8 4 14 3-8h4v2h-2.5l-4.5 10-4-14-2 8H3v-2Z',
  diagnostics: 'M10.5 2h3a1 1 0 0 1 1 .9l.2 2.1 2 .7 1.5-1.6a1 1 0 0 1 1.3-.2l2.1 1.5a1 1 0 0 1 .3 1.3l-1.6 1.6.7 2 2.1.2a1 1 0 0 1 .9 1v3a1 1 0 0 1-.9 1l-2.1.2-.7 2 1.6 1.6a1 1 0 0 1 .2 1.3l-1.5 2.1a1 1 0 0 1-1.3.2l-1.6-1.6-2 .7-.2 2.1a1 1 0 0 1-1 .9h-3a1 1 0 0 1-1-.9l-.2-2.1-2-.7-1.5 1.6a1 1 0 0 1-1.3.2l-2.1-1.5a1 1 0 0 1-.3-1.3l1.6-1.6-.7-2-2.1-.2a1 1 0 0 1-.9-1v-3a1 1 0 0 1 .9-1l2.1-.2.7-2-1.6-1.6a1 1 0 0 1-.2-1.3l1.5-2.1a1 1 0 0 1 1.3-.2l1.6 1.6 2-.7.2-2.1a1 1 0 0 1 1-.9Zm1.5 6a4 4 0 1 0 0 8 4 4 0 0 0 0-8Z',
  sun: 'M12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10Zm0-5h.01L13 4h-2l1-2Zm0 20h.01L13 20h-2l1 2ZM2 12l2 1v-2L2 12Zm20 0v.01L22 12l-2 1v-2l2 1ZM4.9 4.9 6.3 6.3 4.9 4.9Zm14.2 14.2-1.4-1.4 1.4 1.4ZM4.9 19.1l1.4-1.4-1.4 1.4ZM19.1 4.9l-1.4 1.4 1.4-1.4Z',
  moon: 'M12 3a9 9 0 1 0 9 9c0-.5 0-1-.1-1.4A5.4 5.4 0 0 1 12 3Z',
  logout: 'M16 13v-2H7V8l-5 4 5 4v-3h9Zm3-10H11a2 2 0 0 0-2 2v3h2V5h8v14h-8v-3H9v3a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V5a2 2 0 0 0-2-2Z',
  upload: 'M12 3 4 9h5v6h6V9h5l-8-6Zm-8 16h16v2H4v-2Z',
  play: 'M8 5v14l11-7L8 5Z',
  stop: 'M6 6h12v12H6V6Z',
  reload: 'M17.65 6.35A8 8 0 1 0 19.7 14h-2.1a6 6 0 1 1-1.4-6.2L13 11h7V4l-2.35 2.35Z',
  trash: 'M6 7h12l-1 14H7L6 7Zm3-4h6l1 2h4v2H4V5h4l1-2Z',
  plus: 'M11 5h2v6h6v2h-6v6h-2v-6H5v-2h6V5Z',
  logs: 'M4 4h16v2.5H4V4Zm0 4.75h16v2.5H4v-2.5ZM4 13.5h10v2.5H4v-2.5Zm0 4.75h16v2.5H4v-2.5Z',
  'access-log': 'M3 5h3.5v3H3V5Zm5.5 0H21v3H8.5V5ZM3 10.5h3.5v3H3v-3Zm5.5 0H21v3H8.5v-3ZM3 16h3.5v3H3v-3Zm5.5 0H21v3H8.5v-3Z',
  users: 'M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z',
  close: 'M6.4 5 5 6.4 10.6 12 5 17.6 6.4 19 12 13.4 17.6 19 19 17.6 13.4 12 19 6.4 17.6 5 12 10.6 6.4 5Z',
  config: 'M4 4h7v7H4V4Zm9 0h7v7h-7V4ZM4 13h7v7H4v-7Zm9 0h3v3h3v-3h3v3h-3v3h3v3h-3v-3h-3v3h-3v-3h3v-3h-3Z',
};

export function icon(name, size = 18) {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('width', String(size));
  svg.setAttribute('height', String(size));
  svg.setAttribute('aria-hidden', 'true');
  const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  path.setAttribute('d', ICONS[name] || '');
  svg.append(path);
  return svg;
}

export function svgPath(name) {
  return ICONS[name] || '';
}
