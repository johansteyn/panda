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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
//import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;

public class Config {
	public static final String PANDA_HOME;
	public static Properties properties;

	// Individual property fields
	public static Level level = Level.INFO;
	public static String tracks = "tracks";  // Absolute path, ending with a file separator
	public static boolean isNative = false;
	public static boolean isFullscreen = false;
	public static int layout = 1;
	public static int bands = 10;
	public static int wait = 0;
	public static Map<String,Color> genreColors = new HashMap<String,Color>();
	public static Color currentTrackColor = Color.RED;
	public static Color nextTrackColor = Color.GREEN;
	public static Color nextCortinaColor = Color.YELLOW;
	public static Color nextTandaColor = Color.BLUE;
	public static List<String> projectorGenres = new ArrayList<String>();
	public static int projectorWidth;
	public static int projectorHeight;
	public static String projectorHeader = "PANDA";
	public static String projectorImage = "panda-logo.png";
	public static String projectorBody = "Johan Steyn";
	public static String projectorTail = "\u00a9 2017";
	public static String projectorFooter = "DJ  Johan Steyn";

	static {
		PANDA_HOME = initHomeDir();
//		load();
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

	public static void load() {
		Util.log(Level.INFO, "Loading configuration...");
		// Load from config file into a properties instance
		properties = new Properties();
		String filename = PANDA_HOME + "panda.config";
		try {
			FileInputStream fis = new FileInputStream(filename);
			properties.load(fis);
			fis.close();
		} catch (IOException ioe) {
			Util.log(Level.WARNING, "Error loading Panda properties from " + filename + ": " + ioe);
			Util.log(Level.WARNING, "Using default property values.");
			return;
		}

		// Log properties (sorted in alphabetical order)
		StringBuilder sb = new StringBuilder("Configuration:\n");
		List<String> list = new ArrayList<String>();
		Enumeration keys = properties.keys();
		while (keys.hasMoreElements()) {
			String key = (String)keys.nextElement();
			list.add(key);
// TODO: Remove once configuration is fully implemented for "column" and "projector" properties...
String value = System.getProperty(key);
if (value == null) {
	// Only set property if it has not already been set, eg: on command line using -Dkey=value
	value = (String)properties.get(key);
	System.setProperty(key, value);
}
		}
		Collections.sort(list);
		for (String key: list) {
			String value = (String) Config.properties.get(key);
			sb.append("  ");
			sb.append(key);
			sb.append("=");
			sb.append(value);
			sb.append("\n");
		}
		Util.log(Level.INFO, sb.toString());

		// Set individual config fields
		level = getLevelProperty("panda.log.level", Level.INFO);
		tracks = getStringProperty("panda.tracks", null, tracks);
		isNative = getBooleanProperty("panda.gui.native", isNative);
		isFullscreen = getBooleanProperty("panda.gui.fullscreen", isFullscreen);
		layout = getIntProperty("panda.gui.layout", new int[] {1, 2, 3, 4}, 1);
		bands = getIntProperty("panda.equalizer.bands", new int[] {10, 15, 25, 31}, 10);
		wait = getIntProperty("panda.wait", new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, 0);

		currentTrackColor = getColorProperty("panda.colour.currentTrack", currentTrackColor);
		nextTrackColor = getColorProperty("panda.colour.nextTrack", nextTrackColor);
		nextCortinaColor = getColorProperty("panda.colour.nextCortina", nextCortinaColor);
		nextTandaColor = getColorProperty("panda.colour.nextTanda", nextTandaColor);
		Properties properties = System.getProperties();
		Set<String> set = properties.stringPropertyNames();
		Iterator iterator = set.iterator();
		while (iterator.hasNext()) {
			String key = (String) iterator.next();
			String s = "panda.colour.track.genre.";
			if (key.startsWith(s)) {
				String genre = key.substring(s.length());
				Color color = getColorProperty(key, Color.WHITE);
				genreColors.put(genre, color);
			}
		}

		// Projector
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		projectorWidth = getIntProperty("panda.projector.width", 640, 2560, screenSize.width / 2);
		projectorHeight = getIntProperty("panda.projector.height", 480, 1440, screenSize.height / 2);
		projectorHeader = getStringProperty("panda.projector.header", null, projectorHeader);
		projectorImage = getStringProperty("panda.projector.image", null, projectorImage);
		projectorBody = getStringProperty("panda.projector.body", null, projectorBody);
		projectorTail = getStringProperty("panda.projector.tail", null, projectorTail);
		projectorFooter = getStringProperty("panda.projector.footer", null, projectorFooter);
// TODO...
		String defaultValue = "Tango,Vals,Milonga";
		String key = "panda.projector.genres";
//		String value = System.getProperty(key);
		String value = properties.getProperty(key);
		if (value == null) {
			Util.log(Level.WARNING, "Property " + key + " not defined (Using default value " + defaultValue + ")");
			value = defaultValue;
		}
		StringTokenizer st = new StringTokenizer(value, ",");
		while (st.hasMoreTokens()) {
			projectorGenres.add(st.nextToken());
		}

		// TODO: All other properties: panda.column.* and pand.projector.map.*...
	}

	private static boolean getBooleanProperty(String key, boolean defaultValue) {
		String value = properties.getProperty(key);
		if (value == null) {
			Util.log(Level.WARNING, "Property " + key + " not configured (using default value: " + defaultValue + ")");
			return defaultValue;
		}
		if (value.equals("true")) {
			return true;
		} else if (value.equals("false")) {
			return false;
		} else {
			Util.log(Level.WARNING, "Invalid value for property " + key + ": " + value + " (using default value: " + defaultValue + ")");
		}
		return defaultValue;
	}


	private static int getIntProperty(String key, int[] validValues, int defaultValue) {
		String value = properties.getProperty(key);
		if (value == null) {
			Util.log(Level.WARNING, "Property " + key + " not configured (using default value: " + defaultValue + ")");
			return defaultValue;
		}
		try {
			int x = Integer.valueOf(value);
			for (int i = 0; i < validValues.length; i++) {
				if (x == validValues[i]) {
					return x;
				}
			}
		} catch (NumberFormatException nfe) {
			Util.log(Level.WARNING, "Invalid value for property " + key + ": " + value + " (using default value: " + defaultValue + ")");
		}
		return defaultValue;
	}

	private static int getIntProperty(String key, int minValue, int maxValue, int defaultValue) {
		String value = properties.getProperty(key);
		if (value == null) {
			Util.log(Level.WARNING, "Property " + key + " not configured (using default value: " + defaultValue + ")");
			return defaultValue;
		}
		try {
			int x = Integer.valueOf(value);
			if (x >= minValue || x <= maxValue) {
				return x;
			}
		} catch (NumberFormatException nfe) {
			Util.log(Level.WARNING, "Invalid value for property " + key + ": " + value + " (using default value: " + defaultValue + ")");
		}
		return defaultValue;
	}


	private static String getStringProperty(String key, List<String> validValues, String defaultValue) {
		String value = properties.getProperty(key);
		if (value == null) {
			Util.log(Level.WARNING, "Property " + key + " not configured (using default value: " + defaultValue + ")");
			return defaultValue;
		}
		if (validValues != null) {
			if (validValues.contains(value)) {
				return value;
			} else {
				Util.log(Level.WARNING, "Invalid value for property " + key + ": " + value + " (using default value: " + defaultValue + ")");
			}
		}
		return defaultValue;
	}

	private static Level getLevelProperty(String key, Level defaultValue) {
		String value = properties.getProperty(key);
		if (value == null) {
			Util.log(Level.WARNING, "Property " + key + " not configured (using default value: " + defaultValue + ")");
			return defaultValue;
		}
		if (value.equals("FINE")) {
			return Level.FINE;
		} else if (value.equals("INFO")) {
			return Level.INFO;
		} else if (value.equals("WARNING")) {
			return Level.WARNING;
		} else if (value.equals("SEVERE")) {
			return Level.SEVERE;
		}
		Util.log(Level.WARNING, "Invalid value for property " + key + ": " + value + " (using default value: " + defaultValue + ")");
		return defaultValue;
	}

	private static Color getColorProperty(String key, Color defaultValue) {
		String value = properties.getProperty(key);
		if (value == null) {
			Util.log(Level.WARNING, "Property " + key + " not configured (using default value: " + defaultValue + ")");
			return defaultValue;
		}
		StringTokenizer st = new StringTokenizer(value, ",");
		if (st.countTokens() == 3) {
			try {
				int r = Integer.parseInt(st.nextToken());
				int g = Integer.parseInt(st.nextToken());
				int b = Integer.parseInt(st.nextToken());
				if (r >= 0 && r <= 255 && g >= 0 && g <= 255 && b >= 0 && b <= 255) {
					return new Color(r, g, b);
				}
			} catch (NoSuchElementException nsee) {
			} catch (NumberFormatException nfe) {
			}
		}
		Util.log(Level.WARNING, "Invalid value for property " + key + ": " + value + " (using default value: " + defaultValue + ")");
		return defaultValue;
	}



	public static void save(File file, File backup) throws IOException {
		Util.backup(file, backup, 1);
		long oldLength = file.length();
		PrintWriter pw = new PrintWriter(file);
		pw.println("#============ Panda configuration ============");
		pw.println("");
		pw.println("# The PANDA_HOME environment variable should be set.");
		pw.println("# If it is not set, then a default value of <HOME>/panda will be used.");
		pw.println("");
		pw.println("# Log Level");
		pw.println("# Logging output is sent to both the console and the file PANDA_HOME/panda.log");
		pw.println("# Value must be one of: FINE, INFO, WARNING, SEVERE (ordered from low to high)");
		pw.println("# Default value is \"INFO\"");
		pw.println("# Note:");
		pw.println("#   A \"floor\" level for both console and file can be specified in:");
		pw.println("#     jre/lib/logging.properties");
		pw.println("#       java.util.logging.ConsoleHandler.level=INFO");
		pw.println("#       panda.level=FINE");
		pw.println("#   For less verbose console output, set the console log level no lower than INFO");
		pw.println("");
		pw.println("panda.log.level=" + level);
		pw.println("");
		pw.println("");
        pw.println("# Tracks directory");
        pw.println("# This is the path to the directory that contains all the .wav files.");
        pw.println("# Value can be absolute or relative to the PANDA_HOME directory.");
        pw.println("# Default value is \"tracks\"");
		pw.println("");
		pw.println("panda.tracks=" + tracks);
		pw.println("");
		pw.println("");
        pw.println("# Graphical User Interface (GUI) Look-and-Feel (LAF)");
        pw.println("# Use either the \"Nimbus\" or the native, platform-specific LAF");
        pw.println("# The native LAF is good on Mac OS and Windows,");
        pw.println("# but it does a relatively poor job trying to imitate GTK on Linux.");
        pw.println("# If this property is set to false then it will use Nimbus.");
        pw.println("# Note that Nimbus is only avaiable from Java SE 7 onwards.");
        pw.println("# If Nimbus is not available then it will fall back to using the native look-and-feel");
        pw.println("# Default value is \"false\"");
		pw.println("");
		pw.println("panda.gui.native=" + isNative);
		pw.println("");
		pw.println("");
        pw.println("# GUI Fullscreen Mode");
        pw.println("# In fullscreen mode it will not make use of resizable desktop windows");
        pw.println("# Default value is \"false\"");
		pw.println("");
		pw.println("panda.gui.fullscreen=" + isFullscreen);
		pw.println("");
		pw.println("");
        pw.println("# GUI Layout");
        pw.println("# 1 = All controls at the top");
        pw.println("# 2 = Play controls at the top, other controls in the lower left (below the tree)");
        pw.println("# 3 = Play controls at the top, other controls in the lower right (below the table)");
        pw.println("# 4 = Play controls at the bottom");
        pw.println("# Default value is \"1\"");
		pw.println("");
		pw.println("panda.gui.layout=" + layout);
		pw.println("");
		pw.println("");
        pw.println("# Number of equalizer bands");
        pw.println("# Must be one of: 10, 15, 25 or 31");
        pw.println("# Default value is \"10\"");
		pw.println("");
		pw.println("panda.equalizer.bands=" + bands);
		pw.println("");
		pw.println("");
        pw.println("# Wait");
        pw.println("# The number of seconds to wait between tracks");
        pw.println("# More specifically: it will wait at the end of each song - not the start.");
        pw.println("# And it will only wait if the end of the song was reached during normal play,");
        pw.println("# ie. not when the \"Next\" button or \"Play Now\" menu were clicked");
        pw.println("# The value must be between 0 and 10, inclusive.");
        pw.println("# Default value is either:");
        pw.println("#    0 (if a value less than zero was specified)");
        pw.println("#   10 (if a value more than 10 was specified)");
		pw.println("");
		pw.println("panda.wait=" + wait);
		pw.println("");
		pw.println("");
        pw.println("# ------------ Colours ------------");
        pw.println("# Each colour has format: red,green,blue with values from 0 to 255");
		pw.println("");
        pw.println("# The current track and next track/cortina/tanda can be highlighted");
        pw.println("# in the tree, the table and the navigation buttons");
        pw.println("panda.colour.currentTrack=" + currentTrackColor.getRed() + "," + currentTrackColor.getGreen() + "," + currentTrackColor.getBlue());
        pw.println("panda.colour.nextTrack=" + nextTrackColor.getRed() + "," + nextTrackColor.getGreen() + "," + nextTrackColor.getBlue());
        pw.println("panda.colour.nextCortina=" + nextCortinaColor.getRed() + "," + nextCortinaColor.getGreen() + "," + nextCortinaColor.getBlue());
        pw.println("panda.colour.nextTanda=" + nextTandaColor.getRed() + "," + nextTandaColor.getGreen() + "," + nextTandaColor.getBlue());
		pw.println("");
        pw.println("# Tracks listed in the table can have genre-specific colours");
		Set<String> keys = genreColors.keySet();
		Iterator iterator = keys.iterator();
		while (iterator.hasNext()) {
			String key = (String) iterator.next();
			Color color = genreColors.get(key);
        	pw.println("panda.colour.track.genre." + key + "=" + color.getRed() + "," + color.getGreen() + "," + color.getBlue());
		}
		// TODO: set column widths directly in UI, ie. by users resizing columns, rather than a preferences dialog.
		// Though... what about min, max widths?
		// And what about column headings, tags and types?
		// Best may be to read & write these properties, but not male them configurable via UI?
        pw.println("");
        pw.println("");
        pw.println("# ------------ Table ------------");
        pw.println("# Columns names, widths, etc.");
        pw.println("# Some columns are mandatory (such as \"Time\" and \"Title\")");
        pw.println("# while others are optional (such as \"Orchestra\" or \"Comment\")");
        pw.println("# Each column is numbered sequentially, starting at either 0 or 1.");
        pw.println("# Column 0 is special and only the widths can be configured.");
        pw.println("# The remaining columns must have a name and may optionally have:");
        pw.println("# - Type (alpha or numeric, default value is alpha)");
        pw.println("# - Width");
        pw.println("# - Minimum width");
        pw.println("# - Maximum width");
        pw.println("# There are a variety of hard-coded default widths for various columns.");
        pw.println("");
        pw.println("panda.column.0.width=20");
        pw.println("panda.column.0.width.min=20");
        pw.println("panda.column.0.width.max=80");
        pw.println("");
        pw.println("panda.column.1=Title");
        pw.println("panda.column.1.width=160");
        pw.println("panda.column.1.width.min=80");
        pw.println("");
        pw.println("panda.column.2=Orchestra");
        pw.println("panda.column.2.width=80");
        pw.println("panda.column.2.width.min=80");
        pw.println("");
        pw.println("panda.column.3=Singer(s)");
        pw.println("panda.column.3.tag.panda=singers");
        pw.println("panda.column.3.width=160");
        pw.println("panda.column.3.width.min=80");
        pw.println("");
        pw.println("panda.column.4=Genre");
        pw.println("panda.column.4.width=80");
        pw.println("panda.column.4.width.min=60");
        pw.println("panda.column.4.width.max=120");
        pw.println("");
        pw.println("panda.column.5=Year");
        pw.println("panda.column.5.type=numeric");
        pw.println("panda.column.5.width=48");
        pw.println("panda.column.5.width.min=42");
        pw.println("panda.column.5.width.max=60");
        pw.println("");
        pw.println("panda.column.6=Time");
        pw.println("panda.column.6.width=48");
        pw.println("panda.column.6.width.min=42");
        pw.println("panda.column.6.width.max=60");
        pw.println("");
        pw.println("panda.column.7=BPM");
        pw.println("panda.column.7.type=numeric");
        pw.println("panda.column.7.width=48");
        pw.println("panda.column.7.width.min=36");
        pw.println("panda.column.7.width.max=60");
        pw.println("");
        pw.println("panda.column.8=Source");
        pw.println("panda.column.8.width=80");
        pw.println("panda.column.8.width.min=60");
        pw.println("panda.column.8.width.max=120");
        pw.println("");
        pw.println("panda.column.9=Comment");
        pw.println("panda.column.9.width=240");
        pw.println("");
        pw.println("");
        pw.println("# ------------ Projector ------------");
        pw.println("# Dimensions");
        pw.println("# Specify realistic dimensions, such as 640x480 or 800x600");
        pw.println("# Minimum dimension is 640x480");
        pw.println("# Maximum dimension is 2560x1440");
        pw.println("# By default it will use half the screen dimensions");
        pw.println("panda.projector.width=800");
        pw.println("panda.projector.height=600");
        pw.println("");
        pw.println("# Default values to display");
        pw.println("panda.projector.header=PANDA");
        pw.println("panda.projector.image=panda-logo.png");
        pw.println("panda.projector.body=Johan Steyn");
        pw.println("panda.projector.tail=\u00a9 2017");
        pw.println("panda.projector.footer=DJ  Johan Steyn");
        pw.println("");
        pw.println("# List of genres that will be have their info displayed (the rest will display defaults only)");
        pw.println("panda.projector.genres=Tango,Vals,Milonga");
        pw.println("");
        pw.println("# Mappings between full orchestra names and their shorter display names");
        pw.println("panda.projector.map.Adolfo\\ Carabelli=Carabelli");
        pw.println("panda.projector.map.Alberto\\ Castellanos=Castellanos");
        pw.println("panda.projector.map.Alberto\\ Castillo=Castillo");
        pw.println("panda.projector.map.Alberto\\ G\\u00F3mez=G\\u00F3mez");
        pw.println("panda.projector.map.Alfredo\\ De\\ Angelis=De\\ Angelis");
        pw.println("panda.projector.map.Alfredo\\ Gobbi=Gobbi");
        pw.println("panda.projector.map.\\u00C1ngel\\ D\\'Agostino=D\\'Agostino");
        pw.println("panda.projector.map.An\\u00EDbal\\ Troilo=Troilo");
        pw.println("panda.projector.map.Carlos\\ Di\\ Sarli=Di\\ Sarli");
        pw.println("panda.projector.map.Edgardo\\ Donato=Donato");
        pw.println("panda.projector.map.Eduardo\\ Bianco\\,\\ Bachicha=Bianco\\ -\\ Bachicha");
        pw.println("panda.projector.map.Emilio\\ Pellejero=Pellejero");
        pw.println("panda.projector.map.Enrique\\ Francini\\,\\ Armando\\ Pontier=Francini\\ -\\ Pontier");
        pw.println("panda.projector.map.Enrique\\ Rodr\\u00EDguez=Rodr\\u00EDguez");
        pw.println("panda.projector.map.Francisco\\ Canaro=Canaro");
        pw.println("panda.projector.map.Francisco\\ Lomuto=Lomuto");
        pw.println("panda.projector.map.Jos\\u00E9\\ Garc\\u00EDa=Garc\\u00EDa");
        pw.println("panda.projector.map.Juan\\ D\\'Arienzo=D\\'Arienzo");
        pw.println("panda.projector.map.Julio\\ De\\ Caro=De\\ Caro");
        pw.println("panda.projector.map.Lucio\\ Demare=Demare");
        pw.println("panda.projector.map.Manuel\\ Buz\\u00F3n=Buz\\u00F3n");
        pw.println("panda.projector.map.Mercedes\\ Simone=Mercedes\\ Simone");
        pw.println("panda.projector.map.Miguel\\ Cal\\u00F3=Cal\\u00F3");
        pw.println("panda.projector.map.Orquesta\\ T\\u00EDpica\\ Brunswick=O.T.Brunswick");
        pw.println("panda.projector.map.Orquesta\\ T\\u00EDpica\\ Victor=O.T.Victor");
        pw.println("panda.projector.map.Osvaldo\\ Fresedo=Fresedo");
        pw.println("panda.projector.map.Osvaldo\\ Pugliese=Pugliese");
        pw.println("panda.projector.map.Pedro\\ Laurenz=Laurenz");
        pw.println("panda.projector.map.Rafael\\ Canaro=Rafael\\ Canaro");
        pw.println("panda.projector.map.Ricardo\\ Malerba=Malerba");
        pw.println("panda.projector.map.Ricardo\\ Tanturi=Tanturi");
        pw.println("panda.projector.map.Roberto\\ Firpo=Firpo");
        pw.println("panda.projector.map.Rodolfo\\ Biagi=Biagi");
        pw.println("panda.projector.map.Domingo\\ Federico=Federico");
        pw.println("panda.projector.map.Lucio\\ Demare=Demare");
        pw.println("");
        pw.println("");
        pw.println("# TODO...");
        pw.println("# Colours");
        pw.println("# Valid colour values: black, white, red, yellow");
        pw.println("#panda.projector.colour.background=black");
        pw.println("#panda.projector.colour.text=red");
		pw.println("");
		pw.flush();
		pw.close();
		long newLength = file.length();
		if (oldLength != newLength) {
			// File has changed since it was last saved, so copy it to the specified backup file
			Util.copy(file, backup);
		}
	}

}

