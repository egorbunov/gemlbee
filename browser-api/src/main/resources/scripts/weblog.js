var Weblog = React.createClass({
    load: function () {
        bindJson(this);
    },
    getInitialState: function () {
        return {data: "Waiting for log content..."};
    },
    componentDidMount: function () {
        this.load();
        setInterval(this.load, this.props.pollInterval);
    },
    render: function () {
        return <pre>{this.state.data}</pre>;
    }
});

React.render(
    <Weblog url="/weblog" pollInterval={5000}/>,
    document.getElementById('weblog')
);