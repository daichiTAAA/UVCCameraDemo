# Jetpack Compose で AndroidUVCCamera（AUSBC / libausbc）を使う手順（このプロジェクト向け）

このプロジェクト（`UVCCameraDemo`）は Jetpack Compose をUIに使い、UVC対応のUSBカメラを表示/撮影するために **AUSBC（AndroidUSBCamera / libausbc）** を利用します。

> 参照: `docs/AndroidUVCCameraOriginalREADME.md`（元README / API一覧）

---

## 0. できること / 前提

- OTG対応端末にUSB（UVC）カメラを接続して、**システムのカメラ権限なし**でプレビューできます
- ただし **USBデバイス利用許可（UsbManagerのpermission）** は必要です（ダイアログが出ます）

前提条件:
- 端末が **USB Host(OTG)** に対応していること
- 接続するカメラが **UVC** に対応していること
- 給電不足で落ちることがあるので、必要に応じてセルフパワーUSBハブを使用

---

## 1. 依存関係（このプロジェクトは導入済み）

このプロジェクトでは、すでに以下が設定済みです。

- `settings.gradle.kts` に JitPack リポジトリ追加済み
- `app/build.gradle.kts` に `libausbc` 依存追加済み

該当箇所:

- `settings.gradle.kts`
  - `maven("https://jitpack.io")`
- `gradle/libs.versions.toml`
  - `libausbc = "3.3.3"`
- `app/build.gradle.kts`
  - `implementation(libs.libausbc)`

バージョンを上げたい場合は、`gradle/libs.versions.toml` の `libausbc` を更新してください。

---

## 2. Manifest（最低限の考え方）

AUSBC自体は「通常のカメラ権限」なしで動きますが、USBホスト機能を前提にする場合は、端末要件として以下を明示しておくのが安全です。

推奨（必要に応じて）:

- USB Host を使う旨の宣言
  - `android.hardware.usb.host`

注意:
- USB attach を自動検知して起動したい場合は、`intent-filter` / `device_filter.xml` / `BroadcastReceiver` が必要になります。
- 本ドキュメントではまず「アプリ起動 → 接続済みデバイスを開く」ルートを基本にします（最小構成）。

---

## 3. Composeでの基本方針（重要）

AUSBCの元READMEは `CameraFragment` / `CameraActivity` を継承する使い方が中心ですが、Composeでは次のどちらかの方針が現実的です。

### 方針A（最短で動かす）: Fragment（CameraFragment）を使い、Composeはホスト役
- 既存のAUSBCの設計に沿うので動かしやすい
- ただし Composeだけで閉じず、Fragment/Viewのライフサイクルを併用する

### 方針B（Compose寄り）: Controller層 + AndroidViewでTextureView/SurfaceViewをホスト
- UIはComposeに寄せられる
- 代わりにUSB許可やopen/closeの制御を自前で整理する必要がある

このプロジェクトは Compose のみの雛形なので、ここでは **方針B（Compose寄り）** を中心に「最低限動く」考え方を説明します。

---

## 4. USB許可（requestPermission）の流れ

UVCカメラを開くまでの流れは概ねこの順です。

1. `UsbManager.deviceList` から接続済みUSBデバイス一覧を取得
2. UVCっぽいデバイス（もしくはユーザーが選んだデバイス）を決める
3. `UsbManager.hasPermission(device)` を確認
4. permissionがなければ `UsbManager.requestPermission(device, pendingIntent)`
5. `BroadcastReceiver` で permission結果を受け取り
6. 許可されていたら AUSBC 側のクライアントで open → preview開始

Composeでは、`BroadcastReceiver` の登録/解除を `DisposableEffect` で管理するのが定石です。

---

## 5. Composeでプレビューを表示する（AndroidView）

AUSBCは内部的に `TextureView/SurfaceView`（またはライブラリ提供の `IAspectRatio` 実装View）へ描画します。
Composeでは `AndroidView` を使って、その **Viewインスタンスを安定して保持** し、open時にそれを渡します。

ポイント:
- `AndroidView` の `factory` で作った `TextureView` を、`remember { ... }` に近い扱いで保持する
- Composableの再コンポーズで View が作り直されるとプレビューが途切れやすいので注意

### 最小UIイメージ（概念）

- `UvcPreview()`
  - `AndroidView { TextureView(context) }`
- `UvcScreen()`
  - 接続デバイスの列挙
  - 「Open」「Close」「Capture」ボタン
  - `UvcPreview()` を表示

※ 実コードはプロジェクトの設計（State管理、DI、Navigation）によって置き場所が変わるため、まずは `MainActivity` 1ファイルにまとめて動作確認 → 後で分割、がやりやすいです。

---

## 6. カメラ設定（CameraRequest）

AUSBCでは `CameraRequest` でプレビュー解像度・レンダリング方式などを指定できます。

代表例（元READMEより）:

- `setPreviewWidth(1280)` / `setPreviewHeight(720)`
- `setRenderMode(CameraRequest.RenderMode.OPENGL)`
- `setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_MJPEG)`（MJPEGが安定しやすいことが多い）
- `setAspectRatioShow(true)`

解像度やフォーマットが合わないと「黒画面」「数秒で停止」になりやすいので、
最初は **低めの解像度 + MJPEG** から試すのがおすすめです。

---

## 7. ライフサイクル（open/closeの目安）

Composeでは画面遷移やバックグラウンド遷移で View が破棄されずに残ることがあります。
USBデバイスはハンドルリークしやすいので、以下を基本にしてください。

- 画面が見えている間だけ open
- `onStop` 相当で close
- `DisposableEffect` で receiver解除 / クライアント解放

典型:
- `LifecycleEventObserver` を使い、
  - `ON_START`: 必要ならpermission確認・open
  - `ON_STOP`: close

---

## 8. 撮影/録画（AUSBC API）

AUSBC（CameraFragment/CameraActivity系）で提供される代表的なAPIは以下です（元READMEより）。

- 静止画: `captureImage(callBack, savePath)`
- 録画: `captureVideoStart(callBack, path, durationInSec)` / `captureVideoStop()`
- 音声: `captureAudioStart(callBack, path)` / `captureAudioStop()`
- 配信/エンコード取得: `addEncodeDataCallBack(callBack)`
- 解像度変更: `updateResolution(width, height)`
- デバイス取得: `getDeviceList()`

保存先について:
- Android 10+ はスコープストレージの影響があるため、
  - まずは `context.getExternalFilesDir(null)` 配下（アプリ専用領域）に保存
  - 共有ギャラリーへ出したい場合は `MediaStore` を使う

---

## 9. トラブルシュート

### 黒画面になる
- USB permission が許可されていない
- `TextureView/Surface` が生成される前に open している
- 解像度/フォーマットがカメラ非対応（まず 640x480 + MJPEG/YUYV を試す）
- OTG給電不足（ハブ/ケーブルを見直す）

### 接続が不安定
- セルフパワーUSBハブを使う
- 接続ケーブルを短くする
- カメラ側が高解像度で電力/帯域を使いすぎている → 解像度を下げる

### 録画ファイルが出てこない
- 保存パスが書き込み不可
- duration指定や stop 呼び出しの不整合
- まずはアプリ専用ディレクトリに保存して確認

---

## 10. 次にやること（このプロジェクト向けおすすめ）

- `MainActivity` に "UVCプレビュー画面" を作る（AndroidView + USB permission + open/close）
- 動作したら `UvcCameraController` と `UvcScreen` に分割
- 必要に応じて MultiCamera（複数カメラ）へ拡張

---

## 参考

- `docs/AndroidUVCCameraOriginalREADME.md`
  - CameraRequest、capture、MultiCameraの説明・API一覧

