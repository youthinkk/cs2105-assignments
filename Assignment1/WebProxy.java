import java.net.*;
import java.util.HashMap;
import java.io.*;

public class WebProxy {
	private static final int SERVER_PORT = 80;
	private static final int BUFFER_SIZE = 40960;
	
	/** Port for the proxy */
	private static int port;
	
	/** Socket for client connections */
	private static ServerSocket serverSocket;

	/** Cache **/
	private static HashMap<String, String> cache = new HashMap<String, String>();
	
	public static void main(String args[]) throws IOException {	
		/** Read port number as command-line argument **/
		port = Integer.parseInt(args[0]);
		
		/** Create a server socket, bind it to a port and start listening **/
		serverSocket = new ServerSocket(port);
	
		runProxy();
	}
	
	private static void runProxy() throws IOException{
		Socket client = null;
		
		/** Main loop. Listen for incoming connections **/
		while (true)
		{
			String URI = null;	
			int length = 0;
			
			byte[] request = new byte[BUFFER_SIZE];
			
			try {
				client = serverSocket.accept();
				System.out.println("Received a connection from: " + client);

				/** Read client's HTTP request **/
				InputStream fromClient = client.getInputStream();
				length = fromClient.read(request);
				
				String firstLine = new String(request);
				String[] tmp = firstLine.split(" ");
				
				URI = tmp[1];
			} catch (IOException e) {
				System.out.println("Error reading request from client: " + e);
				/* Definitely cannot continue, so skip to next
				 * iteration of while loop. */
				continue;
			}
			
			//System.out.println("request: \r\n" + new String(request));
			
			/** Check cache if file exists **/
			URI uri = java.net.URI.create(URI);
			int port = uri.getPort();
			String hostname = uri.getHost();
			String filename = (hostname + uri.getPath()).replaceAll("/", "%");
			File file;
			boolean cacheExist = false;
			
			if (cache.containsKey(URI)) {
				file = new File(cache.get(URI));
				cacheExist = file.exists();
			}
			
			
			if (cacheExist) {
				/** Read the file **/
				//System.out.println("CACHING");
				file = new File(cache.get(URI));
				OutputStream toClient = client.getOutputStream();
				FileInputStream fromFile = new FileInputStream(file);
				
				byte[] buffer = new byte[BUFFER_SIZE];
				length = fromFile.read(buffer);
				
				/** Generate appropriate respond headers and send the file contents **/
				while (length != -1) {
					toClient.write(buffer, 0, length);
					length = fromFile.read(buffer);
				}
				
				fromFile.close();
				toClient.close();
				
			} else {
				try {
					if (port == -1) {
						port = SERVER_PORT;
					}
					
					/** Connect to server and relay client's request **/
					Socket server = new Socket(hostname, port);
					OutputStream toServer = server.getOutputStream();
					toServer.write(request, 0, length);
					
					/** Get response from server, send it to client and cache it**/
					file = new File(filename);
					InputStream fromServer = server.getInputStream();
					OutputStream toClient = client.getOutputStream();
					FileOutputStream toFile = new FileOutputStream(file);
					
					byte[] buffer = new byte[BUFFER_SIZE];
					length = fromServer.read(buffer);
					
					while (length != -1) {
						toFile.write(buffer, 0, length);
						toClient.write(buffer, 0, length);
						
						toFile.flush();
						toClient.flush();
						length = fromServer.read(buffer);
					}
					
					cache.put(URI, filename);
					
					fromServer.close();
					toClient.close();
					toFile.close();
					
					/** Close sockets **/
					client.close();
					server.close();
					
				} catch (UnknownHostException e) {
					OutputStream toClient = client.getOutputStream();
					String error = "HTTP/1.0 502 Bad Gateway\n";
					toClient.write(error.getBytes());
					
					toClient.close();
				} catch (IOException e) {
					OutputStream toClient = client.getOutputStream();
					String error = "HTTP/1.0 502 Bad Gateway\n";
					toClient.write(error.getBytes());
					
					toClient.close();
				}
			}
		}
	}
}