# 第4段階: MOD形式・装置検索 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 固定版の初期MOD形式と版付き装置対応表を追加する。

**Architecture:** 第3段階の評価を小さなMOD別adapterへ拡張し、NeoForge材料の集合演算を共通化する。装置は独立した版付きtype表で選択し、stationだけの検索ではcodecを解析しない。

**Tech Stack:** Kotlin/JVM、Gson、JUnit、NeoForge、Gradle Kotlin DSL。

**Spec:** [承認済み設計](../specs/2026-10-03-mod-recipe-station-search-design.md)

## Global Constraints

- Minecraft 1.21.1／NeoForge 21.1.252／Java 21。coreはpure Kotlin/JVM、cliにMinecraft／KFF／MOD個別APIの実行時依存を入れない。
- 生codec JSON・未知フィールド・明示nullを保持する。schemaVersion 1、completeと入力解析成功を区別する。
- Kotlinの明示的core API、最小可視性、日本語の理由・契約コメントを維持する。
- 私が順次実装し、後続3段階の完了時に新しいレビュアー1名が全体を確認する。CodeRabbitへの外部送信は行わない。
- 実装は隔離worktreeで行う。既存ワールドを上書きせず、生成物とログは.local／buildに置く。
- 第3段階完了後に実行。collector／schema／依存は変更しない。
- adapter条件: minecraft=1.21.1、mods.versionはcreate=6.0.10／mekanism=10.7.19／farmersdelight=1.3.4。Mekanism実測JARは10.7.19.85だけ、ヘッダーからbuildを判別できない。

## Review Focus

1. 未知の集合演算の子を空集合へ変換しない（Task 1）。
2. fluid／chemical IDがアイテムIDと同じでも未対応serializerを解析しない（Task 2）。
3. FDのchance=0を不可能とせず、設定・fortuneの条件付き候補にする（Task 2）。
4. Mek副出力だけ・chance境界とCreate保持入力を正しく区別する（Task 2）。
5. 旧ダンプの装置検索、版違い・未知装置、動的recipe・0件を区別する（Task 3）。

---

### Task 1: NeoForge複合材料

**Files:** Modify `core/src/main/kotlin/org/mocoserver/recipedump/core/IngredientAnalysis.kt`, `core/src/test/kotlin/org/mocoserver/recipedump/core/IngredientAnalysisTest.kt`。

**Interfaces:** 第3段階`evaluateIngredient`の署名と三値評価を維持。内部再帰でneoforge:compound／intersection／differenceのみ追加。

- [x] `compoundIngredientOperations`でcompound children（alias ingredients）OR、intersection children AND、difference base／subtractedを検査。未知子＋真／偽／未知の組合せ、空・欠落・誤型fields、item_ability、data component／block tag未知も検査。differenceはbase真＋subtracted未知でUNKNOWN、base偽ならNO_MATCH。
- [x] `./gradlew :core:test --tests '*IngredientAnalysisTest'`で新ケースの失敗を確認。
- [x] NeoForge 21.1.252生成ソースcodecと固定MOD実JSONを照合して正確なfield名を実装。指定3type以外はunsupported_ingredient、ORの既知一致を維持し未知子を捨てない。
- [x] 同テストで全件成功を確認。
- [x] commit: `feat: analyze NeoForge compound ingredients`。

### Task 2: 固定版MOD別adapter

**Files:** Create `core/src/main/kotlin/org/mocoserver/recipedump/core/{ModRecipeAnalysis,CreateRecipeAnalysis,MekanismRecipeAnalysis,FarmersDelightRecipeAnalysis}.kt`; Modify `ItemOutputAnalysis.kt`, `DumpSearch.kt`（同じcoreディレクトリ）; Create `core/src/test/kotlin/org/mocoserver/recipedump/core/ModRecipeAnalysisTest.kt`; Create fixture JSONと出所README under `core/src/test/resources/recipes/mods/`。

**Interfaces:** internal `evaluateModRecipe(dump: JsonObject, recipe: JsonObject, uses: String?, produces: String?): MatchEvaluation`が版とserializerを選択。internal `evaluateCreateRecipe`／`evaluateMekanismRecipe`／`evaluateFarmersDelightRecipe`は同じ署名。Task 1・第3段階のMatchEvaluationと出力評価を利用。DumpSearchはvanilla namespaceのserializerをvanilla adapter、それ以外をMOD adapterへ渡す（レシピIDのnamespaceは選択条件にしない）。

- [x] 固定JARのcodec／bytecodeと実snapshotを照合し、設計の全serializerから入力・出力fixtureを抽出。全fieldとchance条件を出所READMEに記録し、推測が必要な形式は対応を追加しない。
- [x] `modSerializerDirections`を設計表全serializerの入力／出力でparameterize。`modVersionSelection`で欠落・不一致版、別namespaceのレシピID、同versionでも不正形を検査。`unsupportedFluidAndSequence`は同じIDを含むfluid／chemical／sequencedをunsupported_serializerと検査。
- [x] `probabilityAndInputRoles`でCreate chance省略1、0以下不正、1以上有効、deploying第2入力keep_held_item時retained-inputを検査。Mek正整数input count、sawing副出力chance必須(0,1]、主なしなら<1、両出力なし／副なしchance不正を検査。FD cutting tool根拠、chance省略1と0候補、finite数値のみ、base-chance＋conditions、cooking container除外を検査。未知入力でも出力解析が独立することを確認。
- [x] `./gradlew :core:test --tests '*ModRecipeAnalysisTest'`で新ケースが失敗することを確認。
- [x] 設計表のfieldと版だけを実装。Createのper-item-roll、Mek secondary-output、FD base-chanceをevidenceへ付け、countは宣言数量として保持。FD result配列のitemを専用に読む。chance型／finite／範囲とMek出力組合せを検証。MOD APIを呼ばず、共通出力評価の拡張は既存バニラ契約を維持する。
- [x] `./gradlew :core:test`で全件成功を確認。
- [x] commit: `feat: analyze pinned Create Mekanism and Farmers Delight recipes`。

### Task 3: 装置対応表・CLI・全段階受け入れ

**Files:** Create `core/src/main/kotlin/org/mocoserver/recipedump/core/RecipeStations.kt`, `core/src/test/kotlin/org/mocoserver/recipedump/core/RecipeStationsTest.kt`; Modify `core/src/main/kotlin/org/mocoserver/recipedump/core/DumpSearch.kt`, `cli/src/main/kotlin/org/mocoserver/recipedump/cli/{Main,SearchArguments}.kt`, `cli/src/test/kotlin/org/mocoserver/recipedump/cli/CliTest.kt`, `README.md`, `docs/{design,dump-format,verification}.md`, stage4／全体spec。

**Interfaces:** internal `stationTypes(dump: JsonObject, station: String): Set<String>`は未知・版不適合をIllegalArgumentException。public `DumpSearch.stations(dump: JsonObject): JsonObject`。`DumpSearch.recipes`の末尾へ`station: String? = null`を追加。SearchArgumentsへstation追加。CLI `stations <dump.json>`は追加引数を認めない。

- [x] `versionedStationTable`は設計表の全17装置とRecipeTypeを期待値として照合し、ID順、旧items欠落、版欠落・違い、未知完全ID、MOD版で一覧選択を検査。`stationSearchComposition`はtype集合OR＋他条件AND、材料部分exit2、stationのみ動的行を含む、既知0件正常、station／stationTypes根拠を検査。`cliStationUsage`は全件一覧・未知装置exit1、無効IDのI/O前拒否・余分引数拒否を検査。
- [x] `./gradlew :core:test :cli:test`で新ケースの失敗を確認。
- [x] coreに設計表だけを版付き定義として実装（装置能力の推測なし）。metadataフィルターへstation typesをAND適用し、station指定時だけ根拠キー追加。CLI usage／stations dispatchを更新。minecraft欠落／非対応は装置検索エラーにし、材料検索の部分結果契約は維持する。
- [x] 同テストで成功を確認。README／検索契約へ対応版・serializer・装置一覧と未対応範囲、確率・保持入力・工具の意味、Mek build判別不能を記載する。
- [x] 共通検証を実行し、配布CLIで固定MOD snapshotの全adapterと装置検索を独立照合する。serializerごとのanalyzed／unsupported／unresolved、根拠・部分終了コードを記録。第1〜3段階の旧コマンドとnull保持も回帰確認し、spec状態を更新する。
- [x] commit: `feat: expose versioned recipe station searches`。
- [x] 全3段階のdiff・受け入れログを新しいレビュアー1名へ渡す。重要指摘をRED→GREENで修正し、未修正の軽微事項を記録。review後に統合方法を既存のユーザー承認範囲と照らして提示する。

## 共通検証

各段階の受け入れで以下をJava 21で実行し、終了コードと実際の結果を確認する。

```sh
./gradlew check :neoforge:build :neoforge:verifyModJar :cli:distZip
./gradlew :neoforge:runGameTestServer
node scripts/check-gametest-result.mjs
node --test scripts/test/*.test.mjs
```

期待値: 全task成功、GameTestの必須件数が正かつ失敗0、Node全件成功。配布CLIの照合には`./gradlew :cli:installDist`の生成物を使う。Gradleには既存の`/tmp/recipe-dump-cli-search/.local/gradle`キャッシュを利用する。整形は`./gradlew ktlintFormat`。ビルド、GameTest、通常JAR、CLI照合を混同せずdocs/verification.mdへ別々に記録する。
