var REQUEST_ID = -1;
var GENOME_BROWSER_STATE = null;

function processResult(data) {
    if (data.error) {
        return ErrorResult(data);
    }
    if (data.type == "Init") {
        return Init(data);
    }
    if (data.type == "Initialized") {
        return Initialized(data);
    }
    if (data.type == "ChangeLocation") {
        return ChangeLocation(data);
    }
    if (data.type == "Processing") {
        return Processing(data);
    }
    if (data.type == "Show") {
        return Show(data);
    }
    if (data.type == "Ignored") {
      // Ignore
      return null
    }
    return ErrorResult({error: true, msg: "Unknown result: " + data});
}

function Init(data) {
    if (isNaN(data.id)) {
        console.error("Data id for check is NaN: " + data.id);
        initSession(null);
        return
    }
    setTimeout(function () {
        initSession(data.id)
    }, 2000);
}

function Initialized(data) {
    // Append completion
    $("#text-edit-line").autocomplete({
        // Limit autocomplete size to max 20 items
        source: function (request, response) {
            var results = $.ui.autocomplete.filter(data.completion, request.term);
            response(results.slice(0, 20));
        },
        select: submitInput
    }).keyup(function (e) {
        // Close completion list on enter
        if (e.which === 13) {
            $(".ui-menu-item").hide();
        }
    });

    /*  Despite of 2 calls on startup, because of initial checkHash() on hash changed, and explicitly called
     this allows correctly refresh web page, otherwise hash won't change and request show result won't be fetched.*/
    location.hash = encodeURI(data.location);
    checkHash();
}

function ChangeLocation(data) {
    // If location already changed
    if (!isNaN(REQUEST_ID) && REQUEST_ID > data.id) {
        console.info("REQUEST_ID: " + REQUEST_ID + "; data.id: " + data.id);
        return;
    }
    /*  Despite of 2 calls on startup, because of initial checkHash() on hash changed, and explicitly called
     this allows correctly refresh web page, otherwise hash won't change and request show result won't be fetched.*/
    location.hash = encodeURI(data.location);
    checkHash();
}

function Processing(data) {
    if (isNaN(data.id)) {
        console.error("Data id for check is NaN: " + data.id);
        return
    }
    // Update actual request id
    REQUEST_ID = isNaN(REQUEST_ID) ? data.id : Math.max(REQUEST_ID, data.id);
    setTimeout(function () {
        // If still actual
        if (REQUEST_ID == data.id) {
          var pathName = document.location.pathname;
          console.info("Check " + pathName + " for id " + data.id);
          useAPI("CHECK", "json", {name: pathName, id: data.id}, processResult);
        }
      }, 300);
}

function Show(data) {
    // If location already changed
    if (REQUEST_ID > data.id) {
        return;
    }
    useAPI("SHOW", "image/png;base64", {name: document.location.pathname, id: data.id}, function (image) {
        if (image.length == 0) {
            // Already cancelled
            return
        }
        var browser = $('#genome-browser');
        browser.empty();
        browser.html('<img id="genome-browser-tracks" disabled="true" src="data:image/png;base64,' + image + '"/>');
        // Move it back
        browser.css('left', 0);
        browser.show();
        useAPI("STATE", "json", {name: document.location.pathname}, function (data) {
            GENOME_BROWSER_STATE = data;
            drawPositionHandle();
        });
        setRequestReady();
    });
}

function ErrorResult(data) {
    console.error("ERROR", data);
    $('#genome-browser-position').remove();
    $('#genome-browser').html(
        "<div id='error' class='content alert alert-danger alert-dismissible fade in error' role='alert' align='center'> \
        <button type='button' class='close' data-dismiss='alert' aria-label='Close'><span aria-hidden='true'>×</span></button>\
        <strong>" + data.msg + "</strong><br>\
        </div>");
    $('#genome-browser').show();
    setRequestReady();
}

