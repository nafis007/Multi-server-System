package activitystreamer.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import activitystreamer.util.Settings;
import java.util.logging.Level;

public class Listener extends Thread{
	private static final Logger log = LogManager.getLogger();
	private ServerSocket serverSocket=null;
	private boolean term = false;
	private int portnum;
	
	public Listener() throws IOException{
		portnum = Settings.getLocalPort(); // keep our own copy in case it changes later
		serverSocket = new ServerSocket(portnum);
                //Control.getInstance().initiateConnection();
		start();
	}
	
	@Override
	public void run() {
                
		log.info("listening for new connections on "+ portnum);
                Control.getInstance().initiateConnection();
		while(!term){
			Socket clientSocket;
			try {
				clientSocket = serverSocket.accept();
				Control.getInstance().incomingConnection(clientSocket);
			} catch (IOException e) {
				log.info("received exception, shutting down");
				term=true;
			}
		}
            try {
                serverSocket.close();
            } catch (IOException ex) {
                java.util.logging.Logger.getLogger(Listener.class.getName()).log(Level.SEVERE, null, ex);
            }
	}

	public void setTerm(boolean term) {
		this.term = term;
		if(term) interrupt();
	}
	
	
}
