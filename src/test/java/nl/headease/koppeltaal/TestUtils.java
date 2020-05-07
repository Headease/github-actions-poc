package nl.headease.koppeltaal;

import org.hl7.fhir.instance.model.Identifier;
import org.hl7.fhir.instance.model.ResourceReference;
import org.hl7.fhir.instance.model.StringType;

import java.util.UUID;

public class TestUtils {

    public static Identifier getRandomIdentifier() {
        return TestUtils.getIdentifier(UUID.randomUUID().toString());
    }

    public static Identifier getIdentifier(String identifier) {
        Identifier identifierObject = new Identifier();
        identifierObject.setValue(new StringType(identifier));
        return identifierObject;
    }

    public static ResourceReference getRandomResourceReference() {
        return TestUtils.getResourceReference(UUID.randomUUID().toString());
    }

    public static ResourceReference getResourceReference(String resourceReference) {
        ResourceReference resourceReferenceObject = new ResourceReference();
        resourceReferenceObject.setReference(new StringType(resourceReference));
        return resourceReferenceObject;
    }
}
