package com.tianzhu.identity.uaa.login;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import com.tianzhu.identity.uaa.mfa.*;
import com.tianzhu.identity.uaa.mock.InjectedMockContextTest;
import com.tianzhu.identity.uaa.mock.util.MockMvcUtils;
import com.tianzhu.identity.uaa.oauth.client.ClientDetailsModification;
import com.tianzhu.identity.uaa.scim.ScimUser;
import com.tianzhu.identity.uaa.scim.ScimUserProvisioning;
import com.tianzhu.identity.uaa.util.JsonUtils;
import com.tianzhu.identity.uaa.zone.IdentityZone;
import com.tianzhu.identity.uaa.zone.IdentityZoneConfiguration;
import com.tianzhu.identity.uaa.zone.IdentityZoneHolder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.common.util.RandomValueStringGenerator;
import org.springframework.security.oauth2.provider.client.BaseClientDetails;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.tianzhu.identity.uaa.mock.util.MockMvcUtils.CookieCsrfPostProcessor.cookieCsrf;
import static org.junit.Assert.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class TotpEndpointMockMvcTests extends InjectedMockContextTest{

    private String adminToken;
    private JdbcUserGoogleMfaCredentialsProvisioning jdbcUserGoogleMfaCredentialsProvisioning;
    private IdentityZoneConfiguration uaaZoneConfig;
    private MfaProvider mfaProvider;
    private MfaProvider otherMfaProvider;
    private String password;
    private UserGoogleMfaCredentialsProvisioning userGoogleMfaCredentialsProvisioning;
    private ScimUser user;
    private MockHttpSession session;

    @Before
    public void setup() throws Exception {
        adminToken = testClient.getClientCredentialsOAuthAccessToken("admin", "adminsecret",
                "clients.read clients.write clients.secret clients.admin uaa.admin");
        jdbcUserGoogleMfaCredentialsProvisioning = (JdbcUserGoogleMfaCredentialsProvisioning) getWebApplicationContext().getBean("jdbcUserGoogleMfaCredentialsProvisioning");
        userGoogleMfaCredentialsProvisioning = (UserGoogleMfaCredentialsProvisioning) getWebApplicationContext().getBean("userGoogleMfaCredentialsProvisioning");

        mfaProvider = new MfaProvider();
        mfaProvider.setName(new RandomValueStringGenerator(5).generate());
        mfaProvider.setType(MfaProvider.MfaProviderType.GOOGLE_AUTHENTICATOR);
        mfaProvider.setIdentityZoneId("uaa");
        mfaProvider.setConfig(new GoogleMfaProviderConfig());
        mfaProvider = JsonUtils.readValue(getMockMvc().perform(
                post("/mfa-providers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(JsonUtils.writeValueAsString(mfaProvider)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse().getContentAsByteArray(), MfaProvider.class);

        otherMfaProvider = new MfaProvider();
        otherMfaProvider.setName(new RandomValueStringGenerator(5).generate());
        otherMfaProvider.setType(MfaProvider.MfaProviderType.GOOGLE_AUTHENTICATOR);
        otherMfaProvider.setIdentityZoneId("uaa");
        otherMfaProvider.setConfig(new GoogleMfaProviderConfig());
        otherMfaProvider = JsonUtils.readValue(getMockMvc().perform(
            post("/mfa-providers")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(APPLICATION_JSON)
                .content(JsonUtils.writeValueAsString(otherMfaProvider)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse().getContentAsByteArray(), MfaProvider.class);


        uaaZoneConfig = MockMvcUtils.getZoneConfiguration(getWebApplicationContext(), "uaa");
        uaaZoneConfig.getMfaConfig().setEnabled(true).setProviderName(mfaProvider.getName());
        MockMvcUtils.setZoneConfiguration(getWebApplicationContext(), "uaa", uaaZoneConfig);

        user = createUser();
        session = new MockHttpSession();
    }

    @After
    public void cleanup () throws Exception {
        uaaZoneConfig.getMfaConfig().setEnabled(false).setProviderName(null);
        MockMvcUtils.setZoneConfiguration(getWebApplicationContext(), "uaa", uaaZoneConfig);
    }

    @Test
    public void testRedirectToMfaAfterLogin() throws Exception {
        redirectToMFARegistration();

        MockHttpServletResponse response = getMockMvc().perform(get("/profile")
                .session(session)).andReturn().getResponse();
        assertTrue(response.getRedirectedUrl().contains("/login"));
    }

    @Test
    public void testGoogleAuthenticatorLoginFlow() throws Exception {
        redirectToMFARegistration();

        performGetMfaRegister().andExpect(view().name("mfa/qr_code"));

        assertFalse(userGoogleMfaCredentialsProvisioning.activeUserCredentialExists(user.getId(), mfaProvider.getId()));

        int code = getMFACodeFromSession();

        MockHttpServletResponse response = performPostVerifyWithCode(code).andReturn().getResponse();
        assertEquals("/", response.getRedirectedUrl());

        getMockMvc().perform(get("/")
                .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("home"));

        getMockMvc().perform(get("/logout.do")).andReturn();

        session = new MockHttpSession();
        performLoginWithSession();
        performPostVerifyWithCode(code)
            .andExpect(view().name("home"));
    }

    @Test
    public void testMFARegistrationHonorsRedirectUri() throws Exception {
        ClientDetailsModification client = MockMvcUtils.utils()
                .getClientDetailsModification("auth-client-id", "secret",
                        Collections.emptyList(), Arrays.asList("openid"), Arrays.asList("authorization_code"), "uaa.resource",
                            Collections.singleton("http://example.com"));
        client.setAutoApproveScopes(Arrays.asList("openid"));
        Map<String, String> information = new HashMap<>();
        information.put("autoapprove", "true");
        client.setAdditionalInformation(information);

        BaseClientDetails authcodeClient = MockMvcUtils.utils().createClient(getMockMvc(),adminToken, client, IdentityZone.getUaa(), status().isCreated());

        //Not using param function because params won't end up in paramsMap.
        String oauthUrl = "/oauth/authorize?client_id=auth-client-id&client_secret=secret&redirect_uri=http://example.com";
        getMockMvc().perform(get(oauthUrl)
                .session(session)
                .with(cookieCsrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));

        performLoginWithSession().andExpect(redirectedUrl("/login/mfa/register"));
        performGetMfaRegister();

        int code = getMFACodeFromSession();
        performPostVerifyWithCode(code)
                .andExpect(redirectedUrl("http://localhost/oauth/authorize?client_id=auth-client-id&client_secret=secret&redirect_uri=http://example.com"));
    }

    @Test
    public void testQRCodeCannotBeSubmittedWithoutLoggedInSession() throws Exception {
        getMockMvc().perform(post("/login/mfa/verify.do")
                    .param("code", "1234")
                    .with(cookieCsrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    public void testQRCodeRedirectIfCodeValidated()  throws Exception {

        redirectToMFARegistration();

        performGetMfaRegister().andExpect(view().name("mfa/qr_code"));

        int code = getMFACodeFromSession();

        performPostVerifyWithCode(code)
            .andExpect(view().name("home"));

        UserGoogleMfaCredentials activeCreds = jdbcUserGoogleMfaCredentialsProvisioning.retrieve(user.getId(), mfaProvider.getId());
        assertNotNull(activeCreds);
        assertEquals(mfaProvider.getId(), activeCreds.getMfaProviderId());
        getMockMvc().perform(get("/logout.do")).andReturn();

        session = new MockHttpSession();
        performLoginWithSession();

        performGetMfaRegister().andExpect(redirectedUrl("/uaa/login/mfa/verify"));
    }

    @Test
    public void testRegisterFlowWithMfaProviderSwitch()  throws Exception {

        redirectToMFARegistration();

        performGetMfaRegister().andExpect(view().name("mfa/qr_code"));

        int code = getMFACodeFromSession();

        performPostVerifyWithCode(code)
            .andExpect(view().name("home"));

        UserGoogleMfaCredentials activeCreds = jdbcUserGoogleMfaCredentialsProvisioning.retrieve(user.getId(), mfaProvider.getId());
        assertNotNull(activeCreds);
        assertEquals(mfaProvider.getId(), activeCreds.getMfaProviderId());
        getMockMvc().perform(get("/logout.do")).andReturn();

        uaaZoneConfig = MockMvcUtils.getZoneConfiguration(getWebApplicationContext(), "uaa");
        uaaZoneConfig.getMfaConfig().setProviderName(otherMfaProvider.getName());
        MockMvcUtils.setZoneConfiguration(getWebApplicationContext(), "uaa", uaaZoneConfig);

        session = new MockHttpSession();
        performLoginWithSession();

        performGetMfaRegister().andExpect(view().name("mfa/qr_code"));

        code = getMFACodeFromSession();

        performPostVerifyWithCode(code)
            .andExpect(view().name("home"));
    }

    @Test
    public void testQRCodeRedirectIfCodeNotValidated()  throws Exception {
        redirectToMFARegistration();

        performGetMfaRegister().andExpect(view().name("mfa/qr_code"));

        UserGoogleMfaCredentials inActiveCreds = (UserGoogleMfaCredentials) session.getAttribute("SESSION_USER_GOOGLE_MFA_CREDENTIALS");
        assertNotNull(inActiveCreds);

        performGetMfaRegister().andExpect(view().name("mfa/qr_code"));
    }

    private ScimUser createUser() throws Exception{
        ScimUser user = new ScimUser(null, new RandomValueStringGenerator(5).generate(), "first", "last");

        password = "sec3Tas";
        user.setPrimaryEmail(user.getUserName());
        user.setPassword(password);
        user = getWebApplicationContext().getBean(ScimUserProvisioning.class).createUser(user, user.getPassword(), IdentityZoneHolder.getUaaZone().getId());
        return user;
    }

    private ResultActions performLoginWithSession() throws Exception {
        return getMockMvc().perform( post("/login.do")
            .session(session)
            .param("username", user.getUserName())
            .param("password", password)
            .with(cookieCsrf()))
            .andDo(print())
            .andExpect(status().isFound());
    }

    private ResultActions performPostVerifyWithCode(int code) throws Exception {
        return getMockMvc().perform(post("/login/mfa/verify.do")
            .param("code", Integer.toString(code))
            .session(session)
            .with(cookieCsrf()))
            .andExpect(status().is3xxRedirection());
    }

    private ResultActions performGetMfaRegister() throws Exception {
        return getMockMvc().perform(get("/uaa/login/mfa/register")
            .session(session)
            .contextPath("/uaa"));
    }

    private void redirectToMFARegistration() throws Exception {
        performLoginWithSession()
                .andExpect(redirectedUrl("/login/mfa/register"));
    }

    private int getMFACodeFromSession() {
        UserGoogleMfaCredentials activeCreds = (UserGoogleMfaCredentials) session.getAttribute("SESSION_USER_GOOGLE_MFA_CREDENTIALS");
        GoogleAuthenticator authenticator = new GoogleAuthenticator(new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder().build());
        return authenticator.getTotpPassword(activeCreds.getSecretKey());
    }
}
