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

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.sound.sampled.*;

import panda.equalizer.*;

// TODO: 
// Find and use optimal buffer size - Look at JEQ test source for an idea...
public class Player {
	static final int BUFFER_SIZE = 8192; // 8KB
	private static Player player; // The player that is currently being played

	// Settings that need to be preserved between tracks, 
	// ie. when a new track starts playing it must have these settings applied.
	private static boolean stopped;
	private static boolean paused;
	private static int volume = 14;
	private static int balance = 0;
	private static boolean equalizerEnabled;
	private static int[] equalizer = new int[Panda.BANDS];

	private Track track;
	private AudioInputStream ais;
	private AudioFormat audioFormat;
	private List<PlayerListener> listeners = new ArrayList<PlayerListener>();
	private SourceDataLine line;
	private int channels; // Number of channels (1 for mono, 2 for stereo)
	private int duration; // Length of track in seconds
	private int position; // Current position in audio stream in seconds
	private int newPosition = -1; // Position that player must seek (jump) to
//	private BooleanControl muteControl;
//	private FloatControl panControl;
	private FloatControl gainControl;
	private FloatControl balanceControl;
	private IIRControls equalizerControl;

	// Main method is just for testing standalone on command line
	public static void main(String[] args) throws Exception {
		// TODO: Check args - not empty and files all exist...
		Util.log(Level.INFO, "Running Player class directly on command line");
		for (int i = 0; i < args.length; i++) {
			String filename = args[i];
			Player player = new Player(new Track(filename));
			/*
			player.addPlayerListener(new PlayerListener() {
				public void positionChanged(int position) {
					Util.log(Level.FINE, "Position: " + position);
				}
				public void error(Exception exception) {
					Util.log(Level.SEVERE, "*** EXCEPTION: " + exception);
					System.exit(1);
				}
			});
			*/
			player.play();
		}
	}

	public Player(Track track) throws IOException, UnsupportedAudioFileException {
		Util.log(Level.FINE, "Constructing player for track: " + track);
		this.track = track;
		String filename = track.getFilename();
		Util.log(Level.FINE, "Getting audio input stream for file: " + filename);
//		ais = AudioSystem.getAudioInputStream(new File(filename));
		//ais = AudioSystem.getAudioInputStream(new File(Panda.TRACKS_DIR + filename));
		File file = new File(Panda.TRACKS_DIR + filename);
		Util.log(Level.FINE, "Getting audio input stream for file: " + file);
		ais = AudioSystem.getAudioInputStream(file);

		audioFormat = ais.getFormat();
		Util.log(Level.FINE, "Audio format: " + audioFormat);
		Util.log(Level.FINE, "  Channels=" + audioFormat.getChannels());
		Util.log(Level.FINE, "  Encoding=" + audioFormat.getEncoding());
		Util.log(Level.FINE, "  FrameRate=" + audioFormat.getFrameRate());
		Util.log(Level.FINE, "  FrameSize=" + audioFormat.getFrameSize());
		Util.log(Level.FINE, "  SampleRate=" + audioFormat.getSampleRate());
		Util.log(Level.FINE, "  SampleSizeInBits=" + audioFormat.getSampleSizeInBits());
		Util.log(Level.FINE, "  isBigEndian? " + audioFormat.isBigEndian());

		channels = audioFormat.getChannels();

		Util.log(Level.FINE, "Calculating duration...");
		int available = ais.available();
		duration = available / (int) (audioFormat.getFrameRate() * audioFormat.getFrameSize()); 
		Util.log(Level.FINE, "Duration: " + duration + " seconds");
		// Note: Must close stream - cannot keep all the files open at the same time (IOException: Too many open files)
		ais.close();
	}

	public Track getTrack() {
		return track;
	}

	// Can be called any number of times.
	// Each time it is called, the track starts playing from the start
	// Should only be invoked once at a time (across all instances)
	// ie. must not be called while already playing!
	// TODO: 
	// - Throw IllegalStateException if invoked while already running
	// - Expose underlying exceptions, or wrap them in a PlayerException?
	//   No, I prefer to expose them...
	public synchronized void play() throws IOException, UnsupportedAudioFileException, LineUnavailableException, LineUnavailableException {
		player = this;
		String filename = track.getFilename();
		Util.log(Level.INFO, "------------ Playing file " + filename + " ------------");
		filename = Panda.TRACKS_DIR + filename;
		// Note, when a track is played, it is by definition NOT stopped and at position 0.
		// However, it may be paused, in which case it will get eberything ready to play and then wait until it is unpaused before continuing
		stopped = false;
		position = 0;
		newPosition = -1;
		// Make sure we obtain a fresh input stream...
		ais = AudioSystem.getAudioInputStream(new File(filename));
		EqualizerAudioInputStream eais = new EqualizerAudioInputStream(ais, equalizer.length);

		Util.log(Level.FINE, "Obtaining line...");
		DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
		SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);

		Util.log(Level.FINE, "Adding line listener...");
		line.addLineListener(new WavLineListener());

		Util.log(Level.FINE, "Opening line...");
		line.open(audioFormat);
		// Cannot obtain controls before line is opened, yet need to set them before playing any sound
		if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
			gainControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
		} else {
			gainControl = null;
		}
		if (line.isControlSupported(FloatControl.Type.BALANCE)) {
			balanceControl = (FloatControl) line.getControl(FloatControl.Type.BALANCE);
		} else {
			balanceControl = null;
		}
		equalizerControl = eais.getControls();
		// Make sure new instance has volume, balance and equalizer set to same level as previous instance
		setVolume(volume);
		setBalance(balance);
		setEqualizerEnabled(equalizerEnabled);

		Util.log(Level.FINE, "Starting line...");
		line.start();

		// Invoke listeners only after controls have been obtained...
		for (PlayerListener listener: listeners) {
			listener.started(this);
		}

		byte[] buffer = new byte[BUFFER_SIZE];
		int totalRead = 0;
		while (true) {
			//Util.log(Level.FINE, "Available: " + ais.available());
            int read = eais.read(buffer, 0, buffer.length);
			if (read < 0) {
				break;
			}
			totalRead += read;
			int i = totalRead / (int) (audioFormat.getFrameRate() * audioFormat.getFrameSize()); 
			if (position != i) {
				position = i;
				Util.log(Level.FINE, "Position: " + position + " seconds");
				for (PlayerListener listener: listeners) {
					listener.positionChanged(this);
				}
			}
			while (paused && !stopped) {
				Util.pause(100);
			}
			if (stopped) {
				break;
			}
			if (newPosition >= 0) {
				long seek = (long) (newPosition * audioFormat.getFrameRate() * audioFormat.getFrameSize());
				long skipped = 0;
				if (newPosition > position) {
					Util.log(Level.FINE, "Seeking forward to position: " + newPosition);
					long skip = seek - totalRead;
					skipped = eais.skip(skip);
					totalRead += (int) skipped;
				} else if (newPosition < position) {
					Util.log(Level.FINE, "Seeking backward to position: " + newPosition);
					// Need to obtain new input stream in order to be able to seek backwards
					ais = AudioSystem.getAudioInputStream(new File(filename));
					eais = new EqualizerAudioInputStream(ais, equalizer.length);
					equalizerControl = eais.getControls();
					setEqualizerEnabled(equalizerEnabled);
					long skip = seek;
					skipped = eais.skip(skip);
					totalRead = (int) skipped;
				}
				newPosition = -1;
				if (skipped > 0) {
					// Note: Don't play whatever remains in the buffer - discard it and read from the new position.
					continue;
				}
			}
			line.write(buffer, 0, read);
		}
		line.drain();
		line.close();
		eais.close();
	}

	public int getDuration() {
		return duration;
	}

	public int getPosition() {
		return position;
	}

	// Doesn't set the position field directly - instructs player to seek to specified position in stream
	public void setPosition(int position) {
		if (position > duration) {
			stop();
			return;
		}
		if (position < 0) {
			position = 0;
		}
// NO! Don't set the position field!
// If position is set (via slider) while player is paused then it won;t seek to the new position
// (it will think that it is already at that position...)
//		if (paused && this.position != position) {
//			// Need to invoke listeners if player is paused, otherwise they won't get notified of change
//			this.position = position;
//			for (PlayerListener listener: listeners) {
//				listener.positionChanged(this);
//			}
//		}
		this.newPosition = position;
	}

	public void addPlayerListener(PlayerListener listener) {
		listeners.add(listener);
	}

	public static void stop() {
		stopped = true;
	}

	public static boolean isPaused() {
		return paused;
	}

	public static void setPaused(boolean paused) {
		Player.paused = paused;
	}

//	public void toggleMute() {
//		if (muteControl != null) {
//			boolean value = muteControl.getValue();
//			muteControl.setValue(value ? false : true);
//			Util.log(Level.FINE, "Mute: " + (muteControl.getValue() ? "ON" : "OFF"));
//		}
//	}

	public static boolean isGainControlSupported() {
		return player != null && player.gainControl != null ? true : false;
	}

	// Derives a gain value from the volume level as follows:
	//   volume  0 = minimum gain 
	//   volume 14 = zero gain
	//   volume 20 = maximum gain
	// Values in-between are mapped directly to dB values, eg: 16=+2dB, 10=-4dB, etc.
	public static void setVolume(int value) {
		if (player == null || player.gainControl == null) {
			return;
		}
		volume = value;
		float gain = 0.0f;
		if (volume == 0) {
			gain = player.gainControl.getMinimum();
		} else if (volume != 14) {
			gain = volume - 14;
		}
		Util.log(Level.FINE, "Setting volume to: " + volume + " (gain=" + gain + ")");
		player.gainControl.setValue(gain);
	}

	public static float getGain() {
		if (player == null || player.gainControl == null) {
			return 0.0f;
		}
		return player.gainControl.getValue();
	}

	public static boolean isBalanceControlSupported() {
		return player != null && player.balanceControl != null ? true : false;
	}

	public static void setBalance(int value) {
		if (player == null || player.balanceControl == null) {
			return;
		}
		balance = value;
		float floatValue = 0.0f;
		// Assuming balance ranges from -1.0 to 1.0
		if (value != 0) {
			floatValue = value / 10.0f;
		}
		Util.log(Level.FINE, "Setting balance to: " + balance + "(float value " + floatValue + ")");
		player.balanceControl.setValue(floatValue);
	}

	public static int getBalance() {
		return balance;
	}

	// Sets the equalizerEnabled boolean flag as well as setting the equalizer control to either zero or the stored value
	public static void setEqualizerEnabled(boolean value) {
		equalizerEnabled = value;
		for (int i = 0; i < equalizer.length; i++) {
			if (equalizerEnabled) {
				setEqualizerControl(i, equalizer[i]);
			} else {
				setEqualizerControl(i, 0);
			}
		}
	}

	public static void setEqualizer(int band, int value) {
		equalizer[band] = value;
		if (equalizerEnabled) {
			setEqualizerControl(band, value);
		}
	}

	private static void setEqualizerControl(int band, int value) {
		if (player == null || player.equalizerControl == null) {
			return;
		}
//		float floatValue = ((float) value) / 10.0f / 5.0f;
//		if (floatValue < -0.2f) {
//			// TODO: Allow negative range to be spread out more...
//			floatValue = -0.2f;
//		}
		float floatValue = ((float) value) / 10.0f / 5.0f;  // Value can range between -0.2 and +0.2
		Util.log(Level.FINE, "Setting equalizer band #" + band + " to: " + value + "(float=" + floatValue + ")");
		for (int i = 0; i < player.channels; i++) {
			player.equalizerControl.setBandValue(band, i, floatValue);
		}
	}

	class WavLineListener implements LineListener {
		boolean first = true;

		public void update(LineEvent event) {
			Util.log(Level.FINE, "Line event:" + event);
			long time = System.currentTimeMillis();
			if (first) {
				first = false;
				Line line = event.getLine();
				printInfo(line);
			}
			LineEvent.Type type = event.getType();
			if (type.equals(LineEvent.Type.OPEN)) {
				Util.log(Level.FINE, "OPEN");
			}
			if (type.equals(LineEvent.Type.START)) {
				Util.log(Level.FINE, "START");
			}
			if (type.equals(LineEvent.Type.STOP)) {
				Util.log(Level.FINE, "STOP");
			}
			if (type.equals(LineEvent.Type.CLOSE)) {
				Util.log(Level.FINE, "CLOSE");
			}
		}

		private void printInfo(Line line) {
			Line.Info info = line.getLineInfo();
			Util.log(Level.FINE, "Line info: " + info);
			Control[] controls = line.getControls();
			for (int i = 0; i < controls.length; i++) {
				Control control = controls[i];
				Util.log(Level.FINE, "Control #" + i + ": " + control);
			}
		}
	}
}

