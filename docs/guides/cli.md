# Minecraft Recipe Dump — CLI利用ガイド

収集MODが保存したJSONファイルを、ゲーム外で集計・比較・検索するCLIです。Minecraft・NeoForge・KotlinForForgeは必要ありません。ダンプの作成方法は [収集MOD利用ガイド](mod.md) を参照してください。

## 入手と必要環境

CLIの配布先は [GitHub Releases](https://github.com/moco-server/minecraft-recipe-dump/releases) です。リリースPRのマージ後に検証したCLI ZIPを添付し、このガイドをZIP内の `README.md` に同梱します。まだReleaseがない場合は [ソースからの起動手順](../../README.md#開発) を参照してください。

以下は配布ZIPを入手した場合の手順です。Java 21を用意し、`JAVA_HOME` をそのインストール先へ設定します。ZIPには起動スクリプトと依存JARが含まれますが、Java本体は含まれません。

1. Releaseに添付されたCLI用ZIPをダウンロードし、全体を展開します。ソースコードのZIPやMOD JARとは別の配布物です。
2. 展開先には `bin/` と `lib/` があることを確認します。起動スクリプトだけを移動せず、両方を同じ配布フォルダー内に保ちます。
3. ターミナルで展開先のフォルダーへ移動し、次のコマンドでダンプを集計します。

Linux／macOS:

```sh
./bin/recipe-dump summary /path/to/recipes.json
```

Windows（PowerShell）:

```powershell
.\bin\recipe-dump.bat summary "C:\path\to\recipes.json"
```

Windowsのコマンドプロンプトでも `bin\recipe-dump.bat` を使えます。起動スクリプトがJavaを呼び出すので、`java -jar` やclasspathの指定は不要です。パスに空白が含まれる場合は引用符で囲みます。

以降の例の `recipe-dump` はLinux／macOSでは `./bin/recipe-dump`、Windowsでは `.\bin\recipe-dump.bat` に置き換えてください。`bin/` をPATHへ追加した場合は、そのまま実行できます。

## MOD開発プロジェクトでLLMから使う

CLI ZIPには利用者向けの `skills/recipe-dump-cli/` を同梱します。[recipe-dump-cli skill](../../skills/recipe-dump-cli/SKILL.md) は検索・差分・終了コードと結果の判断を扱い、このツール自身の開発用skillsには依存しません。

1. ZIPの `skills/recipe-dump-cli/` を、導入先プロジェクトの `.agents/skills/recipe-dump-cli/` へフォルダーごとコピーします。ソースから利用する場合もrepo直下の同じフォルダーをコピーできます。
2. CLI本体は `bin/` と `lib/` を揃えて別途配置し、Java 21を用意します。skillだけではCLIは実行できません。
3. 導入先の `AGENTS.md` にskillの入口、CLI起動スクリプトと対象ダンプのパス、収集構成・再収集手順の参照先を書きます。ログ・ダンプ等は導入先の生成物用ディレクトリへ置きます。

例えば導入先の配置に合わせて、次のように記します。

```markdown
レシピ・材料・出力・加工装置の調査と変更前後の比較には
.agents/skills/recipe-dump-cli/SKILL.md を読む。
CLI: .local/tools/recipe-dump/bin/recipe-dump
対象ダンプ: .local/recipe-dumps/current.json
収集構成と再収集手順: docs/recipe-dump.md
```

最後の手順書には対象MOD・依存・設定・データパック、収集サーバーの場所と起動方法を記します。MOD・設定等を変更したら新しい出力先へ再収集し、[保存成功の確認](mod.md#今回の収集が成功したか確認する)を行います。現在の構成と一致するダンプであることを確認してからLLMに検索を依頼してください。

## よく使うコマンド

```sh
recipe-dump summary recipes.json
recipe-dump csv recipes.json > recipes.csv
recipe-dump diff before.json after.json
recipe-dump recipes recipes.json --id minecraft:iron_ingot_from_smelting_iron_ore
recipe-dump recipes recipes.json --type minecraft:smelting
recipe-dump recipes recipes.json --namespace create --type create:crushing
recipe-dump recipes recipes.json --station create:crushing_wheel --produces minecraft:amethyst_shard
recipe-dump recipes recipes.json --uses minecraft:raw_iron --produces minecraft:iron_ingot
recipe-dump stations recipes.json
recipe-dump items recipes.json
recipe-dump items recipes.json --namespace create
recipe-dump items recipes.json --tag minecraft:planks --namespace minecraft
```

- `summary`: レシピ数、アイテムタグ数、RecipeType別件数。
- `csv`: ID・型・シリアライザー・codecのJSON。独自形式はdata列に保持。
- `diff`: レシピID単位の追加・削除・内容変更。JSONのobject key順序は無視。タグ・構成変更そのものの比較は未実装。
- `recipes`: レシピ一覧。ID・型・namespace・装置・材料・出力で絞り込み、組み合わせはAND条件。フィルターなしなら全件。
- `stations`: 対象ダンプのRecipeTypeに対応する既知の装置別名一覧。
- `items`: 全登録アイテムID一覧。任意の `--tag` と `--namespace` をANDで指定できます。タグ検索は旧ダンプでも利用でき、全件一覧は新しい収集MODで再収集した `items` が必要です。

## オプションと出力形式

検索値は完全一致で、namespaceの省略補完や部分一致は行いません。`--namespace` は検索対象のレシピIDまたはアイテムIDのコロン前の文字列に適用し、実際の提供元MOD／JARや材料・出力の所属は判定しません。
装置は `recipes dump.json --station minecraft:furnace` のように完全修飾アイテムIDで指定し、`stations dump.json` で対象ダンプにあるRecipeTypeに対応する既知の装置別名を一覧できます。材料・出力は `--uses minecraft:iron_ingot`／`--produces minecraft:iron_ingot` を任意のAND条件として指定します。

`recipes` のJSON結果は `recipes` 配列と `count`、`items` は `items` 配列・`count` と、指定時のみ `tag`・`namespace` を持ちます。ID順で出力し、タグ内の重複アイテムは除去します。未知のID・type・namespace・タグによる0件検索も終了コード0です。材料検索は対応serializerのJSON形式を解析し、Minecraft／MODの版情報では対象を制限せず、未知形式を含む部分検索はJSONをstdoutへ返して終了コード2になります。analysis／diagnostics／evidenceで未判定件数と根拠を確認できます。
オプションは `--name value` 形式のみ。同じオプションの重複、未知のオプション、値不足・空値、余分な位置引数は拒否します。診断はstderrへ出します。

不完全なダンプ、未対応のschema、重複ID、読み込み失敗、使用法の誤りではCLIが非ゼロ終了します。競合やゲーム内の成立可否を自動判定するコマンドはありません。

## 終了コードと結果の確認

| 終了コード | 意味 |
| --- | --- |
| `0` | コマンド成功。検索結果が0件の場合も含みます。 |
| `1` | 使用法エラー、読み込み失敗、不完全・未対応・不正なダンプなど。stderrを確認します。 |
| `2` | 材料・出力検索に未判定のレシピが残る部分検索。stdoutのJSON結果は利用できますが、全件判定できた結果ではありません。 |

終了コード2のときは結果の `analysis`・`diagnostics`・`evidence` を確認します。0件であっても、未判定が残っていれば「該当レシピなし」と断定できません。`diff` は差分があること自体をエラーとしません。

Linux／macOSでは実行直後の `$?`、PowerShellでは `$LASTEXITCODE`、コマンドプロンプトでは `%ERRORLEVEL%` で終了コードを確認できます。

## 検索の範囲と限界

材料・出力は完全修飾アイテムIDの候補検索です。全材料の充足・必要数量・ゲーム内の作成可否は判定しません。出力が読めれば独自材料があっても出力方向だけの検索は可能です。空タグは不一致、参照タグ欠落は不明です。動的出力・独自材料は診断に残します。

## 材料JSONの解析形式と検証済み構成

CLIはダンプJSONを調べるインターフェースです。以下の版は検証に使った構成で、検索対象を限定する条件ではありません。`minecraft`／`mods.version`が異なる・欠ける場合も、同じserializerの読めるJSON形式を検索します。未知serializerは未対応、必要フィールドの欠落や不正形は未解決として診断します。他版のゲーム動作や、同じ形で意味が変わった形式の互換性を保証するものではありません。

| 検証済みMOD（配布JAR） | 実測mods.version | 対応serializer（namespace省略） | 装置ID（namespace省略） |
| --- | --- | --- | --- |
| Create 6.0.10 | 6.0.10 | crushing, cutting, haunting, item_application, milling, pressing, sandpaper_polishing, splashing, deploying, mechanical_crafting | crushing_wheel, millstone, mechanical_saw, deployer |
| Mekanism 10.7.19.85 | 10.7.19 | crushing, enriching, combining, sawing | crusher, enrichment_chamber, precision_sawmill |
| Farmer's Delight 1.3.4 | 1.3.4 | cooking, cutting | cooking_pot, cutting_board |

バニラ装置はcrafting_table、furnace、blast_furnace、smoker、campfire／soul_campfire、stonecutter、smithing_table。対応表にない装置別名は終了コード1です（RecipeTypeが分かれば`--type`で直接検索できます）。既知の装置別名は版情報に関係なく検索でき、対応するレシピがなければ正常0件です。装置対応表はRecipeTypeへの検索入口で、装置の全能力・代替・複合設備は保証しません。Mekanismのmods.versionはbuild番号を持たず、10.7.19.85以外のJARの同一性・互換性は確認できません。

NeoForgeのcompound／intersection／difference材料を三値で評価します。成分条件、block tag、未知の独自材料、FDのitem_abilityは不明を維持します。Createの液体処理・sequenced assembly、Mekanismの化学物質／液体形式、表外の動的クラフトは未対応です。

出力のcountは記載個数です。chanceKindはCreateのper-item-roll、Mekanism副産物のsecondary-output、FD cuttingのbase-chanceを区別し、FDはconditions=fortune-and-server-configも付けます。FDの基礎chance=0も設定・fortuneで変わる候補として扱います。Create deployingの保持材料はretained-input、FD cuttingの工具はtoolとしてusesに含め、FD cookingのserving containerは含めません。

## うまく動かない場合

- Javaが見つからない場合は、Java 21のインストールと `JAVA_HOME` を確認します。
- Linux／macOSで起動スクリプトの実行権限がない場合は、`sh ./bin/recipe-dump summary recipes.json` でも実行できます。
- JARを読み込めない場合は、ZIPを全体として展開し、`bin/` と `lib/` の配置を確認します。
- ダンプの読み込みに失敗する場合は、ファイルパスとstderrを確認します。不完全なダンプは [収集MODの手順](mod.md#今回の収集が成功したか確認する) に沿って再収集します。
- 装置別名が未対応の場合は `stations` で確認し、RecipeTypeが分かれば `--type` で直接検索します。

ダンプの仕様は [ダンプ形式](../reference/dump-format.md)、検索結果JSON・評価規則は [CLI検索・差分仕様](../reference/cli-search.md)、実データでの検証範囲は [検証記録](../verification/README.md) を参照してください。
