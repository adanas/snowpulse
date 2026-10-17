# SnowPulse ソースコードレビューレポート

## 1. レビュー対象コード
- データ層: `Entities.kt`, `TrackingDao.kt`, `AppDatabase.kt`, `PreferencesManager.kt`
- ドメイン層: `StateDetectionEngine.kt`, `MotionAnalysisEngine.kt`, `AudioCoachEngine.kt`, `MockScenarioReplayer.kt`
- サービス層: `TrackingForegroundService.kt`
- プレゼンテーション層: `MainActivity.kt`, 各Screen UI

---

## 2. 指摘サマリー

| 区分 | 件数 | 概要 |
| :--- | :---: | :--- |
| **【要修正】(Must Fix)** | **5件** | データの消失リスク、端末依存の計算誤差、画面遷移データの欠落 |
| **【リファクタリング推奨】(Should Refactor)** | **4件** | Kotlinイディオム準拠、Roomリレーション最適化、API互換性 |

---

## 3. 指摘詳細

### 【要修正】(Must Fix)

#### ISSUE-01: ジャイロZ軸の時間積分における固定dtハードコード
- **対象**: `MotionAnalysisEngine.kt` (L88)
- **問題点**: 
  `accumulatedSpinRad += abs(sample.gyroZ) * 0.01f` と100Hz固定（0.01秒）で積分されている。Android端末によって実際のセンサー通知周期は50Hz〜200Hzとばらつきがあるため、固定値だとスピン回転角が2倍〜0.5倍に狂い、180や360の判定が崩れる。
- **修正方針**: 前回のIMUサンプルの `timestampMs` との差分 `dt = (sample.timestampMs - lastTimestampMs) / 1000f` を算出して動的に積分する。

#### ISSUE-02: 計測終了時にバッファ内の未保存GeoPointが破棄される
- **対象**: `TrackingForegroundService.kt` (L231)
- **問題点**: 
  `pendingPoints` は10件ごとにバッチインサートされるが、計測終了時（`stopTrackingSession`）にバッファに残っている最大9件（9秒分）のGPS座標がDBにフラッシュされず消失する。
- **修正方針**: `stopTrackingSession` の冒頭で `pendingPoints` の残余をDBに書き出すフラッシュ処理を追加。

#### ISSUE-03: セッション終了時の非同期DB書き込みとサービス終了の競合
- **対象**: `TrackingForegroundService.kt` (L158)
- **問題点**: 
  `stopTrackingSession()` で `serviceScope.launch { db.updateSession(...) }` を実行した直後に `stopSelf()` および `onDestroy()` で `serviceScope.cancel()` が呼ばれるため、セッション終了データ（総距離・最高速度・終了時刻）の書き込みが途中でキャンセルされる危険がある。
- **修正方針**: `stopTrackingSession` でサマリー更新ジョブの完了を待機（または `Dispatchers.IO` スコープで確実にコミット）してからサービスを終了する。

#### ISSUE-04: MainActivityのRun詳細画面遷移でデータが渡されていない
- **対象**: `MainActivity.kt` (L110)
- **問題点**: 
  `Screen.RUN_DETAIL` 遷移時に `RunDetailScreen(run = null, tricks = emptyList(), ...)` とダミー固定されており、ダッシュボードでタップされたRunのデータが表示されない。
- **修正方針**: `selectedRunId` の状態を保持し、タップされたRunEntityおよび対応するTrickEventEntityリストをDBから読み込んで画面にバインドする。

#### ISSUE-05: ダッシュボード画面のrunsフォールバックにおける型不整合
- **対象**: `MainActivity.kt` (L86)
- **問題点**: 
  `latestSession?.let { ... } ?: db.trackingDao().getAllSessions()` となっており、未計測時に `RunEntity` ではなく `SessionEntity` が渡るため `filterIsInstance<RunEntity>()` というトリッキーな回避策が取られている。
- **修正方針**: セッションが無い場合は空のFlow（`flowOf(emptyList<RunEntity>())`）を返し、型安全なコードにする。

---

### 【リファクタリング推奨】(Should Refactor)

#### REC-01: `java.lang.Math` から `kotlin.math` への統一 - **[修正済]**
- `StateDetectionEngine.kt` および `MotionAnalysisEngine.kt` で混在していた `Math.abs` や `Math.toDegrees` を、Kotlin標準の `kotlin.math.abs` および拡張関数 `Double.toDegrees()`（`kotlin.math.PI` 利用）に統一し、Kotlinイディオムに準拠させました。

#### REC-02: Room リレーションエンティティ（`@Relation`）の導入
- 将来的な拡張として、`SessionWithRuns`（Session + List<Run>）や `RunWithTricks` などのエンティティクラスを定義することで、DAOのクエリ呼び出しを一元化。

#### REC-03: Composeの `LinearProgressIndicator` のAPI互換性
- `TrackingScreen.kt` でラムダ式進捗が渡されている箇所を、より広範なComposeバージョンで安定動作するFloat値プロパティに修正。

#### REC-04: オフライン時のスキーマバージョン管理
- Room DBの `fallbackToDestructiveMigration()` はプロトタイプでは有効だが、本番ゲレンデログ保護のためマイグレーション設計を今後検討。
