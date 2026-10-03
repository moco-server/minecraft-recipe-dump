# GitHub Releaseの作成

[Verify and Release workflow](../../.github/workflows/verify.yml) は、`v*` タグのpushで検証し、成功した成果物をGitHub Releaseへ添付する。ブランチpush・PR・手動のworkflow_dispatchでは検証とActions artifactの保存だけを行う。

## 版とタグ

`gradle.properties` の `mod_version` をSemVerへ更新し、その変更と配布するコードをコミットする。タグは `v<mod_version>` と完全一致させる。例えば `mod_version=0.1.0` なら `v0.1.0`、`mod_version=0.1.0-rc.1` なら `v0.1.0-rc.1`。現在の開発版 `0.1.0-dev` もPre-releaseとして扱う。

版の準備と通常配布JARの検証を終えたら、対象コミットにタグを作成してpushする。以下は `mod_version=0.1.0` に更新済みの場合の例。

```sh
git tag -a v0.1.0 -m "Release 0.1.0"
git push origin v0.1.0
```

CDはタグと版番号の不一致、不正な版形式をビルド前に拒否する。公開前の通常JAR・専用サーバー検証は [検証の現状](../verification/README.md) とrepoの作業規約に従う。CDのGameTestは通常配布JARの実行検証を代替しない。

## 公開する成果物

Linux・Windows両方の検証が成功した後、Linux jobの同一workflow実行から成果物をダウンロードする。再ビルドはしない。

- `minecraft_recipe_dump-<version>.jar`: 内容検査済みの通常MOD JAR。sources・GameTest JARは添付しない。
- `recipe-dump-<version>.zip`: Java 21用のCLI起動script・依存JAR・利用ガイド `README.md`・LICENSE。

ダウンロード後に版番号付きファイル名、2ファイルだけであること、通常ファイルで空でないことを確認する。CLIガイドの関連リンクは、同じ `v<version>` タグのGitHub上の文書へ変換する。repoが非公開の場合は文書リンクにもrepoへのアクセス権が必要だが、同梱の利用手順はローカルで読める。

ReleaseノートはGitHubの自動生成を使う。通常版はRelease、`-dev`／`-rc.1` 等の付いた版はPre-releaseとし、Pre-releaseはLatestにしない。追加secretは不要で、公開jobだけに `GITHUB_TOKEN` の `contents: write` を付与する。

## 失敗と再実行

公開はdraft作成 → 2成果物の添付 → draft解除の順に進める。添付・公開が失敗した場合はdraftを残し、同じタグのworkflowを再実行して復旧できる。既存draftの同名添付は置換するが、公開済みReleaseを再実行で上書きしない。公開済み版の修正は新しい版とタグで行う。

タグの検証失敗、どちらかのOSの検証失敗、成果物欠落・混入は公開jobの実行または公開処理を停止する。Actionsの失敗jobのログを確認する。

実装の根拠: [workflowのタグ条件・権限・job依存](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax)、[gh release create](https://cli.github.com/manual/gh_release_create)、[gh release upload](https://cli.github.com/manual/gh_release_upload)、[gh release edit](https://cli.github.com/manual/gh_release_edit)。
