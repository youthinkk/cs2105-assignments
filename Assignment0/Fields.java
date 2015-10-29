import java.util.Scanner;
import java.util.Hashtable;

public class Fields {
	private static final String ERROR_NULL_POINTER = "Error: No null for field and value";
	
	private static Scanner _scanner = new Scanner(System.in);
	
	public static void main(String[] args) {
		Hashtable<String, String> dictionary = new Hashtable<String, String>();
		
		while (true) {
			String input = readLine();
			String[] parsedInput = input.split(": ");

			try {
				if (isAdd(parsedInput)) {
					dictionary.put(parsedInput[0].toUpperCase(), parsedInput[1]);
				} else if (isSearch(parsedInput)) {
					String key = parsedInput[0].toUpperCase();;
					
					if (key.equals("QUIT")) {
						System.exit(0);
					}
					
					if (dictionary.containsKey(key)) {
						showMessage(dictionary.get(key));
					} else if (!"".equals(key)) {
						showMessage("Unknown field");
					}
				}
			} catch (NullPointerException e) {
				showMessage(ERROR_NULL_POINTER);
			}
		}
	}
	
	private static boolean isSearch(String[] parsedInput) {
		return parsedInput.length == 1;
	}
	
	private static boolean isAdd(String[] parsedInput) {
		return parsedInput.length == 2;
	}
	
	private static String readLine() {
		return _scanner.nextLine();
	}
	
	private static void showMessage(Object message) {
		System.out.println(message);
	}
}