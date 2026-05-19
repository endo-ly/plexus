# 要件定義: File Bridge — モバイルからのファイル閲覧・Git差分表示

## 1. Summary

- やりたいこと：muxport に、tmux セッションのワーキングディレクトリ配下のファイル閲覧と Git 差分表示機能を追加する
- 理由：ターミナルだけではモバイルでの情報スキャンに向かない。ファイルの中身をサッと見る、エラー箇所の diff を確認する、README を読むといった操作をモバイルで完結させたい
- 対象：Gateway（File API / Git API）+ Frontend（ファイルブラウザ / DocumentViewer）+ WebView（Markdown / Diff / Code 描画）
- 優先：高

## 2. Purpose (WHY)

- いま困っていること：
  - エージェントが編集したファイルの diff をモバイルから確認するには、ターミナルで `git diff` を打ってスクロールで探すしかない
  - README やログファイルの中身をサッと見たいだけなのに、ターミナルで `cat` して流れる出力を追う必要がある
  - 「何が変わったか」を俯瞰して把握する手段がモバイルにない
- できるようになったら嬉しいこと：
  - セッションのワーキングディレクトリをファイルマネージャー的にブラウズできる
  - Git のステータス・差分を構造化されたビューで確認できる
  - Markdown ファイルをレンダリング済みの見やすい形で読める
  - ソースコードをシンタックスハイライト付きで閲覧できる
- 成功すると何が変わるか：
  - muxport が「ターミナルだけ」から「ターミナル + ファイル・コード閲覧」の二刀流になる
  - モバイル単体でのコードレビュー・障害調査が実用レベルになる

## 3. Requirements (WHAT)

### 機能要件

#### ファイルブラウザ

- 対象ディレクトリ
  - 各 tmux セッションのアクティブペインの `pane_current_path` を起点とする
  - その配下のみアクセスを許可する（パストラバーサル防止）
- ディレクトリ一覧
  - 指定パス直下のファイル・ディレクトリ一覧を返す
  - 各エントリに名前・種別（file/directory/symlink）・サイズ・更新日時を含む
  - ドットファイル（`.` で始まるもの）はデフォルトで非表示とし、表示トグルで切替可能とする
  - `.git` ディレクトリは一覧に含めない
- ファイル読み取り
  - テキストファイルの内容を返す
  - ファイルサイズが閾値（例: 1MB）を超える場合は先頭部分のみ返し、残りは要求に応じて取得できるようにする
  - バイナリファイルは対応外とし、ファイル種別だけを返す
- セキュリティ
  - `os.path.realpath()` で正規化したパスが `pane_current_path` 配下にあることを検証する
  - シンボリックリンクによる外部ディレクトリへのエスケープを防止する

#### Git 統合

- 前提
  - `pane_current_path` が Git リポジトリ配下であること
  - リポジトリ外の場合は Git 関連 API は「not a git repository」を返す
- Git status
  - カレントブランチ名を返す
  - ステージ済み変更・未ステージ変更・未追跡ファイルの一覧を返す
  - 各ファイルの変更種別（added / modified / deleted / untracked）を含む
- Git diff
  - 指定対象の差分を返す（unstaged / staged / HEAD / 特定コミット）
  - ファイル単位での差分取得をサポートする
  - レスポンスにファイル単位の統計（additions / deletions）と raw patch テキストを含む
  - 大きな差分（例: 10K行超）はファイル単位で paginate または遅延読み込みする
- Git log
  - 直近コミットの一覧を返す
  - 各コミットに sha（短形式）・メッセージ・作者・日時を含む
- Git コミット詳細
  - 指定コミットの差分を返す（diff と同じフォーマット）

#### DocumentViewer（Markdown / Diff / Code 共通ビューア）

- 描画方式
  - WebView 上で JavaScript ライブラリにより描画する
  - ターミナル描画（xterm.js）と同じく、JS/CSS 資産をアプリ assets に同梱する
- 対応コンテンツタイプ
  - Markdown（`.md`）: Markdown → HTML 変換 + GitHub 風スタイル
  - Diff / Patch: side-by-side または unified 表示 + 行ハイライト
  - ソースコード（`.py` `.kt` `.js` `.ts` 等）: 構文ハイライト付き等幅表示
  - プレーンテキスト（`.log` `.txt` `.json` `.yaml` 等）: 等幅表示
  - バイナリ: 対応外（ファイル種別とサイズのみ表示）
- WebView 基盤
  - 既存の TerminalWebView のアセット配信パターン（`shouldInterceptRequest` によるローカル asset 返却）を流用する
  - Kotlin 側の JS Bridge から `render(type, content)` を呼び出してコンテンツを差し替える
- 同梱 JS ライブラリ
  - Markdown レンダラ（例: marked.js）
  - Diff レンダラ（例: diff2html）
  - 構文ハイライト（例: highlight.js）— Markdown 内のコードブロックとスタンドアロンコード表示の両方で使用
  - スタイリング（例: github-markdown-css）— Markdown の見た目統一用

### API 契約（ドラフト）

#### ファイルブラウザ API

```http
GET /api/v1/files/sessions/{session_id}/browse?path=.&show_hidden=false
  200: { path: "/home/user/project/src", entries: [{name, type, size, modified}] }
  400: invalid_path
  403: path_outside_workdir
  404: session_not_found / path_not_found

GET /api/v1/files/sessions/{session_id}/read?path=README.md
  200: { content: "...", language: "markdown", size: 1234, truncated: false }
  400: invalid_path
  403: path_outside_workdir
  404: session_not_found / file_not_found
  422: binary_file
```http

#### Git API

```http
GET /api/v1/git/sessions/{session_id}/status
  200: { branch: "main", staged: [{path, status}], unstaged: [{path, status}], untracked: [{path}] }
  404: session_not_found
  422: not_a_git_repo

GET /api/v1/git/sessions/{session_id}/diff?target=HEAD&path=src/main.py
  target: HEAD | staged | unstaged | <sha>
  path: 省略時は全ファイル
  200: { files: [{ path, additions, deletions, patch }] }
  404: session_not_found
  422: not_a_git_repo

GET /api/v1/git/sessions/{session_id}/log?count=10
  200: { commits: [{ sha, short_sha, message, author, date }] }
  404: session_not_found
  422: not_a_git_repo

GET /api/v1/git/sessions/{session_id}/commits/{sha}
  200: { commit: { sha, message, author, date }, diff: { files: [...] } }
  404: session_not_found / commit_not_found
  422: not_a_git_repo
```http

### 認証

- 全エンドポイントで既存の Bearer token 認証（`verify_gateway_token`）を適用する
- ファイルアクセスは session_id に紐づく `pane_current_path` 配下に制限する
- セッションが存在しない場合は 404 を返す

### 内部仕様

- Git コマンド実行
  - `git -C <path>` を使用し、ワーキングディレクトリを明示的に指定する
  - コマンドは `anyio.to_thread.run_sync` で非同期ラップする
  - タイムアウト（例: 5秒）を設定し、ハング防止する
- パス検証
  - `os.path.realpath()` でシンボリックリンクを解決し、絶対パスに正規化する
  - 許可ルートは `pane_current_path` とする（`os.path.realpath()` で同一正規化）
  - 正規化後のパスと許可ルートを `os.path.commonpath` で比較し、commonpath が許可ルートと一致することを検証する（文字列プレフィックス一致は `/work/app` vs `/work/app2` の誤許可を生むため避ける）
  - 末尾スラッシュやケース差異に依存しないよう、正規化後の `pathlib.PurePath` で統一的に扱う
- ファイル種別判定
  - MIME タイプまたは拡張子ベースでテキスト/バイナリを判定する
  - 判定不能な場合は先頭バイトをチェックする（NUL バイト含有 → バイナリ）

## 4. Scope

### 今回やる

- ファイルブラウザ（ディレクトリ一覧 + テキストファイル読み取り）
- Git status / diff / log / コミット詳細
- DocumentViewer（Markdown / Diff / Code / Plain Text）
- セキュリティ（パス検証 + 認証）

### 今回やらない（Won't）

- ファイル書き込み・編集
- ファイルアップロード・ダウンロード
- 画像・PDF 等のバイナリプレビュー
- Git 操作（commit / push / checkout 等）
- ログファイルのリアルタイム tail 追従
- 複数ペインの切り替え（MVP はアクティブペインのみ）
- オフラインキャッシュ

## 5. User Story Mapping

| Step | MVP（最低限） | Nice to have |
|---|---|---|
| 参照 | セッションのワーキングディレクトリをブラウズできる | ドットファイル表示切替 |
| 閲覧 | テキストファイルを閲覧できる | 大きなファイルのページネーション |
| Git確認 | 変更ファイル一覧と差分を確認できる | コミット間の比較 |
| Markdown | README 等をレンダリング表示できる | リンクをタップして別ファイルに遷移 |
| 導線 | セッション一覧からファイルブラウザに遷移できる | ターミナル画面からの直接遷移 |

## 6. Acceptance Criteria

- Given セッション `agent-0001` が存在する, When ファイルブラウザを開く, Then `agent-0001` のワーキングディレクトリ配下のファイル・ディレクトリ一覧を表示できる
- Given ワーキングディレクトリ配下に `README.md` がある, When そのファイルを選択する, Then Markdown をレンダリングした HTML として表示できる
- Given ワーキングディレクトリが Git リポジトリである, When Git status を表示する, Then ブランチ名・変更ファイル一覧・変更種別を確認できる
- Given 未ステージの変更がある, When 特定ファイルの diff を表示する, Then 変更箇所を行単位でハイライト表示できる
- Given ファイルパスに `../etc/passwd` のようなパストラバーサルを含む, When API を呼ぶ, Then 403 で拒否される
- Given セッションが存在しない, When ファイルブラウザ API を呼ぶ, Then 404 が返る
- Given ワーキングディレクトリが Git リポジトリではない, When Git API を呼ぶ, Then 422 で「not a git repository」が返る

## 7. 例外・境界

- バイナリファイル: 読み取り API は 422 を返し、ブラウザ上はアイコン＋ファイル種別のみ表示する
- 大きなファイル: 1MB 超のテキストファイルは先頭部分のみ返し、`truncated: true` を付与する
- 大きな diff: 差分が 10K 行を超えるファイルは、まず `--stat` のみ表示し、ファイル選択時に個別 diff を取得する
- シンボリックリンク: リンク先が許可パス配下か検証し、外を指す場合は辿らない
- Git リポジトリ外: Git API は全て 422 で応答する。ファイルブラウザは通常通り動作する
- エンコーディング: UTF-8 を前提とする。UTF-8 でないファイルは代替文字で置換して返す

## 8. Non-Functional Requirements

- Performance: ファイル一覧・テキスト読み取りは 1秒以内に応答する。diff はファイル数に依存するが 3秒以内を目標とする
- Security: パストラバーサル・シンボリックリンク攻撃を防止する。既存の認証基盤に乗る
- Usability: ターミナルを開かずにファイル閲覧・差分確認が完結する
- Constraints: Gateway 側に新しい外部依存を追加しない（subprocess で git コマンドを使用）。WebView の JS ライブラリは assets 同梱とする

## 9. RAID

- **Risk**: 大きなリポジトリでの `git diff` 実行が重い。対策: ファイル単位で取得、タイムアウト設定
- **Risk**: WebView の JS ライブラリのバージョン更新が手間。対策: 更新頻度が低く安定したライブラリを選定する
- **Assumption**: `pane_current_path` が正しいワーキングディレクトリを返す。`cd` 後のパスが即座に反映される
- **Assumption**: 対象マシンに `git` がインストールされている
- **Dependency**: Git CLI、WebView（既存基盤）、既存の認証・セッション管理

## 10. UI 仕様

### 設計方針

- ターミナル画面をベースにし、ファイル・Git 機能はそこからの派生導線とする
- 「一覧はコンパクトに選ぶ、中身はフル画面で見る」の二段構え
- 既存のフローティングピルを操作ハブとして拡張する

### フローティングピル（拡張）

既存の操作ピルに2つのボタンを追加する。

```text
[←] 🟢 agent-0001  [📂Files] [🔀Diff] [📋Paste] [📄Copy]
```text

- **初期位置を画面上部に変更する**（既存は画面下部だが、ターミナル出力の邪魔になるため上に移動）
- ドラッグによる位置変更は引き続き可能
- ポップオーバーはピルの**下方向**に展開する（ピルが上にあるため）
- Git リポジトリではないセッションでは、[🔀Diff] ボタンを無効化（グレーアウト）する

### ポップオーバー（ファイル一覧 / Diff一覧）

[📂Files] または [🔀Diff] をタップすると、ピル直下にコンパクトなポップオーバーが展開する。

```text
┌──────────────────────────────────────────┐
│ StatusBar                                │
│  [←] 🟢 agent-0001  [📂] [🔀] [📋] [📄] │  ← ピル（画面上部）
│  ┌─────────────────────┐                 │
│  │ 📂 src/             │                 │  ← ポップオーバー
│  │ 📂 tests/           │                 │    （ピルの下に展開）
│  │ 📄 README.md        │                 │
│  │ 📄 main.py          │                 │
│  │ 📄 config.yaml      │                 │
│  └─────────────────────┘                 │
│──────────────────────────────────────────│
│ $ git diff                               │
│ ...streaming output...                   │  ← ターミナル（見えたまま）
└──────────────────────────────────────────┘
```text

- **ファイル一覧ポップオーバー**: ディレクトリ内のファイル・フォルダ一覧。ディレクトリをタップで階層移動。パンくずリストで現在地を表示
- **Diff一覧ポップオーバー**: 変更ファイル一覧。各ファイルに変更種別アイコン（M/A/D/U）と +/- 行数を表示。ブランチ名をヘッダに表示
- ポップオーバー外（ターミナル領域）をタップすると閉じる
- ポップオーバー内のスクロールはポップオーバー内に閉じる（ターミナルのスクロールに波及させない）
- ピルをドラッグするとポップオーバーは閉じる

### DocumentViewer（フル画面）

ポップオーバー内のファイルをタップすると、フル画面の DocumentViewer に遷移する。

- **Markdown**: `marked.js` で HTML レンダリング + `github-markdown-css` でスタイリング + `highlight.js` でコードブロック着色
- **Diff**: `diff2html` で side-by-side または unified 表示
- **Code**: `highlight.js` で構文ハイライト付き等幅表示
- **Plain Text**: 等幅表示
- **戻るボタン**でターミナル画面に復帰（ターミナルの WebSocket 接続は維持されたまま）
- コンテンツタイプは拡張子で自動判定する

### 遷移図

```text
TerminalScreen
  │
  ├─ [📂] → ポップオーバー（ファイル一覧）
  │           ├─ ディレクトリタップ → ポップオーバー内で階層移動
  │           └─ ファイルタップ → DocumentViewerScreen（フル画面）
  │
  ├─ [🔀] → ポップオーバー（diff一覧）
  │           └─ ファイルタップ → DocumentViewerScreen（フル画面、diffモード）
  │
  ├─ [📋] → paste（既存、そのまま）
  │
  └─ [📄] → CopyModeSheet（既存、ボトムシートのまま）
```text

### コンポーネント配置（frontend）

```text
features/terminal/
  session/
    components/
      DraggableTerminalFloatingControlPill.kt  ← 既存、ボタン追加
      TerminalFloatingControlPill.kt            ← 既存、ボタン追加
      FileBrowserPopover.kt                     ← 新規
      GitDiffPopover.kt                         ← 新規
    TerminalScreen.kt                           ← 既存、微修正
  viewer/
    DocumentViewerScreen.kt                     ← 新規
    DocumentWebView.kt                          ← 新規（共通 interface）
    components/
      MarkdownViewer.kt                         ← 新規
      DiffViewer.kt                             ← 新規
      CodeViewer.kt                             ← 新規
core/platform/viewer/
  DocumentWebView.kt                            ← 新規（platform interface）
  viewer.html                                    ← 新規（asset）
```text

### 既存コードへの影響

- `TerminalFloatingControlPill`: `onFiles` / `onDiff` コールバックを追加、対応するアイコンボタンを追加
- `defaultTerminalFloatingControlPosition`: 初期位置を `bounds.maxY()`（下）から `bounds.minY()`（上）に変更
- `TerminalScreen`: ポップオーバーの開閉状態管理を追加。DocumentViewer へのナビゲーションを追加
- その他の既存機能（ターミナル接続・再接続・Copy Mode・音声入力・特殊キー）は変更しない

## 11. 実現方針（HOW の方向性）

### Gateway 方針

- File API / Git API は既存の `terminal.py` とは別モジュール（例: `api/files.py`, `api/git.py`）に配置する
- Git コマンドの実行は `infrastructure/git.py` に集約し、テスト可能にする
- パス検証は共通ユーティリティ（例: `infrastructure/path_guard.py`）に切り出す
- レスポンスのエラーフォーマットは既存ルール（`invalid_<field>: <reason>`）に従う
- タイムアウト値は既存の `TMUX_COMMAND_TIMEOUT_SECONDS` と同程度（5秒）を基本とする

### Frontend 方針

- DocumentViewer は TerminalWebView と同じパターン（WebView + asset 同梱 + JS Bridge）で実装する
- コンテンツタイプ（Markdown / Diff / Code / Plain）の判定は拡張子ベースで行う
- ポップオーバーは Compose の `Popup` コンポーネントを使用する
- WebView の JS Bridge 経由で `render(type, content)` を呼び出してコンテンツを差し替える

### JS ライブラリ選定方針

- CDN からダウンロードして assets に同梱する（ビルドツール不要）
- バージョンは固定する（latest は使わない）
- ライセンスが permissive（MIT / Apache 2.0）であることを確認する
- xterm.js を同梱した既存パターンに倣う

## 12. Phase Plan

### Phase 1

- Gateway: File API（browse / read）+ Git API（status / diff / log）
- パス検証・セキュリティ基盤
- Frontend: ピルへのボタン追加 + 初期位置変更
- Frontend: ファイル一覧ポップオーバー + Diff一覧ポップオーバー
- Frontend: DocumentViewer（Plain Text + Code）

### Phase 2

- DocumentViewer の Markdown レンダリング
- DocumentViewer の Diff 表示（side-by-side）

### Phase 3（Nice to have）

- Markdown 内リンクのファイル間遷移
- Git log のコミット詳細表示
- ドットファイル表示切替
- 大きなファイルのページネーション
