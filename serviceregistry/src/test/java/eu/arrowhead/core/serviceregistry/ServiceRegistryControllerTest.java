package eu.arrowhead.core.serviceregistry;

import static org.junit.Assert.assertTrue;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.junit4.SpringRunner;

import eu.arrowhead.common.database.entity.ServiceDefinition;
import eu.arrowhead.common.database.service.ServiceRegistryDBService;
import eu.arrowhead.common.exception.BadPayloadException;
import eu.arrowhead.common.exception.InvalidParameterException;

@RunWith (SpringRunner.class)
public class ServiceRegistryControllerTest {
	
	//=================================================================================================
	// members

	@InjectMocks
	ServiceRegistryController serviceRegistryController;
	
	@Mock
	ServiceRegistryDBService serviceRegistryDBService;
	
	//=================================================================================================
	// methods
		
	//-------------------------------------------------------------------------------------------------
	
	@Test
	public void getServiceDefinitionsTest() {
		Page<ServiceDefinition> serviceDefinitionEntries = createServiceDefinitionPageForDBMocking(10);
		
		//Testing with null page but defined size input
		boolean isNullPageButDefinedSizeThrowBadPayloadException = false;
		try {
			serviceRegistryController.getServiceDefinitions(null, 5, null, null);
		} catch (BadPayloadException ex) {
			isNullPageButDefinedSizeThrowBadPayloadException = true;
		}
		assertTrue(isNullPageButDefinedSizeThrowBadPayloadException);
		
		//Testing with defined page but null size input
		boolean isDefinedPageButNullSizeThrowBadPayloadException = false;
		try {
			serviceRegistryController.getServiceDefinitions(0, null, null, null);
		} catch (BadPayloadException ex) {
			isDefinedPageButNullSizeThrowBadPayloadException = true;
		}
		assertTrue(isDefinedPageButNullSizeThrowBadPayloadException);
		
		//Testing with invalid sort direction flag input
		boolean isInvalidDirectionFlagThrowBadPayloadException = false;
		try {
			serviceRegistryController.getServiceDefinitions(null, null, "invalid", null);
		} catch (BadPayloadException ex) {
			isInvalidDirectionFlagThrowBadPayloadException = true;
		}
		assertTrue(isInvalidDirectionFlagThrowBadPayloadException);
		
		//Testing with blank sortFied input
		boolean isBlankSortFieldThrowBadPayloadException = false;
		try {
			serviceRegistryController.getServiceDefinitions(null, null, "ASC", "   ");
		} catch (BadPayloadException ex) {
			isBlankSortFieldThrowBadPayloadException = true;
		}
		assertTrue(isBlankSortFieldThrowBadPayloadException);
		
	}
	

	//=================================================================================================
	// assistant methods
	
	private Page<ServiceDefinition> createServiceDefinitionPageForDBMocking(int amountOfEntry) {
		List<ServiceDefinition> serviceDefinitionList = new ArrayList<>();
		for (int i = 0; i < amountOfEntry; i++) {
			ServiceDefinition serviceDefinition = new ServiceDefinition("mockedService" + i);
			serviceDefinition.setId(i);
			ZonedDateTime timeStamp = ZonedDateTime.now();
			serviceDefinition.setCreatedAt(timeStamp);
			serviceDefinition.setUpdatedAt(timeStamp);
			serviceDefinitionList.add(serviceDefinition);
		}
		Page<ServiceDefinition> entries = new PageImpl<ServiceDefinition>(serviceDefinitionList);
		return entries;
	}
}
