import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.CRC32;


public class Checksum {
	private static final String ERROR_INCORRECT_ARGUMENT = "Incorrect number of arguments";
	
	public static void main(String[] args) throws IOException {
		if (args.length != 1) {
			showMessage(ERROR_INCORRECT_ARGUMENT);
		} else {
			CRC32 crc32 = new CRC32();
			byte[] bytes = Files.readAllBytes(Paths.get(args[0]));

			crc32.update(bytes);
			showMessage(crc32.getValue());
		}
	}
	
	private static void showMessage(Object message) {
		System.out.println(message);
	}
}
