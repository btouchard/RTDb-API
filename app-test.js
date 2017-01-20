// =======================
// get the packages we need
// =======================
var express     = require('express');
var app         = express();
var server      = require('http').Server(app);
var morgan      = require('morgan');
var path        = require("path");
var config      = require('./app-test-config');
var api         = require('./rtdb-api')(config, app, server);

// =======================
// configuration =========
// =======================
var port = process.env.PORT || config.port || 3000;
// use morgan to log requests to the console
app.use(morgan('dev'));


// =======================
// Test Route ============
// =======================
app.get('/', function(req, res) {
    res.sendFile(path.join(__dirname+'/app-test.html'));
});

// =======================
// start the server ======
// =======================
server.listen(port);
app.use('/api', api);

console.log('Magic happens at http://' + config.host + ':' + port);
