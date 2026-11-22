# Changesetsによる配布の検証（2026-11-22）

LinuxでNode.js 24、pnpm 12.8.1、Java 21、Gradle Wrapper 8.12.1、Maven 3.9.11を使用した。

- 独立した作業コピーで `pnpm install --frozen-lockfile --ignore-scripts` と `pnpm run release:version` を実行した。初回のpatch changesetを消費して `CHANGELOG.md` が生成され、`package.json` と `gradle.properties` の両方が `0.1.0-dev` から `0.1.0` に更新された。
- 同じコピーで `./gradlew --offline check :neoforge:build :neoforge:verifyModJar :cli:distZip :neoforge:runGameTestServer` が成功した。新規ワールドで `node scripts/check-gametest-result.mjs` が必須3件の成功を確認した。`release-info.mjs` で通常MOD JAR・CLI ZIPの2ファイルとタグ `v0.1.0` を照合した。
- 修正前はKFF経由のFML 4.0.45とNeoForge 21.1.252が供給する4.0.44が混在し、GameTestが `fml_loader` のモジュール重複で起動に失敗した。KFFの推移依存からloaderを除外し、NeoForgeが指定する版で検証した。
- Maven Deploy Plugin 3.1.4で上記のJARとZIPをファイルベースのMavenリポジトリへ登録した。両座標にPOMが存在し、登録した成果物が元ファイルとバイト単位で一致した。
- Nodeテストで、対象版の変更履歴を使ったdraft作成、添付失敗時の停止、draftからの再開、公開済みReleaseの保護を確認した。actionlint 1.7.12で両workflowを検査した。

ビルドとGameTestのログは `.local/changesets-first-release.log`、Mavenの登録ログは `.local/changesets-maven-{jar,zip}.log` に保存した。GitHubでの実行結果は次節に記す。

## v0.1.0 の初回配布

- ChangesetsがリリースPRを作成し、`package.json` と `gradle.properties` を `0.1.0` に更新した。PRの検証実行を承認し、Linux・Windowsの成功後にマージした。[リリースPR #3](https://github.com/moco-server/minecraft-recipe-dump/pull/3)・[PRのVerify](https://github.com/moco-server/minecraft-recipe-dump/actions/runs/45013626324)
- マージコミット `0e8ff20ecbb133723f28ac0c371210b5ecdd8eee` を再検証し、Linux・Windowsのビルド、Nodeテスト51件、必須GameTest 3件が成功した。検証したコミットにタグ `v0.1.0` が付き、Releaseを作成した。[Release実行](https://github.com/moco-server/minecraft-recipe-dump/actions/runs/45014396305)
- 同じ実行の通常JARをJava 21・Minecraft 1.21.1・NeoForge 21.1.252・KotlinForForge 5.5.0の新規専用サーバーへ導入した。レシピ1,290件を保存し、`complete: true`、ログとダンプのrunId一致、通常停止を確認した。同じ実行のCLI ZIPを展開し、集計、登録アイテム1,333件の一覧、かまどレシピ70件の検索結果をダンプと照合した。
- Releaseからダウンロードした通常JARとCLI ZIPは、起動確認に使った成果物とバイト単位で一致した。[GitHub Release v0.1.0](https://github.com/moco-server/minecraft-recipe-dump/releases/tag/v0.1.0)
- GitHub Packagesの登録ジョブは `org.mocoserver.recipedump:minecraft-recipe-dump:0.1.0`（jar）と `org.mocoserver.recipedump:recipe-dump-cli:0.1.0`（zip）の両方で成功した。[Release実行](https://github.com/moco-server/minecraft-recipe-dump/actions/runs/45014396305)

| Releaseのファイル | SHA-256 |
| --- | --- |
| `minecraft_recipe_dump-0.1.0.jar` | `0bef773a77be3b925a26b9903a8e2f45802992002a611b116a4f34052d59ee30` |
| `recipe-dump-0.1.0.zip` | `3f1bc2537286e71f9765a49e01c56751224f044c4a4b775c0f48ce6107c312d6` |

ローカルの実測ログは `.local/first-release-0.1.0.eoJbxY/` に保存した。Mavenクライアントからの取得は、手元の認証に `read:packages` 権限がないため未検証。
