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
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import javax.sound.sampled.*;

import panda.equalizer.*;

// TODO: 
// Find and use optimal buffer size - Look at JEQ test source for an idea...
public class Track implements Comparable<Track> {
	static final int BUFFER_SIZE = 8192; // 8KB
	private String filename;		// If null then it is not in library
	private String title;			// Up to 32 characters
	private Map<String, String> tags = new HashMap<String, String>();;
	private boolean checked = true;	// Flag to indicate if checbox in UI is selected
	private boolean missing;		// Flag to indicate that the file is missing

	private AudioInputStream ais;
	private AudioFormat audioFormat;
	private List<TrackListener> listeners = new ArrayList<TrackListener>();
	private int duration; // Length of track in seconds
	private int position; // Current position in audio stream in seconds
	private int newPosition = -1; // Position that player must seek (jump) to
	private int channels; // Number of channels (1 for mono, 2 for stereo)
	private FloatControl gainControl;
	private FloatControl balanceControl;
	private IIRControls equalizerControl;

	private int volume = 14;
	private boolean fade;			// Flag to indicate that track must be faded out

	public Track(String filename) throws IOException, UnsupportedAudioFileException {
		this.filename = filename;
		Util.log(Level.FINE, "Getting audio input stream for track: " + filename);
		File file = new File(Panda.TRACKS + filename);
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

	public Track(String filename, boolean missing) {
		if (!missing) {
			throw new IllegalArgumentException("Wrong constructor called for Track!");
		}
		this.filename = filename;
		setMissing(true);
	}

	public String getFilename() {
		return filename;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public void setTag(String name, String value) {
		tags.put(name, value);
	}

	public String getTag(String name) {
		if (tags.containsKey(name)) {
			return tags.get(name);
		}
		return "";
	}

	public Map<String, String> getTags() {
		return tags;
	}

	public void setChecked(boolean value) {
		checked = value;
	}

	public boolean isChecked() {
		return checked;
	}

	public void setMissing(boolean value) {
		missing = value;
		if (missing) {
			checked = false;
		}
	}

	public boolean isMissing() {
		return missing;
	}

	public float getGain() {
		if (gainControl == null) {
			return 0.0f;
		}
		return gainControl.getValue();
	}

	public int getVolume() {
		return volume;
	}

	// Derives a gain value from the volume level as follows:
	//   volume  0 = minimum gain 
	//   volume 14 = zero gain
	//   volume 20 = maximum gain
	// Values in-between are calculated to dB values
	public void setVolume(int volume) {
		this.volume = volume;
		if (gainControl == null) {
			return;
		}
		float gain = 0.0f;
		float min = gainControl.getMinimum();
		float max = gainControl.getMaximum();
		if (volume > 14) {
			gain = max - (20 - volume) * max / 6;
		} else if (volume < 14) {
			gain = (14 - volume) * min / 14;
		}
		Util.log(Level.FINE, "Setting gain to: " + gain);
		gainControl.setValue(gain);
	}

	// Value can range from -10 to 10
	public void setBalance(int balance) {
		if (balanceControl == null) {
			return;
		}
		float floatValue = 0.0f;
		if (balance != 0) {
			// Float value ranges from -1.0 to 1.0
			floatValue = balance / 10.0f;
		}
		Util.log(Level.FINE, "Setting track balance to: " + floatValue);
		balanceControl.setValue(floatValue);
	}

	public void setEqualizer(int band, int value) {
		if (equalizerControl == null) {
			return;
		}
		float floatValue = ((float) value) / 10.0f / 5.0f;  // Value can range between -0.2 and +0.2
		Util.log(Level.FINE, "Setting equalizer band #" + band + " to: " + value + "(float=" + floatValue + ")");
		for (int i = 0; i < channels; i++) {
			equalizerControl.setBandValue(band, i, floatValue);
		}
	}

	private void setEqualizer() {
		for (int i = 0; i < Config.bands; i++) {
			int value = Player.isEqualizerEnabled() ?  Player.getEqualizer(i) : 0;
			setEqualizer(i, value);
		}
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
			Player.stop();
			return;
		}
		if (position < 0) {
			position = 0;
		}
		this.newPosition = position;
	}

	public void fade() {
		fade = true;
	}

	public boolean isGainControlSupported() {
		return gainControl != null ? true : false;
	}

	public boolean isBalanceControlSupported() {
		return balanceControl != null ? true : false;
	}
	public void addTrackListener(TrackListener listener) {
		listeners.add(listener);
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
		Player.track = this;
		Util.log(Level.INFO, "------------ Playing " + filename + " ------------");
		File file = new File(Panda.TRACKS + filename);
		// Note, when a track is played, it is by definition NOT stopped and at position 0.
		// However, it may be paused, in which case it will get everything ready to play and then wait until it is unpaused before continuing
		// TODO: Too confusing... replace play(0 with setStopped(false)...
		Player.play();
		position = 0;
		newPosition = -1;
		// Make sure we obtain a fresh input stream...
		ais = AudioSystem.getAudioInputStream(file);
		EqualizerAudioInputStream eais = new EqualizerAudioInputStream(ais, Config.bands);

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
		setVolume(Player.getVolume());
		setBalance(Player.getBalance());
		setEqualizer();
		fade = false;
		Util.log(Level.FINE, "Starting line...");
		line.start();

		// Invoke listeners only after controls have been obtained...
		for (TrackListener listener: listeners) {
			listener.started(this);
		}

		byte[] buffer = new byte[BUFFER_SIZE];
		int totalRead = 0;
		long timestamp = System.currentTimeMillis();
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
				for (TrackListener listener: listeners) {
					listener.positionChanged(this);
				}
			}
			while (Player.isPaused() && !Player.isStopped()) {
				Util.pause(100);
			}
			if (Player.isStopped()) {
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
					ais = AudioSystem.getAudioInputStream(file);
					eais = new EqualizerAudioInputStream(ais, Config.bands);
					equalizerControl = eais.getControls();
					setEqualizer();
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
			if (fade && System.currentTimeMillis() - timestamp > 1000) {
				// Reduce volume if more than a second has passed
				int newVolume = getVolume() - 1;
				if (newVolume < 0) {
					break;
				}
				setVolume(newVolume);
				timestamp = System.currentTimeMillis();
			}
			line.write(buffer, 0, read);
		}
		line.drain();
		line.close();
		eais.close();
	}

	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("filename=");
		sb.append(filename);
		sb.append(", title=");
		sb.append(title);
		if (tags.size() > 0) {
			sb.append(", tags: ");
			Set set = tags.keySet();
			Iterator it = set.iterator();
			while (it.hasNext()) {
				String key = (String) it.next();
				String value = tags.get(key);
				sb.append(key);
				sb.append("=");
				sb.append(value);
				if (it.hasNext()) {
					sb.append(",");
				}
			}
		}
		return sb.toString();
	}

	public int compareTo(Track that) {
		return this.title.compareTo(that.title);
	}

	class WavLineListener implements LineListener {
		boolean first = true;

		public void update(LineEvent event) {
			Util.log(Level.FINE, "Line event:" + event);
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

	// Main method is just for testing standalone on command line
	public static void main(String[] args) throws Exception {
		// TODO: Check args - not empty and files all exist...
		Util.log(Level.INFO, "Running Track class directly on command line");
		for (int i = 0; i < args.length; i++) {
			String filename = args[i];
			Track track = new Track(filename);
			track.play();
		}
	}
}

