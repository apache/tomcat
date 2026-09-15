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
import { el, clear, formatBytes, formatMs } from '../ui.js';

const POLL_MS = 5000;

const STAGE_LABELS = {
  P: 'Parsing request',
  S: 'Service',
  F: 'Finishing',
  R: 'Ready',
  K: 'Keep-alive',
  '?': 'Unknown',
};

export async function monitoring(container) {
  const view = el('div', {},
      el('div', { class: 'page-head' },
          el('h1', {}, 'Monitoring'),
          el('p', {}, 'Live worker (socket) table. Refreshes every 5 seconds; paused while the tab is hidden.')));
  container.append(view);

  const connectorsCard = el('div', { class: 'card' },
      el('div', { class: 'card-title-row' },
          el('h3', {}, el('span', { class: 'live-dot' }), 'Connectors')),
      el('div', { class: 'table-wrap' }));
  const connectorsWrap = connectorsCard.querySelector('.table-wrap');
  view.append(connectorsCard);

  const workersCard = el('div', { class: 'card' },
      el('div', { class: 'card-title-row' },
          el('h3', {}, el('span', { class: 'live-dot' }), 'Active workers'),
          el('span', { id: 'worker-count', class: 'badge plain' })),
      el('div', { class: 'table-wrap' }));
  const workersWrap = workersCard.querySelector('.table-wrap');
  view.append(workersCard);

  let stopped = false;

  async function tick() {
    if (stopped || document.hidden) return;

    let snap;
    try {
      snap = await get('/api/status');
      renderConnectors(snap.connectors);
    } catch (err) {
      return;
    }

    let workers;
    try {
      workers = await get('/api/status/workers');
    } catch (err) {
      return;
    }
    renderWorkers(workers);
  }

  function renderConnectors(connectors) {
    clear(connectorsWrap);
    const rows = connectors.map((c) => el('tr', {},
        el('td', {}, el('strong', {}, c.name)),
        el('td', { class: 'num' }, c.threads.busy + ' / ' + c.threads.current + ' / ' + c.threads.max),
        el('td', { class: 'num' }, String(c.threads.keepAlive)),
        c.requests
            ? el('td', { class: 'num' }, formatMs(c.requests.processingTime) + ' (max ' + formatMs(c.requests.maxTime) + ')')
            : el('td', {}, '-'),
        el('td', { class: 'num' }, String(c.requests ? c.requests.count : '-')),
        el('td', { class: 'num' }, String(c.requests ? c.requests.errors : '-')),
        el('td', { class: 'num' }, c.requests ? formatBytes(c.requests.bytesReceived) : '-'),
        el('td', { class: 'num' }, c.requests ? formatBytes(c.requests.bytesSent) : '-')));
    connectorsWrap.append(el('table', { class: 'data' },
        el('thead', {}, el('tr', {},
            el('th', {}, 'Connector'),
            el('th', {}, 'Threads (busy / current / max)'),
            el('th', {}, 'Keep-alive'),
            el('th', {}, 'Processing time'),
            el('th', {}, 'Requests'),
            el('th', {}, 'Errors'),
            el('th', {}, 'Bytes in'),
            el('th', {}, 'Bytes out'))),
        el('tbody', {}, rows.length > 0 ? rows
            : el('tr', {}, el('td', { colspan: '8', class: 'empty' }, 'No connectors found')))));
  }

  function renderWorkers(workers) {
    clear(workersWrap);
    const countBadge = document.getElementById('worker-count');
    if (countBadge) {
      countBadge.textContent = workers.length + ' active';
    }
    const rows = workers.map((w) => el('tr', {},
        el('td', {},
            el('span', { class: 'badge ' + (w.stage === 'S' ? 'ok' : 'plain') },
                w.stage + (w.stage === 'S' ? ' · ' + (STAGE_LABELS[w.stage] || '') : ''))),
        el('td', { class: 'num' }, w.time != null ? formatMs(w.time) : '-'),
        el('td', { class: 'num' }, w.bytesSent != null ? formatBytes(w.bytesSent) : '-'),
        el('td', { class: 'num' }, w.bytesReceived != null ? formatBytes(w.bytesReceived) : '-'),
        el('td', {}, el('code', {},
            (w.remoteAddrForwarded ? w.remoteAddrForwarded + ' (' + w.remoteAddr + ')' : (w.remoteAddr || '-')))),
        el('td', {}, w.virtualHost || '-'),
        el('td', {},
            (w.method)
                ? el('code', {}, w.method + ' ' + w.uri + (w.queryString ? '?' + w.queryString : '') + ' ' + w.protocol)
                : '-')));
    workersWrap.append(el('table', { class: 'data' },
        el('thead', {}, el('tr', {},
            el('th', {}, 'Stage'),
            el('th', {}, 'Time'),
            el('th', {}, 'Sent'),
            el('th', {}, 'Received'),
            el('th', {}, 'Remote address'),
            el('th', {}, 'Virtual host'),
            el('th', {}, 'Request'))),
        el('tbody', {}, rows.length > 0 ? rows
            : el('tr', {}, el('td', { colspan: '7', class: 'empty' }, 'No active sockets')))));
  }

  const interval = setInterval(tick, POLL_MS);
  await tick();

  return () => {
    stopped = true;
    clearInterval(interval);
  };
}
