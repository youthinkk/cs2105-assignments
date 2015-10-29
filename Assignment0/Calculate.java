import java.lang.Math;

public class Calculate {
	private static final String ERROR_INCORRECT_ARGUMENT = "Incorrect number of arguments";
	private static final String ERROR_INVALID_INTEGER = "No integer for calculation";
	private static final String ERROR_INVALID_OPERATOR = "Invalid operator";
	private static final String ERROR_DIVIDE_BY_ZERO = "Division by zero";
	
	public static void main(String[] args) {
		if (args.length != 3) {
			showMessage(ERROR_INCORRECT_ARGUMENT);
		} else {
			boolean isDivision = false;
			
			try {
				int result = 0;
				int num1 = Integer.parseInt(args[0]);
				int num2 = Integer.parseInt(args[2]);
				String operator = args[1];
				
				switch (operator) {
					case "+":
						result = Math.addExact(num1, num2);
						break;
					case "-":
						result = Math.subtractExact(num1, num2);
						break;
					case "*":
						result = Math.multiplyExact(num1, num2);
						break;
					case "**":
						result = (int)Math.pow(num1, num2);
						break;
					case "/":
						isDivision = true;
						result = Math.floorDiv(num1, num2);
						break;
					default:
						throw new ArithmeticException();
				}
				showMessage(result);
			} catch (NumberFormatException e) {
				showMessage(ERROR_INVALID_INTEGER);
			} catch (ArithmeticException e) {
				if (isDivision) {
					showMessage(ERROR_DIVIDE_BY_ZERO);
				} else {
					showMessage(ERROR_INVALID_OPERATOR);
				}
			}
		}
	}
	
	private static void showMessage(Object message) {
		System.out.println(message);
	}
}
