// ══════════════════════════════════════════════════════════════════════════
// Bingo Relay & Matchmaking Server
// ══════════════════════════════════════════════════════════════════════════
//
// WHAT THIS SERVER DOES (and does NOT do):
// - It does NOT know any Bingo rules. It never looks at grids, scratches,
//   turns, or win conditions. All of that game logic keeps running exactly
//   like it does today, on the HOST player's device.
// - It IS a relay + matchmaking broker. Its only jobs are:
//     1. Track who is online, under a persistent per-device player ID.
//     2. Create/join "rooms" (by generated code, by direct ID invite, or by
//        matchmaking queue) and remember who is the host of each room.
//     3. Forward every in-game message between room members using the exact
//        same star topology the app already uses locally: a message from
//        the HOST is broadcast to every other member; a message from a
//        GUEST is forwarded ONLY to the host. The server just swaps out
//        "direct TCP socket" for "through this server", nothing else.
//     4. Report presence counts (players online / waiting) so the app can
//        show them in the lobby.
//     5. Give a short reconnect grace window if someone's connection drops,
//        so they can resume their seat instead of being kicked immediately.
//
// PROTOCOL (all messages are single-line JSON over one WebSocket):
//
//   Client -> Server
//     {type:'identify', id, name}                 // id: null on first run, server will assign one
//     {type:'quickMatch'}                          // join the 1v1 random queue
//     {type:'quickTournament', size}               // join a random tournament queue (4/6/8/10)
//     {type:'cancelQueue'}
//     {type:'createRoom'}                          // host a room, get a short code back
//     {type:'joinRoom', roomId}                    // join by code
//     {type:'inviteById', targetId}                // direct invite to a friend's fixed ID
//     {type:'leaveRoom'}
//     {type:'relay', payload}                      // pass-through of the existing game protocol
//                                                   // (hello, grid, scratch, state, win,
//                                                   //  tournamentBracket, emoji, ...) — untouched.
//
//   Server -> Client
//     {type:'identified', id, name}
//     {type:'presence', online, waitingQuickMatch, waitingTournament:{4,6,8,10}}
//     {type:'roomCreated', roomId}
//     {type:'roomJoined', roomId, youAreHost, host:{id,name}, members:[{id,name}]}
//     {type:'roomRejoined', roomId, youAreHost, host:{id,name}, members:[{id,name}]}  // after a reconnect
//     {type:'memberLeft', id}
//     {type:'roomClosed', reason}
//     {type:'matchFound', roomId, youAreHost, opponent:{id,name}}
//     {type:'tournamentMatchFound', roomId, size, youAreHost, players:[{id,name}]}
//     {type:'invited', fromId, fromName, roomId}
//     {type:'inviteFailed', reason}
//     {type:'relay', payload, fromId}
//     {type:'error', message}
//
// ══════════════════════════════════════════════════════════════════════════

const http = require('http');
const crypto = require('crypto');
const { WebSocketServer } = require('ws');

const PORT = process.env.PORT || 8787;
const RECONNECT_GRACE_MS = 45000; // matches the client's existing 40s tournament forfeit window
const ID_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // no 0/O/1/I to avoid confusion when read aloud

/** @type {Map<string, {id:string,name:string,ws:WebSocket|null,roomId:string|null,disconnectTimer:NodeJS.Timeout|null}>} */
const players = new Map();

/** @type {Map<string, {id:string,hostId:string,memberIds:string[],type:'casual'|'tournament',size:number|null,createdAt:number}>} */
const rooms = new Map();

/** @type {string[]} */
let quickMatchQueue = [];

/** @type {Record<4|6|8|10, string[]>} */
const tournamentQueues = { 4: [], 6: [], 8: [], 10: [] };

function genId() {
  return crypto.randomUUID();
}

function genRoomId() {
  let code;
  do {
    code = Array.from({ length: 5 }, () => ID_ALPHABET[Math.floor(Math.random() * ID_ALPHABET.length)]).join('');
  } while (rooms.has(code));
  return code;
}

function send(player, obj) {
  if (!player || !player.ws || player.ws.readyState !== player.ws.OPEN) return false;
  try {
    player.ws.send(JSON.stringify(obj));
    return true;
  } catch (e) {
    return false;
  }
}

function publicInfo(p) {
  return { id: p.id, name: p.name };
}

function broadcastPresence() {
  const payload = {
    type: 'presence',
    online: [...players.values()].filter((p) => p.ws && p.ws.readyState === p.ws.OPEN).length,
    waitingQuickMatch: quickMatchQueue.length,
    waitingTournament: {
      4: tournamentQueues[4].length,
      6: tournamentQueues[6].length,
      8: tournamentQueues[8].length,
      10: tournamentQueues[10].length,
    },
  };
  for (const p of players.values()) send(p, payload);
}

function removeFromAllQueues(id) {
  quickMatchQueue = quickMatchQueue.filter((x) => x !== id);
  for (const size of Object.keys(tournamentQueues)) {
    tournamentQueues[size] = tournamentQueues[size].filter((x) => x !== id);
  }
}

function createRoom(hostId, type, size) {
  const roomId = genRoomId();
  rooms.set(roomId, { id: roomId, hostId, memberIds: [hostId], type, size: size || null, createdAt: Date.now() });
  const host = players.get(hostId);
  if (host) host.roomId = roomId;
  return roomId;
}

function joinRoom(roomId, playerId) {
  const room = rooms.get(roomId);
  if (!room) return { ok: false, reason: 'not_found' };
  if (room.memberIds.length >= 6) return { ok: false, reason: 'full' };
  if (!room.memberIds.includes(playerId)) room.memberIds.push(playerId);
  const p = players.get(playerId);
  if (p) p.roomId = roomId;
  return { ok: true, room };
}

function closeRoom(roomId, reason) {
  const room = rooms.get(roomId);
  if (!room) return;
  for (const memberId of room.memberIds) {
    const p = players.get(memberId);
    if (p) {
      p.roomId = null;
      send(p, { type: 'roomClosed', reason: reason || 'closed' });
    }
  }
  rooms.delete(roomId);
}

function leaveRoom(playerId) {
  const p = players.get(playerId);
  if (!p || !p.roomId) return;
  const room = rooms.get(p.roomId);
  p.roomId = null;
  if (!room) return;
  room.memberIds = room.memberIds.filter((id) => id !== playerId);
  if (room.hostId === playerId || room.memberIds.length === 0) {
    // Host leaving (or room now empty) ends the room for everyone still in it —
    // mirrors today's behavior where the host device IS the game, so losing the
    // host has always meant the room is over.
    closeRoom(room.id, 'host_left');
    return;
  }
  for (const memberId of room.memberIds) {
    send(players.get(memberId), { type: 'memberLeft', id: playerId });
  }
}

// The star-topology relay: exactly mirrors hostConns.forEach(...) / joinConn.dc.send(...)
// from the client's existing local networking code, just routed through the server.
function relay(fromId, payload) {
  const p = players.get(fromId);
  if (!p || !p.roomId) return;
  const room = rooms.get(p.roomId);
  if (!room) return;
  if (fromId === room.hostId) {
    for (const memberId of room.memberIds) {
      if (memberId === fromId) continue;
      send(players.get(memberId), { type: 'relay', payload, fromId });
    }
  } else {
    send(players.get(room.hostId), { type: 'relay', payload, fromId });
  }
}

function tryMatchQuickQueue() {
  while (quickMatchQueue.length >= 2) {
    const aId = quickMatchQueue.shift();
    const bId = quickMatchQueue.shift();
    const a = players.get(aId);
    const b = players.get(bId);
    if (!a || !a.ws || a.ws.readyState !== a.ws.OPEN) { if (b) quickMatchQueue.unshift(bId); continue; }
    if (!b || !b.ws || b.ws.readyState !== b.ws.OPEN) { quickMatchQueue.unshift(aId); continue; }
    const roomId = createRoom(aId, 'casual', null);
    joinRoom(roomId, bId);
    send(a, { type: 'matchFound', roomId, youAreHost: true, opponent: publicInfo(b) });
    send(b, { type: 'matchFound', roomId, youAreHost: false, opponent: publicInfo(a) });
  }
}

function tryMatchTournamentQueue(size) {
  const q = tournamentQueues[size];
  while (q.length >= size) {
    const batch = q.splice(0, size);
    const ids = batch.filter((id) => {
      const p = players.get(id);
      return p && p.ws && p.ws.readyState === p.ws.OPEN;
    });
    if (ids.length < size) {
      // Someone in this batch dropped right before matching — put the still-valid
      // players back at the front of the queue instead of losing their spot.
      tournamentQueues[size] = [...ids, ...tournamentQueues[size]];
      break;
    }
    const hostId = ids[0];
    const roomId = createRoom(hostId, 'tournament', size);
    for (const id of ids.slice(1)) joinRoom(roomId, id);
    const roster = ids.map((id) => publicInfo(players.get(id)));
    for (const id of ids) {
      send(players.get(id), { type: 'tournamentMatchFound', roomId, size, youAreHost: id === hostId, players: roster });
    }
  }
}

function handleMessage(playerId, raw) {
  let msg;
  try {
    msg = JSON.parse(raw);
  } catch (e) {
    return;
  }
  const p = players.get(playerId);
  if (!p) return;

  switch (msg.type) {
    case 'quickMatch':
      removeFromAllQueues(playerId);
      quickMatchQueue.push(playerId);
      tryMatchQuickQueue();
      broadcastPresence();
      break;

    case 'quickTournament': {
      const size = [4, 6, 8, 10].includes(msg.size) ? msg.size : 4;
      removeFromAllQueues(playerId);
      tournamentQueues[size].push(playerId);
      tryMatchTournamentQueue(size);
      broadcastPresence();
      break;
    }

    case 'cancelQueue':
      removeFromAllQueues(playerId);
      broadcastPresence();
      break;

    case 'createRoom': {
      removeFromAllQueues(playerId);
      const roomId = createRoom(playerId, 'casual', null);
      send(p, { type: 'roomCreated', roomId });
      break;
    }

    case 'joinRoom': {
      removeFromAllQueues(playerId);
      const result = joinRoom(String(msg.roomId || '').toUpperCase(), playerId);
      if (!result.ok) {
        send(p, { type: 'error', message: result.reason === 'full' ? 'room_full' : 'room_not_found' });
        break;
      }
      const room = result.room;
      const host = players.get(room.hostId);
      send(p, {
        type: 'roomJoined',
        roomId: room.id,
        youAreHost: false,
        host: host ? publicInfo(host) : null,
        members: room.memberIds.map((id) => publicInfo(players.get(id))).filter(Boolean),
      });
      if (host) send(host, { type: 'memberJoined', member: publicInfo(p) });
      break;
    }

    case 'inviteById': {
      const target = players.get(msg.targetId);
      if (!target || !target.ws || target.ws.readyState !== target.ws.OPEN) {
        send(p, { type: 'inviteFailed', reason: 'offline' });
        break;
      }
      let roomId = p.roomId;
      if (!roomId) roomId = createRoom(playerId, 'casual', null);
      send(target, { type: 'invited', fromId: playerId, fromName: p.name, roomId });
      send(p, { type: 'inviteSent', targetId: msg.targetId, roomId });
      break;
    }

    case 'leaveRoom':
      leaveRoom(playerId);
      break;

    case 'relay':
      relay(playerId, msg.payload);
      break;

    default:
      break;
  }
}

function attachIdentity(ws, requestedId, requestedName) {
  const id = requestedId || genId();
  let p = players.get(id);
  if (p) {
    // Reconnect within the grace window — cancel the pending cleanup and resume.
    if (p.disconnectTimer) {
      clearTimeout(p.disconnectTimer);
      p.disconnectTimer = null;
    }
    p.ws = ws;
    if (requestedName) p.name = requestedName;
  } else {
    p = { id, name: requestedName || 'Player', ws, roomId: null, disconnectTimer: null };
    players.set(id, p);
  }
  send(p, { type: 'identified', id: p.id, name: p.name });
  // If they were mid-room when they dropped, tell them right away so the client
  // can resume (it will re-send its own 'hello' as a relay payload once it knows
  // it's back in a room — the same reattachment pattern already used locally).
  if (p.roomId && rooms.has(p.roomId)) {
    const room = rooms.get(p.roomId);
    const host = players.get(room.hostId);
    send(p, {
      type: 'roomRejoined',
      roomId: room.id,
      youAreHost: room.hostId === p.id,
      host: host ? publicInfo(host) : null,
      members: room.memberIds.map((mid) => publicInfo(players.get(mid))).filter(Boolean),
    });
  }
  broadcastPresence();
  return p;
}

function handleDisconnect(playerId) {
  const p = players.get(playerId);
  if (!p) return;
  p.ws = null;
  removeFromAllQueues(playerId);
  // Grace window: keep their room seat reserved briefly in case it's a blip
  // (matches the reconnect UX already built into the tournament screens).
  p.disconnectTimer = setTimeout(() => {
    if (p.ws) return; // they reconnected in the meantime
    leaveRoom(playerId);
    players.delete(playerId);
    broadcastPresence();
  }, RECONNECT_GRACE_MS);
  broadcastPresence();
}

// ── HTTP + WebSocket bootstrap ──────────────────────────────────────────────
const httpServer = http.createServer((req, res) => {
  // Simple health-check endpoint — most free hosts (Render, etc.) ping this,
  // and it's also handy for the app to detect the server is reachable at all
  // before trying to open a WebSocket.
  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({
    ok: true,
    online: [...players.values()].filter((p) => p.ws && p.ws.readyState === p.ws.OPEN).length,
    rooms: rooms.size,
  }));
});

const wss = new WebSocketServer({ server: httpServer });

wss.on('connection', (ws) => {
  let playerId = null;

  ws.on('message', (raw) => {
    let msg;
    try {
      msg = JSON.parse(raw);
    } catch (e) {
      return;
    }
    if (msg.type === 'identify') {
      const p = attachIdentity(ws, msg.id, msg.name);
      playerId = p.id;
      return;
    }
    if (!playerId) return; // must identify first
    handleMessage(playerId, raw);
  });

  ws.on('close', () => {
    if (playerId) handleDisconnect(playerId);
  });

  ws.on('error', () => {
    if (playerId) handleDisconnect(playerId);
  });
});

httpServer.listen(PORT, () => {
  console.log(`Bingo relay server listening on port ${PORT}`);
});
