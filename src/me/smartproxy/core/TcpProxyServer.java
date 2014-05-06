package me.smartproxy.core;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

import me.smartproxy.tcpip.CommonMethods;


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
							    	((SocketChannelWrapper)key.attachment()).onRead(key);
								}
							    else if(key.isWritable()){
							    	((SocketChannelWrapper)key.attachment()).onWrite(key);
							    }
							    else if (key.isConnectable()) {
							    	((SocketChannelWrapper)key.attachment()).onConnected();
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

	InetSocketAddress getTargetSocketAddress(SocketChannel localChannel){
		short portKey=(short)localChannel.socket().getPort();
		NatSession session =NatSessionManager.getSession(portKey);
		if (session != null) {
			if(ProxyConfig.Instance.needProxy(session.RemoteHost, session.RemoteIP)){
				if(ProxyConfig.IS_DEBUG)
					System.out.printf("%d/%d:[PROXY] %s=>%s:%d\n",NatSessionManager.getSessionCount(), SocketChannelWrapper.SessionCount,session.RemoteHost,CommonMethods.ipIntToString(session.RemoteIP),session.RemotePort&0xFFFF);
				return InetSocketAddress.createUnresolved(session.RemoteHost, session.RemotePort&0xFFFF);
			}else {
				if(ProxyConfig.IS_DEBUG)
					System.out.printf("%d/%d:[DIRECT] %s=>%s:%d\n",NatSessionManager.getSessionCount(),SocketChannelWrapper.SessionCount, session.RemoteHost,CommonMethods.ipIntToString(session.RemoteIP),session.RemotePort&0xFFFF);
				return new InetSocketAddress(localChannel.socket().getInetAddress(),session.RemotePort&0xFFFF);
			}
		}
		return null;
	}
	
	void onAccepted(SelectionKey key){
		SocketChannelWrapper localBrotherChannelWrapper =null;
		try {
			SocketChannel localChannel=m_ServerSocketChannel.accept();
			localBrotherChannelWrapper=new SocketChannelWrapper(localChannel, m_Selector);
			
			InetSocketAddress targetAddress= getTargetSocketAddress(localChannel);
			if(targetAddress!=null){
				SocketChannelWrapper  remoteBrotherChannelWrapper=SocketChannelWrapper.createNew(m_Selector);
				remoteBrotherChannelWrapper.setBrotherChannelWrapper(localBrotherChannelWrapper);//关联兄弟
				localBrotherChannelWrapper.setBrotherChannelWrapper(remoteBrotherChannelWrapper);//关联兄弟
				
				InetSocketAddress proxySocketAddress=null;
				if(targetAddress.isUnresolved()){
					proxySocketAddress=ProxyConfig.Instance.getDefaultProxy();
				}
				
				remoteBrotherChannelWrapper.connect(targetAddress, proxySocketAddress);//开始连接
			}
			else {
				LocalVpnService.Instance.writeLog("Error: socket(%s:%d) target host is null.",localChannel.socket().getInetAddress().toString(),localChannel.socket().getPort());
				localBrotherChannelWrapper.dispose();
			}
		} catch (Exception e) {
			e.printStackTrace();
			LocalVpnService.Instance.writeLog("Error: remote socket create failed: %s",e.toString());
			if(localBrotherChannelWrapper!=null){
				localBrotherChannelWrapper.dispose();
			}
		}
	}
 
}
