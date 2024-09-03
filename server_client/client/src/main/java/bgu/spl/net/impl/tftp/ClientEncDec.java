package bgu.spl.net.impl.tftp;

import java.nio.charset.StandardCharsets;
import bgu.spl.net.api.MessageEncoderDecoder;

public class ClientEncDec implements MessageEncoderDecoder<byte[]> {

    private byte[] bytes = new byte[0];
    private int dataLength = Integer.MAX_VALUE;
    private int opCode = -1;

    @Override
    public byte[] encode(byte[] message) {
        byte[] opCodeS = new byte[5];
        int i = 0;
        while (i < message.length && i < opCodeS.length && message[i] != (byte) (' ')) {// extracting opCode string
            opCodeS[i] = message[i++];
        }
        String stringOpCode = new String(opCodeS, 0, i, StandardCharsets.UTF_8);
        int opCode;
        switch (stringOpCode) {
            case "RRQ":
                opCode = 1;
                break;

            case "WRQ":
                opCode = 2;
                break;

            case "DIRQ":
                opCode = 6;
                break;

            case "DISC":
                opCode = 10;
                break;

            case "DELRQ":
                opCode = 8;
                break;

            case "LOGRQ":
                opCode = 7;
                break;

            default:
                opCode = -1;
                break;
        }
        byte[] s = new byte[] { (byte) (opCode >> 8), (byte) (opCode & 0xff) };

        if (opCode == 1 || opCode == 2 || opCode == 7 || opCode == 8) {
            int len = s.length + message.length - i;
            byte[] msg = new byte[len];
            System.arraycopy(s, 0, msg, 0, s.length);
            System.arraycopy(message, i + 1, msg, s.length, message.length - i - 1);
            msg[msg.length - 1] = 0;
            return msg;
        }
        return s;
    }

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        if (bytes.length >= dataLength && nextByte == 0x0) {
            byte[] message = bytes.clone();
            bytes = new byte[0];
            opCode = -1;
            return message;
        } else {
            byte[] newBytes = new byte[bytes.length + 1];
            System.arraycopy(bytes, 0, newBytes, 0, bytes.length);
            newBytes[bytes.length] = nextByte;

            bytes = newBytes;

            if (bytes.length == 2) {
                opCode = ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
                switch (opCode) {
                    case 1:
                    case 2:
                    case 6:
                    case 7:
                    case 8:
                    case 10:
                        dataLength = 2;
                        break;
                    case 9:
                        dataLength = 3;
                        break;
                    case 4:
                    case 5:
                        dataLength = 4;
                        break;
                    case 3:
                        dataLength = 6;
                        break;
                    default:
                        dataLength = Integer.MAX_VALUE;
                        break;
                }
            }
            if (opCode == 3 && bytes.length == 4) {
                int size = (int) ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
                dataLength = 6 + size;
            }
            if (!lastByteZero() && bytes.length == dataLength) {
                byte[] message = bytes.clone();
                bytes = new byte[0];
                opCode = -1;
                return message;
            }
            return null;
        }
    }

    private boolean lastByteZero() {
        return (opCode == 1 ||
                opCode == 2 ||
                opCode == 5 ||
                opCode == 9 ||
                opCode == 7 ||
                opCode == 8);
    }
}
