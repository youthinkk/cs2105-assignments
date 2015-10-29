import java.util.*;
import java.lang.Math;

public class IPAddress {
	private static final String ERROR_NO_LINE_FOUND = "Error: No line is found.";
	private static final String ERROR_INVALID_BINARY = "Error: The given input is invalid.";
	private static final String MSG_ADDRESS = "%1$s.%2$s.%3$s.%4$s";
	
	public static void main(String[] args) {
		Scanner scanner = new Scanner(System.in);
		
		try {
			String binary = scanner.nextLine();
			printDecimal(binary);
			
		} catch (NoSuchElementException e) {
			showMessage(ERROR_NO_LINE_FOUND);
		} finally {
			scanner.close();
		}
	}
	
	private static void printDecimal(String binary)
	{
		int[] decimal = new int[4];
		
		if (binary.length() != 32) {
			showMessage(ERROR_INVALID_BINARY);
		} else {
			int count = -1;

			for (int i = 0; i < binary.length(); i++) {
				int power = 7 - (i % 8);
				int number = binary.charAt(i) - '0';

				if (number != 0 && number != 1) {
					showMessage(ERROR_INVALID_BINARY);
					break;
				}

				if (power == 7) {
					count += 1;
					decimal[count] = 0;
				}

				decimal[count] += number * Math.pow(2, power);
			}
			showMessage(String.format(MSG_ADDRESS, decimal[0], decimal[1], decimal[2], decimal[3]));
		}
	}
	
	private static void showMessage(Object message) {
		System.out.println(message);
	}
}
