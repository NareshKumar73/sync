// Utility and existing functions
function filterFiles() {
    let input = document.getElementById('searchInput');
    let filter = input.value.toLowerCase();
    let items = document.getElementsByClassName('file-item');
    for (let i = 0; i < items.length; i++) {
        let name = items[i].getAttribute('data-name');
        if (name.indexOf(filter) > -1) {
            items[i].style.display = "flex";
        } else {
            items[i].style.display = "none";
        }
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
                    <button class="btn btn-sm btn-outline-danger border-0" onclick="deleteNode(${node.id})"><i class="bi bi-trash"></i></button>
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