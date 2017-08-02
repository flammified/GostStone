package nl.dobots.bluenet.ble.mesh.structs.reply;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
public class MeshStatusReplyPacket extends MeshCommandReplyPacket {

	public class StatusReplyItem {

		private int _crownstoneId;

		private int _status;

		public StatusReplyItem(int crownstoneId, int status) {
			_crownstoneId = crownstoneId;
			_status = status;
		}

		public int getCrownstoneId() {
			return _crownstoneId;
		}

		public void setCrownstoneId(int crownstoneId) {
			_crownstoneId = crownstoneId;
		}

		public int getStatus() {
			return _status;
		}

		public void setStatus(int status) {
			_status = status;
		}

		public byte[] toArray() {
			ByteBuffer bb = ByteBuffer.allocate(_numberOfReplies * STATUS_REPLY_ITEM_SIZE);
			bb.order(ByteOrder.LITTLE_ENDIAN);

			bb.putShort((short) _crownstoneId);
			bb.putShort((short) _status);

			return bb.array();
		}
	}

	// 2B Crownstone ID + 2B Status
	private static final int STATUS_REPLY_ITEM_SIZE = 4;
	private static final int MAX_STATUS_REPLY_ITEMS = (BluenetConfig.MESH_MAX_PAYLOAD_SIZE - COMMAND_REPLY_PACKET_HEADER_SIZE) / STATUS_REPLY_ITEM_SIZE;

	private StatusReplyItem[] _list = new StatusReplyItem[MAX_STATUS_REPLY_ITEMS];

	public MeshStatusReplyPacket() {
		super();
	}

	public MeshStatusReplyPacket(long messageCounter) {
		super(BluenetConfig.MESH_REPLY_STATUS, messageCounter);
	}

//	public MeshStatusReplyPacket(byte[] bytes) {
//	}

	@Override
	public boolean fromArray(byte[] bytes) {
		if (!super.fromArray(bytes)) {
			return false;
		}
		byte[] payload = getPayload();
		if (_numberOfReplies > MAX_STATUS_REPLY_ITEMS || payload.length < _numberOfReplies * STATUS_REPLY_ITEM_SIZE) {
			return false;
		}
		ByteBuffer bb = ByteBuffer.wrap(getPayload());
		bb.order(ByteOrder.LITTLE_ENDIAN);
		for (int i = 0; i < _numberOfReplies; i++) {
			int crownstoneId = BleUtils.toUint16(bb.getShort());
			int status = BleUtils.toUint16(bb.getShort());
			_list[i] = new StatusReplyItem(crownstoneId, status);
		}
		return true;
	}

	@Override
	public byte[] toArray() {
		ByteBuffer bb = ByteBuffer.allocate(_numberOfReplies * STATUS_REPLY_ITEM_SIZE);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		for (int i = 0; i < _numberOfReplies; i++) {
			bb.put(_list[i].toArray());
		}
		_payload = bb.array();

		return super.toArray();
	}

	public boolean addReplyItem(int crownstoneId, int status) {
		if (_numberOfReplies + 1 < MAX_STATUS_REPLY_ITEMS) {
			_list[_numberOfReplies++] = new StatusReplyItem(crownstoneId, status);
			return true;
		} else {
			BleLog.getInstance().LOGe(TAG, "List is full");
			return false;
		}
	}

	public StatusReplyItem getReplyItem(int index) {
		if (index < _numberOfReplies) {
			return _list[index];
		} else {
			BleLog.getInstance().LOGe(TAG, "index out of bounds");
			return null;
		}
	}

}
