/*
 *  Copyright (C) 2013-2016  Johan Steyn
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package panda;

import java.awt.*;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

//import java.util.ArrayList;
//import java.util.Collections;
import java.util.Date;
//import java.util.Enumeration;
//import java.util.List;

import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

class Util {
	private static final Logger logger = Logger.getLogger("panda");

	static {
		initLogger();
	}

	private static void initLogger() {
		try {
			File dir = new File(Config.PANDA_HOME + "bkp");
			if (!dir.exists()) {
				if (!dir.mkdirs()) {
					throw new IOException("Error creating backup directory: " + Config.PANDA_HOME + "bkp");
				}
			}
			DateFormat df = new SimpleDateFormat("yyyyMMdd.HHmmss");
			String timestamp = df.format(new Date());
			File file = new File(Config.PANDA_HOME + "panda.log");
			File backup = new File(Config.PANDA_HOME + "bkp" + File.separator + "panda.log." + timestamp);
			if (backup(file, backup, 24 * 60, 100)) {
				// Backup the log file if it is older than one day or larger than 100 kilobytes
				file.delete();
			}			

			Handler handler = new FileHandler(Config.PANDA_HOME + "panda.log", true);
			handler.setFormatter(new SimpleFormatter());
			logger.addHandler(handler);
			// First set log level to INFO...
			logger.setLevel(Level.INFO);
			// ...so that warnings can be logged during configuration loading...
			Config.load();
			// ...then set log level to configured value
			logger.setLevel(Config.level);
			logger.log(Level.INFO, "Log level set to: " + Config.level);
			// Logging output is sent to both the console and the file PANDA_HOME/panda.log
			// A "floor" level can also be specified for both the console and file
			// in the Java jre/lib/logging.properties file:
			//   java.util.logging.ConsoleHandler.level=INFO
			//   panda.level=FINE
			// To reduce console output, set the console log level no lower than INFO
//			// Test to see if log level works...
//			logger.log(Level.SEVERE, "SEVERE");
//			logger.log(Level.WARNING, "WARNING");
//			logger.log(Level.INFO, "INFO");
//			logger.log(Level.FINE, "FINE");
			// Having already set PANDA_HOME, we now merely log it's value, 
			// along with a warning if the environment variable is not set.
			if (System.getenv("PANDA_HOME") == null) {
				logger.log(Level.WARNING, "Environment variable PANDA_HOME not set.");
			}
			logger.log(Level.INFO, "Using PANDA_HOME: " + Config.PANDA_HOME);

//			StringBuilder sb = new StringBuilder("Configuration:\n");
//			Enumeration keys = Config.properties.keys();
//			List<String> list = new ArrayList<String>();
//			while (keys.hasMoreElements()) {
//				String key = (String)keys.nextElement();
//				list.add(key);
//			}
//			Collections.sort(list);
//			for (String key: list) {
//				String value = (String) Config.properties.get(key);
//				sb.append("  ");
//				sb.append(key);
//				sb.append("=");
//				sb.append(value);
//				sb.append("\n");
//			}
//			logger.log(Level.INFO, sb.toString());

		} catch (IOException ioe) {
			logger.log(Level.SEVERE, "Error initializing logger: " + ioe);
			System.exit(1);
		}
	}

	public static void log(Level level, String message) {
		logger.log(level, message);
	}

	public static void pause(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException ie) {
		}
	}

	public static String minutesSeconds(int seconds) {
		int minutes = seconds / 60;
		seconds = seconds % 60;
		StringBuilder sb = new StringBuilder();
		sb.append(minutes);
		sb.append(":");
		if (seconds < 10) {
			sb.append("0");
		}
		sb.append(seconds);
		return sb.toString();
	}

	public static String decibels(float value) {
		int intValue = (int) value;
        if (intValue == 0) {
            return "Vol";
        }
		StringBuilder sb = new StringBuilder();
		if (intValue < 0) {
			sb.append("-");
		} else if (intValue > 0) {
			sb.append("+");
		}
		int abs = Math.abs(intValue);
		sb.append(abs);
		sb.append("dB");
		return sb.toString();
	}

	public static String balance(int value) {
        if (value == 0) {
            return "Bal";
        }
		int abs = Math.abs(value);
		StringBuilder sb = new StringBuilder();
		sb.append(abs);
		if (value < 0) {
			sb.append("L");
		} else if (value > 0) {
			sb.append("R");
		}
		return sb.toString();
	}

	public static long startTimer() {
		return System.currentTimeMillis();
	}

	public static void stopTimer(long start, String operation) {
		long end = System.currentTimeMillis();
		long millis = end - start;
		Util.log(Level.INFO, operation + " took " + millis + " millseconds");
	}

	public static String replaceSpecialChars(String string) {
//System.out.println("*** Before: " + string);
		string = string.replace("ñ", "n"); // \u00F1
		string = string.replace("Á", "A"); // \u00C1
		string = string.replace("á", "a"); // \u00E1
		string = string.replace("É", "E"); // \u00C9
		string = string.replace("é", "e"); // \u00E9
		string = string.replace("Í", "I"); // \u00CD
		string = string.replace("í", "i"); // \u00ED
		string = string.replace("Ó", "O"); // \u00D3
		string = string.replace("ó", "o"); // \u00F3
		string = string.replace("Ú", "U"); // \u00DA
		string = string.replace("ú", "u"); // \u00FA
//System.out.println("*** After: " + string);
		return string;
	}

	// Make component's foreground (ie. text) either black or white, depending on how light or dark the background is
	public static void setTextColor(Component component) {
		Color background = component.getBackground();
		int r = background.getRed();
		int g = background.getGreen();
		int b = background.getBlue();
		if (r + g + b > 400) {
			component.setForeground(Color.BLACK);
		} else {
			component.setForeground(Color.WHITE);
		}
	}

	// The Java Color class has "darker" and "lighter" methods, but they result in progressively greyer colors.
	// This method will determine which component is the dominant one (if any) and increase it while reducing the other two.
	public static Color intensify(Color color) {
		int r = color.getRed();
		int g = color.getGreen();
		int b = color.getBlue();
		int a = color.getAlpha();
		if (r == g && g == b) {
			return color;
		}
		int amount = 50;
		if (r > g && r > b) {
			// RED
			r = increase(r, amount);
			g = decrease(g, amount);
			b = decrease(b, amount);
		} else if (g > r && g > b) {
			// GREEN
			r = decrease(r, amount);
			g = increase(g, amount);
			b = decrease(b, amount);
		} else if (b > r && b > g) {
			// BLUE
			r = decrease(r, amount);
			g = decrease(g, amount);
			b = increase(b, amount);
		} else if (r == g && r > b) {
			// YELLOW
			r = increase(r, amount);
			g = increase(g, amount);
			b = decrease(b, amount);
		} else if (r == b && r > g) {
			// MAGENTA
			r = increase(r, amount);
			g = decrease(g, amount);
			b = increase(b, amount);
		} else if (g == b && g > r) {
			// CYAN
			r = decrease(r, amount);
			g = increase(g, amount);
			b = increase(b, amount);
		}
		return new Color(r, g, b, a);
	}

	private Color merge(Color color1, Color color2) {
		int r1 = color1.getRed();
		int g1 = color1.getGreen();
		int b1 = color1.getBlue();
		int a1 = color1.getAlpha();
		int r2 = color2.getRed();
		int g2 = color2.getGreen();
		int b2 = color2.getBlue();
		int a2 = color2.getAlpha();
		return new Color((r1 + r2) / 2, (g1 + g2) / 2, (b1 + b2) / 2, (a1 + a2) / 2);
	}

	// Increase by specified amount (of 255), up to maximum of 255
	private static int increase(int i, int amount) {
		i = i + amount;
		if (i > 255) {
			i = 255;
		}
		return i;
	}

	// Decrease by specified amount, down to minimum of 0
	private static int decrease(int i, int amount) {
		i = i - amount;
		if (i < 0) {
			i = 0;
		}
		return i;
	}

	public static long dump(InputStream is, OutputStream os) throws IOException {
		long size = 0;
		byte[] buffer = new byte[100000];
		int count = 0;
		while (true) {
			count = is.read(buffer);
			if (count < 0) {
				break;
			}
			os.write(buffer, 0, count);
			os.flush();  // Found this to be necessary to avoid empty files...
			size += count;
		}
		return size;
	}

	public static void copy(File src, File dst) throws IOException {
		BufferedInputStream bis = null;
		BufferedOutputStream bos = null;
		try {
			bis = new BufferedInputStream(new FileInputStream(src));
			bos = new BufferedOutputStream(new FileOutputStream(dst));
			dump(bis, bos);
		} finally {
			if (bis != null) bis.close();
			if (bos != null) bos.close();
		}
	}

	public static boolean backup(File file, File backup, int minutes) throws IOException {
		return backup(file, backup, minutes, -1);
	}

	// Copies a file to the specified backup if it is either:
	// - Older than the specified number of minutes, or
	// - Larger than the specified kilobytes
	// Return true if it was backed up (so we can decide to delete it, in the case of log files)
	public static boolean backup(File file, File backup, int minutes, int kilobytes) throws IOException {
		if (!file.exists()) {
			return false;
		}
		long millis = System.currentTimeMillis();
		long modified = file.lastModified();
		if ((millis - modified) > (minutes * 60 * 1000) || (kilobytes > 0 && file.length() >= kilobytes * Math.pow(2, 10))) {
			copy(file, backup);
			// "Touch" the file so that its timestamp doesn't trigger another backup
			file.setLastModified(millis);
			return true;
		}
		return false;
	}
}

