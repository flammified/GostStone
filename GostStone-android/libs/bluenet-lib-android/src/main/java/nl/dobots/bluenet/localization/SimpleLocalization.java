package nl.dobots.bluenet.localization;

import android.util.Log;

import java.util.ArrayList;
import java.util.Date;

import nl.dobots.bluenet.ble.extended.structs.BleDevice;
//import nl.dobots.presence.cfg.Settings;
import nl.dobots.bluenet.localization.locations.Location;
import nl.dobots.bluenet.localization.locations.LocationsList;

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
public class SimpleLocalization implements Localization {

	public static final String TAG = SimpleLocalization.class.getCanonicalName();

//	private static SimpleLocalization instance;

	private float _detectionDistance;

	//	private Settings _settings;
	private LocationsList _locationsList;

	private long _lastDetectionTime = 0;

	public SimpleLocalization(LocationsList list, float detectionDistance) {
//		_settings = Settings.getInstance();
//		_locationsList = _settings.getLocationsList();
		_locationsList = list;
		_detectionDistance = detectionDistance;
		_lastDetectionTime = new Date().getTime();
	}

//	public static SimpleLocalization getInstance() {
//		if (instance == null) {
//			instance = new SimpleLocalization();
//		}
//		return instance;
//	}

	public LocalizationResult findLocation(ArrayList<BleDevice> devices) {
		LocalizationResult result = null;

		Location location;
		// loop over all devices in the list and find ...
		for (BleDevice device : devices) {
			// ... the closest device registered with a location
			if ((location = _locationsList.findLocation(device.getAddress())) != null) {
				double distance = device.getDistance();
				if (distance != -1 && distance < _detectionDistance) {
					Log.i(TAG, String.format("I am in range of: %s at %.2f m", device.getName(), device.getDistance()));

					result = new LocalizationResult();
					result.location = location;
					result.triggerDevice = device;
				}
				_lastDetectionTime = System.currentTimeMillis();
				return result;
			}
		}

		return null;
	}

	public long getLastDetectionTime() {
		return _lastDetectionTime;
	}
}
