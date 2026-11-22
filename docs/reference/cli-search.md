# CLI検索・差分仕様

現在の検索結果JSON、評価規則、差分と終了コードの契約を記す。操作手順は [CLI利用ガイド](../guides/cli.md)、入力JSONの契約は [ダンプ形式](dump-format.md) を参照する。

## 引数と終了コード

検索オプションは `--name value` 形式。未知・重複オプション、値の欠落・空文字・`--` で始まる値、余分な位置引数はファイルを読む前に拒否する。`--uses`、`--produces`、`--station` の値も読み込み前に完全修飾アイテムIDの構文を検証する。

| 終了コード | 結果 |
| --- | --- |
| 0 | コマンド成功。0件検索や差分がある場合も含む |
| 1 | 引数・I/O・ダンプ検証などのエラー。stderrへ診断を出す |
| 2 | 材料・出力検索の未判定行あり。stdoutへ有効なJSONと診断を返す |

## レシピ・タグ検索

`recipes` はレシピID・RecipeType・レシピIDの最初のコロンより前のnamespaceを完全一致のAND条件で絞る。フィルター省略時は全件を返す。結果はID昇順の `recipes` と `count` を持ち、明示的なnullや未知の追加フィールドを含む元のレシピ行全体を保持する。

`items --tag` は `itemTags` のキーを完全一致で参照し、解決済みIDを重複除去して昇順で `tag`・`items`・`count` を返す。参照対象タグの値が文字列配列でなければ拒否する。無関係なタグの値はこの検索では検証しない。未登録タグは空配列と件数0を返す。

未知のID・type・namespace・タグは正常な0件検索として終了コード0。結果は取得時点のsnapshotに限る。

## 登録アイテム検索

`items` コマンドはtagなしなら登録一覧を要求し、欠落時は再収集を案内して終了コード1。tagありなら従来のタグ集合を使用し、任意namespaceをIDのコロン前への完全一致でAND適用する。結果は昇順のitemsとcount、指定時のみtag／namespaceを持つ。アイテム一覧の差分やsummary項目は追加しない。

## CSV

CSVのdata列は再シリアライズしたJSONで、入力原文や条件の履歴ではない。

## 差分

差分はrecipesのIDとレシピ行全体を比較し、objectのキー順序を無視し、配列順序を維持する。タグが変わって同じレシピの成立範囲が変化しても、レシピ行が同じなら `changed` には含めない。競合やゲーム上の意味の等価性は判定の対象外。

## 材料・出力検索

`recipes --uses <item-id> --produces <item-id>` は任意のAND条件。レシピID・type・namespace・装置で絞った候補について、指定した入力方向・出力方向だけを解析する。検索値は完全一致で、`items` 一覧がない旧ダンプでも検索できる。ID構文は [ダンプ形式](dump-format.md#任意の登録アイテム一覧) を参照する。

`--uses` は入力スロット・工具などのいずれかが指定IDを受け付ける候補を探す。`--produces` は明示されたアイテム出力の候補を探す。全入力の充足、必要数量、容器返却、副作用、ゲーム内での作成可否は判定の対象外。

### 材料の評価

- 単純材料は `item` または `tag` のどちらか一方に完全修飾IDを取る。タグは同じダンプの `itemTags` で解決する。空タグは不一致、参照先の欠落は `missing_tag`、タグ値が完全修飾アイテムIDの文字列配列でなければ `invalid_shape`。
- 材料ノードの配列はORで評価し、空配列は不一致。レシピが要求する `ingredients` の入力スロット一覧が空なら `invalid_shape`。
- `neoforge:compound` は `children`（省略時は `ingredients`）のOR、`neoforge:intersection` は `children` のAND。どちらも空でない配列を取る。`neoforge:difference` は `base` に一致し、`subtracted` に不一致のものを採る。
- ORは一致が一つでもあれば一致、すべて不一致なら不一致、残りは不明。ANDは不一致が一つでもあれば不一致、すべて一致なら一致、残りは不明。`--uses` と `--produces` の組み合わせにもこのAND規則を適用する。たとえば差集合で `base` が一致し `subtracted` が不明なら、結果も不明になる。
- 成分条件、block tag、Farmer's Delightの `item_ability` など、対応表にない独自材料は `unsupported_ingredient`。別のOR枝で一致を確定できる場合は、一致した枝を根拠として返す。

### 対応シリアライザー

レシピ行の `serializer` で解析形式を選び、下表のフィールドを `data` から読む。レシピIDのnamespaceや `minecraft`・`mods.version` にかかわらず、同じシリアライザーの同じJSON形式を評価する。実測に使用したバージョンは利用ガイドに記載する。[CLI利用ガイドの検証済み構成](../guides/cli.md#材料jsonの解析形式と検証済み構成)

| シリアライザー | `--uses` が読むフィールド | `--produces` が読むフィールド |
| --- | --- | --- |
| `minecraft:crafting_shaped` | `pattern` と `key` | `result` |
| `minecraft:crafting_shapeless` | `ingredients` | `result` |
| `minecraft:smelting`、`minecraft:blasting`、`minecraft:smoking`、`minecraft:campfire_cooking`、`minecraft:stonecutting` | `ingredient` | `result` |
| `minecraft:smithing_transform` | `template`、`base`、`addition` | `result` |
| `minecraft:smithing_trim` | `template`、`base`、`addition` | 未対応（`unsupported_direction`） |
| `create:crushing`、`create:cutting`、`create:haunting`、`create:item_application`、`create:milling`、`create:pressing`、`create:sandpaper_polishing`、`create:splashing` | `ingredients` | `results[]` |
| `create:deploying` | `ingredients`、`keep_held_item` | `results[]` |
| `create:mechanical_crafting` | `pattern` と `key` | `result` |
| `mekanism:crushing`、`mekanism:enriching` | `input` | `output` |
| `mekanism:combining` | `main_input`、`extra_input` | `output` |
| `mekanism:sawing` | `input` | `main_output`、`secondary_output`、`secondary_chance` |
| `farmersdelight:cooking` | `ingredients` | `result` |
| `farmersdelight:cutting` | `ingredients`、`tool` | `result[].item` と `result[].chance` |

表外のシリアライザーは `unsupported_serializer`。特殊クラフト、Createの液体処理・sequenced assembly、Mekanismの液体・化学物質形式などが該当する。評価に必要なフィールドの欠落や誤型は `invalid_shape`。

- `pattern` は長さの等しい空でない文字列の配列で、スペース以外の記号を少なくとも1つ含む。実使用記号に対応する `key` がすべてそろった後に材料を評価する。使用記号に対応するキーの欠落は、別のキーが一致しても構造不正として扱う。
- Createの `deploying` は入力2つを要求する。`keep_held_item` を指定する場合は真偽値を取り、`true` のとき第2入力の一致を `retained-input` として返す。Mekanismの材料に `count` があれば正の整数を要求する。Farmer's Delightの `cutting` は工具を `tool` として含め、`cooking` の配膳用 `container` は材料検索の対象外。
- アイテム出力は完全修飾 `id` が必須のJSON object。`count` の指定値は正の整数で、省略時は1。出力の成分は生JSONに保持し、検索はアイテムIDに基づく。
- 固定MOD出力の根拠は `chance: 1` を持つ。Createの `results[]` は省略時1の `chance` を持ち、正の値でfloatに変換しても有限であることを要求する。根拠は `chanceKind: "per-item-roll"` で、1以上は個数ごとの判定が成功することを示す。
- Mekanismの `sawing` は主出力か副出力の少なくとも一方が必要。副出力があれば `secondary_chance` は0より大きく1以下で、主出力がない場合は1未満。副出力を省略する場合は `secondary_chance` も省略する。副出力の根拠は `chanceKind: "secondary-output"`。
- Farmer's Delightの `cutting` の `chance` は省略時1。floatに変換しても有限の基礎値を取り、0でも出力候補に含める。根拠は `chanceKind: "base-chance"` と `conditions: "fortune-and-server-config"` を持つ。根拠の `count` は宣言個数で、実際の排出量は条件による。

### 解析結果と根拠

`--uses` または `--produces` を指定したとき、レシピ一覧に `analysis`、`diagnostics`、`evidence` を追加する。

| `analysis` のキー | 意味 |
| --- | --- |
| `candidateCount` | ID・type・namespace・装置の条件で絞った件数 |
| `analyzedCount` | 指定条件の一致・不一致を判定できた件数。一致件数 `count` を含む |
| `unsupportedCount` | `unsupported_serializer` または `unsupported_direction` のみを理由に判定不能な件数 |
| `unresolvedCount` | `missing_tag`、`invalid_shape`、`unsupported_ingredient` などを理由に判定不能な件数 |
| `exhaustive` | 未判定が0件なら `true`。候補が0件の場合も `true` |

`candidateCount = analyzedCount + unsupportedCount + unresolvedCount`。両方の不明理由がある行は `unresolvedCount` に一度だけ数える。`exhaustive` は指定条件を候補全体で評価できたかを表し、`dump.complete` はsnapshotの書き出し結果を表す。

- `diagnostics` は未判定行の `id`・`serializer`・`code` と、該当箇所がある場合は `data` を起点とする `path`（例 `$.ingredient.tag`）をID・path・code順で返す。
- `evidence` は一致行ごとに `id` と指定方向の配列を返す。`uses` の要素は `item`・`path`・`role`（`ingredient`、`retained-input`、`tool`）と参照した場合の `tag` を持つ。`produces` の要素は `item`・`path`・`count` と、対象形式に応じた確率フィールドを持つ。

## 装置検索

`stations dump.json` はダンプにあるRecipeTypeに対応する既知の装置別名を、ID昇順の `stations` 配列（`id` と昇順の `types`）と `count` で返す。items／minecraft／modsが欠落する旧ダンプでも使用でき、レシピが空なら装置一覧も空になる。

`recipes --station <item-id>` は以下の対応表のRecipeTypeをORで選び、他の検索条件とANDで組み合わせる。結果に `station` と昇順の `stationTypes` を追加する。材料・出力条件を指定しなければ、動的レシピもRecipeTypeが一致すれば返す。未知の別名は終了コード1、既知の別名に該当レシピがない場合は0。RecipeTypeが分かっていれば `--type` で直接検索できる。

| 装置ID | RecipeType |
| --- | --- |
| `minecraft:crafting_table` | `minecraft:crafting` |
| `minecraft:furnace` | `minecraft:smelting` |
| `minecraft:blast_furnace` | `minecraft:blasting` |
| `minecraft:smoker` | `minecraft:smoking` |
| `minecraft:campfire`、`minecraft:soul_campfire` | `minecraft:campfire_cooking` |
| `minecraft:stonecutter` | `minecraft:stonecutting` |
| `minecraft:smithing_table` | `minecraft:smithing` |
| `create:crushing_wheel` | `create:crushing` |
| `create:millstone` | `create:milling` |
| `create:mechanical_saw` | `create:cutting` |
| `create:deployer` | `create:deploying` |
| `mekanism:crusher` | `mekanism:crushing` |
| `mekanism:enrichment_chamber` | `mekanism:enriching` |
| `mekanism:precision_sawmill` | `mekanism:sawing` |
| `farmersdelight:cooking_pot` | `farmersdelight:cooking` |
| `farmersdelight:cutting_board` | `farmersdelight:cutting` |

対応表は検索用の別名を定義する。装置の登録状況・全能力・代替装置・複合設備は判定の対象外。
