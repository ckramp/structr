/**
 * Copyright (C) 2010-2018 Structr GmbH
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
package org.structr.flow.impl;

import org.structr.common.PropertyView;
import org.structr.common.View;
import org.structr.core.property.EndNodes;
import org.structr.core.property.Property;
import org.structr.core.property.StartNodes;
import org.structr.flow.api.DataSource;
import org.structr.flow.api.Exception;
import org.structr.flow.api.FlowElement;
import org.structr.flow.api.FlowType;
import org.structr.flow.engine.Context;
import org.structr.flow.engine.FlowError;
import org.structr.flow.engine.FlowException;
import org.structr.flow.impl.rels.FlowDataInput;
import org.structr.flow.impl.rels.FlowExceptionHandlerNodes;

import java.util.List;

public class FlowExceptionHandler extends FlowNode implements Exception, DataSource {

	public static final Property<List<FlowBaseNode>> handledNodes 			= new StartNodes<>("handledNodes", FlowExceptionHandlerNodes.class);
	public static final Property<List<FlowBaseNode>> dataTarget 			= new EndNodes<>("dataTarget", FlowDataInput.class);

	public static final View defaultView 									= new View(FlowNode.class, PropertyView.Public,  next, handledNodes, dataTarget);
	public static final View uiView      									= new View(FlowNode.class, PropertyView.Ui,      next, handledNodes, dataTarget);


	@Override
	public void handleException(Context context) {
		FlowNode next = getProperty(FlowExceptionHandler.next);

		Object data = context.getData(getUuid());

		FlowException exception = data != null ? (FlowException)data : null;

		if (next == null && exception != null) {
			context.error(new FlowError(exception.getMessage()));
			exception.printStackTrace();
		}
	}

	@Override
	public Object get(Context context) throws FlowException {

		Object data = context.getData(getUuid());

		if (data instanceof FlowException) {
			return ((FlowException)data).getMessage();
		}

		return null;
	}

	@Override
	public FlowType getFlowType() {
		return FlowType.Exception;
	}

	@Override
	public FlowContainer getFlowContainer() {
		return this.getProperty(flowContainer);
	}

	@Override
	public FlowElement next() {
		return getProperty(FlowExceptionHandler.next);
	}

}