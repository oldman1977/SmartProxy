package me.smartproxy.tunnel;

import java.nio.ByteBuffer;

public interface IEncryptor {

	void encrypt(ByteBuffer buffer);
	void decrypt(ByteBuffer buffer);
	
}
