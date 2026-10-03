# 第2段階: 全登録アイテム Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 全登録アイテムの一覧とnamespace検索を、新旧ダンプ互換性を保って提供する。

**Architecture:** SnapshotCollectorが登録キーを収集し、coreが任意itemsを検証・検索する。CLIはtag必須制約を解除し、一覧欠落を再収集の必要として表示する。

**Tech Stack:** Kotlin/JVM、Gson、JUnit、NeoForge、Gradle Kotlin DSL。

**Spec:** [承認済み設計](../specs/2026-10-03-item-inventory-design.md)

## Global Constraints

- Minecraft 1.21.1／NeoForge 21.1.252／Java 21。coreはpure Kotlin/JVM、cliにMinecraft／KFF／MOD個別APIの実行時依存を入れない。
- 生codec JSON・未知フィールド・明示nullを保持する。schemaVersion 1、completeと入力解析成功を区別する。
- Kotlinの明示的core API、最小可視性、日本語の理由・契約コメントを維持する。
- 私が順次実装し、後続3段階の完了時に新しいレビュアー1名が全体を確認する。CodeRabbitへの外部送信は行わない。
- 実装は隔離worktreeで行う。既存ワールドを上書きせず、生成物とログは.local／buildに置く。
- itemsは任意フィールド。完全修飾IDの重複を拒否し、minecraft:airも収集する。既存のatomic置換とhalt(false)を維持する。

## Review Focus

1. 旧ダンプのタグ＋namespaceは動作し、全件一覧は再収集エラーになる（Task 2）。
2. items:null、重複、誤型、不正IDは欠落と区別して拒否する（Task 1）。
3. ソート前の一覧・空配列を受理し、未参照アイテムも検索する（Task 1）。
4. 独自JSONのnullが保存・置換後にも残る（Task 1）。
5. 通常JARで保存失敗時に成功マーカーや不完全な置換が発生しない（Task 3）。

---

### Task 1: coreの一覧検証・検索・null保存

**Files:** Modify `core/src/main/kotlin/org/mocoserver/recipedump/core/{DumpJson,DumpSearch}.kt`; Create `core/src/main/kotlin/org/mocoserver/recipedump/core/ItemIds.kt`; Test `core/src/test/kotlin/org/mocoserver/recipedump/core/{DumpReportsTest,DumpSearchTest}.kt`。

**Interfaces:** internal `isItemId(value: String): Boolean`を共通化。public `DumpSearch.items(dump: JsonObject, tag: String? = null, namespace: String? = null): JsonObject`を追加。既存`itemsByTag(dump, tag)`は互換ラッパーとして維持する。

- [x] 失敗テスト追加: `inventoryValidation`はnull／非配列／非文字列／重複／空／大文字／コロン欠落を拒否し、`["test:z", "minecraft:air"]`と`[]`と欠落は受理。`inventorySearch`はタグにない`test:z`を返し、namespace完全一致・昇順・0件・結果deepCopyを確認。
- [x] `atomicSavePreservesNull`で既存出力を置換した後も`data.custom:null`が残ること、保存失敗で既存内容が維持されることを検査。
- [x] `./gradlew :core:test`で新しいアサーションが失敗することを確認。
- [x] `ItemIds.kt`に`[a-z0-9_.-]+:[a-z0-9/._-]+`判定を実装。DumpJson.decodeでitemsが存在する時だけ検証し、writerにserializeNullsを設定。DumpSearch.itemsはtagがあれば旧タグ集合、なければ必須inventoryを使い、namespaceをAND適用する。欠落時は再収集を案内するIllegalArgumentException。tag／namespaceキーは指定時だけ付ける。
- [x] `./gradlew :core:test`で新旧テスト全件成功を確認。
- [x] 対象ファイルをcommit: `feat: validate and search item inventory`。

### Task 2: CLIの一覧・namespace検索

**Files:** Modify `cli/src/main/kotlin/org/mocoserver/recipedump/cli/{Main,SearchArguments}.kt`; Test `cli/src/test/kotlin/org/mocoserver/recipedump/cli/CliTest.kt`; Modify `README.md`, `docs/{design,dump-format}.md`。

**Interfaces:** Task 1の`DumpSearch.items`。SearchArgumentsの既存tag／namespaceを使用し、itemsで両方を任意にする。

- [x] `itemInventoryCommands`に全件、namespace、tag＋namespace、未知0件を追加。`oldDumpInventoryCompatibility`は旧タグ検索の結果形維持・旧タグ＋namespace成功・全件一覧stderr再収集案内とexit1を検査。`invalidItemOptionsBeforeIo`は重複・余分・欠値・空値を存在しないファイルより先に診断する。
- [x] `./gradlew :cli:test`で新ケースの失敗を確認。
- [x] parser／usage／Mainを更新し、itemsからTask 1のAPIを呼ぶ。metadata-only recipes／summary／csv／diffの形を維持。READMEと形式契約へ任意items、namespaceの意味、旧ダンプの境界を記載。
- [x] `./gradlew :cli:test :core:test`で全件成功を確認。
- [x] 対象ファイルをcommit: `feat: expose item inventory searches in CLI`。

### Task 3: registry収集と通常JAR受け入れ

**Files:** Modify `neoforge/src/main/kotlin/org/mocoserver/recipedump/SnapshotCollector.kt`, `neoforge/src/gametest/kotlin/org/mocoserver/recipedump/tests/RecipeDumpGameTests.kt`, `docs/verification.md`, `docs/superpowers/specs/2026-10-03-item-inventory-design.md`。

**Interfaces:** 既存`SnapshotCollector.collect(server: MinecraftServer): JsonObject`の同一snapshotへitemsを追加。core／CLIのTask 1–2契約を消費。

- [x] GameTest `collectsRegisteredItems`を追加し、一覧がregistry keySetと完全一致、重複なし、昇順、airとdiamondありを検査。lookup／assembleの既存テストも残す。
- [x] `./gradlew :neoforge:runGameTestServer`でitems欠落により失敗することを確認。
- [x] collect内でBuiltInRegistries.ITEM.keySetをID化・ソートしてitemsへ追加。列挙失敗は握りつぶさず既存FAILED経路へ渡す。recipe単位codecエラー、取得タイミング・有効化・停止は維持する。
- [x] 共通検証を実行し、GameTest新規ケースも実際に実行されたことを確認。
- [x] .localに新規通常JARサーバー環境を作る。固定MOD版Create 6.0.10／Mekanism 10.7.19.85／FD 1.3.4で有効化時ダンプ＋停止、未指定時継続、既存出力置換、保存失敗を実測する。独自レシピ・上書き・除外・実行時変更の既存fixtureも新形式で再検証する。
- [x] 配布CLIで新旧ダンプのTask 2ケースを独立JSON集計と照合。アイテム件数／namespace件数、保存成功マーカー、終了・失敗ログを別々に記録し、設計状態を実装済みへ更新。
- [x] 対象ファイルをcommit: `feat: collect registered item inventory`。

## 共通検証

各段階の受け入れで以下をJava 21で実行し、終了コードと実際の結果を確認する。

```sh
./gradlew check :neoforge:build :neoforge:verifyModJar :cli:distZip
./gradlew :neoforge:runGameTestServer
node scripts/check-gametest-result.mjs
node --test scripts/test/*.test.mjs
```

期待値: 全task成功、GameTestの必須件数が正かつ失敗0、Node全件成功。配布CLIの照合には`./gradlew :cli:installDist`の生成物を使う。Gradleには既存の`/tmp/recipe-dump-cli-search/.local/gradle`キャッシュを利用する。整形は`./gradlew ktlintFormat`。ビルド、GameTest、通常JAR、CLI照合を混同せずdocs/verification.mdへ別々に記録する。
