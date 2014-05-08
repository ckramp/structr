package org.structr.cloud;

import org.structr.cloud.message.Message;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

/**
 *
 * @author Christian Morgner
 */
public class Sender extends Thread {

	private final Queue<Message> outputQueue = new ArrayBlockingQueue<>(1000);
	private ObjectOutputStream outputStream  = null;
	private CloudConnection connection       = null;
	private Throwable errorMessage           = null;

	public Sender(final CloudConnection connection, final ObjectOutputStream outputStream) {

		this.outputStream = outputStream;
		this.connection   = connection;

		// flush stream to avoid ObjectInputStream to be waiting indefinitely
		try {

			outputStream.flush();

		} catch (IOException ioex) {
			ioex.printStackTrace();
		}
	}

	@Override
	public void run() {

		while (connection.isConnected()) {

			try {

				final Message message = outputQueue.poll();
				if (message != null) {

					if (CloudService.DEBUG) {
						System.out.println("Sender: " + message);
					}

					outputStream.writeObject(message);
					outputStream.flush();

					message.postProcess(connection, null);
				}

			} catch (Throwable t) {

				errorMessage = t;

				connection.shutdown();
			}
		}
	}

	public Throwable getErrorMessage() {
		return errorMessage;
	}

	public void send(final Message message) {
		outputQueue.add(message);
	}
}
