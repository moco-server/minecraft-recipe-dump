# 文書案内

資料は読む目的で分ける。guides・reference・designと検証の現状は、実装に合わせて継続更新する。日付付きの実測記録とarchiveは、当時の情報を残す。

| 目的 | 文書 |
| --- | --- |
| 収集MODを導入してダンプを作る | [収集MOD利用ガイド](guides/mod.md) |
| ダンプをCLIで調べる | [CLI利用ガイド](guides/cli.md) |
| ソースからビルド・検証する | [READMEの開発手順](../README.md#開発) |
| 外部skillsを導入・更新する | [外部skills](guides/external-skills.md) |
| テンプレート由来の基盤を更新する | [基盤更新](guides/template-updates.md) |
| 保存JSONの契約を確認する | [ダンプ形式](reference/dump-format.md) |
| 検索結果JSON・評価・終了コード・差分を確認する | [CLI検索・差分仕様](reference/cli-search.md) |
| 対応構成と依存版の根拠を確認する | [対応構成](reference/compatibility.md) |
| 現在の構成・責務・失敗条件を理解する | [アーキテクチャ](design/architecture.md) |
| 採用理由と方針変更を確認する | [設計判断](design/decisions.md) |
| 検証済みの範囲と未検証項目を確認する | [検証の現状と記録](verification/README.md) |
| 完了した設計案・実装計画を調べる | [設計・計画の履歴](archive/README.md) |

## 書き分けと更新

- `guides/`: 読者が作業を進めるための手順、例、失敗時の対処を書く。詳細な仕様はreferenceへリンクする。
- `reference/`: 現在の仕様と互換性の契約を書く。挙動を変えたら実装・テストと同時に更新する。
- `design/`: 現在の責務・境界と、その採用理由を書く。撤回した判断には撤回と後継方針を明記する。実行ログや作業手順は置かない。
- `verification/README.md`: 現在の検証範囲と制限を要約し、根拠の記録へリンクする。
- `verification/records/YYYY-MM-DD-<topic>.md`: 構成・実行コマンド・観測結果・未実施項目を残す。過去の結果を現在の結果へ書き換えず、追加検証は新しい記録に残す。生ログ・JSON・ワールドは `.local/` またはbuildへ置く。
- `archive/`: 完了・撤回された設計案と計画を残す。現行仕様の参照先にはせず、冒頭で履歴と明示する。

進行中の設計案・計画は `docs/work/` に置き、冒頭に提案中・承認済み・実装中などの状態を記す。完了時は有効な契約と採用理由をreference／designへ反映し、実測をverificationへ記録してからarchiveへ移す。現在、進行中の文書はないため `work/` は作成していない。

新規文書を作ったらこの案内へ追加する。既存文書で説明できる内容はそちらを更新し、整理作業そのものの設計書・計画書は増やさない。移動時はREADME・AGENTS.md・repo内skills・scriptsと文書間リンクも確認する。
