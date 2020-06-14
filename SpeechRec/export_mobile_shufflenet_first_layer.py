import argparse
from pathlib import Path

import torch
from torch.utils.data import DataLoader
from torchvision.transforms import Compose

from main import get_model_weights, CLASSES
from model import shufflenet_v2_x0_5_fistLayer, resnet18, mobilenet_v2
from transforms import ToMelSpectrogram, ToTensor, LoadAudio, FixAudioLength


def get_net(model):
    if model == 'shufflenet':
        net = shufflenet_v2_x0_5_fistLayer(num_classes=len(CLASSES))
    elif model == 'mobilenet':
        net = mobilenet_v2(num_classes=len(CLASSES), width_mult=0.03125)
    elif model == 'resnet':
        net = resnet18(num_classes=len(CLASSES))

    import_model_path = get_model_weights(model=model)

    if Path(import_model_path).exists():
        state_dict = torch.load(import_model_path, map_location=lambda storage, loc: storage)
        net.load_state_dict(state_dict, strict=False)
    else:
        raise Exception("File to import does not exists.")

    return net


def export_model(model, input_path):
    dsize = (1, 1, 40, 32)

    feature_transform = Compose([ToMelSpectrogram(n_mels=40), ToTensor('mel_spectrogram', 'input')])
    transform = Compose([LoadAudio(), FixAudioLength(), feature_transform])

    audio = transform({'path': input_path})
    audio_loader = DataLoader(audio, batch_size=1, shuffle=False,
                              pin_memory=False, num_workers=2)
    audio = audio_loader.dataset['input']
    audio = torch.unsqueeze(audio, 1)
    audio = audio.view(dsize)

    net = get_net(model=model)
    net.cpu()
    net.eval()

    export_model_path = get_model_weights(model=model).replace("weights", "mobile").replace(".pth", "firstLayer.pt")

    # saving for mobile
    traced_script_module = torch.jit.trace(net, audio)
    traced_script_module.save(export_model_path)
    print("Exporting model in " + export_model_path)


def main():
    parser = argparse.ArgumentParser(description='Export the first layer of a model.')
    parser.add_argument('-d', '--data', action='store', dest='data', required=True,
                        help='Specify the input path to use with example for the net trace.')
    parser.add_argument('-m', '--model', action='store', dest='model', required=True,
                        choices=['shufflenet', 'mobilenet', 'resnet'],
                        help='True if you want perform quantization conversion.')

    args = parser.parse_args()

    export_model(model=args.model, input_path=args.data)


if __name__ == '__main__':
    main()
