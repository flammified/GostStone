package nl.dobots.bluenet.ble.extended.structs;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

/**
 * The BleDeviceMap is a wrapper class for a list of Bluetooth devices. It provides
 * updating bluetooth devices in the list, retrieving devices based on their address, and
 * functions to get the raw or a sorted list of devices.
 *
 * Created on 15-7-15
 *
 * @author Dominik Egger
 */
public class BleDeviceMap extends HashMap<String, BleDevice> {

	/**
	 * The sort order used for sorting the list by average RSSI
	 */
	public enum SortOrder {
		ascending,
		descending
	}

	/**
	 * Get the bluetooth device for the given address
	 * @param address the address of the device to be retrieved
	 * @return the device if found, null otherwise
	 */
	public synchronized BleDevice getDevice(String address) {
		return get(address);
	}

	/**
	 * Check if the bluetooth device is present in the list
	 * @param device device to be checked
	 * @return true if present, false otherwise
	 */
	public synchronized boolean contains(BleDevice device) {
		return containsKey(device.getAddress());
	}

	/**
	 * Update the bluetooth device list with the given device. if it is a new device
	 * adds it to the list, if the device was already present, update the rssi value
	 * and add it to rssi history
	 * @param device the device to be updated
	 * @return the updated device
	 */
	public synchronized BleDevice updateDevice(BleDevice device) {
		if (contains(device)) {
			BleDevice old = getDevice(device.getAddress());
			// Update rssi and perform validation
			// Keep old data, as not every device has the service data
			device.copyFromOld(old);
			device.validateCrownstone();
			put(device.getAddress(), device);
			return device;
		}
		put(device.getAddress(), device);
		return device;
	}

	/**
	 * Get the unsorted bluetooth device list
	 * @return unsorted bluetooth device list
	 */
	public synchronized BleDeviceList getList() {
		BleDeviceList result = new BleDeviceList();
		result.addAll(values());
		return result;
	}

	/**
	 * Sort bluetooth device list based on average RSSI in descending order.
 	 * @return device list sorted by average RSSI in descending order
	 */
	public synchronized BleDeviceList getRssiSortedList() {
		return getRssiSortedList(SortOrder.descending);
	}

	/**
	 * Sort bluetooth device list based on average RSSI value and specified
	 * sort order.
	 * Note: Average RSSI values of 0 are used for devices that haven't been
	 *   seen within a specified time and are timed-out. This is so as to avoid
	 *   devices staying in the list forever even if they are not seen anymore
	 * @param order define the order in which the list should be sorted, can
	 *              be ascending or descending
	 * @return device list sorted by average RSSI and specified sort order
	 */
	public synchronized BleDeviceList getRssiSortedList(final SortOrder order) {
		BleDeviceList result = new BleDeviceList();
		result.addAll(values());
		Collections.sort(result, new Comparator<BleDevice>() {
			@Override
			public int compare(BleDevice lhs, BleDevice rhs) {
				int ld = lhs.getAverageRssi();
				int rd = rhs.getAverageRssi();
				// we need to handle the 0 value specifically, because we want it
				// to end up at the end of the list, no matter if we sort ascending
				// or descending
				if (ld == 0 && rd == 0)	return 0;
				if (ld == 0) return 1;
				if (rd == 0) return -1;
				// if neither of the values is 0, check how we want to sort
				switch (order) {
					case ascending:
						return ld - rd;
					case descending:
						return rd - ld;
					default:
						// should never occur, but just in case ...
						return 0;
				}
			}
		});
		return result;
	}

	/**
	 * Sort bluetooth device list in ascending order based on distance value.
	 * Note: Distance values of -1 are used for an undefined distance and will be
	 *   moved to the end of the list
	 * @return device list sorted by distance in ascending order
	 */
	public synchronized BleDeviceList getDistanceSortedList() {
		BleDeviceList result = new BleDeviceList();
		result.addAll(values());
		Collections.sort(result, new Comparator<BleDevice>() {
			@Override
			public int compare(BleDevice lhs, BleDevice rhs) {
				double ld = lhs.getDistance();
				double rd = rhs.getDistance();
				// we need to handle the -1 value specifically, because we want it
				// to end up at the end of the list
//				if (ld == -1 && rd == -1) return 0;
//				if (ld == -1) return 1;
//				if (rd == -1) return -1;
				if (ld < 0 && rd < 0) return 0;
				if (ld < 0) return 1;
				if (rd < 0) return -1;
				// if neither of the values is -1,
				if (ld > rd) {
					return 1;
				} else if (ld < rd) {
					return -1;
				} else {
					return 0;
				}
			}
		});
		return result;
	}

	/**
	 * Refreshes the device list. this triggers recalculation of average rssi and
	 * distance estimation
	 */
	public synchronized void refresh() {
		for (BleDevice device : values()) {
			device.refresh();
		}
	}

}
