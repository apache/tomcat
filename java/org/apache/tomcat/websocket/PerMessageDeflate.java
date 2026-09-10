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
package org.apache.tomcat.websocket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import javax.websocket.Extension;
import javax.websocket.Extension.Parameter;
import javax.websocket.SendHandler;

import org.apache.tomcat.util.res.StringManager;

/**
 * Implementation of the permessage-deflate WebSocket extension as defined in RFC 7692. This extension provides message
 * compression for WebSocket frames.
 */
public class PerMessageDeflate implements Transformation {

    private static final StringManager sm = StringManager.getManager(PerMessageDeflate.class);

    private static final String SERVER_NO_CONTEXT_TAKEOVER = "server_no_context_takeover";
    private static final String CLIENT_NO_CONTEXT_TAKEOVER = "client_no_context_takeover";
    private static final String SERVER_MAX_WINDOW_BITS = "server_max_window_bits";
    private static final String CLIENT_MAX_WINDOW_BITS = "client_max_window_bits";

    private static final int RSV_BITMASK = 0b100;
    private static final byte[] EOM_BYTES = new byte[] { 0, 0, -1, -1 };

    /**
     * The name of the permessage-deflate extension.
     */
    public static final String NAME = "permessage-deflate";

    /**
     * The builder for the permessage-deflate transformation.
     */
    public static final TransformationBuilder BUILDER = new TransformationBuilder() {
        @Override
        public Transformation build(List<List<Parameter>> preferences, boolean isServer) {
            return PerMessageDeflate.build(preferences, isServer);
        }
    };

    private final boolean serverContextTakeover;
    private final int serverMaxWindowBits;
    private final boolean clientContextTakeover;
    private final int clientMaxWindowBits;
    private final boolean isServer;
    private final Inflater inflater = new Inflater(true);
    private final ByteBuffer readBuffer = ByteBuffer.allocate(Constants.DEFAULT_BUFFER_SIZE);
    private final byte[] eomOverflowBuffer = new byte[1];
    private final Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
    private final byte[] EOM_BUFFER = new byte[EOM_BYTES.length + 1];

    /*
     * Whether the LZ77 window used to decompress incoming messages persists across message boundaries. This side's
     * inflater decompresses whatever the *peer* compressed, so it is governed by the peer's context takeover
     * setting: a server's inflater follows clientContextTakeover and a client's inflater follows serverContextTakeover.
     */
    private final boolean inflaterContextTakeover;
    /*
     * Rolling copy of the last up to inflaterWindow.length bytes of output produced by inflater, maintained for every
     * message regardless of inflaterContextTakeover (see the constructor). Needed because resetting the Inflater -
     * either mid-message to recover from an early BFINAL block, or in endFrame() to clear a
     * finished-but-not-really-done state - discards its LZ77 window; feeding this back via setDictionary() immediately
     * after such a reset lets back-references into content compressed before the reset keep resolving correctly.
     * endFrame() clears inflaterWindowLength at the end of a message when inflaterContextTakeover is false, so the
     * window never actually survives *across* messages in that case - only within one.
     */
    private final byte[] inflaterWindow;

    private volatile Transformation next;
    private volatile boolean skipDecompression = false;
    private volatile boolean eomBytesInserted = false;
    private volatile boolean eomOverflowWritten = false;
    /*
     * Offset and length, within readBuffer's backing array, of the compressed bytes most recently passed to
     * inflater.setInput(). Used to work out where the unconsumed tail starts if inflater.finished() becomes true before
     * all of those bytes have been consumed (see getMoreData()). Both fields must be kept in sync with whatever the
     * most recent setInput() call actually used - lastInputOffset is not always readBuffer.arrayOffset(): after the
     * first such recovery, the next input segment starts wherever the previous one left off, not at the start of
     * readBuffer's backing array.
     */
    private volatile int lastInputOffset;
    private volatile int lastInputLength;
    // Number of valid bytes currently held in inflaterWindow.
    private volatile int inflaterWindowLength;
    private volatile ByteBuffer writeBuffer = ByteBuffer.allocate(Constants.DEFAULT_BUFFER_SIZE);
    private volatile boolean firstCompressedFrameWritten = false;
    // Flag to track if a message is completely empty
    private volatile boolean emptyMessage = true;

    static PerMessageDeflate build(List<List<Parameter>> preferences, boolean isServer) {
        // Accept the first preference that the endpoint is able to support
        for (List<Parameter> preference : preferences) {
            boolean ok = true;
            boolean serverContextTakeover = true;
            int serverMaxWindowBits = -1;
            boolean clientContextTakeover = true;
            int clientMaxWindowBits = -1;

            try {
                for (Parameter param : preference) {
                    if (SERVER_NO_CONTEXT_TAKEOVER.equals(param.getName())) {
                        if (serverContextTakeover) {
                            serverContextTakeover = false;
                        } else {
                            // Duplicate definition
                            throw new IllegalArgumentException(
                                    sm.getString("perMessageDeflate.duplicateParameter", SERVER_NO_CONTEXT_TAKEOVER));
                        }
                    } else if (CLIENT_NO_CONTEXT_TAKEOVER.equals(param.getName())) {
                        if (clientContextTakeover) {
                            clientContextTakeover = false;
                        } else {
                            // Duplicate definition
                            throw new IllegalArgumentException(
                                    sm.getString("perMessageDeflate.duplicateParameter", CLIENT_NO_CONTEXT_TAKEOVER));
                        }
                    } else if (SERVER_MAX_WINDOW_BITS.equals(param.getName())) {
                        if (serverMaxWindowBits == -1) {
                            serverMaxWindowBits = Integer.parseInt(param.getValue());
                            if (serverMaxWindowBits < 8 || serverMaxWindowBits > 15) {
                                throw new IllegalArgumentException(sm.getString("perMessageDeflate.invalidWindowSize",
                                        SERVER_MAX_WINDOW_BITS, Integer.valueOf(serverMaxWindowBits)));
                            }
                            // Java SE API (as of Java 11) does not expose the API to
                            // control the Window size. It is effectively hard-coded
                            // to 15
                            if (isServer && serverMaxWindowBits != 15) {
                                ok = false;
                                break;
                                // Note server window size is not an issue for the
                                // client since the client will assume 15 and if the
                                // server uses a smaller window everything will
                                // still work
                            }
                        } else {
                            // Duplicate definition
                            throw new IllegalArgumentException(
                                    sm.getString("perMessageDeflate.duplicateParameter", SERVER_MAX_WINDOW_BITS));
                        }
                    } else if (CLIENT_MAX_WINDOW_BITS.equals(param.getName())) {
                        if (clientMaxWindowBits == -1) {
                            if (param.getValue() == null) {
                                // Hint to server that the client supports this
                                // option. Java SE API (as of Java 11) does not
                                // expose the API to control the Window size. It is
                                // effectively hard-coded to 15
                                clientMaxWindowBits = 15;
                            } else {
                                clientMaxWindowBits = Integer.parseInt(param.getValue());
                                if (clientMaxWindowBits < 8 || clientMaxWindowBits > 15) {
                                    throw new IllegalArgumentException(
                                            sm.getString("perMessageDeflate.invalidWindowSize", CLIENT_MAX_WINDOW_BITS,
                                                    Integer.valueOf(clientMaxWindowBits)));
                                }
                            }
                            // Java SE API (as of Java 11) does not expose the API to
                            // control the Window size. It is effectively hard-coded
                            // to 15
                            if (!isServer && clientMaxWindowBits != 15) {
                                ok = false;
                                break;
                                // Note client window size is not an issue for the
                                // server since the server will assume 15 and if the
                                // client uses a smaller window everything will
                                // still work
                            }
                        } else {
                            // Duplicate definition
                            throw new IllegalArgumentException(
                                    sm.getString("perMessageDeflate.duplicateParameter", CLIENT_MAX_WINDOW_BITS));
                        }
                    } else {
                        // Unknown parameter
                        throw new IllegalArgumentException(
                                sm.getString("perMessageDeflate.unknownParameter", param.getName()));
                    }
                }
            } catch (IllegalArgumentException iae) {
                // An invalid extension parameter has been offered. RFC 7692
                // section 5.1 requires the offer to be declined and the handshake to
                // continue (without this extension) rather than failing. Try the
                // next offered configuration.
                ok = false;
            }
            if (ok) {
                return new PerMessageDeflate(serverContextTakeover, serverMaxWindowBits, clientContextTakeover,
                        clientMaxWindowBits, isServer);
            }
        }
        // Failed to negotiate agreeable terms
        return null;
    }


    private PerMessageDeflate(boolean serverContextTakeover, int serverMaxWindowBits, boolean clientContextTakeover,
            int clientMaxWindowBits, boolean isServer) {
        this.serverContextTakeover = serverContextTakeover;
        this.serverMaxWindowBits = serverMaxWindowBits;
        this.clientContextTakeover = clientContextTakeover;
        this.clientMaxWindowBits = clientMaxWindowBits;
        this.isServer = isServer;
        this.inflaterContextTakeover = isServer ? clientContextTakeover : serverContextTakeover;
        /*
         * 32768 (2^15) is the maximum DEFLATE window size and the one java.util.zip.Inflater/Deflater always
         * effectively use; there is no way, via the public Java SE API, to honour a smaller negotiated max_window_bits
         * value here.
         *
         * Always allocated, even when inflaterContextTakeover is false: that setting only governs whether the window
         * survives *between* messages (see endFrame()). RFC 7692 section 7.2.1 permits a single message to be
         * compressed as multiple DEFLATE blocks, and decompressing that correctly requires window continuity *within*
         * the message (see the mid-message recovery in getMoreData()) regardless of the cross-message context takeover
         * setting.
         */
        this.inflaterWindow = new byte[32768];
    }


    @Override
    public TransformationResult getMoreData(byte opCode, boolean fin, int rsv, ByteBuffer dest) throws IOException {
        // Control frames are never compressed and may appear in the middle of
        // a WebSocket method. Pass them straight through.
        if (Util.isControl(opCode)) {
            return next.getMoreData(opCode, fin, rsv, dest);
        }

        if (!Util.isContinuation(opCode)) {
            // First frame in new message
            skipDecompression = (rsv & RSV_BITMASK) == 0;
        }

        // Pass uncompressed frames straight through.
        if (skipDecompression) {
            return next.getMoreData(opCode, fin, rsv, dest);
        }

        if (eomOverflowWritten) {
            if (!dest.hasRemaining()) {
                return TransformationResult.OVERFLOW;
            }
            dest.put(eomOverflowBuffer[0]);
            eomOverflowWritten = false;
            if (!dest.hasRemaining()) {
                if (inflateEomBytes()) {
                    return TransformationResult.OVERFLOW;
                }
                return endFrame(fin);
            }
        }

        int written;

        while (dest.hasRemaining()) {
            // Space available in destination. Try and fill it.
            written = inflate(dest.array(), dest.arrayOffset() + dest.position(), dest.remaining());
            dest.position(dest.position() + written);

            if (inflater.needsInput() && !eomBytesInserted) {
                readBuffer.clear();
                TransformationResult nextResult = next.getMoreData(opCode, fin, (rsv ^ RSV_BITMASK), readBuffer);
                lastInputOffset = readBuffer.arrayOffset();
                lastInputLength = readBuffer.position();
                inflater.setInput(readBuffer.array(), readBuffer.arrayOffset(), readBuffer.position());
                if (dest.hasRemaining()) {
                    if (TransformationResult.UNDERFLOW.equals(nextResult)) {
                        return nextResult;
                    } else if (TransformationResult.END_OF_FRAME.equals(nextResult) && readBuffer.position() == 0) {
                        if (fin) {
                            inflater.setInput(EOM_BYTES);
                            eomBytesInserted = true;
                        } else {
                            return endFrame(fin);
                        }
                    }
                } else if (readBuffer.position() > 0) {
                    return TransformationResult.OVERFLOW;
                } else if (TransformationResult.END_OF_FRAME.equals(nextResult)) {
                    if (fin) {
                        if (inflateEomBytes()) {
                            return TransformationResult.OVERFLOW;
                        }
                    }
                    return endFrame(fin);
                } else if (TransformationResult.UNDERFLOW.equals(nextResult)) {
                    return nextResult;
                } else {
                    // Should never happen unless next mis-behaves
                    throw new IllegalStateException(
                            sm.getString("perMessageDeflate.next.ise", next.getClass().getName()));
                }
            } else if (written == 0) {
                if (!eomBytesInserted && inflater.finished() && inflater.getRemaining() > 0) {
                    /*
                     * RFC 7692 section 7.2.1 permits an endpoint to compress a single message using multiple DEFLATE
                     * blocks with any mix of BFINAL values, including a block with BFINAL=1 that is not the last block
                     * of the message.
                     *
                     * If inflater is finished without EOM bytes being inserted and with data still to process this
                     * indicates there is at least one more block to process. Inflater has no API to continue once it
                     * has finished. From this point on, it silently ignores any further input. The only way to process
                     * the remaining, still-unconsumed bytes belonging to this same message is to reset() the Inflater
                     * (clearing the finished state) and feed it just the unconsumed tail.
                     */
                    int remaining = inflater.getRemaining();
                    /*
                     * The unconsumed tail starts wherever the *current* input segment started, not necessarily at
                     * readBuffer.arrayOffset(): if this is the second (or later) recovery for the same message, the
                     * current segment already starts partway into readBuffer.
                     */
                    int newOffset = lastInputOffset + lastInputLength - remaining;
                    try {
                        inflater.reset();
                        // reset() discards the LZ77 window along with the
                        // finished state. If context takeover means that
                        // window should have survived, restore it so
                        // back-references into content decompressed before
                        // this reset keep resolving correctly.
                        if (inflaterWindowLength > 0) {
                            inflater.setDictionary(inflaterWindow, 0, inflaterWindowLength);
                        }
                    } catch (IllegalStateException | NullPointerException e) {
                        // As of Java 25, the JRE throws an ISE rather than an NPE
                        throw new IOException(sm.getString("perMessageDeflate.alreadyClosed"), e);
                    }
                    inflater.setInput(readBuffer.array(), newOffset, remaining);
                    lastInputOffset = newOffset;
                    lastInputLength = remaining;
                    // Continue decompression loop
                } else {
                    return endFrame(fin);
                }
            }
        }

        if (eomBytesInserted) {
            if (inflateEomBytes()) {
                return TransformationResult.OVERFLOW;
            }
            return endFrame(fin);
        }

        return TransformationResult.OVERFLOW;
    }


    private boolean inflateEomBytes() throws IOException {
        if (!eomBytesInserted) {
            inflater.setInput(EOM_BYTES);
            eomBytesInserted = true;
        }

        int written = inflate(eomOverflowBuffer, 0, eomOverflowBuffer.length);

        if (written > 0) {
            eomOverflowWritten = true;
            return true;
        }

        return false;
    }


    private int inflate(byte[] dest, int start, int len) throws IOException {
        int written;
        try {
            written = inflater.inflate(dest, start, len);
        } catch (DataFormatException e) {
            throw new IOException(sm.getString("perMessageDeflate.deflateFailed"), e);
        } catch (IllegalStateException | NullPointerException e) {
            // As of Java 25, the JRE throws an ISE rather than an NPE
            throw new IOException(sm.getString("perMessageDeflate.alreadyClosed"), e);
        }
        if (written > 0) {
            updateInflaterWindow(dest, start, written);
        }
        return written;
    }


    /*
     * Keeps inflaterWindow holding a rolling copy of the last up to inflaterWindow.length bytes of decompressed
     * output, across however many inflate() calls and messages that takes - tracked unconditionally, regardless of
     * inflaterContextTakeover (see the constructor and endFrame(), which is where that setting actually takes
     * effect, by clearing inflaterWindowLength at the end of a message when it is false). Called for every
     * successful inflate() (including the single-byte EOM overflow case), so it is the one place that needs to know
     * about that.
     */
    private void updateInflaterWindow(byte[] src, int off, int len) {
        if (len >= inflaterWindow.length) {
            System.arraycopy(src, off + len - inflaterWindow.length, inflaterWindow, 0, inflaterWindow.length);
            inflaterWindowLength = inflaterWindow.length;
        } else {
            int keep = Math.min(inflaterWindowLength, inflaterWindow.length - len);
            System.arraycopy(inflaterWindow, inflaterWindowLength - keep, inflaterWindow, 0, keep);
            System.arraycopy(src, off, inflaterWindow, keep, len);
            inflaterWindowLength = keep + len;
        }
    }


    private TransformationResult endFrame(boolean fin) throws IOException {
        eomBytesInserted = false;
        eomOverflowWritten = false;
        if (fin) {
            /*
             * If the message's final block was itself an independently BFINAL=1 terminated block (see the recovery in
             * getMoreData()), the EOM_BYTES appended to complete the message per RFC 7692 section 7.2.2 were fed to an
             * already-finished Inflater and were never consumed: inflater.finished() stays true with those 4 bytes
             * still reported by getRemaining(). Left in that state, the next message would compute its first recovery
             * offset from this stale, unrelated leftover count, which can go negative. There is no way to continue
             * decompressing past a finished Inflater in place, so it has to be reset here too - even though context
             * takeover may be enabled. Unlike the no-context-takeover case, the window built up so far is still wanted
             * for the next message, so restore it via setDictionary() rather than losing it.
             */
            if (!inflaterContextTakeover || inflater.finished()) {
                try {
                    inflater.reset();
                    if (inflaterContextTakeover && inflaterWindowLength > 0) {
                        inflater.setDictionary(inflaterWindow, 0, inflaterWindowLength);
                    }
                } catch (IllegalStateException | NullPointerException e) {
                    // As of Java 25, the JRE throws an ISE rather than an NPE
                    throw new IOException(sm.getString("perMessageDeflate.alreadyClosed"), e);
                }
                lastInputOffset = 0;
                lastInputLength = 0;
                if (!inflaterContextTakeover) {
                    /*
                     * The window was still legitimately maintained *within* this message (see inflate()), but must not
                     * survive into the next one when context takeover is disabled.
                     */
                    inflaterWindowLength = 0;
                }
            }
        }
        return TransformationResult.END_OF_FRAME;
    }


    @Override
    public boolean validateRsv(int rsv, byte opCode) {
        if (Util.isControl(opCode)) {
            if ((rsv & RSV_BITMASK) != 0) {
                return false;
            } else {
                if (next == null) {
                    return true;
                } else {
                    return next.validateRsv(rsv, opCode);
                }
            }
        } else {
            int rsvNext = rsv;
            if ((rsv & RSV_BITMASK) != 0) {
                rsvNext = rsv ^ RSV_BITMASK;
            }
            if (next == null) {
                return true;
            } else {
                return next.validateRsv(rsvNext, opCode);
            }
        }
    }


    @Override
    public Extension getExtensionResponse() {
        Extension result = new WsExtension(NAME);

        List<Extension.Parameter> params = result.getParameters();

        if (!serverContextTakeover) {
            params.add(new WsExtensionParameter(SERVER_NO_CONTEXT_TAKEOVER, null));
        }
        if (serverMaxWindowBits != -1) {
            params.add(new WsExtensionParameter(SERVER_MAX_WINDOW_BITS, Integer.toString(serverMaxWindowBits)));
        }
        if (!clientContextTakeover) {
            params.add(new WsExtensionParameter(CLIENT_NO_CONTEXT_TAKEOVER, null));
        }
        if (clientMaxWindowBits != -1) {
            params.add(new WsExtensionParameter(CLIENT_MAX_WINDOW_BITS, Integer.toString(clientMaxWindowBits)));
        }

        return result;
    }


    @Override
    public void setNext(Transformation t) {
        if (next == null) {
            this.next = t;
        } else {
            next.setNext(t);
        }
    }


    @Override
    public boolean validateRsvBits(int i) {
        if ((i & RSV_BITMASK) != 0) {
            return false;
        }
        if (next == null) {
            return true;
        } else {
            return next.validateRsvBits(i | RSV_BITMASK);
        }
    }


    @Override
    public List<MessagePart> sendMessagePart(List<MessagePart> uncompressedParts) throws IOException {
        List<MessagePart> allCompressedParts = new ArrayList<>();

        for (MessagePart uncompressedPart : uncompressedParts) {
            byte opCode = uncompressedPart.getOpCode();
            if (Util.isControl(opCode)) {
                // Control messages can appear in the middle of other messages
                // and must not be compressed. Pass it straight through.
                allCompressedParts.add(uncompressedPart);
                continue;
            }

            if (uncompressedPart.getPayload().limit() != 0) {
                emptyMessage = false;
            }
            if (emptyMessage && uncompressedPart.isFin()) {
                // Zero length messages can't be compressed so pass the
                // final (empty) part straight through.
                allCompressedParts.add(uncompressedPart);
            } else {
                List<MessagePart> compressedParts = new ArrayList<>();
                ByteBuffer uncompressedPayload = uncompressedPart.getPayload();
                SendHandler uncompressedIntermediateHandler = uncompressedPart.getIntermediateHandler();

                if (uncompressedPayload.hasArray()) {
                    deflater.setInput(uncompressedPayload.array(),
                            uncompressedPayload.arrayOffset() + uncompressedPayload.position(),
                            uncompressedPayload.remaining());
                } else {
                    byte[] bytes = new byte[uncompressedPayload.remaining()];
                    uncompressedPayload.get(bytes);
                    deflater.setInput(bytes, 0, bytes.length);
                }

                int flush = (uncompressedPart.isFin() ? Deflater.SYNC_FLUSH : Deflater.NO_FLUSH);
                boolean deflateRequired = true;

                while (deflateRequired) {
                    ByteBuffer compressedPayload = writeBuffer;

                    try {
                        int written = deflater.deflate(compressedPayload.array(),
                                compressedPayload.arrayOffset() + compressedPayload.position(),
                                compressedPayload.remaining(), flush);
                        compressedPayload.position(compressedPayload.position() + written);
                    } catch (IllegalStateException | NullPointerException e) {
                        // As of Java 25, the JRE throws an ISE rather than an NPE
                        throw new IOException(sm.getString("perMessageDeflate.alreadyClosed"), e);
                    }

                    if (!uncompressedPart.isFin() && compressedPayload.hasRemaining() && deflater.needsInput()) {
                        // This message part has been fully processed by the
                        // deflater. Fire the send handler for this message part
                        // and move on to the next message part.
                        break;
                    }

                    // If this point is reached, a new compressed message part
                    // will be created...
                    MessagePart compressedPart;

                    // .. and a new writeBuffer will be required.
                    writeBuffer = ByteBuffer.allocate(Constants.DEFAULT_BUFFER_SIZE);

                    // Flip the compressed payload ready for writing
                    compressedPayload.flip();

                    boolean fin = uncompressedPart.isFin();
                    boolean full = compressedPayload.limit() == compressedPayload.capacity();
                    boolean needsInput = deflater.needsInput();
                    boolean blocking = uncompressedPart.isBlocking();
                    long writeTimeoutExpiry = uncompressedPart.getWriteTimeoutExpiry();

                    if (fin && !full && needsInput) {
                        // End of compressed message. Drop EOM bytes and output.
                        compressedPayload.limit(compressedPayload.limit() - EOM_BYTES.length);
                        compressedPart = new MessagePart(true, getRsv(uncompressedPart), opCode, compressedPayload,
                                uncompressedIntermediateHandler, uncompressedIntermediateHandler, blocking,
                                writeTimeoutExpiry);
                        deflateRequired = false;
                        startNewMessage();
                    } else if (full && !needsInput) {
                        // Write buffer full and input message not fully read.
                        // Output and start new compressed part.
                        compressedPart = new MessagePart(false, getRsv(uncompressedPart), opCode, compressedPayload,
                                uncompressedIntermediateHandler, uncompressedIntermediateHandler, blocking,
                                writeTimeoutExpiry);
                    } else if (!fin && full/* note: needsInput is true here */) {
                        // Write buffer full and this part's input fully consumed, but the remainder of the message
                        // is not yet read.
                        // Output and get more data.
                        compressedPart = new MessagePart(false, getRsv(uncompressedPart), opCode, compressedPayload,
                                uncompressedIntermediateHandler, uncompressedIntermediateHandler, blocking,
                                writeTimeoutExpiry);
                        deflateRequired = false;
                    } else if (fin && full/* note: needsInput is true here */) {
                        // Write buffer full. Input fully read. Deflater may be
                        // in one of four states:
                        // - output complete (just happened to align with end of
                        // buffer
                        // - in middle of EOM bytes
                        // - about to write EOM bytes
                        // - more data to write
                        int eomBufferWritten;
                        try {
                            eomBufferWritten = deflater.deflate(EOM_BUFFER, 0, EOM_BUFFER.length, Deflater.SYNC_FLUSH);
                        } catch (NullPointerException e) {
                            throw new IOException(sm.getString("perMessageDeflate.alreadyClosed"), e);
                        }
                        if (eomBufferWritten < EOM_BUFFER.length) {
                            // EOM has just been completed
                            compressedPayload.limit(compressedPayload.limit() - EOM_BYTES.length + eomBufferWritten);
                            compressedPart = new MessagePart(true, getRsv(uncompressedPart), opCode, compressedPayload,
                                    uncompressedIntermediateHandler, uncompressedIntermediateHandler, blocking,
                                    writeTimeoutExpiry);
                            deflateRequired = false;
                            startNewMessage();
                        } else {
                            // More data to write
                            // Copy bytes to new write buffer
                            writeBuffer.put(EOM_BUFFER, 0, eomBufferWritten);
                            compressedPart = new MessagePart(false, getRsv(uncompressedPart), opCode, compressedPayload,
                                    uncompressedIntermediateHandler, uncompressedIntermediateHandler, blocking,
                                    writeTimeoutExpiry);
                        }
                    } else {
                        throw new IllegalStateException(sm.getString("perMessageDeflate.invalidState"));
                    }

                    // Add the newly created compressed part to the set of parts
                    // to pass on to the next transformation.
                    compressedParts.add(compressedPart);
                }

                SendHandler uncompressedEndHandler = uncompressedPart.getEndHandler();
                int size = compressedParts.size();
                if (size > 0) {
                    compressedParts.get(size - 1).setEndHandler(uncompressedEndHandler);
                }

                allCompressedParts.addAll(compressedParts);
            }
        }

        if (next == null) {
            return allCompressedParts;
        } else {
            return next.sendMessagePart(allCompressedParts);
        }
    }


    private void startNewMessage() throws IOException {
        firstCompressedFrameWritten = false;
        emptyMessage = true;
        if (isServer && !serverContextTakeover || !isServer && !clientContextTakeover) {
            try {
                deflater.reset();
            } catch (NullPointerException e) {
                throw new IOException(sm.getString("perMessageDeflate.alreadyClosed"), e);
            }
        }
    }


    private int getRsv(MessagePart uncompressedMessagePart) {
        int result = uncompressedMessagePart.getRsv();
        if (!firstCompressedFrameWritten) {
            result += RSV_BITMASK;
            firstCompressedFrameWritten = true;
        }
        return result;
    }


    @Override
    public void close() {
        // There will always be a next transformation
        next.close();
        inflater.end();
        deflater.end();
    }
}
