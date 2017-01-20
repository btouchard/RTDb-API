var RTDb = function (config) {
    var self = this;
    this.callbacks = {};
    this.config = config || {};
    this.config.apiUrl = (this.config.host || '') + '/api';
    this.initSocket = function (token) {
        if (self.socket && self.socket.connected) self.socket.disconnect();
        self.socket = io.connect(this.config.host, {query: 'token='+token});
        self.socket.on('connect', function () {
            // console.log('authenticated.io');
        }).on('disconnect', function () {
            // console.log('disconnected');
        }).on('insert', function (data) {
            console.log("Socket.IO::Inserted", data);
            // self._select('insert', data.table, data.row);
            var cb = self.callbacks[data.table];
            if (cb) cb._resolve('insert', data.row);
        }).on('update', function (data) {
            console.log("Socket.IO::Updated", data);
            // self._select('update', data.table, data.row);
            var cb = self.callbacks[data.table];
            if (cb) cb._resolve('update', data.row);
        }).on('delete', function (data) {
            console.log("Socket.IO::Deleted", data);
            var cb = self.callbacks[data.table];
            if (cb) cb._resolve('delete', data.row);
        });
    };
    this.login = function (email, password) {
        if (email) self.email = email;
        if (password) self.password = password;
        return new Promise(function (resolve) {
            var token = localStorage.getItem('token');
            function verify() {
                var verify = new XMLHttpRequest();
                verify.open('GET', self.config.apiUrl + '/verify', true);
                verify.setRequestHeader('Content-Type', 'application/json');
                verify.setRequestHeader('x-app-token', token);
                verify.onload = function (e) {
                    var res = JSON.parse(verify.responseText);
                    if (res.success) onAuthenticate(res.success);
                    else authenticate();
                };
                verify.send();
            }
            function authenticate() {
                var auth = new XMLHttpRequest();
                var url = self.config.apiUrl + '/authenticate';
                auth.open('POST', url, true);
                auth.setRequestHeader('Content-Type', 'application/json');
                auth.onload = function (e) {
                    var res = JSON.parse(auth.responseText);
                    // console.log('login', res.success);
                    localStorage.setItem('token', res.success ? res.result.token : null);
                    token = localStorage.getItem('token');
                    onAuthenticate(res.success);
                };
                auth.send(JSON.stringify({email: self.email, password: self.password}));
            }
            function onAuthenticate(success) {
                console.log('authenticated', success, token.substr(0, 20)+'...');
                self.initSocket(token);
                return resolve(success);
            }
            if (token) verify();
            else authenticate();
        });
    };

    this.get = function (table) {
        var callback = self.callbacks[table];
        if (!callback) callback = self.callbacks[table] = {
            cbs: {},
            on: function(type, cb) {
                this.cbs[type] = cb;
                return this;
            },
            find: function () {
                return new Promise(function (resolve) {
                    self._select(function (data) {
                        resolve(data);
                    }, table);
                });
            },
            findOne: function (id) {
                return new Promise(function (resolve) {
                    self._select(function (data) {
                        resolve(data);
                    }, table, id);
                });
            },
            loadImage: function (fileName) {
                return new Promise(function (resolve, reject) {
                    var xhr = new XMLHttpRequest();
                    var url = self.config.apiUrl + '/medias/' + table + '/' + fileName;
                    var ext = fileName.split('.').pop();
                    if (ext == 'jpg') ext = 'jpeg';
                    var mimeType = "image/" + ext;
                    xhr.open('GET', url, true);
                    xhr.responseType = 'arraybuffer';
                    xhr.setRequestHeader('x-app-token', localStorage.getItem('token'));
                    xhr.onload = function (e) {
                        if (this.status != 200) return reject("Status code error: " + this.status);
                        var uInt8Array = new Uint8Array(this.response);
                        var i = uInt8Array.length;
                        var binaryString = new Array(i);
                        while (i--) binaryString[i] = String.fromCharCode(uInt8Array[i]);
                        var data = binaryString.join('');
                        var base64 = window.btoa(data);
                        resolve("data:" + mimeType + ";base64," + base64);
                    };
                    xhr.send();
                })
            },
            insert: function (data, cb) {
                self._insert(table, data, cb);
                return this;
            },
            update: function (id, data, cb) {
                if (isNaN(id) || !(id > 0)) throw 'Id must by defined and greater than 0';
                self._update(table, id, data, cb);
                return this;
            },
            delete: function (id, cb) {
                if (isNaN(id) || !(id > 0)) throw 'Id must by defined and greater than 0';
                self._delete(table, id, cb);
                return this;
            },
            _resolve: function (type, args) {
                var cb = this.cbs[type];
                if (cb) cb(args);
            }
        };
        return callback;
    };

    this._select = function (type, table, id) {
        var xhr = new XMLHttpRequest();
        var url = self.config.apiUrl + '/' + table;
        if (id) url += '/' + id;
        xhr.open('GET', url, true);
        xhr.setRequestHeader('Content-Type', 'application/json');
        xhr.setRequestHeader('x-app-token', localStorage.getItem('token'));
        xhr.onload = function (e) {
            if (this.status == 403) {
                self.login().then(function (success) {
                    if (success)
                        self._select(type, table, id);
                });
                return;
            }
            var res = JSON.parse(this.responseText);
            // console.log(res);
            if (res.success) {
                if (typeof type === 'function')
                    return type(res.result);
                var cb = self.callbacks[table];
                if (cb) cb._resolve(type, res.result);
            }
        };
        xhr.send();
    };
    this._insert = function (table, data, cb) {
        var xhr = new XMLHttpRequest();
        var url = self.config.apiUrl + '/' + table;
        xhr.open('POST', url, true);
        xhr.setRequestHeader('Content-Type', 'application/json');
        xhr.setRequestHeader('x-app-token', localStorage.getItem('token'));
        xhr.onload = function (e) {
            if (this.status == 403) {
                self.login().then(function (success) {
                    if (success)
                        self._insert(table, data);
                });
                return;
            }
            var res = JSON.parse(this.responseText);
            if (cb) cb(res.msg, res.result);
        };
        xhr.send(JSON.stringify(data));
    };
    this._update = function (table, id, data, cb) {
        var xhr = new XMLHttpRequest();
        var url = self.config.apiUrl + '/' + table;
        if (id) url += '/' + id;
        xhr.open('PUT', url, true);
        xhr.setRequestHeader('Content-Type', 'application/json');
        xhr.setRequestHeader('x-app-token', localStorage.getItem('token'));
        xhr.onload = function (e) {
            if (this.status == 403) {
                self.login().then(function (success) {
                    if (success)
                        self._update(table, id, data);
                });
                return;
            }
            var res = JSON.parse(this.responseText);
            if (cb) cb(res.msg, res.result);
        };
        xhr.send(JSON.stringify(data));
    };
    this._delete = function (table, id, cb) {
        var xhr = new XMLHttpRequest();
        var url = self.config.apiUrl + '/' + table;
        if (id) url += '/' + id;
        xhr.open('DELETE', url, true);
        xhr.setRequestHeader('Content-Type', 'application/json');
        xhr.setRequestHeader('x-app-token', localStorage.getItem('token'));
        xhr.onload = function (e) {
            if (this.status == 403) {
                self.login().then(function (success) {
                    if (success)
                        self._delete(table, id);
                });
                return;
            }
            var res = JSON.parse(this.responseText);
            if (cb) cb(res.msg, res.result);
        };
        xhr.send();
    };
};