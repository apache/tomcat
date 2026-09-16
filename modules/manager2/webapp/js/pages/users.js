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
          el('p', {},
              'This user database is read-only. Add ',
              el('code', {}, 'readonly="false"'),
              ' to its ',
              el('code', {}, 'Resource'),
              ' definition in ',
              el('code', {}, 'server.xml'),
              ' and restart the server to allow changes.')));
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
      badges.append(el('span', { class: 'badge warn' }, 'read-only'));
    } else if (data.writable === false) {
      badges.append(el('span', { class: 'badge danger' }, 'not writable'));
    } else {
      badges.append(el('span', { class: 'badge ok' }, 'writable'));
    }
    const title = el('div', { class: 'card-title-row' },
        el('h2', {}, 'Users'),
        badges);

    if ((data.databases || []).length > 1) {
      const select = el('select', {
        class: 'log-field',
        'aria-label': 'User database',
        onchange: () => {
          dbName = select.value;
          load();
        },
      }, data.databases.map((d) => el('option', { value: d.name, selected: d.name === data.name || null },
          d.name)));
      return el('div', { class: 'card' }, title, select);
    }
    const sub = el('p', { class: 'muted' },
        'JNDI resource ', el('code', {}, data.name),
        db.id ? ' (id ' + db.id + ')' : '');
    return el('div', { class: 'card' }, title, sub);
  }

  function rolesBadges(roles, inherited) {
    const node = el('div', { class: 'chips' });
    for (const role of roles || []) {
      node.append(el('span', { class: 'badge plain' }, role));
    }
    const extra = (inherited || []).filter((r) => !(roles || []).includes(r));
    if (extra.length > 0) {
      node.append(el('span', { class: 'inherited', title: 'Inherited through group membership' },
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
    }, icon('plus', 14), document.createTextNode(' Add user'));

    const t = table({
      columns: [
        { key: 'username', label: 'User', render: (u) => el('span', { class: 'user-cell' },
            el('span', {}, u.username),
            u.fullName ? el('span', { class: 'muted' }, '  ' + u.fullName) : null) },
        { key: 'roles', label: 'Roles', render: (u) => rolesBadges(u.roles, u.effectiveRoles) },
        { key: 'groups', label: 'Groups', render: (u) => nameBadges(u.groups) },
        {
          key: 'actions', label: 'Actions',
          render: (u) => actionMenu([
              { label: 'Roles', disabled: !canEdit(), onclick: () => rolesModal('user', u) },
              { label: 'Password', disabled: !canEdit(), onclick: () => passwordModal(u) },
              { label: 'Remove', class: 'btn-danger', disabled: !canEdit(), onclick: () => removeUser(u) },
          ]),
        },
      ],
      rows: data.users || [],
      empty: 'No users in this database.',
      stackable: true,
    });

    const card = el('div', { class: 'card' },
        el('div', { class: 'card-title-row' }, el('h3', {}, 'Users'), addBtn),
        t);
    return card;
  }

  function groupsCard() {
    const addBtn = el('button', {
      type: 'button',
      class: 'btn btn-primary btn-sm',
      disabled: canEdit() ? null : true,
      onclick: addGroupModal,
    }, icon('plus', 14), document.createTextNode(' Add group'));

    const t = table({
      columns: [
        { key: 'groupname', label: 'Group', render: (g) => el('span', {}, g.groupname) },
        { key: 'roles', label: 'Roles', render: (g) => rolesBadges(g.roles, null) },
        { key: 'members', label: 'Members', render: (g) => nameBadges(g.members) },
        {
          key: 'actions', label: 'Actions',
          render: (g) => actionMenu([
              { label: 'Members', disabled: !canEdit(), onclick: () => membersModal(g) },
              { label: 'Roles', disabled: !canEdit(), onclick: () => rolesModal('group', g) },
              { label: 'Remove', class: 'btn-danger', disabled: !canEdit(), onclick: () => removeGroup(g) },
          ]),
        },
      ],
      rows: data.groups || [],
      empty: 'No groups in this database.',
      stackable: true,
    });

    return el('div', { class: 'card' },
        el('div', { class: 'card-title-row' }, el('h3', {}, 'Groups'), addBtn),
        t);
  }

  function rolesCard() {
    const addBtn = el('button', {
      type: 'button',
      class: 'btn btn-primary btn-sm',
      disabled: canEdit() ? null : true,
      onclick: addRoleModal,
    }, icon('plus', 14), document.createTextNode(' Add role'));

    const t = table({
      columns: [
        { key: 'rolename', label: 'Role', render: (r) => el('span', { class: 'badge plain' }, r.rolename) },
        { key: 'description', label: 'Description', render: (r) => r.description
            ? el('span', {}, r.description)
            : el('span', { class: 'muted' }, '-') },
        { key: 'users', label: 'Users', render: (r) => nameBadges((data.users || [])
            .filter((u) => (u.roles || []).includes(r.rolename))
            .map((u) => u.username)) },
        { key: 'groups', label: 'Groups', render: (r) => nameBadges((data.groups || [])
            .filter((g) => (g.roles || []).includes(r.rolename))
            .map((g) => g.groupname)) },
        {
          key: 'actions', label: 'Actions', render: (r) => el('div', { class: 'row-actions' },
              el('button', { type: 'button', class: 'btn btn-sm btn-danger', disabled: canEdit() ? null : true,
                  onclick: (e) => { e.stopPropagation(); removeRole(r); } }, 'Remove')),
        },
      ],
      rows: data.roles || [],
      empty: 'No roles defined in this database.',
      stackable: true,
    });

    return el('div', { class: 'card' },
        el('div', { class: 'card-title-row' }, el('h3', {}, 'Roles'), addBtn),
        el('p', { class: 'muted' },
            'Roles can also be created implicitly when assigned to a user or group.'),
        t);
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
    const roles = listInput('Roles (comma separated)', 'ud-roles', [], roleNames());
    const rolesInput = roles.querySelector('input');

    modal({
      title: 'Add user',
      content: el('div', {},
          el('div', { class: 'field' }, el('label', {}, 'User name'), username),
          el('div', { class: 'field' }, el('label', {}, 'Password'), password),
          el('div', { class: 'field' }, el('label', {}, 'Full name (optional)'), fullName),
          roles),
      actions: [
        { label: 'Cancel' },
        {
          label: 'Add user',
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
              toast(res.message || 'User added.');
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
    const list = listInput('Roles (comma separated)', 'roles-edit', item.roles, roleNames());
    const input = list.querySelector('input');

    modal({
      title: 'Roles for ' + name,
      content: el('div', {}, list),
      actions: [
        { label: 'Cancel' },
        {
          label: 'Save',
          class: 'btn-primary',
          onClick: async (close) => {
            try {
              const res = await api('POST', field + '/roles', { roles: parseList(input.value) });
              close();
              toast(res.message || 'Roles saved.');
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
      title: 'Password for ' + user.username,
      content: el('div', {},
          el('div', { class: 'field' },
              el('label', {}, 'New password'),
              password,
              el('span', { class: 'hint' }, 'Stored with the same semantics as the password attribute of tomcat-users.xml.'))),
      actions: [
        { label: 'Cancel' },
        {
          label: 'Save',
          class: 'btn-primary',
          onClick: async (close) => {
            try {
              const res = await api('POST', '/api/users/' + encodeURIComponent(user.username) + '/password',
                  { password: password.value });
              close();
              toast(res.message || 'Password saved.');
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
      title: 'Remove user',
      message: 'Remove the user "' + user.username + '" and all of its roles and group memberships?',
      confirmLabel: 'Remove user',
      danger: true,
      requireText: user.username,
    });
    if (!ok) return;
    try {
      const res = await api('DELETE', '/api/users/' + encodeURIComponent(user.username));
      toast(res.message || 'User removed.');
      load();
    } catch (err) {
      toast(err.message, 'error');
    }
  }

  function addGroupModal() {
    const groupname = el('input', { type: 'text', autocomplete: 'off' });
    const description = el('input', { type: 'text', autocomplete: 'off' });
    const roles = listInput('Roles (comma separated)', 'ug-roles', [], roleNames());
    const rolesInput = roles.querySelector('input');

    modal({
      title: 'Add group',
      content: el('div', {},
          el('div', { class: 'field' }, el('label', {}, 'Group name'), groupname),
          el('div', { class: 'field' }, el('label', {}, 'Description (optional)'), description),
          roles),
      actions: [
        { label: 'Cancel' },
        {
          label: 'Add group',
          class: 'btn-primary',
          onClick: async (close) => {
            const body = { groupname: groupname.value.trim() };
            if (description.value.trim()) body.description = description.value.trim();
            const r = parseList(rolesInput.value);
            if (r.length) body.roles = r;
            try {
              const res = await api('POST', '/api/groups', body);
              close();
              toast(res.message || 'Group added.');
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
    const list = listInput('Members (comma separated)', 'members-edit', group.members, userNames());
    const input = list.querySelector('input');

    modal({
      title: 'Members of ' + group.groupname,
      content: el('div', {}, list),
      actions: [
        { label: 'Cancel' },
        {
          label: 'Save',
          class: 'btn-primary',
          onClick: async (close) => {
            try {
              const res = await api('POST', '/api/groups/' + encodeURIComponent(group.groupname) + '/members',
                  { members: parseList(input.value) });
              close();
              toast(res.message || 'Members saved.');
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
      title: 'Remove group',
      message: 'Remove the group "' + group.groupname + '" and its membership from all users?',
      confirmLabel: 'Remove group',
      danger: true,
      requireText: group.groupname,
    });
    if (!ok) return;
    try {
      const res = await api('DELETE', '/api/groups/' + encodeURIComponent(group.groupname));
      toast(res.message || 'Group removed.');
      load();
    } catch (err) {
      toast(err.message, 'error');
    }
  }

  function addRoleModal() {
    const rolename = el('input', { type: 'text', autocomplete: 'off' });
    const description = el('input', { type: 'text', autocomplete: 'off' });

    modal({
      title: 'Add role',
      content: el('div', {},
          el('div', { class: 'field' }, el('label', {}, 'Role name'), rolename),
          el('div', { class: 'field' }, el('label', {}, 'Description (optional)'), description)),
      actions: [
        { label: 'Cancel' },
        {
          label: 'Add role',
          class: 'btn-primary',
          onClick: async (close) => {
            const body = { rolename: rolename.value.trim() };
            if (description.value.trim()) body.description = description.value.trim();
            try {
              const res = await api('POST', '/api/roles', body);
              close();
              toast(res.message || 'Role added.');
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
      title: 'Remove role',
      message: 'Remove the role "' + role.rolename + '"? It will be detached from all users and groups that hold it.',
      confirmLabel: 'Remove role',
      danger: true,
      requireText: role.rolename,
    });
    if (!ok) return;
    try {
      const res = await api('DELETE', '/api/roles/' + encodeURIComponent(role.rolename));
      toast(res.message || 'Role removed.');
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
