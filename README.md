# Minecraft Recipe Dump

MOD入りMinecraftの**実際に読み込まれたレシピを収集し、ゲーム外で調べる開発補助ツール**です。専用サーバー側の収集MODと、ダンプを集計・比較するKotlin/JVM CLIを同じrepoで開発します。

収集はMinecraft／Loader本来の読み込み処理と、各MODの登録済みシリアライザーを使います。MODのJARからレシピJSONを抽出する方式ではありません。ゲーム画面やプレイヤー接続は不要ですが、収集時には専用サーバーを起動します。

初期対応は **Minecraft 1.21.1 / NeoForge 21.1.252 / Java 21**。収集MODには **KotlinForForge 5.5.0の配布JAR**も必要です。CLIはMinecraft・NeoForge・KotlinForForgeなしで動作します。現在は実証コードを移植した初期実装で、レシピの競合自動判定や任意インスタンスの自動セットアップは含みません。

## 収集MODを使う

1. 対応する専用サーバーの**新規検証環境またはコピー**を用意します。対象MOD、設定、スクリプト、データパックも収集したい構成に合わせます。
2. このrepoでビルドした通常JARと、[KotlinForForge 5.5.0の配布JAR](https://modrinth.com/mod/kotlin-for-forge/version/tdvjylfz)を `mods/` に置きます。開発用Maven aggregate JARとは別物です。
3. EULAの確認など、通常のサーバー初回セットアップを済ませます。
4. JVMオプションを付けて起動します。`-D` はメインクラス／起動引数ファイルより前に置きます。

```sh
java -Drecipedump.enabled=true -Drecipedump.output=/absolute/path/recipes.json \
  @user_jvm_args.txt @libraries/net/neoforged/neoforge/21.1.252/unix_args.txt nogui
```

Windowsの通常サーバーでは `win_args.txt` を使用します。空白を含む引数はshellの規約に従って引用します。

最初のサーバーティック終了時にレシピとアイテムタグを収集し、JSON保存後に通常のワールド保存・停止を行います。ワールド生成とポート待受は通常どおり発生します。オプションなしでは収集・自動停止を行わず、クライアントでも収集を行いません。

既定の出力先はサーバー作業ディレクトリの `recipes.json`。既存ファイルは完成したダンプで置き換えます。成功時は `RECIPE_DUMP_SAVED` とrunId、失敗時は `RECIPE_DUMP_FAILED` をログに出します。**保存失敗でも正常停止の終了コードは0になり得ます。** 実行ごとに新しい出力パスを使い、今回のログとダンプを照合してください。

## CLIを使う

```sh
./gradlew :cli:installDist
cli/build/install/recipe-dump/bin/recipe-dump summary /path/to/recipes.json
cli/build/install/recipe-dump/bin/recipe-dump csv /path/to/recipes.json > recipes.csv
cli/build/install/recipe-dump/bin/recipe-dump diff /path/to/before.json /path/to/after.json
cli/build/install/recipe-dump/bin/recipe-dump recipes /path/to/recipes.json --id minecraft:iron_ingot_from_smelting_iron_ore
cli/build/install/recipe-dump/bin/recipe-dump recipes /path/to/recipes.json --type minecraft:smelting
cli/build/install/recipe-dump/bin/recipe-dump recipes /path/to/recipes.json --namespace create --type create:crushing
cli/build/install/recipe-dump/bin/recipe-dump items /path/to/recipes.json
cli/build/install/recipe-dump/bin/recipe-dump items /path/to/recipes.json --namespace create
cli/build/install/recipe-dump/bin/recipe-dump items /path/to/recipes.json --tag minecraft:planks --namespace minecraft
```

Java 21を `JAVA_HOME` に設定します。Windowsは `gradlew.bat` と `recipe-dump.bat` を使用します。配布用ZIPは `./gradlew :cli:distZip` で生成します。

- `summary`: レシピ数、アイテムタグ数、RecipeType別件数。
- `csv`: ID・型・シリアライザー・codecのJSON。独自形式はdata列に保持。
- `diff`: レシピID単位の追加・削除・内容変更。JSONのobject key順序は無視。タグ・構成変更そのものの比較は未実装。
- `recipes`: レシピ一覧。任意の `--id`・`--type`・`--namespace` で絞り込み、組み合わせはAND条件。フィルターなしなら全件。
- `items`: 全登録アイテムID一覧。任意の `--tag` と `--namespace` をANDで指定できます。タグ検索は旧ダンプでも利用でき、全件一覧は新しい収集MODで再収集した `items` が必要です。

検索値は完全一致で、namespaceの省略補完や部分一致は行いません。`--namespace` は検索対象のレシピIDまたはアイテムIDのコロン前の文字列に適用し、実際の提供元MOD／JARや材料・出力の所属は判定しません。
装置は `recipes dump.json --station minecraft:furnace` のように完全修飾アイテムIDで指定し、`stations dump.json` で対象ダンプの版に対応する装置を一覧できます。材料・出力は `--uses minecraft:iron_ingot`／`--produces minecraft:iron_ingot` を任意のAND条件として指定します。

`recipes` のJSON結果は `recipes` 配列と `count`、`items` は `items` 配列・`count` と、指定時のみ `tag`・`namespace` を持ちます。ID順で出力し、タグ内の重複アイテムは除去します。未知のID・type・namespace・タグによる0件検索も終了コード0です。材料検索はMinecraft 1.21.1の対応バニラserializerと下表の固定MOD形式を解析し、未知形式を含む部分検索はJSONをstdoutへ返して終了コード2になります。analysis／diagnostics／evidenceで未判定件数と根拠を確認できます。
オプションは `--name value` 形式のみ。同じオプションの重複、未知のオプション、値不足・空値、余分な位置引数は拒否します。診断はstderrへ出します。

不完全なダンプ、未対応のschema、重複ID、読み込み失敗、使用法の誤りではCLIが非ゼロ終了します。競合やゲーム内の成立可否を自動判定するコマンドはありません。

## ダンプが保証する範囲

schemaVersion 1の `recipes` は現在のRecipeManagerのスナップショットです。各レシピはID、RecipeType、serializer、再シリアライズしたdataを持ちます。解決済みアイテムタグ、MOD版一覧、選択データパック順序、取得段階、runId、取得エラーも保存します。

`complete` は列挙したレシピの書き出し・codec再読み込みが成功したことを示します。**入力レシピすべての読み込み成功ではありません。** 対象版の読み込みエラーフラグだけでは失敗を判定できないため、`loadDiagnostics` は `not-tracked` としています。入力の解析エラーはサーバーログでも確認してください。

元JSONの条件や既定値の書き方は保持されないことがあります。RecipeManager外の独自加工、JEI／EMI専用表示、後のティックやreloadでの変更、他Loader・他Minecraft版は今回の保証範囲に含みません。

## 開発

| module | 担当 |
| --- | --- |
| `core/` | ゲームに依存しないJSON検証、atomic保存、集計、CSV、差分、検索 |
| `neoforge/` | イベント購読、RecipeManager収集、登録済みcodec、正常停止 |
| `cli/` | 引数、ファイル読み込み、stdout／stderr、終了コード |

```sh
./gradlew check :neoforge:build :neoforge:verifyModJar :cli:distZip
./gradlew :neoforge:runGameTestServer
node scripts/check-gametest-result.mjs
node --test scripts/test/*.test.mjs
```

通常MOD JARは `neoforge/build/libs/`。開発用ダンプ起動は `./gradlew :neoforge:runServerDump`、環境は `.local/dump-server/`（EULA等の初回設定が必要）。対話的クライアントは収集ツールの基本検収には不要です。

LLMは [AGENTS.md](AGENTS.md) と必要なrepo内skillを読みます。[設計](docs/design.md)、[ダンプ形式](docs/dump-format.md)、[対応構成](docs/compatibility.md)、[検証記録](docs/verification.md)に判断と実測を残します。

基盤は [moco-server/minecraft-mod-template](https://github.com/moco-server/minecraft-mod-template) 0.1.0です。識別情報は初期化済みで、初期化scriptを再実行する必要はありません。非公開repo、ソースライセンスはMITです。

材料・出力は完全修飾アイテムIDの候補検索です。全材料の充足・必要数量・ゲーム内の作成可否は判定しません。出力が読めれば独自材料があっても出力方向だけの検索は可能です。空タグは不一致、参照タグ欠落は不明です。動的出力・独自材料は診断に残します。

## 初期MOD検索の対応範囲

| MOD（配布JAR） | adapter選択のmods.version | 対応serializer（namespace省略） | 装置ID（namespace省略） |
| --- | --- | --- | --- |
| Create 6.0.10 | 6.0.10 | crushing, cutting, haunting, item_application, milling, pressing, sandpaper_polishing, splashing, deploying, mechanical_crafting | crushing_wheel, millstone, mechanical_saw, deployer |
| Mekanism 10.7.19.85 | 10.7.19 | crushing, enriching, combining, sawing | crusher, enrichment_chamber, precision_sawmill |
| Farmer's Delight 1.3.4 | 1.3.4 | cooking, cutting | cooking_pot, cutting_board |

バニラ装置はcrafting_table、furnace、blast_furnace、smoker、campfire／soul_campfire、stonecutter、smithing_table。対象表・版が一致しない装置検索は終了コード1です。装置対応表はRecipeTypeへの検索入口で、装置の全能力・代替・複合設備は保証しません。Mekanismのmods.versionはbuild番号を持たず、10.7.19.85以外のJARの同一性・互換性は確認できません。

NeoForgeのcompound／intersection／difference材料を三値で評価します。成分条件、block tag、未知の独自材料、FDのitem_abilityは不明を維持します。Createの液体処理・sequenced assembly、Mekanismの化学物質／液体形式、表外の動的クラフトは未対応です。

出力のcountは記載個数です。chanceKindはCreateのper-item-roll、Mekanism副産物のsecondary-output、FD cuttingのbase-chanceを区別し、FDはconditions=fortune-and-server-configも付けます。FDの基礎chance=0も設定・fortuneで変わる候補として扱います。Create deployingの保持材料はretained-input、FD cuttingの工具はtoolとしてusesに含め、FD cookingのserving containerは含めません。

```sh
recipe-dump recipes dump.json --station create:crushing_wheel --produces minecraft:amethyst_shard
recipe-dump recipes dump.json --uses minecraft:raw_iron --produces minecraft:iron_ingot
recipe-dump stations dump.json
```
