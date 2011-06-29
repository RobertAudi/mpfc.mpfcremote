package com.sgsoftware.mpfcremote;

import javax.net.SocketFactory;
import java.net.Socket;
import java.io.InputStream;
import java.io.OutputStream;

public class RemotePlayer {
	
	Socket m_sock;
	InputStream m_input;
	OutputStream m_output;
	
	public RemotePlayer(String addr, int port) 
			throws java.net.UnknownHostException, java.io.IOException
	{
		SocketFactory sockFactory = SocketFactory.getDefault();
		m_sock = sockFactory.createSocket(addr, port);
		m_input = m_sock.getInputStream();
		m_output = m_sock.getOutputStream();
		
	}
	
	public void pause()
	{
		send("pause\n");
	}
	
	private void send(String s)
	{
		try
		{
			m_output.write(s.getBytes());
		}
		catch (java.io.IOException e)
		{
		}
	}

}
