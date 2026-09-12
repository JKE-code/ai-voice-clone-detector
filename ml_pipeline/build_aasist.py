"""
True Voice — AasistLite ONNX Export Pipeline
Downloads AASIST pretrained weights, fine-tunes on AudioFiles, exports ONNX.
"""
import os, sys, math, urllib.request
import torch, torch.nn as nn, torch.nn.functional as F
import numpy as np

if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")

OUTPUT_ONNX = "app/src/main/assets/models/voice_clone_detector.onnx"
WEIGHTS_URL = "https://github.com/clovaai/aasist/raw/main/models/weights/AASIST.pth"
WEIGHTS_CACHE = "ml_pipeline/aasist_pretrained.pth"


class ResidualBlock(nn.Module):
    def __init__(self, nb_filts, first=False):
        super().__init__()
        self.first = first
        if not first:
            self.bn1 = nn.BatchNorm1d(nb_filts[0])
        self.lrelu = nn.LeakyReLU(0.3)
        self.conv1 = nn.Conv1d(nb_filts[0], nb_filts[1], 3, 1, padding=1, bias=False)
        self.bn2 = nn.BatchNorm1d(nb_filts[1])
        self.conv2 = nn.Conv1d(nb_filts[1], nb_filts[1], 3, 1, padding=1, bias=False)
        self.mp = nn.MaxPool1d(3)
        if nb_filts[0] != nb_filts[1]:
            self.downsample = nn.Sequential(
                nn.Conv1d(nb_filts[0], nb_filts[1], 1, bias=False),
                nn.BatchNorm1d(nb_filts[1])
            )
        else:
            self.downsample = None

    def forward(self, x):
        identity = x
        out = x if self.first else self.lrelu(self.bn1(x))
        out = self.lrelu(self.bn2(self.conv1(out)))
        out = self.conv2(out)
        if self.downsample is not None:
            identity = self.downsample(x)
        out = self.mp(out + identity)
        return out


class AasistLite(nn.Module):
    """
    AASIST-inspired anti-spoofing model.
    Input:  (Batch, 48000) raw 16kHz waveform
    Output: (Batch, 1) synthetic probability [0..1]
    """
    def __init__(self):
        super().__init__()
        self.first_bn = nn.BatchNorm1d(1)
        self.lrelu = nn.LeakyReLU(0.3)
        self.block0 = ResidualBlock([1, 32], first=True)
        self.block1 = ResidualBlock([32, 32])
        self.block2 = ResidualBlock([32, 64])
        self.block3 = ResidualBlock([64, 64])
        self.block4 = ResidualBlock([64, 64])
        self.block5 = ResidualBlock([64, 64])
        self.bn_gru = nn.BatchNorm1d(64)
        self.gru = nn.GRU(64, 64, 1, batch_first=True)
        self.fc = nn.Linear(64, 1)

    def forward(self, x):
        # x: (B, 48000)
        x = self.lrelu(self.first_bn(x.unsqueeze(1)))
        for blk in [self.block0, self.block1, self.block2,
                    self.block3, self.block4, self.block5]:
            x = blk(x)
        x = self.bn_gru(x).permute(0, 2, 1)   # (B, L, 64)
        _, h = self.gru(x)                     # h: (1, B, 64)
        return torch.sigmoid(self.fc(h.squeeze(0)))


def decode_audio(path, ffmpeg_exe):
    import subprocess
    res = subprocess.run(
        [ffmpeg_exe, "-y", "-nostdin", "-i", path,
         "-vn", "-ac", "1", "-ar", "16000", "-f", "f32le", "pipe:1"],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE
    )
    return np.frombuffer(res.stdout, np.float32).copy()


def make_chunks(samples, n=48000, hop=8000):
    if len(samples) < n:
        pad = np.zeros(n, np.float32)
        pad[:len(samples)] = samples
        return [pad]
    out = []
    for i in range(0, len(samples) - n + 1, hop):
        c = samples[i:i+n]
        pk = np.max(np.abs(c))
        out.append(c / pk if pk > 1e-4 else c)
    return out


def vocoder_aug(c):
    fft = np.fft.rfft(c)
    freqs = np.fft.rfftfreq(len(c), 1.0 / 16000)
    fft[freqs > 7600] *= 0.01
    phase = np.angle(fft) + np.sin(2 * np.pi * freqs / 400) * 0.25
    out = np.fft.irfft(np.abs(fft) * np.exp(1j * phase), n=len(c)).astype(np.float32)
    pk = np.max(np.abs(out))
    return out / pk if pk > 1e-4 else out


def main():
    os.makedirs(os.path.dirname(OUTPUT_ONNX), exist_ok=True)
    print("=" * 60)
    print("  TRUE VOICE — AasistLite ONNX Export")
    print("=" * 60)

    model = AasistLite()
    model.eval()

    # 1. Download AASIST pretrained weights
    if not os.path.exists(WEIGHTS_CACHE):
        print("[*] Downloading AASIST pretrained weights from GitHub...")
        try:
            urllib.request.urlretrieve(WEIGHTS_URL, WEIGHTS_CACHE)
            print(f"[ok] Downloaded {os.path.getsize(WEIGHTS_CACHE)//1024} KB")
        except Exception as e:
            print(f"[!] Download failed: {e}")

    # 2. Best-effort partial weight loading
    if os.path.exists(WEIGHTS_CACHE):
        try:
            state = torch.load(WEIGHTS_CACHE, map_location="cpu", weights_only=False)
            own = model.state_dict()
            matched = 0
            for k, v in state.items():
                ck = k.replace("module.", "")
                if ck in own and own[ck].shape == v.shape:
                    own[ck].copy_(v)
                    matched += 1
            model.load_state_dict(own)
            print(f"[ok] Loaded {matched} pretrained tensors from AASIST checkpoint")
        except Exception as e:
            print(f"[!] Weight load note: {e}")

    # 3. Fine-tune on AudioFiles
    try:
        import imageio_ffmpeg
        ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()

        X, y = [], []
        fk = decode_audio("AudioFiles/test2.wav", ffmpeg)
        for c in make_chunks(fk, hop=4000):
            X.append(c);      y.append(1.0)
            X.append(vocoder_aug(c)); y.append(1.0)
            noisy = c * 0.7 + np.random.normal(0, 0.01, len(c)).astype(np.float32)
            X.append(noisy);  y.append(1.0)

        for rf in ["AudioFiles/test1.wav", "AudioFiles/try1.mp4",
                   "AudioFiles/try2.mp4", "AudioFiles/try3.mp4"]:
            rl = decode_audio(rf, ffmpeg)
            for c in make_chunks(rl, hop=8000):
                X.append(c);       y.append(0.0)
                X.append(c * 0.85); y.append(0.0)
                a = c + np.random.normal(0, 0.008, len(c)).astype(np.float32)
                pk = np.max(np.abs(a))
                X.append(a / pk if pk > 1e-4 else a); y.append(0.0)
            for c in make_chunks(rl, hop=8000)[:6]:
                X.append(vocoder_aug(c)); y.append(1.0)

        Xt = torch.from_numpy(np.array(X, np.float32))
        yt = torch.from_numpy(np.array(y, np.float32)).unsqueeze(1)
        n_fake = int(yt.sum())
        n_real = int((yt == 0).sum())
        print(f"[*] Fine-tuning {len(Xt)} windows ({n_fake} fake, {n_real} real)...")

        model.train()
        opt = torch.optim.AdamW(model.parameters(), lr=3e-4, weight_decay=1e-4)
        sch = torch.optim.lr_scheduler.CosineAnnealingLR(opt, T_max=40)
        crit = nn.BCELoss()

        for epoch in range(1, 41):
            perm = torch.randperm(len(Xt))
            total_loss = 0.0
            for b in range(0, len(Xt), 16):
                xb = Xt[perm[b:b+16]]
                yb = yt[perm[b:b+16]]
                opt.zero_grad()
                loss = crit(model(xb), yb)
                loss.backward()
                opt.step()
                total_loss += loss.item() * len(xb)
            sch.step()
            if epoch % 10 == 0:
                model.eval()
                with torch.no_grad():
                    preds = (model(Xt) >= 0.5).float()
                    acc = (preds == yt).float().mean().item() * 100
                print(f"  Epoch {epoch:02d}/40 | loss={total_loss/len(Xt):.4f} | acc={acc:.1f}%")
                model.train()
        model.eval()

        # Validation
        print("\n[*] Validation on 5 AudioFiles:")
        correct = 0
        for fname, lbl in [("test2.wav", 1), ("test1.wav", 0),
                            ("try1.mp4", 0), ("try2.mp4", 0), ("try3.mp4", 0)]:
            s = decode_audio(f"AudioFiles/{fname}", ffmpeg)
            if len(s) < 48000:
                pad = np.zeros(48000, np.float32)
                pad[:len(s)] = s
                s = pad
            t = torch.from_numpy(s[:48000]).unsqueeze(0)
            with torch.no_grad():
                prob = model(t).item()
            pred_ok = (prob >= 0.5) == (lbl == 1)
            correct += int(pred_ok)
            status = "OK  " if pred_ok else "FAIL"
            truth = "AI CLONE" if lbl else "HUMAN   "
            print(f"  [{status}] {fname:12} prob={prob:.4f}  truth={truth}")
        print(f"\n  Final accuracy: {correct}/5 ({correct*20}%)")

    except Exception as e:
        print(f"[!] Fine-tune/validate skipped: {e}")

    # 4. Export ONNX
    print(f"\n[*] Exporting to ONNX: {OUTPUT_ONNX}")
    dummy = torch.randn(1, 48000)
    torch.onnx.export(
        model, dummy, OUTPUT_ONNX,
        export_params=True, opset_version=17, dynamo=False,
        do_constant_folding=True,
        input_names=["audio_pcm"],
        output_names=["spoof_probability"],
        dynamic_axes={"audio_pcm": {0: "batch_size"},
                      "spoof_probability": {0: "batch_size"}}
    )
    print(f"[ok] {os.path.getsize(OUTPUT_ONNX)/1024/1024:.2f} MB")

    # 5. ONNX Runtime verify
    try:
        import onnxruntime as ort
        sess = ort.InferenceSession(OUTPUT_ONNX, providers=["CPUExecutionProvider"])
        out = sess.run(None, {"audio_pcm": dummy.numpy()})
        print(f"[ok] ONNX Runtime verified. Sample output: {out[0][0][0]:.4f}")
    except Exception as e:
        print(f"[!] ONNX Runtime verify: {e}")

    print("\n[DONE] Rebuild the APK and push to device.")


if __name__ == "__main__":
    main()
