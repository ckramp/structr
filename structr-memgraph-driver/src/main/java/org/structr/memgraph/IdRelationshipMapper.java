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
package org.structr.memgraph;

import java.util.function.Function;
import org.structr.api.graph.Relationship;

/**
 *
 */
class IdRelationshipMapper implements Function<Long, Relationship> {

	private MemgraphDatabaseService db = null;

	public IdRelationshipMapper(final MemgraphDatabaseService db) {
		this.db = db;
	}

	@Override
	public Relationship apply(final Long id) {
		return RelationshipWrapper.newInstance(db, id);
	}
}
