# Muxport

Muxport is a tmux + WebSocket + mobile infrastructure tool.
It makes tmux sessions reachable from your phone.

Concept documents:

- English: `docs/CONCEPT.md`
- Japanese: `docs/CONCEPT.ja.md`
- Architecture: `docs/10.architecture/`

Muxport consists of two components:

- `gateway/`: Starlette-based API for tmux session management, WebSocket terminal, push notifications, and webhook handling
- `frontend/`: Android terminal client to access tmux sessions from mobile

## Scope

Muxport owns runtime-oriented capabilities:

- terminal access
- tmux session lifecycle
- runtime-facing push notifications

## Repository Layout

```text
plexus/
├── docs/        # terminal, FCM, webhook, and deployment notes
├── frontend/    # Android terminal client (KMP + Compose Multiplatform)
├── gateway/     # Starlette-based runtime API
└── maestro/     # E2E flows for terminal UI
```

## Development

### Gateway

```bash
# 手動起動（開発用）
cd /root/workspace/plexus
tmux new-session -d -s 'uv run --project gateway python -m gateway.main'

# systemd での自動起動（本番推奨）
sudo bash gateway/deploy/plexus install
# → OS 起動時に自動で立ち上がる
# 詳細: docs/40.deploy/gateway-systemd.md
```

### Frontend

```bash
cd /root/workspace/plexus/frontend
./gradlew :androidApp:assembleDebug
```

### Tests

```bash
cd /root/workspace/plexus
uv run pytest tests -v

cd /root/workspace/plexus/frontend
./gradlew :shared:testDebugUnitTest
```
