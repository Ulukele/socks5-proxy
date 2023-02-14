package ru.nsu.ccfit.arkhipov;

import ru.nsu.ccfit.arkhipov.config.ServerConfig;
import ru.nsu.ccfit.arkhipov.config.Socks5Config;
import ru.nsu.ccfit.arkhipov.entity.Attachment;
import ru.nsu.ccfit.arkhipov.util.ClientState;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.*;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;

public class Server {

    private final int port;

    private final String host;

    private final byte[] host_addr;

    private final int bufferSize;

    private Selector selector;

    public Server(ServerConfig serverConfig) {
        this.port = serverConfig.port;
        this.host = serverConfig.host;
        this.bufferSize = serverConfig.bufferSize;
        this.host_addr = new InetSocketAddress(host, port).getAddress().getAddress();
    }

    private void configure() throws IOException {
        selector = SelectorProvider.provider().openSelector();
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.socket().bind(new InetSocketAddress(host, port));
        serverChannel.register(selector, serverChannel.validOps());
    }

    public void run() throws IOException {
        configure();
        System.out.println("Start listening on " + host + ":" + port);

        while (selector.select() > -1) {
            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                if (key.isValid()) {
                    processSelectionKey(key);
                }
            }
        }
    }

    private byte[] getServerResponse(byte status) {
        byte[] response = new byte[4 + Socks5Config.IPV4_SIZE + Socks5Config.PORT_SIZE];
        response[0] = Socks5Config.VERSION;
        response[1] = status;
        response[2] =  Socks5Config.RESERVED_BYTE;
        response[3] = Socks5Config.ADDR_IP_V4;
        byte[] port = ByteBuffer.allocate(Socks5Config.PORT_SIZE)
                .order(ByteOrder.BIG_ENDIAN).putShort((short)this.port).array();
        System.arraycopy(host_addr, 0, response, 4, Socks5Config.IPV4_SIZE);
        System.arraycopy(port, 0, response, 4 + Socks5Config.IPV4_SIZE, Socks5Config.PORT_SIZE);
        return response;
    }

    private void processSelectionKey(SelectionKey key) {
        try {
            if (key.isAcceptable()) {
                acceptKey(key);
            } else if (key.isConnectable()) {
                connectKey(key);
            } else if (key.isReadable()) {
                readKey(key);
            } else if (key.isWritable()) {
                writeKey(key);
            }
            return;
        } catch (Exception e) {
            e.printStackTrace();
        }

        // If exception occurred
        try {
            closeKey(key);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void acceptKey(SelectionKey key) throws IOException {
        SocketChannel clientChannel = ((ServerSocketChannel) key.channel()).accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(key.selector(), SelectionKey.OP_READ);
        System.out.println("Accept new client: " + clientChannel.getRemoteAddress());
    }

    private void connectKey(SelectionKey key) throws IOException {
        SocketChannel channel = ((SocketChannel) key.channel());
        Attachment attachment = ((Attachment) key.attachment());

        channel.finishConnect();
        System.out.println("Connected to: " + channel.getRemoteAddress());
        attachment.createInBuffer(bufferSize);
        attachment.in.put(getServerResponse(Socks5Config.REQUEST_GRANTED_RESPONSE)).flip();
        attachment.out = ((Attachment) attachment.peer.attachment()).in;
        ((Attachment) attachment.peer.attachment()).out = attachment.in;

        attachment.peer.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
        key.interestOps( 0 );
    }

    private void readKey(SelectionKey key) throws IOException {
        SocketChannel channel = ((SocketChannel) key.channel());
        Attachment attachment = ((Attachment) key.attachment());
        if (attachment == null) {
            key.attach(attachment = new Attachment());
            attachment.createInBuffer(bufferSize);
            attachment.state = ClientState.GREETING;
        }
        if (channel.read(attachment.in) < 1) {
            closeKey(key);
        } else if (attachment.peer == null) {
            attachment.state.execute(key);
        } else {
            attachment.peer.interestOps(attachment.peer.interestOps() | SelectionKey.OP_WRITE);
            key.interestOps(key.interestOps() ^ SelectionKey.OP_READ);
            attachment.in.flip();
        }
    }

    private void writeKey(SelectionKey key) throws IOException {
        SocketChannel channel = ((SocketChannel) key.channel());
        Attachment attachment = ((Attachment) key.attachment());

        final int writeSize = channel.write(attachment.out);
        if (writeSize == -1) {
            closeKey(key);
        } else if (attachment.out.remaining() ==  0) {
            if (attachment.peer == null) {
                closeKey(key);
            } else {
                attachment.out.clear();
                attachment.peer.interestOps(attachment.peer.interestOps() | SelectionKey.OP_READ);
                key.interestOps(key.interestOps() ^ SelectionKey.OP_WRITE);
            }
        }
    }

    private void closeKey(SelectionKey key) throws IOException {
        key.cancel();
        key.channel().close();
        SelectionKey peerKey = ((Attachment) key.attachment()).peer;
        if (peerKey != null) {
            ((Attachment) peerKey.attachment()).peer = null;
            if ((peerKey.interestOps() & SelectionKey.OP_WRITE) == 0) {
                ((Attachment) peerKey.attachment()).out.flip();
            }
            peerKey.interestOps(SelectionKey.OP_WRITE);
        }
    }
}
