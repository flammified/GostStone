package nl.dobots.bluenet.ble.extended;

/**
 *
 * Include Filter. E.g. if filter is set to crownstone, only Crownstones go through the filter,
 * all other devices will be filtered out. If set to all, all devices go through, nothing is
 * filtered out. etc.
 *
 * Created by dominik on 15-7-15.
 */
public enum BleDeviceFilter {

	all,
	iBeacon,
	anyStone,
	crownstonePlug,
	crownstoneBuiltin,
	guidestone,
	setupStone
}
