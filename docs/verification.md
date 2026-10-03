# 検証記録

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
