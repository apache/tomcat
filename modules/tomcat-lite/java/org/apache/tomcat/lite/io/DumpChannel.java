/*
 */
package org.apache.tomcat.lite.io;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

// TODO: dump to a file, hex, etc.

/**
 * For debug - will print all bytes that go trough the channel
 */
public class DumpChannel extends IOChannel {

    IOBuffer in = new IOBuffer(this);
    IOBuffer out = new IOBuffer(this);
    static final boolean dumpToFile = false;
    static int idCnt = 0;

    DumpChannel(String id) {
        this.id = id + idCnt++;
    }

    public static IOChannel wrap(String id, IOChannel net) throws IOException {
        if (id == null) {
            id = "";
        }
        DumpChannel dmp = new DumpChannel(id + idCnt++);
        net.setHead(dmp);
        return dmp;
    }

    public String toString() {
        return "Dump-" + id + "-" + net.toString();
    }

    @Override
    public void handleReceived(IOChannel ch) throws IOException {
        processInput(ch.getIn());
    }

    private void processInput(IOBuffer netIn) throws IOException {
        boolean any = false;
        while (true) {
            BBucket first = netIn.popFirst();
            if (first == null) {
                if (netIn.isClosedAndEmpty()) {
                    out("IN", first, true);
                    in.close();
                    any = true;
                }
                if (any) {
                    sendHandleReceivedCallback();
                }
                return;
            }
            any = true;
            out("IN", first, false);
            if (!in.isAppendClosed()) {
                in.queue(first);
            }
        }
    }

    public void startSending() throws IOException {
        while (true) {
            BBucket first = out.popFirst();
            if (first == null) {
                if (out.isClosedAndEmpty()) {
                    out("OUT", first, true);
                    net.getOut().close();
                }

                net.startSending();
                return;
            }
            // Dump
            out("OUT", first, net.getOut().isAppendClosed());
            net.getOut().queue(first);
        }
    }

    static int did = 0;

    protected void out(String dir, BBucket first, boolean closed) {
        // Dump
        if (first != null) {
            String hd = Hex.getHexDump(first.array(), first.position(),
                    first.remaining(), true);
            System.err.println("\n" + dir + ": " + id + " " +
                    (closed ? "CLS" : "") +
                    + first.remaining() + "\n" +
                    hd);
        } else {
            System.err.println("\n" + dir + ": " + id + " " +
                    (closed ? "CLS " : "") +
                     "END\n");
        }
        if (dumpToFile && first != null) {
            try {
                OutputStream os = new FileOutputStream("dmp" + did++);
                os.write(first.array(), first.position(), first.remaining());
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public IOBuffer getIn() {
        return in;
    }

    @Override
    public IOBuffer getOut() {
        return out;
    }
}
