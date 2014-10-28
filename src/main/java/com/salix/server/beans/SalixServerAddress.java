package com.salix.server.beans;

import java.io.Serializable;

public class SalixServerAddress implements Serializable {

	private static final long serialVersionUID = 1L;

	private String ip;

	private int port;

	public SalixServerAddress(String ip, int port) {
		this.ip = ip;
		this.port = port;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

}
