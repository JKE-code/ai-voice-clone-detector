from __future__ import annotations

import argparse
import json
from pathlib import Path

from voice_detector.forensics_detector import DEFAULT_FORENSICS_MODEL_ID, FORENSICS_BACKBONE_ID
from voice_detector.hf_detector import DEFAULT_CACHE_DIR, download_model


def main() -> int:
    parser = argparse.ArgumentParser(description="Download the Hugging Face AI voice detector model.")
    parser.add_argument("--model-id", default=DEFAULT_FORENSICS_MODEL_ID, help="Hugging Face model id to download.")
    parser.add_argument("--cache-dir", type=Path, default=DEFAULT_CACHE_DIR, help="Local model cache directory.")
    parser.add_argument(
        "--skip-backbone",
        action="store_true",
        help="For the forensics model, skip downloading the required WavLM-large backbone.",
    )
    args = parser.parse_args()

    cache_dir = download_model(args.model_id, args.cache_dir)
    payload = {"model_id": args.model_id, "cache_dir": str(cache_dir), "status": "ready"}
    if args.model_id == DEFAULT_FORENSICS_MODEL_ID and not args.skip_backbone:
        backbone_dir = download_model(FORENSICS_BACKBONE_ID, args.cache_dir)
        payload["backbone_model_id"] = FORENSICS_BACKBONE_ID
        payload["backbone_cache_dir"] = str(backbone_dir)

    print(json.dumps(payload, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
