import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.zip.CRC32;

public class FileReceiver {
	private static final int DATA_SIZE = 1000;
	private static final int HEADER_SIZE = 16;
	private static final int CHECKSUM_SIZE = 8;
	private static final int TIMEOUT = 5;
	
	private static final String SUCCESSFUL_MSG = "SUCCESSFUL";
	private static final String TERMINATE_MSG = "TERMINATE";
	
	private int _lastPacketNumber = -1;
	private int _currentWriteNumber = 0;
	private int _currentAckNumber = 0;
	private int _prevWriteNumber = -1;
	private int _prevAckNumber = -1;
	private int _port;
	
	private SocketAddress _prevAckAddress = null;
	private SocketAddress _lastPacketAddress = null;
	
	private boolean _isFinish = false;
	//private ConcurrentHashMap<Integer, byte[]> _receivedPackets = new ConcurrentHashMap<Integer, byte[]>();
	private ConcurrentSkipListMap<Integer, byte[]> _receivedPackets = new ConcurrentSkipListMap<Integer, byte[]>();
	private ConcurrentSkipListMap<Integer, SocketAddress> _addresses = new ConcurrentSkipListMap<Integer, SocketAddress>();
	
	private CRC32 _crc = new CRC32();
	
	private DatagramSocket _socket;
	
	public FileReceiver(int port) {
		_port = port;
	}
	
	public void receive() throws Exception {
		// Receive package variables
		_socket = new DatagramSocket(_port);

		// Receive data variables
		byte[] data = new byte[DATA_SIZE];
		DatagramPacket packet = new DatagramPacket(data, data.length);
		ByteBuffer buffer = ByteBuffer.wrap(data);
		
		// File variables
		BufferedOutputStream toFile = null;
		
		WriteThread writeThread = new WriteThread();
		writeThread.start();
		
		ACKThread ackThread = new ACKThread();
		ackThread.start();
		
		System.out.println("START");

		while (true) {
			try {
				if (_isFinish) {
					_socket.setSoTimeout(TIMEOUT);
					sendAck(_lastPacketNumber, true, _lastPacketAddress);
				}
				
				packet.setLength(data.length);
				_socket.receive(packet);

				if (packet.getLength() < HEADER_SIZE) {
					System.out.println("Error: Packet is too short!");
					continue;
				}

				buffer.rewind();
				
				long checksum = buffer.getLong();	// Get checksum
				int number = buffer.getInt();		// Get sequence number
				int byteWrite = buffer.getInt();	// Get number of bytes to write
				byte[] content = null;


				// Read the content from data
				if (byteWrite > 0) {
					content = Arrays.copyOfRange(data, HEADER_SIZE, HEADER_SIZE + byteWrite);
				} else {				// Terminal package
					content = Arrays.copyOfRange(data, HEADER_SIZE, data.length);
				}

				if(!isCorrupted(checksum, data)) {
					if (byteWrite == -1) {
						_lastPacketNumber = number;
					} 
					
					// Discard repeated package
					int prevWriteNumber = _currentWriteNumber - 1;
					boolean isWrite = (number < prevWriteNumber);
					if (!_receivedPackets.containsKey(number) && !isWrite) {
						while (!_receivedPackets.containsKey(number)) {
							_receivedPackets.put(number, content);
						}
						
						while (!_addresses.containsKey(number)) {
							_addresses.put(number, packet.getSocketAddress());
						}
					}
					
				}
			} catch (SocketTimeoutException e) {
				reset();
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (toFile != null) {
					toFile.close();
				}
			}
		} 
	}
	
	private void reset() {
		try {
			_socket.setSoTimeout(0);
			
			_isFinish = false;
			_lastPacketNumber = -1;
			_lastPacketAddress = null;
			
			_currentWriteNumber = 0;
			_currentAckNumber = 0;
			
			_prevWriteNumber = -1;
			_prevAckNumber = -1;
			_prevAckAddress = null;
			
			_receivedPackets.clear();
			_addresses.clear();
			
			System.out.println("reset");
		} catch (Exception e) {
			
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
	
	private void sendAck(int number, boolean isTerminated, SocketAddress address) throws Exception {
		String messageStr = SUCCESSFUL_MSG;
		String terminalStr = TERMINATE_MSG;
		byte[] message; 
		
		if (!isTerminated) {
			message = messageStr.getBytes();
		} else {
			message = terminalStr.getBytes();
		}
		
		byte[] reply = new byte[DATA_SIZE];
		ByteBuffer buffer = ByteBuffer.wrap(reply);
		
		buffer.clear();
		buffer.putLong(0);
		buffer.putInt(number);
		buffer.putInt(message.length);
		
		for (int i = 0; i < message.length; i++) {
			reply[HEADER_SIZE + i] = message[i];
		}
		
		_crc.reset();
		_crc.update(reply, CHECKSUM_SIZE, reply.length - CHECKSUM_SIZE);
		
		buffer.rewind();
		buffer.putLong(_crc.getValue());
		
		DatagramPacket ackPacket = new DatagramPacket(reply, reply.length, address);
		_socket.send(ackPacket);
	}
	
	private class ACKThread extends Thread {
		@Override
		public void run() {			
			while (true) {
				try {					
					int tempWriteNumber = _currentWriteNumber - 1;
					
					if (!_isFinish) {
						if ((tempWriteNumber == _prevWriteNumber) && (_prevAckAddress != null)) {
							sendAck(_prevAckNumber, false, _prevAckAddress);
							//System.out.println("**repeated ack number: " + _currentAckNumber);
							Thread.sleep(20);
							continue;
						}

						if (_currentAckNumber != _lastPacketNumber) {
							for (; _currentAckNumber < tempWriteNumber; _currentAckNumber++) {						
								if (_currentAckNumber == (tempWriteNumber - 1)) {
									sendAck(_currentAckNumber, false, _addresses.get(_currentAckNumber));
									_prevWriteNumber = tempWriteNumber;
									_prevAckNumber = _currentAckNumber;
									_prevAckAddress = _addresses.get(_currentAckNumber);
								}
								_addresses.remove(_currentAckNumber);
							}
						}
					}
					
					Thread.sleep(20);
				} catch (Exception e) {
					//e.printStackTrace();
				}
			}
		}
	}
	
	private class WriteThread extends Thread {
		@Override
		public void run() {
			BufferedOutputStream toFile = null;
			
			while (true) {				
				try {
					if (_receivedPackets.containsKey(_currentWriteNumber)) {
						byte[] content = _receivedPackets.get(_currentWriteNumber);
						
						if (_currentWriteNumber == 0) {	// If it is the packet which stores file name
							reset();
							String fileName = new String(content);
							File file = new File(fileName);
							//System.out.println("fileName: " + fileName);

							toFile = new BufferedOutputStream(new FileOutputStream(file));
							_receivedPackets.remove(_currentWriteNumber);
							_currentWriteNumber += 1;
						} else if (_currentWriteNumber != _lastPacketNumber) {
							toFile.write(content);
							_receivedPackets.remove(_currentWriteNumber);
							_currentWriteNumber += 1;
						} else {
							if (toFile != null) {
								toFile.close();
							}
							
							_lastPacketAddress = _addresses.get(_lastPacketNumber);
							
							sendAck(_lastPacketNumber, true, _lastPacketAddress);
							System.out.println("FINISH WRITING");
							
							// Reset
							_currentWriteNumber = 0;
							_currentAckNumber = 0;
							_prevWriteNumber = -1;
							_prevAckNumber = -1;
							_isFinish = true;
							_prevAckAddress = null;
							_receivedPackets.clear();
							_addresses.clear();
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	public static void main(String[] args) {
		// Check missing arguments
		if (args.length != 1) {
			System.err.println("Usage: FileReceiver <port>");
			System.exit(-1);
		}

		// Get the parameters
		int port = Integer.parseInt(args[0]);
		
		FileReceiver receiver = new FileReceiver(port);
		try {
			receiver.receive();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
