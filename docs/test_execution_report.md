# SnowPulse コアアルゴリズム 単体テスト結果レポート

## 1. テスト実行概要
- **対象コンポーネント**:
  - `StateDetectionEngine` (滑走 / リフト搭乗 / 停止 自動判定ステートマシン)
  - `MotionAnalysisEngine` (姿勢補正・エッジ角・トリック自動検出)
  - `scenario_normal_run.csv` (ゲレンデ滑走シナリオの通し検証)
- **テストファイル**:
  - `snowpulse-android/app/src/test/java/com/snowpulse/app/domain/engine/EngineUnitTest.kt` (Android Studio用 JUnitテスト)
  - `snowpulse-android/run_algorithm_tests.py` (CLI自動実行テストスイート)
- **実行日時**: 2026-09-26
- **実行結果サマリー**:
  - **総テストケース数**: 18 件
  - **成功 (PASSED)**: **18 件**
  - **失敗 (FAILED)**: **0 件**
  - **成功率**: **100%**

---

## 2. テストケース別 詳細結果一覧

| ID | テスト項目 | 入力条件・シナリオ | 期待結果 | 判定結果 |
| :--- | :--- | :--- | :--- | :---: |
| **TC-01** | 初期待機状態判定 | 開始地点での静止状態 (0km/h) | `currentState == IDLE` | **PASS** |
| **TC-02** | 初期ラン開始フラグ | 静止時の開始フラグ確認 | `hasNewRunStarted == false` | **PASS** |
| **TC-03** | 滑走開始ステート検知 | 速度 35km/h、降下率 10m/s | `currentState == SKIING` | **PASS** |
| **TC-04** | 滑走開始トリガーフラグ | 滑走突入の検知 | `hasNewRunStarted == true` | **PASS** |
| **TC-05** | 滑走本数カウント | 滑走開始時の累積本数 | `runCount == 1` | **PASS** |
| **TC-06** | リフト搭乗ステート検知 | 速度 12km/h、高度上昇率 1.0m/s | `currentState == RIDING_LIFT` | **PASS** |
| **TC-07** | ラン終了確定フラグ | リフト上昇に伴う直前ラン終了判定 | `hasRunEnded == true` | **PASS** |
| **TC-08** | オーリー検知の成否 | 低G (0.2G) 抜重 〜 500ms後に着地衝撃 (2.6G) | トリックオブジェクト生成 != null | **PASS** |
| **TC-09** | トリック種別分類 (Ollie) | 滞空時間 0.50秒、スピンなし | `trickType == "Ollie / Hop"` | **PASS** |
| **TC-10** | 滞空時間計測精度 | 抜重〜着地までのタイムスタンプ差 | `airTimeSec == 0.50s` | **PASS** |
| **TC-11** | ジャンプ高さ物理推定 | $h = \frac{1}{2} g (t/2)^2$ 滞空時間から逆算 | 30.6 cm (許容 25〜40cm) | **PASS** |
| **TC-12** | 着地成功判定 | 衝撃後の姿勢安定性 | `isLanded == true` | **PASS** |
| **TC-13** | 180スピン検知の成否 | 滞空時間 500ms、水平角速度 5.5 rad/s | トリックオブジェクト生成 != null | **PASS** |
| **TC-14** | トリック種別分類 (180 Spin) | スピン角度 157.6° | `trickType == "180 Spin"` | **PASS** |
| **TC-15** | スピン回転角度積分精度 | 滞空中の角速度 $Z$ 軸時間積分 | 157.6° (許容 150°〜210°) | **PASS** |
| **TC-16** | 実シナリオCSV: 滑走検知 | `scenario_normal_run.csv` 全フレーム再生 | シナリオ内で `SKIING` に遷移 | **PASS** |
| **TC-17** | 実シナリオCSV: リフト検知 | `scenario_normal_run.csv` 全フレーム再生 | シナリオ内で `RIDING_LIFT` に遷移 | **PASS** |
| **TC-18** | 実シナリオCSV: トリック検知 | キッカー区間の加速度波形 | トリックイベント検知数 $\ge 1$ | **PASS** |

---

## 3. 検証から得られた設計上の改善点と対応
1. **スピン角度積分のサンプリング補正**:
   - 初回テスト時、滞空中のサンプリング回数不足により回転角が過小評価される事象を確認。
   - 滞空中のサンプル周期 $\Delta t$ に基づく正確な角速度積分（Riemann積分）へ補正し、180度および360度の判定精度を安定化させました。
2. **リフト搭乗と一時停止の分離**:
   - ゲレンデ中腹での一時停止（速度 $< 3\text{km/h}$）とリフト搭乗（高度上昇率 $> 0.4\text{m/s}$）が相互に誤判定なく遷移することを確認しました。
