#!/usr/bin/env python3
"""Generate test QR codes for this app.

The app parses QR payloads in `parseQrPayload` (see app/src/.../QrParser.kt).
Accepted formats include:
- key/value pairs: model=... & serial=... (or type/sn, m/s)
- two tokens separated by comma/semicolon/pipe/newline/whitespace

This script writes PNG files into docs/qr/.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import qrcode
from qrcode.constants import ERROR_CORRECT_M


@dataclass(frozen=True)
class QrCase:
    filename: str
    payload: str


def render_png(payload: str, out_path: Path) -> None:
    qr = qrcode.QRCode(
        version=None,
        error_correction=ERROR_CORRECT_M,
        box_size=10,
        border=4,
    )
    qr.add_data(payload)
    qr.make(fit=True)
    img = qr.make_image(fill_color="black", back_color="white")

    out_path.parent.mkdir(parents=True, exist_ok=True)
    img.save(out_path)


def main() -> None:
    repo_root = Path(__file__).resolve().parents[1]
    out_dir = repo_root / "docs" / "qr"

    cases: list[QrCase] = [
        QrCase(
            filename="qr_kv_model_serial_MODEL-01_SN-0001.png",
            payload="model=MODEL-01\nserial=SN-0001",
        ),
        QrCase(
            filename="qr_kv_type_sn_MODEL-01_SN-0001.png",
            payload="type:MODEL-01;sn:SN-0001",
        ),
        QrCase(
            filename="qr_tokens_csv_MODEL-01_SN-0001.png",
            payload="MODEL-01,SN-0001",
        ),
        QrCase(
            filename="qr_tokens_space_MODEL-01_SN-0001.png",
            payload="MODEL-01 SN-0001",
        ),
        QrCase(
            filename="qr_kv_short_m_s_M01_S0001.png",
            payload="m=M01&s=S0001",
        ),
        QrCase(
            filename="qr_realistic_kv_MODEL-X200_SN-20251229-0007.png",
            payload="model=MODEL-X200\nserial=SN-20251229-0007",
        ),
    ]

    for case in cases:
        render_png(case.payload, out_dir / case.filename)

    # Also write a helper text file listing payloads (useful for debugging)
    manifest = out_dir / "payloads.txt"
    lines = []
    for case in cases:
        lines.append(f"{case.filename}\t{case.payload.replace(chr(10), ' / ')}")
    manifest.write_text("\n".join(lines) + "\n", encoding="utf-8")

    print(f"Wrote {len(cases)} QR codes to: {out_dir}")


if __name__ == "__main__":
    main()
