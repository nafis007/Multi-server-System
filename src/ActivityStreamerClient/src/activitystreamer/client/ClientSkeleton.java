package activitystreamer.client;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import activitystreamer.util.Settings;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.logging.Level;

public class ClientSkeleton extends Thread {

    private static final Logger log = LogManager.getLogger();
    private static ClientSkeleton clientSolution;
    private static ClientInput ClientInput;
    private TextFrame textFrame;

    private static Socket socket;
    //private static DataInputStream input;
    //private static DataOutputStream output;
    private static BufferedReader reader;
    private static BufferedWriter writer;
    boolean disconnect = false;
    boolean threadRunning = true;

    public static ClientSkeleton getInstance() {
        if (clientSolution == null) {
            clientSolution = new ClientSkeleton();
            ClientInput = new ClientInput();

        }
        return clientSolution;
    }

    public ClientSkeleton() {

        try {
            // Output and Input Stream
            //System.out.println(Settings.getRemotePort());
            socket = new Socket(Settings.getRemoteHostname(), Settings.getRemotePort());
            //input = new DataInputStream(socket.getInputStream());
            //output = new DataOutputStream(socket.getOutputStream());

            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));

        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {

        }

        //textFrame = new TextFrame(); // maybe for gui, commenting out to check
        start();
    }

    @SuppressWarnings("unchecked")
    public void sendActivityObject(JSONObject activityObj) {
        //System.out.println("trying to send object");
        //textFrame.setOutputText(activityObj);

        //////////////////////// trying to send to actual server//////////////////////////////////
        try {

            writer.write(activityObj.toJSONString() + "\n");
            writer.flush();

            if (activityObj.get("command").equals("LOGOUT")) {
                disconnect = true; 
            }

        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {

        }

       
    }

    public void disconnect() throws IOException {

        JSONObject activityObj = new JSONObject();
        activityObj.put("command", "LOGOUT");

        writer.write(activityObj.toJSONString() + "\n");
        writer.flush();
        disconnect = true;

        
    }

    @Override
    public void run() {

        while (true) {

            String data = null;
            JSONParser parser = new JSONParser();
            JSONObject command = null;
            try {
                while (!disconnect) {
                    if ((data = reader.readLine()) != null) {
                        //System.out.println("received from server: " + data);
                        //textFrame.setOutputText(data);
                        log.info(data);
                        command = (JSONObject) parser.parse(data);
                        if (command.get("command").equals("REGISTER_FAILED")
                                || command.get("command").equals("LOGIN_FAILED")
                                || command.get("command").equals("INVALID_MESSAGE")) {
                            System.exit(0);
                        }
                        if(command.get("command").equals("REGISTER_SUCCESS")){
                            if(Settings.getregisterClient())
                            {
                                log.info("Secret for the user " + Settings.getUsername() + " is : " + Settings.getSecret());
                                JSONObject newCommand = new JSONObject();
                                newCommand.put("command", "LOGIN");
                                newCommand.put("username", Settings.getUsername());
                                newCommand.put("secret", Settings.getSecret());
                                
                                sendActivityObject(newCommand);
                            } 
                        }
                        if(command.get("command").equals("REDIRECT"))
                        {
                            String hostname = command.get("hostname").toString();
                            String port = command.get("port").toString();
                            writer.close();
                            reader.close();
                            socket.close();
                            Settings.setRemoteHostname(hostname);
                            Settings.setRemotePort(Integer.parseInt(port));
                            
                            if(hostname.equals("localhost"))
                            {
                                Settings.setRemoteHostname(InetAddress.getLocalHost().toString());
                            }
                            else
                            {
                                socket = new Socket(Settings.getRemoteHostname(), Settings.getRemotePort());
                            
                            }
                            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
                            
                            JSONObject newCommand = new JSONObject();
                            newCommand.put("command", "LOGIN");
                            newCommand.put("username", Settings.getUsername());
                            newCommand.put("secret", Settings.getSecret());
                            sendActivityObject(newCommand); 
                            
                        }

                    }
                }
            } catch (IOException ex) {
                log.info("Server closed the connection");
                //java.util.logging.Logger.getLogger(ClientSkeleton.class.getName()).log(Level.SEVERE, null, ex);
            } catch (ParseException ex) {
                java.util.logging.Logger.getLogger(ClientSkeleton.class.getName()).log(Level.SEVERE, null, ex);
            } 
            
            try {
                writer.close();
                reader.close();
                socket.close();
            } catch (IOException ex) {
                java.util.logging.Logger.getLogger(ClientSkeleton.class.getName()).log(Level.SEVERE, null, ex);
            }

            System.exit(0);
        }
    }

}
