package fr.ippon.wip.http.hc;

import fr.ippon.wip.http.HttpExecutor;
import fr.ippon.wip.http.Request;
import fr.ippon.wip.http.Response;
import fr.ippon.wip.state.PortletWindow;
import org.apache.http.*;
import org.apache.http.auth.*;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import javax.portlet.MimeResponse;
import javax.portlet.PortletRequest;
import javax.portlet.PortletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


/**
 * This class is unique entry point for executing request with Apache HttpComponents
 * It is entirely stateless and thread-safe
 *
 * @author François Prot
 */
public class HttpClientExecutor implements HttpExecutor {

    /**
     * Send an HTTP request to the remote site and process the returned HTTP response
     * This method:
     * <ul>
     * <li>creates an org.apache.http.HttpRequest</li>
     * <li>executes it using instances of HttpClient and HttpContext provides by HttpClientResourceManager</li>
     * <li>converts the resulting org.apache.http.HttpResponse to a fr.ippon.wip.http.Response</li>
     * </ul>
     *
     * @param request         Contains all the data needed to create an org.apache.http.HttpRequest
     * @param portletRequest  Gives access to javax.portlet.PortletSession and windowID
     * @param portletResponse Used to create PortletURL if instance of MimeResponse
     * @return The returned Response instance can reference an InputStream linked to a connection from an HttpClient pool
     *         It necessary to call either Response#dispose, Response#printResponseContent or Response#sendResponse
     *         to release the underlying HTTP connection.
     * @throws IOException
     */
    public Response execute(Request request, PortletRequest portletRequest, PortletResponse portletResponse) throws IOException {
        Response response = null;
        HttpClientResourceManager resourceManager = HttpClientResourceManager.getInstance();
        try {
            // Get Apache HttpComponents resources from ResourceManager
            HttpClient client = resourceManager.getHttpClient(portletRequest);
            HttpContext context = resourceManager.initExecutionContext(portletRequest, portletResponse, request);
            HttpUriRequest httpRequest;
            // Create HttpRequest object
            if (request.getHttpMethod() == Request.HttpMethod.POST) {
                httpRequest = createPostRequest(request);
            } else {
                httpRequest = createGetRequest(request);
            }

            // Execute the request
            HttpResponse httpResponse = null;
            try {
                httpResponse = client.execute(httpRequest, context);

                // Check if authentication is requested by remote host
                PortletWindow portletWindow = PortletWindow.getInstance(portletRequest);
                int statusCode = httpResponse.getStatusLine().getStatusCode();
                List<String> schemes;
                if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
                    // Check what authentication scheme are required
                    schemes = new ArrayList<String>();
                    for (Header authHeader : httpResponse.getHeaders(HttpHeaders.WWW_AUTHENTICATE)) {
                        String headerValue = authHeader.getValue();
                        schemes.add(headerValue.split(" ")[0]);
                    }
                    portletWindow.setRequestedAuthSchemes(schemes);
                } else {
                    portletWindow.setRequestedAuthSchemes(null);
                }

                // Updates authentication state
                AuthState authState = (AuthState) context.getAttribute(ClientContext.TARGET_AUTH_STATE);
                portletWindow.setAuthenticated(authState != null & authState.getCredentials() != null);

                // Get final URL (ie. perhaps redirected)
                HttpUriRequest actualRequest = (HttpUriRequest) context.getAttribute(
                        ExecutionContext.HTTP_REQUEST);
                HttpHost actualHost = (HttpHost) context.getAttribute(
                        ExecutionContext.HTTP_TARGET_HOST);
                String actualUrl = (actualRequest.getURI().isAbsolute()) ? actualRequest.getURI().toString() : (actualHost.toURI() + actualRequest.getURI());

                // Create Response object from HttpResponse
                response = createResponse(httpResponse, actualUrl, portletResponse instanceof MimeResponse);
            } catch (RuntimeException rte) {
                if (httpResponse != null && httpResponse.getEntity() != null) {
                    EntityUtils.consume(httpResponse.getEntity());
                }
                throw rte;
            }
        } finally {
            resourceManager.releaseThreadResources();
        }
        return response;
    }

    /**
     * This method updates the CredentialsProvider associated to the current PortletSession
     * and the windowID with the provided login and password.
     * Basic and NTLM authentication schemes are supported. This method uses the current
     * fr.ippon.wip.state.PortletWindow to retrieve the authentication schemes requested by remote server.
     *
     * @param login
     * @param password
     * @param portletRequest Used to get current javax.portlet.PortletSession and windowID
     */
    public void login(String login, String password, PortletRequest portletRequest) {
        HttpClientResourceManager resourceManager = HttpClientResourceManager.getInstance();
        CredentialsProvider credentialsProvider = resourceManager.getCredentialsProvider(portletRequest);
        PortletWindow portletWindow = PortletWindow.getInstance(portletRequest);
        List<String> schemes = portletWindow.getRequestedAuthSchemes();

        if (schemes.contains("Basic")) {
            // Creating basic credentials
            AuthScope scope = new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, AuthScope.ANY_REALM, "Basic");
            Credentials credentials = new UsernamePasswordCredentials(login, password);
            credentialsProvider.setCredentials(scope, credentials);
        }
        if (schemes.contains("NTLM")) {
            // Creating ntlm credentials
            AuthScope scope = new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, AuthScope.ANY_REALM, "NTLM");
            Credentials credentials = new NTCredentials(login, password, "", "");
            credentialsProvider.setCredentials(scope, credentials);
        }
    }

    /**
     * This method clears all credentials from the CredentialsProvider associated to the current PortletSession
     * and the windowID.
     * The current fr.ippon.wip.state.PortletWindow is also deleted.
     *
     * @param portletRequest Used to get current javax.portlet.PortletSession and windowID
     */
    public void logout(PortletRequest portletRequest) {
        HttpClientResourceManager resourceManager = HttpClientResourceManager.getInstance();
        CredentialsProvider credentialsProvider = resourceManager.getCredentialsProvider(portletRequest);

        // Clear credentials
        credentialsProvider.clear();

        // Clear state
        PortletWindow.clearInstance(portletRequest);
    }

    /**
     * This method must be executed on portlet undeploy to release all Apache HttpComponents resources.
     */
    public void destroy() {
        HttpClientResourceManager.getInstance().releaseGlobalResources();
    }

    private HttpUriRequest createPostRequest(Request request) {
        // TODO: manage Content-Type:multipart/form-data
        // Create Post request and set parameters if any
        HttpPost postRequest = new HttpPost(request.getRequestedURL());
        Map<String, String[]> paramMap = request.getParameterMap();

        if (paramMap != null) {
            List<NameValuePair> httpParams = new LinkedList<NameValuePair>();
            for (Map.Entry<String, String[]> entry : paramMap.entrySet()) {
                for (String value : entry.getValue()) {
                    httpParams.add(new BasicNameValuePair(entry.getKey(), value));
                }
            }
            HttpEntity formEntity = new UrlEncodedFormEntity(httpParams, ContentType.APPLICATION_FORM_URLENCODED.getCharset());
            postRequest.setEntity(formEntity);
        }

        return postRequest;
    }

    private HttpUriRequest createGetRequest(Request request) {
        return new HttpGet(request.getRequestedURL());
    }

    private Response createResponse(HttpResponse httpResponse, String url, boolean portalUrlComputed) throws IOException {
        // Create Response object from HttpResponse
        ContentType contentType = ContentType.getOrDefault(httpResponse.getEntity());
        Charset charset = contentType.getCharset();
        String mimeType = contentType.toString();
        if (mimeType.contains(";")) {
            mimeType = mimeType.substring(0, mimeType.indexOf(";"));
        }
        int statusCode = httpResponse.getStatusLine().getStatusCode();
        InputStream content = httpResponse.getEntity().getContent();

        return new Response(content, charset, mimeType, url, statusCode, portalUrlComputed);
    }
}