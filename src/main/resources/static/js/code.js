const listContainer = document.getElementById('file-list-container');
const fileItems = Array.from(document.querySelectorAll('.file-item'));
const selectionBar = document.getElementById('selection-bar');
const countSpan = document.getElementById('selected-count');
const checkboxes = document.querySelectorAll('.file-check');

function copyToClipboard(text) {
    navigator.clipboard.writeText(text).then(() => {
        // Optional: Show a tiny toast or alert
        alert("IP Copied: " + text);
    }).catch(err => {
        console.error('Could not copy text: ', err);
    });
}

// --- Search Filter ---
function filterFiles() {
    const query = document.getElementById('searchInput').value.toLowerCase();
    fileItems.forEach(item => {
        const name = item.getAttribute('data-name');
        if (name.includes(query)) {
            item.style.display = 'grid'; // Maintain grid layout
        } else {
            item.style.display = 'none';
        }
    });
}

// --- Sorting ---
function sortFiles() {
    const criteria = document.getElementById('sortSelect').value;
    
    const sortedItems = fileItems.sort((a, b) => {
        if (criteria === 'name') {
            return a.dataset.name.localeCompare(b.dataset.name);
        } else if (criteria === 'size') {
            // Parse raw bytes (requires rawSize in data attribute)
            return parseInt(b.dataset.size) - parseInt(a.dataset.size);
        } else if (criteria === 'date') {
            // String comparison works for ISO dates, otherwise requires parsing
            return b.dataset.date.localeCompare(a.dataset.date);
        }
    });

    // Re-append to container
    listContainer.innerHTML = '';
    sortedItems.forEach(item => listContainer.appendChild(item));
}

// --- Selection Logic ---
function updateSelection() {
    const checked = document.querySelectorAll('.file-check:checked');
    const count = checked.length;
    
    countSpan.innerText = count;
    
    if (count > 0) {
        selectionBar.classList.add('active');
    } else {
        selectionBar.classList.remove('active');
    }
}

function toggleAll(source) {
    checkboxes.forEach(cb => {
        // Only check visible items (respect filter)
        if(cb.closest('.file-item').style.display !== 'none'){
            cb.checked = source.checked;
        }
    });
    updateSelection();
}

function submitZipDownload() {
    const checked = document.querySelectorAll('.file-check:checked');
    let files = [];
    checked.forEach((el) => files.push(el.value));
    
    document.getElementById("file-array").value = JSON.stringify(files);
    document.getElementById("download-form").submit();
}

// --- Preview Logic ---
function openPreview(url, name) {
    const modal = new bootstrap.Modal(document.getElementById('previewModal'));
    const title = document.getElementById('previewTitle');
    const body = document.getElementById('previewBody');
    const dlBtn = document.getElementById('previewDownloadBtn');

    title.innerText = name;
    dlBtn.href = url;
    body.innerHTML = ''; // Clear previous

    const ext = name.split('.').pop().toLowerCase();
    
    if (['jpg', 'jpeg', 'png', 'gif', 'webp'].includes(ext)) {
        body.innerHTML = `<img src="${url}" class="preview-content" alt="Preview">`;
    } else if (['mp4', 'webm'].includes(ext)) {
        body.innerHTML = `<video controls autoplay class="preview-content"><source src="${url}"></video>`;
    } else if (['pdf', 'txt'].includes(ext)) {
        body.innerHTML = `<iframe src="${url}" class="preview-content"></iframe>`;
    } else {
        body.innerHTML = `<div class="p-5 text-center text-muted">Preview not available for this file type.<br>Please download to view.</div>`;
    }

    modal.show();
    
    // Stop video when modal closes
    document.getElementById('previewModal').addEventListener('hidden.bs.modal', () => {
        body.innerHTML = '';
    });
}

// let downloadAllBtn = document.getElementById("download-selected");

// let checkedbox = document.querySelectorAll(".filecode>input[type=checkbox]");

// let downloadForm = document.getElementById("download-form");

// let downloadableFileList = document.getElementById("file-array");

// function showDownloadSelected() {
// 	let checked = document.querySelectorAll(".filecode>input[type=checkbox]:checked")
	
// 	if (checked.length > 0)
// 		downloadAllBtn.style.visibility = "visible";
// 	else
// 		downloadAllBtn.style.visibility = "hidden";	
// }

// function init() {
	
// 	checkedbox.forEach((c) => { c.addEventListener("click", (e) => {
// 		showDownloadSelected();
// 		console.log(e.target.value);
// 	})});
// };

// init();


// function selectAllCheckBox() {
// 	checkedbox.forEach((el) => el.checked = event.currentTarget.checked) 
// 	showDownloadSelected();
// }

// function sendDownloadList() {
// 	let checked = document.querySelectorAll(".filecode>input[type=checkbox]:checked")

// 	let files = [];
	
// 	checked.forEach((el) => {
// 		files.push(el.value);
// 	})	
	
// 	console.log(files);
	
// 	downloadableFileList.value = JSON.stringify(files);
	
//     downloadForm.submit();
// }