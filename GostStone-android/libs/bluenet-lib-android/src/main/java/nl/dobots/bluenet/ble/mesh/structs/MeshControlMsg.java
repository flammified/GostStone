package nl.dobots.bluenet.ble.mesh.structs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Locale;

import nl.dobots.bluenet.ble.cfg.BluenetConfig;
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
 * This class holds a mesh control message which can be written to the connected device. The _payload
 * of the message will then be forwarded to all other nodes in the mesh network.
 *
 * The mesh message has the following fields:
 * 		* Handle: the handle on which the message should be sent, see MeshMessageTypes for details
 * 		* Length: the length in bytes of the _payload
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
public class MeshControlMsg {

	// size of message without _payload is:
	// 1B _handle + 1B RESERVED + 2B _length
	private static final int MESH_CONTROL_MSG_HEADER_SIZE = 4;

	// _handle on which the message is sent in the mesh network
	private int _handle;
	// _length of data, includes target, type and _payload
	private int _length;
	// _payload of the mesh message, i.e. the data to be sent into the mesh
	private byte[] _payload;

	/**
	 * Create an empty, invalid, mesh message
	 */
	public MeshControlMsg() {
		_handle = -1;
		_length = 0;
		_payload = null;
	}

	/**
	 * Create a mesh message from the parameters to be written to devices mesh characteristic
	 * @param handle handle on which the message is sent in the mesh network
	 * @param payload payload of the mesh message, i.e. the data to be sent into the mesh
	 */
	public MeshControlMsg(int handle, MeshPayload payload) {
		this._handle = handle;
		setPayload(payload);
	}

	/**
	 * Create a mesh message from the parameters to be written to devices mesh characteristic
	 * @param handle handle on which the message is sent in the mesh network
	 * @param length length of data, includes target, type and _payload
	 * @param payload payload of the mesh message, i.e. the data to be sent into the mesh
	 */
	@Deprecated
	public MeshControlMsg(int handle, int length, byte[] payload) {
		this._handle = handle;
		this._length = length;
		this._payload = payload;
	}
	
//	public MeshControlMsg(int _handle, int _length, MeshPayload _payload) {
//
//	}

//	/**
//	 * Parses the given byte array into a mesh message
//	 * @param bytes byte array containing the mesh message
//	 */
//	public MeshControlMsg(byte[] bytes) {
//	}

	/**
	 * Parses the given byte array into a mesh message
	 * @param bytes byte array containing the mesh message
	 * @return true when parsing was successful
	 */
	public boolean fromArray(byte[] bytes) {
		if (bytes.length < MESH_CONTROL_MSG_HEADER_SIZE) {
			return false;
		}
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		_handle = bb.get();
		bb.get(); // skip reserved field
		_length = bb.getShort();
		if (bytes.length < MESH_CONTROL_MSG_HEADER_SIZE + _length) {
			return false;
		}
		_payload = new byte[bb.remaining()];
		bb.get(_payload);
		return true;
	}

	/**
	 * Convert the mesh message into a byte array to be written to the mesh characteristic
	 * @return byte array representation of the mesh message
	 */
	public byte[] toArray() {
		ByteBuffer bb = ByteBuffer.allocate(MESH_CONTROL_MSG_HEADER_SIZE + _payload.length);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		bb.put((byte) _handle);
		bb.put((byte) BluenetConfig.RESERVED);
		bb.putShort((short) _length);
		bb.put(_payload);

		return bb.array();
	}

	/**
	 * For debug purposes, create a string representation of the mesh message
	 * @return string representation of the object
	 */
	@Override
	public String toString() {
		return String.format(Locale.ENGLISH, "{_handle: %d, _length: %d, _payload: %s}",
				_handle, _length, BleUtils.bytesToString(_payload));
	}

	/**
	 * Get the handle
	 * @return handle
	 */
	public int getHandle() {
		return _handle;
	}

	/**
	 * Set a new handle
	 * @param handle new handle
	 */
	public void setHandle(int handle) {
		this._handle = handle;
	}

	/**
	 * Get the payload size
	 * @return size of the payload in bytes
	 */
	public int getPayloadSize() {
		return _length;
	}

//	/**
//	 * Set a new payload size
//	 * @param new size in bytes
//	 */
//	public void setPayloadSize(int length) {
//		this._length = length;
//	}

	/**
	 * Get the mesh message's payload
	 * @return payload
	 */
	public byte[] getPayload() {
		return _payload;
	}

	/**
	 * Set a new message payload
	 * @param payload new payload
	 */
	@Deprecated
	public void setPayload(byte[] payload) {
		this._payload = payload;
	}

	/**
	 * Set a new message payload
	 * @param payload new payload
	 */
	public boolean setPayload(MeshPayload payload) {
		if (payload == null) {
			_payload = null;
			_length = 0;
			return false;
		}
		_payload = payload.toArray();
		if (_payload == null) {
			_length = 0;
			return false;
		}
		_length = _payload.length;
		return true;
	}

}
