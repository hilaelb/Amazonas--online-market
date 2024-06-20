package com.amazonas.frontend.control;

import com.amazonas.common.requests.Request;
import com.amazonas.common.requests.RequestBuilder;
import com.amazonas.common.requests.auth.AuthenticationRequest;
import com.amazonas.common.requests.users.LoginRequest;
import com.amazonas.common.requests.users.RegisterRequest;
import com.amazonas.common.utils.APIFetcher;
import com.amazonas.common.utils.Response;
import com.amazonas.frontend.exceptions.ApplicationException;
import com.google.gson.JsonSyntaxException;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Base64;
import java.util.List;

@Component("appController")
public class AppController {

    private static final String BACKEND_URI = "https://localhost:8443/";

    public String getWelcomeMessage() {
        return "Welcome to my app!";
    }

    public String getExampleMessage(int num) {
        return "Example " + num;
    }

    // ==================================================================================== |
    // ============================= AUTHENTICATION METHODS =============================== |
    // ==================================================================================== |

    public boolean enterAsGuest() {
        if(isUserLoggedIn() || isGuestLoggedIn()) {
            return false;
        }

        String token, userId;
        try {
            userId = (String) getByEndpoint(Endpoints.ENTER_AS_GUEST).getFirst();
            AuthenticationRequest request =  new AuthenticationRequest(userId, null);
            token = (String) postByEndpoint(Endpoints.AUTHENTICATE_GUEST, request).getFirst();
        } catch (ApplicationException e) {
            return false;
        }
        // ---------> logged in as guest
        setCurrentUserId(userId);
        setGuestLoggedIn(true);
        setToken(token);
        return true;
    }

    public boolean login(String userId, String password) {
        if(isUserLoggedIn()){
            return false;
        }

        String credentialsString = "%s:%s".formatted(userId, password);
        String auth = "Basic " + new String(Base64.getEncoder().encode(credentialsString.getBytes()));

        String body,token, guestId = getCurrentUserId();
        Response authResponse, loginResponse;
        try {
            body = RequestBuilder.create()
                    .withPayload(new AuthenticationRequest(userId, password))
                    .build()
                    .toJson();
            String fetched = APIFetcher.create()
                    .withUri(BACKEND_URI + Endpoints.AUTHENTICATE_USER.location())
                    .withHeader("Authorization", auth)
                    .withBody(body)
                    .withPost()
                    .fetch();
            authResponse = Response.fromJson(fetched);
            if(authResponse == null || ! authResponse.success()){
                return false;
            }
            // ---------> passed authentication
            token = authResponse.<String>payload(String.class).getFirst();
            body = RequestBuilder.create()
                    .withUserId(userId)
                    .withToken(token)
                    .withPayload(new LoginRequest(guestId, userId))
                    .build()
                    .toJson();
            fetched = APIFetcher.create()
                    .withUri(BACKEND_URI + Endpoints.LOGIN_TO_REGISTERED.location())
                    .withHeader("Authorization", getBearerAuth())
                    .withBody(body)
                    .withPost()
                    .fetch();
            loginResponse = Response.fromJson(fetched);
            if(loginResponse == null || ! loginResponse.success()){
                return false;
            }
            // ---------> logged in
        } catch (IOException | InterruptedException | JsonSyntaxException e) {
            return false;
        }
        setCurrentUserId(userId);
        setToken(token);
        setUserLoggedIn(true);
        setGuestLoggedIn(false);
        return true;
    }

    public boolean register(String email, String username, String password, String confirmPassword) {
        if(isUserLoggedIn()){
            return false;
        }

        if (!password.equals(confirmPassword)) {
            return false;
        }

        RegisterRequest request = new RegisterRequest(email, username, password);
        try {
            postByEndpoint(Endpoints.REGISTER_USER, request);
        } catch (ApplicationException e) {
            return false;
        }
        return true;
    }

    public boolean logout() {
        if(!isUserLoggedIn()){
            return false;
        }
        try {
             postByEndpoint(Endpoints.LOGOUT, null);
        } catch (ApplicationException e) {
            return false;
        }
        return true;
    }

    public boolean logoutAsGuest() {
        if(!isGuestLoggedIn()){
            return false;
        }
        try {
            postByEndpoint(Endpoints.LOGOUT_AS_GUEST, null);
        } catch (ApplicationException e) {
            return false;
        }
        return true;
    }
    
    // ==================================================================================== |
    // ============================= API FETCHING METHODS ================================= |
    // ==================================================================================== |

    public <T> List<T> getByEndpoint(Endpoints endpoint) throws ApplicationException {
        return get(endpoint.location(), endpoint.clazz());
    }

    public <T> List<T> postByEndpoint(Endpoints endpoint, Object payload) throws ApplicationException {
        return post(endpoint.location(),endpoint.clazz() ,payload);
    }

    private <T> List<T> get(String location, Type t) throws ApplicationException {
        ApplicationException fetchFailed = new ApplicationException("Failed to fetch data");

        Response response;
        try {
            String fetched = APIFetcher.create()
                    .withUri(BACKEND_URI + location)
                    .withHeader("Authorization", getBearerAuth())
                    .fetch();
            response = Response.fromJson(fetched);
        } catch (IOException | InterruptedException | JsonSyntaxException e) {
            throw fetchFailed;
        }

        if (response == null) {
            throw fetchFailed;
        }
        if (!response.success()) {
            throw new ApplicationException(response.message());
        }
        return response.payload(t);
    }

    private <T> List<T> post(String location, Type t, Object payload) throws ApplicationException {
        ApplicationException postFailed = new ApplicationException("Failed to send data");

        String body = RequestBuilder.create()
                .withUserId(getCurrentUserId())
                .withToken(getToken())
                .withPayload(payload)
                .build()
                .toJson();

        Response response;
        try {
            String fetched = APIFetcher.create()
                    .withUri(BACKEND_URI + location)
                    .withHeader("Authorization", getBearerAuth())
                    .withBody(body)
                    .withPost()
                    .fetch();
            response = Response.fromJson(fetched);
        } catch (IOException | InterruptedException | JsonSyntaxException e) {
            throw postFailed;
        }

        if (response == null) {
            throw postFailed;
        }
        if (!response.success()) {
            throw new ApplicationException(response.message());
        }
        return response.payload(t);
    }

    private String getBearerAuth() {
        String token = getToken();
        if (token == null){
            return "";
        }
        return "Bearer " + token;
    }

    // ==================================================================================== |
    // ===========================  SESSION FUNCTIONS ===================================== |
    // ==================================================================================== |

    public static boolean isUserLoggedIn() {
        Boolean isUserLoggedIn = getSessionsAttribute("isUserLoggedIn");
        return isUserLoggedIn != null && isUserLoggedIn;
    }

    public static void setUserLoggedIn(boolean value) {
        getSession().setAttribute("isUserLoggedIn", value);
    }

    public static boolean isGuestLoggedIn() {
        Boolean isGuestLoggedIn = getSessionsAttribute("isGuestLoggedIn");
        return isGuestLoggedIn != null && isGuestLoggedIn;
    }

    public static void setGuestLoggedIn(boolean value) {
        getSession().setAttribute("isGuestLoggedIn", value);
    }

    public static String getCurrentUserId(){
        return getSessionsAttribute("userId");
    }

    public static void setCurrentUserId(String userId){
        setSessionsAttribute("userId", userId);
    }

    public static String getToken(){
        return getSessionsAttribute("token");
    }

    public static void setToken(String token){
        setSessionsAttribute("token", token);
    }

    public static void clearSession(){
        getSession().invalidate();
    }

    public void addSessionDestroyListener(){
        Boolean sessionDestroyListener = getSessionsAttribute("sessionDestroyListener");
        if(sessionDestroyListener != null && sessionDestroyListener){
            return;
        }
        VaadinService service = VaadinService.getCurrent();
        service.addSessionDestroyListener(event -> {
            VaadinSession session = event.getSession();
            Boolean isUserLoggedIn = (Boolean) session.getAttribute("isUserLoggedIn");
            Boolean isGuestLoggedIn = (Boolean) session.getAttribute("isGuestLoggedIn");
            String userId = (String) session.getAttribute("userId");
            String token = (String) session.getAttribute("token");
            Request request = RequestBuilder.create()
                    .withUserId(userId)
                    .withToken(token)
                    .build();
            APIFetcher fetcher = APIFetcher.create()
                    .withHeader("Authorization", getBearerAuth())
                    .withBody(request.toJson())
                    .withPost();
            try{
                if(isGuestLoggedIn != null && isGuestLoggedIn){
                    fetcher.withUri(BACKEND_URI + Endpoints.LOGOUT_AS_GUEST.location()).fetch();
                } else if(isUserLoggedIn != null && isUserLoggedIn){
                    fetcher.withUri(BACKEND_URI + Endpoints.LOGOUT.location()).fetch();
                }
            } catch (Exception ignored){}
        });
    }

    private static void setSessionsAttribute(String key, Object value){
        getSession().setAttribute(key, value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T getSessionsAttribute(String key){
        return (T) getSession().getAttribute(key);
    }

    private static WrappedSession getSession() {
        return VaadinService.getCurrentRequest().getWrappedSession();
    }
}
