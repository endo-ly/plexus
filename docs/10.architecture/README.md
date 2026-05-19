# Muxport アーキテクチャ

## 概要

Muxport のアーキテクチャ設計をまとめた章。
tmux を中心としたランタイムと、それに接続するフロントエンド / ゲートウェイの責務境界を整理する。

## ドキュメント構成

### 01-overview

| ドキュメント | 内容 |
| --- | --- |
| [system-architecture](./01-overview/system-architecture.md) | Muxport 全体像、主要コンポーネント、実行時のつながり |
| [tech-stack](./01-overview/tech-stack.md) | コンポーネント別の技術スタックと役割 |

### 02-frontend

| ドキュメント | 内容 |
| --- | --- |
| [architecture](./02-frontend/architecture.md) | Android ターミナルクライアントの MVVM / DI / 状態管理設計 |
| [terminal](./02-frontend/terminal.md) | セッション一覧、ターミナル接続、設定画面の機能設計 |

### 03-gateway

| ドキュメント | 内容 |
| --- | --- |
| [architecture](./03-gateway/architecture.md) | ランタイム API、WebSocket、プッシュ通知、tmux 連携の設計 |

## 設計原則

- tmux セッションを実行の中心として扱う
- ターミナル層とランタイム API を別責務として分離する
- フロントエンドはモバイルターミナルクライアントに責務を絞る
- オーケストレーションへの拡張を妨げない境界を保つ
