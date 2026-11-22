# Changesetsによる配布の検証（2026-11-22）

LinuxでNode.js 24、pnpm 12.8.1、Java 21、Gradle Wrapper 8.12.1、Maven 3.9.11を使用した。

- 独立した作業コピーで `pnpm install --frozen-lockfile --ignore-scripts` と `pnpm run release:version` を実行した。初回のpatch changesetを消費して `CHANGELOG.md` が生成され、`package.json` と `gradle.properties` の両方が `0.1.0-dev` から `0.1.0` に更新された。
- 同じコピーで `./gradlew --offline check :neoforge:build :neoforge:verifyModJar :cli:distZip :neoforge:runGameTestServer` が成功した。新規ワールドで `node scripts/check-gametest-result.mjs` が必須3件の成功を確認した。`release-info.mjs` で通常MOD JAR・CLI ZIPの2ファイルとタグ `v0.1.0` を照合した。
- 修正前はKFF経由のFML 4.0.45とNeoForge 21.1.252が供給する4.0.44が混在し、GameTestが `fml_loader` のモジュール重複で起動に失敗した。KFFの推移依存からloaderを除外し、NeoForgeが指定する版で検証した。
- Maven Deploy Plugin 3.1.4で上記のJARとZIPをファイルベースのMavenリポジトリへ登録した。両座標にPOMが存在し、登録した成果物が元ファイルとバイト単位で一致した。
- Nodeテストで、対象版の変更履歴を使ったdraft作成、添付失敗時の停止、draftからの再開、公開済みReleaseの保護を確認した。actionlint 1.7.12で両workflowを検査した。

ビルドとGameTestのログは `.local/changesets-first-release.log`、Mavenの登録ログは `.local/changesets-maven-{jar,zip}.log` に保存した。GitHub上でのリリースPR作成、Windows CI、Release公開、GitHub Packagesへの登録は初回実行で確認する。
