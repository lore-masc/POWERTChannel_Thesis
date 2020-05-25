package com.powert.speechcommandapp;

import android.content.Context;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;

import java.io.IOException;

import static com.powert.speechcommandapp.DebugFragment.assetFilePath;

public class ModuleForwarder {
    public enum VERSION {LOW, HIGH};
    private static final String LOW_MODEL = "mobile_mobilenet_2_quantized.pt";
    private static final String HIGH_MODEL = "mobile_mobilenet_2.pt";
    long[] shape = {1, 1, 40, 32};
    private Module module_low;
    private Module module_high;
    private Tensor inputTensor;

    public ModuleForwarder(Context context) throws IOException {
            this.module_low = Module.load(assetFilePath(context, LOW_MODEL));
            this.module_high = Module.load(assetFilePath(context, HIGH_MODEL));
    }

    public void prepare(float[] input) {
        this.inputTensor = Tensor.fromBlob(input, shape);
    }

    public float[] forward(VERSION version) {
        Tensor outputTensor;
        if (version == VERSION.LOW) {
            outputTensor = module_low.forward(IValue.from(this.inputTensor)).toTensor();
        } else {
            outputTensor = module_high.forward(IValue.from(this.inputTensor)).toTensor();
        }
        return outputTensor.getDataAsFloatArray();
    }
}
