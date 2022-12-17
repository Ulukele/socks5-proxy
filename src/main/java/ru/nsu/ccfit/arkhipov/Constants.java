package ru.nsu.ccfit.arkhipov;

public class Constants {
//    SOCKS 5
    public static byte SOCKS_VERSION = 0x05;

//    For requests
    public static byte TCP_STREAM = 0x01;

//    For addresses
    public static byte IPV4_ADDR = 0x01;
    public static byte DOMAIN_ADDR = 0x03;

//    For sizes
    public static int PORT_SIZE = 2;
    public static int IPV4_SIZE = 4;

    // For packets
    public static byte[] GREETING_RESPONSE = new byte[] {SOCKS_VERSION, 0x00};
}
