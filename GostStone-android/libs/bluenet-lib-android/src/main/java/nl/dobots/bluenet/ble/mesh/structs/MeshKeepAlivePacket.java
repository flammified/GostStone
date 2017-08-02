package nl.dobots.bluenet.ble.mesh.structs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Locale;

import nl.dobots.bluenet.ble.cfg.BluenetConfig;
import nl.dobots.bluenet.utils.BleLog;
import nl.dobots.bluenet.utils.BleUtils;


/**
 * Copyright (c) 2015 Dominik Egger <dominik@dobots.nl>. All rights reserved.
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
 * Created on 15-7-15
 * Updated for new protocol on 27-1-17
 *
 * This class holds a mesh control message which can be written to the connected device. The payload
 * of the message will then be forwarded to all other nodes in the mesh network.
 *
 * The mesh message has the following fields:
 * 		* Handle: the handle on which the message should be sent, see MeshMessageTypes for details
 * 		* Length: the size in bytes of the payload
 * 		* Payload: the payload data of the mesh message, this layout depends on the type
 *
 * The format is:
 * 		--------------------------------------------------
 * 		| Handle | Reserved | Length (2B) | Payload (xB) |
 * 		--------------------------------------------------
 *
 * Note: Byte Order is Little-Endian, i.e. Least Significant Bit comes first
 *
 * @author Dominik Egger
 */
public class MeshKeepAlivePacket implements MeshPayload {

	private static final String TAG = MeshKeepAlivePacket.class.getCanonicalName();

	public class KeepAliveItem {

		private int _crownstoneId;

		private int _actionSwitchState;

		public KeepAliveItem(int crownstoneId, int actionSwitchState) {
			_crownstoneId = crownstoneId;
			_actionSwitchState = actionSwitchState;
		}

		public KeepAliveItem(ByteBuffer bb) {
			_crownstoneId = BleUtils.toUint16(bb.getShort());
			_actionSwitchState = BleUtils.toUint8(bb.get());
		}

		public int getCrownstoneId() {
			return _crownstoneId;
		}

		public void setCrownstoneId(int crownstoneId) {
			_crownstoneId = crownstoneId;
		}

		public int getActionSwitchState() {
			return _actionSwitchState;
		}

		public void setActionSwitchState(int actionSwitchState) {
			_actionSwitchState = actionSwitchState;
		}

		@Override
		public String toString() {
			return String.format(Locale.ENGLISH, "{id: %d, action: %d}", _crownstoneId, _actionSwitchState);
		}
	}

	// 2B timeout + 1B size + 2B reserved
	private static final int KEEP_ALIVE_PACKET_HEADER_SIZE = 5;
	// 2B Crownstone ID + 1B action + switch state
	private static final int KEEP_ALIVE_ITEM_SIZE = 3; // bytes
	// max capacity of the list
	private static final int MAX_LIST_ELEMENTS = (BluenetConfig.MESH_MAX_PAYLOAD_SIZE - KEEP_ALIVE_PACKET_HEADER_SIZE) / KEEP_ALIVE_ITEM_SIZE;

	// handle on which the message is sent in the mesh network
	private int _timeout;
	// number of elements in the list
	private int _size;
	// list of keep alive items
	private KeepAliveItem[] _list = new KeepAliveItem[MAX_LIST_ELEMENTS];

	/**
	 * Create an empty, invalid, ble stream message
	 */
	public MeshKeepAlivePacket() {
		_timeout = 0;
		_size = 0;
	}

	public MeshKeepAlivePacket(int timeout) {
		_timeout = timeout;
		_size = 0;
	}

//	/**
//	 * Parses the given byte array into a keep alive packet
//	 * @param bytes byte array containing the keep alive packet
//	 */
//	public MeshKeepAlivePacket(byte[] bytes) {
//	}

	/**
	 * Parses the given byte array into a keep alive packet
	 * @param bytes byte array containing the keep alive packet
	 * @return true when parsing was successful
	 */
	@Override
	public boolean fromArray(byte[] bytes) {
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		if (bytes.length < KEEP_ALIVE_PACKET_HEADER_SIZE) {
//			BleLog.getInstance().LOGe(TAG, "Invalid length: " + _size);
//			BleLog.getInstance().LOGe(TAG, "from mesh message: " + BleUtils.bytesToString(bytes));
			_timeout = 0;
			_size = 0;
			return false;
		}

		_timeout = BleUtils.toUint16(bb.getShort());
		_size = BleUtils.toUint8(bb.get());
		int reserved = BleUtils.toUint16(bb.getShort());
		if ((_size > MAX_LIST_ELEMENTS) ||
				(bytes.length < KEEP_ALIVE_PACKET_HEADER_SIZE + _size * KEEP_ALIVE_ITEM_SIZE)) {
//			BleLog.getInstance().LOGe(TAG, "Invalid length: " + _size);
//			BleLog.getInstance().LOGe(TAG, "from mesh message: " + BleUtils.bytesToString(bytes));
			_timeout = 0;
			_size = 0;
			return false;
		}

		for (int i = 0; i < _size; i++) {
			_list[i] = new KeepAliveItem(bb);
		}
		return true;
	}

	/**
	 * Convert the keep alive packet into a byte array to be set as payload in a mesh control message
	 * @return byte array representation of the keep alive packet
	 */
	@Override
	public byte[] toArray() {
		ByteBuffer bb = ByteBuffer.allocate(KEEP_ALIVE_PACKET_HEADER_SIZE + _size * KEEP_ALIVE_ITEM_SIZE);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		bb.putShort((short) _timeout);
		bb.put((byte)_size);
		bb.putShort((short) 0); // Reserved

		for (int i = 0; i < _size; i++) {
			bb.putShort((short) _list[i].getCrownstoneId());
			bb.put((byte) _list[i].getActionSwitchState());
		}

		return bb.array();
	}

	/**
	 * For debug purposes, create a string representation of the keep alive packet
	 * @return string representation of the object
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < _size; i++) {
			sb.append(_list[i].toString());
		}
		return String.format(Locale.ENGLISH, "{timeout: %d, size: %d, list: [%s]}", _timeout, _size, sb.toString());
	}

	public int getTimeout() {
		return _timeout;
	}

	public void setTimeout(int timeout) {
		_timeout = timeout;
	}

	/**
	 * Get the size
	 * @return size
	 */
	public int getSize() {
		return _size;
	}

	/**
	 * Set a new size
	 * @param size new size
	 */
	public void setSize(int size) {
		this._size = size;
	}

	public boolean addKeepAlive(int crownstoneId, int actionSwitchState) {
		if (_size < MAX_LIST_ELEMENTS) {
			_list[_size++] = new KeepAliveItem(crownstoneId, actionSwitchState);
			return true;
		} else {
			BleLog.getInstance().LOGe(TAG, "List is full");
			return false;
		}
	}

	public KeepAliveItem getKeepAliveItem(int index) {
		if (index < _size) {
			return _list[index];
		} else {
			BleLog.getInstance().LOGe(TAG, "index out of bounds");
			return null;
		}
	}

}
