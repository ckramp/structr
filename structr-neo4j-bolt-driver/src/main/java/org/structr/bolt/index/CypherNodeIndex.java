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
package org.structr.bolt.index;

import org.structr.api.QueryResult;
import org.structr.api.graph.Node;
import org.structr.api.search.QueryContext;
import org.structr.api.util.Iterables;
import org.structr.bolt.BoltDatabaseService;
import org.structr.bolt.SessionTransaction;
import org.structr.bolt.mapper.NodeNodeMapper;

/**
 *
 */
public class CypherNodeIndex extends AbstractCypherIndex<Node> {

	public CypherNodeIndex(final BoltDatabaseService db, final int queryCacheSize) {
		super(db, queryCacheSize);
	}

	@Override
	public String getQueryPrefix(final QueryContext context, final String typeLabel, final String sourceTypeLabel, final String targetTypeLabel) {

		return getQueryPrefix(context, typeLabel);

	}

	@Override
	public String getQueryPrefix(final QueryContext context, final String typeLabel) {

		if (typeLabel != null) {

			return getSecurityPrefix(context)+"MATCH (n:" + typeLabel + ")";

		} else {

                        return getSecurityPrefix(context)+"MATCH (n)";

                }


	}


	@Override
	public QueryResult<Node> getResult(final CypherQuery query) {

		final int queryHashCode  = query.getHashCode();
		CachedQueryResult result = queryCache.get(queryHashCode);

		if (result == null) {

			final SessionTransaction tx = db.getCurrentTransaction();
			final NodeNodeMapper mapper = new NodeNodeMapper(db);
			final Iterable<Node> mapped = Iterables.map(mapper, tx.getNodes(query.getStatement(), query.getParameters()));

			result = new CachedQueryResult(mapped);

			queryCache.put(queryHashCode, result);
		}

		return result;
	}
}
