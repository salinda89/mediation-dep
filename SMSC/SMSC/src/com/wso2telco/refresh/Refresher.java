package com.wso2telco.refresh;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.codec.binary.Base64;
import org.apache.cxf.helpers.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * @author Charith_02380
 *
 */
public class Refresher extends Thread {

	private static AtomicReference<String> accessToken = new AtomicReference<String>();
	private static String refreshToken = null;
	private static int validityPeriod;
	private int refreshOffset = 2500;
	private String tokenEndpoint = "";
	private String tokenData = "";
	private String tokenRefreshData = "";
	private String consumerKey = "";
	private String consumerSecret = "";
	private Gson gson;
	private String bearerToken = "";
	private String username;
	private String password;

	/**
	 * 
	 */
	public Refresher() {
		try {
			gson = new GsonBuilder().serializeNulls().create();
			
			Properties settings = new Properties();
			
			String defaultPath = new File("./conf/settings.xml").getCanonicalPath();
			System.out.println("Looking for file in default location..."+new File("./conf").getCanonicalPath());
			File file = new File(defaultPath);
			file.getAbsoluteFile();
			InputStream is = new FileInputStream(file);
			
			settings.loadFromXML(is/*Refresher.class.getResourceAsStream("settings.xml")*/);

			tokenEndpoint = settings.getProperty("token_endpoint");
			tokenData = settings.getProperty("token_data");
			tokenRefreshData = settings.getProperty("token_refresh");
			consumerKey = settings.getProperty("app_key");
			consumerSecret = settings.getProperty("app_secret");
			username = settings.getProperty("username");
			password = settings.getProperty("password");
			refreshOffset = Integer.parseInt(settings.getProperty("refresh_offset"));
			bearerToken = new String(Base64.encodeBase64((consumerKey+":"+consumerSecret).getBytes()));

			//===========================REFRESH TOKEN PATCH===========================
			accessToken.set(settings.getProperty("access_token"));//get access token from properties file
			refreshToken = settings.getProperty("refresh_token");
			validityPeriod = Integer.valueOf(settings.getProperty("validity_period"));
			
			if(accessToken.get()== null) {
				generateToken();//generate access token
			}
			//===========================REFRESH TOKEN PATCH===========================
			start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 
	 */
	private void generateToken() {
			try {
				HttpPost post = new HttpPost(tokenEndpoint);
				post.addHeader("Authorization", "Basic "+bearerToken);				
				Object[] args = {username, password};
				String tokenCreatePostData = MessageFormat.format(tokenData, args);
				StringEntity strEntity = new StringEntity(tokenCreatePostData, "UTF-8");
				strEntity.setContentType("application/x-www-form-urlencoded");
				post.setEntity(strEntity);
				CloseableHttpResponse response = null;
				SSLContextBuilder builder = new SSLContextBuilder();
			    builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
			    SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build());
			    CloseableHttpClient httpclient = HttpClients.custom().setSSLSocketFactory(sslsf).build();
				response = httpclient.execute(post);
				HttpEntity entity = response.getEntity();
		      
				if (entity != null) {
			        InputStream instream = entity.getContent();
			        StringWriter writer = new StringWriter();
			        IOUtils.copy(new InputStreamReader(instream), writer, 1024);
			        String body = writer.toString();
			        TokenResponse resp = gson.fromJson(body, TokenResponse.class);
			        accessToken.set(resp.getAccessToken());
			        System.out.println("RECEIVED TOKEN(1)>" + resp.getAccessToken());
			        refreshToken = resp.getRefreshToken();
			        validityPeriod = resp.getExpiresIn();
				}		
			} catch (UnknownHostException e) {
			  e.printStackTrace();
			  System.out.println("Please check your internet connection.");
			  System.exit(0);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	/**
	 * 
	 * @return
	 */
	public static String getToken() {
		if(accessToken.get()  == null) {
			new Refresher();
		}
		return accessToken.get();
	}
	
	@Override
	public void run() {
		while(true) {
			try {
				  try {
				    if(validityPeriod > refreshOffset) {
				      sleep((validityPeriod-refreshOffset)*1000);
				    }
				  } catch (InterruptedException e) {
					  e.printStackTrace();
				  }
				HttpPost post = new HttpPost(tokenEndpoint);
				post.addHeader("Authorization", "Basic "+bearerToken);
				
				Object[] args = {refreshToken, refreshToken};
				String tokenRefreshPostData = MessageFormat.format(tokenRefreshData, args);
				
				StringEntity strEntity = new StringEntity(tokenRefreshPostData, "UTF-8");
				strEntity.setContentType("application/x-www-form-urlencoded");
				post.setEntity(strEntity);
				
				CloseableHttpResponse response = null;
				
				SSLContextBuilder builder = new SSLContextBuilder();
			    builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
			    SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
			            builder.build());
			    CloseableHttpClient httpclient = HttpClients.custom().setSSLSocketFactory(
			            sslsf).build();
				
				response = httpclient.execute(post);
				HttpEntity entity = response.getEntity();
				
				if (entity != null) {
	                InputStream instream = entity.getContent();
	                StringWriter writer = new StringWriter();
	                IOUtils.copy(new InputStreamReader(instream), writer, 1024);
	                String body = writer.toString();
	                
	                //System.out.println(body);
	                
	                TokenResponse resp = gson.fromJson(body, TokenResponse.class);
	                accessToken.set(resp.getAccessToken());
	                System.out.println("RECEIVED TOKEN(2)>" + resp.getAccessToken());
	                refreshToken = resp.getRefreshToken();
	                validityPeriod = resp.getExpiresIn();
				}				
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public static void main(String[] args) {
		new Thread() {
			@Override
			public void run() {
				while(true) {
					try {
						System.out.println(Refresher.getToken());
						sleep(1000);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}.start();
	}
}