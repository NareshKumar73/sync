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
                    globalHtml += `<div class="mb-3 p-3 bg-white rounded-3 border">
                        <div class="d-flex justify-content-between mb-2 border-bottom pb-2">
                            <span class="fw-bold text-dark"><i class="bi bi-hdd-network me-2 text-primary"></i>Node: ${result.node.ipAddress}</span>
                            <a href="http://${result.node.ipAddress}:${result.node.port}/" target="_blank" class="badge bg-primary text-decoration-none">Open Node</a>
                        </div>`;
                        
                    matchedFiles.forEach(f => {
                        let iconClass = f.directory ? 'bi-folder-fill text-warning' : 'bi-file-earmark-text text-info';
                        globalHtml += `<div class="d-flex justify-content-between align-items-center py-1">
                            <span class="small text-secondary"><i class="bi ${iconClass} me-2"></i>${f.relativePath || f.name}</span>
                            <a href="http://${result.node.ipAddress}:${result.node.port}/resource?filecode=${f.fileCode}" class="btn btn-sm btn-light py-0" title="Download"><i class="bi bi-download"></i></a>
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
        body.innerHTML = `<div class="p-5 text-center text-muted"><i class="bi bi-file-earmark-x display-1 d-block mb-3"></i><p>Preview not available for .${fileType} files.</p></div>`;
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
            return; // abort this file
        }
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
                    <button class="btn btn-sm btn-outline-danger border-0" onclick="deleteNode(${node.id})" title="Delete"><i class="bi bi-trash"></i></button>
                    <a href="http://${node.ipAddress}:${node.port}/" target="_blank" class="btn btn-sm btn-outline-success border-0 ms-1" title="Browse Remote Files"><i class="bi bi-box-arrow-up-right"></i></a>
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
    alert(result.message);
}

async function deleteNode(id) {
    await fetch('/api/nodes/' + id, { method: 'DELETE' });
    loadNodes();
}

// Sync Controls
async function triggerSync() {
    await fetch('/api/sync/trigger', { method: 'POST' });
    alert('Sync triggered successfully.');
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
        // Find node IP. We need base URL. We can fetch active nodes or just prompt.
        // For simplicity, we just pass the URL we know from the conflict, but meta only has relative URL.
        // We'll extract host from `meta.url` if it was absolute, but it's relative.
        // Actually the backend needs `baseUrl`. Let's just trigger a full overwrite from UI if possible.
        // Since `syncConflicts` is just a map, we can change backend to resolve without baseUrl if we store the remote IP or just ping again.
        // For now, let's just show them.
        
        container.innerHTML += `
            <div class="d-flex justify-content-between align-items-center mb-2 p-2 rounded" style="background: rgba(255, 255, 255, 0.05);">
                <div>
                    <span class="d-block fw-bold text-light">${meta.name} <small class="text-secondary">(${path})</small></span>
                    <span class="text-muted">Remote Size: ${meta.size} | Remote Date: ${meta.lastModified}</span>
                </div>
                <button class="btn btn-sm btn-outline-warning" onclick="alert('To resolve, delete local file and sync again.')">Resolve</button>
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
        alert("Failed to fetch system IPs.");
    }
}

let qrcodeObj = null;

function showQrCode() {
    const currentUrl = window.location.href;
    const qrContainer = document.getElementById('qrcode');
    
    if (!qrcodeObj) {
        qrcodeObj = new QRCode(qrContainer, {
            text: currentUrl,
            width: 200,
            height: 200,
            colorDark : "#000000",
            colorLight : "#ffffff",
            correctLevel : QRCode.CorrectLevel.H
        });
    } else {
        qrcodeObj.clear();
        qrcodeObj.makeCode(currentUrl);
    }
    
    new bootstrap.Modal(document.getElementById('qrModal')).show();
}

function copyCurrentUrl() {
    navigator.clipboard.writeText(window.location.href).then(() => {
        alert("Link copied to clipboard!");
    });
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
        pauseBtn.innerHTML = `<i class="bi bi-pause me-1"></i> Pause`;
        pauseBtn.className = 'btn btn-warning';
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
        btn.innerHTML = `<i class="bi bi-pause me-1"></i> Pause`;
        btn.className = 'btn btn-warning';
        document.getElementById('downloadStatusText').innerText = `Downloading ${downloadState.filename}...`;
        fetchDownloadChunk();
    } else {
        downloadState.paused = true;
        if (currentDownloadController) {
            currentDownloadController.abort();
        }
        btn.innerHTML = `<i class="bi bi-play me-1"></i> Resume`;
        btn.className = 'btn btn-success';
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
            ${isMe ? `<button class="btn btn-sm btn-link text-danger shadow-none p-1 ms-1 mb-1" onclick="deleteChat(${chat.id})"><i class="bi bi-trash"></i></button>` : ''}
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

async function loadClipboards() {
    const res = await fetch('/api/clipboard');
    const items = await res.json();
    const container = document.getElementById('clipboardItems');
    container.innerHTML = '';
    items.forEach(item => {
        const time = new Date(item.timestamp).toLocaleString();
        container.innerHTML += `
            <div class="mb-3 p-3 bg-white rounded border">
                <div class="d-flex justify-content-between align-items-center mb-2">
                    <small class="text-muted">${item.senderIp || 'Unknown'} - ${time}</small>
                    <div>
                        <button class="btn btn-sm btn-outline-secondary py-0 px-2 me-1" onclick="copyToClipboard(this, \`${item.content.replace(/`/g, '\\`')}\`)"><i class="bi bi-clipboard"></i> Copy</button>
                        <button class="btn btn-sm btn-outline-danger py-0 px-2" onclick="deleteClipboard(${item.id})"><i class="bi bi-trash"></i></button>
                    </div>
                </div>
                <div class="text-dark" style="white-space: pre-wrap; word-wrap: break-word;">${item.content}</div>
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
    navigator.clipboard.writeText(text).then(() => {
        const originalText = btn.innerHTML;
        btn.innerHTML = '<i class="bi bi-check2"></i> Copied!';
        btn.classList.replace('btn-outline-secondary', 'btn-success');
        btn.classList.add('text-white');
        setTimeout(() => {
            btn.innerHTML = originalText;
            btn.classList.replace('btn-success', 'btn-outline-secondary');
            btn.classList.remove('text-white');
        }, 2000);
    });
}

// Theme & Background Management
let petalInterval = null;

function managePetals(themeName) {
    if (themeName === 'girl') {
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
            alert("Image is too large to save permanently. It will only apply for this session.");
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
        alert("Please provide an image URL or upload a file.");
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