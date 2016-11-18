/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.fhg.camel.ids.both;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.jsse.ClientAuthentication;
import org.apache.camel.util.jsse.KeyManagersParameters;
import org.apache.camel.util.jsse.KeyStoreParameters;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.camel.util.jsse.SSLContextServerParameters;
import org.apache.camel.util.jsse.TrustManagersParameters;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.fhg.camel.ids.client.TestServletFactory;
import de.fhg.camel.ids.client.WsComponent;
import de.fhg.camel.ids.server.WebsocketComponent;

/**
 *
 */
public class WsProducerConsumerTest extends CamelTestSupport {
    protected static final String TEST_MESSAGE = "Hello World!";
    protected static final int PORT = AvailablePortFinder.getNextAvailable();
    protected Server server;
    private Process ttp = null;
    private Process tpm2dc = null;
    private Process tpm2ds = null;    
    private File socketServer;
    private File socketClient;
    protected List<Object> messages;
	private static String PWD = "changeit";
	private String dockerName = "registry.netsec.aisec.fraunhofer.de/ids/tpm2dmock:latest";
	private String sockets = "tpm2ds.sock";
	private String socketc = "tpm2dc.sock";
	
    @Override
	protected void doPostSetup() throws Exception {
        // start a simple websocket echo service
        server = new Server(PORT);
        Connector connector = new ServerConnector(server);
        server.addConnector(connector);

        ServletContextHandler ctx = new ServletContextHandler();
        ctx.setContextPath("/");
        ctx.addServlet(TestServletFactory.class.getName(), "/*");

        server.setHandler(ctx);
        
        server.start();
        assertTrue(server.isStarted());      
    }
    
    @Before
    public void initMockServer() throws InterruptedException, IOException {
		socketServer = new File("mock/socket/"+sockets);
		socketClient = new File("mock/socket/"+socketc);
		
		String folder = socketServer.getAbsolutePath().substring(0, socketServer.getAbsolutePath().length() - sockets.length());

		// build a docker imagess
    	ProcessBuilder image = new ProcessBuilder("docker", "build", "-t", dockerName, "mock");
    	Process generator = image.start();
    	
    	// then start the docker image as tpm2d for the server
    	ProcessBuilder tpm2dsService = new ProcessBuilder("docker", "run", "--name", "tpm2ds", "-d", "-v", folder +":/socket/", dockerName, "su", "-m", "tpm2d", "-c", "/tpm2d/tpm2dc.py");
    	ProcessBuilder tpm2dcService = new ProcessBuilder("docker", "run", "--name", "tpm2dc", "-d", "-v", folder +":/socket/", dockerName, "su", "-m", "tpm2d", "-c", "/tpm2d/tpm2ds.py");
    	ProcessBuilder ttpService = new ProcessBuilder("docker", "run", "--name", "ttp", "-d", "-p", "127.0.0.1:7331:29663", dockerName, "/tpm2d/ttp.py");
    	tpm2dc = tpm2dcService.start();
    	tpm2ds = tpm2dsService.start();
    	ttp = ttpService.start();
    }
    
    @After
    public void teardownMockServer() throws Exception {
    	Process stopTtp = new ProcessBuilder("docker", "stop", "ttp").start();
    	Process stopTpm2ds = new ProcessBuilder("docker", "stop", "tpm2ds").start();
    	Process stopTpm2dc = new ProcessBuilder("docker", "stop", "tpm2dc").start();
    	Process rmTtp = new ProcessBuilder("docker", "rm", "ttp").start();
    	Process rmTpm2ds = new ProcessBuilder("docker", "rm", "tpm2ds").start();
    	Process rmTpm2dc = new ProcessBuilder("docker", "rm", "tpm2dc").start();
    	
    	socketServer.delete();
    	socketClient.delete();
    	server.stop();
        server.destroy();  
    }
    
    private String getInputAsString(InputStream is) {
       try(java.util.Scanner s = new java.util.Scanner(is))  { 
           return s.useDelimiter("\\A").hasNext() ? s.next() : ""; 
       }
    }

    @Test
    public void testTwoRoutes() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(TEST_MESSAGE);

        template.sendBody("direct:input", TEST_MESSAGE);

        mock.assertIsSatisfied();
    }

    @Test
    public void testTwoRoutesRestartConsumer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(TEST_MESSAGE);

        template.sendBody("direct:input", TEST_MESSAGE);

        mock.assertIsSatisfied();

        resetMocks();

        log.info("Restarting bar route");
        context.stopRoute("bar");
        Thread.sleep(500);
        context.startRoute("bar");

        mock.expectedBodiesReceived(TEST_MESSAGE);

        template.sendBody("direct:input", TEST_MESSAGE);

        mock.assertIsSatisfied();
    }
    
    private static SSLContextParameters defineClientSSLContextClientParameters() {

        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource("jsse/localhost.ks");
        ksp.setPassword(PWD);

        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyPassword(PWD);
        kmp.setKeyStore(ksp);

        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setKeyStore(ksp);

        // NOTE: Needed since the client uses a loose trust configuration when no ssl context
        // is provided.  We turn on WANT client-auth to prefer using authentication
        SSLContextServerParameters scsp = new SSLContextServerParameters();
        scsp.setClientAuthentication(ClientAuthentication.NONE.name());	//TODO CHANGE TO REQUIRE

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setKeyManagers(kmp);
        sslContextParameters.setTrustManagers(tmp);
        sslContextParameters.setServerParameters(scsp);

        return sslContextParameters;
    }
    
    private static SSLContextParameters defineServerSSLContextParameters() {
    	   KeyStoreParameters ksp = new KeyStoreParameters();
           // ksp.setResource(this.getClass().getClassLoader().getResource("jsse/localhost.ks").toString());
           ksp.setResource("jsse/localhost.ks");
           ksp.setPassword(PWD);

           KeyManagersParameters kmp = new KeyManagersParameters();
           kmp.setKeyPassword(PWD);
           kmp.setKeyStore(ksp);

           TrustManagersParameters tmp = new TrustManagersParameters();
           tmp.setKeyStore(ksp);

           // NOTE: Needed since the client uses a loose trust configuration when no ssl context
           // is provided.  We turn on WANT client-auth to prefer using authentication
           SSLContextServerParameters scsp = new SSLContextServerParameters();
           scsp.setClientAuthentication(ClientAuthentication.NONE.name());

           SSLContextParameters sslContextParameters = new SSLContextParameters();
           sslContextParameters.setKeyManagers(kmp);
           sslContextParameters.setTrustManagers(tmp);
           sslContextParameters.setServerParameters(scsp);

           return sslContextParameters;
    }
    
    @Override
    protected RouteBuilder[] createRouteBuilders() throws Exception {
        RouteBuilder[] rbs = new RouteBuilder[2];
        
        // An IDS consumer
        rbs[0] = new RouteBuilder() {
            public void configure() {
        		
            	// Needed to configure TLS on the client side
		        WsComponent wsComponent = (WsComponent) context.getComponent("idsclientplain");
//		        wsComponent.setSslContextParameters(defineClientSSLContextClientParameters());

		        from("direct:input").routeId("foo")
                	.log(">>> Message from direct to WebSocket Client : ${body}")
                	.to("idsclientplain://localhost:9292/echo")
                    .log(">>> Message from WebSocket Client to server: ${body}");
                }
        };
        
        // An IDS provider
        rbs[1] = new RouteBuilder() {
            public void configure() {
            	
            		// Needed to configure TLS on the server side
            		WebsocketComponent websocketComponent = (WebsocketComponent) context.getComponent("idsserver");
//					websocketComponent.setSslContextParameters(defineServerSSLContextParameters());

					// This route is set to use TLS, referring to the parameters set above
                    from("idsserver:localhost:9292/echo")
                    .log(">>> Message from WebSocket Server to mock: ${body}")
                	.to("mock:result");
            }
        };
        return rbs;
    }
}
