# CLI検索・差分仕様

現在の検索結果JSON、評価規則、差分と終了コードの契約を記す。操作手順は [CLI利用ガイド](../guides/cli.md)、入力JSONの契約は [ダンプ形式](dump-format.md) を参照する。

## レシピ・タグ検索

検索追加によるschemaVersion 1の変更はない。`recipes` コマンドはID・type・レシピIDのnamespaceを完全一致のAND条件で絞る。明示的なnullや未知の追加フィールドを含む行全体を保持し、ID昇順で `recipes` と `count` を返す。入力snapshotを変更しない。

`items --tag` は `itemTags` のキーを完全一致で参照し、解決済みIDを重複除去して昇順で `tag`・`items`・`count` を返す。参照対象タグの値が文字列配列でなければ拒否する。無関係なタグの値はこの検索では検証しない。未登録タグは空配列と件数0を返す。

未知の検索値は正常な0件検索として終了コード0。検索結果もsnapshotの範囲に限り、入力読み込み成功やゲーム内の作成可否を保証しない。

## 登録アイテム検索

`items` コマンドはtagなしなら登録一覧を要求し、欠落時は再収集を案内して終了コード1。tagありなら従来のタグ集合を使用し、任意namespaceをIDのコロン前への完全一致でAND適用する。結果は昇順のitemsとcount、指定時のみtag／namespaceを持つ。アイテム一覧の差分やsummary項目は追加しない。

## CSV

CSVのdata列は再シリアライズしたJSONで、入力原文や条件の履歴ではない。

## 差分

差分はrecipesのIDとJSON内容を比較し、object key順序を無視、配列順序を維持する。タグが変わって同じレシピの成立範囲が変化しても、dataが同じならchangedに入らない。競合判定やゲーム上の意味の等価判定ではない。

## 材料・出力検索

`recipes --uses <item-id> --produces <item-id>`は任意のAND条件。ID構文は[ダンプ形式](dump-format.md#任意の登録アイテム一覧)のitemsと同じ完全修飾形式で、CLIはI/O前に検証する。metadata条件を先に適用し、指定方向だけ解析する。バニラserializerのcrafting_shaped／shapeless、smelting／blasting／smoking／campfire_cooking、stonecutting、smithing_transformに対応する。smithing_trimは入力のみ対応。特殊crafting・未知serializerは未対応。item／tag／配列ORを扱い、独自材料は未解決。patternの実使用keyだけを参照し、outputはidと正整数count（省略1）を読む。componentsは生JSONへ保持する。

ORは既知一致優先、uses／producesのANDは既知不一致優先。不明を空集合と扱わない。候補検索であり、全入力充足・容器返却・副作用・数量条件・作成可否は評価しない。

材料指定時だけ`analysis`（candidateCount／analyzedCount／unsupportedCount／unresolvedCount／exhaustive）、`diagnostics`、`evidence`を追加する。candidate=analyzed+unsupported+unresolved、一致と不一致を判断した行はanalyzed、両不明分類を持つ行はunresolvedへ一度だけ数える。不明行のdiagnosticsはid／serializer／code／任意pathをID・path・code順に返す。一致行だけのevidenceはusesにitem／path／role／任意tag、producesにitem／path／countを持つ。recipesは明示nullを含む元の行全体。

終了コード0は評価完了（0件も含む）、1は引数・I/O・入力検証エラー、2は部分検索。exhaustiveは不明行0でtrue、metadata候補0もtrue。部分検索でもstdoutはJSON、診断はその中へ返しstderrは通常エラー専用。dump.completeとは独立する。metadataだけの既存検索では新キーと部分終了コードを付けない。

## MOD形式と装置検索

adapterはserializerで選択し、必要方向のJSON形式を検証する。minecraft／mods.versionの欠落・不一致で検索を制限しない。検証に使用した構成はMinecraft 1.21.1、Create 6.0.10、Mekanism 10.7.19.85（mods.versionは10.7.19）、FD 1.3.4。対象serializerと装置別名の表は [CLI利用ガイド](../guides/cli.md#材料jsonの解析形式と検証済み構成) に記載する。未知serializerはunsupported_serializer、必要フィールドの欠落／不正形はinvalid_shapeで、流体・化学物質のidをアイテムに読み替えない。unsupported_versionは返さない。版による意味の変更や実ゲームの互換性を判定する機能ではない。

NeoForgeのcompound（children、alias ingredients）、intersection（children）、difference（base／subtracted）を三値評価する。未知子を空集合にしない。成分条件・block tag・FD item_abilityは未解決だが、ORの別枝で証明できれば一致する。

MODの出力根拠へcount／chanceを付け、確率形式へchanceKindを付ける。Create chanceは省略1、正の有限floatのみ（1以上はその個数別判定が成功）。Mek副出力chanceは必須(0,1]、主出力なしなら<1。両出力なし、副出力なしのchance指定は不正。FD cuttingはfiniteな基礎chance（省略1、0も候補）とbase-chance／fortune-and-server-config条件を示す。保証数量やゲーム内成立可否は評価しない。保持するheld itemはretained-input、工具はtool、cooking serving containerはuses対象外。

`stations dump.json`はダンプ内のRecipeTypeに対応する既知の装置別名をID順のstations配列（id／types）とcountで返す。items／minecraft／modsが欠落する旧ダンプでも使用できる。装置の登録状況は判定せず、recipesが空ならstationsも空。`recipes --station <item-id>`は別名表のtype集合ORと他条件ANDを適用し、stationとstationTypesを追加する。未知装置別名は終了コード1（RecipeTypeによる直接検索は--type）、既知別名の0件は0。stationだけなら動的codecも列挙する。表は装置の全能力・代替・複合設備を網羅しない。
