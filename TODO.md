# TODO

## Bugs & security (from repo audit, 2026-11-24)

Ordered most severe first. File:line refs point at the current code.

### Must fix
- [x] **Path traversal via sync (write anywhere on disk)** — `SyncService.java` (`downloadFile`, and the directory-create branch in `processRemoteFiles`). Fixed: both now reject any resolved path that doesn't stay inside `fs.getAppDir()`.
- [x] **Path traversal via `/zip-folder` (read anywhere on disk)** — `FileController.java`. Fixed: rejects (400) if the resolved folder isn't inside `appDir`.
- [x] **Path traversal via `/upload` and `/upload/chunk` merge (write anywhere on disk)** — `FileController.java`. Fixed: both reject filenames containing `..` or a path separator.
- [x] **Docker build is currently broken** — `Dockerfile`. Fixed: now copies `sync-0.0.8.jar`, matching `pom.xml`.
- [x] **Storage limit race under concurrent uploads** — `StorageService.java`. Fixed: `reserveSpace` is now `synchronized`.
- [x] **Filecode hashing isn't thread-safe** — `FileService.java`. Fixed: `getHash(...)` now creates a fresh `MessageDigest` per call instead of reusing a shared field.

### Should fix
- [ ] **No tests for any of the risky logic** — only test in the repo is the context-load smoke test. At minimum add a test for the `appDir.startsWith(...)` traversal guard (so the three bugs above can't silently regress) and one for the sync diff logic.
- [x] **Three overlapping download implementations** — `/resource` (`FileController.java:88`), `/download` (`:404`), `/download/manual` (`:447`) duplicate ETag/Range handling and already disagree on headers. Fixed: collapsed into one `/download` endpoint using Spring's `ResourceRegion`/`HttpRange.toResourceRegions` (see improvement plan item 1). Bonus: this also fixes a latent bug where peer-to-peer sync resumable downloads sent a `Range` header that the old `/download` silently ignored (always returned 200, so resume never actually resumed).
- [ ] **~500 lines of dead, commented-out code** — `NetworkUtil.java:117-314`, `FileService.java:100-190`. Delete. (The `FileController.java` dead block and the dead `RangeInputStream`/`ThrottledInputStream` classes it referenced are already gone as part of the download-endpoint consolidation above.)
- [ ] **`StorageService.getCurrentUsedSpace()` walks the whole shared folder on every call** — `StorageService.java:29-40`. Called by every `/api/storage/status` poll and every upload start; gets slower as the shared folder grows. Consider tracking a running total instead of a full `Files.walk` each time.
- [ ] **`/upload` silently swallows failures** — `FileController.java:196-208`. Catches `IOException`/`InvalidPathException` per file, logs, and still returns success to the client.

### Nice to have
- [ ] Remove leftover debug print — `PageController.java:43` (`System.out.println(ipMap)`), runs on every page load.

---

## Improvement plan — same functionality, less code / fewer dependencies

Researched against what Spring Boot 4.1 / JDK 25 already provide, so these are "use what's already there" options, not new frameworks. None of this has been applied yet — flagging trade-offs for a decision before touching working code.

1. ~~Replace the three hand-rolled download endpoints with Spring's built-in range support.~~ **Done.** `FileController` now has two `@GetMapping("/download")` methods on the same path: one for plain requests (returns `Resource`, ETag/Last-Modified 304 support) and one matched only `headers = "Range"` (returns `List<ResourceRegion>` via `HttpRange.toResourceRegions`, handling single-range, multi-range `multipart/byteranges`, and stale `If-Range` automatically). Split in two because `ResourceRegionHttpMessageConverter` needs a concrete declared generic return type to be selected — a single method returning `ResponseEntity<?>` fails at runtime with `HttpMessageNotWritableException` (verified by running the app and hitting it with curl). Removed `/resource`, the old single-method `/download`, `/download/manual`, the hand-rolled `streamRange` helper, and the dead commented-out Range implementation + `RangeInputStream`/`ThrottledInputStream`. `code.js`'s global-search download link and the chunked-download UI were updated to call the single endpoint (also fixed a bug where that link used a nonexistent `f.fileCode` field instead of the server-provided `f.url`). Bonus fix verified live: peer-to-peer sync's resumable download sends a `Range` header that the old `/download` silently ignored (always 200, so resume never resumed) — now correctly gets back `206` with the right byte range.

2. **Reconsider `oshi-core-ffm` for CPU/memory metrics.** **Decided: keep OSHI.** The JDK's `com.sun.management.OperatingSystemMXBean` would drop the dashboard's network upload/download speed readout (no per-NIC byte counters) — a real feature loss the user doesn't want. No change.

3. ~~Check whether `springdoc-openapi-starter-webmvc-ui` (Swagger) is actually used.~~ **Decided: keep it.** Confirmed needed for testing the APIs independently of the UI — no change.

4. **Fold the byte-counting servlet wrappers into `Content-Length` where possible.** **Decided: keep the wrappers.** `TrafficTrackingFilter` also covers `/zip-stream`, `/zip-folder`, and multi-range `/download` responses, none of which send a `Content-Length` header (they're streamed with an unknown/not-yet-known size) — a `Content-Length`-based counter would silently under-count exactly the traffic this feature exists to measure. The current wrapper is correct for every case; not worth trading that for less code.

All decided items are applied except #1, which changed internal implementation only (same URLs, headers, and behavior from the client's perspective, aside from fixing the sync-resume bug noted above).
