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
package org.structr.mqtt.entity;

import java.util.HashMap;
import java.util.Map;
import org.apache.cxf.common.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.common.PropertyView;
import org.structr.common.SecurityContext;
import org.structr.common.View;
import org.structr.common.error.ErrorBuffer;
import org.structr.common.error.FrameworkException;
import static org.structr.core.GraphObject.createdBy;
import static org.structr.core.GraphObject.createdDate;
import static org.structr.core.GraphObject.id;
import static org.structr.core.GraphObject.lastModifiedDate;
import static org.structr.core.GraphObject.type;
import static org.structr.core.GraphObject.visibilityEndDate;
import static org.structr.core.GraphObject.visibilityStartDate;
import static org.structr.core.GraphObject.visibleToAuthenticatedUsers;
import static org.structr.core.GraphObject.visibleToPublicUsers;
import org.structr.core.entity.AbstractNode;
import org.structr.core.graph.ModificationQueue;
import static org.structr.core.graph.NodeInterface.deleted;
import static org.structr.core.graph.NodeInterface.hidden;
import static org.structr.core.graph.NodeInterface.name;
import static org.structr.core.graph.NodeInterface.owner;
import org.structr.core.property.Property;
import org.structr.core.property.StartNode;
import org.structr.core.property.StringProperty;
import org.structr.mqtt.entity.relation.MQTTSubscribers;
import org.structr.schema.SchemaService;

public class MQTTSubscriber extends AbstractNode {

	private static final Logger logger = LoggerFactory.getLogger(MQTTSubscriber.class.getName());

	public static final Property<MQTTClient>		client			= new StartNode<>("client", MQTTSubscribers.class);
	public static final Property<String>			topic			= new StringProperty("topic");

	public static final View defaultView = new View(MQTTClient.class, PropertyView.Public, id, type, client, topic);

	public static final View uiView = new View(MQTTClient.class, PropertyView.Ui,
		id, name, owner, type, createdBy, deleted, hidden, createdDate, lastModifiedDate, visibleToPublicUsers, visibleToAuthenticatedUsers, visibilityStartDate, visibilityEndDate,
        client, topic
	);

	static {

		SchemaService.registerBuiltinTypeOverride("MQTTSubscriber", MQTTSubscriber.class.getName());
	}

	@Override
	public boolean onCreation(final SecurityContext securityContext, final ErrorBuffer errorBuffer) throws FrameworkException {

		if(!StringUtils.isEmpty(getProperty(topic)) && (getProperty(client) != null) && getProperty(client).getProperty(MQTTClient.isConnected)) {
			Map<String,Object> params = new HashMap<>();
			params.put("topic", getProperty(topic));
			getProperty(client).invokeMethod("subscribeTopic", params, false);
		}

		return super.onCreation(securityContext, errorBuffer);
	}

	@Override
	public boolean onModification(final SecurityContext securityContext, final ErrorBuffer errorBuffer, final ModificationQueue modificationQueue) throws FrameworkException {

		if(!StringUtils.isEmpty(getProperty(topic)) && (getProperty(client) != null) && getProperty(client).getProperty(MQTTClient.isConnected)) {

			if(modificationQueue.isPropertyModified(topic)){

				Map<String,Object> params = new HashMap<>();
				params.put("topic", getProperty(topic));
				getProperty(client).invokeMethod("subscribeTopic", params, false);
			}
		}


		return super.onModification(securityContext, errorBuffer, modificationQueue);
	}

}
