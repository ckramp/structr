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
package org.structr.bolt;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.StatementResultCursor;
import org.neo4j.driver.v1.Transaction;
import org.neo4j.driver.v1.Value;
import org.neo4j.driver.v1.Values;
import org.neo4j.driver.v1.exceptions.ClientException;
import org.neo4j.driver.v1.exceptions.DatabaseException;
import org.neo4j.driver.v1.exceptions.NoSuchRecordException;
import org.neo4j.driver.v1.exceptions.ServiceUnavailableException;
import org.neo4j.driver.v1.exceptions.TransientException;
import org.neo4j.driver.v1.types.Entity;
import org.neo4j.driver.v1.types.Node;
import org.neo4j.driver.v1.types.Relationship;
import org.structr.api.ConstraintViolationException;
import org.structr.api.DataFormatException;
import org.structr.api.NetworkException;
import org.structr.api.NotFoundException;
import org.structr.api.RetryException;
import org.structr.api.UnknownClientException;
import org.structr.api.UnknownDatabaseException;
import org.structr.api.util.Iterables;
import org.structr.bolt.mapper.RecordMapMapper;
import org.structr.bolt.mapper.RecordNodeMapper;
import org.structr.bolt.mapper.RecordRelationshipMapper;
import org.structr.bolt.wrapper.EntityWrapper;
import org.structr.bolt.wrapper.NodeWrapper;
import org.structr.bolt.wrapper.RelationshipWrapper;

/**
 *
 */
public class SessionTransaction implements org.structr.api.Transaction {

	private static final AtomicLong idSource          = new AtomicLong();
	private final Set<EntityWrapper> accessedEntities = new HashSet<>();
	private final Set<EntityWrapper> modifiedEntities = new HashSet<>();
	private final Set<Long> deletedNodes              = new HashSet<>();
	private final Set<Long> deletedRels               = new HashSet<>();
	private BoltDatabaseService db                    = null;
	private Session session                           = null;
	private Transaction tx                            = null;
	private long transactionId                        = 0L;
	private boolean closed                            = false;
	private boolean success                           = false;
	private boolean isPing                            = false;

	public SessionTransaction(final BoltDatabaseService db, final Session session) {

		this.transactionId = idSource.getAndIncrement();
		this.session       = session;
		this.tx            = session.beginTransaction();
		this.db            = db;
	}

	@Override
	public void failure() {
		tx.failure();
	}

	@Override
	public void success() {

		tx.success();

		// transaction must be marked successfull explicitly
		success = true;
	}

	@Override
	public void close() {

		if (!success) {

			for (final EntityWrapper entity : accessedEntities) {

				entity.rollback(transactionId);
				entity.removeFromCache();
			}

			for (final EntityWrapper entity : modifiedEntities) {
				entity.stale();
			}

		} else {

			RelationshipWrapper.expunge(deletedRels);
			NodeWrapper.expunge(deletedNodes);

			for (final EntityWrapper entity : accessedEntities) {
				entity.commit(transactionId);
			}

			for (final EntityWrapper entity : modifiedEntities) {
				entity.clearCaches();
			}
		}

		// mark this transaction as closed BEFORE trying to actually close it
		// so that it is closed in case of a failure
		closed = true;

		try {

			tx.close();
			session.close();

		} catch (TransientException tex) {

			// transient exceptions can be retried
			throw new RetryException(tex);

		} finally {

			// Notify all nodes that are modified in this transaction
			// so that the relationship caches are rebuilt.
			for (final EntityWrapper entity : modifiedEntities) {
				entity.onClose();
			}

			// make sure that the resources are freed
			if (session.isOpen()) {
				session.close();
			}
		}
	}

	public boolean isClosed() {
		return closed;
	}

	public void setClosed(final boolean closed) {
		this.closed = closed;
	}

	public boolean getBoolean(final String statement) {

		try {

			logQuery(statement);
			return getBoolean(statement, Collections.EMPTY_MAP);

		} catch (TransientException tex) {
			closed = true;
			throw new RetryException(tex);
		} catch (NoSuchRecordException nex) {
			throw new NotFoundException(nex);
		} catch (ServiceUnavailableException ex) {
			throw new NetworkException(ex.getMessage(), ex);
		} catch (DatabaseException dex) {
			throw SessionTransaction.translateDatabaseException(dex);
		} catch (ClientException cex) {
			throw SessionTransaction.translateClientException(cex);
		}
	}

	public boolean getBoolean(final String statement, final Map<String, Object> map) {

		try {

			logQuery(statement, map);
			return tx.run(statement, map).next().get(0).asBoolean();

		} catch (TransientException tex) {
			closed = true;
			throw new RetryException(tex);
		} catch (NoSuchRecordException nex) {
			throw new NotFoundException(nex);
		} catch (ServiceUnavailableException ex) {
			throw new NetworkException(ex.getMessage(), ex);
		} catch (DatabaseException dex) {
			throw SessionTransaction.translateDatabaseException(dex);
		} catch (ClientException cex) {
			throw SessionTransaction.translateClientException(cex);
		}
	}

	public long getLong(final String statement) {

		try {

			logQuery(statement);
			return getLong(statement, Collections.EMPTY_MAP);

		} catch (TransientException tex) {
			closed = true;
			throw new RetryException(tex);
		} catch (NoSuchRecordException nex) {
			throw new NotFoundException(nex);
		} catch (ServiceUnavailableException ex) {
			throw new NetworkException(ex.getMessage(), ex);
		} catch (DatabaseException dex) {
			throw SessionTransaction.translateDatabaseException(dex);
		} catch (ClientException cex) {
			throw SessionTransaction.translateClientException(cex);
		}
	}

	public long getLong(final String statement, final Map<String, Object> map) {

		try {

			logQuery(statement, map);
			return tx.run(statement, map).next().get(0).asLong();

		} catch (TransientException tex) {
			closed = true;
			throw new RetryException(tex);
		} catch (NoSuchRecordException nex) {
			throw new NotFoundException(nex);
		} catch (ServiceUnavailableException ex) {
			throw new NetworkException(ex.getMessage(), ex);
		} catch (DatabaseException dex) {
			throw SessionTransaction.translateDatabaseException(dex);
		} catch (ClientException cex) {
			throw SessionTransaction.translateClientException(cex);
		}
	}

	public Object getObject(final String statement, final Map<String, Object> map) {

		try {

			logQuery(statement, map);
			final StatementResult result = tx.run(statement, map);
			if (result.hasNext()) {

				return result.next().get(0).asObject();
			}

		} catch (TransientException tex) {
			closed = true;
			throw new RetryException(tex);
		} catch (NoSuchRecordException nex) {
			throw new NotFoundException(nex);
		} catch (ServiceUnavailableException ex) {
			throw new NetworkException(ex.getMessage(), ex);
		} catch (DatabaseException dex) {
			throw SessionTransaction.translateDatabaseException(dex);
		} catch (ClientException cex) {
			throw SessionTransaction.translateClientException(cex);
		}

		return null;
	}

	public Entity getEntity(final String statement, final Map<String, Object> map) {

		try {

			logQuery(statement, map);
			return tx.run(statement, map).next().get(0).asEntity();

		} catch (TransientException tex) {
			closed = true;
			throw new RetryException(tex);
		} catch (NoSuchRecordException nex) {
			throw new NotFoundException(nex);
		} catch (ServiceUnavailableException ex) {
			throw new NetworkException(ex.getMessage(), ex);
		} catch (DatabaseException dex) {
			throw SessionTransaction.translateDatabaseException(dex);
		} catch (ClientException cex) {
			throw SessionTransaction.translateClientException(cex);
		}
	}

	public Node getNode(final String statement, final Map<String, Object> map) {

		try {

			logQuery(statement, map);
			return tx.run(statement, map).next().get(0).asNode();

		} catch (TransientException tex) {
			closed = true;
			throw new RetryException(tex);
		} catch (NoSuchRecordException nex) {
			throw new NotFoundException(nex);
		} catch (ServiceUnavailableException ex) {
			throw new NetworkException(ex.getMessage(), ex);
		} catch (DatabaseException dex) {
			throw SessionTransaction.translateDatabaseException(dex);
		} catch (ClientException cex) {
			throw SessionTransaction.translateClientException(cex);
		}
	}

	public Relationship getRelationship(final String statement, final Map<String, Object> map) {

		try {

			logQuery(statement, map);
			return tx.run(statement, map).next().get(0).asRelationship();

		} catch (TransientException tex) {
			closed = true;
			throw new RetryException(tex);
		} catch (NoSuchRecordException nex) {
			throw new NotFoundException(nex);
		} catch (ServiceUnavailableException ex) {
			throw new NetworkException(ex.getMessage(), ex);
		} catch (DatabaseException dex) {
			throw SessionTransaction.translateDatabaseException(dex);
		} catch (ClientException cex) {
			throw SessionTransaction.translateClientException(cex);
		}
	}

	public Iterable<Node> getNodes(final String statement, final Map<String, Object> map) {

		try {

			final IterableQueueingConsumer<Record> consumer = new IterableQueueingConsumer<>();

			logQuery(statement, map);

			tx.runAsync(statement, map)
				.thenCompose(cursor -> consumer.start(cursor))
				.thenCompose(cursor -> cursor.forEachAsync(consumer::add))
				.thenAccept(ignore  -> consumer.finish());

			return Iterables.map(new RecordNodeMapper(), consumer);

		} catch (TransientException tex) {
			closed = true;
			throw new RetryException(tex);
		} catch (NoSuchRecordException nex) {
			throw new NotFoundException(nex);
		} catch (ServiceUnavailableException ex) {
			throw new NetworkException(ex.getMessage(), ex);
		} catch (DatabaseException dex) {
			throw SessionTransaction.translateDatabaseException(dex);
		} catch (ClientException cex) {
			throw SessionTransaction.translateClientException(cex);
		}
	}

	public Iterable<Relationship> getRelationships(final String statement, final Map<String, Object> map) {

		try {

			final IterableQueueingConsumer<Record> consumer = new IterableQueueingConsumer<>();

			logQuery(statement, map);

			tx.runAsync(statement, map)
				.thenCompose(cursor -> consumer.start(cursor))
				.thenCompose(cursor -> cursor.forEachAsync(consumer::add))
				.thenAccept(ignore  -> consumer.finish());

			return Iterables.map(new RecordRelationshipMapper(db), consumer);

		} catch (TransientException tex) {
			closed = true;
			throw new RetryException(tex);
		} catch (NoSuchRecordException nex) {
			throw new NotFoundException(nex);
		} catch (ServiceUnavailableException ex) {
			throw new NetworkException(ex.getMessage(), ex);
		} catch (DatabaseException dex) {
			throw SessionTransaction.translateDatabaseException(dex);
		} catch (ClientException cex) {
			throw SessionTransaction.translateClientException(cex);
		}
	}

	public Iterable<String> getStrings(final String statement, final Map<String, Object> map) {

		try {

			logQuery(statement, map);
			final StatementResult result = tx.run(statement, map);
			final Record record          = result.next();
			final Value value            = record.get(0);

			return new IteratorWrapper<>(value.asList(Values.ofString()).iterator());

		} catch (TransientException tex) {
			closed = true;
			throw new RetryException(tex);
		} catch (NoSuchRecordException nex) {
			throw new NotFoundException(nex);
		} catch (ServiceUnavailableException ex) {
			throw new NetworkException(ex.getMessage(), ex);
		} catch (DatabaseException dex) {
			throw SessionTransaction.translateDatabaseException(dex);
		} catch (ClientException cex) {
			throw SessionTransaction.translateClientException(cex);
		}
	}

	public Iterable<Map<String, Object>> run(final String statement, final Map<String, Object> map) {

		try {

			logQuery(statement, map);
			return Iterables.map(new RecordMapMapper(db), new IteratorWrapper<>(tx.run(statement, map)));

		} catch (TransientException tex) {
			closed = true;
			throw new RetryException(tex);
		} catch (NoSuchRecordException nex) {
			throw new NotFoundException(nex);
		} catch (ServiceUnavailableException ex) {
			throw new NetworkException(ex.getMessage(), ex);
		} catch (DatabaseException dex) {
			throw SessionTransaction.translateDatabaseException(dex);
		} catch (ClientException cex) {
			throw SessionTransaction.translateClientException(cex);
		}
	}

	public void set(final String statement, final Map<String, Object> map) {

		try {

			logQuery(statement, map);
			tx.run(statement, map).consume();

		} catch (TransientException tex) {
			closed = true;
			throw new RetryException(tex);
		} catch (NoSuchRecordException nex) {
			throw new NotFoundException(nex);
		} catch (ServiceUnavailableException ex) {
			throw new NetworkException(ex.getMessage(), ex);
		} catch (DatabaseException dex) {
			throw SessionTransaction.translateDatabaseException(dex);
		} catch (ClientException cex) {
			throw SessionTransaction.translateClientException(cex);
		}
	}

	public void logQuery(final String statement) {
		logQuery(statement, null);
	}

	public void logQuery(final String statement, final Map<String, Object> map) {

		if (db.logQueries()) {

			if (!isPing || db.logPingQueries()) {

				if (map != null && map.size() > 0) {

					if (statement.contains("extractedContent")) {
						System.out.println(Thread.currentThread().getId() + ": " + statement + "\t\t SET on extractedContent - value suppressed");
					} else {
						System.out.println(Thread.currentThread().getId() + ": " + statement + "\t\t Parameters: " + map.toString());
					}

				} else {

					System.out.println(Thread.currentThread().getId() + ": " + statement);
				}
			}
		}
	}

	public void deleted(final NodeWrapper wrapper) {
		deletedNodes.add(wrapper.getDatabaseId());
	}

	public void deleted(final RelationshipWrapper wrapper) {
		deletedRels.add(wrapper.getDatabaseId());
	}

	public boolean isDeleted(final EntityWrapper wrapper) {

		if (wrapper instanceof NodeWrapper) {
			return deletedNodes.contains(wrapper.getDatabaseId());
		}

		if (wrapper instanceof RelationshipWrapper) {
			return deletedRels.contains(wrapper.getDatabaseId());
		}

		return false;
	}

	public void modified(final EntityWrapper wrapper) {
		modifiedEntities.add(wrapper);
	}

	public void accessed(final EntityWrapper wrapper) {
		accessedEntities.add(wrapper);
	}

	public void setIsPing(final boolean isPing) {
		this.isPing = isPing;
	}

	@Override
	public long getTransactionId() {
		return this.transactionId;
	}

	// ----- public static methods -----
	public static RuntimeException translateClientException(final ClientException cex) {

		switch (cex.code()) {

			case "Neo.ClientError.Schema.ConstraintValidationFailed":
				throw new ConstraintViolationException(cex, cex.code(), cex.getMessage());

			// add handlers / translated exceptions for ClientExceptions here..
		}

		// wrap exception if no other cause could be found
		throw new UnknownClientException(cex, cex.code(), cex.getMessage());
	}

	public static RuntimeException translateDatabaseException(final DatabaseException dex) {

		switch (dex.code()) {

			case "Neo.DatabaseError.General.UnknownError":
				throw new DataFormatException(dex, dex.code(), dex.getMessage());

			// add handlers / translated exceptions for DatabaseExceptions here..
		}

		// wrap exception if no other cause could be found
		throw new UnknownDatabaseException(dex, dex.code(), dex.getMessage());
	}

	// ----- nested classes -----
	public class IteratorWrapper<T> implements Iterable<T> {

		private Iterator<T> iterator = null;

		public IteratorWrapper(final Iterator<T> iterator) {
			this.iterator = iterator;
		}

		@Override
		public Iterator<T> iterator() {
			return new CloseableIterator<>(iterator);
		}
	}

	public class CloseableIterator<T> implements Iterator<T>, AutoCloseable {

		private Iterator<T> iterator = null;

		public CloseableIterator(final Iterator<T> iterator) {
			this.iterator = iterator;
		}

		@Override
		public boolean hasNext() {

			try {
				return iterator.hasNext();

			} catch (ClientException dex) {
				throw SessionTransaction.translateClientException(dex);
			} catch (DatabaseException dex) {
				throw SessionTransaction.translateDatabaseException(dex);
			}
		}

		@Override
		public T next() {

			try {

				return iterator.next();

			} catch (ClientException dex) {
				throw SessionTransaction.translateClientException(dex);
			} catch (DatabaseException dex) {
				throw SessionTransaction.translateDatabaseException(dex);
			}
		}

		@Override
		public void close() throws Exception {

			if (iterator instanceof StatementResult) {

				((StatementResult)iterator).consume();
			}
		}
	}

	public class IterableQueueingConsumer<T> implements Iterable<T>, Iterator<T>, AutoCloseable {

		private final BlockingQueue<T> queue = new LinkedBlockingQueue<>();
		private final AtomicBoolean aborted  = new AtomicBoolean(false);
		private final AtomicBoolean finished = new AtomicBoolean(false);
		private final AtomicBoolean added    = new AtomicBoolean(false);
		private StatementResultCursor cursor = null;

		@Override
		public Iterator<T> iterator() {
			return this;
		}

		@Override
		public void close() {
			aborted.set(true);
			cursor.consumeAsync();
		}

		public void finish() {
			finished.set(true);
			added.set(true);
		}

		@Override
		public boolean hasNext() {

			// make the consuming thread wait for results util elements have been added OR the producer has no more results (finished == true)
			while (!added.get() || (!finished.get() && queue.isEmpty())) {
				try { Thread.yield(); } catch (Throwable t) {}
			}

			return !queue.isEmpty();
		}

		@Override
		public T next() {
			return queue.poll();
		}

		void add(final T t) {

			if (aborted.get()) {
				return;
			}

			queue.add(t);
			added.set(true);
		}

		CompletionStage<StatementResultCursor> start(final StatementResultCursor cursor) {
			this.cursor = cursor;
			return CompletableFuture.completedFuture(cursor);
		}
	}
}
