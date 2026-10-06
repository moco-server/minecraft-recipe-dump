# 2026-10-06 CLI利用者向けskill

`skills/recipe-dump-cli/SKILL.md` を追加し、CLI ZIPへ同梱した。導入先へのコピーとAGENTS.mdの記載例をCLI利用ガイドへ追加した。開発用skillsへの依存はなく、収集・検索・保存形式の仕様変更はない。

## 構成と実行

Linux、Minecraft 1.21.1／NeoForge 21.1.252。Java 21が既存環境になかったため、AdoptiumのJava 21配布APIから取得したTemurin 21.0.12.1を `.local/cli-skill-tools/jdk21/` へ展開した。

```sh
python3 /home/osamu/.codex/skills/.system/skill-creator/scripts/quick_validate.py skills/recipe-dump-cli
JAVA_HOME="$PWD/.local/cli-skill-tools/jdk21" ./gradlew check :neoforge:build :neoforge:verifyModJar :cli:distZip :cli:installDist
node --test scripts/test/*.test.mjs
python3 .local/cli-skill-smoke.py
JAVA_HOME="$PWD/.local/cli-skill-tools/jdk21" ./gradlew -I .local/cli-skill-gametest.gradle :neoforge:runGameTestServer
git diff --check
```

## 結果

- skill frontmatter、変更対象文書のローカルリンク、diff空白検査が成功。
- Gradle check・通常MOD JAR内容検査・CLI ZIP生成が成功。core 40件、CLI 15件のテスト結果は失敗0（既存テストtaskはUP-TO-DATE）。通常MOD JARにskillsの混入なし。
- Nodeテスト50件成功。
- ZIP内のskillがソースとバイト一致し、開発用skillsの混入なし。
- 展開した配布CLIで16照合成功。実測済みcodecの保存fixtureと未知serializerを含む合成ダンプでsummary・ID／namespace／type／装置／材料／出力検索・items・タグ・stations・diffを実行した。部分検索0件は終了コード2と未判定を保持、未知装置は1、itemsなしの旧ダンプは全一覧で1・タグ検索で0。タグだけを変えてもレシピdiffは変わらないことを確認。新しい実サーバーダンプを収集したものではない。
- 新規GameTest環境向けの一時init scriptは初回にbuildSrcまで適用されて起動前に失敗。findProjectによる対象判定を加えて再実行した。
- GameTest再実行は `Module thedarkcolour.kotlinforforge reads more than one module named fml_loader` で起動失敗。成功件数未確認。`scripts/check-gametest-result.mjs` のcheckGameTestResult関数へ今回のlatest.logを渡す検査も `No positive passing required GameTest result` で失敗。過去の成功ログは今回の証拠に使っていない。

ログは `.local/cli-skill-check.log`、`.local/cli-skill-node.log`、`.local/cli-skill-smoke.log`、`.local/cli-skill-game.log`。CLI照合script・合成ダンプ・ZIP展開先は `.local/cli-skill-smoke.py` と `.local/cli-skill-smoke/`。新規GameTest環境は `.local/cli-skill-gametest/` で、既存保存ワールドは使用していない。

## LLMによる利用確認と未検証範囲

別LLMへ同じ利用課題を渡し、skillなしとskillのみを読んだ場合を比較した。skillなしでは具体的なCLI引数と装置検索方法を特定できなかった。skillを読んだLLMは用途・出力・装置検索のコマンドを示し、部分検索0件、タグ変更を含まないdiff、completeと入力読み込み成功の区別、未知装置と旧itemsなしダンプへの対処を説明できた。未知装置のtypeの探し方が不足するという指摘に対し、summaryとnamespace検索のtypeを参照する手順を追記した。

限定的な手順確認であり、導入先のMOD開発プロジェクトでの運用実績ではない。Windows／macOS実行、今回の通常JARによる専用サーバー収集、GameTest成功は未確認。
