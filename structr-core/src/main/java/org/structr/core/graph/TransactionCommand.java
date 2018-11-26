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
package org.structr.core.graph;


import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.DatabaseService;
import org.structr.api.NetworkException;
import org.structr.api.NotInTransactionException;
import org.structr.api.graph.Node;
import org.structr.api.graph.Relationship;
import org.structr.common.SecurityContext;
import org.structr.common.error.DatabaseServiceNetworkException;
import org.structr.common.error.DatabaseServiceNotAvailableException;
import org.structr.common.error.ErrorBuffer;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.Services;
import org.structr.core.StructrTransactionListener;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.Principal;
import org.structr.core.property.PropertyKey;

/**
 * Graph service command for database operations that need to be wrapped in
 * a transaction.
 */
public class TransactionCommand {

	private static final Logger logger                             = LoggerFactory.getLogger(TransactionCommand.class.getName());
	private static final Set<StructrTransactionListener> listeners = new LinkedHashSet<>();
	private static final ThreadLocal<TransactionCommand> commands  = new ThreadLocal<>();
	private static final MultiSemaphore                  semaphore = new MultiSemaphore();

	private ModificationQueue queue          = null;
	private ErrorBuffer errorBuffer          = null;
	private TransactionReference transaction = null;


	private static TransactionCommand getInstance() {

		TransactionCommand cmd = commands.get();
		if (cmd == null) {

			cmd = new TransactionCommand();
		}

		return cmd;
	}

	public static TransactionCommand beginTx(final SecurityContext securityContext) throws FrameworkException {

		final TransactionCommand cmd  = TransactionCommand.getInstance();
		final DatabaseService graphDb = Services.getInstance().getDatabaseService();

		if (graphDb != null) {

			if (cmd.transaction == null) {

				try {
					// start new transaction
					cmd.transaction = new TransactionReference(graphDb.beginTx());

				} catch (NetworkException nex) {
					throw new DatabaseServiceNetworkException(503, nex.getMessage());
				}

				cmd.queue = new ModificationQueue();
				cmd.errorBuffer = new ErrorBuffer();

				commands.set(cmd);
			}

			// increase depth
			cmd.transaction.begin();

		} else {

			throw new DatabaseServiceNotAvailableException(503, "Database service is not available, ensure the database is running and that there is a working network connection to it");
		}

		return cmd;
	}

	public static void commitTx(final SecurityContext securityContext, final boolean doValidation) throws FrameworkException {

		final TransactionCommand cmd  = TransactionCommand.getInstance();

		if (cmd.transaction != null && cmd.transaction.isToplevel()) {

			final ModificationQueue modificationQueue = cmd.queue;
			final ErrorBuffer errorBuffer             = cmd.errorBuffer;

			// 0.5: let transaction listeners examine (and prevent?) commit
			for (final StructrTransactionListener listener : listeners) {
				listener.beforeCommit(securityContext, modificationQueue.getModificationEvents());
			}

			// 1. do inner callbacks (may cause transaction to fail)
			if (!modificationQueue.doInnerCallbacks(securityContext, errorBuffer)) {

				cmd.transaction.failure();
				throw new FrameworkException(422, "Unable to commit transaction, validation failed", errorBuffer);
			}

			// 2. fetch all types of entities modified in this tx
			Set<String> synchronizationKeys = modificationQueue.getSynchronizationKeys();

			// we need to protect the validation and indexing part of every transaction
			// from being entered multiple times in the presence of validators
			// 3. acquire semaphores for each modified type
			try { semaphore.acquire(synchronizationKeys); } catch (InterruptedException iex) { return; }

			// do validation under the protection of the semaphores for each type
				if (doValidation && !modificationQueue.doValidation(securityContext, errorBuffer, doValidation)) {

				cmd.transaction.failure();

				// create error
				throw new FrameworkException(422, "Unable to commit transaction, validation failed", errorBuffer);
			}

			// finally: execute validatable post-transaction action
			if (!modificationQueue.doPostProcessing(securityContext, errorBuffer)) {

				cmd.transaction.failure();
				throw new FrameworkException(422, "Unable to commit transaction, transaction post processing failed", errorBuffer);
			}

			try {
				cmd.transaction.success();

			} catch (Throwable t) {
				logger.error("Unable to commit transaction", t);
			}
		}
	}

	public static ModificationQueue finishTx() {

		final TransactionCommand cmd        = TransactionCommand.getInstance();
		ModificationQueue modificationQueue = null;

		if (cmd.transaction != null) {

			if (cmd.transaction.isToplevel()) {

				modificationQueue = cmd.queue;

				final Set<String> synchronizationKeys = modificationQueue.getSynchronizationKeys();

				// cleanup
				commands.remove();

				try {
					cmd.transaction.close();

				} finally {

					// release semaphores as the transaction is now finished
					semaphore.release(synchronizationKeys);	// careful: this can be null
				}

			} else {

				cmd.transaction.end();
			}
		}

		return modificationQueue;
	}

	// ----- static methods -----
	public static void postProcess(final String key, final TransactionPostProcess process) {

		TransactionCommand command = commands.get();
		if (command != null) {

			ModificationQueue modificationQueue = command.getModificationQueue();
			if (modificationQueue != null) {

				modificationQueue.postProcess(key, process);

			} else {

				logger.error("Got empty changeSet from command!");
			}

		} else {

			throw new NotInTransactionException("Not in transaction.");
		}

	}

	public static void nodeCreated(final Principal user, final NodeInterface node) {

		TransactionCommand command = commands.get();
		if (command != null) {

			assertSameTransaction(node, command.getTransactionId());

			ModificationQueue modificationQueue = command.getModificationQueue();
			if (modificationQueue != null) {

				modificationQueue.create(user, node);

			} else {

				logger.error("Got empty changeSet from command!");
			}

		} else {

			throw new NotInTransactionException("Not in transaction.");
		}
	}

	public static void nodeModified(final Principal user, final AbstractNode node, final PropertyKey key, final Object previousValue, final Object newValue) {

		TransactionCommand command = commands.get();
		if (command != null) {

			//assertSameTransaction(node, command.getTransactionId());

			ModificationQueue modificationQueue = command.getModificationQueue();
			if (modificationQueue != null) {

				modificationQueue.modify(user, node, key, previousValue, newValue);

			} else {

				logger.error("Got empty changeSet from command!");
			}

		} else {

			throw new NotInTransactionException("Not in transaction.");
		}
	}

	public static void nodeDeleted(final Principal user, final NodeInterface node) {

		TransactionCommand command = commands.get();
		if (command != null) {

			assertSameTransaction(node, command.getTransactionId());

			ModificationQueue modificationQueue = command.getModificationQueue();
			if (modificationQueue != null) {

				modificationQueue.delete(user, node);

			} else {

				logger.error("Got empty changeSet from command!");
			}

		} else {

			throw new NotInTransactionException("Not in transaction.");
		}
	}

	public static void relationshipCreated(final Principal user, final RelationshipInterface relationship) {

		TransactionCommand command = commands.get();
		if (command != null) {

			assertSameTransaction(relationship, command.getTransactionId());

			ModificationQueue modificationQueue = command.getModificationQueue();
			if (modificationQueue != null) {

				modificationQueue.create(user, relationship);

			} else {

				logger.error("Got empty changeSet from command!");
			}

		} else {

			throw new NotInTransactionException("Not in transaction.");
		}
	}

	public static void relationshipModified(final Principal user, final RelationshipInterface relationship, final PropertyKey key, final Object previousValue, final Object newValue) {

		TransactionCommand command = commands.get();
		if (command != null) {

			assertSameTransaction(relationship, command.getTransactionId());

			ModificationQueue modificationQueue = command.getModificationQueue();
			if (modificationQueue != null) {

				modificationQueue.modify(user, relationship, key, previousValue, newValue);

			} else {

				logger.error("Got empty changeSet from command!");
			}

		} else {

			throw new NotInTransactionException("Not in transaction.");
		}
	}

	public static void relationshipDeleted(final Principal user, final RelationshipInterface relationship, final boolean passive) {

		TransactionCommand command = commands.get();
		if (command != null) {

			assertSameTransaction(relationship, command.getTransactionId());

			ModificationQueue modificationQueue = command.getModificationQueue();
			if (modificationQueue != null) {

				modificationQueue.delete(user, relationship, passive);

			} else {

				logger.error("Got empty changeSet from command!");
			}

		} else {

			throw new NotInTransactionException("Not in transaction.");
		}
	}

	public static void registerTransactionListener(final StructrTransactionListener listener) {
		listeners.add(listener);
	}

	public static void removeTransactionListener(final StructrTransactionListener listener) {
		listeners.remove(listener);
	}

	public static Set<StructrTransactionListener> getTransactionListeners() {
		return listeners;
	}

	public static void simpleBroadcastGenericMessage (final Map<String, Object> data) {
		simpleBroadcast("GENERIC_MESSAGE", data, null);
	}

	public static void simpleBroadcastException (final Exception ex, final Map<String, Object> data, final boolean printStackTrace) {

		data.put("message", ex.getMessage());
		data.put("stringvalue", ex.toString());

		if (printStackTrace) {
			logger.warn("", ex);
		}

		simpleBroadcast("GENERIC_MESSAGE", data, null);
	}

	public static void simpleBroadcast (final String messageName, final Map<String, Object> data) {
		simpleBroadcast(messageName, data, null);
	}

	public static void simpleBroadcast (final String messageName, final Map<String, Object> data, final String exemptedSessionId) {

		try (final Tx tx = StructrApp.getInstance().tx()) {

			for (final StructrTransactionListener listener : TransactionCommand.getTransactionListeners()) {
				listener.simpleBroadcast(messageName, data, exemptedSessionId);
			}

			tx.success();

		} catch (FrameworkException fex) {
			logger.warn("Exception during simple broadcast", fex);
		}
	}

	public static boolean inTransaction() {
		return commands.get() != null;
	}

	public static long getCurrentTransactionId() {

		final TransactionCommand cmd = commands.get();
		if (cmd != null) {

			return cmd.getTransactionId();
		}

		throw new NotInTransactionException("Not in transaction.");
	}

	public static boolean isDeleted(final Node node) {

		TransactionCommand cmd = commands.get();
		if (cmd != null) {

			return cmd.queue.isDeleted(node);
		}

		throw new NotInTransactionException("Not in transaction.");
	}

	public static boolean isDeleted(final Relationship rel) {


		TransactionCommand cmd = commands.get();
		if (cmd != null) {

			return cmd.queue.isDeleted(rel);

		} else {

			throw new NotInTransactionException("Not in transaction.");
		}
	}

	public static void registerNodeCallback(final NodeInterface node, final String callbackId) {

		TransactionCommand command = commands.get();
		if (command != null) {

			ModificationQueue modificationQueue = command.getModificationQueue();
			if (modificationQueue != null) {

				modificationQueue.registerNodeCallback(node, callbackId);

			} else {

				logger.error("Got empty changeSet from command!");
			}

		} else {

			logger.error("Unable to register node callback");
		}

	}

	public static void registerRelCallback(final RelationshipInterface rel, final String callbackId) {

		TransactionCommand command = commands.get();
		if (command != null) {

			ModificationQueue modificationQueue = command.getModificationQueue();
			if (modificationQueue != null) {

				modificationQueue.registerRelCallback(rel, callbackId);

			} else {

				logger.error("Got empty changeSet from command!");
			}

		} else {

			logger.error("Unable to register relationship callback");
		}

	}

	private static void assertSameTransaction(final GraphObject obj, final long currentTransactionId) {

		final long nodeTransactionId = obj.getSourceTransactionId();
		if (currentTransactionId != nodeTransactionId) {

			logger.warn("Possible leaking {} instance detected: created in transaction {}, modified in {}", obj.getClass().getSimpleName(), nodeTransactionId, currentTransactionId);
			Thread.dumpStack();
		}
	}

	// ----- private methods -----
	private ModificationQueue getModificationQueue() {
		return queue;
	}

	private long getTransactionId() {
		return transaction.getTransactionId();
	}
}
