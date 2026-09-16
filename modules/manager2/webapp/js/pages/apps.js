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

import { BASE, api, getCsrfToken, setCsrfToken } from '../api.js';
import { el, clear, table, stateBadge, toast, modal, confirm, drawer, actionMenu,
    formatTimestamp, formatDuration } from '../ui.js';

/**
 * The segment used in URLs to refer to a context path.
 * "" (ROOT) becomes "root"; "/docs" becomes "docs".
 */
function toSegment(contextPath) {
  return contextPath === '' ? 'root' : encodeURIComponent(contextPath.replace(/^\//, '').replace(/\/$/, ''));
}

function fromSegment(segment) {
  const s = decodeURIComponent(segment);
  return s === 'root' ? '' : '/' + s;
}

function appUrl(host, contextPath) {
  return '/apps/' + host + '/' + toSegment(contextPath);
}

// ============================ Apps list ================================

export async function apps(container) {
  const view = el('div', {},
      el('div', { class: 'page-head' },
          el('h1', {}, 'Applications'),
          el('p', {}, 'Deploy, start, stop, reload and undeploy web applications.'),
          el('span', { class: 'head-spacer' }),
          el('button', { type: 'button', class: 'btn btn-primary', onclick: () => deployModal() },
              'Deploy application')));
  container.append(view);

  const wrap = el('div', { class: 'card' });
  view.append(wrap);

  async function load() {
    let data;
    try {
      data = await api('GET', '/api/apps');
    } catch (err) {
      wrap.append(el('div', { class: 'empty' }, err.message));
      return;
    }
    clear(wrap);
    wrap.append(table({
      columns: [
        {
          key: 'path', label: 'Path',
          render: (a) => el('a', {
            href: BASE + appUrl(a.host, a.path),
            onclick: (e) => {
              e.preventDefault();
              window.history.pushState({}, '', BASE + appUrl(a.host, a.path));
              window.dispatchEvent(new PopStateEvent('popstate'));
            },
          }, a.path === '' ? '/' : a.path),
        },
        { key: 'displayName', label: 'Display name', muted: true },
        { key: 'version', label: 'Version', muted: true, render: (a) => a.version || '-' },
        { key: 'state', label: 'State', render: (a) => stateBadge(a.available ? 'RUNNABLE' : 'STOPPED') },
        { key: 'sessions', label: 'Sessions', numeric: true },
        {
          key: 'docBase', label: 'Doc base', muted: true,
          render: (a) => el('code', {}, a.docBase || '-'),
        },
        {
          key: 'actions', label: 'Actions',
          render: (a) => actionMenu([
              a.available
                  ? { label: 'Stop', onclick: () => lifecycle(a, 'stop') }
                  : { label: 'Start', class: 'btn-primary', onclick: () => lifecycle(a, 'start') },
              { label: 'Reload', disabled: !a.available, onclick: () => lifecycle(a, 'reload') },
              {
                label: 'Undeploy', class: 'btn-danger', disabled: a.self,
                title: a.self ? 'Cannot undeploy the manager itself' : 'Undeploy',
                onclick: () => undeploy(a),
              },
          ]),
        }],
      rows: data.apps,
      onRowClick: (a) => {
        window.history.pushState({}, '', BASE + appUrl(a.host, a.path));
        window.dispatchEvent(new PopStateEvent('popstate'));
      },
      empty: 'No applications deployed',
      stackable: true,
    }));
  }

  await load();
  return null;
}

async function lifecycle(app, action) {
  const ok = action === 'start'
      ? true
      : await confirm({
        title: action === 'stop' ? 'Stop application' : 'Reload application',
        message: 'Stop or reload ' + (app.path === '' ? '/' : app.path) + '?',
        confirmLabel: action === 'stop' ? 'Stop' : 'Reload',
        danger: false,
      });
  if (!ok) return;
  try {
    const res = await api('POST', '/api/apps/' + toSegment(app.path) + '/' + action +
        '?path=' + encodeURIComponent(app.path) + '&version=' + encodeURIComponent(app.version || ''));
    toast(res.message, 'ok');
    window.dispatchEvent(new PopStateEvent('popstate'));
  } catch (err) {
    toast(err.message, 'error');
  }
}

async function undeploy(app) {
  const ok = await confirm({
    title: 'Undeploy application',
    message: 'Undeploy ' + (app.path === '' ? '/' : app.path) + '? The deployed files are kept on disk.',
    confirmLabel: 'Undeploy',
    danger: true,
    requireText: app.path === '' ? '/' : app.path,
  });
  if (!ok) return;
  try {
    const res = await api('DELETE', '/api/apps/' + toSegment(app.path) +
        '?path=' + encodeURIComponent(app.path) + '&version=' + encodeURIComponent(app.version || ''));
    toast(res.message, 'ok');
    window.dispatchEvent(new PopStateEvent('popstate'));
  } catch (err) {
    toast(err.message, 'error');
  }
}

// ============================ Deploy modal =============================

function deployModal() {
  let tab = 'upload';

  const uploadPane = el('div', {},
      el('div', { class: 'field' },
          el('label', {}, 'WAR file'),
          el('input', { type: 'file', accept: '.war', id: 'deploy-war' })),
      el('div', { class: 'form-grid' },
          el('div', { class: 'field' },
              el('label', {}, 'Context path (optional)'),
              el('input', { type: 'text', id: 'deploy-path', placeholder: '/myapp' }),
              el('span', { class: 'hint' }, 'Defaults to the WAR file name.')),
          el('div', { class: 'field' },
              el('label', {}, 'Version (optional)'),
              el('input', { type: 'text', id: 'deploy-version' }))));

  const serverPane = el('div', { style: 'display:none' },
      el('div', { class: 'form-grid' },
          el('div', { class: 'field' },
              el('label', {}, 'Context path'),
              el('input', { type: 'text', id: 'srv-path', placeholder: '/myapp' })),
          el('div', { class: 'field' },
              el('label', {}, 'Version (optional)'),
              el('input', { type: 'text', id: 'srv-version' })),
          el('div', { class: 'field span-2' },
              el('label', {}, 'XML configuration (optional)'),
              el('input', { type: 'text', id: 'srv-config', placeholder: 'http://.../context.xml' })),
          el('div', { class: 'field span-2' },
              el('label', {}, 'WAR location (optional)'),
              el('input', { type: 'text', id: 'srv-war', placeholder: 'file:///.../myapp.war' })),
          el('div', { class: 'field span-2' },
              el('label', {}, ''),
              el('label', { class: 'check' },
                  el('input', { type: 'checkbox', id: 'srv-replace' }),
                  'Replace an existing deployment'))));

  const tabs = el('div', { class: 'tabs' },
      el('button', { type: 'button', class: 'tab active', onclick: () => switchTab('upload') }, 'Upload'),
      el('button', { type: 'button', class: 'tab', onclick: () => switchTab('server') }, 'From server'));

  function switchTab(name) {
    tab = name;
    tabs.querySelectorAll('.tab').forEach((t, i) => {
      t.classList.toggle('active', (i === 0) === (name === 'upload'));
    });
    uploadPane.style.display = name === 'upload' ? '' : 'none';
    serverPane.style.display = name === 'server' ? '' : 'none';
  }

  const progress = el('div', { class: 'progress', style: 'display:none' }, el('div'));
  let busy = false;

  const close = modal({
    title: 'Deploy application',
    wide: true,
    content: el('div', {},
        tabs,
        uploadPane,
        serverPane,
        progress),
    actions: [
      { label: 'Cancel' },
      {
        label: 'Deploy',
        class: 'btn-primary',
        onClick: async (c) => {
          if (busy) return;
          busy = true;
          try {
            let message;
            if (tab === 'upload') {
              const fileInput = document.getElementById('deploy-war');
              const file = fileInput.files[0];
              if (!file) {
                toast('Select a WAR file first.', 'warn');
                return;
              }
              const fd = new FormData();
              fd.append('war', file);
              const pathValue = document.getElementById('deploy-path').value.trim();
              if (pathValue) fd.append('path', pathValue);
              const version = document.getElementById('deploy-version').value.trim();
              if (version) fd.append('version', version);
              progress.style.display = '';
              progress.firstChild.style.width = '0%';
              message = await uploadWithProgress('/api/apps/upload', fd, (p) => {
                progress.firstChild.style.width = p + '%';
              });
            } else {
              const body = {};
              const pathValue = document.getElementById('srv-path').value.trim();
              if (pathValue) body.path = pathValue;
              const version = document.getElementById('srv-version').value.trim();
              if (version) body.version = version;
              const config = document.getElementById('srv-config').value.trim();
              if (config) body.config = config;
              const war = document.getElementById('srv-war').value.trim();
              if (war) body.war = war;
              if (document.getElementById('srv-replace').checked) body.replace = true;
              const res = await api('POST', '/api/apps/deploy', body);
              message = res.message;
            }
            toast(message, 'ok');
            c();
            window.dispatchEvent(new PopStateEvent('popstate'));
          } catch (err) {
            toast(err.message, 'error');
          } finally {
            busy = false;
            progress.style.display = 'none';
          }
        },
      },
    ],
  });
}

function uploadWithProgress(path, formData, onProgress) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', BASE + path);
    xhr.withCredentials = true;
    xhr.setRequestHeader('X-CSRF-Token', getCsrfToken());
    xhr.upload.addEventListener('progress', (e) => {
      if (e.lengthComputable) {
        onProgress(Math.round(e.loaded / e.total * 100));
      }
    });
    xhr.addEventListener('load', () => {
      const token = xhr.getResponseHeader('X-CSRF-Token');
      if (token) setCsrfToken(token);
      let data = null;
      try {
        data = JSON.parse(xhr.responseText);
      } catch (e) {
        // not JSON
      }
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(data ? data.message : xhr.responseText);
      } else {
        const err = new Error((data && data.message) || ('Upload failed with status ' + xhr.status));
        err.status = xhr.status;
        reject(err);
      }
    });
    xhr.addEventListener('error', () => reject(new Error('Upload failed (network error)')));
    xhr.send(formData);
  });
}

// ============================ App detail ===============================

export async function appDetail(container, params) {
  const host = params.host;
  const contextPath = fromSegment(params.path);
  const seg = toSegment(contextPath);
  const query = '?path=' + encodeURIComponent(contextPath);

  const view = el('div', {},
      el('div', { class: 'breadcrumb' },
          el('a', { href: BASE + '/apps', onclick: (e) => {
            e.preventDefault();
            window.history.pushState({}, '', BASE + '/apps');
            window.dispatchEvent(new PopStateEvent('popstate'));
          } }, 'Applications'),
          ' / ',
          document.createTextNode(contextPath === '' ? '/' : contextPath)));

  const head = el('div', { class: 'page-head' },
      el('h1', { id: 'app-title' }, contextPath === '' ? '/' : contextPath),
      el('span', { id: 'app-state' }));
  view.append(head);

  const tabs = el('div', { class: 'tabs' },
      el('button', { type: 'button', class: 'tab active' }, 'Overview'),
      el('button', { type: 'button', class: 'tab' }, 'Sessions'),
      el('button', { type: 'button', class: 'tab' }, 'Metrics'));
  const panes = el('div', {},
      el('div', { class: 'pane' }, el('div', { class: 'spinner' })),
      el('div', { class: 'pane', style: 'display:none' }),
      el('div', { class: 'pane', style: 'display:none' }));
  view.append(tabs, panes);
  container.append(view);

  const tabButtons = tabs.querySelectorAll('.tab');
  const paneNodes = panes.querySelectorAll('.pane');
  let loaded = [false, false, false];

  function switchTab(index) {
    tabButtons.forEach((t, i) => t.classList.toggle('active', i === index));
    paneNodes.forEach((p, i) => { p.style.display = i === index ? '' : 'none'; });
    if (!loaded[index]) {
      loaded[index] = true;
      if (index === 0) loadOverview();
      else if (index === 1) loadSessions();
      else loadMetrics();
    }
  }
  tabButtons.forEach((t, i) => t.addEventListener('click', () => switchTab(i)));

  // ---------------- Overview ----------------
  let appInfo = null;
  async function loadOverview() {
    const pane = paneNodes[0];
    clear(pane);
    let data;
    try {
      data = await api('GET', '/api/apps' + query);
    } catch (err) {
      pane.append(el('div', { class: 'empty' }, err.message));
      return;
    }
    const app = data.apps.find((a) => a.host === host && a.path === contextPath);
    if (!app) {
      pane.append(el('div', { class: 'empty' }, 'Application not found.'));
      return;
    }
    appInfo = app;

    const available = app.available;
    const stateEl = document.getElementById('app-state');
    clear(stateEl);
    stateEl.append(stateBadge(available ? 'RUNNABLE' : 'STOPPED'));

    const actions = el('div', { class: 'row-actions', style: 'margin-bottom:16px;' },
        available
            ? el('button', { type: 'button', class: 'btn', onclick: () => lifecycle(app, 'stop') }, 'Stop')
            : el('button', { type: 'button', class: 'btn btn-primary', onclick: () => lifecycle(app, 'start') }, 'Start'),
        el('button', { type: 'button', class: 'btn', disabled: !available, onclick: () => lifecycle(app, 'reload') }, 'Reload'),
        el('button', {
          type: 'button', class: 'btn btn-danger', disabled: app.self,
          title: app.self ? 'Cannot undeploy the manager itself' : 'Undeploy',
          onclick: () => undeploy(app),
        }, 'Undeploy'));

    const dl = el('dl', { class: 'kv' },
        kv('Host', host),
        kv('Path', contextPath === '' ? '/' : contextPath),
        kv('Display name', app.displayName || '-'),
        kv('Version', app.version || '-'),
        kv('Doc base', el('code', {}, app.docBase || '-')),
        kv('Session timeout', app.sessionTimeout != null ? app.sessionTimeout + ' min' : '-'),
        kv('Active sessions', String(app.sessions)));

    pane.append(el('div', { class: 'card' },
        el('h3', {}, 'Details'),
        actions,
        dl));
  }

  function kv(label, value) {
    return [
      el('dt', {}, label),
      el('dd', {}, typeof value === 'object' ? value : document.createTextNode(String(value))),
    ];
  }

  // ---------------- Sessions ----------------
  async function loadSessions() {
    const pane = paneNodes[1];
    clear(pane);

    const controls = el('div', { class: 'row-actions', style: 'margin-bottom:12px;' },
        el('input', {
          type: 'number', id: 'expire-idle', min: '0', placeholder: 'idle seconds',
          class: 'expire-idle-input',
        }),
        el('button', {
          type: 'button', class: 'btn btn-sm',
          onclick: async () => {
            const idle = parseInt(document.getElementById('expire-idle').value, 10);
            if (isNaN(idle) || idle < 0) {
              toast('Enter an idle timeout in seconds.', 'warn');
              return;
            }
            try {
              const res = await api('POST', '/api/apps/' + seg + '/expire' + query, { idle });
              toast(res.message, 'ok');
              loadSessions();
            } catch (err) {
              toast(err.message, 'error');
            }
          },
        }, 'Expire idle'),
        el('span', { class: 'head-spacer' }),
        el('button', {
          type: 'button', class: 'btn btn-sm btn-danger',
          onclick: async () => {
            const selected = Array.from(pane.querySelectorAll('input.session-check:checked'))
                .map((c) => c.dataset.id);
            if (selected.length === 0) {
              toast('Select sessions to invalidate.', 'warn');
              return;
            }
            const ok = await confirm({
              title: 'Invalidate sessions',
              message: 'Invalidate ' + selected.length + ' session(s)?',
              confirmLabel: 'Invalidate',
              danger: true,
            });
            if (!ok) return;
            try {
              const res = await api('POST', '/api/apps/' + seg + '/sessions/invalidate' + query, { ids: selected });
              toast(res.message, 'ok');
              loadSessions();
            } catch (err) {
              toast(err.message, 'error');
            }
          },
        }, 'Invalidate selected'));
    pane.append(controls, el('div', { id: 'sessions-table' }));

    let sort = 'lastAccessedTime';
    let asc = false;
    await loadSessionTable(sort, asc);
  }

  async function loadSessionTable(sortKey, sortAsc) {
    const holder = document.getElementById('sessions-table');
    let url = '/api/apps/' + seg + '/sessions' + query;
    if (sortKey) {
      url += '&sort=' + encodeURIComponent(sortKey) + '&order=' + (sortAsc ? 'ASC' : 'DESC');
    }
    let data;
    try {
      data = await api('GET', url);
    } catch (err) {
      clear(holder);
      holder.append(el('div', { class: 'empty' }, err.message));
      return;
    }
    clear(holder);
    holder.append(table({
      columns: [
        {
          key: 'check', label: '',
          render: (s) => el('input', {
            type: 'checkbox', class: 'session-check', 'data-id': s.id,
            style: 'accent-color:var(--accent);',
          }),
        },
        {
          key: 'id', label: 'Id', sortable: true,
          render: (s) => el('code', {}, s.id),
        },
        {
          key: 'user', label: 'User', sortable: true,
          render: (s) => s.user || '-',
        },
        {
          key: 'creationTime', label: 'Created', sortable: true,
          render: (s) => formatTimestamp(s.creationTime),
        },
        {
          key: 'lastAccessedTime', label: 'Last accessed', sortable: true,
          render: (s) => formatTimestamp(s.lastAccessedTime),
        },
        {
          key: 'maxInactiveInterval', label: 'Timeout (s)', sortable: true, numeric: true,
        },
        {
          key: 'active', label: 'State',
          render: (s) => el('span', { class: 'badge ' + (s.active ? 'ok' : 'stop') },
              s.active ? 'active' : 'proxy'),
        },
      ],
        rows: data.sessions,
        sortKey: data.sort || sortKey,
        sortAsc: data.order === 'ASC',
        onSort: (key) => {
          if (sortKey === key) {
            asc = !asc;
          } else {
            sortKey = key;
            asc = true;
          }
          loadSessionTable(sortKey, asc);
        },
        onRowClick: (s) => sessionDrawer(s),
        empty: 'No sessions',
        stackable: true,
      }));
  }

  function sessionDrawer(session) {
    const body = el('div', {},
        el('dl', { class: 'kv', style: 'margin-bottom:20px;' },
            el('dt', {}, 'Id'), el('dd', {}, el('code', {}, session.id)),
            el('dt', {}, 'User'), el('dd', {}, session.user || '-'),
            el('dt', {}, 'Locale'), el('dd', {}, session.locale || '-'),
            el('dt', {}, 'Created'), el('dd', {}, formatTimestamp(session.creationTime)),
            el('dt', {}, 'Last accessed'), el('dd', {}, formatTimestamp(session.lastAccessedTime)),
            el('dt', {}, 'Timeout'), el('dd', {}, session.maxInactiveInterval + ' s')));

    const actions = el('div', { class: 'row-actions', style: 'margin-bottom:16px;' },
        el('button', {
          type: 'button', class: 'btn btn-sm btn-danger',
          onclick: async () => {
            const ok = await confirm({
              title: 'Invalidate session',
              message: 'Invalidate session ' + session.id + '?',
              confirmLabel: 'Invalidate',
              danger: true,
            });
            if (!ok) return;
            try {
              const res = await api('POST', '/api/apps/' + seg + '/sessions/invalidate' + query,
                  { ids: [session.id] });
              toast(res.message, 'ok');
              close();
              loadSessions();
            } catch (err) {
              toast(err.message, 'error');
            }
          },
        }, 'Invalidate session'));
    body.append(actions, el('h3', {}, 'Attributes'), el('div', { id: 'attr-holder' }));

    const close = drawer({ title: 'Session ' + session.id, content: body });

    api('GET', '/api/apps/' + seg + '/sessions/' + encodeURIComponent(session.id) + query)
        .then((detail) => {
          const holder = body.querySelector('#attr-holder');
          clear(holder);
          if (!detail.attributes || detail.attributes.length === 0) {
            holder.append(el('div', { class: 'empty' }, 'No attributes'));
            return;
          }
          holder.append(el('div', { class: 'table-wrap' },
              el('table', { class: 'data' },
                  el('thead', {}, el('tr', {},
                      el('th', {}, 'Name'),
                      el('th', {}, 'Class'),
                      el('th', {}, 'Value'),
                      el('th', {}))),
                  el('tbody', {}, detail.attributes.map((a) => el('tr', {},
                      el('td', {}, el('code', {}, a.name)),
                      el('td', { class: 'muted' }, el('code', {}, a.class || '-')),
                      el('td', {}, el('code', {}, a.value || '-')),
                      el('td', {},
                          el('button', {
                            type: 'button', class: 'btn btn-sm btn-danger',
                            onclick: async () => {
                              const ok = await confirm({
                                title: 'Remove attribute',
                                message: 'Remove attribute ' + a.name + '?',
                                confirmLabel: 'Remove',
                                danger: true,
                              });
                              if (!ok) return;
                              try {
                                await api('DELETE', '/api/apps/' + seg + '/sessions/' +
                                    encodeURIComponent(session.id) + '/attributes/' +
                                    encodeURIComponent(a.name) + query);
                                toast('Attribute removed.', 'ok');
                                close();
                                loadSessions();
                              } catch (err) {
                                toast(err.message, 'error');
                              }
                            },
                          }, 'Remove'))))))));
        })
        .catch((err) => {
          const holder = body.querySelector('#attr-holder');
          clear(holder);
          holder.append(el('div', { class: 'empty' }, err.message));
        });
  }

  // ---------------- Metrics ----------------
  async function loadMetrics() {
    const pane = paneNodes[2];
    clear(pane);
    let data;
    try {
      data = await api('GET', '/api/status/apps/' + seg + '/' + host + query);
    } catch (err) {
      pane.append(el('div', { class: 'empty' }, err.message));
      return;
    }

    const m = data.manager || {};
    pane.append(el('div', { class: 'grid kpis' },
        kpi('Active sessions', m.activeSessions != null ? String(m.activeSessions) : '-'),
        kpi('Expired sessions', m.expiredSessions != null ? String(m.expiredSessions) : '-'),
        kpi('Avg session lifetime', m.sessionAverageAliveTime != null ?
            formatDuration(m.sessionAverageAliveTime) : '-'),
        kpi('Max session life', m.sessionMaxAliveTime != null ?
            formatDuration(m.sessionMaxAliveTime) : '-')));

    if (data.jsp) {
      pane.append(el('div', { class: 'card' },
          el('h3', {}, 'JSPs'),
          el('dl', { class: 'kv' },
              el('dt', {}, 'JSP files'), el('dd', {}, String(data.jsp.jspCount)),
              el('dt', {}, 'Reloads'), el('dd', {}, String(data.jsp.jspReloadCount)))));
    }

    pane.append(el('div', { class: 'card' },
        el('h3', {}, 'Servlets'),
        el('div', { class: 'table-wrap stackable' },
            el('table', { class: 'data' },
                el('thead', {}, el('tr', {},
                    el('th', {}, 'Name'),
                    el('th', {}, 'Mappings'),
                    el('th', { class: 'num' }, 'Requests'),
                    el('th', { class: 'num' }, 'Errors'),
                    el('th', { class: 'num' }, 'Processing time'),
                    el('th', { class: 'num' }, 'Max time'))),
                el('tbody', {}, (data.wrappers || []).map((w) => el('tr', {},
                    el('td', { 'data-label': 'Name' }, el('code', {}, w.name)),
                    el('td', { class: 'muted', 'data-label': 'Mappings' }, (w.mappings || []).join(', ')),
                    el('td', { class: 'num', 'data-label': 'Requests' }, String(w.requestCount)),
                    el('td', { class: 'num', 'data-label': 'Errors' }, String(w.errorCount)),
                    el('td', { class: 'num', 'data-label': 'Processing time' }, formatDuration(w.processingTime)),
                    el('td', { class: 'num', 'data-label': 'Max time' }, formatDuration(w.maxTime)))))))));
  }

  function kpi(label, value) {
    return el('div', { class: 'card kpi' },
        el('span', { class: 'kpi-label' }, label),
        el('span', { class: 'kpi-value' }, value));
  }

  loaded[0] = true;
  await loadOverview();
  return null;
}
