# alt-core-cli

Command-line HTTP client for testing Alterante REST API endpoints.

## Build

```bash
cd alt-core-cli
mvn clean compile package
```

Produces `target/alt-core-cli.jar` (uber JAR, ~301KB).

**Requirements:** JDK 17+, Maven 3.9+

## Quick Start

```bash
# Configure server (persists to /tmp/alt-core-cli-config)
java -jar alt-core-cli.jar config http localhost 8081

# Login (saves UUID for future requests)
java -jar alt-core-cli.jar login admin valid

# Search for files
java -jar alt-core-cli.jar query --ftype .all --foo test --days 7

# Raw endpoint call
java -jar alt-core-cli.jar raw --path /cass/getsession.fn
```

## Commands

### Connection

| Command | Usage | Description |
|---------|-------|-------------|
| `config` | `config [protocol] [server] [port]` | Configure server connection |
| `login` | `login <user> <pass>` | Authenticate and save session UUID |

### Search

| Command | Usage | Description |
|---------|-------|-------------|
| `query` | `query --ftype .all --foo test --days 7` | Search files |
| `sidebar` | `sidebar --ftype .all --days 0` | File type counts |
| `suggest` | `suggest --foo test` | Search suggestions |

### Files

| Command | Usage | Description |
|---------|-------|-------------|
| `fileinfo` | `fileinfo --md5 <hash>` | File metadata |
| `getfile` | `getfile --md5 <hash> --filename <name> [--output path]` | Download file |
| `downloadmulti` | `downloadmulti --md5 h1,h2,h3 [--output path]` | Download multiple as ZIP |

### Tags

| Command | Usage | Description |
|---------|-------|-------------|
| `tags` | `tags [--md5 <hash>]` | List tags |
| `addtag` | `addtag --tag <name> --md5 <hash>` | Add tag to file |
| `removetag` | `removetag --tag <name> --md5 <hash>` | Remove tag from file |

### Folders

| Command | Usage | Description |
|---------|-------|-------------|
| `folders` | `folders [--folder scanfolders]` | List folders |
| `folders open` | `folders open --name <folder> [--md5 <hash>]` | Open folder |
| `folderperm` | `folderperm --folder <path>` | Get folder permissions |
| `setfolderperm` | `setfolderperm --folder <path> --perms <json>` | Set folder permissions |

### Chat

| Command | Usage | Description |
|---------|-------|-------------|
| `chat pull` | `chat pull [--md5 X] [--from 0]` | Pull messages |
| `chat push` | `chat push --msg <text> [--md5 X] [--user admin]` | Send message (Base64 encoded) |
| `chat clear` | `chat clear` | Clear all messages |

### Sharing

| Command | Usage | Description |
|---------|-------|-------------|
| `share list` | `share list` | List active shares |
| `share create` | `share create --tag <tag> --user <user> [--perms rw]` | Create share |
| `share remove` | `share remove --id <shareId>` | Remove share |
| `share settings` | `share settings --id <shareId>` | View share settings |
| `share invite` | `share invite --tag <tag> --user <email>` | Send share invitation |

### System

| Command | Usage | Description |
|---------|-------|-------------|
| `nodeinfo` | `nodeinfo` | Device/node information |
| `serverprop` | `serverprop --property <name>` | Get server property value |
| `cluster` | `cluster` | Cluster information |
| `transcript` | `transcript --md5 <hash>` | Video transcript |

### Users (Admin)

| Command | Usage | Description |
|---------|-------|-------------|
| `users list` | `users list` | List all users and emails |
| `users add` | `users add --user <name> --pass <password>` | Create user account |

### Utility

| Command | Usage | Description |
|---------|-------|-------------|
| `raw` | `raw --path /cass/endpoint.fn?params` | Raw GET request to any endpoint |
| `help` | `help` | Show all commands |

## Global Options

| Option | Description |
|--------|-------------|
| `--noauth` | Skip authentication cookie (test unauthenticated access) |

## Architecture

```
com.alterante.cli/
├── Main.java           # Entry point, command dispatcher, arg parsing
├── HttpSession.java    # HTTP client, config persistence, auth cookie
└── commands/           # One class per command (19 classes)
    ├── ConfigCommand.java
    ├── LoginCommand.java
    ├── QueryCommand.java
    └── ...
```

### Key Classes

**Main.java** — Parses CLI arguments, dispatches to command classes via switch statement. Provides `getArg()` and `hasFlag()` argument helpers.

**HttpSession.java** — Wraps Java 11+ HttpClient. Manages server config and UUID persistence via `/tmp/alt-core-cli-config`. Methods:
- `get(path, includeAuth)` — GET with text response
- `post(url, contentType, body, includeAuth)` — POST with text response
- `getBytes(path, includeAuth)` — GET with binary response (file downloads)
- `printResponse(response)` — JSON-formatted output with status, URL, body

### How Authentication Works

1. `config` sets `protocol`, `server`, `port` in `/tmp/alt-core-cli-config`
2. `login` calls `/cass/login.fn`, extracts UUID from HTML response, saves to config
3. Subsequent commands read UUID from config and send as `Cookie: uuid=<value>` header
4. `--noauth` flag skips the cookie for testing unauthenticated endpoints

### Dependencies

- **json-smart** v2.4.11 — JSON parsing
- **Java 17 stdlib** — HttpClient, Properties, Base64, regex

## API Endpoint Mapping

| CLI Command | Server Endpoint |
|-------------|----------------|
| `login` | `/cass/login.fn` |
| `query` | `/cass/query.fn` |
| `sidebar` | `/cass/sidebar.fn` |
| `suggest` | `/cass/suggest.fn` |
| `fileinfo` | `/cass/getfileinfo.fn` |
| `getfile` | `/cass/getfile.fn` |
| `downloadmulti` | `/cass/downloadmulti.fn` |
| `tags` | `/cass/gettags_webapp.fn` |
| `addtag` / `removetag` | `/cass/applytags.fn` |
| `folders` | `/cass/getfolders-json.fn` |
| `folders open` | `/cass/openfolder.fn` |
| `folderperm` | `/cass/getfolderperm.fn` |
| `setfolderperm` | `/cass/setfolderperm.fn` |
| `chat pull` | `/cass/chat_pull.fn` |
| `chat push` | `/cass/chat_push.fn` |
| `chat clear` | `/cass/chat_clear.fn` |
| `share list` | `/cass/refreshsharetable.fn` |
| `share create` | `/cass/doshare_webapp.fn` |
| `share remove` | `/cass/removeshare.fn` |
| `share settings` | `/cass/getsharesettingsmodal.fn` |
| `share invite` | `/cass/invitation_webapp.fn` |
| `nodeinfo` | `/cass/nodeinfo.fn` |
| `serverprop` | `/cass/serverproperty.fn` |
| `users list` | `/cass/getusersandemail.fn` |
| `users add` | `/cass/adduser.fn` |
| `cluster` | `/cass/getcluster.fn` |
| `transcript` | `/cass/gettranslate_json.fn` |
| `raw` | Any `/cass/...` path |

## Usage with Smoke Test

The smoke test (`test-smoke/smoke-test.sh`) uses this CLI for login and some endpoint calls:

```bash
# The smoke test calls the CLI like this:
java -jar alt-core-cli.jar login admin valid
java -jar alt-core-cli.jar raw --path /cass/endpoint.fn?params
```

## Examples

```bash
# Search for MP4 files from the last 30 days
java -jar alt-core-cli.jar query --ftype .mp4 --days 30

# Get file metadata by hash
java -jar alt-core-cli.jar fileinfo --md5 d41d8cd98f00b204e9800998ecf8427e

# Download a file
java -jar alt-core-cli.jar getfile --md5 abc123 --filename video.mp4 --output ./downloads/

# List all tags
java -jar alt-core-cli.jar tags

# Send a chat message
java -jar alt-core-cli.jar chat push --msg "Hello from CLI"

# Test endpoint without auth (security testing)
java -jar alt-core-cli.jar raw --path /cass/shutdown.fn --noauth

# List cluster nodes
java -jar alt-core-cli.jar nodeinfo
```
