# TODO

Full-stack reassessment (2026-11-26): backend feature ideas from the previous
pass, plus a fresh frontend audit (`code.js`, `index.html`, `history.html`,
`design.css`). Sorted into three buckets: backend work that's ready to start,
frontend work that's ready to start, and frontend work that can't start until
a backend item above it exists. Nothing in this file is implemented yet.

## Ready now — Backend

1. ~~**Delete and rename/move files or folders.**~~ **Fixed:** `FileController`
   now has `DELETE /files` + `PUT /files/rename` (by filecode) and
   `DELETE /folder` + `PUT /folder/rename` (by relative path, matching
   `/zip-folder`'s existing convention for folder-level operations) — all
   reusing the `resolved.normalize().startsWith(appDir)` guard. Delete moves
   to trash rather than hard-deleting (see #4).
2. ~~**Create-folder endpoint.**~~ **Fixed:** `POST /folder?relativePath=...`.
3. ~~**Broadcast a "file list changed" event on the existing STOMP broker**~~ —
   **Fixed:** `FileController` and `SyncService` both inject
   `SimpMessagingTemplate` and send a plain `"changed"` payload to
   `/topic/files` after upload, chunked-upload completion, the new
   delete/rename/create-folder endpoints, and once per `triggerSync()` run
   (not per file, to avoid flooding the topic during a big sync).
4. ~~**Trash instead of hard delete**~~ — **Fixed:** `FileService.moveToTrash()`
   moves the file/folder into `data/.trash` (preserving its relative path,
   `Files.move` handles a whole directory tree in one call) instead of
   deleting; used by both the file and folder delete endpoints.
5. **Optional shared secret between sync nodes.** Still open — you had no
   preference when asked, so going with the smaller default (skip): nodes
   still trust each other by IP:port only. `/api/nodes` accepts any peer
   with zero authentication; revisit if this ever needs to run on something
   less trusted than a LAN.
6. ~~**Remove dead config**~~ — **Fixed:** removed `upd.port=11111` from
   `application.properties`.

Verified live against a running instance: created/renamed/deleted both a
file and a folder, confirmed deletes land in `data/.trash` with the right
relative path, confirmed both path-traversal guards (`../evil` on create,
`../../evil.txt` on rename) and the can't-delete-the-shared-root guard all
return 400, and connected a real STOMP/SockJS client to `/topic/files` to
confirm the broadcast fires with payload `"changed"` after a create-folder
call.

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
11. ~~**Resumable download buffers the whole file in RAM**~~ —
    `code.js:864-1041`. **Fixed:** when the browser supports the File System
    Access API (`showSaveFilePicker`), each chunk is written straight to the
    picked file via a `FileSystemWritableFileStream` instead of being
    collected in `downloadState.chunks[]`; that array now only exists as the
    fallback for browsers without the API, matching prior behavior exactly
    there. `cancelDownload()` aborts the writable too. Verified with a
    harness that loads the real function and mocks `showSaveFilePicker`/
    `fetch`: a simulated 17MB download writes 4 chunks (5+5+5+2MB) straight
    through with `chunks[]` staying empty throughout, closes the stream on
    completion, and `cancelDownload()` aborts it mid-transfer.
12. ~~**`filterFiles()` can throw and abort mid-search**~~ — `code.js:162`.
    **Fixed:** added the same `|| ''` guard `assignFileIconClasses()` already
    has.
13. ~~**Preview video doesn't autoplay**~~ — `code.js:339`. **Fixed:** added
    `video.muted = true` before `video.autoplay = true`.
14. ~~**Chat avatars need outbound internet**~~ — `code.js:1153`. **Fixed:**
    default avatars are now generated locally (an inline SVG data URI with
    the sender's initial on a color derived from their name) instead of
    calling `api.dicebear.com`.
15. ~~**`filterByType(type)` reads the implicit global `event`**~~ —
    `code.js:73`. **Fixed:** now takes `event` as a parameter; the 6 `onclick`
    handlers in `index.html` pass it explicitly.

## Ready now — Frontend: accessibility

16. ~~**9 of 10 modal close buttons have no `aria-label`**~~ —
    `index.html:274,321,352,370,401,468,506,546,567,603`. **Fixed:** all 11
    (counting the QR modal that already had one) now have
    `aria-label="Close"`.
17. ~~**Per-file selection checkboxes have no accessible name**~~ —
    `index.html:211`. **Fixed:** added `th:aria-label="${'Select ' + file.name}"`.
18. ~~**Icon-only file-action buttons rely on `title` only**~~ —
    `index.html:230-245`. **Fixed:** added a matching `aria-label` to each
    (preview/resumable-download/direct-download/folder-zip).
19. ~~**Labels never associated to inputs via `for`/`id`**~~ — e.g.
    `index.html:325,472,514,518,571,579`. **Fixed:** added `for="<id>"` to
    each (also caught one more of the same bug at `:575`, `bgImageFile`,
    not in the original list).

Verified live: loaded the real rendered page and checked every close button,
file checkbox, and action button has an `aria-label`, and every listed
`<label>` now resolves via `document.querySelector('label[for="..."]')`. No
console errors from the new `th:aria-label` Thymeleaf attribute.

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

## Frontend work, now unblocked (backend #1-3 are done)

30. **Delete/rename UI** (buttons, confirm dialog) — backend ready:
    `DELETE`/`PUT .../rename` on `/files` and `/folder`.
31. **Create-folder UI** (input + button next to upload) — backend ready:
    `POST /folder`.
32. **Live file-list auto-refresh** (subscribe to `/topic/files` the same
    way chat already subscribes to `/topic/public`, re-render on message
    instead of requiring a manual refresh) — backend ready.

## No action needed, flagged only for awareness

- `FileService.listDirectory`/`listDirectoryRecursive` return the whole
  directory in one JSON response with no pagination. Fine at current scale;
  revisit only for a shared folder with tens of thousands of files in one
  directory.
- Security in the templates themselves is clean: no `th:utext` anywhere in
  `index.html`/`history.html` — every dynamic value goes through
  auto-escaping `th:text`. The XSS issues above (#7-10) are all in
  `code.js`'s own `innerHTML` building, not the Thymeleaf templates.
