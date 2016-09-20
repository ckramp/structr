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

import java.util.Arrays;
import org.structr.bolt.*;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.structr.api.QueryResult;
import org.structr.api.graph.PropertyContainer;
import org.structr.api.index.Index;
import org.structr.api.search.ArrayQuery;
import org.structr.api.search.EmptyQuery;
import org.structr.api.search.ExactQuery;
import org.structr.api.search.FulltextQuery;
import org.structr.api.search.GroupQuery;
import org.structr.api.search.NotEmptyQuery;
import org.structr.api.search.QueryContext;
import org.structr.api.search.QueryPredicate;
import org.structr.api.search.RangeQuery;
import org.structr.api.search.SpatialQuery;
import org.structr.api.search.TypeConverter;
import org.structr.api.search.TypeQuery;
import org.structr.bolt.index.converter.BooleanTypeConverter;
import org.structr.bolt.index.converter.DateTypeConverter;
import org.structr.bolt.index.converter.DoubleTypeConverter;
import org.structr.bolt.index.converter.IntTypeConverter;
import org.structr.bolt.index.converter.LongTypeConverter;
import org.structr.bolt.index.converter.StringTypeConverter;
import org.structr.bolt.index.factory.ArrayQueryFactory;
import org.structr.bolt.index.factory.EmptyQueryFactory;
import org.structr.bolt.index.factory.GroupQueryFactory;
import org.structr.bolt.index.factory.KeywordQueryFactory;
import org.structr.bolt.index.factory.NotEmptyQueryFactory;
import org.structr.bolt.index.factory.QueryFactory;
import org.structr.bolt.index.factory.RangeQueryFactory;
import org.structr.bolt.index.factory.SpatialQueryFactory;
import org.structr.bolt.index.factory.TypeQueryFactory;

/**
 *
 */
public abstract class AbstractCypherIndex<T extends PropertyContainer> implements Index<T>, QueryFactory {

	private static final Logger logger                       = Logger.getLogger(AbstractCypherIndex.class.getName());
	public static final TypeConverter DEFAULT_CONVERTER      = new StringTypeConverter();
	public static final Map<Class, TypeConverter> CONVERTERS = new HashMap<>();
	public static final Map<Class, QueryFactory> FACTORIES   = new HashMap<>();

	public static final Set<Class> INDEXABLE = new HashSet<>(Arrays.asList(new Class[] {
		String.class, Boolean.class, Double.class, Integer.class, Long.class, Character.class, Float.class
	}));

	static {

		FACTORIES.put(NotEmptyQuery.class, new NotEmptyQueryFactory());
		FACTORIES.put(FulltextQuery.class, new KeywordQueryFactory());
		FACTORIES.put(SpatialQuery.class,  new SpatialQueryFactory());
		FACTORIES.put(GroupQuery.class,    new GroupQueryFactory());
		FACTORIES.put(RangeQuery.class,    new RangeQueryFactory());
		FACTORIES.put(ExactQuery.class,    new KeywordQueryFactory());
		FACTORIES.put(ArrayQuery.class,    new ArrayQueryFactory());
		FACTORIES.put(EmptyQuery.class,    new EmptyQueryFactory());
		FACTORIES.put(TypeQuery.class,     new TypeQueryFactory());

		CONVERTERS.put(Boolean.class, new BooleanTypeConverter());
		CONVERTERS.put(String.class,  new StringTypeConverter());
		CONVERTERS.put(Date.class,    new DateTypeConverter());
		CONVERTERS.put(Long.class,    new LongTypeConverter());
		CONVERTERS.put(Integer.class, new IntTypeConverter());
		CONVERTERS.put(Double.class,  new DoubleTypeConverter());
	}

	protected BoltDatabaseService db = null;

	public AbstractCypherIndex(final BoltDatabaseService db) {
		this.db = db;
	}

	public abstract QueryResult<T> getResult(final CypherQuery query);
	public abstract String getQueryPrefix(final QueryContext context,final String typeLabel);

	public String getQuerySuffix(final QueryContext context){

                final StringBuilder buf = new StringBuilder();
                Boolean isAdmin         = null;

                if(context.hasProperty("isAuthenticatedUser") && context.hasProperty("isAdmin")) {

			isAdmin = context.getBooleanProperty("isAdmin");

                }

                if(isAdmin != null && isAdmin) {

                        return " RETURN DISTINCT n";

                } else {

                        buf.append(" ")
                        .append("WITH n, COLLECT(DISTINCT n) AS foundNodes, totalResult as accessibleNodes")
                        .append("\n")
                        .append("WHERE ALL (x IN foundNodes WHERE x IN accessibleNodes)")
                        .append("\n")
                        .append("RETURN DISTINCT n");

                }

		return buf.toString();

        }

        protected String getSecurityPrefix(QueryContext context){

                final StringBuilder buf = new StringBuilder();
                final String nodeType   = context.getStringProperty("nodeType");
                Boolean isAnonymousUser = null;
		Boolean isAdmin         = null;

                if(context.hasProperty("isAuthenticatedUser")) {

                        isAnonymousUser = !context.getBooleanProperty("isAuthenticatedUser");

                        if(context.hasProperty("isAdmin")) {

                                isAdmin = context.getBooleanProperty("isAdmin");
                        }
                }

                if(isAnonymousUser != null && !isAnonymousUser) {

                        buf.append("OPTIONAL MATCH (node")
			.append(nodeType)
			.append(")")
                        .append("\n")
                        .append("WHERE node.`visibleToAuthenticatedUsers` = true")
                        .append("\n")
                        .append("WITH collect(DISTINCT node) AS result_VisibleToAuthenticatedUsers")
                        .append("\n")
                        .append("OPTIONAL MATCH (node")
			.append(nodeType)
			.append(")")
                        .append("\n")
                        .append("WHERE node.id = { uuid }")
                        .append("\n")
                        .append("WITH result_VisibleToAuthenticatedUsers+collect(DISTINCT node) AS result_Self")
                        .append("\n")
                        .append("OPTIONAL MATCH (user:Principal)-[:OWNS]->(node")
			.append(nodeType)
			.append(")")
                        .append("\n")
                        .append("WHERE user.id = { uuid }")
                        .append("\n")
                        .append("WITH result_Self+collect(DISTINCT node) AS result_Ownership")
                        .append("\n")
                        .append("OPTIONAL MATCH (user:Principal)-[s:SECURITY]->(node")
			.append(nodeType)
			.append(")")
                        .append("\n")
                        .append("WHERE user.id = { uuid } AND ANY(x IN s.allowed WHERE x = 'read')")
                        .append("\n")
                        .append("WITH result_Ownership+collect(DISTINCT node) AS result_DirectPermissionGrant")
                        .append("\n")
                        .append("OPTIONAL MATCH (user:Principal)<-[:CONTAINS]-(group:Group)")
                        .append("\n")
                        .append("WHERE user.id = { uuid }")
                        .append("\n")
                        .append("WITH result_DirectPermissionGrant+collect(DISTINCT group) AS result_ContainedGroupGrant")
                        .append("\n")
                        .append("OPTIONAL MATCH (user:Principal)<-[:CONTAINS*]-(group:Group)-[s:SECURITY]->(node")
			.append(nodeType)
			.append(")")
                        .append("\n")
                        .append("WHERE user.id = { uuid }")
                        .append("\n")
                        .append("WITH result_ContainedGroupGrant+collect(DISTINCT node) AS totalResult")
                        .append("\n");

                } else if(isAdmin != null && isAdmin){

                        return "";

                } else {

	                    //Deal with anonymous user
                	buf.append("OPTIONAL MATCH (node")
			.append(nodeType)
			.append(")")
                	.append("\n")
                	.append("WHERE node.`visibleToPublicUsers` = true")
                	.append("\n")
                	.append("WITH collect(DISTINCT node) AS totalResult")
                	.append("\n");

                }

                return buf.toString();

        }

	@Override
	public void add(final PropertyContainer t, final String key, final Object value, final Class typeHint) {

		if (!t.hasProperty(key)) {

			Object indexValue = value;
			if (value != null) {

				if (value.getClass().isEnum()) {
					indexValue = indexValue.toString();
				}

				if (!INDEXABLE.contains(value.getClass())) {
					return;
				}
			}

			t.setProperty(key, indexValue);
		}
	}

	@Override
	public void remove(final PropertyContainer t) {
	}

	@Override
	public void remove(final PropertyContainer t, final String key) {
	}

	@Override
	public Iterable<T> query(final QueryContext context, final QueryPredicate predicate) {

		final CypherQuery query = new CypherQuery(context,this);

		createQuery(this, predicate, query, true);

		final String sortKey = predicate.getSortKey();
		if (sortKey != null) {

			query.sort(predicate.getSortType(), sortKey, predicate.sortDescending());
		}

		if (db.logQueries()) {
			System.out.println(query);
		}

		return getResult(query);
	}

	// ----- interface QueryFactory -----
	@Override
	public boolean createQuery(final QueryFactory parent, final QueryPredicate predicate, final CypherQuery query, final boolean isFirst) {

		final Class type = predicate.getQueryType();
		if (type != null) {

			final QueryFactory factory = FACTORIES.get(type);
			if (factory != null) {

				return factory.createQuery(this, predicate, query, isFirst);

			} else {

				logger.log(Level.WARNING, "No query factory registered for type {0}", type);
			}
		}

		return false;
	}
}
