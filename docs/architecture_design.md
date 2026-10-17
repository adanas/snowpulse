# スノーボードアプリ「SnowPulse」基本設計・アーキテクチャ設計書

## 1. 全体システムアーキテクチャ

Android推奨の**Clean Architecture + MVVM (Model-View-ViewModel)**パターンを採用し、センサーやGPSなどのハードウェア依存部分とドメインロジック（滑走・リフト判定、モーション解析）を疎結合に分離します。

```mermaid
flowchart TD
    subgraph UI ["Presentation Layer (Jetpack Compose)"]
        TrackScreen["滑走中画面 (TrackingScreen)"]
        SummaryScreen["ラン分析・サマリー (RunDetailScreen)"]
        Dashboard["全体ダッシュボード (DashboardScreen)"]
        TrackVM["TrackingViewModel"]
        SummaryVM["RunDetailViewModel"]
    end

    subgraph Domain ["Domain Layer (Business Logic)"]
        TrackUseCase["TrackingUseCase (計測制御)"]
        StateDetectionUseCase["StateDetectionUseCase (滑走/リフト自動判定)"]
        MotionAnalysisUseCase["MotionAnalysisUseCase (トリック/エッジ角解析)"]
        AudioCoachUseCase["AudioCoachUseCase (リアルタイム音声ガイド)"]
    end

    subgraph Data ["Data Layer (Repositories & Sources)"]
        TrackingRepo["TrackingRepository"]
        SensorRepo["SensorRepository"]
        AudioRepo["AudioRepository (TTS)"]
        
        RoomDB[("Room Database (SQLite)")]
        LocationSource["FusedLocationProviderClient"]
        IMUSource["SensorManager (Acc/Gyro)"]
        TTS["TextToSpeech Engine"]
    end

    subgraph Service ["Background Service"]
        ForegroundService["TrackingForegroundService"]
    end

    TrackScreen --> TrackVM
    SummaryScreen --> SummaryVM
    Dashboard --> TrackVM
    
    TrackVM --> TrackUseCase
    SummaryVM --> TrackingRepo
    
    ForegroundService --> TrackUseCase
    ForegroundService --> LocationSource
    ForegroundService --> IMUSource
    
    TrackUseCase --> StateDetectionUseCase
    TrackUseCase --> MotionAnalysisUseCase
    TrackUseCase --> AudioCoachUseCase
    
    StateDetectionUseCase --> TrackingRepo
    MotionAnalysisUseCase --> TrackingRepo
    AudioCoachUseCase --> AudioRepo
    
    TrackingRepo --> RoomDB
    SensorRepo --> IMUSource
    AudioRepo --> TTS
```

---

## 2. データモデル設計 (Database Schema)

オフライン環境でもすべての滑走ログを永続化できるよう、**Room (SQLite)** を利用した階層型データ構造とします。

```mermaid
erDiagram
    SESSION ||--o{ RUN : contains
    SESSION ||--o{ LIFT_RIDE : contains
    RUN ||--o{ GEO_POINT : contains
    RUN ||--o{ TRICK_EVENT : contains

    SESSION {
        string id PK "UUID"
        int date "日付 (UNIXタイムスタンプ)"
        string resort_name "スキー場名 (逆ジオコーディング)"
        float total_distance "総滑走距離 (m)"
        float max_speed "最高速度 (km/h)"
        float total_elevation_drop "総獲得標高 (m)"
        int total_runs "滑走本数"
        int total_duration "合計時間 (秒)"
    }

    RUN {
        string id PK "UUID"
        string session_id FK "セッションID"
        int run_number "第○本目"
        int start_time "滑走開始時間"
        int end_time "滑走終了時間"
        float distance "滑走距離 (m)"
        float max_speed "最高速度 (km/h)"
        float avg_speed "平均速度 (km/h)"
        float elevation_drop "標高差 (m)"
        float max_edge_angle "最大エッジ角 (deg)"
        float avg_vibration_index "路面粗さ/雪質指標"
    }

    LIFT_RIDE {
        string id PK "UUID"
        string session_id FK "セッションID"
        int start_time "乗車開始時間"
        int end_time "降車時間"
        float elevation_gain "獲得高度 (m)"
    }

    GEO_POINT {
        int id PK "Auto Increment"
        string run_id FK "ランID"
        double latitude "緯度"
        double longitude "経度"
        float altitude "高度 (気圧/GPS補正後)"
        float speed "瞬時速度 (km/h)"
        float heading "進行方向方位角"
        float edge_angle "エッジ角 (推定)"
        int timestamp "記録日時"
    }

    TRICK_EVENT {
        string id PK "UUID"
        string run_id FK "ランID"
        string trick_type "トリック種別 (Ollie / 180 / 360 / Jump)"
        float air_time "滞空時間 (秒)"
        float jump_height "推定ジャンプ高 (cm)"
        float rotation_degrees "回転角 (deg)"
        boolean is_landed "着地成功判定"
        int timestamp "発生時間"
    }
```

---

## 3. コアアルゴリズム設計

### 3.1 滑走 / リフト搭乗 自動判定ステートマシン
ゲレンデ上でユーザーが操作しなくても、ステートマシンにより「滑走」「リフト」「待機」を自動遷移します。

```mermaid
stateDiagram-v2
    [*] --> IDLE : 計測開始
    
    IDLE --> SKIING : 速度 > 12km/h かつ 高度降下率 > 0.8m/s
    SKIING --> PAUSED_RUN : 速度 < 3km/h (30秒継続)
    PAUSED_RUN --> SKIING : 速度 > 8km/h
    PAUSED_RUN --> RIDING_LIFT : 高度上昇率 > 0.5m/s (45秒継続)
    
    SKIING --> RIDING_LIFT : 高度上昇率 > 0.5m/s (一定移動後)
    
    RIDING_LIFT --> IDLE : 高度上昇停止 かつ 移動停止
    RIDING_LIFT --> SKIING : 急速な高度降下開始
    
    SKIING --> [*] : 計測終了
```

- **リフト搭乗確定時のフック**:
  - 直前の「滑走（RUN）」データを確定してRoom DBに保存。
  - **リアルタイム音声サマリーをトリガー**（「○本目終了。滑走距離 1.2km、最高速度 54km/h」をイヤホンに再生）。
  - リフト中は不要な高周波IMUサンプリングを自動停止し、バッテリー消費を半減。

---

### 3.2 3Dモーション＆エッジ角解析パイプライン

スマホはウェアのポケット（腿や胸）に入れられるため、**姿勢キャリブレーション（姿勢推定）**を行ってからターンやトリックを判定します。

```mermaid
flowchart TD
    RawSensor["6軸センサー入力 (Acc 100Hz / Gyro 100Hz)"] --> LowPass["ローパスフィルタ (重力ベクトル抽出)"]
    RawSensor --> HighPass["ハイパスフィルタ (動的加速度・ジャンプ抽出)"]
    
    LowPass --> CoordinateAlign["座標系変換 (端末座標系 -> 身体/進行方向座標系)"]
    CoordinateAlign --> LeanAngle["エッジ角 (ロール角/リーン角) 計算"]
    
    HighPass --> JumpDetect{"無重力状態 (Acc < 0.2G) 検出?"}
    JumpDetect -- Yes --> AirTimeTimer["滞空時間タイマー起動"]
    AirTimeTimer --> ImpactDetect{"着地衝撃 (Acc > 2.5G) 検出?"}
    ImpactDetect -- Yes --> GyroIntegral["滞空中のジャイロZ軸積分 (回転角算出)"]
    GyroIntegral --> TrickClassification["トリック判定 (Ollie / 180 / 360)"]
    
    RawSensor --> FFT["周波数解析 (路面振動抽出: 15〜40Hz)"]
    FFT --> SnowSurfaceIndex["雪質・バーン荒れ度指数算出"]
```

1. **姿勢キャリブレーション**:
   - 重力加速度の方向からスマホの傾き姿勢（基準クォータニオン）を初期化・動的補正。
2. **ターン・エッジ角の算出**:
   - 進行方向に対する左右の傾斜角（Roll）を算出し、遠心加速度（Gフォース）と照合して真のエッジ角を推定。
3. **トリック判定**:
   - オーリー／ジャンプ: 抜重（落下による一時的な低G状態: $< 0.3G$）から着地衝撃（$> 2.0G$）までの時間を「滞空時間」として計測。
   - スピン: 滞空時間内の鉛直軸周りの角速度（Yaw）を積分し、回転角度（$180^\circ, 360^\circ$）を算出。

---

## 4. バックグラウンド＆省電力設計

ゲレンデの低温環境下でも、1日（約6〜7時間）連続動作させるための省電力アーキテクチャ。

1. **フォアグラウンドサービス (`TrackingForegroundService`)**:
   - Android OSによるプロセスキルの防止。
   - 通知バーに「現在の速度」「滑走本数」「一時停止/再開ボタン」を常時表示。
2. **動的センサーサンプリング制御**:
   - **滑走中 (SKIING)**:
     - GPS: 1Hz (高精度 `PRIORITY_HIGH_ACCURACY`)
     - IMU (加速度/ジャイロ): 50Hz〜100Hz
   - **リフト中 / 休憩中 (RIDING_LIFT / IDLE)**:
     - GPS: 0.1Hz〜0.2Hz（5〜10秒に1回）
     - IMU: **停止**（CPU負荷とバッテリー消費を大幅削減）
3. **バッチ書き込み**:
   - GPS座標やセンサーデータはメモリ上のリングバッファに蓄積し、数十件単位でRoom DBにバルクインサート（I/O負荷低減）。

---

## 5. 音声フィードバック（Text-to-Speech）設計

- **発話キューマネージャー (`AudioCoachManager`)**:
  - 音声案内が重複しないよう優先度付きキューで管理。
    1. **緊急/即時案内 (最高優先)**: 速度上限超過警告
    2. **イベント案内 (中優先)**: トリック成功通知（「Nice 360!」）
    3. **ラン終了サマリー (低優先)**: リフト乗車検知時の統計読み上げ
  - 音楽再生アプリ（Spotify等）と競合しないよう、`AudioFocusRequest`（Audio Ducking: 音声案内中だけ音楽の音量を一時的に下げる）を使用。
