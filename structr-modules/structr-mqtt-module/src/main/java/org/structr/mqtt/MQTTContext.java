/**
 * Copyright (C) 2010-2017 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.mqtt;

import java.util.HashMap;
import java.util.Map;
import org.apache.cxf.common.util.StringUtils;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.service.InitializationCallback;
import org.structr.common.error.FrameworkException;
import org.structr.core.Services;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.graph.Tx;
import org.structr.core.property.PropertyMap;
import org.structr.mqtt.entity.MQTTClient;

public abstract class MQTTContext {

	private static final Logger									logger			= LoggerFactory.getLogger(MQTTContext.class.getName());
	private static final Map<String, MQTTClientConnection>		connections		= new HashMap<>();

	static {

		Services.getInstance().registerInitializationCallback(new InitializationCallback() {

			@Override
			public void initializationDone() {

				final App app = StructrApp.getInstance();

				try (final Tx tx = app.tx()) {

					for (final MQTTClient client : app.nodeQuery(MQTTClient.class).getAsList()) {

						client.setProperties(client.getSecurityContext(), new PropertyMap(MQTTClient.isConnected, false));

						// enable clients on startup
						if (client.getProperty(MQTTClient.isEnabled)) {

							MQTTContext.connect(client);
							MQTTContext.subscribeAllTopics(client);
						}
					}

					tx.success();

				} catch (Throwable t) {
					logger.warn("Could not connect to MQTT broker.");
				}
			}
		});
	}

	public static MQTTClientConnection getClientForId(String id){
		return connections.get(id);
	}

	public static void connect(MQTTInfo info) throws FrameworkException {

		try {

			MQTTClientConnection con = getClientForId(info.getUuid());

			if(con == null){

				con = new MQTTClientConnection(info);
				connections.put(info.getUuid(), con);
				con.connect();
			} else {

				if(!con.isConnected()){

					con.connect();
				}
			}

		} catch (MqttException ex) {

			throw new FrameworkException(422, "Error while connecting to MQTT broker.");
		}

	}

	public static void subscribeAllTopics(MQTTInfo info) throws FrameworkException {

		MQTTClientConnection con = getClientForId(info.getUuid());

		for(String topic : info.getTopics()) {
			if(!StringUtils.isEmpty(topic)){

				con.subscribeTopic(topic);
			}
		}

	}
}


