# 検証記録

## 2026-10-03: CLI検索の第1段階

Minecraft 1.21.1、NeoForge 21.1.252、Eclipse Adoptium Java 21.0.12.1、Kotlin 2.0.21。Linux（WSL2）の作業用worktreeで実施。Java 21をJAVA_HOME、既存キャッシュをコピーした.local/gradleをGRADLE_USER_HOMEへ指定した。

追加機能はrecipesのID／type／レシピnamespace検索とitemsのタグ検索。schema 1、収集タイミング、停止処理は変更していない。

実施:

```sh
./gradlew :core:test :cli:test
./gradlew :core:test --tests '*DumpSearchTest'
./gradlew ktlintFormat :core:test :core:check
./gradlew :cli:test --tests '*CliTest'
./gradlew ktlintFormat :core:test :cli:test ktlintCheck
./gradlew check :neoforge:build :neoforge:verifyModJar :cli:distZip :cli:installDist
./gradlew :neoforge:runGameTestServer
node scripts/check-gametest-result.mjs
node --test scripts/test/*.test.mjs
python3 .local/search-integration/check.py
```

変更前のcore／CLI 6テストは成功。検索テストを先に追加し、coreはDumpSearch未実装によるコンパイル失敗、CLIは新コマンド拒否による3テスト失敗を確認してから実装した。
実装後はcore 13件（新規検索9件）、CLIは初回7件・最終修正後8件、buildSrc 7件が失敗・skipなしで成功。完全一致、AND、namespace境界、安定した出力順、未知の検索値の0件、未知フィールド保持、入力の非破壊性、タグ重複除去、不正な参照対象タグ、引数エラーのI/Oに対する優先、既存コマンドを確認した。

root check、整形・型解決付きdetekt、core境界、通常MOD JAR内容検査、CLI ZIP／installDistは成功。GameTestは必須2件が成功し、結果検査scriptでも2件を確認。Node scriptテスト29件は失敗0件。
Gradle 9に関する既存の非推奨警告とbuildSrcのplugin descriptor警告は残るが、各検証は成功した。

配布CLIは、前回の通常配布JAR検証で保存した3つの実ダンプを.local/search-integrationへコピーして使用。今回の通常配布JARサーバー起動は実施していない。Pythonの独立したJSON絞り込みと、JSON結果全体・件数・終了コード0・stderr空を照合し、各13検索、計39検索が一致した。

| snapshot | レシピ数 | タグ数 | smelting | create namespace | create AND crushing |
| --- | --- | --- | --- | --- | --- |
| baseline | 4,749 | 674 | 118 | 1,553 | 67 |
| modified | 4,750 | 675 | 118 | 1,553 | 67 |
| dynamic | 4,750 | 675 | 118 | 1,553 | 67 |

全件、diamond_blockのID、type、namespace、AND条件、条件不一致、未知ID／type／namespace、短いID、planksタグ、未知／短いタグを照合。レシピ行の独自codec JSONも保持され、MODの形式解釈は行っていない。
入力hash・照合script・結果JSON・ログは.local/search-integrationと.localに保存しGit除外。

未検証: Windowsネイティブの配布CLI起動。全登録アイテム一覧、材料・出力・装置名検索は未実装。
CodeRabbitの外部サービスへの差分送信は自動承認レビューで拒否されたため未実施。別エージェントによる外部送信を伴わない最終レビューで、CLIのGson出力が明示的なnullフィールドを省略する問題を1件検出した。
レシピ行・data内・ネストしたobject・配列内objectのnull保持を確認する回帰テストを追加し、失敗を再現してからrecipes出力だけにserializeNullsを適用した。summary／csv／diffの出力方針は維持した。修正後にcheck・JAR検査・CLI配布ビルドと39件の実データ検索照合を再実行し成功。配布CLIでも別のnull検証用fixtureを1件処理し、入力行全体の保持を確認した。

## 2026-10-03: 実証コードのKotlin移植

構成: Minecraft 1.21.1、NeoForge 21.1.252、Eclipse Adoptium Java 21.0.12.1、Kotlin 2.0.21、KotlinForForge 5.5.0。Linux（WSL2）。固定ビルドツールはcompatibility.mdとcatalogを参照。

実施:

```sh
./gradlew ktlintFormat check :neoforge:build :cli:installDist --write-locks
./gradlew :neoforge:runGameTestServer :cli:distZip
node scripts/check-gametest-result.mjs
node --test scripts/test/*.test.mjs
```

GradleにはJava 21と専用GRADLE_USER_HOMEを指定した。coreのダンプ検証・差分・CSV・atomic保存の4テスト、CLIの使用法／I/O失敗と不正JSONの2テストを実施。テンプレート由来buildSrcのJAR検査・依存境界テスト、Node script 29テストも対象。単体テストとroot check、通常JAR検査は成功。

GameTestは実RecipeManagerからのsnapshotとcodec再読み込み、および収集後のiron_blockのレシピlookup・assembleを確認し、必須2件が成功。通常配布JARとは別のsource setで行う。

通常サーバー検証は親packの検証用NeoForgeインストールのライブラリを利用し、設定とワールドを新規に作成。Create 6.0.10、Mekanism配布JAR 10.7.19.85、Farmer's Delight 1.3.4、作者配布のKFF 5.5.0をコピーした。通常JARの実行で基準4,749件、49種類、674アイテムタグを取得。全件encode/decode成功、終了コード0。レシピとタグはJava実証版の基準JSONと一致した。

データパックでdiamond_blockを上書きし、導入MODによる条件付きレシピと独自タグを追加。4,750件、675タグを取得し、未導入MODを条件にしたレシピは除外された。配布用Kotlin CLIのdiffで追加probe:included、変更minecraft:diamond_blockを確認。

基準の起動から終了までは13.71秒、データパック変更後は10.25秒。各1回、同一ホストでGameTest等も実行した観測であり、性能比較や保証ではない。

追加検証:

- オプションなしでDone後3秒も稼働し、ダンプなし。検証側のconsole stopで正常停止（12.55秒）。
- 別の検証用Java MODを一時環境だけへ導入し、ServerStartedEventで追加したprobe:runtime_addedと削除したminecraft:gold_blockを取得。配布用CLIも追加・削除を検出（9.90秒）。このfixtureは配布JARに含めない。
- 保存先をディレクトリにして失敗させ、RECIPE_DUMP_FAILED、成功マーカーなし、通常停止を確認。JVM終了コードは0。
- 配布用CLIのCSV 4,750行を別のCSV parserで読み込み、data列のJSONを全件解析できた。

## 初期Java実証から引き継ぐ制限

親packの使い捨て実証では、不正なserializerによる解析エラーがログへ出てもhadErrorsLoadingがfalseであった。Kotlin版はこのフラグを成功判定に使わずloadDiagnostics=not-trackedとしている。completeはsnapshot書き出し完了のみ。

未検証: KubeJS実物、他Loader／Minecraft版、Windowsネイティブでの起動、client・接続、後のティック／reloadでの変更、競合検査、ワールド生成前の終了。通常のダンプ取得にGUIは不要で、recipe viewerは導入していない。

証拠のログ・JSON・入力hashは開発環境の.localへ保存しGit除外。利用者のワールドは使用していない。
