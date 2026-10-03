# CLI検索 第1段階 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** schema 1のダンプから、レシピID・RecipeType・レシピnamespace・アイテムタグを検索できるCLIを提供する。

**Architecture:** coreに検索専用のDumpSearchを追加し、既存DumpReportsを維持する。cliでコマンドと引数を検証してからファイルを読み、coreの結果をJSONとして出力する。収集MODとschemaは変更しない。

**Tech Stack:** Kotlin 2.0.21、Java 21、Gson 2.10.1、JUnit 5.11.4、Gradle 8.12.1。依存追加なし。

**Spec:** [承認済み設計](../specs/2026-10-03-cli-search-design.md)。第1段階のみを実装する。

## Global Constraints

- 初期対応はMinecraft 1.21.1／NeoForge 21.1.252。現在のcore／neoforge／cliの境界を維持する。
- coreはpure Kotlin/JVM。Minecraft／loader／KFF／MOD個別APIの型を参照しない。
- 第1段階ではneoforge、収集タイミング、停止動作、schemaVersionを変更しない。
- 既存summary／csv／diffと不完全ダンプ拒否を維持する。
- ID・type・tagは完全一致、namespaceは最初のコロンより前との完全一致。複数フィルターはAND。
- 出力はJSON、診断はstderr。成功と0件は終了コード0、引数・I/O・検証エラーは1。
- Kotlin規約に従い、val、明示的なcore API、最小の可視性、日本語の理由・契約コメントを使う。
- ログ・生成物は.local／buildに置き、保存済みワールドを上書きしない。

## Review Focus

- create検索でcreate_extraやserializerだけがcreateの行を含めない（Task 1）。
- 元のレシピ行に未知の追加フィールドがあっても結果に保持し、検索で入力を変更しない（Task 1）。
- タグ値が配列以外、または配列に数値・nullがある場合、文字列へ黙って変換しない（Task 1）。
- オプション値が次のオプションになっている場合、値不足としてファイル読み込み前に拒否する（Task 2）。
- 未指定のnamespaceを補完せず、未知IDの0件と不完全ダンプのエラーを区別する（Task 2）。

## ファイル構成

- 新規core/src/main/kotlin/org/mocoserver/recipedump/core/DumpSearch.kt: フィルターと結果生成。
- 新規core/src/test/kotlin/org/mocoserver/recipedump/core/DumpSearchTest.kt: 検索・タグ検証・非破壊性。
- 変更cli/src/main/kotlin/org/mocoserver/recipedump/cli/Main.kt: 新コマンドへの振り分け。
- 新規cli/src/main/kotlin/org/mocoserver/recipedump/cli/SearchArguments.kt: 検索引数の解析。
- 変更cli/src/test/kotlin/org/mocoserver/recipedump/cli/CliTest.kt: 新コマンドと既存機能の受け入れ。
- 変更README.md、docs/design.md、docs/dump-format.md、docs/verification.md: 使用法・契約・実測。

### Task 1: coreの検索API

**Interfaces:**
- Consumes: `DumpJson.decode(text: String): JsonObject`で検証済みのsnapshot。
- Produces: `public object DumpSearch`。
- `public fun recipes(dump: JsonObject, id: String? = null, type: String? = null, namespace: String? = null): JsonObject`。
- `public fun itemsByTag(dump: JsonObject, tag: String): JsonObject`。

- [x] **Step 1: 失敗する検索テストを書く。** ID順が逆の3行（create:a、create_extra:b、minecraft:c）を作り、typeとserializerのnamespaceがIDと異なる行を含める。`recipes`の全件順、ID／type／namespace単独検索、AND、不一致、部分ID不一致を確認する。未知フィールド保持と入力JSON不変も検証する。

```kotlin
assertEquals(listOf("create:a"), ids(DumpSearch.recipes(dump, namespace = "create")))
assertEquals(0, DumpSearch.recipes(dump, id = "a").get("count").asInt)
assertEquals(0, DumpSearch.recipes(dump, id = "create:a", type = "minecraft:smelting").get("count").asInt)
```

`ids(result: JsonObject): List<String>`はテスト内private helper。create:aのtypeはminecraft:craftingとする。

- [x] **Step 2: タグテストを書く。** minecraft:planksにoak_planks、birch_planks、oak_planksをこの順で入れる。昇順2件、tagフィールド、未知／空タグの0件を確認する。参照対象タグのobject値、数値要素、null要素は`assertThrows(IllegalArgumentException::class.java)`。別タグだけが不正の場合は検索対象タグの結果に影響させない。
- [x] **Step 3: `./gradlew :core:test --tests '*DumpSearchTest'`を実行する。** 新API未実装によるコンパイル失敗を確認する。
- [x] **Step 4: DumpSearchを実装する。** recipesはID昇順、行全体をdeepCopyして`recipes`配列と`count`を返す。itemsByTagはタグ未登録を空配列とし、登録済みの参照対象値を厳密に検証、重複除去と昇順後に`tag`・`items`・`count`を返す。namespaceは最初のコロンの位置で区切る。IDにコロンがない行はnamespace検索では一致させない。
- [x] **Step 5: `./gradlew :core:test :core:check`を実行する。** 新規検索と既存テストが成功し、core依存境界検査が通ることを確認する。
- [x] **Step 6: Task 1のファイルだけをコミットする。** メッセージ: `feat: add recipe and item tag search in core`。

### Task 2: CLIコマンドと引数契約

**Interfaces:**
- Consumes: Task 1のDumpSearch API、既存`runCli(args: List<String>, out: PrintStream, err: PrintStream): Int`。
- Produces: 既存runCliの署名を維持してrecipes／itemsを提供する。
- SearchArguments.ktに`internal data class SearchArguments(val id: String? = null, val type: String? = null, val namespace: String? = null, val tag: String? = null)`を置く。
- `internal fun parseSearchArguments(command: String, args: List<String>): SearchArguments`。argsはコマンド・ファイル名を除いたオプション部分。

- [x] **Step 1: 新CLIの成功テストを書く。** TempDirの完全ダンプにTask 1相当のレシピとタグを保存。recipes全件、各フィルター、AND、items --tag、0件で終了コード0・stderr空、stdoutがcoreと同じJSON結果であることを確認する。短いIDは補完されず0件とする。
- [x] **Step 2: 引数と入力失敗テストを書く。** 未知／重複オプション、値不足、`--id --type minecraft:crafting`、空値、余分な位置引数、itemsのtagなし、recipesのtag指定、itemsのid指定を拒否する。存在しないファイル名でも引数エラーが先に出ることを確認する。不完全ダンプは1・stdout空・stderr非空。
- [x] **Step 3: 既存コマンドの回帰テストを追加する。** 同じfixtureでsummaryの件数、csvのヘッダーとdata、同一ダンプdiffの空配列を確認し、既存不正JSON・I/Oテストを残す。
- [x] **Step 4: `./gradlew :cli:test --tests '*CliTest'`を実行する。** 新コマンドの成功テストが現在の使用法拒否で失敗することを確認する。
- [x] **Step 5: parseSearchArgumentsとMain.ktの振り分けを実装する。** recipesはid／type／namespaceのみ任意、itemsはtagのみ必須。オプションは`--name value`形式に限定し、値が`--`から始まる場合も拒否する。ファイル読み込み前に全引数を検証する。エラー文はUsageを含める。summary／csvは2引数、diffは3引数のままにする。
- [x] **Step 6: `./gradlew :core:test :cli:test ktlintCheck`を実行する。** 全テストと整形検査の成功を確認する。必要な整形修正は`./gradlew ktlintFormat`後に同じ検証を再実行する。
- [x] **Step 7: Task 2のファイルだけをコミットする。** メッセージ: `feat: expose recipe and tag search commands in CLI`。

### Task 3: 文書・配布CLI・必須検証

**Interfaces:**
- Consumes: Task 2の実装と`./gradlew :cli:installDist`で生成する実行可能CLI。
- Produces: READMEの検索使用法、現在設計と検索契約、実測に基づくverification記録。

- [x] **Step 1: 文書を更新する。** READMEにrecipes／itemsの使用例、AND／完全一致／namespaceの対象／0件／tag必須を記載。designにcore検索の担当範囲を追加。dump-formatにschema 1を変更しないことと参照対象タグの検証を記載。将来の材料・出力・装置名検索を実装済みと書かない。
- [x] **Step 2: `./gradlew check :neoforge:build :neoforge:verifyModJar :cli:distZip :cli:installDist`を実行する。** BUILD SUCCESSFULと各検査の成功を確認する。生成物をGitへ追加しない。
- [x] **Step 3: `./gradlew :neoforge:runGameTestServer`を実行する。** 終了後に`node scripts/check-gametest-result.mjs`を実行し、必須2件の成功を確認する。0件や失敗を成功扱いしない。
- [x] **Step 4: `node --test scripts/test/*.test.mjs`を実行する。** 失敗0件を確認する。
- [x] **Step 5: 実ダンプの検証入力を確保する。** .local／build内の既存実ダンプを優先し、ない場合は検証用専用サーバー環境を確認して新規の出力先へ収集する。必要な環境がなければ未検証として理由を記録し、作成したfixtureを実ダンプと呼ばない。通常配布JAR実行を行った場合はGameTestと区別して記録する。
- [x] **Step 6: 配布CLIで検索し照合する。** 実ダンプから存在するID・type・namespace・タグを選び、CLI結果とJSONの単純なID／type／namespace絞り込み、タグのsort／unique結果を比較する。recipes全件と各フィルター、AND、既知／未知タグ、空結果を確認し、.localに結果と照合コマンドを保存する。
- [x] **Step 7: verificationへ実測を追記する。** 実行環境、単体テスト件数、ビルド、GameTest件数、script件数、配布CLIの実データ照合、未検証事項を別々に記載する。収集・停止は変更していないことを記録する。
- [x] **Step 8: 最終差分を仕様と照合し、レビュー指摘を解決する。** code-review／requesting-code-reviewの該当手順を読み、現環境で可能なレビューを実施する。変更が生じた場合は関連テスト・checkを再実行する。
- [x] **Step 9: `git diff --check`を実行して文書と検証記録をコミットする。** メッセージ: `docs: document and verify CLI search commands`。実装完了・実データ未検証などの状態を区別してユーザーへ報告する。
