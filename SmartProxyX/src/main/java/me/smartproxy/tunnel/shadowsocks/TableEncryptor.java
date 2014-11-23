package me.smartproxy.tunnel.shadowsocks;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.security.MessageDigest;

import me.smartproxy.tunnel.IEncryptor;

public class TableEncryptor implements IEncryptor {

	private byte[] encryptTable = new byte[256];
	private byte[] decryptTable = new byte[256];
    
	public TableEncryptor(String password){
		
		 long a = passwordToInt64(password);
         for (int i = 0; i < 256; i++)
         {
             encryptTable[i] = (byte)i;
         }
         
         System.out.println("mergeSort....");
         long startTime=System.nanoTime();
         encryptTable = mergeSort(encryptTable, a);
         long endTime=System.nanoTime();
         System.out.printf("mergeSort: %3fms\n", (endTime-startTime)/1000D/1000D);
         
         for (int i = 0; i < 256; i++)
         {
             decryptTable[encryptTable[i]&0xFF] = (byte)i;
         }
	}
	
	long passwordToInt64(String password){
		try {
			byte[] passwordBytes=password.getBytes("UTF-8");
			MessageDigest md5 = MessageDigest.getInstance("MD5"); 
			byte[] hashPwd=md5.digest(passwordBytes);
			long a = bytesToInt64(hashPwd);
			return a;
		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
	}
 
	long bytesToInt64(byte[] data){
		long value=data[0];
		value|=((long)(data[1]&0xFF)<<8);
		value|=((long)(data[2]&0xFF)<<16);
		value|=((long)(data[3]&0xFF)<<24);
		value|=((long)(data[4]&0xFF)<<32);
		value|=((long)(data[5]&0xFF)<<40);
		value|=((long)(data[6]&0xFF)<<48);
		value|=((long)(data[7]&0xFF)<<56);
		return value;
	}
 
	 private  byte[] mergeSort(byte[] srcArray , long a ){
	   	  byte[] dstArray=new byte[256];
	   	  long a1=Long.MAX_VALUE;
		  long a2=(a&Long.MAX_VALUE);
	   	  int stepSize,leftOffset,leftMaxOffset,rightOffset,rightMaxOffset,dstOffset,leftValue,rightValue;
	   	  
	   	  for (int i = 1; i < 1024; i++) {
             for (stepSize = 1;stepSize < 256;stepSize<<=1){
                 for (dstOffset=0;dstOffset<256;){
	               	  leftOffset=dstOffset;
	               	  leftMaxOffset = leftOffset + stepSize;
	               	  rightOffset=leftMaxOffset;
	                  rightMaxOffset = rightOffset + stepSize;
	                  for (;dstOffset<rightMaxOffset; dstOffset++) {
	                	    if (rightOffset == rightMaxOffset) {
	                       	     dstArray[dstOffset] = srcArray[leftOffset++];
	                        }
	                        else if (leftOffset == leftMaxOffset){
	                       	     dstArray[dstOffset] = srcArray[rightOffset++];
	                        }else {
	                        	leftValue=(srcArray[leftOffset]&0xFF)+i;
		                	    rightValue=(srcArray[rightOffset]&0xFF)+i;
		                	    boolean isLeftSmallThanRight=a>0?((a%leftValue - a%rightValue) <=0):(((a1%leftValue+a2%leftValue+1)%leftValue - (a1%rightValue+a2%rightValue+1)%rightValue)<=0);
		                	    if(isLeftSmallThanRight){
		                	    	dstArray[dstOffset] =  srcArray[leftOffset++];
		                	    }else {
		                	    	dstArray[dstOffset] = srcArray[rightOffset++];
								}
							} 
	                  }   
	              }

                 byte[] temp = dstArray;
                 dstArray = srcArray;
                 srcArray = temp;   
             }
		  }
          return srcArray;
     }
 
	@Override
	public void encrypt(ByteBuffer buffer) {
		byte[] data=buffer.array();
		for (int i = buffer.arrayOffset()+buffer.position(); i < buffer.limit(); i++) {
			data[i]=encryptTable[data[i]&0xFF];
		}
	}

	@Override
	public void decrypt(ByteBuffer buffer) {
		byte[] data=buffer.array();
		for (int i = buffer.arrayOffset()+buffer.position(); i < buffer.limit(); i++) {
			data[i]=decryptTable[data[i]&0xFF];
		}
	}
 
}
