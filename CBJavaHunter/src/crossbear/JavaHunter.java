/*
* Copyright (c) 2011, Thomas Riedmaier, TU München
* All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are met:
*     * Redistributions of source code must retain the above copyright
*       notice, this list of conditions and the following disclaimer.
*     * Redistributions in binary form must reproduce the above copyright
*       notice, this list of conditions and the following disclaimer in the
*       documentation and/or other materials provided with the distribution.
*     * Neither the name of Crossbear nor the
*       names of its contributors may be used to endorse or promote products
*       derived from this software without specific prior written permission.
* 
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
* ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
* WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
* DISCLAIMED. IN NO EVENT SHALL THOMAS RIEDMAIER BE LIABLE FOR ANY
* DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
* (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
* LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
* ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
* (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
* SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package crossbear;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;

import javax.naming.NamingException;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import crossbear.messaging.CurrentServerTime;
import crossbear.messaging.HuntingTask;
import crossbear.messaging.HuntingTaskReply;
import crossbear.messaging.HuntingTaskReplyKnownCertChain;
import crossbear.messaging.HuntingTaskReplyNewCertChain;
import crossbear.messaging.Message;
import crossbear.messaging.MessageList;
import crossbear.messaging.PublicIPNotification;

/**
 * A JavaHunter is a Java-based command-line application that implements the Crossbear-Hunting-functionality. When executed a JavaHunter will connect to the Crossbear-Server to download the current
 * HuntingTask-List. Afterwards it will execute the latter's HuntingTasks in the same way the CBHunter of the Firefox-Add-on would. Finally, the generated HuntingTaskResults will be send to the Crossbear-Server.
 * 
 * @author Thomas Riedmaier
 * 
 */
public class JavaHunter {

	// The duration in seconds that a PublicIP will be considered as unchanged (after that duration the current PublicIP will be requested)
	private final static int pipCacheValidity = 60000;
	
	/**
	 * Download the current HuntingTask-List from the Crossbear-Server, execute it and send the results back to the server
	 * 
	 * @param args Currently not used
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

	    /*
	     * Adding the bouncy castle Security Provider is
	     * required for the use of - "SHA256"-HMAC -
	     * "AES/CBC/PKCS7Padding"-Symmetric Encryption -
	     * "RSA/None/OAEPWithSHA1AndMGF1Padding"-Asymmetric
	     * Encryption all of these are used in Crossbear.
	     */
	    Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
	    
	    // Create a new JavaHunter that will contact the Crossbear-Server using a specific domain and execute the HuntingTasks using the given Traceroute-parameters
	    JavaHunter jh = new JavaHunter("crossbear.net.in.tum.de",20,5);
	    
	    // Fetch the HuntingTask-List from the Crossbear-Server and parse it
	    jh.getAndParseHTL();
	    
	    // Execute the HuntingTask-List and send the generated results to the Crosbear-Server
	    jh.executeHTL();
	    
	}

	/**
	 * Put a specific record in the JVM's DNS-cache.
	 * 
	 * This code was created by the use of http://stackoverflow.com/questions/1835421/java-dns-cache-viewer
	 * 
	 * @param domain The domain for which the IP-addresses should be set (e.g. "www.google.com")
	 * @param addresses The IP-addresses that should be used when connecting to "domain" (e.g. ["1.2.3.4", "5.6.7.8"])
	 * @throws SecurityException 
	 * @throws NoSuchFieldException 
	 * @throws UnknownHostException 
	 * @throws IllegalAccessException 
	 * @throws IllegalArgumentException 
	 * @throws Exception
	 */
	private static void setDNSCacheEntry(String domain, InetAddress[] addresses) throws NoSuchFieldException, SecurityException, UnknownHostException, IllegalArgumentException, IllegalAccessException{
		
		// Make sure that there is a entry in the local DNS-cache for "domain"
		InetAddress.getByName(domain);
		
		// Get a handle to the JVM-internal DNS-cache using reflection
		Class<InetAddress> klass = InetAddress.class;
		Field acf = klass.getDeclaredField("addressCache");
		acf.setAccessible(true);
		Object addressCache = acf.get(null);
		Class<? extends Object> cacheKlass = addressCache.getClass();
		Field cf = cacheKlass.getDeclaredField("cache");
		cf.setAccessible(true);
		
		// Because of the use of the reflection technique the cache doesn't have the correct type: Cast it into a Map<String, Object>
		@SuppressWarnings("unchecked")
		Map<String, Object> cache = (Map<String, Object>) cf.get(addressCache);
		
		// Iterate over all entries of the DNS-cache
		for (Map.Entry<String, Object> hi : cache.entrySet()) {
			
			// Dont't do anything if the entry is not for "domain"
			if(!hi.getKey().equals(domain)) continue;
			
			// When "domain"'s entry is finally found: Use reflection to get its type so it can be accessed
			Object cacheEntry = hi.getValue();
			Class<? extends Object> cacheEntryKlass = cacheEntry.getClass();
			
			// Set the entry's validity to 10 seconds
			Field expf = cacheEntryKlass.getDeclaredField("expiration");
			expf.setAccessible(true);
			expf.set(cacheEntry, System.currentTimeMillis()+10000);

			// Set the entry's addresses
			Field af = cacheEntryKlass.getDeclaredField("addresses");
			af.setAccessible(true);
			af.set(cacheEntry, addresses);

		}	
	}
	
	// The Hostname of the Crossbear-Server (e.g. crossbear.net.in.tum.de)
	private String cbServerHostName;

	// The SHA256-Hash of the certificate that the Crossbear-Server uses
	private byte[] cbServerCertHash;

	// The most recent CurrentServerTime-message that was received from the Crossbear-Server (can be used to estimate the server local time)
	private CurrentServerTime cst = null;
	
	// The most recent PublicIPNotification-messages that were received from the Crossbear-Server
	private PublicIPNotification pip4 = null, pip6 = null;
	
	// The Timestamps stating when the PublicIPNotification-messages have been received (in local time)
	private Timestamp pip4LU = new Timestamp(0), pip6LU = new Timestamp(0);

	// The list that contains all HuntingTasks that were received from the Crossbear-Server
	private LinkedList<HuntingTask> hts = new LinkedList<HuntingTask>();

	// An object that can be used to fetch PublicIPNotifications from the Crossbear-Server
	private PIPFetcher pipfetcher;
	
	// An object that can be used to download the HuntingTask-List from the Crossbear-Server
	private HTLFetcher htlfetcher;
	
	// An object that can be used to perform Traceroutes
	private Tracer tracer;

	// A CertificateManager that can be used to complete certificate chains
	private CertificateManager cm;
	
	/**
	 * Create a new JavaHunter
	 * 
	 * @param cbServerHostName The Hostname of the Crossbear-Server (e.g. crossbear.net.in.tum.de)
	 * @param tracerMaxHops Traceroute-parameter: Number of hops that should be taken into account before terminating the Traceroute
	 * @param tracerSamplesPerHop Traceroute-parameter: Number of samples to be taken per hop (i.e. should be sent with the same TTL)
	 * @throws CertificateException
	 * @throws NoSuchAlgorithmException
	 * @throws NamingException
	 * @throws IOException 
	 * @throws SQLException 
	 * @throws KeyStoreException 
	 */
	public JavaHunter(String cbServerHostName, int tracerMaxHops, int tracerSamplesPerHop) throws CertificateException, NoSuchAlgorithmException, NamingException, KeyStoreException, SQLException, IOException {
		this.cbServerHostName = cbServerHostName;

		// Load the certificate of the Crossbear-Server from the local file system
		X509Certificate cbServerCert = CertificateManager.loadCertificateFromFile("cbserver.crt");

		// Calculate the SHA256-Hash of the Crossbear-certificate and store it
		this.cbServerCertHash = CertificateManager.SHA256(cbServerCert.getEncoded());
		
		// Initialize the helpers that are needed during hunting 
		this.pipfetcher = new PIPFetcher(cbServerHostName, cbServerCert);
		this.htlfetcher = new HTLFetcher(cbServerHostName+":443",cbServerCertHash);
		this.tracer = new Tracer(tracerMaxHops,tracerSamplesPerHop);
		this.cm = new CertificateManager(null,0, "changeit");
	}
	
	/**
	 * Calculate the Hash of a certificate chain. This is, concatenate the SHA256-hash of the server's certificate with the MD5-hashes of the chain certificates and calculate the SHA256-hash on the result
	 * 
	 * @param certChain The certificate chain to calculate the hash for (including the server certificate)
	 * @return The hash of the certificate chain
	 * @throws CertificateEncodingException
	 * @throws NoSuchAlgorithmException
	 * @throws UnsupportedEncodingException
	 */
	private byte[] calculateCertChainHash(X509Certificate[] certChain) throws CertificateEncodingException, NoSuchAlgorithmException, UnsupportedEncodingException {

		// Convert the certificate chain into a LinkedList
		LinkedList<X509Certificate> certChainLL = new LinkedList<X509Certificate>(Arrays.asList(certChain));

		// Remove the server's certificate from the chain
		certChainLL.removeFirst();

		// Get the concatenation of the md5 hashes of the chain certificates ...
		String certChainMD5 = CertificateManager.getCertChainMD5(certChainLL);
		
		// Calculate the SHA256-hash of the server certificate
		byte[] serverCertHash = CertificateManager.SHA256(certChain[0].getEncoded());

		// Concatenate the hash of the server certificate with the ones of its chain and calculate the SHA256-hash for the result
		return CertificateManager.SHA256(Message.hexStringToByteArray(Message.byteArrayToHexString(serverCertHash) + certChainMD5));
	}
	
	/**
	 * Execute the HuntingTask-List and send the execution results to the Crossbear-Server
	 * 
	 * @throws Exception
	 */
	private void executeHTL() throws Exception {

		// Create an empty list of HuntingTaskReplies
		int numOfResults = 0;
		MessageList htr = new MessageList();

		// Randomize the order of the HuntingTasks
		Collections.shuffle(hts, new Random());
		
		// Iterate over all HuntingTasks ...
		Iterator<HuntingTask> hti = hts.iterator();
		while (hti.hasNext()) {

			// ... and execute them.
			HuntingTaskReply htrep = executeHuntingTask(hti.next());
			
			// In case the execution was successful store the Result in the list of HuntingTaskReplies
			if(htrep != null){
				numOfResults++;
				htr.add(htrep);
			}

			// If 5 or more HuntingTaskReplies are available or if there are no more HuntingTasks: Send the Replies to the Crossbear-Server
			if (numOfResults >= 5 || (!hti.hasNext() && numOfResults>0) ) {
				System.out.println("Sending "+numOfResults+" HuntingTaskReplies to the Crossbear-Server");
				sendHuntingTaskResultsToServer(htr);
				htr = new MessageList();
				numOfResults = 0;
			}

		}

	}

	/**
	 * Execute a HuntingTask and create a HuntingTaskReply for it
	 * 
	 * @param task The HuntingTask to execute
	 * @return A HuntingTaskReply if the execution of the HuntingTask was successful, and null if not
	 * @throws Exception
	 */
	private HuntingTaskReply executeHuntingTask(HuntingTask task) throws Exception{
		
		// Extract the IP-version of the HuntingTask
		boolean taskIsv4 = (task.getType() == Message.MESSAGE_TYPE_IPV4_SHA256_TASK);

		// Check if there is a fresh PublicIP for that IP-version
		if (!isFreshPublicIPAvailable(taskIsv4?4:6)){
			
			// If not: Return null since it is not possible to execute the HuntingTask
			System.out.println("Skipping execution of Task "+task.getTaskID()+ " since there is no fresh PublicIP for it");
			return null;
		}
		
		System.out.println("Executing Task "+task.getTaskID());

		// Get the CertificateChain for the HuntingTask's target. To do so put the Target's Hostname/IP-combination in the JVM's DNS-cache. A normal connect to that Hostname will then connect to the correct IP while still using SNI. 
		setDNSCacheEntry(task.getTargetHostName(),new InetAddress[] {task.getTargetIP()});
		CertificateChainContainer CCC = CertificateManager.getCertChainFromServer(task.getTargetHostName() , task.getTargetPort());
		X509Certificate[] targetCertChain = CCC.getChain();
		
		// Try to complete the chain
		LinkedList<X509Certificate> completedChain = cm.makeCertChainValid(targetCertChain, 50, true);
		if(completedChain != null){
			targetCertChain = completedChain.toArray(new X509Certificate[]{});
		}

		// Calculate the Hash of the Target's certificate chain
		byte[] targetCertChainHash = calculateCertChainHash(targetCertChain);
		

		// Check if it is already well known for the Target
		boolean certIsKnown = false;
		for (int i = 0; i < task.getAlreadyKnownCertChainHashes().length; i++) {
			if (Arrays.equals(targetCertChainHash, task.getAlreadyKnownCertChainHashes()[i])) {
				certIsKnown = true;
				break;
			}
		}

		// Perform a traceroute for the Target's IP
		String trace = tracer.traceroute(task.getTargetIP(), taskIsv4 ? 4 : 6);
		trace = Tracer.addOwnPublicIPAndRemovePrivateIPs(taskIsv4 ? pip4.getPublicIP() : pip6.getPublicIP(), trace);

		// Build and return either a HuntingTaskReplyKnownCertChain or a HuntingTaskReplyNewCertChain depending on whether the Target's certificate is already well known.
		if (certIsKnown) {
			return new HuntingTaskReplyKnownCertChain(task.getTaskID(), this.cst.getCurrentServerTime(), taskIsv4 ? pip4.gethMac() : pip6.gethMac(), targetCertChainHash, trace);
		} else {
			return new HuntingTaskReplyNewCertChain(task.getTaskID(), this.cst.getCurrentServerTime(), taskIsv4 ? pip4.gethMac() : pip6.gethMac(), targetCertChain, trace);
		}
		
	}

	/**
	 * Download the HuntingTask-List from the Crossbear-Server and parse it. Parsing in this context means:
	 * - Store the contained CurrentServerTime-message in the global cst-variable
	 * - Store the contained PublicIPNotification-message in the suitable global pipX-variable (and update its freshness-timestamp)
	 * - Store the contained HuntingTasks in the global hts-list
	 * 
	 * @throws CertificateException
	 * @throws NoSuchAlgorithmException
	 * @throws KeyManagementException
	 * @throws IOException
	 */
	private void getAndParseHTL() throws CertificateException, NoSuchAlgorithmException, KeyManagementException, IOException {

		// Download the HuntingTask-List from the Crossbear-Server
		LinkedList<Message> htl = htlfetcher.getHTLFromServer();

		// Iterate over all Messages of the HTL
		Iterator<Message> mIt = htl.iterator();
		while (mIt.hasNext()) {
			
			// Store the messages depending on their types
			Message m = mIt.next();
			switch (m.getType()) {
			
			//Store the CurrentServerTime-message in the global cst-variable
			case Message.MESSAGE_TYPE_CURRENT_SERVER_TIME:
				cst = (CurrentServerTime) m;
				break;
				
			// Store the PublicIPNotification-message in the suitable global pipX-variable (and update its freshness-timestamp)
			case Message.MESSAGE_TYPE_PUBLIC_IP_NOTIF4:
				pip4 = (PublicIPNotification) m;
				pip4LU = new Timestamp(System.currentTimeMillis());
				break;
			case Message.MESSAGE_TYPE_PUBLIC_IP_NOTIF6:
				pip6 = (PublicIPNotification) m;
				pip6LU = new Timestamp(System.currentTimeMillis());
				break;
			
			// Store all HuntingTasks in the global hts-list
			case Message.MESSAGE_TYPE_IPV4_SHA256_TASK:
			case Message.MESSAGE_TYPE_IPV6_SHA256_TASK:
				hts.add((HuntingTask) m);
				break;
			}
		}
	}

	/**
	 * Check if the currently known PublicIP is still considered fresh. If it is: return true. If not try to obtain a fresh PublicIP from the Crossbear-Server. If that succeeded return true, else false.
	 * 
	 * @param ipVersion The IP-version of the PublicIP
	 * @return True if the current PublicIP either was fresh or could be refreshed; else false.
	 * @throws Exception
	 */
	private boolean isFreshPublicIPAvailable(int ipVersion) throws Exception {
		
		// Flag indicating if the current PublicIP is considered to be fresh
		boolean freshPubIPAvailable = false;

		// Check for IP-version 4
		if (ipVersion == 4) {

			// Check if the already known PublicIP is still fresh
			if (new Timestamp(System.currentTimeMillis() - pipCacheValidity).after(pip4LU)) {

				// If it is not fresh anymore: Try to refresh it
				System.out.println("Requesting a new IPv4 PublicIP");
				pip4 = pipfetcher.getFreshPublicIPNot(4);
				if (pip4 != null) {
					
					// If refreshing succeeded: Store the new PublicIP and the time of its observation
					pip4LU = new Timestamp(System.currentTimeMillis());
					freshPubIPAvailable = true;
				}

			// If it is still fresh: Everything is fine :)
			} else {
				freshPubIPAvailable = true;
			}

		// Check for IP-version 6
		} else if (ipVersion == 6) {

			// Check if the already known PublicIP is still fresh
			if (new Timestamp(System.currentTimeMillis() - pipCacheValidity).after(pip6LU)) {

				// If it is not fresh anymore: Try to refresh it
				System.out.println("Requesting a new IPv6 PublicIP");
				pip6 = pipfetcher.getFreshPublicIPNot(6);
				if (pip6 != null) {
					
					// If refreshing succeeded: Store the new PublicIP and the time of its observation
					pip6LU = new Timestamp(System.currentTimeMillis());
					freshPubIPAvailable = true;
				}
				
			// If it is still fresh: Everything is fine :)
			} else {
				freshPubIPAvailable = true;
			}

		}

		// Return the status of the freshness of the current PublicIP
		return freshPubIPAvailable;
	}


	/**
	 * Send a list of HuntingTaskReplies to the Crossbear-Server
	 * 
	 * @param huntingTaskResults The list to send
	 * @throws InvalidKeyException
	 * @throws CertificateEncodingException
	 * @throws NoSuchAlgorithmException
	 * @throws NoSuchProviderException
	 * @throws IOException
	 * @throws SQLException
	 * @throws KeyManagementException
	 */
	private void sendHuntingTaskResultsToServer(MessageList huntingTaskResults) throws InvalidKeyException, CertificateEncodingException, NoSuchAlgorithmException, NoSuchProviderException, IOException, SQLException, KeyManagementException {

		// Construct the URL that will receive the HuntingTaskReplies
		URL url = new URL("https://" + cbServerHostName + ":443/reportHTResults.jsp");

		// Open a HttpsURLConnection for that url
		HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

		// Make sure that the Crossbear server uses the certificate it is supposed to use (prevent Mitm-attacks against Crossbear)
		SSLContext sc = SSLContext.getInstance("SSL");
		sc.init(null, new TrustManager[] { new TrustSingleCertificateTM(cbServerCertHash) }, new java.security.SecureRandom());
		conn.setSSLSocketFactory(sc.getSocketFactory());
		
		// Send the HuntingTaskReplies to the server
		conn.setDoOutput(true);
		OutputStream out = conn.getOutputStream();
		out.write(huntingTaskResults.getBytes());
		out.flush();
		
		// Open the InputStream (required to actually send the data)
		InputStream is = conn.getInputStream();
		
		// Close all opened Streams
		is.close();
		out.close();
		
	}

}
