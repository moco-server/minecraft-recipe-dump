# Changesetsでリリースする

通常MOD JARとCLI ZIPは同じバージョンで公開する。公開のタイミングは、メンテナーがChangesetsのリリースPRを `main` へマージした時点で決まる。

## 変更をリリースに含める

1. Node.js 24とpnpm 12.8.1で `pnpm install --frozen-lockfile --ignore-scripts` を実行する。
2. 配布に含めたい変更のブランチで `pnpm changeset` を実行し、版の上げ幅（patch／minor／major）と利用者向けの変更内容を入力する。生成された `.changeset/*.md` を変更と同じPRに含める。
3. `main` に変更が入ると、Changesetsが `changeset-release/main` のPRを作成・更新する。PRには今回分の変更履歴、`package.json` の新しい版、同じ版の `gradle.properties` の `mod_version` が入る。
4. 版と変更履歴を確認し、通常配布JARの検証を終えたら、このPRをマージする。[Release workflow](../../.github/workflows/release.yml) がマージコミットで [Linux・Windowsの検証](../../.github/workflows/verify.yml) を実行する。両方が成功すると、そのコミットに `v<version>` タグを作成する。

初回用のpatch changesetは開発版 `0.1.0-dev` を `0.1.0` に更新する。通常の変更PRのマージ、changesetがないpush、タグの手動pushでは配布物を公開しない。

## 配布物と取得先

GitHub Releaseには、同じ実行のLinuxジョブで検証した通常MOD JARとCLI ZIPを添付する。Releaseノートには今回のChangesetsの変更履歴を使う。Pre-releaseにはLatestを付けない。

Release公開後、各ファイルをそのReleaseからダウンロードし、以下の座標でGitHub Packagesへ登録する。

| Release添付ファイル | Maven座標 | 形式 |
| --- | --- | --- |
| `minecraft_recipe_dump-<version>.jar` | `org.mocoserver.recipedump:minecraft-recipe-dump:<version>` | jar |
| `recipe-dump-<version>.zip` | `org.mocoserver.recipedump:recipe-dump-cli:<version>` | zip |

MavenリポジトリのURLは `https://maven.pkg.github.com/moco-server/minecraft-recipe-dump`。CLIをMavenから取得するときはtypeに `zip` を指定する。Packagesからの取得には、パッケージの読み取り権限を持つ認証情報が必要になる。[GitHub Mavenレジストリの認証](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry)

## GitHubの設定と復旧

リポジトリの **Settings → Actions → General → Workflow permissions** で **Allow GitHub Actions to create and approve pull requests** を有効にする。組織側でもPR作成、タグ作成、Mavenパッケージ作成を許可しておく。各ジョブはリポジトリの `GITHUB_TOKEN` を使い、PR作成、Release公開、Packages登録に必要な権限を個別に要求する。

検証が失敗した場合はReleaseを作成しない。添付中に失敗した場合はdraftが残る。原因を解消して、該当する **Release** 実行で **Re-run failed jobs** を選ぶ。同じコミットのタグと既存draftから再開できる。公開済みのReleaseの添付ファイルは上書きしない。

Packagesの登録はJARとZIPを別々のジョブで行う。一方が失敗した場合も **Re-run failed jobs** で失敗したジョブだけを再実行できる。既に同じ座標・版が登録されている場合は実物を確認する。配布物を修正する場合は新しいchangesetで次の版を公開する。
