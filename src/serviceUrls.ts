/*
# Documentation

## Interfaces

### ServiceUrls

This interface represents the structure of the service URLs.

- `orderNumber`: A string representing the order number.
- `providerUrls`: An array of `ProviderUrl` objects.

### ProviderUrl

This interface represents the structure of the provider URL.

- `providerId`: A string representing the provider ID.
- `urls`: An array of `Url` objects.

### Url

This interface represents the structure of the URL.

- `serviceId`: A string representing the service ID.
- `url`: A string representing the URL.

## Functions

### getUrls

This is an asynchronous function that returns a promise which resolves to a `ServiceUrls` object.

**Syntax:**

```js
getUrls(parent, context)
```

**Parameters:**

- `parent`: An object containing the following properties:
  - `retailId`: The retail ID.
  - `orderNumber`: The order number.
  - `customerData`: The customer data.
  - `services`: An array of services.
- `context`: The context object.

**Returns:**

A promise that resolves to a `ServiceUrls` object.

**Throws:**

An error if the start time or end time of the chosen time window is not in a valid date format.

**Example:**

```js
getUrls({ retailId: '123', orderNumber: '456', customerData: {}, services: [] }, {})
  .then(serviceUrls => console.log(serviceUrls))
  .catch(error => console.error(error));
```

**Note:**

This function first maps the services to include the provider. Then, it groups the services by provider. For each provider, it validates the date format of the chosen time window. If the date format is invalid, it throws an error. Otherwise, it fetches the provider data and returns a `ProviderUrl` object. Finally, it returns a `ServiceUrls` object containing the order number and the provider URLs.
*/
import { Config } from '../config/config';
import { serviceUrls } from '../connectors/providerAPI';
import { ErrorCauses } from '../errors/errorConstants';
import { validateDateFormat } from '../helpers/helpers';

export interface ServiceUrls {
  orderNumber: string;
  providerUrls: ProviderUrl[];
}

export interface ProviderUrl {
  providerId: string;
  urls: Url[];
}

export interface Url {
  serviceId: string;
  url: string;
}

export async function getUrls(parent, context): Promise<ServiceUrls> {
  const { retailId, orderNumber, customerData, services } = parent;

  // Find and add the provider
  const withProvider = services.map((service) => {
    const provider = Config.getProvider(retailId, service.serviceId);
    return {
      ...service,
      provider,
    };
  });

  const serviceInfosByProvider = withProvider.reduce((result, currentValue) => {
    (result[currentValue.provider] = result[currentValue.provider] || []).push(currentValue);
    return result;
  }, {});

  const providerUrls = await Promise.all(
    Object.keys(serviceInfosByProvider).map(async (provider): Promise<ProviderUrl> => {
      const serviceInfos = serviceInfosByProvider[provider];
      delete serviceInfos[0].provider;

      if (
        !validateDateFormat(serviceInfos[0].chosenTimeWindow.startTime) &&
        validateDateFormat(serviceInfos[0].chosenTimeWindow.endTime)
      ) {
        throw new Error(ErrorCauses.INVALID_DATE);
      }

      const providerData = await serviceUrls(context, provider, retailId, orderNumber, customerData, serviceInfos);

      return {
        providerId: provider,
        urls: providerData.services,
      };
    }),
  );

  return {
    orderNumber,
    providerUrls,
  };
}