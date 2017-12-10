package com.tianzhu.identity.uaa.integration;

import com.dumbster.smtp.SimpleSmtpServer;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import com.tianzhu.identity.uaa.ServerRunning;
import com.tianzhu.identity.uaa.integration.feature.DefaultIntegrationTestConfig;
import com.tianzhu.identity.uaa.integration.feature.TestClient;
import com.tianzhu.identity.uaa.integration.util.IntegrationTestUtils;
import com.tianzhu.identity.uaa.mfa.MfaProvider;
import com.tianzhu.identity.uaa.mock.util.MockMvcUtils;
import com.tianzhu.identity.uaa.scim.ScimUser;
import com.tianzhu.identity.uaa.zone.IdentityZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.test.TestAccounts;
import org.springframework.security.oauth2.client.token.grant.client.ClientCredentialsResourceDetails;
import org.springframework.security.oauth2.common.util.RandomValueStringGenerator;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = DefaultIntegrationTestConfig.class)
public class TotpEndpointIntegrationTests {

    @Autowired
    WebDriver webDriver;

    @Value("${integration.test.base_url}")
    String baseUrl;

    @Autowired
    TestAccounts testAccounts;

    @Autowired
    SimpleSmtpServer simpleSmtpServer;

    @Autowired
    TestClient testClient;

    private static final String USER_PASSWORD = "sec3Tas";

    @Rule
    public ServerRunning serverRunning = ServerRunning.isRunning();
    private IdentityZone mfaZone;
    private RestTemplate adminClient;
    private String zoneUrl;
    private String username;
    private MfaProvider mfaProvider;

    @Before
    public void setup() throws Exception {
        ClientCredentialsResourceDetails adminResource = IntegrationTestUtils.getClientCredentialsResource(baseUrl, new String[0], "admin", "adminsecret");
        adminClient = IntegrationTestUtils.getClientCredentialsTemplate(
                adminResource);

        mfaZone = IntegrationTestUtils.fixtureIdentityZone("testzone1", "testzone1");
        mfaZone = IntegrationTestUtils.createZoneOrUpdateSubdomain(adminClient, baseUrl, "testzone1", "testzone1");

        zoneUrl = baseUrl.replace("localhost", mfaZone.getSubdomain() + ".localhost");

        String zoneAdminToken = IntegrationTestUtils.getZoneAdminToken(baseUrl, serverRunning, mfaZone.getId());
        username = createRandomUser();
        mfaProvider = enableMfaInZone(zoneAdminToken);
        webDriver.get(zoneUrl + "/logout.do");
    }

    @After
    public void cleanup() {
        webDriver.get(zoneUrl + "/logout.do");
        mfaZone.getConfig().getMfaConfig().setEnabled(false).setProviderName(null);
        IntegrationTestUtils.createZoneOrUpdateSubdomain(adminClient, baseUrl, mfaZone.getId(), mfaZone.getSubdomain(), mfaZone.getConfig());
    }

    @Test
    public void testQRCodeScreen() throws Exception {
        performLogin(username);
        assertEquals(zoneUrl + "/login/mfa/register", webDriver.getCurrentUrl());

        String imageSrc = webDriver.findElement(By.id("qr")).getAttribute("src");

        String[] qparams = qrCodeText(imageSrc).split("\\?")[1].split("&");
        for(String param : qparams) {
            if(param.contains("issuer=")) {
                assertEquals("issuer=" + mfaProvider.getConfig().getIssuer(), URLDecoder.decode(param, "UTF-8"));
                break;
            }
        }
        String secretKey = "";
        for(String param: qparams) {
            String[] keyVal = param.split("=");
            if(keyVal[0].equals("secret")) {
                secretKey = keyVal[1];
                break;
            }
        }

        assertFalse("secret not found", secretKey.isEmpty());

        GoogleAuthenticator authenticator = new GoogleAuthenticator(new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder().build());

        webDriver.findElement(By.id("Next")).click();
        assertEquals(zoneUrl + "/login/mfa/verify", webDriver.getCurrentUrl());

        Integer verificationCode = authenticator.getTotpPassword(secretKey);
        webDriver.findElement(By.name("code")).sendKeys(verificationCode.toString());
        webDriver.findElement(By.cssSelector("form button")).click();

        assertEquals(zoneUrl + "/", webDriver.getCurrentUrl());
    }

    @Test
    public void testMfaRegisterPageWithoutLoggingIn() {
        webDriver.get(zoneUrl + "/logout.do");
        webDriver.get("/login/mfa/register");
        assertEquals(zoneUrl + "/login", webDriver.getCurrentUrl());
    }

    @Test
    public void testMfaVerifyPageWithoutLoggingIn() {
        webDriver.get(zoneUrl + "/logout.do");
        webDriver.get("/login/mfa/verify");
        assertEquals(zoneUrl + "/login", webDriver.getCurrentUrl());
    }

    private String qrCodeText(String dataUrl) throws Exception {
        QRCodeReader reader = new QRCodeReader();
        String[] rawSplit = dataUrl.split(",");
        assertEquals("data:image/png;base64", rawSplit[0]);
        byte[] decodedByte = Base64.getDecoder().decode(rawSplit[1]);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(decodedByte));
        BufferedImageLuminanceSource source = new BufferedImageLuminanceSource(image);
        Map<DecodeHintType, Object> hintMap = new HashMap<>();
        hintMap.put(DecodeHintType.PURE_BARCODE, true);

        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        return reader.decode(bitmap, hintMap).getText();
    }

    @Test
    public void testQRCodeValidation() {
        performLogin(username);
        assertEquals(zoneUrl + "/login/mfa/register", webDriver.getCurrentUrl());

        webDriver.findElement(By.id("Next")).click();
        assertEquals(zoneUrl + "/login/mfa/verify", webDriver.getCurrentUrl());
        webDriver.findElement(By.name("code")).sendKeys("1111111111111111112222");

        webDriver.findElement(By.id("verify_code_btn")).click();
        assertEquals("Incorrect code, please try again.", webDriver.findElement(By.cssSelector("form .error-color")).getText());
    }

    @Test
    public void checkAccessForTotpPage() {
        webDriver.get(zoneUrl + "/logout.do");
        webDriver.get(zoneUrl + "/login/mfa/register");

        assertEquals(zoneUrl + "/login", webDriver.getCurrentUrl());
    }

    @Test
    public void testDisplayIdentityZoneNameOnRegisterPage() {
        performLogin(username);
        assertEquals(zoneUrl + "/login/mfa/register", webDriver.getCurrentUrl());

        assertEquals(webDriver.findElement(By.id("mfa-identity-zone")).getText(), mfaZone.getName());
    }

    @Test
    public void testDisplayIdentityZoneNameOnVerifyPage() {
        performLogin(username);
        webDriver.findElement(By.id("Next")).click();
        
        assertEquals(zoneUrl + "/login/mfa/verify", webDriver.getCurrentUrl());
        assertEquals(webDriver.findElement(By.id("mfa-identity-zone")).getText(), mfaZone.getName());

        webDriver.findElement(By.id("verify_code_btn")).click();
        assertEquals(webDriver.findElement(By.id("mfa-identity-zone")).getText(), mfaZone.getName());
    }

    private void performLogin(String username) {
        webDriver.get(zoneUrl + "/login");

        webDriver.findElement(By.name("username")).sendKeys(username);
        webDriver.findElement(By.name("password")).sendKeys(USER_PASSWORD);
        webDriver.findElement(By.xpath("//input[@value='Sign in']")).click();
    }

    private MfaProvider enableMfaInZone(String zoneAdminToken) {
        MfaProvider provider = IntegrationTestUtils.createGoogleMfaProvider(baseUrl, zoneAdminToken, MockMvcUtils.constructGoogleMfaProvider(), mfaZone.getId());
        mfaZone.getConfig().getMfaConfig().setEnabled(true).setProviderName(provider.getName());
        mfaZone = IntegrationTestUtils.createZoneOrUpdateSubdomain(adminClient, baseUrl, "testzone1", mfaZone.getSubdomain() , mfaZone.getConfig());
        return provider;
    }

    private String createRandomUser() {
        ScimUser user = new ScimUser(null, new RandomValueStringGenerator(5).generate(), "first", "last");
        user.setPrimaryEmail(user.getUserName());
        user.setPassword(USER_PASSWORD);

        return IntegrationTestUtils.createAnotherUser(webDriver, USER_PASSWORD, simpleSmtpServer, zoneUrl, testClient);
    }

}
