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
import { el, clear, table, toast, modal, confirm, actionMenu } from '../ui.js';

export async function hosts(container) {
  const view = el('div', {},
      el('div', { class: 'page-head' },
          el('h1', {}, 'Virtual hosts'),
          el('p', {}, 'Add, start, stop and remove virtual hosts on this engine.'),
          el('span', { class: 'head-spacer' }),
          el('button', { type: 'button', class: 'btn', onclick: () => persist() }, 'Save to server.xml'),
          el('button', { type: 'button', class: 'btn btn-primary', onclick: () => addHostModal() }, 'Add host')));
  container.append(view);

  const wrap = el('div', { class: 'card' });
  view.append(wrap);

  async function load() {
    let data;
    try {
      data = await api('GET', '/api/hosts');
    } catch (err) {
      wrap.append(el('div', { class: 'empty' }, err.message));
      return;
    }
    clear(wrap);
    wrap.append(table({
      columns: [
        { key: 'name', label: 'Name' },
        { key: 'aliases', label: 'Aliases', muted: true, render: (h) => h.aliases.join(', ') || '-' },
        {
          key: 'appBase', label: 'App base', muted: true,
          render: (h) => el('code', {}, h.appBase || '-'),
        },
        {
          key: 'state', label: 'State',
          render: (h) => el('span', { class: 'badge ' + (h.started ? 'ok' : 'stop') },
              h.started ? 'Running' : 'Stopped'),
        },
        {
          key: 'self', label: 'Self',
          render: (h) => h.self
              ? el('span', { class: 'badge plain' }, 'this host')
              : el('span', { class: 'muted' }, '-'),
        },
        {
          key: 'actions', label: 'Actions',
          render: (h) => actionMenu([
              h.started
                  ? { label: 'Stop', disabled: h.self, onclick: () => startStop(h, 'stop') }
                  : { label: 'Start', class: 'btn-primary', disabled: h.self, onclick: () => startStop(h, 'start') },
              {
                label: 'Remove', class: 'btn-danger', disabled: h.self,
                title: h.self ? 'Cannot remove the host the manager is installed in' : 'Remove',
                onclick: () => removeHost(h),
              },
          ]),
        },
      ],
      rows: data,
      empty: 'No virtual hosts configured',
      stackable: true,
    }));
  }

  async function startStop(host, action) {
    const ok = action === 'stop'
        ? await confirm({
          title: 'Stop host',
          message: 'Stop host ' + host.name + '? All applications on it will be stopped.',
          confirmLabel: 'Stop',
          danger: false,
        })
        : true;
    if (!ok) return;
    try {
      const res = await api('POST', '/api/hosts/' + encodeURIComponent(host.name) + '/' + action);
      toast(res.message, 'ok');
      load();
    } catch (err) {
      toast(err.message, 'error');
    }
  }

  async function removeHost(host) {
    const ok = await confirm({
      title: 'Remove host',
      message: 'Remove host ' + host.name + '? Applications on it will be undeployed (files are kept).',
      confirmLabel: 'Remove',
      danger: true,
      requireText: host.name,
    });
    if (!ok) return;
    try {
      const res = await api('DELETE', '/api/hosts/' + encodeURIComponent(host.name));
      toast(res.message, 'ok');
      load();
    } catch (err) {
      toast(err.message, 'error');
    }
  }

  async function persist() {
    try {
      const res = await api('POST', '/api/hosts/persist');
      toast(res.message, 'ok');
    } catch (err) {
      toast(err.message, 'error');
    }
  }

  function addHostModal() {
    const body = el('div', {},
        el('div', { class: 'form-grid' },
            field('Name', el('input', { type: 'text', id: 'h-name', required: true, placeholder: 'localhost' })),
            field('Aliases (comma separated)', el('input', { type: 'text', id: 'h-aliases', placeholder: 'example.com, www.example.com' })),
            fieldSpan2('App base', el('input', { type: 'text', id: 'h-appbase', placeholder: '${catalina.base}/webapps' })),
            field('Manager webapp',
                el('label', { class: 'check' },
                    el('input', { type: 'checkbox', id: 'h-manager' }),
                    'Deploy the manager webapp to this host')),
            field('Auto deploy',
                el('label', { class: 'check' },
                    el('input', { type: 'checkbox', id: 'h-autodeploy', checked: true }),
                    'Auto deploy')),
            field('Deploy on startup',
                el('label', { class: 'check' },
                    el('input', { type: 'checkbox', id: 'h-deployonstartup', checked: true }),
                    'Deploy on startup')),
            field('Deploy XML',
                el('label', { class: 'check' },
                    el('input', { type: 'checkbox', id: 'h-deployxml', checked: true }),
                    'Deploy XML')),
            field('Unpack WARs',
                el('label', { class: 'check' },
                    el('input', { type: 'checkbox', id: 'h-unpackwars', checked: true }),
                    'Unpack WARs')),
            fieldSpan2('Copy XML',
                el('label', { class: 'check' },
                    el('input', { type: 'checkbox', id: 'h-copyxml' }),
                    'Copy context XML from the deployed WAR into META-INF/context.xml'))));

    modal({
      title: 'Add virtual host',
      content: body,
      actions: [
        { label: 'Cancel' },
        {
          label: 'Add',
          class: 'btn-primary',
          onClick: async (close) => {
            const body_ = {
              name: document.getElementById('h-name').value.trim(),
            };
            if (!body_.name) {
              toast('Enter a host name.', 'warn');
              return;
            }
            const aliases = document.getElementById('h-aliases').value.trim();
            if (aliases) body_.aliases = aliases.split(',').map((s) => s.trim()).filter(Boolean);
            const appBase = document.getElementById('h-appbase').value.trim();
            if (appBase) body_.appBase = appBase;
            body_.manager = document.getElementById('h-manager').checked;
            body_.autoDeploy = document.getElementById('h-autodeploy').checked;
            body_.deployOnStartup = document.getElementById('h-deployonstartup').checked;
            body_.deployXML = document.getElementById('h-deployxml').checked;
            body_.unpackWARs = document.getElementById('h-unpackwars').checked;
            body_.copyXML = document.getElementById('h-copyxml').checked;
            try {
              const res = await api('POST', '/api/hosts', body_);
              toast(res.message, 'ok');
              close();
              load();
            } catch (err) {
              toast(err.message, 'error');
            }
          },
        },
      ],
    });
  }

  function field(label, input) {
    return el('div', { class: 'field' }, el('label', {}, label), input);
  }

  function fieldSpan2(label, input) {
    return el('div', { class: 'field span-2' }, el('label', {}, label), input);
  }

  await load();
  return null;
}
