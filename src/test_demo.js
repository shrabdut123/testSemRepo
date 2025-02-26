/*
# Centiro Fetch Module

This module is responsible for fetching data from Centiro, a cloud-based delivery management system. It includes error handling for various types of errors that may occur during the fetch process.

## Import Statements

The module imports various configurations, error types, helper functions, and utilities from other modules in the application.

## Constants

The module defines several constants for error codes, error messages, and order sources.

## Functions

### getOrderSourceMapping(locality)

This function takes a locality as an argument and returns the corresponding order source. If the locality does not have an order source mapping, it logs an error and returns the first order source.

### buildUrlDSM(path, retailId)

This function takes a path and a retailId as arguments and returns a URL for the DSM endpoint. The URL is built based on the region corresponding to the retailId. If the region is not 'CN', the URL includes the version 'v2' for all paths except 'GetServiceCompatibility'. If the region is 'CN', the URL does not include the version.

### centiroFetch(path, body, requestId, version, retailId, zipCode)

This function is responsible for fetching data from Centiro. It takes a path, body, requestId, version, retailId, and zipCode as arguments. It builds the URL for the fetch request based on whether the DSM feature is enabled in the configuration. It sets the headers for the request and sends a POST request to the URL with the body. If an error occurs during the fetch process, it logs the error and throws a ConnectionError.

## Error Handling

The module includes error handling for various types of errors that may occur during the fetch process, including input errors, connection errors, and specific errors related to zip codes, business units, service codes, localities, and capacity. It defines error codes and error messages for these errors.
*/
import { Config } from '../config/config';
import {
  InputError,
  ConnectionError,
  BuError,
  CapacityError,
  ZipCodeError,
  ServiceCodeError,
  LocalityError,
  ErrorCauses,
  ServiceProviderError,
} from '../errors';
import { ErrorCodes, ErrorMessages } from '../errors/errorConstants';
import soFetcherObject from '../helpers/soFetcher';
import {
  isValidServiceById,
  getHSU,
  Log,
  processZipcode,
  sortAccordingTo,
  getLocalityMapping,
} from '../helpers/helpers';
import { get as getFromCache, set as setToCache, CacheDb } from '../util/redis';
import { log } from '../helpers/logger';

const zipCodeErrorCode = 'UNDEFINED_ZIPCODE';
const buErrorCode = 'UNDEFINED_ORIGINATOR';
const invalidServiceCode = 'UNDEFINED_SERVICE_CODE';

const localityErrorCode = {
  UNDEFINED_LANGUAGE: ['retailId'],
  UNDEFINED_AREA: ['retailId', 'locality'],
};
const inputErrorCode = {
  UNKNOWN_TSP_TIME_WINDOW: ['services[].deliveryTimeWindowId'],
  NO_CAPACITY_INPUT: ['???'], // ??
};

const invalidRequestErrorCode = 'INVALID_REQUEST';
const invalidRequestKeyword = {
  transportTimewindowId: ['deliveryTimeWindowId'],
  startDate: ['startDate'],
  OrderNumberSource: ['locality'],
  ZipCode: ['zipCode'],
  CapacityUnit: ['capacityUnit'],
  BUCode: ['businessUnit.buCode', 'businessUnit.buType'],
  BUType: ['businessUnit.buCode', 'businessUnit.buType'],
};

const connectionErrorCode = ['INTERNAL_ERROR', 'TEMPORARY_FAILURE'];

const noAvailableSlotsCode = ['NO_CAPACITY_FOUND', 'NO_TIME_WINDOW_FOUND'];

const orderSource = {
  EU: 'A01',
  AP: 'A02',
  NA: 'A03',
  CN: 'A04',
  RU: 'A05',
  GB: 'A06',
};

const getOrderSourceMapping = (locality) => {
  const source = orderSource?.[locality];
  if (!source) {
    Log.incorrectLocality({
      message: `Centiro call was made with locality (${locality}) that doesn't have an order source mapping!`,
      system: 'Centiro',
    });
    return orderSource[0];
  }
  return source;
};

// eslint-disable-next-line consistent-return
const buildUrlDSM = (path, retailId) => {
  const region = getLocalityMapping(retailId);
  if (region !== 'CN') {
    // If we target GetServiceCompatibility then v2 is not supported.
    // The rest of the paths that we use supports v2.
    const apiVersion = path === 'GetServiceCompatibility' ? '' : '/v2';
    return `${Config.config.dsm.endpoint}${apiVersion}/capacityapplication/${path}`;
  }
  if (region === 'CN') {
    return `${Config.config.dsm.endpoint}/capacityapplication/${path}`;
  }
};

const centiroFetch = async (path, body, requestId, version, retailId, zipCode) => {
  const url = Config.config.feature.enableDSM
    ? buildUrlDSM(path, retailId)
    : `${Config.config.centiro.endpoint}/${path}`;
  const headers = {
    'Content-Type': 'application/json',
    Accept: `application/vnd.centiro+json;version=v${version}`,
  };
  const response = await soFetcherObject
    .soFetcher({
      url,
      payload: {
        method: 'POST',
        headers,
        body: JSON.stringify(body),
      },
      system: 'centiro',
      requestId,
      retailId,
    })
    .catch(async (fetch) => {
      if (typeof fetch?.response?.json !== 'function') {
        if (!fetch) {
          console.log('Fetch is undefined');
        } else if (!fetch.response) {
          console.log('response is missing', JSON.stringify(fetch));
        } else if (typeof fetch.response.clone !== 'function') {
          console.log('clone is not a function, have a json!', JSON.stringify(fetch));
        }
        throw new ConnectionError(ErrorCauses.CENTIRO_CONNECTION_PROBLEM, {
          statusCode: 500,
          errorDescription: 'Unable to retrieve proper response from Centiro, time out',
          requestId,
          retailId,
          log: {
            system: 'centiro',
            url,
            payload: body,
            response: await fetch?.response?.clone().text(),
            centiroConnectionError: true,
          },
        });
      }
      await fetch.response
        .clone()
        .json()
        .catch(async (jsonParseError) => {
          throw new ConnectionError(ErrorCauses.CENTIRO_CONNECTION_PROBLEM, {
            statusCode: 500,
            errorDescription: 'Unable to retrieve proper response from Centiro, did not receive JSON response',
            requestId,
            retailId,
            log: {
              system: 'centiro',
              url,
              payload: body,
              jsonParseError,
              response: await fetch?.response?.clone().text(),
              centiroConnectionError: true,
            },
          });
        })
        .then((responseJSON) => {
          // Error can be at top level or in service-line depending on what kind of request was made
          const errorCode =
            responseJSON?.error?.errorCode ||
            responseJSON?.getServiceDateProposalResponse?.proposedServiceTypes.find((proposedServiceType) =>
              proposedServiceType.serviceLines.find((serviceLine) => serviceLine?.error?.errorCode),
            )?.serviceLines[0].error.errorCode;
          const errorMessage = responseJSON?.error?.errorDescription;
          if (invalidServiceCode === errorCode) {
            throw new ServiceCodeError(ErrorCauses.SERVICECODE_NOT_MATCHED, {
              errorCode: 422,
              field: ['services[].serviceCode'],
              requestId,
              retailId,
            });
          }
          if (Object.keys(localityErrorCode).includes(errorCode)) {
            throw new LocalityError(ErrorCauses.LOCALITY_NOT_DEFINED, {
              statusCode: 422,
              value: [retailId, zipCode],
              errorDescription: 'The provided retail ID and/or locality combination is not available',
              field: localityErrorCode[errorCode],
              requestId,
              logAsInfo: true,
              retailId,
            });
          }
          if (zipCodeErrorCode === errorCode) {
            throw new ZipCodeError(ErrorCauses.LOCALITY_NOT_DEFINED, {
              errorCode: 422,
              field: ['zipCode'],
              requestId,
              logAsInfo: true,
              retailId,
            });
          }
          if (buErrorCode === errorCode) {
            throw new BuError(ErrorCauses.BU_NOT_DEFINED_CENTIRO, {
              statusCode: 422,
              field: ['businessUnit.buCode', 'businessUnit.buType'],
              requestId,
              retailId,
            });
          }
          if (Object.keys(inputErrorCode).includes(errorCode)) {
            throw new InputError(ErrorCauses.INPUT_INVALID_BY_CENTIRO, {
              statusCode: 422,
              field: inputErrorCode[errorCode],
              requestId,
              retailId,
            });
          }
          if (invalidRequestErrorCode === errorCode && errorMessage) {
            const errorField = Object.keys(invalidRequestKeyword).find((keyword) =>
              errorMessage.match(`.*${keyword}.*`),
            );
            throw new InputError(ErrorCauses.INPUT_INVALID_BY_CENTIRO, {
              statusCode: 422,
              ...(errorField && {
                field: invalidRequestKeyword[errorField],
                errorDescription: `Validation failed. Centiro was unable to parse the field ${invalidRequestKeyword[errorField]}`,
              }),
              ...(!errorField && {
                downstreamErrorMessage: errorMessage,
              }),
              requestId,
              logAsInfo: true,
              retailId,
            });
          }
          if (noAvailableSlotsCode.includes(errorCode)) {
            throw new CapacityError(ErrorCauses.SERVICE_TIME_WINDOW_FOR_START_DATE, {
              statusCode: 404,
              value: [retailId, zipCode],
              errorDescription:
                'No service time windows with capacity could be found after the provided start date from Centiro',
              requestId,
              retailId,
              logAsInfo: true,
            });
          }
          if (ErrorCodes.NO_SERVICE_PROVIDER_AVAILABLE === errorCode) {
            throw new ServiceProviderError(ErrorCauses.NO_SERVICE_PROVIDER_AVAILABLE, {
              statusCode: 422,
              value: [retailId, zipCode],
              errorDescription: ErrorMessages.NO_SERVICE_PROVIDER_AVAILABLE,
              requestId,
              retailId,
              logAsInfo: true,
            });
          }
          if (connectionErrorCode.includes(errorCode)) {
            throw new ConnectionError(ErrorCauses.CENTIRO_CONNECTION_PROBLEM, {
              statusCode: 500,
              errorDescription: `Centiro response failed with ${errorCode}`,
              requestId,
              retailId,
              log: {
                system: 'centiro',
                response: responseJSON,
                url,
              },
            });
          }
          throw new ConnectionError(ErrorCauses.CENTIRO_CONNECTION_PROBLEM, {
            statusCode: 500,
            errorDescription: 'Unable to retrieve proper response from Centiro',
            requestId,
            retailId,
            log: {
              system: 'centiro',
              response: responseJSON,
              url,
              payload: JSON.stringify(body),
              requestId,
            },
          });
        });
    });

  return response
    ?.json()
    .then((responseJSON) => responseJSON)
    .catch(() => {
      if (response.status === 204) return {};
      throw new ConnectionError(ErrorCauses.CENTIRO_CONNECTION_PROBLEM, {
        statusCode: response.status,
        errorDescription: 'Unable to retrieve proper response from Centiro, did not receive JSON response',
        requestId,
        retailId,
        log: {
          system: 'centiro',
          url,
          payload: JSON.stringify(body),
        },
      });
    });
};

const validateServiceDateProposal = async (args) => {
  const locality = args.locality ? args.locality.toUpperCase() : getLocalityMapping(args.retailId, args.requestId);
  const body = {
    validateServiceDateProposalRequest: {
      shipToAddress: {
        country: args.retailId,
        zipCode: args.zipCode,
        state: args.state,
      },
      businessUnit: {
        buCode: args?.businessUnit?.buCode || getHSU(args.retailId),
        buType: args?.businessUnit?.buType || 'STO',
      },
      orderKey: {
        orderNumber: args.orderKey?.orderNumber || '999999999',
        orderNumberSource: args.orderKey?.orderNumberSource || getOrderSourceMapping(locality),
      },
      serviceLines: args.services.map((service) => ({
        serviceCode: service.serviceCode,
        capacityUnit: service.capacityUnit,
        capacityNeeded: null,
        serviceProviderId: service.serviceProviderId,
        timeWindowId: service.serviceTimeWindowId,
        fromDate: service.startDate || null,
        serviceNo: service.serviceNo,
        manualChangeType: service.manualChangeType || 'NO_CHANGE',
        transportTimewindowId: service.deliveryTimeWindowId || null,
      })),
    },
    messageId: args.retailId,
    userId: 'OnlineServiceOffers',
    locality,
  };
  const resp = await centiroFetch('ValidateServiceDateProposal', body, 0);
  return resp.validationServiceResponse?.validationServiceTypes
    .flatMap((serviceType) => serviceType.serviceLines)
    .reduce((acc, serviceLine) => {
      // Error from Centiro here implies time window is not available
      if (serviceLine.error) {
        acc[serviceLine.serviceNo] = {
          available: false,
          reasonCode: serviceLine.error.errorCode,
          reason: serviceLine.error.errorDescription || serviceLine.noCapacityFoundReason,
          ...serviceLine,
        };
        return acc;
      }
      // Merge timeWindows for same provider and service instead of polluting the response
      if (acc[serviceLine.serviceNo]) {
        acc[serviceLine.serviceNo].timeWindows = [
          ...acc[serviceLine.serviceNo].timeWindows,
          ...serviceLine.serviceProvider.timeWindows,
        ];
      } else {
        const formattedServiceLine = {
          ...serviceLine,
          ...serviceLine.serviceProvider,
          available: true,
        };
        delete formattedServiceLine.serviceProvider;
        acc[serviceLine.serviceNo] = formattedServiceLine;
      }
      return acc;
    }, []);
};

const filterPaymentTypes = (_paymentTypes, _retailId, serviceCode) => {
  const paymentTypes = _paymentTypes || [];
  const retailId = _retailId.toUpperCase();

  if (Config.config.markets[retailId]?.[serviceCode].payToProvider) {
    return paymentTypes.filter((paymentType) => paymentType.type === 'PAY_TO_SP');
  }

  const containsPayToIkea = paymentTypes.some((paymentType) => paymentType.type === 'PAY_TO_IKEA');

  return containsPayToIkea ? paymentTypes.filter((paymentType) => paymentType.type === 'PAY_TO_IKEA') : paymentTypes;
};

const getAvailableServiceTimeWindows = async (args) => {
  const locality = args.locality ? args.locality.toUpperCase() : getLocalityMapping(args.retailId, args.requestId);
  // Applying zipcode regex to all the zipcodes passing through centiro since the zipcodes are handled differently in markets(Ex: JP and CA)
  const processedZipCode =
    args.retailId.toUpperCase() === 'NL' ? args.zipCode : processZipcode(args.zipCode, args.retailId);
  const body = {
    getAvailableServiceTimewindowsRequest: {
      shipToAddress: {
        country: args.retailId,
        zipCode: processedZipCode,
        state: args.state,
      },
      businessUnit: {
        buCode: args?.businessUnit?.buCode || getHSU(args.retailId),
        buType: args?.businessUnit?.buType || 'STO',
      },
      capacityUnit: args.capacityUnit,
      daysToReturn: args.daysToReturn || 7,
      filterDuplicates: true,
      orderKey: {
        orderNumber: args.orderKey?.orderNumber || '999999999',
        orderNumberSource: args.orderKey?.orderNumberSource || getOrderSourceMapping(locality),
      },
      serviceNo: args.serviceNo,
      serviceCode: args.serviceCode,
      articles: args.items,
      startDate: args.startDate || args.deliveryTimeWindows[0].start,
      capacityNeeded: args.capacityNeeded || null,
    },
    messageId: args.requestId,
    userId: 'OnlineServiceOffers',
    locality,
  };
  let apiVersion = 1;
  if (args.deliveryTimeWindows && args.deliveryTimeWindows.length) {
    body.getAvailableServiceTimewindowsRequest.transportTimewindows = args.deliveryTimeWindows.map(
      (deliveryTimeWindow) => ({
        transportTimewindowId: deliveryTimeWindow.transportTimeWindowId,
        end: deliveryTimeWindow.end,
        start: deliveryTimeWindow.start,
        serviceIdentifier: deliveryTimeWindow.serviceIdentifier,
      }),
    );
  } else if (args.deliveryTimeWindowId) {
    body.getAvailableServiceTimewindowsRequest.transportTimewindowId = args.deliveryTimeWindowId;
    apiVersion = 0;
  }
  let resp;
  // Todo: when soFetcher is being re-written remove this try catch
  try {
    resp = await centiroFetch(
      'GetAvailableServiceTimeWindows',
      body,
      args.requestId,
      apiVersion,
      args.retailId,
      processedZipCode,
    );
  } catch (error) {
    log.error({ message: 'GetAvailableServiceTimeWindows blew up', error });
    throw error;
  }
  const data =
    resp?.getAvailableServiceTimeWindowsResponse?.serviceProvider?.flatMap((serviceProvider) =>
      serviceProvider.timeWindowProposals
        .filter((timeWindowProposal) => timeWindowProposal.willBeOverbooked !== true)
        .map((timeWindowProposal) => ({
          serviceProviderId: serviceProvider.serviceProviderId,
          serviceProviderName: serviceProvider.serviceProviderName,
          supplierId: serviceProvider.supplierId,
          paymentTypes: filterPaymentTypes(serviceProvider.paymentTypes, args.retailId, args.serviceCode),
          ...timeWindowProposal,
        })),
    ) || [];

  return {
    availableServiceTimeWindows: data.filter((dataPoint) => {
      if (dataPoint.timeWindowDetails[0].start < args.deliveryTimeWindows[0].start) {
        console.log(
          'getAvailableServiceTimeWindows - assembly date is prior to delivery date',
          data[0].timeWindowDetails[0].start,
          args.deliveryTimeWindows[0].start,
        );
        Log.error({
          message: 'Assembly date is prior to delivery date',
          deliveryStartDate: args.deliveryTimeWindows[0].start,
          assemblyStartDate: data[0].timeWindowDetails[0].start,
          requestId: args.requestId,
          retailId: args.retailId,
          zipCode: args.zipCode,
          args: JSON.stringify(args),
          data: JSON.stringify(data),
          body: JSON.stringify(body),
        });
        return false;
      }
      return true;
    }),
  };
};

const getServiceDateProposal = async (args) => {
  const locality = args.locality ? args.locality.toUpperCase() : getLocalityMapping(args.retailId, args.requestId);
  // Applying zipcode regex to all the zipcodes passing through centiro since the zipcodes are handled differently in markets(Ex: JP and CA)
  const processedZipCode =
    args.retailId.toUpperCase() === 'NL' ? args.zipCode : processZipcode(args.zipCode, args.retailId);
  const body = {
    getServiceDateProposalRequest: {
      shipToAddress: {
        country: args.retailId,
        zipCode: processedZipCode,
        state: args.state,
      },
      businessUnit: {
        buCode: args?.businessUnit?.buCode || getHSU(args.retailId),
        buType: args?.businessUnit?.buType || 'STO',
      },
      orderKey: {
        orderNumber: args.orderKey?.orderNumber || '999999999',
        orderNumberSource: args.orderKey?.orderNumberSource || getOrderSourceMapping(locality),
      },
      serviceLines: [
        {
          capacityUnit: args.capacityUnit,
          serviceNo: args.serviceNo,
          serviceCode: args.serviceCode,
          articles: args.items,
          capacityNeeded: args.capacityNeeded || null,
        },
      ],
      startDate: args.startDate || args.deliveryTimeWindows[0].start,
    },
    messageId: args.requestId,
    userId: 'OnlineServiceOffers',
    locality,
  };

  let apiVersion = 1;
  if (args.deliveryTimeWindows && args.deliveryTimeWindows.length) {
    body.getServiceDateProposalRequest.serviceLines[0].transportTimewindows = args.deliveryTimeWindows.map(
      (deliveryTimeWindow) => ({
        transportTimewindowId: deliveryTimeWindow.transportTimeWindowId,
        end: deliveryTimeWindow.end,
        start: deliveryTimeWindow.start,
        serviceIdentifier: deliveryTimeWindow.serviceIdentifier,
      }),
    );
  } else if (args.deliveryTimeWindowId) {
    body.getServiceDateProposalRequest.serviceLines[0].transportTimewindowId = args.deliveryTimeWindowId;
    apiVersion = 0;
  }
  let resp;

  if (['HR'].includes(args?.retailId?.toUpperCase())) {
    const traceArgs = JSON.stringify(args);
    const traceBody = JSON.stringify(body);

    console.log('HR args from oc', traceArgs);
    console.log('HR body to centiro/dsm', traceBody);
  }
  // Todo: when soFetcher is being re-written remove this try catch
  try {
    resp = await centiroFetch(
      'GetServiceDateProposal',
      body,
      args.requestId,
      apiVersion,
      args.retailId,
      processedZipCode,
    );
  } catch (error) {
    log.error({ message: 'GetServiceDateProposal blew up', error });
    throw error;
  }
  let data = resp.getServiceDateProposalResponse?.proposedServiceTypes[0].serviceLines?.[0].serviceProvider;

  if (
    data &&
    !!data.timeWindows &&
    !!args.deliveryTimeWindows &&
    data.timeWindows[0].start < args.deliveryTimeWindows[0].start
  ) {
    console.log(
      'GetServiceDateProposal - assembly date is prior to delivery date',
      data.timeWindows[0].start,
      args.deliveryTimeWindows[0].start,
    );
    Log.error({
      message: 'Assembly date is prior to delivery date',
      deliveryStartDate: args.deliveryTimeWindows[0].start,
      assemblyStartDate: data.timeWindows[0].start,
      requestId: args.requestId,
      retailId: args.retailId,
      zipCode: args.zipCode,
      args: JSON.stringify(args),
      data: JSON.stringify(data),
      body: JSON.stringify(body),
    });
    data = null;
  }
  return {
    proposedServiceTimeWindows: data,
  };
};

const centiroPredicateFn = (a, b) => {
  if (isValidServiceById(a.serviceCode.replace(/SGR/i, ''))) {
    return a.serviceCode === b.input.serviceCode;
  }
  return false;
};

const getServiceDateProposalV2LoaderFunc = async (servicesInput) => {
  const res = await getServiceDateProposalBatchLogic(servicesInput);
  return sortAccordingTo(res, servicesInput, centiroPredicateFn);
};

const getServiceDateProposalV2 = (input, context) =>
  context.Loader.load(getServiceDateProposalV2LoaderFunc, {
    input,
  }).then((res) => ({
    proposedServiceTimeWindows: res.serviceProvider,
  }));

const getServiceDateProposalBatchLogic = async (servicesInput) => {
  const args = servicesInput[0].input;
  const services = servicesInput.map((serviceItem) => serviceItem.input);
  const locality = args.locality ? args.locality.toUpperCase() : getLocalityMapping(args.retailId, args.requestId);
  // Applying zipcode regex to all the zipcodes passing through centiro since the zipcodes are handled differently in markets(Ex: JP and CA)
  const processedZipCode =
    args.retailId.toUpperCase() === 'NL' ? args.zipCode : processZipcode(args.zipCode, args.retailId);
  const body = {
    getServiceDateProposalRequest: {
      shipToAddress: {
        country: args.retailId,
        zipCode: processedZipCode,
        state: args.state,
      },
      businessUnit: {
        buCode: args?.businessUnit?.buCode || getHSU(args.retailId),
        buType: args?.businessUnit?.buType || 'STO',
      },
      orderKey: {
        orderNumber: args.orderKey?.orderNumber || '999999999',
        orderNumberSource: args.orderKey?.orderNumberSource || getOrderSourceMapping(locality),
      },
      serviceLines: services.map((service) => ({
        capacityUnit: service.capacityUnit,
        serviceNo: service.serviceNo,
        serviceCode: service.serviceCode,
        articles: service.items,
        transportTimewindows: service.deliveryTimeWindows || null,
        transportTimewindowId: service.deliveryTimeWindowId || null,
        capacityNeeded: service.capacityNeeded || null,
      })),
      startDate: args.startDate || null,
    },
    messageId: args.requestId,
    userId: 'OnlineServiceOffers',
    locality,
  };
  let apiVersion = 1;
  if (args.deliveryTimeWindows && args.deliveryTimeWindows.length) {
    apiVersion = 1;
  } else if (args.deliveryTimeWindowId) {
    apiVersion = 0;
  }
  let resp;
  // Todo: when soFetcher is being re-written remove this try catch
  try {
    resp = await centiroFetch(
      'GetServiceDateProposal',
      body,
      args.requestId,
      apiVersion,
      args.retailId,
      processedZipCode,
    );
  } catch (error) {
    log.error({ message: 'GetServiceDateProposal blew up', error });
    throw error;
  }
  const data = resp?.getServiceDateProposalResponse?.proposedServiceTypes?.flatMap((serviceType) =>
    serviceType.serviceLines.map((serviceLine) => serviceLine),
  );
  return data;
};

const getServiceDateProposalWithAvailableTimeWindowsV2 = (args, context) =>
  Promise.all([getServiceDateProposalV2(args, context), getAvailableServiceTimeWindows(args)]).then(
    (timeWindowCall) => ({ ...timeWindowCall[0], ...timeWindowCall[1] }),
  );

const getServiceDateProposalWithAvailableTimeWindows = (args) =>
  Promise.all([getServiceDateProposal(args), getAvailableServiceTimeWindows(args)]).then((timeWindowCall) => ({
    ...timeWindowCall[0],
    ...timeWindowCall[1],
  }));

const getCompatibleServices = async (_retailId, serviceTypeCode, serviceProductId, requestId, forceRefresh = false) => {
  const retailId = _retailId.toUpperCase();
  if (serviceProductId === 'installation') {
    // Since installation is provided by third party provider, compatible delivery methods are not configured in centiro
    return Config.config.markets[retailId]?.[serviceProductId].compatibleDeliveryMethods;
  }
  const cacheKey = `compatibleServices:${retailId}:${serviceTypeCode}:${serviceProductId}`;
  const data = await getFromCache(cacheKey, CacheDb.Items);
  if (data && !forceRefresh) {
    Log.cacheToCallRatio({
      call: 'getCompatibleServices',
      requestId,
      cacheKey,
      system: 'centiro',
      ratio: 1,
      retailId,
    });

    return data;
  }

  Log.cacheToCallRatio({
    call: 'getCompatibleServices',
    requestId,
    cacheKey,
    system: 'centiro',
    ratio: 0,
    retailId,
  });

  const body = {
    getServiceCompatibilityRequest: {
      country: retailId,
      serviceTypeCodes: [serviceTypeCode],
    },
    messageId: requestId,
    userId: 'OnlineServiceOffers',
    locality: getLocalityMapping(retailId, requestId),
  };

  const resp = await centiroFetch('GetServiceCompatibility', body, requestId, 1, retailId);
  const compatibleServices = resp?.serviceCompatibility
    ?.find((serviceType) => serviceType.serviceTypeCode.toUpperCase() === serviceTypeCode)
    ?.services?.find((service) => service.serviceProductId === serviceProductId)?.compatibleServices;

  if (compatibleServices) {
    await setToCache(cacheKey, JSON.stringify(compatibleServices), Config.config.centiro.cacheTime);
    return compatibleServices;
  }

  return [];
};

export {
  getAvailableServiceTimeWindows,
  getServiceDateProposal,
  getServiceDateProposalWithAvailableTimeWindows,
  validateServiceDateProposal,
  getCompatibleServices,
  getServiceDateProposalV2,
  getServiceDateProposalWithAvailableTimeWindowsV2,
};