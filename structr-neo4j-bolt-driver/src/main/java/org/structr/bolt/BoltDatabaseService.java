/**
 * Copyright (C) 2010-2017 Structr GmbH
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
package org.structr.bolt;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Config;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.exceptions.ClientException;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.DatabaseService;
import org.structr.api.NativeResult;
import org.structr.api.NotInTransactionException;
import org.structr.api.QueryResult;
import org.structr.api.Transaction;
import org.structr.api.config.Settings;
import org.structr.api.graph.GraphProperties;
import org.structr.api.graph.Label;
import org.structr.api.graph.Node;
import org.structr.api.graph.Relationship;
import org.structr.api.graph.RelationshipType;
import org.structr.api.index.Index;
import org.structr.api.util.QueryUtils;
import org.structr.bolt.index.CypherNodeIndex;
import org.structr.bolt.index.CypherRelationshipIndex;
import org.structr.bolt.index.NodeResultStream;
import org.structr.bolt.index.RelationshipResultStream;
import org.structr.bolt.index.SimpleCypherQuery;
import org.structr.bolt.mapper.NodeNodeMapper;
import org.structr.bolt.mapper.RelationshipRelationshipMapper;
import org.structr.bolt.wrapper.NodeWrapper;
import org.structr.bolt.wrapper.RelationshipWrapper;

/**
 *
 */
public class BoltDatabaseService implements DatabaseService, GraphProperties {

	private static final Logger logger                                = LoggerFactory.getLogger(BoltDatabaseService.class.getName());
	private static final Map<String, RelationshipType> relTypeCache   = new ConcurrentHashMap<>();
	private static final Map<String, Label> labelCache                = new ConcurrentHashMap<>();
	private static final ThreadLocal<SessionTransaction> sessions     = new ThreadLocal<>();
	private Properties globalGraphProperties                          = null;
	private CypherRelationshipIndex relationshipIndex                 = null;
	private CypherNodeIndex nodeIndex                                 = null;
	private GraphDatabaseService graphDb                              = null;
	private boolean needsIndexRebuild                                 = false;
	private String databaseUrl                                        = null;
	private String databasePath                                       = null;
	private Driver driver                                             = null;
	private int queryCacheSize                                        = 1000;

	@Override
	public void initialize() {

		this.databasePath = Settings.DatabasePath.getValue();

		final GraphDatabaseSettings.BoltConnector bolt = GraphDatabaseSettings.boltConnector("0");
		databaseUrl                                    = Settings.ConnectionUrl.getValue();
		final String username                          = Settings.ConnectionUser.getValue();
		final String password                          = Settings.ConnectionPassword.getValue();
		final String driverMode                        = Settings.DatabaseDriverMode.getValue();
		final String confPath                          = databasePath + "/neo4j.conf";
		final File confFile                            = new File(confPath);
		boolean tryAgain                               = true;

		// see https://github.com/neo4j/neo4j-java-driver/issues/364 for an explanation
		final String databaseServerUrl;
		final String databaseDriverUrl;

		if (databaseUrl.length() >= 7 && databaseUrl.substring(0, 7).equalsIgnoreCase("bolt://")) {
			databaseServerUrl = databaseUrl.substring(7);
			databaseDriverUrl = databaseUrl;
		} else {
			databaseServerUrl = databaseUrl;
			databaseDriverUrl = "bolt://" + databaseUrl;
		}

		// create db directory if it does not exist
		new File(databasePath).mkdirs();

		if (!"remote".equals(driverMode)) {

			final GraphDatabaseBuilder builder = new GraphDatabaseFactory()
				.newEmbeddedDatabaseBuilder(new File(databasePath))
				.setConfig( GraphDatabaseSettings.allow_store_upgrade, "true")
				.setConfig("dbms.allow_format_migration", "true")
				.setConfig( bolt.type, "BOLT" )
				.setConfig( bolt.enabled, "true" )
				.setConfig( bolt.address, databaseServerUrl);

			if (confFile.exists()) {
				builder.loadPropertiesFromFile(confPath);
			}

			while (tryAgain) {

				try {
					graphDb  = builder.newGraphDatabase();
					tryAgain = false;

				} catch (Throwable t) {

					tryAgain = handleMigration(t);
				}
			}
		}

		driver = GraphDatabase.driver(databaseDriverUrl,
			AuthTokens.basic(username, password),
			Config.build().withEncryptionLevel(Config.EncryptionLevel.NONE).toConfig()
		);

		final int relCacheSize  = Settings.RelationshipCacheSize.getValue();
		final int nodeCacheSize = Settings.NodeCacheSize.getValue();
		this.queryCacheSize     = Settings.QueryCacheSize.getValue();

		NodeWrapper.initialize(nodeCacheSize);
		logger.info("Node cache size set to {}", nodeCacheSize);

		RelationshipWrapper.initialize(relCacheSize);
		logger.info("Relationship cache size set to {}", relCacheSize);
	}

	@Override
	public void shutdown() {

		RelationshipWrapper.clearCache();
		NodeWrapper.clearCache();

		driver.close();
		graphDb.shutdown();
	}

	@Override
	public <T> T forName(final Class<T> type, final String name) {

		if (Label.class.equals(type)) {

			return (T)getOrCreateLabel(name);
		}

		if (RelationshipType.class.equals(type)) {

			return (T)getOrCreateRelationshipType(name);
		}

		throw new RuntimeException("Cannot create object of type " + type);
	}


	@Override
	public Transaction beginTx() {

		SessionTransaction session = sessions.get();
		if (session == null || session.isClosed()) {

			try {
				session = new SessionTransaction(this, driver.session());
				sessions.set(session);

			} catch (ClientException cex) {
				logger.warn("Cannot connect to Neo4j database server at {}", databaseUrl);
			}
		}

		return session;
	}

	@Override
	public Node createNode(final Set<String> labels, final Map<String, Object> properties) {

		final StringBuilder buf       = new StringBuilder("CREATE (n");
		final Map<String, Object> map = new HashMap<>();

		for (final String label : labels) {

			buf.append(":");
			buf.append(label);
		}

		buf.append(" {properties}) RETURN n");

		// make properties available to Cypher statement
		map.put("properties", properties);

		return NodeWrapper.newInstance(this, getCurrentTransaction().getNode(buf.toString(), map));
	}

	@Override
	public Node getNodeById(final long id) {
		return NodeWrapper.newInstance(this, id);
	}

	@Override
	public Relationship getRelationshipById(final long id) {

		final SessionTransaction tx   = getCurrentTransaction();
		final Map<String, Object> map = new HashMap<>();

		map.put("id", id);

		final org.neo4j.driver.v1.types.Relationship rel = tx.getRelationship("MATCH ()-[r]->() WHERE ID(r) = {id} RETURN r", map);

		return RelationshipWrapper.newInstance(this, rel);

	}

	@Override
	public QueryResult<Node> getAllNodes() {

		final SessionTransaction tx = getCurrentTransaction();
		final NodeNodeMapper mapper = new NodeNodeMapper(this);

		return QueryUtils.map(mapper, new NodeResultStream(tx, new SimpleCypherQuery("MATCH (n) RETURN n")));
	}

	@Override
	public QueryResult<Node> getNodesByLabel(final String type) {

		if (type == null) {
			return getAllNodes();
		}

		final SessionTransaction tx = getCurrentTransaction();
		final NodeNodeMapper mapper = new NodeNodeMapper(this);

		return QueryUtils.map(mapper, new NodeResultStream(tx, new SimpleCypherQuery("MATCH (n:" + type + ") RETURN n")));
	}

	@Override
	public QueryResult<Node> getNodesByTypeProperty(final String type) {

		if (type == null) {
			return getAllNodes();
		}

		final SessionTransaction tx   = getCurrentTransaction();
		final NodeNodeMapper mapper   = new NodeNodeMapper(this);
		final SimpleCypherQuery query = new SimpleCypherQuery("MATCH (n) WHERE n.type = {type} RETURN n");

		query.getParameters().put("type", type);

		return QueryUtils.map(mapper, new NodeResultStream(tx, query));
	}

	@Override
	public QueryResult<Relationship> getAllRelationships() {

		final RelationshipRelationshipMapper mapper = new RelationshipRelationshipMapper(this);
		final SessionTransaction tx                 = getCurrentTransaction();

		return QueryUtils.map(mapper, new RelationshipResultStream(tx, new SimpleCypherQuery("MATCH ()-[r]->() RETURN r")));
	}

	@Override
	public QueryResult<Relationship> getRelationshipsByType(final String type) {

		if (type == null) {
			return getAllRelationships();
		}

		final RelationshipRelationshipMapper mapper = new RelationshipRelationshipMapper(this);
		final SessionTransaction tx                 = getCurrentTransaction();

		return QueryUtils.map(mapper, new RelationshipResultStream(tx, new SimpleCypherQuery("MATCH ()-[r:" + type + "]->() RETURN r")));
	}

	@Override
	public GraphProperties getGlobalProperties() {
		return this;
	}

	@Override
	public Index<Node> nodeIndex() {

		if (nodeIndex == null) {
			nodeIndex = new CypherNodeIndex(this, queryCacheSize);
		}

		return nodeIndex;
	}

	@Override
	public Index<Relationship> relationshipIndex() {

		if (relationshipIndex == null) {
			relationshipIndex = new CypherRelationshipIndex(this, queryCacheSize);
		}

		return relationshipIndex;
	}

	@Override
	public NativeResult execute(final String nativeQuery, final Map<String, Object> parameters) {
		return getCurrentTransaction().run(nativeQuery, parameters);
	}

	@Override
	public NativeResult execute(final String nativeQuery) {
		return execute(nativeQuery, Collections.EMPTY_MAP);
	}

	@Override
	public void invalidateQueryCache() {

		if (nodeIndex != null) {
			nodeIndex.invalidateCache();
		}

		if (relationshipIndex != null) {
			relationshipIndex.invalidateCache();
		}
	}

	public SessionTransaction getCurrentTransaction() {

		final SessionTransaction tx = sessions.get();
		if (tx == null || tx.isClosed()) {

			throw new NotInTransactionException("Not in transaction");
		}

		return tx;
	}

	public boolean logQueries() {
		return Settings.CypherDebugLogging.getValue();
	}

	// ----- interface GraphProperties -----
	@Override
	public void setProperty(final String name, final Object value) {

		final Properties properties = getProperties();
		boolean hasChanges          = false;

		if (value == null) {

			if (properties.containsKey(name)) {

				properties.remove(name);
				hasChanges = true;
			}

		} else {

			properties.setProperty(name, value.toString());
			hasChanges = true;
		}

		if (hasChanges) {

			final File propertiesFile   = new File(databasePath + "/graph.properties");

			try (final Writer writer = new FileWriter(propertiesFile)) {

				properties.store(writer, "Created by Structr at " + new Date());

			} catch (IOException ioex) {

				logger.warn("Unable to write properties file", ioex);
			}
		}
	}

	@Override
	public Object getProperty(final String name) {
		return getProperties().getProperty(name);
	}

	@Override
	public boolean needsIndexRebuild() {
		return needsIndexRebuild;
	}

	public Label getOrCreateLabel(final String name) {

		Label label = labelCache.get(name);
		if (label == null) {

			label = new LabelImpl(name);
			labelCache.put(name, label);
		}

		return label;
	}

	public RelationshipType getOrCreateRelationshipType(final String name) {

		RelationshipType relType = relTypeCache.get(name);
		if (relType == null) {

			relType = new RelationshipTypeImpl(name);
			relTypeCache.put(name, relType);
		}

		return relType;
	}

	// ----- private methods -----
	private Properties getProperties() {

		if (globalGraphProperties == null) {

			globalGraphProperties = new Properties();
			final File propertiesFile   = new File(databasePath + "/graph.properties");

			try (final Reader reader = new FileReader(propertiesFile)) {

				globalGraphProperties.load(reader);

			} catch (IOException ioex) {}
		}

		return globalGraphProperties;
	}

	private boolean handleMigration(final Throwable t) {

		final List<String> messages = collectMessages(t);
		if (contains(messages, "Legacy index migration failed")) {

			// try to remove index directory and try again
			logger.info("Legacy index migration failed, moving offending index files out of the way.");

			final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
			final File indexDbFile    = new File(databasePath + "/index.db");
			final File indexDir       = new File(databasePath + "/index");

			if (indexDbFile.exists()) {

				indexDbFile.renameTo(new File(databasePath + "/index.db.orig-" + df.format(System.currentTimeMillis())));
			}

			if (indexDir.exists()) {

				indexDir.renameTo(new File(databasePath + "/index.orig-" + df.format(System.currentTimeMillis())));
			}

			// raise rebuild index flag
			this.needsIndexRebuild = true;

			// signal the service to try again
			return true;
		}

		// cannot handle error
		throw new RuntimeException(t);
	}

	private boolean contains(final List<String> src, final String toFind) {

		for (final String s : src) {

			if (s.contains(toFind)) {

				return true;
			}
		}

		return false;
	}

	private List<String> collectMessages(final Throwable t) {

		final List<String> messages = new LinkedList<>();
		Throwable current           = t;

		// collect exception messages
		while (current != null) {

			final String message = current.getMessage();
			if (message != null) {

				messages.add(message);
			}

			current = current.getCause();
		}

		return messages;
	}

	// ----- nested classes -----
	private static class LabelImpl implements Label {

		private String name = null;

		private LabelImpl(final String name) {
			this.name = name;
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public int hashCode() {
			return name.hashCode();
		}

		@Override
		public boolean equals(final Object other) {

			if (other instanceof Label) {
				return other.hashCode() == hashCode();
			}

			return false;
		}
	}

	private static class RelationshipTypeImpl implements RelationshipType {

		private String name = null;

		private RelationshipTypeImpl(final String name) {
			this.name = name;
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public int hashCode() {
			return name.hashCode();
		}

		@Override
		public boolean equals(final Object other) {

			if (other instanceof RelationshipType) {
				return other.hashCode() == hashCode();
			}

			return false;
		}
	}
}
