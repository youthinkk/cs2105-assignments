import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Arrays;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;

import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SealedObject;

/**
 * Amy is a TCP client who does not know Bryan's public key.
 * 
 * Amy will initiate a TCP connection to Bryan. When it is connected, 
 * Bryan sends Amy his RSA public key, followed by an encrypted signature 
 * signed by Berisign. Alice will then decrypt signature with using 
 * Berisign's public key and compare the MD5 sum with the received 
 * Bryan's public key. If it matches, Amy will generate an AES session 
 * key, encrypt it with Bryan’s public key and send it to Bryan. Finally, 
 * she will receive 10 encrypted messages from Bryan, and decrypt and save 
 * them to a file.
 * 
 * @author YuTing
 *
 */
public class Amy {

	String bryanIP;		// IP address of Bryan
	int bryanPort;		// port Bryan listens to
	Socket connectionSkt;					// socket used to talk to Bryan
	private ObjectOutputStream toBryan;		// to send session key to Bryan
	private ObjectInputStream fromBryan;	// to read encrypted messages from Bryan
	private Crypto crypto;					// object for encryption and decryption
	public static final String MESSAGE_FILE = "msgs.txt";	// file to save received messages.

	public static void main(String[] args) {

		// Check if the number of command line argument is 2
		if (args.length != 2) {
			System.err.println("Usage: java Amy BryanIP BryanPort");
			System.exit(1);
		}

		new Amy(args[0], args[1]);
	}

	/**
	 * Constructor of Amy.
	 * @param ipStr - IP address
	 * @param portStr - port number
	 */
	public Amy(String ipStr, String portStr) {

		this.bryanIP = ipStr;

		try {
			this.bryanPort = Integer.parseInt(portStr);
			this.connectionSkt = new Socket(bryanIP, bryanPort);
		} catch (NumberFormatException e) {
			System.out.println("Error: port number is not integer.");
			System.exit(1);
		} catch (UnknownHostException e) {
			System.out.println("Error: IP address of the host could not be determined.");
			System.exit(1);
		} catch (IOException e) {
			System.out.println("Error: socket cannot be initialised.");
			System.exit(1);
		} 

		try {
			this.toBryan = new ObjectOutputStream(this.connectionSkt.getOutputStream());
			this.fromBryan = new ObjectInputStream(this.connectionSkt.getInputStream());
		} catch (IOException e) {
			System.out.println("Error: input and output stream cannot be initialised.");
		}

		this.crypto = new Crypto();
		
		// Receive public key from Bryan
		receivePublicKey();

		// Send session key to Bryan
		sendSessionKey();

		// Receive encrypted messages from Bryan,
		// decrypt and save them to file
		receiveMessages();
	}
	
	/**
	 * Receive public key from Bryan
	 */
	public void receivePublicKey() {
		
		try {
			PublicKey pubKey = (PublicKey) this.fromBryan.readObject();
			byte[] digest = (byte[]) this.fromBryan.readObject();

			boolean isVerified = crypto.verifyBryanPubKey(pubKey, digest);
			
			if (!isVerified) {
				System.out.println("Error:MD5 signature does not match");
				System.exit(1);
			}
			
		} catch (ClassNotFoundException e) {
			System.out.println("Error: cannot typecast to class PublicKey or byte[].");
			System.exit(1);
		} catch (IOException e) {
			System.out.println("Error: cannot varify bryan's key.");
			System.exit(1);
		}
	}

	/**
	 * Send session key to Bryan.
	 */
	public void sendSessionKey() {
		
		SealedObject object = crypto.getSessionKey();
        try {
			this.toBryan.writeObject(object);
		} catch (IOException e) {
			System.out.println("Error: cannot send to Bryan.");
		}
	}

	/**
	 * Receive messages one by one from Bryan, decrypt and write to file
	 */
	public void receiveMessages() {
		
		try {
    		PrintWriter writer = new PrintWriter(new FileWriter(MESSAGE_FILE));
    		
    		for (int i = 0; i < 10; i++) {
    			SealedObject object = (SealedObject) this.fromBryan.readObject();
    			String msg = crypto.decryptMsg(object);
    			writer.write(msg);
    			writer.write("\n");
    		}
    		writer.close();
    	} catch (ClassNotFoundException e) {
    		System.out.println("Error: cannot typecast to class SealedObject.");
    		System.exit(1);
    	} catch (IOException e) {
    		System.out.println("Error: cannot receive messages from Bryan.");
    		System.exit(1);
    	}
	}

	class Crypto {
		
		private PublicKey berisignPubKey;	// Berisign's public key
		private PublicKey bryanPubKey;		// Bryan's public key
		private SecretKey sessionKey;		// Session key for communication session
		public static final String PUBLIC_KEY_FILE = "berisign.pub";	// File of Berisign's public key

		/**
         * Constructor of Crypto.
         */
		public Crypto() {
			
			// Read Berisign's public key from file
			readBerisignPublicKey();
			
			// Generate session key dynamically
			initSessionKey();
		}

		/**
		 * Read Berisign's public key from file
		 */
		public void readBerisignPublicKey() {
			
			File pubKeyFile = new File(PUBLIC_KEY_FILE);

			if (pubKeyFile.exists() && !pubKeyFile.isDirectory()) {
				try {
					ObjectInputStream inputStream =
							new ObjectInputStream(new FileInputStream(PUBLIC_KEY_FILE));
					this.berisignPubKey = (PublicKey) inputStream.readObject();
					inputStream.close();
				} catch(IOException e) {
					System.out.println("Error: cannot read Berisign's public key from file.");
					System.exit(1);
				} catch(ClassNotFoundException e) {
					System.out.println("Error: cannot typecast to class PublicKey.");
					System.exit(1);
				}
			} else {
				System.out.println("Error: Amy cannot find Berisign's public key.");
				System.exit(1);
			}

			System.out.println("Public key read from file " + PUBLIC_KEY_FILE);
		}

		/**
         * Generate session key.
         */
		public void initSessionKey() {
			
			try {
				KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
				keyGenerator.init(128);
				sessionKey = keyGenerator.generateKey();
			} catch (NoSuchAlgorithmException e) {
				System.out.println("Error: No algorithm entered exists.");
				System.exit(1);
			}
		}
		
		/**
		 * Verify Bryan's public key.
		 * @param pubKey - public key received through TCP
		 * @param digest - signature received through TCP
		 * @return true if it is verified successfully; false otherwise
		 */
		public boolean verifyBryanPubKey(PublicKey pubKey, byte[] digest) {
			
			try {
				String bryanStr = "bryan";
				MessageDigest md5 = MessageDigest.getInstance("MD5");
				md5.update(bryanStr.getBytes(StandardCharsets.US_ASCII));
				md5.update(pubKey.getEncoded());

				Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
				cipher.init(Cipher.DECRYPT_MODE, berisignPubKey);
				cipher.update(digest);

				byte[] bryanDigest = md5.digest();
				byte[] berisignDigest = cipher.doFinal();

				
				if (Arrays.equals(bryanDigest, berisignDigest)) {
					this.bryanPubKey = pubKey;
				} else {
					return false;
				}
			} catch (NoSuchAlgorithmException e) {
				System.out.println("Error: No algorithm entered exists.");
				return false;
			} catch (NoSuchPaddingException e) {
				System.out.println("Error: transformation contains a padding scheme is not available.");
				return false;
			} catch (InvalidKeyException e) {
				System.out.println("Error: the public key is invalid.");
				return false;
			} catch (IllegalBlockSizeException e) {
				System.out.println("Error: the block size is invalid.");
				return false;
			} catch (BadPaddingException e) {
				System.out.println("Error: decrypted data is not bounded by the valid padding bytes.");
				return false;
			} 

			return true;
		}
		
		/**
         * Seal session key with RSA public key in a SealedObject.
         * @return SealedObject of session key
         */
		public SealedObject getSessionKey() {

			SealedObject object = null;

			try {
				Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
				cipher.init(Cipher.ENCRYPT_MODE, bryanPubKey);
				byte[] sessionKeyBytes = sessionKey.getEncoded();
				object = new SealedObject(sessionKeyBytes, cipher);
			} catch (NoSuchAlgorithmException e) {
				System.out.println("Error: No algorithm entered exists.");
				System.exit(1);
			} catch (NoSuchPaddingException e) {
				System.out.println("Error: transformation contains a padding scheme is not available.");
				System.exit(1);
			} catch (InvalidKeyException e) {
				System.out.println("Error: the public key is invalid.");
				System.exit(1);
			} catch (IllegalBlockSizeException e) {
				System.out.println("Error: the block size is invalid.");
				System.exit(1);
			} catch (IOException e) {
				System.out.println("Error: cannot create SealedObject");
				System.exit(1);
			}

			return object;         
		}

		/**
         * Decrypt and extract a message from SealedObject
         * @param encryptedMsgObj - encrypted SealedObject
         * @return message
         */
		public String decryptMsg(SealedObject encryptedMsgObj) {

			String plainText = null;
			
			try {
				Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
				cipher.init(Cipher.DECRYPT_MODE, sessionKey);
				plainText = (String) encryptedMsgObj.getObject(cipher);
			} catch (NoSuchAlgorithmException e ) {
				System.out.println("Error: No algorithm entered exists.");
				System.exit(1);
			} catch (NoSuchPaddingException e) {
				System.out.println("Error: transformation contains a padding scheme is not available.");
				System.exit(1);
			} catch (InvalidKeyException e) {
				System.out.println("Error: the session key is invalid.");
				System.exit(1);
			} catch (ClassNotFoundException e) {
				System.out.println("Error: cannot typecast to byte[].");
				System.exit(1);
			} catch (IllegalBlockSizeException e) {
				System.out.println("Error: the block size is invalid.");
				System.exit(1);
			} catch (BadPaddingException e) {
				System.out.println("Error: decrypted data is not bounded by the valid padding bytes.");
				System.exit(1);
			} catch (IOException e) {
				System.out.println("Error: cannot decrypt message.");
			}

			return plainText;
		}
	}

}
