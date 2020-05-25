package com.powert.speechcommandapp;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public class PowertChannelManager {
    private ModuleForwarder moduleForwarder;
    public static final int LONG_STREAM_SIZE = 180;
    public static final int PREAMBLE_SIZE = 5;
    public static final long TIME = 200;                   // [ms]
    public static final long WAIT = 15;                     // [ms]
    private float[] input;

    PowertChannelManager(Context context) throws IOException {
        this.moduleForwarder = new ModuleForwarder(context);
        this.input = new float[40*32];

        Random random = new Random();
        for(int i = 0; i < this.input.length; i++)
            this.input[i] = random.nextFloat() * 100 - 200;
        this.moduleForwarder.prepare(this.input);
    }

    private void sendStreamBits(int bits[]) {
        long start, end;
        for(int i = 0; i < bits.length; i++) {
            long timer = PowertChannelManager.TIME;
            if (bits[i] == 1)
                while (timer > 0) {
                    start = System.currentTimeMillis();
                    this.moduleForwarder.forward(ModuleForwarder.VERSION.HIGH);
                    try {
                        Thread.sleep(PowertChannelManager.WAIT);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    end = System.currentTimeMillis();
                    timer -= (end - start);
                }
            else
                while (timer > 0) {
                    start = System.currentTimeMillis();
                    this.moduleForwarder.forward(ModuleForwarder.VERSION.LOW);
                    try {
                        Thread.sleep(PowertChannelManager.WAIT);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    end = System.currentTimeMillis();
                    timer -= (end - start);
                }
            Log.d("POWERT", "bit " + i);
        }


    }

    /**
     * Send a long stream of 1 and 0 bits in order to synchronize sink.
     */
    private void sendLongStream () {
        int[] longStream = new int[PowertChannelManager.LONG_STREAM_SIZE];
        for (int i = 0; i < longStream.length; i++)
            if (i % 2 == 0)
                longStream[i] = 1;
            else
                longStream[i] = 0;
        this.sendStreamBits(longStream);
    }

    /**
     * Send a prefixed weight of zero bits in order to prepare sink to receive message.
     */
    private void sendPreamble () {
        int[] preambleStream = new int[PowertChannelManager.PREAMBLE_SIZE];
        for (int i = 0; i < preambleStream.length; i++)
            preambleStream[i] = 0;
        this.sendStreamBits(preambleStream);
    }

    /**
     * Send a message.
     * @param message String of message to send.
     */
    void sendPackage(String message) {
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

        this.sendLongStream();
        this.sendPreamble();
        this.sendStreamBits(bits);
    }

}
