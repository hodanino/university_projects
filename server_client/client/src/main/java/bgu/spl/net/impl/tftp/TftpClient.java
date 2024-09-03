package bgu.spl.net.impl.tftp;

import java.io.IOException;
import java.net.Socket;

public class TftpClient {

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            args = new String[] { "localhost", "hello" };
        }

        if (args.length < 2) {
            System.out.println("you must supply two arguments: host, message");
            System.exit(1);
        }

        // Establish connection to the server
        try (Socket socket = new Socket(args[0], 7777)) {
            // Create and start the listening thread
            ClientEncDec encdec = new ClientEncDec(); // same here
            ListeningThread listeningThread = new ListeningThread(socket, encdec);
            listeningThread.start();

            // Create and start the keyboard input thread
            KeyboardThread keyboardThread = new KeyboardThread(socket, encdec,listeningThread);
            keyboardThread.start();

            // Wait for the threads to complete
            try {
                listeningThread.join();
                keyboardThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}