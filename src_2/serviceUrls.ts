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
