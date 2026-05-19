# Project Overview

## 1. What Muxport Is

**Muxport** is a tmux + WebSocket + mobile infrastructure tool.

It makes tmux sessions reachable from your phone, providing a terminal surface where humans can connect to running tmux sessions from mobile devices.

Muxport treats tmux sessions as the center of execution, with terminal access and session management as its core capabilities.

---

## 2. Project Goal

### 2.1 Vision

**Build a tmux infrastructure tool that is reachable from mobile.**

- tmux sits at the center as the execution environment
- terminal access from mobile is the primary interface
- push notifications keep you connected even when away

### 2.2 Problems It Solves

| Problem | How Muxport solves it |
| --- | --- |
| **Execution environments are fragile** | tmux sessions provide continuity and reconnectable execution contexts |
| **Terminal access is desktop-first** | a mobile-accessible terminal surface connects to tmux sessions directly |
| **Session state is invisible from mobile** | snapshots and push notifications keep you informed |

---

## 3. Design Philosophy

### 3.1 tmux-Centered

Muxport treats tmux as the runtime body of execution.

A session is not just a screen. It is:

- the continuity point of a running task
- an attachable endpoint
- a concrete execution object that can be inspected and managed

### 3.2 Terminal Infrastructure

Muxport is infrastructure, not orchestration. It provides:

- **Terminal Surface**: session list, websocket terminal, snapshot, mobile access
- **Session Management**: create, browse, and manage tmux sessions
- **Push Notifications**: stay informed about session events

These capabilities serve as building blocks for whatever runs inside tmux sessions.

---

## 4. System Shape

Muxport consists of three main elements:

- **tmux session**: the center of execution
- **gateway**: the API layer connecting mobile clients to tmux
- **frontend**: the mobile terminal client

---

## 5. Why The Name "Muxport"

The name **Muxport** combines **mux** (from multiplexer) and **port** (a gateway or harbor).

- **mux** directly references tmux, the core technology
- **port** conveys the idea of an entry point — the gateway between mobile and tmux sessions
- Together they describe exactly what the tool does: **the port to your tmux multiplexer**

It is short, easy to type, and memorable.
