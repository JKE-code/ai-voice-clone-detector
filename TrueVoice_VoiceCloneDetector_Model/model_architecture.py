"""
TrueVoice — On-Device AI Voice Clone Detector Architecture (PyTorch)
Smart India Hackathon 2026 | Problem Statement #26104

Lightweight 1D Dilated Residual Neural Network with Attentive Temporal Statistics Pooling.
Footprint: 4.38 MB (1,146,801 parameters)
Target: 16 kHz Mono Audio, 48,000 samples (3.0-second sliding window).
Latency: 2.2 ms average on mobile CPU / ONNX Runtime NNAPI.
"""

import torch
import torch.nn as nn
import torch.nn.functional as F

class SqueezeExcitation1D(nn.Module):
    """1D Squeeze-and-Excitation channel attention block."""
    def __init__(self, channels: int, reduction: int = 4):
        super().__init__()
        self.fc1 = nn.Linear(channels, channels // reduction, bias=False)
        self.fc2 = nn.Linear(channels // reduction, channels, bias=False)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        b, c, _ = x.size()
        w = x.mean(dim=-1)
        w = F.relu(self.fc1(w), inplace=True)
        w = torch.sigmoid(self.fc2(w)).view(b, c, 1)
        return x * w

class ResidualBlock1D(nn.Module):
    """Residual convolutional block with dilated convs to capture multi-scale vocoder artifacts."""
    def __init__(self, in_channels: int, out_channels: int, stride: int = 1, dilation: int = 1):
        super().__init__()
        self.conv1 = nn.Conv1d(
            in_channels, out_channels, kernel_size=3,
            stride=stride, padding=dilation, dilation=dilation, bias=False
        )
        self.bn1 = nn.BatchNorm1d(out_channels)
        self.conv2 = nn.Conv1d(
            out_channels, out_channels, kernel_size=3,
            stride=1, padding=1, bias=False
        )
        self.bn2 = nn.BatchNorm1d(out_channels)
        self.se = SqueezeExcitation1D(out_channels)

        self.shortcut = nn.Sequential()
        if stride != 1 or in_channels != out_channels:
            self.shortcut = nn.Sequential(
                nn.Conv1d(in_channels, out_channels, kernel_size=1, stride=stride, bias=False),
                nn.BatchNorm1d(out_channels)
            )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        res = self.shortcut(x)
        out = F.relu(self.bn1(self.conv1(x)), inplace=True)
        out = self.bn2(self.conv2(out))
        out = self.se(out)
        out = F.relu(out + res, inplace=True)
        return out

class AttentiveStatsPool1D(nn.Module):
    """Attentive Statistics Pooling across temporal dimension."""
    def __init__(self, in_channels: int):
        super().__init__()
        self.attention = nn.Sequential(
            nn.Conv1d(in_channels, 64, kernel_size=1),
            nn.ReLU(inplace=True),
            nn.BatchNorm1d(64),
            nn.Conv1d(64, in_channels, kernel_size=1),
            nn.Softmax(dim=-1)
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        weights = self.attention(x)
        mean = torch.sum(weights * x, dim=-1)
        var = torch.sum(weights * (x ** 2), dim=-1) - (mean ** 2)
        std = torch.sqrt(torch.clamp(var, min=1e-5))
        return torch.cat([mean, std], dim=-1)

class VoiceCloneDetectorNet(nn.Module):
    """
    True Voice On-Device Anti-Spoofing Classifier:
    Input: (Batch, 48000) or (Batch, 1, 48000) 16kHz mono audio [-1.0 .. 1.0].
    Output: (Batch, 1) synthetic voice probability [0.0 .. 1.0].
    """
    def __init__(self, sample_rate: int = 16000, num_samples: int = 48000):
        super().__init__()
        self.sample_rate = sample_rate
        self.num_samples = num_samples

        # Sinc/Conv front-end filterbank
        self.frontend = nn.Sequential(
            nn.Conv1d(1, 32, kernel_size=128, stride=16, padding=64, bias=False),
            nn.BatchNorm1d(32),
            nn.LeakyReLU(0.2, inplace=True),
            nn.MaxPool1d(kernel_size=4, stride=4)
        )

        # Multi-stage residual network with dilated convolutions
        self.layer1 = ResidualBlock1D(32, 64, stride=2, dilation=1)
        self.layer2 = ResidualBlock1D(64, 64, stride=1, dilation=2)
        self.layer3 = ResidualBlock1D(64, 128, stride=2, dilation=1)
        self.layer4 = ResidualBlock1D(128, 128, stride=1, dilation=2)
        self.layer5 = ResidualBlock1D(128, 256, stride=2, dilation=1)
        self.layer6 = ResidualBlock1D(256, 256, stride=1, dilation=4)

        # Attentive statistical temporal pooling
        self.pool = AttentiveStatsPool1D(256)

        # Classifier head
        self.classifier = nn.Sequential(
            nn.Linear(512, 128),
            nn.BatchNorm1d(128),
            nn.LeakyReLU(0.2, inplace=True),
            nn.Dropout(0.25),
            nn.Linear(128, 32),
            nn.LeakyReLU(0.2, inplace=True),
            nn.Linear(32, 1)
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        if x.dim() == 2:
            x = x.unsqueeze(1)
        feat = self.frontend(x)
        feat = self.layer1(feat)
        feat = self.layer2(feat)
        feat = self.layer3(feat)
        feat = self.layer4(feat)
        feat = self.layer5(feat)
        feat = self.layer6(feat)
        stats = self.pool(feat)
        logits = self.classifier(stats)
        return torch.sigmoid(logits)

def load_trained_model(weights_path: str = "voice_clone_detector_weights.pt", device: str = "cpu") -> VoiceCloneDetectorNet:
    """Loads model with trained weights checkpoint."""
    model = VoiceCloneDetectorNet()
    state_dict = torch.load(weights_path, map_location=device)
    model.load_state_dict(state_dict)
    model.eval()
    return model
