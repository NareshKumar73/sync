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
- [ ] **Three overlapping download implementations** — `/resource` (`FileController.java:88`), `/download` (`:404`), `/download/manual` (`:447`) duplicate ETag/Range handling and already disagree on headers. Pick one (see improvement plan below for a built-in replacement) and delete the others.
- [ ] **~500 lines of dead, commented-out code** — `FileController.java:285-402`, `NetworkUtil.java:117-314`, `FileService.java:100-190`, and `RangeInputStream.java` / `ThrottledInputStream.java` (both classes only referenced from inside that dead comment block). Delete.
- [ ] **`StorageService.getCurrentUsedSpace()` walks the whole shared folder on every call** — `StorageService.java:29-40`. Called by every `/api/storage/status` poll and every upload start; gets slower as the shared folder grows. Consider tracking a running total instead of a full `Files.walk` each time.
- [ ] **`/upload` silently swallows failures** — `FileController.java:196-208`. Catches `IOException`/`InvalidPathException` per file, logs, and still returns success to the client.

### Nice to have
- [ ] Remove leftover debug print — `PageController.java:43` (`System.out.println(ipMap)`), runs on every page load.

---

## Improvement plan — same functionality, less code / fewer dependencies

Researched against what Spring Boot 4.1 / JDK 25 already provide, so these are "use what's already there" options, not new frameworks. None of this has been applied yet — flagging trade-offs for a decision before touching working code.

1. **Replace the three hand-rolled download endpoints with Spring's built-in range support.**
   Spring Framework has shipped `HttpRange.toResourceRegions(ranges, resource)` + a `ResourceRegion`/`List<ResourceRegion>` return type since 4.3, auto-handled by the `ResourceRegionHttpMessageConverter` that Spring Boot registers for you — single range, multi-range (`multipart/byteranges`), and plain 200 downloads all go through it. That's the same behavior `/download/manual`'s ~180 lines of manual `FileChannel`/`ByteBuffer` streaming and boundary-writing code implement by hand. One controller method returning `ResourceRegion` (or the plain resource when there's no `Range` header) can replace `/resource`, `/download`, and `/download/manual`, cutting roughly 350 lines of controller code to well under 50. ([Spring `HttpRange` javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/http/HttpRange.html))

2. **Reconsider `oshi-core-ffm` for CPU/memory metrics.**
   `SystemMetricsService` uses OSHI for three things: CPU load, memory, and network throughput. Since JDK 14, `com.sun.management.OperatingSystemMXBean` (cast from `ManagementFactory.getOperatingSystemMXBean()`) exposes `getCpuLoad()`, `getTotalMemorySize()`, and `getFreeMemorySize()` directly in the JDK — no extra dependency, no native FFM bindings, no `--enable-native-access=ALL-UNNAMED` flag in `pom.xml` (currently set in two places: the Spring Boot Maven plugin and Surefire). ([OpenJDK `OperatingSystemMXBean` docs](https://cr.openjdk.org/~alanb/sc-jdk26/api/jdk.management/com/sun/management/OperatingSystemMXBean.html))
   Trade-off: that JDK interface has no per-NIC byte-counters, so the network upload/download speed metric would need to go (or be read some other OS-specific way). This is a real feature loss, not a free win — worth a decision rather than doing it silently. If network throughput stays a requirement, keep OSHI as-is.

3. ~~Check whether `springdoc-openapi-starter-webmvc-ui` (Swagger) is actually used.~~ **Decided: keep it.** Confirmed needed for testing the APIs independently of the UI — no change.

4. **Fold the byte-counting servlet wrappers into `Content-Length` where possible.**
   `CountingHttpServletRequestWrapper`/`CountingHttpServletResponseWrapper` (~150 lines) wrap every request/response stream byte-by-byte to feed `TrafficTrackingFilter`. For the common case — a request/response with a known `Content-Length` (which covers ordinary file uploads and downloads, the bulk of this app's traffic) — the size is already available from `request.getContentLengthLong()` / the response's `Content-Length` header without wrapping any stream. The wrappers would only be needed as a fallback for chunked/unknown-length bodies. Smaller change than #1–#3, and only worth doing if traffic accounting is a hot path worth trimming; the current code is correct, just more code than the common case needs.

None of these change user-visible behavior except #2's network-metric trade-off. Suggest tackling the "Must fix" security list first; the improvement-plan items are independent and can be picked off separately.
