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

// Only these stages are doing real work; R (ready), K (keep-alive) and '?'
// are idle one way or another and are left out of the table.
const ACTIVE_STAGES = new Set(['P', 'S', 'F']);
const STAGE_BADGES = { P: 'info', S: 'ok', F: 'warn' };

function pctText(fraction) {
  return fraction == null ? '-' : (fraction * 100).toFixed(1) + ' %';
}

function usageBar(fraction) {
  const bar = el('div', { class: 'progress' }, el('div'));
  const fill = bar.firstChild;
  if (fraction != null) {
    fill.style.width = Math.min(100, Math.max(0, fraction * 100)) + '%';
    if (fraction >= 0.9) bar.classList.add('danger');
    else if (fraction >= 0.75) bar.classList.add('warn');
  }
  return bar;
}

function metric(label, value, fraction) {
  return el('div', { class: 'sys-metric' },
      el('div', { class: 'sys-metric-head' },
          el('span', { class: 'sys-label' }, label),
          el('span', { class: 'sys-value' }, value)),
      usageBar(fraction));
}

function fact(label, value) {
  return el('div', { class: 'sys-fact' },
      el('span', { class: 'sys-fact-label' }, label),
      el('span', { class: 'sys-fact-value' }, value));
}

export async function monitoring(container) {
  const view = el('div', {},
      el('div', { class: 'page-head' },
          el('h1', {}, 'Monitoring'),
          el('p', {}, 'Instant CPU and memory snapshot, live connectors and worker (socket) table. ' +
              'Refreshes every 5 seconds; paused while the tab is hidden.')));
  container.append(view);

  const sysGrid = el('div', { class: 'grid charts' });
  const cpuCard = el('div', { class: 'card col-6' },
      el('div', { class: 'card-title-row' },
          el('h3', {}, el('span', { class: 'live-dot' }), 'CPU')),
      el('div', { class: 'sys-body' }));
  const cpuBody = cpuCard.querySelector('.sys-body');
  const memoryCard = el('div', { class: 'card col-6' },
      el('div', { class: 'card-title-row' },
          el('h3', {}, el('span', { class: 'live-dot' }), 'Memory')),
      el('div', { class: 'sys-body' }));
  const memoryBody = memoryCard.querySelector('.sys-body');
  sysGrid.append(cpuCard, memoryCard);
  view.append(sysGrid);

  const connectorsCard = el('div', { class: 'card' },
      el('div', { class: 'card-title-row' },
          el('h3', {}, el('span', { class: 'live-dot' }), 'Connectors')),
      el('div', { class: 'table-wrap stackable' }));
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

    try {
      const sys = await get('/api/status/system');
      renderCpu(sys.cpu);
      renderMemory(sys.memory);
    } catch (err) {
      // The cards keep the values of the previous snapshot.
    }

    let workers;
    try {
      workers = await get('/api/status/workers');
    } catch (err) {
      return;
    }
    renderWorkers(workers);
  }

  function renderCpu(cpu) {
    clear(cpuBody);
    if (!cpu) {
      cpuBody.append(el('div', { class: 'empty' }, 'Not available'));
      return;
    }
    cpuBody.append(
        metric('System CPU', pctText(cpu.systemLoad), cpu.systemLoad),
        metric('JVM process CPU', pctText(cpu.processLoad), cpu.processLoad),
        el('div', { class: 'sys-facts' },
            fact('Cores', String(cpu.availableProcessors)),
            fact('Load average', cpu.loadAverage != null ? cpu.loadAverage.toFixed(2) : '-'),
            fact('Threads', cpu.threads + ' live'),
            fact('Daemon threads', String(cpu.daemonThreads)),
            fact('Peak threads', String(cpu.peakThreads))));
  }

  function renderMemory(mem) {
    clear(memoryBody);
    if (!mem) {
      memoryBody.append(el('div', { class: 'empty' }, 'Not available'));
      return;
    }
    const heap = mem.heap || {};
    const nonHeap = mem.nonHeap || {};
    memoryBody.append(
        mem.physical && mem.physical.total > 0
            ? metric('Physical memory',
                formatBytes(mem.physical.total - mem.physical.free) + ' / ' +
                formatBytes(mem.physical.total),
                (mem.physical.total - mem.physical.free) / mem.physical.total)
            : metric('Physical memory', '-', null),
        mem.swap && mem.swap.total > 0
            ? metric('Swap',
                formatBytes(mem.swap.total - mem.swap.free) + ' / ' + formatBytes(mem.swap.total),
                (mem.swap.total - mem.swap.free) / mem.swap.total)
            : metric('Swap', 'none', null),
        heap.max > 0
            ? metric('JVM heap', formatBytes(heap.used) + ' / ' + formatBytes(heap.max),
                heap.used / heap.max)
            : metric('JVM heap', formatBytes(heap.used) + ' (unbounded)', null));
    memoryBody.append(el('div', { class: 'sys-facts' },
        fact('Heap committed', formatBytes(heap.committed)),
        fact('Non-heap used', formatBytes(nonHeap.used))));

    const pools = mem.pools || [];
    if (pools.length > 0) {
      const rows = pools.map((p) => el('tr', {},
          el('td', {}, el('strong', {}, p.name)),
          el('td', { class: 'num' }, formatBytes(p.used)),
          el('td', { class: 'num' }, formatBytes(p.committed)),
          el('td', { class: 'num' }, p.max >= 0 ? formatBytes(p.max) : '-')));
      memoryBody.append(el('div', { class: 'table-wrap' },
          el('table', { class: 'data' },
              el('thead', {}, el('tr', {},
                  el('th', {}, 'Pool'),
                  el('th', {}, 'Used'),
                  el('th', {}, 'Committed'),
                  el('th', {}, 'Max'))),
              el('tbody', {}, rows))));
    }
  }

  function renderConnectors(connectors) {
    clear(connectorsWrap);
    const rows = connectors.map((c) => el('tr', {},
        el('td', { 'data-label': 'Connector' }, el('strong', {}, c.name)),
        el('td', { class: 'num', 'data-label': 'Threads' }, c.threads.busy + ' / ' + c.threads.current + ' / ' + c.threads.max),
        el('td', { class: 'num', 'data-label': 'Keep-alive' }, String(c.threads.keepAlive)),
        c.requests
            ? el('td', { class: 'num', 'data-label': 'Processing time' }, formatMs(c.requests.processingTime) + ' (max ' + formatMs(c.requests.maxTime) + ')')
            : el('td', { 'data-label': 'Processing time' }, '-'),
        el('td', { class: 'num', 'data-label': 'Requests' }, String(c.requests ? c.requests.count : '-')),
        el('td', { class: 'num', 'data-label': 'Errors' }, String(c.requests ? c.requests.errors : '-')),
        el('td', { class: 'num', 'data-label': 'Bytes in' }, c.requests ? formatBytes(c.requests.bytesReceived) : '-'),
        el('td', { class: 'num', 'data-label': 'Bytes out' }, c.requests ? formatBytes(c.requests.bytesSent) : '-')));
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
    const active = workers.filter((w) => ACTIVE_STAGES.has(w.stage));
    const countBadge = document.getElementById('worker-count');
    if (countBadge) {
      countBadge.textContent = active.length + ' active · ' +
          (workers.length - active.length) + ' idle';
    }
    const rows = active.map((w) => el('tr', {},
        el('td', {},
            el('span', { class: 'badge ' + (STAGE_BADGES[w.stage] || 'plain') },
                w.stage + ' · ' + (STAGE_LABELS[w.stage] || ''))),
        el('td', { class: 'num' }, w.time != null ? formatMs(w.time) : '-'),
        el('td', { class: 'num' }, w.bytesSent != null ? formatBytes(w.bytesSent) : '-'),
        el('td', { class: 'num' }, w.bytesReceived != null ? formatBytes(w.bytesReceived) : '-'),
        el('td', {}, el('code', {},
            (w.remoteAddrForwarded ? w.remoteAddrForwarded + ' (' + w.remoteAddr + ')' : (w.remoteAddr || '-')))),
        el('td', {}, w.virtualHost || '-'),
        el('td', { class: 'wide' },
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
