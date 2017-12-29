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

import java.util.logging.Level;

// The Player class stores settings that need to be:
// - passed on to the track currently playing
// - preserved between tracks (ie. applied when a new track starts playing)
public class Player {
	// TODO: Make private and add setter...
	static Track track; // The track that is currently being played
	private static boolean stopped;
	private static boolean paused;
	private static int volume = 14;
	private static int balance = 0;
	private static boolean equalizerEnabled;
	private static int[] equalizer = new int[Config.bands];

	public static Track getTrack() {
		return track;
	}

	public static boolean isStopped() {
		return stopped;
	}

	public static void stop() {
		stopped = true;
	}

	// Synonym for: setStopped(false);
	public static void play() {
		stopped = false;
	}

	public static boolean isPaused() {
		return paused;
	}

	public static void setPaused(boolean paused) {
		Player.paused = paused;
	}

	public static int getVolume() {
		return volume;
	}

	// Limits range from -20 to 20
	public static void setVolume(int value) {
		volume = value;
		if (value >= 20) {
			volume = 20;
		} else if (value <= 0) {
			volume = 0;
		}
		Util.log(Level.FINE, "Setting volume to: " + volume);
		if (track != null) {
			track.setVolume(value);
		}
	}

	public static float getGain() {
		if (track == null) {
			return 0.0f;
		}
		return track.getGain();
	}

	public static int getBalance() {
		return balance;
	}

	// Limits range from -10 to 10
	public static void setBalance(int value) {
		balance = value;
		if (value >= 10) {
			balance = 10;
		} else if (value <= -10) {
			balance = -10;
		}
		Util.log(Level.FINE, "Setting balance to: " + balance);
		if (track != null) {
			track.setBalance(value);
		}
	}

	public static boolean isEqualizerEnabled() {
		return equalizerEnabled;
	}

	// Sets the equalizerEnabled boolean flag,
	// and sets the individual bands to either zero or the stored value
	public static void setEqualizerEnabled(boolean value) {
		equalizerEnabled = value;
		if (track != null) {
			for (int i = 0; i < equalizer.length; i++) {
				if (equalizerEnabled) {
					track.setEqualizer(i, equalizer[i]);
				} else {
					track.setEqualizer(i, 0);
				}
			}
		}
	}

	public static int getEqualizer(int band) {
		return equalizer[band];
	}

	// Value can range from -10 to 10
	public static void setEqualizer(int band, int value) {
		equalizer[band] = value;
		if (track != null && equalizerEnabled) {
			track.setEqualizer(band, value);
		}
	}

//	// Convenience method to set all values
//	public static void setEqualizer() {
//		for (int i = 0; i < Config.bands; i++) {
//			setEqualizer(i, equalizer[band]);
//		}
//	}
}

