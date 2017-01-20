# RTDb-API
==========

The RealTime Database access with REST and Socket.IO

How does it work:
-----------------

This server couples a RESTful and Socket.IO API for real-time notifications. It is of course secure with a token authentication (JWS).
It automatically exposes the tables in your database (except the user table) and allows you to select, insert, update and delete entities.
It also supports uploading and downloading media to attach them to your entities.
When you make a change on a table (insert, update, delete), connected clients are notified via Socket.IO.