package com.powert.speechcommandapp;

import android.content.Context;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.PyTorchAndroid;
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
    private IValue iValue;

    public ModuleForwarder(Context context) throws IOException {
        this.module_low = Module.load(assetFilePath(context, LOW_MODEL));
        this.module_high = Module.load(assetFilePath(context, HIGH_MODEL));
//        PyTorchAndroid.setNumThreads(1);
    }

    public void prepare(float[] input) {
        Tensor inputTensor = Tensor.fromBlob(input, shape);
        this.iValue = IValue.from(inputTensor);
    }

    public float[] forward(VERSION version) {
        Tensor outputTensor;
        if (version == VERSION.LOW) {
            outputTensor = module_low.forward(this.iValue).toTensor();
        } else {
            outputTensor = module_high.forward(this.iValue).toTensor();
        }
        return outputTensor.getDataAsFloatArray();
    }
}
