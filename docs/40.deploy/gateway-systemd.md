# Gateway systemd 自動起動

Plexus Gateway を systemd サービスとして登録し、OS 起動時に自動的に立ち上げる方法。

## 前提

- Linux（systemd 採用ディストリビューション）
- [uv](https://docs.astral.sh/uv/) がインストール済み
- Plexus リポジトリがクローン済み

## クイックスタート

```bash
cd /path/to/plexus
sudo bash gateway/deploy/plexus install
```

これでサービス登録、有効化、起動まで完了する。

## サブコマンド

| コマンド | 説明 |
|---|---|
| `sudo ./plexus install` | デフォルトパスでインストール・起動 |
| `sudo ./plexus install /opt/plexus` | パス指定でインストール |
| `sudo ./plexus uninstall` | 停止・登録解除 |
| `sudo ./plexus update` | コード更新 + 再起動 |
| `sudo ./plexus restart` | 再起動 |
| `./plexus logs [-f] [-n N]` | ログ表示 |
| `./plexus status` | 状態確認（root 不要） |

## 運用コマンド

```bash
# ログ追跡
sudo journalctl -u plexus-gateway -f

# 直近のログを確認
sudo journalctl -u plexus-gateway -n 50 --no-pager

# 再起動
sudo systemctl restart plexus-gateway

# 停止
sudo systemctl stop plexus-gateway
```

## 仕組み

### ファイル構成

```text
gateway/deploy/
├── plexus-gateway.service.tmpl   # systemd テンプレート
└── plexus                        # サービス管理CLI
```

### インストーラの動作

1. 前提チェック（`gateway/main.py` の存在、`uv` の存在）
2. テンプレートからサービスファイルを生成（`__WORKDIR__` / `__UV__` / `__USER__` を置換）
3. `/etc/systemd/system/plexus-gateway.service` に配置
4. `daemon-reload` → `enable` → `start`

### セキュリティ設定

サービスには以下の systemd セキュリティディレクティブを設定している:

| ディレクティブ | 効果 |
|---|---|
| `NoNewPrivileges=true` | 権限昇格禁止 |
| `ProtectSystem=strict` | ファイルシステムを読み取り専用に（`ReadWritePaths` のみ書き込み可） |
| `ReadWritePaths=<workdir> /tmp /run` | Gateway の作業ディレクトリと tmux runtime socket 用パスのみ書き込み可 |

### 環境変数

`gateway/.env` が存在する場合、`EnvironmentFile` ディレクティブで自動的に読み込まれる。
必要な環境変数は [gateway/README.md](../../gateway/README.md) を参照。
