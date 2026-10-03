# CLI検索の設計案

状態: 設計承認済み。第1段階を実装。第2段階以降は未実装で、個別の詳細設計が必要。

## 目的

専用サーバーが保存したsnapshotから、ゲームを起動せず、レシピ・アイテム・タグの関係を調べられるようにする。
利用者が提案した検索は、装置、材料アイテム、出力アイテム、レシピID、MOD ID、アイテムタグの6種類。
MOD IDによる検索はIDのnamespaceによる検索として提供する。

初期対応はMinecraft 1.21.1／NeoForge 21.1.252。現在のcore／neoforge／cliの境界を維持する。
この文書は将来の設計案であり、現在の実装・出力契約は[design](../../design.md)と[dump-format](../../dump-format.md)を参照する。

## 方針と段階

採用案は、現在のダンプだけで確実に検索できる機能から段階的に追加する方式。

1. レシピID・RecipeType・レシピIDのnamespace・アイテムタグによる検索。
2. 全登録アイテムIDの収集と、アイテムIDのnamespaceによる検索。
3. バニラの対応serializerに限定した材料・出力検索。
4. 必要性と実データを確認したMOD固有serializerへの対応、および装置名とRecipeTypeの対応。

生JSONの全文検索は実装が小さいが、材料・出力・触媒・表示用IDを区別できないため、材料・出力検索の根拠にはしない。
全MODを共通材料モデルへ変換する方式は対応範囲と保守負担が大きいため採用しない。
段階的なserializer対応は対象外の形式が残るが、その範囲を検索結果で明示できる。

実装計画は第1段階に限定する。第2段階以降は、以下の方向性を踏まえ、個別に詳細設計と受け入れ条件を確定する。

## 第1段階のCLI契約

既存のsummary／csv／diffは維持し、次を追加する。

```sh
recipe-dump recipes dump.json
recipe-dump recipes dump.json --id minecraft:iron_ingot_from_smelting_iron_ore
recipe-dump recipes dump.json --type minecraft:smelting
recipe-dump recipes dump.json --namespace create
recipe-dump items dump.json --tag minecraft:planks
```

- recipesはフィルターなしなら全レシピを返す。id／type／namespaceは任意に組み合わせ、AND条件とする。
- idとtypeとtagは完全一致。namespaceはIDの最初のコロンより前の文字列との完全一致。省略namespaceの補完・部分一致は行わない。
- namespaceはレシピIDに適用する。serializerや材料・出力アイテムのnamespaceには適用しない。
- 第1段階のitemsにはtagを必須とする。全アイテム一覧としては扱わない。
- 同じオプションの重複、未知のオプション、値不足、空の値、余分な位置引数は使用法エラーとする。
- stdoutはJSON、診断はstderrへ出す。結果はレシピID／アイテムIDの昇順とし、アイテムは重複を除く。
- recipesの結果は`{"recipes": [...], "count": N}`。各行はダンプのレシピ行を保持する。
- itemsの結果は`{"tag": "...", "items": [...], "count": N}`。
- 不明なレシピID・type・namespace・tagは正常な0件検索として扱い、終了コード0とする。
- 引数エラー、入力I/Oエラー、ダンプ検証エラーは既存CLIと同じ終了コード1とする。

ダンプのcompleteはsnapshotの書き出し成功であり、検索結果も取得時点のsnapshotの範囲に限る。
入力読み込み成功やRecipeManager外の加工の網羅性を新たに保証しない。

## 境界と検証

coreが絞り込み、タグ参照、結果生成を担当する。cliは引数・ファイル・表示・終了コードを担当する。
第1段階ではneoforge、収集タイミング、停止動作、schemaVersionを変更しない。
items検索時は参照対象タグの値が文字列配列であることを検証し、不正な値はダンプ検証エラーとして扱う。
未知の追加フィールドは現在の読み込み方針に従う。

第1段階の受け入れ条件:

- ID完全一致、type一致、namespace境界、AND条件、0件、安定した並び順をcoreのテストで確認する。
- タグの解決済みID参照、重複除去、未知のタグ、参照対象タグの不正値をテストする。
- CLIの各コマンド・JSON出力・引数拒否・stderr・終了コードをテストする。
- 既存summary／csv／diff、不完全ダンプ拒否の回帰を確認する。
- 通常ビルド・配布・GameTest・scriptの必須検証を[AGENTS.md](../../../AGENTS.md)に従って実施する。
- 保存済みの実ダンプをCLIで検索し、件数と結果を元データと照合する。
- CLI使用法と検索契約を文書へ追記し、検証結果と未検証範囲をverificationへ記録する。

## 後続段階の設計条件

### 全アイテム一覧

レシピやタグに出現しないアイテムも検索できるよう、登録済みアイテムIDをダンプへ追加する。
表示名・翻訳・提供元JARの推定は初期対象に含めない。
旧ダンプの一覧欠落を空集合と扱わず、全アイテム検索には必要情報がないと診断する。
追加フィールドの形式、検証、収集失敗時の扱い、schemaVersion維持可否を実装前に確定する。

### 材料・出力検索

`recipes --uses <item-id>`と`recipes --produces <item-id>`を追加する方向とする。
codec JSONを保持したまま、対象版・serializerごとの明示的な解析をcoreへ置く。
タグ材料は同じダンプのitemTagsから解決する。未知の形式や取得不能な情報を不一致と断定しない。

対応済み、未対応、対応対象だが解析できない行を区別し、評価対象件数と評価不能件数を結果に付ける。
型やnamespaceなどのメタデータ条件で絞ってから材料・出力を評価し、件数の分母を明確にする。
0件でも評価不能な行がある場合は検索が部分的であると表示する。
snapshotのcompleteと検索の解析範囲は別の概念として扱う。

出力検索はそのアイテムが出力候補にあることを示す。資源や設備を含めた実際の作成可否は判定しない。
確率出力、複数出力、触媒、コンテナ返却、独自材料条件の意味はserializerごとに契約を定める。
対応serializerの一覧と実データの根拠を確定してから実装する。

### 装置検索

まずRecipeTypeによる検索を提供する。装置アイテムを材料として使う検索とは区別する。
装置名検索は、確認済みの装置とRecipeTypeの対応表で実現する方向とする。
一対多・多対一の対応を認め、IDやJSONから対応を自動推定しない。
対応表の管理方法とMOD別の対応範囲は、対象を決めた段階で設計する。

## 対象外

実際の提供元MOD／JARの特定、RecipeManager外の加工、完全な競合判定、依存素材の再帰探索、GUI、永続検索DB、Minecraftの自動起動は今回の設計対象に含めない。
