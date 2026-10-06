# Minecraft Recipe Dump

MOD入りMinecraftの**実際に読み込まれたレシピを収集し、ゲーム外で調べる開発補助ツール**です。専用サーバー側の収集MODと、ダンプを集計・比較するKotlin/JVM CLIを同じrepoで開発します。

収集はMinecraft／Loader本来の読み込み処理と、各MODの登録済みシリアライザーを使います。MODのJARからレシピJSONを抽出する方式ではありません。ゲーム画面やプレイヤー接続は不要ですが、収集時には専用サーバーを起動します。

初期対応は **Minecraft 1.21.1 / NeoForge 21.1.252 / Java 21**。収集MODには **KotlinForForge 5.5.0の配布JAR**も必要です。CLIはMinecraft・NeoForge・KotlinForForgeなしで動作します。現在は実証コードを移植した初期実装で、レシピの競合自動判定や任意インスタンスの自動セットアップは含みません。

## 利用者向けガイド

使い方は、入手するツールごとに分けています。配布物を使う場合、リポジトリのcloneやGradleは不要です。

| ツール | 配布先（予定） | 利用ガイド |
| --- | --- | --- |
| 収集MOD | CurseForge／Modrinth | [導入・収集・成功確認](docs/guides/mod.md) |
| CLI | [GitHub Releases](https://github.com/moco-server/minecraft-recipe-dump/releases) | [展開・起動・集計・検索](docs/guides/cli.md) |

本READMEには本MODの公開配布ページへのリンクをまだ掲載していません。CLI ZIPには利用ガイドを同梱し、`v*` タグのpushで検証後に通常MOD JARとCLI ZIPをGitHub Releaseへ添付します。手順は [Releaseガイド](docs/guides/release.md) を参照してください。まだReleaseがない場合は、下の開発手順でビルドしてください。

基本の流れは、対象構成の専用サーバーで収集MODを有効にしてJSONを保存し、そのJSONをCLIで調べる、というものです。CLIは別のPCでも使えます。

収集MODは明示的に有効にした専用サーバーだけで収集し、保存後に停止します。有効化しない場合は収集・自動停止を行いません。ダンプはRecipeManagerのスナップショットで、入力全件の読み込み成功やRecipeManager外の全加工を保証するものではありません。範囲と確認方法は各ガイドを参照してください。

CLI ZIPには、別のMOD開発プロジェクトのLLMが検索・差分に使う [recipe-dump-cli skill](skills/recipe-dump-cli/SKILL.md) も同梱します。[導入手順](docs/guides/cli.md#mod開発プロジェクトでllmから使う)に従って導入先へコピーしてください。このrepo自身の開発用skillsとは別の配布物です。

## 開発

| module | 担当 |
| --- | --- |
| `core/` | ゲームに依存しないJSON検証、atomic保存、集計、CSV、差分、検索 |
| `neoforge/` | イベント購読、RecipeManager収集、登録済みcodec、正常停止 |
| `cli/` | 引数、ファイル読み込み、stdout／stderr、終了コード |

```sh
./gradlew check :neoforge:build :neoforge:verifyModJar :cli:distZip
./gradlew :neoforge:runGameTestServer
node scripts/check-gametest-result.mjs
node --test scripts/test/*.test.mjs
```

通常MOD JARは `neoforge/build/libs/`。sources JARとは区別してください。CLI配布ZIPは `cli/build/distributions/` に生成します。

ソースからCLIを起動する場合:

```sh
./gradlew :cli:installDist
cli/build/install/recipe-dump/bin/recipe-dump summary /path/to/recipes.json
```

Windowsは `gradlew.bat` と `recipe-dump.bat` を使用します。Java 21を `JAVA_HOME` に設定してください。

開発用ダンプ起動は `./gradlew :neoforge:runServerDump`、環境は `.local/dump-server/`（EULA等の初回設定が必要）。対話的クライアントは収集ツールの基本検収には不要です。

文書の入口は [docsの案内](docs/README.md) です。利用手順・現行仕様・設計・検証・過去の計画を分けています。

LLMは [AGENTS.md](AGENTS.md) と必要なrepo内skillを読みます。[設計](docs/design/architecture.md)、[ダンプ形式](docs/reference/dump-format.md)、[対応構成](docs/reference/compatibility.md)、[検証記録](docs/verification/README.md)に判断と実測を残します。

基盤は [moco-server/minecraft-mod-template](https://github.com/moco-server/minecraft-mod-template) 0.1.0です。識別情報は初期化済みで、初期化scriptを再実行する必要はありません。非公開repo、ソースライセンスはMITです。
