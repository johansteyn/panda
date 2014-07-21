/*
 *  Copyright (C) 2013  Johan Steyn
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

class Util {
	public static final String PANDA_HOME;
	public static List<String> projectorGenres = new ArrayList<String>();
	private static final Logger logger = Logger.getLogger("panda");
	private static Properties properties;

	static {
		PANDA_HOME = initHomeDir();
		initProperties();
		initLogger();
	}

	private static String initHomeDir() {
		String homeDir = System.getenv("PANDA_HOME");
		if (homeDir == null) {
			homeDir = System.getProperty("user.home") + File.separator + "panda" + File.separator;
		}
		File file = new File(homeDir);
		// Check that it actually exists
		if (!file.exists()) {
			System.out.println("Panda home directory not found: " + homeDir);
			System.exit(1);
		}
		// Get absolute path so that on Windows the drive portion is included
		homeDir = file.getAbsolutePath();
		if (homeDir.charAt(homeDir.length() - 1) != File.separatorChar) {
			homeDir += File.separator;
		}
		return homeDir;
	}

	private static void initProperties() {
//		Properties properties = new Properties();
		properties = new Properties();
		String filename = PANDA_HOME + "panda.properties";
		try {
			FileInputStream fis = new FileInputStream(filename);
			properties.load(fis);
			fis.close();
		} catch (IOException ioe) {
			Util.log(Level.WARNING, "Error loading Panda properties from " + filename + ": " + ioe);
			Util.log(Level.WARNING, "Using default property values.");
			return;
		}
		Enumeration keys = properties.keys();
		while (keys.hasMoreElements()) {
			String key = (String)keys.nextElement();
			String value = System.getProperty(key);
			if (value == null) {
				// Only set property if it has not already been set, eg: on command line using -Dkey=value
				value = (String)properties.get(key);
				System.setProperty(key, value);
			}
		}

		String defaultValue = "Tango,Vals,Milonga";
		String key = "panda.projector.genres";
		String value = System.getProperty(key);
		if (value == null) {
			Util.log(Level.WARNING, "Property " + key + " not defined (Using default value " + defaultValue + ")");
			value = defaultValue;
		}
		StringTokenizer st = new StringTokenizer(value, ",");
		while (st.hasMoreTokens()) {
			projectorGenres.add(st.nextToken());
		}
	}

	private static void initLogger() {
		try {
			Handler handler = new FileHandler(PANDA_HOME + "panda.log", true);
			handler.setFormatter(new SimpleFormatter());
			logger.addHandler(handler);
			// Set default log level to INFO, but then override that with configured value (if any)
			Level level = Level.INFO;
			String logLevel = System.getProperty("panda.log.level");
			if (logLevel != null) {
				if (logLevel.equals("FINE")) {
					level = Level.FINE;
				} else if (logLevel.equals("INFO")) {
					level = Level.INFO;
				} else if (logLevel.equals("WARNING")) {
					level = Level.WARNING;
				} else if (logLevel.equals("SEVERE")) {
					level = Level.SEVERE;
				} else {
					System.out.println("Invalid log level specified: " + logLevel);
					System.exit(1);
				}
			}
			System.out.println("Setting log level to: " + level);
			// Logging output is sent to both the console and the file PANDA_HOME/panda.log
			// A "floor" level can also be specified for both the console and file
			// in the Java jre/lib/logging.properties file:
			//   java.util.logging.ConsoleHandler.level=INFO
			//   panda.level=FINE
			// To reduce console output, set the console log level no lower than INFO
			logger.setLevel(level);
			// Test to see if log level works...
			logger.log(Level.SEVERE, "SEVERE");
			logger.log(Level.WARNING, "WARNING");
			logger.log(Level.INFO, "INFO");
			logger.log(Level.FINE, "FINE");
			// Having already set PANDA_HOME, we now merely log it's value, 
			// along with a warning if the environment variable is not set.
			if (System.getenv("PANDA_HOME") == null) {
				logger.log(Level.WARNING, "Environment variable PANDA_HOME not set.");
			}
			logger.log(Level.INFO, "Using PANDA_HOME: " + PANDA_HOME);
			StringBuilder sb = new StringBuilder("Configured properties:\n");
			Enumeration keys = properties.keys();
			/*
			while (keys.hasMoreElements()) {
				String key = (String)keys.nextElement();
				String value = (String)properties.get(key);
				sb.append("  ");
				sb.append(key);
				sb.append("=");
				sb.append(value);
				sb.append("\n");
			}
			*/
			List<String> list = new ArrayList<String>();
			while (keys.hasMoreElements()) {
				String key = (String)keys.nextElement();
				list.add(key);
			}
			Collections.sort(list);
			for (String key: list) {
				String value = (String)properties.get(key);
				sb.append("  ");
				sb.append(key);
				sb.append("=");
				sb.append(value);
				sb.append("\n");
			}
			logger.log(Level.INFO, sb.toString());
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
/*
	public static String replaceSpecialChars(String string) {
System.out.println("*** Before: " + string);
		string = string.replace("ñ", "\u00F1");
		string = string.replace("Á", "\u00C1");
		string = string.replace("á", "\u00E1");
		string = string.replace("É", "\u00C9");
		string = string.replace("é", "\u00E9");
		string = string.replace("Í", "\u00CD");
		string = string.replace("í", "\u00ED");
		string = string.replace("Ó", "\u00D3");
		string = string.replace("ó", "\u00F3");
		string = string.replace("Ú", "\u00DA");
		string = string.replace("ú", "\u00FA");
System.out.println("*** After: " + string);
		return string;
	}
*/
}

