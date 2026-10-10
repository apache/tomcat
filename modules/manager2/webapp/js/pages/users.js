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

import { api, get } from '../api.js';
import { el, clear, toast, table, modal, confirm, icon, actionMenu } from '../ui.js';
import { t } from '../i18n.js';

// Build a localized message with inline code spans: the {0}, {1}, ...
// placeholders of the message are replaced by the given code values.
function codeNodes(key, codes) {
  const message = t(key);
  const nodes = [];
  const regex = /\{(\d+)\}/g;
  let last = 0;
  let m;
  while ((m = regex.exec(message))) {
    if (m.index > last) {
      nodes.push(document.createTextNode(message.substring(last, m.index)));
    }
    nodes.push(el('code', {}, codes[Number(m[1])]));
    last = regex.lastIndex;
  }
  if (last < message.length) {
    nodes.push(document.createTextNode(message.substring(last)));
  }
  return nodes;
}

export async function users(container) {
  let data = null;
  let dbName = null;
  let loading = false;

  const view = el('div', { class: 'users-page' });
  container.append(view);

  const canEdit = () => data && !data.readonly && data.writable;

  async function load() {
    if (loading) return;
    loading = true;
    try {
      const query = dbName ? '?name=' + encodeURIComponent(dbName) : '';
      data = await get('/api/users' + query);
      render();
    } finally {
      loading = false;
    }
  }

  function render() {
    clear(view);

    view.append(header());
    if (data.readonly) {
      view.append(el('div', { class: 'banner warn' },
          el('p', {}, codeNodes('manager2.ui.users.readonlyBanner',
              ['readonly="false"', 'Resource', 'server.xml']))));
    }
    view.append(usersCard());
    view.append(groupsCard());
    view.append(rolesCard());
  }

  function header() {
    const db = (data.databases || []).find((d) => d.name === data.name) || {};
    const badges = el('div', { class: 'row-actions' },
        el('span', { class: 'badge plain' }, db.type || 'UserDatabase'));
    if (data.readonly) {
      badges.append(el('span', { class: 'badge warn' }, t('manager2.ui.users.badgeReadonly')));
    } else if (data.writable === false) {
      badges.append(el('span', { class: 'badge danger' }, t('manager2.ui.users.badgeNotWritable')));
    } else {
      badges.append(el('span', { class: 'badge ok' }, t('manager2.ui.users.badgeWritable')));
    }
    const title = el('div', { class: 'card-title-row' },
        el('h2', {}, t('manager2.ui.nav.users')),
        badges);

    if ((data.databases || []).length > 1) {
      const select = el('select', {
        class: 'log-field',
        'aria-label': t('manager2.ui.users.dbSelect'),
        onchange: () => {
          dbName = select.value;
          load();
        },
      }, data.databases.map((d) => el('option', { value: d.name, selected: d.name === data.name || null },
          d.name)));
      return el('div', { class: 'card' }, title, select);
    }
    const sub = el('p', { class: 'muted' },
        codeNodes('manager2.ui.users.jndiResource', [data.name]),
        db.id ? t('manager2.ui.users.jndiId', db.id) : '');
    return el('div', { class: 'card' }, title, sub);
  }

  function rolesBadges(roles, inherited) {
    const node = el('div', { class: 'chips' });
    for (const role of roles || []) {
      node.append(el('span', { class: 'badge plain' }, role));
    }
    const extra = (inherited || []).filter((r) => !(roles || []).includes(r));
    if (extra.length > 0) {
      node.append(el('span', { class: 'inherited', title: t('manager2.ui.users.inheritedTitle') },
          '+ ' + extra.join(', ')));
    }
    return node;
  }

  function nameBadges(names) {
    const node = el('div', { class: 'chips' });
    for (const name of names || []) {
      node.append(el('span', { class: 'badge plain' }, name));
    }
    if (!(names || []).length) {
      node.append(el('span', { class: 'muted' }, '-'));
    }
    return node;
  }

  function usersCard() {
    const addBtn = el('button', {
      type: 'button',
      class: 'btn btn-primary btn-sm',
      disabled: canEdit() ? null : true,
      onclick: addUserModal,
    }, icon('plus', 14), document.createTextNode(' ' + t('manager2.ui.users.addUser')));

    const tbl = table({
      columns: [
        { key: 'username', label: t('manager2.ui.col.user'), render: (u) => el('span', { class: 'user-cell' },
            el('span', {}, u.username),
            u.fullName ? el('span', { class: 'muted' }, '  ' + u.fullName) : null) },
        { key: 'roles', label: t('manager2.ui.col.roles'), render: (u) => rolesBadges(u.roles, u.effectiveRoles) },
        { key: 'groups', label: t('manager2.ui.col.groups'), render: (u) => nameBadges(u.groups) },
        {
          key: 'actions', label: t('manager2.ui.col.actions'),
          render: (u) => actionMenu([
              { label: t('manager2.ui.col.roles'), disabled: !canEdit(), onclick: () => rolesModal('user', u) },
              { label: t('manager2.ui.col.password'), disabled: !canEdit(), onclick: () => passwordModal(u) },
              { label: t('manager2.ui.common.remove'), class: 'btn-danger', disabled: !canEdit(), onclick: () => removeUser(u) },
          ]),
        },
      ],
      rows: data.users || [],
      empty: t('manager2.ui.users.noUsers'),
      stackable: true,
    });

    const card = el('div', { class: 'card' },
        el('div', { class: 'card-title-row' }, el('h3', {}, t('manager2.ui.nav.users')), addBtn),
        tbl);
    return card;
  }

  function groupsCard() {
    const addBtn = el('button', {
      type: 'button',
      class: 'btn btn-primary btn-sm',
      disabled: canEdit() ? null : true,
      onclick: addGroupModal,
    }, icon('plus', 14), document.createTextNode(' ' + t('manager2.ui.users.addGroup')));

    const tbl = table({
      columns: [
        { key: 'groupname', label: t('manager2.ui.users.groupColumn'), render: (g) => el('span', {}, g.groupname) },
        { key: 'roles', label: t('manager2.ui.col.roles'), render: (g) => rolesBadges(g.roles, null) },
        { key: 'members', label: t('manager2.ui.col.members'), render: (g) => nameBadges(g.members) },
        {
          key: 'actions', label: t('manager2.ui.col.actions'),
          render: (g) => actionMenu([
              { label: t('manager2.ui.col.members'), disabled: !canEdit(), onclick: () => membersModal(g) },
              { label: t('manager2.ui.col.roles'), disabled: !canEdit(), onclick: () => rolesModal('group', g) },
              { label: t('manager2.ui.common.remove'), class: 'btn-danger', disabled: !canEdit(), onclick: () => removeGroup(g) },
          ]),
        },
      ],
      rows: data.groups || [],
      empty: t('manager2.ui.users.noGroups'),
      stackable: true,
    });

    return el('div', { class: 'card' },
        el('div', { class: 'card-title-row' }, el('h3', {}, t('manager2.ui.users.groupsHeading')), addBtn),
        tbl);
  }

  function rolesCard() {
    const addBtn = el('button', {
      type: 'button',
      class: 'btn btn-primary btn-sm',
      disabled: canEdit() ? null : true,
      onclick: addRoleModal,
    }, icon('plus', 14), document.createTextNode(' ' + t('manager2.ui.users.addRole')));

    const tbl = table({
      columns: [
        { key: 'rolename', label: t('manager2.ui.users.roleColumn'), render: (r) => el('span', { class: 'badge plain' }, r.rolename) },
        { key: 'description', label: t('manager2.ui.col.description'), render: (r) => r.description
            ? el('span', {}, r.description)
            : el('span', { class: 'muted' }, '-') },
        { key: 'users', label: t('manager2.ui.nav.users'), render: (r) => nameBadges((data.users || [])
            .filter((u) => (u.roles || []).includes(r.rolename))
            .map((u) => u.username)) },
        { key: 'groups', label: t('manager2.ui.users.groupsHeading'), render: (r) => nameBadges((data.groups || [])
            .filter((g) => (g.roles || []).includes(r.rolename))
            .map((g) => g.groupname)) },
        {
          key: 'actions', label: t('manager2.ui.col.actions'), render: (r) => el('div', { class: 'row-actions' },
              el('button', { type: 'button', class: 'btn btn-sm btn-danger', disabled: canEdit() ? null : true,
                  onclick: (e) => { e.stopPropagation(); removeRole(r); } }, t('manager2.ui.common.remove'))),
        },
      ],
      rows: data.roles || [],
      empty: t('manager2.ui.users.noRoles'),
      stackable: true,
    });

    return el('div', { class: 'card' },
        el('div', { class: 'card-title-row' }, el('h3', {}, t('manager2.ui.users.rolesHeading')), addBtn),
        el('p', { class: 'muted' }, t('manager2.ui.users.rolesHint')),
        tbl);
  }

  // ------------------------------- Modals ---------------------------------

  function listInput(label, id, values, suggestions, placeholder) {
    const list = el('datalist', { id });
    for (const s of (suggestions || [])) {
      list.append(el('option', { value: s }));
    }
    const input = el('input', { type: 'text', list: id, placeholder: placeholder || '' });
    input.value = (values || []).join(', ');
    return el('div', { class: 'field' }, el('label', {}, label), input, list);
  }

  function parseList(value) {
    return String(value || '')
        .split(',')
        .map((s) => s.trim())
        .filter((s) => s.length > 0);
  }

  function roleNames() {
    return (data.roles || []).map((r) => r.rolename);
  }

  function userNames() {
    return (data.users || []).map((u) => u.username);
  }

  function addUserModal() {
    const username = el('input', { type: 'text', autocomplete: 'off' });
    const password = el('input', { type: 'password', autocomplete: 'new-password' });
    const fullName = el('input', { type: 'text', autocomplete: 'off' });
    const roles = listInput(t('manager2.ui.users.rolesField'), 'ud-roles', [], roleNames());
    const rolesInput = roles.querySelector('input');

    modal({
      title: t('manager2.ui.users.addUser'),
      content: el('div', {},
          el('div', { class: 'field' }, el('label', {}, t('manager2.ui.col.username')), username),
          el('div', { class: 'field' }, el('label', {}, t('manager2.ui.col.password')), password),
          el('div', { class: 'field' }, el('label', {}, t('manager2.ui.users.fullNameField')), fullName),
          roles),
      actions: [
        { label: t('manager2.ui.common.cancel') },
        {
          label: t('manager2.ui.users.addUser'),
          class: 'btn-primary',
          onClick: async (close) => {
            const body = {
              username: username.value.trim(),
              password: password.value,
            };
            if (fullName.value.trim()) body.fullName = fullName.value.trim();
            const r = parseList(rolesInput.value);
            if (r.length) body.roles = r;
            try {
              const res = await api('POST', '/api/users', body);
              close();
              toast(res.message || t('manager2.ui.users.userAdded'));
              load();
            } catch (err) {
              toast(err.message, 'error');
            }
          },
        },
      ],
    });
    username.focus();
  }

  function rolesModal(kind, item) {
    const name = kind === 'user' ? item.username : item.groupname;
    const field = kind === 'user' ? '/api/users/' + encodeURIComponent(name) : '/api/groups/' + encodeURIComponent(name);
    const list = listInput(t('manager2.ui.users.rolesField'), 'roles-edit', item.roles, roleNames());
    const input = list.querySelector('input');

    modal({
      title: t('manager2.ui.users.rolesFor', name),
      content: el('div', {}, list),
      actions: [
        { label: t('manager2.ui.common.cancel') },
        {
          label: t('manager2.ui.common.save'),
          class: 'btn-primary',
          onClick: async (close) => {
            try {
              const res = await api('POST', field + '/roles', { roles: parseList(input.value) });
              close();
              toast(res.message || t('manager2.ui.users.rolesSaved'));
              load();
            } catch (err) {
              toast(err.message, 'error');
            }
          },
        },
      ],
    });
    input.focus();
  }

  function passwordModal(user) {
    const password = el('input', { type: 'password', autocomplete: 'new-password' });
    modal({
      title: t('manager2.ui.users.passwordFor', user.username),
      content: el('div', {},
          el('div', { class: 'field' },
              el('label', {}, t('manager2.ui.users.newPassword')),
              password,
              el('span', { class: 'hint' }, t('manager2.ui.users.passwordHint')))),
      actions: [
        { label: t('manager2.ui.common.cancel') },
        {
          label: t('manager2.ui.common.save'),
          class: 'btn-primary',
          onClick: async (close) => {
            try {
              const res = await api('POST', '/api/users/' + encodeURIComponent(user.username) + '/password',
                  { password: password.value });
              close();
              toast(res.message || t('manager2.ui.users.passwordSaved'));
              load();
            } catch (err) {
              toast(err.message, 'error');
            }
          },
        },
      ],
    });
    password.focus();
  }

  async function removeUser(user) {
    const ok = await confirm({
      title: t('manager2.ui.users.removeUserTitle'),
      message: t('manager2.ui.users.removeUserConfirm', user.username),
      confirmLabel: t('manager2.ui.users.removeUserTitle'),
      danger: true,
      requireText: user.username,
    });
    if (!ok) return;
    try {
      const res = await api('DELETE', '/api/users/' + encodeURIComponent(user.username));
      toast(res.message || t('manager2.ui.users.userRemoved'));
      load();
    } catch (err) {
      toast(err.message, 'error');
    }
  }

  function addGroupModal() {
    const groupname = el('input', { type: 'text', autocomplete: 'off' });
    const description = el('input', { type: 'text', autocomplete: 'off' });
    const roles = listInput(t('manager2.ui.users.rolesField'), 'ug-roles', [], roleNames());
    const rolesInput = roles.querySelector('input');

    modal({
      title: t('manager2.ui.users.addGroup'),
      content: el('div', {},
          el('div', { class: 'field' }, el('label', {}, t('manager2.ui.users.groupNameField')), groupname),
          el('div', { class: 'field' }, el('label', {}, t('manager2.ui.users.descriptionField')), description),
          roles),
      actions: [
        { label: t('manager2.ui.common.cancel') },
        {
          label: t('manager2.ui.users.addGroup'),
          class: 'btn-primary',
          onClick: async (close) => {
            const body = { groupname: groupname.value.trim() };
            if (description.value.trim()) body.description = description.value.trim();
            const r = parseList(rolesInput.value);
            if (r.length) body.roles = r;
            try {
              const res = await api('POST', '/api/groups', body);
              close();
              toast(res.message || t('manager2.ui.users.groupAdded'));
              load();
            } catch (err) {
              toast(err.message, 'error');
            }
          },
        },
      ],
    });
    groupname.focus();
  }

  function membersModal(group) {
    const list = listInput(t('manager2.ui.users.membersField'), 'members-edit', group.members, userNames());
    const input = list.querySelector('input');

    modal({
      title: t('manager2.ui.users.membersOf', group.groupname),
      content: el('div', {}, list),
      actions: [
        { label: t('manager2.ui.common.cancel') },
        {
          label: t('manager2.ui.common.save'),
          class: 'btn-primary',
          onClick: async (close) => {
            try {
              const res = await api('POST', '/api/groups/' + encodeURIComponent(group.groupname) + '/members',
                  { members: parseList(input.value) });
              close();
              toast(res.message || t('manager2.ui.users.membersSaved'));
              load();
            } catch (err) {
              toast(err.message, 'error');
            }
          },
        },
      ],
    });
    input.focus();
  }

  async function removeGroup(group) {
    const ok = await confirm({
      title: t('manager2.ui.users.removeGroupTitle'),
      message: t('manager2.ui.users.removeGroupConfirm', group.groupname),
      confirmLabel: t('manager2.ui.users.removeGroupTitle'),
      danger: true,
      requireText: group.groupname,
    });
    if (!ok) return;
    try {
      const res = await api('DELETE', '/api/groups/' + encodeURIComponent(group.groupname));
      toast(res.message || t('manager2.ui.users.groupRemoved'));
      load();
    } catch (err) {
      toast(err.message, 'error');
    }
  }

  function addRoleModal() {
    const rolename = el('input', { type: 'text', autocomplete: 'off' });
    const description = el('input', { type: 'text', autocomplete: 'off' });

    modal({
      title: t('manager2.ui.users.addRole'),
      content: el('div', {},
          el('div', { class: 'field' }, el('label', {}, t('manager2.ui.users.roleNameField')), rolename),
          el('div', { class: 'field' }, el('label', {}, t('manager2.ui.users.descriptionField')), description)),
      actions: [
        { label: t('manager2.ui.common.cancel') },
        {
          label: t('manager2.ui.users.addRole'),
          class: 'btn-primary',
          onClick: async (close) => {
            const body = { rolename: rolename.value.trim() };
            if (description.value.trim()) body.description = description.value.trim();
            try {
              const res = await api('POST', '/api/roles', body);
              close();
              toast(res.message || t('manager2.ui.users.roleAdded'));
              load();
            } catch (err) {
              toast(err.message, 'error');
            }
          },
        },
      ],
    });
    rolename.focus();
  }

  async function removeRole(role) {
    const ok = await confirm({
      title: t('manager2.ui.users.removeRoleTitle'),
      message: t('manager2.ui.users.removeRoleConfirm', role.rolename),
      confirmLabel: t('manager2.ui.users.removeRoleTitle'),
      danger: true,
      requireText: role.rolename,
    });
    if (!ok) return;
    try {
      const res = await api('DELETE', '/api/roles/' + encodeURIComponent(role.rolename));
      toast(res.message || t('manager2.ui.users.roleRemoved'));
      load();
    } catch (err) {
      toast(err.message, 'error');
    }
  }

  try {
    await load();
  } catch (err) {
    if (err.code === 'USER_DATABASE_MISSING') {
      clear(view);
      view.append(el('div', { class: 'card empty' }, el('p', {}, err.message)));
      return;
    }
    throw err;
  }
}
