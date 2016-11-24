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
import java.awt.event.*;
import java.beans.*;
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EventObject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.plaf.*;
import javax.swing.plaf.basic.*;
import javax.swing.table.*;
import javax.swing.tree.*;


// TODO: 
// - CTRL-C and CTRL-N to display Current and Next tracks
// - Make the table row select colour configurable (default being whatever the LAF dictates)
//   Normally I would leave this entirely up to the LAF, but with user being able to configure track colours it makes sense to make this configurable too...
public class Panda extends JFrame {
	public static final String TRACKS_DIR;  // Absolute path, ending with a file separator
	public static final int BANDS;
	private static final boolean FULLSCREEN;
	public static final int WAIT;
	private static SplashScreen splash = SplashScreen.getSplashScreen();
	private static Graphics2D splashGraphics;

	private static List<Image> iconImages = new ArrayList<Image>();

	// Track colors
	private static Color currentTrackColor = Color.RED;
	private static Color nextTrackColor = Color.GREEN;
	private static Color nextCortinaColor = Color.YELLOW;
	private static Color nextTandaColor = Color.BLUE;
	private static Map<String,Color> genreColors = new HashMap<String,Color>();

	private static int defaultModifier; // The platform-specific key modifier, eg: CTRL on Linux/Windows or CMD on Mac

	private int[] customPresets;
	private Container contentPane;
	private List<String> filenames = new ArrayList<String>();
	private Map<String, Track> trackMap = new HashMap<String, Track>();  // A map of filename to Track instances
	private Map<String, List<Track>> playlistMap = new HashMap<String, List<Track>>();  // A map of playlist name to list of Track instances
	private List<String> playlists = new ArrayList<String>(); // An list of playlist names, in the order that they appear in the playlist file, with main "Tracks" playlist at the head
	private List<Track> displayPlaylist; // The playlist that is selected in the tree (and displayed in the table)
	private List<Track> currentTrackPlaylist = new ArrayList<Track>();
	private List<Track> nextTrackPlaylist = currentTrackPlaylist;
	private List<Track> nextCortinaPlaylist;
	private List<Track> nextTandaPlaylist;
	private List<Track> emptyPlaylist = new ArrayList<Track>(); 
	private List<Track> historyPlaylist = new ArrayList<Track>();
	private List<Track> holdPlaylist;
	// NOTE: Use track indexes rather than track instances, since tracks can appear more than once in a playlist
	private int currentTrackIndex = -1;
	private int nextTrackIndex = -1; 
	private int nextCortinaIndex = -1; 
	private int nextTandaIndex = -1; 
	private PlayThread playThread = new PlayThread();
	private SaveThread saveThread = new SaveThread();
	private boolean settingPosition; // Flag to indicate that the position is being set during play, ie. not by the user dragging the slider.
	private int prevPosition = -1; // The last position that the slider was set to by the user

	// UI components
	private JButton logoButton = new ImageButton("panda-logo-transparent-064.png");
	private JPopupMenu logoPopupMenu = new JPopupMenu();
	private JPopupMenu tablePopupMenu = new JPopupMenu();
	// TODO: Keyboard shortcuts for menu items...
	private JCheckBoxMenuItem projectorMenuItem = new JCheckBoxMenuItem("Projector", false);
	private JMenuItem quitMenuItem = new JMenuItem("Quit");
	private JMenuItem playNowMenuItem = new JMenuItem("Play now");
	private JMenuItem nextTrackMenuItem = new JMenuItem("Next Track");
	private JMenuItem nextCortinaMenuItem = new JMenuItem("Next Cortina");
	private JMenuItem nextTandaMenuItem = new JMenuItem("Next Tanda");
	private JMenuItem selectAllMenuItem = new JMenuItem("Select all");
	private JMenuItem selectNoneMenuItem = new JMenuItem("Select none");

	private JLabel leftLabel = new JLabel(" ");
	private JButton prevButton = new ImageButton("panda-prev-032.png");
	private	JButton playButton = new ImageButton("panda-play-032.png");
	private	JButton nextButton = new ImageButton("panda-next-032.png");
	private JLabel rightLabel = new JLabel(" ");

	private JSlider positionSlider = new PositionSlider(SwingConstants.HORIZONTAL);
	private JLabel trackLabel = new JLabel(" "); // Need at least a space in order to establish height for component
	private List<EQSlider> equalizerSliders = new ArrayList<EQSlider>();
	private JSlider volumeSlider = new ControlSlider();
	private JLabel volumeLabel = new ControlLabel("Vol");
	private JSlider balanceSlider = new ControlSlider();
	private JLabel balanceLabel = new ControlLabel("Bal");
	private JComboBox equalizerPresets = new PresetComboBox();
	private JCheckBox equalizerCheckbox = new JCheckBox();  // Unselected by default
	private JTree tree;
	private JTable table;
	private JLabel statusLabel = new JLabel(" ");
	private JTextField searchField = new JTextField("Search...", 20);
	private TableColumnModel tableColumnModel = new PandaTableColumnModel();

	private JButton showCurrentTrackButton = new JButton();
	private JButton showNextTrackButton = new JButton();
	private JButton showNextCortinaButton = new JButton();
	private JButton showNextTandaButton = new JButton();

	private JSplitPane splitPane;
	private Projector projector;

	static {
		// Ensure properties are loaded first by referencing Util class...
		Util.log(Level.INFO, "Initializing Panda...");
		updateSplash("Initializing Panda...");

		String dirname = System.getProperty("panda.tracks.directory");
		if (dirname == null) {
			// Not configured, so use default
			dirname = Util.PANDA_HOME + "tracks";
		}
		// Ensure configured/default directory exists
		File dir = new File(dirname);
		if (!dir.exists()) {
			Util.log(Level.SEVERE, "Tracks directory not found: " + dirname);
			System.exit(1);
		}
		dirname = dir.getAbsolutePath();
		if (dirname.charAt(dirname.length() - 1) != File.separatorChar) {
			dirname += File.separator;
		}
		TRACKS_DIR = dirname;
		Util.log(Level.INFO, "Scanning tracks directory: " + TRACKS_DIR);

		String fullscreen = System.getProperty("panda.gui.fullscreen");
		if (fullscreen != null && fullscreen.equals("true")) {
			FULLSCREEN = true;
		} else {
			FULLSCREEN = false;
		}

		int bands = 10;
		String key = "panda.equalizer.bands";
		String value = System.getProperty(key);
		if (value != null) {
			try {
				bands = Integer.parseInt(value);
			} catch (NumberFormatException nfe) {
				bands = -1;
			}
		}
		if (bands != 10 && bands != 15 && bands != 25 && bands != 31) {
			Util.log(Level.INFO, "Invalid value for property " + key + ": " + value);
		}
		BANDS = bands;

		int wait = 0;
		key = "panda.wait";
		value = System.getProperty(key);
		if (value != null) {
			try {
				wait = Integer.parseInt(value);
			} catch (NumberFormatException nfe) {
				wait = -1;
			}
		}
		if (wait < 0 || wait > 10) {
			Util.log(Level.INFO, "Invalid value for property " + key + ": " + value);
		}
		WAIT = wait;

		currentTrackColor = getColor("panda.colour.currentTrack", currentTrackColor);
		nextTrackColor = getColor("panda.colour.nextTrack", nextTrackColor);
		nextCortinaColor = getColor("panda.colour.nextCortina", nextCortinaColor);
		nextTandaColor = getColor("panda.colour.nextTanda", nextTandaColor);
		Properties properties = System.getProperties();
		Set<String> keys = properties.stringPropertyNames();
		Iterator iterator = keys.iterator();
		while (iterator.hasNext()) {
			key = (String) iterator.next();
			String s = "panda.colour.track.genre.";
			if (key.startsWith(s)) {
				String genre = key.substring(s.length());
				Color color = getColor(key, Color.WHITE);
				genreColors.put(genre, color);
			}
		}

		// Ensure that all text is anti-aliased (especially for projector)
		System.setProperty("awt.useSystemAAFontSettings","on"); 
		System.setProperty("swing.aatext", "true"); 
	}

	static void updateSplash(String message) {
		long millis = System.currentTimeMillis();
		if (splash == null) {
			splash = SplashScreen.getSplashScreen();
			if (splash == null) {
				return;
			}
		}
		if (splashGraphics == null) {
			splashGraphics = splash.createGraphics();
			if (splashGraphics == null) {
				return;
			}
		}
		FontMetrics fm = splashGraphics.getFontMetrics();
		int width = fm.stringWidth(message);
		splashGraphics.setColor(Color.WHITE);
		splashGraphics.fillRect(58, 56, 140, 16);
		splashGraphics.setPaintMode();
		splashGraphics.setColor(Color.BLACK);
		splashGraphics.drawString(message, (256 - width) / 2, 68);
		splash.update();
	}

	public static void main(String[] args) throws Exception {
		Util.log(Level.FINE, "Starting Panda...");
		updateSplash("Starting Panda...");
		UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		String guiNative = System.getProperty("panda.gui.native");
		if (guiNative == null || guiNative.equals("false")) {
			try {
				UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
			} catch (ClassNotFoundException cnfe) {
				// Failing to load Nimbus will result in native look-and-feel being retained
			}
		}
		// Workaround to remove value label from slider in GTK LAF
		// http://bugs.sun.com/view_bug.do?bug_id=6465237
		UIManager.put("Slider.paintValue", Boolean.FALSE);
		Toolkit toolkit = Toolkit.getDefaultToolkit();
		Dimension dimension = toolkit.getScreenSize();
		defaultModifier = toolkit.getMenuShortcutKeyMask();
		// TODO: Figure out how to provide best resolution images for each platform...
		iconImages.add(toolkit.getImage(Panda.class.getResource("panda-logo-012.png")));
		iconImages.add(toolkit.getImage(Panda.class.getResource("panda-logo-016.png")));
		iconImages.add(toolkit.getImage(Panda.class.getResource("panda-logo-024.png")));
		iconImages.add(toolkit.getImage(Panda.class.getResource("panda-logo-032.png")));
		iconImages.add(toolkit.getImage(Panda.class.getResource("panda-logo-048.png")));
		iconImages.add(toolkit.getImage(Panda.class.getResource("panda-logo-064.png")));
		iconImages.add(toolkit.getImage(Panda.class.getResource("panda-logo-096.png")));
		final Panda panda;
		// TODO: Decide whether to use a JFrame or JWindow for fullscreen...
		// + On Ubuntu no window controls are displayed, therefor it stays in fullscreen mode (cannot minimize/maximize)
		// - On Ubuntu the task switcher doesn't work
		// + Able to use entire screen minus whatever space is taken up by desktop taskbar on Ubuntu
		// - On Mac OS it doesn't use correct insets...
		// JFrame:
		// - On Ubuntu the window controls (close, maximize, minimize) are still displayed (even when setting it undecorated)
		// + On Ubuntu the task switcher works
		if (FULLSCREEN) {
			JWindow window = new JWindow();
			Container contentPane = window.getContentPane();
			panda = new Panda(contentPane);
			GraphicsConfiguration gc = window.getGraphicsConfiguration();
			Insets insets = toolkit.getScreenInsets(gc);
			int width = dimension.width - insets.left - insets.right;
			int height = dimension.height - insets.top - insets.bottom;
			window.setSize(width, height);
			window.setLocation(insets.left, insets.top);
			//int width = dimension.width;
			//int height = dimension.height;
			//window.setSize(width, height);
			//window.setLocation(posX, posY);
			window.setIconImages(iconImages);
			window.setVisible(true);
		} else {
			JFrame frame = new JFrame("Panda");
			Container contentPane = frame.getContentPane();
			panda = new Panda(contentPane);
			int width = 2 * dimension.width / 3;
			int height = 2 * dimension.height / 3;
			frame.setSize(width, height);
			frame.setLocation((dimension.width - width) / 2, (dimension.height - height) / 3);
			frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
			frame.addWindowListener(new WindowAdapter() {
				public void windowClosing(WindowEvent e) {
					panda.quit();
				}
			});
			frame.setIconImages(iconImages);
			frame.setVisible(true);
		}
		panda.playButton.requestFocusInWindow();
		// Note: Need to set divider location here, after having made the UI visible
		panda.splitPane.setDividerLocation(0.25);
	}

	public Panda(Container contentPane) throws IOException, UnsupportedAudioFileException {
		this.contentPane = contentPane;
		updateSplash("Scanning tracks...");
		scan();
		updateSplash("Loading tracks...");
		loadTracks();
		updateSplash("Reading tags...");
		readTags();
		// Note: Need to sort main playlist after loading the tracks AND reading the tags.
		Collections.sort(currentTrackPlaylist);
		updateSplash("Reading playlists...");
		readPlaylists();
		// Not sure if I need to do this on event dispatch thread - better safe than sorry...
		updateSplash("Building UI...");
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				buildUI();
			}
		});
		customPresets = new int[BANDS];
		updateSplash("Starting threads...");
		playThread.start();
		saveThread.start();
		projector = new Projector(this);
		if (splash != null) {
			splash.close();
		}
	}
	
	// Kicks off scan of tracks directory
	private void scan() {
		long start = Util.startTimer();
		scanDir(new File(TRACKS_DIR));
		StringBuffer sb = new StringBuffer();
		for (String filename: filenames) {
			sb.append(filename);
			sb.append("\n");
		}
		int number = filenames.size();
		Util.log(Level.FINE, "Found track files:\n" + sb);
		Util.stopTimer(start, "Scan of " + number + " track files");
	}

	// Recursive method
	private void scanDir(File dir) {
		File[] entries = dir.listFiles();
		if (entries == null) {
			return;
		}
		Collections.sort(Arrays.asList(entries));
		for (int i = 0; i < entries.length; i++) {
			File file = entries[i];
			if (file.isDirectory()) {
				scanDir(file);
			}
			if (!file.isFile()) {
				continue;
			}
			if (file.getName().startsWith(".")) {
				// Exclude hidden files (annoyingly added by Dropbox)
				continue;
			}
			String filename = file.getAbsolutePath();
			filename = filename.substring(TRACKS_DIR.length());
			if (filename.endsWith(".wav")) {
				filenames.add(filename);
			}
		}
		// TODO: Sort filenames so that the tags file is sorted
		Collections.sort(filenames);
	}

	// Populates the main playlist of tracks, creating a new Track instance for every file found during scan
	private void loadTracks() throws IOException, UnsupportedAudioFileException {
		Util.log(Level.INFO, "Loading track files...");
		long start = Util.startTimer();
		for (String filename: filenames) {
			Track track = new Track(filename);
			track.setTitle(filename); // By default, each track's title is the filename (minus the tracks directory name)
			Player player = track.getPlayer();
			player.addPlayerListener(new PlayerListener() {
				public void started(final Player player) {
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							int duration = player.getDuration();
							leftLabel.setText(Util.minutesSeconds(0));
							rightLabel.setText(Util.minutesSeconds(duration));
							settingPosition = true;
							positionSlider.setValue(0);
							Track track = player.getTrack();
							String s = track.getTitle();
							// TODO: Make configurable in case some users use something other than "orchestra" (eg: "artist")
							String orchestra = track.getTag("orchestra");  
							if (orchestra != null && orchestra.length() > 0) {
								s = s + " - " + orchestra;
							}
							trackLabel.setText(s);
							if (player.isGainControlSupported()) {
								volumeSlider.setEnabled(true);
							} else {
								volumeSlider.setEnabled(false);
							}
							if (player.isBalanceControlSupported()) {
								balanceSlider.setEnabled(true);
							} else {
								balanceSlider.setEnabled(false);
							}
						}
					});
				}
				public void positionChanged(final Player player) {
					settingPosition = true;
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							int duration = player.getDuration();
							int position = player.getPosition();
							leftLabel.setText(Util.minutesSeconds(position));
							rightLabel.setText(Util.minutesSeconds(duration - position));
							positionSlider.setMinimum(0);
							positionSlider.setMaximum(player.getDuration());
							positionSlider.setValue(player.getPosition());
						}
					});
					// Track is playing, so to be safe, have play button grab focus, but not if some other component needs to retain focus
					if (!searchField.hasFocus() && !equalizerPresets.hasFocus()) {
						playButton.requestFocusInWindow();
					}
				}
			});
			trackMap.put(filename, track);
			currentTrackPlaylist.add(track);
		}
		int number = currentTrackPlaylist.size();
		Util.stopTimer(start, "Load of " + number + " tracks");
	}

	// Read tags from the Panda tag file
	private void readTags() throws IOException {
		Util.log(Level.INFO, "Reading tags...");
		long start = Util.startTimer();
		String filename = Util.PANDA_HOME + "panda.tags";
		BufferedReader br = null;
		try {
			FileInputStream fis = new FileInputStream(filename);
			br = new BufferedReader(new InputStreamReader(fis));
			String line = null;
			Track track = null;
			while (true) {
				line = br.readLine();
				if (line == null) {
					break;
				}
				line = line.trim();
				if (line.length() <= 0) {
					continue;
				}
				if (line.startsWith("#")) {
					continue;
				}
				if (line.startsWith("file=")) {
					filename = line.substring(5);
					Util.log(Level.FINE, "Reading tags for file: " + filename);
					track = trackMap.get(filename);
					if (track == null) {
						// Tags found for file that doesn't exist
						Util.log(Level.SEVERE, "File not found for tag entry: " + filename);
					}
					continue;
				}
				if (track == null) {
					continue;
				}
				int index = line.indexOf("=");
				if (index > 0 && index < line.length() - 2) {
					String name = line.substring(0, index);
					String value = line.substring(index + 1);
					if (name.equals("title")) {
						track.setTitle(value);
					} else {
						// TODO: Buffer tags for file and log them all together...
						Util.log(Level.FINE, "Setting tag: " + name + "=" + value);
						track.setTag(name, value);
					}
				}
			}
		} catch (IOException ioe) {
			Util.log(Level.WARNING, "Error loading Panda tags from " + filename + ": " + ioe);
			Util.log(Level.WARNING, "Using default property values.");
			return;
		} finally {
			if (br != null) {
				br.close();
			}
		}
		Util.stopTimer(start, "Reading of tags");
	}

	// Read playlists from the Panda playlists file
	private void readPlaylists() throws IOException {
		Util.log(Level.INFO, "Reading playlists...");
		// First add the main playlist of all tracks
		playlistMap.put("Tracks", currentTrackPlaylist);
		playlists.add("Tracks");
		long start = Util.startTimer();
		String filename = Util.PANDA_HOME + "panda.playlists";
		BufferedReader br = null;
		try {
			FileInputStream fis = new FileInputStream(filename);
			br = new BufferedReader(new InputStreamReader(fis));
			String line = null;
			List<Track> tracks = null;
			while (true) {
				line = br.readLine();
				if (line == null) {
					break;
				}
				line = line.trim();
				if (line.length() <= 0) {
					continue;
				}
				if (line.startsWith("#")) {
					continue;
				}
				if (line.startsWith("playlist=")) {
					int index = line.indexOf("=");
					String name = line.substring(index + 1);
					tracks = new ArrayList<Track>();
					playlistMap.put(name, tracks);
					playlists.add(name);
					continue;
				} else {
					// Line is name of track - look it up in main list of tracks and add to tracks...
					Track track = trackMap.get(line);
					if (track == null) {
						// Currently this results in a null p[ointer exception in the getValueAt method of PandaTableModel
						Util.log(Level.SEVERE, "File not found for playlist entry: " + line);
					} else {
						tracks.add(track);
					}
				}
			}
		} catch (IOException ioe) {
			Util.log(Level.WARNING, "Error reading Panda playlists from " + filename + ": " + ioe);
			return;
		} finally {
			if (br != null) {
				br.close();
			}
		}
		String historyPlaylistName = "History/" + new Date();
		playlists.add(historyPlaylistName);
		playlistMap.put(historyPlaylistName, historyPlaylist);
		Util.stopTimer(start, "Reading of playlists");
	}

	private void buildUI() {
		create();
		layoutUI();
		addListeners();
		//setDebugBorders();
	}

	private void create() {
		// Icons
		// Todo: Setting selected icons doesn't seem to have any effect (I assume it's supposed to be used when focus is obtained?)
		logoButton.setRolloverIcon(new ImageIcon(Panda.class.getResource("panda-logo-064.png")));
		logoButton.setSelectedIcon(new ImageIcon(Panda.class.getResource("panda-logo-064.png")));
		logoButton.setPressedIcon(new ImageIcon(Panda.class.getResource("panda-logo-pressed-064.png")));
		prevButton.setRolloverIcon(new ImageIcon(Panda.class.getResource("panda-prev-rollover-032.png")));
		prevButton.setSelectedIcon(new ImageIcon(Panda.class.getResource("panda-prev-rollover-032.png")));
		prevButton.setPressedIcon(new ImageIcon(Panda.class.getResource("panda-prev-pressed-032.png")));
		playButton.setRolloverIcon(new ImageIcon(Panda.class.getResource("panda-play-rollover-032.png")));
		playButton.setSelectedIcon(new ImageIcon(Panda.class.getResource("panda-play-rollover-032.png")));
		playButton.setPressedIcon(new ImageIcon(Panda.class.getResource("panda-play-pressed-032.png")));
		nextButton.setRolloverIcon(new ImageIcon(Panda.class.getResource("panda-next-rollover-032.png")));
		nextButton.setSelectedIcon(new ImageIcon(Panda.class.getResource("panda-next-rollover-032.png")));
		nextButton.setPressedIcon(new ImageIcon(Panda.class.getResource("panda-next-pressed-032.png")));
		// Slider ranges
		positionSlider.setMinimum(0);
		positionSlider.setMaximum(150);
		positionSlider.setValue(0);
		volumeSlider.setMinimum(0);
		volumeSlider.setMaximum(20);
		volumeSlider.setValue(14);
		// Volume and Balance controls are disabled by default
		volumeSlider.setEnabled(false);
		balanceSlider.setEnabled(false);
		// Preset values
		equalizerPresets.addItem("None");
		equalizerPresets.addItem("Bass +");
		equalizerPresets.addItem("Bass -");
		equalizerPresets.addItem("Mid +");
		equalizerPresets.addItem("Mid -");
		equalizerPresets.addItem("Treble +");
		equalizerPresets.addItem("Treble -");
		equalizerPresets.addItem("Custom");
		// Equalizer sliders
		for (int i = 0; i < BANDS; i++) {
			EQSlider slider = new EQSlider();
			equalizerSliders.add(slider);
		}
		// ToolTips
		ToolTipManager ttm = ToolTipManager.sharedInstance();
		ttm.setInitialDelay(0);
		equalizerCheckbox.setToolTipText("Enable equalizer");
		// I find tooltips to be annoying here...
		//showCurrentTrackButton.setToolTipText("Now Playing");
		//showNextTrackButton.setToolTipText("Next Track");
		//showNextCortinaButton.setToolTipText("Next Cortina");
		//showNextTandaButton.setToolTipText("Next Tanda");

		DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Root");
		for (String playlist : playlists) {
			// Traverse the tree, from root down, looking for node where to add the new node, creating parent nodes along the way as necessary.
			createNodesForPath(rootNode, playlist);
		}
		tree = new JTree(rootNode);
		tree.setRootVisible(false);
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		tree.setCellRenderer(new PandaTreeCellRenderer());

		displayPlaylist = currentTrackPlaylist;
		table = new PandaTable(new PandaTableModel(displayPlaylist), tableColumnModel);
		table.setFillsViewportHeight(true); // Makes it easier to make the table a target for drag-and-drop
		table.setBackground(Color.white);
		// Don't want these...
		//table.setRowHeight(36);
		//table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		//table.getColumnModel().getColumn(0).setPreferredWidth(50);
		//table.getColumnModel().getColumn(1).setPreferredWidth(100);
	}

	// Recursive method to create nodes that represent the path from the specified node down
	private void createNodesForPath(DefaultMutableTreeNode node, String path) {
		int index = path.indexOf("/");
		if (index < 0) {
			// Path represents a leaf node 
			// TODO: Check first if it already exists?
			DefaultMutableTreeNode tn = new DefaultMutableTreeNode(path);
			node.add(tn);
		} else {
			String name = path.substring(0, index);
			// Find node that represents the first node in the path
			// If not found, then create one and add it to the node
			// Then call the method recursively with remainder of path
			DefaultMutableTreeNode tn = null;
			for (int i = 0; i < node.getChildCount(); i++) {
				DefaultMutableTreeNode n = (DefaultMutableTreeNode) node.getChildAt(i);
				String s = (String) n.getUserObject();
				if (s.equals(name)) {
					tn = n;
					break;
				}
			}
			if (tn == null) {
				tn = new DefaultMutableTreeNode(name);
				node.add(tn);
			}
			path = path.substring(index + 1);
			createNodesForPath(tn, path);
		}
	}

	private int getIndexForPath(String path) {
		TreeModel treeModel = tree.getModel();
		DefaultMutableTreeNode tn = (DefaultMutableTreeNode) treeModel.getRoot();
		String string = "";
		for (int i = 0; ; i++) {
			// Note: need to start with first node after root, since root node is not displayed.
			tn = tn.getNextNode();
			if (tn == null) {
				break;
			}
			String s = (String) tn.getUserObject();
			//String str = string + (i == 0 ? "" : "/") + s;
			String str = string;
			if (str.length() > 0) {
				str = str + "/";
			}
			str = str + s;
			if (str.equals(path)) {
				return i;
			}
			if (path.startsWith(str)) {
				string = str;
			}
		}
		return -1;
	}

	// Find the playlist value in the map, and return the name (which is the key)
	String findPlaylistName(List<Track> playlist) {
		Set set = playlistMap.keySet();
		Iterator it = set.iterator();
		while (it.hasNext()) {
			String key = (String) it.next();
			if (playlist == playlistMap.get(key)) {
				return key;
			}
		}
		return null;
	}


	// Returns new Color instance with red, green and blue values for specified property
	// If property is not defined, or values are invalid, then the default color will be returned
	private static Color getColor(String key, Color def) {
		String value = System.getProperty(key);
		if (value == null) {
			Util.log(Level.WARNING, "Property " + key + " not defined (Using default value " + def + ")");
		} else {
			boolean valid = false;
			StringTokenizer st = new StringTokenizer(value, ",");
			if (st.countTokens() == 3) {
				try {
					int r = Integer.parseInt(st.nextToken());
					int g = Integer.parseInt(st.nextToken());
					int b = Integer.parseInt(st.nextToken());
					if (r >= 0 && r <= 255 && g >= 0 && g <= 255 && b >= 0 && b <= 255) {
						def = new Color(r, g, b);
						valid = true;
					}
				} catch (NoSuchElementException nsee) {
				} catch (NumberFormatException nfe) {
				}
			}
			if (!valid) {
				Util.log(Level.WARNING, "Invalid value for property " + key + ": " + value + " (Using default value " + def + ")");
			}
		}
		return def;
	}

	// The Java Color class has "darker" and "lighter" methods, but they result in progressively greyer colors.
	// This method will determine which component is the dominant one (if any) and increase it while reducing the other two.
	private Color intensify(Color color) {
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
	private int increase(int i, int amount) {
		i = i + amount;
		if (i > 255) {
			i = 255;
		}
		return i;
	}

	// Decrease by specified amount, down to minimum of 0
	private int decrease(int i, int amount) {
		i = i - amount;
		if (i < 0) {
			i = 0;
		}
		return i;
	}

	// Returns the configured number of the column
	// TODO: Move closer to PandaTableColumnModel?
	private int getColumnNumber(String column) {
		for (int i = 1; ; i++) {
			String name = System.getProperty("panda.column." + i);
			if (name == null) {
				break;
			}
			if (name.equals(column)) {
				return i;
			}
		}
		return -1;
	}

	private void setDebugBorders() {
		//leftLabel.setBorder(new LineBorder(Color.red));
		positionSlider.setBorder(new LineBorder(Color.red));
		trackLabel.setBorder(new LineBorder(Color.red));
		volumeSlider.setBorder(new LineBorder(Color.red));
		volumeLabel.setBorder(new LineBorder(Color.red));
	}

	private void layoutUI() {
		int layout = 1;
		String layoutProperty = System.getProperty("panda.gui.layout");
		if (layoutProperty != null) {
			if (layoutProperty.equals("2")) {
				layout = 2;
			} else if (layoutProperty.equals("3")) {
				layout = 3;
			} else if (layoutProperty.equals("4")) {
				layout = 4;
			}
		}

		contentPane.setLayout(new BorderLayout());

		logoPopupMenu.add(projectorMenuItem);
		logoPopupMenu.addSeparator();
		logoPopupMenu.add(quitMenuItem);
		tablePopupMenu.add(playNowMenuItem);
		tablePopupMenu.add(nextTrackMenuItem);
		tablePopupMenu.add(nextCortinaMenuItem);
		tablePopupMenu.add(nextTandaMenuItem);
		tablePopupMenu.addSeparator();
		tablePopupMenu.add(selectAllMenuItem);
		tablePopupMenu.add(selectNoneMenuItem);

		Box buttonBox = new Box(BoxLayout.X_AXIS);
		leftLabel.setHorizontalAlignment(SwingConstants.LEFT);
		leftLabel.setVerticalAlignment(SwingConstants.BOTTOM);
		prevButton.setBorder(new EmptyBorder(4, 0, 0, 0)); // top, left, bottom, right
		playButton.setBorder(new EmptyBorder(4, 0, 0, 0));
		nextButton.setBorder(new EmptyBorder(4, 0, 0, 0));
		rightLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		rightLabel.setVerticalAlignment(SwingConstants.BOTTOM);
		leftLabel.setAlignmentY(1.0f);
		prevButton.setAlignmentY(1.0f);
		playButton.setAlignmentY(1.0f);
		nextButton.setAlignmentY(1.0f);
		rightLabel.setAlignmentY(1.0f);
		buttonBox.add(Box.createRigidArea(new Dimension(2, 0)));
		buttonBox.add(leftLabel);
		buttonBox.add(Box.createHorizontalGlue());
		buttonBox.add(prevButton);
		buttonBox.add(Box.createRigidArea(new Dimension(16, 0)));
		buttonBox.add(playButton);
		buttonBox.add(Box.createRigidArea(new Dimension(12, 0)));
		buttonBox.add(nextButton);
		buttonBox.add(Box.createHorizontalGlue());
		buttonBox.add(rightLabel);
		buttonBox.add(Box.createRigidArea(new Dimension(2, 0)));

		Box controlBox1 = new Box(BoxLayout.Y_AXIS);
		controlBox1.add(buttonBox);
		controlBox1.add(positionSlider);
		controlBox1.add(trackLabel);
		trackLabel.setHorizontalAlignment(SwingConstants.CENTER);
		
		// Volume
		volumeLabel.setHorizontalAlignment(SwingConstants.LEFT);
		Box volumeBox = new Box(BoxLayout.X_AXIS);
		volumeBox.add(Box.createRigidArea(new Dimension(0, 0)));
		volumeBox.add(volumeSlider);
		volumeBox.add(Box.createRigidArea(new Dimension(2, 0)));
		volumeBox.add(volumeLabel);
		// Balance
		balanceLabel.setHorizontalAlignment(SwingConstants.LEFT);
		Box balanceBox = new Box(BoxLayout.X_AXIS);
		balanceBox.add(Box.createRigidArea(new Dimension(0, 0)));
		balanceBox.add(balanceSlider);
		balanceBox.add(Box.createRigidArea(new Dimension(2, 0)));
		balanceBox.add(balanceLabel);
		// Equalizer Presets
		Box presetsBox = new Box(BoxLayout.X_AXIS);
		presetsBox.add(Box.createRigidArea(new Dimension(0, 0)));
		presetsBox.add(equalizerPresets);
		presetsBox.add(Box.createRigidArea(new Dimension(0, 0)));
		presetsBox.add(equalizerCheckbox);

		Box vbpBox = new Box(BoxLayout.Y_AXIS);
		presetsBox.setAlignmentX(0.0f);
		volumeBox.setAlignmentX(0.0f);
		balanceBox.setAlignmentX(0.0f);
		vbpBox.add(Box.createVerticalGlue());
		vbpBox.add(volumeBox);
		vbpBox.add(balanceBox);
		vbpBox.add(presetsBox);
		vbpBox.add(Box.createVerticalGlue());
//vbpBox.setBorder(new LineBorder(Color.blue));

		Box lvbpBox = new Box(BoxLayout.X_AXIS);
		lvbpBox.add(Box.createHorizontalGlue());
		lvbpBox.add(logoButton);
		lvbpBox.add(Box.createHorizontalGlue());
		lvbpBox.add(vbpBox);
		lvbpBox.add(Box.createHorizontalGlue());
//lvbpBox.setBorder(new LineBorder(Color.green));

		// Equalizer
		Box equalizerBox = new Box(BoxLayout.X_AXIS);
		equalizerBox.add(Box.createRigidArea(new Dimension(0, 0)));
		equalizerBox.add(Box.createHorizontalGlue());
		for (int i = 0; i < equalizerSliders.size(); i++) {
			JSlider slider = equalizerSliders.get(i);
			equalizerBox.add(slider);
			equalizerBox.add(Box.createHorizontalGlue());
		}
		equalizerBox.add(Box.createRigidArea(new Dimension(0, 0)));
//equalizerBox.setBorder(new LineBorder(Color.red));

		Box controlBox2 = new Box(BoxLayout.X_AXIS);
		if (layout == 1 || layout == 4) {
			controlBox2.add(Box.createHorizontalGlue());
			controlBox2.add(lvbpBox);
			controlBox2.add(Box.createHorizontalGlue());
			controlBox2.add(equalizerBox);
			controlBox2.add(Box.createHorizontalGlue());
		} else if (layout == 2) {
			controlBox2 = new Box(BoxLayout.Y_AXIS);
			controlBox2.add(lvbpBox);
			controlBox2.add(equalizerBox);
//		} else if (layout == 4) {
//			controlBox2.add(lvbpBox);
		}

		showCurrentTrackButton.setBackground(currentTrackColor);
		showNextTrackButton.setBackground(nextTrackColor);
		showNextCortinaButton.setBackground(nextCortinaColor);
		showNextTandaButton.setBackground(nextTandaColor);
		showCurrentTrackButton.setBorder(new EmptyBorder(8, 0, 8, 0)); // top, left, bottom, right
		showNextTrackButton.setBorder(new EmptyBorder(8, 0, 8, 0));
		showNextCortinaButton.setBorder(new EmptyBorder(8, 0, 8, 0));
		showNextTandaButton.setBorder(new EmptyBorder(8, 0, 8, 0));
		JPanel buttonPanel = new JPanel(new GridLayout(1, 3));
		buttonPanel.add(showCurrentTrackButton);
		buttonPanel.add(showNextTrackButton);
		buttonPanel.add(showNextCortinaButton);
		buttonPanel.add(showNextTandaButton);
		showCurrentTrackButton.setEnabled(false);
		showNextTrackButton.setEnabled(false);
		showNextCortinaButton.setEnabled(false);
		showNextTandaButton.setEnabled(false);

		JPanel leftPanel = new JPanel(new BorderLayout());
		JScrollPane treeScrollPane = new JScrollPane(tree);
		leftPanel.add(treeScrollPane);
//		leftPanel.add(showCurrentTrackButton, BorderLayout.SOUTH);
		leftPanel.add(buttonPanel, BorderLayout.SOUTH);

		JPanel rightPanel = new JPanel(new BorderLayout());
		JScrollPane tableScrollPane = new JScrollPane(table);

		JPanel infoSearchPanel = new JPanel(new BorderLayout());
		infoSearchPanel.add(statusLabel);
		infoSearchPanel.add(searchField, BorderLayout.EAST);
		rightPanel.add(tableScrollPane);
		rightPanel.add(infoSearchPanel, BorderLayout.SOUTH);
		if (layout == 3) {
			JPanel p = rightPanel;
			rightPanel = new JPanel(new BorderLayout());
			rightPanel.add(p);
			rightPanel.add(controlBox2, BorderLayout.SOUTH);
		}
//		if (layout == 4) {
//			JPanel p = rightPanel;
//			rightPanel = new JPanel(new BorderLayout());
//			rightPanel.add(p);
//			rightPanel.add(equalizerBox, BorderLayout.SOUTH);
//		}

		splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
		splitPane.setDividerSize(4);

		JPanel topBox = new JPanel(new BorderLayout());
		topBox.add(controlBox1);
		contentPane.add(topBox, BorderLayout.NORTH);
		if (layout == 1) {
			topBox.add(controlBox2, BorderLayout.EAST);
		} else if (layout == 2) {
			leftPanel.add(controlBox2, BorderLayout.SOUTH);
		} else if (layout == 3) {
			leftPanel.add(lvbpBox, BorderLayout.SOUTH);
			equalizerBox.setBorder(new BevelBorder(BevelBorder.RAISED));
			contentPane.add(equalizerBox, BorderLayout.SOUTH);
		}
		if (layout == 4) {
			controlBox2.setBorder(new BevelBorder(BevelBorder.LOWERED));
			contentPane.add(controlBox2, BorderLayout.SOUTH);
		}
		contentPane.add(splitPane);
	}

	// Pause the player and update play button icon accordingly
	private void setPaused(boolean value) {
		Player.setPaused(value);
		updateProjector();
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				if (Player.isPaused()) {
					playButton.setIcon(new ImageIcon(Panda.class.getResource("panda-play-032.png")));
					playButton.setRolloverIcon(new ImageIcon(Panda.class.getResource("panda-play-rollover-032.png")));
					playButton.setSelectedIcon(new ImageIcon(Panda.class.getResource("panda-play-rollover-032.png")));
					playButton.setPressedIcon(new ImageIcon(Panda.class.getResource("panda-play-pressed-032.png")));
				} else {
					playButton.setIcon(new ImageIcon(Panda.class.getResource("panda-pause-032.png")));
					playButton.setRolloverIcon(new ImageIcon(Panda.class.getResource("panda-pause-rollover-032.png")));
					playButton.setSelectedIcon(new ImageIcon(Panda.class.getResource("panda-pause-rollover-032.png")));
					playButton.setPressedIcon(new ImageIcon(Panda.class.getResource("panda-pause-pressed-032.png")));
				}
			}
		});
	}

	private void addListeners() {
		// A global key listener
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
			@Override
			public boolean dispatchKeyEvent(KeyEvent e) {
				int keyCode = e.getKeyCode();
				int modifiers = e.getModifiers();
				int id = e.getID();
				if (id == KeyEvent.KEY_RELEASED) {
					if (modifiers == defaultModifier) {
						if (keyCode == KeyEvent.VK_C) {
System.out.println("*** CTRL-C");
							Util.log(Level.FINE, "dispatchKeyEvent: CTRL-C");
							showCurrentTrack();
						} else if (keyCode == KeyEvent.VK_N) {
System.out.println("*** CTRL-N");
							Util.log(Level.FINE, "dispatchKeyEvent: CTRL-N");
							showNextTrack();
						}
					}
					if (keyCode == KeyEvent.VK_SPACE) {
System.out.println("*** SPACE");
						Util.log(Level.FINE, "dispatchKeyEvent: SPACE");
					}
				}
				return false;
			}
		});
		logoButton.addMouseListener(new MouseAdapter() {
			// Show popup menu when button is clicked - regardless of whether or not it is a popup trigger
			public void mouseClicked(MouseEvent e) {
				logoPopupMenu.show(logoButton, e.getX(), e.getY());
			}
		});
		table.addMouseListener(new MouseAdapter() {
			// Show popup menu when button is clicked
			// but only if it is a popup trigger AND the mouse was on a selected row
			public void mouseClicked(MouseEvent e) {
				maybeShowPopup(e);
			}
			public void mousePressed(MouseEvent e) {
				maybeShowPopup(e);
			}
			public void mouseReleased(MouseEvent e) {
				maybeShowPopup(e);
			}
			private void maybeShowPopup(MouseEvent e) {
				if (!e.isPopupTrigger()) {
					return;
				}
				JTable table = (JTable) e.getSource();
				// Determine if mouse was clicked on a selected row
				boolean clickedSelectedRow = false;
				int row = table.rowAtPoint(e.getPoint());
				int[] rows = table.getSelectedRows();
				for (int i = 0; i < rows.length; i++) {
					if (row == rows[i]) {
						clickedSelectedRow = true;
						break;
					}
				}
				// By default, the play and next track/cortina/tanda menu items are disabled
				playNowMenuItem.setEnabled(false);
				nextTrackMenuItem.setEnabled(false);
				nextCortinaMenuItem.setEnabled(false);
				nextTandaMenuItem.setEnabled(false);
				if (table.getSelectedRowCount() == 1 && clickedSelectedRow) {
					// Only enable these menu items if:
					// - Exactly one row was selected
					// - The clicked row is the selected row
					// - Plus further criteria...
					if (Player.isPaused()) {
						// Only enable "play now" if the player is in paused state
						playNowMenuItem.setEnabled(true);
					}
					if (displayPlaylist != currentTrackPlaylist || row != currentTrackIndex) {
						// Only enable "next" options if this is not the current track
						nextTrackMenuItem.setEnabled(true);
						if (isCortina(row, displayPlaylist)) {
							// Only enable "next cortina" if it can be used as a cortina
							nextCortinaMenuItem.setEnabled(true);
						} else {
							// Else enable "next tanda", ie. a track can be either next cortina or next tanda, but not both.
							nextTandaMenuItem.setEnabled(true);
						}
					}
				}
				tablePopupMenu.show(table, e.getX(), e.getY());
			}
		});
		projectorMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				boolean state = projectorMenuItem.getState();
				projector.setVisible(state);
				updateProjector();
			}
		});
		quitMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Panda.this.quit();
			}
		});
		playNowMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Player.stop();
				int row = table.getSelectedRow();
				currentTrackIndex = row;
				// NOTE: We need to set next track index, as we will stop the current track so that the "next" track can play.
				nextTrackIndex = row;
				currentTrackPlaylist = displayPlaylist;
				nextTrackPlaylist = displayPlaylist;
				PandaTableModel model = (PandaTableModel) table.getModel();
				model.setValueAt(new Boolean(true), row, 0);
				table.clearSelection();
				playButton.requestFocusInWindow();
				refresh();
				updateProjector();
				setPaused(false);
				playThread.next();
			}
		});
		nextTrackMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int row = table.getSelectedRow();
				nextTrackIndex = row;
				nextTrackPlaylist = displayPlaylist;
				showNextTrackButton.setEnabled(true);
				PandaTableModel model = (PandaTableModel) table.getModel();
				model.setValueAt(new Boolean(true), row, 0);
				table.clearSelection();
				playButton.requestFocusInWindow();
				refresh();
				updateProjector();
			}
		});
		nextCortinaMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int row = table.getSelectedRow();
				// If the current track is the last track in a tanda, then the next cortina immediately becomes the next track
				if (isLastTrackInTanda(currentTrackIndex, currentTrackPlaylist)) {
					nextTrackIndex = row;
					nextTrackPlaylist = displayPlaylist;
					showNextTrackButton.setEnabled(true);
				}
				// But we stil retain the next cortina...
				nextCortinaIndex = row;
				nextCortinaPlaylist = displayPlaylist;
				showNextCortinaButton.setEnabled(true);
				PandaTableModel model = (PandaTableModel) table.getModel();
				model.setValueAt(new Boolean(true), row, 0);
				table.clearSelection();
				playButton.requestFocusInWindow();
				refresh();
				updateProjector();
			}
		});
		nextTandaMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int row = table.getSelectedRow();
				// If the current track is a cortina, then the next tanda immediately becomes the next track
				Track track = currentTrackPlaylist.get(currentTrackIndex);
				String genre = track.getTag("genre");
				if (genre == null || !Util.projectorGenres.contains(genre)) {
					nextTrackIndex = row;
					nextTrackPlaylist = displayPlaylist;
					showNextTrackButton.setEnabled(true);
				} else {
					nextTandaIndex = row;
					nextTandaPlaylist = displayPlaylist;
					showNextTandaButton.setEnabled(true);
				}
				PandaTableModel model = (PandaTableModel) table.getModel();
				model.setValueAt(new Boolean(true), row, 0);
				table.clearSelection();
				playButton.requestFocusInWindow();
				refresh();
				updateProjector();
			}
		});
		showCurrentTrackButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				showCurrentTrack();
			}
		});
		showNextTrackButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				showNextTrack();
			}
		});
		showNextCortinaButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				showNextCortina();
			}
		});
		showNextTandaButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				showNextTanda();
			}
		});
		selectAllMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				table.selectAll();
			}
		});
		selectNoneMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				table.clearSelection();
				playButton.requestFocusInWindow();
			}
		});
		playButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setPaused(Player.isPaused() ? false : true);
			}
		});
		prevButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				playThread.prev();
			}
		});
		nextButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				playThread.next();
			}
		});
		positionSlider.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				int newPosition = positionSlider.getValue();
				if (settingPosition) {
					settingPosition = false;
					prevPosition = newPosition;
					return;
				}
				if (newPosition == prevPosition) {
					// Prevent jumping caused by setting to same position in rapid succession
					return;
				}
				if (currentTrackIndex >= 0) {
					Track track = currentTrackPlaylist.get(currentTrackIndex);
					Player player = track.getPlayer();
					int position = player.getPosition();
					if (newPosition != position) {
						player.setPosition(newPosition);
						prevPosition = newPosition;
					}
					// Update left and right labels to reflect current slider position - even if player is paused!
					if (Player.isPaused()) {
						int duration = player.getDuration();
						// Note: Here we are displaying the position of the slider - not the player.
						position = newPosition;
						leftLabel.setText(Util.minutesSeconds(newPosition));
						rightLabel.setText(Util.minutesSeconds(duration - newPosition));
					}
				}
			}
		});
		volumeSlider.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				int newValue = volumeSlider.getValue();
				Player.setVolume(newValue);
				volumeLabel.setText(Util.decibels(Player.getGain()));
			}
		});
		balanceSlider.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				int newValue = balanceSlider.getValue();
				Player.setBalance(newValue);
				balanceLabel.setText(Util.balance(Player.getBalance()));
			}
		});
		for (int i = 0; i < equalizerSliders.size(); i++) {
			final EQSlider slider = equalizerSliders.get(i);
			slider.addChangeListener(new ChangeListener() {
				public void stateChanged(ChangeEvent e) {
					// First determine which band needs to be set
					int band = -1;
					for (int i = 0; i < equalizerSliders.size(); i++) {
						EQSlider s = equalizerSliders.get(i);
						if (s == slider) {
							band = i;
							break;
						}
					}
					if (band >= 0) {
						Player.setEqualizer(band, slider.getValue());
					}
					if (slider.adjusting) {
						// Slider value was changed due to preset being selected, so don't alter custom preset values - simply reset it.
						slider.adjusting = false;
					} else {
						// Change is due to user adjusting individual slider, so make sure that ALL custom preset values reflect what is currently set.
						for (int i = 0; i < equalizerSliders.size(); i++) {
							EQSlider s = equalizerSliders.get(i);
							int v = s.getValue();
							customPresets[i] = v;
						}
						// And make sure that custom preset is selected
						equalizerPresets.setSelectedItem("Custom");
					}
				}
			});
		}
		equalizerPresets.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String s = (String) equalizerPresets.getSelectedItem();
				int[] presets = new int[BANDS];
				if (s.equals("None")) {
					// Default values of zero will be used
				} else if (s.equals("Bass +")) {
					if (BANDS == 10) {
						presets = Presets.PRESETS_10_BASS_PLUS;
					} else if (BANDS == 15) {
						presets = Presets.PRESETS_15_BASS_PLUS;
					} else if (BANDS == 25) {
						presets = Presets.PRESETS_25_BASS_PLUS;
					} else if (BANDS == 31) {
						presets = Presets.PRESETS_31_BASS_PLUS;
					}
				} else if (s.equals("Bass -")) {
					if (BANDS == 10) {
						presets = Presets.PRESETS_10_BASS_MINUS;
					} else if (BANDS == 15) {
						presets = Presets.PRESETS_15_BASS_MINUS;
					} else if (BANDS == 25) {
						presets = Presets.PRESETS_25_BASS_MINUS;
					} else if (BANDS == 31) {
						presets = Presets.PRESETS_31_BASS_MINUS;
					}
				} else if (s.equals("Mid +")) {
					if (BANDS == 10) {
						presets = Presets.PRESETS_10_MID_PLUS;
					} else if (BANDS == 15) {
						presets = Presets.PRESETS_15_MID_PLUS;
					} else if (BANDS == 25) {
						presets = Presets.PRESETS_25_MID_PLUS;
					} else if (BANDS == 31) {
						presets = Presets.PRESETS_31_MID_PLUS;
					}
				} else if (s.equals("Mid -")) {
					if (BANDS == 10) {
						presets = Presets.PRESETS_10_MID_MINUS;
					} else if (BANDS == 15) {
						presets = Presets.PRESETS_15_MID_MINUS;
					} else if (BANDS == 25) {
						presets = Presets.PRESETS_25_MID_MINUS;
					} else if (BANDS == 31) {
						presets = Presets.PRESETS_31_MID_MINUS;
					}
				} else if (s.equals("Treble +")) {
					if (BANDS == 10) {
						presets = Presets.PRESETS_10_TREBLE_PLUS;
					} else if (BANDS == 15) {
						presets = Presets.PRESETS_15_TREBLE_PLUS;
					} else if (BANDS == 25) {
						presets = Presets.PRESETS_25_TREBLE_PLUS;
					} else if (BANDS == 31) {
						presets = Presets.PRESETS_31_TREBLE_PLUS;
					}
				} else if (s.equals("Treble -")) {
					if (BANDS == 10) {
						presets = Presets.PRESETS_10_TREBLE_MINUS;
					} else if (BANDS == 15) {
						presets = Presets.PRESETS_15_TREBLE_MINUS;
					} else if (BANDS == 25) {
						presets = Presets.PRESETS_25_TREBLE_MINUS;
					} else if (BANDS == 31) {
						presets = Presets.PRESETS_31_TREBLE_MINUS;
					}
				} else if (s.equals("Custom")) {
					presets = customPresets;
				}
				for (int i = 0; i < BANDS; i++) {
					EQSlider slider = equalizerSliders.get(i);
					// NOTE: Call adjustValue instead of setValue in order to flag that the slider is being set due to preset selection and not by user directly moving the slider
					slider.adjustValue(presets[i]);
				}
			}
		});
		equalizerCheckbox.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				Player.setEqualizerEnabled(equalizerCheckbox.isSelected());
				if (equalizerCheckbox.isSelected()) {
					equalizerCheckbox.setToolTipText("Disable equalizer");
				} else {
					equalizerCheckbox.setToolTipText("Enable equalizer");
				}
			}
		});
		tree.addTreeSelectionListener(new TreeSelectionListener() {
			public void valueChanged(TreeSelectionEvent e) {
				DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
				if (node == null) {
					return;
				}
				if (!node.isLeaf()) {
					// TODO: Either display empty table, or display ALL tracks in ALL playlists under this folder... (like iTunes)
					Panda.this.displayPlaylist = emptyPlaylist;
					table.setModel(new PandaTableModel(Panda.this.displayPlaylist));
					return;
				}
				String playlist = (String) node.getUserObject();
				while (true) {
					node = (DefaultMutableTreeNode) node.getParent();
					if (node == null) {
						break;
					}
					//playlist = (String) node.getUserObject() + "/" + playlist;
					String s = (String) node.getUserObject();
					if (s.equals("Root")) {
						break;
					}
					playlist = s + "/" + playlist;
				}
				Util.log(Level.FINE, "TreeSelectionListener.valueChanged: Selected playlist = " + playlist);	
				Panda.this.displayPlaylist = playlistMap.get(playlist);
				table.setModel(new PandaTableModel(Panda.this.displayPlaylist));
				refresh();
			}
		});
		searchField.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String text = searchField.getText();
				text = text.trim();
//System.out.println("*** Search: [" + text + "]");
				if (text.equals("")) {
					if (Panda.this.holdPlaylist == null) {
						// Nothing to do
						return;
					}
					// Restore table to original contents before search was done
					Panda.this.displayPlaylist = Panda.this.holdPlaylist;
					table.setModel(new PandaTableModel(Panda.this.displayPlaylist));
					Panda.this.holdPlaylist = null;
					return;
				}
				String field = null;
				String regex = text;
				int index = text.indexOf("=");
				if (index > 0 && index < text.length() - 1) {
					field = text.substring(0, index);
					field = field.trim();
					regex = text.substring(index + 1);
					regex = regex.trim();
				}
				regex = ".*" + regex + ".*";
				if (Panda.this.holdPlaylist == null) {
					Panda.this.holdPlaylist = Panda.this.displayPlaylist;
				}
				List<Track> matchingPlaylist = new ArrayList<Track>();
				for (Track track : Panda.this.holdPlaylist) {
					if (track.matches(field, regex)) {
						matchingPlaylist.add(track);
					}
				}
				Panda.this.displayPlaylist = matchingPlaylist;
				table.setModel(new PandaTableModel(Panda.this.displayPlaylist));
				return;
			}
		});
	}

	private void showCurrentTrack() {
		if (displayPlaylist != currentTrackPlaylist) {
			displayPlaylist = currentTrackPlaylist;
			table.setModel(new PandaTableModel(Panda.this.displayPlaylist));
			refresh();
		}
		displayTrack(currentTrackIndex);
	}

	private void showNextTrack() {
		if (nextTrackPlaylist == null || nextTrackIndex < 0) {
			return;
		}
		displayPlaylist = nextTrackPlaylist;
		table.setModel(new PandaTableModel(Panda.this.displayPlaylist));
		refresh();
		displayTrack(nextTrackIndex);
	}

	private void showNextCortina() {
		if (nextCortinaPlaylist == null || nextCortinaIndex < 0) {
			return;
		}
		displayPlaylist = nextCortinaPlaylist;
		table.setModel(new PandaTableModel(Panda.this.displayPlaylist));
		refresh();
		displayTrack(nextCortinaIndex);
	}

	private void showNextTanda() {
		if (nextTandaPlaylist == null || nextTandaIndex < 0) {
			return;
		}
		displayPlaylist = nextTandaPlaylist;
		table.setModel(new PandaTableModel(Panda.this.displayPlaylist));
		refresh();
		displayTrack(nextTandaIndex);
	}

	private void displayTrack(int index) {
		if (index < 0) {
			return;
		}
		if (!(table.getParent() instanceof JViewport)) {
			return;
		}
		JViewport viewport = (JViewport) table.getParent();
		Rectangle rectangle = table.getCellRect(index, 0, true);
		Point point = viewport.getViewPosition();
		rectangle.setLocation(rectangle.x - point.x, rectangle.y - point.y);
		viewport.scrollRectToVisible(rectangle);

		// Ensure that the displayed playlist is selected in the tree
		// First obtain path for display playlist
		String path = null;
		Set set = playlistMap.keySet();
		Iterator it = set.iterator();
		while (it.hasNext()) {
			String key = (String) it.next();
			List<Track> playlist = playlistMap.get(key);
			if (playlist == displayPlaylist) {
				path = key;
			}
		}
		if (path != null) {
			// Then obtain the index and select that row in the tree
			index = getIndexForPath(path);
			Util.log(Level.FINE, "dispatchKeyEvent: path=" + path + ", index=" + index);
			if (index >= 0) {
				// Need to make sure that all nodes in the path are expanded
				for (int i = 0; i <= index; i++) {
					tree.expandRow(i);
				}
				// TODO: Somehow avoid expanding nodes that aren't relevant
				//       ie. expand backwards until we reach the top level, then stop.
				//       I thought this would work, but the index doesn't correspond if there are collapsed nodes above...
				//TreePath treePath = tree.getPathForRow(index);
				//tree.expandPath(treePath);
				tree.setSelectionRow(index);
				tree.scrollRowToVisible(index);
			}
		}
	}

	// Returns the index of the first checked track that follows the specified index in the specified playlist
	// If no checked track is found, return -1
	private int findNextCheckedTrackIndex(int index, List<Track> playlist) {
		int next = -1;
		for (int i = index + 1; i < playlist.size(); i++) {
			Track track = playlist.get(i);
			if (track.isChecked()) {
				next = i;
				break;
			}
		}
		Util.log(Level.FINE, "findNextCheckedTrackIndex: index=" + index + ", next=" + next);
		return next;
	}

	// Returns true if the specified track and the one before it are both of the same, configured genre
	// AND either there is no next track OR the next track is of a different genre
	private boolean isLastTrackInTanda(int index, List<Track> playlist) {
		if (index <= 0 || index >= playlist.size()) {
			return false;
		}
		Track track = playlist.get(index);
		String genre = track.getTag("genre");
		if (genre == null || !Util.projectorGenres.contains(genre)) {
			return false;
		}
		Track previousTrack = playlist.get(index - 1);
		String previousGenre = previousTrack.getTag("genre");
		if (!genre.equals(previousGenre)) {
			return false;
		}
		if (nextTrackPlaylist == null || nextTrackIndex < 0) {
			return true;
		}
		Track nextTrack = nextTrackPlaylist.get(nextTrackIndex);
		String nextGenre = nextTrack.getTag("genre");
		if (!genre.equals(nextGenre)) {
			return true;
		}
		return false;
	}


	// Returns true if the specified track does not have a genre that is one of the "projector" genres.
	private boolean isCortina(int index, List<Track> playlist) {
		if (index < 0 || index >= playlist.size()) {
			return true;
		}
		Track track = playlist.get(index);
		String genre = track.getTag("genre");
		if (genre == null) {
			return true;
		}
		if (Util.projectorGenres.contains(genre)) {
			return false;
		}
		return true;
	}

	// Returns the first checked track that precedes the specified index in the *current* playlist
	// If no checked track is found, return the index (ie. remain at the same track where we are)
	private int findPrevCheckedTrackIndex(int index) {
		if (index < 0) {
			return -1;
		}
		int prev = index;
		for (int i = index - 1; i >= 0; i--) {
			Track track = currentTrackPlaylist.get(i);
			if (track.isChecked()) {
				prev = i;
				break;
			}
		}
		Util.log(Level.FINE, "findPrevCheckedTrackIndex: index=" + index + ", prev=" + prev);
		return prev;
	}

	private void updateProjector() {
		if (projector == null) {
			return;
		}
		if (currentTrackIndex < 0) {
			projector.setDefaults();
			return;
		}
		boolean useDefaults = true;
		Track track = currentTrackPlaylist.get(currentTrackIndex);
		String genre = track.getTag("genre");
		if (genre != null && Util.projectorGenres.contains(genre)) {
			Player player = track.getPlayer();
			if (player != null && !player.isPaused()) {
				// Only display track info if current track is actually playing one of the configured genres
				useDefaults = false;
			}
		}
		if (useDefaults) {
			projector.setDefaults();
			if (!Util.projectorGenres.contains(genre)) {
				projector.setFooter(nextTanda());
			}
			return;
		}
		String orchestra = track.getTag("orchestra");
		String header = System.getProperty("panda.projector.map." + orchestra);
		if (header == null) {
			header = orchestra;
		//} else {
		//	header = Util.replaceSpecialChars(header);
		}
		projector.setHeader(header);
		projector.setImage("orchestra/" + header + ".jpg");
		String year = track.getTag("year");
		projector.setText(track.getTitle(), year);
		projector.setDefaultFooter();
		projector.repaint();
	}

	// Determine the genre and orchestra of the next tanda.
	// ie. the set of 3 or more checked tracks that follow a checked cortina
	// and all belong to the same, configured genre.
	// Note that any track that isn't one of the configured genres is considered as a cortina
	// TODO: Also indicate if current track is number x out of n tracks in the tanda...
	private String nextTanda() {
		String string = "";
		if (nextTrackIndex < 0) {
			return string;
		}
		boolean cortinaFound = false;
		String currentGenre = "";
		if (currentTrackPlaylist != null && currentTrackIndex >= 0) {
			Track track = currentTrackPlaylist.get(currentTrackIndex);
			String g = track.getTag("genre");
			if (g != null) {
				currentGenre = g;
			}
		}
		if (!Util.projectorGenres.contains(currentGenre)) {
			cortinaFound = true;
		}
		List<Track> playlist = nextTrackPlaylist;
		if (playlist == null) {
			playlist = currentTrackPlaylist;
			if (playlist == null) {
				return string;
			}
		}
		int count = 0;
		String genre = null;
		String orchestra = null;
		for (int i = nextTrackIndex; i < playlist.size(); i++) {
			Track track = playlist.get(i);
			if (!track.isChecked()) {
				// Skip over any unchecked tracks
				continue;
			}
			String g = track.getTag("genre");
			if (g == null) {
				continue;
			}
			if (!Util.projectorGenres.contains(g)) {
				cortinaFound = true;
				continue;
			}
			if (!cortinaFound) {
				// Don't bother with anything else until a cortina is found
				continue;
			}
			// A cortina has been found, so the next 3 tracks must all be of the same (configured) genre...
			if (!Util.projectorGenres.contains(g)) {
				genre = null;
				break;
			}
			if (genre == null) {
				genre = g;
				count = 1;
				continue;
			}
			if (!g.equals(genre)) {
				genre = null;
				break;
			}
			String o = track.getTag("orchestra");
			if (o == null || orchestra != null && !o.equals(orchestra)) {
				orchestra = "Mixed";
			} else {
				orchestra = o;
			}
			count++;
			if (count >= 3) {
				break;
			}
		}
		if (genre != null && genre.length() > 0 && orchestra != null && orchestra.length() > 0) {
			String s = System.getProperty("panda.projector.map." + orchestra);
			if (s != null) {
				orchestra = s;
			}
			//string = "Next Tanda:  " + genre + " - " + orchestra;
			//string = "Next Tanda:  " + orchestra + " (" + genre + ")";
			string = "Next:  " + orchestra + " (" + genre + ")";
		}
		return string;
	}

	private void refresh() {
		tree.repaint();
		table.repaint();
	}

	private void quit() {
		int n = JOptionPane.showConfirmDialog(Panda.this, "Really quit?", "Panda", JOptionPane.YES_NO_OPTION);
		if (n == JOptionPane.YES_OPTION) {
			save();
			cleanup();
			System.exit(0);
		}
	}

	class SaveThread extends Thread {
		public SaveThread() {
			setDaemon(true);
			setPriority(Thread.MIN_PRIORITY);
		}

		public void run() {
			Util.log(Level.INFO, "Starting save thread...");
			while (true) {
				// Save every 10 minutes, starting immediately (so we have backups from the start)
				save();
				Util.pause(10 * 60 * 1000);
			}
		}
	}

	// Save playlists and tags (including backup copies)
	private void save() {
		Util.log(Level.FINE, "Saving...");
		DateFormat df = new SimpleDateFormat("yyyyMMdd.HHmmss");
		String timestamp = df.format(new Date());
		try {
			savePlaylists(timestamp);
			saveTags(timestamp);
		} catch (IOException ioe) {
			Util.log(Level.SEVERE, "Error saving playlists!" + " (" + ioe.getMessage() + ")");
		}
	}

	private void savePlaylists(String timestamp) throws IOException {
		File file = new File(Util.PANDA_HOME + "panda.playlists");
		File backup = new File(Util.PANDA_HOME + "bkp" + File.separator + "panda.playlists." + timestamp);
		savePlaylists(file, backup);
	}

	private void saveTags(String timestamp) throws IOException {
		File file = new File(Util.PANDA_HOME + "panda.tags");
		File backup = new File(Util.PANDA_HOME + "bkp" + File.separator + "panda.tags." + timestamp);
		saveTags(file, backup);
	}

	private void savePlaylists(File file, File backup) throws IOException {
		Util.backup(file, backup, 1);
		long oldLength = file.length();
		PrintWriter pw = new PrintWriter(file);
		pw.println("#============ Panda playlists ============");
		for (String playlist : playlists) {
			if (playlist.equals("Tracks")) {
				continue;
			}
			List<Track> tracks = playlistMap.get(playlist);
			if (tracks.isEmpty()) {
				// Don't save empty playlists (multiple restarts can lead to empty history playlists)
				continue;
			}
			pw.println("");
			pw.println("#-------------------------------------------------------------------------------");
			pw.println("playlist=" + playlist);
			for (Track track : tracks) {
				pw.println(track.getFilename());
			}
			pw.flush();
		}
		pw.close();
		long newLength = file.length();
		if (oldLength != newLength) {
			// File has changed since it was last saved, so copy it to the specified backup file
			Util.copy(file, backup);
		}
	}

	private void saveTags(File file, File backup) throws IOException {
		Util.backup(file, backup, 1);
		long oldLength = file.length();
		PrintWriter pw = new PrintWriter(file);
		pw.println("#============ Panda tags ============");
		pw.println("");
		for (String filename : filenames) {
			Track track = trackMap.get(filename);
			String title = track.getTitle();
			Map<String, String> tags = track.getTags();
			if (title.equals(filename) && tags.isEmpty()) {
				// No tags configured for this file
				continue;
			}
			pw.println("#-------------------------------------------------------------------------------");
			pw.println("file=" + filename);
			pw.println("title=" + track.getTitle());
			Set set = tags.keySet();
			Iterator it = set.iterator();
			while (it.hasNext()) {
				String key = (String) it.next();
				String value = (String) tags.get(key);
				pw.println(key + "=" + value);
			}
			pw.println("");
			pw.flush();
		}
		pw.close();
		long newLength = file.length();
		if (oldLength != newLength) {
			// File has changed since it was last saved, so copy it to the specified backup file
			Util.copy(file, backup);
		}
	}

	// Removes duplicate backup files
	private void cleanup() {
		Util.log(Level.FINE, "Cleaning up...");
		File dir = new File(Util.PANDA_HOME + "bkp");
		File[] filesArray = dir.listFiles();
		List<File> files = Arrays.asList(filesArray);
		Collections.sort(files);
		File previousFile = null;
		for (File file : files) {
			String filename = file.getName();
			String basename = stripTimestamp(filename);
			if (basename.equals(filename)) {
				// Ignore files that might end up here not conforming to expectations
				continue;
			}
			if (previousFile == null) {
				previousFile = file;
				continue;
			}
			String previousFilename = previousFile.getName();
			String previousBasename = stripTimestamp(previousFilename);
			if (basename.equals(previousBasename) && file.length() == previousFile.length()) {
				// Basenames match and files have same length, so delete the older one
				previousFile.delete();
			}
			previousFile = file;
		}
	}

	// Strips the timestamp off a backup filename, which must start with "panda." and contain a timestamp
	// Eg: panda.playlists.20161123.185820 returns panda.playlists
	// If doesn't start with "panda." or cannot be stripped for whatever reason, then simply return the filename.
	private String stripTimestamp(String filename) {
		if (!filename.startsWith("panda.")) {
			return filename;
		}
		// Strip off the leading "panda."
		String string = filename.substring(6);
		int index = string.indexOf(".");
		if (index <= 0) {
			// No timestamp found
			return filename;
		}
		return filename.substring(0, index + 6);
	}

	class PlayThread extends Thread {
		private boolean proceed = true; // flag to indicate that next track must become current track

		public PlayThread() {
			setDaemon(true);
			setPriority(Thread.MAX_PRIORITY);
		}

		public void run() {
			Util.log(Level.INFO, "Starting play thread...");
			// Start off in paused state
			setPaused(true);
			while (true) {
				// Wait until current track index is set...
				while (currentTrackIndex < 0) {
					Util.pause(1000);
				}
				showCurrentTrackButton.setEnabled(true);
				// By default, the next track is the following unchecked track in the current playlist
				nextTrackPlaylist = currentTrackPlaylist;
				nextTrackIndex = findNextCheckedTrackIndex(currentTrackIndex, currentTrackPlaylist);
				// Unless the current track is the last track in the tanda,
				// and a "next cortina" has been specified, then it becomes the next track
				if (isLastTrackInTanda(currentTrackIndex, currentTrackPlaylist)) {
					if (nextCortinaPlaylist != null && nextCortinaIndex >= 0) {
						nextTrackPlaylist = nextCortinaPlaylist;
						nextTrackIndex = nextCortinaIndex;
					}
				}
				// Or if the current track is a cortina and a "next tanda" has been specified, then it becomes the next track
				if (isCortina(currentTrackIndex, currentTrackPlaylist)) {
					if (nextTandaPlaylist != null && nextTandaIndex >= 0) {
						nextTrackPlaylist = nextTandaPlaylist;
						nextTrackIndex = nextTandaIndex;
					}
				}
				showNextTrackButton.setEnabled(nextTrackIndex < 0 ? false : true);
				if ((nextTandaPlaylist == currentTrackPlaylist && nextTandaIndex == currentTrackIndex)  || (nextTandaPlaylist == nextTrackPlaylist && nextTandaIndex == nextTrackIndex)) {
					// The "next tanda" is now either the current or next track, so reset it
					nextTandaPlaylist = null;
					nextTandaIndex = -1;
					showNextTandaButton.setEnabled(false);
				}
				Track track = currentTrackPlaylist.get(currentTrackIndex);
				historyPlaylist.add(track);
				// Current and next tracks must be highlighted in UI, and history playlist must show current track
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						refresh();
					}
				});
				final Player player = track.getPlayer();
				updateProjector();
				try {
					player.play();
					// Wait specified number of seconds before starting the next track
					// But only if the end of the track has been reached during play.
					// ie. not if the "Next" button or "Play Now" menu has been clicked.
					if (WAIT > 0 && player.getDuration() == player.getPosition()) {
						Util.pause(1000 * WAIT);
					}
					if (proceed) {
						if (nextTrackIndex >= 0 && nextTrackIndex < nextTrackPlaylist.size()) {
							currentTrackPlaylist = nextTrackPlaylist;
							currentTrackIndex = nextTrackIndex;
						} else {
							// Reached end of playlist, so restore state to how it was at the start
							currentTrackIndex = -1;
							nextTrackIndex = -1;
							nextCortinaIndex = -1;
							nextTandaIndex = -1;
							showCurrentTrackButton.setEnabled(false);
							showNextTrackButton.setEnabled(false);
							showNextCortinaButton.setEnabled(false);
							showNextTandaButton.setEnabled(false);
							setPaused(true);
							settingPosition = true;
						}
					} else {
						proceed = true;
					}
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							refresh();
						}
					});
				} catch (IOException ioe) {
					Util.log(Level.SEVERE, "Error while playing track " + currentTrackPlaylist.get(currentTrackIndex) + ": " + ioe);
				} catch (UnsupportedAudioFileException uafe) {
					Util.log(Level.SEVERE, "Error while playing track " + currentTrackPlaylist.get(currentTrackIndex) + ": " + uafe);
				} catch (LineUnavailableException lue) {
					Util.log(Level.SEVERE, "Error while playing track " + currentTrackPlaylist.get(currentTrackIndex) + ": " + lue);
				}
			}
		}

		synchronized void prev() {
			Track track = currentTrackPlaylist.get(currentTrackIndex);
			if (track != null) {
				Player player = track.getPlayer();
				int position = player.getPosition();
				if (position < 4) {
					// We are less than 4 seconds into a track which is not the first track, so go to previous track
					currentTrackIndex = findPrevCheckedTrackIndex(currentTrackIndex);
				}
			}
			proceed = false;
			Player.stop();
		}

		synchronized void next() {
			settingPosition = true;
			proceed = true;
			Player.stop();
			settingPosition = true;
			// Not sure if I want this...
			// iTunes always changes the display to show the track when it starts playing,
			// but I find it annoying when it does that while I'm busy.
			//showNextTrack();
		}
	}

	class PandaTreeCellRenderer implements TreeCellRenderer {
		JPanel panel;
		JLabel label;
		Color backgroundSelectionColor;
		Color backgroundNonSelectionColor;
	
		public PandaTreeCellRenderer() {
			panel = new JPanel(new GridLayout(1, 1));
			label = new JLabel(" ");
			panel.add(label);
			panel.setBorder(new EmptyBorder(2, 4, 2, 4)); // top, left, bottom, right
			DefaultTreeCellRenderer defaultRenderer = new DefaultTreeCellRenderer();
			backgroundSelectionColor = defaultRenderer.getBackgroundSelectionColor();
			backgroundNonSelectionColor = defaultRenderer.getBackgroundNonSelectionColor();
		}
	
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
			Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
			String name = (String) userObject;
			label.setText(name);
			panel.setBackground(backgroundNonSelectionColor);
			label.setBackground(backgroundNonSelectionColor);
			if (selected) {
				panel.setBackground(backgroundSelectionColor);
				label.setBackground(backgroundSelectionColor);
			}
			if (leaf && currentTrackIndex >= 0) {
				setColor(name, nextTandaPlaylist, nextTandaColor);
				setColor(name, nextCortinaPlaylist, nextCortinaColor);
				setColor(name, nextTrackPlaylist, nextTrackColor);
				setColor(name, currentTrackPlaylist, currentTrackColor);
			}
			Util.setTextColor(label);
			panel.setEnabled(tree.isEnabled());
			return panel;
		}

		private void setColor(String name, List<Track> playlist, Color color) {
			if (playlist == null) {
				return;
			}
			String playlistName = findPlaylistName(playlist);
			if (playlistName.indexOf(name) >= 0) {
				panel.setBackground(color);
				label.setBackground(color);
			}
		}
	}

	class PandaTable extends JTable {
		public PandaTable(TableModel tableModel, TableColumnModel columnModel) {
			super(tableModel, columnModel);
			// For some reason, even though we override the prepareRenderer method,
			// we need to specify a custom renderer for the checkbox column
			// otherwise only every second row gets rendered in the Nimbus LAF
			CheckBoxRenderer checkBoxRenderer = new CheckBoxRenderer();
			this.getColumnModel().getColumn(0).setCellRenderer(checkBoxRenderer);
		}

		public Component prepareRenderer(TableCellRenderer renderer, int row, int col) {
			JComponent component = (JComponent) super.prepareRenderer(renderer, row, col);
			PandaTableModel model = (PandaTableModel) getModel();
			int modelRow = convertRowIndexToModel(row);
			int modelCol = convertColumnIndexToModel(col);
			if (isRowSelected(row) && modelCol != 0) {
				return component;
			}
			// Need to set opaque in order for background color to show
			component.setOpaque(true);
			Color color = Color.WHITE;
			String genre = model.getGenre(row);
			if (genreColors.containsKey(genre)) {
				color = genreColors.get(genre);
			}
			if (displayPlaylist == nextTandaPlaylist) {
				if (modelRow == Panda.this.nextTandaIndex) {
					color = nextTandaColor;
				}
			}
			if (displayPlaylist == nextCortinaPlaylist) {
				if (modelRow == Panda.this.nextCortinaIndex) {
					color = nextCortinaColor;
				}
			}
			if (displayPlaylist == nextTrackPlaylist) {
				if (modelRow == Panda.this.nextTrackIndex) {
					color = nextTrackColor;
				}
			}
			if (displayPlaylist == currentTrackPlaylist) {
				if (modelRow == Panda.this.currentTrackIndex) {
					color = currentTrackColor;
				}
			}
			component.setBackground(color);
			Util.setTextColor(component);
			return component;
		}
	}

	class CheckBoxRenderer extends JCheckBox implements TableCellRenderer {
		CheckBoxRenderer() {
			setHorizontalAlignment(JLabel.CENTER);
		}

		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			setSelected((value != null && ((Boolean) value).booleanValue()));
			return this;
		}
	}

	// Creates a table model for specified playlist, while sharing column layout with all other instances
	// TODO: 
	// - Order columns (by clicking column headers)
	//   NOTE: the "Time" column contains strings but must be ordered numerically...
	// - Cells (or most of them) are already editable, but how to maintain (and persist) changes?
	//   Probably need some change listener...
	// - Ordering the columns themselves, most should be re-orderable,
	//   but we want to keep first two columns ("Number" and "Selected") where they are...
	class PandaTableModel extends AbstractTableModel {
		private List<Track> playlist;

		public PandaTableModel(List<Track> playlist) {
			this.playlist = playlist;
		}

		public int getRowCount() {
			int count = playlist.size();
			return count;
		}

		public int getColumnCount() {
			int count = tableColumnModel.getColumnCount();
			return count;
		}

		public Object getValueAt(int row, int col) {
			Object object = null;
			Track track = playlist.get(row);
			// NOTE: If track is null (because the playlist configuration lists a file that cannot be found)
			//       then it will result in a null pointer exception below...
			String column = getColumnName(col);
			if (col == 0) {
				// Special column
				object = Boolean.valueOf(track.isChecked());  // TODO: NullPointerException
			} else if (column.equals("Title")) {
				object = track.getTitle(); 
			} else if (column.equals("Time")) {
				Player player = track.getPlayer();
				if (player != null) {
					int duration = player.getDuration();
					object = Util.minutesSeconds(duration); 
					// TODO: Work out how to make it numeric while still displaying string value...
				}
			} else {
				// The value for this column is contained in a tag
				String tagName = column.toLowerCase();
				int number = getColumnNumber(column);
				if (number >= 1) {
					String s = System.getProperty("panda.column." + number + ".tag.panda");
					if (s != null) {
						tagName = s;
					}
				}
				object = track.getTag(tagName); 
			}
			return object;
		}

		// Obtains the value of the "Genre" column for specified row
		public String getGenre(int row) {
			Track track = playlist.get(row);
			String genre = track.getTag("genre"); 
			return genre;
		}

		// TODO: Add a "readonly" feature where every cell is made un-editable
		public boolean isCellEditable(int row, int col) {
			boolean editable = true;
			String column = getColumnName(col);
			if (column.equals("Time")) {
				editable = false;
			}
			return editable;
		}

		// Overrides the default of using spreadsheet conventions: A, B, C... AA, AB, etc.
		public String getColumnName(int col) {
			String column = ((PandaTableColumnModel) tableColumnModel).getColumnName(col);
			return column;
		}

		// Numeric columns will be right-aligned
		// Boolean columns will render checkboxes instead of strings with value "true" or "false"
		public Class getColumnClass(int col) {
			if (col == 0) {
				// Special column
				return Boolean.class;
			}
			return getValueAt(0, col).getClass();
		}

		public void setValueAt(Object value, int row, int col) {
			Util.log(Level.FINE, "PandaTableModel.setValueAt: value=" + value + ", row=" + row + ", col=" + col);
			// TODO: this is where we need to update the underlying value in the model...
			Track track = playlist.get(row);
			if (col == 0) {
				Boolean bool = (Boolean) value;
				boolean b = bool.booleanValue();
				track.setChecked(b);
				if (!b) {
					// User unchecked a track
					if (displayPlaylist == nextTrackPlaylist && row == nextTrackIndex) {
						// It is the next track, so find another next track (if any)
						nextTrackIndex = findNextCheckedTrackIndex(nextTrackIndex, nextTrackPlaylist);
						if (nextTrackIndex < 0) {
							// No more tracks in the "next" playlist, so find one in the current playlist
							nextTrackPlaylist = currentTrackPlaylist;
							nextTrackIndex = findNextCheckedTrackIndex(currentTrackIndex, currentTrackPlaylist);
						}
					}
					if (displayPlaylist == nextCortinaPlaylist && row == nextCortinaIndex) {
						// It is the "next cortina", so reset it
						nextCortinaPlaylist = null;
						nextCortinaIndex = -1;
					}
					if (displayPlaylist == nextTandaPlaylist && row == nextTandaIndex) {
						// It is the "next tanda" track, so reset it
						nextTandaPlaylist = null;
						nextTandaIndex = -1;
					}
				} else {
					// User checked a track - if it occurs after the current track
					// and either there is no next track or the newly checked track 
					// occurs between the current track and the next track, 
					// then make it the next track
					int index = playlist.indexOf(track);
					int nextIndex = playlist.size();
					if (nextTrackIndex >= 0) {
						nextIndex = nextTrackIndex;
					}
					if (index > Panda.this.currentTrackIndex && index < nextIndex) {
						nextTrackIndex = nextIndex;
					}
				}
				refresh();
				updateProjector();
			}
			// TODO: Do we need to fire the event? What happens if we don't?
			fireTableCellUpdated(row, col);
		}
	}

	// TODO: 
	// - Do I really need to extand it? Why not just use it directly?
	//	 Well, extending it means that I can do all the initialization from the constructor
	// - Make the "Selected" column header render a checkbox so that you can select or unselect all the rows with a single click
	class PandaTableColumnModel extends DefaultTableColumnModel {
		private ArrayList<String> columns = new ArrayList<String>();

		public PandaTableColumnModel() {
			// Build up a list of columns that will be used.
			// A column is either mandatory or optional.
			// All optional columns are configurable (by definition)
			// whereas only some fields of certain mandatory columns are configurable.
			// Columns will the listed in the following order:
			// - Mandatory columns that cannot be configured
			// - Mandatory columns that are not configured (if any)
			// - The mandatory "Special" first column
			// - Any mandatory "Time" and "Title" columns if they are not configured to be in a different position
			// - Configured columns (mandatory and optional) in order of configuration
			columns.add(0, "");  // Special column
			// Flags to indicate which mandatory columns have been configured:
			boolean timeConfigured = false;
			boolean titleConfigured = false;
			for (int i = 0; ; i++) {
				String name = System.getProperty("panda.column." + (i + 1));
				if (name == null) {
					break;
				}
				columns.add(name);
				if (name.equals("Time")) {
					timeConfigured = true;
				}
				if (name.equals("Title")) {
					titleConfigured = true;
				}
			}
			if (!timeConfigured) {
				columns.add(1, "Time");
			}
			if (!titleConfigured) {
				columns.add(2, "Title");
			}
			for (int i = 0; i < columns.size(); i++) {
				String column = (String) columns.get(i);
				Util.log(Level.FINE, "*** Adding column #" + i + ": " + column);
				TableColumn tableColumn = new TableColumn(i);
				tableColumn.setHeaderValue(column);
				addColumn(tableColumn);
				int number = getColumnNumber(column);
				// Default widths with outer bounds
				int preferred = 120;
				int min = 0;
				int max = 640;
				// Column-specific default widths
				if (i == 0) {
					// Special column
					number = i;
					preferred = 40;
					min = 20;
					max = 80;
				} else if (column.equals("Time")) {
					preferred = 48;
					min = 42;
					max = 60;
				} else if (column.equals("Title")) {
					preferred = 160;
					min = 80;
				} else if (column.equals("Orchestra")) {
					preferred = 80;
					min = 80;
					max = 240;
				} else if (column.equals("Singer(s)")) {
					preferred = 160;
					min = 80;
					max = 240;
				} else if (column.equals("Genre")) {
					preferred = 80;
					min = 60;
					max = 120;
				} else if (column.equals("Year")) {
					preferred = 48;
					min = 42;
					max = 60;
				} else if (column.equals("Time")) {
					preferred = 48;
					min = 42;
					max = 60;
				} else if (column.equals("BPM")) {
					preferred = 48;
					min = 36;
					max = 60;
				} else if (column.equals("Source")) {
					preferred = 80;
					min = 60;
					max = 120;
				} else if (column.equals("Comment")) {
					preferred = 240;
					max = 1000;
				}
				// Configured widths
				if (number >= 0) {
					preferred = readIntProperty("panda.column." + number + ".width", 0, 640, preferred);
					min = readIntProperty("panda.column." + number + ".width.min", 0, 640, min);
					max = readIntProperty("panda.column." + number + ".width.max", 0, 640, max);
				}
				Util.log(Level.INFO, "Column #" + i + " using width values: preferred=" + preferred + ", min=" + min + ", max=" + max);
				tableColumn.setPreferredWidth(preferred);
				tableColumn.setMinWidth(min);
				tableColumn.setMaxWidth(max);
			}
		}

		// Returns the integer value for the number represented by the string value of the specified property.
		// If it is NOT configured or does not represent a valid number, then the default value will be returned,
		// else, if it falls ouside of the min-max range then either the min or max value will be returned,
		// else the configured value will be returned.
		// NOTE: Move to Util class if it will be used elsewhere...
		private int readIntProperty(String key, int min, int max, int def) {
			int value = def;
			String s = System.getProperty(key);
			if (s == null) {
				Util.log(Level.WARNING, "Property " + key + " not defined (Using default value " + def + ")");
				value = def;
			} else {
				try {
					value = Integer.parseInt(s);
					if (value < min) {
						Util.log(Level.WARNING, "Invalid value for property " + key + ": " + value + " (Using min value " + min + ")");
						value = min;
					} else if (value > max) {
						Util.log(Level.WARNING, "Invalid value for property " + key + ": " + value + " (Using max value " + max + ")");
						value = max;
					}
				} catch (NumberFormatException nfe) {
					Util.log(Level.WARNING, "Invalid value for property " + key + ": " + value + " (Using default value " + def + ")");
				}
			}
			Util.log(Level.FINE, "Value for property " + key + ": " + value + " (min=" + min + ", max=" + max + ", def=" + def + ")");
			return value;
		}

		public String getColumnName(int col) {
			return columns.get(col);
		}
	}
}

class ImageButton extends JButton {
	public ImageButton(String filename) {
		super(new ImageIcon(Panda.class.getResource(filename)));
		setOpaque(false);
		setContentAreaFilled(false);
		setBorderPainted(false);
	}

	public ImageButton(String text, String filename) {
		super(text, new ImageIcon(Panda.class.getResource(filename)));
		setOpaque(false);
		setContentAreaFilled(false);
		setBorderPainted(false);
	}
}

// Moves slider to exact position where mouse is clicked.
class PositionSlider extends JSlider {
	static boolean initialized;

	public PositionSlider(int orientation) {
		super(orientation);
		// Remove existing mouse listeners
		MouseListener[] listeners = getMouseListeners();
		for (MouseListener listener : listeners) {
			removeMouseListener(listener);
		}
		// Add new mouse listener to jump to position of mouse click in track
		final BasicSliderUI ui = (BasicSliderUI) getUI();
		BasicSliderUI.TrackListener trackListener = ui.new TrackListener() {
			@Override public void mouseClicked(MouseEvent event) {
				Point point = event.getPoint();
				int value = ui.valueForXPosition(point.x);
				setValue(value);
			}
			// Disable check that will invoke scrollDueToClickInTrack
			@Override public boolean shouldScroll(int dir) {
				return false;
			}
		};
		addMouseListener(trackListener);

		// Add MouseMotionListener to update tooltip
		MouseMotionListener mml = new MouseMotionListener() {
			public void mouseMoved(MouseEvent e) {
				ToolTipManager ttm = ToolTipManager.sharedInstance();
				long time = System.currentTimeMillis() - ttm.getInitialDelay() + 1;  // So that the tooltip will trigger immediately
				// Translate the X-coordinate into a slider position...
				int position =  e.getX() * getMaximum() / getWidth();
				setToolTipText(Util.minutesSeconds(position));
			}
			public void mouseDragged(MouseEvent e) {
			}
		};
		addMouseMotionListener(mml);

		// By default, all sliders will range from -10 to 10
		setMinimum(-10);
		setMaximum(10);
		setValue(0);
	}
}

// Consumes mouse events that occur outside of thumb in order to prevent accidentally setting slider
class SafeSlider extends JSlider {
	static boolean initialized;

	public SafeSlider(int orientation) {
		super(orientation);
		if (!initialized) {
			Toolkit toolkit = getToolkit();
			toolkit.addAWTEventListener(new AWTEventListener() {
				public void eventDispatched(AWTEvent e) {
					Object source = e.getSource();
					if (e instanceof MouseEvent && source instanceof SafeSlider) {
						MouseEvent me = (MouseEvent) e;
						SafeSlider slider = (SafeSlider) source;
						BasicSliderUI bsui = (BasicSliderUI) slider.getUI();
						int mouseValue = bsui.valueForXPosition(me.getPoint().x);
						if (slider.getOrientation() == SwingConstants.VERTICAL) {
							mouseValue = bsui.valueForYPosition(me.getPoint().y);
						}
						int sliderValue = slider.getValue();
						if (mouseValue < sliderValue - 1 || mouseValue > sliderValue + 1) {
							me.consume();
						}
					}
				}
			}, AWTEvent.MOUSE_EVENT_MASK);
		}
		// By default, all sliders will range from -10 to 10
		setMinimum(-10);
		setMaximum(10);
		setValue(0);
	}
}


class CustomSlider extends SafeSlider {
	public CustomSlider(int orientation) {
		super(orientation);
		// Tried to reduce space in GTK LAF, but seems to have no effect...
		setBorder(new EmptyBorder(0, 0, 0, 0));
		//setBorder(new LineBorder(Color.red));
	}

	public Dimension getMinimumSize() {
		Dimension d = super.getMinimumSize();
		if (orientation == SwingConstants.HORIZONTAL) {
			d.width = 80;
		} else {
			d.height = 80;
		}
		return d;
	}
	public Dimension getPreferredSize() {
		return getMinimumSize();
	}

	public Dimension getMaximumSize() {
		return super.getMaximumSize();
	}
}

class EQSlider extends CustomSlider {
	boolean adjusting;

	public EQSlider() {
		super(SwingConstants.VERTICAL);
	}
	
	public void adjustValue(int value) {
		adjusting = true;
		setValue(value);
	}
}

class ControlSlider extends CustomSlider {
	public ControlSlider() {
		super(SwingConstants.HORIZONTAL);
	}
}

class ControlLabel extends JLabel {
	public ControlLabel(String text) {
		super(text);
	}

	public Dimension getMinimumSize() {
		Dimension d = super.getMinimumSize();
		Font font = getFont();
		FontMetrics fm = getFontMetrics(font);
		d.width = fm.stringWidth("-80db");
		return d;
	}

	public Dimension getPreferredSize() {
		Dimension d = super.getPreferredSize();
		Dimension min = getMinimumSize();
		if (d.width < min.width) {
			d.width = min.width;
		}
		return d;
	}

	public Dimension getMaximumSize() {
		Dimension d = super.getMaximumSize();
		Dimension min = getMinimumSize();
		if (d.width < min.width) {
			d.width = min.width;
		}
		return d;
	}
}

class PresetComboBox extends JComboBox {
	public PresetComboBox() {
		super();
	}

	public Dimension getMinimumSize() {
		Dimension d = super.getMinimumSize();
		d.width = 100;
		return d;
	}

	public Dimension getPreferredSize() {
		return getMinimumSize();
	}

	public Dimension getMaximumSize() {
		Dimension d = getMinimumSize();
		d.height = 2 * d.height;
		return d;
	}
}


interface Presets {
	// Equalizer preset values                      1   2   3   4   5   6   7   8   9  10  11  12  13  14  15  16  17  18  19  20  21  22  23  24  25  26  27  28  29  30  31
	static final int[] PRESETS_10_BASS_PLUS    = {  7,  5,  3,  1,  0,  0,  0,  0,  0,  0};
	static final int[] PRESETS_15_BASS_PLUS    = { 10,  9,  8,  7,  6,  5,  4,  3,  2,  1,  0,  0,  0,  0,  0};
	static final int[] PRESETS_25_BASS_PLUS    = { 10,  9,  8,  7,  6,  5,  4,  3,  2,  1,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0};
	static final int[] PRESETS_31_BASS_PLUS    = { 10, 10,  9,  8,  7,  6,  5,  4,  3,  2,  1,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0};
	static final int[] PRESETS_10_BASS_MINUS   = { -7, -5, -3, -1,  0,  0,  0,  0,  0,  0};
	static final int[] PRESETS_15_BASS_MINUS   = {-10, -9, -8, -7, -6, -5, -4, -3, -2, -1,  0,  0,  0,  0,  0};
	static final int[] PRESETS_25_BASS_MINUS   = {-10, -9, -8, -7, -6, -5, -4, -3, -2, -1,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0};
	static final int[] PRESETS_31_BASS_MINUS   = {-10,-10, -9, -8, -7, -6, -5, -4, -3, -2, -1,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0};
	static final int[] PRESETS_10_MID_PLUS     = {  0,  1,  3,  5,  7,  7,  5,  3,  1,  0};
	static final int[] PRESETS_15_MID_PLUS     = {  0,  0,  2,  4,  6,  8,  9, 10,  9,  8,  6,  4,  2,  0,  0};
	static final int[] PRESETS_25_MID_PLUS     = {  0,  0,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10,  9,  8,  7,  6,  5,  4,  3,  2,  1,  0,  0,  0};
	static final int[] PRESETS_31_MID_PLUS     = {  0,  0,  0,  0,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 10, 10,  9,  8,  7,  6,  5,  4,  3,  2,  1,  0,  0,  0,  0,  0};
	static final int[] PRESETS_10_MID_MINUS    = {  0, -1, -3, -5, -7, -7, -5, -3, -1,  0};
	static final int[] PRESETS_15_MID_MINUS    = {  0,  0, -2, -4, -6, -8, -9,-10, -9, -8, -6, -4, -2,  0,  0};
	static final int[] PRESETS_25_MID_MINUS    = {  0,  0,  0, -1, -2, -3, -4, -5, -6, -7, -8, -9,-10, -9, -8, -7, -6, -5, -4, -3, -2, -1,  0,  0,  0};
	static final int[] PRESETS_31_MID_MINUS    = {  0,  0,  0,  0,  0, -1, -2, -3, -4, -5, -6, -7, -8, -9,-10,-10,-10, -9, -8, -7, -6, -5, -4, -3, -2, -1,  0,  0,  0,  0,  0};
	static final int[] PRESETS_10_TREBLE_PLUS  = {  0,  0,  0,  0,  0,  0,  1,  3,  5,  7};
	static final int[] PRESETS_15_TREBLE_PLUS  = {  0,  0,  0,  0,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10};
	static final int[] PRESETS_25_TREBLE_PLUS  = {  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10};
	static final int[] PRESETS_31_TREBLE_PLUS  = {  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 10};
	static final int[] PRESETS_10_TREBLE_MINUS = {  0,  0,  0,  0,  0,  0, -1, -3, -5, -7};
	static final int[] PRESETS_15_TREBLE_MINUS = {  0,  0,  0,  0,  0, -1, -2, -3, -4, -5, -6, -7, -8, -9,-10};
	static final int[] PRESETS_25_TREBLE_MINUS = {  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0, -1, -2, -3, -4, -5, -6, -7, -8, -9,-10};
	static final int[] PRESETS_31_TREBLE_MINUS = {  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0, -1, -2, -3, -4, -5, -6, -7, -8, -9,-10,-10};
}

