package nl.dobots.bluenet.ble.extended.structs;

import java.util.ArrayList;
import java.util.Collection;

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
 * Created on 12-8-15
 *
 * @author Dominik Egger
 */
public class BleDeviceList extends ArrayList<BleDevice> {

	public BleDeviceList() {
		super();
	}

	public BleDeviceList(Collection<? extends BleDevice> collection) {
		super(collection);
	}

	public boolean containsDevice(String address) {
		return getDevice(address) != null;
	}

	public BleDeviceList clone() {
		return (BleDeviceList)super.clone();
	}

	public BleDevice getDevice(String address) {
		for (BleDevice device : this) {
			if (device.getAddress().equals(address)) {
				return device;
			}
		}
		return null;
	}
}
