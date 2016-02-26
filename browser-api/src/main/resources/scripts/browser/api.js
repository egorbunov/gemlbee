function useAPI(apiFunction, mimeType, data, callback) {
    if (!navigator.cookieEnabled) {
        ErrorResult({error: true, msg: "Cookies should be enabled for correct work."});
        return
    }

    data.function = apiFunction;
    $.ajax({
        url: "/api",
        type: "POST",
        data: data,
        mimeType: mimeType,

        success: function (data, textStatus, XMLHttpRequest) {
            callback(data);
        },

        error: function (XMLHttpRequest) {
            var responseText = XMLHttpRequest.responseText;
            if (responseText == "") {
                // TODO investigation required
                console.error("Empty response for request " + apiFunction + " with data <br>" + JSON.stringify(data));
            } else {
                console.error("Error response for request " + apiFunction + " with data <br>" + JSON.stringify(data) + "<br>" + responseText);
                ErrorResult({error: true, msg: responseText})
            }
        }
    });
}
