/**
 * Copyright (C) 2010-2018 Structr GmbH
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
package org.structr.bolt.wrapper;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.NotFoundException;
import org.structr.api.graph.Direction;
import org.structr.api.graph.Label;
import org.structr.api.graph.Node;
import org.structr.api.graph.Relationship;
import org.structr.api.graph.RelationshipType;
import org.structr.api.util.FixedSizeCache;
import org.structr.api.util.Iterables;
import org.structr.bolt.BoltDatabaseService;
import org.structr.bolt.SessionTransaction;
import org.structr.bolt.mapper.RelationshipRelationshipMapper;

/**
 *
 */
public class NodeWrapper extends EntityWrapper<org.neo4j.driver.v1.types.Node> implements Node {

	private static final Logger logger                                           = LoggerFactory.getLogger(NodeWrapper.class);
	private final Map<String, Map<String, RelationshipResult>> relationshipCache = new HashMap<>();
	private static FixedSizeCache<Long, NodeWrapper> nodeCache                   = null;
	private boolean dontUseCache                                                 = false;

	private NodeWrapper(final BoltDatabaseService db, final org.neo4j.driver.v1.types.Node node) {
		super(db, node);
	}

	public static void initialize(final int cacheSize) {
		nodeCache = new FixedSizeCache<>(cacheSize);
	}

	@Override
	protected String getQueryPrefix() {

		return concat("MATCH (n", getTenantIdentifer(db), ")");
	}

	@Override
	public void onRemoveFromCache() {
		relationshipCache.clear();
		this.stale = true;
	}

	@Override
	public void clearCaches() {
		relationshipCache.clear();
	}

	@Override
	public void onClose() {
		dontUseCache = false;
		relationshipCache.clear();
	}

	@Override
	public Relationship createRelationshipTo(final Node endNode, final RelationshipType relationshipType) {
		return createRelationshipTo(endNode, relationshipType, new LinkedHashMap<>());
	}

	@Override
	public Relationship createRelationshipTo(final Node endNode, final RelationshipType relationshipType, final Map<String, Object> properties) {

		assertNotStale();

		dontUseCache = true;

		final SessionTransaction tx   = db.getCurrentTransaction();
		final Map<String, Object> map = new HashMap<>();
		final NodeWrapper otherNode   = (NodeWrapper)endNode;
		final String tenantIdentifier = getTenantIdentifer(db);
		final StringBuilder buf       = new StringBuilder();

		map.put("id1", id);
		map.put("id2", endNode.getId());
		map.put("relProperties", properties);

		buf.append("MATCH (n");
		buf.append(tenantIdentifier);
		buf.append("), (m");
		buf.append(tenantIdentifier);
		buf.append(") WHERE ID(n) = {id1} AND ID(m) = {id2} ");
		buf.append("MERGE (n)-[r:");
		buf.append(relationshipType.name());
		buf.append("]->(m)");
		buf.append(" SET r += {relProperties} RETURN r");

		final org.neo4j.driver.v1.types.Relationship rel = tx.getRelationship(buf.toString(), map);

		setModified();
		otherNode.setModified();

		// clear caches
		((NodeWrapper)endNode).relationshipCache.clear();
		relationshipCache.clear();

		final RelationshipWrapper createdRelationship = RelationshipWrapper.newInstance(db, rel);

		createdRelationship.setModified();

		return createdRelationship;
	}

	@Override
	public void addLabel(final Label label) {

		assertNotStale();

		final SessionTransaction tx   = db.getCurrentTransaction();
		final Map<String, Object> map = new HashMap<>();
		final String tenantIdentifier = getTenantIdentifer(db);

		map.put("id", id);

		tx.set(concat("MATCH (n", tenantIdentifier, ") WHERE ID(n) = {id} SET n :", label.name()), map);

		setModified();
	}

	@Override
	public void removeLabel(final Label label) {

		assertNotStale();

		final SessionTransaction tx   = db.getCurrentTransaction();
		final Map<String, Object> map = new HashMap<>();
		final String tenantIdentifier = getTenantIdentifer(db);

		map.put("id", id);

		tx.set(concat("MATCH (n", tenantIdentifier, ") WHERE ID(n) = {id} REMOVE n:", label.name()), map);
		setModified();
	}

	@Override
	public Iterable<Label> getLabels() {

		assertNotStale();

		final SessionTransaction tx   = db.getCurrentTransaction();
		final Map<String, Object> map = new HashMap<>();
		final List<Label> result      = new LinkedList<>();
		final String tenantIdentifier = getTenantIdentifer(db);

		map.put("id", id);

		// execute query
		for (final String label : tx.getStrings(concat("MATCH (n", tenantIdentifier, ") WHERE ID(n) = {id} RETURN LABELS(n)"), map)) {
			result.add(db.forName(Label.class, label));
		}

		return result;
	}

	@Override
	public boolean hasRelationshipTo(final RelationshipType type, final Node targetNode) {

		assertNotStale();

		final SessionTransaction tx      = db.getCurrentTransaction();
		final Map<String, Object> params = new LinkedHashMap<>();
		final String tenantIdentifier = getTenantIdentifer(db);

		params.put("id1", getId());
		params.put("id2", targetNode.getId());

		try {

			// try to fetch existing relationship by node ID(s)
			// FIXME: this call can be very slow when lots of relationships exist
			tx.getLong(concat("MATCH (n", tenantIdentifier, ")-[r:", type.name(), "]->(m", tenantIdentifier, ") WHERE id(n) = {id1} AND id(m) = {id2} RETURN id(r)"), params);

			// success
			return true;

		} catch (Throwable t) {

			return false;
		}
	}

	@Override
	public Iterable<Relationship> getRelationships() {

		assertNotStale();

		final RelationshipResult cache = getRelationshipCache(null, null);
		final String tenantIdentifier = getTenantIdentifer(db);

		return cache.getResult(db, id, concat("(n", tenantIdentifier, ")-[r]-(o)"), "(n)-[]-()", "RETURN r, o ORDER BY r.internalTimestamp");
	}

	@Override
	public Iterable<Relationship> getRelationships(final Direction direction) {

		assertNotStale();

		final RelationshipResult cache = getRelationshipCache(direction, null);
		final String tenantIdentifier = getTenantIdentifer(db);

		switch (direction) {

			case BOTH:
				return getRelationships();

			case OUTGOING:
				return cache.getResult(db, id, concat("(n", tenantIdentifier, ")-[r]->(t)"), "(n)-[]->()", "RETURN r, t ORDER BY r.internalTimestamp");

			case INCOMING:
				return cache.getResult(db, id, concat("(n", tenantIdentifier , ")<-[r]->(s)"), "(n)<-[]-()", "RETURN r, s ORDER BY r.internalTimestamp");
		}

		return null;
	}

	@Override
	public Iterable<Relationship> getRelationships(final Direction direction, final RelationshipType relationshipType) {

		assertNotStale();


		final RelationshipResult cache = getRelationshipCache(direction, relationshipType);
		final String tenantIdentifier = getTenantIdentifer(db);
		final String rel               = relationshipType.name();

		switch (direction) {

			case BOTH:
				return cache.getResult(db, id, concat("(n", tenantIdentifier, ")-[r:", rel, "]-(o)"), concat("(n)-[:", rel, "]-()"), "RETURN r, o ORDER BY r.internalTimestamp");

			case OUTGOING:
				return cache.getResult(db, id, concat("(n", tenantIdentifier, ")-[r:", rel, "]->(t)"), concat("(n)-[:", rel, "]->()"), "RETURN r, t ORDER BY r.internalTimestamp");

			case INCOMING:
				return cache.getResult(db, id, concat("(n", tenantIdentifier, ")<-[r:", rel, "]-(s)"), concat("(n)<-[:", rel, "]-()"), "RETURN r, s ORDER BY r.internalTimestamp");
		}

		return null;
	}

	@Override
	public void delete(final boolean deleteRelationships) {

		super.delete(deleteRelationships);

		final SessionTransaction tx = db.getCurrentTransaction();
		tx.deleted(this);
	}

	/**
	 * Evaluate a custom query and return result as a boolean value
	 *
	 * @param customQuery
	 * @param parameters
	 * @return
	 */
	public boolean evaluateCustomQuery(final String customQuery, final Map<String, Object> parameters) {

		final SessionTransaction tx = db.getCurrentTransaction();
		boolean result              = false;

		try {
			result = tx.getBoolean(customQuery, parameters);

		} catch (Exception ignore) {}

		return result;
	}

	public void addToCache(final RelationshipWrapper rel) {

		final Direction direction   = rel.getDirectionForNode(this);
		final RelationshipType type = rel.getType();
		RelationshipResult list = getRelationshipCache(direction, type);

		list.add(rel);
	}

	// ----- public static methods -----
	public static FixedSizeCache<Long, NodeWrapper> getCache() {
		return nodeCache;
	}

	public static void expunge(final Set<Long> toRemove) {

		synchronized (nodeCache) {

			nodeCache.removeAll(toRemove);
		}
	}

	public static void clearCache() {

		synchronized (nodeCache) {

			nodeCache.clear();
		}
	}

	public static NodeWrapper newInstance(final BoltDatabaseService db, final org.neo4j.driver.v1.types.Node node) {

		synchronized (nodeCache) {

			NodeWrapper wrapper = nodeCache.get(node.id());
			if (wrapper == null || wrapper.stale) {

				wrapper = new NodeWrapper(db, node);
				nodeCache.put(node.id(), wrapper);
			}

			return wrapper;
		}
	}

	public static NodeWrapper newInstance(final BoltDatabaseService db, final long id) {

		synchronized (nodeCache) {

			NodeWrapper wrapper = nodeCache.get(id);
			if (wrapper == null || wrapper.stale) {

				final SessionTransaction tx   = db.getCurrentTransaction();
				final Map<String, Object> map = new HashMap<>();

				map.put("id", id);

				final Iterable<org.neo4j.driver.v1.types.Node> result   = tx.getNodes("MATCH (n) WHERE ID(n) = {id} RETURN DISTINCT n", map);
				final Iterator<org.neo4j.driver.v1.types.Node> iterator = result.iterator();

				if (iterator.hasNext()) {

					wrapper = NodeWrapper.newInstance(db, iterator.next());

					nodeCache.put(id, wrapper);

				} else {

					throw new NotFoundException("Node with ID " + id + " not found.");
				}
			}

			return wrapper;
		}
	}

	// ----- protected methods -----
	@Override
	protected boolean isNode() {
		return true;
	}

	// ----- private methods -----
	private Map<String, RelationshipResult> getCache(final Direction direction) {

		final String directionKey             = direction != null ? direction.name() : "*";
		Map<String, RelationshipResult> cache = relationshipCache.get(directionKey);

		if (cache == null) {

			cache = new HashMap<>();
			relationshipCache.put(directionKey, cache);
		}

		return cache;
	}

	private RelationshipResult getRelationshipCache(final Direction direction, final RelationshipType relType) {

		final String relTypeKey                     = relType != null ? relType.name() : "*";
		final Map<String, RelationshipResult> cache = getCache(direction);

		RelationshipResult count = cache.get(relTypeKey);
		if (count == null) {

			count = new RelationshipResult();
			cache.put(relTypeKey, count);
		}

		// never return null
		return count;
	}

	private String concat(final String... parts) {

		final StringBuilder buf = new StringBuilder();

		for (final String part : parts) {

			// handle nulls gracefully (ignore)
			if (part != null) {

				buf.append(part);
			}
		}

		return buf.toString();
	}

	private String getTenantIdentifer(final BoltDatabaseService db) {

		final String identifier = db.getTenantIdentifier();

		if (StringUtils.isNotBlank(identifier)) {

			return ":" + identifier;
		}

		return "";
	}

	// ----- nested classes -----
	private class RelationshipResult {

		private Set<Relationship> set = null;
		private Long count            = null;
		private final int threshold   = 1000;

		public void add(final Relationship rel) {

			if (set != null) {

				set.add(rel);
			}
		}

		public synchronized Iterable<Relationship> getResult(final BoltDatabaseService db, final long id, final String match, final String pattern, final String returnStatement) {

			final RelationshipRelationshipMapper mapper = new RelationshipRelationshipMapper(db);
			final Map<String, Object> map               = new HashMap<>();
			final SessionTransaction tx                 = db.getCurrentTransaction();
			final String whereStatement                 = " WHERE ID(n) = {id} ";

			map.put("id", id);

			if (count == null) {

				// do count query
				count = tx.getLong(concat("MATCH (n)", whereStatement, "RETURN SIZE(", pattern, ")"), map);
			}

			if (count > threshold || dontUseCache) {

				if (count > threshold) {

					logger.info("Using explicit result streaming for collection of {} relationships.", count);
				}

				// return streaming result
				return Iterables.map(mapper, tx.getRelationships(concat("MATCH ", match, whereStatement, returnStatement), map));

			} else {

				// else: return cached result
				if (set == null) {

					set = Iterables.toSet(Iterables.map(mapper, tx.getRelationships(concat("MATCH ", match, whereStatement, returnStatement), map)));
				}

				return set;
			}
		}
	}
}
