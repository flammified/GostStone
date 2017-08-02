package nl.dobots.bluenet.localization;

import java.util.ArrayList;

import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.bluenet.localization.locations.Location;

/**
 * Copyright (c) 2015 Dominik Egger <dominik@dobots.nl>. All rights reserved.
 * <p/>
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3, as
 * published by the Free Software Foundation.
 * <p/>
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * <p/>
 * Created on 11-8-15
 *
 * @author Dominik Egger
 */
public interface Localization {

	class LocalizationResult {
		public Location location;
		public BleDevice triggerDevice;
	}

	/**
	 * go through the list of devices and determine the current location
	 * if no location was found, return null
	 * @param devices list of scanned devices (ordered by distance?)
	 * @return return the found location and the device which triggered
	 * 			the location
	 */
	LocalizationResult findLocation(ArrayList<BleDevice> devices);

	/**
	 * return the last time a device was seen which is registered in one of
	 * the locations
	 * @return time in ms
	 */
	long getLastDetectionTime();

}
