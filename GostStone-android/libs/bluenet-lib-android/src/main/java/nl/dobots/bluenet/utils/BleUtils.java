package nl.dobots.bluenet.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.util.Base64;

import nl.dobots.bluenet.ble.cfg.BluenetConfig;

public class BleUtils {

	public static final String BASE_UUID_START = "0000";
	public static final String BASE_UUID_END = "-0000-1000-8000-00805f9b34fb";
	private static final String TAG = BleUtils.class.getCanonicalName();

	private static final int STR_ADDRESS_LENGTH = 17;
	// String Bluetooth address, such as "00:43:A8:23:10:F0"
	private static final int ADDRESS_LENGTH = 6;

	public static String uuidToString(UUID uuid) {
		String uuidString = uuid.toString();

		if (uuidString.startsWith(BASE_UUID_START) && uuidString.endsWith(BASE_UUID_END))
		{
			return uuidString.substring(4, 8);
		}

		return uuidString;
	}

	public static UUID[] stringToUuid(String[] uuids) {
		UUID[] result = new UUID[uuids.length];

		for (int i = 0; i < uuids.length; ++i) {
			result[i] = stringToUuid(uuids[i]);
		}
		return result;
	}

	public static UUID stringToUuid(String uuidStr) {
		if (uuidStr.length() == 4) {
			uuidStr = BASE_UUID_START + uuidStr + BASE_UUID_END;
		}
		return UUID.fromString(uuidStr);
	}

	public static byte[] uuidToBytes(String uuidStr) {
		return uuidToBytes(stringToUuid(uuidStr));
	}

	public static byte[] uuidToBytes(UUID uuid) {
		ByteBuffer bb = ByteBuffer.allocate(16);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		bb.putLong(uuid.getLeastSignificantBits());
		bb.putLong(uuid.getMostSignificantBits());
		return bb.array();
	}

	public static String bytesToUuid(byte[] bytes) {
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		long lsb = bb.getLong();
		long msb = bb.getLong();
		UUID uuid = new UUID(msb, lsb);
		return uuidToString(uuid);
	}

	public static byte[] encodedStringToBytes(String encoded) {
		return Base64.decode(encoded, Base64.NO_WRAP);
	}

	public static String bytesToEncodedString(byte[] bytes) {
		return Base64.encodeToString(bytes, Base64.NO_WRAP);
	}

	public static int byteArrayToInt(byte[] bytes) {
		return byteArrayToInt(bytes, 0);
	}

	public static int byteArrayToInt(byte[] bytes, int offset) {
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		return bb.getInt(offset);
	}

	public static int byteArrayToShort(byte[] bytes) {
		return byteArrayToShort(bytes, 0);
	}

	public static int byteArrayToShort(byte[] bytes, int offset) {
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		return bb.getShort(offset);
	}

	public static float byteArrayToFloat(byte[] bytes) {
		return byteArrayToFloat(bytes, 0);
	}

	public static float byteArrayToFloat(byte[] bytes, int offset) {
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		return bb.getFloat(offset);
	}

	public static byte[] intToByteArray(int val) {
		ByteBuffer bb = ByteBuffer.allocate(4);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		bb.putInt(val);
		return bb.array();
	}

	public static byte[] shortToByteArray(int value) {
		ByteBuffer bb = ByteBuffer.allocate(2);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		bb.putShort((short) value);
		return bb.array();
	}

	public static byte[] floatToByteArray(float value) {
		ByteBuffer bb = ByteBuffer.allocate(4);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		bb.putFloat(value);
		return bb.array();
	}

	public static int toUint8(byte b) {
		return b & 0xFF;
	}

	public static int toUint16(int num) {
		return num & 0xFFFF;
	}

	public static long toUint32(int num) {
		return num & 0xFFFFFFFFL;
	}

	public static byte[] uint32ToByteArray(long num) {
		byte[] bytes = new byte[4];
		bytes[0] = (byte) ((num >> 0) & 0xFF);
		bytes[1] = (byte) ((num >> 8) & 0xFF);
		bytes[2] = (byte) ((num >> 16) & 0xFF);
		bytes[3] = (byte) ((num >> 24) & 0xFF);
		return bytes;
	}

	public static byte[] hexStringToBytes(String hex) {
		byte[] result = new byte[hex.length() / 2];
		for (int i = 0; i < result.length; ++i) {
			result[i] = Integer.valueOf(hex.substring(2*i, 2*i+2), 16).byteValue();
		}
		return result;
	}

	public static String bytesToHexString(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}

	public static byte[] addressToBytes(String address) {
		if (address == null || address.length() != STR_ADDRESS_LENGTH) {
			return null;
		}

		byte[] result = new byte[ADDRESS_LENGTH];

		try {
			for (int i = 0; i < ADDRESS_LENGTH; ++i) {
//				result[i] = Byte.valueOf(address.substring(3 * i, 3 * i + 2), 16);
				result[ADDRESS_LENGTH-1-i] = Integer.valueOf(address.substring(3 * i, 3 * i + 2), 16).byteValue();
			}
		} catch (java.lang.NumberFormatException e) {
			BleLog.getInstance().LOGe(TAG, "Wrong address format: " + address);
			e.printStackTrace();
			result = null;
		}

		return result;
	}

	public static String bytesToAddress(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes) {
			sb.append(String.format("%02X:", b));
		}
		sb.deleteCharAt(sb.length()-1); // remove last semicolon
		return sb.toString();
	}

	public static boolean isBitSet(int value, int bit) {
		return (value & (1 << bit)) > 0;
	}

	public static int clearBit(int value, int bit) {
		return value & ~(1 << bit);
	}

	public static byte[] reverse(byte[] array) {
		byte[] result = new byte[array.length];
		for (int i = 0; i < array.length; ++i) {
			result[i] = array[array.length - (i+1)];
		}
		return result;
	}

	// Handy for logging, does not turn byte values into the corresponding ascii values!
	public static String bytesToString(byte[] bytes) {
		if (bytes == null) {
			return "";
		}
		if (bytes.length == 0) {
			return "[]";
		}
		String str = "[" + toUint8(bytes[0]);
		for (int i=1; i<bytes.length; i++) {
			str += ", " + toUint8(bytes[i]);
		}
		str += "]";
		return str;
	}

	public static boolean isValidAddress(String address) {
		return BluetoothAdapter.checkBluetoothAddress(address);
	}

}
