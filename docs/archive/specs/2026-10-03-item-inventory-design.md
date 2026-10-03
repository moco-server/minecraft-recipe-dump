# 第2段階: 全登録アイテムの収集と検索

> 履歴資料: この作業は完了済み。本文は当時の設計・計画であり、現行仕様や実行指示ではない。現在の契約は [ダンプ形式](../../reference/dump-format.md) と [CLI検索・差分仕様](../../reference/cli-search.md)、実測は [検証記録](../../verification/README.md) を参照する。

状態: 承認済み。実装・受け入れ検証済み（docs/verification/records/2026-10-03-development.md）。

## 目的と範囲

レシピやタグに現れないアイテムも含め、ダンプ時点の全登録アイテムIDをゲーム外で一覧・namespace検索できるようにする。
[全体方針](2026-10-03-cli-search-design.md)の第2段階。材料・出力・装置名検索は別の設計書で扱う。
Minecraft 1.21.1／NeoForge 21.1.252／Java 21を維持し、core／cliにゲーム型や新依存を導入しない。

## 採用方式と互換性

schemaVersion 1に任意の追加フィールド`items`を加える。値は全登録アイテムの完全修飾IDの文字列配列で、収集側は重複なし・昇順で保存する。minecraft:airも登録アイテムとして含める。
既存schema 1の必須フィールド・意味を変更せず、旧CLIが未知の追加フィールドを許容するため、schemaVersionは維持する。
タグやレシピの参照IDの合成では、未参照アイテムを取りこぼすので一覧の代替にしない。
必須フィールドへ変更するschema 2は旧ダンプの既存機能まで移行が必要になるため、今回は採用しない。

新CLIはitemsフィールドが存在する場合、配列であること、全要素が空でない完全修飾アイテムIDであること、重複がないことをdecode時に検証する。入力の配列順序は受理するが検索出力は昇順とする。
IDの許容文字は対象版ResourceLocationに合わせ、namespaceは`[a-z0-9_.-]+`、pathは`[a-z0-9/._-]+`とする。
itemsが欠落した旧ダンプもdecodeでき、summary／csv／diff／recipes／タグ検索を維持する。欠落を空一覧として扱わない。
itemsが明示的にnullまたは不正なら、欠落と区別して検証エラーとする。
空配列は形式として受理する。実際のMinecraft収集ではGameTestでair等の存在を確認する。

## 収集・保存・失敗

SnapshotCollectorが同じサーバースレッド・同じsnapshot取得タイミングでBuiltInRegistries.ITEMのkeySetを列挙する。
MOD個別APIを使わず、アイテム名・翻訳・提供元JAR・アイコンは追加しない。
アイテム一覧の収集に失敗した場合はcollect全体を失敗させ、既存のRECIPE_DUMP_FAILEDとhalt(false)へ戻す。空一覧やitems欠落で成功を装わない。
collectが完了する前に保存・既存出力置換をしない。各レシピのcodec例外は従来どおりID付きerrorsに保持する。
保存はatomic置換を維持し、独自codecの明示的なnullも保持するGson設定を使う。保存用の回帰テストで確認する。
有効化条件、client／通常GameTestで自動停止しない条件、JVM終了コードと保存成功の区別は維持する。

## CLI契約

```sh
recipe-dump items dump.json
recipe-dump items dump.json --namespace create
recipe-dump items dump.json --tag minecraft:planks --namespace create
```

- itemsのtag必須制約を解除し、任意のtag／namespaceをANDで組み合わせる。
- tagありの場合は解決済みタグID集合を検索する。itemsフィールドを要求しないため、旧ダンプでもtag＋namespaceが動作する。
- tagなしの場合はitemsフィールドを検索する。旧ダンプの欠落はstderrで再収集が必要と診断し、終了コード1とする。
- namespaceはアイテムIDの最初のコロン前との完全一致。提供元MODの判定ではない。
- 未知のtag／namespaceは0件・終了コード0。重複除去とID昇順を維持する。
- 結果は`{"items": [...], "count": N}`。tag指定時は既存の`tag`を、namespace指定時は`namespace`を付ける。既存items --tagの結果形を維持する。
- 未知・重複オプション、値不足・空値、余分な引数はI/O前に拒否する。
- summaryの既存出力、recipesの結果形、レシピ単位のdiffを変更しない。itemsの差分は追加しない。

## 境界と受け入れ

neoforgeはレジストリ列挙、coreは追加フィールド検証とアイテム検索、cliは引数・表示・終了コードを担当する。
汎用レジストリ収集基盤や永続インデックスは作らない。

- core／CLI: 新旧ダンプ、items不正値・null・重複・無効ID、0件、並び順、タグ＋namespace、旧コマンド回帰、保存時のnull保持を検証する。
- GameTest: minecraft:air・diamond等が一覧に存在し、登録済みキー集合と一覧が一致することを確認。通常のレシピlookup・assembleも維持する。
- 必須のcheck／neoforge build／verifyModJar／cli distZip、GameTest結果検査、Nodeテストを実施する。
- 新規検証環境で通常配布JARを起動し、Create 6.0.10、Mekanism 10.7.19.85、Farmer's Delight 1.3.4入りの一覧・タグ・レシピを取得する。保存済みワールドは使用しない。
- 有効化時の出力と正常停止、未指定時の通常稼働、既存出力置換、保存失敗を確認する。独自レシピと上書き・除外の既存検証も新形式で再確認する。
- 配布CLIで新ダンプの全件・namespace・タグ＋namespace、旧ダンプのタグ検索成功と全件検索拒否を確認する。
- dump-format／design／READMEを更新し、実測と未検証範囲をverificationへ記録する。

## 根拠

対象版の生成ソースでBuiltInRegistries.ITEMとRegistryのキー列挙を確認。現在のSnapshotCollector、DumpJson.decode、RecipeDumpModの失敗・停止経路を確認した。
旧schema 1の追加フィールド許容と既存検索契約は[dump-format](../../reference/dump-format.md)に従う。
