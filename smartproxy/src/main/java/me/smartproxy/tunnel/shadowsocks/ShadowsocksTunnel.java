package me.smartproxy.tunnel.shadowsocks;

import java.nio.ByteBuffer;
import java.nio.channels.Selector;

import me.smartproxy.tunnel.IEncryptor;
import me.smartproxy.tunnel.Tunnel;

public class ShadowsocksTunnel extends Tunnel {

	private IEncryptor m_Encryptor;
	private ShadowsocksConfig m_Config;
	private boolean m_TunnelEstablished;
	
	public ShadowsocksTunnel(ShadowsocksConfig config,Selector selector) throws Exception {
		super(config.ServerAddress,selector);
		if(config.Encryptor==null){
			throw new Exception("Error: The Encryptor for ShadowsocksTunnel is null.");
		}
		m_Config=config;
		m_Encryptor=config.Encryptor;
	}

	@Override
	protected void onConnected(ByteBuffer buffer) throws Exception {
		
		//构造socks5请求（跳过前3个字节）
		buffer.clear();
		buffer.put((byte)0x03);//domain
		byte[] domainBytes=m_DestAddress.getHostName().getBytes();
		buffer.put((byte)domainBytes.length);//domain length;
		buffer.put(domainBytes);
		buffer.putShort((short)m_DestAddress.getPort());
		buffer.flip();
		
		m_Encryptor.encrypt(buffer);
        if(write(buffer, true)){
        	m_TunnelEstablished=true;
        	onTunnelEstablished();
        }else {
        	m_TunnelEstablished=true;
			this.beginReceive();
		}
	}

	@Override
	protected boolean isTunnelEstablished() {
		return m_TunnelEstablished;
	}

	@Override
	protected void beforeSend(ByteBuffer buffer) throws Exception {
		 m_Encryptor.encrypt(buffer);
	}

	@Override
	protected void afterReceived(ByteBuffer buffer) throws Exception {
		m_Encryptor.decrypt(buffer);
	}

	@Override
	protected void onDispose() {
		 m_Config=null;
		 m_Encryptor=null;
	}

}
