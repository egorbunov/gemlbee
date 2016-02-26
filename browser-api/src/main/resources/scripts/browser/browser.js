function executeButtonClick() {
    submitInput();
}

function getLocation() {
    var hash = location.hash;
    if (hash != "" && hash.charAt(0) == '#') {
        var s = hash.substring(1, hash.length);
        return decodeURI(s);
    }
    return null;
}

function initialize() {
    // Add key listener
    var body = $("body");
    // Process keyboard events
    body.keydown(processKey);

    // Allow selection only with SHIFT key pressed
    document.onkeydown = function (e) {
        if (!tracksShown()) {
            return;
        }

        //keycode for shift
        if (e.keyCode == 16) {
            // Enable selection
            configureRangeSelector(true);

            // Disable selection at all
            document.onkeyup = function () {
                configureRangeSelector(false);
            }
        }
    };

    // Bind dragging and scrolling
    var browser = $("#genome-browser");
    // Clear
    browser.empty();
    drawProgress();

    browser.bind('mousewheel DOMMouseScroll', processBrowserMouseWheel);

    // Configure dragging
    browser.draggable({axis: 'x', distance: 10, scroll: false});
    browser.on("dragstart", browserStartDragNDrop);
    browser.on("dragstop", browserEndDragNDrop);
    browser.on("drag", browserDrag);

    REQUEST_IN_PROGRESS = true;

    // Init location
    if (getLocation() == null) {
        initSession(null);
    } else {
        checkHash();
    }
}

var SPINNER;
function drawProgress() {
    if (tracksShown()) {
        var browser = $('#genome-browser');
        // Add darkening
        var fade = $('<div id="genome-browser-tracks-loading"></div>')
            .css('width', browser.width() + 'px')
            .css('height', browser.height() + 'px')
            .css('left', '0px')
            .css('top', browser.position().top + 'px');
        fade.addClass('progress-fade');
        SPINNER = new Spinner({color: '#FFF'}).spin();
        fade.append(SPINNER.el);
        $("#genome-browser-panel").append(fade);
    } else {
        SPINNER = new Spinner({scale: 0.6, color: '#000'}).spin();
        // Use hardcoded position
        $(SPINNER.el).css('top', '37px');
        $('#genome-browser-input').append(SPINNER.el);
    }
}

function hideProgress() {
    $('#genome-browser-tracks-loading').remove();
    SPINNER.stop();
}

function configureRangeSelector(enabled) {
    var tracks = $('#genome-browser-tracks');
    tracks.imgAreaSelect(!enabled ? {disable: true}
        : {
        maxHeight: tracks.height(),
        minHeight: tracks.height(),
        autoHide: true,
        onSelectEnd: zoom,
        enable: true
    });
}


function tracksShown() {
    return !REQUEST_IN_PROGRESS && GENOME_BROWSER_STATE != null && $('#genome-browser-tracks').length > 0;
}

function drawPositionHandle() {
    if (!tracksShown()) {
        return;
    }
    var position = $('#genome-browser-position');
    if (position.length == 0) {
        var panel = $('#genome-browser-panel');
        position = $('<div id="genome-browser-position"></div>').
            // Here we should explicitly set position attribute, because it is being overridden by draggable ui
            css('position', 'absolute').
            css('top', ($('#genome-browser').position().top + GENOME_BROWSER_STATE.positionHandlerY) + 'px').
            css('height', (GENOME_BROWSER_STATE.pointerHeight + 1) + 'px');
        position.draggable({axis: "x", distance: 10, scroll: false});

        position.on("dragstart", positionStartDragNDrop);
        position.on("dragstop", positionEndDragNDrop);

        panel.append(position);
    }
    // Move position handler according to GENOME_BROWSER_STATE start and end
    var browser = $('#genome-browser');
    var width = browser.width();
    var left = browser.position().left + Math.floor(width * GENOME_BROWSER_STATE.start / GENOME_BROWSER_STATE.length);
    var handlerWidth = Math.floor(width * (GENOME_BROWSER_STATE.end - GENOME_BROWSER_STATE.start) / GENOME_BROWSER_STATE.length) + 1;
    position.css('width', handlerWidth).css('left', left);
}
