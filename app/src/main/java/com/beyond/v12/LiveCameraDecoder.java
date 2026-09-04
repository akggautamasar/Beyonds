package com.beyond.v12;

import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;

/** Robust optical receiver: accepts frames in any order, rejects duplicates/corruption,
 * and completes only after every frame is present. */
final class LiveCameraDecoder {
    private static final int HEADER = 64;
    private static final int PAYLOAD = 504;
    private static final long MIN_PROCESS_INTERVAL_MS = 70;

    private static final Map<Integer, byte[]> chunks = new HashMap<>();
    private static int total = -1;
    private static long expected = -1;
    private static String name = "file.bin";
    private static long lastProcess;
    private static boolean saving;

    static synchronized void reset() {
        chunks.clear();
        total = -1;
        expected = -1;
        name = "file.bin";
        lastProcess = 0;
        saving = false;
    }

    static void feed(byte[] y, int w, int h, MainActivity a) {
        long now = SystemClock.elapsedRealtime();
        synchronized (LiveCameraDecoder.class) {
            if (now - lastProcess < MIN_PROCESS_INTERVAL_MS) return;
            lastProcess = now;
            if (saving) return;
        }

        byte[] p;
        try {
            p = MainActivity.Vision.parse(y, w, h);
        } catch (Throwable t) {
            return;
        }
        if (p == null || p.length < HEADER) return;
        if (p[0] != 'B' || p[1] != 'Y' || p[2] != 'N' || p[3] != '1') return;

        int version = MainActivity.Packet.getInt(p, 4);
        int frame = MainActivity.Packet.getInt(p, 8);
        int t = MainActivity.Packet.getInt(p, 12);
        int len = MainActivity.Packet.getInt(p, 16);
        long fileLen = MainActivity.Packet.getLong(p, 20);
        int nameLen = MainActivity.Packet.getInt(p, 28);

        if (version != 1 || t <= 0 || t > 1000000 || frame < 0 || frame >= t || len < 0 || len > PAYLOAD || p.length < HEADER + len) return;
        if (fileLen < 0 || fileLen > (long)t * PAYLOAD) return;
        if (nameLen < 0 || nameLen > 24 || 32 + nameLen > 56) return;

        byte[] payload = new byte[len];
        System.arraycopy(p, HEADER, payload, 0, len);
        CRC32 crc = new CRC32();
        crc.update(payload, 0, payload.length);
        int stored = MainActivity.Packet.getInt(p, 56);
        if ((int)crc.getValue() != stored) return;

        boolean complete = false;
        byte[] result = null;
        String resultName = null;
        synchronized (LiveCameraDecoder.class) {
            if (total != t || expected != fileLen) {
                // A new Beyond stream/loop was detected. Start a clean assembly.
                chunks.clear();
                total = t;
                expected = fileLen;
                name = nameLen == 0 ? "file.bin" : new String(p, 32, nameLen, StandardCharsets.UTF_8);
            }
            if (!chunks.containsKey(frame)) {
                chunks.put(frame, payload);
            }
            if (chunks.size() == total) {
                try {
                    ByteArrayOutputStream out = new ByteArrayOutputStream((int)Math.min(expected, Integer.MAX_VALUE));
                    for (int i = 0; i < total; i++) {
                        byte[] c = chunks.get(i);
                        if (c == null) return;
                        out.write(c);
                    }
                    result = out.toByteArray();
                    if (result.length != expected) return;
                    resultName = name;
                    saving = true;
                } catch (Exception e) {
                    return;
                }
            }
        }

        if (complete || result == null) {
            int got;
            int all;
            synchronized (LiveCameraDecoder.class) { got = chunks.size(); all = total; }
            if (all > 0 && got % 5 == 0) {
                a.status("Receiving… " + got + "/" + all + " frames");
            }
            return;
        }
        final byte[] data = result;
        final String outName = resultName;
        a.status("RECEIVED\n" + data.length + " bytes\nSaving recovered file…");
        a.saveCameraResult(outName, data);
    }
}
