package ru.nsu.ccfit.arkhipov.util;

import ru.nsu.ccfit.arkhipov.config.Socks5Config;
import ru.nsu.ccfit.arkhipov.entity.Attachment;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

public enum ClientState implements ExecutableState {
    GREETING {
        private void authenticate(SelectionKey selectionKey) throws IOException {
            SocketChannel channel = ((SocketChannel) selectionKey.channel());
            channel.write(Socks5Config.getGreetingResponse());
            Attachment attachment = (Attachment) selectionKey.attachment();
            attachment.in.clear();
            attachment.state = ClientState.AUTHORISED;
        }

        @Override
        public void execute(SelectionKey selectionKey) throws IOException {
            Attachment attachment = (Attachment) selectionKey.attachment();
            byte[] body = attachment.in.array();

            if (body.length < 3) {
                throw new IllegalStateException("Greeting should contain VER, NAUTH, AUTH bytes");
            } else if (body[0] != Socks5Config.VERSION) {
                throw new IllegalStateException("Version should be equal 5");
            } else if (body[1] + 2 > body.length) {
                throw new IllegalStateException("Provided auth methods count mismatch with NAUTH");
            }

            for (int i = 2; i < body[1] + 2; ++i) {
                if (body[i] == Socks5Config.NO_AUTH_METHOD) {
                    authenticate(selectionKey);
                    return;
                }
            }
            throw new IllegalStateException("Provided auth methods don't contains NO AUTH method");
        }
    },
    AUTHORISED {

        private void connectToHost(SelectionKey selectionKey, InetSocketAddress socketAddress) throws IOException {
            Attachment attachment = (Attachment) selectionKey.attachment();

            SocketChannel peer = SocketChannel.open();
            peer.configureBlocking(false);
            peer.connect(socketAddress);
            SelectionKey peerKey = peer.register(selectionKey.selector(), SelectionKey.OP_CONNECT);
            selectionKey.interestOps( 0 );
            attachment.peer = peerKey;
            Attachment peerAttachment = new Attachment();
            peerAttachment.peer = selectionKey;
            peerKey.attach(peerAttachment);
            attachment.in.clear();
        }

        @Override
        public void execute(SelectionKey selectionKey) throws IOException {
            Attachment attachment = (Attachment) selectionKey.attachment();
            byte[] body = attachment.in.array();

            if (body.length < 4) {
                throw new IllegalStateException("Request body length: " + body.length);
            } else if (body[0] != Socks5Config.VERSION) {
                throw new IllegalStateException("Requested socks version: " + body[0]);
            } else if (body[1] != Socks5Config.TCP_IP_STREAM) {
                throw new IllegalStateException("Specified command: " + body[1]);
            }

            final InetAddress address;
            final int port;
            if (body[3] == Socks5Config.ADDR_IP_V4) {
                byte[] addr = new byte[] { body[4], body[5], body[6], body[7] };
                address = InetAddress.getByAddress(addr);
                port = (((0xFF & body[8]) << 8) + (0xFF & body[9]));
            } else if (body[3] == Socks5Config.ADDR_DOMAIN) {
                int domainSize = body[4];
                byte[] domain = new byte[domainSize];
                System.arraycopy(body, 5, domain, 0, domainSize);
                String domainStr = Arrays.toString(domain);
                address = InetAddress.getByName(domainStr);
                port = (((0xFF & body[5 + domainSize]) << 8) + (0xFF & body[6 + domainSize]));
            } else {
                throw new IllegalStateException("Specified method: " + body[3]);
            }
            InetSocketAddress socketAddress = new InetSocketAddress(address, port);
            connectToHost(selectionKey, socketAddress);
        }
    }
}
