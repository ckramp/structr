/*
 * Copyright (C) 2010-2021 Structr GmbH
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
package org.structr.core.property;

import java.lang.reflect.ParameterizedType;
import org.structr.api.search.SortType;
import org.structr.common.SecurityContext;
import org.structr.core.GraphObject;
import org.structr.core.converter.PropertyConverter;

/**
 * A generic dummy property that does nothing.
 */
public class GenericProperty<T> extends AbstractPrimitiveProperty<T> {

	public GenericProperty(String name) {

		this(name, name);

	}

	public GenericProperty(String jsonName, String dbName) {

		super(jsonName, dbName);

	}

	@Override
	public String typeName() {
		return valueType().getSimpleName();
	}

	@Override
	public Class valueType() {

		ParameterizedType pType = (ParameterizedType) getClass().getGenericSuperclass();

		if ("T".equals(pType.getRawType().toString())) {

			Class<? extends GraphObject> relType = relatedType();

			return relType != null ? relType : null;

		}

		return pType.getRawType().getClass();
	}

	@Override
	public Object fixDatabaseProperty(Object value) {

		return null;

	}

	@Override
	public PropertyConverter<T, ?> databaseConverter(SecurityContext securityContext) {
		return null;
	}

	@Override
	public PropertyConverter<T, ?> databaseConverter(SecurityContext securityContext, GraphObject entity) {
		return null;
	}

	@Override
	public PropertyConverter<?, T> inputConverter(SecurityContext securityContext) {
		return null;
	}

	@Override
	public Class<? extends GraphObject> relatedType() {
		return null;
	}

	@Override
	public boolean isCollection() {
		return false;
	}

	@Override
	public SortType getSortType() {
		return SortType.Default;
	}

	// ----- OpenAPI -----
	@Override
	public Object getExampleValue(final String type, final String viewName) {
		return null;
	}
}
