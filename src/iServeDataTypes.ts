export interface GetAvailableServicesFetchInput extends getAvailableServicesBaseRequest {
  url: string;
  cacheKey: string;
}

export interface getAvailableServicesRequest extends getAvailableServicesBaseRequest {
  sgrNo: string;
  state?: string;
}

export interface getAvailableServicesBaseRequest {
  buCode: string;
  buType: string;
  retailId: string;
  zipCode: string;
  requestId: string;
}

export interface CapacityUnitCode {
  code: string;
  translatedText: string;
  translationId: string;
}

export interface CompatibleService {
  capacityUnitCode: CapacityUnitCode;
  compatibleServices: CompatibleService[];
  sequenceNumber: number;
  serviceConfiguration: CompleteServiceConfiguration;
  serviceProductId: ServiceProductId;
  serviceProductType: ServiceProductType;
  transportDependent: boolean;
  _links: Links;
}

export interface CompleteServiceConfiguration extends ServiceConfiguration {
  afterSales: boolean;
  requiresItem: boolean;
  serviceProductLocation: string;
  surveyConnected: boolean;
  taxLocation: string;
}

export interface ServiceConfiguration {
  priceCalculationDetails: PriceCalculationDetail;
  serviceItemNo: string;
}

export interface PriceCalculationDetail {
  basePrice: number;
  basePriceType: {
    translatedText: string;
    translationId: string;
    type: string;
  };
  calculationType: {
    translatedText: string;
    translationId: string;
    type: string;
  };
  currencyCode: string;
  unitPrice: number;
}

export interface ServiceProductId {
  id: string;
  translatedText: string;
  translationId: string;
}

export interface ServiceProductType {
  translatedText: string;
  translationId: string;
  type: string;
}

export interface Links {
  self: Self[];
}

export interface Self {
  href: string;
}

export interface CompleteServiceProduct extends ServiceProduct {
  compatibleServices: CompatibleService[];
  sequenceNumber: number;
  serviceConfiguration: CompleteServiceConfiguration;
  transportDependent: boolean;
  _links: Links;
}

export interface ServiceProduct {
  capacityUnitCode: CapacityUnitCode;
  serviceConfiguration: ServiceConfiguration;
  serviceProductId: ServiceProductId;
  serviceProductType: ServiceProductType;
}
