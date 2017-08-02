package nl.dobots.bluenet.ble.base.structs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import nl.dobots.bluenet.utils.BleUtils;

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
 * Created on 15-7-15
 *
 * Class holding information about a tracked device. Used to add new tracked devices and to
 * parse the received list of tracked devices.
 *
 * A tracked device has the following fields:
 * 		* MAC address of the device
 * 		* RSSI value, defining the trigger threshold
 *
 * 	And the format is:
 * 		--------------------------------------------------
 * 		| ADR[6] | ADR[5] | ADR[4] | ... | ADR[0] | RSSI |
 * 		--------------------------------------------------
 *
 * Note: Byte Order is Little-Endian, i.e. Least Significant Bit comes first
 *
 * @author Dominik Egger
 */
public class TrackedDeviceMsg {

	// the MAC address of the tracked device
	byte[] address;
	// the rssi value used for the trigger threshold
	int rssi;

	/**
	 * Create a new tracked device
	 * @param address MAC address of the tracked device (as a byte array)
	 * @param rssi rssi value to be used as trigger threshold
	 */
	public TrackedDeviceMsg(byte[] address, int rssi) {
		this.address = address;
		this.rssi = rssi;
	}

	/**
	 * Create a new tracked device
	 * @param address MAC address of the tracked device (as a string of the format
	 *                "00:43:A8:23:10:F0")
	 * @param rssi rssi value to be used as trigger threshold
	 */
	public TrackedDeviceMsg(String address, int rssi) {
		this.rssi = rssi;
		this.address = BleUtils.addressToBytes(address);
	}

	/**
	 * Convert the object to a byte array in order to write it to the characteristic
	 * @return byte array representation of the object
	 */
	public byte[] toArray() {
		ByteBuffer bb = ByteBuffer.allocate(7);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		bb.put(address);
		bb.put((byte) rssi);

		return bb.array();
	}

	/**
	 * For debug purposes, create a string representation of the tracked device
	 * @return string representation of the object
	 */
	@Override
	public String toString() {
//		return String.format("{address: %s, rssi: %d}", BleUtils.bytesToString(address), rssi);
//		return BleUtils.bytesToString(toArray());
		return String.format("{address: %s, rssi: %d, array: %s}", BleUtils.bytesToString(address), rssi, BleUtils.bytesToString(toArray()));
	}

	/**
	 * Get the MAC address of the tracked device
	 * @return MAC address
	 */
	public byte[] getAddress() {
		return address;
	}

	/**
	 * Set the MAC address of the tracked device
	 * @param address new MAC address
	 */
	public void setAddress(byte[] address) {
		this.address = address;
	}

	/**
	 * Get the threshold rssi value
	 * @return rssi value
	 */
	public int getRssi() {
		return rssi;
	}

	/**
	 * Set the threshold rssi value
	 * @param rssi new rssi value
	 */
	public void setRssi(int rssi) {
		this.rssi = rssi;
	}
}
