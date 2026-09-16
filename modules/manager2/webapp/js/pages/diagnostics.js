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

import { api } from '../api.js';
import { el, clear, toast, confirm } from '../ui.js';

export async function diagnostics(container) {
  const view = el('div', {},
      el('div', { class: 'page-head' },
          el('h1', {}, 'Diagnostics'),
          el('p', {}, 'SSL, memory leaks, JNDI resources and JVM diagnostics.')));
  container.append(view);

  const tabs = el('div', { class: 'tabs' },
      el('button', { type: 'button', class: 'tab active' }, 'SSL'),
      el('button', { type: 'button', class: 'tab' }, 'Memory leaks'),
      el('button', { type: 'button', class: 'tab' }, 'JNDI resources'),
      el('button', { type: 'button', class: 'tab' }, 'JVM'));
  const panes = el('div', {},
      el('div', { class: 'pane' }),
      el('div', { class: 'pane', style: 'display:none' }),
      el('div', { class: 'pane', style: 'display:none' }),
      el('div', { class: 'pane', style: 'display:none' }));
  view.append(tabs, panes);

  const tabButtons = tabs.querySelectorAll('.tab');
  const paneNodes = panes.querySelectorAll('.pane');
  const loaders = [loadSsl, loadLeaks, loadResources, loadJvm];
  let loaded = [false, false, false, false];

  function switchTab(index) {
    tabButtons.forEach((t, i) => t.classList.toggle('active', i === index));
    paneNodes.forEach((p, i) => { p.style.display = i === index ? '' : 'none'; });
    if (!loaded[index]) {
      loaded[index] = true;
      loaders[index]();
    }
  }
  tabButtons.forEach((t, i) => t.addEventListener('click', () => switchTab(i)));

  // ---------------- SSL ----------------
  async function loadSsl() {
    const pane = paneNodes[0];
    const reloadRow = el('div', { class: 'row-actions', style: 'margin-bottom:14px;' },
        el('input', {
          type: 'text', placeholder: 'TLS SNI host name (optional)', class: 'tls-host', id: 'tls-host',
        }),
        el('button', {
          type: 'button', class: 'btn btn-sm',
          onclick: async () => {
            const ok = await confirm({
              title: 'Reload SSL',
              message: 'Reload the SSL context? Connections in flight are interrupted.',
              confirmLabel: 'Reload',
            });
            if (!ok) return;
            const body = {};
            const host = document.getElementById('tls-host').value.trim();
            if (host) body.tlsHostName = host;
            try {
              const res = await api('POST', '/api/ssl/reload', body);
              toast(res.message, 'ok');
            } catch (err) {
              toast(err.message, 'error');
            }
          },
        }, 'Reload SSL context'));
    const list = el('div');
    pane.append(reloadRow, list);

    const subTabs = el('div', { class: 'tabs' },
        el('button', { type: 'button', class: 'tab active' }, 'Cipher suites'),
        el('button', { type: 'button', class: 'tab' }, 'Certificates'),
        el('button', { type: 'button', class: 'tab' }, 'Trusted certificates'));
    list.append(subTabs, el('div', { id: 'ssl-content' }));

    let current = 'ciphers';
    const subButtons = subTabs.querySelectorAll('.tab');
    const urls = { ciphers: '/api/ssl/ciphers', certs: '/api/ssl/certs', trusted: '/api/ssl/trusted' };
    subButtons.forEach((btn, i) => btn.addEventListener('click', () => {
      subButtons.forEach((b, j) => b.classList.toggle('active', i === j));
      current = Object.keys(urls)[i];
      loadCurrent();
    }));

    async function loadCurrent() {
      const holder = list.querySelector('#ssl-content');
      clear(holder);
      let data;
      try {
        data = await api('GET', urls[current]);
      } catch (err) {
        holder.append(el('div', { class: 'empty' }, err.message));
        return;
      }
      const entries = Object.entries(data);
      if (entries.length === 0) {
        holder.append(el('div', { class: 'empty' },
            'No SSL connector configured on this server.'));
        return;
      }
      for (const [connector, values] of entries) {
        holder.append(el('div', { class: 'card' },
            el('h3', {}, connector),
            el('div', { class: 'table-wrap' },
                el('table', { class: 'data' },
                    el('tbody', {}, values.map((v) => el('tr', {},
                        el('td', {}, el('code', {}, v)))))))));
      }
    }
    await loadCurrent();
  }

  // ---------------- Memory leaks ----------------
  function loadLeaks() {
    const pane = paneNodes[1];
    const holder = el('div', { style: 'margin-top:14px;' });
    pane.append(el('div', { class: 'card' },
        el('p', { style: 'color:var(--text-soft);margin-top:0;' },
            'Check whether the deployed web applications hold any memory-leaking references '
            + '(class loaders, threads or file handles).'),
        el('div', { class: 'row-actions' },
            el('button', {
              type: 'button', class: 'btn',
              onclick: async (e) => {
                const btn = e.currentTarget;
                btn.disabled = true;
                clear(holder);
                holder.append(document.createTextNode('Checking… this can take a while.'));
                try {
                  const data = await api('GET', '/api/leaks');
                  clear(holder);
                  if (!data.leaks || data.leaks.length === 0) {
                    holder.append(el('div', { class: 'empty' }, 'No leaks found.'));
                  } else {
                    holder.append(el('pre', { class: 'block' }, data.leaks.join('\n')));
                  }
                } catch (err) {
                  clear(holder);
                  holder.append(el('div', { class: 'empty' }, err.message));
                } finally {
                  btn.disabled = false;
                }
              },
            }, 'Check for leaks')),
        holder));
  }

  // ---------------- JNDI resources ----------------
  async function loadResources() {
    const pane = paneNodes[2];
    pane.append(el('div', { class: 'card' },
        el('div', { class: 'row-actions', style: 'margin-bottom:14px;' },
            el('select', { id: 'res-type', class: 'res-type' },
                el('option', { value: '' }, 'All types'),
                el('option', { value: 'env/java:comp/env' }, 'env/java:comp/env'),
                el('option', { value: 'env/ejb' }, 'env/ejb'),
                el('option', { value: 'env/jndi/kerberos' }, 'env/jndi/kerberos')),
            el('button', {
              type: 'button', class: 'btn btn-sm',
              onclick: () => loadResTable(),
            }, 'Refresh')),
        el('div', { id: 'resources-table' })));

    async function loadResTable() {
      const holder = pane.querySelector('#resources-table');
      const type = document.getElementById('res-type').value;
      let data;
      try {
        data = await api('GET', '/api/resources' + (type ? '?type=' + encodeURIComponent(type) : ''));
      } catch (err) {
        clear(holder);
        holder.append(el('div', { class: 'empty' }, err.message));
        return;
      }
      clear(holder);
      const lines = (data.resources || '').split('\n').filter((l) => l.trim());
      if (lines.length === 0) {
        holder.append(el('div', { class: 'empty' }, 'No resources found.'));
        return;
      }
      holder.append(el('div', { class: 'table-wrap' },
          el('table', { class: 'data' },
              el('thead', {}, el('tr', {}, el('th', {}, 'Name'), el('th', {}, 'Class'))),
              el('tbody', {}, lines.map((line) => {
                const idx = line.indexOf(':');
                const name = idx >= 0 ? line.substring(0, idx).trim() : line;
                const cls = idx >= 0 ? line.substring(idx + 1).trim() : '';
                return el('tr', {},
                    el('td', {}, el('code', {}, name)),
                    el('td', { class: 'muted' }, el('code', {}, cls || '-')));
              })))));
    }
    await loadResTable();
  }

  // ---------------- JVM ----------------
  function loadJvm() {
    const pane = paneNodes[3];
    const holder = el('div');
    pane.append(el('div', { class: 'card' },
        el('p', { style: 'color:var(--text-soft);margin-top:0;' },
            'VM information and a thread dump of this process.'),
        el('div', { class: 'row-actions' },
            el('button', {
              type: 'button', class: 'btn',
              onclick: (e) => show('info', e.currentTarget),
            }, 'VM information'),
            el('button', {
              type: 'button', class: 'btn',
              onclick: (e) => show('threaddump', e.currentTarget),
            }, 'Thread dump')),
        holder));

    async function show(which, btn) {
      const title = which === 'info' ? 'VM information' : 'Thread dump';
      const url = which === 'info' ? '/api/diagnostics/vminfo' : '/api/diagnostics/threaddump';
      let section = holder.querySelector('#jvm-' + which);
      let body;
      if (section) {
        body = section.querySelector('.jvm-body');
      } else {
        body = el('div', { class: 'jvm-body' });
        section = el('div', { id: 'jvm-' + which, style: 'margin-top:14px;' },
            el('h3', {}, title), body);
        holder.append(section);
      }
      clear(body);
      body.append(el('div', { class: 'spinner' }));
      btn.disabled = true;
      try {
        const data = await api('GET', url);
        const text = which === 'info' ? data.info : data.dump;
        clear(body);
        body.append(el('pre', { class: 'block' }, text || '(empty)'));
      } catch (err) {
        clear(body);
        body.append(el('div', { class: 'empty' }, err.message));
      } finally {
        btn.disabled = false;
      }
    }
  }

  await loaders[0]();
  return null;
}
