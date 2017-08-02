package nl.dobots.bluenet.ble.mesh.structs.cmd;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.UUID;

import nl.dobots.bluenet.ble.cfg.BluenetConfig;
import nl.dobots.bluenet.utils.BleUtils;

/**
 * Copyright (c) 2017 Dominik Egger <dominik@dobots.nl>. All rights reserved.
 * <p>
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3, as
 * published by the Free Software Foundation.
 * <p>
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * <p>
 * Created on 27-1-17
 *
 * @author Dominik Egger <dominik@dobots.nl>
 */
public class MeshBeaconConfigPackage extends MeshCommandPacket {

	public class BeaconConfigPackage {

		// 2B major + 2B minor + 16B UUID + 1B tx power
		private static final int BEACON_CONFIG_MESSAGE_SIZE = 21;

		private int _major;
		private int _minor;
		private UUID _uuid;
		private int _txPower;

		public BeaconConfigPackage() {

		}

		public BeaconConfigPackage(int major, int minor, UUID uuid, int txPower) {
			_major = major;
			_minor = minor;
			_uuid = uuid;
			_txPower = txPower;
		}

		public boolean fromArray(byte[] bytes) {
			ByteBuffer bb = ByteBuffer.wrap(bytes);
			bb.order(ByteOrder.LITTLE_ENDIAN);

			if (bytes.length < BEACON_CONFIG_MESSAGE_SIZE) {
				return false;
			}
			_major = BleUtils.toUint16(bb.getShort());
			_minor = BleUtils.toUint16(bb.getShort());
			byte[] uuidBytes = new byte[16];
			bb.get(uuidBytes);
			_uuid = UUID.fromString(BleUtils.bytesToUuid(uuidBytes));
			_txPower = BleUtils.toUint8(bb.get());
			return true;
		}

		public byte[] toArray() {
			ByteBuffer bb = ByteBuffer.allocate(BEACON_CONFIG_MESSAGE_SIZE);
			bb.order(ByteOrder.LITTLE_ENDIAN);

			bb.putShort((short) _major);
			bb.putShort((short) _minor);
			bb.put(BleUtils.uuidToBytes(_uuid));
			bb.put((byte) _txPower);

			return bb.array();
		}

		public int getMajor() {
			return _major;
		}

		public void setMajor(int major) {
			_major = major;
		}

		public int getMinor() {
			return _minor;
		}

		public void setMinor(int minor) {
			_minor = minor;
		}

		public UUID getUuid() {
			return _uuid;
		}

		public void setUuid(UUID uuid) {
			_uuid = uuid;
		}

		public int getTxPower() {
			return _txPower;
		}

		public void setTxPower(int txPower) {
			_txPower = txPower;
		}

		@Override
		public String toString() {
			return String.format(Locale.ENGLISH, "{major: %d, minor: %d, uuid: %s, txPower: %d}",
					_major, _minor, _uuid.toString(), _txPower);
		}
	}

	private BeaconConfigPackage _package;

	public MeshBeaconConfigPackage() {
		super();
		_package = null;
	}

	public MeshBeaconConfigPackage(int major, int minor, UUID uuid, int txPower, int... ids) {
		super(BluenetConfig.MESH_CMD_BEACON, ids);
		_package = new BeaconConfigPackage(major, minor, uuid, txPower);
		setPayload(_package.toArray());
	}

	@Override
	public boolean fromArray(byte[] bytes) {
		if (!super.fromArray(bytes)) {
			return false;
		}
		_package = new BeaconConfigPackage();
		if (!_package.fromArray(getPayload())) {
			_package = null;
			return false;
		}
		return true;
	}

	@Override
	protected String payloadToString() {
		return _package.toString();
	}

}
