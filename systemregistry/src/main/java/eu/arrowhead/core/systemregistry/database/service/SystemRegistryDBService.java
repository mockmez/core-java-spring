package eu.arrowhead.core.systemregistry.database.service;

import eu.arrowhead.common.*;
import eu.arrowhead.common.database.entity.*;
import eu.arrowhead.common.database.entity.System;
import eu.arrowhead.common.database.repository.DeviceRepository;
import eu.arrowhead.common.database.repository.SystemRegistryRepository;
import eu.arrowhead.common.database.repository.SystemRepository;
import eu.arrowhead.common.dto.internal.DTOConverter;
import eu.arrowhead.common.dto.internal.DeviceListResponseDTO;
import eu.arrowhead.common.dto.internal.SystemListResponseDTO;
import eu.arrowhead.common.dto.internal.SystemRegistryListResponseDTO;
import eu.arrowhead.common.dto.shared.*;
import eu.arrowhead.common.exception.ArrowheadException;
import eu.arrowhead.common.exception.InvalidParameterException;
import jdk.jshell.execution.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class SystemRegistryDBService {

    //=================================================================================================
    // members

    private static final String COULD_NOT_DELETE_SYSTEM_ERROR_MESSAGE = "Could not delete System, with given parameters";
    private static final String COULD_NOT_DELETE_DEVICE_ERROR_MESSAGE = "Could not delete Device, with given parameters";
    private static final String PORT_RANGE_ERROR_MESSAGE = "Port must be between " + CommonConstants.SYSTEM_PORT_RANGE_MIN + " and " + CommonConstants.SYSTEM_PORT_RANGE_MAX + ".";

    private static final int MAX_BATCH_SIZE = 200;

    private final Logger logger = LogManager.getLogger(SystemRegistryDBService.class);


    @Autowired
    private SystemRegistryRepository systemRegistryRepository;

    @Autowired
    private SystemRepository systemRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private SSLProperties sslProperties;

    //=================================================================================================
    // methods

    //-------------------------------------------------------------------------------------------------
    public SystemResponseDTO getSystemById(final long systemId) {
        logger.debug("getSystemById started...");

        try {
            final Optional<System> systemOption = systemRepository.findById(systemId);
            if (systemOption.isEmpty()) {
                throw new InvalidParameterException("System with id " + systemId + " not found.");
            }

            return DTOConverter.convertSystemToSystemResponseDTO(systemOption.get());
        } catch (final InvalidParameterException ex) {
            throw ex;
        } catch (final Exception ex) {
            logger.debug(ex.getMessage(), ex);
            throw new ArrowheadException(CoreCommonConstants.DATABASE_OPERATION_EXCEPTION_MSG);
        }
    }


    //-------------------------------------------------------------------------------------------------
    public SystemListResponseDTO getSystemEntries(final CoreUtilities.ValidatedPageParams pageParams, final String sortField) {
        logger.debug("getSystemEntries started...");

        final int validatedPage = pageParams.getValidatedPage();
        final int validatedSize = pageParams.getValidatedSize();
        final Sort.Direction validatedDirection = pageParams.getValidatedDirection();
        final String validatedSortField = Utilities.isEmpty(sortField) ? CoreCommonConstants.COMMON_FIELD_NAME_ID : sortField.trim();

        if (!System.SORTABLE_FIELDS_BY.contains(validatedSortField)) {
            throw new InvalidParameterException("Sortable field with reference '" + validatedSortField + "' is not available");
        }

        try {
            return DTOConverter.convertSystemEntryListToSystemListResponseDTO(
                    systemRepository.findAll(PageRequest.of(validatedPage, validatedSize, validatedDirection, validatedSortField)));
        } catch (final Exception ex) {
            logger.debug(ex.getMessage(), ex);
            throw new ArrowheadException(CoreCommonConstants.DATABASE_OPERATION_EXCEPTION_MSG);
        }
    }

    //-------------------------------------------------------------------------------------------------
    @Transactional(rollbackFor = ArrowheadException.class)
    public SystemResponseDTO createSystemDto(final String systemName, final String address, final int port, final String authenticationInfo) {
        logger.debug("createSystemResponse started...");

        return DTOConverter.convertSystemToSystemResponseDTO(createSystem(systemName, address, port, authenticationInfo));
    }

    //-------------------------------------------------------------------------------------------------
    @Transactional(rollbackFor = ArrowheadException.class)
    public SystemResponseDTO updateSystemDto(final long systemId, final String systemName, final String address, final int port,
                                             final String authenticationInfo) {
        logger.debug("updateSystemResponse started...");

        return DTOConverter.convertSystemToSystemResponseDTO(updateSystem(systemId, systemName, address, port, authenticationInfo));
    }

    //-------------------------------------------------------------------------------------------------
    @Transactional(rollbackFor = ArrowheadException.class)
    public System updateSystem(final long systemId, final String systemName, final String address, final int port, final String authenticationInfo) {
        logger.debug("updateSystem started...");

        final long validatedSystemId = validateId(systemId);
        final int validatedPort = validateSystemPort(port);
        final String validatedSystemName = validateParamString(systemName);
        if (validatedSystemName.contains(".")) {
            throw new InvalidParameterException("System name can't contain dot (.)");
        }
        final String validatedAddress = validateParamString(address);


        try {
            final Optional<System> systemOptional = systemRepository.findById(validatedSystemId);
            if (systemOptional.isEmpty()) {
                throw new InvalidParameterException("No system with id : " + validatedSystemId);
            }

            final System system = systemOptional.get();

            if (checkSystemIfUniqueValidationNeeded(system, validatedSystemName, validatedAddress, validatedPort)) {
                checkConstraintsOfSystemTable(validatedSystemName, validatedAddress, validatedPort);
            }

            system.setSystemName(validatedSystemName);
            system.setAddress(validatedAddress);
            system.setPort(validatedPort);
            system.setAuthenticationInfo(authenticationInfo);

            return systemRepository.saveAndFlush(system);
        } catch (final InvalidParameterException ex) {
            throw ex;
        } catch (final Exception ex) {
            logger.debug(ex.getMessage(), ex);
            throw new ArrowheadException(CoreCommonConstants.DATABASE_OPERATION_EXCEPTION_MSG);
        }
    }

    //-------------------------------------------------------------------------------------------------
    @Transactional(rollbackFor = ArrowheadException.class)
    public void removeSystemById(final long id) {
        logger.debug("removeSystemById started...");

        try {
            if (!systemRepository.existsById(id)) {
                throw new InvalidParameterException(COULD_NOT_DELETE_SYSTEM_ERROR_MESSAGE);
            }

            systemRepository.deleteById(id);
            systemRepository.flush();
        } catch (final InvalidParameterException ex) {
            throw ex;
        } catch (final Exception ex) {
            logger.debug(ex.getMessage(), ex);
            throw new ArrowheadException(CoreCommonConstants.DATABASE_OPERATION_EXCEPTION_MSG);
        }
    }

    //-------------------------------------------------------------------------------------------------
    @Transactional(rollbackFor = ArrowheadException.class)
    public SystemResponseDTO mergeSystemResponse(final long systemId, final String systemName, final String address, final Integer port,
                                                 final String authenticationInfo) {
        logger.debug("mergeSystemResponse started...");

        return DTOConverter.convertSystemToSystemResponseDTO(mergeSystem(systemId, systemName, address, port, authenticationInfo));
    }

    //-------------------------------------------------------------------------------------------------
    @Transactional(rollbackFor = ArrowheadException.class)
    public System mergeSystem(final long systemId, final String systemName, final String address, final Integer port, final String authenticationInfo) {
        logger.debug("mergeSystem started...");

        final long validatedSystemId = validateId(systemId);
        final Integer validatedPort = validateAllowNullSystemPort(port);
        final String validatedSystemName = validateAllowNullParamString(systemName);
        if (validatedSystemName != null && validatedSystemName.contains(".")) {
            throw new InvalidParameterException("System name can't contain dot (.)");
        }
        final String validatedAddress = validateAllowNullParamString(address);

        try {
            final Optional<System> systemOptional = systemRepository.findById(validatedSystemId);
            if (systemOptional.isEmpty()) {
                throw new InvalidParameterException("No system with id : " + validatedSystemId);
            }

            final System system = systemOptional.get();

            if (checkSystemIfUniqueValidationNeeded(system, validatedSystemName, validatedAddress, validatedPort)) {
                checkConstraintsOfSystemTable(validatedSystemName != null ? validatedSystemName : system.getSystemName(),
                        validatedAddress != null ? validatedAddress : system.getAddress(),
                        validatedPort != null ? validatedPort : system.getPort());
            }

            if (Utilities.notEmpty(validatedSystemName)) {
                system.setSystemName(validatedSystemName);
            }

            if (Utilities.notEmpty(validatedAddress)) {
                system.setAddress(validatedAddress);
            }

            if (validatedPort != null) {
                system.setPort(validatedPort);
            }

            if (Utilities.notEmpty(authenticationInfo)) {
                system.setAuthenticationInfo(authenticationInfo);
            }

            return systemRepository.saveAndFlush(system);
        } catch (final InvalidParameterException ex) {
            throw ex;
        } catch (final Exception ex) {
            logger.debug(ex.getMessage(), ex);
            throw new ArrowheadException(CoreCommonConstants.DATABASE_OPERATION_EXCEPTION_MSG);
        }
    }


    //-------------------------------------------------------------------------------------------------
    public DeviceResponseDTO getDeviceById(long deviceId) {
        logger.debug("getDeviceById started...");

        try {
            final Optional<Device> deviceOptional = deviceRepository.findById(deviceId);
            if (deviceOptional.isEmpty()) {
                throw new InvalidParameterException("Device with id " + deviceId + " not found.");
            }

            return DTOConverter.convertDeviceToDeviceResponseDTO(deviceOptional.get());
        } catch (final InvalidParameterException ex) {
            throw ex;
        } catch (final Exception ex) {
            logger.debug(ex.getMessage(), ex);
            throw new ArrowheadException(CoreCommonConstants.DATABASE_OPERATION_EXCEPTION_MSG);
        }
    }

    //-------------------------------------------------------------------------------------------------
    public DeviceListResponseDTO getDeviceEntries(final CoreUtilities.ValidatedPageParams pageParams, final String sortField) {
        logger.debug("getDeviceList started...");

        final String validatedSortField = Utilities.isEmpty(sortField) ? CoreCommonConstants.COMMON_FIELD_NAME_ID : sortField.trim();

        if (!System.SORTABLE_FIELDS_BY.contains(validatedSortField)) {
            throw new InvalidParameterException("Sortable field with reference '" + validatedSortField + "' is not available");
        }

        try {
            final Page<Device> devices = deviceRepository.findAll(PageRequest.of(pageParams.getValidatedPage(), pageParams.getValidatedSize(),
                    pageParams.getValidatedDirection(), validatedSortField));
            return DTOConverter.convertDeviceEntryListToDeviceListResponseDTO(devices);
        } catch (final Exception ex) {
            logger.debug(ex.getMessage(), ex);
            throw new ArrowheadException(CoreCommonConstants.DATABASE_OPERATION_EXCEPTION_MSG);
        }
    }

    //-------------------------------------------------------------------------------------------------
    @Transactional(rollbackFor = ArrowheadException.class)
    public DeviceResponseDTO createDeviceDto(final String name, final String address, final String macAddress, final String authenticationInfo) {
        return DTOConverter.convertDeviceToDeviceResponseDTO(createDevice(name, address, macAddress, authenticationInfo));
    }

    //-------------------------------------------------------------------------------------------------
    @Transactional(rollbackFor = ArrowheadException.class)
    public DeviceResponseDTO updateDeviceByIdResponse(final long id, final String name, final String address, final String macAddress, final String authenticationInfo) {

        logger.debug("updateDeviceByIdResponse started...");

        try {

            final long validatedId = validateId(id);
            final Device newDevice = validateNonNullDeviceParameters(name, address, macAddress, authenticationInfo);

            final Optional<Device> optionalDevice = deviceRepository.findById(validatedId);
            final Device device = optionalDevice.orElseThrow(() -> new InvalidParameterException("No device with id : " + id));

            device.setDeviceName(newDevice.getDeviceName());
            device.setAddress(newDevice.getAddress());
            device.setMacAddress(newDevice.getMacAddress());
            device.setAuthenticationInfo(newDevice.getAuthenticationInfo());

            return DTOConverter.convertDeviceToDeviceResponseDTO(deviceRepository.saveAndFlush(device));
        } catch (final InvalidParameterException ex) {
            throw ex;
        } catch (final Exception ex) {
            logger.debug(ex.getMessage(), ex);
            throw new ArrowheadException(CoreCommonConstants.DATABASE_OPERATION_EXCEPTION_MSG);
        }
    }

    //-------------------------------------------------------------------------------------------------
    @Transactional(rollbackFor = ArrowheadException.class)
    public void removeDeviceById(final long id) {
        logger.debug("removeDeviceById started...");

        try {
            if (!deviceRepository.existsById(id)) {
                throw new InvalidParameterException(COULD_NOT_DELETE_DEVICE_ERROR_MESSAGE);
            }

            deviceRepository.deleteById(id);
            deviceRepository.flush();
        } catch (final InvalidParameterException ex) {
            throw ex;
        } catch (final Exception ex) {
            logger.debug(ex.getMessage(), ex);
            throw new ArrowheadException(CoreCommonConstants.DATABASE_OPERATION_EXCEPTION_MSG);
        }
    }

    //-------------------------------------------------------------------------------------------------
    @Transactional(rollbackFor = ArrowheadException.class)
    public SystemRegistryListResponseDTO getSystemRegistryEntries(final CoreUtilities.ValidatedPageParams params, final String sortField) {
        logger.debug("getSystemRegistryEntries started...");
        final String validatedSortField = sortField == null ? CoreCommonConstants.COMMON_FIELD_NAME_ID : sortField.trim();

        if (!SystemRegistry.SORTABLE_FIELDS_BY.contains(validatedSortField)) {
            throw new InvalidParameterException("Sortable field with reference '" + validatedSortField + "' is not available");
        }

        try {
            final PageRequest pageRequest = PageRequest.of(params.getValidatedPage(),
                    params.getValidatedSize(),
                    params.getValidatedDirection(),
                    validatedSortField);
            final Page<SystemRegistry> systemRegistryPage = systemRegistryRepository.findAll(pageRequest);
            return DTOConverter.convertSystemRegistryPageToSystemRegistryListResponseDTO(systemRegistryPage);
        } catch (final Exception ex) {
            logger.debug(ex.getMessage(), ex);
            throw new ArrowheadException(CoreCommonConstants.DATABASE_OPERATION_EXCEPTION_MSG);
        }
    }

    //-------------------------------------------------------------------------------------------------
    @Transactional(rollbackFor = ArrowheadException.class)
    public SystemRegistryResponseDTO getSystemRegistryById(final long id) {
        logger.debug("getSystemRegistryById started...");

        return DTOConverter.convertSystemRegistryToSystemRegistryResponseDTO(getSystemRegistryEntryById(id));
    }

    //-------------------------------------------------------------------------------------------------
    @Transactional(rollbackFor = ArrowheadException.class)
    public void removeSystemRegistryEntryById(final long id) {
        logger.debug("removeSystemRegistryEntryById started...");

        try {
            if (!systemRegistryRepository.existsById(id)) {
                throw new InvalidParameterException("System Registry entry with id '" + id + "' does not exists");
            }

            systemRegistryRepository.deleteById(id);
            systemRegistryRepository.flush();
        } catch (final InvalidParameterException ex) {
            throw ex;
        } catch (final Exception ex) {
            logger.debug(ex.getMessage(), ex);
            throw new ArrowheadException(CoreCommonConstants.DATABASE_OPERATION_EXCEPTION_MSG);
        }
    }


    //-------------------------------------------------------------------------------------------------
    @Transactional(rollbackFor = ArrowheadException.class)
    public SystemRegistryResponseDTO registerSystemRegistry(final SystemRegistryRequestDTO request) {
        logger.debug("registerSystemRegistry started...");
        checkSystemRegistryRequest(request);

        try {
            final System systemDb = findOrCreateSystem(request.getSystem());
            final Device deviceDb = findOrCreateDevice(request.getProvider());

            final ZonedDateTime endOfValidity = getZonedDateTime(request);
            final String metadataStr = Utilities.map2Text(request.getMetadata());
            final int version = (request.getVersion() != null) ? request.getVersion() : 1;
            final SystemRegistry srEntry = createSystemRegistry(systemDb, deviceDb, endOfValidity, metadataStr, version);

            return DTOConverter.convertSystemRegistryToSystemRegistryResponseDTO(srEntry);
        } catch (final DateTimeParseException ex) {
            logger.debug(ex.getMessage(), ex);
            throw new InvalidParameterException("End of validity is specified in the wrong format. Please provide UTC time using " + Utilities.getDatetimePattern() + " pattern.", ex);
        } catch (final InvalidParameterException ex) {
            throw ex;
        } catch (final Exception ex) {
            logger.debug(ex.getMessage(), ex);
            throw new ArrowheadException(CoreCommonConstants.DATABASE_OPERATION_EXCEPTION_MSG);
        }
    }

    //-------------------------------------------------------------------------------------------------
    @Transactional(rollbackFor = ArrowheadException.class)
    public SystemRegistryResponseDTO updateSystemRegistryById(final long id, final SystemRegistryRequestDTO request) {
        logger.debug("updateSystemRegistryById started...");

        Assert.isTrue(0 < id, "id is not greater than zero");
        checkSystemRegistryRequest(request);

        try {
            final SystemRegistry srEntry;
            final SystemRegistry updateSrEntry;

            final Optional<SystemRegistry> srEntryOptional = systemRegistryRepository.findById(id);
            srEntry = srEntryOptional.orElseThrow(() -> new InvalidParameterException("System Registry entry with id '" + id + "' not exists"));

            final System systemDb = findOrCreateSystem(request.getSystem());
            final Device deviceDb = findOrCreateDevice(request.getProvider());

            final ZonedDateTime endOfValidity = getZonedDateTime(request);
            final String metadataStr = Utilities.map2Text(request.getMetadata());
            final int version = (request.getVersion() != null) ? request.getVersion() : 1;
            updateSrEntry = updateSystemRegistry(srEntry, systemDb, deviceDb, endOfValidity, metadataStr, version);

            return DTOConverter.convertSystemRegistryToSystemRegistryResponseDTO(updateSrEntry);
        } catch (final DateTimeParseException ex) {
            logger.debug(ex.getMessage(), ex);
            throw new InvalidParameterException("End of validity is specified in the wrong format. Please provide UTC time using " + Utilities.getDatetimePattern() + " pattern.", ex);
        } catch (final InvalidParameterException ex) {
            throw ex;
        } catch (final Exception ex) {
            logger.debug(ex.getMessage(), ex);
            throw new ArrowheadException(CoreCommonConstants.DATABASE_OPERATION_EXCEPTION_MSG);
        }

    }

    //-------------------------------------------------------------------------------------------------
    @Transactional(rollbackFor = ArrowheadException.class)
    public SystemRegistryResponseDTO mergeSystemRegistryById(final long id, final SystemRegistryRequestDTO request) {

        logger.debug("mergeSystemRegistryById started...");
        Assert.notNull(request, "request is null.");
        Assert.isTrue(0 < id, "id is not greater than zero");

        try {
            final SystemRegistry srEntry;
            final SystemRegistry updateSrEntry;

            final Optional<SystemRegistry> srEntryOptional = systemRegistryRepository.findById(id);
            srEntry = srEntryOptional.orElseThrow(() -> new InvalidParameterException("System Registry entry with id '" + id + "' not exists"));

            final System systemDb = mergeSystem(request.getSystem(), srEntry.getSystem());
            final Device deviceDb = mergeDevice(request.getProvider(), srEntry.getDevice());

            final ZonedDateTime endOfValidity = Utilities.notEmpty(request.getEndOfValidity()) ?
                    Utilities.parseUTCStringToLocalZonedDateTime(request.getEndOfValidity().trim()) :
                    srEntry.getEndOfValidity();
            
            final String validatedMetadataStr = Objects.nonNull(request.getMetadata()) ? 
                    Utilities.map2Text(request.getMetadata()) : 
                    srEntry.getMetadata();
            
            final int validatedVersion = (request.getVersion() != null) ? request.getVersion() : srEntry.getVersion();

            updateSrEntry = updateSystemRegistry(srEntry, systemDb, deviceDb, endOfValidity, validatedMetadataStr, validatedVersion);

            return DTOConverter.convertSystemRegistryToSystemRegistryResponseDTO(updateSrEntry);
        } catch (final InvalidParameterException ex) {
            throw ex;
        } catch (final DateTimeParseException ex) {
            logger.debug(ex.getMessage(), ex);
            throw new InvalidParameterException("End of validity is specified in the wrong format. Please provide UTC time using " + Utilities.getDatetimePattern() + " pattern.", ex);
        } catch (final Exception ex) {
            logger.debug(ex.getMessage(), ex);
            throw new ArrowheadException(CoreCommonConstants.DATABASE_OPERATION_EXCEPTION_MSG);
        }
    }

    //-------------------------------------------------------------------------------------------------
    @Transactional(rollbackFor = ArrowheadException.class)
    public SystemRegistryListResponseDTO getSystemRegistryEntriesBySystemName(final String systemName, final CoreUtilities.ValidatedPageParams pageParameters, final String sortField) {
        final List<System> systemList = systemRepository.findBySystemName(systemName);
        final PageRequest pageRequest = PageRequest.of(pageParameters.getValidatedPage(), pageParameters.getValidatedSize(), pageParameters.getValidatedDirection());

        final Page<SystemRegistry> systemRegistries = systemRegistryRepository.findAllBySystem(systemList, pageRequest);
        return DTOConverter.convertSystemRegistryPageToSystemRegistryListResponseDTO(systemRegistries);
    }


    //-------------------------------------------------------------------------------------------------
    @Transactional(rollbackFor = ArrowheadException.class)
    public void removeSystemRegistryByNameAndAddressAndPort(final String systemName, final String address, final int port) {
        final System system = getSystemByNameAndAddressAndPort(systemName, address, port);

        final Optional<SystemRegistry> optionalSystemRegistry = systemRegistryRepository.findBySystem(system);
        final SystemRegistry systemRegistry = optionalSystemRegistry.orElseThrow(
                () -> new InvalidParameterException("System Registry entry for System with name '" + systemName + "', address '" + address + "' and port '" + port + "' does not exists"));

        systemRegistryRepository.delete(systemRegistry);
        systemRegistryRepository.flush();
    }


    //-------------------------------------------------------------------------------------------------
    @Transactional(rollbackFor = ArrowheadException.class)
    public SystemQueryResultDTO queryRegistry(final SystemQueryFormDTO form) {
        logger.debug("queryRegistry is started...");
        Assert.notNull(form, "Form is null.");
        Assert.isTrue(!Utilities.isEmpty(form.getServiceDefinitionRequirement()), "Service definition requirement is null or blank");

        final String serviceDefinitionRequirement = form.getServiceDefinitionRequirement().toLowerCase().trim();
        try {
            final Optional<ServiceDefinition> optServiceDefinition = serviceDefinitionRepository.findByServiceDefinition(serviceDefinitionRequirement);
            if (optServiceDefinition.isEmpty()) {
                // no service definition found
                logger.debug("Service definition not found: {}", serviceDefinitionRequirement);
                return DTOConverter.convertListOfServiceRegistryEntriesToServiceQueryResultDTO(null, 0);
            }

            final List<ServiceRegistry> providedServices = new ArrayList<>(serviceRegistryRepository.findByServiceDefinition(optServiceDefinition.get()));
            final int unfilteredHits = providedServices.size();
            logger.debug("Potential service providers before filtering: {}", unfilteredHits);
            if (providedServices.isEmpty()) {
                // no providers found
                return DTOConverter.convertListOfServiceRegistryEntriesToServiceQueryResultDTO(providedServices, unfilteredHits);
            }

            // filter on interfaces
            if (form.getInterfaceRequirements() != null && !form.getInterfaceRequirements().isEmpty()) {
                final List<String> normalizedInterfaceRequirements = RegistryUtils.normalizeInterfaceNames(form.getInterfaceRequirements());
                RegistryUtils.filterOnInterfaces(providedServices, normalizedInterfaceRequirements);
            }

            // filter on security type
            if (!providedServices.isEmpty() && form.getSecurityRequirements() != null && !form.getSecurityRequirements().isEmpty()) {
                final List<ServiceSecurityType> normalizedSecurityTypes = RegistryUtils.normalizeSecurityTypes(form.getSecurityRequirements());
                RegistryUtils.filterOnSecurityType(providedServices, normalizedSecurityTypes);
            }

            // filter on version
            if (!providedServices.isEmpty()) {
                if (form.getVersionRequirement() != null) {
                    RegistryUtils.filterOnVersion(providedServices, form.getVersionRequirement().intValue());
                } else if (form.getMinVersionRequirement() != null || form.getMaxVersionRequirement() != null) {
                    final int minVersion = form.getMinVersionRequirement() == null ? 1 : form.getMinVersionRequirement().intValue();
                    final int maxVersion = form.getMaxVersionRequirement() == null ? Integer.MAX_VALUE : form.getMaxVersionRequirement().intValue();
                    RegistryUtils.filterOnVersion(providedServices, minVersion, maxVersion);
                }
            }

            // filter on metadata
            if (!providedServices.isEmpty() && form.getMetadataRequirements() != null && !form.getMetadataRequirements().isEmpty()) {
                final Map<String,String> normalizedMetadata = RegistryUtils.normalizeMetadata(form.getMetadataRequirements());
                RegistryUtils.filterOnMeta(providedServices, normalizedMetadata);
            }

            // filter on ping
            if (!providedServices.isEmpty() && form.getPingProviders()) {
                RegistryUtils.filterOnPing(providedServices, pingTimeout);
            }

            logger.debug("Potential service providers after filtering: {}", providedServices.size());

            return DTOConverter.convertListOfServiceRegistryEntriesToServiceQueryResultDTO(providedServices, unfilteredHits);
        } catch (final IllegalStateException e) {
            throw new InvalidParameterException("Invalid keys in the metadata requirements (whitespace only differences)");
        } catch (final Exception ex) {
            logger.debug(ex.getMessage(), ex);
            throw new ArrowheadException(CoreCommonConstants.DATABASE_OPERATION_EXCEPTION_MSG);
        }
    }


    //-------------------------------------------------------------------------------------------------
    @Transactional(rollbackFor = ArrowheadException.class)
    public SystemResponseDTO getSystemDtoByNameAndAddressAndPort(final String systemName, final String address, final int port) {
        final System system = getSystemByNameAndAddressAndPort(systemName, address, port);
        return DTOConverter.convertSystemToSystemResponseDTO(system);
    }

    //=================================================================================================
    // assistant methods
    //-------------------------------------------------------------------------------------------------
    private System getSystemByNameAndAddressAndPort(final String systemName, final String address, final int port) {
        final Optional<System> optionalSystem = systemRepository.findBySystemNameAndAddressAndPort(systemName, address, port);
        return optionalSystem.orElseThrow(() -> new InvalidParameterException("System entry with name '" + systemName + "', address '" + address + "' and port '" + port + "' does not exists"));
    }

    //-------------------------------------------------------------------------------------------------
    @SuppressWarnings("squid:S1126")
    private boolean checkSystemIfUniqueValidationNeeded(final System system, final String validatedSystemName, final String validatedAddress,
                                                        final Integer validatedPort) {
        logger.debug("checkSystemIfUniqueValidationNeeded started...");

        final String actualSystemName = system.getSystemName();
        final String actualAddress = system.getAddress();
        final int actualPort = system.getPort();

        if (validatedSystemName != null && !actualSystemName.equalsIgnoreCase(validatedSystemName)) {
            return true;
        } else if (validatedAddress != null && !actualAddress.equalsIgnoreCase(validatedAddress)) {
            return true;
        } else return validatedPort != null && actualPort != validatedPort;
    }

    //-------------------------------------------------------------------------------------------------
    private void checkConstraintsOfSystemTable(final String validatedSystemName, final String validatedAddress, final int validatedPort) {
        logger.debug("checkConstraintsOfSystemTable started...");

        try {
            final Optional<System> find = systemRepository
                    .findBySystemNameAndAddressAndPort(validatedSystemName.toLowerCase().trim(), validatedAddress.toLowerCase().trim(), validatedPort);
            if (find.isPresent()) {
                throw new InvalidParameterException(
                        "System with name: " + validatedSystemName + ", address: " + validatedAddress + ", port: " + validatedPort + " already exists.");
            }
        } catch (final InvalidParameterException ex) {
            throw ex;
        } catch (final Exception ex) {
            logger.debug(ex.getMessage(), ex);
            throw new ArrowheadException(CoreCommonConstants.DATABASE_OPERATION_EXCEPTION_MSG);
        }
    }

    //-------------------------------------------------------------------------------------------------
    private void checkConstraintsOfDeviceTable(final String validatedDeviceName, final String validatedMacAddress) {
        logger.debug("checkConstraintsOfDeviceTable started...");

        try {
            final Optional<Device> find = deviceRepository.findByDeviceNameAndMacAddress(validatedDeviceName, validatedMacAddress);
            if (find.isPresent()) {
                throw new InvalidParameterException(
                        "Device with name: " + validatedDeviceName + ", MAC address: " + validatedMacAddress + " already exists.");
            }
        } catch (final InvalidParameterException ex) {
            throw ex;
        } catch (final Exception ex) {
            logger.debug(ex.getMessage(), ex);
            throw new ArrowheadException(CoreCommonConstants.DATABASE_OPERATION_EXCEPTION_MSG);
        }
    }

    //-------------------------------------------------------------------------------------------------
    private System validateNonNullSystemParameters(final String systemName, final String address, final int port, final String authenticationInfo) {
        logger.debug("validateNonNullSystemParameters started...");

        validateNonNullParameters(systemName, address, authenticationInfo);

        if (port < CommonConstants.SYSTEM_PORT_RANGE_MIN || port > CommonConstants.SYSTEM_PORT_RANGE_MAX) {
            throw new InvalidParameterException(PORT_RANGE_ERROR_MESSAGE);
        }

        final String validatedSystemName = Utilities.lowerCaseTrim(systemName);
        final String validatedAddress = Utilities.lowerCaseTrim(address);

        checkConstraintsOfSystemTable(validatedSystemName, validatedAddress, port);

        return new System(validatedSystemName, validatedAddress, port, authenticationInfo);
    }

    //-------------------------------------------------------------------------------------------------
    private Device validateNonNullDeviceParameters(final String deviceName, final String address, final String macAddress, final String authenticationInfo) {
        logger.debug("validateNonNullDeviceParameters started...");

        validateNonNullParameters(deviceName, address, authenticationInfo);

        if (Utilities.isEmpty(macAddress)) {
            throw new InvalidParameterException("MAC address is null or empty");
        }

        final String validatedDeviceName = Utilities.lowerCaseTrim(deviceName);
        final String validatedAddress = Utilities.lowerCaseTrim(address);
        final String validatedMacAddress = macAddress.trim().toUpperCase();

        checkConstraintsOfDeviceTable(validatedDeviceName, validatedMacAddress);

        return new Device(validatedDeviceName, validatedAddress, validatedMacAddress, authenticationInfo);
    }

    //-------------------------------------------------------------------------------------------------
    private void validateNonNullParameters(final String name, final String address, final String authenticationInfo) {
        logger.debug("validateNonNullParameters started...");

        if (Utilities.isEmpty(name)) {
            throw new InvalidParameterException("Name is null or empty");
        }

        if (Utilities.isEmpty(address)) {
            throw new InvalidParameterException("Address is null or empty");
        }

        if (name.contains(".")) {
            throw new InvalidParameterException("Name can't contain dot (.)");
        }
    }

    //-------------------------------------------------------------------------------------------------
    private String validateParamString(final String param) {
        logger.debug("validateSystemParamString started...");

        if (Utilities.isEmpty(param)) {
            throw new InvalidParameterException("parameter null or empty");
        }

        return Utilities.lowerCaseTrim(param);
    }

    //-------------------------------------------------------------------------------------------------
    private int validateSystemPort(final int port) {
        logger.debug("validateSystemPort started...");

        if (port < CommonConstants.SYSTEM_PORT_RANGE_MIN || port > CommonConstants.SYSTEM_PORT_RANGE_MAX) {
            throw new InvalidParameterException(PORT_RANGE_ERROR_MESSAGE);
        }

        return port;
    }

    //-------------------------------------------------------------------------------------------------
    private long validateId(final long id) {
        logger.debug("validateId started...");

        if (id < 1) {
            throw new IllegalArgumentException("Id must be greater than zero");
        }

        return id;
    }

    //-------------------------------------------------------------------------------------------------
    private Integer validateAllowNullSystemPort(final Integer port) {
        logger.debug("validateAllowNullSystemPort started...");

        if (port != null && (port < CommonConstants.SYSTEM_PORT_RANGE_MIN || port > CommonConstants.SYSTEM_PORT_RANGE_MAX)) {
            throw new IllegalArgumentException(PORT_RANGE_ERROR_MESSAGE);
        }

        return port;
    }

    //-------------------------------------------------------------------------------------------------
    private String validateAllowNullParamString(final String param) {
        logger.debug("validateAllowNullParamString started...");

        if (Utilities.isEmpty(param)) {
            return null;
        }

        return Utilities.lowerCaseTrim(param);
    }


    //-------------------------------------------------------------------------------------------------
    private SystemRegistry getSystemRegistryEntryById(final long id) {
        logger.debug("getSystemRegistryEntryById started...");
        try {
            final Optional<SystemRegistry> systemRegistry = systemRegistryRepository.findById(id);
            return systemRegistry.orElseThrow(() -> new InvalidParameterException("System Registry with id of '" + id + "' not exists"));
        } catch (final InvalidParameterException ex) {
            throw ex;
        } catch (final Exception ex) {
            logger.debug(ex.getMessage(), ex);
            throw new ArrowheadException(CoreCommonConstants.DATABASE_OPERATION_EXCEPTION_MSG);
        }
    }

    //-------------------------------------------------------------------------------------------------
    private System findOrCreateSystem(SystemRequestDTO requestSystemDto) {
        return findOrCreateSystem(requestSystemDto.getSystemName(),
                requestSystemDto.getAddress(),
                requestSystemDto.getPort(),
                requestSystemDto.getAuthenticationInfo());
    }

    //-------------------------------------------------------------------------------------------------
    private System findOrCreateSystem(final String name, final String address, final int port, final String authenticationInfo) {

        final String validatedName = Utilities.lowerCaseTrim(name);
        final String validatedAddress = Utilities.lowerCaseTrim(address);
        final String validatedAuthenticationInfo = Utilities.lowerCaseTrim(authenticationInfo);

        final Optional<System> optSystem = systemRepository.findBySystemNameAndAddressAndPort(validatedName, validatedAddress, port);
        System provider;

        if (optSystem.isPresent()) {
            provider = optSystem.get();
            if (!Objects.equals(validatedAuthenticationInfo, provider.getAuthenticationInfo()) ||
                    !Objects.equals(validatedAddress, provider.getAddress())) { // authentication info or system has changed
                provider.setAuthenticationInfo(validatedAuthenticationInfo);
                provider.setAddress(validatedAddress);
                provider = systemRepository.saveAndFlush(provider);
            }
        } else {
            provider = createSystem(validatedName, validatedAddress, port, validatedAuthenticationInfo);
        }
        return provider;
    }

    //-------------------------------------------------------------------------------------------------
    private System createSystem(final String systemName, final String address, final int port, final String authenticationInfo) {
        logger.debug("createSystem started...");

        final System system = validateNonNullSystemParameters(systemName, address, port, authenticationInfo);

        try {
            return systemRepository.saveAndFlush(system);
        } catch (final Exception ex) {
            logger.debug(ex.getMessage(), ex);
            throw new ArrowheadException(CoreCommonConstants.DATABASE_OPERATION_EXCEPTION_MSG);
        }
    }

    //-------------------------------------------------------------------------------------------------
    private Device findOrCreateDevice(DeviceRequestDTO requestDeviceDto) {
        return findOrCreateDevice(requestDeviceDto.getDeviceName(),
                requestDeviceDto.getAddress(),
                requestDeviceDto.getMacAddress(),
                requestDeviceDto.getAuthenticationInfo());
    }

    //-------------------------------------------------------------------------------------------------
    private Device findOrCreateDevice(String deviceName, String address, String macAddress, String authenticationInfo) {

        final String validateName = Utilities.lowerCaseTrim(deviceName);
        final String validateAddress = Utilities.lowerCaseTrim(address);
        final String validatedMacAddress = Utilities.lowerCaseTrim(macAddress);
        final String validatedAuthenticationInfo = Utilities.lowerCaseTrim(authenticationInfo);

        final Optional<Device> optProvider = deviceRepository.findByDeviceNameAndMacAddress(validateName, validatedMacAddress);
        Device provider;

        if (optProvider.isPresent()) {
            provider = optProvider.get();
            if (!Objects.equals(validatedAuthenticationInfo, provider.getAuthenticationInfo()) ||
                    !Objects.equals(validateAddress, provider.getAddress())) { // authentication info or provider has changed
                provider.setAuthenticationInfo(validatedAuthenticationInfo);
                provider.setAddress(validateAddress);
                provider = deviceRepository.saveAndFlush(provider);
            }
        } else {
            provider = createDevice(validateName, validateAddress, validatedMacAddress, validatedAuthenticationInfo);
        }
        return provider;
    }

    //-------------------------------------------------------------------------------------------------
    private Device createDevice(final String name, final String address, final String macAddress, final String authenticationInfo) {
        final Device device = new Device(name, address, macAddress, authenticationInfo);
        return deviceRepository.save(device);
    }

    //-------------------------------------------------------------------------------------------------
    private void checkSystemRegistryRequest(final SystemRegistryRequestDTO request) {
        logger.debug("checkSystemRegistryRequest started...");
        Assert.notNull(request, "Request is null.");

        Assert.notNull(request.getSystem(), "System is not specified.");
        Assert.isTrue(Utilities.notEmpty(request.getSystem().getSystemName()), "System name is not specified.");
        Assert.isTrue(Utilities.notEmpty(request.getSystem().getAddress()), "System address is not specified.");
        Assert.notNull(request.getSystem().getPort(), "System port is not specified.");

        Assert.notNull(request.getProvider(), "Provider Device is not specified.");
        Assert.isTrue(Utilities.notEmpty(request.getProvider().getDeviceName()), "Provider Device name is not specified.");
        Assert.isTrue(Utilities.notEmpty(request.getProvider().getAddress()), "Provider Device address is not specified.");
        Assert.isTrue(Utilities.notEmpty(request.getProvider().getMacAddress()), "Provider Device MAC is not specified.");
    }

    //-------------------------------------------------------------------------------------------------
    private void checkConstraintsOfSystemRegistryTable(final System systemDb, final Device deviceDb) {
        logger.debug("checkConstraintOfSystemRegistryTable started...");

        try {
            final Optional<SystemRegistry> find = systemRegistryRepository.findBySystemAndDevice(systemDb, deviceDb);
            if (find.isPresent()) {
                throw new InvalidParameterException("System Registry entry with provider: (" + deviceDb.getDeviceName() + ", " + deviceDb.getMacAddress() +
                        ") and system : " + systemDb.getSystemName() + " already exists.");
            }
        } catch (final InvalidParameterException ex) {
            throw ex;
        } catch (final Exception ex) {
            logger.debug(ex.getMessage(), ex);
            throw new ArrowheadException(CoreCommonConstants.DATABASE_OPERATION_EXCEPTION_MSG);
        }
    }

    //-------------------------------------------------------------------------------------------------
    private Device mergeDevice(final DeviceRequestDTO request, final Device device) {
        final String name = Utilities.firstNotNull(request.getDeviceName(), device.getDeviceName());
        final String address = Utilities.firstNotNull(request.getAddress(), device.getAddress());
        final String macAddress = Utilities.firstNotNull(request.getDeviceName(), device.getDeviceName());
        final String authenticationInfo = Utilities.firstNotNull(request.getAuthenticationInfo(), device.getAuthenticationInfo());
        return findOrCreateDevice(name, address, macAddress, authenticationInfo);
    }

    //-------------------------------------------------------------------------------------------------
    private System mergeSystem(final SystemRequestDTO request, final System system) {
        final String name = Utilities.firstNotNull(request.getSystemName(), system.getSystemName());
        final String address = Utilities.firstNotNull(request.getAddress(), system.getAddress());
        final int port = request.getPort() > 0 ? request.getPort() : system.getPort();
        final String authenticationInfo = Utilities.firstNotNull(request.getAuthenticationInfo(), system.getAuthenticationInfo());
        return findOrCreateSystem(name, address, port, authenticationInfo);
    }

    //-------------------------------------------------------------------------------------------------
    private SystemRegistry createSystemRegistry(final System systemDb, final Device deviceDb, final ZonedDateTime endOfValidity, final String metadataStr, final int version) {
        logger.debug("createSystemRegistry started...");

        checkConstraintsOfSystemRegistryTable(systemDb, deviceDb);

        try {
            return systemRegistryRepository.saveAndFlush(new SystemRegistry(systemDb, deviceDb, endOfValidity, metadataStr, version));
        } catch (final Exception ex) {
            logger.debug(ex.getMessage(), ex);
            throw new ArrowheadException(CoreCommonConstants.DATABASE_OPERATION_EXCEPTION_MSG);
        }
    }

    //-------------------------------------------------------------------------------------------------
    private boolean checkSystemRegistryIfUniqueValidationNeeded(final SystemRegistry srEntry, final System system, final Device device) {
        logger.debug("checkSystemRegistryIfUniqueValidationNeeded started...");

        return srEntry.getSystem().getId() != system.getId() || srEntry.getDevice().getId() != device.getId();

    }

    //-------------------------------------------------------------------------------------------------
    private SystemRegistry updateSystemRegistry(final SystemRegistry srEntry, final System system, final Device device,
                                                final ZonedDateTime endOfValidity, final String metadataStr, final int version) {
        logger.debug("updateSystemRegistry started...");
        Assert.notNull(srEntry, "SystemRegistry Entry is not specified.");
        Assert.notNull(system, "System is not specified.");
        Assert.notNull(device, "Device is not specified.");

        if (checkSystemRegistryIfUniqueValidationNeeded(srEntry, system, device)) {
            checkConstraintsOfSystemRegistryTable(system, device);
        }

        return setModifiedValuesOfSystemRegistryEntryFields(srEntry, system, device, endOfValidity, metadataStr, version);
    }

    //-------------------------------------------------------------------------------------------------
    private ZonedDateTime getZonedDateTime(SystemRegistryRequestDTO request) {
        return Utilities.isEmpty(request.getEndOfValidity()) ? null : Utilities.parseUTCStringToLocalZonedDateTime(request.getEndOfValidity().trim());
    }

    //-------------------------------------------------------------------------------------------------
    private SystemRegistry setModifiedValuesOfSystemRegistryEntryFields(final SystemRegistry srEntry, final System system, final Device device,
                                                                        final ZonedDateTime endOfValidity, final String metadataStr, final int version) {

        logger.debug("setModifiedValuesOfSystemRegistryEntryFields started...");

        try {
            srEntry.setSystem(system);
            srEntry.setDevice(device);
            srEntry.setEndOfValidity(endOfValidity);
            srEntry.setMetadata(metadataStr);
            srEntry.setVersion(version);

            return systemRegistryRepository.saveAndFlush(srEntry);
        } catch (final Exception ex) {
            logger.debug(ex.getMessage(), ex);
            throw new ArrowheadException(CoreCommonConstants.DATABASE_OPERATION_EXCEPTION_MSG);
        }
    }
}
