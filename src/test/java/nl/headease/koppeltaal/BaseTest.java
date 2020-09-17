package nl.headease.koppeltaal;

import com.google.api.client.testing.util.LogRecordingHandler;
import nl.koppeltaal.api.*;
import nl.koppeltaal.api.model.ActivityDefinitionParams;
import nl.koppeltaal.api.model.ActivityStatusParams;
import nl.koppeltaal.api.model.enums.ActivityKind;
import nl.koppeltaal.api.model.enums.ActivityPerformer;
import nl.koppeltaal.api.model.enums.CarePlanActivityStatus;
import nl.koppeltaal.api.util.UrlUtil;
import org.hl7.fhir.instance.model.DateTimeType;
import org.hl7.fhir.instance.model.Other;
import org.hl7.fhir.instance.model.ResourceType;
import org.junit.Before;
import org.junit.BeforeClass;
import org.slf4j.LoggerFactory;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.apache.commons.lang3.StringUtils.contains;
import static org.apache.commons.lang3.StringUtils.startsWith;

public abstract class BaseTest {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(BaseTest.class);

    static final Path RESOURCE_LOG_OUTPUT_PATH = Paths.get("target/test-koppeltaal-integration");

    // initialized every @Test
    LogRecordingHandler logRecordingHandler;

    static final String APP_SOURCE_ENDPOINT = "http://dev.myapplication.nl";
    static final String APP_SOURCE_SOFTWARE = "MyApp";
    static final String APP_SOURCE_NAME = "My Application";
    static final String APP_SOURCE_VERSION = "1.0.0";

    static final String BASE_URL = "http://ggz.koppeltaal.nl/fhir/Koppeltaal";
    static final String NEW_RESOURCE_VERSION = "";
    static final String SELF_LINK = "self";

    static String activityRedirectUri;

    static String clientId;
    static String clientSecret;

    static String domain;
    static String server;

    static String username;
    String password;

    KoppeltaalClient jsonKoppeltaalClient;
    KoppeltaalClient xmlKoppeltaalClient;

    @BeforeClass
    public static void beforeClass() throws Exception {

        //prints http requests/response on console
        // Configure the logging mechanisms.
        Logger httpLogger = Logger.getLogger("com.google.api.client.http.HttpTransport");
        httpLogger.setLevel(Level.ALL);

        // Create a log handler which prints all log events to the console.
        ConsoleHandler logHandler = new ConsoleHandler();
        logHandler.setLevel(Level.ALL);
        httpLogger.addHandler(logHandler);

        Files.createDirectories(RESOURCE_LOG_OUTPUT_PATH);
    }

    @Before
    public void init() throws Exception {
        Properties properties = loadTestProperties();

        server = properties.getProperty("server");
        username = properties.getProperty("username");
        password = properties.getProperty("password");

        activityRedirectUri = properties.getProperty("activity.redirecturi");
        domain = properties.getProperty("domain");

        clientId = properties.getProperty("client.id");
        clientSecret = properties.getProperty("client.secret");

        xmlKoppeltaalClient = new KoppeltaalClient(server, username, password, Format.XML);
        jsonKoppeltaalClient = new KoppeltaalClient(server, username, password, Format.JSON);

        // initialize recorder for http transport logs
        logRecordingHandler = new LogRecordingHandler();
        Logger httpLogger = Logger.getLogger("com.google.api.client.http.HttpTransport");
        httpLogger.setLevel(Level.ALL);
        httpLogger.addHandler(logRecordingHandler);
    }

    Other createActivityDefinitionResource(String activityId) {
        return createActivityDefinitionResource(activityId, "name from unit test", ActivityKind.E_LEARNING);
    }

    Other createActivityDefinitionResource(String activityId, String name, ActivityKind type) {
        return createActivityDefinitionResource(activityId, name, type, "description from unit test", ActivityPerformer.PATIENT, true, true, false);
    }

    Other createActivityDefinitionResource(String activityId, String name, ActivityKind type, String description, ActivityPerformer performer, boolean isActive, boolean isDomainSpecific, boolean isArchived) {
        System.out.println("Create new activity definition with Id: " + activityId);

        ActivityDefinitionParams activityDefinitionParams = new ActivityDefinitionParams(
                activityId,
                name,
                description,
                type,
                performer,
                isActive,
                isDomainSpecific,
                isArchived
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

        return new KoppeltaalBundleBuilder(messageId, domain, clientId, BASE_URL, null, null, Event.UPDATE_CARE_PLAN_ACTIVITY_STATUS, patientUrl,
                activityStatus.getUrl()).addActivityStatus(activityStatus).and().build();

    }

    static Date convertDateTimeTypeToDate(DateTimeType dateTimeType) {
        final String lexicalXSDDateTime = dateTimeType.getValue().toString();
        return DatatypeConverter.parseDateTime(lexicalXSDDateTime).getTime();
    }

    static void writeStringToFile(String fileName, String resource) throws IOException {
        final Path filePath = Files.write(Paths.get(RESOURCE_LOG_OUTPUT_PATH + "/" + fileName), resource.getBytes());
        LOG.info("Koppeltaal integration test resource output file written to: " + filePath);
    }

    void writeLastPostedMessageBundleToFile(String fileName, boolean isBundledResource) throws IOException {

        final String lastPostedBundle = logRecordingHandler.messages().stream()
                .filter(recording -> startsWith(recording, "<?xml version")) // filter xml recordings
                .filter(recording -> !isBundledResource || contains(recording, "<feed xmlns")) // filter feeds (message bundles)
                .reduce((first, last) -> last)
                .orElse("");

        writeStringToFile(fileName, lastPostedBundle);
    }

    private static Properties loadTestProperties() throws IOException {

        Properties properties = new Properties();

        try (InputStream inputStream = KoppeltaalClientTest.class.getResourceAsStream("/KoppeltaalClientTest.properties")) {

            // load from file
            properties.load(inputStream);

            // set from environment
            Optional.ofNullable(System.getenv("APPLICATION_PASSWORD"))
                    .ifPresent(var -> properties.setProperty("password", var));
            Optional.ofNullable(System.getenv("CLIENT_SECRET"))
                    .ifPresent(var -> properties.setProperty("client.secret", var));
        }

        return properties;
    }
}
