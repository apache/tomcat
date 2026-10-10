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

import { get } from '../api.js';
import { el, clear, formatDuration, formatMs, table } from '../ui.js';
import { t, numberFormatter } from '../i18n.js';

const POLL_MS = 10000;

function shortClassName(name) {
  if (!name) return '-';
  const dot = name.lastIndexOf('.');
  return dot >= 0 ? name.substring(dot + 1) : name;
}

function fact(label, value) {
  const node = el('div', { class: 'sys-fact' },
      el('span', { class: 'sys-fact-label' }, label));
  const valueNode = el('span', { class: 'sys-fact-value' });
  if (value !== null && value !== undefined && value.nodeType) {
    valueNode.append(value);
  } else {
    valueNode.textContent = value === null || value === undefined ? '-' : String(value);
  }
  node.append(valueNode);
  return node;
}

function stateBadge(state) {
  const running = state === 'STARTED';
  return el('span', { class: 'badge ' + (running ? 'ok' : 'stop') }, state || '-');
}

function memberStatus(member) {
  if (member.local) return el('span', { class: 'badge info' }, t('manager2.ui.cluster.statusThisNode'));
  if (member.failing) return el('span', { class: 'badge danger' }, t('manager2.ui.cluster.statusFailing'));
  if (member.suspect) return el('span', { class: 'badge warn' }, t('manager2.ui.cluster.statusSuspect'));
  if (member.ready) return el('span', { class: 'badge ok' }, t('manager2.ui.cluster.statusReady'));
  return el('span', { class: 'badge stop' }, t('manager2.ui.cluster.statusNotReady'));
}

function portOrDash(value) {
  return value >= 0 ? String(value) : '-';
}

function lastSendText(lastSendTime) {
  if (!lastSendTime) return '-';
  const age = Date.now() - lastSendTime;
  if (age < 1000) return t('manager2.ui.cluster.lastSendNow');
  return t('manager2.ui.cluster.lastSendAgo', formatDuration(age));
}

function clusterCard(cluster) {
  const card = el('div', { class: 'card' });
  card.append(el('div', { class: 'card-title-row' },
      el('h3', {}, el('span', { class: 'live-dot' }),
          cluster.name || t('manager2.ui.cluster.unnamed')),
      stateBadge(cluster.state)));

  card.append(el('div', { class: 'sys-facts' },
      fact(t('manager2.ui.cluster.owner'), cluster.owner),
      fact(t('manager2.ui.cluster.class'), shortClassName(cluster.className)),
      fact(t('manager2.ui.cluster.sendMode'), cluster.sendOptions)));

  const membership = cluster.membership;
  let membershipText;
  if (membership) {
    membershipText = shortClassName(membership.className);
    if (membership.mcastAddr) {
      membershipText += ' · ' + membership.mcastAddr + ':' + membership.mcastPort;
    }
  } else {
    membershipText = '-';
  }
  card.append(el('div', { class: 'sys-facts' },
      fact(t('manager2.ui.cluster.membershipService'), membershipText),
      fact(t('manager2.ui.cluster.localMember'),
          cluster.localMember || t('manager2.ui.cluster.noLocalMember'))));

  const members = cluster.members || [];
  // Membership pings are only counted by the multicast service; under static
  // membership (or a custom Member implementation) the column carries no
  // information and stays hidden.
  const hasPings = members.some((m) => typeof m.msgs === 'number' && m.msgs > 0);
  const columns = [
    { key: 'name', label: t('manager2.ui.cluster.col.member'),
      render: (m) => el('code', {}, m.name) },
    { key: 'host', label: t('manager2.ui.col.host') },
    { key: 'port', label: t('manager2.ui.cluster.col.tcpPort'), numeric: true,
      render: (m) => portOrDash(m.port) },
    { key: 'securePort', label: t('manager2.ui.cluster.col.securePort'), numeric: true,
      render: (m) => portOrDash(m.securePort) },
    { key: 'udpPort', label: t('manager2.ui.cluster.col.udpPort'), numeric: true,
      render: (m) => portOrDash(m.udpPort) },
    { key: 'status', label: t('manager2.ui.cluster.col.status'), render: (m) => memberStatus(m) },
    { key: 'alive', label: t('manager2.ui.cluster.col.alive'), numeric: true,
      render: (m) => formatDuration(m.aliveMs) },
  ];
  if (hasPings) {
    columns.push({ key: 'msgs', label: t('manager2.ui.cluster.col.pings'), numeric: true,
      render: (m) => numberFormatter().format(m.msgs || 0) });
  }
  card.append(table({ columns: columns, rows: members, stackable: true,
    empty: t('manager2.ui.cluster.noMembers') }));

  const replication = cluster.replication;
  if (replication) {
    const highlight = (value) => (value > 0
        ? el('span', { class: 'badge warn' }, numberFormatter().format(value))
        : numberFormatter().format(value));
    card.append(el('div', { class: 'card-title-row' }, el('h3', {},
        t('manager2.ui.cluster.replication'))),
        el('div', { class: 'sys-facts' },
            fact(t('manager2.ui.cluster.contexts'), numberFormatter().format(replication.contexts)),
            fact(t('manager2.ui.cluster.requests'), numberFormatter().format(replication.requests)),
            fact(t('manager2.ui.cluster.sendRequests'), numberFormatter().format(replication.sendRequests)),
            fact(t('manager2.ui.cluster.avgSendTime'),
                replication.avgSendTimeMs != null ? formatMs(replication.avgSendTimeMs) : '-'),
            fact(t('manager2.ui.cluster.lastSend'), lastSendText(replication.lastSendTime)),
            fact(t('manager2.ui.cluster.messagesSent'), numberFormatter().format(replication.messagesSent)),
            fact(t('manager2.ui.cluster.messagesReceived'), numberFormatter().format(replication.messagesReceived))),
        el('div', { class: 'sys-facts' },
            fact(t('manager2.ui.cluster.rejectedSessions'), highlight(replication.rejectedSessions)),
            fact(t('manager2.ui.cluster.duplicates'), highlight(replication.duplicates)),
            fact(t('manager2.ui.cluster.receivedQueueSize'), highlight(replication.receivedQueueSize))));
  }

  return card;
}

export async function cluster(container) {
  const view = el('div', {},
      el('div', { class: 'page-head' },
          el('h1', {}, t('manager2.ui.nav.cluster')),
          el('p', {}, t('manager2.ui.cluster.subtitle'))));
  const body = el('div', {});
  view.append(body);
  container.append(view);

  let stopped = false;

  async function tick() {
    if (stopped || document.hidden) return;

    let snap;
    try {
      snap = await get('/api/status/cluster');
    } catch (err) {
      // The page keeps the previous render.
      return;
    }

    clear(body);
    if (!snap.clustered || !snap.clusters || snap.clusters.length === 0) {
      body.append(el('div', { class: 'card' },
          el('div', { class: 'empty' }, t('manager2.ui.cluster.empty'))));
      return;
    }
    body.append(...snap.clusters.map((c) => clusterCard(c)));
  }

  const interval = setInterval(tick, POLL_MS);
  await tick();

  return () => {
    stopped = true;
    clearInterval(interval);
  };
}
