package nl.dobots.bluenet.ble.base;

import android.util.Log;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import nl.dobots.bluenet.ble.base.structs.EncryptionKeys;
import nl.dobots.bluenet.ble.base.structs.EncryptionSessionData;
import nl.dobots.bluenet.ble.cfg.BluenetConfig;
import nl.dobots.bluenet.utils.BleLog;
import nl.dobots.bluenet.utils.BleUtils;
import nl.dobots.bluenet.utils.Logging;

/**
 * Copyright (c) 2015 Bart van Vliet <bart@dobots.nl>. All rights reserved.
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
 * Created on 28-9-16
 *
 * @author Bart van Vliet
 */
public class BleBaseEncryption {

	// use BleLog.getInstance().setLogLevelPerTag(BleBaseEncryption.class.getCanonicalName(), <NEW_LOG_LEVEL>)
	// to change the log level
	private static final int LOG_LEVEL = Log.WARN;

	private static final String TAG = BleBaseEncryption.class.getCanonicalName();

	public static final char ACCESS_LEVEL_ADMIN =               0;
	public static final char ACCESS_LEVEL_MEMBER =              1;
	public static final char ACCESS_LEVEL_GUEST =               2;
	public static final char ACCESS_LEVEL_SETUP =               100;
	public static final char ACCESS_LEVEL_NOT_SET =             201;
	public static final char ACCESS_LEVEL_HIGHEST_AVAILABLE =   202;
	public static final char ACCESS_LEVEL_ENCRYPTION_DISABLED = 255;


	public static final int AES_BLOCK_SIZE = 16;
	private static final int VALIDATION_KEY_LENGTH = 4;
	private static final int SESSION_NONCE_LENGTH = 5;
	private static final int PACKET_NONCE_LENGTH = 3;
	private static final int ACCESS_LEVEL_LENGTH = 1;

//	private Cipher _cipher = null;
//	private boolean _cipherInitialized = false;

//	public BleBaseEncryption() {
//		try {
//			_cipher = Cipher.getInstance("AES/CTR/NoPadding");
//		} catch (GeneralSecurityException e) {
//			getLogger().LOGe(TAG, "Encryption not available");
//			e.printStackTrace();
//		}
//	}

//	public boolean setKey(String keyStr) {
//		Key key = new SecretKeySpec(keyStr.getBytes(Charset.forName("UTF-8")), "AES");
//		try {
//			_cipher.init(Cipher.ENCRYPT_MODE, key);
//			_cipherInitialized = true;
//			return true;
//		} catch (InvalidKeyException e) {
//			getLogger().LOGe(TAG, "Encryption not available");
//			e.printStackTrace();
//		}
//		return false;
//	}

	public static byte[] encryptCtr(byte[] payloadData, byte[] sessionNonce, byte[] validationKey, byte[] key, char accessLevel) {
		if (payloadData == null || payloadData.length < 1) {
			getLogger().LOGw(TAG, "wrong data length");
			return null;
		}
		if (sessionNonce == null || sessionNonce.length != SESSION_NONCE_LENGTH) {
			getLogger().LOGw(TAG, "wrong session nonce length");
			return null;
		}
		if (validationKey == null || validationKey.length != VALIDATION_KEY_LENGTH) {
			getLogger().LOGw(TAG, "wrong validation key length");
			return null;
		}
		if (key == null || key.length != AES_BLOCK_SIZE) {
			getLogger().LOGw(TAG, "wrong key length");
			return null;
		}
		getLogger().LOGv(TAG, "payloadData: " + BleUtils.bytesToString(payloadData));

		// Packet nonce is randomly generated.
		SecureRandom random = new SecureRandom();
		byte[] packetNonce = new byte[PACKET_NONCE_LENGTH];
		random.nextBytes(packetNonce);
		getLogger().LOGv(TAG, "packetNonce: " + BleUtils.bytesToString(packetNonce));

		// Create iv by concatting session nonce and packet nonce.
		byte[] iv = new byte[AES_BLOCK_SIZE];
		System.arraycopy(packetNonce, 0, iv, 0, PACKET_NONCE_LENGTH);
		System.arraycopy(sessionNonce, 0, iv, PACKET_NONCE_LENGTH, SESSION_NONCE_LENGTH);

		// Allocate to-be-encrypted payload array, fill with payloadData prefixed with validation key.
		int payloadLen = VALIDATION_KEY_LENGTH + (payloadData.length);
		int paddingLen = ((AES_BLOCK_SIZE - (payloadLen % AES_BLOCK_SIZE)) % AES_BLOCK_SIZE);
		byte[] payload = new byte[payloadLen+paddingLen];
		//Arrays.fill(payload, (byte)0); // Already zeroes by default
		System.arraycopy(validationKey, 0, payload, 0, VALIDATION_KEY_LENGTH);
		System.arraycopy(payloadData, 0, payload, VALIDATION_KEY_LENGTH, payloadData.length);
		getLogger().LOGv(TAG, "payload: " + BleUtils.bytesToString(payload));

		// Allocate output array
		byte[] encryptedData = new byte[PACKET_NONCE_LENGTH + ACCESS_LEVEL_LENGTH + payloadLen + paddingLen];
		System.arraycopy(packetNonce, 0, encryptedData, 0, PACKET_NONCE_LENGTH);
		encryptedData[PACKET_NONCE_LENGTH] = (byte)accessLevel;

		// Encrypt payload
		try {
			Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
			getLogger().LOGv(TAG, "IV before: " + BleUtils.bytesToString(cipher.getIV()));
			// doFinal(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset)
			cipher.doFinal(payload, 0, payload.length, encryptedData, PACKET_NONCE_LENGTH+ACCESS_LEVEL_LENGTH);
			getLogger().LOGv(TAG, "IV after: " + BleUtils.bytesToString(cipher.getIV()));
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
			return null;
		}
//		try {
//			System.arraycopy(payload, 0, encryptedData, PACKET_NONCE_LENGTH+ACCESS_LEVEL_LENGTH, payload.length);
//			Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
//			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
//			for (int ctr=0; ctr<payload.length/16; ctr++) {
//				iv[AES_BLOCK_SIZE-1] = (byte)ctr;
//				getLogger().LOGd(TAG, "IV: " + BleUtils.bytesToString(iv));
//				byte[] encryptedIv = new byte[AES_BLOCK_SIZE];
//				cipher.doFinal(iv, 0, iv.length, encryptedIv, 0);
//				for (int i=0; i<AES_BLOCK_SIZE; i++) {
//					encryptedData[i+PACKET_NONCE_LENGTH+ACCESS_LEVEL_LENGTH] = (byte)(0xff & ((int)encryptedData[i+PACKET_NONCE_LENGTH+ACCESS_LEVEL_LENGTH]) ^ ((int)encryptedIv[i]));
//				}
//			}
//		} catch (GeneralSecurityException e) {
//			e.printStackTrace();
//			return null;
//		}
		getLogger().LOGv(TAG, "encryptedData: " + BleUtils.bytesToString(encryptedData));
		return encryptedData;
	}

	public static byte[] decryptCtr(byte[] encryptedData, byte[] sessionNonce, byte[] validationKey, EncryptionKeys keys) {
		if (encryptedData == null || encryptedData.length < PACKET_NONCE_LENGTH + ACCESS_LEVEL_LENGTH + AES_BLOCK_SIZE) {
			getLogger().LOGw(TAG, "wrong data length");
			return null;
		}
		if (sessionNonce == null || sessionNonce.length != SESSION_NONCE_LENGTH) {
			getLogger().LOGw(TAG, "wrong session nonce length");
			return null;
		}
		if (validationKey == null || validationKey.length != VALIDATION_KEY_LENGTH) {
			getLogger().LOGw(TAG, "wrong validation key length");
			return null;
		}
		if (keys == null) {
			getLogger().LOGw(TAG, "no keys supplied");
			return null;
		}
		getLogger().LOGv(TAG, "encryptedData: " + BleUtils.bytesToString(encryptedData));

		byte[] decryptedData = new byte[encryptedData.length - PACKET_NONCE_LENGTH - ACCESS_LEVEL_LENGTH];
		if (decryptedData.length % AES_BLOCK_SIZE != 0) {
			getLogger().LOGv(TAG, "encrypted data length must be multiple of 16");
			return null;
		}

		char accessLevel = (char)BleUtils.toUint8(encryptedData[PACKET_NONCE_LENGTH]);
		getLogger().LOGv(TAG, "accessLevel: " + (int)accessLevel);
		byte[] key = keys.getKey(accessLevel);
		if (key == null || key.length != AES_BLOCK_SIZE) {
			getLogger().LOGw(TAG, "wrong key length: " + key);
			return null;
		}

		// Create iv by concatting session nonce and packet nonce
		byte[] iv = new byte[AES_BLOCK_SIZE];
		System.arraycopy(encryptedData, 0, iv, 0, PACKET_NONCE_LENGTH);
		System.arraycopy(sessionNonce, 0, iv, PACKET_NONCE_LENGTH, SESSION_NONCE_LENGTH);

		// Decrypt encrypted payload
		try {
			Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
			getLogger().LOGv(TAG, "IV: " + BleUtils.bytesToString(cipher.getIV()));
			// doFinal(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset)
			cipher.doFinal(encryptedData, PACKET_NONCE_LENGTH+ACCESS_LEVEL_LENGTH, decryptedData.length, decryptedData, 0);
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
			return null;
		}

		// Check validation key
		for (int i=0; i<VALIDATION_KEY_LENGTH; i++) {
			if (decryptedData[i] != validationKey[i]) {
				getLogger().LOGw(TAG, "incorrect validation key");
				return null;
			}
		}

		byte[] payloadData = new byte[decryptedData.length-VALIDATION_KEY_LENGTH];
		System.arraycopy(decryptedData, VALIDATION_KEY_LENGTH, payloadData, 0, payloadData.length);
		return payloadData;
	}

	public static byte[] decryptEcb(byte[] payloadData, byte[] key) {
		return decryptEcb(payloadData, 0 , key);
	}

	public static byte[] decryptEcb(byte[] payloadData, int inputOffset, byte[] key) {
		if (inputOffset < 0) {
			return null;
		}
		if (payloadData == null || (payloadData.length-inputOffset) < AES_BLOCK_SIZE) {
			getLogger().LOGw(TAG, "payload data too short");
			return null;
		}
		int length = payloadData.length-inputOffset;
		if (length % AES_BLOCK_SIZE != 0) {
			getLogger().LOGw(TAG, "wrong payload data length");
			return null;
		}
		byte[] decryptedData = new byte[length];
		if (key == null) {
			// If there is no key set, then simply do not decrypt.
			System.arraycopy(payloadData, inputOffset, decryptedData, 0, length);
			return decryptedData;
		}
		if (key.length != AES_BLOCK_SIZE) {
			getLogger().LOGw(TAG, "wrong key length: " + key.length);
			return null;
		}
		try {
			Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
			cipher.doFinal(payloadData, inputOffset, length, decryptedData, 0);
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
			return null;
		}
		return decryptedData;
	}

	public static EncryptionSessionData getSessionData(byte[] decryptedData) {
		return getSessionData(decryptedData, true);
	}

	public static EncryptionSessionData getSessionData(byte[] decryptedData, boolean wasEncrypted) {
		if (decryptedData == null) {
			return null;
		}
		// When the data was encrypted, the first 4 bytes should be CAFEBABE, to check if encryption succeeded.
		if (wasEncrypted) {
			if (decryptedData.length < VALIDATION_KEY_LENGTH + SESSION_NONCE_LENGTH) {
				getLogger().LOGe(TAG, "invalid session data length: " + BleUtils.bytesToString(decryptedData));
				return null;
			}
			// Bytes 0-3 (validation key) should be CAFEBABE
			if (BleUtils.byteArrayToInt(decryptedData) != BluenetConfig.CAFEBABE) {
				getLogger().LOGe(TAG, "validation failed: " + BleUtils.bytesToString(decryptedData));
				return null;
			}
			return _getSessionData(decryptedData, VALIDATION_KEY_LENGTH);
		}
		else {
			if (decryptedData.length < SESSION_NONCE_LENGTH) {
				getLogger().LOGe(TAG, "invalid session data length: " + BleUtils.bytesToString(decryptedData));
				return null;
			}
			return _getSessionData(decryptedData, 0);
		}
	}

	private static EncryptionSessionData _getSessionData(byte[] data, int offset) {
		byte[] sessionNonce = new byte[SESSION_NONCE_LENGTH];
		byte[] validationKey = new byte[VALIDATION_KEY_LENGTH];

		// Copy bytes 4-8 to sessionNonce, copy bytes 4-7 to validationKey
		System.arraycopy(data, offset, sessionNonce, 0, SESSION_NONCE_LENGTH);
		System.arraycopy(data, offset, validationKey, 0, VALIDATION_KEY_LENGTH);

		EncryptionSessionData sessionData = new EncryptionSessionData();
		sessionData.sessionNonce = sessionNonce;
		sessionData.validationKey = validationKey;

		return sessionData;
	}

	private static BleLog getLogger() {
		BleLog logger = BleLog.getInstance();
		// update the log level to the default of this class if it hasn't been set already
		if (logger.getLogLevel(TAG) == null) {
			logger.setLogLevelPerTag(TAG, LOG_LEVEL);
		}
		return logger;
	}
}
