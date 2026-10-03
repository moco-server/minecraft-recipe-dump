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

完全なsnapshot以外はCLIが拒否する。id/type/serializer/dataの欠落や重複IDも拒否する。未知の追加フィールドは保持できる。

## 構成情報

MODのversionと選択pack順序を記録するが、JAR・設定・スクリプト・packのハッシュはダンプへ含めていない。namespaceから提供元JARを断定しない。

## 任意の登録アイテム一覧

`items` はschema 1の任意追加フィールドで、全登録アイテムの完全修飾IDを収集側が重複なし・昇順で保存する。minecraft:airも含む。decodeは配列順を問わず空配列を受理するが、null・誤型・重複・不正IDを拒否する。IDは`[a-z0-9_.-]+:[a-z0-9/._-]+`。旧ダンプの欠落は許容する。

保存は独自JSONの明示nullを含めてatomic置換する。

CLIの検索結果・差分・終了コードは [CLI検索・差分仕様](cli-search.md) を参照する。
