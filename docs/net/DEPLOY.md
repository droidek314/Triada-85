# Deploying TRIADA-NET

This guide puts the TRIADA-NET server on the internet, so that TRIADA-85s everywhere (the web version, desktops, the command line) can dial in. It assumes you have never run a server before. Plan for about an hour the first time.

The project itself never creates accounts or deploys anything: you do these steps, with your own accounts. Everything the server needs is in the repository's [`deploy/`](../../deploy) folder.

## What you are building

```
   web version (HTTPS page) ── wss://net.example.org/net ──┐
                                                           ▼
                                  ┌──────────── your server ────────────┐
                                  │  Caddy  (ports 80, 443)             │
                                  │    gets the TLS certificate,        │
                                  │    passes /net /health /admin on    │
                                  │      │                              │
                                  │      ▼                              │
   desktop / command line ─ TCP 2385 ─▶ triada-net  (Java, port 2386)   │
                                  │      │                              │
                                  │      ▼                              │
                                  │  /data/triada-net.db  (SQLite)      │
                                  └─────────────────────────────────────┘
```

- **The TRIADA-NET server** (`triada-net`) holds the boards and the database.
- **Caddy** is a small web server in front of it. It gets a free HTTPS certificate for your domain by itself, and renews it. The web version is served over HTTPS (GitHub Pages), and a browser only lets an HTTPS page open an encrypted WebSocket (`wss://`). That is why Caddy is needed.
- **Docker** runs both as *containers*: sealed boxes with everything they need inside, started by one command.

## 1. What you need

- **A small server.** A "VPS" (virtual private server) from any hosting company. The smallest plan is enough: 1 CPU, 1 GB of memory, 10 GB of disk, Linux (Ubuntu or Debian). You get its **IP address** and a way to log in with **SSH** (a remote command line).
- **A domain name**, or a subdomain of one you already have, for example `net.example.org`.
- On your own computer: a terminal with `ssh` (Windows, macOS and Linux all have one).

## 2. Point the domain at the server

At the company where your domain is registered, open its DNS settings and add an **A record**:

| Type | Name | Value |
|---|---|---|
| A | `net` (for `net.example.org`) | your server's IP address |

It can take a few minutes to an hour to take effect. Check from your computer:

```bash
ping net.example.org
```

The replies should come from your server's IP address.

## 3. Install Docker on the server

Log in:

```bash
ssh root@net.example.org
```

Install Docker with its official script (on Ubuntu or Debian), then check that it works:

```bash
curl -fsSL https://get.docker.com | sh
```

```bash
docker compose version
```

## 4. Get TRIADA-NET

```bash
git clone https://github.com/droidek314/Triada-85.git
```

```bash
cd Triada-85/deploy
```

## 5. Settings

Copy the example settings and edit them (`nano` is a simple editor; Ctrl+O saves, Ctrl+X quits):

```bash
cp .env.example .env
```

```bash
nano .env
```

- `TRIADA_NET_DOMAIN`: your domain, e.g. `net.example.org`.
- `TRIADA_NET_ORIGINS`: the web pages allowed to connect. For the official web version that is `https://droidek314.github.io`. Other sites' pages are refused.
- `TRIADA_NET_ADMIN_TOKEN`: the password of the sysop's page, at least 16 characters. Make a random one:

  ```bash
  openssl rand -hex 24
  ```

  Never share it, and never commit `.env` (the repository's `.gitignore` already leaves it out).

Every setting is listed in `ServerConfig` ([source](../../triada-net-server/src/main/java/org/triada/net/server/ServerConfig.java)): ports, limits per address, and a folder of your own `.board` files.

## 6. Open the ports

The server must accept connections on **80** and **443** (Caddy: certificates and HTTPS) and **2385** (desktops calling over TCP). Many VPS have no firewall by default. If yours uses `ufw`:

```bash
ufw allow 22,80,443,2385/tcp
```

Port 2386 stays closed: only Caddy talks to it, inside the server.

## 7. Start it

```bash
docker compose up -d --build
```

The first time this takes a few minutes: it builds the server from the source. Then check:

```bash
curl https://net.example.org/health
```

It should answer `OK`. If it doesn't yet, Caddy may still be fetching the certificate; wait a minute. See what is happening:

```bash
docker compose logs -f
```

The server's log is one JSON object per line: calls, logins, uploads, moderation. Passwords never appear in it.

## 8. Dial in

- **From a desktop or the command line**, the modem dials over TCP. Start the desktop with `--modem net.example.org:2385`, then in TRIBASIC try the directory:

  ```
  MDIAL "0-00-00"
  PRINT MRECV$
  ```

  (`programs/basic/AUTOCALL.bas` dials the M17 echo number; change its line 40 to `0-00-00` to call your server.) The command line takes the same option: `triada run --modem net.example.org:2385 …`.
- **The web version** needs the address `wss://net.example.org/net`. Until M19 makes this a build setting, it is the `TRIADA_NET_URL` line in [`triada-web/src/main/webapp/index.html`](../../triada-web/src/main/webapp/index.html). For a quick test without changing anything, add `?net=wss://net.example.org/net` to the web version's address.

## 9. Being the sysop

- **The admin page** is at `https://net.example.org/admin`. Enter the admin token and press Load to see:
  - uploads waiting for approval (Approve / Reject);
  - reported messages (Delete message / Ban the author);
  - bans by handle or by address.

  Five wrong tokens lock your address out of the page for ten minutes.
- **The command line** does the same on the server:

  ```bash
  docker compose exec triada-net /app/bin/triada-net-server admin queue
  ```

  The other commands are `approve ID`, `reject ID`, `reports`, `show ID`, `delete ID`, `ban HANDLE`, `unban HANDLE`, `ban-ip IP` and `unban-ip IP`.

## 10. Backups and updates

- **Back up** the database now and then (it holds every account, message and file). Stop the server for a moment, so the file is complete, copy it out, and start again:

  ```bash
  docker compose stop triada-net
  ```

  ```bash
  docker compose cp triada-net:/data/triada-net.db ./triada-net-backup.db
  ```

  ```bash
  docker compose start triada-net
  ```

- **Update** to a newer TRIADA-NET:

  ```bash
  git pull
  ```

  ```bash
  docker compose up -d --build
  ```

  The database is upgraded automatically when the server starts (its migrations run once), and your data stays.

## Without a VPS: container hosts

Some services run a container image for you without a server of your own. TRIADA-NET works there if the service gives you:
- **a public HTTPS address** forwarded to the container's port 2386 (WebSocket support is needed);
- **a disk that survives restarts**, mounted at `/data`;
- optionally, a raw **TCP port** forwarded to 2385. Without it, only the web version can call.

On such a service, Caddy isn't needed: the service does the HTTPS. Build the image with `docker build -f deploy/Dockerfile -t triada-net .` from the repository's top folder, and set the environment variables of step 5 in the service's settings, plus `TRIADA_NET_TRUST_PROXY=true` if the service puts its own proxy in front.

## Running it on your own computer (no Docker)

For trying it out, with the built-in test board:

```bash
./gradlew :triada-net-server:run
```

It listens on TCP 2385 and `ws://localhost:2386/net`, and keeps its database in `triada-net.db` in the project folder. Then dial it with `triada run --modem localhost:2385 ...` or the web version with `?net=ws://localhost:2386/net`. Set `TRIADA_NET_ADMIN_TOKEN` in the environment to get the admin page at `http://localhost:2386/admin`.

## The security checklist

- [ ] `TRIADA_NET_ADMIN_TOKEN` is long and random, and only you have it.
- [ ] `TRIADA_NET_ORIGINS` lists only the web version's site.
- [ ] Port 2386 is not open to the internet (only 80, 443, 2385, and SSH).
- [ ] The server runs as a plain user inside its container (the Dockerfile does this).
- [ ] Backups are made, and kept somewhere else.
- [ ] You read the reports and the upload queue now and then. Uploads stay invisible until you approve them.

## When something goes wrong

| What you see | What to check |
|---|---|
| `curl https://…/health` fails | `docker compose ps` (both running?), `docker compose logs caddy` (certificate problems: is the A record right? are ports 80 and 443 open?) |
| The web version says "offline" | the page's server address (`wss://…/net`), and `TRIADA_NET_ORIGINS` (it must be the exact site address, `https://` and no trailing `/`) |
| Desktops can't connect | port 2385 open in the firewall and published in `docker-compose.yml` |
| Everyone appears to come from one address | `TRIADA_NET_TRUST_PROXY=true` is missing, so the server sees Caddy's address for every WebSocket caller |
| Some callers are refused (`"reason":"too many calls"` in the log; their modem says offline) | many people behind one address (a school?): raise `TRIADA_NET_CONNECTIONS_PER_IP` |
