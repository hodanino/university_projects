package bgu.spl.net.impl.tftp;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import bgu.spl.net.api.MessageEncoderDecoder;

/*taking care of the following actions:
- LOGRQ<username> = sending logrq to server 
- DELRQ<filename> = sending delrq to server
- RRQ<filename> = create an empty file if not exists
- WRQ<filename> = if file exists send wrq to server
- DIRQ
- DISC
 */

public class KeyboardThread extends Thread {
    private Socket socket;
    private final MessageEncoderDecoder<byte[]> encdec;
    private ListeningThread listeningThread;
    private boolean shouldTerminate;

    public KeyboardThread(Socket socket, MessageEncoderDecoder<byte[]> encdec, ListeningThread listeningThread) {
        this.socket = socket;
        this.encdec = encdec;
        this.listeningThread = listeningThread;
        this.shouldTerminate = false;
    }

    @Override
    public void run() {
        try {
            BufferedOutputStream serverOut = new BufferedOutputStream(socket.getOutputStream());
            Scanner scanner = new Scanner(System.in);

            while (!shouldTerminate) {
                String userInput = scanner.nextLine();
                // need to implement methods and call for example RRQ to check if we have this
                // file and open a file accordingly
                byte[] message = encdec.encode(userInput.getBytes(StandardCharsets.UTF_8));
                short opCode = (short) (((short) message[0]) << 8 | (short) (message[1]) & 0x00ff);

                if (opCode == 1) {
                    // RRQ: if the file exists here ERROR, else open a file and save the name
                    String fileName = new String(message, 2, message.length - 3, StandardCharsets.UTF_8); // Replace
                                                                                                          // with your
                                                                                                          // desired
                                                                                                          // file name
                    File file = new File(fileName);
                    if (!file.createNewFile()) {
                        System.out.println("File already exists");
                        continue;
                    } else
                        listeningThread.fileNameRRQ = fileName;
                } else if (opCode == 2) {// WRQ: if file does not exists here ERROR, else save all data
                    String fileName = new String(message, 2, message.length - 3, StandardCharsets.UTF_8);
                    File file = new File(fileName);
                    if (!file.exists()) {
                        System.out.println("File does not exist");
                        continue;
                    }
                    listeningThread.setFileNameWRQ(fileName);
                }
                if (opCode == 10)
                    shouldTerminate = true;
                serverOut.write(message);
                serverOut.flush();
                userInput = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
