package me.smartproxy.core;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

import me.smartproxy.tcpip.CommonMethods;
import me.smartproxy.tunnel.Config;
import me.smartproxy.tunnel.RawTunnel;
import me.smartproxy.tunnel.Tunnel;
import me.smartproxy.tunnel.httpconnect.HttpConnectConfig;
import me.smartproxy.tunnel.shadowsocks.ShadowsocksConfig;

public class TcpProxyServer implements Runnable {

	public boolean Stopped;
	public short Port;

	Selector m_Selector;
	ServerSocketChannel m_ServerSocketChannel;
	Thread m_ServerThread;
 
	public TcpProxyServer(int port) throws IOException {
		m_Selector = Selector.open();
		m_ServerSocketChannel = ServerSocketChannel.open();
		m_ServerSocketChannel.configureBlocking(false);
		m_ServerSocketChannel.socket().bind(new InetSocketAddress(port));
		m_ServerSocketChannel.register(m_Selector, SelectionKey.OP_ACCEPT);
		this.Port=(short) m_ServerSocketChannel.socket().getLocalPort();
		System.out.printf("AsyncTcpServer listen on %d success.\n", this.Port&0xFFFF);
	}
	
	public void start(){
		m_ServerThread=new Thread(this);
		m_ServerThread.setName("TcpProxyServerThread");
		m_ServerThread.start();
	}
	
	public void stop(){
		this.Stopped=true;
		if(m_Selector!=null){
			try {
				m_Selector.close();
				m_Selector=null;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
			
		if(m_ServerSocketChannel!=null){
			try {
				m_ServerSocketChannel.close();
				m_ServerSocketChannel=null;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	@Override
	public void run() {
		try {
			while (true) {
				m_Selector.select();
				Iterator<SelectionKey> keyIterator = m_Selector.selectedKeys().iterator();
				while (keyIterator.hasNext()) {
					SelectionKey key = keyIterator.next();
					if (key.isValid()) {
						try {
							    if (key.isReadable()) {
							    	((Tunnel)key.attachment()).onReadable(key);
								}
							    else if(key.isWritable()){
							    	((Tunnel)key.attachment()).onWritable(key);
							    }
							    else if (key.isConnectable()) {
							    	((Tunnel)key.attachment()).onConnectable();
								}
							    else  if (key.isAcceptable()) {
									onAccepted(key);
								}
						} catch (Exception e) {
							System.out.println(e.toString());
						}
					}
					keyIterator.remove();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}finally{
			this.stop();
			System.out.println("TcpServer thread exited.");
		}
	}

	InetSocketAddress getDestAddress(SocketChannel localChannel){
		short portKey=(short)localChannel.socket().getPort();
		NatSession session =NatSessionManager.getSession(portKey);
		if (session != null) {
			if(ProxyConfig.Instance.needProxy(session.RemoteHost, session.RemoteIP)){
				if(ProxyConfig.IS_DEBUG)
					System.out.printf("%d/%d:[PROXY] %s=>%s:%d\n",NatSessionManager.getSessionCount(), Tunnel.SessionCount,session.RemoteHost,CommonMethods.ipIntToString(session.RemoteIP),session.RemotePort&0xFFFF);
				return InetSocketAddress.createUnresolved(session.RemoteHost, session.RemotePort&0xFFFF);
			}else {
			    return new InetSocketAddress(localChannel.socket().getInetAddress(),session.RemotePort&0xFFFF);
			}
		}
		return null;
	}
	
	void onAccepted(SelectionKey key){
		Tunnel localTunnel =null;
		try {
			SocketChannel localChannel=m_ServerSocketChannel.accept();
			localTunnel=TunnelFactory.wrap(localChannel, m_Selector);

			InetSocketAddress destAddress=getDestAddress(localChannel);
			if(destAddress!=null){
				Tunnel remoteTunnel=TunnelFactory.createTunnelByConfig(destAddress,m_Selector);
				remoteTunnel.setBrotherTunnel(localTunnel);//关联兄弟
				localTunnel.setBrotherTunnel(remoteTunnel);//关联兄弟
				remoteTunnel.connect(destAddress);//开始连接
			}
			else {
				LocalVpnService.Instance.writeLog("Error: socket(%s:%d) target host is null.",localChannel.socket().getInetAddress().toString(),localChannel.socket().getPort());
				localTunnel.dispose();
			}
		} catch (Exception e) {
			e.printStackTrace();
			LocalVpnService.Instance.writeLog("Error: remote socket create failed: %s",e.toString());
			if(localTunnel!=null){
				localTunnel.dispose();
			}
		}
	}
 
}
