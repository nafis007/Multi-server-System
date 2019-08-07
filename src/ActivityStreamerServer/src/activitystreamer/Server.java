package activitystreamer;


import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import activitystreamer.server.Control;

import activitystreamer.util.Settings;

public class Server {
	private static final Logger log = LogManager.getLogger();
        
	
	private static void help(Options options){
		String header = "An ActivityStream Server for Unimelb COMP90015\n\n";
		String footer = "\ncontact aharwood@unimelb.edu.au for issues.";
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("ActivityStreamer.Server", header, options, footer, true);
		System.exit(-1);
	}
	
	public static void main(String[] args) {
		//System.out.println("checking arg length: " + args.length);
		log.info("reading command line options");
		Options options = new Options();
		options.addOption("lp",true,"local port number");
		options.addOption("rp",true,"remote port number");
		options.addOption("rh",true,"remote hostname");
		options.addOption("lh",true,"local hostname");
		options.addOption("a",true,"activity interval in milliseconds");
		options.addOption("s",true,"secret for the server to use");
		//options.addOption("t",true,"secret for the server to use");
		//help(options);
		// build the parser
		CommandLineParser parser = new DefaultParser();
		//System.out.println(args[0]);
		CommandLine cmd = null;
		try {
			cmd = parser.parse( options, args);
                        //System.out.println("in try "+ args.length);
                        
		} catch (ParseException e1) {
			help(options);
		}
		
		if(cmd.hasOption("lp")){
                    //System.out.println("check for lp");
			try{
				int port = Integer.parseInt(cmd.getOptionValue("lp"));
                                //log.info("Check port " + port);
                                //System.out.println("check number of port: "+port);
				Settings.setLocalPort(port);
			} catch (NumberFormatException e){
				log.info("-lp requires a port number, parsed: "+cmd.getOptionValue("lp"));
				help(options);
			}
		}
		
		if(cmd.hasOption("rh")){
			Settings.setRemoteHostname(cmd.getOptionValue("rh"));
		}
		
		if(cmd.hasOption("rp")){
			try{
				int port = Integer.parseInt(cmd.getOptionValue("rp"));
				Settings.setRemotePort(port);
			} catch (NumberFormatException e){
				log.error("-rp requires a port number, parsed: "+cmd.getOptionValue("rp"));
				help(options);
			}
		}
		
		if(cmd.hasOption("a")){
			try{
				int a = Integer.parseInt(cmd.getOptionValue("a"));
				Settings.setActivityInterval(a);
			} catch (NumberFormatException e){
				log.error("-a requires a number in milliseconds, parsed: "+cmd.getOptionValue("a"));
				help(options);
			}
		}
		
		try {
			Settings.setLocalHostname(InetAddress.getLocalHost().getHostAddress());
		} catch (UnknownHostException e) {
			log.warn("failed to get localhost IP address");
		}
		
		if(cmd.hasOption("lh")){
			Settings.setLocalHostname(cmd.getOptionValue("lh"));
		}

		if(cmd.hasOption("s")){
			Settings.setSecret(cmd.getOptionValue("s"));
		}
                
                if(cmd.hasOption("rp") && cmd.hasOption("rh") && cmd.hasOption("s"))
                {
                    Settings.setRemoteFlag(true);
                }
		
                
		log.info("starting server");
		
		
		final Control c = Control.getInstance(); 
		// the following shutdown hook doesn't really work, it doesn't give us enough time to
		// cleanup all of our connections before the jvm is terminated.
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {  
				c.setTerm(true);
				c.interrupt();
		    }
		 });
	}

}
