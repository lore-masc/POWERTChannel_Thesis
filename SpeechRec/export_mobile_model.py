import argparse
from pathlib import Path
import torch
from torch.utils.data import DataLoader
from torchvision.transforms import Compose
from model import DenseNet
from speech_commands_dataset import CLASSES
from transforms import ToMelSpectrogram, ToTensor, LoadAudio, FixAudioLength


def get_net(import_model_path, depth, growthRate):
    net = DenseNet(depth=depth, growthRate=growthRate, compressionRate=2, num_classes=len(CLASSES), in_channels=1)
    if Path(import_model_path).exists():
        # forcing all GPU tensors to be in CPU while loading
        state_dict = torch.load(import_model_path, map_location=lambda storage, loc: storage)
        net.load_state_dict(state_dict, strict=False)
    return net


def export_model(input_path, net, export_model_path):
    dsize = (1, 1, 40, 32)

    feature_transform = Compose([ToMelSpectrogram(n_mels=40), ToTensor('mel_spectrogram', 'input')])
    transform = Compose([LoadAudio(), FixAudioLength(), feature_transform])

    audio = transform({'path': input_path})
    audio_loader = DataLoader(audio, batch_size=1, shuffle=False,
                              pin_memory=False, num_workers=2)
    audio = audio_loader.dataset['input']
    audio = torch.unsqueeze(audio, 1)
    audio = audio.view(dsize)

    net.cpu()
    net.eval()
    traced_script_module = torch.jit.trace(net, audio)
    traced_script_module.save(export_model_path)
    print("Model saved in " + export_model_path)


def main():
    parser = argparse.ArgumentParser(description='Detect speech command by neural network DenseNet')
    parser.add_argument('-d', '--data', action='store', dest='data', required=True,
                        help='Specify the input path to use with example for the net trace.')
    parser.add_argument('-v', '--version', action='store', dest='version', required=True, choices=['low', 'high'],
                        help='Specify the version of the net to export.')

    args = parser.parse_args()

    if args.version == 'low':
        depth = 22
        growthRate = 12
        import_model_path = 'model/weights_dn_22_12.pth'
        export_model_path = 'model/mobile_densenet_22_12.pt'
    else:
        depth = 250
        growthRate = 24
        import_model_path = 'model/weights_dn_250_24.pth'
        export_model_path = 'model/mobile_densenet_250_24.pt'

    net = get_net(import_model_path=import_model_path, depth=depth, growthRate=growthRate).to(torch.device("cpu"))
    export_model(input_path=args.data, net=net, export_model_path=export_model_path)


if __name__ == '__main__':
    main()
