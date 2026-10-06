---
name: recipe-dump-cli
description: Minecraft Recipe Dump CLIで、収集済みダンプのレシピ・材料・出力・アイテムタグ・加工装置を調査する時や、MOD開発の変更前後を比較する時に使う。収集MODやCLI自体の実装・ビルドには使わない。
---

# Recipe Dump CLIの利用

専用サーバーで有効になったRecipeManagerのスナップショットを、ゲーム外で調べる。CLIとJava 21だけで検索でき、Minecraft・Loader・KotlinForForgeは不要。

## 入力と準備

導入先のAGENTS.mdや利用ガイドから、CLI起動スクリプトと対象ダンプのパスを確認する。CLI ZIPはbin/とlib/を揃えて展開し、JAVA_HOMEをJava 21へ向ける。下の`recipe-dump`を実際の起動スクリプトのパスへ置き換える。Windowsはbin/recipe-dump.batを使う。起動スクリプトだけをコピーしない。

指定されたダンプを優先する。指定がなければ候補の構成・runIdを確認して選び、複数構成を区別できなければ確認する。ファイルの更新日時だけで現在の開発構成と一致すると断定しない。MOD・設定・スクリプト・データパック変更後の古いダンプは、その変更を検証する証拠にしない。

まず`recipe-dump summary dump.json`で読み込みを確認する。巨大なダンプを会話へ全件展開せず、目的に合わせてCLIで絞る。パスの空白はshellの規約に従って引用する。

## 調査の入口

| 目的 | コマンド例 |
| --- | --- |
| MOD namespaceの登録アイテム | `recipe-dump items dump.json --namespace create` |
| タグの解決済みアイテム | `recipe-dump items dump.json --tag minecraft:planks` |
| レシピIDを特定 | `recipe-dump recipes dump.json --id minecraft:iron_ingot_from_smelting_iron_ore` |
| レシピIDのnamespaceで絞る | `recipe-dump recipes dump.json --namespace create` |
| アイテムの用途 | `recipe-dump recipes dump.json --uses minecraft:iron_ingot` |
| アイテムの作り方の候補 | `recipe-dump recipes dump.json --produces minecraft:iron_ingot` |
| 既知の加工装置別名 | `recipe-dump stations dump.json` |
| 装置と出力で絞る | `recipe-dump recipes dump.json --station create:crushing_wheel --produces minecraft:amethyst_shard` |
| RecipeTypeで直接調べる | `recipe-dump recipes dump.json --type create:crushing` |
| 変更前後のレシピ差分 | `recipe-dump diff before.json after.json` |

検索値は完全一致。アイテム・タグ・レシピ・装置・typeは完全修飾ID、namespaceはコロン前を指定する。オプションは`--name value`形式で、複数条件はAND。同じオプションの重複や部分一致・名前補完は使えない。`--namespace`はレシピIDまたはアイテムIDへの条件で、材料・出力の所属や提供元JARを示さない。

人間の呼び名しか分からない場合は、items等でID候補を確認する。未知装置別名はエラーになるためstationsで確認し、別名がなければ分かっているRecipeTypeを--typeへ渡す。typeも不明ならsummaryのRecipeType別件数や、--namespaceで絞ったレシピのtypeを確認する。stationsは既知のtype対応表であり、装置の登録・全能力を証明しない。

## 実行結果の判断

stdout、stderr、終了コードを一緒に確認する。パイプ先の終了コードや`|| true`でCLIの終了コードを失わない。正常な検索結果はJSONで、recipes/items/stations配列とcountを持つ。summary・csv・diffを同じJSON構造として扱わない。

| 終了コード | 判断と次の行動 |
| --- | --- |
| 0 | 成功。0件も正常。指定条件・snapshotの範囲に限定して報告する。 |
| 1 | 引数・読み込み・入力検証等のエラー。stderrを確認し、検索結果として解釈しない。 |
| 2 | 材料・出力の部分検索。stdoutのJSONは有効。analysis・diagnostics・evidenceを確認し、未判定を残して報告する。 |

材料・出力検索ではanalysis.exhaustiveとunsupportedCount/unresolvedCountを見る。終了コード2でcount=0でも「該当なし」と断定しない。diagnosticsのレシピID・serializer・code・pathから、必要な行を--idで取得してdataを調べる。未知形式は不明のまま扱い、必要なら対象MODの仕様・ソース・実ゲームで確認する。独自形式のJSON中に同じ文字列があるだけで材料や出力と決めない。

## 証拠と限界

- completeはsnapshotの書き出し成功。入力全件の読み込み成功ではなく、loadDiagnostics=not-trackedは未追跡。入力失敗は収集時のサーバーログも必要。
- 収集は最初のサーバーティック終了時。後のティック・reloadによる変更、RecipeManager外の独自加工やJEI/EMI専用表示は対象外。ダンプがなければ収集手順へ戻り、CLIだけで生成しようとしない。
- uses/producesはアイテムIDによる候補検索。全材料の充足、必要数量、成分条件、ゲーム内の作成可否は保証しない。evidenceのroleで工具・保持材料を区別し、countやchanceを保証数量として扱わない。液体・化学物質IDをアイテムIDへ読み替えない。
- diffはレシピIDの追加・削除・JSON内容変更。object key順序は無視し、配列順序は保持する。タグ・構成の変更や意味の等価性、競合を判定しない。タグ影響の調査は両ダンプのitems --tagも比較する。
- itemsなしの旧ダンプは全登録アイテム一覧を取得できない。items --tagは利用できるが、全一覧が必要なら再収集する。

回答には使用ダンプのパス、取得できればrunId・構成、実行した検索条件と終了コード、該当レシピIDとdata/evidenceの根拠、未判定の件数・理由を必要な範囲で示す。観測結果と推測を区別する。ダンプは観測結果なので、修正は開発プロジェクトのレシピソース・datagen・スクリプト等で行い、再収集して比較する。
