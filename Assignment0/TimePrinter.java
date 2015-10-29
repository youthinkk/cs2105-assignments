import java.util.Timer;
import java.util.TimerTask;
import java.util.Scanner;

public class TimePrinter extends TimerTask{
	private static final String ERROR_INCORRECT_ARGUMENT = "Error: Incorrect number of arguments";
	private static final String ERROR_INVALID_INTEGER = "Error: No start time or interval is given";
	private static final String ERROR_INVALID_TIME = "Error: Invalid start time or interval";
	
	private static String _string = null;
	
	public static void main(String[] args) {
		if (args.length != 3) {
			showMessage(ERROR_INCORRECT_ARGUMENT);
		} else {
			try {
				_string = args[0];
				int startTime = Integer.parseInt(args[1]);
				int interval = Integer.parseInt(args[2]);
				
				Scanner scanner = new Scanner(System.in);
				Timer timer = new Timer();
				
				timer.schedule(new TimePrinter(), startTime*1000, interval*1000);
				
				while(true) {
					String cmd = scanner.next();
					if (cmd.equalsIgnoreCase("q")) {
						timer.cancel();
						scanner.close();
						System.exit(0);
					}
				}
				
			} catch (NumberFormatException e) {
				showMessage(ERROR_INVALID_INTEGER);
			} catch (IllegalArgumentException e) {
				showMessage(ERROR_INVALID_TIME);
			}
		}
	}
	
	public void run() {
        showMessage(_string);
    }
	
	private static void showMessage(Object message) {
		System.out.println(message);
	}
}
