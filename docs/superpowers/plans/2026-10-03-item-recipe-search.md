# 第3段階: 材料・出力検索 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 明示対応したバニラserializerで材料・出力を検索し、未判定行を診断付きで返す。

**Architecture:** core内の三値評価と版付きserializer adapterで必要方向だけを解析する。DumpSearchがメタデータ絞り込みと集計、CLIが引数と0／1／2終了コードを担当する。

**Tech Stack:** Kotlin/JVM、Gson、JUnit、NeoForge、Gradle Kotlin DSL。

**Spec:** [承認済み設計](../specs/2026-10-03-item-recipe-search-design.md)

## Global Constraints

- Minecraft 1.21.1／NeoForge 21.1.252／Java 21。coreはpure Kotlin/JVM、cliにMinecraft／KFF／MOD個別APIの実行時依存を入れない。
- 生codec JSON・未知フィールド・明示nullを保持する。schemaVersion 1、completeと入力解析成功を区別する。
- Kotlinの明示的core API、最小可視性、日本語の理由・契約コメントを維持する。
- 私が順次実装し、後続3段階の完了時に新しいレビュアー1名が全体を確認する。CodeRabbitへの外部送信は行わない。
- 実装は隔離worktreeで行う。既存ワールドを上書きせず、生成物とログは.local／buildに置く。
- 第2段階完了後に実行。items一覧は材料検索の前提にしない。collector／schemaは変更しない。
- 解析版はminecraft=1.21.1。未判定を不一致と扱わず、候補件数をanalyzed／unsupported／unresolvedに一度だけ分割する。

## Review Focus

1. ORの既知一致・ANDの既知不一致が未知枝より優先される（Task 1／3）。
2. 空タグと欠落タグ、空ingredientと空の非空必須入力列を区別する（Task 1／2）。
3. 独自材料があっても出力方向だけの検索は実行できる（Task 2）。
4. 型不正・null・小数／非正countが0件へ黙って落ちない（Task 2）。
5. メタデータ0件はexhaustive=true、部分検索はstdout JSONとexit2になる（Task 3）。

---

### Task 1: 三値評価と材料評価

**Files:** Create `core/src/main/kotlin/org/mocoserver/recipedump/core/RecipeAnalysis.kt`, `IngredientAnalysis.kt`; Create `core/src/test/kotlin/org/mocoserver/recipedump/core/IngredientAnalysisTest.kt`。

**Interfaces:** internal enum `MatchState { MATCH, NO_MATCH, UNKNOWN }`; internal data class `AnalysisIssue(code: String, path: String? = null)`; internal data class `MatchEvaluation(state: MatchState, evidence: List<JsonObject> = emptyList(), issues: List<AnalysisIssue> = emptyList())`。internal `anyMatch(values: List<MatchEvaluation>): MatchEvaluation`／`allMatch(values: List<MatchEvaluation>): MatchEvaluation`。internal `evaluateIngredient(value: JsonElement?, dump: JsonObject, item: String, path: String, role: String = "ingredient"): MatchEvaluation`。

- [ ] `threeValuedCombinations`でOR真優先／AND偽優先・全既知・未知を検査。`ingredientShapesAndTags`でitem、tag、OR配列、空配列、空タグ、欠落タグ、無関係な不正タグ、誤型・item＋tag・type付き材料を検査し、path／role／tag根拠を固定する。
- [ ] `./gradlew :core:test --tests '*IngredientAnalysisTest'`で未実装により失敗を確認。
- [ ] 上記型と関数を実装。OR空はNO_MATCH。tagは要求されたタグのみ検査し、不正ID／値はinvalid_shape、欠落はmissing_tag、type付きはunsupported_ingredient。根拠は要求item一致だけ。三値合成後に確定した結果から不要な不明診断を除く。
- [ ] 同コマンドで全ケース成功を確認。
- [ ] commit: `feat: evaluate ingredient matches with explicit unknowns`。

### Task 2: バニラserializerの方向別adapter

**Files:** Create `core/src/main/kotlin/org/mocoserver/recipedump/core/VanillaRecipeAnalysis.kt`, `ItemOutputAnalysis.kt`; Create `core/src/test/kotlin/org/mocoserver/recipedump/core/VanillaRecipeAnalysisTest.kt`; Create fixtures under `core/src/test/resources/recipes/vanilla/`（serializer名ごとのJSON）。

**Interfaces:** internal `evaluateVanillaRecipe(dump: JsonObject, recipe: JsonObject, uses: String?, produces: String?): MatchEvaluation`。internal `evaluateItemOutput(value: JsonElement?, item: String, path: String): MatchEvaluation`はid／count形式の出力を読む。Task 1型を使用し、入力と出力の結果をallMatchで合成する。

- [ ] 対象版codec由来の小さいJSON fixtureを全9 serializer（shaped、shapeless、4調理、stonecutting、smithing_transform、smithing_trim）から抽出。原snapshotとserializer／版を対応づけ、fixtureに出所をREADMEとして残す。
- [ ] `vanillaDirections`で各serializerの一致・不一致と根拠pathを検査。`shapedUsedKeys`は未使用keyを無視し、使用key欠落をinvalid_shape。`independentDirections`は未知入力＋既知出力、trim入力一致＋出力unsupported_directionを検査。`invalidOutputShapes`は欠落／null／文字列result／小数・0・負count／不正IDをUNKNOWNと検査。空非空必須入力列と版欠落／違い、未知serializerも固定する。
- [ ] `./gradlew :core:test --tests '*VanillaRecipeAnalysisTest'`で失敗を確認。
- [ ] 明示serializerホワイトリストを実装。要求方向だけ読む。shapedはpatternの非空白使用文字、smithingは3入力、その他は設計表のfieldを読む。count省略は1、正整数必須。componentsを解析条件にせず、生JSONを変更しない。minecraft版不一致はunsupported_version、表外はunsupported_serializer。
- [ ] 同テストと`:core:test`で成功を確認。
- [ ] commit: `feat: analyze vanilla recipe inputs and outputs`。

### Task 3: 検索集計・CLI・配布受け入れ

**Files:** Modify `core/src/main/kotlin/org/mocoserver/recipedump/core/DumpSearch.kt`, `cli/src/main/kotlin/org/mocoserver/recipedump/cli/{Main,SearchArguments}.kt`; Test `core/src/test/kotlin/org/mocoserver/recipedump/core/DumpSearchTest.kt`, `cli/src/test/kotlin/org/mocoserver/recipedump/cli/CliTest.kt`; Modify `README.md`, `docs/{design,dump-format,verification}.md`, stage3 spec。

**Interfaces:** `DumpSearch.recipes(dump: JsonObject, id: String? = null, type: String? = null, namespace: String? = null, uses: String? = null, produces: String? = null): JsonObject`へ末尾引数追加。internal SearchArgumentsへuses／produces追加。新core public `DumpSearch.isItemId(value: String): Boolean`は第2段階のinternal判定へ委譲し、CLIがI/O前に呼ぶ。

- [ ] `materialSearchPartition`でcandidate=analyzed+unsupported+unresolved、一致countはanalyzed以下、両原因はunresolved、診断ID／path／code順、一致行だけの根拠、行deepCopyとnull保持を検査。`metadataFilteringBeforeAnalysis`は未知形式除外後0件がexhaustive=true、材料条件なしの旧形不変を検査。`cliMaterialExitCodes`は完全0／エラー1／部分2とstderr空、ID構文エラーのI/O前拒否、旧ダンプitems欠落でも解析可能を検査。
- [ ] `./gradlew :core:test :cli:test`で新ケースの失敗を確認。
- [ ] metadata選択を先に適用してTask 2評価を呼ぶ。未判定行のみdiagnostics、一致行のみevidence、材料指定時のみanalysisを追加する。CLIはexhaustive=falseでexit2、通常エラーは既存exit1。Gson serializeNullsを維持。
- [ ] `./gradlew :core:test :cli:test`で全件成功を確認。検索仕様と終了コード、候補と実作成可否の違い、未知材料の境界を文書へ追加。
- [ ] 共通検証を実行。配布CLIで実snapshotのsmelting入力／出力、diamond_block、材料AND、全件の未対応件数を独立集計と照合。serializer別件数と未検証範囲を記録し、stage3状態を実装済みにする。
- [ ] commit: `feat: expose diagnostic recipe material searches`。

## 共通検証

各段階の受け入れで以下をJava 21で実行し、終了コードと実際の結果を確認する。

```sh
./gradlew check :neoforge:build :neoforge:verifyModJar :cli:distZip
./gradlew :neoforge:runGameTestServer
node scripts/check-gametest-result.mjs
node --test scripts/test/*.test.mjs
```

期待値: 全task成功、GameTestの必須件数が正かつ失敗0、Node全件成功。配布CLIの照合には`./gradlew :cli:installDist`の生成物を使う。Gradleには既存の`/tmp/recipe-dump-cli-search/.local/gradle`キャッシュを利用する。整形は`./gradlew ktlintFormat`。ビルド、GameTest、通常JAR、CLI照合を混同せずdocs/verification.mdへ別々に記録する。
