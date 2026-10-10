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
import { t } from '../i18n.js';

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
          el('h1', {}, t('manager2.ui.nav.apps')),
          el('p', {}, t('manager2.ui.apps.subtitle')),
          el('span', { class: 'head-spacer' }),
          el('button', { type: 'button', class: 'btn btn-primary', onclick: () => deployModal() },
              t('manager2.ui.apps.deployButton'))));
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
          key: 'path', label: t('manager2.ui.col.path'),
          render: (a) => el('a', {
            href: BASE + appUrl(a.host, a.path),
            onclick: (e) => {
              e.preventDefault();
              window.history.pushState({}, '', BASE + appUrl(a.host, a.path));
              window.dispatchEvent(new PopStateEvent('popstate'));
            },
          }, a.path === '' ? '/' : a.path),
        },
        { key: 'displayName', label: t('manager2.ui.apps.displayName'), muted: true },
        { key: 'version', label: t('manager2.ui.col.version'), muted: true, render: (a) => a.version || '-' },
        { key: 'state', label: t('manager2.ui.col.state'), render: (a) => stateBadge(a.available ? 'RUNNABLE' : 'STOPPED') },
        { key: 'sessions', label: t('manager2.ui.col.sessions'), numeric: true },
        {
          key: 'docBase', label: t('manager2.ui.apps.docBase'), muted: true,
          render: (a) => el('code', {}, a.docBase || '-'),
        },
        {
          key: 'actions', label: t('manager2.ui.col.actions'),
          render: (a) => actionMenu([
              a.available
                  ? { label: t('manager2.ui.common.stop'), onclick: () => lifecycle(a, 'stop') }
                  : { label: t('manager2.ui.common.start'), class: 'btn-primary', onclick: () => lifecycle(a, 'start') },
              { label: t('manager2.ui.common.reload'), disabled: !a.available, onclick: () => lifecycle(a, 'reload') },
              {
                label: t('manager2.ui.apps.undeploy'), class: 'btn-danger', disabled: a.self,
                title: a.self ? t('manager2.ui.apps.cannotUndeploySelf') : t('manager2.ui.apps.undeploy'),
                onclick: () => undeploy(a),
              },
          ]),
        }],
      rows: data.apps,
      onRowClick: (a) => {
        window.history.pushState({}, '', BASE + appUrl(a.host, a.path));
        window.dispatchEvent(new PopStateEvent('popstate'));
      },
      empty: t('manager2.ui.apps.noApps'),
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
        title: action === 'stop' ? t('manager2.ui.apps.stopConfirmTitle') : t('manager2.ui.apps.reloadConfirmTitle'),
        message: t('manager2.ui.apps.stopOrReloadConfirm', app.path === '' ? '/' : app.path),
        confirmLabel: action === 'stop' ? t('manager2.ui.common.stop') : t('manager2.ui.common.reload'),
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
    title: t('manager2.ui.apps.undeployConfirmTitle'),
    message: t('manager2.ui.apps.undeployConfirm', app.path === '' ? '/' : app.path),
    confirmLabel: t('manager2.ui.apps.undeploy'),
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
          el('label', {}, t('manager2.ui.apps.warFile')),
          el('input', { type: 'file', accept: '.war', id: 'deploy-war' })),
      el('div', { class: 'form-grid' },
          el('div', { class: 'field' },
              el('label', {}, t('manager2.ui.apps.contextPathOptional')),
              el('input', { type: 'text', id: 'deploy-path', placeholder: '/myapp' }),
              el('span', { class: 'hint' }, t('manager2.ui.apps.contextPathHint'))),
          el('div', { class: 'field' },
              el('label', {}, t('manager2.ui.apps.versionOptional')),
              el('input', { type: 'text', id: 'deploy-version' }))));

  const serverPane = el('div', { style: 'display:none' },
      el('div', { class: 'form-grid' },
          el('div', { class: 'field' },
              el('label', {}, t('manager2.ui.apps.contextPath')),
              el('input', { type: 'text', id: 'srv-path', placeholder: '/myapp' })),
          el('div', { class: 'field' },
              el('label', {}, t('manager2.ui.apps.versionOptional')),
              el('input', { type: 'text', id: 'srv-version' })),
          el('div', { class: 'field span-2' },
              el('label', {}, t('manager2.ui.apps.xmlConfigOptional')),
              el('input', { type: 'text', id: 'srv-config', placeholder: 'http://.../context.xml' })),
          el('div', { class: 'field span-2' },
              el('label', {}, t('manager2.ui.apps.warLocationOptional')),
              el('input', { type: 'text', id: 'srv-war', placeholder: 'file:///.../myapp.war' })),
          el('div', { class: 'field span-2' },
              el('label', {}, ''),
              el('label', { class: 'check' },
                  el('input', { type: 'checkbox', id: 'srv-replace' }),
                  t('manager2.ui.apps.replaceExisting')))));

  const tabs = el('div', { class: 'tabs' },
      el('button', { type: 'button', class: 'tab active', onclick: () => switchTab('upload') }, t('manager2.ui.apps.tabUpload')),
      el('button', { type: 'button', class: 'tab', onclick: () => switchTab('server') }, t('manager2.ui.apps.tabServer')));

  function switchTab(name) {
    tab = name;
    tabs.querySelectorAll('.tab').forEach((tab, i) => {
      tab.classList.toggle('active', (i === 0) === (name === 'upload'));
    });
    uploadPane.style.display = name === 'upload' ? '' : 'none';
    serverPane.style.display = name === 'server' ? '' : 'none';
  }

  const progress = el('div', { class: 'progress', style: 'display:none' }, el('div'));
  let busy = false;

  const close = modal({
    title: t('manager2.ui.apps.deployTitle'),
    wide: true,
    content: el('div', {},
        tabs,
        uploadPane,
        serverPane,
        progress),
    actions: [
      { label: t('manager2.ui.common.cancel') },
      {
        label: t('manager2.ui.apps.deploy'),
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
                toast(t('manager2.ui.apps.selectWarFirst'), 'warn');
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
        const err = new Error((data && data.message) || t('manager2.ui.apps.uploadFailedStatus', xhr.status));
        err.status = xhr.status;
        reject(err);
      }
    });
    xhr.addEventListener('error', () => reject(new Error(t('manager2.ui.apps.uploadNetworkError'))));
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
          } }, t('manager2.ui.nav.apps')),
          ' / ',
          document.createTextNode(contextPath === '' ? '/' : contextPath)));

  const head = el('div', { class: 'page-head' },
      el('h1', { id: 'app-title' }, contextPath === '' ? '/' : contextPath),
      el('span', { id: 'app-state' }));
  view.append(head);

  const tabs = el('div', { class: 'tabs' },
      el('button', { type: 'button', class: 'tab active' }, t('manager2.ui.apps.tabOverview')),
      el('button', { type: 'button', class: 'tab' }, t('manager2.ui.apps.tabSessions')),
      el('button', { type: 'button', class: 'tab' }, t('manager2.ui.apps.tabMetrics')));
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
    tabButtons.forEach((tab, i) => tab.classList.toggle('active', i === index));
    paneNodes.forEach((p, i) => { p.style.display = i === index ? '' : 'none'; });
    if (!loaded[index]) {
      loaded[index] = true;
      if (index === 0) loadOverview();
      else if (index === 1) loadSessions();
      else loadMetrics();
    }
  }
  tabButtons.forEach((tab, i) => tab.addEventListener('click', () => switchTab(i)));

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
      pane.append(el('div', { class: 'empty' }, t('manager2.ui.apps.notFound')));
      return;
    }
    appInfo = app;

    const available = app.available;
    const stateEl = document.getElementById('app-state');
    clear(stateEl);
    stateEl.append(stateBadge(available ? 'RUNNABLE' : 'STOPPED'));

    const actions = el('div', { class: 'row-actions', style: 'margin-bottom:16px;' },
        available
            ? el('button', { type: 'button', class: 'btn', onclick: () => lifecycle(app, 'stop') }, t('manager2.ui.common.stop'))
            : el('button', { type: 'button', class: 'btn btn-primary', onclick: () => lifecycle(app, 'start') }, t('manager2.ui.common.start')),
        el('button', { type: 'button', class: 'btn', disabled: !available, onclick: () => lifecycle(app, 'reload') }, t('manager2.ui.common.reload')),
        el('button', {
          type: 'button', class: 'btn btn-danger', disabled: app.self,
          title: app.self ? t('manager2.ui.apps.cannotUndeploySelf') : t('manager2.ui.apps.undeploy'),
          onclick: () => undeploy(app),
        }, t('manager2.ui.apps.undeploy')));

    const dl = el('dl', { class: 'kv' },
        kv(t('manager2.ui.col.host'), host),
        kv(t('manager2.ui.col.path'), contextPath === '' ? '/' : contextPath),
        kv(t('manager2.ui.apps.displayName'), app.displayName || '-'),
        kv(t('manager2.ui.col.version'), app.version || '-'),
        kv(t('manager2.ui.apps.docBase'), el('code', {}, app.docBase || '-')),
        kv(t('manager2.ui.apps.sessionTimeout'), app.sessionTimeout != null ? t('manager2.ui.apps.minutes', app.sessionTimeout) : '-'),
        kv(t('manager2.ui.dashboard.kpi.activeSessions'), String(app.sessions)));

    pane.append(el('div', { class: 'card' },
        el('h3', {}, t('manager2.ui.apps.details')),
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
          type: 'number', id: 'expire-idle', min: '0', placeholder: t('manager2.ui.apps.idleSecondsPlaceholder'),
          class: 'expire-idle-input',
        }),
        el('button', {
          type: 'button', class: 'btn btn-sm',
          onclick: async () => {
            const idle = parseInt(document.getElementById('expire-idle').value, 10);
            if (isNaN(idle) || idle < 0) {
              toast(t('manager2.ui.apps.idleTimeoutNeeded'), 'warn');
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
        }, t('manager2.ui.apps.expireIdle')),
        el('span', { class: 'head-spacer' }),
        el('button', {
          type: 'button', class: 'btn btn-sm btn-danger',
          onclick: async () => {
            const selected = Array.from(pane.querySelectorAll('input.session-check:checked'))
                .map((c) => c.dataset.id);
            if (selected.length === 0) {
              toast(t('manager2.ui.apps.selectSessions'), 'warn');
              return;
            }
            const ok = await confirm({
              title: t('manager2.ui.apps.invalidateSessionsTitle'),
              message: t('manager2.ui.apps.invalidateSessionsConfirm', selected.length),
              confirmLabel: t('manager2.ui.apps.invalidate'),
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
        }, t('manager2.ui.apps.invalidateSelected')));
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
          key: 'id', label: t('manager2.ui.apps.colId'), sortable: true,
          render: (s) => el('code', {}, s.id),
        },
        {
          key: 'user', label: t('manager2.ui.col.user'), sortable: true,
          render: (s) => s.user || '-',
        },
        {
          key: 'creationTime', label: t('manager2.ui.apps.colCreated'), sortable: true,
          render: (s) => formatTimestamp(s.creationTime),
        },
        {
          key: 'lastAccessedTime', label: t('manager2.ui.apps.colLastAccessed'), sortable: true,
          render: (s) => formatTimestamp(s.lastAccessedTime),
        },
        {
          key: 'maxInactiveInterval', label: t('manager2.ui.apps.colTimeout'), sortable: true, numeric: true,
        },
        {
          key: 'active', label: t('manager2.ui.col.state'),
          render: (s) => el('span', { class: 'badge ' + (s.active ? 'ok' : 'stop') },
              s.active ? t('manager2.ui.apps.sessionActive') : t('manager2.ui.apps.sessionProxy')),
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
        empty: t('manager2.ui.apps.noSessions'),
        stackable: true,
      }));
  }

  function sessionDrawer(session) {
    const body = el('div', {},
        el('dl', { class: 'kv', style: 'margin-bottom:20px;' },
            el('dt', {}, t('manager2.ui.apps.colId')), el('dd', {}, el('code', {}, session.id)),
            el('dt', {}, t('manager2.ui.col.user')), el('dd', {}, session.user || '-'),
            el('dt', {}, t('manager2.ui.apps.colLocale')), el('dd', {}, session.locale || '-'),
            el('dt', {}, t('manager2.ui.apps.colCreated')), el('dd', {}, formatTimestamp(session.creationTime)),
            el('dt', {}, t('manager2.ui.apps.colLastAccessed')), el('dd', {}, formatTimestamp(session.lastAccessedTime)),
            el('dt', {}, t('manager2.ui.apps.timeoutLabel')), el('dd', {}, t('manager2.ui.apps.seconds', session.maxInactiveInterval))));

    const actions = el('div', { class: 'row-actions', style: 'margin-bottom:16px;' },
        el('button', {
          type: 'button', class: 'btn btn-sm btn-danger',
          onclick: async () => {
            const ok = await confirm({
              title: t('manager2.ui.apps.invalidateSessionTitle'),
              message: t('manager2.ui.apps.invalidateSessionConfirm', session.id),
              confirmLabel: t('manager2.ui.apps.invalidate'),
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
        }, t('manager2.ui.apps.invalidateSession')));
    body.append(actions, el('h3', {}, t('manager2.ui.apps.attributes')), el('div', { id: 'attr-holder' }));

    const close = drawer({ title: t('manager2.ui.apps.sessionTitle', session.id), content: body });

    api('GET', '/api/apps/' + seg + '/sessions/' + encodeURIComponent(session.id) + query)
        .then((detail) => {
          const holder = body.querySelector('#attr-holder');
          clear(holder);
          if (!detail.attributes || detail.attributes.length === 0) {
            holder.append(el('div', { class: 'empty' }, t('manager2.ui.apps.noAttributes')));
            return;
          }
          holder.append(el('div', { class: 'table-wrap' },
              el('table', { class: 'data' },
                  el('thead', {}, el('tr', {},
                      el('th', {}, t('manager2.ui.col.name')),
                      el('th', {}, t('manager2.ui.apps.colClass')),
                      el('th', {}, t('manager2.ui.apps.colValue')),
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
                                title: t('manager2.ui.apps.removeAttributeTitle'),
                                message: t('manager2.ui.apps.removeAttributeConfirm', a.name),
                                confirmLabel: t('manager2.ui.common.remove'),
                                danger: true,
                              });
                              if (!ok) return;
                              try {
                                await api('DELETE', '/api/apps/' + seg + '/sessions/' +
                                    encodeURIComponent(session.id) + '/attributes/' +
                                    encodeURIComponent(a.name) + query);
                                toast(t('manager2.ui.apps.attributeRemoved'), 'ok');
                                close();
                                loadSessions();
                              } catch (err) {
                                toast(err.message, 'error');
                              }
                            },
                          }, t('manager2.ui.common.remove')))))))));
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
        kpi(t('manager2.ui.dashboard.kpi.activeSessions'), m.activeSessions != null ? String(m.activeSessions) : '-'),
        kpi(t('manager2.ui.apps.expiredSessions'), m.expiredSessions != null ? String(m.expiredSessions) : '-'),
        kpi(t('manager2.ui.apps.avgSessionLifetime'), m.sessionAverageAliveTime != null ?
            formatDuration(m.sessionAverageAliveTime) : '-'),
        kpi(t('manager2.ui.apps.maxSessionLife'), m.sessionMaxAliveTime != null ?
            formatDuration(m.sessionMaxAliveTime) : '-')));

    if (data.jsp) {
      pane.append(el('div', { class: 'card' },
          el('h3', {}, t('manager2.ui.apps.jsps')),
          el('dl', { class: 'kv' },
              el('dt', {}, t('manager2.ui.apps.jspFiles')), el('dd', {}, String(data.jsp.jspCount)),
              el('dt', {}, t('manager2.ui.apps.reloads')), el('dd', {}, String(data.jsp.jspReloadCount)))));
    }

    pane.append(el('div', { class: 'card' },
        el('h3', {}, t('manager2.ui.apps.servlets')),
        el('div', { class: 'table-wrap stackable' },
            el('table', { class: 'data' },
                el('thead', {}, el('tr', {},
                    el('th', {}, t('manager2.ui.col.name')),
                    el('th', {}, t('manager2.ui.apps.mappings')),
                    el('th', { class: 'num' }, t('manager2.ui.col.requests')),
                    el('th', { class: 'num' }, t('manager2.ui.col.errors')),
                    el('th', { class: 'num' }, t('manager2.ui.monitoring.col.processingTime')),
                    el('th', { class: 'num' }, t('manager2.ui.apps.colMaxTime')))),
                el('tbody', {}, (data.wrappers || []).map((w) => el('tr', {},
                    el('td', { 'data-label': t('manager2.ui.col.name') }, el('code', {}, w.name)),
                    el('td', { class: 'muted', 'data-label': t('manager2.ui.apps.mappings') }, (w.mappings || []).join(', ')),
                    el('td', { class: 'num', 'data-label': t('manager2.ui.col.requests') }, String(w.requestCount)),
                    el('td', { class: 'num', 'data-label': t('manager2.ui.col.errors') }, String(w.errorCount)),
                    el('td', { class: 'num', 'data-label': t('manager2.ui.monitoring.col.processingTime') }, formatDuration(w.processingTime)),
                    el('td', { class: 'num', 'data-label': t('manager2.ui.apps.colMaxTime') }, formatDuration(w.maxTime)))))))));
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
