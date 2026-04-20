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
    let select = document.getElementById('sortSelect');
    let sortBy = select.value;
    let container = document.getElementById('file-list-container');
    let items = Array.from(container.getElementsByClassName('file-item'));

    items.sort(function(a, b) {
        if (sortBy === 'name') {
            return a.getAttribute('data-name').toLowerCase().localeCompare(b.getAttribute('data-name').toLowerCase());
        } else if (sortBy === 'name-desc') {
            return b.getAttribute('data-name').toLowerCase().localeCompare(a.getAttribute('data-name').toLowerCase());
        } else if (sortBy === 'date') {
            return parseInt(b.getAttribute('data-date')) - parseInt(a.getAttribute('data-date'));
        } else if (sortBy === 'date-asc') {
            return parseInt(a.getAttribute('data-date')) - parseInt(b.getAttribute('data-date'));
        } else if (sortBy === 'size') {
            return parseInt(b.getAttribute('data-size')) - parseInt(a.getAttribute('data-size'));
        } else if (sortBy === 'size-asc') {
            return parseInt(a.getAttribute('data-size')) - parseInt(b.getAttribute('data-size'));
        }
    });

    for (let i = 0; i < items.length; i++) {
        container.appendChild(items[i]);
    }
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