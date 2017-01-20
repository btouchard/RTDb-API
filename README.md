# RTDb-API
==========

The RealTime Database access with REST and Socket.IO

How does it work:
-----------------

This server couples a RESTful (express) and Socket.IO API for real-time notifications. It is of course secure with a token authentication (JWS).
It automatically exposes the tables in your database (except the user table) and allows you to select, insert, update and delete entities.
It also supports uploading and downloading media to attach them to your entities.
When you make a change on a table (insert, update, delete), connected clients are notified via Socket.IO.

Installation
------------

Create a new nodejs project, add needed dependencies to your package.json:
```json
{
  "name": "my_project",
  "engines": {
    "node": ">=6.9.1"
  },
  "dependencies": {
    "rtdb-api": "^1.0.0" 
  }
}
```

```bash
npm install rtdb-api
```

Server Usage
------------

First you need to create a database with user table :

```mysql
CREATE TABLE `user` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `email` varchar(100) NOT NULL,
  `password` varchar(255) NOT NULL,
  `firstname` varchar(100) NOT NULL,
  `lastname` varchar(100) NOT NULL,
  `created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `email` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8;
```

Next, create a configuration file in your node project (config.js):
```javascript
module.exports = {
    secret: 'mysupersecretkey_tochange', // for jws
    host: 'localhost', // server ip listening
    port: 3000, // port
    mysql: { // mysql config
        host: 'localhost',
        port: 3306,
        user: 'user',
        password: 'pwd',
        database: 'database'
    },
    rootDir = __dirname // IMPORTANT !!!
};
```

Now in your express app (app.js) :

```javascript
// Setup express server
var express     = require('express');
var app         = express();
var server      = require('http').Server(app);
// import your config
var config      = require('./config');
// initialize api
var api         = require('rtdb-api')(config, app, server);

// START SERVER
server.listen(config.port);
// ADD RTDB API ROUTES
app.use('/api', api);
```

And you can start your server:

```bash
node app.js
```

Javascript client
-----------------

RTDb-API including a Javascript client.
You can import it on your HTML front-end with socket.io (please include socket.io first):

```html
<script src="/socket.io/socket.io.js"></script>
<script src="/rtdb/rtdb-client.js"></script>
```

In your personal script :
```javascript
var db = new RTDb();
var repo = db.get('category');
db.login('user@in.db', 'pwd')
    .then(function(success) {
        if (success) {
            // init realtime
            repo.on('insert', function(data){})
                .on('update', function(data){})
                .on('delete', function(data){});
            
            // use find or findOne
            repo.find().then(function(data){});
            repo.findOne(1).then(function(data){});
            // your can insert, update or delete with:
            repo.insert({title: title}, function(err, result) {
                            if (err) throw err;
                            console.log('Api::Inserted', result);
                        });
            repo.update(1, {title: title}, function(err, result) {
                            if (err) throw err;
                            console.log('Api::Updated', result);
                        });
            repo.delete(1, function(err, result) {
                            if (err) throw err;
                            console.log('Api::Deleted', result);
                        });
        }
    });
```

Android client
--------------

I have so create a Android client (ContentProvider with Sync & Service using Socket.IO)
You can find Sample App in sample folder.