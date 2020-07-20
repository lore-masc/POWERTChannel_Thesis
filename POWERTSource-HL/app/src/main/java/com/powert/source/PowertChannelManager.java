package com.powert.source;

import android.content.Context;
import android.os.Process;
import android.util.Log;
import android.widget.LinearLayout;

import java.io.IOException;
import java.util.Random;

public class PowertChannelManager {
    private LinearLayout ll;
    private ModuleForwarder moduleForwarder;
    private boolean manchester;
    public static final int LONG_STREAM_SIZE = 40;
    public static final int PREAMBLE_SIZE = 5;
    public static long TIME = 500;                   // [ms]
    private static int count = 0;

    PowertChannelManager(LinearLayout ll, Context context) throws IOException {
        this.ll = ll;
        this.moduleForwarder = new ModuleForwarder(context);
        this.manchester = false;
        float[] input = new float[40 * 32];

        Random random = new Random();
        for(int i = 0; i < input.length; i++)
            input[i] = random.nextFloat() * 100 - 200;
        this.moduleForwarder.prepare(input);
    }

    /**
     * Send a single zero bit.
     */
    private void sendBit0() {
        long p1 = System.currentTimeMillis();
        for (int c = 0; c < count && System.currentTimeMillis() < p1 + PowertChannelManager.TIME; c++) {
            long start = System.currentTimeMillis();
            this.moduleForwarder.forward(ModuleForwarder.VERSION.LOW);
//                    Log.d(i + "POWERT-1", "Low: " + (System.currentTimeMillis() - start));
        }

        long p2 = PowertChannelManager.TIME - (System.currentTimeMillis() - p1);
        Log.d("POWERT", "Sleep time: " + p2);
        if (p2 > 0) {
            try {
                Thread.sleep(p2);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Send a single one bit.
     */
    private void sendBit1() {
        long p1 = System.currentTimeMillis();
        count = 0;
        while (System.currentTimeMillis() < p1 + PowertChannelManager.TIME) {
            long start = System.currentTimeMillis();
            this.moduleForwarder.forward(ModuleForwarder.VERSION.HIGH);
//                    Log.d(i + "POWERT-1", "High: " + (System.currentTimeMillis() - start));
            count++;
        }
    }

    /**
     * Send an array of bits using powert channel.
     * @param bits array of 1 or 0 integers.
     */
    private void sendStreamBits(int bits[]) {
        android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);
        for(int i = 0; i < bits.length; i++) {
            if (bits[i] == 1) {
                this.sendBit1();
                if (this.usingManchesterEncoding())
                    this.sendBit0();
            } else {
                this.sendBit0();
                if (this.usingManchesterEncoding())
                    this.sendBit1();
            }
//            Log.d("POWERT", "Bit " + i + " sended. It's a " + bits[i] + ". Total time " + (System.currentTimeMillis() - p1) + " - Count: " + count);
        }
    }

    /**
     * Send a long stream of 1 and 0 bits in order to synchronize sink.
     */
    private void sendLongStream () {
        int[] longStream = new int[PowertChannelManager.LONG_STREAM_SIZE];

        for (int i = 0; i < longStream.length; i++)
            if (i % 2 == 0)
                longStream[i] = 0;
            else
                longStream[i] = 1;
        this.sendStreamBits(longStream);
    }

    /**
     * Send a prefixed weight of zero bits in order to prepare sink to receive message.
     */
    private void sendPreamble () {
        int[] preambleStream = new int[PowertChannelManager.PREAMBLE_SIZE];
        this.sendStreamBits(preambleStream);
    }

    /**
     * Send a message converting the passed string into a binary encode.
     * The package will be composed with a long bit stream and a short preamble.
     * @param message String of message to send.
     */
    void sendPackage(String message) {
        int[] bits = getBitArray(message);
        this.sendLongStream();
        this.sendPreamble();
        this.sendStreamBits(bits);
    }

    /**
     * Check if Manchester encoding is enabled.
     * @return True if it is enabled.
     */
    private boolean usingManchesterEncoding() {
        return this.manchester;
    }

    /**
     * Set manchester encoding doubling the sending time.
     * @param enable true if Manchester encoding is enabled.
     */
    void useManchesterEncoding (boolean enable) {
        this.manchester = enable;
    }

    /**
     * Convert a string into int array.
     * @param message String to convert.
     * @return the array of bits.
     */
    static int[] getBitArray(String message) {
        byte[] bytes = message.getBytes();
        StringBuilder binary = new StringBuilder();
        for (byte b : bytes) {
            int val = b;
            for (int i = 0; i < 8; i++) {
                binary.append((val & 128) == 0 ? 0 : 1);
                val <<= 1;
            }
        }
        String stringBinary = binary.toString();
        int[] bits = new int[binary.length()];
        for (int i = 0; i < bits.length; i++)
            bits[i] = Integer.parseInt(stringBinary.substring(i, i+1));
        return bits;
    }

}
