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

interface PlayerListener {
//	public void started();  // Invoked when the play method is called
//	public void positionChanged(int position);  // Invoked every time the (second) position in the audio stream changes
	public void started(Player player);  // Invoked when the play method is called
	public void positionChanged(Player player);  // Invoked every time the (second) position in the audio stream changes
//	// TODO: stopped method? Or simply check if new position is larger or equal to duration?
//	public void error(Exception exception);  // Invoked whenever an exception occurs during playback
	//TODO: pauseChanged method so that play/pause button can change state accordingly
	// Also muteChanged?
	// And gainChanged?
	// And BalanceChanged?a
	// NB! If everything were driven through GUI components (on the screen) then we wouldn't need all the listener methods.
	// But something like play/pause can be controlled from 2 places: the play/pause button in the screen or the spacebar on the keyboard
	// If the user presses the spacebar, then the play/pause button on the screen needs to be updated.
}

