package se.uu.it.dtlsfuzzer.sut;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.learnlib.api.SUL;
import de.learnlib.api.exception.SULException;
import se.uu.it.dtlsfuzzer.CleanupTasks;
import se.uu.it.dtlsfuzzer.config.SulDelegate;

/**
 * The role of the resetting wrapper is to communicate with a SUL wrapper over a
 * TCP connection. The resetting wrapper sends "reset" commands to the SUL
 * wrapper. The SUL wrapper reacts by terminating the current SUL instance,
 * starting a new one and responding with the fresh port number the instance
 * listens to. This response is also used as a form of acknowledgement, telling
 * the learning setup that the new instance is ready to receive messages.
 * 
 * Setting the port dynamically (rather than binding it statically) proved
 * necessary in order to avoid port collisions.
 */
public class ResettingWrapper<I, O> implements SUL<I, O>, DynamicPortProvider {

	private static final Logger LOGGER = LogManager
			.getLogger(ResettingWrapper.class);

	private static final String RESET_CMD = "reset";

	private SUL<I, O> sul;

	private Socket resetSocket;
	private InetSocketAddress resetAddress;
	private long resetCommandWait;
	private Integer dynamicPort;
	private BufferedReader reader;
	private PrintWriter writer;

	public ResettingWrapper(SUL<I, O> sul, SulDelegate sulDelegate,
			CleanupTasks tasks) {
		this.sul = sul;
		resetAddress = new InetSocketAddress(sulDelegate.getResetAddress(),
				sulDelegate.getResetPort());
		resetCommandWait = sulDelegate.getResetCommandWait();
		try {
			resetSocket = new Socket();
			resetSocket.setReuseAddress(true);
			resetSocket.setSoTimeout(30000);
			tasks.submit(new Runnable() {
				@Override
				public void run() {
					try {
						if (!resetSocket.isClosed()) {
							LOGGER.debug("Closing socket");
							resetSocket.close();
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			});
		} catch (SocketException e) {
			throw new RuntimeException(e);
		}
	}

	public Integer getSulPort() {
		return dynamicPort;
	}

	@Override
	public void pre() {
		try {
			if (!resetSocket.isConnected()) {
				resetSocket.connect(resetAddress);
				reader = new BufferedReader(new InputStreamReader(
						resetSocket.getInputStream()));
				writer = new PrintWriter(new OutputStreamWriter(resetSocket.getOutputStream()));
			}
			writer.println(RESET_CMD);
			writer.flush();

			String portString = reader.readLine();
			if (portString == null) {
				throw new RuntimeException("Server has closed the socket");
			}
			
			dynamicPort = Integer.valueOf(portString);
			if (resetCommandWait > 0) {
				Thread.sleep(resetCommandWait);
			}
			
			LOGGER.debug("Server listening at port {}", portString);

			/*
			 * We have to pre before the SUT does, so we have a port available
			 * for it.
			 */
			sul.pre();
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void post() {
		sul.post();
	}

	@Override
	public O step(I in) throws SULException {
		return sul.step(in);
	}
}
