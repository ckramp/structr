/**
 * Copyright (C) 2010-2019 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.core.function;

import java.util.Map;
import org.structr.api.service.LicenseManager;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObjectMap;
import org.structr.core.app.StructrApp;
import org.structr.core.converter.PropertyConverter;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.PropertyMap;
import org.structr.schema.ConfigurationProvider;
import org.structr.schema.action.ActionContext;
import org.structr.schema.action.Function;

public class CreateFunction extends Function<Object, Object> {

	public static final String ERROR_MESSAGE_CREATE    = "Usage: ${create(type, key, value)}. Example: ${create(\"Feedback\", \"text\", this.text)}";
	public static final String ERROR_MESSAGE_CREATE_JS = "Usage: ${{Structr.create(type, {key: value})}}. Example: ${{Structr.create(\"Feedback\", {text: \"Structr is awesome.\"})}}";

	@Override
	public String getName() {
		return "create";
	}

	@Override
	public int getRequiredLicense() {
		return LicenseManager.Community;
	}

	@Override
	public Object apply(final ActionContext ctx, final Object caller, final Object[] sources) throws FrameworkException {

		if (sources != null && sources.length > 0) {

			final SecurityContext securityContext = ctx.getSecurityContext();
			final ConfigurationProvider config = StructrApp.getConfiguration();
			PropertyMap propertyMap;
			Class type = null;

			if (sources.length >= 1 && sources[0] != null) {

				type = config.getNodeEntityClass(sources[0].toString());
			}

			if (type == null) {

				throw new FrameworkException(422, "Unknown type '" + sources[0].toString() + "' in create() method!");
			}

			// extension for native javascript objects
			if (sources.length == 2 && sources[1] instanceof Map) {

				propertyMap = PropertyMap.inputTypeToJavaType(securityContext, type, (Map)sources[1]);

			} else if (sources.length == 2 && sources[1] instanceof GraphObjectMap) {

				propertyMap = PropertyMap.inputTypeToJavaType(securityContext, type, ((GraphObjectMap)sources[1]).toMap());

			} else {

				propertyMap               = new PropertyMap();
				final int parameter_count = sources.length;

				if (parameter_count % 2 == 0) {

					throw new FrameworkException(400, "Invalid number of parameters: " + parameter_count + ". Should be uneven: " + (ctx.isJavaScriptContext() ? ERROR_MESSAGE_CREATE_JS : ERROR_MESSAGE_CREATE));
				}

				for (int c = 1; c < parameter_count; c += 2) {

					final PropertyKey key = StructrApp.key(type, sources[c].toString());

					if (key != null) {

						final PropertyConverter inputConverter = key.inputConverter(securityContext);
						Object value = sources[c + 1];

						if (inputConverter != null) {

							value = inputConverter.convert(value);
						}

						propertyMap.put(key, value);
					}

				}
			}

			return StructrApp.getInstance(securityContext).create(type, propertyMap);

		} else {

			logParameterError(caller, sources, ctx.isJavaScriptContext());

			return usage(ctx.isJavaScriptContext());
		}
	}

	@Override
	public String usage(boolean inJavaScriptContext) {
		return (inJavaScriptContext ? ERROR_MESSAGE_CREATE_JS : ERROR_MESSAGE_CREATE);
	}

	@Override
	public String shortDescription() {
		return "Creates a new entity with the given key/value pairs in the database";
	}

}
