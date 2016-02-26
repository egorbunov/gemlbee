var REQUEST_IN_PROGRESS = false;

function initSession(requestId) {
    var l = getLocation();
    if (requestId != null) {
        useAPI("INITIALIZE", "json", {
            name: document.location.pathname,
            id: requestId,
            query: l}, processResult);
    } else {
        useAPI("INITIALIZE", "json", {name: document.location.pathname, query: l}, processResult);
    }
}

function newRequest(query) {
    console.log("Request: " + query);
    if (!REQUEST_IN_PROGRESS) {
        drawProgress();
    }
    REQUEST_IN_PROGRESS = true;
    useAPI("REQUEST", "json", {
        name: document.location.pathname,
        query: query,
        width: $(window).width()
    }, processResult);
}

function setRequestReady() {
    hideProgress();
    REQUEST_IN_PROGRESS = false;
}

function zoom(img, selection) {
    // Ignore tiny selections or selections by mistake
    if (selection.x2 - selection.x1 < 5) {
        return;
    }
    var width = $(window).width();
    newRequest("zoom " + Math.round(100 * selection.x1 / width) + " " + Math.round(100 * selection.x2 / width))
}
