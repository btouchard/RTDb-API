var express     = require('express');
var app         = express();
var server      = require('http').Server(app);
var morgan      = require('morgan');
var path        = require("path");
var config      = require('./config');
var api         = require('rtdb-api')(config, app, server);

var port = process.env.PORT || config.port || 3000;
app.use(morgan('dev'));


app.get('/', function(req, res) {
    res.sendFile(path.join(__dirname+'/app.html'));
});

server.listen(port);
app.use('/api', api);

console.log('Magic happens at http://' + config.host + ':' + port);
