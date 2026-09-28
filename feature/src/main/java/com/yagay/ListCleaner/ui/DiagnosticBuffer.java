package com.yagay.ListCleaner.ui;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Bounded head+tail retention. Draining a noisy process never grows a temporary file. */
public final class DiagnosticBuffer {
    private final byte[] head;
    private final byte[] tail;
    private int headSize, tailSize, tailNext;
    private long total;

    public DiagnosticBuffer(int capacity) {
        if (capacity < 4) throw new IllegalArgumentException("capacity < 4");
        head = new byte[Math.min(capacity / 4, 256 * 1024)];
        tail = new byte[capacity - head.length];
    }

    public synchronized void append(byte[] data, int length) {
        if (length < 0 || length > data.length) throw new IllegalArgumentException("length");
        total += length;
        int offset = 0;
        int count = Math.min(length, head.length - headSize);
        System.arraycopy(data, 0, head, headSize, count);
        headSize += count;
        offset += count;
        int remaining = length - offset;
        if (remaining >= tail.length) {
            System.arraycopy(data, length - tail.length, tail, 0, tail.length);
            tailSize = tail.length;
            tailNext = 0;
            return;
        }
        while (remaining > 0) {
            count = Math.min(remaining, tail.length - tailNext);
            System.arraycopy(data, offset, tail, tailNext, count);
            tailNext = (tailNext + count) % tail.length;
            tailSize = Math.min(tail.length, tailSize + count);
            offset += count;
            remaining -= count;
        }
    }

    public synchronized long totalBytes() { return total; }
    public synchronized boolean truncated() { return total > head.length + tail.length; }

    public synchronized byte[] snapshot() {
        ByteArrayOutputStream out = new ByteArrayOutputStream(headSize + tailSize + 100);
        out.write(head, 0, headSize);
        if (truncated()) {
            byte[] marker = ("\n[... omitted " + (total - headSize - tailSize) + " bytes ...]\n")
                .getBytes(StandardCharsets.UTF_8);
            out.write(marker, 0, marker.length);
        }
        int start = tailSize == tail.length ? tailNext : 0;
        int first = Math.min(tailSize, tail.length - start);
        out.write(tail, start, first);
        out.write(tail, 0, tailSize - first);
        return out.toByteArray();
    }
}
