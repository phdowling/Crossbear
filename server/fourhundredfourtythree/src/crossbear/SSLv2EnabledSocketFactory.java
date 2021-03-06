/*
    This file is part of Crossbear.

    Crossbear is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Crossbear is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Crossbear.  If not, see <http://www.gnu.org/licenses/>.

    Original authors: Thomas Riedmaier, Ralph Holz (TU Muenchen, Germany)
*/

package crossbear;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.LinkedList;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * This class is almost identical to the javax.net.ssl.SSLSocketFactory-class. The only difference is that the SSLv2EnabledSocketFactory-class generates SSLSockets that have the "SSLv2Hello"-Protocol
 * enabled while the SSLSocketFactory-class doesn't have this anymore since Java version 1.7. Since Crossbear is bound to use Java version 1.7 in order to connect to SNI-enabled hosts this class
 * became necessary.
 * 
 * Please Note: "SSLv2Hello" is deprecated but still used by some old webservers. Since Crossbear must also be able to connect to them it uses the SSLv2EnabledSocketFactory-class.
 * 
 * @author Thomas Riedmaier
 * 
 */
public class SSLv2EnabledSocketFactory extends SSLSocketFactory {
	
	/**
	 * Enable the deprecated "SSLv2Hello"-Protocol on a SSLSocket
	 * 
	 * @param sock The SSLSocket to enhance with the "SSLv2Hello"-Protocol
	 * @return The "SSLv2Hello"-enhanced SSLSocket
	 */
	private static SSLSocket enableSSLv2Hello(SSLSocket sock) {
		// Get the socket's currently active protocols
		LinkedList<String> prots = new LinkedList<String>(Arrays.asList(sock.getEnabledProtocols()));
		
		// Add the "SSLv2Hello"-Protocol
		prots.add("SSLv2Hello");
		sock.setEnabledProtocols(prots.toArray(new String[]{}));
		
		// Return the enhanced Socket
		return sock;
	}

	// The SSLSocketFactory that is actually used to create the SSLSockets which will then be enhanced with the "SSLv2Hello"-Protocol
	private final SSLSocketFactory factory;

	/* (non-Javadoc)
	 * @see javax.net.SSLContext#getSocketFactory()
	 */
	public SSLv2EnabledSocketFactory(SSLContext sslContext) {

		factory = sslContext.getSocketFactory();
	}

	/* (non-Javadoc)
	 * @see javax.net.SocketFactory#createSocket(java.net.InetAddress, int)
	 */
	@Override
	public Socket createSocket(InetAddress arg0, int arg1) throws IOException {
		SSLSocket sock = (SSLSocket) factory.createSocket(arg0, arg1);

		return enableSSLv2Hello(sock);
	}

	/* (non-Javadoc)
	 * @see javax.net.SocketFactory#createSocket(java.net.InetAddress, int, java.net.InetAddress, int)
	 */
	@Override
	public Socket createSocket(InetAddress arg0, int arg1, InetAddress arg2, int arg3) throws IOException {
		SSLSocket sock = (SSLSocket) factory.createSocket(arg0, arg1, arg2, arg3);

		return enableSSLv2Hello(sock);

	}

	/* (non-Javadoc)
	 * @see javax.net.ssl.SSLSocketFactory#createSocket(java.net.Socket, java.lang.String, int, boolean)
	 */
	@Override
	public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
		SSLSocket sock = (SSLSocket) factory.createSocket(s, host, port, autoClose);

		return enableSSLv2Hello(sock);
	}

	/* (non-Javadoc)
	 * @see javax.net.SocketFactory#createSocket(java.lang.String, int)
	 */
	@Override
	public Socket createSocket(String arg0, int arg1) throws IOException, UnknownHostException {
		SSLSocket sock = (SSLSocket) factory.createSocket(arg0, arg1);

		return enableSSLv2Hello(sock);
	}

	/* (non-Javadoc)
	 * @see javax.net.SocketFactory#createSocket(java.lang.String, int, java.net.InetAddress, int)
	 */
	@Override
	public Socket createSocket(String arg0, int arg1, InetAddress arg2, int arg3) throws IOException, UnknownHostException {
		SSLSocket sock = (SSLSocket) factory.createSocket(arg0, arg1, arg2, arg3);

		return enableSSLv2Hello(sock);
	}
	
	/* (non-Javadoc)
	 * @see javax.net.SocketFactory#createSocket()
	 */
	@Override
	public Socket createSocket() throws IOException{
		SSLSocket sock = (SSLSocket) factory.createSocket();
		
		return enableSSLv2Hello(sock);
	}

	/* (non-Javadoc)
	 * @see javax.net.ssl.SSLSocketFactory#getDefaultCipherSuites()
	 */
	@Override
	public String[] getDefaultCipherSuites() {
		return factory.getDefaultCipherSuites();
	}

	/* (non-Javadoc)
	 * @see javax.net.ssl.SSLSocketFactory#getSupportedCipherSuites()
	 */
	@Override
	public String[] getSupportedCipherSuites() {
		return factory.getSupportedCipherSuites();
	}

}
