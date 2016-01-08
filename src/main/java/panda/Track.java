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

import java.io.*;
import java.util.*;
import javax.sound.sampled.*;

public class Track implements Comparable<Track> {
	// Normally both player and filename are not null
	// But both cannot be null at the same time
	private Player player;			// If null then file does not exist
	private String filename;		// If null then it is not in library
	private String title;			// Up to 32 characters
//	private String orchestra;		// Up to 32 characters
//	private List<String> singers;	// Up to 32 characters each
//	private String genre;			// Up to 8 alphabetic letters
//	private int year;				// Between 1900 and 2100
//	private int bpm;				// Between 0 and 200
//	private String comment;			// Up to 128 characters
	private Map<String, String> tags = new HashMap<String, String>();;
	private boolean checked = true;	// Flag to indicate if checbox in UI is selected

	public Track(String filename) throws IOException, UnsupportedAudioFileException {
		this.filename = filename;
		this.player = new Player(this);
//		// By default, the track title will be the same as the filename until we find a title tag and set it afterwards
//		this.title = filename;
	}

	public Player getPlayer() {
		return player;
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

	public void setChecked(boolean value) {
		checked = value;
	}

	public boolean isChecked() {
		return checked;
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
/*
	public String getOrchestra() {
		return orchestra;
	}

	public void setOrchestra(String orchestra) {
		this.orchestra = orchestra;
	}

	public List<String> getSingers() {
		return singers;
	}

	public void setSingers(List<String> singers) {
		this.singers = singers;
	}

	public String getGenre() {
		return genre;
	}

	public void setGenre(String genre) {
		this.genre = genre;
	}

	public int getYear() {
		return year;
	}

	public void setYear(int year) {
		this.year = year;
	}

	public int getBPM() {
		return bpm;
	}

	public void setBPM(int bpm) {
		this.bpm = bpm;
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}
*/
}

