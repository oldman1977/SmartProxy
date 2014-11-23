package me.smartproxy.dns;

import java.nio.ByteBuffer;

public class Question {
	 public String Domain;
     public short Type;
     public short Class;
     
     private int offset;
     public int Offset() {
		return offset;
	 }
     
     private int length;
     public int Length() {
		return length;
	 }
     
     public static Question FromBytes(ByteBuffer buffer){
         Question q=new Question();
         q.offset=buffer.arrayOffset()+buffer.position();
         q.Domain = DnsPacket.ReadDomain(buffer,buffer.arrayOffset());
         q.Type=buffer.getShort();
         q.Class=buffer.getShort();
         q.length=buffer.arrayOffset()+buffer.position()-q.offset;
         return q;
     }
     
     public void ToBytes(ByteBuffer buffer)
     {
    	 this.offset=buffer.position();
         DnsPacket.WriteDomain(this.Domain, buffer);
         buffer.putShort(this.Type);
         buffer.putShort(this.Class);
         this.length=buffer.position()-this.offset;
     }
}
