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
import { t } from '../i18n.js';

export async function hosts(container) {
  const view = el('div', {},
      el('div', { class: 'page-head' },
          el('h1', {}, t('manager2.ui.hosts.title')),
          el('p', {}, t('manager2.ui.hosts.subtitle')),
          el('span', { class: 'head-spacer' }),
          el('button', { type: 'button', class: 'btn', onclick: () => persist() }, t('manager2.ui.hosts.save')),
          el('button', { type: 'button', class: 'btn btn-primary', onclick: () => addHostModal() }, t('manager2.ui.hosts.add'))));
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
        { key: 'name', label: t('manager2.ui.col.name') },
        { key: 'aliases', label: t('manager2.ui.col.aliases'), muted: true, render: (h) => h.aliases.join(', ') || '-' },
        {
          key: 'appBase', label: t('manager2.ui.hosts.appBase'), muted: true,
          render: (h) => el('code', {}, h.appBase || '-'),
        },
        {
          key: 'state', label: t('manager2.ui.col.state'),
          render: (h) => el('span', { class: 'badge ' + (h.started ? 'ok' : 'stop') },
              h.started ? t('manager2.ui.state.running') : t('manager2.ui.state.stopped')),
        },
        {
          key: 'self', label: t('manager2.ui.hosts.selfColumn'),
          render: (h) => h.self
              ? el('span', { class: 'badge plain' }, t('manager2.ui.hosts.thisHost'))
              : el('span', { class: 'muted' }, '-'),
        },
        {
          key: 'actions', label: t('manager2.ui.col.actions'),
          render: (h) => actionMenu([
              h.started
                  ? { label: t('manager2.ui.common.stop'), disabled: h.self, onclick: () => startStop(h, 'stop') }
                  : { label: t('manager2.ui.common.start'), class: 'btn-primary', disabled: h.self, onclick: () => startStop(h, 'start') },
              {
                label: t('manager2.ui.common.remove'), class: 'btn-danger', disabled: h.self,
                title: h.self ? t('manager2.ui.hosts.cannotRemoveSelf') : t('manager2.ui.common.remove'),
                onclick: () => removeHost(h),
              },
          ]),
        },
      ],
      rows: data,
      empty: t('manager2.ui.hosts.empty'),
      stackable: true,
    }));
  }

  async function startStop(host, action) {
    const ok = action === 'stop'
        ? await confirm({
          title: t('manager2.ui.hosts.stopConfirmTitle'),
          message: t('manager2.ui.hosts.stopConfirm', host.name),
          confirmLabel: t('manager2.ui.common.stop'),
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
      title: t('manager2.ui.hosts.removeConfirmTitle'),
      message: t('manager2.ui.hosts.removeConfirm', host.name),
      confirmLabel: t('manager2.ui.common.remove'),
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
            field(t('manager2.ui.col.name'), el('input', { type: 'text', id: 'h-name', required: true, placeholder: t('manager2.ui.hosts.namePlaceholder') })),
            field(t('manager2.ui.hosts.aliasesLabel'), el('input', { type: 'text', id: 'h-aliases', placeholder: t('manager2.ui.hosts.aliasesPlaceholder') })),
            fieldSpan2(t('manager2.ui.hosts.appBase'), el('input', { type: 'text', id: 'h-appbase', placeholder: '${catalina.base}/webapps' })),
            field(t('manager2.ui.hosts.managerWebapp'),
                el('label', { class: 'check' },
                    el('input', { type: 'checkbox', id: 'h-manager' }),
                    t('manager2.ui.hosts.managerWebappHelp'))),
            field(t('manager2.ui.hosts.autoDeploy'),
                el('label', { class: 'check' },
                    el('input', { type: 'checkbox', id: 'h-autodeploy', checked: true }),
                    t('manager2.ui.hosts.autoDeploy'))),
            field(t('manager2.ui.hosts.deployOnStartup'),
                el('label', { class: 'check' },
                    el('input', { type: 'checkbox', id: 'h-deployonstartup', checked: true }),
                    t('manager2.ui.hosts.deployOnStartup'))),
            field(t('manager2.ui.hosts.deployXml'),
                el('label', { class: 'check' },
                    el('input', { type: 'checkbox', id: 'h-deployxml', checked: true }),
                    t('manager2.ui.hosts.deployXml'))),
            field(t('manager2.ui.hosts.unpackWars'),
                el('label', { class: 'check' },
                    el('input', { type: 'checkbox', id: 'h-unpackwars', checked: true }),
                    t('manager2.ui.hosts.unpackWars'))),
            fieldSpan2(t('manager2.ui.hosts.copyXml'),
                el('label', { class: 'check' },
                    el('input', { type: 'checkbox', id: 'h-copyxml' }),
                    t('manager2.ui.hosts.copyXmlHelp')))));

    modal({
      title: t('manager2.ui.hosts.addTitle'),
      content: body,
      actions: [
        { label: t('manager2.ui.common.cancel') },
        {
          label: t('manager2.ui.common.add'),
          class: 'btn-primary',
          onClick: async (close) => {
            const body_ = {
              name: document.getElementById('h-name').value.trim(),
            };
            if (!body_.name) {
              toast(t('manager2.ui.hosts.nameMissing'), 'warn');
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
