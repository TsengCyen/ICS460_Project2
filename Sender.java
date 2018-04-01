import java.io.*;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import javax.swing.JFileChooser;

public class Sender {

    private final static int PORT = 13;

    public static void main(String[] args) {
    	//File in = new File(args[0]);
    	
    	ByteBuffer headerBB;
    	File in=null;
		double bad = 0.20;
		byte ackerr;
		int ackno;
		String ackStatus ="";

    	//filechooser as substitue for cmd while developing
    	JFileChooser jfc = new JFileChooser(System.getProperty("user.dir"));
		int returnValue = jfc.showSaveDialog(null);
		if (returnValue == JFileChooser.APPROVE_OPTION) {
			in = jfc.getSelectedFile();
		}

		long inLength = in.length();
		
        try (DatagramSocket socket = new DatagramSocket(14)) {
        	ByteArrayOutputStream baos = new ByteArrayOutputStream();
        	DataOutputStream dos = new DataOutputStream(baos);

        	byte[] data;
        	byte[] img = Files.readAllBytes(in.toPath());
        	byte[] buf = new byte[1040];
            InetAddress host = InetAddress.getByName("localhost");
            int offset = 0;
            int packetLength = (int) Math.ceil(inLength/12.0);
            DatagramPacket request;
            
            //for images of smaller size, split them into 12 packets still, packet size = img size/12 + header
            if (inLength < 1024*12) {
            	for (int counter=1;counter<=12;counter++)
            	{
            		buf=null;
            		offset = packetLength*(counter-1);
            		
            		//get data from image file, if last packet then just get remaining data instead of full packet
            		if(inLength <= offset + packetLength - 1)
            			data = Arrays.copyOfRange(img, offset, (int) inLength);
            		else
            			data = Arrays.copyOfRange(img, offset, offset+packetLength);
            		
            		//add header for packet
            		dos.writeInt(counter);
            		dos.writeInt(offset);
            		dos.writeLong(inLength);
            		dos.write(data);
            		buf = baos.toByteArray();
            		
            		
            		System.out.println(counter + "-" + offset+ "-" +(offset+data.length-1));
            		
            		//create packet and send request
            		if(inLength <= offset + packetLength - 1)
                		request = new DatagramPacket(buf, (int) inLength-offset+16,host, PORT);
            		else
            			request = new DatagramPacket(buf, packetLength+16,host, PORT);
            		       
            		long time = System.nanoTime();
            		socket.send(request);
            		//logging message for smaller files
            		System.out.println("SENDing " + counter +  " " + offset + ":" + (offset+data.length-1) + " " 
            		+ TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - time) + " " + dropCheck(bad));
            		
            		socket.setSoTimeout(2000);

            		//receiving ack and resending if drop
            		while(true) {
            			try{
            				DatagramPacket ack = new DatagramPacket(new byte[5], 5);
	            			socket.receive(ack);
	            			
	            			//parsing ack
	                        headerBB = ByteBuffer.wrap(ack.getData());
	                        ackerr = headerBB.get();
	                        ackno = headerBB.getInt();
	                        
	                        if (ackerr == 1) {
	                        	ackStatus = "ErrAck";
	                        }
	                        else if(ackno != counter) {
	                        	ackStatus = "DuplAck";
	                        }
	                        else {
	                        	ackStatus = "MoveWnd";
	                        }
	                        
	                        //logging ack
	            			System.out.println("AckRcvd " + ackno + " " + ackStatus);
            				if(ackStatus.equals("MoveWnd")){
            					break;
            				}
            			}
            			//logging timeout
            			catch (SocketTimeoutException e) {
            				System.out.println("TimeOut: " + counter);
            			}
            			
            			//resend for timeout and err acks
            			time = System.nanoTime();
            			socket.send(request);
            			//logging resend message
            			System.out.println("ReSend" + counter +  " " + offset + ":" + (offset+data.length-1) + " " 
                        		+ TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - time) + " " + dropCheck(bad));
            		}
            		
            		baos.reset();
            	}
            }
            
            //for images of larger size, just use packet size of 1024 bytes of data + header
        	else {
            	int counter=0;
        		while(offset + 1024 < in.length()) {
        		buf=null;
        		counter++;
        		offset = 1024*(counter-1);
        		
        		//get data from image file, if last packet then just get remaining data instead of full packet
        		if(inLength <= offset + 1023)
        			{data = Arrays.copyOfRange(img, offset, (int) inLength);}
        		else
        			{data = Arrays.copyOfRange(img, offset, offset+1024);}
        		
        		//add header
    			dos.writeInt(counter);
    			dos.writeInt(offset);
    			dos.writeLong(inLength);
        		
        		dos.write(data);
        		buf = baos.toByteArray();
        		
        		System.out.println(counter + "-" + offset +"-"+(offset+data.length-1));
        		
        		//create packet and send request
        		if(inLength <= offset + 1023)
            		request = new DatagramPacket(buf, (int) inLength-offset+16,host, PORT);
        		else 
            		request = new DatagramPacket(buf, 1040, host, PORT);

        		long time = System.nanoTime();
        		socket.send(request);
        		//logging send message for larger files
        		System.out.println("SENDing " + counter +  " " + offset + ":" + (offset+data.length-1) + " " 
        		+ TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - time) + " " + dropCheck(bad));
        		
        		socket.setSoTimeout(2000);
        		
        		while(true) {
        			try{
        				DatagramPacket ack = new DatagramPacket(new byte[8], 8);
            			socket.receive(ack);
            			
            			//parsing ack
                        headerBB = ByteBuffer.wrap(ack.getData());
                        ackerr = headerBB.get();
                        ackno = headerBB.getInt();
                        
                        if (ackerr == 1) {
                        	ackStatus = "ErrAck";
                        }
                        else if(ackno != counter) {
                        	ackStatus = "DuplAck";
                        }
                        else {
                        	ackStatus = "MoveWnd";
                        }
            			
                        //loggin ack
            			System.out.println("AckRcvd " + ackno + " " + ackStatus);
        				if(ackStatus.equals("MoveWnd")){
        					break;
        				}
        			}
        			//logging timeout
        			catch (SocketTimeoutException e) {
        				System.out.println("TimeOut: " + counter);
        			}
        			
        			//resending for timeout and err ack
        			time = System.nanoTime();
        			socket.send(request);
        			//logging resend
        			System.out.println("ReSend" + counter +  " " + offset + ":" + (offset+data.length-1) + " " 
                    		+ TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - time) + " " + dropCheck(bad));
        		}
        		baos.reset();
        		}
        	}
        } catch (IOException ex) {
            ex.printStackTrace();
        }
}
    
    private static String dropCheck (double bad) {
    	double badCheck = Math.random();
		if (badCheck < bad/2)
			return "DROP";
		else if (badCheck < bad)
			return "ERRR";
		else
			return "SENT";
    }
    
}