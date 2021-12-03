package net.guerra24.mcproxy;

import java.util.ArrayList;

public class Settings {
	private String versionName;
	private int maxPlayers;
	private int onlinePlayers;
	private String rconPassword;
	private int rconPort;
	private String motd;
	private int sleepTime;
	private int port;
	private int remotePort;
	private String instance;
	private String accessKey;
	private String secretKey;

	private ArrayList<String> whitelist;

	public String getVersionName() {
		return versionName;
	}

	public void setVersionName(String versionName) {
		this.versionName = versionName;
	}

	public int getMaxPlayers() {
		return maxPlayers;
	}

	public void setMaxPlayers(int maxPlayers) {
		this.maxPlayers = maxPlayers;
	}

	public int getOnlinePlayers() {
		return onlinePlayers;
	}

	public void setOnlinePlayers(int onlinePlayers) {
		this.onlinePlayers = onlinePlayers;
	}

	public String getRconPassword() {
		return rconPassword;
	}

	public void setRconPassword(String rconPassword) {
		this.rconPassword = rconPassword;
	}

	public int getRconPort() {
		return rconPort;
	}

	public void setRconPort(int rconPort) {
		this.rconPort = rconPort;
	}

	public String getMotd() {
		return motd;
	}

	public void setMotd(String motd) {
		this.motd = motd;
	}

	public int getSleepTime() {
		return sleepTime;
	}

	public void setSleepTime(int sleepTime) {
		this.sleepTime = sleepTime;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public int getRemotePort() {
		return remotePort;
	}

	public void setRemotePort(int remotePort) {
		this.remotePort = remotePort;
	}

	public String getInstance() {
		return instance;
	}

	public void setInstance(String instance) {
		this.instance = instance;
	}

	public String getAccessKey() {
		return accessKey;
	}

	public void setAccessKey(String accessKey) {
		this.accessKey = accessKey;
	}

	public String getSecretKey() {
		return secretKey;
	}

	public void setSecretKey(String secretKey) {
		this.secretKey = secretKey;
	}

	public void setWhitelist(ArrayList<String> whitelist) {
		this.whitelist = whitelist;
	}

	public ArrayList<String> getWhitelist() {
		return whitelist;
	}

}
