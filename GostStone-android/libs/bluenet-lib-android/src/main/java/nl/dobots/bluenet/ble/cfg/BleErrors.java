package nl.dobots.bluenet.ble.cfg;

/**
 * Defines error values used and created by the bluenet library
 *
 * Created on 16-9-15
 * @author Dominik Egger
 */
public class BleErrors {

	// BleCore
	public static final int ERROR_DISCOVERY_FAILED = 200;
	public static final int ERROR_CHARACTERISTIC_READ_FAILED = 201;
	public static final int ERROR_CHARACTERISTIC_WRITE_FAILED = 202;
	public static final int ERROR_NOT_INITIALIZED = 203;
	public static final int ERROR_NEVER_CONNECTED = 204;
	public static final int ERROR_NOT_CONNECTED = 205;
	public static final int ERROR_ALREADY_DISCOVERING = 206;
	public static final int ERROR_ALREADY_SCANNING = 207;
	public static final int ERROR_SCAN_FAILED = 208;
	public static final int ERROR_NOT_SCANNING = 209;
	public static final int ERROR_CONNECT_FAILED = 210;
	public static final int ERROR_RECONNECT_FAILED = 211;
	public static final int ERROR_SERVICE_NOT_FOUND = 212;
	public static final int ERROR_CHARACTERISTIC_NOT_FOUND = 213;
	public static final int ERROR_WRITE_VALUE_NOT_SET = 214;
	public static final int ERROR_WRITE_FAILED = 215;
	public static final int ERROR_DEVICE_NOT_FOUND = 216;
	public static final int ERROR_WRONG_LENGTH_PARAMETER = 217;
	public static final int ERROR_EMPTY_VALUE = 218;
	public static final int ERROR_BLUETOOTH_INITIALIZATION_CANCELLED = 219;
	public static final int ERROR_BLUETOOTH_TURNED_OFF = 220;
	public static final int ERROR_BLUETOOTH_NOT_ENABLED = 221;
	public static final int ERROR_DISCONNECT_FAILED = 222;
	public static final int ERROR_NO_ADDRESS_PROVIDED = 223;
	public static final int ERROR_STILL_CONNECTED = 224;
	public static final int ERROR_RETURN_VALUE_PARSING = 225;
	public static final int ERROR_ADVERTISEMENT_PARSING = 226;
	public static final int ERROR_BLE_HARDWARE_MISSING = 227;
	public static final int ERROR_NOTIFICATION_DESCRIPTOR_NOT_FOUND = 228;
	public static final int ERROR_SUBSCRIBE_NOTIFICATION_FAILED = 229;
	public static final int ERROR_DESCRIPTOR_READ_FAILED = 230;
	public static final int ERROR_UNSUBSCRIBE_FAILED = 231;
	public static final int ERROR_SUBSCRIBE_FAILED = 232;
	public static final int ERROR_DESCRIPTOR_SET_FAILED = 233;
	public static final int ERROR_UNSUBSCRIBE_NOTIFICATION_FAILED = 234;
	public static final int ERROR_BLE_PERMISSION_MISSING = 235;
	public static final int WRONG_CALLBACK = 236;
	public static final int NO_SUBSCRIBER_ID = 237;
	public static final int ERROR_RECOVER_MODE_DISABLED = 238;
	public static final int ERROR_NOT_IN_RECOVERY_MODE = 239;
	public static final int ERROR_SETUP_FAILED = 240;
	public static final int ERROR_VALIDATION_FAILED = 241;
	public static final int ERROR_LOCATION_SERVICES_TURNED_OFF = 242;
	public static final int ERROR_SETUP_CANCELED = 243;
	public static final int ERROR_WRONG_PAYLOAD_SIZE = 244;
	public static final int ERROR_MSG_PARSING = 245;
	public static final int ERROR_REFRESH_FAILED = 246;
	public static final int ERROR_WRONG_PAYLOAD_TYPE = 247;


	// BleBase
	public static final int ENCRYPTION_ERROR = 400;

	// BleExt
	public static final int ERROR_WRONG_STATE = 500;
	public static final int ERROR_JSON_PARSING = 501;


	// BluetoothGatt errors (see https://android.googlesource.com/platform/external/bluetooth/bluedroid/+/master/stack/include/gatt_api.h)
	// 8    GATT_CONN_TIMEOUT
	//      [01.11.17] (Bart @ oneplus 3) When I get this error, it continuously fails.
	// 133  GATT_ERROR
	//      [01.11.17] This error seems rather common, retry usually helps.
	// 257  ??
	//      [01.11.17] (Bart @ oneplus 3) When I get this error, not long after i will get scan error 2
	//                                  And scanning completely stops working.

	// ScanCallback errors
	// 1    SCAN_FAILED_ALREADY_STARTED
	//      [01.11.17] (Bart @ nexus 4, CM) Scan stop failed, so retrying to stop helps here.
	// 2    SCAN_FAILED_APPLICATION_REGISTRATION_FAILED
	//      [01.11.17] (Bart @ oneplus 3) When i get this error, scanning completely fails. Retry may help to start scanning again, but no results come in anymore.
}
