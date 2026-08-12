# Bingo Relay & Matchmaking Server

WebSocket server for real-internet Bingo play: quick match (1v1 random), random
tournaments (4/6/8/10 players), invite-by-permanent-ID, and live online/waiting
counts.

It does **not** know any Bingo rules — it never touches grids, scratches, or
win logic. It only tracks who's online, creates/joins rooms, and relays
messages between room members using the exact same "host is authoritative,
guests just relay through" model the app already uses for local network play.
The game logic itself keeps running unchanged on the host player's device.

## Local test (needs Node.js ≥ 18 and internet access to install `ws`)

```bash
npm install
npm start
# -> "Bingo relay server listening on port 8787"
```

Health check: open `http://localhost:8787/` in a browser — should return
`{"ok":true,"online":0,"rooms":0}`.

## Deploying for free on Render.com

1. Push this `bingo-server/` folder to its own GitHub repo (or a subfolder of
   an existing repo — Render lets you set a "Root Directory").
2. On [render.com](https://render.com), click **New +** → **Web Service**,
   connect the repo.
3. Settings:
   - **Environment**: Node
   - **Build Command**: `npm install`
   - **Start Command**: `npm start`
   - **Instance Type**: Free
4. Deploy. Render gives you a URL like `https://bingo-relay.onrender.com`.
   The app connects to it as a WebSocket at `wss://bingo-relay.onrender.com`.

### The one real trade-off of the free tier

Render's free web services **go to sleep after ~15 minutes with no traffic**
and take 30–50 seconds to wake back up on the next connection. In practice
that means: if nobody has played in a while, the *first* person to open the
app and try quick-match/tournament might wait up to a minute before the
server responds. Once at least one player is connected, the server stays
awake for everyone. This is normal for free hosting — the alternative
(always-on, sub-second wake) means paying a few dollars a month once the
player base grows enough for it to matter.

## Environment variables

- `PORT` — set automatically by Render; defaults to `8787` locally.

## What's NOT built yet

This server is ready to receive connections, but the Android app's
`bingo-v18.html` still only knows how to talk to the local Kotlin TCP bridge
for multiplayer. Wiring the app up to this server (new lobby screens for
quick match / tournament queue / invite-by-ID, and swapping the transport
layer from local TCP to this WebSocket) is the next phase.
