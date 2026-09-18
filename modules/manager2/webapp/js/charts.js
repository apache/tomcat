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

// Small hand-written canvas charting: rolling line charts with multiple
// series and radial gauges. No dependencies.

const PALETTE = [
  '#e7600c', '#2456a6', '#1a7f4b', '#9a3412', '#6d28d9',
  '#0e7490', '#b45309', '#be185d', '#4d7c0f', '#475569',
];

function cssVar(name, fallback) {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return value || fallback;
}

/**
 * Multi-series rolling line chart.
 *
 * @param {HTMLCanvasElement} canvas
 * @param {object} opts { series: [{name, color, unit}], windowMs (rolling
 *          window), formatValue(v) }
 */
export class LineChart {
  constructor(canvas, opts) {
    this.canvas = canvas;
    this.series = opts.series;
    this.windowMs = opts.windowMs || 5 * 60 * 1000;
    this.formatValue = opts.formatValue || ((v) => String(v));
    this.points = new Map(); // series index -> [ [ts, value], ... ]
    this.resizeObserver = new ResizeObserver(() => this.draw());
    this.resizeObserver.observe(canvas.parentElement || canvas);
    this.draw();
  }

  /**
   * Add a point to each series (values aligned with this.series).
   */
  push(ts, values) {
    values.forEach((v, i) => {
      if (v === null || v === undefined || isNaN(v)) return;
      if (!this.points.has(i)) this.points.set(i, []);
      const arr = this.points.get(i);
      arr.push([ts, v]);
    });
    // Trim points outside the rolling window
    const cutoff = ts - this.windowMs;
    for (const arr of this.points.values()) {
      while (arr.length > 0 && arr[0][0] < cutoff) {
        arr.shift();
      }
    }
    this.draw();
  }

  /**
   * Replace all points with the given entries (e.g. the full history window
   * collected by the server). Each entry is `[ts, values]` where values are
   * aligned with this.series; null/undefined values are skipped, like in
   * {@link #push}.
   *
   * @param {Array<[number, (number|null)[]]>} entries
   */
  setData(entries) {
    const points = new Map();
    for (const [ts, values] of entries) {
      values.forEach((v, i) => {
        if (v === null || v === undefined || isNaN(v)) return;
        if (!points.has(i)) points.set(i, []);
        points.get(i).push([ts, v]);
      });
    }
    // Trim points outside the rolling window (the server normally already
    // trims to its window; this keeps the chart correct if the window is
    // reduced server-side).
    const last = entries.length > 0 ? entries[entries.length - 1][0] : Date.now();
    const cutoff = last - this.windowMs;
    for (const arr of points.values()) {
      while (arr.length > 0 && arr[0][0] < cutoff) {
        arr.shift();
      }
    }
    this.points = points;
    this.draw();
  }

  clear() {
    this.points.clear();
    this.draw();
  }

  draw() {
    const canvas = this.canvas;
    const dpr = window.devicePixelRatio || 1;
    const rect = canvas.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) return;
    if (canvas.width !== rect.width * dpr || canvas.height !== rect.height * dpr) {
      canvas.width = rect.width * dpr;
      canvas.height = rect.height * dpr;
    }
    const ctx = canvas.getContext('2d');
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    const w = rect.width;
    const h = rect.height;
    ctx.clearRect(0, 0, w, h);

    const padL = 46;
    const padR = 8;
    const padT = 8;
    const padB = 20;
    const plotW = w - padL - padR;
    const plotH = h - padT - padB;

    // Determine time domain and value domain
    let tMin = null;
    let tMax = null;
    let vMax = 0;
    let vMin = 0;
    for (const arr of this.points.values()) {
      for (const [ts, v] of arr) {
        if (tMin === null || ts < tMin) tMin = ts;
        if (tMax === null || ts > tMax) tMax = ts;
        if (v > vMax) vMax = v;
        if (v < vMin) vMin = v;
      }
    }
    if (tMin === null) {
      tMin = Date.now() - this.windowMs;
      tMax = Date.now();
    }
    if (tMax - tMin < 1000) tMax = tMin + 1000;
    if (vMax === vMin) vMax = vMin + 1;
    vMax *= 1.08; // headroom

    const gridColor = cssVar('--border', '#ddd');
    const textColor = cssVar('--text-faint', '#888');

    // Grid + y labels (4 divisions)
    ctx.font = '11px ' + cssVar('--font', 'sans-serif');
    ctx.fillStyle = textColor;
    ctx.strokeStyle = gridColor;
    ctx.lineWidth = 1;
    const divisions = 4;
    for (let i = 0; i <= divisions; i++) {
      const v = vMin + (vMax - vMin) * i / divisions;
      const y = padT + plotH - plotH * i / divisions;
      ctx.beginPath();
      ctx.moveTo(padL, y);
      ctx.lineTo(padL + plotW, y);
      ctx.stroke();
      ctx.textAlign = 'right';
      ctx.textBaseline = 'middle';
      ctx.fillText(this.formatValue(v), padL - 6, y);
    }
    // x labels (3)
    ctx.textAlign = 'center';
    ctx.textBaseline = 'top';
    for (let i = 0; i <= 2; i++) {
      const ts = tMin + (tMax - tMin) * i / 2;
      const x = padL + plotW * i / 2;
      ctx.fillText(new Date(ts).toLocaleTimeString([], { hour12: false }), x, padT + plotH + 6);
    }

    // Series
    this.series.forEach((s, i) => {
      const arr = this.points.get(i);
      if (!arr || arr.length < 2) return;
      ctx.beginPath();
      arr.forEach(([ts, v], j) => {
        const x = padL + plotW * (ts - tMin) / (tMax - tMin);
        const y = padT + plotH - plotH * (v - vMin) / (vMax - vMin);
        if (j === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      });
      ctx.strokeStyle = s.color || PALETTE[i % PALETTE.length];
      ctx.lineWidth = 1.8;
      ctx.lineJoin = 'round';
      ctx.stroke();
    });
  }
}

/**
 * Radial gauge.
 *
 * @param {HTMLCanvasElement} canvas
 */
export class Gauge {
  constructor(canvas) {
    this.canvas = canvas;
    this.value = 0;
    this.max = 1;
    this.resizeObserver = new ResizeObserver(() => this.draw());
    this.resizeObserver.observe(canvas.parentElement || canvas);
  }

  set(value, max) {
    this.value = value;
    this.max = max;
    this.draw();
  }

  draw() {
    const canvas = this.canvas;
    const dpr = window.devicePixelRatio || 1;
    const rect = canvas.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) return;
    if (canvas.width !== rect.width * dpr || canvas.height !== rect.height * dpr) {
      canvas.width = rect.width * dpr;
      canvas.height = rect.height * dpr;
    }
    const ctx = canvas.getContext('2d');
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    const w = rect.width;
    const h = rect.height;
    ctx.clearRect(0, 0, w, h);

    const cx = w / 2;
    const cy = h - 6;
    const r = Math.min(w / 2 - 8, h - 16);
    const start = Math.PI;
    const end = 2 * Math.PI;
    const ratio = this.max > 0 ? Math.min(1, this.value / this.max) : 0;

    ctx.lineCap = 'round';
    ctx.lineWidth = 10;
    ctx.beginPath();
    ctx.arc(cx, cy, r, start, end);
    ctx.strokeStyle = cssVar('--bg-inset', '#eee');
    ctx.stroke();

    if (ratio > 0) {
      const color = ratio > 0.9 ? cssVar('--danger', '#c00')
          : ratio > 0.75 ? cssVar('--warn', '#a60')
              : cssVar('--ok', '#1a7');
      ctx.beginPath();
      ctx.arc(cx, cy, r, start, start + (end - start) * ratio);
      ctx.strokeStyle = color;
      ctx.stroke();
    }
  }
}

export function palette(index) {
  return PALETTE[index % PALETTE.length];
}
