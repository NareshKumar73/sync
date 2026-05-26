// ===================================================================
// SYNC-KING — Material You Expressive JavaScript
// ===================================================================

// Toast Notification System (replaces alert())
function showToast(message, duration = 3000) {
    const container = document.getElementById('toastContainer');
    if (!container) return;
    const toast = document.createElement('div');
    toast.className = 'toast-m3';
    toast.innerHTML = `<span class="material-symbols-rounded" style="font-size:20px;">info</span> ${message}`;
    container.appendChild(toast);
    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateX(40px)';
        toast.style.transition = 'all 300ms ease';
        setTimeout(() => toast.remove(), 300);
    }, duration);
}

// File Type Icon Assignment
function assignFileIconClasses() {
    const fileItems = document.querySelectorAll('.file-item');
    const imageExts = ['jpg','jpeg','png','gif','svg','webp','bmp','ico'];
    const videoExts = ['mp4','webm','ogg','avi','mkv','mov','flv'];
    const docExts = ['pdf','doc','docx','txt','md','html','json','xml','csv','xls','xlsx','ppt','pptx'];
    const archiveExts = ['zip','rar','7z','tar','gz','bz2'];
    const audioExts = ['mp3','wav','ogg','flac','aac','m4a','wma'];
    
    fileItems.forEach(item => {
        const name = (item.getAttribute('data-name') || '').toLowerCase();
        const isDir = item.getAttribute('data-is-dir') === 'true';
        const iconEl = item.querySelector('.file-icon');
        if (!iconEl) return;
        
        if (isDir) {
            iconEl.classList.add('folder-icon');
        } else {
            const ext = name.split('.').pop();
            if (imageExts.includes(ext)) iconEl.classList.add('image-icon');
            else if (videoExts.includes(ext)) iconEl.classList.add('video-icon');
            else if (docExts.includes(ext)) iconEl.classList.add('document-icon');
            else if (archiveExts.includes(ext)) iconEl.classList.add('archive-icon');
            else if (audioExts.includes(ext)) iconEl.classList.add('audio-icon');
            else iconEl.classList.add('generic-icon');
        }
    });
}

// File Type Chip Filter
function filterByType(type) {
    // Update chip active states
    document.querySelectorAll('.chip').forEach(c => c.classList.remove('active'));
    event.currentTarget.classList.add('active');
    
    const imageExts = ['jpg','jpeg','png','gif','svg','webp','bmp','ico'];
    const videoExts = ['mp4','webm','ogg','avi','mkv','mov','flv'];
    const docExts = ['pdf','doc','docx','txt','md','html','json','xml','csv','xls','xlsx','ppt','pptx'];
    const archiveExts = ['zip','rar','7z','tar','gz','bz2'];
    const audioExts = ['mp3','wav','ogg','flac','aac','m4a','wma'];
    
    const items = document.querySelectorAll('.file-item');
    items.forEach(item => {
        const name = (item.getAttribute('data-name') || '').toLowerCase();
        const isDir = item.getAttribute('data-is-dir') === 'true';
        
        if (type === 'all') {
            item.style.display = 'flex';
            return;
        }
        
        const ext = name.split('.').pop();
        let match = false;
        
        if (type === 'image') match = imageExts.includes(ext);
        else if (type === 'video') match = videoExts.includes(ext);
        else if (type === 'document') match = docExts.includes(ext) || isDir;
        else if (type === 'archive') match = archiveExts.includes(ext);
        else if (type === 'audio') match = audioExts.includes(ext);
        
        item.style.display = match ? 'flex' : 'none';
    });
}

// Storage Stats Strip
async function updateStorageStrip() {
    try {
        const res = await fetch('/api/storage/status');
        const storage = await res.json();
        const usedGB = ((storage.used + storage.reserved) / (1024**3)).toFixed(1);
        const limitGB = (storage.limit / (1024**3)).toFixed(0);
        const percent = ((storage.used + storage.reserved) / storage.limit) * 100;
        
        const text = document.getElementById('storageStripText');
        if (text) text.innerText = `${usedGB} GB / ${limitGB} GB`;
        
        const ring = document.getElementById('storageRing');
        if (ring) {
            const circumference = 2 * Math.PI * 15.91549430918954;
            const offset = circumference - (percent / 100) * circumference;
            ring.style.strokeDasharray = circumference;
            ring.style.strokeDashoffset = offset;
        }
    } catch(e) {
        const text = document.getElementById('storageStripText');
        if (text) text.innerText = 'Unavailable';
    }
}

// App Bar Elevation on Scroll + FAB Hide on Scroll
let lastScrollY = 0;
window.addEventListener('scroll', () => {
    // Navbar elevation
    const nav = document.querySelector('.glass-nav');
    if (nav) {
        nav.classList.toggle('elevated', window.scrollY > 10);
    }
    
    // FAB hide on scroll down
    const fab = document.getElementById('uploadFab');
    if (fab) {
        if (window.scrollY > lastScrollY && window.scrollY > 200) {
            fab.style.transform = 'translateY(200px)';
            fab.style.transition = 'transform 300ms cubic-bezier(0.2, 0, 0, 1)';
        } else {
            fab.style.transform = 'translateY(0)';
            fab.style.transition = 'transform 300ms cubic-bezier(0.05, 0.7, 0.1, 1.0)';
        }
    }
    lastScrollY = window.scrollY;
});

// Utility and existing functions
async function filterFiles() {
    const input = document.getElementById('searchInput');
    const scope = document.getElementById('searchScope');
    if(!input) return;
    
    const filter = input.value.toLowerCase();
    
    const items = document.getElementsByClassName('file-item');
    for (let i = 0; i < items.length; i++) {
        const name = items[i].getAttribute('data-name').toLowerCase();
        if (name.includes(filter)) {
            items[i].style.display = 'flex';
        } else {
            items[i].style.display = 'none';
        }
    }
    
    // Handle Global Search
    const globalContainer = document.getElementById('global-search-results');
    const globalResults = document.getElementById('global-search-container');
    
    if (scope && scope.value === 'global' && filter.length >= 2) {
        if (globalContainer) globalContainer.classList.remove('d-none');
        if (globalResults) globalResults.innerHTML = '<div class="text-center p-3"><div class="spinner-border spinner-border-sm text-primary"></div><small class="ms-2 text-secondary">Searching nodes...</small></div>';
        
        try {
            const res = await fetch('/api/nodes');
            const nodes = await res.json();
            const activeNodes = nodes.filter(n => n.isWorking);
            
            if (activeNodes.length === 0) {
                globalResults.innerHTML = '<div class="alert alert-secondary small py-2">No active network nodes found.</div>';
                return;
            }
            
            const searchPromises = activeNodes.map(node => 
                fetch(`http://${node.ipAddress}:${node.port}/files/recursive`)
                    .then(r => r.json())
                    .then(data => ({ node, files: data.files || [] }))
                    .catch(e => ({ node, error: true }))
            );
            
            const results = await Promise.all(searchPromises);
            
            let globalHtml = '';
            let totalGlobalFound = 0;
            
            results.forEach(result => {
                if (result.error || !result.files) return;
                
                const matchedFiles = result.files.filter(f => f.name.toLowerCase().includes(filter));
                if (matchedFiles.length > 0) {
                    globalHtml += `<div class="mb-3 p-3 rounded-3" style="background: var(--md-sys-color-surface-container-low); border: 1px solid var(--md-sys-color-outline-variant);">
                        <div class="d-flex justify-content-between mb-2 border-bottom pb-2">
                            <span class="fw-bold" style="color: var(--md-sys-color-on-surface);"><span class="material-symbols-rounded me-2" style="color: var(--md-sys-color-primary); font-size: 18px;">dns</span>Node: ${result.node.ipAddress}</span>
                            <a href="http://${result.node.ipAddress}:${result.node.port}/" target="_blank" class="badge" style="background: var(--md-sys-color-primary); color: var(--md-sys-color-on-primary); text-decoration: none;">Open Node</a>
                        </div>`;
                        
                    matchedFiles.forEach(f => {
                        let iconName = f.directory ? 'folder' : 'description';
                        let iconColor = f.directory ? 'var(--file-color-folder)' : 'var(--md-sys-color-secondary)';
                        globalHtml += `<div class="d-flex justify-content-between align-items-center py-1">
                            <span class="small" style="color: var(--md-sys-color-on-surface-variant);"><span class="material-symbols-rounded me-2" style="font-size: 16px; color: ${iconColor};">${iconName}</span>${f.relativePath || f.name}</span>
                            <a href="http://${result.node.ipAddress}:${result.node.port}/resource?filecode=${f.fileCode}" class="btn btn-sm py-0" style="color: var(--md-sys-color-primary);" title="Download"><span class="material-symbols-rounded" style="font-size: 18px;">download</span></a>
                        </div>`;
                        totalGlobalFound++;
                    });
                    globalHtml += `</div>`;
                }
            });
            
            if (totalGlobalFound > 0) {
                globalResults.innerHTML = globalHtml;
            } else {
                globalResults.innerHTML = `<div class="alert alert-secondary small py-2">No matches found on remote nodes.</div>`;
            }
            
        } catch(e) {
            globalResults.innerHTML = '<div class="alert alert-danger small py-2">Error connecting to network nodes.</div>';
        }
    } else {
        if (globalContainer) globalContainer.classList.add('d-none');
    }
}

function sortFiles() {
    const select = document.getElementById('sortSelect');
    if (!select) return;
    
    const sortBy = select.value;
    localStorage.setItem('fileSortOrder', sortBy);
    
    const container = document.getElementById('file-list-container');
    if (!container) return;
    
    const items = Array.from(container.getElementsByClassName('file-item'));
    if (items.length === 0) return;

    items.sort((a, b) => {
        const aVal = a.dataset;
        const bVal = b.dataset;

        const aIsDir = aVal.isDir === 'true';
        const bIsDir = bVal.isDir === 'true';

        if (aIsDir !== bIsDir) {
            return aIsDir ? -1 : 1;
        }

        let result = 0;
        switch (sortBy) {
            case 'name':
                result = (aVal.name || '').localeCompare(bVal.name || '');
                break;
            case 'name-desc':
                result = (bVal.name || '').localeCompare(aVal.name || '');
                break;
            case 'date':
                result = (parseInt(bVal.date) || 0) - (parseInt(aVal.date) || 0);
                break;
            case 'date-asc':
                result = (parseInt(aVal.date) || 0) - (parseInt(bVal.date) || 0);
                break;
            case 'size':
                result = (parseInt(bVal.size) || 0) - (parseInt(aVal.size) || 0);
                break;
            case 'size-asc':
                result = (parseInt(aVal.size) || 0) - (parseInt(bVal.size) || 0);
                break;
        }
        return result;
    });

    // Re-append items in sorted order
    items.forEach(item => container.appendChild(item));
}

function toggleAll(source) {
    let checkboxes = document.getElementsByClassName('file-check');
    for (let i = 0; i < checkboxes.length; i++) {
        checkboxes[i].checked = source.checked;
    }
    updateSelection();
}

function updateSelection() {
    let checkboxes = document.getElementsByClassName('file-check');
    let count = 0;
    for (let i = 0; i < checkboxes.length; i++) {
        if (checkboxes[i].checked) count++;
    }
    
    document.getElementById('selected-count').innerText = count;
    let selectionBar = document.getElementById('selection-bar');
    
    if (count > 0) {
        selectionBar.classList.add('show');
    } else {
        selectionBar.classList.remove('show');
        document.getElementById('select-all').checked = false;
    }
}

function submitZipDownload() {
    document.getElementById('download-form').submit();
}

function openPreview(url, name, fileType) {
    document.getElementById('previewTitle').innerText = name;
    document.getElementById('previewDownloadBtn').href = url;
    let body = document.getElementById('previewBody');
    body.innerHTML = ''; // clear

    fileType = (fileType || '').toLowerCase();
    
    if (['jpg', 'jpeg', 'png', 'gif', 'svg', 'webp'].includes(fileType)) {
        let img = document.createElement('img');
        img.src = url;
        img.style.maxWidth = '100%';
        img.style.maxHeight = '80vh';
        img.style.objectFit = 'contain';
        body.appendChild(img);
    } else if (['mp4', 'webm', 'ogg'].includes(fileType)) {
        let video = document.createElement('video');
        video.src = url;
        video.controls = true;
        video.autoplay = true;
        video.style.maxWidth = '100%';
        video.style.maxHeight = '80vh';
        body.appendChild(video);
    } else if (['pdf', 'txt', 'md', 'html', 'json'].includes(fileType)) {
        let iframe = document.createElement('iframe');
        iframe.src = url;
        iframe.style.width = '100%';
        iframe.style.height = '80vh';
        iframe.style.border = 'none';
        iframe.style.background = '#fff';
        body.appendChild(iframe);
    } else {
        body.innerHTML = `<div class="p-5 text-center" style="color: var(--md-sys-color-on-surface-variant);"><span class="material-symbols-rounded d-block mb-3" style="font-size: 72px;">draft</span><p>Preview not available for .${fileType} files.</p></div>`;
    }

    new bootstrap.Modal(document.getElementById('previewModal')).show();
}

// Resumable Chunked Upload
const CHUNK_SIZE = 5 * 1024 * 1024; // 5MB chunks

async function startChunkedUpload() {
    const fileInput = document.getElementById('fileInput');
    const files = fileInput.files;
    
    if (files.length === 0) return;

    document.getElementById('uploadProgressContainer').classList.remove('d-none');
    
    for (let i = 0; i < files.length; i++) {
        await uploadFile(files[i]);
    }
    
    document.getElementById('uploadStatusText').innerText = "All uploads complete!";
    setTimeout(() => {
        window.location.reload();
    }, 1500);
}

async function uploadFile(file) {
    const totalChunks = Math.ceil(file.size / CHUNK_SIZE);
    document.getElementById('uploadStatusText').innerText = `Uploading ${file.name}...`;
    
    const fileUuid = crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(36).substring(2) + Date.now().toString(36);

    try {
        const reserveRes = await fetch(`/api/storage/reserve?uuid=${fileUuid}&size=${file.size}`, { method: 'POST' });
        if (!reserveRes.ok) {
            const err = await reserveRes.json();
            document.getElementById('uploadStatusText').innerText = `Rejected: ${err.error} (${file.name})`;
            document.getElementById('uploadStatusText').classList.add('text-danger');
            return; // Skip this file
        }
    } catch (e) {
        console.error("Storage reservation failed", e);
        return;
    }

    for (let chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
        const start = chunkIndex * CHUNK_SIZE;
        const end = Math.min(start + CHUNK_SIZE, file.size);
        const chunk = file.slice(start, end);

        const formData = new FormData();
        formData.append('file', chunk);
        formData.append('filename', file.name);
        formData.append('relativePath', currentRelativePath || '');
        formData.append('chunkIndex', chunkIndex);
        formData.append('totalChunks', totalChunks);
        formData.append('uuid', fileUuid);

        try {
            const response = await fetch('/upload/chunk', {
                method: 'POST',
                body: formData
            });

            if (!response.ok) throw new Error('Chunk upload failed');
            
            const progress = Math.round(((chunkIndex + 1) / totalChunks) * 100);
            document.getElementById('uploadProgressBar').style.width = `${progress}%`;
            document.getElementById('uploadPercentage').innerText = `${progress}%`;

        } catch (error) {
            console.error(error);
            document.getElementById('uploadStatusText').innerText = `Failed to upload ${file.name}`;
            document.getElementById('uploadStatusText').classList.add('text-danger');
            fetch(`/api/storage/release?uuid=${fileUuid}`, { method: 'POST' });
            return; // abort this file
        }
    }
}

// System Metrics Fetcher
let metricsInterval = null;
const systemModalEl = document.getElementById('systemModal');
if (systemModalEl) {
    systemModalEl.addEventListener('show.bs.modal', () => {
        fetchMetrics();
        metricsInterval = setInterval(fetchMetrics, 2000);
    });
    systemModalEl.addEventListener('hide.bs.modal', () => {
        clearInterval(metricsInterval);
    });
}

async function fetchMetrics() {
    try {
        const [sysRes, storageRes] = await Promise.all([
            fetch('/api/system/metrics'),
            fetch('/api/storage/status')
        ]);
        const sys = await sysRes.json();
        const storage = await storageRes.json();
        
        const cpuText = document.getElementById('metric-cpu-text');
        if (cpuText) cpuText.innerText = sys.cpu + '%';
        const cpuBar = document.getElementById('metric-cpu-bar');
        if (cpuBar) cpuBar.style.width = sys.cpu + '%';
        
        const memUsedGB = (sys.memoryUsed / (1024**3)).toFixed(2);
        const memTotalGB = (sys.memoryTotal / (1024**3)).toFixed(2);
        const memPercent = (sys.memoryUsed / sys.memoryTotal) * 100;
        const memText = document.getElementById('metric-mem-text');
        if (memText) memText.innerText = `${memUsedGB} / ${memTotalGB} GB`;
        const memBar = document.getElementById('metric-mem-bar');
        if (memBar) memBar.style.width = memPercent + '%';
        
        const rxMB = (sys.networkDownload / (1024**2)).toFixed(2);
        const txMB = (sys.networkUpload / (1024**2)).toFixed(2);
        const rxText = document.getElementById('metric-net-rx');
        if (rxText) rxText.innerText = rxMB + ' MB/s';
        const txText = document.getElementById('metric-net-tx');
        if (txText) txText.innerText = txMB + ' MB/s';
        
        const limitGB = (storage.limit / (1024**3)).toFixed(2);
        const usedGB = (storage.used / (1024**3)).toFixed(2);
        const reservedGB = (storage.reserved / (1024**3)).toFixed(2);
        const remainingGB = (storage.remaining / (1024**3)).toFixed(2);
        const totalUsedGB = ((storage.used + storage.reserved) / (1024**3)).toFixed(2);
        
        const storageText = document.getElementById('metric-storage-text');
        if (storageText) storageText.innerText = `${totalUsedGB} GB / ${limitGB} GB`;
        const storageUsed = document.getElementById('metric-storage-used');
        if (storageUsed) storageUsed.style.width = (storage.used / storage.limit) * 100 + '%';
        const storageReserved = document.getElementById('metric-storage-reserved');
        if (storageReserved) storageReserved.style.width = (storage.reserved / storage.limit) * 100 + '%';
        const storageRemaining = document.getElementById('metric-storage-remaining');
        if (storageRemaining) storageRemaining.innerText = `${remainingGB} GB`;
    } catch (e) {
        console.error("Failed to fetch system metrics", e);
    }
}

// Network Nodes Management
document.getElementById('networkModal').addEventListener('show.bs.modal', function () {
    loadNodes();
    loadConflicts();
});

async function loadNodes() {
    const res = await fetch('/api/nodes');
    const nodes = await res.json();
    const tbody = document.getElementById('nodesTableBody');
    tbody.innerHTML = '';
    
    nodes.forEach(node => {
        const statusClass = node.isWorking ? 'status-online' : 'status-offline';
        const lastActive = node.lastActive ? new Date(node.lastActive).toLocaleString() : 'Never';
        
        tbody.innerHTML += `
            <tr>
                <td><span class="status-indicator ${statusClass}"></span></td>
                <td>${node.ipAddress}</td>
                <td>${node.port}</td>
                <td class="small text-muted">${lastActive}</td>
                <td>
                    <button class="file-action-btn" onclick="deleteNode(${node.id})" title="Delete"><span class="material-symbols-rounded" style="color: var(--md-sys-color-error);">delete</span></button>
                    <a href="http://${node.ipAddress}:${node.port}/" target="_blank" class="file-action-btn ms-1" title="Browse Remote Files"><span class="material-symbols-rounded" style="color: var(--md-sys-color-success);">open_in_new</span></a>
                </td>
            </tr>
        `;
    });
}

async function addNode() {
    const ip = document.getElementById('nodeIp').value;
    const port = document.getElementById('nodePort').value;
    if (!ip || !port) return;
    
    await fetch('/api/nodes', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ipAddress: ip, port: port, alias: ip })
    });
    
    document.getElementById('nodeIp').value = '';
    loadNodes();
}

async function testNode() {
    const ip = document.getElementById('nodeIp').value;
    const port = document.getElementById('nodePort').value;
    if (!ip || !port) return;
    
    const res = await fetch('/api/nodes/test', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ipAddress: ip, port: port })
    });
    const result = await res.json();
    showToast(result.message);
}

async function deleteNode(id) {
    await fetch('/api/nodes/' + id, { method: 'DELETE' });
    loadNodes();
}

// Sync Controls
async function triggerSync() {
    await fetch('/api/sync/trigger', { method: 'POST' });
    showToast('Sync triggered successfully');
}

async function toggleAutoSync() {
    const enabled = document.getElementById('autoSyncToggle').checked;
    await fetch('/api/sync/toggle?enabled=' + enabled, { method: 'POST' });
}

// Load sync state on page load
window.addEventListener('DOMContentLoaded', async () => {
    try {
        const res = await fetch('/api/sync/status');
        const status = await res.json();
        document.getElementById('autoSyncToggle').checked = status.enabled;
    } catch(e) {}
    
    let savedSort = localStorage.getItem('fileSortOrder') || 'name';
    let select = document.getElementById('sortSelect');
    if (select) {
        select.value = savedSort;
        sortFiles();
    }
    
    // Assign file icon classes based on extension
    assignFileIconClasses();
    
    // Update storage strip
    updateStorageStrip();
});

async function loadConflicts() {
    const res = await fetch('/api/sync/conflicts');
    const conflictsMap = await res.json();
    const container = document.getElementById('conflictsContainer');
    container.innerHTML = '';
    
    if (Object.keys(conflictsMap).length === 0) {
        container.innerHTML = '<span class="text-muted">No conflicts detected.</span>';
        return;
    }

    for (const [path, meta] of Object.entries(conflictsMap)) {
        container.innerHTML += `
            <div class="d-flex justify-content-between align-items-center mb-2 p-2 rounded" style="background: rgba(255, 255, 255, 0.05);">
                <div>
                    <span class="d-block fw-bold text-light">${meta.name} <small class="text-secondary">(${path})</small></span>
                    <span class="text-muted">Remote Size: ${meta.size} | Remote Date: ${meta.lastModified}</span>
                </div>
                <button class="btn btn-sm btn-outline-warning" onclick="showToast('To resolve, delete local file and sync again.')">Resolve</button>
            </div>
        `;
    }
}

// System IPs and QR Code functions
async function showSystemIps() {
    try {
        const res = await fetch('/ip/system');
        const ipMap = await res.json();
        const tbody = document.getElementById('ipTableBody');
        tbody.innerHTML = '';
        
        for (const [nic, ip] of Object.entries(ipMap)) {
            tbody.innerHTML += `
                <tr>
                    <td class="fw-medium">${nic}</td>
                    <td>${ip}</td>
                </tr>
            `;
        }
        new bootstrap.Modal(document.getElementById('ipModal')).show();
    } catch (error) {
        console.error("Failed to fetch system IPs", error);
        showToast('Failed to fetch system IPs', 4000);
    }
}

async function showQrCode() {
    const qrContainer = document.getElementById('qrCodesContainer');
    qrContainer.innerHTML = ''; // clear previous QRs

    try {
        const res = await fetch('/ip/system');
        const ipMap = await res.json();
        
        const port = window.location.port ? ':' + window.location.port : '';
        const path = window.location.pathname + window.location.search;
        const protocol = window.location.protocol;

        if (Object.keys(ipMap).length === 0) {
            throw new Error("No IPs found");
        }

        let isFirst = true;
        for (const [nic, ip] of Object.entries(ipMap)) {
            const url = `${protocol}//${ip}${port}${path}`;
            
            const itemDiv = document.createElement('div');
            itemDiv.className = `carousel-item ${isFirst ? 'active' : ''}`;
            itemDiv.innerHTML = `
                <div class="d-flex justify-content-center">
                    <div class="text-center">
                        <div class="p-3 bg-white rounded-4 shadow-sm mb-2" style="border: 2px solid var(--border-color);">
                            <div id="qr-${ip.replace(/\./g, '-')}" class="d-flex justify-content-center"></div>
                        </div>
                        <div class="fw-medium text-light">${nic}</div>
                        <div class="small text-secondary">${ip}</div>
                    </div>
                </div>
            `;
            qrContainer.appendChild(itemDiv);

            new QRCode(document.getElementById(`qr-${ip.replace(/\./g, '-')}`), {
                text: url,
                width: 180,
                height: 180,
                colorDark : "#000000",
                colorLight : "#ffffff",
                correctLevel : QRCode.CorrectLevel.H
            });
            isFirst = false;
        }

        // Hide carousel controls if only one QR code
        const prevBtn = document.querySelector('#qrCarousel .carousel-control-prev');
        const nextBtn = document.querySelector('#qrCarousel .carousel-control-next');
        const numItems = Object.keys(ipMap).length;
        if (numItems <= 1) {
            if (prevBtn) prevBtn.style.display = 'none';
            if (nextBtn) nextBtn.style.display = 'none';
        } else {
            if (prevBtn) prevBtn.style.display = '';
            if (nextBtn) nextBtn.style.display = '';
        }

    } catch (e) {
        console.error('Failed to load system IPs for QR codes', e);
        // Fallback to current URL if fetching IPs fails
        const url = window.location.href;
        const itemDiv = document.createElement('div');
        itemDiv.className = 'carousel-item active';
        itemDiv.innerHTML = `
            <div class="d-flex justify-content-center">
                <div class="text-center">
                    <div class="p-3 bg-white rounded-4 shadow-sm mb-2" style="border: 2px solid var(--border-color);">
                        <div id="qr-fallback" class="d-flex justify-content-center"></div>
                    </div>
                    <div class="fw-medium text-light">Current URL</div>
                </div>
            </div>
        `;
        qrContainer.appendChild(itemDiv);

        new QRCode(document.getElementById('qr-fallback'), {
            text: url,
            width: 180,
            height: 180,
            colorDark : "#000000",
            colorLight : "#ffffff",
            correctLevel : QRCode.CorrectLevel.H
        });

        const prevBtn = document.querySelector('#qrCarousel .carousel-control-prev');
        const nextBtn = document.querySelector('#qrCarousel .carousel-control-next');
        if (prevBtn) prevBtn.style.display = 'none';
        if (nextBtn) nextBtn.style.display = 'none';
    }
    
    new bootstrap.Modal(document.getElementById('qrModal')).show();
}

function copyCurrentUrl() {
    const text = window.location.href;
    if (navigator.clipboard && window.isSecureContext) {
        navigator.clipboard.writeText(text).then(() => {
            showToast('Link copied to clipboard!');
        });
    } else {
        const textArea = document.createElement("textarea");
        textArea.value = text;
        textArea.style.position = "absolute";
        textArea.style.left = "-999999px";
        document.body.appendChild(textArea);
        textArea.select();
        try {
            document.execCommand('copy');
            showToast('Link copied to clipboard!');
        } catch (error) {
            console.error("Failed to copy URL", error);
        }
        document.body.removeChild(textArea);
    }
}

// Resumable Download UI Logic
let currentDownloadController = null;
let downloadState = {
    url: '',
    filename: '',
    totalBytes: 0,
    downloadedBytes: 0,
    paused: false,
    chunks: []
};

async function startDownloadInUI(url, filename, size) {
    console.log('Starting download:', { url, filename, size });
    
    if (!url || !filename) {
        console.error('Missing download info');
        return;
    }

    downloadState = {
        url: url,
        filename: filename,
        totalBytes: parseInt(size) || 0,
        downloadedBytes: 0,
        paused: false,
        chunks: []
    };
    
    const statusText = document.getElementById('downloadStatusText');
    const percentageText = document.getElementById('downloadPercentage');
    const progressBar = document.getElementById('downloadProgressBar');
    const downloadedText = document.getElementById('downloadDownloaded');
    const speedText = document.getElementById('downloadSpeed');
    const pauseBtn = document.getElementById('pauseResumeBtn');

    if (statusText) {
        statusText.innerText = `Downloading ${filename}...`;
        statusText.classList.remove('text-danger');
    }
    if (percentageText) percentageText.innerText = '0%';
    if (progressBar) progressBar.style.width = '0%';
    if (downloadedText) downloadedText.innerText = `0 MB / ${(downloadState.totalBytes / (1024*1024)).toFixed(2)} MB`;
    if (speedText) speedText.innerText = `0 MB/s`;
    
    if (pauseBtn) {
        pauseBtn.innerHTML = `<span class="material-symbols-rounded">pause</span> Pause`;
        pauseBtn.className = 'btn btn-surface';
        pauseBtn.disabled = false;
    }
    
    const modalEl = document.getElementById('downloadModal');
    if (modalEl) {
        const modal = new bootstrap.Modal(modalEl);
        modal.show();
    } else {
        console.error('Download modal not found');
    }
    
    await fetchDownloadChunk();
}

async function fetchDownloadChunk() {
    if (downloadState.paused) return;
    
    const CHUNK_SIZE = 5 * 1024 * 1024; // 5MB chunks
    const start = downloadState.downloadedBytes;
    const end = Math.min(start + CHUNK_SIZE - 1, downloadState.totalBytes - 1);
    
    if (start >= downloadState.totalBytes) {
        finishDownload();
        return;
    }
    
    currentDownloadController = new AbortController();
    
    try {
        let fetchUrl = downloadState.url;
        if (fetchUrl.includes('/download?')) {
            fetchUrl = fetchUrl.replace('/download?', '/download/manual?');
        }
        
        const startTime = Date.now();
        const response = await fetch(fetchUrl, {
            headers: {
                'Range': `bytes=${start}-${end}`
            },
            signal: currentDownloadController.signal
        });
        
        if (!response.ok && response.status !== 206) {
            throw new Error('Download failed');
        }
        
        const blob = await response.blob();
        downloadState.chunks.push(blob);
        downloadState.downloadedBytes += blob.size;
        
        const endTime = Date.now();
        const durationSeconds = (endTime - startTime) / 1000;
        let speed = 0;
        if (durationSeconds > 0) {
            speed = (blob.size / (1024 * 1024)) / durationSeconds;
        }
        
        updateDownloadUI(speed);
        
        // Continue downloading the next chunk
        fetchDownloadChunk();
    } catch (e) {
        if (e.name === 'AbortError') {
            console.log('Download chunk aborted (paused/cancelled)');
        } else {
            console.error('Download error:', e);
            document.getElementById('downloadStatusText').innerText = 'Error downloading file.';
            document.getElementById('downloadStatusText').classList.add('text-danger');
        }
    }
}

function updateDownloadUI(speed) {
    const progress = Math.round((downloadState.downloadedBytes / downloadState.totalBytes) * 100);
    document.getElementById('downloadPercentage').innerText = `${progress}%`;
    document.getElementById('downloadProgressBar').style.width = `${progress}%`;
    document.getElementById('downloadDownloaded').innerText = `${(downloadState.downloadedBytes / (1024*1024)).toFixed(2)} MB / ${(downloadState.totalBytes / (1024*1024)).toFixed(2)} MB`;
    if (speed !== undefined) {
        document.getElementById('downloadSpeed').innerText = `${speed.toFixed(2)} MB/s`;
    }
}

function toggleDownloadPause() {
    let btn = document.getElementById('pauseResumeBtn');
    if (downloadState.paused) {
        downloadState.paused = false;
        btn.innerHTML = `<span class="material-symbols-rounded">pause</span> Pause`;
        btn.className = 'btn btn-surface';
        document.getElementById('downloadStatusText').innerText = `Downloading ${downloadState.filename}...`;
        fetchDownloadChunk();
    } else {
        downloadState.paused = true;
        if (currentDownloadController) {
            currentDownloadController.abort();
        }
        btn.innerHTML = `<span class="material-symbols-rounded">play_arrow</span> Resume`;
        btn.className = 'btn btn-premium';
        document.getElementById('downloadStatusText').innerText = `Paused ${downloadState.filename}...`;
        document.getElementById('downloadSpeed').innerText = `0 MB/s`;
    }
}

function cancelDownload() {
    downloadState.paused = true;
    if (currentDownloadController) {
        currentDownloadController.abort();
    }
    downloadState.chunks = [];
}

function finishDownload() {
    document.getElementById('downloadStatusText').innerText = `Finishing download...`;
    document.getElementById('pauseResumeBtn').disabled = true;
    
    const finalBlob = new Blob(downloadState.chunks);
    const url = URL.createObjectURL(finalBlob);
    
    const a = document.createElement('a');
    a.href = url;
    a.download = downloadState.filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    
    document.getElementById('downloadStatusText').innerText = `Download Complete!`;
    document.getElementById('downloadProgressBar').classList.remove('progress-bar-animated');
    document.getElementById('downloadProgressBar').classList.add('bg-success');
    
    setTimeout(() => {
        let modal = bootstrap.Modal.getInstance(document.getElementById('downloadModal'));
        if(modal) modal.hide();
        document.getElementById('downloadProgressBar').classList.add('progress-bar-animated');
        document.getElementById('downloadProgressBar').classList.remove('bg-success');
        downloadState.chunks = []; // free memory
    }, 2000);
}

// Drag & Drop Upload Logic
const dropZone = document.getElementById('drop-zone-overlay');
let dragCounter = 0;

document.addEventListener('dragenter', (e) => {
    e.preventDefault();
    dragCounter++;
    if (dropZone) dropZone.classList.remove('d-none');
});

document.addEventListener('dragleave', (e) => {
    e.preventDefault();
    dragCounter--;
    if (dragCounter === 0 && dropZone) {
        dropZone.classList.add('d-none');
    }
});

document.addEventListener('dragover', (e) => {
    e.preventDefault();
});

document.addEventListener('drop', (e) => {
    e.preventDefault();
    dragCounter = 0;
    if (dropZone) dropZone.classList.add('d-none');
    
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
        const fileInput = document.getElementById('fileInput');
        fileInput.files = e.dataTransfer.files;
        new bootstrap.Modal(document.getElementById('uploadModal')).show();
    }
});

// Chat Profile & WebSocket Variables
let stompClient = null;
let chatProfile = {
    name: localStorage.getItem('chatProfileName') || '',
    avatar: localStorage.getItem('chatProfileAvatar') || ''
};

function toggleChatSettings() {
    const settingsDiv = document.getElementById('chatProfileSettings');
    if (settingsDiv.classList.contains('d-none')) {
        document.getElementById('chatProfileName').value = chatProfile.name;
        document.getElementById('chatProfileAvatar').value = chatProfile.avatar;
        settingsDiv.classList.remove('d-none');
    } else {
        settingsDiv.classList.add('d-none');
    }
}

function saveChatProfile() {
    chatProfile.name = document.getElementById('chatProfileName').value.trim();
    chatProfile.avatar = document.getElementById('chatProfileAvatar').value.trim();
    localStorage.setItem('chatProfileName', chatProfile.name);
    localStorage.setItem('chatProfileAvatar', chatProfile.avatar);
    toggleChatSettings();
    // Re-render chats to show updated profile
    renderChatMessages(window.currentChats || []);
}

function connectWebSocket() {
    if (stompClient && stompClient.connected) return;
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    stompClient.debug = null; // disable debug logs
    stompClient.connect({}, function (frame) {
        console.log('Connected: ' + frame);
        stompClient.subscribe('/topic/public', function (chatMessage) {
            const chat = JSON.parse(chatMessage.body);
            window.currentChats.push(chat);
            appendChatMessage(chat);
        });
    }, function(error) {
        console.error('STOMP error:', error);
        setTimeout(connectWebSocket, 5000);
    });
}

// Chat Functions
document.getElementById('chatModal')?.addEventListener('show.bs.modal', () => {
    loadChats();
    connectWebSocket();
});

window.currentChats = [];

async function loadChats() {
    const res = await fetch('/api/chat');
    window.currentChats = await res.json();
    renderChatMessages(window.currentChats);
}

function renderChatMessages(chats) {
    const container = document.getElementById('chatMessages');
    container.innerHTML = '';
    chats.forEach(chat => appendChatMessage(chat, false));
    container.scrollTop = container.scrollHeight;
}

function appendChatMessage(chat, scroll = true) {
    const container = document.getElementById('chatMessages');
    const time = new Date(chat.timestamp).toLocaleString();
    const displayName = chat.senderName || chat.senderIp || 'Anonymous';
    
    // Default avatar if none provided
    let avatarUrl = chat.senderAvatar;
    if (!avatarUrl) {
        const hash = displayName.split('').reduce((a, b) => { a = ((a << 5) - a) + b.charCodeAt(0); return a & a }, 0);
        avatarUrl = `https://api.dicebear.com/7.x/identicon/svg?seed=${hash}`;
    }

    const isMe = chatProfile.name && chat.senderName === chatProfile.name;
    const bubbleClass = isMe ? 'bg-primary text-white ms-auto' : 'bg-white text-dark border';
    const alignClass = isMe ? 'flex-row-reverse' : '';
    const textObj = isMe ? 'text-end' : 'text-start';

    const chatHtml = `
        <div class="d-flex align-items-end mb-3 ${alignClass}">
            <img src="${avatarUrl}" alt="Avatar" class="rounded-circle shadow-sm mx-2" style="width: 40px; height: 40px; object-fit: cover;">
            <div class="${textObj}" style="max-width: 75%;">
                <small class="text-muted mb-1 d-block" style="font-size: 0.75rem;">${displayName} • ${time}</small>
                <div class="p-2 rounded shadow-sm ${bubbleClass}" style="word-wrap: break-word; font-size: 0.95rem;">
                    ${chat.message}
                </div>
            </div>
            ${isMe ? `<button class="file-action-btn" onclick="deleteChat(${chat.id})" style="width:28px;height:28px;"><span class="material-symbols-rounded" style="font-size:16px;color:var(--md-sys-color-error);">delete</span></button>` : ''}
        </div>
    `;
    
    container.innerHTML += chatHtml;
    if (scroll) {
        container.scrollTop = container.scrollHeight;
    }
}

async function sendChatMessage() {
    const input = document.getElementById('chatInput');
    const msg = input.value.trim();
    if (!msg) return;
    
    if (stompClient && stompClient.connected) {
        const chatMessage = {
            message: msg,
            senderName: chatProfile.name,
            senderAvatar: chatProfile.avatar
        };
        stompClient.send("/app/chat.sendMessage", {}, JSON.stringify(chatMessage));
        input.value = '';
    } else {
        // Fallback to REST
        await fetch('/api/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ message: msg, senderName: chatProfile.name, senderAvatar: chatProfile.avatar })
        });
        input.value = '';
        loadChats();
    }
}

async function deleteChat(id) {
    if(!id) return;
    await fetch(`/api/chat/${id}`, { method: 'DELETE' });
    loadChats();
}

async function clearAllChats() {
    if (confirm('Are you sure you want to clear all chat history?')) {
        await fetch('/api/chat', { method: 'DELETE' });
        loadChats();
    }
}

// Clipboard Functions
document.getElementById('clipboardModal')?.addEventListener('show.bs.modal', loadClipboards);

function escapeHtml(unsafe) {
    return (unsafe || '').toString()
         .replace(/&/g, "&amp;")
         .replace(/</g, "&lt;")
         .replace(/>/g, "&gt;")
         .replace(/"/g, "&quot;")
         .replace(/'/g, "&#039;");
}

function copyClipboardItem(btn) {
    const text = decodeURIComponent(btn.getAttribute('data-clipboard'));
    copyToClipboard(btn, text);
}

async function loadClipboards() {
    const res = await fetch('/api/clipboard');
    const items = await res.json();
    const container = document.getElementById('clipboardItems');
    container.innerHTML = '';
    items.forEach(item => {
        const time = new Date(item.timestamp).toLocaleString();
        const safeContent = escapeHtml(item.content);
        const encodedContent = encodeURIComponent(item.content);
        container.innerHTML += `
            <div class="mb-3 p-3 rounded-3" style="background: var(--md-sys-color-surface-container-low); border: 1px solid var(--md-sys-color-outline-variant);">
                <div class="d-flex justify-content-between align-items-center mb-2">
                    <small style="color: var(--md-sys-color-on-surface-variant);">${escapeHtml(item.senderIp) || 'Unknown'} - ${time}</small>
                    <div>
                        <button class="btn btn-sm btn-outline-secondary py-0 px-2 me-1" data-clipboard="${encodedContent}" onclick="copyClipboardItem(this)"><span class="material-symbols-rounded" style="font-size:14px;">content_copy</span> Copy</button>
                        <button class="file-action-btn" style="width:28px;height:28px;display:inline-flex;" onclick="deleteClipboard(${item.id})"><span class="material-symbols-rounded" style="font-size:16px;color:var(--md-sys-color-error);">delete</span></button>
                    </div>
                </div>
                <div style="color: var(--md-sys-color-on-surface); white-space: pre-wrap; word-wrap: break-word;">${safeContent}</div>
            </div>
        `;
    });
    container.scrollTop = container.scrollHeight;
}

async function sendClipboardItem() {
    const input = document.getElementById('clipboardInput');
    const content = input.value.trim();
    if (!content) return;
    
    await fetch('/api/clipboard', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content: content })
    });
    input.value = '';
    loadClipboards();
}

async function deleteClipboard(id) {
    await fetch(`/api/clipboard/${id}`, { method: 'DELETE' });
    loadClipboards();
}

async function clearAllClipboards() {
    if (confirm('Are you sure you want to clear all clipboard history?')) {
        await fetch('/api/clipboard', { method: 'DELETE' });
        loadClipboards();
    }
}

function copyToClipboard(btn, text) {
    const onSuccess = () => {
        const originalText = btn.innerHTML;
        btn.innerHTML = '<span class="material-symbols-rounded" style="font-size:14px;">check</span> Copied!';
        btn.classList.replace('btn-outline-secondary', 'btn-success');
        btn.classList.add('text-white');
        setTimeout(() => {
            btn.innerHTML = originalText;
            btn.classList.replace('btn-success', 'btn-outline-secondary');
            btn.classList.remove('text-white');
        }, 2000);
    };

    if (navigator.clipboard && window.isSecureContext) {
        navigator.clipboard.writeText(text).then(onSuccess);
    } else {
        const textArea = document.createElement("textarea");
        textArea.value = text;
        textArea.style.position = "absolute";
        textArea.style.left = "-999999px";
        document.body.appendChild(textArea);
        textArea.select();
        try {
            document.execCommand('copy');
            onSuccess();
        } catch (error) {
            console.error("Failed to copy text", error);
        }
        document.body.removeChild(textArea);
    }
}

// Theme & Background Management
let petalInterval = null;

function managePetals(themeName) {
    if (themeName === 'blossom') {
        if (!petalInterval) {
            petalInterval = setInterval(createPetal, 400);
        }
    } else {
        if (petalInterval) {
            clearInterval(petalInterval);
            petalInterval = null;
        }
        document.querySelectorAll('.cherry-petal').forEach(p => p.remove());
    }
}

function createPetal() {
    const petal = document.createElement('div');
    petal.className = 'cherry-petal';
    petal.style.left = Math.random() * 100 + 'vw';
    petal.style.animationDuration = (Math.random() * 3 + 4) + 's';
    petal.style.opacity = Math.random() * 0.5 + 0.3;
    
    // Slight size variations
    const size = Math.random() * 10 + 10;
    petal.style.width = size + 'px';
    petal.style.height = size + 'px';
    
    document.body.appendChild(petal);
    setTimeout(() => {
        if(petal.parentNode) petal.remove();
    }, 8000);
}

function changeTheme(themeName) {
    document.documentElement.setAttribute('data-theme', themeName);
    localStorage.setItem('syncTheme', themeName);
    managePetals(themeName);
}

function applyCustomBg() {
    let urlInput = document.getElementById('bgImageUrl').value.trim();
    let fileInput = document.getElementById('bgImageFile').files[0];
    let sizeSelect = document.getElementById('bgSizeSelect').value; // format: size_position_repeat
    
    let parts = sizeSelect.split('_');
    let bgSize = parts[0];
    let bgPos = parts[1];
    let bgRepeat = parts[2];

    const saveAndApply = (bgUrl) => {
        // Warning: LocalStorage has a 5MB limit. Base64 images might exceed this.
        // We catch quota errors if the user uploads a large image.
        try {
            localStorage.setItem('syncBgImage', bgUrl);
            localStorage.setItem('syncBgSize', bgSize);
            localStorage.setItem('syncBgPos', bgPos);
            localStorage.setItem('syncBgRepeat', bgRepeat);
        } catch (e) {
            console.warn("Could not save background to localStorage, possibly too large.", e);
            showToast('Image too large to save permanently');
        }
        
        document.documentElement.style.setProperty('--body-bg-image', `url('${bgUrl}')`);
        document.documentElement.style.setProperty('--body-bg-size', bgSize);
        document.documentElement.style.setProperty('--body-bg-position', bgPos);
        document.documentElement.style.setProperty('--body-bg-repeat', bgRepeat);
        
        // Hide modal
        let modalEl = document.getElementById('bgSettingsModal');
        if (modalEl) {
            let modal = bootstrap.Modal.getInstance(modalEl);
            if(modal) modal.hide();
        }
    };

    if (fileInput) {
        let reader = new FileReader();
        reader.onload = function(e) {
            saveAndApply(e.target.result);
        };
        reader.readAsDataURL(fileInput);
    } else if (urlInput) {
        saveAndApply(urlInput);
    } else {
        showToast('Please provide an image URL or upload a file');
    }
}

function clearCustomBg() {
    localStorage.removeItem('syncBgImage');
    localStorage.removeItem('syncBgSize');
    localStorage.removeItem('syncBgPos');
    localStorage.removeItem('syncBgRepeat');
    
    document.documentElement.style.removeProperty('--body-bg-image');
    document.documentElement.style.removeProperty('--body-bg-size');
    document.documentElement.style.removeProperty('--body-bg-position');
    document.documentElement.style.removeProperty('--body-bg-repeat');
    
    document.getElementById('bgImageUrl').value = '';
    document.getElementById('bgImageFile').value = '';
    
    let modalEl = document.getElementById('bgSettingsModal');
    if (modalEl) {
        let modal = bootstrap.Modal.getInstance(modalEl);
        if(modal) modal.hide();
    }
}

function initThemeAndBg() {
    let savedTheme = localStorage.getItem('syncTheme') || 'light';
    changeTheme(savedTheme);
    
    let savedBg = localStorage.getItem('syncBgImage');
    if (savedBg) {
        document.documentElement.style.setProperty('--body-bg-image', `url('${savedBg}')`);
        document.documentElement.style.setProperty('--body-bg-size', localStorage.getItem('syncBgSize') || 'cover');
        document.documentElement.style.setProperty('--body-bg-position', localStorage.getItem('syncBgPos') || 'center');
        document.documentElement.style.setProperty('--body-bg-repeat', localStorage.getItem('syncBgRepeat') || 'no-repeat');
    }
}

async function runSpeedTest(mode) {
    const size = document.getElementById('speedTestSize').value;
    const speedEl = document.getElementById('speedTestSpeed');
    const statusEl = document.getElementById('speedTestStatus');
    const resultBox = document.getElementById('speedTestResult');
    const btnDown = document.getElementById('btnTestDownload');
    const btnUp = document.getElementById('btnTestUpload');
    
    resultBox.classList.remove('d-none');
    speedEl.innerText = '-- MB/s';
    btnDown.disabled = true;
    btnUp.disabled = true;
    
    if (mode === 'download') {
        statusEl.innerText = `Testing Download (${size}MB)...`;
        
        const startTime = Date.now();
        try {
            const response = await fetch(`/api/speedtest/download?sizeMB=${size}`);
            const blob = await response.blob();
            const endTime = Date.now();
            
            const durationSec = (endTime - startTime) / 1000;
            const sizeInBytes = blob.size;
            const speedMbps = (sizeInBytes / 1024 / 1024) / durationSec;
            
            speedEl.innerText = speedMbps.toFixed(2) + ' MB/s';
            statusEl.innerText = 'Download Test Complete';
        } catch (e) {
            speedEl.innerText = 'Error';
            statusEl.innerText = 'Failed to connect';
        }
    } else if (mode === 'upload') {
        statusEl.innerText = `Generating dummy data...`;
        
        // Generate random bytes for the file
        const totalBytes = size * 1024 * 1024;
        const array = new Uint8Array(totalBytes);
        const blob = new Blob([array], {type: 'application/octet-stream'});
        const formData = new FormData();
        formData.append('file', blob, 'speedtest.dummy');
        
        statusEl.innerText = `Testing Upload (${size}MB)...`;
        const startTime = Date.now();
        
        try {
            const xhr = new XMLHttpRequest();
            xhr.open('POST', '/api/speedtest/upload', true);
            
            xhr.upload.onprogress = function(e) {
                if (e.lengthComputable) {
                    const durationSec = (Date.now() - startTime) / 1000;
                    if(durationSec > 0.5) {
                        const speedMbps = (e.loaded / 1024 / 1024) / durationSec;
                        speedEl.innerText = speedMbps.toFixed(2) + ' MB/s';
                    }
                }
            };
            
            xhr.onload = function() {
                if (xhr.status === 200) {
                    const durationSec = (Date.now() - startTime) / 1000;
                    const speedMbps = (totalBytes / 1024 / 1024) / durationSec;
                    speedEl.innerText = speedMbps.toFixed(2) + ' MB/s';
                    statusEl.innerText = 'Upload Test Complete';
                } else {
                    speedEl.innerText = 'Error';
                    statusEl.innerText = 'Server rejected upload';
                }
                btnDown.disabled = false;
                btnUp.disabled = false;
            };
            
            xhr.onerror = function() {
                speedEl.innerText = 'Error';
                statusEl.innerText = 'Network error';
                btnDown.disabled = false;
                btnUp.disabled = false;
            };
            
            xhr.send(formData);
            return; // Exit early since XHR is async and buttons are handled
        } catch (e) {
            speedEl.innerText = 'Error';
            statusEl.innerText = 'Failed to execute';
        }
    }
    
    btnDown.disabled = false;
    btnUp.disabled = false;
}

initThemeAndBg();