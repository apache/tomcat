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

import { BASE, get } from '../api.js';
import { t } from '../i18n.js';
import { el, clear, formatBytes, formatRate, formatDuration, stateBadge } from '../ui.js';
import { LineChart, palette } from '../charts.js';

// Fallback poll period if the server does not (yet) report one.
const FALLBACK_TICK_MS = 2000;

/**
 * Build a card with a title and a live indicator.
 */
function chartCard(title, extra) {
  return el('div', { class: 'card chart-card' + (extra ? ' ' + extra : '') },
      el('div', { class: 'card-title-row' },
          el('h3', {}, el('span', { class: 'live-dot' }), title)),
      el('canvas'));
}

export async function dashboard(container) {
  const view = el('div', {},
      el('div', { class: 'page-head' },
          el('h1', {}, t('manager2.ui.dashboard.title')),
          el('p', {}, '')));
  const subtitle = view.querySelector('.page-head p');
  container.append(view);

  // ---------------- KPI cards ----------------
  const kpiGrid = el('div', { class: 'grid kpis' });
  const kpiRefs = {};
  function kpi(key, label) {
    const card = el('div', { class: 'card kpi' },
        el('span', { class: 'kpi-label' }, label),
        el('span', { class: 'kpi-value' }, '-'),
        el('span', { class: 'kpi-sub' }, ''));
    kpiRefs[key] = {
      value: card.querySelector('.kpi-value'),
      sub: card.querySelector('.kpi-sub'),
    };
    kpiGrid.append(card);
  }
  kpi('heap', t('manager2.ui.dashboard.kpi.heapUsed'));
  kpi('threads', t('manager2.ui.dashboard.kpi.busyThreads'));
  kpi('sessions', t('manager2.ui.dashboard.kpi.activeSessions'));
  kpi('rps', t('manager2.ui.dashboard.kpi.requestsPerSecond'));
  kpi('errors', t('manager2.ui.dashboard.kpi.errorsPerSecond'));
  view.append(kpiGrid);

  // ---------------- Charts ----------------
  const grid = el('div', { class: 'grid charts' });
  const heapCard = chartCard(t('manager2.ui.dashboard.chart.heap'), 'col-6');
  const heapCanvas = heapCard.querySelector('canvas');
  const heapChart = new LineChart(heapCanvas, {
    series: [
      { name: 'used', color: palette(0) },
      { name: 'committed', color: palette(1) },
    ],
    windowMs: 10 * 60 * 1000,
    formatValue: (v) => formatBytes(v),
  });
  heapCard.append(el('div', { class: 'chart-legend' },
      el('span', {}, el('span', { class: 'swatch', style: 'background:' + palette(0) }), t('manager2.ui.dashboard.legend.used')),
      el('span', {}, el('span', { class: 'swatch', style: 'background:' + palette(1) }), t('manager2.ui.dashboard.legend.committed'))));

  const threadsCard = chartCard(t('manager2.ui.dashboard.chart.threads'), 'col-6');
  const threadsCanvas = threadsCard.querySelector('canvas');
  const threadsChart = new LineChart(threadsCanvas, {
    series: [{ name: 'busy', color: palette(2) }],
    windowMs: 10 * 60 * 1000,
    formatValue: (v) => String(Math.round(v)),
  });

  const rateCard = chartCard(t('manager2.ui.dashboard.chart.requestRate'), 'col-6');
  const rateCanvas = rateCard.querySelector('canvas');
  const rateChart = new LineChart(rateCanvas, {
    series: [
      { name: 'requests', color: palette(1) },
      { name: 'errors', color: palette(3) },
    ],
    windowMs: 10 * 60 * 1000,
    formatValue: (v) => v.toFixed(1),
  });
  rateCard.append(el('div', { class: 'chart-legend' },
      el('span', {}, el('span', { class: 'swatch', style: 'background:' + palette(1) }), t('manager2.ui.dashboard.legend.requestsPerSecond')),
      el('span', {}, el('span', { class: 'swatch', style: 'background:' + palette(3) }), t('manager2.ui.dashboard.legend.errorsPerSecond'))));

  const bytesCard = chartCard(t('manager2.ui.dashboard.chart.network'), 'col-6');
  const bytesCanvas = bytesCard.querySelector('canvas');
  const bytesChart = new LineChart(bytesCanvas, {
    series: [
      { name: 'sent', color: palette(4) },
      { name: 'received', color: palette(5) },
    ],
    windowMs: 10 * 60 * 1000,
    formatValue: (v) => formatBytes(v),
  });
  bytesCard.append(el('div', { class: 'chart-legend' },
      el('span', {}, el('span', { class: 'swatch', style: 'background:' + palette(4) }), t('manager2.ui.dashboard.legend.sentPerSecond')),
      el('span', {}, el('span', { class: 'swatch', style: 'background:' + palette(5) }), t('manager2.ui.dashboard.legend.receivedPerSecond'))));

  grid.append(heapCard, threadsCard, rateCard, bytesCard);
  view.append(grid);

  // ---------------- Applications strip ----------------
  const appsCard = el('div', { class: 'card' },
      el('div', { class: 'card-title-row' },
          el('h3', {}, el('span', { class: 'live-dot' }), t('manager2.ui.nav.apps'))),
      el('div', { class: 'table-wrap' }));
  const appsTableWrap = appsCard.querySelector('.table-wrap');
  view.append(appsCard);

  // ---------------- Rendering ----------------
  // The charts are driven by the history the server collects in the
  // background (one sample per tick, keeping the configured window). Each
  // poll therefore renders the full window; nothing is accumulated in the
  // browser, so the charts always show the last windowMs of server activity
  // no matter when the page was opened.
  let subtitleSet = false;

  function render(data) {
    if (!subtitleSet) {
      subtitle.textContent = t('manager2.ui.dashboard.subtitle',
          formatDuration(data.windowMs), data.tickMs / 1000);
      subtitleSet = true;
    }

    const sample = data.samples.length > 0 ? data.samples[data.samples.length - 1] : null;
    if (sample) {
      kpiRefs.heap.value.textContent = formatBytes(sample.heapUsed);
      kpiRefs.heap.sub.textContent = t('manager2.ui.dashboard.of', formatBytes(sample.heapMax));
      kpiRefs.threads.value.textContent = sample.threadsBusy + ' / ' + sample.threadsMax;
      kpiRefs.sessions.value.textContent = String(sample.sessions);
      // The rates are computed by the server; the first sample has no
      // baseline yet, so the rates are null there (rendered as '-').
      kpiRefs.rps.value.textContent = formatRate(sample.rps);
      kpiRefs.errors.value.textContent = formatRate(sample.eps);
    }

    heapChart.windowMs = data.windowMs;
    threadsChart.windowMs = data.windowMs;
    rateChart.windowMs = data.windowMs;
    bytesChart.windowMs = data.windowMs;
    heapChart.setData(data.samples.map((s) => [s.ts, [s.heapUsed, s.heapCommitted]]));
    threadsChart.setData(data.samples.map((s) => [s.ts, [s.threadsBusy]]));
    rateChart.setData(data.samples.map((s) => [s.ts, [s.rps, s.eps]]));
    bytesChart.setData(data.samples.map((s) => [s.ts, [s.bpsSent, s.bpsRecv]]));

    // Applications table
    clear(appsTableWrap);
    const apps = data.apps || [];
    const seg = (a) => encodeURIComponent(a.path === '' ? 'root' : a.path.replace(/^\//, ''));
    const rows = apps.map((a) => el('tr', {},
        el('td', {},
            el('a', {
              href: BASE + '/apps/' + a.host + '/' + seg(a),
              onclick: (e) => {
                e.preventDefault();
                window.history.pushState({}, '', BASE + '/apps/' + a.host + '/' + seg(a));
                window.dispatchEvent(new PopStateEvent('popstate'));
              },
            }, a.path === '' ? '/' : a.path)),
        el('td', {}, stateBadge(a.state)),
        el('td', { class: 'num' }, String(a.activeSessions))));
    appsTableWrap.append(el('table', { class: 'data' },
        el('thead', {}, el('tr', {},
            el('th', {}, t('manager2.ui.col.path')),
            el('th', {}, t('manager2.ui.col.state')),
            el('th', {}, t('manager2.ui.col.sessions')))),
        el('tbody', {}, rows.length > 0 ? rows
            : el('tr', {}, el('td', { colspan: '3', class: 'empty' }, 'No applications deployed')))));
  }

  // ---------------- Polling ----------------
  let stopped = false;
  let timer = 0;

  function schedule(delay) {
    if (stopped) return;
    timer = setTimeout(tick, delay > 0 ? delay : FALLBACK_TICK_MS);
  }

  async function tick() {
    if (stopped) return;
    if (document.hidden) {
      // Paused while the tab is hidden; the server keeps collecting.
      schedule(FALLBACK_TICK_MS);
      return;
    }
    let data;
    try {
      data = await get('/api/status/history');
    } catch (err) {
      schedule(FALLBACK_TICK_MS); // transient; next tick retries
      return;
    }
    if (stopped) return;
    render(data);
    schedule(data.tickMs);
  }

  await tick();

  return () => {
    stopped = true;
    clearTimeout(timer);
  };
}
