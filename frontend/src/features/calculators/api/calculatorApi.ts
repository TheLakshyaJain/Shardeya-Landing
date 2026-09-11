import { apiFetch } from '@/lib/api/client';
import type {
  BrokerageCalcRequest,
  BrokerageCalcResponse,
  PlotSizeRequest,
  PlotSizeResponse,
  StampDutyCalcRequest,
  StampDutyCalcResponse,
  UnitResponse,
} from '../types';

export function getUnits(stateCode?: string): Promise<UnitResponse[]> {
  const params = stateCode ? `?stateCode=${encodeURIComponent(stateCode)}` : '';
  return apiFetch(`/calc/units${params}`);
}

export function calculatePlotSize(req: PlotSizeRequest): Promise<PlotSizeResponse> {
  return apiFetch('/calc/plot-size', { method: 'POST', body: req });
}

export function calculateBrokerage(req: BrokerageCalcRequest): Promise<BrokerageCalcResponse> {
  return apiFetch('/calc/brokerage', { method: 'POST', body: req });
}

export function calculateStampDuty(req: StampDutyCalcRequest): Promise<StampDutyCalcResponse> {
  return apiFetch('/calc/stamp-duty', { method: 'POST', body: req });
}

export function getStampDutyStates(): Promise<string[]> {
  return apiFetch('/calc/stamp-duty/states');
}
