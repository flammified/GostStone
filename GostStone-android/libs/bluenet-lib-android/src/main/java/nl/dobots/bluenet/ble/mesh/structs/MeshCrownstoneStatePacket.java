package nl.dobots.bluenet.ble.mesh.structs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

import nl.dobots.bluenet.ble.cfg.BluenetConfig;
import nl.dobots.bluenet.utils.BleLog;
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
public class MeshCrownstoneStatePacket implements MeshPayload {

	private static final String TAG = MeshCrownstoneStatePacket.class.getCanonicalName();

	public class CrownstoneStateItem {
		private int _crownstoneId;
		private int _switchState;
		private int _eventBitmask;
		private int _powerUsage;
		private int _accumulatedEnergy;

		public CrownstoneStateItem(int crownstoneId, int switchState, int eventBitmask, int powerUsage, int accumulatedEnergy) {
			_crownstoneId = crownstoneId;
			_switchState = switchState;
			_eventBitmask = eventBitmask;
			_powerUsage = powerUsage;
			_accumulatedEnergy = accumulatedEnergy;
		}

		public CrownstoneStateItem(ByteBuffer bb) {
			_crownstoneId =      BleUtils.toUint16(bb.getShort());
			_switchState =       BleUtils.toUint8(bb.get());
			_eventBitmask =      BleUtils.toUint8(bb.get());
			_powerUsage =        bb.getInt();
			_accumulatedEnergy = bb.getInt();
		}

		public void toArray(ByteBuffer bb) {
			bb.putShort((short) _crownstoneId);
			bb.put((byte) _switchState);
			bb.put((byte) _eventBitmask);
			bb.putInt(_powerUsage);
			bb.putInt(_accumulatedEnergy);
		}

		public int getCrownstoneId() {
			return _crownstoneId;
		}

		public void setCrownstoneId(int crownstoneId) {
			_crownstoneId = crownstoneId;
		}

		public int getEventBitmask() {
			return _eventBitmask;
		}

		public void setEventBitmask(int eventBitmask) {
			_eventBitmask = eventBitmask;
		}

		public int getSwitchState() {
			return _switchState;
		}

		public void setSwitchState(int switchState) {
			_switchState = switchState;
		}

		public int getPowerUsage() {
			return _powerUsage;
		}

		public void setPowerUsage(int powerUsage) {
			_powerUsage = powerUsage;
		}

		public int getAccumulatedEnergy() {
			return _accumulatedEnergy;
		}

		public void setAccumulatedEnergy(int accumulatedEnergy) {
			_accumulatedEnergy = accumulatedEnergy;
		}

		@Override
		public String toString() {
			return String.format(Locale.ENGLISH, "{id: %d, switchState: %d, bitmask: %d, powerUsage: %d, accumulatedEnergy: %d}",
					_crownstoneId, _switchState, _eventBitmask, _powerUsage, _accumulatedEnergy);
		}
	}

	// 1B head + 1B tail + 1B size + 5B reserved
	private static final int CROWNSTONE_STATE_PACKET_HEADER_SIZE = 8;
	// 2B Crownstone ID + 1B switch state + 1B event bitmask + 4B power usage + 4B accumulated energy
	private static final int CROWNSTONE_STATE_ITEM_SIZE = 12; // bytes
	// max capacity of the list
	private static final int MAX_LIST_ELEMENTS = (BluenetConfig.MESH_MAX_PAYLOAD_SIZE - CROWNSTONE_STATE_PACKET_HEADER_SIZE) / CROWNSTONE_STATE_ITEM_SIZE;

	private int _head;

	private int _tail;

	private int _size;

	private CrownstoneStateItem[] _list = new CrownstoneStateItem[MAX_LIST_ELEMENTS];

	public MeshCrownstoneStatePacket() {
		_head = 0;
		_tail = 0;
		_size = 0;
	}

//	public MeshCrownstoneStatePacket(byte[] bytes) {
//	}

	@Override
	public boolean fromArray(byte[] bytes) {
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		if (bytes.length < CROWNSTONE_STATE_PACKET_HEADER_SIZE + CROWNSTONE_STATE_ITEM_SIZE*CROWNSTONE_STATE_ITEM_SIZE) {
			return false;
		}
		_head = BleUtils.toUint8(bb.get());
		_tail = BleUtils.toUint8(bb.get());
		_size = BleUtils.toUint8(bb.get());
		byte[] reserved = new byte[5];
		bb.get(reserved);
		if (_head > MAX_LIST_ELEMENTS ||
				_tail > MAX_LIST_ELEMENTS ||
				_size > MAX_LIST_ELEMENTS ||
				(_tail == _head && (_size != 0 && _size != MAX_LIST_ELEMENTS)) ||
				(_tail != _head && ((_tail + MAX_LIST_ELEMENTS - _head) % MAX_LIST_ELEMENTS != _size))
				) {
//			BleLog.getInstance().LOGe(TAG, "Invalid message: " + BleUtils.bytesToString(bytes));
			_size = 0;
			_head = 0;
			_tail = 0;
			return false;
		}

		for (int i = 0; i < MAX_LIST_ELEMENTS; i++) {
			_list[i] = new CrownstoneStateItem(bb);
		}
		return true;
	}

	@Override
	public byte[] toArray() {
		ByteBuffer bb = ByteBuffer.allocate(BluenetConfig.MESH_MAX_PAYLOAD_SIZE);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		bb.put((byte)_head);
		bb.put((byte)_tail);
		bb.put((byte)_size);
		byte[] reserved = new byte[5];
		bb.put(reserved);

		for (int i = 0; i < _size; i++) {
			_list[i].toArray(bb);
		}
		return bb.array();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < _size; i++) {
			sb.append(getCrownstoneState(i).toString());
		}
		return String.format(Locale.ENGLISH, "{head: %d, tail: %d, size: %d, list: [%s]", _head, _tail, _size, sb.toString());
	}

	private void incTail() {
		_tail = (_tail + 1) % MAX_LIST_ELEMENTS;
		if (++_size > MAX_LIST_ELEMENTS) {
			incHead();
		}
	}

	private void incHead() {
		_head = (_head + 1) % MAX_LIST_ELEMENTS;
		--_size;
	}

	public void addCrownstoneState(int crownstoneId, int switchState, int eventBitmask, int powerUsage, int accumulatedEnergy) {
		_list[_tail] = new CrownstoneStateItem(crownstoneId, switchState, eventBitmask, powerUsage, accumulatedEnergy);
		incTail();
	}

	public CrownstoneStateItem getCrownstoneState(int index) {
		if (index < _size) {
			return _list[(_head + index) % MAX_LIST_ELEMENTS];
		} else {
			BleLog.getInstance().LOGe(TAG, "index out of bounds");
			return null;
		}
	}

	public CrownstoneStateItem popCrownstoneState() {
		CrownstoneStateItem item = _list[_head];
		incHead();
		return item;
	}
}
