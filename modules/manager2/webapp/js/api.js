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

// Base path of the web application, derived from the location of this
// script so the webapp can be deployed under any context path.
// e.g. this file lives at {context}/js/api.js -> BASE = {context}
export const BASE = new URL('.', import.meta.url).pathname.replace(/js\/$/, '').replace(/\/$/, '');

import { t } from './i18n.js';

let csrfToken = null;

/**
 * Perform a JSON API request.
 *
 * @param {string} method HTTP method
 * @param {string} path   API path, e.g. /api/apps
 * @param {object|FormData} [body] JSON body or multipart data
 * @returns {Promise<object|string>} parsed JSON payload (or raw text for
 *          endpoints that return plain text)
 */
export async function api(method, path, body) {
  const headers = { Accept: 'application/json' };

  if (body instanceof FormData) {
    // multipart: the browser sets the content type with the boundary
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (method !== 'GET' && method !== 'HEAD') {
    if (!csrfToken) {
      throw new Error(t('manager2.ui.api.csrfTokenMissing'));
    }
    headers['X-CSRF-Token'] = csrfToken;
  }

  let payload;
  if (body instanceof FormData) {
    payload = body;
  } else if (body !== undefined) {
    payload = JSON.stringify(body);
  }

  const resp = await fetch(BASE + path, {
    method,
    headers,
    body: payload,
    credentials: 'same-origin',
  });

  const token = resp.headers.get('X-CSRF-Token');
  if (token) {
    csrfToken = token;
  }

  const text = await resp.text();

  // Unauthenticated: the container redirected to the login page, or the
  // response is a 401.
  if (resp.redirected || resp.status === 401) {
    redirectToLogin();
    throw new Error('unauthenticated');
  }

  const type = (resp.headers.get('Content-Type') || '').toLowerCase();
  const isHtml = type.includes('text/html')
      || /^\s*<!DOCTYPE/i.test(text)
      || /^\s*<html/i.test(text);

  // Error (4xx/5xx): surface the message to the page. Even when the error
  // body is HTML (the container's error page), this is not an
  // unauthenticated condition and must not trigger a login redirect.
  if (!resp.ok) {
    let data = null;
    try {
      data = JSON.parse(text);
    } catch (e) {
      data = null;
    }
    let message = (data && data.message) || t('manager2.ui.api.requestFailed', resp.status);
    if (resp.status === 403 && !data) {
      // The container's HTML 403 page: the signed-in account lacks the
      // role required for this endpoint (e.g. manager-status on a
      // manager-gui-only API).
      message = t('manager2.ui.api.forbidden');
    }
    const err = new Error(message);
    err.code = data && data.error;
    err.status = resp.status;
    throw err;
  }

  // Unauthenticated: with FORM authentication the container does not answer
  // a 401, it forwards the request to the login page, so the response is a
  // 200 with the login page HTML instead of JSON (the API servlets only
  // ever produce JSON, so a 200 HTML response can only be the login page).
  if (isHtml) {
    redirectToLogin();
    throw new Error('unauthenticated');
  }

  let data = null;
  try {
    data = JSON.parse(text);
  } catch (e) {
    data = null;
  }

  return data !== null ? data : text;
}

export function getCsrfToken() {
  return csrfToken;
}

/**
 * Update the CSRF token (used by XHR-based uploads, which cannot go
 * through fetch()).
 */
export function setCsrfToken(token) {
  csrfToken = token;
}

/**
 * Fetch and parse a JSON API endpoint (GET).
 */
export const get = (path) => api('GET', path);

let redirected = false;

const BOUNCE_GUARD_MS = 8000;
const BOUNCE_GUARD_KEY = 'manager2.authBounce';

/**
 * Send the browser to the login page while preserving the current page.
 * <p>
 * A full navigation to the current URL is performed: the server gates every
 * page (see HomeServlet) and shows the login page at that same URL, so a
 * successful login returns the user to exactly the page they were on
 * (instead of dropping them at the application root).
 * <p>
 * A short guard against reload loops: if authentication keeps failing
 * (e.g. bad credentials), the second bounce within the guard window is
 * suppressed so the browser does not spin in a reload cycle.
 */
export function redirectToLogin() {
  if (redirected) {
    return;
  }
  redirected = true;
  let last = 0;
  try {
    last = Number(window.sessionStorage.getItem(BOUNCE_GUARD_KEY)) || 0;
  } catch (e) {
    // sessionStorage unavailable: bounce once, guarded by 'redirected'
  }
  const now = Date.now();
  try {
    window.sessionStorage.setItem(BOUNCE_GUARD_KEY, String(now));
  } catch (e) {
    // ignore
  }
  if (now - last < BOUNCE_GUARD_MS) {
    // Authentication failed again right after a bounce: the reload of the
    // current page did not help. Fall back to the application root, which
    // always renders the login page (a blank shell would otherwise leave
    // the user without a way to sign in).
    window.location.replace(BASE + '/');
    return;
  }
  window.location.assign(window.location.href);
}
