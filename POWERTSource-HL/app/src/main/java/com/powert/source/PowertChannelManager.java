package com.powert.source;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Process;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Random;

public class PowertChannelManager {
    private LinearLayout ll;
    private ModuleForwarder moduleForwarder;
    private Context context;
    private boolean manchester;
    public static int LONG_STREAM_SIZE = 40;
    public static final int PREAMBLE_SIZE = 5;
    public static long TIME = 500;                   // [ms]
    private static int count = 0;
    ArrayList<Long> logTimestamps;
    ArrayList<Integer> logManchesterBits;
    ArrayList<Integer> logStandardBits;
    private boolean running;

    PowertChannelManager(LinearLayout ll, Context context) throws IOException {
        this.ll = ll;
        this.context = context;
        this.moduleForwarder = new ModuleForwarder(context);
        this.manchester = false;
        this.running = false;

        float[] input = new float[40 * 32];

        Random random = new Random();
        for(int i = 0; i < input.length; i++)
            input[i] = random.nextFloat() * 100 - 200;
        this.moduleForwarder.prepare(input);
    }

    /**
     * Interrupt the session.
     */
    public void interrupt() {
        this.running = false;
    }

    /**
     * Send a single zero bit.
     */
    private void sendBit0() {
        this.logTimestamps.add(System.currentTimeMillis());
        this.logManchesterBits.add(0);
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
        this.logTimestamps.add(System.currentTimeMillis());
        this.logManchesterBits.add(1);
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
        for(int i = 0; i < bits.length && this.running; i++) {
            long p1 = System.currentTimeMillis();
            if (bits[i] == 1) {
                this.logStandardBits.add(1);
                this.sendBit1();
                if (this.usingManchesterEncoding())
                    this.sendBit0();
            } else {
                this.logStandardBits.add(0);
                this.sendBit0();
                if (this.usingManchesterEncoding())
                    this.sendBit1();
            }
            Log.d("POWERT-"+p1, "Bit " + i + " sended. It's a " + bits[i] + ". Total time " + (System.currentTimeMillis() - p1) + " - Count: " + count);
        }
    }

    /**
     * Send a long stream of 1 and 0 bits in order to synchronize sink.
     */
    private void sendLongStream () {
        int[] longStream = new int[PowertChannelManager.LONG_STREAM_SIZE];

        for (int i = 0; i < longStream.length && this.running; i++)
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
     * Set number of bit in long stream.
     * @param size number of bits.
     */
    public void setLongStreamSize (int size) { LONG_STREAM_SIZE = size; }

    /**
     * Send a message converting the passed string into a binary encode.
     * The package will be composed with a long bit stream and a short preamble.
     * @param message String of message to send.
     * @param encode_type type of encoding to convert.
     */
    void sendPackage(String message, PocFragment.ENCODE_TYPE encode_type, final int sessions) {
        this.logTimestamps = new ArrayList<>();
        this.logManchesterBits = new ArrayList<>();
        this.logStandardBits = new ArrayList<>();
        this.running = true;
        final int[] bits = (encode_type == PocFragment.ENCODE_TYPE.CHARACTER) ? stringToBitArray(message) : bitsToBitArray(message);

        @SuppressLint("StaticFieldLeak") final AsyncTask<Void, String, Void> asyncTask = new AsyncTask<Void, String, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                int iterations = Math.max(1, sessions);
                int i = 0;
                do {
                    publishProgress("Session " + (i+1) + " - Long stream sending...");
                    sendLongStream();
                    publishProgress("Session " + (i+1) + " - Preamble sending...");
                    sendPreamble();
                    publishProgress("Session " + (i+1) + " - Message sending...");
                    sendStreamBits(bits);
                    i++;
                    if (i < iterations && iterations > 1) {
                        publishProgress("Session " + (i) + " - Terminating...");
                        long wait_time = (usingManchesterEncoding()) ? 16*PowertChannelManager.TIME : 8*PowertChannelManager.TIME;
                        try {
                            Thread.sleep(wait_time);   // window of null byte
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                } while (i < iterations && running);
                publishProgress("Done.");
                return null;
            }

            @Override
            protected void onProgressUpdate(String... values) {
                super.onProgressUpdate(values);
                TextView textView10 = ll.findViewById(R.id.textView10);

                if (values.length == 1) {
                    if (!values[0].equals("")) {
                        textView10.setText(values[0]);
                        Toast.makeText(context.getApplicationContext(), values[0], Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                Toast.makeText(context, "Message sent", Toast.LENGTH_SHORT).show();
                try {
                    exportLogs(Environment.getExternalStorageDirectory().getAbsolutePath()+"/Source-HL.csv");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        asyncTask.execute();
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
     * Convert the string in correspondent bits. Then, they are stored into int array.
     * @param message String to convert.
     * @return the array of bits.
     */
    static int[] stringToBitArray(String message) {
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

    /**
     * Convert the represented bits into int array.
     * @param message String containing represented bits.
     * @return the array of bits.
     */
    static int[] bitsToBitArray(String message) {
        int[] bits = new int[message.length()];
        char[] chars = message.toCharArray();
        for (int i = 0; i < chars.length; i++)
            if (chars[i] == '0') bits[i] = 0;
            else bits[i] = 1;
        return bits;
    }

    /**
     * Export csv file for external analysis.
     * @param FILE_PATH Path of the exported file.
     * @throws IOException
     */
    private void exportLogs(final String FILE_PATH) throws IOException {
        File file = new File(FILE_PATH);
        if (file.exists())
            file.delete();
        FileOutputStream fileOutputStream = new FileOutputStream(file, true);
        OutputStreamWriter writer = new OutputStreamWriter(fileOutputStream);
        String header = "Timestamps, Manchester bits, Standard bits\n";
        writer.write(header);

        for (int i = 0; i < this.logTimestamps.size(); i++) {
            writer.write(this.logTimestamps.get(i) + ", " + this.logManchesterBits.get(i) + ", " + this.logStandardBits.get((this.manchester) ? i/2 : i));
            writer.write("\n");
        }
        writer.close();
        fileOutputStream.close();
    }

}
