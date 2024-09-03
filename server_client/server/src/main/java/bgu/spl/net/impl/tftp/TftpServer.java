package bgu.spl.net.impl.tftp;

import bgu.spl.net.srv.Server;

public class TftpServer {

    // you can use any server...
    public static void main(String[] args) {
        Server.threadPerClient(
                7777, // port
                () -> new TftpProtocol(), // protocol factory
                TftpEncoderDecoder::new // message encoder decoder factory
        ).serve();
    }
}
