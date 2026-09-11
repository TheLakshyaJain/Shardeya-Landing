import { z } from 'zod';
import type { TFunction } from 'i18next';

// Every message here mirrors backend/.../dto/ProjectCreateRequest.java's own
// Bean Validation constraints exactly, sourced from the same "errors"
// namespace a backend validation failure would use (see auth/schemas.ts for
// the established pattern this follows).
const e = (t: TFunction, key: string) => t(key, { ns: 'errors' });

export function buildProjectSchema(t: TFunction) {
  return z.object({
    name: z.string().min(2, e(t, 'project.nameInvalid')).max(150, e(t, 'project.nameInvalid')),
    projectType: z.enum(['RESIDENTIAL_PLOT_COLONY', 'APARTMENT', 'VILLA', 'COMMERCIAL', 'MIXED_USE'], {
      message: e(t, 'project.typeRequired'),
    }),
    address: z.string().min(5, e(t, 'project.addressInvalid')).max(500, e(t, 'project.addressInvalid')),
    locality: z.string().min(2, e(t, 'project.localityInvalid')).max(150, e(t, 'project.localityInvalid')),
    city: z.string().min(2, e(t, 'project.cityInvalid')).max(100, e(t, 'project.cityInvalid')),
    stateCode: z.string().regex(/^[A-Z]{2}$/, e(t, 'project.stateInvalid')),
    pincode: z
      .string()
      .regex(/^[1-9][0-9]{5}$/, e(t, 'project.pincodeInvalid'))
      .optional()
      .or(z.literal('')),
    googleMapsUrl: z
      .string()
      .regex(/^https:\/\/.*$/, e(t, 'project.mapsUrlInvalid'))
      .optional()
      .or(z.literal('')),
    latitude: z.number().min(-90).max(90).optional().nullable(),
    longitude: z.number().min(-180).max(180).optional().nullable(),
    // Backend caps at 3,000,000 (03-BUILDER-MODULES.md B-02 §11) -- this was
    // missing here, so the wizard let a user fill out all 5 steps and reach
    // Review before the backend rejected an over-limit value on submit,
    // instead of flagging it on the step where they actually typed it.
    totalAreaValue: z
      .number({ message: e(t, 'project.areaRequired') })
      .positive(e(t, 'project.areaInvalid'))
      .max(3_000_000, e(t, 'project.areaInvalid')),
    totalAreaUnit: z.string().min(1, e(t, 'project.areaUnitRequired')),
    declaredPlotCount: z
      .number({ message: e(t, 'project.plotCountRequired') })
      .int()
      .min(1, e(t, 'project.plotCountInvalid'))
      .max(100_000, e(t, 'project.plotCountInvalid')),
    launchDate: z.string().optional().or(z.literal('')),
    expectedCompletionDate: z.string().optional().or(z.literal('')),
    description: z.string().max(5000, e(t, 'project.descriptionInvalid')).optional().or(z.literal('')),
    reraNumber: z.string().max(60, e(t, 'project.reraInvalid')).optional().or(z.literal('')),
    coverMediaId: z.string().optional(),
    layoutMediaId: z.string().optional(),
    brochureMediaId: z.string().optional(),
  });
}
export type ProjectFormValues = z.infer<ReturnType<typeof buildProjectSchema>>;

export const PROJECT_FORM_STEP_FIELDS: (keyof ProjectFormValues)[][] = [
  ['name', 'projectType'],
  ['address', 'locality', 'city', 'stateCode', 'pincode', 'googleMapsUrl', 'latitude', 'longitude'],
  ['totalAreaValue', 'totalAreaUnit', 'declaredPlotCount', 'launchDate', 'expectedCompletionDate', 'description', 'reraNumber'],
  ['coverMediaId', 'layoutMediaId', 'brochureMediaId'],
  [],
];
