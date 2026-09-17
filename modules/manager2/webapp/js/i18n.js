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

// Client-side localization. The messages come from the LocalStrings bundle
// of the web application, served by the /i18n endpoint localized with the
// locales the browser sent in its Accept-Language header.

import { BASE } from './api.js';

let messages = {};

// The locale the server resolved for the bundle ('en' when no translation
// is installed). Live ESM binding: importers see the update after
// loadMessages() completes.
export let locale = 'en';

/**
 * Load the localized messages of the client. Must be awaited before the
 * interface is rendered; rendering falls back to the keys when this fails.
 */
export async function loadMessages() {
  try {
    const resp = await fetch(BASE + '/i18n', {
      headers: { Accept: 'application/json' },
      credentials: 'same-origin',
    });
    if (resp.ok) {
      const data = await resp.json();
      if (data && data.messages) {
        messages = data.messages;
        if (data.locale) {
          locale = data.locale;
        }
      }
    }
  } catch (e) {
    // Without the bundle the keys are displayed as-is.
  }
  document.documentElement.setAttribute('lang', locale);
  return messages;
}

/**
 * Translate a message of the bundle.
 *
 * @param {string} key  the bundle key, e.g. manager2.ui.apps.title
 * @param {...*}   args the values for the {0}, {1}, ... placeholders
 * @returns {string} the localized message, or the key when it is unknown
 */
export function t(key, ...args) {
  let text = messages[key];
  if (text === undefined) {
    return key;
  }
  if (args.length > 0) {
    text = text.replace(/\{(\d+)\}/g, (match, index) =>
      (args[Number(index)] !== undefined ? String(args[Number(index)]) : match));
  }
  return text;
}

/**
 * Whether the loaded bundle contains a key.
 *
 * @param {string} key the bundle key
 * @returns {boolean} true when the key is known
 */
export function has(key) {
  return Object.prototype.hasOwnProperty.call(messages, key);
}

/**
 * A number formatter for the active locale (the base locale is a valid
 * fallback: it renders exactly the English number format).
 */
export function numberFormatter(options) {
  return new Intl.NumberFormat(locale, options);
}
