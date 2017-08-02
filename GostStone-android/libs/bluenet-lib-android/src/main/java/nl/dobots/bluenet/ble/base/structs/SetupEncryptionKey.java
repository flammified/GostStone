package nl.dobots.bluenet.ble.base.structs;

import nl.dobots.bluenet.ble.base.BleBaseEncryption;
import nl.dobots.bluenet.utils.BleUtils;

public class SetupEncryptionKey extends EncryptionKeys {

	private String _setupKey;
	private byte[] _setupKeyBytes;

	public SetupEncryptionKey(byte[] setupKey) {
		_setupKeyBytes = setupKey;
		_setupKey = null;
		if (_setupKeyBytes != null) {
			_setupKey = BleUtils.bytesToHexString(_setupKeyBytes);
		}
	}

	public String getSetupKeyString() {
		return _setupKey;
	}

	public byte[] getSetupKey() {
		return _setupKeyBytes;
	}

	public byte[] getKey(char accessLevel) {
		return getSetupKey();
	}

	public static byte[] getSetupKey(SetupEncryptionKey keys) {
		if (keys == null) {
			return null;
		}
		byte [] key = keys.getSetupKey();
		return key;
	}

	public KeyAccessLevelPair getHighestKey() {
		return new KeyAccessLevelPair(getSetupKey(), BleBaseEncryption.ACCESS_LEVEL_SETUP);
	}

}
