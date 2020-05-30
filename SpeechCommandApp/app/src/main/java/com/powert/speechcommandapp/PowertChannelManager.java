package com.powert.speechcommandapp;

import android.content.Context;
import android.os.Process;
import android.util.Log;

import java.io.IOException;
import java.util.Random;

public class PowertChannelManager {
    private ModuleForwarder moduleForwarder;
    public static final int LONG_STREAM_SIZE = 30;
    public static final int PREAMBLE_SIZE = 5;
    public static final long TIME = 500;                   // [ms]

    PowertChannelManager(Context context) throws IOException {
        this.moduleForwarder = new ModuleForwarder(context);
        float[] input = new float[40 * 32];

        Random random = new Random();
        for(int i = 0; i < input.length; i++)
            input[i] = random.nextFloat() * 100 - 200;
        this.moduleForwarder.prepare(input);
    }

    private void sendStreamBits(int bits[]) {
        long start, required;

        for(int i = 0; i < bits.length; i++) {
            long timer = PowertChannelManager.TIME;
            if (bits[i] == 1) {
                android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);
//                while (timer > 5) {
                    start = System.currentTimeMillis();
                    moduleForwarder.forward(ModuleForwarder.VERSION.HIGH);
                    required = System.currentTimeMillis() - start;
//                    Log.d("POWERT-1", "times " + required);
                if (PowertChannelManager.TIME - required > 1) {
                    while (timer > 0) {
                        start = System.currentTimeMillis();
                        this.moduleForwarder.forward(ModuleForwarder.VERSION.LOW);
                        required = System.currentTimeMillis() - start;
                        timer -= required;
                    }
                }
//                    timer -= required;
//                }
                Log.d(i + "POWERT-1", "" + timer);
            } else {
                android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

                start = System.currentTimeMillis();
                this.moduleForwarder.forward(ModuleForwarder.VERSION.LOW);
                required = System.currentTimeMillis() - start;

                if (PowertChannelManager.TIME - 10 - required > 0) {
                    try {
                        Thread.sleep(PowertChannelManager.TIME - required - 2);
//                        Thread.sleep(PowertChannelManager.TIME);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                Log.d(i + "POWERT-0", " " + (PowertChannelManager.TIME - (System.currentTimeMillis() - start)));
            }
//          Log.d("POWERT", "Bit " + i + " sended.");
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
