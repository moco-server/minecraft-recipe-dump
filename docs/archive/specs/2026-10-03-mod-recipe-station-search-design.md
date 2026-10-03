# 第4段階: 初期MOD形式への対応と装置検索

> 履歴資料: この作業は完了済み。本文は当時の設計・計画であり、現行仕様や実行指示ではない。現在の契約は [ダンプ形式](../../reference/dump-format.md) と [CLI検索・差分仕様](../../reference/cli-search.md)、実測は [検証記録](../../verification/README.md) を参照する。

> 2026-10-03追記: ユーザー指示により、本文の固定版gateと版条件による装置一覧は撤回した。現在の検索はserializerとJSON形式で判定し、版情報は検索可否に使わない。stationsはダンプ内のRecipeTypeに対応する既知別名を列挙する。現在の契約は[ダンプ形式](../../reference/dump-format.md)と[実装判断](../../design/decisions.md)を参照する。


状態: 承認済み。実装・受け入れ検証済み。全体レビューと指摘修正済み。

## 目的・採用範囲

[全体方針](2026-10-03-cli-search-design.md)の第4段階として、第3段階の解析へMOD固有の確認済み形式を追加し、装置アイテムIDからRecipeTypeで検索できるようにする。
初期MODは実snapshotのあるCreate 6.0.10、Mekanism 10.7.19.85、Farmer's Delight 1.3.4とする。MOD固有形式の全件対応は宣言しない。
core／cliからMOD個別APIを参照せず、保存済みJSONと構成メタデータを解析する。
未知MODや違う版を似たJSON形だけで自動対応する方式は採用しない。追加対応はserializerと版を明示する。

## 初期adapterの対応表

以下のserializerを名前単位で登録し、必要方向のみ解析する。minecraftは1.21.1、modsに対象MODの対応するversionがある場合に限ってMOD adapterを使用する。
版欠落／不一致はunsupported_version。別namespaceのレシピIDもserializerと版が一致すれば対象とする。
選択に使うmods.versionはcreate=6.0.10、farmersdelight=1.3.4、mekanism=10.7.19。Mekanismの配布JARは10.7.19.85だが実dumpのversionは10.7.19で、build番号を区別できない。
この一致は形式adapterの選択条件であり、同じversionを報告する別JARの同一性を保証しない。確認済みのJARは10.7.19.85に限定して記録し、同じヘッダーでも未知のJSON形は診断する。

| MOD／serializer | 入力 | アイテム出力 |
| --- | --- | --- |
| create:crushing／cutting／haunting／item_application／milling／pressing／sandpaper_polishing／splashing | ingredients | results |
| create:deploying | ingredients（保持するheld itemも入力候補） | results |
| create:mechanical_crafting | pattern／key | result |
| mekanism:crushing／enriching | input | output |
| mekanism:combining | main_input／extra_input | output |
| mekanism:sawing | input | main_output／secondary_output |
| farmersdelight:cooking | ingredients | result |
| farmersdelight:cutting | ingredients／tool | result配列のitem |

Createのfluidを扱うmixing／compacting／filling／emptying、sequenced assembly、Mekanismの化学物質・液体を含む表外serializer、動的クラフトはこの初期adapterでは未対応とする。
これらは第3段階の診断件数に残す。液体・化学物質のoutput.idをアイテムIDとみなさない。

CreateのresultsはItemStack形式のid／countと正のchanceを読み、chance省略は1。chanceが1未満の場合は個数ごとの確率判定、1以上はその判定が常に成功する形式として扱う。0以下・非有限値・型不正はinvalid_shape。
sequenced assemblyの重み付きchanceを確率と読み替えない。
Mekanismのinputはitem／tag／材料の集合演算と正の整数countを扱う。
sawingは主出力と副出力の省略をそれぞれ出力なしとする。副出力がある場合はsecondary_chanceが必須で、有限値かつ0より大きく1以下、主出力がない場合は1未満でなければinvalid_shapeとする。副出力がない場合はchanceも省略され、内部既定値0のため副産物候補はない。両出力の省略や副出力なしのchance指定もinvalid_shapeとする。
Farmer's Delightのcuttingはresultのitemとchanceを読み、toolはrole=toolとして入力検索に含める。
cuttingのchance省略は1だが、実際の判定にはfortuneとサーバー設定のbonusも入る。記載された出力を候補として返し、根拠のchanceKind=base-chanceとconditions=fortune-and-server-configを明示する。0を無条件の出力不可と断定せず、非有限値・型不正は診断する。
cookingのserving用containerは調理材料と区別し、今回のusesには含めない。生JSONに保持し、使用法でこの境界を説明する。
Create deployingのkeep_held_item=trueの場合、2番目の入力の根拠をrole=retained-inputとし、消費材料と区別する。
出力根拠にはcountとchanceを付け、chanceKindでCreateのper-item-roll、Mekanismのsecondary-output、Farmer's Delightのbase-chanceを区別する。countは記載個数で、確率出力の保証数量ではない。recipe行の生JSONを保持し、レシピのゲーム内成立可否・必要電力・速度等は評価しない。

## 複合材料

第3段階のitem／tag／配列ORに加え、確認したNeoForgeのcompound（OR）・intersection（AND）・difference（baseからsubtractedを除く）を評価する。
typeとその型の正確なフィールドを見て分岐し、子ノードを再帰評価する。未知の子を空集合と扱わない。
三値評価を維持し、differenceでsubtractedが不明ならbaseが一致しても不明とする。
data component条件、block tag、未知の独自材料は引き続き未解析とする。
Farmer's Delightのitem_abilityはダンプに全アイテムの能力がないため未解析とする。別のOR枝のtagで一致が証明できれば一致を返せる。
実装前に対象MOD版の配布JARのcodec・sourceと実snapshotの該当行を突き合わせ、表のfield／chance／保持入力の意味を確認する。確認に合わない形式は推測対応せず診断する。

## 装置検索

```sh
recipe-dump recipes dump.json --station minecraft:furnace
recipe-dump recipes dump.json --station create:crushing_wheel --produces minecraft:amethyst_shard
recipe-dump stations dump.json
```

stationは装置アイテムの完全修飾ID。日本語表示名や曖昧な文字列からの補完は初期対象にしない。
既存id／type／namespace・uses／producesとAND条件で組み合わせる。対応する複数typeはORで選び、その後に他の条件を適用する。
未知の装置・未対応のMOD版は対応表にないと診断し、終了コード1。既知装置の該当レシピなしは正常0件。
装置条件だけの検索はcodecを解析せず、動的レシピもRecipeTypeが一致すれば列挙する。

初期対応表:

| 装置ID | RecipeType |
| --- | --- |
| minecraft:crafting_table | minecraft:crafting |
| minecraft:furnace | minecraft:smelting |
| minecraft:blast_furnace | minecraft:blasting |
| minecraft:smoker | minecraft:smoking |
| minecraft:campfire／soul_campfire | minecraft:campfire_cooking |
| minecraft:stonecutter | minecraft:stonecutting |
| minecraft:smithing_table | minecraft:smithing |
| create:crushing_wheel | create:crushing |
| create:millstone | create:milling |
| create:mechanical_saw | create:cutting |
| create:deployer | create:deploying |
| mekanism:crusher | mekanism:crushing |
| mekanism:enrichment_chamber | mekanism:enriching |
| mekanism:precision_sawmill | mekanism:sawing |
| farmersdelight:cooking_pot | farmersdelight:cooking |
| farmersdelight:cutting_board | farmersdelight:cutting |

この対応表は列挙したRecipeTypeへの検索入口であり、装置の全能力・他装置による代替・複合設備の必要性を網羅する保証ではない。
MekanismのfactoryやCreateの補助設備等をIDの接頭辞だけで追加しない。
対応表はcore内の版付き定義とし、検索ロジックから分離する。外部ファイルからの任意上書きや推測は今回導入しない。
stationsはdump.minecraft／modsの版に対応する装置をID順に返す。結果形は`{"stations": [{"id": "...", "types": ["..."]}], "count": N}`。
旧ダンプでも構成メタデータがそろえば使用できる。minecraft版欠落・非対応版はstations／station検索のエラーとする。
recipesの装置検索結果には`station`と対応する`stationTypes`を付け、選択根拠を確認できるようにする。

## 境界・受け入れ

- coreにMOD別の小さなadapterと版付き装置表を置く。CLIは追加オプション・stations・既存の0／1／2終了コードを担当する。
- neoforgeとschemaにMOD個別対応を追加しない。MOD APIへのruntime依存を追加しない。
- 表の各serializerについて、固定版配布JAR由来の実codec JSONで入力・出力・副産物・chance・再使用入力・toolをテストする。
- ingredientのOR／intersection／differenceと不明子、item_ability、成分条件、版違い、fluid／chemicalとのID衝突、sequenced chanceの未対応を検証する。
- 装置ID完全一致、未知装置、版条件、複数typeと他条件のAND、0件、stationsの順序を確認する。
- 生JSON・明示null・第1〜3段階の結果契約を維持し、未対応行の件数と根拠を隠さない。
- 必須ビルド・JAR検査・GameTest・Nodeテストと配布CLIの実データ照合を実施し、serializer別の解析件数・不明件数を記録する。
- 対応MOD／版／serializer／装置と未対応範囲をREADME／design／検索契約へ記載する。移植性は未検証の版へ広げない。

## 根拠

実snapshotはCreate 6.0.10、Mekanism 10.7.19.85、Farmer's Delight 1.3.4を含む4,749件のダンプ。各serializerの実JSON形と件数を確認した。
固定版の配布JARでProcessingOutput、ChanceResult、SawmillRecipeSerializer、BasicSawmillRecipeのbytecodeも確認し、chanceの既定値・範囲・判定の違いを設計へ反映した。調査出力は.localに保存する。
NeoForge 21.1.252の生成ソースでCompoundIngredient、DifferenceIngredient、IntersectionIngredient、DataComponentIngredientを確認した。
[NeoForge Ingredients](https://docs.neoforged.net/docs/1.21.1/resources/server/recipes/ingredients/)、[Create作者ソース](https://github.com/Creators-of-Create/Create/blob/mc1.21.1/dev/src/main/java/com/simibubi/create/content/processing/recipe/ProcessingRecipe.java)、[Mekanism作者ソース](https://github.com/mekanism/Mekanism/blob/1.21.x/src/main/java/mekanism/common/recipe/serializer/MekanismRecipeSerializer.java)は意味の確認に使うが、可変branchを固定版対応の証拠にしない。実装時には固定版JARまたは対応する固定commitと照合する。
