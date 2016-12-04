// usage: mongo admin create-users.js
var a = {
    user: "admin",
    pwd: "admin",
    roles: ["userAdminAnyDatabase"]};
db.createUser(a);
var u = {
    user: "user",
    pwd: "user",
    roles: [{role: "readWrite", db: "DataKind"},
	    {role: "readWrite", db: "DataKind1"},
	    {role: "readWrite", db: "DataKind2"}]};
db.createUser(u);
