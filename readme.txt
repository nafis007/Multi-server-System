The aim of the project was to build a simple multi-server system for broadcasting activity objects between a number of clients. The multi-server system will load balance client requests over the servers, using a redirection mechanism to ask clients to reconnect to another server. It will allow clients to register a username and secret, that can act as an authentication mechanism. Clients can login and logout as either anonymous or using a username/secret pair. It will allow clients to broadcast an activity object to all other clients connected at the time.


** Opeing the first server on port 3000, ip will be local host(by default)
** and we need to set the secret
java -jar ActivityStreamerServer.jar -lp 3000 -s gen
** Opeing a new server on port 3100 and connect the server to a remote server
** (using rp and rh). By this command the server will join the 
** network. 
java -jar ActivityStreamerServer.jar -lp 3100 -rp 3000 -rh 10.13.12.136 -s gen

** Opening a client 
java -jar ActivityStreamerClient.jar -rp 3000 -rh 10.13.12.136
** This command will only connect the client. 
** For Registration 
{"command" : "REGISTER","username" : "aaron","secret" : "asd"}
** For Login 
{"command" : "LOGIN","username" : "aaron","secret" : "asd"}

{"command" : "ACTIVITY_MESSAGE", "username" : "aaron", "secret" : "asd", "activity" : {"aaron":"hello"}}

** Registration and log in using command line arguments 
java -jar ActivityStreamerClient.jar -rp 3000 -rh 10.13.12.136 -u aaron
** In this case server will generate a secret for the client and the
** client will use it.
