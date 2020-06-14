from torch.nn import Conv2d
from torchvision.models import ShuffleNetV2, MobileNetV2, ResNet
from torchvision.models.resnet import BasicBlock


class ShuffleNetFirstLayer(ShuffleNetV2):
    def __init__(self, *args, **kwargs):
        super(ShuffleNetFirstLayer, self).__init__(*args, **kwargs)

    def _forward_impl(self, x):
        x = self.conv1(x)
        return x


class MobileNetV2FistLayer(MobileNetV2):
    def __init__(self, *args, **kwargs):
        super(MobileNetV2FistLayer, self).__init__(*args, **kwargs)

    def _forward_impl(self, x):
        x = self.features[0][0](x)
        return x


class ResNetFirstLayer(ResNet):
    def __init__(self, *args, **kwargs):
        super(ResNetFirstLayer, self).__init__(*args, **kwargs)

    def _forward_impl(self, x):
        x = self.conv1(x)
        return x


def resnet18(**kwargs):
    net = ResNet(BasicBlock, [2, 2, 2, 2], **kwargs)
    net.conv1 = Conv2d(1, 64, kernel_size=7, stride=2, padding=3, bias=False)
    return net


def mobilenet_v2(**kwargs):
    net = MobileNetV2(**kwargs)
    net.features[0][0] = Conv2d(1, 8, kernel_size=(3, 3), stride=(2, 2), padding=(1, 1), bias=False)
    return net


def shufflenet_v2_x0_5_fistLayer(**kwargs):
    net = ShuffleNetFirstLayer([4, 8, 4], [24, 48, 96, 192, 1024], **kwargs)
    net.conv1[0] = Conv2d(1, 24, 3, 2, 1, bias=False)
    return net
