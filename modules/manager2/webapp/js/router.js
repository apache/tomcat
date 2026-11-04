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

// A minimal history-based router. Routes are registered in priority order;
// the first matcher wins. Matchers may return a boolean (handled) or an
// async result.

const routes = [];
let notFoundHandler = null;

// Base path of the web application, derived from the location of this
// script so the webapp can be deployed under any context path.
const BASE = new URL('.', import.meta.url).pathname.replace(/js\/$/, '').replace(/\/$/, '');

function currentPath() {
  const path = window.location.pathname;
  let p = path.startsWith(BASE) ? path.substring(BASE.length) : path;
  if (!p.startsWith('/')) p = '/' + p;
  if (p === '/index.html') {
    p = '/';
  }
  return p.replace(/\/+$/, '') || '/';
}

/**
 * Register a route.
 *
 * @param {string} pattern path pattern with {param} placeholders
 * @param {function} handler (params, path) => void
 */
export function register(pattern, handler) {
  const names = [];
  const regex = new RegExp('^' + pattern.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
      .replace(/\\{([a-zA-Z0-9_]+)\\}/g, (_, n) => {
        names.push(n);
        return '([^/]+)';
      }) + '$');
  routes.push({ regex, names, handler });
}

export function setNotFound(handler) {
  notFoundHandler = handler;
}

export async function render() {
  const path = currentPath();
  for (const route of routes) {
    const m = path.match(route.regex);
    if (m) {
      const params = {};
      route.names.forEach((n, i) => {
        params[n] = decodeURIComponent(m[i + 1]);
      });
      await route.handler(params, path);
      updateNav(path);
      return;
    }
  }
  if (notFoundHandler) {
    await notFoundHandler(path);
  }
  updateNav(path);
}

export function navigate(path) {
  window.history.pushState({}, '', BASE + path);
  render();
}

export function currentRoute() {
  return currentPath();
}

let navUpdater = null;
export function setNavUpdater(fn) {
  navUpdater = fn;
}

function updateNav(path) {
  if (navUpdater) {
    navUpdater(path);
  }
}

window.addEventListener('popstate', () => {
  render();
});
