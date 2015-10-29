import java.io.BufferedInputStream;
//import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
//import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.zip.CRC32;

public class FileSender {
    private static final int DATA_SIZE = 1000;
    private static final int CHECKSUM_SIZE = 8;
    private static final int HEADER_SIZE = 16;
    private static final int WINDOW_SIZE = 50;
    private static final int WINDOW_BUFFER_SIZE = WINDOW_SIZE + 20;
    private static final String TERMINATE_MSG = "TERMINATE";
    
    private String _hostname;
    private String _filePath;
    private String _fileName;
    private int _port;
    
    private CRC32 _crc = new CRC32();
    private boolean _stopThread = false;
    
    private int _currentAckNumber = -1;
    private int _prevAckNumber = -1;
    
    private ConcurrentSkipListMap<Integer, byte[]> _sentPackets = new ConcurrentSkipListMap<Integer, byte[]>();
    
    DatagramSocket socket;
    
    public FileSender(String hostname, int port, String filePath, String fileName) {
        _hostname = hostname;
        _port = port;
        _filePath = filePath;
        _fileName = fileName;
    }
    
    private boolean send() throws Exception {
        // Sent package variables
        InetSocketAddress address = new InetSocketAddress(_hostname, _port);
        socket = new DatagramSocket();

        // Thread variables
        ACKThread ackThread = new ACKThread();
        ReadThread readThread = new ReadThread();
        
        // Start receiving acknowledgement package
        ackThread.start();
        readThread.start();
        
        Thread.sleep(10);
        
        try {
            while (!_stopThread) {
                if (_prevAckNumber != _currentAckNumber) {
                	for (int i = _prevAckNumber; i <= _currentAckNumber; i++) {
                		_sentPackets.remove(i);
                	}
                	_prevAckNumber = _currentAckNumber;
                }
                
                NavigableSet<Integer> keySet = _sentPackets.keySet();
                List<Integer> sentNumbers = new ArrayList<Integer>(keySet);
                
                for (int i = 0; i < sentNumbers.size(); i++) {
                    byte[] data = _sentPackets.get(sentNumbers.get(i));
                    
                    if (data != null) {
                        DatagramPacket packet = new DatagramPacket(data, data.length, address);
                        socket.send(packet);
                    }
                }
                Thread.sleep(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            ackThread.join();
            socket.close();
        }
        
        return true;
    }
    
    private class ACKThread extends Thread {
        @Override
        public void run() {
            byte[] data = new byte[DATA_SIZE];
            DatagramPacket packet = new DatagramPacket(data, data.length);
            ByteBuffer buffer = ByteBuffer.wrap(data);
            
            while (!_stopThread) {
                try {
                    socket.receive(packet);
                    
                    if (packet.getLength() < HEADER_SIZE) {
                        continue;
                    }
                    
                    buffer.rewind();
                    
                    long checksum = buffer.getLong();	// Get checksum
                    int number = buffer.getInt();		// Get sequence number
                    int byteRead = buffer.getInt();		// Get number of bytes to write
                    byte[] content = null;
                    
                    // Read the content from data
                    if (byteRead > 0) {
                        content = Arrays.copyOfRange(data, HEADER_SIZE, HEADER_SIZE + byteRead);
                    }
                    
                    if (!isCorrupted(checksum, data)) {
                        String message = new String(content);
                        
                        if (message.equals(TERMINATE_MSG)) {
                            _stopThread = true;
                            //System.out.println("TERMINATE");
                        } 
                        
                        _currentAckNumber = Math.max(_currentAckNumber, number);
                    }
                } catch (SocketTimeoutException e) {
                    //e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        
        private boolean isCorrupted(long checksum, byte[] data) {
            _crc.reset();
            _crc.update(data, CHECKSUM_SIZE, data.length - CHECKSUM_SIZE);
            
            if (checksum == _crc.getValue()) {
                return false;
            }
            
            return true;
        }
    }
    
    private class ReadThread extends Thread {
        @Override
        public void run() {
            byte[] data = new byte[DATA_SIZE];
            ByteBuffer buffer = ByteBuffer.wrap(data);
            
            // File variables
            File file = new File(_filePath);
            BufferedInputStream fromFile = null;
            
            // Helper variables
            int count = 0;
            int byteRead = 0;
            
            try {
                fromFile = new BufferedInputStream(new FileInputStream(file));
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            while (byteRead != -1) {
                try {
                	while ((byteRead != -1) && (_sentPackets.size() < WINDOW_BUFFER_SIZE)) {
                        //System.out.println("START READ THREAD");
                        
                    	buffer.clear();
                    	
                        if (count == 0) {
                            // Save file name into data
                            byte[] fileName = _fileName.getBytes();
                            byteRead = fileName.length;
                            
                            for (int i = 0; i < fileName.length; i++) {
                                data[HEADER_SIZE + i] = fileName[i];
                            }
                        } else {
                            // Read from file and save to data
                            byteRead = fromFile.read(data, HEADER_SIZE, data.length - HEADER_SIZE);
                        }
                        
                        buffer.rewind();
                        buffer.putLong(0);
                        buffer.putInt(count);
                        buffer.putInt(byteRead);
                        
                        // Get checksum
                        _crc.reset();
                        _crc.update(data, CHECKSUM_SIZE, data.length - CHECKSUM_SIZE);
                        long checksum = _crc.getValue();
                        
                        // Update checksum in the buffer
                        buffer.rewind();
                        buffer.putLong(checksum);
                        
                        
                        // Put data in to hashmap
                        byte[] putData = data.clone();
                        _sentPackets.put(count, putData);
                        count += 1;
                    }
                    
                    if (byteRead == -1) {
                        if (fromFile != null) {
                            fromFile.close();
                        }
                        //System.out.println("FINISH READING THREAD");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    public static void main(String[] args) {
        
        // Check missing arguments
        if (args.length != 4) {
            System.err.println("Usage: FileSender <host name> <port> <source file> <dest file name>");
            System.exit(-1);
        }
        
        // Get the parameters
        int port = Integer.parseInt(args[1]);
        String hostname = args[0];
        String filePath = args[2];
        String fileName = args[3];
        
        FileSender sender = new FileSender(hostname, port, filePath, fileName);
        try {
            long startTime = System.currentTimeMillis();
            boolean isSuccessful = sender.send();
            long endTime = System.currentTimeMillis();
            double timeTaken = (endTime - startTime) / 1000.0;
            
            if (isSuccessful) {
                System.out.println("TIME TAKEN: " + timeTaken + " s");
                System.exit(-1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
