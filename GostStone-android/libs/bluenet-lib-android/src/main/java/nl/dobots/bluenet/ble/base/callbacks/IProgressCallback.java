package nl.dobots.bluenet.ble.base.callbacks;

import android.support.annotation.Nullable;

import org.json.JSONObject;

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
 * Created on 26-10-16
 *
 * Returns a double indicating the progress (0.0 to 1.0)
 * Optionally returns a json object with current status, which _can_ contain the following data:
 *      - finished: an array of strings of finished tasks
 *      - active:   an array of strings of tasks currently active
 *      - pending:  an array of strings of pending tasks
 *
 * @author Bart van Vliet
 */
public interface IProgressCallback extends IBaseCallback {

	void onProgress(double progress, @Nullable JSONObject statusJson);

}
