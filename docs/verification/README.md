# 検証の現状と記録

このページは現在までに確認した範囲を要約する。実行構成・コマンド・件数・証拠の保存場所は日付付き記録を参照する。過去の中間段階の未実装記述を、現在の制限として読まない。

## 検証済みの範囲

実測構成はMinecraft 1.21.1／NeoForge 21.1.252／Java 21、Linux（WSL2）。通常配布MODの検証には作者配布のKotlinForForge 5.5.0、Create 6.0.10、Mekanism配布JAR 10.7.19.85、Farmer's Delight 1.3.4を使った。依存版の根拠は [対応構成](../reference/compatibility.md) を参照する。

| 検証 | 確認した内容 | 根拠 |
| --- | --- | --- |
| ビルド・静的解析・配布内容 | check、通常MOD JAR、verifyModJar、CLI distZip。JSON検索の方針変更後はcore 40件・CLI 15件成功 | [JSON検索の契約変更](records/2026-10-03-development.md#jsonインターフェースとしてのcli検索2026-10-03) |
| GameTest | RecipeManager snapshotとcodec、収集後のレシピ動作、登録アイテム一覧。必須3件成功 | [第2段階](records/2026-10-03-development.md#2026-10-03-後続cli第2段階)、[契約変更後](records/2026-10-03-development.md#jsonインターフェースとしてのcli検索2026-10-03) |
| 通常配布MODでの専用サーバー | 有効化時の保存と正常停止、未指定時の継続、既存出力の置換、保存失敗と既存内容維持 | [第2段階](records/2026-10-03-development.md#2026-10-03-後続cli第2段階) |
| レシピ変更の収集 | 独自datapack、条件による除外、上書き、ServerStartedEventでの実行時追加・削除 | [Kotlin移植](records/2026-10-03-development.md#2026-10-03-実証コードのkotlin移植)、[第2段階](records/2026-10-03-development.md#2026-10-03-後続cli第2段階) |
| 配布CLIの実データ処理 | 4,749件のsnapshotを含む保存済みデータで集計・CSV・差分・検索を照合。MOD形式・装置検索の23照合、metadata／タグ39照合、一覧5照合 | [Kotlin移植](records/2026-10-03-development.md#2026-10-03-実証コードのkotlin移植)、[第4段階](records/2026-10-03-development.md#2026-10-03-後続cli第4段階) |
| JSON検索の版非依存 | 版情報削除・値変更による21照合で結果と終了コードが一致。他版のゲーム起動を検証したものではない | [JSON検索の契約変更](records/2026-10-03-development.md#jsonインターフェースとしてのcli検索2026-10-03) |
| 開発scripts | 初回リリースのLinux CIでNodeテスト51件成功 | [初回配布](records/2026-11-22-changesets-release.md#v010-の初回配布) |
| 初回リリース | GitHub ReleasesへのJAR・ZIP添付、ダウンロードしたファイルの照合、GitHub Packagesへの両成果物の登録 | [初回配布](records/2026-11-22-changesets-release.md#v010-の初回配布) |

## 未検証と保証しない範囲

- 他Minecraft版・Loader・MOD版でのゲーム動作。Mekanismのダンプ内versionは10.7.19で、配布JARのbuild番号を識別できない。
- Windowsネイティブ／macOSの配布物実行、対話的client・プレイヤー接続、KubeJS実物。
- 後のティック／reloadで変化するレシピの収集、登録アイテム列挙そのものの障害注入。
- 入力レシピ全件の解析成功。`loadDiagnostics=not-tracked` で、`complete` はsnapshotの書き出し成功を示す。
- 表外のJSON形式、RecipeManager外の加工、完全な競合検出、全材料の充足やゲーム内での作成可否。

ビルド、GameTest、通常配布JARの起動、配布CLIの実データ処理は別々の検証として扱う。JVM終了コード0だけでは保存成功と判定しない。初回の通常MOD JARとCLI ZIPはアクセス権のあるユーザーが取得できる。[GitHub Release v0.1.0](https://github.com/moco-server/minecraft-recipe-dump/releases/tag/v0.1.0)

## 実測記録

- [2026-11-22 Changesetsによる公開](records/2026-11-22-changesets-release.md)

- [2026-10-06 CLI利用者向けskill](records/2026-10-06-cli-consumer-skill.md): CLI ZIPへのskill同梱、配布CLIの16照合、LLMによる利用手順確認。ビルド成功、Node 50件成功。今回のGameTest再実行はfml_loaderモジュール重複で起動失敗し、成功件数未確認。

- [2026-10-03 Release CD](records/2026-10-03-release-cd.md): タグと版・成果物の検証、draft公開手順のAPI境界テスト、CLIガイド同梱、ビルド・新規ワールドのGameTest・配布ZIPの起動。

- [2026-10-03 文書整理](records/2026-10-03-docs-reorganization.md): 文書分類・参照更新、開発scriptの新配置対応、ビルド・新規ワールドのGameTest・Nodeテスト。

- [2026-10-03 開発・検証履歴](records/2026-10-03-development.md): Kotlin移植、CLI検索第1〜4段階、レビュー修正、JSON検索の方針変更、利用ガイド分離。

新しい検証は `records/YYYY-MM-DD-<topic>.md` へ記録する。版・手順・観測結果・未実施項目を記し、検証範囲が変わったらこのページも更新する。
