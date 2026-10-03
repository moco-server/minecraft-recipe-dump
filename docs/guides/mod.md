# Minecraft Recipe Dump — 収集MODの利用ガイド

専用サーバーで実際に読み込まれたレシピを収集し、JSONファイルへ保存するMODです。ゲーム画面やプレイヤー接続は不要です。アイテム・装置・ゲーム進行は追加しません。

保存したJSONを調べる手順は [CLI利用ガイド](cli.md)、プロジェクト全体とソースからのビルドは [ルートREADME](../../README.md) を参照してください。

## 入手と必要環境

MODの配布先はCurseForge／Modrinthを予定しています。本ガイドにはまだ本MODの公開配布ページへのリンクを掲載していません。公開前に利用する場合は、[開発手順](../../README.md#開発)で通常MOD JARをビルドしてください。

| 必要なもの | 初期対応・検証構成 |
| --- | --- |
| Minecraft専用サーバー | 1.21.1 |
| Loader | NeoForge 21.1.252 |
| Java | 21 |
| 実行時依存MOD | KotlinForForge 5.5.0 |

配布ページではMinecraft版とLoaderを確認し、通常MOD JARを選びます。sources JARはサーバーに導入するファイルではありません。KotlinForForgeも別途必要です。詳しい対応根拠は [対応構成](../reference/compatibility.md)、実測と未検証範囲は [検証記録](../verification/README.md) を参照してください。

## 専用サーバーへの導入と収集

1. 対応する専用サーバーの**新規検証環境またはコピー**を用意します。対象MOD、設定、スクリプト、データパックも収集したい構成に合わせます。
2. Minecraft Recipe Dumpの通常MOD JARと、[KotlinForForge 5.5.0の配布JAR](https://modrinth.com/mod/kotlin-for-forge/version/tdvjylfz)を `mods/` に置きます。開発用Maven aggregate JARとは別物です。
3. EULAの確認など、通常のサーバー初回セットアップを済ませます。
4. JVMオプションを付けて起動します。`-D` はメインクラス／起動引数ファイルより前に置きます。

```sh
java -Drecipedump.enabled=true -Drecipedump.output=/absolute/path/recipes.json \
  @user_jvm_args.txt @libraries/net/neoforged/neoforge/21.1.252/unix_args.txt nogui
```

Windows（コマンドプロンプト）では `win_args.txt` を使用します。

```bat
java -Drecipedump.enabled=true -Drecipedump.output=C:\dumps\recipes.json @user_jvm_args.txt @libraries\net\neoforged\neoforge\21.1.252\win_args.txt nogui
```

サーバーのフォルダーから実行してください。例の出力先は自分の保存先に置き換えます。空白を含む引数はshellの規約に従って引用します。

最初のサーバーティック終了時にレシピ、登録アイテム、アイテムタグを収集し、JSON保存後に通常のワールド保存・停止を行います。ワールド生成とポート待受は通常どおり発生します。オプションなしでは収集・自動停止を行わず、クライアントでも収集を行いません。

既定の出力先はサーバー作業ディレクトリの `recipes.json`。既存ファイルは完成したダンプで置き換えます。成功時は `RECIPE_DUMP_SAVED` とrunId、失敗時は `RECIPE_DUMP_FAILED` をログに出します。**保存失敗でも正常停止の終了コードは0になり得ます。** 実行ごとに新しい出力パスを使い、今回のログとダンプを照合してください。

## 今回の収集が成功したか確認する

1. 今回の起動ログに `RECIPE_DUMP_SAVED` があることを確認します。`RECIPE_DUMP_FAILED` がある場合は成功として扱いません。
2. 指定した出力ファイルの `runId` とログのrunIdを照合し、`complete` が `true` であることを確認します。以前のファイルが残っているだけでは今回の成功を示しません。
3. 入力レシピの解析エラーはサーバーログも確認します。ダンプ保存成功と、入力全件の読み込み成功は別です。
4. 保存したファイルをCLIで集計・検索します。サーバーとは別のPCへコピーして処理できます。

## ダンプが保証する範囲

schemaVersion 1の `recipes` は現在のRecipeManagerのスナップショットです。各レシピはID、RecipeType、serializer、再シリアライズしたdataを持ちます。登録アイテム一覧、解決済みアイテムタグ、MOD版一覧、選択データパック順序、取得段階、runId、取得エラーも保存します。

`complete` は列挙したレシピの書き出し・codec再読み込みが成功したことを示します。**入力レシピすべての読み込み成功ではありません。** 対象版の読み込みエラーフラグだけでは失敗を判定できないため、`loadDiagnostics` は `not-tracked` としています。入力の解析エラーはサーバーログでも確認してください。

元JSONの条件や既定値の書き方は保持されないことがあります。RecipeManager外の独自加工、JEI／EMI専用表示、後のティックやreloadでの変更、他Loader・他Minecraft版は今回の保証範囲に含みません。

出力フィールドの詳細は [ダンプ形式](../reference/dump-format.md) を参照してください。

## うまく動かない場合

- 起動に失敗する場合は、Minecraft／NeoForge／Javaの版とKotlinForForgeの配布JARを確認します。
- サーバーが通常稼働を続ける場合は、`-Drecipedump.enabled=true` がJVMオプションとして渡されているか確認します。起動引数ファイルより後ろに置かないでください。
- 保存に失敗する場合は、`RECIPE_DUMP_FAILED` と前後のログ、出力先の権限や空き容量を確認します。修正後は新しい出力パスで再実行します。
- レシピが見つからない場合は、対象MOD・設定・スクリプト・データパックがサーバー側にも揃っているか、入力解析エラーがログにないか確認します。RecipeManager外の加工は収集対象に含みません。
