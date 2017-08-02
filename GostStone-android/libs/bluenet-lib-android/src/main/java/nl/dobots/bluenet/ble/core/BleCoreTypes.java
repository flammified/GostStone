package nl.dobots.bluenet.ble.core;

import java.util.UUID;

public class BleCoreTypes {

	public static final UUID CLIENT_CONFIGURATION_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
	
	// Characteristic properties
	public static final String CHARACTERISTIC_PROP_EXTENDED_PROPERTIES = "extendedProperties";
	public static final String CHARACTERISTIC_PROP_SIGNED_WRITE = "signedWrite";
	public static final String CHARACTERISTIC_PROP_INDICATE = "indicate";
	public static final String CHARACTERISTIC_PROP_NOTIFY = "notify";
	public static final String CHARACTERISTIC_PROP_WRITE = "write";
	public static final String CHARACTERISTIC_PROP_WRITE_NO_RESPONSE = "writeNoResponse";
	public static final String CHARACTERISTIC_PROP_READ = "read";
	public static final String CHARACTERISTIC_PROP_BROADCAST = "broadcast";

	// JSON property fields
	public static final String PROPERTY_VALUE = "value";
	public static final String PROPERTY_SCAN_RESULT = "scanResult";
	public static final String PROPERTY_ADVERTISEMENT = "advertisement";
	public static final String PROPERTY_RSSI = "rssi";
	public static final String PROPERTY_SERVICES_LIST = "services";
	public static final String PROPERTY_CHARACTERISTICS_LIST = "characteristics";
	public static final String PROPERTY_DESCRIPTORS = "descriptors";
	public static final String PROPERTY_DESCRIPTOR_UUID = "descriptorUUID";
	public static final String PROPERTY_PROPERTIES = "properties";
	public static final String PROPERTY_CHARACTERISTIC_UUID = "characteristicUUID";
	public static final String PROPERTY_SERVICE_UUID = "serviceUUID";
	public static final String PROPERTY_STATUS = "status";
	public static final String PROPERTY_NAME = "name";
	public static final String PROPERTY_ADDRESS = "address";

	// JSON status values
	public static final String STATUS_DISCOVERED = "discovered";
	public static final String STATUS_WRITTEN = "written";

	public static final String CHARACTERISTIC_UNSUBSCRIBED = "unsubscribed";
	public static final String CHARACTERISTIC_SUBSCRIBED = "subscribed";

	// EVENT status values
	public static final String EVT_BLUETOOTH_OFF = "BLUETOOTH_OFF";
	public static final String EVT_BLUETOOTH_ON = "BLUETOOTH_ON";
	public static final String EVT_LOCATION_SERVICES_ON = "LOCATION_SERVICES_ON";
	public static final String EVT_LOCATION_SERVICES_OFF = "LOCATION_SERVICES_OFF";

}
