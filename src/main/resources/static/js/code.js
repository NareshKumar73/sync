let downloadAllBtn = document.getElementById("download-selected");

let checkedbox = document.querySelectorAll(".filecode>input[type=checkbox]");

let downloadForm = document.getElementById("download-form");

let downloadableFileList = document.getElementById("file-array");

function showDownloadSelected() {
	let checked = document.querySelectorAll(".filecode>input[type=checkbox]:checked")
	
	if (checked.length > 0)
		downloadAllBtn.style.visibility = "visible";
	else
		downloadAllBtn.style.visibility = "hidden";	
}

function init() {
	
	checkedbox.forEach((c) => { c.addEventListener("click", (e) => {
		showDownloadSelected();
		console.log(e.target.value);
	})});
};

init();


function selectAllCheckBox() {
	checkedbox.forEach((el) => el.checked = event.currentTarget.checked) 
	showDownloadSelected();
}

function sendDownloadList() {
	let checked = document.querySelectorAll(".filecode>input[type=checkbox]:checked")

	let files = [];
	
	checked.forEach((el) => {
		files.push(el.value);
	})	
	
	console.log(files);
	
	downloadableFileList.value = JSON.stringify(files);
	
    downloadForm.submit();
}