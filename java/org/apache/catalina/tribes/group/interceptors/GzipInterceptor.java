/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.tribes.group.interceptors;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.group.ChannelInterceptorBase;
import org.apache.catalina.tribes.group.InterceptorPayload;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;


/**
 * @version 1.0
 */
public class GzipInterceptor extends ChannelInterceptorBase implements GzipInterceptorMBean {

    private static final Log log = LogFactory.getLog(GzipInterceptor.class);
    protected static final StringManager sm = StringManager.getManager(GzipInterceptor.class);

    public static final int DEFAULT_BUFFER_SIZE = 2048;
    public static final int DEFAULT_OPTION_COMPRESSION_ENABLE = 0x0100;

    private int compressionMinSize = 0;
    private volatile boolean statsEnabled = false;
    private int interval = 0;

    // Stats
    private final AtomicInteger count = new AtomicInteger();
    private final AtomicInteger countCompressedTX = new AtomicInteger();
    private final AtomicInteger countUncompressedTX = new AtomicInteger();
    private final AtomicInteger countCompressedRX = new AtomicInteger();
    private final AtomicInteger countUncompressedRX = new AtomicInteger();
    private final AtomicLong sizeTX = new AtomicLong();
    private final AtomicLong compressedSizeTX = new AtomicLong();
    private final AtomicLong uncompressedSizeTX = new AtomicLong();
    private final AtomicLong sizeRX = new AtomicLong();
    private final AtomicLong compressedSizeRX = new AtomicLong();
    private final AtomicLong uncompressedSizeRX = new AtomicLong();


    public GzipInterceptor() {
        setOptionFlag(DEFAULT_OPTION_COMPRESSION_ENABLE);
    }


    @Override
    public void sendMessage(Member[] destination, ChannelMessage msg, InterceptorPayload payload)
            throws ChannelException {
        try {
            byte[] data = msg.getMessage().getBytes();
            if (statsEnabled) {
                sizeTX.addAndGet(data.length);
            }

            if (data.length > compressionMinSize) {
                data = compress(data);
                // Set the flag that indicates that the message is compressed
                msg.setOptions(msg.getOptions() | getOptionFlag());
                if (statsEnabled) {
                    countCompressedTX.incrementAndGet();
                    compressedSizeTX.addAndGet(data.length);
                }
            } else if (statsEnabled){
                countUncompressedTX.incrementAndGet();
                uncompressedSizeTX.addAndGet(data.length);
            }

            msg.getMessage().trim(msg.getMessage().getLength());
            msg.getMessage().append(data,0,data.length);
            super.sendMessage(destination, msg, payload);

            int currentCount = count.incrementAndGet();
            if (statsEnabled && interval > 0 && currentCount % interval == 0) {
                report();
            }
        } catch ( IOException x ) {
            log.error(sm.getString("gzipInterceptor.compress.failed"));
            throw new ChannelException(x);
        }
    }


    @Override
    public void messageReceived(ChannelMessage msg) {
        try {
            byte[] data = msg.getMessage().getBytes();
            if ((msg.getOptions() & getOptionFlag()) > 0) {
                if (statsEnabled) {
                    countCompressedRX.incrementAndGet();
                    compressedSizeRX.addAndGet(data.length);
                }
                // Message was compressed
                data = decompress(data);
            } else if (statsEnabled) {
                countUncompressedRX.incrementAndGet();
                uncompressedSizeRX.addAndGet(data.length);
            }

            if (statsEnabled) {
                sizeRX.addAndGet(data.length);
            }

            msg.getMessage().trim(msg.getMessage().getLength());
            msg.getMessage().append(data,0,data.length);
            super.messageReceived(msg);

            int currentCount = count.incrementAndGet();
            if (statsEnabled && interval > 0 && currentCount % interval == 0) {
                report();
            }
        } catch ( IOException x ) {
            log.error(sm.getString("gzipInterceptor.decompress.failed"),x);
        }
    }


    public static byte[] compress(byte[] data) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        GZIPOutputStream gout = new GZIPOutputStream(bout);
        gout.write(data);
        gout.flush();
        gout.close();
        return bout.toByteArray();
    }


    /**
     * @param data  Data to decompress
     * @return      Decompressed data
     * @throws IOException Compression error
     */
    public static byte[] decompress(byte[] data) throws IOException {
        ByteArrayOutputStream bout =
            new ByteArrayOutputStream(DEFAULT_BUFFER_SIZE);
        ByteArrayInputStream bin = new ByteArrayInputStream(data);
        GZIPInputStream gin = new GZIPInputStream(bin);
        byte[] tmp = new byte[DEFAULT_BUFFER_SIZE];
        int length = gin.read(tmp);
        while (length > -1) {
            bout.write(tmp, 0, length);
            length = gin.read(tmp);
        }
        return bout.toByteArray();
    }


    @Override
    public void report() {
        log.info(sm.getString("gzipInterceptor.report", Integer.valueOf(getCount()),
                Integer.valueOf(getCountCompressedTX()), Integer.valueOf(getCountUncompressedTX()),
                Integer.valueOf(getCountCompressedRX()), Integer.valueOf(getCountUncompressedRX()),
                Long.valueOf(getSizeTX()), Long.valueOf(getCompressedSizeTX()),
                Long.valueOf(getUncompressedSizeTX()),
                Long.valueOf(getSizeRX()), Long.valueOf(getCompressedSizeRX()),
                Long.valueOf(getUncompressedSizeRX())));
    }


    @Override
    public int getCompressionMinSize() {
        return compressionMinSize;
    }


    @Override
    public void setCompressionMinSize(int compressionMinSize) {
        this.compressionMinSize = compressionMinSize;
    }


    @Override
    public boolean getStatsEnabled() {
        return statsEnabled;
    }


    @Override
    public void setStatsEnabled(boolean statsEnabled) {
        this.statsEnabled = statsEnabled;
    }


    @Override
    public int getInterval() {
        return interval;
    }


    @Override
    public void setInterval(int interval) {
        this.interval = interval;
    }


    @Override
    public int getCount() {
        return count.get();
    }


    @Override
    public int getCountCompressedTX() {
        return countCompressedTX.get();
    }


    @Override
    public int getCountUncompressedTX() {
        return countUncompressedTX.get();
    }


    @Override
    public int getCountCompressedRX() {
        return countCompressedRX.get();
    }


    @Override
    public int getCountUncompressedRX() {
        return countUncompressedRX.get();
    }


    @Override
    public long getSizeTX() {
        return sizeTX.get();
    }


    @Override
    public long getCompressedSizeTX() {
        return compressedSizeTX.get();
    }


    @Override
    public long getUncompressedSizeTX() {
        return uncompressedSizeTX.get();
    }


    @Override
    public long getSizeRX() {
        return sizeRX.get();
    }


    @Override
    public long getCompressedSizeRX() {
        return compressedSizeRX.get();
    }


    @Override
    public long getUncompressedSizeRX() {
        return uncompressedSizeRX.get();
    }


    @Override
    public void reset() {
        count.set(0);
        countCompressedTX.set(0);
        countUncompressedTX.set(0);
        countCompressedRX.set(0);
        countUncompressedRX.set(0);
        sizeTX.set(0);
        compressedSizeTX.set(0);
        uncompressedSizeTX.set(0);
        sizeRX.set(0);
        compressedSizeRX.set(0);
        uncompressedSizeRX.set(0);
    }
}
