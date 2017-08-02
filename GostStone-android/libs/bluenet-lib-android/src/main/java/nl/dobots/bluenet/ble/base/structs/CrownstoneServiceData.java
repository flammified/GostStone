package nl.dobots.bluenet.ble.base.structs;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import nl.dobots.bluenet.ble.base.BleBaseEncryption;
import nl.dobots.bluenet.utils.BleLog;
import nl.dobots.bluenet.utils.BleUtils;

/**
 * Copyright (c) 2016 Dominik Egger <dominik@dobots.nl>. All rights reserved.
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
 * Created on 9-6-16
 *
 * @author Dominik Egger
 */
public class CrownstoneServiceData extends JSONObject {

	// use BleLog.getInstance().setLogLevelPerTag(CrownstoneServiceData.class.getCanonicalName(), <NEW_LOG_LEVEL>)
	// to change the log level
	private static final int LOG_LEVEL = Log.WARN;

	private static final String TAG = CrownstoneServiceData.class.getCanonicalName();

	public CrownstoneServiceData() {
		super();
	}

	public CrownstoneServiceData(String json) throws JSONException {
		super(json);
	}

	public CrownstoneServiceData(JSONObject obj) throws JSONException {
		this(obj.toString());
	}

	public boolean parseBytes(byte[] bytes, boolean encrypted, byte[] key) {
		// Includes the service UUID (first 2 bytes)
		getLogger().LOGv(TAG, "serviceData: " + BleUtils.bytesToString(bytes));
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		try {
			setServiceUuid(BleUtils.toUint16(bb.getShort()));

			bb.mark();
			int firmwareVersion = BleUtils.toUint8(bb.get());
			setFirmwareVersion(firmwareVersion);

			// First parse without decrypting
			if (!parseDecryptedData(bytes, 3, firmwareVersion)) {
				return false;
			}
			// Check if data is setup mode data, if so, we don't have to decrypt
			if (isSetupPacket()) {
				return true;
			}

			if (encrypted) {
				firmwareVersion = BleUtils.toUint8(bytes[2]);
				//byte[] decryptedBytes = BleBaseEncryption.decryptEcb(bytes, bb.position(), key);
				byte[] decryptedBytes = BleBaseEncryption.decryptEcb(bytes, 3, key);
				if (decryptedBytes == null) {
					return false;
				}
				// Parse again, but now with decrypted data
				if (!parseDecryptedData(decryptedBytes, 0, firmwareVersion)) {
					return false;
				}
			}
			return true;
		} catch (BufferUnderflowException e) {
			getLogger().LOGv(TAG, "failed to parse");
//			e.printStackTrace();
			return false;
		}
	}

	private boolean parseDecryptedData(byte[] bytes, int offset, int firmwareVersion) {
		if (bytes.length - offset < 16) return false;

		ByteBuffer bb = ByteBuffer.wrap(bytes, offset, bytes.length-offset);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		byte[] test = new byte[16];
		bb.mark();
		bb.get(test);
		getLogger().LOGv(TAG, "parseDecryptedData: " + BleUtils.bytesToString(test));
		bb.reset();

		switch(firmwareVersion) {
			case 1: {
				int crownstoneId = BleUtils.toUint16(bb.getShort());
				setSwitchState(BleUtils.toUint8(bb.get()));
				byte eventBitmask = bb.get();
				setEventBitmask(eventBitmask);
				setTemperature(bb.get());
				setPowerUsage(bb.getInt());
				setAccumulatedEnergy(bb.getInt());
				byte[] randomBytes = new byte[3];
				bb.get(randomBytes);
				setRandomBytes(randomBytes);

				setRelayState(BleUtils.isBitSet(getSwitchState(), 7));
				setPwm(getSwitchState() & ~(1 << 7));

				if (isExternalData(eventBitmask)) {
					setCrownstoneId(-1);
					setCrownstoneStateId(crownstoneId);
				} else {
					setCrownstoneId(crownstoneId);
					setCrownstoneStateId(-1);
				}
				return true;
			}
			default: {
				// TODO: this is deprecated (for advertisements from before firmwareVersion)
				bb.reset();
				setFirmwareVersion(0);
				setCrownstoneId(BleUtils.toUint16(bb.getShort()));
				setCrownstoneStateId(bb.getShort());
				setSwitchState((bb.get() & 0xff));
				setEventBitmask(bb.get());
				setTemperature(bb.get());
				bb.get(); // skip reserved
				setPowerUsage(bb.getInt());
				setAccumulatedEnergy(bb.getInt());

				setRelayState(BleUtils.isBitSet(getSwitchState(), 7));
				setPwm(getSwitchState() & ~(1 << 7));
				return true;
			}
		}
//		return false;
	}

	private boolean isSetupPacket() {
		getLogger().LOGv(TAG, "setupbit=" + isSetupMode(getEventBitmask()) + " id=" + getCrownstoneId() + " switch=" + getSwitchState() + " power=" + getPowerUsage() + " energy=" + getAccumulatedEnergy());
		return (isSetupMode(getEventBitmask()) && getCrownstoneId() == 0 && getSwitchState() == 0 && getPowerUsage() == 0 && getAccumulatedEnergy() == 0);
	}


	public int getFirmwareVersion() {
		try {
			return getInt("firmwareVersion");
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "no firmware version found");
			return 0;
		}
	}

	private void setFirmwareVersion(int firmwareVersion) {
		try {
			put("firmwareVersion", firmwareVersion);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "failed to add firmware version");
			e.printStackTrace();
		}
	}

	public int getServiceUuid() {
		try {
			return getInt("serviceUuid");
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "no service uuid found");
			return 0;
		}
	}

	private void setServiceUuid(int serviceUuid) {
		try {
			put("serviceUuid", serviceUuid);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "failed to add service uuid");
			e.printStackTrace();
		}
	}

	public int getCrownstoneId() {
		try {
			return getInt("crownstoneId");
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "no crownstone id found");
			return 0;
		}
	}

	private void setCrownstoneId(int crownstoneId) {
		try {
			put("crownstoneId", crownstoneId);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "failed to add crownstone id");
			e.printStackTrace();
		}
	}

	public int getCrownstoneStateId() {
		try {
			return getInt("crownstoneStateId");
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "no crownstone state id found");
			return 0;
		}
	}

	private void setCrownstoneStateId(int crownstoneStateId) {
		try {
			put("crownstoneStateId", crownstoneStateId);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "failed to add crownstone state id");
			e.printStackTrace();
		}
	}

	public int getSwitchState() {
		try {
			return getInt("switchState");
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "no switch state found");
			return 0;
		}
	}

	private void setSwitchState(int switchState) {
		try {
			put("switchState", switchState);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "failed to add switch state");
			e.printStackTrace();
		}
	}

	public boolean getRelayState() {
		try {
			return getBoolean("relayState");
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "no relay state found");
			return false;
		}
	}

	private void setRelayState(boolean relayState) {
		try {
			put("relayState", relayState);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "failed to add relay state");
			e.printStackTrace();
		}
	}

	public int getPwm() {
		try {
			return getInt("pwm");
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "no pwm found");
			return 0;
		}
	}

	private void setPwm(int pwm) {
		try {
			put("pwm", pwm);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "failed to add pwm");
			e.printStackTrace();
		}
	}

	public byte getEventBitmask() {
		try {
			return (byte)getInt("eventBitmask");
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "no event bitmask found");
			return 0;
		}
	}

	public boolean isNewData() { return isNewData(getEventBitmask()); }
	public static boolean isNewData(byte eventBitmask) {
		return ((eventBitmask & (1L << 0)) != 0);
	}

	public boolean isExternalData() { return isExternalData(getEventBitmask()); }
	public static boolean isExternalData(byte eventBitmask) {
		return ((eventBitmask & (1L << 1)) != 0);
	}

	public boolean isErrorBit() { return isErrorBit(getEventBitmask()); }
	public static boolean isErrorBit(byte eventBitmask) {
		return ((eventBitmask & (1L << 2)) != 0);
	}

//	public boolean isSetupMode() { return isSetupMode(getEventBitmask()); }
//	public static boolean isSetupMode(byte eventBitmask) {
//		return ((eventBitmask & (1L << 7)) != 0);
//	}
	public boolean isSetupMode() { return isSetupPacket(); }
//	public static boolean isSetupMode(byte eventBitmask) {
//		return isSetupPacket();
//	}
	private static boolean isSetupMode(byte eventBitmask) {
		return ((eventBitmask & (1L << 7)) != 0);
	}

	private void setEventBitmask(byte eventBitmask) {
		try {
			put("eventBitmask", eventBitmask);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "failed to add event bitmask");
			e.printStackTrace();
		}
	}

	public byte getTemperature() {
		try {
			return (byte)getInt("temperature");
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "no temperature found");
			return 0;
		}
	}

	private void setTemperature(byte temperature) {
		try {
			put("temperature", temperature);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "failed to add temperature");
			e.printStackTrace();
		}
	}

	public int getPowerUsage() {
		try {
			return getInt("powerUsage");
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "no power usage found");
			return 0;
		}
	}

	private void setPowerUsage(int powerUsage) {
		try {
			put("powerUsage", powerUsage);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "failed to add power usage");
			e.printStackTrace();
		}
	}                                                                                                                                                                                                                                                                                                                                                                                                     

	public int getAccumulatedEnergy() {
		try {
			return getInt("accumulatedEnergy");
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "no accumulated energy found");
			return 0;
		}
	}

	private void setAccumulatedEnergy(int accumulatedEnergy) {
		try {
			put("accumulatedEnergy", accumulatedEnergy);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "failed to add accumulated energy");
			e.printStackTrace();
		}
	}

	public String getRandomBytes() {
		try {
			return getString("randomBytes");
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "no random bytes found");
			return null;
		}
	}

	private void setRandomBytes(byte[] randomBytes) {
		try {
			put("randomBytes", BleUtils.bytesToEncodedString(randomBytes));
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "failed to add random bytes");
			e.printStackTrace();
		}
	}

	private BleLog getLogger() {
		BleLog logger = BleLog.getInstance();
		// update the log level to the default of this class if it hasn't been set already
		if (logger.getLogLevel(TAG) == null) {
			logger.setLogLevelPerTag(TAG, LOG_LEVEL);
		}
		return logger;
	}
}
