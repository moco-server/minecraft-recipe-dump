# 実装判断

2026-10-03: Java MOD＋Node CLIで成立を確認した収集をKotlinへ移植した。既存基盤のcore境界・ktlint・detekt・JAR検査・GameTestを維持し、CLIを同じcoreへ接続する。Gson 2.10.1は対象Minecraftの実行環境に合わせる。

現在のsnapshot取得段階は最初のサーバーティックのPost LOWEST。起動途中を中断する方式は採用せず、通常のワールド保存と停止を使う。変更後の一般MODまで最終状態を保証するものではない。

1.21.1のhadErrorsLoadingは入力レシピの解析失敗を確実に表さないため、loadDiagnostics=not-trackedとして扱う。Java実証のrecipeManagerHasErrorsフィールドは移植版の成功判定に使用しない。completeはsnapshotの書き出し完了に限定する。

## 後続CLI第2〜4段階の実装・レビュー判断（2026-10-03）

- inventoryのテストは既存2クラスへ分散せず独立ItemInventoryTestへ置いた。共通fixtureで新契約をまとめ、振る舞いの変更はない。
- 結果JSONの集計は内部RecipeSearchResultへ分けた。DumpSearchは選択へ集中し、代償は内部ファイル1個の追加。
- recipesの名前付き引数APIを維持するため、引数数の規則だけ日本語理由付きで限定抑制した。代償は任意引数が増えること。他の静的解析違反は分割して解消した。
- 成分条件・全入力充足・実作成可否・容器返却／宣言出力以外の副作用は候補ID検索の対象外と維持した。利用者はゲーム内で成立条件を確認する必要がある。
- 装置の全能力・代替・factory・複合設備は固定type表から推測しない。検索入口は全装置・全設備を列挙するものではない。
- 表外serializer／他版は診断に残し、RecipeManager外の加工は収集対象外と維持した。対応外の捕捉済み行は部分検索に残るが、未収集加工を検索することはできない。
- Mekanismの同じ10.7.19ヘッダーを持つ別buildの互換性は宣言しない。実測は10.7.19.85で、ヘッダー一致だけでは他build互換を判断できない。

全体レビューのImportant 1件（使用key欠落の診断が別key一致で消える）は3回帰テストのRED→GREENで修正。Critical／Minor指摘なし。
