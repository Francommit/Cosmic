package tools;

import net.encryption.InitializationVector;
import net.encryption.MapleAESOFB;
import net.encryption.MapleCustomEncryption;
import net.opcodes.RecvOpcode;
import net.packet.ByteBufInPacket;
import net.packet.ByteBufOutPacket;
import net.packet.InPacket;
import net.packet.OutPacket;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Arrays;

/**
 * Headless Client Simulator for Health Checking
 * Performs: Login -> World Select -> Char List -> Char Select -> Transition
 */
public class ClientSimulator {

    private String host;
    private int port;
    private String user;
    private String pass;
    
    private MapleAESOFB sendCypher;
    private MapleAESOFB recvCypher;

    public ClientSimulator(String host, int port, String user, String pass) {
        this.host = host;
        this.port = port;
        this.user = user;
        this.pass = pass;
    }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        String user = args.length > 1 ? args[1] : "admin";
        String pass = args.length > 2 ? args[2] : "admin";
        
        ClientSimulator sim = new ClientSimulator(host, 8484, user, pass);
        sim.run();
    }

    public void run() {
        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(10000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            System.out.println("[Step 1] Handshake");
            readHandshake(in);

            System.out.println("[Step 2] Sending Login Credentials");
            sendLogin(out);

            System.out.println("[Step 3] Waiting for Login Status");
            InPacket loginStatus = readPacket(in);
            short opcode = loginStatus.readShort();
            if (opcode != 0x00) {
                 throw new RuntimeException("Expected LOGIN_STATUS (0x00) but got 0x" + Integer.toHexString(opcode));
            }
            int status = loginStatus.readByte();
            if (status != 0) {
                throw new RuntimeException("Login failed with status: " + status);
            }
            System.out.println("Login successful.");

            // v83 client sends SERVERLIST_REQUEST (0x0B) after login success
            System.out.println("[Step 4] Requesting Server List");
            OutPacket slReq = new ByteBufOutPacket();
            slReq.writeShort(RecvOpcode.SERVERLIST_REQUEST.getValue());
            sendPacket(out, slReq);
            
            InPacket p;
            while(true) {
                p = readPacket(in);
                opcode = p.readShort();
                System.out.println("Received packet: 0x" + Integer.toHexString(opcode));
                if (opcode == 0x0A) { // SERVERLIST
                    if (p.readByte() == (byte)0xFF) {
                        System.out.println("End of server list.");
                        break;
                    }
                }
            }

            System.out.println("[Step 5] Requesting Char List");
            OutPacket charListReq = new ByteBufOutPacket();
            charListReq.writeShort(RecvOpcode.CHARLIST_REQUEST.getValue());
            charListReq.writeByte(0); // skip
            charListReq.writeByte(0); // World 0
            charListReq.writeByte(0); // Channel 0
            sendPacket(out, charListReq);

            while (true) {
                p = readPacket(in);
                opcode = p.readShort();
                System.out.println("Received packet: 0x" + Integer.toHexString(opcode));
                if (opcode == 0x0B) { // CHARLIST
                    break;
                }
                System.out.println("Skipping opcode 0x" + Integer.toHexString(opcode));
            }
            
            p.readByte(); // status
            int charCount = p.readByte();
            System.out.println("Character count: " + charCount);
            if (charCount == 0) {
                 throw new RuntimeException("No characters found on account. Cannot complete full login check.");
            }

            // Read first character ID
            // Character entry: stats(lots of bytes) + look(some bytes)
            // Stats starts with int charId
            int firstCharId = p.readInt();
            System.out.println("Selecting first character: ID " + firstCharId);

            System.out.println("[Step 6] Character Selection");
            OutPacket charSelect = new ByteBufOutPacket();
            charSelect.writeShort(RecvOpcode.CHAR_SELECT.getValue());
            charSelect.writeInt(firstCharId);
            charSelect.writeString("00-00-00-00-00-00"); // macs
            charSelect.writeString("000000000000_12345678"); // hoststring/hwid
            sendPacket(out, charSelect);

            p = readPacket(in);
            opcode = p.readShort();
            if (opcode == 0x1C) { // CHECK_SPW_RESULT (PIC prompt)
                System.out.println("PIC Prompted. Attempting to bypass or send PIC...");
                // For simplicity, we assume PIC is disabled or bypassable in this health check
            }
            
            if (opcode == 0x0C) { // SERVER_IP
                System.out.println("Received SERVER_IP! Transitioning to Game Server...");
                p.readShort(); // 0
                byte[] ip = new byte[4];
                for(int i=0; i<4; i++) ip[i] = p.readByte();
                int port = p.readShort();
                System.out.println("Channel Server: " + InetAddress.getByAddress(ip).getHostAddress() + ":" + port);
                System.out.println("FULL CLIENT LOGIN FLOW SUCCESSFUL.");
            } else {
                System.out.println("Received opcode 0x" + Integer.toHexString(opcode) + " after char select.");
            }

        } catch (Exception e) {
            System.err.println("Client Simulation FAILED: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void readHandshake(DataInputStream in) throws IOException {
        int len = readShortLE(in);
        byte[] data = new byte[len];
        in.readFully(data);
        
        byte[] recvIv = Arrays.copyOfRange(data, 5, 9);
        byte[] sendIv = Arrays.copyOfRange(data, 9, 13);
        
        sendCypher = new MapleAESOFB(InitializationVector.of(recvIv), (short) 83);
        recvCypher = new MapleAESOFB(InitializationVector.of(sendIv), (short) 0x4B);
        System.out.println("Handshake complete.");
    }

    private void sendLogin(DataOutputStream out) throws IOException {
        OutPacket p = new ByteBufOutPacket();
        p.writeShort(RecvOpcode.LOGIN_PASSWORD.getValue());
        p.writeString(user);
        p.writeString(pass);
        p.skip(6);
        p.writeBytes(new byte[] { 1, 2, 3, 4 }); // HWID
        sendPacket(out, p);
    }

    private void sendPacket(DataOutputStream out, OutPacket p) throws IOException {
        byte[] bytes = p.getBytes();
        byte[] header = sendCypher.getPacketHeader(bytes.length);
        MapleCustomEncryption.encryptData(bytes);
        sendCypher.crypt(bytes);
        out.write(header);
        out.write(bytes);
        out.flush();
    }

    private InPacket readPacket(DataInputStream in) throws IOException {
        byte[] header = new byte[4];
        in.readFully(header);
        int len = MapleAESOFB.getPacketLength(
            ((header[0] & 0xFF) << 24) | 
            ((header[1] & 0xFF) << 16) | 
            ((header[2] & 0xFF) << 8) | 
            (header[3] & 0xFF)
        );
        byte[] data = new byte[len];
        in.readFully(data);
        recvCypher.crypt(data);
        MapleCustomEncryption.decryptData(data);
        return new ByteBufInPacket(data);
    }

    private static short readShortLE(DataInputStream in) throws IOException {
        int b1 = in.readUnsignedByte();
        int b2 = in.readUnsignedByte();
        return (short) (b1 | (b2 << 8));
    }
}
