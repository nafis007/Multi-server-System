package activitystreamer.server;

import activitystreamer.server.Control.MyRunnable;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.sql.Timestamp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import activitystreamer.util.Settings;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.sun.jmx.snmp.BerDecoder;
import java.io.StringReader;
import java.net.UnknownHostException;
import java.util.Arrays;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.json.simple.JSONObject;

public class Control extends Thread {

    private static final Logger log = LogManager.getLogger();
    private static ArrayList<Connection> connections;
    private static ArrayList<Connection> tempConnections;

    private static Thread registerThread;
    public static Set<Connection> serverList;

    HashMap<String, ArrayList<String>> serverLoggedin = new HashMap<String, ArrayList<String>>();

    private static ServerInput ServerInput;

    public static HashMap<String, String> server_IP_Port_list = new HashMap<String, String>();
    public static HashMap<String, String> userList = new HashMap<String, String>();

    public static HashMap<Connection, String> connection_ID = new HashMap<Connection, String>();

    HashMap<String, ArrayList<String>> backLog = new HashMap<String, ArrayList<String>>();
    HashMap<String, String> loggedInList = new HashMap<String, String>();
    HashMap<Connection, String> conLoggedInList = new HashMap<Connection, String>();
    HashMap<String, Timestamp> disconnectionTime = new HashMap<String, Timestamp>();
    HashMap<String, String[]> serverInfo = new HashMap<String, String[]>();

    public static HashMap<String, String> lockDenied = new HashMap<String, String>();
    private static ArrayList<Connection> connectionsServer;

    private static boolean term = false;
    private static Listener listener;

    protected static Control control = null;

    public static Control getInstance() {
        if (control == null) {
            control = new Control();
            ServerInput = new ServerInput();
        }
        return control;
    }

    public Control() {
        // initialize the connections array
        connections = new ArrayList<Connection>();
        tempConnections = new ArrayList<Connection>();
        connectionsServer = new ArrayList<Connection>();
        serverList = new HashSet<Connection>();

        Settings.setID(Settings.nextSecret());
        // start a listener
        try {
            listener = new Listener();
            start();
        } catch (IOException e1) {
            log.fatal("failed to startup a listening thread: " + e1);
            System.exit(-1);
        }
    }

    public void initiateConnection() {
        // make a connection to another server if remote hostname is supplied
        if (Settings.getRemoteFlag()) {
            try {

                Connection con = outgoingConnection(new Socket(Settings.getRemoteHostname(), Settings.getRemotePort()));

                JSONObject authenticateCommand = new JSONObject();
                authenticateCommand.put("command", "AUTHENTICATE");
                authenticateCommand.put("secret", Settings.getSecret());
                con.write(authenticateCommand.toJSONString());
            } catch (IOException e) {
                log.error("failed to make connection to " + Settings.getRemoteHostname() + ":" + Settings.getRemotePort() + " :" + e);
                System.exit(-1);
            }

        } else if (Settings.getRemoteHostname() != null) {
            try {
                outgoingConnection(new Socket(Settings.getRemoteHostname(), Settings.getRemotePort()));
            } catch (IOException e) {
                log.error("failed to make connection to " + Settings.getRemoteHostname() + ":" + Settings.getRemotePort() + " :" + e);
                System.exit(-1);
            }
        }
    }

    /*
     * Processing incoming messages from the connection.
     * Return true if the connection should close.
     */
    public synchronized boolean process(Connection con, JSONObject command) throws IOException, InterruptedException {

        if (!command.containsKey("command")) {

            JSONObject invalidCommand = new JSONObject();
            invalidCommand.put("command", "INVALID_MESSAGE");
            invalidCommand.put("info", "the received message did not contain a command");
            con.write(invalidCommand.toJSONString());
            //con.closeCon();
            if (conLoggedInList.containsKey(con)) {
                String usernameToRemove = conLoggedInList.get(con);
                conLoggedInList.remove(con);
                loggedInList.remove(usernameToRemove);
            }
            if (connectionsServer.contains(con)) {
                connectionsServer.remove(con);
            }
            return true;
        } else {
            if (command.get("command").equals("REGISTER")) {
                if (command.containsKey("username") && command.containsKey("secret")) {
                    String username = command.get("username").toString();
                    String secret = command.get("secret").toString();

                    Set set = userList.entrySet();
                    Iterator iterator = set.iterator();
                    while (iterator.hasNext()) {
                        Map.Entry temp = (Map.Entry) iterator.next();
                        if (temp.getKey().toString().equals(username)) {

                            JSONObject newCommand = new JSONObject();
                            newCommand.put("command", "REGISTER_FAILED");
                            newCommand.put("info", username + " is already registered with the system");
                            con.write(newCommand.toJSONString());
                            //con.closeCon();
                            return true;
                        }
                    }
                    /*
                     lockDenied.clear();
                     JSONObject lockRequestCommand = new JSONObject();
                     lockRequestCommand.put("command", "LOCK_REQUEST");
                     lockRequestCommand.put("username", username);
                     lockRequestCommand.put("secret", secret);

                     for (Iterator<Connection> it = serverList.iterator(); it.hasNext();) {
                     it.next().write(lockRequestCommand.toJSONString());
                     }
                     MyRunnable myRunnable = new MyRunnable(username, secret, con);
                     registerThread = new Thread(myRunnable);
                     registerThread.start();*/

                    userList.put(username, secret);
                    JSONObject newCommand = new JSONObject();
                    newCommand.put("command", "REGISTER_SUCCESS");
                    newCommand.put("info", "register success for " + username);
                    con.write(newCommand.toJSONString());

                    JSONObject regCommand = new JSONObject();
                    regCommand.put("command", "NEW_USER");
                    regCommand.put("username", username);
                    regCommand.put("secret", secret);
                    userList.put(username, secret);
                    for (Connection server : serverList) {

                        server.write(regCommand.toJSONString());

                    }
                    //connections.add(con);
                } else {
                    JSONObject newCommand = new JSONObject();
                    newCommand.put("command", "INVALID_MESSAGE");
                    newCommand.put("info", "JSON parse error while parsing message");
                    con.write(newCommand.toJSONString());
                    //con.closeCon();
                    return true;
                }

            }

            if (command.get("command").equals("LOGIN")) {
                if (command.containsKey("username") && command.containsKey("secret")) {
                    String username = command.get("username").toString();
                    String secret = command.get("secret").toString();

                    if (secret.equals(userList.get(username))) {
                        JSONObject newCommand = new JSONObject();
                        newCommand.put("command", "LOGIN_SUCCESS");
                        newCommand.put("info", "logged in as user " + username);
                        con.write(newCommand.toJSONString());

                        Boolean flag = true;

                        Set set = serverInfo.entrySet();
                        Iterator iterator = set.iterator();
                        while (iterator.hasNext() && flag) {
                            Map.Entry temp = (Map.Entry) iterator.next();
                            String[] tempArray = (String[]) temp.getValue();
                            if ((Integer.parseInt(tempArray[2]) + 2 <= loggedInList.size())) {

                                JSONObject redirectCommand = new JSONObject();

                                redirectCommand.put("command", "REDIRECT");
                                redirectCommand.put("hostname", tempArray[0]);
                                redirectCommand.put("port", tempArray[1]);
                                con.write(redirectCommand.toJSONString());

                                flag = false;
                            }
                        }
                        if (flag) {
                            loggedInList.put(username, secret);
                            conLoggedInList.put(con, username);
                            connections.add(con);

                            set = backLog.entrySet();
                            iterator = set.iterator();
                            boolean found = false;
                            while (iterator.hasNext()) {
                                Map.Entry temp = (Map.Entry) iterator.next();
                                if (temp.getKey().toString().equals(username)) {
                                    ArrayList<String> mList = (ArrayList<String>) temp.getValue();
                                    if (!(mList.isEmpty())) {
                                        for (int i = 0; i < mList.size(); i++) {
                                            con.write(mList.get(i));
                                        }

                                    }
                                    disconnectionTime.remove(temp.getKey().toString());
                                    backLog.remove(temp.getKey().toString());
                                }

                            }

                            JSONObject serverAnnounce = new JSONObject();
                            serverAnnounce.put("command", "SERVER_ANNOUNCE");
                            serverAnnounce.put("id", Settings.getID());
                            serverAnnounce.put("load", String.valueOf(loggedInList.size()));
                            serverAnnounce.put("hostname", Settings.getLocalHostname());
                            serverAnnounce.put("port", Settings.getLocalPort());
                            //Gson gson = new Gson(); 
                            //String sList = gson.toJson(loggedInList);
                            //serverAnnounce.put("loggedUser", sList);
                            serverAnnounce.put("newLoggeduser", username);

                            //System.out.println(serverAnnounce.toJSONString());
                            for (Iterator<Connection> it = serverList.iterator(); it.hasNext();) {
                                try {
                                    it.next().write(serverAnnounce.toJSONString());

                                } catch (IOException ex) {
                                    java.util.logging.Logger.getLogger(Control.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            }

                        } else {
                            return true;
                        }

                    } else {
                        JSONObject newCommand = new JSONObject();
                        newCommand.put("command", "LOGIN_FAILED");
                        newCommand.put("info", "attempt to login with wrong secret");
                        con.write(newCommand.toJSONString());
                        //con.closeCon();
                        return true;

                    }

                } else {
                    JSONObject newCommand = new JSONObject();
                    newCommand.put("command", "INVALID_MESSAGE");
                    newCommand.put("info", "JSON parse error while parsing message");
                    con.write(newCommand.toJSONString());
                    //con.closeCon();
                    return true;
                }
            }

            if (command.get("command").equals("ACTIVITY_MESSAGE")) {
                if (command.containsKey("username") && command.containsKey("activity")
                        && command.containsKey("secret")) {

                    String username = command.get("username").toString();
                    String secret = command.get("secret").toString();
                    JSONObject activityObj = (JSONObject) command.get("activity");
                    activityObj.put("command", "ACTIVITY_BROADCAST");
                    Timestamp timestamp = new Timestamp(System.currentTimeMillis());
                    activityObj.put("timestamp", timestamp.toString());
                    if (username.equals("anonymous")) {
                        //System.out.println(activityObj.toJSONString());
                        con.write(activityObj.toJSONString());
                    } else if (!username.equals("anonymous")) {
                        Set set = loggedInList.entrySet();
                        Iterator iterator = set.iterator();
                        boolean found = false;
                        while (iterator.hasNext()) {
                            Map.Entry temp = (Map.Entry) iterator.next();
                            if (temp.getKey().toString().equals(username)
                                    && temp.getValue().toString().equals(secret)) {
                                found = true;
                                //activityObj.put("command", "ACTIVITY_BROADCAST");
                                //Thread.sleep(10000);
                                for (Connection server : serverList) {

                                    server.write(activityObj.toJSONString());
                                }

                                activityObj.remove("timestamp");
                                for (Connection cn : connections) {
                                    cn.write(activityObj.toJSONString());
                                }

                                set = disconnectionTime.entrySet();
                                iterator = set.iterator();
                                while (iterator.hasNext()) {
                                    Map.Entry temp1 = (Map.Entry) iterator.next();
                                    Timestamp temp_t = (Timestamp) temp1.getValue();
                                    //System.out.println(temp_t.toString() + "      ---------     " + timestamp);
                                    if (temp_t.after(timestamp)) {
                                        ArrayList<String> tlist = (ArrayList<String>) backLog.get(temp1.getKey().toString());
                                        tlist.add(activityObj.toJSONString());
                                        backLog.put(temp1.getKey().toString(), tlist);
                                    }

                                }
                                //System.out.println(backLog.size());

                                //con.write(activityObj.toJSONString());
                            }
                        }
                        if (found == false) {
                            JSONObject authenticateFail = new JSONObject();
                            authenticateFail.put("command", "AUTHENTICATION_FAIL");
                            authenticateFail.put("info", "user not found, maybe wrong secret or not logged in yet");
                            con.write(authenticateFail.toJSONString());

                            String usernameToRemove = conLoggedInList.get(con);
                            conLoggedInList.remove(con);
                            loggedInList.remove(usernameToRemove);

                            return true;
                        }
                    }
                    /* else {
                     JSONObject authenticateFail = new JSONObject();
                     authenticateFail.put("command","AUTHENTICATION_FAIL");
                     authenticateFail.put("info","the supplied secret is incorrect: fmnmpp3ai91qb3gc2bvs14g3ue");
                     con.write(authenticateFail.toJSONString());
                     return true;
                     } */
                } else {
                    JSONObject invalidCommand = new JSONObject();
                    invalidCommand.put("command", "INVALID_MESSAGE");
                    invalidCommand.put("info", "JSON parse error while parsing message");
                    con.write(invalidCommand.toJSONString());
                    //con.closeCon();

                    String usernameToRemove = conLoggedInList.get(con);
                    conLoggedInList.remove(con);
                    loggedInList.remove(usernameToRemove);
                    return true;
                }

            }

            if (command.get("command").equals("ACTIVITY_BROADCAST")) {
                //command.remove("timestamp");
                Timestamp msgSendTime = Timestamp.valueOf(command.get("timestamp").toString());
                JSONObject activityMessage = (JSONObject) command.clone();
                activityMessage.remove("timestamp");
                for (Connection cn : connections) {
                    cn.write(activityMessage.toJSONString());
                }

                Set set = disconnectionTime.entrySet();
                Iterator iterator = set.iterator();
                while (iterator.hasNext()) {
                    Map.Entry temp1 = (Map.Entry) iterator.next();
                    Timestamp temp = (Timestamp) temp1.getValue();
                    //System.out.println(temp.toString() + "      ---------     " + msgSendTime.toString());
                    if (temp.after(msgSendTime)) {
                        ArrayList<String> tlist = (ArrayList<String>) backLog.get(temp1.getKey().toString());
                        tlist.add(activityMessage.toJSONString());
                        backLog.put(temp1.getKey().toString(), tlist);
                    }

                }

            }

            if (command.get("command").equals("LOCK_REQUEST")) {
                if (command.containsKey("username") && command.containsKey("secret")) {

                    /*
                     for (Connection server : serverList) {
                     if (server != con) {
                     server.write(command.toJSONString());
                     }
                     }*/
                    String username = command.get("username").toString();
                    String secret = command.get("secret").toString();

                    boolean deniedFlag = false;

                    Set set = userList.entrySet();
                    Iterator iterator = set.iterator();
                    while (iterator.hasNext()) {
                        Map.Entry temp = (Map.Entry) iterator.next();
                        if (temp.getKey().toString().equals(username)) {
                            //con.closeCon();
                            deniedFlag = true;
                            break;
                            //return true;
                        }
                    }

                    if (deniedFlag == true) {
                        JSONObject newCommand = new JSONObject();
                        newCommand.put("command", "LOCK_DENIED");
                        newCommand.put("username", username);
                        newCommand.put("secret", secret);
                        userList.remove(username);

                        for (Connection server : serverList) {
                            server.write(newCommand.toJSONString());

                        }

                    } else {
                        JSONObject newCommand = new JSONObject();
                        newCommand.put("command", "LOCK_ALLOWED");
                        newCommand.put("username", username);
                        newCommand.put("secret", secret);
                        userList.put(username, secret);
                        for (Connection server : serverList) {

                            server.write(newCommand.toJSONString());

                        }
                    }

                } else if (!serverList.contains(con)) {
                    JSONObject newCommand = new JSONObject();
                    newCommand.put("command", "INVALID_MESSAGE");
                    newCommand.put("info", "JSON parse error while parsing message, lock request from unauthenticated server");
                    con.write(newCommand.toJSONString());
                    //con.closeCon();
                    connectionsServer.remove(con);
                    return true;
                } else if (!command.containsKey("username") || !command.containsKey("secret")) {
                    JSONObject newCommand = new JSONObject();
                    newCommand.put("command", "INVALID_MESSAGE");
                    newCommand.put("info", "JSON parse error while parsing message, invalid lock request message");
                    con.write(newCommand.toJSONString());
                    //con.closeCon();
                    connectionsServer.remove(con);
                    return true;
                }
            }

            if (command.get("command").equals("LOCK_ALLOWED")) {
                if (!command.containsKey("username") || !command.containsKey("secret")) {
                    JSONObject newCommand = new JSONObject();
                    newCommand.put("command", "INVALID_MESSAGE");
                    newCommand.put("info", "JSON parse error while parsing message, invalid lock allowed message");
                    con.write(newCommand.toJSONString());
                    //con.closeCon();
                    connectionsServer.remove(con);
                    return true;
                } else if (!serverList.contains(con)) {
                    JSONObject newCommand = new JSONObject();
                    newCommand.put("command", "INVALID_MESSAGE");
                    newCommand.put("info", "JSON parse error while parsing message, lock request from unauthenticated server");
                    con.write(newCommand.toJSONString());
                    //con.closeCon();
                    connectionsServer.remove(con);
                    return true;
                } else {
                    userList.put(command.get("username").toString(), command.get("secret").toString());
                    /*
                     for (Connection server : serverList) {
                     if (server != con) {
                     server.write(command.toJSONString());
                     }
                     }
                     */
                }
            }

            if (command.get("command").equals("LOCK_DENIED")) {
                if (command.containsKey("username") && command.containsKey("secret")) {

                    String username = command.get("username").toString();
                    String secret = command.get("secret").toString();
                    lockDenied.put(username, secret);
                    userList.remove(username);
                    /*
                     for (Connection server : serverList) {
                     if (server != con) {
                     server.write(command.toJSONString());
                     }
                     }
                     */

                } else if (!serverList.contains(con)) {
                    JSONObject newCommand = new JSONObject();
                    newCommand.put("command", "INVALID_MESSAGE");
                    newCommand.put("info", "JSON parse error while parsing message, lock denied from unauthenticated server");
                    con.write(newCommand.toJSONString());
                    //con.closeCon();
                    connectionsServer.remove(con);
                    return true;
                } else if (!command.containsKey("username") || !command.containsKey("secret")) {
                    JSONObject newCommand = new JSONObject();
                    newCommand.put("command", "INVALID_MESSAGE");
                    newCommand.put("info", "JSON parse error while parsing message, invalid lock denied message");
                    con.write(newCommand.toJSONString());
                    //con.closeCon();
                    connectionsServer.remove(con);
                    return true;
                }
            }

            /* if (command.get("command").equals("ACTIVITY_BROADCAST")) {
             for (Connection cn : connections) {
             cn.write(command.toJSONString());
             } 
             } */
            if (command.get("command").equals("LOGOUT")) {
                //con.closeCon();
                String usernameToRemove = conLoggedInList.get(con);
                conLoggedInList.remove(con);
                loggedInList.remove(usernameToRemove);

                backLog.put(usernameToRemove, new ArrayList<String>());

                JSONObject newCommand = new JSONObject();
                newCommand.put("command", "LOGOUT_USERNAME");
                newCommand.put("username", usernameToRemove);
                Timestamp timestamp = new Timestamp(System.currentTimeMillis());
                newCommand.put("timestamp", timestamp.toString());

                for (Connection server : serverList) {

                    server.write(newCommand.toJSONString());

                }

                JSONObject serverAnnounce = new JSONObject();
                serverAnnounce.put("command", "SERVER_ANNOUNCE");
                serverAnnounce.put("id", Settings.getID());
                serverAnnounce.put("load", String.valueOf(loggedInList.size()));
                serverAnnounce.put("hostname", Settings.getLocalHostname());
                serverAnnounce.put("port", Settings.getLocalPort());
                Gson gson = new Gson();
                String sList = gson.toJson(loggedInList);
                serverAnnounce.put("loggedUser", sList);
                ;

                //System.out.println(serverAnnounce.toJSONString());
                for (Iterator<Connection> it = serverList.iterator(); it.hasNext();) {
                    try {
                        Connection temp = it.next();
                        temp.write(serverAnnounce.toJSONString());

                    } catch (IOException ex) {
                        java.util.logging.Logger.getLogger(Control.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                return true;
            }

            if (command.get("command").equals("SERVER_ANNOUNCE")) {
                serverList.add(con);
                String hostname = command.get("hostname").toString();
                String port = command.get("port").toString();
                String load = command.get("load").toString();
                String id = command.get("id").toString();
                serverInfo.put(id, new String[]{hostname, port, load});

                if (command.containsKey("loggedUser")) {

                    HashMap<String, String> tlist = new HashMap<String, String>();
                    String bList = command.get("loggedUser").toString();
                    Map<String, String> retMap1 = new Gson().fromJson(
                            bList, new TypeToken<HashMap<String, String>>() {
                            }.getType()
                    );

                    tlist = (HashMap) retMap1;
                    ArrayList<String> temp1 = new ArrayList<String>();
                    Set set = tlist.entrySet();
                    Iterator iterator = set.iterator();
                    while (iterator.hasNext()) {
                        Map.Entry temp = (Map.Entry) iterator.next();
                        temp1.add(temp.getKey().toString());

                    }
                    serverLoggedin.put(Settings.socketAddress(con.getSocket()), temp1);
                }
                if (command.containsKey("newLoggeduser")) {
                    String userName = command.get("newLoggeduser").toString();
                    disconnectionTime.remove(userName);
                    backLog.remove(userName);
                }
                if (loggedInList.size() > Integer.valueOf(load) + 2) {
                    JSONObject redirectCommand = new JSONObject();

                    redirectCommand.put("command", "REDIRECT");
                    redirectCommand.put("hostname", hostname);
                    redirectCommand.put("port", port);
                    Connection c = connections.get(0);
                    String usernameToRemove = conLoggedInList.get(c);
                    c.write(redirectCommand.toJSONString());
                    connections.remove(c);
                    c.closeCon();
                    conLoggedInList.remove(c);
                    loggedInList.remove(usernameToRemove);
                    
                }


                /*
                 for (Connection server : serverList) {
                 if (server != con) {
                 server.write(command.toJSONString());
                 }
                 }
                 */
            }

            if (command.get("command").equals("AUTHENTICATE")) {
                if (serverList.contains(con)) {
                    JSONObject invalidCommand = new JSONObject();
                    invalidCommand.put("command", "INVALID_MESSAGE");
                    invalidCommand.put("info", "Server already authenticated");
                    con.write(invalidCommand.toJSONString());
                    connectionsServer.remove(con);
                    serverList.remove(con);
                    return true;
                } else {
                    if (command.get("secret").equals(Settings.getSecret())) {

                        //send server list
                        Gson gson = new Gson();
                        String sList = gson.toJson(serverInfo);
                        Gson gson1 = new Gson();
                        String uList = gson1.toJson(userList);
                        Gson gson2 = new Gson();
                        String bList = gson2.toJson(backLog);

                        JSONObject sendStatusList = new JSONObject();
                        sendStatusList.put("command", "NETWORK_STATUS");
                        sendStatusList.put("server_info", sList);
                        sendStatusList.put("user_info", uList);
                        sendStatusList.put("backlog_info", bList);
                        con.write(sendStatusList.toJSONString());

                        serverList.add(con);

                        //send user list
                    } else {
                        JSONObject invalidCommand = new JSONObject();
                        invalidCommand.put("command", "AUTHENTICATION_FAIL");
                        invalidCommand.put("info", "the supplied secret is incorrect: " + command.get("secret"));
                        con.write(invalidCommand.toJSONString());
                        connectionsServer.remove(con);
                        return true;
                    }
                }

            }

            if (command.get("command").equals("AUTHENTICATE_TO_NETWORK")) {
                if (serverList.contains(con)) {
                    JSONObject invalidCommand = new JSONObject();
                    invalidCommand.put("command", "INVALID_MESSAGE");
                    invalidCommand.put("info", "Server already authenticated");
                    con.write(invalidCommand.toJSONString());
                    connectionsServer.remove(con);
                    serverList.remove(con);
                    return true;
                } else {
                    if (command.get("secret").equals(Settings.getSecret())) {
                        serverList.add(con);

                    } else {
                        JSONObject invalidCommand = new JSONObject();
                        invalidCommand.put("command", "AUTHENTICATION_FAIL");
                        invalidCommand.put("info", "the supplied secret is incorrect: " + command.get("secret"));
                        con.write(invalidCommand.toJSONString());
                        connectionsServer.remove(con);
                        return true;
                    }
                }

            }
            if (command.get("command").equals("NETWORK_STATUS")) {
                //serverList.add(con);
                String sList = command.get("server_info").toString();
                Map<String, String[]> retMap = new Gson().fromJson(
                        sList, new TypeToken<HashMap<String, String[]>>() {
                        }.getType()
                );

                serverInfo = (HashMap) retMap;

                Set set = serverInfo.entrySet();
                Iterator iterator = set.iterator();
                while (iterator.hasNext()) {
                    Map.Entry temp = (Map.Entry) iterator.next();
                    String[] tempArray = (String[]) temp.getValue();

                    try {

                        Connection tempCon = outgoingConnection(new Socket(tempArray[0], Integer.parseInt(tempArray[1])));
                        JSONObject authenticateCommand = new JSONObject();
                        authenticateCommand.put("command", "AUTHENTICATE_TO_NETWORK");
                        authenticateCommand.put("secret", Settings.getSecret());
                        tempCon.write(authenticateCommand.toJSONString());
                    } catch (IOException e) {
                        log.error("failed to make connection to " + Settings.getRemoteHostname() + ":" + Settings.getRemotePort() + " :" + e);
                        System.exit(-1);
                    }

                }

                String uList = command.get("user_info").toString();
                Map<String, String> retMap2 = new Gson().fromJson(
                        uList, new TypeToken<HashMap<String, String>>() {
                        }.getType()
                );

                userList = (HashMap) retMap2;

                String bList = command.get("backlog_info").toString();
                Map<String, ArrayList<String>> retMap1 = new Gson().fromJson(
                        bList, new TypeToken<HashMap<String, ArrayList<String>>>() {
                        }.getType()
                );

                backLog = (HashMap) retMap1;

            }

            if (command.get("command").equals("NEW_USER")) {
                userList.put(command.get("username").toString(), command.get("secret").toString());
                backLog.put(command.get("username").toString(), new ArrayList<String>());
            }
            if (command.get("command").equals("LOGOUT_USERNAME")) {
                String username = command.get("username").toString();
                Timestamp disconnectTime = Timestamp.valueOf(command.get("timestamp").toString());
                disconnectionTime.put(username, disconnectTime);
                //System.out.println(username + "             " + disconnectTime.toString());
                backLog.put(command.get("username").toString(), new ArrayList<String>());
            }

            if (command.get("command").equals("AUTHENTICATION_FAIL")) {

            }
        }

        System.out.println(command.toJSONString());
        //return true;
        /*for (Connection cn : connections) {
         cn.write(command.toJSONString());
         } */
        return false;
    }

    public void sendActivityObjectServer(JSONObject activityObj) {
        //System.out.println("trying to send object");
        //textFrame.setOutputText(activityObj);
        //System.out.println("print connected server size: " + connectionsServer.size());

        try {
            for (int i = 0; i < connectionsServer.size(); i++) {
                connectionsServer.get(i).write(activityObj.toJSONString());
            }

            // Print out results received from server..
            //String result = input.readUTF();
            //System.out.println("Received from server: "+result);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {

        }

        //////////////////////// trying to send to actual server//////////////////////////////////
    }

    /*
     * The connection has been closed by the other party.
     */
    public synchronized void connectionClosed(Connection con) {
        if (!term) {
            connections.remove(con);
        }
    }

    /*
     * A new incoming connection has been established, and a reference is returned to it
     */
    public synchronized Connection incomingConnection(Socket s) throws IOException {
        log.debug("incomming connection: " + Settings.socketAddress(s)
                + " User: " + Settings.getUsername()
                + " Secret: " + null);
        Connection c = new Connection(s);
        tempConnections.add(c);
        return c;

    }

    /*
     * A new outgoing connection has been established, and a reference is returned to it
     */
    public synchronized Connection outgoingConnection(Socket s) throws IOException {

        log.debug("outgoing connection: " + Settings.socketAddress(s));
        Connection c = new Connection(s);
        //connections.add(c);
        connectionsServer.add(c);
        return c;

    }

    private static JsonObject jsonFromString(String jsonObjectStr) throws IOException {

        JsonParser jsonParser = new JsonParser();
        JsonObject jo = (JsonObject) jsonParser.parse(jsonObjectStr);
        return jo;
    }

    @Override
    public void run() {
        //log.info("using activity interval of " + Settings.getActivityInterval() + " milliseconds");
        while (!term) {
            // do something with 5 second intervals in between
            JSONObject serverAnnounce = new JSONObject();
            serverAnnounce.put("command", "SERVER_ANNOUNCE");
            serverAnnounce.put("id", Settings.getID());
            serverAnnounce.put("load", String.valueOf(loggedInList.size()));
            serverAnnounce.put("hostname", Settings.getLocalHostname());
            serverAnnounce.put("port", Settings.getLocalPort());
            Gson gson = new Gson();
            String sList = gson.toJson(loggedInList);
            serverAnnounce.put("loggedUser", sList);

            //System.out.println(serverAnnounce.toJSONString());
            for (Iterator<Connection> it = serverList.iterator(); it.hasNext();) {
                try {
                    it.next().write(serverAnnounce.toJSONString());

                } catch (IOException ex) {
                    java.util.logging.Logger.getLogger(Control.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            try {
                Thread.sleep(Settings.getActivityInterval());
            } catch (InterruptedException e) {
                log.info("received an interrupt, system is shutting down");
                break;
            }
            if (!term) {
                //     log.debug("doing activity");
                term = doActivity();
            }

        }
        log.info("closing " + connections.size() + " connections");
        // clean up
        for (Connection connection : connections) {
            connection.closeCon();
        }
        listener.setTerm(true);
    }

    public boolean doActivity() {
        return false;
    }

    public final void setTerm(boolean t) {
        term = t;
    }

    public final ArrayList<Connection> getConnections() {
        return connections;
    }

    void sendLogOutMessage(String usernameToRemove, Timestamp t) throws IOException {
        disconnectionTime.put(usernameToRemove, t);
        backLog.put(usernameToRemove, new ArrayList<String>());

        JSONObject newCommand = new JSONObject();

        newCommand.put("command", "LOGOUT_USERNAME");
        newCommand.put("username", usernameToRemove);
        newCommand.put("timestamp", t.toString());

        for (Connection server : serverList) {

            server.write(newCommand.toJSONString());

        }
        JSONObject serverAnnounce = new JSONObject();
        serverAnnounce.put("command", "SERVER_ANNOUNCE");
        serverAnnounce.put("id", Settings.getID());
        serverAnnounce.put("load", String.valueOf(loggedInList.size()));
        serverAnnounce.put("hostname", Settings.getLocalHostname());
        serverAnnounce.put("port", Settings.getLocalPort());
        Gson gson = new Gson();
        String sList = gson.toJson(loggedInList);
        serverAnnounce.put("loggedUser", sList);

        //System.out.println(serverAnnounce.toJSONString());
        for (Iterator<Connection> it = serverList.iterator(); it.hasNext();) {
            try {
                Connection temp = it.next();
                temp.write(serverAnnounce.toJSONString());
                //temp.write(newCommand.toJSONString());
            } catch (IOException ex) {
                java.util.logging.Logger.getLogger(Control.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

    void serverClosing(String socketAddress, Timestamp t) {

        ArrayList<String> tList = serverLoggedin.get(socketAddress);
        for (int i = 0; i < tList.size(); i++) {
            //System.out.println(tList.get(i));
            disconnectionTime.put(tList.get(i), t);
            backLog.put(tList.get(i), new ArrayList<String>());
            //System.out.println(tList.get(i));
        }
        serverLoggedin.remove(socketAddress);

    }

    public static class MyRunnable implements Runnable {

        private String username;
        private String secret;
        private Connection con;

        public MyRunnable(String userName, String secret, Connection con) {
            this.username = userName;
            this.secret = secret;
            this.con = con;
        }

        public void run() {
            try {

                sleep(1000);
                Set set = lockDenied.entrySet();
                Iterator iterator = set.iterator();
                boolean flag = true;
                while (iterator.hasNext()) {
                    Map.Entry temp = (Map.Entry) iterator.next();
                    if (temp.getKey().toString().equals(username)) {

                        flag = false;
                        break;
                        //con.write(activityObj.toJSONString());
                    }
                }

                if (flag) {
                    userList.put(username, secret);
                    JSONObject newCommand = new JSONObject();
                    newCommand.put("command", "REGISTER_SUCCESS");
                    newCommand.put("info", "register success for " + username);
                    con.write(newCommand.toJSONString());

                    JSONObject regCommand = new JSONObject();
                    regCommand.put("command", "NEW_USER");
                    regCommand.put("username", username);
                    regCommand.put("secret", secret);
                    userList.put(username, secret);
                    for (Connection server : serverList) {

                        server.write(newCommand.toJSONString());

                    }

                } else {
                    JSONObject newCommand = new JSONObject();
                    newCommand.put("command", "REGISTER_FAILED");
                    newCommand.put("info", username + " is already registered with the system");
                    con.write(newCommand.toJSONString());
                    con.closeCon();

                }

            } catch (InterruptedException ex) {
                java.util.logging.Logger.getLogger(Control.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                java.util.logging.Logger.getLogger(Control.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

}
