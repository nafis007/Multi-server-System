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
