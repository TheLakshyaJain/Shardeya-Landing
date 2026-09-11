import { z } from 'zod';
import type { TFunction } from 'i18next';

// Mirrors backend/.../dto/PlotCreateRequest.java's Bean Validation constraints.
const e = (t: TFunction, key: string) => t(key, { ns: 'errors' });

export function buildPlotSchema(t: TFunction) {
  return z
    .object({
      plotNumber: z.string().min(1, e(t, 'plot.numberInvalid')).max(30, e(t, 'plot.numberInvalid')),
      status: z.enum(['AVAILABLE', 'RESERVED', 'SOLD']),
      reservedFor: z.string().optional().or(z.literal('')),
      reservedUntil: z.string().optional().or(z.literal('')),
      sizeValue: z.number({ message: e(t, 'plot.sizeRequired') }).positive(e(t, 'plot.sizeInvalid')),
      sizeUnit: z.string().min(1, e(t, 'plot.sizeUnitRequired')),
      facing: z.enum(['N', 'S', 'E', 'W', 'NE', 'NW', 'SE', 'SW']).optional(),
      price: z.number({ message: e(t, 'plot.priceRequired') }).nonnegative(e(t, 'plot.priceInvalid')),
      isGarden: z.boolean(),
      isCorner: z.boolean(),
      isHot: z.boolean(),
      remarks: z.string().max(2000, e(t, 'plot.remarksInvalid')).optional().or(z.literal('')),
      gridRow: z.number().int().min(0).optional(),
      gridCol: z.number().int().min(0).optional(),
    })
    .refine((data) => data.status !== 'RESERVED' || !!data.reservedFor, {
      path: ['reservedFor'],
      message: e(t, 'plot.reservedForRequired'),
    });
}
export type PlotFormValues = z.infer<ReturnType<typeof buildPlotSchema>>;
