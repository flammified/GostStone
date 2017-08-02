package nl.dobots.bluenet.ble.cfg;

/**
 * Configuration of characteristics, uuids, configuration values, etc. used by the Bluenet code running
 * on the crownstones and guidestone.
 *
 * Needs to be kept consistent with the values used in the bluenet code!
 *
 * Created on 15-7-15
 * Updated on 9-6-16 for Protocol version 0.4.0
 * Updated on 27-1-17 for Protocol version 0.8.0
 * @author Dominik Egger
 */
public class BluenetConfig {

	public static final int BLE_DEVICE_ADDRESS_LENGTH = 6;
	public static final int BLE_MAX_MULTIPART_NOTIFICATION_LENGTH = 512;

	//
	// UUID string should be written with lower case!
	//

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Dfu service
	public static final String DFU_SERVICE_UUID =                       "00001530-1212-efde-1523-785feabcd123";
	public static final String DFU_CONTROL_UUID =                       "00001531-1212-efde-1523-785feabcd123";

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Crownstone Service
	public static final String CROWNSTONE_SERVICE_UUID =                "24f00000-7d10-4805-bfc1-7663a01c3bff";
	// Crownstone Service - Characteristics
	public static final String CHAR_CONTROL_UUID =                      "24f00001-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_MESH_CONTROL_UUID =                 "24f00002-7d10-4805-bfc1-7663a01c3bff";
	// public static final String CHAR_MESH_READ_UUID =                    "24f00003-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_CONFIG_CONTROL_UUID =               "24f00004-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_CONFIG_READ_UUID =                  "24f00005-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_STATE_CONTROL_UUID =                "24f00006-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_STATE_READ_UUID =                   "24f00007-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_SESSION_NONCE_UUID =                "24f00008-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_RECOVERY_UUID =                     "24f00009-7d10-4805-bfc1-7663a01c3bff";

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Setup Service
	public static final String SETUP_SERVICE_UUID =                     "24f10000-7d10-4805-bfc1-7663a01c3bff";
	// Setup Service - Characteristics
	public static final String CHAR_SETUP_CONTROL_UUID =                "24f10001-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_MAC_ADDRESS_UUID =                  "24f10002-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_SESSION_KEY_UUID =                  "24f10003-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_SETUP_CONFIG_CONTROL_UUID =         "24f10004-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_SETUP_CONFIG_READ_UUID =            "24f10005-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_SETUP_GOTO_DFU_UUID =               "24f10006-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_SETUP_SESSION_NONCE_UUID =          "24f10008-7d10-4805-bfc1-7663a01c3bff";
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// General Service
	public static final String GENERAL_SERVICE_UUID =                   "24f20000-7d10-4805-bfc1-7663a01c3bff";
	// General Service - Characteristics
	public static final String CHAR_TEMPERATURE_UUID =                  "24f20001-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_RESET_UUID =                        "24f20002-7d10-4805-bfc1-7663a01c3bff";
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Power Service
	public static final String POWER_SERVICE_UUID =                     "24f30000-7d10-4805-bfc1-7663a01c3bff";
	// Power Service - Characteristics
	public static final String CHAR_PWM_UUID =                          "24f30001-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_RELAY_UUID =                        "24f30002-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_POWER_SAMPLES_UUID =                "24f30003-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_POWER_CONSUMPTION_UUID =            "24f30004-7d10-4805-bfc1-7663a01c3bff";
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Indoor Localization Service
	public static final String INDOOR_LOCALIZATION_SERVICE_UUID =       "24f40000-7d10-4805-bfc1-7663a01c3bff";
	// Indoor Localization Service - Characteristics
	public static final String CHAR_TRACK_CONTROL_UUID =                "24f40001-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_TRACKED_DEVICES_UUID =              "24f40002-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_SCAN_CONTROL_UUID =                 "24f40003-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_SCANNED_DEVICES_UUID =              "24f40004-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_RSSI_UUID =                         "24f40005-7d10-4805-bfc1-7663a01c3bff";
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Schedule Service
	public static final String SCHEDULE_SERVICE_UUID =                  "24f50000-7d10-4805-bfc1-7663a01c3bff";
	// Alert Service - Characteristics
	public static final String CHAR_CURRENT_TIME_UUID =                 "24f50001-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_WRITE_SCHEDULE_ENTRY =              "24f50002-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_LIST_SCHEDULE_ENTRIES =             "24f50003-7d10-4805-bfc1-7663a01c3bff";
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Mesh Service
	public static final String MESH_SERVICE_UUID =                      "0000fee4-0000-1000-8000-00805f9b34fb";
	// Mesh Service - Characteristics
	public static final String MESH_META_CHARACTERISTIC_UUID =          "2a1e0004-fd51-d882-8ba8-b98c0000cd1e";
	public static final String MESH_DATA_CHARACTERISTIC_UUID =          "2a1e0005-fd51-d882-8ba8-b98c0000cd1e";
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Device Information Service
//	public static final String DEVICE_INFO_SERVICE_UUID =               "0x180a";
	public static final String DEVICE_INFO_SERVICE_UUID =               "0000180a-0000-1000-8000-00805f9b34fb";
	// Device Information Service - Characteristics
//	public static final String CHAR_HARDWARE_REVISION_UUID =            "0x2a27";
//	public static final String CHAR_SOFTWARE_REVISION_UUID =            "0x2a26";
	public static final String CHAR_HARDWARE_REVISION_UUID =            "00002a27-0000-1000-8000-00805f9b34fb";
	public static final String CHAR_SOFTWARE_REVISION_UUID =            "00002a26-0000-1000-8000-00805f9b34fb";



	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// ControlMsg types
	public static final char CMD_SWITCH =                               0;    // 0x00
	public static final char CMD_PWM =                                  1;    // 0x01
	public static final char CMD_SET_TIME =                             2;    // 0x02
	public static final char CMD_GOTO_DFU =                             3;    // 0x03
	public static final char CMD_RESET =                                4;    // 0x04
	public static final char CMD_FACTORY_RESET =                        5;    // 0x05
	public static final char CMD_KEEP_ALIVE_STATE =                     6;    // 0x06
	public static final char CMD_KEEP_ALIVE =                           7;    // 0x07
	public static final char CMD_ENABLE_MESH =                          8;    // 0x08
	public static final char CMD_ENABLE_ENCRYPTION =                    9;    // 0x09
	public static final char CMD_ENABLE_IBEACON =                       10;   // 0x0A
	public static final char CMD_ENABLE_CONT_POWER_MEASURE =            11;   // 0x0B
	public static final char CMD_ENABLE_SCANNER =                       12;   // 0x0C
	public static final char CMD_SCAN_DEVICES =                         13;   // 0x0D
	public static final char CMD_USER_FEEDBACK =                        14;   // 0x0E
	public static final char CMD_SCHEDULE_ENTRY_SET =                   15;   // 0x0F
	public static final char CMD_RELAY =                                16;   // 0x10
	public static final char CMD_VALIDATE_SETUP =                       17;   // 0x11
	public static final char CMD_REQUEST_SERVICE_DATA =                 18;   // 0x12
	public static final char CMD_DISCONNECT =                           19;   // 0x13
	public static final char CMD_SET_LED =                              20;   // 0x14
	public static final char CMD_NOP =                                  21;   // 0x15
	public static final char CMD_INCREASE_TX =                          22;   // 0x16
	public static final char CMD_RESET_STATE_ERRORS =                   23;   // 0x17
	public static final char CMD_KEEP_ALIVE_MESH =                      24;   // 0x18
	public static final char CMD_MULTI_SWITCH =                         25;   // 0x19
	public static final char CMD_SCHEDULE_ENTRY_CLEAR =                 26;   // 0x1A
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Configuration types
	public static final char CONFIG_NAME =                              0;    // 0x00
	public static final char CONFIG_DEVICE_TYPE =                       1;    // 0x01
	public static final char CONFIG_ROOM =                              2;    // 0x02
	public static final char CONFIG_FLOOR =                             3;    // 0x03
	public static final char CONFIG_NEARBY_TIMEOUT =                    4;    // 0x04
	public static final char CONFIG_PWM_PERIOD =                        5;    // 0x05
	public static final char CONFIG_IBEACON_MAJOR =                     6;    // 0x06
	public static final char CONFIG_IBEACON_MINOR =                     7;    // 0x07
	public static final char CONFIG_IBEACON_PROXIMITY_UUID =            8;    // 0x08
	public static final char CONFIG_IBEACON_TXPOWER =                   9;    // 0x09
	public static final char CONFIG_WIFI_SETTINGS =                     10;   // 0x0A
	public static final char CONFIG_TX_POWER =                          11;   // 0x0B
	public static final char CONFIG_ADV_INTERVAL =                      12;   // 0x0C
	public static final char CONFIG_PASSKEY =                           13;   // 0x0D
	public static final char CONFIG_MIN_ENV_TEMP =                      14;   // 0x0E
	public static final char CONFIG_MAX_ENV_TEMP =                      15;   // 0x0F
	public static final char CONFIG_SCAN_DURATION =                     16;   // 0x10
	public static final char CONFIG_SCAN_SEND_DELAY =                   17;   // 0x11
	public static final char CONFIG_SCAN_BREAK_DURATION =               18;   // 0x12
	public static final char CONFIG_BOOT_DELAY =                        19;   // 0x13
	public static final char CONFIG_MAX_CHIP_TEMP =                     20;   // 0x14
	public static final char CONFIG_SCAN_FILTER =                       21;   // 0x15
	public static final char CONFIG_SCAN_FILTER_SEND_FRACTION =         22;   // 0x16
	public static final char CONFIG_CURRENT_LIMIT =                     23;   // 0x17
	public static final char CONFIG_MESH_ENABLED =                      24;   // 0x18
	public static final char CONFIG_ENCRYPTION_ENABLED =                25;   // 0x19
	public static final char CONFIG_IBEACON_ENABLED =                   26;   // 0x1A
	public static final char CONFIG_SCANNER_ENABLED =                   27;   // 0x1B
	public static final char CONFIG_CONT_POWER_SAMPLER_ENABLED =        28;   // 0x1C
	public static final char CONFIG_TRACKER_ENABLED =                   29;   // 0x1D
	public static final char CONFIG_ADC_SAMPLE_RATE =                   30;   // 0x1E
	public static final char CONFIG_POWER_SAMPLE_BURST_INTERVAL =       31;   // 0x1F
	public static final char CONFIG_POWER_SAMPLE_CONT_INTERVAL =        32;   // 0x20
	public static final char CONFIG_POWER_SAMPLE_CONT_NUM_SAMPLES =     33;   // 0x21
	public static final char CONFIG_CROWNSTONE_ID =                     34;   // 0x22
	public static final char CONFIG_KEY_ADMIN =                         35;   //! 0x23
	public static final char CONFIG_KEY_MEMBER =                        36;   //! 0x24
	public static final char CONFIG_KEY_GUEST =                         37;   //! 0x25
	public static final char CONFIG_DEFAULT_ON =                        38;   //! 0x26
	public static final char CONFIG_SCAN_INTERVAL =                     39;   //! 0x27
	public static final char CONFIG_SCAN_WINDOW =                       40;   //! 0x28
	public static final char CONFIG_RELAY_HIGH_DURATION =               41;   //! 0x29
	public static final char CONFIG_LOW_TX_POWER =                      42;   //! 0x2A
	public static final char CONFIG_VOLTAGE_MULTIPLIER =                43;   //! 0x2B
	public static final char CONFIG_CURRENT_MULTIPLIER =                44;   //! 0x2C
	public static final char CONFIG_VOLTAGE_ZERO =                      45;   //! 0x2D
	public static final char CONFIG_CURRENT_ZERO =                      46;   //! 0x2E
	public static final char CONFIG_POWER_ZERO =                        47;   //! 0x2F
	public static final char CONFIG_POWER_AVG_WINDOW =                  48;   //! 0x30
	public static final char CONFIG_MESH_ACCESS_ADDRESS =               49;   //! 0x31
	public static final char CONFIG_CURRENT_THRESHOLD =                 50;   //! 0x32
	public static final char CONFIG_CURRENT_THRESHOLD_DIMMER =          51;   //! 0x33
	public static final char CONFIG_DIMMER_TEMP_UP =                    52;   //! 0x34
	public static final char CONFIG_DIMMER_TEMP_DOWN =                  53;   //! 0x35

	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// State types
	public static final char STATE_RESET_COUNTER =                      128;  // 0x80
	public static final char STATE_SWITCH_STATE =                       129;  // 0x81
	public static final char STATE_ACCUMULATED_ENERGY =                 130;  // 0x82
	public static final char STATE_POWER_USAGE =                        131;  // 0x83
	public static final char STATE_TRACKED_DEVICES =                    132;  // 0x84
	public static final char STATE_SCHEDULE =                           133;  // 0x85
	public static final char STATE_OPERATION_MODE =                     134;  // 0x86
	public static final char STATE_TEMPERATURE =                        135;  // 0x87
	public static final char STATE_TIME =                               136;  // 0x88
	public static final char STATE_ERRORS =                             139;  // 0x8B
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Value Op Code
	public static final char READ_VALUE =                               0;    // 0x00
	public static final char WRITE_VALUE =                              1;    // 0x01
	public static final char NOTIFY_VALUE =                             2;    // 0x02

	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Value set at reserved bytes for alignment
	public static final char RESERVED =                                 0x00;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Mesh handles (channel)
	public static final char MESH_HANDLE_KEEP_ALIVE =                       1;    // 0x01
	public static final char MESH_HANDLE_STATE_BROADCAST =                  2;    // 0x02
	public static final char MESH_HANDLE_STATE_CHANGE =                     3;    // 0x03
	public static final char MESH_HANDLE_COMMAND =                          4;    // 0x04
	public static final char MESH_HANDLE_COMMAND_REPLY =                    5;    // 0x05
	public static final char MESH_HANDLE_SCAN_RESULT =                      6;    // 0x06
	public static final char MESH_HANDLE_BIG_DATA =                         7;    // 0x07
	public static final char MESH_HANDLE_MULTI_SWITCH =                     8;    // 0x08
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Mesh command types
	public static final int MESH_MAX_PAYLOAD_SIZE =                     92; // bytes
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Mesh command types
	public static final char MESH_CMD_CONTROL =                         0;    // 0x00;
	public static final char MESH_CMD_BEACON =                          1;    // 0x01;
	public static final char MESH_CMD_CONFIG =                          2;    // 0x02;
	public static final char MESH_CMD_STATE =                           3;    // 0x03;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Mesh reply types
	public static final char MESH_REPLY_STATUS =                        0;    // 0x00;
	public static final char MESH_REPLY_CONFIG =                        1;    // 0x01;
	public static final char MESH_REPLY_STATE =                         2;    // 0x02;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Switch intent types
	public static final char SWITCH_INTENT_SPHERE_ENTER =               0;    // 0x00;
	public static final char SWITCH_INTENT_SPHERE_EXIT =                1;    // 0x01;
	public static final char SWITCH_INTENT_ENTER =                      2;    // 0x02;
	public static final char SWITCH_INTENT_EXIT =                       3;    // 0x03;
	public static final char SWITCH_INTENT_MANUAL =                     4;    // 0x04;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// State error bits
	public static final char STATE_ERROR_POS_OVERCURRENT =              0;    // 0x00;
	public static final char STATE_ERROR_POS_OVERCURRENT_DIMMER =       1;    // 0x01;
	public static final char STATE_ERROR_POS_TEMP_CHIP =                2;    // 0x02;
	public static final char STATE_ERROR_POS_TEMP_DIMMER =              3;    // 0x03;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// iBeacon Identifiers
	public static final int APPLE_COMPANY_ID =                          0x004c;
	public static final int IBEACON_ADVERTISEMENT_ID =                  0x0215;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Crownstone
	public static final short DOBOTS_COMPANY_ID =                       (short)0x038E;
	public static final short CROWNSTONE_PLUG_SERVICE_DATA_UUID =       (short)0xC001;
	public static final short CROWNSTONE_BUILTIN_SERVICE_DATA_UUID =    (short)0xC002;
	public static final short GUIDESTONE_SERVICE_DATA_UUID =            (short)0xC003;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Reset OP codes
	public static final int RESET_DEFAULT =                             1;     // 0x01
	public static final int RESET_DFU =                                 66;    // 0x42
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Recovery code
	public static final int RECOVERY_CODE =                             0xDEADBEEF;
	public static final int FACTORY_RESET_CODE =                        0xDEADBEEF;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Verification code for ECB encryption
	public static final int CAFEBABE =                                  0xCAFEBABE;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Device Type Identifiers
	public static final int DEVICE_UNDEF =                              0;
	public static final int DEVICE_CROWNSTONE =                         1;
	public static final int DEVICE_GUIDESTONE =                         2;
	public static final int DEVICE_FRIDGE =                             3;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// constant used to convert the advertisement interval from ms to the unit expected by the
	// characteristic (increments of 0.625 ms)
	public static final double ADVERTISEMENT_INCREMENT =                0.625;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	public static final int PWM_ON =                                    100;
	public static final int PWM_OFF =                                   0;
	public static final int RELAY_ON =                                  128; // Can be 100 too actually
	public static final int RELAY_OFF =                                 0;
	public static final int SWITCH_ON =                                 100; // Fully on
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	public static final int KEEP_ALIVE_NO_ACTION =                      255;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
}
