package me.smartproxy.tunnel;

import java.net.InetSocketAddress;
import java.nio.channels.Selector;

public abstract class Config {
	public InetSocketAddress ServerAddress;
	public IEncryptor Encryptor;
}
