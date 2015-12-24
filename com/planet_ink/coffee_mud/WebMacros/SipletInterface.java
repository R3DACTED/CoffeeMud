package com.planet_ink.coffee_mud.WebMacros;

import com.planet_ink.coffee_web.interfaces.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.DatabaseEngine.PlayerData;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.channels.Pipe;
import java.security.MessageDigest;
import java.util.*;

import com.planet_ink.coffee_mud.core.exceptions.HTTPServerException;
import com.planet_ink.siplet.applet.*;
import com.sun.org.apache.xml.internal.security.exceptions.Base64DecodingException;
import com.sun.org.apache.xml.internal.security.utils.Base64;

/*
   Copyright 2011-2015 Bo Zimmerman

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

	   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
public class SipletInterface extends StdWebMacro
{
	@Override
	public String name()
	{
		return "SipletInterface";
	}

	@Override
	public boolean isAWebPath()
	{
		return true;
	}

	public static final LinkedList<String> removables		 = new LinkedList<String>();
	public static final Object 			   sipletConnectSync = new Object();
	public static volatile boolean 		   initialized		 = false;
	public static final SHashtable<String,SipletSession> 	 siplets 	= new SHashtable<String,SipletSession>();

	protected class SipletSession
	{
		public long 		lastTouched = System.currentTimeMillis();
		public Siplet 		siplet		= null;
		public String   	response	= "";
		public SipletSession(Siplet sip) { siplet=sip;}
	}

	protected class PipeSocket extends Socket
	{
		private boolean					isClosed	= false;
		private final PipedInputStream	inStream	= new PipedInputStream();
		private final PipedOutputStream	outStream	= new PipedOutputStream();
		private InetAddress				addr		= null;
		private PipeSocket				friendPipe	= null;

		public PipeSocket(InetAddress addr, PipeSocket pipeLocal) throws IOException
		{
			this.addr=addr;
			if(pipeLocal!=null)
			{
				pipeLocal.inStream.connect(outStream);
				pipeLocal.outStream.connect(inStream);
				friendPipe=pipeLocal;
				pipeLocal=friendPipe;
			}
		}

		@Override
		public void shutdownInput() throws IOException
		{
			inStream.close();
			isClosed = true;
		}

		@Override
		public void shutdownOutput() throws IOException
		{
			outStream.close();
			isClosed = true;
		}

		@Override
		public boolean isConnected()
		{
			return !isClosed;
		}

		@Override
		public boolean isClosed()
		{
			return isClosed;
		}

		@Override
		public synchronized void close() throws IOException
		{
			inStream.close();
			outStream.close();
			if (friendPipe != null)
			{
				friendPipe.shutdownInput();
				friendPipe.shutdownOutput();
			}
			isClosed = true;
		}

		@Override
		public InputStream getInputStream() throws IOException
		{
			return inStream;
		}

		@Override
		public OutputStream getOutputStream() throws IOException
		{
			return outStream;
		}

		@Override
		public InetAddress getInetAddress()
		{
			return addr;
		}
	}
	
	protected void initialize()
	{
		initialized = true;
		CMLib.threads().startTickDown(new Tickable()
		{
			private int	tickStatus	= Tickable.STATUS_NOT;

			@Override
			public int getTickStatus()
			{
				return tickStatus;
			}

			@Override
			public String name()
			{
				return "SipletInterface";
			}

			@Override
			public boolean tick(Tickable ticking, int tickID)
			{
				tickStatus = Tickable.STATUS_ALIVE;
				synchronized (siplets)
				{
					for (final String key : siplets.keySet())
					{
						final SipletSession p = siplets.get(key);
						if ((p != null) && ((System.currentTimeMillis() - p.lastTouched) > (2 * 60 * 1000)))
						{
							p.siplet.disconnectFromURL();
							removables.addLast(key);
						}
					}
					if (removables.size() > 0)
					{
						for (final String remme : removables)
							siplets.remove(remme);
						removables.clear();
					}
				}
				tickStatus = Tickable.STATUS_NOT;
				return true;
			}

			@Override
			public String ID()
			{
				return "SipletInterface";
			}

			@Override
			public CMObject copyOf()
			{
				return this;
			}

			@Override
			public void initializeClass()
			{
			}

			@Override
			public CMObject newInstance()
			{
				return this;
			}

			@Override
			public int compareTo(CMObject o)
			{
				return o == this ? 0 : 1;
			}
		}, Tickable.TICKID_MISCELLANEOUS, 10);
	}

	@Override
	public String runMacro(HTTPRequest httpReq, String parm, HTTPResponse httpResp) throws HTTPServerException
	{
		if(!CMProps.getBoolVar(CMProps.Bool.MUDSTARTED))
			return "false;";
		if(!initialized)
		{
			initialize();
		}
		
		if(("websocket".equals(httpReq.getHeader("upgrade")))
		&&("Upgrade".equals(httpReq.getHeader("connection"))))
		{
			try
			{
				final String key = httpReq.getHeader("sec-websocket-key");
				final String tokenStr = key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
				final MessageDigest cript = MessageDigest.getInstance("SHA-1");
				cript.reset();
				cript.update(tokenStr.getBytes("utf8"));
				final String token = Base64.encode(cript.digest());
				httpResp.setStatusCode(101);
				httpResp.setHeader("Upgrade", "websocket");
				httpResp.setHeader("Connection", "Upgrade");
				httpResp.setHeader("Sec-WebSocket-Accept", token);
				return "";
			}
			catch (Exception e)
			{
				Log.errOut(e);
				throw new HTTPServerException(e.getMessage());
			}
		}

		if(httpReq.isUrlParameter("CONNECT"))
		{
			final String url=httpReq.getUrlParameter("URL");
			final int port=CMath.s_int(httpReq.getUrlParameter("PORT"));
			String hex="";
			final Siplet sip = new Siplet();
			boolean success=false;
			if(url!=null)
			{
				sip.init();
				synchronized(sipletConnectSync)
				{
					for(final MudHost h : CMLib.hosts())
					{
						if(h.getPort()==port)
						{
							try
							{
								final PipeSocket lsock=new PipeSocket(httpReq.getClientAddress(),null);
								final PipeSocket rsock=new PipeSocket(httpReq.getClientAddress(),lsock);
								success=sip.connectToURL(url, port,lsock);
								sip.setFeatures(true, Siplet.MSPStatus.External, false);
								h.acceptConnection(rsock);
							}
							catch(final IOException e)
							{
								success=false;
							}
						}
					}
				}
				if(success)
				{
					synchronized(siplets)
					{
						int tokenNum=0;
						int tries=1000;
						while((tokenNum==0)&&((--tries)>0))
						{
							tokenNum = new Random().nextInt();
							if(tokenNum<0)
								tokenNum = tokenNum * -1;
							hex=Integer.toHexString(tokenNum);
							if(httpReq.isUrlParameter(hex))
								tokenNum=0;
						}
						siplets.put(hex, new SipletSession(sip));
					}
				}
			}
			return Boolean.toString(success)+';'+hex+';'+sip.info()+hex+';';
		}
		else
		if(httpReq.isUrlParameter("DISCONNECT"))
		{
			final String token=httpReq.getUrlParameter("TOKEN");
			boolean success = false;
			if(token != null)
			{
				final SipletSession p = siplets.get(token);
				if(p!=null)
				{
					siplets.remove(token);
					p.siplet.disconnectFromURL();
					success=true;
				}
			}
			return Boolean.toString(success)+';';
		}
		else
		if(httpReq.isUrlParameter("SENDDATA"))
		{
			final String token=httpReq.getUrlParameter("TOKEN");
			boolean success = false;
			if(token != null)
			{
				final SipletSession p = siplets.get(token);
				if(p!=null)
				{
					String data=httpReq.getUrlParameter("DATA");
					if(data!=null)
					{
						p.lastTouched=System.currentTimeMillis();
						p.siplet.sendData(data);
						if(p.siplet.isConnectedToURL())
						{
							CMLib.s_sleep(10);
							if(p.siplet.isConnectedToURL())
							{
								p.lastTouched=System.currentTimeMillis();
								p.siplet.readURLData();
								data = p.siplet.getURLData();
								final String jscript = p.siplet.getJScriptCommands();
								success=p.siplet.isConnectedToURL();
								p.response=Boolean.toString(success)+';'+data+token+';'+jscript+token+';';
								return p.response;
							}
						}
					}
				}
			}
			return Boolean.toString(success)+';';
		}
		else
		if(httpReq.isUrlParameter("POLL"))
		{
			final String token=httpReq.getUrlParameter("TOKEN");
			if(token != null)
			{
				final SipletSession p = siplets.get(token);
				if(p!=null)
				{
					if(p.siplet.isConnectedToURL())
					{
						if(httpReq.isUrlParameter("LAST"))
							return p.response;
						else
						{
							p.lastTouched=System.currentTimeMillis();
							p.siplet.readURLData();
							final String data = p.siplet.getURLData();
							final String jscript = p.siplet.getJScriptCommands();
							final boolean success=p.siplet.isConnectedToURL();
							p.response=Boolean.toString(success)+';'+data+token+';'+jscript+token+';';
							return p.response;
						}
					}
				}
			}
			return "false;"+token+";"+token+";";
		}
		return "false;";
	}
}
