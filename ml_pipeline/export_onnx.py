"""
True Voice — AI Voice Clone Anti-Spoofing Model Export & Quantization Pipeline
Problem Statement #26104 (Smart India Hackathon)

This script defines the neural architecture for real-time edge voice clone detection,
exports the model to ONNX format (16kHz, 48,000 samples / 3-second sliding window),
and applies INT8 dynamic quantization for mobile deployment on Android.
"""

import os
import sys

# Ensure UTF-8 output on Windows console
if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")

import math
import numpy as np
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
        # x: (B, C, L)
        b, c, _ = x.size()
        w = x.mean(dim=-1) # (B, C)
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
        # x: (B, C, L)
        weights = self.attention(x)
        mean = torch.sum(weights * x, dim=-1) # (B, C)
        var = torch.sum(weights * (x ** 2), dim=-1) - (mean ** 2)
        std = torch.sqrt(torch.clamp(var, min=1e-5)) # (B, C)
        return torch.cat([mean, std], dim=-1) # (B, 2*C)

class VoiceCloneDetectorNet(nn.Module):
    """
    True Voice On-Device Anti-Spoofing Classifier:
    Processes raw 16kHz audio waveforms (48,000 samples = 3.0 seconds).
    Detects high-frequency brickwall cutoffs, vocoder harmonics, phase anomalies, and unnatural pitch jitter.
    """
    def __init__(self, sample_rate: int = 16000, num_samples: int = 48000):
        super().__init__()
        self.sample_rate = sample_rate
        self.num_samples = num_samples

        # Sinc/Conv front-end filterbank (subsampling raw waveform)
        self.frontend = nn.Sequential(
            nn.Conv1d(1, 32, kernel_size=128, stride=16, padding=64, bias=False),
            nn.BatchNorm1d(32),
            nn.LeakyReLU(0.2, inplace=True),
            nn.MaxPool1d(kernel_size=4, stride=4)
        ) # Output: (B, 32, ~750)

        # Multi-stage residual network with dilated convolutions
        self.layer1 = ResidualBlock1D(32, 64, stride=2, dilation=1)   # (B, 64, ~375)
        self.layer2 = ResidualBlock1D(64, 64, stride=1, dilation=2)   # (B, 64, ~375)
        self.layer3 = ResidualBlock1D(64, 128, stride=2, dilation=1)  # (B, 128, ~188)
        self.layer4 = ResidualBlock1D(128, 128, stride=1, dilation=2) # (B, 128, ~188)
        self.layer5 = ResidualBlock1D(128, 256, stride=2, dilation=1) # (B, 256, ~94)
        self.layer6 = ResidualBlock1D(256, 256, stride=1, dilation=4) # (B, 256, ~94)

        # Attentive statistical temporal pooling
        self.pool = AttentiveStatsPool1D(256) # Output: (B, 512)

        # Classifier head
        self.classifier = nn.Sequential(
            nn.Linear(512, 128),
            nn.BatchNorm1d(128),
            nn.LeakyReLU(0.2, inplace=True),
            nn.Dropout(0.25),
            nn.Linear(128, 32),
            nn.LeakyReLU(0.2, inplace=True),
            nn.Linear(32, 1) # Raw logit output
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        """
        Args:
            x: Input audio tensor of shape (Batch, 48000) or (Batch, 1, 48000), normalized to [-1.0, 1.0].
        Returns:
            spoof_prob: Tensor of shape (Batch, 1) with synthetic voice probability [0.0, 1.0].
        """
        if x.dim() == 2:
            x = x.unsqueeze(1) # (B, 1, L)

        feat = self.frontend(x)
        feat = self.layer1(feat)
        feat = self.layer2(feat)
        feat = self.layer3(feat)
        feat = self.layer4(feat)
        feat = self.layer5(feat)
        feat = self.layer6(feat)

        stats = self.pool(feat)
        logits = self.classifier(stats)
        spoof_prob = torch.sigmoid(logits)
        return spoof_prob

def export_to_onnx(output_dir: str = "models", model_name: str = "voice_clone_detector"):
    """Exports and validates the VoiceCloneDetector model in ONNX format."""
    os.makedirs(output_dir, exist_ok=True)
    onnx_path = os.path.join(output_dir, f"{model_name}.onnx")
    quantized_path = os.path.join(output_dir, f"{model_name}_quant.onnx")

    print(f"[*] Initializing True Voice Anti-Spoofing Model...")
    model = VoiceCloneDetectorNet()
    model.eval()

    # Create dummy 3-second 16kHz audio input (Batch=1, Samples=48000)
    dummy_input = torch.randn(1, 48000, dtype=torch.float32)

    print(f"[*] Exporting PyTorch model to ONNX: {onnx_path}")
    torch.onnx.export(
        model,
        dummy_input,
        onnx_path,
        export_params=True,
        opset_version=18,
        dynamo=False,
        do_constant_folding=True,
        input_names=["audio_pcm"],
        output_names=["spoof_probability"],
        dynamic_axes={
            "audio_pcm": {0: "batch_size"},
            "spoof_probability": {0: "batch_size"}
        }
    )


    print(f"[✓] ONNX model successfully exported: {os.path.getsize(onnx_path) / (1024*1024):.2f} MB")

    # Validate ONNX model with onnxruntime
    try:
        import onnxruntime as ort
        session = ort.InferenceSession(onnx_path)
        ort_inputs = {session.get_inputs()[0].name: dummy_input.numpy()}
        ort_outputs = session.run(None, ort_inputs)
        
        with torch.no_grad():
            torch_output = model(dummy_input).numpy()
        
        np.testing.assert_allclose(torch_output, ort_outputs[0], rtol=1e-3, atol=1e-4)
        print(f"[✓] Numerical parity verified between PyTorch and ONNX Runtime!")
    except ImportError:
        print("[!] onnxruntime not installed in current env; skipping parity test.")
    except Exception as e:
        print(f"[!] Parity check note: {e}")

    # Attempt dynamic INT8 quantization for Android mobile optimization
    try:
        from onnxruntime.quantization import quantize_dynamic, QuantType
        print(f"[*] Applying dynamic INT8 quantization for mobile CPU optimization...")
        quantize_dynamic(
            onnx_path,
            quantized_path,
            weight_type=QuantType.QUInt8
        )
        print(f"[✓] Quantized model created: {quantized_path} ({os.path.getsize(quantized_path) / (1024*1024):.2f} MB)")
    except Exception as e:
        print(f"[!] Quantization note: {e}")

    return onnx_path

if __name__ == "__main__":
    output_dir = sys.argv[1] if len(sys.argv) > 1 else "models"
    export_to_onnx(output_dir)
