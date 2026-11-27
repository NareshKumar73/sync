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

20. ~~**No `response.ok` check or `try/catch` on ~12 fetches**~~ —
    `loadNodes`, `addNode`, `deleteNode`, `testNode`, `loadConflicts`,
    `loadChats`, `sendChatMessage`, `deleteChat`, `clearAllChats`,
    `loadClipboards`, `sendClipboardItem`, `deleteClipboard`,
    `clearAllClipboards`. **Fixed:** all 12 now check `res.ok` and wrap the
    call in `try/catch`, matching `fetchMetrics`/`updateStorageStrip`'s
    existing pattern — a failed request now shows a toast or logs instead of
    throwing unhandled or silently doing nothing.
21. ~~**Duplicated file-extension arrays**~~ — `code.js:43-47` and `75-79`.
    **Fixed:** both now read from one shared `FILE_EXT_GROUPS` constant.
22. ~~**Duplicated clipboard-copy-with-fallback logic**~~ — `copyCurrentUrl`
    vs `copyToClipboard`. **Fixed:** `copyCurrentUrl` now just calls
    `copyToClipboard(null, text, message)`; `copyToClipboard` falls back to
    a toast when no button is given instead of touching `btn.innerHTML`.
23. ~~**O(n²) `innerHTML +=` in render loops**~~ — `loadNodes`,
    `loadConflicts`, `loadClipboards`, chat rendering. **Fixed:** all four
    now build an array via `.map()` and assign `innerHTML` once via
    `.join('')`. `appendChatMessage` (single live message) and
    `renderChatMessages` (bulk) now share one `buildChatHtml()` string
    builder instead of the bulk path calling the single-append path N times.
24. ~~**`CHUNK_SIZE` defined twice**~~ — **Fixed:** removed the local
    redeclaration in `fetchDownloadChunk()`; it now uses the one top-level
    `const` already defined for upload.
25. ~~**Leftover debug `console.log`s**~~ — **Fixed:** removed all three.
26. ~~**Hundreds of repeated inline `style="color: var(--md-sys-color-*)"`**~~
    — **Fixed:** mechanically converted every *pure* single-property
    occurrence (76 in `index.html`, 4 in `history.html`) to the matching
    utility class (`.text-primary`, `.text-danger`, etc.), adding one
    missing class (`.text-tertiary`) to `design.css`. Mixed styles like
    `style="font-size: 18px; color: ...)"` were deliberately left alone —
    the utility classes only cover `color` on its own. The conversion script
    introduced two duplicate `class="..."` attributes (where another
    attribute sat between `class` and the old `style`) — caught and fixed
    both by hand before verifying.
27. ~~**`history.html` duplicates markup instead of using a Thymeleaf
    fragment**~~ — **Fixed:** the transfer-type badge block is now a
    `th:fragment="typeBadge"` referenced via `th:insert="~{::typeBadge}"`
    from the Raw tab. Left as a **parameterless** fragment on purpose: a
    parameterized version (`typeBadge(history)`, called as
    `~{::typeBadge(${history})}`) parsed and ran, but the call-site's
    `history` argument silently didn't bind the way the fragment body
    expected, breaking `history.type` lookups inside it — easy to not notice
    since Thymeleaf only throws once that line is reached. Parameterless
    works because both tables already bind their own `history` loop
    variable via `th:each`, so the fragment just shares that context
    directly; verified by fetching `/history` and checking the response
    wasn't truncated (an in-template exception mid-stream shows up as the
    client seeing an incomplete response, not a clean 500). The surrounding
    `<tr>` row structure itself was left duplicated — the Raw table has one
    extra column (the delete action), and a shared row fragment would need
    a parameter just for that, which didn't seem worth it for one column.
28. **Dead CSS — partially fixed, one audit claim was wrong.**
    `.theme-chip`/`.theme-dot` and `.ripple`/`.mobile-overflow` were
    genuinely dead (zero references anywhere, confirmed by grep) and are
    now deleted. **The `.file-icon.*-icon` background-tint classes are NOT
    dead** — checked the live computed style and the background renders
    exactly as designed (`#FFF3D6` for folders, matching
    `--file-color-folder-bg`); the earlier audit's claim here was incorrect.
    No change needed there.
29. ~~**Two different timezone-picker widgets for the same feature**~~ —
    **Fixed:** `history.html` now uses the same chip-picker UI as
    `index.html` (copied the markup, plus local `selectTimezone()`/
    `updateTzLiveClock()` functions calling `history.html`'s own
    `formatHistoryDates()` instead of `index.html`'s `formatDates()` —
    kept duplicated rather than shared, matching `history.html`'s existing
    pattern of being fully self-contained with its own copy of `showToast`
    etc., not pulling in `code.js`).

Verified live end-to-end after a full server restart (not just unit-level):
reloaded both pages, exercised the search/filter/icon-color/delete-button
rendering on `index.html`, fetched `/history` directly and confirmed the
response completes without truncation, opened the new timezone picker there
and confirmed clicking a chip updates the zone label, `localStorage`, and
the visible dates — matching `index.html`'s behavior exactly.

## Frontend work, now unblocked (backend #1-3 are done)

30. ~~**Delete/rename UI**~~ — **Fixed:** each file/folder row gets a Rename
    and a Delete action button. Rename uses `prompt()` (same native-dialog
    convention this app already uses for clear-history confirmations);
    Delete uses `confirm()` and warns it moves to trash. Both call the
    matching file- or folder-level endpoint based on the row's
    `data-is-dir`, then reload.
31. ~~**Create-folder UI**~~ — **Fixed:** a "New Folder" button next to
    Upload, `prompt()`s for a name, creates it under whatever directory
    you're currently browsing (read from the `path` query param), then
    reloads.
32. ~~**Live file-list auto-refresh**~~ — **Fixed:** `connectWebSocket()` is
    now called unconditionally on page load (previously only when the chat
    modal opened) and also subscribes to `/topic/files`. On a change, it
    auto-reloads after a short toast - *unless* a modal is currently open
    (`document.body` has Bootstrap's `modal-open` class), in which case it
    only shows a toast. That guard matters: the upload and download modals
    stay open for the whole transfer, so without it a multi-file upload in
    one browser tab would get its own page reloaded out from under it by
    its own first file's completion broadcast, aborting the rest of the
    queue.

Verified live against a running instance (not just unit-level): created a
folder, renamed it, and deleted it through the actual rendered buttons
(native dialogs mocked for automation only, everything else — fetch calls,
DOM, server — real); confirmed the page auto-reloads on its own broadcast
when no modal is open, and confirmed it does *not* reload while a modal
(tested with the upload modal) is open.

## No action needed, flagged only for awareness

- `FileService.listDirectory`/`listDirectoryRecursive` return the whole
  directory in one JSON response with no pagination. Fine at current scale;
  revisit only for a shared folder with tens of thousands of files in one
  directory.
- Security in the templates themselves is clean: no `th:utext` anywhere in
  `index.html`/`history.html` — every dynamic value goes through
  auto-escaping `th:text`. The XSS issues above (#7-10) are all in
  `code.js`'s own `innerHTML` building, not the Thymeleaf templates.
