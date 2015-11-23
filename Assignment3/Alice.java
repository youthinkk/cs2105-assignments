import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.PublicKey;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SealedObject;

/**
 * Alice is a TCP client who knows Bob's public key.
 * 
 * Alice will initiate a TCP connection to Bob. When it is connected, 
 * generate an AES session key, encrypt it with Bob’s public key and 
 * send it to Bob. She will then receive 10 encrypted messages from 
 * Bob, and decrypt and save them to a file.
 * 
 * @author Yu Ting
 *
 */
class Alice {
    String bobIP;	// IP address of Bob
    int bobPort;	// port Bob listens to
    Socket connectionSkt;  				// socket used to talk to Bob
    private ObjectOutputStream toBob;	// to send session key to Bob
    private ObjectInputStream fromBob;	// to read encrypted messages from Bob
    private Crypto crypto;				// object for encryption and decryption
    public static final String MESSAGE_FILE = "msgs.txt";	// file to save received messages.
    
    public static void main(String[] args) {
        
        // Check if the number of command line argument is 2
        if (args.length != 2) {
            System.err.println("Usage: java Alice BobIP BobPort");
            System.exit(1);
        }
        
        new Alice(args[0], args[1]);
    }
    
    /**
     * Constructor of Alice.
     * @param ipStr - IP address
     * @param portStr - port number
     */
    public Alice(String ipStr, String portStr) {
    	
        this.bobIP = ipStr;
        
        try {
        	this.bobPort = Integer.parseInt(portStr);
        	this.connectionSkt = new Socket(bobIP, bobPort);
        } catch (NumberFormatException e) {
        	System.out.println("Error: port number is not integer");
        	System.exit(1);
        } catch (UnknownHostException e) {
        	System.out.println("Error: IP address of the host could not be determined.");
        	System.exit(1);
        } catch (IOException e) {
			System.out.println("Error: socket cannot be initialised.");
			System.exit(1);
		} 
        
        try {
        	this.toBob = new ObjectOutputStream(this.connectionSkt.getOutputStream());
        	this.fromBob = new ObjectInputStream(this.connectionSkt.getInputStream());
        } catch (IOException e) {
        	System.out.println("Error: input and output stream cannot be initialised.");
        }
    	
        this.crypto = new Crypto();
        
        // Send session key to Bob
        sendSessionKey();
        
        // Receive encrypted messages from Bob,
        // decrypt and save them to file
        receiveMessages();
    }

    /**
     * Send session key to Bob.
     */
    public void sendSessionKey() {
    	
        SealedObject object = crypto.getSessionKey();
        try {
			this.toBob.writeObject(object);
		} catch (IOException e) {
			System.out.println("Error: cannot send to Bob.");
		}
    }
    
    /**
     * Receive messages one by one from Bob, decrypt and write to file.
     */
    public void receiveMessages() {
    	
    	try {
    		PrintWriter writer = new PrintWriter(new FileWriter(MESSAGE_FILE));
    		
    		for (int i = 0; i < 10; i++) {
    			SealedObject object = (SealedObject) this.fromBob.readObject();
    			String msg = crypto.decryptMsg(object);
    			writer.write(msg);
    			writer.write("\n");
    		}
    		writer.close();
    	} catch (ClassNotFoundException e) {
    		System.out.println("Error: cannot typecast to class SealedObject.");
    		System.exit(1);
    	} catch (IOException e) {
    		System.out.println("Error: cannot receive messages from Bob.");
    		System.exit(1);
    	}
    }
    
    class Crypto {
    	
        private PublicKey pubKey;		// Bob's public key
        private SecretKey sessionKey;	// Session key for communication session
        public static final String PUBLIC_KEY_FILE = "bob.pub";	// File of Bob's public key
        
        /**
         * Constructor of Crypto.
         */
        public Crypto() {
        	
            // Read Bob's public key from file
            readPublicKey();
            
            // Generate session key dynamically
            initSessionKey();
        }
        
        /**
         * Read Bob's public key from file.
         */
        public void readPublicKey() {
        	
        	File pubKeyFile = new File(PUBLIC_KEY_FILE);
        	
        	if (pubKeyFile.exists() && !pubKeyFile.isDirectory()) {
        		try {
        			ObjectInputStream inputStream =
        					new ObjectInputStream(new FileInputStream(PUBLIC_KEY_FILE));
        			this.pubKey = (PublicKey) inputStream.readObject();
        			inputStream.close();
        		} catch(IOException e) {
        			System.out.println("Error: cannot read Bob's public key from file.");
                    System.exit(1);
        		} catch(ClassNotFoundException e) {
        			System.out.println("Error: cannot typecast to class PublicKey.");
                    System.exit(1);
        		}
        	} else {
        		System.out.println("Error: Alice cannot find Bob's public key.");
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
         * Seal session key with RSA public key in a SealedObject.
         * @return SealedObject of session key
         */
        public SealedObject getSessionKey() {
        	
        	SealedObject object = null;

        	try {
        		Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        		cipher.init(Cipher.ENCRYPT_MODE, pubKey);
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
				System.out.println("Error: cannot decrypt message");
				System.exit(1);
			}
            
            return plainText;
        }
    }
}