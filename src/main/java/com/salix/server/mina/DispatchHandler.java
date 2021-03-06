package com.salix.server.mina;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.session.IoSessionConfig;
import org.apache.mina.transport.socket.SocketSessionConfig;

import com.salix.core.message.Message;
import com.salix.core.message.ShutdownMessage;
import com.salix.core.ser.Deserializer;
import com.salix.core.ser.MyDeserializer;
import com.salix.core.ser.MySerializer;
import com.salix.core.ser.SerializeException;
import com.salix.core.ser.Serializer;
import com.salix.core.util.ThreadPool;
import com.salix.exception.ServerInternalException;
import com.salix.server.RpcServiceContext;
import com.salix.server.processor.IProcessor;

public class DispatchHandler extends IoHandlerAdapter {

	public static final Logger LOG = Logger.getLogger(DispatchHandler.class);

	private static AtomicBoolean isTobeShutdown = new AtomicBoolean(false);

	private static final BlockingQueue<Task> messageQ = new LinkedBlockingQueue<Task>();
	private static int threadNum = Runtime.getRuntime().availableProcessors() * 2;
	private static final ThreadPool threadPool = ThreadPool.newInstance("salix-main-threadpool", threadNum,
			threadNum * 5, 1000 * 5);

	private Serializer ser;
	private Deserializer deser;

	private RpcServiceContext rsc;

	public DispatchHandler(RpcServiceContext rsc) {
		this.ser = MySerializer.getInstance();
		this.deser = MyDeserializer.getInstance();
		this.rsc = rsc;

		new Thread() {
			public void run() {
				while (true) {
					Task task = null;
					try {
						task = messageQ.take();
					} catch (InterruptedException e) {
						LOG.warn(e.getMessage());
						continue;
					}
					threadPool.submit(new ProcessTask(task));
				}
			}
		}.start();
	}

	@Override
	public void sessionCreated(IoSession session) throws Exception {
		if (isTobeShutdown.get()) {
			session.close(true);
		}

		IoSessionConfig cfg = session.getConfig();
		if (cfg instanceof SocketSessionConfig) {
//			((SocketSessionConfig) cfg).setReceiveBufferSize(1024 * 1024);
			((SocketSessionConfig) cfg).setKeepAlive(true);
			((SocketSessionConfig) cfg).setSoLinger(0);
			((SocketSessionConfig) cfg).setTcpNoDelay(true);
		}
	}

	@Override
	public void messageReceived(IoSession session, Object message) throws Exception {
		byte[] pkg = (byte[]) message;
		Message in = deser.deser(pkg, Message.class);

		if (in instanceof ShutdownMessage) {
			isTobeShutdown.set(true);
			new Thread() {
				public void run() {
					while (true) {
						if (messageQ.isEmpty()) {
							rsc.destroy();
							System.exit(0);
						}

						try {
							Thread.sleep(500);
						} catch (InterruptedException e) {
						}
					}
				}
			}.start();
		}

		if (!isTobeShutdown.get()) {
			Task task = new Task();
			task.session = session;
			task.msg = in;
			messageQ.put(task);
		}
	}

	@Override
	public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
		if (!cause.getMessage().contains("Connection reset by peer")) {
			LOG.info(cause);
		}
	}

	private class ProcessTask implements Runnable {

		private IoSession session;
		private Message in;

		public ProcessTask(Task task) {
			this.session = task.session;
			this.in = task.msg;
		}

		public void run() {
			IProcessor processor = rsc.get(in.getClass());

			byte[] pkg = null;
			Message out = null;
			try {
				out = processor.process(in);
				pkg = ser.ser(out);
			} catch (Throwable e) {
				out = new Message();
				if (e instanceof InvocationTargetException) {
					out.setBody(((InvocationTargetException) e).getTargetException());
				} else {
					out.setBody(new ServerInternalException(e));
				}
				try {
					pkg = ser.ser(out);
				} catch (SerializeException e1) {
					LOG.error(e.getMessage(), e1);
				}
			}

			if (LOG.isDebugEnabled()) {
				LOG.debug("send message size " + pkg.length);
			}

			IoBuffer buf = IoBuffer.allocate(4 + pkg.length);
			buf.putInt(pkg.length).put(pkg);
			buf.flip();
			session.write(buf);
		}
	}

	private class Task {
		public IoSession session;
		public Message msg;
	}
}
