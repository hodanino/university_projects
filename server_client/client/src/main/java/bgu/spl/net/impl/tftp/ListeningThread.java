package bgu.spl.net.impl.tftp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.nio.ByteBuffer;
import bgu.spl.net.api.MessageEncoderDecoder;

/*
 * taking care of the following actions:
 * DATA - saving the data to a file or buffer
 * ACK - print to the terminal
 * BCAST - print
 * ERROR - print
 */
public class ListeningThread extends Thread {
    private Socket socket;
    private final MessageEncoderDecoder<byte[]> encdec;
    private List<byte[]> data;
    public String fileNameRRQ;
    public String fileNameWRQ;
    private BufferedOutputStream serverOut;
    private Path path;
    private boolean shouldTerminate;

    public ListeningThread(Socket socket, MessageEncoderDecoder<byte[]> encdec) {
        this.socket = socket;
        this.encdec = encdec;
        this.fileNameRRQ = null;
        this.fileNameWRQ = null;
        this.data = new LinkedList<byte[]>();
        this.path = null;
        this.shouldTerminate = false;
    }

    @Override
    public void run() {
        try {
            BufferedInputStream serverIn = new BufferedInputStream(socket.getInputStream());
            serverOut = new BufferedOutputStream(socket.getOutputStream());
            int response;
            while ((response = serverIn.read()) >= 0 && !shouldTerminate) {
                byte[] nextMessage = encdec.decodeNextByte((byte) response);
                if (nextMessage != null) {
                    short opCode = (short) (((short) nextMessage[0]) << 8 | (short) (nextMessage[1]) & 0x00ff);
                    switch (opCode) {
                        case 3:
                            handleData(nextMessage);
                            break;

                        case 4:
                            handleAck(nextMessage);
                            break;

                        case 5:
                            handleError(nextMessage);
                            break;

                        case 9:
                            handleBcast(nextMessage);
                            break;

                        default:
                            break;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleData(byte[] msg) {
        int packetSize = msg.length - 6;
        ByteBuffer dataBufferIn = ByteBuffer.allocate(packetSize);
        dataBufferIn.put(msg, 6, packetSize);
        dataBufferIn.flip();
        if (fileNameRRQ != null) {// need to write the data into a file
            Path currentDirectory = Paths.get("").toAbsolutePath();
            try (FileOutputStream fos = new FileOutputStream(currentDirectory.resolve(fileNameRRQ).toString(), true)) {
                // Write the contents of the ByteBuffer to the FileOutputStream
                while (dataBufferIn.hasRemaining()) {
                    fos.write(dataBufferIn.get()); // Write byte-by-byte
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            // reset the buffer
            dataBufferIn.clear();

            // sending ack
            short prevBN = (short) (((short) msg[4]) << 8 | (short) (msg[5]) & 0x00ff);
            prevBN++;
            byte[] ack = { (byte) 0, (byte) 4, (byte) ((prevBN) >> 8), (byte) (prevBN & 0xff) };
            try {
                serverOut.write(ack);
                serverOut.flush();
            } catch (Exception e) {
            }

            // this is the last packet
            if (packetSize < 512) {
                fileNameRRQ = null;
            }
        } else {// DIRQ print until 0;
            while (dataBufferIn.hasRemaining()) {
                StringBuilder stringBuilder = new StringBuilder();
                byte currentByte;
                while ((dataBufferIn.position() != dataBufferIn.limit()) && (currentByte = dataBufferIn.get()) != 0) {
                    stringBuilder.append((char) currentByte);
                }
                System.out.println(stringBuilder.toString());
            }
            // reset the buffer
            dataBufferIn.clear();
        }
    }

    private void handleAck(byte[] msg) {
        // printing the block number
        try {
            // if we sent an WRQ we need to get the file
            if (fileNameWRQ != null) {
                this.path = Paths.get("").toAbsolutePath().resolve(fileNameWRQ);
                divideData();
                fileNameWRQ = null;
            }
            short blockNum = (short) ((short) (msg[2]) << 8 | (short) (msg[3]) & 0xff);
            System.out.println("ACK " + blockNum);
            blockNum++;
            if (!data.isEmpty()) {
                byte[] chunk = data.get(0);
                byte[] dataOut = new byte[chunk.length + 6];
                dataOut[0] = (byte) 0;
                dataOut[1] = (byte) 3;
                dataOut[4] = (byte) (((short) blockNum >> 8));
                dataOut[5] = (byte) ((short) blockNum & 0x00ff);
                dataOut[2] = (byte) (((short) chunk.length >> 8) & 0x00ff);
                dataOut[3] = (byte) ((short) chunk.length & 0x00ff);
                System.arraycopy(chunk, 0, dataOut, 6, chunk.length);
                data.remove(0);
                serverOut.write(dataOut); // send out to server
                serverOut.flush();
            }
        } catch (Exception e) {
        }
    }

    private void handleError(byte[] msg) {// if error is file not exist in server delete it from directory
        String error = new String(msg, 4, msg.length - 4, StandardCharsets.UTF_8);
        short errorCode = (short) (((short) msg[2]) << 8 | (short) (msg[3]) & 0x00ff);
        if (fileNameWRQ != null) {// reset the flag
            fileNameWRQ = null;
        }
        if (errorCode == 1) {
            Path currentDirectory = Paths.get("").toAbsolutePath();
            try {
                Path newPath = Paths.get(currentDirectory.resolve(fileNameRRQ).toString());
                Files.delete(newPath);
            } catch (Exception e) {
            }
        }
        if (errorCode == 8)
            shouldTerminate = true;
        System.out.println(error);
    }

    private void handleBcast(byte[] msg) {
        String fileName = new String(msg, 3, msg.length - 3, StandardCharsets.UTF_8);
        System.out.print("BCAST ");
        if (msg[2] == (byte) 1)
            System.out.print("add ");
        else
            System.out.print("del ");
        System.out.println(fileName);
    }

    public void setFileNameWRQ(String fileNameWRQ) {
        this.fileNameWRQ = fileNameWRQ;
    }

    public void setFileNameRRQ(String fileNameRRQ) {
        this.fileNameRRQ = fileNameRRQ;
    }

    private void divideData() {
        try (FileInputStream fis = new FileInputStream(this.path.toString())) {
            byte[] buffer = new byte[512]; // Buffer to read data
            int bytesRead;

            // Read data in chunks of 512 bytes until end of file
            while ((bytesRead = fis.read(buffer)) != -1) {
                // Create a new byte[] chunk and copy the read data into it
                byte[] chunk = new byte[bytesRead];
                System.arraycopy(buffer, 0, chunk, 0, bytesRead);

                // Add the chunk to the list
                data.add(chunk);
            }
        } catch (Exception e) {
        }
        this.path = null;
    }
}