/*
 * Copyright 2010 Virgo Systems Kft.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package hu.virgo.projects.iwiw.opensocial.example.webapp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.oauth.OAuth;
import net.oauth.OAuthAccessor;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthMessage;
import net.oauth.ParameterStyle;
import net.oauth.client.OAuthResponseMessage;
import net.oauth.example.consumer.webapp.CookieConsumer;
import net.oauth.http.HttpMessage;
import net.oauth.server.HttpRequestMessage;

import org.opensocial.Client;
import org.opensocial.Response;
import org.opensocial.auth.AuthScheme;
import org.opensocial.auth.OAuth2LeggedScheme;
import org.opensocial.auth.OAuth3LeggedScheme;
import org.opensocial.auth.OAuth3LeggedScheme.Token;
import org.opensocial.models.Activity;
import org.opensocial.models.MediaItem;
import org.opensocial.models.Model;
import org.opensocial.models.Person;
import org.opensocial.providers.Provider;
import org.opensocial.services.ActivitiesService;
import org.opensocial.services.PeopleService;

public class IWiWConsumer extends HttpServlet {
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doGet(req, resp);
	}
    
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        OAuthConsumer consumer = null;
        String url = request.getParameter("url") != null ? request.getParameter("url") : "people/me/@self";
        String httpMethod = request.getParameter("httpMethod") != null ? request.getParameter("httpMethod") : "GET";
        String api = request.getParameter("api");
        try {
            consumer = CookieConsumer.getConsumer("iwiw", getServletContext());
            OAuthAccessor accessor;
            Client os;
            String restBase;
            if("social".equals(api)) {
            	//Social API - 2 labas oauth
            	accessor = new OAuthAccessor(consumer);
            	restBase = consumer.getProperty("serviceProvider.baseURL") + "social/rest/";
            	os = getOSClient(accessor, request.getParameter("xoauth_requestor_id"), restBase);
            } else {
            	//Connect API - 3 labas oauth
            	accessor = CookieConsumer.getAccessor(request, response, consumer);            	
            	restBase = consumer.getProperty("serviceProvider.baseURL") + "social/connect/rest/";
            	os = getOSClient(accessor, null, restBase);
            }
            
            request.setAttribute("token", accessor.accessToken);
            
            
            List<Person> people = null;
            Person person = null;
            String responseBody = null;
            
            //altalanos JSON REST
            if(request.getParameter("rest") != null) {
            	Collection<OAuth.Parameter> parameters = HttpRequestMessage.getParameters(request);
            	if("social".equals(api)) {
            		parameters.add(new OAuth.Parameter("xoauth_requestor_id",request.getParameter("xoauth_requestor_id")));
            	}
            	OAuthMessage message;
            	if("post".equalsIgnoreCase(httpMethod) || "put".equalsIgnoreCase(httpMethod)) {
            		message = accessor.newRequestMessage(
            				httpMethod, 
            				restBase + url, 
            				parameters, 
            				new ByteArrayInputStream(request.getParameter("requestBody").getBytes()));
           			message.getHeaders().add(new OAuth.Parameter(HttpMessage.CONTENT_TYPE, "application/json"));
            	} else {
            		message = accessor.newRequestMessage(httpMethod, restBase + url, parameters);
            	}
	            
	            // response HTTP status code feldolgozasa
	            OAuthResponseMessage iWiWOAuthResponseMessage = CookieConsumer.CLIENT.access(message, ParameterStyle.AUTHORIZATION_HEADER);
	            
	            int httpStatusCode =  iWiWOAuthResponseMessage.getHttpResponse().getStatusCode();
	            request.setAttribute("statusCode", httpStatusCode);
	            if( httpStatusCode / 200 == 1 ) {
	            	responseBody = iWiWOAuthResponseMessage.readBodyAsString();
	            }
	         
	        //fetchPerson	            
            } else if(request.getParameter("fetchPerson") != null){
            	person = os.send(PeopleService.getViewer()).getEntry();
	        //fetchFriends
            } else if(request.getParameter("fetchFriends") != null){
            	people = os.send(PeopleService.getFriends()).getEntries();
            //createActivity
            } else if(request.getParameter("createActivity") != null) {
            	Activity activity = new Activity();
            	activity.setTitle(request.getParameter("title"));
            	activity.setBody(request.getParameter("body"));
            	Response osResponse = os.send(ActivitiesService.createActivity(activity));
            	responseBody = osResponse.<Model>getEntry() != null ? osResponse.<Model>getEntry().toJSONString() : null;
        	//createActivity2            	
            } else if(request.getParameter("createActivity2") != null) {
            	Activity activity = new Activity();
            	activity.setTitle(request.getParameter("title"));
            	activity.setBody(request.getParameter("body"));
            	MediaItem mediaItem = new MediaItem();
            	//mediaItem.setCaption(caption);
            	mediaItem.setField("title",request.getParameter("mediaitem_title"));
            	mediaItem.setUrl(request.getParameter("mediaitem_url"));
            	mediaItem.setField("thumbnailUrl",request.getParameter("mediaitem_thumbnailUrl"));
            	activity.setField("mediaItems", Collections.singletonList(mediaItem));
            	Response osResponse = os.send(ActivitiesService.createActivity(activity)); 
            	responseBody = osResponse.<Model>getEntry() != null ? osResponse.<Model>getEntry().toJSONString() : null;
            }
            
            request.setAttribute("people", people);
            request.setAttribute("person", person);
            request.setAttribute("responseBody", responseBody);
            request.getRequestDispatcher("/iwiw.jsp").forward(request, response);
        } catch (Exception e) {
            CookieConsumer.handleException(e, request, response, consumer);
        }
    }

    private Client getOSClient(OAuthAccessor accessor, String requestorId, String restBase) {    	
    	Provider provider = new Provider();
    	provider.setRestEndpoint(restBase);
    	provider.setVersion("0.9");
    	/*
		accessor.consumer.serviceProvider.requestTokenURL
		accessor.consumer.serviceProvider.userAuthorizationURL
		accessor.consumer.serviceProvider.accessTokenURL
		*/ 
    	AuthScheme auth;
    	if(requestorId == null) {
        	OAuth3LeggedScheme oauthScheme = new OAuth3LeggedScheme(
        			provider, 
        			accessor.consumer.consumerKey, 
        			accessor.consumer.consumerSecret);
        	oauthScheme.setAccessToken(new Token(accessor.accessToken, accessor.tokenSecret));
        	auth = oauthScheme;
    	} else {
        	OAuth2LeggedScheme oauthScheme = new OAuth2LeggedScheme(
        			accessor.consumer.consumerKey, 
        			accessor.consumer.consumerSecret, requestorId);
        	auth = oauthScheme;
    	}

    	Client client = new Client(provider, auth);
    	return client;
    }

    private static final long serialVersionUID = 1L;

}
