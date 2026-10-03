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

未知の検索値は正常な0件検索として終了コード0。検索結果もsnapshotの範囲に限り、入力読み込み成功やゲーム内の作成可否を保証しない。

## 差分と構成情報

差分はrecipesのIDとJSON内容を比較し、object key順序を無視、配列順序を維持する。タグが変わって同じレシピの成立範囲が変化しても、dataが同じならchangedに入らない。競合判定やゲーム上の意味の等価判定ではない。

MODのversionと選択pack順序を記録するが、JAR・設定・スクリプト・packのハッシュはダンプへ含めていない。namespaceから提供元JARを断定しない。

## 任意の登録アイテム一覧

`items` はschema 1の任意追加フィールドで、全登録アイテムの完全修飾IDを収集側が重複なし・昇順で保存する。minecraft:airも含む。decodeは配列順を問わず空配列を受理するが、null・誤型・重複・不正IDを拒否する。IDは`[a-z0-9_.-]+:[a-z0-9/._-]+`。旧ダンプの欠落は許容する。

`items` コマンドはtagなしなら登録一覧を要求し、欠落時は再収集を案内して終了コード1。tagありなら従来のタグ集合を使用し、任意namespaceをIDのコロン前への完全一致でAND適用する。結果は昇順のitemsとcount、指定時のみtag／namespaceを持つ。アイテム一覧の差分やsummary項目は追加しない。保存は独自JSONの明示nullを含めてatomic置換する。

## 材料・出力検索

`recipes --uses <item-id> --produces <item-id>`は任意のAND条件。ID構文はitemsと同じ完全修飾形式で、CLIはI/O前に検証する。metadata条件を先に適用し、指定方向だけ解析する。Minecraft 1.21.1のcrafting_shaped／shapeless、smelting／blasting／smoking／campfire_cooking、stonecutting、smithing_transformに対応する。smithing_trimは入力のみ対応。特殊crafting・未知版・未知serializerは未対応。item／tag／配列ORを扱い、独自材料は未解決。patternの実使用keyだけを参照し、outputはidと正整数count（省略1）を読む。componentsは生JSONへ保持する。

ORは既知一致優先、uses／producesのANDは既知不一致優先。不明を空集合と扱わない。候補検索であり、全入力充足・容器返却・副作用・数量条件・作成可否は評価しない。

材料指定時だけ`analysis`（candidateCount／analyzedCount／unsupportedCount／unresolvedCount／exhaustive）、`diagnostics`、`evidence`を追加する。candidate=analyzed+unsupported+unresolved、一致と不一致を判断した行はanalyzed、両不明分類を持つ行はunresolvedへ一度だけ数える。不明行のdiagnosticsはid／serializer／code／任意pathをID・path・code順に返す。一致行だけのevidenceはusesにitem／path／role／任意tag、producesにitem／path／countを持つ。recipesは明示nullを含む元の行全体。

終了コード0は評価完了（0件も含む）、1は引数・I/O・入力検証エラー、2は部分検索。exhaustiveは不明行0でtrue、metadata候補0もtrue。部分検索でもstdoutはJSON、診断はその中へ返しstderrは通常エラー専用。dump.completeとは独立する。metadataだけの既存検索では新キーと部分終了コードを付けない。

## MOD形式と装置検索

Minecraft 1.21.1とmods.version（Create 6.0.10、Mekanism 10.7.19、FD 1.3.4）でadapterを選択する。対象serializerと装置の全表は[第4段階設計](superpowers/specs/2026-10-03-mod-recipe-station-search-design.md)へ固定する。Mekanismの実測配布JARは10.7.19.85、mods.versionからbuildを判別できない。版欠落／不一致はunsupported_version。未知JSON形はinvalid_shapeで、流体・化学物質のidをアイテムに読み替えない。

NeoForgeのcompound（children、alias ingredients）、intersection（children）、difference（base／subtracted）を三値評価する。未知子を空集合にしない。成分条件・block tag・FD item_abilityは未解決だが、ORの別枝で証明できれば一致する。

MODの出力根拠へcount／chanceを付け、確率形式へchanceKindを付ける。Create chanceは省略1、正の有限floatのみ（1以上はその個数別判定が成功）。Mek副出力chanceは必須(0,1]、主出力なしなら<1。両出力なし、副出力なしのchance指定は不正。FD cuttingはfiniteな基礎chance（省略1、0も候補）とbase-chance／fortune-and-server-config条件を示す。保証数量やゲーム内成立可否は評価しない。保持するheld itemはretained-input、工具はtool、cooking serving containerはuses対象外。

`stations dump.json`は版に対応する装置をID順のstations配列（id／types）とcountで返す。items欠落の旧ダンプでも使用できる。minecraft版欠落／非対応は終了コード1。`recipes --station <item-id>`はtype集合ORと他条件ANDを適用し、stationとstationTypesを追加する。未知装置・未対応MOD版は終了コード1、既知0件は0。stationだけなら動的codecも列挙する。表は装置の全能力・代替・複合設備を網羅しない。
