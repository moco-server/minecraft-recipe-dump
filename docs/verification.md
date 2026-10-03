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

## 2026-10-03 後続CLI第2段階

Java 21、Minecraft 1.21.1、NeoForge 21.1.252で`check :neoforge:build :neoforge:verifyModJar :cli:distZip :cli:installDist`成功。core 17件、CLI 9件、Node 29件成功。GameTestは追加したregistry完全一致・air／diamond存在を含む必須3件成功。

通常配布JARを新しい検証instanceと各ケースの新規worldで実行した。Create 6.0.10、Mekanism配布JAR 10.7.19.85、Farmer's Delight 1.3.4、KFF 5.5.0で登録アイテム2,598件（Create namespace 699件）、baselineレシピ4,749件。itemsは重複なし・昇順。SAVEDマーカーと新runId、正常停止を確認した。既存出力の置換、未指定時の継続後console stop、非空ディレクトリ宛て保存のFAILEDと既存sentinel維持も確認した。JVM終了コードはいずれも0で、保存失敗の判定にはFAILEDマーカーを使った。

独自datapackの条件付きincluded収集・excluded除外、diamond_blockの出力をdiamond×2へ上書き、実行時fixtureによるruntime_added追加・gold_block除外を再確認した。modified／dynamicは各4,750レシピ、アイテム数は各2,598件。配布CLIで一覧、namespace、タグ＋namespace、新旧タグ検索（planks 11件）、旧ダンプ一覧拒否を独立集計と照合した。並行サーバー検証中にJVMのスレッド資源警告がstdoutへ混ざったため、CLI照合はJAVA_OPTS=-XX:ActiveProcessorCount=2で実施し、JSONのみのstdoutを確認した。

証拠は実装worktreeの.local/inventory-*.logと.local/integration/。未検証: registry列挙そのものの障害注入、他Minecraft／MOD版、対話的client。列挙失敗を握りつぶさない既存外側FAILED経路はコードで確認した。

## 2026-10-03 後続CLI第3段階

同じJava／Minecraft／NeoForge版で必須check／build／verifyModJar／distZipとinstallDistが成功。core 26件、CLI 11件のテストが成功し、実codec由来9バニラserializer fixture、三値のOR／AND、欠落タグ・誤型・不正出力count、方向独立・partition・根拠・null保持、終了コード0／1／2を検査した。

配布CLIで通常JARの4,749レシピsnapshotを独立Python集計と照合した。全件diamond出力検索は解析済み2,680、未対応2,069、未解決0で終了コード2。smelting 118候補のiron_ingot出力およびraw_iron入力とのANDは全118件を評価でき終了コード0。diamond_blockの入力diamond／出力diamond_blockのAND、metadata絞り込み後0候補のexhaustiveも一致した。GameTest必須3件とNode 29件も成功。

証拠は.local/material-*.log、.local/integration/vanilla-cli-result.log。この段階ではMOD固有serializerを未対応として数える。バニラ名のserializerでも独自材料や未知タグは部分検索になる。収集と停止は第2段階から変更していない。

## 2026-10-03 後続CLI第4段階

必須check／neoforge build／verifyModJar／cli distZip、installDist成功。core 37件、CLI 13件、GameTest必須3件、Node 29件成功。固定MODの実codec由来16 serializer fixtureに加え、複合材料三値・版違い・確率境界・Mek副出力組合せ・工具と保持入力・流体／化学物質未対応、版付き17装置表とCLIの終了コードを検査した。

通常JARで取得した同じ4,749レシピsnapshotに配布CLIの23照合を実行。全MOD対象typeの出力を各serializer別に全件評価でき、独立集計と一致した。件数は以下の通り。

| serializer | 評価した出力検索候補 |
| --- | ---: |
| create:crushing | 67 |
| create:cutting | 143 |
| create:deploying | 167 |
| create:haunting | 21 |
| create:item_application | 8 |
| create:mechanical_crafting | 4 |
| create:milling | 55 |
| create:pressing | 8 |
| create:sandpaper_polishing | 1 |
| create:splashing | 37 |
| farmersdelight:cooking | 28 |
| farmersdelight:cutting | 105 |
| mekanism:combining | 82 |
| mekanism:crushing | 200 |
| mekanism:enriching | 169 |
| mekanism:sawing | 136 |

全件diamond出力検索は解析済み3,911／未対応838／未解決0。全件iron_axe入力は3,858／820／71、diamondおよびraw_iron入力は各3,824／820／105。すべて独立三値評価・タグ解決と一致し、終了コード2。crushing_wheelとamethyst_shard出力のANDは全67候補を評価し、stationTypes根拠を確認。新旧ダンプのstationsは各17装置でID順。

既存metadata／タグ検索は保存済み3 snapshotで39照合すべて一致。一覧検索の第2段階5ケースも再実行して一致した。証拠は.local/later-*.logと.local/integration/later-cli-result.log。Mekanismのheaderは10.7.19で、配布JAR 10.7.19.85以外のbuildを識別・保証できない。表外形式・他版・clientは未検証。

### 全体レビューと修正

新規contextのread-onlyレビュアー1名が第2〜4段階の全diffを確認。Critical／Minorなし、Important 1件（pattern使用key欠落を別keyの一致が隠す）を修正した。バニラshapedとCreate mechanical_craftingのcore回帰2件、CLI回帰1件が修正前に失敗し、key存在の先行検証で成功した。未使用key無視、材料ORの既知一致優先、出力だけの解析は維持する。

修正後の必須check／build／verifyModJar／distZipとinstallDist成功、core 39件・CLI 14件成功。配布CLIで両形式の不正例がexit2、candidate1／analyzed0／unresolved1、invalid_shapeと$.key.Bになることを確認。実snapshotの新23／旧39／inventory5照合も再実行して一致した。最終GameTest必須3件とNode 29件も成功。証拠は.local/review-*-fix-red.log、.local/review-fix-green.log、.local/later-final-review.md。


## JSONインターフェースとしてのCLI検索（2026-10-03）

Minecraft／MODの版情報による検索gateを除去。版欠落／不一致でも同じserializer・JSON形式は解析し、未知serializer／必要field欠落は従来どおり診断する。stationsはsnapshot内のRecipeTypeに対応する既知別名を列挙し、既知別名で該当typeがない検索は正常0件。

/tmp/recipe-dump-later-search（main 2aeda0cと同じ基点の隔離worktree）で新しい契約の回帰テストを先に実行し、core 3件とCLI 2件の期待どおりの失敗を確認。実装修正後にcore 40件・CLI 15件すべて成功。ktlintFormat、check／:neoforge:build／:neoforge:verifyModJar／:cli:distZip／:cli:installDist成功。静的解析で新テストのLongMethod 1件を検出し、診断結果の検証をtest helperへ分けて解消した。収集・保存・停止・配布MODの契約は変更していない。

GameTest必須3件、結果検査script、Node 29件すべて成功。保存済み4,749レシピの実snapshotを配布CLIで処理し、既存MOD／装置／材料の独立23照合は一致。さらに全件出力・入力、装置＋出力、装置のみ、レシピnamespace、アイテムnamespace、stationsの7検索について、元の版情報・minecraft／mods削除・将来版を模した値の3構成で計21照合し、終了コードと結果JSON全体が一致した。元snapshotは別ファイルへコピーし、書き換えていない。他版のゲームやMODを新規起動した検証ではなく、JSON検索の版非依存を検証した。

ローカルread-onlyレビューでCritical／Important／Minorの具体的指摘なし。証拠は隔離worktreeの.local/json-interface-{baseline,red,core-red,green,check,game,game-result,node,real,versions}.log。
