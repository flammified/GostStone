package nl.dobots.bluenet.ibeacon;

import android.util.Log;

import java.util.UUID;

/**
 * Copyright (c) 2015 Bart van Vliet <bart@dobots.nl>. All rights reserved.
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
 * Created on 10-10-16
 *
 * @author Bart van Vliet
 */
public class BleIbeaconFilter {
	private UUID uuid;
	private int major; // Use -1 to ignore this value
	private int minor; // Use -1 to ignore this value

	public BleIbeaconFilter(UUID uuid) {
		this(uuid, -1, -1);
	}

	public BleIbeaconFilter(UUID uuid, int major, int minor) {
		this.uuid = uuid;
		this.major = major;
		this.minor = minor;
//		Log.d("bluenet ibeacon filter", "Set filter to: " + uuid.toString() + "=" + this.uuid.toString() + " " + major + " " + minor);
	}

	public boolean equals(BleIbeaconFilter filter) {
		return (this.uuid.equals(filter.uuid) && this.major == filter.major && this.minor == filter.minor);
	}

	public boolean matches(UUID uuid, int major, int minor) {
//		Log.d("bluenet ibeacon filter", "filter: " + this.uuid.toString() + " vs " + uuid.toString());
		if (!this.uuid.equals(uuid)) {
			return false;
		}
		if (this.major != -1 && this.major != major) {
			return false;
		}
		if (this.minor != -1 && this.minor != minor) {
			return false;
		}
		return true;
	}

	public UUID getUuid() {
		return uuid;
	}
}
