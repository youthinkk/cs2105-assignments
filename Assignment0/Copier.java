import java.io.BufferedInputStream;  
import java.io.BufferedOutputStream;  
import java.io.FileInputStream;  
import java.io.FileNotFoundException;  
import java.io.FileOutputStream;  
import java.io.IOException;  

public class Copier {
	private static final String ERROR_INCORRECT_ARGUMENT = "Incorrect number of arguments";
	private static final String ERROR_FILE_NOT_FOUND = "File not found";
	
	private static final String MSG_SUCCESSFUL = "%1$s successfully copied to %2$s";
	
	public static void main(String[] args) throws IOException {
		if (args.length != 2) {
			showMessage(ERROR_INCORRECT_ARGUMENT);
		} else {
			BufferedInputStream bufferedInput = null;  
			BufferedOutputStream bufferedOutput = null;  
			
			try {
				String src = args[0];
				String dest = args[1];
				
				bufferedInput = new BufferedInputStream(new FileInputStream(src));
				bufferedOutput = new BufferedOutputStream(new FileOutputStream(dest));
				
				int data;
				
				while((data = bufferedInput.read()) != -1) {
					bufferedOutput.write(data);
				}
				
				showMessage(String.format(MSG_SUCCESSFUL, src, dest));
				
			} catch (FileNotFoundException e) {
				showMessage(ERROR_FILE_NOT_FOUND);
			} finally {
				if (bufferedInput != null) {
					bufferedInput.close();
				}
				
				if (bufferedOutput != null) {
					bufferedOutput.close();
				}
			}
		}
	}
	
	private static void showMessage(Object message) {
		System.out.println(message);
	}
}
