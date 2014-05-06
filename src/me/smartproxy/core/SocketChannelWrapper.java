package me.smartproxy.core;

import android.annotation.SuppressLint;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class SocketChannelWrapper {

	final static ByteBuffer GL_BUFFER=ByteBuffer.allocate(10000);
	public static long SessionCount;
 
	SocketChannel m_InnerChannel;
	ByteBuffer m_SendRemainBuffer;
	Selector m_Selector;
	SocketChannelWrapper m_BrotherChannelWrapper;
	InetSocketAddress m_TargetSocketAddress;
	boolean m_Disposed;
	boolean m_UseProxy;
	boolean m_TunnelEstablished;
	String m_ID;
 
	public SocketChannelWrapper(SocketChannel innerChannel,Selector selector){
		this.m_InnerChannel=innerChannel;
		this.m_Selector=selector;
		SessionCount++;
	}
	
	public String toString(){
		return m_ID;
	}
	
	public static SocketChannelWrapper createNew(Selector selector) throws Exception{
		SocketChannel innerChannel=null;
		try {
			innerChannel=SocketChannel.open();
			innerChannel.configureBlocking(false);
			return new SocketChannelWrapper(innerChannel, selector);
		} catch (Exception e) {
			if(innerChannel!=null){
				innerChannel.close();
			}
			throw e;
		}
	}
	
	public void setBrotherChannelWrapper(SocketChannelWrapper brotherChannelWrapper){
		m_BrotherChannelWrapper=brotherChannelWrapper;
	}
	
	@SuppressLint("DefaultLocale")
	public void connect(InetSocketAddress targetSocketAddress,InetSocketAddress proxySocketAddress) throws Exception{
		if(LocalVpnService.Instance.protect(m_InnerChannel.socket())){//保护socket不走vpn
			m_TargetSocketAddress=targetSocketAddress;
			String id=String.format("%d%s", m_BrotherChannelWrapper.m_InnerChannel.socket().getPort(),targetSocketAddress);
			m_ID="[R]"+id;
			m_BrotherChannelWrapper.m_ID="[L]"+id;
			
			m_InnerChannel.register(m_Selector, SelectionKey.OP_CONNECT,this);//注册连接事件
			if(proxySocketAddress!=null){
				m_UseProxy=true;
				m_BrotherChannelWrapper.m_UseProxy=true;
				m_InnerChannel.connect(proxySocketAddress);//连接代理
			}else {
				m_UseProxy=false;
				m_BrotherChannelWrapper.m_UseProxy=false;
				m_InnerChannel.connect(targetSocketAddress);//直连
			}
		}else {
			throw new Exception("VPN protect socket failed.");
		}
	}
  
	void registerReadOperation() throws Exception{
		if(m_InnerChannel.isBlocking()){
			m_InnerChannel.configureBlocking(false);
		}
		m_InnerChannel.register(m_Selector, SelectionKey.OP_READ,this);//注册读事件
	}
	
	void trySendPartOfHeader(ByteBuffer buffer)  throws Exception {
		int bytesSent=0;
		if(m_UseProxy&&m_TunnelEstablished&& m_TargetSocketAddress!=null&&m_TargetSocketAddress.getPort()!=443&&buffer.remaining()>10){
			int pos=buffer.position()+buffer.arrayOffset();
    		String firString=new String(buffer.array(),pos,10).toUpperCase();
    		if(firString.startsWith("GET /") || firString.startsWith("POST /")){
    			int limit=buffer.limit();
    			buffer.limit(buffer.position()+10);
    			bytesSent=m_InnerChannel.write(buffer);
    			buffer.limit(limit);
    			if(ProxyConfig.IS_DEBUG)
    				System.out.printf("Send %d bytes(%s) to %s\n",bytesSent,firString,m_TargetSocketAddress);
    		}
		}
	}
	
    boolean write(ByteBuffer buffer,boolean copyRemainData) throws Exception {
    	
    	if(ProxyConfig.Instance.isIsolateHttpHostHeader()){
    		trySendPartOfHeader(buffer);//尝试发送请求头的一部分，让请求头的host在第二个包里面发送，从而绕过机房的白名单机制。
    	}
    	
    	int bytesSent;
    	while (buffer.hasRemaining()) {
			bytesSent=m_InnerChannel.write(buffer);
			if(bytesSent==0){
				break;//不能再发送了，终止循环
			}
		}
    	
    	if(buffer.hasRemaining()){//数据没有发送完毕
    		if(copyRemainData){//拷贝剩余数据，然后侦听写入事件，待可写入时写入。
    			if(m_SendRemainBuffer==null){
    				m_SendRemainBuffer=ByteBuffer.allocate(buffer.capacity());
    			}
    			
    			//拷贝剩余数据
        		m_SendRemainBuffer.clear();
        		m_SendRemainBuffer.put(buffer);
    			m_SendRemainBuffer.flip();
    			
    			m_InnerChannel.register(m_Selector,SelectionKey.OP_WRITE, this);//注册写事件
    		}
			return false;
    	}
    	else {//发送完毕了
    		return true;
		}
	}
 
    void onTunnelEstablished() throws Exception{
    	m_TunnelEstablished=true;
		m_BrotherChannelWrapper.m_TunnelEstablished=true;
		this.registerReadOperation();//开始接收数据
		m_BrotherChannelWrapper.registerReadOperation();//兄弟也开始收数据吧
    }
    
    @SuppressLint("DefaultLocale")
	public void onConnected(){
    	try {
    		ByteBuffer buffer=GL_BUFFER;
        	if(m_InnerChannel.finishConnect()){//连接成功
        		if(m_UseProxy){//使用代理
        			String request = String.format("CONNECT %s:%d HTTP/1.0\r\nProxy-Connection: keep-alive\r\nUser-Agent: %s\r\nX-App-Install-ID: %s\r\n\r\n", 
        					m_TargetSocketAddress.getHostName(),
        					m_TargetSocketAddress.getPort(),
        					ProxyConfig.Instance.getUserAgent(),
        					ProxyConfig.AppInstallID);
        			
        			buffer.clear();
        			buffer.put(request.getBytes());
        			buffer.flip();
        			if(this.write(buffer,true)){//发送连接请求到代理服务器
        				this.registerReadOperation();//开始接收代理服务器响应数据
        			}
        		}else {//直连
        			onTunnelEstablished();//开始接收数据
				}
        	}else {//连接失败
        		System.out.printf("%s connect failed.\n", m_ID);
        		LocalVpnService.Instance.writeLog("Error: connect to %s failed.", m_UseProxy?"proxy":"server");
				this.dispose();
			}
		} catch (Exception e) {
			System.out.printf("%s connect error: %s.\n", m_ID,e);
			LocalVpnService.Instance.writeLog("Error: connect to %s failed: %s", m_UseProxy?"proxy":"server",e);
			this.dispose();
		}
    }
    
	public void onRead(SelectionKey key){
		try {
			ByteBuffer buffer=GL_BUFFER;
			buffer.clear();
			int bytesRead=m_InnerChannel.read(buffer);
			if(bytesRead>0){
				if(m_TunnelEstablished){
					//将读到的数据，转发给兄弟。
					buffer.flip();
					if(!m_BrotherChannelWrapper.write(buffer,true)){
						key.cancel();//兄弟吃不消，就取消读取事件。
						if(ProxyConfig.IS_DEBUG)
							System.out.printf("%s can not read more.\n", m_ID);
					}
				}else {
					//这里忽略检查
					//代理隧道已建立
					onTunnelEstablished();//开始接收数据
				}
			}else if(bytesRead<0) {
				this.dispose();//连接已关闭，释放资源。
			}
		} catch (Exception e) {
			this.dispose();
		}
	}

	public void onWrite(SelectionKey key){
		try {
			if(this.write(m_SendRemainBuffer, false)) {//如果剩余数据已经发送完毕
				key.cancel();//取消写事件。
				if(m_TunnelEstablished){
					m_BrotherChannelWrapper.registerReadOperation();//这边数据发送完毕，通知兄弟可以收数据了。
				}else {
					this.registerReadOperation();//开始接收代理服务器响应数据
				}
			}
		} catch (Exception e) {
			this.dispose();
		}
	}
	
	public void dispose(){
		disposeInternal(true);
	}
	
	void disposeInternal(boolean disposeBrother) {
		if(m_Disposed){
			return;
		}
		else {
			try {
				m_InnerChannel.close();
			} catch (Exception e) {
			}
			
			if(m_BrotherChannelWrapper!=null&&disposeBrother){
				m_BrotherChannelWrapper.disposeInternal(false);//把兄弟的资源也释放了。
			}

			m_InnerChannel=null;
		    m_SendRemainBuffer=null;
			m_Selector=null;
			m_BrotherChannelWrapper=null;
			m_TargetSocketAddress=null;
			m_Disposed=true;
			SessionCount--;
		}
	}
}
