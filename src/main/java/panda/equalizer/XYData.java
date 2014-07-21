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
package panda.equalizer;

public class XYData {
    /**
     * X data
     */
    public double x[] = new double[3]; /* x[n], x[n-1], x[n-2] */
    /**
     * Y data
     */
    public double y[] = new double[3]; /* y[n], y[n-1], y[n-2] */

    /**
     * Constructs new XYData object
     */
    public XYData() {
        zero();
    }

    /**
     * Fills all content with zero
     */
    public void zero() {
        for (int i = 0; i < 3; i++) {
            x[i] = 0;
            y[i] = 0;
        }
    }
}
