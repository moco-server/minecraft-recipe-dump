# 文書整理の検証（2026-10-03）

## 変更範囲

利用・開発手順、現在の仕様、現在の設計、検証の現状と日付付き実測、完了した設計案・計画を分離した。[文書案内](../../README.md) に分類・更新先と、進行中資料を完了時にarchiveへ移すルールを記した。

ダンプ形式からCLI検索・差分の契約を分離し、現行設計から過去の設計案への依存を解消した。過去の検証本文は当時の情報として保持した。README・AGENTS.md・repo内skillsの参照を更新した。初期化scriptは旧配置のfixtureへの対応を残し、新配置への対応と既存テスト内の検査を追加した。製品の収集・保存・停止・検索挙動とschemaは変更していない。

## 実行結果

Linux（WSL2）、Eclipse Adoptium Java 21.0.12.1。既存の `/tmp/recipe-dump-cli-search/.local/gradle` をGRADLE_USER_HOMEとして使用した。

```sh
./gradlew check :neoforge:build :neoforge:verifyModJar :cli:distZip
./gradlew :neoforge:runGameTestServer
node scripts/check-gametest-result.mjs
node --test scripts/test/*.test.mjs
```

- check／通常MOD JARビルド／JAR内容検査／CLI配布ZIP生成は成功。54 tasksのうち3件実行・51件UP-TO-DATEで、単体テストは既存結果を再利用した。
- GameTestは新規ワールドで実行し、必須3件成功。結果検査scriptでも3件を確認した。既存検証ワールドは実行前に退避して保全した。
- Nodeテスト29件成功・失敗0件。旧文書配置と新しい `docs/design/architecture.md` の初期化処理を確認した。実repoの初期化は実行していない。
- README・AGENTS.md・docs・repo内skillsのMarkdownについて、ローカルリンクの存在、見出しアンカー、コードフェンスの対応を確認。`git diff --check` も成功。

ログは `.local/docs-reorganization-{check,game,game-result,node}.log`。リンク検査scriptは `/tmp/check-recipe-doc-links.py` に置いた。

## 未実施

通常配布MOD JARでの専用サーバー再実行、配布CLIの実データ再照合、Windows／macOSの実行、外部サービスへのレビュー送信・配布公開は行っていない。文書と継承した開発scriptの文書配置対応のみの変更で、製品挙動の実測範囲は [検証の現状](../README.md) にある過去の記録を参照する。
