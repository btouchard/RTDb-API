'use strict';

var rtdb = function (config, app, server) {
    // =======================
    // get the packages we need
    // =======================
    var router      = require('express').Router();
    var io          = require('socket.io')(server);
    var ioJwt       = require('socketio-jwt');
    var bodyParser  = require('body-parser');
    var upload      = require('express-fileupload');
    var path        = require("path");
    var fs          = require("fs");

    var dir = __dirname + '/medias';
    fs.stat(dir, function (err) {
        if (err && err.message && err.message.indexOf('no such file or directory') > -1)
            fs.mkdirSync(dir);
    });

    var mysql = require("mysql");
    var jwt = require('jsonwebtoken');

    // =======================
    // configuration =========
    // =======================
    var pool = mysql.createPool(config.mysql);

    app.use(bodyParser.urlencoded({ extended: true }));
    app.use(bodyParser.json());
    app.use(upload());

    // =======================
    // Socket.IO JWT =========
    // =======================
    io.use(ioJwt.authorize({
        secret: config.secret,
        handshake: true
    }));

    // =======================
    // routes ================
    // =======================
    app.get('/rtdb/rtdb-client.js', function(req, res) {
        // console.log("path", path.join(__dirname+'/client.js'));
        res.sendFile(path.join(__dirname+'/client.js'));
    });

    // route to verify a user token (POST http://localhost:8080/api/verify)
    router.get('/verify', function (req, res) {
        var token = req.headers['x-app-token'];
        if (token) {
            jwt.verify(token, config.secret, function (err, user) {
                if (err) return res.json({success: false});
                res.json({success: true, result: user});
            });
        } else {
            res.json({success: false});
        }
    });

    // route to authenticate a user (POST http://localhost:8080/api/authenticate)
    router.post('/authenticate', function (req, res) {
        pool.getConnection(function (err, con) {
            if (err) throw 'Error connecting to Db';
            // console.log('Connection established');
            con.query('SELECT id, firstname, lastname FROM user WHERE email=? AND password=?', [req.body.email, req.body.password],
                function (err, rows) {
                    if (rows.length != 1)
                        return res.status(400).json({success: false, msg: "Unknown email or invalid password"});
                    var user = rows[0];
                    jwt.sign(user, config.secret, {expiresIn: 60*60*24}, function (err, token) {
                        if (err) return res.status(400).json({success: false, msg: err});
                        //console.log('authenticated, token:', token);
                        res.json({success: true, result: {token: token}});
                    });
                });
            con.release();
        });
    });

    // route middleware to verify a token
    router.use('/', function (req, res, next) {
        var token = req.body.token || req.query.token || req.headers['x-app-token'];
        if (token) {
            jwt.verify(token, config.secret, function (err, decoded) {
                if (err)
                    return res.status(401).json({success: false, message: 'Failed to authenticate token.'});
                req.decoded = decoded;
                next();
            });
        } else {
            return res.status(401).json({success: false, message: 'No token provided.'});

        }
    });

    // Api

    router.get('/', function (req, res) {
        res.json({message: 'Welcome to the coolest API on earth!'});
    });

    router.get('/medias/:table/:filename', function (req, res) {
        var uri = '/medias/' + req.params.table + '/' + req.params.filename;
        return res.sendFile(path.join(__dirname+uri));
    });

    router.post('/medias/:table/:filename', function (req, res) {
        if (!req.files) return res.status(400).json({success: false, msg: 'No file to process'});
        var dir = __dirname + '/medias/' + req.params.table;
        fs.stat(dir, function (err, stats) {
            if (err && err.message.indexOf('no such file or directory') > -1)
                fs.mkdirSync(dir);
            var filePath = dir + '/' + req.params.filename;
            for (var key in req.files) {
                if (!req.files.hasOwnProperty(key)) continue;
                var file = req.files[key];
                file.mv(filePath, function (err) {
                    if (err) return res.status(403).json({success: false, msg: err});
                    res.json({success: true});
                })
            }
        });
    });

    router.get('/:table', function (req, res) {
        pool.getConnection(function (err, con) {
            if (err) throw 'Error connecting to Db';
            // console.log('Connection established');
            var table = req.params.table;
            var qry;
            if (table == 'user') table = 'users';
            if (table == 'users') qry = 'SELECT id, firstname, lastname, email FROM user';
            else qry = 'SELECT * FROM ' + table;
            // console.log('query', qry);
            con.query(qry, function (err, result) {
                if (err) return res.status(404).json({success: false, msg: err});
                res.json({success: true, result: result});
            });
            con.release();
        });
    });

    router.get('/:table/:id', function (req, res) {
        pool.getConnection(function (err, con) {
            if (err) throw 'Error connecting to Db';
            // console.log('Connection established');
            var table = req.params.table;
            var id = req.params.id;
            var qry;
            if (table == 'user') table = 'users';
            if (table == 'users') qry = 'SELECT id, firstname, lastname, email FROM user WHERE id=' + req.params.id;
            else qry = 'SELECT * FROM ' + table + ' WHERE id=' + id;
            // console.log('query', qry);
            con.query(qry, function (err, result) {
                if (err) return res.status(404).json({success: false, msg: err});
                if (result.length == 1)
                    res.json({success: true, result: result[0]});
                else
                    res.status(404).json({success: false, msg: 'Not found'});
            });
            con.release();
        });
    });

    function saveFile(table, files) {
        for (var key in files) {
            if (!files.hasOwnProperty(key)) continue;
            var file = files[key];
            var path = '/medias/'+table+'/' + file.name;
            file.mv(__dirname + path, function(err) {
                if (err) throw err;
            });
            return path;
        }
    }

    router.post('/:table', function (req, res) {
        pool.getConnection(function (err, con) {
            if (err) throw 'Error connecting to Db';
            // console.log('Connection established');
            var table = req.params.table;
            var data = req.body;
            var qry;
            if (table == 'user') return res.status(404).json({success: false, msg: 'This table is private'});
            else qry = 'INSERT INTO ' + table + ' SET ?';
            // console.log('query', qry, data);
            con.query(qry, data, function (err, result) {
                if (err) return res.status(404).json({success: false, msg: err});
                data.id = result.insertId;
                res.json({success: true, result: data});
                if (result.affectedRows > 0)
                    io.emit('insert', {table: table, row: data});
            });
            con.release();
        });
    });

    router.put('/:table/:id', function (req, res) {
        pool.getConnection(function (err, con) {
            if (err) throw 'Error connecting to Db';
            // console.log('Connection established');
            var table = req.params.table;
            var id = req.params.id;
            var data = req.body;
            if (req.files) {
                data.data = saveFile(table, req.files);
                data.size = fs.statSync(__dirname + data.data)['size'];
            }
            var qry;
            if (table == 'user') return res.status(404).json({success: false, msg: 'This table is private'});
            else qry = 'UPDATE ' + table + ' SET ? WHERE id=' + id;
            // console.log('query', qry, req.body);
            con.query(qry, req.body, function (err, result) {
                if (err) return res.status(404).json({success: false, msg: err});
                data.id = id;
                res.json({success: true, result: data});
                if (result.affectedRows > 0)
                    io.emit('update', {table: table, row: data});
            });
            con.release();
        });
    });

    router.delete('/:table/:id', function (req, res) {
        pool.getConnection(function (err, con) {
            if (err) throw 'Error connecting to Db';
            // console.log('Connection established');
            var table = req.params.table;
            var id = req.params.id;
            var data = {id: id};
            var qry;
            if (table == 'user') return res.status(404).json({success: false, msg: 'This table is private'});
            else qry = 'DELETE FROM ' + table + ' WHERE id=' + id;
            // console.log('query', qry);
            con.query(qry, function (err, result) {
                if (err) return res.status(404).json({success: false, msg: err});
                res.json({success: true, result: data});
                if (result.affectedRows > 0) io.emit('delete', {table: table, row: data});
            });
            con.release();
        });
    });

    return router;
};

module.exports = rtdb;