// Use this function to bind react js classes
function bindJson(thisArg) {
    $.ajax({
        url: thisArg.props.url,
        dataType: 'json',
        cache: false,
        success: function (data) {
            thisArg.setState({data: data});
        }.bind(thisArg),
        error: function (xhr, status, err) {
            console.error(thisArg.props.url, status, err.toString());
        }.bind(thisArg)
    })
}