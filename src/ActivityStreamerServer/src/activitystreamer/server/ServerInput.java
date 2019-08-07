/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package activitystreamer.server;

import java.util.Scanner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


public class ServerInput extends Thread {
    
    private static final Logger log = LogManager.getLogger();
    boolean disconnect = false;
    private JSONParser parser = new JSONParser();

    public ServerInput() {
        start();
    }
    public void run() {
        try (Scanner scanner = new Scanner(System.in)) {
            String inputStr = null;

            //While the user input differs from "exit"
            while (!disconnect) {
                inputStr = scanner.nextLine();
                String msg = inputStr.trim().replaceAll("\r", "").replaceAll("\n", "").replaceAll("\t", "");
                JSONObject obj;
                try {
                    //System.out.println(inputStr)
                    obj = (JSONObject) parser.parse(msg);
                    Control.getInstance().sendActivityObjectServer(obj);
                } catch (ParseException e1) {
                    log.error("invalid JSON object entered into input text field, data not sent");
                }
            }
        }

    }
    
}
