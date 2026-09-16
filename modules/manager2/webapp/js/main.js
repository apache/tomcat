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

import { BASE, get } from './api.js';
import { register, setNotFound, render, setNavUpdater } from './router.js';
import { el, clear, icon, svgPath, toast, formatDuration } from './ui.js';
import { dashboard } from './pages/dashboard.js';
import { apps, appDetail } from './pages/apps.js';
import { hosts } from './pages/hosts.js';
import { monitoring } from './pages/monitoring.js';
import { diagnostics } from './pages/diagnostics.js';
import { logs } from './pages/logs.js';
import { accessLog } from './pages/accesslog.js';
import { users } from './pages/users.js';
import { configuration } from './pages/configuration.js';

const NAV_ITEMS = [
  { route: '/', icon: 'dashboard', label: 'Dashboard' },
  { route: '/apps', icon: 'apps', label: 'Applications' },
  { route: '/hosts', icon: 'hosts', label: 'Hosts' },
  { route: '/configuration', icon: 'config', label: 'Configuration' },
  { route: '/users', icon: 'users', label: 'Users' },
  { route: '/monitoring', icon: 'monitoring', label: 'Monitoring' },
  { route: '/diagnostics', icon: 'diagnostics', label: 'Diagnostics' },
  { route: '/logs', icon: 'logs', label: 'Logs' },
  { route: '/access-log', icon: 'access-log', label: 'Access log' },
];

let currentCleanup = null;
let serverInfo = null;

// ============================ Theme ====================================

function applyTheme(theme) {
  document.documentElement.setAttribute('data-theme', theme);
  updateThemeIcon();
}

function currentTheme() {
  return document.documentElement.getAttribute('data-theme') || 'auto';
}

function updateThemeIcon() {
  const btn = document.getElementById('theme-toggle');
  const theme = currentTheme();
  const dark = theme === 'dark'
      || (theme === 'auto' && window.matchMedia('(prefers-color-scheme: dark)').matches);
  btn.innerHTML = '';
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('width', '18');
  svg.setAttribute('height', '18');
  const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  path.setAttribute('d', dark ? svgPath('sun') : svgPath('moon'));
  svg.append(path);
  btn.append(svg);
}

function initTheme() {
  const stored = localStorage.getItem('manager2.theme');
  applyTheme(stored || 'auto');
  document.getElementById('theme-toggle').addEventListener('click', () => {
    const dark = document.documentElement.getAttribute('data-theme') === 'dark'
        || (document.documentElement.getAttribute('data-theme') !== 'light'
            && window.matchMedia('(prefers-color-scheme: dark)').matches);
    const next = dark ? 'light' : 'dark';
    localStorage.setItem('manager2.theme', next);
    applyTheme(next);
  });
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', updateThemeIcon);
}

// ============================ Navigation ===============================

function isActive(route, path) {
  if (route === '/') return path === '/';
  return path === route || path.startsWith(route + '/');
}

function buildNav() {
  const nav = document.querySelector('.sidenav');
  for (const item of NAV_ITEMS) {
    const btn = nav.querySelector('[data-nav="' + item.route + '"]');
    btn.innerHTML = '';
    // The per-tab class selects the accent hue of the active state (CSS);
    // the aria-label keeps the accessible name when the visible label is
    // hidden on narrow screens (bottom nav).
    btn.classList.add('nav-tab-' + item.icon);
    btn.setAttribute('aria-label', item.label);
    btn.append(icon(item.icon, 18));
    const label = document.createElement('span');
    label.className = 'nav-label';
    label.textContent = item.label;
    btn.append(label);
    btn.addEventListener('click', () => {
      window.history.pushState({}, '', BASE + item.route);
      render();
    });
  }
}

function updateNav(path) {
  document.querySelectorAll('.nav-item').forEach((btn) => {
    const route = btn.dataset.nav;
    btn.classList.toggle('active', isActive(route, path));
  });
}

// ============================ Routing ==================================

function pageRoute(pattern, handler) {
  register(pattern, async (params, path) => {
    if (currentCleanup) {
      currentCleanup();
      currentCleanup = null;
    }
    const container = document.getElementById('view');
    clear(container);
    container.append(el('div', { class: 'spinner', role: 'status', 'aria-label': 'Loading' }));
    try {
      clear(container);
      const cleanup = await handler(container, params, path);
      if (typeof cleanup === 'function') {
        currentCleanup = cleanup;
      }
    } catch (err) {
      clear(container);
      container.append(el('div', { class: 'card' },
          el('h3', {}, 'Something went wrong'),
          el('p', { style: 'color:var(--text-soft);' }, err.message)));
    }
    container.focus({ preventScroll: true });
  });
}

// ============================ Boot =====================================

async function boot() {
  initTheme();
  buildNav();
  setNavUpdater(updateNav);

  // Rebuild the logout button as icon + label: on narrow screens the label
  // is hidden and the icon takes its place (see the CSS).
  const logoutBtn = document.getElementById('logout');
  logoutBtn.setAttribute('aria-label', 'Log out');
  const logoutLabel = document.createElement('span');
  logoutLabel.className = 'logout-label';
  logoutLabel.textContent = logoutBtn.textContent;
  logoutBtn.textContent = '';
  logoutBtn.append(icon('logout', 18), logoutLabel);

  document.getElementById('logout').addEventListener('click', async () => {
    try {
      await fetch(BASE + '/logout', { method: 'POST', credentials: 'same-origin' });
    } catch (e) {
      // ignore; the redirect below ends the session on the server anyway
    }
    window.location.href = BASE + '/';
  });

  pageRoute('/', dashboard);
  pageRoute('/apps', apps);
  pageRoute('/apps/{host}/{path}', appDetail);
  pageRoute('/hosts', hosts);
  pageRoute('/configuration', configuration);
  pageRoute('/monitoring', monitoring);
  pageRoute('/diagnostics', diagnostics);
  pageRoute('/logs', logs);
  pageRoute('/access-log', accessLog);
  pageRoute('/users', users);

  setNotFound((path) => {
    const container = document.getElementById('view');
    clear(container);
    container.append(el('div', { class: 'card empty' },
        el('p', {}, 'Not found: ' + path)));
  });

  try {
    serverInfo = await get('/api/info');
  } catch (err) {
    // api() already redirected to the login page on 401/redirect
    return;
  }

  const version = document.getElementById('server-version');
  const status = document.getElementById('server-status');
  if (serverInfo.server && serverInfo.server.info) {
    version.textContent = serverInfo.server.info.replace(/^Apache Tomcat /, '');
    version.title = serverInfo.server.info;
  }
  if (serverInfo.runtime && serverInfo.host) {
    status.append(
        el('span', {}, serverInfo.host.name),
        el('span', { style: 'color:var(--text-faint);' }, '·'),
        el('span', {}, 'up ' + formatDuration(serverInfo.runtime.uptimeMs)));
  }

  document.getElementById('app').hidden = false;
  await render();
}

boot();
