# テンプレート由来の基盤

moco-server/minecraft-mod-templateの0.1.0（生成時main dfee13176ce3ec8e64d27dfdd12376a7cfd58b6b）から独立repoを作成した。.template-initialized.jsonへ識別情報を記録済み。初期化scriptは再実行しない。

現在の製品設計は [アーキテクチャ](../design/architecture.md)。テンプレートのitem、recipe、datagen、数量サンプルは削除し、coreのダンプ処理・CLI・収集MOD・GameTestへ置き換えた。JAR内容検査とCIも実機能に合わせる。

基盤を更新するときは、固定依存・品質検査・skills等の必要な差分だけを取り込み、このrepoの識別情報と契約を維持する。テンプレートのサンプルを復活させない。初期化scriptとfixturesのテストは、継承した開発ツールの検証として残している。
