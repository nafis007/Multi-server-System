package activitystreamer.server;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import activitystreamer.util.Settings;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.sql.Timestamp;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.json.simple.JSONObject;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class Connection extends Thread {

    private static final Logger log = LogManager.getLogger();
    private DataInputStream in;
    private DataOutputStream out;
    private BufferedReader inreader;
    private static BufferedWriter outwriter;
    private String server_id;
    //private PrintWriter outwriter;
    private boolean open = false;
    private Socket socket;
    private boolean term = false;

    Connection(Socket socket) throws IOException {
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
        inreader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        outwriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));	
        //outwriter = new PrintWriter(out, true);
        this.socket = socket;
        open = true;
        start();
    }

    /*
     * returns true if the message was written, otherwise false
     */
    public boolean writeMsg(String msg) {
        if (open) {
            //outwriter.println(msg);
            //outwriter.flush();
            return true;
        }
        return false;
    }

    public void write(String msg) throws IOException {
        outwriter = new BufferedWriter(new OutputStreamWriter(getSocket().getOutputStream(), "UTF-8"));
        outwriter.write(msg + "\n");
	outwriter.flush();
        //out.write(msg);
        //out.flush();
    }

    public void closeCon() {
        if (open) {
            log.info("closing connection " + Settings.socketAddress(socket));
            try {
                term = true;
                inreader.close();
                out.close();
                outwriter.close();
            } catch (IOException e) {
                // already closed?
                log.error("received exception closing the connection " + Settings.socketAddress(socket) + ": " + e);
            }
        }
    }

    public void run() {
        try {
            JSONParser parser = new JSONParser();
            JSONObject command = null ;
            String data = null;
            /*while(!term && (data = inreader.readLine())!=null){
             term=Control.getInstance().process(this,data);
                                
             }*/
            ////////////////////////////////////
            while (!term && (data = inreader.readLine())!=null) {
                
                command = (JSONObject) parser.parse(data);
                inreader = new BufferedReader(new InputStreamReader(getSocket().getInputStream(), "UTF-8"));
        
                term = Control.getInstance().process(this, command);
                
                //System.out.println("object received:: "+data);
                //out.writeUTF(data);
            }
            
            
            log.debug("connection closed to " + Settings.socketAddress(socket));
            Control.getInstance().connectionClosed(this);
            Control.getInstance().serverList.remove(this);
            in.close();
        } catch (IOException e) {
            Timestamp temptimestamp = new Timestamp(System.currentTimeMillis());
            log.error("connection " + Settings.socketAddress(socket) + " closed with exception: " + e);
            String ipAddress = this.socket.getInetAddress().toString().substring(1);
            String portNumber = String.valueOf(this.socket.getPort());
            
            boolean clientFlag  = true; 
            Set set = Control.getInstance().serverInfo.entrySet();
            Iterator iterator = set.iterator();
            while (iterator.hasNext()) {
                Map.Entry temp = (Map.Entry) iterator.next();
                String[] tempArray = (String[]) temp.getValue();
                 if (tempArray[0].equals(ipAddress) && tempArray[1].equals(portNumber)) {
                     Control.getInstance().serverInfo.remove(temp.getKey());
                     clientFlag = false;
                     Control.getInstance().connectionClosed(this);
                     Control.getInstance().conLoggedInList.remove(this);
                     Control.getInstance().serverList.remove(this);
                     Control.getInstance().serverClosing(Settings.socketAddress(socket),temptimestamp);
                     break;
                 }
            } 
            
            if(clientFlag){
                
                String usernameToRemove = Control.getInstance().conLoggedInList.get(this);
                
                if(usernameToRemove == null)
                {   
                    Control.getInstance().connectionClosed(this);
                    Control.getInstance().conLoggedInList.remove(this);
                    Control.getInstance().serverList.remove(this);
                    Control.getInstance().serverClosing(Settings.socketAddress(socket),temptimestamp);
                    Control.getInstance().loggedInList.remove(usernameToRemove);
                }
                //System.out.println(usernameToRemove);
                else{
                    log.info("Client : " + usernameToRemove + " connection closed.");
                    try {
                        Control.getInstance().sendLogOutMessage(usernameToRemove,temptimestamp);
                    } catch (IOException ex) {
                        java.util.logging.Logger.getLogger(Connection.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    Control.getInstance().connectionClosed(this);
                    Control.getInstance().conLoggedInList.remove(this);
                    Control.getInstance().loggedInList.remove(usernameToRemove);
                }
            
            }else{
                Control.getInstance().connectionClosed(this);
                Control.getInstance().serverList.remove(this); 
                
            }
            
            
        } catch (ParseException ex) {
            java.util.logging.Logger.getLogger(Connection.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InterruptedException ex) {
            java.util.logging.Logger.getLogger(Connection.class.getName()).log(Level.SEVERE, null, ex);
        }
        open = false;
        try {
            socket.close();
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(Connection.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public Socket getSocket() {
        return socket;
    }

    public boolean isOpen() {
        return open;
    }
    
    public void setServerId(String s){
        server_id = s;
    }
}
