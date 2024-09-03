package bgu.spl.net.impl.tftp;

import java.util.Arrays;
import bgu.spl.net.api.MessageEncoderDecoder;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {

    private byte[] bytes = new byte[0];
    private int opCode = -1;
    private int dataLength = Integer.MAX_VALUE;

    @Override
    public byte[] encode(byte[] message) {
        short opCode = (short) (((short) message[0]) << 8 | (short) (message[1]) & 0xff);
        if (opCode == 1 || opCode == 2 || opCode == 5 || opCode == 7 || opCode == 8) {
            byte[] msg = Arrays.copyOf(message, message.length + 1);
            msg[msg.length - 1] = 0;
            return msg;
        }
        return message;
    }

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        if (bytes.length >= dataLength && nextByte == 0x0) {
            byte[] message = bytes.clone();
            bytes = new byte[0];// reset
            opCode = -1;
            return message;
        } else {
            byte[] newBytes = new byte[bytes.length + 1];
            System.arraycopy(bytes, 0, newBytes, 0, bytes.length);
            newBytes[bytes.length] = nextByte;
            bytes = newBytes;

            if (bytes.length == 2) {// check the opCode and initialize dataLen
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
                    default:// illigal operation
                        byte[] message = { 0, 15 };
                        bytes = new byte[0];
                        return message;
                }
            }
            if (opCode == 3 && bytes.length == 4) {// size of message in LOGRQ
                int a = ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
                dataLength = 6 + a;
            }
            if (!lastByteZero() && bytes.length == dataLength) {
                byte[] message = bytes.clone();
                bytes = new byte[0];// reset
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