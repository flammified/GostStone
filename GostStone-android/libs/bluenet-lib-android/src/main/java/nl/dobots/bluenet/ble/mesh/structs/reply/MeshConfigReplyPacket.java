package nl.dobots.bluenet.ble.mesh.structs.reply;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

import nl.dobots.bluenet.ble.base.structs.ConfigurationMsg;
import nl.dobots.bluenet.ble.cfg.BluenetConfig;
import nl.dobots.bluenet.utils.BleLog;

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
public class MeshConfigReplyPacket extends MeshCommandReplyPacket {

	// TODO: this only holds 1 config reply packet at the moment

	private ConfigurationMsg _configMsg;

	public MeshConfigReplyPacket() {
		super();
		_configMsg = null;
	}

	public MeshConfigReplyPacket(ConfigurationMsg message, long messageNumber) {
		super(BluenetConfig.MESH_REPLY_CONFIG, messageNumber);
		_configMsg = message;
		setNumberOfReplies(1);
		setPayload(_configMsg.toArray());
	}

//	public MeshConfigReplyPacket(byte[] bytes) {
//	}

	@Override
	public boolean fromArray(byte[] bytes) {
		if (!super.fromArray(bytes)) {
			return false;
		}
		_configMsg = new ConfigurationMsg();
		if (!_configMsg.fromArray(getPayload())) {
			_configMsg = null;
			return false;
		}
		return true;
	}

	public ConfigurationMsg getReplyItem() {
		return _configMsg;
	}

/*
	private ArrayList<ConfigurationMsg> _list = new ArrayList<>();
	private int _remainingPayloadBytes = BluenetConfig.MESH_MAX_PAYLOAD_SIZE - COMMAND_REPLY_PACKET_HEADER_SIZE;

	public MeshConfigReplyPacket(long messageNumber) {
		super(BluenetConfig.MESH_REPLY_CONFIG, messageNumber);
		_list = new ArrayList<>();
	}

	public MeshConfigReplyPacket(byte[] bytes) {
		super(bytes);
		_list = new ArrayList<>(_numberOfReplies);

		ByteBuffer bb = ByteBuffer.wrap(getPayload());
		int bytesHandled = 0;

		for (int i = 0; i < _numberOfReplies; i++) {
			byte[] buffer = new byte[bb.remaining()];

			bb.get(buffer);
			ConfigurationMsg msg = new ConfigurationMsg(buffer);
			_list.add(msg);

			bytesHandled += 4 + msg.getLength();

			bb.position(bytesHandled);
		}
		_remainingPayloadBytes -= bytesHandled;
	}

	@Override
	public byte[] toArray() {
		ByteBuffer bb = ByteBuffer.allocate(BluenetConfig.MESH_MAX_PAYLOAD_SIZE);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		for (ConfigurationMsg configurationMsg : _list) {
			bb.put(configurationMsg.toArray());
		}
		_payload = bb.array();
		_numberOfReplies = _list.size();

		return super.toArray();
	}

	public boolean addConfigReplyItem(ConfigurationMsg msg) {
		int messageLength = msg.getLength() + 4;
		if (messageLength < _remainingPayloadBytes) {
			_list.add(msg);
			_remainingPayloadBytes -= messageLength;
			return true;
		} else {
			BleLog.getInstance().LOGe(TAG, "List is full");
			return false;
		}
	}

	public ConfigurationMsg getReplyItem(int index) {
		if (index < _list.size()) {
			return _list.get(index);
		} else {
			BleLog.getInstance().LOGe(TAG, "index out of bounds");
			return null;
		}
	}
*/

}
