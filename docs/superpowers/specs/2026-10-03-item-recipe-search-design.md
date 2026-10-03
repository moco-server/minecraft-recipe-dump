# 第3段階: バニラ形式の材料・出力検索

状態: 承認済み。実装・受け入れ検証済み（docs/verification.md）。

## 目的と範囲

ダンプから指定アイテムを入力に使うレシピと、指定アイテムを出力候補に持つレシピを検索する。
[全体方針](2026-10-03-cli-search-design.md)の第3段階。最初はMinecraft 1.21.1の明示対応serializerのみを解析する。
MODのレシピIDでもバニラserializerなら対象に含める。動的クラフトやMOD固有serializerを、JSONにアイテムIDがあるだけで解析済みと扱わない。
第2段階のアイテム一覧は検索対象IDの全件確認に使えるが、材料・出力検索の実行には必須としない。

## 採用方式

登録済みcodecのdataを保存したまま、coreに版・serializerを限定した解析を追加する。JSON全文検索やMinecraft実行時依存は導入しない。
検索用の材料モデルをダンプへ追加する方式は形式変更と再収集が必要になるため採用しない。
ゲーム内でassembleを試す方式は、入力スタックや世界条件をCLIへ持ち込むため採用しない。
今回の検索はアイテムIDによる入力・出力候補の検索であり、必要数量・設備・世界条件を含む実際の作成可否の判定ではない。

## 対応serializer

| serializer | 入力参照 | 出力参照 |
| --- | --- | --- |
| minecraft:crafting_shaped | patternで使用するkeyの材料 | result |
| minecraft:crafting_shapeless | ingredients | result |
| minecraft:smelting | ingredient | result |
| minecraft:blasting | ingredient | result |
| minecraft:smoking | ingredient | result |
| minecraft:campfire_cooking | ingredient | result |
| minecraft:stonecutting | ingredient | result |
| minecraft:smithing_transform | template・base・addition | result |
| minecraft:smithing_trim | template・base・addition | 未対応（固定resultがない） |

特殊クラフト、decorated pot等の表にないserializerは未対応とする。明示対応した方向だけを評価する。
dump.minecraftが1.21.1以外または欠落の場合、材料・出力解析はunsupported_versionとして扱う。基本ID／type／namespace検索は引き続き利用できる。

## 材料の意味

- typeなしのitem／tagと、材料候補の配列（OR）に対応する。itemとtagの両方、型不正、必須値欠落はinvalid_shape。
- codecで許される空の材料配列は候補なしとして扱う。非空を要求するレシピの入力配列自体が空ならinvalid_shapeとする。
- tagは同じダンプのitemTagsを参照する。登録済みの空タグは候補なし、欠落した参照タグはmissing_tagとし、空集合と推測しない。
- 参照タグの値が文字列配列でなければinvalid_shape。無関係なタグは解析しない。
- 第3段階ではtype付きの独自材料はunsupported_ingredientとし、成分条件・block tagをアイテムタグへ読み替えない。
- --usesは入力スロットのいずれかに指定IDが候補として含まれることを意味する。数量や全入力の充足は判定しない。
- shapedはpatternの空白を除く実使用文字だけを見る。対応するkeyがない等の不正な形は診断する。

## 出力の意味

対象版のresultはItemStackのobjectで、idがアイテムID、count省略時は1。旧版の文字列resultを推測変換しない。
idの型・完全修飾ID、countが正の整数であることを検証する。結果のcomponentsは生JSONに保持するが、ID検索は成分による絞り込みを行わない。
動的な出力を空出力と扱わない。smithing_trimではusesだけなら入力を評価でき、producesを要求するとunsupported_directionになる。
容器返却や副作用で生成するアイテムは、明示されたレシピ出力とは別であり、この段階のproducesには含めない。

## CLI・結果・終了コード

```sh
recipe-dump recipes dump.json --uses minecraft:iron_ingot
recipe-dump recipes dump.json --produces minecraft:iron_ingot --type minecraft:smelting
recipe-dump recipes dump.json --uses minecraft:raw_iron --produces minecraft:iron_ingot
```

id／type／レシピnamespaceの条件を先に適用し、残った行のuses／producesをANDで評価する。
検索値は完全修飾アイテムIDの完全一致で、namespace補完を行わない。構文が不正なIDは引数エラーとする。
解析は入力・出力の要求された方向だけで行い、出力が読める行を独自材料の存在だけで出力検索から除外しない。

材料候補のORでは既知の一致があれば一致、全候補が既知の不一致なら不一致、それ以外は不明。
uses／producesのANDでは既知の不一致があれば全体は不一致、全条件が既知の一致なら一致、それ以外は不明とする。
「不一致の証拠」と「解釈不能」を区別し、不明の行を結果から黙って落とさない。

既存のrecipes配列（行全体・明示nullを保持）とcountに、材料・出力検索時だけ次を追加する。

```json
{
  "analysis": {
    "candidateCount": 10,
    "analyzedCount": 8,
    "unsupportedCount": 1,
    "unresolvedCount": 1,
    "exhaustive": false
  },
  "diagnostics": [
    {"id": "example:dynamic", "serializer": "example:special", "code": "unsupported_serializer"},
    {"id": "example:missing_tag", "serializer": "minecraft:smelting", "code": "missing_tag", "path": "$.ingredient.tag"}
  ],
  "evidence": [
    {"id": "example:matched", "uses": [{"item": "minecraft:raw_iron", "path": "$.ingredient", "role": "ingredient"}]}
  ]
}
```

candidateCountはメタデータ条件で残った件数。analyzedCountは指定検索条件の一致／不一致を判断できた件数で、countを含む。
unsupportedCountは未対応serializer／方向／版で全体の判断ができない件数。unresolvedCountは対応形式の未知材料・タグ欠落・不正形で判断できない件数。
candidateCount = analyzedCount + unsupportedCount + unresolvedCount。各行は一度だけ数える。unsupportedとunresolvedの双方がある行はunresolvedへ数える。
diagnosticsは不明の行のID・serializer・原因・該当JSON pathを保持し、ID・path・code順で安定出力する。
evidenceは一致した行の指定検索IDの根拠を保持する。usesは材料path・role・参照タグ（ある場合）、producesは出力path・countを含める。レシピそのものを書き換えない。

exhaustiveは不明行が0件の時trueで、dump.completeとは独立する。メタデータ条件後に0件ならtrue。
終了コードは0（検索を評価し切った、0件を含む）、1（引数・I/O・ダンプ検証失敗）、2（部分的な検索結果）とする。
部分検索でもJSONをstdoutへ返し、diagnosticsを結果に含める。通常の診断エラーのみstderrへ出す。
ID／type／namespaceだけの既存検索では追加解析も終了コード2も導入しない。

## 境界・受け入れ

coreに小さなserializer別解析と共通の材料候補評価を置く。cliは検索値・追加オプション・終了コードを扱う。
neoforgeやschemaを変更せず、新旧schema 1の保存済みダンプで解析できる。

- 実codecの出力を小さなfixtureにし、表の全serializerと要求方向をテストする。
- item／tag／OR、空タグと欠落タグ、未知材料、shapedの使用文字、未知版、特殊クラフト、方向別解析、ANDの不一致優先を確認する。
- 欠落・null・誤型・小数count・0以下countを診断し、解析不能を不一致と扱わない。
- partition式、根拠path、diagnostics順、0件、終了コード0／1／2、既存コマンド、未知フィールド・null保持を確認する。
- 必須ビルド・JAR検査・GameTest・Nodeテストを実施する。収集・停止の変更はない。
- 配布CLIで実snapshotのsmeltingやdiamond_block等の既知入力・出力を照合し、全件検索の未対応serializer件数も独立集計と照合する。
- README／design／検索契約／verificationを更新し、バニラ名のserializerでも独自材料による部分検索があり得ると明示する。

## 根拠

対象版の生成ソース内のShapedRecipe、ShapelessRecipe、SingleItemRecipe、Ingredientのcodecと、4,749件の実snapshotを確認した。
[NeoForge 1.21.1 Ingredients](https://docs.neoforged.net/docs/1.21.1/resources/server/recipes/ingredients/)ではtype付き独自材料と成分条件等を区別している。表示候補を無条件な材料集合へ変換しない。
