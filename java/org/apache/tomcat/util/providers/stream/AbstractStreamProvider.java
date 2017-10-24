/**
 *  Copyright 2017 Ismaïl Senhaji, Guillaume Pythoud, Maxime Beck
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.tomcat.util.providers.stream;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class AbstractStreamProvider implements StreamProvider {
    private static final Logger log = Logger.getLogger(AbstractStreamProvider.class.getName());

    public URLConnection openConnection(String url, Map<String, String> headers, int connectTimeout, int readTimeout) throws IOException {
        if (log.isLoggable(Level.FINE)) {
            log.log(Level.FINE, String.format("%s opening connection: url [%s], headers [%s], connectTimeout [%s], readTimeout [%s]", getClass().getSimpleName(), url, headers, connectTimeout, readTimeout));
        }
        URLConnection connection = new URL(url).openConnection();
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.addRequestProperty(entry.getKey(), entry.getValue());
            }
        }
        if (connectTimeout < 0 || readTimeout < 0) {
            throw new IllegalArgumentException(
                String.format("Neither connectTimeout [%s] nor readTimeout [%s] can be less than 0 for URLConnection.", connectTimeout, readTimeout));
        }
        connection.setConnectTimeout(connectTimeout);
        connection.setReadTimeout(readTimeout);
        return connection;
    }

}
