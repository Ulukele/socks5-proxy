package ru.nsu.ccfit.arkhipov.config;

import java.nio.ByteBuffer;

public class Socks5Config {
    public static byte VERSION = 0x05;

    public static byte RESERVED_BYTE = 0x00;

    // Auth methods
    // No more methods provided, simple socks proxy
    public static byte NO_AUTH_METHOD = 0x00;

    // Auth responses
    public static byte AUTH_RESP_SUCCESS = 0x00;
    public static byte AUTH_RESP_FAILURE = 0x01;

    // Responses
    public static byte REQUEST_GRANTED_RESPONSE = 0x00;
    public static byte GENERAL_FAILURE_RESPONSE = 0x01;
    public static byte CONN_NOT_ALLOWED_BY_RULESET_RESPONSE = 0x02;
    public static byte NETWORK_UNREACHABLE_RESPONSE = 0x03;
    public static byte HOST_UNREACHABLE_RESPONSE = 0x04;
    public static byte CONN_REFUSED_BY_DEST_HOST_RESPONSE = 0x05;
    public static byte TTL_EXPIRED_RESPONSE = 0x06;
    public static byte PROTOCOL_ERROR_RESPONSE = 0x07;
    public static byte ADDR_TYPE_NOT_SUPPORTED_RESPONSE = 0x08;

    public static byte[] GREETING_RESPONSE = new byte[] {Socks5Config.VERSION, 0x00};

    // Address Types
    public static byte ADDR_IP_V4 = 0x01;
    public static byte ADDR_DOMAIN = 0x03;
    public static byte ADDR_IP_V6 = 0x04;

    // Connection Commands
    public static byte TCP_IP_STREAM = 0x01;
    public static byte TCP_IP_PORT_BINDING = 0x02;
    public static byte ASSOCIATE_UDP_PORT = 0x03;

    // Size constants
    public static int PORT_SIZE = 2;
    public static int IPV4_SIZE = 4;

    public static ByteBuffer getGreetingResponse() {
        return ByteBuffer.allocate(GREETING_RESPONSE.length).put(GREETING_RESPONSE).flip();
    }
}
