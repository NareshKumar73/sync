# TODO

Full-stack reassessment (2026-11-26): backend feature ideas from the previous
pass, plus a fresh frontend audit (`code.js`, `index.html`, `history.html`,
`design.css`). Sorted into three buckets: backend work that's ready to start,
frontend work that's ready to start, and frontend work that can't start until
a backend item above it exists. Nothing in this file is implemented yet.

## Ready now — Backend

1. **Delete and rename/move files or folders.** Still the biggest functional
   gap — no `@DeleteMapping` or rename endpoint exists anywhere in
   `FileController`. Add `DELETE /files` (by filecode, same access pattern as
   `/download`) and a rename/move endpoint reusing the existing
   `resolved.normalize().startsWith(appDir)` guard.
2. **Create-folder endpoint.** One `FileService` method + one controller
   method, same guard pattern.
3. **Broadcast a "file list changed" event on the existing STOMP broker**
   after upload, chunked-upload completion, sync download, and (once #1
   exists) delete. `WebSocketConfig`'s `/topic` broker is already running for
   chat; this just reuses it.
4. **Trash instead of hard delete**, once #1 exists — move to `data/.trash`
   instead of `Files.delete`, so an accidental delete (including one from a
   sync pull) is recoverable.
5. **Optional shared secret between sync nodes.** `/api/nodes` accepts any
   IP:port as a peer with zero authentication. Reasonable default for a LAN
   tool, but worth an opt-in per-node token. **Flagging as a trade-off
   decision for you, not auto-applying** — changes the trust model.
6. **Remove dead config** — `upd.port=11111` in `application.properties` is
   never read anywhere; leftover from an abandoned UDP-discovery feature that
   only ever existed as commented-out code (already deleted).

## Ready now — Frontend: bugs & security (do first)

7. ~~**Stored/live XSS via chat**~~ — `code.js:1163,1165,1167`. **Fixed:**
   `avatarUrl`, `displayName`, and `chat.message` now go through the
   existing `escapeHtml()` helper before being interpolated into `innerHTML`.
8. ~~**XSS via a peer's file list in global search**~~ — `code.js:207-217`.
   **Fixed:** `result.node.ipAddress` and `f.relativePath || f.name` are
   escaped; `f.url` is only used if it's a same-origin relative path
   (`startsWith('/')`), so a malicious peer can't supply a `javascript:` URI
   as the download link.
9. ~~**XSS via a peer's data in sync conflicts**~~ — `code.js:709-719`.
   **Fixed:** `meta.name`, `path`, and `meta.size` now go through
   `escapeHtml()`.
10. ~~**Local node table unescaped**~~ — `code.js:523-534`. **Fixed:**
    `node.ipAddress` now goes through `escapeHtml()` in both places it's
    rendered.
11. **Resumable download buffers the whole file in RAM** —
    `code.js:864-1041`. `fetchDownloadChunk()` collects every 5MB chunk in
    `downloadState.chunks[]` and only assembles the `Blob` at the end. The
    backend allows uploads/downloads up to 1TB — this will exhaust browser
    memory on exactly the large transfers "resumable" exists for. Needs
    streaming-to-disk (File System Access API or a service worker); not a
    one-liner, biggest frontend fix in the list.
12. **`filterFiles()` can throw and abort mid-search** — `code.js:162`. Missing
    the `|| ''` guard that the near-identical code in `assignFileIconClasses()`
    (`code.js:50`) already has.
13. **Preview video doesn't autoplay** — `code.js:339`. `video.autoplay = true`
    without `video.muted = true`; browsers block unmuted autoplay.
14. **Chat avatars need outbound internet** — `code.js:1153`. Default avatars
    call `api.dicebear.com`; on an offline LAN every avatar is a broken image
    in an app whose whole point is working without internet. Generate a
    local initials/color avatar instead.
15. **`filterByType(type)` reads the implicit global `event`** —
    `code.js:73`. Fragile; accept `event` as a parameter from the `onclick`
    handler instead.

## Ready now — Frontend: accessibility

16. **9 of 10 modal close buttons have no `aria-label`** —
    `index.html:274,321,352,370,401,468,506,546,567,603` (only the QR modal
    at `:427` has one). Screen readers announce them as unlabeled buttons.
17. **Per-file selection checkboxes have no accessible name** —
    `index.html:211`.
18. **Icon-only file-action buttons rely on `title` only** —
    `index.html:230-245` (preview/download/zip). Add matching `aria-label`.
19. **Labels never associated to inputs via `for`/`id`** — e.g.
    `index.html:325,472,514,518,571,579`.

## Ready now — Frontend: quality/maintainability

20. **No `response.ok` check or `try/catch` on ~12 fetches** — `loadNodes`,
    `addNode`, `deleteNode`, `testNode`, `loadConflicts`, `loadChats`,
    `sendChatMessage`, `deleteChat`, `clearAllChats`, `loadClipboards`,
    `sendClipboardItem`, `deleteClipboard`, `clearAllClipboards`. A failed
    request either throws unhandled or silently does nothing.
    `fetchMetrics`/`updateStorageStrip` already show the right pattern —
    apply it consistently.
21. **Duplicated file-extension arrays** — `code.js:43-47` and `75-79`. One
    shared `const`.
22. **Duplicated clipboard-copy-with-fallback logic** — `copyCurrentUrl`
    (`:840-861`) vs `copyToClipboard` (`:1286-1316`). Have the former call
    the latter.
23. **O(n²) `innerHTML +=` in render loops** — `loadNodes`, `loadConflicts`,
    `loadClipboards`, chat rendering. Build an array, assign once.
24. **`CHUNK_SIZE` defined twice** (upload `:359`, download `:927`) with the
    same value in separate scopes — share one constant.
25. **Leftover debug `console.log`s** — `code.js:875,968,1111`.
26. **Hundreds of repeated inline `style="color: var(--md-sys-color-*)"`**
    duplicating utility classes already in `design.css:442-449`
    (`.text-primary`, `.text-success`, etc.) — likely why so many theme rules
    in `design.css` need `!important` (inline styles win over class rules).
27. **`history.html` duplicates markup instead of using a Thymeleaf
    fragment** — the transfer-type badge and `<tr>` structure are
    copy-pasted between the Consolidated and Raw tabs (`:178-189` vs
    `:215-231`). No `th:fragment`/`th:insert`/`th:replace` used anywhere in
    either template.
28. **Dead CSS** — `.file-icon.*-icon` background-tint classes
    (`design.css:818-845`, never applied — `index.html:212-217` hardcodes
    inline color instead, so this component's background half never
    renders), `.theme-chip`/`.theme-dot` (`:1723-1756`, leftover from an
    earlier design), `.ripple`/`.mobile-overflow` (`:1828-1841`, `:901-910`).
    Decide per-rule: delete, or (for the file-icon tints specifically) wire
    them up since they look like an unfinished feature rather than true dead
    code.
29. **Two different timezone-picker widgets for the same feature** — custom
    chip UI over a hidden `<select>` on `index.html:50-83`, a plain visible
    `<select>` on `history.html:41-47`. Pick one.

## Frontend work blocked on a backend item above

30. **Delete/rename UI** (buttons, confirm dialog) — blocked on backend #1.
31. **Create-folder UI** (input + button next to upload) — blocked on
    backend #2.
32. **Live file-list auto-refresh** (listen for the broadcast, re-render
    without a manual click) — blocked on backend #3.

## No action needed, flagged only for awareness

- `FileService.listDirectory`/`listDirectoryRecursive` return the whole
  directory in one JSON response with no pagination. Fine at current scale;
  revisit only for a shared folder with tens of thousands of files in one
  directory.
- Security in the templates themselves is clean: no `th:utext` anywhere in
  `index.html`/`history.html` — every dynamic value goes through
  auto-escaping `th:text`. The XSS issues above (#7-10) are all in
  `code.js`'s own `innerHTML` building, not the Thymeleaf templates.
