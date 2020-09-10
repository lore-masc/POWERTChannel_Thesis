package com.powert.powertnoiser;

import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.util.regex.Pattern;

public class Utils {

    static String get_param(String path) throws IOException {
        ProcessBuilder cmd;
        String[] command = {"/system/bin/cat", path};
        cmd = new ProcessBuilder(command);
        Process process = cmd.start();

        BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
        StringBuilder values = new StringBuilder();
        values.append(bufferedReader.readLine());
        bufferedReader.close();
        return values.toString();
    }

    //for multi core value
    static float readCore(long wait) {
        /*
         * how to calculate multicore
         * this function reads the bytes from a logging file in the android system (/proc/stat for cpu values)
         * then puts the line into a string
         * then splits up each individual part into an array
         * then(since he know which part represents what) we are able to determine each cpu total and work
         * then combine it together to get a single float for overall cpu usage
         */
        try {
            RandomAccessFile reader = new RandomAccessFile("/proc/stat", "r");
            //skip to the line we need
            /*
            for(int ii = 0; ii < i + 1; ++ii)
                reader.readLine();
             */
            String load = reader.readLine();

            //cores will eventually go offline, and if it does, then it is at 0% because it is not being
            //used. so we need to do check if the line we got contains cpu, if not, then this core = 0
            if(load.contains("cpu")) {
                String[] toks = load.split("  *");

                //we are recording the work being used by the user and system(work) and the total info
                //of cpu stuff (total)
                //https://stackoverflow.com/questions/3017162/how-to-get-total-cpu-usage-in-linux-c/3017438#3017438

                long work1 = Long.parseLong(toks[1])+ Long.parseLong(toks[2]) + Long.parseLong(toks[3]);
                long total1 = Long.parseLong(toks[1])+ Long.parseLong(toks[2]) + Long.parseLong(toks[3]) +
                        Long.parseLong(toks[4]) + Long.parseLong(toks[5])
                        + Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);

                try {
                    //short sleep time = less accurate. But android devices typically don't have more than
                    //4 cores, and I'n my app, I run this all in a second. So, I need it a bit shorter
                    Thread.sleep(wait);
                } catch (Exception e) {}

                reader.seek(0);
                //skip to the line we need
                /*
                for(int ii = 0; ii < i + 1; ++ii)
                    reader.readLine();
                 */
                load = reader.readLine();
                //cores will eventually go offline, and if it does, then it is at 0% because it is not being
                //used. so we need to do check if the line we got contains cpu, if not, then this core = 0%
                if(load.contains("cpu")) {
                    reader.close();
                    toks = load.split("  *");

                    long work2 = Long.parseLong(toks[1])+ Long.parseLong(toks[2]) + Long.parseLong(toks[3]);
                    long total2 = Long.parseLong(toks[1])+ Long.parseLong(toks[2]) + Long.parseLong(toks[3]) +
                            Long.parseLong(toks[4]) + Long.parseLong(toks[5])
                            + Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);

                    //here we find the change in user work and total info, and divide by one another to get our total
                    //seems to be accurate need to test on quad core
                    //https://stackoverflow.com/questions/3017162/how-to-get-total-cpu-usage-in-linux-c/3017438#3017438

                    return (float)(work2 - work1) / ((total2 - total1));
                } else {
                    reader.close();
                    return 0;
                }

            } else {
                reader.close();
                return 0;
            }
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }

        return 0;
    }

    static int getNumCores() {
        //Private Class to display only CPU devices in the directory listing
        class CpuFilter implements FileFilter {
            @Override
            public boolean accept(File pathname) {
                //Check if filename is "cpu", followed by one or more digits
                if(Pattern.matches("cpu[0-9]+", pathname.getName())) {
                    return true;
                }
                return false;
            }
        }

        try {
            //Get directory containing CPU info
            File dir = new File("/sys/devices/system/cpu/");
            //Filter to only list the devices we care about
            File[] files = dir.listFiles(new CpuFilter());
            //Return the number of cores (virtual CPU devices)
            return files.length;
        } catch(Exception e) {
            //Default to return 1 core
            return 1;
        }
    }

    static float topProcessCPU() {
        Process process;
        float perc = 0;
        try {
            process = Runtime.getRuntime().exec("top -n 1 -d 1 -m 1");
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            int read;
            char[] buffer = new char[4096];
            StringBuffer output = new StringBuffer();
            output.append(buffer, 0, bufferedReader.read(buffer));
            bufferedReader.close();

            // Waits for the command to finish.
//            process.waitFor();
            perc = Float.parseFloat(output.toString().split("\n")[7].split("  ")[2].split("%")[0]);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return perc/100f;
    }
}
