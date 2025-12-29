# テスト用QRコード（Androidアプリ）

このフォルダは、AndroidアプリのQR読み取り（型式/機番）を手元で検証するためのテストQRコード画像を格納します。

## アプリが期待する形式
アプリ側のパースは `parseQrPayload`（app/src/main/java/com/example/uvccamerademo/QrParser.kt）で行われており、以下のいずれかで **型式（model）** と **機番（serial）** を取得できます。

- Key/Value形式（推奨）
  - `model=MODEL-01\nserial=SN-0001`
  - `type:MODEL-01;sn:SN-0001`（別名キーも可）
  - 使えるキー: `model/type/m` と `serial/sn/s`
- 2トークン形式
  - `MODEL-01,SN-0001`（区切り: `, ; | 改行`）
  - `MODEL-01 SN-0001`（空白区切り）

## 生成済みファイル
- `qr_kv_model_serial_MODEL-01_SN-0001.png`
- `qr_kv_type_sn_MODEL-01_SN-0001.png`
- `qr_tokens_csv_MODEL-01_SN-0001.png`
- `qr_tokens_space_MODEL-01_SN-0001.png`
- `qr_kv_short_m_s_M01_S0001.png`
- `qr_realistic_kv_MODEL-X200_SN-20251229-0007.png`

ペイロード一覧は `payloads.txt` にも出力されています。

## 再生成方法（conda環境: qr）
リポジトリルートで以下を実行します。

- `conda run -n qr python scripts/generate_test_qr_codes.py`

