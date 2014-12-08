package me.smartproxy.core;

import me.smartproxy.tcpip.CommonMethods;
import android.util.SparseArray;
 
public class NatSessionManager {

	static final int MAX_SESSION_COUNT=60;
	static final long SESSION_TIMEOUT_NS=60*1000000000L;
	static final SparseArray<NatSession> Sessions = new SparseArray<NatSession>();
	 
	public static NatSession getSession(int portKey){
		return Sessions.get(portKey);
	}
	
	public static  int getSessionCount() {
		return Sessions.size();
	}
	
    static void clearExpiredSessions(){
    	long now=System.nanoTime();
    	for (int i = Sessions.size()-1; i>=0; i--) {
			NatSession session=Sessions.valueAt(i);
			if(now- session.LastNanoTime>SESSION_TIMEOUT_NS){
				Sessions.removeAt(i);
			}
		}
    }
    
	public static NatSession createSession(int portKey,int remoteIP,short remotePort){
		if(Sessions.size()>MAX_SESSION_COUNT){
			clearExpiredSessions();//清理过期的会话。
		}
 
		NatSession session=new NatSession();
		session.LastNanoTime=System.nanoTime();
		session.RemoteIP=remoteIP;
		session.RemotePort=remotePort;
		
		if(ProxyConfig.isFakeIP(remoteIP)){
			session.RemoteHost=DnsProxy.reverseLookup(remoteIP);
		}
		
		if(session.RemoteHost==null){
			session.RemoteHost=CommonMethods.ipIntToString(remoteIP);
		}
		Sessions.put(portKey, session);
		return session;
	}
}
