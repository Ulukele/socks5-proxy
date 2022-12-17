package ru.nsu.ccfit.arkhipov;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Specify exact 1 arg: port");
            return;
        }
        int port = Integer.parseInt(args[0]);
        Socks5Proxy proxy = new Socks5Proxy(port);
        proxy.run();
    }
}