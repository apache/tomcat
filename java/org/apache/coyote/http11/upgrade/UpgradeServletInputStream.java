/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
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
package org.apache.coyote.http11.upgrade;

import java.io.IOException;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;

public abstract class UpgradeServletInputStream extends ServletInputStream {

    protected static final int EOF = -1;
    protected static final int NO_DATA = -2;

    private volatile boolean finished = false;
    private volatile boolean ready = true;
    private volatile ReadListener listener = null;

    @Override
    public final boolean isFinished() {
        return finished;
    }

    @Override
    public boolean isReady() {
        try {
            ready = doIsReady();
        } catch (IOException e) {
            listener.onError(e);
        }
        return ready;
    }

    @Override
    public void setReadListener(ReadListener listener) {
        if (listener == null) {
            // TODO i18n
            throw new IllegalArgumentException();
        }
        this.listener = listener;

        isReady();
    }

    @Override
    public final int read() throws IOException {
        if (!ready) {
            // TODO i18n
            throw new IllegalStateException();
        }
        ReadListener readListener = this.listener;
        int result = doRead(readListener == null);
        if (result == EOF) {
            finished = true;
            if (readListener != null) {
                readListener.onAllDataRead();
            }
            return EOF;
        } else if (result == NO_DATA) {
            return EOF;
        }
        return result;
    }

    protected void onDataAvailable() {
        ready = true;
        listener.onDataAvailable();
    }

    protected abstract int doRead(boolean block) throws IOException;

    protected abstract boolean doIsReady() throws IOException;
}
