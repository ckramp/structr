/**
 * Copyright (C) 2010-2017 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * Structr is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * Structr. If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.mqtt;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class MQTTContext {

	private static final Logger									logger			= LoggerFactory.getLogger(MQTTContext.class.getName());
	private static final Map<String, MQTTClientConnection>		connections		= new HashMap<>();

	public static MQTTClientConnection getClientForId(String id){
		return connections.get(id);
	}

	public static void connect(MQTTInfo info){

		MQTTClientConnection con = getClientForId(info.getUuid());

		if(con == null){

			con = new MQTTClientConnection();
			connections.put(info.getUuid(), con);
			con.connect();
		} else {

			if(!con.isConnected()){

				con.connect();
			}
		}

	}

}
