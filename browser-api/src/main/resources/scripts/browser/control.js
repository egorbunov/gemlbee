function processBrowserMouseWheel(event) {
    // Shift scroll to not interrupt browser behavior
    if (!event.shiftKey) {
        return;
    }
    var tracks = $('#genome-browser-tracks');
    if (tracks.length == 0) {
        return;
    }
    if (event.originalEvent.wheelDelta > 0 || event.originalEvent.detail < 0) {
        newRequest("zoom in");
    }
    else {
        newRequest("zoom out");
    }
    event.stopPropagation();
}

function processKey(event) {
    // Ignore keys in case of text-edit-line or completion is active
    if (document.activeElement.id == "text-edit-line") {
        return true;
    }
    switch (event.which) {
        case 37: // Left arrow
            newRequest("scroll left");
            event.stopPropagation();
            break;
        case 39: // Right arrow
            newRequest("scroll right");
            event.stopPropagation();
            break;
        case 107: // Plus
        case 187:
            newRequest("zoom in");
            event.stopPropagation();
            break;
        case 109: // Plus
        case 189:
            newRequest("zoom out");
            event.stopPropagation();
            break;
    }
}

var DRAG_X = 0;

function positionStartDragNDrop(event, ui) {
    DRAG_X = $('#genome-browser-position').position().left - event.pageX;
}

function positionEndDragNDrop(event, ui) {
    var tracks = $('#genome-browser-tracks');
    var width = (tracks.width());
    var start = Math.round((DRAG_X + event.pageX - tracks.position().left) / width * GENOME_BROWSER_STATE.length);
    var end = start + GENOME_BROWSER_STATE.end - GENOME_BROWSER_STATE.start;
    var scrollStart = Math.max(0, start);
    var scrollEnd = Math.min(GENOME_BROWSER_STATE.length, end);
    if (scrollStart < scrollEnd) {
        newRequest("dragndrop " + scrollStart + " " + scrollEnd);
    }
}

function browserStartDragNDrop(event, ui) {
    event.stopPropagation();
    DRAG_X = $('#genome-browser-tracks').position().left - event.pageX;
}

function getBrowserDragStart(event, length) {
    var tracks = $('#genome-browser-tracks');
    var width = tracks.width();
    return GENOME_BROWSER_STATE.start - Math.round((DRAG_X + event.pageX - tracks.position().left) / width * length);
}

function browserEndDragNDrop(event, ui) {
    event.stopPropagation();
    var length = GENOME_BROWSER_STATE.end - GENOME_BROWSER_STATE.start;
    var start = getBrowserDragStart(event, length);
    var end = start + length;
    var scrollStart = Math.max(0, start);
    var scrollEnd = Math.min(GENOME_BROWSER_STATE.length, end);
    if (scrollStart < scrollEnd) {
        newRequest("dragndrop " + scrollStart + " " + scrollEnd);
    }
}

function browserDrag(event, ui) {
    event.stopPropagation();
    // Move position handler according to start and end
    var tracks = $('#genome-browser-tracks');
    var width = tracks.width();
    var length = GENOME_BROWSER_STATE.end - GENOME_BROWSER_STATE.start;
    var left = tracks.position().left + Math.floor(width * getBrowserDragStart(event, length) / GENOME_BROWSER_STATE.length);
    $('#genome-browser-position').css('left', left);
}

// This function is called on onhashchange and onresize as well
function checkHash() {
    var l = getLocation();
    if (l != null) {
        $("#text-edit-line").val(l);
        newRequest(l);
    }
}

function submitInput() {
    var queryString = $("#text-edit-line").val();
    location.hash = encodeURI(queryString);
    $("#input-form").submit()
}
