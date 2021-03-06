package com.salix.client.connection;

import java.io.IOException;
import java.util.concurrent.Semaphore;

import org.apache.log4j.Logger;

import com.salix.core.message.Message;

/**
 * 连接池中的连接器.
 * 
 * @author duanbn
 * @since 1.1
 */
public class CpConnection extends AbstractConnection {
	public static final Logger LOG = Logger.getLogger(CpConnection.class);

	/**
	 * 当前连接是否被激活
	 */
	private boolean active;

	private Semaphore se;

	private ConnectionPool cp;

	public CpConnection(String host, int port) throws IOException {
		super(host, port);
	}

	/**
	 * 将此连接设置为活动. 表示此连接正在被使用
	 */
	public void setActive(Semaphore se) {
		this.active = true;
		this.se = se;
	}

	/**
	 * 判断此连接是否是活动的.
	 */
	public boolean isActive() {
		return this.active;
	}

	@Override
	public Message send(Message message) throws IOException {
		if (!isActive()) {
			throw new IllegalStateException("连接处于非活动状态, 请重新从连接池中获取连接");
		}

		return super.send(message);
	}

	/**
	 * 逻辑关闭连接. 关闭连接并将状态设置为非活动.从连接池中获取此连接的时候 使用逻辑关闭
	 */
	public void close() {
		this.active = false;
		if (this.se != null)
			this.se.release();
	}

	/**
	 * 物理关闭此连接关联的通道. 当直接使用此连接的时候需物理关闭连接.
	 */
	public void closeChannel() {
		try {
			if (isOpen()) {
				this.channel.close();

				this.close();

				this.cp.removeConnection(this.getLocalAddress());

				LOG.info(this.getLocalAddress() + " disconnection");
			}
		} catch (IOException e) {
			LOG.warn("关闭通道失败");
		}
	}

	@Override
	public String toString() {
		StringBuilder info = new StringBuilder();
		info.append("host=").append(super.getLocalAddress());
		info.append(", port=").append(super.getLocalPort());
		return info.toString();
	}

	public ConnectionPool getCp() {
		return cp;
	}

	public void setCp(ConnectionPool cp) {
		this.cp = cp;
	}
}
