import argparse
from pathlib import Path

import torch
from torch.utils.data import DataLoader
from torchvision.transforms import Compose

from main import get_cost_function, get_data, test
from model.resnet import resnet18, resnext101_32x8d, quantize_model
from speech_commands_dataset import CLASSES
from transforms import ToMelSpectrogram, ToTensor, LoadAudio, FixAudioLength


def get_net(import_model_path, version='low', quantize=False):
    if version == 'low':
        empty_net = resnet18(num_classes=len(CLASSES), quantize=False)
    else:
        empty_net = resnext101_32x8d(num_classes=len(CLASSES), quantize=False)

    if Path(import_model_path).exists():
        net = empty_net

        if quantize:
            # backend = 'fbgemm'
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


def export_model(input_path, version, quantize=False):
    if version == 'low':
        import_model_path = 'model/weights_rn_18.pth'
        export_model_path = 'model/mobile_resnet_18.pt'
    else:
        import_model_path = 'model/weights_rn_101.pth'
        export_model_path = 'model/mobile_resnext_101.pt'

    dsize = (1, 1, 40, 32)

    feature_transform = Compose([ToMelSpectrogram(n_mels=40), ToTensor('mel_spectrogram', 'input')])
    transform = Compose([LoadAudio(), FixAudioLength(), feature_transform])

    audio = transform({'path': input_path})
    audio_loader = DataLoader(audio, batch_size=1, shuffle=False,
                              pin_memory=False, num_workers=2)
    audio = audio_loader.dataset['input']
    audio = torch.unsqueeze(audio, 1)
    audio = audio.view(dsize)

    net = get_net(version=version, import_model_path=import_model_path, quantize=False).to(torch.device("cpu"))
    net.cpu()
    net.eval()

    if quantize:
        # saving quantized version
        quantized_net = get_net(version=version, import_model_path=import_model_path, quantize=True).to(
            torch.device("cpu"))
        # quantized_model_path = import_model_path.split('.')[0] + '_quantized.pth'
        # torch.jit.save(torch.jit.script(quantized_net), quantized_model_path)
        # print("Saving quantized weights in " + quantized_model_path)

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
        traced_script_module = torch.jit.trace(quantized_net, audio)
        export_quantized_model_path = export_model_path.split('.')[0] + '_quantized.pt'
        traced_script_module.save(export_quantized_model_path)
        print("Exporting quantized version in " + export_quantized_model_path)

    # saving not-quantized version for mobile
    traced_script_module = torch.jit.trace(net, audio)
    traced_script_module.save(export_model_path)
    print("Exporting model in " + export_model_path)


def main():
    parser = argparse.ArgumentParser(description='Detect speech command by neural network DenseNet')
    parser.add_argument('-d', '--data', action='store', dest='data', required=True,
                        help='Specify the input path to use with example for the net trace.')
    parser.add_argument('-q', '--quantize', action='store_true', dest='quantize', default=False,
                        help='True if you want perform quantization conversion.')
    parser.add_argument('-v', '--version', action='store', dest='version', required=True, choices=['low', 'high'],
                        help='Specify the version of the net to export.')

    args = parser.parse_args()

    export_model(input_path=args.data, version=args.version, quantize=args.quantize)


if __name__ == '__main__':
    main()
