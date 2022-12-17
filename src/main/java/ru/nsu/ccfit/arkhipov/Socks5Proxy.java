package ru.nsu.ccfit.arkhipov;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.*;
import java.nio.channels.spi.SelectorProvider;
import java.util.Arrays;
import java.util.Iterator;

import static ru.nsu.ccfit.arkhipov.Constants.*;

public class Socks5Proxy implements Runnable {

    private final int bufferSize = 8192;
    private final int port;
    private static final String host = "127.0.0.1";
    private final byte[] host_addr;

    private Selector selector;
    private ServerSocketChannel serverChannel;

    private byte[] getServerResponse(byte status) {
        byte[] response = new byte[4 + IPV4_SIZE + PORT_SIZE];
        response[0] = SOCKS_VERSION;
        response[1] = status;
        response[2] =  0x00;
        response[3] = IPV4_ADDR;
        byte[] port = ByteBuffer.allocate(PORT_SIZE).order(ByteOrder.BIG_ENDIAN).putShort((short)this.port).array();
        System.arraycopy(host_addr, 0, response, 4, IPV4_SIZE);
        System.arraycopy(port, 0, response, 4 + IPV4_SIZE, PORT_SIZE);
        return response;
    }

    public Socks5Proxy(int port) {
        this.port = port;
        this.host_addr = new InetSocketAddress(host, port).getAddress().getAddress();
    }

    private void configure() throws IOException {
        selector = SelectorProvider.provider().openSelector();
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.socket().bind(new InetSocketAddress(host, port));
        serverChannel.register(selector, serverChannel.validOps());
    }

    @Override
    public void run()  {
        try {
            configure();
            while (selector.select() > -1) {
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();
                    if (key.isValid()) {
                        try {
                            if (key.isAcceptable()) {
                                accept(key);
                            } else if (key.isConnectable()) {
                                connect(key);
                            } else if (key.isReadable()) {
                                read(key);
                            } else if (key.isWritable()) {
                                write(key);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            close(key);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void accept(SelectionKey key) throws IOException, ClosedChannelException {
        SocketChannel newChannel = ((ServerSocketChannel) key.channel()).accept();
        newChannel.configureBlocking(false);
        newChannel.register(key.selector(), SelectionKey.OP_READ);
        System.out.println("Accept new client: " + newChannel.getRemoteAddress());
    }

    private void connect(SelectionKey key) throws IOException {
        SocketChannel channel = ((SocketChannel) key.channel());
        Attachment attachment = ((Attachment) key.attachment());

        channel.finishConnect();
        System.out.println("Connected to: " + channel.getRemoteAddress());

        attachment.in = ByteBuffer.allocate(bufferSize);
        attachment.in.put(getServerResponse((byte)0x00)).flip();
        attachment.out = ((Attachment) attachment.peer.attachment()).in;
        ((Attachment) attachment.peer.attachment()).out = attachment.in;

        attachment.peer.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
        key.interestOps( 0 );
    }

    private void read(SelectionKey key) throws IOException, UnknownHostException, ClosedChannelException {
        SocketChannel channel = ((SocketChannel) key.channel());
        Attachment attachment = ((Attachment) key.attachment());
        if (attachment == null) {
            key.attach(attachment = new Attachment());
            attachment.in = ByteBuffer.allocate(bufferSize);
            attachment.state = null;
        }
        if (channel.read(attachment.in) < 1) {
            close(key);
        } else if (attachment.peer == null) {
            processState(key, attachment);
        } else {
            attachment.peer.interestOps(attachment.peer.interestOps() | SelectionKey.OP_WRITE);
            key.interestOps(key.interestOps() ^ SelectionKey.OP_READ);
            attachment.in.flip();
        }
    }

    private void processState(SelectionKey key, Attachment attachment) throws IOException {
        if (attachment.state == null) {
            attachment.state = ClientState.GREETING;
        }
        switch (attachment.state) {
            case GREETING -> {
                handleChooseAuthMethod(key, attachment);
            }
            case AUTHORISED -> {
                handleConnectionRequest(key, attachment);
            }
        }
    }

    private void handleChooseAuthMethod(SelectionKey key, Attachment attachment) throws IOException {
        byte[] ar = attachment.in.array();
        if (ar[0] != SOCKS_VERSION) {
            throw new IllegalStateException("Requested socks version: " + ar[0]);
        }
        int nAuth = ar[1];
        boolean noAuthProvided = false;
        for (int i = 0; i < nAuth; ++i) {
            if (ar[2 + i] == 0) {
                noAuthProvided = true;
                break;
            }
        }
        if (!noAuthProvided) {
            throw new IllegalStateException("There is no method without auth");
        } else {
            SocketChannel channel = ((SocketChannel) key.channel());
            channel.write(ByteBuffer.allocate(GREETING_RESPONSE.length).put(GREETING_RESPONSE).flip());
        }
        attachment.in.clear();
        attachment.state = ClientState.AUTHORISED;
    }

    private void handleConnectionRequest(SelectionKey key, Attachment attachment) throws IOException {
        byte[] ar = attachment.in.array();
        if (ar[0] != SOCKS_VERSION) {
            throw new IllegalStateException("Requested socks version: " + ar[0]);
        }
        if (ar[1] != TCP_STREAM) {
            throw new IllegalStateException("Specified command: " + ar[1]);
        }
        InetAddress address;
        int p;
        if (ar[3] == IPV4_ADDR) {
            byte[] addr = new byte[] { ar[4], ar[5], ar[6], ar[7] };
            address = InetAddress.getByAddress(addr);
            p = (((0xFF & ar[8]) << 8) + (0xFF & ar[9]));
        } else if (ar[3] == DOMAIN_ADDR) {
            int domainSize = ar[4];
            int l = 5;
            byte[] domain = new byte[domainSize];
            System.arraycopy(ar, l, domain, 0, domainSize);
            String domainStr = Arrays.toString(domain);
            address = InetAddress.getByName(domainStr);
            p = (((0xFF & ar[5 + domainSize]) << 8) + (0xFF & ar[6 + domainSize]));
        } else {
            throw new IllegalStateException("Specified method: " + ar[3]);
        }

        InetSocketAddress socketAddress = new InetSocketAddress(address, p);

        SocketChannel peer = SocketChannel.open();
        peer.configureBlocking(false);
        peer.connect(socketAddress);
        SelectionKey peerKey = peer.register(key.selector(), SelectionKey.OP_CONNECT);
        key.interestOps( 0 );
        attachment.peer = peerKey;
        Attachment peerAttachment = new Attachment();
        peerAttachment.peer = key;
        peerKey.attach(peerAttachment);
        attachment.in.clear();
    }

    private void write(SelectionKey key) throws IOException {
        SocketChannel channel = ((SocketChannel) key.channel());
        Attachment attachment = ((Attachment) key.attachment());
        if (channel.write(attachment.out) == -1) {
            close(key);
        } else if (attachment.out.remaining() ==  0 ) {
            if (attachment.peer == null) {
                close(key);
            } else {
                attachment.out.clear();
                attachment.peer.interestOps(attachment.peer.interestOps() | SelectionKey.OP_READ);
                key.interestOps(key.interestOps() ^ SelectionKey.OP_WRITE);
            }
        }
    }

    private void close(SelectionKey key) throws IOException {
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

    private static class Attachment {
        ByteBuffer in;
        ByteBuffer out;
        SelectionKey peer;
        ClientState state;
    }

    private enum ClientState {
        GREETING,
        AUTHORISED
    }
}
