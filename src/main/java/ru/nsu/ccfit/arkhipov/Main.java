package ru.nsu.ccfit.arkhipov;

import ru.nsu.ccfit.arkhipov.config.ServerConfig;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Specify exact 1 arg: port");
            return;
        }
        int port = Integer.parseInt(args[0]);
        ServerConfig serverConfig = new ServerConfig(port);
        Server server = new Server(serverConfig);
        try {
            server.run();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}