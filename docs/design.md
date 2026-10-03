# レシピ収集ツールの設計

## 目的

任意の対応MOD構成について、実際に読み込まれたレシピを保存してゲーム外で集計・比較する。初版はMinecraft 1.21.1／NeoForge 21.1.252に絞り、収集MODとKotlin CLIを同じrepoで保守する。moco-server/minecraft-mod-templateを基盤に、親packで検証した使い捨てJava MOD＋Node CLIの挙動をKotlinへ移植した。

## 構成

- core: Gsonによるschema 1の検証、atomic保存、レシピ集計、CSV、ID単位の差分、レシピ・登録アイテム・アイテムタグ検索。Minecraft／loader／KFFの依存を禁止する。
- neoforge: 有効化オプション、専用サーバーの最初のティック終了時の収集、登録済みRecipe.CODEC、タグ・構成の取得、通常停止。
- cli: 引数・ファイル読み込み・stdout／stderr・終了コード。coreを利用する。

ゲームのオブジェクトを独自の材料モデルへ再実装せず、登録済みcodecのJSONを保持する。収集MODにCreateやMekanismの個別APIは不要。ゲームから独立した操作だけcoreへ置く。

## 成功・失敗

未指定時は購読も自動停止も行わない。有効化した専用サーバーではServerTickEvent.Post LOWESTの最初の呼出しで収集し、保存の成否にかかわらずhalt(false)で通常停止する。GameTestとclientは自動収集の対象外。

各レシピのencode/decode例外はID付きで残し、残りを続行する。例外なしのsnapshotだけcomplete=true。JSONの保存は同じディレクトリの一時ファイルからatomic moveで置換する。保存失敗はRECIPE_DUMP_FAILEDとしてログに残す。

読み込み時の解析失敗、条件による除外、書き出し失敗は別の事象。現在は入力の読み込み診断を捕捉しないためloadDiagnostics=not-tracked。対象版hadErrorsLoadingは解析エラー時にもfalseのため成功判定に使用しない。

JVM終了コードは保存成功を保証しない。出力runIdをログに記録するが、実行ID付き状態ファイルとCLIの自動起動管理は未実装。利用者は新規出力パスと今回のログ・ダンプを照合する。

## 今回の範囲と今後

一覧・CSV・レシピ差分に加え、レシピID／RecipeType／レシピIDのnamespaceによるAND検索と、解決済みアイテムタグの検索を提供する。検索用のJSON生成はcore、引数・I/O・表示はcliが担当する。IDのnamespaceは提供元JARの判定には使わない。同じsnapshotの登録キーから全アイテム一覧も収集し、タグまたは一覧へアイテムnamespace条件を適用する。旧ダンプはタグ検索を維持し、一覧検索は再収集を要求する。

バニラ材料・出力検索はcoreの版・serializer限定adapterで必要方向だけ三値評価する。metadata条件後の候補を解析済み／未対応／未解決へ分割し、不明行は診断と部分終了コード2で明示する。items一覧は解析の前提にしない。MOD固有JSONは固定版・明示serializer別の小さいadapterをcoreに置く。装置IDとRecipeTypeの版付き表を解析から独立させ、装置だけの検索ではcodecを読まない。対応範囲は[段階的検索の設計](superpowers/specs/2026-10-03-cli-search-design.md)とREADMEへ記載する。MOD API依存や装置能力の推測は追加しない。
完全な競合検出、タグ／構成の差分、RecipeManager外の加工、任意インスタンス自動準備、ワールド生成前の終了、他Loader対応は含まない。後のティックやreloadによる変更もsnapshotに含まれると保証しない。

今後は読み込み診断とrunId付き状態ファイルを先に検討する。競合検査を追加するなら対象RecipeTypeを明示する。対応版追加はアダプター別の実測で判断する。

## 根拠

[NeoForge 1.21.1 Recipes](https://docs.neoforged.net/docs/1.21.1/resources/server/recipes/)、[Data Load Conditions](https://docs.neoforged.net/docs/1.21.1/resources/server/conditions/)と、21.1.252開発環境が生成する対象版ソースを確認した。実証結果と移植後の実測はverification.mdへ記録する。
