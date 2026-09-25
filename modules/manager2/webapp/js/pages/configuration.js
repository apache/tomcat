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
import { t, has } from '../i18n.js';

// Localized display name of a component type: falls back to the raw type
// identifier when the bundle has no label for it.
function typeLabel(type) {
  const key = 'manager2.ui.type.' + type;
  return has(key) ? t(key) : type;
}

// Build a localized message with inline code spans (the {0}, ...
// placeholders are replaced by the given code values).
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
  sslHostConfig: ['certificate', 'preSharedKey'],
  certificate: [],
  preSharedKey: [],
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
          el('h1', {}, t('manager2.ui.nav.configuration')),
          el('p', {}, t('manager2.ui.config.subtitle')),
          el('span', { class: 'head-spacer' }),
          el('button', { type: 'button', class: 'btn', onclick: reloadAll }, t('manager2.ui.common.reload')),
          el('button', { type: 'button', class: 'btn btn-primary', onclick: () => saveToServerXml() }, t('manager2.ui.config.save'))));
  view.append(el('div', { class: 'config-split' },
      el('div', { class: 'card config-tree-card' }, el('div', { class: 'card-title-row' },
          el('h3', {}, t('manager2.ui.config.components')))),
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
    treeWrap.append(el('div', { class: 'spinner', role: 'status', 'aria-label': t('manager2.ui.common.loading') }));
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
          'aria-label': hasKids ? t('manager2.ui.config.toggle') : '',
          onclick: (e) => {
            e.stopPropagation();
            if (!hasKids) return;
            if (isOpen) expanded.delete(node.id); else expanded.add(node.id);
            loadTree();
          },
        }, hasKids ? (isOpen ? '\u25BC' : '\u25B6') : ''),
        el('span', { class: 'config-node-label', onclick: () => selectNode(node.id), style: 'cursor:pointer' },
            el('span', { class: 'config-type ' + node.type }, typeLabel(node.type)),
            el('span', { class: 'config-node-name' }, node.name || t('manager2.ui.config.unnamed')),
            node.self ? el('span', { class: 'badge plain', style: 'margin-left:6px' }, t('manager2.ui.config.thisApp')) : '',
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
    detailCard.append(el('div', { class: 'spinner', role: 'status', 'aria-label': t('manager2.ui.common.loading') }));
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
    if (window.matchMedia('(max-width: 900px)').matches) {
      detailCard.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }

  function renderDetail() {
    const d = selectedDetail;
    clear(detailCard);

    const actions = [];
    if (addable(d)) {
      actions.push({ label: t('manager2.ui.config.addChild'), onclick: () => addChildModal(d) });
    }
    if (d.type !== 'server') {
      actions.push({
        label: t('manager2.ui.common.remove'), class: 'btn-danger', disabled: d.self,
        title: d.self ? t('manager2.ui.config.cannotRemoveSelf') : t('manager2.ui.common.remove'),
        onclick: () => removeNode(d),
      });
    }
    actions.push(...lifecycleActions(d));
    const head = el('div', { class: 'card-title-row' },
        el('div', { class: 'config-detail-title' },
            el('span', { class: 'config-type ' + d.type }, typeLabel(d.type)),
            el('h3', {}, d.name || t('manager2.ui.config.unnamed')),
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
          el('h4', {}, t('manager2.ui.config.properties')),
          naming ? el('button', {
            type: 'button', class: 'btn btn-sm',
            onclick: () => paramForm(head, d),
          }, t('manager2.ui.config.addParameter')) : null);
      const body = rows.length
          ? el('div', { class: 'config-props' }, rows)
          : el('p', { class: 'muted' }, t('manager2.ui.config.noParameters'));
      detailCard.append(head, body);
    } else {
      detailCard.append(el('p', { class: 'muted' }, t('manager2.ui.config.noProperties')));
    }

    const kids = d.children || [];
    if (kids.length) {
      detailCard.append(el('h4', {}, t('manager2.ui.config.children')),
          el('div', { class: 'config-children' }, kids.map((k) => el('button', {
            type: 'button', class: 'config-child-chip', onclick: () => selectNode(k.id),
          }, typeLabel(k.type) + ': ' + (k.name || t('manager2.ui.config.unnamed'))))));
    }
  }

  function propRow(node, p) {
    const label = el('label', { class: 'config-prop-name' }, p.name,
        p.param ? el('span', { class: 'config-param-hint' }, t('manager2.ui.config.parameter')) : '',
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
        }, t('manager2.ui.common.apply')));
    if (p.param) {
      if (paramSet(p)) {
        edit.append(el('button', {
          type: 'button', class: 'btn btn-sm btn-danger',
          title: t('manager2.ui.config.removeParameter'),
          onclick: () => removeParameter(node, p),
        }, t('manager2.ui.common.remove')));
      } else {
        // An unset parameter: nothing to remove. Applying a value adds
        // the parameter (or, for a boolean, unchecking it is a no-op).
        edit.append(el('span', { class: 'config-param-hint' }, t('manager2.ui.config.notSet')));
      }
    }
    return el('div', { class: 'config-prop' }, label, edit);
  }

  // Whether a parameter of a JNDI entry is set (has a non empty value).
  function paramSet(p) {
    return p.value !== null && p.value !== undefined && String(p.value) !== '';
  }

  function buildInput(p) {
    const ptype = p.type;
    if (ptype === 'boolean') {
      const box = el('input', { type: 'checkbox', class: 'config-check' });
      // A parameter is stored as the string "true" or "false": only
      // "true" checks the box (Boolean("false") would be wrongly
      // truthy). A real boolean attribute is stored as an actual
      // boolean.
      box.checked = p.param ? p.value === 'true' : Boolean(p.value);
      return box;
    }
    if (NUMERIC_TYPES.has(ptype)) {
      return el('input', { type: 'text', inputmode: 'numeric', value: p.value == null ? '' : String(p.value), class: 'config-input num' });
    }
    if (ptype === '[Ljava.lang.String;') {
      const arr = Array.isArray(p.value) ? p.value : (p.value == null ? [] : [p.value]);
      return el('input', { type: 'text', value: arr.join(', '), class: 'config-input', placeholder: t('manager2.ui.config.commaSeparated') });
    }
    if (ptype === 'java.lang.String') {
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
    const ptype = p.type;
    if (ptype === 'boolean') return input.checked;
    if (NUMERIC_TYPES.has(ptype)) return input.value.trim();
    if (ptype === '[Ljava.lang.String;') {
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
      toast(t('manager2.ui.config.noChanges'), 'info');
      return;
    }
    if (RISKY_ATTRIBUTES.has(p.name)) {
      const ok = await confirm({
        title: t('manager2.ui.config.changeTitle', p.name),
        message: t('manager2.ui.config.changeMessage', p.name, node.name || typeLabel(node.type)),
        confirmLabel: t('manager2.ui.config.change'),
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
    const nameInput = el('input', { type: 'text', class: 'config-input', placeholder: t('manager2.ui.config.paramNamePlaceholder') });
    const valueInput = el('input', { type: 'text', class: 'config-input', placeholder: t('manager2.ui.config.valuePlaceholder') });
    const form = el('div', { class: 'config-param-form' },
        nameInput,
        valueInput,
        el('button', {
          type: 'button', class: 'btn btn-sm btn-primary',
          onclick: () => addParameter(node, nameInput.value.trim(), valueInput.value),
        }, t('manager2.ui.common.add')),
        el('button', { type: 'button', class: 'btn btn-sm', onclick: () => form.remove() }, t('manager2.ui.common.cancel')));
    heading.after(form);
    nameInput.focus();
  }

  // Add a generic parameter to a JNDI entry. Any parameter name is
  // accepted: the JNDI factory decides which ones it consumes at lookup
  // time.
  async function addParameter(node, name, value) {
    if (!name) {
      toast(t('manager2.ui.config.paramNameRequired'), 'error');
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
      toast(t('manager2.ui.config.paramNotSet'), 'info');
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
        types.map((ty) => el('option', { value: ty }, typeLabel(ty))));
    const fieldsWrap = el('div', { class: 'form-grid' });
    const noteWrap = el('div', {});

    function currentCtx(type) {
      if (type !== 'resource') return {};
      const typeEl = document.getElementById('c-type');
      const factoryEl = document.getElementById('c-factory');
      return { jndiType: typeEl ? typeEl.value.trim() : '', factory: factoryEl ? factoryEl.value.trim() : '' };
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
              t('manager2.ui.config.replaceNote', typeLabel(type), current.name || current.className)));
        }
      }
      if (type === 'resource') {
        noteWrap.append(el('p', { class: 'muted' },
            t('manager2.ui.config.factoryOptionsNote')));
      }
      if (type === 'upgradeProtocol') {
        noteWrap.append(el('p', { class: 'muted' },
            t('manager2.ui.config.upgradeProtocolNote')));
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
        el('div', { class: 'field' }, el('label', {}, t('manager2.ui.config.componentType')), select),
        fieldsWrap,
        noteWrap);

    modal({
      title: t('manager2.ui.config.addChildTitle', typeLabel(parent.type)),
      content: body,
      actions: [
        { label: t('manager2.ui.common.cancel') },
        {
          label: t('manager2.ui.common.add'),
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
      [t('manager2.ui.config.certType'), select('c-cert-type', CERT_TYPES)],
      [t('manager2.ui.config.keystoreFile'), input('c-cert-file', 'text', 'conf/keystore.p12'), true],
      [t('manager2.ui.config.keystorePassword'), input('c-cert-pass', 'password', 'changeit')],
      [t('manager2.ui.config.keyAlias'), input('c-cert-alias', 'text', 'tomcat')],
      [t('manager2.ui.config.keystoreType'), input('c-cert-storetype', 'text', 'PKCS12')],
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
        return [[t('manager2.ui.config.name'), input('c-name', 'text', 'Catalina2')]];
      case 'resource': {
        const fields = [
          [t('manager2.ui.config.jndiName'), input('c-name', 'text', 'jdbc/MyDB')],
          [t('manager2.ui.config.typeLabel'), input('c-type', 'text', 'javax.sql.DataSource')],
          [t('manager2.ui.config.factory'), input('c-factory', 'text', BASIC_DATA_SOURCE_FACTORY), true],
          [t('manager2.ui.config.auth'), input('c-auth', 'text', 'Container')],
        ];
        fields.push(...resourceOptionFields(ctx && ctx.jndiType, ctx && ctx.factory).fields);
        return fields;
      }
      case 'resourceLink':
        return [
          [t('manager2.ui.config.jndiName'), input('c-name', 'text', 'jdbc/MyDB')],
          [t('manager2.ui.config.typeLabel'), input('c-type', 'text', 'javax.sql.DataSource')],
          [t('manager2.ui.config.globalJndiName'), input('c-global', 'text', 'jdbc/MyGlobalDB')],
          [t('manager2.ui.config.factory'), input('c-factory', 'text', ''), true],
        ];
      case 'resourceEnvRef':
        return [
          [t('manager2.ui.config.jndiName'), input('c-name', 'text', 'jdbc/MyDB')],
          [t('manager2.ui.config.typeLabel'), input('c-type', 'text', 'javax.sql.DataSource')],
        ];
      case 'environment':
        return [
          [t('manager2.ui.config.jndiName'), input('c-name', 'text', 'mail/Session')],
          [t('manager2.ui.config.typeLabel'), input('c-type', 'text', 'java.lang.String')],
          [t('manager2.ui.config.valueLabel'), input('c-value', 'text', '')],
        ];
      case 'ejb':
        return [
          [t('manager2.ui.config.jndiName'), input('c-name', 'text', 'ejb/MyBean')],
          [t('manager2.ui.config.typeHomeInterface'), input('c-type', 'text', 'org.example.MyBeanHome')],
          [t('manager2.ui.config.link'), input('c-link', 'text', '')],
        ];
      case 'localEjb':
        return [
          [t('manager2.ui.config.jndiName'), input('c-name', 'text', 'ejb/MyBean')],
          [t('manager2.ui.config.typeLocalHome'), input('c-type', 'text', 'org.example.MyBeanLocalHome')],
          [t('manager2.ui.config.typeLocalInterface'), input('c-local', 'text', '')],
          [t('manager2.ui.config.link'), input('c-link', 'text', '')],
        ];
      case 'serviceRef':
        return [
          [t('manager2.ui.config.jndiName'), input('c-name', 'text', 'service/MyService')],
          [t('manager2.ui.config.typeServiceInterface'), input('c-type', 'text', 'org.example.MyService')],
          [t('manager2.ui.apps.displayName'), input('c-displayname', 'text', '')],
        ];
      case 'host':
        return [
          [t('manager2.ui.config.name'), input('c-name', 'text', 'example.com')],
          [t('manager2.ui.hosts.aliasesLabel'), input('c-aliases', 'text', 'www.example.com')],
          [t('manager2.ui.hosts.appBase'), input('c-appbase', 'text', 'webapps/example.com'), true],
        ];
      case 'context':
        return [
          [t('manager2.ui.config.path'), input('c-path', 'text', '/myapp')],
          [t('manager2.ui.apps.displayName'), input('c-display', 'text', '')],
          [t('manager2.ui.apps.docBase'), input('c-docbase', 'text', t('manager2.ui.config.docBasePlaceholder')), true],
        ];
      case 'wrapper':
        return [
          [t('manager2.ui.config.name'), input('c-name', 'text', 'myservlet')],
          [t('manager2.ui.config.servletClass'), input('c-servlet', 'text', 'org.example.MyServlet'), true],
          [t('manager2.ui.config.urlPatterns'), input('c-urlpats', 'text', '/hello, /hi'), true],
        ];
      case 'valve':
        return [[t('manager2.ui.config.className'), input('c-class', 'text', 'org.apache.catalina.valves.AccessLogValve'), true]];
      case 'listener':
        return [[t('manager2.ui.config.className'), input('c-class', 'text', 'org.apache.catalina.mbeans.GlobalResourcesLifecycleListener'), true]];
      case 'cluster':
        return [[t('manager2.ui.config.className'), input('c-class', 'text', 'org.apache.catalina.ha.tcp.SimpleTcpCluster'), true]];
      case 'clusterValve':
        return [[t('manager2.ui.config.className'), input('c-class', 'text', 'org.apache.catalina.ha.tcp.ReplicationValve'), true]];
      case 'channel':
        return [[t('manager2.ui.config.className'), input('c-class', 'text', 'org.apache.catalina.tribes.group.GroupChannel'), true]];
      case 'membership':
        return [[t('manager2.ui.config.className'), input('c-class', 'text', 'org.apache.catalina.tribes.membership.McastService'), true]];
      case 'sender':
        return [[t('manager2.ui.config.className'), input('c-class', 'text', 'org.apache.catalina.tribes.transport.ReplicationTransmitter'), true]];
      case 'receiver':
        return [[t('manager2.ui.config.className'), input('c-class', 'text', 'org.apache.catalina.tribes.transport.nio.NioReceiver'), true]];
      case 'interceptor':
        return [[t('manager2.ui.config.className'), input('c-class', 'text', 'org.apache.catalina.tribes.group.interceptors.MessageDispatchInterceptor'), true]];
      case 'deployer':
        return [[t('manager2.ui.config.className'), input('c-class', 'text', 'org.apache.catalina.ha.deploy.FarmWarDeployer'), true]];
      case 'clusterManager':
        return [[t('manager2.ui.config.className'), input('c-class', 'text', 'org.apache.catalina.ha.session.DeltaManager'), true]];
      case 'transport':
        return [[t('manager2.ui.config.className'), input('c-class', 'text', 'org.apache.catalina.tribes.transport.nio.PooledParallelSender'), true]];
      case 'clusterListener':
        return [[t('manager2.ui.config.className'), input('c-class', 'text', 'org.apache.catalina.ha.session.ClusterSessionListener'), true]];
      case 'realm':
        return [[t('manager2.ui.config.className'), input('c-class', 'text', 'org.apache.catalina.realm.UserDatabaseRealm'), true]];
      case 'manager':
        return [[t('manager2.ui.config.className'), input('c-class', 'text', 'org.apache.catalina.session.StandardManager'), true]];
      case 'sessionIdGenerator':
        return [[t('manager2.ui.config.className'), input('c-class', 'text', 'org.apache.catalina.util.StandardSessionIdGenerator'), true]];
      case 'resources':
        return [[t('manager2.ui.config.className'), input('c-class', 'text', 'org.apache.catalina.webresources.StandardRoot'), true]];
      case 'loader':
        return [[t('manager2.ui.config.className'), input('c-class', 'text', 'org.apache.catalina.loader.WebappLoader'), true]];
      case 'cookieProcessor':
        return [[t('manager2.ui.config.className'), input('c-class', 'text', 'org.apache.tomcat.util.http.Rfc6265CookieProcessor'), true]];
      case 'connector':
        return [
          [t('manager2.ui.config.protocol'), input('c-protocol', 'text', 'HTTP/1.1')],
          [t('manager2.ui.config.port'), input('c-port', 'text', '8081')],
        ];
      case 'executor':
        return [
          [t('manager2.ui.config.name'), input('c-name', 'text', 'tomcatThreadPool')],
          [t('manager2.ui.config.maxThreads'), input('c-maxthreads', 'text', '150')],
          [t('manager2.ui.config.minSpareThreads'), input('c-minspare', 'text', '4')],
        ];
      case 'alias':
        return [[t('manager2.ui.config.alias'), input('c-alias', 'text', 'www.example.com')]];
      case 'sslHostConfig':
        return [
          [t('manager2.ui.config.hostName'), input('c-hostname', 'text', '_default_'), true],
          ...certificateFields(),
        ];
      case 'certificate':
        return certificateFields();
      case 'preSharedKey':
        return [
          [t('manager2.ui.config.pskIdentity'), input('c-psk-identity', 'text', 'client-identity')],
          [t('manager2.ui.config.pskKey'), input('c-psk-key', 'text', t('manager2.ui.config.pskKeyPlaceholder')), true],
          [t('manager2.ui.config.pskDigest'), select('c-psk-digest', ['(default)', 'SHA256', 'SHA384'])],
        ];
      case 'upgradeProtocol':
        return [[t('manager2.ui.config.className'), input('c-class', 'text', 'org.apache.coyote.http2.Http2Protocol'), true]];
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
      case 'preSharedKey':
        return [['identity', 'c-psk-identity'], ['key', 'c-psk-key'], ['digest', 'c-psk-digest']];
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
      title: t('manager2.ui.config.removeTitle', typeLabel(d.type)),
      message: t('manager2.ui.config.removeMessage', label),
      confirmLabel: t('manager2.ui.common.remove'),
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
        el('p', {}, t('manager2.ui.config.selectComponent'))));
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
    const selfImpactTitle = t('manager2.ui.config.selfImpactTitle');
    return [
      {
        label: t('manager2.ui.common.start'),
        disabled: running || selfImpact,
        title: selfImpact ? selfImpactTitle : t('manager2.ui.common.start'),
        onclick: () => lifecycleOp(d, 'start'),
      },
      {
        label: t('manager2.ui.common.stop'),
        disabled: !running || selfImpact,
        title: selfImpact ? selfImpactTitle : t('manager2.ui.common.stop'),
        onclick: () => lifecycleOp(d, 'stop'),
      },
      {
        label: t('manager2.ui.common.restart'),
        title: t('manager2.ui.config.restartHelp'),
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
        title: t('manager2.ui.config.stopTitle', typeLabel(d.type)),
        message: t('manager2.ui.config.stopMessage', label),
        confirmLabel: t('manager2.ui.common.stop'),
        danger: false,
      });
    } else {
      ok = await confirm({
        title: t('manager2.ui.config.restartTitle', typeLabel(d.type)),
        message: d.affectsSelf
            ? t('manager2.ui.config.restartMessageSelf', label)
            : t('manager2.ui.config.restartMessage', label),
        confirmLabel: t('manager2.ui.common.restart'),
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
    toast(t('manager2.ui.config.reconnecting'), 'info', 8000);
    for (let attempt = 0; attempt < 10; attempt++) {
      await sleep(1000);
      try {
        await api('GET', '/api/csrf');
        toast(t('manager2.ui.config.reconnected'), 'ok');
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
    toast(t('manager2.ui.config.reconnectFailed'), 'error', 10000);
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
            t('manager2.ui.config.storeWarning')));
    if (files.length) {
      body.append(el('p', { style: 'color:var(--text-soft)' },
          t('manager2.ui.config.storeFiles')),
          el('ul', { class: 'config-file-list' },
              files.map((f) => el('li', {}, el('code', {}, f)))));
    }
    body.append(
        el('div', { class: 'field', style: 'margin-bottom:12px' },
            el('label', {}, ...codeNodes('manager2.ui.confirm.typeToConfirm', ['server.xml'])), input),
        pre);
    if (data.restartsManager) {
      body.append(el('div', { class: 'config-store-warning' },
          el('strong', {}, t('manager2.ui.config.warning')),
          t('manager2.ui.config.storeManagerWarning')));
    }
    modal({
      title: t('manager2.ui.config.save'),
      wide: true,
      content: body,
      actions: [
        { label: t('manager2.ui.common.cancel') },
        {
          label: t('manager2.ui.common.save'),
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
