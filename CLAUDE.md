# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

`sync` is a Spring Boot (4.1.0, Java 25) LAN file-sharing/sync server. A single JAR, run on each machine on a LAN, serves a web UI (Thymeleaf + vanilla JS, Bootstrap, Dropzone) for browsing/uploading/downloading files, and can discover and sync with other instances of itself running on other nodes over plain HTTP. It also bundles a LAN chat and a shared clipboard, built on STOMP/WebSocket.

The `data/` directory at the project root is runtime state (H2 database files, synced/shared files, logs, upload temp dir) — it is gitignored and unrelated to the codebase; do not read or document its contents.

## Common commands

Build/run (Maven wrapper, no local Maven install needed):
```
./mvnw clean package          # build the jar (target/sync-<version>.jar)
./mvnw spring-boot:run         # run the app from source
./mvnw test                    # run all tests
./mvnw test -Dtest=SyncApplicationTests#contextLoads   # run a single test method
```
There is currently only one test (`SyncApplicationTests`, a context-load smoke test), so `./mvnw test` is fast.

The app listens on port `9005` (`application.yml`). The H2 database file, `shared` files directory, and logs all live under `${user.dir}/data`, created on first run by `FileService`.

Docker: `Dockerfile` copies a pre-built `target/sync-<version>.jar` into an Alpine JRE 25 image and runs it on port 9005. The `pom.xml` `package` phase also shells out to `docker build` via `exec-maven-plugin`, so `./mvnw package` will attempt a Docker build as part of packaging (requires Docker available and the jar version in `Dockerfile`'s `COPY` line to match `pom.xml`'s `<version>` — keep these in sync when bumping the version).

Java 25 virtual threads are enabled (`spring.threads.virtual.enabled=true`) and native access is enabled via `--enable-native-access=ALL-UNNAMED` JVM args (set in both the Spring Boot Maven plugin and Surefire config) because OSHI's `oshi-core-ffm` module uses the Java 25 FFM API for system metrics.

Spring Boot 4 renamed several starters; `pom.xml`'s `spring-boot-starter-webmvc`, `spring-boot-starter-webmvc-test`, `spring-boot-starter-thymeleaf-test`, and `spring-boot-starter-validation-test` are the correct Boot 4 artifact IDs (the old Boot 3 names like `spring-boot-starter-web`/`-test` are deprecated), not typos to "fix".

## Architecture

### File model and "filecode" indirection

`FileService` (`util/FileService.java`) is the core abstraction over the filesystem. Key points:
- `appDir` = `data/shared` — the single root directory that is browsed, uploaded to, downloaded from, and synced.
- Every file/directory is represented as a `FileMeta` (`payload/FileMeta.java`), keyed in an in-memory `LinkedHashMap<String, FileMeta> localFiles` by a **filecode**: a URL-safe Base64 encoding of the first 8 bytes of a SHA3-256 hash of the file's name (`listDirectory`) or relative path (`listDirectoryRecursive`).
- Download/stream endpoints (`FileController`) never take raw file paths from the client — they resolve a `filecode` through `FileService.getLocalFiles()`. This is the access-control boundary: a client can only act on files the server has already indexed.
- `listDirectory(relativePath)` lists one directory (used by the browser UI); `listDirectoryRecursive()` walks the whole `appDir` tree (used by node-to-node sync and `/files/recursive`). Both populate/refresh the shared `localFiles` map as a side effect, so listing and lookup are coupled — don't call these expecting pure reads.
- `refreshFileList()` clears `localFiles` before relisting, effectively invalidating old filecodes.

### Multi-node sync

`SyncService` treats every other `sync` instance as a peer, tracked in the `InstanceNode` JPA entity/`InstanceNodeRepository` (ip, port, alias, last-known-reachability). Peers are added manually via `NetworkController`'s `/api/nodes` endpoints (`index.html` UI), not auto-discovered.

- A `@Scheduled` job (`monitorNodeStatuses`, every 10s) pings each node's `/api/ping` and flips its `isWorking` flag.
- `triggerSync()` (manual via `/api/sync/trigger`, or automatic hourly if `isAutoSyncEnabled`) fetches each peer's full recursive file list (`GET /files/recursive`) and diffs it against the local recursive list by `relativePath`:
  - missing locally → downloaded via `downloadFile()`, which supports resuming partial downloads (HTTP `Range`) into `data/temp/*.part` files before an atomic move into `appDir`.
  - present locally but size/mtime differ (beyond a 2s tolerance) → **not** auto-overwritten; recorded in an in-memory `syncConflicts` map and surfaced via `/api/sync/conflicts`, resolved one-by-one via `/api/sync/resolve`.
- Sync is one-directional pull per node pair: each node only pulls files it's missing or stale from peers, it doesn't push. Running sync on all nodes converges the set.
- All inter-node HTTP calls use a `RestClient` with a 2s connect/read timeout, so an unreachable peer fails fast rather than blocking the scheduler.

### Streaming downloads / range requests

`FileController` has three ways to serve a file, reflecting an evolution visible in the (partially commented-out) code:
- `/resource` and `/download` — simple, Spring-handled `Resource`/`FileSystemResource` responses (ETag + Last-Modified via `ServletWebRequest.checkNotModified`).
- `/download/manual` — hand-rolled HTTP Range support (single range, and multi-range `multipart/byteranges`) using `FileChannel.transferTo`/manual chunked copy, for byte-range-accurate streaming (e.g. video seeking) that Spring's default resource handling doesn't give fine control over.
- `/zip-stream` and `/zip-folder` — on-the-fly ZIP streaming of multiple files/a folder without buffering the whole archive in memory.

Chunked uploads (`/upload/chunk`) reassemble large files from client-side chunks written to a per-upload temp dir (`.temp_<uuid>` under the target directory); the merge is `synchronized` on the interned `uuid` string to avoid concurrent double-merges when the last chunk races.

### Storage accounting

`StorageService` enforces `app.storage.limit` (bytes, default 25GB, `application.properties`) against **actual used disk space under `appDir` plus space reserved for in-flight uploads** (`reserveSpace`/`releaseSpace`, keyed by the chunked-upload `uuid`). The chunk upload flow calls `/api/storage/reserve` before starting and `/api/storage/release` after a chunk set completes — reservations exist only to prevent concurrent uploads from jointly exceeding the limit before any of them finish writing.

### Traffic/transfer accounting

Two independent, overlapping accounting mechanisms exist:
- `TrafficTrackingFilter` (a servlet `Filter` at `Ordered.HIGHEST_PRECEDENCE`) wraps every request/response to count raw bytes read/written per request, independent of what the request was for, and persists per-IP cumulative totals via `NetworkTrafficService`/`NetworkTrafficRepository`. This is coarse, protocol-level traffic.
- `TransferHistory` (`TransferHistoryRepository`) records semantically-typed transfer events (`MANUAL_DOWNLOAD`, `MANUAL_UPLOAD`, `SYNC_JOB`) with filename/IP/size, logged explicitly by `FileController` and `SyncService.downloadFile()`. Repeated transfers of the same file/IP/type within a 1-hour window are coalesced into one row (size accumulated) rather than creating new rows, both when logging (`FileController.logTransfer`) and when the `/history` page renders (`PageController.transferHistory` re-merges on read as a second pass).

`PageController.transferHistory` is where both accounting sources are joined and summarized for the `history.html` template (top IPs, top files, totals).

### Realtime features (WebSocket)

`WebSocketConfig` enables a STOMP broker at `/ws` (SockJS fallback) with destination prefix `/app` and broadcast topic `/topic`. `ChatController` serves chat both as a REST resource (`/api/chat`, backed by `ChatRepository`/H2) and as a STOMP endpoint (`/app/chat.sendMessage` → broadcast to `/topic/public`), persisting every message either way. `ClipboardController` (`/api/clipboard`) is REST-only (no WebSocket push) — clients presumably poll it.

### Persistence

JPA entities (`Chat`, `Clipboard`, `InstanceNode`, `NetworkTraffic`, `TransferHistory`) are plain `@Entity` classes under `payload/`, each with a matching `*Repository extends JpaRepository` under `util/` — there's no separate `repository`/`entity` package split in this codebase. Backing store is a file-based H2 database at `data/sync` (`spring.jpa.hibernate.ddl-auto=update`, so schema changes to entities apply automatically on next boot without migrations).

### Frontend

Server-rendered Thymeleaf templates (`templates/index.html` — file browser, `templates/history.html` — transfer history dashboard) plus static assets under `static/` (`js/code.js`, Bootstrap, Dropzone for drag-and-drop uploads with chunking/progress). There is no separate frontend build step or package manager — static assets are committed directly and served by Spring. (Despite the `ui-react` branch name, no React code exists yet in this tree.)

## Gotchas

- `Dockerfile`'s `COPY target/sync-<version>.jar app.jar` (currently `0.0.8`) is hardcoded to a specific version string and must be bumped manually whenever `pom.xml`'s `<version>` changes, or the Docker build step (which runs automatically during `mvn package`) will fail to find the jar.
- `app.storage.limit` and multipart size limits are in `application.properties`; multipart temp storage location is `${user.dir}/data/tmp` (also created explicitly in `Dockerfile`).
- Lombok is used throughout (`@Data`, `@Getter`, `@RequiredArgsConstructor`, `@Log4j2`) — annotation processing must be enabled in the IDE for compilation to resolve.
- Any new endpoint or sync path that turns a client- or peer-supplied string into a filesystem `Path` under `appDir` (or `data/temp`) must check `resolved.normalize().startsWith(fs.getAppDir())` (or reject `..`/path separators in a bare filename) before touching the filesystem — see the checks in `FileService.listDirectory`, `FileController`'s `/zip-folder` and `/upload*` endpoints, and `SyncService.downloadFile`/`processRemoteFiles` for the existing pattern. This boundary was previously missing in three places and has been fixed; don't reintroduce an unchecked `resolve()`.
- Indentation is inconsistent between files: older classes (`FileService`, `FileController`) use tabs, newer ones (`SyncService`, `StorageService`, `NetworkTrafficService`) use spaces. Match whichever a file already uses rather than reformatting it.

## Repository conventions

- Commit messages in this repo's history are past-tense, not imperative (`Added X`, `Fixed Y`, `removed Z`) — match that style rather than the generic `git commit` imperative-mood convention.
- Current feature branch is `ui-react`, based off `main`; despite the name, no React code exists in this tree yet (see Frontend, above).
