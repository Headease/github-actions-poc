package nl.headease.koppeltaal;

import nl.koppeltaal.api.*;
import nl.koppeltaal.api.model.ActivityDefinitionParams;
import nl.koppeltaal.api.model.ActivityStatusParams;
import nl.koppeltaal.api.model.enums.ActivityKind;
import nl.koppeltaal.api.model.enums.ActivityPerformer;
import nl.koppeltaal.api.model.enums.CarePlanActivityStatus;
import nl.koppeltaal.api.util.UrlUtil;
import org.hl7.fhir.instance.model.Other;
import org.hl7.fhir.instance.model.ResourceType;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class BaseTest {

    static final String BASE_URL = "http://ggz.koppeltaal.nl/fhir/Koppeltaal";
    static final String FHIR_KOPPELTAAL_SUFFIX = "/FHIR/Koppeltaal";
    static final String NEW_RESOURCE_VERSION = "";
    static final String SELF_LINK = "self";

    static String activityId;
    static String activityRedirectUri;

    static String gameUsername;
    String gamePassword;

    static String clientId;
    static String clientSecret;

    static String domain;
    static String server;

    static String username;
    String password;

    KoppeltaalClient jsonKoppeltaalClient;
    KoppeltaalClient xmlKoppeltaalClient;

    @BeforeClass
    public static void beforeClass() {

        //prints http requests/response on console
        // Configure the logging mechanisms.
        Logger httpLogger = Logger.getLogger("com.google.api.client.http.HttpTransport");
        httpLogger.setLevel(Level.ALL);

        // Create a log handler which prints all log events to the console.
        ConsoleHandler logHandler = new ConsoleHandler();
        logHandler.setLevel(Level.ALL);
        httpLogger.addHandler(logHandler);
    }

    @Before
    public void init() throws Exception {
        Properties properties = loadTestProperties();

        server = properties.getProperty("server");
        username = properties.getProperty("username");
        password = properties.getProperty("password");

        activityId = properties.getProperty("activity.id");
        activityRedirectUri = properties.getProperty("activity.redirecturi");
        gameUsername = properties.getProperty("game.username");
        gamePassword = properties.getProperty("game.password");
        domain = properties.getProperty("domain");

        clientId = properties.getProperty("client.id");
        clientSecret = properties.getProperty("client.secret");

        xmlKoppeltaalClient = new KoppeltaalClient(server, username, password, Format.XML);
        jsonKoppeltaalClient = new KoppeltaalClient(server, username, password, Format.JSON);
    }

    Other createActivityDefinitionResource(String activityId) {
        return createActivityDefinitionResource(activityId, "name from unit test", ActivityKind.E_LEARNING);
    }

    Other createActivityDefinitionResource(String activityId, String name, ActivityKind type) {
        System.out.println("Create new activity definition with Id: " + activityId);

        ActivityDefinitionParams activityDefinitionParams = new ActivityDefinitionParams(
                activityId,
                name,
                "description from unit test",
                type,
                ActivityPerformer.PATIENT,
                true,
                true
        );

        return new KoppeltaalResourceBuilder()
                .addActivityDefinition(activityDefinitionParams)
                .addSubActivityDefinition(true, "sad description from test 1", "sad identifier from test 1", "sad name from test 1")
                .addSubActivityDefinition(true, "sad description from test 2", "sad identifier from test 2", "sad name from test 2")
                .build();
    }

    KoppeltaalBundle newUpdateCarePlanActivityStatus(String messageId, String activityId, String patientId, CarePlanActivityStatus status) {
        System.out.println("messageId: " + messageId);
        System.out.println("activityId: " + activityId);
        System.out.println("ActivityStatus: " + status.toString() + "\n######################################");

        String patientUrl = ResourceURL.create(UrlUtil.KOPPELTAAL_NAMESPACE, ResourceType.Patient, patientId, NEW_RESOURCE_VERSION);
        System.out.println("patientUrl: " + patientUrl);
        ActivityStatusParams activityStatus = new ActivityStatusParams(activityId, UUID.randomUUID().toString(), UrlUtil.KOPPELTAAL_NAMESPACE, NEW_RESOURCE_VERSION,
                status);

        System.out.println("ActivityStatusUrl: " + activityStatus.getUrl());

        return new KoppeltaalBundleBuilder(messageId, domain, Event.UPDATE_CARE_PLAN_ACTIVITY_STATUS, patientUrl,
                activityStatus.getUrl()).addActivityStatus(activityStatus).and().build();

    }

    private static Properties loadTestProperties() throws IOException {

        Properties properties = new Properties();

        try (InputStream inputStream = KoppeltaalClientTest.class.getResourceAsStream("/KoppeltaalClientTest.properties")) {

            // load from file
            properties.load(inputStream);

            // set from environment
            properties.setProperty("password", System.getenv("password"));
            properties.setProperty("game.password", System.getenv("game.password"));
            properties.setProperty("client.secret", System.getenv("client.secret"));
        }

        return properties;
    }
}
