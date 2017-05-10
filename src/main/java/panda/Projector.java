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
import java.awt.image.*;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.*;
import javax.imageio.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;

// UI consists of four broad areas:
//  ---------------------------------
//  | Header                        |
//  ---------------------------------
//  |               |               |
//  |    IMAGE      |    Box        |
//  |               |               |
//  |               |               |
//  ---------------------------------
//  | Footer                        |
//  ---------------------------------
// The Header is a label used for the (shortened) orchestra
// The Footer is a label used to indicate the next tanda
// The box contains a body and tail, consisting of up to six labels
// The body labels contain the track title
// The (optional) tail contains the track year
public class Projector extends JWindow {
	static Projector projector;  // Shared instance when using standalone
//	private int width;
//	private int height;
	private int width = Config.projectorWidth;
	private int height = Config.projectorHeight;

//	private String defaultHeader = "";
//	private String defaultImage = "";
//	private String defaultBody = "";
//	private String defaultTail = "";
//	private String defaultFooter = "";
	private String defaultHeader = Config.projectorHeader;
	private String defaultImage = Config.projectorImage;
	private String defaultBody = Config.projectorBody;
	private String defaultTail = Config.projectorTail;
	private String defaultFooter = Config.projectorFooter;

	private JLabel headerLabel;
	private JLabel imageLabel;
	private Box box = new Box(BoxLayout.Y_AXIS);
	private JLabel footerLabel;

	private Map<String, Image> imageMap = new HashMap<String, Image>();  // Cache of images for efficiency

	static {
		// Ensure properties are loaded first by referencing Util class...
		Util.log(Level.INFO, "Initializing Panda class...");
	}

	public static void main(String[] args) throws Exception {
		Util.log(Level.FINE, "Starting Projector...");
		Updater updater = new Updater();
		updater.start();
		projector = new Projector(new JFrame());
		projector.setVisible(true);
	}

	public Projector(JFrame owner) {
		super(owner);
//		init();
		setSize(width, height);
		setLocationRelativeTo(null);  // Places window in center of screen
		setAlwaysOnTop(true);

		Container contentPane = getContentPane();
		contentPane.setBackground(Color.black);

		int h = (int) (height / 6.0);

		headerLabel = new CustomLabel(width, h, Color.white, new Font("SansSerif", Font.BOLD, (int) (0.8 * h)));
		imageLabel = new CustomLabel(width / 2, 4 * h, Color.white, null);
		footerLabel = new CustomLabel(width, h, Color.red, new Font("SansSerif", Font.BOLD, (int) (0.4 * h)));

		setLayout(new BorderLayout());
		add(headerLabel, BorderLayout.NORTH);
		add(imageLabel, BorderLayout.WEST);
		add(footerLabel, BorderLayout.SOUTH);

		Listener listener = new Listener();
		addMouseListener(listener);
		addMouseMotionListener(listener);

		setDefaults();
	}
/*
	private void init() {
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		width = screenSize.width / 2;
		height = screenSize.height / 2;
		String string = System.getProperty("panda.projector.width");
		if (string != null) {
			try {
				width = Integer.parseInt(string);
				if (width < 640 || width > screenSize.width) {
					throw new NumberFormatException(string);
				}
			} catch (NumberFormatException nfe) {
				Util.log(Level.WARNING, "Invalid width: " + string);
			}
		}
		string = System.getProperty("panda.projector.height");
		if (string != null) {
			try {
				height = Integer.parseInt(string);
				if (height < 480 || height > screenSize.height) {
					throw new NumberFormatException(string);
				}
			} catch (NumberFormatException nfe) {
				Util.log(Level.WARNING, "Invalid height: " + string);
			}
		}
		string = System.getProperty("panda.projector.header");
		if (string != null) {
			defaultHeader = string;
		}
		string = System.getProperty("panda.projector.image");
		if (string != null) {
			defaultImage = string;
		}
		string = System.getProperty("panda.projector.body");
		if (string != null) {
			defaultBody = string;
		}
		string = System.getProperty("panda.projector.tail");
		if (string != null) {
			defaultTail = string;
		}
		string = System.getProperty("panda.projector.footer");
		if (string != null) {
			defaultFooter = string;
		}
	}
*/
	public void setDefaults() {
		headerLabel.setText(defaultHeader);
		Image image = loadImage(defaultImage);
		if (image != null) {
			imageLabel.setIcon(new ImageIcon(image));
		}
		setText(defaultBody, defaultTail);
		footerLabel.setText(defaultFooter);
	}

	public void setHeader(String header) {
		headerLabel.setText(header);
	}

	public void setImage(String filename) {
		Image image = loadImage(filename);
		if (image != null) {
			imageLabel.setIcon(new ImageIcon(image));
		}
	}

	// The body will be split by whitespace into up to 4 or 5 substrings
	// The tail will not be split
	public void setText(String body, String tail) {
		Graphics graphics = getGraphics();
		if (graphics == null) {
			return;
		}
		//int maxLabels = 4;
		int maxLabels = 6;
		if (tail != null) {
			maxLabels--;
		}
		StringTokenizer st = new StringTokenizer(body);
		//int count = st.countTokens();
		List<String> tokens = new ArrayList<String>();
		if (st.countTokens() == 0) {
			// One-word body
			tokens.add(body);
		}
		while (st.hasMoreTokens()) {
			tokens.add(st.nextToken());
		}
		List<String> strings = new ArrayList<String>();
		//int h = (int) (height / 6.0);
		//int fontSize = (int) (0.6 * h);
		int h = (int) (2 * height / 3.0 / 6);
		int fontSize = (int) (0.8 * h);
		Font font = new Font("SansSerif", Font.BOLD, fontSize);
		FontMetrics fontMetrics = graphics.getFontMetrics(font);
		String string = "";
		//for (String token : tokens) {
		for (int i = 0; i < tokens.size(); i++) {
			String token = tokens.get(i);
			String s = string + " " + token;
			if (fontMetrics.stringWidth(s) < width / 2) {
				string = s;
			} else {
				if (i == 0) {
					// First token, so string will be empty
					strings.add(token);
				} else {
					strings.add(string);
					string = token;
				}
			}
			if (i == tokens.size() - 1) {
				// Last token
				strings.add(string);
			}
		}

		remove(box);
		box = new Box(BoxLayout.Y_AXIS);
		box.add(Box.createVerticalGlue());
		for (int i = 0; i < strings.size() && i < maxLabels; i++) {
			string = strings.get(i);
			JLabel label = new CustomLabel(width / 2, h, Color.white, font);
			label.setText(string);
			box.add(label);
		}
		if (tail != null) {
			JLabel tailLabel = new CustomLabel(width / 2, h, Color.gray, font);
			tailLabel.setText(tail);
			box.add(Box.createRigidArea(new Dimension(0, (int) (0.2 * h))));
			box.add(tailLabel);
		}
		box.add(Box.createVerticalGlue());
		add(box, BorderLayout.EAST);
		validate();
	}

	public void setFooter(String footer) {
		footerLabel.setText(footer);
	}

	public void setDefaultFooter() {
		footerLabel.setText(defaultFooter);
	}

	private Image loadImage(String filename) {
		Image image = imageMap.get(filename);
		if (image != null) {
			return image;
		}
		try {
//			image = ImageIO.read(new File(Util.PANDA_HOME + "images/" + filename));
			image = ImageIO.read(new File(Config.PANDA_HOME + "images/" + filename));
		} catch (IOException ioe) {
			Util.log(Level.WARNING, "Error loading image " + filename + ": " + ioe + " (loading default image " + defaultImage + ")");
			try {
//				image = ImageIO.read(new File(Util.PANDA_HOME + "images/" + defaultImage));
				image = ImageIO.read(new File(Config.PANDA_HOME + "images/" + defaultImage));
			} catch (IOException e) {
				Util.log(Level.SEVERE, "Error loading default image " + defaultImage + ": " + e);
			}
		}
		// Scale image (or fill with black)
		int w = width / 2;
		int h = (int) (2.0 * height / 3);
		BufferedImage bufferedImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = bufferedImage.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		if (image == null) {
			graphics.setColor(Color.black);
			graphics.fillRect(0, 0, w, h);
		} else {
			graphics.drawImage(image, 0, 0, w, h, null);
		}
		graphics.dispose();
		image = bufferedImage;
		imageMap.put(filename, image);
		return image;
	}

	class Listener implements MouseListener, MouseMotionListener {
		private boolean mousePressed;
		private int x_offset;
		private int y_offset;
		private long lastDrag;

		public void mouseClicked(MouseEvent e) {
		}

		public void mouseEntered(MouseEvent e) {
		}

		public void mouseExited(MouseEvent e) {
		}

		public void mousePressed(MouseEvent e) {
			x_offset = e.getX();
			y_offset = e.getY();
			mousePressed = true;
		}

		public void mouseReleased(MouseEvent e) {
		}

		public void mouseDragged(MouseEvent e) {
			long millis = System.currentTimeMillis();
			if (lastDrag > 0 && (millis - lastDrag) < 100) {
				// prevent flickering by not moving too frequently
				return;
			}
			int new_x = e.getX();
			int new_y = e.getY();
			if (mousePressed) {
				Point p = getLocation();
				p.x += (new_x - x_offset);
				p.y += (new_y - y_offset);
				setLocation(p);
				repaint();
			}
		  lastDrag = millis;
		}

		public void mouseMoved(MouseEvent e) {
		}
	}
}

class CustomLabel extends JLabel {
	private int width;
	private int height;

	public CustomLabel(int width, int height, Color color, Font font) {
		super();
		this.width = width;
		this.height = height;
		setSize(width, height);
		setOpaque(true);
		setBackground(Color.black);
		setForeground(color);
		if (font != null) {
			setFont(font);
		}
		setHorizontalTextPosition(SwingConstants.CENTER);
		setHorizontalAlignment(SwingConstants.CENTER);
//setBorder(new LineBorder(Color.green));
	}

	public Dimension getMinimumSize() {
		return new Dimension(width, height);
	}

	public Dimension getPreferredSize() {
		return getMinimumSize();
	}

	public Dimension getMaximumSize() {
		return getMinimumSize();
	}
}


class Updater extends Thread {
	public void run() {
		Exception exception = null;
		int count = 0;
		while (true) {
			System.out.println("Getting info from iTunes...");
			try {
				Runtime runtime = Runtime.getRuntime();
				String[] args = { "osascript", "-e","tell application \"iTunes\" to if player state is playing then name of current track & \"\\n\" & artist of current track & \"\\n\" & year of current track & \"\\n\" & genre of current track & \"\\n\" & grouping of current track"};
				Process process = runtime.exec(args);
				process.waitFor();
				StreamConsumer stdoutConsumer = new StreamConsumer(process.getInputStream());
				StreamConsumer stderrConsumer = new StreamConsumer(process.getErrorStream());
				stdoutConsumer.start();
				stdoutConsumer.join();
				stderrConsumer.join();
				exception = null;
				count = 0;
			} catch (Exception e) {
				if (Projector.projector != null) {
					Projector.projector.setDefaults();
					Projector.projector.repaint();
				}
				// Don't exit just because an isolated exception occurred - it might be due to something temporary.
				// But also don't flood screen indefinitely with unnecessary repeated error messages.
				// Only exit if same exception occurs a number of times in a row.
				if (exception == null) {
					exception = e;
				} else {
					String message = exception.getMessage();
					String m = e.getMessage();
					if (m.equals(message)) {
						count++;
					}
				}
				if (count >= 300) {
					System.out.println("Exception: " + e);
					System.exit(1);
				}
			} finally {
				Util.pause(1000);
			}
		}
	}
}

class StreamConsumer extends Thread {
	private InputStream is;

	public StreamConsumer(InputStream is) {
		this.is = is;
	}

	public void run() {
		String artist = "";
		String title = "";
		String year = "";
		String genre = "";
		String grouping = "";
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		for (int i = 0; i < 5; i++) {
			try {
				String line = br.readLine();
				if (line == null) {
					break;
				}
				line = line.trim();
				if (i == 0 && line.length() == 0) {
					// Nothing received from iTunes
					break;
				}
				if (i == 0) {
					title = line;
				} else if (i == 1) {
					artist = line;
				} else if (i == 2) {
					year = line;
				} else if (i == 3) {
					genre = line;
				} else if (i == 4) {
					grouping = line;
				}
			} catch (IOException ioe) {
			}
		}
// --------------------------------------------------------------------------------
// TODO: Cache values to prevent flicker? (ie. only repaint if values have actually changed...)
//		if (genre == null || !Util.projectorGenres.contains(genre)) {
		if (genre == null || !Config.projectorGenres.contains(genre)) {
			Projector.projector.setDefaults();
			return;
		}
		String header = System.getProperty("panda.projector.map." + artist);
		if (header == null) {
			header = artist;
		}
		Projector.projector.setHeader(header);
		Projector.projector.setImage("orchestra/" + header + ".jpg");

		if (year.equals("0")) {
			year = "";
		}
		Projector.projector.setText(title, year);
		Projector.projector.repaint();
	}
}



