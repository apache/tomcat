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
import { el, clear, toast, modal, confirm, stateBadge, actionMenu } from '../ui.js';

const NUMERIC_TYPES = new Set(['int', 'long', 'short', 'byte', 'float', 'double']);
const RISKY_ATTRIBUTES = new Set(['name', 'path', 'defaultHost']);

// Which structural child types can be added to a node of a given type.
// Independently of these, a lifecycle listener can be added to any node
// whose component implements Lifecycle (the node detail reports this as
// `acceptsListener`); see addChildModal.
const CHILD_TYPES = {
  server: ['service'],
  service: ['connector', 'executor'],
  engine: ['host', 'realm', 'valve', 'cluster'],
  host: ['context', 'alias', 'realm', 'valve', 'cluster'],
  context: ['wrapper', 'realm', 'manager', 'resources', 'loader', 'cookieProcessor', 'valve', 'cluster'],
  wrapper: [],
  connector: ['sslHostConfig', 'upgradeProtocol'],
  sslHostConfig: ['certificate'],
  certificate: [],
  upgradeProtocol: [],
  realm: [],
  manager: ['sessionIdGenerator'],
  cluster: ['channel', 'deployer', 'clusterValve', 'clusterManager', 'clusterListener'],
  channel: ['membership', 'sender', 'receiver', 'interceptor'],
  membership: [],
  sender: ['transport'],
  receiver: [],
  interceptor: [],
  deployer: [],
  clusterManager: ['sessionIdGenerator'],
  transport: [],
  clusterValve: [],
  clusterListener: [],
  resources: [],
  loader: [],
  cookieProcessor: [],
  sessionIdGenerator: [],
  namingResources: ['resource', 'resourceLink', 'resourceEnvRef', 'environment', 'ejb', 'localEjb', 'serviceRef'],
  resource: [],
  resourceLink: [],
  resourceEnvRef: [],
  environment: [],
  ejb: [],
  localEjb: [],
  serviceRef: [],
  executor: [],
  valve: [],
  alias: [],
  listener: [],
};

// The string parameters (RefAddr keys) consumed by the first party JNDI
// ObjectFactory implementations shipped with Tomcat. Mirrors the tables
// of the server (the entry detail gets them from there); used to
// pre-render the fields of the add dialog. Compact form: name, with a
// trailing ':b' (boolean) or ':i' (int) or ':l' (long) suffix.
const POOL_FACTORY_OPTIONS = [
  'instanceKey', 'description', 'loginTimeout:i', 'blockWhenExhausted:b',
  'evictionPolicyClassName', 'lifo:b', 'maxIdlePerKey:i', 'maxTotalPerKey:i',
  'maxWaitMillis:l', 'minEvictableIdleTimeMillis:l', 'minIdlePerKey:i',
  'numTestsPerEvictionRun:i', 'softMinEvictableIdleTimeMillis:l', 'testOnCreate:b',
  'testOnBorrow:b', 'testOnReturn:b', 'testWhileIdle:b',
  'timeBetweenEvictionRunsMillis:l', 'validationQuery', 'validationQueryTimeout:i',
  'rollbackAfterValidation:b', 'maxConnLifetimeMillis:l', 'defaultAutoCommit:b',
  'defaultTransactionIsolation:i', 'defaultReadOnly:b',
];
const FACTORY_OPTIONS = {
  'org.apache.tomcat.dbcp.dbcp2.BasicDataSourceFactory': [
    'defaultAutoCommit:b', 'defaultReadOnly:b', 'defaultTransactionIsolation',
    'defaultCatalog', 'defaultSchema', 'cacheState:b', 'driverClassName', 'lifo:b',
    'maxTotal:i', 'maxIdle:i', 'minIdle:i', 'initialSize:i', 'maxWaitMillis:l',
    'testOnCreate:b', 'testOnBorrow:b', 'testOnReturn:b',
    'timeBetweenEvictionRunsMillis:l', 'numTestsPerEvictionRun:i',
    'minEvictableIdleTimeMillis:l', 'softMinEvictableIdleTimeMillis:l',
    'evictionPolicyClassName', 'testWhileIdle:b', 'password', 'url', 'username',
    'validationQuery', 'validationQueryTimeout:l', 'connectionInitSqls',
    'accessToUnderlyingConnectionAllowed:b', 'removeAbandonedOnBorrow:b',
    'removeAbandonedOnMaintenance:b', 'removeAbandonedTimeout:l', 'logAbandoned:b',
    'abandonedUsageTracking', 'poolPreparedStatements:b',
    'clearStatementPoolOnReturn:b', 'maxOpenPreparedStatements:i',
    'connectionProperties', 'maxConnLifetimeMillis:l', 'logExpiredConnections:b',
    'rollbackOnReturn:b', 'enableAutoCommitOnReturn:b', 'defaultQueryTimeout:l',
    'fastFailValidation:b', 'disconnectionSqlCodes', 'disconnectionIgnoreSqlCodes',
    'jmxName', 'registerConnectionMBean:b', 'connectionFactoryClassName',
  ],
  'org.apache.catalina.users.MemoryUserDatabaseFactory': [
    'pathname', 'readonly:b', 'watchSource:b',
  ],
  'org.apache.catalina.users.DataSourceUserDatabaseFactory': [
    'dataSourceName', 'readonly:b', 'userTable', 'groupTable', 'roleTable',
    'userRoleTable', 'userGroupTable', 'groupRoleTable', 'roleNameCol',
    'roleAndGroupDescriptionCol', 'groupNameCol', 'userCredCol',
    'userFullNameCol', 'userNameCol',
  ],
  'org.apache.tomcat.dbcp.dbcp2.datasources.PerUserPoolDataSourceFactory': [
    'defaultMaxTotal:i', 'defaultMaxIdle:i', 'defaultMaxWaitMillis:l',
    ...POOL_FACTORY_OPTIONS,
  ],
  'org.apache.tomcat.dbcp.dbcp2.datasources.SharedPoolDataSourceFactory': [
    'maxTotal:i', ...POOL_FACTORY_OPTIONS,
  ],
};
const BASIC_DATA_SOURCE_FACTORY = 'org.apache.tomcat.dbcp.dbcp2.BasicDataSourceFactory';

// The JNDI entry types: all of them extend ResourceBase, which carries a
// generic map of string parameters (factory options and friends) that the
// JNDI factories consume at lookup time. Those parameters are shown in the
// entry detail and can be added, edited and removed there.
const NAMING_ENTRY_TYPES = new Set(['resource', 'resourceLink', 'resourceEnvRef', 'environment', 'ejb', 'localEjb', 'serviceRef']);

// The factory a resource resolves to: the explicit factory, or the
// default the ResourceFactory dispatches the resource type to.
function effectiveFactory(jndiType, factory) {
  if (factory) return factory;
  if (jndiType === 'javax.sql.DataSource') return BASIC_DATA_SOURCE_FACTORY;
  return null;
}

// The option list of the effective factory of a resource, or null when
// the factory is not one of the first party factories with a closed set
// of options (their parameters stay free form).
function factoryOptions(jndiType, factory) {
  const f = effectiveFactory(jndiType, factory);
  return f ? (FACTORY_OPTIONS[f] || null) : null;
}

// Component types that a context (or a manager) holds exactly one of.
// Adding one of these replaces the current instance.
const REPLACE_TYPES = new Set(['manager', 'resources', 'loader', 'cookieProcessor', 'sessionIdGenerator',
  'channel', 'membership', 'sender', 'receiver', 'deployer', 'clusterManager', 'transport']);

// Build a request path for a node id. Each segment is percent encoded so
// that the container's single path decode yields exactly the (already
// encoded) id segments the server expects.
function nodePath(id) {
  return id.split('/').map(encodeURIComponent).join('/');
}

export async function configuration(container) {
  let selectedId = null;
  let selectedDetail = null;
  const expanded = new Set(['server']);

  const view = el('div', { class: 'config-page' },
      el('div', { class: 'page-head' },
          el('h1', {}, 'Configuration'),
          el('p', {}, 'The live component tree of this server. Changes apply immediately; save to make them permanent.'),
          el('span', { class: 'head-spacer' }),
          el('button', { type: 'button', class: 'btn', onclick: reloadAll }, 'Reload'),
          el('button', { type: 'button', class: 'btn btn-primary', onclick: () => saveToServerXml() }, 'Save to server.xml')));
  view.append(el('div', { class: 'config-split' },
      el('div', { class: 'card config-tree-card' }, el('div', { class: 'card-title-row' },
          el('h3', {}, 'Components'))),
      el('div', { class: 'card config-detail-card' })));
  container.append(view);

  const treeCard = view.querySelector('.config-tree-card');
  const detailCard = view.querySelector('.config-detail-card');
  const treeWrap = el('div', { class: 'config-tree' });
  treeCard.append(treeWrap);

  // ============================ Tree ==================================

  async function loadTree() {
    const savedScroll = treeWrap.scrollTop;
    clear(treeWrap);
    treeWrap.append(el('div', { class: 'spinner', role: 'status', 'aria-label': 'Loading' }));
    let data;
    try {
      data = await api('GET', '/api/config/tree');
    } catch (err) {
      clear(treeWrap);
      treeWrap.append(el('div', { class: 'empty' }, err.message));
      return;
    }
    clear(treeWrap);
    treeWrap.append(buildTree(data.tree, 0));
    treeWrap.scrollTop = savedScroll;
  }

  function buildTree(node, depth) {
    const kids = node.children || [];
    const hasKids = kids.length > 0;
    const isOpen = expanded.has(node.id);

    // The indent is capped so that deeply nested branches still fit on
    // narrow screens.
    const row = el('div', { class: 'config-node', style: 'padding-left:' + (Math.min(depth, 6) * 16 + 4) + 'px' },
        el('button', {
          type: 'button', class: 'config-node-toggle' + (hasKids ? '' : ' leaf'),
          'aria-label': hasKids ? 'Toggle' : '',
          onclick: (e) => {
            e.stopPropagation();
            if (!hasKids) return;
            if (isOpen) expanded.delete(node.id); else expanded.add(node.id);
            loadTree();
          },
        }, hasKids ? (isOpen ? '\u25BC' : '\u25B6') : ''),
        el('span', { class: 'config-node-label', onclick: () => selectNode(node.id), style: 'cursor:pointer' },
            el('span', { class: 'config-type ' + node.type }, node.type),
            el('span', { class: 'config-node-name' }, node.name || '(unnamed)'),
            node.self ? el('span', { class: 'badge plain', style: 'margin-left:6px' }, 'this app') : '',
            node.state ? el('span', {
              class: 'badge ' + (node.state === 'STARTED' || node.state === 'AVAILABLE' ? 'ok' : 'stop'),
              style: 'margin-left:6px',
            }, node.state) : ''));

    const frag = document.createDocumentFragment();
    frag.append(row);
    if (hasKids && isOpen) {
      for (const child of kids) {
        frag.append(buildTree(child, depth + 1));
      }
    }
    return frag;
  }

  // ============================ Detail ===============================

  async function selectNode(id) {
    selectedId = id;
    clear(detailCard);
    detailCard.append(el('div', { class: 'spinner', role: 'status', 'aria-label': 'Loading' }));
    let data;
    try {
      data = await api('GET', '/api/config/node/' + nodePath(id));
    } catch (err) {
      clear(detailCard);
      detailCard.append(el('div', { class: 'empty' }, err.message));
      selectedDetail = null;
      return;
    }
    selectedDetail = data;
    renderDetail();
    // On narrow screens the detail card is stacked below the tree; bring it
    // into view after a selection so the result is immediately visible.
    if (window.matchMedia('(max-width: 768px)').matches) {
      detailCard.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }

  function renderDetail() {
    const d = selectedDetail;
    clear(detailCard);

    const actions = [];
    if (addable(d)) {
      actions.push({ label: '+ Add', onclick: () => addChildModal(d) });
    }
    if (d.type !== 'server') {
      actions.push({
        label: 'Remove', class: 'btn-danger', disabled: d.self,
        title: d.self ? 'Cannot remove the component the manager is installed in' : 'Remove',
        onclick: () => removeNode(d),
      });
    }
    actions.push(...lifecycleActions(d));
    const head = el('div', { class: 'card-title-row' },
        el('div', { class: 'config-detail-title' },
            el('span', { class: 'config-type ' + d.type }, d.type),
            el('h3', {}, d.name || '(unnamed)'),
            d.state ? stateBadge(d.state) : ''),
        actionMenu(actions));
    detailCard.append(head);

    if (d.className) {
      detailCard.append(el('p', { class: 'config-class' }, el('code', {}, d.className)));
    }

    const props = d.properties || [];
    const naming = NAMING_ENTRY_TYPES.has(d.type);
    if (props.length || naming) {
      const rows = props.map((p) => propRow(d, p));
      const head = el('div', { class: 'config-section-head' },
          el('h4', {}, 'Properties'),
          naming ? el('button', {
            type: 'button', class: 'btn btn-sm',
            onclick: () => paramForm(head, d),
          }, '+ Add parameter') : null);
      const body = rows.length
          ? el('div', { class: 'config-props' }, rows)
          : el('p', { class: 'muted' }, 'No parameters set. Use "+ Add parameter" to add one.');
      detailCard.append(head, body);
    } else {
      detailCard.append(el('p', { class: 'muted' }, 'This component exposes no editable properties.'));
    }

    const kids = d.children || [];
    if (kids.length) {
      detailCard.append(el('h4', {}, 'Children'),
          el('div', { class: 'config-children' }, kids.map((k) => el('button', {
            type: 'button', class: 'config-child-chip', onclick: () => selectNode(k.id),
          }, k.type + ': ' + (k.name || '(unnamed)')))));
    }
  }

  function propRow(node, p) {
    const label = el('label', { class: 'config-prop-name' }, p.name,
        p.param ? el('span', { class: 'config-param-hint' }, 'parameter') : '',
        p.description ? el('span', { class: 'config-prop-desc', title: p.description }, p.description) : '');
    if (!p.writable) {
      return el('div', { class: 'config-prop readonly' },
          label,
          el('span', { class: 'config-prop-value muted' }, formatValue(p.value)));
    }
    const input = buildInput(p);
    input.dataset.name = p.name;
    const edit = el('div', { class: 'config-prop-edit' },
        input,
        el('button', {
          type: 'button', class: 'btn btn-sm',
          onclick: () => applyProperty(node, p, input),
        }, 'Apply'));
    if (p.param) {
      if (paramSet(p)) {
        edit.append(el('button', {
          type: 'button', class: 'btn btn-sm btn-danger',
          title: 'Remove this parameter',
          onclick: () => removeParameter(node, p),
        }, 'Remove'));
      } else {
        // An unset parameter: nothing to remove. Applying a value adds
        // the parameter (or, for a boolean, unchecking it is a no-op).
        edit.append(el('span', { class: 'config-param-hint' }, 'not set'));
      }
    }
    return el('div', { class: 'config-prop' }, label, edit);
  }

  // Whether a parameter of a JNDI entry is set (has a non empty value).
  function paramSet(p) {
    return p.value !== null && p.value !== undefined && String(p.value) !== '';
  }

  function buildInput(p) {
    const t = p.type;
    if (t === 'boolean') {
      const box = el('input', { type: 'checkbox', class: 'config-check' });
      // A parameter is stored as the string "true" or "false": only
      // "true" checks the box (Boolean("false") would be wrongly
      // truthy). A real boolean attribute is stored as an actual
      // boolean.
      box.checked = p.param ? p.value === 'true' : Boolean(p.value);
      return box;
    }
    if (NUMERIC_TYPES.has(t)) {
      return el('input', { type: 'text', inputmode: 'numeric', value: p.value == null ? '' : String(p.value), class: 'config-input num' });
    }
    if (t === '[Ljava.lang.String;') {
      const arr = Array.isArray(p.value) ? p.value : (p.value == null ? [] : [p.value]);
      return el('input', { type: 'text', value: arr.join(', '), class: 'config-input', placeholder: 'comma, separated' });
    }
    if (t === 'java.lang.String') {
      return el('input', { type: 'text', value: p.value == null ? '' : String(p.value), class: 'config-input' });
    }
    // Non editable simple type: show as read only text.
    const ro = el('input', { type: 'text', readonly: true, value: formatValue(p.value), class: 'config-input' });
    ro.disabled = true;
    return ro;
  }

  function formatValue(v) {
    if (v === null || v === undefined) return '';
    if (Array.isArray(v)) return v.join(', ');
    if (typeof v === 'object') return JSON.stringify(v);
    return String(v);
  }

  function readInput(input, p) {
    const t = p.type;
    if (t === 'boolean') return input.checked;
    if (NUMERIC_TYPES.has(t)) return input.value.trim();
    if (t === '[Ljava.lang.String;') {
      return input.value.split(',').map((s) => s.trim()).filter(Boolean);
    }
    return input.value;
  }

  async function applyProperty(node, p, input) {
    let value = readInput(input, p);
    // Guard against no-op writes. A boolean parameter is a toggle: the
    // effective state is whether it is set to "true", so the no-op
    // check is on the checkbox state. That also means applying while
    // the box is unchecked does not store "false" for an absent
    // parameter, and re-applying an already stored "false" is a no-op.
    const noOp = p.param && p.type === 'boolean'
        ? Boolean(value) === (p.value === 'true')
        : sameValue(value, p.value);
    if (noOp) {
      toast('No changes to apply.', 'info');
      return;
    }
    if (RISKY_ATTRIBUTES.has(p.name)) {
      const ok = await confirm({
        title: 'Change ' + p.name,
        message: 'Changing ' + p.name + ' of ' + (node.name || node.type) + ' may break routing. Continue?',
        confirmLabel: 'Change',
        danger: true,
        requireText: formatValue(p.value) || node.name,
      });
      if (!ok) return;
    }
    try {
      const res = await api('POST', '/api/config/attribute', { id: node.id, name: p.name, value });
      toast(res.message, 'ok');
      selectNode(selectedId);
    } catch (err) {
      toast(err.message, 'error');
    }
  }

  // Toggle the inline "add a parameter" form below the Properties heading
  // of a JNDI entry. The form is removed again when it is toggled off or
  // when the entry detail is re-rendered.
  function paramForm(heading, node) {
    const existing = heading.parentElement.querySelector('.config-param-form');
    if (existing) {
      existing.remove();
      return;
    }
    const nameInput = el('input', { type: 'text', class: 'config-input', placeholder: 'parameter name (e.g. url, maxTotal)' });
    const valueInput = el('input', { type: 'text', class: 'config-input', placeholder: 'value' });
    const form = el('div', { class: 'config-param-form' },
        nameInput,
        valueInput,
        el('button', {
          type: 'button', class: 'btn btn-sm btn-primary',
          onclick: () => addParameter(node, nameInput.value.trim(), valueInput.value),
        }, 'Add'),
        el('button', { type: 'button', class: 'btn btn-sm', onclick: () => form.remove() }, 'Cancel'));
    heading.after(form);
    nameInput.focus();
  }

  // Add a generic parameter to a JNDI entry. Any parameter name is
  // accepted: the JNDI factory decides which ones it consumes at lookup
  // time.
  async function addParameter(node, name, value) {
    if (!name) {
      toast('A parameter name is required.', 'error');
      return;
    }
    try {
      const res = await api('POST', '/api/config/attribute', { id: node.id, name, value });
      toast(res.message, 'ok');
      await selectNode(node.id);
    } catch (err) {
      toast(err.message, 'error');
    }
  }

  // Remove a generic parameter from a JNDI entry by clearing it: the server
  // drops parameters whose value is empty.
  async function removeParameter(node, p) {
    if (!paramSet(p)) {
      toast('This parameter is not set.', 'info');
      return;
    }
    try {
      const res = await api('POST', '/api/config/attribute', { id: node.id, name: p.name, value: '' });
      toast(res.message, 'ok');
      await selectNode(node.id);
    } catch (err) {
      toast(err.message, 'error');
    }
  }

  function sameValue(a, b) {
    if (Array.isArray(a) || Array.isArray(b)) {
      const aa = Array.isArray(a) ? a : [a];
      const bb = Array.isArray(b) ? b : [b];
      if (aa.length !== bb.length) return false;
      return aa.every((x, i) => String(x) === String(bb[i]));
    }
    return String(a) === String(b);
  }

  function addableTypes(node) {
    const types = (CHILD_TYPES[node.type] || []).slice();
    // Only combined realms accept (sub) realms; the node detail reports
    // this as `acceptsSubRealm` (not derivable from the class name).
    if (node.type === 'realm' && node.acceptsSubRealm) types.push('realm');
    // The server level naming resources do not accept resource links
    // (they are not parsed from <GlobalNamingResources>).
    if (node.type === 'namingResources' && node.global) {
      const i = types.indexOf('resourceLink');
      if (i >= 0) types.splice(i, 1);
    }
    if (node.acceptsListener) types.push('listener');
    return types;
  }

  function addable(node) {
    return addableTypes(node).length > 0;
  }

  // ============================ Add child ============================

  function addChildModal(parent) {
    const types = addableTypes(parent);
    if (!types.length) return;

    const select = el('select', { class: 'config-input' },
        types.map((t) => el('option', { value: t }, t)));
    const fieldsWrap = el('div', { class: 'form-grid' });
    const noteWrap = el('div', {});

    function currentCtx(type) {
      if (type !== 'resource') return {};
      const t = document.getElementById('c-type');
      const f = document.getElementById('c-factory');
      return { jndiType: t ? t.value.trim() : '', factory: f ? f.value.trim() : '' };
    }

    function renderFields() {
      const type = select.value;
      const ctx = currentCtx(type);
      // Remember the values already entered so that a re-render (the
      // factory options appear or change) does not lose them.
      const previous = {};
      for (const [key, id] of childFieldIds(type, ctx)) {
        const node = document.getElementById(id);
        if (node) previous[id] = node.type === 'checkbox' ? node.checked : node.value;
      }
      clear(fieldsWrap);
      clear(noteWrap);
      for (const [label, input, span2] of childFields(type, ctx)) {
        fieldsWrap.append(span2
            ? el('div', { class: 'field span-2' }, el('label', {}, label), input)
            : el('div', { class: 'field' }, el('label', {}, label), input));
      }
      for (const id in previous) {
        const node = document.getElementById(id);
        if (!node) continue;
        if (node.type === 'checkbox') node.checked = previous[id];
        else node.value = previous[id];
      }
      // A context (or a manager) holds exactly one of the "replace"
      // component types: adding one replaces the current instance.
      if (REPLACE_TYPES.has(type)) {
        const current = (parent.children || []).find((c) => c.type === type);
        if (current) {
          noteWrap.append(el('p', { class: 'muted' },
              'Replaces the current ' + type + ' (' + (current.name || current.className) + ').'));
        }
      }
      if (type === 'resource') {
        noteWrap.append(el('p', { class: 'muted' },
            'The options of a first party factory are shown as fields; further parameters can be edited in the entry detail.'));
      }
      if (type === 'upgradeProtocol') {
        noteWrap.append(el('p', { class: 'muted' },
            'The default class is the HTTP/2 upgrade protocol. '
            + 'The protocol only becomes active when the connector is restarted.'));
      }
    }
    // The factory options of a resource depend on the (effective)
    // factory; re-render when the user leaves those fields.
    fieldsWrap.addEventListener('change', (e) => {
      if (select.value === 'resource'
              && (e.target.id === 'c-factory' || e.target.id === 'c-type')) {
        renderFields();
      }
    });
    select.addEventListener('change', renderFields);
    renderFields();

    const body = el('div', {},
        el('div', { class: 'field' }, el('label', {}, 'Component type'), select),
        fieldsWrap,
        noteWrap);

    modal({
      title: 'Add ' + parent.type + ' child',
      content: body,
      actions: [
        { label: 'Cancel' },
        {
          label: 'Add',
          class: 'btn-primary',
          onClick: async (close) => {
            const type = select.value;
            const bodyObj = { parent: parent.id, type };
            for (const [key, id] of childFieldIds(type, currentCtx(type))) {
              const node = document.getElementById(id);
              if (!node) continue;
              if (node.type === 'checkbox') {
                if (node.checked) assignField(bodyObj, key, true);
              } else {
                const value = node.value.trim();
                // Empty fields (and the "(default)" placeholder of the
                // certificate type select) are omitted from the payload.
                if (value === '' || value === '(default)') continue;
                assignField(bodyObj, key, value);
              }
            }
            try {
              const res = await api('POST', '/api/config/child', bodyObj);
              toast(res.message, 'ok');
              close();
              await loadTree();
              selectNode(parent.id);
            } catch (err) {
              toast(err.message, 'error');
            }
          },
        },
      ],
    });
  }

  const CERT_TYPES = ['(default)', 'RSA', 'DSA', 'EC', 'MLDSA'];

  // The fields shared by a certificate: the type plus the keystore
  // location (and, for PEM files, the individual file paths).
  function certificateFields() {
    return [
      ['Certificate type', select('c-cert-type', CERT_TYPES)],
      ['Keystore file', input('c-cert-file', 'text', 'conf/keystore.p12'), true],
      ['Keystore password', input('c-cert-pass', 'password', 'changeit')],
      ['Key alias', input('c-cert-alias', 'text', 'tomcat')],
      ['Keystore type', input('c-cert-storetype', 'text', 'PKCS12')],
    ];
  }

  function certificateFieldIds() {
    return [
      ['type', 'c-cert-type'],
      ['certificateKeystoreFile', 'c-cert-file'],
      ['certificateKeystorePassword', 'c-cert-pass'],
      ['certificateKeyAlias', 'c-cert-alias'],
      ['certificateKeystoreType', 'c-cert-storetype'],
    ];
  }

  // One form field per option of the effective factory of a new
  // resource (the first party factories with a closed set of options).
  function resourceOptionFields(jndiType, factory) {
    const options = factoryOptions(jndiType, factory);
    if (!options) return { fields: [], ids: [] };
    const fields = [];
    const ids = [];
    for (const spec of options) {
      const sep = spec.lastIndexOf(':');
      const name = sep < 0 ? spec : spec.slice(0, sep);
      const kind = sep < 0 ? 'text' : spec.slice(sep + 1);
      const id = 'c-param-' + name;
      if (kind === 'b') {
        fields.push([name, checkbox(id)]);
        ids.push(['params.' + name, id]);
      } else {
        fields.push([name, input(id, 'text', kind === 'i' || kind === 'l' ? 'number' : '')]);
        ids.push(['params.' + name, id]);
      }
    }
    return { fields, ids };
  }

  // The form fields of the add dialog of a child type. ctx carries the
  // values of the fields a form depends on (the JNDI type and factory
  // of a resource).
  function childFields(type, ctx) {
    switch (type) {
      case 'service':
        return [['Name', input('c-name', 'text', 'Catalina2')]];
      case 'resource': {
        const fields = [
          ['JNDI name', input('c-name', 'text', 'jdbc/MyDB')],
          ['Type', input('c-type', 'text', 'javax.sql.DataSource')],
          ['Factory', input('c-factory', 'text', BASIC_DATA_SOURCE_FACTORY), true],
          ['Auth', input('c-auth', 'text', 'Container')],
        ];
        fields.push(...resourceOptionFields(ctx && ctx.jndiType, ctx && ctx.factory).fields);
        return fields;
      }
      case 'resourceLink':
        return [
          ['JNDI name', input('c-name', 'text', 'jdbc/MyDB')],
          ['Type', input('c-type', 'text', 'javax.sql.DataSource')],
          ['Global JNDI name', input('c-global', 'text', 'jdbc/MyGlobalDB')],
          ['Factory', input('c-factory', 'text', ''), true],
        ];
      case 'resourceEnvRef':
        return [
          ['JNDI name', input('c-name', 'text', 'jdbc/MyDB')],
          ['Type', input('c-type', 'text', 'javax.sql.DataSource')],
        ];
      case 'environment':
        return [
          ['JNDI name', input('c-name', 'text', 'mail/Session')],
          ['Type', input('c-type', 'text', 'java.lang.String')],
          ['Value', input('c-value', 'text', '')],
        ];
      case 'ejb':
        return [
          ['JNDI name', input('c-name', 'text', 'ejb/MyBean')],
          ['Type (home interface)', input('c-type', 'text', 'org.example.MyBeanHome')],
          ['Link', input('c-link', 'text', '')],
        ];
      case 'localEjb':
        return [
          ['JNDI name', input('c-name', 'text', 'ejb/MyBean')],
          ['Type (local home)', input('c-type', 'text', 'org.example.MyBeanLocalHome')],
          ['Local (business interface)', input('c-local', 'text', '')],
          ['Link', input('c-link', 'text', '')],
        ];
      case 'serviceRef':
        return [
          ['JNDI name', input('c-name', 'text', 'service/MyService')],
          ['Type (service interface)', input('c-type', 'text', 'org.example.MyService')],
          ['Display name', input('c-displayname', 'text', '')],
        ];
      case 'host':
        return [
          ['Name', input('c-name', 'text', 'example.com')],
          ['Aliases (comma separated)', input('c-aliases', 'text', 'www.example.com')],
          ['App base', input('c-appbase', 'text', 'webapps/example.com'), true],
        ];
      case 'context':
        return [
          ['Path', input('c-path', 'text', '/myapp')],
          ['Display name', input('c-display', 'text', '')],
          ['Doc base', input('c-docbase', 'text', 'relative to the host app base, or a .war'), true],
        ];
      case 'wrapper':
        return [
          ['Name', input('c-name', 'text', 'myservlet')],
          ['Servlet class', input('c-servlet', 'text', 'org.example.MyServlet'), true],
          ['URL patterns (comma separated)', input('c-urlpats', 'text', '/hello, /hi'), true],
        ];
      case 'valve':
        return [['Class name', input('c-class', 'text', 'org.apache.catalina.valves.AccessLogValve'), true]];
      case 'listener':
        return [['Class name', input('c-class', 'text', 'org.apache.catalina.mbeans.GlobalResourcesLifecycleListener'), true]];
      case 'cluster':
        return [['Class name', input('c-class', 'text', 'org.apache.catalina.ha.tcp.SimpleTcpCluster'), true]];
      case 'clusterValve':
        return [['Class name', input('c-class', 'text', 'org.apache.catalina.ha.tcp.ReplicationValve'), true]];
      case 'channel':
        return [['Class name', input('c-class', 'text', 'org.apache.catalina.tribes.group.GroupChannel'), true]];
      case 'membership':
        return [['Class name', input('c-class', 'text', 'org.apache.catalina.tribes.membership.McastService'), true]];
      case 'sender':
        return [['Class name', input('c-class', 'text', 'org.apache.catalina.tribes.transport.ReplicationTransmitter'), true]];
      case 'receiver':
        return [['Class name', input('c-class', 'text', 'org.apache.catalina.tribes.transport.nio.NioReceiver'), true]];
      case 'interceptor':
        return [['Class name', input('c-class', 'text', 'org.apache.catalina.tribes.group.interceptors.MessageDispatchInterceptor'), true]];
      case 'deployer':
        return [['Class name', input('c-class', 'text', 'org.apache.catalina.ha.deploy.FarmWarDeployer'), true]];
      case 'clusterManager':
        return [['Class name', input('c-class', 'text', 'org.apache.catalina.ha.session.DeltaManager'), true]];
      case 'transport':
        return [['Class name', input('c-class', 'text', 'org.apache.catalina.tribes.transport.nio.PooledParallelSender'), true]];
      case 'clusterListener':
        return [['Class name', input('c-class', 'text', 'org.apache.catalina.ha.session.ClusterSessionListener'), true]];
      case 'realm':
        return [['Class name', input('c-class', 'text', 'org.apache.catalina.realm.UserDatabaseRealm'), true]];
      case 'manager':
        return [['Class name', input('c-class', 'text', 'org.apache.catalina.session.StandardManager'), true]];
      case 'sessionIdGenerator':
        return [['Class name', input('c-class', 'text', 'org.apache.catalina.util.StandardSessionIdGenerator'), true]];
      case 'resources':
        return [['Class name', input('c-class', 'text', 'org.apache.catalina.webresources.StandardRoot'), true]];
      case 'loader':
        return [['Class name', input('c-class', 'text', 'org.apache.catalina.loader.WebappLoader'), true]];
      case 'cookieProcessor':
        return [['Class name', input('c-class', 'text', 'org.apache.tomcat.util.http.Rfc6265CookieProcessor'), true]];
      case 'connector':
        return [
          ['Protocol', input('c-protocol', 'text', 'HTTP/1.1')],
          ['Port', input('c-port', 'text', '8081')],
        ];
      case 'executor':
        return [
          ['Name', input('c-name', 'text', 'tomcatThreadPool')],
          ['Max threads', input('c-maxthreads', 'text', '150')],
          ['Min spare threads', input('c-minspare', 'text', '4')],
        ];
      case 'alias':
        return [['Alias', input('c-alias', 'text', 'www.example.com')]];
      case 'sslHostConfig':
        return [
          ['Host name', input('c-hostname', 'text', '_default_'), true],
          ...certificateFields(),
        ];
      case 'certificate':
        return certificateFields();
      case 'upgradeProtocol':
        return [['Class name', input('c-class', 'text', 'org.apache.coyote.http2.Http2Protocol'), true]];
      default:
        return [];
    }
  }

  function childFieldIds(type, ctx) {
    switch (type) {
      case 'service': return [['name', 'c-name']];
      case 'resource': {
        const ids = [
          ['name', 'c-name'],
          ['jndiType', 'c-type'],
          ['factory', 'c-factory'],
          ['auth', 'c-auth'],
        ];
        ids.push(...resourceOptionFields(ctx && ctx.jndiType, ctx && ctx.factory).ids);
        return ids;
      }
      case 'resourceLink':
        return [['name', 'c-name'], ['jndiType', 'c-type'], ['global', 'c-global'], ['factory', 'c-factory']];
      case 'resourceEnvRef':
        return [['name', 'c-name'], ['jndiType', 'c-type']];
      case 'environment':
        return [['name', 'c-name'], ['jndiType', 'c-type'], ['value', 'c-value']];
      case 'ejb':
        return [['name', 'c-name'], ['jndiType', 'c-type'], ['link', 'c-link']];
      case 'localEjb':
        return [['name', 'c-name'], ['jndiType', 'c-type'], ['local', 'c-local'], ['link', 'c-link']];
      case 'serviceRef':
        return [['name', 'c-name'], ['jndiType', 'c-type'], ['displayname', 'c-displayname']];
      case 'host': return [['name', 'c-name'], ['aliases', 'c-aliases'], ['appBase', 'c-appbase']];
      case 'context': return [['path', 'c-path'], ['displayName', 'c-display'], ['docBase', 'c-docbase']];
      case 'wrapper': return [['name', 'c-name'], ['servletClass', 'c-servlet'], ['urlPatterns', 'c-urlpats']];
      case 'valve': return [['className', 'c-class']];
      case 'listener': return [['className', 'c-class']];
      case 'cluster': return [['className', 'c-class']];
      case 'clusterValve': return [['className', 'c-class']];
      case 'channel': return [['className', 'c-class']];
      case 'membership': return [['className', 'c-class']];
      case 'sender': return [['className', 'c-class']];
      case 'receiver': return [['className', 'c-class']];
      case 'interceptor': return [['className', 'c-class']];
      case 'deployer': return [['className', 'c-class']];
      case 'clusterManager': return [['className', 'c-class']];
      case 'transport': return [['className', 'c-class']];
      case 'clusterListener': return [['className', 'c-class']];
      case 'realm': return [['className', 'c-class']];
      case 'manager': return [['className', 'c-class']];
      case 'sessionIdGenerator': return [['className', 'c-class']];
      case 'resources': return [['className', 'c-class']];
      case 'loader': return [['className', 'c-class']];
      case 'cookieProcessor': return [['className', 'c-class']];
      case 'connector': return [['protocol', 'c-protocol'], ['port', 'c-port']];
      case 'executor': return [['name', 'c-name'], ['maxThreads', 'c-maxthreads'], ['minSpareThreads', 'c-minspare']];
      case 'alias': return [['alias', 'c-alias']];
      case 'sslHostConfig': {
        // The initial certificate is nested under the 'certificate'
        // object of the request body.
        const ids = [['hostName', 'c-hostname']];
        for (const [key, id] of certificateFieldIds()) ids.push(['certificate.' + key, id]);
        return ids;
      }
      case 'certificate': return certificateFieldIds();
      case 'upgradeProtocol': return [['className', 'c-class']];
      default: return [];
    }
  }

  function input(id, type, placeholder) {
    return el('input', { id, type, placeholder: placeholder || '', class: 'config-input' });
  }

  function checkbox(id) {
    return el('input', { id, type: 'checkbox', class: 'config-check' });
  }

  function select(id, options) {
    const node = el('select', { id, class: 'config-input' });
    for (const option of options) {
      node.append(el('option', { value: option }, option));
    }
    return node;
  }

  // Assign a value to a (possibly nested) key of the request payload:
  // 'certificate.type' becomes bodyObj.certificate.type.
  function assignField(bodyObj, key, value) {
    const parts = key.split('.');
    let target = bodyObj;
    for (let i = 0; i < parts.length - 1; i++) {
      if (typeof target[parts[i]] !== 'object' || target[parts[i]] === null) {
        target[parts[i]] = {};
      }
      target = target[parts[i]];
    }
    target[parts[parts.length - 1]] = value;
  }

  // ============================ Remove ===============================

  async function removeNode(d) {
    const label = d.name || d.type;
    const ok = await confirm({
      title: 'Remove ' + d.type,
      message: 'Remove ' + label + ' from the running server? This cannot be undone without a reload.',
      confirmLabel: 'Remove',
      danger: true,
      requireText: label,
    });
    if (!ok) return;
    try {
      const res = await api('DELETE', '/api/config/child', { id: d.id, confirm: label });
      toast(res.message, 'ok');
      selectedId = null;
      selectedDetail = null;
      clear(detailCard);
      renderEmpty();
      await loadTree();
    } catch (err) {
      toast(err.message, 'error');
    }
  }

  function renderEmpty() {
    detailCard.append(el('div', { class: 'empty' },
        el('p', {}, 'Select a component in the tree to inspect and edit it.')));
  }

  // ============================ Lifecycle =============================

  // The states that count as "running" for a component. A context
  // reports STARTED while it is accepting requests; AVAILABLE is
  // accepted as well (some components report it instead).
  function isRunning(d) {
    return d.state === 'STARTED' || d.state === 'AVAILABLE';
  }

  // The Start / Stop / Restart actions of the detail card, for the
  // components that implement Lifecycle (the node detail reports this as
  // `lifecycle`). Not every change takes effect until the affected
  // component is restarted, so the actions make the restart explicit.
  // Start and Stop are disabled for the components that affect access
  // to this page (`affectsSelf`): stopping them would destroy the admin
  // session mid-request. Restart stays enabled for them: the client's
  // connection may be interrupted during the operation, but the
  // component is running again at the end and the client reconnects.
  // Returned as action descriptors (see ui.js actionMenu): they render
  // inline on wide screens and inside the overflow menu on narrow ones.
  function lifecycleActions(d) {
    if (!d.lifecycle) return [];
    const running = isRunning(d);
    const selfImpact = d.affectsSelf;
    const selfImpactTitle = 'This component serves this page: starting or stopping it would interrupt access to the manager. Use Restart instead.';
    return [
      {
        label: 'Start',
        disabled: running || selfImpact,
        title: selfImpact ? selfImpactTitle : 'Start',
        onclick: () => lifecycleOp(d, 'start'),
      },
      {
        label: 'Stop',
        disabled: !running || selfImpact,
        title: selfImpact ? selfImpactTitle : 'Stop',
        onclick: () => lifecycleOp(d, 'stop'),
      },
      {
        label: 'Restart',
        title: 'Stop the component and start it again',
        onclick: () => lifecycleOp(d, 'restart'),
      },
    ];
  }

  async function lifecycleOp(d, op) {
    const label = d.name || d.type;
    let ok;
    if (op === 'start') {
      // Starting a stopped component is safe: no confirmation.
      ok = true;
    } else if (op === 'stop') {
      ok = await confirm({
        title: 'Stop ' + d.type,
        message: 'Stop ' + label + '? Any in-memory state it holds (e.g. the sessions of the contexts below it) is lost.',
        confirmLabel: 'Stop',
        danger: false,
      });
    } else {
      ok = await confirm({
        title: 'Restart ' + d.type,
        message: 'Restart ' + label + '?' + (d.affectsSelf
            ? ' This component serves this page: the connection is interrupted during the operation and the page reconnects when it is done. When the restarted component holds the admin sessions (the server, a service, an engine, a host or this context) you will need to sign in again.'
            : ''),
        confirmLabel: 'Restart',
        danger: true,
        requireText: label,
      });
    }
    if (!ok) return;
    let res;
    try {
      res = await api('POST', '/api/config/lifecycle', { id: d.id, op });
    } catch (err) {
      // A network-level failure (fetch rejects with a TypeError) means
      // the connection was interrupted mid-operation: what is expected
      // when the component that serves this page itself is restarted.
      // The operation may well have completed server-side; try to
      // reconnect.
      if (err && err.name === 'TypeError') {
        await reconnectAfterLifecycle();
      } else {
        toast(err.message, 'error');
      }
      return;
    }
    toast(res.message, 'ok');
    await loadTree();
    if (selectedId) {
      selectNode(selectedId);
    } else {
      renderEmpty();
    }
  }

  // The connection was interrupted during a lifecycle operation (the
  // component that serves this page - the server, a service, an engine,
  // the host, the connector or this context - was restarted and its
  // start phase has not necessarily finished yet). Wait for the server
  // to come back and reconnect: a read-only API call re-establishes the
  // CSRF token; when the admin session was reset by the restart, api()
  // navigates to the login page, and a successful login returns to this
  // page.
  async function reconnectAfterLifecycle() {
    toast('The connection was interrupted during the operation - this is expected when the component that serves this page is restarted. Reconnecting...', 'info', 8000);
    for (let attempt = 0; attempt < 10; attempt++) {
      await sleep(1000);
      try {
        await api('GET', '/api/csrf');
        toast('Reconnected. Reloading the components.', 'ok');
        await loadTree();
        if (selectedId) {
          selectNode(selectedId);
        } else {
          renderEmpty();
        }
        return;
      } catch (err) {
        // api() has already navigated to the login page.
        if (err && err.message === 'unauthenticated') return;
      }
    }
    toast('Could not reconnect after the operation. The component may still be stopped - check the server status and try again.', 'error', 10000);
  }

  const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

  // ============================ Save to server.xml ===================

  async function saveToServerXml() {
    let data;
    try {
      data = await api('GET', '/api/config/store/preview');
    } catch (err) {
      toast(err.message, 'error');
      return;
    }
    const xml = data.xml;
    const files = data.files || [];
    const pre = el('pre', { class: 'config-xml' }, xml);
    const input = el('input', { type: 'text', autocomplete: 'off', placeholder: 'server.xml', class: 'config-input' });
    const body = el('div', {},
        el('p', { style: 'margin-top:0;color:var(--text-soft)' },
            'This will overwrite conf/server.xml with the live state (a timestamped backup is kept).'));
    if (files.length) {
      body.append(el('p', { style: 'color:var(--text-soft)' },
          'It will also rewrite the following context configuration files:'),
          el('ul', { class: 'config-file-list' },
              files.map((f) => el('li', {}, el('code', {}, f)))));
    }
    body.append(
        el('div', { class: 'field', style: 'margin-bottom:12px' },
            el('label', {}, 'Type ', el('code', {}, 'server.xml'), ' to confirm'), input),
        pre);
    if (data.restartsManager) {
      body.append(el('div', { class: 'config-store-warning' },
          el('strong', {}, 'Warning: '),
          'the file of the context this manager runs in is among them. Saving will restart the manager and reset your session - you will need to log in again.'));
    }
    modal({
      title: 'Save to server.xml',
      wide: true,
      content: body,
      actions: [
        { label: 'Cancel' },
        {
          label: 'Save',
          class: 'btn-primary',
          onClick: async (close) => {
            if (input.value.trim() !== 'server.xml') {
              input.focus();
              return;
            }
            try {
              const res = await api('POST', '/api/config/store', {});
              toast(res.message, 'ok');
              close();
            } catch (err) {
              toast(err.message, 'error');
            }
          },
        },
      ],
    });
  }

  // ============================ Misc =================================

  async function reloadAll() {
    await loadTree();
    if (selectedId) {
      selectNode(selectedId);
    } else {
      renderEmpty();
    }
  }

  await loadTree();
  renderEmpty();
  return null;
}
