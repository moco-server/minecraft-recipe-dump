# 設計・計画の履歴

以下は完了した作業の設計案と実装計画。本文には撤回した方針や当時の実行指示も残っている。現在の実装契約は [アーキテクチャ](../design/architecture.md)、[ダンプ形式](../reference/dump-format.md)、[CLI検索・差分仕様](../reference/cli-search.md) を参照する。

| 作業（2026-10-03） | 当時の設計 | 当時の実装計画 | 状態 |
| --- | --- | --- | --- |
| CLI検索の全体方針・第1段階 | [設計](specs/2026-10-03-cli-search-design.md) | [計画](plans/2026-10-03-cli-search.md) | 第1〜4段階実装・検証済み |
| 第2段階: 登録アイテム一覧 | [設計](specs/2026-10-03-item-inventory-design.md) | [計画](plans/2026-10-03-item-inventory.md) | 実装・検証済み |
| 第3段階: 材料・出力検索 | [設計](specs/2026-10-03-item-recipe-search-design.md) | [計画](plans/2026-10-03-item-recipe-search.md) | 実装・検証済み。版gateは撤回 |
| 第4段階: MOD形式・装置検索 | [設計](specs/2026-10-03-mod-recipe-station-search-design.md) | [計画](plans/2026-10-03-mod-recipe-station-search.md) | 実装・検証済み。版gate・版条件付き装置一覧は撤回 |

撤回の理由と後継方針は [設計判断](../design/decisions.md#cliをjsonのインターフェースとして扱う変更2026-10-03)、実測は [検証記録](../verification/README.md) に記録している。未チェックの計画項目は当時の記録であり、現在の未完了作業一覧として使わない。
