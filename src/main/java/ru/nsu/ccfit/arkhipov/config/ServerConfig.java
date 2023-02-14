package ru.nsu.ccfit.arkhipov.config;

public class ServerConfig {
    public final int bufferSize = 8192;

    public final String host = "127.0.0.1";
    public final int port;

    public ServerConfig(int port) {
        this.port = port;
    }
}
