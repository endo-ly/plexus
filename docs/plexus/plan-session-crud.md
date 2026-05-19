# Plan: セッション CRUD（作成・削除）

フロントエンドから tmux セッションの作成・削除を行えるようにする。Gateway API に POST/DELETE エンドポイントを追加し、Frontend の UI・State・Repository を拡張する。

設計書: [docs/session-crud.md](../session-crud.md)

> **Note**: 以下の具体的なコード例・API 設計・構成（How）はあくまで参考である。実装時によりよい設計方針があれば積極的に採用すること。

## 設計方針

- **tmux を真実ソースとする** — DB は使用せず、`subprocess.run` で `tmux new-session` / `tmux kill-session` を直接呼ぶ。既存の `list_sessions` / `session_exists` と同じパターン。
- **既存パターンに従う** — Gateway: `verify_gateway_token` + `_validate_session_id` + `anyio.to_thread.run_sync`。Frontend: `RepositoryClient.post/delete` + `wrapRepositoryOperation` + `StateFlow/Channel`。
- **セッション名は自由入力 + 自動採番** — ユーザーが空入力の場合 `session-\d+` の最大値+1 を自動採番。入力値の検証は Gateway 側に委ねる。
- **TDD で下から上へ** — Infrastructure → Domain → API → Repository → ScreenModel → UI の順で依存関係に沿って実装する。

## Plan スコープ

WT作成 → 実装(TDD) → コミット(意味ごとに分離) → 端末実機確認(ADB) → PR作成

## 対象一覧

| 対象 | 実装元 |
| --- | --- |
| `gateway/infrastructure/tmux.py` | `create_session()`, `kill_session()` |
| `gateway/domain/models.py` | `CreateSessionRequest` |
| `gateway/api/terminal.py` | `POST /sessions`, `DELETE /sessions/{id}` ハンドラ |
| `frontend/.../RepositoryClient.kt` | `delete()` メソッド |
| `frontend/.../TerminalRepository.kt` | `createSession()`, `deleteSession()` interface |
| `frontend/.../TerminalRepositoryImpl.kt` | 上記の実装 |
| `frontend/.../AgentListState.kt` | `isCreatingSession`, `deletingSessionIds` |
| `frontend/.../AgentListEffect.kt` | `SessionCreated`, `SessionDeleted` |
| `frontend/.../AgentListScreenModel.kt` | `createSession()`, `deleteSession()`, `suggestSessionName()` |
| `frontend/.../AgentListScreen.kt` | Effect 処理追加 |
| `frontend/.../SessionList.kt` | ヘッダー [+] アイコン + Dialog |
| `frontend/.../SessionListItem.kt` | 長押しコンテキストメニュー |

---

## Step 0: Worktree 作成

`/worktree-create` スキルを使用して WT を作成する。

---

## Step 1: Gateway infrastructure/tmux.py — create_session / kill_session (TDD)

### RED: テスト先行

| テストケース | 内容 |
| --- | --- |
| `test_create_session_success` | `tmux new-session -d -s test-session` 成功時に Session を返す |
| `test_create_session_with_working_dir` | `-c /tmp` 付きでコマンドが呼ばれることを検証 |
| `test_create_session_tmux_not_installed` | `FileNotFoundError` → `OSError("tmux is not installed")` |
| `test_create_session_timeout` | `TimeoutExpired` → `OSError` |
| `test_create_session_command_fails` | `CalledProcessError` → そのまま raise |
| `test_kill_session_success` | `tmux kill-session -t =name` 成功時に例外なし |
| `test_kill_session_verifies_exact_match` | `=` プレフィックス付きで完全一致することを検証 |
| `test_kill_session_tmux_not_installed` | `FileNotFoundError` → `OSError` |
| `test_kill_session_timeout` | `TimeoutExpired` → `OSError` |

### GREEN: 実装

`infrastructure/tmux.py` に `create_session(session_name, working_dir=None)` と `kill_session(session_name)` を追加。

- `create_session`: `tmux new-session -d -s {name} [-c {working_dir}]` → `list_sessions()` から該当 Session を引いて返す
- `kill_session`: `tmux kill-session -t ={name}`（`=` で完全一致）
- エラーハンドリングは既存 `list_sessions` / `session_exists` のパターンに従う

### コミット

`feat(gateway): add create_session and kill_session to tmux infrastructure`

---

## Step 2: Gateway domain/models.py — CreateSessionRequest (TDD)

前提: Step 1

### RED: テスト先行

| テストケース | 内容 |
| --- | --- |
| `test_create_session_request_valid` | `session_id` + `working_dir` が正常にパースされる |
| `test_create_session_request_without_working_dir` | `working_dir` 省略時 `None` になる |
| `test_create_session_request_missing_session_id` | `session_id` 欠落で `ValidationError` |
| `test_create_session_request_empty_session_id` | 空文字で `ValidationError` |
| `test_create_session_request_session_id_too_long` | 101文字で `ValidationError` |

### GREEN: 実装

`domain/models.py` に `CreateSessionRequest(BaseModel)` を追加。

```python
class CreateSessionRequest(BaseModel):
    """セッション作成リクエスト。"""
    session_id: str = Field(..., min_length=1, max_length=100, description="セッション名")
    working_dir: str | None = Field(None, description="初期作業ディレクトリ")
```

### コミット

`feat(gateway): add CreateSessionRequest domain model`

---

## Step 3: Gateway api/terminal.py — POST / DELETE エンドポイント (TDD)

前提: Step 1, Step 2

### RED: テスト先行

| テストケース | 内容 |
| --- | --- |
| `test_create_session_returns_201` | 正常作成時に 201 + セッション情報を返す |
| `test_create_session_conflict_409` | 既存セッション名で 409 を返す |
| `test_create_session_invalid_name_400` | 不正なセッション名で 400 を返す |
| `test_create_session_unauthorized_401` | API Key なしで 401（既存 `verify_gateway_token` の責務） |
| `test_create_session_tmux_failure_500` | tmux コマンド失敗時に 500 を返す |
| `test_delete_session_returns_204` | 正常削除時に 204（body なし）を返す |
| `test_delete_session_not_found_404` | 存在しないセッションで 404 を返す |
| `test_delete_session_invalid_name_400` | 不正なセッション名で 400 を返す |
| `test_delete_session_unauthorized_401` | API Key なしで 401 |
| `test_delete_session_tmux_failure_500` | tmux コマンド失敗時に 500 を返す |

### GREEN: 実装

`api/terminal.py` に `create_session()` と `delete_session()` ハンドラを追加し、`get_terminal_routes()` にルートを登録。

- `POST /v1/terminal/sessions` → `create_session()` → 201
- `DELETE /v1/terminal/sessions/{session_id}` → `delete_session()` → 204
- 認証: `verify_gateway_token`
- バリデーション: `_validate_session_id`
- tmux 呼び出し: `anyio.to_thread.run_sync`

### コミット

`feat(gateway): add POST and DELETE session endpoints`

---

## Step 4: Frontend RepositoryClient + TerminalRepository (TDD)

前提: Step 3（API が存在する前提でモックテスト）

### RED: テスト先行

| テストケース | 内容 |
| --- | --- |
| `RepositoryClient delete sends DELETE request` | `delete<T>()` が HTTP DELETE を送信する |
| `RepositoryClient delete throws HttpError on non-2xx` | 非 2xx で `ApiError.HttpError` を投げる |
| `TerminalRepository createSession calls POST` | `createSession()` が正しいパス・body で POST を呼ぶ |
| `TerminalRepository deleteSession calls DELETE` | `deleteSession()` が正しいパスで DELETE を呼ぶ |
| `TerminalRepository deleteSession encodes sessionId` | セッションIDがURLエンコードされる |

### GREEN: 実装

1. `RepositoryClient.kt` に `delete<T>(path)` を追加（`get`/`post`/`put` と同じパターン）
2. `TerminalRepository.kt` interface に `createSession()`, `deleteSession()` を追加
3. `TerminalRepositoryImpl.kt` に実装を追加
4. 成功時にキャッシュをクリア

### コミット

`feat(frontend): add createSession and deleteSession to TerminalRepository`

---

## Step 5: Frontend State / Effect / ScreenModel (TDD)

前提: Step 4

### RED: テスト先行

| テストケース | 内容 |
| --- | --- |
| `AgentListState default isCreatingSession is false` | 初期状態の `isCreatingSession` が `false` |
| `AgentListState default deletingSessionIds is empty` | 初期状態の `deletingSessionIds` が空 |
| `AgentListState updating isCreatingSession` | `isCreatingSession` の更新 |
| `AgentListState updating deletingSessionIds` | `deletingSessionIds` の追加・削除 |
| `suggestSessionName returns session-01 when no sessions` | セッションなし時に `session-01` を返す |
| `suggestSessionName increments from max` | `session-01` → `session-02` |
| `suggestSessionName ignores non-matching sessions` | `agent-0001` などを無視して `session-01` を返す |
| `suggestSessionName handles gaps` | `session-01, session-03` → `session-04` |

### GREEN: 実装

1. `AgentListState.kt` — `isCreatingSession: Boolean`, `deletingSessionIds: Set<String>` を追加
2. `AgentListEffect.kt` — `SessionCreated(session)`, `SessionDeleted(sessionId)` を追加
3. `AgentListScreenModel.kt` — `createSession()`, `deleteSession()`, `suggestSessionName()` を追加

### コミット

`feat(frontend): add session create/delete logic to AgentListScreenModel`

---

## Step 6: Frontend UI — SessionList ヘッダー + Dialog (TDD)

前提: Step 5

### RED: テスト先行

Compose UI のテストは Robolectric / Compose Test Rule が必要なため、ロジック部分に絞る。

| テストケース | 内容 |
| --- | --- |
| `CreateSessionDialog empty input uses suggested name` | 空入力時に suggestSessionName の値が使われる |
| `CreateSessionDialog non-empty input uses entered name` | 入力値がそのまま使われる |

### GREEN: 実装

1. `SessionList.kt` — ヘッダー行に `Icons.Default.Add` の `IconButton` を追加
2. `SessionList.kt` — `[+]` タップ時に `AlertDialog` を表示する state を追加
3. Dialog 内容: テキストフィールド（placeholder: 自動採番名）+ Cancel / Create ボタン
4. `isCreatingSession = true` の間は `[+]` ボタンと Create ボタンを disabled にする
5. Create 成功時に Dialog を閉じ、`loadSessions()` で一覧を更新

### コミット

`feat(frontend): add session creation dialog to SessionList header`

---

## Step 7: Frontend UI — SessionListItem 長押しコンテキストメニュー (TDD)

前提: Step 5

### RED: テスト先行

| テストケース | 内容 |
| --- | --- |
| `SessionListItem shows context menu on long press` | 長押しでメニューが表示される |
| `SessionListItem delete triggers confirmation` | 削除選択時に確認ダイアログが表示される |
| `SessionListItem is disabled while deleting` | `deletingSessionIds` に含まれるセッションは disabled 表示 |

### GREEN: 実装

1. `SessionListItem.kt` — `combinedClickable(onLongClick = ...)` に変更
2. 長押しで `DropdownMenu` を表示（削除オプション）
3. 削除選択 → 確認 `AlertDialog` → `onDeleteSession(sessionId)` コールバック
4. `deletingSessionIds` に含まれるセッションはカード全体を `enabled = false` にする
5. `SessionList.kt` → `SessionListContent` → `SessionListItem` に `onLongClick` / `onDeleteSession` を伝播

### コミット

`feat(frontend): add long-press delete context menu to SessionListItem`

---

## Step 8: 自動テスト・Lint 通過確認

### Gateway

```bash
cd /root/workspace/plexus
uv run pytest gateway/tests/unit -v
uv run ruff check gateway/
uv run ruff format --check gateway/
```

### Frontend

```bash
cd /root/workspace/plexus/frontend
./gradlew :shared:testDebugUnitTest
./gradlew ktlintCheck
./gradlew detekt
```

---

## Step 9: 端末実機確認（ADB Install）

前提: ADB 接続済み。`/android-adb-debug` スキルを使用。

### 1. Gateway 起動確認

```bash
# Gateway が起動していることを確認
bash gateway/deploy/plexus status
```

起動していない場合:

```bash
tmux new-session -d -s 'uv run --project gateway python -m gateway.main'
```

### 2. Debug APK ビルド & インストール

```bash
cd /root/workspace/plexus/frontend
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:installDebug
```

### 3. ADB でアプリ起動・動作確認

```bash
# アプリ起動
adb shell am start -n dev.muxport.androidapp/.MainActivity

# ログ監視
adb logcat -s Plexus
```

### 4. 手動確認項目

- [ ] セッション一覧画面のヘッダーに [+] アイコンが表示される
- [ ] [+] タップ → 作成ダイアログが開く（placeholder に自動採番名が表示される）
- [ ] 空入力で Create → 自動採番名でセッションが作成される（一覧が更新される）
- [ ] 名前入力で Create → 入力した名前でセッションが作成される
- [ ] 重複名で Create → エラーメッセージが表示される
- [ ] セッションカードを長押し → コンテキストメニュー（削除）が表示される
- [ ] 削除選択 → 確認ダイアログ → 実行 → セッションが一覧から消える
- [ ] 存在しないセッションの削除 → エラーメッセージが表示される
- [ ] 作成中・削除中のローディング状態が適切に表示される

### コミット

動作確認はコミット対象なし。不具合が見つかった場合は追加 Step で修正。

---

## Step 10: PR 作成

```bash
gh pr create --title "feat: session CRUD from frontend" --body "$(cat <<'EOF'
## Summary

- Gateway: セッション作成 (POST)・削除 (DELETE) API エンドポイントを追加
- Frontend: セッション一覧画面に作成ダイアログ・長押し削除メニューを追加

## Test plan

- [x] Gateway: `uv run pytest gateway/tests/unit -v` 全通過
- [x] Gateway: `ruff check` / `ruff format` 通過
- [x] Frontend: `./gradlew :shared:testDebugUnitTest` 全通過
- [x] Frontend: `ktlintCheck` / `detekt` 通過
- [x] 端末実機確認: ADB install → セッション作成・削除のE2E確認
EOF
)"
```

---

## 変更ファイル一覧

| ファイル | 変更種別 | 内容 |
| --- | --- | --- |
| `gateway/infrastructure/tmux.py` | 変更 | `create_session()`, `kill_session()` 追加 |
| `gateway/domain/models.py` | 変更 | `CreateSessionRequest` 追加 |
| `gateway/api/terminal.py` | 変更 | POST/DELETE ハンドラ + ルート追加 |
| `gateway/tests/unit/test_tmux.py` | 変更 | create/kill テスト追加 |
| `gateway/tests/unit/test_models.py` | 変更 | `CreateSessionRequest` テスト追加 |
| `gateway/tests/unit/test_terminal_api.py` | 変更 | POST/DELETE ハンドラテスト追加 |
| `frontend/.../internal/RepositoryClient.kt` | 変更 | `delete()` メソッド追加 |
| `frontend/.../TerminalRepository.kt` | 変更 | interface に create/delete 追加 |
| `frontend/.../TerminalRepositoryImpl.kt` | 変更 | 実装追加 |
| `frontend/.../AgentListState.kt` | 変更 | `isCreatingSession`, `deletingSessionIds` 追加 |
| `frontend/.../AgentListEffect.kt` | 変更 | `SessionCreated`, `SessionDeleted` 追加 |
| `frontend/.../AgentListScreenModel.kt` | 変更 | `createSession()`, `deleteSession()`, `suggestSessionName()` 追加 |
| `frontend/.../AgentListScreen.kt` | 変更 | Effect 処理追加 |
| `frontend/.../SessionList.kt` | 変更 | ヘッダー [+] アイコン + Dialog 追加 |
| `frontend/.../SessionListItem.kt` | 変更 | 長押しコンテキストメニュー追加 |
| `frontend/.../agentlist/AgentListStateTest.kt` | 変更 | 新規フィールドのテスト追加 |
| `docs/session-crud.md` | **新規** | 設計書 |
| `docs/plexus/plan-session-crud.md` | **新規** | 本 Plan |

---

## コミット分割

1. `feat(gateway): add create_session and kill_session to tmux infrastructure` — `gateway/infrastructure/tmux.py`, `gateway/tests/unit/test_tmux.py`
2. `feat(gateway): add CreateSessionRequest domain model` — `gateway/domain/models.py`, `gateway/tests/unit/test_models.py`
3. `feat(gateway): add POST and DELETE session endpoints` — `gateway/api/terminal.py`, `gateway/tests/unit/test_terminal_api.py`
4. `feat(frontend): add createSession and deleteSession to TerminalRepository` — `RepositoryClient.kt`, `TerminalRepository.kt`, `TerminalRepositoryImpl.kt`, テスト
5. `feat(frontend): add session create/delete logic to AgentListScreenModel` — `AgentListState.kt`, `AgentListEffect.kt`, `AgentListScreenModel.kt`, テスト
6. `feat(frontend): add session creation dialog to SessionList header` — `SessionList.kt`, `AgentListScreen.kt`
7. `feat(frontend): add long-press delete context menu to SessionListItem` — `SessionListItem.kt`, `SessionList.kt`
8. `docs: add session CRUD design and plan` — `docs/session-crud.md`, `docs/plexus/plan-session-crud.md`

※ Step 9（端末実機確認）はコミットなし。不具合修正時は追加コミット。

---

## テストケース一覧（全 37 件）

### Gateway infrastructure/tmux (9)

1. `test_create_session_success` — `tmux new-session` 成功時に Session を返す
2. `test_create_session_with_working_dir` — `-c` オプション付きで呼ばれる
3. `test_create_session_tmux_not_installed` — `FileNotFoundError` → `OSError`
4. `test_create_session_timeout` — `TimeoutExpired` → `OSError`
5. `test_create_session_command_fails` — `CalledProcessError` を raise
6. `test_kill_session_success` — 成功時に例外なし
7. `test_kill_session_verifies_exact_match` — `=` プレフィックスで完全一致
8. `test_kill_session_tmux_not_installed` — `FileNotFoundError` → `OSError`
9. `test_kill_session_timeout` — `TimeoutExpired` → `OSError`

### Gateway domain/models (5)

10. `test_create_session_request_valid` — 正常パース
11. `test_create_session_request_without_working_dir` — `working_dir` 省略時 `None`
12. `test_create_session_request_missing_session_id` — `ValidationError`
13. `test_create_session_request_empty_session_id` — 空文字 `ValidationError`
14. `test_create_session_request_session_id_too_long` — 101文字 `ValidationError`

### Gateway api/terminal (10)

15. `test_create_session_returns_201` — 正常作成 201
16. `test_create_session_conflict_409` — 重複 409
17. `test_create_session_invalid_name_400` — 不正名 400
18. `test_create_session_unauthorized_401` — 認証なし 401
19. `test_create_session_tmux_failure_500` — tmux 失敗 500
20. `test_delete_session_returns_204` — 正常削除 204
21. `test_delete_session_not_found_404` — 存在せず 404
22. `test_delete_session_invalid_name_400` — 不正名 400
23. `test_delete_session_unauthorized_401` — 認証なし 401
24. `test_delete_session_tmux_failure_500` — tmux 失敗 500

### Frontend Repository (5)

25. `RepositoryClient delete sends DELETE request` — DELETE メソッド送信
26. `RepositoryClient delete throws HttpError on non-2xx` — エラー時 `ApiError.HttpError`
27. `TerminalRepository createSession calls POST` — POST 呼び出し
28. `TerminalRepository deleteSession calls DELETE` — DELETE 呼び出し
29. `TerminalRepository deleteSession encodes sessionId` — URL エンコード

### Frontend State / ScreenModel (8)

30. `AgentListState default isCreatingSession is false` — 初期値確認
31. `AgentListState default deletingSessionIds is empty` — 初期値確認
32. `AgentListState updating isCreatingSession` — 更新確認
33. `AgentListState updating deletingSessionIds` — 追加・削除確認
34. `suggestSessionName returns session-01 when no sessions` — セッションなし
35. `suggestSessionName increments from max` — 採番インクリメント
36. `suggestSessionName ignores non-matching sessions` — 非マッチ除外
37. `suggestSessionName handles gaps` — 歯抜け対応

---

## 工数見積もり

| Step | 内容 | 見積もり |
| --- | --- | --- |
| Step 0 | Worktree 作成 | ~10 行 |
| Step 1 | Gateway tmux infra | ~100 行（実装 40 + テスト 60） |
| Step 2 | Gateway domain model | ~40 行（実装 10 + テスト 30） |
| Step 3 | Gateway API endpoints | ~120 行（実装 60 + テスト 60） |
| Step 4 | Frontend Repository | ~80 行（実装 40 + テスト 40） |
| Step 5 | Frontend State/Effect/ScreenModel | ~100 行（実装 60 + テスト 40） |
| Step 6 | Frontend SessionList UI | ~80 行（実装 60 + テスト 20） |
| Step 7 | Frontend SessionListItem UI | ~60 行（実装 40 + テスト 20） |
| Step 8 | 自動テスト・Lint | 0 行（確認のみ） |
| Step 9 | 端末実機確認（ADB） | 0 行（確認のみ） |
| **合計** | | **~590 行** |
