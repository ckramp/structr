/**
 * Copyright (C) 2010-2016 Structr GmbH
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
package org.structr.rest.resource;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObjectMap;
import org.structr.core.Result;
import org.structr.core.Services;
import org.structr.core.app.StructrApp;
import org.structr.core.property.GenericProperty;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.StringProperty;
import org.structr.module.StructrModule;
import org.structr.rest.RestMethodResult;
import org.structr.rest.exception.IllegalMethodException;
import org.structr.rest.exception.IllegalPathException;

/**
 *
 *
 */
public class EnvResource extends Resource {

	public enum UriPart {
		_env
	}

	@Override
	public boolean checkAndConfigure(String part, SecurityContext securityContext, HttpServletRequest request) throws FrameworkException {

		this.securityContext = securityContext;

		if (UriPart._env.name().equals(part)) {

			return true;
		}

		return false;
	}

	@Override
	public Result doGet(PropertyKey sortKey, boolean sortDescending, int pageSize, int page) throws FrameworkException {

		final Map<String, Map<String, Object>> modules    = new HashMap<>();
		final Map<String, Map<String, String>> components = new HashMap<>();
		final List<GraphObjectMap> resultList             = new LinkedList<>();
		final GraphObjectMap info                         = new GraphObjectMap();
		final String classPath                            = System.getProperty("java.class.path");
		final Pattern outerPattern                        = Pattern.compile("(structr-.+?(?=.jar))");
		final Matcher outerMatcher                        = outerPattern.matcher(classPath);

		while (outerMatcher.find()) {

			final String group               = outerMatcher.group();
			final Pattern innerPattern       = Pattern.compile("(structr-core|structr-rest|structr-ui)-([^-]*(?:-SNAPSHOT){0,1})-{0,1}(?:([0-9]{0,12})\\.{0,1}([0-9a-f]{0,5})).*");
			final Matcher innerMatcher       = innerPattern.matcher(group);
			final Map<String, String> module = new HashMap<>();

			if (innerMatcher.matches()) {

				module.put("version", innerMatcher.group(2));
				module.put("date", innerMatcher.group(3));
				module.put("build", innerMatcher.group(4));

				components.put(innerMatcher.group(1), module);
			}
		}

		// collect StructrModules
		for (final StructrModule module : StructrApp.getConfiguration().getModules().values())  {

			final Map<String, Object> map = new LinkedHashMap<>();

			map.put("source", module.getClass().getProtectionDomain().getCodeSource().getLocation().getPath());

			if (module.getDependencies() != null) {
				map.put("dependencies", module.getDependencies());
			}

			if (module.getFeatures() != null) {
				map.put("features", module.getFeatures());
			}

			modules.put(module.getName(), map);
		}

		info.setProperty(new GenericProperty("modules"), modules);
		info.setProperty(new GenericProperty("components"), components);
		info.setProperty(new StringProperty("classPath"), classPath);
		info.setProperty(new StringProperty("instanceName"), StructrApp.getConfigurationValue(Services.APPLICATION_INSTANCE_NAME, ""));
		info.setProperty(new StringProperty("instanceStage"), StructrApp.getConfigurationValue(Services.APPLICATION_INSTANCE_STAGE, ""));

		resultList.add(info);

		return new Result(resultList, resultList.size(), false, false);
	}

	@Override
	public RestMethodResult doPost(Map<String, Object> propertySet) throws FrameworkException {
		throw new IllegalMethodException("POST not allowed on " + getResourceSignature());
	}

	@Override
	public Resource tryCombineWith(Resource next) throws FrameworkException {
		throw new IllegalPathException(getResourceSignature() + " has no subresources");
	}

	@Override
	public String getUriPart() {
		return getResourceSignature();
	}

	@Override
	public Class getEntityClass() {
		return null;
	}

	@Override
	public String getResourceSignature() {
		return UriPart._env.name();
	}

	@Override
	public boolean isCollectionResource() throws FrameworkException {
		return false;
	}
}
