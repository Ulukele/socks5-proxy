package ru.nsu.ccfit.arkhipov.entity;

import ru.nsu.ccfit.arkhipov.util.ClientState;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

public class Attachment {
    public ByteBuffer in;
    public ByteBuffer out;
    public SelectionKey peer;
    public ClientState state;

    public void createInBuffer(int bufferSize) {
        in = ByteBuffer.allocate(bufferSize);
    }
}
