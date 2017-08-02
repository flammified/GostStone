package nl.dobots.bluenet.ble.extended;

/**
 * Enum to keep track of the device's connection state
 *
 * Created on 15-7-15
 * @author Dominik Egger
 */
public enum BleDeviceConnectionState {

	uninitialized,
	initialized,
	scanning,
	connecting,
	connected,
	disconnecting,

}
