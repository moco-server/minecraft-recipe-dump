# Minecraft Recipe Dumpの開発

日本語で対話する。目的により適した方針があれば理由とトレードオフを示して提案する。

## このrepoの目的

Minecraft／Loader本来の読み込みで有効になったレシピを収集する専用サーバー側MODと、ダンプをゲーム外で調べるCLIを開発する。modpackや通常のコンテンツ追加MODではない。アイテム・装置・ゲーム進行を追加しない。

初期対応はMinecraft 1.21.1、NeoForge 21.1.252、Java 21。Kotlin／KotlinForForge等の固定版はGradle properties／catalog、対応根拠は[compatibility](docs/reference/compatibility.md)。[design](docs/design/architecture.md)が現在の設計、[dump-format](docs/reference/dump-format.md)が保存形式、[cli-search](docs/reference/cli-search.md)がCLI検索・差分の契約、[verification](docs/verification/README.md)が実測と未検証範囲。

## 実装境界と契約

- `core`はpure Kotlin/JVM。JSON検証・保存・集計・差分を担当し、Minecraft／loader／KFF／連携MODの型を内部にも公開APIにも入れない。
- `neoforge`は登録済みRecipeManager／codecとサーバーのライフサイクルを扱う。MOD個別APIを参照しない現方式を優先し、必要性を確認してから専用連携を足す。
- `cli`はcoreを使ってファイルと引数、表示、終了コードを扱う。MinecraftやKFFを実行時依存にしない。Nodeは開発script用で、製品CLIではない。
- 収集は `recipedump.enabled=true` を指定した専用サーバーだけで行う。未指定時・client・通常GameTestを勝手に停止しない。サーバースレッドからの停止は `halt(false)` とし、自身のjoinやSystem.exitを使わない。
- schemaの `complete` は現在のsnapshotの書き出し成功。入力解析の成功と混同しない。`hadErrorsLoading()`だけで安全を断定しない。取得不能な情報は不明として残す。
- 各レシピのcodec例外をID付きで保持する。独自JSONを無理に共通材料モデルへ変換しない。RecipeManager外の全加工や完全な競合判定への対応を宣言しない。
- 出力失敗を成功として表示しない。既存出力は完成後に置き換える。JVM終了コード0をダンプ成功の唯一の根拠にしない。
- 収集タイミング・schema・CLI仕様の変更は文書とテストも更新する。形式バージョンを維持できない変更は明示して扱う。

文書の分類・更新先は [文書案内](docs/README.md) に従う。進行中の設計案・計画は `docs/work/`、完了した資料は `docs/archive/`。現行仕様はreference／designへ反映し、実測は `docs/verification/records/` へ記録する。

## 作業・検証

- [Kotlin規約](.agents/skills/mod-kotlin/references/coding-conventions.md)に従い、val、明示的なcore API、最小の可視性、日本語の理由・契約コメントを使う。
- `./gradlew check :neoforge:build :neoforge:verifyModJar :cli:distZip`、`./gradlew :neoforge:runGameTestServer`、`node scripts/check-gametest-result.mjs`、`node --test scripts/test/*.test.mjs`を実行する。整形修正は `./gradlew ktlintFormat`。
- 収集・停止の変更は通常配布JARでも専用サーバー実行する。有効化時のダンプと正常停止、未指定時の通常稼働、独自レシピ、上書き・除外、実行時変更、保存失敗を変更に応じて確認する。
- GameTest成功、ビルド、通常JAR実行、CLIの実データ処理を区別して記録する。GameTest 0件を成功扱いしない。
- 現在はコンテンツとdatagenを持たない。テンプレートのsample_tokenや数量サンプルを復活させない。JARにはCLI・GameTest・外部MOD・開発資料を混入させない。
- `.local/`とbuildへ環境・ログ・生成物を置く。保存済みワールドを上書きせず新規またはコピーを使う。JAR、ワールド、ログ、認証情報をGitへ追加しない。
- 基本検証はヘッドレスで実施する。対話的client検証が必要になった場合だけ、対象版のEMI／JEIを開発環境へ用意する。本体の依存にはしない。
- 外部skillsは[固定manifest](scripts/skills.tsv)と[導入手順](docs/guides/external-skills.md)に従いrepoの `.agents/skills/` だけへ導入する。通常ビルドはskills／Nodeへ依存させない。

## repo内skills

CLI利用者へ配布するskillは `skills/recipe-dump-cli/` に置き、CLI ZIPへ同梱する。以下の開発用skillsとは用途を分ける。CLIの引数・検索・終了コードの仕様を変更したら、利用ガイドと配布用skillも更新する。

必要なものだけ読む。テンプレート由来の一般MOD向け記述は、このrepoの収集ツールとしての目的・契約を優先して適用する。

| 作業 | skill |
| --- | --- |
| 設計・境界・形式・失敗条件 | [.agents/skills/mod-design/SKILL.md](.agents/skills/mod-design/SKILL.md) |
| Kotlin・Java相互運用 | [.agents/skills/mod-kotlin/SKILL.md](.agents/skills/mod-kotlin/SKILL.md) |
| 収集・保存・loader連携 | [.agents/skills/mod-implement/SKILL.md](.agents/skills/mod-implement/SKILL.md) |
| 起動・ダンプ・CLI不具合 | [.agents/skills/mod-debug/SKILL.md](.agents/skills/mod-debug/SKILL.md) |
| 検証・受け入れ | [.agents/skills/mod-verify/SKILL.md](.agents/skills/mod-verify/SKILL.md) |
| MOD JAR／CLI配布・リリース | [.agents/skills/mod-release/SKILL.md](.agents/skills/mod-release/SKILL.md) |

委任時は目的・対象ファイル・受け入れ条件・必須task・side／保存制約を渡す。統括者はdiffと実測を確認する。
