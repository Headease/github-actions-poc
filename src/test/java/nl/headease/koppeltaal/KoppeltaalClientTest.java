package nl.headease.koppeltaal;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
import nl.koppeltaal.api.*;
import nl.koppeltaal.api.model.*;
import nl.koppeltaal.api.model.enums.*;
import nl.koppeltaal.api.responsehandler.RedirectLocationResponseHandler;
import nl.koppeltaal.api.util.ResourceUtil;
import nl.koppeltaal.api.util.UrlBuilder;
import nl.koppeltaal.api.util.UrlUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.MultilineRecursiveToStringStyle;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.hl7.fhir.instance.model.*;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static nl.koppeltaal.api.ResourceURL.RESOURCE_VERSION_SEPARATOR;
import static nl.koppeltaal.api.util.UrlUtil.*;
import static org.apache.commons.lang3.StringUtils.substringAfterLast;
import static org.junit.Assert.*;

/**
 * These tests depend on the availability of a Koppeltaal server.
 */
public class KoppeltaalClientTest extends BaseTest {

	private static final Logger LOG = LoggerFactory.getLogger(KoppeltaalClientTest.class);

    private final String ACTIVITY_DEFINITION_EXTENSION = "http://ggz.koppeltaal.nl/fhir/Koppeltaal/CarePlan#ActivityDefinition";
    private final String ACTIVITY_DEFINITION_IDENTIFIER_EXTENSION = "http://ggz.koppeltaal.nl/fhir/Koppeltaal/ActivityDefinition#ActivityDefinitionIdentifier";
    private final String CARE_PLAN_ACTIVITY_STATUS_ACTIVITY_EXTENSION = "http://ggz.koppeltaal.nl/fhir/Koppeltaal/CarePlanActivityStatus#Activity";
	private final String CARE_TEAM_STATUS_EXTENSION = "http://ggz.koppeltaal.nl/fhir/Koppeltaal/CareTeam#Status";

	private final String ACTIVITY_DEFINITION_RESOURCE_URL = "https://edgekoppeltaal.vhscloud.nl/FHIR/Koppeltaal/Other?code=ActivityDefinition";
	private final String RESOURCE_TYPE_ACTIVITY_DEFINITION = "ActivityDefinition";
	private final String RESOURCE_TYPE_CARE_PLAN = "CarePlan";
	private final String RESOURCE_TYPE_CARE_TEAM = "CareTeam";
	private final String RESOURCE_TYPE_PRACTITIONER = "Practitioner";
	private final String RESOURCE_TYPE_RELATED_PERSON = "RelatedPerson";
	private final String RESOURCE_TYPE_PATIENT = "Patient";
	private final String RESOURCE_TYPE_MESSAGE_HEADER = "MessageHeader";
	private final String RESOURCE_TYPE_ACTIVITY_STATUS = "CarePlanActivityStatus";

    @Test
	public void testAuthentication() throws Exception {
		Assert.assertTrue(xmlKoppeltaalClient.testAuthentication());
	}

	@Test
	public void testAuthenticationFails() throws Exception {
		try (KoppeltaalClient koppeltaalClient = new KoppeltaalClient(server, username, "incorrect password", Format.JSON)) {
			Assert.assertFalse(koppeltaalClient.testAuthentication());
		}
	}

	@Test
	public void testGetMetadataAsString() throws Exception {
		String metadata = xmlKoppeltaalClient.getMetadataAsString();
		Assert.assertTrue(metadata.startsWith("<Conformance"));
	}

	@Test
	public void testGetMetadataXml() throws Exception {
		xmlKoppeltaalClient.getMetadata();
	}

	@Test
	public void testGetMetaDataJson() throws Exception {
		jsonKoppeltaalClient.getMetadata();
	}

	@Test
	public void testGetActivityDefinitionById() throws Exception {
		Resource resource = createActivityDefinitionResource(UUID.randomUUID().toString());

		resource = xmlKoppeltaalClient.postResource(resource, ACTIVITY_DEFINITION_RESOURCE_URL, null);

		Resource activityDefinition = xmlKoppeltaalClient.getActivityDefinitionById(resource.getXmlId());

		Assert.assertEquals(resource.getXmlId(), activityDefinition.getXmlId());
	}

	@Test
	public void testActivityDefinitionsAsString() throws Exception {
		String activityDefinitions = xmlKoppeltaalClient.getActivityDefinitionsAsString();
		Assert.assertTrue(activityDefinitions.startsWith("<feed"));
		Assert.assertTrue(activityDefinitions.contains("ActivityDefinitionIdentifier"));
	}

	@Test
	@Ignore
	public void testActivityDefinitionsXml() throws IOException {
		KoppeltaalBundle activityDefinitions = xmlKoppeltaalClient.getActivityDefinitions();
		for (AtomEntry<? extends Resource> atomEntry : activityDefinitions.getFeed().getEntryList()) {
			Assert.assertTrue(atomEntry.getId().contains(server + "/FHIR/Koppeltaal/Other/ActivityDefinition"));
		}
		assertEquals(activityDefinitions.getFeed().getTotalResults().intValue(), activityDefinitions.getFeed().getEntryList().size());
	}

	@Test
	@Ignore
	public void testActivityDefinitionsJson() throws IOException {
		KoppeltaalBundle activityDefinitions = jsonKoppeltaalClient.getActivityDefinitions();
		for (AtomEntry<? extends Resource> atomEntry : activityDefinitions.getFeed().getEntryList()) {
			Assert.assertTrue(atomEntry.getId().contains(server + "/FHIR/Koppeltaal/Other/ActivityDefinition"));
		}

		// This doesn't work for the JSON parser as it's not implemented by the FHIR lib (see org.hl7.fhir.instance.formats.JsonParserBase#parseAtom)
		// assertEquals(activityDefinitions.getFeed().getTotalResults().intValue(), activityDefinitions.getFeed().getEntryList().size());
	}

	@Test
	public void testLaunch() throws IOException {
		String baseUrl = BASE_URL;
		String patientUrl = ResourceURL.create(baseUrl, ResourceType.Patient, UUID.randomUUID().toString(), NEW_RESOURCE_VERSION);
		String userUrl = ResourceURL.create(baseUrl, ResourceType.Practitioner, UUID.randomUUID().toString(), NEW_RESOURCE_VERSION);

		String launchUrl = xmlKoppeltaalClient.launch(activityId, patientUrl, userUrl, null);
		Assert.assertTrue(launchUrl.contains("iss="));
		Assert.assertTrue(launchUrl.contains("launch="));
	}

	@Test
	public void testMobileLaunch() throws IOException {
		String baseUrl = BASE_URL;
		String patientUrl = ResourceURL.create(baseUrl, ResourceType.Patient, UUID.randomUUID().toString(), NEW_RESOURCE_VERSION);
		String userUrl = ResourceURL.create(baseUrl, ResourceType.Practitioner, UUID.randomUUID().toString(), NEW_RESOURCE_VERSION);

		MobileLaunchCode mobileLaunchCode = xmlKoppeltaalClient.mobileLaunch(activityId, patientUrl, userUrl, null);
		Assert.assertNotNull(mobileLaunchCode.getActivationCode());
		Assert.assertTrue(mobileLaunchCode.getExpiresIn() > 0);
	}

	@Test
	public void testOAuthAuthorization() throws Exception {
		KoppeltaalClient gameKoppeltaalClient = new KoppeltaalClient(server, gameUsername, gamePassword, Format.JSON);

		String baseUrl = BASE_URL;
		String patientUrl = ResourceURL.create(baseUrl, ResourceType.Patient, UUID.randomUUID().toString(), NEW_RESOURCE_VERSION);
		String userUrl = ResourceURL.create(baseUrl, ResourceType.Practitioner, UUID.randomUUID().toString(), NEW_RESOURCE_VERSION);

		String launchUrl = xmlKoppeltaalClient.launch(activityId, patientUrl, userUrl, null);

		Conformance metadata = gameKoppeltaalClient.getMetadata();
		Map<String, String> parameters = getParametersAsMap(launchUrl);
		String oAuthAuthorizeUrl = gameKoppeltaalClient.createOAuthAuthorizeUrl(clientId, activityRedirectUri,
				parameters.get("launch"), "abc", metadata);

		GenericUrl httpGet = new GenericUrl(oAuthAuthorizeUrl);
		String response = new RedirectLocationResponseHandler().handleResponse(createHttpClient().buildGetRequest(httpGet).execute());

		Map<String, String> oauthParams = getParametersAsMap(response);
		String code = oauthParams.get("code");

		OAuthTokenDetails tokenDetails = gameKoppeltaalClient.getOAuthToken(code, activityRedirectUri, metadata);

		gameKoppeltaalClient.getNextNewAndClaim(tokenDetails);

		try {
			Assert.assertNotNull(tokenDetails);
			Assert.assertTrue(StringUtils.isNotEmpty(tokenDetails.getToken()));
		} finally {
			gameKoppeltaalClient.close();
		}
	}

	@Test
	public void testMobileOAuthAuthorization() throws Exception {

		try (KoppeltaalClient gameKoppeltaalClient = new KoppeltaalClient(server, gameUsername, gamePassword, Format.JSON)) {
			/*
			 * THIS IS THE PART EXECUTED BY THE PORTAL
			 */

			String baseUrl = BASE_URL;
			String patientUrl = ResourceURL.create(baseUrl, ResourceType.Patient, UUID.randomUUID().toString(), NEW_RESOURCE_VERSION);
			String userUrl = ResourceURL.create(baseUrl, ResourceType.Practitioner, UUID.randomUUID().toString(), NEW_RESOURCE_VERSION);

			// Goes to the user...
			MobileLaunchCode mobileLaunch = xmlKoppeltaalClient.mobileLaunch(activityId, patientUrl, userUrl, null);

			/*
			 * THE USER RECIEVES THE LAUNCHCODE, THIS IS THE MOBILE PART.
			 * clientId = The application identifier activityRedirectUri =
			 * Should not be required, ticket created in
			 */
			OAuthTokenDetails tokenDetails = gameKoppeltaalClient.getMobileOAuthToken(clientId, activityRedirectUri,
					mobileLaunch.getActivationCode());

			/*
			 * THE MOBILE APP NEEDS TO STORE THE OAUTH ACCESS TOKEN IN THE FORM
			 * OF OAuthTokenDetails.
			 */

			gameKoppeltaalClient.getNextNewAndClaim(tokenDetails);
			Assert.assertNotNull(tokenDetails);
			Assert.assertTrue(StringUtils.isNotEmpty(tokenDetails.getToken()));

			OAuthTokenDetails newTokenDetails = gameKoppeltaalClient.refreshTokenDetails(tokenDetails, clientId, clientSecret);

			Assert.assertNotEquals(newTokenDetails.getToken(), tokenDetails.getToken());
			Assert.assertNotEquals(newTokenDetails.getRefreshToken(), tokenDetails.getRefreshToken());

			try {
				// Met oude token proberen te connecten.
				gameKoppeltaalClient.getNextNewAndClaim(tokenDetails);
				Assert.fail("Should have gotten an Excpetion");
			} catch (Exception e) {
//				System.out.println(e);
			}

//			tokenDetails = gameKoppeltaalClient.refreshTokenDetails(tokenDetails, clientId, "bla");

		}
	}

	@Test
	@Ignore("Temporarily excluded until https://pim.vzvz.nl/servicedesk/customer/portal/1/KS-243 has been resolved")
	public void testPostGetAndClaim() throws Exception {
		String messageId = UUID.randomUUID().toString();
		KoppeltaalBundle bundle = newCreateOrUpdateCarePlanBundle(messageId);
		xmlKoppeltaalClient.postMessage(bundle);

		KoppeltaalBundle messageBundle = xmlKoppeltaalClient.getNextNewAndClaim();
		Assert.assertNotNull(messageBundle);

		xmlKoppeltaalClient.updateMessageStatus(messageBundle.getMessageHeader(), ProcessingStatus.SUCCESS);
		KoppeltaalBundle updatedMessageBundle = xmlKoppeltaalClient
				.getMessageBundleByHeader(messageBundle.getMessageHeader());
		Assert.assertEquals(ProcessingStatus.SUCCESS, updatedMessageBundle.getMessageHeader().getProcessingStatus());
	}

	@Test
	public void testGetNextNewAndClaimWithParameters() throws Exception {
		String messageId2 = UUID.randomUUID().toString();
		KoppeltaalBundle bundle2 = newUpdateCarePlanActivityStatus(messageId2);

		xmlKoppeltaalClient.postMessage(bundle2);

		String patientReference = bundle2.getMessageHeader().getPatientReference();

		KoppeltaalBundle messageBundle = xmlKoppeltaalClient.getNextNewAndClaim(patientReference,
				Event.UPDATE_CARE_PLAN_ACTIVITY_STATUS);

		Assert.assertNotNull(messageBundle.getMessageHeaderByMessageId(messageId2));

		// Log received UpdateCarePlanActivityStatus resource
		final Map<String, AtomEntry<? extends Resource>> atomEntryMap = getAtomEntryMap(messageBundle);
		LOG.info(toString(atomEntryMap.get(RESOURCE_TYPE_ACTIVITY_STATUS).getResource()));
	}

	@Test
	public void shouldGetVersionsForAllEntriesInResponse() throws Exception {
		String messageId = UUID.randomUUID().toString();
		KoppeltaalBundle bundle = newCreateOrUpdateCarePlanBundle(messageId);

		KoppeltaalBundle responseBundle = xmlKoppeltaalClient.postMessage(bundle);

		MessageHeader responseHeader = (MessageHeader) responseBundle.getFeed().getEntryList().get(0).getResource();
		assertEquals("Expecting the same amount of entries in the response as KT 1.3.5 should version all entries (minus the request header)",
				bundle.getFeed().getEntryList().size() - 1, responseHeader.getData().size());

		responseHeader.getData().forEach(resourceReference ->
                assertTrue(StringUtils.contains(resourceReference.getReferenceSimple(), "/_history/"))
        );
	}

	@Ignore("Result on number of returned MessageHeaderEntries for event CreateOrUpdateActivityDefinition is always 0, for some reason")
	@Test
	public void testGetNextNewAndClaimActivityDefinition() throws Exception {

		KoppeltaalBundle messageBundle = xmlKoppeltaalClient.getNextNewAndClaimActivityDefinition(null);

        Assert.assertEquals(1, messageBundle.getMessageHeaderEntries().size());

		KoppeltaalBundle bundle = xmlKoppeltaalClient.getMessageBundleByHeader(messageBundle.getMessageHeader());

		Assert.assertNotNull(bundle);

	}

	@Ignore("Get message headers for Event.CreateOrUpdateActivityDefinition does not work yet in Koppeltaal.")
	@Test
	public void testGetCreateOrUpdateActivityDefinitionMessageSummary() throws Exception {
		KoppeltaalBundle summary = xmlKoppeltaalClient.getMessageHeaderSummary(Event.CREATE_OR_UPDATE_ACTIVITY_DEFINITION, null, 10, null);

		List<KoppeltaalMessageHeader> headerEntries = summary.getMessageHeaderEntries();

		assertTrue(headerEntries.size() > 0);
		assertTrue(headerEntries.size() < 11);

		KoppeltaalBundle bundle = xmlKoppeltaalClient.getMessageBundleByHeader(headerEntries.get(0));

		assertNotNull(bundle.getFeed());
	}

	@Test
	public void testPostGetHeadersUpdateStatus() throws Exception {
		String messageId = UUID.randomUUID().toString();
		KoppeltaalBundle bundle = newCreateOrUpdateCarePlanBundle(messageId);

		xmlKoppeltaalClient.postMessage(bundle);

		GetMessageParameters parameters = new GetMessageParameters();
		String patientReference = bundle.getMessageHeaderEntries().get(0).getPatientReference();
		parameters.setPatientUrl(patientReference);
		parameters.setEvent(Event.CREATE_OR_UPDATE_CARE_PLAN);
		parameters.setProcessingStatus(ProcessingStatus.NEW);

		KoppeltaalBundle messageHeadersBundle = xmlKoppeltaalClient.getMessageHeaders(parameters);
		KoppeltaalMessageHeader postedMessageHeader = messageHeadersBundle.getMessageHeaderByMessageId(messageId);
		Assert.assertNotNull(postedMessageHeader);

		xmlKoppeltaalClient.updateMessageStatus(postedMessageHeader, ProcessingStatus.CLAIMED);
		KoppeltaalBundle messageBundleById = xmlKoppeltaalClient.getMessageBundleByHeader(postedMessageHeader);
		Assert.assertEquals(ProcessingStatus.CLAIMED, messageBundleById.getMessageHeader().getProcessingStatus());

		xmlKoppeltaalClient.updateMessageStatus(postedMessageHeader, ProcessingStatus.SUCCESS);
	}

	@Test
	public void testPostGetHeadersWithParameters() throws Exception {
		String messageId = UUID.randomUUID().toString();
		String messageId2 = UUID.randomUUID().toString();
		KoppeltaalBundle bundle1 = newCreateOrUpdateCarePlanBundle(messageId);
		KoppeltaalBundle bundle2 = newUpdateCarePlanActivityStatus(messageId2);
		xmlKoppeltaalClient.postMessage(bundle1);
		xmlKoppeltaalClient.postMessage(bundle2);

		GetMessageParameters parameters = new GetMessageParameters();
		parameters.setPatientUrl(bundle1.getMessageHeader().getPatientReference());
		parameters.setEvent(Event.CREATE_OR_UPDATE_CARE_PLAN);
		parameters.setProcessingStatus(ProcessingStatus.NEW);
		parameters.setCount(5000);
		KoppeltaalBundle messageHeaders = xmlKoppeltaalClient.getMessageHeaders(parameters);

		KoppeltaalMessageHeader headerByMessageId = messageHeaders.getMessageHeaderByMessageId(messageId);
		Assert.assertNotNull(headerByMessageId);

		KoppeltaalMessageHeader headerByMessageId2 = messageHeaders.getMessageHeaderByMessageId(messageId2);
		Assert.assertNull(headerByMessageId2);
	}

	@Test
	public void testCreateNewActivityDefinition() throws Exception {
		final String activityDefinitionId = UUID.randomUUID().toString();
		final String activityName = UUID.randomUUID().toString();
		final ActivityKind activityType = ActivityKind.E_LEARNING;
		Other resource = createActivityDefinitionResource(activityDefinitionId, activityName, activityType);

		Resource otherResource = xmlKoppeltaalClient.postResource(resource, ACTIVITY_DEFINITION_RESOURCE_URL, null);
		assertNotNull(otherResource.getResourceType());

		final Other activityDefinition = (Other) xmlKoppeltaalClient.getActivityDefinitionById(otherResource.getXmlId());
		assertEquals(activityDefinitionId, activityDefinition.getIdentifier().get(0).getValueSimple());

		// Test required fields
		assertEquals(TYPE__ACTIVITY_DEFINITION, activityDefinition.getCode().getCoding().get(0).getDisplaySimple());
		assertEquals(activityName, ((StringType) activityDefinition.getExtension("http://ggz.koppeltaal.nl/fhir/Koppeltaal/ActivityDefinition#ActivityName").getValue()).asStringValue());
		assertEquals(activityType.getCode(), ((Coding) activityDefinition.getExtension("http://ggz.koppeltaal.nl/fhir/Koppeltaal/ActivityDefinition#ActivityKind").getValue()).getCodeSimple());
		assertEquals(activityType.getDisplay(), ((Coding) activityDefinition.getExtension("http://ggz.koppeltaal.nl/fhir/Koppeltaal/ActivityDefinition#ActivityKind").getValue()).getDisplaySimple());
		assertEquals(clientId, ((ResourceReference) activityDefinition.getExtension("http://ggz.koppeltaal.nl/fhir/Koppeltaal/ActivityDefinition#Application").getValue()).getDisplaySimple());

		// Log received CreateOrUpdateActivityDefinition resource
		LOG.info(toString(activityDefinition));
	}

	@Test
	public void testCreateNewActivityDefinitionWithJsonClientAndMultipleActivityTemplate() throws Exception {
		final String activityDefinitionId = UUID.randomUUID().toString();
		final String activityName = UUID.randomUUID().toString();
		final ActivityKind activityType = ActivityKind.MULTIPLE_ACTIVITY_TEMPLATE;
		Other resource = createActivityDefinitionResource(activityDefinitionId, activityName, activityType);

		Resource otherResource = jsonKoppeltaalClient.postResource(resource, ACTIVITY_DEFINITION_RESOURCE_URL, null);
		assertNotNull(otherResource.getResourceType());

		final Other activityDefinition = (Other) jsonKoppeltaalClient.getActivityDefinitionById(otherResource.getXmlId());
		assertEquals(activityDefinitionId, activityDefinition.getIdentifier().get(0).getValueSimple());

		// Test required fields
		assertEquals(TYPE__ACTIVITY_DEFINITION, activityDefinition.getCode().getCoding().get(0).getDisplaySimple());
		assertEquals(activityName, ((StringType) activityDefinition.getExtension("http://ggz.koppeltaal.nl/fhir/Koppeltaal/ActivityDefinition#ActivityName").getValue()).asStringValue());
		assertEquals(activityType.getCode(), ((Coding) activityDefinition.getExtension("http://ggz.koppeltaal.nl/fhir/Koppeltaal/ActivityDefinition#ActivityKind").getValue()).getCodeSimple());
		assertEquals(activityType.getDisplay(), ((Coding) activityDefinition.getExtension("http://ggz.koppeltaal.nl/fhir/Koppeltaal/ActivityDefinition#ActivityKind").getValue()).getDisplaySimple());
		assertEquals(clientId, ((ResourceReference) activityDefinition.getExtension("http://ggz.koppeltaal.nl/fhir/Koppeltaal/ActivityDefinition#Application").getValue()).getDisplaySimple());

		// Log received CreateOrUpdateActivityDefinition resource
		LOG.info(toString(activityDefinition));
	}

	@Test
	public void shouldCreateCarePlan() throws Exception {

		Resource activityDefinition = createActivityDefinitionAndFetchFullModel();

		Extension activityDefinitionIdentifierExtension = activityDefinition.getExtension(ACTIVITY_DEFINITION_IDENTIFIER_EXTENSION);
		String activityDefinitionIdentifier = ((StringType) activityDefinitionIdentifierExtension.getValue()).getValue();

		CarePlan carePlan = createCarePlanFromActivityDefinitionAndFetchFullModel(activityDefinitionIdentifier);
        assertNotNull(carePlan);

		CarePlan.CarePlanActivityComponent carePlanActivity = carePlan.getActivity().get(0);
		String carePlanActivityDefinitionIdentifier = ((StringType) carePlanActivity.getExtension(ACTIVITY_DEFINITION_EXTENSION).getValue()).asStringValue();

		assertEquals(activityDefinitionIdentifier, carePlanActivityDefinitionIdentifier);
	}

	@Test
	public void testPublishUpdateCarePlanActivityStatus() throws Exception {

        //create ActivityDefinition
        Resource activityDefinition = createActivityDefinitionAndFetchFullModel();

		//fetch the activity ID
        String activityDefinitionIdentifier = ResourceUtil.getRequiredStringValueFromExtension(activityDefinition, ACTIVITY_DEFINITION_IDENTIFIER_EXTENSION);

		//create careplan
		CarePlan carePlan = createCarePlanFromActivityDefinitionAndFetchFullModel(activityDefinitionIdentifier);
        assertNotNull(carePlan);

		CarePlan.CarePlanActivityComponent carePlanActivity = carePlan.getActivity().get(0);
		String carePlanActivityDefinitionIdentifier = ((StringType) carePlanActivity.getExtension(ACTIVITY_DEFINITION_EXTENSION).getValue()).asStringValue();

		//fetch the patient from the careplan
		String patientReferenceUri = carePlan.getPatient().getReference().getValue();

		String updateActivityStatusMessageId= UUID.randomUUID().toString();

		KoppeltaalBundle changeStatus = newUpdateCarePlanActivityStatus(updateActivityStatusMessageId, carePlanActivityDefinitionIdentifier, "0", CarePlanActivityStatus.InProgress);

        //set same patient in the update status message as in careplan
		ResourceReference resourceReference = (ResourceReference) changeStatus.getMessageHeader().getEntry().getResource()
				.getExtension(PATIENT_EXTENSION).getValue();
		resourceReference.setReferenceSimple(patientReferenceUri);

        //publish the update status message
		xmlKoppeltaalClient.postMessage(changeStatus);

        //query koppeltaal for the update message
		KoppeltaalBundle nextNewAndClaim =  xmlKoppeltaalClient.getNextNewAndClaim(patientReferenceUri,
				Event.UPDATE_CARE_PLAN_ACTIVITY_STATUS);
		nextNewAndClaim.getMessageHeaderByMessageId(updateActivityStatusMessageId);

        //verify message has same id
        assertEquals(updateActivityStatusMessageId, nextNewAndClaim.getMessageHeader().getEntry().getResource().getIdentifierSimple());

        //verify activityDefinitionId of the updated activity
        boolean activityIdFound = false;
        for (AtomEntry<? extends Resource> atomEntry : nextNewAndClaim.getFeed().getEntryList()) {
            Extension extension = atomEntry.getResource().getExtension(CARE_PLAN_ACTIVITY_STATUS_ACTIVITY_EXTENSION);
            if (extension != null && ((StringType) extension.getValue()).getValue().equals(activityDefinitionIdentifier)) {
                activityIdFound = true;
                break;
            }
        }
        Assert.assertTrue(activityIdFound);
    }

	@Test
	public void testCareTeamResourceVersioning() {

		final String dateString = Calendar.getInstance().toString();
		final Identifier careTeamIdentifier = TestUtils.getRandomIdentifier();
		final CareTeamParams careTeamParams = new CareTeamParams(careTeamIdentifier.getValueSimple(), BASE_URL, CareTeamStatus.ACTIVE, "CareTeam name", getPeriod(), dateString);

		assertTrue("URL should contain version.", StringUtils.contains(careTeamParams.getUrl(), RESOURCE_VERSION_SEPARATOR + dateString));
	}

	@Test
	public void testCreateAndUpdatePractitioner() throws Exception {
		final String practitionerId = UUID.randomUUID().toString();
		final String familyName = "family";
		final PractitionerParams practitionerParams = new PractitionerParams(practitionerId, BASE_URL, "", new NameParams("createName", familyName));

		final String messageId = UUID.randomUUID().toString();
		final KoppeltaalBundle koppeltaalBundle = new KoppeltaalBundleBuilder(messageId, domain, Event.CREATE_OR_UPDATE_PRACTITIONER, practitionerParams.getUrl(), practitionerParams.getUrl())
				.addPractitioner(practitionerParams)
				.build();

		xmlKoppeltaalClient.postMessage(koppeltaalBundle);

		// Retrieve creation message
		final KoppeltaalBundle fullResourceBundle = getFullResourceBundle(messageId, koppeltaalBundle, Event.CREATE_OR_UPDATE_PRACTITIONER);

		final Map<String, AtomEntry<? extends Resource>> atomEntryMap = getAtomEntryMap(fullResourceBundle);
		assertEquals(2, atomEntryMap.size());

		final AtomEntry<? extends Resource> practitionerEntry = atomEntryMap.get(RESOURCE_TYPE_PRACTITIONER);
		assertNotNull(practitionerEntry);

		final String practitionerVersion = substringAfterLast(practitionerEntry.getLinks().get(SELF_LINK), RESOURCE_VERSION_SEPARATOR);

		// Update practitioner
		final String updatedGivenName = "updatedName";
		final PractitionerParams practitionerParams2 = new PractitionerParams(practitionerId, BASE_URL, practitionerVersion, new NameParams(updatedGivenName, familyName));

		final String messageId2 = UUID.randomUUID().toString();
		final KoppeltaalBundle koppeltaalBundle2 = new KoppeltaalBundleBuilder(messageId2, domain, Event.CREATE_OR_UPDATE_PRACTITIONER, practitionerParams2.getUrl(), practitionerParams2.getUrl())
				.addPractitioner(practitionerParams2)
				.build();

		xmlKoppeltaalClient.postMessage(koppeltaalBundle2);

		// Retrieve update message
		final KoppeltaalBundle fullResourceBundle2 = getFullResourceBundle(messageId2, koppeltaalBundle2, Event.CREATE_OR_UPDATE_PRACTITIONER);

		final Map<String, AtomEntry<? extends Resource>> atomEntryMap2 = getAtomEntryMap(fullResourceBundle2);
		assertEquals(2, atomEntryMap2.size());

		final Practitioner practitioner = (Practitioner) atomEntryMap2.get(RESOURCE_TYPE_PRACTITIONER).getResource();

		// Test required fields
		final HumanName name = practitioner.getName();
		assertEquals(familyName, name.getFamily().get(0).getValue());
		assertEquals(updatedGivenName, name.getGiven().get(0).getValue());

		// Log received CreateOrUpdatePractitioner resource
		LOG.info(toString(practitioner));
	}

	@Test
	public void testCreateAndUpdateRelatedPerson() throws Exception {
		final String relatedPersonId = UUID.randomUUID().toString();
		final String familyName = "family";
		final String patientIdentifier = ResourceURL.create(BASE_URL, ResourceType.Patient, UUID.randomUUID().toString(), "");
		final RelatedPersonParams relatedPersonParams = new RelatedPersonParams(relatedPersonId, BASE_URL, "", patientIdentifier, new NameParams("createName", familyName));

		final String messageId = UUID.randomUUID().toString();
		final KoppeltaalBundle koppeltaalBundle = new KoppeltaalBundleBuilder(messageId, domain, Event.CREATE_OR_UPDATE_RELATED_PERSON, relatedPersonParams.getUrl(), relatedPersonParams.getUrl())
				.addRelatedPerson(relatedPersonParams)
				.build();

		xmlKoppeltaalClient.postMessage(koppeltaalBundle);

		// Retrieve creation message
		final KoppeltaalBundle fullResourceBundle = getFullResourceBundle(messageId, koppeltaalBundle, Event.CREATE_OR_UPDATE_RELATED_PERSON);

		final Map<String, AtomEntry<? extends Resource>> atomEntryMap = getAtomEntryMap(fullResourceBundle);
		assertEquals(2, atomEntryMap.size());

		final AtomEntry<? extends Resource> relatedPersonEntry = atomEntryMap.get(RESOURCE_TYPE_RELATED_PERSON);
		assertNotNull(relatedPersonEntry);

		final String relatedPersonVersion = substringAfterLast(relatedPersonEntry.getLinks().get(SELF_LINK), RESOURCE_VERSION_SEPARATOR);

		// Update relatedPerson
		final String updatedGivenName = "updatedName";
		final RelatedPersonParams relatedPersonParams2 = new RelatedPersonParams(relatedPersonId, BASE_URL, relatedPersonVersion, patientIdentifier, new NameParams(updatedGivenName, familyName));

		final String messageId2 = UUID.randomUUID().toString();
		final KoppeltaalBundle koppeltaalBundle2 = new KoppeltaalBundleBuilder(messageId2, domain, Event.CREATE_OR_UPDATE_RELATED_PERSON, relatedPersonParams2.getUrl(), relatedPersonParams2.getUrl())
				.addRelatedPerson(relatedPersonParams2)
				.build();

		xmlKoppeltaalClient.postMessage(koppeltaalBundle2);

		// Retrieve update message
		final KoppeltaalBundle fullResourceBundle2 = getFullResourceBundle(messageId2, koppeltaalBundle2, Event.CREATE_OR_UPDATE_RELATED_PERSON);

		final Map<String, AtomEntry<? extends Resource>> atomEntryMap2 = getAtomEntryMap(fullResourceBundle2);
		assertEquals(2, atomEntryMap2.size());

		final RelatedPerson relatedPerson = (RelatedPerson) atomEntryMap2.get(RESOURCE_TYPE_RELATED_PERSON).getResource();

		// Test required fields
		final HumanName name = relatedPerson.getName();
		assertEquals(familyName, name.getFamily().get(0).getValue());
		assertEquals(updatedGivenName, name.getGiven().get(0).getValue());

		assertEquals(patientIdentifier, relatedPerson.getPatient().getReferenceSimple());

		// Log received CreateOrUpdateRelatedPerson resource
		LOG.info(toString(relatedPerson));
	}

	@Test
	public void testCreateRelatedPersonOptionalFields() throws Exception {

		final String patientIdentifier = ResourceURL.create(BASE_URL, ResourceType.Patient, UUID.randomUUID().toString(), NEW_RESOURCE_VERSION);
		final PatientRelationshipType relationship = PatientRelationshipType.WORK;
		final GenderParams gender = new GenderParams(AdministrativeGender.FEMALE);
		final Integer age = 30;
		final ArrayList<ContactParams> telecom = new ArrayList<>();
		telecom.add(new ContactParams(Contact.ContactSystem.email, Contact.ContactUse.work, "test@example.com", Date.from(Instant.now()), Date.from(Instant.now().plus(java.time.Duration.ofDays(100)))));
		telecom.add(new ContactParams(Contact.ContactSystem.phone, Contact.ContactUse.mobile, "+31600000000", Date.from(Instant.now()), Date.from(Instant.now().plus(java.time.Duration.ofDays(365)))));

		final RelatedPersonParams relatedPersonParams = new RelatedPersonParams(UUID.randomUUID().toString(),
				BASE_URL,
				NEW_RESOURCE_VERSION,
				patientIdentifier,
				new NameParams("givenName", "family"),
				telecom,
				relationship,
				gender,
				age
				);

		final String messageId = UUID.randomUUID().toString();
		final KoppeltaalBundle koppeltaalBundle = new KoppeltaalBundleBuilder(messageId, domain, Event.CREATE_OR_UPDATE_RELATED_PERSON, relatedPersonParams.getUrl(), relatedPersonParams.getUrl())
				.addRelatedPerson(relatedPersonParams)
				.build();

		xmlKoppeltaalClient.postMessage(koppeltaalBundle);

		// Retrieve creation message
		final KoppeltaalBundle fullResourceBundle = getFullResourceBundle(messageId, koppeltaalBundle, Event.CREATE_OR_UPDATE_RELATED_PERSON);

		final Map<String, AtomEntry<? extends Resource>> atomEntryMap = getAtomEntryMap(fullResourceBundle);
		assertEquals(2, atomEntryMap.size());

		final RelatedPerson relatedPerson = (RelatedPerson) atomEntryMap.get(RESOURCE_TYPE_RELATED_PERSON).getResource();

		assertEquals(patientIdentifier, relatedPerson.getPatient().getReferenceSimple());
		// Test optional fields
		assertEquals(telecom.size(), relatedPerson.getTelecom().size());
		assertEquals(relationship.getDisplay(), relatedPerson.getRelationship().getCoding().get(0).getDisplaySimple());
		assertEquals(relationship.getCode(), relatedPerson.getRelationship().getCoding().get(0).getCodeSimple());
		assertEquals(gender.getDisplay(), relatedPerson.getGender().getCoding().get(0).getDisplaySimple());
		assertEquals(gender.getCode(), relatedPerson.getGender().getCoding().get(0).getCodeSimple());
		assertEquals(age, ResourceUtil.getOptionalIntegerValueFromExtension(relatedPerson, new UrlBuilder(KOPPELTAAL_NAMESPACE).setResource(UrlExtensionType.RELATED_PERSON).setField(UrlExtensionField.AGE).build()));

		// Log received CreateOrUpdateRelatedPerson resource
		LOG.info(toString(relatedPerson));
	}

@Test
	public void testCreatePractitionerOptionalFields() throws Exception {

		final ArrayList<ContactParams> telecoms = new ArrayList<>();
		telecoms.add(new ContactParams(Contact.ContactSystem.email, Contact.ContactUse.work, "test@example.com", Date.from(Instant.now()), Date.from(Instant.now().plus(java.time.Duration.ofDays(100)))));
		telecoms.add(new ContactParams(Contact.ContactSystem.phone, Contact.ContactUse.mobile, "+31600000000", Date.from(Instant.now()), Date.from(Instant.now().plus(java.time.Duration.ofDays(365)))));

		final PractitionerParams practitionerParams = new PractitionerParams(UUID.randomUUID().toString(), BASE_URL, NEW_RESOURCE_VERSION, new NameParams("givenName", "family"), telecoms);

		final String messageId = UUID.randomUUID().toString();
		final KoppeltaalBundle koppeltaalBundle = new KoppeltaalBundleBuilder(messageId, domain, Event.CREATE_OR_UPDATE_PRACTITIONER, practitionerParams.getUrl(), practitionerParams.getUrl())
				.addPractitioner(practitionerParams)
				.build();

		xmlKoppeltaalClient.postMessage(koppeltaalBundle);

		// Retrieve creation message
		final KoppeltaalBundle fullResourceBundle = getFullResourceBundle(messageId, koppeltaalBundle, Event.CREATE_OR_UPDATE_PRACTITIONER);

		final Map<String, AtomEntry<? extends Resource>> atomEntryMap = getAtomEntryMap(fullResourceBundle);
		assertEquals(2, atomEntryMap.size());

		final Practitioner practitioner = (Practitioner) atomEntryMap.get(RESOURCE_TYPE_PRACTITIONER).getResource();

		// Test optional fields
		assertEquals(telecoms.size(), practitioner.getTelecom().size());
		assertEquals(telecoms.get(0).getValue(), practitioner.getTelecom().get(0).getValueSimple());
		assertEquals(telecoms.get(1).getValue(), practitioner.getTelecom().get(1).getValueSimple());
		assertEquals(telecoms.get(0).getSystem(), practitioner.getTelecom().get(0).getSystemSimple());
		assertEquals(telecoms.get(1).getSystem(), practitioner.getTelecom().get(1).getSystemSimple());

		// Log received CreateOrUpdateRelatedPerson resource
		LOG.info(toString(practitioner));
	}

	@Test
	public void testCreateAndUpdatePatient() throws Exception {
		final String patientId = UUID.randomUUID().toString();
		final PatientParams patientParams = new PatientParams(patientId, BASE_URL, "", new NameParams("createName", "family"));
		patientParams.setGender(new GenderParams("F", "Female"));
		patientParams.setAge(36);

		final String messageId = UUID.randomUUID().toString();
		final KoppeltaalBundle koppeltaalBundle = new KoppeltaalBundleBuilder(messageId, domain, Event.CREATE_OR_UPDATE_PATIENT, patientParams.getUrl(), patientParams.getUrl())
				.addPatient(patientParams)
				.and()
				.build();

		xmlKoppeltaalClient.postMessage(koppeltaalBundle);

		// Retrieve creation message
		final KoppeltaalBundle fullResourceBundle = getFullResourceBundle(messageId, koppeltaalBundle, Event.CREATE_OR_UPDATE_PATIENT);

		final Map<String, AtomEntry<? extends Resource>> atomEntryMap = getAtomEntryMap(fullResourceBundle);
		assertEquals(2, atomEntryMap.size());

		final AtomEntry<? extends Resource> patientEntry = atomEntryMap.get(RESOURCE_TYPE_PATIENT);
		assertNotNull(patientEntry);

		final String patientVersion = substringAfterLast(patientEntry.getLinks().get(SELF_LINK), RESOURCE_VERSION_SEPARATOR);

		// Update patient
		final String updatedGivenName = "updatedName";
		final PatientParams patientParams2 = new PatientParams(patientId, BASE_URL, patientVersion, new NameParams(updatedGivenName, "family"));
		patientParams.setGender(new GenderParams("F", "Female"));
		patientParams.setAge(36);

		final String messageId2 = UUID.randomUUID().toString();
		final KoppeltaalBundle koppeltaalBundle2 = new KoppeltaalBundleBuilder(messageId2, domain, Event.CREATE_OR_UPDATE_PATIENT, patientParams2.getUrl(), patientParams2.getUrl())
				.addPatient(patientParams2)
				.and()
				.build();

		xmlKoppeltaalClient.postMessage(koppeltaalBundle2);

		// Retrieve update message
		final KoppeltaalBundle fullResourceBundle2 = getFullResourceBundle(messageId2, koppeltaalBundle2, Event.CREATE_OR_UPDATE_PATIENT);

		final Map<String, AtomEntry<? extends Resource>> atomEntryMap2 = getAtomEntryMap(fullResourceBundle2);
		assertEquals(2, atomEntryMap2.size());

		final Patient patient = (Patient) atomEntryMap2.get(RESOURCE_TYPE_PATIENT).getResource();

		// Test required fields
		final HumanName name = patient.getName().get(0);
		assertEquals(updatedGivenName, name.getGiven().get(0).getValue());

		// Log received CreateOrUpdatePatient resource
		LOG.info(toString(patient));
	}

	@Test
	public void testCareTeamUpdate() throws Exception {
		// Update CareTeam in KT v1.3.5
		// ----------------------------
		// 1. correct version provided (changes accepted by KT)
		// 2. no version provided (changes accepted by KT)
		// 3. wrong version provided (changes NOT accepted by KT)

		final String carePlanId = UUID.randomUUID().toString();
		final CarePlanParams carePlanParams = new CarePlanParams(carePlanId, BASE_URL, "", CarePlan.CarePlanStatus.planned);

		final String patientId = UUID.randomUUID().toString();
		final PatientParams patient = new PatientParams(patientId, BASE_URL, "", new NameParams("Client", "Name"));

		final String practitionerId = UUID.randomUUID().toString();
		final PractitionerParams practitioner = new PractitionerParams(practitionerId, BASE_URL, "", new NameParams("Practitioner", "Name"));

		final String relatedPersonId = UUID.randomUUID().toString();
		final RelatedPersonParams relatedPerson = new RelatedPersonParams(relatedPersonId, BASE_URL, "", patient.getUrl(), new NameParams("RelatedPerson", "Name"));

		final Identifier careTeamIdentifier = TestUtils.getRandomIdentifier();
		final CareTeamParams careTeamParams = new CareTeamParams(careTeamIdentifier.getValueSimple(), BASE_URL, CareTeamStatus.ACTIVE, "CareTeam name", getPeriod(), "");
		final ResourceReference patientReference = TestUtils.getResourceReference(patient.getUrl());

        final String fullCareTeamIdentifier = ResourceURL.create(BASE_URL, "CareTeam", careTeamIdentifier.getValueSimple());
        final List<ParticipantParams> participantParams = Arrays.asList(
                new ParticipantParams(practitioner.getId(), fullCareTeamIdentifier, CarePlanParticipantRole.REQUESTER),
                new ParticipantParams(relatedPerson.getId(), CarePlanParticipantRole.THIRDPARTY));

        final ActivityParams activityParams = new ActivityParams(UUID.randomUUID().toString(), UUID.randomUUID().toString(), new CodingParams("ELearning", "E-Learning"), "Available", new DateAndTime(new Date()), participantParams, new ArrayList<>());

        final String messageId1 = UUID.randomUUID().toString();
		final KoppeltaalBundle koppeltaalBundle = new KoppeltaalBundleBuilder(messageId1, domain, Event.CREATE_OR_UPDATE_CARE_PLAN, patient.getId(), carePlanParams.getUrl())
				.addCarePlan(carePlanParams)
                .addActivity(activityParams)
                .addParticipant(CarePlanParticipantRole.REQUESTER.getValue(), practitioner.getId(), careTeamParams.getId())
                .addParticipant(CarePlanParticipantRole.THIRDPARTY.getValue(), relatedPerson.getId())
				.setPatientReference(patient.getId())
				.and()
				.addPatient(patient)
				.and()
				.addPractitioner(practitioner)
				.addRelatedPerson(relatedPerson)
				.addCareTeam(careTeamParams)
				.addCareTeamIdentifier(careTeamIdentifier)
				.addManagingOrganization(TestUtils.getRandomResourceReference())
				.addSubject(patientReference)
				.and()
				.build();

		xmlKoppeltaalClient.postMessage(koppeltaalBundle);

		// now get full bundle containing resource versions
		final KoppeltaalBundle fullResourceBundle = getFullResourceBundle(messageId1, koppeltaalBundle, Event.CREATE_OR_UPDATE_CARE_PLAN);
		final Map<String, AtomEntry<? extends Resource>> atomEntryMap = getAtomEntryMap(fullResourceBundle);

		final AtomEntry<? extends Resource> careTeamEntry = atomEntryMap.get(RESOURCE_TYPE_CARE_TEAM);
		assertEquals(CareTeamStatus.ACTIVE, ResourceUtil.getRequiredEnumValueFromCodingExtension(CareTeamStatus.class, careTeamEntry.getResource(), CARE_TEAM_STATUS_EXTENSION));

		// make CareTeam with updated version and changed status
		final String careTeamVersion = substringAfterLast(careTeamEntry.getLinks().get(SELF_LINK), RESOURCE_VERSION_SEPARATOR);
		final CareTeamParams careTeamParams2 = new CareTeamParams(careTeamIdentifier.getValueSimple(), BASE_URL, CareTeamStatus.ENTERED_IN_ERROR, "CareTeam name", getPeriod(), careTeamVersion);

		// make CarePlan with updated version
		final AtomEntry<? extends Resource> carePlanEntry = atomEntryMap.get(RESOURCE_TYPE_CARE_PLAN);
		final String carePlanVersion = substringAfterLast(carePlanEntry.getLinks().get(SELF_LINK), RESOURCE_VERSION_SEPARATOR);
		final CarePlan.CarePlanStatus updatedCarePlanStatus = CarePlan.CarePlanStatus.active;
		final CarePlanParams carePlanParams2 = new CarePlanParams(carePlanId, BASE_URL, carePlanVersion, updatedCarePlanStatus);

		// make Patient with updated version
		final AtomEntry<? extends Resource> patientEntry = atomEntryMap.get(RESOURCE_TYPE_PATIENT);
		final String patientVersion = substringAfterLast(patientEntry.getLinks().get(SELF_LINK), RESOURCE_VERSION_SEPARATOR);
		final PatientParams patientParams2 = new PatientParams(patientId, BASE_URL, patientVersion, new NameParams("Client", "Name"));

		// make Practitioner with updated version
		final AtomEntry<? extends Resource> practitionerEntry = atomEntryMap.get(RESOURCE_TYPE_PRACTITIONER);
		final String practitionerVersion = substringAfterLast(practitionerEntry.getLinks().get(SELF_LINK), RESOURCE_VERSION_SEPARATOR);
		final PractitionerParams practitionerParams2 = new PractitionerParams(practitionerId, BASE_URL, practitionerVersion, new NameParams("Practitioner", "Name"));

		// make RelatedPerson with updated version
		final AtomEntry<? extends Resource> relatedPersonEntry = atomEntryMap.get(RESOURCE_TYPE_RELATED_PERSON);
		final String relatedPersonVersion = substringAfterLast(relatedPersonEntry.getLinks().get(SELF_LINK), RESOURCE_VERSION_SEPARATOR);
		final RelatedPersonParams relatedPersonParams2 = new RelatedPersonParams(relatedPersonId, BASE_URL, relatedPersonVersion, patient.getUrl(), new NameParams("RelatedPerson", "Name"));

		final String messageId2 = UUID.randomUUID().toString();
		final KoppeltaalBundle koppeltaalBundle2 = new KoppeltaalBundleBuilder(messageId2, domain, Event.CREATE_OR_UPDATE_CARE_PLAN, patientParams2.getId(), carePlanParams2.getUrl())
				.addCarePlan(carePlanParams2)
                .addActivity(activityParams)
                .addParticipant(CarePlanParticipantRole.REQUESTER.getValue(), practitionerParams2.getId(), careTeamParams2.getId())
                .addParticipant(CarePlanParticipantRole.THIRDPARTY.getValue(), relatedPersonParams2.getId())
				.setPatientReference(patientParams2.getId())
				.and()
				.addPatient(patientParams2)
				.and()
				.addPractitioner(practitionerParams2)
				.addRelatedPerson(relatedPersonParams2)
				.addCareTeam(careTeamParams2)
				.addCareTeamIdentifier(careTeamIdentifier)
				.addManagingOrganization(TestUtils.getRandomResourceReference())
				.addSubject(patientReference)
				.and()
				.build();

		// should succeed with new version for CarePlan and CareTeam
		xmlKoppeltaalClient.postMessage(koppeltaalBundle2);

		// now get full bundle containing resource versions
		final KoppeltaalBundle fullResourceBundle2 = getFullResourceBundle(messageId2, koppeltaalBundle2, Event.CREATE_OR_UPDATE_CARE_PLAN);
		final Map<String, AtomEntry<? extends Resource>> atomEntryMap2 = getAtomEntryMap(fullResourceBundle2);

		final Other careTeam = (Other) atomEntryMap2.get(RESOURCE_TYPE_CARE_TEAM).getResource();
		assertEquals(CareTeamStatus.ENTERED_IN_ERROR, ResourceUtil.getRequiredEnumValueFromCodingExtension(CareTeamStatus.class, careTeam, CARE_TEAM_STATUS_EXTENSION));

		// Test required CarePlan fields
		final CarePlan carePlan = (CarePlan) atomEntryMap2.get(RESOURCE_TYPE_CARE_PLAN).getResource();
		assertEquals(updatedCarePlanStatus, carePlan.getStatus().getValue());

		// Log received CreateOrUpdateCarePlan resources
		LOG.info(toString(carePlan));
		LOG.info(toString(careTeam));
		LOG.info(toString(atomEntryMap2.get(RESOURCE_TYPE_PATIENT).getResource()));
		LOG.info(toString(atomEntryMap2.get(RESOURCE_TYPE_PRACTITIONER).getResource()));
		LOG.info(toString(atomEntryMap2.get(RESOURCE_TYPE_RELATED_PERSON).getResource()));
	}

	@Test
	public void testCareTeam() throws Exception {
		String messageId = UUID.randomUUID().toString();

		Resource activityDefinition = createActivityDefinitionAndFetchFullModel();

		Extension activityDefinitionIdentifierExtension = activityDefinition.getExtension(ACTIVITY_DEFINITION_IDENTIFIER_EXTENSION);
		String activityDefinitionIdentifier = ((StringType) activityDefinitionIdentifierExtension.getValue()).getValue();

		KoppeltaalBundle koppeltaalBundle = createCarePlanBundle(messageId, activityDefinitionIdentifier);

		Map<String, AtomEntry<? extends Resource>> resourceMap = getAtomEntryMap(koppeltaalBundle);

		assertNotNull(resourceMap.get(RESOURCE_TYPE_CARE_TEAM));
	}

	@Test
	public void sendAndReadUserMessageRequiredFields() throws Exception {

		final String sendingApplicationUrl = ResourceURL.create(BASE_URL, ResourceType.Device, UUID.randomUUID().toString());
        final PractitionerParams receivingPractitionerParams = new PractitionerParams(UUID.randomUUID().toString(), BASE_URL, NEW_RESOURCE_VERSION, new NameParams("given", "family"));
		final String messageSubject = "integration-test-subject";
		final String messageContent = "integration-test-content";

		final UserMessageParams userMessageParams = new UserMessageParams(UUID.randomUUID().toString(), sendingApplicationUrl, receivingPractitionerParams.getId(), MessageKind.NOTIFICATION, messageSubject, messageContent, BASE_URL);

		final String messageId = UUID.randomUUID().toString();
		final String PatientUrl = ResourceURL.create(BASE_URL, ResourceType.Patient, UUID.randomUUID().toString());

		final KoppeltaalBundle bundle = new KoppeltaalBundleBuilder(messageId, domain, Event.CREATE_OR_UPDATE_USER_MESSAGE, PatientUrl, userMessageParams.getUrl())
				.addUserMessage(userMessageParams)
                .and().addPractitioner(receivingPractitionerParams)
                .build();

		xmlKoppeltaalClient.postMessage(bundle);

		final KoppeltaalBundle fetchedResourceBundle = getFullResourceBundle(messageId, bundle, Event.CREATE_OR_UPDATE_USER_MESSAGE);

		Map<String, AtomEntry<? extends Resource>> resourceMap = getAtomEntryMap(fetchedResourceBundle);
		final Other messageResource = (Other) resourceMap.get(UrlExtensionType.USER_MESSAGE.getTypeName()).getResource();
		assertEquals(5, messageResource.getExtensions().size());

		UrlBuilder urlBuilder = new UrlBuilder(KOPPELTAAL_NAMESPACE).setResource(UrlExtensionType.USER_MESSAGE);
		assertEquals(MessageKind.NOTIFICATION.getValue(), ((CodeableConcept) messageResource.getExtension(urlBuilder.setField(UrlExtensionField.MESSAGE_KIND).build()).getValue()).getCoding().get(0).getCodeSimple());
		assertEquals(sendingApplicationUrl, ((ResourceReference) messageResource.getExtension(urlBuilder.setField(UrlExtensionField.FROM).build()).getValue()).getReferenceSimple());
		assertEquals(receivingPractitionerParams.getId(), ((ResourceReference) messageResource.getExtension(urlBuilder.setField(UrlExtensionField.TO).build()).getValue()).getReferenceSimple());
		assertEquals(messageSubject, ((StringType) messageResource.getExtension(urlBuilder.setField(UrlExtensionField.SUBJECT_STRING).build()).getValue()).asStringValue());
		assertEquals(messageContent, ((StringType) messageResource.getExtension(urlBuilder.setField(UrlExtensionField.CONTENT).build()).getValue()).asStringValue());
		assertNull(messageResource.getExtension(urlBuilder.setField(UrlExtensionField.CONTEXT).build()));

		LOG.info(toString(messageResource));
	}

	@Test
	public void sendAndReadUserMessageContext() throws Exception {

		final String sendingApplicationUrl = ResourceURL.create(BASE_URL, ResourceType.Device, UUID.randomUUID().toString());
        final PractitionerParams receivingPractitionerParams = new PractitionerParams(UUID.randomUUID().toString(), BASE_URL, NEW_RESOURCE_VERSION, new NameParams("given", "family"));
		final String messageSubject = "integration-test-subject";
		final String messageContent = "integration-test-content";
		final String messageContext = "http://ggz.koppeltaal.nl/fhir/Koppeltaal/CarePlan#ActivityIdentifier/0102030405";

		final UserMessageParams userMessageParams = new UserMessageParams(UUID.randomUUID().toString(), sendingApplicationUrl, receivingPractitionerParams.getId(), MessageKind.NOTIFICATION, messageSubject, messageContent, messageContext, BASE_URL);

		final String messageId = UUID.randomUUID().toString();
		final String patientUrl = ResourceURL.create(BASE_URL, ResourceType.Patient, UUID.randomUUID().toString());
		final KoppeltaalBundle bundle = new KoppeltaalBundleBuilder(messageId, domain, Event.CREATE_OR_UPDATE_USER_MESSAGE, patientUrl, userMessageParams.getUrl())
				.addUserMessage(userMessageParams)
                .and().addPractitioner(receivingPractitionerParams)
                .build();

		xmlKoppeltaalClient.postMessage(bundle);

		final KoppeltaalBundle fetchedResourceBundle = getFullResourceBundle(messageId, bundle, Event.CREATE_OR_UPDATE_USER_MESSAGE);

		Map<String, AtomEntry<? extends Resource>> resourceMap = getAtomEntryMap(fetchedResourceBundle);
		final Other messageResource = (Other) resourceMap.get(UrlExtensionType.USER_MESSAGE.getTypeName()).getResource();

		assertEquals(UrlExtensionType.USER_MESSAGE.getTypeName(), messageResource.getCode().getCoding().get(0).getCodeSimple());
		assertEquals(UrlUtil.OTHER_RESOURCE_USAGE_SYSTEM, messageResource.getCode().getCoding().get(0).getSystemSimple());
		assertEquals(6, messageResource.getExtensions().size());

		UrlBuilder urlBuilder = new UrlBuilder(KOPPELTAAL_NAMESPACE).setResource(UrlExtensionType.USER_MESSAGE);
		assertEquals(messageContext, ((UriType) messageResource.getExtension(urlBuilder.setField(UrlExtensionField.CONTEXT).build()).getValue()).asStringValue());

		LOG.info(toString(messageResource));
	}

	@Test(expected = IllegalArgumentException.class)
	public void shouldBreakWhenMessageKindNotSet() throws Exception {

        final String sendingApplicationUrl = ResourceURL.create(BASE_URL, ResourceType.Device, UUID.randomUUID().toString());
        final PractitionerParams receivingPractitionerParams = new PractitionerParams(UUID.randomUUID().toString(), BASE_URL, NEW_RESOURCE_VERSION, new NameParams("given", "family"));
        final String messageSubject = "integration-test-subject";
		final String messageContent = "integration-test-content";
		final MessageKind messageKind = null;

		final UserMessageParams userMessageParams = new UserMessageParams(UUID.randomUUID().toString(), sendingApplicationUrl, receivingPractitionerParams.getId(), messageKind, messageSubject, messageContent, null, BASE_URL);

		final String patientUrl = ResourceURL.create(BASE_URL, ResourceType.Patient, UUID.randomUUID().toString());

		new KoppeltaalBundleBuilder(UUID.randomUUID().toString(), domain, Event.CREATE_OR_UPDATE_USER_MESSAGE, patientUrl, userMessageParams.getUrl())
				.addUserMessage(userMessageParams)
                .and().addPractitioner(receivingPractitionerParams)
				.build();
	}

	private Map<String, AtomEntry<? extends Resource>> getAtomEntryMap(KoppeltaalBundle koppeltaalBundle) {

		assertNotNull("Bundle should not be null", koppeltaalBundle);

		List<AtomEntry<? extends Resource>> entryList = koppeltaalBundle.getFeed().getEntryList();

		Map<String, AtomEntry<? extends Resource>> entryMap = new HashMap<>();

		for (AtomEntry<? extends Resource> atomEntry : entryList) {
			Resource resource = atomEntry.getResource();

			if (resource instanceof MessageHeader) {
				entryMap.put(RESOURCE_TYPE_MESSAGE_HEADER, atomEntry);
			} else if (resource instanceof CarePlan) {
				entryMap.put(RESOURCE_TYPE_CARE_PLAN, atomEntry);
			} else if (resource instanceof Patient) {
				entryMap.put(RESOURCE_TYPE_PATIENT, atomEntry);
			} else if (resource instanceof Practitioner) {
				entryMap.put(RESOURCE_TYPE_PRACTITIONER, atomEntry);
			} else if (resource instanceof RelatedPerson) {
				entryMap.put(RESOURCE_TYPE_RELATED_PERSON, atomEntry);
			} else if (resource instanceof Other && ((Other) resource).getCode().getCoding().get(0).getCodeSimple().equals(RESOURCE_TYPE_CARE_TEAM)) {
				entryMap.put(RESOURCE_TYPE_CARE_TEAM, atomEntry);
			} else if (resource instanceof Other && ((Other) resource).getCode().getCoding().get(0).getCodeSimple().equals(RESOURCE_TYPE_ACTIVITY_DEFINITION)) {
				entryMap.put(RESOURCE_TYPE_ACTIVITY_DEFINITION, atomEntry);
			} else if (resource instanceof Other && ((Other) resource).getCode().getCoding().get(0).getCodeSimple().equals(RESOURCE_TYPE_ACTIVITY_STATUS)) {
				entryMap.put(RESOURCE_TYPE_ACTIVITY_STATUS, atomEntry);
			} else if (resource instanceof Other && ((Other) resource).getCode().getCoding().get(0).getCodeSimple().equals(UrlExtensionType.USER_MESSAGE.getTypeName())) {
				entryMap.put(UrlExtensionType.USER_MESSAGE.getTypeName(), atomEntry);
			}
		}

		return entryMap;
	}

	private Resource createActivityDefinitionAndFetchFullModel() throws IOException {
		//First create an AD
		Resource activityDefinitionResource = createActivityDefinitionResource(UUID.randomUUID().toString());
		activityDefinitionResource = xmlKoppeltaalClient.postResource(activityDefinitionResource, ACTIVITY_DEFINITION_RESOURCE_URL, null);

		//Fetch the full model instead of the proxy
		activityDefinitionResource = xmlKoppeltaalClient.getActivityDefinitionById(activityDefinitionResource.getXmlId());
		return activityDefinitionResource;
	}

	private CarePlan createCarePlanFromActivityDefinitionAndFetchFullModel(String activityDefinitionIdentifier) throws Exception {

        //careplan message id
        String messageId = UUID.randomUUID().toString();
        System.out.println("create care plan messageId: " + messageId);

		KoppeltaalBundle fullCarePlanBundle = createCarePlanBundle(messageId, activityDefinitionIdentifier);
		final Map<String, AtomEntry<? extends Resource>> resourceMap = getAtomEntryMap(fullCarePlanBundle);

		assertNotNull("Should have found care plan", resourceMap.get(RESOURCE_TYPE_CARE_PLAN));

		return (CarePlan) resourceMap.get(RESOURCE_TYPE_CARE_PLAN).getResource();
	}

	private KoppeltaalBundle createCarePlanBundle(String messageId, String activityDefinitionIdentifier) throws Exception {

		CodingParams kind = new CodingParams("activityKindCode", "activityKindDisplay");
		Identifier careTeamIdentifier = TestUtils.getRandomIdentifier();

		List<ParticipantParams> participants = new ArrayList<>();
		participants.add(new ParticipantParams("participant", careTeamIdentifier.getValueSimple(), CarePlanParticipantRole.CLIENT));

		ActivityParams activity = new ActivityParams(UUID.randomUUID().toString(), activityDefinitionIdentifier, kind, "Active", new DateAndTime(Calendar.getInstance()), participants, null);

		KoppeltaalBundle carePlanBundle = newCreateOrUpdateCarePlanBundle(messageId, activity, careTeamIdentifier);

        xmlKoppeltaalClient.postMessage(carePlanBundle);

        return getFullResourceBundle(messageId, carePlanBundle, Event.CREATE_OR_UPDATE_CARE_PLAN);
	}

	private KoppeltaalBundle getFullResourceBundle(String messageId, KoppeltaalBundle bundle, Event event) throws KoppeltaalException, IOException {

		String patientReference = bundle.getMessageHeaderEntries().get(0).getPatientReference();

		GetMessageParameters parameters = new GetMessageParameters();
		parameters.setPatientUrl(patientReference);
		parameters.setProcessingStatus(ProcessingStatus.NEW);
		parameters.setEvent(event);
		parameters.setCount(1); // we know there will only be one, so return immediately after its found

		long start = System.currentTimeMillis();
		KoppeltaalBundle messageHeadersBundle = xmlKoppeltaalClient.getMessageHeaders(parameters);
		long spent = System.currentTimeMillis() - start;
		System.out.println("Time spent: " + spent + " ms");

		KoppeltaalMessageHeader postedMessageHeader = messageHeadersBundle.getMessageHeaderByMessageId(messageId);
		assertNotNull("Header should be present", postedMessageHeader);

		xmlKoppeltaalClient.updateMessageStatus(postedMessageHeader, ProcessingStatus.CLAIMED);

		KoppeltaalBundle fullCarePlanBundle = xmlKoppeltaalClient.getMessageBundleByHeader(postedMessageHeader);

		xmlKoppeltaalClient.updateMessageStatus(postedMessageHeader, ProcessingStatus.SUCCESS);
		return fullCarePlanBundle;
	}

	private KoppeltaalBundle newCreateOrUpdateCarePlanBundle(String messageId) {
		String baseUrl = BASE_URL;

		PatientParams patient = new PatientParams(UUID.randomUUID().toString(), baseUrl, NEW_RESOURCE_VERSION,
				new NameParams("Claes", "de Vries"));
		CarePlanParams carePlan = new CarePlanParams(UUID.randomUUID().toString(), baseUrl, NEW_RESOURCE_VERSION,
				CarePlan.CarePlanStatus.active);

		List<ParticipantParams> participants = new ArrayList<>();
		participants.add(new ParticipantParams("participant", null, CarePlanParticipantRole.CLIENT));

		ActivityParams activity = new ActivityParams(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
				new CodingParams("activityKindCode", "activityKindDisplay"), "Active", new DateAndTime(Calendar.getInstance()), participants,null);

		PractitionerParams practitioner = new PractitionerParams(UUID.randomUUID().toString(), baseUrl, NEW_RESOURCE_VERSION,
				new NameParams("John", "Doe"));

		RelatedPersonParams relatedPerson = new RelatedPersonParams(UUID.randomUUID().toString(), baseUrl, NEW_RESOURCE_VERSION,
				patient.getUrl(), new NameParams("Related", "Person"));

		return new KoppeltaalBundleBuilder(messageId, domain, Event.CREATE_OR_UPDATE_CARE_PLAN, patient.getUrl(),
				carePlan.getUrl())
				.addCarePlan(carePlan).addGoal("1", "Activity goal", null)
					.addActivity(activity)
					.addParticipant("Assigner", practitioner.getUrl()).setPatientReference(patient.getUrl())
				.and()
					.addPatient(patient)
				.and()
					.addPractitioner(practitioner)
					.addRelatedPerson(relatedPerson)
				.build();
	}

	private KoppeltaalBundle newCreateOrUpdateCarePlanBundle(String messageId, ActivityParams activity, Identifier careTeamIdentifier) {
		String baseUrl = BASE_URL;

		PatientParams patient = new PatientParams(UUID.randomUUID().toString(), baseUrl, NEW_RESOURCE_VERSION,
				new NameParams("Claes", "de Vries"));
		CarePlanParams carePlan = new CarePlanParams(UUID.randomUUID().toString(), baseUrl, NEW_RESOURCE_VERSION,
				CarePlan.CarePlanStatus.active);

		PractitionerParams practitioner = new PractitionerParams(UUID.randomUUID().toString(), baseUrl, NEW_RESOURCE_VERSION,
				new NameParams("John", "Doe"));

        CareTeamParams careTeam = new CareTeamParams(careTeamIdentifier.getValueSimple(), baseUrl, CareTeamStatus.ACTIVE, "Team Awesome", getPeriod(), "", patient.getUrl(), UUID.randomUUID().toString());

		KoppeltaalBundleBuilder koppeltaalBundleBuilder = new KoppeltaalBundleBuilder(messageId, domain, Event.CREATE_OR_UPDATE_CARE_PLAN, patient.getUrl(),
				carePlan.getUrl());

		return koppeltaalBundleBuilder
				.addCarePlan(carePlan)
				.addGoal("1", "Activity goal", null)
				.addActivity(activity)
				.addParticipant("Assigner", practitioner.getUrl())
				.setPatientReference(patient.getUrl())
				.and()
				.addPatient(patient)
				.and()
				.addPractitioner(practitioner)
				.addCareTeam(careTeam)
				.and()
				.build();
	}

	private Period getPeriod() {
		DateAndTime start = DateAndTime.now();

		DateAndTime end = DateAndTime.now();
		end.add(Calendar.YEAR, 1);

		Period period = new Period();
		period.setStartSimple(start);
		period.setEndSimple(end);

		return period;
	}

	private KoppeltaalBundle newUpdateCarePlanActivityStatus(String messageId) {
		String patientUrl = ResourceURL.create(BASE_URL, ResourceType.Patient, UUID.randomUUID().toString(), NEW_RESOURCE_VERSION);
		ActivityStatusParams activityStatus = new ActivityStatusParams(UUID.randomUUID().toString(), UUID.randomUUID().toString(), BASE_URL, NEW_RESOURCE_VERSION,
				CarePlanActivityStatus.InProgress);

		return new KoppeltaalBundleBuilder(messageId, domain, Event.UPDATE_CARE_PLAN_ACTIVITY_STATUS, patientUrl,
				activityStatus.getUrl()).addActivityStatus(activityStatus).and().build();
	}

	private Map<String, String> getParametersAsMap(String url) throws URISyntaxException {
		List<NameValuePair> parameters = URLEncodedUtils.parse(new URI(url), StandardCharsets.UTF_8.name());
		HashMap<String, String> map = new HashMap<>();

		for (NameValuePair parameter : parameters) {
			map.put(parameter.getName(), parameter.getValue());
		}

		return map;
	}

	private HttpRequestFactory createHttpClient() {
		return new NetHttpTransport().createRequestFactory(request -> {
			request.setFollowRedirects(false);
			request.setThrowExceptionOnExecuteError(false);
		});
	}

	private String toString(Resource resource) {
    	return new ReflectionToStringBuilder(resource, new MultilineRecursiveToStringStyle()).toString();
	}
}
