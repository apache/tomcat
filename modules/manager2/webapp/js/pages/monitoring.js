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
import { t, numberFormatter } from '../i18n.js';

const POLL_MS = 5000;

const STAGE_LABELS = {
  P: 'manager2.ui.monitoring.stage.parsing',
  S: 'manager2.ui.monitoring.stage.service',
  F: 'manager2.ui.monitoring.stage.finishing',
  R: 'manager2.ui.monitoring.stage.ready',
  K: 'manager2.ui.monitoring.stage.keepAlive',
  '?': 'manager2.ui.monitoring.stage.unknown',
};

// Only these stages are doing real work; R (ready), K (keep-alive) and '?'
// are idle one way or another and are left out of the table.
const ACTIVE_STAGES = new Set(['P', 'S', 'F']);
const STAGE_BADGES = { P: 'info', S: 'ok', F: 'warn' };

function pctText(fraction) {
  return fraction == null
      ? '-'
      : numberFormatter({ minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(fraction * 100) + ' %';
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
          el('h1', {}, t('manager2.ui.nav.monitoring')),
          el('p', {}, t('manager2.ui.monitoring.subtitle'))));
  container.append(view);

  const sysGrid = el('div', { class: 'grid charts' });
  const cpuCard = el('div', { class: 'card col-6' },
      el('div', { class: 'card-title-row' },
          el('h3', {}, el('span', { class: 'live-dot' }), t('manager2.ui.monitoring.cpu'))),
      el('div', { class: 'sys-body' }));
  const cpuBody = cpuCard.querySelector('.sys-body');
  const memoryCard = el('div', { class: 'card col-6' },
      el('div', { class: 'card-title-row' },
          el('h3', {}, el('span', { class: 'live-dot' }), t('manager2.ui.monitoring.memory'))),
      el('div', { class: 'sys-body' }));
  const memoryBody = memoryCard.querySelector('.sys-body');
  sysGrid.append(cpuCard, memoryCard);
  view.append(sysGrid);

  const connectorsCard = el('div', { class: 'card' },
      el('div', { class: 'card-title-row' },
          el('h3', {}, el('span', { class: 'live-dot' }), t('manager2.ui.monitoring.connectors'))),
      el('div', { class: 'table-wrap stackable' }));
  const connectorsWrap = connectorsCard.querySelector('.table-wrap');
  view.append(connectorsCard);

  const workersCard = el('div', { class: 'card' },
      el('div', { class: 'card-title-row' },
          el('h3', {}, el('span', { class: 'live-dot' }), t('manager2.ui.monitoring.workers')),
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
      cpuBody.append(el('div', { class: 'empty' }, t('manager2.ui.monitoring.notAvailable')));
      return;
    }
    cpuBody.append(
        metric(t('manager2.ui.monitoring.systemCpu'), pctText(cpu.systemLoad), cpu.systemLoad),
        metric(t('manager2.ui.monitoring.processCpu'), pctText(cpu.processLoad), cpu.processLoad),
        el('div', { class: 'sys-facts' },
            fact(t('manager2.ui.monitoring.cores'), String(cpu.availableProcessors)),
            fact(t('manager2.ui.monitoring.loadAverage'),
                cpu.loadAverage != null
                    ? numberFormatter({ minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(cpu.loadAverage)
                    : '-'),
            fact(t('manager2.ui.monitoring.threads'), t('manager2.ui.monitoring.threadsLive', cpu.threads)),
            fact(t('manager2.ui.monitoring.daemonThreads'), String(cpu.daemonThreads)),
            fact(t('manager2.ui.monitoring.peakThreads'), String(cpu.peakThreads))));
  }

  function renderMemory(mem) {
    clear(memoryBody);
    if (!mem) {
      memoryBody.append(el('div', { class: 'empty' }, t('manager2.ui.monitoring.notAvailable')));
      return;
    }
    const heap = mem.heap || {};
    const nonHeap = mem.nonHeap || {};
    memoryBody.append(
        mem.physical && mem.physical.total > 0
            ? metric(t('manager2.ui.monitoring.physicalMemory'),
                t('manager2.ui.monitoring.usedOfTotal',
                    formatBytes(mem.physical.total - mem.physical.free), formatBytes(mem.physical.total)),
                (mem.physical.total - mem.physical.free) / mem.physical.total)
            : metric(t('manager2.ui.monitoring.physicalMemory'), '-', null),
        mem.swap && mem.swap.total > 0
            ? metric(t('manager2.ui.monitoring.swap'),
                t('manager2.ui.monitoring.usedOfTotal',
                    formatBytes(mem.swap.total - mem.swap.free), formatBytes(mem.swap.total)),
                (mem.swap.total - mem.swap.free) / mem.swap.total)
            : metric(t('manager2.ui.monitoring.swap'), t('manager2.ui.monitoring.none'), null),
        heap.max > 0
            ? metric(t('manager2.ui.monitoring.jvmHeap'),
                t('manager2.ui.monitoring.usedOfTotal', formatBytes(heap.used), formatBytes(heap.max)),
                heap.used / heap.max)
            : metric(t('manager2.ui.monitoring.jvmHeap'),
                t('manager2.ui.monitoring.heapUnbounded', formatBytes(heap.used)), null));
    memoryBody.append(el('div', { class: 'sys-facts' },
        fact(t('manager2.ui.monitoring.heapCommitted'), formatBytes(heap.committed)),
        fact(t('manager2.ui.monitoring.nonHeapUsed'), formatBytes(nonHeap.used))));

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
                  el('th', {}, t('manager2.ui.monitoring.pool')),
                  el('th', {}, t('manager2.ui.col.used')),
                  el('th', {}, t('manager2.ui.col.committed')),
                  el('th', {}, t('manager2.ui.col.max')))),
              el('tbody', {}, rows))));
    }
  }

  function renderConnectors(connectors) {
    clear(connectorsWrap);
    const rows = connectors.map((c) => el('tr', {},
        el('td', { 'data-label': t('manager2.ui.monitoring.col.connector') }, el('strong', {}, c.name)),
        el('td', { class: 'num', 'data-label': t('manager2.ui.monitoring.col.threads') },
            c.threads.busy + ' / ' + c.threads.current + ' / ' + c.threads.max),
        el('td', { class: 'num', 'data-label': t('manager2.ui.monitoring.col.keepAlive') }, String(c.threads.keepAlive)),
        c.requests
            ? el('td', { class: 'num', 'data-label': t('manager2.ui.monitoring.col.processingTime') },
                t('manager2.ui.monitoring.processingTime', formatMs(c.requests.processingTime), formatMs(c.requests.maxTime)))
            : el('td', { 'data-label': t('manager2.ui.monitoring.col.processingTime') }, '-'),
        el('td', { class: 'num', 'data-label': t('manager2.ui.monitoring.col.requests') }, String(c.requests ? c.requests.count : '-')),
        el('td', { class: 'num', 'data-label': t('manager2.ui.monitoring.col.errors') }, String(c.requests ? c.requests.errors : '-')),
        el('td', { class: 'num', 'data-label': t('manager2.ui.monitoring.col.bytesIn') }, c.requests ? formatBytes(c.requests.bytesReceived) : '-'),
        el('td', { class: 'num', 'data-label': t('manager2.ui.monitoring.col.bytesOut') }, c.requests ? formatBytes(c.requests.bytesSent) : '-')));
    connectorsWrap.append(el('table', { class: 'data' },
        el('thead', {}, el('tr', {},
            el('th', {}, t('manager2.ui.monitoring.col.connector')),
            el('th', {}, t('manager2.ui.monitoring.col.threadsBusyCurrentMax')),
            el('th', {}, t('manager2.ui.monitoring.col.keepAlive')),
            el('th', {}, t('manager2.ui.monitoring.col.processingTime')),
            el('th', {}, t('manager2.ui.monitoring.col.requests')),
            el('th', {}, t('manager2.ui.monitoring.col.errors')),
            el('th', {}, t('manager2.ui.monitoring.col.bytesIn')),
            el('th', {}, t('manager2.ui.monitoring.col.bytesOut')))),
        el('tbody', {}, rows.length > 0 ? rows
            : el('tr', {}, el('td', { colspan: '8', class: 'empty' }, t('manager2.ui.monitoring.noConnectors'))))));
  }

  function renderWorkers(workers) {
    clear(workersWrap);
    const active = workers.filter((w) => ACTIVE_STAGES.has(w.stage));
    const countBadge = document.getElementById('worker-count');
    if (countBadge) {
      countBadge.textContent = t('manager2.ui.monitoring.workerCount',
          active.length, workers.length - active.length);
    }
    const rows = active.map((w) => el('tr', {},
        el('td', {},
            el('span', { class: 'badge ' + (STAGE_BADGES[w.stage] || 'plain') },
                w.stage + ' · ' + (STAGE_LABELS[w.stage] ? t(STAGE_LABELS[w.stage]) : ''))),
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
            el('th', {}, t('manager2.ui.monitoring.col.stage')),
            el('th', {}, t('manager2.ui.monitoring.col.time')),
            el('th', {}, t('manager2.ui.monitoring.col.sent')),
            el('th', {}, t('manager2.ui.monitoring.col.received')),
            el('th', {}, t('manager2.ui.monitoring.col.remoteAddress')),
            el('th', {}, t('manager2.ui.monitoring.col.virtualHost')),
            el('th', {}, t('manager2.ui.monitoring.col.request')))),
        el('tbody', {}, rows.length > 0 ? rows
            : el('tr', {}, el('td', { colspan: '7', class: 'empty' }, t('manager2.ui.monitoring.noWorkers'))))));
  }

  const interval = setInterval(tick, POLL_MS);
  await tick();

  return () => {
    stopped = true;
    clearInterval(interval);
  };
}
