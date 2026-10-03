# ダンプ形式（schemaVersion 1）

JSON object。初期Java実証のschema 1もCLIで読み込める。読み込み診断がない古い出力も診断成功とは扱わない。

| フィールド | 内容 |
| --- | --- |
| schemaVersion | 整数1 |
| complete | 列挙したsnapshotのencode/decode成功。入力読み込み成功ではない |
| errors | 書き出し失敗のidとmessageの配列 |
| recipes | id、RecipeTypeのtype、serializer、codec JSONのdataを持つ配列。失敗行にはerror |
| itemTags | タグIDから解決済みアイテムID配列へのobject |
| minecraft | Minecraft版 |
| mods | mod idとversionの配列 |
| selectedPacks | 選択データパックIDの順序付き配列 |
| capturePhase | first-server-tick-post-lowest |
| runId | 移植版で追加した実行識別子。保存ログにも記録 |
| loadDiagnostics | 移植版ではnot-tracked。入力の解析失敗・除外理由を捕捉していない |

完全なsnapshot以外はCLIが拒否する。id/type/serializer/dataの欠落や重複IDも拒否する。未知の追加フィールドは保持できる。CSVのdata列は再シリアライズしたJSONで、入力原文や条件の履歴ではない。

## 検索契約

検索追加によるschemaVersion 1の変更はない。`recipes` コマンドはID・type・レシピIDのnamespaceを完全一致のAND条件で絞る。明示的なnullや未知の追加フィールドを含む行全体を保持し、ID昇順で `recipes` と `count` を返す。入力snapshotを変更しない。

`items --tag` は `itemTags` のキーを完全一致で参照し、解決済みIDを重複除去して昇順で `tag`・`items`・`count` を返す。参照対象タグの値が文字列配列でなければ拒否する。無関係なタグの値はこの検索では検証しない。未登録タグは空配列と件数0を返す。

未知の検索値は正常な0件検索として終了コード0。材料・出力の解析、装置アイテムとRecipeTypeの対応は現在の検索契約に含まれない。検索結果もsnapshotの範囲に限り、入力読み込み成功やゲーム内の作成可否を保証しない。

## 差分と構成情報

差分はrecipesのIDとJSON内容を比較し、object key順序を無視、配列順序を維持する。タグが変わって同じレシピの成立範囲が変化しても、dataが同じならchangedに入らない。競合判定やゲーム上の意味の等価判定ではない。

MODのversionと選択pack順序を記録するが、JAR・設定・スクリプト・packのハッシュはダンプへ含めていない。namespaceから提供元JARを断定しない。

## 任意の登録アイテム一覧

`items` はschema 1の任意追加フィールドで、全登録アイテムの完全修飾IDを収集側が重複なし・昇順で保存する。minecraft:airも含む。decodeは配列順を問わず空配列を受理するが、null・誤型・重複・不正IDを拒否する。IDは`[a-z0-9_.-]+:[a-z0-9/._-]+`。旧ダンプの欠落は許容する。

`items` コマンドはtagなしなら登録一覧を要求し、欠落時は再収集を案内して終了コード1。tagありなら従来のタグ集合を使用し、任意namespaceをIDのコロン前への完全一致でAND適用する。結果は昇順のitemsとcount、指定時のみtag／namespaceを持つ。アイテム一覧の差分やsummary項目は追加しない。保存は独自JSONの明示nullを含めてatomic置換する。
