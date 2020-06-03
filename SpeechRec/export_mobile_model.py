import argparse
from pathlib import Path

import torch
from torch.utils.data import DataLoader
from torchvision.transforms import Compose

from main import get_cost_function, get_data, test, get_model_weights, prepare_model
from speech_commands_dataset import CLASSES
from transforms import ToMelSpectrogram, ToTensor, LoadAudio, FixAudioLength


def quantize_model(model, backend):
    if backend not in torch.backends.quantized.supported_engines:
        raise RuntimeError("Quantized backend not supported ")
    torch.backends.quantized.engine = backend
    model.eval()

    # Make sure that weight qconfig matches that of the serialized models
    my_qconfig = torch.quantization.QConfig(activation=torch.quantization.MinMaxObserver.with_args(dtype=torch.quint8),
                                            weight=torch.quantization.default_observer.with_args(dtype=torch.qint8))
    if backend == 'fbgemm':
        model.qconfig = torch.quantization.QConfig(
            activation=torch.quantization.default_observer,
            weight=torch.quantization.default_per_channel_weight_observer)
    elif backend == 'qnnpack':
        model.qconfig = my_qconfig

    model.fuse_model()
    torch.quantization.prepare(model, inplace=True)

    # Calibrate with the training set
    batch_size = 32
    num_batches = 10
    print('Calibration in progress. Total batches: ' + str(batch_size * num_batches))
    cost_function = get_cost_function()
    train_loader, test_loader = get_data(batch_size=batch_size,
                                         root='data/', use_gpu=False)
    test_loss, test_accuracy = test(model, train_loader, cost_function, 'cpu', num_batches=num_batches)
    print()
    print('Test loss: %.5f, Test accuracy: %.2f' % (test_loss, test_accuracy))

    torch.quantization.convert(model, inplace=True)

    return


def get_net(model, quantize=False):
    empty_net = prepare_model(model=model)
    import_model_path = get_model_weights(model=model)

    if Path(import_model_path).exists():
        net = empty_net

        if quantize:
            backend = 'qnnpack'
            state_dict = torch.load(import_model_path, map_location=lambda storage, loc: storage)
            net.load_state_dict(state_dict, strict=False)
            quantize_model(model=net, backend=backend)
        else:
            state_dict = torch.load(import_model_path, map_location=lambda storage, loc: storage)
            net.load_state_dict(state_dict, strict=False)
    else:
        raise Exception("File to import does not exists.")

    return net


def export_model(model, input_path, quantize=False):
    dsize = (1, 1, 40, 32)

    feature_transform = Compose([ToMelSpectrogram(n_mels=40), ToTensor('mel_spectrogram', 'input')])
    transform = Compose([LoadAudio(), FixAudioLength(), feature_transform])

    audio = transform({'path': input_path})
    audio_loader = DataLoader(audio, batch_size=1, shuffle=False,
                              pin_memory=False, num_workers=2)
    audio = audio_loader.dataset['input']
    audio = torch.unsqueeze(audio, 1)
    audio = audio.view(dsize)

    net = get_net(model=model, quantize=False)
    net.cpu()
    net.eval()

    export_model_path = get_model_weights(model=model).replace("weights", "mobile").replace(".pth", ".pt")

    if quantize:
        # saving quantized version
        quantized_net = get_net(model=model, quantize=True).to(torch.device("cpu"))

        # Test
        num_batches = 5
        batch_size = 32
        print('Test on ' + str(batch_size * num_batches) + ' samples')
        cost_function = get_cost_function()
        train_loader, test_loader = get_data(batch_size=batch_size,
                                             root='data/', use_gpu=False)
        test_loss, test_accuracy = test(quantized_net, test_loader, cost_function, 'cpu', num_batches=num_batches)
        print()
        print('Test loss: %.5f, Test accuracy: %.2f' % (test_loss, test_accuracy))

        # saving quantized version for mobile
        traced_script_module = torch.jit.trace(quantized_net, audio, check_trace=False)
        export_quantized_model_path = export_model_path.split('.')[0] + '_quantized.pt'
        traced_script_module.save(export_quantized_model_path)
        print("Exporting quantized version in " + export_quantized_model_path)

    # saving not-quantized version for mobile
    traced_script_module = torch.jit.trace(net, audio)
    traced_script_module.save(export_model_path)
    print("Exporting model in " + export_model_path)
    output = traced_script_module(audio)
    class_index = output.data.max(1, keepdim=True)[1]
    print("[" + CLASSES[class_index] + "]")


def main():
    parser = argparse.ArgumentParser(description='Detect speech command by neural network DenseNet')
    parser.add_argument('-d', '--data', action='store', dest='data', required=True,
                        help='Specify the input path to use with example for the net trace.')
    parser.add_argument('-q', '--quantize', action='store_true', dest='quantize', default=False,
                        help='True if you want perform quantization conversion.')
    parser.add_argument('-m', '--model', action='store', dest='model', required=True,
                        choices=['shufflenet', 'mobilenet', 'resnet'],
                        help='True if you want perform quantization conversion.')

    args = parser.parse_args()

    export_model(model=args.model, input_path=args.data, quantize=args.quantize)


if __name__ == '__main__':
    main()
