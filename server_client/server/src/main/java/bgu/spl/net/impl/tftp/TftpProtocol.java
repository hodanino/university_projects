package bgu.spl.net.impl.tftp;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;

class holder {
    static ConcurrentHashMap<String, Integer> username_login = new ConcurrentHashMap<>();
    static ConcurrentHashMap<String, Boolean> exisitingFiles = new ConcurrentHashMap<>();
}

public class TftpProtocol implements BidiMessagingProtocol<byte[]> {

    private int connectionId;
    public Connections<byte[]> connections;
    private boolean shouldTerminate;
    private Path path;
    private byte[] ack;
    private ByteBuffer dataBufferIn;
    private String fileNameRRQ;
    private List<byte[]> data;
    private String username;
    private Path filesDirectory;

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
        this.data = new LinkedList<>(); // stores the data to send out
        this.shouldTerminate = false;
        this.filesDirectory = Paths.get("").toAbsolutePath().resolve("Flies");
        this.ack = new byte[] { 0, 4, 0, 0 };
        this.dataBufferIn = ByteBuffer.allocate(512);
        this.fileNameRRQ = null;
        File file = new File(filesDirectory.toString());
        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) {
                String fileName = f.getName();
                holder.exisitingFiles.put(fileName, true);
            }
        }
    }

    @Override
    public void process(byte[] message) {
        short opCode = (short) (((short) message[0]) << 8 | (short) (message[1]) & 0x00ff);
        // checking if user didn't login first thing
        if (opCode != 7 && !holder.username_login.containsValue(connectionId) && opCode <= 10) {
            connections.send(connectionId, errorType((byte) 0, (byte) 6));
        } else {
            switch (opCode) {
                case 1:// read request: download file from the server
                       // System.out.println("[" + LocalDateTime.now() + "]: " + opCode);
                    fileNameRRQ = new String(message, 2, message.length - 2, StandardCharsets.UTF_8);
                    if (holder.exisitingFiles.containsKey(fileNameRRQ)) {
                        this.path = filesDirectory.resolve(fileNameRRQ);
                        divideData();
                        ack[3] = (byte) 1;
                        process(ack);
                        ack[3] = (byte) 0;
                    } else {
                        connections.send(connectionId, errorType((byte) 0, (byte) 1));
                    }
                    break;

                case 2:// write request: Upload File from current working directory to the server -
                       // System.out.println("[" + LocalDateTime.now() + "]: " + opCode);
                    String fileNameWRQ = new String(message, 2, message.length - 2, StandardCharsets.UTF_8);
                    if (Files.exists(Paths.get(filesDirectory.toString(), fileNameWRQ))) {
                        connections.send(connectionId, errorType((byte) 0, (byte) 5));
                    } else {
                        try {
                            path = Files.createFile(
                                    FileSystems.getDefault().getPath(filesDirectory.resolve(fileNameWRQ).toString()));
                            // sending ACK
                            connections.send(connectionId, ack);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    break;

                case 3:// DATA
                       // System.out.println("[" + LocalDateTime.now() + "]: " + opCode);
                    short packetSize = (short) (((short) message[2]) << 8 | (short) (message[3]) & 0xff);
                    dataBufferIn.put(message, 6, packetSize);
                    try (FileOutputStream fos = new FileOutputStream(path.toString(), true)) {
                        // Write the contents of the ByteBuffer to the FileOutputStream
                        dataBufferIn.flip();
                        while (dataBufferIn.hasRemaining()) {
                            fos.write(dataBufferIn.get()); // Write byte-by-byte
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    // reset the buffer
                    dataBufferIn.clear();

                    // sending ack
                    ack[2] = message[4];
                    ack[3] = message[5];
                    connections.send(connectionId, ack);
                    ack[2] = (byte) 0;
                    ack[3] = (byte) 0;

                    // this is the last packet
                    if (packetSize < 512) {
                        File file = new File(path.toString());
                        String fName = file.getName();
                        holder.exisitingFiles.put(fName, true);
                        byte[] comp = new byte[13 + fName.length()];
                        comp[0] = 0;
                        comp[1] = 5;
                        comp[2] = 0;
                        comp[3] = 0;
                        byte[] m = (fName + " complete").getBytes(StandardCharsets.UTF_8);
                        System.arraycopy(m, 0, comp, 4, m.length);
                        connections.send(connectionId, comp);
                        byte[] fileName = fName.getBytes(StandardCharsets.UTF_8);
                        byte[] sendBCast = new byte[4 + fileName.length];
                        System.arraycopy(fileName, 0, sendBCast, 3, fileName.length);
                        sendBCast[0] = 0;
                        sendBCast[1] = 9;
                        sendBCast[2] = 1;
                        sendBCast[sendBCast.length - 1] = 0;
                        process(sendBCast);
                    }
                    break;

                case 4:// ACK -
                       // System.out.println("[" + LocalDateTime.now() + "]: " + opCode);
                    if (!data.isEmpty()) {// is there is data to send out
                        byte[] chunk = data.get(0);
                        byte[] dataOut = new byte[chunk.length + 6];
                        dataOut[0] = (byte) 0;
                        dataOut[1] = (byte) 3;
                        dataOut[4] = message[2];
                        dataOut[5] = message[3];
                        short size = (short) chunk.length;
                        dataOut[2] = (byte) (size >> 8);
                        dataOut[3] = (byte) (size & 0xFF);
                        System.arraycopy(chunk, 0, dataOut, 6, chunk.length);
                        data.remove(0);
                        connections.send(connectionId, dataOut);
                    } else if (fileNameRRQ != null) {// no more data to send and not a DIRQ
                        byte[] comp = new byte[13 + fileNameRRQ.length()];
                        comp[0] = 0;
                        comp[1] = 5;
                        comp[2] = 0;
                        comp[3] = 0;
                        byte[] m = (fileNameRRQ + " complete").getBytes(StandardCharsets.UTF_8);
                        System.arraycopy(m, 0, comp, 4, m.length);
                        connections.send(connectionId, comp);
                        fileNameRRQ = null;
                    }
                    break;

                case 6:// DIRQ List: all the file names that are in Files folder in the server
                       // System.out.println("[" + LocalDateTime.now() + "]: " + opCode);
                    ByteBuffer buffer = createDirqBuffer();
                    while (buffer.hasRemaining()) {
                        int remaining = buffer.remaining();
                        int currentChunkSize = Math.min(remaining, 512);
                        byte[] chunk = new byte[currentChunkSize];
                        buffer.get(chunk);
                        data.add(chunk);
                    }
                    ack[3] = (byte) 1;
                    process(ack);
                    ack[3] = (byte) 0;

                    break;

                case 7: // LOGRQ
                    // System.out.println("[" + LocalDateTime.now() + "]: " + opCode);
                    String name = new String(message, 2, message.length - 2, StandardCharsets.UTF_8);
                    if (!holder.username_login.containsKey(name)) {
                        Integer id = connectionId;
                        username = name;
                        holder.username_login.put(name, id);
                        connections.send(connectionId, ack);
                    } else {
                        connections.send(connectionId, errorType((byte) 0, (byte) 7));
                    }
                    break;

                case 8:// DELRQ(Delete File Request)
                       // System.out.println("[" + LocalDateTime.now() + "]: " + opCode);
                    String fileNameDELRQ = new String(message, 2, message.length - 2, StandardCharsets.UTF_8);
                    if (holder.exisitingFiles.containsKey(fileNameDELRQ)) {
                        File fileToDelete = new File(filesDirectory.resolve(fileNameDELRQ).toString());
                        fileToDelete.delete();
                        holder.exisitingFiles.remove(fileNameDELRQ);

                        // sending ack
                        connections.send(connectionId, ack);
                        // send BCAST
                        byte[] fileName = fileNameDELRQ.getBytes(StandardCharsets.UTF_8);
                        byte[] sendBCast = new byte[4 + fileName.length];
                        System.arraycopy(fileName, 0, sendBCast, 3, fileName.length);
                        sendBCast[0] = 0;
                        sendBCast[1] = 9;
                        sendBCast[2] = 0;
                        sendBCast[sendBCast.length - 1] = 0;
                        process(sendBCast);
                    } else {
                        connections.send(connectionId, errorType((byte) 0, (byte) 1));
                    }
                    break;

                case 9: // BCAST
                    // System.out.println("[" + LocalDateTime.now() + "]: " + opCode);
                    for (Integer id : holder.username_login.values())
                        connections.send(id, message);
                    break;

                case 10: // Disconnect (Server remove user from Logged-in list) from the server and close
                         // the program.
                    // System.out.println("[" + LocalDateTime.now() + "]: " + opCode);
                    holder.username_login.remove(username);
                    connections.send(connectionId, errorType((byte) 0, (byte) 8));
                    break;

                default:
                    connections.send(connectionId, errorType((byte) 0, (byte) 4));
                    break;
            }
            shouldTerminate = (opCode == 10);
        }
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    private byte[] errorType(Byte errorCode1, Byte errorCode2) {
        byte[] output1 = { 0, 5, errorCode1, errorCode2 };
        byte[] error;
        short errorCode = (short) (((short) errorCode1) << 8 | (short) (errorCode2) & 0x00ff);
        switch (errorCode) {
            case 0:
                error = "Not defined".getBytes(StandardCharsets.UTF_8);
                break;

            case 1:
                error = "File not found".getBytes(StandardCharsets.UTF_8);
                break;

            case 2:
                error = "Access violation".getBytes(StandardCharsets.UTF_8);
                break;

            case 3:
                error = "Disk full or allocation exceeded".getBytes(StandardCharsets.UTF_8);
                break;

            case 4:
                error = "Illegal TFTP operation".getBytes(StandardCharsets.UTF_8);
                break;

            case 5:
                error = "File already exists".getBytes(StandardCharsets.UTF_8);
                break;

            case 6:
                error = "User not logged in".getBytes(StandardCharsets.UTF_8);
                break;

            case 7:
                error = "User already logged in".getBytes(StandardCharsets.UTF_8);
                break;

            case 8:
                error = "you can close the socket".getBytes(StandardCharsets.UTF_8);
                break;

            default:
                error = null;
                break;
        }
        int len = output1.length + error.length;
        byte[] output = new byte[len];
        System.arraycopy(output1, 0, output, 0, output1.length);
        System.arraycopy(error, 0, output, output1.length, error.length);
        return output;
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

    public ByteBuffer createDirqBuffer() {
        int totalLength = 0;

        // Calculate the total length including strings and separators
        for (String key : holder.exisitingFiles.keySet()) {
            totalLength += key.getBytes(StandardCharsets.UTF_8).length + 1; // Add 1 for the separator byte
        }

        ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        for (String key : holder.exisitingFiles.keySet()) {
            buffer.put(key.getBytes(StandardCharsets.UTF_8));
            buffer.put((byte) 0);
        }
        // Set the position of the buffer to its current limit minus one
        buffer.position(buffer.limit() - 1);

        // Reset the limit of the buffer to its current position
        buffer.limit(buffer.position());
        buffer.flip();

        return buffer;
    }
}
