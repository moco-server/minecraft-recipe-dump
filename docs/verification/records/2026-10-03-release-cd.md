# GitHub Release CDの検証（2026-10-03）

## 変更と契約

既存Verify workflowへ `v*` タグのRelease jobを追加した。版一致を両OSのビルド前に確認し、Linux・Windows両検証の成功後に同一実行の通常MOD JARとCLI ZIPを添付する。公開jobだけにcontents writeを付け、draft作成・添付・公開の順で処理する。Pre-releaseはLatestにせず、公開済みReleaseは上書きしない。手順と復旧条件は [Releaseガイド](../../guides/release.md) を参照する。

CLI ZIPへ利用ガイドとLICENSEを同梱した。ガイドの相対リンクは同じ版のタグの文書URLへ変換する。Gradleの通常ビルドはNodeに依存しない。製品の収集・停止・保存・検索・schemaは変更していない。

## 実測

Linux（WSL2）、Eclipse Adoptium Java 21.0.12.1、Gradle 8.12.1。GRADLE_USER_HOMEは既存 `/tmp/recipe-dump-cli-search/.local/gradle` を使用した。

```sh
./gradlew ktlintFormat check :neoforge:build :neoforge:verifyModJar :cli:distZip :cli:installDist
./gradlew :neoforge:runGameTestServer
node scripts/check-gametest-result.mjs
node --test scripts/test/*.test.mjs
```

- 必須check・通常JAR・JAR内容検査・CLI ZIP・installDist成功。75 tasks中8件実行・67件UP-TO-DATE。単体テストは既存結果を再利用した。
- 既存検証ワールドを退避し、新規ワールドでGameTest必須3件成功。結果検査も3件を確認した。既存ワールドは実行後に元の場所へ戻した。
- Nodeは既存29件と新規21件、計50件成功。版・tag・成果物の検証15件、公開scriptの外部API境界6件を検査した。正常版と公開の正常経路は実装前に失敗し、実装後に成功した。
- 公開scriptはGitHub CLIだけを代替し、実際のBashを実行した。通常版のdraft→2成果物添付→公開、Pre-release／Latest、既存draftの再開、添付失敗後の未公開、公開済みReleaseの拒否、不一致tagでAPIを呼ばないことを確認した。実際のGitHub APIの動作確認ではない。
- actionlint 1.7.12でworkflowを検査し成功。公式GitHub Releaseの配布物とchecksumを照合して `.local/release-cd-tools/` に配置した。Bash構文検査、文書リンク・見出しアンカー検査、git diff --checkも成功。
- CLI ZIPを `.local/release-cd-unpacked/` へ新規展開し、README・LICENSE・版付きリンク・bin/libを確認した。起動scriptで空snapshotのsummaryを実行し、終了コード0・期待JSON・stderr空を確認した。並行JVMによるperf data警告を避けるため、このCLI検証だけにJAVA_OPTSのActiveProcessorCount=2とUsePerfData無効を指定した。
- 実生成した `minecraft_recipe_dump-0.1.0-dev.jar` と `recipe-dump-0.1.0-dev.zip` をコピーし、`v0.1.0-dev` と版番号・2ファイルの一致、Pre-release判定を確認した。

証拠は `.local/release-cd-{baseline,red,green,publish-red,publish-green,node,package,check,game,game-result,cli-smoke}.log`、展開ZIPとコピーした成果物は `.local/release-cd-unpacked/` と `.local/release-cd-assets/`。

## ローカルレビュー

別contextの読み取り専用レビュアーが全差分と生成ZIPを確認し、Critical／Importantなし。Minor 1件として、不正SemVerのテストがタグ不一致だけを検査している点を指摘した。版とタグを一致させた不正版5ケースへ修正し、Node全50件の成功を確認した。外部サービスへの送信は行っていない。

## 未実施

GitHubへのタグpush、実際のRelease作成・artifactダウンロード・添付・公開、Windows／macOSでの配布CLI起動、通常配布MODでの専用サーバー再実行は未実施。CDの外部連携は初回タグpushで確認する。公開jobが必要とするrepo設定・権限の実環境検証も、その実行時に確認する。
